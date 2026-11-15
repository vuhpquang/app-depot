package com.vuhpquang.appdepot.core

/**
 * What a background check does with its result.
 * [notified] is the state to persist for the next run: slug -> latest version the user has been told about
 * (or that is being installed). Apps that are no longer updatable drop out.
 */
data class BackgroundDecision(
    val notifySlugs: List<String>,
    val installSlugs: List<String>,
    val notified: Map<String, String>,
)

/**
 * [prev] is what the last run already dealt with, [next] the fresh result. Only updates that are new
 * (never notified, or a newer version than the notified one) notify. With [autoInstall] every updatable
 * app is installed (self last) and nothing is announced up front. Anything not an available update
 * (up to date, failed lookup, local build, …) never notifies.
 */
fun decide(prev: Map<String, AppState>, next: Map<String, AppState>, autoInstall: Boolean): BackgroundDecision {
    val updatable = next.filterValues { it is AppState.UpdateAvailable }
    fun version(s: AppState?) = (s as? AppState.UpdateAvailable)?.latest?.version
    val fresh = updatable.keys.filter { version(prev[it]) != version(next[it]) }
    return BackgroundDecision(
        notifySlugs = if (autoInstall) emptyList() else fresh,
        installSlugs = if (autoInstall) orderForUpdateAll(updatable.keys.toList()) else emptyList(),
        notified = updatable.mapValues { version(it.value)!! },
    )
}

/** What a background run should do, after the glue facts are known. */
data class BackgroundPlan(
    val announce: List<String>,
    val install: List<String>,
    /** Latest versions to remember as told; unchanged by notification delivery (the caller adjusts). */
    val notified: Map<String, String>,
)

/**
 * [told] slug -> version already announced, [failed] slug -> version whose silent install failed or needed
 * the user (never retried until a newer version), [silentCapable] apps App Depot may update without a prompt,
 * [busy] slugs whose install state the user hasn't acted on (left alone).
 */
fun planBackground(
    told: Map<String, String>,
    failed: Map<String, String>,
    next: Map<String, AppState>,
    autoInstall: Boolean,
    silentCapable: Set<String>,
    busy: Set<String> = emptySet(),
): BackgroundPlan {
    fun version(s: AppState?) = (s as? AppState.UpdateAvailable)?.latest?.version
    val prev = next.filter { (slug, s) -> version(s) == told[slug] }
    val d = decide(prev, next, autoInstall)
    val canInstall = d.installSlugs.filter { it in silentCapable && it !in busy && failed[it] != version(next[it]) }
    val manual = d.installSlugs.filter { it !in silentCapable && it !in prev }
    return BackgroundPlan(d.notifySlugs + manual, canInstall, d.notified)
}
