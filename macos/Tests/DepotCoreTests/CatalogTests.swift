import DepotCore
import Foundation
import Testing

@Suite struct CatalogParserTests {
    @Test func parsesLiveSample() throws {
        let apps = try CatalogParser.parse(Data(catalogMacSample.utf8))
        #expect(apps.count == 6)
        let z = try #require(apps.first { $0.slug == "zentask-macos" })
        #expect(z.latest?.version == "0.15.3")
        #expect(z.appId == "com.vuhpquang.zentask")
        #expect(z.installMode == "managed")
        #expect(z.latest?.size == 8_054_010)
        let launch = try #require(apps.first { $0.slug == "launch-app" })
        #expect(launch.relaunchArgs == ["--background"])
        let devkit = try #require(apps.first { $0.slug == "devkit" })
        #expect(devkit.latest == nil)
        #expect(devkit.signer == nil)
        #expect(apps.first { $0.slug == "wisp" }?.installMode == "selfUpdates")
    }

    @Test func malformedFails() {
        #expect(throws: (any Error).self) { try CatalogParser.parse(Data("{not json".utf8)) }
        #expect(throws: (any Error).self) { try CatalogParser.parse(Data(#"{"data":null}"#.utf8)) }
    }

    @Test func unknownKeysIgnoredAndDefaults() throws {
        let j = #"{"data":{"apps":[{"slug":"a","name":"A","tagline":"t","platform":"macos","appId":"x.y","repo":"o/r","extra":1,"latest":null}]},"zzz":true}"#
        let apps = try CatalogParser.parse(Data(j.utf8))
        #expect(apps.count == 1)
        #expect(apps[0].latest == nil)
        #expect(apps[0].relaunchArgs == [])
        #expect(apps[0].installMode == "managed")
    }
}

