package com.repflow.app.presentation.exercise.editor

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Calls [onFocusLost] when the field loses focus after having had it - not for
 * the initial unfocused state - so leaving a field untouched-but-empty counts as
 * touching it (functional review R2-F-1).
 */
@Composable
internal fun Modifier.notifyOnFocusLost(onFocusLost: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    val currentOnFocusLost by rememberUpdatedState(onFocusLost)
    return onFocusChanged { state ->
        if (hadFocus && !state.isFocused) currentOnFocusLost()
        hadFocus = state.isFocused
    }
}

/**
 * Scrolls this field into view whenever [show] turns true, and again whenever [trigger]
 * changes while it is true (functional review R2-F-2, R3-F-3): a refused save reports
 * against the name field, which may be scrolled off-screen when `Save` is tapped, so the
 * reason has to be brought to the user - on every refusal, not only the first. [trigger]
 * is the refusal itself, so a second refusal for the same reason still counts.
 */
@Composable
internal fun Modifier.bringIntoViewWhen(
    show: Boolean,
    trigger: Any? = null,
): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(show, trigger) { if (show) requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}
