package com.repflow.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.repflow.app.application.history.BestSet
import com.repflow.app.application.history.ObserveWorkoutSummary
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.navigation.RepFlowDestinations
import com.repflow.app.presentation.progression.reasonAfterDash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The done screen's plain values and its ViewModel (remediation-1 CP9): the
 * recap rows' runs and deltas (`D67`), the tiles' counts, and the rule that
 * only this completion's recommendations are listed (`D37`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDoneTest {
    private val startedAt = Instant.parse("2026-08-11T17:12:00Z")
    private val endedAt = startedAt.plusSeconds(3_000)
    private val squat = ExerciseId("squat")
    private val plank = ExerciseId("plank")

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun consecutiveWorkingSetsAtOneLoadReadAsOneRunAndALoadChangeStartsAnother() {
        val groups =
            recapGroups(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                listOf(set(0, 80.0, 8), set(1, 80.0, 8), set(2, 82.5, 6), set(3, 80.0, 7), set(4, null, 10)),
            )

        assertEquals(
            listOf(
                RecapGroup.Loaded(BigDecimal.valueOf(80.0), listOf(8, 8)),
                RecapGroup.Loaded(BigDecimal.valueOf(82.5), listOf(6)),
                RecapGroup.Loaded(BigDecimal.valueOf(80.0), listOf(7)),
                RecapGroup.Unloaded(listOf(10)),
            ),
            groups,
        )
        assertEquals(
            listOf(RecapGroup.Timed(listOf(45, 40))),
            recapGroups(ExerciseTrackingType.DURATION, listOf(hold(0, 45), hold(1, 40))),
        )
    }

    @Test
    fun theDeltaComparesLikeWithLikeAndSaysSoWhenItCannot() {
        assertEquals(RecapDelta.NoWorkingSets, recapDelta(best = null, lastTime = BestSet.Reps(5)))
        assertEquals(RecapDelta.FirstTime, recapDelta(BestSet.Reps(5), lastTime = null))
        assertEquals(RecapDelta.FirstTime, recapDelta(BestSet.Load(20.0, 5), BestSet.Reps(8)))
        assertEquals(RecapDelta.Load(BigDecimal("2.5")), recapDelta(BestSet.Load(82.5, 6), BestSet.Load(80.0, 8)))
        assertEquals(RecapDelta.Load(BigDecimal("-0.1")), recapDelta(BestSet.Load(80.0, 6), BestSet.Load(80.1, 6)))
        // Same load: the reps at that load decide (D67), so a rep gain is a gain, not a grey "same load".
        assertEquals(RecapDelta.SameLoadReps(2), recapDelta(BestSet.Load(80.0, 10), BestSet.Load(80.0, 8)))
        assertEquals(RecapDelta.SameLoadReps(-1), recapDelta(BestSet.Load(80.0, 7), BestSet.Load(80.0, 8)))
        assertEquals(0, (recapDelta(BestSet.Load(80.0, 8), BestSet.Load(80.0, 8)) as RecapDelta.Load).kg.signum())
        assertEquals(RecapDelta.Load(BigDecimal("2.5")), recapDelta(BestSet.Load(82.5, 3), BestSet.Load(80.0, 8)))
        assertTrue(RecapDelta.SameLoadReps(2).isGain())
        assertFalse(RecapDelta.SameLoadReps(-1).isGain())
        assertEquals(RecapDelta.Reps(-2), recapDelta(BestSet.Reps(8), BestSet.Reps(10)))
        assertEquals(RecapDelta.Seconds(0), recapDelta(BestSet.Seconds(60), BestSet.Seconds(60)))
    }

    @Test
    fun theDoneScreenListsOnlyTheRecommendationsThisCompletionComputed() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val workouts = InMemoryWorkoutRepository()
            val recommendations = InMemoryProgressionRecommendationRepository()
            val session =
                session(
                    exercises =
                        listOf(
                            exercise(
                                0,
                                squat,
                                ExerciseTrackingType.WEIGHT_AND_REPS,
                                listOf(set(0, 100.0, 5), set(1, 40.0, 10, warmup = true)),
                            ),
                            exercise(1, plank, ExerciseTrackingType.DURATION, emptyList()),
                        ),
                )
            workouts.insert(session)
            recommendations.insert(recommendation("current", squat, endedAt.plusSeconds(1)))
            recommendations.insert(recommendation("stale", plank, endedAt.minusSeconds(86_400)))
            // Computed at completion for an exercise nothing was logged on: not listed (functional review J2).
            recommendations.insert(recommendation("untrained", plank, endedAt.plusSeconds(1), ProgressionResult.WaitForMoreData))
            val viewModel =
                WorkoutDoneViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(RepFlowDestinations.WORKOUT_DONE_ARG to session.id.value)),
                    observeWorkoutSummary = ObserveWorkoutSummary(workouts),
                    observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(InMemoryTrainingPlanRepository()),
                    progressionRecommendationRepository = recommendations,
                )

            viewModel.uiState.test {
                var content = awaitItem().content
                while (content !is WorkoutDoneContent.Loaded) content = awaitItem().content

                assertEquals(listOf(squat), content.recommendations.map { it.exerciseId })
                assertEquals(1, content.workingSets)
                assertEquals(1, content.exercisesTrained)
                assertEquals(2, content.exerciseCount)
                assertEquals(listOf(RecapDelta.FirstTime, RecapDelta.NoWorkingSets), content.recaps.map { it.delta })
                assertEquals(listOf(1, 0), content.recaps.map { it.warmups })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aReasonContinuesTheLineAfterTheDashInLowerCase() {
        assertEquals("fewer than 2 working sets recorded", reasonAfterDash("Fewer than 2 working sets recorded"))
        assertEquals("pain while walking is elevated (4/5)", reasonAfterDash("Pain while walking is elevated (4/5)"))
        assertEquals("", reasonAfterDash(""))
    }

    @Test
    fun anIdWithNoCompletedSessionIsNotFound() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val viewModel =
                WorkoutDoneViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(RepFlowDestinations.WORKOUT_DONE_ARG to "missing")),
                    observeWorkoutSummary = ObserveWorkoutSummary(InMemoryWorkoutRepository()),
                    observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(InMemoryTrainingPlanRepository()),
                    progressionRecommendationRepository = InMemoryProgressionRecommendationRepository(),
                )

            viewModel.uiState.test {
                var content = awaitItem().content
                while (content == WorkoutDoneContent.Loading) content = awaitItem().content
                assertEquals(WorkoutDoneContent.NotFound, content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun session(exercises: List<WorkoutExercise>): WorkoutSession =
        success(
            WorkoutSession.reconstruct(
                id = SESSION_ID,
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = endedAt,
                exercises = exercises,
            ),
        )

    private fun exercise(
        order: Int,
        exerciseId: ExerciseId,
        tracking: ExerciseTrackingType,
        sets: List<WorkoutSet>,
    ): WorkoutExercise =
        success(
            WorkoutExercise.create(
                id = WorkoutExerciseId("we-$order"),
                sessionId = SESSION_ID,
                exerciseId = exerciseId,
                order = order,
                exerciseNameSnapshot = exerciseId.value,
                trackingType = tracking,
                plannedExerciseId = null,
                sets = sets,
            ),
        )

    private fun set(
        order: Int,
        load: Double?,
        reps: Int,
        warmup: Boolean = false,
    ): WorkoutSet =
        success(
            WorkoutSet.create(
                id = WorkoutSetId("set-$order"),
                order = order,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                load = load,
                reps = reps,
                durationSeconds = null,
                rpe = null,
                isWarmup = warmup,
                createdAt = startedAt,
                updatedAt = startedAt,
            ),
        )

    private fun hold(
        order: Int,
        seconds: Int,
    ): WorkoutSet =
        success(
            WorkoutSet.create(
                id = WorkoutSetId("hold-$order"),
                order = order,
                trackingType = ExerciseTrackingType.DURATION,
                load = null,
                reps = null,
                durationSeconds = seconds,
                rpe = null,
                isWarmup = false,
                createdAt = startedAt,
                updatedAt = startedAt,
            ),
        )

    private fun recommendation(
        id: String,
        exerciseId: ExerciseId,
        computedAt: Instant,
        result: ProgressionResult = ProgressionResult.MaintainLoad,
    ): ProgressionRecommendation =
        success(
            ProgressionRecommendation.create(
                id = ProgressionRecommendationId(id),
                exerciseId = exerciseId,
                result = result,
                reasons = listOf("Average RPE was 8."),
                policyVersion = 1,
                computedAt = computedAt,
            ),
        )

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private companion object {
        val SESSION_ID = WorkoutSessionId("session-done")
    }
}
