import DepotCore
import Foundation
import Testing
@testable import AppDepot

@MainActor
final class RecordingNotifier: Notifying {
    var available: [[String]] = []
    var updated: [(String, String)] = []
    var needsYou: [[String]] = []
    var canPost = true
    func updatesAvailable(_ names: [String]) async -> Bool { available.append(names); return canPost }
    func updated(_ name: String, version: String) async { updated.append((name, version)) }
    func finishUpdating(_ names: [String]) async -> Bool { needsYou.append(names); return canPost }
}

@MainActor
@Suite(.serialized) struct BackgroundRunTests {
    let tmp: TempDir
    let installer = FakeInstaller()
    let notifier = RecordingNotifier()
    let defaults = UserDefaults(suiteName: "appdepot.bg.\(UUID())")!

    init() throws { tmp = try TempDir() }

    func entry(_ slug: String, name: String, mode: String = "managed", latest: String = "2.0.0") -> CatalogApp {
        CatalogApp(slug: slug, name: name, tagline: "", platform: "macos", appId: "com.test.\(slug)", repo: "v/\(slug)",
                   signer: "identifier \"com.test.\(slug)\"", installMode: mode,
                   latest: CatalogRelease(version: latest, assetName: "a.zip", downloadUrl: "https://x/a.zip", sha256: "s", size: 10, publishedAt: "t"))
    }

    func make(apps: [CatalogApp], running: Set<String> = [], autoInstall: Bool, active: Bool = false,
              selfUpdates: Ref<Int> = Ref(0)) throws -> BackgroundRun {
        for a in apps where a.slug != selfSlug {
            _ = try makeBundle(in: tmp.url, name: a.name, bundleId: a.appId, version: "1.0.0")
        }
        let location = InstallLocation(root: tmp.url)
        let coordinator = InstallCoordinator(installer: installer, location: location, events: nil)
        let model = DepotModel(coordinator: coordinator, location: location, history: nil, control: FakeControl(),
                               defaults: defaults, catalogCache: nil, selfVersion: "0.1.0",
                               fetch: { (Data(), apps) })
        return BackgroundRun(model: model, notifier: notifier, defaults: defaults,
                             autoCheck: { true }, autoInstall: { autoInstall }, isAppActive: { active },
                             isRunning: { running.contains($0.slug) },
                             selfUpdate: { selfUpdates.value += 1 })
    }

    @Test func notifiesOnceWithoutAutoInstall() async throws {
        let run = try make(apps: [entry("a", name: "Alpha"), entry("b", name: "Beta")], autoInstall: false)
        await run.run()
        #expect(notifier.available == [["Alpha", "Beta"]])
        #expect(installer.calls.isEmpty)
        await run.run()
        #expect(notifier.available.count == 1, "the same versions aren't announced twice")
    }

    @Test func autoInstallUpdatesQuietAppsAndAnnouncesRunningOnes() async throws {
        let run = try make(apps: [entry("a", name: "Alpha"), entry("b", name: "Beta")], running: ["a"], autoInstall: true)
        await run.run()
        #expect(installer.calls == ["b"], "a running app is never quit by a background run")
        #expect(notifier.available == [["Alpha"]])
        #expect(notifier.updated.map(\.0) == ["Beta"])
    }

    @Test func wispIsNeverInstalledInTheBackground() async throws {
        let run = try make(apps: [entry("wisp", name: "Wisp", mode: "selfUpdates")], autoInstall: true)
        await run.run()
        #expect(installer.calls.isEmpty)
        #expect(notifier.available.isEmpty)
    }

    @Test func failedSilentInstallIsNotRetriedUntilANewerVersion() async throws {
        installer.results["b"] = .failed("The download failed. Check the connection and try again.")
        let run = try make(apps: [entry("b", name: "Beta")], autoInstall: true)
        await run.run()
        await run.run()
        #expect(installer.calls == ["b"])
    }

    @Test func selfUpdateRunsLastAndNotWhileTheUserIsInTheApp() async throws {
        let count = Ref(0)
        let apps = [entry(selfSlug, name: "App Depot", latest: "0.2.0"), entry("b", name: "Beta")]
        let busyRun = try make(apps: apps, autoInstall: true, active: true, selfUpdates: count)
        await busyRun.run()
        #expect(installer.calls == ["b"])
        #expect(count.value == 0)
        let idle = try BackgroundRunTests().make(apps: apps, autoInstall: true, active: false, selfUpdates: count)
        await idle.run()
        #expect(count.value == 1)
    }

    @Test func otherPlatformEntriesAreNeverPlannedInstalledOrAnnounced() async throws {
        // Same bundle id as the installed Mac app (App Depot macOS and Android share one too).
        var phone = entry("a-android", name: "Alpha Phone")
        phone.platform = "android"
        phone.appId = "com.test.a"
        phone.projectSlug = "a"
        var mac = entry("a", name: "Alpha")
        mac.projectSlug = "a"
        let run = try make(apps: [phone, mac, entry("b", name: "Beta")], autoInstall: true)
        await run.run()
        #expect(installer.calls.sorted() == ["a", "b"])
        #expect(!notifier.updated.map(\.0).contains("Alpha Phone"))
        let model = run.model
        #expect(model.allApps.count == 3, "the full catalog is kept")
        #expect(model.rows.allSatisfy { $0.app.platform == "macos" })
        #expect(model.updateAllApps.allSatisfy { $0.platform == "macos" })
        #expect(model.products.count == 2)
        model.skip(phone)
        #expect(model.skipped["a-android"] == nil)
        await model.uninstall(phone)
        #expect(model.notice == nil, "nothing to uninstall for another platform's entry")
    }

    @Test func platformFilterIsPersistedAndCounted() async throws {
        var phone = entry("w", name: "Wiki")
        phone.platform = "android"
        let run = try make(apps: [phone, entry("b", name: "Beta")], autoInstall: false)
        let model = run.model
        await model.refresh()
        #expect(model.platformFilter == .device)
        #expect(model.filterCounts == FilterCounts(device: 1, all: 2))
        #expect(model.sections.all.map(\.product.key) == ["b"])
        model.platformFilter = .all
        #expect(defaults.string(forKey: "appdepot.ui.platformFilter") == "all")
        #expect(model.sections.other.map(\.product.key) == ["w"])
    }
}

/// A tiny mutable box for closures in tests.
final class Ref<T> { var value: T; init(_ v: T) { value = v } }
