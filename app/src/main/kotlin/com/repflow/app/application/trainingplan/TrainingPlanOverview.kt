package com.repflow.app.application.trainingplan

import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanVersion

/**
 * An application-level read model joining a [TrainingPlan] with its current
 * (highest [TrainingPlanVersion.versionNumber]) version.
 *
 * "Current version" is deliberately not state carried on [TrainingPlan]
 * itself (see that type's documentation) - this composite is how the
 * application and presentation layers observe "the plan as it stands
 * today" without the domain aggregate needing to track it. It is still a
 * plain composite of two domain types, not a Room type, so it does not
 * violate the "no persistence type crosses this boundary" rule.
 */
data class TrainingPlanOverview(
    val plan: TrainingPlan,
    val latestVersion: TrainingPlanVersion,
)
