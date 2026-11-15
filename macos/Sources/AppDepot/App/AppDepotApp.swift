// App/AppDepotApp.swift — scenes: the main window, the menu bar extra and Settings (app-shell's shell).
import AboutKit
import AppKit
import AppShellCore
import SettingsKit
import SwiftUI
import UpdaterKit

@main
struct AppDepotApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var settingsPane = "general"

    var body: some Scene {
        Window("App Depot", id: "main") {
            Text("App Depot")
                .frame(minWidth: Theme.Size.windowMinWidth, minHeight: Theme.Size.windowMinHeight)
        }
        .commands { AppCommands() }

        MenuBarExtra("App Depot", systemImage: "shippingbox") {
            Button("Open App Depot") { NSApp.activate() }
            Divider()
            Button("Quit App Depot") { NSApp.terminate(nil) }.keyboardShortcut("q")
        }

        Settings {
            SettingsShell(app: .appDepot, updater: Updater.shared, panes: [
                SettingsKit.SettingsPane(id: "general", title: "General", systemImage: "gearshape") {
                    Form { Section { LaunchAtLoginRow(appName: "App Depot") } }.formStyle(.grouped)
                },
            ], selection: $settingsPane)
        }
    }
}

/// App menu items per the app-shell rule: About, Check for Updates…, (Settings… ⌘, comes from the scene).
struct AppCommands: Commands {
    var body: some Commands {
        CommandGroup(replacing: .appInfo) {
            Button("About App Depot") { AboutWindow.show(app: .appDepot, updater: Updater.shared) }
            Button("Check for Updates…") { Updater.shared.check(userInitiated: true) }
        }
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationWillFinishLaunching(_ notification: Notification) {
        #if DEBUG
        if let slug = DebugCLI.slug {
            NSApp.setActivationPolicy(.prohibited)
            Task { @MainActor in exit(await DebugCLI.run(slug)) }
            return
        }
        #endif
    }

    func applicationDidFinishLaunching(_ notification: Notification) {}
}
