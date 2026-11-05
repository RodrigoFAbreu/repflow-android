# safety-copy-polish — Execution Plan (Revision 3)

- Work item: `safety-copy-polish` (backlog order 24, size 1, `docs/items/safety-copy-polish.md`)
- Governing workflow version: `2.2`
- Base commit: `df7d2197068e1c743d5f57a30d665b1c661cffe9`
- Plan revision: 3

Pull request title: `fix: block the app with a Restoring screen during a restore, and say what Erase and Backup really do`

## Goal and scope

Don't lose data, don't hide failures. Issue #34, from `restore-safety-snapshot`'s review and test.

A restore now saves a copy first, so it takes longer, and nothing says to keep the app open. While
it runs, the rest of the app stays usable: a user can go back to a workout and log a set, and that
set waits behind the restore (`fix-workout-actions-in-order` made them wait in order; it did not
stop them waiting). A set still waiting when the user leaves the workout is lost with no message,
and a correction can change the wrong set. The cleanest fix is that nothing can wait: a Restoring
screen covers the whole app from the confirmation until the restore ends.

Two wording corrections go with it. The Erase confirmation (`settings_erase_confirm_body`) does not
say it also deletes the safety copy, though board 40k does. The Backup note
(`backup_restore_note`) says "RepFlow keeps a copy of your current data first" even on a phone with
no training data, where `SavePreRestoreCopy` makes none.

**Out of scope:** the other items on issue #34 (including the failed action hidden by a queued
success, which needs no restore); Erase blocking the app (the item names the restore only; Erase
runs one short transaction and has no copy step); a restore interrupted by the process being
killed; changing any restore rule from `restore-safety-snapshot`.

## Design (one new mechanism)

**`RestoreInProgress`**, a `@Singleton` in `presentation/backup` (beside `RestoreOutcomeSlot`),
holds how many confirmed restores are running as a `StateFlow<Boolean>` named `active`, with
`begin()` and `end()` that count up and down (`MutableStateFlow.update`), so `active` is true while
the count is above zero. Singleton for the reason `RestoreOutcomeSlot` is: a restore outlives its
screen's ViewModel, and two instances would pass any test that builds its own. A test asserts the
annotation, as `RestoreOutcomeSlotTest` does.

**`BackupViewModel.onRestoreConfirmed`** calls `restoreInProgress.begin()` synchronously, before it
launches (on the main thread, in the same call that sets `isBusy`), and calls `end()` in a `finally`
around `restoreBackup(json)`, immediately after `restoreOutcomeSlot.report(message)`. It does not
wait for the `takenAt` read that follows, which can queue on `PreRestoreCopyLock` behind an Erase and
would keep the screen up for no reason. The existing `NonCancellable` and `UNDISPATCHED` stay.

**`RestoringOverlayHost`**, a composable in `presentation/backup` (file `RestoringOverlay.kt`), is
what `MainActivity` wraps the app in: `RestoringOverlayHost(active = restoring) { RepFlowNavHost() }`,
with `restoring` collected from `restoreInProgress.active` with `collectAsStateWithLifecycle` (the
activity injects `RestoreInProgress` as it already injects `SettingsRepository`). The tests run this
same composable, so there is one wrapper and no copy of it. Placing it in the activity, not in a
screen, is what covers every route, with no change to `RepFlowNavHost` or its tests. No notification
opens the app yet (`setContentIntent` appears nowhere in `app/src/main`); when
`rest-notification-tap` (order 34) adds that, whatever it opens is under this same host.

While `active` is true the host:

- **Draws `RestoringOverlay`**, in a `Box` above the content: the dialog scrim, a new token
  `RepFlowColor.restoringScrim` (`Color(0x9E05060C)`, board 40j's `rgba(5,6,12,.62)`, beside
  `sheetScrim`), and a centred card with the `40g` content: a 22 dp spinner
  (`CircularProgressIndicator`, 3 dp stroke), `Restoring…` (15 sp, medium) and
  `Keep RepFlow open. This takes a moment.` (12.5 sp, secondary). Title and body are one merged
  node in a polite live region.
