package io.github.vgdotnet.dayboard.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Unread counts from the Gmail app's label provider (the one launchers use for badges):
 * content://com.google.android.gm/<account>/labels, permission
 * com.google.android.gm.permission.READ_CONTENT_PROVIDER. Accounts are learned from
 * Gmail notifications, the provider does not list them.
 */
class GmailCounts(context: Context) {
    private val context = context.applicationContext

    /** The provider's permission is a runtime one defined by Gmail; without it every query is refused. */
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Unread conversations in the inbox (or the Important label) summed over [accounts]; null when nothing could be read. */
    fun unread(accounts: Collection<String>, importantOnly: Boolean): Int? {
        if (accounts.isEmpty() || !hasPermission) return null
        val wanted = if (importantOnly) LABEL_IMPORTANT else LABEL_INBOX
        var total = 0
        var any = false
        for (account in accounts) {
            val count = unreadFor(account, wanted) ?: continue
            any = true
            total += count
        }
        return if (any) total else null
    }

    private fun unreadFor(account: String, canonical: String): Int? {
        val uri = Uri.parse("content://$AUTHORITY/$account/labels")
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(COL_CANONICAL, COL_UNREAD), null, null, null)
        }.onFailure { Log.w(TAG, "labels of $account", it) }.getOrNull() ?: return null
        cursor.use { c ->
            val canonicalIdx = c.getColumnIndex(COL_CANONICAL)
            val unreadIdx = c.getColumnIndex(COL_UNREAD)
            if (canonicalIdx < 0 || unreadIdx < 0) return null
            while (c.moveToNext()) {
                if (c.getString(canonicalIdx) == canonical) {
                    val n = c.getInt(unreadIdx)
                    Log.d(TAG, "$account $canonical unread=$n")
                    return n
                }
            }
        }
        return null
    }

    /** Calls [onChange] when Gmail reports a change in its provider; returns the unregister action, or null if refused. */
    fun observe(onChange: () -> Unit): (() -> Unit)? {
        if (!hasPermission) return null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        return runCatching {
            context.contentResolver.registerContentObserver(Uri.parse("content://$AUTHORITY/"), true, observer)
            val stop: () -> Unit = { context.contentResolver.unregisterContentObserver(observer) }
            stop
        }.onFailure { Log.w(TAG, "observe", it) }.getOrNull()
    }

    companion object {
        const val PERMISSION = "com.google.android.gm.permission.READ_CONTENT_PROVIDER"
        private const val TAG = "GmailCounts"
        const val AUTHORITY = "com.google.android.gm"
        const val COL_CANONICAL = "canonicalName"
        const val COL_UNREAD = "numUnreadConversations"
        const val LABEL_INBOX = "^i"
        const val LABEL_IMPORTANT = "^iim"
    }
}
