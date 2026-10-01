package com.repflow.app.data.backup

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.TrainingDataRepository
import com.repflow.app.data.exercise.ExerciseEntityMapper
import com.repflow.app.data.progression.ProgressionRecommendationMapper
import com.repflow.app.data.recovery.FutsalSessionMapper
import com.repflow.app.data.recovery.RecoveryEntryMapper
import com.repflow.app.data.trainingplan.TrainingPlanEntityMapper
import com.repflow.app.data.workout.WorkoutEntityMapper
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.backup.TrainingPlanSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetEntity
import javax.inject.Inject

/**
 * The [BackupRepository] implementation: [BackupJsonMapper] handles the
 * text <-> entity-row shape, existing per-aggregate `*Mapper` objects handle
 * entity <-> domain, and [replaceAll] clears and rewrites **the training-data
 * tables** - the ten the snapshot carries - in one
 * [androidx.room.withTransaction] call, all-or-nothing.
 *
 * That scope is deliberately narrower than "every table" (remediation-1
 * CP14): the restore used to open with `clearAllTables()`, which would also
 * delete the `settings` row - and nothing would put it back, because
 * preferences are device settings and are not in the snapshot. The clear is
 * now [TrainingDataRepository.clearTrainingData], the same one `Erase all
 * data` uses, called inside this transaction (Room's `withTransaction` is
 * re-entrant, so it joins it), and the receiving device keeps its own
 * settings. "Restore replaces all local data atomically" therefore means all
 * local **training** data.
 */
