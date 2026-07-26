package com.repflow.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.progression.RecordManualOverride
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
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
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
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
        observeTrainingPlans: ObserveTrainingPlans,
        private val getExercise: GetExercise,
        private val getWorkoutDayContext: GetWorkoutDayContext,
        private val progressionRecommendationRepository: ProgressionRecommendationRepository,
        private val recordManualOverride: RecordManualOverride,
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
        private val recommendationRefreshTrigger = MutableStateFlow(0)

        /**
         * Milestone 8, CP6: a plain `MutableStateFlow` eagerly kept in sync from
         * `init`, mirroring [_dayContext] - not a nested `stateIn`, since a
         * second independent `WhileSubscribed` `stateIn` racing the outer
         * [uiState] combine's own subscription can start observing before this
         * one's upstream collection has produced its first value, making
         * [onStartWorkout]'s synchronous `.value` lookup see a stale/empty list
         * right after construction. Collecting eagerly here means it's always
         * caught up by the time a caller reads `.value`.
         */
        private val trainingPlanOverviewsFlow = MutableStateFlow<List<TrainingPlanOverview>>(emptyList())

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
            viewModelScope.launch {
                observeTrainingPlans(TrainingPlanStatusFilter.ACTIVE)
                    .catch { failure -> if (failure is CancellationException) throw failure }
                    .collect { overviews -> trainingPlanOverviewsFlow.value = overviews }
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
            combine(
                observeExercises(ExerciseStatusFilter.ACTIVE, ""),
                recommendationRefreshTrigger,
            ) { exercises, _ -> exercises }
                .map { exercises -> exercises.map { exercise -> toPickerItem(exercise, latestRecommendationUi(exercise.id)) } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(emptyList())
                }

        private val availablePlans =
            trainingPlanOverviewsFlow.map { overviews ->
                overviews.map { overview -> TrainingPlanPickerItem(overview.latestVersion.id, overview.plan.name.value) }
            }

        val uiState =
            combine(content, availableExercises, availablePlans, error) { observed, exercises, plans, err ->
                ActiveWorkoutUiState(observed, exercises, plans, err)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ActiveWorkoutUiState(),
            )

        /**
         * Starts a new session, optionally from [planVersionId]. Ad-hoc
         * (`null`) remains fully supported - it's the only path this used to
         * have. When a plan is chosen, every one of its planned exercises is
         * added right after the session starts, each carrying its
         * [com.repflow.app.domain.trainingplan.PlannedExercise.id] so
         * [com.repflow.app.application.workout.CompleteWorkoutSession] can
         * later resolve a planned rep range for progression (Milestone 8, CP6).
         */
        fun onStartWorkout(planVersionId: TrainingPlanVersionId? = null) {
            val overview = planVersionId?.let { id -> trainingPlanOverviewsFlow.value.find { it.latestVersion.id == id } }
            launchAction {
                val result = startWorkoutSession(StartWorkoutSessionCommand(trainingPlanVersionId = planVersionId))
                if (result is DomainResult.Success && overview != null) {
                    seedPlannedExercises(result.value, overview)
                }
                result
            }
        }

        private suspend fun seedPlannedExercises(
            sessionId: WorkoutSessionId,
            overview: TrainingPlanOverview,
        ) {
            for (plannedExercise in overview.latestVersion.plannedExercises.sortedBy { it.order }) {
                val exercise =
                    (getExercise(plannedExercise.exerciseId) as? DomainResult.Success)?.value ?: continue
                addWorkoutExercise(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        exerciseNameSnapshot = exercise.name.value,
                        trackingType = exercise.trackingType,
                        plannedExerciseId = plannedExercise.id,
                    ),
                )
            }
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

        @Suppress("LongParameterList")
        fun onRecordSet(
            exerciseId: WorkoutExerciseId,
            load: Double?,
            reps: Int?,
            durationSeconds: Int? = null,
            rpe: Double? = null,
            isWarmup: Boolean = false,
            pain: Int? = null,
            techniqueQuality: Int? = null,
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
                            durationSeconds = durationSeconds,
                            rpe = rpe,
                            isWarmup = isWarmup,
                            pain = pain,
                            techniqueQuality = techniqueQuality,
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

        @Suppress("LongParameterList")
        fun onEditLastSet(
            exerciseId: WorkoutExerciseId,
            load: Double?,
            reps: Int?,
            durationSeconds: Int? = null,
            rpe: Double? = null,
            isWarmup: Boolean = false,
            pain: Int? = null,
            techniqueQuality: Int? = null,
        ) {
            val sessionId = activeSessionId() ?: return
            launchAction {
                editLastWorkoutSet(
                    EditLastWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        load = load,
                        reps = reps,
                        durationSeconds = durationSeconds,
                        rpe = rpe,
                        isWarmup = isWarmup,
                        pain = pain,
                        techniqueQuality = techniqueQuality,
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

        fun onOverrideRecommendation(
            exerciseId: ExerciseId,
            override: ProgressionResultUi,
        ) {
            viewModelScope.launch {
                recordManualOverride(exerciseId, override.toDomain())
                recommendationRefreshTrigger.update { it + 1 }
            }
        }

        private suspend fun latestRecommendationUi(exerciseId: ExerciseId): ProgressionRecommendationUi? =
            progressionRecommendationRepository.findLatestForExercise(exerciseId)?.let { recommendation ->
                ProgressionRecommendationUi(
                    result = (recommendation.manualOverride?.result ?: recommendation.result).toUi(),
                    topReason = recommendation.reasons.firstOrNull(),
                    isOverridden = recommendation.manualOverride != null,
                )
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
                                    rpe = set.rpe,
                                    isWarmup = set.isWarmup,
                                    pain = set.pain,
                                    techniqueQuality = set.techniqueQuality,
                                )
                            },
                    )
                },
        )
    }

