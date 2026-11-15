import Foundation

/// What App Depot knows about one app on this Mac (same states as Android).
public enum AppState: Equatable, Hashable, Sendable {
    case notInstalled
    case upToDate(installed: String)
    case updateAvailable(installed: String, latest: CatalogRelease)
    /// An update the user chose to skip: no badge, not in Update all or background runs, until a newer version.
    case skipped(installed: String, latest: CatalogRelease)
    /// Installed version newer than the catalog (office `PUBLISH=0` build): never downgraded.
    case localBuild(installed: String, latest: CatalogRelease)
    case noRelease(installed: String?)
    /// Wisp: updates from its own checkout; App Depot never installs, quits or replaces it.
    case selfUpdates(installed: String?, latest: CatalogRelease?)
    case notInstallable

    public var installedVersion: String? {
        switch self {
        case .notInstalled, .notInstallable: return nil
        case let .upToDate(v): return v
        case let .updateAvailable(v, _), let .skipped(v, _), let .localBuild(v, _): return v
        case let .noRelease(v): return v
        case let .selfUpdates(v, _): return v
        }
    }

    public var isUpdateAvailable: Bool {
        if case .updateAvailable = self { return true }
        return false
    }

    public var availableVersion: String? {
        if case let .updateAvailable(_, latest) = self { return latest.version }
        return nil
    }
}

/// `skippedVersion` is the version the user skipped for this app (`appdepot.skipped`), if any.
public func planFor(_ app: CatalogApp, installedVersion: String?, skippedVersion: String? = nil) -> AppState {
    let latest = app.latest
    if app.installMode == InstallMode.none {
        return latest == nil ? .noRelease(installed: installedVersion) : .notInstallable
    }
    if app.installMode == InstallMode.selfUpdates { return .selfUpdates(installed: installedVersion, latest: latest) }
    guard let latest else { return .noRelease(installed: installedVersion) }
    guard let installedVersion else { return .notInstalled }
    let c = Versions.compare(installedVersion, latest.version)
    if c < 0 && latest.version == skippedVersion { return .skipped(installed: installedVersion, latest: latest) }
    if c < 0 { return .updateAvailable(installed: installedVersion, latest: latest) }
    if c > 0 { return .localBuild(installed: installedVersion, latest: latest) }
    return .upToDate(installed: installedVersion)
}

/// `slug=version` lines, the format of every slug map in the settings.
public func parseSlugMap(_ text: String?) -> [String: String] {
    guard let text else { return [:] }
    var map: [String: String] = [:]
    for line in text.split(whereSeparator: \.isNewline) {
        let parts = line.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty else { continue }
        map[String(parts[0])] = String(parts[1])
    }
    return map
}

public func formatSlugMap(_ map: [String: String]) -> String {
    map.keys.sorted().map { "\($0)=\(map[$0]!)" }.joined(separator: "\n")
}

/// The one gate in front of the installer.
public enum ReplaceGuard {
    public static let noSignerMessage = "This app can't be installed safely yet."

    public enum Refusal: Error, Equatable { case noSigner }

    /// Only `managed` apps that are not installed or have an available update get an install path.
    /// Wisp (`selfUpdates`), DevKit (`none`), local builds, skipped, up to date, no-release and entries for
    /// another platform (an Android APK can share a bundle id with a Mac app) never do.
    public static func canInstall(_ app: CatalogApp, state: AppState) -> Bool {
        guard app.platform == macPlatform, app.installMode == InstallMode.managed, app.latest != nil else { return false }
        switch state {
        case .notInstalled, .updateAvailable: return true
        default: return false
        }
    }

    /// The requirement a candidate must satisfy: the installed copy's designated requirement (update),
    /// else the catalog `signer` (first install); neither → refuse.
    public static func requirement(for app: CatalogApp, installedRequirement: String?) -> Result<String, Refusal> {
        if let installedRequirement, !installedRequirement.isEmpty { return .success(installedRequirement) }
        if let signer = app.signer, !signer.isEmpty { return .success(signer) }
        return .failure(.noSigner)
    }
}
