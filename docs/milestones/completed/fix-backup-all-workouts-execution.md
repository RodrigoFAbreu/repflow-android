# fix-backup-all-workouts — Execution Plan (Revision 2)

- Work item: `fix-backup-all-workouts` (backlog order 19, size 2, `docs/items/fix-backup-all-workouts.md`)
- Governing workflow version: `2.2`
- Base commit: `b31d9f2337117e02f4cb6b5d1cd13c37aa8b9672`
- Plan revision: 2

Pull request title: `fix: back up running and abandoned workouts and warn before a restore`

## Goal and scope

Don't lose data, don't hide failures. One finding from the 2026-11-04 council
(A-2 / O-f4-4, design item 4), re-checked against today's code on the base commit:

- **Backups leave out running and abandoned workouts.** `ExportBackup.kt:43` reads
  `workoutRepository.observeCompletedSessions(includeInvalidated = true)`, whose query
  is `WHERE status = 'COMPLETED'` (`WorkoutSessionDao.kt:39`). A running (`ACTIVE`) or
  `ABANDONED` session is therefore never written. The file format already carries it:
  `BackupJsonMapper` writes and reads `status` and `endedAt` as plain columns
  (`BackupJsonMapper.kt:202`, `:214`), `WorkoutEntityMapper.toDomain` accepts any
  `WorkoutSessionStatus`, and `LocalBackupRepository.replaceAll` inserts whatever
  sessions the snapshot holds. No schema version bump and no migration are needed.
- **Restoring replaces a running workout without saying so.** `BackupRestoreConfirmDialog`
  (`BackupFileActions.kt:90`) says "Every exercise, plan, workout, … is replaced", but
  unlike the Erase dialog (`settings_erase_confirm_body`) it never mentions a workout
  that is still running, which `clearTrainingData` deletes with the rest.

