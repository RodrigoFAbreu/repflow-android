package com.repflow.app.domain.backup

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.workout.WorkoutSession

/**
 * An explicit, versioned transfer schema for a full local-data export, per
 * `docs/TECHNICAL_DECISIONS.md`'s "Backup" decision and
 * `docs/DOMAIN_GLOSSARY.md`'s "Backup schema version": independent of the
 * Room database version and the app version, and never a direct
 * serialization of Room entities.
 *
 * **It carries the user's training data and nothing else.** The device's
 * settings (remediation-1 CP14: rest-timer and workout preferences) are
 * deliberately excluded - they are device settings, not training data - so a
 * restored backup applies the receiving device's own settings, and the
 * restore leaves them untouched. Adding them was considered and rejected; it
 * is a decision, not an omission.
 */
@ConsistentCopyVisibility
data class BackupSnapshot private constructor(
    val schemaVersion: Int,
    val exercises: List<Exercise>,
    val trainingPlans: List<TrainingPlanSnapshot>,
    val workoutSessions: List<WorkoutSession>,
    val recoveryEntries: List<RecoveryEntry>,
    val futsalSessions: List<FutsalSession>,
    val progressionRecommendations: List<ProgressionRecommendation>,
) {
    companion object {
        /**
         * Bumped from 1 to 2 in Milestone 8 (CP14) when the JSON mapper
         * gained five new nullable fields (`WorkoutSet.pain`/`techniqueQuality`,
         * `PlannedExercise.targetWarmupSets`, `WorkoutSession.invalidatedAt`,
         * `TrainingPlan.archivedAt`). A schema-version-1 backup still parses
         * cleanly - every new field is read via a null-safe optional lookup,
         * so a missing key comes back `null` exactly like an explicit one
         * would, with no version-branching logic anywhere in the mapper.
         */
        const val CURRENT_SCHEMA_VERSION = 2

        @Suppress("LongParameterList")
        fun create(
            schemaVersion: Int,
            exercises: List<Exercise>,
            trainingPlans: List<TrainingPlanSnapshot>,
            workoutSessions: List<WorkoutSession>,
            recoveryEntries: List<RecoveryEntry>,
            futsalSessions: List<FutsalSession>,
            progressionRecommendations: List<ProgressionRecommendation>,
        ): DomainResult<BackupSnapshot, BackupValidationError> {
            if (schemaVersion < 1) {
                return DomainResult.Failure(BackupValidationError.InvalidSchemaVersion(schemaVersion))
            }
            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                return DomainResult.Failure(BackupValidationError.UnsupportedSchemaVersion(schemaVersion))
            }
            return DomainResult.Success(
                BackupSnapshot(
                    schemaVersion = schemaVersion,
                    exercises = exercises,
                    trainingPlans = trainingPlans,
                    workoutSessions = workoutSessions,
                    recoveryEntries = recoveryEntries,
                    futsalSessions = futsalSessions,
                    progressionRecommendations = progressionRecommendations,
                ),
            )
        }
    }
}

/** A [TrainingPlan] with every one of its [TrainingPlanVersion]s, preserving historical version data. */
data class TrainingPlanSnapshot(
    val plan: TrainingPlan,
    val versions: List<TrainingPlanVersion>,
)

sealed interface BackupValidationError {
    data class InvalidSchemaVersion(
        val schemaVersion: Int,
    ) : BackupValidationError

    data class UnsupportedSchemaVersion(
        val schemaVersion: Int,
    ) : BackupValidationError

    /** The backup text is not valid JSON, is missing a required field, or has a field of the wrong shape. */
    data class Malformed(
        val reason: String,
    ) : BackupValidationError
}
