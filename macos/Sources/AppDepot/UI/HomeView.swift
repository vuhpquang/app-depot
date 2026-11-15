// UI/HomeView.swift — the main window: apps (list or grid) on the left, the selected app on the right.
// Toolbar: search (⌘F), sort, layout, Update all (n · size), History, refresh (⌘R).
import DepotCore
import SwiftUI

struct HomeView: View {
    @Environment(DepotModel.self) private var depot
    @FocusState private var searchFocused: Bool
    @State private var searchActive = false

    var body: some View {
        @Bindable var depot = depot
        NavigationSplitView {
            AppsColumn()
                .navigationSplitViewColumnWidth(min: Theme.Size.sidebarMin, ideal: depot.layout == .grid ? 400 : 340, max: 560)
        } detail: {
            if let slug = depot.selection, let row = depot.row(slug) {
                AppDetailView(row: row)
            } else {
                ContentUnavailableView("Select an app", systemImage: "shippingbox",
                                       description: Text(depot.statusLine))
            }
        }
        .searchable(text: $depot.search, isPresented: $searchActive, placement: .sidebar, prompt: "Search apps")
        .toolbar { HomeToolbar() }
        .frame(minWidth: Theme.Size.windowMinWidth, minHeight: Theme.Size.windowMinHeight)
        .background {
            Button("") { searchActive = true }.keyboardShortcut("f").hidden()
        }
        .alert(item: $depot.confirmUninstall) { app in
            Alert(title: Text("Move \(app.name) to the Trash?"), message: Text(DepotModel.uninstallMessage(app)),
                  primaryButton: .destructive(Text("Move to Trash")) { Task { await depot.uninstall(app) } },
                  secondaryButton: .cancel())
        }
        .sheet(isPresented: $depot.showHistory) { HistorySheet() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            depot.rescanInstalled()
        }
    }
}

private struct AppsColumn: View {
    @Environment(DepotModel.self) private var depot

    var body: some View {
        @Bindable var depot = depot
        VStack(spacing: 0) {
            if let error = depot.refreshError {
                Label(error, systemImage: "wifi.exclamationmark")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Theme.Spacing.l)
                    .padding(.vertical, Theme.Spacing.s)
                Divider()
            }
            let rows = depot.visibleRows
            if rows.isEmpty {
                ContentUnavailableView {
                    Label(depot.search.isEmpty ? depot.statusLine : "No apps match “\(depot.search)”", systemImage: "magnifyingglass")
                }
                .frame(maxHeight: .infinity)
            } else if depot.layout == .list {
                List(rows, selection: $depot.selection) { row in
                    AppListRow(row: row).tag(row.app.slug)
                }
                .listStyle(.sidebar)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: Theme.Size.gridCardWidth), spacing: Theme.Spacing.m)],
                              spacing: Theme.Spacing.m) {
                        ForEach(rows) { row in
                            AppGridCard(row: row, selected: depot.selection == row.app.slug)
                                .onTapGesture { depot.selection = row.app.slug }
                        }
                    }
                    .padding(Theme.Spacing.m)
                }
            }
            Divider()
            Text(depot.statusLine)
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.Spacing.l)
                .padding(.vertical, Theme.Spacing.s)
        }
    }
}

private struct HomeToolbar: ToolbarContent {
    @Environment(DepotModel.self) private var depot

    var body: some ToolbarContent {
        @Bindable var depot = depot
        ToolbarItemGroup(placement: .primaryAction) {
            Button {
                depot.updateAll()
            } label: {
                Label(updateAllTitle, systemImage: "arrow.down.circle")
                    .labelStyle(.titleAndIcon)
            }
            .disabled(depot.updateCount == 0)
            .help(depot.updateCount == 0 ? "Everything is up to date" : "Download \(Format.bytes(depot.updateAllSize)) and update")

            Picker("Layout", selection: $depot.layout) {
                Label("List", systemImage: "list.bullet").tag(HomeLayout.list)
                Label("Grid", systemImage: "square.grid.2x2").tag(HomeLayout.grid)
            }
            .pickerStyle(.segmented)
            .help("Show apps as a list or a grid")

            Menu {
                Picker("Sort by", selection: $depot.sort) {
                    Text("Updates first").tag(DepotCore.SortOrder.updatesFirst)
                    Text("Name").tag(DepotCore.SortOrder.name)
                    Text("Recently released").tag(DepotCore.SortOrder.recentlyReleased)
                }
                .pickerStyle(.inline)
            } label: {
                Label("Sort", systemImage: "arrow.up.arrow.down")
            }
            .help("Sort apps")

            Button { depot.showHistory = true } label: { Label("History", systemImage: "clock.arrow.circlepath") }
                .help("Show history")

            Button { Task { await depot.refresh() } } label: { Label("Refresh", systemImage: "arrow.clockwise") }
                .keyboardShortcut("r")
                .disabled(depot.isRefreshing)
                .help("Check the catalog again")
        }
    }

    private var updateAllTitle: String {
        depot.updateCount == 0 ? "Update all" : "Update all (\(depot.updateCount)) · \(Format.bytes(depot.updateAllSize))"
    }
}
