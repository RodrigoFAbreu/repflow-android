package com.repflow.app.presentation.trainingplan.editor

/**
 * Groups every per-row callback the editor form needs into a single object,
 * so [TrainingPlanEditorScreen] and [EditorForm] each take one parameter
 * instead of ten - keeping both under Detekt's `LongParameterList`
 * threshold while still exposing one distinct callback per field to the
 * row composable itself.
 */
data class TrainingPlanEditorRowActions(
    val onExerciseSelected: (rowId: Long, exerciseId: String) -> Unit,
    val onTargetSetsChanged: (rowId: Long, value: String) -> Unit,
    val onTargetWarmupSetsChanged: (rowId: Long, value: String) -> Unit,
    val onRepMinChanged: (rowId: Long, value: String) -> Unit,
    val onRepMaxChanged: (rowId: Long, value: String) -> Unit,
    val onDurationMinChanged: (rowId: Long, value: String) -> Unit,
    val onDurationMaxChanged: (rowId: Long, value: String) -> Unit,
    val onRestSecondsChanged: (rowId: Long, value: String) -> Unit,
    val onOptionalChanged: (rowId: Long, value: Boolean) -> Unit,
    val onMoveUp: (rowId: Long) -> Unit,
    val onMoveDown: (rowId: Long) -> Unit,
    val onRemove: (rowId: Long) -> Unit,
)
