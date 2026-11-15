package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductsTest {
    private fun rel(v: String = "1.0") =
        CatalogRelease(version = v, assetName = "a", downloadUrl = "https://x/a", sha256 = "s", size = 1, publishedAt = "2026-11-01T00:00:00Z")

    private fun app(slug: String, platform: String, project: String? = null, name: String = slug, appId: String = "com.$slug") =
        CatalogApp(slug = slug, name = name, tagline = "", platform = platform, appId = appId, repo = "v/$slug", projectSlug = project, latest = rel())

    @Test fun groupsByProjectSlugFallingBackToSlug() {
        val apps = listOf(
            app("zentask-macos", "macos", "zentask", name = "ZenTask Mac"),
            app("padlet", "macos", "padlet"),
            app("zentask-android", "android", "zentask", name = "ZenTask"),
            app("loose", "android", null),
        )
        val products = groupProducts(apps, "android")
        assertEquals(listOf("zentask", "padlet", "loose"), products.map { it.key })
        val zen = products[0]
        assertEquals(setOf("android", "macos"), zen.variants.keys)
        // The name comes from this platform's entry, even when another platform comes first.
        assertEquals("ZenTask", zen.display.name)
        assertEquals("zentask-android", zen.display.slug)
        assertEquals("loose", products[2].variants.getValue("android").slug)
    }

    @Test fun displayFallsBackToFirstEntryWithoutLocalVariant() {
        val p = groupProducts(listOf(app("a-mac", "macos", "a", name = "A"), app("a-win", "windows", "a", name = "A win")), "android").single()
        assertEquals("A", p.display.name)
        assertNull(p.variants["android"])
    }

    @Test fun platformsListLocalFirst() {
        val p = groupProducts(listOf(app("z-mac", "macos", "z"), app("z-and", "android", "z")), "android").single()
        assertEquals(listOf("android", "macos"), p.platforms("android"))
        assertEquals(listOf("macos", "android"), p.platforms("macos"))
        assertEquals(listOf("macos"), p.others("android").map { it.platform })
    }

    @Test fun localAppsKeepOnlyThisPlatformEvenWhenAppIdsCollide() {
        // app-depot-macos and app-depot-android share a bundle id / package name in the real catalog.
        val apps = listOf(
            app("app-depot-android", "android", "app-depot", appId = "com.vuhpquang.appdepot"),
            app("app-depot-macos", "macos", "app-depot", appId = "com.vuhpquang.appdepot"),
            app("padlet", "macos", "padlet"),
        )
        assertEquals(listOf("app-depot-android"), localApps(apps).map { it.slug })
        assertEquals(ANDROID, "android")
    }

    @Test fun productSearchMatchesAnyVariantNameOrTagline() {
        val p = groupProducts(listOf(app("w", "macos", "w", name = "Wisp")), "android").single()
        assertTrue(matchesQuery(p, "wis"))
        assertTrue(matchesQuery(p, ""))
        assertEquals(false, matchesQuery(p, "zen"))
    }
}
