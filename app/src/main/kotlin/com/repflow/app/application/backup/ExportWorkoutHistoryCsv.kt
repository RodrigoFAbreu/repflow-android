package com.repflow.app.application.backup

import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Formats every completed workout session as a CSV string (one row per
 * recorded set) for external use in spreadsheets. A plain, manually-built
 * CSV - no new dependency for this scope (see the milestone reference's
 * architecture decisions).
 */
class ExportWorkoutHistoryCsv
    @Inject
    constructor(
        private val workoutRepository: WorkoutRepository,
    ) {
        suspend operator fun invoke(): String {
            // includeInvalidated = true: an export must never silently drop an
            // invalidated session's data (Milestone 8, CP11's "never physically
            // deleted" invariant) - the is_invalidated column below is how a reader
            // tells such a row apart from a normal one.
            val sessions = workoutRepository.observeCompletedSessions(includeInvalidated = true).first()
            val builder = StringBuilder(HEADER)
            for (session in sessions) {
                appendSessionRows(builder, session)
            }
            return builder.toString()
        }

        private fun appendSessionRows(
            builder: StringBuilder,
            session: WorkoutSession,
        ) {
            for (exercise in session.exercises) {
                for (set in exercise.sets) {
                    builder
                        .append(csvField(session.id.value))
                        .append(',')
                        .append(csvField(session.startedAt.toString()))
                        .append(',')
                        .append(csvField(session.endedAt?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(exercise.exerciseNameSnapshot))
                        .append(',')
                        .append(set.order)
                        .append(',')
                        .append(csvField(set.reps?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(set.load?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(set.durationSeconds?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(set.rpe?.toString().orEmpty()))
                        .append(',')
                        .append(set.isWarmup)
                        .append(',')
                        .append(session.isInvalidated)
                        .append('\n')
                }
            }
        }

        /** Wraps [value] in double quotes, doubling any embedded quote, per RFC 4180. */
        private fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

        private companion object {
            const val HEADER =
                "session_id,started_at,ended_at,exercise_name,set_order,reps,load_kg,duration_seconds,rpe,is_warmup,is_invalidated\n"
        }
    }
