package io.github.vgdotnet.dayboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vgdotnet.dayboard.data.DashboardRepository
import io.github.vgdotnet.dayboard.data.DeviceCalendar
import io.github.vgdotnet.dayboard.data.GmailCounts
import io.github.vgdotnet.dayboard.data.ImapMail
import io.github.vgdotnet.dayboard.data.MailCredentials
import io.github.vgdotnet.dayboard.data.FakeDashboardRepository
import io.github.vgdotnet.dayboard.data.SettingsStore
import io.github.vgdotnet.dayboard.model.AppSettings
import io.github.vgdotnet.dayboard.model.CalendarEvent
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.model.MinerSettings
import io.github.vgdotnet.dayboard.mining.MinerController
import io.github.vgdotnet.dayboard.mining.MinerRuntime
import io.github.vgdotnet.dayboard.mining.MinerStatus
import io.github.vgdotnet.dayboard.model.TaskItem
import io.github.vgdotnet.dayboard.model.UnreadSummary
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.notifications.NotificationInbox
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
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
    /** READ_CALENDAR granted; without it the agenda shows a prompt instead of events. */
    val calendarGranted: Boolean = false,
    /** Unread count from the Gmail label provider; null when it could not be read (then notifications are counted). */
    val gmailUnread: Int? = null,
    /** Gmail's provider permission granted. */
    val gmailGranted: Boolean = false,
    /** Unread mail from IMAP per account; the feed shows these instead of Gmail notifications. */
    val imapItems: List<InboxItem> = emptyList(),
    /** Accounts that have an app password. */
    val imapAccounts: Set<String> = emptySet(),
    /** Last IMAP error per account, for the settings screen. */
    val imapErrors: Map<String, String> = emptyMap(),
    /** Unread count from IMAP, used when the Gmail provider gives none. */
    val imapUnread: Int? = null,
) {
    /** The feed: IMAP mail where configured, plus everything from notifications except Gmail items of IMAP accounts. */
    val feed: List<InboxItem>
        get() = (imapItems + inbox.filter { !(it.source == InboxSource.GMAIL && it.account in imapAccounts) })
            .sortedByDescending { it.time }

    fun dateShown(now: ZonedDateTime): LocalDate = selectedDate ?: now.toLocalDate()

    fun eventsOn(date: LocalDate): List<CalendarEvent> =
        events.filter { it.start.withZoneSameInstant(home.zone).toLocalDate() == date }.sortedBy { it.start }

    /** The home zone is the first in the list; the user reorders the list to change it. */
    val home: ZoneClock get() = zones.first()

    fun nextEvent(now: ZonedDateTime): CalendarEvent? =
        events.filter { !it.allDay && it.isUpcoming(now) }.minByOrNull { it.start }

    fun unread(source: InboxSource): UnreadSummary {
        val items = feed.filter { it.source == source && it.unread }
        val latest = items.firstOrNull()
        return UnreadSummary(
            source = source,
            count = if (source == InboxSource.GMAIL) gmailUnread ?: imapUnread ?: items.size else items.size,
            latestLine = latest?.let { "${it.sender} · ${it.preview}" } ?: "ничего нового",
        )
    }

    /** Items that arrived within the last 10 minutes and are still unread: they blink. */
    fun recentUnread(now: ZonedDateTime): List<InboxItem> =
        feed.filter { it.unread && Duration.between(it.time, now).toMinutes() <= RECENT_MINUTES }

    val totalUnread: Int get() = feed.count { it.unread }

    companion object {
        const val RECENT_MINUTES = 10L
    }
}

