package com.repflow.app.presentation.trainingplan.list

import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * A single list row's data. Mirrors [com.repflow.app.presentation.exercise.list.ExerciseListItem]:
 * only raw values, no formatted text - the count is a plain `Int` the
 * Composable turns into a formatted string via a string resource.
 */
data class TrainingPlanListItem(
    val id: TrainingPlanId,
    val name: String,
    val plannedExerciseCount: Int,
)
