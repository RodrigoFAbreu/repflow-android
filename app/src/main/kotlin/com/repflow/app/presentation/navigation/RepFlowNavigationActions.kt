package com.repflow.app.presentation.navigation

import androidx.navigation.NavController

/**
 * Leaves workout mode for Home (remediation-1 CP7): the leave sheet's `Leave
 * it running and go Home`, and the hand-back once a session has ended.
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
