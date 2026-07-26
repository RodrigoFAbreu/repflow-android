package com.repflow.app.presentation.exercise.editor

import com.repflow.app.domain.exercise.ExerciseId

/** Whether the editor is creating a brand-new exercise or editing an existing one. */
sealed interface ExerciseEditorMode {
    data object Create : ExerciseEditorMode

    data class Edit(
        val exerciseId: ExerciseId,
    ) : ExerciseEditorMode
}
