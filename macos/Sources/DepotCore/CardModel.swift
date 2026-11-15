import Foundation

/// The primary button on a row; nil = no button.
public enum CardAction: Equatable, Sendable { case install, update, open }

/// The state chip; nil for a plain not-installed app (its "Latest X" line says enough).
public enum CardChip: Equatable, Sendable { case upToDate, updateAvailable, skipped, localBuild, noRelease, notInstallable, selfUpdates }

/// An install in flight: replaces the button with a progress line.
public enum CardActivity: Equatable, Sendable { case queued, downloading(percent: Int), verifying, installing }

public struct CardModel: Equatable, Sendable {
    public var chip: CardChip?
    public var installed: String?
    public var latest: String?
    public var action: CardAction?
    public var activity: CardActivity?
    public var error: String?
}

/// Pure mapping from the plan + the coordinator's progress to what a row shows.
/// `latest` is only needed for notInstalled / notInstallable (those states carry no release).
public func cardModel(_ state: AppState, progress: InstallProgress?, canLaunch: Bool = true,
                      latest: CatalogRelease? = nil) -> CardModel {
    let open: CardAction? = canLaunch ? .open : nil
    var m: CardModel
    switch state {
    case .notInstalled: m = CardModel(chip: nil, installed: nil, latest: latest?.version, action: .install)
    case let .upToDate(v): m = CardModel(chip: .upToDate, installed: v, latest: nil, action: open)
    case let .updateAvailable(v, l): m = CardModel(chip: .updateAvailable, installed: v, latest: l.version, action: .update)
    case let .skipped(v, l): m = CardModel(chip: .skipped, installed: v, latest: l.version, action: open)
    case let .localBuild(v, l): m = CardModel(chip: .localBuild, installed: v, latest: l.version, action: nil)
    case let .noRelease(v): m = CardModel(chip: .noRelease, installed: v, latest: nil, action: nil)
    case .notInstallable: m = CardModel(chip: .notInstallable, installed: nil, latest: latest?.version, action: nil)
    case let .selfUpdates(v, l): m = CardModel(chip: .selfUpdates, installed: v, latest: l?.version, action: v != nil ? open : nil)
    }
    switch progress {
    case .queued: m.activity = .queued
    case let .downloading(p): m.activity = .downloading(percent: p)
    case .verifying: m.activity = .verifying
    case .installing: m.activity = .installing
    case let .failed(msg): m.error = msg
    default: break
    }
    if m.activity != nil { m.action = nil }
    return m
}
