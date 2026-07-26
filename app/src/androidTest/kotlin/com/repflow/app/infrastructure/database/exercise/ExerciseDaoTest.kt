package com.repflow.app.infrastructure.database.exercise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.data.exercise.buildNameSearchPattern
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [ExerciseDao], run against an
 * in-memory Room database on an actual device/emulator (see plan.md section
 * L, checkpoint 6 - JVM-only Robolectric is deliberately not used here).
 */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        dao = database.exerciseDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entity(
        id: String,
        name: String,
        nameKey: String,
        archivedAt: Long? = null,
    ) = ExerciseEntity(
        id = id,
        name = name,
        nameKey = nameKey,
        trackingType = "WEIGHT_AND_REPS",
        instructions = null,
        defaultLoadIncrementGrams = null,
        defaultRestSeconds = null,
        origin = "CUSTOM",
        archivedAt = archivedAt,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun observe_returnsActiveRowsOrderedByNameKeyThenId() =
        runBlocking {
            dao.insert(entity(id = "b", name = "Squat", nameKey = "squat"))
            dao.insert(entity(id = "a", name = "Bench Press", nameKey = "bench press"))
            dao.insert(entity(id = "z", name = "Archived Row", nameKey = "archived row", archivedAt = 500L))

            val active = dao.observe(archived = false, likePattern = buildNameSearchPattern("")).first()

            assertEquals(listOf("bench press", "squat"), active.map { it.nameKey })
        }

    /**
     * `name_key` is UNIQUE across every row (active and archived - D-9), so
     * two rows can never actually share a `name_key` value; the `id ASC`
     * clause in [ExerciseDao.observe]'s `ORDER BY` is purely defensive and
     * cannot be exercised without violating that constraint. There is
     * intentionally no test forcing a same-`name_key` tie, since doing so
     * would require bypassing the very invariant this schema guarantees.
     */
    @Test
    fun observe_filtersByArchivedStatus() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Active", nameKey = "active"))
            dao.insert(entity(id = "b", name = "Gone", nameKey = "gone", archivedAt = 500L))

            val archived = dao.observe(archived = true, likePattern = buildNameSearchPattern("")).first()

            assertEquals(listOf("b"), archived.map { it.id })
        }

    @Test
    fun observe_matchesSubstringSearch() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Bench Press", nameKey = "bench press"))
            dao.insert(entity(id = "b", name = "Squat", nameKey = "squat"))

            val results = dao.observe(archived = false, likePattern = buildNameSearchPattern("bench")).first()

            assertEquals(listOf("a"), results.map { it.id })
        }

    @Test
    fun observe_treatsPercentAsALiteralCharacterRatherThanAWildcard() =
        runBlocking {
            dao.insert(entity(id = "a", name = "100% Effort", nameKey = "100% effort"))
            dao.insert(entity(id = "b", name = "100 Effort Plus", nameKey = "100 effort plus"))

            val results = dao.observe(archived = false, likePattern = buildNameSearchPattern("100%")).first()

            assertEquals(listOf("a"), results.map { it.id })
        }

    @Test
    fun observe_treatsUnderscoreAsALiteralCharacterRatherThanAWildcard() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Row_Variant", nameKey = "row_variant"))
            dao.insert(entity(id = "b", name = "RowXVariant", nameKey = "rowxvariant"))

            val results = dao.observe(archived = false, likePattern = buildNameSearchPattern("row_")).first()

            assertEquals(listOf("a"), results.map { it.id })
        }

    @Test
    fun observe_treatsBackslashAsALiteralCharacterRatherThanAnEscapeIntroducer() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Row\\Variant", nameKey = "row\\variant"))
            dao.insert(entity(id = "b", name = "RowVariant", nameKey = "rowvariant"))

            val results = dao.observe(archived = false, likePattern = buildNameSearchPattern("row\\")).first()

            assertEquals(listOf("a"), results.map { it.id })
        }

    @Test
    fun observe_isAccentSensitive() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Curl", nameKey = "curl"))
            dao.insert(entity(id = "b", name = "Cúrl", nameKey = "cúrl"))

            val results = dao.observe(archived = false, likePattern = buildNameSearchPattern("cúrl")).first()

            assertEquals(listOf("b"), results.map { it.id })
        }

    @Test(expected = Exception::class)
    fun insert_rejectsADuplicateNameKey() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Squat", nameKey = "squat"))
            dao.insert(entity(id = "b", name = "SQUAT", nameKey = "squat"))
        }

    @Test
    fun update_returnsZeroRowsChangedWhenTheIdDoesNotExist() =
        runBlocking {
            val rowsUpdated = dao.update(entity(id = "missing", name = "Ghost", nameKey = "ghost"))

            assertEquals(0, rowsUpdated)
        }

    @Test
    fun update_returnsOneRowChangedForAnExistingId() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Squat", nameKey = "squat"))

            val rowsUpdated = dao.update(entity(id = "a", name = "Front Squat", nameKey = "front squat"))

            assertEquals(1, rowsUpdated)
        }

    @Test
    fun findIdByNameKey_returnsTheMatchingId() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Squat", nameKey = "squat"))

            assertEquals("a", dao.findIdByNameKey("squat"))
        }

    @Test
    fun findIdByNameKey_returnsNullWhenNoRowMatches() =
        runBlocking {
            assertEquals(null, dao.findIdByNameKey("squat"))
        }
}
