package com.repflow.app.data.trainingplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
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
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * [LocalTrainingPlanRepository.observeExercisePlanUsage] - the exercise
 * library's `in N plans` (remediation-1 CP10) - against a real, in-memory Room
 * database: only non-archived plans count, only each plan's **latest** version
 * counts, an exercise held twice by one plan counts that plan once, and the
 * flow re-emits when a plan is archived. Kept beside
 * `LocalTrainingPlanRepositoryTest` rather than inside it, as the plan states.
 */
@RunWith(AndroidJUnit4::class)
class LocalTrainingPlanRepositoryPlanUsageTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalTrainingPlanRepository

    @Before
    fun createDatabase() =
        runBlocking {
            database =
                Room
                    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                    .build()
            repository =
                LocalTrainingPlanRepository(
                    database = database,
                    planDao = database.trainingPlanDao(),
                    versionDao = database.trainingPlanVersionDao(),
                    plannedExerciseDao = database.plannedExerciseDao(),
                )
            listOf("bench" to "Bench Press", "squat" to "Squat", "row" to "Row").forEach { (id, name) ->
                database.exerciseDao().insert(
                    ExerciseEntity(
                        id = id,
                        name = name,
                        nameKey = name.lowercase(),
                        trackingType = "WEIGHT_AND_REPS",
                        instructions = null,
                        defaultLoadIncrementGrams = null,
                        defaultRestSeconds = null,
                        origin = "CUSTOM",
                        archivedAt = null,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                    ),
                )
            }
        }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun countsNonArchivedPlansByTheirLatestVersionOnly() =
        runBlocking {
            // Push: version 1 held bench + squat; version 2 holds bench twice and row.
            val push = plan("push", "Push Day")
            success(repository.createPlanWithFirstVersion(push, version("push-v1", "push", 1, "bench", "squat")))
            success(repository.addVersion(push, version("push-v2", "push", 2, "bench", "bench", "row")))
            // Legs: one version, bench + squat.
            success(repository.createPlanWithFirstVersion(plan("legs", "Legs"), version("legs-v1", "legs", 1, "bench", "squat")))

            assertEquals(
                mapOf(ExerciseId("bench") to 2, ExerciseId("squat") to 1, ExerciseId("row") to 1),
                repository.observeExercisePlanUsage().first(),
            )
        }

    @Test
    fun anArchivedPlanStopsCountingAndTheFlowReEmits() =
        runBlocking {
            val push = plan("push", "Push Day")
            success(repository.createPlanWithFirstVersion(push, version("push-v1", "push", 1, "bench")))
            success(repository.createPlanWithFirstVersion(plan("legs", "Legs"), version("legs-v1", "legs", 1, "bench", "squat")))

            repository.observeExercisePlanUsage().test {
                assertEquals(mapOf(ExerciseId("bench") to 2, ExerciseId("squat") to 1), awaitItem())

                success(repository.updatePlan(push.archive(Instant.ofEpochMilli(2_000L))))

                assertEquals(mapOf(ExerciseId("bench") to 1, ExerciseId("squat") to 1), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun plan(
        id: String,
        rawName: String,
    ): TrainingPlan =
        success(
            TrainingPlan.create(
                id = TrainingPlanId(id),
                name = success(TrainingPlanName.create(rawName)),
                createdAt = Instant.ofEpochMilli(1_000L),
            ),
        )

    private fun version(
        id: String,
        planId: String,
        versionNumber: Int,
        vararg exerciseIds: String,
    ): TrainingPlanVersion =
        success(
            TrainingPlanVersion.create(
                id = TrainingPlanVersionId(id),
                planId = TrainingPlanId(planId),
                versionNumber = versionNumber,
                plannedExercises =
                    exerciseIds.mapIndexed { index, exerciseId ->
                        PlannedExercise(
                            id = PlannedExerciseId("$id-$index"),
                            exerciseId = ExerciseId(exerciseId),
                            order = index,
                            targetSets = success(TargetSets.create(3)),
                            target = PlannedExerciseTarget.Reps(success(RepRange.create(8, 12))),
                            restDuration = null,
                            isOptional = false,
                        )
                    },
                note = null,
                createdAt = Instant.ofEpochMilli(1_000L + versionNumber),
            ),
        )

    private fun <V, E> success(result: DomainResult<V, E>): V =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
