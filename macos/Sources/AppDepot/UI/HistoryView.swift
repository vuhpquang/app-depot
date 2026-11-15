// UI/HistoryView.swift — every check, install, update and failure, newest first.
import DepotCore
import SwiftUI

struct HistoryLine: View {
    let row: HistoryRow
    let showApp: Bool
    @Environment(DepotModel.self) private var depot

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.s) {
            Image(systemName: icon).foregroundStyle(row.type == "install_failed" ? Color.red : Theme.accent).frame(width: 16)
            VStack(alignment: .leading, spacing: Theme.Spacing.xxs) {
                Text(title).font(.callout)
                if let message = row.message, row.type == "install_failed" {
                    Text(message).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer()
            Text(row.at.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary)
        }
    }

    private var appName: String {
        guard let slug = row.appSlug else { return "" }
        return depot.apps.first { $0.slug == slug }?.name ?? slug
    }

    private var title: String {
        let prefix = showApp && !appName.isEmpty ? "\(appName): " : ""
        switch row.type {
        case "check": return "Checked for updates" + (row.message.map { " · \($0)" } ?? "")
        case "install": return prefix + "Installed \(row.toVersion ?? "")"
        case "update": return prefix + "Updated \(row.fromVersion ?? "?") → \(row.toVersion ?? "?")"
        case "self_update": return "App Depot updated \(row.fromVersion ?? "?") → \(row.toVersion ?? "?")"
        case "install_failed": return prefix + "Couldn't install \(row.toVersion ?? "")"
        case "open": return prefix + "Opened"
        case "uninstall": return prefix + "Moved to the Trash"
        default: return prefix + row.type
        }
    }

    private var icon: String {
        switch row.type {
        case "check": return "arrow.clockwise"
        case "install", "update", "self_update": return "checkmark.circle"
        case "install_failed": return "exclamationmark.triangle"
        case "open": return "arrow.up.forward.app"
        case "uninstall": return "trash"
        default: return "circle"
        }
    }
}

struct HistorySheet: View {
    @Environment(DepotModel.self) private var depot
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let rows = (depot.history?.recent(limit: 300) ?? []).filter { $0.type != "depot_started" }
        VStack(alignment: .leading, spacing: 0) {
            Text("History").font(.title2.bold()).padding(Theme.Spacing.l)
            Divider()
            if rows.isEmpty {
                ContentUnavailableView("Nothing yet", systemImage: "clock",
                                       description: Text("Checks, installs and updates show up here."))
                    .frame(maxHeight: .infinity)
            } else {
                List(rows) { HistoryLine(row: $0, showApp: true) }
            }
            Divider()
            HStack {
                Spacer()
                Button("Done") { dismiss() }.keyboardShortcut(.defaultAction)
            }
            .padding(Theme.Spacing.m)
        }
        .frame(width: 520, height: 480)
    }
}
