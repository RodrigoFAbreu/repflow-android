package com.repflow.app.data.workout

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.StartWorkoutSessionFromPlanCommand
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
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
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Real-SQLite instrumented coverage (Milestone 8, implementation-review
 * finding #1) proving [StartWorkoutSessionFromPlan] leaves no partial active
 * session in a real database when a planned exercise fails to resolve - the
 * exact failure mode [InMemoryWorkoutRepository]-backed
 * `StartWorkoutSessionFromPlanTest` can't fully prove, since that fake's
 * `insert` doesn't go through Room's transaction machinery.
 */
@RunWith(AndroidJUnit4::class)
class StartWorkoutSessionFromPlanAtomicityTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private lateinit var database: RepFlowDatabase
    private lateinit var useCase: StartWorkoutSessionFromPlan

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        useCase =
            StartWorkoutSessionFromPlan(
                LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao()),
                GetExercise(LocalExerciseRepository(database.exerciseDao())),
                { now },
                {
                    java.util.UUID
                        .randomUUID()
                        .toString()
                },
            )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private suspend fun seedExercise(id: String): Exercise {
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
        requireSuccess(LocalExerciseRepository(database.exerciseDao()).insert(exercise))
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
    fun startingFromAPlanWithAMissingExerciseLeavesNoPartialSessionInTheRealDatabase() =
        runBlocking {
            val existing = seedExercise("exercise-1")
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
            assertNull(database.workoutSessionDao().findActive())
            assertEquals(emptyList<Any>(), database.workoutExerciseDao().findAllForSession("does-not-matter"))
        }

    /**
     * `workout_exercises.planned_exercise_id` carries a real foreign key to
     * `planned_exercises.id` (see [com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity]),
     * so - unlike the missing-exercise case above, which never reaches
     * `insert` at all - proving the success path against a real database
     * requires genuinely persisted planned-exercise rows, exactly what
     * [ActiveWorkoutViewModel][com.repflow.app.presentation.workout.ActiveWorkoutViewModel]
     * passes in production (`TrainingPlanOverview.latestVersion.plannedExercises`,
     * itself read back from [LocalTrainingPlanRepository]).
     */
    @Test
    fun startingFromAPlanWithEveryExerciseResolvingPersistsTheWholeSessionAtomically() =
        runBlocking {
            val first = seedExercise("exercise-1")
            val second = seedExercise("exercise-2")
            val trainingPlanRepository =
                LocalTrainingPlanRepository(
                    database,
                    database.trainingPlanDao(),
                    database.trainingPlanVersionDao(),
                    database.plannedExerciseDao(),
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
                        id = TrainingPlanVersionId("version-1"),
                        planId = plan.id,
                        versionNumber = 1,
                        plannedExercises =
                            listOf(
                                plannedExercise("planned-1", first.id, order = 0),
                                plannedExercise("planned-2", second.id, order = 1),
                            ),
                        note = null,
                        createdAt = now,
                    ),
                )
            requireSuccess(trainingPlanRepository.createPlanWithFirstVersion(plan, version))
            val command =
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = version.id,
                    plannedExercises = version.plannedExercises,
                )

            val result = useCase(command)

            assertEquals(true, result is DomainResult.Success)
            val session = requireNotNull(database.workoutSessionDao().findActive())
            assertEquals(2, database.workoutExerciseDao().findAllForSession(session.id).size)
        }
}
