package com.vuhpquang.appdepot.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class DamagedDownloadException : java.io.IOException("download larger than expected")

class ApkDownloader(private val userAgent: String) {
    /** Downloads [url] into [dest] (replacing it). The caller deletes the file when it isn't needed. */
    suspend fun download(url: String, dest: File, expectedSize: Long, onPercent: (Int) -> Unit) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        // GitHub answers with a redirect to its download host; HttpURLConnection follows it (both https).
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", userAgent)
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val total = c.contentLengthLong
            var done = 0L
            var last = -1
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // More bytes than the catalog promised: it can't verify, stop early.
                        if (expectedSize > 0 && done > expectedSize) throw DamagedDownloadException()
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; onPercent(p) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
