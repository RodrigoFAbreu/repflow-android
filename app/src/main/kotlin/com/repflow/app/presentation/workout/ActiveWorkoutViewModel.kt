package com.repflow.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.EditLastWorkoutSetCommand
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.RecordWorkoutSetCommand
import com.repflow.app.application.workout.RestTimerAdjustment
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [ActiveWorkoutUiState] from [ObserveActiveWorkoutSession] and
 * [ObserveExercises], and dispatches the active-workout use cases: starting,
 * adding an ad hoc exercise, recording/undoing/editing a set, and
 * completing/abandoning the session.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class ActiveWorkoutViewModel
    @Inject
    constructor(
        observeActiveWorkoutSession: ObserveActiveWorkoutSession,
        observeExercises: ObserveExercises,
        private val getWorkoutDayContext: GetWorkoutDayContext,
        private val startWorkoutSession: StartWorkoutSession,
        private val addWorkoutExercise: AddWorkoutExercise,
        private val recordWorkoutSet: RecordWorkoutSet,
        private val undoLastWorkoutSet: UndoLastWorkoutSet,
        private val editLastWorkoutSet: EditLastWorkoutSet,
        private val startRestTimer: StartRestTimer,
        private val adjustRestTimer: AdjustRestTimer,
        private val skipRestTimer: SkipRestTimer,
        private val completeWorkoutSession: CompleteWorkoutSession,
        private val abandonWorkoutSession: AbandonWorkoutSession,
    ) : ViewModel() {
        private val error = MutableStateFlow<ActiveWorkoutErrorReason?>(null)
        private val _dayContext = MutableStateFlow<WorkoutDayContextUi?>(null)
        val dayContext: StateFlow<WorkoutDayContextUi?> = _dayContext

        init {
            viewModelScope.launch {
                val context = getWorkoutDayContext()
                _dayContext.value =
                    WorkoutDayContextUi(
                        heavyLegs = context.latestRecoveryEntry?.heavyLegs,
                        legDoms = context.latestRecoveryEntry?.legDoms,
                        futsalLoad = context.recentFutsalSession?.load,
                    )
            }
        }

        private val content =
            observeActiveWorkoutSession()
                .map { session -> toContent(session) }
                .onStart { emit(ActiveWorkoutContent.Loading) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(ActiveWorkoutContent.ObservationFailed(ActiveWorkoutErrorReason.UNKNOWN))
                }

        private val availableExercises =
            observeExercises(ExerciseStatusFilter.ACTIVE, "")
                .map { exercises -> exercises.map(::toPickerItem) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(emptyList())
                }

        val uiState =
            combine(content, availableExercises, error) { observed, exercises, err ->
                ActiveWorkoutUiState(observed, exercises, err)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ActiveWorkoutUiState(),
            )

        fun onStartWorkout() {
            launchAction { startWorkoutSession(StartWorkoutSessionCommand(trainingPlanVersionId = null)) }
        }

        fun onAddExercise(exercise: ExercisePickerItem) {
            val sessionId = activeSessionId() ?: return
            launchAction {
                addWorkoutExercise(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        exerciseNameSnapshot = exercise.name,
                        trackingType = exercise.trackingType,
                        plannedExerciseId = null,
                    ),
                )
            }
        }

        fun onRecordSet(
            exerciseId: WorkoutExerciseId,
            load: Double?,
            reps: Int?,
        ) {
            val sessionId = activeSessionId() ?: return
            launchAction {
                val result =
                    recordWorkoutSet(
                        RecordWorkoutSetCommand(
                            sessionId = sessionId,
                            exerciseId = exerciseId,
                            load = load,
                            reps = reps,
                            durationSeconds = null,
                            rpe = null,
                            isWarmup = false,
                        ),
                    )
                if (result is DomainResult.Success) startRestTimer(sessionId)
                result
            }
        }

        fun onAddRestTime(seconds: Long) {
            val sessionId = activeSessionId() ?: return
            launchAction { adjustRestTimer(sessionId, RestTimerAdjustment.ADD, seconds) }
        }

        fun onRemoveRestTime(seconds: Long) {
            val sessionId = activeSessionId() ?: return
            launchAction { adjustRestTimer(sessionId, RestTimerAdjustment.REMOVE, seconds) }
        }

        fun onSkipRestTimer() {
            val sessionId = activeSessionId() ?: return
            launchAction { skipRestTimer(sessionId) }
        }

        fun onUndoLastSet(exerciseId: WorkoutExerciseId) {
            val sessionId = activeSessionId() ?: return
            launchAction { undoLastWorkoutSet(sessionId, exerciseId) }
        }

        fun onEditLastSet(
            exerciseId: WorkoutExerciseId,
            load: Double?,
            reps: Int?,
        ) {
            val sessionId = activeSessionId() ?: return
            launchAction {
                editLastWorkoutSet(
                    EditLastWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        load = load,
                        reps = reps,
                        durationSeconds = null,
                        rpe = null,
                        isWarmup = false,
                    ),
                )
            }
        }

        fun onCompleteWorkout(sessionId: WorkoutSessionId) {
            launchAction { completeWorkoutSession(sessionId) }
        }

        fun onAbandonWorkout(sessionId: WorkoutSessionId) {
            launchAction { abandonWorkoutSession(sessionId) }
        }

        fun onErrorShown() {
            error.update { null }
        }

        private fun activeSessionId(): WorkoutSessionId? = (uiState.value.content as? ActiveWorkoutContent.Active)?.sessionId

        private fun launchAction(action: suspend () -> DomainResult<*, WorkoutOperationError>) {
            viewModelScope.launch {
                when (val result = action()) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> error.update { result.error.toReason() }
                }
            }
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
            restTimer = session.restTimer?.toUi(),
            exercises =
                session.exercises.map { exercise ->
                    ActiveExerciseUi(
                        id = exercise.id,
                        name = exercise.exerciseNameSnapshot,
                        trackingType = exercise.trackingType,
                        sets =
                            exercise.sets.map { set ->
                                ActiveSetUi(
                                    id = set.id,
                                    setNumber = set.order + 1,
                                    load = set.load,
                                    reps = set.reps,
                                    durationSeconds = set.durationSeconds,
                                )
                            },
                    )
                },
        )
    }

private fun RestTimer.toUi(): RestTimerUi = RestTimerUi(endAt = endAt, totalDurationSeconds = totalDurationSeconds)

private fun toPickerItem(exercise: Exercise): ExercisePickerItem =
    ExercisePickerItem(id = exercise.id, name = exercise.name.value, trackingType = exercise.trackingType)

private fun WorkoutOperationError.toReason(): ActiveWorkoutErrorReason =
    when (this) {
        WorkoutOperationError.ActiveSessionAlreadyExists -> ActiveWorkoutErrorReason.ALREADY_ACTIVE
        WorkoutOperationError.NotFound -> ActiveWorkoutErrorReason.NOT_FOUND
        is WorkoutOperationError.ValidationFailed -> ActiveWorkoutErrorReason.VALIDATION_FAILED
        WorkoutOperationError.PersistenceUnavailable -> ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE
    }
