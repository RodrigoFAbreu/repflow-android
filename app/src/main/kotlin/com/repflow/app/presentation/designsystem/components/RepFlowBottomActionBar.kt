// Named after the composable it holds, the Compose convention; its one
// top-level object is that composable's `...Defaults`.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.repflow.app.presentation.designsystem.RepFlowColor

/*
 * `6b`'s "one primary action per screen, pinned to a bottom bar". Drawn
 * identically wherever it appears (`RepFlow.dc.html:179-185`, `:1155-1157`):
 * fill `#1b1d2b` - the dark scheme's `surfaceContainer`, the same fill as the
 * bottom nav, which this bar replaces on the screens that carry one - a
 * `1px rgba(233,233,237,.12)` top edge, padding 12/16/16, and the primary
 * button at 56.
 *
 * System-bar insets are deliberately not applied here: the app's one outer
 * `Scaffold` (`RepFlowNavHost`) already pads its content by them, and this bar
 * lives inside that content.
 */

object RepFlowBottomActionBarDefaults {
    val topPadding = 12.dp
    val horizontalPadding = 16.dp
    val bottomPadding = 16.dp
    val gap = 8.dp
    val edgeThickness = 1.dp

    /** `6a`'s secondary row: `min-height:48px`, the accent tier's height. */
    val secondaryMinHeight = 48.dp
}

/**
 * The bar itself, for the screens whose actions are not one primary plus one
 * secondary (focus mode's `Log set` beside `Next`, the recommendation's
 * three-way choice). Content stacks 8 apart.
 */
@Composable
fun RepFlowBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider(
            thickness = RepFlowBottomActionBarDefaults.edgeThickness,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.bottomBarEdgeAlpha),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = RepFlowBottomActionBarDefaults.horizontalPadding,
                        end = RepFlowBottomActionBarDefaults.horizontalPadding,
                        top = RepFlowBottomActionBarDefaults.topPadding,
                        bottom = RepFlowBottomActionBarDefaults.bottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(RepFlowBottomActionBarDefaults.gap),
            content = content,
        )
    }
}

/**
 * The common case: one full-width 56dp primary, and optionally one quieter
 * full-width secondary under it (a neutral outline at 48).
 */
@Composable
fun RepFlowBottomActionBar(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    @DrawableRes primaryIcon: Int? = null,
    secondaryText: String? = null,
    onSecondaryClick: () -> Unit = {},
) {
    RepFlowBottomActionBar(modifier = modifier) {
        RepFlowPrimaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            leadingIcon = primaryIcon,
            modifier = Modifier.fillMaxWidth(),
        )
        if (secondaryText != null) {
            RepFlowNeutralOutlineButton(
                text = secondaryText,
                onClick = onSecondaryClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RepFlowBottomActionBarDefaults.secondaryMinHeight),
            )
        }
    }
}
