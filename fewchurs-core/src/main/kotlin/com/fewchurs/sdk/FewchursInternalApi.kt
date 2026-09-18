package com.fewchurs.sdk

/**
 * Marks declarations that are public only so the Fewchurs modules can share them.
 * They are not part of the stable API and may change in any release.
 */
@RequiresOptIn(
    message = "Internal Fewchurs API. It may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class FewchursInternalApi
