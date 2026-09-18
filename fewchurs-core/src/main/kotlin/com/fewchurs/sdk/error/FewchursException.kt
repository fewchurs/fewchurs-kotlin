package com.fewchurs.sdk.error

/**
 * Every failure the SDK reports. [userMessage] is safe to show to end users (English); the UI
 * module shows localized strings instead.
 */
public sealed class FewchursException(
    message: String,
    public val userMessage: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** [com.fewchurs.sdk.Fewchurs.configure] was not called. Developer error. */
    public class NotConfigured : FewchursException(
        "Fewchurs is not configured. Call Fewchurs.configure(context, apiKey) in Application.onCreate " +
            "or add the com.fewchurs.sdk.API_KEY meta-data to your manifest.",
        GENERIC,
    )

    /** The API key is wrong or was revoked. Developer error. */
    public class InvalidApiKey : FewchursException("Invalid or inactive Fewchurs API key.", GENERIC)

    /** Input was rejected. [message] comes from the server or client-side validation. */
    public class Validation(message: String) : FewchursException(message, message)

    /** Daily per-device limit reached (15 requests, 10 comments). */
    public class RateLimited : FewchursException(
        "Rate limited.", "You've reached today's limit. Try again tomorrow.",
    )

    /** The board's plan limit was reached. */
    public class LimitReached(message: String = "Limit reached.") : FewchursException(
        message, "This board isn't accepting requests right now.",
    )

    public class NotFound : FewchursException("Not found.", "This request no longer exists.")

    public class Server(public val statusCode: Int) : FewchursException(
        "Server error ($statusCode).", GENERIC,
    )

    /** No response: offline, DNS, timeout. */
    public class Network(cause: Throwable?) : FewchursException(
        "Network error: ${cause?.message}", "You're offline.", cause,
    )

    /** An SDK bug (e.g. missing device ID). */
    public class Internal(message: String) : FewchursException(message, GENERIC)

    /** Unexpected response (bad JSON, unknown error). */
    public class Unknown(message: String, cause: Throwable? = null) : FewchursException(message, GENERIC, cause)

    private companion object {
        const val GENERIC = "Something went wrong. Try again."
    }
}
