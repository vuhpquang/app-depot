// Install/AppController.swift — how App Depot talks to running apps. It is app-shell's
// `AppProcessControlling`: running apps are matched by their exact bundle path (`ExternalInstaller.isSameBundle`),
// never by name or bundle id, so only the copy being replaced can be asked to quit. Tests inject a fake.
import AppKit
import UpdaterKit

typealias AppController = AppProcessControlling

extension WorkspaceAppProcessControl {
    /// The live controller.
    @MainActor static let live = WorkspaceAppProcessControl()
}
