package com.fewchurs.sdk

/** How much the SDK writes to Logcat (tag `Fewchurs`). The API key is never logged. */
public enum class LogLevel { None, Error, Debug }

/** Resolved SDK configuration. Create it through [Fewchurs.configure]. */
public class FewchursConfig internal constructor(
    public val apiKey: String,
    public val baseUrl: String,
    public val logLevel: LogLevel,
    internal val isSubscriberProvider: (() -> Boolean)?,
    internal val userEmailProvider: (() -> String?)?,
    internal val deviceIdProvider: (() -> String?)?,
    extensions: Map<String, Any>,
) {
    /** Settings contributed by other Fewchurs modules (theme, board, strings). */
    @FewchursInternalApi
    public val extensions: Map<String, Any> = extensions.toMap()

    /** The email to prefill for follow/submit, from [Builder.userEmail]. */
    public fun currentUserEmail(): String? = runCatching { userEmailProvider?.invoke() }
        .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** Whether the current user pays for the host app, from [Builder.isSubscriber]. */
    public fun currentIsSubscriber(): Boolean = runCatching { isSubscriberProvider?.invoke() }
        .getOrNull() ?: false

    public class Builder internal constructor(private val apiKey: String) {
        /** API base URL. Override for staging or a local server. */
        public var baseUrl: String = DEFAULT_BASE_URL

        public var logLevel: LogLevel = LogLevel.Error

        /** Tells the dashboard which requests come from paying users. */
        public var isSubscriber: (() -> Boolean)? = null

        /** Prefills the email when the user follows or submits a request. */
        public var userEmail: (() -> String?)? = null

        /**
         * Identifies the user across devices. Return a **hash** of your user ID, never an email
         * or raw ID. Returning null falls back to the SDK's random per-install ID.
         */
        public var deviceId: (() -> String?)? = null

        @FewchursInternalApi
        public val extensions: MutableMap<String, Any> = mutableMapOf()

        internal fun build(): FewchursConfig = FewchursConfig(
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trimEnd('/'),
            logLevel = logLevel,
            isSubscriberProvider = isSubscriber,
            userEmailProvider = userEmail,
            deviceIdProvider = deviceId,
            extensions = extensions,
        )
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://www.fewchurs.shop/api/v1"
    }
}
