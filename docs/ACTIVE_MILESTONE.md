# Active Milestone

## `fix-backup-all-workouts` — awaiting functional review (implementation revision 2; plan revision 2, technical approval by policy 2026-11-05)

Backups include running and abandoned workouts, and restore warns about a running workout. Plan: `docs/milestones/fix-backup-all-workouts-execution.md`.

- **CP1, complete.** `Export backup` now reads every session through the new `WorkoutRepository.findAllForBackup`, so a file holds completed (including invalidated), abandoned and running workouts with their exercises and sets. The stale comments are corrected. Verified: `ExportBackupTest` (3 tests pass, and the new one failed before the change), `LocalBackupRepositoryRoundTripTest` on `emulator-5554` (passes, and fails with the old `COMPLETED` filter put back), `spotlessCheck`, `detekt`.

- **CP2, complete.** The restore confirmation now warns first, and only when a workout is running or that can't be read (`BackupUiState.hasRunningWorkout`, live from `observeActiveSession`). Its text scrolls at large font sizes. A session that can't be read fails `Export backup now` with the existing message and opens no file picker. The final text is on `docs/items/design-3-redraws.md`. Verified: `BackupViewModelTest` (22 tests pass, and the 3 new ones failed before the change), `BackupScreenTest` on `emulator-5554` (12 tests pass, and the new ones fail when the warning is moved below the body, the scroll is removed or the condition is inverted), `spotlessCheck`, `detekt`.

- **Implementation review round 1, fixes applied.** The external review found that the restore warning could be missing before the first read of the running workout (I1), so `hasRunningWorkout` now starts `true` and a confirmed absence removes it. A new test in the real dialog at font scale 2.0 checks that no line of the warning is cut off (O1). Verified: `spotlessCheck`, `detekt`, `lintDebug`, 755 JVM tests and 422 instrumented tests on `emulator-5554` all pass. The new delayed-read test failed before the fix, and mutations show the new tests catch a removed failed-read warning, a clipped warning and a warning moved below the body.

**Next action:** perform the functional review below, write findings to `.ai-review/fix-backup-all-workouts/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification is current: the working tree is clean since the last full run (`spotlessCheck`, `detekt`, `lintDebug`, 755 JVM tests, 422 instrumented tests on `emulator-5554`, all passing).

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. Set the AVD font size to the largest setting (Settings > Display > Font size, plus Display size if offered) for flow F4 and restore it afterwards. No feature flags. Backups are written to a file the picker lets you choose (for example Downloads on the AVD).

**Test data.** Start from a fresh install (or Erase all data), then seed:
1. One exercise and a completed workout with 2 logged sets (finish it).
2. A second workout you abandon (start, log 1 set, then abandon).
3. A third workout left running with 1 logged set and a rest timer started.

**F1 - Backup includes every workout (R1).** Settings > Backup > `Export backup now`, choose a file name, save. Expected: "Saved" confirmation, file picker opens only once, buttons stay enabled. Open the file (e.g. `adb pull`, then read the `workoutSessions` JSON): it holds three sessions with statuses `COMPLETED`, `ABANDONED` and `ACTIVE`, each with exercises and sets, and the running one has its rest-timer fields.

**F2 - Restore brings them back (R2).** Erase all data (or `Erase all data` then reopen). Restore the file from F1. Expected: after restore, Home shows the running workout as active (with its logged set), History shows the completed workout, and the abandoned one is not offered as active. Opening the active workout shows the set and the saved rest timer state.

**F3 - Warning appears only when a workout is running (R3).**
- With the F3 workout still running, pick a backup file to restore. Expected: the confirmation opens with "A workout is running on this phone. Restoring deletes it, with every set you've logged." as the first paragraph, then the usual body, then `Cancel` / `Replace my data`. `Cancel` closes it and changes nothing.
- Finish or abandon the running workout so none is running, pick the file again. Expected: the warning paragraph is absent; the usual body only.

**F4 - Real dialog at large font (R3, design `DESIGN_SYSTEM.md:17-19`).** With the font at its largest and a workout running, pick a file. Expected: the warning is the first thing on screen, fully readable; the rest of the text scrolls; both buttons stay reachable; nothing is cut off. Repeat at font scale 1.3 if possible. Restore the font setting afterwards.

**F5 - Replace my data deletes the running workout (R3 consequence).** With a workout running, confirm `Replace my data` for a file with no running workout. Expected: restore succeeds and the running workout is gone, as the warning said.

**F6 - Export failure message (R5), best effort.** A damaged session row cannot be produced through the UI; this is covered by `BackupViewModelTest` and is not exercised manually. Skip unless you can corrupt a row on the AVD (then expect "Couldn't complete the operation. Try again.", no picker, buttons enabled, no crash).

**Known limitations / out of scope.**
- A restored running workout can show a very long elapsed time and blocks starting a new workout until finished or abandoned (tracked in `docs/items/overnight-workout.md`).
- A leftover rest alarm from a rest running during the restore can post "Rest done" once (accepted in the plan).
- A damaged session blocks every export (accepted; see plan self-review).
- CSV history export still lists completed sessions only; the restore safety snapshot is the next item (`restore-safety-snapshot`).
- Restore of a rest-timer value damaged in a hand-edited file may crash (implementation review finding O-1, follow-up item).
- Old backup files restore as before (completed sessions only).

## `fix-workout-errors` — complete (accepted by policy 2026-11-04)

A failed workout action now shows a message, an entry clears only after a successful save, and Try again really re-reads the workout. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-workout-errors-execution.md` and the Optional findings at `docs/milestones/completed/fix-workout-errors-findings.md`.

Earlier sections are archived in `docs/milestones/completed/ACTIVE_MILESTONE-history-to-2026-11-04.md`.
