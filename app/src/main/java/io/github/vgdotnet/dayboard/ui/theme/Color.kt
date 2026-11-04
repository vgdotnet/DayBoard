package io.github.vgdotnet.dayboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens from docs/DESIGN.md. Beyond the Material 3 scheme the app needs a few of its own. */
@Immutable
data class DayBoardColors(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val cardInset: Color,
    val segmentSelected: Color,
    val text: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val barFill: Color,
    val accentText: Color,
    val accent: Color,
    val onAccent: Color,
    val highlightBorder: Color,
    val success: Color,
    val switchOff: Color,
    val switchThumb: Color,
    val switchThumbOn: Color,
) {
    val eventNowLine: Color get() = Color(0xFFEA4335)
    val taskCheck: Color get() = Color(0xFF4285F4)
    val onEvent: Color get() = Color.White
}

val DarkColors = DayBoardColors(
    background = Color(0xFF111318),
    card = Color(0xFF1A1D24),
    cardBorder = Color.Transparent,
    cardInset = Color(0xFF262A33),
    segmentSelected = Color(0xFF2C313B),
    text = Color(0xFFF2F3F5),
    textBody = Color(0xFFC5CAD3),
    textSecondary = Color(0xFF9AA0AB),
    textTertiary = Color(0xFF6E7A8F),
    barFill = Color(0xFF6E7A8F),
    accentText = Color(0xFFF5B544),
    accent = Color(0xFFF5B544),
    onAccent = Color(0xFF111318),
    highlightBorder = Color(0xFFF5B544),
    success = Color(0xFF7BC67E),
    switchOff = Color(0xFF3A404C),
    switchThumb = Color(0xFFF2F3F5),
    switchThumbOn = Color(0xFF111318),
)

val LightColors = DayBoardColors(
    background = Color(0xFFF6F7F9),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE6E8EC),
    cardInset = Color(0xFFE6E8EC),
    segmentSelected = Color(0xFFE6E8EC),
    text = Color(0xFF16181D),
    textBody = Color(0xFF3F4652),
    textSecondary = Color(0xFF5F6775),
    textTertiary = Color(0xFF6B7382),
    barFill = Color(0xFFA4ADBC),
    accentText = Color(0xFFB7791F),
    accent = Color(0xFFF5B544),
    onAccent = Color(0xFF1B1500),
    highlightBorder = Color(0xFFE0A733),
    success = Color(0xFF2E8B57),
    switchOff = Color(0xFFC9CDD5),
    switchThumb = Color(0xFF16181D),
    switchThumbOn = Color(0xFFF6F7F9),
)

val LocalDayBoardColors = staticCompositionLocalOf { DarkColors }
