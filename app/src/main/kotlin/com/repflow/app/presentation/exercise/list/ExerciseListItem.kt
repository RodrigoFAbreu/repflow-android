package com.repflow.app.presentation.exercise.list

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType

/**
 * A single list row's data. Tracking-type labels and rest/load unit
 * formatting are resolved in Composables from string resources (see plan.md
 * section H) - this type carries only raw domain values, no formatted text.
 */
data class ExerciseListItem(
    val id: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val defaultRestSeconds: Long?,
    val defaultLoadIncrementGrams: Long?,
)
