package com.repflow.app.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Spacing comes from RepFlow's own literal layout rules ("screen padding 16,
 * card padding 14-18, gaps 6/8/10/12"), not the generic Nocturne stylesheet's
 * fractional 2.8/5.6/8.4/11.2/16.8/22.4px scale, which no RepFlow screen uses.
 */

@Immutable
data class RepFlowSpacingScale(
    val gapXs: Dp = 6.dp,
    val gapSm: Dp = 8.dp,
    val gapMd: Dp = 10.dp,
    val gapLg: Dp = 12.dp,
    val cardPaddingMin: Dp = 14.dp,
    val screenPadding: Dp = 16.dp,
    val cardPaddingMax: Dp = 18.dp,
)

val RepFlowDefaultSpacingScale = RepFlowSpacingScale()

val LocalRepFlowSpacingScale = staticCompositionLocalOf { RepFlowDefaultSpacingScale }

/**
 * Spacing tokens, reached the same way as [RepFlowColor]'s per-theme extras so
 * there is one rule for both: `MaterialTheme` for anything Material 3 has a
 * slot for, this object for anything it does not.
 */
object RepFlowSpacing {
    val gapXs: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.gapXs

    val gapSm: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.gapSm

    val gapMd: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.gapMd

    val gapLg: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.gapLg

    val cardPaddingMin: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.cardPaddingMin

    val screenPadding: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.screenPadding

    val cardPaddingMax: Dp
        @Composable @ReadOnlyComposable
        get() = LocalRepFlowSpacingScale.current.cardPaddingMax
}
