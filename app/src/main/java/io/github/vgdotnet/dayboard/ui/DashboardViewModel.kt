package io.github.vgdotnet.dayboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vgdotnet.dayboard.data.DashboardRepository
import io.github.vgdotnet.dayboard.data.FakeDashboardRepository
import io.github.vgdotnet.dayboard.data.SettingsStore
import io.github.vgdotnet.dayboard.model.AppSettings
import io.github.vgdotnet.dayboard.model.CalendarEvent
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.model.UnreadSummary
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.notifications.NotificationInbox
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val zones: List<ZoneClock> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val inbox: List<InboxItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /** Day shown in the calendar; null means today. */
    val selectedDate: LocalDate? = null,
) {
    fun dateShown(now: ZonedDateTime): LocalDate = selectedDate ?: now.toLocalDate()

    fun eventsOn(date: LocalDate): List<CalendarEvent> =
        events.filter { it.start.withZoneSameInstant(home.zone).toLocalDate() == date }.sortedBy { it.start }

    /** The home zone is the first in the list; the user reorders the list to change it. */
    val home: ZoneClock get() = zones.first()

    fun nextEvent(now: ZonedDateTime): CalendarEvent? =
        events.filter { it.isUpcoming(now) }.minByOrNull { it.start }

    fun unread(source: InboxSource): UnreadSummary {
        val items = inbox.filter { it.source == source && it.unread }.sortedByDescending { it.time }
        val latest = items.firstOrNull()
        return UnreadSummary(
            source = source,
            count = items.size,
            latestLine = latest?.let { "${it.sender} · ${it.preview}" } ?: "ничего нового",
        )
    }

    /** Items that arrived within the last 10 minutes and are still unread: they blink. */
    fun recentUnread(now: ZonedDateTime): List<InboxItem> =
        inbox.filter { it.unread && Duration.between(it.time, now).toMinutes() <= RECENT_MINUTES }

    val totalUnread: Int get() = inbox.count { it.unread }

    companion object {
        const val RECENT_MINUTES = 10L
    }
}

class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: DashboardRepository = FakeDashboardRepository(),
) : AndroidViewModel(application) {

    private val store = SettingsStore(application)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _now = MutableStateFlow(ZonedDateTime.now())
    val now: StateFlow<ZonedDateTime> = _now.asStateFlow()

    init {
        val zones = repository.zones()
        val start = ZonedDateTime.now(zones.first().zone)
        _now.value = start
        _state.value = DashboardState(
            zones = zones,
            events = repository.events(start),
            tasks = repository.tasks(),
        )
        NotificationInbox.init(application)
        viewModelScope.launch {
            NotificationInbox.items.collect { list -> _state.update { it.copy(inbox = list) } }
        }
        viewModelScope.launch {
            val saved = store.load()
            _state.update { it.copy(zones = saved.zones ?: it.zones, settings = saved.settings) }
            loaded = true
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _now.value = ZonedDateTime.now(_state.value.home.zone)
            }
        }
    }

    /** Set once the saved zones and settings are in; until then nothing is written back. */
    private var loaded = false

    /** Writes zones and settings after a change; a new write replaces one still pending. */
    private var saveJob: Job? = null
    private fun persist() {
        if (!loaded) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            val s = _state.value
            store.save(s.zones, s.settings)
        }
    }

    fun toggleTask(id: String) = _state.update { s ->
        s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun markRead(id: String) = NotificationInbox.markRead(id)

    fun markAllRead() = NotificationInbox.markAllRead()

    /** Opens the message in Gmail or Slack and marks it read. */
    fun openItem(item: InboxItem) = NotificationInbox.open(getApplication(), item)

    fun shiftDay(delta: Long) = _state.update { s ->
        s.copy(selectedDate = s.dateShown(_now.value).plusDays(delta))
    }

    fun goToToday() = _state.update { it.copy(selectedDate = null) }

    fun addZone(zone: ZoneClock) {
        _state.update { s -> if (s.zones.any { it.id == zone.id }) s else s.copy(zones = s.zones + zone) }
        persist()
    }

    fun removeZone(id: String) {
        _state.update { s -> if (s.zones.size <= 1) s else s.copy(zones = s.zones.filterNot { it.id == id }) }
        persist()
    }

    fun moveZone(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.zones.indices || to !in s.zones.indices || from == to) s
            else s.copy(zones = s.zones.toMutableList().apply { add(to, removeAt(from)) })
        }
        persist()
    }

    fun toggleWorkHours() = updateSettings { it.copy(workHoursCollapsed = !it.workHoursCollapsed) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _state.update { s -> s.copy(settings = transform(s.settings)) }
        persist()
    }
}

val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun ZonedDateTime.hhmm(): String = format(TimeFormat)

fun ZonedDateTime.hhmmIn(zone: ZoneClock): String = withZoneSameInstant(zone.zone).format(TimeFormat)

fun formatHoursDelta(hours: Int): String = when {
    hours > 0 -> "+$hours ч"
    hours < 0 -> "-${-hours} ч"
    else -> "0 ч"
}

fun formatMinutesUntil(minutes: Long): String = when {
    minutes <= 0 -> "сейчас"
    minutes < 60 -> "через $minutes мин"
    else -> "через ${minutes / 60} ч ${minutes % 60} мин"
}
