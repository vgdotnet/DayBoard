@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.vgdotnet.dayboard.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.model.CalendarEvent
import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.ui.components.rememberOpenUrl
import io.github.vgdotnet.dayboard.ui.formatMinutesUntil
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.hhmmIn
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

/**
 * Tablet "Сегодня" panel in the style of the Google Calendar day view:
 * one date line with day arrows, optionally a row of task chips, an hour scale with coloured
 * event blocks and the red "now" line.
 */
@Composable
fun DayViewPanel(
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    zones: List<ZoneClock>,
    now: ZonedDateTime,
    date: LocalDate,
    onToggleTask: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
    showTasks: Boolean = true,
) {
    val c = DayBoard.colors
    val isToday = date == now.toLocalDate()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date.dateLine(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    Box(
                        Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.card)
                            .clickable(onClick = onToday)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Сегодня", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.accentText)
                    }
                }
                DayArrow(R.drawable.ic_chevron_left, "Предыдущий день", onPrevDay)
                DayArrow(R.drawable.ic_chevron_right, "Следующий день", onNextDay)
            }
        }

        if (showTasks) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("Задачи", fontSize = 11.sp, color = c.textSecondary, modifier = Modifier.width(48.dp).padding(top = 6.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tasks.forEach { task -> TaskChip(task) { onToggleTask(task.id) } }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardInset))
        }

        HourGrid(events, zones, now, date, Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp))
    }
}

@Composable
private fun DayArrow(icon: Int, description: String, onClick: () -> Unit) {
    val c = DayBoard.colors
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(c.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = c.textSecondary, modifier = Modifier.size(14.dp))
    }
}

/** Task chip with the round blue checkbox of Google Tasks. */
@Composable
private fun TaskChip(task: TaskItem, onToggle: () -> Unit) {
    val c = DayBoard.colors
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .border(2.dp, c.taskCheck, CircleShape)
                .background(if (task.done) c.taskCheck else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(
            task.title,
            fontSize = 13.sp,
            color = if (task.done) c.textSecondary else c.text,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
        )
    }
}

/**
 * Full day on a scrollable canvas with a fixed height per hour. Today opens with the "now" line
 * near the top, another day opens at its first event or at 8:00.
 */
@Composable
private fun HourGrid(
    events: List<CalendarEvent>,
    zones: List<ZoneClock>,
    now: ZonedDateTime,
    date: LocalDate,
    modifier: Modifier = Modifier,
    hourHeight: Dp = 56.dp,
) {
    val c = DayBoard.colors
    val openUrl = rememberOpenUrl()
    val gutter = 48.dp
    val isToday = date == now.toLocalDate()
    val dayStart = date.atStartOfDay(now.zone)
    val h = hourHeight * 24
    fun y(t: ZonedDateTime): Dp = hourHeight * (java.time.Duration.between(dayStart, t).toMinutes() / 60f)

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) {
        val anchor = when {
            isToday -> y(now)
            events.isNotEmpty() -> y(events.first().start)
            else -> hourHeight * 8
        }
        scroll.scrollTo(with(density) { (anchor - 16.dp).coerceAtLeast(0.dp).roundToPx() })
    }

    BoxWithConstraints(modifier.verticalScroll(scroll)) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(h + 8.dp)) {
            for (i in 0..24) {
                val lineY = hourHeight * i
                Box(Modifier.offset(y = lineY).fillMaxWidth().height(1.dp).background(c.cardInset))
                if (i in 1..23) {
                    Text(
                        "%02d:00".format(i),
                        fontSize = 11.sp,
                        color = c.textSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.offset(y = lineY - 7.dp).width(40.dp),
                    )
                }
            }
            Box(Modifier.offset(x = gutter).width(1.dp).height(h).background(c.cardInset))

            events.forEach { event ->
                val top = y(event.start).coerceIn(0.dp, h)
                val bottom = y(event.end).coerceIn(0.dp, h)
                if (bottom <= top) return@forEach
                val counterpart = event.counterpartZoneId?.let { id -> zones.firstOrNull { it.id == id } }
                val details = buildString {
                    append(event.start.hhmm()); append(" - "); append(event.end.hhmm())
                    if (event.meetUrl != null) append(" · Google Meet")
                    if (counterpart != null) append(" · ${event.start.hhmmIn(counterpart)} в ${inZoneName(counterpart)}")
                    if (event.isUpcoming(now) && event.start.isAfter(now)) append(" · ${formatMinutesUntil(event.minutesUntil(now))}")
                }
                Column(
                    Modifier
                        .offset(x = gutter + 4.dp, y = top + 1.dp)
                        .width(width - gutter - 8.dp)
                        .height(bottom - top - 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(event.tint.argb))
                        .clickable { openUrl(event.meetUrl ?: event.openUrl) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(event.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, color = c.onEvent, maxLines = 1)
                    Text(details, fontSize = 11.sp, lineHeight = 14.sp, color = c.onEvent.copy(alpha = 0.9f), maxLines = 1)
                }
            }

            if (isToday) {
                val nowY = y(now)
                Box(Modifier.offset(x = gutter - 6.dp, y = nowY - 1.dp).width(width - gutter + 6.dp).height(2.dp).background(c.eventNowLine))
                Box(Modifier.offset(x = gutter - 12.dp, y = nowY - 6.dp).size(12.dp).background(c.eventNowLine, CircleShape))
            }
        }
    }
}

/** "в Лондоне", "в Москве": the prepositional case for the zones in the test data. */
private fun inZoneName(zone: ZoneClock): String = when (zone.id) {
    "london" -> "Лондоне"
    "moscow" -> "Москве"
    "novosibirsk" -> "Новосибирске"
    "granada" -> "Гранаде"
    else -> zone.name
}
