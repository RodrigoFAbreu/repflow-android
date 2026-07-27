package com.repflow.app.application.recovery

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import java.time.LocalDate
import javax.inject.Inject

/** Raw, UI-shaped input for recording a recovery entry for a given date. */
data class RecordRecoveryEntryCommand(
    val date: LocalDate,
    val sleepQuality: Int,
    val energy: Int,
    val legDoms: Int,
    val heelStiffness: Int,
    val painWhileWalking: Int,
    val heavyLegs: Int,
    val futsalInPrevious24h: Boolean,
    val futsalExpectedNext24h: Boolean,
    val notes: String?,
)

/**
 * Records (creates or replaces) the [RecoveryEntry] for a given date. At
 * most one entry exists per date, so an existing row for
 * [RecordRecoveryEntryCommand.date] is updated in place rather than
 * duplicated.
 */
class RecordRecoveryEntry
    @Inject
    constructor(
        private val repository: RecoveryRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: RecordRecoveryEntryCommand): DomainResult<RecoveryEntryId, RecoveryOperationError> {
            val now = clock.now()
            val existing = repository.findForDate(command.date)

            val entry =
                if (existing != null) {
                    existing
                        .update(
                            sleepQuality = command.sleepQuality,
                            energy = command.energy,
                            legDoms = command.legDoms,
                            heelStiffness = command.heelStiffness,
                            painWhileWalking = command.painWhileWalking,
                            heavyLegs = command.heavyLegs,
                            futsalInPrevious24h = command.futsalInPrevious24h,
                            futsalExpectedNext24h = command.futsalExpectedNext24h,
                            notes = command.notes,
                            at = now,
                        ).getOrElse { error ->
                            return DomainResult.Failure(RecoveryOperationError.ValidationFailed(error))
                        }
                } else {
                    RecoveryEntry
                        .create(
                            id = RecoveryEntryId(identifierGenerator.newId()),
                            date = command.date,
                            sleepQuality = command.sleepQuality,
                            energy = command.energy,
                            legDoms = command.legDoms,
                            heelStiffness = command.heelStiffness,
                            painWhileWalking = command.painWhileWalking,
                            heavyLegs = command.heavyLegs,
                            futsalInPrevious24h = command.futsalInPrevious24h,
                            futsalExpectedNext24h = command.futsalExpectedNext24h,
                            notes = command.notes,
                            createdAt = now,
                            updatedAt = now,
                        ).getOrElse { error ->
                            return DomainResult.Failure(RecoveryOperationError.ValidationFailed(error))
                        }
                }

            return when (repository.upsert(entry)) {
                is DomainResult.Success -> DomainResult.Success(entry.id)
                is DomainResult.Failure -> DomainResult.Failure(RecoveryOperationError.PersistenceUnavailable)
            }
        }
    }
