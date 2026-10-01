package com.repflow.app.presentation.home

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.repflow.app.R
import com.repflow.app.domain.recovery.ReadinessBand
import com.repflow.app.presentation.designsystem.isDarkColorScheme

/*
 * How a readiness band is drawn: its word and its colour. Every band is a
 * word first - `6b`'s "destructive and accent are never the only signal" -
 * and the colour is the second signal on top of it.
 *
 * Dark values are the prototype's `RDY_BANDS` (`RepFlow.dc.html:3153-3158`),
 * the three `oklch()` ones converted to sRGB: Ready `#b5abfc`; Hold
 * `oklch(.80 .10 85)` = `#DBB970`; Back off `oklch(.76 .12 55)` = `#EC9C63`;
 * Protect `oklch(.72 .13 25)` = `#EB827B`, which is the dark scheme's `error`
 * already. On `surface` they measure 7.38 / 8.09 / 6.87 / 5.80 :1.
 *
 * The design draws no band in light theme, and its dark values measure
 * 1.73-2.41:1 on the light `surface`. Light therefore keeps each band's hue
 * and chroma and lowers OKLCH lightness to the first step that clears 4.5:1
 * on both light grounds (deviation register D41): Ready reuses light
 * `primary` (accent-700), Protect reuses light `error`, Hold is
 * `oklch(.515 .10 85)` = `#826210` and Back off `oklch(.525 .12 55)` =
 * `#9E5416`.
 */

/** The band's word - the sheet's and Home's band label. */
@StringRes
internal fun readinessBandLabel(band: ReadinessBand): Int =
    when (band) {
        ReadinessBand.READY -> R.string.readiness_band_ready
        ReadinessBand.HOLD -> R.string.readiness_band_hold
        ReadinessBand.BACK_OFF -> R.string.readiness_band_back_off
        ReadinessBand.PROTECT -> R.string.readiness_band_protect
    }

/** The band's colour for [scheme]'s theme; see the file note for where each value comes from. */
internal fun readinessBandColor(
    band: ReadinessBand,
    scheme: ColorScheme,
): Color =
    if (isDarkColorScheme(scheme)) {
        when (band) {
            ReadinessBand.READY -> ReadinessBandColors.readyDark
            ReadinessBand.HOLD -> ReadinessBandColors.holdDark
            ReadinessBand.BACK_OFF -> ReadinessBandColors.backOffDark
            ReadinessBand.PROTECT -> scheme.error
        }
    } else {
        when (band) {
            ReadinessBand.READY -> scheme.primary
            ReadinessBand.HOLD -> ReadinessBandColors.holdLight
            ReadinessBand.BACK_OFF -> ReadinessBandColors.backOffLight
            ReadinessBand.PROTECT -> scheme.error
        }
    }

internal object ReadinessBandColors {
    val readyDark = Color(READY_DARK_ARGB)
    val holdDark = Color(HOLD_DARK_ARGB)
    val backOffDark = Color(BACK_OFF_DARK_ARGB)
    val holdLight = Color(HOLD_LIGHT_ARGB)
    val backOffLight = Color(BACK_OFF_LIGHT_ARGB)
}

private const val READY_DARK_ARGB = 0xFFB5ABFC
private const val HOLD_DARK_ARGB = 0xFFDBB970
private const val BACK_OFF_DARK_ARGB = 0xFFEC9C63
private const val HOLD_LIGHT_ARGB = 0xFF826210
private const val BACK_OFF_LIGHT_ARGB = 0xFF9E5416
