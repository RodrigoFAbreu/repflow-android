package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Raw, UI-shaped input for starting a new session seeded from a training plan version. */
data class StartWorkoutSessionFromPlanCommand(
    val trainingPlanVersionId: TrainingPlanVersionId,
    val plannedExercises: List<PlannedExercise>,
)

/**
 * Starts a session from a training plan version and seeds every one of its
 * planned exercises, all-or-nothing (Milestone 8, implementation-review
 * finding #1: the previous `ActiveWorkoutViewModel.seedPlannedExercises`
 * silently skipped exercises that failed to resolve via `?: continue`,
 * ignored `addWorkoutExercise` failures, and always reported the original
 * [StartWorkoutSession] result as success - so a missing/archived exercise,
 * a persistence hiccup, or a process death mid-loop could leave a
 * persisted active session containing only part of the selected plan while
 * the UI reported success).
 *
 * The whole [WorkoutSession] aggregate - the session plus every seeded
 * [WorkoutExercise] - is built and validated entirely in memory first;
 * [WorkoutRepository.insert] is called exactly once, at the very end, with
 * the complete aggregate. [com.repflow.app.data.workout.LocalWorkoutRepository.insert]
 * already writes the session, its exercises, and their sets inside a single
 * `database.withTransaction` block, so this ordering - resolve and validate
 * everything, then persist once - is what makes the whole operation
 * atomic: any exercise that fails to resolve aborts before a single row is
 * written, and any persistence failure during the single `insert` call
 * rolls back the whole transaction via Room, never leaving a partial
 * session.
 *
 * The ad-hoc start path ([StartWorkoutSession], used when no plan is
 * selected) is untouched by this use case.
 */
class StartWorkoutSessionFromPlan
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val getExercise: GetExercise,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: StartWorkoutSessionFromPlanCommand): DomainResult<WorkoutSessionId, WorkoutOperationError> {
            if (repository.findActiveSession() != null) {
                return DomainResult.Failure(WorkoutOperationError.ActiveSessionAlreadyExists)
            }
            var session =
                WorkoutSession.start(
                    id = WorkoutSessionId(identifierGenerator.newId()),
                    trainingPlanVersionId = command.trainingPlanVersionId,
                    startedAt = clock.now(),
                )
            for (plannedExercise in command.plannedExercises.sortedBy { it.order }) {
                session = session.withSeededPlannedExercise(plannedExercise) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            }
            return when (val result = repository.insert(session)) {
                is DomainResult.Success -> DomainResult.Success(session.id)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }

        /** Resolves [plannedExercise]'s underlying [com.repflow.app.domain.exercise.Exercise] and appends it; `null` if resolution or a domain invariant fails. */
        @Suppress("ReturnCount")
        private suspend fun WorkoutSession.withSeededPlannedExercise(plannedExercise: PlannedExercise): WorkoutSession? {
            val exercise = (getExercise(plannedExercise.exerciseId) as? DomainResult.Success)?.value ?: return null
            val workoutExercise =
                WorkoutExercise
                    .create(
                        id = WorkoutExerciseId(identifierGenerator.newId()),
                        sessionId = id,
                        exerciseId = exercise.id,
                        order = nextExerciseOrder(),
                        exerciseNameSnapshot = exercise.name.value,
                        trackingType = exercise.trackingType,
                        plannedExerciseId = plannedExercise.id,
                    ).getOrElse { return null }
            return withAddedExercise(workoutExercise).getOrElse { return null }
        }
    }