- **Swallows every touch**, with `pointerInput` consuming all events on the overlay.
- **Takes Back, also after the activity is recreated.** The last back handler registered wins. When
  the flag rises at runtime the overlay composes last and wins. After a recreation (rotation, theme
  or font size; the manifest has no orientation lock and no `configChanges`) the overlay is in the
  first composition, while the `Scaffold` in `RepFlowNavHost` subcomposes `NavHost` during measure,
  so `NavHost`'s handler registers after it, and Back would leave the Backup screen. So the overlay
  has one handler, and registers it again a frame later:

  ```kotlin
  var settled by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { withFrameNanos { }; settled = true }
  key(settled) { BackHandler(true) {} }
  ```

  The handler exists from the overlay's first composition, so a flag rising at runtime leaves no
  gap. Round 2's review ran this form: it held for a flag rising at runtime, `recreate()`, `recreate()`
  then background and return, and a real rotation followed by a real Back key, and dropping
  `key(settled)` failed the three recreate and rotation cases. `RestoringOverlayActivityTest` is the
  test that keeps it that way.
- **Swallows hardware keys**, with `Modifier.onPreviewKeyEvent { active }` on the host, so a Bluetooth
  keyboard, a desktop mode or a Chromebook presses nothing underneath. Round 1's review measured Tab,
  Enter, Space and D-pad centre pressing nothing with it, with focus set before the overlay rose and
  with none.
- **Hides the app underneath from accessibility**: the `Box` child holding the content gets
  `Modifier.clearAndSetSemantics {}` only while `active` is true, so a screen reader cannot reach a
  button the touch layer blocks. This changes the accessibility tree and nothing else: it does not
  stop a keyboard, which is why the key swallowing above is a separate modifier. No state is lost,
  and the screen below is exactly as it was when the overlay lifts.

The Backup screen's own `isBusy` stays as it is (buttons disabled); the overlay is the whole-app
version of the same state. The restore's outcome reaches the Backup screen as before, through
`RestoreOutcomeSlot`, and shows when the overlay lifts. With touch, keys and Back all blocked, also
after a recreation, the user cannot leave the Backup screen from inside the app during a restore;
Home and Recents leave the app, and the screen is still there on the way back. So the slot's
carry-to-the-next-visit case no longer arises in the app; the slot and its tests stay, since removing
them is not needed here.

**Wording (no mechanism):**

- `settings_erase_confirm_body` gains the sentence `Any safety copy RepFlow kept before a restore is
  deleted too.` after the first one, so it is true whether or not a copy exists and needs no new
  state. The rest of the body is unchanged.
- That longer body must not cost the user the way to confirm. In `EraseAllDataDialog` the body and
  the field sit in a `Column` with no scroll, in the dialog's height-limited text slot, so the text
  is measured first and the field gets what is left. Round 2's review measured the new sentence at
  Android's font size 1.8, on a 384x832dp screen: the field gets 0 dp, `It can't be undone.` is cut
  off, and `Erase everything` can never be enabled. So that `Column` gets
  `Modifier.verticalScroll(rememberScrollState())`, the rule 40j draws for the restore dialog ("The
  text scrolls at large font; the buttons stay visible"). It also fixes font size 2.0, which is
  already broken before this item.
- The Backup note has two texts. The existing `backup_restore_note` (it is the design's note,
  verbatim) shows when the phone has training data. A new `backup_restore_note_no_data` shows when it
  has none: `Restoring replaces all data on this phone with the file's contents. Your settings stay.
  This phone has no data to keep, so RepFlow makes no copy first.` `BackupViewModel` reads
  `hasTrainingData` once with the copy's date at start, under `PreRestoreCopyLock` and in the same
  block, and again after a restore ends, in the block that already re-reads the date; the existing
  `BackupUiState.hasTrainingData` field carries it (its default `true` stays, so not knowing shows the
  copy sentence, as the confirmation does). `Export a backup first.` is dropped from the empty-phone
  text because there is nothing to export.

No Room entity, schema or migration change; no new dependency or module; nothing touches the
network. No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided here.

**Wording that is not on the design page, for the reviewer and owner to confirm:** the design
draws the restore note once (`RNOTE`, 40g/40k) and the Erase card text without the safety copy; the
empty-phone note and the Erase sentence are written here from board 40f ("no data to protect, so no
safety card") and 40k ("Erase all data also deletes the safety copy"). The Restoring screen is drawn
on 40g as a card inside the Backup screen with actions disabled; the item asks for it to block the
whole app, so the same card content is centred on a scrim. If the owner prefers other words or a
different layout they change strings and one composable, not the mechanism.

## Requirements

| Id | Requirement |
|---|---|
| R1 | From the moment a restore is confirmed until it ends, whether it succeeds or fails, a Restoring screen covers the whole app and blocks touch, hardware keys, Back, accessibility actions and any way in from a notification, also after the activity is recreated, so no workout action can be started and wait behind the restore. |
| R2 | The Restoring screen shows the design's spinner, `Restoring…` and `Keep RepFlow open. This takes a moment.`, is announced to screen readers, and always lifts when the restore ends, leaving the outcome message as before. |
| R3 | The Erase all data confirmation says it also deletes the safety copy RepFlow keeps before a restore, and at font scales 1.8 and 2.0 the field can still be reached, the word typed and `Erase everything` pressed. |
| R4 | The note on the Backup screen no longer says RepFlow keeps a copy first on a phone with no training data, where none is made. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | A Restoring screen blocks the whole app during a restore (R1, R2) | - | 2 | 1 |
| CP2 | The Erase dialog and the Backup note say what really happens (R3, R4) | - | 1 | 1 |

<!-- CP1 -->
### CP1 — A Restoring screen blocks the whole app during a restore (R1, R2)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/backup/RestoreInProgress.kt` (new),
`app/src/main/kotlin/com/repflow/app/presentation/backup/RestoringOverlay.kt` (new),
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/MainActivity.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/RepFlowColor.kt`,
`app/src/main/res/values/strings.xml`,
`docs/UX_FLOWS.md`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelSafetyCopyTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/RestoreInProgressTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelRestoringTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowPrimitivesTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/RestoringOverlayTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/RestoringOverlayActivityTest.kt` (new).

