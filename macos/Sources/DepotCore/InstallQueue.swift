import Foundation

/// App Depot's own catalog slug: never installed by the queue, always last.
public let selfSlug = "app-depot-macos"

public enum InstallProgress: Equatable, Sendable {
    case queued
    case downloading(percent: Int)
    case verifying
    case installing
    case done(version: String)
    case failed(String)
}

public enum InstallOutcome: Equatable, Sendable {
    case success(version: String)
    case failed(String)
}

/// True while `p` means an install is queued or running for the slug, so a second request is a no-op.
public func isBusy(_ p: InstallProgress?) -> Bool {
    switch p {
    case .queued, .downloading, .verifying, .installing: return true
    default: return false
    }
}

/// The slugs that may start now: not already busy, no duplicates, order kept.
public func claimable(_ current: [String: InstallProgress], _ slugs: [String]) -> [String] {
    var seen = Set<String>()
    return slugs.filter { seen.insert($0).inserted && !isBusy(current[$0]) }
}

/// A version string made safe for a file name.
public func safeFileSegment(_ s: String) -> String {
    String(s.map { $0.isASCII && ($0.isLetter || $0.isNumber || "._-".contains($0)) ? $0 : "_" })
}

/// Self last, everything else in the given (catalog) order.
public func orderForUpdateAll(_ slugs: [String], selfSlug me: String = selfSlug) -> [String] {
    slugs.filter { $0 != me } + slugs.filter { $0 == me }
}

public func outcomeProgress(_ o: InstallOutcome) -> InstallProgress {
    switch o {
    case let .success(v): return .done(version: v)
    case let .failed(m): return .failed(m)
    }
}

/// Runs the apps one at a time, self last; a failed app never stops the rest.
@MainActor
public func runInstallQueue(_ apps: [CatalogApp], install: (CatalogApp) async -> InstallOutcome,
                            onProgress: (String, InstallProgress) -> Void) async -> [String: InstallOutcome] {
    let bySlug = Dictionary(apps.map { ($0.slug, $0) }, uniquingKeysWith: { a, _ in a })
    var seen = Set<String>()
    let order = orderForUpdateAll(apps.map(\.slug).filter { seen.insert($0).inserted })
    order.forEach { onProgress($0, .queued) }
    var results: [String: InstallOutcome] = [:]
    for slug in order {
        let outcome = await install(bySlug[slug]!)
        results[slug] = outcome
        onProgress(slug, outcomeProgress(outcome))
    }
    return results
}
