package com.repflow.app.application.progression

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionPolicyInput
import com.repflow.app.domain.progression.ProgressionPolicyV1
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.trainingplan.RepRange
import javax.inject.Inject

/**
 * Raw performance data for one exercise occurrence, gathered by the caller
 * from the just-completed [com.repflow.app.domain.workout.WorkoutSession]
 * (which sets were warmups, tracking type etc. are presentation/caller
 * concerns - this use case only needs the working-set numbers).
 */
data class ComputeProgressionRecommendationCommand(
    val exerciseId: ExerciseId,
    val workingSetReps: List<Int>,
    val workingSetRpe: List<Double>,
    val plannedRepRange: RepRange?,
)

/**
 * Evaluates [ProgressionPolicyV1] for one exercise occurrence, folding in
 * the current [com.repflow.app.application.recovery.WorkoutDayContext], and
 * persists the resulting [ProgressionRecommendation].
 */
class ComputeProgressionRecommendation
    @Inject
    constructor(
        private val repository: ProgressionRecommendationRepository,
        private val getWorkoutDayContext: GetWorkoutDayContext,
        private val clock: Clock,
        private val idGenerator: IdentifierGenerator,
    ) {
        suspend operator fun invoke(
            command: ComputeProgressionRecommendationCommand,
        ): DomainResult<ProgressionRecommendation, ProgressionOperationError> {
            val dayContext = getWorkoutDayContext()
            val evaluation =
                ProgressionPolicyV1.evaluate(
                    ProgressionPolicyInput(
                        workingSetReps = command.workingSetReps,
                        workingSetRpe = command.workingSetRpe,
                        plannedRepRange = command.plannedRepRange,
                        latestPainWhileWalking = dayContext.latestRecoveryEntry?.painWhileWalking,
                        latestHeavyLegs = dayContext.latestRecoveryEntry?.heavyLegs,
                        hasRecentFutsalSession = dayContext.recentFutsalSession != null,
                    ),
                )
            val recommendation =
                ProgressionRecommendation
                    .create(
                        id = ProgressionRecommendationId(idGenerator.newId()),
                        exerciseId = command.exerciseId,
                        result = evaluation.result,
                        reasons = evaluation.reasons,
                        policyVersion = ProgressionPolicyV1.VERSION,
                        computedAt = clock.now(),
                    ).getOrElse {
                        error("ProgressionPolicyV1 produced an invalid recommendation: $it")
                    }
            return when (val insertResult = repository.insert(recommendation)) {
                is DomainResult.Success -> DomainResult.Success(recommendation)
                is DomainResult.Failure -> DomainResult.Failure(insertResult.error.toOperationError())
            }
        }
    }
