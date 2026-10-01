package com.repflow.app.presentation.recovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowStepper
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal

/*
 * `3c`'s futsal section (`RepFlow.dc.html`, toggles `:2050`, block to `:2078`; script
 * `:4449-4466`):
 * the `Futsal` label, the two 52-tall toggles side by side, and - only while
 * "Played in last 24h" is on (`showFutsalBlock: s.futPrev`) - the card with
 * the minutes and session-RPE steppers, the hint, and the training load.
 */

@Composable
internal fun FutsalSection(
    uiState: RecoveryFutsalUiState,
    onFutsalPreviousToggled: (Boolean) -> Unit,
    onFutsalNextToggled: (Boolean) -> Unit,
    onDurationChanged: (String) -> Unit,
    onSessionRpeChanged: (String) -> Unit,
) {
    RepFlowSectionLabel(
        text = stringResource(R.string.recovery_futsal_futsal_section_title),
        modifier = Modifier.padding(top = SectionTopGap - ScaleRowGapAbove, bottom = SectionBottomGap),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        FutsalToggle(
            text = stringResource(R.string.recovery_futsal_futsal_previous_24h),
            checked = uiState.futsalInPrevious24h,
            onCheckedChange = onFutsalPreviousToggled,
            modifier = Modifier.weight(1f),
        )
        FutsalToggle(
            text = stringResource(R.string.recovery_futsal_futsal_next_24h),
            checked = uiState.futsalExpectedNext24h,
            onCheckedChange = onFutsalNextToggled,
            modifier = Modifier.weight(1f),
        )
    }
    if (uiState.futsalInPrevious24h) {
        FutsalSessionCard(
            uiState = uiState,
            onDurationChanged = onDurationChanged,
            onSessionRpeChanged = onSessionRpeChanged,
            modifier = Modifier.padding(top = ToggleBottomGap),
        )
    }
}

/**
 * One of `3c`'s futsal toggles: 52 tall, radius 10, the selected pill's
 * colours while on. A checkbox to accessibility, and a `check-circle` while
 * on, so the state is never carried by the tint alone (`6b`).
 */
@Composable
private fun FutsalToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (checked) selected.fill else Color.Transparent
    val border = if (checked) selected.border else RepFlowColor.hairline
    val label = if (checked) selected.label else repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier =
            modifier
                .heightIn(min = ToggleMinHeight)
                .clip(ToggleShape)
                .background(fill, ToggleShape)
                .border(BorderStroke(1.dp, border), ToggleShape)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = RepFlowSpacing.gapMd),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(RepFlowIcons.checkCircle),
                contentDescription = null,
                tint = label,
                modifier = Modifier.padding(end = ToggleGlyphGap).size(ToggleGlyphSize),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = ToggleFontSize, lineHeight = ToggleLineHeight),
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FutsalSessionCard(
    uiState: RecoveryFutsalUiState,
    onDurationChanged: (String) -> Unit,
    onSessionRpeChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    RepFlowCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(RepFlowSpacing.cardPaddingMin)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
            FutsalStepper(
                label = stringResource(R.string.recovery_futsal_duration_minutes),
                value = uiState.durationMinutesInput,
                onValueChanged = onDurationChanged,
                step = MINUTES_STEP,
                maxValue = null,
                allowDecimal = false,
                decrementDescription = stringResource(R.string.recovery_futsal_duration_decrease),
                incrementDescription = stringResource(R.string.recovery_futsal_duration_increase),
                modifier = Modifier.weight(1f),
            )
            FutsalStepper(
                label = stringResource(R.string.recovery_futsal_session_rpe),
                value = uiState.sessionRpeInput,
                onValueChanged = onSessionRpeChanged,
                step = BigDecimal.ONE,
                maxValue = SESSION_RPE_MAX,
                allowDecimal = true,
                decrementDescription = stringResource(R.string.recovery_futsal_session_rpe_decrease),
                incrementDescription = stringResource(R.string.recovery_futsal_session_rpe_increase),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.recovery_futsal_stepper_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = HintFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = RepFlowSpacing.gapLg),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.bottomBarEdgeAlpha),
        )
        TrainingLoadLine(load = uiState.futsalLoad, modifier = Modifier.padding(top = RepFlowSpacing.gapLg))
    }
}

/**
 * A `Minutes` or `Session RPE` stepper: CP3's stepper at its compact 44/24
 * size, whose value opens the keypad (`3c`: "Type minutes"). An empty field
 * reads `–` rather than a number nobody entered.
 */
@Composable
private fun FutsalStepper(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    step: BigDecimal,
    maxValue: BigDecimal?,
    allowDecimal: Boolean,
    decrementDescription: String,
    incrementDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = HintFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.padding(bottom = RepFlowSpacing.gapXs),
        )
        RepFlowStepper(
            value = value.trim().toBigDecimalOrNull(),
            onValueChange = { onValueChanged(RepFlowStepperMath.format(it)) },
            step = step,
            keypadTitle = label,
            decrementContentDescription = decrementDescription,
            incrementContentDescription = incrementDescription,
            maxValue = maxValue,
            allowDecimal = allowDecimal,
            emptyText = EMPTY_VALUE,
        )
    }
}

/** `Training load 420 — minutes × RPE`, the figure in 600 (`:2075-2077`). */
@Composable
private fun TrainingLoadLine(
    load: Double?,
    modifier: Modifier = Modifier,
) {
    val figure = load?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: EMPTY_VALUE
    val sentence = stringResource(R.string.recovery_futsal_load, figure)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.soccerBall),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LoadGlyphSize),
        )
        Text(
            text = emphasised(sentence, figure, MaterialTheme.colorScheme.onSurface),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = LoadFontSize, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = LOAD_TEXT_ALPHA),
        )
    }
}

/** [sentence] with the first occurrence of [figure] in 600 and full-strength [color]. */
private fun emphasised(
    sentence: String,
    figure: String,
    color: Color,
): AnnotatedString {
    val start = sentence.indexOf(figure)
    return buildAnnotatedString {
        append(sentence)
        if (start >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color), start, start + figure.length)
        }
    }
}

private const val MINUTES_STEP_SIZE = 5L
private val MINUTES_STEP = BigDecimal.valueOf(MINUTES_STEP_SIZE)
private val SESSION_RPE_MAX = BigDecimal.TEN
private const val EMPTY_VALUE = "–"
private const val LOAD_TEXT_ALPHA = 0.7f

/** The scale row above already leaves its own 16 below it. */
private val ScaleRowGapAbove = 16.dp
private val ToggleMinHeight = 52.dp
private val ToggleShape = RoundedCornerShape(10.dp)
private val ToggleFontSize = 13.5.sp
private val ToggleLineHeight = 17.5.sp
private val ToggleGlyphSize = 15.dp
private val ToggleGlyphGap = 6.dp
private val ToggleBottomGap = 14.dp
private val HintFontSize = 11.5.sp
private val LoadFontSize = 13.sp
private val LoadGlyphSize = 16.dp
