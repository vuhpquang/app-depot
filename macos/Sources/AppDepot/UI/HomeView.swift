// UI/HomeView.swift — the main window: apps (list or grid) on the left, the selected app on the right.
// The list is the whole catalog grouped into products; the platform filter ("This Mac" / "All", ⌘1 / ⌘2)
// hides or shows apps with no Mac build, which then come last under "Other platforms".
// Top of the list: the platform filter. Toolbar: search (⌘F), sort, layout, Update all (n · size), History, refresh (⌘R).
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
                .navigationSplitViewColumnWidth(min: Theme.Size.sidebarMin, ideal: depot.layout == .grid ? 400 : (depot.platformFilter == .all ? 380 : 340), max: 560)
        } detail: {
            if let key = depot.selection, let item = depot.product(key) {
                ProductDetailView(item: item)
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
            Button("") { depot.platformFilter = .device }.keyboardShortcut("1").hidden()
            Button("") { depot.platformFilter = .all }.keyboardShortcut("2").hidden()
        }
        .alert(item: $depot.confirmUninstall) { app in
            Alert(title: Text("Move \(app.name) to the Trash?"), message: Text(DepotModel.uninstallMessage(app)),
                  primaryButton: .destructive(Text("Move to Trash")) { Task { await depot.uninstall(app) } },
                  secondaryButton: .cancel())
        }
        .sheet(isPresented: $depot.showHistory) { HistorySheet() }
        .sheet(item: $depot.sendToPhone) { product in
            SendToPhoneSheet(product: product).environment(depot)
        }
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
            PlatformFilterPicker()
                .padding(.horizontal, Theme.Spacing.m)
                .padding(.bottom, Theme.Spacing.s)
            if let error = depot.refreshError {
                Label(error, systemImage: "wifi.exclamationmark")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Theme.Spacing.l)
                    .padding(.vertical, Theme.Spacing.s)
                Divider()
            }
            let sections = depot.sections
            if sections.isEmpty {
                emptyState
            } else if depot.layout == .list {
                List(selection: $depot.selection) {
                    Section {
                        ForEach(sections.local) { AppListRow(item: $0).tag($0.product.key) }
                    }
                    if !sections.other.isEmpty {
                        Section("Other platforms") {
                            ForEach(sections.other) { AppListRow(item: $0).tag($0.product.key) }
                        }
                    }
                }
                .listStyle(.sidebar)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Theme.Spacing.m) {
                        grid(sections.local)
                        if !sections.other.isEmpty {
                            Text("Other platforms")
                                .font(.headline)
                                .foregroundStyle(.secondary)
                                .padding(.top, Theme.Spacing.s)
                            grid(sections.other)
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

extension AppsColumn {
    private func grid(_ items: [ProductRow]) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: Theme.Size.gridCardWidth), spacing: Theme.Spacing.m)],
                  spacing: Theme.Spacing.m) {
            ForEach(items) { item in
                AppGridCard(item: item, selected: depot.selection == item.product.key)
                    .onTapGesture { depot.selection = item.product.key }
            }
        }
    }

    @ViewBuilder private var emptyState: some View {
        if !depot.search.isEmpty {
            ContentUnavailableView {
                Label("No apps match “\(depot.search)”", systemImage: "magnifyingglass")
            } actions: {
                if depot.platformFilter == .device && depot.filterCounts.all > depot.filterCounts.device {
                    Button("Search all apps") { depot.platformFilter = .all }
                }
            }
            .frame(maxHeight: .infinity)
        } else if depot.platformFilter == .device && depot.filterCounts.all > 0 {
            ContentUnavailableView {
                Label("Nothing for this Mac yet", systemImage: "laptopcomputer")
            } actions: {
                Button("Show all apps") { depot.platformFilter = .all }
            }
            .frame(maxHeight: .infinity)
        } else {
            ContentUnavailableView { Label(depot.statusLine, systemImage: "shippingbox") }
                .frame(maxHeight: .infinity)
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

/// "This Mac · N" / "All · M" at the top of the list (⌘1 / ⌘2), persisted as `appdepot.ui.platformFilter`.
private struct PlatformFilterPicker: View {
    @Environment(DepotModel.self) private var depot

    var body: some View {
        @Bindable var depot = depot
        let counts = depot.filterCounts
        Picker("Platforms", selection: $depot.platformFilter) {
            Text("This Mac · \(counts.device)").tag(PlatformFilter.device)
            Text("All · \(counts.all)").tag(PlatformFilter.all)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .frame(maxWidth: .infinity)
        .help("Show only apps for this Mac (⌘1) or every app, Android ones included (⌘2)")
    }
}
