# Active Milestone

## `fix-workout-error-followups` — complete (accepted by policy 2026-11-05)

A successful retry clears a stale workout error, a failed read shows a message instead of closing the app, and Try again restores the exercise picker. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-workout-error-followups-execution.md` and the Optional findings at `docs/milestones/completed/fix-workout-error-followups-findings.md`.

**Next action:** `restore-safety-snapshot`, One automatic snapshot before a restore, restorable from Backup. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

Workout errors: no stale message after a retry, no crash on a failed read, and Try again restores the picker. Plan: `docs/milestones/fix-workout-error-followups-execution.md`.

- **CP1, complete.** A SQLite failure while reading a workout for an action now shows the existing "couldn't complete that" message instead of closing the app. `LocalWorkoutRepository.findById` and `findActiveSession` turn `SQLiteException` into the new `WorkoutReadFailedException`, and `ActiveWorkoutViewModel` reports it through `guardedRead` as `PERSISTENCE_UNAVAILABLE`, in `launchAction`, `onCompleteWorkout` and `onAbandonWorkout`. A read failure in the rest timer after a set is saved is ignored, so a saved set is never reported as failed. Read failures on other screens still crash as before (out of scope), and the rest alert's failure sink still catches the new type. Verified: `ActiveWorkoutActionErrorTest` (9 tests pass; the 4 new ones failed before the fix with the exception escaping the ViewModel), `LocalWorkoutRepositoryReadFailureTest` on `emulator-5554` (5 tests pass; before the fix the 3 failure tests saw a raw `SQLiteException`), the workout packages and `LayerBoundaryTest` (137 JVM tests pass), `spotlessCheck`, `detekt`. Mutations: a broad `catch (Exception)` in the repository fails both cancellation tests, and removing the rest timer guard fails its test. Two changes from the plan's test notes, neither to behavior: (1) the instrumented test fails the read by dropping a table, because on Room 2.8 a closed database fails a suspend read with `CancellationException`, not `SQLiteException`, so closing it never reaches the catch; (2) the new ViewModel tests are in `ActiveWorkoutActionErrorTest`, not `ActiveWorkoutViewModelTest`, which is over detekt's `LargeClass` limit. One test beyond the plan: a failure while loading a found session's exercises.

- **CP2, complete.** A successful action, finish or abandon now clears the error, so the old message goes away as soon as a retry works. `Try again` on the load-failure state also re-reads the exercise picker and the last-session seeds: `availableExercises` is wrapped in `retryTrigger.flatMapLatest` with its `.catch` inside, and `onRetryObservation` calls `refreshLastPerformances()`. Verified: `ActiveWorkoutActionErrorTest` (12 tests pass; the 3 new ones failed before the fix, with the error still set), `ActiveWorkoutObservationRetryTest` (4 tests pass; the 2 new ones failed before the fix, with an empty picker and no seed), the workout packages and `LayerBoundaryTest` (142 JVM tests pass), `ActiveWorkoutScreenTest` and `ActiveWorkoutErrorRouteTest` on `emulator-5554` (46 tests pass), `spotlessCheck`, `detekt`. Mutations: moving the picker's `.catch` outside `flatMapLatest` fails the picker test, and keying the screen's `LaunchedEffect` on `Unit` fails the new screen test. Two changes from the plan's test notes, neither to behavior: (1) the new ViewModel tests are in `ActiveWorkoutActionErrorTest` and `ActiveWorkoutObservationRetryTest`, not `ActiveWorkoutViewModelTest`, which is over detekt's `LargeClass` limit; (2) the picker test's exercise source fails every subscription until the test heals it, because `InMemoryExerciseRepository`'s one-shot failure can be used up by the board's exercise details first. One test beyond the plan: `clearingTheFieldTakesDownTheShowingMessage` in `ActiveWorkoutScreenTest`, which proves the plan's claim that clearing the field takes the showing message down. One corner left as it is: if `Try again` reads the session but its history read fails, seeds from an earlier good read are lost until history is next read. The steppers are empty and nothing saved changes. The full runs are still to come, in the wrap-up.

**Next action:** perform the functional review below, write findings to `.ai-review/fix-workout-error-followups/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification is current: the working tree is clean and no code has changed since implementation revision 1 was bundled and technically approved (CP1 and CP2 results above).

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. No feature flags, no network needed.

**Test data.** Fresh install with two exercises (e.g. Bench Press, Squat) and one finished workout containing each, so the seeds and history exist.

**Honest scope note.** The two failures this milestone fixes (a SQLite read failure, a failed observation) cannot be produced from the UI. They are proven by the automated tests listed in CP1 and CP2. The flows below are regression checks that the normal paths still behave and that nothing visible changed.

**F1 - Normal workout still works (R1).** Start a workout, add an exercise from the picker, log two sets, complete the workout. Expected: no error message at any point, the picker lists your exercises, the sets save, the workout appears in History.

**F2 - Abandon still works (R1).** Start a workout, log a set, abandon it. Expected: no error message, you return to Home, the workout is not in History.

**F3 - Seeds and picker still load (R2).** Start a second workout with an exercise from F1. Expected: the steppers are pre-filled from the last session, and the picker lists both exercises.

