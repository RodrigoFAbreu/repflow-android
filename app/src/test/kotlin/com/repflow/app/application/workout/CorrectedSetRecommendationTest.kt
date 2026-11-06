package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * A corrected RPE reaches the suggestion that workout completion stores, because
 * [EditWorkoutSet] rewrites the same persisted set [CompleteWorkoutSession] reads.
 * The rep range comes from a real planned exercise: without one the policy gives
 * `WaitForMoreData` whatever the RPE. The recovery and futsal fakes stay empty, so no
 * recovery adjustment applies.
 */
class CorrectedSetRecommendationTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val finishClock = FixedClock(now.plusSeconds(3600))
    private val workoutRepository = InMemoryWorkoutRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendationRepository = InMemoryProgressionRecommendationRepository()
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val startSession = StartWorkoutSession(workoutRepository, FixedClock(now), ids)
    private val addExercise = AddWorkoutExercise(workoutRepository, ids)
    private val recordSet = RecordWorkoutSet(workoutRepository, FixedClock(now), ids)
    private val editSet = EditWorkoutSet(workoutRepository, FixedClock(now.plusSeconds(60)))
    private val complete =
        CompleteWorkoutSession(
            workoutRepository,
            trainingPlanRepository,
            ComputeProgressionRecommendation(
                recommendationRepository,
                GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), finishClock),
                finishClock,
                SequentialIdentifierGenerator(prefix = "rec"),
            ),
            finishClock,
        )

    private val exerciseId = ExerciseId("exercise-1")
    private val plannedExerciseId = PlannedExerciseId("planned-1")
    private val versionId = TrainingPlanVersionId("version-1")

    private suspend fun seedPlanWithARepRangeOf8To12() {
        val plannedExercise =
            PlannedExercise(
                id = plannedExerciseId,
                exerciseId = exerciseId,
                order = 0,
                targetSets = requireSuccess(TargetSets.create(2)),
                target = PlannedExerciseTarget.Reps(requireSuccess(RepRange.create(8, 12))),
                restDuration = null,
                isOptional = false,
            )
        val plan =
            requireSuccess(
                TrainingPlan.create(
                    id = TrainingPlanId("plan-1"),
                    name = requireSuccess(TrainingPlanName.create("Push day")),
                    createdAt = now,
                ),
            )
        val version =
            requireSuccess(
                TrainingPlanVersion.create(
                    id = versionId,
                    planId = plan.id,
                    versionNumber = 1,
                    plannedExercises = listOf(plannedExercise),
                    note = null,
                    createdAt = now,
                ),
            )
        requireSuccess(trainingPlanRepository.createPlanWithFirstVersion(plan, version))
    }

    private suspend fun logTwoWorkingSetsOf12Reps(
        firstRpe: Double = 7.0,
        secondRpe: Double = 7.0,
    ): Pair<WorkoutSessionId, WorkoutExerciseId> {
        seedPlanWithARepRangeOf8To12()
        val sessionId = requireSuccess(startSession(StartWorkoutSessionCommand(trainingPlanVersionId = versionId)))
        val workoutExerciseId =
            requireSuccess(
                addExercise(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        exerciseNameSnapshot = "Bench",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        plannedExerciseId = plannedExerciseId,
                    ),
                ),
            )
        listOf(firstRpe, secondRpe).forEach { rpe ->
            requireSuccess(
                recordSet(
                    RecordWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = workoutExerciseId,
                        load = 60.0,
                        reps = 12,
                        durationSeconds = null,
                        rpe = rpe,
                        isWarmup = false,
                    ),
                ),
            )
        }
        return sessionId to workoutExerciseId
    }

    private suspend fun loggedSetIds(sessionId: WorkoutSessionId): List<WorkoutSetId> =
        requireNotNull(workoutRepository.findById(sessionId))
            .exercises
            .single()
            .sets
            .map { it.id }

    private suspend fun correctRpe(
        sessionId: WorkoutSessionId,
        workoutExerciseId: WorkoutExerciseId,
        setId: WorkoutSetId,
        rpe: Double,
    ) {
        requireSuccess(
            editSet(
                EditWorkoutSetCommand(
                    sessionId = sessionId,
                    exerciseId = workoutExerciseId,
                    setId = setId,
                    load = 60.0,
                    reps = 12,
                    durationSeconds = null,
                    rpe = rpe,
                    isWarmup = false,
                ),
            ),
        )
    }

    private suspend fun completeAndReadTheSuggestion(sessionId: WorkoutSessionId): ProgressionResult? {
        requireSuccess(complete(sessionId))
        return recommendationRepository.findLatestForExercise(exerciseId)?.result
    }

    @Test
    fun `with no correction the logged RPE gives an increase`() =
        runTest {
            val (sessionId, _) = logTwoWorkingSetsOf12Reps()

            assertEquals(ProgressionResult.IncreaseLoad, completeAndReadTheSuggestion(sessionId))
        }

    @Test
    fun `an RPE corrected before completing changes the stored suggestion`() =
        runTest {
            val (sessionId, workoutExerciseId) = logTwoWorkingSetsOf12Reps()

            correctRpe(sessionId, workoutExerciseId, loggedSetIds(sessionId).last(), rpe = 10.0)

            assertEquals(ProgressionResult.MaintainLoad, completeAndReadTheSuggestion(sessionId))
        }

    /** `ProgressionPolicyV1` averages, so RPE 10 then 7 is 8.5, and only a correction of the first set can move it. */
    @Test
    fun `a high RPE logged on the first set holds the suggestion with no correction`() =
        runTest {
            val (sessionId, _) = logTwoWorkingSetsOf12Reps(firstRpe = 10.0, secondRpe = 7.0)

            assertEquals(ProgressionResult.MaintainLoad, completeAndReadTheSuggestion(sessionId))
        }

    @Test
    fun `an RPE corrected on the first set changes the suggestion, and only on completion`() =
        runTest {
            val (sessionId, workoutExerciseId) = logTwoWorkingSetsOf12Reps(firstRpe = 10.0, secondRpe = 7.0)

            correctRpe(sessionId, workoutExerciseId, loggedSetIds(sessionId).first(), rpe = 7.0)

            assertNull(recommendationRepository.findLatestForExercise(exerciseId))
            assertEquals(ProgressionResult.IncreaseLoad, completeAndReadTheSuggestion(sessionId))
        }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but got $result")
        }
}
