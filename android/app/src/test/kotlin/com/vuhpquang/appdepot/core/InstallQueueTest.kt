package com.vuhpquang.appdepot.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallQueueTest {
    private fun app(slug: String) = CatalogApp(
        slug = slug, name = slug, tagline = "", platform = "android", appId = "x.$slug", repo = "r/$slug",
    )

    @Test fun selfGoesLast() = assertEquals(
        listOf("zentask-android", "personal-wiki-android", "app-depot-android"),
        orderForUpdateAll(listOf("app-depot-android", "zentask-android", "personal-wiki-android")),
    )

    @Test fun failedAppDoesNotStopTheQueue() = runTest {
        val ran = mutableListOf<String>()
        val installer = Installer { a ->
            ran += a.slug
            if (a.slug == "b") InstallOutcome.Failed("boom") else InstallOutcome.Success("1.0")
        }
        val events = mutableListOf<Pair<String, InstallProgress>>()
        val results = runInstallQueue(listOf(app("a"), app("b"), app("c")), installer) { s, p -> events += s to p }
        assertEquals(listOf("a", "b", "c"), ran)
        assertEquals(InstallOutcome.Failed("boom"), results["b"])
        assertEquals(InstallProgress.Done("1.0"), events.last { it.first == "c" }.second)
        assertEquals(InstallProgress.Failed("boom"), events.last { it.first == "b" }.second)
    }

    @Test fun selfRunsLastEvenWhenListedFirst() = runTest {
        val ran = mutableListOf<String>()
        runInstallQueue(listOf(app("app-depot-android"), app("z")), { a -> ran += a.slug; InstallOutcome.Success("1") }) { _, _ -> }
        assertEquals(listOf("z", "app-depot-android"), ran)
    }

    @Test fun missingPermissionStopsTheQueue() = runTest {
        val ran = mutableListOf<String>()
        val events = mutableListOf<Pair<String, InstallProgress>>()
        runInstallQueue(listOf(app("a"), app("b")), { a -> ran += a.slug; InstallOutcome.NeedsPermission }) { s, p -> events += s to p }
        assertEquals(listOf("a"), ran)
        assertEquals(InstallProgress.NeedsPermission, events.last { it.first == "b" }.second)
    }

    @Test fun unconfirmedInstallFailsAndQueueKeepsMoving() = runTest {
        val ran = mutableListOf<String>()
        val installer = Installer { a ->
            ran += a.slug
            if (a.slug == "a") InstallOutcome.Failed("The install wasn't confirmed. Try again.") else InstallOutcome.Success("2")
        }
        val results = runInstallQueue(listOf(app("a"), app("b")), installer) { _, _ -> }
        assertEquals(listOf("a", "b"), ran)
        assertEquals(InstallOutcome.Failed("The install wasn't confirmed. Try again."), results["a"])
        assertEquals(InstallOutcome.Success("2"), results["b"])
    }
}
