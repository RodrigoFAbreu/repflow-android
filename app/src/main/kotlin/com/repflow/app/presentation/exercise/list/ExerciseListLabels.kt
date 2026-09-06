package com.repflow.app.presentation.exercise.list

import androidx.annotation.StringRes
import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter

/*
 * The exercise list's plain-data mapping: which words the screen puts on a
 * filter pill and on an empty list. Separate from `ExerciseListScreen.kt`
 * because none of it is composable - which is also what lets a plain-JVM
 * test reach it (`ExerciseListScreenWiringTest`), where the index-based
 * filter row would otherwise only be checkable on a device.
 */

/**
 * The filter row's order, left to right. `RepFlowPillPicker` is index-based,
 * so this list is what keeps the labels, the selected index and the emitted
 * filter in step with one another.
 */
internal val ExerciseListFilterOrder =
    listOf(ExerciseStatusFilter.ACTIVE, ExerciseStatusFilter.ARCHIVED)

/** The label each filter carries in the row above the list. */
@StringRes
internal fun exerciseListFilterLabelRes(filter: ExerciseStatusFilter): Int =
    when (filter) {
        ExerciseStatusFilter.ACTIVE -> R.string.exercise_list_filter_active
        ExerciseStatusFilter.ARCHIVED -> R.string.exercise_list_filter_archived
    }

/** Why the list is empty, in the user's words. */
@StringRes
internal fun exerciseListEmptyMessageRes(reason: ExerciseListEmptyReason): Int =
    when (reason) {
        ExerciseListEmptyReason.NO_EXERCISES -> R.string.exercise_list_empty_no_exercises
        ExerciseListEmptyReason.NO_SEARCH_RESULTS -> R.string.exercise_list_empty_no_search_results
        ExerciseListEmptyReason.NO_ARCHIVED -> R.string.exercise_list_empty_no_archived
    }
