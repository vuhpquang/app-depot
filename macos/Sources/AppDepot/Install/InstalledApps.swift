// Install/InstalledApps.swift — where an app is installed and which version it is.
// `APPDEPOT_INSTALL_ROOT` (tests / dev) confines every lookup and every install target to that folder:
// with it set, nothing in /Applications or anywhere else is read as "installed" or replaced.
import AppKit
import DepotCore
import Foundation

struct InstallLocation: Sendable {
    /// nil = the real Mac (`/Applications`, LaunchServices); a folder = only that folder.
    let root: URL?

    static var fromEnvironment: InstallLocation {
        let env = ProcessInfo.processInfo.environment["APPDEPOT_INSTALL_ROOT"].flatMap { $0.isEmpty ? nil : $0 }
        return InstallLocation(root: env.map { URL(fileURLWithPath: $0, isDirectory: true).standardizedFileURL })
    }

    /// The installed copy of `app`, or nil.
    /// Real Mac: `/Applications/<Name>.app`, `~/Applications/<Name>.app`, then any copy LaunchServices knows
    /// in an Applications folder, then LaunchServices' own pick — always with the right bundle id.
    func installedURL(for app: CatalogApp) -> URL? {
        if let root {
            let fm = FileManager.default
            let named = root.appendingPathComponent("\(app.name).app")
            if Self.bundleId(at: named) == app.appId { return named }
            let items = (try? fm.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
            return items.first { $0.pathExtension == "app" && Self.bundleId(at: $0) == app.appId }
        }
        let named = ["/Applications", NSHomeDirectory() + "/Applications"].map {
            URL(fileURLWithPath: $0).appendingPathComponent("\(app.name).app")
        }
        if let hit = named.first(where: { Self.bundleId(at: $0) == app.appId }) { return hit }
        let known = NSWorkspace.shared.urlsForApplications(withBundleIdentifier: app.appId)
        if let inApplications = known.first(where: { Self.isInApplicationsFolder($0) }) { return inApplications }
        return NSWorkspace.shared.urlForApplication(withBundleIdentifier: app.appId)
    }

    /// Where a first install goes: `<root or /Applications>/<Name>.app`.
    func freshTarget(for app: CatalogApp) -> URL {
        (root ?? URL(fileURLWithPath: "/Applications")).appendingPathComponent("\(Self.safeBundleName(app.name)).app")
    }

    /// The installed copy, else where a first install goes.
    func target(for app: CatalogApp) -> URL { installedURL(for: app) ?? freshTarget(for: app) }

    /// True when `url` may be written by an install under this location (inside the root when one is set).
    func allows(_ url: URL) -> Bool {
        guard let root else { return true }
        let path = url.resolvingSymlinksInPath().standardizedFileURL.path
        let rootPath = root.resolvingSymlinksInPath().standardizedFileURL.path
        return path.hasPrefix(rootPath.hasSuffix("/") ? rootPath : rootPath + "/")
    }

    func installedVersion(of app: CatalogApp) -> String? {
        installedURL(for: app).flatMap(Self.version(at:))
    }

    static func bundleId(at url: URL) -> String? { Bundle(url: url)?.bundleIdentifier }

    static func version(at url: URL) -> String? {
        guard let dict = NSDictionary(contentsOf: url.appendingPathComponent("Contents/Info.plist")) else { return nil }
        return dict["CFBundleShortVersionString"] as? String
    }

    static func isInApplicationsFolder(_ url: URL) -> Bool {
        let parent = url.deletingLastPathComponent().standardizedFileURL.path
        return parent == "/Applications" || parent == NSHomeDirectory() + "/Applications"
    }

    /// A catalog name made safe as a file name (no path separators).
    static func safeBundleName(_ name: String) -> String {
        let cleaned = name.replacingOccurrences(of: "/", with: "-").replacingOccurrences(of: ":", with: "-")
        return cleaned.isEmpty || cleaned.hasPrefix(".") ? "App" : cleaned
    }
}
