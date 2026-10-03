# repflow-redesign-visual-foundation-remediation-1-remediation-1 — Execution Plan (Revision 4)

Second remediation child. Its parent is `repflow-redesign-visual-foundation-remediation-1`,
itself a remediation child of `repflow-redesign-visual-foundation`.

- Work item: `repflow-redesign-visual-foundation-remediation-1-remediation-1`
- Parent: `repflow-redesign-visual-foundation-remediation-1` (functional review round 4 passed cleanly; waits only for this item)
- Governing workflow version: `2.1`
- Base commit: `389608ee3a16e09565145673cd496953a9c3b091`
- Plan revision: 3

## Goal and scope

Discharge **group B** of the parent's functional review round 1
(`FUNCTIONAL_REVIEW.md`, "Group B — defer to a second remediation child"), plus
one item the user added on 2026-10-03. The scope is exactly **B1–B7**; nothing
else is in this item.

| Id | Requirement |
|---|---|
| B1 | Settings follow the newer `5c`/`5d`: `Theme`, `Default rest`, `Extra set fields`, an `Archived exercises and plans` row, and a dedicated Backup screen (`5d`). Closes inventory `O11`. |
| B2 | Progress follows the newer `5b`: exercise picker sheet, line chart, `3m`/`6m`/`All`, `Sessions`/`Avg RPE` tiles, `Training frequency`, `Records`. Closes inventory `O12`. |
| B3 | Set entry keeps weight and reps after each logged set and starts each exercise from last session's numbers. **Replaces `D60`** (the clear-after-set rule); user decision, adopting the design's behaviour. Rest after a set follows Q9's precedence (B3's neighbour, user decision 2026-10-03). |
| B4 | Empty-state glyphs for the Library (both filters) and Recovery history. |
| B5 | Search fields are 48dp (`2c`), not 56dp, in the Library and both pickers. |
| B6 | Progress orders exercises by most recently trained, and an exercise with 0 sets in a session does not count as trained there. |
| B7 | Finishing or abandoning a workout also cancels the `Rest done` notification (id 1001, channel `rest_timer`); so do a successful `Erase all data` and a successful `Restore` (review I-3), which also discard the active session. |

