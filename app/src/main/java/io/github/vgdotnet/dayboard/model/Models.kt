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
    /** Gmail account the message belongs to (from the notification), used for the unread count. */
    val account: String = "",
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
    /** Count unread in the Important label instead of the whole inbox. */
    val gmailImportantOnly: Boolean = false,
    /** Gmail accounts seen in notifications; the label provider is queried per account. */
    val gmailAccounts: Set<String> = emptySet(),
    val miner: MinerSettings = MinerSettings(),
)

/** Solo mining on a Stratum pool; defaults match the ESP32 miner (public-pool.io, the same wallet). */
data class MinerSettings(
    val enabled: Boolean = false,
    val host: String = "public-pool.io",
    val port: Int = 21496,
    val wallet: String = "bc1qhve4ahh4smm3haa2mt4p0zr35gda2hd95wdzt3",
    val worker: String = "tab",
    val password: String = "x",
    /** Share threshold asked from the pool; public-pool.io does not go below 1. */
    val suggestDifficulty: Double = 0.005,
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    val onlyWhenCharging: Boolean = true,
)
