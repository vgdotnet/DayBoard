package io.github.vgdotnet.dayboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.data.City
import io.github.vgdotnet.dayboard.model.AppSettings
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SettingsScreen(
    zones: List<ZoneClock>,
    now: ZonedDateTime,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onAddZone: (City) -> Unit,
    onRemoveZone: (String) -> Unit,
    onMoveZone: (Int, Int) -> Unit,
    accessGranted: Boolean,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DayBoard.colors
    var addingCity by rememberSaveable { mutableStateOf(false) }
    if (addingCity) {
        AddCityDialog(zones, now, onAdd = { onAddZone(it); addingCity = false }, onDismiss = { addingCity = false })
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Настройки", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = c.text)

        Group("Часовые пояса", hint = "тяните за ручку, чтобы изменить порядок") {
            ZoneList(zones, now, onMove = onMoveZone, onRemove = onRemoveZone, onAdd = { addingCity = true })
        }

        Group("Источники") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onGrantAccess).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Gmail и Slack", fontSize = 15.sp, color = c.text)
                    Text("из уведомлений на этом устройстве", fontSize = 12.sp, color = c.textSecondary)
                }
                Text(
                    if (accessGranted) "доступ есть" else "дать доступ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (accessGranted) c.success else c.accentText,
                )
            }
            Divider()
            AccountRow("Google Календарь", "события из календаря устройства", "не подключён")
        }

        Group("Уведомления") {
            ToggleRow("Почта", "только ярлык «Важные»", settings.mailEnabled) { v -> onChange { it.copy(mailEnabled = v) } }
            Divider()
            ToggleRow("Slack", "упоминания и личные сообщения", settings.slackEnabled) { v -> onChange { it.copy(slackEnabled = v) } }
            Divider()
            ToggleRow("Мигание при новых", "счётчики и точки мигают, пока не прочитано", settings.blinkEnabled) { v -> onChange { it.copy(blinkEnabled = v) } }
            Divider()
            ToggleRow("Напоминать о событиях", "за ${settings.remindMinutes} минут", settings.remindMinutes > 0) { v -> onChange { it.copy(remindMinutes = if (v) 10 else 0) } }
            Divider()
            ToggleRow("Тихие часы", "${settings.quietFrom.format(hhmm)} - ${settings.quietTo.format(hhmm)}", settings.quietHoursEnabled) { v -> onChange { it.copy(quietHoursEnabled = v) } }
            Divider()
            SettingsRow {
                Text("Проверять каждые", fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 15, 30).forEach { minutes ->
                        val selected = settings.pollMinutes == minutes
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) c.accent else c.cardInset)
                                .clickable { onChange { it.copy(pollMinutes = minutes) } }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (minutes == 5) "5 мин" else minutes.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) c.onAccent else c.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun utcLabel(zone: ZoneClock, now: ZonedDateTime): String {
    val seconds = now.withZoneSameInstant(zone.zone).offset.totalSeconds
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val sign = if (seconds >= 0) "+" else "-"
    return if (minutes == 0) "UTC$sign${kotlin.math.abs(hours)}" else "UTC$sign${kotlin.math.abs(hours)}:${"%02d".format(kotlin.math.abs(minutes))}"
}

@Composable
private fun Group(title: String, hint: String? = null, content: @Composable () -> Unit) {
    val c = DayBoard.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
            if (hint != null) Text(hint, fontSize = 12.sp, color = c.textTertiary)
        }
        Card(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DayBoard.colors.cardInset))
}

@Composable
private fun AddRow(label: String) {
    val c = DayBoard.colors
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = c.accentText, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.accentText)
    }
}

@Composable
private fun AccountRow(name: String, detail: String, status: String) {
    val c = DayBoard.colors
    SettingsRow {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 15.sp, color = c.text)
            Text(detail, fontSize = 12.sp, color = c.textSecondary)
        }
        Text(status, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (status == "подключён") c.success else c.textSecondary)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val c = DayBoard.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, color = c.text)
            Text(subtitle, fontSize = 12.sp, color = c.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.switchThumbOn,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = c.switchOff,
                uncheckedThumbColor = c.switchThumb,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
