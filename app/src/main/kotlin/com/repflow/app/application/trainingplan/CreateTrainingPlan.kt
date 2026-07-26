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

/** Raw, UI-shaped input for creating a new training plan. */
data class CreateTrainingPlanCommand(
    val name: String,
    val plannedExercises: List<PlannedExerciseInput>,
)

class CreateTrainingPlan
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
        private val exerciseRepository: ExerciseRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        /**
         * Guard-clause-style early returns keep each failure next to the
         * check that produces it (see
         * [com.repflow.app.application.exercise.CreateExercise.invoke] for
         * the `@Suppress` rationale).
         */
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: CreateTrainingPlanCommand): DomainResult<TrainingPlanId, TrainingPlanOperationError> {
            val name =
                TrainingPlanName.create(command.name).getOrElse { error ->
                    return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                }

            if (repository.findPlanIdByNameKey(name.key) != null) {
                return DomainResult.Failure(TrainingPlanOperationError.DuplicateName)
            }

            val plannedExercises =
                when (val result = validatePlannedExercises(command.plannedExercises, exerciseRepository, identifierGenerator)) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> return result
                }

            val now = clock.now()
            val planId = TrainingPlanId(identifierGenerator.newId())

            val version =
                TrainingPlanVersion
                    .create(
                        id = TrainingPlanVersionId(identifierGenerator.newId()),
                        planId = planId,
                        versionNumber = 1,
                        plannedExercises = plannedExercises,
                        note = null,
                        createdAt = now,
                    ).getOrElse { error ->
                        return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                    }

            val plan =
                TrainingPlan.create(id = planId, name = name, createdAt = now).getOrElse { error ->
                    return DomainResult.Failure(TrainingPlanOperationError.ValidationFailed(listOf(error)))
                }

            return when (val result = repository.createPlanWithFirstVersion(plan, version)) {
                is DomainResult.Success -> DomainResult.Success(planId)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
