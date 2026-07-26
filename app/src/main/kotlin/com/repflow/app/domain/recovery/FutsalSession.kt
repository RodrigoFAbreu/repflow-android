package com.repflow.app.domain.recovery

import com.repflow.app.domain.common.DomainResult
import java.time.Instant
import java.time.LocalDate

/**
 * A recorded futsal session used to estimate training load, per
 * `docs/DOMAIN_GLOSSARY.md`'s "Futsal load" (`duration_minutes *
 * session_rpe`).
 *
 * At most one [FutsalSession] exists per [date] (enforced by the
 * persistence layer's unique constraint, not here); re-recording the same
 * date replaces it via [update] rather than creating a duplicate.
 */
@ConsistentCopyVisibility
data class FutsalSession private constructor(
    val id: FutsalSessionId,
    val date: LocalDate,
    val durationMinutes: Int,
    val sessionRpe: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The derived training load; never stored as an independent mutable field. */
    val load: Double get() = durationMinutes * sessionRpe

    /** Returns a new instance with updated fields and `updatedAt = at`, preserving [createdAt]. */
    fun update(
        durationMinutes: Int,
        sessionRpe: Double,
        at: Instant,
    ): DomainResult<FutsalSession, FutsalValidationError> =
        create(
            id = id,
            date = date,
            durationMinutes = durationMinutes,
            sessionRpe = sessionRpe,
            createdAt = createdAt,
            updatedAt = at,
        )

    companion object {
        private val SESSION_RPE_RANGE = 0.0..10.0

        @Suppress("LongParameterList")
        fun create(
            id: FutsalSessionId,
            date: LocalDate,
            durationMinutes: Int,
            sessionRpe: Double,
            createdAt: Instant,
            updatedAt: Instant,
        ): DomainResult<FutsalSession, FutsalValidationError> {
            if (durationMinutes <= 0) {
                return DomainResult.Failure(FutsalValidationError.DurationNotPositive)
            }
            if (sessionRpe !in SESSION_RPE_RANGE) {
                return DomainResult.Failure(FutsalValidationError.SessionRpeOutOfRange)
            }
            if (updatedAt < createdAt) {
                return DomainResult.Failure(FutsalValidationError.UpdatedBeforeCreated)
            }
            return DomainResult.Success(
                FutsalSession(
                    id = id,
                    date = date,
                    durationMinutes = durationMinutes,
                    sessionRpe = sessionRpe,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
    }
}
