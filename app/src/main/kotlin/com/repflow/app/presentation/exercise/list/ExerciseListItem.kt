package com.repflow.app.presentation.exercise.list

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType

/**
 * A single list row's data. Tracking-type labels and rest/load unit
 * formatting are resolved in Composables from string resources (see plan.md
 * section H) - this type carries only raw domain values, no formatted text.
 *
 * [planUsageCount] is how many non-archived plans hold the exercise in their
 * latest version (`2c`'s `in 2 plans`, remediation-1 CP10). It deliberately
 * has no default, so every construction site has to say where its count comes
 * from rather than silently showing `not in any plan`.
 */
data class ExerciseListItem(
    val id: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val defaultRestSeconds: Long?,
    val defaultLoadIncrementGrams: Long?,
    val planUsageCount: Int,
)
