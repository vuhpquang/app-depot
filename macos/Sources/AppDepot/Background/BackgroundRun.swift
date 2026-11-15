// Background/BackgroundRun.swift — one background check, by the app-shell update conventions:
// never shows an error, never replaces a state the user hasn't acted on (busy / failed slugs are left
// alone), announces only new versions, and with "Install updates automatically" installs only managed
// apps that are NOT running (a running app is announced instead — a background run never quits an app).
// Wisp and DevKit never qualify. App Depot itself goes last, through app-shell's Updater, and not while
// the user is in the app.
import DepotCore
import Foundation

@MainActor
final class BackgroundRun {
    let model: DepotModel
    let notifier: Notifying
    let defaults: UserDefaults
    let autoCheck: () -> Bool
    let autoInstall: () -> Bool
    let isAppActive: () -> Bool
    let isRunning: (CatalogApp) -> Bool
    let selfUpdate: @MainActor () async -> Void
    /// Refreshes App Depot's own status in Settings → Updates (never installs).
    var selfCheck: @MainActor () async -> Void = {}
    var onChecked: (@MainActor (Int) -> Void)?

    enum Keys {
        static let notified = "appdepot.background.notified"
        static let failed = "appdepot.background.failed"
    }

    init(model: DepotModel, notifier: Notifying, defaults: UserDefaults, autoCheck: @escaping () -> Bool,
         autoInstall: @escaping () -> Bool, isAppActive: @escaping () -> Bool, isRunning: @escaping (CatalogApp) -> Bool,
         selfUpdate: @escaping @MainActor () async -> Void) {
        self.model = model
        self.notifier = notifier
        self.defaults = defaults
        self.autoCheck = autoCheck
        self.autoInstall = autoInstall
        self.isAppActive = isAppActive
        self.isRunning = isRunning
        self.selfUpdate = selfUpdate
    }

    func run() async {
        guard autoCheck() else { return }
        guard await model.refresh(quiet: true) else { return }   // offline: end quietly
        let rows = model.rows
        let next = rows.map { ($0.app.slug, $0.state) }
        onChecked?(next.filter { $0.1.isUpdateAvailable }.count)
        let apps = model.apps
        let bySlug = Dictionary(apps.map { ($0.slug, $0) }, uniquingKeysWith: { a, _ in a })
        let auto = autoInstall()
        let running = Set(apps.filter(isRunning).map(\.slug))
        let silent = auto ? silentCapableSlugs(next: next, apps: apps, running: running) : []
        let busy = Set(model.coordinator.progress.keys)
        let told = parseSlugMap(defaults.string(forKey: Keys.notified))
        var failed = parseSlugMap(defaults.string(forKey: Keys.failed))
        let plan = planBackground(told: told, failed: failed, next: next, autoInstall: auto, silentCapable: silent, busy: busy)

        var needsYou: [String] = []
        let others = plan.install.filter { $0 != selfSlug }.compactMap { bySlug[$0] }
        if !others.isEmpty {
            let outcomes = await model.coordinator.updateAll(others, interactive: false)
            for app in others {
                let version = model.state(of: app).availableVersion ?? app.latest?.version
                switch outcomes[app.slug] {
                case let .success(v)?:
                    await notifier.updated(app.name, version: v)
                    model.coordinator.clear(app.slug)
                case let .failed(message)?:
                    if message == MacInstaller.quitMessage(app.name) { needsYou.append(app.slug) }
                    if let version { failed[app.slug] = version }
                case nil:
                    break
                }
            }
            model.rescanInstalled()
        }

        var saveTold = plan.notified
        let announce = plan.announce.compactMap { bySlug[$0]?.name }
        let posted = announce.isEmpty ? true : await notifier.updatesAvailable(announce)
        if !posted {
            for slug in plan.announce { saveTold[slug] = told[slug] }   // try again next run
        }
        if !needsYou.isEmpty {
            let shown = await notifier.finishUpdating(needsYou.compactMap { bySlug[$0]?.name })
            if !shown { for slug in needsYou { failed[slug] = nil } }
        }
        let deferSelf = plan.install.contains(selfSlug) && isAppActive()
        if deferSelf { saveTold[selfSlug] = told[selfSlug] }
        defaults.set(formatSlugMap(saveTold.compactMapValues { $0 }), forKey: Keys.notified)
        // Forget failures once the app moved on to another version or is no longer updatable.
        let nextVersions = Dictionary(next.map { ($0.0, $0.1.availableVersion) }, uniquingKeysWith: { a, _ in a })
        defaults.set(formatSlugMap(failed.filter { nextVersions[$0.key] == $0.value }), forKey: Keys.failed)

        if plan.install.contains(selfSlug) && !deferSelf {
            await selfUpdate()
        } else {
            await selfCheck()
        }
    }
}
