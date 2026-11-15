package com.vuhpquang.appdepot.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.vuhpquang.appdepot.ui.iconFileName
import com.vuhpquang.appdepot.ui.theme.DepotTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

/** A cached icon older than this is fetched again (the cached one stays on screen meanwhile). */
private const val ICON_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

private fun decode(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

private fun download(url: String): ByteArray? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()

/** Disk copy (cacheDir/icons/<sha256 of url>.png) first, so icons show offline; then the network. */
private suspend fun loadIcon(url: String, cacheDir: File): ImageBitmap? = iconCache[url] ?: withContext(Dispatchers.IO) {
    val file = File(File(cacheDir, "icons"), iconFileName(url))
    val cached = if (file.exists()) runCatching { decode(file.readBytes()) }.getOrNull() else null
    if (cached != null && System.currentTimeMillis() - file.lastModified() < ICON_MAX_AGE_MS) {
        return@withContext cached.also { iconCache[url] = it }
    }
    val fresh = download(url)?.let { bytes ->
        decode(bytes)?.also {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(file)
            }
        }
    }
    (fresh ?: cached)?.also { iconCache[url] = it }
}

/** The catalog icon when there is one, else a letter tile in the app's accent container (also while loading). */
@Composable
fun AppIcon(name: String, iconUrl: String?, size: Dp = DepotTokens.IconCard, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(DepotTokens.RadiusMd * (size / DepotTokens.IconCard))
    val cacheDir = LocalContext.current.cacheDir
    val bitmap by produceState<ImageBitmap?>(iconUrl?.let { iconCache[it] }, iconUrl) {
        if (iconUrl != null && value == null) value = loadIcon(iconUrl, cacheDir)
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = if (size > DepotTokens.IconCard) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
