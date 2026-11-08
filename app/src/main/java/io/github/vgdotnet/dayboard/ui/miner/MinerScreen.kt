package io.github.vgdotnet.dayboard.ui.miner

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.mining.MinerStatus
import io.github.vgdotnet.dayboard.mining.formatDifficulty
import io.github.vgdotnet.dayboard.mining.formatHashrate
import io.github.vgdotnet.dayboard.model.MinerSettings
import io.github.vgdotnet.dayboard.ui.components.BlinkDot
import io.github.vgdotnet.dayboard.ui.components.Card
import io.github.vgdotnet.dayboard.ui.components.TextDialog
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

/** Miner screen in the style of Settings: state, pool, performance. */
@Composable
fun MinerScreen(
    status: MinerStatus,
    settings: MinerSettings,
    now: ZonedDateTime,
    onToggle: (Boolean) -> Unit,
    onChange: ((MinerSettings) -> MinerSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DayBoard.colors
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    editing?.let { field ->
        val (title, placeholder, initial, numeric) = when (field) {
            "pool" -> listOf("Пул", "host:port", "${settings.host}:${settings.port}", false)
            "wallet" -> listOf("Адрес выплат", "bc1q…", settings.wallet, false)
            "worker" -> listOf("Имя работника", "tab", settings.worker, false)
            "password" -> listOf("Пароль пула", "x", settings.password, false)
            else -> listOf("Порог доли", "0.005", settings.suggestDifficulty.toString(), true)
        }
        TextDialog(
            title as String, placeholder as String,
            confirmLabel = "Сохранить", initial = initial as String, numeric = numeric as Boolean,
            onConfirm = { value ->
                val v = value.trim()
                when (field) {
                    "pool" -> {
                        val host = v.substringBefore(':').trim()
                        val port = v.substringAfter(':', "").trim().toIntOrNull()
                        if (host.isNotEmpty()) onChange { it.copy(host = host, port = port ?: it.port) }
                    }
                    "wallet" -> if (v.isNotEmpty()) onChange { it.copy(wallet = v) }
                    "worker" -> if (v.isNotEmpty()) onChange { it.copy(worker = v) }
                    "password" -> onChange { it.copy(password = v.ifEmpty { "x" }) }
                    else -> v.toDoubleOrNull()?.let { d -> if (d > 0) onChange { it.copy(suggestDifficulty = d) } }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Майнер", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = c.text)

        Group("Состояние") {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(!settings.enabled) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Майнить", fontSize = 15.sp, color = c.text)
                        if (status.running && status.poolConnected && !status.paused) BlinkDot(size = 8.dp, active = true, low = 0.4f)
                    }
                    Text(
                        if (!status.running) "соло на ${settings.host}, выплата целиком на ваш адрес" else statusLine(status),
                        fontSize = 12.sp,
                        color = if (status.selfTestOk == false) c.eventNowLine else c.textSecondary,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = c.switchThumbOn, checkedBorderColor = Color.Transparent,
                        uncheckedTrackColor = c.switchOff, uncheckedThumbColor = c.switchThumb, uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            Divider()
            val threads = status.threads.takeIf { it > 0 } ?: settings.threads
            BigValue("Скорость", if (status.running) formatHashrate(status.hashrate) else "-", "$threads ${plural(threads, "поток", "потока", "потоков")}${if (status.kernel.isNotEmpty()) " · ядро ${status.kernel}" else ""}")
            Divider()
            ValueRow("Доли", "${status.accepted} принято${if (status.rejected > 0) " · ${status.rejected} отклонено" else ""}", if (status.lastShareAt > 0) "последняя ${ago(status.lastShareAt, now)}" else "порог ${formatDifficulty(status.shareDifficulty)}")
            Divider()
            ValueRow("Лучшая доля", formatDifficulty(status.best), if (status.netDifficulty > 0) "сеть ${formatDifficulty(status.netDifficulty)}" else "")
            Divider()
            ValueRow("Блок", if (status.height > 0) "#${status.height}" else "-", if (status.blocks > 0) "найдено блоков: ${status.blocks}" else "version rolling: ${if (status.versionMask != 0) "8 версий" else "нет"}")
            Divider()
            ValueRow("Работает", if (status.startedAt > 0) uptime(status.startedAt, now) else "-", if (status.hashes > 0) "всего ${formatHashes(status.hashes)}" else "")
        }

        Group("Пул") {
            EditRow("Пул", "${settings.host}:${settings.port}") { editing = "pool" }
            Divider()
            EditRow("Адрес выплат", settings.wallet) { editing = "wallet" }
            Divider()
            EditRow("Работник", settings.worker) { editing = "worker" }
            Divider()
            EditRow("Пароль", settings.password) { editing = "password" }
            Divider()
            EditRow("Порог доли", "${settings.suggestDifficulty} (пул может поднять)") { editing = "diff" }
        }

        Group("Производительность") {
            val cores = Runtime.getRuntime().availableProcessors()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Потоки", fontSize = 15.sp, color = c.text)
                    Text("ядер в устройстве: $cores", fontSize = 12.sp, color = c.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, cores / 2, cores).distinct().filter { it in 1..cores }.forEach { n ->
                        val selected = settings.threads == n
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) c.accent else c.cardInset)
                                .clickable { onChange { it.copy(threads = n) } }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(n.toString(), fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) c.onAccent else c.textSecondary)
                        }
                    }
                }
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().clickable { onChange { it.copy(onlyWhenCharging = !it.onlyWhenCharging) } }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Только на зарядке", fontSize = 15.sp, color = c.text)
                    Text("без зарядки перебор приостанавливается, соединение с пулом остаётся", fontSize = 12.sp, color = c.textSecondary)
                }
                Switch(
                    checked = settings.onlyWhenCharging,
                    onCheckedChange = { v -> onChange { it.copy(onlyWhenCharging = v) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent, checkedThumbColor = c.switchThumbOn, checkedBorderColor = Color.Transparent,
                        uncheckedTrackColor = c.switchOff, uncheckedThumbColor = c.switchThumb, uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

private fun statusLine(s: MinerStatus): String = when {
    s.selfTestOk == false -> "самопроверка ядра не прошла, перебор остановлен"
    s.paused -> "ждёт зарядку · пул ${if (s.poolConnected) "подключён" else "недоступен"}"
    else -> when (s.status) {
        "mining" -> "перебор идёт, пул подключён"
        "pool..." -> "подключение к пулу…"
        "subscribing" -> "подписка на задания…"
        "pool down" -> "пул недоступен, повтор…"
        "pool silent" -> "пул молчит, переподключение…"
        "no answer" -> "пул не ответил на долю, переподключение…"
        "auth failed" -> "пул не принял адрес или работника"
        else -> s.status
    }
}

private fun uptime(startedAt: Long, now: ZonedDateTime): String {
    val sec = (now.toInstant().toEpochMilli() - startedAt) / 1000
    val h = sec / 3600
    val m = sec % 3600 / 60
    return if (h > 0) "$h ч $m мин" else "$m мин ${sec % 60} с"
}

private fun ago(at: Long, now: ZonedDateTime): String {
    val sec = (now.toInstant().toEpochMilli() - at) / 1000
    return when {
        sec < 60 -> "$sec с назад"
        sec < 3600 -> "${sec / 60} мин назад"
        else -> "${sec / 3600} ч назад"
    }
}

private fun formatHashes(h: Long): String = when {
    h >= 1_000_000_000_000L -> String.format("%.2f TH", h / 1e12)
    h >= 1_000_000_000L -> String.format("%.2f GH", h / 1e9)
    h >= 1_000_000L -> String.format("%.1f MH", h / 1e6)
    else -> String.format("%.0f kH", h / 1e3)
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1 && n100 != 11 -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = DayBoard.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
        Card(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DayBoard.colors.cardInset))
}

@Composable
private fun BigValue(label: String, value: String, detail: String) {
    val c = DayBoard.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, color = c.text)
            Text(detail, fontSize = 12.sp, color = c.textSecondary)
        }
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = c.accentText)
    }
}

@Composable
private fun ValueRow(label: String, value: String, detail: String) {
    val c = DayBoard.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, color = c.text)
            if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = c.textSecondary)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    }
}

@Composable
private fun EditRow(label: String, value: String, onClick: () -> Unit) {
    val c = DayBoard.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 15.sp, color = c.text)
        Text(value, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
