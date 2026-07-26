package com.repflow.app.application.trainingplan

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import javax.inject.Inject

/** Raw, UI-shaped input for revising an existing training plan into a new version. */
data class ReviseTrainingPlanCommand(
    val planId: String,
    val name: String,
    val plannedExercises: List<PlannedExerciseInput>,
)

class ReviseTrainingPlan
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
        private val exerciseRepository: ExerciseRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        /**
         * Always creates a brand-new [TrainingPlanVersion] with
         * `versionNumber = existing latest + 1` - never mutates the
         * existing latest version's row. See [CreateTrainingPlan.invoke]
         * for the `@Suppress` rationale.
         */
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: ReviseTrainingPlanCommand): DomainResult<Unit, TrainingPlanOperationError> {
            val existing =
                repository.findOverviewByPlanId(TrainingPlanId(command.planId))
                    ?: return DomainResult.Failure(TrainingPlanOperationError.NotFound)

            val name =
                TrainingPlanName.create(command.name).getOrElse { error ->
                    return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                }

            val conflictingId = repository.findPlanIdByNameKey(name.key)
            if (conflictingId != null && conflictingId != existing.plan.id) {
                return DomainResult.Failure(TrainingPlanOperationError.DuplicateName)
            }

            val plannedExercises =
                when (val result = validatePlannedExercises(command.plannedExercises, exerciseRepository, identifierGenerator)) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> return result
                }

            val now = clock.now()

            val newVersion =
                TrainingPlanVersion
                    .create(
                        id = TrainingPlanVersionId(identifierGenerator.newId()),
                        planId = existing.plan.id,
                        versionNumber = existing.latestVersion.versionNumber + 1,
                        plannedExercises = plannedExercises,
                        note = null,
                        createdAt = now,
                    ).getOrElse { error ->
                        return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                    }

            val updatedPlan =
                TrainingPlan
                    .reconstruct(
                        id = existing.plan.id,
                        name = name,
                        createdAt = existing.plan.createdAt,
                        updatedAt = now,
                    ).getOrElse { error ->
                        return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                    }

            return when (val result = repository.addVersion(updatedPlan, newVersion)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
