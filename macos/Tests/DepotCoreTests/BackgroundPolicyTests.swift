import DepotCore
import Testing

private func upd(_ v: String) -> AppState { .updateAvailable(installed: "0.1", latest: rel(v)) }

@Suite struct DecideTests {
    @Test func newUpdateNotifies() {
        let d = decide(prev: [:], next: [("a", upd("2"))], autoInstall: false)
        #expect(d.notifySlugs == ["a"])
        #expect(d.installSlugs.isEmpty)
    }
    @Test func sameUpdateAlreadyNotifiedIsSilent() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", upd("2"))], autoInstall: false).notifySlugs.isEmpty)
    }
    @Test func newerVersionOfNotifiedAppNotifiesAgain() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", upd("3"))], autoInstall: false).notifySlugs == ["a"])
    }
    @Test func onlyNewOnesNotify() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", upd("2")), ("b", upd("5"))], autoInstall: false).notifySlugs == ["b"])
    }
    @Test func autoInstallListsAllUpdatableSelfLast() {
        let next: [(String, AppState)] = [(selfSlug, upd("9")), ("a", upd("2")), ("b", .upToDate(installed: "1")), ("c", upd("4"))]
        let d = decide(prev: [:], next: next, autoInstall: true)
        #expect(d.installSlugs == ["a", "c", selfSlug])
        #expect(d.notifySlugs.isEmpty)
    }
    @Test func autoInstallRetriesAlreadyNotified() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", upd("2"))], autoInstall: true).installSlugs == ["a"])
    }
    @Test func nonUpdatableStatesNeverNotify() {
        let next: [(String, AppState)] = [
            ("a", .upToDate(installed: "1")), ("b", .notInstalled), ("c", .localBuild(installed: "9", latest: rel("1"))),
            ("d", .selfUpdates(installed: "1", latest: rel("2"))), ("e", .notInstallable), ("f", .noRelease(installed: "1")),
        ]
        let d = decide(prev: [:], next: next, autoInstall: true)
        #expect(d.notifySlugs.isEmpty)
        #expect(d.installSlugs.isEmpty)
    }
    @Test func nextStateKeptForRemaining() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", upd("2")), ("b", upd("3"))], autoInstall: false).notified == ["a": "2", "b": "3"])
    }
    @Test func resolvedUpdatesAreForgotten() {
        #expect(decide(prev: ["a": upd("2")], next: [("a", .upToDate(installed: "2"))], autoInstall: false).notified.isEmpty)
    }
}

@Suite struct PlanBackgroundTests {
    @Test func checkErrorMeansEmptyNextAndNothingHappens() {
        let p = planBackground(told: ["a": "2"], failed: [:], next: [], autoInstall: true, silentCapable: ["a"])
        #expect(p.announce.isEmpty)
        #expect(p.install.isEmpty)
    }
    @Test func failedVersionIsNotRetriedUntilNewer() {
        let failed = ["a": "2"]
        #expect(planBackground(told: [:], failed: failed, next: [("a", upd("2"))], autoInstall: true, silentCapable: ["a"]).install.isEmpty)
        #expect(planBackground(told: [:], failed: failed, next: [("a", upd("3"))], autoInstall: true, silentCapable: ["a"]).install == ["a"])
    }
    @Test func busySlugIsSkipped() {
        let p = planBackground(told: [:], failed: [:], next: [("a", upd("2")), ("b", upd("2"))], autoInstall: true,
                               silentCapable: ["a", "b"], busy: ["a"])
        #expect(p.install == ["b"])
    }
    @Test func nonSilentAutoInstallAnnouncesOnce() {
        let next: [(String, AppState)] = [("a", upd("2"))]
        #expect(planBackground(told: [:], failed: [:], next: next, autoInstall: true, silentCapable: []).announce == ["a"])
        #expect(planBackground(told: ["a": "2"], failed: [:], next: next, autoInstall: true, silentCapable: []).announce.isEmpty)
    }
    @Test func runningAppIsNotifiedNotInstalled() {
        // macOS: a running app is not silent-capable (background never quits an app) → announced instead.
        let next: [(String, AppState)] = [("padlet", upd("2.6")), ("zentask-macos", upd("0.16"))]
        let silent = silentCapableSlugs(next: next, apps: [app("padlet"), app("zentask-macos")], running: ["padlet"])
        #expect(silent == ["zentask-macos"])
        let p = planBackground(told: [:], failed: [:], next: next, autoInstall: true, silentCapable: silent)
        #expect(p.install == ["zentask-macos"])
        #expect(p.announce == ["padlet"])
    }
    @Test func wispIsNeverInstalled() {
        let wisp = app("wisp", mode: "selfUpdates")
        let next: [(String, AppState)] = [("wisp", planFor(wisp, installedVersion: "0.0.1"))]
        let silent = silentCapableSlugs(next: next, apps: [wisp], running: [])
        #expect(silent.isEmpty)
        let p = planBackground(told: [:], failed: [:], next: next, autoInstall: true, silentCapable: ["wisp"])
        #expect(p.install.isEmpty)
        #expect(p.announce.isEmpty)
        // Even a forged updateAvailable state for Wisp is filtered out of silent installs.
        #expect(silentCapableSlugs(next: [("wisp", upd("9"))], apps: [wisp], running: []).isEmpty)
    }
}
