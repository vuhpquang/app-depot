// App/AppEnvironment.swift — builds the app's objects once and wires them together.
import AboutKit
import AppKit
import AppShellCore
import DepotCore
import Foundation
import OSLog
import UpdaterKit

@MainActor
final class AppEnvironment {
    static let shared = AppEnvironment()

    let location = InstallLocation.fromEnvironment
    let history: HistoryStore?
    let pairing: Pairing
    let uploader: EventUploader?
    let coordinator: InstallCoordinator
    let model: DepotModel
    let icons = IconCache()
    let api: DepotAPI
    /// Opens (or brings back) the main window; set once the menu bar label exists.
    var openMain: (@MainActor () -> Void)?
    private let log = Logger(subsystem: "com.vuhpquang.appdepot", category: "app")

    static var isSnapshot: Bool { ProcessInfo.processInfo.environment["APPDEPOT_SNAPSHOT"] != nil }

    private init() {
        let defaults = DepotDefaults.store
        let version = AppIdentity.appDepot.version
        var api = DepotAPI(version: version)
        #if DEBUG
        if let server = defaults.string(forKey: "appdepot.debug.server").flatMap(normalizeServerUrl), let url = URL(string: server) {
            api.baseURL = url
        }
        #endif
        self.api = api

        history = Self.isSnapshot ? nil : (try? HistoryStore(path: HistoryStore.defaultPath))
        let tokens: TokenStore
        #if DEBUG
        tokens = DefaultsTokenStore(defaults: defaults)
        #else
        tokens = Self.isSnapshot ? MemoryTokenStore() : KeychainTokenStore()
        #endif
        let pairingAPI = api
        pairing = Pairing(defaults: defaults, tokens: tokens) { code, device in
            await pairingAPI.pair(code: code, deviceId: device, name: Host.current().localizedName ?? "Mac")
        }
        if let history {
            uploader = EventUploader(store: history, pairing: pairing) { token, body in await pairingAPI.postEvents(token: token, body: body) }
        } else {
            uploader = nil
        }

        let installer = MacInstaller(location: location, downloader: URLSessionDownloader(userAgent: "AppDepot-macOS/\(version)"),
                                     control: WorkspaceAppProcessControl.live)
        coordinator = InstallCoordinator(installer: installer, location: location, events: history)
        coordinator.selfUpdate = {
            let updater = Updater.shared
            await updater.checkNow(userInitiated: true)
            await updater.installNow()
        }
        let fetchAPI = api
        model = DepotModel(coordinator: coordinator, location: location, history: history, control: WorkspaceAppProcessControl.live,
                           defaults: defaults, catalogCache: Self.isSnapshot ? nil : DepotPaths.catalogCache,
                           selfVersion: version, fetch: { try await fetchAPI.fetchCatalog() })
        #if DEBUG
        model.pretend = parseSlugMap(ProcessInfo.processInfo.environment["APPDEPOT_PRETEND"]?.replacingOccurrences(of: ",", with: "\n"))
        #endif
        history?.onRecorded = { [weak self] in
            guard let self else { return }
            Task { await self.uploader?.flush() }
        }
    }

    /// Launch bookkeeping: `depot_started`, `self_update` when the version changed since the last run.
    func recordLaunch() {
        guard let history else { return }
        let defaults = DepotDefaults.store
        let version = AppIdentity.appDepot.version
        if let last = defaults.string(forKey: "appdepot.lastRunVersion"), last != version, Versions.compare(version, last) > 0 {
            history.record(DepotEvent(type: "self_update", appSlug: selfSlug, fromVersion: last, toVersion: version))
        }
        defaults.set(version, forKey: "appdepot.lastRunVersion")
        history.record(DepotEvent(type: "depot_started", toVersion: version))
    }

    /// A foreground refresh + its `check` row.
    func refreshAndRecord() async {
        if await model.refresh() {
            let n = model.updateCount
            history?.recordCheck(n == 1 ? "1 update" : "\(n) updates")
        }
    }

    func showWhatsNew() {
        WhatsNewWindow.show(app: .appDepot, version: AppIdentity.appDepot.version, tracker: WhatsNewTracker.shared,
                            transport: CatalogTransport.appDepot)
    }
}
