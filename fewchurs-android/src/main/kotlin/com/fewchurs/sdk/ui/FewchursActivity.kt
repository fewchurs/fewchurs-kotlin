package com.fewchurs.sdk.ui

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Hosts [FewchursBoard] for View-based apps. Opened by [com.fewchurs.sdk.Fewchurs.showBoard].
 * Picks up the app theme's `colorPrimary` so the board matches the app.
 */
public class FewchursActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val brand = appPrimaryColor()
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                brand != null -> {
                    val on = if (brand.luminance() > 0.5f) Color.Black else Color.White
                    if (dark) darkColorScheme(primary = brand, onPrimary = on) else lightColorScheme(primary = brand, onPrimary = on)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                else -> if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                FewchursBoard(onClose = ::finish)
            }
        }
    }

    private fun appPrimaryColor(): Color? {
        val appTheme = applicationInfo.theme.takeIf { it != 0 } ?: return null
        val themed = ContextThemeWrapper(this, appTheme)
        val value = TypedValue()
        // AppCompat / Material Components attribute first, then the platform one.
        val attrs = listOfNotNull(
            resources.getIdentifier("colorPrimary", "attr", packageName).takeIf { it != 0 },
            android.R.attr.colorPrimary,
        )
        for (attr in attrs) {
            if (themed.theme.resolveAttribute(attr, value, true) &&
                value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return Color(value.data)
            }
        }
        return null
    }
}
