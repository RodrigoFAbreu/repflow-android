package com.repflow.app.presentation.exercise.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration

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

/**
 * Whether the tap in progress landed on a text field. The form's tap handler
 * ([clearFocusOnTapOutsideTextFields]) runs before its children see the same tap, so
 * each text field ([keepsFocusOnTap]) marks it here and the handler reads the mark when
 * the pointer lifts.
 */
internal class TapFocusState {
    var onTextField = false
}

internal val LocalTapFocusState: ProvidableCompositionLocal<TapFocusState?> = compositionLocalOf { null }

/**
 * A tap that is not a drag and does not land on a text field clears the focus, whatever
 * else it lands on (blank space, a chip, a stepper, a button): functional review R3-F-2,
 * so leaving the name field by touch counts as touching it. Observes at the initial pass
 * and consumes nothing, so the tapped control still gets its click.
 */
@Composable
internal fun Modifier.clearFocusOnTapOutsideTextFields(state: TapFocusState): Modifier {
    val focusManager = LocalFocusManager.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    return pointerInput(state, touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.onTextField = false
            var dragged = false
            var finished = false
            while (!finished) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                finished = change == null || !change.pressed
                if (change != null) {
                    if ((change.position - down.position).getDistance() > touchSlop) dragged = true
                    if (change.changedToUp() && !dragged && !state.onTextField) focusManager.clearFocus()
                }
            }
        }
    }
}

/** Marks taps on this text field, so [clearFocusOnTapOutsideTextFields] leaves its focus alone. */
@Composable
internal fun Modifier.keepsFocusOnTap(): Modifier {
    val state = LocalTapFocusState.current ?: return this
    return pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.onTextField = true
        }
    }
}
