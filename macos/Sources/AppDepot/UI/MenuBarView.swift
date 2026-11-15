// UI/MenuBarView.swift — the menu bar extra: status, apps with updates, Update all (n), Open App Depot,
// Check for Updates…, What's New (while there is one), Settings… ⌘, and Quit ⌘Q.
import AboutKit
import DepotCore
import SwiftUI
import UpdaterKit

struct MenuBarView: View {
    @Environment(DepotModel.self) private var depot
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings
    @ObservedObject var whatsNew: WhatsNewTracker
    let onWhatsNew: () -> Void

    var body: some View {
        Text(depot.statusLine)
        if let error = depot.refreshError { Text(error) }
        let updates = depot.rows.filter { $0.state.isUpdateAvailable && ReplaceGuard.canInstall($0.app, state: $0.state) }
        if !updates.isEmpty {
            Divider()
            ForEach(updates) { row in
                Button("Update \(row.app.name) to \(row.state.availableVersion ?? "")") { depot.install(row.app) }
                    .disabled(isBusy(depot.coordinator.progress[row.app.slug]))
            }
            Button("Update All (\(updates.count))") { depot.updateAll() }
        }
        Divider()
        Button("Open App Depot") { showMain() }.keyboardShortcut("o")
        Button("Check for Updates…") {
            Task { await depot.refresh() }
            Updater.shared.check(userInitiated: true)
        }
        if let title = whatsNew.menuTitle {
            Button(title) { onWhatsNew() }
        }
        Divider()
        Button("Settings…") { NSApp.activate(); openSettings() }.keyboardShortcut(",")
        Button("Quit App Depot") { NSApp.terminate(nil) }.keyboardShortcut("q")
    }

    private func showMain() {
        openWindow(id: "main")
        NSApp.activate()
    }
}

/// The same menu drawn as a panel, for snapshots only (a real menu can't be rendered offscreen).
struct MenuBarSnapshotView: View {
    @Environment(DepotModel.self) private var depot

    var body: some View {
        let updates = depot.rows.filter { $0.state.isUpdateAvailable && ReplaceGuard.canInstall($0.app, state: $0.state) }
        VStack(alignment: .leading, spacing: 0) {
            item(depot.statusLine, secondary: true)
            divider
            ForEach(updates) { row in item("Update \(row.app.name) to \(row.state.availableVersion ?? "")") }
            if !updates.isEmpty { item("Update All (\(updates.count))"); divider }
            item("Open App Depot", shortcut: "⌘O")
            item("Check for Updates…")
            divider
            item("Settings…", shortcut: "⌘,")
            item("Quit App Depot", shortcut: "⌘Q")
        }
        .padding(.vertical, Theme.Spacing.xs)
        .frame(width: Theme.Size.menuWidth)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: Theme.Radius.icon, style: .continuous))
    }

    private var divider: some View { Divider().padding(.vertical, Theme.Spacing.xs).padding(.horizontal, Theme.Spacing.m) }

    private func item(_ title: String, shortcut: String? = nil, secondary: Bool = false) -> some View {
        HStack {
            Text(title).foregroundStyle(secondary ? .secondary : .primary)
            Spacer()
            if let shortcut { Text(shortcut).foregroundStyle(.secondary) }
        }
        .font(.body)
        .padding(.horizontal, Theme.Spacing.l)
        .padding(.vertical, Theme.Spacing.xs)
    }
}
