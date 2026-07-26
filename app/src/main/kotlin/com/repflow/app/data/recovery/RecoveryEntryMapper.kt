package com.repflow.app.data.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import com.repflow.app.domain.recovery.RecoveryValidationError
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryEntity
import java.time.Instant
import java.time.LocalDate

/** A mapping failure raised when a persisted [RecoveryEntryEntity] no longer satisfies domain invariants. */
data class RecoveryMappingError(
    val entityId: String,
    val error: RecoveryValidationError,
)

/**
 * Pure Kotlin, directly unit-testable conversion between the domain
 * [RecoveryEntry] aggregate and its persisted [RecoveryEntryEntity] row
 * shape, mirroring
 * [com.repflow.app.data.exercise.ExerciseEntityMapper].
 */
object RecoveryEntryMapper {
    fun toEntity(entry: RecoveryEntry): RecoveryEntryEntity =
        RecoveryEntryEntity(
            id = entry.id.value,
            entryDate = entry.date.toString(),
            sleepQuality = entry.sleepQuality,
            energy = entry.energy,
            legDoms = entry.legDoms,
            heelStiffness = entry.heelStiffness,
            painWhileWalking = entry.painWhileWalking,
            heavyLegs = entry.heavyLegs,
            futsalInPrevious24h = entry.futsalInPrevious24h,
            futsalExpectedNext24h = entry.futsalExpectedNext24h,
            notes = entry.notes,
            createdAt = entry.createdAt.toEpochMilli(),
            updatedAt = entry.updatedAt.toEpochMilli(),
        )

    fun toDomain(entity: RecoveryEntryEntity): DomainResult<RecoveryEntry, RecoveryMappingError> =
        RecoveryEntry
            .create(
                id = RecoveryEntryId(entity.id),
                date = LocalDate.parse(entity.entryDate),
                sleepQuality = entity.sleepQuality,
                energy = entity.energy,
                legDoms = entity.legDoms,
                heelStiffness = entity.heelStiffness,
                painWhileWalking = entity.painWhileWalking,
                heavyLegs = entity.heavyLegs,
                futsalInPrevious24h = entity.futsalInPrevious24h,
                futsalExpectedNext24h = entity.futsalExpectedNext24h,
                notes = entity.notes,
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            ).let { result ->
                when (result) {
                    is DomainResult.Success -> result
                    is DomainResult.Failure -> DomainResult.Failure(RecoveryMappingError(entity.id, result.error))
                }
            }
}
