package io.github.vgdotnet.dayboard.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.ui.DashboardState
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

fun ZonedDateTime.dateLine(): String = toLocalDate().dateLine()

fun LocalDate.dateLine(): String =
    format(dateFormat).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) }

/** Phone main screen: time, zone cards, work hours, agenda, unread. Designed to fit without scrolling. */
@Composable
fun TodayScreen(
    state: DashboardState,
    now: ZonedDateTime,
    onToggleTask: (String) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleWorkHours: () -> Unit,
    modifier: Modifier = Modifier,
    accessGranted: Boolean = true,
    onGrantAccess: () -> Unit = {},
) {
    val c = DayBoard.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${now.dateLine()} · ${state.home.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                Text(now.hhmm(), fontSize = 64.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp, lineHeight = 64.sp, color = c.text)
            }
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_gear), contentDescription = "Настройки", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }

        ZoneCards(state.zones, state.home, now)
        WorkHoursCard(state.zones, state.home, now, state.settings.workHoursCollapsed, onToggleWorkHours)
        AgendaSection(state.eventsOn(now.toLocalDate()), state.tasks, now, onToggleTask)
        UnreadSection(state, now, onOpenInbox, accessGranted = accessGranted, onGrantAccess = onGrantAccess)
    }
}
