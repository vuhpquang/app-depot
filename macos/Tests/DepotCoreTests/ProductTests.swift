import DepotCore
import Testing

private func entry(_ slug: String, _ platform: String, project: String? = nil, name: String? = nil, tagline: String = "",
                   latest: CatalogRelease? = rel("1.0")) -> CatalogApp {
    CatalogApp(slug: slug, name: name ?? slug, tagline: tagline, platform: platform, appId: "com.\(project ?? slug)",
               repo: "vuhpquang/\(slug)", projectSlug: project, latest: latest)
}

private let catalog = [
    entry("app-depot-android", "android", project: "app-depot", name: "App Depot"),
    entry("app-depot-macos", "macos", project: "app-depot", name: "App Depot"),
    entry("padlet", "macos", project: "padlet", name: "Padlet", tagline: "Trackpad edge controls"),
    entry("zentask-android", "android", project: "zentask", name: "ZenTask Mobile",
          latest: rel("0.15.3", url: "https://github.com/vuhpquang/zentask/releases/download/v0.15.3/ZenTask-0.15.3.apk")),
    entry("personal-wiki-android", "android", project: "personal-wiki", name: "Personal Wiki", tagline: "Ghi chú cá nhân",
          latest: rel("0.1.11", at: "2026-11-14T00:00:00Z", url: "https://vuhpquang.com/api/depot/download/personal-wiki-android")),
    entry("wisp", "macos", project: nil, name: "Wisp", latest: rel("0.3.2", at: "2026-11-07T00:00:00Z")),
    entry("zentask-macos", "macos", project: "zentask", name: "ZenTask"),
]

/// Plans every entry as on this Mac and records which ones were asked for.
private func products(installed: [String: String] = [:], asked: inout [String]) -> [ProductRow] {
    var seen: [String] = []
    let r = productRows(catalog, local: macPlatform) { a in
        seen.append(a.slug)
        return AppRow(app: a, state: planFor(a, installedVersion: installed[a.slug]), launchable: installed[a.slug] != nil)
    }
    asked = seen
    return r
}

private func products(installed: [String: String] = [:]) -> [ProductRow] {
    var asked: [String] = []
    return products(installed: installed, asked: &asked)
}

@Suite struct ProductGroupingTests {
    @Test func groupsByProjectSlugElseSlug() {
        let p = groupProducts(catalog, local: macPlatform)
        #expect(p.map(\.key) == ["app-depot", "padlet", "zentask", "personal-wiki", "wisp"])
        let zen = p.first { $0.key == "zentask" }!
        #expect(Set(zen.variants.keys) == ["android", "macos"])
    }

    @Test func displayNameComesFromTheLocalVariant() {
        let zen = groupProducts(catalog, local: macPlatform).first { $0.key == "zentask" }!
        #expect(zen.display.slug == "zentask-macos")
        #expect(zen.display.name == "ZenTask")
        let onPhone = groupProducts(catalog, local: "android").first { $0.key == "zentask" }!
        #expect(onPhone.display.name == "ZenTask Mobile")
    }

    @Test func blankProjectSlugFallsBackToSlug() {
        let p = groupProducts([entry("x", "macos", project: ""), entry("y", "macos", project: "")], local: macPlatform)
        #expect(p.map(\.key) == ["x", "y"])
    }

    @Test func platformsLocalFirst() {
        let zen = groupProducts(catalog, local: macPlatform).first { $0.key == "zentask" }!
        #expect(zen.platforms(local: macPlatform) == ["macos", "android"])
        #expect(zen.others(local: macPlatform).map(\.slug) == ["zentask-android"])
        #expect(platformLabel("macos") == "macOS")
        #expect(platformLabel("android") == "Android")
    }

    @Test func rowsArePlannedForMacVariantsOnly() {
        var asked: [String] = []
        let r = products(asked: &asked)
        #expect(asked.sorted() == ["app-depot-macos", "padlet", "wisp", "zentask-macos"])
        #expect(r.first { $0.product.key == "personal-wiki" }?.row == nil)
        #expect(localApps(catalog).allSatisfy { $0.platform == "macos" })
    }
}

@Suite struct PlatformFilterTests {
    @Test func keyRoundTripDefaultsToDevice() {
        #expect(PlatformFilter(key: nil) == .device)
        #expect(PlatformFilter(key: "bogus") == .device)
        #expect(PlatformFilter(key: "all") == .all)
        #expect(PlatformFilter.device.rawValue == "device")
    }