class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: DashboardRepository = FakeDashboardRepository(),
) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val calendar = DeviceCalendar(application)
    private val gmail = GmailCounts(application)
    private val imap = ImapMail()
    private val credentials = MailCredentials(application)

    /** Live miner state, written by the mining service. */
    val miner: StateFlow<MinerStatus> = MinerRuntime.state
    private var stopObserving: (() -> Unit)? = null
    private var stopGmailObserving: (() -> Unit)? = null

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
            tasks = repository.tasks(),
            calendarGranted = calendar.hasPermission,
            gmailGranted = gmail.hasPermission,
        )
        refreshImapAccounts()
        refreshEvents()
        startObserving()
        NotificationInbox.init(application)
        viewModelScope.launch {
            NotificationInbox.items.collect { list ->
                _state.update { it.copy(inbox = list) }
                rememberGmailAccounts(list)
                refreshGmailCount()
            }
        }
        stopGmailObserving = gmail.observe { refreshGmailCount() }
        viewModelScope.launch {
            val saved = store.load()
            _state.update { it.copy(zones = saved.zones ?: it.zones, settings = saved.settings) }
            loaded = true
            refreshGmailCount()
            refreshImapAccounts()
            refreshMail()
            // The miner was on when the app last ran: bring it back
            if (saved.settings.miner.enabled && !MinerRuntime.state.value.running) MinerController.start(getApplication(), saved.settings.miner)
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val t = ZonedDateTime.now(_state.value.home.zone)
                _now.value = t
                if (t.second == 0 && t.minute % _state.value.settings.pollMinutes == 0) {
                    refreshGmailCount()
                    refreshMail()
                }
            }
        }
    }

    /** Loads the events around the shown day from the device calendar, off the main thread. */
    fun refreshEvents() {
        val s = _state.value
        if (!calendar.hasPermission) {
            if (s.calendarGranted || s.events.isNotEmpty()) _state.update { it.copy(calendarGranted = false, events = emptyList()) }
            return
        }
        val zone = s.home.zone
        val date = s.dateShown(_now.value)
        viewModelScope.launch(Dispatchers.IO) {
            val from = date.minusDays(1).atStartOfDay(zone).toInstant()
            val to = date.plusDays(8).atStartOfDay(zone).toInstant()
            val events = calendar.events(from, to, zone)
            _state.update { it.copy(events = events, calendarGranted = true) }
        }
    }

    /** Called after the permission dialog; a grant loads the events right away. */
    fun calendarPermissionChanged() {
        refreshEvents()
        startObserving()
    }

    /** Registering an observer on the calendar provider needs the permission too, so only once granted. */
    private fun startObserving() {
        if (stopObserving != null || !calendar.hasPermission) return
        stopObserving = runCatching { calendar.observe { refreshEvents() } }.getOrNull()
    }

    override fun onCleared() {
        stopObserving?.invoke()
        stopGmailObserving?.invoke()
    }

    /** Gmail accounts come from the notifications' sub text; new ones are saved for the counter. */
    private fun rememberGmailAccounts(items: List<InboxItem>) {
        val seen = items.filter { it.source == InboxSource.GMAIL }.map { it.account }.filter { it.isNotEmpty() }.toSet()
        val known = _state.value.settings.gmailAccounts
        if (seen.isNotEmpty() && !known.containsAll(seen)) updateSettings { it.copy(gmailAccounts = known + seen) }
    }

    private var gmailJob: Job? = null

    /**
     * Reads the unread count from the Gmail provider off the main thread. Gmail's observer fires
     * once per changed row, so a burst of calls is coalesced into one query.
     */
    fun refreshGmailCount() {
        gmailJob?.cancel()
        gmailJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            val settings = _state.value.settings
            val granted = gmail.hasPermission
            val count = if (granted) gmail.unread(settings.gmailAccounts, settings.gmailImportantOnly) else null
            _state.update { it.copy(gmailUnread = count, gmailGranted = granted) }
        }
    }

    private var mailJob: Job? = null

    /** Fetches unread mail over IMAP for every account with a password, off the main thread. */
    fun refreshMail() {
        val s = _state.value
        val accounts = s.settings.gmailAccounts.filter { credentials.hasPassword(it) }
        if (accounts.isEmpty()) {
            if (s.imapItems.isNotEmpty() || s.imapUnread != null) _state.update { it.copy(imapItems = emptyList(), imapUnread = null) }
            return
        }
        if (mailJob?.isActive == true) return
        mailJob = viewModelScope.launch(Dispatchers.IO) {
            val items = ArrayList<InboxItem>()
            val errors = HashMap<String, String>()
            var total = 0
            var any = false
            for (account in accounts) {
                val password = credentials.password(account) ?: continue
                runCatching { imap.unread(account, password, s.settings.gmailImportantOnly) }
                    .onSuccess { r -> items += r.items; total += r.unread; any = true }
                    .onFailure { e -> errors[account] = e.message ?: e.javaClass.simpleName }
            }
            _state.update { it.copy(imapItems = items, imapErrors = errors, imapUnread = if (any) total else null) }
        }
    }

    private fun refreshImapAccounts() {
        val accounts = _state.value.settings.gmailAccounts.filter { credentials.hasPassword(it) }.toSet()
        _state.update { it.copy(imapAccounts = accounts) }
    }

    /** Switches the miner on or off; the choice is persisted so it survives a restart. */
    fun toggleMiner(on: Boolean) {
        updateSettings { it.copy(miner = it.miner.copy(enabled = on)) }
        val s = _state.value.settings.miner
        if (on) MinerController.start(getApplication(), s) else MinerController.stop(getApplication())
    }

    /** Changes miner settings; a running miner is restarted with them. */
    fun updateMiner(transform: (MinerSettings) -> MinerSettings) {
        updateSettings { it.copy(miner = transform(it.miner)) }
        val s = _state.value.settings.miner
        if (s.enabled && MinerRuntime.state.value.running) MinerController.restart(getApplication(), s)
    }

    fun setMailPassword(account: String, password: String) {
        if (password.isBlank()) credentials.clear(account) else credentials.set(account, password)
        refreshImapAccounts()
        _state.update { it.copy(imapErrors = it.imapErrors - account) }
        refreshMail()
    }

    /** After the permission dialog: start observing and read the count. */
    fun gmailPermissionChanged() {
        if (stopGmailObserving == null) stopGmailObserving = gmail.observe { refreshGmailCount() }
        refreshGmailCount()
    }

    fun addGmailAccount(account: String) {
        val a = account.trim().lowercase()
        if (a.contains('@')) updateSettings { it.copy(gmailAccounts = it.gmailAccounts + a) }
    }

    fun removeGmailAccount(account: String) = updateSettings { it.copy(gmailAccounts = it.gmailAccounts - account) }

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

    fun markAllRead() = NotificationInbox.markAllRead()

    /** Opens the message in Gmail or Slack and marks it read. */
    fun openItem(item: InboxItem) = NotificationInbox.open(getApplication(), item)

    fun shiftDay(delta: Long) {
        _state.update { s -> s.copy(selectedDate = s.dateShown(_now.value).plusDays(delta)) }
        refreshEvents()
    }

    fun goToToday() {
        _state.update { it.copy(selectedDate = null) }
        refreshEvents()
    }

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
        if (from == 0 || to == 0) refreshEvents()
    }

    fun toggleWorkHours() = updateSettings { it.copy(workHoursCollapsed = !it.workHoursCollapsed) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _state.value.settings
        _state.update { s -> s.copy(settings = transform(s.settings)) }
        persist()
        val after = _state.value.settings
        if (after.gmailImportantOnly != before.gmailImportantOnly || after.gmailAccounts != before.gmailAccounts) {
            refreshGmailCount()
            refreshImapAccounts()
            refreshMail()
        }
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
