package com.repflow.app.presentation.exercise.editor

import androidx.compose.runtime.Composable
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
