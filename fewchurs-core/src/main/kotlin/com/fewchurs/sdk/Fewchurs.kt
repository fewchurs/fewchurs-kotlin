package com.fewchurs.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fewchurs.sdk.data.FewchursRepository
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.internal.Logger
import com.fewchurs.sdk.internal.identity.DeviceIdentity
import com.fewchurs.sdk.internal.network.FewchursApi
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.model.FewchursEvent
import com.fewchurs.sdk.model.Sort
import com.fewchurs.sdk.state.BoardState
import com.fewchurs.sdk.state.FeatureDetailState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Entry point of the Fewchurs SDK.
 *
 * ```
 * Fewchurs.configure(this, apiKey = "fr_live_xxx") {
 *     isSubscriber = { billing.hasActiveSubscription() }
 *     userEmail = { auth.currentUser?.email }
 * }
 * ```
 */
public object Fewchurs {

    private class Runtime(
        val config: FewchursConfig,
        val repository: FewchursRepository,
        val appContext: Context,
    )

    @Volatile private var runtime: Runtime? = null
    private var warnedNotConfigured = false

    private val _events = MutableSharedFlow<FewchursEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** SDK events for your analytics. Safe to collect before [configure]. */
    public val events: SharedFlow<FewchursEvent> = _events.asSharedFlow()

    public val isConfigured: Boolean get() = runtime != null

    /** The active configuration, or null before [configure]. */
    public val config: FewchursConfig? get() = runtime?.config

    /** Low-level API access for custom UIs. Calls throw [FewchursException.NotConfigured] before [configure]. */
    public val client: FewchursClient = FewchursClient { requireRuntime().repository }

    /**
     * Configures the SDK. Call once from `Application.onCreate`. Calling it again replaces the
     * configuration. Does no network or disk I/O.
     */
    @JvmStatic
    @JvmOverloads
    public fun configure(context: Context, apiKey: String, block: FewchursConfig.Builder.() -> Unit = {}) {
        val config = FewchursConfig.Builder(apiKey).apply(block).build()
        val logger = Logger(config.logLevel)
        if (!config.apiKey.startsWith("fr_")) {
            logger.error("The Fewchurs API key should start with \"fr_\". Copy it from your dashboard.")
        }
        val appContext = context.applicationContext ?: context
        val identity = DeviceIdentity(appContext, config.deviceIdProvider)
        val api = FewchursApi(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            deviceId = identity::current,
            userAgent = userAgent(appContext),
            acceptLanguage = { Locale.getDefault().toLanguageTag() },
            logger = logger,
        )
        val repository = FewchursRepository(
            api = api,
            deviceId = identity::current,
            emit = ::emit,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
        runtime = Runtime(config, repository, appContext)
        logger.debug("Configured for ${config.baseUrl}")
    }

    /**
     * Submits a feature request from your own UI.
     *
     * @param isSubscriber null uses [FewchursConfig.Builder.isSubscriber].
     * @param email if set, the user follows the request and is emailed when it ships.
     * @throws FewchursException on validation, network or server errors.
     */
    public suspend fun submitFeature(
        title: String,
        description: String = "",
        email: String? = null,
        isSubscriber: Boolean? = null,
    ): Feature {
        val rt = requireRuntime()
        val subscriber = isSubscriber ?: withContext(Dispatchers.Default) { rt.config.currentIsSubscriber() }
        return rt.repository.submitFeature(title, description, email, subscriber)
    }

    /**
     * A headless, paginated feature list. Pass a [scope] (e.g. `viewModelScope`) to tie it to a
     * lifecycle, or call [BoardState.close] when done.
     */
    @JvmOverloads
    public fun boardState(
        sort: Sort = Sort.Top,
        scope: CoroutineScope? = null,
        pageSize: Int = 20,
        hideDeclined: Boolean = false,
    ): BoardState {
        val filter: (Feature) -> Boolean = if (hideDeclined) {
            { it.status != FeatureStatus.DECLINED }
        } else {
            { true }
        }
        return BoardState(requireRuntime().repository, sort, scope ?: ownedScope(), pageSize, filter, ownsScope = scope == null)
    }

    /** Headless state for one feature's detail screen. See [boardState] for [scope]. */
    @JvmOverloads
    public fun featureDetailState(featureId: String, scope: CoroutineScope? = null, pageSize: Int = 20): FeatureDetailState =
        FeatureDetailState(requireRuntime().repository, featureId, scope ?: ownedScope(), pageSize, ownsScope = scope == null)

    /**
     * Opens the board in its own Activity. Works from any Context; needs the
     * `fewchurs-android` artifact.
     */
    @JvmStatic
    public fun showBoard(context: Context) {
        val intent = Intent().setComponent(ComponentName(context.packageName, BOARD_ACTIVITY))
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Logger(LogLevel.Error).error("Fewchurs.showBoard needs the fewchurs-android dependency.", e)
        }
    }

    // --- shared with fewchurs-android ---

    @FewchursInternalApi
    public val repository: FewchursRepository get() = requireRuntime().repository

    @FewchursInternalApi
    public fun emit(event: FewchursEvent) {
        _events.tryEmit(event)
    }

    /** Clears configuration. For tests. */
    @FewchursInternalApi
    public fun reset() {
        runtime = null
    }

    private fun requireRuntime(): Runtime = runtime ?: run {
        if (!warnedNotConfigured) {
            warnedNotConfigured = true
            Logger(LogLevel.Error).error("Fewchurs used before configure(). ")
        }
        throw FewchursException.NotConfigured()
    }

    private fun ownedScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun userAgent(context: Context): String =
        "Fewchurs-Android/${com.fewchurs.sdk.core.BuildConfig.VERSION_NAME} " +
            "(Android ${Build.VERSION.RELEASE}; ${context.packageName})"

    private const val BOARD_ACTIVITY = "com.fewchurs.sdk.ui.FewchursActivity"
}
