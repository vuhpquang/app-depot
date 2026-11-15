package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.SELF_SLUG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListTest {
    private fun rel(v: String = "1.0", at: String = "2026-11-01T00:00:00Z", size: Long = 1, url: String = "https://github.com/v/x/a.apk") =
        CatalogRelease(version = v, assetName = "a.apk", downloadUrl = url, sha256 = "s", size = size, publishedAt = at)

    private fun app(name: String, latest: CatalogRelease? = rel(), slug: String = name.lowercase(), repo: String = "vuhpquang/$slug") =
        CatalogApp(slug = slug, name = name, tagline = "", platform = "android", appId = "com.$slug", repo = repo, latest = latest)

    private fun row(a: CatalogApp, state: AppState) = AppRow(a, state, cardModel(state, null, true, a.latest), true)

    @Test fun updatesFirstThenByName() {
        val r = listOf(
            row(app("beta"), AppState.UpToDate("1.0")),
            row(app("Zeta"), AppState.UpdateAvailable("0.9", rel())),
            row(app("alpha"), AppState.NotInstalled),
            row(app("Mu"), AppState.UpdateAvailable("0.9", rel())),
        )
        assertEquals(listOf("Mu", "Zeta", "alpha", "beta"), sortRows(r, SortOrder.UpdatesFirst).map { it.app.name })
    }

    @Test fun byNameIgnoresCaseAndAccents() {
        val r = listOf(row(app("beta"), AppState.NotInstalled), row(app("Ánh"), AppState.NotInstalled), row(app("Zed"), AppState.NotInstalled))
        assertEquals(listOf("Ánh", "beta", "Zed"), sortRows(r, SortOrder.Name).map { it.app.name })
    }

    @Test fun recentlyReleasedNewestFirstNoReleaseLast() {
        val r = listOf(
            row(app("old", rel(at = "2026-01-01T00:00:00Z")), AppState.NotInstalled),
            row(app("none", null), AppState.NoRelease(null)),
            row(app("new", rel(at = "2026-11-10T00:00:00Z")), AppState.NotInstalled),
        )
        assertEquals(listOf("new", "old", "none"), sortRows(r, SortOrder.RecentlyReleased).map { it.app.name })
    }

    @Test fun prefKeysRoundTripWithDefaults() {
        SortOrder.entries.forEach { assertEquals(it, SortOrder.fromKey(it.key)) }
        HomeLayout.entries.forEach { assertEquals(it, HomeLayout.fromKey(it.key)) }
        assertEquals(SortOrder.UpdatesFirst, SortOrder.fromKey(null))
        assertEquals(SortOrder.UpdatesFirst, SortOrder.fromKey("bogus"))
        assertEquals(HomeLayout.List, HomeLayout.fromKey(null))
        assertEquals("grid", HomeLayout.Grid.key)
        assertEquals("list", HomeLayout.List.key)
    }

    @Test fun skippedShowsSkippedChipAndNoUpdate() {
        val m = cardModel(AppState.Skipped("0.9", rel("1.0")), null)
        assertEquals(CardChip.Skipped, m.chip)
        assertEquals(CardAction.Open, m.action)
        assertEquals("1.0", m.latest)
        assertNull(cardModel(AppState.Skipped("0.9", rel("1.0")), null, canLaunch = false).action)
    }

    @Test fun updateAllSizeSumsOnlyUpdates() {
        val r = listOf(
            row(app("a", rel(size = 57_000_000)), AppState.UpdateAvailable("0.9", rel(size = 57_000_000))),
            row(app("b", rel(size = 26_000_000)), AppState.UpdateAvailable("0.9", rel(size = 26_000_000))),
            row(app("c", rel(size = 99)), AppState.Skipped("0.9", rel(size = 99))),
            row(app("d", rel(size = 5)), AppState.NotInstalled),
        )
        assertEquals(83_000_000L, updateAllBytes(r))
    }

    @Test fun downloadBytesForInstallAndUpdateOnly() {
        assertEquals(57L, downloadBytes(row(app("a", rel(size = 57)), AppState.UpdateAvailable("0.9", rel(size = 57)))))
        assertEquals(5L, downloadBytes(row(app("d", rel(size = 5)), AppState.NotInstalled)))
        assertNull(downloadBytes(row(app("u"), AppState.UpToDate("1.0"))))
    }

    @Test fun releasePagePublicAndPrivate() {
        assertEquals("https://github.com/vuhpquang/zentask/releases", releasePageUrl(app("ZenTask", repo = "vuhpquang/zentask")))
        val wiki = app("Wiki", rel(url = "https://vuhpquang.com/api/depot/download/personal-wiki-android/0.1.11"), repo = "vuhpquang/personal-wiki")
        assertEquals("https://vuhpquang.com/apps", releasePageUrl(wiki))
    }

    @Test fun uninstallOnlyInstalledAndNeverSelf() {
        assertTrue(canUninstall(row(app("a"), AppState.UpToDate("1.0"))))
        assertTrue(canUninstall(row(app("a"), AppState.UpdateAvailable("0.9", rel()))))
        assertTrue(canUninstall(row(app("a"), AppState.Skipped("0.9", rel()))))
        assertTrue(canUninstall(row(app("a"), AppState.LocalBuild("2.0", rel()))))
        assertFalse(canUninstall(row(app("a"), AppState.NotInstalled)))
        assertFalse(canUninstall(row(app("a"), AppState.NoRelease(null))))
        assertFalse(canUninstall(row(app("Depot", slug = SELF_SLUG), AppState.UpToDate("1.0"))))
    }

    @Test fun shareTextHasNameAndLink() {
        assertEquals("ZenTask: https://github.com/v/x/a.apk", shareText(app("ZenTask")))
        assertNull(shareText(app("None", null)))
    }

    @Test fun iconFileNameIsStableHash() {
        val a = iconFileName("https://vuhpquang.com/app-icons/zentask.png")
        assertEquals(a, iconFileName("https://vuhpquang.com/app-icons/zentask.png"))
        assertFalse(a == iconFileName("https://vuhpquang.com/app-icons/padlet.png"))
        assertTrue(a.matches(Regex("[0-9a-f]{64}\\.png")))
    }

    @Test fun menuForInstalledAndNotInstalled() {
        assertEquals(
            listOf(MenuAction.Open, MenuAction.Uninstall, MenuAction.Share, MenuAction.ReleasePage),
            menuActions(row(app("a"), AppState.UpToDate("1.0"))),
        )
        assertEquals(listOf(MenuAction.Share, MenuAction.ReleasePage), menuActions(row(app("a"), AppState.NotInstalled).copy(launchable = false)))
        assertEquals(listOf(MenuAction.Share, MenuAction.ReleasePage), menuActions(row(app("D", slug = SELF_SLUG), AppState.UpToDate("1.0"))))
    }
}
