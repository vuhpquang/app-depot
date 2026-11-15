import AppShellCore
import Testing
@testable import AppDepot

@Suite struct SmokeTests {
    @Test func identity() {
        #expect(AppIdentity.appDepot.name == "App Depot")
        #expect(AppIdentity.appDepot.repo == "vuhpquang/app-depot")
        #expect(DepotEndpoints.selfSlug == "app-depot-macos")
    }
}
