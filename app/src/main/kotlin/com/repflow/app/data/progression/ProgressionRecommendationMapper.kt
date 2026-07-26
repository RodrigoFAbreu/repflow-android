package com.repflow.app.data.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ManualOverride
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.progression.ProgressionValidationError
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import java.time.Instant

/** A mapping failure raised when a persisted [ProgressionRecommendationEntity] no longer satisfies domain invariants. */
data class ProgressionMappingError(
    val entityId: String,
    val error: ProgressionValidationError,
)

/**
 * Pure Kotlin, directly unit-testable conversion between the domain
 * [ProgressionRecommendation] aggregate and its persisted
 * [ProgressionRecommendationEntity] row shape, mirroring
 * [com.repflow.app.data.recovery.RecoveryEntryMapper].
 */
object ProgressionRecommendationMapper {
    private const val REASON_DELIMITER = "\n"

    fun toEntity(recommendation: ProgressionRecommendation): ProgressionRecommendationEntity =
        ProgressionRecommendationEntity(
            id = recommendation.id.value,
            exerciseId = recommendation.exerciseId.value,
            result = recommendation.result.toStorageKey(),
            reasons = recommendation.reasons.joinToString(REASON_DELIMITER),
            policyVersion = recommendation.policyVersion,
            computedAt = recommendation.computedAt.toEpochMilli(),
            overrideResult = recommendation.manualOverride?.result?.toStorageKey(),
            overrideAt = recommendation.manualOverride?.overriddenAt?.toEpochMilli(),
        )

    fun toDomain(entity: ProgressionRecommendationEntity): DomainResult<ProgressionRecommendation, ProgressionMappingError> {
        val manualOverride =
            if (entity.overrideResult != null && entity.overrideAt != null) {
                ManualOverride(entity.overrideResult.toResult(), Instant.ofEpochMilli(entity.overrideAt))
            } else {
                null
            }
        return when (
            val result =
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId(entity.id),
                    exerciseId = ExerciseId(entity.exerciseId),
                    result = entity.result.toResult(),
                    reasons = entity.reasons.split(REASON_DELIMITER),
                    policyVersion = entity.policyVersion,
                    computedAt = Instant.ofEpochMilli(entity.computedAt),
                    manualOverride = manualOverride,
                )
        ) {
            is DomainResult.Success -> result
            is DomainResult.Failure -> DomainResult.Failure(ProgressionMappingError(entity.id, result.error))
        }
    }

    private fun ProgressionResult.toStorageKey(): String =
        when (this) {
            ProgressionResult.IncreaseLoad -> "increase_load"
            ProgressionResult.MaintainLoad -> "maintain_load"
            ProgressionResult.ReduceLoad -> "reduce_load"
            ProgressionResult.RecoveryAdjustment -> "recovery_adjustment"
            ProgressionResult.WaitForMoreData -> "wait_for_more_data"
        }

    private fun String.toResult(): ProgressionResult =
        when (this) {
            "increase_load" -> ProgressionResult.IncreaseLoad
            "maintain_load" -> ProgressionResult.MaintainLoad
            "reduce_load" -> ProgressionResult.ReduceLoad
            "recovery_adjustment" -> ProgressionResult.RecoveryAdjustment
            "wait_for_more_data" -> ProgressionResult.WaitForMoreData
            else -> error("Unknown ProgressionResult storage key: $this")
        }
}
