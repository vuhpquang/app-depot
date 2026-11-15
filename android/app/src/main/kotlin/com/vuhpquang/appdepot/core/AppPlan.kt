package com.vuhpquang.appdepot.core

sealed interface AppState {
    data object NotInstalled : AppState
    data class UpToDate(val installed: String) : AppState
    data class UpdateAvailable(val installed: String, val latest: CatalogRelease) : AppState
    /** An update the user chose to skip: no badge, not in Update all or background runs, until a newer version. */
    data class Skipped(val installed: String, val latest: CatalogRelease) : AppState
    data class LocalBuild(val installed: String, val latest: CatalogRelease) : AppState
    data class NoRelease(val installed: String?) : AppState
    data class SelfUpdates(val installed: String?, val latest: CatalogRelease?) : AppState
    data object NotInstallable : AppState
}

/** [skippedVersion] is the version the user skipped for this app (Settings `appdepot.skipped`), if any. */
fun planFor(app: CatalogApp, installedVersion: String?, skippedVersion: String? = null): AppState {
    val latest = app.latest
    return when {
        app.installMode == "none" -> if (latest == null) AppState.NoRelease(installedVersion) else AppState.NotInstallable
        app.installMode == "selfUpdates" -> AppState.SelfUpdates(installedVersion, latest)
        latest == null -> AppState.NoRelease(installedVersion)
        installedVersion == null -> AppState.NotInstalled
        else -> {
            val c = compareVersions(installedVersion, latest.version)
            when {
                c < 0 && latest.version == skippedVersion -> AppState.Skipped(installedVersion, latest)
                c < 0 -> AppState.UpdateAvailable(installedVersion, latest)
                c > 0 -> AppState.LocalBuild(installedVersion, latest)
                else -> AppState.UpToDate(installedVersion)
            }
        }
    }
}

/** `slug=version` lines, the format of every slug map in Settings. */
fun parseSlugMap(text: String?): Map<String, String> = text?.lineSequence()
    ?.mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] } }
    ?.toMap() ?: emptyMap()

fun formatSlugMap(map: Map<String, String>): String = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
