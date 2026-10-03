package com.repflow.app.presentation.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.common.Clock
import com.repflow.app.application.progress.ObserveExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.domain.exercise.ExerciseId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Owns the Progress tab (`5b`): the live progress read model and which exercise,
 * metric and range are charted. The read model follows the history, so
 * invalidating a workout or finishing a new one updates the chart while the
 * tab is open. The three choices live in [SavedStateHandle] (nothing is stored
 * on the device), so a rotation or process death keeps them.
 */
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        observeExerciseProgress: ObserveExerciseProgress,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                ProgressUiState(
                    selectedExerciseId = savedStateHandle.get<String>(KEY_EXERCISE)?.let(::ExerciseId),
                    selectedMetric = decodeMetric(savedStateHandle.get<String>(KEY_METRIC)),
                    range = decodeRange(savedStateHandle.get<String>(KEY_RANGE)),
                    now = clock.now(),
                    zone = ZoneId.systemDefault(),
                ),
            )
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                observeExerciseProgress().collect { exercises ->
                    _uiState.update { it.copy(isLoading = false, exercises = exercises, now = clock.now()) }
                }
            }
        }

        fun onExerciseSelected(id: ExerciseId) {
            savedStateHandle[KEY_EXERCISE] = id.value
            _uiState.update { it.copy(selectedExerciseId = id) }
        }

        fun onMetricSelected(metric: ProgressMetric) {
            savedStateHandle[KEY_METRIC] = metric.name
            _uiState.update { it.copy(selectedMetric = metric) }
        }

        fun onRangeSelected(range: ProgressRange) {
            savedStateHandle[KEY_RANGE] = range.name
            _uiState.update { it.copy(range = range, now = clock.now()) }
        }

        private companion object {
            const val KEY_EXERCISE = "progress.exercise"
            const val KEY_METRIC = "progress.metric"
            const val KEY_RANGE = "progress.range"

            fun decodeMetric(name: String?): ProgressMetric =
                ProgressMetric.entries.firstOrNull { it.name == name } ?: ProgressMetric.TOP_SET

            fun decodeRange(name: String?): ProgressRange = ProgressRange.entries.firstOrNull { it.name == name } ?: ProgressRange.ALL
        }
    }