@Suite struct AppPlanTests {
    @Test func notInstalled() { #expect(planFor(app(), installedVersion: nil) == .notInstalled) }
    @Test func updateAvailable() {
        guard case let .updateAvailable(installed, latest) = planFor(app(), installedVersion: "0.15.1") else {
            Issue.record("expected update"); return
        }
        #expect(installed == "0.15.1")
        #expect(latest.version == "0.15.2")
    }
    @Test func upToDate() { #expect(planFor(app(), installedVersion: "0.15.2") == .upToDate(installed: "0.15.2")) }
    @Test func localBuild() {
        if case .localBuild = planFor(app(), installedVersion: "0.15.3") {} else { Issue.record("expected local build") }
    }
    @Test func noRelease() { #expect(planFor(app(latest: nil), installedVersion: "1.0") == .noRelease(installed: "1.0")) }
    @Test func noReleaseNotInstalled() { #expect(planFor(app(latest: nil), installedVersion: nil) == .noRelease(installed: nil)) }
    @Test func selfUpdates() {
        guard case let .selfUpdates(installed, latest) = planFor(app(mode: "selfUpdates"), installedVersion: "0.1") else {
            Issue.record("expected selfUpdates"); return
        }
        #expect(installed == "0.1")
        #expect(latest?.version == "0.15.2")
    }
    @Test func selfUpdatesNeverUpdateAvailableEvenWhenOlder() {
        if case .selfUpdates = planFor(app(mode: "selfUpdates"), installedVersion: "0.0.1") {} else { Issue.record("wisp must stay selfUpdates") }
    }
    @Test func noneIsNotInstallable() { #expect(planFor(app(mode: "none"), installedVersion: nil) == .notInstallable) }
    @Test func noneWithoutReleaseIsNoRelease() {
        #expect(planFor(app(latest: nil, mode: "none"), installedVersion: nil) == .noRelease(installed: nil))
    }
}

@Suite struct SkipTests {
    @Test func skippedSameVersionIsSkipped() {
        guard case let .skipped(installed, latest) = planFor(app(), installedVersion: "0.15.1", skippedVersion: "0.15.2") else {
            Issue.record("expected skipped"); return
        }
        #expect(installed == "0.15.1")
        #expect(latest.version == "0.15.2")
    }
    @Test func newerVersionThanSkippedIsAnUpdateAgain() {
        if case .updateAvailable = planFor(app(latest: rel("0.15.3")), installedVersion: "0.15.1", skippedVersion: "0.15.2") {} else {
            Issue.record("expected update")
        }
    }
    @Test func skipDoesNotTouchOtherStates() {
        #expect(planFor(app(), installedVersion: "0.15.2", skippedVersion: "0.15.2") == .upToDate(installed: "0.15.2"))
        #expect(planFor(app(), installedVersion: nil, skippedVersion: "0.15.2") == .notInstalled)
    }
    @Test func skippedIsNotUpdatableInTheBackground() {
        let next: [(String, AppState)] = [("z", planFor(app("z"), installedVersion: "0.15.1", skippedVersion: "0.15.2"))]
        let plan = planBackground(told: [:], failed: [:], next: next, autoInstall: true, silentCapable: ["z"])
        #expect(plan.install.isEmpty)
        #expect(plan.announce.isEmpty)
        #expect(planBackground(told: [:], failed: [:], next: next, autoInstall: false, silentCapable: []).announce.isEmpty)
    }
    @Test func skippedMapRoundTrip() {
        #expect(parseSlugMap(formatSlugMap(["a": "1.0", "b": "2.1"])) == ["a": "1.0", "b": "2.1"])
        #expect(parseSlugMap(nil) == [:])
        #expect(parseSlugMap("=x\nnoequals\nk=v=w") == ["k": "v=w"])
    }
}

@Suite struct ReplaceGuardTests {
    @Test func onlyManagedInstallOrUpdate() {
        let managed = app()
        #expect(ReplaceGuard.canInstall(managed, state: .notInstalled))
        #expect(ReplaceGuard.canInstall(managed, state: .updateAvailable(installed: "0.1", latest: rel("0.15.2"))))
        #expect(!ReplaceGuard.canInstall(managed, state: .upToDate(installed: "0.15.2")))
        #expect(!ReplaceGuard.canInstall(managed, state: .localBuild(installed: "9", latest: rel())))
        #expect(!ReplaceGuard.canInstall(managed, state: .noRelease(installed: nil)))
        #expect(!ReplaceGuard.canInstall(managed, state: .skipped(installed: "0.1", latest: rel())))
    }
    @Test func wispAndDevKitNever() {
        let wisp = app("wisp", mode: "selfUpdates")
        #expect(!ReplaceGuard.canInstall(wisp, state: .selfUpdates(installed: "0.1", latest: rel("0.3"))))
        // Even a forged state can't open an install path for a selfUpdates / none app.
        #expect(!ReplaceGuard.canInstall(wisp, state: .updateAvailable(installed: "0.1", latest: rel("0.3"))))
        #expect(!ReplaceGuard.canInstall(wisp, state: .notInstalled))
        let devkit = app("devkit", latest: nil, mode: "none")
        #expect(!ReplaceGuard.canInstall(devkit, state: .notInstalled))
        #expect(!ReplaceGuard.canInstall(devkit, state: .notInstallable))
    }
    @Test func requirementChoice() {
        let a = app(signer: "identifier \"com.s\" and certificate leaf = H\"ab\"")
        #expect(ReplaceGuard.requirement(for: a, installedRequirement: "DR") == .success("DR"))
        #expect(ReplaceGuard.requirement(for: a, installedRequirement: nil) == .success("identifier \"com.s\" and certificate leaf = H\"ab\""))
        #expect(ReplaceGuard.requirement(for: app(signer: nil), installedRequirement: nil) == .failure(.noSigner))
        #expect(ReplaceGuard.noSignerMessage == "This app can't be installed safely yet.")
    }
}
