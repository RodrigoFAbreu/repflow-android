package com.repflow.app.presentation.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * The `label 11/500` uppercase section header (`6b`; first drawn at
 * `RepFlow.dc.html:53`) used throughout Settings, Progress and the workout
 * flow. Uppercased here, so callers pass the string in sentence case and a
 * screen reader is handed the same words a sighted user reads.
 *
 * Colour is the secondary-text tier, not the design's 45%: see
 * [com.repflow.app.presentation.designsystem.RepFlowColor]'s text-tier note
 * for why the 45% step cannot clear 4.5:1 on any ground it sits on.
 */
@Composable
fun RepFlowSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        modifier = modifier.semantics { heading() },
    )
}
