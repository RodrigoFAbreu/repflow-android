# Active Milestone

## `fix-backup-all-workouts` — in implementation review (plan revision 2, approved by policy 2026-11-05)

Backups include running and abandoned workouts, and restore warns about a running workout. Plan: `docs/milestones/fix-backup-all-workouts-execution.md`.

- **CP1, complete.** `Export backup` now reads every session through the new `WorkoutRepository.findAllForBackup`, so a file holds completed (including invalidated), abandoned and running workouts with their exercises and sets. The stale comments are corrected. Verified: `ExportBackupTest` (3 tests pass, and the new one failed before the change), `LocalBackupRepositoryRoundTripTest` on `emulator-5554` (passes, and fails with the old `COMPLETED` filter put back), `spotlessCheck`, `detekt`.

- **CP2, complete.** The restore confirmation now warns first, and only when a workout is running or that can't be read (`BackupUiState.hasRunningWorkout`, live from `observeActiveSession`). Its text scrolls at large font sizes. A session that can't be read fails `Export backup now` with the existing message and opens no file picker. The final text is on `docs/items/design-3-redraws.md`. Verified: `BackupViewModelTest` (22 tests pass, and the 3 new ones failed before the change), `BackupScreenTest` on `emulator-5554` (12 tests pass, and the new ones fail when the warning is moved below the body, the scroll is removed or the condition is inverted), `spotlessCheck`, `detekt`.

- **Implementation review round 1, fixes applied.** The external review found that the restore warning could be missing before the first read of the running workout (I1), so `hasRunningWorkout` now starts `true` and a confirmed absence removes it. A new test in the real dialog at font scale 2.0 checks that no line of the warning is cut off (O1). Verified: `spotlessCheck`, `detekt`, `lintDebug`, 755 JVM tests and 422 instrumented tests on `emulator-5554` all pass. The new delayed-read test failed before the fix, and mutations show the new tests catch a removed failed-read warning, a clipped warning and a warning moved below the body.

**Next action:** run `/review-implementation fix-backup-all-workouts`, then the manual external review through `/record-manual-implementation-review`.

## `fix-workout-errors` — complete (accepted by policy 2026-11-04)

A failed workout action now shows a message, an entry clears only after a successful save, and Try again really re-reads the workout. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-workout-errors-execution.md` and the Optional findings at `docs/milestones/completed/fix-workout-errors-findings.md`.

Earlier sections are archived in `docs/milestones/completed/ACTIVE_MILESTONE-history-to-2026-11-04.md`.
