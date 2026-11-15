// UI/Components.swift — small shared pieces: state chip, action button, progress line, size text.
import DepotCore
import SwiftUI

enum Format {
    static func bytes(_ n: Int64) -> String { ByteCountFormatter.string(fromByteCount: n, countStyle: .file) }

    static func date(_ iso: String) -> String? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let d = f.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        return d?.formatted(date: .abbreviated, time: .omitted)
    }
}

extension CardChip {
    var title: String {
        switch self {
        case .upToDate: return "Up to date"
        case .updateAvailable: return "Update available"
        case .skipped: return "Skipped"
        case .localBuild: return "Local build"
        case .noRelease: return "No release yet"
        case .notInstallable: return "Not available here"
        case .selfUpdates: return "Updates itself"
        }
    }

    var tint: Color {
        switch self {
        case .updateAvailable: return Theme.accent
        case .localBuild: return .orange
        default: return .secondary
        }
    }
}

struct ChipView: View {
    let chip: CardChip

    var body: some View {
        Text(chip.title)
            .font(.caption.weight(.medium))
            .foregroundStyle(chip == .updateAvailable ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(.secondary))
            .padding(.horizontal, Theme.Spacing.s - 2)
            .padding(.vertical, Theme.Spacing.xxs)
            .background(chip.tint.opacity(chip == .updateAvailable ? 0.14 : 0.12),
                        in: RoundedRectangle(cornerRadius: Theme.Radius.chip, style: .continuous))
            .fixedSize()
    }
}

/// "Installed 1.4.0 · Latest 1.5.1" style line.
func versionLine(_ m: CardModel) -> String {
    switch (m.installed, m.latest) {
    case let (i?, l?) where i != l: return "\(i) → \(l)"
    case let (i?, _): return "Version \(i)"
    case let (nil, l?): return "Latest \(l)"
    default: return ""
    }
}

extension CardActivity {
    var title: String {
        switch self {
        case .queued: return "Waiting…"
        case let .downloading(p): return "Downloading \(p)%"
        case .verifying: return "Checking…"
        case .installing: return "Installing…"
        }
    }

    var fraction: Double? {
        if case let .downloading(p) = self { return Double(p) / 100 }
        return nil
    }
}

/// The primary action of a row or card (or its progress while busy).
struct PrimaryActionView: View {
    let row: AppRow
    let model: CardModel
    @Environment(DepotModel.self) private var depot
    var compact = false

    var body: some View {
        if let activity = model.activity {
            HStack(spacing: Theme.Spacing.s) {
                if let f = activity.fraction {
                    ProgressView(value: f).frame(width: compact ? 56 : 72)
                } else {
                    ProgressView().controlSize(.small)
                }
                if !compact { Text(activity.title).font(.callout).foregroundStyle(.secondary).monospacedDigit() }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(activity.title)
        } else if let action = model.action {
            Button {
                switch action {
                case .install, .update: depot.install(row.app)
                case .open: depot.open(row.app)
                }
            } label: {
                Text(title(action))
            }
            .buttonStyle(.bordered)
            .tint(action == .open ? nil : Theme.accent)
            .controlSize(compact ? .small : .regular)
            .help(help(action))
        }
    }

    private func title(_ a: CardAction) -> String {
        switch a {
        case .install: return "Install"
        case .update: return "Update"
        case .open: return "Open"
        }
    }

    private func help(_ a: CardAction) -> String {
        switch a {
        case .install, .update: return downloadBytes(row).map { "Download \(Format.bytes($0))" } ?? ""
        case .open: return "Open \(row.app.name)"
        }
    }
}
