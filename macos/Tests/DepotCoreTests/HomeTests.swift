import DepotCore
import Testing

private func row(_ a: CatalogApp, _ s: AppState, launchable: Bool = true) -> AppRow { AppRow(app: a, state: s, launchable: launchable) }

@Suite struct SearchTests {
    @Test func foldStripsVietnameseDiacriticsAndCase() {
        #expect(foldForSearch("Ghi chú cá nhân") == "ghi chu ca nhan")
        #expect(foldForSearch("Đường đi") == "duong di")
        #expect(foldForSearch("TIẾNG VIỆT") == "tieng viet")
    }
    @Test func blankQueryMatchesEverything() {
        #expect(matchesQuery(app(name: "ZenTask"), ""))
        #expect(matchesQuery(app(name: "ZenTask"), "   "))
    }
    @Test func matchesNameOrTaglineCaseInsensitive() {
        let z = app(name: "ZenTask", tagline: "Personal task manager")
        #expect(matchesQuery(z, "zen"))
        #expect(matchesQuery(z, "TASK MAN"))
        #expect(!matchesQuery(z, "wiki"))
    }
    @Test func matchesWithoutTypingAccents() {
        #expect(matchesQuery(app(name: "Sổ tay", tagline: "Ghi chú cá nhân"), "ghi chu"))
        #expect(matchesQuery(app(name: "Đường"), "duong"))
        #expect(matchesQuery(app(name: "Wiki", tagline: "ghi chu"), "ghi chú"))
    }
}

