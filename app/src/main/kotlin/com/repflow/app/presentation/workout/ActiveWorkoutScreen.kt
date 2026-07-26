package com.repflow.app.presentation.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.workout.WorkoutSessionId

/**
 * Stateless current-workout screen: state in, events out (see
 * [com.repflow.app.presentation.trainingplan.list.TrainingPlanListScreen] for
 * the same shape). Fast set entry is Milestone 3 CP7 - this screen only
 * covers start/resume/complete/abandon (CP6 scope).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    onStartWorkout: () -> Unit,
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
                is ActiveWorkoutContent.Loading -> LoadingIndicator()
                is ActiveWorkoutContent.NoActiveSession -> NoActiveSessionState(onStartWorkout)
                is ActiveWorkoutContent.Active -> ActiveSessionState(content, onCompleteWorkout, onAbandonWorkout)
                is ActiveWorkoutContent.ObservationFailed -> FailureState(onRetry)
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
private fun NoActiveSessionState(onStartWorkout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.workout_active_no_session))
        Button(onClick = onStartWorkout) {
            Text(stringResource(R.string.workout_active_start))
        }
    }
}

@Composable
private fun ActiveSessionState(
    content: ActiveWorkoutContent.Active,
    onCompleteWorkout: (WorkoutSessionId) -> Unit,
    onAbandonWorkout: (WorkoutSessionId) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.workout_active_exercise_count, content.exerciseCount))
        Text(stringResource(R.string.workout_active_set_count, content.setCount))
        Button(onClick = { onCompleteWorkout(content.sessionId) }) {
            Text(stringResource(R.string.workout_active_complete))
        }
        OutlinedButton(onClick = { onAbandonWorkout(content.sessionId) }) {
            Text(stringResource(R.string.workout_active_abandon))
        }
    }
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
