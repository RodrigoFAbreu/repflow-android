// Named after the composable it holds, the Compose convention; its one
// top-level object is that composable's `...Defaults`.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/*
 * The screen frame every converted screen is built in, so no screen re-derives
 * its own title treatment, padding or bottom bar (the root cause F1 named).
 *
 * Two title treatments, chosen by whether the screen has somewhere to go back
 * to - exactly the split `6b`'s "bottom nav on the four top-level
 * destinations only" draws:
 *
 * - **Top-level** (`onBack == null`): the 25/500 title, tracking -.015em,
 *   under padding 18/16/0 (`RepFlow.dc.html:349-350`).
 * - **Sub-screen**: a 44x44 `arrow-left` at 80%, then a 17/500 title, under
 *   padding 10/8/6/4 (`:47-50`).
 *
 * Content gets the design's 16 screen padding at the sides, already folded
 * into the [PaddingValues] it is handed, and [bottomBar] is where
 * [RepFlowBottomActionBar] pins the one primary action.
 *
 * Window insets: none are applied here. The app's one outer `Scaffold`
 * (`RepFlowNavHost`) already pads every destination by the system bars, so a
 * second inset here would double the status-bar gap.
 */

object RepFlowScreenScaffoldDefaults {
    val topLevelTitleTopPadding = 18.dp
    val topLevelTitleLetterSpacing = (-0.015).em

    val subScreenTopPadding = 10.dp
    val subScreenEndPadding = 8.dp
    val subScreenBottomPadding = 6.dp
    val subScreenStartPadding = 4.dp
    val subScreenGap = 6.dp
    val subScreenTitleFontSize = 17.sp
    val subScreenTitleLineHeight = 24.sp

    /** The back button's tap target, the design's own 44. */
    val backButtonSize = 44.dp
    val backIconSize = 21.dp
    val backIconAlpha = 0.8f
}

/**
 * @param onBack null for a top-level destination; otherwise the sub-screen
 *   bar's back arrow calls it.
 * @param actions trailing affordances on the title line (Home's settings gear,
 *   Recovery's `History`).
 * @param content receives padding that already includes the 16dp sides and
 *   clears [bottomBar].
 */
@Composable
fun RepFlowScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = stringResource(R.string.repflow_back_content_description),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (onBack == null) {
                TopLevelTitle(title = title, actions = actions)
            } else {
                SubScreenTopBar(
                    title = title,
                    onBack = onBack,
                    backContentDescription = backContentDescription,
                    actions = actions,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        content(
            PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + RepFlowSpacing.screenPadding,
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection) + RepFlowSpacing.screenPadding,
                bottom = innerPadding.calculateBottomPadding(),
            ),
        )
    }
}

@Composable
private fun TopLevelTitle(
    title: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = RepFlowSpacing.screenPadding,
                    end = RepFlowSpacing.screenPadding,
                    top = RepFlowScreenScaffoldDefaults.topLevelTitleTopPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = RepFlowScreenScaffoldDefaults.topLevelTitleLetterSpacing,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

@Composable
private fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = RepFlowScreenScaffoldDefaults.subScreenStartPadding,
                    end = RepFlowScreenScaffoldDefaults.subScreenEndPadding,
                    top = RepFlowScreenScaffoldDefaults.subScreenTopPadding,
                    bottom = RepFlowScreenScaffoldDefaults.subScreenBottomPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowScreenScaffoldDefaults.subScreenGap),
    ) {
        Box(
            modifier =
                Modifier
                    .size(RepFlowScreenScaffoldDefaults.backButtonSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.arrowLeft),
                contentDescription = backContentDescription,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = RepFlowScreenScaffoldDefaults.backIconAlpha),
                modifier = Modifier.size(RepFlowScreenScaffoldDefaults.backIconSize),
            )
        }
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = RepFlowScreenScaffoldDefaults.subScreenTitleFontSize,
                    lineHeight = RepFlowScreenScaffoldDefaults.subScreenTitleLineHeight,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
