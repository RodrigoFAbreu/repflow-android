package com.repflow.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [ActiveWorkoutUiState] from [ObserveActiveWorkoutSession], and
 * dispatches [StartWorkoutSession]/[CompleteWorkoutSession]/
 * [AbandonWorkoutSession]. Mirrors
 * [com.repflow.app.presentation.trainingplan.list.TrainingPlanListViewModel]'s
 * shape for observation, plus a one-off action-error surfaced alongside the
 * observed [ActiveWorkoutContent] on [ActiveWorkoutUiState].
 */
@HiltViewModel
class ActiveWorkoutViewModel
    @Inject
    constructor(
        observeActiveWorkoutSession: ObserveActiveWorkoutSession,
        private val startWorkoutSession: StartWorkoutSession,
        private val completeWorkoutSession: CompleteWorkoutSession,
        private val abandonWorkoutSession: AbandonWorkoutSession,
    ) : ViewModel() {
        private val error = MutableStateFlow<ActiveWorkoutErrorReason?>(null)

        private val content =
            observeActiveWorkoutSession()
                .map { session -> toContent(session) }
                .onStart { emit(ActiveWorkoutContent.Loading) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(ActiveWorkoutContent.ObservationFailed(ActiveWorkoutErrorReason.UNKNOWN))
                }

        val uiState =
            combine(content, error) { observed, err -> ActiveWorkoutUiState(observed, err) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = ActiveWorkoutUiState(),
                )

        fun onStartWorkout() {
            viewModelScope.launch {
                when (val result = startWorkoutSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> error.update { result.error.toReason() }
                }
            }
        }

        fun onCompleteWorkout(sessionId: WorkoutSessionId) {
            viewModelScope.launch {
                when (val result = completeWorkoutSession(sessionId)) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> error.update { result.error.toReason() }
                }
            }
        }

        fun onAbandonWorkout(sessionId: WorkoutSessionId) {
            viewModelScope.launch {
                when (val result = abandonWorkoutSession(sessionId)) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> error.update { result.error.toReason() }
                }
            }
        }

        fun onErrorShown() {
            error.update { null }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun toContent(session: WorkoutSession?): ActiveWorkoutContent =
    if (session == null) {
        ActiveWorkoutContent.NoActiveSession
    } else {
        ActiveWorkoutContent.Active(
            sessionId = session.id,
            startedAt = session.startedAt,
            exerciseCount = session.exercises.size,
            setCount = session.exercises.sumOf { it.sets.size },
        )
    }

private fun WorkoutOperationError.toReason(): ActiveWorkoutErrorReason =
    when (this) {
        WorkoutOperationError.ActiveSessionAlreadyExists -> ActiveWorkoutErrorReason.ALREADY_ACTIVE
        WorkoutOperationError.NotFound -> ActiveWorkoutErrorReason.NOT_FOUND
        is WorkoutOperationError.ValidationFailed -> ActiveWorkoutErrorReason.VALIDATION_FAILED
        WorkoutOperationError.PersistenceUnavailable -> ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE
    }
