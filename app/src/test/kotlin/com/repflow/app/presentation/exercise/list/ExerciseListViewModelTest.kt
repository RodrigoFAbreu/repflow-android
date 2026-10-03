package com.repflow.app.presentation.exercise.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.ArchiveExercise
import com.repflow.app.application.exercise.ExercisePersistenceError
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.RestoreExercise
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.ArchiveTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveExercisePlanUsage
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseListViewModelTest {
    private val repository = InMemoryExerciseRepository()
    private val clock = FixedClock(Instant.parse("2026-01-03T00:00:00Z"))
    private val planRepository = InMemoryTrainingPlanRepository()
    private val viewModel =
        ExerciseListViewModel(
            ObserveExercises(repository),
            ArchiveExercise(repository, clock),
            RestoreExercise(repository, clock),
            ObserveExercisePlanUsage(planRepository),
        )

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun exercise(
        id: String,
        name: String,
        archived: Boolean = false,
    ): Exercise {
        val active =
            requireSuccess(
                Exercise.create(
                    id = ExerciseId(id),
                    name = requireSuccess(ExerciseName.create(name)),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            )
        return if (archived) active.archive(Instant.parse("2026-01-02T00:00:00Z")) else active
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    /**
     * Polls until [predicate] matches a genuinely settled state rather than
     * assuming a fixed emission count (Milestone 8, CP15 root-cause fix,
     * matching the pattern CP7/CP11/CP12 already established elsewhere):
     * `uiState` here is `combine(contentState, messages)`, and under
     * [UnconfinedTestDispatcher] the repository write inside an archive/
     * restore call and the separate `messages.update` it triggers can each
     * produce their own combine tick in either order, so [predicate] must
     * describe the final state across every field it cares about together -
     * never "the next item after N awaits."
     */
    private suspend fun ReceiveTurbine<ExerciseListUiState>.awaitUntil(predicate: (ExerciseListUiState) -> Boolean): ExerciseListUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }

    @Test
    fun `starts loading then shows the NO_EXERCISES empty state when there are none`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                assertEquals(ExerciseListContent.Loading, awaitItem().content)
                val loaded = awaitUntil { it.content !is ExerciseListContent.Loading }
                assertEquals(
                    ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES),
                    loaded.content,
                )
            }
        }

    @Test
    fun `shows the seeded active exercises as content`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.seed(exercise("2", "Bench Press"))

            viewModel.uiState.test {
                val loaded = awaitUntil { it.content !is ExerciseListContent.Loading }
                val content = loaded.content as ExerciseListContent.Content
                assertEquals(listOf("Bench Press", "Squat"), content.items.map { it.name })
            }
        }

    @Test
    fun `onFilterChanged switches between active and archived exercises`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.seed(exercise("2", "Retired Lift", archived = true))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                viewModel.onFilterChanged(ExerciseStatusFilter.ARCHIVED)

                val archived =
                    awaitUntil { it.filter == ExerciseStatusFilter.ARCHIVED && it.content !is ExerciseListContent.Loading }
                val content = archived.content as ExerciseListContent.Content
                assertEquals(listOf("Retired Lift"), content.items.map { it.name })
                assertEquals(ExerciseStatusFilter.ARCHIVED, archived.filter)
            }
        }

    @Test
    fun `an empty archived filter reports NO_ARCHIVED rather than NO_EXERCISES`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                viewModel.onFilterChanged(ExerciseStatusFilter.ARCHIVED)

                val archived =
                    awaitUntil { it.filter == ExerciseStatusFilter.ARCHIVED && it.content !is ExerciseListContent.Loading }
                assertEquals(
                    ExerciseListContent.Empty(ExerciseListEmptyReason.NO_ARCHIVED),
                    archived.content,
                )
            }
        }

    /** GF-2: on the Archived filter a query with no matches is "no results", not an empty archive. */
    @Test
    fun `a query with no matches on the archived filter reports NO_SEARCH_RESULTS`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.seed(exercise("2", "Old Press", archived = true))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading }

                viewModel.onFilterChanged(ExerciseStatusFilter.ARCHIVED)
                viewModel.onQueryChanged("zzz")

                val queried =
                    awaitUntil {
                        it.filter == ExerciseStatusFilter.ARCHIVED && it.query == "zzz" && it.content !is ExerciseListContent.Loading
                    }
                assertEquals(ExerciseListContent.Empty(ExerciseListEmptyReason.NO_SEARCH_RESULTS), queried.content)
            }
        }

    @Test
    fun `a query with no matches reports NO_SEARCH_RESULTS`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // content

                viewModel.onQueryChanged("nonexistent")

                val queried = awaitUntil { it.query == "nonexistent" && it.content !is ExerciseListContent.Loading }
                assertEquals(
                    ExerciseListContent.Empty(ExerciseListEmptyReason.NO_SEARCH_RESULTS),
                    queried.content,
                )
                assertEquals("nonexistent", queried.query)
            }
        }

    @Test
    fun `an observation failure is visible and retry resubscribes to fresh content`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.observeFailureOnNextSubscription = true

            viewModel.uiState.test {
                val failed = awaitUntil { it.content is ExerciseListContent.ObservationFailed }
                assertEquals(
                    ExerciseListContent.ObservationFailed(ExerciseListFailureReason.UNKNOWN),
                    failed.content,
                )

                viewModel.onRetry()

                val recovered = awaitUntil { it.content is ExerciseListContent.Content }
                val content = recovered.content as ExerciseListContent.Content
                assertEquals(listOf("Squat"), content.items.map { it.name })
            }
        }

    @Test
    fun `archiving queues an Archived message and removes the exercise from the active list`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content with Squat

                viewModel.onArchiveClicked(ExerciseId("1"))

                val settled =
                    awaitUntil {
                        it.messages.size == 1 &&
                            it.content == ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)
                    }
                val message = settled.messages.single()
                assertTrue(message is ExerciseListMessage.Archived)
                assertEquals(ExerciseId("1"), (message as ExerciseListMessage.Archived).exerciseId)
            }
        }

    @Test
    fun `onMessageShown removes only the given message id and preserves a newer one`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.seed(exercise("2", "Bench Press"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                viewModel.onArchiveClicked(ExerciseId("1"))
                val afterFirst =
                    awaitUntil {
                        it.messages.size == 1 &&
                            (it.content as? ExerciseListContent.Content)?.items?.map { item -> item.name } ==
                            listOf("Bench Press")
                    }
                val firstId = afterFirst.messages.single().id

                viewModel.onArchiveClicked(ExerciseId("2"))
                val afterSecond =
                    awaitUntil {
                        it.messages.size == 2 &&
                            it.content == ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)
                    }
                val secondId = afterSecond.messages.last().id

                viewModel.onMessageShown(firstId)
                val afterConsume = awaitUntil { it.messages.map { m -> m.id } == listOf(secondId) }
                assertEquals(listOf(secondId), afterConsume.messages.map { it.id })
            }
        }

    @Test
    fun `undo archive restores the exercise via RestoreExercise`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                viewModel.onArchiveClicked(ExerciseId("1"))
                awaitUntil {
                    it.messages.isNotEmpty() && it.content == ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)
                }

                viewModel.onRestoreClicked(ExerciseId("1"))
                val restored =
                    awaitUntil {
                        (it.content as? ExerciseListContent.Content)?.items?.map { item -> item.name } == listOf("Squat")
                    }
                val content = restored.content as ExerciseListContent.Content
                assertEquals(listOf("Squat"), content.items.map { it.name })
            }
        }

    @Test
    fun `undo archive is idempotent when the exercise is already active`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                // Squat is already active; undo should be a neutral no-op, not an error message.
                viewModel.onRestoreClicked(ExerciseId("1"))
                expectNoEvents()
            }
        }

    @Test
    fun `a failed archive queues an OperationFailed message`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat"))
            repository.nextUpdateFailure = ExercisePersistenceError.Unavailable

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active content

                viewModel.onArchiveClicked(ExerciseId("1"))

                val messaged = awaitUntil { it.messages.isNotEmpty() }
                assertEquals(1, messaged.messages.size)
                assertTrue(messaged.messages.single() is ExerciseListMessage.OperationFailed)
            }
        }

    @Test
    fun `restoring from the archived filter moves the exercise back to active`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("1", "Squat", archived = true))

            viewModel.uiState.test {
                awaitUntil { it.content !is ExerciseListContent.Loading } // active filter, empty (Squat is archived)

                viewModel.onFilterChanged(ExerciseStatusFilter.ARCHIVED)
                val archivedList =
                    awaitUntil { it.filter == ExerciseStatusFilter.ARCHIVED && it.content !is ExerciseListContent.Loading }
                val archivedContent = archivedList.content as ExerciseListContent.Content
                assertEquals(listOf("Squat"), archivedContent.items.map { it.name })

                viewModel.onRestoreClicked(ExerciseId("1"))
                val afterRestore = awaitUntil { it.content == ExerciseListContent.Empty(ExerciseListEmptyReason.NO_ARCHIVED) }
                assertEquals(
                    ExerciseListContent.Empty(ExerciseListEmptyReason.NO_ARCHIVED),
                    afterRestore.content,
                )
            }
        }

    /**
     * Remediation-1 CP10: each row's `in N plans` counts the non-archived
     * plans that hold the exercise, and follows the plans live - archiving a
     * plan drops it from the count without the list being re-opened.
     */
    @Test
    fun `plan usage counts the non-archived plans that hold an exercise`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            repository.seed(exercise("bench", "Bench Press"))
            repository.seed(exercise("squat", "Squat"))
            val createPlan = CreateTrainingPlan(planRepository, repository, clock, SequentialIdentifierGenerator("plan"))
            requireSuccess(createPlan(planCommand("Push Day", "bench")))
            val archivedPlanId = requireSuccess(createPlan(planCommand("Old Push", "bench")))

            viewModel.uiState.test {
                val bothPlans =
                    awaitUntil { state ->
                        (state.content as? ExerciseListContent.Content)?.items?.any { it.planUsageCount == 2 } == true
                    }
                assertEquals(
                    mapOf("Bench Press" to 2, "Squat" to 0),
                    (bothPlans.content as ExerciseListContent.Content).items.associate { it.name to it.planUsageCount },
                )

                requireSuccess(ArchiveTrainingPlan(planRepository, clock)(archivedPlanId))

                val oneArchived =
                    awaitUntil { state ->
                        (state.content as? ExerciseListContent.Content)?.items?.any { it.planUsageCount == 1 } == true
                    }
                assertEquals(
                    mapOf("Bench Press" to 1, "Squat" to 0),
                    (oneArchived.content as ExerciseListContent.Content).items.associate { it.name to it.planUsageCount },
                )
            }
        }

    private fun planCommand(
        name: String,
        exerciseId: String,
    ) = CreateTrainingPlanCommand(
        name = name,
        plannedExercises =
            listOf(
                PlannedExerciseInput(
                    exerciseId = exerciseId,
                    order = 0,
                    targetSets = 3,
                    targetKind = PlannedExerciseTargetKind.REPS,
                    repMin = 8,
                    repMax = 12,
                    durationMinSeconds = null,
                    durationMaxSeconds = null,
                    restSeconds = 90,
                    isOptional = false,
                ),
            ),
    )
}
