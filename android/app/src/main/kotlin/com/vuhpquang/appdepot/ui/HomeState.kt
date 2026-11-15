package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.InstallProgress

/** The primary button on a card; null in [CardModel.action] means no button. */
enum class CardAction { Install, Update, Open }

/** The state chip; null for a plain not-installed app (its "Latest X" line says enough). */
enum class CardChip { UpToDate, UpdateAvailable, Skipped, LocalBuild, NoRelease, NotInstallable, SelfUpdates, NotOnThisDevice }

/** An install in flight: replaces the button with a progress line. */
sealed interface CardActivity {
    data object Queued : CardActivity
    /** App Depot's own update, held back until [count] other apps have finished. */
    data class WaitingForOthers(val count: Int) : CardActivity
    data class Downloading(val percent: Int) : CardActivity
    data object Verifying : CardActivity
    data object Installing : CardActivity
    data object WaitingForConfirm : CardActivity
}

data class CardModel(
    val chip: CardChip?,
    val installed: String?,
    val latest: String?,
    val action: CardAction?,
    val activity: CardActivity? = null,
    val error: String? = null,
    val needsPermission: Boolean = false,
)

/**
 * Pure mapping from the planner's state + the coordinator's progress to what a card shows.
 * [latest] is only needed for NotInstalled (the state itself carries no release).
 */
fun cardModel(
    state: AppState,
    progress: InstallProgress?,
    canLaunch: Boolean = true,
    latest: CatalogRelease? = null,
    othersFirst: Int = 0,
): CardModel {
    val open = if (canLaunch) CardAction.Open else null
    val base = when (state) {
        AppState.NotInstalled -> CardModel(null, null, latest?.version, CardAction.Install)
        is AppState.UpToDate -> CardModel(CardChip.UpToDate, state.installed, null, open)
        is AppState.UpdateAvailable -> CardModel(CardChip.UpdateAvailable, state.installed, state.latest.version, CardAction.Update)
        is AppState.Skipped -> CardModel(CardChip.Skipped, state.installed, state.latest.version, open)
        is AppState.LocalBuild -> CardModel(CardChip.LocalBuild, state.installed, state.latest.version, null)
        is AppState.NoRelease -> CardModel(CardChip.NoRelease, state.installed, null, null)
        AppState.NotInstallable -> CardModel(CardChip.NotInstallable, null, latest?.version, null)
        is AppState.SelfUpdates -> CardModel(
            CardChip.SelfUpdates, state.installed, state.latest?.version,
            if (state.installed != null) open else null,
        )
    }
    val activity = when (progress) {
        InstallProgress.Queued -> if (othersFirst > 0) CardActivity.WaitingForOthers(othersFirst) else CardActivity.Queued
        is InstallProgress.Downloading -> CardActivity.Downloading(progress.percent)
        InstallProgress.Verifying -> CardActivity.Verifying
        InstallProgress.Installing -> CardActivity.Installing
        InstallProgress.NeedsUserAction -> CardActivity.WaitingForConfirm
        else -> null
    }
    return when {
        activity != null -> base.copy(action = null, activity = activity)
        progress is InstallProgress.Failed -> base.copy(error = progress.message)
        progress == InstallProgress.NeedsPermission -> base.copy(needsPermission = true)
        else -> base
    }
}

/** Debug-only `slug=version` lines (files/pretend.txt) that make the planner see another installed version. */
fun parsePretendVersions(text: String): Map<String, String> = text.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
    .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    .filter { (k, v) -> k.isNotEmpty() && v.isNotEmpty() }
    .toMap()
