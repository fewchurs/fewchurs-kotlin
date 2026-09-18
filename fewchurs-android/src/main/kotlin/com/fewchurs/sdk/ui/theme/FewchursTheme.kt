package com.fewchurs.sdk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.ui.config.DarkMode
import com.fewchurs.sdk.ui.config.FewchursColorOverrides
import com.fewchurs.sdk.ui.config.FewchursDensity
import com.fewchurs.sdk.ui.config.FewchursThemeSpec
import com.fewchurs.sdk.ui.config.UiConfig

@Immutable
public data class FewchursColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    val border: Color,
    val error: Color,
    val statusColors: Map<FeatureStatus, Color>,
) {
    public fun status(status: FeatureStatus): Color = statusColors[status] ?: muted
}

@Immutable
public data class FewchursShapes(
    val card: Shape,
    val button: Shape,
    val chip: Shape,
    val sheet: Shape,
)

@Immutable
public data class FewchursTypography(
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

@Immutable
public data class FewchursSpacing(
    val screen: Dp,
    val cardPadding: Dp,
    val itemGap: Dp,
) {
    public companion object {
        public val Comfortable: FewchursSpacing = FewchursSpacing(screen = 16.dp, cardPadding = 16.dp, itemGap = 12.dp)
        public val Compact: FewchursSpacing = FewchursSpacing(screen = 12.dp, cardPadding = 12.dp, itemGap = 8.dp)
    }
}

private class ThemeOverride(
    val colors: FewchursColors?,
    val shapes: FewchursShapes?,
    val typography: FewchursTypography?,
    val spacing: FewchursSpacing?,
)

private val LocalThemeOverride = staticCompositionLocalOf<ThemeOverride?> { null }
private val LocalColors = staticCompositionLocalOf<FewchursColors?> { null }
private val LocalShapes = staticCompositionLocalOf<FewchursShapes?> { null }
private val LocalTypography = staticCompositionLocalOf<FewchursTypography?> { null }
private val LocalSpacing = staticCompositionLocalOf { FewchursSpacing.Comfortable }

/** Read the resolved tokens inside Fewchurs UI or your own slot content. */
public object FewchursTheme {
    public val colors: FewchursColors
        @Composable @ReadOnlyComposable get() = LocalColors.current ?: defaultColors(MaterialTheme.colorScheme)

    public val shapes: FewchursShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current ?: defaultShapes(MaterialTheme.shapes, null)

    public val typography: FewchursTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current ?: defaultTypography(MaterialTheme.typography)

    public val spacing: FewchursSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
}

/**
 * Scoped theme for the Fewchurs UI inside [content]. Wins over `Fewchurs.configure { theme { } }`,
 * which wins over the host MaterialTheme.
 */
@Composable
public fun FewchursTheme(
    colors: FewchursColors? = null,
    shapes: FewchursShapes? = null,
    typography: FewchursTypography? = null,
    spacing: FewchursSpacing? = null,
    content: @Composable () -> Unit,
) {
    val outer = LocalThemeOverride.current
    val merged = ThemeOverride(
        colors = colors ?: outer?.colors,
        shapes = shapes ?: outer?.shapes,
        typography = typography ?: outer?.typography,
        spacing = spacing ?: outer?.spacing,
    )
    CompositionLocalProvider(LocalThemeOverride provides merged, content = content)
}

/** Resolves every token source and applies it to both Fewchurs tokens and MaterialTheme. */
@Composable
internal fun ResolvedFewchursTheme(content: @Composable () -> Unit) {
    val spec = UiConfig.theme
    val override = LocalThemeOverride.current
    val host = MaterialTheme.colorScheme
    val dark = when (spec.darkMode) {
        DarkMode.FollowHost -> host.background.luminance() < 0.5f
        DarkMode.System -> isSystemInDarkTheme()
        DarkMode.Light -> false
        DarkMode.Dark -> true
    }
    val overrides = if (dark) spec.darkColors ?: spec.colors else spec.colors
    val baseScheme = when (spec.darkMode) {
        DarkMode.FollowHost -> host
        else -> (if (dark) darkColorScheme() else lightColorScheme()).let { generated ->
            // Keep the host brand color when switching to a generated light/dark scheme.
            if (host.primary != lightColorScheme().primary) generated.copy(primary = host.primary, onPrimary = host.onPrimary) else generated
        }
    }
    val scheme = baseScheme.applying(overrides)

    val colors = override?.colors ?: defaultColors(scheme).applying(overrides)
    val shapes = override?.shapes ?: defaultShapes(MaterialTheme.shapes, spec.cornerRadius?.let { RoundedCornerShape(it) })
    val typography = override?.typography ?: defaultTypography(MaterialTheme.typography.withFont(spec))
    val spacing = override?.spacing ?: if (spec.density == FewchursDensity.Compact) FewchursSpacing.Compact else FewchursSpacing.Comfortable

    val materialShapes = spec.cornerRadius?.let { r ->
        MaterialTheme.shapes.copy(small = RoundedCornerShape(r), medium = RoundedCornerShape(r), large = RoundedCornerShape(r))
    } ?: MaterialTheme.shapes
    val materialScheme = scheme.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        surface = colors.background,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.muted,
        outlineVariant = colors.border,
        error = colors.error,
    )

    MaterialTheme(
        colorScheme = materialScheme,
        shapes = materialShapes,
        typography = MaterialTheme.typography.withFont(spec),
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalShapes provides shapes,
            LocalTypography provides typography,
            LocalSpacing provides spacing,
            content = content,
        )
    }
}

