package com.repflow.app.application.trainingplan

import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A training plan's stable identity and current display name, for labeling
 * one of its historical versions.
 *
 * @property versionNumber the labelled version's own number - History's
 *   `<plan> · version N` line (remediation-1 CP12).
 */
data class TrainingPlanVersionLabel(
    val planId: TrainingPlanId,
    val planName: String,
    val versionNumber: Int,
)

/**
 * Resolves every training-plan version ever created back to its owning
 * plan's identity and current name, reactively - delegates to
 * [TrainingPlanRepository.observeVersionLabels] (Milestone 8,
 * implementation-review finding #5: this was previously a one-shot
 * `suspend` read taken once in `HistoryViewModel.init`, so a backup
 * restore, plan rename, or archive/restore while History stayed open left
 * the labels stale even though sessions kept updating live).
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
        operator fun invoke(): Flow<Map<TrainingPlanVersionId, TrainingPlanVersionLabel>> = repository.observeVersionLabels()
    }
