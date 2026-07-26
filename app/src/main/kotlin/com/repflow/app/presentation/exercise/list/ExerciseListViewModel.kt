package com.repflow.app.presentation.exercise.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ArchiveExercise
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.RestoreExercise
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
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
 * Drives [ExerciseListUiState] from [ObserveExercises] (see plan.md section
 * H for the retry design).
 *
 * [criteria] and [retryTrigger] are combined so that changing the query or
 * filter, or calling [onRetry], all resubscribe to a fresh repository
 * `Flow` via `flatMapLatest` - a Room `Flow` that throws terminates at the
 * source, so visible recovery requires exactly this kind of explicit
 * resubscription (D-24). The whole [ExerciseListUiState] (query, filter and
 * content together) is built inside the single `flatMapLatest` chain rather
 * than combined from two independent flows, so a filter/query change can
 * never be paired with stale content from before the change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseListViewModel
    @Inject
    constructor(
        private val observeExercises: ObserveExercises,
        private val archiveExercise: ArchiveExercise,
        private val restoreExercise: RestoreExercise,
    ) : ViewModel() {
        private data class Criteria(
            val query: String,
            val filter: ExerciseStatusFilter,
        )

        private data class ContentState(
            val query: String,
            val filter: ExerciseStatusFilter,
            val content: ExerciseListContent,
        )

        private val criteria = MutableStateFlow(Criteria(query = "", filter = ExerciseStatusFilter.ACTIVE))
        private val retryTrigger = MutableStateFlow(0)
        private val messages = MutableStateFlow<List<ExerciseListMessage>>(emptyList())
        private val nextMessageId = AtomicLong(0)

        private val contentState =
            combine(criteria, retryTrigger) { c, _ -> c }
                .flatMapLatest { c ->
                    observeExercises(c.filter, c.query)
                        .map { exercises -> ContentState(c.query, c.filter, toContent(exercises, c)) }
                        .onStart { emit(ContentState(c.query, c.filter, ExerciseListContent.Loading)) }
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            val failed = ExerciseListContent.ObservationFailed(ExerciseListFailureReason.UNKNOWN)
                            emit(ContentState(c.query, c.filter, failed))
                        }
                }

        val uiState =
            combine(contentState, messages) { cs, msgs ->
                ExerciseListUiState(cs.query, cs.filter, cs.content, msgs)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExerciseListUiState(),
            )

        private fun toContent(
            exercises: List<Exercise>,
            criteria: Criteria,
        ): ExerciseListContent {
            if (exercises.isNotEmpty()) {
                return ExerciseListContent.Content(exercises.map(::toListItem))
            }
            val reason =
                when {
                    criteria.filter == ExerciseStatusFilter.ARCHIVED -> ExerciseListEmptyReason.NO_ARCHIVED
                    criteria.query.isNotBlank() -> ExerciseListEmptyReason.NO_SEARCH_RESULTS
                    else -> ExerciseListEmptyReason.NO_EXERCISES
                }
            return ExerciseListContent.Empty(reason)
        }

        private fun toListItem(exercise: Exercise) =
            ExerciseListItem(
                id = exercise.id,
                name = exercise.name.value,
                trackingType = exercise.trackingType,
                defaultRestSeconds = exercise.defaultRestDuration?.seconds,
                defaultLoadIncrementGrams = exercise.defaultLoadIncrement?.grams,
            )

        fun onQueryChanged(query: String) {
            criteria.update { it.copy(query = query) }
        }

        fun onFilterChanged(filter: ExerciseStatusFilter) {
            criteria.update { it.copy(filter = filter) }
        }

        fun onRetry() {
            retryTrigger.update { it + 1 }
        }

        /**
         * Archives immediately (one tap, no confirmation) and queues an
         * [ExerciseListMessage.Archived] snackbar with Undo (plan.md section
         * B). The archive is persisted right away - Undo below issues a real
         * [RestoreExercise] call rather than cancelling a pending write.
         */
        fun onArchiveClicked(id: ExerciseId) {
            viewModelScope.launch {
                when (archiveExercise(id)) {
                    is DomainResult.Success -> enqueue(ExerciseListMessage.Archived(nextId(), id))
                    is DomainResult.Failure -> enqueue(ExerciseListMessage.OperationFailed(nextId()))
                }
            }
        }

        /**
         * Restores an archived exercise. Backs both the archived filter's
         * "Restore" action and the archive snackbar's "Undo" action; both
         * are safe to call even if the exercise is already active
         * (idempotent restore, correction #8).
         */
        fun onRestoreClicked(id: ExerciseId) {
            viewModelScope.launch {
                if (restoreExercise(id) is DomainResult.Failure) {
                    enqueue(ExerciseListMessage.OperationFailed(nextId()))
                }
            }
        }

        fun onMessageShown(messageId: Long) {
            messages.update { current -> current.filterNot { it.id == messageId } }
        }

        private fun enqueue(message: ExerciseListMessage) {
            messages.update { it + message }
        }

        private fun nextId(): Long = nextMessageId.incrementAndGet()

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
