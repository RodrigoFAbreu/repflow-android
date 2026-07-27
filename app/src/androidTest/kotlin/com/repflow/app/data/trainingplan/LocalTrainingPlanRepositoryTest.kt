package com.repflow.app.data.trainingplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.repflow.app.application.trainingplan.TrainingPlanPersistenceError
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.RestDuration
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * [LocalTrainingPlanRepository] against a real, in-memory Room database on
 * an actual device/emulator - in particular the transactional
 * [LocalTrainingPlanRepository.createPlanWithFirstVersion] /
 * [LocalTrainingPlanRepository.addVersion] writes and the
 * `SQLiteConstraintException` -> [TrainingPlanPersistenceError.DuplicateName]
 * translation, neither of which can be exercised from a pure JVM test.
 * Mirrors `LocalExerciseRepositoryTest`.
 */
@RunWith(AndroidJUnit4::class)
class LocalTrainingPlanRepositoryTest {
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
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = "exercise-1",
                    name = "Bench Press",
                    nameKey = "bench press",
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

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun plan(
        id: String = "plan-1",
        rawName: String = "Push Day",
    ): TrainingPlan {
        val name = requireSuccessValue(TrainingPlanName.create(rawName))
        return requireSuccessValue(
            TrainingPlan.create(id = TrainingPlanId(id), name = name, createdAt = Instant.ofEpochMilli(1_000L)),
        )
    }

    private fun version(
        id: String = "version-1",
        planId: String = "plan-1",
        versionNumber: Int = 1,
    ): TrainingPlanVersion {
        val plannedExercise =
            PlannedExercise(
                id = PlannedExerciseId("planned-$id"),
                exerciseId = ExerciseId("exercise-1"),
                order = 0,
                targetSets = requireSuccessValue(TargetSets.create(3)),
                target = PlannedExerciseTarget.Reps(requireSuccessValue(RepRange.create(8, 12))),
                restDuration = requireSuccessValue(RestDuration.create(90)),
                isOptional = false,
            )
        return requireSuccessValue(
            TrainingPlanVersion.create(
                id = TrainingPlanVersionId(id),
                planId = TrainingPlanId(planId),
                versionNumber = versionNumber,
                plannedExercises = listOf(plannedExercise),
                note = null,
                createdAt = Instant.ofEpochMilli(1_000L),
            ),
        )
    }

    private fun <V, E> requireSuccessValue(result: DomainResult<V, E>): V =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun createPlanWithFirstVersion_thenObserveOverviews_returnsThePlanAndVersion() =
        runBlocking {
            val result = repository.createPlanWithFirstVersion(plan(), version())

            assertTrue(result is DomainResult.Success)
            val overviews = repository.observeOverviews(TrainingPlanStatusFilter.ACTIVE).first()
            assertEquals(listOf("plan-1"), overviews.map { it.plan.id.value })
            assertEquals(1, overviews.single().latestVersion.versionNumber)
        }

    @Test
    fun updatePlan_archivesAPlanAndExcludesItFromTheActiveFilter() =
        runBlocking {
            repository.createPlanWithFirstVersion(plan(), version())
            val overview = requireNotNull(repository.findOverviewByPlanId(TrainingPlanId("plan-1")))
            val archived = overview.plan.archive(Instant.ofEpochMilli(2_000L))

            val result = repository.updatePlan(archived)

            assertTrue(result is DomainResult.Success)
            assertEquals(emptyList<String>(), repository.observeOverviews(TrainingPlanStatusFilter.ACTIVE).first().map { it.plan.id.value })
            val archivedOverviews = repository.observeOverviews(TrainingPlanStatusFilter.ARCHIVED).first()
            assertEquals(listOf("plan-1"), archivedOverviews.map { it.plan.id.value })
            assertTrue(archivedOverviews.single().plan.isArchived)
        }

    @Test
    fun updatePlan_returnsUnavailableWhenThePlanDoesNotExist() =
        runBlocking {
            val result = repository.updatePlan(plan())

            assertEquals(DomainResult.Failure(TrainingPlanPersistenceError.Unavailable), result)
        }

    @Test
    fun createPlanWithFirstVersion_rejectsADuplicateNormalizedName() =
        runBlocking {
            repository.createPlanWithFirstVersion(plan(id = "plan-1", rawName = "Push Day"), version())

            val result =
                repository.createPlanWithFirstVersion(
                    plan(id = "plan-2", rawName = "  PUSH   DAY  "),
                    version(id = "version-2", planId = "plan-2"),
                )

            assertEquals(DomainResult.Failure(TrainingPlanPersistenceError.DuplicateName), result)
        }

    @Test
    fun addVersion_insertsANewVersionAndPreservesThePreviousOneUnchanged() =
        runBlocking {
            repository.createPlanWithFirstVersion(plan(), version())
            val revisedPlan = plan()
            val secondVersion = version(id = "version-2", versionNumber = 2)

            val result = repository.addVersion(revisedPlan, secondVersion)

            assertTrue(result is DomainResult.Success)
            val overview = repository.findOverviewByPlanId(TrainingPlanId("plan-1"))
            assertEquals(2, overview?.latestVersion?.versionNumber)
            // The first version row itself is never mutated or deleted.
            val firstVersionEntity = database.trainingPlanVersionDao().findAllForPlan("plan-1").first()
            assertEquals("version-1", firstVersionEntity.id)
        }

    @Test
    fun addVersion_returnsUnavailableWhenThePlanDoesNotExist() =
        runBlocking {
            val result = repository.addVersion(plan(), version())

            assertEquals(DomainResult.Failure(TrainingPlanPersistenceError.Unavailable), result)
        }

    @Test
    fun findOverviewByPlanId_returnsNullWhenNoRowMatches() =
        runBlocking {
            assertNull(repository.findOverviewByPlanId(TrainingPlanId("missing")))
        }

    @Test
    fun findPlanIdByNameKey_returnsTheMatchingId() =
        runBlocking {
            repository.createPlanWithFirstVersion(plan(), version())

            assertEquals(TrainingPlanId("plan-1"), repository.findPlanIdByNameKey("push day"))
        }

    /**
     * Real-database proof (Milestone 8, implementation-review finding #5)
     * that [LocalTrainingPlanRepository.observeVersionLabels] re-emits for
     * an already-open subscriber when the plan is renamed - not just that a
     * fresh query happens to see the new name, which a one-shot `suspend`
     * read would also do.
     */
    @Test
    fun observeVersionLabels_reEmitsForAnAlreadyOpenSubscriberWhenThePlanIsRenamed() =
        runBlocking {
            requireSuccessValue(repository.createPlanWithFirstVersion(plan(), version()))
            val versionId = TrainingPlanVersionId("version-1")

            repository.observeVersionLabels().test {
                var labels = awaitItem()
                while (labels[versionId]?.planName != "Push Day") {
                    labels = awaitItem()
                }

                val renamed =
                    requireSuccessValue(
                        TrainingPlan.create(
                            id = TrainingPlanId("plan-1"),
                            name = requireSuccessValue(TrainingPlanName.create("Push Day Renamed")),
                            createdAt = Instant.ofEpochMilli(1_000L),
                        ),
                    )
                requireSuccessValue(repository.updatePlan(renamed))

                var afterRename = awaitItem()
                while (afterRename[versionId]?.planName != "Push Day Renamed") {
                    afterRename = awaitItem()
                }
                assertEquals("Push Day Renamed", afterRename[versionId]?.planName)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
