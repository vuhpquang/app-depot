package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNoticeTest {
    private val q = InstallProgress.Queued

    @Test fun nothingActiveMeansNoNotice() {
        val b = DownloadBatch()
        assertNull(b.update(emptyMap()))
        assertNull(b.update(mapOf("zentask" to InstallProgress.Done("2.0"), "wiki" to InstallProgress.Failed("x"))))
        assertNull(b.update(mapOf("zentask" to InstallProgress.NeedsPermission)))
    }

    @Test fun singleDownload() {
        val n = DownloadBatch().update(mapOf("zentask" to InstallProgress.Downloading(50)))!!
        assertEquals("zentask", n.slug)
        assertEquals(DownloadNotice.Phase.Downloading(50), n.phase)
        assertEquals(1, n.index)
        assertEquals(1, n.total)
        assertEquals(45, n.overallPercent)
        assertFalse(n.indeterminate)
    }

    @Test fun batchKeepsFinishedSegmentsAfterTheMapForgetsThem() {
        val b = DownloadBatch()
        val first = b.update(mapOf("zentask" to InstallProgress.Downloading(10), "wiki" to q, "app-depot-android" to q))!!
        assertEquals(1, first.index)
        assertEquals(3, first.total)

        // ZenTask finished and the UI cleared it: still one of three, and counted as finished.
        val second = b.update(mapOf("wiki" to InstallProgress.Verifying, "app-depot-android" to q))!!
        assertEquals("wiki", second.slug)
        assertEquals(2, second.index)
        assertEquals(3, second.total)
        assertEquals(1, second.finished)
        assertEquals((100 + 92) / 3, second.overallPercent)
        assertTrue(second.indeterminate)

        // App Depot waits last; it is the only one left, still queued.
        val third = b.update(mapOf("wiki" to InstallProgress.Done("2.0"), "app-depot-android" to q))!!
        assertEquals("app-depot-android", third.slug)
        assertEquals(DownloadNotice.Phase.Queued, third.phase)
        assertEquals(3, third.index)
        assertEquals(2, third.finished)
    }

    @Test fun aNewBatchStartsAfterTheLastOneEnded() {
        val b = DownloadBatch()
        b.update(mapOf("zentask" to InstallProgress.Downloading(10), "wiki" to q))
        assertNull(b.update(mapOf("zentask" to InstallProgress.Done("2.0"), "wiki" to InstallProgress.Done("2.0"))))
        val n = b.update(mapOf("wiki" to InstallProgress.Installing))!!
        assertEquals(1, n.total)
        assertEquals(1, n.index)
        assertEquals(DownloadNotice.Phase.Installing, n.phase)
    }

    @Test fun waitingForTheUserIsTheConfirmPhase() {
        val n = DownloadBatch().update(mapOf("zentask" to InstallProgress.NeedsUserAction))!!
        assertEquals(DownloadNotice.Phase.Confirm, n.phase)
    }
}
