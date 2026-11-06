# Active Milestone

## `backup-reminder` — complete (accepted by policy 2026-11-06)

Home reminds you when your last backup is old, with a Settings switch to turn it off. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/backup-reminder-execution.md` and the Optional findings at `docs/milestones/completed/backup-reminder-findings.md`.

**Next action:** `timed-entry-mmss`, Timed sets use m:ss everywhere, and the shared number pad opens on the current value. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

A quiet line on Home when the last backup is old, and a `Backup reminder` switch in Settings that turns it off. Plan: `docs/milestones/backup-reminder-execution.md`.

- **CP1, complete.** Settings now has a `Backup reminder` switch under `Your data`, right after `Backup and restore`, on by default and with the meta `Shows a note on Home when your last backup is 21 or more days old`. It is stored in the settings row: `AppSettings.backupReminder`, the `backup_reminder` column and `MIGRATION_9_10`, which adds that one column with `DEFAULT 1`, so every existing install gets it on and no row is rewritten. `RepFlowDatabase.VERSION` is 10 and `10.json` is exported. It's a device setting, so it isn't in `BackupSnapshot` and no transfer-schema change is needed. Nothing reads the switch yet; Home does in CP2. No new dependency. Verified: 20 JVM tests pass (`SettingsViewModelTest`, `LayerBoundaryTest`), 55 instrumented tests on `emulator-5554` (`RepFlowDatabaseMigrationTest`, `SettingsPersistenceTest`, `SettingsScreenTest`), `spotlessCheck`, `detekt`. Mutations: all 4 edits in the plan's list were caught by the named tests. A migration default of 0 fails `migrate9To10_turnsTheReminderOnAndChangesNothingElse`, the full chain and the production-builder test; an entity default of 0 fails 3 `SettingsPersistenceTest` cases, including the fresh-database read; `withToggle` returning `this` fails `the backup reminder switch writes only itself` and `each switch writes only its own preference`; the row drawn before `Backup and restore` fails `yourDataHoldsLibraryArchivedBackupAndThenTheEraseCard`. They ran before the formatter and one added assertion, neither of which changes production code. The full runs are still to come, in the wrap-up.

  Looked at in the real app on the AVD, light theme at font scale 1.0. With 1 exercise, 1 finished workout, 1 set and `Keep the screen on` turned on, its database file read schema 9. After installing over it and opening the app, the file read schema 10 with the same 3 rows, `keep_screen_awake = 1` and `backup_reminder = 1`, and Settings drew the switch on. Pressing it stored 0, and after a force-stop and relaunch the switch still drew off; pressing it again stored 1. The meta wraps to two lines on this 384 dp screen and nothing is clipped.

  Changes from the plan, none to the rules: (1) that look installed over the build already on the AVD, installed 2026-11-05 23:19, before #38 merged, and not over a fresh build from `main`; its database was schema 9, so the migration really ran. (2) `fullChainFrom1To9OpensThroughAllMigrations` is renamed `fullChainFrom1ToTheCurrentVersionOpensThroughAllMigrations`, because the chain now ends at 10. Beyond the plan's tests: `theBackupReminderShowsAStoredOff`, and one assertion in `theProductionBuilderUpgradesARealVersion8DatabaseToTheNewDefaults`.

- **CP2, complete.** Home now shows a quiet line under `Last workout` when the last backup is 21 or more calendar days old: `Last backup N days ago` and a `Back up` link that opens Backup and restore. With no backup at all it says `No backup yet`, starting 21 days after the first finished workout. The rule is one pure function, `backupReminderOf` in `HomeBackupReminder.kt`, and gives no line with the switch off, during a workout, or on a phone with no training data. `RecentTraining` gains `firstWorkoutEndedAt`, computed from the sessions it already reads, so there is no new query. `HomeViewModel` reads the settings and re-reads `hasTrainingData` when the settings, the sessions or `today` change. A failed settings or history read shows no line, and a failed `hasTrainingData` read counts as having data. Above font scale 1.1 the link goes on its own line, as `10n` draws it. `ic_ph_floppy_disk` is copied from Phosphor's 2.1.1 SVG, the same source whose `database` glyph matches the repo's byte for byte. No schema change and no new dependency. Verified: 79 JVM tests pass (the `presentation/home` and `presentation/settings` packages, `ObserveRecentTrainingTest`, `RepFlowIconsTest`, `LayerBoundaryTest`), 43 instrumented tests on `emulator-5554` from a clean install (`HomeScreenTest`, `HomeRouteLifecycleTest`, `ActiveWorkoutLeaveRouteTest`, `MainActivityNavHostSmokeTest`), `spotlessCheck`, `detekt`. Mutations: all of the plan's list was caught by the named tests, with the boundary tried both ways (21 to 20 and to 22), and so were 8 more: the ViewModel ignoring the running workout, a failed settings read using the defaults, a failed history read still applying the rule, the newest workout counted as the first, an invalidated workout counted as the first, a swallowed cancellation, and the click left unwired at the route and at the nav host. Production code hasn't changed since they ran; one test assertion was added after. The full runs are still to come, in the wrap-up.

  Looked at in the real app on the AVD, with 1 exercise and 1 finished workout with 1 set, made through the app. Its times and `last_backup_at` were then aged with `sqlite3` while the app was stopped; the device date wasn't changed. Just finished, with no backup: no line. No backup, first workout 20 days ago: no line; 21 days: `No backup yet`. A backup 20 days ago: no line; 21 days: `Last backup 21 days ago`; 40 days: `Last backup 40 days ago`, and Backup's hero says the same. In light and dark at font scale 1.0 all three share a row, and at 1.3 and 2.0 the link takes its own line at the start, for `Last backup 21 days ago` and for `No backup yet` at 2.0. Nothing is clipped, and at 2.0 the line sits clear of the bottom navigation: the link's text ends at y 1939 and the bar starts at 1980. `Back up` opens Backup with no bottom navigation, and the real Back returns Home with the line still there. A real export through the file picker showed `Saved` and `Last backup today`, stored `last_backup_at`, and the line was gone on Home. The switch off stored 0 and the line went; on stored 1 and it came back. `Leave it running and go Home` showed the resume card and no line, and abandoning that workout brought the line back. A real Erase left 0 sessions and 0 exercises and kept `last_backup_at`, and Home showed the first-run card with no line. Seen and left as it is, because this item doesn't touch it: at font scale 2.0 the bottom bar's `Progress` label is cut to `Progr…`. Board `10a` says nav labels stop growing at 1.3× so this can't happen, and `RepFlowBottomNavigationBar` has no such cap. It isn't on the backlog yet.

  Changes from the plan, none to the rules: (1) `HomeViewModel` feeds `recentTraining` through the one flow that also reads `hasTrainingData`, not into a second subscription, so the sessions are read once. The `Last workout` card therefore waits for that short read as well. (2) `RepFlowDestinations.kt` also said Backup is reached only from Settings, so its KDoc is corrected too. (3) The link's colour is `repFlowAccentOutlineColors(...).label`, the one `Details ›` and `Log ›` use, as the plan says. In dark that is `accent300`, a step lighter than the board's `T.acc`. (4) `MainActivityNavHostSmokeTest` gains one private helper, `abandonTheWorkoutThroughItsX`, shared with the workout walk. Beyond the plan's tests: the dates use the zone's own calendar, Home and Backup count the same days, a backup date in the future shows nothing, a phone with no training data gets the line once a finished workout makes the re-read find data, and the link is at least 44 dp at every font scale tried.

- **Implementation review, first round: one fix.** The local review approved. The external review found I1: with an old backup and no finished workout, an Erase followed by a return inside the 5-second subscription window left the line on an emptied phone, because `hasTrainingData` was re-read only when the settings, sessions or day changed, and that Erase changes none of them. Reproduced before the fix by `HomeViewModelTest` and, over real Room on `emulator-5554`, by `HomeRouteLifecycleTest.anEraseInsideTheWindowTakesTheBackupLineAwayOnReturn`. The window starts on leaving Home, so a person has to open Settings, type `ERASE` and come back inside it. It is narrow, but R2 says never, and no person was timed doing it.

  The fix, commit `4272dcd`: Home observes the answer through the new `TrainingDataRepository.observeHasTrainingData`, one `TrainingDataDao` query shared by the read and the flow. A failed read still counts as having data. A flow that failed stays at that answer until Home's flows restart, where the old read would have tried again on the next change. No schema change and no new dependency.

  Changes from the plan, none to the rules: (1) this replaces the plan's one-shot read, and its accepted staleness, a first exercise on an emptied phone waiting for another change, no longer exists; (2) the `Last workout` card no longer waits behind a `mapLatest`, and it still waits for the first answer, through the `combine`; (3) the plan's file isn't edited, because it is the approved plan. The line now reaches a new day's state one emission before the header's date, where it used to arrive after it, so `a backup 20 days old gets the line on the next day` waits for the settled state. A trace showed the two states and no third. Commit `6084cc9` adds `HomeScreenTest.theLineComesUnderLastWorkout`, the local review's O2. Its O1 asked for a case that catches a read stuck on `true`: the new regression does, and its other mutation, no re-read on a settings change, no longer applies, because there is no re-read.

  Mutations, each caught by the named test after the one survivor below was dealt with: the answer stuck on `true` (the regression), only the first answer read (the regression and the first-data case), a failed read hiding the line (`a failed read of the training data still gives the line`), a swallowed cancellation (`a cancelled read`), `distinctUntilChanged` removed (`observingSaysNothingNewWhenTheAnswerStaysTheSame`), `recovery_entries` left out of the query (both `LocalTrainingDataRepositoryTest` table tests), and the line moved above the card (`theLineComesUnderLastWorkout`). `distinctUntilChanged` survived the first version of its test, because Room coalesced the insert and the clear that followed it, so the test now waits and expects nothing. That wait is 500 ms of real time, so a slow device could let this mutation through, but correct code can't fail it. The `recovery_entries` mutation stayed in place by mistake during the line-move run, and the line-move test still failed for its own reason. Verified on `6084cc9`: `spotlessCheck`, `detekt`, `lintDebug`, 875 JVM tests, and 524 instrumented tests on `emulator-5554` from a clean install, 0 failures. The real app wasn't looked at again in this round.

**Next action:** perform the functional review below, write findings to `.ai-review/backup-reminder/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification: production code last changed in `4272dcd` (implementation review round 1, I1); `6084cc9` added a test only, and later commits are workflow records. The last full run on `6084cc9` is in the round 1 bullet above: `spotlessCheck`, `detekt`, `lintDebug`, 875 JVM tests, 524 instrumented tests on `emulator-5554`, 0 failures.

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug` (an install over an older build also exercises the schema 9 to 10 migration). No feature flags, no network. The reminder needs old dates: make data through the app, then with the app stopped age `sessions` times and `last_backup_at` with `sqlite3` (the device date is not changed). Close the app before editing the database.

**Test data.** 1 exercise and 1 finished workout with 1 set. Case table for the aging: (a) no backup, first workout 20 days ago; (b) the same, 21 days; (c) backup 20 days ago; (d) backup 21 days ago; (e) backup 40 days ago.

**F1 - The switch (R1).** Settings > `Your data`. Expected: `Backup reminder` sits right after `Backup and restore`, is on by default, and its meta reads `Shows a note on Home when your last backup is 21 or more days old`. Turn it off, force-stop, relaunch: it still draws off. Turn it on again.

**F2 - When the line shows (R2).** With the switch on, check each case on Home. Expected: just finished with no backup: no line. (a) no line. (b) `No backup yet` under `Last workout`. (c) no line. (d) `Last backup 21 days ago`. (e) `Last backup 40 days ago`, and Backup's hero says the same.

**F3 - The link (R3).** Press `Back up` on the line. Expected: Backup opens with no bottom navigation; the real Back returns to Home with the line still there.

**F4 - A backup clears it (R4).** Export a real backup through the file picker. Expected: `Saved` and `Last backup today` on Backup, and the line is gone on Home.

**F5 - Switch and running workout (R5).** With an old backup, turn the switch off: the line goes. On: it returns. Start a workout and `Leave it running and go Home`: the resume card shows and no line. Abandon that workout: the line returns.

**F6 - Erase (R6, the review fix).** With an old backup, Settings > Erase all data, type `ERASE`, confirm, and come back to Home quickly. Expected: the first-run card and no line, even when you return inside a few seconds. `last_backup_at` is kept. Add one exercise and finish a workout: the line comes back once the dates qualify.

**F7 - Look (R7).** Light and dark at font scale 1.0, then 1.3 and 2.0. Expected: at 1.0 the text and link share a row; above 1.1 the link takes its own line at the start; nothing clipped; the line sits under `Last workout` and clear of the bottom navigation; the link is easy to press. Restore the font to 1.0 afterwards.

**Known limitations / out of scope.**
- The device date is not changed; ages come from edited database times.
- At font scale 2.0 the bottom bar's `Progress` label is cut to `Progr…`. It is not part of this item and is not yet on the backlog.
- `No backup yet` counts from the first finished workout, so a phone with no workout never shows a line.
- A failed read of the settings or history shows no line; a failed training-data read counts as having data.
- The real app was not looked at again after the review fix; F6 is the check for it.
- The full wrap-up runs come at acceptance.

## `safety-copy-polish` — complete (accepted by policy 2026-11-05)

A Restoring screen blocks the app during a restore, and the Erase and Backup wording says what really happens. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/safety-copy-polish-execution.md` and the Optional findings at `docs/milestones/completed/safety-copy-polish-findings.md`.

