// App/AppShell+AppDepot.swift — App Depot's values for the shared app shell (Vendor/app-shell):
// who it is, where its updates come from (the catalog entry `app-depot-macos`), its settings store.
import AboutKit
import AppKit
import AppShellCore
import UpdaterKit

extension AppIdentity {
    static let appDepot: AppIdentity = {
        var app = AppIdentity(
            name: "App Depot",
            tagline: "Install and update my apps in one place",
            repo: "vuhpquang/app-depot",
            firstYear: 2026,
            accent: Theme.accent,
            whatsNew: .automatic
        )
        app.updateSourceDescription = "Updates come from the App Depot catalog on vuhpquang.com."
        app.tagPrefix = "macos-v"   // one repo, two platforms: tags are macos-vX.Y.Z / android-vX.Y.Z
        return app
    }()
}

/// Where App Depot keeps its settings. Debug builds use their own domain so a dev run never changes the
/// installed copy's settings; snapshot runs use a throwaway one.
enum DepotDefaults {
    nonisolated(unsafe) static let store: UserDefaults = {
        if ProcessInfo.processInfo.environment["APPDEPOT_SNAPSHOT"] != nil {
            let name = "com.vuhpquang.appdepot.snapshot"
            UserDefaults().removePersistentDomain(forName: name)
            return UserDefaults(suiteName: name)!
        }
        #if DEBUG
        return UserDefaults(suiteName: "com.vuhpquang.appdepot.debug")!
        #else
        return .standard
        #endif
    }()
}

enum DepotEndpoints {
    static let base = URL(string: "https://vuhpquang.com")!
    static let catalog = URL(string: "https://vuhpquang.com/api/depot/catalog?platform=macos")!
    static let selfSlug = "app-depot-macos"
}

extension Updater {
    /// App Depot updates itself from the catalog (not GitHub "Latest": one repo, two platforms).
    @MainActor static let shared: Updater = {
        let updater = Updater(app: .appDepot, matcher: .zip(prefix: "AppDepot-"),
                              settings: UserDefaultsUpdateSettings(defaults: DepotDefaults.store),
                              transport: CatalogTransport.appDepot)
        updater.unreachableMessage = "Couldn't reach vuhpquang.com. Check your connection and try again"
        return updater
    }()
}

extension CatalogTransport {
    /// Where App Depot's own release (and its notes) come from.
    static let appDepot = CatalogTransport(catalogURL: DepotEndpoints.catalog, slug: DepotEndpoints.selfSlug)
}

extension WhatsNewTracker {
    @MainActor static let shared = WhatsNewTracker(app: .appDepot, defaults: DepotDefaults.store)
}
