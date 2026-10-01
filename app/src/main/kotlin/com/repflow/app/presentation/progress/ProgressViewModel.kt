package com.repflow.app.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.progress.ObserveExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.domain.exercise.ExerciseId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Progress tab (remediation-1 CP15): the live progress read model and
 * which exercise and metric are charted. The read model follows the history,
 * so invalidating a workout or finishing a new one updates the chart while the
 * tab is open.
 */
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        observeExerciseProgress: ObserveExerciseProgress,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProgressUiState())
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                observeExerciseProgress().collect { exercises ->
                    _uiState.update { it.copy(isLoading = false, exercises = exercises) }
                }
            }
        }

        fun onExerciseSelected(id: ExerciseId) {
            _uiState.update { it.copy(selectedExerciseId = id) }
        }

        fun onMetricSelected(metric: ProgressMetric) {
            _uiState.update { it.copy(selectedMetric = metric) }
        }
    }
