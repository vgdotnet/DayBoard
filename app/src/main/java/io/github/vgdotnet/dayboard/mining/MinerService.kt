package io.github.vgdotnet.dayboard.mining

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.vgdotnet.dayboard.MainActivity
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.model.MinerSettings

/**
 * Foreground service that owns the engine and the Stratum client, so mining goes on with the
 * screen off. Holds a partial wake lock; pauses when the setting says "only while charging" and
 * the charger is unplugged.
 */
class MinerService : Service() {
    private var engine: MinerEngine? = null
    private var stratum: StratumClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastHashes = 0L
    private val window = ArrayDeque<Long>()
    private var settings = MinerSettings()
    private var charging = true
    private var ticks = 0

    private val tick = object : Runnable {
        override fun run() {
            val e = engine ?: return
            val total = e.hashes.get()
            window.addLast(total - lastHashes)
            lastHashes = total
            while (window.size > 10) window.removeFirst()
            val rate = window.sum().toDouble() / window.size
            MinerRuntime.update { it.copy(hashrate = rate, hashes = total) }
            if (++ticks % 5 == 0) updateNotification()
            handler.postDelayed(this, 1_000)
        }
    }

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> charging = true
                Intent.ACTION_POWER_DISCONNECTED -> charging = false
                else -> readBattery(intent)
            }
            Log.d(TAG, "charging=$charging (${intent.action})")
            applyPause()
        }
    }

    private fun readBattery(intent: Intent) {
        // "Charging" here means a charger is attached, whatever the battery status says
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        settings = MinerController.settings
        startAsForeground()
        if (engine == null) startMining()
        return START_STICKY
    }

    private fun startMining() {
        val ok = runCatching { PoW.selfTest() }.getOrDefault(false)
        // The native kernel is used only when it passes its own self-test; otherwise the Kotlin one
        val native = runCatching { PoW.nativeSelfTest() }.getOrDefault(false)
        if (NativeKernel.isAvailable && !native) Log.w(TAG, "native kernel failed the self-test, using Kotlin")
        val threads = settings.threads.coerceIn(1, Runtime.getRuntime().availableProcessors())
        MinerRuntime.update {
            it.copy(running = true, selfTestOk = ok, threads = threads, startedAt = System.currentTimeMillis(), kernel = if (native) "ARMv8 SHA2" else "Kotlin", status = if (ok) "старт" else "самопроверка не прошла")
        }
        if (!ok) {
            Log.e(TAG, "self-test failed, not mining")
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DayBoard:miner").also { it.acquire() }
        val e = MinerEngine(threads, native)
        engine = e
        e.start()
        stratum = StratumClient(settings, e, native).also { it.start() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // The sticky battery intent comes back from registerReceiver: apply it now, the receiver may not be called for it
        val sticky = ContextCompat.registerReceiver(this, battery, filter, ContextCompat.RECEIVER_EXPORTED)
        if (sticky != null) readBattery(sticky)
        Log.d(TAG, "charging at start=$charging")
        applyPause()
        handler.post(tick)
    }

    private fun applyPause() {
        val e = engine ?: return
        val pause = settings.onlyWhenCharging && !charging
        if (e.paused != pause) {
            e.paused = pause
            MinerRuntime.update { it.copy(paused = pause) }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(battery) }
        stratum?.stop()
        engine?.stop()
        stratum = null
        engine = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        MinerRuntime.reset()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Майнер", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Состояние майнера"
                setShowBadge(false)
            })
        }
    }

    private fun buildNotification(): Notification {
        val s = MinerRuntime.state.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MinerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (s.paused) "ждёт зарядку" else "${formatHashrate(s.hashrate)} · долей ${s.accepted}/${s.accepted + s.rejected} · ${s.status}"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Майнер DayBoard")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Остановить", stop)
            .build()
    }

    private fun startAsForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
    }

    companion object {
        const val CHANNEL = "miner"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "io.github.vgdotnet.dayboard.miner.STOP"
        private const val TAG = "MinerService"
    }
}

/** Starts and stops the service; the settings for the next start are kept here. */
object MinerController {
    @Volatile var settings = MinerSettings()

    fun start(context: Context, s: MinerSettings) {
        settings = s
        ContextCompat.startForegroundService(context, Intent(context, MinerService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MinerService::class.java))
    }

    /** Applies new settings to a running miner by restarting it. */
    fun restart(context: Context, s: MinerSettings) {
        stop(context)
        start(context, s)
    }
}
