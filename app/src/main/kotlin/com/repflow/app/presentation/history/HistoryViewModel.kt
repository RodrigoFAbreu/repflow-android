package com.repflow.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.history.ObserveWorkoutHistory
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InvalidateWorkoutSession
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/** Owns the history list, its filter/sort selection, and the read-only detail selection state. */
@Suppress("TooManyFunctions")
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val observeWorkoutHistory: ObserveWorkoutHistory,
        private val observeTrainingPlanVersionLabels: ObserveTrainingPlanVersionLabels,
        private val invalidateWorkoutSession: InvalidateWorkoutSession,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HistoryUiState())
        val uiState = _uiState.asStateFlow()
        private val nextMessageId = AtomicLong(0)

        // Sessions and version labels are combined into one continuous subscription rather
        // than loaded independently (Milestone 8, implementation-review finding #5): labels
        // used to be a one-shot `suspend` read taken once in `init`, so a backup restore,
        // plan rename, or archive/restore while this screen stayed open left them stale
        // even though sessions kept updating live.
        init {
            viewModelScope.launch {
                combine(observeWorkoutHistory(), observeTrainingPlanVersionLabels()) { sessions, labels -> sessions to labels }
                    .collect { (sessions, labels) ->
                        _uiState.update { it.copy(isLoading = false, sessions = sessions, versionLabels = labels) }
                    }
            }
        }

        fun onSessionClick(id: WorkoutSessionId) {
            _uiState.update { it.copy(selectedSessionId = id) }
        }

        fun onDetailDismissed() {
            _uiState.update { it.copy(selectedSessionId = null) }
        }

        fun onExerciseFilterChanged(exerciseId: ExerciseId?) {
            updateFilters { it.copy(exerciseId = exerciseId) }
        }

        fun onPlanFilterChanged(plan: HistoryPlanFilter) {
            updateFilters { it.copy(plan = plan) }
        }

        fun onStartDateChanged(date: LocalDate?) {
            updateFilters { it.copy(startDate = date) }
        }

        fun onEndDateChanged(date: LocalDate?) {
            updateFilters { it.copy(endDate = date) }
        }

        fun onShowInvalidatedChanged(showInvalidated: Boolean) {
            updateFilters { it.copy(showInvalidated = showInvalidated) }
        }

        fun onSortOrderChanged(sortOrder: HistorySortOrder) {
            updateFilters { it.copy(sortOrder = sortOrder) }
        }

        private inline fun updateFilters(transform: (HistoryFilters) -> HistoryFilters) {
            _uiState.update { it.copy(filters = transform(it.filters)) }
        }

        /**
         * Invalidates a completed session as a correction for a wrongly
         * recorded workout - it disappears from the default (hide-invalidated)
         * view once excluded by [HistoryUiState.visibleSessions], but the row
         * and its sets are never deleted, and remain visible via the
         * show-invalidated filter. The UI confirms with the user before
         * calling this (no Undo path exists yet, unlike Exercise archive).
         */
        fun onInvalidateClicked(id: WorkoutSessionId) {
            viewModelScope.launch {
                when (invalidateWorkoutSession(id)) {
                    is DomainResult.Success -> {
                        enqueue(HistoryMessage.Invalidated(nextId()))
                        if (_uiState.value.selectedSessionId == id) {
                            _uiState.update { it.copy(selectedSessionId = null) }
                        }
                    }

                    is DomainResult.Failure -> {
                        enqueue(HistoryMessage.OperationFailed(nextId()))
                    }
                }
            }
        }

        fun onMessageShown(messageId: Long) {
            _uiState.update { it.copy(messages = it.messages.filterNot { message -> message.id == messageId }) }
        }

        private fun enqueue(message: HistoryMessage) {
            _uiState.update { it.copy(messages = it.messages + message) }
        }

        private fun nextId(): Long = nextMessageId.incrementAndGet()
    }
