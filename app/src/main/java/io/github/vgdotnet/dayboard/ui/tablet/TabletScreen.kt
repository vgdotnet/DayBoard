package io.github.vgdotnet.dayboard.ui.tablet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.ui.DashboardState
import io.github.vgdotnet.dayboard.ui.components.SectionHeader
import io.github.vgdotnet.dayboard.ui.components.rememberOpenUrl
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.inbox.AccessPrompt
import io.github.vgdotnet.dayboard.ui.inbox.InboxList
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import io.github.vgdotnet.dayboard.ui.today.DayViewPanel
import io.github.vgdotnet.dayboard.ui.today.RecentUnreadLabel
import io.github.vgdotnet.dayboard.ui.today.WorkHoursCard
import io.github.vgdotnet.dayboard.ui.today.ZoneCards
import io.github.vgdotnet.dayboard.ui.today.dateLine
import java.time.ZonedDateTime

/**
 * Tablet landscape "Сегодня". Top row across the width: the big clock and the zone cards.
 * Below, two equal columns: the day view on the left; working hours and the unread feed on the right.
 */
@Composable
fun TabletTodayScreen(
    state: DashboardState,
    now: ZonedDateTime,
    onToggleTask: (String) -> Unit,
    onOpen: (InboxItem) -> Unit,
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onToggleWorkHours: () -> Unit,
    onGrantCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = state.dateShown(now)
    val c = DayBoard.colors
    val openUrl = rememberOpenUrl()
    val offset = now.offset.totalSeconds / 3600
    val gap = 24.dp
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${now.dateLine()} · ${state.home.name} · UTC${if (offset >= 0) "+" else ""}$offset", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                Text(now.hhmm(), fontSize = 72.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2.5).sp, lineHeight = 72.sp, color = c.text)
            }
            ZoneCards(state.zones, state.home, now, Modifier.weight(1f), timeSize = 26, labelSize = 12, radius = 16.dp, padding = 12.dp, gap = 10.dp)
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            DayViewPanel(state.eventsOn(date), state.tasks, state.zones, now, date, onToggleTask, onPrevDay, onNextDay, onToday, Modifier.weight(1f).fillMaxHeight(), showTasks = false, calendarGranted = state.calendarGranted, onGrantCalendar = onGrantCalendar)

            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkHoursCard(state.zones, state.home, now, state.settings.workHoursCollapsed, onToggleWorkHours, labelWidth = 80.dp, radius = 16.dp)
                SectionHeader("Непрочитанное", titleSize = 16) { RecentUnreadLabel(state, now, ping = true) }
                if (!accessGranted) AccessPrompt(onGrantAccess)
                InboxList(state, now, onOpen, showHeader = false, Modifier.weight(1f), compact = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InboxSource.entries.forEach { source ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.card)
                                .clickable { openUrl(source.openUrl) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Открыть ${source.label}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
