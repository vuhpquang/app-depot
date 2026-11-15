import CryptoKit
import Foundation

/// One app on the home screen: catalog entry, plan, and whether it can be opened.
public struct AppRow: Equatable, Sendable, Identifiable {
    public var app: CatalogApp
    public var state: AppState
    public var launchable: Bool
    public var id: String { app.slug }

    public init(app: CatalogApp, state: AppState, launchable: Bool) {
        self.app = app
        self.state = state
        self.launchable = launchable
    }
}

/// Home layout, persisted as `appdepot.ui.layout`.
public enum HomeLayout: String, CaseIterable, Sendable {
    case list, grid
    public init(key: String?) { self = key.flatMap(HomeLayout.init(rawValue:)) ?? .list }
}

/// Home sort order, persisted as `appdepot.ui.sort`.
public enum SortOrder: String, CaseIterable, Sendable {
    case updatesFirst = "updates", name, recentlyReleased = "recent"
    public init(key: String?) { self = key.flatMap(SortOrder.init(rawValue:)) ?? .updatesFirst }
}

private func byName(_ a: AppRow, _ b: AppRow) -> Bool {
    let x = foldForSearch(a.app.name), y = foldForSearch(b.app.name)
    return x != y ? x < y : a.app.slug < b.app.slug
}

public func sortRows(_ rows: [AppRow], _ order: SortOrder) -> [AppRow] {
    switch order {
    case .updatesFirst:
        return rows.sorted { a, b in
            let x = a.state.isUpdateAvailable ? 0 : 1, y = b.state.isUpdateAvailable ? 0 : 1
            return x != y ? x < y : byName(a, b)
        }
    case .name:
        return rows.sorted(by: byName)
    case .recentlyReleased:
        // ISO-8601 timestamps sort as text; apps without a release go last.
        return rows.sorted { a, b in
            switch (a.app.latest?.publishedAt, b.app.latest?.publishedAt) {
            case let (x?, y?) where x != y: return x > y
            case (nil, _?): return false
            case (_?, nil): return true
            default: return byName(a, b)
            }
        }
    }
}

/// Bytes to download for the primary Install / Update action; nil when there is nothing to download.
public func downloadBytes(_ row: AppRow) -> Int64? {
    let size: Int64?
    switch row.state {
    case let .updateAvailable(_, latest): size = latest.size
    case .notInstalled: size = row.app.latest?.size
    default: size = nil
    }
    return size.flatMap { $0 > 0 ? $0 : nil }
}

/// What "Update all" installs: available updates of managed apps (skipped, Wisp, local builds never).
public func updateAllSlugs(_ rows: [AppRow]) -> [String] {
    orderForUpdateAll(rows.filter { $0.state.isUpdateAvailable && ReplaceGuard.canInstall($0.app, state: $0.state) }.map(\.app.slug))
}

/// Total download of Update all.
public func updateAllBytes(_ rows: [AppRow]) -> Int64 {
    rows.reduce(0) { sum, r in
        if case let .updateAvailable(_, latest) = r.state { return sum + latest.size }
        return sum
    }
}

private let privateDownloadPrefix = "https://vuhpquang.com/api/depot/download/"

/// GitHub releases for public repos; private ones (served through vuhpquang.com) get the apps page.
public func releasePageURL(_ app: CatalogApp) -> String {
    app.latest?.downloadUrl.hasPrefix(privateDownloadPrefix) == true ? "https://vuhpquang.com/apps" : "https://github.com/\(app.repo)/releases"
}

/// Installed apps can be uninstalled (moved to the Trash), except App Depot itself.
public func canUninstall(_ row: AppRow) -> Bool {
    guard row.app.slug != selfSlug else { return false }
    switch row.state {
    case .upToDate, .updateAvailable, .skipped, .localBuild: return true
    case let .noRelease(installed): return installed != nil
    case let .selfUpdates(installed, _): return installed != nil
    case .notInstalled, .notInstallable: return false
    }
}

/// "ZenTask: <download link>", or nil when there is no release to share.
public func shareText(_ app: CatalogApp) -> String? { app.latest.map { "\(app.name): \($0.downloadUrl)" } }

/// Disk cache file name for an icon URL.
public func iconFileName(_ url: String) -> String {
    SHA256.hash(data: Data(url.utf8)).map { String(format: "%02x", $0) }.joined() + ".png"
}

/// Items of a row's context menu.
public enum MenuAction: Equatable, Sendable { case open, uninstall, share, releasePage }

public func menuActions(_ row: AppRow) -> [MenuAction] {
    var items: [MenuAction] = []
    if row.launchable && row.app.slug != selfSlug { items.append(.open) }
    if canUninstall(row) { items.append(.uninstall) }
    if row.app.latest != nil { items.append(.share) }
    items.append(.releasePage)
    return items
}