**Next action:** `backup-reminder`, A reminder on Home when the last backup is old. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

A Restoring screen blocks the whole app during a restore, and the Erase and Backup wording says what really happens. Plan: `docs/milestones/safety-copy-polish-execution.md`.

- **CP1, complete.** From the moment a restore is confirmed until it ends, whether it succeeds or fails, a Restoring screen covers every route. `RestoreInProgress`, a counted `@Singleton`, rises in the confirming call itself and falls in a `finally` right after `RestoreOutcomeSlot.report`, so it doesn't wait for the safety copy's date. `MainActivity` wraps the app in `RestoringOverlayHost`, which draws `40g`'s card on the `restoringScrim` token and has one guard per way in: touch is consumed, Back has a handler that registers again a frame later so it still wins after a recreation, hardware keys are swallowed by `onPreviewKeyEvent`, and the app underneath leaves the semantics tree. So no workout action can be started and wait behind a restore. The outcome reaches Backup through the slot as before. `BackupScreen`'s KDoc and `docs/UX_FLOWS.md` are corrected. No schema change and no new dependency. Verified: 110 JVM tests pass (the backup package, including the new `RestoreInProgressTest` and `BackupViewModelRestoringTest`, `RepFlowPrimitivesTest` and `LayerBoundaryTest`), 44 instrumented tests in `presentation/backup` on `emulator-5554` (including the new `RestoringOverlayTest` and `RestoringOverlayActivityTest`), `MainActivityNavHostSmokeTest` (10 pass, on a fresh install), `spotlessCheck`, `detekt`. Mutations: all 14 edits in the plan's table were caught by the named tests. They ran before change (2) below, a layout-only line. The full runs and the note on issue #34 are still to come, in the wrap-up.

  Looked at in the real app on the AVD, with a generated 14 MB file of 30,000 exercises, which holds the screen up for about 5 s. No debug delay was used. The confirmation froze for about 0.7 s on the first restore, while the JSON is parsed in the tap (`Skipped 40 frames`), and for less than the 0.5 s that log line needs on the second, the only later one checked. A real backup is far smaller, so this isn't noticeable in normal use. The real Back key did nothing. Tab, Enter, Space and D-pad centre from `adb shell input keyboard keyevent` pressed nothing. A rotation then Back stayed on Backup. Home left the app, and on return the overlay was still up, Back did nothing, and the outcome then showed on Backup. Light and dark at font scale 1.0, and both at 2.0, draw with nothing clipped. Two things seen and left as they are: Tab with no prior focus still moves the focus highlight to the back arrow underneath, which shows once the overlay lifts, and nothing can be pressed from there (`tabThenEnterPressNothingWithNoPriorFocus`). And TalkBack, with its focus on the card, says `Restoring…` at 1.4 s and `Keep RepFlow open. This takes a moment.` at 2.4 s, then says both again just after the overlay lifts (6.1 s, on a restore that ended at 5.8 s), then `Alert`, `Backup restored` at 9.7 s. The repeat looks like TalkBack reading the card again when its focus lands there after the dialog closes; that isn't proven. Since it does speak, `paneTitle` isn't needed. A small restore with no delay (3 exercises over 3): the overlay lasts under 100 ms beneath the fading dialog, as a slightly darker dim with no flash of its own, then `Backup restored`. TalkBack says only `Backup restored`, `Alert` and the back button, which is right, so no code change. The same small file over 30,000 rows holds the overlay for about 2.4 s, because of the copy, and TalkBack says both lines during it.

  Changes from the plan, none to the rules: (1) `RestoreInProgress` keeps its count in a plain `Int` under `@Synchronized`, not `MutableStateFlow.update`, because the count and the flag are two values that have to change as one; (2) the card sits inside `safeDrawingPadding()`, because the look found it running under the 3-button navigation bar in landscape. The scrim and the touch swallowing still fill the screen. No test holds this line, only the look; (3) the card is `RepFlowCard` with its 16dp radius, not `40g`'s 14dp, and the spinner's arc is the theme's `primary`, not the design's accent step, which has no theme slot. One test beyond the plan: `aFlagRisingAtRuntimeTakesBackAtOnce`, for the case the overlay's first handler exists for.

