package com.repflow.app.presentation.archived

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanId
import java.time.Instant

/** What a restore (or its undo) acts on: an archived exercise or an archived plan. */
sealed interface ArchivedTarget {
    data class Exercise(
        val id: ExerciseId,
    ) : ArchivedTarget

    data class Plan(
        val id: TrainingPlanId,
    ) : ArchivedTarget
}

/** One archived row: the thing it restores, its name, and when it was archived. */
data class ArchivedItem(
    val target: ArchivedTarget,
    val name: String,
    val archivedAt: Instant?,
)

/** The Archived screen's content. Both lists are most recently archived first. */
sealed interface ArchivedContent {
    data object Loading : ArchivedContent

    data class Loaded(
        val exercises: List<ArchivedItem>,
        val plans: List<ArchivedItem>,
    ) : ArchivedContent

    data object ObservationFailed : ArchivedContent
}

/** A one-off outcome for the snackbar. [id] is unique and monotonic, so a queued message is never lost. */
sealed interface ArchivedMessage {
    val id: Long

    /** [name] was restored; the snackbar's `Undo` archives [target] again. */
    data class Restored(
        override val id: Long,
        val target: ArchivedTarget,
        val name: String,
    ) : ArchivedMessage

    data class OperationFailed(
        override val id: Long,
    ) : ArchivedMessage
}

data class ArchivedUiState(
    val content: ArchivedContent = ArchivedContent.Loading,
    val messages: List<ArchivedMessage> = emptyList(),
)
