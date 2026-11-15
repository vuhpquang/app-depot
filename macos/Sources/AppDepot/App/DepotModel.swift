// App/DepotModel.swift — the one state holder of the UI: catalog (live, else the cached copy), what is
// installed, the rows the home screen shows, and the user's actions (install, update all, open, skip,
// uninstall). Installs go through `InstallCoordinator` only.
import AppKit
import DepotCore
import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class DepotModel {
    // MARK: State

    /// The full catalog, every platform (filtering happens here, never on the server).
    private(set) var allApps: [CatalogApp] = []
    /// The macOS entries: the only ones App Depot plans, installs, updates, skips or uninstalls.
    var apps: [CatalogApp] { localApps(allApps) }
    /// When the shown catalog was fetched (live or cached).
    private(set) var catalogDate: Date?
    /// Set when the last refresh failed; the cached list stays on screen.
    private(set) var refreshError: String?
    private(set) var isRefreshing = false
    private(set) var installedVersions: [String: String] = [:]
    private(set) var installedURLs: [String: URL] = [:]

    var search = ""
    var sort: DepotCore.SortOrder { didSet { defaults.set(sort.rawValue, forKey: Keys.sort) } }
    var layout: HomeLayout { didSet { defaults.set(layout.rawValue, forKey: Keys.layout) } }
    var platformFilter: PlatformFilter { didSet { defaults.set(platformFilter.rawValue, forKey: Keys.platformFilter) } }
    /// The product whose "Send to phone" sheet is showing.
    var sendToPhone: Product?
    /// The selected product's key (`projectSlug`, else slug).
    var selection: String?
    private(set) var skipped: [String: String] { didSet { defaults.set(formatSlugMap(skipped), forKey: Keys.skipped) } }
    /// A short message about the last action (uninstall result, open failure); cleared by the view.
    var notice: String?
    /// The app whose "Move to Trash" confirmation is showing.
    var confirmUninstall: CatalogApp?
    /// Whether the all-apps history sheet is showing.
    var showHistory = false

    // MARK: Dependencies

    @ObservationIgnored let coordinator: InstallCoordinator
    @ObservationIgnored let location: InstallLocation
    @ObservationIgnored let history: HistoryStore?
    @ObservationIgnored let control: AppController
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let fetch: @MainActor () async throws -> (Data, [CatalogApp])
    @ObservationIgnored private let catalogCache: URL?
    @ObservationIgnored private let selfVersion: String
    @ObservationIgnored private let log = Logger(subsystem: "com.vuhpquang.appdepot", category: "model")
    /// DEBUG: `slug=version` overrides of installed versions (`APPDEPOT_PRETEND`), to test updates.
    @ObservationIgnored var pretend: [String: String] = [:]

    enum Keys {
        static let sort = "appdepot.ui.sort"
        static let layout = "appdepot.ui.layout"
        static let skipped = "appdepot.skipped"
        static let platformFilter = "appdepot.ui.platformFilter"
    }

    init(coordinator: InstallCoordinator, location: InstallLocation, history: HistoryStore?, control: AppController,
         defaults: UserDefaults, catalogCache: URL?, selfVersion: String,
         fetch: @escaping @MainActor () async throws -> (Data, [CatalogApp])) {
        self.coordinator = coordinator
        self.location = location
        self.history = history
        self.control = control
        self.defaults = defaults
        self.catalogCache = catalogCache
        self.selfVersion = selfVersion
        self.fetch = fetch
        sort = DepotCore.SortOrder(key: defaults.string(forKey: Keys.sort))
        layout = HomeLayout(key: defaults.string(forKey: Keys.layout))
        platformFilter = PlatformFilter(key: defaults.string(forKey: Keys.platformFilter))
        skipped = parseSlugMap(defaults.string(forKey: Keys.skipped))
        coordinator.skipped = { [weak self] in self?.skipped ?? [:] }
        coordinator.updatableOthers = { [weak self] in self?.updateAllApps.filter { $0.slug != selfSlug } ?? [] }
        loadCachedCatalog()
    }

    // MARK: Catalog

    private func loadCachedCatalog() {
        guard let catalogCache, let data = try? Data(contentsOf: catalogCache), let parsed = try? CatalogParser.parse(data) else { return }
        allApps = parsed
        catalogDate = (try? FileManager.default.attributesOfItem(atPath: catalogCache.path)[.modificationDate]) as? Date
        rescanInstalled()
    }

    /// Fetches the live catalog. Offline or malformed → the cached list stays with a "Couldn't refresh" line.
    /// `quiet` (background runs) never changes what the user sees on failure.
    @discardableResult
    func refresh(quiet: Bool = false) async -> Bool {
        guard !isRefreshing else { return false }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let (data, parsed) = try await fetch()
            allApps = parsed
            catalogDate = Date()
            refreshError = nil
            if let catalogCache {
                try? FileManager.default.createDirectory(at: catalogCache.deletingLastPathComponent(), withIntermediateDirectories: true)
                try? data.write(to: catalogCache, options: .atomic)
            }
            rescanInstalled()
            return true
        } catch {
            log.error("catalog refresh failed: \(error.localizedDescription, privacy: .public)")
            if quiet { return false }
            refreshError = allApps.isEmpty ? "Couldn't load the app list. Check your connection and try again."
                                        : "Couldn't refresh. Showing the list from \(catalogDate.map(Self.relative) ?? "earlier")."
            rescanInstalled()
            return false
        }
    }

    static func relative(_ date: Date) -> String { date.formatted(.relative(presentation: .named)) }

    /// Re-reads installed versions (after installs, uninstalls, and on window focus).
    func rescanInstalled() {
        var versions: [String: String] = [:], urls: [String: URL] = [:]
        for app in apps {
            if app.slug == selfSlug {
                versions[app.slug] = selfVersion
                continue
            }
            if let url = location.installedURL(for: app) {
                urls[app.slug] = url
                if let v = InstallLocation.version(at: url) { versions[app.slug] = pretend[app.slug] ?? v }
            }
        }
        installedVersions = versions
        installedURLs = urls
    }

    /// Snapshot runs: show these installed versions without looking at the disk.
    func applySnapshot(apps: [CatalogApp], installed: [String: String], skipped: [String: String], refreshError: String?) {
        allApps = apps
        installedVersions = installed
        installedURLs = Dictionary(uniqueKeysWithValues: installed.keys.map { ($0, URL(fileURLWithPath: "/Applications/\($0).app")) })
        self.skipped = skipped
        self.refreshError = refreshError
        catalogDate = Date()
    }

    // MARK: Rows

    func state(of app: CatalogApp) -> AppState {
        planFor(app, installedVersion: installedVersions[app.slug], skippedVersion: skipped[app.slug])
    }

    var rows: [AppRow] {
        apps.map { AppRow(app: $0, state: state(of: $0), launchable: installedURLs[$0.slug] != nil && $0.slug != selfSlug) }
    }

    func row(_ slug: String) -> AppRow? { rows.first { $0.app.slug == slug } }

    /// Every product of the catalog, each with its planned macOS row (nil = not for this Mac).
    var products: [ProductRow] {
        productRows(allApps, local: macPlatform) { app in
            AppRow(app: app, state: state(of: app), launchable: installedURLs[app.slug] != nil && app.slug != selfSlug)
        }
    }

    var filterCounts: FilterCounts { DepotCore.filterCounts(products) }

    /// What Home lists: this Mac's products, then (filter All) the rest under "Other platforms".
    var sections: HomeSections { homeSections(products, filter: platformFilter, sort: sort, query: search) }

    func product(_ key: String) -> ProductRow? { products.first { $0.product.key == key } }

    var updateAllApps: [CatalogApp] {
        let slugs = updateAllSlugs(rows)
        return slugs.compactMap { s in apps.first { $0.slug == s } }
    }

    var updateCount: Int { updateAllApps.count }
    var updateAllSize: Int64 { updateAllBytes(rows.filter { updateAllSlugs(rows).contains($0.app.slug) }) }

    func card(_ row: AppRow) -> CardModel {
        cardModel(row.state, progress: coordinator.progress[row.app.slug], canLaunch: row.launchable, latest: row.app.latest)
    }

    /// A product row's card: the macOS row's (with install progress), else "Not for Mac".
    func card(_ item: ProductRow) -> CardModel { item.row.map(card) ?? item.model }

    var statusLine: String {
        if allApps.isEmpty { return isRefreshing ? "Loading apps…" : "No apps yet" }
        switch updateCount {
        case 0: return "No updates waiting"
        case 1: return "1 update available"
        default: return "\(updateCount) updates available"
        }
    }

    // MARK: Actions

    func install(_ app: CatalogApp) {
        guard app.platform == macPlatform else { return }
        Task {
            await coordinator.install(app)
            rescanInstalled()
        }
    }

    func updateAll() {
        let todo = updateAllApps
        guard !todo.isEmpty else { return }
        Task {
            await coordinator.updateAll(todo)
            rescanInstalled()
        }
    }

    func open(_ app: CatalogApp) {
        guard app.platform == macPlatform, let url = installedURLs[app.slug] else { return }
        let config = NSWorkspace.OpenConfiguration()
        NSWorkspace.shared.openApplication(at: url, configuration: config) { _, error in
            if let error { Task { @MainActor in self.notice = "Couldn't open \(app.name): \(error.localizedDescription)" } }
        }
        history?.record(DepotEvent(type: "open", appSlug: app.slug, toVersion: installedVersions[app.slug]))
    }

    func skip(_ app: CatalogApp) {
        guard app.platform == macPlatform, case let .updateAvailable(_, latest) = state(of: app) else { return }
        skipped[app.slug] = latest.version
    }

    func undoSkip(_ app: CatalogApp) { skipped[app.slug] = nil }

    /// Moves the installed copy to the Trash. Quits only the copy at that path first; refuses if it won't quit.
    func uninstall(_ app: CatalogApp) async {
        guard app.platform == macPlatform, app.slug != selfSlug, let url = installedURLs[app.slug], location.allows(url) else { return }
        if control.isRunning(bundleURL: url) {
            control.terminate(bundleURL: url)
            let deadline = ContinuousClock.now + .seconds(10)
            while control.isRunning(bundleURL: url) {
                if ContinuousClock.now >= deadline {
                    notice = "\(app.name) didn't quit. Quit it, then try again."
                    return
                }
                try? await Task.sleep(for: .milliseconds(200))
            }
        }
        do {
            _ = try await NSWorkspace.shared.recycle([url])
            history?.record(DepotEvent(type: "uninstall", appSlug: app.slug, fromVersion: installedVersions[app.slug]))
            notice = "\(app.name) was moved to the Trash."
        } catch {
            notice = "Couldn't move \(app.name) to the Trash: \(error.localizedDescription)"
        }
        rescanInstalled()
    }

    func releasePage(_ app: CatalogApp) -> URL? { URL(string: releasePageURL(app)) }

    func shareURL(_ app: CatalogApp) -> URL? { app.latest.flatMap { URL(string: $0.downloadUrl) } }

    /// The message the uninstall confirmation shows.
    static func uninstallMessage(_ app: CatalogApp) -> String {
        if app.slug == "wisp" {
            return "Wisp is the terminal you're using. Moving it to the Trash quits it and closes its windows. You can put it back from the Trash."
        }
        return "\(app.name) will quit if it's open. You can put it back from the Trash; its settings stay on this Mac."
    }
}
