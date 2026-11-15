// UI/AppDetailView.swift — one app: status, actions (Install/Update/Open, Skip, Move to Trash, Share,
// Release page), What's New from the catalog notes, and this app's history.
import DepotCore
import SwiftUI

struct AppDetailView: View {
    let row: AppRow
    @Environment(DepotModel.self) private var depot
    @Environment(\.openURL) private var openURL

    var body: some View {
        let m = depot.card(row)
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.xl) {
                header(m)
                if let error = m.error {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                        .font(.callout)
                }
                facts(m)
                notes
                historySection
            }
            .padding(Theme.Spacing.xl)
            .frame(maxWidth: 640, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle(row.app.name)
    }

    private func header(_ m: CardModel) -> some View {
        HStack(alignment: .top, spacing: Theme.Spacing.l) {
            AppIconView(app: row.app, size: Theme.Size.detailIcon)
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                HStack(spacing: Theme.Spacing.s) {
                    Text(row.app.name).font(.title.bold())
                    if let chip = m.chip { ChipView(chip: chip) }
                }
                Text(row.app.tagline).foregroundStyle(.secondary)
                HStack(spacing: Theme.Spacing.s) {
                    PrimaryActionView(row: row, model: m)
                    if let bytes = downloadBytes(row), m.activity == nil, m.action == .install || m.action == .update {
                        Text(Format.bytes(bytes)).font(.callout).foregroundStyle(.secondary)
                    }
                    secondaryActions
                }
                .padding(.top, Theme.Spacing.s)
            }
        }
    }

    @ViewBuilder private var secondaryActions: some View {
        if case .updateAvailable = row.state, ReplaceGuard.canInstall(row.app, state: row.state) {
            Button("Skip This Version") { depot.skip(row.app) }.help("Hide this update until a newer version comes out")
        }
        if case .skipped = row.state {
            Button("Undo Skip") { depot.undoSkip(row.app) }
        }
        Menu {
            AppContextMenu(row: row)
        } label: {
            Label("More", systemImage: "ellipsis")
        }
        .menuIndicator(.hidden)
        .fixedSize()
        .help("More actions")
    }

    private func facts(_ m: CardModel) -> some View {
        Grid(alignment: .leading, horizontalSpacing: Theme.Spacing.l, verticalSpacing: Theme.Spacing.s) {
            GridRow {
                Text("Installed").foregroundStyle(.secondary)
                Text(row.state.installedVersion ?? "Not installed")
            }
            GridRow {
                Text("Latest").foregroundStyle(.secondary)
                Text(latestText)
            }
            if case .selfUpdates = row.state {
                GridRow {
                    Text("Updates").foregroundStyle(.secondary)
                    Text("\(row.app.name) updates itself. App Depot never installs or replaces it.")
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            if case .localBuild = row.state {
                GridRow {
                    Text("Note").foregroundStyle(.secondary)
                    Text("This copy is newer than the latest release, so App Depot leaves it alone.")
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .font(.callout)
    }

    private var latestText: String {
        guard let l = row.app.latest else { return "No release yet" }
        return [l.version, Format.date(l.publishedAt), Format.bytes(l.size)].compactMap { $0 }.joined(separator: " · ")
    }

    @ViewBuilder private var notes: some View {
        if let notes = row.app.latest?.notes, !parseReleaseNotes(notes).isEmpty {
            VStack(alignment: .leading, spacing: Theme.Spacing.s) {
                Text("What's New in \(row.app.latest!.version)").font(.title3.bold())
                NotesView(items: parseReleaseNotes(notes))
            }
        }
    }

    @ViewBuilder private var historySection: some View {
        let rows = (depot.history?.recent(limit: 20, appSlug: row.app.slug) ?? []).filter { $0.type != "depot_started" }
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: Theme.Spacing.s) {
                Text("History").font(.title3.bold())
                ForEach(rows) { HistoryLine(row: $0, showApp: false) }
            }
        }
    }
}

/// Release notes: headings and bullets only, everything else plain text (never interpreted).
struct NotesView: View {
    let items: [NoteItem]

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                switch item {
                case let .heading(t):
                    Text(verbatim: t).font(.headline).padding(.top, i > 0 ? Theme.Spacing.s : 0)
                case let .bullet(t):
                    HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.s) {
                        Text("•").foregroundStyle(Theme.accent)
                        Text(verbatim: t).fixedSize(horizontal: false, vertical: true)
                    }
                case let .text(t):
                    Text(verbatim: t).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .textSelection(.enabled)
    }
}
