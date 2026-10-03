package com.repflow.app.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.presentation.designsystem.LocalRepFlowExtraColors
import com.repflow.app.presentation.designsystem.LocalRepFlowSpacingScale
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowDefaultSpacingScale
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import com.repflow.app.presentation.designsystem.RepFlowShapeScheme
import com.repflow.app.presentation.designsystem.RepFlowTypography
import com.repflow.app.presentation.designsystem.repFlowExtraColors

/**
 * The app's Material 3 theme, built from the RepFlow design tokens in
 * `presentation/designsystem`.
 *
 * Every screen already renders through stock Material 3 components reading
 * `MaterialTheme`, so swapping the schemes here re-colours and re-types the
 * whole app without touching another file. The two tokens Material 3 has no
 * slot for (`control`, `hairline`) and the spacing scale ride alongside it as
 * composition locals.
 *
 * The extras are derived from the scheme this function applies, through
 * `repFlowExtraColors`, rather than from the `isSystemInDarkTheme()` call that
 * chose it. Both readings agree today - the same boolean picks both - but they
 * are one rule rather than two: this was the one place the design system asked
 * the system theme a second time, and `isDarkColorScheme`'s KDoc promises the
 * applied scheme wins. A `RepFlowTheme` that is one day handed an explicit
 * scheme therefore cannot end up with light colours around a dark hairline.
 * They are provided inside `MaterialTheme` so that binding is visible at the
 * call site.
 *
 * [themeMode] is the stored `Theme` preference (`System` / `Light` / `Dark`);
 * `System` follows the device, the other two override it ([isDarkTheme]).
 */
@Composable
fun RepFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isDarkTheme(themeMode, isSystemInDarkTheme())) RepFlowDarkColorScheme else RepFlowLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RepFlowTypography,
        shapes = RepFlowShapeScheme,
    ) {
        CompositionLocalProvider(
            LocalRepFlowExtraColors provides repFlowExtraColors(colorScheme),
            LocalRepFlowSpacingScale provides RepFlowDefaultSpacingScale,
            content = content,
        )
    }
}

/** Whether [mode] resolves to the dark scheme, given whether the system is dark: `System` follows it, `Light` and `Dark` ignore it. */
internal fun isDarkTheme(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean =
    when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
