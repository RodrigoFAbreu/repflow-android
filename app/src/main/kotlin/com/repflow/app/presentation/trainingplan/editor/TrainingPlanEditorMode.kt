package com.repflow.app.presentation.trainingplan.editor

import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Mirrors [com.repflow.app.presentation.exercise.editor.ExerciseEditorMode]:
 * the ViewModel distinguishes "create" from "edit" purely from whether a
 * plan id argument is present in `SavedStateHandle`.
 */
sealed interface TrainingPlanEditorMode {
    data object Create : TrainingPlanEditorMode

    data class Edit(
        val planId: TrainingPlanId,
    ) : TrainingPlanEditorMode
}
