package io.github.vgdotnet.dayboard.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.ui.DashboardState
import io.github.vgdotnet.dayboard.ui.components.BlinkDot
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.components.CountBadge
import io.github.vgdotnet.dayboard.ui.components.SectionHeader
import io.github.vgdotnet.dayboard.ui.components.SourceIcon
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

/** "N новых за 10 мин" with a blinking dot; nothing when there is nothing new. */
@Composable
fun RecentUnreadLabel(state: DashboardState, now: ZonedDateTime, ping: Boolean = false) {
    val recent = state.recentUnread(now)
    if (recent.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BlinkDot(active = true, ping = ping)
        Text(
            "${recent.size} ${plural(recent.size, "новое", "новых", "новых")} за ${DashboardState.RECENT_MINUTES} мин",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = DayBoard.colors.accentText,
        )
    }
}

/** Phone "Непрочитанное" block: one row per source with a blinking counter. */
@Composable
fun UnreadSection(
    state: DashboardState,
    now: ZonedDateTime,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DayBoard.colors
    val recentSources = state.recentUnread(now).map { it.source }.toSet()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Непрочитанное") { RecentUnreadLabel(state, now) }
        InboxSource.entries.forEach { source ->
            val summary = state.unread(source)
            Card(Modifier.fillMaxWidth(), onClick = onOpenInbox) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SourceIcon(source)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(source.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text(summary.latestLine, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (summary.count > 0) {
                        CountBadge(summary.count, active = source in recentSources)
                    }
                }
            }
        }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1 && n100 != 11 -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}
