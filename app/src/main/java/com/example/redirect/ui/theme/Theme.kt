package com.example.redirect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redirect.data.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4F3A),
    onPrimary = Color(0xFFFBF8F2),
    primaryContainer = Color(0xFFEADBC8),
    onPrimaryContainer = Color(0xFF3E2C1F),
    secondary = Color(0xFF8C7B6B),
    onSecondary = Color(0xFFFBF8F2),
    secondaryContainer = Color(0xFFECE3D6),
    onSecondaryContainer = Color(0xFF3B322A),
    tertiary = Color(0xFF66745A),
    onTertiary = Color(0xFFFBF8F2),
    tertiaryContainer = Color(0xFFDFE5D3),
    onTertiaryContainer = Color(0xFF2C3524),
    background = Color(0xFFF6F1E9),
    onBackground = Color(0xFF2B2622),
    surface = Color(0xFFF6F1E9),
    onSurface = Color(0xFF2B2622),
    surfaceVariant = Color(0xFFEFE8DC),
    onSurfaceVariant = Color(0xFF6E6359),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFBF7F0),
    surfaceContainer = Color(0xFFF3EDE3),
    surfaceContainerHigh = Color(0xFFEFE8DD),
    surfaceContainerHighest = Color(0xFFE9E1D5),
    outline = Color(0xFFD9CEBE),
    outlineVariant = Color(0xFFE6DDD0),
    error = Color(0xFFA4493D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5DDD7),
    onErrorContainer = Color(0xFF4A1A13),
    inverseSurface = Color(0xFF2F2A25),
    inverseOnSurface = Color(0xFFF3ECE2),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9B99A),
    onPrimary = Color(0xFF2B1E13),
    primaryContainer = Color(0xFF4A3727),
    onPrimaryContainer = Color(0xFFF1DECB),
    secondary = Color(0xFFC2B2A1),
    onSecondary = Color(0xFF2A221B),
    secondaryContainer = Color(0xFF3A3129),
    onSecondaryContainer = Color(0xFFE9DDD0),
    tertiary = Color(0xFFB4C2A4),
    onTertiary = Color(0xFF223018),
    tertiaryContainer = Color(0xFF39452F),
    onTertiaryContainer = Color(0xFFDDE6D0),
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFEDE5DA),
    surface = Color(0xFF1A1714),
    onSurface = Color(0xFFEDE5DA),
    surfaceVariant = Color(0xFF2F2924),
    onSurfaceVariant = Color(0xFFB5A898),
    surfaceContainerLowest = Color(0xFF15120F),
    surfaceContainerLow = Color(0xFF1F1B17),
    surfaceContainer = Color(0xFF241F1B),
    surfaceContainerHigh = Color(0xFF2A2420),
    surfaceContainerHighest = Color(0xFF332C26),
    outline = Color(0xFF4A4139),
    outlineVariant = Color(0xFF3A332D),
    error = Color(0xFFE59C8F),
    onError = Color(0xFF3F0F08),
    errorContainer = Color(0xFF5C2A22),
    onErrorContainer = Color(0xFFF7DCD6),
    inverseSurface = Color(0xFFEDE5DA),
    inverseOnSurface = Color(0xFF2B2622),
    scrim = Color(0xFF000000),
)

val Serif = FontFamily.Serif
val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Muted tones for generated book covers. */
@Immutable
data class CoverPalette(val background: Color, val ink: Color, val accent: Color)

val CoverPalettes = listOf(
    CoverPalette(Color(0xFF6E7B66), Color(0xFFF5EFE4), Color(0xFFD9CDB4)), // sage
    CoverPalette(Color(0xFF4F5E4C), Color(0xFFF1EADC), Color(0xFFC9B98F)), // forest
    CoverPalette(Color(0xFFB27B5E), Color(0xFFFBF4EA), Color(0xFFF0D9BF)), // clay
    CoverPalette(Color(0xFFC09A58), Color(0xFF2E241A), Color(0xFF5A4630)), // ochre
    CoverPalette(Color(0xFF3F4A5A), Color(0xFFEFE8DC), Color(0xFFB8A98F)), // slate
    CoverPalette(Color(0xFF7D5D6C), Color(0xFFF7EEE8), Color(0xFFE2C6C9)), // plum
    CoverPalette(Color(0xFFE5DACA), Color(0xFF3A2E24), Color(0xFF9C7B5B)), // parchment
    CoverPalette(Color(0xFF8C5A44), Color(0xFFF6EBDD), Color(0xFFE3BE98)), // terracotta
)

data class ExtraColors(val success: Color, val successContainer: Color, val warning: Color, val warningContainer: Color)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF5E6E52), Color(0xFFE2E8D7), Color(0xFF8A6A2F), Color(0xFFF3E6C8))
}

@Composable
fun RedirectTheme(appTheme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val extras = if (dark) {
        ExtraColors(Color(0xFFB4C2A4), Color(0xFF34402B), Color(0xFFE2C48A), Color(0xFF4A3B1E))
    } else {
        ExtraColors(Color(0xFF5E6E52), Color(0xFFE2E8D7), Color(0xFF8A6A2F), Color(0xFFF3E6C8))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extras, LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val LocalIsDark = staticCompositionLocalOf { false }
