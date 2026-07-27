package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseTrackingType
import java.time.Instant

/**
 * A recorded set performed during a workout, for a single [WorkoutExercise].
 *
 * Which of [load], [reps], [durationSeconds] are populated is dictated by
 * the exercise's [ExerciseTrackingType], validated in [create] rather than
 * encoded as separate subtypes - keeping a single shape lines up with the
 * Room row this maps onto and matches
 * [com.repflow.app.domain.trainingplan.PlannedExerciseTarget]'s reasoning
 * that not every distinction needs its own subtype.
 */
@ConsistentCopyVisibility
data class WorkoutSet private constructor(
    val id: WorkoutSetId,
    val order: Int,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pain: Int?,
    val techniqueQuality: Int?,
) {
    companion object {
        private val RPE_RANGE = 0.0..10.0
        private val SCALE_RANGE = 0..5

        @Suppress("LongParameterList")
        fun create(
            id: WorkoutSetId,
            order: Int,
            trackingType: ExerciseTrackingType,
            load: Double?,
            reps: Int?,
            durationSeconds: Int?,
            rpe: Double?,
            isWarmup: Boolean,
            createdAt: Instant,
            updatedAt: Instant,
            pain: Int? = null,
            techniqueQuality: Int? = null,
        ): DomainResult<WorkoutSet, WorkoutValidationError> {
            if (order < 0) {
                return DomainResult.Failure(WorkoutValidationError.NegativeOrder)
            }
            if (updatedAt < createdAt) {
                return DomainResult.Failure(WorkoutValidationError.UpdatedBeforeCreated)
            }
            if (rpe != null && rpe !in RPE_RANGE) {
                return DomainResult.Failure(WorkoutValidationError.RpeOutOfRange)
            }
            if (pain != null && pain !in SCALE_RANGE) {
                return DomainResult.Failure(WorkoutValidationError.PainOutOfRange)
            }
            if (techniqueQuality != null && techniqueQuality !in SCALE_RANGE) {
                return DomainResult.Failure(WorkoutValidationError.TechniqueQualityOutOfRange)
            }
            validateTrackedValues(trackingType, load, reps, durationSeconds)?.let {
                return DomainResult.Failure(it)
            }
            return DomainResult.Success(
                WorkoutSet(
                    id = id,
                    order = order,
                    load = load,
                    reps = reps,
                    durationSeconds = durationSeconds,
                    rpe = rpe,
                    isWarmup = isWarmup,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    pain = pain,
                    techniqueQuality = techniqueQuality,
                ),
            )
        }

        @Suppress("ReturnCount")
        private fun validateTrackedValues(
            trackingType: ExerciseTrackingType,
            load: Double?,
            reps: Int?,
            durationSeconds: Int?,
        ): WorkoutValidationError? {
            if (trackingType == ExerciseTrackingType.DURATION) {
                if (reps != null) return WorkoutValidationError.RepsNotApplicable
                if (load != null) return WorkoutValidationError.LoadNotApplicable
                if (durationSeconds == null || durationSeconds <= 0) return WorkoutValidationError.DurationRequired
                return null
            }
            if (durationSeconds != null) return WorkoutValidationError.DurationNotApplicable
            if (reps == null || reps <= 0) return WorkoutValidationError.RepsRequired
            if (!trackingType.supportsLoad && load != null) return WorkoutValidationError.LoadNotApplicable
            if (load != null && load < 0.0) return WorkoutValidationError.LoadOutOfRange
            return null
        }
    }
}
