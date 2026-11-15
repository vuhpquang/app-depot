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
        case .notOnThisDevice: return "Not for Mac"
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
                if !compact { Text(activityTitle(activity)).font(.callout).foregroundStyle(.secondary).monospacedDigit() }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(activityTitle(activity))
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

    /// App Depot waiting for the other updates says so instead of "Waiting…".
    private func activityTitle(_ a: CardActivity) -> String {
        if row.app.slug == selfSlug, let waiting = selfWaitingLine(progress: depot.coordinator.progress) { return waiting }
        return a.title
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

/// Capsule labels for the platforms a product ships on, this Mac's first ("macOS", "Android").
struct PlatformBadges: View {
    let product: Product

    var body: some View {
        HStack(spacing: Theme.Spacing.xs) {
            ForEach(product.platforms(local: macPlatform), id: \.self) { platform in
                Text(platformLabel(platform))
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, Theme.Spacing.s - 2)
                    .padding(.vertical, 1)
                    .overlay(Capsule().strokeBorder(Theme.Fill.separator))
                    .fixedSize()
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Available for " + product.platforms(local: macPlatform).map(platformLabel).joined(separator: " and "))
    }
}

/// The action of a product with no Mac build: "Send to phone" when there is an Android download.
struct RemoteActionView: View {
    let product: Product
    var compact = false
    @Environment(DepotModel.self) private var depot
    @Environment(\.openURL) private var openURL

    var body: some View {
        if case .sendToPhone = remoteActions(product, local: macPlatform).first {
            Button("Send to phone") { depot.sendToPhone = product }
                .buttonStyle(.bordered)
                .tint(Theme.accent)
                .controlSize(compact ? .small : .regular)
                .help("Show a QR code to install \(product.display.name) on your phone")
        } else if !compact, case let .openOnWeb(url)? = remoteActions(product, local: macPlatform).first, let u = URL(string: url) {
            Button("Open on the web") { openURL(u) }
                .buttonStyle(.bordered)
        }
    }
}
