package com.fewchurs.sdk.ui.config

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.FewchursConfig
import com.fewchurs.sdk.model.FeatureStatus

public enum class FewchursDensity { Comfortable, Compact }

public enum class DarkMode {
    /** Use the host's MaterialTheme exactly as provided (default). */
    FollowHost,

    /** Light or dark from the system setting, using your primary color. */
    System,
    Light,
    Dark,
}

public enum class BoardTab {
    Top,
    New,

    /** Planned / In progress / Done. Hidden until the server supports status filters. */
    Roadmap,
}

public enum class EmailField { Hidden, Optional, Required }

public enum class SubmitButton { ExtendedFab, BottomBar, TopBarAction }

/** Color overrides. Anything left [Color.Unspecified] comes from the host's MaterialTheme. */
public class FewchursColorOverrides {
    public var primary: Color = Color.Unspecified
    public var onPrimary: Color = Color.Unspecified
    public var background: Color = Color.Unspecified
    public var surface: Color = Color.Unspecified
    public var onSurface: Color = Color.Unspecified
    public var muted: Color = Color.Unspecified
    public var border: Color = Color.Unspecified
    public var error: Color = Color.Unspecified
    public val statusColors: MutableMap<FeatureStatus, Color> = mutableMapOf()
}

public class FewchursThemeSpec {
    internal val colors = FewchursColorOverrides()
    internal var darkColors: FewchursColorOverrides? = null

    /** Corner radius for cards, buttons and fields. Null uses MaterialTheme.shapes. */
    public var cornerRadius: Dp? = null
    public var fontFamily: FontFamily? = null
    public var density: FewchursDensity = FewchursDensity.Comfortable
    public var darkMode: DarkMode = DarkMode.FollowHost

    /** Colors for light mode, and for dark mode unless [darkColors] is set. */
    public fun colors(block: FewchursColorOverrides.() -> Unit) {
        colors.block()
    }

    public fun darkColors(block: FewchursColorOverrides.() -> Unit) {
        darkColors = (darkColors ?: FewchursColorOverrides()).apply(block)
    }
}

public class BoardOptions {
    public var tabs: List<BoardTab> = listOf(BoardTab.Top, BoardTab.New)
    public var defaultTab: BoardTab = BoardTab.Top
    public var showDeclined: Boolean = true
    public var allowSubmissions: Boolean = true
    public var allowComments: Boolean = true
    public var emailField: EmailField = EmailField.Optional
    public var submitButtonStyle: SubmitButton = SubmitButton.ExtendedFab
    public var pageSize: Int = 20
}

/** Copy overrides. Null keeps the string resource (which you can also override per locale). */
public class FewchursStrings {
    public var boardTitle: String? = null
    public var submitButton: String? = null
    public var emptyTitle: String? = null
    public var emptyMessage: String? = null
    public var submitTitle: String? = null
    public var submittedTitle: String? = null
    public var submittedMessage: String? = null
    public var followDialogMessage: String? = null
}

private const val THEME_KEY = "fewchurs.ui.theme"
private const val BOARD_KEY = "fewchurs.ui.board"
private const val STRINGS_KEY = "fewchurs.ui.strings"

/** Theme tokens for the built-in UI. */
public fun FewchursConfig.Builder.theme(block: FewchursThemeSpec.() -> Unit) {
    (extensions.getOrPut(THEME_KEY) { FewchursThemeSpec() } as FewchursThemeSpec).block()
}

/** Behavior of the built-in board. */
public fun FewchursConfig.Builder.board(block: BoardOptions.() -> Unit) {
    (extensions.getOrPut(BOARD_KEY) { BoardOptions() } as BoardOptions).block()
}

/** Copy overrides for the built-in UI. */
public fun FewchursConfig.Builder.strings(block: FewchursStrings.() -> Unit) {
    (extensions.getOrPut(STRINGS_KEY) { FewchursStrings() } as FewchursStrings).block()
}

internal object UiConfig {
    private val defaultTheme = FewchursThemeSpec()
    private val defaultBoard = BoardOptions()
    private val defaultStrings = FewchursStrings()

    val theme: FewchursThemeSpec get() = Fewchurs.config?.extensions?.get(THEME_KEY) as? FewchursThemeSpec ?: defaultTheme
    val board: BoardOptions get() = Fewchurs.config?.extensions?.get(BOARD_KEY) as? BoardOptions ?: defaultBoard
    val strings: FewchursStrings get() = Fewchurs.config?.extensions?.get(STRINGS_KEY) as? FewchursStrings ?: defaultStrings
}
