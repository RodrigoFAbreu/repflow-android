package com.repflow.app.presentation.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ArchiveExercise
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.RestoreExercise
import com.repflow.app.application.trainingplan.ArchiveTrainingPlan
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.RestoreTrainingPlan
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Drives the Archived screen (remediation-1-remediation-1 CP7, Q4) from the
 * existing archived observations of exercises and plans, restoring through
 * [RestoreExercise] / [RestoreTrainingPlan] - the same use cases the library's
 * and the plans' `Restore` call. A restore is persisted at once and the row
 * leaves because the observation re-emits without it; the snackbar's `Undo`
 * archives it again through the existing archive use cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchivedViewModel
    @Inject
    constructor(
        private val observeExercises: ObserveExercises,
        private val observeTrainingPlans: ObserveTrainingPlans,
        private val restoreExercise: RestoreExercise,
        private val restoreTrainingPlan: RestoreTrainingPlan,
        private val archiveExercise: ArchiveExercise,
        private val archiveTrainingPlan: ArchiveTrainingPlan,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        private val messages = MutableStateFlow<List<ArchivedMessage>>(emptyList())
        private val nextMessageId = AtomicLong(0)

        private val content =
            retryTrigger.flatMapLatest {
                combine(
                    observeExercises(ExerciseStatusFilter.ARCHIVED, ""),
                    observeTrainingPlans(TrainingPlanStatusFilter.ARCHIVED),
                ) { exercises, plans -> toContent(exercises, plans) }
                    .onStart { emit(ArchivedContent.Loading) }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(ArchivedContent.ObservationFailed)
                    }
            }

        val uiState =
            combine(content, messages) { c, msgs -> ArchivedUiState(c, msgs) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = ArchivedUiState(),
                )

        fun onRetry() {
            retryTrigger.update { it + 1 }
        }

        /** Restores [item]; on success queues `<name> restored · Undo`, on failure the generic message. */
        fun onRestoreClicked(item: ArchivedItem) {
            viewModelScope.launch {
                if (setArchived(item.target, archived = false)) {
                    enqueue(ArchivedMessage.Restored(nextId(), item.target, item.name))
                } else {
                    enqueue(ArchivedMessage.OperationFailed(nextId()))
                }
            }
        }

        /** The snackbar's `Undo`: archives the just-restored [target] again. */
        fun onUndoRestoreClicked(target: ArchivedTarget) {
            viewModelScope.launch {
                if (!setArchived(target, archived = true)) enqueue(ArchivedMessage.OperationFailed(nextId()))
            }
        }

        fun onMessageShown(messageId: Long) {
            messages.update { current -> current.filterNot { it.id == messageId } }
        }

        private fun toContent(
            exercises: List<Exercise>,
            plans: List<TrainingPlanOverview>,
        ): ArchivedContent =
            ArchivedContent.Loaded(
                exercises =
                    exercises
                        .map { ArchivedItem(ArchivedTarget.Exercise(it.id), it.name.value, it.archivedAt) }
                        .mostRecentlyArchivedFirst(),
                plans =
                    plans
                        .map { ArchivedItem(ArchivedTarget.Plan(it.plan.id), it.plan.name.value, it.plan.archivedAt) }
                        .mostRecentlyArchivedFirst(),
            )

        private suspend fun setArchived(
            target: ArchivedTarget,
            archived: Boolean,
        ): Boolean =
            when (target) {
                is ArchivedTarget.Exercise -> {
                    val result = if (archived) archiveExercise(target.id) else restoreExercise(target.id)
                    result is DomainResult.Success
                }

                is ArchivedTarget.Plan -> {
                    val result = if (archived) archiveTrainingPlan(target.id) else restoreTrainingPlan(target.id)
                    result is DomainResult.Success
                }
            }

        private fun enqueue(message: ArchivedMessage) {
            messages.update { it + message }
        }

        private fun nextId(): Long = nextMessageId.incrementAndGet()

        private fun List<ArchivedItem>.mostRecentlyArchivedFirst(): List<ArchivedItem> =
            sortedWith(compareByDescending<ArchivedItem> { it.archivedAt }.thenBy { it.name.lowercase() })

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
