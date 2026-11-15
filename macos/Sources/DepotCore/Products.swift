import Foundation

/// This Mac's platform in the catalog. Install, update, uninstall, skip, history and background runs only
/// ever touch these entries.
public let macPlatform = "macos"

/// The catalog entries this Mac can install. Entries of other platforms may share an app id (App Depot
/// macOS and Android are both `com.vuhpquang.appdepot`), so never look one up by app id alone.
public func localApps(_ apps: [CatalogApp], platform: String = macPlatform) -> [CatalogApp] {
    apps.filter { $0.platform == platform }
}

/// "macOS", "Android"; anything else as the catalog spells it.
public func platformLabel(_ platform: String) -> String {
    switch platform {
    case "macos": return "macOS"
    case "android": return "Android"
    default: return platform
    }
}

/// One app across platforms: catalog entries grouped by `projectSlug` (else `slug`).
/// `display` (name, tagline, icon) is this platform's entry when there is one, else the first entry.
public struct Product: Equatable, Sendable, Identifiable {
    public var key: String
    public var display: CatalogApp
    public var variants: [String: CatalogApp]
    public var id: String { key }

    public init(key: String, display: CatalogApp, variants: [String: CatalogApp]) {
        self.key = key
        self.display = display
        self.variants = variants
    }

    /// Platforms this app ships on, `local` first, the rest alphabetically.
    public func platforms(local: String) -> [String] {
        variants.keys.sorted { a, b in (a == local) != (b == local) ? a == local : a < b }
    }

    /// The entries for other platforms, in `platforms` order.
    public func others(local: String) -> [CatalogApp] {
        platforms(local: local).filter { $0 != local }.compactMap { variants[$0] }
    }
}

/// Products in the order their first entry appears in the catalog.
public func groupProducts(_ apps: [CatalogApp], local: String = macPlatform) -> [Product] {
    var order: [String] = []
    var groups: [String: [CatalogApp]] = [:]
    for app in apps {
        let key = app.projectSlug.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 } ?? app.slug
        if groups[key] == nil { order.append(key) }
        groups[key, default: []].append(app)
    }
    return order.map { key in
        let entries = groups[key]!
        var variants: [String: CatalogApp] = [:]
        // A second entry for the same platform would be a catalog mistake: the first one wins.
        for e in entries where variants[e.platform] == nil { variants[e.platform] = e }
        return Product(key: key, display: variants[local] ?? entries[0], variants: variants)
    }
}

/// Search over a product: any of its entries' name or tagline.
public func matchesQuery(_ product: Product, _ query: String) -> Bool {
    matchesQuery(product.display, query) || product.variants.values.contains { matchesQuery($0, query) }
}

/// Home platform filter, persisted as `appdepot.ui.platformFilter`.
public enum PlatformFilter: String, CaseIterable, Sendable {
    case device, all
    public init(key: String?) { self = key.flatMap(PlatformFilter.init(rawValue:)) ?? .device }
}

/// A Home row: one product, plus the planned row of its macOS variant (nil = not for this Mac).
public struct ProductRow: Equatable, Sendable, Identifiable {
    public var product: Product
    public var row: AppRow?
    public var id: String { product.key }

    public init(product: Product, row: AppRow?) {
        self.product = product
        self.row = row
    }

    /// The entry the row shows: this Mac's variant, else the product's display entry.
    public var app: CatalogApp { row?.app ?? product.display }
    public var isLocal: Bool { row != nil }
    /// The card for a product with no Mac build (local rows get theirs from `cardModel` with progress).
    public var model: CardModel { remoteCardModel(product) }
}

/// Groups the full catalog into products; `rowFor` is called for this platform's entries only.
public func productRows(_ apps: [CatalogApp], local: String = macPlatform, rowFor: (CatalogApp) -> AppRow) -> [ProductRow] {
    groupProducts(apps, local: local).map { p in ProductRow(product: p, row: p.variants[local].map(rowFor)) }
}

public struct FilterCounts: Equatable, Sendable {
    public var device: Int
    public var all: Int
    public init(device: Int, all: Int) { self.device = device; self.all = all }
}

public func filterCounts(_ products: [ProductRow]) -> FilterCounts {
    FilterCounts(device: products.filter(\.isLocal).count, all: products.count)
}

/// What Home lists: this Mac's products, then (filter All only) the ones for other platforms.
public struct HomeSections: Equatable, Sendable {
    public var local: [ProductRow]
    public var other: [ProductRow]
    public var isEmpty: Bool { local.isEmpty && other.isEmpty }
    public var all: [ProductRow] { local + other }
}

public func homeSections(_ products: [ProductRow], filter: PlatformFilter, sort: SortOrder, query: String) -> HomeSections {
    let found = products.filter { matchesQuery($0.product, query) }
    return HomeSections(local: sortProducts(found.filter(\.isLocal), sort),
                        other: filter == .all ? sortProducts(found.filter { !$0.isLocal }, sort) : [])
}

public func sortProducts(_ rows: [ProductRow], _ order: SortOrder) -> [ProductRow] {
    sortItems(rows, order, app: \.app, state: { $0.row?.state })
}

/// A product with no build for this Mac: shown, never installable.
public func remoteCardModel(_ product: Product) -> CardModel {
    CardModel(chip: .notOnThisDevice, installed: nil, latest: product.display.latest?.version, action: nil)
}

/// Nothing to download for a product that isn't for this Mac.
public func downloadBytes(_ p: ProductRow) -> Int64? { p.row.flatMap(downloadBytes) }

/// What a product with no Mac build offers instead of Install.
public enum RemoteAction: Equatable, Hashable, Sendable {
    /// A QR code of the Android download link, to scan with the phone.
    case sendToPhone(url: String)
    case openOnWeb(url: String)
}

/// The product's page on vuhpquang.com (every catalog project has one).
public func productWebURL(_ product: Product) -> String { "https://vuhpquang.com/projects/\(product.key)" }

public func remoteActions(_ product: Product, local: String) -> [RemoteAction] {
    var actions: [RemoteAction] = []
    if local != "android", let url = product.variants["android"]?.latest?.downloadUrl, !url.isEmpty {
        actions.append(.sendToPhone(url: url))
    }
    actions.append(.openOnWeb(url: productWebURL(product)))
    return actions
}

/// "Also on Android · 0.15.3" for a product this Mac has that also ships elsewhere; nil otherwise.
public func alsoOnLine(_ product: Product, local: String) -> String? {
    guard product.variants[local] != nil else { return nil }
    let parts = product.others(local: local).map { a in
        [platformLabel(a.platform), a.latest?.version].compactMap { $0 }.joined(separator: " · ")
    }
    return parts.isEmpty ? nil : "Also on " + parts.joined(separator: ", ")
}