**Out of scope:** the CSV history export (`ExportWorkoutHistoryCsv` keeps listing
completed sessions: it is a history report, not a backup, and because what it reads
doesn't change, this item adds no new failure there); History, progress and
recommendation reads (they keep reading completed sessions only); the restore safety
snapshot (`restore-safety-snapshot`, the next backlog item); the restore card's note on
the Backup screen; any change to what restoring does to a running workout.

## Design (no new mechanism)

All three changes follow patterns already in the code.

1. **Export every session.** `WorkoutRepository` gains
   `suspend fun findAllForBackup(): List<WorkoutSession>`, the same name and shape as
   `TrainingPlanRepository.findAllForBackup` (`TrainingPlanRepository.kt:35`).
   `WorkoutSessionDao` gains `findAll()` (`SELECT * FROM workout_sessions ORDER BY
   started_at, id`), and `LocalWorkoutRepository` maps each row with its existing
   `toSessionOrThrow`, so exercises and sets come with it. `ExportBackup` calls it in
   place of `observeCompletedSessions(...).first()`. The invalidated sessions that the
   current call keeps stay in, because the query has no filter at all. The existing
   regression test for them must keep passing. `InMemoryWorkoutRepository` (the only
   other implementer; the instrumented fakes delegate) returns every session it holds.
   A row that fails to map makes the read throw, as the read it replaces does for a
   completed row; design item 3 is what the user then sees.

   Restoring such a file needs nothing new: the sessions go in as rows, a restored
   `ACTIVE` session becomes the running workout, and the existing rest-notification
   cancel after a restore (`BackupViewModel.onRestoreConfirmed`) stays.
2. **Warn about a running workout.** `BackupViewModel` takes a `WorkoutRepository` and, in
   `init` and next to the existing settings observation, collects
   `observeActiveSession()` into a new `BackupUiState.hasRunningWorkout: Boolean`
   (`true` when a session is active). A failed observation sets `true` after rethrowing
   `CancellationException`: not knowing is treated as "may be running", so the warning is
   never silently dropped. The flag is live, so the dialog is right at the moment it is
   shown. `BackupRestoreConfirmDialog` gains a `hasRunningWorkout` parameter, and its
   text slot becomes a small `BackupRestoreConfirmText(hasRunningWorkout)` composable: a
   column with `verticalScroll`, because this text must not clip at font scale 2.0
   (`DESIGN_SYSTEM.md:17-19`) and nothing here shows that `AlertDialog`'s text slot
   scrolls on its own. When set, the warning goes first, above the existing body (new
   string `backup_restore_confirm_running_workout`):
   "A workout is running on this phone. Restoring deletes it, with every set you've
   logged." It goes first so the line that matters most shows without scrolling at any
   font size. The wording follows the Erase dialog's "including a workout that is still
   running". `BackupScreen` passes `uiState.hasRunningWorkout`. The dialog's buttons,
   confirmation step and `onRestoreConfirmed` are unchanged.

   This differs on purpose from the always-on sentence design pass 1 carried forward for
   `design-3-redraws` ("...replaced by the file's contents, including a workout that is
   still running."): a conditional paragraph warns only when the risk is real (R3). CP2
   records the final text on that item's page so its board draws the same thing, which
   gives the one shared wording the council asked for (A-2 and D-43).
3. **Report a failed export.** `onExportBackupRequested` gains the failure path it lacks:
   `flow { emit(exportBackup()) }` with the `.catch` this class already uses
   (`BackupViewModel.kt:55-59`), which rethrows `CancellationException`, clears `isBusy`
   and sets `BackupStatusMessage.OperationFailed` (the existing "Couldn't complete the
   operation. Try again." snackbar). `onReady` runs from `collect`, so `.catch` doesn't
   also swallow a failure from `onReady` itself, and a failed export never opens the file
   picker. Design item 1 makes this necessary: export now reads running and abandoned
   rows, which nothing read before, and a damaged one would otherwise throw with no
   handler (`BackupViewModel.kt:77-83`) and end the process. A partial file that skips the
   bad row is not an option, since it would silently drop data. `onCsvExportRequested`
   keeps its shape, because its reads don't change.

No Room schema, migration, backup-format, domain or navigation change; Domain stays pure
Kotlin; no new dependency or module; nothing touches the network. Persisted enum values
stay stable strings. No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided
here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | A backup file contains every workout session: completed (including invalidated), abandoned and running, each with its exercises and sets. |
| R2 | Restoring a file that holds running and abandoned sessions brings them back with the same status, end time, exercises and sets, and the running one is the active workout. |
| R3 | The restore confirmation tells the user a running workout will be deleted, when one exists, and does not mention it when none does. |
| R4 | If the app cannot tell whether a workout is running, the restore confirmation shows the warning rather than leaving it out. |
| R5 | If a workout session can't be read, `Export backup now` shows the existing failure message, leaves the buttons enabled, opens no file picker and doesn't crash. No partial file is ever written. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Export every workout session and prove the round trip (R1, R2) | - | 2 | 1 |
| CP2 | Warn in the restore dialog when a workout is running, and report a failed export (R3, R4, R5) | CP1 | 2 | 1 |

<!-- CP1 -->
### CP1 — Export every workout session and prove the round trip (R1, R2)

**Files:**
`app/src/main/kotlin/com/repflow/app/application/workout/WorkoutRepository.kt`,
`app/src/main/kotlin/com/repflow/app/application/backup/ExportBackup.kt`,
`app/src/main/kotlin/com/repflow/app/data/workout/LocalWorkoutRepository.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/database/workout/WorkoutSessionDao.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/RestTimerExpiryHandler.kt` (comment only),
`app/src/test/kotlin/com/repflow/app/application/workout/InMemoryWorkoutRepository.kt`,
`app/src/test/kotlin/com/repflow/app/application/backup/ExportBackupTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalBackupRepositoryRoundTripTest.kt` (new).

Work: design item 1. Correct every comment this makes stale:
- `ExportBackup`'s kdoc and its `includeInvalidated` comment: the call returns every
  session, so the "never silently dropped" rule now reads from the repository method's
  contract, and a failed read throws for the caller to report.
- `WorkoutRepository.kt:14-16,26-32` and `WorkoutSessionDao.kt:26-35`: the completed-session
  read now serves history and CSV export, not backup.
- `ExportBackupTest.kt:73-80`: `ExportBackup` no longer requests `includeInvalidated = true`.
- `RestTimerExpiryHandler.kt:34-40`: a restore can now leave a running session with a rest
  timer (see the self-review notes).

Tests (a failing test is written first for each):
- Unit, `ExportBackupTest`: with one completed, one abandoned and one running session in
  `InMemoryWorkoutRepository` (the running one holding a logged set), the exported
  snapshot holds all three with their statuses. The existing invalidated-session test
  still passes; only its comment changes.
- Instrumented, new `LocalBackupRepositoryRoundTripTest`, set up like
  `LocalBackupRepositoryAtomicityTest`: over an in-memory Room database seed a completed
  (one invalidated), an abandoned and a running session, each with an exercise and sets,
  through `LocalWorkoutRepository`. The running session holds a `RestTimer`, so the
  comparison covers the timer fields rather than `null` against `null`. Insert the
  exercise row first, since `workout_exercises.exercise_id` is a foreign key
  (`WorkoutExerciseEntity.kt:29-33`); the sessions have no plan link, so no plan rows are
  needed. Build the snapshot from `exerciseRepository.findAll()` and `findAllForBackup()`,
  serialize and parse it with the real `BackupJsonMapper`, `replaceAll` it into a second
  empty in-memory database, then assert that `findAllForBackup()` there equals the first
  list and that `findActiveSession()` returns the running one. This is the round-trip
  test the backlog item asks for; it runs only on the device, since CI compiles
  instrumented tests without running them.
<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Warn in the restore dialog when a workout is running, and report a failed export (R3, R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupUiState.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupFileActions.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/BackupScreenTest.kt`,
`docs/items/design-3-redraws.md`.

Work: design items 2 and 3. Every place that builds `BackupViewModel` or
`BackupRestoreConfirmDialog` gains the new argument (Hilt supplies the repository in the
app; the unit test passes its existing `InMemoryWorkoutRepository`). Update the
`BackupViewModel` kdoc to mention the running-workout flag, and
`onExportBackupRequested`'s to say a failed read reports `OperationFailed`. Add the final
dialog text to `docs/items/design-3-redraws.md`'s design findings: the warning paragraph
goes first and appears only when a workout is running, in place of design pass 1's
always-on sentence.

Tests (a failing test is written first for each):
- ViewModel unit: `hasRunningWorkout` is `false` with no session, becomes `true` once a
  running session is inserted, and goes back to `false` when it is completed. With a
  small delegating `WorkoutRepository` whose `observeActiveSession` throws, it is `true`
  and `onRestoreFilePicked` still stages the restore, so the warning reaches the dialog.
- ViewModel unit, failed export (R5): build `ExportBackup` over that same failing
  repository with `findAllForBackup` throwing as well, as in production, where both read
  the same row. The class's own `exportBackup` (`BackupViewModelTest.kt:53-62`) uses the
  plain repository, so this test needs its own. `onExportBackupRequested` then leaves
  `isBusy` false, sets `statusMessage` to `OperationFailed` and doesn't call `onReady`.
- Instrumented screen: with `pendingRestoreJson` set and `hasRunningWorkout = true` the
  dialog shows `backup_restore_confirm_running_workout`; with `false` it does not; in
  both cases `Replace my data` calls the confirm callback once and `Cancel` the cancel
  callback.
- Instrumented, large fonts: render `BackupRestoreConfirmText(hasRunningWorkout = true)` in
  a short box at font scale 1.3 and 2.0, with the `Density` override from
  `RepFlowSearchFieldTest.kt:47-51`. The warning is displayed without scrolling, and the
  body can be scrolled to. The test renders the text composable because it hasn't been
  checked whether a `LocalDensity` override reaches a dialog's own window; the functional
  review checks the real dialog at font scale 2.0 on the emulator.

Verification for the whole item (run once, after CP2), using `AGENTS.md`'s commands:
`./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug
assembleDebugAndroidTest`, then `./gradlew connectedDebugAndroidTest` on
`emulator-5554` only, within the memory caps. CI only compiles instrumented tests, so
the device run is the only one that executes the round trip.
<!-- /CP2 -->

## Review decisions

Plan review round 1 (`LOCAL_MODEL_PLAN_REVIEW`, verdict `REVISE`, on revision 1). Each
finding was checked against the code on the base commit. All are accepted and none is
rejected.

| Finding | Decision and evidence | Applied in |
|---|---|---|
| I-1 a damaged row crashes Export backup | Accepted. `toSessionOrThrow` throws on a row that fails to map (`LocalWorkoutRepository.kt:107-113`), `onExportBackupRequested` has no failure path (`BackupViewModel.kt:77-83`), and the only `CoroutineExceptionHandler` in `app/src/main` is the rest receiver's (`RestTimerExpiredReceiver.kt:98`). Today's export reads `COMPLETED` rows only (`WorkoutSessionDao.kt:39`), so the crash on a running or abandoned row would be new. Revision 1's "(export still works)" couldn't fail, because `BackupViewModelTest.kt:53-62` builds `exportBackup` over the plain repository. | Design item 3, R5, CP2 Work and Tests |
| O-1 the warning can clip at large font | Accepted, using both remedies: scrolling alone leaves the warning below the fold, and putting it first alone leaves the body clipped. `DESIGN_SYSTEM.md:17-19` asks for 1.3 and 2.0, so the test covers both. The library source isn't in the local Gradle cache, so the slot's behaviour wasn't confirmed; the test pins the result either way. | Design item 2, CP2 Tests |
| O-2 wording differs from the design pass | Accepted. Pass 1's always-on sentence is at `design-passes/pass1.md:51`, and the council's request for one shared wording at `council-2026-11-04/round3-app.md:22`. The conditional paragraph stays, because it is what R3 asks for, and the final text goes to `design-3-redraws`. | Design item 2, CP2 Work and Files |
| O-3 round-trip test gaps | Accepted, both. CP1's seed started no rest timer, and `workout_exercises.exercise_id` is a foreign key (`WorkoutExerciseEntity.kt:29-33`). | CP1 Tests |
| O-4 stale comments | Accepted. All three places still say what the finding reports, and all are in CP1's files. | CP1 Work |
| O-5 leftover rest alarm | Accepted as the finding advises. The alarm is cancelled only at `ActiveWorkoutRoute.kt:88`, `SystemRestNotificationCanceller` clears only a posted notification (`RestNotificationCanceller.kt:32-36`), and the gate is `RestTimerExpiryHandler.kt:57`. | Self-review notes, CP1 Work and Files |
| Missing tests (failed export, rest timer seed) | Accepted, as part of I-1 and O-3. | CP2 Tests, CP1 Tests |
| Usability (long elapsed time, over-warning) | Accepted as recorded notes, with no change. `docs/items/overnight-workout.md` already tracks the first. | Self-review notes |

## Self-review notes (SELF_REVIEWING_PLAN)

- **Size:** two checkpoints, no new mechanism: a repository method that copies an
  existing one, a flag that copies the settings observation, and a failure path that
  copies that observation's `.catch`. Inside the item's "one new mechanism at most" rule.
- **Restoring a running workout from a file:** it comes back running with its saved rest
  timer, which may already be past its end. Rest-timer expiry is already handled when
  the workout screen opens, and the round-trip test covers that the row and timer fields
  survive. This plan does not change timer handling.
- **Leftover rest alarm:** only the workout route cancels the OS alarm, and the restore's
  `restNotificationCanceller.cancel()` clears only a posted notification. Today a restore
  leaves no active session, so `RestTimerExpiryHandler.kt:57` stays silent. If a rest is
  running during the restore and the file's running session holds a rest timer, the old
  alarm now posts "Rest done" and buzzes for a rest that isn't running. It happens once
  and in a narrow case, so it is accepted, and CP1 corrects the handler's comment.
- **A restored running workout can look stale:** Home can show a very long elapsed time,
  it blocks a new workout until finished or abandoned (`StartWorkoutSession.kt:25`), and
  finishing it can record a many-hour duration. `docs/items/overnight-workout.md` already
  tracks this.
- **A damaged row blocks every export:** with one unreadable session, each attempt fails
  with the existing message, so no backup can be taken while the row exists. For an
  abandoned row, no screen removes it short of `Erase all data`, and `Try again` won't
  help. Accepted: refusing and saying so is better than a file that silently drops data,
  and the review found no path that writes such a row short of storage damage.
- **Old backups:** a file written before this change holds completed sessions only and
  restores as it does today.
- **Warning accuracy:** the flag reads the same `ACTIVE` row that `clearTrainingData`
  deletes, so the warning and the deletion cannot disagree. A running workout started
  after the file is picked but before `Replace my data` is covered by the live flag.
  After a failed observation the flag stays `true` until the user leaves the screen,
  because `.catch` ends the flow; that can only over-warn.
- **Read consistency:** `findAllForBackup` maps sessions one after another, like the
  completed-session read it replaces; a set logged in the middle of an export could be
  missing from that one file. The next backup has it, and the window is the length of
  one read.
- **Offline-first:** no network involved.
