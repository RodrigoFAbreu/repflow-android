package com.repflow.app.application.trainingplan

/** Which kind of target a [PlannedExerciseInput] declares. */
enum class PlannedExerciseTargetKind {
    REPS,
    DURATION,
}

/**
 * Raw, UI-shaped input for one row in the training-plan editor, shared by
 * [CreateTrainingPlan] and [ReviseTrainingPlan].
 *
 * Only the fields matching [targetKind] are read; the others are ignored
 * (the editor UI only ever populates one pair at a time, driven by the
 * selected exercise's tracking type).
 */
data class PlannedExerciseInput(
    val exerciseId: String,
    val order: Int,
    val targetSets: Int,
    val targetKind: PlannedExerciseTargetKind,
    val repMin: Int?,
    val repMax: Int?,
    val durationMinSeconds: Long?,
    val durationMaxSeconds: Long?,
    val restSeconds: Long?,
    val isOptional: Boolean,
    val targetWarmupSets: Int? = null,
)
