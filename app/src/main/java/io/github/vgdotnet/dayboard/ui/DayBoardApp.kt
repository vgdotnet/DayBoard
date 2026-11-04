package io.github.vgdotnet.dayboard.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.notifications.NotificationInbox
import io.github.vgdotnet.dayboard.ui.components.BlinkProvider
import io.github.vgdotnet.dayboard.ui.components.NavDot
import io.github.vgdotnet.dayboard.ui.inbox.InboxScreen
import io.github.vgdotnet.dayboard.ui.settings.SettingsScreen
import io.github.vgdotnet.dayboard.ui.tablet.TabletTodayScreen
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import io.github.vgdotnet.dayboard.ui.today.TodayScreen
import java.time.ZonedDateTime

enum class Destination(val label: String, val icon: Int) {
    Today("Сегодня", R.drawable.ic_clock),
    Inbox("Входящие", R.drawable.ic_inbox),
    Settings("Настройки", R.drawable.ic_sliders),
}

/** Width from which the layout switches to the tablet shell (side rail, two columns). */
private const val TABLET_MIN_WIDTH_DP = 840

@Composable
fun DayBoardApp(viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Today) }
    val config = LocalConfiguration.current
    val tablet = config.screenWidthDp >= TABLET_MIN_WIDTH_DP && config.screenWidthDp > config.screenHeightDp
    val context = LocalContext.current
    // Re-read every tick (once a second): cheap, and picks up the grant when the user comes back from Android settings.
    val accessGranted = remember(now) { NotificationInbox.isEnabled(context) }
    val onGrantAccess = { NotificationInbox.openAccessSettings(context) }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.calendarPermissionChanged() }
    val onGrantCalendar = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }

    BlinkProvider(enabledInSettings = state.settings.blinkEnabled) {
        Box(Modifier.fillMaxSize().background(DayBoard.colors.background)) {
            if (tablet) {
                TabletShell(state, now, destination, { destination = it }, viewModel, accessGranted, onGrantAccess, onGrantCalendar)
            } else {
                PhoneShell(state, now, destination, { destination = it }, viewModel, accessGranted, onGrantAccess, onGrantCalendar)
            }
        }
    }
}

@Composable
private fun PhoneShell(
    state: DashboardState,
    now: ZonedDateTime,
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    viewModel: DashboardViewModel,
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    onGrantCalendar: () -> Unit,
) {
    val c = DayBoard.colors
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f)) {
            when (destination) {
                Destination.Today -> TodayScreen(state, now, viewModel::toggleTask, { onNavigate(Destination.Inbox) }, { onNavigate(Destination.Settings) }, viewModel::toggleWorkHours, accessGranted = accessGranted, onGrantAccess = onGrantAccess, calendarGranted = state.calendarGranted, onGrantCalendar = onGrantCalendar)
                Destination.Inbox -> InboxScreen(state, now, viewModel::openItem, viewModel::markAllRead, accessGranted, onGrantAccess)
                Destination.Settings -> SettingsScreen(state.zones, now, state.settings, viewModel::updateSettings, { viewModel.addZone(it.toZoneClock()) }, viewModel::removeZone, viewModel::moveZone, accessGranted, onGrantAccess, state.calendarGranted, onGrantCalendar)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardInset))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)) {
            Destination.entries.forEach { dest ->
                val selected = dest == destination
                val tint = if (selected) c.accentText else c.textSecondary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onNavigate(dest) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NavIcon(dest, tint, showDot = dest == Destination.Inbox && state.recentUnread(now).isNotEmpty())
                    Text(dest.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint)
                }
            }
        }
    }
}

@Composable
private fun TabletShell(
    state: DashboardState,
    now: ZonedDateTime,
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    viewModel: DashboardViewModel,
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    onGrantCalendar: () -> Unit,
) {
    val c = DayBoard.colors
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.width(96.dp).fillMaxHeight().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Destination.entries.forEach { dest ->
                val selected = dest == destination
                val tint = if (selected) c.accentText else c.textSecondary
                Column(
                    Modifier
                        .width(72.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) c.card else c.background)
                        .clickable { onNavigate(dest) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    NavIcon(dest, tint, showDot = dest == Destination.Inbox && state.recentUnread(now).isNotEmpty(), size = 24.dp)
                    Text(dest.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.cardInset))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (destination) {
                Destination.Today, Destination.Inbox -> TabletTodayScreen(state, now, viewModel::toggleTask, viewModel::openItem, accessGranted, onGrantAccess, { viewModel.shiftDay(-1) }, { viewModel.shiftDay(1) }, viewModel::goToToday, viewModel::toggleWorkHours, onGrantCalendar)
                Destination.Settings -> SettingsScreen(state.zones, now, state.settings, viewModel::updateSettings, { viewModel.addZone(it.toZoneClock()) }, viewModel::removeZone, viewModel::moveZone, accessGranted, onGrantAccess, state.calendarGranted, onGrantCalendar, Modifier.width(520.dp))
            }
        }
    }
}

@Composable
private fun NavIcon(dest: Destination, tint: androidx.compose.ui.graphics.Color, showDot: Boolean, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Box(Modifier.size(size)) {
        Icon(painterResource(dest.icon), contentDescription = dest.label, tint = tint, modifier = Modifier.size(size))
        if (showDot) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 0.dp)) {
                Box(Modifier.padding(start = 4.dp)) { NavDot(active = true, background = DayBoard.colors.background) }
            }
        }
    }
}
