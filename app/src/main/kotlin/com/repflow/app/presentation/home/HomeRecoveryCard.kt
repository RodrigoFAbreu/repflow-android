package com.repflow.app.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.recovery.ReadinessFactor
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/*
 * Home's recovery card (remediation-1 CP5): `4a`'s `:763-783`, and `1d`'s
 * empty state (`:3002-3008`).
 */

private val LogButtonMinHeight = 44.dp
private val ChipShape = RoundedCornerShape(999.dp)
private val ScoreTextStyle = RepFlowNumericTextStyle.copy(fontSize = 38.sp, lineHeight = 38.sp, letterSpacing = (-0.03).em)
private const val CHIP_FILL_ALPHA = 0.07f
private const val CHIP_TEXT_ALPHA = 0.7f
private const val DRIVER_TEXT_ALPHA = 0.78f
private const val DIVIDER_ALPHA = 0.12f

/**
 * The recovery card (`:763-783`): `Recovery today` and `Log ›`; with today's
 * check-in, the score (opening the readiness sheet), its bar, the driver
 * sentence and three chips back into the entry screen; without one, `1d`'s
 * empty state (`:3002-3008`).
 */
@Composable
internal fun RecoveryCard(
    readiness: HomeReadiness,
    onLogClick: () -> Unit,
    onScoreClick: () -> Unit,
) {
    RepFlowCard(modifier = Modifier.fillMaxWidth(), contentPadding = CardPadding) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RepFlowSectionLabel(text = stringResource(R.string.home_recovery_label), modifier = Modifier.weight(1f))
            LogButton(onLogClick)
        }
        when (readiness) {
            is HomeReadiness.Logged -> {
                LoggedReadiness(readiness.score, onScoreClick, onLogClick)
            }

            HomeReadiness.NotLogged -> {
                Spacer(Modifier.size(RepFlowSpacing.gapLg))
                EmptyStateBody(glyph = RepFlowIcons.moonStars, message = stringResource(R.string.home_recovery_empty_message))
                RepFlowAccentOutlineButton(
                    text = stringResource(R.string.home_recovery_empty_action),
                    onClick = onLogClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HomeReadiness.Loading, HomeReadiness.Unavailable -> {
                Unit
            }
        }
    }
}

@Composable
private fun LogButton(onClick: () -> Unit) {
    val label = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label
    val description = stringResource(R.string.home_recovery_log_content_description)
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = LogButtonMinHeight).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = RepFlowSpacing.gapMd),
    ) {
        Text(
            text = stringResource(R.string.home_recovery_log),
            style = MaterialTheme.typography.bodySmall,
            color = label,
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            painter = painterResource(RepFlowIcons.caretRight),
            contentDescription = null,
            tint = label,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun LoggedReadiness(
    score: ReadinessScore,
    onScoreClick: () -> Unit,
    onLogClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bandColor = readinessBandColor(score.band, scheme)
    val bandWord = stringResource(readinessBandLabel(score.band))
    val accent = repFlowAccentOutlineColors(scheme).label
    val description = stringResource(R.string.home_readiness_content_description, score.score, bandWord)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = RepFlowSpacing.gapSm)
                .clickable(onClick = onScoreClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        Text(text = score.score.toString(), style = ScoreTextStyle, color = bandColor)
        Column(modifier = Modifier.weight(1f).padding(bottom = 3.dp)) {
            Text(text = bandWord, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = bandColor)
            Text(
                text = stringResource(R.string.home_readiness_caption),
                style = MaterialTheme.typography.bodySmall,
                color = homeMetaColor(),
            )
        }
        Row(modifier = Modifier.padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.home_readiness_details), style = MaterialTheme.typography.bodySmall, color = accent)
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
    ScoreBar(score = score.score, bandColor = bandColor)
    Text(
        text = score.driverSentence,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
        color = scheme.onSurface.copy(alpha = DRIVER_TEXT_ALPHA),
    )
    RecoveryChips(score = score, onClick = onLogClick)
}

/** `Sleep N · Energy N · DOMS N` (`nRecChips`, `:3773`), the whole row a way back into the entry screen. */
@Composable
private fun RecoveryChips(
    score: ReadinessScore,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    fun valueOf(factor: ReadinessFactor): Int = score.factors.first { it.factor == factor }.value
    val chips =
        listOf(
            stringResource(R.string.home_recovery_chip_sleep, valueOf(ReadinessFactor.SLEEP_QUALITY)),
            stringResource(R.string.home_recovery_chip_energy, valueOf(ReadinessFactor.ENERGY)),
            stringResource(R.string.home_recovery_chip_doms, valueOf(ReadinessFactor.LEG_DOMS)),
        )
    HorizontalDivider(
        modifier = Modifier.padding(top = RepFlowSpacing.gapLg),
        color = scheme.onSurface.copy(alpha = DIVIDER_ALPHA),
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = LogButtonMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(top = RepFlowSpacing.gapLg),
        horizontalArrangement = Arrangement.spacedBy(CardActionsGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            Text(
                text = chip,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                color = scheme.onSurface.copy(alpha = CHIP_TEXT_ALPHA),
                modifier =
                    Modifier
                        .background(scheme.onSurface.copy(alpha = CHIP_FILL_ALPHA), ChipShape)
                        .padding(horizontal = RepFlowSpacing.gapMd, vertical = 5.dp),
            )
        }
    }
}
