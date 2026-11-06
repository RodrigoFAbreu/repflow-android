# backup-reminder — Execution Plan (Revision 2)

- Work item: `backup-reminder` (backlog order 25, size 1, `docs/items/backup-reminder.md`)
- Governing workflow version: `2.2`
- Base commit: `31561878d68af71c0304925fc9f466d0ef4a8e7a`
- Plan revision: 2

Pull request title: `feat: remind on Home when the last backup is old, with a Backup reminder switch in Settings`

## Goal and scope

Don't lose data, don't hide failures. A lost phone loses everything since the last manual export,
and nothing today says the last export is old. Home gets one quiet line at the bottom when the last
backup is 21 or more days old, with a `Back up ›` link to Backup and restore, and Settings gets a
`Backup reminder` switch (on by default) that turns the line off. The design is boards `10n`
("Last backup 21 days ago"), `10o` ("No backup yet") and `40e` (the switch), recorded as N5 in `10z`.
The owner's decisions of 2026-11-05 are binding: 21 days, never-backed-up users get it from 21 days
after their first finished workout, and the switch is on by default.

**Out of scope:** any notification or system reminder (the line is on Home only; no permission, so
the open row "Notification behavior when permission is denied" is not touched); a configurable
interval; changing what counts as a backup (`lastBackupAt` stays "a backup export succeeded", CSV
never changes it); the safety copy; a banner or dialog that blocks anything.

## Design (one new mechanism: the reminder rule)

**The rule**, one pure function `backupReminderOf(...)` in `presentation/home` (file
`HomeBackupReminder.kt`), so it is tested without Compose. It returns `HomeBackupReminder?`, a sealed
type with `LastBackup(days: Int)` and `NoBackupYet`, and returns `null` (no line) unless **all** of:

1. the `Backup reminder` switch is on;
2. no workout is running (board `10n`: "does not appear while a workout is running");
3. the phone has training data (board `10n`: Erase keeps `lastBackupAt`, so an emptied phone would
   otherwise show an old date on the first-run Home);
4. and either
   - `lastBackupAt` is set and the calendar-day gap to today, in the device zone, is **21 or more**
     → `LastBackup(days)`. The gap uses the same calendar-day count as `lastBackupLabel`
     (`LastBackupLabel.kt`), so Home and Backup never disagree about "N days ago"; or
   - `lastBackupAt` is `null` and the first finished workout ended 21 or more calendar days ago
     → `NoBackupYet`. A phone with no finished workout yet is never nagged.

"Finished workout" is a completed, non-invalidated session, the set `ObserveRecentTraining` already
reads (`observeCompletedSessions(includeInvalidated = false)`). Its oldest `endedAt` is added to
`RecentTraining` as `firstWorkoutEndedAt: Instant?`, computed in `recentTrainingOf` beside the fields
it already derives. No new repository method and no new query.

**Reading it.** `HomeViewModel` gains `SettingsRepository` (`observe()`) and `TrainingDataRepository`
(`hasTrainingData()`, a one-shot read) as constructor arguments, and `HomeUiState` gains
`backupReminder: HomeBackupReminder? = null`. The read of `hasTrainingData` runs when the settings,
the completed sessions or the day change, which are the inputs that can change the answer; a
`Backup` export writes `lastBackupAt`, so the line goes away the moment a backup succeeds, with no
extra signal. Failure rules, each "say less, never claim wrong": a failed settings or history read
shows no line; a failed `hasTrainingData` read counts as having data (the same choice
`BackupViewModel` makes), so a read failure can show a reminder but never hide one that is due. A
failed read of the running workout counts as no workout, because `HomeViewModel.activeSession` already
emits `null` on failure, so the line could show during a workout despite R2. This is accepted: it is
rare, and Home then also shows the start card instead of Resume, so the line is not the wrong part of
that screen. A `CancellationException` is rethrown, as the other Home flows do. Because the state field
is computed in the ViewModel from `activeWorkout`, the "no running workout" rule has one place. The
rule's "today" is `HomeViewModel.today`, the flow the readiness card already follows through its
midnight wait and `onForeground()`, so the line appears on day 21 with no other change.

