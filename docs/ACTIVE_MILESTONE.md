# Active Milestone

## `restore-safety-snapshot` — in progress

Keep a copy of the user's data before a restore, and offer it on the Backup screen. Plan: `docs/milestones/restore-safety-snapshot-execution.md`.

- **CP1, complete.** A confirmed restore of a valid file on a phone with training data now saves a copy first, in 40k's order: check the file, save the new copy beside the old one, restore, and only then replace the old copy. If the copy can't be built or saved, nothing is replaced and the user sees the existing "Couldn't complete the operation" message. An invalid file or a failed restore leaves the old copy byte-identical, and a phone with no training data writes no copy and keeps any old one. Leaving the screen no longer abandons a confirmed restore, because `onRestoreConfirmed` runs under `NonCancellable`. `PreRestoreCopyLock` is held across each whole restore and erase, so restores can't overlap and no copy can be written after an erase. `Erase all data` deletes the copy first and does nothing else if that fails. New: `PreRestoreSnapshotStore` and `FilePreRestoreSnapshotStore` (three files in `filesDir`), `PreRestoreCopy` and `SavePreRestoreCopy`, `PreRestoreCopyLock`, and `TrainingDataRepository.hasTrainingData`. No schema change and no new dependency. Nothing on screen offers the copy yet, and the outcome of a restore that outlives its screen still stops at the cleared ViewModel; CP2 does both. Verified: the backup and settings JVM packages and `LayerBoundaryTest` (84 tests pass, including `PreRestoreCopyLockTest`, `SavePreRestoreCopyTest` and 5 new `BackupViewModelTest` cases), the `data/backup` instrumented tests and `SettingsPersistenceTest` on `emulator-5554` (42 tests pass, including `FilePreRestoreSnapshotStoreTest`, `LocalTrainingDataRepositoryTest` and `PreRestoreSnapshotRoundTripTest`), `spotlessCheck`, `detekt`. Mutations: 22 source edits were each caught by the expected test, among them removing the lock from the restore and from the erase, discarding on a thrown cancellation, removing `NonCancellable`, using `File.renameTo` and `File.delete`, and leaving a table out of the `EXISTS` query. Three changes from the plan, none to behavior: (1) `@Suppress("TooManyFunctions")` on `TrainingDataDao` and `RepositoryModule`, because each reaches detekt's limit of 11; (2) a one-line `replaceAllWith` on the `InMemoryExerciseRepository` test fake, so the overlap tests can swap the data; (3) `saveNew` and `clear` return the narrower `BackupRestoreError.Unavailable` type. One test beyond the plan: `theInjectedStoreKeepsTheCopyInTheAppsPrivateFilesDirectory`, for R3. The full runs are still to come, in the wrap-up.

