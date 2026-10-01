// Named after the composable it holds, the Compose convention; its one
// top-level object is that composable's `...Defaults`.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing

/*
 * The design's bottom sheet - `6b`'s "sheets for choices, dialogs only for
 * destructive confirmation". Every picker, menu and chooser the remediation
 * builds is one of these; `DropdownMenu` is retired from converted screens.
 *
 * Values (`4a`'s start sheet, `RepFlow.dc.html:1407-1410`, and the keypad,
 * `:2826-2829`): fill `#232532`, top radius 16, a 32x4 grabber at 25%
 * opacity, scrim `rgba(15,17,28,.6)`.
 *
 * **Colour-role decision (plan CP3).** `ModalBottomSheet`'s default container
 * is `BottomSheetDefaults.ContainerColor` = `SurfaceContainerLow`, a role
 * neither RepFlow scheme assigns. This primitive does not read it: it passes
 * [repFlowSheetContainerColor] - `surface`, which *is* the design's `#232532`
 * in dark - explicitly, so the role stays at the Material 3 baseline with no
 * consumer, and every sheet's text is measured on `surface`, a ground the
 * role audit already carries. See ROLE_AUDIT.md's "Deliberately unassigned".
 */

object RepFlowSheetDefaults {
    /** `border-radius:16px 16px 0 0` - the top of `6b`'s 14-16 sheet range. */
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    val handleWidth = 32.dp
    val handleHeight = 4.dp
    val handleShape = RoundedCornerShape(2.dp)
    val handleTopMargin = 6.dp
    val handleBottomMargin = 12.dp

    /** `padding:... 16px` under the last row, above the system inset. */
    val bottomPadding = 16.dp
}

/** The sheet's fill: `surface`, never the unassigned `surfaceContainerLow`. */
internal fun repFlowSheetContainerColor(scheme: ColorScheme): Color = scheme.surface

/**
 * A modal bottom sheet in the design's treatment. It opens fully expanded -
 * the design's sheets are short choice lists with no half-height state.
 *
 * @param title an optional section label above the content (the start
 *   sheet's `From Upper / Lower 4x`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepFlowSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RepFlowSheetDefaults.shape,
        containerColor = repFlowSheetContainerColor(scheme),
        contentColor = scheme.onSurface,
        scrimColor = RepFlowColor.sheetScrim,
        dragHandle = { RepFlowSheetHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = RepFlowSheetDefaults.bottomPadding)) {
            if (title != null) {
                RepFlowSectionLabel(
                    text = title,
                    modifier =
                        Modifier.padding(
                            start = RepFlowSpacing.screenPadding,
                            end = RepFlowSpacing.screenPadding,
                            bottom = RepFlowSpacing.gapSm,
                        ),
                )
            }
            content()
        }
    }
}

@Composable
private fun RepFlowSheetHandle() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = RepFlowSheetDefaults.handleTopMargin,
                    bottom = RepFlowSheetDefaults.handleBottomMargin,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(RepFlowSheetDefaults.handleWidth, RepFlowSheetDefaults.handleHeight)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.sheetHandleAlpha),
                        shape = RepFlowSheetDefaults.handleShape,
                    ),
        )
    }
}
