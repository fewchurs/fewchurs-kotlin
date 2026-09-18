package com.fewchurs.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal fun <T> CoroutineScope.launchCollect(flow: Flow<T>, into: MutableList<T>) {
    launch { flow.collect { into += it } }
}

/**
 * Waits (in real time, off the test thread) until [condition] holds. MockWebServer answers on
 * real threads, so virtual-time helpers like advanceUntilIdle can't wait for it.
 */
internal suspend fun eventually(condition: () -> Boolean) {
    withContext(Dispatchers.Default) { withTimeout(5_000) { while (!condition()) delay(10) } }
}