class LocalBackupRepository
    @Inject
    constructor(
        private val database: RepFlowDatabase,
        private val trainingDataRepository: TrainingDataRepository,
    ) : BackupRepository {
        override fun serializeSnapshot(snapshot: BackupSnapshot): String = BackupJsonMapper.serialize(toEntitySnapshot(snapshot))

        override fun parseSnapshot(json: String): DomainResult<BackupSnapshot, BackupValidationError> =
            when (val parsed = BackupJsonMapper.parse(json)) {
                is DomainResult.Failure -> parsed
                is DomainResult.Success -> toDomainSnapshot(parsed.value)
            }

        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
            val entitySnapshot = toEntitySnapshot(snapshot)
            return try {
                database.withTransaction {
                    if (trainingDataRepository.clearTrainingData() is DomainResult.Failure) {
                        // Aborts the enclosing transaction; mapped below like any storage failure.
                        throw SQLiteException("Clearing training data before the restore failed")
                    }
                    entitySnapshot.exercises.forEach { database.exerciseDao().insert(it) }
                    entitySnapshot.trainingPlans.forEach { database.trainingPlanDao().insert(it) }
                    entitySnapshot.trainingPlanVersions.forEach { database.trainingPlanVersionDao().insert(it) }
                    database.plannedExerciseDao().insertAll(entitySnapshot.plannedExercises)
                    entitySnapshot.workoutSessions.forEach { database.workoutSessionDao().insert(it) }
                    database.workoutExerciseDao().insertAll(entitySnapshot.workoutExercises)
                    database.workoutSetDao().insertAll(entitySnapshot.workoutSets)
                    entitySnapshot.recoveryEntries.forEach { database.recoveryEntryDao().upsert(it) }
                    entitySnapshot.futsalSessions.forEach { database.futsalSessionDao().upsert(it) }
                    entitySnapshot.progressionRecommendations.forEach { database.progressionRecommendationDao().insert(it) }
                }
                DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                DomainResult.Failure(BackupRestoreError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                DomainResult.Failure(BackupRestoreError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                DomainResult.Failure(BackupRestoreError.Unavailable)
            } catch (expected: SQLiteException) {
                DomainResult.Failure(BackupRestoreError.Unavailable)
            }
        }

        private fun toEntitySnapshot(snapshot: BackupSnapshot): BackupEntitySnapshot {
            val workoutExercises = mutableListOf<WorkoutExerciseEntity>()
            val workoutSets = mutableListOf<WorkoutSetEntity>()
            snapshot.workoutSessions.forEach { session ->
                workoutExercises += WorkoutEntityMapper.toExerciseEntities(session)
                workoutSets += WorkoutEntityMapper.toSetEntities(session)
            }
            return BackupEntitySnapshot(
                schemaVersion = snapshot.schemaVersion,
                exercises = snapshot.exercises.map(ExerciseEntityMapper::toEntity),
                trainingPlans = snapshot.trainingPlans.map { TrainingPlanEntityMapper.toEntity(it.plan) },
                trainingPlanVersions = snapshot.trainingPlans.flatMap { it.versions }.map(TrainingPlanEntityMapper::toEntity),
                plannedExercises = snapshot.trainingPlans.flatMap { it.versions }.flatMap(TrainingPlanEntityMapper::toEntities),
                workoutSessions = snapshot.workoutSessions.map(WorkoutEntityMapper::toSessionEntity),
                workoutExercises = workoutExercises,
                workoutSets = workoutSets,
                recoveryEntries = snapshot.recoveryEntries.map(RecoveryEntryMapper::toEntity),
                futsalSessions = snapshot.futsalSessions.map(FutsalSessionMapper::toEntity),
                progressionRecommendations = snapshot.progressionRecommendations.map(ProgressionRecommendationMapper::toEntity),
            )
        }

        @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod") // maps every backed-up table 1:1
        private fun toDomainSnapshot(entitySnapshot: BackupEntitySnapshot): DomainResult<BackupSnapshot, BackupValidationError> {
            val exercises =
                entitySnapshot.exercises.map { entity ->
                    when (val result = ExerciseEntityMapper.toDomain(entity)) {
                        is DomainResult.Success -> result.value

                        is DomainResult.Failure -> return DomainResult.Failure(
                            BackupValidationError.Malformed("exercise ${entity.id}: ${result.error}"),
                        )
                    }
                }
            val plannedExercisesByVersion = entitySnapshot.plannedExercises.groupBy { it.versionId }
            val versionsByPlan = entitySnapshot.trainingPlanVersions.groupBy { it.planId }
            val trainingPlans =
                entitySnapshot.trainingPlans.map { planEntity ->
                    val plan =
                        when (val result = TrainingPlanEntityMapper.toDomain(planEntity)) {
                            is DomainResult.Success -> result.value

                            is DomainResult.Failure -> return DomainResult.Failure(
                                BackupValidationError.Malformed("plan ${planEntity.id}: ${result.error}"),
                            )
                        }
                    val versions =
                        versionsByPlan[planEntity.id].orEmpty().map { versionEntity ->
                            val rows = plannedExercisesByVersion[versionEntity.id].orEmpty()
                            when (val result = TrainingPlanEntityMapper.toDomain(versionEntity, rows)) {
                                is DomainResult.Success -> {
                                    result.value
                                }

                                is DomainResult.Failure -> {
                                    return DomainResult.Failure(
                                        BackupValidationError.Malformed("version ${versionEntity.id}: ${result.error}"),
                                    )
                                }
                            }
                        }
                    TrainingPlanSnapshot(plan = plan, versions = versions)
                }
            val exerciseRowsBySession = entitySnapshot.workoutExercises.groupBy { it.sessionId }
            val setRowsByExercise = entitySnapshot.workoutSets.groupBy { it.workoutExerciseId }
            val workoutSessions =
                entitySnapshot.workoutSessions.map { sessionEntity ->
                    val exerciseRows = exerciseRowsBySession[sessionEntity.id].orEmpty()
                    val setRowsByExerciseId = exerciseRows.associate { it.id to setRowsByExercise[it.id].orEmpty() }
                    when (val result = WorkoutEntityMapper.toDomain(sessionEntity, exerciseRows, setRowsByExerciseId)) {
                        is DomainResult.Success -> result.value

                        is DomainResult.Failure -> return DomainResult.Failure(
                            BackupValidationError.Malformed("session ${sessionEntity.id}: ${result.error}"),
                        )
                    }
                }
            val recoveryEntries =
                entitySnapshot.recoveryEntries.map { entity ->
                    when (val result = RecoveryEntryMapper.toDomain(entity)) {
                        is DomainResult.Success -> result.value

                        is DomainResult.Failure -> return DomainResult.Failure(
                            BackupValidationError.Malformed("recovery ${entity.id}: ${result.error}"),
                        )
                    }
                }
            val futsalSessions =
                entitySnapshot.futsalSessions.map { entity ->
                    when (val result = FutsalSessionMapper.toDomain(entity)) {
                        is DomainResult.Success -> result.value

                        is DomainResult.Failure -> return DomainResult.Failure(
                            BackupValidationError.Malformed("futsal ${entity.id}: ${result.error}"),
                        )
                    }
                }
            val progressionRecommendations =
                entitySnapshot.progressionRecommendations.map { entity ->
                    when (val result = ProgressionRecommendationMapper.toDomain(entity)) {
                        is DomainResult.Success -> result.value

                        is DomainResult.Failure -> return DomainResult.Failure(
                            BackupValidationError.Malformed("recommendation ${entity.id}: ${result.error}"),
                        )
                    }
                }
            return BackupSnapshot.create(
                schemaVersion = entitySnapshot.schemaVersion,
                exercises = exercises,
                trainingPlans = trainingPlans,
                workoutSessions = workoutSessions,
                recoveryEntries = recoveryEntries,
                futsalSessions = futsalSessions,
                progressionRecommendations = progressionRecommendations,
            )
        }
    }
