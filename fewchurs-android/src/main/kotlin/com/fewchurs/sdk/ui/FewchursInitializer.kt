package com.fewchurs.sdk.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.startup.Initializer
import com.fewchurs.sdk.Fewchurs

/**
 * Zero-code setup through AndroidX App Startup. Reads, from the app's `<application>`:
 * - `com.fewchurs.sdk.API_KEY` (required)
 * - `com.fewchurs.sdk.BASE_URL` (optional)
 *
 * Only stores configuration — no network or disk work at startup. A later
 * [Fewchurs.configure] call in code replaces it.
 */
public class FewchursInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (Fewchurs.isConfigured) return
        val meta = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }.metaData
        }.getOrNull() ?: return
        val apiKey = meta.getString(API_KEY)?.takeIf { it.isNotBlank() } ?: return
        val baseUrl = meta.getString(BASE_URL)
        Fewchurs.configure(context, apiKey) {
            if (!baseUrl.isNullOrBlank()) this.baseUrl = baseUrl
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        const val API_KEY = "com.fewchurs.sdk.API_KEY"
        const val BASE_URL = "com.fewchurs.sdk.BASE_URL"
    }
}
