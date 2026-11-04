package io.github.vgdotnet.dayboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.ui.DashboardState
import io.github.vgdotnet.dayboard.ui.components.BlinkDot
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.components.Segmented
import io.github.vgdotnet.dayboard.ui.components.SourceIcon
import io.github.vgdotnet.dayboard.ui.components.rememberOpenUrl
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import io.github.vgdotnet.dayboard.ui.today.RecentUnreadLabel
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekdayFormat = DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("ru"))

/** Phone "Входящие": title, "read all", filter, the list. */
@Composable
fun InboxScreen(
    state: DashboardState,
    now: ZonedDateTime,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DayBoard.colors
    Column(
        modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Входящие", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = c.text)
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.card)
                    .clickable(onClick = onMarkAllRead)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Прочитать все", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
            }
        }
        InboxList(state, now, onMarkRead, showHeader = true, Modifier.weight(1f))
    }
}

/** The shared list: filter segments, day groups, items. Used on the phone and as the tablet column. */
@Composable
fun InboxList(
    state: DashboardState,
    now: ZonedDateTime,
    onMarkRead: (String) -> Unit,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = DayBoard.colors
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val all = state.inbox.sortedByDescending { it.time }
    val mail = all.filter { it.source == InboxSource.GMAIL }
    val slack = all.filter { it.source == InboxSource.SLACK }
    val shown = when (filter) { 1 -> mail; 2 -> slack; else -> all }
    val recent = state.recentUnread(now).map { it.id }.toSet()
    val today = shown.filter { it.time.toLocalDate() == now.toLocalDate() }
    val earlier = shown - today.toSet()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
        Segmented(
            options = listOf("Все · ${all.count { it.unread }}", "Почта · ${mail.count { it.unread }}", "Slack · ${slack.count { it.unread }}"),
            selected = filter,
            height = if (compact) 32.dp else 36.dp,
            onSelect = { filter = it },
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showHeader && today.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Сегодня", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                        RecentUnreadLabel(state, now, ping = true)
                    }
                }
            }
            items(today, key = { it.id }) { item -> InboxRow(item, now, item.id in recent, compact, onMarkRead) }
            if (earlier.isNotEmpty()) {
                item { Text("Вчера", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary, modifier = Modifier.padding(start = 2.dp, top = 8.dp)) }
                items(earlier, key = { it.id }) { item -> InboxRow(item, now, false, compact, onMarkRead) }
            }
            if (!compact) {
                item {
                    Text(
                        "Нажатие открывает письмо или тред в Gmail / Slack и снимает отметку «не прочитано».",
                        fontSize = 12.sp,
                        color = c.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, now: ZonedDateTime, isRecent: Boolean, compact: Boolean, onMarkRead: (String) -> Unit) {
    val c = DayBoard.colors
    val openUrl = rememberOpenUrl()
    val timeText = if (Duration.between(item.time, now).toHours() < 20 && item.time.toLocalDate() == now.toLocalDate()) item.time.hhmm() else item.time.format(weekdayFormat)
    Card(Modifier.fillMaxWidth(), onClick = { onMarkRead(item.id); openUrl(item.openUrl) }) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceIcon(item.source, boxSize = 36.dp, iconSize = 18.dp, radius = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.sender, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1)
                        if (compact) {
                            Text("· ${item.label}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (item.isMention) c.accentText else c.textSecondary, maxLines = 1)
                        }
                    }
                    if (!compact) TimeLabel(timeText, item.unread && isRecent)
                }
                Text(item.preview, fontSize = 13.sp, color = c.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (item.isMention) c.accentText else c.textSecondary)
                }
            }
            if (compact) TimeLabel(timeText, item.unread && isRecent)
        }
    }
}

@Composable
private fun TimeLabel(text: String, highlighted: Boolean) {
    val c = DayBoard.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (highlighted) BlinkDot()
        Text(text, fontSize = 12.sp, fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal, color = if (highlighted) c.accentText else c.textSecondary)
    }
}
