package com.repflow.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.history.ObserveWorkoutSummary
import com.repflow.app.application.history.WorkoutSummary
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.navigation.RepFlowDestinations
import com.repflow.app.presentation.progression.toSummaryUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Drives the done screen (remediation-1 CP9) for the session named by the
 * route's [RepFlowDestinations.WORKOUT_DONE_ARG] - the id the finish sheet's
 * confirm handed over once `CompleteWorkoutSession` succeeded.
 *
 * The summary and the plan name are observed; the recommendations are read
 * per exercise from [ProgressionRecommendationRepository], as the workout's
 * picker reads them, and re-read on every `ON_START` ([onRefreshRecommendations])
 * so a choice recorded on the recommendation screen shows on return.
 *
 * **Only this completion's recommendations are listed** (`D37`): one computed
 * at or after the session ended. `CompleteWorkoutSession` computes them after
 * storing the session, best-effort, so an exercise whose computation failed
 * shows none rather than an older one passed off as this workout's.
 */
@HiltViewModel
class WorkoutDoneViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeWorkoutSummary: ObserveWorkoutSummary,
        observeTrainingPlanVersionLabels: ObserveTrainingPlanVersionLabels,
        private val progressionRecommendationRepository: ProgressionRecommendationRepository,
    ) : ViewModel() {
        private val sessionId =
            WorkoutSessionId(checkNotNull(savedStateHandle.get<String>(RepFlowDestinations.WORKOUT_DONE_ARG)))
        private val recommendationRefreshTrigger = MutableStateFlow(0)

        private val planNames =
            observeTrainingPlanVersionLabels()
                .map { labels -> labels.mapValues { it.value.planName } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(emptyMap())
                }

        val uiState: StateFlow<WorkoutDoneUiState> =
            combine(observeWorkoutSummary(sessionId), planNames, recommendationRefreshTrigger) { summary, names, _ ->
                if (summary == null) {
                    WorkoutDoneContent.NotFound
                } else {
                    summary.toLoaded(
                        planName = summary.session.trainingPlanVersionId?.let { names[it] },
                        recommendations = recommendationsFor(summary),
                    )
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(WorkoutDoneContent.Failed)
            }.map(::WorkoutDoneUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = WorkoutDoneUiState(),
                )

        /** Re-reads the recommendations; the route calls it on every `ON_START`. */
        fun onRefreshRecommendations() {
            recommendationRefreshTrigger.update { it + 1 }
        }

        private suspend fun recommendationsFor(summary: WorkoutSummary): List<DoneRecommendationUi> {
            val endedAt = checkNotNull(summary.session.endedAt)
            return summary.session.exercises
                // An exercise with no working set this time has nothing to suggest about: its
                // `Not enough data yet` row is noise, repeated for every exercise left untouched.
                // Filtered before deduplicating, so a trained later entry of a repeated exercise
                // is not shadowed by an untrained earlier one.
                .filter { exercise -> exercise.sets.any { !it.isWarmup } }
                .distinctBy { it.exerciseId }
                .mapNotNull { exercise ->
                    progressionRecommendationRepository
                        .findLatestForExercise(exercise.exerciseId)
                        ?.takeIf { !it.computedAt.isBefore(endedAt) }
                        ?.let { DoneRecommendationUi(exercise.exerciseId, exercise.exerciseNameSnapshot, it.toSummaryUi()) }
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
