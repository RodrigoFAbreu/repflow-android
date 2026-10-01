package com.repflow.app.presentation.navigation

import androidx.navigation.NavController

/**
 * Leaves workout mode for Home (remediation-1 CP7): the leave sheet's `Leave
 * it running and go Home`, the hand-back once a session has ended, a finish
 * sheet raised from Home and then dismissed (CP9, `D16`), and the done
 * screen's `Back to Home`.
 *
 * Explicit stack removal, the same idiom the bottom bar's top-level switches
 * use: everything above `HOME` is popped and the existing `HOME` entry is
 * reused, so the workout entry is gone rather than left under a second Home,
 * and Back from Home cannot reopen it. It runs no use case - a session left
 * running stays active in Room, and Home's resume card finds it there.
 */
fun NavController.leaveWorkoutForHome() {
    navigate(RepFlowDestinations.HOME) {
        popUpTo(RepFlowDestinations.HOME)
        launchSingleTop = true
    }
}

/**
 * Opens the done screen for a session the finish sheet just completed
 * (remediation-1 CP9). The workout entry is removed on the way, as
 * [leaveWorkoutForHome] removes it, so Back from the done screen - and from
 * Home after it - never returns to a board whose session has ended.
 */
fun NavController.openWorkoutDone(sessionId: String) {
    navigate(RepFlowDestinations.workoutDoneRoute(sessionId)) {
        popUpTo(RepFlowDestinations.HOME)
        launchSingleTop = true
    }
}
