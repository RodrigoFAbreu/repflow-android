package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseInstructions
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseValidationError
import com.repflow.app.domain.exercise.LoadIncrement
import com.repflow.app.domain.exercise.RestDuration

/**
 * The subset of an exercise's fields shared by [CreateExercise] and
 * [UpdateExercise], once each has been individually validated.
 */
internal data class ValidatedExerciseFields(
    val name: ExerciseName,
    val instructions: ExerciseInstructions?,
    val loadIncrement: LoadIncrement?,
    val restDuration: RestDuration?,
)

/**
 * Validates the raw, UI-shaped fields shared by [CreateExercise] and
 * [UpdateExercise], collecting every violated invariant (rather than
 * failing on the first one) so the editor can show every field error at
 * once.
 */
internal fun validateExerciseFields(
    name: String,
    instructions: String?,
    loadIncrementGrams: Long?,
    restSeconds: Long?,
): DomainResult<ValidatedExerciseFields, List<ExerciseValidationError>> {
    val nameResult = ExerciseName.create(name)
    val instructionsResult = ExerciseInstructions.createOrNull(instructions)
    val loadIncrementResult = loadIncrementGrams?.let(LoadIncrement::create)
    val restDurationResult = restSeconds?.let(RestDuration::create)

    val errors =
        listOfNotNull(
            (nameResult as? DomainResult.Failure)?.error,
            (instructionsResult as? DomainResult.Failure)?.error,
            (loadIncrementResult as? DomainResult.Failure)?.error,
            (restDurationResult as? DomainResult.Failure)?.error,
        )
    if (errors.isNotEmpty()) {
        return DomainResult.Failure(errors)
    }

    return DomainResult.Success(
        ValidatedExerciseFields(
            name = (nameResult as DomainResult.Success).value,
            instructions = (instructionsResult as? DomainResult.Success)?.value,
            loadIncrement = (loadIncrementResult as? DomainResult.Success)?.value,
            restDuration = (restDurationResult as? DomainResult.Success)?.value,
        ),
    )
}
