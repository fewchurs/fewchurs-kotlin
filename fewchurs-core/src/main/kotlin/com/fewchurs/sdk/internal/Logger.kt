package com.fewchurs.sdk.internal

import android.util.Log
import com.fewchurs.sdk.LogLevel

internal class Logger(private val level: LogLevel) {
    fun debug(message: String) {
        if (level == LogLevel.Debug) Log.d(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (level != LogLevel.None) Log.e(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "Fewchurs"
    }
}
