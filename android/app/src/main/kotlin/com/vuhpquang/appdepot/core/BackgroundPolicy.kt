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
