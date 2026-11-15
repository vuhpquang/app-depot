// Background/Scheduler.swift — background checks 10 s after launch, then every 6 hours, only while
// "Check for updates automatically" is on (app-shell's Updates pane setting).
import AppKit
import Combine
import DepotCore
import Foundation
import UpdaterKit

@MainActor
final class BackgroundScheduler {
    static let shared = BackgroundScheduler()

    private var timer: Timer?
    private var startTask: Task<Void, Never>?
    private var subscription: AnyCancellable?
    private var running = false
    private(set) var runner: BackgroundRun?

    func start() {
        let env = AppEnvironment.shared
        let settings = Updater.shared.settings
        let runner = BackgroundRun(
            model: env.model, notifier: SystemNotifier.shared, defaults: DepotDefaults.store,
            autoCheck: { settings.autoCheck }, autoInstall: { settings.autoCheck && settings.autoInstall },
            isAppActive: { NSApp.isActive },
            isRunning: { app in env.model.installedURLs[app.slug].map { env.model.control.isRunning(bundleURL: $0) } ?? false },
            selfUpdate: {
                let updater = Updater.shared
                if case .available = updater.state {} else { await updater.checkNow(userInitiated: false) }
                await updater.installNow()
            })
        runner.selfCheck = { await Updater.shared.checkNow(userInitiated: false) }
        runner.onChecked = { n in env.history?.recordCheck(n == 1 ? "1 update" : "\(n) updates") }
        self.runner = runner
        subscription = settings.changes.receive(on: DispatchQueue.main).sink { [weak self] in
            MainActor.assumeIsolated { self?.reschedule() }
        }
        reschedule()
    }

    private func reschedule() {
        timer?.invalidate()
        timer = nil
        startTask?.cancel()
        startTask = nil
        guard Updater.shared.settings.autoCheck else { return }
        startTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled else { return }
            await self?.runOnce()
        }
        timer = Timer.scheduledTimer(withTimeInterval: 6 * 3600, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { Task { await self?.runOnce() } }
        }
    }

    func runOnce() async {
        guard !running, let runner else { return }
        running = true
        defer { running = false }
        await runner.run()
        await AppEnvironment.shared.uploader?.flush()
    }
}
