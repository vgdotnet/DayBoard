package io.github.vgdotnet.dayboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.vgdotnet.dayboard.model.AppSettings
import io.github.vgdotnet.dayboard.model.ZoneClock
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dayboard")

/** What survives a restart: the zone list and the settings. */
data class Persisted(val zones: List<ZoneClock>?, val settings: AppSettings)

/**
 * Settings and zones in DataStore Preferences. Zones are stored as text, one zone per line,
 * fields separated by tabs: id, name, short name, zone id, isHome, work start, work end.
 */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    suspend fun load(): Persisted {
        val p = store.data.first()
        val zones = p[ZONES]?.let(::decodeZones)?.takeIf { it.isNotEmpty() }
        val defaults = AppSettings()
        val settings = AppSettings(
            blinkEnabled = p[BLINK] ?: defaults.blinkEnabled,
            mailEnabled = p[MAIL] ?: defaults.mailEnabled,
            slackEnabled = p[SLACK] ?: defaults.slackEnabled,
            remindMinutes = p[REMIND] ?: defaults.remindMinutes,
            quietHoursEnabled = p[QUIET] ?: defaults.quietHoursEnabled,
            quietFrom = p[QUIET_FROM]?.let(::minutesToTime) ?: defaults.quietFrom,
            quietTo = p[QUIET_TO]?.let(::minutesToTime) ?: defaults.quietTo,
            pollMinutes = p[POLL] ?: defaults.pollMinutes,
            workHoursCollapsed = p[WORK_HOURS_COLLAPSED] ?: defaults.workHoursCollapsed,
        )
        return Persisted(zones, settings)
    }

    suspend fun save(zones: List<ZoneClock>, settings: AppSettings) {
        store.edit { p ->
            p[ZONES] = encodeZones(zones)
            p[BLINK] = settings.blinkEnabled
            p[MAIL] = settings.mailEnabled
            p[SLACK] = settings.slackEnabled
            p[REMIND] = settings.remindMinutes
            p[QUIET] = settings.quietHoursEnabled
            p[QUIET_FROM] = settings.quietFrom.toSecondOfDay() / 60
            p[QUIET_TO] = settings.quietTo.toSecondOfDay() / 60
            p[POLL] = settings.pollMinutes
            p[WORK_HOURS_COLLAPSED] = settings.workHoursCollapsed
        }
    }

    private fun encodeZones(zones: List<ZoneClock>): String = zones.joinToString("\n") { z ->
        listOf(z.id, z.name, z.shortName, z.zone.id, z.isHome.toString(), z.workStart.toSecondOfDay() / 60, z.workEnd.toSecondOfDay() / 60)
            .joinToString("\t")
    }

    private fun decodeZones(text: String): List<ZoneClock> = text.lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 7) return@mapNotNull null
        val zone = runCatching { ZoneId.of(f[3]) }.getOrNull() ?: return@mapNotNull null
        ZoneClock(
            id = f[0],
            name = f[1],
            shortName = f[2],
            zone = zone,
            isHome = f[4].toBoolean(),
            workStart = f[5].toIntOrNull()?.let(::minutesToTime) ?: LocalTime.of(9, 0),
            workEnd = f[6].toIntOrNull()?.let(::minutesToTime) ?: LocalTime.of(18, 0),
        )
    }

    private fun minutesToTime(minutes: Int): LocalTime = LocalTime.ofSecondOfDay((minutes.coerceIn(0, 24 * 60 - 1) * 60).toLong())

    private companion object {
        val ZONES = stringPreferencesKey("zones")
        val BLINK = booleanPreferencesKey("blink")
        val MAIL = booleanPreferencesKey("mail")
        val SLACK = booleanPreferencesKey("slack")
        val REMIND = intPreferencesKey("remind_minutes")
        val QUIET = booleanPreferencesKey("quiet_hours")
        val QUIET_FROM = intPreferencesKey("quiet_from")
        val QUIET_TO = intPreferencesKey("quiet_to")
        val POLL = intPreferencesKey("poll_minutes")
        val WORK_HOURS_COLLAPSED = booleanPreferencesKey("work_hours_collapsed")
    }
}