- **CP2, complete.** The Backup screen now offers the copy. A card below the restore card (40k) shows when the copy was taken and `Restore the safety copy`, which goes through the same confirmation and the same restore as a file, so restoring the copy keeps a new copy of what it replaced and the two states swap. The restore note and the confirmation no longer say a restore can't be undone: they are 40k's and 40j's text, with file and copy variants, the running-workout warning first, the safety-copy paragraph only when a copy exists, and no copy paragraphs on a phone with no training data. `Cancel` is on the left and `Replace my data` is outlined in the destructive colour; above font scale 1.1 they stack with the destructive action on top. Every `BackupViewModel` read of the store or of `hasTrainingData` takes `PreRestoreCopyLock`, and the state starts busy, so a screen reopened during a restore keeps its buttons disabled until that restore ends and then shows the card as it now stands. `RestoreOutcomeSlot` carries a restore's outcome to whichever Backup screen is there: when the restore ends if one is open, otherwise at the next visit, once. An outcome equal to the one on screen merges into that snackbar, and the next equal outcome still shows. This closes what CP1 left open. Verified: the backup and settings JVM packages and `LayerBoundaryTest` (113 tests pass, including `BackupViewModelSafetyCopyTest` and `RestoreOutcomeSlotTest`), `presentation/backup` on `emulator-5554` (32 tests pass, including the two route tests with their new `waitUntil`), `MainActivityNavHostSmokeTest` (10 tests pass), `spotlessCheck`, `detekt`. Mutations: 28 source edits, among them removing the lock from each read, each `consume`, the slot's collector, `@Singleton`, the busy start and the copy-paragraph conditions, moving the warning, and swapping the buttons. 27 were caught at first. The survivor showed the "replaces the safety copy" paragraph with no copy, because the test only looked for one date, so the test now looks for the sentence and catches it. Looked at in the real app on the AVD, in light and dark at font scale 1.0 and dark at 2.0: restoring the copy swapped the states and showed `Backup restored`. That look found `Replace my data` wrapping and a title far larger than 40j's, both fixed below. Changes from the plan, none to the rules: (1) `@Suppress("TooManyFunctions")` on `BackupViewModel`, which reaches detekt's limit of 11; (2) a local `BackupOutlinedButton` for the card and both dialog buttons, not the 44dp neutral tier, because 40j and 40k draw 48dp, a 10dp radius and 14.5sp; (3) its side padding is 8dp, not the board's 12, so `Replace my data` fits on one line on a 384dp phone, and the pair keeps one height where it still wraps; (4) the dialog's title is 18sp and its paragraphs 14sp in the secondary colour, as 40j draws them; (5) `STACK_ACTIONS_FONT_SCALE` is a second private constant, because Home's is private to that file. One small addition: a copy that has vanished also takes its card down, instead of offering a copy that can only fail. Tests beyond the plan: the note's exact text, the two heights when the label wraps, and a file staged over a pending copy. The full runs and the note on issue #29 are still to come, in the wrap-up.

- **Implementation review round 1, fixes applied.** The external review found that a restore that outlives its screen could delete a workout saved while it ran (I1). A workout started from Home after the copy was read and before the replace committed reported success, and then the restore deleted it, so it was in neither the restored data nor the copy. The plan had accepted this corner because a guard seemed to need every write path to take the lock. It needs none: the read, the save and the replace are now one write transaction (`BackupRepository.inRestoreTransaction`), and SQLite has one writer, so every other write waits. A write that finished first is in the copy, and one that lands after the commit stays on top of the restored data. `LocalWorkoutRepository.insert` now checks for a running workout inside its transaction, so a start during a restore of a file with a running workout is refused instead of becoming a second one. A change to a workout the restore deleted fails with its usual message. Two Optional findings from the local review are fixed as well: a failed save no longer leaves a partial temp file (R1-O1), and a second tap on the card while the copy loads is ignored, with the screen busy during the load (R1-O2). Verified at `8dceb70`: `spotlessCheck`, `detekt`, `lintDebug`, 827 JVM tests and 480 instrumented tests on `emulator-5554`, all passing. Before the fix, `aWorkoutStartedDuringARestoreWhoseScreenHasGoneWaitsForItAndSurvivesIt` failed with "The workout the user saw start is still there". Mutations: 10 source edits, each caught by a named test, among them removing the transaction, checking for a running workout before it, removing its catch, and promoting or discarding on every outcome. Changes from the plan: the accepted corner above no longer applies, and `PreRestoreSnapshotRoundTripTest` now uses a real file in WAL, because an in-memory database has one connection, so every read during a restore would wait for it. The plan itself is unchanged, because its approved text is pinned.

- **Implementation review round 2, fixes applied.** The external review found that an override chosen during a surviving restore could report success without saving anything (I2). A plain read still sees the old data, so `RecordManualOverride` read a recommendation the incoming file omits. Its update waited for the commit and then changed no row, and the repository returned `Success`, so the choice was in neither the restored data nor the copy. `ProgressionRecommendationDao.update` now returns the rows changed and the repository turns zero into `Unavailable`, as the exercise, plan and workout repositories already do, so the screen shows `Couldn't save your choice. Try again.` An override on a row the restore kept is still saved on top of it. No other `@Update` has this gap, and the remaining writes are inserts and upserts, which either land or throw. Two Optional findings from the local review are handled as well: a second tap on Home's start while one is in flight is ignored (R2-O1), and what the record says about reads during a restore is corrected (R2-O2): a screen opened during a restore can sit on its loading state until the restore ends, and on a low-RAM phone every read waits. Verified at `e5423c6`: `spotlessCheck`, `detekt`, `lintDebug`, 830 JVM tests and 483 instrumented tests on `emulator-5554`, all passing. Before the fix, `anOverrideOnARecommendationTheRestoreRemovedFailsInsteadOfReportingSuccess` failed on the AVD with `expected:<Failure(error=PersistenceFailed(cause=Unavailable))> but was:<Success(value=kotlin.Unit)>`. Mutations: 4 edits, each caught by a named test. The fixed plan text is unchanged.

