package com.repflow.app.presentation.trainingplan.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.trainingplan.ArchiveTrainingPlan
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.RestoreTrainingPlan
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Drives [TrainingPlanListUiState] from [ObserveTrainingPlans], mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListViewModel]'s
 * active/archived filter and archive/restore/Undo shape (Milestone 8, CP12) -
 * minus the search query, which plans still don't have.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrainingPlanListViewModel
    @Inject
    constructor(
        private val observeTrainingPlans: ObserveTrainingPlans,
        private val archiveTrainingPlan: ArchiveTrainingPlan,
        private val restoreTrainingPlan: RestoreTrainingPlan,
    ) : ViewModel() {
        private val filter = MutableStateFlow(TrainingPlanStatusFilter.ACTIVE)
        private val retryTrigger = MutableStateFlow(0)
        private val messages = MutableStateFlow<List<TrainingPlanListMessage>>(emptyList())
        private val nextMessageId = AtomicLong(0)

        private data class ContentState(
            val filter: TrainingPlanStatusFilter,
            val content: TrainingPlanListContent,
        )

        private val contentState =
            combine(filter, retryTrigger) { f, _ -> f }
                .flatMapLatest { f ->
                    observeTrainingPlans(f)
                        .map { overviews -> ContentState(f, toContent(overviews, f)) }
                        .onStart { emit(ContentState(f, TrainingPlanListContent.Loading)) }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            val failed = TrainingPlanListContent.ObservationFailed(TrainingPlanListFailureReason.UNKNOWN)
                            emit(ContentState(f, failed))
                        }
                }

        val uiState =
            combine(contentState, messages) { cs, msgs ->
                TrainingPlanListUiState(cs.filter, cs.content, msgs)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrainingPlanListUiState(),
            )

        fun onFilterChanged(newFilter: TrainingPlanStatusFilter) {
            filter.update { newFilter }
        }

        fun onRetry() {
            retryTrigger.update { it + 1 }
        }

        /**
         * Archives immediately (one tap, no confirmation) and queues a
         * [TrainingPlanListMessage.Archived] snackbar with Undo, mirroring
         * [com.repflow.app.presentation.exercise.list.ExerciseListViewModel.onArchiveClicked].
         * Unlike a completed workout session (Milestone 8, CP11), a plan's
         * archive/restore is fully reversible, so the one-tap-plus-Undo
         * pattern is safe here.
         */
        fun onArchiveClicked(id: TrainingPlanId) {
            viewModelScope.launch {
                when (archiveTrainingPlan(id)) {
                    is DomainResult.Success -> enqueue(TrainingPlanListMessage.Archived(nextId(), id))
                    is DomainResult.Failure -> enqueue(TrainingPlanListMessage.OperationFailed(nextId()))
                }
            }
        }

        /** Restores an archived plan. Backs both the archived filter's "Restore" action and the archive snackbar's "Undo" action. */
        fun onRestoreClicked(id: TrainingPlanId) {
            viewModelScope.launch {
                if (restoreTrainingPlan(id) is DomainResult.Failure) {
                    enqueue(TrainingPlanListMessage.OperationFailed(nextId()))
                }
            }
        }

        fun onMessageShown(messageId: Long) {
            messages.update { current -> current.filterNot { it.id == messageId } }
        }

        private fun toContent(
            overviews: List<TrainingPlanOverview>,
            filter: TrainingPlanStatusFilter,
        ): TrainingPlanListContent {
            if (overviews.isNotEmpty()) {
                return TrainingPlanListContent.Content(overviews.map(::toListItem))
            }
            val reason =
                if (filter == TrainingPlanStatusFilter.ARCHIVED) {
                    TrainingPlanListEmptyReason.NO_ARCHIVED
                } else {
                    TrainingPlanListEmptyReason.NO_PLANS
                }
            return TrainingPlanListContent.Empty(reason)
        }

        private fun toListItem(overview: TrainingPlanOverview) =
            TrainingPlanListItem(
                id = overview.plan.id,
                name = overview.plan.name.value,
                plannedExerciseCount = overview.latestVersion.plannedExercises.size,
            )

        private fun enqueue(message: TrainingPlanListMessage) {
            messages.update { it + message }
        }

        private fun nextId(): Long = nextMessageId.incrementAndGet()

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
