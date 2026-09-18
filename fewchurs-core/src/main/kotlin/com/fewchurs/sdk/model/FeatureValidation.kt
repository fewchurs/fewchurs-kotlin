package com.fewchurs.sdk.model

/** Client-side rules that mirror the server's validation exactly. */
public object FeatureValidation {
    public const val TITLE_MIN: Int = 3
    public const val TITLE_MAX: Int = 100
    public const val DESCRIPTION_MAX: Int = 1000
    public const val COMMENT_MAX: Int = 1000
    public const val AUTHOR_NAME_MAX: Int = 60

    private val EMAIL = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    public fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

    public fun isValidTitle(title: String): Boolean = title.trim().length in TITLE_MIN..TITLE_MAX

    public fun isValidDescription(description: String): Boolean = description.trim().length <= DESCRIPTION_MAX

    public fun isValidComment(text: String): Boolean = text.trim().length in 1..COMMENT_MAX

    public fun isValidAuthorName(name: String): Boolean = name.trim().length <= AUTHOR_NAME_MAX
}