- **CP2, complete.** The Erase confirmation now says `Any safety copy RepFlow kept before a restore is deleted too.`, and its text scrolls, so at font sizes 1.8 and 2.0 the field can be reached, the word typed and `Erase everything` pressed. The Backup note has two texts: a phone with no training data gets the new `backup_restore_note_no_data`, which promises no copy, and any other phone keeps the design's note. `BackupViewModel` reads `hasTrainingData` with the copy's date, in one block under `PreRestoreCopyLock`, when the screen opens and after a restore ends, as well as when a restore is staged. A failed read counts as having data, so the copy wording is never dropped silently. No schema change and no new dependency. Verified: 95 JVM tests pass (the backup and settings packages and `LayerBoundaryTest`, including 4 new `BackupViewModelSafetyCopyTest` cases), on `emulator-5554` 46 instrumented tests in `presentation/backup` and 25 in `SettingsScreenTest`, including the new note and font-scale tests, `MainActivityNavHostSmokeTest` (10 pass, on a fresh install), `spotlessCheck`, `detekt`. Mutations: all 4 edits in the plan's list were caught by the named tests, and so were 3 more: dropping the start-up read, reading the flag outside the lock, and removing the Erase sentence. Without the scroll, both font-scale tests fail. The full runs and the note on issue #34 are still to come, in the wrap-up.

  Looked at in the real app on the AVD, with the system font at 1.8, 2.0 and then back at 1.0. At 1.8 and 2.0 the dialog opens with `Keep my data` and a greyed `Erase everything` showing and the text cut off part way down; a finger swipe reaches the field. **The on-screen keyboard covers both buttons at 1.8 and 2.0**: its top is at y 1488, and `Erase everything` sits at 1564-1668 at 1.8 and 1559-1671 at 2.0. The keyboard's ✓ (tried at 1.8) and Back (tried at 2.0) each close it and keep the word, and `Erase everything` then shows enabled, so it can be pressed but takes that one extra step. Both looks left with `Keep my data`, as the plan says. At 1.0 everything sits above the keyboard (`Erase everything` at 1179-1241), and there the word was typed and `Erase everything` pressed for real: `All data erased`. Seen and left as it is: at 1.8 and 2.0 the field's label wraps to two lines, and once the field has focus or text the bottom edge of its outline is cut off, though the word stays readable. Gboard's own `Keyboard font size updated` ribbon also raises the keyboard once after a font change; that isn't the app. The note in the real app: an empty phone shows the no-copy text, one exercise brings the copy text, a real Erase brings the no-copy text back, and restoring a file onto that empty phone, staying on the screen, shows `Backup restored` and the copy text.

  Changes from the plan, none to the rules: one private helper, `refreshCopyAndTrainingData`, holds the read the two places share. One test beyond the plan: `a screen reopened during a restore reads the flag only when that restore ends`, for the lock.

