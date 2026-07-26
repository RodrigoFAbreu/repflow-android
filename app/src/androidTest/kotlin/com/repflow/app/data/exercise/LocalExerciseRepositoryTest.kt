package com.repflow.app.data.exercise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.exercise.ExercisePersistenceError
import com.repflow.app.application.exercise.ExerciseQueryCriteria
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.infrastructure.database.RepFlowDatabase
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
 * [LocalExerciseRepository] against a real, in-memory Room database on an
 * actual device/emulator - in particular the `SQLiteConstraintException` ->
 * [ExercisePersistenceError.DuplicateName] translation, which cannot be
 * exercised from a pure JVM test (see plan.md section L, checkpoint 6).
 */
@RunWith(AndroidJUnit4::class)
class LocalExerciseRepositoryTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalExerciseRepository

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        repository = LocalExerciseRepository(database.exerciseDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun exercise(
        id: String = "11111111-1111-1111-1111-111111111111",
        rawName: String = "Bench Press",
        createdAt: Instant = Instant.ofEpochMilli(1_000L),
    ): Exercise {
        val name = requireSuccessValue(ExerciseName.create(rawName))
        return requireSuccessValue(
            Exercise.create(
                id = ExerciseId(id),
                name = name,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = createdAt,
            ),
        )
    }

    private fun <V, E> requireSuccessValue(result: DomainResult<V, E>): V =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun insert_thenObserve_returnsTheInsertedExercise() =
        runBlocking {
            val original = exercise()

            val insertResult = repository.insert(original)

            assertTrue(insertResult is DomainResult.Success)
            val observed = repository.observe(ExerciseQueryCriteria()).first()
            assertEquals(listOf(original.id), observed.map { it.id })
        }

    @Test
    fun insert_rejectsADuplicateNormalizedName() =
        runBlocking {
            repository.insert(exercise(id = "11111111-1111-1111-1111-111111111111", rawName = "Bench Press"))

            val result =
                repository.insert(
                    exercise(id = "22222222-2222-2222-2222-222222222222", rawName = "  BENCH   PRESS  "),
                )

            assertEquals(DomainResult.Failure(ExercisePersistenceError.DuplicateName), result)
        }

    @Test
    fun update_persistsChangesForAnExistingExercise() =
        runBlocking {
            val original = exercise()
            repository.insert(original)
            val renamedName = requireSuccessValue(ExerciseName.create("Incline Bench Press"))
            val updated = original.copyForTest(name = renamedName)

            val result = repository.update(updated)

            assertTrue(result is DomainResult.Success)
            val reloaded = repository.findById(original.id)
            assertEquals("Incline Bench Press", reloaded?.name?.value)
        }

    @Test
    fun update_returnsUnavailableWhenTheExerciseDoesNotExist() =
        runBlocking {
            val result = repository.update(exercise())

            assertEquals(DomainResult.Failure(ExercisePersistenceError.Unavailable), result)
        }

    @Test
    fun findById_returnsNullWhenNoRowMatches() =
        runBlocking {
            assertNull(repository.findById(ExerciseId("missing")))
        }

    @Test
    fun findIdByNameKey_returnsTheMatchingId() =
        runBlocking {
            val original = exercise()
            repository.insert(original)

            assertEquals(original.id, repository.findIdByNameKey(original.name.key))
        }

    @Test
    fun observe_appliesTheArchivedFilter() =
        runBlocking {
            repository.insert(exercise())

            val archived =
                repository.observe(ExerciseQueryCriteria(status = ExerciseStatusFilter.ARCHIVED)).first()

            assertEquals(emptyList<Exercise>(), archived)
        }
}

/**
 * [Exercise.copy] is private by design (see [Exercise]'s KDoc); this test
 * uses the public [Exercise.reconstruct] path instead of reaching for
 * reflection, which keeps the fixture honest about which invariants apply.
 */
private fun Exercise.copyForTest(name: ExerciseName): Exercise =
    when (
        val result =
            Exercise.reconstruct(
                id = id,
                name = name,
                trackingType = trackingType,
                instructions = instructions,
                defaultLoadIncrement = defaultLoadIncrement,
                defaultRestDuration = defaultRestDuration,
                origin = origin,
                archivedAt = archivedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
    ) {
        is DomainResult.Success -> result.value
        is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
    }
