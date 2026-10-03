package com.repflow.app.application.recovery

import com.repflow.app.domain.recovery.ReadinessScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Emits the [ReadinessScore] for [date]'s recovery entry, or `null` when that
 * date has none (remediation-1 plan CP4 items 2-3).
 *
 * **Only [date]'s own entry counts** - an earlier day's entry is never
 * carried forward, so a day with no check-in has no score. Choosing [date]
 * (today, re-derived when the day changes) is the caller's job; this use case
 * never reads the clock.
 *
 * The flow re-emits when the entry is written - `RecordRecoveryEntry`'s
 * upsert is enough, no refresh call - and drops repeats of an unchanged
 * score, since Room re-emits on any write to the table.
 */
class ObserveReadiness
    @Inject
    constructor(
        private val repository: RecoveryRepository,
    ) {
        operator fun invoke(date: LocalDate): Flow<ReadinessScore?> =
            repository
                .observeForDate(date)
                .map { entry -> entry?.let(ReadinessScore::of) }
                .distinctUntilChanged()
    }