**Showing it.** `HomeScreen` draws `HomeBackupNote` after `LastWorkoutCard`: a 16 dp floppy-disk icon
(`RepFlowIcons.floppyDisk`, a new `ic_ph_floppy_disk` vector made like the other `ic_ph_*` drawables), the text, and a text link `Back up ›` with a 44 dp target, in the
accent colour, with the same chevron treatment as `home_readiness_details`. No card, no fill, no
badge. The font scale decides the wrap, not running out of room: board `10n`'s `backupNote` forces it
when `S > 1.1`, and a `FlowRow` never reads the scale, so `Last backup 21 days ago` and the link would
still share a row at 1.3×, and `No backup yet` would even at 2.0×. So when
`LocalDensity.current.fontScale > 1.1f`, the check `HomeResumeCard.kt:106` uses, the icon and text take
one line and the link goes on its own line under them, at the start, as the board draws it. Otherwise
all three share one row, with the text taking the free width and the link at the end. The constant is
private to `HomeResumeCard.kt`, so this file has its own, as `BackupFileActions.kt:271` does.
Text is `lastBackupTitle(LastBackupLabel.DaysAgo(days))` ("Last backup 21 days ago",
the Backup screen's own string) or `R.string.backup_none_title` ("No backup yet"); only the link
text is new (`home_backup_note_action`). `HomeRoute` takes a new `onBackupClick`, which
`RepFlowNavHost` wires to `navController.navigate(RepFlowDestinations.BACKUP)`, the call Settings
already makes; Back from Backup returns to Home.

**The switch.** `AppSettings.backupReminder: Boolean = true`. The settings row is one column on the
single `settings` row: `SettingsEntity.backupReminder` (`backup_reminder INTEGER NOT NULL DEFAULT 1`),
`MIGRATION_9_10` (`ALTER TABLE settings ADD COLUMN backup_reminder INTEGER NOT NULL DEFAULT 1`),
`RepFlowDatabase.VERSION = 10`, the new migration appended to `ALL_MIGRATIONS`, and Room's exported
`app/schemas/.../10.json`. Additive, with a default, no row rewritten, no destructive migration, and
every existing install gets the reminder on, as the owner decided. `SettingToggle.BACKUP_REMINDER`
joins the other switches (`isOn`, `withToggle`), and `SettingsScreen`'s `DataGroup` draws a
`SettingsSwitchRow` right after `Backup and restore` (board `40e`):
title `Backup reminder`, meta `Shows a note on Home when your last backup is 21 or more days old`.

**Settings and backups (the item says "check").** Settings are device settings, not training data:
`AppSettings`' KDoc and `SettingsEntity`' KDoc say the table is outside the backup snapshot, and a
restore and Erase leave it alone. So the switch is **not** in `BackupSnapshot`/`BackupJsonMapper`,
needs no transfer-schema change, and a restore onto a new phone starts with the reminder on, like
every other setting. This is a stated consequence, not a gap.

No Android type enters `domain` or `application` (the new field is an `Instant` on an existing
application class); `LayerBoundaryTest` must stay green. No new dependency or module, no network.
No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | Home shows `Last backup N days ago` with a `Back up ›` link when the last backup is 21 or more calendar days old, and `No backup yet` with the same link when there has never been a backup and the first finished workout ended 21 or more calendar days ago. The line goes away once a backup is exported. |
| R2 | The line never shows while a workout is running, on a phone with no training data, or when the `Backup reminder` switch is off, and never blocks or covers anything: Start, Resume and the other cards behave as before. |
| R3 | Settings, under `Your data` after `Backup and restore`, has a `Backup reminder` switch, on by default, including on a phone upgraded from the previous version, and its value persists. |
| R4 | `Back up ›` opens Backup and restore, and Back returns to Home. At font scales above 1.1 the link wraps to its own line and nothing is clipped, in light and dark. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | A Backup reminder switch in Settings, stored on the device (R3) | - | 2 | 1 |
| CP2 | Home shows the backup line, and Back up opens Backup (R1, R2, R4) | CP1 | 2 | 1 |

<!-- CP1 -->
### CP1 — A Backup reminder switch in Settings, stored on the device (R3)

**Files:**
`app/src/main/kotlin/com/repflow/app/application/settings/AppSettings.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/database/settings/SettingsEntity.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/database/RepFlowMigrations.kt`,
`app/src/main/kotlin/com/repflow/app/infrastructure/database/RepFlowDatabase.kt`,
`app/src/main/kotlin/com/repflow/app/data/settings/LocalSettingsRepository.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/settings/SettingsViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/settings/SettingsScreen.kt`,
`app/src/main/res/values/strings.xml`,
`app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/10.json` (new, generated by Room),
`app/src/test/kotlin/com/repflow/app/presentation/settings/SettingsViewModelTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/infrastructure/database/RepFlowDatabaseMigrationTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/settings/SettingsPersistenceTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/settings/SettingsScreenTest.kt`,
`docs/UX_FLOWS.md`

What: the field, column, migration, version bump and switch described above, the KDoc lines that
count "five switches" or list the version history, and the switch in `docs/UX_FLOWS.md`'s `Data` list.

Tests:

1. `RepFlowDatabaseMigrationTest`: a v9 database with the pinned settings row migrates to v10 with
   `backup_reminder = 1`, every other column unchanged, and the other tables untouched (the file's
   existing 8-to-9 test is the pattern). The existing test that pins `ALL_MIGRATIONS` to
   `RepFlowDatabase.VERSION` stays green.
2. `SettingsPersistenceTest`: a fresh database reads `backupReminder == true`;
   `AppSettings.DEFAULT.backupReminder` is `true`; writing `false` persists across a reopened
   database. The file's in-memory database cannot be reopened, so that one case uses a real file through
   `DatabaseModule.buildRepFlowDatabase`, as `theProductionBuilderUpgradesARealVersion8DatabaseToTheNewDefaults`
   does, and deletes it afterwards. The file's `customised` value also sets `backupReminder = false`, so
   its existing Erase and restore checks hold the new column too.
3. `SettingsViewModelTest`: `onToggle(BACKUP_REMINDER, false)` writes `backupReminder = false` and
   leaves every other field; `isOn` and `withToggle` agree for the new constant.
4. `SettingsScreenTest`: the row shows under `Your data`, after `Backup and restore`, with the meta
   text; it is on by default; pressing it switches it off.

Mutation check: make the migration's default 0 (test 1; test 2 never runs a migration); make the
entity's `defaultValue` 0 (test 2's fresh-database read); make `withToggle`'s `BACKUP_REMINDER` arm
`-> this`, because the `when` is an exhaustive expression and dropping the arm would not compile
(test 3); draw the row before `Backup and restore` (test 4).

