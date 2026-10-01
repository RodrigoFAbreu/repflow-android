package com.repflow.app.presentation.trainingplan.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val archivedDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * One `5a` plan card (`RepFlow.dc.html:364-386`): radius 14, padding 16, the
 * surface fill inside a hairline ring. The name at 17/500, `N exercises · vN`
 * (no days, `D4`), an archived plan's `Archived <date>`, and `Archive` /
 * `Restore` on the card face; then the action row - `Start workout` (the
 * accent primary, `play`) on an active plan, and `Open` (`5a`'s secondary).
 * An archived plan has no start: Home only ever starts an active plan.
 *
 * The card itself opens the plan, as the old row did, so a tap anywhere but an
 * action still reaches the editor.
 */
@Composable
internal fun PlanCard(
    item: TrainingPlanListItem,
    isArchivedFilter: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(scheme.surface)
                .border(1.dp, RepFlowColor.hairline, CardShape)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(CardPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = NameFontSize),
                    color = scheme.onSurface,
                )
                Text(
                    text =
                        stringResource(
                            R.string.training_plan_list_card_meta,
                            pluralStringResource(
                                R.plurals.training_plan_list_card_exercise_count,
                                item.plannedExerciseCount,
                                item.plannedExerciseCount,
                            ),
                            item.versionNumber,
                        ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = repFlowSecondaryTextColor(scheme),
                    modifier = Modifier.padding(top = MetaTopGap),
                )
                item.archivedAt?.let { archivedAt ->
                    val date = archivedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(archivedDateFormatter)
                    Text(
                        text = stringResource(R.string.training_plan_list_card_archived_on, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = repFlowSecondaryTextColor(scheme),
                    )
                }
            }
            CardOutlineButton(
                text =
                    stringResource(
                        if (isArchivedFilter) R.string.training_plan_list_card_restore else R.string.training_plan_list_card_archive,
                    ),
                onClick = if (isArchivedFilter) onRestore else onArchive,
                fontSize = ArchiveFontSize,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ActionRowTopGap),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            if (!isArchivedFilter) {
                StartButton(onClick = onStart, modifier = Modifier.weight(1f))
            }
            CardOutlineButton(
                text = stringResource(R.string.training_plan_list_card_open),
                onClick = onOpen,
                fontSize = ActionFontSize,
                minHeight = ActionMinHeight,
                modifier = if (isArchivedFilter) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

/** `5a`'s start (`:380`): 46 tall, radius 9, the accent fill, a filled `play` and the label at 14/600. */
@Composable
private fun StartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ActionMinHeight),
        shape = ActionShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = ActionHorizontalPadding),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.playFill),
            contentDescription = null,
            modifier = Modifier.size(PlayGlyphSize),
        )
        Spacer(Modifier.width(PlayGlyphGap))
        Text(
            text = stringResource(R.string.training_plan_list_card_start),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = ActionFontSize, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * `5a`'s neutral card buttons - `Archive` / `Restore` (`:375`, drawn 40 tall)
 * and `Open` (`:386`, 46): a hairline ring and the label at 70-80% of the text
 * colour. `Archive` is lifted to `6b`'s 44 floor.
 */
@Composable
private fun CardOutlineButton(
    text: String,
    onClick: () -> Unit,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    minHeight: Dp = PlanTapTargetMinHeight,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        shape = ArchiveShape,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = NEUTRAL_LABEL_ALPHA),
            ),
        border = BorderStroke(1.dp, RepFlowColor.hairline),
        contentPadding = PaddingValues(horizontal = ActionHorizontalPadding),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize))
    }
}

/**
 * `5a`'s empty card (`:358-363`): a hairline ring at radius 14, a 26dp
 * `list-checks` and the reason at 13.5, centred.
 */
@Composable
internal fun PlanEmptyCard(reason: TrainingPlanListEmptyReason) {
    val scheme = MaterialTheme.colorScheme
    val textRes =
        when (reason) {
            TrainingPlanListEmptyReason.NO_PLANS -> R.string.training_plan_list_empty
            TrainingPlanListEmptyReason.NO_ARCHIVED -> R.string.training_plan_list_empty_no_archived
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, RepFlowColor.hairline, CardShape)
                .padding(horizontal = EmptyHorizontalPadding, vertical = EmptyVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.listChecks),
            contentDescription = null,
            tint = scheme.onSurface.copy(alpha = EMPTY_GLYPH_ALPHA),
            modifier = Modifier.size(EmptyGlyphSize),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EmptyFontSize),
            color = repFlowSecondaryTextColor(scheme),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * `5a`'s info line (`:388-391`), its second sentence only: "Archiving never
 * touches completed workouts." The first - "Only one plan is active" - states
 * an active-plan concept RepFlow does not have (`D4`).
 */
@Composable
internal fun ArchiveFootnote() {
    val color = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(horizontalArrangement = Arrangement.spacedBy(FootnoteGap)) {
        Icon(
            painter = painterResource(RepFlowIcons.info),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = FootnoteGlyphTopGap).size(PlanGlyphSize),
        )
        Text(
            text = stringResource(R.string.training_plan_list_archive_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

private val CardShape = RoundedCornerShape(14.dp)
private val CardPadding = 16.dp
private val NameFontSize = 17.sp
private val MetaTopGap = 3.dp
private val ActionRowTopGap = 14.dp
private val ActionMinHeight = 46.dp
private val ActionShape = RoundedCornerShape(9.dp)
private val ArchiveShape = RoundedCornerShape(8.dp)
private val ActionHorizontalPadding = 14.dp
private val ActionFontSize = 14.sp
private val ArchiveFontSize = 12.5.sp
private val PlayGlyphSize = 12.dp
private val PlayGlyphGap = 7.dp
private const val NEUTRAL_LABEL_ALPHA = 0.8f
private val EmptyHorizontalPadding = 18.dp
private val EmptyVerticalPadding = 24.dp
private val EmptyGlyphSize = 26.dp
private const val EMPTY_GLYPH_ALPHA = 0.35f
private val EmptyFontSize = 13.5.sp
private val FootnoteGap = 9.dp
private val FootnoteGlyphTopGap = 1.dp
