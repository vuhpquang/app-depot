// App/AppDepotApp.swift — scenes: the main window, the menu bar extra and Settings (app-shell's shell).
import AboutKit
import AppKit
import AppShellCore
import DepotCore
import SettingsKit
import SwiftUI
import UpdaterKit

@main
struct AppDepotApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @AppStorage("appdepot.settings.pane", store: DepotDefaults.store) private var settingsPane = "general"
    private let env = AppEnvironment.shared

    var body: some Scene {
        Window("App Depot", id: "main") {
            HomeView()
                .environment(env.model)
                .environment(env.icons)
                .tint(Theme.accent)
        }
        .defaultSize(width: 980, height: 640)
        .commands { AppCommands(env: env) }

        MenuBarExtra {
            MenuBarView(whatsNew: WhatsNewTracker.shared, onWhatsNew: { env.showWhatsNew() })
                .environment(env.model)
        } label: {
            MenuBarLabel(model: env.model)
        }

        Settings {
            SettingsShell(app: .appDepot, updater: Updater.shared, panes: [
                SettingsKit.SettingsPane(id: "general", title: "General", systemImage: "gearshape") {
                    GeneralPane(onPaired: { Task { await env.uploader?.flush() } })
                        .environment(env.pairing)
                },
            ], selection: $settingsPane, onWhatsNew: { env.showWhatsNew() })
        }
    }
}

/// Menu bar icon: a crate, with a badge dot style symbol while updates wait.
struct MenuBarLabel: View {
    let model: DepotModel
    var body: some View {
        Image(systemName: model.updateCount > 0 ? "shippingbox.and.arrow.backward.fill" : "shippingbox")
            .accessibilityLabel(model.updateCount > 0 ? "App Depot, \(model.statusLine)" : "App Depot")
    }
}

/// App menu items per the app-shell rule: About, Check for Updates…, What's New while there is one;
/// Settings… ⌘, comes from the Settings scene, Quit ⌘Q from the system.
struct AppCommands: Commands {
    let env: AppEnvironment
    @ObservedObject private var whatsNew = WhatsNewTracker.shared

    init(env: AppEnvironment) { self.env = env }

    var body: some Commands {
        CommandGroup(replacing: .appInfo) {
            Button("About App Depot") {
                AboutWindow.show(app: .appDepot, updater: Updater.shared, onWhatsNew: { env.showWhatsNew() })
            }
            Button("Check for Updates…") {
                Task { await env.refreshAndRecord() }
                Updater.shared.check(userInitiated: true)
            }
            if let title = whatsNew.menuTitle {
                Button(title) { env.showWhatsNew() }
            }
        }
        CommandGroup(replacing: .newItem) {}
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var snapshotMode = false

    func applicationWillFinishLaunching(_ notification: Notification) {
        #if DEBUG
        if let slug = DebugCLI.slug {
            NSApp.setActivationPolicy(.prohibited)
            Task { @MainActor in exit(await DebugCLI.run(slug)) }
            return
        }
        #endif
        if let dir = ProcessInfo.processInfo.environment["APPDEPOT_SNAPSHOT"] {
            snapshotMode = true
            NSApp.setActivationPolicy(.accessory)
            Task { @MainActor in
                await Snapshot.run(to: URL(fileURLWithPath: dir, isDirectory: true))
                exit(0)
            }
        }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard !snapshotMode, DebugCLI.slugIfDebug == nil else { return }
        let env = AppEnvironment.shared
        Self.useRepoIconInDebug()
        env.recordLaunch()
        WhatsNewTracker.shared.evaluateLaunch()
        if WhatsNewTracker.shared.pendingVersion != nil, AppIdentity.appDepot.whatsNew == .automatic { env.showWhatsNew() }
        Task {
            await env.refreshAndRecord()
            await env.uploader?.flush()
        }
    }

    /// The window can be closed; App Depot keeps running in the menu bar.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }

    /// A dev run from `.build` has no bundle icon: use the shared drawing's PNG so About looks right.
    static func useRepoIconInDebug() {
        #if DEBUG
        guard Bundle.main.object(forInfoDictionaryKey: "CFBundleIconFile") == nil else { return }
        let png = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../../../../assets/AppIcon-1024.png").standardizedFileURL
        if let img = NSImage(contentsOf: png) { NSApp.applicationIconImage = img }
        #endif
    }
}

#if !DEBUG
enum DebugCLI { static var slugIfDebug: String? { nil } }
#else
extension DebugCLI { static var slugIfDebug: String? { slug } }
#endif
