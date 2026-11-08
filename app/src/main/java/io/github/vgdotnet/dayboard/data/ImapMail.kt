package io.github.vgdotnet.dayboard.data

import android.util.Log
import io.github.vgdotnet.dayboard.model.InboxItem
import io.github.vgdotnet.dayboard.model.InboxSource
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeUtility
import jakarta.mail.search.FlagTerm
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Properties

/**
 * Unread mail over Gmail IMAP with an app password. Read-only: the folder is opened READ_ONLY and
 * only envelopes are fetched, so nothing gets marked as read.
 */
class ImapMail {

    data class Result(val items: List<InboxItem>, val unread: Int)

    /** Newest [limit] unread messages of the inbox (or the Important folder) of [account]. */
    fun unread(account: String, password: String, importantOnly: Boolean, limit: Int = 30): Result {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", HOST)
            put("mail.imaps.port", "993")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(HOST, account, password)
        try {
            val folder = if (importantOnly) importantFolder(store) else store.getFolder(FOLDER_INBOX)
            folder.open(Folder.READ_ONLY)
            try {
                val messages = folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
                val newest = messages.takeLast(limit).reversed()
                folder.fetch(newest.toTypedArray(), jakarta.mail.FetchProfile().apply { add(jakarta.mail.FetchProfile.Item.ENVELOPE) })
                val items = newest.mapNotNull { m -> runCatching { toItem(m, account) }.onFailure { Log.w(TAG, "message", it) }.getOrNull() }
                return Result(items, messages.size)
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    /** Gmail localizes system folder names, so the Important label is found by its \\Important attribute. */
    private fun importantFolder(store: jakarta.mail.Store): Folder {
        val byAttribute = runCatching {
            store.defaultFolder.list("*").firstOrNull { f ->
                (f as? org.eclipse.angus.mail.imap.IMAPFolder)?.attributes?.any { it.equals("\\Important", ignoreCase = true) } == true
            }
        }.getOrNull()
        return byAttribute ?: store.getFolder(FOLDER_IMPORTANT)
    }

    private fun toItem(m: Message, account: String): InboxItem {
        val from = (m.from?.firstOrNull() as? InternetAddress)
        val sender = from?.personal?.takeIf { it.isNotBlank() }?.let { MimeUtility.decodeText(it) } ?: from?.address ?: "(без отправителя)"
        val subject = m.subject?.takeIf { it.isNotBlank() } ?: "(без темы)"
        val date = (m.receivedDate ?: m.sentDate)?.toInstant() ?: java.time.Instant.now()
        val messageId = (m.getHeader("Message-ID")?.firstOrNull() ?: "").trim().trim('<', '>')
        val link = if (messageId.isNotEmpty()) {
            "https://mail.google.com/mail/?authuser=${URLEncoder.encode(account, "UTF-8")}#search/rfc822msgid%3A${URLEncoder.encode(messageId, "UTF-8")}"
        } else {
            InboxSource.GMAIL.openUrl
        }
        return InboxItem(
            id = "imap:$account:${messageId.ifEmpty { "${m.messageNumber}@${date.toEpochMilli()}" }}",
            source = InboxSource.GMAIL,
            sender = sender,
            preview = subject,
            label = "Gmail · ${account.substringBefore('@')}",
            time = ZonedDateTime.ofInstant(date, ZoneId.systemDefault()),
            unread = true,
            openUrl = link,
            account = account,
        )
    }

    private companion object {
        const val TAG = "ImapMail"
        const val HOST = "imap.gmail.com"
        const val FOLDER_INBOX = "INBOX"
        const val FOLDER_IMPORTANT = "[Gmail]/Important"
    }
}
