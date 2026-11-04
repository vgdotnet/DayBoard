package io.github.vgdotnet.dayboard.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Listens to Gmail and Slack notifications and feeds [NotificationInbox].
 * A removed notification (the message was read or dismissed in its app) marks the item read.
 */
class NotificationCatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        NotificationInbox.init(this)
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        active.forEach { handle(it) }
        NotificationInbox.retainOnly(active.filter { sourceOf(it.packageName) != null }.map { it.key }.toSet())
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationInbox.init(this)
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationInbox.init(this)
        if (sourceOf(sbn.packageName) != null) NotificationInbox.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification) {
        val item = parse(sbn) ?: return
        NotificationInbox.upsert(item, sbn.notification.contentIntent)
    }

    companion object {
        /** The bound service, for cancelling notifications from the UI; null while not connected. */
        @Volatile
        var instance: NotificationCatcher? = null
            private set

        private const val PREVIEW_MAX = 160

        fun sourceOf(packageName: String): InboxSource? = InboxSource.entries.firstOrNull { it.packageName == packageName }

        /** Builds an inbox item from a notification, or null when it is not a message. */
        fun parse(sbn: StatusBarNotification): InboxItem? {
            val source = sourceOf(sbn.packageName) ?: return null
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            // EXTRA_TEXT is the short line (subject and a snippet); EXTRA_BIG_TEXT may hold the whole mail.
            val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
                ?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()

            // Messaging-style notifications (Slack) carry the messages themselves.
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
            val last = messages?.lastOrNull()

            val sender: String
            val preview: String
            val label: String
            var mention = false
            var account = ""
            when (source) {
                InboxSource.GMAIL -> {
                    if (title.isEmpty()) return null
                    sender = title
                    preview = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "(без темы)" }
                    label = if (subText.isNotEmpty()) "Gmail · ${subText.substringBefore('@')}" else "Gmail"
                    account = subText.takeIf { it.contains('@') }.orEmpty()
                }
                InboxSource.SLACK -> {
                    val channel = conversation ?: title
                    val who = last?.senderPerson?.name?.toString() ?: last?.sender?.toString()
                    val body = last?.text?.toString() ?: text
                    if (channel.isEmpty() && body.isEmpty()) return null
                    sender = if (who != null && channel.isNotEmpty() && who != channel) "$channel · $who" else channel.ifEmpty { who.orEmpty() }
                    preview = body.ifEmpty { "(сообщение)" }
                    mention = body.contains('@') && channel.startsWith("#")
                    label = if (mention) "упоминание" else if (channel.startsWith("#")) "Slack · канал" else "Slack · личное"
                }
            }
            return InboxItem(
                id = sbn.key,
                source = source,
                sender = sender,
                preview = preview.replace('\n', ' ').take(PREVIEW_MAX),
                label = label,
                time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), ZoneId.systemDefault()),
                unread = true,
                isMention = mention,
                account = account,
            )
        }
    }
}
