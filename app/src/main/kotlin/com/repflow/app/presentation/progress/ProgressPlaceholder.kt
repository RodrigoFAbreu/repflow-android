package com.repflow.app.presentation.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

// Placeholder - replaced by remediation-1 CP15 (the Progress tab).

/**
 * The `PROGRESS` tab until CP15 builds it: the top-level title over the
 * design's empty-state treatment (artboard `1d`: a 26dp glyph at 35%, one
 * 13.5 line). Nothing is relocated into Progress, so it carries no inward
 * path for anything else.
 */
@Composable
fun ProgressPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_progress_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.chartLineUp),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.35f),
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(R.string.progress_placeholder_empty),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
