package io.github.vgdotnet.dayboard.ui.components

import android.provider.Settings
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.ui.theme.DayBoard

/** Whether unread indicators may blink: the setting in the app and the system "remove animations" switch. */
val LocalBlinkEnabled = compositionLocalOf { true }

@Composable
fun BlinkProvider(enabledInSettings: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemAnimations = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    CompositionLocalProvider(LocalBlinkEnabled provides (enabledInSettings && systemAnimations), content = content)
}

/** blink: opacity 1 → low → 1, 1.2 s, ease-in-out. */
@Composable
fun Modifier.blink(active: Boolean = true, low: Float = 0.3f): Modifier {
    if (!active || !LocalBlinkEnabled.current) return this
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = low,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "alpha",
    )
    return this.alpha(alpha)
}

/** ping: a 2 dp accent ring that grows from the badge and fades, 1.6 s, ease-out. */
@Composable
fun PingRing(size: Dp, active: Boolean = true, maxScale: Float = 2.4f) {
    if (!active || !LocalBlinkEnabled.current) return
    val transition = rememberInfiniteTransition(label = "ping")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseOut), RepeatMode.Restart),
        label = "progress",
    )
    Box(
        Modifier
            .size(size)
            .scale(1f + (maxScale - 1f) * progress)
            .alpha(0.7f * (1f - progress))
            .border(2.dp, DayBoard.colors.accent, CircleShape),
    )
}

@Composable
fun BlinkDot(size: Dp = 8.dp, active: Boolean = true, ping: Boolean = false, low: Float = 0.3f) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (ping) PingRing(size, active)
        Box(
            Modifier
                .size(size)
                .blink(active, low)
                .background(DayBoard.colors.accent, CircleShape),
        )
    }
}

/** Accent badge with a count. Blinks while [active] and shows the ping ring. */
@Composable
fun CountBadge(count: Int, active: Boolean, size: Dp = 28.dp, fontSize: Int = 14, ping: Boolean = true) {
    val c = DayBoard.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (ping) PingRing(size, active)
        Box(
            Modifier
                .widthIn(min = size)
                .size(size)
                .blink(active)
                .background(c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = c.onAccent,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Small dot in the top-right corner of a navigation icon. */
@Composable
fun NavDot(active: Boolean, background: Color) {
    Box(
        Modifier
            .size(10.dp)
            .blink(active)
            .background(DayBoard.colors.accent, CircleShape)
            .border(2.dp, background, CircleShape),
    )
}
