package com.repflow.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

// Placeholder - replaced wholesale by remediation-1 CP5 (Home screen).

/**
 * The `HOME` start destination until CP5 builds the design's Home.
 *
 * It carries exactly the three inward paths the four-tab bar would otherwise
 * strand (remediation-1 CP2 item 4), and nothing else:
 *
 * - **start / resume** ([onWorkoutClick]) - today's Workout tab entry point,
 *   carried over: the workout route itself still decides between starting a
 *   session and resuming the active one. CP5's start and resume cards absorb it;
 * - **recovery `Log ›`** ([onRecoveryClick]), wearing `moon-stars`. CP5's
 *   recovery card absorbs it;
 * - **`Settings ›`** ([onSettingsClick]) - the only inward path to Settings,
 *   and through it to the exercise library and to backup. CP5's header gear
 *   absorbs it.
 */
@Composable
fun HomePlaceholder(
    onWorkoutClick: () -> Unit,
    onRecoveryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsDescription = stringResource(R.string.home_placeholder_settings_content_description)
    val recoveryDescription = stringResource(R.string.home_placeholder_recovery_log_content_description)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nav_home_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.home_placeholder_settings),
                onClick = onSettingsClick,
                modifier = Modifier.semantics { contentDescription = settingsDescription },
            )
        }
        RepFlowPrimaryButton(
            text = stringResource(R.string.home_placeholder_start),
            onClick = onWorkoutClick,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = RepFlowIcons.barbell,
        )
        RepFlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(RepFlowIcons.moonStars),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(RepFlowSpacing.gapMd))
                Text(
                    text = stringResource(R.string.home_placeholder_recovery_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.home_placeholder_recovery_log),
                    onClick = onRecoveryClick,
                    modifier = Modifier.semantics { contentDescription = recoveryDescription },
                )
            }
        }
    }
}
