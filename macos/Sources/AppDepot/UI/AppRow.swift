// UI/AppRow.swift — one product in the list layout and in the grid layout, plus its context menu.
// A product with a Mac build behaves as before (its macOS row); one without shows "Not for Mac" and
// "Send to phone" instead of Install. Platform badges show when the filter is "All".
import DepotCore
import SwiftUI

struct AppListRow: View {
    let item: ProductRow
    @Environment(DepotModel.self) private var depot

    var body: some View {
        let m = depot.card(item)
        HStack(spacing: Theme.Spacing.m) {
            AppIconView(app: item.app, size: Theme.Size.rowIcon)
            VStack(alignment: .leading, spacing: Theme.Spacing.xxs) {
                if depot.platformFilter == .all {
                    // The name wins: badges drop out of a row too narrow for both.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: Theme.Spacing.s - 2) {
                            Text(item.app.name).font(.headline).lineLimit(1).fixedSize()
                            PlatformBadges(product: item.product)
                        }
                        Text(item.app.name).font(.headline).lineLimit(1)
                    }
                } else {
                    Text(item.app.name).font(.headline).lineLimit(1)
                }
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
            Group {
                if let row = item.row {
                    PrimaryActionView(row: row, model: m, compact: true)
                } else {
                    RemoteActionView(product: item.product, compact: true)
                }
            }
            .fixedSize()
        }
        .padding(.vertical, Theme.Spacing.xs)
        .contentShape(Rectangle())
        .contextMenu { ProductContextMenu(item: item) }
        .accessibilityElement(children: .contain)
    }

    private func subtitle(_ m: CardModel) -> String {
        if item.app.slug == selfSlug, let waiting = selfWaitingLine(progress: depot.coordinator.progress) { return waiting }
        let v = versionLine(m)
        return v.isEmpty ? item.app.tagline : v
    }
}

struct AppGridCard: View {
    let item: ProductRow
    let selected: Bool
    @Environment(DepotModel.self) private var depot

    var body: some View {
        let m = depot.card(item)
        VStack(spacing: Theme.Spacing.s) {
            AppIconView(app: item.app, size: Theme.Size.gridIcon)
            VStack(spacing: Theme.Spacing.xs) {
                Text(item.app.name).font(.headline).lineLimit(1)
                if depot.platformFilter == .all { PlatformBadges(product: item.product) }
            }
            Group {
                if let chip = m.chip { ChipView(chip: chip) } else { Text(versionLine(m)).font(.caption).foregroundStyle(.secondary) }
            }
            .frame(height: 20)
            Spacer(minLength: 0)
            Group {
                if let row = item.row {
                    PrimaryActionView(row: row, model: m, compact: true)
                } else {
                    RemoteActionView(product: item.product, compact: true)
                }
            }
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
        .contextMenu { ProductContextMenu(item: item) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(item.app.name)
    }
}

/// The macOS row's menu, or Send to phone / Open on the web for a product that isn't for this Mac.
struct ProductContextMenu: View {
    let item: ProductRow
    @Environment(DepotModel.self) private var depot
    @Environment(\.openURL) private var openURL

    var body: some View {
        if let row = item.row {
            AppContextMenu(row: row)
        } else {
            ForEach(remoteActions(item.product, local: macPlatform), id: \.self) { action in
                switch action {
                case .sendToPhone:
                    Button("Send to Phone…") { depot.sendToPhone = item.product }
                case let .openOnWeb(url):
                    Button("Open on the Web") { if let u = URL(string: url) { openURL(u) } }
                }
            }
        }
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
