package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.ComputeProgressionRecommendationCommand
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Edits/undo of individual sets happen via [RecordWorkoutSet] and repository updates directly from the presentation layer's ViewModel; this use case only handles the terminal transition. */
class CompleteWorkoutSession
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val trainingPlanRepository: TrainingPlanRepository,
        private val computeProgressionRecommendation: ComputeProgressionRecommendation,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(sessionId: WorkoutSessionId): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val completed =
                session.complete(clock.now()).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(completed)) {
                is DomainResult.Success -> {
                    computeRecommendations(completed.exercises)
                    DomainResult.Success(Unit)
                }

                is DomainResult.Failure -> {
                    DomainResult.Failure(result.error.toOperationError())
                }
            }
        }

        /**
         * Best-effort: a recommendation failing to compute must never block
         * the (already-persisted) workout completion, so failures here are
         * intentionally swallowed rather than surfaced to the caller.
         */
        private suspend fun computeRecommendations(exercises: List<WorkoutExercise>) {
            for (exercise in exercises) {
                val workingSets = exercise.sets.filterNot { it.isWarmup }
                val plannedRepRange =
                    exercise.plannedExerciseId
                        ?.let { trainingPlanRepository.findPlannedExercise(it) }
                        ?.target
                        ?.let { it as? PlannedExerciseTarget.Reps }
                        ?.range
                computeProgressionRecommendation(
                    ComputeProgressionRecommendationCommand(
                        exerciseId = exercise.exerciseId,
                        workingSetReps = workingSets.mapNotNull { it.reps },
                        workingSetRpe = workingSets.mapNotNull { it.rpe },
                        plannedRepRange = plannedRepRange,
                        hadOnlyWarmupSets = exercise.sets.isNotEmpty() && workingSets.isEmpty(),
                    ),
                )
            }
        }
    }
