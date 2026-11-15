package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.planFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductListTest {
    private fun rel(v: String = "1.0", at: String = "2026-11-01T00:00:00Z") =
        CatalogRelease(version = v, assetName = "a", downloadUrl = "https://x/a", sha256 = "s", size = 10, publishedAt = at)

    private fun app(slug: String, platform: String, project: String? = null, name: String = slug, latest: CatalogRelease? = rel()) =
        CatalogApp(slug = slug, name = name, tagline = "", platform = platform, appId = "com.$slug", repo = "v/$slug", projectSlug = project, latest = latest)

    private val catalog = listOf(
        app("zentask-android", "android", "zentask", "ZenTask"),
        app("zentask-macos", "macos", "zentask", "ZenTask"),
        app("padlet", "macos", "padlet", "Padlet"),
        app("wiki", "android", "wiki", "Personal Wiki"),
        app("app-depot-android", "android", "app-depot", "App Depot"),
        app("app-depot-macos", "macos", "app-depot", "App Depot"),
        app("wisp", "macos", "wisp", "Wisp", latest = rel(at = "2026-11-14T00:00:00Z")),
    )

    /** Installed versions by slug; every app here plans as on a phone. */
    private fun rows(installed: Map<String, String> = emptyMap()): List<ProductRow> {
        val asked = mutableListOf<String>()
        val r = productRows(catalog, "android") { a ->
            asked += a.slug
            val s = planFor(a, installed[a.slug])
            AppRow(a, s, cardModel(s, null, true, a.latest), true)
        }
        assertTrue("rows are planned for android variants only: $asked", asked.all { it.endsWith("-android") || it == "wiki" })
        return r
    }

    @Test fun oneProductPerProjectWithLocalRow() {
        val r = rows()
        assertEquals(5, r.size)
        val zen = r.first { it.product.key == "zentask" }
        assertEquals("zentask-android", zen.row?.app?.slug)
        assertNull(r.first { it.product.key == "padlet" }.row)
    }

    @Test fun countsAndDeviceFilter() {
        val r = rows()
        assertEquals(FilterCounts(device = 3, all = 5), filterCounts(r))
        val device = homeSections(r, PlatformFilter.Device, SortOrder.Name, "")
        assertEquals(listOf("App Depot", "Personal Wiki", "ZenTask"), device.local.map { it.app.name })
        assertTrue(device.other.isEmpty())
    }

    @Test fun allShowsLocalFirstThenOthersEachSorted() {
        val all = homeSections(rows(), PlatformFilter.All, SortOrder.Name, "")
        assertEquals(listOf("App Depot", "Personal Wiki", "ZenTask"), all.local.map { it.app.name })
        assertEquals(listOf("Padlet", "Wisp"), all.other.map { it.app.name })
        val recent = homeSections(rows(), PlatformFilter.All, SortOrder.RecentlyReleased, "")
        assertEquals(listOf("Wisp", "Padlet"), recent.other.map { it.app.name })
    }

    @Test fun updatesFirstUsesTheLocalVariantState() {
        val r = rows(mapOf("zentask-android" to "0.9", "wiki" to "1.0"))
        val s = homeSections(r, PlatformFilter.All, SortOrder.UpdatesFirst, "")
        assertEquals("ZenTask", s.local.first().app.name)
    }

    @Test fun searchAppliesToProducts() {
        val s = homeSections(rows(), PlatformFilter.All, SortOrder.Name, "pad")
        assertTrue(s.local.isEmpty())
        assertEquals(listOf("Padlet"), s.other.map { it.app.name })
        assertTrue(homeSections(rows(), PlatformFilter.Device, SortOrder.Name, "pad").isEmpty)
    }

    @Test fun notLocalProductNeverOffersInstall() {
        val padlet = rows().first { it.product.key == "padlet" }
        val m = padlet.model
        assertNull(m.action)
        assertEquals(CardChip.NotOnThisDevice, m.chip)
        assertEquals("1.0", m.latest)
        assertEquals(listOf(MenuAction.OpenOnWeb, MenuAction.ShareWebLink), productMenuActions(padlet))
        assertFalse(MenuAction.Uninstall in productMenuActions(padlet))
    }

    @Test fun localProductKeepsTheRowMenu() {
        val zen = rows(mapOf("zentask-android" to "1.0")).first { it.product.key == "zentask" }
        assertEquals(menuActions(zen.row!!), productMenuActions(zen))
    }

    @Test fun updateAllOnlyCoversAndroidVariants() {
        // Both App Depot entries share a package name; only the Android one may be planned or updated.
        val r = rows(mapOf("app-depot-android" to "0.9", "app-depot-macos" to "0.1"))
        val updatable = r.mapNotNull { it.row }.filter { it.state is AppState.UpdateAvailable }
        assertEquals(listOf("app-depot-android"), updatable.map { it.app.slug })
    }

    @Test fun webUrlAndAlsoOn() {
        val r = rows()
        val zen = r.first { it.product.key == "zentask" }
        assertEquals("https://vuhpquang.com/projects/padlet", productWebUrl(r.first { it.product.key == "padlet" }.product))
        assertEquals(listOf("macos"), zen.product.others("android").map { it.platform })
        assertEquals("Padlet: https://vuhpquang.com/projects/padlet", webShareText(r.first { it.product.key == "padlet" }.product))
    }

    @Test fun filterKeyRoundTrip() {
        assertEquals(PlatformFilter.Device, PlatformFilter.fromKey(null))
        assertEquals(PlatformFilter.Device, PlatformFilter.fromKey("bogus"))
        assertEquals(PlatformFilter.All, PlatformFilter.fromKey("all"))
        assertEquals("device", PlatformFilter.Device.key)
    }
}
