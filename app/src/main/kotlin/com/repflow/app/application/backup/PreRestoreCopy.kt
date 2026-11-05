package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import javax.inject.Inject

/**
 * What runs around a restore of an already-validated file. A port only so that [RestoreBackup]'s test
 * call sites can pass `PreRestoreCopy { restore -> restore() }` instead of building seven repositories;
 * [SavePreRestoreCopy] is the one implementation.
 */
fun interface PreRestoreCopy {
    suspend fun aroundRestore(restore: suspend () -> DomainResult<Unit, BackupRestoreError>): DomainResult<Unit, BackupRestoreError>
}

/**
 * Saves a copy of the user's data before a restore replaces it, holding [PreRestoreCopyLock] from the
 * read through the restore to the promote or discard. The copy is exactly what [ExportBackup] produces,
 * so it is an ordinary backup file in the versioned transfer schema.
 *
 * The read, the save and the restore are one [BackupRepository.inRestoreTransaction], because a restore
 * can outlive its screen and the user can go and write something while it runs. A write that lands before
 * the read is in the copy, and one that lands after the commit stays on top of the restored data, so a
 * write the user saw succeed is never in neither. The promote or discard comes after the commit, because
 * the old copy may be the only place that data exists until then.
 *
 * Only a returned [DomainResult.Failure] discards the new copy. Anything thrown, a
 * [CancellationException] included, discards nothing: a suspending call can resume with one after its
 * work has finished, so a leftover new copy is kept and wins at the next read.
 *
 * With no training data there is nothing to protect, so nothing is written and any old copy stays.
 */
class SavePreRestoreCopy
    @Inject
    constructor(
        private val trainingDataRepository: TrainingDataRepository,
        private val exportBackup: ExportBackup,
        private val backupRepository: BackupRepository,
        private val store: PreRestoreSnapshotStore,
        private val lock: PreRestoreCopyLock,
    ) : PreRestoreCopy {
        override suspend fun aroundRestore(
            restore: suspend () -> DomainResult<Unit, BackupRestoreError>,
        ): DomainResult<Unit, BackupRestoreError> =
            lock.withLock {
                var copySaved = false
                val outcome =
                    backupRepository.inRestoreTransaction {
                        when (val current = readCurrentData()) {
                            CurrentData.Unreadable -> {
                                DomainResult.Failure(BackupRestoreError.Unavailable)
                            }

                            CurrentData.NoTrainingData -> {
                                restore()
                            }

                            is CurrentData.Exported -> {
                                val saved = store.saveNew(current.json)
                                copySaved = saved is DomainResult.Success
                                if (saved is DomainResult.Failure) saved else restore()
                            }
                        }
                    }
                if (copySaved) {
                    when (outcome) {
                        is DomainResult.Success -> store.promoteNew()
                        is DomainResult.Failure -> store.discardNew()
                    }
                }
                outcome
            }

        /**
         * Without a copy nothing may be replaced, so a read that throws stops the restore instead of
         * crashing. The catch surrounds only this read, so anything thrown while parsing or replacing
         * is still a program bug that should crash. It cannot catch by type: the exceptions that a
         * damaged row or a failed query throw live where this layer may not import them.
         */
        private suspend fun readCurrentData(): CurrentData =
            flow<CurrentData> {
                emit(if (trainingDataRepository.hasTrainingData()) CurrentData.Exported(exportBackup()) else CurrentData.NoTrainingData)
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(CurrentData.Unreadable)
            }.single()

        private sealed interface CurrentData {
            data class Exported(
                val json: String,
            ) : CurrentData

            data object NoTrainingData : CurrentData

            data object Unreadable : CurrentData
        }
    }
