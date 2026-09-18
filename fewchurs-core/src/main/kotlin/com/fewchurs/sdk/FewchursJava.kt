package com.fewchurs.sdk

import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.Feature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Callback-based wrappers for Java callers. Callbacks run on the main thread. */
public object FewchursJava {
    public interface Callback<T> {
        public fun onSuccess(result: T)
        public fun onError(error: FewchursException)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    @JvmOverloads
    public fun submitFeature(
        title: String,
        description: String,
        callback: Callback<Feature>,
        email: String? = null,
    ) {
        scope.launch {
            val result = try {
                Fewchurs.submitFeature(title, description, email)
            } catch (e: FewchursException) {
                callback.onError(e)
                return@launch
            }
            callback.onSuccess(result)
        }
    }
}
