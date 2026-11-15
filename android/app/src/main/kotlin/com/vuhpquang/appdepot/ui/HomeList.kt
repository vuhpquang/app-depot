package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.foldForSearch
import java.security.MessageDigest

/** Home layout, persisted as `appdepot.ui.layout`. */
enum class HomeLayout(val key: String) {
    List("list"), Grid("grid");

    companion object {
        fun fromKey(key: String?): HomeLayout = entries.firstOrNull { it.key == key } ?: List
    }
}

/** Home sort order, persisted as `appdepot.ui.sort`. */
enum class SortOrder(val key: String) {
    UpdatesFirst("updates"), Name("name"), RecentlyReleased("recent");

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: UpdatesFirst
    }
}

/** One comparator for app rows and product rows: [app] gives name/release, [state] the planned state (null = not here). */
internal fun <T> rowComparator(order: SortOrder, app: (T) -> CatalogApp, state: (T) -> AppState?): Comparator<T> {
    val byName: Comparator<T> = compareBy({ foldForSearch(app(it).name) }, { app(it).slug })
    return when (order) {
        SortOrder.UpdatesFirst -> compareBy<T> { if (state(it) is AppState.UpdateAvailable) 0 else 1 }.then(byName)
        SortOrder.Name -> byName
        // ISO-8601 timestamps sort as text; apps without a release go last.
        SortOrder.RecentlyReleased -> compareBy<T> { if (app(it).latest == null) 1 else 0 }
            .thenByDescending { app(it).latest?.publishedAt ?: "" }
            .then(byName)
    }
}

fun sortRows(rows: List<AppRow>, order: SortOrder): List<AppRow> = rows.sortedWith(rowComparator(order, { it.app }, { it.state }))

/** Bytes to download for the primary Install / Update action; null when there is nothing to download. */
fun downloadBytes(row: AppRow): Long? = when (val s = row.state) {
    is AppState.UpdateAvailable -> s.latest.size
    AppState.NotInstalled -> row.app.latest?.size
    else -> null
}?.takeIf { it > 0 }

/** Total download of Update all. */
fun updateAllBytes(rows: List<AppRow>): Long =
    rows.sumOf { (it.state as? AppState.UpdateAvailable)?.latest?.size ?: 0L }

private const val PRIVATE_DOWNLOAD_PREFIX = "https://vuhpquang.com/api/depot/download/"
private const val APPS_PAGE = "https://vuhpquang.com/apps"

/** GitHub releases for public repos; private ones (served through vuhpquang.com) get the apps page. */
fun releasePageUrl(app: CatalogApp): String =
    if (app.latest?.downloadUrl?.startsWith(PRIVATE_DOWNLOAD_PREFIX) == true) APPS_PAGE
    else "https://github.com/${app.repo}/releases"

/** Installed apps can be uninstalled, except App Depot itself. */
fun canUninstall(row: AppRow): Boolean {
    if (row.app.slug == SELF_SLUG) return false
    return when (val s = row.state) {
        is AppState.UpToDate, is AppState.UpdateAvailable, is AppState.Skipped, is AppState.LocalBuild -> true
        is AppState.NoRelease -> s.installed != null
        is AppState.SelfUpdates -> s.installed != null
        AppState.NotInstalled, AppState.NotInstallable -> false
    }
}

/** "ZenTask: <download link>", or null when there is no release to share. */
fun shareText(app: CatalogApp): String? = app.latest?.let { "${app.name}: ${it.downloadUrl}" }

/** Disk cache file name for an icon URL. */
fun iconFileName(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".png"

/** Items of a card's overflow menu ("⋮"). */
enum class MenuAction { Open, Uninstall, Share, ReleasePage, OpenOnWeb, ShareWebLink }

fun menuActions(row: AppRow): List<MenuAction> = buildList {
    if (row.launchable && row.app.slug != SELF_SLUG) add(MenuAction.Open)
    if (canUninstall(row)) add(MenuAction.Uninstall)
    if (row.app.latest != null) add(MenuAction.Share)
    add(MenuAction.ReleasePage)
}
