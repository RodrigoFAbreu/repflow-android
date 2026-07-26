package com.repflow.app.presentation.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId

/**
 * Stateless current-workout screen: state in, events out (see
 * [com.repflow.app.presentation.trainingplan.list.TrainingPlanListScreen] for
 * the same shape). Covers start/resume (CP6) and fast set entry with
 * edit/undo of the most recently recorded set (CP7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    dayContext: WorkoutDayContextUi?,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onOverrideRecommendation: (ExerciseId, ProgressionResultUi) -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
    onAddRestTime: () -> Unit,
    onRemoveRestTime: () -> Unit,
    onSkipRestTimer: () -> Unit,
    onCompleteWorkout: (WorkoutSessionId) -> Unit,
    onAbandonWorkout: (WorkoutSessionId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.workout_active_title)) }) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val content = uiState.content) {
                is ActiveWorkoutContent.Loading -> {
                    LoadingIndicator()
                }

                is ActiveWorkoutContent.NoActiveSession -> {
                    NoActiveSessionState(uiState.availablePlans, onStartWorkout)
                }

                is ActiveWorkoutContent.Active -> {
                    ActiveSessionState(
                        content = content,
                        availableExercises = uiState.availableExercises,
                        dayContext = dayContext,
                        onAddExercise = onAddExercise,
                        onOverrideRecommendation = onOverrideRecommendation,
                        onRecordSet = onRecordSet,
                        onUndoLastSet = onUndoLastSet,
                        onEditLastSet = onEditLastSet,
                        onAddRestTime = onAddRestTime,
                        onRemoveRestTime = onRemoveRestTime,
                        onSkipRestTimer = onSkipRestTimer,
                        onCompleteWorkout = onCompleteWorkout,
                        onAbandonWorkout = onAbandonWorkout,
                    )
                }

                is ActiveWorkoutContent.ObservationFailed -> {
                    FailureState(onRetry)
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoActiveSessionState(
    availablePlans: List<TrainingPlanPickerItem>,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.workout_active_no_session))
        Box {
            Button(onClick = { expanded = true }) {
                Text(stringResource(R.string.workout_active_start))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workout_active_start_ad_hoc)) },
                    onClick = {
                        expanded = false
                        onStartWorkout(null)
                    },
                )
                availablePlans.forEach { plan ->
                    DropdownMenuItem(
                        text = { Text(plan.planName) },
                        onClick = {
                            expanded = false
                            onStartWorkout(plan.versionId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionState(
    content: ActiveWorkoutContent.Active,
    availableExercises: List<ExercisePickerItem>,
    dayContext: WorkoutDayContextUi?,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onOverrideRecommendation: (ExerciseId, ProgressionResultUi) -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
    onAddRestTime: () -> Unit,
    onRemoveRestTime: () -> Unit,
    onSkipRestTimer: () -> Unit,
    onCompleteWorkout: (WorkoutSessionId) -> Unit,
    onAbandonWorkout: (WorkoutSessionId) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (dayContext?.heavyLegs != null || dayContext?.legDoms != null || dayContext?.futsalLoad != null) {
            Column(modifier = Modifier.padding(8.dp)) {
                dayContext.heavyLegs?.let { Text(stringResource(R.string.workout_day_context_heavy_legs, it)) }
                dayContext.legDoms?.let { Text(stringResource(R.string.workout_day_context_leg_doms, it)) }
                dayContext.futsalLoad?.let { Text(stringResource(R.string.workout_day_context_futsal_load, it)) }
            }
        }
        content.restTimer?.let { timer ->
            RestTimerBar(timer, onAddRestTime, onRemoveRestTime, onSkipRestTimer)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = content.exercises, key = { it.id.value }) { exercise ->
                ExerciseCard(exercise, onRecordSet, onUndoLastSet, onEditLastSet)
            }
            item { AddExercisePicker(availableExercises, onAddExercise, onOverrideRecommendation) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = { onCompleteWorkout(content.sessionId) }) {
                Text(stringResource(R.string.workout_active_complete))
            }
            OutlinedButton(onClick = { onAbandonWorkout(content.sessionId) }) {
                Text(stringResource(R.string.workout_active_abandon))
            }
        }
    }
}

@Composable
private fun RestTimerBar(
    timer: RestTimerUi,
    onAddRestTime: () -> Unit,
    onRemoveRestTime: () -> Unit,
    onSkipRestTimer: () -> Unit,
) {
    var remainingSeconds by remember(timer.endAt) {
        mutableStateOf(
            (
                timer.endAt.epochSecond -
                    java.time.Instant
                        .now()
                        .epochSecond
            ).coerceAtLeast(0),
        )
    }
    androidx.compose.runtime.LaunchedEffect(timer.endAt) {
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(TICK_INTERVAL_MILLIS)
            remainingSeconds =
                (
                    timer.endAt.epochSecond -
                        java.time.Instant
                            .now()
                            .epochSecond
                ).coerceAtLeast(0)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(
                R.string.workout_active_rest_timer_remaining,
                remainingSeconds / MINUTE_SECONDS,
                remainingSeconds % MINUTE_SECONDS,
            ),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemoveRestTime) { Text(stringResource(R.string.workout_active_rest_timer_remove)) }
        TextButton(onClick = onAddRestTime) { Text(stringResource(R.string.workout_active_rest_timer_add)) }
        TextButton(onClick = onSkipRestTimer) { Text(stringResource(R.string.workout_active_rest_timer_skip)) }
    }
}

private const val MINUTE_SECONDS = 60L
private const val TICK_INTERVAL_MILLIS = 1_000L

@Composable
private fun ExerciseCard(
    exercise: ActiveExerciseUi,
    onRecordSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?) -> Unit,
) {
    var loadText by remember(exercise.id) { mutableStateOf("") }
    var repsText by remember(exercise.id) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(exercise.name)
        exercise.sets.forEach { set ->
            Text(stringResource(R.string.workout_active_set_row, set.setNumber, set.load ?: 0.0, set.reps ?: 0))
        }
        Row {
            OutlinedTextField(
                value = loadText,
                onValueChange = { loadText = it },
                label = { Text(stringResource(R.string.workout_active_load_label)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = repsText,
                onValueChange = { repsText = it },
                label = { Text(stringResource(R.string.workout_active_reps_label)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row {
            Button(onClick = { onRecordSet(exercise.id, loadText.toDoubleOrNull(), repsText.toIntOrNull()) }) {
                Text(stringResource(R.string.workout_active_add_set))
            }
            if (exercise.sets.isNotEmpty()) {
                TextButton(onClick = { onUndoLastSet(exercise.id) }) {
                    Text(stringResource(R.string.workout_active_undo_set))
                }
                TextButton(
                    onClick = { onEditLastSet(exercise.id, loadText.toDoubleOrNull(), repsText.toIntOrNull()) },
                ) {
                    Text(stringResource(R.string.workout_active_edit_set))
                }
            }
        }
    }
}

@Composable
private fun AddExercisePicker(
    availableExercises: List<ExercisePickerItem>,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onOverrideRecommendation: (ExerciseId, ProgressionResultUi) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.workout_active_add_exercise))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableExercises.forEach { exercise ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(exercise.name)
                            exercise.recommendation?.let { recommendation ->
                                RecommendationRow(exercise.id, recommendation, onOverrideRecommendation)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onAddExercise(exercise)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecommendationRow(
    exerciseId: ExerciseId,
    recommendation: ProgressionRecommendationUi,
    onOverrideRecommendation: (ExerciseId, ProgressionResultUi) -> Unit,
) {
    Text(
        text =
            stringResource(recommendation.result.toLabelRes()) +
                (recommendation.topReason?.let { " — $it" } ?: "") +
                if (recommendation.isOverridden) " (${stringResource(R.string.progression_overridden)})" else "",
        style = MaterialTheme.typography.bodySmall,
    )
    Row {
        TextButton(onClick = { onOverrideRecommendation(exerciseId, ProgressionResultUi.INCREASE_LOAD) }) {
            Text(stringResource(R.string.progression_result_increase_load), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { onOverrideRecommendation(exerciseId, ProgressionResultUi.MAINTAIN_LOAD) }) {
            Text(stringResource(R.string.progression_result_maintain_load), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { onOverrideRecommendation(exerciseId, ProgressionResultUi.REDUCE_LOAD) }) {
            Text(stringResource(R.string.progression_result_reduce_load), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun ProgressionResultUi.toLabelRes(): Int =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> R.string.progression_result_increase_load
        ProgressionResultUi.MAINTAIN_LOAD -> R.string.progression_result_maintain_load
        ProgressionResultUi.REDUCE_LOAD -> R.string.progression_result_reduce_load
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> R.string.progression_result_recovery_adjustment
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> R.string.progression_result_wait_for_more_data
    }

@Composable
private fun FailureState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.workout_active_observation_failed))
        }
        Button(onClick = onRetry) {
            Text(stringResource(R.string.workout_active_retry))
        }
    }
}
