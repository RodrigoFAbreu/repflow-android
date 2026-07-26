package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.RestDuration

/**
 * A single exercise configured inside a [TrainingPlanVersion], in order.
 *
 * [order] is validated for the whole containing list by
 * [TrainingPlanVersion.create] (uniqueness and contiguity are a cross-item
 * invariant, not one this type can check by itself), not here.
 *
 * Reuses [ExerciseId] and [RestDuration] directly from
 * `domain.exercise` - reuse between packages within the domain layer is
 * fine; only crossing into application/data/infrastructure is not.
 */
data class PlannedExercise(
    val id: PlannedExerciseId,
    val exerciseId: ExerciseId,
    val order: Int,
    val targetSets: TargetSets,
    val target: PlannedExerciseTarget,
    val restDuration: RestDuration?,
    val isOptional: Boolean,
)
