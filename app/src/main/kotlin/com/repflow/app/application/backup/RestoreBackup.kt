package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import javax.inject.Inject

/**
 * Parses and validates a backup's JSON text, then atomically replaces all
 * local training data with it via [BackupRepository.replaceAll] - the
 * device's settings stay as they are (remediation-1 CP14). A parse/validation
 * failure never touches stored data (see [BackupRepository.replaceAll]'s
 * atomicity contract) or the safety copy: only a valid file runs inside
 * [PreRestoreCopy.aroundRestore].
 */
class RestoreBackup
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val preRestoreCopy: PreRestoreCopy,
    ) {
        suspend operator fun invoke(json: String): DomainResult<Unit, BackupRestoreError> =
            when (val parsed = backupRepository.parseSnapshot(json)) {
                is DomainResult.Failure -> DomainResult.Failure(BackupRestoreError.InvalidBackup(parsed.error))
                is DomainResult.Success -> preRestoreCopy.aroundRestore { backupRepository.replaceAll(parsed.value) }
            }
    }
