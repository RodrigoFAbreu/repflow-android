package com.repflow.app.data.backup

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.repflow.app.application.backup.TrainingDataError
import com.repflow.app.application.backup.TrainingDataRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import javax.inject.Inject

/**
 * The [TrainingDataRepository] implementation (remediation-1 CP14): exactly
 * the ten training-data tables, children first, in one
 * [androidx.room.withTransaction] - never `clearAllTables()`, which would take
 * the `settings` table with it.
 *
 * Room's `withTransaction` is re-entrant, so when [LocalBackupRepository]
 * calls this from inside its own transaction the deletes join that one, and a
 * failure later in the restore rolls them back with everything else
 * (`LocalBackupRepositoryAtomicityTest` proves it).
 */
class LocalTrainingDataRepository
    @Inject
    constructor(
        private val database: RepFlowDatabase,
    ) : TrainingDataRepository {
        override suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError> =
            try {
                database.withTransaction {
                    val dao = database.trainingDataDao()
                    dao.deleteWorkoutSets()
                    dao.deleteWorkoutExercises()
                    dao.deleteWorkoutSessions()
                    dao.deletePlannedExercises()
                    dao.deleteTrainingPlanVersions()
                    dao.deleteTrainingPlans()
                    dao.deleteExercises()
                    dao.deleteRecoveryEntries()
                    dao.deleteFutsalSessions()
                    dao.deleteProgressionRecommendations()
                }
                DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                DomainResult.Failure(TrainingDataError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                DomainResult.Failure(TrainingDataError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                DomainResult.Failure(TrainingDataError.Unavailable)
            } catch (expected: SQLiteException) {
                DomainResult.Failure(TrainingDataError.Unavailable)
            }
    }
