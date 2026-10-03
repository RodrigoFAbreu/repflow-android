package com.repflow.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.LocalRepFlowExtraColors
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowCardTone
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowExtraColors
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * Home's resume card (remediation-1 CP5): `4a`'s `:738-753`.
 */

private val ResumeGlyphSize = 20.dp

/** Above this font scale the three actions stack instead of sharing a row. */
private const val STACK_ACTIONS_FONT_SCALE = 1.1f
private const val ELAPSED_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L

/** The running marker's `#b5abfc` - the accent ramp's 400 step the design draws it in. */
private const val RESUME_GLYPH_ARGB = 0xFFB5ABFC
private val ResumeGlyphColor = Color(RESUME_GLYPH_ARGB)

/**
 * `4a`'s resume card (`:738-753`): the accent-tinted card, which is a dark
 * surface in both themes ([RepFlowCardTone.Accent]), so its content is drawn
 * with the dark scheme whichever theme is in force.
 */
@Composable
internal fun ResumeCard(
    workout: HomeActiveWorkout,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
    onAbandonClick: () -> Unit,
) {
    val title = workout.planName ?: stringResource(R.string.home_untitled_workout)
    val elapsed = rememberElapsedSeconds(workout.startedAt)
    val meta =
        stringResource(
            R.string.home_resume_meta,
            elapsedLabel(elapsed),
            pluralStringResource(R.plurals.home_sets_logged, workout.setsLogged, workout.setsLogged),
        )
    RepFlowCard(
        modifier = Modifier.fillMaxWidth(),
        tone = RepFlowCardTone.Accent,
        contentPadding = CardPadding,
    ) {
        OnDarkSurface {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.recordFill),
                    contentDescription = null,
                    tint = ResumeGlyphColor,
                    modifier = Modifier.size(ResumeGlyphSize),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.home_resume_title, title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = homeMetaColor(),
                    )
                }
            }
            if (LocalDensity.current.fontScale > STACK_ACTIONS_FONT_SCALE) {
                // Large font (design 9f): labels never break mid-word, so the actions stack.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapLg),
                    verticalArrangement = Arrangement.spacedBy(CardActionsGap),
                ) {
                    RepFlowPrimaryButton(
                        text = stringResource(R.string.home_resume),
                        onClick = onResumeClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RepFlowAccentOutlineButton(
                        text = stringResource(R.string.home_finish_it),
                        onClick = onFinishClick,
                        modifier = Modifier.fillMaxWidth().heightIn(min = ActionRowMinHeight),
                    )
                    AbandonButton(onAbandonClick, Modifier.align(Alignment.End))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapLg),
                    horizontalArrangement = Arrangement.spacedBy(CardActionsGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepFlowPrimaryButton(
                        text = stringResource(R.string.home_resume),
                        onClick = onResumeClick,
                        modifier = Modifier.weight(1f),
                    )
                    RepFlowAccentOutlineButton(
                        text = stringResource(R.string.home_finish_it),
                        onClick = onFinishClick,
                        modifier = Modifier.weight(1f).heightIn(min = ActionRowMinHeight),
                    )
                    AbandonButton(onAbandonClick)
                }
            }
        }
    }
}

@Composable
private fun AbandonButton(
    onAbandonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onAbandonClick,
        modifier = modifier.heightIn(min = ActionRowMinHeight),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.trash),
            contentDescription = stringResource(R.string.home_abandon_content_description),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** Renders [content] with the dark scheme and its extra tokens - for a card that is a dark surface in both themes. */
@Composable
private fun OnDarkSurface(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RepFlowDarkColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalRepFlowExtraColors provides repFlowExtraColors(RepFlowDarkColorScheme),
            LocalContentColor provides RepFlowDarkColorScheme.onSurface,
            content = content,
        )
    }
}

/** Seconds since [startedAt], re-read from the wall clock every second - the timer is always derived, never counted. */
@Composable
private fun rememberElapsedSeconds(startedAt: Instant): Long {
    var elapsed by remember(startedAt) { mutableLongStateOf(secondsSince(startedAt)) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = secondsSince(startedAt)
            delay(ELAPSED_TICK_MILLIS)
        }
    }
    return elapsed
}

private fun secondsSince(startedAt: Instant): Long = (Instant.now().toEpochMilli() - startedAt.toEpochMilli()) / MILLIS_PER_SECOND
