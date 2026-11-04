package io.github.vgdotnet.dayboard.data

import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.model.ZoneClock
import java.time.ZoneId

interface DashboardRepository {
    fun zones(): List<ZoneClock>
    fun tasks(): List<TaskItem>
}

/**
 * Default zones and test tasks. Events come from the device calendar and the inbox from
 * notifications; tasks stay test data until Google Tasks is wired up.
 */
class FakeDashboardRepository : DashboardRepository {

    private val zones = listOf(
        ZoneClock("granada", "Гранада", "Гранада", ZoneId.of("Europe/Madrid")),
        ZoneClock("moscow", "Москва", "Москва", ZoneId.of("Europe/Moscow")),
        ZoneClock("novosibirsk", "Новосибирск", "Новосиб.", ZoneId.of("Asia/Novosibirsk")),
        ZoneClock("london", "Лондон", "Лондон", ZoneId.of("Europe/London")),
    )

    override fun zones(): List<ZoneClock> = zones

    override fun tasks(): List<TaskItem> = listOf(
        TaskItem("contract", "Ответить агентству по договору"),
        TaskItem("english", "Урок английского, глава 7"),
    )
}