**F4 - No stale message after normal use (R2).** Do several actions in a row (add exercise, log set, edit set, delete set, finish). Expected: no error banner ever appears or lingers.

**F5 - Rest timer after a set (R1).** Log a set so the rest timer starts, let it finish or skip it. Expected: no error, the saved set stays saved.

**Known limitations / out of scope.**
- Neither failure can be triggered from the UI, so the fixed behavior is covered by automated tests only.
- Read failures on other screens still crash as before (out of scope, per CP1).
- If `Try again` reads the session but its history read fails, seeds from an earlier good read are lost until history is next read; the steppers are empty and nothing saved changes (accepted corner, per CP2).

## `fix-restore-damaged-file` — complete (accepted by policy 2026-11-05)

Restoring a damaged backup file now shows an error instead of closing the app, and leaves your data untouched. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-restore-damaged-file-execution.md` and the Optional findings at `docs/milestones/completed/fix-restore-damaged-file-findings.md`.

**Next action:** `fix-workout-error-followups`, Workout errors: no stale message after a retry, no crash on a failed read, and Try again restores the picker. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

Restoring a damaged backup file shows an error instead of closing the app. Plan: `docs/milestones/fix-restore-damaged-file-execution.md`.

- **CP1, complete.** `LocalBackupRepository.parseSnapshot` now turns `IllegalArgumentException` and `DateTimeException` into `BackupValidationError.Malformed`, and an unknown progression key now throws `IllegalArgumentException` instead of `IllegalStateException`. A zero-length rest timer, a blank workout id, a malformed date, or an unknown `result` or `overrideResult` therefore returns `InvalidBackup`, the user sees the existing "That file isn't a valid RepFlow backup." message, and nothing stored changes. The export-side dead end stays on issue #29, unchanged. Verified: `LocalBackupRepositoryDamagedFileTest` on `emulator-5554` (7 tests pass; before the fix the 5 damaged-file tests failed with the exceptions the plan names and the 2 controls passed), `ProgressionRecommendationMapperTest` (4 tests pass; the 2 new ones failed before the fix), the full suites (429 instrumented tests on `emulator-5554` and 757 JVM tests, all passing), `spotlessCheck`, `detekt`, `lintDebug`. One control beyond the plan: the same file with a valid override restores, so the unknown-override failure can only be the key.

**Next action:** perform the functional review below, write findings to `.ai-review/fix-restore-damaged-file/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification is current: the working tree is clean and unchanged in code since the last full run (`spotlessCheck`, `detekt`, `lintDebug`, 757 JVM tests, 429 instrumented tests on `emulator-5554`, all passing).

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. No feature flags. A damaged file must be made by hand: export a backup (Settings > Backup > `Export backup now`), `adb pull` it, edit one value (below) with a text editor, `adb push` it to Downloads on the AVD, and pick it in Restore. Keep an untouched copy of the exported file for F5.

**Test data.** Fresh install, then: two exercises, one completed workout with 2 sets, one running workout with a rest timer started, one futsal session, and a progression recommendation (finish the workout so one is computed). Note what Home, History and the exercise list show before each restore attempt.

**F1 - Zero-length rest timer (R1).** In the file, set the running session's `restTimerTotalDurationSeconds` to `0`. Restore it. Expected: the app stays open and shows "That file isn't a valid RepFlow backup."; no crash or restart.

**F2 - Blank workout id (R1).** Set a session's `"id"` to `""`. Restore. Expected: same message, no crash.

**F3 - Malformed date (R1).** Set the futsal session's `"entryDate"` to `"not-a-date"`. Restore. Expected: same message, no crash.

**F4 - Unknown progression result (R1).** Set a recommendation's `"result"` to `"INCREASE_LOAD"` (upper case, an unknown key). Then separately set `"overrideResult"` to `"INCREASE_LOAD"` together with `"overrideAt": 2` on a recommendation with a valid `result`. Restore each. Expected: same message each time, no crash.

**F5 - Data untouched after a rejected file (R2).** After each of F1-F4, check Home, History, the exercise list and the running workout (with its rest timer). Expected: exactly what you noted before; nothing deleted, nothing added. Then restore the untouched copy: expected success, so the failures were the damage, not the setup.

**F6 - Existing behaviors unchanged.** Restore a file that is not JSON (e.g. a text file). Expected: the same "isn't a valid RepFlow backup" message. Restore the valid file again: expected success.

**Known limitations / out of scope.**
- The export-side dead end (one unreadable saved workout makes every export fail) is unchanged and stays on issue #29 (R3).
- No per-field message: every damaged file shows the one generic message.
- Only `IllegalArgumentException` and date errors are turned into the message; other exception types on bad text would still crash visibly (accepted in the plan).
- A damaged value is only reachable by hand-editing a file, so there is no UI path to produce it.

## `fix-backup-all-workouts` — complete (accepted by policy 2026-11-05)

Backups now include running and abandoned workouts, and restore warns first when a workout is running. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-backup-all-workouts-execution.md` and the Optional findings at `docs/milestones/completed/fix-backup-all-workouts-findings.md`.

**Next action:** `suggest-keep-untouched`, An exercise you didn't train keeps its real suggestion. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

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
