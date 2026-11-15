package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipTest {
    private fun rel(v: String) = CatalogRelease(version = v, assetName = "a.apk", downloadUrl = "u", sha256 = "s", size = 1, publishedAt = "t")
    private fun app(latest: CatalogRelease? = rel("0.15.2")) =
        CatalogApp(slug = "z", name = "Z", tagline = "", platform = "android", appId = "com.z", repo = "v/z", latest = latest)

    @Test fun skippedSameVersionIsSkipped() {
        val s = planFor(app(), "0.15.1", skippedVersion = "0.15.2")
        assertTrue(s is AppState.Skipped)
        s as AppState.Skipped
        assertEquals("0.15.1", s.installed)
        assertEquals("0.15.2", s.latest.version)
    }

    @Test fun newerVersionThanSkippedIsAnUpdateAgain() =
        assertTrue(planFor(app(rel("0.15.3")), "0.15.1", skippedVersion = "0.15.2") is AppState.UpdateAvailable)

    @Test fun skipDoesNotTouchOtherStates() {
        assertEquals(AppState.UpToDate("0.15.2"), planFor(app(), "0.15.2", skippedVersion = "0.15.2"))
        assertEquals(AppState.NotInstalled, planFor(app(), null, skippedVersion = "0.15.2"))
    }

    @Test fun skippedIsNotUpdatableInTheBackground() {
        val next = mapOf("z" to planFor(app(), "0.15.1", skippedVersion = "0.15.2"))
        val plan = planBackground(emptyMap(), emptyMap(), next, autoInstall = true, silentCapable = setOf("z"))
        assertTrue(plan.install.isEmpty())
        assertTrue(plan.announce.isEmpty())
        val notify = planBackground(emptyMap(), emptyMap(), next, autoInstall = false, silentCapable = emptySet())
        assertTrue(notify.announce.isEmpty())
    }

    @Test fun skippedMapRoundTrip() {
        assertEquals(mapOf("a" to "1.0", "b" to "2.1"), parseSlugMap(formatSlugMap(mapOf("a" to "1.0", "b" to "2.1"))))
        assertEquals(emptyMap<String, String>(), parseSlugMap(null))
    }
}
