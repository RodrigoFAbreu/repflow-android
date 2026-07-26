package com.repflow.app.data.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.mapFailure
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSessionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetEntity
import java.time.Instant

/**
 * Pure Kotlin, directly unit-testable conversion between the domain workout
 * aggregate and its persisted Room row shapes. The only place either
 * direction of this mapping happens, mirroring
 * [com.repflow.app.data.trainingplan.TrainingPlanEntityMapper].
 */
object WorkoutEntityMapper {
    fun toSessionEntity(session: WorkoutSession): WorkoutSessionEntity =
        WorkoutSessionEntity(
            id = session.id.value,
            trainingPlanVersionId = session.trainingPlanVersionId?.value,
            status = session.status.name,
            startedAt = session.startedAt.toEpochMilli(),
            endedAt = session.endedAt?.toEpochMilli(),
        )

    fun toExerciseEntities(session: WorkoutSession): List<WorkoutExerciseEntity> =
        session.exercises.map { exercise ->
            WorkoutExerciseEntity(
                id = exercise.id.value,
                sessionId = session.id.value,
                exerciseId = exercise.exerciseId.value,
                sortOrder = exercise.order,
                exerciseNameSnapshot = exercise.exerciseNameSnapshot,
                trackingType = exercise.trackingType.name,
                plannedExerciseId = exercise.plannedExerciseId?.value,
            )
        }

    fun toSetEntities(session: WorkoutSession): List<WorkoutSetEntity> =
        session.exercises.flatMap { exercise ->
            exercise.sets.map { set ->
                WorkoutSetEntity(
                    id = set.id.value,
                    workoutExerciseId = exercise.id.value,
                    sortOrder = set.order,
                    load = set.load,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    rpe = set.rpe,
                    isWarmup = set.isWarmup,
                    createdAt = set.createdAt.toEpochMilli(),
                    updatedAt = set.updatedAt.toEpochMilli(),
                )
            }
        }

    /**
     * Reassembles a full [WorkoutSession] aggregate from its session row,
     * this session's exercise rows, and every one of those exercises' set
     * rows (keyed by [WorkoutExerciseEntity.id], mirroring how
     * [com.repflow.app.data.trainingplan.TrainingPlanEntityMapper] takes
     * planned-exercise rows alongside a version row).
     */
    @Suppress("ReturnCount")
    fun toDomain(
        session: WorkoutSessionEntity,
        exerciseRows: List<WorkoutExerciseEntity>,
        setRowsByExerciseId: Map<String, List<WorkoutSetEntity>>,
    ): DomainResult<WorkoutSession, WorkoutMappingError> {
        val status =
            WorkoutSessionStatus.entries.firstOrNull { it.name == session.status }
                ?: return DomainResult.Failure(WorkoutMappingError.UnknownStatus(session.id, session.status))

        val exercises = mutableListOf<WorkoutExercise>()
        for (row in exerciseRows) {
            when (val result = toDomain(row, setRowsByExerciseId[row.id].orEmpty())) {
                is DomainResult.Success -> exercises += result.value
                is DomainResult.Failure -> return DomainResult.Failure(result.error)
            }
        }

        return WorkoutSession
            .reconstruct(
                id = WorkoutSessionId(session.id),
                trainingPlanVersionId = session.trainingPlanVersionId?.let(::TrainingPlanVersionId),
                status = status,
                startedAt = Instant.ofEpochMilli(session.startedAt),
                endedAt = session.endedAt?.let(Instant::ofEpochMilli),
                exercises = exercises,
            ).mapFailure { error -> WorkoutMappingError.InvalidFields(session.id, listOf(error)) }
    }

    private fun toDomain(
        row: WorkoutExerciseEntity,
        setRows: List<WorkoutSetEntity>,
    ): DomainResult<WorkoutExercise, WorkoutMappingError> {
        val trackingType =
            ExerciseTrackingType.entries.firstOrNull { it.name == row.trackingType }
                ?: return DomainResult.Failure(WorkoutMappingError.UnknownStatus(row.id, row.trackingType))

        val sets = mutableListOf<WorkoutSet>()
        for (setRow in setRows) {
            when (val result = toDomain(setRow, trackingType)) {
                is DomainResult.Success -> sets += result.value
                is DomainResult.Failure -> return DomainResult.Failure(result.error)
            }
        }

        return WorkoutExercise
            .create(
                id = WorkoutExerciseId(row.id),
                sessionId = WorkoutSessionId(row.sessionId),
                exerciseId = ExerciseId(row.exerciseId),
                order = row.sortOrder,
                exerciseNameSnapshot = row.exerciseNameSnapshot,
                trackingType = trackingType,
                plannedExerciseId = row.plannedExerciseId?.let(::PlannedExerciseId),
                sets = sets,
            ).mapFailure { error -> WorkoutMappingError.InvalidFields(row.id, listOf(error)) }
    }

    private fun toDomain(
        row: WorkoutSetEntity,
        trackingType: ExerciseTrackingType,
    ): DomainResult<WorkoutSet, WorkoutMappingError> =
        WorkoutSet
            .create(
                id = WorkoutSetId(row.id),
                order = row.sortOrder,
                trackingType = trackingType,
                load = row.load,
                reps = row.reps,
                durationSeconds = row.durationSeconds,
                rpe = row.rpe,
                isWarmup = row.isWarmup,
                createdAt = Instant.ofEpochMilli(row.createdAt),
                updatedAt = Instant.ofEpochMilli(row.updatedAt),
            ).mapFailure { error -> WorkoutMappingError.InvalidFields(row.id, listOf(error)) }
}
