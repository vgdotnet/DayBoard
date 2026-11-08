package io.github.vgdotnet.dayboard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * App passwords for IMAP, one per Gmail account, in EncryptedSharedPreferences
 * (falls back to plain app-private preferences if the keystore refuses).
 */
class MailCredentials(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "mail_credentials",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure { Log.w(TAG, "encrypted prefs unavailable, using plain", it) }
        .getOrElse { context.applicationContext.getSharedPreferences("mail_credentials_plain", Context.MODE_PRIVATE) }

    fun password(account: String): String? = prefs.getString(account, null)?.takeIf { it.isNotEmpty() }

    fun hasPassword(account: String): Boolean = password(account) != null

    fun set(account: String, password: String) {
        prefs.edit().putString(account, password.replace(" ", "")).apply()
    }

    fun clear(account: String) {
        prefs.edit().remove(account).apply()
    }

    private companion object {
        const val TAG = "MailCredentials"
    }
}
