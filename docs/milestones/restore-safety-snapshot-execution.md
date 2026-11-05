# restore-safety-snapshot — Execution Plan (Revision 5)

- Work item: `restore-safety-snapshot` (backlog order 22, size 2, `docs/items/restore-safety-snapshot.md`)
- Governing workflow version: `2.2`
- Base commit: `255cd7a1be74652235e713ed547ded5d0d408e9f`
- Plan revision: 5

Pull request title: `feat: keep a copy of your data before a restore and restore it from Backup`

## Goal and scope

Don't lose data, don't hide failures. Owner decision 5(b), re-checked against the base commit:
today a restore replaces everything and "can't be undone" (`strings.xml` `backup_restore_note`,
`backup_restore_confirm_body`). A wrong file picked, or the right file at the wrong time, loses
every workout since the last manual export.

After this item, **a confirmed restore on a phone with training data first saves one copy of that
data on the phone**, and the Backup screen offers to restore that copy. One copy only: a newer
successful restore replaces the older copy.

**Design source.** Boards 40e, 40f, 40j and 40k (Claude Design project "Repflow mobile app
design", page `4 Recovery Settings Backup`; `design-3-redraws` is `done`). The page wins over any
older board or doc. `docs/items/design-3-redraws.md` describes the dialog text before this item:
40j says "Applies with the safety copy; until then the app's current text stands." The external
reviewer cannot read Claude Design, so the rules and the text are quoted here.

**Out of scope:** the reminder when the last backup is old (`backup-reminder`, order 23); keeping
several copies (`PROJECT_BRIEF.md` says "multiple recent snapshots", the owner chose one, and the
brief is left as it is); a copy for `Erase all data` other than deleting the copy (below); a
copy in shared storage or the cloud; including the copy in CSV export; the export-side dead end
on issue #29; the style of any other dialog (`dialogs-and-sheets`, order 50, owns Leave, Finish,
Abandon and Discard).

## Design (one new mechanism)

The one new mechanism is a **private store holding the latest pre-restore copy**, behind an
application port.

**Order** (40k, quoted): "Order: check the file, save the new copy, restore, and only then drop
the old copy. An invalid file or a failed restore leaves the old copy in place." So the store has
two states, a new copy beside an old one, and the old one is replaced only after the database has
committed.

- `PreRestoreSnapshotStore` (application, pure Kotlin):
  - `saveNew(json)` returns a `DomainResult` (failure = `BackupRestoreError.Unavailable`) and
    durably writes the new copy beside the old one. It first promotes any leftover new file, so a
    later failed restore cannot take away the copy the user was being offered.
  - `promoteNew()` replaces the old copy with the new one, and `discardNew()` deletes the new one.
    Neither returns a failure: the restore's own outcome is what the user needs to hear. A
    promotion that fails leaves a leftover, which wins at the next load.
  - `load()` returns the JSON and the time it was taken, or `null`. `takenAt()` returns only the
    time, so opening the Backup screen never reads the user's whole history into memory. In both,
    **a leftover new file wins**.
  - `clear()` returns a `DomainResult` and deletes every file.
- `FilePreRestoreSnapshotStore` (data): three names in the app's private `filesDir` (not shared
  storage; `allowBackup` is already `false`): `pre-restore-snapshot.tmp`,
  `pre-restore-snapshot.new.json`, `pre-restore-snapshot.json`.
  - `saveNew` writes the temporary file, calls `FileOutputStream.fd.sync()` before closing it
    (`flush()` does not reach the disk), then moves it to the new name. A rename can otherwise
    reach the disk before the contents, leaving the data replaced and the copy empty.
  - Every move is `java.nio.file.Files.move` with `ATOMIC_MOVE` (minSdk is 28), and every delete is
    `Files.deleteIfExists`. `File.renameTo` and `File.delete` report failure by returning `false`,
    which a narrow `IOException` catch would miss, so `saveNew` would report success and the
    restore would go ahead with no copy.
  - The time is the file's last-modified time, which a rename keeps. IO runs on the existing
    `@IoDispatcher`. `IOException` is caught narrowly and `CancellationException` is never
    swallowed. An unreadable file reads as "no copy".
- `PreRestoreCopy` (a `fun interface` in application) has one method,
  `aroundRestore(restore)`, and one implementation, `SavePreRestoreCopy`. It is a port only so
  `RestoreBackup`'s test call sites can pass `PreRestoreCopy { restore -> restore() }` instead of
  building seven repositories. The text is exactly what `ExportBackup` produces, so a
  copy is an ordinary backup file in the versioned transfer schema
  (`BackupSnapshot`/`BackupJsonMapper`); no Room entity is serialized.

**"No training data"** is a new `TrainingDataRepository.hasTrainingData()`, one `EXISTS` query over
the ten training-data tables in `TrainingDataDao`, the port that already defines the term. Nothing
seeds them (the only database callback is `SETTINGS_SEED_CALLBACK`), and the `settings` row does
not count. `SavePreRestoreCopy` and the dialog ask the same question, so they cannot disagree. 40k:
"If this phone has no training data, no copy is made and the card does not appear." 40f: "That phone
had no data to protect, so there is no safety card". The old copy, if any, stays, because the dialog
then says nothing about replacing it.

**Restore order.** `RestoreBackup` parses the file first, so a bad file never touches the copy.
Only a valid file then runs `preRestoreCopy.aroundRestore { backupRepository.replaceAll(...) }`,
and `SavePreRestoreCopy` does this, holding `PreRestoreCopyLock` (below) throughout:

