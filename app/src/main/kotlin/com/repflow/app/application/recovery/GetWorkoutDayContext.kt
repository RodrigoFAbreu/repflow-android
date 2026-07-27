package com.repflow.app.application.recovery

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import java.time.Duration
import javax.inject.Inject

/**
 * A read model summarizing the most relevant recovery/futsal information
 * for the current workout day, per `docs/UX_FLOWS.md`'s "Relevant recovery
 * or futsal context".
 */
data class WorkoutDayContext(
    val latestRecoveryEntry: RecoveryEntry?,
    val recentFutsalSession: FutsalSession?,
)

/**
 * Loads the [WorkoutDayContext] shown as read-only context on the active
 * workout screen: the most recently recorded [RecoveryEntry] (regardless of
 * age) and any [FutsalSession] recorded within the last
 * [RECENT_FUTSAL_WINDOW_HOURS] hours.
 *
 * This is a read-only summary, not a recommendation - inferring "warnings"
 * from these values is explicitly deferred to Milestone 6's progression
 * engine (see milestone-5-reference.md).
 */
class GetWorkoutDayContext
    @Inject
    constructor(
        private val recoveryRepository: RecoveryRepository,
        private val futsalRepository: FutsalRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(): WorkoutDayContext {
            val since = clock.now().minus(Duration.ofHours(RECENT_FUTSAL_WINDOW_HOURS))
            val recentFutsalSession = futsalRepository.findSince(since).maxByOrNull { it.date }
            return WorkoutDayContext(
                latestRecoveryEntry = recoveryRepository.findLatest(),
                recentFutsalSession = recentFutsalSession,
            )
        }

        private companion object {
            const val RECENT_FUTSAL_WINDOW_HOURS = 24L
        }
    }
