package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExerciseId

/**
 * The occurrence of a planned or manually added exercise inside a
 * [WorkoutSession].
 *
 * [exerciseNameSnapshot] and [trackingType] are captured at creation time
 * rather than looked up live from [com.repflow.app.domain.exercise.Exercise],
 * so this stays historically meaningful even if the source exercise is
 * later renamed or its tracking type changes (see the domain glossary's
 * "Workout exercise" entry). [plannedExerciseId] is null for an ad hoc
 * exercise added without a training-plan version.
 */
@ConsistentCopyVisibility
data class WorkoutExercise private constructor(
    val id: WorkoutExerciseId,
    val sessionId: WorkoutSessionId,
    val exerciseId: ExerciseId,
    val order: Int,
    val exerciseNameSnapshot: String,
    val trackingType: ExerciseTrackingType,
    val plannedExerciseId: PlannedExerciseId?,
    val sets: List<WorkoutSet>,
) {
    companion object {
        @Suppress("LongParameterList")
        fun create(
            id: WorkoutExerciseId,
            sessionId: WorkoutSessionId,
            exerciseId: ExerciseId,
            order: Int,
            exerciseNameSnapshot: String,
            trackingType: ExerciseTrackingType,
            plannedExerciseId: PlannedExerciseId?,
            sets: List<WorkoutSet> = emptyList(),
        ): DomainResult<WorkoutExercise, WorkoutValidationError> {
            if (order < 0) {
                return DomainResult.Failure(WorkoutValidationError.NegativeOrder)
            }
            if (exerciseNameSnapshot.isBlank()) {
                return DomainResult.Failure(WorkoutValidationError.ExerciseNameSnapshotBlank)
            }
            val setOrders = sets.map { it.order }
            if (setOrders.toSet().size != setOrders.size) {
                return DomainResult.Failure(WorkoutValidationError.DuplicateSetOrder)
            }
            return DomainResult.Success(
                WorkoutExercise(
                    id = id,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    order = order,
                    exerciseNameSnapshot = exerciseNameSnapshot,
                    trackingType = trackingType,
                    plannedExerciseId = plannedExerciseId,
                    sets = sets.sortedBy { it.order },
                ),
            )
        }
    }

    /** The next zero-based `order` value to use for a newly recorded set. */
    fun nextSetOrder(): Int = (sets.maxOfOrNull { it.order } ?: -1) + 1

    /** Appends [set], rejecting a duplicate `order` value against the existing sets. */
    fun withRecordedSet(set: WorkoutSet): DomainResult<WorkoutExercise, WorkoutValidationError> {
        if (sets.any { it.order == set.order }) {
            return DomainResult.Failure(WorkoutValidationError.DuplicateSetOrder)
        }
        return DomainResult.Success(copy(sets = (sets + set).sortedBy { it.order }))
    }

    /** Replaces the set with [WorkoutSet.id] matching [updated]'s, leaving order untouched otherwise. */
    fun withUpdatedSet(updated: WorkoutSet): DomainResult<WorkoutExercise, WorkoutValidationError> {
        if (sets.none { it.id == updated.id }) {
            return DomainResult.Failure(WorkoutValidationError.ExerciseNotFound)
        }
        return DomainResult.Success(
            copy(sets = sets.map { if (it.id == updated.id) updated else it }.sortedBy { it.order }),
        )
    }

    /** Removes the set identified by [setId], if present. */
    fun withoutSet(setId: WorkoutSetId): WorkoutExercise = copy(sets = sets.filterNot { it.id == setId })
}
