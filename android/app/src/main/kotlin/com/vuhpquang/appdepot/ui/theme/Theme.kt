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
    val SpaceXl = 32.dp

    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 20.dp

    /** App icon on a card / in the detail header / in About. */
    val IconCard = 48.dp
    val IconHeader = 72.dp

    /** Minimum touch target. */
    val TouchMin = 48.dp

    /** Small leading icon in list rows (history). */
    val IconRow = 24.dp
    val ProgressHeight = 4.dp

    /** Content alpha for disabled rows (Material's disabled level). */
    const val DisabledAlpha = 0.38f
}

// Every role is set: Material's defaults are a lavender baseline that doesn't belong to this app.
private val LightColors: ColorScheme = lightColorScheme(
    primary = DepotTokens.GreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEDF),
    onPrimaryContainer = Color(0xFF0B3A27),
    inversePrimary = DepotTokens.GreenDark,
    secondary = Color(0xFF4D6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8DA),
    onSecondaryContainer = Color(0xFF0A1F14),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceTint = DepotTokens.GreenLight,
    inverseSurface = Color(0xFF2D322F),
    inverseOnSurface = Color(0xFFEEF2EE),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    surfaceBright = Color(0xFFF7FAF8),
    surfaceDim = Color(0xFFD7DBD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBEFEC),
    surfaceContainerHigh = Color(0xFFE5EAE6),
    surfaceContainerHighest = Color(0xFFE0E4E1),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DepotTokens.GreenDark,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF1B5A40),
    onPrimaryContainer = Color(0xFFD3EEDF),
    inversePrimary = DepotTokens.GreenLight,
    secondary = Color(0xFFB4CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFD0E8DA),
    tertiary = Color(0xFFFFB951),
    onTertiary = Color(0xFF482A00),
    tertiaryContainer = Color(0xFF673F00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF101412),
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceTint = DepotTokens.GreenDark,
    inverseSurface = Color(0xFFDFE4E0),
    inverseOnSurface = Color(0xFF2D322F),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    surfaceBright = Color(0xFF353A37),
    surfaceDim = Color(0xFF101412),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
    surfaceContainerHighest = Color(0xFF313633),
)

@Composable
fun AppDepotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
