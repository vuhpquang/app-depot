package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.InstallProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    private val rel = CatalogRelease(
        version = "0.15.2", assetName = "a.apk", downloadUrl = "https://x/a.apk",
        sha256 = "00", size = 1, publishedAt = "2026-11-14T15:34:55.000Z",
    )

    @Test fun updateAvailableShowsUpdateAndBothVersions() {
        val m = cardModel(AppState.UpdateAvailable("0.15.1", rel), null)
        assertEquals(CardAction.Update, m.action)
        assertEquals(CardChip.UpdateAvailable, m.chip)
        assertEquals("0.15.1", m.installed)
        assertEquals("0.15.2", m.latest)
    }

    @Test fun localBuildShowsChipAndNoButton() {
        val m = cardModel(AppState.LocalBuild("0.16.0", rel), null)
        assertEquals(CardChip.LocalBuild, m.chip)
        assertNull(m.action)
    }

    @Test fun notInstalledShowsInstallAndLatestOnly() {
        val m = cardModel(AppState.NotInstalled, null)
        assertEquals(CardAction.Install, m.action)
        assertNull(m.chip)
        assertNull(m.installed)
    }

    @Test fun notInstalledShowsLatestFromRelease() {
        assertEquals("0.15.2", cardModel(AppState.NotInstalled, null, latest = rel).latest)
    }

    @Test fun upToDateOpensOnlyWhenLaunchable() {
        assertEquals(CardAction.Open, cardModel(AppState.UpToDate("0.15.2"), null).action)
        assertNull(cardModel(AppState.UpToDate("0.15.2"), null, canLaunch = false).action)
        assertEquals(CardChip.UpToDate, cardModel(AppState.UpToDate("0.15.2"), null).chip)
    }

    @Test fun noReleaseAndNotInstallableHaveNoButton() {
        assertNull(cardModel(AppState.NoRelease(null), null).action)
        assertEquals(CardChip.NoRelease, cardModel(AppState.NoRelease(null), null).chip)
        assertNull(cardModel(AppState.NotInstallable, null).action)
        assertEquals(CardChip.NotInstallable, cardModel(AppState.NotInstallable, null).chip)
    }

    @Test fun selfUpdatesIsStatusPlusOpenOnly() {
        val installed = cardModel(AppState.SelfUpdates("1.0", rel), null)
        assertEquals(CardChip.SelfUpdates, installed.chip)
        assertEquals(CardAction.Open, installed.action)
        assertNull(cardModel(AppState.SelfUpdates(null, rel), null).action)
    }

    @Test fun downloadingHidesButtonAndShowsPercent() {
        val m = cardModel(AppState.UpdateAvailable("0.15.1", rel), InstallProgress.Downloading(42))
        assertNull(m.action)
        assertEquals(CardActivity.Downloading(42), m.activity)
    }

    @Test fun queuedVerifyingInstallingAndConfirmAreBusy() {
        val s = AppState.NotInstalled
        assertEquals(CardActivity.Queued, cardModel(s, InstallProgress.Queued).activity)
        assertEquals(CardActivity.Verifying, cardModel(s, InstallProgress.Verifying).activity)
        assertEquals(CardActivity.Installing, cardModel(s, InstallProgress.Installing).activity)
        assertEquals(CardActivity.WaitingForConfirm, cardModel(s, InstallProgress.NeedsUserAction).activity)
        assertNull(cardModel(s, InstallProgress.Installing).action)
    }

    @Test fun failedKeepsTheButtonAndShowsTheMessage() {
        val m = cardModel(AppState.NotInstalled, InstallProgress.Failed("The download failed."))
        assertEquals(CardAction.Install, m.action)
        assertEquals("The download failed.", m.error)
        assertNull(m.activity)
    }

    @Test fun needsPermissionFlagsItAndKeepsTheButton() {
        val m = cardModel(AppState.UpdateAvailable("0.15.1", rel), InstallProgress.NeedsPermission)
        assertTrue(m.needsPermission)
        assertEquals(CardAction.Update, m.action)
        assertFalse(cardModel(AppState.NotInstalled, null).needsPermission)
    }

    @Test fun doneFallsBackToTheState() {
        val m = cardModel(AppState.UpToDate("0.15.2"), InstallProgress.Done("0.15.2"))
        assertNull(m.activity)
        assertEquals(CardAction.Open, m.action)
    }

    @Test fun pretendVersionsParseSlugEqualsVersionLines() {
        assertEquals(
            mapOf("zentask-android" to "0.0.1", "wiki" to "1.2"),
            parsePretendVersions("zentask-android=0.0.1\n\n# comment\n wiki = 1.2 \nbroken\n=1\n"),
        )
    }
}
