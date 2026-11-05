package com.repflow.app.application.backup

import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.recovery.FutsalRepository
import com.repflow.app.application.recovery.RecoveryRepository
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import javax.inject.Inject

/**
 * Builds a full [BackupSnapshot] by reading every repository and serializes
 * it to the on-disk transfer format via [BackupRepository]. A repository read
 * that fails throws for the caller to report, since a partial file would
 * silently drop data. Building the snapshot itself always succeeds with
 * [BackupSnapshot.CURRENT_SCHEMA_VERSION] - see [BackupSnapshot.create]'s
 * validation, which only ever rejects an out-of-range schema version.
 */
class ExportBackup
    @Suppress("LongParameterList")
    @Inject
    constructor(
        private val exerciseRepository: ExerciseRepository,
        private val trainingPlanRepository: TrainingPlanRepository,
        private val workoutRepository: WorkoutRepository,
        private val recoveryRepository: RecoveryRepository,
        private val futsalRepository: FutsalRepository,
        private val progressionRepository: ProgressionRecommendationRepository,
        private val backupRepository: BackupRepository,
    ) {
        suspend operator fun invoke(): String {
            val snapshot =
                BackupSnapshot.create(
                    schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                    exercises = exerciseRepository.findAll(),
                    trainingPlans = trainingPlanRepository.findAllForBackup(),
                    // Every session, by the repository method's contract: a backup must
                    // never silently drop a running, abandoned or invalidated session
                    // (Milestone 8, CP11's "never physically deleted" invariant). An
                    // invalidatedAt round-trips via CP14's BackupJsonMapper wiring.
                    workoutSessions = workoutRepository.findAllForBackup(),
                    recoveryEntries = recoveryRepository.findAll(),
                    futsalSessions = futsalRepository.findAll(),
                    progressionRecommendations = progressionRepository.findAll(),
                )
            val value =
                when (snapshot) {
                    is DomainResult.Success -> {
                        snapshot.value
                    }

                    is DomainResult.Failure -> {
                        error("BackupSnapshot.create unexpectedly rejected CURRENT_SCHEMA_VERSION: ${snapshot.error}")
                    }
                }
            return backupRepository.serializeSnapshot(value)
        }
    }
