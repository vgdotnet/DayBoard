package io.github.vgdotnet.dayboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The mockup uses Inter. Until the font files are added to res/font the system sans-serif
 * (Roboto) is used; the metrics are close enough for layout work.
 */
val DayBoardFontFamily: FontFamily = FontFamily.SansSerif

private fun schemeOf(c: DayBoardColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accentText,
        onPrimary = c.onAccent,
        primaryContainer = c.accent,
        onPrimaryContainer = c.onAccent,
        background = c.background,
        onBackground = c.text,
        surface = c.background,
        onSurface = c.text,
        surfaceVariant = c.cardInset,
        onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.card,
        surfaceContainerLow = c.card,
        surfaceContainerHigh = c.cardInset,
        outline = c.cardBorder,
        outlineVariant = c.cardInset,
        secondary = c.textSecondary,
        onSecondary = c.background,
        tertiary = c.textTertiary,
        error = c.eventNowLine,
    )
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, letterSpacing = (-2).sp, lineHeight = 64.sp),
    headlineMedium = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = DayBoardFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun DayBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalDayBoardColors provides colors) {
        MaterialTheme(
            colorScheme = schemeOf(colors, darkTheme),
            typography = typography,
            content = content,
        )
    }
}

object DayBoard {
    val colors: DayBoardColors
        @Composable @ReadOnlyComposable get() = LocalDayBoardColors.current
}
