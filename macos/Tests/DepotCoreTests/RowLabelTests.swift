import DepotCore
import Testing

@Suite struct VersionLineTests {
    private func m(_ chip: CardChip?, _ i: String?, _ l: String?) -> CardModel {
        CardModel(chip: chip, installed: i, latest: l, action: nil, activity: nil, error: nil)
    }
    @Test func selfUpdatesNewerThanCatalogShowsOnlyVersion() {
        #expect(versionLine(m(.selfUpdates, "0.3.3", "0.3.2")) == "Version 0.3.3")
    }
    @Test func selfUpdatesBehindCatalogShowsLatest() {
        #expect(versionLine(m(.selfUpdates, "0.3.3", "0.3.4")) == "Version 0.3.3 · Latest 0.3.4")
    }
    @Test func selfUpdatesEqualShowsOnlyVersion() {
        #expect(versionLine(m(.selfUpdates, "0.3.3", "0.3.3")) == "Version 0.3.3")
    }
    @Test func localBuildNewerThanCatalogShowsOnlyVersion() {
        #expect(versionLine(m(.localBuild, "2.6.0", "2.5.0")) == "Version 2.6.0")
    }
    @Test func localBuildBehindCatalogShowsLatest() {
        #expect(versionLine(m(.localBuild, "2.4.0", "2.5.0")) == "Version 2.4.0 · Latest 2.5.0")
    }
    @Test func selfUpdatesNotInstalledShowsLatest() {
        #expect(versionLine(m(.selfUpdates, nil, "0.3.2")) == "Latest 0.3.2")
    }
    @Test func managedUpdateKeepsArrow() {
        #expect(versionLine(m(.updateAvailable, "1.4.0", "1.5.1")) == "1.4.0 → 1.5.1")
        #expect(versionLine(m(.upToDate, "1.5.1", nil)) == "Version 1.5.1")
        #expect(versionLine(m(nil, nil, "1.5.1")) == "Latest 1.5.1")
        #expect(versionLine(m(.noRelease, nil, nil)) == "")
    }
}

@Suite struct IconSourceTests {
    @Test func installedWins() {
        #expect(iconSource(installedPath: "/Applications/ZenTask.app", catalogURL: "https://x/z.png")
                == .installed(path: "/Applications/ZenTask.app"))
    }
    @Test func catalogWhenNotInstalled() {
        #expect(iconSource(installedPath: nil, catalogURL: "https://x/z.png") == .catalog(url: "https://x/z.png"))
    }
    @Test func noneWhenNeither() {
        #expect(iconSource(installedPath: nil, catalogURL: nil) == .none)
    }
}
