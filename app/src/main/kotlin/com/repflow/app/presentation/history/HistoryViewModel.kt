package com.repflow.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.history.ObserveWorkoutHistory
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the history list + read-only detail selection state. */
class HistoryViewModel
    @Inject
    constructor(
        private val observeWorkoutHistory: ObserveWorkoutHistory,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HistoryUiState())
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                observeWorkoutHistory().collect { sessions ->
                    _uiState.update { it.copy(isLoading = false, sessions = sessions) }
                }
            }
        }

        fun onSessionClick(id: WorkoutSessionId) {
            _uiState.update { it.copy(selectedSessionId = id) }
        }

        fun onDetailDismissed() {
            _uiState.update { it.copy(selectedSessionId = null) }
        }
    }
