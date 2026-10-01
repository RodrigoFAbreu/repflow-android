package com.repflow.app.presentation.progression

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * What changes between the recommendation screen's five outcome states
 * (remediation-1 CP6): `6c`'s own rule is that only the glyph and the accent
 * treatment change, never the layout - so those are the only per-outcome
 * values, and they live here.
 */

/** One glyph per outcome (`6a`/`6c`); `Maintain load` has no artboard of its own and takes `6a`'s value-card arrow. */
@DrawableRes
internal fun ProgressionResultUi.outcomeIcon(): Int =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> RepFlowIcons.trendUp
        ProgressionResultUi.MAINTAIN_LOAD -> RepFlowIcons.arrowRight
        ProgressionResultUi.REDUCE_LOAD -> RepFlowIcons.trendDown
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> RepFlowIcons.heartbeat
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> RepFlowIcons.hourglassMedium
    }

/** The reason rows' glyph: `6a`'s check for an increase or a hold, `6c`'s arrow-down for a reduction and warning for recovery. */
@DrawableRes
internal fun ProgressionResultUi.reasonIcon(): Int =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD, ProgressionResultUi.MAINTAIN_LOAD -> RepFlowIcons.checkCircle
        ProgressionResultUi.REDUCE_LOAD -> RepFlowIcons.arrowDown
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> RepFlowIcons.warningCircle
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> RepFlowIcons.info
    }

/** The `Your call` row's second line, for a row that is not the suggestion itself. */
@StringRes
internal fun ProgressionResultUi.optionSubRes(): Int =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> R.string.progression_option_increase
        ProgressionResultUi.MAINTAIN_LOAD -> R.string.progression_option_maintain
        ProgressionResultUi.REDUCE_LOAD -> R.string.progression_option_reduce
        ProgressionResultUi.RECOVERY_ADJUSTMENT, ProgressionResultUi.WAIT_FOR_MORE_DATA -> R.string.progression_option_suggested
    }

/**
 * The outcome glyph's tint (`6a`/`6c`): accent for an increase or a hold, the
 * destructive tone for a reduction, neutral for recovery and for not enough
 * data. Never the only signal - the outcome's word is always beside it.
 */
@Composable
internal fun outcomeTint(result: ProgressionResultUi): Color {
    val scheme = MaterialTheme.colorScheme
    return when (result) {
        ProgressionResultUi.INCREASE_LOAD, ProgressionResultUi.MAINTAIN_LOAD -> repFlowAccentOutlineColors(scheme).label
        ProgressionResultUi.REDUCE_LOAD -> scheme.error
        ProgressionResultUi.RECOVERY_ADJUSTMENT, ProgressionResultUi.WAIT_FOR_MORE_DATA -> repFlowSecondaryTextColor(scheme)
    }
}
