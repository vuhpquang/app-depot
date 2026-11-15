package com.vuhpquang.appdepot.core

sealed interface AppState {
    data object NotInstalled : AppState
    data class UpToDate(val installed: String) : AppState
    data class UpdateAvailable(val installed: String, val latest: CatalogRelease) : AppState
    data class LocalBuild(val installed: String, val latest: CatalogRelease) : AppState
    data class NoRelease(val installed: String?) : AppState
    data class SelfUpdates(val installed: String?, val latest: CatalogRelease?) : AppState
    data object NotInstallable : AppState
}

fun planFor(app: CatalogApp, installedVersion: String?): AppState {
    val latest = app.latest
    return when {
        app.installMode == "none" -> if (latest == null) AppState.NoRelease(installedVersion) else AppState.NotInstallable
        app.installMode == "selfUpdates" -> AppState.SelfUpdates(installedVersion, latest)
        latest == null -> AppState.NoRelease(installedVersion)
        installedVersion == null -> AppState.NotInstalled
        else -> {
            val c = compareVersions(installedVersion, latest.version)
            when {
                c < 0 -> AppState.UpdateAvailable(installedVersion, latest)
                c > 0 -> AppState.LocalBuild(installedVersion, latest)
                else -> AppState.UpToDate(installedVersion)
            }
        }
    }
}
