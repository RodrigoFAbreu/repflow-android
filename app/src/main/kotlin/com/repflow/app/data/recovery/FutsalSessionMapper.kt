package com.repflow.app.data.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import com.repflow.app.domain.recovery.FutsalValidationError
import com.repflow.app.infrastructure.database.recovery.FutsalSessionEntity
import java.time.Instant
import java.time.LocalDate

/** A mapping failure raised when a persisted [FutsalSessionEntity] no longer satisfies domain invariants. */
data class FutsalMappingError(
    val entityId: String,
    val error: FutsalValidationError,
)

/** Mirrors [RecoveryEntryMapper] for [FutsalSession]/[FutsalSessionEntity]. */
object FutsalSessionMapper {
    fun toEntity(session: FutsalSession): FutsalSessionEntity =
        FutsalSessionEntity(
            id = session.id.value,
            entryDate = session.date.toString(),
            durationMinutes = session.durationMinutes,
            sessionRpe = session.sessionRpe,
            createdAt = session.createdAt.toEpochMilli(),
            updatedAt = session.updatedAt.toEpochMilli(),
        )

    fun toDomain(entity: FutsalSessionEntity): DomainResult<FutsalSession, FutsalMappingError> =
        FutsalSession
            .create(
                id = FutsalSessionId(entity.id),
                date = LocalDate.parse(entity.entryDate),
                durationMinutes = entity.durationMinutes,
                sessionRpe = entity.sessionRpe,
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            ).let { result ->
                when (result) {
                    is DomainResult.Success -> result
                    is DomainResult.Failure -> DomainResult.Failure(FutsalMappingError(entity.id, result.error))
                }
            }
}
