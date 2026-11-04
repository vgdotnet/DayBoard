package io.github.vgdotnet.dayboard.data

import io.github.vgdotnet.dayboard.model.ZoneClock
import java.time.ZoneId

/** A city the user can add as a clock. */
data class City(val name: String, val zoneId: String) {
    val zone: ZoneId get() = ZoneId.of(zoneId)

    /** The English name from the zone id: "Europe/Tbilisi" → "Tbilisi". */
    val englishName: String get() = zoneId.substringAfterLast('/').replace('_', ' ')

    fun toZoneClock(): ZoneClock = ZoneClock(
        id = zoneId,
        name = name,
        shortName = if (name.length > 9) name.take(8) + "." else name,
        zone = zone,
    )
}

/** Cities offered in "Добавить город". Searched by the Russian name, the English name or the zone id. */
object Cities {
    val all: List<City> = listOf(
        City("Гранада", "Europe/Madrid"),
        City("Мадрид", "Europe/Madrid"),
        City("Барселона", "Europe/Madrid"),
        City("Лиссабон", "Europe/Lisbon"),
        City("Лондон", "Europe/London"),
        City("Дублин", "Europe/Dublin"),
        City("Париж", "Europe/Paris"),
        City("Берлин", "Europe/Berlin"),
        City("Амстердам", "Europe/Amsterdam"),
        City("Брюссель", "Europe/Brussels"),
        City("Цюрих", "Europe/Zurich"),
        City("Вена", "Europe/Vienna"),
        City("Прага", "Europe/Prague"),
        City("Варшава", "Europe/Warsaw"),
        City("Стокгольм", "Europe/Stockholm"),
        City("Осло", "Europe/Oslo"),
        City("Копенгаген", "Europe/Copenhagen"),
        City("Хельсинки", "Europe/Helsinki"),
        City("Таллин", "Europe/Tallinn"),
        City("Рига", "Europe/Riga"),
        City("Вильнюс", "Europe/Vilnius"),
        City("Рим", "Europe/Rome"),
        City("Афины", "Europe/Athens"),
        City("Стамбул", "Europe/Istanbul"),
        City("Киев", "Europe/Kyiv"),
        City("Минск", "Europe/Minsk"),
        City("Кишинёв", "Europe/Chisinau"),
        City("Москва", "Europe/Moscow"),
        City("Санкт-Петербург", "Europe/Moscow"),
        City("Калининград", "Europe/Kaliningrad"),
        City("Самара", "Europe/Samara"),
        City("Екатеринбург", "Asia/Yekaterinburg"),
        City("Омск", "Asia/Omsk"),
        City("Новосибирск", "Asia/Novosibirsk"),
        City("Красноярск", "Asia/Krasnoyarsk"),
        City("Иркутск", "Asia/Irkutsk"),
        City("Якутск", "Asia/Yakutsk"),
        City("Владивосток", "Asia/Vladivostok"),
        City("Магадан", "Asia/Magadan"),
        City("Камчатка", "Asia/Kamchatka"),
        City("Тбилиси", "Asia/Tbilisi"),
        City("Ереван", "Asia/Yerevan"),
        City("Баку", "Asia/Baku"),
        City("Астана", "Asia/Almaty"),
        City("Алматы", "Asia/Almaty"),
        City("Ташкент", "Asia/Tashkent"),
        City("Бишкек", "Asia/Bishkek"),
        City("Душанбе", "Asia/Dushanbe"),
        City("Тель-Авив", "Asia/Tel_Aviv"),
        City("Дубай", "Asia/Dubai"),
        City("Эр-Рияд", "Asia/Riyadh"),
        City("Тегеран", "Asia/Tehran"),
        City("Карачи", "Asia/Karachi"),
        City("Дели", "Asia/Kolkata"),
        City("Мумбаи", "Asia/Kolkata"),
        City("Бангалор", "Asia/Kolkata"),
        City("Коломбо", "Asia/Colombo"),
        City("Дакка", "Asia/Dhaka"),
        City("Бангкок", "Asia/Bangkok"),
        City("Ханой", "Asia/Ho_Chi_Minh"),
        City("Джакарта", "Asia/Jakarta"),
        City("Сингапур", "Asia/Singapore"),
        City("Куала-Лумпур", "Asia/Kuala_Lumpur"),
        City("Манила", "Asia/Manila"),
        City("Гонконг", "Asia/Hong_Kong"),
        City("Шанхай", "Asia/Shanghai"),
        City("Пекин", "Asia/Shanghai"),
        City("Тайбэй", "Asia/Taipei"),
        City("Сеул", "Asia/Seoul"),
        City("Токио", "Asia/Tokyo"),
        City("Сидней", "Australia/Sydney"),
        City("Мельбурн", "Australia/Melbourne"),
        City("Перт", "Australia/Perth"),
        City("Окленд", "Pacific/Auckland"),
        City("Каир", "Africa/Cairo"),
        City("Йоханнесбург", "Africa/Johannesburg"),
        City("Найроби", "Africa/Nairobi"),
        City("Лагос", "Africa/Lagos"),
        City("Касабланка", "Africa/Casablanca"),
        City("Нью-Йорк", "America/New_York"),
        City("Бостон", "America/New_York"),
        City("Вашингтон", "America/New_York"),
        City("Майами", "America/New_York"),
        City("Торонто", "America/Toronto"),
        City("Чикаго", "America/Chicago"),
        City("Остин", "America/Chicago"),
        City("Денвер", "America/Denver"),
        City("Финикс", "America/Phoenix"),
        City("Лос-Анджелес", "America/Los_Angeles"),
        City("Сан-Франциско", "America/Los_Angeles"),
        City("Сиэтл", "America/Los_Angeles"),
        City("Ванкувер", "America/Vancouver"),
        City("Мехико", "America/Mexico_City"),
        City("Богота", "America/Bogota"),
        City("Лима", "America/Lima"),
        City("Сантьяго", "America/Santiago"),
        City("Буэнос-Айрес", "America/Argentina/Buenos_Aires"),
        City("Сан-Паулу", "America/Sao_Paulo"),
        City("Гонолулу", "Pacific/Honolulu"),
        City("UTC", "UTC"),
    )

    fun search(query: String): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.englishName.lowercase().contains(q) ||
                it.zoneId.lowercase().contains(q)
        }
    }
}
