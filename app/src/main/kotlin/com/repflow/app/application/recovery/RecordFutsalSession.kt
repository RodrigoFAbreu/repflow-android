package com.repflow.app.application.recovery

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import java.time.LocalDate
import javax.inject.Inject

/** Raw, UI-shaped input for recording a futsal session for a given date. */
data class RecordFutsalSessionCommand(
    val date: LocalDate,
    val durationMinutes: Int,
    val sessionRpe: Double,
)

/**
 * Records (creates or replaces) the [FutsalSession] for a given date,
 * mirroring [RecordRecoveryEntry]'s "at most one row per date" behaviour.
 */
class RecordFutsalSession
    @Inject
    constructor(
        private val repository: FutsalRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: RecordFutsalSessionCommand): DomainResult<FutsalSessionId, FutsalOperationError> {
            val now = clock.now()
            val existing = repository.findForDate(command.date)

            val session =
                if (existing != null) {
                    existing
                        .update(
                            durationMinutes = command.durationMinutes,
                            sessionRpe = command.sessionRpe,
                            at = now,
                        ).getOrElse { error ->
                            return DomainResult.Failure(FutsalOperationError.ValidationFailed(error))
                        }
                } else {
                    FutsalSession
                        .create(
                            id = FutsalSessionId(identifierGenerator.newId()),
                            date = command.date,
                            durationMinutes = command.durationMinutes,
                            sessionRpe = command.sessionRpe,
                            createdAt = now,
                            updatedAt = now,
                        ).getOrElse { error ->
                            return DomainResult.Failure(FutsalOperationError.ValidationFailed(error))
                        }
                }

            return when (repository.upsert(session)) {
                is DomainResult.Success -> DomainResult.Success(session.id)
                is DomainResult.Failure -> DomainResult.Failure(FutsalOperationError.PersistenceUnavailable)
            }
        }
    }
