// UI/AppRow.swift — one app in the list layout and in the grid layout, plus its context menu.
import DepotCore
import SwiftUI

struct AppListRow: View {
    let row: AppRow
    @Environment(DepotModel.self) private var depot

    var body: some View {
        let m = depot.card(row)
        HStack(spacing: Theme.Spacing.m) {
            AppIconView(app: row.app, size: Theme.Size.rowIcon)
            VStack(alignment: .leading, spacing: Theme.Spacing.xxs) {
                Text(row.app.name).font(.headline).lineLimit(1)
                if let error = m.error {
                    Text(error).font(.callout).foregroundStyle(.red).lineLimit(2)
                } else {
                    HStack(spacing: Theme.Spacing.s - 2) {
                        if let chip = m.chip { ChipView(chip: chip) }
                        Text(subtitle(m)).font(.callout).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
            .layoutPriority(1)
            Spacer(minLength: Theme.Spacing.s)
            PrimaryActionView(row: row, model: m, compact: true)
        }
        .padding(.vertical, Theme.Spacing.xs)
        .contentShape(Rectangle())
        .contextMenu { AppContextMenu(row: row) }
        .accessibilityElement(children: .contain)
    }

    private func subtitle(_ m: CardModel) -> String {
        let v = versionLine(m)
        return v.isEmpty ? row.app.tagline : v
    }
}

struct AppGridCard: View {
    let row: AppRow
    let selected: Bool
    @Environment(DepotModel.self) private var depot

    var body: some View {
        let m = depot.card(row)
        VStack(spacing: Theme.Spacing.s) {
            AppIconView(app: row.app, size: Theme.Size.gridIcon)
            Text(row.app.name).font(.headline).lineLimit(1)
            Group {
                if let chip = m.chip { ChipView(chip: chip) } else { Text(versionLine(m)).font(.caption).foregroundStyle(.secondary) }
            }
            .frame(height: 20)
            Spacer(minLength: 0)
            PrimaryActionView(row: row, model: m, compact: true)
                .frame(height: 24)
        }
        .padding(Theme.Spacing.m)
        .frame(width: Theme.Size.gridCardWidth, height: Theme.Size.gridCardHeight)
        .background(Theme.Fill.card, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous)
                .strokeBorder(selected ? Theme.accent : Theme.Fill.separator, lineWidth: selected ? 2 : 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
        .contextMenu { AppContextMenu(row: row) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(row.app.name)
    }
}

struct AppContextMenu: View {
    let row: AppRow
    @Environment(DepotModel.self) private var depot
    @Environment(\.openURL) private var openURL

    var body: some View {
        ForEach(menuActions(row), id: \.self) { action in
            switch action {
            case .open:
                Button("Open \(row.app.name)") { depot.open(row.app) }
            case .uninstall:
                Button("Move to Trash…") { depot.confirmUninstall = row.app }
            case .share:
                if let url = depot.shareURL(row.app) {
                    ShareLink(item: url, subject: Text(row.app.name), message: Text(shareText(row.app) ?? row.app.name)) {
                        Text("Share Link…")
                    }
                }
            case .releasePage:
                Button("Open Release Page") { if let url = depot.releasePage(row.app) { openURL(url) } }
            }
        }
        if case .updateAvailable = row.state, ReplaceGuard.canInstall(row.app, state: row.state) {
            Divider()
            Button("Skip This Version") { depot.skip(row.app) }
        }
        if case .skipped = row.state {
            Divider()
            Button("Undo Skip") { depot.undoSkip(row.app) }
        }
    }
}
