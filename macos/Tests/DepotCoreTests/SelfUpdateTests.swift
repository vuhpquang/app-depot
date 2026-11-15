import DepotCore
import Testing

@Suite struct SelfUpdateOrderTests {
    @Test func othersFirstThenSelf() {
        #expect(planSelfUpdate(updatable: ["padlet", selfSlug, "zentask-macos"]) == ["padlet", "zentask-macos", selfSlug])
        #expect(planSelfUpdate(updatable: []) == [selfSlug])
        #expect(planSelfUpdate(updatable: ["a", "a"]) == ["a", selfSlug])
    }

    @Test func selfMayStartOnlyWhenNothingElseIsBusy() {
        #expect(selfMayStart(progress: [:]))
        #expect(selfMayStart(progress: [selfSlug: .queued, "a": .done(version: "1"), "b": .failed("x")]))
        #expect(!selfMayStart(progress: ["a": .queued]))
        #expect(!selfMayStart(progress: ["a": .downloading(percent: 3)]))
        #expect(!selfMayStart(progress: ["a": .verifying]))
        #expect(!selfMayStart(progress: ["a": .installing, selfSlug: .queued]))
    }

    @Test func waitingLineCountsOtherBusyApps() {
        #expect(selfWaitingLine(progress: ["a": .queued, "b": .installing, selfSlug: .queued]) == "Updating 2 other apps first")
        #expect(selfWaitingLine(progress: ["a": .downloading(percent: 9), selfSlug: .queued]) == "Updating 1 other app first")
        #expect(selfWaitingLine(progress: [selfSlug: .queued]) == nil)
        #expect(selfWaitingLine(progress: ["a": .queued]) == nil, "only while App Depot itself is waiting")
    }
}
