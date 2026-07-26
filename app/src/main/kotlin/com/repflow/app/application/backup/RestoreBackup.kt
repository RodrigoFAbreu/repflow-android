package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import javax.inject.Inject

/**
 * Parses and validates a backup's JSON text, then atomically replaces all
 * local data with it via [BackupRepository.replaceAll]. A parse/validation
 * failure never touches stored data (see [BackupRepository.replaceAll]'s
 * atomicity contract).
 */
class RestoreBackup
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
    ) {
        suspend operator fun invoke(json: String): DomainResult<Unit, BackupRestoreError> =
            when (val parsed = backupRepository.parseSnapshot(json)) {
                is DomainResult.Failure -> DomainResult.Failure(BackupRestoreError.InvalidBackup(parsed.error))
                is DomainResult.Success -> backupRepository.replaceAll(parsed.value)
            }
    }
