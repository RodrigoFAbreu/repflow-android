package com.repflow.app.application.workout

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import java.time.Instant

/**
 * What the user last did on one exercise (remediation-1-remediation-1 CP9, Q8):
 * the last **working** set of the most recent valid session that logged it.
 * Seeds an untouched set entry and feeds focus mode's `Last time:` line.
 */
data class LastPerformance(
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    /** When the session it comes from started, as the Progress tab dates a session. */
    val date: Instant,
)

/**
 * Each exercise's [LastPerformance] over [sessions]. Pure and derived, never
 * stored. Only completed, non-invalidated sessions count, and a session is
 * passed over for an exercise unless it has at least one **working** set of it
 * (a skipped, warm-up-only or invalidated session never seeds anything), the
 * same "trained" rule the Progress read model uses. An exercise added twice to
 * one session reads as one: its last working set is the later occurrence's.
 */
fun lastPerformancesOf(sessions: List<WorkoutSession>): Map<ExerciseId, LastPerformance> {
    val result = mutableMapOf<ExerciseId, LastPerformance>()
    val newestFirst =
        sessions
            .filter { it.status == WorkoutSessionStatus.COMPLETED && !it.isInvalidated }
            .sortedByDescending { it.startedAt }
    for (session in newestFirst) {
        session.exercises
            .groupBy { it.exerciseId }
            .filterKeys { it !in result }
            .forEach { (exerciseId, occurrences) ->
                lastWorkingSet(occurrences)?.let { last ->
                    result[exerciseId] = LastPerformance(last.load, last.reps, last.durationSeconds, session.startedAt)
                }
            }
    }
    return result
}

/** The last working set across one session's occurrences of an exercise, in board then set order. */
private fun lastWorkingSet(occurrences: List<WorkoutExercise>): WorkoutSet? =
    occurrences
        .sortedBy { it.order }
        .flatMap { occurrence -> occurrence.sets.filterNot { it.isWarmup }.sortedBy { it.order } }
        .lastOrNull()