1. Reads `hasTrainingData()` and, if true, `ExportBackup()`, wrapped in the same
   `flow { ... }.catch { ... }` shape `BackupViewModel` uses for export, rethrowing
   `CancellationException`. The catch surrounds only this read, so the rule at
   `LocalBackupRepository.kt:53-57` ("anything else thrown on this path is a program bug and should
   crash") stays true for parsing and `replaceAll`. It cannot catch by type: `SQLiteException` and
   `WorkoutMappingException` live where `LayerBoundaryTest` forbids `application` to import. A read
   that throws returns `BackupRestoreError.Unavailable` and does not restore, so the user sees the
   existing "Couldn't complete the operation" message, not a crash.
2. With no training data: runs `restore()` and returns its result. Nothing is written.
3. `store.saveNew(json)`. A failure returns `Unavailable` and does not restore ("If the copy can't
   be saved, nothing is replaced", 40j).
4. `restore()`. A returned `Success` calls `promoteNew()`. A returned `Failure` calls
   `discardNew()`, which leaves the old copy byte-identical. **Only a returned `Failure` discards.**
   Anything thrown, `CancellationException` included, discards nothing, because a suspending call
   can resume with `CancellationException` after its work has finished, and on Room 2.8 a closed
   database also fails a suspend read that way (`fix-workout-error-followups`, CP1). A leftover new
   file is therefore kept and wins.

**Leaving the screen does not abandon a restore.** Leaving the Backup route cancels
`viewModelScope`. With the rule above, a cancellation that rolled the transaction back would leave
a new file holding the unchanged data, and it would win over the old copy, which is the data loss
this order exists to prevent. So `onRestoreConfirmed` runs its whole body under
`withContext(NonCancellable)`, started `UNDISPATCHED`, as `onExportWriteSucceeded` already does in
the same class. The user has confirmed the restore, so it finishes, and
`restNotificationCanceller.cancel()` still runs for a workout it deleted.

**One owner for the whole sequence.** A restore that outlives its screen can overlap the next one,
because reopening Backup builds a new `BackupViewModel` with `isBusy = false` over the same three
files. With data A and nothing to stop it: restore 1 saves A and starts restoring B; before that
commits, restore 2 reads A and calls `saveNew`, which takes restore 1's active new file for a
leftover, promotes it and writes another A; restore 1 commits B and promotes; restore 2 commits C and
has nothing left to promote. Both report success and the copy holds A, when the data restore 2
replaced was B. An erase has the same problem: a restore that read the data before the erase could
write its copy after it, bringing back what the user just erased. Locking each file operation fixes
neither, because the rule is about the whole sequence.

So `PreRestoreCopyLock` (application, pure Kotlin) is one `kotlinx.coroutines.sync.Mutex` behind a
`withLock`, in a `@Singleton` class so every caller in the process shares it:

- `SavePreRestoreCopy.aroundRestore` holds it for all of the steps above, from the read through
  `restore()` to the promote or discard. `EraseAllData` holds it for `clear()` and
  `clearTrainingData()`. Whichever comes second waits (`Mutex` is first come, first served) and then
  reads what the first left, so in the example restore 2 saves B and the copy ends up holding B. A
  new file found under the lock really is a leftover, from a process death or a failed promote or
  discard.
- The parse stays outside it, so an invalid file never waits.
- A confirmed restore waits under its `NonCancellable`, so it still runs. An erase waits in
  `viewModelScope`, where it runs today, so leaving Settings while it waits cancels it and deletes
  nothing.
- Every `BackupViewModel` read of the store or of `hasTrainingData` takes it too (CP2). Without that,
  a reopened screen could `load()` a new file that the running restore then discards, and restoring
  that text would put the same data back and replace the old copy with it. `init` sets `isBusy` until
  it has the lock, so a screen reopened during a restore keeps its buttons disabled until that restore
  ends, and then shows the right card.
- It is not re-entrant and does not need to be: nothing inside it takes it again (`replaceAll` calls
  `TrainingDataRepository.clearTrainingData`, not `EraseAllData`), and it is never taken inside a Room
  transaction, so it cannot deadlock with one.
- `@Singleton` is what makes this work, and two instances would compile and pass any test that builds
  its own, so `PreRestoreCopyLockTest` asserts the annotation. `LayerBoundaryTest` allows `javax.inject`
  and `kotlinx.coroutines` in `application`. A process death ends the lock and the restore together,
  and the next process finds a leftover under the rule above.

**The outcome outlives the screen too.** Without this, the message of a restore that outlives its
screen goes to the cleared `BackupViewModel`. Reopen Backup during a restore that then returns
`Unavailable`, and the new screen releases its busy state and shows the old card with no error, which
the item's "don't hide failures" rules out. So `RestoreOutcomeSlot` (presentation) holds
one `BackupStatusMessage?` in a `MutableStateFlow`, in a `@Singleton` class with an `@Inject`
constructor, for the same reason as the lock. `RestoreOutcomeSlotTest` asserts the annotation too.

- `onRestoreConfirmed` calls `report(message)` for every outcome, inside its `NonCancellable`, and only
  then re-reads the card, because that read can wait for the lock. It never asks whether its ViewModel
  is still alive: `viewModelScope` can still be active with the screen already gone, and that branch
  would lose an outcome that ended in between.
- Every `BackupViewModel` collects the slot for as long as it lives and copies a non-null value to
  `statusMessage`. A screen reopened before the restore ends gets the message when it ends, one opened
  afterwards gets it at once, and a screen still open gets it as it does today.
- `BackupMessages` calls a new `onStatusShowing` action as the message starts, and the ViewModel then
  calls `consume(message)`, which empties the slot only if it still holds that message. `onStatusShown`
  still clears `statusMessage` when the snackbar ends, for the reason in the comment there, and the
  ViewModel then calls `consume` again with the message it clears. So a back press during the snackbar
  does not show the message again at the next visit, and a message that never reached a snackbar stays
  for the next one.
- The second `consume` is for an outcome equal to the one on screen. A `MutableStateFlow` ignores a value
  equal to the one it holds, and `LaunchedEffect(message)` (`BackupScreen.kt:143`) does not restart for
  an equal key. So that outcome merges into the snackbar already showing, as it does at the base commit,
  and no effect starts to consume it. Without this `consume` the slot keeps it after the snackbar ends:
  the next equal outcome would change nothing and show nothing, and a later visit would report a restore
  that ended earlier. If the user leaves between that outcome and the snackbar's end, it stays for the
  next visit, because it never got a snackbar of its own. The slot's collector runs on `viewModelScope`'s
  `Main.immediate` and `consume` runs on the main thread, so the collector sees the empty slot at once,
  and the next equal outcome is a change it can see.
- One slot is enough. The parse does not suspend, so a confirmed restore reaches the lock before anything
  else can run, and a reopened screen's buttons come back only after that restore has ended. If two
  outcomes ever did meet, the newer replaces the older, as `statusMessage` already does, and it describes
  the data the user now has.
- There is no new text: it is the same "Backup restored", "That file isn't a valid RepFlow backup." or
  "Couldn't complete the operation. Try again."

**Restoring the copy** runs the same restore: the Backup screen loads the copy's text into
`pendingRestoreJson`, 40j's dialog runs, then `RestoreBackup`. That restore saves a new copy of the
data being replaced first, so the two states swap (40k: "the two can be swapped"). There is no
second restore path. If it fails, the copy is still there and the user can try again from the card.

**It does not change `Last backup`** (40k: "it is not a backup: it does not change Last backup").
Neither restore writes `lastBackupAt`.

**Erase all data** deletes the copy first (it holds the very data being erased; 40e: "Erase all
data also deletes the safety copy (40k), and its card goes"), then clears the training data. It
holds `PreRestoreCopyLock` for both, so it waits for a running restore and no copy can be written
after it. If deleting the copy fails, the erase stops with `TrainingDataError.Unavailable`, the
existing failure, and nothing else is deleted. Settings are untouched, as today.

**Accepted corners.**

- *The process dies after the new file is saved and before the database commits.* The new file
  wins at the next load, so the old copy is lost and the data is unchanged. If that restore was of
  the copy itself, what the copy held is gone. The other way round would be worse: a process death
  just after the commit must not lose the data that was just replaced. Telling the two apart
  reliably needs a marker written inside the transaction, which is a schema change this item does
  not make. The window is one transaction and needs the process to die in it; a back press does not
  reach it (above). A `discardNew` that fails has the same result, and needs a delete in the app's
  own directory to fail.
- *A power cut within seconds of a restore* can lose a move, because the file's contents are synced
  and its directory is not. The restore stands and the older copy stays on offer. Syncing the
  directory needs a platform call that no test here can exercise.
- *The outcome of a surviving restore waits in memory for the Backup screen.* Only Backup shows it, at
  the next visit however late that is, and an erase in between does not remove it. A process death
  before that visit loses the message: the user finds the data and the old copy unchanged after a
  failure, or the restored data after a success. A process death during a restore already goes
  unreported at the base commit. Keeping the message across one needs a file or a settings column for
  one line of text.
- *One damaged row now blocks a restore as well as an export.* `findAllForBackup` throws on one
  unreadable session (`LocalWorkoutRepository.kt:57,125`), and at the base commit a restore never
  read the current data (`RestoreBackup.kt:18-22`), so it was how a user replaced damaged data. Now
  the user sees "Couldn't complete the operation. Try again." every time, and the way out is `Erase
  all data`, then restore. 40j's "If the copy can't be saved, nothing is replaced" asks for this.
  The wrap-up adds a note to issue #29.
- *Another screen can write while a surviving restore runs.* At the base commit, leaving Backup cancels
  the restore (`BackupViewModel.kt:172`). Now it finishes, so a user who goes back to Home and starts a
  workout in those seconds can lose it. A write that lands after `ExportBackup`'s read and before the
  commit is in neither the restored data nor the copy. The running-workout warning cannot have covered
  a workout that did not exist when the user confirmed, and the outcome shows only on Backup. The window
  is short and needs the user to leave and act inside it, and a guard would need every write path in the
  app to take the lock.
- *The Erase failure message can become untrue.* If the copy is deleted and `clearTrainingData`
  then fails, the user sees "Couldn't erase. Nothing was changed." (`strings.xml:672`) with the copy
  gone. Leaving Settings in that same gap has the same result, with no message. Deleting the copy
  first is still the right order for privacy.
- The copy is not protected against the user clearing app storage. A copy taken by an older app
  version is restored by the same parser as any backup file.

**Text.** Exactly as drawn, typed with the straight apostrophe `strings.xml` already uses. `2 Oct,
08:15` stands for `%1$s, %2$s`, in the hero's existing date and time formats.

- `backup_restore_note` (40k, `RNOTE`): "Restoring replaces all data on this phone with the file's
  contents. Your settings stay. RepFlow keeps a copy of your current data first, and you can
  restore that copy from this screen. Export a backup first."
- Card (40k), its own card below the restore card, not a row inside it:
  - Label: "Before your last restore"
  - Title: "Safety copy, 2 Oct, 08:15"
  - Meta: "RepFlow kept this copy of your data just before you restored. It stays on this phone,
    and the next restore replaces it."
  - Outlined button: "Restore the safety copy"
- Dialog (40j). The existing running-workout paragraph ("A workout is running on this phone.
  Restoring deletes it, with every set you've logged.") stays first in every variant, shown under
  today's condition. The four variants drawn are these two, with and without it.
  - **From a file**, title "Restore this backup?":
    1. "This replaces every workout, plan, exercise, recovery entry and recommendation on this
       phone with the file's contents. Your settings stay as they are."
    2. "RepFlow first keeps a copy of what is on this phone. You can restore it from Backup and
       restore."
    3. "This replaces the safety copy from 2 Oct, 08:15." Only when a copy exists. It is the one
       warning for a user about to lose a good copy (40k: "Restoring a wrong file and then a second
       file replaces the good copy with the wrong data").
  - **The safety copy**, title "Restore the safety copy?":
    1. "This replaces every workout, plan, exercise, recovery entry and recommendation on this
       phone with the safety copy from 2 Oct, 08:15. Your settings stay as they are."
    2. "RepFlow first keeps a copy of what is on this phone, so you can switch back."
  - **No training data** (40j: "with no data on the phone, the dialog leaves out the copy
    paragraphs"): only each variant's paragraph 1, because no copy is made and none is replaced.
    A failed read of `hasTrainingData` counts as having data, so the paragraphs are never silently
    dropped, as `hasRunningWorkout` already does.
- Buttons (40j): "Cancel on the left, Replace my data outlined in the destructive colour and never
  filled; stacked at large font with the destructive action on top." Both are outlined, at least
  48dp. "Large font" is above 1.1, the board's `stack()` and the app's existing
  `STACK_ACTIONS_FONT_SCALE`. Today's are text buttons (`BackupFileActions.kt:106-118`). Both move
  into `AlertDialog`'s `confirmButton` slot, in a `Row` or `Column` this code controls, so the order
  does not depend on how Material wraps its own row. The text keeps scrolling and the buttons stay
  visible.

**Registered deviations from the boards.**

- *The frame around the card.* 40k draws `Restore from a backup` as a plain outlined button with
  the note below it, and the CSV action as an outlined button. The app keeps today's layout, the
  destructive `Replaces everything` card with the note above its button, and the hairline CSV row.
  Redrawing that is not this item's work. The safety card sits directly below the restore card, so
  it is still "below the restore note".
- *40g's states.* The inline invalid-file message and the `Restoring…` card are not added. Today's
  snackbar and disabled buttons stay. Neither is part of the safety copy.

The `no safety-snapshot row` deviation in `BackupScreen.kt:68-71` comes out, because this item
draws it.

No Room entity, schema or migration change (`hasTrainingData` is a new query on existing tables);
no new dependency or Gradle module; nothing touches the network. No "Open decision" row in
`docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | A confirmed restore of a valid file follows 40k's order: check the file, save the new copy beside the old one, restore, then replace the old copy. If the copy cannot be built or saved, the restore does not happen and the user sees the failure message. An invalid file or a failed restore leaves the old copy byte-identical, whether the restore was of a file or of the copy. Leaving the screen does not abandon a confirmed restore. Restores never overlap: a second one waits for the first, so the copy holds the data immediately before the last restore, apart from the accepted corner of a write another screen makes during it. On a phone with no training data no copy is made and any old copy stays. |
| R2 | The Backup screen shows 40k's card, below the restore card, only when a copy exists, so a phone restored with no data to protect shows none. `Restore the safety copy` goes through 40j's confirmation and restores it. Neither restore changes `Last backup`. A screen reopened during a restore keeps its buttons disabled until that restore ends, and then shows the card as it now stands. The outcome of a restore, failure or success, shows on the Backup screen even if the screen that confirmed it has gone: when that restore ends if Backup is open, and otherwise at the next visit, once. An outcome equal to the one on screen merges into that snackbar, and the next equal outcome still shows. |
| R3 | `Erase all data` also deletes the copy, and does nothing else if that fails. It waits for a running restore, so a completed erase leaves neither training data nor a copy. The copy lives only in the app's private storage. |
| R4 | The restore note and confirmation no longer say a restore can't be undone. They are 40k's and 40j's text and buttons: file and copy variants, the running-workout warning first, the safety-copy paragraph only when a copy exists, and no copy paragraphs on a phone with no training data. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Save a copy before a restore, and delete it on Erase (R1, R3) | - | 3 | 1 |
| CP2 | Offer the copy on the Backup screen and fix the text (R2, R4) | CP1 | 3 | 1 |

<!-- CP1 -->
### CP1 — Save a copy before a restore, and delete it on Erase (R1, R3)

**Files:**
`app/src/main/kotlin/com/repflow/app/application/backup/PreRestoreSnapshotStore.kt` (new),
`app/src/main/kotlin/com/repflow/app/application/backup/PreRestoreCopy.kt` (new, also holds the SavePreRestoreCopy class),
`app/src/main/kotlin/com/repflow/app/application/backup/PreRestoreCopyLock.kt` (new),
`app/src/main/kotlin/com/repflow/app/application/backup/RestoreBackup.kt`,
`app/src/main/kotlin/com/repflow/app/application/backup/EraseAllData.kt`,
`app/src/main/kotlin/com/repflow/app/application/backup/TrainingDataRepository.kt`,
`app/src/main/kotlin/com/repflow/app/data/backup/FilePreRestoreSnapshotStore.kt` (new),
`app/src/main/kotlin/com/repflow/app/data/backup/LocalTrainingDataRepository.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/database/TrainingDataDao.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/di/RepositoryModule.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupViewModel.kt`,
`app/src/test/kotlin/com/repflow/app/application/backup/InMemoryPreRestoreSnapshotStore.kt` (new),
`app/src/test/kotlin/com/repflow/app/application/backup/InMemoryTrainingDataRepository.kt`,
`app/src/test/kotlin/com/repflow/app/application/backup/RestoreBackupTest.kt`,
`app/src/test/kotlin/com/repflow/app/application/backup/EraseAllDataTest.kt`,
`app/src/test/kotlin/com/repflow/app/application/backup/SavePreRestoreCopyTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/application/backup/PreRestoreCopyLockTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/settings/SettingsViewModelTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/backup/FilePreRestoreSnapshotStoreTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalTrainingDataRepositoryTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/data/backup/PreRestoreSnapshotRoundTripTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalBackupRepositoryVersionCompatibilityTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalBackupRepositoryDamagedFileTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/settings/SettingsPersistenceTest.kt`.

Add the port, the file store, `PreRestoreCopyLock`, `SavePreRestoreCopy` (it takes the lock and holds
it for its whole body), `hasTrainingData()`, the `RestoreBackup` change (new constructor argument,
`replaceAll` runs inside `aroundRestore` after a successful parse) and the `EraseAllData` change
(constructor takes the store and the lock; under the lock, `clear()` first, a failure mapped to
`TrainingDataError.Unavailable`). Bind the store and `PreRestoreCopy` in `RepositoryModule`; the lock
needs no binding, because its class carries `@Singleton` and an `@Inject` constructor. In
`BackupViewModel`, this checkpoint changes only `onRestoreConfirmed`, for `NonCancellable`.
`BackupViewModelTest`, which already builds all seven repositories, passes the real
`SavePreRestoreCopy` with the fake store and a real lock. The three instrumented `RestoreBackup(...)`
sites pass `PreRestoreCopy { restore -> restore() }`. `EraseAllData(...)` takes the fake store and a
real lock in `EraseAllDataTest` and `SettingsViewModelTest`, and the real store and a real lock in
`SettingsPersistenceTest`. In `InMemoryTrainingDataRepository`, `hasTrainingData` is a value a test
sets and `clearTrainingData` makes `false`.

Tests, JVM:

- `PreRestoreCopyLockTest`: the class carries `@Singleton`; a second `withLock` does not start until
  the first returns; waiters run in the order they asked; a block that throws, or is cancelled,
  releases the lock.

- `RestoreBackupTest`: `replaceAll` runs inside the copy; a copy that refuses returns `Unavailable`
  and `lastReplacedWith` stays `null`; an invalid file never calls the copy.
- `SavePreRestoreCopyTest`: the saved text is `ExportBackup`'s output, saved before the restore
  runs; a returned `Success` promotes; a returned `Failure` discards and the old copy is
  byte-identical; a thrown `CancellationException` is rethrown, discards nothing, and the new copy
  wins at the next load; a failing save returns `Unavailable` and does not restore; a read that
  throws (`hasTrainingData` or the export) returns `Unavailable`, does not restore and calls no
  store write, and a `CancellationException` from it is rethrown; with no training data the restore
  runs, nothing is written and the old copy stays. Two overlapping calls sharing one lock and store,
  the first suspended once in its read and once in `restore()`: the second reads nothing and calls no
  store method until the first has finished, and the copy then holds the data the first left. If the
  first's `restore()` returns a `Failure`, the second still runs, against the unchanged data.
- `EraseAllDataTest`: the store is cleared before the data; a failing clear returns
  `TrainingDataError.Unavailable` and clears no data.
- `BackupViewModelTest`: `viewModelScope` cancelled during `replaceAll` still finishes the restore,
  promotes the copy and cancels the rest notification, in the same way as the existing test at
  `BackupViewModelTest.kt:287-291`.
- `BackupViewModelTest`, overlap. Two `BackupViewModel`s share the store, the lock and the
  repositories, through a local `BackupRepository` whose `replaceAll` waits on a gate and then applies
  the snapshot's exercises to the fake repository, so the next `ExportBackup` reads them. With data A,
  the first confirms B and is suspended in `replaceAll`, its `viewModelScope` is cancelled, and the
  second confirms C. `replaceAll` is not entered again while the gate is shut. With the gate open the
  data is C, and `load()` holds B, not A.
- `BackupViewModelTest`, erase against a surviving restore, same set-up. An `EraseAllData` sharing the
  store and the lock, started while the first restore is suspended, touches neither the store nor the
  data until that restore ends. It then returns `Success`, `hasTrainingData()` is false and `load()` is
  `null`. In the other order, the file is staged before the erase starts, so CP2's lock on staging cannot
  leave the confirm with nothing to do. With the erase suspended in `clearTrainingData`, a restore
  confirmed meanwhile waits, finds no training data, writes no copy and still runs: the data is the
  file's and `load()` is `null`. An erase cancelled while it waits deletes nothing.

Tests, instrumented on `emulator-5554`:

- `FilePreRestoreSnapshotStoreTest`: save, promote and load round trip the text and a time, and
  `takenAt` agrees; a second round replaces the first; an absent file loads `null`; a discard leaves
  the old copy byte-identical; a leftover new file wins in both `load` and `takenAt`; `saveNew` over
  a leftover promotes it first, so a discard then leaves that leftover's text; `clear` deletes all
  three files and is idempotent; a failing delete (a non-empty directory at the new file's path)
  returns `Unavailable`; a failing write (the temp file's path made unwritable) and **a failing
  move** (a non-empty directory at the new file's path) each return `Unavailable` and leave the
  previous copy readable. The `fd.sync()` call has no observable effect in a test and is checked by
  reading.
- `LocalTrainingDataRepositoryTest`: `hasTrainingData` is false on an empty database with its
  settings row, and true with one row in each of the six parent tables in turn.
- `PreRestoreSnapshotRoundTripTest`, on the real store and database: seed data A, restore file B,
  assert the data is B and the copy restores to A; a failed `replaceAll` leaves the old copy
  byte-identical, for a restore from a file and for a restore of the copy; the whole path runs under
  `withContext(NonCancellable)` with a real Room transaction. Two overlapping restores, the first held
  at a gate just before its real `replaceAll`, end with data C and a copy that restores to B; an erase
  started during a held restore ends with `hasTrainingData` false and `load` `null`.

<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Offer the copy on the Backup screen and fix the text (R2, R4)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupUiState.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupFileActions.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupScreenActions.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupRoute.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/RestoreOutcomeSlot.kt` (new),
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelSafetyCopyTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/backup/RestoreOutcomeSlotTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/BackupScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/BackupRouteUnreadableRestoreFileTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/BackupRouteSafCancellationTest.kt`,
`docs/UX_FLOWS.md`.

`BackupUiState` gains `preRestoreSnapshotAt: Instant?`, `pendingRestoreSnapshotAt: Instant?`
(non-null means the pending text is the copy, and gives its time) and `hasTrainingData: Boolean =
true`. `BackupViewModel` takes the store, `PreRestoreCopyLock`, `TrainingDataRepository` and
`RestoreOutcomeSlot`, and every
read of the store or of `hasTrainingData` runs under the lock. `init` sets `isBusy`, reads `takenAt()`
under the lock, and then clears `isBusy`. It reads `takenAt()` again after every restore attempt, and
never `load()` for that. `onRestoreFilePicked` and the new `onRestoreSnapshotRequested()` read
`hasTrainingData` first and then set the pending text and the flag together, so the dialog never
shows the wrong variant. Cancelling or confirming
clears `pendingRestoreSnapshotAt` along with the text. A copy that has vanished shows
`OperationFailed`. After a restore of either kind the screen says "Backup restored" and the
card shows the new copy (40k).

`onRestoreConfirmed` stops setting `statusMessage` itself and reports through `RestoreOutcomeSlot`;
`init` collects the slot, a new `onStatusMessageShowing()` consumes what is showing, and
`onStatusMessageShown()` consumes what it clears. At the end of CP1 a surviving restore's outcome still stops at its cleared ViewModel. This is what fixes that, and
both checkpoints ship in one pull request. `BackupScreenActions` gains `onStatusShowing`, `BackupRoute`
wires it, and `BackupMessages` calls it first in its `LaunchedEffect`.

`BackupScreen` shows the 40k card as a `RepFlowCard` below `RestoreCard`, only when
`preRestoreSnapshotAt` is non-null, with a neutral outlined button of at least 48dp, disabled while
busy. `BackupRestoreConfirmDialog` and `BackupRestoreConfirmText` take the three new inputs and
draw 40j's text and buttons. The `no safety-snapshot row` deviation comes out of `BackupScreen`'s
KDoc. `docs/UX_FLOWS.md` gets two sentences beside `Restore from a backup`: the card, and that a restore
keeps running after the user leaves and reports at the next visit.

Tests, JVM, in a new `BackupViewModelSafetyCopyTest`, because `BackupViewModelTest` is 458 lines at the
base commit, CP1 adds to it, and detekt's `LargeClass` limit is 600. `BackupViewModelTest` keeps its
existing tests and CP1's. The new ones: the time is exposed after init and after a restore; init never calls `load()`; tapping the
action stages the copy's text and confirming restores it and writes a new copy; a restore of the
copy whose `replaceAll` fails leaves the old copy byte-identical and still offered; a restore whose
export read throws shows `OperationFailed`, restores nothing and does not crash; a copy that
vanished shows `OperationFailed`; the flag is set for both ways in and a failed read leaves it
`true`; a restore from a file and a restore of the copy each leave `lastBackupAt` unchanged and
report `RestoreSucceeded`. A ViewModel built while another's restore is suspended is busy, shows no
card and stages nothing (`onRestoreFilePicked` and `onRestoreSnapshotRequested` both wait). When that
restore succeeds, it is idle and shows the new copy's time. When that restore fails, it shows the old
copy's time and `onRestoreSnapshotRequested` stages the old copy's text. CP1's overlap test changes
for the same reason: its second ViewModel now stages C only after the gate opens, and then confirms,
and the data is C and `load()` holds B as before. `SavePreRestoreCopyTest` keeps covering two restores
that both reach the lock.

Tests, JVM, the outcome of a restore that outlives its screen, in the same file. They use a local
`BackupRepository` whose `replaceAll` waits on a gate and then returns the result the test gave it. In
each, the first ViewModel confirms a restore and is suspended in `replaceAll`, and its `viewModelScope`
is cancelled, except where the case says otherwise:

- Reopened before the restore ends: a second ViewModel is busy with no message. The gate opens on
  `Unavailable`. The second is idle with `statusMessage` `OperationFailed`, shows the old copy's time,
  and `load()` holds the old copy.
- Reopened after the restore ends: the gate opens on `Unavailable` with no ViewModel alive. A second
  ViewModel built afterwards has `OperationFailed` and is idle.
- The same two ending in `Success`: `RestoreSucceeded` and the new copy's time.
- Shown once: after the second ViewModel's `onStatusMessageShowing`, a third has no message, whether or
  not `onStatusMessageShown` ran first (a back press during the snackbar).
- Not yet shown: the first ViewModel is not cancelled, the restore ends, `onStatusMessageShowing` is never
  called, and only then is it cancelled. The next ViewModel has the message.

Tests, JVM, an outcome that repeats while its snackbar is up, in the same file, with the same local
`BackupRepository` and its gate open, on the eager `Main` dispatcher `BackupViewModelTest` already sets.
One ViewModel stays open and `replaceAll` keeps returning `Unavailable`. The file is staged and confirmed
and `onStatusMessageShowing` is called; the file is staged and confirmed again; then
`onStatusMessageShown` is called, and `statusMessage` is `null`:

- A third restore: `statusMessage` is `OperationFailed` after it.
- A new ViewModel instead of the third restore: it has no message.
- The same two with `replaceAll` returning `Success`: `RestoreSucceeded`, and no message.
- Stopping before `onStatusMessageShown`, as a back press does, and building a new ViewModel: it has the
  message, the one outcome that never got a snackbar of its own.
- A different outcome during the snackbar, first `Unavailable` and then `Success`: `statusMessage` becomes
  `RestoreSucceeded`, and after its `onStatusMessageShowing` and `onStatusMessageShown` a new ViewModel has
  no message.

`RestoreOutcomeSlotTest`: the class carries `@Singleton`; `consume` empties the slot only for the message
it names, so a newer message survives an older one's `consume`.

Tests, instrumented (`BackupScreenTest`):

- While a status message's snackbar is on screen, `onStatusShowing` has run once and `onStatusShown` has
  not. When the snackbar ends, `onStatusShown` runs once.

- The card is absent with no copy; with one it shows the exact label, title and meta below the
  restore card; tapping it reports the action; it is disabled while busy.
- All four dialog variants show their exact text: the safety-copy paragraph only when a copy
  exists, and the running-workout warning first in each.
- With no training data, the file and copy variants show paragraph 1 only.
- The copy variants pass the existing check at font scale 2.0: the warning shows without scrolling
  and uncut, and the body scrolls into view.
- `Cancel` is left of `Replace my data` at font scale 1.0, and `Replace my data` is above `Cancel`
  at 2.0.
- The existing running-workout and large-font tests keep passing with the changed body string.

Tests, instrumented, the two route tests. `init` now keeps the buttons disabled until `takenAt()` returns
from the IO dispatcher, and Compose's test idling does not wait for that. A click in that window does
nothing, so `BackupRouteUnreadableRestoreFileTest` would fail and `BackupRouteSafCancellationTest` could
pass without exporting anything. Each adds a `waitUntil` on its button being enabled before the click.

<!-- /CP2 -->

## Verification (wrap-up)

`spotlessCheck`, `detekt`, `lintDebug`, the JVM suite, and on `emulator-5554` the backup instrumented
tests (`data/backup`, `presentation/backup`) and `SettingsPersistenceTest`, which sits under
`data/settings` and which CP1 edits (one Gradle worker at a time, within the memory caps).

Add a note to issue #29: a damaged row now also blocks a restore, and `Erase all data` then restore
is the way out. If `gh` is not permitted in that session, report the note as not posted.

## Review decisions (round 1, `LOCAL_MODEL_PLAN_REVIEW`)

Every finding was checked against the repository and the boards, and accepted.

| Finding | Resolution |
|---|---|
| B1 | Two-file store and 40k's order. The failed-restore corner is gone. One addition beyond the finding: `NonCancellable` in `onRestoreConfirmed`, because "never discard on cancellation" alone would let a back press during a restore cost the old copy. |
| I1 | `hasTrainingData()` instead of all six `BackupSnapshot` lists being empty: the dialog needs the answer before any restore, and building the whole snapshot to get it would read the whole history and fail on a damaged row. |
| I2 | The drawn text, card and dialog are quoted above, and 40j's buttons are drawn here because no other item owns this dialog. Two deviations are registered. |
| I3 | `fd.sync()`, then `Files.move` with `ATOMIC_MOVE`. The same trap applies to `File.delete`, so `Files.deleteIfExists` too. |
| O1 | `SettingsViewModelTest.kt` and `BackupViewModelTest.kt` are in CP1's Files. |
| O2 | The catch surrounds only the read in `SavePreRestoreCopy`, and all of R1 is in CP1. |
| O3 | An accepted corner, and a note to issue #29 at wrap-up. |
| O4 | `takenAt()`. |
| O5 | An accepted corner. |
| O6 | Named in the wrap-up. |
| Missing tests | All added. One moved: "a failed `replaceAll` leaves the old copy" is not in `RestoreBackupTest`, which passes a stand-in for the copy. It is in `SavePreRestoreCopyTest`, where the logic lives, in `BackupViewModelTest` end to end, and on the real store in `PreRestoreSnapshotRoundTripTest`. |

## Review decisions (round 2, `MANUAL_EXTERNAL_PLAN_REVIEW`)

Checked against the repository, and accepted.

| Finding | Resolution |
|---|---|
| I1 | Confirmed: `BackupRoute` takes a per-entry `hiltViewModel()`, `BackupUiState.isBusy` starts `false`, `main` has no `Mutex`, and revision 2's `NonCancellable` is what lets a restore outlive its ViewModel. The trace is under "One owner for the whole sequence". `PreRestoreCopyLock` covers capture, save, replace and finalize, and `EraseAllData` shares it. Two additions beyond the finding: `BackupViewModel`'s reads take the lock too, because staging a new file that a running restore then discards would cost the old copy; and `init` shows busy, so a reopened screen cannot start a second restore in the first place, and the lock is the backstop. |
| Missing tests | Both added to `BackupViewModelTest` as asked, and again on the real store and a real Room transaction in `PreRestoreSnapshotRoundTripTest`. `SavePreRestoreCopyTest` covers the lock at each stage, and `PreRestoreCopyLockTest` the `@Singleton` that makes it shared. |
| Required acceptance criteria | CP1 defines the shared ownership and coordinates erasure with it. The cancellation, failure-preservation and round-trip tests stay as they were. |

## Review decisions (round 3, `MANUAL_EXTERNAL_PLAN_REVIEW`)

Checked against the repository, and accepted.

| Finding | Resolution |
|---|---|
| I2 | Confirmed: `_uiState` belongs to each `BackupViewModel`, `BackupRoute` takes a per-entry `hiltViewModel()`, and revision 3's reopened screen learned only that the lock was free. The accepted corner is gone, and `RestoreOutcomeSlot` is under "The outcome outlives the screen too". Two additions beyond the finding. Every outcome goes through the slot, not only a failure after the ViewModel is cleared, because `viewModelScope` can still be active with the screen gone, and "Backup restored" belongs on a reopened screen as much as a failure does. And `onStatusShowing`, because consuming at `onStatusShown` would show the message again after a back press during the snackbar. What remains is narrower and is recorded as an accepted corner: a process death before the next visit. |
| Missing tests | Added to `BackupViewModelSafetyCopyTest`: reopening before and after the restore ends, for `Unavailable` as asked and for `Success`, plus shown once and not yet shown. `BackupScreenTest` proves the message is consumed while its snackbar is on screen. |
| Required acceptance criteria | The next Backup screen gets the failure through the slot, and the accepted silent failure is removed. The shared ownership and overlap tests from round 2 are unchanged. |

One change the finding did not ask for: CP2's JVM tests move to the new file. They would take `BackupViewModelTest` past detekt's 600-line `LargeClass` limit (default config; `config/detekt/detekt.yml` does not override it).

## Review decisions (round 4, `LOCAL_MODEL_PLAN_REVIEW`)

Checked against the repository, and accepted.

| Finding | Resolution |
|---|---|
| R4-I1 | Confirmed: `LaunchedEffect(message)` is at `BackupScreen.kt:143`, and revision 4 emptied the slot only from `onStatusShowing`, which runs only when that effect starts. `onStatusMessageShown` (`BackupViewModel.kt:191`) now also calls `consume`, the change the finding suggested. R2 now says what an equal outcome does. |
| O16 | Confirmed: `onRestoreConfirmed` returns at its null check (`BackupViewModel.kt:170`) when nothing is staged. Applied in CP1's test, not as a change in CP2: it stages before the erase starts and asserts that the restore ran, so CP2 does not have to rewrite it. |
| O17 | Confirmed: both route tests click straight after navigating. Each gets a `waitUntil`, and both are in CP2's Files. `MainActivityNavHostSmokeTest` only asserts the button is displayed, so it needs no change. |
| O18 | Confirmed: `BackupViewModel.kt:172` is a plain `viewModelScope.launch` at the base commit, and `main` has no guard on other write paths. Recorded under "Accepted corners", with no guard, as asked. R1 names the exception. |
| Missing tests | All added to `BackupViewModelSafetyCopyTest`, with two more: the one message a back press leaves for the next visit, and a different outcome during a snackbar, so the fix is shown not to change that case. |
| Required acceptance criteria | Met by the second `consume` and those tests. The shared ownership, overlap and reopen tests are unchanged. |

O7 to O12, the `TooManyFunctions` part of O13, and O14 and O15 are not applied here. As the verdict says, they stay on the ledger for triage before acceptance.

## Self-review notes

- *Why not keep the copy in Room?* A restore clears every training table in one transaction,
  and a copy in the database would be cleared with it or need its own table and migration. A
  file outside the database needs neither.
- *Why parse before copying?* A wrong or damaged file must not cost the user the older copy
  (R1). The parse already happens first, so the copy step comes after it.
- *Why two files, if there is one copy?* A restore of the copy needs the old copy intact until the
  database has committed, because it is the only place that data exists. The user only ever sees
  one, so "one copy" still holds, and it is still one mechanism.
- *Is the lock a second mechanism?* No. It is what keeps the store's order true once a restore can
  outlive its screen: one small class with no state of its own, and no new dependency, because
  `kotlinx.coroutines` is already in use.
- *Is the slot a second mechanism?* This is closer to the item's "one new mechanism at most" rule than
  the lock was: it is a second small class, and it holds state. It exists only because a restore can now
  outlive its screen, it holds one message with no logic of its own, and it cannot be split off, because
  without it this item hides failures.
- *Why a second `consume`, not a unique value for each outcome?* A unique value would let the next equal
  outcome through, but the slot would still hold the merged one after the snackbar ended, and a later
  visit would repeat it. The second `consume` fixes both with one line and no new class.
- *Why wait, not refuse?* A refusal needs a new message for a restore the user has already confirmed,
  and a process-wide "running" flag would still need this lock around the check. Waiting gives the
  second restore the right result with neither.
- *Why not put the lock in the store?* The fake store would then own the lock in every JVM test, so the
  tests would prove the fake's locking and not the real one. A separate class is the same code in both.
- *Why `aroundRestore`, not separate calls on `RestoreBackup`?* Which outcome promotes and which
  discards is the part that must not be got wrong, so it lives in one method with its own tests,
  and the test call sites stay one line.
- *Could undo loop forever?* Each restore replaces the copy with the data it replaces, so two
  taps swap the two states; there is never more than one copy on offer.
- *Privacy.* The copy holds the user's whole history in the app's private directory, which is
  where the database already lives; `Erase all data` deletes it (R3).
- *Large histories.* The copy is built in memory like an export; the same limit already
  applies to `Export backup now`.
- *Usability:* the card appears only when it can be used, the dialog says which copy it replaces
  before the user commits, and "Export a backup first" stays in the note, because the copy does not
  survive a lost phone.