@Suite struct HomeListTests {
    @Test func updatesFirstThenByName() {
        let r = [row(app("beta"), .upToDate(installed: "1.0")), row(app("Zeta"), .updateAvailable(installed: "0.9", latest: rel())),
                 row(app("alpha"), .notInstalled), row(app("Mu"), .updateAvailable(installed: "0.9", latest: rel()))]
        #expect(sortRows(r, .updatesFirst).map(\.app.name) == ["Mu", "Zeta", "alpha", "beta"])
    }
    @Test func byNameIgnoresCaseAndAccents() {
        let r = [row(app("beta"), .notInstalled), row(app("Ánh"), .notInstalled), row(app("Zed"), .notInstalled)]
        #expect(sortRows(r, .name).map(\.app.name) == ["Ánh", "beta", "Zed"])
    }
    @Test func recentlyReleasedNewestFirstNoReleaseLast() {
        let r = [row(app("old", latest: rel(at: "2026-01-01T00:00:00Z")), .notInstalled), row(app("none", latest: nil), .noRelease(installed: nil)),
                 row(app("new", latest: rel(at: "2026-11-10T00:00:00Z")), .notInstalled)]
        #expect(sortRows(r, .recentlyReleased).map(\.app.name) == ["new", "old", "none"])
    }
    @Test func prefKeysRoundTripWithDefaults() {
        for o in SortOrder.allCases { #expect(SortOrder(key: o.rawValue) == o) }
        for l in HomeLayout.allCases { #expect(HomeLayout(key: l.rawValue) == l) }
        #expect(SortOrder(key: nil) == .updatesFirst)
        #expect(SortOrder(key: "bogus") == .updatesFirst)
        #expect(HomeLayout(key: nil) == .list)
        #expect(HomeLayout.grid.rawValue == "grid")
        #expect(HomeLayout.list.rawValue == "list")
        #expect(SortOrder.recentlyReleased.rawValue == "recent")
    }
    @Test func updateAllSizeSumsOnlyUpdates() {
        let r = [row(app("a"), .updateAvailable(installed: "0.9", latest: rel(size: 57_000_000))),
                 row(app("b"), .updateAvailable(installed: "0.9", latest: rel(size: 26_000_000))),
                 row(app("c"), .skipped(installed: "0.9", latest: rel(size: 99))),
                 row(app("d", latest: rel(size: 5)), .notInstalled)]
        #expect(updateAllBytes(r) == 83_000_000)
    }
    @Test func updateAllExcludesSelfWispSkippedAndLocal() {
        let r = [row(app("a"), .updateAvailable(installed: "0.9", latest: rel())),
                 row(app("wisp", mode: "selfUpdates"), .selfUpdates(installed: "0.1", latest: rel("9"))),
                 row(app("c"), .skipped(installed: "0.9", latest: rel())),
                 row(app("d"), .localBuild(installed: "9", latest: rel())),
                 row(app(selfSlug), .updateAvailable(installed: "0.1", latest: rel()))]
        #expect(updateAllSlugs(r) == ["a", selfSlug])
    }
    @Test func downloadBytesForInstallAndUpdateOnly() {
        #expect(downloadBytes(row(app("a"), .updateAvailable(installed: "0.9", latest: rel(size: 57)))) == 57)
        #expect(downloadBytes(row(app("d", latest: rel(size: 5)), .notInstalled)) == 5)
        #expect(downloadBytes(row(app("u"), .upToDate(installed: "1.0"))) == nil)
    }
    @Test func releasePagePublicAndPrivate() {
        #expect(releasePageURL(app(name: "ZenTask", repo: "vuhpquang/zentask")) == "https://github.com/vuhpquang/zentask/releases")
        let wiki = app("Wiki", latest: rel(url: "https://vuhpquang.com/api/depot/download/personal-wiki/0.1.11"), repo: "vuhpquang/personal-wiki")
        #expect(releasePageURL(wiki) == "https://vuhpquang.com/apps")
    }
    @Test func uninstallOnlyInstalledAndNeverSelf() {
        #expect(canUninstall(row(app("a"), .upToDate(installed: "1.0"))))
        #expect(canUninstall(row(app("a"), .updateAvailable(installed: "0.9", latest: rel()))))
        #expect(canUninstall(row(app("a"), .skipped(installed: "0.9", latest: rel()))))
        #expect(canUninstall(row(app("a"), .localBuild(installed: "2.0", latest: rel()))))
        #expect(canUninstall(row(app("w", mode: "selfUpdates"), .selfUpdates(installed: "0.3", latest: nil))))
        #expect(!canUninstall(row(app("a"), .notInstalled)))
        #expect(!canUninstall(row(app("a"), .noRelease(installed: nil))))
        #expect(!canUninstall(row(app(selfSlug), .upToDate(installed: "1.0"))))
    }
    @Test func shareTextHasNameAndLink() {
        #expect(shareText(app(name: "ZenTask", latest: rel())) == "ZenTask: https://github.com/v/x/a.zip")
        #expect(shareText(app(name: "None", latest: nil)) == nil)
    }
    @Test func iconFileNameIsStableHash() {
        let a = iconFileName("https://vuhpquang.com/app-icons/zentask.png")
        #expect(a == iconFileName("https://vuhpquang.com/app-icons/zentask.png"))
        #expect(a != iconFileName("https://vuhpquang.com/app-icons/padlet.png"))
        #expect(a.count == 68 && a.hasSuffix(".png") && a.dropLast(4).allSatisfy { "0123456789abcdef".contains($0) })
    }
    @Test func menuForInstalledAndNotInstalled() {
        #expect(menuActions(row(app("a"), .upToDate(installed: "1.0"))) == [.open, .uninstall, .share, .releasePage])
        #expect(menuActions(row(app("a"), .notInstalled, launchable: false)) == [.share, .releasePage])
        #expect(menuActions(row(app(selfSlug), .upToDate(installed: "1.0"))) == [.share, .releasePage])
    }
}

@Suite struct CardModelTests {
    private let r = rel("0.15.2")

    @Test func updateAvailableShowsUpdateAndBothVersions() {
        let m = cardModel(.updateAvailable(installed: "0.15.1", latest: r), progress: nil)
        #expect(m.action == .update && m.chip == .updateAvailable && m.installed == "0.15.1" && m.latest == "0.15.2")
    }
    @Test func localBuildShowsChipAndNoButton() {
        let m = cardModel(.localBuild(installed: "0.16.0", latest: r), progress: nil)
        #expect(m.chip == .localBuild && m.action == nil)
    }
    @Test func notInstalledShowsInstallAndLatest() {
        let m = cardModel(.notInstalled, progress: nil, latest: r)
        #expect(m.action == .install && m.chip == nil && m.installed == nil && m.latest == "0.15.2")
    }
    @Test func upToDateOpensOnlyWhenLaunchable() {
        #expect(cardModel(.upToDate(installed: "1"), progress: nil).action == .open)
        #expect(cardModel(.upToDate(installed: "1"), progress: nil, canLaunch: false).action == nil)
    }
    @Test func skippedShowsChipAndOpen() {
        let m = cardModel(.skipped(installed: "0.9", latest: rel("1.0")), progress: nil)
        #expect(m.chip == .skipped && m.action == .open && m.latest == "1.0")
    }
    @Test func noReleaseAndNotInstallableHaveNoButton() {
        #expect(cardModel(.noRelease(installed: nil), progress: nil).action == nil)
        #expect(cardModel(.noRelease(installed: nil), progress: nil).chip == .noRelease)
        #expect(cardModel(.notInstallable, progress: nil).action == nil)
        #expect(cardModel(.notInstallable, progress: nil).chip == .notInstallable)
    }
    @Test func selfUpdatesIsStatusPlusOpenOnly() {
        #expect(cardModel(.selfUpdates(installed: "1.0", latest: r), progress: nil).action == .open)
        #expect(cardModel(.selfUpdates(installed: "1.0", latest: r), progress: nil).chip == .selfUpdates)
        #expect(cardModel(.selfUpdates(installed: nil, latest: r), progress: nil).action == nil)
    }
    @Test func busyHidesButton() {
        let m = cardModel(.updateAvailable(installed: "0.15.1", latest: r), progress: .downloading(percent: 42))
        #expect(m.action == nil && m.activity == .downloading(percent: 42))
        #expect(cardModel(.notInstalled, progress: .queued).activity == .queued)
        #expect(cardModel(.notInstalled, progress: .verifying).activity == .verifying)
        #expect(cardModel(.notInstalled, progress: .installing).activity == .installing)
    }
    @Test func failedKeepsTheButtonAndShowsTheMessage() {
        let m = cardModel(.notInstalled, progress: .failed("The download failed."))
        #expect(m.action == .install && m.error == "The download failed." && m.activity == nil)
    }
}

@Suite struct ReleaseNotesTests {
    @Test func parsesHeadingsAndBulletsInOrder() {
        #expect(parseReleaseNotes("## What's new\n- A\n- B\n## Fixes\n- C") ==
                [.heading("What's new"), .bullet("A"), .bullet("B"), .heading("Fixes"), .bullet("C")])
    }
    @Test func returnsHtmlAsPlainTextNeverParsed() {
        #expect(parseReleaseNotes("<script>alert(1)</script>") == [.text("<script>alert(1)</script>")])
        #expect(parseReleaseNotes("- <b>x</b>") == [.bullet("<b>x</b>")])
    }
    @Test func skipsBlankLinesAndHandlesCrlfAndStarBullets() {
        #expect(parseReleaseNotes("\r\n## H\r\n\r\n* one\r\nplain\r\n") == [.heading("H"), .bullet("one"), .text("plain")])
    }
    @Test func emptyNotesGiveNothing() { #expect(parseReleaseNotes("  \n\n").isEmpty) }
}
