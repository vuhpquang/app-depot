package com.vuhpquang.appdepot.core

/** What the install notification says right now. */
data class DownloadNotice(
    /** The app being worked on (or the next one waiting). */
    val slug: String,
    val phase: Phase,
    /** 1-based position of [slug] in the batch, and the batch size: "2 of 3". */
    val index: Int,
    val total: Int,
    /** Finished apps in this batch (done, failed, or handed back to the user). */
    val finished: Int,
    /** Whole-batch progress in segment units: each app is a 100-unit segment, so 0..100 × [total]. */
    val units: Int,
) {
    /** Whole-batch progress, 0..100. */
    val overallPercent: Int get() = units / total

    sealed interface Phase {
        data object Queued : Phase
        data class Downloading(val percent: Int) : Phase
        data object Verifying : Phase
        data object Installing : Phase
        data object Confirm : Phase
    }

    /** Verifying / installing / waiting: no meaningful percentage inside the segment. */
    val indeterminate: Boolean get() = phase !is Phase.Downloading || phase.percent <= 0
}

/**
 * Follows the install coordinator's progress map and remembers the batch, so finished apps keep their
 * segment even after the UI clears them from the map. The batch ends (null notice) as soon as nothing
 * is queued or running; the next install starts a new one.
 */
class DownloadBatch {
    private val order = mutableListOf<String>()

    fun update(progress: Map<String, InstallProgress>): DownloadNotice? {
        val active = progress.filterValues { it.isActive() }
        if (active.isEmpty()) {
            order.clear()
            return null
        }
        active.keys.forEach { if (it !in order) order += it }
        val running = active.entries.firstOrNull { it.value != InstallProgress.Queued }
            ?: active.entries.first { it.key in order }
        val slug = running.key
        val phase = running.value.toPhase()
        val total = order.size
        val finished = order.count { it !in active }
        val inSegment = when (phase) {
            DownloadNotice.Phase.Queued -> 0
            is DownloadNotice.Phase.Downloading -> phase.percent.coerceIn(0, 100) * 90 / 100
            DownloadNotice.Phase.Verifying -> 92
            DownloadNotice.Phase.Installing -> 96
            DownloadNotice.Phase.Confirm -> 98
        }
        return DownloadNotice(
            slug = slug,
            phase = phase,
            index = order.indexOf(slug) + 1,
            total = total,
            finished = finished,
            units = finished * 100 + inSegment,
        )
    }

    private fun InstallProgress.isActive() = when (this) {
        InstallProgress.Queued, is InstallProgress.Downloading, InstallProgress.Verifying,
        InstallProgress.Installing, InstallProgress.NeedsUserAction -> true
        InstallProgress.NeedsPermission, is InstallProgress.Done, is InstallProgress.Failed -> false
    }

    private fun InstallProgress.toPhase(): DownloadNotice.Phase = when (this) {
        is InstallProgress.Downloading -> DownloadNotice.Phase.Downloading(percent)
        InstallProgress.Verifying -> DownloadNotice.Phase.Verifying
        InstallProgress.Installing -> DownloadNotice.Phase.Installing
        InstallProgress.NeedsUserAction -> DownloadNotice.Phase.Confirm
        else -> DownloadNotice.Phase.Queued
    }
}
