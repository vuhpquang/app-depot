// UI/Snapshot.swift — `APPDEPOT_SNAPSHOT=/dir .build/debug/AppDepot` renders every screen to PNG, light and
// dark, from a fixture catalog with made-up installed versions. Installs, launches and quits nothing.
import AboutKit
import AppKit
import AppShellCore
import DepotCore
import SettingsKit
import SwiftUI
import UpdaterKit

@MainActor
enum Snapshot {
    static func run(to dir: URL) async {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let env = AppEnvironment.shared
        AppDelegate.useRepoIconInDebug()
        let model = env.model
        let apps = (try? CatalogParser.parse(Data(fixtureCatalog.utf8))) ?? []
        model.applySnapshot(apps: apps, installed: [
            "padlet": "2.5.0", "launch-app": "1.5.0", "quickcast": "1.4.0", "wisp": "0.3.3",
            "zentask-macos": "0.15.3", selfSlug: "0.1.0",
        ], skipped: ["quickcast": "1.4.1"], refreshError: nil)
        env.coordinator.showForSnapshot("launch-app", .downloading(percent: 42))
        await env.icons.preload(apps.compactMap(\.iconUrl))

        env.coordinator.showForSnapshot(selfSlug, .queued)
        model.platformFilter = .device
        model.layout = .list
        model.selection = "app-depot"
        render(dir, "detail-self-waiting", CGSize(width: 1000, height: 640), HomeSnapshotFrame(), toolbar: true)
        env.coordinator.showForSnapshot(selfSlug, .done(version: "0.1.1"))
        env.coordinator.clear(selfSlug)
        model.selection = "zentask"
        render(dir, "home-list", CGSize(width: 1000, height: 640), HomeSnapshotFrame(), toolbar: true)
        model.layout = .grid
        model.selection = "padlet"
        render(dir, "home-grid", CGSize(width: 1100, height: 640), HomeSnapshotFrame(), toolbar: true)
        model.platformFilter = .all
        model.layout = .list
        model.selection = "zentask"
        render(dir, "home-list-all", CGSize(width: 1000, height: 720), HomeSnapshotFrame(), toolbar: true)
        model.layout = .grid
        model.selection = "personal-wiki"
        render(dir, "home-grid-all", CGSize(width: 1100, height: 1180), HomeSnapshotFrame(), toolbar: true)
        model.layout = .list
        model.selection = "personal-wiki"
        render(dir, "detail-android-only", CGSize(width: 1000, height: 720), HomeSnapshotFrame(), toolbar: true)
        if let wiki = model.product("personal-wiki")?.product {
            render(dir, "send-to-phone", CGSize(width: Theme.Size.sheetWidth, height: 470),
                   SendToPhoneSheet(product: wiki).environment(model).environment(env.icons).tint(Theme.accent))
        }
        model.platformFilter = .device
        model.selection = "wisp"
        render(dir, "detail-wisp", CGSize(width: 1000, height: 640), HomeSnapshotFrame(), toolbar: true)
        model.selection = "quickcast"
        render(dir, "detail-skipped", CGSize(width: 1000, height: 640), HomeSnapshotFrame(), toolbar: true)

        model.applySnapshot(apps: apps, installed: ["padlet": "2.5.0"], skipped: [:],
                            refreshError: "Couldn't refresh. Showing the list from 2 hours ago.")
        env.coordinator.showForSnapshot("launch-app", .failed("Quit LaunchApp to finish updating"))
        model.selection = "launch-app"
        render(dir, "home-offline", CGSize(width: 1000, height: 640), HomeSnapshotFrame(), toolbar: true)

        model.applySnapshot(apps: apps, installed: [
            "padlet": "2.4.0", "launch-app": "1.5.0", "quickcast": "1.4.1", "wisp": "0.3.2", "zentask-macos": "0.15.3",
        ], skipped: [:], refreshError: nil)
        env.coordinator.clear("launch-app")
        render(dir, "menubar", CGSize(width: 340, height: 300), MenuBarSnapshotView().environment(model).padding(10))

        let settings = UserDefaultsUpdateSettings(defaults: DepotDefaults.store)
        let updater = Updater(app: .appDepot, matcher: .zip(prefix: "AppDepot-"), settings: settings, transport: FixtureTransport())
        await updater.checkNow(userInitiated: true)
        let panes = [SettingsKit.SettingsPane(id: "general", title: "General", systemImage: "gearshape") {
            GeneralPane(onPaired: {}).environment(env.pairing)
        }]
        for pane in ["general", "updates", "about"] {
            render(dir, "settings-\(pane)", CGSize(width: 760, height: 560),
                   SettingsShell(app: .appDepot, updater: updater, panes: panes, selection: .constant(pane)), toolbar: true)
        }
    }

