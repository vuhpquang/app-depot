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
import androidx.compose.ui.unit.Dp
import com.vuhpquang.appdepot.ui.theme.DepotTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

private suspend fun loadIcon(url: String): ImageBitmap? = iconCache[url] ?: withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()?.also { iconCache[url] = it }
}

/** The catalog icon when there is one, else a letter tile in the app's accent container. */
@Composable
fun AppIcon(name: String, iconUrl: String?, size: Dp = DepotTokens.IconCard, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(DepotTokens.RadiusMd)
    val bitmap by produceState<ImageBitmap?>(iconUrl?.let { iconCache[it] }, iconUrl) {
        if (iconUrl != null && value == null) value = loadIcon(iconUrl)
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
