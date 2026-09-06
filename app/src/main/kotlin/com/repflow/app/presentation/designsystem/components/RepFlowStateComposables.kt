package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.repflow.app.presentation.designsystem.RepFlowSpacing

/*
 * The three screen-level states every list surface in this app already
 * hand-rolls for itself: loading, empty, and observation-failed.
 *
 * Consolidating them is a de-duplication, not a new capability - the bodies
 * below are the existing per-screen ones, which agree with each other. The
 * failure state keeps its retry callback: it is the one state where the user
 * has no other way forward.
 *
 * The names carry the RepFlow prefix because Material 3 now ships its own
 * `LoadingIndicator` composable, and an unprefixed one here would resolve by
 * import order at each call site.
 */

/** Centred progress indicator, filling whatever box it is given. */
@Composable
fun RepFlowLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Centred explanation of why a surface has nothing to show.
 *
 * @param message already resolved by the caller, since which of several
 *   reasons applies is the screen's own business.
 */
@Composable
fun RepFlowEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(RepFlowSpacing.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The observation-failed state: what went wrong, and the one way forward.
 *
 * Retry is an accent-outline button, which is how the design renders exactly
 * this affordance ("Retry", "Empty workout") on its own light-theme screen.
 */
@Composable
fun RepFlowFailureState(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(RepFlowSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        RepFlowAccentOutlineButton(text = retryLabel, onClick = onRetry)
    }
}
