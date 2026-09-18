package com.fewchurs.sample

import android.app.Application
import android.util.Log
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.LogLevel
import com.fewchurs.sdk.model.FewchursEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fewchurs.configure(this, apiKey = BuildConfig.FEWCHURS_API_KEY) {
            baseUrl = BuildConfig.FEWCHURS_BASE_URL
            logLevel = LogLevel.Debug
            isSubscriber = { false }
            userEmail = { null } // e.g. { auth.currentUser?.email }
        }

        // Analytics hook: the SDK sends nothing itself; forward what you care about.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            Fewchurs.events.collect { event ->
                Log.i("FewchursSample", "event: $event")
                eventLog.update { (listOf(event) + it).take(50) }
            }
        }
    }

    companion object {
        val eventLog = MutableStateFlow<List<FewchursEvent>>(emptyList())
    }
}
