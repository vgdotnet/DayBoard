package io.github.vgdotnet.dayboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.vgdotnet.dayboard.model.AppSettings
import io.github.vgdotnet.dayboard.model.MinerSettings
import io.github.vgdotnet.dayboard.model.ZoneClock
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dayboard")

/** What survives a restart: the zone list and the settings. */
data class Persisted(val zones: List<ZoneClock>?, val settings: AppSettings)

/**
 * Settings and zones in DataStore Preferences. Zones are stored as text, one zone per line,
 * fields separated by tabs: id, name, short name, zone id, (unused), work start, work end.
 * The first line is the home zone.
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
            gmailImportantOnly = p[GMAIL_IMPORTANT] ?: defaults.gmailImportantOnly,
            gmailAccounts = p[GMAIL_ACCOUNTS] ?: defaults.gmailAccounts,
            miner = MinerSettings(
                enabled = p[MINER_ENABLED] ?: defaults.miner.enabled,
                host = p[MINER_HOST] ?: defaults.miner.host,
                port = p[MINER_PORT] ?: defaults.miner.port,
                wallet = p[MINER_WALLET] ?: defaults.miner.wallet,
                worker = p[MINER_WORKER] ?: defaults.miner.worker,
                password = p[MINER_PASSWORD] ?: defaults.miner.password,
                suggestDifficulty = p[MINER_DIFF] ?: defaults.miner.suggestDifficulty,
                threads = p[MINER_THREADS] ?: defaults.miner.threads,
                onlyWhenCharging = p[MINER_CHARGING] ?: defaults.miner.onlyWhenCharging,
            ),
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
            p[GMAIL_IMPORTANT] = settings.gmailImportantOnly
            p[GMAIL_ACCOUNTS] = settings.gmailAccounts
            p[MINER_ENABLED] = settings.miner.enabled
            p[MINER_HOST] = settings.miner.host
            p[MINER_PORT] = settings.miner.port
            p[MINER_WALLET] = settings.miner.wallet
            p[MINER_WORKER] = settings.miner.worker
            p[MINER_PASSWORD] = settings.miner.password
            p[MINER_DIFF] = settings.miner.suggestDifficulty
            p[MINER_THREADS] = settings.miner.threads
            p[MINER_CHARGING] = settings.miner.onlyWhenCharging
        }
    }

    private fun encodeZones(zones: List<ZoneClock>): String = zones.joinToString("\n") { z ->
        listOf(z.id, z.name, z.shortName, z.zone.id, "", z.workStart.toSecondOfDay() / 60, z.workEnd.toSecondOfDay() / 60)
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
        val GMAIL_IMPORTANT = booleanPreferencesKey("gmail_important_only")
        val GMAIL_ACCOUNTS = stringSetPreferencesKey("gmail_accounts")
        val MINER_ENABLED = booleanPreferencesKey("miner_enabled")
        val MINER_HOST = stringPreferencesKey("miner_host")
        val MINER_PORT = intPreferencesKey("miner_port")
        val MINER_WALLET = stringPreferencesKey("miner_wallet")
        val MINER_WORKER = stringPreferencesKey("miner_worker")
        val MINER_PASSWORD = stringPreferencesKey("miner_password")
        val MINER_DIFF = doublePreferencesKey("miner_suggest_difficulty")
        val MINER_THREADS = intPreferencesKey("miner_threads")
        val MINER_CHARGING = booleanPreferencesKey("miner_only_when_charging")
    }
}
