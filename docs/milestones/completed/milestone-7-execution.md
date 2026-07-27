# Milestone 7 execution — History and backup

See `milestone-7-reference.md` for full rationale, invariants, and the
"Unresolved decision" section (SAF for file location, no auto-retention).

## Checkpoints

1. **CP1 - Domain**: `domain/backup/BackupSnapshot.kt`,
   `BackupValidationError.kt`; `domain/history` read models if needed.
   Verify: `./gradlew testDebugUnitTest --tests "*backup*" --tests "*history*"`.
2. **CP2 - Application**: `application/history/ObserveWorkoutHistory.kt`;
   `application/backup/{ExportBackup,RestoreBackup,ExportWorkoutHistoryCsv}.kt`
   + `BackupRepository` port + in-memory fakes/tests. Verify: same as CP1.
3. **CP3 - Data/Infrastructure**: `data/backup/BackupJsonMapper.kt`,
   `LocalBackupRepository.kt` (`replaceAll` in one Room transaction),
   `WorkoutRepository.observeCompletedSessions()` additive method + DAO
   query, CSV formatter. Verify: `testDebugUnitTest`, `spotlessApply`,
   `detekt`, `lintDebug`.
4. **CP4 - Instrumented**: `replaceAll` atomicity test on a real Room
   database. Verify: `connectedDebugAndroidTest` filtered to the new
   test class on `emulator-5554`.
5. **CP5 - Presentation**: History screen (list + detail), Backup
   screen/section with SAF `CreateDocument`/`OpenDocument` launchers,
   ViewModels + tests. Verify: `testDebugUnitTest`, `spotlessApply`,
   `detekt`, `lintDebug`, `compileDebugAndroidTestKotlin`.
6. **CP6 - Full verification + docs**: full `testDebugUnitTest`,
   `spotlessCheck`, `detekt`, `lintDebug`, `connectedDebugAndroidTest`
   (full suite), manual smoke test (export -> restore -> data intact,
   CSV export), update `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`,
   archive this plan to `docs/milestones/completed/`, completion commit.

## Completion criteria

Every checkpoint above committed independently (or combined only when
genuinely inseparable), narrow checks green at each step, full
verification green at CP6, docs current.
