# Active Milestone

## `fix-backup-all-workouts` — implementing (plan revision 2, approved by policy 2026-11-05)

Backups include running and abandoned workouts, and restore warns about a running workout. Plan: `docs/milestones/fix-backup-all-workouts-execution.md`.

- **CP1, complete.** `Export backup` now reads every session through the new `WorkoutRepository.findAllForBackup`, so a file holds completed (including invalidated), abandoned and running workouts with their exercises and sets. The stale comments are corrected. Verified: `ExportBackupTest` (3 tests pass, and the new one failed before the change), `LocalBackupRepositoryRoundTripTest` on `emulator-5554` (passes, and fails with the old `COMPLETED` filter put back), `spotlessCheck`, `detekt`.

**Next action:** CP2, warn in the restore dialog when a workout is running and report a failed export.

## `fix-workout-errors` — complete (accepted by policy 2026-11-04)

A failed workout action now shows a message, an entry clears only after a successful save, and Try again really re-reads the workout. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-workout-errors-execution.md` and the Optional findings at `docs/milestones/completed/fix-workout-errors-findings.md`.

Earlier sections are archived in `docs/milestones/completed/ACTIVE_MILESTONE-history-to-2026-11-04.md`.
