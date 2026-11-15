import DepotCore
import Testing

@MainActor
@Suite struct InstallQueueTests {
    @Test func selfGoesLast() {
        #expect(orderForUpdateAll(["app-depot-macos", "zentask-macos", "padlet"]) == ["zentask-macos", "padlet", "app-depot-macos"])
        #expect(selfSlug == "app-depot-macos")
    }

    @Test func failedAppDoesNotStopTheQueue() async {
        var ran: [String] = []
        var events: [(String, InstallProgress)] = []
        let results = await runInstallQueue([app("a"), app("b"), app("c")], install: { a in
            ran.append(a.slug)
            return a.slug == "b" ? .failed("boom") : .success(version: "1.0")
        }, onProgress: { events.append(($0, $1)) })
        #expect(ran == ["a", "b", "c"])
        #expect(results["b"] == .failed("boom"))
        #expect(events.last { $0.0 == "c" }?.1 == .done(version: "1.0"))
        #expect(events.last { $0.0 == "b" }?.1 == .failed("boom"))
    }

    @Test func selfRunsLastEvenWhenListedFirst() async {
        var ran: [String] = []
        _ = await runInstallQueue([app("app-depot-macos"), app("z")], install: { a in
            ran.append(a.slug); return .success(version: "1")
        }, onProgress: { _, _ in })
        #expect(ran == ["z", "app-depot-macos"])
    }

    @Test func everythingQueuedFirst() async {
        var events: [(String, InstallProgress)] = []
        _ = await runInstallQueue([app("a"), app("b")], install: { _ in .success(version: "1") },
                                  onProgress: { events.append(($0, $1)) })
        #expect(events.prefix(2).map(\.1) == [.queued, .queued])
    }

    @Test func secondRequestForBusySlugIsIgnored() {
        let current: [String: InstallProgress] = ["a": .downloading(percent: 10), "b": .queued, "c": .failed("x")]
        #expect(claimable(current, ["a", "b", "c", "d", "d"]) == ["c", "d"])
    }

    @Test func busyStates() {
        #expect(isBusy(.queued) && isBusy(.downloading(percent: 1)) && isBusy(.verifying) && isBusy(.installing))
        #expect(!isBusy(nil) && !isBusy(.done(version: "1")) && !isBusy(.failed("x")))
    }

    @Test func claimedSlugsRunOnceThroughTheQueue() async {
        var ran: [String] = []
        var current: [String: InstallProgress] = [:]
        let apps = [app("a"), app("b")]
        let first = claimable(current, apps.map(\.slug))
        first.forEach { current[$0] = .queued }
        let second = claimable(current, apps.map(\.slug))
        let results = await runInstallQueue(apps.filter { first.contains($0.slug) }, install: { a in
            ran.append(a.slug); return .success(version: "1")
        }, onProgress: { _, _ in })
        #expect(second.isEmpty)
        #expect(ran == ["a", "b"])
        #expect(Set(results.keys) == ["a", "b"])
    }

    @Test func versionIsMadeFileNameSafe() {
        #expect(safeFileSegment("1.2.3-beta+1") == "1.2.3-beta_1")
        #expect(safeFileSegment("../../etc/passwd") == ".._.._etc_passwd")
    }
}
