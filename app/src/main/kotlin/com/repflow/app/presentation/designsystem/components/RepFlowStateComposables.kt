package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Drawn as `1d`'s empty treatment (remediation-1 CP16): an optional 26dp
 * glyph at 35% over a 13.5 line.
 *
 * @param message already resolved by the caller, since which of several
 *   reasons applies is the screen's own business.
 * @param icon the surface's own glyph, decorative (the message is the label).
 */
@Composable
fun RepFlowEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(RepFlowSpacing.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = EMPTY_GLYPH_ALPHA),
                    modifier = Modifier.size(EmptyGlyphSize),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = EmptyTextSize),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** `1d`'s empty state: a 26 glyph at 35% over a 13.5 line. */
private const val EMPTY_GLYPH_ALPHA = 0.35f
private val EmptyGlyphSize = 26.dp
private val EmptyTextSize = 13.5.sp

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
