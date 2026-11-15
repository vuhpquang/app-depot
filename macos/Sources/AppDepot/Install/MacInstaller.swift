// Install/MacInstaller.swift — installs or updates ONE app, verified end to end:
// download → size + SHA-256 (catalog) → unpack + bundle id + `codesign --strict` (app-shell stage) →
// signer: the installed copy's designated requirement, or the catalog `signer` for a first install →
// replace (quits only the copy at the target path; relaunches it if it was running).
// Temporary files are always removed. Never weaken any of these checks.
import AppKit
import DepotCore
import Foundation
import OSLog
import UpdaterKit

/// What one install step reports while it runs.
enum InstallStep: Sendable { case downloading(Int), verifying, installing }

@MainActor
protocol AppInstalling: AnyObject {
    /// `interactive: false` (background) never quits a running app: it fails with "Quit … to finish updating".
    func install(_ app: CatalogApp, interactive: Bool, step: @escaping @MainActor (InstallStep) -> Void) async -> InstallOutcome
}

@MainActor
final class MacInstaller: AppInstalling {
    let location: InstallLocation
    let downloader: Downloading
    let control: AppController
    let downloadsDir: URL
    let quitTimeout: Duration
    private let log = Logger(subsystem: "com.vuhpquang.appdepot", category: "install")

    init(location: InstallLocation, downloader: Downloading, control: AppController,
         downloadsDir: URL = MacInstaller.defaultDownloadsDir, quitTimeout: Duration = .seconds(10)) {
        self.location = location
        self.downloader = downloader
        self.control = control
        self.downloadsDir = downloadsDir
        self.quitTimeout = quitTimeout
    }

    nonisolated static var defaultDownloadsDir: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("com.vuhpquang.appdepot/downloads", isDirectory: true)
    }

    static func quitMessage(_ name: String) -> String { "Quit \(name) to finish updating" }

    func install(_ app: CatalogApp, interactive: Bool, step: @escaping @MainActor (InstallStep) -> Void) async -> InstallOutcome {
        guard app.slug != selfSlug else { return .failed("App Depot updates itself from Settings → Updates.") }
        guard app.installMode == InstallMode.managed, let release = app.latest else {
            return .failed("\(app.name) can't be installed from App Depot.")
        }
        let installed = location.installedURL(for: app)
        let target = installed ?? location.freshTarget(for: app)
        guard location.allows(target) else { return .failed("\(app.name) is outside the install folder.") }

        // Which signer the download must have, decided before anything is downloaded.
        let installedDR = installed.flatMap { ExternalInstaller.designatedRequirement(of: $0) }
        let requirement: String
        switch ReplaceGuard.requirement(for: app, installedRequirement: installedDR) {
        case let .success(r): requirement = r
        case .failure: return .failed(ReplaceGuard.noSignerMessage)
        }

        let lower = release.assetName.lowercased()
        let kind: AssetMatcher.Kind
        if lower.hasSuffix(".zip") { kind = .zip } else if lower.hasSuffix(".dmg") { kind = .dmg } else {
            return .failed("\(app.name)'s download isn't a zip or disk image.")
        }
        guard let url = URL(string: release.downloadUrl) else { return .failed("The catalog has a bad download link for \(app.name).") }

        let file = downloadsDir.appendingPathComponent("\(app.slug)-\(safeFileSegment(release.version)).\(kind == .zip ? "zip" : "dmg")")
        var staged: URL?
        defer {
            try? FileManager.default.removeItem(at: file)
            if let staged { Self.removeStagingDir(of: staged) }
        }

        step(.downloading(0))
        do {
            try await downloader.download(url, to: file, expectedSize: release.size) { pct in
                Task { @MainActor in step(.downloading(pct)) }
            }
        } catch {
            log.error("download of \(app.slug, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            return .failed("The download failed. Check the connection and try again.")
        }

        step(.verifying)
        let fileForCheck = file
        let check = await Task.detached { verifyDownload(fileForCheck, expectedSize: release.size, expectedSha256: release.sha256) }.value
        guard check == .ok else { return .failed("The download was damaged. Try again.") }

        let appId = app.appId
        do {
            staged = try await Task.detached { try ExternalInstaller().stage(fileForCheck, kind: kind, bundleId: appId) }.value
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            log.error("stage of \(app.slug, privacy: .public) refused: \(message, privacy: .public)")
            if message.contains("different app") { return .failed("This download isn't \(app.name). It wasn't installed.") }
            if message.contains("signature") { return .failed("\(app.name)'s signature is invalid. It wasn't installed.") }
            return .failed("\(app.name) couldn't be unpacked. Try again.")
        }
        guard let candidate = staged else { return .failed("\(app.name) couldn't be unpacked. Try again.") }
        guard ExternalInstaller.satisfies(candidate, requirement: requirement) else {
            return .failed("This file isn't signed like \(app.name). It wasn't installed.")
        }

        if !interactive && control.isRunning(bundleURL: target) { return .failed(Self.quitMessage(app.name)) }
        step(.installing)
        do {
            let outcome = try await ExternalInstaller(process: control)
                .replace(target: target, with: candidate, relaunchArguments: app.relaunchArgs, quitTimeout: quitTimeout)
            switch outcome {
            case .appRefusedToQuit: return .failed(Self.quitMessage(app.name))
            case .replaced: return .success(version: InstallLocation.version(at: target) ?? release.version)
            }
        } catch {
            log.error("replace of \(app.slug, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            return .failed("\(app.name) couldn't be replaced. \(error.localizedDescription)")
        }
    }

    /// app-shell stages into `<tmp>/AppShellUpdate-<uuid>/staged/<App>.app`; remove that whole folder.
    nonisolated static func removeStagingDir(of staged: URL) {
        let work = staged.deletingLastPathComponent().deletingLastPathComponent()
        guard work.lastPathComponent.hasPrefix("AppShellUpdate-") else { return }
        try? FileManager.default.removeItem(at: work)
    }
}