private fun RestTimer.toUi(): RestTimerUi = RestTimerUi(endAt = endAt, totalDurationSeconds = totalDurationSeconds)

private fun toPickerItem(
    exercise: Exercise,
    recommendation: ProgressionRecommendationUi?,
): ExercisePickerItem =
    ExercisePickerItem(
        id = exercise.id,
        name = exercise.name.value,
        trackingType = exercise.trackingType,
        recommendation = recommendation,
    )

private fun ProgressionResult.toUi(): ProgressionResultUi =
    when (this) {
        ProgressionResult.IncreaseLoad -> ProgressionResultUi.INCREASE_LOAD
        ProgressionResult.MaintainLoad -> ProgressionResultUi.MAINTAIN_LOAD
        ProgressionResult.ReduceLoad -> ProgressionResultUi.REDUCE_LOAD
        ProgressionResult.RecoveryAdjustment -> ProgressionResultUi.RECOVERY_ADJUSTMENT
        ProgressionResult.WaitForMoreData -> ProgressionResultUi.WAIT_FOR_MORE_DATA
    }

private fun ProgressionResultUi.toDomain(): ProgressionResult =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> ProgressionResult.IncreaseLoad
        ProgressionResultUi.MAINTAIN_LOAD -> ProgressionResult.MaintainLoad
        ProgressionResultUi.REDUCE_LOAD -> ProgressionResult.ReduceLoad
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> ProgressionResult.RecoveryAdjustment
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> ProgressionResult.WaitForMoreData
    }

private fun WorkoutOperationError.toReason(): ActiveWorkoutErrorReason =
    when (this) {
        WorkoutOperationError.ActiveSessionAlreadyExists -> ActiveWorkoutErrorReason.ALREADY_ACTIVE

        WorkoutOperationError.NotFound -> ActiveWorkoutErrorReason.NOT_FOUND

        is WorkoutOperationError.ValidationFailed -> ActiveWorkoutErrorReason.VALIDATION_FAILED

        WorkoutOperationError.PersistenceUnavailable -> ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE

        // Never returned by any use case this ViewModel calls (only InvalidateWorkoutSession
        // returns it, which is History's concern) - mapped for `when` exhaustiveness.
        WorkoutOperationError.AlreadyInvalidated -> ActiveWorkoutErrorReason.UNKNOWN
    }
