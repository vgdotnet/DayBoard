package io.github.vgdotnet.dayboard.mining

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the miner screen shows; written by the engine, the Stratum client and the service. */
data class MinerStatus(
    val running: Boolean = false,
    /** Short status: "pool...", "mining", "auth failed", "ждёт зарядку"... */
    val status: String = "выключен",
    val poolConnected: Boolean = false,
    /** Hashes per second over the last ten seconds. */
    val hashrate: Double = 0.0,
    val hashes: Long = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val blocks: Int = 0,
    val best: Double = 0.0,
    val height: Long = 0,
    val netDifficulty: Double = 0.0,
    val shareDifficulty: Double = 0.0,
    val versionMask: Int = 0,
    val threads: Int = 0,
    val startedAt: Long = 0,
    val lastShareAt: Long = 0,
    val selfTestOk: Boolean? = null,
    val paused: Boolean = false,
)

/** Process-wide miner state so the UI reads it without binding the service. */
object MinerRuntime {
    private val _state = MutableStateFlow(MinerStatus())
    val state: StateFlow<MinerStatus> = _state

    fun update(transform: (MinerStatus) -> MinerStatus) = _state.update(transform)

    fun status(text: String) = update { it.copy(status = text) }

    fun reset() {
        _state.value = MinerStatus()
    }
}

fun formatHashrate(h: Double): String = when {
    h >= 1e9 -> String.format("%.2f GH/s", h / 1e9)
    h >= 1e6 -> String.format("%.2f MH/s", h / 1e6)
    h >= 1e3 -> String.format("%.1f kH/s", h / 1e3)
    else -> String.format("%.0f H/s", h)
}

fun formatDifficulty(d: Double): String = when {
    d <= 0 -> "-"
    d >= 1e12 -> String.format("%.2f T", d / 1e12)
    d >= 1e9 -> String.format("%.2f G", d / 1e9)
    d >= 1e6 -> String.format("%.2f M", d / 1e6)
    d >= 1e3 -> String.format("%.1f k", d / 1e3)
    d >= 10 -> String.format("%.1f", d)
    else -> String.format("%.4f", d)
}
