package com.repflow.app.application.trainingplan

import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.map
import com.repflow.app.domain.common.mapFailure
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.domain.trainingplan.DurationTarget
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError

/**
 * Validates the raw, UI-shaped [PlannedExerciseInput] rows shared by
 * [CreateTrainingPlan] and [ReviseTrainingPlan].
 *
 * Two independent kinds of failure exist and are reported separately,
 * following the same "collect everything, don't fail on the first issue"
 * approach as [com.repflow.app.application.exercise.validateExerciseFields]:
 * - domain-level field violations ([TrainingPlanValidationError], e.g. a rep
 *   range with `min > max`) are reported as
 *   [TrainingPlanOperationError.ValidationFailed];
 * - cross-referencing violations against the actual referenced exercise
 *   ([PlannedExerciseValidationError], e.g. a missing exercise id, or a
 *   target kind that does not match the exercise's tracking type) are
 *   reported as [TrainingPlanOperationError.PlannedExerciseInvalid], and are
 *   only checked once every row's own fields already passed domain
 *   validation.
 */
@Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
internal suspend fun validatePlannedExercises(
    inputs: List<PlannedExerciseInput>,
    exerciseRepository: ExerciseRepository,
    identifierGenerator: IdentifierGenerator,
): DomainResult<List<PlannedExercise>, TrainingPlanOperationError> {
    val domainErrors = mutableListOf<TrainingPlanValidationError>()
    val rows = inputs.map { input -> buildPlannedExerciseTargetSetsAndErrors(input, domainErrors) }
    if (domainErrors.isNotEmpty()) {
        return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(domainErrors))
    }

    val crossErrors = mutableListOf<PlannedExerciseValidationError>()
    val plannedExercises = mutableListOf<PlannedExercise>()
    for ((index, input) in inputs.withIndex()) {
        val row = rows[index]
        val targetSets = requireNotNull(row.targetSets) { "targetSets must be non-null once domainErrors is empty" }
        val target = requireNotNull(row.target) { "target must be non-null once domainErrors is empty" }
        val exercise = exerciseRepository.findById(ExerciseId(input.exerciseId))
        if (exercise == null) {
            crossErrors += PlannedExerciseValidationError.ExerciseNotFound(index)
            continue
        }
        if (!targetMatchesTrackingType(target, exercise.trackingType)) {
            crossErrors += PlannedExerciseValidationError.TargetKindMismatch(index)
            continue
        }
        plannedExercises +=
            PlannedExercise(
                id = PlannedExerciseId(identifierGenerator.newId()),
                exerciseId = exercise.id,
                order = input.order,
                targetSets = targetSets,
                target = target,
                restDuration = row.restDuration,
                isOptional = input.isOptional,
            )
    }

    if (crossErrors.isNotEmpty()) {
        return DomainResult.Failure(TrainingPlanOperationError.PlannedExerciseInvalid(crossErrors))
    }
    return DomainResult.Success(plannedExercises)
}

private data class RowFields(
    val targetSets: TargetSets?,
    val target: PlannedExerciseTarget?,
    val restDuration: RestDuration?,
)

/**
 * Builds one row's [TargetSets], [PlannedExerciseTarget] and [RestDuration],
 * appending any violated invariant to [domainErrors] rather than failing
 * fast. [RowFields.targetSets] and [RowFields.target] are `null` only when
 * that field failed validation - callers only read them once [domainErrors]
 * is confirmed empty, at which point both are guaranteed non-null.
 */
private fun buildPlannedExerciseTargetSetsAndErrors(
    input: PlannedExerciseInput,
    domainErrors: MutableList<TrainingPlanValidationError>,
): RowFields {
    val targetSetsResult = TargetSets.create(input.targetSets)
    val targetResult = buildTarget(input)
    val restResult: DomainResult<RestDuration?, TrainingPlanValidationError> =
        input.restSeconds?.let { seconds ->
            RestDuration.create(seconds).mapFailure { TrainingPlanValidationError.RestDurationOutOfRange }
        } ?: DomainResult.Success(null)

    domainErrors +=
        listOfNotNull(
            (targetSetsResult as? DomainResult.Failure)?.error,
            (targetResult as? DomainResult.Failure)?.error,
            (restResult as? DomainResult.Failure)?.error,
        )

    return RowFields(
        targetSets = (targetSetsResult as? DomainResult.Success)?.value,
        target = (targetResult as? DomainResult.Success)?.value,
        restDuration = (restResult as? DomainResult.Success)?.value,
    )
}

private fun buildTarget(input: PlannedExerciseInput): DomainResult<PlannedExerciseTarget, TrainingPlanValidationError> =
    when (input.targetKind) {
        PlannedExerciseTargetKind.REPS -> {
            if (input.repMin == null || input.repMax == null) {
                DomainResult.Failure(TrainingPlanValidationError.RepRangeInvalid)
            } else {
                RepRange.create(input.repMin, input.repMax).map { PlannedExerciseTarget.Reps(it) }
            }
        }

        PlannedExerciseTargetKind.DURATION -> {
            if (input.durationMinSeconds == null || input.durationMaxSeconds == null) {
                DomainResult.Failure(TrainingPlanValidationError.DurationRangeInvalid)
            } else {
                DurationTarget
                    .create(input.durationMinSeconds, input.durationMaxSeconds)
                    .map { PlannedExerciseTarget.Duration(it) }
            }
        }
    }

private fun targetMatchesTrackingType(
    target: PlannedExerciseTarget,
    trackingType: ExerciseTrackingType,
): Boolean =
    when (target) {
        is PlannedExerciseTarget.Reps -> {
            trackingType == ExerciseTrackingType.WEIGHT_AND_REPS || trackingType == ExerciseTrackingType.REPS_ONLY
        }

        is PlannedExerciseTarget.Duration -> {
            trackingType == ExerciseTrackingType.DURATION
        }
    }
