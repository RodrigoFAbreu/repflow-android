package com.repflow.app.application.backup

import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.TrainingPlanVersionLabel
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Formats every completed workout session as a CSV string (one row per
 * recorded set) for external use in spreadsheets. A plain, manually-built
 * CSV - no new dependency for this scope (see the milestone reference's
 * architecture decisions).
 *
 * [pain]/[techniqueQuality] and a plan-origin identifier were added in
 * Milestone 8 (implementation-review finding #6): the milestone added
 * per-set pain/technique tracking and plan-linked workouts, but the export
 * still only carried reps/load/duration/RPE/warm-up/invalidation, so a
 * user inspecting their own CSV couldn't see data the app itself already
 * recorded. `plan_name` is resolved via [ObserveTrainingPlanVersionLabels]
 * - an application-layer capability, not a presentation-layer label - so
 * this stays deterministic and independent of any UI formatting.
 */
class ExportWorkoutHistoryCsv
    @Inject
    constructor(
        private val workoutRepository: WorkoutRepository,
        private val observeTrainingPlanVersionLabels: ObserveTrainingPlanVersionLabels,
    ) {
        suspend operator fun invoke(): String {
            // includeInvalidated = true: an export must never silently drop an
            // invalidated session's data (Milestone 8, CP11's "never physically
            // deleted" invariant) - the is_invalidated column below is how a reader
            // tells such a row apart from a normal one.
            val sessions = workoutRepository.observeCompletedSessions(includeInvalidated = true).first()
            val versionLabels = observeTrainingPlanVersionLabels().first()
            val builder = StringBuilder(HEADER)
            for (session in sessions) {
                appendSessionRows(builder, session, versionLabels)
            }
            return builder.toString()
        }

        private fun appendSessionRows(
            builder: StringBuilder,
            session: WorkoutSession,
            versionLabels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel>,
        ) {
            val planLabel = session.trainingPlanVersionId?.let { versionLabels[it] }
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
                        .append(',')
                        .append(csvField(set.pain?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(set.techniqueQuality?.toString().orEmpty()))
                        .append(',')
                        .append(csvField(planLabel?.planId?.value.orEmpty()))
                        .append(',')
                        .append(csvField(planLabel?.planName.orEmpty()))
                        .append(',')
                        .append(csvField(session.trainingPlanVersionId?.value.orEmpty()))
                        .append('\n')
                }
            }
        }

        /** Wraps [value] in double quotes, doubling any embedded quote, per RFC 4180. */
        private fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

        private companion object {
            const val HEADER =
                "session_id,started_at,ended_at,exercise_name,set_order,reps,load_kg,duration_seconds,rpe," +
                    "is_warmup,is_invalidated,pain,technique_quality,plan_id,plan_name,plan_version_id\n"
        }
    }