    private struct HomeSnapshotFrame: View {
        var body: some View {
            HomeView()
                .environment(AppEnvironment.shared.model)
                .environment(AppEnvironment.shared.icons)
                .tint(Theme.accent)
        }
    }

    /// Puts a real window far off screen (so the owner never sees it), lets AppKit lay it out with its
    /// toolbar and list, then captures just that window with `screencapture -l` (window id).
    static func render<V: View>(_ dir: URL, _ name: String, _ size: CGSize, _ view: V, toolbar: Bool = false) {
        for (suffix, appearance) in [("light", NSAppearance.Name.aqua), ("dark", .darkAqua)] {
            let style: NSWindow.StyleMask = toolbar ? [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView]
                                                    : [.borderless]
            let window = SnapshotWindow(contentRect: CGRect(origin: .zero, size: size), styleMask: style, backing: .buffered, defer: false)
            window.level = NSWindow.Level(rawValue: Int(CGWindowLevelForKey(.desktopWindow)))
            window.appearance = NSAppearance(named: appearance)
            window.isReleasedWhenClosed = false
            let controller = NSHostingController(rootView: view
                .frame(width: size.width, height: size.height)
                .background(Color(nsColor: .windowBackgroundColor))
                .environment(\.colorScheme, suffix == "dark" ? .dark : .light))
            controller.sceneBridgingOptions = toolbar ? [.toolbars, .title] : []
            window.contentViewController = controller
            window.setContentSize(size)
            window.orderFrontRegardless()
            window.setFrameOrigin(SnapshotWindow.hiddenOrigin(for: window.frame.size))
            RunLoop.main.run(until: Date().addingTimeInterval(1.2))
            let file = dir.appendingPathComponent("\(name)-\(suffix).png")
            let p = Process()
            p.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
            p.arguments = ["-x", "-o", "-l", "\(window.windowNumber)", file.path]
            try? p.run()
            p.waitUntilExit()
            print(file.path, window.frame)
            window.orderOut(nil)
            window.close()
        }
    }

    /// Sits below every other window with a single pixel on screen (a window entirely off screen
    /// can't be captured), so the owner never sees the snapshot run.
    final class SnapshotWindow: NSWindow {
        override func constrainFrameRect(_ frameRect: NSRect, to screen: NSScreen?) -> NSRect { frameRect }

        static func hiddenOrigin(for size: NSSize) -> NSPoint {
            let screen = NSScreen.screens.first?.frame ?? .zero
            return NSPoint(x: screen.minX - size.width + 1, y: screen.minY - size.height + 1)
        }
    }

    /// App Depot's own entry, offline, for the Updates/About panes.
    struct FixtureTransport: UpdateTransport {
        func latestRelease(repo: String, userAgent: String) async throws -> Release {
            Release(tagName: "v0.1.1", body: "## Fixes\n- Update all keeps going when an app refuses to quit",
                    publishedAt: nil, assets: [.init(name: "AppDepot-0.1.1-macos.zip", url: URL(string: "https://example.invalid/a.zip")!)])
        }
        func release(repo: String, tag: String, userAgent: String) async throws -> Release {
            try await latestRelease(repo: repo, userAgent: userAgent)
        }
        func download(_ url: URL) async throws -> URL { throw URLError(.cancelled) }
    }
}
