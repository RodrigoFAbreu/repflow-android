package com.repflow.app.presentation.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * What `MainActivity` wraps the whole app in, so a restore blocks every route: while [active], the
 * Restoring screen covers [content] and nothing underneath can be pressed, by any way in. A set that
 * can't be started can't wait behind the restore, so it can't be lost or change the wrong set.
 *
 * Each way in has its own guard. [RestoringOverlay] takes touch and Back. The key swallowing here stops
 * a hardware keyboard, and the cleared semantics stop a screen reader, which reads the tree and does
 * nothing to a keyboard. [content] stays composed throughout, so the screen below is exactly as it was
 * when the overlay lifts.
 */
@Composable
fun RestoringOverlayHost(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().onPreviewKeyEvent { active }) {
        Box(
            modifier = Modifier.fillMaxSize().then(if (active) Modifier.clearAndSetSemantics {} else Modifier),
            propagateMinConstraints = true,
        ) {
            content()
        }
        if (active) RestoringOverlay()
    }
}

/** `40g`'s `Restoring…` card, centred on the dialog scrim over the whole app. */
@Composable
private fun RestoringOverlay() {
    SwallowBack()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RepFlowColor.restoringScrim)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }.safeDrawingPadding()
                .padding(RepFlowSpacing.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        RepFlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).clearAndSetSemantics {},
                    strokeWidth = 3.dp,
                    trackColor = RepFlowColor.hairline,
                )
                Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        text = stringResource(R.string.backup_restoring_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = stringResource(R.string.backup_restoring_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    )
                }
            }
        }
    }
}

/**
 * Takes Back for as long as this is composed. Every `BackHandler` in the app, `NavHost`'s included, registers
 * at the dispatcher's default priority, and the dispatcher offers Back to an enabled handler at the overlay
 * priority before any of those, whenever they registered. So the order doesn't matter: after a recreation
 * `NavHost`'s handler registers during measure, after this one, and there is no moment when it is on top.
 */
@Composable
private fun SwallowBack() {
    val dispatcher =
        checkNotNull(LocalNavigationEventDispatcherOwner.current) {
            "RestoringOverlay needs a NavigationEventDispatcherOwner, which any ComponentActivity provides"
        }.navigationEventDispatcher
    DisposableEffect(dispatcher) {
        val handler =
            object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = true) {
                // The default throws.
                override fun onBackCompleted() = Unit
            }
        dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        onDispose { handler.remove() }
    }
}
