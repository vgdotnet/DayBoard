package io.github.vgdotnet.dayboard.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * The unread feed: exactly the Gmail and Slack notifications currently in the shade. A removed
 * notification (the mail was read, deleted or swiped away) drops its row; on every reconnect the
 * feed is reconciled with the active notifications. Shared between the listener service and the
 * UI; items are cached in a JSON file for a quick start, the content intents only in memory
 * (opening falls back to launching the app when the intent is gone).
 */
object NotificationInbox {
    private const val TAG = "NotificationInbox"
    private const val MAX_ITEMS = 50

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items

    private val intents = HashMap<String, PendingIntent>()
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "inbox.json")
        _items.value = load()
    }

    /** Whether the user granted notification access to this app. */
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Synchronized
    fun upsert(item: InboxItem, intent: PendingIntent?) {
        if (intent != null) intents[item.id] = intent
        _items.update { list ->
            (listOf(item) + list.filterNot { it.id == item.id }).sortedByDescending { it.time }.take(MAX_ITEMS)
        }
        save()
    }

    /** The notification is gone from the shade: the row goes too. */
    @Synchronized
    fun remove(id: String) {
        if (_items.value.none { it.id == id }) return
        intents.remove(id)
        _items.update { list -> list.filterNot { it.id == id } }
        save()
    }

    /** Keeps only the rows whose notifications are still active. */
    @Synchronized
    fun retainOnly(activeIds: Set<String>) {
        if (_items.value.all { it.id in activeIds }) return
        intents.keys.retainAll(activeIds)
        _items.update { list -> list.filter { it.id in activeIds } }
        save()
    }

    /** Dismisses the Gmail and Slack notifications; the listener's removal callbacks then empty the feed. */
    fun markAllRead() {
        val keys = _items.value.map { it.id }
        val service = NotificationCatcher.instance
        if (service != null && keys.isNotEmpty()) {
            runCatching { service.cancelNotifications(keys.toTypedArray()) }
        } else {
            retainOnly(emptySet())
        }
    }

    /**
     * Opens the message in its own app: the notification's intent, or the app itself.
     * From Android 14 the sender must allow the intent's creator to start an activity,
     * otherwise the launch is dropped without an error.
     */
    fun open(context: Context, item: InboxItem) {
        val intent = intents[item.id]
        val sent = intent?.let {
            runCatching {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                it.send(context, 0, null, null, null, null, options.toBundle())
            }.onFailure { e -> Log.w(TAG, "content intent failed for ${item.id}", e) }.isSuccess
        } ?: false
        Log.d(TAG, "open ${item.id}: intent=${intent != null} sent=$sent")
        if (!sent) {
            // No notification intent (IMAP mail): the message link, which Gmail handles, else the app itself.
            val link = item.openUrl.takeIf { it.startsWith("http") && it != item.source.openUrl }
            val launch = link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(item.source.packageName) }
                ?: context.packageManager.getLaunchIntentForPackage(item.source.packageName)
            if (launch != null) {
                runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure {
                        val plain = link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
                        if (plain != null) runCatching { context.startActivity(plain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
            }
        }
    }

    private fun save() {
        val f = file ?: return
        val array = JSONArray()
        _items.value.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("source", item.source.name)
                    .put("sender", item.sender)
                    .put("preview", item.preview)
                    .put("label", item.label)
                    .put("time", item.time.toInstant().toEpochMilli())
                    .put("unread", item.unread)
                    .put("mention", item.isMention)
                    .put("account", item.account),
            )
        }
        runCatching { f.writeText(array.toString()) }
    }

    private fun load(): List<InboxItem> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                InboxItem(
                    id = o.getString("id"),
                    source = InboxSource.valueOf(o.getString("source")),
                    sender = o.getString("sender"),
                    preview = o.getString("preview"),
                    label = o.getString("label"),
                    time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(o.getLong("time")), ZoneId.systemDefault()),
                    unread = o.getBoolean("unread"),
                    isMention = o.optBoolean("mention"),
                    account = o.optString("account"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
