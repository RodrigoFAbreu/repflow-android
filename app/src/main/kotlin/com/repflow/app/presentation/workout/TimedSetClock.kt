package com.repflow.app.presentation.workout

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowCardDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.formatDuration
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.components.spokenDuration
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.Instant

/**
 * A timed set's clock (`20o`, `20p`), directly above the Time card: `Clock`, the
 * time, one line, one button. It draws what [SetEntryState] holds and counts
 * nothing itself, so what it shows survives whatever the entry survives.
 *
 * Past the target the time turns accent and the card gets an accent edge, and the
 * check mark and the words mean colour is never the only cue. A range is reached
 * at its bottom, the moment `timed-set-alert` will fire.
 *
 * The time is not a live region: a value that changes every second would make a
 * screen reader announce itself continuously. It carries its words for when the
 * user focuses it, as the top bar's elapsed time does.
 *
 * @param nowMillis read at each tap, so a test can hold what `Stop` writes.
 */
@Composable
internal fun TimedSetClockCard(
    entry: SetEntryState,
    target: LongRange?,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier,
) {
    val startedAt = entry.clockStartedAtMillis
    val stopped = entry.clockStoppedSeconds
    val running = startedAt != null
    val shown = if (startedAt != null) rememberClockSeconds(startedAt) else stopped ?: 0
    val pastTarget = running && target != null && shown >= target.first
    val scheme = MaterialTheme.colorScheme
    val edge =
        if (pastTarget) {
            Modifier.border(
                BorderStroke(1.dp, repFlowSelectedPillColors(scheme).border),
                RepFlowCardDefaults.shape,
            )
        } else {
            Modifier
        }
    RepFlowCard(
        modifier = modifier.fillMaxWidth().then(edge).testTag(TIMED_SET_CLOCK_CARD_TAG),
        contentPadding = PaddingValues(ClockCardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClockCardGap)) {
            FocusFieldLabel(text = stringResource(R.string.workout_focus_clock_label))
            ClockReadout(seconds = shown, color = if (pastTarget) repFlowAccentOutlineColors(scheme).label else scheme.onSurface)
            val line = clockLine(running, stopped, pastTarget, target) { formatDuration(it) }
            val spokenLine = clockLine(running, stopped, pastTarget, target) { spokenDuration(it) }
            if (line != null && spokenLine != null) {
                ClockLine(
                    icon =
                        when {
                            stopped != null -> RepFlowIcons.stopBold
                            pastTarget -> RepFlowIcons.check
                            else -> RepFlowIcons.timer
                        },
                    text = line,
                    spokenText = spokenLine,
                )
            }
            ClockButton(running = running, stopped = stopped != null, entry = entry, nowMillis = nowMillis)
        }
    }
}

/**
 * The running clock's whole seconds, from its start moment and the wall clock on
 * each tick. [rememberElapsedSeconds] doesn't clamp, and a clock set back past the
 * start moment would otherwise read `0:-5`.
 */
@Composable
private fun rememberClockSeconds(startedAtMillis: Long): Long =
    rememberElapsedSeconds(Instant.ofEpochMilli(startedAtMillis)).coerceAtLeast(0)

@Composable
private fun ClockReadout(
    seconds: Long,
    color: Color,
) {
    val spoken = spokenDuration(seconds)
    Text(
        text = formatDuration(seconds),
        style = RepFlowNumericTextStyle.copy(fontSize = ClockFontSize, lineHeight = ClockLineHeight),
        color = color,
        modifier = Modifier.testTag(TIMED_SET_CLOCK_READOUT_TAG).semantics { contentDescription = spoken },
    )
}

/**
 * What the line under the time says, or `null` when an exercise with no target has
 * nothing to say. [time] writes each time: as the clock for the screen, in words
 * for a screen reader, so the two can't say different things.
 */
@Composable
private fun clockLine(
    running: Boolean,
    stopped: Long?,
    pastTarget: Boolean,
    target: LongRange?,
    time: @Composable (Long) -> String,
): String? {
    if (stopped != null) return stringResource(R.string.workout_focus_clock_stopped_at, time(stopped))
    if (target == null) return null
    val fixed = target.first == target.last
    if (pastTarget) {
        return stringResource(
            if (fixed) R.string.workout_focus_clock_target_reached else R.string.workout_focus_clock_target_passed,
            time(target.first),
        )
    }
    val targetText =
        if (fixed) {
            stringResource(R.string.workout_focus_clock_target, time(target.first))
        } else {
            stringResource(R.string.workout_focus_clock_target_range, time(target.first), time(target.last))
        }
    return if (running) stringResource(R.string.workout_focus_clock_counting_up, targetText) else targetText
}

@Composable
private fun ClockLine(
    @DrawableRes icon: Int,
    text: String,
    spokenText: String,
) {
    val scheme = MaterialTheme.colorScheme
    // The glyph stays one size while the text grows, so it is centred on the first line, not a fixed distance down.
    val iconTop = with(LocalDensity.current) { (ClockLineLineHeight.toDp() - ClockLineIconSize) / 2 }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ClockLineGap),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = repFlowAccentOutlineColors(scheme).label,
            modifier = Modifier.padding(top = iconTop).size(ClockLineIconSize),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = ClockLineFontSize, lineHeight = ClockLineLineHeight),
            color = repFlowSecondaryTextColor(scheme),
            modifier = Modifier.semantics { contentDescription = spokenText },
        )
    }
}

/** `Start` and `Stop` in the accent outline at 52 dp, `Start again` in the neutral one at 48, each the card's width. */
@Composable
private fun ClockButton(
    running: Boolean,
    stopped: Boolean,
    entry: SetEntryState,
    nowMillis: () -> Long,
) {
    when {
        running -> {
            RepFlowAccentOutlineButton(
                text = stringResource(R.string.workout_focus_clock_stop),
                onClick = { entry.stopClock(nowMillis()) },
                leadingIcon = RepFlowIcons.stopBold,
                modifier = Modifier.fillMaxWidth().heightIn(min = ClockMainButtonMinHeight),
            )
        }

        stopped -> {
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.workout_focus_clock_start_again),
                onClick = { entry.startClock(nowMillis()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ClockStartAgainMinHeight),
            )
        }

        else -> {
            RepFlowAccentOutlineButton(
                text = stringResource(R.string.workout_focus_clock_start),
                onClick = { entry.startClock(nowMillis()) },
                leadingIcon = RepFlowIcons.playBold,
                modifier = Modifier.fillMaxWidth().heightIn(min = ClockMainButtonMinHeight),
            )
        }
    }
}

/** Lets a test read the card's top edge, and bound the time, which is on the real clock. */
internal const val TIMED_SET_CLOCK_CARD_TAG = "timed_set_clock_card"
internal const val TIMED_SET_CLOCK_READOUT_TAG = "timed_set_clock_readout"

/** `clockCard`: padding 10, gap 8, the time at 40/500 with line-height 1.15. */
private val ClockCardPadding = 10.dp
private val ClockCardGap = 8.dp
private val ClockFontSize = 40.sp
private val ClockLineHeight = 46.sp

/** `clockCard`'s line: 12.5 at line-height 1.4, gap 6. */
private val ClockLineFontSize = 12.5.sp
private val ClockLineLineHeight = 17.5.sp
private val ClockLineGap = 6.dp
private val ClockLineIconSize = 14.dp

private val ClockMainButtonMinHeight = 52.dp
private val ClockStartAgainMinHeight = 48.dp
