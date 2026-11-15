package com.vuhpquang.appdepot.android

import android.content.Context
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.parseCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CatalogRepository(context: Context, private val baseUrl: String) {
    private val versionName: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
    private val cacheFile = File(context.filesDir, "catalog.json")

    suspend fun refresh(): Result<List<CatalogApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${baseUrl.trimEnd('/')}/api/depot/catalog?platform=android")
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
            val tmp = File(cacheFile.parentFile, "catalog.json.tmp")
            tmp.writeText(body)
            check(tmp.renameTo(cacheFile)) { "Could not write catalog cache" }
            apps
        }
    }

    fun cached(): List<CatalogApp> =
        runCatching { parseCatalog(cacheFile.readText()).getOrThrow() }.getOrDefault(emptyList())
}
