import AppKit
import DepotCore
import Foundation
import Testing
import UpdaterKit
@testable import AppDepot

// MARK: - Fixtures (real bundles built and signed ad-hoc at test time; nothing outside a temp folder)

/// A temp folder removed at deinit.
final class TempDir {
    let url: URL
    init() throws {
        url = FileManager.default.temporaryDirectory.appendingPathComponent("AppDepotTests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    }
    deinit { try? FileManager.default.removeItem(at: url) }
}

@discardableResult
func sh(_ tool: String, _ args: [String]) throws -> String {
    let p = Process()
    p.executableURL = URL(fileURLWithPath: tool)
    p.arguments = args
    let pipe = Pipe()
    p.standardOutput = pipe
    p.standardError = pipe
    try p.run()
    let out = String(decoding: pipe.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
    p.waitUntilExit()
    guard p.terminationStatus == 0 else { throw DownloadError("\(tool) failed: \(out)") }
    return out
}

/// `<dir>/<name>.app` with the given bundle id and version, signed ad-hoc.
func makeBundle(in dir: URL, name: String, bundleId: String, version: String, marker: String = "") throws -> URL {
    let app = dir.appendingPathComponent("\(name).app")
    let macos = app.appendingPathComponent("Contents/MacOS")
    try FileManager.default.createDirectory(at: macos, withIntermediateDirectories: true)
    let plist: [String: Any] = ["CFBundleIdentifier": bundleId, "CFBundleExecutable": "tool", "CFBundleName": name,
                                "CFBundlePackageType": "APPL", "CFBundleShortVersionString": version, "CFBundleVersion": "1",
                                "Marker": marker]
    try PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0)
        .write(to: app.appendingPathComponent("Contents/Info.plist"))
    try FileManager.default.copyItem(atPath: "/usr/bin/true", toPath: macos.appendingPathComponent("tool").path)
    try sh("/usr/bin/codesign", ["--force", "--deep", "--sign", "-", app.path])
    return app
}

func zip(_ app: URL, to zip: URL) throws -> (sha: String, size: Int64) {
    try sh("/usr/bin/ditto", ["-c", "-k", "--keepParent", app.path, zip.path])
    let size = (try FileManager.default.attributesOfItem(atPath: zip.path)[.size] as! NSNumber).int64Value
    return (try sha256Hex(of: zip), size)
}

/// "Downloads" by copying a local file (and records the URLs it was asked for).
final class CopyDownloader: Downloading, @unchecked Sendable {
    var files: [String: URL] = [:]
    var requested: [String] = []
    func download(_ url: URL, to destination: URL, expectedSize: Int64, progress: @escaping @Sendable (Int) -> Void) async throws {
        requested.append(url.absoluteString)
        guard let src = files[url.absoluteString] else { throw DownloadError("404") }
        try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.copyItem(at: src, to: destination)
        progress(100)
    }
}

@MainActor
final class FakeControl: AppProcessControlling {
    var running: Set<String> = []
    var refuseToQuit: Set<String> = []
    var terminated: [URL] = []
    var opened: [(URL, [String]?)] = []
    func isRunning(bundleURL: URL) -> Bool { running.contains(bundleURL.standardizedFileURL.path) }
    func terminate(bundleURL: URL) {
        terminated.append(bundleURL)
        if !refuseToQuit.contains(bundleURL.standardizedFileURL.path) { running.remove(bundleURL.standardizedFileURL.path) }
    }
    func open(_ app: URL, arguments: [String]?) { opened.append((app, arguments)) }
}

final class RecordingSink: EventSink {
    var events: [DepotEvent] = []
    func record(_ e: DepotEvent) { events.append(e) }
}

/// Catalog entry for a fixture zip.
func catalogApp(_ slug: String, name: String, bundleId: String, version: String, zip: (sha: String, size: Int64),
                signer: String?, mode: String = "managed", relaunch: [String] = []) -> CatalogApp {
    CatalogApp(slug: slug, name: name, tagline: "", platform: "macos", appId: bundleId, repo: "vuhpquang/\(slug)",
               signer: signer, relaunchArgs: relaunch, installMode: mode,
               latest: CatalogRelease(version: version, assetName: "\(name)-\(version).zip",
                                      downloadUrl: "https://example.invalid/\(slug)/\(version).zip",
                                      sha256: zip.sha, size: zip.size, publishedAt: "2026-11-15T00:00:00Z"))
}

// MARK: - Tests

@MainActor
@Suite(.serialized) struct MacInstallerTests {
    let tmp: TempDir
    let root: URL
    let downloads: URL
    let src: URL
    let downloader = CopyDownloader()
    let control = FakeControl()

    init() throws {
        tmp = try TempDir()
        root = tmp.url.appendingPathComponent("root")
        downloads = tmp.url.appendingPathComponent("downloads")
        src = tmp.url.appendingPathComponent("src")
        for d in [root, downloads, src] { try FileManager.default.createDirectory(at: d, withIntermediateDirectories: true) }
    }

    func installer(timeout: Duration = .milliseconds(300)) -> MacInstaller {
        MacInstaller(location: InstallLocation(root: root), downloader: downloader, control: control,
                     downloadsDir: downloads, quitTimeout: timeout)
    }

    /// Builds `<src>/v<version>/<Name>.app`, zips it, registers it with the downloader.
    func release(_ slug: String, name: String, bundleId: String, version: String, signer: String?,
                 builtId: String? = nil, relaunch: [String] = []) throws -> CatalogApp {
        let dir = src.appendingPathComponent("\(slug)-\(version)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let app = try makeBundle(in: dir, name: name, bundleId: builtId ?? bundleId, version: version)
        let z = try zip(app, to: dir.appendingPathComponent("\(name).zip"))
        let entry = catalogApp(slug, name: name, bundleId: bundleId, version: version, zip: z, signer: signer, relaunch: relaunch)
        downloader.files[entry.latest!.downloadUrl] = dir.appendingPathComponent("\(name).zip")
        return entry
    }

    func leftovers() -> [String] { (try? FileManager.default.contentsOfDirectory(atPath: downloads.path)) ?? [] }

    @Test func firstInstallSatisfyingCatalogSignerLandsInTheRoot() async throws {
        let app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", signer: "identifier \"com.test.tiny\"")
        let outcome = await installer().install(app, interactive: true) { _ in }
        #expect(outcome == .success(version: "1.0.0"))
        #expect(InstallLocation.version(at: root.appendingPathComponent("Tiny.app")) == "1.0.0")
        #expect(leftovers().isEmpty)
        #expect(control.terminated.isEmpty)
    }

    @Test func firstInstallWithoutSignerIsRefused() async throws {
        let app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", signer: nil)
        #expect(await installer().install(app, interactive: true) { _ in } == .failed("This app can't be installed safely yet."))
        #expect(downloader.requested.isEmpty, "refused before downloading")
        #expect(!FileManager.default.fileExists(atPath: root.appendingPathComponent("Tiny.app").path))
    }

    @Test func wrongSignerIsRefused() async throws {
        let app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0",
                              signer: "identifier \"com.test.tiny\" and anchor apple")
        let outcome = await installer().install(app, interactive: true) { _ in }
        #expect(outcome == .failed("This file isn't signed like Tiny. It wasn't installed."))
        #expect(!FileManager.default.fileExists(atPath: root.appendingPathComponent("Tiny.app").path))
        #expect(leftovers().isEmpty)
    }

    @Test func updateMustSatisfyTheInstalledCopysRequirement() async throws {
        // Installed copy signed ad-hoc: its designated requirement is its own cdhash, so a different build
        // (another signer as far as macOS is concerned) is refused even though the catalog signer would allow it.
        _ = try makeBundle(in: root, name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", marker: "old")
        let app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.1.0", signer: "identifier \"com.test.tiny\"")
        let outcome = await installer().install(app, interactive: true) { _ in }
        #expect(outcome == .failed("This file isn't signed like Tiny. It wasn't installed."))
        #expect(InstallLocation.version(at: root.appendingPathComponent("Tiny.app")) == "1.0.0")
    }

    @Test func wrongBundleIdIsRefused() async throws {
        let app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", signer: "identifier \"com.test.tiny\"",
                              builtId: "com.test.other")
        #expect(await installer().install(app, interactive: true) { _ in } == .failed("This download isn't Tiny. It wasn't installed."))
        #expect(leftovers().isEmpty)
    }

