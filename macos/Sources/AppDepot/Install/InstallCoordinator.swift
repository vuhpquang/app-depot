// Install/InstallCoordinator.swift — the one place that installs. Claims a slug before queueing (a second
// request for a busy app is a no-op), runs installs one at a time, records every outcome as an event.
// App Depot itself is never installed here: after the queue it is handed to app-shell's Updater.
import DepotCore
import Foundation
import Observation

@MainActor
@Observable
final class InstallCoordinator {
    private(set) var progress: [String: InstallProgress] = [:]

    @ObservationIgnored let installer: AppInstalling
    @ObservationIgnored let location: InstallLocation
    @ObservationIgnored weak var events: EventSink?
    /// The user's skipped versions (slug → version), read at install time.
    @ObservationIgnored var skipped: () -> [String: String] = { [:] }
    /// Hands App Depot's own update to app-shell's Updater (runs after everything else).
    @ObservationIgnored var selfUpdate: (@MainActor () async -> Void)?

    @ObservationIgnored private var tail: Task<Void, Never>?

    init(installer: AppInstalling, location: InstallLocation, events: EventSink?) {
        self.installer = installer
        self.location = location
        self.events = events
    }

    /// Snapshot runs only: show a state without installing anything.
    func showForSnapshot(_ slug: String, _ p: InstallProgress) { progress[slug] = p }

    /// Forget a finished / failed state once it has been shown.
    func clear(_ slug: String) {
        if !isBusy(progress[slug]) { progress[slug] = nil }
    }

    /// Installs or updates one app. nil when it was already queued or running, or has no install path.
    @discardableResult
    func install(_ app: CatalogApp, interactive: Bool = true) async -> InstallOutcome? {
        if app.slug == selfSlug {
            await selfUpdate?()
            return nil
        }
        return await updateAll([app], interactive: interactive)[app.slug]
    }

    /// Installs the apps one at a time, App Depot itself last (through the Updater). Apps that are busy,
    /// skipped, Wisp, DevKit or local builds are left out — nothing reaches the installer without passing
    /// `ReplaceGuard` against the state on disk right now.
    @discardableResult
    func updateAll(_ apps: [CatalogApp], interactive: Bool = true) async -> [String: InstallOutcome] {
        let includesSelf = apps.contains { $0.slug == selfSlug }
        let eligible = apps.filter { app in
            guard app.slug != selfSlug else { return false }
            let state = planFor(app, installedVersion: location.installedVersion(of: app), skippedVersion: skipped()[app.slug])
            return ReplaceGuard.canInstall(app, state: state)
        }
        let mine = claimable(progress, eligible.map(\.slug))
        mine.forEach { progress[$0] = .queued }
        let todo = eligible.filter { mine.contains($0.slug) }

        let previous = tail
        let job = Task { @MainActor [weak self] () -> [String: InstallOutcome] in
            await previous?.value
            guard let self, !todo.isEmpty else { return [:] }
            return await runInstallQueue(todo, install: { app in await self.runOne(app, interactive: interactive) },
                                         onProgress: { slug, p in self.progress[slug] = p })
        }
        tail = Task { _ = await job.value }
        let results = await job.value
        if includesSelf { await selfUpdate?() }
        return results
    }

    private func runOne(_ app: CatalogApp, interactive: Bool) async -> InstallOutcome {
        let from = location.installedVersion(of: app)
        // A second request queued behind the first one: the work is already done.
        if let latest = app.latest?.version, from == latest { return .success(version: latest) }
        let outcome = await installer.install(app, interactive: interactive) { [weak self] step in
            switch step {
            case let .downloading(p): self?.progress[app.slug] = .downloading(percent: p)
            case .verifying: self?.progress[app.slug] = .verifying
            case .installing: self?.progress[app.slug] = .installing
            }
        }
        switch outcome {
        case let .success(version):
            events?.record(DepotEvent(type: from == nil ? "install" : "update", appSlug: app.slug, fromVersion: from, toVersion: version))
        case let .failed(message):
            events?.record(DepotEvent(type: "install_failed", appSlug: app.slug, fromVersion: from,
                                      toVersion: app.latest?.version, message: String(message.prefix(maxEventMessage))))
        }
        return outcome
    }
}