**Preserved, untouched:** J1–J9, the rest-alert design (exact alarm, explicit
vibration, the `rest_timer` sound, the accepted ~5 s `−15s` delay), the
`RestTimerExpiryHandler` active-session guard, and every register row not named
here. Architecture is `CLAUDE.md`'s: Domain → Application → Data/Infrastructure
→ Presentation, no new Gradle module, no new dependency (the chart is Compose
Canvas, as the parent's Progress card already is).

## Source of truth

The live Claude Design project `9e1d47b2-48ae-45bc-a101-c59c37c49550`
(`RepFlow.dc.html`, newest turn wins) is authoritative; this repository is
authoritative for implementation reality. Artboards read for this plan (via the
claude-design MCP, 2026-10-03): `5b` Progress, `5c` Settings, `5d` Backup and
restore, `2c` Library search, `6b` component spec, `4a` focus mode. **Every
checkpoint that builds a screen starts by re-reading its artboard from the live
project**, not from this plan's summary (parent plan, "Source-of-truth
discipline"). Values quoted below are the design as read on 2026-10-03.

### What the design draws, and what it does not

- `5b`: title and a full-width exercise picker button (min-height 52, radius 10,
  barbell icon, name, caret); three metric buttons `Top set` / `Est. 1RM` /
  `Volume` (min-height 40, radius 8, default `Top set`); a chart card (radius
  14) with a label (`Heaviest working set` / `Estimated 1RM` / `Volume per
  session`), range pills `3m` `6m` `All` (default `All`), the current value
  30/500 and a delta line (`+10 kg · +14%`), a selection readout strip
  (`Latest · 12 Aug` / `5 Aug`, value right), and a 96dp line chart (3 y
  labels and gridlines at max/mid/min, 2.4 line `#9184d9`, soft area fill,
  selected dot r 4.5 `#d2cefd`, dashed cursor, x labels every 3rd point when
  more than 6); two stat tiles `Sessions`, `Avg RPE`; section `Training
  frequency` (8 weekly bars, last one accent, `8 weeks ago` / `this week`,
  `3.4 sessions a week on average`); section `Records` (`Heaviest set — 82.5 kg
  × 7`, `Most reps at 80 kg — 8`, each with a date). The picker sheet is
  `Track an exercise`, rows min-height 56, selected row tinted with a check.
  **Not drawn:** any empty or one-point state; how tiles, frequency and
  records are computed or scoped; date versus point-count ranges (the
  prototype slices by point count: 6/9/12).
- `5c`: `Units and appearance` (`Weight unit` kg/lb; `Theme` System/Light/Dark,
  full-width segments), `Rest timer` (`Start rest automatically`, `Default rest`
  with value `2:00 ›`, `Vibrate when rest ends`, `Notify when rest ends`),
  `During a workout` (`Keep the screen on`, `Confirm before finishing`, `Extra
  set fields` with value `Collapsed ›`), `Data` (`Backup and restore` → `5d`,
  `Archived exercises and plans` ›), the `Irreversible` card, the footer.
  **Not drawn:** the chooser for `Default rest`, the chooser for `Extra set
  fields`, and the destination of `Archived exercises and plans`.
- `5d`: hero card (`Last backup 2 days ago`, `9 Aug, 21:04 · 412 KB · schema
  v3`, `Export backup now`, the JSON note), `Recent backups on this device`
  (files with `Share`, plus the safety snapshot), `Export for other tools` →
  `Workout history as CSV`, the `Replaces everything` card with `Restore from
  a backup`, and a three-step restore (file sheet, `Restore this backup?`
  dialog, `Backup restored · 214 workouts` toast with `Undo`).
- `2c`: search field min-height 48, radius 10, surface fill, 1dp hairline,
  padding 0/12, gap 9, magnifier, text 14.5, clear glyph.
- `4a` focus mode: after `Log set` weight and reps stay; only the warm-up
  toggle and RPE/pain/technique clear. On opening an exercise the stepper is
  seeded from last session's weight; `Last time: 80 kg × 8` shows before the
  first set.

## Product decisions (user, 2026-10-03)

All of Q1-Q8 were answered by the user on 2026-10-03, each accepting the plan's
own proposal; Q9 and the Q6 amendment were added after local plan review round 1
(the same day). They are decided product facts, not open questions; each names
the checkpoints written against it. The user also **declined** "keep the Save bar
above the keyboard": the usual hide-keyboard-then-Save behaviour stays and that
change is out of scope.

- **Q1 — Default rest (CP5, CP6, CP7).** Presets 1:00, 1:30, 2:00, 3:00 plus
  `Other` (any whole 1-1800 s); **initial 1:30** (90 s, so behaviour is unchanged
  on upgrade, the `D21` precedent; not the drawing's 2:00).
- **Q2 — Extra set fields (CP5, CP6, CP7).** `Always shown` / `Collapsed` / `Off`,
  initial `Collapsed` (today's behaviour). `Off` hides RPE, pain and technique in
  focus mode only; already-logged values are still shown and the corrections
  sheet is unchanged.
- **Q3 — Theme (CP5, CP6, CP7).** `System` / `Light` / `Dark`, initial `System`,
  so nothing changes on upgrade.
- **Q4 — Archived exercises and plans (CP7).** A new `Archived` screen (exercises,
  then plans, each row with `Restore`) built on the existing archived queries and
  `RestoreExercise`/`RestoreTrainingPlan`, reached only from Settings.
- **Q5 — Backup screen `5d` (CP5, CP8).** Build the hero (`Last backup <relative
  time>` from one new nullable device setting `last_backup_at`, written when a
  **backup** export succeeds and kept out of the backup file; `No backup yet`
  before the first), `Export backup now`, `Workout history as CSV`, and the
  `Replaces everything` card with today's destructive restore confirmation.
  **Omitted, as registered deviations** (each needs storage or a restore-undo that
  does not exist): `Recent backups on this device`, `Share`, the safety-snapshot
  row, file metadata, the `Undo` toast, the restore file sheet with its preview,
  and the `Backup restored · N workouts` toast copy (today's restore feedback
  stays). `last_backup_at` is definitely part of `MIGRATION_8_9`, `9.json` and CP8.
- **Q6 — Sessions / Avg RPE / Training frequency / Records (CP2, CP3).** All four
  are **for the chosen exercise** and ignore the range pills except where stated.
  `Sessions` = valid sessions with at least one working set of it, within the
  range. `Avg RPE` = mean of the recorded RPE over its working sets in the range,
  `—` when none is recorded. `Training frequency` = sessions of it per calendar
  week (Monday start) for the last 8 weeks, with the average over those 8.
  `Records` (all-time, ignoring the range, each dated): `Heaviest set — <load> ×
  <reps>` (heaviest working load, most reps at it, its date) and, **amended
  2026-10-03 (resolves review O-4)**, `Best est. 1RM — <value>` (Brzycki, 12-rep
  ceiling, the same value as the chart, dated), replacing "Most reps at <that
  load>", which repeated the first row's reps. For reps-only and timed exercises
  the records are `Most reps in a set` / `Longest hold` (single record row).
  Est. 1RM stays Brzycki with the 12-rep ceiling (J6).
- **Q7 — Ranges (CP2, CP3).** By date: `3m` = the last 3 calendar months, `6m` = 6,
  `All` = everything, replacing the parent's 12-session window (`D110`, which is
  **superseded**, not amended); the chart draws every session in range.
- **Q8 — Seed numbers (CP9).** Weight and reps both from the **last working set of
  the most recent valid session that logged the exercise**; empty (`—`) for a
  never-done exercise; the plan's reps range is a caption only. No
  readiness-based `+load step` or deload seeding. CP9's seed source is refined
  for an exercise already worked this session (review I-2).
- **Q9 — Rest precedence (CP6).** The rest after a set is the plan row's rest if
  set; otherwise the exercise's own `Default rest` (`2b`); otherwise the app-wide
  `Default rest` from Settings (Q1, initial 1:30). This also fixes an empty or
  ad-hoc workout ignoring the exercise's own `Default rest`.

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | B7: clear the rest-done notification when a workout is finished or abandoned | - | 2 | 1 |
| CP2 | B6 and B2 read model: trained-only ordering, date ranges, tiles, frequency, records | - | 3 | 2 |
| CP3 | B2 Progress screen conversion to 5b | CP2 | 3 | 2 |
| CP4 | B5 and B4: shared 48dp search field and empty-state glyphs | - | 2 | 1 |
| CP5 | Settings schema 8 to 9: theme, default rest, extra set fields | - | 3 | 2 |
| CP6 | Apply the new preferences: theme, default rest, extra set fields | CP5 | 3 | 2 |
| CP7 | B1 Settings screen to 5c and the Archived screen | CP5, CP6 | 3 | 2 |
| CP8 | B1 dedicated Backup screen (5d) | CP7 | 3 | 2 |
| CP9 | B3 set entry keeps its numbers and seeds from last session | CP6 | 3 | 2 |
| CP10 | Verification, deviation register and decision records | CP1, CP2, CP3, CP4, CP5, CP6, CP7, CP8, CP9 | 2 | 1 |

Each checkpoint ends with the full local gate for what it touched
(`./gradlew spotlessCheck detekt testDebugUnitTest assembleDebugAndroidTest`,
plus the named instrumented tests on the AVD at the checkpoint that adds them;
no phone). CP1, CP2, CP4 and CP5 are independent of each other and of the
screens; CP3 follows CP2; CP6–CP9 follow CP5 as the table says.

### CP1 — B7: clear the rest-done notification when a workout ends

Data/persistence impact: none.

Cause: `RestTimerExpiryHandler` guards the alert, but a notification already
posted (id 1001) stays in the shade after finish or abandon, because nothing
cancels it; only `RestTimerAlarmScheduler.cancel` (the alarm) is ever called.

1. New `presentation/workout/RestNotificationCanceller.kt`: `fun interface
   RestNotificationCanceller { fun cancel() }`, `SystemRestNotificationCanceller`
   (`@Inject`, `@ApplicationContext`, `NotificationManager.cancel(
   RestTimerExpiredReceiver.NOTIFICATION_ID)`; no channel deletion), and a
   Hilt `@Binds` module beside `SystemRestAlertVibrator`'s `RestAlertModule`
   pattern.
2. `ActiveWorkoutViewModel` (complete and abandon actions), `HomeViewModel`
   (`onAbandonWorkout`), `SettingsViewModel.onEraseAllDataConfirmed` and
   `BackupViewModel.onRestoreConfirmed` take the seam and call it after the use
   case returns `Success` (review I-3: `Erase all data` and `Restore` also
   discard the active session, and the notification has `setAutoCancel(true)`
   with no content intent, so it would otherwise linger for a workout that no
   longer exists; `RestTimerExpiryHandler`'s own KDoc names both as "rest has
   gone" paths). Failure leaves the notification (the session is still
   active). Skipping the rest and starting the next rest are unchanged.
3. Tests: JVM `ActiveWorkoutViewModelTest` (complete success cancels; abandon
   success cancels; failure does not), `HomeViewModelTest` (abandon success
   cancels, failure does not), `SettingsViewModelTest` (erase all data success
   cancels, failure does not) and `BackupViewModelTest` (restore success
   cancels; failure and an invalid backup do not) with a recording fake; instrumented
   `RestTimerExpiryHandlerTest` gains one case: post through the handler,
   call the real `SystemRestNotificationCanceller`, assert the notification
   is gone from `NotificationManager.activeNotifications`.
4. Nothing else changes: the alarm cancel, the `RestTimerExpiryHandler`
   active-session guard and the channel stay as built.

### CP2 — B6 and B2 read model

Data/persistence impact: none; every value is derived from completed valid
sessions, as today.

Files: `application/progress/ExerciseProgress.kt`, `ObserveExerciseProgress.kt`
(and new `ProgressRange.kt`, `ExerciseProgressDetail.kt` if the file would
pass detekt's size limit).

1. **B6:** `exerciseProgressOf` counts an exercise occurrence only when
   `exercise.sets.isNotEmpty()`. **One "trained" rule, stated once (review
   O-5):** for **B6's ordering and list membership** an occurrence counts when
   it has any set (a warm-up-only occurrence is still a trained exercise, as
   today); for **Q6's `Sessions`, `Avg RPE`, records and the Progress series**
   (and CP9's seed) it must have at least one **working** set, because those are
   about performance. `lastTrainedAt`, the ordering and the list membership use
   only B6-counted occurrences; an exercise never counted anywhere is not
   listed; `name` and `trackingType` come from the last **counted** occurrence,
   not from `chronological.last()`.
2. `ProgressRange { THREE_MONTHS, SIX_MONTHS, ALL }` with `startsAfter(now,
   zone)` (calendar months back from `now`; month-end clamps to the last day of the
   target month, so 31 May minus 3 months is 29 Feb or 28 Feb, and the bound is
   **exclusive**: a session exactly at the bound is outside, `ALL` has no bound;
   review O-13). `ProgressSeries` loses the fixed
   12-point window; `window`, `hasTrend`, `delta`, `best`, `latest` become
   functions of a range. The range's points drive the chart and the headline
   delta (`+10 kg · +14%`: last minus first, percent rounded; no percent when
   the first value is 0).
3. `ExerciseProgress` gains the Q6 derivations as pure functions of the
   sessions and a range/now: sessions count, average RPE, weekly frequency
   (8 entries, Monday-start weeks in the given zone, average), records
   (`Heaviest set` and `Best est. 1RM` per Q6 as amended; one record for a
   reps-only or timed exercise). With no
   eligible set (none of 12 reps or fewer), `Best est. 1RM` is absent in the
   model and the row shows `—` (review P-4, consistent with the chart's `Est.
   1RM needs a set of 12 reps or fewer.`; registered). A
   reps-only or timed exercise offers the Top-set series only (`D22` kept) and
   its own record labels.
4. Tests (`ExerciseProgressTest`, plus `ProgressRangeTest`): an exercise with
   0 sets in the latest session is not "most recent" and a session with only
   it contributes no point and no session count; an exercise with 0 sets in
   every session is absent; ranges include/exclude by date at month edges and
   across a year; delta and percent; frequency across a week boundary and a
   DST change; average RPE with and without recorded values; records tie
   rules (heaviest load ties → most reps → latest date; best est. 1RM ties →
   latest date), the month-end clamp and the exclusive bound, and `name` and
   `trackingType` taken from the last counted occurrence.

### CP3 — B2: Progress screen to `5b`

Data/persistence impact: none (a picker choice, a metric and a range are held
in the ViewModel's state and survive rotation via `SavedStateHandle`; nothing
stored).

Files: `presentation/progress/ProgressScreen.kt`, `ProgressCard.kt` (replaced
by the chart card), new `ProgressChart.kt`, `ProgressExercisePicker.kt`,
`ProgressStatsSection.kt`, `ProgressModel.kt` (chart maths), `ProgressUiState.kt`,
`ProgressViewModel.kt`, strings, and `RepFlowIcons` (+ `repeat` if the Records
row uses it, from the bounded local Phosphor set).

1. Re-read `5b` first; transcribe the structure above. The exercise chips
   become the picker button and the `Track an exercise` bottom sheet
   (`RepFlowSheet`, rows 56dp, selected row tinted, check-fat; scrolls for
   long lists; the sheet lists exercises with counted history, most recent
   first).
2. Line chart on Compose Canvas (no dependency): three y labels and
   gridlines at max/mid/min, 2.4dp line, area fill, scrubber driven by **nearest-point hit
   testing** on the tap and drag x position (no fixed-width strips, which cannot
   fit `All` with dozens of sessions; review O-9), selection readout strip defaulting to the latest point. **X-axis labels are
width-aware (review GX-I2):** the design's "every 3rd point when more than 6"
comes from a prototype capped at 12 points and is not carried over, because `All`
now plots every session. The chart measures one date label (`TextMeasurer`, the
widest of the plotted labels) and takes the label stride as the smallest `n` such
that `n x (pixels between adjacent points)` is at least that width plus a
**minimum gap of 8dp**; labels are drawn at points `0, n, 2n ...`, and the
**last point's label is always drawn**, the stride-aligned label nearest to it
being dropped when the two would be closer than the gap. The first point's label
is always drawn. The rule is a pure function in `ProgressModel.kt`
(`xLabelIndices(pointCount, plotWidthPx, labelWidthPx, gapPx)`), so it is JVM
testable. States
   the design does not draw, decided here and registered: **no sessions** →
   the existing empty state with a glyph; **one point** → value and readout
   only, no line (a dot) and no delta; Est. 1RM with no eligible set → J6's
   sentence, unchanged.
3. Accessibility: the chart is one node with a spoken summary (latest value,
   delta, range); the selection strip is a live readout; range pills and
   metric buttons are selectable with state, never colour alone; every tap
   target 44dp (the picker 52, metric buttons 40 as drawn **plus** a 44dp
   hit area, registered).
4. Tests: `ProgressModelTest` (chart scaling, flat series, one point, and
   `xLabelIndices`: few points show every label, the first and last indices are
   always present, consecutive shown labels are at least `labelWidth + gap`
   apart, and 60, 120 and 400 point series at 360dp and 384dp widths never
   overlap), and a `ProgressScreenTest` case with a dense `All` series (60+
   sessions) at 360dp and 384dp widths asserting that the drawn labels do not
   overlap (their bounds, from the chart's label test tags, are pairwise disjoint
   by at least the gap) and that the first and last labels are shown (review
   GX-I2), `ProgressViewModelTest` (picker, metric and range state, range
   kept across exercises, falls back when the exercise leaves the list),
   `ProgressScreenTest` rewritten for the new surface (picker sheet opens and
   selects; range pill changes the headline; tapping the chart moves the
   readout; one-point and empty states; tiles, frequency and records text, and the `Best est. 1RM` row showing `—` when
   no set is eligible, review P-4).
   Of the seven existing `ProgressScreenTest` methods, three methods change
   meaning and are rewritten (named here, review O-6, P-5): `theCardShowsTheLatestValueTheDeltaSinceTheWindowStartTheChartAndTheBest`
   (loses its window), `theChipsAndSegmentsReportTheChoice` (chips become the
   picker), `anOfferedMetricWithOnePointShowsTheEmptyStateInsteadOfAChart`
   (inverts: one point now shows its value and readout; the bar-chart semantics
   are pinned inside these, not a fourth item); `volumeReadsInKilogramsTotal`,
   `estimatedOneRepMaxNamesTheRepCeilingWhenNoSetCanGiveOne`,
   `aRepsOnlyExerciseOffersTopSetAloneAndSaysWhy` and `noHistoryShowsTheTabsEmptyState`
   are kept and re-run, adjusted only for the new surface.

### CP4 — B5 and B4

Data/persistence impact: none. Starts by re-reading `2c` from the live project
(review O-10).

Cause of B5: all three fields already say `heightIn(min = 48.dp)`, but
Material's `OutlinedTextField` has its own 56dp minimum, so the `heightIn`
never takes effect. One shared control fixes the three at once.

1. New `designsystem/components/RepFlowSearchField.kt`: `BasicTextField` with
   `OutlinedTextFieldDefaults.DecorationBox` (tight content padding), `height(48.dp)` at default
   font scale that **grows above 48dp only when the font scale needs it**
   (`heightIn(min = 48.dp)` on the decoration box, so text never clips; review
   O-8), radius 10, `surface` fill, 1dp hairline, magnifier,
   14.5sp text, placeholder, clear button with 48dp hit area (the visible
   field stays 48). Replaces the inline field in `ExerciseListScreen`,
   `WorkoutSheets` (workout picker) and `TrainingPlanEditorPicker`; their
   per-file `SearchFieldMinHeight`/`SearchMinHeight` constants go.
2. **B4:** `exerciseListEmptyMessageRes` callers pass a glyph per reason:
   `NO_EXERCISES` → barbell, `NO_ARCHIVED` → archive, `NO_SEARCH_RESULTS` →
   magnifying glass (the create-from-query footer variant is unchanged);
   `RecoveryHistoryScreen`'s empty state → moon-stars (the design's own
   recovery empty glyph on Home). All exist in `RepFlowIcons`; no new asset.
   `RepFlowEmptyState` already draws the `1d` treatment (26dp at 35%).
3. Tests: new instrumented `RepFlowSearchFieldTest` (measured height exactly 48dp at
   default font scale; at 200% font scale the field is at least 48dp and the
   typed text is not clipped; clear button; IME search action);
   `ExerciseListScreenTest`, `TrainingPlanEditorScreenTest` and a new picker case
   in `ActiveWorkoutScreenTest` (the workout picker has no search test today)
   assert the 48dp height; `ExerciseListScreenTest` and
   `RecoveryHistoryScreenTest` assert the glyph exists for both Library
   filters and Recovery history (matched by a test tag on the glyph).

### CP5 — Settings schema 8 → 9

Data/persistence impact: **yes, the only schema change in this item.** Room
8 → 9, an explicit `MIGRATION_8_9`, a migration test, schema JSON `9.json`.

1. `MIGRATION_8_9` is additive, four `ALTER TABLE settings ADD COLUMN`
   statements, every column with a default so the existing pinned row is valid
   and no row is rewritten: `theme TEXT NOT NULL DEFAULT 'SYSTEM'`,
   `default_rest_seconds INTEGER NOT NULL DEFAULT 90`, `extra_set_fields TEXT
   NOT NULL DEFAULT 'COLLAPSED'`, and `last_backup_at INTEGER` (nullable, no
   default; Q5). Enum values persist as **stable strings**
   through explicit converters, never ordinals. `@ColumnInfo(defaultValue = …)`
   matches the DDL exactly so Room's schema validation passes. An unknown stored
   `theme` or `extra_set_fields` string (corruption, a downgrade) decodes to the
   default instead of throwing, because the theme is read at launch (review
   O-12). **One `ALL_MIGRATIONS` array** (`MIGRATION_1_2` ... `MIGRATION_8_9`) is
   defined in `RepFlowMigrations.kt` and used by `DatabaseModule` (which today lists
   them by hand) and the 1 → 9 chain test. **The end version is a shared constant (review
   GX-I1):** `RepFlowDatabase.VERSION` (`const val VERSION = 9`) is used in
   `@Database(version = VERSION)` and by the contents test; Room 2.8.4 keeps
   `@Database` at binary retention, so it cannot be read reflectively. The production builder is extracted as
   `DatabaseModule.buildRepFlowDatabase(context, name)` (`.addMigrations(*ALL_MIGRATIONS)`
   plus `SETTINGS_SEED_CALLBACK`, no destructive fallback); `provideRepFlowDatabase`
   delegates to it with `DATABASE_NAME`, and the test below calls it with its own
   file name, so a migration missing from the production registration fails a test
   (review I-4, N-2); no destructive path exists or is added.
2. `SettingsEntity`, `AppSettings` (adds `theme: ThemeMode`, `defaultRestSeconds:
   Int`, `extraSetFields: ExtraSetFields`, `lastBackupAt: Instant?`; `DEFAULT`
   keeps today's behaviour per Q1–Q3), `LocalSettingsRepository` mapping. `INSERT_DEFAULT_SETTINGS_ROW_SQL` stays
   **byte-for-byte unchanged** (`MIGRATION_7_8` executes it against the v8-shaped
   table, and shipped migration SQL is frozen; adding the new columns would fail
   every 7 → 8 upgrade): the new columns get their values from the DDL defaults
   on both the migration path and the `SETTINGS_SEED_CALLBACK` path (asserted;
   `SettingsPersistenceTest.aFreshDatabaseHasThePinnedRowAtTheDefaults` covers the
   fresh-install half).
   `ThemeMode`/`ExtraSetFields` live in `application/settings` (no Android
   types; domain stays pure).
3. Backup: settings stay **outside** the snapshot (`BackupSnapshot` and
   `BackupJsonMapper` unchanged, snapshot version unchanged); restore and
   `Erase all data` leave the row untouched, as today. A test asserts the new
   columns survive both.
4. Tests: `RepFlowDatabaseMigrationTest` (8 → 9 keeps the five existing
   switch values, gives the new defaults, and opens against `9.json` with
   `MigrationTestHelper`; a full chain 1 → 9 through `ALL_MIGRATIONS` still opens; a contents test that
   `ALL_MIGRATIONS` contains every `MIGRATION_n_n+1` from 1 → 2 up to
   `RepFlowDatabase.VERSION`, so a future 9 → 10 cannot be forgotten; and **the production-registration test (review N-2)**:
   create a real version-8 database with `MigrationTestHelper` under the
   file name `migration-8-9-registration-test`, seeded with the pinned settings
   row **with one non-default value** (`keep_screen_awake = 1`, whose default is
   0), and close it; open that file through
   `DatabaseModule.buildRepFlowDatabase(context, "migration-8-9-registration-test")`
   with `context = InstrumentationRegistry.getInstrumentation().targetContext`,
   **the same context the helper creates its file in** (`MigrationTestHelper`
   uses `targetContext.getDatabasePath(name)` and `Room.databaseBuilder` opens
   `context.getDatabasePath(name)`; the test-APK context would open a different
   path and a fresh v9 database would pass), and assert it opens, the row reads
   the new defaults (`SYSTEM`, 90, `COLLAPSED`, null), `keep_screen_awake` is
   still 1 and the other four switches are kept (a fresh-install row has
   `keep_screen_awake = 0`, so a file that bypassed the migration fails). Close
   the built `RepFlowDatabase` in a `finally` (or `helper.closeWhenFinished`).
   It fails if the production builder omits `MIGRATION_8_9`, whichever list it
   uses (review GX-I1), a repository test
   for each new field's round trip, an unknown enum string reading the default,
   and an absent row reading `DEFAULT`,
   `InMemorySettingsRepository` fixture updated, `LocalBackupRepositoryAtomicityTest`
   and the restore-preserves-settings test extended to the new columns.

### CP6 — Apply the new preferences

Data/persistence impact: none beyond CP5.

1. **Theme:** `RepFlowTheme` takes the stored `ThemeMode` (collected at the
   activity from `SettingsRepository.observe()`) and picks the scheme from it
   instead of `isSystemInDarkTheme()` alone; `repFlowExtraColors` already
   derives from the applied scheme. Decided and registered (review O-3): until
   the stored mode loads the first frame renders `System`, which **accepts** a
   brief flash on cold start for a user whose stored choice differs from the
   system theme (the window background in the XML theme and the first bar style
   follow the system theme; no splash dependency is added); this is a
   registered deviation, not an "avoided flash". System bars follow the applied
   scheme through an explicit `SystemBarStyle` (`enableEdgeToEdge(statusBarStyle,
   navigationBarStyle)` re-applied when the mode changes).
2. **Default rest (Q9 precedence):** the rest after a set is the plan row's rest
   if set, else the exercise's own `Default rest` (`Exercise.defaultRestDuration`,
   carried on `ActiveExerciseUi`), else `settings.defaultRestSeconds`.
   `ActiveWorkoutViewModel.onRecordSet` (via `plannedRestSecondsFor`, which today
   falls straight to `DEFAULT_REST_TIMER_SECONDS`) and
   `WorkoutFocusModel.restSecondsAfterSet` both resolve through **one shared
   function** so the displayed rest and the started rest cannot disagree. An
   empty or ad-hoc workout (no plan row) therefore now honours the exercise's own
   `Default rest`. The constant remains as the setting's default and for
   `StartRestTimer`'s own default parameter.
3. **Extra set fields:** `SetDetailSection` follows the mode: `COLLAPSED`
   today's disclosure; `ALWAYS` always expanded (no toggle); `OFF` not drawn
   (values entered earlier in the session are not lost: a hidden section
   submits no RPE/pain/technique). Warm-up chip unaffected.
4. Tests: `ActiveWorkoutViewModelTest` and a JVM test of the shared resolver
   (plan row rest wins over both; exercise `Default rest` wins over the app
   default; app default used when neither is set; **empty/ad-hoc workout with an
   exercise `Default rest` uses it, and with none uses the app default**; a
   settings change applies to the next set), **the displayed rest (review
   P-2)**: `WorkoutFocusModel.restSecondsAfterSet` changes from a no-argument
   extension to taking **one parameter, the app default** (the receiver,
   `ActiveExerciseUi`, already supplies the plan row's rest and the exercise's
   `Default rest`; the ViewModel, which already observes `_settings`, puts the
   resolved app default on the UI state; review O-3), with a screen or model case that the warm-up hint shows the
   exercise's `Default rest` in an ad-hoc workout and the app default when it has
   none; and a ViewModel case that an exercise added mid-workout to a planned
   workout (`plannedExerciseId = null`) starts its own `Default rest`, not 90 s;
   `ActiveWorkoutScreenTest` (three modes), a JVM theme-selection test for `ThemeMode` × system flag,
   `MainActivityNavHostSmokeTest` boots in each theme (and asserts the system-bar
   style follows the applied scheme where the harness allows).

### CP7 — B1: Settings screen to `5c`, and the Archived screen

Data/persistence impact: none beyond CP5.

1. Re-read `5c` from the live project, then restructure `SettingsScreen`/`SettingsRows`: `Units and
   appearance` (**`Weight unit` is not built, `D5` stands**; `Theme` as three
   full-width segments), `Rest timer` (`Start rest automatically`, `Default
   rest` row with its value, `Vibrate when rest ends`, `Notify when rest
   ends`), `During a workout` (`Keep the screen on`, `Confirm before
   finishing`, `Extra set fields` row with its value), `Data` (`Backup and
   restore` row → the CP8 screen, `Archived exercises and plans` row → the
   Archived screen), the `Irreversible` card with today's typed confirmation,
   the footer. The `Exercise library` row stays (`D25`).
2. `Default rest` and `Extra set fields` open **sheets** (`6b`: sheets for
   choices): Q1's presets with `Other` (the existing numeric keypad), and the
   three modes with one-line explanations. Copy follows `5c`; strings the
   design does not state are registered.
3. The Archived screen (per Q4): `presentation/archived/` route
   `settings/archived`, ViewModel over the existing archived observation of
   exercises and plans, `Restore` through `RestoreExercise`/`RestoreTrainingPlan`
   with the library's/plans' snackbar-with-undo pattern; empty states with
   glyphs. Navigation: `RepFlowNavHost` and `RepFlowDestinations` gain the two
   routes (Backup is CP8's).
4. Tests: `SettingsViewModelTest` (each new setting writes through and
   reports `SAVE_FAILED`), `SettingsScreenTest` rewritten for the grouped
   layout (named: the methods pinning `Data` group rows move to CP8's screen),
   an instrumented `ArchivedScreenTest`, `MainActivityNavHostSmokeTest`
   (Settings → Archived → back).

### CP8 — B1: dedicated Backup screen (`5d`)

Data/persistence impact: `last_backup_at` write on a successful **backup**
export (Q5); no other. Starts by re-reading `5d` from the live project (review
O-10).

1. New `presentation/backup/BackupScreen.kt` and route `settings/backup`,
   reusing `BackupViewModel` and `BackupFileActions` (system picker, restore
   validation and the existing destructive confirmation are unchanged and
   remain the only restore path). Layout per Q5's proposal: hero card,
   `Export for other tools`, `Replaces everything` card. The Settings `Data`
   group's three action rows are removed from Settings (CP7 already links
   here); `Saved` feedback moves with the export button.
2. `BackupViewModel` writes `last_backup_at` through `SettingsRepository`
   after a successful **`BackupExportKind.BACKUP`** export only, never a CSV
   export (a "Last backup just now" after a CSV export would mislead the user
   about data safety; review O-2); a failed write does not fail the export.
3. Registered deviations (Q5): no recent-files list, no `Share`, no safety
   snapshot row, no file metadata, no restore `Undo` toast, no restore file
   sheet with a preview, no `Backup restored · N workouts` toast copy.
4. Tests: `BackupViewModelTest` (timestamp written on a backup success; **not
   on a CSV success**, a failure or a cancel), `BackupScreenTest` (hero text for no backup / a backup,
   export, CSV, restore confirmation still required), the two existing
   `BackupRoute…` instrumented tests re-pointed at the new route (they
   exercise SAF cancel and an unreadable file and must keep passing).

### CP9 — B3: set entry keeps its numbers, seeds from last session

Data/persistence impact: none; derived from completed sessions. Starts by
re-reading `4a` from the live project (review O-10).

This **replaces `D60`**. The accidental-duplicate-submit concern behind it
(Milestone 8 finding #3) is mitigated by what already exists: `Undo last`, the
`Last:` line and the correction sheet; CP10 records the trade-off.

1. New `application/workout/LastPerformance.kt`: `LastPerformance(load, reps,
   durationSeconds, date)` and a pure function over `WorkoutSession` history
   giving, per exercise id, the last **working** set of the most recent valid
   session that logged it with at least one working set (a skipped, warm-up-only
   or invalidated session is passed over; the "trained" rule of CP2 item 1).
   Per Q8. It is read once per workout session by `ActiveWorkoutViewModel`
   (one-shot read of `observeCompletedSessions(false)`, re-read when a workout
   completes), not subscribed to history, so a logged set does not re-read and
   re-map the whole history (review O-11).
2. **Seed source (review I-2).** `WorkoutFocus` leaves composition whenever the
   user goes to the Board or uses Next (`ActiveWorkoutScreen`'s focus/board
   `if/else`), so `SetEntryState` (`rememberSaveable` per exercise) is rebuilt on
   every reopen. The seed for an **untouched** entry is therefore: this
   session's **last logged working set** of that exercise when it already has one
   (a warm-up-only exercise falls back to its last set), otherwise Q8's
   `LastPerformance`. An entry is "untouched" until the user types or steps a
   value. **The flag is saved state (review N-1):** `SetEntryState` gains
   `touched`, set by typing or stepping and never by applying the seed, and
   `SetEntryState.Saver` persists it (an eighth saved item), so a rotation or
   process death cannot turn a typed entry back into an untouched one. The seed is applied **whenever the entry is untouched and the data is
   present, including data that arrives after focus opened** (the one-shot read
   may emit late), never over a value the user entered. `Last time: 80 kg × 8`
   shows only before the first set of this session. Where the seed is computed
   (review P-1): the `ActiveWorkoutViewModel` computes it and hands it to
   `WorkoutFocus` precomputed on `ActiveExerciseUi` (this session's last logged
   working set, else `LastPerformance`), so the composable only applies it to an
   untouched entry. After `Log set`,
   `SetEntryState.clear()` becomes `clearAfterSet()`: weight, reps and seconds
   stay; RPE, pain, technique and the warm-up flag clear (the design's rule).
   `clearAfterSet()` leaves `touched` unchanged. The saver changes only by the one
   saved flag.
3. Tests: JVM `LastPerformanceTest` (most recent session wins; warm-up and
   empty sessions skipped; invalidated skipped; per tracking type),
   `ActiveWorkoutViewModelTest` (seed source: this session's last working set,
   else last performance; warm-up-only fallback), and `ActiveWorkoutScreenTest`:
   `tappingAddSetClearsTheEntryFields` is **rewritten** to
   `tappingLogSetKeepsWeightAndRepsAndClearsTheRest` (its subject changes, so
   it is named here rather than deleted), plus seeded-from-last-session,
   no-history-stays-empty, **late-arriving last performance still seeds an
   untouched entry and never overwrites a typed one**, and a **reopen** case:
   log a set at a changed weight, go to the Board (and, separately, use Next and
   come back), reopen the exercise and assert the steppers show this session's
   numbers, not last session's. **State restoration (review N-1):** with
   `StateRestorationTester`, type a weight and reps with the seed present,
   recreate, and assert the typed values stay and are not replaced by the seed;
   and the late-read-after-process-death case: type, recreate with the seed
   absent, then deliver the seed and assert the typed values still stay (an
   untouched entry, by contrast, still takes the late seed). A `SetEntryState`
   saver round trip also asserts the flag. The reopen case needs a small
   **stateful wrapper** in the harness (review P-1): `ActiveWorkoutScreenTest.setContent`
   is stateless today (`focusedExerciseId` fixed, `onFocusExercise` a no-op,
   `onRecordSet` not appending to `exercise.sets`), so the wrapper holds the focus
   and appends the recorded set. Both layers are needed: the ViewModel test
   catches a wrong seed source, the screen case the composition half (a reopened
   untouched entry takes the current seed). The other 30 existing `ActiveWorkoutScreenTest`
   methods stay untouched except for fixture edits.

### CP10 — Verification, register and decision records

1. Full gate: `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
   assembleDebugAndroidTest` and the whole instrumented suite on the AVD
   (`RepFlow_S24Ultra_384dp_API36`). No phone.
2. Side-by-side pass of `5b`, `5c`, `5d`, `2c` against the AVD at 384dp and at
   large font scale; each divergence is a register row.
3. `docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`:
   new rows from **D115** (next free id) for every deviation named above (no
   `Weight unit`; Backup cut per Q5; chart empty and one-point states; date
   ranges replacing the 12-session window; metric buttons' 44dp hit area; the
   Q6 scoping; seed rule per Q8); `D60` marked **superseded by B3** (user
   decision, 2026-10-03), `D110` marked **superseded** by date ranges, and
   `D103` (inline Data rows, the Backup route), `D104` (restore subtitle and file
   sheet), `D108` (chips 44 tall, now a picker) and `D111` (bar colours, now a
   line chart) marked superseded or amended as `5b`/`5c`/`5d` change them (review
   O-7); the theme first-frame flash (CP6) gets a row; `D63` (its "Today" column states
   the old rule, the exercise's planned rest else the 90 s default) is marked
   **amended** by Q9, with a row for the new precedence; `5b`'s drawn
   `Most reps at 80 kg — 8` record becomes `Best est. 1RM — <value>` in its own
   register row (separate from the Q6 scoping); the `Best est. 1RM` empty-state
   `—` (CP2/CP3) gets a row too; `O11` and `O12` marked
   closed with their built rows. Never renumber.
4. `docs/UX_FLOWS.md` updated for Settings, Backup, Progress and set entry
   (protected path); `docs/DOMAIN_GLOSSARY.md` only if a term is added
   (`Records`, `Training frequency`).
5. Records the out-of-scope neighbour seen: the `Last backup` setting is not in
   the backup file. (`Erase all data` and `Restore` cancelling notification 1001
   are now **in scope**, CP1 item 2.) The declined "Save bar above the keyboard"
   change is recorded as out of scope.
6. Re-run every instrumented test that CP1–CP9 named, with each rewritten
   test failing on the pre-change code and passing on the new (the parent's
   discipline for rewritten tests).

## Requirements traceability

| Requirement | Checkpoints |
|---|---|
| B1 | CP5, CP6, CP7, CP8 |
| B2 | CP2, CP3 |
| B3 | CP9 |
| B4 | CP4 |
| B5 | CP4 |
| B6 | CP2 |
| B7 | CP1 |
| REG | CP10 |

## Migration and schema impact

Room 8 → 9, `MIGRATION_8_9`, additive columns on the existing single-row
`settings` table (CP5). Enum columns store stable strings. No destructive
migration, no change to any training-data table, no ordinal persistence. The
migration test, `9.json`, and the 1 → 9 chain are CP5's. All other checkpoints
are presentation plus derived read models over existing rows.

## Backup compatibility impact

None. Preferences, including the new ones and `last_backup_at`, are device
settings outside the backup snapshot; `BackupSnapshot`, `BackupJsonMapper` and
the transfer schema version are unchanged, so backups from before this item
restore as before. CP5 extends the existing restore/erase tests to assert the
new columns are left alone.

## Open decisions this plan touches (`docs/TECHNICAL_DECISIONS.md`)

None is finalized by this plan; Q1-Q9 above are product decisions, not entries in
that file. Notification behaviour when permission is denied is
unchanged (CP1 only cancels). "Final Room entity schema" stays open: CP5 is one
more additive migration under ADR-0002.

## Areas the reviewer should specifically challenge

1. Q1-Q9 are decided by the user; check only that each is implemented as
   stated.
2. Replacing `D60`: is "keep weight and reps" safe enough with `Undo last`
   (decided by the user; a one-frame guard against a double `Log set` while the
   record call is in flight was suggested by the local review and is **not**
   adopted: out of scope for B3)?
3. The shared search field's `BasicTextField` approach versus keeping the
   Material field with an explicit `height` and reduced `contentPadding`.
4. Date-based ranges (Q7) over the parent's session window.
5. CP7's size: Settings plus the Archived screen in one checkpoint.

## Plan-review dispositions (round 1, LOCAL_MODEL_PLAN_REVIEW, REVISE: 0 Blocking, 4 Important, 14 Optional)

Plan revision 2. Each finding was validated against the repository before it was
applied (`DatabaseModule.kt` lists migrations by hand; `SettingsViewModel`
`onEraseAllDataConfirmed` and `BackupViewModel.onRestoreConfirmed` discard the
session; `WorkoutFocus.kt:100` keys `SetEntryState` per exercise inside a
focus/board `if/else`; `ActiveWorkoutScreenTest` has 31 `@Test` methods).

- **I-1 accepted.** Open questions became "Product decisions (user, 2026-10-03)"; Q1-Q9 stated as facts, no conditional "if Q5 is accepted" remains, the declined Save-bar change is recorded as out of scope.
- **I-2 accepted.** CP9 item 2: seed = this session's last logged working set, else Q8; applies whenever untouched and data present (late data); `Last time:` only before the first set; reopen and late-data tests added.
- **I-3 accepted.** CP1 now also cancels notification 1001 on successful Erase all data and Restore, with JVM tests; CP10 item 5 no longer lists it as out of scope.
- **I-4 accepted.** CP5: one shared `ALL_MIGRATIONS` used by `DatabaseModule` and the 1 → 9 chain test, plus a test binding it to the production registration.
- **O-1 accepted.** `INSERT_DEFAULT_SETTINGS_ROW_SQL` stays byte-for-byte unchanged.
- **O-2 accepted.** `last_backup_at` written for `BackupExportKind.BACKUP` only; CSV-success test added.
- **O-3 accepted.** Flash accepted and registered (no splash dependency); explicit `SystemBarStyle` for the bars.
- **O-4 resolved by the user.** Q6 amendment: second record is `Best est. 1RM`.
- **O-5 accepted.** One "trained" rule stated in CP2 item 1 (B6 ordering counts any set; Q6 and CP9 count working sets); `name`/`trackingType` from the last counted occurrence.
- **O-6 accepted.** Test counts corrected (31 and 7); rewritten methods named; the workout picker has no search test today, so CP4 adds one.
- **O-7 accepted.** CP10 register rows extended (`D103`, `D104`, `D108`, `D111`; `D110` superseded); the two omitted `5d` parts added to Q5's list.
- **O-8 accepted.** The search field grows above 48dp only when the font scale needs it; test asserts no clipping.
- **O-9 accepted.** Nearest-point hit testing replaces 24dp strips.
- **O-10 accepted.** The live-artboard re-read is restated in CP4, CP7, CP8 and CP9.
- **O-11 accepted.** One-shot per-workout read in `application/workout/LastPerformance.kt`.
- **O-12 accepted.** Unknown enum strings decode to the default.
- **O-13 accepted.** Month-end clamp and exclusive bound pinned in CP2.
- **O-14 rejected.** CP7 stays one checkpoint (session_target 2, as the plan's own challenge item 5 already flags): the Archived screen is a small list screen over existing use cases, and splitting it would add a checkpoint, registry and mapping churn for no review benefit. If the implementation review finds it too large, the split is a revision then.

## Plan-review dispositions (round 2, LOCAL_MODEL_PLAN_REVIEW, REVISE: 0 Blocking, 2 Important, 5 Optional)

Plan revision 3. Validated against the repository: `SetEntryState.Saver`
(`WorkoutFocusEntry.kt`) saves only the seven field values; `DatabaseModule.provideRepFlowDatabase`
registers `MIGRATION_1_2`..`MIGRATION_7_8` by hand inline, with no extracted builder.

- **N-1 accepted.** CP9 item 2: the untouched flag is saved state in `SetEntryState.Saver` (set by typing or stepping, never by applying the seed); "the saver is unchanged" removed. CP9 item 3 adds `StateRestorationTester` cases (typed value survives with the seed present; late read after process death).
- **N-2 accepted.** CP5 items 1 and 4: `DatabaseModule.buildRepFlowDatabase(context, name)` extracted and used by the Hilt provider; a test creates a v8 database with `MigrationTestHelper`, opens it through that builder and asserts the new settings defaults, so omitting `MIGRATION_8_9` fails. The contents check reads its end version from `@Database(version)`.
- **P-1 accepted.** CP9: stateful harness wrapper named; the seed is computed by `ActiveWorkoutViewModel` and carried precomputed on `ActiveExerciseUi`; both the ViewModel and screen layers are tested.
- **P-2 accepted.** CP6 item 4: the displayed rest (`restSecondsAfterSet` now takes the exercise default and app default) and the mid-workout-added exercise are tested; the app default enters the UI state from the ViewModel's `_settings`.
- **P-3 accepted.** CP10 item 3: `D63` named as amended; `5b`'s record row change and the `Best est. 1RM` empty state get their own register rows.
- **P-4 accepted.** CP2 item 3 and CP3 item 4: with no eligible set `Best est. 1RM` shows `—` (consistent with the chart's `Est. 1RM needs a set of 12 reps or fewer.`), registered and tested.
- **P-5 accepted.** CP3 item 4 reworded: three methods are rewritten; the bar-chart semantics are not a fourth item.

## Plan-review dispositions (round 1, MANUAL_EXTERNAL_PLAN_REVIEW, REVISE: 0 Blocking, 2 Important, 0 Optional; plus local round-3 Optional O-1 to O-4)

Plan revision 4. Validated against the repository: `RepFlowMigrations.kt`
(`INSERT_DEFAULT_SETTINGS_ROW_SQL` seeds `keep_screen_awake = 0`, and
`SETTINGS_SEED_CALLBACK` inserts the same row on a fresh database, so a bypassed
migration reads identically unless a non-default value is seeded); Room 2.8.4's
`@Database` has binary retention; `ExerciseProgress.kt` caps the series at 12
points today, which `All` no longer does.

- **GX-I1 accepted.** CP5: the helper and `buildRepFlowDatabase` share `targetContext` and the file name `migration-8-9-registration-test`; the v8 row is seeded with `keep_screen_awake = 1` and the test asserts it survives next to the new defaults; the built database is closed; `RepFlowDatabase.VERSION` (a `const val`) replaces the reflective `@Database(version)` read, in the annotation and in the `ALL_MIGRATIONS` contents test. Folds in local O-1 and O-2.
- **GX-I2 accepted.** CP3 items 2 and 4: width-aware x-axis labels (stride from the measured label width, the point spacing and an 8dp minimum gap; first and last labels always shown), as the pure function `xLabelIndices`, with JVM tests and a dense `All` (60+ sessions) screen test at 360dp and 384dp asserting no overlap and the first and last labels shown. The prototype's "every third point" is not carried over.
- **O-3 (local round 3) accepted.** CP6 item 4: the receiver supplies the plan and exercise rest; `restSecondsAfterSet` takes the app default as its only parameter.
- **O-4 (local round 3) accepted.** CP9 item 2: `clearAfterSet()` leaves `touched` unchanged.
