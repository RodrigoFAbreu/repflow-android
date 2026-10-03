package com.repflow.app.presentation.progression

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.progression.ProgressionPersistenceError
import com.repflow.app.application.progression.RecordManualOverride
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.RecordRecoveryEntryCommand
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.ManualOverride
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.presentation.navigation.RepFlowDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The recommendation screen's ViewModel (remediation-1 CP6).
 *
 * Plan CP6 owes **one state test per `ProgressionResult` case**: each of the
 * five below seeds that outcome and asserts it loads as its own state - the
 * outcome, every reason the policy recorded, in order and unchanged, and no
 * choice. `RecoveryAdjustment` additionally carries today's readiness (plan
 * CP6 item 4), and no other outcome reads it.
 *
 * The rest pin the override path: a pick reaches `RecordManualOverride` and
 * re-renders as the recorded choice; going with the suggestion writes nothing;
 * a change of mind back to the suggestion is recorded; a failed save is
 * reported and changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionRecommendationViewModelTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.parse("2026-08-11")
    private val computedAt: Instant =
        today
            .minusDays(2)
            .atTime(18, 0)
            .atZone(zone)
            .toInstant()
    private val clock = FixedClock(today.atTime(10, 0).atZone(zone).toInstant())
    private val exerciseId = ExerciseId("exercise-1")

    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val exercises = InMemoryExerciseRepository()
    private val recovery = InMemoryRecoveryRepository()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        exercises.seed(exercise())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an increase loads as its own state with every reason`() =
        assertLoadsAsItsOwnState(
            ProgressionResult.IncreaseLoad,
            ProgressionResultUi.INCREASE_LOAD,
            listOf("Every working set reached 8+ reps at average RPE 7.0"),
        )

    @Test
    fun `a maintain loads as its own state with every reason`() =
        assertLoadsAsItsOwnState(
            ProgressionResult.MaintainLoad,
            ProgressionResultUi.MAINTAIN_LOAD,
            listOf("Performance was within the planned rep range but not clearly at either extreme"),
        )

    @Test
    fun `a reduction loads as its own state with every reason`() =
        assertLoadsAsItsOwnState(
            ProgressionResult.ReduceLoad,
            ProgressionResultUi.REDUCE_LOAD,
            listOf("Average RPE 9.2 is at or above 9.0, or fewer than half the working sets reached 6 reps"),
        )

    @Test
    fun `not enough data loads as its own state, not as an error`() =
        assertLoadsAsItsOwnState(
            ProgressionResult.WaitForMoreData,
            ProgressionResultUi.WAIT_FOR_MORE_DATA,
            listOf("Only warm-up sets were recorded - no working sets to evaluate"),
        )

    @Test
    fun `a recovery adjustment loads with every reason and today's readiness`() =
        runTest {
            val reasons = listOf("Heavy legs is elevated (4/5)", "Futsal session recorded in the last 24h")
            seed(ProgressionResult.RecoveryAdjustment, reasons)
            recordCheckIn(today)
            val expected = ReadinessScore.of(checkNotNull(recovery.findForDate(today)))

            viewModel().uiState.test {
                val loaded = awaitLoaded { it.readiness != null }
                assertEquals(ProgressionResultUi.RECOVERY_ADJUSTMENT, loaded.suggested)
                assertEquals(reasons, loaded.reasons)
                assertNull(loaded.choice)
                assertEquals(expected, loaded.readiness)
                assertEquals(expected.driverSentence, loaded.readiness?.driverSentence)
            }
        }

    @Test
    fun `a recovery adjustment without today's check-in carries no readiness`() =
        runTest {
            seed(ProgressionResult.RecoveryAdjustment, listOf("Heavy legs is elevated (4/5)"))
            recordCheckIn(today.minusDays(1))

            viewModel().uiState.test {
                val loaded = awaitLoaded()
                assertNull(loaded.readiness)
            }
        }

    @Test
    fun `picking another outcome reaches RecordManualOverride and re-renders as the recorded choice`() =
        runTest {
            seed(ProgressionResult.IncreaseLoad, listOf("reason"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onChooseAnother()
                assertTrue(awaitItem().choosing)

                viewModel.onPick(ProgressionResultUi.REDUCE_LOAD)

                val settled = awaitLoaded { it.choice != null }
                assertEquals(RecommendationChoice(ProgressionResultUi.REDUCE_LOAD, clock.now()), settled.choice)
                assertTrue(settled.isOverridden)
                assertEquals(ProgressionResultUi.INCREASE_LOAD, settled.suggested)
                assertFalse(viewModel.uiState.value.choosing)
            }
            val stored = checkNotNull(recommendations.findLatestForExercise(exerciseId))
            assertEquals(ProgressionResult.IncreaseLoad, stored.result)
            assertEquals(ProgressionResult.ReduceLoad, stored.manualOverride?.result)
            // The workout picker's row reads the same recommendation: reduce, marked overridden.
            assertEquals(ProgressionRecommendationUi(ProgressionResultUi.REDUCE_LOAD, "reason", isOverridden = true), stored.toSummaryUi())
        }

    @Test
    fun `keep the same load records an override to maintain`() =
        runTest {
            seed(ProgressionResult.IncreaseLoad, listOf("reason"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onKeepSameLoad()
                assertEquals(ProgressionResultUi.MAINTAIN_LOAD, awaitLoaded { it.choice != null }.choice?.result)
            }
            assertEquals(ProgressionResult.MaintainLoad, recommendations.findLatestForExercise(exerciseId)?.manualOverride?.result)
        }

    @Test
    fun `picking the suggestion itself writes nothing and closes the options`() =
        runTest {
            seed(ProgressionResult.MaintainLoad, listOf("reason"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onChooseAnother()
                assertTrue(awaitItem().choosing)
                viewModel.onPick(ProgressionResultUi.MAINTAIN_LOAD)
                assertFalse(awaitItem().choosing)
            }
            assertNull(recommendations.findLatestForExercise(exerciseId)?.manualOverride)
        }

    @Test
    fun `a change of mind back to the suggestion is recorded and no longer reads as overridden`() =
        runTest {
            seed(
                ProgressionResult.IncreaseLoad,
                listOf("reason"),
                override = ManualOverride(ProgressionResult.MaintainLoad, computedAt.plusSeconds(60)),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertTrue(awaitLoaded().isOverridden)
                viewModel.onPick(ProgressionResultUi.INCREASE_LOAD)
                val settled = awaitLoaded { it.choice?.result == ProgressionResultUi.INCREASE_LOAD }
                assertFalse(settled.isOverridden)
            }
            val stored = checkNotNull(recommendations.findLatestForExercise(exerciseId))
            assertEquals(ProgressionResult.IncreaseLoad, stored.manualOverride?.result)
            assertFalse(stored.toSummaryUi().isOverridden)
        }

    @Test
    fun `a failed save is reported and leaves the recommendation as it was`() =
        runTest {
            seed(ProgressionResult.IncreaseLoad, listOf("reason"))
            recommendations.nextUpdateFailure = ProgressionPersistenceError.Unavailable
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onPick(ProgressionResultUi.REDUCE_LOAD)
                var state = awaitItem()
                while (state.error == null) state = awaitItem()
                assertEquals(RecommendationErrorReason.SAVE_FAILED, state.error)
                assertNull((state.content as RecommendationContent.Loaded).choice)
                viewModel.onErrorShown()
                assertNull(viewModel.uiState.value.error)
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(recommendations.findLatestForExercise(exerciseId)?.manualOverride)
        }

    @Test
    fun `an exercise with no recommendation yet is not found`() =
        runTest {
            viewModel().uiState.test {
                var state = awaitItem()
                while (state.content == RecommendationContent.Loading) state = awaitItem()
                assertEquals(RecommendationContent.NotFound, state.content)
            }
        }

    @Test
    fun `the screen shows the exercise's name`() =
        runTest {
            seed(ProgressionResult.IncreaseLoad, listOf("reason"))
            viewModel().uiState.test { assertEquals("Back Squat", awaitLoaded().exerciseName) }
        }

    private fun assertLoadsAsItsOwnState(
        result: ProgressionResult,
        expected: ProgressionResultUi,
        reasons: List<String>,
    ) = runTest {
        seed(result, reasons)
        // A check-in exists today, so a readiness that showed up here would be read for the wrong outcome.
        recordCheckIn(today)

        val viewModel = viewModel()
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals(expected, loaded.suggested)
            assertEquals(reasons, loaded.reasons)
            assertEquals(1, loaded.policyVersion)
            assertNull(loaded.choice)
            assertNull(loaded.readiness)
            assertFalse(viewModel.uiState.value.choosing)
        }
    }

    private fun viewModel(): ProgressionRecommendationViewModel =
        ProgressionRecommendationViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RepFlowDestinations.PROGRESSION_EXERCISE_ARG to exerciseId.value)),
            repository = recommendations,
            getExercise = GetExercise(exercises),
            recordManualOverride = RecordManualOverride(recommendations, clock),
            observeReadiness = ObserveReadiness(recovery),
            clock = clock,
        )

    private suspend fun seed(
        result: ProgressionResult,
        reasons: List<String>,
        override: ManualOverride? = null,
    ) {
        val recommendation =
            success(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = exerciseId,
                    result = result,
                    reasons = reasons,
                    policyVersion = 1,
                    computedAt = computedAt,
                    manualOverride = override,
                ),
            )
        success(recommendations.insert(recommendation))
    }

    /** Heavy legs 4 and leg DOMS 3, so the readiness has drivers to name. */
    private suspend fun recordCheckIn(date: LocalDate) {
        val result =
            RecordRecoveryEntry(recovery, clock, SequentialIdentifierGenerator(prefix = "recovery-$date"))(
                RecordRecoveryEntryCommand(
                    date = date,
                    sleepQuality = 4,
                    energy = 3,
                    legDoms = 3,
                    heelStiffness = 1,
                    painWhileWalking = 0,
                    heavyLegs = 4,
                    futsalInPrevious24h = false,
                    futsalExpectedNext24h = false,
                    notes = null,
                ),
            )
        assertTrue("check-in not recorded: $result", result is DomainResult.Success)
    }

    private fun exercise(): Exercise =
        success(
            Exercise.create(
                id = exerciseId,
                name = success(ExerciseName.create("Back Squat")),
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = computedAt,
            ),
        )

    private suspend fun ReceiveTurbine<ProgressionRecommendationUiState>.awaitLoaded(
        predicate: (RecommendationContent.Loaded) -> Boolean = { true },
    ): RecommendationContent.Loaded {
        while (true) {
            val content = awaitItem().content
            if (content is RecommendationContent.Loaded && predicate(content)) return content
        }
    }

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }
}
