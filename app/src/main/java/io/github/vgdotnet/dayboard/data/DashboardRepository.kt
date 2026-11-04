package io.github.vgdotnet.dayboard.data

import io.github.vgdotnet.dayboard.model.CalendarEvent
import io.github.vgdotnet.dayboard.model.EventTint
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.model.ZoneClock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

interface DashboardRepository {
    fun zones(): List<ZoneClock>
    fun events(now: ZonedDateTime): List<CalendarEvent>
    fun tasks(): List<TaskItem>
    fun inbox(now: ZonedDateTime): List<InboxItem>
}

/**
 * Test data that mirrors the mockup. Times are anchored to "now" so the demo always
 * looks like the design: the stand-up is in 28 minutes, the client call later today.
 */
class FakeDashboardRepository : DashboardRepository {

    private val zones = listOf(
        ZoneClock("granada", "Гранада", "Гранада", ZoneId.of("Europe/Madrid"), isHome = true),
        ZoneClock("moscow", "Москва", "Москва", ZoneId.of("Europe/Moscow")),
        ZoneClock("novosibirsk", "Новосибирск", "Новосиб.", ZoneId.of("Asia/Novosibirsk")),
        ZoneClock("london", "Лондон", "Лондон", ZoneId.of("Europe/London")),
    )

    override fun zones(): List<ZoneClock> = zones

    override fun events(now: ZonedDateTime): List<CalendarEvent> {
        val base = now.truncatedTo(ChronoUnit.MINUTES)
        val standup = base.plusMinutes(28)
        val client = base.plusMinutes(178)
        val english = base.plusMinutes(328)
        return listOf(
            CalendarEvent(
                id = "standup",
                title = "Стендап команды",
                start = standup,
                end = standup.plusMinutes(45),
                tint = EventTint.PEACOCK,
                meetUrl = "https://meet.google.com",
            ),
            CalendarEvent(
                id = "client",
                title = "Созвон с клиентом",
                start = client,
                end = client.plusMinutes(60),
                tint = EventTint.BLUEBERRY,
                counterpartZoneId = "london",
            ),
            CalendarEvent(
                id = "english",
                title = "Английский, Azar глава 7",
                start = english,
                end = english.plusMinutes(60),
                tint = EventTint.SAGE,
            ),
            CalendarEvent(
                id = "doctor",
                title = "Визит к врачу",
                start = base.plusDays(1).withHour(10).withMinute(0),
                end = base.plusDays(1).withHour(11).withMinute(0),
                tint = EventTint.PEACOCK,
            ),
        )
    }

    override fun tasks(): List<TaskItem> = listOf(
        TaskItem("contract", "Ответить агентству по договору"),
        TaskItem("english", "Урок английского, глава 7"),
    )

    override fun inbox(now: ZonedDateTime): List<InboxItem> {
        val base = now.truncatedTo(ChronoUnit.MINUTES)
        return listOf(
            InboxItem(
                id = "slack-anton",
                source = InboxSource.SLACK,
                sender = "#dev · Антон",
                preview = "@vitalii, глянешь PR до стендапа?",
                label = "упоминание",
                time = base.minusMinutes(4),
                unread = true,
                isMention = true,
            ),
            InboxItem(
                id = "mail-gabriel",
                source = InboxSource.GMAIL,
                sender = "Гэбриэль",
                preview = "Review ticket #42 - comments inline",
                label = "Gmail · Работа",
                time = base.minusMinutes(8),
                unread = true,
            ),
            InboxItem(
                id = "slack-marina",
                source = InboxSource.SLACK,
                sender = "Марина",
                preview = "Пришлю акт к вечеру, ок?",
                label = "Slack · личное",
                time = base.minusMinutes(142),
                unread = true,
            ),
            InboxItem(
                id = "mail-hr",
                source = InboxSource.GMAIL,
                sender = "HR, Contoso",
                preview = "Interview: .NET Engineer - slots for Thursday",
                label = "Gmail · Вакансии",
                time = base.minusMinutes(232),
                unread = true,
            ),
            InboxItem(
                id = "mail-bank",
                source = InboxSource.GMAIL,
                sender = "Банк",
                preview = "Выписка за сентябрь готова",
                label = "Gmail · Личное",
                time = base.minusDays(1).withHour(11).withMinute(5),
                unread = true,
            ),
        )
    }
}
