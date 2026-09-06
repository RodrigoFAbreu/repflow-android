package com.repflow.app.presentation.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Colour tokens for the RepFlow visual foundation.
 *
 * Values come from the live Claude Design project's own RepFlow component
 * spec, transcribed in docs/milestones/repflow-redesign-visual-foundation-
 * reference.md. Which ColorScheme roles are assigned here (and which are
 * deliberately left at the Material 3 baseline, because a stock component
 * elsewhere in the app reads the same role) is recorded in ROLE_AUDIT.md
 * next to this file - read that before adding or changing a role.
 */

/**
 * Design tokens with no Material 3 `ColorScheme` slot of their own, but which
 * still vary per theme. Reached through [RepFlowColor], never directly.
 */
@Immutable
data class RepFlowExtraColors(
    /** Third surface tier below `surface`: stepper buttons, chip backgrounds. */
    val control: Color,
    /**
     * Hairline border for the primitives that draw one. Deliberately *not*
     * `ColorScheme.outline`, which is also every stock `OutlinedTextField`'s
     * and `Switch`'s resting border, where this value measures 1.2-1.8:1.
     */
    val hairline: Color,
)

val RepFlowDarkExtraColors =
    RepFlowExtraColors(
        control = Color(0xFF292B31),
        hairline = Color(0xFF3F424D),
    )

/**
 * Light `control` is neutral-400, one ramp step past `hairline`'s neutral-300:
 * the design renders no stepper/chip/scale-row element in light theme, so this
 * is a disclosed judgment call rather than a design-confirmed value. Reusing
 * neutral-300 would collapse `control` and `hairline` onto the same colour and
 * leave control-filled primitives with no visible border.
 */
val RepFlowLightExtraColors =
    RepFlowExtraColors(
        control = Color(0xFFB2B6CA),
        hairline = Color(0xFFCFD3E5),
    )

/**
 * Deliberately has **no** default value.
 *
 * These two tokens are the only ones in the system that vary per theme and
 * have no `ColorScheme` slot to fall back on, so any default would be one
 * theme's values handed to whatever composition forgot to install
 * [com.repflow.app.presentation.RepFlowTheme] - a plausible-looking wrong
 * colour rather than a failure. Failing loudly instead means a missing
 * provider is found by the first test or preview that renders the surface,
 * not by someone noticing the wrong grey.
 */
val LocalRepFlowExtraColors =
    staticCompositionLocalOf<RepFlowExtraColors> {
        error("No RepFlowExtraColors provided - wrap this content in RepFlowTheme { }")
    }

/**
 * The RepFlow tokens that sit outside `MaterialTheme.colorScheme`.
 *
 * `MaterialTheme.colorScheme` stays canonical for every role Material 3 has a
 * slot for; this object is reached for exactly the handful of values it does
 * not: the two per-theme extras ([control], [hairline]), the four accent-ramp
 * steps the primitives quote directly, and the bottom nav's own opacity
 * multipliers.
 */
object RepFlowColor {
    val control: Color
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowExtraColors.current.control

    val hairline: Color
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowExtraColors.current.hairline

    /*
     * Accent-ramp steps read verbatim off the shared Nocturne ramp. These are
     * theme-independent constants - which step a given theme's variant of a
     * primitive uses is stated per component, not baked into the token.
     */

    /** accent-300: dark selected nav icon, dark accent-outline button label. */
    val accent300 = Color(0xFFD2CEFD)

    /** accent-600: light accent-outline button border. */
    val accent600 = Color(0xFF796CBF)

    /** accent-700: light `primary`, dark accent-outline button border. */
    val accent700 = Color(0xFF5D5294)

    /** accent-900: accent-tinted card fill. */
    val accent900 = Color(0xFF2B2741)

    /*
     * Opacity multipliers the bottom nav applies to already-assigned roles.
     * Named here rather than left as literals inside the @Composable call site
     * so RepFlowBottomNavigationBar and RepFlowThemeTest read the same value.
     */

    /** Dark unselected nav icon + label: `onSurface` at 60%, the design's own value. */
    val navUnselectedAlphaDark = 0.6f

    /**
     * Light unselected nav icon + label: `onSurface` at 66%. The design's
     * literal 55% composites to 3.34:1 against the light bar, below the 4.5:1
     * an always-visible label owes; 66% is the first step that clears it.
     */
    val navUnselectedAlphaLight = 0.66f

    /** Dark selected-item indicator pill: `primary` at 20%. */
    val navSelectedIndicatorAlphaDark = 0.20f

    /** Light selected-item indicator pill: `primary` at 16%. */
    val navSelectedIndicatorAlphaLight = 0.16f
}

/**
 * Which theme is in force, asked of the applied scheme rather than of
 * `isSystemInDarkTheme()`.
 *
 * Several primitives read a different design value per theme, and the scheme
 * they are actually rendering with is the honest input: a preview or a future
 * theme override that supplies the light scheme gets light's values, where a
 * second `isSystemInDarkTheme()` call would silently disagree with the colours
 * around it.
 *
 * It lives here, beside the two schemes whose luminance it reads, rather than
 * in `components/`: it resolves a token, it is not a button concern, and the
 * bottom nav reads it without that making `navigation` depend on `components`.
 */
internal fun isDarkColorScheme(scheme: ColorScheme): Boolean = scheme.surface.luminance() < LIGHT_SURFACE_LUMINANCE_FLOOR

/** Both schemes' `surface` sit far from this - dark 0.02, light 0.90. */
private const val LIGHT_SURFACE_LUMINANCE_FLOOR = 0.5f

/**
 * Dark scheme.
 *
 * `surfaceContainer` is the bottom nav's own bar fill and is a value of its
 * own, distinct from both `background` and `surface`. `surfaceContainerHigh`
 * (dialog/date-picker container) and `surfaceContainerHighest` (the plan
 * editor's row card) are both reassigned to `surface` so the `primary`-coloured
 * text those containers carry clears 4.5:1 - see ROLE_AUDIT.md.
 */
val RepFlowDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF9184D9),
        onPrimary = Color(0xFF161826),
        secondary = Color(0xFFE9E9ED),
        background = Color(0xFF161826),
        onBackground = Color(0xFFE9E9ED),
        surface = Color(0xFF232532),
        onSurface = Color(0xFFE9E9ED),
        surfaceContainer = Color(0xFF1B1D2B),
        surfaceContainerHigh = Color(0xFF232532),
        surfaceContainerHighest = Color(0xFF232532),
        error = Color(0xFFEB827B),
    )

/**
 * Light scheme, built from the same neutral ramp rather than a separate
 * palette. `primary` is accent-700, not the base accent the dark scheme uses,
 * and `onPrimary` is accent-100 rather than dark's ground colour: dark's pair
 * would measure 2.60:1 on this lighter fill.
 *
 * `surfaceContainerHighest` is deliberately left at the Material 3 baseline -
 * light `primary` already clears 4.5:1 on it, so there is nothing to fix.
 */
val RepFlowLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF5D5294),
        onPrimary = Color(0xFFF5F4FF),
        secondary = Color(0xFF292B31),
        background = Color(0xFFE4E7F5),
        onBackground = Color(0xFF292B31),
        surface = Color(0xFFF3F5FE),
        onSurface = Color(0xFF292B31),
        surfaceContainer = Color(0xFFF3F5FE),
        surfaceContainerHigh = Color(0xFFF3F5FE),
        error = Color(0xFFA74541),
    )
