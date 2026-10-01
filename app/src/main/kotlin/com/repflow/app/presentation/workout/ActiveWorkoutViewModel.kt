package com.repflow.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.DEFAULT_REST_TIMER_SECONDS
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
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.StartWorkoutSessionFromPlanCommand
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import com.repflow.app.presentation.progression.toSummaryUi
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
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Drives [ActiveWorkoutUiState] from [ObserveActiveWorkoutSession] and
 * [ObserveExercises], and dispatches the active-workout use cases: starting,
 * adding an ad hoc exercise, recording/undoing/editing a set, and
 * completing/abandoning the session.
 *
 * Since remediation-1 CP7 the workout surface has no start menu - Home starts
 * every workout (CP5) - so [onStartWorkout] and `availablePlans` have no
 * screen consumer. They are kept, with their tests, as the plan's JVM pass
 * over this package states; CP16's sweep decides whether they go.
 *
 * Remediation-1 CP14: Settings gates three of this surface's behaviours, read
 * from [SettingsRepository]. [onRecordSet] starts the rest timer only while
 * `Start rest timer automatically` is on, reading the switch when the set is
 * logged; [settings] carries the rest to the screen (keep screen awake, confirm
 * before finishing) and [notificationEnabled] to the route's permission prompt
 * - both `null` until the repository first emits, never a stored default.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class ActiveWorkoutViewModel
    @Inject
    constructor(
        observeActiveWorkoutSession: ObserveActiveWorkoutSession,
        observeExercises: ObserveExercises,
        observeTrainingPlans: ObserveTrainingPlans,
        private val trainingPlanRepository: TrainingPlanRepository,
        private val getWorkoutDayContext: GetWorkoutDayContext,
        private val progressionRecommendationRepository: ProgressionRecommendationRepository,
        private val startWorkoutSession: StartWorkoutSession,
        private val startWorkoutSessionFromPlan: StartWorkoutSessionFromPlan,
        private val addWorkoutExercise: AddWorkoutExercise,
        private val recordWorkoutSet: RecordWorkoutSet,
        private val undoLastWorkoutSet: UndoLastWorkoutSet,
        private val editLastWorkoutSet: EditLastWorkoutSet,
        private val startRestTimer: StartRestTimer,
        private val adjustRestTimer: AdjustRestTimer,
        private val skipRestTimer: SkipRestTimer,
        private val completeWorkoutSession: CompleteWorkoutSession,
        private val abandonWorkoutSession: AbandonWorkoutSession,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val error = MutableStateFlow<ActiveWorkoutErrorReason?>(null)
        private val _dayContext = MutableStateFlow<WorkoutDayContextUi?>(null)
        val dayContext: StateFlow<WorkoutDayContextUi?> = _dayContext
        private val recommendationRefreshTrigger = MutableStateFlow(0)
        private val _finish = MutableStateFlow<WorkoutFinishState>(WorkoutFinishState.Idle)

        /** The finish sheet's confirm, from request to the completed session's id (remediation-1 CP9). */
        val finish: StateFlow<WorkoutFinishState> = _finish

        private val _settings = MutableStateFlow<AppSettings?>(null)

        /** The device's settings, `null` until [SettingsRepository] first emits (remediation-1 CP14). */
        val settings: StateFlow<AppSettings?> = _settings

        /**
         * The Notification switch for the route's permission prompt: `null` until
         * it has loaded, so nothing is asked before the switch's real value is
         * known (remediation-1 CP14).
         */
        val notificationEnabled: StateFlow<Boolean?> =
            _settings
                .map { it?.restTimerNotification }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
                settingsRepository
                    .observe()
                    .catch { failure -> if (failure is CancellationException) throw failure }
                    .collect { value -> _settings.value = value }
            }
            viewModelScope.launch {
                observeTrainingPlans(TrainingPlanStatusFilter.ACTIVE)
                    .catch { failure -> if (failure is CancellationException) throw failure }
                    .collect { overviews -> trainingPlanOverviewsFlow.value = overviews }
            }
        }

        /**
         * Plan names for the board's title (remediation-1 CP7). A failed read
         * degrades to no names - the board then reads `Untitled workout` - rather
         * than failing the whole workout surface.
         */
        private val planLabels =
            trainingPlanRepository
                .observeVersionLabels()
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(emptyMap())
                }

        /**
         * Every library exercise, archived ones included, by id: focus mode's
         * load step and technique notes (remediation-1 CP8) come from the
         * exercise a workout exercise records, which may have been archived
         * since it was added. A failed read degrades to the stepper's default
         * step and no notes rather than failing the workout surface.
         */
        private val exerciseDetails =
            combine(
                observeExercises(ExerciseStatusFilter.ACTIVE, ""),
                observeExercises(ExerciseStatusFilter.ARCHIVED, ""),
            ) { active, archived -> (active + archived).associateBy { it.id } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(emptyMap())
                }

        private val content =
            combine(observeActiveWorkoutSession(), planLabels, exerciseDetails) { session, labels, details ->
                toContent(session, session?.trainingPlanVersionId?.let { labels[it]?.planName }, details)
            }.onStart { emit(ActiveWorkoutContent.Loading) }
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
         * (`null`) remains fully supported via the unchanged
         * [StartWorkoutSession] path. When a plan is chosen,
         * [StartWorkoutSessionFromPlan] seeds every one of its planned
         * exercises atomically - either the whole session (with every
         * exercise) is persisted, or none of it is (Milestone 8,
         * implementation-review finding #1: the previous inline loop here
         * silently skipped exercises it couldn't resolve and always reported
         * success regardless). Each seeded exercise carries its
         * [com.repflow.app.domain.trainingplan.PlannedExercise.id] so
         * [com.repflow.app.application.workout.CompleteWorkoutSession] can
         * later resolve a planned rep range for progression (Milestone 8, CP6).
         *
         * A non-null [planVersionId] that no longer resolves to an overview
         * (round-2 implementation-review finding #1: the plan list changed
         * between rendering and the click, or the selection was stale) must
         * surface [WorkoutOperationError.NotFound] rather than silently
         * falling back to an ad-hoc session that would still carry the
         * plan-version id without any of that plan's exercises.
         */
        fun onStartWorkout(planVersionId: TrainingPlanVersionId? = null) {
            val overview = planVersionId?.let { id -> trainingPlanOverviewsFlow.value.find { it.latestVersion.id == id } }
            launchAction {
                when {
                    planVersionId == null -> {
                        startWorkoutSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
                    }

                    overview != null -> {
                        startWorkoutSessionFromPlan(
                            StartWorkoutSessionFromPlanCommand(
                                trainingPlanVersionId = planVersionId,
                                plannedExercises = overview.latestVersion.plannedExercises,
                            ),
                        )
                    }

                    else -> {
                        DomainResult.Failure(WorkoutOperationError.NotFound)
                    }
                }
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
            val restSeconds = plannedRestSecondsFor(exerciseId) ?: DEFAULT_REST_TIMER_SECONDS
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
                // Read when the set is logged, so a switch changed mid-workout applies to the next set.
                if (result is DomainResult.Success && settingsRepository.get().restTimerAutoStart) {
                    startRestTimer(sessionId, restSeconds)
                }
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

        /**
         * The finish sheet's confirm - the only caller of [CompleteWorkoutSession]
         * (remediation-1 CP9). On success [finish] carries the session id to the
         * done screen; on failure it returns to idle and the error is reported
         * as any other action's is. A second confirm while one is in flight is
         * ignored: its certain failure would otherwise turn the first one's
         * finish into a plain "session ended, go Home".
         */
        fun onCompleteWorkout(sessionId: WorkoutSessionId) {
            if (_finish.value != WorkoutFinishState.Idle) return
            _finish.value = WorkoutFinishState.InFlight
            viewModelScope.launch {
                when (val result = completeWorkoutSession(sessionId)) {
                    is DomainResult.Success -> {
                        _finish.value = WorkoutFinishState.Finished(sessionId)
                    }

                    is DomainResult.Failure -> {
                        _finish.value = WorkoutFinishState.Idle
                        error.update { result.error.toReason() }
                    }
                }
            }
        }

        fun onAbandonWorkout(sessionId: WorkoutSessionId) {
            launchAction { abandonWorkoutSession(sessionId) }
        }

        fun onErrorShown() {
            error.update { null }
        }

        /**
         * Re-reads every picker row's recommendation. The override itself is
         * recorded on the recommendation screen (remediation-1 CP6), which this
         * ViewModel never sees, so the route calls this on every `ON_START` -
         * a return from that screen inside the 5-second `WhileSubscribed`
         * window would otherwise keep showing the choice from before it.
         */
        fun onRefreshRecommendations() {
            recommendationRefreshTrigger.update { it + 1 }
        }

        private suspend fun latestRecommendationUi(exerciseId: ExerciseId): ProgressionRecommendationUi? =
            progressionRecommendationRepository.findLatestForExercise(exerciseId)?.toSummaryUi()

        private fun activeSessionId(): WorkoutSessionId? = (uiState.value.content as? ActiveWorkoutContent.Active)?.sessionId

        private fun launchAction(action: suspend () -> DomainResult<*, WorkoutOperationError>) {
            viewModelScope.launch {
                when (val result = action()) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> error.update { result.error.toReason() }
                }
            }
        }

        /**
         * Resolves each exercise's planned target (Milestone 8,
         * implementation-review finding #2) via [trainingPlanRepository] -
         * `null` for an ad-hoc exercise ([WorkoutExercise.plannedExerciseId]
         * is `null`) or one whose plan/version has since been altered such
         * that the planned exercise no longer resolves. Suspend calls inside
         * `List.map` are fine here: `map` is `inline`, so its lambda is
         * inlined into this already-`suspend` function rather than compiled
         * as a separate non-suspend closure (same pattern
         * [latestRecommendationUi] already relies on above).
         */
        private suspend fun toContent(
            session: WorkoutSession?,
            planName: String?,
            details: Map<ExerciseId, Exercise>,
        ): ActiveWorkoutContent =
            if (session == null) {
                ActiveWorkoutContent.NoActiveSession
            } else {
                ActiveWorkoutContent.Active(
                    sessionId = session.id,
                    startedAt = session.startedAt,
                    restTimer = session.restTimer?.toUi(),
                    exercises = session.exercises.map { exercise -> toExerciseUi(exercise, details[exercise.exerciseId]) },
                    planName = planName,
                )
            }

        private suspend fun toExerciseUi(
            exercise: WorkoutExercise,
            detail: Exercise?,
        ): ActiveExerciseUi =
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
                plannedTarget =
                    exercise.plannedExerciseId
                        ?.let { trainingPlanRepository.findPlannedExercise(it) }
                        ?.toUi(),
                exerciseId = exercise.exerciseId,
                defaultLoadIncrement = detail?.defaultLoadIncrement?.let { BigDecimal.valueOf(it.grams, GRAMS_TO_KG_SCALE) },
                instructions = detail?.instructions?.value,
            )

        /** The exercise's planned rest, if it was seeded from a plan target; `null` for an ad-hoc exercise (Milestone 8, implementation-review finding #2). */
        private fun plannedRestSecondsFor(exerciseId: WorkoutExerciseId): Int? =
            (uiState.value.content as? ActiveWorkoutContent.Active)
                ?.exercises
                ?.find { it.id == exerciseId }
                ?.plannedTarget
                ?.restSeconds

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** `LoadIncrement` is stored in grams; the stepper steps in kg. */
            const val GRAMS_TO_KG_SCALE = 3
        }
    }

private fun RestTimer.toUi(): RestTimerUi = RestTimerUi(endAt = endAt, totalDurationSeconds = totalDurationSeconds)

/** Pure mapping from the domain plan target to its presentation shape - no DI dependency, so it stays a top-level function like the file's other `toUi()` mappers. */
private fun PlannedExercise.toUi(): PlannedTargetUi =
    PlannedTargetUi(
        targetWarmupSets = targetWarmupSets,
        targetWorkingSets = targetSets.value,
        repRange = (target as? PlannedExerciseTarget.Reps)?.range?.let { it.min..it.max },
        durationRangeSeconds = (target as? PlannedExerciseTarget.Duration)?.range?.let { it.minSeconds..it.maxSeconds },
        restSeconds = restDuration?.seconds?.toInt(),
    )

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
