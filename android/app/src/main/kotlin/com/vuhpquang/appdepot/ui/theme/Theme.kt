package com.vuhpquang.appdepot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens. Dynamic colour is off on purpose: App Depot has its own depot-green accent. */
object DepotTokens {
    val GreenLight = Color(0xFF2E7D5B)
    val GreenDark = Color(0xFF6FCF9F)

    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp

    val RadiusMd = 12.dp
    val RadiusLg = 20.dp
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = DepotTokens.GreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEDF),
    onPrimaryContainer = Color(0xFF0B3A27),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DepotTokens.GreenDark,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF1B5A40),
    onPrimaryContainer = Color(0xFFD3EEDF),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
)

@Composable
fun AppDepotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
