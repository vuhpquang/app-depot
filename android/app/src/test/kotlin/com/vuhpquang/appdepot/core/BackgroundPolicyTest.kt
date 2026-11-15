package com.vuhpquang.appdepot.core

import org.junit.Assert.*
import org.junit.Test

class BackgroundPolicyTest {
    private fun rel(v: String) = CatalogRelease(version = v, assetName = "a.apk", downloadUrl = "u", sha256 = "s", size = 1, publishedAt = "t")
    private fun upd(v: String) = AppState.UpdateAvailable("0.1", rel(v))

    @Test fun newUpdateNotifies() {
        val d = decide(emptyMap(), mapOf("a" to upd("2")), autoInstall = false)
        assertEquals(listOf("a"), d.notifySlugs)
        assertTrue(d.installSlugs.isEmpty())
    }

    @Test fun sameUpdateAlreadyNotifiedIsSilent() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to upd("2")), autoInstall = false)
        assertTrue(d.notifySlugs.isEmpty())
    }

    @Test fun newerVersionOfNotifiedAppNotifiesAgain() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to upd("3")), autoInstall = false)
        assertEquals(listOf("a"), d.notifySlugs)
    }

    @Test fun onlyNewOnesNotify() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to upd("2"), "b" to upd("5")), autoInstall = false)
        assertEquals(listOf("b"), d.notifySlugs)
    }

    @Test fun autoInstallListsAllUpdatableSelfLast() {
        val next = linkedMapOf(
            SELF_SLUG to upd("9"),
            "a" to upd("2"),
            "b" to AppState.UpToDate("1"),
            "c" to upd("4"),
        )
        val d = decide(emptyMap(), next, autoInstall = true)
        assertEquals(listOf("a", "c", SELF_SLUG), d.installSlugs)
        assertTrue(d.notifySlugs.isEmpty())
    }

    @Test fun autoInstallRetriesAlreadyNotified() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to upd("2")), autoInstall = true)
        assertEquals(listOf("a"), d.installSlugs)
    }

    @Test fun nonUpdatableStatesNeverNotify() {
        val next = mapOf(
            "a" to AppState.UpToDate("1"),
            "b" to AppState.NotInstalled,
            "c" to AppState.LocalBuild("9", rel("1")),
            "d" to AppState.SelfUpdates("1", rel("2")),
        )
        val d = decide(emptyMap(), next, autoInstall = true)
        assertTrue(d.notifySlugs.isEmpty()); assertTrue(d.installSlugs.isEmpty())
    }

    @Test fun nextStateKeptForRemaining() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to upd("2"), "b" to upd("3")), autoInstall = false)
        assertEquals(mapOf("a" to "2", "b" to "3"), d.notified)
    }

    @Test fun resolvedUpdatesAreForgotten() {
        val d = decide(mapOf("a" to upd("2")), mapOf("a" to AppState.UpToDate("2")), autoInstall = false)
        assertTrue(d.notified.isEmpty())
    }
}
