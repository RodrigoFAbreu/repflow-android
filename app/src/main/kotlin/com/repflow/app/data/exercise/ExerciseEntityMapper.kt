package com.repflow.app.data.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.mapFailure
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseInstructions
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.LoadIncrement
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import java.time.Instant

/**
 * Pure Kotlin, directly unit-testable conversion between the domain
 * [Exercise] aggregate and its persisted [ExerciseEntity] row shape. The
 * only place either direction of this mapping happens (see plan.md
 * section G).
 */
object ExerciseEntityMapper {
    fun toEntity(exercise: Exercise): ExerciseEntity =
        ExerciseEntity(
            id = exercise.id.value,
            name = exercise.name.value,
            nameKey = exercise.name.key,
            trackingType = exercise.trackingType.name,
            instructions = exercise.instructions?.value,
            defaultLoadIncrementGrams = exercise.defaultLoadIncrement?.grams,
            defaultRestSeconds = exercise.defaultRestDuration?.seconds,
            origin = exercise.origin.name,
            archivedAt = exercise.archivedAt?.toEpochMilli(),
            createdAt = exercise.createdAt.toEpochMilli(),
            updatedAt = exercise.updatedAt.toEpochMilli(),
        )

    /**
     * Fails loudly (D-28) rather than silently defaulting or skipping the
     * row when `tracking_type` or `origin` is not one of the currently
     * recognised stable names, or when the row otherwise violates a domain
     * invariant.
     */
    @Suppress("ReturnCount")
    fun toDomain(entity: ExerciseEntity): DomainResult<Exercise, ExerciseMappingError> {
        val trackingType =
            ExerciseTrackingType.entries.find { it.name == entity.trackingType }
                ?: return DomainResult.Failure(
                    ExerciseMappingError.UnknownTrackingType(entity.id, entity.trackingType),
                )
        val origin =
            ExerciseOrigin.entries.find { it.name == entity.origin }
                ?: return DomainResult.Failure(ExerciseMappingError.UnknownOrigin(entity.id, entity.origin))

        val nameResult = ExerciseName.create(entity.name)
        val instructionsResult = ExerciseInstructions.createOrNull(entity.instructions)
        val loadIncrementResult = entity.defaultLoadIncrementGrams?.let(LoadIncrement::create)
        val restDurationResult = entity.defaultRestSeconds?.let(RestDuration::create)

        val fieldErrors =
            listOfNotNull(
                (nameResult as? DomainResult.Failure)?.error,
                (instructionsResult as? DomainResult.Failure)?.error,
                (loadIncrementResult as? DomainResult.Failure)?.error,
                (restDurationResult as? DomainResult.Failure)?.error,
            )
        if (fieldErrors.isNotEmpty()) {
            return DomainResult.Failure(ExerciseMappingError.InvalidFields(entity.id, fieldErrors))
        }

        val exerciseResult =
            Exercise.reconstruct(
                id = ExerciseId(entity.id),
                name = (nameResult as DomainResult.Success).value,
                trackingType = trackingType,
                instructions = (instructionsResult as? DomainResult.Success)?.value,
                defaultLoadIncrement = (loadIncrementResult as? DomainResult.Success)?.value,
                defaultRestDuration = (restDurationResult as? DomainResult.Success)?.value,
                origin = origin,
                archivedAt = entity.archivedAt?.let(Instant::ofEpochMilli),
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            )
        return exerciseResult.mapFailure { errors -> ExerciseMappingError.InvalidFields(entity.id, errors) }
    }
}
