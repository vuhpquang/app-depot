package com.vuhpquang.appdepot.core

import org.junit.Assert.*
import org.junit.Test

class AppPlanTest {
    private fun rel(v: String) = CatalogRelease(version = v, assetName = "a.apk", downloadUrl = "u", sha256 = "s", size = 1, publishedAt = "t")
    private fun app(latest: CatalogRelease? = rel("0.15.2"), mode: String = "managed") =
        CatalogApp(slug = "s", name = "n", tagline = "t", platform = "android", appId = "a.b", repo = "o/r", installMode = mode, latest = latest)

    @Test fun notInstalled() = assertEquals(AppState.NotInstalled, planFor(app(), null))
    @Test fun updateAvailable() {
        val s = planFor(app(), "0.15.1") as AppState.UpdateAvailable
        assertEquals("0.15.1", s.installed); assertEquals("0.15.2", s.latest.version)
    }
    @Test fun upToDate() = assertEquals(AppState.UpToDate("0.15.2"), planFor(app(), "0.15.2"))
    @Test fun localBuild() = assertTrue(planFor(app(), "0.15.3") is AppState.LocalBuild)
    @Test fun noRelease() = assertEquals(AppState.NoRelease("1.0"), planFor(app(latest = null), "1.0"))
    @Test fun noReleaseNotInstalled() = assertEquals(AppState.NoRelease(null), planFor(app(latest = null), null))
    @Test fun selfUpdates() {
        val s = planFor(app(mode = "selfUpdates"), "0.1") as AppState.SelfUpdates
        assertEquals("0.1", s.installed); assertEquals("0.15.2", s.latest?.version)
    }
    @Test fun noneIsNotInstallable() = assertEquals(AppState.NotInstallable, planFor(app(mode = "none"), null))
    @Test fun noneWithoutReleaseIsNoRelease() = assertEquals(AppState.NoRelease(null), planFor(app(latest = null, mode = "none"), null))
}
