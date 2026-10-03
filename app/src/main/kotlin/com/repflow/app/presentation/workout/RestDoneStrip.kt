package com.repflow.app.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * The strip once the rest has ended (design `9c`/`8e`, R3): a neutral edge, a
 * check, `Rest done` over `Next set is ready`, and the 44dp dismiss `X`. No
 * nudge or skip buttons - there is no rest left to change - and no fill.
 * Logging the next set clears it, as before.
 */
@Composable
internal fun RestDoneStrip(
    onDismiss: () -> Unit,
    dismissDescription: String,
) {
    RepFlowCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = RestStripSideMargin, end = RestStripSideMargin, bottom = RestStripBottomMargin),
        contentPadding = PaddingValues(start = RestStripHorizontalPadding, end = RestDoneEndPadding),
    ) {
        Row(
            modifier = Modifier.heightIn(min = RestDoneMinHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.checkCircle),
                contentDescription = null,
                tint = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
                modifier = Modifier.size(RestDoneCheckSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.workout_rest_done),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = RestDoneTitleFontSize),
                )
                Text(
                    text = stringResource(R.string.workout_rest_ready),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = RestStripSubFontSize),
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = dismissDescription },
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.x),
                    contentDescription = null,
                    modifier = Modifier.size(RestTimerIconSize),
                )
            }
        }
    }
}

/** `9c` done strip: `min-height:56px`, `padding:0 4px 0 12px`, a 20px check, 15/500 title. */
private val RestDoneMinHeight = 56.dp
private val RestDoneEndPadding = 4.dp
private val RestDoneCheckSize = 20.dp
private val RestDoneTitleFontSize = 15.sp