    @Test func countsDeviceAndAll() {
        #expect(filterCounts(products()) == FilterCounts(device: 4, all: 5))
    }

    @Test func deviceFilterHidesOtherPlatforms() {
        let s = homeSections(products(), filter: .device, sort: .name, query: "")
        #expect(s.local.map(\.app.name) == ["App Depot", "Padlet", "Wisp", "ZenTask"])
        #expect(s.other.isEmpty)
    }

    @Test func allShowsLocalFirstThenOthersEachSorted() {
        let s = homeSections(products(), filter: .all, sort: .name, query: "")
        #expect(s.local.map(\.app.name) == ["App Depot", "Padlet", "Wisp", "ZenTask"])
        #expect(s.other.map(\.app.name) == ["Personal Wiki"])
        let recent = homeSections(products(), filter: .all, sort: .recentlyReleased, query: "")
        #expect(recent.local.first?.app.name == "Wisp")
        #expect(recent.other.map(\.app.name) == ["Personal Wiki"])
    }

    @Test func updatesFirstUsesTheLocalVariantState() {
        let s = homeSections(products(installed: ["zentask-macos": "0.9", "padlet": "1.0"]), filter: .all, sort: .updatesFirst, query: "")
        #expect(s.local.first?.app.name == "ZenTask")
    }

    @Test func searchCoversEveryVariantOfAProduct() {
        let s = homeSections(products(), filter: .all, sort: .name, query: "ghi chu")
        #expect(s.local.isEmpty)
        #expect(s.other.map(\.app.name) == ["Personal Wiki"])
        #expect(homeSections(products(), filter: .device, sort: .name, query: "ghi chu").isEmpty)
        // "Mobile" is only in the Android entry's name, still finds the Mac card.
        #expect(homeSections(products(), filter: .device, sort: .name, query: "mobile").local.map(\.product.key) == ["zentask"])
    }
}

@Suite struct RemoteProductTests {
    @Test func notLocalProductNeverOffersInstall() {
        let wiki = products().first { $0.product.key == "personal-wiki" }!
        let m = wiki.model
        #expect(m.action == nil)
        #expect(m.chip == .notOnThisDevice)
        #expect(m.latest == "0.1.11")
        #expect(!wiki.isLocal)
        #expect(downloadBytes(wiki) == nil)
    }

    @Test func androidOnlyProductCanBeSentToThePhone() {
        let wiki = products().first { $0.product.key == "personal-wiki" }!
        #expect(remoteActions(wiki.product, local: macPlatform)
            == [.sendToPhone(url: "https://vuhpquang.com/api/depot/download/personal-wiki-android"), .openOnWeb(url: "https://vuhpquang.com/projects/personal-wiki")])
        #expect(remoteActions(groupProducts([entry("a", "android", latest: nil)], local: macPlatform)[0], local: macPlatform)
            == [.openOnWeb(url: "https://vuhpquang.com/projects/a")])
    }

    @Test func alsoOnLineForProductsOnBothPlatforms() {
        let p = groupProducts(catalog, local: macPlatform)
        #expect(alsoOnLine(p.first { $0.key == "zentask" }!, local: macPlatform) == "Also on Android · 0.15.3")
        #expect(alsoOnLine(p.first { $0.key == "padlet" }!, local: macPlatform) == nil)
        #expect(alsoOnLine(p.first { $0.key == "personal-wiki" }!, local: macPlatform) == nil)
    }

    @Test func replaceGuardRefusesOtherPlatforms() {
        let phone = catalog.first { $0.slug == "zentask-android" }!
        #expect(!ReplaceGuard.canInstall(phone, state: .notInstalled))
        #expect(!ReplaceGuard.canInstall(phone, state: .updateAvailable(installed: "0.1", latest: rel("0.15.3"))))
        #expect(ReplaceGuard.canInstall(catalog.first { $0.slug == "zentask-macos" }!, state: .notInstalled))
    }

    @Test func updateAllOnlyCoversMacVariants() {
        // Both App Depot entries share a bundle id; only the macOS one may be planned or updated.
        let r = products(installed: ["app-depot-macos": "0.9", "zentask-macos": "0.9"])
        let rows = r.compactMap(\.row)
        #expect(updateAllSlugs(rows).sorted() == ["app-depot-macos", "zentask-macos"].sorted())
        #expect(rows.allSatisfy { $0.app.platform == "macos" })
    }
}