    @Test func tamperedZipIsRefused() async throws {
        var app = try release("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", signer: "identifier \"com.test.tiny\"")
        app.latest!.sha256 = String(repeating: "0", count: 64)
        #expect(await installer().install(app, interactive: true) { _ in } == .failed("The download was damaged. Try again."))
        #expect(leftovers().isEmpty, "the damaged download is deleted")
        #expect(!FileManager.default.fileExists(atPath: root.appendingPathComponent("Tiny.app").path))
    }

    @Test func runningCopyRefusingToQuitIsLeftAlone() async throws {
        let installed = try makeBundle(in: root, name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0")
        // Same signer as the installed copy: re-use its exact requirement by shipping the identical bundle bytes.
        let dir = src.appendingPathComponent("same")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try sh("/usr/bin/ditto", [installed.path, dir.appendingPathComponent("Tiny.app").path])
        let z = try zip(dir.appendingPathComponent("Tiny.app"), to: dir.appendingPathComponent("Tiny.zip"))
        var app = catalogApp("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", zip: z, signer: nil)
        downloader.files[app.latest!.downloadUrl] = dir.appendingPathComponent("Tiny.zip")
        app.relaunchArgs = []
        control.running = [installed.standardizedFileURL.path]
        control.refuseToQuit = [installed.standardizedFileURL.path]
        #expect(await installer().install(app, interactive: true) { _ in } == .failed("Quit Tiny to finish updating"))
        #expect(control.terminated.map(\.path) == [installed.path])
        #expect(control.opened.isEmpty)
    }

    @Test func runningCopyIsQuitReplacedAndRelaunchedWithArgs() async throws {
        let installed = try makeBundle(in: root, name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0")
        let dir = src.appendingPathComponent("same")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try sh("/usr/bin/ditto", [installed.path, dir.appendingPathComponent("Tiny.app").path])
        let z = try zip(dir.appendingPathComponent("Tiny.app"), to: dir.appendingPathComponent("Tiny.zip"))
        let app = catalogApp("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", zip: z, signer: nil, relaunch: ["--background"])
        downloader.files[app.latest!.downloadUrl] = dir.appendingPathComponent("Tiny.zip")
        control.running = [installed.standardizedFileURL.path]
        #expect(await installer().install(app, interactive: true) { _ in } == .success(version: "1.0.0"))
        #expect(control.opened.first?.0.path == installed.path)
        #expect(control.opened.first?.1 == ["--background"])
    }

    @Test func backgroundInstallNeverQuitsARunningApp() async throws {
        let installed = try makeBundle(in: root, name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0")
        let dir = src.appendingPathComponent("same")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try sh("/usr/bin/ditto", [installed.path, dir.appendingPathComponent("Tiny.app").path])
        let z = try zip(dir.appendingPathComponent("Tiny.app"), to: dir.appendingPathComponent("Tiny.zip"))
        let app = catalogApp("tiny", name: "Tiny", bundleId: "com.test.tiny", version: "1.0.0", zip: z, signer: nil)
        downloader.files[app.latest!.downloadUrl] = dir.appendingPathComponent("Tiny.zip")
        control.running = [installed.standardizedFileURL.path]
        #expect(await installer().install(app, interactive: false) { _ in } == .failed("Quit Tiny to finish updating"))
        #expect(control.terminated.isEmpty)
    }

    @Test func installRootIsRespected() throws {
        let loc = InstallLocation(root: root)
        let calc = CatalogApp(slug: "calc", name: "Calculator", tagline: "", platform: "macos", appId: "com.apple.calculator", repo: "x/y")
        #expect(loc.installedURL(for: calc) == nil, "/System/Applications is not looked at with a root set")
        #expect(loc.target(for: calc).path == root.appendingPathComponent("Calculator.app").path)
        #expect(loc.allows(root.appendingPathComponent("Calculator.app")))
        #expect(!loc.allows(URL(fileURLWithPath: "/Applications/Calculator.app")))
        #expect(!loc.allows(URL(fileURLWithPath: root.path + "-evil/Calculator.app")))
        #expect(InstallLocation.safeBundleName("../x/y") == "App")
        #expect(InstallLocation.safeBundleName("Padlet") == "Padlet")
    }
}

@MainActor
final class FakeInstaller: AppInstalling {
    var calls: [String] = []
    var results: [String: InstallOutcome] = [:]
    func install(_ app: CatalogApp, interactive: Bool, step: @escaping @MainActor (InstallStep) -> Void) async -> InstallOutcome {
        calls.append(app.slug)
        step(.downloading(50))
        return results[app.slug] ?? .success(version: app.latest?.version ?? "?")
    }
}

@MainActor
@Suite struct InstallCoordinatorTests {
    let tmp: TempDir
    let installer = FakeInstaller()
    let sink = RecordingSink()

    init() throws { tmp = try TempDir() }

    func coordinator() -> InstallCoordinator {
        InstallCoordinator(installer: installer, location: InstallLocation(root: tmp.url), events: sink)
    }

    func entry(_ slug: String, mode: String = "managed", latest: String? = "2.0.0") -> CatalogApp {
        CatalogApp(slug: slug, name: slug.capitalized, tagline: "", platform: "macos", appId: "com.test.\(slug)", repo: "v/\(slug)",
                   signer: "identifier \"com.test.\(slug)\"", installMode: mode,
                   latest: latest.map { CatalogRelease(version: $0, assetName: "a.zip", downloadUrl: "https://x/a.zip", sha256: "s", size: 10, publishedAt: "t") })
    }

    @Test func refusedQuitDoesNotStopUpdateAll() async {
        installer.results["a"] = .failed("Quit A to finish updating")
        let c = coordinator()
        let results = await c.updateAll([entry("a"), entry("b")])
        #expect(installer.calls == ["a", "b"])
        #expect(results["a"] == .failed("Quit A to finish updating"))
        #expect(results["b"] == .success(version: "2.0.0"))
        #expect(c.progress["a"] == .failed("Quit A to finish updating"))
        #expect(c.progress["b"] == .done(version: "2.0.0"))
        #expect(sink.events.map(\.type) == ["install_failed", "install"])
        #expect(sink.events[0].message == "Quit A to finish updating")
    }

    @Test func wispDevKitAndLocalBuildsNeverReachTheInstaller() async throws {
        // A local build: installed version newer than the catalog.
        _ = try makeBundle(in: tmp.url, name: "Local", bundleId: "com.test.local", version: "9.0.0")
        let c = coordinator()
        let wisp = entry("wisp", mode: "selfUpdates"), devkit = entry("devkit", mode: "none", latest: nil)
        let local = entry("local")
        _ = await c.updateAll([wisp, devkit, local])
        #expect(await c.install(wisp) == nil)
        #expect(await c.install(devkit) == nil)
        #expect(installer.calls.isEmpty)
        #expect(sink.events.isEmpty)
    }

    @Test func skippedVersionIsLeftOutOfUpdateAll() async throws {
        _ = try makeBundle(in: tmp.url, name: "Z", bundleId: "com.test.z", version: "1.0.0")
        let c = coordinator()
        c.skipped = { ["z": "2.0.0"] }
        _ = await c.updateAll([entry("z")])
        #expect(installer.calls.isEmpty)
    }

    @Test func selfIsHandedToTheUpdaterAfterTheQueue() async {
        var order: [String] = []
        let c = coordinator()
        c.selfUpdate = { order.append("self:\(self.installer.calls)") }
        _ = await c.updateAll([entry(selfSlug), entry("a")])
        #expect(installer.calls == ["a"], "App Depot never goes through the app installer")
        #expect(order == ["self:[\"a\"]"])
    }

    @Test func busySlugIsNotQueuedTwice() async {
        let c = coordinator()
        async let first = c.updateAll([entry("a")])
        async let second = c.updateAll([entry("a")])
        _ = await (first, second)
        #expect(installer.calls == ["a"])
    }
}
