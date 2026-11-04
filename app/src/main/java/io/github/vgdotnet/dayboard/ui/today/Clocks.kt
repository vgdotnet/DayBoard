package io.github.vgdotnet.dayboard.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.model.DayPart
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.formatHoursDelta
import io.github.vgdotnet.dayboard.ui.hhmmIn
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

/** One card per non-home zone: name, time, offset and whether they are at work now. */
@Composable
fun ZoneCards(
    zones: List<ZoneClock>,
    home: ZoneClock,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
    timeSize: Int = 24,
    labelSize: Int = 12,
    radius: Dp = 14.dp,
    padding: Dp = 12.dp,
    gap: Dp = 10.dp,
) {
    val c = DayBoard.colors
    val others = zones.drop(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        others.chunked(3).forEach { rowZones ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowZones.forEach { zone ->
                    val part = zone.dayPart(now)
                    val working = part == DayPart.WORKING
                    Card(Modifier.weight(1f), radius = radius) {
                        Column(Modifier.padding(horizontal = padding + 2.dp, vertical = padding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(zone.name, fontSize = labelSize.sp, fontWeight = FontWeight.Medium, color = c.textSecondary, maxLines = 1)
                            Text(now.hhmmIn(zone), fontSize = timeSize.sp, fontWeight = FontWeight.SemiBold, color = c.text, letterSpacing = (-0.5).sp)
                            Text(
                                "${formatHoursDelta(zone.hoursFrom(home, now))} · ${part.label}",
                                fontSize = labelSize.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (working) c.accentText else c.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(3 - rowZones.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Working hours of every zone on the home day timeline, with the "now" line.
 * A zone that is +d hours ahead works from (9 - d) to (18 - d) home time.
 * Tapping the header collapses the card to one line that names who is at work now.
 */
@Composable
fun WorkHoursCard(
    zones: List<ZoneClock>,
    home: ZoneClock,
    now: ZonedDateTime,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 72.dp,
    radius: Dp = 14.dp,
) {
    val c = DayBoard.colors
    val nowFraction = (now.hour * 60 + now.minute) / (24f * 60f)
    val working = zones.filter { it.dayPart(now) == DayPart.WORKING }.joinToString { it.name }
    Card(modifier.fillMaxWidth(), radius = radius, onClick = onToggle) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (collapsed) "Рабочие часы" else "Рабочие часы 9-18 по местному", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary, modifier = Modifier.padding(end = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (collapsed) (if (working.isEmpty()) "никто не работает" else "работают: $working") else "сейчас",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (collapsed && working.isNotEmpty()) c.accentText else c.textSecondary,
                        maxLines = 1,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = if (collapsed) "Развернуть" else "Свернуть",
                        tint = c.textSecondary,
                        modifier = Modifier.size(14.dp).rotate(if (collapsed) 90f else -90f),
                    )
                }
            }
            AnimatedVisibility(visible = !collapsed) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 8.dp
                val trackWidth = maxWidth - labelWidth - gap
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    zones.forEach { zone ->
                        val delta = zone.hoursFrom(home, now)
                        val startH = zone.workStart.hour + zone.workStart.minute / 60f - delta
                        val endH = zone.workEnd.hour + zone.workEnd.minute / 60f - delta
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Text(zone.name, fontSize = 11.sp, color = c.textSecondary, modifier = Modifier.width(labelWidth), maxLines = 1)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(c.cardInset, RoundedCornerShape(4.dp)),
                            ) {
                                WorkSpan(startH, endH, trackWidth)
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .offset(x = labelWidth + gap + trackWidth * nowFraction - 1.dp, y = (-2).dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(c.accent, RoundedCornerShape(1.dp)),
                    )
                }
            }
            }
        }
    }
}

/** Draws the [startH, endH) span (in home hours, may wrap around midnight) on a 24 h track. */
@Composable
private fun WorkSpan(startH: Float, endH: Float, trackWidth: Dp) {
    val c = DayBoard.colors
    val s = ((startH % 24) + 24) % 24
    val e = s + (endH - startH)
    val segments = if (e <= 24f) listOf(s to e) else listOf(s to 24f, 0f to e - 24f)
    segments.forEach { (from, to) ->
        val f = from.coerceIn(0f, 24f) / 24f
        val t = to.coerceIn(0f, 24f) / 24f
        if (t > f) {
            Box(
                Modifier
                    .offset(x = trackWidth * f)
                    .width(trackWidth * (t - f))
                    .height(8.dp)
                    .background(c.barFill, RoundedCornerShape(4.dp)),
            )
        }
    }
}
