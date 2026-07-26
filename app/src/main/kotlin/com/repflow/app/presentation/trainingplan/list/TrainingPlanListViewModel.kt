package com.repflow.app.presentation.trainingplan.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Drives [TrainingPlanListUiState] from [ObserveTrainingPlans]. There is no
 * query/filter to resubscribe on (unlike `ExerciseListViewModel`), but the
 * same visible-recovery-on-retry shape is kept: a Room `Flow` that throws
 * terminates at the source, so [onRetry] resubscribes to a fresh `Flow` via
 * [retryTrigger] rather than relying on the dead one to recover itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrainingPlanListViewModel
    @Inject
    constructor(
        observeTrainingPlans: ObserveTrainingPlans,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)

        val uiState =
            retryTrigger
                .flatMapLatest {
                    observeTrainingPlans()
                        .map { overviews -> TrainingPlanListUiState(toContent(overviews)) }
                        .onStart { emit(TrainingPlanListUiState(TrainingPlanListContent.Loading)) }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            val failed = TrainingPlanListContent.ObservationFailed(TrainingPlanListFailureReason.UNKNOWN)
                            emit(TrainingPlanListUiState(failed))
                        }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = TrainingPlanListUiState(),
                )

        fun onRetry() {
            retryTrigger.update { it + 1 }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun toContent(overviews: List<TrainingPlanOverview>): TrainingPlanListContent =
    if (overviews.isEmpty()) {
        TrainingPlanListContent.Empty
    } else {
        TrainingPlanListContent.Content(overviews.map(::toListItem))
    }

private fun toListItem(overview: TrainingPlanOverview) =
    TrainingPlanListItem(
        id = overview.plan.id,
        name = overview.plan.name.value,
        plannedExerciseCount = overview.latestVersion.plannedExercises.size,
    )
