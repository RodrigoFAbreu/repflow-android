package com.repflow.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.TrainingDataRepository
import com.repflow.app.application.common.Clock
import com.repflow.app.application.history.ObserveRecentTraining
import com.repflow.app.application.history.RecentTraining
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.application.trainingplan.TrainingPlanVersionLabel
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.StartWorkoutSessionFromPlanCommand
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.workout.RestNotificationCanceller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives Home (remediation-1 CP5): the running session, the plan to start,
 * today's readiness and the last workout, plus the start and abandon actions.
 *
 * **"Today" is held, not computed once** (plan CP4 item 2). [today] is read
 * from the injected [Clock], and readiness follows it through `flatMapLatest`,
 * so a new day drops the old day's subscription. Two things re-read the clock
 * into it: a wait until the next local midnight, which runs only while the
 * state is collected (a screen left on across midnight), and [onForeground],
 * which the route calls on every `ON_START` - because the midnight wait counts
 * uptime, which stops in deep sleep, and a return inside the 5-second
 * `WhileSubscribed` window never restarts the upstream. `StateFlow` drops an
 * equal date, so a same-day return does not resubscribe. The header date is
 * the date the readiness was read for, so the two cannot disagree.
 *
 * **The backup line** follows the same [today], so it appears on day 21 with
 * nothing else changing. Its inputs fail by saying less: a failed settings or
 * history read shows no line, and a failed
 * [TrainingDataRepository.hasTrainingData] read counts as having data, as on
 * the Backup screen. A failed read of the running workout counts as none, as
 * [activeSession] already does for the whole screen, so the line can show then.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val clock: Clock,
        private val observeReadiness: ObserveReadiness,
        observeActiveWorkoutSession: ObserveActiveWorkoutSession,
        private val observeTrainingPlans: ObserveTrainingPlans,
        observeTrainingPlanVersionLabels: ObserveTrainingPlanVersionLabels,
        private val observeRecentTraining: ObserveRecentTraining,
        private val startWorkoutSession: StartWorkoutSession,
        private val startWorkoutSessionFromPlan: StartWorkoutSessionFromPlan,
        private val abandonWorkoutSession: AbandonWorkoutSession,
        private val restNotificationCanceller: RestNotificationCanceller,
        settingsRepository: SettingsRepository,
        private val trainingDataRepository: TrainingDataRepository,
    ) : ViewModel() {
        private val today = MutableStateFlow(currentDate())
        private val historyRetry = MutableStateFlow(0)
        private val error = MutableStateFlow<HomeErrorReason?>(null)
        private val openWorkout = MutableStateFlow(false)
        private var startInFlight = false

        /** [today], plus the midnight wait that advances it, for as long as this flow is collected. */
        private val date: Flow<LocalDate> =
            channelFlow {
                launch {
                    while (true) {
                        delay(millisUntilNextMidnight())
                        today.value = currentDate()
                    }
                }
                today.collect { send(it) }
            }

        private val readiness: Flow<DatedReadiness> =
            date.flatMapLatest { day ->
                observeReadiness(day)
                    .map { score -> DatedReadiness(day, score?.let(HomeReadiness::Logged) ?: HomeReadiness.NotLogged) }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(DatedReadiness(day, HomeReadiness.Unavailable))
                    }
            }

        private val activeSession: Flow<WorkoutSession?> =
            observeActiveWorkoutSession().catch { failure ->
                if (failure is CancellationException) throw failure
                emit(null)
            }

        private val activePlans: Flow<List<TrainingPlanOverview>> =
            observeTrainingPlans(TrainingPlanStatusFilter.ACTIVE).catch { failure ->
                if (failure is CancellationException) throw failure
                emit(emptyList())
            }

        private val versionLabels: Flow<Map<TrainingPlanVersionId, TrainingPlanVersionLabel>> =
            observeTrainingPlanVersionLabels().catch { failure ->
                if (failure is CancellationException) throw failure
                emit(emptyMap())
            }

        private val recentTraining: Flow<RecentTraining?> =
            historyRetry.flatMapLatest {
                observeRecentTraining()
                    .map<RecentTraining, RecentTraining?> { it }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(null)
                    }
            }

        private val settings: Flow<AppSettings?> =
            settingsRepository
                .observe()
                .map<AppSettings, AppSettings?> { it }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(null)
                }

        /** The history and what the backup line reads. [readHasTrainingData] has no flow of its own, so it runs when any of the others change. */
        private val historyAndBackup: Flow<HistoryAndBackup> =
            combine(settings, recentTraining, today) { settings, recent, day -> Triple(settings, recent, day) }
                .mapLatest { (settings, recent, day) -> HistoryAndBackup(recent, settings, day, readHasTrainingData()) }

        private val content: Flow<HomeUiState> =
            combine(readiness, activeSession, activePlans, versionLabels, historyAndBackup) { dated, session, plans, labels, history ->
                toUiState(dated, session, plans, labels, history)
            }

        val uiState: StateFlow<HomeUiState> =
            combine(content, error, openWorkout) { state, err, open -> state.copy(error = err, openWorkout = open) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = HomeUiState(date = today.value),
                )

        /** Re-reads the clock into [today]: Home's route calls this on every `ON_START`. */
        fun onForeground() {
            today.value = currentDate()
        }

        /**
         * Starts a session from [planVersionId]'s plan, or with no plan when it is
         * `null`, through the same use cases the workout screen calls, and asks
         * the route to open the workout once the start succeeds. A version that
         * is no longer an active plan's latest surfaces as an error rather than
         * falling back to an empty session. A second tap while one start is in
         * flight is ignored, so it can never report the first start's own session
         * as "already running". A start can wait for a whole restore with nothing
         * on screen, which invites that tap.
         */
        fun onStartWorkout(planVersionId: TrainingPlanVersionId?) {
            if (startInFlight) return
            startInFlight = true
            viewModelScope.launch {
                try {
                    val result =
                        if (planVersionId == null) {
                            startWorkoutSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
                        } else {
                            startFromPlan(planVersionId)
                        }
                    when (result) {
                        is DomainResult.Success -> openWorkout.value = true
                        is DomainResult.Failure -> error.value = result.error.toStartReason()
                    }
                } finally {
                    startInFlight = false
                }
            }
        }

        /** The route has shown the workout surface. */
        fun onWorkoutOpened() {
            openWorkout.value = false
        }

        /** The resume card's abandon, after its destructive confirmation: the session is marked abandoned, nothing is deleted. */
        fun onAbandonWorkout(sessionId: WorkoutSessionId) {
            viewModelScope.launch {
                when (abandonWorkoutSession(sessionId)) {
                    is DomainResult.Success -> restNotificationCanceller.cancel()
                    is DomainResult.Failure -> error.value = HomeErrorReason.UNKNOWN
                }
            }
        }

        fun onRetryHistory() {
            historyRetry.update { it + 1 }
        }

        fun onErrorShown() {
            error.value = null
        }

        private suspend fun startFromPlan(planVersionId: TrainingPlanVersionId): DomainResult<*, WorkoutOperationError> {
            val overview =
                activePlans.first().find { it.latestVersion.id == planVersionId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            return startWorkoutSessionFromPlan(
                StartWorkoutSessionFromPlanCommand(
                    trainingPlanVersionId = planVersionId,
                    plannedExercises = overview.latestVersion.plannedExercises,
                ),
            )
        }

        /** Not knowing counts as having data, so a failed read can't hide a line that is due. */
        private suspend fun readHasTrainingData(): Boolean =
            flow { emit(trainingDataRepository.hasTrainingData()) }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(true)
                }.first()

        private fun currentDate(): LocalDate = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

        private fun millisUntilNextMidnight(): Long {
            val zone = ZoneId.systemDefault()
            val now = clock.now()
            val nextMidnight =
                now
                    .atZone(zone)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
            return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** A readiness reading and the day it was read for, kept together so the header and the card agree. */
private data class DatedReadiness(
    val date: LocalDate,
    val readiness: HomeReadiness,
)

/** The history, or `null` when it can't be read, and the other inputs of the backup line, read together. */
private data class HistoryAndBackup(
    val recent: RecentTraining?,
    val settings: AppSettings?,
    val today: LocalDate,
    val hasTrainingData: Boolean,
)

private fun toUiState(
    dated: DatedReadiness,
    session: WorkoutSession?,
    plans: List<TrainingPlanOverview>,
    labels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel>,
    history: HistoryAndBackup,
): HomeUiState {
    val recent = history.recent
    val options = plans.map(::toPlanOption)
    val lastTrainedPlanId = recent?.lastTrainedPlanVersionId?.let { labels[it]?.planId }
    val startPlan = plans.find { it.plan.id == lastTrainedPlanId } ?: plans.firstOrNull()
    return HomeUiState(
        date = dated.date,
        activeWorkout =
            session?.let {
                HomeActiveWorkout(
                    sessionId = it.id,
                    planName = it.trainingPlanVersionId?.let { id -> labels[id]?.planName },
                    startedAt = it.startedAt,
                    setsLogged = it.exercises.sumOf { exercise -> exercise.sets.size },
                )
            },
        start = startPlan?.let { HomeStartCard.Plan(toPlanOption(it)) } ?: HomeStartCard.NoPlan,
        startOptions = options,
        readiness = dated.readiness,
        lastWorkout = toLastWorkout(recent, labels),
        backupReminder = toBackupReminder(history, workoutRunning = session != null),
    )
}

private fun toBackupReminder(
    history: HistoryAndBackup,
    workoutRunning: Boolean,
): HomeBackupReminder? {
    val settings = history.settings ?: return null
    val recent = history.recent ?: return null
    return backupReminderOf(
        reminderOn = settings.backupReminder,
        workoutRunning = workoutRunning,
        hasTrainingData = history.hasTrainingData,
        lastBackupAt = settings.lastBackupAt,
        firstWorkoutEndedAt = recent.firstWorkoutEndedAt,
        today = history.today,
        zone = ZoneId.systemDefault(),
    )
}

private fun toLastWorkout(
    recent: RecentTraining?,
    labels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel>,
): HomeLastWorkout {
    if (recent == null) return HomeLastWorkout.Failed
    val last = recent.lastWorkout ?: return HomeLastWorkout.None
    val session = last.session
    return HomeLastWorkout.Summary(
        planName = session.trainingPlanVersionId?.let { labels[it]?.planName },
        startedAt = session.startedAt,
        endedAt = session.endedAt ?: session.startedAt,
        loadIncreases = last.loadIncreases,
    )
}

private fun toPlanOption(overview: TrainingPlanOverview): HomePlanOption =
    HomePlanOption(
        versionId = overview.latestVersion.id,
        planName = overview.plan.name.value,
        exerciseCount = overview.latestVersion.plannedExercises.size,
        workingSetCount = overview.latestVersion.plannedExercises.sumOf { it.targetSets.value },
    )

private fun WorkoutOperationError.toStartReason(): HomeErrorReason =
    when (this) {
        WorkoutOperationError.ActiveSessionAlreadyExists -> HomeErrorReason.ALREADY_ACTIVE
        WorkoutOperationError.NotFound -> HomeErrorReason.PLAN_NOT_FOUND
        else -> HomeErrorReason.UNKNOWN
    }
