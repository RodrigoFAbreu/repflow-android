package com.repflow.app.presentation.progression

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowCardDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.ReadinessSheet
import com.repflow.app.presentation.home.readinessBandColor
import com.repflow.app.presentation.home.readinessBandLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * The progression recommendation screen (remediation-1 CP6): `6a`
 * (`RepFlow.dc.html:43-196`) and `6c` (`:198-251`), with `6a`'s option copy
 * from the script (`:3462-3505`).
 *
 * Three states, as `6a` draws them: the **suggestion** (outcome, every reason
 * the policy used, the footnote; a pinned bar to go with it, pick another or
 * keep the same load), **`Your call`** (the three outcomes to choose from) and
 * the **recorded choice** (who decided what, then the same reasons; `Change my
 * mind` and `Done`). All five `ProgressionResult` cases render as their own
 * state - only the glyph, its tint and the action labels change, never the
 * layout (`6c`'s own note, `:246`).
 *
 * Not drawn, each a deviation-register row: the value card and every load
 * figure (`D49`), `What it looked at` (`D50`), the applied state's `Next
 * session starts as` and `Recorded as` lines and `Earlier suggestions`
 * (`D51`), the typed-load option (`D32`) and the override-streak line
 * (`D33`). The copy that says what a choice does is CP6's own (`D52`).
 */

private val OutcomeIconSize = 22.dp
private val ReasonIconSize = 16.dp
private val OptionRowMinHeight = 68.dp
private val OptionRowShape = RoundedCornerShape(12.dp)
private val RecordBoxShape = RoundedCornerShape(10.dp)
private val SecondaryActionMinHeight = 48.dp
private val CaretSize = 12.dp
private val OutcomeTitleStyleSize = 26.sp
private val ChooseTitleSize = 24.sp
private const val TITLE_LETTER_SPACING_EM = -0.015f
private const val REASON_TEXT_ALPHA = 0.8f
private const val BODY_TEXT_ALPHA = 0.7f
private const val OPTION_SELECTED_FILL_ALPHA = 0.12f
private const val RECORD_BOX_FILL_ALPHA = 0.05f
private const val CARET_ALPHA = 0.3f

@Suppress("LongParameterList")
@Composable
fun ProgressionRecommendationScreen(
    uiState: ProgressionRecommendationUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onChooseAnother: () -> Unit,
    onCloseChoices: () -> Unit,
    onPick: (ProgressionResultUi) -> Unit,
    onKeepSameLoad: () -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var readinessOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.error?.let { stringResource(R.string.progression_save_failed) }
    LaunchedEffect(uiState.error) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorShown()
        }
    }
    BackHandler(enabled = uiState.choosing, onBack = onCloseChoices)

    val content = uiState.content
    RepFlowScreenScaffold(
        title = stringResource(R.string.progression_screen_title),
        onBack = if (uiState.choosing) onCloseChoices else onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (content is RecommendationContent.Loaded && !uiState.choosing) {
                RecommendationActions(
                    content = content,
                    saving = uiState.saving,
                    onDone = onDone,
                    onChooseAnother = onChooseAnother,
                    onKeepSameLoad = onKeepSameLoad,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (content) {
                RecommendationContent.Loading -> {
                    RepFlowLoadingIndicator()
                }

                RecommendationContent.NotFound -> {
                    RepFlowEmptyState(message = stringResource(R.string.progression_not_found))
                }

                RecommendationContent.Failed -> {
                    RepFlowFailureState(
                        message = stringResource(R.string.progression_load_failed),
                        retryLabel = stringResource(R.string.progression_retry),
                        onRetry = onRetry,
                    )
                }

                is RecommendationContent.Loaded -> {
                    LoadedBody(
                        content = content,
                        choosing = uiState.choosing,
                        saving = uiState.saving,
                        onPick = onPick,
                        onCloseChoices = onCloseChoices,
                        onReadinessClick = { readinessOpen = true },
                    )
                }
            }
        }
    }

    val readiness = (content as? RecommendationContent.Loaded)?.readiness
    if (readinessOpen && readiness != null) {
        ReadinessSheet(readiness = readiness, onDismissRequest = { readinessOpen = false })
    }
}

@Suppress("LongParameterList")
@Composable
private fun LoadedBody(
    content: RecommendationContent.Loaded,
    choosing: Boolean,
    saving: Boolean,
    onPick: (ProgressionResultUi) -> Unit,
    onCloseChoices: () -> Unit,
    onReadinessClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = RepFlowSpacing.screenPadding),
    ) {
        content.exerciseName?.let { RepFlowSectionLabel(text = it) }
        when {
            choosing -> ChoicesBody(content = content, saving = saving, onPick = onPick, onBack = onCloseChoices)
            content.choice != null -> RecordedChoiceBody(content = content, choice = content.choice, onReadinessClick = onReadinessClick)
            else -> SuggestionBody(content = content, onReadinessClick = onReadinessClick)
        }
    }
}

/** `6a`'s suggestion state, and `6c`'s card content: outcome, reasons, footnote. */
@Composable
private fun SuggestionBody(
    content: RecommendationContent.Loaded,
    onReadinessClick: () -> Unit,
) {
    OutcomeHeadline(result = content.suggested)
    ReasonsSection(content = content, onReadinessClick = onReadinessClick)
}

/** `6a`'s outcome row: the glyph (22) and the outcome as the title (26/500). */
@Composable
private fun OutcomeHeadline(result: ProgressionResultUi) {
    Row(
        modifier = Modifier.padding(top = RepFlowSpacing.gapXs, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(result.outcomeIcon()),
            contentDescription = null,
            tint = outcomeTint(result),
            modifier = Modifier.size(OutcomeIconSize),
        )
        Text(
            text = stringResource(result.labelRes()),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = OutcomeTitleStyleSize,
                    letterSpacing = TITLE_LETTER_SPACING_EM.em,
                ),
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** `Why` and one row per reason the policy recorded; today's check-in for a recovery adjustment; the footnote. */
@Composable
private fun ReasonsSection(
    content: RecommendationContent.Loaded,
    onReadinessClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    RepFlowSectionLabel(
        text = stringResource(R.string.progression_why_label),
        modifier = Modifier.padding(top = 18.dp, bottom = RepFlowSpacing.gapSm),
    )
    content.reasons.forEach { reason ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = RepFlowSpacing.gapSm),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Icon(
                painter = painterResource(content.suggested.reasonIcon()),
                contentDescription = null,
                tint = outcomeTint(content.suggested),
                modifier = Modifier.padding(top = 2.dp).size(ReasonIconSize),
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = scheme.onSurface.copy(alpha = REASON_TEXT_ALPHA),
                modifier = Modifier.weight(1f),
            )
        }
    }
    content.readiness?.let { TodayReadinessCard(readiness = it, onClick = onReadinessClick) }
    Text(
        text = stringResource(R.string.progression_footnote, content.policyVersion),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        color = repFlowSecondaryTextColor(scheme),
        modifier = Modifier.padding(top = RepFlowSpacing.gapMd),
    )
}

/**
 * A recovery adjustment's link to the readiness sheet (plan CP6 item 4):
 * today's score and band, CP4's driver sentence, `Details ›`. The policy's own
 * recovery reasons stay above it, in the policy's words; this card says what
 * today's check-in reads, and claims nothing about what the policy saw.
 */
@Composable
private fun TodayReadinessCard(
    readiness: ReadinessScore,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bandColor = readinessBandColor(readiness.band, scheme)
    val bandWord = stringResource(readinessBandLabel(readiness.band))
    val accent = repFlowAccentOutlineColors(scheme).label
    val description = stringResource(R.string.home_readiness_content_description, readiness.score, bandWord)
    RepFlowCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = RepFlowSpacing.gapMd)
                .clip(RepFlowCardDefaults.shape)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        RepFlowSectionLabel(text = stringResource(R.string.progression_readiness_label))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.progression_readiness_value, readiness.score, bandWord),
                style = RepFlowNumericTextStyle.copy(fontSize = 17.sp, lineHeight = 22.sp),
                color = bandColor,
                modifier = Modifier.weight(1f),
            )
            Text(text = stringResource(R.string.home_readiness_details), style = MaterialTheme.typography.bodySmall, color = accent)
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(CaretSize),
            )
        }
        Text(
            text = readiness.driverSentence,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = scheme.onSurface.copy(alpha = REASON_TEXT_ALPHA),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** `6a`'s `Your call`: the three outcomes as 68-tall rows, then `Back`. */
@Composable
private fun ChoicesBody(
    content: RecommendationContent.Loaded,
    saving: Boolean,
    onPick: (ProgressionResultUi) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.progression_choose_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = ChooseTitleSize, letterSpacing = TITLE_LETTER_SPACING_EM.em),
        modifier = Modifier.padding(top = RepFlowSpacing.gapXs, bottom = 4.dp).semantics { heading() },
    )
    Text(
        text = stringResource(R.string.progression_choose_line),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
        color = repFlowSecondaryTextColor(scheme),
        modifier = Modifier.padding(bottom = RepFlowSpacing.screenPadding),
    )
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        RECOMMENDATION_CHOICES.forEach { option ->
            ChoiceRow(
                option = option,
                isSuggestion = option == content.suggested,
                selected = option == content.inForce,
                enabled = !saving,
                onClick = { onPick(option) },
            )
        }
    }
    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapXs).heightIn(min = SecondaryActionMinHeight),
    ) {
        Text(
            text = stringResource(R.string.progression_choose_back),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = repFlowSecondaryTextColor(scheme),
        )
    }
}

@Composable
private fun ChoiceRow(
    option: ProgressionResultUi,
    isSuggestion: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (selected) scheme.primary.copy(alpha = OPTION_SELECTED_FILL_ALPHA) else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = OptionRowMinHeight)
                .clip(OptionRowShape)
                .background(fill, OptionRowShape)
                .border(1.dp, RepFlowColor.hairline, OptionRowShape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(option.labelRes()), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp))
            Text(
                text = stringResource(if (isSuggestion) R.string.progression_option_suggested else option.optionSubRes()),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(scheme),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painter = painterResource(RepFlowIcons.caretRight),
            contentDescription = null,
            tint = scheme.onSurface.copy(alpha = CARET_ALPHA),
            modifier = Modifier.size(CaretSize),
        )
    }
}

/**
 * `6a`'s overridden state: who decided what, and when - then the reasons
 * again, since every reason the policy used stays on screen whatever was
 * chosen. A choice back to the suggestion (recorded after an earlier
 * override) reads as going with it.
 */
@Composable
private fun RecordedChoiceBody(
    content: RecommendationContent.Loaded,
    choice: RecommendationChoice,
    onReadinessClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val chosenDate = choice.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", locale))
    RepFlowCard(
        modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapMd),
        contentPadding = PaddingValues(RepFlowSpacing.cardPaddingMax),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
            Icon(
                painter = painterResource(RepFlowIcons.userCircle),
                contentDescription = null,
                tint = scheme.onSurface.copy(alpha = BODY_TEXT_ALPHA),
                modifier = Modifier.size(OutcomeIconSize),
            )
            Text(
                text =
                    stringResource(
                        if (content.isOverridden) R.string.progression_overridden_title else R.string.progression_confirmed_title,
                    ),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.progression_settled_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = scheme.onSurface.copy(alpha = BODY_TEXT_ALPHA),
            modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(scheme.onSurface.copy(alpha = RECORD_BOX_FILL_ALPHA), RecordBoxShape)
                    .padding(horizontal = RepFlowSpacing.gapLg, vertical = 11.dp),
        ) {
            val recordStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
            Text(
                text = stringResource(R.string.progression_record_suggested, stringResource(content.suggested.labelRes())),
                style = recordStyle,
                color = repFlowSecondaryTextColor(scheme),
            )
            Text(
                text = stringResource(R.string.progression_record_chosen, stringResource(choice.result.labelRes())),
                style = recordStyle,
                color = repFlowAccentOutlineColors(scheme).label,
            )
            Text(
                text = stringResource(R.string.progression_record_policy, content.policyVersion, chosenDate),
                style = recordStyle,
                color = repFlowSecondaryTextColor(scheme),
            )
        }
    }
    ReasonsSection(content = content, onReadinessClick = onReadinessClick)
}

/**
 * The pinned bar. Suggestion state: go with it (writes nothing - the
 * recommendation already stands), `Pick another load`, and for an increase or a
 * reduction `Keep the same load` (`6a`'s `Keep 80 kg`). `Not enough data yet`
 * has nothing to go with, so its primary is `Done`. Recorded-choice state:
 * `Change my mind` and `Done`.
 */
@Composable
private fun RecommendationActions(
    content: RecommendationContent.Loaded,
    saving: Boolean,
    onDone: () -> Unit,
    onChooseAnother: () -> Unit,
    onKeepSameLoad: () -> Unit,
) {
    if (content.choice != null) {
        RepFlowBottomActionBar {
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd), verticalAlignment = Alignment.CenterVertically) {
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.progression_change_mind),
                    onClick = onChooseAnother,
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = SecondaryActionMinHeight),
                )
                RepFlowPrimaryButton(
                    text = stringResource(R.string.progression_done),
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val primary =
        if (content.suggested == ProgressionResultUi.WAIT_FOR_MORE_DATA) R.string.progression_done else R.string.progression_go_with_it
    val offersKeep = content.suggested == ProgressionResultUi.INCREASE_LOAD || content.suggested == ProgressionResultUi.REDUCE_LOAD
    RepFlowBottomActionBar {
        RepFlowPrimaryButton(text = stringResource(primary), onClick = onDone, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.progression_pick_another),
                onClick = onChooseAnother,
                enabled = !saving,
                modifier = Modifier.weight(1f).heightIn(min = SecondaryActionMinHeight),
            )
            if (offersKeep) {
                TextButton(
                    onClick = onKeepSameLoad,
                    enabled = !saving,
                    modifier = Modifier.weight(1f).heightIn(min = SecondaryActionMinHeight),
                ) {
                    Text(
                        text = stringResource(R.string.progression_keep_same),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    )
                }
            }
        }
    }
}