Work: the design above. `BackupViewModel` gains the `RestoreInProgress` constructor parameter and
the `begin()` / `finally { end() }` pair; the existing tests that build the ViewModel
(`BackupViewModelTest`, `BackupViewModelSafetyCopyTest`) pass a fresh `RestoreInProgress()`.
`MainActivity`'s `restoreInProgress` is an `@Inject lateinit var`, as `settingsRepository` is, and the
activity test reads it. Strings: `backup_restoring_title`, `backup_restoring_body`.

Two texts this makes false change with it:

- `BackupScreen.kt:72-73`: `40g`'s inline invalid-file message stays not drawn, and its `Restoring…`
  card is now `RestoringOverlay`, centred over the whole app.
- `docs/UX_FLOWS.md:46-49`: "A confirmed restore keeps running after the user leaves the screen, and
  its outcome shows at the next visit to Backup. Anything saved on another screen meanwhile waits…"
  becomes: a confirmed restore covers the whole app with a Restoring screen until it ends, with
  nothing to press and no way back, and its outcome shows on Backup when the screen lifts.

Tests (new classes, because `BackupViewModelTest` is large and `detekt`'s `LargeClass` applies):

1. `RestoreInProgressTest`: starts inactive; `begin()` makes `active` true; `end()` makes it false;
   two `begin()` then one `end()` stays true (counted, so a second restore cannot lift the first's
   screen); the class carries `@Singleton`.
2. `BackupViewModelRestoringTest`, on the shared-singleton setup of `BackupViewModelSafetyCopyTest`
   (the store, lock, slot and the new `RestoreInProgress` shared; the eager `Main` dispatcher). The
   fakes are `BackupRepository` wrappers, as its `GatedBackupRepository` is.
   - `the flag is up from the confirmation until the restore ends`: a restore held open on a
     `CompletableDeferred` in the repository; `active` is true right after `onRestoreConfirmed`, and
     false once the deferred completes and the restore finishes.
   - `the flag is already up when the restore first runs`: a fake's `parseSnapshot`, the first thing
     `RestoreBackup` calls, records `restoreInProgress.active.value`, and the test asserts it recorded
     true. Checking `active` after `onRestoreConfirmed` cannot show this: `UNDISPATCHED` and
     `withContext(NonCancellable)` run the body inside the caller, even on a queued test dispatcher.
   - `the flag lifts after a rejected file and after a failed restore`: an invalid file
     (`InvalidBackup`), and a store that fails to save the copy (`Unavailable`): `active` ends false
     and the matching message is in the slot.
   - `the flag lifts when the restore throws`: a fake's `replaceAll` throws `IllegalStateException`;
     the body asserts `active` is false. The exception leaves `viewModelScope` with no handler, and
     `runTest` reports it when the test ends, so the test wraps `runTest` in `assertThrows`. It stays
     inside `runTest`, because an uncaught exception outside it can be carried into the next `runTest`
     in the same JVM (`UncaughtExceptionsBeforeTest`). Round 2's review ran this: it passes on the
     design and fails when the `finally` is dropped.
   - `the flag does not wait for the copy read`: an invalid file, which skips the lock inside the
     restore (a valid one would wait for the lock itself and never reach `report`). Stage it first,
     since staging also takes the lock, then hold `PreRestoreCopyLock` in another coroutine and
     confirm: `active` is false and `InvalidBackup` is in the slot while `isBusy` is still true,
     because `takenAt` has not been read. Release the lock: `isBusy` goes false.
3. `RestoringOverlayTest` (instrumented, Compose): the real `RestoringOverlayHost` around a test
   button with a click counter and a `BackHandler` probe, its flag in a `mutableStateOf`. Each blocking
   check has a control with the flag down, so it can be seen to fail. Back goes through
   `onBackPressedDispatcher.onBackPressed()` here and in the next class, as
   `ActiveWorkoutLeaveRouteTest` does: it is the path a back gesture takes, and a key event could be
   eaten by the key swallowing and hide a dropped handler.
   - shows `Restoring…` and `Keep RepFlow open. This takes a moment.`, and neither once the flag is down;
   - touch: `onRoot().performTouchInput { click(center) }` over a button filling the content leaves the
     counter at 0; with the flag down it counts 1;
   - Back: the probe is not called; with the flag down it is;
   - keys, sent at the window as a keyboard sends them (`Instrumentation.sendKeyDownUpSync`): with no
     prior focus, Tab then Enter, and with focus set by a `FocusRequester` before the flag rose, Enter,
     Space and D-pad centre, all leave the counter at 0; with the flag down, a focused Enter counts 1;
   - the button is absent from the semantics tree while the overlay is up
     (`onNodeWithText(...).assertDoesNotExist()`) and back once the flag is down;
   - the title and body are one merged node with the live region set. This checks a property, not
     speech: the look below checks what a screen reader says.
4. `RestoringOverlayActivityTest` (instrumented, on the real `MainActivity`:
   `createAndroidComposeRule<MainActivity>`, as `MainActivityNavHostSmokeTest` does). Each test walks
   Home, Settings, Backup and calls `composeRule.activity.restoreInProgress.begin()`. The screen is
   checked with `backup_restore_action`, never the title: `backup_title` and `settings_backup_row` are
   the same words, so a title check passes on Settings too. An `@After` calls `end()` for any
   `begin()` the test has not ended, because the singleton outlives the test.
   - the overlay shows and `backup_restore_action` is gone from the tree; Back; `end()`;
     `backup_restore_action` exists (fails if `MainActivity` stops drawing the host);
   - the same with `activityRule.scenario.recreate()` before Back, and the overlay shown again after it
     (R1-I1's failing case);
   - the same with `recreate()`, then `moveToState(CREATED)` and `moveToState(RESUMED)`, before Back;
   - control: no `begin()`, Back, and `settings_backup_row` shows with `backup_restore_action` gone,
     so the check above can tell the two screens apart.
5. `RepFlowPrimitivesTest` (existing class): `theRestoringScrimIsTheDesignsOwnValue`, beside
   `theSheetScrimIsTheDesignsOwnValue` and in its form: red 5, green 6, blue 12, alpha 0.62.

Mutation check (each edit must fail the named test): drop `begin()`; move `begin()` after the
launch; `end()` only on success; drop the `finally`; move `end()` after the `takenAt` read; replace
the counter with a boolean; drop the touch swallowing; drop `BackHandler`; drop `key(settled)`; drop
the key swallowing; drop `clearAndSetSemantics`; make `MainActivity` call `RepFlowNavHost()` with no
host; drop `@Singleton`; change `restoringScrim`'s value.

Look at it in the real app on the AVD (`start-avd.sh`, not the phone), in light and dark and at font
scale 2.0. Restore a large generated file, confirm, and try: Back; a rotation, then Back; Tab and Enter
from a keyboard (`adb shell input keyboard keyevent`); Home, then back from the launcher. The first
frame of the overlay comes after the file is parsed: expect the confirmation to stay up, frozen, for
that long (see the self-review), and record how long it was. With TalkBack
(`com.google.android.marvin.talkback`, installed on the AVD; its developer setting for showing speech
output puts its words on screen), record what it says when the overlay appears. If it says nothing,
the live region appearing as a new node is why: use `paneTitle`, the semantics for a pane appearing,
and change the test above to match. A restore of a small file may end in milliseconds, so these steps
use a file large enough to hold the screen up, or a debug delay that is removed before the commit.
Then restore one small file with no delay, and record how the overlay appears and goes and what
TalkBack says in that time; no code change unless it looks wrong. Turn TalkBack off afterwards. Record
what was seen. A rest-notification tap is not in the look: no notification opens anything until
`rest-notification-tap`.

<!-- /CP1 -->

<!-- CP2 -->
### CP2 — The Erase dialog and the Backup note say what really happens (R3, R4)

**Files:**
`app/src/main/res/values/strings.xml`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupUiState.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/backup/BackupScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/settings/SettingsErase.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/backup/BackupViewModelSafetyCopyTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/settings/SettingsScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/backup/BackupScreenTest.kt`.

Work: the wording design above. `settings_erase_confirm_body` gets its second sentence;
`EraseAllDataDialog`'s `Column` (`SettingsErase.kt:107`) gets the scroll;
`backup_restore_note_no_data` is added; `BackupScreen`'s `RestoreCard` takes the text by
`hasTrainingData`; `BackupViewModel` reads `hasTrainingData` with the copy's date at start and after
a restore (`readHasTrainingData()`, already private there). `BackupUiState.kt:15-16` says it is read
when a restore is staged; it now says all three times.

Tests:

1. `SettingsScreenTest`: the Erase confirmation shows the safety-copy sentence, added to
   `eraseAllDataNeedsTheTypedConfirmation`'s dialog, which keeps passing at font 1.0.
2. `SettingsScreenTest`, the real `EraseAllDataDialog` at font scales 1.8 and 2.0 (one test each),
   built the way `BackupScreenTest.renderTheRealDialogAtFontScale` builds its dialog: the scale goes
   on the context, because a dialog's window takes its density from there. Each test asserts the
   scale reached the dialog's window (`layoutInfo.density.fontScale`) and `Keep my data` is displayed
   when the dialog opens, then scrolls to the field (`performScrollTo`), types the word, checks
   `Erase everything` is displayed and enabled, presses it, and checks `onConfirm` ran once.
3. `BackupScreenTest`: with `hasTrainingData = true` the note is `backup_restore_note`; with `false`
   it is `backup_restore_note_no_data`, and the copy sentence is absent.
4. `BackupViewModelSafetyCopyTest` (existing class, the setup already has `InMemoryTrainingDataRepository`):
   `hasTrainingData` is false on an empty phone and true with data once the start-up read ends; after
   a restore of a file onto an empty phone it becomes true; a failed read leaves it true.

Mutation check: swap the two note texts; drop the post-restore re-read; make a failed read false; drop
the scroll on the Erase dialog's `Column`.

Look at it in the real app on the AVD (`start-avd.sh`, not the phone), with the system font set to 1.8
and then 2.0 (`adb shell settings put system font_scale`). Open `Erase all data`, scroll to the field
with a finger, type the word with the on-screen keyboard up, and check `Erase everything` can be
reached and is enabled. Leave with `Keep my data`, put the font scale back, and record what was seen,
including whether the keyboard covers the buttons. The tests above reach the field through test
actions, with the scale on the dialog's context, so this is what checks a finger and a keyboard.

<!-- /CP2 -->

## Wrap-up (after the checkpoints; not a checkpoint)

Full `spotlessCheck`, `detekt`, `lintDebug`, the JVM suite and `connectedDebugAndroidTest` on the
AVD (`AGENTS.md`). Then the note on issue #34 saying which notes this item closed (the waiting set
lost on leaving the workout, the correction changing the wrong set) and which it only narrows (the
double press, a set refused after Finish), and how the tests showed it. It also records two things
for later: `backup_safety_copy_meta`'s "the next restore replaces it" is false on an empty phone
holding an old copy, and, if CP1's look found it noticeable, a large file's JSON is parsed on the main
thread. Posting it is an outward action: it is done only at that step, with the text shown first.

## Review decisions (round 1)

| Finding | Decision |
|---|---|
| R1-I1, Back after a recreation | Accepted. The design's "Takes Back" bullet, and `RestoringOverlayActivityTest`. `RepFlowNavHost.kt` has `Scaffold` at `:64` and `NavHost` inside its content lambda at `:80`. Not moved to a `Dialog` window, which would get Back and keys from the window system: nothing has measured that here, and the review's probes cover this design. |
| R1-I2, keys under the overlay | Accepted. `onPreviewKeyEvent` on the host, a test with controls, and a mutation. The back arrow is not gated by `isBusy` (`BackupScreen.kt:104-106`). The two sentences saying semantics stop a keyboard are corrected. |
| R1-O1, CP1's Files | Accepted. `BackupViewModelSafetyCopyTest.kt` is listed under CP1 too. |
| R1-O2, stale texts | Accepted. `BackupScreen.kt:72-73` and `docs/UX_FLOWS.md:46-49` in CP1, `BackupUiState.kt:15-16` in CP2. |
| R1-O3, scrim colour | Accepted. Board 40j's `scrim` helper is `rgba(5,6,12,.62)`, read on the design page. A token beside `RepFlowColor.sheetScrim`. |
| R1-O4, JSON parsed in the tap | Accepted as a note, no change. `parseSnapshot` is a plain `fun` (`BackupRepository.kt:19`), first in `RestoreBackup.invoke`. CP1's look expects it, and it goes in the issue note if noticeable. |
| R1-O5, `backup_safety_copy_meta` | Deferred, as the finding allows. True as stated: `PreRestoreCopy.kt` keeps an old copy when there is no training data. It is the design's own text (board 40k's card) in a narrow state, reached by restoring an empty file, and the item keeps to the three things it names. It goes in the issue note. |
| R1-O6, notification tap | Accepted. Removed from the look; the design says what covers `rest-notification-tap` later. `grep setContentIntent` finds nothing in `app/src/main`, and the manifest has only the launcher filter. |
| R1-M1, one wrapper | Accepted. `RestoringOverlayHost`, used by `MainActivity` and the tests, and `RestoringOverlayActivityTest` with `backup_restore_action` and an `@After`. |
| R1-M2, flag before suspension | Accepted. Replaced by a fake that records `active` at its first call. |
| R1-M3, flag on a throw | Accepted, by a third way. `assertThrows` around `runTest` keeps the exception inside a test; the review's "outside `runTest`" could carry it into the next one. `kotlinx-coroutines-test` 1.11.0 ships `UncaughtExceptionsBeforeTest` and `ExceptionCollector`. Not run. |
| R1-M4, flag and the copy read | Accepted. An invalid file, staged before the lock is held, since `onRestoreFilePicked` takes the lock too. |
| R1-M5, announced to screen readers | Accepted. TalkBack in CP1's look, with `paneTitle` if it says nothing. |
| Back through the dispatcher (from R1-I1 and R1-I2 together) | Added here, not in the review: with the key swallowing in place, a Back key event could be eaten and hide a dropped `BackHandler`, so the tests use `onBackPressedDispatcher`. |

## Review decisions (round 2)

| Finding | Decision |
|---|---|
| R2-I1, the Erase field gone at font size 1.8 | Accepted. `SettingsErase.kt:107-115` is a `Column` with no scroll, and CP2 lengthens its text. The design says the same thing twice: 40j ("The text scrolls at large font; the buttons stay visible") and the page's dialog helper ("The dialog text scrolls, the buttons stay"). `docs/DESIGN_SYSTEM.md:17-19` requires large fonts to work. The scroll is in the wording design and in CP2, `SettingsErase.kt` is in CP2's Files, and R3 now says it. The heights in the finding are the review's own; they were not measured again here. |
| R2-O1, no test holds the scrim's value | Accepted. `theRestoringScrimIsTheDesignsOwnValue` beside `theSheetScrimIsTheDesignsOwnValue` (`designsystem/components/RepFlowPrimitivesTest.kt:361`), and a mutation. |
| R2-O2, nobody looks at a small restore | Accepted. One small-file restore in CP1's look, with no code change unless it looks wrong. |
| R2-M1, the Erase dialog at large font | Accepted. Test 2 in CP2, in `SettingsScreenTest`, which already has the `ComponentActivity` rule the font-scale helper needs and is 393 lines long, so no new class. "Drop the scroll" is in CP2's mutation check. |
| Round 2's limit on the Erase probe | Added here, not asked for: a look at the Erase dialog in CP2. The review set the scale on the dialog's context and reached the field through test actions, and the same goes for test 2, so nothing else checks a finger and the on-screen keyboard at these font sizes. |
| The plan's two "not run" notes | Updated from round 2's runs. `key(settled)` held, so its fallback is removed, and `assertThrows` around `runTest` works. Round 1's table above stays as it was written. |

## Self-review notes

- *Does the overlay really stop a set waiting?* It stops every way to start an action from the UI
  after the confirmation. A set pressed in the same frame as the confirmation cannot happen: the
  confirmation is on the Backup screen, and `begin()` runs in the confirming call itself. Anything
  that reaches the workout screen before the confirmation is already finished when the restore
  starts or waits behind it, which `fix-workout-actions-in-order` orders.
- *Can the screen get stuck?* A success or a failure result lifts it, and clearing the screen's
  ViewModel does not matter, since `end()` is inside the `NonCancellable` block and the counter is a
  singleton. A throw has no handler in `viewModelScope`, so in the app it ends the process and the flag
  goes with it; the `finally` is for anywhere a handler exists, and its test shows it runs. A killed
  process loses the state with it. One case is left: `restoreBackup` waiting for `PreRestoreCopyLock`
  behind an Erase holds the overlay until the Erase ends, which is bounded by one short transaction.
  Accepted.
- *Back is blocked.* The user cannot cancel a restore in flight, which was already true (it runs
  `NonCancellable`); now they are told. No Cancel is added: the restore is a transaction and a
  half-cancel is the thing the safety copy exists to avoid.
- *The one frame after a recreation.* Before `settled`, `NavHost`'s handler is on top for one frame,
  the activity's first. A Back landing inside that frame is the only gap left, and it is not one a
  test can hold open. Accepted.
- *A large file.* `parseSnapshot` is a plain `fun` and `onRestoreConfirmed` enters undispatched, so
  the JSON is parsed on the main thread inside the tap, before the overlay's first frame. The modal
  confirmation stays up meanwhile, so no action can slip through; the user sees a frozen dialog and
  then the overlay. This is existing behaviour and this item does not change it.
- *System dialogs and sheets.* Compose `AlertDialog`s are separate windows above the overlay. None can
  be open at the confirmation (the Backup dialog is dismissed by it and no other screen is reachable
  to open one), so the overlay is complete for the app's real paths. A rest alarm or notification
  posting while the overlay is up is outside the app's window and is unchanged.
- *Hardware keyboard and TalkBack.* Each has its own guard. `onPreviewKeyEvent` stops the keys, and
  `clearAndSetSemantics {}` removes the screen below from the accessibility tree, which stops a screen
  reader and does nothing to a keyboard. Whether TalkBack speaks when the overlay appears is what
  CP1's look finds out.
- *Restore note staleness.* The note reads `hasTrainingData` at start and after a restore. The data
  can change in between only from outside this screen, and a stale `true` shows the existing copy
  sentence, which is the safe wording.
- *Layer boundaries.* All new code is in `presentation`. `RestoreInProgress` has no Android type,
  and `LayerBoundaryTest` stays green. No `domain` or `application` change.
- *Usability.* No new setting or screen to learn: the user sees one centred card for as long as the
  restore takes, then the usual `Backup restored` or error message. Wording differences from the
  design are listed in the design section for the owner to confirm.
- *The Erase dialog at large font.* The scroll works at any dialog height, so a shorter phone, which
  round 2 did not measure, is covered too. Other dialogs at large font were not checked, and this item
  does not change them: only the Erase dialog's text gets longer.
- *Size.* One mechanism (the flag with its overlay). The two wording changes are string edits plus
  one existing state field and one scroll modifier. Two checkpoints, no dependency between them.
