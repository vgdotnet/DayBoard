package io.github.vgdotnet.dayboard.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.model.CalendarEvent
import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.components.SectionHeader
import io.github.vgdotnet.dayboard.ui.components.rememberOpenUrl
import io.github.vgdotnet.dayboard.ui.formatMinutesUntil
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

/** Phone "Сегодня" section: the next event in a highlighted card, the rest as rows, then tasks. */
@Composable
fun AgendaSection(
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    now: ZonedDateTime,
    onToggleTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DayBoard.colors
    val openUrl = rememberOpenUrl()
    val upcoming = events.filter { it.isUpcoming(now) }.sortedBy { it.start }
    val next = upcoming.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Сегодня") {
            Text("Google Календарь", fontSize = 12.sp, color = c.textSecondary)
        }
        if (next != null) {
            Card(Modifier.fillMaxWidth(), border = c.highlightBorder, onClick = { openUrl(next.meetUrl ?: next.openUrl) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(next.start.withZoneSameInstant(now.zone).hhmm(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accentText, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(next.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
                        val via = if (next.meetUrl != null) " · Google Meet" else ""
                        Text(formatMinutesUntil(next.minutesUntil(now)) + via, fontSize = 12.sp, color = c.accentText)
                    }
                }
            }
        }
        upcoming.drop(1).forEach { event ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(event.openUrl) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(event.start.withZoneSameInstant(now.zone).hhmm(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, modifier = Modifier.width(48.dp))
                Text(event.title, fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
            }
        }
        if (upcoming.isEmpty()) {
            Text("Событий больше нет", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(horizontal = 14.dp))
        }
        tasks.forEach { task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleTask(task.id) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(48.dp)) {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggleTask(task.id) },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = c.accent,
                            checkmarkColor = c.onAccent,
                            uncheckedColor = c.textSecondary,
                        ),
                    )
                }
                Text(
                    task.title,
                    fontSize = 15.sp,
                    color = if (task.done) c.textSecondary else c.text,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
