package com.repflow.app.data.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.mapFailure
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.domain.trainingplan.DurationTarget
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity
import java.time.Instant

/**
 * Pure Kotlin, directly unit-testable conversion between the domain
 * training-plan aggregates and their persisted Room row shapes. The only
 * place either direction of this mapping happens, mirroring
 * [com.repflow.app.data.exercise.ExerciseEntityMapper].
 */
object TrainingPlanEntityMapper {
    private const val TARGET_KIND_REPS = "REPS"
    private const val TARGET_KIND_DURATION = "DURATION"

    fun toEntity(plan: TrainingPlan): TrainingPlanEntity =
        TrainingPlanEntity(
            id = plan.id.value,
            name = plan.name.value,
            nameKey = plan.name.key,
            createdAt = plan.createdAt.toEpochMilli(),
            updatedAt = plan.updatedAt.toEpochMilli(),
            archivedAt = plan.archivedAt?.toEpochMilli(),
        )

    fun toEntity(version: TrainingPlanVersion): TrainingPlanVersionEntity =
        TrainingPlanVersionEntity(
            id = version.id.value,
            planId = version.planId.value,
            versionNumber = version.versionNumber,
            note = version.note,
            createdAt = version.createdAt.toEpochMilli(),
        )

    fun toEntities(version: TrainingPlanVersion): List<PlannedExerciseEntity> =
        version.plannedExercises.map { plannedExercise -> toEntity(plannedExercise, version.id.value) }

    private fun toEntity(
        plannedExercise: PlannedExercise,
        versionId: String,
    ): PlannedExerciseEntity {
        val target = plannedExercise.target
        return PlannedExerciseEntity(
            id = plannedExercise.id.value,
            versionId = versionId,
            exerciseId = plannedExercise.exerciseId.value,
            sortOrder = plannedExercise.order,
            targetSets = plannedExercise.targetSets.value,
            targetKind = if (target is PlannedExerciseTarget.Reps) TARGET_KIND_REPS else TARGET_KIND_DURATION,
            repMin = (target as? PlannedExerciseTarget.Reps)?.range?.min,
            repMax = (target as? PlannedExerciseTarget.Reps)?.range?.max,
            durationMinSeconds = (target as? PlannedExerciseTarget.Duration)?.range?.minSeconds,
            durationMaxSeconds = (target as? PlannedExerciseTarget.Duration)?.range?.maxSeconds,
            restSeconds = plannedExercise.restDuration?.seconds,
            isOptional = plannedExercise.isOptional,
            targetWarmupSets = plannedExercise.targetWarmupSets,
        )
    }

    /**
     * Fails loudly (mirrors [com.repflow.app.data.exercise.ExerciseEntityMapper.toDomain]'s
     * D-28 rationale) rather than silently defaulting or skipping the row
     * when `target_kind` is not one of the two currently recognised stable
     * names, or when the row otherwise violates a domain invariant.
     */
    @Suppress("ReturnCount")
    fun toDomain(plan: TrainingPlanEntity): DomainResult<TrainingPlan, TrainingPlanMappingError> {
        val nameResult = TrainingPlanName.create(plan.name)
        val name =
            when (nameResult) {
                is DomainResult.Success -> {
                    nameResult.value
                }

                is DomainResult.Failure -> {
                    return DomainResult.Failure(TrainingPlanMappingError.InvalidFields(plan.id, listOf(nameResult.error)))
                }
            }
        return TrainingPlan
            .reconstruct(
                id = TrainingPlanId(plan.id),
                name = name,
                createdAt = Instant.ofEpochMilli(plan.createdAt),
                updatedAt = Instant.ofEpochMilli(plan.updatedAt),
                archivedAt = plan.archivedAt?.let(Instant::ofEpochMilli),
            ).mapFailure { errors -> TrainingPlanMappingError.InvalidFields(plan.id, listOf(errors)) }
    }