Look at it on the AVD (`start-avd.sh`, not the phone): install over a build from `main` that holds
data, so the migration really runs, and check Settings shows the switch on. Record what was seen.

<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Home shows the backup line, and Back up opens Backup (R1, R2, R4)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeBackupReminder.kt` (new),
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeUiState.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeRoute.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/home/HomeCards.kt`,
`app/src/main/kotlin/com/repflow/app/application/history/ObserveRecentTraining.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/navigation/RepFlowNavHost.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/icons/RepFlowIcons.kt`,
`app/src/main/res/drawable/ic_ph_floppy_disk.xml` (new),
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/home/HomeBackupReminderTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/home/HomeViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/application/history/ObserveRecentTrainingTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/icons/RepFlowIconsTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/home/HomeScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/home/HomeRouteLifecycleTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutLeaveRouteTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/MainActivityNavHostSmokeTest.kt`,
`docs/UX_FLOWS.md`

What: the rule, the state field, the ViewModel wiring, `HomeBackupNote`, the route and nav-host
parameter, and one paragraph in `docs/UX_FLOWS.md`'s Home section. `HomeRouteLifecycleTest` and
`ActiveWorkoutLeaveRouteTest` build `HomeViewModel` by named arguments and call `HomeRoute`, so they
get the new arguments, and `RepFlowIconsTest`'s `EXPECTED_GLYPHS` gets `ic_ph_floppy_disk`. Two
statements that Backup opens only from Settings become false and are corrected: `docs/UX_FLOWS.md`
lines 43-44 and the `RepFlowNavHost` KDoc. If a test file named here does not exist under that name,
the nearest existing class for the same subject is used and the plan's deviation note says which.

Tests:

1. `HomeBackupReminderTest` (pure): 20 days → `null`, 21 → `LastBackup(21)`, 90 → `LastBackup(90)`;
   the gap is in calendar days of the zone (a backup at 23:50 and a check at 00:10 twenty-one dates
   later shows; one minute short of a date does not); switch off, workout running and no training
   data each → `null`, each with a due date; `lastBackupAt = null` with the first workout 20 / 21
   days ago → `null` / `NoBackupYet`; `null` with no finished workout → `null`; `lastBackupAt` set
   and a first workout also old → `LastBackup` (the backup wins).
2. `ObserveRecentTrainingTest`: `firstWorkoutEndedAt` is the oldest `endedAt` among valid sessions, ignores
   an invalidated one, and is `null` with none.
3. `HomeViewModelTest`: the state carries `LastBackup` for a stale backup; goes to `null` when the
   settings emit a fresh `lastBackupAt` (the exported-backup case); goes to `null` when the switch is
   turned off and back when on; is `null` while a session runs and returns when it ends; a failing
   settings read or history read gives no line; a failing `hasTrainingData` read still gives the line
   (R2's failure rule); a cancelled read is not swallowed. A new day needs nothing else to change: a
   backup 20 days old gives `null`, and after `VirtualClock.jumpTo` past midnight and `onForeground()`
   it gives `LastBackup(21)`.
4. `HomeScreenTest`: the line shows `Last backup 21 days ago` and `No backup yet` for the two states
   with `Back up` clickable once; nothing for `null`; at font scale 2.0 the link is displayed and its
   bounds are at least 44 dp and inside the screen, in light and dark; the start card's button is
   still enabled and clickable with the line present. The wrap, with both texts on the file's 384 dp
   width: at `LARGE_FONT_SCALE_MEDIUM` (1.3) and `LARGE_FONT_SCALE_MAX` (2.0) the link's top is at or
   below the text's bottom, and at 1.0 they share a row.
5. `MainActivityNavHostSmokeTest`: Home shows the line, `Back up` opens the Backup screen, and Back
   returns to Home. The class has no Hilt test rule, so the test makes its data by walking the app, in
   this order, because `hasTrainingData` is re-read only when settings, sessions or the day change:
   (a) `Empty workout`, then the `X`, then abandon, the walk `workoutIsReachableFromHomeAndReplacesTheNav`
   already makes, which leaves a `workout_sessions` row, and `hasAnyRow` counts any row; (b) write a
   `lastBackupAt` 30 days old through `composeRule.activity.settingsRepository`, as
   `MainActivityThemeSmokeTest` writes its theme. A `finally` resets `lastBackupAt` to `null`, because
   Erase leaves the settings row and the class's KDoc promises "the next walk finds Home as this one
   did". The abandoned session stays, as it does after the existing walk: Home reads only completed
   sessions, and with no finished workout and no backup date the rule returns `null`. Run on a fresh
   install (it needs an empty AVD state, as it always does).

Mutation check: 20 → 21 boundary off by one (test 1); use `Instant` difference instead of calendar
days (test 1's midnight cases); drop the running-workout condition, the training-data condition and
the switch condition one at a time (tests 1 and 3); let a `hasTrainingData` failure mean `false`
(test 3); read the clock once instead of following `today` (test 3's new-day case); remove the
font-scale condition, so the link never takes its own line (test 4's wrap case); leave the link's
`onClick` unwired (tests 4 and 5).

Look at it on the AVD with real data, in light and dark at font scale 1.0, 1.3 and 2.0 (`adb shell
settings put system font_scale`, put back to 1.0): set the device date forward, or seed
`last_backup_at` in the app's database, so a backup is 21 and then 40 days old; check the line, the
link on its own line at 1.3 and 2.0 for `Last backup 21 days ago` and for `No backup yet` at 2.0,
`Back up ›` opening Backup, a real export making the line go, the switch off in Settings hiding it,
and a running workout hiding it. Record what was seen, including whether the line sits clear of the
bottom navigation at 2.0.

<!-- /CP2 -->

## Wrap-up (after the checkpoints; not a checkpoint)

Full `spotlessCheck`, `detekt`, `lintDebug`, the JVM suite and `connectedDebugAndroidTest` on the AVD
(`AGENTS.md`). No issue note is owed by this item.

## Self-review notes

- *Does a new user get nagged?* No. With no backup and no finished workout the rule returns `null`;
  the first reminder is 21 days after the first finished workout, as decided.
- *Erase.* Erase keeps `lastBackupAt` and deletes the data. The training-data condition hides the line
  on the emptied phone; once new data exists the old date is a true statement again.
- *Restore onto a new phone.* A restore does not bring `lastBackupAt` or the switch. The old finished
  workouts make `firstWorkoutEndedAt` old, so the line says `No backup yet` straight away. That is
  true (this phone has made no backup) and it is the quiet line, not a block, and board `40f` agrees.
  Accepted, and it goes in the functional checklist's known limitations.
- *Clock changes.* The rule reads `Clock` and the device zone; a clock set back past the backup date
  gives a negative gap and no line. `lastBackupLabel` clamps the same case to "Today". Same source,
  no new behaviour.
- *Staleness while Home is open.* `hasTrainingData` is re-read when the settings, sessions or day
  change, not on every frame. A user who erases, then adds only an exercise, sees the line on the next
  of those events. The wording is a reminder, not a safety claim; accepted.
- *Migration risk.* One additive column with a default, on a one-row table, the 8-to-9 pattern; the
  migration test runs on the real database. No destructive path, no fallback.
- *Usability.* One quiet line, one switch, where the owner's boards put them. The switch row is the
  way out of a reminder the user does not want. It never blocks Start or Resume.
- *Size.* One mechanism (the rule). Two checkpoints, the second depending on the first only for the
  setting it reads.

## Decisions on review findings (local plan review, round 1)

Each finding was checked against the code. All are applied above, with one difference in O1.

| Finding | Decision | Evidence |
|---|---|---|
| I1, the link's wrap | Accepted | `ActiveWorkoutScreen.kt:531` describes `FlowRow` as dropping a child only when it no longer fits. Board `10n`'s `backupNote` sets the text's `flex-basis` to `calc(100% - 28px)` under `stack()` (`S > 1.1`). Measured with `inter_regular.ttf` at 13sp, not run in the app: at 1.3× the text is 197 dp and `Back up` 65 dp, and at 2.0× `No backup yet` is 179 dp and `Back up` 99 dp, against 328 dp free beside the icon on a 384 dp screen. |
| O1, smoke test seeding | Accepted, one difference | The test cannot pass with only a stale date, and the row must go in first. **Rejected: that the row must be undone too.** The row comes from the walk `MainActivityNavHostSmokeTest.workoutIsReachableFromHomeAndReplacesTheNav` already makes, which leaves the same abandoned session behind today. `TrainingDataDao.hasAnyRow` counts it (`TrainingDataDao.kt:27`), and `ObserveRecentTraining.kt:61` reads completed sessions only, so Home is unchanged. Only `lastBackupAt` needs its reset. |
| O2, a new day | Accepted | `HomeViewModelTest` has `VirtualClock.jumpTo` and `onForeground()`, in `after a deep sleep only onForeground re-reads the clock`. |
| O3, `Files:` | Accepted | `HomeRouteLifecycleTest.kt:109,159`, `ActiveWorkoutLeaveRouteTest.kt:377,459`, `RepFlowIconsTest.kt:83`. These are the only other `HomeRoute(` and `HomeViewModel(` callers. |
| O4, mutations | Accepted | `SettingsPersistenceTest.kt` builds with `inMemoryDatabaseBuilder`, so no migration runs. `withToggle` in `SettingsViewModel.kt` is an exhaustive `when` expression. |
| O5, persistence test | Accepted | As O4, and `customised` in `SettingsPersistenceTest.kt` feeds the Erase and restore checks. |
| O6, `docs/UX_FLOWS.md` | Accepted | `docs/UX_FLOWS.md:43-44` and the `RepFlowNavHost` KDoc say Backup opens from Settings. The switch line goes with CP1, which adds the switch. |
| O7, failed workout read | Accepted, as a stated limit | `HomeViewModel.activeSession` emits `null` on failure. Design's failure rules now say so. |
