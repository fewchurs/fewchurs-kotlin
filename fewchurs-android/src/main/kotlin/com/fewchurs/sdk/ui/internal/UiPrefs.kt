package com.fewchurs.sdk.ui.internal

import android.content.Context

/** Small per-device UI memory: the commenter's display name and (with consent) their email. */
internal class UiPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fewchurs_sdk_ui", Context.MODE_PRIVATE)

    var authorName: String?
        get() = prefs.getString("author_name", null)
        set(value) = prefs.edit().putString("author_name", value?.trim()?.takeIf { it.isNotEmpty() }).apply()

    var rememberedEmail: String?
        get() = prefs.getString("email", null)
        set(value) = prefs.edit().putString("email", value?.trim()?.takeIf { it.isNotEmpty() }).apply()
}