    @Suppress("ReturnCount")
    fun toDomain(
        version: TrainingPlanVersionEntity,
        plannedExerciseRows: List<PlannedExerciseEntity>,
    ): DomainResult<TrainingPlanVersion, TrainingPlanMappingError> {
        val plannedExercises = mutableListOf<PlannedExercise>()
        for (row in plannedExerciseRows) {
            when (val result = toDomain(row)) {
                is DomainResult.Success -> plannedExercises += result.value
                is DomainResult.Failure -> return DomainResult.Failure(result.error)
            }
        }
        return TrainingPlanVersion
            .create(
                id = TrainingPlanVersionId(version.id),
                planId = TrainingPlanId(version.planId),
                versionNumber = version.versionNumber,
                plannedExercises = plannedExercises,
                note = version.note,
                createdAt = Instant.ofEpochMilli(version.createdAt),
            ).mapFailure { error -> TrainingPlanMappingError.InvalidFields(version.id, listOf(error)) }
    }

    @Suppress("ReturnCount")
    fun toDomain(row: PlannedExerciseEntity): DomainResult<PlannedExercise, TrainingPlanMappingError> {
        val fieldErrors = mutableListOf<TrainingPlanValidationError>()

        val targetSetsResult = TargetSets.create(row.targetSets)
        if (targetSetsResult is DomainResult.Failure) fieldErrors += targetSetsResult.error

        val restDurationResult = row.restSeconds?.let(RestDuration::create)
        if (restDurationResult is DomainResult.Failure) fieldErrors += TrainingPlanValidationError.RestDurationOutOfRange

        TargetSets.validateWarmupSets(row.targetWarmupSets)?.let { fieldErrors += it }

        val target =
            when (row.targetKind) {
                TARGET_KIND_REPS -> buildRepsTarget(row, fieldErrors)
                TARGET_KIND_DURATION -> buildDurationTarget(row, fieldErrors)
                else -> return DomainResult.Failure(TrainingPlanMappingError.UnknownTargetKind(row.id, row.targetKind))
            }

        if (fieldErrors.isNotEmpty() || target == null) {
            return DomainResult.Failure(TrainingPlanMappingError.InvalidFields(row.id, fieldErrors))
        }

        return DomainResult.Success(
            PlannedExercise(
                id = PlannedExerciseId(row.id),
                exerciseId = ExerciseId(row.exerciseId),
                order = row.sortOrder,
                targetSets = (targetSetsResult as DomainResult.Success).value,
                target = target,
                restDuration = (restDurationResult as? DomainResult.Success)?.value,
                isOptional = row.isOptional,
                targetWarmupSets = row.targetWarmupSets,
            ),
        )
    }

    private fun buildRepsTarget(
        row: PlannedExerciseEntity,
        fieldErrors: MutableList<TrainingPlanValidationError>,
    ): PlannedExerciseTarget.Reps? {
        val min = row.repMin
        val max = row.repMax
        if (min == null || max == null) {
            fieldErrors += TrainingPlanValidationError.RepRangeInvalid
            return null
        }
        return when (val result = RepRange.create(min, max)) {
            is DomainResult.Success -> {
                PlannedExerciseTarget.Reps(result.value)
            }

            is DomainResult.Failure -> {
                fieldErrors += result.error
                null
            }
        }
    }

    private fun buildDurationTarget(
        row: PlannedExerciseEntity,
        fieldErrors: MutableList<TrainingPlanValidationError>,
    ): PlannedExerciseTarget.Duration? {
        val min = row.durationMinSeconds
        val max = row.durationMaxSeconds
        if (min == null || max == null) {
            fieldErrors += TrainingPlanValidationError.DurationRangeInvalid
            return null
        }
        return when (val result = DurationTarget.create(min, max)) {
            is DomainResult.Success -> {
                PlannedExerciseTarget.Duration(result.value)
            }

            is DomainResult.Failure -> {
                fieldErrors += result.error
                null
            }
        }
    }
}
