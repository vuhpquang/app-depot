import Foundation

/// What a background check does with its result. `notified` = slug → latest version the user has been
/// told about (or that is being installed); apps that are no longer updatable drop out.
public struct BackgroundDecision: Equatable, Sendable {
    public var notifySlugs: [String]
    public var installSlugs: [String]
    public var notified: [String: String]
}

/// `prev` = what the last run dealt with, `next` = the fresh result in catalog order. Only new updates
/// notify; with `autoInstall` every updatable app is installed (self last) and nothing is announced.
public func decide(prev: [String: AppState], next: [(String, AppState)], autoInstall: Bool) -> BackgroundDecision {
    let updatable = next.filter { $0.1.isUpdateAvailable }
    let fresh = updatable.filter { prev[$0.0]?.availableVersion != $0.1.availableVersion }.map(\.0)
    return BackgroundDecision(
        notifySlugs: autoInstall ? [] : fresh,
        installSlugs: autoInstall ? orderForUpdateAll(updatable.map(\.0)) : [],
        notified: Dictionary(updatable.map { ($0.0, $0.1.availableVersion!) }, uniquingKeysWith: { a, _ in a })
    )
}

public struct BackgroundPlan: Equatable, Sendable {
    public var announce: [String]
    public var install: [String]
    /// Latest versions to remember as told (the caller adjusts when a notification couldn't be shown).
    public var notified: [String: String]
}

/// `told` slug → version already announced, `failed` slug → version whose silent install failed (not
/// retried until a newer version), `silentCapable` apps App Depot may update now without the user,
/// `busy` slugs with an install state the user hasn't acted on.
public func planBackground(told: [String: String], failed: [String: String], next: [(String, AppState)],
                           autoInstall: Bool, silentCapable: Set<String>, busy: Set<String> = []) -> BackgroundPlan {
    let versions = Dictionary(next.map { ($0.0, $0.1.availableVersion) }, uniquingKeysWith: { a, _ in a })
    var prev: [String: AppState] = [:]
    for (slug, state) in next where state.availableVersion != nil && state.availableVersion == told[slug] { prev[slug] = state }
    let d = decide(prev: prev, next: next, autoInstall: autoInstall)
    let canInstall = d.installSlugs.filter {
        silentCapable.contains($0) && !busy.contains($0) && failed[$0] != versions[$0] ?? nil
    }
    let manual = d.installSlugs.filter { !silentCapable.contains($0) && prev[$0] == nil }
    return BackgroundPlan(announce: d.notifySlugs + manual, install: canInstall, notified: d.notified)
}

/// macOS: apps a background run may update without the user — managed, update available, and not running
/// (a background run never quits an app; a running one is announced instead). Wisp/DevKit never qualify.
public func silentCapableSlugs(next: [(String, AppState)], apps: [CatalogApp], running: Set<String>) -> Set<String> {
    let bySlug = Dictionary(apps.map { ($0.slug, $0) }, uniquingKeysWith: { a, _ in a })
    return Set(next.compactMap { slug, state in
        guard let app = bySlug[slug], ReplaceGuard.canInstall(app, state: state), state.isUpdateAvailable,
              !running.contains(slug) else { return nil }
        return slug
    })
}
