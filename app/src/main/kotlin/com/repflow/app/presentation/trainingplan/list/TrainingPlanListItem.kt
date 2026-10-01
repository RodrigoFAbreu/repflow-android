package com.repflow.app.presentation.trainingplan.list

import com.repflow.app.domain.trainingplan.TrainingPlanId
import java.time.Instant

/**
 * A single plan card's data. Mirrors [com.repflow.app.presentation.exercise.list.ExerciseListItem]:
 * only raw values, no formatted text - the count, version and archive date are
 * turned into words by the Composable via string resources.
 *
 * [versionNumber] is the latest version's (`5a`'s `v4`); [archivedAt] is the
 * plan's own archive instant, null while it is active (`5a`'s `Archived 2 Feb`
 * note).
 */
data class TrainingPlanListItem(
    val id: TrainingPlanId,
    val name: String,
    val plannedExerciseCount: Int,
    val versionNumber: Int,
    val archivedAt: Instant?,
)
