package com.repflow.app.application.backup

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExportWorkoutHistoryCsvTest {
    private val repository = InMemoryWorkoutRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val useCase = ExportWorkoutHistoryCsv(repository, ObserveTrainingPlanVersionLabels(trainingPlanRepository))

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `formats one row per recorded set with a header`() =
        runTest {
            val set =
                requireSuccess(
                    WorkoutSet.create(
                        id = WorkoutSetId("set-1"),
                        order = 0,
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = 7.5,
                        isWarmup = false,
                        createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                        updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                        pain = 3,
                        techniqueQuality = 4,
                    ),
                )
            val exercise =
                requireSuccess(
                    WorkoutExercise.create(
                        id = WorkoutExerciseId("we-1"),
                        sessionId = WorkoutSessionId("session-1"),
                        exerciseId = ExerciseId("ex-1"),
                        order = 0,
                        exerciseNameSnapshot = "Bench Press",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        plannedExerciseId = null,
                        sets = listOf(set),
                    ),
                )
            val session =
                requireSuccess(
                    requireSuccess(
                        WorkoutSession
                            .start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
                            .withAddedExercise(exercise),
                    ).complete(Instant.parse("2026-01-01T01:00:00Z")),
                )
            repository.insert(session)

            val csv = useCase()

            val lines = csv.trim().lines()
            assertTrue(lines[0].startsWith("session_id,started_at,ended_at,exercise_name"))
            assertTrue(lines[0].contains("pain,technique_quality,plan_id,plan_name"))
            assertTrue(lines[1].contains("\"session-1\""))
            assertTrue(lines[1].contains("\"Bench Press\""))
            assertTrue(lines[1].contains("\"8\""))
            assertTrue(lines[1].contains("\"60.0\""))
            assertTrue(lines[1].contains("\"7.5\""))
            // pain=3, technique_quality=4, then empty plan_id/plan_name (ad-hoc session).
            assertTrue(lines[1].endsWith("\"3\",\"4\",\"\",\"\""))
        }

    @Test
    fun `a plan-linked session includes the resolved plan id and name`() =
        runTest {
            val planId = seedPlanLinkedSession()

            val csv = useCase()

            val row = csv.trim().lines()[1]
            assertTrue(row.contains(csvField(planId.value)))
            assertTrue(row.endsWith(csvField(planId.value) + "," + csvField("Push day")))
        }

    /** Creates a real plan (via [CreateTrainingPlan], not a repository shortcut) with one exercise, returning its id and that exercise's id. */
    private suspend fun createPlanWithOneExercise(): Pair<TrainingPlanId, ExerciseId> {
        val exerciseRepository = InMemoryExerciseRepository()
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        val ids = SequentialIdentifierGenerator(prefix = "plan")
        val bench =
            requireSuccess(
                Exercise.create(
                    id = ExerciseId("ex-1"),
                    name = requireSuccess(ExerciseName.create("Bench Press")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = clock.now(),
                ),
            )
        exerciseRepository.seed(bench)
        val createTrainingPlan = CreateTrainingPlan(trainingPlanRepository, exerciseRepository, clock, ids)
        val plannedExercise =
            PlannedExerciseInput(
                exerciseId = bench.id.value,
                order = 0,
                targetSets = 3,
                targetKind = PlannedExerciseTargetKind.REPS,
                repMin = 8,
                repMax = 12,
                durationMinSeconds = null,
                durationMaxSeconds = null,
                restSeconds = 90,
                isOptional = false,
            )
        val planId = requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push day", listOf(plannedExercise))))
        return planId to bench.id
    }

    /** Creates a real plan and a completed session started from it, returning the plan's id. */
    private suspend fun seedPlanLinkedSession(): TrainingPlanId {
        val (planId, exerciseId) = createPlanWithOneExercise()
        val versionId = requireNotNull(trainingPlanRepository.findOverviewByPlanId(planId)).latestVersion.id

        val set =
            requireSuccess(
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                ),
            )
        val exercise =
            requireSuccess(
                WorkoutExercise.create(
                    id = WorkoutExerciseId("we-1"),
                    sessionId = WorkoutSessionId("session-1"),
                    exerciseId = exerciseId,
                    order = 0,
                    exerciseNameSnapshot = "Bench Press",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ),
            )
        val session =
            requireSuccess(
                requireSuccess(
                    WorkoutSession
                        .start(WorkoutSessionId("session-1"), versionId, Instant.parse("2026-01-01T00:00:00Z"))
                        .withAddedExercise(exercise),
                ).complete(Instant.parse("2026-01-01T01:00:00Z")),
            )
        repository.insert(session)
        return planId
    }

    private fun csvField(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
}
