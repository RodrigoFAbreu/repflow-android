package com.repflow.app.application.trainingplan

import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import javax.inject.Inject

/** A training plan's stable identity and current display name, for labeling one of its historical versions. */
data class TrainingPlanVersionLabel(
    val planId: TrainingPlanId,
    val planName: String,
)

/**
 * Resolves every training-plan version ever created back to its owning
 * plan's identity and current name - reuses
 * [TrainingPlanRepository.findAllForBackup]'s existing "every plan, every
 * version" capability (Milestone 7) rather than adding a new one.
 *
 * A [com.repflow.app.domain.workout.WorkoutSession] only ever carries the
 * exact [TrainingPlanVersionId] it was started from (never re-pointed at a
 * later revision), so History's training-plan filter (Milestone 8, CP13)
 * needs to resolve an old session's version id back to its plan even if
 * that version is no longer the latest one, or the plan itself is now
 * archived - `ObserveTrainingPlans`, which only exposes each plan's latest
 * version, cannot do that.
 */
class ObserveTrainingPlanVersionLabels
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
    ) {
        suspend operator fun invoke(): Map<TrainingPlanVersionId, TrainingPlanVersionLabel> =
            repository
                .findAllForBackup()
                .flatMap { snapshot ->
                    val label = TrainingPlanVersionLabel(snapshot.plan.id, snapshot.plan.name.value)
                    snapshot.versions.map { version -> version.id to label }
                }.toMap()
    }