- **Implementation review round 1, fixes applied.** The external review found that Back could leave Backup right after a recreation, while the restore still ran (I1). The overlay's handler got back on top of `NavHost`'s by registering again a frame later, so a Back before that frame popped Backup and the outcome waited for the next visit. The handler now registers once, at `NavigationEventDispatcher.PRIORITY_OVERLAY`, and nothing waits for a frame. `BackHandler` registers at the default priority and `NavHost`'s handler goes through the same dispatcher, which tries overlay handlers first, whichever registered last. This was read from the bytecode of activity-compose 1.13.0, navigation-compose 2.9.8 and navigationevent 1.0.0, not only measured. Those types are already on the compile classpath through activity-compose, so no build file changed. Verified on the tree that became `879671f`: `spotlessCheck`, `detekt`, `lintDebug`, 848 JVM tests and 503 instrumented tests on `emulator-5554`, all passing. Before the fix, 3 of the new tests failed on the AVD: `backStaysOnBackupBeforeTheNextFrameAfterARecreationAndLeavesOnceTheRestoreEnds` and `aRestoresOutcomeStillShowsOnBackupAfterABackBeforeTheNextFrame`, each when `backup_restore_action` was gone once the overlay lifted, and `backReachesNothingThroughAHandlerRegisteredAfterTheOverlay` with `expected:<0> but was:<1>`. The first two stop the test clock across a real recreation, which holds the old gap open. The outcome test runs a real rejected restore through the real screen, so no data changes. Mutations: 4 edits, each caught: the default priority (5 tests fail), no handler (9), never removing it (3), and leaving out `onBackCompleted`, whose default throws (9). The plan's `key(settled)` mutation no longer applies, because that code is gone.

  Looked at in the real app on the AVD with a generated file of 70,000 exercises, which holds the screen up for about 8 s, and real keys from `adb shell input`, sent while the screen's pixels showed the overlay. Back: the overlay stayed, then Backup with `Backup restored`. Home, return, Back: the same. Once the restore had ended, a real Back left Backup for Settings as it should; three early runs with smaller files landed there by accident, because the key came late. A burst of 292 to 812 real Back keys across a real rotation stayed on Backup in all 4 runs, and the one run that checked in time saw `Backup restored`. **That burst is not evidence that the gap is closed: the old handler also stayed on Backup in all 3 runs of the same burst**, so real keys don't reach that one frame on this AVD. The bursts show the new handler works through the real `OnBackInvokedCallback`. The gap itself is held by the stopped-clock tests and the library's own order. Only API 36 was run; below API 33 Back goes through `onBackPressed()`, the path the tests use.

  Optional findings from the local review: O1's test is added (`backStaysOnBackupAfterReturnsFromTheBackgroundWithNoRecreation`) and passed before the fix too, so it holds behavior rather than fixing it. O3's literals are now tokens, each with the same value. O2 (the Erase field's outline at font 2.0) is left for `theme-text-levels`, as the finding suggests: that item already changes this outline, and the word stays readable. Change from the plan, none to the rules: the plan's `key(settled)` block is replaced by the above, and R1 is unchanged. The approved plan text is pinned, so it still shows the old block.

**Next action:** perform the functional review below, write findings to `.ai-review/safety-copy-polish/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification: the production code has not changed since `879671f` (implementation review round 1, I1); later commits are workflow records only. The last full run on that tree is in the round 1 bullet above: `spotlessCheck`, `detekt`, `lintDebug`, 848 JVM tests, 503 instrumented tests on `emulator-5554`, all passing.

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. No feature flags, no network needed.

**Test data.** A small backup file exported from a phone with a few exercises and one finished workout (Backup > Export). For F1, a large file makes the Restoring screen last long enough to look at (e.g. several thousand exercises; about 5 s at 30,000). Without one, F1 can only be seen as a brief flash.

**F1 - The Restoring screen (R1, R2).** Backup > Restore, pick the large file, confirm. Expected: a dimmed full-screen card with a spinner, `Restoring…` and `Keep RepFlow open. This takes a moment.`; touching anywhere and pressing Back do nothing; when it ends the card lifts and Backup shows `Backup restored`. With the small file the card only flashes under the fading dialog, with no error.

**F2 - Back and rotation during a restore (R1).** Start the large restore, rotate the AVD, press Back. Expected: still on Backup; after the restore ends the `Backup restored` message shows, and a later Back leaves Backup normally. Same after Home then return.

**F3 - Look at font scale and theme (R2).** Open the Restoring screen in light and dark, at font scale 1.0 and 2.0. Expected: nothing clipped; in landscape the card clears the navigation bar.

**F4 - Erase wording (R3).** Settings > Erase all data. Expected: the text includes `Any safety copy RepFlow kept before a restore is deleted too.`, and the other sentences are unchanged.

**F5 - Erase at large font (R3).** Set the system font to 1.8 and then 2.0, open the Erase dialog. Expected: the text scrolls; swipe to reach the field, type the word, press `Erase everything` (it enables). Cancel with `Keep my data`, or erase on the AVD only. Restore font to 1.0 afterwards.

**F6 - Backup note on an empty phone (R4).** With no training data (after an Erase), open Backup. Expected: the note says `This phone has no data to keep, so RepFlow makes no copy first.` and does not promise a copy.

**F7 - Backup note with data (R4).** Add one exercise, reopen Backup. Expected: the note says RepFlow keeps a copy of the current data first. Restore a file onto an empty phone and stay on Backup: after `Backup restored` the note switches to the copy text.

**Known limitations / out of scope.**
- The confirmation can freeze for about 0.7 s on a very large file, because the JSON is parsed in the tap; a real backup is far smaller.
- At fonts 1.8 and 2.0 the on-screen keyboard covers both Erase buttons; closing it (the keyboard's ✓ or Back) keeps the word and lets `Erase everything` be pressed. The field's outline is also cut at the bottom once focused; both are left for `theme-text-levels`.
- Tab with no prior focus can move the highlight to the back arrow under the overlay; nothing can be pressed from there.
- TalkBack may read the Restoring card twice around the moment it lifts; not proven to be a defect.
- `backup_safety_copy_meta` saying "the next restore replaces it" is wrong on an empty phone holding an old copy; noted for the issue #34 wrap-up, not changed here.
- The full wrap-up runs and the note on issue #34 come at acceptance, not now.

## `fix-workout-actions-in-order` — complete (accepted by policy 2026-11-05)

Workout actions now run one after another, so a set logged during a restore can't be lost. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/fix-workout-actions-in-order-execution.md` and the Optional findings at `docs/milestones/completed/fix-workout-actions-in-order-findings.md`.

**Next action:** `safety-copy-polish`, Safety copy: a Restoring screen, and the Erase and Backup wording says what really happens. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

Workout actions run one after another, so a set logged during a restore can't be lost. Plan: `docs/milestones/fix-workout-actions-in-order-execution.md`.

- **CP1, complete.** Workout actions now run one after another, in the order they were pressed. One `Mutex`, `actionLock`, in `ActiveWorkoutViewModel` covers `launchAction`, `onCompleteWorkout` and `onAbandonWorkout`, around the use case and the handling of its result. So during a restore a set followed by another set, Finish or Abandon keeps every effect, and an earlier failure can't overwrite a later success. No schema change and no new dependency. Verified: `ActiveWorkoutActionOrderTest` (4 tests pass), the `presentation/workout` JVM package and `LayerBoundaryTest` (113 tests pass), `spotlessCheck`, `detekt`. Before the fix the four new tests failed as the plan measured: `expected:<[8, 9]> but was:<[9]>`, `expected:<1> but was:<0>` twice, and `expected null, but was:<PERSISTENCE_UNAVAILABLE>`. Mutations: removing the lock from `launchAction` fails all four, from `onCompleteWorkout` the Finish test, and from `onAbandonWorkout` the Abandon test, as in the plan's table. One assertion beyond the plan: the Finish test also checks that there is no error. The full runs and the note on issue #34 are still to come, in the wrap-up.

**Next action:** perform the functional review below, write findings to `.ai-review/fix-workout-actions-in-order/feedback/FUNCTIONAL_REVIEW.md`, or run `/accept-milestone` if testing is clean.

### Functional review checklist

Automated verification: the code has not changed since CP1 (`98bc58b`); later commits are workflow records only. CP1's targeted runs are listed above (`ActiveWorkoutActionOrderTest`, the `presentation/workout` JVM package, `LayerBoundaryTest`, `spotlessCheck`, `detekt`). The full JVM, `lintDebug` and `connectedDebugAndroidTest` runs are not recorded yet; they belong to the wrap-up.

**Setup.** Use the AVD (`start-avd.sh`, 3 GB cap), not the phone. Install with `./gradlew installDebug`. No feature flags, no network needed.

**Honest scope note.** The bug is a race: two workout actions overlapping while a restore holds writes. A restore of a small file ends in milliseconds, so it cannot be produced reliably from the UI. It is proven by the four `ActiveWorkoutActionOrderTest` cases and the mutation table above. The flows below are regression checks that normal workout actions still run, in press order, with nothing visibly changed.

**Test data.** Two exercises (e.g. Bench Press, Squat) and one finished workout, so seeds and history exist. For F6, a backup file exported from this data.

**F1 - Sets log in press order (R1).** Start a workout, add Bench Press, log three sets with different reps (e.g. 8, 9, 10), tapping `Log set` as fast as you can. Expected: three sets, in that order, with those reps; no error banner.

**F2 - A set then Finish keeps the set (R1).** Log a set and tap Finish straight away. Expected: the workout completes and History shows it with that set.

**F3 - A set then Abandon (R1).** Start a workout, log a set, then abandon at once. Expected: no error, back on Home, the workout is not in History.

**F4 - Edit, delete and Undo in a row (R1, R2).** Log a set, edit its reps, delete it, tap Undo. Expected: the final state matches the last action pressed; no error banner appears or lingers.

**F5 - Rest timer controls (R1).** Log a set so the rest timer starts, then tap add time, remove time and skip in quick succession. Expected: the timer ends in the state of the last tap; no error; the saved set stays.

**F6 - Actions during a restore (R1), best effort.** On the Backup screen start restoring the file; as soon as you confirm, go back to the workout and log two sets. Expected: both sets are kept if the restored data still holds that workout. Timing is tight; if the restore ends first, note it and move on.

**Known limitations / out of scope.**
- The race cannot be triggered reliably from the UI, so the fix is covered by the automated tests (F6 is best effort).
- Home's resume-card abandon is not under this lock (it doesn't normally overlap).
- A double press during a restore now logs two identical sets (R1 asks for it; Undo corrects it).
- A set still waiting when the user leaves the workout is lost with no message, as before. It goes on issue #34 at wrap-up.
- Other items on issue #34 are not part of this item.

## `restore-safety-snapshot` — complete (accepted by policy 2026-11-05)

RepFlow now keeps a safety copy of your data before a restore, and you can restore that copy from the Backup screen. Accepted by the gate policy, not by a person; the plan is archived at `docs/milestones/completed/restore-safety-snapshot-execution.md` and the Optional findings at `docs/milestones/completed/restore-safety-snapshot-findings.md`.

**Next action:** `backup-reminder`, A reminder on Home when the last backup is old. Ready for `/milestone-plan`.

The sections below are this milestone's historical record.

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
