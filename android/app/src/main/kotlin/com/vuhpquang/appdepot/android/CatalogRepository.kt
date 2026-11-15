package com.vuhpquang.appdepot.android

import android.content.Context
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.parseCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The full catalog, every platform (filtering is done in the app, see `core/Products.kt`), cached in `catalog-all.json`.
 * [baseUrl] is read on every refresh so the debug Server setting applies without a restart.
 */
class CatalogRepository(context: Context, private val baseUrl: () -> String) {
    private val versionName: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
    // 0.3.0 switched to the full catalog. Before that `catalog.json` held Android apps only, and loading it
    // after an update showed "All · 3" until the next refresh (which waits 5 minutes after a check), so
    // the full catalog gets its own file and the old one is dropped: the first launch refreshes.
    private val cacheFile = File(context.filesDir, "catalog-all.json").also {
        File(context.filesDir, "catalog.json").delete()
    }

    suspend fun refresh(): Result<List<CatalogApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${baseUrl().trimEnd('/')}/api/depot/catalog")
                .openConnection() as HttpURLConnection
            val body = try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "AppDepot-Android/$versionName")
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                check(code in 200..299) { "Catalog request failed (HTTP $code)" }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val apps = parseCatalog(body).getOrThrow()
            val tmp = File(cacheFile.parentFile, "catalog-all.json.tmp")
            tmp.writeText(body)
            check(tmp.renameTo(cacheFile)) { "Could not write catalog cache" }
            apps
        }
    }

    fun cached(): List<CatalogApp> =
        runCatching { parseCatalog(cacheFile.readText()).getOrThrow() }.getOrDefault(emptyList())
}