**Next action:** perform the functional review below, write findings to `.ai-review/restore-safety-snapshot/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification is current: the working tree is clean and no code has changed since the round 2 full runs at `e5423c6` (`spotlessCheck`, `detekt`, `lintDebug`, 830 JVM tests, 483 instrumented tests on `emulator-5554`); later commits are workflow records only.

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. No feature flags, no network needed. Backup files are written and read through the system file picker, so export to the device's Downloads folder.

**Test data.** State A (the "current" data): two exercises (Bench Press, Squat) and one finished workout. State B (a file to restore): from State A, add a third exercise (Deadlift) and a second finished workout, then Export a backup to Downloads; then delete Deadlift and the second workout so the phone is back to State A-like data. Also keep a garbage file (e.g. a text file renamed `bad.json`) in Downloads, and a fresh install with no data for F5.

**F1 - First restore keeps a copy (R: CP1/CP2).** Backup screen before any restore: no safety-copy card. Pick the State B file, read the confirmation. Expected: the body no longer says "can't be undone"; it has the "RepFlow first keeps a copy of what is on this phone" paragraph and no "replaces the safety copy" paragraph; `Cancel` is on the left, `Replace my data` is outlined in the destructive colour. Confirm. Expected: `Backup restored`; Deadlift and the second workout are back; a card "Safety copy, <date>, <time>" appears below the restore card with `Restore the safety copy`.

**F2 - Restore the safety copy swaps the states (R: CP2).** Tap `Restore the safety copy`. Expected: a confirmation titled "Restore the safety copy?" with the copy's date and time and the "so you can switch back" paragraph. Confirm. Expected: `Backup restored`; the data is State A-like again (no Deadlift, no second workout); the card still shows, now dated just now, and tapping it again swaps back to State B data.

**F3 - Second restore replaces the old copy (R: CP1).** Restore the State B file again while a copy exists. Expected: the confirmation adds "This replaces the safety copy from <date>, <time>."; after confirming, the card shows the new time.

**F4 - A bad file changes nothing (R: CP1).** Pick `bad.json`. Expected: the existing invalid-file message, data unchanged, the card and its date unchanged.

**F5 - No data, no copy (R: CP1).** On a fresh install with no training data, restore the State B file. Expected: its confirmation has no safety-copy paragraphs, the restore succeeds, and no card appears. Then (with data present) Erase all data: the card disappears and stays gone after reopening Backup.

**F6 - Leaving the screen mid-restore (R: CP1/CP2).** Start a restore and press back to Home straight after confirming. Expected: the restore still finishes; reopening Backup shows the `Backup restored` outcome once and the card as it now stands. (Timing on a small file is tight; if it finishes too fast to catch, note it and move on.)

**F7 - Layout (R: CP2).** Open the confirmation in light and dark at font scale 1.0 and 2.0 (Settings > Display). Expected: `Replace my data` on one line at 1.0; above 1.1 the buttons stack with the destructive action on top; nothing clipped.

**Known limitations / out of scope.**
- Only one copy is kept, and the next restore replaces it; there is no history of copies.
- The copy stays on the phone (app-private files), is not part of a backup export and is not encrypted.
- A restore of a file with a running workout refuses a new workout start while it runs (I1); this window is milliseconds and is covered by automated tests only.
- On a low-RAM phone, reads wait while a restore runs, so a screen opened then can sit on its loading state until it ends (R2-O2).
- Settings are never part of a restore or of the copy.

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
