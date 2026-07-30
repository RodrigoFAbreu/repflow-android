package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Milestone 8, implementation-review finding #1: [StartWorkoutSessionFromPlan]
 * must be all-or-nothing - either the whole session with every seeded
 * exercise is persisted, or none of it is. [InMemoryWorkoutRepository.insert]
 * mirrors the real [com.repflow.app.data.workout.LocalWorkoutRepository]'s
 * shape closely enough (single map entry added atomically) to prove the
 * use case itself builds and validates the whole aggregate before ever
 * calling `insert`; [StartWorkoutSessionFromPlanAtomicityTest] proves the
 * same thing against a real Room database.
 */
class StartWorkoutSessionFromPlanTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val useCase =
        StartWorkoutSessionFromPlan(
            workoutRepository,
            GetExercise(exerciseRepository),
            FixedClock(now),
            SequentialIdentifierGenerator(prefix = "session"),
        )

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun seedExercise(id: String): Exercise {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = ExerciseId(id),
                    name = requireSuccess(ExerciseName.create("Exercise $id")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            )
        exerciseRepository.seed(exercise)
        return exercise
    }

    private fun plannedExercise(
        id: String,
        exerciseId: ExerciseId,
        order: Int,
    ) = PlannedExercise(
        id = PlannedExerciseId(id),
        exerciseId = exerciseId,
        order = order,
        targetSets = requireSuccess(TargetSets.create(3)),
        target = PlannedExerciseTarget.Reps(requireSuccess(RepRange.create(8, 12))),
        restDuration = null,
        isOptional = false,
    )

    @Test
    fun `seeds every planned exercise preserving order and plannedExerciseId`() =
        runTest {
            val first = seedExercise("1")
            val second = seedExercise("2")
            val command =
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = TrainingPlanVersionId("version-1"),
                    plannedExercises =
                        listOf(
                            plannedExercise("planned-2", second.id, order = 1),
                            plannedExercise("planned-1", first.id, order = 0),
                        ),
                )

            val result = useCase(command)

            assertTrue(result is DomainResult.Success)
            val session = requireNotNull(workoutRepository.findActiveSession())
            assertEquals(listOf(first.id, second.id), session.exercises.map { it.exerciseId })
            assertEquals(
                listOf(PlannedExerciseId("planned-1"), PlannedExerciseId("planned-2")),
                session.exercises.map { it.plannedExerciseId },
            )
        }

    @Test
    fun `a planned exercise whose underlying exercise no longer exists fails without persisting a partial session`() =
        runTest {
            val existing = seedExercise("1")
            val command =
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = TrainingPlanVersionId("version-1"),
                    plannedExercises =
                        listOf(
                            plannedExercise("planned-1", existing.id, order = 0),
                            plannedExercise("planned-missing", ExerciseId("does-not-exist"), order = 1),
                        ),
                )

            val result = useCase(command)

            assertEquals(DomainResult.Failure(WorkoutOperationError.NotFound), result)
            assertEquals(null, workoutRepository.findActiveSession())
        }

    @Test
    fun `rejects starting a plan session while one is already active`() =
        runTest {
            val exercise = seedExercise("1")
            val command =
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = TrainingPlanVersionId("version-1"),
                    plannedExercises = listOf(plannedExercise("planned-1", exercise.id, order = 0)),
                )
            requireSuccess(useCase(command))

            val result =
                useCase(command.copy(trainingPlanVersionId = TrainingPlanVersionId("version-2")))

            assertEquals(DomainResult.Failure(WorkoutOperationError.ActiveSessionAlreadyExists), result)
        }

    @Test
    fun `a persistence failure during insert is reported without a partial session`() =
        runTest {
            val exercise = seedExercise("1")
            workoutRepository.nextInsertFailure = WorkoutPersistenceError.Unavailable
            val command =
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = TrainingPlanVersionId("version-1"),
                    plannedExercises = listOf(plannedExercise("planned-1", exercise.id, order = 0)),
                )

            val result = useCase(command)

            assertEquals(DomainResult.Failure(WorkoutOperationError.PersistenceUnavailable), result)
            assertEquals(null, workoutRepository.findActiveSession())
        }
}
