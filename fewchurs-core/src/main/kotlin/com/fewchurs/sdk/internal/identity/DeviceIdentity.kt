package com.fewchurs.sdk.internal.identity

import android.content.Context
import java.util.UUID

/**
 * Anonymous identity used for votes, follows and rate limits: a random UUID kept in private
 * SharedPreferences. Deliberately not ANDROID_ID or the advertising ID.
 */
internal class DeviceIdentity(context: Context, private val override: (() -> String?)?) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var generated: String? = null

    fun current(): String {
        val custom = runCatching { override?.invoke() }.getOrNull()?.trim()
        if (!custom.isNullOrEmpty()) return custom.take(MAX_LENGTH)
        return generated ?: synchronized(this) {
            generated ?: (prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY, it).apply()
            }).also { generated = it }
        }
    }

    internal companion object {
        const val PREFS = "fewchurs_sdk"
        private const val KEY = "device_id"
        private const val MAX_LENGTH = 128
    }
}
