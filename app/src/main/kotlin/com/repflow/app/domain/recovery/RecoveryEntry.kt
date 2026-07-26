package com.repflow.app.domain.recovery

import com.repflow.app.domain.common.DomainResult
import java.time.Instant
import java.time.LocalDate

/**
 * A quick daily record of the user's recovery status, per
 * `docs/UX_FLOWS.md`'s "Recovery entry" flow.
 *
 * At most one [RecoveryEntry] exists per [date] (enforced by the
 * persistence layer's unique constraint, not here); re-recording the same
 * date replaces it via [update] rather than creating a duplicate.
 */
@ConsistentCopyVisibility
data class RecoveryEntry private constructor(
    val id: RecoveryEntryId,
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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Returns a new instance with updated fields and `updatedAt = at`, preserving [createdAt]. */
    @Suppress("LongParameterList")
    fun update(
        sleepQuality: Int,
        energy: Int,
        legDoms: Int,
        heelStiffness: Int,
        painWhileWalking: Int,
        heavyLegs: Int,
        futsalInPrevious24h: Boolean,
        futsalExpectedNext24h: Boolean,
        notes: String?,
        at: Instant,
    ): DomainResult<RecoveryEntry, RecoveryValidationError> =
        create(
            id = id,
            date = date,
            sleepQuality = sleepQuality,
            energy = energy,
            legDoms = legDoms,
            heelStiffness = heelStiffness,
            painWhileWalking = painWhileWalking,
            heavyLegs = heavyLegs,
            futsalInPrevious24h = futsalInPrevious24h,
            futsalExpectedNext24h = futsalExpectedNext24h,
            notes = notes,
            createdAt = createdAt,
            updatedAt = at,
        )

    companion object {
        const val NOTES_MAX_LENGTH = 500

        /** Milestone 8, CP3: widened from 0..4 - existing stored values (all ≤ 4) remain valid. */
        private val SCALE_RANGE = 0..5

        @Suppress("LongParameterList", "ReturnCount")
        fun create(
            id: RecoveryEntryId,
            date: LocalDate,
            sleepQuality: Int,
            energy: Int,
            legDoms: Int,
            heelStiffness: Int,
            painWhileWalking: Int,
            heavyLegs: Int,
            futsalInPrevious24h: Boolean,
            futsalExpectedNext24h: Boolean,
            notes: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): DomainResult<RecoveryEntry, RecoveryValidationError> {
            val scaleValues = listOf(sleepQuality, energy, legDoms, heelStiffness, painWhileWalking, heavyLegs)
            if (scaleValues.any { it !in SCALE_RANGE }) {
                return DomainResult.Failure(RecoveryValidationError.ScaleValueOutOfRange)
            }
            if (notes != null && notes.length > NOTES_MAX_LENGTH) {
                return DomainResult.Failure(RecoveryValidationError.NotesTooLong)
            }
            if (updatedAt < createdAt) {
                return DomainResult.Failure(RecoveryValidationError.UpdatedBeforeCreated)
            }
            return DomainResult.Success(
                RecoveryEntry(
                    id = id,
                    date = date,
                    sleepQuality = sleepQuality,
                    energy = energy,
                    legDoms = legDoms,
                    heelStiffness = heelStiffness,
                    painWhileWalking = painWhileWalking,
                    heavyLegs = heavyLegs,
                    futsalInPrevious24h = futsalInPrevious24h,
                    futsalExpectedNext24h = futsalExpectedNext24h,
                    notes = notes,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
    }
}
