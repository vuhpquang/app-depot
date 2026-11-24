package com.vuhpquang.appdepot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.ui.theme.DepotTokens

/** "Android", "macOS", … for a catalog platform key. */
@Composable
fun platformName(platform: String): String = when (platform) {
    "android" -> stringResource(R.string.platform_android)
    "macos" -> stringResource(R.string.platform_macos)
    "windows" -> stringResource(R.string.platform_windows)
    "ios" -> stringResource(R.string.platform_ios)
    "web" -> stringResource(R.string.platform_web)
    else -> platform.replaceFirstChar { it.uppercase() }
}

/** Outlined labels, one per platform, [local] first and tinted with the accent. Not clickable. */
@Composable
fun PlatformBadges(platforms: List<String>, local: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs)) {
        platforms.forEach { p ->
            val here = p == local
            val c = MaterialTheme.colorScheme
            Text(
                platformName(p),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                color = if (here) c.primary else c.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .border(DepotTokens.Hairline, if (here) c.primary else c.outline, CircleShape)
                    .padding(horizontal = DepotTokens.SpaceSm, vertical = DepotTokens.SpaceXs / 2),
            )
        }
    }
}
