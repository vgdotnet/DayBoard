package io.github.vgdotnet.dayboard.model

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One clock. The home zone is the first entry of the zone list, see [io.github.vgdotnet.dayboard.ui.DashboardState.home]. */
data class ZoneClock(
    val id: String,
    val name: String,
    val shortName: String,
    val zone: ZoneId,
    val workStart: LocalTime = LocalTime.of(9, 0),
    val workEnd: LocalTime = LocalTime.of(18, 0),
) {
    fun dayPart(now: ZonedDateTime): DayPart {
        val local = now.withZoneSameInstant(zone).toLocalTime()
        return when {
            !local.isBefore(workStart) && local.isBefore(workEnd) -> DayPart.WORKING
            local.hour in 6 until workStart.hour -> DayPart.MORNING
            local.hour in workEnd.hour until 23 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }
    }

    /** Offset of this zone relative to [home] at the given instant, in whole hours. */
    fun hoursFrom(home: ZoneClock, now: ZonedDateTime): Int {
        val mine = now.withZoneSameInstant(zone).offset.totalSeconds
        val theirs = now.withZoneSameInstant(home.zone).offset.totalSeconds
        return (mine - theirs) / 3600
    }
}

enum class DayPart(val label: String) {
    WORKING("работает"),
    MORNING("утро"),
    EVENING("вечер"),
    NIGHT("ночь"),
}

/** Google Calendar event colours used in the day view. */
enum class EventTint(val argb: Long) {
    PEACOCK(0xFF039BE5),
    BLUEBERRY(0xFF3F51B5),
    SAGE(0xFF33B679),
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** ARGB colour of the block: the calendar's colour on the device. */
    val color: Long,
    val allDay: Boolean = false,
    val meetUrl: String? = null,
    /** content:// uri of the event for Google Calendar, or a web link. */
    val openUrl: String = "https://calendar.google.com",
    val calendarName: String = "",
    /** Zone of the other party, shown as "16:30 в Лондоне" under the event. */
    val counterpartZoneId: String? = null,
) {
    fun minutesUntil(now: ZonedDateTime): Long = Duration.between(now, start).toMinutes()
    fun isUpcoming(now: ZonedDateTime): Boolean = end.isAfter(now)
}

data class TaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
)

enum class InboxSource(val label: String, val openUrl: String, val packageName: String) {
    GMAIL("Gmail", "https://mail.google.com", "com.google.android.gm"),
    SLACK("Slack", "https://slack.com", "com.Slack"),
}

data class InboxItem(
    val id: String,
    val source: InboxSource,
    val sender: String,
    val preview: String,
    /** Label or channel: "Gmail · Работа", "Slack · личное". */
    val label: String,
    val time: ZonedDateTime,
    val unread: Boolean,
    val isMention: Boolean = false,
    val openUrl: String = source.openUrl,
)

data class UnreadSummary(
    val source: InboxSource,
    val count: Int,
    val latestLine: String,
)

data class AppSettings(
    val blinkEnabled: Boolean = true,
    val mailEnabled: Boolean = true,
    val slackEnabled: Boolean = true,
    val remindMinutes: Int = 10,
    val quietHoursEnabled: Boolean = true,
    val quietFrom: LocalTime = LocalTime.of(23, 0),
    val quietTo: LocalTime = LocalTime.of(8, 0),
    val pollMinutes: Int = 5,
    /** The working-hours strip is collapsed to one line. */
    val workHoursCollapsed: Boolean = false,
)