private fun ColorScheme.applying(o: FewchursColorOverrides): ColorScheme {
    val primary = o.primary.takeOrElse { primary }
    return copy(
        primary = primary,
        onPrimary = o.onPrimary.takeOrElse { if (o.primary.isSpecified()) bestOn(primary) else onPrimary },
        background = o.background.takeOrElse { background },
        surface = o.background.takeOrElse { surface },
        onSurface = o.onSurface.takeOrElse { onSurface },
        onBackground = o.onSurface.takeOrElse { onBackground },
        error = o.error.takeOrElse { error },
    )
}

private fun FewchursColors.applying(o: FewchursColorOverrides): FewchursColors = copy(
    surface = o.surface.takeOrElse { surface },
    muted = o.muted.takeOrElse { muted },
    border = o.border.takeOrElse { border },
    statusColors = statusColors + o.statusColors,
)

private fun Color.isSpecified() = this != Color.Unspecified

private fun bestOn(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

internal fun defaultColors(scheme: ColorScheme): FewchursColors {
    val dark = scheme.background.luminance() < 0.5f
    return FewchursColors(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        background = scheme.background,
        surface = scheme.surfaceContainerLow,
        onSurface = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        border = scheme.outlineVariant,
        error = scheme.error,
        statusColors = mapOf(
            FeatureStatus.OPEN to scheme.onSurfaceVariant,
            FeatureStatus.PLANNED to if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB),
            FeatureStatus.IN_PROGRESS to if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
            FeatureStatus.DONE to if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
            FeatureStatus.DECLINED to scheme.error,
            FeatureStatus.UNKNOWN to scheme.onSurfaceVariant,
        ),
    )
}

internal fun defaultShapes(shapes: Shapes, custom: Shape?): FewchursShapes = FewchursShapes(
    card = custom ?: shapes.medium,
    button = custom ?: shapes.medium,
    chip = RoundedCornerShape(50),
    sheet = shapes.extraLarge,
)

internal fun defaultTypography(t: Typography): FewchursTypography = FewchursTypography(
    title = t.titleMedium,
    body = t.bodyMedium,
    label = t.labelMedium,
)

private fun Typography.withFont(spec: FewchursThemeSpec): Typography {
    val font = spec.fontFamily ?: return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = font), displayMedium = displayMedium.copy(fontFamily = font),
        displaySmall = displaySmall.copy(fontFamily = font), headlineLarge = headlineLarge.copy(fontFamily = font),
        headlineMedium = headlineMedium.copy(fontFamily = font), headlineSmall = headlineSmall.copy(fontFamily = font),
        titleLarge = titleLarge.copy(fontFamily = font), titleMedium = titleMedium.copy(fontFamily = font),
        titleSmall = titleSmall.copy(fontFamily = font), bodyLarge = bodyLarge.copy(fontFamily = font),
        bodyMedium = bodyMedium.copy(fontFamily = font), bodySmall = bodySmall.copy(fontFamily = font),
        labelLarge = labelLarge.copy(fontFamily = font), labelMedium = labelMedium.copy(fontFamily = font),
        labelSmall = labelSmall.copy(fontFamily = font),
    )
}
