package com.wikg.aidaily.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 「晨光 · 靛紫」：主色是偏冷的靛紫（区别于 IT之家 的红），点缀晨光琥珀；小米专栏用小米橙。
object Palette {
    val Indigo = Color(0xFF4A3AFF)
    val IndigoDeep = Color(0xFF2C1E9E)
    val IndigoSoft = Color(0xFF9C92FF)
    val Amber = Color(0xFFFFB547)
    val MiOrange = Color(0xFFFF6900)
}

val LightColors = lightColorScheme(
    primary = Palette.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E7FF),
    onPrimaryContainer = Color(0xFF1B1466),
    secondary = Color(0xFF5E5A7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEAF6),
    onSecondaryContainer = Color(0xFF221F38),
    tertiary = Color(0xFFB86E00),
    tertiaryContainer = Color(0xFFFFEBCC),
    onTertiaryContainer = Color(0xFF3D2400),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFF4F5F9),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFEDEEF3),
    onSurfaceVariant = Color(0xFF5D6370),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F1F6),
    surfaceContainerHighest = Color(0xFFE8E9F0),
    outline = Color(0xFFC6C9D3),
    outlineVariant = Color(0xFFE7E9EF),
    error = Color(0xFFD93A3F),
)

val DarkColors = darkColorScheme(
    primary = Palette.IndigoSoft,
    onPrimary = Color(0xFF160F5C),
    primaryContainer = Color(0xFF2A2380),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Color(0xFFC8C4E0),
    onSecondary = Color(0xFF2C2943),
    secondaryContainer = Color(0xFF2A2838),
    onSecondaryContainer = Color(0xFFE5E1F7),
    tertiary = Color(0xFFFFC56E),
    tertiaryContainer = Color(0xFF4A3000),
    onTertiaryContainer = Color(0xFFFFE2B5),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE9EAF0),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE9EAF0),
    surfaceVariant = Color(0xFF20232B),
    onSurfaceVariant = Color(0xFF9BA1AE),
    surfaceContainerLowest = Color(0xFF0A0B0F),
    surfaceContainerLow = Color(0xFF14161C),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1E2129),
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF3A3F4A),
    outlineVariant = Color(0xFF242832),
    error = Color(0xFFFF6B6F),
)

@Immutable
data class ExtraColors(
    val regionCn: Color,
    val regionUs: Color,
    val regionIntl: Color,
    val xiaomi: Color,
    val xiaomiContainer: Color,
    val update: Color,
    val updateContainer: Color,
    val card: Color,
    val readTitle: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val isDark: Boolean,
)

val LightExtra = ExtraColors(
    regionCn = Color(0xFFE5484D),
    regionUs = Color(0xFF2F6FEB),
    regionIntl = Color(0xFF7C8594),
    xiaomi = Palette.MiOrange,
    xiaomiContainer = Color(0xFFFFF1E8),
    update = Color(0xFF0E9F6E),
    updateContainer = Color(0xFFE3F6EE),
    card = Color.White,
    readTitle = Color(0xFF8A909C),
    heroStart = Color(0xFF4A3AFF),
    heroEnd = Color(0xFF7A3CF0),
    isDark = false,
)

val DarkExtra = ExtraColors(
    regionCn = Color(0xFFFF6B6F),
    regionUs = Color(0xFF6EA2FF),
    regionIntl = Color(0xFF9AA3B2),
    xiaomi = Color(0xFFFF8A3D),
    xiaomiContainer = Color(0xFF2B1B10),
    update = Color(0xFF34D399),
    updateContainer = Color(0xFF0F2A20),
    card = Color(0xFF171A21),
    readTitle = Color(0xFF6E7480),
    heroStart = Color(0xFF2E2690),
    heroEnd = Color(0xFF4B2390),
    isDark = true,
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }
