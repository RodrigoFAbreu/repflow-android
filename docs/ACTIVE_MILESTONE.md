# Active Milestone

## `repflow-redesign-visual-foundation` — Functional review round 2: findings applied (2026-10-03, implementation revision 7 pending review)

Round 2 of the parent's functional review (32 PASS, 5 FAIL, 7 findings) was
applied in this item, not in a new child: every fix is a bounded change, so
`/apply-functional-review`'s bounded branch applies. The technical approval was
marked `STALE` first (`e289f2c`), and a fresh implementation-review round is
required before the functional gate is reachable again. The children
`-remediation-1` and `-remediation-1-remediation-1` stay `MILESTONE_COMPLETE`.

| Finding | Class | Disposition | Commit |
|---|---|---|---|
| P2-F-1 History Back resets the list | defect | Fixed: the list's `LazyListState` is held above the detail; instrumented regression `backFromDetailKeepsTheListScrollPosition` (failed before, passes after) | `400d4c0` |
| P2-F-5 `Rest done` strip shows dead buttons | defect | Fixed: the done strip is its own layout (check, copy, 44dp X); regression `theDoneRestStripOffersOnlyTheDismissX` (`D164`) | `c8246b6` |
| P2-F-2 rest strip buttons wrap/clip at large font | defect | Fixed: a `FlowRow` of content-sized buttons; regression at 1.3 and 2.0 (`D163`) | `8e4fbd9` |
| P2-F-3 resume card buttons break mid-word | defect | Fixed: actions stack above font scale 1.1; regression at 1.3 and 2.0 (`D163`) | `8ec9d18` |
| P2-F-6 empty Recovery card shows two Log entries | defect | Fixed: header link hidden while empty, kept with an entry; two regressions (`D165`) | `c6c8e85` |
| P2-F-4 `VOLUME (KG)` truncated at large font | defect | Fixed: the stat tile caption wraps and the row takes the tallest tile's height; regression at 1.3 and 2.0, failed before (`D163`) | `4e51142` |
| P2-F-7 14 newer-design differences | enhancement | Not built, per the user's decision: registered as `D149`-`D162` for the post-PR follow-up item | this commit |

Also: `5af2c90` moves the done strip into `RestDoneStrip.kt` (detekt
`TooManyFunctions`), and `66e02fc` points the nav smoke test at `Log recovery`
(the header link is gone while the card is empty).

**Re-test (round 3, AVD only):** C2 (History Back keeps position), B7 (rest
strip running and the done strip), A1 (resume card, empty Recovery card), G2
and G3 (font 1.3 and 2.0: rest strip, resume card, History detail tiles).

### Implementation review of revision 7 — applied (implementation revision 8 pending review)

The external review (REVISE: 0 Blocking, 2 Important, 0 Optional) and the local
review (1 Important, 4 Optional) found no production defect beyond one height
regression; the rest were weak regression tests.

| Finding | Disposition | Commit |
|---|---|---|
| Local I1 `Skip rest` 56dp vs 44dp nudges at font 1.0 | Fixed: `RepFlowPrimaryButton(minHeight = ...)`, the strip passes its 44dp floor; test at 384dp / font 1.0 asserts same top and equal heights (failed before) | `0db4043` |
| PX-I1 label test could pass while clipped | Fixed: shared `assertButtonLabelWhole` (one line, no ellipsis, every character, inside text box and button), one test per scale; a temporary one-line clipped label failed it at 1.3 and 2.0 | `0db4043` |
| PX-I2 Home test gave the card 384dp | Fixed: renders `HomeScreen` at 384dp (352dp card), one test per scale; with the threshold raised to 1.5 both fail | `4c4cd85` |
| Local O1-O4 | Postponed to a later milestone (user rule) | n/a |

### Implementation review of revision 8 — applied (implementation revision 9 pending review)

The external review (REVISE: 0 Blocking, 1 Important, 0 new Optional) found no
production defect; the local review approved the same bundle with one Optional
about the same helper. Test-only fix.

| Finding | Disposition | Commit |
|---|---|---|
| PX2-I1 `assertButtonLabelWhole` accepted a vertically clipped label and compared already-clipped `boundsInRoot` | Fixed: the line bottom must fit the text node's height, and containment uses unclipped bounds (`positionInRoot` + `size`). With the three rest strip buttons temporarily forced to a fixed 44dp height the label tests failed at 1.3 and 2.0 (`-15s` line bottom 84px and 115px against a 78px text box); the real layout passes (ActiveWorkoutScreenTest + HomeScreenTest, 46 tests, 0 failed). Mutation reverted | `e2c37b5` |
| O-r8-1 helper comment inexact | Folded in: the comment now states what each check catches | `e2c37b5` |
| Local O1-O4 | Still postponed (user rule) | n/a |

## `repflow-redesign-visual-foundation` — Functional review checklist, ROUND 2 (parent, final end-to-end acceptance; implementation revision 6)

The parent's new functional review. Round 1 (finding F1, "screens were
reskinned, not converted to the Claude Design layouts") was routed to
`repflow-redesign-visual-foundation-remediation-1` (functional round 4 clean,
awaiting acceptance) and its child
`repflow-redesign-visual-foundation-remediation-1-remediation-1` (Settings
`5c`, Archived, Backup `5d`, Progress `5b`, set carry-over, glyphs, 48dp
search; MILESTONE_COMPLETE, commit `31a4b11`). This round is **one coherent
walk through the whole redesign as it stands now**, against the live Claude
Design project `Repflow mobile app design` (file `RepFlow.dc.html`). Newest
turn wins: turn 9 redraws the older artboards to the built decisions. Detail
per area is in the children's checklists above (R4 of
`...-remediation-1`, R2 of `...-remediation-1-remediation-1`); this round does
not repeat every step.

Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md` headed "round 2,
repflow-redesign-visual-foundation". An older file of that name there belongs
to the grandchild's consumed round 1: overwrite it. Every command names the id
explicitly.

**Tags.** Every step is **[AVD]**: the AVD `RepFlow_S24Ultra_384dp_API36`
(`emulator-5554`). **EMULATOR ONLY this round**: the user cannot connect the
phone, so phone-only feel checks (real vibration, sound, haptics, real
notification shade feel) are skipped and are not findings. WRITES marks steps
that write data.

### What does not count as a finding (already decided)

- The registered deviations (inventory D1-D165):
  `docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`.
- The follow-up list: number pad m:ss, permission-state Settings rows, loading
  placeholders, resume card in light theme, text contrast tiers (55%/70%),
  set-row index/warm-up layout, tinted "Just logged" bar, dimmed pending
  targets, stacked dialog order, Undo-archive date, an old "Rest done" staying
  during the next rest, chip checks in the exercise editor.
- User decisions:
  `/home/rodrigo/.local/share/claude-lanes/repflow-lane/design-turn7-guidance.md`.
- Weight unit not built (D5). Phone-only checks (vibration, sound).

A real finding is: a crash, wrong data, a flow that does not work, a screen
that visibly differs from the turn-9/newest artboard in a way not in the lists
above, clipped or unreachable content, or a regression.

### Setup and automated state

- `ANDROID_SERIAL=emulator-5554 ./gradlew installDebug` (Room 9). Restore the
  sample backup first, or seed: Bench Press (weight and reps) with at least
  eight sessions over four months, Pull Up (reps), Plank (timed), one archived
  exercise and one archived plan, two or more plans (one with enough rows to
  scroll), one exercise never done, one Recovery entry, one history workout with
  a recommendation. Also test one fresh install for empty states. Notifications
  and `Alarms & reminders` allowed.
- Automated state is current and not re-run: the working tree is clean and
  `git diff 91a6475 HEAD -- app` is empty (91a6475 is the last app change;
  everything since is docs and `WORKFLOW_STATE.json`). Last full gates are those
  recorded by the two children (grandchild: spotlessCheck, detekt, lintDebug 0
  errors, JVM tests and AVD instrumented 0 failures).

### A. Home and Plans

1. **[AVD] A1. Home.** Open Home (dark): layout against the Home artboard
   (greeting/header, resume card when a workout is active, start actions,
   recent activity). Nothing clipped; tap each entry and Back.
2. **[AVD] A2. Plans list.** Plans tab: list, archive action, empty state on a
   fresh install (glyph and copy).
3. **[AVD] A3. Plan editor (WRITES).** `New plan`: name, add exercises (picker),
   sets/reps steppers, reorder/remove a row, Save. Empty name shows `required`;
   a duplicate name is refused with the error; leaving with edits asks to
   discard. Reopen the saved plan: values persisted.

### B. Workout

4. **[AVD] B1. Start (WRITES).** Start a workout from the plan: the Board lists
   its exercises with targets.
5. **[AVD] B2. Board.** Rows show state (pending/done), set counts; add an
   exercise from the picker; a row opens focus mode.
6. **[AVD] B3. Focus mode, set entry.** Bench Press (history): weight and reps
   seeded from the last working set, `Last time: ...` line shown. Step weight and
   reps; `Log set` logs it; weight and reps stay for the next set while RPE,
   pain, technique and warm-up clear.
7. **[AVD] B4. Unlogged values survive.** Type values without logging, go to
   the Board and back (and rotate): values kept.
8. **[AVD] B5. Log set rules.** Never-done weight-and-reps exercise: `Log set`
   disabled until both have a value (0 kg counts); Pull Up needs only reps; Plank
   only the duration.
9. **[AVD] B6. Edit and undo a set.** Edit a logged set and delete/undo one;
   totals update.
10. **[AVD] B7. Rest strip.** After a logged set the rest strip runs; `-15s`,
    `+15s` and `Skip` work; a rest that ends shows `Rest done` once.
11. **[AVD] B8. Rest notification.** With the app in the background the rest
    notification posts and the ending alert arrives (no sound/vibration
    judgement).
12. **[AVD] B9. Previous/Next.** Move between exercises with Previous/Next:
    each keeps its own values.
13. **[AVD] B10. Leave.** Back or the leave action offers leave versus stay;
    leaving keeps the workout running (Home resume card).
14. **[AVD] B11. Abandon (WRITES).** Abandon a second test workout: confirm
    dialog, the workout is gone, the rest notification is cleared.
15. **[AVD] B12. Finish (WRITES).** Finish the first workout: confirm (unlogged
    exercises handled), rest notification cleared, the done screen shows
    totals and exercises; with enough history the recommendation appears and
    its accept/dismiss work.

### C. History and Recovery

16. **[AVD] C1. History list.** The finished workout is at the top; filters and
    empty state (fresh install) correct.
17. **[AVD] C2. History detail.** Opens with exercises and sets as logged,
    matching what was entered; Back returns to the list position.
18. **[AVD] C3. Recovery entry (WRITES).** Add a Recovery entry: required
    fields, Save; it appears in Recovery history.
19. **[AVD] C4. Recovery history.** Open an entry; edit it and delete (or undo)
    one; the list updates.

### D. Library and Progress

20. **[AVD] D1. Library.** Active/Archived filters, search (48dp field); a
    search with no match shows the magnifier copy and the `Create "..."` row
    (Active) or `No archived exercises match "..."` (Archived).
21. **[AVD] D2. Exercise editor (WRITES).** Create an exercise (type, muscle
    group, equipment); empty/duplicate name errors and focus behaviour as in the
    child's R4 (keyboard stays, no keystroke lost); Save persists; archive it,
    then restore it from Archived.
22. **[AVD] D3. Progress.** Progress for Bench Press: chart, `3m` `6m` `All`
    range pills, metric captions, span line, `Records · all time`, per-session
    list; a range or metric with no data shows the unavailable copy. An exercise
    with zero sets in a session is not counted.
23. **[AVD] D4. Progress empty.** Fresh install: Progress empty state.

### E. Settings, Archived and Backup

24. **[AVD] E1. Settings.** Groups in order `Appearance`, `Rest timer`,
    `During a workout`, `Your data`, then the Erase card and footer; nothing
    clipped; compare with the Settings artboard (`5c`/`8c`).
25. **[AVD] E2. Default rest (WRITES a setting).** Sheet: a preset saves and
    closes, `Other` opens the pad, a custom value shows as a selected chip.
    Restore `1:30`.
26. **[AVD] E3. Archived.** `Archived` row opens the screen: sections for
    exercises and plans, rows with `Archived <date>` and a 44dp Restore;
    restoring removes the row and shows `<name> restored · Undo`; both
    sections empty shows `Nothing archived...`.
27. **[AVD] E4. Backup (WRITES).** Backup screen: Export JSON stamps `Last
    backup today` (also on the Settings row subtitle); CSV export does not
    stamp. Restore from the exported file shows the confirm dialog and the data
    is intact afterwards.
28. **[AVD] E5. Erase (WRITES, last, or on a throwaway install).** The Erase
    card confirms in a dialog; cancelling changes nothing.

### F. Light and dark

29. **[AVD] F1. Dark pass.** Steps A1, B3, C2, D3, E1 already done in dark are
    on-spec: colours from the tokens, readable text, no stray light surfaces.
30. **[AVD] F2. Light pass.** Set the theme to Light (Settings > Appearance) and
    revisit Home, Plans, the Board, focus mode, rest strip, the done screen,
    History detail, Library, Progress, Settings, Archived and Backup. No
    unreadable text, invisible borders or dark leftovers (the resume card
    staying dark is a decided follow-up). Restore `System`.
31. **[AVD] F3. Dialogs and sheets in light and dark.** One dialog (Abandon) and
    one sheet (Default rest) in each theme: scrim, surfaces, buttons readable.

### G. 384dp and font scale

32. **[AVD] G1. 384dp width.** The AVD is 384dp: Home, Board, focus mode, Progress
    and Settings have no clipped or overlapping content and every action is
    reachable.
33. **[AVD] G2. Font scale 1.3.** Settings > Display > Font size set to
    1.3 (`adb shell settings put system font_scale 1.3`): repeat the Board,
    focus mode (set entry, `Log set`), the rest strip, History detail and
    Settings. Text is scaled, nothing is cut off, buttons keep their 44-56dp
    targets.
34. **[AVD] G3. Font scale 2.0.** `font_scale 2.0`: the same screens plus
    Progress and Backup. Content may scroll but `Log set`, Finish, Save and
    dialog buttons stay reachable; no text clipped to illegibility. Reset with
    `adb shell settings put system font_scale 1.0`.
35. **[AVD] G4. Landscape spot check.** Rotate on the focus screen and the
    Board: no crash, values kept, actions reachable.

### H. Regression and cleanup

36. **[AVD] H1. Process death.** With a workout running, force-stop the app
    (`adb shell am force-stop` with the app's package id, or App info); reopen: the active
    workout resumes with its logged sets.
37. **[AVD] H2. Cleanup.** Abandon leftover test workouts; restore Settings
    (theme `System`, rest `1:30`, font scale 1.0).

### Known limitations and out of scope

- Everything under "What does not count as a finding".
- No phone this round: real vibration, sound and haptic feel are not tested.
- Weight unit not built (D5).

### Expected result and what happens next

Every step behaves as stated, nothing crashes, no area regressed. If clean:
`/accept-milestone repflow-redesign-visual-foundation` is the only acceptance
command and needs every checkpoint in this item's own registry `COMPLETE`; an
outstanding checkpoint goes to `/milestone-implement`. The child
`repflow-redesign-visual-foundation-remediation-1` still awaits its own
acceptance. No command records acceptance of a partial round. Findings go to
`.ai-review/feedback/FUNCTIONAL_REVIEW.md`, then
`/apply-functional-review repflow-redesign-visual-foundation` (bounded branch
for a same-scope fix, broad branch for a `<parent-id>-remediation-<n>` child).

---

## In implementation: `repflow-redesign-visual-foundation-remediation-1-remediation-1`

Second remediation child (group B of the parent's functional review round 1).
Workflow v2.1; plan revision 4 (approval commit `79abb9d`). Plan:
`docs/milestones/repflow-redesign-visual-foundation-remediation-1-remediation-1-execution.md`;
registry `docs/ai-workflow/registry/repflow-redesign-visual-foundation-remediation-1-remediation-1-registry.json`
(CP1-CP10, array order). Every command names this id explicitly;
`active_work_item_id` still points at the top-level parent.

- **CP1 (B7) complete, 2026-10-03.** New seam
  `presentation/workout/RestNotificationCanceller` (`SystemRestNotificationCanceller`
  cancels notification id 1001 only; channel kept; Hilt `@Binds` in
  `RestNotificationModule`). `ActiveWorkoutViewModel` (finish and abandon),
  `HomeViewModel.onAbandonWorkout`, `SettingsViewModel.onEraseAllDataConfirmed`
  and `BackupViewModel.onRestoreConfirmed` call it after the use case returns
  `Success`; a failure leaves the notification. Tests: JVM
  `ActiveWorkoutRestNotificationTest` (new class, because
  `ActiveWorkoutViewModelTest` is at detekt's `LargeClass` limit),
  `HomeViewModelTest`, `SettingsViewModelTest`, `BackupViewModelTest` (restore
  success cancels; invalid backup and an `Unavailable` failure do not);
  instrumented `RestTimerExpiryHandlerTest.theRealCancellerClearsAPostedRestDoneNotification`.
  Gate: spotlessCheck, detekt, lintDebug (0 errors, 21 warnings, 1 hint),
  testDebugUnitTest (648 tests, 104 classes, 0 failures), assembleDebugAndroidTest
  all green; AVD run of `RestTimerExpiryHandlerTest`, `HomeRouteLifecycleTest`,
  `ActiveWorkoutLeaveRouteTest`, `ProgressionRecommendationRouteTest`:
  26 tests, 0 failures. CP1 builds no screen, so no design artboard was read.
- **CP2 (B6 and the B2 read model) complete, 2026-10-03.** Application layer
  only; no schema, no screen. `exerciseProgressOf` now counts an occurrence
  only when it has a set (B6): an exercise with 0 sets in a session is not
  "most recent" there, contributes no point or session, and one never counted
  is not listed; `name`, `trackingType` and `lastTrainedAt` come from the last
  counted occurrence. New `ProgressRange` (`THREE_MONTHS`/`SIX_MONTHS`/`ALL`,
  calendar months back with a month-end clamp, exclusive bound) replaces the
  12-session window (`D110` superseded): `ProgressSeries` is uncapped and
  `within(range, now, zone)` narrows it; `delta`, `deltaPercent` (whole
  percent, none from a 0 start), `best`, `first`, `latest` read the points it
  holds. `ExerciseProgress` carries `performances` (working sets per session);
  `ExerciseProgressDetail.kt` derives `sessionCount`, `averageRpe` (one
  decimal, null when none recorded), `weeklyFrequency` (8 Monday-start local
  weeks, average over all 8) and `records()` (`Loaded(heaviestSet, bestEstimatedOneRepMax?)`,
  `MostReps`, `LongestHold`; ties per Q6; Best est. 1RM is the chart's whole-kg
  Brzycki value and absent with no set of 12 reps or fewer). `xLabelIndices`
  is CP3's (it needs the chart). Bridge until CP3 replaces the card:
  `ProgressUiState.series` still hands the old bar card its last 12 points,
  so the screen is unchanged. Tests: new JVM `ProgressRangeTest`,
  `ExerciseProgressDetailTest`, `ExerciseProgressTrainedTest` (shared
  `ProgressFixtures`), and `ExerciseProgressTest` updated (the window test
  became an uncapped-series test). Gate: spotlessCheck, detekt, lintDebug
  (0 errors, 21 warnings, 1 hint), testDebugUnitTest (670 tests, 107 classes,
  0 failures), assembleDebugAndroidTest all green. CP2 builds no screen, so no
  device run; the `5b` artboard was re-read (matches the plan's summary).
- **CP3 (B2 Progress screen to `5b`) complete, 2026-10-03.** Presentation
  only; no schema. The live `5b` artboard (`RepFlow.dc.html`, id `5b`) was
  re-read first. Chips and the segmented control are replaced by the
  full-width exercise picker button (52, radius 10, barbell, caret) opening
  the `Track an exercise` `RepFlowSheet` (rows 56, chosen row tinted with a
  check-fat, scrolls at 60% of the window height) and three metric buttons (40
  at radius 8 inside a 44 touch target, check on the chosen one).
  `ProgressCard` is now the chart card: metric caption (`Heaviest working set`
  / `Most reps in a set` / `Longest set` / `Estimated 1RM` / `Volume per
  session`), the `3m` `6m` `All` pills (32 inside a 44 target, default `All`),
  the 30/500 value with `+10 kg · +14%` (no percent from a 0 start, no delta
  with one point), the live-region readout strip (`Latest · 26 May`, or the
  touched date), and `ProgressChart` (Compose Canvas, no dependency): 96 tall,
  y gridlines and labels at max/mid/min (one label for a flat series), 2.4
  line, area, dashed cursor, 4.5 dot; one accessibility node with a spoken
  summary; tap and horizontal drag pick the **nearest point**
  (`nearestPointIndex`). Date labels are width-aware (GX-I2): the widest label
  is measured and `xLabelIndices(pointCount, plotWidthPx, labelWidthPx, gapPx)`
  (`ProgressModel.kt`) picks the smallest stride that leaves at least
  label + 8dp, always drawing the first and last labels (the stride-aligned
  label nearest the last is the one dropped); labels carry the year only when
  the series spans years. `ProgressStatsSection` draws the `Sessions` / `Avg
  RPE` tiles (`RepFlowStatRow`, `—` with no RPE), `Training frequency` (8
  weekly bars, last in the accent, `8 weeks ago` / `this week`, `N sessions a
  week on average`, a spoken per-week summary) and `Records` (`Heaviest set —
  82.5 kg × 7`, `Best est. 1RM — 99 kg` or `Best est. 1RM — —` with no eligible
  set, `Most reps in a set`, `Longest hold`; each dated). `ProgressUiState`
  gains `range`, `now`, `zone`, the range-narrowed `series` and `stats`
  (the CP2 12-point bridge is gone); `ProgressViewModel` takes `SavedStateHandle`
  and `Clock`, keeps exercise, metric and range in the handle (an unknown
  stored metric or range reads the default), and stamps `now` on each history
  emission and range choice. Decided here, not drawn by the design (CP10
  registers): no session in the range shows the card's `progress_metric_empty`
  (reworded: `No sessions in this range yet.`) in place of value and chart with
  the pills kept; one session shows value, readout and a lone dot; the records
  second row uses `trend-up` (no new asset, the plan's optional `repeat` icon
  is not added); a pill's selected state is tint, ring and medium weight (no
  check, they would not fit beside the caption); the `Only valid sessions
  count` note stays under the sections (`5b` omits it); `progress_best` and the
  bar-chart helpers (`barHeightFractions`, `barMonthLabels`) are removed.
  Tests: JVM `ProgressModelTest` (scale, flat, `xLabelIndices` incl. 2 to 400
  points at 360 and 384dp widths, nearest point, point x, date, numbers),
  `ProgressViewModelTest` (range default and kept across exercises, narrowing,
  restored and garbled handle, an exercise leaving the list); instrumented
  `ProgressScreenTest` rewritten (13 tests; the three named methods rewritten,
  the four named kept and adjusted, new: pills change the headline, tap moves
  the readout, one-point, empty range, tiles/frequency/records, dense 70-session
  `All` labels disjoint by 8dp with first and last shown at 360 and 384dp).
  Gate: spotlessCheck, detekt, lintDebug (0 errors, no new warning in the
  progress files), testDebugUnitTest (678 tests, 0 failures),
  assembleDebugAndroidTest, assembleDebug all green. AVD
  (`RepFlow_S24Ultra_384dp_API36`, `emulator-5554`, stopped afterwards):
  `ProgressScreenTest` 13/13 and `MainActivityNavHostSmokeTest` 9/9 pass. The
  side-by-side visual pass against `5b` is CP10's.
- **CP4 (B5 and B4) complete, 2026-10-03.** Presentation only; no schema, no
  dependency. The `2c` artboard (search field 48 min, radius 10, glyph 18,
  14.5 text, hairline) was re-read first. New
  `designsystem/components/RepFlowSearchField`: a `BasicTextField` decorated by
  `OutlinedTextFieldDefaults.DecorationBox` (Material's 56dp minimum lives on
  `OutlinedTextField`, not on the decoration box), `heightIn(min = 48.dp)`
  with 8dp vertical content padding so it is exactly 48 at default font scale
  and grows only when a larger font needs it; hairline, magnifier, a clear
  button (48dp `IconButton`, only once there is text), IME action Search
  (hides the keyboard). `containerColor` defaults to `surface` (the Library,
  as before); the workout picker and the plan-editor picker pass `control`
  (the plan editor's was already `control`; the workout picker's was Material's
  transparent default and now matches the design's `#292b31`). Replaces the
  inline fields in `ExerciseListScreen`, `WorkoutSheets` and
  `TrainingPlanEditorPicker`; their `SearchFieldMinHeight`/`SearchMinHeight`
  constants are gone. The plan-editor picker gains the clear button (it had
  none), reusing `exercise_list_search_clear_content_description`. B4:
  `exerciseListEmptyIconRes` (barbell / archive / magnifying glass for
  `NO_EXERCISES` / `NO_ARCHIVED` / `NO_SEARCH_RESULTS`; the create-from-query
  footer is unchanged) and Recovery history's empty state passes `moonStars`.
  `RepFlowEmptyState`'s glyph carries `EMPTY_STATE_GLYPH_TAG`. Tests: new
  instrumented `RepFlowSearchFieldTest` (48dp exactly at 1x; 200% font scale
  grows, text layout fits the field; clear; IME Search); `ExerciseListScreenTest`
  (48dp, glyph for no-exercises, archived, and a no-results case on the
  archived filter where no footer shows), `TrainingPlanEditorScreenTest` (48dp),
  `ActiveWorkoutScreenTest` (new picker 48dp case), `RecoveryHistoryScreenTest`
  (glyph). Gate: spotlessCheck, detekt, lintDebug (no new issue),
  testDebugUnitTest (678 tests, 0 failures), assembleDebug,
  assembleDebugAndroidTest green. AVD (`emulator-5554`, stopped afterwards): the
  five classes above 86/86 and `MainActivityNavHostSmokeTest` 9/9 pass.
- **CP5 (Settings schema 8 to 9) complete, 2026-10-03.** The only schema change
  in this item. Room 8 to 9: explicit `MIGRATION_8_9` (four additive
  `ALTER TABLE settings ADD COLUMN`: `theme TEXT NOT NULL DEFAULT 'SYSTEM'`,
  `default_rest_seconds INTEGER NOT NULL DEFAULT 90`, `extra_set_fields TEXT NOT
  NULL DEFAULT 'COLLAPSED'`, nullable `last_backup_at INTEGER`); exported
  `9.json`; `RepFlowDatabase.VERSION = 9` used by `@Database`; one shared
  `ALL_MIGRATIONS` in `RepFlowMigrations.kt`; `DatabaseModule.buildRepFlowDatabase(
  context, name)` (no destructive fallback) used by the Hilt provider.
  `INSERT_DEFAULT_SETTINGS_ROW_SQL` is untouched; the new columns take their DDL
  defaults on the migration and fresh-install paths. `ThemeMode` and
  `ExtraSetFields` (`application/settings`, stable `storageValue` strings, unknown
  string reads the default), `AppSettings` (+`theme`, `defaultRestSeconds`,
  `extraSetFields`, `lastBackupAt: Instant?`), `SettingsEntity` and
  `LocalSettingsRepository` mapping. Backup snapshot/mapper unchanged; settings
  stay outside the backup. Nothing reads the new fields yet (CP6/CP7/CP8). Tests
  (instrumented, AVD): `RepFlowDatabaseMigrationTest` (8 to 9 keeps the switches
  and gives the defaults; 7 to 9; full 1 to 9 chain through `ALL_MIGRATIONS`;
  contents test against `RepFlowDatabase.VERSION`; production-registration test
  opening a real v8 file `migration-8-9-registration-test`, seeded with
  `keep_screen_awake = 1`, through `buildRepFlowDatabase` on `targetContext`),
  `SettingsPersistenceTest` (fresh-install DDL defaults, round trips and stable
  strings, unknown enum string, absent row, erase/restore leave the new columns
  because `customised` now uses non-default values for all of them).
  `LocalBackupRepositoryAtomicityTest` needed no change (it never touches
  settings). No design artboard read: CP5 builds no screen. Gate:
  spotlessCheck, detekt, lintDebug, testDebugUnitTest (678 tests, 0 failures),
  assembleDebugAndroidTest green. AVD (`emulator-5580`, stopped afterwards): the
  three classes above, 30 tests, 0 failures.
- **CP6 (apply the new preferences) complete, 2026-10-03.** Presentation only;
  no schema. **Theme:** `RepFlowTheme(themeMode)` picks the scheme through the
  pure `isDarkTheme(mode, systemDark)`; `MainActivity` observes
  `SettingsRepository` (injected) and re-applies `enableEdgeToEdge` with an
  explicit `SystemBarStyle` (transparent status bar, the framework's own
  navigation scrims) keyed on the applied scheme. Until the stored mode loads
  the first frame follows the system: the accepted, registered cold-start flash
  (CP10 row; no splash dependency). **Default rest (Q9):** one shared
  `resolveRestSeconds(plan, exercise, app)` (`WorkoutFocusModel.kt`); both
  `ActiveWorkoutViewModel.onRecordSet` (app default read from settings when the
  set is logged, so a change applies to the next set) and the warm-up hint
  (`restSecondsAfterSet(appDefault)`, now taking the app default) use it.
  `ActiveExerciseUi.defaultRestSeconds` carries the exercise's own `Default
  rest`; `ActiveWorkoutContent.Active` carries `appDefaultRestSeconds` and
  `extraSetFields`, put there by the ViewModel from the settings it observes.
  An ad-hoc or mid-workout exercise therefore honours its own `Default rest`;
  `DEFAULT_REST_TIMER_SECONDS` remains only as the setting's default and
  `StartRestTimer`'s own default parameter. **Extra set fields (Q2):**
  `COLLAPSED` the existing disclosure, `ALWAYS_SHOWN` the three rows with no
  disclosure (`SetDetailSection(showHeader = false)`), `OFF` nothing drawn and
  no RPE/pain/technique submitted; the warm-up chip and the corrections sheet
  are unchanged. Tests: JVM `ActiveWorkoutRestPrecedenceTest` (new; ad-hoc with
  and without an exercise rest, a changed app default applies to the next set,
  the UI state carries the three values), `WorkoutFocusModelTest` (the hint's
  three-way precedence; the shared resolver), `ThemeSelectionTest`; the plan-row
  over app default case was already `ActiveWorkoutViewModelTest`'s. Instrumented
  `ActiveWorkoutPreferencesTest` (new: three modes, hint precedence) and
  `MainActivityThemeSmokeTest` (new: boots in Dark, Light and System; status-bar
  icon appearance follows the applied scheme, theme restored to System). No
  design artboard was read (CP6 builds no screen; design turn 7 does not touch
  it). Gate: spotlessCheck, detekt, lintDebug (0 errors, 21 warnings, 1 hint),
  testDebugUnitTest (684 tests, 109 classes, 0 failures), assembleDebug,
  assembleDebugAndroidTest green. AVD (`emulator-5554`, stopped afterwards):
  `ActiveWorkoutPreferencesTest`, `MainActivityThemeSmokeTest`,
  `ActiveWorkoutScreenTest`, `MainActivityNavHostSmokeTest`: 50 tests, 0
  failures. Not built, by the plan: the Settings controls that change these
  values are CP7's.
- **CP7 (B1 Settings screen to `5c`, and the Archived screen) complete,
  2026-10-03.** Presentation only; no schema, no dependency. The live `5c`
  artboard and design turn 7 (`7c` N1, N2, N3) were read first; the turn-7
  guidance applied only inside this scope (B2 Default rest sheet, B5 Extra set
  fields sheet, B8 Archived screen); B3, B6, B7 (row label stays `Archived
  exercises and plans`), B22 (notification subtitle stays `Shows when rest
  ends`) are kept ours, for CP10's deviation register. **Settings:** groups
  `Library` (`Exercise library`, `D25`), `Units and appearance` (`Theme` as
  three full-width 44dp segments, `System`/`Light`/`Dark`; `Weight unit` not
  built, `D5`), `Rest timer` (`Start rest automatically`, the `Default rest`
  value row `1:30 ›`, `Vibrate when rest ends`, `Notify when rest ends`),
  `During a workout` (`Keep the screen on`, `Confirm before finishing`, the
  `Extra set fields` value row `Collapsed ›`), `Data` (`Archived exercises and
  plans ›` plus, **until CP8 replaces them with `Backup and restore ›`, the
  three existing backup rows**: the Backup route is CP8's, so CP7 cannot link
  to it and removing the rows would lose export/restore for a checkpoint), the
  `Irreversible` card, the footer. Titles follow `5c`; the subtitles the design
  does not draw are kept ours. `SettingsViewModel` gains `onThemeSelected`,
  `onDefaultRestSelected` (clamped to the exercise rest's 1-1800 s) and
  `onExtraSetFieldsSelected`, each a write-through with `SAVE_FAILED`. **Sheets**
  (`RepFlowSheet`, new `SettingsChoices.kt`): `Default rest` has the caption
  `Used when neither the plan nor the exercise sets a rest.`, chips `1:00 1:30
  2:00 3:00`, a preset saves and closes, `Other` opens the existing numeric
  keypad unchanged (whole seconds, titled `Rest in seconds`; B3 stays ours, so
  not m:ss), and a non-preset value stands in `Other`'s place as a selected
  chip that reopens the keypad; `Extra set fields` is a radio list with the
  three explanation lines (a pick saves and closes). **Archived screen:** new
  `presentation/archived/` (route `settings/archived`, reached only from
  Settings): `ArchivedViewModel` over `ObserveExercises(ARCHIVED)` and
  `ObserveTrainingPlans(ARCHIVED)`, `RestoreExercise`/`RestoreTrainingPlan` and
  the archive use cases for `Undo`; sections `Exercises` and `Plans`, rows name
  over `Archived d MMM yyyy` (the plan card's own date format; the design draws
  `12 Sep`) with an outlined 44dp `Restore` (accessible name `Restore <name>`),
  most recently archived first, the restored row leaves at once (the
  observation re-emits), snackbar `<name> restored` with `Undo` (re-archives;
  reuses the library's snackbar card), an inline empty row per section (`No
  archived exercises.` / `No archived plans.`), both empty one `Nothing
  archived. Archived exercises and plans show up here.` with the archive glyph,
  a failure state with retry. `ExerciseListSnackbar` became `internal` to be
  reused. Decided here, registered by CP10: the `Other` keypad clamps 0 or a
  value over 1800 into 1-1800 rather than rejecting it; the Data group keeps the
  three backup rows until CP8. Tests: JVM `SettingsViewModelTest` (+6: theme,
  default rest, clamp, extra set fields, each failed write reports
  `SAVE_FAILED`, `formatRest`), new `ArchivedViewModelTest` (7); instrumented
  `SettingsScreenTest` rewritten for the grouped layout (14 tests: group order,
  theme, default rest row and preset, `Other` keypad, custom chip, extra set
  fields sheet, disabled until loaded, Archived row, the three backup rows kept,
  plus the kept switch, library, erase and message tests), new
  `ArchivedScreenTest` (6), `MainActivityNavHostSmokeTest` (+1: Settings,
  Archived, back). Gate: spotlessCheck, detekt, lintDebug (0 errors, 21
  warnings, 1 hint), testDebugUnitTest (697 tests, 110 classes, 0 failures),
  assembleDebug, assembleDebugAndroidTest green. AVD (`emulator-5560`, stopped
  afterwards): `SettingsScreenTest`, `ArchivedScreenTest`,
  `MainActivityNavHostSmokeTest` 30/30 after the last change; earlier in the same
  session (32 tests, 0 failures) those three plus `RestTimerReceiverDeliveryTest`,
  and `BackupRouteUnreadableRestoreFileTest`, `BackupRouteSafCancellationTest` and
  `MainActivityThemeSmokeTest` (5 tests, 0 failures), which also use the Settings
  screen. The side-by-side visual pass against `5c` is CP10's.
- **CP8 (B1 dedicated Backup screen, `5d`) complete, 2026-10-03.**
  Presentation plus one write of CP5's `last_backup_at`; no schema change, no
  dependency. The live `5d` artboard and the designer's newer **turn 8** (`8c`,
  "replaces 5c and 5d", added after the turn-7 guidance) were read first; `8c`'s
  Backup screen matches Q5 exactly (it drops the same recent-files, `Share`,
  safety-snapshot, size/schema, in-app file chooser and `Undo` items), so the
  layout follows it: title `Backup and restore`; a hero card (shield-check glyph,
  `Last backup <today | yesterday | N days ago>` over `d MMM, HH:mm`, or the
  shield-warning glyph, `No backup yet` over `Your data lives only on this phone`
  before the first), the 56dp primary `Export backup now` (`Export your first
  backup` before the first) and the note `One file with every exercise, plan,
  workout, recovery entry and suggestion. You choose where it's saved. RepFlow
  never uploads it.`; `Export for other tools` with a 52dp `Workout history as
  CSV` row; the `Replaces everything` card (destructive ring, the `Restoring
  replaces all data on this phone ...` note, outlined `Restore from a backup`).
  New `presentation/backup/`: `BackupScreen`, `BackupScreenActions`,
  `BackupRoute` (route `settings/backup`, `RepFlowDestinations.BACKUP`, reached
  only from Settings), and the pure `lastBackupLabel` (calendar days in the
  device zone). Four Phosphor glyphs added (`shield-check` fill, `shield-warning`,
  `download-simple`, `upload-simple`; upstream path data, `RepFlowIconsTest`'s
  enumerated set extended). **Settings:** the three backup rows and `Saved`
  leave; the `Data` group is `Archived exercises and plans ›` and `Backup and
  restore ›` (the `database` glyph, no subtitle), and `SettingsScreen`/`Route`
  no longer take the backup state or `BackupViewModel` (`SettingsActions` drops
  six callbacks, gains `onBackupClick`; unused `SettingsActionRow` `enabled` and
  `trailing` parameters removed). **`BackupViewModel`** now takes
  `SettingsRepository` and `Clock`: it observes `lastBackupAt` into
  `BackupUiState` (`lastBackupAt`, `isLastBackupLoaded`, `now`) and writes
  `lastBackupAt = clock.now()` after a successful **`BackupExportKind.BACKUP`**
  export only, never a CSV export, a cancel or a failure; a failed write is
  dropped (the export still reports success). Restore is unchanged: the system
  picker, the existing destructive confirmation (`BackupRestoreConfirmDialog`,
  our dialog text kept per the guidance, B10) and today's `Backup restored`
  snackbar. An export's success is the hero's own `Saved` mark (moved from
  Settings' row). Registered deviations (Q5, for CP10): no recent-files list, no
  `Share`, no safety-snapshot row, no file metadata, no restore `Undo` toast, no
  restore file sheet with a preview, no `Backup restored · N workouts` toast
  copy; also not built: `8c`'s `Last backup <when>` subtitle on Settings'
  `Backup and restore` row and its `Your data` regrouping (CP7's committed
  Settings layout stands; follow-up). Tests: JVM `BackupViewModelTest` (+6: stamp
  written on a backup success, hero starts from the stored value, not on a CSV
  success, not on a cancel or failure, a failed stamp write does not fail the
  export, a restore leaves it alone), new `LastBackupLabelTest` (4); instrumented
  new `BackupScreenTest` (8: no-backup and backup hero, today, the three actions,
  disabled while busy, restore confirmation still required, confirm, `Saved` and
  snackbars), `SettingsScreenTest` (the three-backup-rows test replaced by the
  `Backup and restore` row test; the message test no longer carries backup
  state), `BackupRouteSafCancellationTest` and `BackupRouteUnreadableRestoreFileTest`
  re-pointed Settings -> `Backup and restore` -> the screen's button,
  `MainActivityNavHostSmokeTest` (Settings, Backup, back; bottom nav absent).
  Gate: spotlessCheck, detekt, lintDebug (0 errors, 22 warnings, 1 hint; the new
  one is `PluralsCandidate` on `backup_last_days_ago`, the same category as four
  existing strings), testDebugUnitTest (707 tests, 111 classes, 0 failures),
  assembleDebug, assembleDebugAndroidTest green. AVD (`emulator-5560`, stopped
  afterwards): `BackupScreenTest`, `BackupRouteSafCancellationTest`,
  `BackupRouteUnreadableRestoreFileTest`, `SettingsScreenTest`,
  `MainActivityNavHostSmokeTest` 34/34. Not visually compared side by side with
  `8c`: that is CP10's.
- **CP9 (B3 set entry keeps its numbers and seeds from the last session) complete,
  2026-10-03.** Replaces `D60` (CP10 records the accidental-double-submit
  trade-off; `Undo last`, `Last:` and the correction sheet remain). Live `8d`
  (set entry) and turn 9 re-read first; `4a`'s `Last time` line was already
  in the live file. New `application/workout/LastPerformance.kt`:
  `LastPerformance(load, reps, durationSeconds, date)` and the pure
  `lastPerformancesOf(sessions)`: per exercise, the last **working** set of the
  most recent valid (completed, not invalidated) session with at least one
  working set of it; warm-up-only, empty and invalidated sessions are passed
  over. `ActiveWorkoutViewModel` takes `WorkoutRepository` (new constructor
  argument, six construction sites updated), reads it once at creation and
  again when a finish succeeds, not subscribed to history; a failed read
  leaves the steppers empty. It carries on `ActiveExerciseUi` the
  `lastPerformance` and a precomputed `seed: SetEntrySeed?`
  (`entrySeedOf`: this session's last working set, a warm-up-only exercise
  falling back to its last set, else `LastPerformance`; only the fields the
  tracking type records; never the plan). `SetEntryState`: `load`, `reps` and
  `seconds` are set through `enterLoad`/`enterReps`/`enterSeconds` (which set
  `touched`), `applySeed` fills an untouched entry and never marks it touched,
  `clearAfterSet()` (replacing `clear()`) leaves weight, reps and seconds and
  clears RPE, pain, technique and warm-up, `Saver` persists `touched` as an
  eighth item. `WorkoutFocus` seeds in the first frame and re-applies on a
  changed seed (late data), so a reopen, a late read and a recreation all
  behave per the plan. `Last time: 80 kg × 8` (colon, `4a`'s wording;
  `8d` draws a middle dot, a register row for CP10) shows only before this
  session's first set. `Log set` is disabled until the value the domain
  requires is present (reps; seconds for a timed exercise); weight stays
  optional because `WorkoutSet` accepts a loadless weight-and-reps set (`8d`
  would also require weight: open question, below). Tests: JVM new
  `LastPerformanceTest` (7), `ActiveWorkoutSeedTest` (6: history seed, never
  done, this session's set wins, warm-up-only fallback, re-read after a finish,
  per-type fields; new class because `ActiveWorkoutViewModelTest` is at
  detekt's `LargeClass` limit), `SetEntryStateTest` (7, incl. saver round trip
  with the flag); instrumented new `ActiveWorkoutSetEntryTest` (stateful
  harness: seeded and `Last time`, never-done empty and `Log set` disabled,
  `Last time` gives way to `Last:`, late seed fills untouched and never a typed
  entry, reopen via the Board and via Next shows this session's numbers,
  `StateRestorationTester` typed-stays, typed-stays-with-late-seed and
  untouched-takes-late-seed), `ActiveWorkoutScreenTest`
  `tappingAddSetClearsTheEntryFields` rewritten to
  `tappingLogSetKeepsWeightAndRepsAndClearsTheRest`; fixture-only edits to
  one `ActiveWorkoutScreenTest` test and `ActiveWorkoutPreferencesTest` (a seed
  with reps, so `Log set` is enabled) and the two route tests' constructor call.
  Gate: spotlessCheck, detekt, lintDebug (0 errors, 22 warnings, 1 hint, the
  baseline), testDebugUnitTest (727 tests, 114 classes, 0 failures),
  assembleDebug, assembleDebugAndroidTest green. AVD (`emulator-5554`, killed
  afterwards): `ActiveWorkoutSetEntryTest`, `ActiveWorkoutScreenTest`,
  `ActiveWorkoutPreferencesTest`, `ActiveWorkoutLeaveRouteTest`,
  `ProgressionRecommendationRouteTest` 59/59. Not built (follow-up per the
  design guidance): dimmed pending-row targets, the tinted `Just logged` bar,
  index/warm-up row layout; `8d`'s "first + starts reps at the bottom of the
  plan range" (the stepper's first + still gives 1) and its weight-required
  `Log set`. Open question for CP10's register: whether a weight-and-reps set
  may be logged without a weight.
- **CP10 (verification, register and decision records) complete, 2026-10-03.**
  No product code changed. **Gate:** `spotlessCheck detekt lintDebug
  testDebugUnitTest assembleDebugAndroidTest` green (lint 0 errors, 22 warnings,
  1 hint, the baseline; 727 unit tests in 114 classes, 0 failures); the whole
  instrumented suite on the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`,
  killed afterwards, never the phone): `connectedDebugAndroidTest` 377 tests, 0
  failed. **Fail-on-old-behaviour re-runs** (the rewritten and new tests were run
  against deliberately reverted behaviour, then the source was restored with
  `git checkout`): JVM, one run, 18 failures across `ProgressRangeTest` (5),
  `BackupViewModelTest` (2), `HomeViewModelTest`, `SettingsViewModelTest`,
  `ActiveWorkoutRestNotificationTest` (2), `ActiveWorkoutRestPrecedenceTest`,
  `ActiveWorkoutSeedTest` (3), `SetEntryStateTest`, `WorkoutFocusModelTest` (2)
  for: the clear-everything `clearAfterSet`, the exercise default dropped from
  the rest precedence, the four cancel calls removed, date ranges off by a month,
  the `last_backup_at` stamp removed, history seed removed. Instrumented, on the
  AVD: `ActiveWorkoutScreenTest.tappingLogSetKeepsWeightAndRepsAndClearsTheRest`,
  `theWorkoutPickersSearchFieldIsExactly48dpTall` and
  `RestTimerExpiryHandlerTest.theRealCancellerClearsAPostedRestDoneNotification`
  failed with the old clear-all entry, a 56dp search field and a no-op canceller.
  Not mutation-checked (named, not claimed): the `ProgressScreenTest` rewrites,
  the `SettingsScreenTest` rewrite, the migration tests (a missing `MIGRATION_8_9`
  is what `RepFlowDatabaseMigrationTest`'s registration test targets).
  **Side-by-side pass:** only partly done. On the AVD (empty database, 384dp,
  system theme, and Backup also at font scale 1.3) Settings and Backup were
  compared with `5c`/`8c`: layout, copy and wrapping match the register; nothing
  clipped at 1.3. Progress, the Library search and `2c` could not be seen with
  real data there (empty database, no data seeding in this checkpoint); they
  rest on `ProgressScreenTest` (including the 360/384dp dense-label case) and
  `RepFlowSearchFieldTest` (exact 48dp, 200% scale). `5d`'s hero with a backup
  was not photographed. The functional review should do these by eye.
  **Register:** `D115`-`D142` appended to the inventory (never renumbered);
  `D60` superseded, `D110` superseded, `D63`, `D103`, `D104`, `D108`, `D111`
  marked amended or superseded; `O11` and `O12` closed with their built rows;
  every worker-made decision from CP3, CP4, CP6, CP7, CP8 and CP9 has a row
  (empty and one-point chart states, Best est. 1RM row and its `—`, pill states,
  the kept note, width-aware labels, search-field colour and clear button, the
  theme first-frame flash, the keypad clamp, the Archived date format, the
  colon in `Last time:`, the first `+`, the double-`Log set` trade-off).
  `docs/UX_FLOWS.md` updated (Settings groups, Archived, Backup, Progress,
  set entry, rest precedence); `DOMAIN_GLOSSARY.md` unchanged (no new term).
  **Out of scope, recorded:** the `Last backup` setting is not in the backup
  file (`D117`); the declined "Save bar above the keyboard" change stays out.
  **Listed for the functional review (not changed in CP10):**
  1. **User decision after CP9:** for a weight-and-reps exercise `Log set` must
     require weight **and** reps (`8d`). Today weight is optional. To be raised
     as a fix there; it is not registered as an accepted deviation.
  2. Progress copy against turns 8/9 (`5b`): metric captions, the span line,
     the unavailable-metric copy, the `Only valid sessions count` note (`D125`),
     and the range-pill/chip check marks (`D124`).
  3. Settings against `8c`: regroup `Appearance` / `Rest timer` / `During a
     workout` / `Your data`, the Archived row label, the `Last backup <when>`
     subtitle on `Backup and restore`, permission slot rows, `Default rest`
     as m:ss, `Always shown` wording (`D138`).
  4. Set entry (`8d`, turn 9): dimmed pending targets, the `Just logged` bar,
     index/warm-up row layout, first `+` at the plan range's bottom (`D128`,
     `D141`).
  5. Number pad (`D136`), permission rows (`D137`), loading placeholders, Resume
     card in light theme, text contrast tiers (`D140`), exact-match search,
     notification icon and tap action, funnel-x and chart-line glyphs (`D142`):
     follow-ups once the designer answers `DESIGN_FOLLOWUP.md` section C.
  **Instrumented tests still needing a device:** none; all ran on the AVD.
- **Implementation review round 1 applied, 2026-10-03** (local review, REVISE,
  0 Blocking, 3 Important, 11 Optional). Each Important finding was fixed
  with a test that failed before the fix. **I-1** `BackupViewModel` settings
  `.catch` (rethrows only `CancellationException`, marks the hero loaded),
  `897e22e`. **I-2** `ProgressChart` reads `onSelect` through
  `rememberUpdatedState`, `02af222`; the self-review's "not reproduced"
  rejection is withdrawn (its repro switched the metric before any touch).
  **I-3** the `Off` extra-set-fields test now enters RPE, pain and technique
  in `Collapsed`, switches to `Off`, logs and asserts all three null,
  `18d5984`; it fails with the `takeIf { extraFieldsOn }` guards removed.
  Optional: O-1 (`897e22e`) and O-6 (`041d4e6`) applied; O-2 to O-5, O-7 to
  O-11 and the extra missing tests are recorded as not applied, with reasons,
  in the bundle's `IMPLEMENTATION_SUMMARY.md`. The functional-review items above are
  untouched. Gate: spotlessCheck, detekt, lintDebug (0 errors, 22 warnings),
  testDebugUnitTest 729 tests 0 failures; AVD instrumented 378 tests
  (94 + 87 + 197) 0 failures.
- **External implementation review round 1 applied, 2026-10-03** (manual
  Codex review of revision 2, REVISE, 0 Blocking, 1 Important, 0 Optional).
  **GXI-I1** fixed in `663090b`: the `last_backup_at` stamp after a successful
  backup export ran in `viewModelScope`, so leaving the Backup route could
  cancel it. It now runs inside `withContext(NonCancellable)`: no new scope or
  dependency (the app has no application-scoped `CoroutineScope`), and the
  write is one short Room update. Rules unchanged: stamp only after a
  successful backup export; CSV, cancel and failure never stamp; a failed stamp
  never fails the export. New test `BackupViewModelTest` "a suspended
  last_backup_at write still lands after the ViewModel is cleared" (cancels
  `viewModelScope` mid-write) failed before the fix and passes after. The
  functional-review items are untouched. Gate: spotlessCheck, detekt,
  lintDebug, testDebugUnitTest 730 tests 0 failures; AVD instrumented 378
  tests 0 failures.
- **External implementation review round 2 applied, 2026-10-03** (manual
  Codex review of revision 3, REVISE, 0 Blocking, 1 Important, 0 Optional).
  **GXI2-I1** fixed in `46b4731`: the stamp was launched with the default start,
  so a scope cancelled before the queued body first ran never entered
  `withContext(NonCancellable)`. It now uses
  `launch(start = CoroutineStart.UNDISPATCHED)` with the existing
  `NonCancellable` block. Write rules unchanged. New test `BackupViewModelTest`
  "a last_backup_at write still lands when the scope is cancelled before a
  queued dispatch runs" (a `StandardTestDispatcher` set up only for that test:
  call, cancel `viewModelScope`, run pending tasks, assert the stamp) failed
  before the fix (1 of 19) and passes after; the suspended-write test is kept.
  The functional-review items are untouched. Gate: spotlessCheck, detekt,
  lintDebug, assembleDebugAndroidTest, testDebugUnitTest 731 tests 0 failures.
  No instrumented run: the change is a JVM-only ViewModel launch start.

---

## `repflow-redesign-visual-foundation-remediation-1-remediation-1` — Functional review round 1: findings applied (2026-10-03)

`/apply-functional-review` took the **bounded branch** for all five findings
(GF-1 to GF-5, the user's decision: fix all in this item). Technical approval
of revision 4 was marked STALE first (`28b4dfc`), before any edit. No broad
child was needed: each fix is one contained change, no schema change, no new
module. The checklist below is **superseded for the changed areas** by the
re-test list at the end of this section; `/prepare-functional-review` rebuilds
it after a fresh implementation review and technical approval.

| Finding | Class | Disposition | Commit |
|---|---|---|---|
| GF-5 | defect | The rest notification posts on `rest_timer_v2` (vibration disabled, same importance and sound); the old `rest_timer` channel is deleted when it is created. Notification id 1001 and cancellation unchanged. Any customisation of the old channel resets (accepted by the user). `RestTimerExpiryHandlerTest`: the legacy vibrating channel is replaced by a quiet v2 one | `99e03ff` |
| GF-1 | defect | Per-exercise `SetEntryState` hoisted into a saveable `FocusEntries` map keyed by workout exercise id at `ActiveWorkoutScreen`, above focus mode. Seed and `touched` rules unchanged. 4 new `ActiveWorkoutSetEntryTest` tests, all failing before the fix (seeded and never-done exercise across the Board, focus moving away and back, recreation after a Board visit) | `b88446a` |
| GF-3 | missing requirement | `canLog` needs weight and reps for weight and reps. **0 kg counts as a weight; an empty value does not.** Reps-only and timed unchanged; stored sets unaffected. Unit tests; seeds in three screen tests now carry a weight | `afd3f84` (+ `08b929a` formatting) |
| GF-2 | defect | No-results state per `7c` N13 on both filters; `No archived exercises.` only for a truly empty archive with no query. VM test, screen tests, wiring test | `264a18a` |
| GF-4 | missing requirement | Settings (`8c`) regrouped and re-worded, Backup row subtitle, check on Default rest chips (`6ad3ec8`); Progress (`8b`) note removed, span line, captions, unavailable copy (`1688b97`); smoke test scroll (`6362410`). Out of scope and left: m:ss pad, permission rows, stacked dialog order, empty-range and single-session extra lines | `6ad3ec8`, `1688b97`, `6362410` |

Register: `D143`-`D148` appended to the inventory; `D125` superseded, `D138`
amended. The three open follow-ups from this review's notes (Undo after Restore
re-archives with today's date, an older `Rest done` stays in the shade, the
landscape rest panel over the steppers) are not changed.

**Gate:** `spotlessCheck detekt lintDebug testDebugUnitTest --rerun-tasks`:
BUILD SUCCESSFUL, 114 classes, 737 unit tests, 0 failures, lint 0 errors and 22
warnings (the baseline), detekt empty. Instrumented on the AVD
(`emulator-5554`, killed afterwards, never the phone), in three package groups:
data + infrastructure 94, presentation.workout 92, presentation excluding
workout 206; total 392, 0 failures (377 before this round, plus 15 new).

**Re-test (for the next functional review):** D3/D-set entry (type or step, go
to the Board, return; Next and back; `Log set` disabled until weight and reps,
0 kg accepted); E2 Library no-results on Active and Archived; A1-A7 Settings
layout and copy, the Backup row subtitle, the check on the Default rest chips
and none on Theme; C Progress (no note, span line, captions, unavailable
copy); G2 on the phone: one buzz at rest end, with the notification's sound.

## `repflow-redesign-visual-foundation-remediation-1-remediation-1` — Functional review checklist, ROUND 2 (implementation revision 6)

Functional review ROUND 2. Technical re-approval of **revision 6** is recorded
(commit `5b97352`, EXTERNAL_APPROVE). Round 1's findings
(`.ai-review/feedback/FUNCTIONAL_REVIEW.md`, consumed) and their outcome are in
"Functional review round 1: findings applied" above. This round re-tests
**only what round 1 and its review fixes changed** (GF-1 to GF-5, plus review
fixes GXI4-I1 and O1) and a short regression pass. For everything else, refer
back to the round 1 checklist below (superseded here for the changed areas).
Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md` (headed "round 2",
this item). Every command names the id explicitly.

**Tags.** **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`):
may write data and use Save, Restore and Export. **[PHONE]** the physical
SM-S928B, only with the user's permission: **look-only**, with one exception:
a single test workout, abandoned afterwards, used for GF-1, GF-3 and GF-5 on
real hardware. On the phone never save an editor, never Restore, Erase or
Export, never change a setting. A step that changes a setting is **[AVD]**.
**[BOTH]** AVD first, look-only on the phone. WRITES marks steps that write.

### R2 setup and automated state

- `ANDROID_SERIAL=emulator-5554 ./gradlew installDebug` (Room 9). Seed and test
  data as in round 1 "Test data" (Bench Press weight and reps with at least
  eight sessions across four months, Pull Up reps only, Plank timed, one
  archived exercise, plans, one never-done exercise, a fresh install for empty
  states). Notifications and `Alarms & reminders` allowed.
- Automated state: last full gate (round 1 fixes): spotlessCheck, detekt,
  lintDebug 0 errors, 737 JVM unit tests 0 failures, AVD instrumented 392 0
  failures. Revision 6 added two small app changes since (`WorkoutFocus.kt`
  seed effect keyed by the entry; `RestTimerExpiryHandler.kt` legacy channel
  deleted at every rest end) with tests, covered by the technical review. Not
  re-run here; the working tree is clean.

### R2-A. GF-1: unlogged values survive

1. **[BOTH] R2-A1. Seeded exercise, Board round trip. WRITES a workout (phone:
   the one test workout).** Start a workout with Bench Press (history). Open it
   in focus, type or step weight and reps to values that differ from the seed
   (do not log). Go to the Board, reopen it: your values are there, not the
   seed.
2. **[BOTH] R2-A2. Never-done exercise.** Add a never-done exercise, step a
   weight and reps (do not log), Board and back: values kept. Untouched, it
   stays empty (`—`).
3. **[BOTH] R2-A3. Next/Previous.** With unlogged values in exercise 1, press
   Next, edit exercise 2 unlogged, press Previous then Next: both keep their
   values.
4. **[AVD] R2-A4. Recreation.** With unlogged values, visit the Board, then rotate the
   screen: values kept.
5. **[AVD] R2-A5. GXI4-I1 late seed.** Cold start the app (force-stop, then open
   the active workout straight away) and open two exercises in focus quickly,
   before history arrives, then switch between them: the **second** exercise
   gets its seed too (not blank), and a typed value is never overwritten. If
   history arrives too fast to catch, say so; the unit test covers it.
6. **[AVD] R2-A6. Log clears per set.** Log a set: weight and reps stay for the
   next set; RPE/pain/technique/warm-up clear (unchanged from round 1).

### R2-B. GF-2: Library no-results

1. **[BOTH] R2-B1. Active.** Library, Active filter, search a name that matches
   nothing: magnifier, `Nothing called "…". Create it below.`, then the
   `Create "…"` row.
2. **[BOTH] R2-B2. Archived, no match.** Archived filter with at least one
   archived exercise, search a non-matching name: magnifier and
   `No archived exercises match "…".` (no create row).
3. **[AVD] R2-B3. Truly empty archive.** With nothing archived (restore it, or
   fresh install) and no query: archive glyph and `No archived exercises.`
   Clearing the query on B2 returns the list.

### R2-C. GF-3: Log set needs weight and reps

1. **[BOTH] R2-C1. Weight and reps.** Never-done weight and reps exercise
   (phone: the test workout): `Log set` disabled when both empty; still disabled
   with reps only; still disabled with weight only; enabled with both.
2. **[BOTH] R2-C2. 0 kg.** Weight `0` and reps `5`: `Log set` enabled and logs
   `0 kg × 5`. Clear the weight to empty: disabled again.
3. **[AVD] R2-C3. Unchanged types.** Pull Up (reps only): reps alone enables
   `Log set`. Plank (timed): the duration alone enables it.

### R2-D. GF-4: Settings and Progress copy

1. **[BOTH] R2-D1. Settings groups.** Scroll all of Settings: groups in order
   `Appearance`, `Rest timer`, `During a workout`, `Your data`, then the Erase
   card and footer. Nothing clipped. Compare with `8c`.
2. **[BOTH] R2-D2. Rows.** `Your data` holds a row `Archived` with subtitle
   `Exercises and plans`, and the Backup row whose subtitle reads `Last backup
   …` (a phone that has exported) or `No backup yet`. Row copy matches `8c`.
   The Erase card text names recovery entries and suggestions.
3. **[AVD] R2-D3. Chips and checks (WRITES a setting).** `Default rest` sheet:
   the selected chip has a check, others none; pick another and reopen, the
   check moved. Theme choices (`System`, `Light`, `Dark`) show **no** check
   (look only on the phone: open and close without choosing, never on the
   phone for Default rest). Restore `1:30` and `System`.
4. **[BOTH] R2-D4. Progress copy.** Progress for Bench Press: the span line
   under the chart (e.g. the date span of the range), the metric captions,
   `Records · all time`, and **no** `Only valid sessions count` note. The range
   pills (`3m` `6m` `All`) have no check.
5. **[BOTH] R2-D5. Unavailable copy.** A range or metric with nothing to show
   reads the new unavailable copy (a range with no sessions; a metric with no
   data such as `Best est. 1RM` with no set of 12 reps or fewer).

### R2-E. GF-5: one buzz, sound kept, channel renamed

1. **[BOTH] R2-E1. One buzz. WRITES a workout (phone: the same test workout).**
   Log a set so rest runs, lock the phone or leave the app, let the rest end:
   **exactly one** vibration (the app's 400 ms), not two, and the notification
   still plays its sound (phone not on silent/DND).
2. **[BOTH] R2-E2. Channel.** Settings app > RepFlow > Notifications (look
   only), or `adb shell dumpsys notification | grep rest_timer` on the AVD: the
   channel is `rest_timer_v2` and the old `rest_timer` is not listed. Open
   RepFlow after an update from the earlier build to confirm the old one is
   removed (AVD with the revision 4 build first, optional).
3. **[AVD] R2-E3. Notification still posts and is cleared** at Finish and at
   Abandon (round 1 F1, G1).

### R2-F. Short regression pass

1. **[BOTH] R2-F1. Rest timer.** `Skip` ends the rest; `-15s` and `+15s` change
   the remaining time; a `-15s` that ends the rest gives one alert.
2. **[AVD] R2-F2. Notification cleared on finish and abandon.** Rest
   notification showing, `Finish`: cleared; again with `Abandon`: cleared. On
   the phone only the abandon, in the test workout.
3. **[AVD] R2-F3. Editors (WRITES).** Exercise editor and plan editor: Save
   persists the change; leaving with edits and discarding does not.
4. **[AVD] R2-F4. Backup export stamps Last backup (WRITES).** Export a backup:
   the Backup screen shows `Last backup today`, and the Settings Backup row
   subtitle shows `Last backup …` too. CSV does not stamp.
5. **[AVD] R2-F5. Abandon the test workout.** Abandon any workout left over.

### R2 phone tester list

Back up the phone first and use the user's permission. Look-only except the one
test workout (abandon it at the end); no save, Restore, Erase, Export or
setting change.

1. **[BOTH] R2-D1, R2-D2** Settings groups and rows (look-only).
2. **[BOTH] R2-D4, R2-D5** Progress copy (look-only).
3. **[BOTH] R2-B1, R2-B2** Library no-results (type a search only).
4. **[BOTH] R2-A1 to R2-A3, R2-C1, R2-C2, R2-E1, R2-F1** one test workout:
   GF-1, GF-3, one buzz with the notification sound, rest timer; then
   `Abandon`.
5. **[BOTH] R2-E2** channel check (look-only).

### R2 known limitations and out of scope

- Not changed and not findings: m:ss pad, permission-state Settings rows,
  stacked dialog order, empty-range and single-session extra lines, Undo after
  Restore re-archiving with today's date, an older `Rest done` staying in the
  shade, the landscape rest panel over the steppers.
- Customisation of the old rest channel resets (accepted). Weight unit not
  built (`D5`).

### R2 expected result and what happens next

Every step behaves as stated, nothing crashes, no earlier area regressed. If
clean: `/accept-milestone repflow-redesign-visual-foundation-remediation-1-remediation-1`
is the only acceptance command and needs every checkpoint in the registry
`COMPLETE`; an outstanding checkpoint goes to `/milestone-implement`. No
command records acceptance of a partial round. Findings go to
`.ai-review/feedback/FUNCTIONAL_REVIEW.md`, then
`/apply-functional-review repflow-redesign-visual-foundation-remediation-1-remediation-1`
(bounded branch for a same-scope fix, broad branch for a `...-remediation-<n>`
child).

---

## `repflow-redesign-visual-foundation-remediation-1-remediation-1` — Functional review checklist, round 1 (implementation revision 4; superseded by round 2 above for the changed areas)

Functional review ROUND 1 of group B. Technical approval of **revision 4** is
recorded (commit `7159408`). Scope: B1 to B7 (Settings `5c` and the Archived
screen; Backup `5d` with `Last backup`; Progress `5b`; set-entry carry-over and
seeding; 48dp search fields and empty-state glyphs; Progress ignoring 0-set
exercises; the rest notification cleared on finish, abandon, erase and
restore), the Q1 to Q9 behaviour (Default rest sheet, Extra set fields
Always/Collapsed/Off, Theme System/Light/Dark, rest precedence plan > exercise
> app) and the three review fixes (Backup screen not crashing, chart taps after
a metric switch, `Last backup` stamp after leaving the screen). Findings go to
`.ai-review/feedback/FUNCTIONAL_REVIEW.md` (headed "round 1", this item). Every
command names the id explicitly (`active_work_item_id` still points at the
top-level parent).

**Tags.** **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`):
may write data, use Save, Restore and Export. **[PHONE]** the physical SM-S928B
(`RFCXA0RLSVT`), only with the user's permission: **look-only**, except step
G2, one test workout that is abandoned afterwards. On the phone never save an
editor, never Restore, Erase or Export, never change a setting. A step that
changes a setting is **AVD-only** and says so. **[BOTH]** either, AVD first and
look-only on the phone. Steps that write data say **WRITES**.

### Known items for this round (user decisions; to be raised as findings)

These are recorded in the CP10 entry above and in
`/home/rodrigo/.local/share/claude-lanes/repflow-lane/design-turn7-guidance.md`.
Testers **confirm the current state; they do not judge these as new defects**.
They are raised as findings so `/apply-functional-review` routes them.

1. **`Log set` must require weight AND reps** for a weight-and-reps exercise
   (`8d`). Today weight is optional (reps only gates it). Confirm that.
2. **Turn-8 in-scope tweaks**, current state to confirm, not new:
   - Settings regroup: now `Library` / `Units and appearance` / `Rest timer` /
     `During a workout` / `Data`; the design wants `Appearance` / `Rest timer` /
     `During a workout` / `Your data`.
   - Settings row label is `Archived exercises and plans`; the design wants
     `Archived` with the subtitle `Exercises and plans`.
   - The `Backup and restore` row has no subtitle; the design wants `Last backup
     ...`.
   - Progress: metric captions, the span line, the unavailable-metric copy
     (`No sessions in this range yet.`), the kept `Only valid sessions count`
     note (the design removes it), and range-pill/chip check marks.

### Setup and automated state

- Build and install on the AVD: `ANDROID_SERIAL=emulator-5554 ./gradlew
  installDebug`. Room 9 (migration 8 to 9). Seed the AVD per "Test data" below.
  On the phone install the same debug build only after the user backs up.
  Notifications and `Alarms & reminders` allowed for the rest-notification steps.
- **Automated verification is current and not re-run here.** The working tree
  is clean and `git diff <last app commit> HEAD -- app` is only the review
  fixes already gated. Last full gate: spotlessCheck, detekt, lintDebug (0
  errors), JVM unit tests 731, 0 failures; AVD instrumented 378, 0 failures.

### Test data

Seed the AVD, not the phone. Restore a backup file (copy it to
`/sdcard/Download/` with `adb -s emulator-5554 push`, then Settings -> Backup and
restore -> Restore from a backup) with: `Bench Press` (weight and reps), `Pull
Up` (reps only), `Plank` (duration), one archived exercise, two active plans and
one archived plan (one plan with a rest set on a row), **at least eight
completed workouts of Bench Press across at least four calendar months** (so
`3m`, `6m` and `All` differ, with some sessions with RPE), one session where an
exercise was added but has **0 sets**, and an exercise with no history. Also
keep a **fresh install** for the empty states.

### A. Settings `5c` and the Archived screen (B1)

1. **[BOTH] A1. Layout.** Settings > scroll the whole screen. Expect the groups
   in order `Library`, `Units and appearance`, `Rest timer`, `During a workout`,
   `Data`, then the `Irreversible` card and footer; `Data` holds `Archived
   exercises and plans` and `Backup and restore`. Compare with `5c`/`8c`
   (known items 2: do not report the regroup or label as new). Nothing clipped
   at default font scale.
2. **[AVD] A2. Theme (WRITES a setting).** Choose `Light`, `Dark`, `System`. The
   app recolours at once and the status-bar icons stay legible; the choice
   survives leaving and reopening the app. Leave it on `System`.
3. **[AVD] A3. Default rest sheet (WRITES a setting).** Tap `Default rest`. The
   caption reads `Used when neither the plan nor the exercise sets a rest.`
   Chips `1:00 1:30 2:00 3:00` and `Other`. A preset saves and closes and the row
   shows it. `Other` opens the numeric keypad (whole seconds); enter `45`: the
   chip row shows a selected `45` chip that reopens the keypad. Restore `1:30`.
4. **[AVD] A4. Extra set fields sheet (WRITES a setting).** Three radio rows
   (`Always shown`, `Collapsed`, `Off`) with explanations; a pick saves and
   closes and the row shows it. Leave it on `Collapsed`.
5. **[BOTH] A5. Archived screen.** Settings > `Archived exercises and plans`.
   Sections `Exercises` and `Plans`; each row has the name, `Archived d MMM yyyy`
   and an outlined `Restore`; most recently archived first. Back returns to
   Settings; the bottom bar is hidden. On the phone look only, do not tap
   `Restore`.
6. **[AVD] A6. Restore and Undo (WRITES).** Tap `Restore` on the archived
   exercise: the row leaves at once, snackbar `<name> restored` with `Undo`; Undo
   re-archives it. Restore again, confirm the exercise is in the Library, then
   archive it again from the Library.
7. **[AVD] A7. Empty Archived.** With nothing archived (restore all, or a fresh
   install) the screen shows the archive glyph and `Nothing archived. Archived
   exercises and plans show up here.`; with only one section empty the inline
   row (`No archived plans.`) shows.

### B. Backup `5d` with Last backup (B1)

1. **[BOTH] B1. Opens without crashing.** Settings > `Backup and restore`. The
   screen opens (the earlier crash fix), title `Backup and restore`; bottom bar
   hidden; back returns to Settings.
2. **[BOTH] B2. By eye, BEFORE an export, with real data.** On a database that
   has never exported: hero with the shield-warning glyph, `No backup yet` over
   `Your data lives only on this phone`; the primary button reads `Export your
   first backup`; the note, `Export for other tools` with `Workout history as
   CSV`, the `Replaces everything` card with `Restore from a backup`. Compare
   with `5d`/`8c`; nothing clipped. (A phone that has exported before shows
   `Last backup ...` instead: look only.)
3. **[AVD] B3. Export a backup (WRITES).** Tap the export button, choose a place
   in the system picker, confirm. `Saved` shows on the hero.
4. **[AVD] B4. By eye, AFTER the export.** Hero now shield-check, `Last backup
   today` over `d MMM, HH:mm`, button `Export backup now`. The stamp is the time
   you exported.
5. **[AVD] B5. Stamp after leaving.** Start an export, and press Back (or leave
   the screen) as soon as the picker returns. Reopen: `Last backup` shows the
   new time (review fix).
6. **[AVD] B6. CSV does not stamp.** Note the time, export `Workout history as
   CSV`: `Last backup` does not change. Cancel a backup export from the picker:
   no change either.
7. **[AVD] B7. Restore (WRITES).** `Restore from a backup`, pick the file,
   confirm in the destructive dialog: `Backup restored`, data matches the file.
   Cancelling the dialog or the picker changes nothing. Re-seed afterwards if
   needed.
8. **[AVD] B8. Large text.** Font scale 1.3 (Settings app, not RepFlow): Backup
   and Settings wrap and nothing is clipped. Restore the font scale.

### C. Progress `5b` (B2)

1. **[BOTH] C1. By eye with real data.** Progress with Bench Press: the exercise
   picker button, three metric buttons, the chart card with caption, the `3m`
   `6m` `All` pills (default `All`), value with delta, readout strip, chart with
   gridlines and date labels that do not overlap and include the first and last;
   `Sessions` / `Avg RPE` tiles, `Training frequency` bars, `Records`. Compare
   with `5b`. Known items 2 cover the copy, span line, `Only valid sessions
   count` and pill checks.
2. **[BOTH] C2. Several sessions across months.** With sessions in four or more
   months, `3m`, `6m` and `All` give different spans, headline, delta and
   first/last labels; labels read the year when the series spans years.
3. **[BOTH] C3. Chart taps after a metric switch (review fix).** Tap the
   chart, change the metric (for example `Most reps in a set`), tap the chart
   again, drag across it: the readout and dot follow each touch, the first touch
   after the switch works.
4. **[BOTH] C4. Exercise picker.** `Track an exercise` sheet lists exercises,
   the chosen row is tinted with a check; pick another and the range keeps its
   choice; the sheet scrolls with many rows.
5. **[BOTH] C5. Edge states.** An exercise with one session shows a lone dot and
   no delta; a range with no sessions shows `No sessions in this range yet.`
   with the pills kept; an exercise without RPE shows `—` for `Avg RPE`; with no
   set of 12 reps or fewer `Best est. 1RM — —`. The empty Progress (fresh
   install) shows its empty state.
6. **[BOTH] C6. Progress ignores 0-set exercises (B6).** An exercise that was
   added to a session but had no sets there does not appear as a point or
   session, and one never trained is not in the picker. (Seed: the 0-set
   session.)

### D. Set entry keeps its numbers and seeds (B3)

1. **[AVD] D1. Seed from the last session (WRITES a workout).** Start a workout
   with Bench Press (history exists). Before any set the steppers show the last
   working set from the last valid session and `Last time: 80 kg × 8` style line
   shows. Never-done exercise: steppers empty (`—`) and `Log set` disabled.
2. **[AVD] D2. Carry-over.** Log a set: weight and reps stay in the steppers
   (not cleared); RPE, pain, technique and warm-up clear; `Last time` gives way
   to `Last:` with `Undo last`. Log again.
3. **[AVD] D3. Typed values stay.** Type a weight, rotate the screen (or leave to
   the Board and come back): the typed value stays; an untouched entry may take a
   late seed but a typed one never changes.
4. **[AVD] D4. Known item 1.** For Bench Press with weight emptied and reps
   entered, `Log set` is enabled today. Confirm only; do not report as new.
5. **[AVD] D5. Timed and reps-only.** Plank (seconds) and Pull Up (reps) seed and
   gate `Log set` on the value they need.
6. **[AVD] D6. Abandon the test workout.** Abandon it afterwards (see G).

### E. 48dp search fields and empty-state glyphs (B4, B5)

1. **[BOTH] E1. Search fields.** The Library search, the workout exercise picker
   and the plan-editor exercise picker: field is 48dp tall, hairline, magnifier,
   a clear button appears only with text, the keyboard's Search action hides the
   keyboard. The workout picker field is the darker control colour like the
   design. Do not save anything on the phone.
2. **[BOTH] E2. Empty glyphs.** Library with no exercises shows the barbell
   glyph, the archived filter with none shows the archive glyph, a search with no
   result shows the magnifying glass (the create-from-query footer still shows
   where it did); Recovery history empty shows the moon-and-stars glyph. On the
   phone use a search that matches nothing (look-only).
3. **[AVD] E3. Large text.** Font scale 2.0: the search field grows, text not
   clipped. Restore the scale.

### F. Rest notification cleared on finish (B7)

1. **[AVD] F1. Finish.** Start a workout, log a set so rest runs, let the rest
   end so the `Rest done` notification shows (notifications allowed), then
   `Finish`: the notification is gone from the shade.

### G. Rest notification cleared on abandon, erase, restore (B7)

1. **[AVD] G1. Abandon from Home.** With a workout in progress and the rest
   notification showing, `Abandon` from Home: the notification clears.
2. **[BOTH] G2. WRITES a test workout (phone: the one allowed test workout).**
   Start a workout, log one set, let the rest end so the notification shows,
   then `Abandon` the workout from the active workout screen: the notification
   clears. Do not finish it; the abandon removes the data. Never use Erase or
   Restore on the phone.
3. **[AVD] G3. Erase (WRITES).** With the notification showing, Settings >
   `Erase all data` and confirm: the notification clears. Re-seed afterwards.
4. **[AVD] G4. Restore (WRITES).** With the notification showing, restore a
   backup (B7): the notification clears. A failed restore (a non-RepFlow file)
   leaves the notification.

### H. Q1 to Q9 behaviour

1. **[AVD] H1. Rest precedence.** Plan row with a rest > exercise `Default rest`
   > the app `Default rest` (Settings, A3). Start a workout from a plan whose row
   has a rest, one with no row rest on an exercise with its own `Default rest`,
   and one ad-hoc exercise with neither: logging a set starts the timer with
   the plan value, the exercise value, the app value respectively. Change the app
   default and the next set picks it up. (WRITES a setting and workouts.)
2. **[AVD] H2. Extra set fields.** Set `Always shown`: RPE, pain and technique
   rows show under every set without a disclosure. `Collapsed`: the disclosure.
   `Off`: nothing shown, and logging records no RPE/pain/technique. The warm-up
   chip and the corrections sheet still work. (WRITES a setting; restore
   `Collapsed`.)
3. **[AVD] H3. Theme in a workout.** In `Light` and `Dark` the active workout,
   done screen and Progress read correctly; reset to `System`.

### Phone tester list

Back up the phone's data first and use the user's permission. Everything is
look-only; no step saves, restores, erases, exports or changes a setting.

1. **[BOTH] A1** Settings layout (look-only).
2. **[BOTH] A5** Archived screen (look-only, do not tap `Restore`).
3. **[BOTH] B1** Backup screen opens and back works.
4. **[BOTH] B2** Backup hero by eye (look-only; do not export).
5. **[BOTH] C1** Progress by eye with real data.
6. **[BOTH] C2** `3m`/`6m`/`All` across months.
7. **[BOTH] C3** Chart taps after a metric switch.
8. **[BOTH] C4** Exercise picker.
9. **[BOTH] C5** Edge states (look-only).
10. **[BOTH] C6** 0-set exercises absent.
11. **[BOTH] E1** Search fields 48dp (do not save).
12. **[BOTH] E2** Empty glyphs.
13. **[BOTH] G2** One test workout, then `Abandon`.

### Known limitations and out of scope

- The Known items above (weight AND reps for `Log set`, turn-8 Settings and
  Progress tweaks) are raised as findings; do not re-judge them.
- Follow-ups, not findings: dimmed pending-row targets, the `Just logged` bar,
  index/warm-up row layout, first `+` at the plan range's bottom, the number pad
  (open on the current value, `Done`), permission-state Settings rows, loading
  placeholders, Resume card in light theme, contrast tiers, exact-match search,
  notification icon and tap action, funnel-x and chart-line glyphs, `m:ss` in
  the `Other` keypad, the `Last backup` setting not being in the backup file.
- Registered deviations (CP10 `D115` to `D142`): no recent-files list, no
  `Share`, no safety-snapshot row, no restore `Undo` toast; the cold-start
  theme flash; `Last time:` with a colon.
- Weight unit not built (`D5`).

### Expected result and what happens next

Every step behaves as stated, nothing crashes, no earlier area regressed. If
clean: `/accept-milestone repflow-redesign-visual-foundation-remediation-1-remediation-1`
is the only acceptance command and it needs every checkpoint in this item's
registry `COMPLETE`. A checkpoint still outstanding goes to
`/milestone-implement`; no command records acceptance of a partial round.
Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md`, then
`/apply-functional-review repflow-redesign-visual-foundation-remediation-1-remediation-1`
(bounded branch for a same-scope fix, broad branch for a
`...-remediation-<n>` child).

---

## In implementation: `repflow-redesign-visual-foundation-remediation-1`

The remediation child that discharges the parent's functional-review finding
F1 (see the parent's "Functional review round 1 — outcome" below). Governed
by Workflow v2.1; plan approved at **revision 20** (approval commit
`17e7f32`, basis `EXTERNAL_APPROVE`). Every command must **name the child id
explicitly** — `active_work_item_id` still points at the parent.

- **Active plan:**
  `docs/milestones/repflow-redesign-visual-foundation-remediation-1-execution.md`
  (revision 20). Registry:
  `docs/ai-workflow/registry/repflow-redesign-visual-foundation-remediation-1-registry.json`
  (CP1–CP16, executed in array order).
- **Current checkpoint: CP16 — Verification, side-by-side validation, and
  decision updates: complete. All sixteen checkpoints are done.** The
  self-review of the full milestone diff and the full gate are done (below).
  Implementation review of revision 1 returned REVISE (0 Blocking, 1
  Important, 9 Optional); it is applied (below), and next is the review of
  revision 2.

### Self-review of the full milestone diff (2026-10-02)

`/milestone-implement` step 2 over `17e7f32..6595108`, in four slices
(persistence/settings/backup; domain and application read models with their
ViewModels; the workout surfaces; every other converted screen, navigation
and the design system), each read against the plan, the deviation register
and the pre-milestone code. Mechanical checks first: no Android, coroutine
or outer-layer import in `domain/`, no Room type outside `data/` and
`infrastructure/`, no `.ordinal`, no broad `catch`, no destructive
migration; the one schema change is CP14's planned `MIGRATION_7_8`, with its
test. **Two Important findings, both fixed:**

- **The rest alert fired for a workout that had already ended.** Only the
  workout route cancels the rest alarm, and the milestone added ways to end a
  session without that route on screen: Home's resume card `Abandon` (CP5)
  and Settings' `Erase all data` (CP14) (a restore already could). The
  "rest over" notification and buzz then fired for a session that no longer
  existed, and the finish path depended on Room's update reaching the route
  before it navigated to the done screen. Fixed at the one place every path
  reaches: `RestTimerExpiryHandler` now alerts only while an active session
  still has a rest timer (finished, abandoned, erased or skipped → nothing).
  Tests: `RestTimerExpiryHandlerTest` seeds an active session with a running
  rest, and has four new cases (abandoned, completed, skipped, erased: no
  buzz, no notification, every switch on). `RestTimerReceiverDeliveryTest`
  now gives the app's own database a running rest first, through a second
  Room instance, and abandons or restores that session afterwards.
- **The exercise editor's `Other` field closed while the user typed** (CP10).
  It was shown only while the text matched no preset, so typing `600` closed
  it at `60` (the `1:00` preset), and `5.5` closed it at `5` (the `5 kg`
  preset). Rests of 600–609, 900–909, 1200–1209 and 1800 s, and load steps of
  5.x and 50–59.x kg, could not be entered. `Other` now stays open once
  chosen, until a preset is tapped. While it is open, tapping a preset selects
  that preset rather than clearing the value. New `ExerciseEditorScreenTest`
  cases `otherRestFieldStaysOpenWhileTypingThroughAPresetValue` and
  `otherLoadStepFieldStaysOpenWhileTypingThroughAPresetValue`.

Each new test fails with the fix reverted and passes with it (all six were
re-run on the AVD both ways).

**Rejected with evidence:** a `+0.0 kg` delta on Android, on the theory that
libcore's `BigDecimal.stripTrailingZeros()` keeps a zero's scale. On the API
36 AVD, `0.0`, `80.0 − 80.0` and `0.00` all strip to `0`, as on the JVM.

**Minor, reported and not changed** (for the reviewer):

- **A failed settings read can crash the app from the background.** The
  receiver's coroutine has no handler, and its Room reads are new. The same
  applies to `ActiveWorkoutViewModel`'s auto-start read after a set is
  recorded.
- **An already-expired rest is re-scheduled** whenever the route re-enters
  composition, so AlarmManager fires it again. This is the same code as
  before the milestone. A rest that is still running is the precondition
  the fix above now enforces.
- **Focus mode's warm-up hint always says `Ns rest`,** even with auto-start
  off.
- **Set RPE takes whole numbers only (`0–10`).** The old text field accepted
  halves; `D11` names only the range.
- **The focus keypad loses its typed text on rotation** across the 400 dp
  `Row`/`Column` switch.
- **Recovery's `Saved` can mark an edit made during an in-flight save,** a
  window of milliseconds.
- **A few non-tab navigations have no `launchSingleTop`,** so a fast double
  tap can open a screen twice.
- **System back on the History detail leaves the tab** instead of closing the
  detail (as before the milestone).
- **The recovery scale cells are announced without their scale's name.**
- **`ProgressViewModel` has no `catch`,** like `HistoryViewModel` before it.

**Full gate (step 3), after the fixes, on the tree committed with this
record:**

- `./gradlew --rerun-tasks spotlessCheck detekt lintDebug testDebugUnitTest
  assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL, 95/95 tasks
  executed.**
- JVM: **621 tests in 101 classes, 0 failures.**
- Lint: **0 errors, 21 warnings, 1 hint** (the standing baseline).
- detekt: **0 issues.**
- `connectedDebugAndroidTest` on **`RepFlow_S24Ultra_384dp_API36`** (AVD),
  in three package groups that together cover the whole suite
  (`presentation.workout` 56; the rest of `presentation` 135; everything
  outside `presentation` 84): **275 tests, 0 failures, 0 errors, 0 skipped**
  (CP16's 269 plus the six new ones).
- This is emulator evidence. The device coverage is CP16's SM-S928B runs.

### Implementation review of revision 1 — applied (2026-10-02)

`/review-implementation`'s verdict on revision 1 (bundle `73268b76`,
`review_content_id` `aee0ca19`): **REVISE, 0 Blocking, 1 Important, 9
Optional**, plus missing-test notes. Each finding was checked against the
code before acting.

**Fixed:**

- **I1 — focus `Next ›` was a dead button on the last unfinished exercise**
  (`7c530e0`). Reproduced: `nextUnfinishedExercise` wrapped round to the
  focused exercise itself, and `WorkoutFocusModelTest` pinned that. Plan CP8
  item 9 says `Next ›` there returns to the board with the finish sheet
  (CP9 item 1). The function now never answers the current exercise, so
  `Next ›` sends the one finish request when no *other* exercise is
  unfinished. New screen test
  `nextOnTheLastUnfinishedExerciseReturnsToTheBoardWithTheFinishSheetRaised`
  fails against the old function.
- **O1 — a failed read in the rest receiver crashed the backgrounded app**
  (`b619334`). The receiver now launches through `launchRestAlert`, whose
  scope has a `CoroutineExceptionHandler` that logs the failure and skips
  the alert; the pending broadcast is still finished. The reviewer's narrow
  `SQLiteException` catch inside the handler was tried first and failed
  `LayerBoundaryTest`: `presentation` may not import `android.database`.
  This is the root coroutine's failure sink, not a `catch` around suspend
  work, and cancellation never reaches it. Two new `RestTimerExpiryHandlerTest`
  cases cover a failed session read and a failed settings read.
- **O2 — the delivery test left an `ABANDONED` row in the installed app's
  database** (`c0d890f`). It now deletes the session it inserted.
- **O3 — no guard that the scoped clear covers every training table**
  (`c0d890f`). `SettingsPersistenceTest` now compares `TRAINING_TABLES` with
  every table in `sqlite_master` except `settings` and the bookkeeping
  tables.
- **O8 — recovery `Save entry` was enabled while a new date loaded**
  (`51031b1`). It is now disabled while loading; new screen test.
- **Missing test — the finish confirm's state machine** (`eaa3401`). Two JVM
  tests: success ends `Finished(id)` with a second in-flight confirm ignored
  (fails with the guard removed), and a failure returns to `Idle` with
  `NOT_FOUND`.

**Not changed, recorded for the functional review** (each is optional, and
fixing it would add behaviour the plan does not specify):

- **O4 — Home shows the start card when the active-session read fails.** A
  distinct failure state is a new Home state with its own copy. It needs a
  storage failure; `Start workout` then fails with "already active", so no
  data is lost.
- **O5 — a failed readiness read leaves the recovery card without a
  message.** A message or retry is new copy and behaviour.
- **O6 — the recommendation screen's readiness block reads today's
  check-in,** while the policy uses the latest entry. The policy's own
  reasons, which the screen also shows, explain the outcome correctly.
- **O7 — focus mode drops the suggestion strip for an exercise archived
  mid-workout.** Looking the recommendation up independently of the picker
  needs a new read path; the strip is advisory, and the stored
  recommendation itself is unaffected.
- **O9 — optional plan-row values can be stepped to 0 but not back to
  blank.** Focus mode treats 0 and blank alike, so nothing visible changes.

**Gate after the fixes, on the committed tree:**

- `./gradlew --rerun-tasks spotlessCheck detekt lintDebug testDebugUnitTest
  assembleDebugAndroidTest`: **BUILD SUCCESSFUL.**
- JVM: **623 tests in 101 classes, 0 failures** (621 plus the two
  finish-state tests).
- Lint: **0 errors, 21 warnings, 1 hint** (the baseline). detekt: **0**.
- `connectedDebugAndroidTest` on **`RepFlow_S24Ultra_384dp_API36`** (AVD), in
  three package groups covering all 40 classes: `data` + `infrastructure` 85,
  `presentation.workout` 59, the rest of `presentation` 136. **280 tests, 0
  failures, 0 errors, 0 skipped** (275 plus the five new tests).

### CP16 — what was done and verified (2026-10-02)

- **Device run of every instrumented test (plan item 1, F1 acceptance
  criterion 5).** The CP2–CP15 instrumented tests had only been compiled.
  On the physical **SM-S928B** (`RFCXA0RLSVT`, Android 16, 384 dp) the first
  full `connectedDebugAndroidTest` surfaced three kinds of failure, all
  fixed in this checkpoint:
  - **Eight screen tests rendered without `RepFlowTheme`**
    (`ExerciseEditorScreenTest`, `HistoryScreenTest`, `HistoryDetailScreenTest`,
    `ProgressScreenTest`, `RecoveryFutsalScreenTest`, `RecoveryHistoryScreenTest`,
    `TrainingPlanEditorScreenTest`, `TrainingPlanListScreenTest`): the
    converted screens read `LocalRepFlowExtraColors`, whose default throws
    outside the theme. Each `setContent` now wraps the screen in
    `RepFlowTheme`, as `ExerciseListScreenTest` already did. Test-only.
  - **A real defect in CP14's Settings** (`BackupRouteUnreadableRestoreFileTest`):
    `SettingsMessages` consumed a message *before* showing it, which cleared
    the key its `LaunchedEffect` is keyed on, so the restart cancelled the
    snackbar — no backup or Settings message (`Backup restored`, the failure
    message, `All data erased`) ever stayed on screen. Now shown first,
    consumed after, as every other screen does; new
    `SettingsScreenTest.aMessageStaysOnScreenAfterItIsConsumed` pins it.
  - **`ProgressScreenTest.theChipsAndSegmentsReportTheChoice`** matched two
    nodes (the card repeats the selected exercise's name); it now selects the
    selectable chip.
  - The smoke test's `historyTab…`/`progressTab…` need an empty database: the
    first run was over the phone's own data. The app was uninstalled before
    the later runs (the orchestrator had backed the data up).
- **Side-by-side validation (plan item 2: F1 required remediation 5 and 6,
  acceptance criterion 4).** The design was captured from the live project
  (headless Chromium over `render_preview`): every static artboard plus `4a`'s
  prototype driven through 23 states. The app was captured on the phone
  (dark, every converted surface, with a synthetic history restored through
  `Restore from a file` and live workouts) and, once the user needed the phone
  back, on the 384 dp AVD for the populated light-theme pass (supporting
  evidence). Contact sheets: `.ai-review/repflow-redesign-visual-foundation-remediation-1/cp16-side-by-side/`
  (`INDEX.txt`; gitignored). Every surface matches its artboard's
  composition apart from registered deviations. **Fixed** from the pass:
  - Settings and Progress applied the scaffold's padding *inside* their
    scroll, so content scrolled under the top bar (Settings' rows ran through
    its title). The padding is now applied outside the scroll.
  - The board's context line read `Heavy legs: n/4 · Leg DOMS: n/4`; the
    scales are `0–5` since Milestone 8. Now `/5` (`D57`'s text).
  - The finish sheet's footnote said "finish or discard it"; the action is
    `Abandon this workout` (`D18`). Now "abandon" (new `D112`).
  - `RepFlowEmptyState` drew a bare body-large line; it now draws `1d`'s
    empty treatment (optional 26dp glyph at 35% over a 13.5 line), and
    History's empty state passes its tab glyph.
  **Observed, not changed** (outside this checkpoint's scope, reported):
  the plan editor asks `Discard changes?` on back even with no edit (its
  edit-mode `isDirty` is `true` once loaded — unchanged since Milestone 2);
  on a fresh install the rest-end alarm is inexact (`setAndAllowWhileIdle`,
  up to ~2 min late) unless exact alarms are allowed — Milestone 4's
  scheduler, unchanged here.
- **Deviation register (item 3)** reviewed end to end in the inventory, with
  the review written under its table: every row still justified; `D57`'s
  denominator fixed; `D112` added. Next free id: **D113**. Rows their
  checkpoints flagged for the reviewer stay flagged.
- **Decisions (item 4).** `docs/TECHNICAL_DECISIONS.md`: `Navigation
  structure` **resolved** (the four-destination IA, new section); a new
  resolved row and section for the **readiness score** (the user's
  2026-09-30 adoption of the prototype's engine); every other open row
  untouched — `Whether completed workouts may be manually corrected` and
  `How substitutions affect progression history` included.
- **`docs/UX_FLOWS.md` (item 5)** rewritten to the delivered IA and the
  board/focus split. Rows still unimplemented are kept and marked *declared
  intent* with an `IMPROVEMENT_ROADMAP.md` pointer: the pre-start preview
  (`:39–43` before), previous performance, suggested load as a figure, reuse
  the previous set, skip or substitute, recovery or pain warnings and
  workout notes on completion. `Discard workout` became `Abandon this
  workout`, with confirmation (`D18`).
- **`docs/improvements/IMPROVEMENT_ROADMAP.md` (item 6):** new §9 — the five
  deferred capabilities (supersets, swap/substitute, notes, active plan and
  multi-day plans, kg/lb) as named future milestones with scope and
  preconditions, the other no-domain surfaces, the declared-intent rows, and
  §9.8 the decided next remediation child (`5b`, `5c`/`5d`, `D60`'s set-entry
  carry-over). §8.1 and §8.2 marked delivered.
- **ADR-0003's snapshot (item 7)** names the fourth Hilt module,
  `RestAlertModule` in `presentation/workout/`, and the new packages.
- **`ROLE_AUDIT.md` re-run by its own method (item 8)** over the converted
  tree: 17 imported components (ten retired), 0 colour literals outside
  `designsystem/`, direct reads in 41 files, all of assigned roles; the
  `Consumers` column re-derived — `surfaceContainer` is now the nav bar alone,
  `surfaceContainerHighest`, `secondaryContainer`, `primaryContainer` and
  `outlineVariant` have no consumer, `onSurfaceVariant` is stock-only. No value
  changed, so no ratio was recomputed. `RepFlowColor.kt`'s KDoc no longer
  names the plan editor's row card as `surfaceContainerHighest`'s consumer.
- **Checks run (item 1's forced gate), final, on the AVD after every change:**
  `./gradlew --rerun-tasks spotlessCheck detekt lintDebug testDebugUnitTest
  assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest` —
  **BUILD SUCCESSFUL, 96/96 tasks executed**; **621 JVM tests (101 classes),
  0 failures**; **269 instrumented tests on `RepFlow_S24Ultra_384dp_API36`,
  0 failures**; lint 0 errors, 21 warnings, 1 hint (the standing baseline).
  **Device runs on the SM-S928B, earlier in the checkpoint:** the full suite
  (269 tests, 268 passed, 1 failed — the `ProgressScreenTest` match, fixed),
  then that class with `SettingsScreenTest` and both backup route tests
  (13 tests, 0 failures). The final AVD run is emulator evidence and is not
  claimed as device coverage: the three later presentation fixes (scroll
  padding, the two strings, the empty state) ran on the AVD only.

### CP15 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  Progress tab (`RepFlow.dc.html:1013–1050`), its script (`nProgChips` …
  `nProgBest`, `:3743–3771`; `nSeries`, `:3531–3532`) and sample data
  (`PROGRESS`, `PMONTHS`, `PLABEL`, `:3106–3124`), and `1c`'s `Best set` /
  `Est. 1RM` pair (`:2959–2960`). Built to `4a`, per the user's 2026-10-01
  decision on `O12`; `5b` is a later remediation child's.
- **Estimated 1RM (plan item 2).** New `domain/progression/EstimatedOneRepMax.kt`,
  pure Kotlin: **Brzycki**, `load × 36 / (37 − reps)` - it reproduces `1c`'s
  `82.5 × 7 → 99 kg` exactly - for 1 to 12 reps; outside that (or a negative
  load) there is no estimate (`D109`, flagged).
- **Read model (plan items 3, 4, 6).** New `application/progress/`:
  `ExerciseProgress.kt` (`ProgressMetric`, `ProgressPoint`, `ProgressSeries`,
  `ExerciseProgress`, the pure `exerciseProgressOf`) and
  `ObserveExerciseProgress` over `WorkoutRepository.observeCompletedSessions(includeInvalidated = false)`.
  Valid (completed, not invalidated) sessions only, filtered again in the pure
  function; working sets only; one point per session per metric, only when
  it has a value. `WEIGHT_AND_REPS` offers `Top set` (heaviest working load),
  `Est. 1RM` (best per-set estimate, whole kg) and `Volume` (Σ load × reps,
  whole kg, as History's); `REPS_ONLY` and `DURATION` offer `Top set` alone
  (most reps / longest set in seconds; `D22`). The window is the last twelve
  points; `hasTrend` (two or more), `delta` (latest − window start) and
  `best` are measured within it. Exercises are ordered most recently trained
  first and named and typed by their latest session. No schema change, no new
  repository method.
- **Progress tab (plan items 1, 5, 7; `presentation/progress/`).** Replaces
  CP2's `ProgressPlaceholder` (deleted) at the `PROGRESS` route:
  `ProgressRoute` / `ProgressViewModel` (`@HiltViewModel`, live over the read
  model) / `ProgressUiState` (selection with fallbacks: an absent exercise →
  the most recently trained, an unoffered metric → `Top set`, the user's metric
  restored when they return) / `ProgressScreen` (title, sideways-scrolling
  exercise chips, the three-segment metric control, the card, the note
  "Only valid sessions count…" with `info`) / `ProgressCard` (exercise and
  signed delta `+10 kg since 5 May`, the value at 30/500 with `kg` / `kg total`
  / `reps` / `s`, the bars drawn with Compose `Canvas` - latest in the accent,
  month labels where a month starts, one spoken description - and
  `Best: 82.5 kg · 12-session window`; fewer than two points shows `1d`'s
  empty treatment in the card; a reps-only or timed exercise says `Est. 1RM
  and Volume need a recorded load.`) / `ProgressModel` (bar heights by the
  prototype's 14–96% rule, month labels, signed numbers). No history shows the
  tab's `1d` empty state. **No new dependency** - no charting library.
- **Deviations:** `D108`–`D111` added; `O12` closed. Flagged for the reviewer:
  `D109` (Brzycki and the 12-rep ceiling) and `D110` (window and empty-state
  copy). Next free register id: **D112**. No new glyph (`check-fat`, `info`,
  `chart-line-up` already existed).
- **Copy:** new `progress_*` strings (metric labels, units, delta, best line,
  metric empty state, the `D22` line, the note, the chart's spoken
  description as a plural); `progress_placeholder_empty` renamed
  `progress_empty`, same words.
- **Tests.** New JVM: `EstimatedOneRepMaxTest` (5), `ExerciseProgressTest`
  (10 - plan item 8's enumeration: the offered set and each value per tracking
  type, reps-only and duration with many sessions still `Top set` only and not
  empty, one weight-and-reps session in the empty state, an unloaded session
  giving no point; plus item 4's pin that invalidating a session removes its
  point from the live `ObserveExerciseProgress` flow, the 12-session window,
  ordering/naming, a duplicated exercise), `ProgressModelTest` (4),
  `ProgressViewModelTest` (4). New instrumented (compile only):
  `ProgressScreenTest` (6). Rewritten: `MainActivityNavHostSmokeTest.progressTabOpensWithoutCrashing`
  (waits for `progress_empty` - the placeholder's string id is gone).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `domain.progression.*`, `application.progress.*`, `presentation.progress.*`,
  `presentation.navigation.*`, `presentation.designsystem.*`,
  `architecture.LayerBoundaryTest` - 13 classes, 109 tests, 0 failures;
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` -
  green; lint 0 errors, 21 warnings and 1 hint (baseline), none in the
  progress package (a `PluralsCandidate` on the new chart description was
  fixed by making it a plural). **Not run (no device):** `ProgressScreenTest`
  (6) and the rewritten smoke method need `connectedDebugAndroidTest`, as do
  CP2–CP14's.

### CP14 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  Settings (`RepFlow.dc.html:1052–1111`), its restore sheet (`:1461–1487`),
  `nTimerRows` / `nSetRows` (`:3928–3946`) and `5d`'s CSV row (`:657`). Built
  to `4a`, per the user's 2026-10-01 decision on `O11`.
- **Schema 7 → 8 (plan items 7, "Migration and schema impact").** One table,
  `settings`: a single row pinned at `id = 1`, one typed `INTEGER NOT NULL`
  column per switch (no enum-valued preference exists, so no `TEXT` column).
  `MIGRATION_7_8` is the plain `CREATE TABLE` plus `INSERT OR IGNORE` of the
  default row (auto-start, vibrate, notification and confirm-before-finishing
  on, keep screen awake off - `D21`); `SETTINGS_SEED_CALLBACK` seeds the same
  row on a fresh install; `DatabaseModule` registers both; exported schema
  `8.json`. No existing table, column or enum changes; no destructive
  migration. `LocalSettingsRepository` reads an absent row as
  `AppSettings.DEFAULT` and writes the whole row in a transaction.
- **Layers.** `application/settings/` (`AppSettings`, `SettingsRepository`);
  `application/backup/` (`TrainingDataRepository` port, `EraseAllData`);
  `data/settings/LocalSettingsRepository`, `data/backup/LocalTrainingDataRepository`
  (the ten training tables, children first, one `withTransaction`, never
  `clearAllTables()`); `infrastructure/database/` (`SettingsEntity`,
  `SettingsDao`, `TrainingDataDao`). Bound in `RepositoryModule`.
- **Restore path (item 9).** `LocalBackupRepository.replaceAll` now clears
  through `TrainingDataRepository.clearTrainingData()` inside its own
  transaction (a failed clear aborts it) instead of `clearAllTables()`, so the
  settings row survives a restore; its KDoc, `BackupRepository.replaceAll`'s,
  `RestoreBackup`'s and `BackupSnapshot`'s now say "all local **training**
  data" and that preferences are deliberately not in the snapshot.
- **Rest timer (item 3).** `ActiveWorkoutViewModel` gains `SettingsRepository`:
  `onRecordSet` starts rest only while auto-start is on (read when the set is
  logged); `settings` and `notificationEnabled` are `StateFlow`s that are
  `null` until the repository emits. The receiver is `@AndroidEntryPoint`
  (bytecode checked: Hilt's transform calls `Hilt_…onReceive`, which injects)
  and hands off through `goAsync()` on `Dispatchers.IO` to the injected
  `RestTimerExpiryHandler`, which reads the switches **when the alarm fires**
  and runs `restAlertPlan`'s two halves: the notification (permission check
  first, unchanged; today's `rest_timer` channel, sound kept, no vibration,
  never deleted) and an explicit one-shot buzz through `RestAlertVibrator`
  with `USAGE_NOTIFICATION` (`VibrationAttributes` on 33+, `AudioAttributes`
  on 28–32), bound to `SystemRestAlertVibrator` by `RestAlertModule` in
  `presentation/workout/`. The route schedules the alarm for every rest and
  asks for `POST_NOTIFICATIONS` only through `RestTimerPermissionPromptEffect`
  (Notification on, loaded, API 33+, not granted).
  `RestTimerAlarmScheduler.scheduledPendingIntent` is the test hook. Receiver
  KDoc rewritten. **Visible change:** with the defaults, rest end now buzzes
  in normal ringer mode.
- **During a workout (item 4).** `Keep screen awake` sets the hosting view's
  `keepScreenOn` while the board or focus mode is composed (off by default).
  `Confirm before finishing` gates the one finish request: off, every entry
  point - the board's and focus mode's `Finish`, the leave sheet's `Finish and
  save it now`, `Next ›` with nothing left, and Home's `Finish it` - completes
  at once and lands on the done screen; `null` (not loaded) counts as on for a
  tap, and Home's `Finish it` waits for the value.
- **Settings screen (items 1, 2, 5, 8; `presentation/settings/`).** Replaces
  `SettingsPlaceholder`: `Library` → `Exercise library` (`D25`), `Rest timer`
  and `During a workout` switches (the whole row toggles, announced as a
  switch, disabled until loaded), `Data` - `Export a backup` (`Saved` once
  written), `Restore from a file`, `Workout history as CSV` - the
  `Irreversible` card and `Erase all data` behind a typed confirmation, and the
  footer. No `Units` group (`D5`). The Backup screen and its route are retired:
  its SAF wiring moved unchanged into `presentation/backup/BackupFileActions.kt`,
  `BackupViewModel` untouched (`D103`).
- **Erase all data (item 6).** `EraseAllData` → `clearTrainingData()`: every
  training table, an active session included, in one transaction; the
  settings row and exported files untouched; the confirmation copy says both.
- **Deviations:** `D103`–`D107` added; `O11` closed. Flagged for the reviewer:
  `D103` (Backup screen retired, CSV row kept), `D105` (erase confirmation
  copy). Next free register id: **D108**. Four Phosphor glyphs added
  (`database`, `table`, `check`, `warning`); `cloud-arrow-up` retired with
  the Backup screen.
- **Tests.** JVM: `ActiveWorkoutViewModelTest` (fixture + 2: auto-start on
  starts rest, off records the set with no rest), `ActiveWorkoutFocusPlumbingTest`
  (fixture only - **not in the plan's enumeration**: it also constructs the
  ViewModel), new `RestAlertTest` (4: the four combinations, the prompt rule,
  the usage constants), `SettingsViewModelTest` (7), `EraseAllDataTest` (2),
  `RepFlowIconsTest` (expected set). Instrumented (compile only):
  `RepFlowDatabaseMigrationTest` (+2, `MIGRATION_7_8`),
  `LocalBackupRepositoryAtomicityTest` (**retargeted**: KDoc, the third test's
  name and message now name the scoped clear; mechanism kept),
  `LocalBackupRepositoryVersionCompatibilityTest` (constructor only), new
  `SettingsPersistenceTest` (4: fresh seed, update, erase keeps settings and
  discards the active session, a pre-milestone schema-2 backup restores all
  ten collections and keeps the settings row), `RestTimerExpiryHandlerTest`
  (6: every combination × permission, without and with a pre-created channel;
  each switch flipped both ways after scheduling), `RestTimerReceiverDeliveryTest`
  (2, real Hilt graph via `MainActivity`), `RestTimerPermissionPromptEffectTest`
  (4), `KeepScreenAwakeTest` (4), `SettingsScreenTest` (4),
  `ActiveWorkoutLeaveRouteTest` (+3: confirm off via Home's `Finish it` and the
  board's `Finish`, and back on restores the sheet; fixture edit),
  `ProgressionRecommendationRouteTest` (fixture edit), the smoke test's two
  Settings walks and the two backup route tests' opening navigation (the four
  string ids kept).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.workout.*`, `presentation.settings.*`, `presentation.backup.*`,
  `application.backup.*`, `presentation.designsystem.*`,
  `presentation.navigation.*`, `data.backup.*`,
  `architecture.LayerBoundaryTest` - 21 classes, 151 tests, 0 failures;
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` -
  green; lint 0 errors, 21 warnings and 1 hint, none in the touched packages.
  **Not run (no device):** every instrumented test above, including the
  `MIGRATION_7_8` test, needs `connectedDebugAndroidTest`, as do CP2–CP13's.

### CP13 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `3c`
  (`RepFlow.dc.html:2017–2092`) and `3d` (`:2095–2203`), their script
  (`SCALES` `:3126–3133`, `RECOV` `:3089–3096`, `recVals` `:4408–4466`), and
  `6b`'s stepper and scale row (`:289–308`).
- **Recovery entry (`3c`, plan items 1, 2 and 5; `RecoveryFutsalScreen.kt`,
  new `RecoveryFutsalBlock.kt`, `RecoveryScales.kt`):** CP3's sub-screen bar
  (`Recovery`, a back arrow it never had - Recovery stopped being a tab in CP2 -
  and `History` at 13.5 in the accent, keeping its content description); the
  date row (`calendar-blank`, `Today · 11 Aug 2026`, `Change`), whose date
  picker moved from a dialog into CP3's sheet (`Cancel` / `Set`, still today or
  earlier only); the **six `-`/`+` steppers replaced by CP3's
  `RepFlowScaleRow`**, each labelled at both ends; `Futsal` with the two 52-tall
  toggles (`Played in last 24h`, `Playing in next 24h`; a checkbox to
  accessibility, `check-circle` while on); while `Played` is on, the card with
  the `Minutes` (±5) and `Session RPE` (±1, 0–10) steppers - CP3's stepper,
  value opening CP3's keypad - the hint, and `Training load 420 — minutes ×
  RPE` (`soccer-ball`); `Notes`; and one pinned `Save entry` that reads `Saved`
  with a check until the next edit.
- **Polarity (plan item 5):** the end labels are chosen *from*
  `ReadinessFactor.inverted` - `RecoveryScaleField` now names its readiness
  factor - so DOMS, heel stiffness, pain while walking and heavy legs run
  `None → Severe` and sleep / energy `Terrible → Great` / `Flat → Fresh`; a
  non-inverted factor without its own words fails at initialisation. CP13 reads
  no score and changes no readiness input.
- **One save (D98):** `RecoveryFutsalViewModel.onSaveEntry` replaces
  `onSaveRecovery` / `onSaveFutsal`: it saves the check-in and, while `Played`
  is on, the session; both futsal fields empty saves the check-in alone; one
  without the other (or a non-number) is rejected before anything is written.
  `isSaving` / `isEntrySaved` replace the two in-flight flags and the two saved
  messages; any edit clears `isEntrySaved`. The `Saved` snackbar is retired for
  the bar's own `Saved` (a polite live region); errors still use the snackbar,
  now the design's toast card. `today` is new UI state from the injected clock.
- **Recovery history (`3d`, plan items 3 and 4; `RecoveryHistoryScreen.kt`,
  new `RecoveryHistoryModel.kt`, `RecoveryTrendChart.kt`,
  `RecoveryTrendDrawing.kt`):** CP3's sub-screen bar; the trend card -
  `Sleep & energy · 14 days`, `avg 3.6 / 3.1`, the selected-day readout, the
  5 / 3 / 1 axis, sleep solid in the accent and energy dashed, futsal dots on the
  baseline, a dashed marker on the selected day, day ticks every third day,
  `Tap any day for its values` and the futsal legend - drawn with Compose
  `Canvas`, **no charting dependency**; then `Entries` (`Today · Sleep 4 ·
  Energy 3 · DOMS 2`, the ball for a check-in that played in the last 24h) and
  `Futsal sessions` (`10 Aug · 50 min · RPE 8 · load 400`). The window is
  `recoveryTrendOf` - pure: the 14 days ending today, gaps left unbridged,
  averages over the window's check-ins. `RecoveryHistoryViewModel` gains the
  injected `Clock` (its one new constructor argument) for `today`.
- **Not built:** `3d`'s insight card (`D101` - its second sentence is false
  against `ProgressionPolicyV1`, which holds back every exercise for 24 hours
  after a recorded session, not leg work for two days).
- **Deviations:** `D97`–`D102` added; `O10` closed. Flagged for the reviewer:
  `D98` (one save, the optional futsal session, the retired snackbar) and
  `D101` (insight card not built). Next free register id: **D103**.
- **Copy changed:** the entry's labels (`History`, `Change`, `Set`, `Played in
  last 24h`, `Playing in next 24h`, `Minutes`, `Session RPE`, `Notes`, the load
  line), the history's `Entries` and its row (`DOMS` in place of `Pain`, `D102`),
  the futsal row (`50 min · RPE 8` + `load 400`). New: the six end-label words,
  the stepper descriptions and hint, the notes prompt, `Save entry`, the date
  sheet's title, and the chart's strings. Removed: `recovery_history_back`,
  `recovery_futsal_recovery_section_title`, `recovery_futsal_save_recovery`,
  `recovery_futsal_save_futsal`. One new glyph, `soccer-ball` (Phosphor
  regular, MIT), with `RepFlowIconsTest`'s expected set extended.
- **Tests (plan item 6's enumeration, as built):** the plan's grep is empty
  for the two screens, as it said - **but the destination smoke test reads the
  entry screen by a string**: `MainActivityNavHostSmokeTest.recoveryIsReachableFromHome`
  asserted `recovery_futsal_recovery_section_title` (`Recovery entry`), a heading
  `3c` does not have. It is rewritten to assert the pinned `Save entry`
  (`recoveryHistoryIsReachableBehindRecovery` is unchanged - its content
  description and title survive). The plan's grep is closed on composable
  invocations and the smoke test navigates instead, which is why it could not
  see it. **`RecoveryFutsalViewModelTest` did not survive as a pure affordance
  swap**, though the plan expected it to: its saves are `onSaveEntry` now, so
  every method that called `onSaveRecovery` / `onSaveFutsal` is retargeted
  (9 → 14); the two the plan named as the domain pins (`… persists the current
  scale values`, `… up to the widened maximum of 5`) keep their stored-value
  assertions verbatim, the futsal-error method additionally asserts the check-in
  was not written, and the `"saved"` message assertions become `isEntrySaved`.
  **New:** both futsal fields empty saves the check-in only; `Played` off saves
  no session; an edit clears `Saved`; `today`; and every scale row shows the
  value its readiness factor reads (item 5, end to end through the load).
  `RecoveryHistoryViewModelTest` - **fixture edit only** (the clock), both
  assertions untouched. New JVM: `RecoveryScalesTest` (4: order, polarity,
  words, labels) and `RecoveryHistoryModelTest` (7). New instrumented:
  `RecoveryFutsalScreenTest` (9) and `RecoveryHistoryScreenTest` (6).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.recovery.*`, `presentation.designsystem.*`,
  `presentation.home.*`, `application.recovery.*`, `domain.recovery.*`,
  `architecture.LayerBoundaryTest` - 20 classes, 155 tests, 0 failures;
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` -
  green; lint 0 errors, 21 warnings and 1 hint, none in the recovery package.
  **Not run (no device):** `RecoveryFutsalScreenTest` (9),
  `RecoveryHistoryScreenTest` (6) and the rewritten
  `MainActivityNavHostSmokeTest.recoveryIsReachableFromHome` compile but need
  `connectedDebugAndroidTest`, as do CP2–CP12's.

### CP12 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `3a`
  (`RepFlow.dc.html:1830–1956`) and `3b` (`:1958–2014`), their script
  (`SESSIONS` `:3072–3085`, `histVals` `:4369–4406`), and `4a`'s History tab
  and detail (`:952–1011`, script `:3699–3730`).
- **History list (`3a`, plan item 1; `HistoryScreen.kt`, new
  `HistoryFilterChips.kt`, `HistoryModel.kt`):** CP3's top-level frame
  (`History` at 25/500); the existing filters as `3a`'s chips at 36 tall inside
  44dp targets, in a `FlowRow` — the plan (a sheet: `Any plan`, `No plan`, each
  plan), `Show invalidated` (a toggle chip, `prohibit`), `From any date` /
  `To any date` (each a sheet with Material's date picker, `Clear`, `Set`) and
  the exercise (a sheet); `N workouts · newest first`, where the order word is
  the sort toggle (`Change order`); month section labels; and one 68-tall row
  per workout — the plan's name (or `Untitled workout`) at 15.5/500 with a `PR`
  badge (`medal`) and an `invalidated` badge (`prohibit`), the meta
  `Sat 8 Aug · 10:20 · 52 min · 18 sets · 6,720 kg`, a caret. The two
  `DropdownMenu`s and the date-picker dialog are gone; the empty and
  no-match states are `RepFlowEmptyState`; the snackbar is the design's card
  and is now hosted over the detail too. `HistoryFilters` and
  `HistoryUiState`'s filtering and sorting are unchanged.
- **PR badge (plan item 1, "where derivable"):** derived, never stored —
  `sessionsWithPersonalBests` (`ObserveWorkoutSummary.kt`, pure, one pass) badges
  exactly the valid sessions whose done-screen summary lists a best set (CP9's
  `D68` rule); invalidated workouts never carry it. `HistoryViewModel` computes
  it on every emission into the new `HistoryUiState.personalBestSessionIds`
  (default empty); its constructor is unchanged.
- **Workout detail (`3b`, plan item 2; `HistoryDetailScreen.kt`):** CP3's
  sub-screen bar (back keeps `history_detail_back` as its content
  description) with `3a`'s `⋮`; `SAT 8 AUG · 10:20 → 11:12`; the name at
  28/500; `<plan> · version N` (or `No plan`) with an `invalidated` badge on an
  invalidated workout; `Time` / `Volume (kg)` / `Sets` tiles; one block per
  exercise — name, `N warm-up sets`, CP9's change against last time (`+2.5 kg`,
  `same load`, `first time` …, medal for a personal best), then every logged set:
  working sets numbered, warm-ups marked `warm-up`, the value by tracking type
  (`70 kg × 10`, `12 reps`, `45 s`), `RPE 8`, and `Pain 2/5` / `Technique 4/5`
  below. A zero-set exercise reads `No sets logged` with no change (`D20`). The
  detail is now given a `WorkoutSummary` (from `workoutSummaryOf` over the
  loaded sessions) and the session's `TrainingPlanVersionLabel`.
- **Version number:** `TrainingPlanVersionLabel` gains `versionNumber` (no
  default), filled from the existing `training_plan_versions.version_number`
  column in `LocalTrainingPlanRepository.observeVersionLabels` — **no query,
  table or migration change; Room stays at version 7.**
- **Invalidation (plan items 3 and 6):** the trigger moved from the row to the
  detail's `⋮`, named `Invalidate workout` and absent on an invalidated
  workout; it keeps the destructive confirmation (`Invalidate this workout?`,
  `Keep it` / `Invalidate workout` in the error tone with `prohibit`). Its copy
  no longer promises a way back that no use case offers (`D93`). The ViewModel's
  `onInvalidateClicked` is unchanged and still closes the detail on success.
- **DURATION defect (plan item 5):** fixed — the row picks its words by
  tracking type (`historySetValueOf`), so a timed set reads `45 s` and never
  `Set 0:  kg x `; `history_detail_set_row` is removed.
- **Not built:** the set-edit pencil (`D7`, open decision), the session note
  (`D3`), `This plan` (`D88`).
- **Deviations:** `D88`–`D96` added; `O9` closed (`D88`, `D92`; month sections,
  the `invalidated` badge and `<plan> · version N` built). Flagged for the
  reviewer: `D93` (the `⋮` opens the destructive dialog directly and is named
  for it; reworded dialog copy), `D94` (volume unit in the caption), `D95`
  (every logged set listed, warm-ups included). Next free register id:
  **D97**.
- **Copy changed:** `history_empty`, `history_empty_no_matches`,
  `history_filter_exercise_all` (`Any exercise`), `history_filter_plan_ad_hoc`
  (`No plan`), the date chip labels, `history_filter_sort_newest` /
  `_oldest` (lower case, inside the count line), `history_date_picker_confirm`
  (`Set`), `history_session_invalidate_action` (`Invalidate workout`, now the
  `⋮`'s description), `history_invalidate_dialog_message` and `_cancel`
  (`Keep it`), and the set-field strings (`RPE 8.5`, `Pain 2/5`, `Technique
  4/5`, `warm-up`, `45 s`). Removed: `history_session_summary`,
  `history_detail_set_count`, `history_detail_set_row`,
  `history_session_headline_invalidated`, `history_date_picker_dismiss`. Two new
  glyphs, `prohibit` and `calendar-blank` (Phosphor regular, MIT), with
  `RepFlowIconsTest`'s expected set extended.
- **Tests (plan item 6's enumeration, as built):** instrumented
  `HistoryScreenTest` (9 → 10): `rendersContentRows` rewritten (name, meta and
  count line shown; no invalidate action on the list); the three invalidate
  methods rewritten to open the detail (a selected session) and click the `⋮`
  by its description, same assertions; `exerciseFilterMenu…` →
  `exerciseFilterSheetInvokesOnExerciseFilterChanged` (asserts the sheet);
  **`rowClickInvokesOnSessionClick` rewritten too, though the plan listed it
  untouched** — it located the row by the old `d MMM yyyy, HH:mm` text, which
  the converted row no longer shows, so it now clicks the row by its name;
  **new** `rowsCarryThePrAndInvalidatedBadges`; the snackbar, sort and
  show-invalidated methods unchanged. `HistoryDetailScreenTest` (3 → 5): the two
  set-row methods rewritten against the converted rows asserting the same five
  facts (the warm-up marker is now an exact match, since the `1 warm-up set`
  line also contains the word); `backButtonInvokesOnBackClick` rewritten from
  text to content description (CP3's back is an icon); **new**
  `aZeroSetExerciseShowsNoSetsLoggedAndNoDelta` and
  `aDurationSetHasNoLoadAndRepsTemplate` (item 5's defect test). JVM: new
  `HistoryModelTest` (7); `ObserveWorkoutSummaryTest` + 2 (the badge set equals
  the summaries' and same-instant sessions); `ObserveTrainingPlanVersionLabelsTest`'s
  first method also asserts version numbers (renamed);
  `HistoryUiStateTest` — **fixture edit only** (the label's new argument), its
  nine assertions untouched; `HistoryViewModelTest` untouched (8/8), as planned
  — the one-line badge wiring is covered by `ObserveWorkoutSummaryTest`, not by
  a ViewModel test.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.history.*`, `application.history.*`,
  `application.trainingplan.*`, `presentation.designsystem.*`,
  `presentation.home.*`, `presentation.workout.*`, `application.backup.*`,
  `presentation.backup.*`, `LayerBoundaryTest` — 32 classes, 212 tests, 0
  failures; `spotlessCheck detekt lintDebug assembleDebug
  assembleDebugAndroidTest` — green; lint 0 errors, 22 warnings and 1 hint (two
  fewer than before: the removed `%d exercises` / `%d sets` strings). **Not run
  (no device):** `HistoryScreenTest` (10) and `HistoryDetailScreenTest` (5)
  compile but need `connectedDebugAndroidTest`, as do CP2–CP11's.
- **Observed, not changed:** the system back gesture on the detail is not
  intercepted (pre-existing — the detail is a state of the History route, not a
  destination), so it leaves History rather than closing the detail; only the
  bar's back arrow closes it. Not in CP12's items; noted for the functional
  review.

### CP11 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `5a`
  (`RepFlow.dc.html:345–409`) and its script (`:3410–3438`); `4a`'s Plans tab
  and plan edit (`:825–950`), their script (`:3609–3694`) and the plan picker
  sheet `nPlanPickOpen` (`:1635–1659`); `2a` (`:2212–2370`) and its script
  (`:4470–4541`).
- **Plans list (`5a`, plan item 1; `TrainingPlanListScreen.kt`,
  `TrainingPlanListCards.kt`):** CP3's top-level frame (`Plans` at 25/500),
  the `Active` / `Archived` pills at `5a`'s size inside 44dp targets, and one
  card per plan (radius 14, padding 16, surface inside a hairline): the name at
  17/500, `N exercises · vN` (no days, `D4`), an archived plan's `Archived <d
  MMM yyyy>`, `Archive` / `Restore` **on the card face** (44 tall, `D79`), then
  `Start workout` (accent primary, filled `play`, 46 tall) on an active plan
  and `Open` (`5a`'s word over `4a`'s `Edit`, `D78`). The card body still opens
  the plan, as the row did. `5a`'s empty card (`list-checks`, `No active plans.
  Restore one, or create a plan.` / `Nothing archived.`), the footnote
  `Archiving never touches completed workouts.` (`D80`), the archive snackbar
  as `5a`'s card (`archive` glyph, accent `Undo`), and `New plan` as a 52 accent
  outline on `RepFlowBottomActionBar` in place of the FAB (keeping the FAB's
  content description, whose text is now `New plan`). The row `DropdownMenu`
  is gone.
- **Start action (plan item 1, `O8` → `D77`, flagged for the reviewer):**
  `TrainingPlanListViewModel` gains `StartWorkoutSessionFromPlan` (a fourth
  constructor dependency), `onStartClicked(planId)` — the plan's **latest
  version**, read with one `observeTrainingPlans(ACTIVE).first()` exactly as
  Home's `startFromPlan` does, then the same use case Home's start card calls —
  and `onWorkoutOpened()`. Success raises `TrainingPlanListUiState.openWorkout`
  (Home's navigation-via-stable-state flag); the route clears it and opens
  `workout` (`RepFlowNavHost`: `onOpenWorkout`). A workout already running is
  refused with the new message `WorkoutAlreadyActive` → `A workout is already
  running.`; any other refusal is the existing `OperationFailed`. A second tap
  while one start is in flight is ignored. Archived cards have no start.
  `TrainingPlanListItem` gains `versionNumber` and `archivedAt`, no defaults.
- **Plan editor (`4a` + `2a`, plan item 2; `TrainingPlanEditorScreen.kt`,
  `…FormFields.kt`, new `…Rows.kt`, `…Targets.kt`, `…Picker.kt`,
  `…RowModel.kt`):** CP3's sub-screen bar (`Edit plan` / `New plan`, real
  `arrow-left`, the existing `Back` description), the name as `2b`'s 52-tall
  field (`D81`), `EXERCISES` with `N exercises · N working sets`, and one row per
  planned exercise at `4a`'s 64: `2a`'s stacked up/down carets (44×32, `D82`),
  the name over `3 × 8–12 reps · 1 warm-up · 90s rest` (or `Check this
  exercise's targets.` when a field is invalid), `2a`'s `optional` badge, a
  caret, and a 44 `trash` — **reorder and remove stay on the collapsed row**,
  which is what keeps plan item 5's three row-action tests reachable. Tapping a
  row **expands it in place** (one at a time) into `4a`'s stepper lines on CP3's
  stepper (44 buttons, value 16 tabular, the value opens the keypad): working
  sets, warm-up sets, min/max reps or min/max seconds (`D83`), rest (step 15,
  shown `m:ss`) with `2a`'s six presets, then `Optional` (a checkbox toggle) and
  `Change exercise` (`D84`). Each stepper writes the row's existing text field
  through the unchanged `String` setters; field errors show under their line.
  Steps and bounds are `PlanRowStepping` (pure): the domain's own `TargetSets` /
  `RepRange` / `DurationTarget` / `RestDuration` limits, and an empty field's
  first tap lands on `2a`'s new-row defaults (3 sets, 8–12, 30–60 s; rest 1:30).
  `Save` / `Saving…` is the bottom bar's primary (`D85`); the discard dialog
  stays. **The ViewModel is unchanged.**
- **Picker as a sheet (plan item 2, `D87`):** the row `DropdownMenu` became
  `4a`'s sheet — `Add to plan`, a 48 search over names, `Create a new exercise`
  (opens the exercise editor; `onCreateExercise` on the route, wired in
  `RepFlowNavHost` for both editor routes), 60-tall rows with an accent
  `plus`. `Add exercise` still calls `onAddRowClicked`; the screen then opens the
  sheet for the new empty row as soon as it appears, and a choice fills it via
  `onExerciseSelected` and expands it. A row still without an exercise reopens
  the sheet from its header.
- **Plan-version note (plan item 3):** "Changes apply to the next session you
  start from this plan. Past workouts keep the version they were run on."
  (`check-circle`, under the rows) when editing an existing plan — "this day"
  reads "this plan" (`D4`).
- **Deviations (plan item 4) and `O8`:** no day tabs, `Active plan` badge, `Make
  active` or superset toggle (`D1`, `D4`); `D76`–`D87` added, `O8` closed
  (`D77`, `D78`, `D84`, `D85`, `D86`). Flagged for the reviewer: `D76` (`Last
  used` not built), `D77` (start directly from the card), `D82` (carets 32 tall,
  below the 44 floor), `D85` (no `Saving creates version N` bar). Next free
  register id: **D88**.
- **Copy changed:** `training_plan_list_add_content_description` → `New plan`;
  `training_plan_list_empty` / `_empty_no_archived` → `5a`'s two lines;
  `training_plan_editor_no_exercises` → `Nothing in this plan yet. Add the
  exercises you want to hit.`; the row labels → `Working sets`, `Warm-up sets`,
  `Min seconds`, `Max seconds`, `Rest`. `training_plan_list_row_menu_*` became
  `training_plan_list_card_*`; `training_plan_list_exercise_count` was removed
  (the meta is now the `training_plan_list_card_exercise_count` plural).
- **Tests (plan item 5's enumeration, as built):** instrumented
  `TrainingPlanListScreenTest` (11 → 12): `createFabClickInvokesOnCreateClick` →
  `createBarButtonClickInvokesOnCreateClick` (same content description); the
  two archive/restore methods → `cardShowsArchive…` / `cardShowsRestore…`
  (click the card's own action, and assert the other one and an archived
  card's start are absent); **`rowMenuEditItemInvokesOnPlanClick` is rewritten,
  not retired** — `Open` is its own affordance, so it becomes
  `cardOpenButtonInvokesOnPlanClick`; **new** `cardStartButtonInvokesOnStartClickWithoutOpeningThePlan`;
  `rendersContentRowsAndInvokesOnPlanClick` unchanged in intent (fixture via
  `planItem()`, plus the meta line); the rest unchanged.
  `TrainingPlanEditorScreenTest` (15 → 17): helper gains
  `onCreateExerciseClick`; the three row-action methods untouched and still
  reach their descriptions on the collapsed row; **new**
  `aRowWithNoExerciseOpensThePickerSheetAndTheChoiceFillsThatRow` and
  `addExerciseOpensThePickerSheetForTheNewRowOnceItAppears`. JVM:
  `TrainingPlanListViewModelTest` (11 → 14) — **not "unexamined" as plan item 5
  expected**: the start action needs the new constructor argument, so the
  fixture changes, and three tests are added (version and archive instant on
  the item; start opens the workout from the latest version; start while a
  workout runs reports it and keeps the running session). New
  `PlanRowSteppingTest` (6). `TrainingPlanEditorViewModelTest` (8) untouched.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.trainingplan.*`, `application.trainingplan.*`,
  `presentation.navigation.*`, `presentation.designsystem.*`,
  `presentation.home.*`, `LayerBoundaryTest` — 20 classes, 143 tests, 0
  failures (one earlier run of the same set saw a single 3 s Turbine timeout in
  the pre-existing `onRetry resubscribes and still reflects current content`;
  it did not recur in 15 isolated runs of that class and 3 forced re-runs of
  the full set); `spotlessCheck detekt
  lintDebug assembleDebug assembleDebugAndroidTest` — green; lint 0 errors, 24
  warnings and 1 hint (one fewer than before: the removed
  `training_plan_list_exercise_count`). **Not run (no device):**
  `TrainingPlanListScreenTest` (12) and `TrainingPlanEditorScreenTest` (17)
  compile but need `connectedDebugAndroidTest`, as do CP2–CP10's.
- **Room stays at version 7**: no query, table, migration or domain change; the
  one new write path is the existing `StartWorkoutSessionFromPlan`.

### CP10 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `2c`
  (`RepFlow.dc.html:2418–2487`) and `2b` (`:2373–2416`).
- **Library (`2c`, plan items 1, 2, 5; `ExerciseListScreen.kt`,
  `ExerciseListRows.kt`):** CP3's sub-screen bar with a back arrow (the
  library is pushed from Settings; `D74`), a 48 search field (surface fill,
  hairline, radius 10), the `Active` / `Archived` chips drawn at `2c`'s pill
  size inside 44dp tap targets (`ExerciseFilterChipMinHeight`; the funnel
  glyph went with the muscle-group chip it belongs to, `D8`), `N matches`
  while a search is active, flat rows at `ExerciseRowMinHeight` (64, kept as
  the row's `heightIn`) — the name (two lines) over `<type> · rest m:ss · in N
  plans` / `not in any plan` — an archived row dimmed to 60% with its
  `archived` badge, and a 44dp `⋮` (`D36`) that opens a **row-action sheet**
  (Edit; Archive, or Restore on the archived filter) in place of the
  `DropdownMenu`; the trigger keeps its content description. Create is the
  bottom action bar's primary (`Add exercise`, the FAB's accessible name;
  `D35`) — `ExerciseFabSize` and the FAB are retired. The archive snackbar is
  `2c`'s card (surface fill, ring, `arrow-counter-clockwise`, accent `Undo`).
- **`Create "<query>"` (plan item 3):** `Not here? Create “<query>”` under the
  results while searching the active list, and in place of them when nothing
  matches. The create route gained one optional argument
  (`exercises/new?name={name}`, `EXERCISE_NEW_PATTERN`, nullable, default
  null), so the bare `exercises/new` (workout picker, the bar) still resolves;
  the mode stays derived from `EXERCISE_EDIT_ARG` only.
  `ExerciseEditorViewModel` seeds the draft name from it only as the fallback
  under a restored draft, and `isDirty`'s name clause became `name.trim() !=
  prefill.trim()`. `ExerciseListContent.Empty` keeps its shape;
  `exercise_list_empty_no_search_results` keeps its id with the text `Not
  here?`; the screen gained `onCreateFromQueryClick: (String) -> Unit` (and
  `onBackClick`).
- **Plan usage (plan item 5):** `TrainingPlanRepository.observeExercisePlanUsage()`
  (one read query, `PlannedExerciseDao.observeExercisePlanUsage` over the
  existing three tables — non-archived plans, latest version only, a plan
  counted once per exercise; **no schema change, Room stays at version 7**),
  the use case `ObserveExercisePlanUsage`, a fourth `ExerciseListViewModel`
  dependency combined inside the same `flatMapLatest` (a failure in either
  source is the existing retryable `ObservationFailed`), and
  `ExerciseListItem.planUsageCount` with no default.
- **Editor (`2b`, plan item 4; `ExerciseEditorScreen.kt`,
  `ExerciseEditorFormFields.kt`):** CP3's sub-screen bar (real `arrow-left`
  replacing the `<` text) and `Save` on the bottom action bar (`D70`); the
  name field 52 tall with `2b`'s inline error — a refused duplicate name now
  reports under the name field with the error ring and `warning-circle`
  (`D72`); `Tracking type` as `2b`'s three-segment control, the selected
  segment tinted and checked; `Default rest` presets `1:00 1:30 2:00 3:00
  Other` and `Load step` presets `1.25 kg 2.5 kg 5 kg Other` on `6b`'s
  scale-row cells — a preset calls the same `String` setter typing does,
  `Other` opens the typed field (and stays selected for any non-preset value,
  so a restored or invalid draft is never hidden), and **tapping the selected
  preset again clears the optional default** (the design is silent on
  clearing; flagged for the reviewer); technique notes with `2b`'s label and
  prompt. Field labels stay inside the text fields (`D71`).
  `onTrackingTypeChanged`'s clear-the-load-and-queue-a-message rule is
  unchanged. The ViewModel's input contract is unchanged.
- **Copy changed** (named here, per plan item 6): `exercise_list_empty_no_exercises`
  → `No exercises yet. Add one below.` (the `+` it pointed at is gone);
  `exercise_list_row_archived_badge` → `archived` (as drawn);
  `exercise_editor_instructions_label` → `Technique notes — shown during the
  workout`. The unused `exercise_default_rest_seconds_summary` /
  `exercise_default_load_increment_summary` were removed (the meta no longer
  shows the load step, plan item 5).
- **`O7` closed:** `D70`–`D73`; `N matches` and the `Other` presets built. `D73`
  (the tracking-type filter chip not built) is **flagged for the reviewer**.
  Also added `D74` (header), `D75` (archived rows only under `Archived`).
  Next free register id: **D76**.
- **Tests (plan item 6's enumeration, as built):** JVM —
  `ExerciseListScreenWiringTest`: `rowAndFabSizesAreTheDesignsOwn` →
  `rowHeightIsTheDesignsOwn` (row half kept), `everyTapTargetClearsTheMinimum`
  rewritten against the bar's primary, the chips, the overflow and the badge;
  the other three unchanged. `ExerciseListViewModelTest` fixture edit + 1 new
  (usage counts non-archived plans and follows an archive live; 13/13).
  `ExerciseEditorViewModelTest` + 3 new (prefill starts named and saveable;
  an edited prefill survives recreation; an untouched prefill backs out
  without the dialog; 19/19). `InMemoryTrainingPlanRepository` gains the
  query. Instrumented — `ExerciseListScreenTest`: `rendersContentRows`
  (count + meta), `rendersTheNoSearchResultsEmptyState` (new copy + the tap
  carries `zzz`), `createFabClickInvokesOnCreateClick` →
  `createBarButtonClickInvokesOnCreateClick` (same content description), the
  three row-menu tests unchanged in code (their second click now lands on the
  sheet), helper fixture edits; `ExerciseEditorScreenTest`:
  `loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad` rewritten
  to first show the `Load step` row exists, then that it is gone for
  `REPS_ONLY`; **`restDurationPresetClickInvokesOnRestSecondsChanged`
  rewritten too** (the plan had it surviving on the old `90` label — it now
  clicks `1:30` and still asserts `"90"`). New instrumented
  `LocalTrainingPlanRepositoryPlanUsageTest` (2: latest-version-only and
  per-plan counting; an archived plan stops counting and the flow re-emits).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.exercise.*`, `application.trainingplan.*`,
  `presentation.navigation.*`, `presentation.designsystem.*`,
  `LayerBoundaryTest` — 17 classes, 135 tests, 0 failures;
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` —
  green; lint 0 errors, 25 warnings and 1 hint, all pre-existing.
  **Not run (no device):** `ExerciseListScreenTest` (15),
  `ExerciseEditorScreenTest` (13) and `LocalTrainingPlanRepositoryPlanUsageTest`
  (2) compile but need `connectedDebugAndroidTest`, as do CP2–CP9's.

### CP9 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  finish sheet `nFinishSheet` (`RepFlow.dc.html:1736–1763`), the done screen
  `nDone` (`:1364–1405`), the leave sheet `nExitSheet` (`:1437–1447`, for
  `Finish and save it now`), and the script behind them — `nUnfinished` /
  `nextUnfinished` (`:3510–3527`), `nNextExercise` / `nAskFinish` /
  `nConfirmFinish` / the summary and recap values (`:4310–4366`).
- **One finish sheet, one finish request** (plan item 1;
  `WorkoutFinishSheet.kt`): `Finish this workout?`, `<elapsed> elapsed ·
  <the board's progress line>`, the unfinished box (`N still unfinished`, `○
  <name> … N sets left`; an ad-hoc exercise with nothing logged reads `No sets
  yet`, `D55`) from `unfinishedExercises` — the board's own `isFinished` rule —
  then `Finish and save` (56 primary, `D69`), `Keep training`, `Leave it running
  and go Home` and the design's footnote. It is raised by the board's and focus
  mode's `Finish`, the leave sheet's **new** `Finish and save it now` (neutral
  outline, between leave and abandon, as drawn), focus mode's `Next ›` with
  nothing unfinished left (back to the board with the sheet up), and **Home's
  `Finish it`**, re-wired to open `workout?finish=true` with the sheet raised
  (`D16`): dismissing that sheet — scrim, back, `Keep training` — returns Home
  through the same `leaveWorkoutForHome` stack removal. The sheet's confirm is
  the only caller of `CompleteWorkoutSession`. It ships **unconditionally**;
  CP14 item 4 gates it.
- **Completion → done screen without a detour Home.**
  `ActiveWorkoutViewModel.finish` (`WorkoutFinishState`: idle / in flight /
  finished with the session id) is a separate `StateFlow`, so `uiState`'s
  emissions are unchanged (`ActiveWorkoutViewModelTest`'s `:650` still sees
  `NoActiveSession` next). While a finish is in flight the ended session no
  longer triggers the "go Home" hand-back; on success the route opens
  `workout/done/{sessionId}` with `popUpTo(HOME)` (`openWorkoutDone`), so no
  board entry survives behind the done screen. A second confirm while one is in
  flight is ignored.
- **Done screen** (plan items 2–5; `WorkoutDone*.kt`): `<day> · finished`, the
  plan name or `Untitled workout` at 30/500, `Time` / `Sets` (working sets) /
  `Trained` (`N of M`) tiles, one best-set card per record (`D68`), `Versus
  last time` with a recap row per exercise and its delta (`D67`), **`Suggestions
  for next time`** — each exercise's recommendation computed by this completion
  (computed at or after the session ended), its summary and `Why ›` into CP6's
  screen (`D37`; re-read on every `ON_START`) — `Saved to History as <start> →
  <end>.`, and a pinned `Back to Home`. No session note (`D3`, plan item 5).
- **PR detection (plan item 4) is built, not omitted:** new application read
  model `ObserveWorkoutSummary` (`application/history/`), derived in memory from
  `observeCompletedSessions` — valid sessions only, earlier ones only — the same
  cost and the same rules as Home's `ObserveRecentTraining`. Nothing stored.
- **One Phosphor drawable** (`@phosphor-icons/core@2.1.1`, fill, path data
  verbatim): `medal-fill`; `RepFlowIconsTest`'s expected set (now a companion
  property, the method had reached detekt's 60-line limit) 53 → 54.
- **Strings:** `workout_finish_*`, `workout_done_*`, `workout_leave_finish_now`;
  the copy of `D67` (`same load`, `first time`, …) and the `Suggestions for
  next time` label are CP9's — **flagged for the reviewer**.
- **Deviation register:** `D67`–`D69`. Next free register id: **D70**.
- **Tests:** new JVM `ObserveWorkoutSummaryTest` (9: best-set rules per
  tracking type, last time skipping invalidated / later / unrelated sessions,
  incomparable measures, records incl. ties and same-load-more-reps, the flow)
  and `WorkoutDoneTest` (4: recap runs, deltas, this-completion-only
  recommendations and tile counts through the ViewModel, not found);
  `WorkoutBoardModelTest` +1 (the unfinished list). Instrumented:
  `ActiveWorkoutScreenTest` +3 (the board's `Finish` raises the sheet, lists
  unfinished work, and only the confirm completes; `Next ›` with nothing left
  raises it; the leave sheet's `Finish and save it now` raises it);
  `ActiveWorkoutLeaveRouteTest` +2 — **the test plan item 1 owes**: Home's
  `Finish it` opens the sheet without completing, and dismissing it returns
  Home with the session active and no workout entry on the back stack; and
  confirm → `COMPLETED` → done screen with only Home behind it → `Back to Home`.
  That file's harness now registers the workout as `RepFlowNavHost` does
  (`WORKOUT_PATTERN` with the `finish` argument, plus the done route), so its
  two existing back-stack assertions compare against `WORKOUT_PATTERN`.
  `HomeRouteLifecycleTest` and `ProgressionRecommendationRouteTest` each gain
  one argument for the new required callbacks.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.workout.*`, `presentation.designsystem.*`,
  `presentation.navigation.*`, `presentation.home.*`,
  `presentation.progression.*`, `application.history.*`, `LayerBoundaryTest` —
  20 classes, 163 tests, 0 failures (`ActiveWorkoutViewModelTest` 14/14 and
  `ActiveWorkoutScreenWiringTest` 8/8 unchanged and green);
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` —
  green; lint 0 errors, 25 warnings and 1 hint, all pre-existing.
  **Not run (no device):** `ActiveWorkoutScreenTest` (27 methods),
  `ActiveWorkoutLeaveRouteTest` (6), `ProgressionRecommendationRouteTest` (3)
  and `HomeRouteLifecycleTest` (1) compile but need `connectedDebugAndroidTest`, as
  do CP2–CP8's.
- **Room stays at version 7**: no query, table or migration; no domain change;
  no write path added — the finish sheet calls the existing
  `CompleteWorkoutSession`, and the new application class only reads.

### CP8 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  focus screen `nFocus` (`RepFlow.dc.html:1240–1362`), the correction sheet
  `nSetEditOpen` (`:1549–1581`), the script behind them — `nFocusRows` /
  `nRepTarget` / the steppers / `nSugLabel` / the detail pickers /
  `nWarmHint` / `nLastSetHint` (`:4115–4273`), `nLogSet` / `nUndoSet` /
  `nNextExercise` (`:4275–4313`), `nextUnfinished` (`:3520–3527`) — and `6b`'s
  stepper and scale-row spec (`:290–308`).
- **Focus mode replaces the flat exercise card** (`presentation/workout/`;
  `ActiveWorkoutExerciseCard.kt` deleted):
  - `WorkoutFocus.kt`: `Board` (`ph-list-bullets`) / elapsed (re-derived from
    `startedAt`) / `Finish` (accent text; completes directly until CP9);
    `Exercise N of M`, the name at 26/500, `X of Y sets done` (plus `0 of 2
    warm-ups` when the plan has warm-ups; an ad-hoc exercise only counts,
    `D55`); **technique notes** (`Exercise.instructions`) as a collapsed row
    under the header, absent when null (plan item 11); `Last: …` + `Undo
    last`; the **suggestion strip** — the progression recommendation's action
    and top reason, `ph-pulse`, `Why ›` → CP6's screen (plan item 5, `D27`),
    absent without a recommendation; the type note; the pinned bar — `Log set`
    / `Log warm-up` (primary, check glyph) and `Next ›` (next unfinished
    exercise in board order, wrapping; the board with the finish sheet when
    no *other* exercise is unfinished — implementation-review revision 1's
    I1). The
    shared rest strip (tick loop untouched, `D58`) sits above the bar.
  - `WorkoutFocusSets.kt`: logged rows (26dp disc — check on accent / flame
    on hairline — the unchanged one-node summary with `(warm-up)` /
    `(extra)`, the RPE/pain/technique line, and the pencil on the **last**
    set only) and one **pending row per planned working set not logged yet**
    (`Set N: not logged · Target: 8-12 reps · Rest: 60s`, wrapping) — the
    chips' replacement (`D65`); the **correction sheet** (last set only,
    weight/reps/seconds via `EditLastWorkoutSet`, other fields handed back
    unchanged, no `Delete`, `D66`).
  - `WorkoutFocusEntry.kt`: **steppers replace the text fields** — CP3's
    `RepFlowStepper` (value → keypad); weight steps by the exercise's load
    increment (2.5 kg fallback), reps by 1, seconds by 5; captions `kg · 2.5
    steps`, `target 8–12`, `target 30–60 s` / `seconds held`; cards side by
    side from 400dp, stacked below (`D62`). The disclosure (`ph-sliders`,
    summary `RPE 8 · pain 2/5` or "RPE, pain, technique", caret, hairline
    ring, `stateDescription` unchanged) reveals **scale rows**: RPE 0–10 in
    two lines (`D11`, `D64`), pain and technique 0–5 (`D10`); tapping the
    chosen cell clears it. The **warm-up chip** (44 pill, flame, a switch to
    accessibility) replaces the `Switch`; its hint states the real rest
    (`D63`, closes `O6`). Entry state is one saveable `SetEntryState`,
    cleared after every logged set (`D60`).
  - `WorkoutFocusModel.kt` (pure): rows, header counts, `Next ›`'s target,
    the hint's rest, the load step; `setsWithExtraFlag` moved here unchanged.
- **Plumbing:** `ActiveExerciseUi` gains `exerciseId`, `defaultLoadIncrement`
  (kg, from `LoadIncrement`'s grams) and `instructions`, all defaulted — one
  field more than the plan's two, because the strip's `Why ›` needs the
  library exercise id. `ActiveWorkoutViewModel` reads them from the exercise
  catalogue, archived exercises included (`ObserveExercises` ACTIVE ∪
  ARCHIVED); constructor unchanged. The strip's recommendation is the picker
  item's (already refreshed on every `ON_START`).
  `recommendationSummary` is shared by the picker row and the strip.
- **Two Phosphor drawables** (`@phosphor-icons/core@2.1.1`, regular, path
  data verbatim): `list-bullets`, `pulse`; `RepFlowIconsTest` 51 → 53.
- **Strings:** `workout_focus_*` (header, rows, `Last:`, correction sheet,
  captions, stepper descriptions, detail summary and end labels, type notes,
  warm-up hints, `Log set` / `Log warm-up` / `Next`, technique notes); the
  label strings now carry the design's words (`Weight`, `Seconds`, `Effort
  (RPE)`, `Pain`, `Technique`, and the disclosure's "RPE, pain, technique");
  removed the six that lost their last consumer
  (`workout_active_plan_warmup_progress`, `…_plan_working_progress`,
  `…_add_set`, `…_undo_set`, `…_edit_set`, `workout_focus_back_content_description`).
- **Deviation register:** `D60`–`D66`; `O6` closed by `D63`. Next free
  register id: **D67**.
- **Tests:** new JVM `WorkoutFocusModelTest` (7) and
  `ActiveWorkoutFocusPlumbingTest` (2: id / grams→kg / notes, archived
  included). `ActiveWorkoutScreenTest`, against plan CP8's table: rewritten
  `aPlannedExerciseShowsWarmupAndWorkingProgress` (header + pending row),
  `everyPlannedTargetValueStaysOnScreenWhenThePendingRowOutgrowsTheWidth` (the
  parent's clipping guard **retargeted** onto the pending row, same absurd
  values, plus a right-edge bound check), `anAdHocExerciseShowsNoPlannedTargetSummary`
  (positive: the count-only header, no pending rows),
  `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing`
  (drives the scale rows, same 8.0 / 2 contract) and
  `tappingAddSetClearsTheEntryFields` (stepper `+` and keypad, same contract);
  the block comment above the disclosure tests rewritten; the other nine
  unchanged; six new — strip present + `Why` hands out the exercise, strip
  absent, technique notes collapsed → revealed, notes absent, `Next ›` skips a
  finished exercise, the correction sheet keeps the set's other fields.
  `ProgressionRecommendationRouteTest` gains the strip's **inward path**
  (board row → focus → `Why ›` → the recommendation screen, in-memory Room).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.workout.*`, `presentation.designsystem.*`,
  `presentation.progression.*`, `presentation.home.*`,
  `presentation.navigation.*`, `LayerBoundaryTest` — 16 classes, 141 tests,
  0 failures (`ActiveWorkoutViewModelTest` 14/14 and
  `ActiveWorkoutScreenWiringTest` 8/8 unchanged and green);
  `spotlessCheck detekt lintDebug assembleDebug assembleDebugAndroidTest` —
  green; lint 0 errors, 25 warnings and 1 hint, all pre-existing (the one new
  `PluralsCandidate`, `Last: N reps`, was fixed by making it a plural).
  **Not run (no device):** the changed `ActiveWorkoutScreenTest` (24
  methods) and `ProgressionRecommendationRouteTest` (3) compile but need
  `connectedDebugAndroidTest`, as do CP2–CP7's.
- **Room stays at version 7**: no query, table or migration; no domain or
  application write path added. `ROLE_AUDIT.md` still cites the deleted
  `ActiveWorkoutExerciseCard.kt` lines — its consumer re-run is CP16 item 8's.

### CP7 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  board `nBoard` (`RepFlow.dc.html:1161–1238`) with the rest strip, the leave
  sheet `nExitSheet` (`:1437–1447`), the picker sheet `nPickSheet`
  (`:1705–1734`), and the script behind them — `statusOf` /
  `nextUnfinished` (`:3510–3527`), `nTitle` / `nProgress` / `nBoardRows` /
  `nPickRows` (`:3954–3995`), `nCreateEx` (`:4020–4031`), the rest strip and
  leave/discard handlers (`:4296–4331`).
- **The workout surface is now workout mode** (`presentation/workout/`):
  - **Board** (`WorkoutBoard.kt`): `X` (`Leave workout`), the title — the
    plan's name, else `Untitled workout` (no rename, `D30`) — over `ph-timer`
    and the elapsed clock re-derived from `startedAt` every second, and
    `Finish`; the progress line `N of M exercises · S/T sets`; one 64-tall row
    per exercise with the `UP NEXT` hint on the first unfinished one and a
    status chip that always carries a word and a glyph (`T sets` /
    `ph-circle`, `d/T sets` / `ph-dot-outline`, done / `ph-check-fat`); `4a`'s
    empty board (`ph-barbell`, `Empty workout`, the "clock is already
    running" line); `Add exercise` (52, accent outline); the rest strip pinned
    under the list. **No `⋮` and no row sheet** (plan item 7). Tapping a row
    opens that exercise's set entry — out of order is fine; `up next` is a
    hint.
  - **Rules** (`WorkoutBoardModel.kt`, pure): only working sets count;
    planned target = the plan's working-set count; an ad-hoc exercise has no
    target and is finished after one working set (`D55`); `up next` = first
    unfinished row; the picker search matches names case-insensitively.
  - **Set entry, interim until CP8:** the existing `ExerciseCard`, unchanged,
    under a sub-screen bar (exercise name, back arrow `Back to the board`),
    with the rest strip as its bottom bar. Which exercise is open is
    `rememberSaveable` in `ActiveWorkoutRoute`.
  - **Leave sheet** (`WorkoutSheets.kt`): `Leave this workout?`, "Leaving is
    not finishing …", `Leave it running and go Home` (accent outline,
    `ph-house`), `Abandon this workout` (error tone, `ph-trash`), `Keep
    training`. `Finish and save it now` is CP9's. Abandon opens the shared
    `AbandonWorkoutDialog` (moved out of `HomeScreen.kt` into
    `presentation/workout/`, same copy, Home now imports it) → the existing
    `AbandonWorkoutSession`, nothing deleted (`D17`, `D18`).
  - **System back** (the plan's flagged platform change, shipped here):
    `BackHandler` in workout mode — set entry → board; board → leave sheet;
    an open sheet or dialog closes itself. **Today's unconfirmed `Abandon
    workout` button and the bare bottom `Finish workout` button are gone.**
  - **Picker sheet**, converted from the `DropdownMenu`: search field (name
    only, `D8`), `Create a new exercise` → the existing exercise editor
    (`EXERCISE_NEW`; `D56`, closes `O4`), the empty lines "Nothing matches.
    Create it as a new exercise instead." / "No exercises yet. …", and
    60-tall rows (name, tracking type, `+`) that **carry CP6's
    recommendation summary and `Why ›` unchanged** (plan item 6).
  - **Rest strip** restyled in place, tick loop untouched: `m:ss` (24/500,
    accent) / `Rest done` at zero, `Resting` / `Next set is ready`, a 4dp bar
    (`restTimerProgress` kept), a dismiss `X` at ≥ 44 (`Dismiss rest timer`), and
    `−15s` / `+15s` / `Skip rest` (primary) at 44 — all on the existing
    `AdjustRestTimer` / `SkipRestTimer` (`D58`, closes `O5`).
  - **Recovery/futsal day context** kept as a meta line under the progress
    line (`D57`).
- **Leaving and ending:** `NavController.leaveWorkoutForHome()`
  (`presentation/navigation/RepFlowNavigationActions.kt`) navigates `HOME`
  with `popUpTo(HOME)` + `launchSingleTop`, so no board entry stays on the
  stack. It is the leave sheet's action and also the hand-back when the
  session ends (`NoActiveSession` — abandoned, or finished by the board's
  `Finish`, which still completes directly until CP9's finish sheet). The
  workout surface **no longer has a start menu**: Home starts every workout.
  `ActiveWorkoutViewModel.onStartWorkout` / `availablePlans` therefore have
  no screen consumer; they and their tests are kept (the plan's JVM pass),
  for CP16's sweep to decide. The ViewModel gains only the plan name for the
  title (`observeVersionLabels`, a failed read degrading to `Untitled
  workout`); its constructor is unchanged.
- **Five new Phosphor drawables** (`@phosphor-icons/core@2.1.1`, regular,
  path data verbatim): `check-fat`, `dot-outline`, `circle`, `timer`,
  `plus-circle`; five `RepFlowIcons` entries; `RepFlowIconsTest`'s
  enumerated set 46 → 51. `barbell` has a consumer again (the empty board).
- **Strings:** the `workout_board_*`, `workout_leave_*`, `workout_picker_*`,
  `workout_rest_*` and `workout_focus_back_content_description` sets (the
  progress line and the chip texts as plurals); removed the eight that lost
  their last consumer (`workout_active_title`, `…_no_session`, `…_start`,
  `…_start_ad_hoc`, `…_complete`, `…_abandon`, `…_rest_timer_remaining`,
  `…_rest_timer_skip`).
- **Deviation register:** `D55` (ad-hoc target), `D56` (create → editor;
  closes `O4`), `D57` (day-context line kept), `D58` (rest strip: no exercise
  name, no lit fill, no `Rest complete` banner, dismiss `X` at ≥ 44; closes `O5`),
  `D59` (`Finish` at the accent-outline tier). Next free register id:
  **D60**.
- **Tests:** new JVM `WorkoutBoardModelTest` (7: chip states and warm-ups;
  ad-hoc rows; `up next` out of order; nothing up next when all finished;
  progress with planned + ad-hoc; no planned → no total; picker search).
  New instrumented `ActiveWorkoutLeaveRouteTest` (4, in-memory Room, a graph
  registering `HOME` and `WORKOUT` as `RepFlowNavHost` does with the same
  `leaveWorkoutForHome`, entered through Home's `Resume`): **leave → Home
  with the session `ACTIVE`, the resume card shown and no `WORKOUT` entry on
  the back stack**; **system back on the board opens the leave sheet and the
  route stays `WORKOUT`**; **`Abandon this workout` abandons nothing until
  confirmed** (`Keep it` leaves it `ACTIVE`; confirm → `ABANDONED`, back on
  Home); **a confirmed abandon is stored `ABANDONED` with both logged sets
  kept**. `ActiveWorkoutScreenTest`: the helper opens the first exercise's
  set entry by default (the 14 set-entry methods are otherwise untouched and
  stay CP8's), CP6's picker method now opens the picker **sheet** from the
  board; three new board methods — **a row has one action and no `Exercise
  options` trigger or any of the six row-sheet labels** (plan item 7's state
  test), progress/status/`up next` (out of order, warm-ups excluded), the
  empty board. `MainActivityNavHostSmokeTest`'s workout walk now asserts the
  empty board and leaves through `X` → `Abandon this workout` → `Abandon`
  back to Home. `ProgressionRecommendationRouteTest` passes the route's two
  new callbacks only.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `presentation.workout.*` (`WorkoutBoardModelTest`,
  `ActiveWorkoutViewModelTest` 14/14, `ActiveWorkoutScreenWiringTest` 8/8),
  `RepFlowIconsTest`, `presentation.home.*`, `presentation.navigation.*`,
  `presentation.progression.*`, `LayerBoundaryTest` — 75 tests, 0 failures;
  `spotlessCheck detekt lintDebug assembleDebugAndroidTest assembleDebug` —
  green; lint 0 errors, 28 warnings and 1 hint, all pre-existing (the one
  new `PluralsCandidate` on the progress line was fixed by making it a
  plural). **Not run (no device):** `ActiveWorkoutLeaveRouteTest`, the
  changed `ActiveWorkoutScreenTest`, `MainActivityNavHostSmokeTest` and
  `ProgressionRecommendationRouteTest` compile but need
  `connectedDebugAndroidTest`, as do CP2–CP6's.
- **Room stays at version 7**: no query, table or migration added; no
  domain or application write path added.

### CP6 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `6a`
  (`RepFlow.dc.html:43–196`), `6c` (`:198–251`) and the script behind them
  (`prOptions`/`prVals`, `:3462–3505`).
- **New recommendation screen** under `presentation/progression/`
  (`ProgressionRecommendationRoute`, `…Screen`, `…ViewModel`, `…UiState`,
  `ProgressionOutcomeStyle`), route `progression/{exerciseId}`
  (`RepFlowDestinations.PROGRESSION_PATTERN` / `progressionRoute`), no bottom
  nav. It reads the exercise's **latest** recommendation (the one the picker
  row summarises) through the existing `ProgressionRecommendationRepository`
  port, and writes a choice through the existing `RecordManualOverride` — no
  domain or application concept, no policy change, no schema change. Three
  states, as `6a` draws them:
  - **Suggestion:** exercise label, the outcome glyph + title (26/500), `Why`
    with one row per reason the policy recorded (its own words, its order),
    the footnote; pinned bar `Go with the suggestion` (writes nothing — the
    recommendation already stands; `Done` for `Not enough data yet`),
    `Pick another load`, and `Keep the same load` (override → maintain) for an
    increase or a reduction.
  - **`Your call`:** `Increase load` / `Maintain load` / `Reduce load` as
    68-tall selectable rows (`The suggestion` under the suggested one, the
    one in force selected), `Back`; system back closes it. Picking what is
    already in force writes nothing; anything else is a `RecordManualOverride`
    and the screen re-reads.
  - **Recorded choice:** `You overrode this` (or `You went with the
    suggestion` for a change of mind back to it, which is recorded as an
    override equal to the result — an override can be replaced, not
    removed), `Suggested · …`, `Chosen · …`, `Policy v1 · d MMM`, then the
    reasons again; `Change my mind` and `Done`.
  - **All five `ProgressionResult` cases** render as their own state — only
    the glyph, its tint and the action labels change (`6c`'s rule).
    **`RecoveryAdjustment`** additionally shows a `Today's check-in` card
    (score, band, CP4's driver sentence, `Details ›`) opening CP4's
    `ReadinessSheet`, from `ObserveReadiness(today)` — read for that outcome
    only (plan CP6 item 4); no card without today's check-in.
- **Entry point (the one that exists at CP6):** the workout picker row keeps
  its summary and replaces its three inline override `TextButton`s with
  `Why ›` (44 tall, described `Why this suggestion for <exercise>`), routed by
  `ActiveWorkoutRoute(onOpenRecommendation)` → `RepFlowNavHost`.
  `ActiveWorkoutViewModel` loses `RecordManualOverride`/`onOverrideRecommendation`
  and gains `onRefreshRecommendations()`, called from the route's `ON_START`
  so a choice made on the screen shows on return. CP7 carries this row into
  the picker sheet; CP8 wires its focus strip's `Why ›` here and owns that
  inward-path test; CP9 adds the finish-screen entry.
- **Shared mapping:** `ProgressionResultUi` and `ProgressionRecommendationUi`
  moved from `presentation/workout/` to `presentation/progression/`, with one
  `toSummaryUi()`; the picker row's `overridden` marker now means "the
  choice differs from the suggestion" (`isOverridden()`), so a change of mind
  back to the suggestion is not marked. `progression_result_wait_for_more_data`
  now reads `6c`'s `Not enough data yet`.
- **Eight new Phosphor drawables** (`@phosphor-icons/core@2.1.1`, path data
  verbatim): `trend-up`, `trend-down`, `arrow-right`, `arrow-down`,
  `heartbeat`, `hourglass-medium`, `check-circle`, `user-circle`; eight
  `RepFlowIcons` entries; `RepFlowIconsTest`'s enumerated set 38 → 46.
  `info` and `warning-circle` gain the reason-row consumers.
- **Deviation register:** `D49` (no value card, no load figures — closes
  `O3`'s value row), `D50` (no `What it looked at`), `D51` (no applied state,
  no `Earlier suggestions`), **`D52` (copy: footnote, `Your call` line,
  recorded-choice body, `Not enough data yet` — flagged for the reviewer to
  accept or reword)**, `D53` (one screen per exercise, five states;
  `Pick another load` for every outcome), `D54` (the recovery card's
  `Today's check-in` link). `O3` closed. Next free register id: **D55**.
- **Tests:** new `ProgressionRecommendationViewModelTest` (13 — one state
  test per `ProgressionResult` case, `RecoveryAdjustment` with today's
  readiness and without; a pick reaching `RecordManualOverride` and
  re-rendering as the recorded choice, with the picker summary agreeing;
  `Keep the same load`; picking the suggestion writes nothing; change of mind
  back to the suggestion; failed save; not found; exercise name).
  `ActiveWorkoutViewModelTest` drops the removed constructor argument only
  (14/14). Instrumented: new `ProgressionRecommendationScreenTest` (7 — one
  render test per case, the recovery card opening the readiness sheet,
  `Your call` selection and pick, the recorded-choice state); new
  `ProgressionRecommendationRouteTest` (2, in-memory Room — **an override
  reaches `RecordManualOverride`** (stored override, result untouched) **and
  the screen re-renders as overridden**; **the inward path**: a nav graph
  registering `WORKOUT` and `PROGRESSION_PATTERN` as `RepFlowNavHost` does,
  `Add exercise` → the row's `Why ›` → the recommendation screen);
  `ActiveWorkoutScreenTest` gains `thePickerRowsWhyOpensTheRecommendationForItsExercise`
  (summary kept, the three override buttons gone, `Why ›` hands out the
  row's exercise) and its no-op `onOverrideRecommendation` becomes
  `onOpenRecommendation`.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `ProgressionRecommendationViewModelTest`, `RepFlowIconsTest`,
  `ActiveWorkoutViewModelTest`, `ActiveWorkoutScreenWiringTest`,
  `LayerBoundaryTest`, `ComputeProgressionRecommendationTest`,
  `RecordManualOverrideTest` — 50 tests, 0 failures; `spotlessCheck detekt
  lintDebug assembleDebugAndroidTest assembleDebug` — green (the last
  validates the Hilt graph for the new `@HiltViewModel`); lint 0 errors, 28 warnings,
  1 hint, all pre-existing, none in CP6's files. **Not run (no device):**
  `ProgressionRecommendationScreenTest`, `ProgressionRecommendationRouteTest`
  and the changed `ActiveWorkoutScreenTest` compile but need
  `connectedDebugAndroidTest`, as do CP2–CP5's.
- **Not covered by a device-free path:** the real `RepFlowNavHost` lambda and
  the screen's `hiltViewModel()` construction. `MainActivityNavHostSmokeTest`
  walks a fresh install, where no recommendation exists and the picker row
  (and so `Why ›`) is not drawn; the route test reproduces the graph instead.
- **Room stays at version 7**: no query, table or migration added.

### CP5 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `4a`'s
  Home tab (`:726–822`), the start sheet `nStartSheet` (`:1405–1436`), `1d`'s
  light Home with its empty, error and first-run states (`:2976–3030`), and
  the script behind them — `nTodayMeta`/`nDayOptions` (`:3550–3560`),
  `nRecChips`/`nRdy*` (`:3772–3813`), `nResumeMeta`/`nDiscardSession`/
  `nAskFinish` (`:4323–4330`), the board title `nTitle` (`:1165`).
- **Home replaces CP2's `HomePlaceholder`** (deleted) under
  `presentation/home/`: `HomeRoute`, `HomeScreen`, `HomeResumeCard`,
  `HomeStartCard`, `HomeRecoveryCard`, `HomeCards`, `HomeViewModel`,
  `HomeUiState`, `HomeFormatting`. Top to bottom:
  - **Header:** the date as an uppercase label over `Ready when you are`
    (25/500), and the 44dp round `gear-six` button → Settings — absorbing the
    placeholder's `Settings ›` (same accessible name, `Open settings`).
  - **Resume card** (only while a session is active): the accent card,
    `<plan> — still running` (`Untitled workout` for an ad-hoc session, the
    design's default), `m:ss elapsed · N sets logged` re-derived from
    `startedAt` every second (never counted), `Resume` and `Finish it` → the
    workout surface (CP9 re-wires `Finish it` into its finish sheet), and the
    trash → `Abandon this workout?` (`D17`/`D18`'s copy; confirm `Abandon`,
    `Keep it`) → the existing `AbandonWorkoutSession`, nothing deleted. The
    accent card is a dark surface in both themes (the parent's
    `RepFlowCardTone.Accent` decision), so its content renders with the dark
    scheme in light theme too.
  - **Start card** (no active session): `Today`, the plan, `N exercises · N
    working sets`, `Start workout` (56, `play-fill`) and `Train something
    else ›` → the start sheet (`Your plans`: one row per active plan, then
    `Empty workout`), converted from the workout screen's `DropdownMenu`. The
    plan is **the one last trained** (newest valid completed session started
    from a plan, resolved to its plan's *latest* version), else the first
    active plan; with no active plan, `1d`'s first-run card (`Create a plan` →
    the plan editor, `Empty workout`). Dark: `4a`'s gradient `#262a60 →
    #232532` with the `#423a6a` ring; light: `1d`'s surface and hairline.
  - **Recovery card:** `Recovery today` + `Log ›` (always) → the recovery
    entry screen; with today's check-in, the score button (38/500 in the band
    colour, band word, `readiness score`, `Details ›`) → CP4's
    `ReadinessSheet`, now hosted; CP4's bar (`ScoreBar`, now `internal`); the
    driver sentence; and the `Sleep N · Energy N · DOMS N` chips → the entry
    screen. Without one, `1d`'s empty state. A failed read shows the header
    only (claims nothing either way).
  - **Last workout** (full width — `This week` is `D9`): name, `Mon · 61 min`
    (Today / Yesterday / weekday within a week / `d MMM`), and `N load
    increases` in the accent, or `No load increases`; `1d`'s `Couldn't load
    your history` + `Retry` when the history read fails; nothing before the
    first workout.
- **"Today" (plan CP4 item 2), as specified:** `HomeViewModel` holds
  `MutableStateFlow<LocalDate>` from the injected `Clock`; readiness follows
  it through `flatMapLatest`; a midnight wait (inside the collected flow, so
  only while Home's state is collected) and `onForeground()` re-read the
  clock; `HomeRoute` calls `onForeground()` from
  `LifecycleEventEffect(ON_START)`. The header date is the date the readiness
  was read for.
- **Start path:** Home calls `StartWorkoutSession` / `StartWorkoutSessionFromPlan`
  itself (same use cases, same "stale version → `NotFound`, never an empty
  fallback" rule as the workout screen) and opens the workout on success.
  `ActiveWorkoutViewModel` is untouched — its own start menu stays until CP7 —
  so `ActiveWorkoutViewModelTest`'s start methods survive unchanged (14/14
  green).
- **Application read model (no schema change):** new
  `application/history/ObserveRecentTraining` over
  `WorkoutRepository.observeCompletedSessions(includeInvalidated = false)`:
  the last valid workout, its **load increases** (per exercise, top working
  load — heaviest non-warm-up set with a load — above the same exercise's
  most recent earlier valid session that has one; unloaded exercises and
  first times never count), and the plan version last trained. Invalidated
  sessions are excluded throughout, as from progression.
- **Eight new Phosphor drawables** (`@phosphor-icons/core@2.1.1`, path data
  verbatim): `gear-six`, `calendar-check`, `play` (fill), `record` (fill),
  `trash`, `lightning`, `list-plus`, `warning-circle`; eight `RepFlowIcons`
  entries; `RepFlowIconsTest`'s enumerated set 30 → 38. `barbell` has no
  consumer now (its KDoc says so; CP16's sweep decides).
- **Strings:** the six `home_placeholder_*` strings replaced by `home_*` (three
  plurals) and the shared `workout_abandon_confirm_*` / `workout_abandon_keep_action`
  CP7 will reuse. `home_settings_content_description` and
  `home_recovery_log_content_description` keep the placeholder's values.
- **Deviation register:** `D44` (no `~N min`; closes `O1`), `D45` (start sheet
  rows are plans, no `A different plan ›`; closes `O2`), `D46` (no start card
  while a session runs), **`D47` (recovery-card copy: `Details ›`, the empty
  line names the inputs the policy actually reads, `Log recovery` — flagged
  for the reviewer to accept or reword)**, `D48` (resume-card buttons at the
  design-system tiers, `Log ›` at 44). Next free register id: **D49**.
- **Tests:** new `ObserveRecentTrainingTest` (7); new `HomeViewModelTest` (9 —
  CP4's Home half: saving today's check-in through `RecordRecoveryEntry`
  replaces the log prompt on the same subscription; yesterday's entry is not
  today's; **the midnight case** on a virtual-time `Clock` from 23:59; **the
  sleep case** — wall clock jumped to 07:00 next day, `expectNoEvents()`, then
  `onForeground()`; first-run start + `onWorkoutOpened`; last-trained plan
  over the first plan, and starting it; first-plan fallback; a stale plan
  version → `PLAN_NOT_FOUND`, nothing started; abandon → `ABANDONED`, kept);
  new `HomeFormattingTest` (4: elapsed, rounded minutes, workout day, the
  start card's text ≥ 4.5:1 on its own ground in both themes). Instrumented:
  new `HomeRouteLifecycleTest` (plan CP5 item 4's route half: in-memory Room,
  settable clock at 10:00, score shown; clock to 07:00 next day,
  `CREATED` → `RESUMED`, `Nothing logged today` shown) and `HomeScreenTest`
  (6: abandon only after confirmation, `Keep it` cancels; `Resume`/`Finish it`
  and no start card while running; start card, sheet rows and `Empty
  workout`; first-run card; score → readiness sheet; empty state → log).
  `MainActivityNavHostSmokeTest` re-pointed at Home's real affordances (the
  greeting; `Empty workout` → workout, then abandoned again so the walk leaves
  a fresh install's state; the recovery `Log` and the gear by their unchanged
  descriptions); the two backup route tests' gear string id renamed only.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `HomeViewModelTest`, `HomeFormattingTest`, `ObserveRecentTrainingTest`,
  `RepFlowIconsTest`, `LayerBoundaryTest`, `ReadinessBandStyleTest`,
  `ObserveReadinessTest`, `ActiveWorkoutViewModelTest`,
  `ObserveWorkoutHistoryTest`, `RepFlowBottomNavigationBarTest`,
  `RepFlowPrimitivesTest` — 90 tests, 0 failures; `spotlessCheck detekt
  lintDebug assembleDebugAndroidTest` — green; lint 0 errors, 28 warnings and
  1 hint, all pre-existing, none in CP5's files (the two `NonObservableLocale`
  errors lint raised on the first pass were fixed by reading the locale from
  `LocalConfiguration`). **Not run (no device):** `HomeRouteLifecycleTest`,
  `HomeScreenTest`, the re-pointed `MainActivityNavHostSmokeTest` and the two
  backup route tests compile but need `connectedDebugAndroidTest`, as do
  CP2's, CP3's and CP4's.
- **Room stays at version 7**: no query, table or migration added in CP5.

### CP4 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): the
  engine (`RDY`, `RDY_BANDS`, `readiness()`, `:3142–3166`), `SCALES`
  (`:3126–3133`), `nRdyFactors` / `nRdyDrivers` (`:3797–3813`) and the sheet
  markup `nRdySheet` (`:1489–1547`).
- **Domain:** new pure-Kotlin `domain/recovery/ReadinessScore.kt` —
  `ReadinessFactor` (the six scales in `RDY` order, weights in integer tenths
  10/12/10/8/7/15, the four inverted ones), `ReadinessBand`
  (Ready/Hold/Back off/Protect), `ReadinessFactorReading` (value, normalized,
  flagged at `n ≤ 2`) and `ReadinessScore.of(entry)`: score
  `floor((200·S + 310) / 620)` = `round(10·S/31)` in integers, pain gate
  (pain while walking ≥ 3 or heel stiffness ≥ 4 → Protect) applied before the
  bands 75/58/42, drivers = first three flagged, and the prototype's driver
  sentence with the label lowercased whole. Futsal flags are not read.
- **Read path only (no schema change):** `RecoveryEntryDao.observeForDate`
  (Room `Flow`, same `entry_date` predicate as `findForDate`),
  `RecoveryRepository.observeForDate`, `LocalRecoveryRepository`'s mapping.
  New `application/recovery/ObserveReadiness(date)` maps the entry through
  `ReadinessScore.of` (or `null`) with `distinctUntilChanged`, since Room
  re-emits on any write to the table. It never reads the clock — choosing
  "today" (and re-deriving it at midnight / on foreground) is CP5's Home
  ViewModel, per plan CP4 item 2.
- **Sheet (unhosted until CP5):** `presentation/home/ReadinessSheet.kt` —
  `ReadinessSheet` wraps `ReadinessDetail` in `RepFlowSheet`: title, one
  line, score 44/500 tabular in the band colour + band word + "out of 100",
  4dp bar, driver sentence, `The inputs and what they weigh` with one row per
  factor (label, `v/5 · pulling the score down|fine`, five 7dp dots filled to
  `n`, weight `×1.2`), the gate note, `Close` (48) on a
  `RepFlowBottomActionBar`. Not drawn: the advice line (D19), the decision
  list and override (D29). `ReadinessBandStyle.kt`: band word (string
  resources) and colour per theme.
- **Deviation register:** D41 (light-theme band colours, darkened to clear
  4.5:1 — the design has no light render), **D42 (sheet copy: title, intro
  line and gate note rewritten so they do not claim the score adjusts
  proposals; flagged for the reviewer to accept or reword)**, D43 (each factor
  row carries `v/5` and the flagged/fine word, plan item 4 / `6b`'s never
  colour alone). Next free register id: **D44**.
- **Tests:** new `ReadinessScoreTest` (19: weights and inversion, field
  mapping, 100/0, seed 75.16 → 75 Ready and heavy legs 3 72.58 → 73 Hold,
  75/74, 58/57, 42/41, pain gate 2 vs 3 and heel 3 vs 4 against an
  otherwise-Ready input, flagging at `n` 2 vs 3 incl. an inverted scale, the
  pinned literal "Driven by leg doms 3/5, heavy legs 4/5.", the three-driver
  cap and order, the all-clear sentence, futsal flags inert); new
  `ObserveReadinessTest` (4: none, yesterday-only → none, upsert emits on the
  same subscription and re-scores, another date's write does not re-emit);
  new `ReadinessBandStyleTest` (4: distinct words, the design's dark values,
  distinct colours, ≥ 4.5:1 on `surface`/`background` both themes).
  `InMemoryRecoveryRepository` now backs its map with a `MutableStateFlow`
  and gains `observeForDate` (re-emitting on every write, like Room) — no
  assertion changes elsewhere. Instrumented: one new `RecoveryDaoTest`
  method (`observeForDateReEmitsAfterAnUpsertForThatDate`, Turbine) and new
  `ReadinessDetailTest` (2: content, Close).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `ReadinessScoreTest`, `ObserveReadinessTest`, `ReadinessBandStyleTest`,
  `LayerBoundaryTest`, every `application.recovery` and
  `presentation.recovery` test, and every other consumer of the fake
  (`BackupViewModelTest`, `ExportBackupTest`,
  `ComputeProgressionRecommendationTest`,
  `AddWorkoutExerciseAndRecordWorkoutSetTest`, `ActiveWorkoutViewModelTest`)
  — 87 tests, 0 failures; `spotlessCheck detekt assembleDebugAndroidTest
  lintDebug` — green, no lint finding in CP4's files. **Not run (no
  device):** `RecoveryDaoTest` (5) and `ReadinessDetailTest` (2) compile but
  need `connectedDebugAndroidTest`, as do CP2's three and CP3's one.
- **Room stays at version 7**: a new `@Query` only; no table, column or
  migration.

### CP3 — what was done and verified (2026-10-01)

- **Design re-read** from the live project (etag `1786483024007421`): `6b`
  (`:253–333`), the sub-screen top bar (`:47–50`), the pinned bars
  (`:179–185`, `:1155–1157`), the top-level title (`:349–350`), History's row
  and stat tiles (`:958–983`), the start sheet (`:1407–1416`), `3c`'s scale
  rows (`:2034–2043`), the keypad (`:2826–2843`) and its key logic
  (`:4600–4630`).
- **Seven new primitives** under `presentation/designsystem/components/`:
  `RepFlowScreenScaffold` (top-level 25/500 title vs. sub-screen 44dp back +
  17/500 title, chosen by `onBack`; content padding already includes the 16
  sides; a `bottomBar` slot; no window insets of its own, because
  `RepFlowNavHost`'s outer `Scaffold` already pads every destination),
  `RepFlowBottomActionBar` (`surfaceContainer` fill = the design's `#1b1d2b`,
  12% top edge, padding 12/16/16; a slot form and a primary + optional
  secondary form), `RepFlowSheet` (`ModalBottomSheet`, fully expanded, top
  radius 16, 32×4 grabber, the design's scrim, optional section-label
  title), `RepFlowStatTile`/`RepFlowStatRow` (+ `RepFlowStat`),
  `RepFlowSectionLabel` (uppercased in the primitive, heading semantics),
  `RepFlowNumericKeypad` (`1–9 . 0 ⌫`, keys 56 on `control`, Cancel : Set at
  1 : 2; input rules in the pure `RepFlowKeypadInput`, exactly the
  prototype's: six characters, one `.`, none for whole numbers, Set on an
  empty entry dismisses), `RepFlowListRow` (56/68 min heights, 15/500 title,
  tabular meta, default trailing `caret-right` at 30% on clickable rows, 9%
  divider, whole row the tap target).
- **Three reworked:** `RepFlowStepper` — `6b`'s 6dp gap; the value becomes a
  ringed button when `onValueClick` is given; a new `BigDecimal` overload owns
  the step size, range and the keypad (arithmetic in the pure
  `RepFlowStepperMath`, exact decimal, clamped). `RepFlowPillPicker` — sizing
  gains `cellGap`/`labelFontSize`; the scale sizing is now `6b`'s gap 5 and
  13.5 tabular digits; new `RepFlowScaleRow` puts the required `low → high`
  end labels over it (the Exercise list's filter row keeps the pill sizing
  and its 8dp gap). `RepFlowStatusChip` — colour resolution extracted to
  `repFlowStatusChipColors`; `UpNext` now gets `6b`'s accent pill, settling
  the parent's open modelling note.
- **`RepFlowSheet` role decision:** `surfaceContainerLow` (the
  `ModalBottomSheet` default) stays unassigned and is **not reached** — the
  sheet passes `surface` explicitly (= the design's `#232532`). Recorded in
  `ROLE_AUDIT.md`'s "Deliberately unassigned" with baseline ratios, and a new
  CP3 section tabulates every new text/ground pair.
- **Text tiers:** new `RepFlowColor.secondaryTextAlphaDark` (.55, the
  design's) / `secondaryTextAlphaLight` (.70) and `repFlowSecondaryTextColor`.
  The design's 45% tertiary step measures 3.72–3.91:1 in dark, so labels use
  the secondary tier — **deviation D39**. Keypad Cancel/Set use the 56dp
  button tiers rather than `1a`'s 52 — **D40**. Next free register id: D41.
  Status-chip audit: no primitive fails (the word and both end labels are
  required parameters); recorded under the register.
- **One new drawable**, `ic_ph_caret_right` (path verbatim from
  `@phosphor-icons/core@2.1.1`), and `RepFlowIcons.caretRight`;
  `RepFlowIconsTest`'s enumerated set is now 30.
- **Strings:** `repflow_back_content_description`, `repflow_keypad_cancel`,
  `repflow_keypad_confirm`, `repflow_keypad_backspace_content_description`,
  `repflow_stepper_type_value`, `repflow_scale_end_labels`.
- **Tests:** `RepFlowPrimitivesTest` 16 → 31 — contrast region +7
  (secondary text on page/card/bar in both themes; the design's 45% shown to
  miss; stat figure; keypad digits on `control`; sheet on `surface`, never
  `surfaceContainerLow`; bar secondary label on the bar fill; `up next`
  chip), dimensions region +8 and the tap-target list +5 entries; the two
  methods the inventory flagged (`theStepperCarriesBothOfTheDesignsSizes`,
  `thePillPickerCarriesBothOfTheDesignsCellShapes`) survive unchanged. New
  `RepFlowKeypadInputTest` (7) and `RepFlowStepperMathTest` (5). New
  instrumented `RepFlowStructuralPrimitivesTest` (5: step buttons, value →
  keypad → Set, Cancel, scale end labels, list-row tap).
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests` for
  `RepFlowPrimitivesTest`, `RepFlowKeypadInputTest`, `RepFlowStepperMathTest`,
  `RepFlowIconsTest`, `RepFlowThemeTest`, `RepFlowBottomNavigationBarTest`,
  `LayerBoundaryTest` — 73 tests, 0 failures; `spotlessCheck detekt
  assembleDebugAndroidTest lintDebug` — green, lint findings unchanged (none
  in CP3's files). **Not run (no device):** `RepFlowStructuralPrimitivesTest`
  compiles but needs a `connectedDebugAndroidTest` run, as do CP2's three.
- **No consumer yet, by design:** CP5–CP15 consume these; no existing screen
  was converted here. Four files whose only top-level class is the
  composable's `…Defaults` object carry a commented
  `@file:Suppress("MatchingDeclarationName")` (detekt), named after the
  composable per the Compose convention.

### CP2 — what was done and verified (2026-10-01)

- **Four tabs.** `TOP_LEVEL_DESTINATIONS` is now Home, Plans, History,
  Progress (re-read from the live design: `4a`'s `nTabs`, `RepFlow.dc.html`
  `:3590–3603`, and the bar markup `:1114–1123`). `TopLevelDestination`
  gained `selectedIcon`; the bar draws the fill glyph when selected and the
  regular one otherwise, on top of the unchanged colour treatment.
- **Six new Phosphor drawables** (`house`, `house-fill`, `chart-line-up`,
  `chart-line-up-fill`, `list-checks-fill`, `clock-counter-clockwise-fill`),
  path data verbatim from `@phosphor-icons/core@2.1.1` (the version the
  design loads via `@phosphor-icons/web@2.1.1`; the two existing regular
  glyphs were checked byte-identical against the same release). Six matching
  top-level `RepFlowIcons` entries; `RepFlowIcons.Nav` is now four
  regular/fill pairs, and its KDoc is rewritten. `books`, `barbell`,
  `moon-stars` and `cloud-arrow-up` stay top-level entries, each documented
  with its CP2 consumer.
- **New routes** `HOME` (start destination, replacing `EXERCISES`),
  `PROGRESS`, `SETTINGS`, rendered by `HomePlaceholder`
  (`presentation/home/`, replaced by CP5), `SettingsPlaceholder`
  (`presentation/settings/`, replaced by CP14) and `ProgressPlaceholder`
  (`presentation/progress/`, replaced by CP15), each carrying a one-line
  "Placeholder - replaced by … CP<n>" comment for CP16's grep. Home carries
  exactly the three contracted affordances: `Start or resume a workout`
  (`barbell`) → `WORKOUT`; the recovery `Log ›` row (`moon-stars`) →
  `RECOVERY`; `Settings ›` → `SETTINGS`. Settings carries `Library`
  (`books`) → `EXERCISES` and `Backup and restore` (`cloud-arrow-up`) →
  `BACKUP`, plus a back arrow. Progress shows the title and `1d`'s
  empty-state treatment.
- **Nav suppression** needed no code: the workout and settings routes are
  not in `TOP_LEVEL_ROUTES`, so the bar is absent there. Back-gesture
  interception stays CP7's (plan CP2 item 6).
- **Strings:** added `nav_*`, `home_placeholder_*`, `settings_*`,
  `progress_placeholder_empty`; removed the four retired tabs' now-unused
  content descriptions (`exercise_list_content_description`,
  `exercise_list_workout_content_description`,
  `exercise_list_recovery_content_description`,
  `exercise_list_backup_content_description`).
- **Tests (plan CP2 item 7):** `RepFlowIconsTest` — the 23-name literal is
  now 29 with the six additions named; `everyTopLevelDestinationGetsItsOwnGlyph`
  rewritten as `…GlyphPair` (pairs distinct, no glyph shared);
  `designConfirmedNavGlyphsAreTheOnesTheDesignNames` asserts all eight.
  `RepFlowBottomNavigationBarTest` — the glyph test asserts the
  `icon`/`selectedIcon` pairs, and the six-route pin (and its stale KDoc) is
  replaced by `topLevelDestinationsAreTheDesignsFourTabsInOrder`; the three
  colour tests untouched. `MainActivityNavHostSmokeTest` rewritten (9
  methods): Home start + four tabs; Workout from Home with the nav absent
  (asserted as a pair); Recovery and recovery history from Home; Settings
  from Home with the nav absent; Exercises and Backup from Settings, walked
  from Home; History and Progress tabs. The two backup route tests are
  re-routed (Home → `Settings ›` → `Backup and restore`); their subjects
  and second-step string ids are unchanged.
- **Checks run:** `spotlessApply`; `testDebugUnitTest --tests
  RepFlowIconsTest --tests RepFlowBottomNavigationBarTest --tests
  *LayerBoundaryTest` — 16 tests, 0 failures; `spotlessCheck detekt
  assembleDebugAndroidTest` — green; `lintDebug` — 0 errors, no new
  warnings. **Not run (no device):** `MainActivityNavHostSmokeTest`,
  `BackupRouteSafCancellationTest`, `BackupRouteUnreadableRestoreFileTest`
  compile but still need a `connectedDebugAndroidTest` run.
- **Known interim limitations, deliberately not fixed at CP2:** the Home
  placeholder's Settings affordance is a `Settings ›` text button, not the
  design's `ph-gear-six` glyph (the plan budgets exactly six new drawables;
  CP5's header gear replaces it). The Exercises, Recovery, Backup and
  Workout screens, now pushed rather than tabs, still have no top-bar back
  arrow — system back returns to where they were opened from; their
  conversions (CP3's `RepFlowScreenScaffold`, CP7, CP10, CP13, CP14) add it.

### CP1 — what was done and verified (2026-10-01)

- Read the live Claude Design project in full — every artboard's markup and
  the prototype's component script (`RepFlow.dc.html`, 4 723 lines, etag
  `1786483024007421`) — and wrote
  `docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`:
  per screen, the defining artboards, regions with the design's own values
  (cited to `6b` or to the artboard's line), every interactive element, the
  Compose owner after conversion, and the existing tests that read what the
  conversion changes, each found by grep and marked survives / rewritten.
- **Deviation register:** D1–D24 seeded verbatim from the plan; **D25–D38
  added** from the full read (Settings `Library` row, recovery check-in as a
  screen rather than `4a`'s sheet, the focus strip carrying the progression
  recommendation rather than readiness, readiness chips / decision list /
  override not rendered, ad-hoc naming, set-edit scope, custom-load override,
  override-streak line, backup history / safety snapshot / undo, `2c`'s FAB
  and 40dp overflow, the done-screen recommendations section, and `1b`/`1c`
  not built). Next free id: D39.
- **Open items O1–O12** — design elements that pass the domain-backing test
  but that the plan text does not decide — each assigned to its owning
  checkpoint to build or register. **Two need a user decision before their
  checkpoint starts:** `O11` (CP14 — `5c`, the newer turn, draws `Theme`,
  `Default rest`, `Extra set fields` and `Archived exercises and plans`,
  which the plan's `4a`-based CP14 omits; any of them would be a new column
  in `MIGRATION_7_8`) and `O12` (CP15 — `5b`, the newer turn, draws a
  different Progress composition from the `4a` one the plan describes).
  `D38` (`1c` exercise detail, domain-backed, not built) is flagged for the
  reviewer to accept or reject.
- **Test-column findings beyond the plan's enumerations:** the two backup
  route tests' *second* step reads four string ids on the Backup screen CP14
  converts (two behind `assertDoesNotExist`), so CP14 must keep those ids or
  rewrite the methods; `RepFlowPrimitivesTest`'s stepper and pill-picker
  size tests read the primitives CP3 reworks; the three `performTextInput`
  calls outside the workout all type into fields that stay text fields.
- No production code changed (CP1 item 4). No Gradle check applies to a
  documentation-only checkpoint; none was run.

### Next action

*(Superseded: technical approval is recorded and the item is at `AWAITING_FUNCTIONAL_REVIEW`; see the "Functional review checklist" section below. Historical text follows.)* External implementation review of the implementation-revision-1 bundle
(`.ai-review/repflow-redesign-visual-foundation-remediation-1/current/`): a
hard gate. The self-review, the full gate and the bundle are done; the
reviewer's feedback goes to
`.ai-review/repflow-redesign-visual-foundation-remediation-1/feedback/REVIEW_FEEDBACK.md`,
then `/apply-implementation-review repflow-redesign-visual-foundation-remediation-1`
(or `/approve-review` on a clean `APPROVE`). `O11`, `O12` and `D60`'s carry-over were
decided by the user on 2026-10-01: they go to a later remediation child
(`IMPROVEMENT_ROADMAP.md` §9.8).

## `repflow-redesign-visual-foundation-remediation-1` — Functional review checklist, ROUND 4 (implementation revision 8)

Round 4 of the functional review. Round 3 returned FAIL (outcome above, under
"Functional review round 3 — outcome"); R3-F-2 to R3-F-5 were fixed in this
item, R3-F-1 was a wording change (the user accepted the delay), and technical
re-approval of **revision 8** is recorded (commit `8561a43`, basis
`EXTERNAL_APPROVE`). This round re-tests **only what round 3 changed** plus a
short regression pass. Rounds 1 to 3 stand for everything else and are not
repeated. Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md` (the round-3
file was consumed; write a fresh one headed "round 4"). Every command names the
child id explicitly (`active_work_item_id` still points at the grandparent).

**Tags.** **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`),
sample data from round 1 "Test data". **[PHONE]** the physical SM-S928B
(`RFCXA0RLSVT`); a tester will use it this round with the user's permission and
after a data backup. **[BOTH]** either; AVD first. Steps that write data say
**WRITES** in bold. Phone steps never `Save` in the editors on the user's real
exercises or plans: every Save-based step is on the AVD.

### R4 setup and automated state

- Build and install on the AVD: `ANDROID_SERIAL=emulator-5554 ./gradlew
  installDebug`; restore the sample backup on the AVD first (the editors need
  at least one existing exercise and two existing plans, one with enough rows
  to scroll). On the phone install the same debug build only after the backup.
  Room 8, no flags, no network. R4-I1 needs notifications and
  `Alarms & reminders` allowed.
- **Automated verification is current and not re-run here.** The working tree
  is clean and `git diff faec518 HEAD -- app` is empty (faec518 is the last app
  change; everything since is docs and `WORKFLOW_STATE.json`). Last full gate
  (round-3 fixes, AVD): `spotlessCheck detekt lintDebug` clean, JVM unit tests
  644, 0 failures, instrumented 312, 0 failures.

### R4-A. R3-F-4: the name keeps focus while its error toggles

Do this in the exercise editor (`Add exercise`) and the plan editor
(`New plan`). Software keyboard on.

- **R4-F4a [AVD]** Exercise editor. Tap the name, type `Abc`, then delete one
  character at a time to empty. Expect: the field keeps focus and the keyboard
  stays up the whole time, and the `required` error appears when it is empty.
- **R4-F4b [AVD]** Exercise editor, immediately after F4a (still empty and
  focused) type `P`. Expect: `P` appears (no keystroke lost), focus and keyboard
  stay, the error clears. Keep typing a few characters: none are lost.
- **R4-F4c [AVD]** Exercise editor. Type the exact name of an existing exercise.
  Expect: the duplicate-name error shows (after `Save`, or live where it
  already did) with focus and keyboard kept. Add and delete one character so the
  duplicate error toggles on and off several times: focus and keyboard stay,
  no keystroke is lost. Toggle between the `required` and the duplicate error
  by emptying and retyping the duplicate name.
- **R4-F4d [AVD]** Plan editor: repeat F4a, F4b and F4c (an existing plan's
  name as the duplicate).
- **R4-F4e [PHONE]** Look-only, no Save, nothing written. Exercise editor and
  plan editor: delete the name to empty and type again; keyboard and focus stay,
  no keystroke lost. Leave with Back and discard.

### R4-B. R3-F-2: touch and Done take focus off the name

In each editor focus the name field first, then perform the action.

- **R4-F2a [AVD]** Exercise editor, name focused and **empty**: tap blank space
  (a label or the gap between sections). Expect: focus leaves (keyboard hides)
  and the `required` error shows.
- **R4-F2b [AVD]** Same, tapping a chip (muscle group, equipment or similar).
  Expect: the chip toggles (its action happens) and focus leaves the name.
- **R4-F2c [AVD]** Same, tapping a stepper (a numeric stepper in the form, where
  the exercise editor has one). Expect: the value changes and focus leaves.
  If the exercise editor has no stepper, record "n/a" and do it in the plan
  editor (F2f).
- **R4-F2d [AVD]** Name focused, press the keyboard's **Done**. Expect: focus
  leaves, keyboard hides, an empty name shows `required`.
- **R4-F2e [AVD]** Name focused: tap another text field (`Other`, notes). Expect:
  focus moves to that field, keyboard stays up; scroll the form by dragging:
  scrolling works and does not by itself clear focus; move between text fields
  with the keyboard's Next/Done action where one is offered.
- **R4-F2f [AVD]** Plan editor, `New plan`, name focused and **empty**: tap blank
  space. Expect: focus leaves and `required` shows on the empty `New plan`.
  Repeat tapping a chip, a stepper (sets or reps on a row) and `Add exercise`
  (the picker opens or a row is added: the tap's action happens), then Done.
- **R4-F2g [PHONE]** Look-only, no Save: in both editors with an empty name
  focused, tap blank space, a chip or stepper and press Done. Focus leaves,
  `required` shows, the tapped control worked. Leave with Back and discard.

### R4-C. R3-F-3: every refused duplicate Save scrolls to the name

- **R4-F3a [AVD]** Plan editor with a plan that has enough rows to scroll. Type
  a name that duplicates an existing plan. Scroll to the bottom, `Save`. Expect:
  refused, the screen scrolls to the name, duplicate error under the name.
  Scroll away to the bottom again, `Save` again. Repeat at least 3 times in
  total, each time scrolling away first. Expect: it scrolls to the name every
  time, not only the first.
- **R4-F3b [AVD]** Exercise editor, large font or a short view so the name can
  be scrolled out of view, duplicate name: repeat the refusal at least 3 times,
  scrolling away in between. Expect: it scrolls to the name every time.

### R4-D. R3-F-5: editing the name clears the duplicate error

- **R4-F5a [AVD]** Plan editor: after a refused duplicate Save, type one more
  character (or change the name to a free one). Expect: the duplicate error
  clears as soon as the name is edited. `Save` again with a free name: it
  saves (**WRITES** a plan on the AVD). `Save` with the duplicate name restored
  is refused again.
- **R4-F5b [AVD]** Exercise editor: same (**WRITES** one exercise on the AVD).
  A different error (for example a required field) is not cleared by editing
  the name.

### R4-E. R3-F-1: a `-15s` that ends the rest gives one alert

- **R4-F1 [BOTH]** **WRITES** a test workout (phone: finish or `Abandon` it
  afterwards). Log a set so a rest starts, wait until under 15 s remain, tap
  `-15s`. Expect: the rest ends and **exactly one** alert arrives within about
  5 seconds (Android's alarm minimum; not at the old end time). No second
  alert afterwards. That delay is accepted; this step only confirms it is still
  a single alert.

### R4-F. Regression pass (short)

- **R4-R1 [AVD]** Rest timer: `Skip rest` clears the strip with no alert;
  `+15s` extends the rest; a new set restarts it; `Finish` ends the workout with
  no alert afterwards; leaving and re-entering the workout (`Leave`, then
  `Resume`) after the rest ended gives no second alert. **WRITES** a workout.
- **R4-R2 [AVD]** Plan editor: `Save` of a valid **new** plan and of an
  **existing** plan (new version); Back with edits asks `Discard changes?` and
  discarding leaves the plan unchanged; Back unedited does not ask. No name
  error on a fresh open. **WRITES** on the AVD.
- **R4-R3 [AVD]** Exercise editor: `Save` of a valid **new** exercise and of an
  **existing** exercise; Back with edits asks to discard and discarding keeps
  the saved values. No name error on a fresh open. **WRITES** on the AVD.

### R4 for the phone tester

Back up the phone's data first. Do **not** `Save` in the editors on the phone.

1. **[PHONE] R4-F4e. Nothing written.** Both editors: empty the name, type
   again; keyboard and focus stay, no key lost. Back, discard.
2. **[PHONE] R4-F2g. Nothing written.** Both editors, empty name focused: tap
   blank space, a chip or stepper, press Done. Focus leaves, `required` shows,
   the tapped control worked. Back, discard.
3. **[BOTH] R4-F1. WRITES a test workout.** Log a set, wait until under 15 s of
   rest remain, tap `-15s`: exactly one alert within about 5 seconds, no second.
   Finish or `Abandon` the workout afterwards.

### R4 known deferred (not findings)

- **Group B (B1-B6) stays in the grandchild
  `repflow-redesign-visual-foundation-remediation-1-remediation-1`**
  (phase `PLANNING`): Settings 5c/5d, Progress 5b, set entry keeping its values
  (D60), the three empty-state / search-field / ordering defects, and the
  Settings entry point for the exact-alarm prompt. Do not report them here.
- R3-F-1's delay (the `-15s` alert within about 5 seconds, not instant) is
  ACCEPTED by the user.
- Review observations left as-is: O3, O4, O5, and O-3 (see round 3's list).
- Everything rounds 1 to 3 listed as deferred or accepted stays so.

### R4 expected result and what happens next

Every step behaves as stated, nothing crashes, no earlier area regressed. If
clean: `/accept-milestone repflow-redesign-visual-foundation-remediation-1` is
the only acceptance command; it needs every checkpoint in this item's registry
`COMPLETE` (all sixteen are) and, per the workflow, the group-B child must also
reach completion before this item can reach `MILESTONE_COMPLETE`. A checkpoint
still outstanding goes to `/milestone-implement`; no command records acceptance
of a partial round. Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md`,
then `/apply-functional-review repflow-redesign-visual-foundation-remediation-1`
(bounded branch for a same-scope fix, broad branch for a `...-remediation-<n>`
child).

---

## `repflow-redesign-visual-foundation-remediation-1` — Functional review checklist, round 3 (implementation revision 7; superseded by round 4 above for the changed areas)

Round 3 of the functional review. Round 2 returned FAIL (six findings, outcome
below under round 2); all six were fixed in this item, the implementation
review of revision 6 found one Important (I-1, a `-15s` that ends the rest),
that was fixed too, and technical approval of **revision 7** is recorded
(commit `9e531a7`, basis `EXTERNAL_APPROVE`). This round re-tests **only what
round 2 and the I-1 review changed** (P-1, I-1, R2-F-1 to R2-F-5) plus a short
regression pass on the rest timer and both editors. Everything else: rounds 1
and 2 stand and are not repeated. Findings go to
`.ai-review/feedback/FUNCTIONAL_REVIEW.md` (the round-2 file was consumed;
write a fresh one headed "round 3"). Every command names the child id
explicitly (`active_work_item_id` still points at the grandparent).

**Tags.** **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`),
sample data from round 1 "Test data". **[PHONE]** the physical SM-S928B
(`RFCXA0RLSVT`), a tester will use it this round with the user's permission and
after a data backup. **[BOTH]** either; AVD first. Steps that write data say
**WRITES** in bold; on the phone they write a test workout (finish or `Abandon`
it afterwards).

### R3 setup and automated state

- Build and install on the AVD: `ANDROID_SERIAL=emulator-5554 ./gradlew
  installDebug`; on the phone, install the same debug build only after the
  backup. Restore the sample backup on the AVD first. Room 8, no flags, no
  network. Notifications and `Alarms & reminders` must be allowed for the alert
  steps (R3-P1, R3-I1); R3-F5 needs notifications denied.
- **Automated verification is current and not re-run here.** The working tree
  is clean and `git diff 8c4698f HEAD -- app` is empty (the last app change;
  everything since is docs and `WORKFLOW_STATE.json`). Last full gate (revision
  7, AVD): `spotlessCheck detekt lintDebug` clean, JVM unit tests 643, 0
  failures, instrumented 306, 0 failures (`RestAlarmEffectTest` 6 tests).
  Not run by anything yet: the physical-phone P-1 path through the real route.

### R3-A. Rest alert fixes (P-1, I-1)

- **R3-P1a [BOTH]** (P-1, back from `Why ›`) **WRITES** a workout. Log a set so
  a rest starts; let it end (one alert, strip reads `Rest done`). Open `Why ›`
  on an exercise, then Back. Expect: **no second alert** on returning.
- **R3-P1b [BOTH]** (P-1, Leave then Resume) **WRITES**. Rest ended and
  `Rest done` showing: `Leave` the workout to Home, then `Resume`. Expect: no
  second alert.
- **R3-P1c [BOTH]** (P-1, background then reopen) **WRITES**. Start a rest,
  background the app (lock the screen on the phone), let it end: **exactly one**
  alert. Reopen the app. Expect: no second alert, `Rest done` shown.
- **R3-P1d [AVD]** (P-1 control) Reopen the app while a rest is **still
  counting down**. Expect: its alert still fires once at the end.
- **R3-I1a [BOTH]** (I-1) **WRITES**. Start a rest, wait until under 15 s
  remain, tap `-15s`. Expect: the rest ends at once with **exactly one** alert,
  within about 5 seconds (Android's alarm minimum; not up to 15 s late), and no
  second one afterwards.
- **R3-I1b [AVD]** (I-1) With more than 15 s left, tap `-15s`: the rest
  shortens, alerts once at the new end. Tap `+15s`: alerts once at the later end.

### R3-B. Round-2 editor and recovery fixes (R2-F-1 to R2-F-4)

- **R3-F1a [AVD]** (R2-F-1) `Add exercise`: tap the name field, then tap away
  or move focus **without typing**. Expect: the name error appears. Fresh open
  shows none.
- **R3-F1b [AVD]** (R2-F-1) Same on `New plan`: focus the name field and leave
  it empty -> error appears.
- **R3-F2a [AVD]** (R2-F-2) Plan editor, a plan with enough rows to scroll:
  type a name that duplicates an existing plan, scroll to the bottom, `Save`.
  Expect: the screen scrolls to the name field and the duplicate error reads
  **under the name field** (not at the foot of the list).
- **R3-F2b [AVD]** (R2-F-2) Exercise editor on a short screen or at large
  font, name scrolled out of view, empty or duplicate name, `Save`: the name
  field scrolls into view with its error.
- **R3-F3 [AVD]** (R2-F-3) Home -> `Log recovery` at 384dp, nothing chosen.
  Expect: the hint `Choose a value on each of the six scales to save.` sits
  **directly above `Save entry`** inside the pinned bar, Save disabled. Choose
  all six: hint gone, Save enabled. **WRITES** one check-in (AVD). Dark and
  light.
- **R3-F4 [AVD]** (R2-F-4) Plan editor: add a new exercise row, blank a
  required field (set count or reps) -> the error reads **`Enter a value.`**,
  not `Add at least one exercise.`.

### R3-C. Notification prompt (R2-F-5)

- **R3-F5a [AVD]** (R2-F-5) Fresh install (uninstall first), log a first set;
  **deny** notifications. With the rest running tap `-15s` and `+15s` several
  times. Expect: **no** notification prompt reappears mid-rest.
- **R3-F5b [AVD]** (R2-F-5) After `Skip rest` (or leaving and re-entering the
  workout) and starting a new rest, the prompt may ask again; logging a new set
  while a rest is still running does not re-ask. **WRITES** a workout.

### R3-D. Regression pass

- **R3-R1 [AVD]** Rest timer: strip appears after a set, `Skip rest` (one line)
  clears it with no alert, `+15s` extends, a new set restarts the rest, `Finish`
  ends the workout with no alert afterwards and nothing left running.
- **R3-R2 [AVD]** Plan editor: add, reorder, expand a row, steppers, `Save`
  (new version); Back with edits asks `Discard changes?`; Back unedited does
  not; no name error on open.
- **R3-R3 [AVD]** Exercise editor: presets, save, duplicate-name error shows
  after typing, no error on open.
- **R3-R4 [AVD]** Workout: board -> focus -> log -> `Undo last` -> `Finish`
  -> done -> `Back to Home`.

### R3 for the phone tester

Back up the phone's data first. Steps that write say so; they log a test
workout, finish or `Abandon` it afterwards.

1. **[PHONE] R3-P1a. WRITES a test workout.** Log a set, let the rest end
   (one alert, `Rest done`), open `Why ›`, Back: no second alert.
2. **[PHONE] R3-P1b. WRITES (same workout).** With `Rest done` showing, `Leave`
   -> Home -> `Resume`: no second alert.
3. **[PHONE] R3-P1c. WRITES (same workout).** Start a rest, lock the screen,
   let it end: exactly one alert; unlock and reopen: no second alert.
4. **[PHONE] R3-I1a. WRITES (same workout).** Start a rest, wait until under 15
   s remain, tap `-15s`: one alert within about 5 seconds, no second.
5. **[BOTH] R3-F1/F2/F3/F4** are look-only on the phone if the tester does not
   `Save`; use the AVD for any step marked WRITES.

### R3 known deferred (not findings)

- **Group B (B1-B6) stays in the grandchild
  `repflow-redesign-visual-foundation-remediation-1-remediation-1`**
  (phase `PLANNING`): Settings 5c/5d, Progress 5b, set entry keeping its values
  (D60), the three empty-state / search-field / ordering defects, and the
  Settings entry point for the exact-alarm prompt. Do not report them here.
- Review observations left as-is: O3 (exact-alarm prompt offered once), O4
  (font scale for `Skip rest`), O5 (formatting), and implementation review O-3
  (the P-1 test covers the effect, not the route; this round's phone steps
  cover the real path).
- Everything rounds 1 and 2 listed as deferred or accepted stays so.

### R3 expected result and what happens next

Every step behaves as stated, nothing crashes, no earlier area regressed. If
clean: `/accept-milestone repflow-redesign-visual-foundation-remediation-1` is
the only acceptance command; it needs every checkpoint in this item's registry
`COMPLETE` (all sixteen are) and, per the workflow, the group-B child must also
reach completion before this item can reach `MILESTONE_COMPLETE`. A checkpoint
still outstanding goes to `/milestone-implement`; no command records acceptance
of a partial round. Findings go to `.ai-review/feedback/FUNCTIONAL_REVIEW.md`,
then `/apply-functional-review repflow-redesign-visual-foundation-remediation-1`
(bounded branch for a same-scope fix, broad branch for a `...-remediation-<n>`
child).

---

## `repflow-redesign-visual-foundation-remediation-1` — Functional review checklist, round 2 (implementation revision 5; superseded by round 3 above for the changed areas)

Round 2 of the functional review. Round 1 (the longer checklist further down,
now headed "round 1") returned FAIL; its group-A findings were fixed
(`A1`–`A6`, `J1`–`J9`, the dash nit), two implementation-review rounds then
corrected `J9`'s alarm flow and the done screen, and technical approval of
revision 5 is recorded (commit `8b564e9`, basis `EXTERNAL_APPROVE`). This round
re-tests **only what changed**, plus a short regression pass; for areas not
named here, round 1's steps stand and are not repeated. Findings go to
`.ai-review/repflow-redesign-visual-foundation-remediation-1/feedback/FUNCTIONAL_REVIEW.md`
(the file round 1 used was consumed). Every command names the child id
explicitly (`active_work_item_id` still points at the grandparent).

**Tags.** **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`),
loaded with round 1's sample data (see round 1 "Test data"; anything that
writes, restores or erases is AVD-only). **[PHONE]** the physical SM-S928B,
which holds **real data**: look only, and only what a step marks safe.
**[BOTH]** either; AVD first. Steps that write data say **WRITES** in bold.

### R2 setup and automated state

- Build and install on the AVD: `ANDROID_SERIAL=emulator-5554 ./gradlew
  installDebug`. Check dark and light where a step says so
  (`adb -s <serial> shell cmd uimode night yes|no`). Schema, flags and test data
  as round 1 (Room 7 -> 8, no flags, no network). Restore the sample backup on
  the AVD first (Settings -> Restore from a file).
- **Automated verification is current and not re-run here.** The working tree is
  clean and `git diff b66677d HEAD -- app` is empty (everything since is docs and
  `WORKFLOW_STATE.json`). Last runs, from the implementation bundle's
  `TEST_RESULTS.md`: revision 5 (`b66677d`) `spotlessCheck detekt lintDebug
  testDebugUnitTest` BUILD SUCCESSFUL, **641 JVM tests, 0 failures**; revision 4
  instrumented on the AVD (`presentation.workout` 63, `presentation.navigation`
  2, `MainActivityNavHostSmokeTest` 9, 0 failures); lint 0 errors, 21 warnings.
  Not run by anything yet: the physical-phone J9 re-test (below).

### R2-A. Group-A fixes (each: do the step, expect the result)

- **R2-A1 [AVD]** (A1) Plans -> Open a plan -> `Add exercise` -> dismiss the
  picker (swipe down / tap outside). Expect: no blank row appears in the plan.
  Repeat with `Create a new exercise`, then Back out of the editor: still no
  blank row.
- **R2-A2 [BOTH]** (A2) In a workout, log a set so the rest strip shows. Expect:
  `Skip rest` is on **one line** at 384dp, wider than `-15s` / `+15s`. **WRITES**
  a workout: on the phone only if you accept a real session; use the AVD.
- **R2-A3 [AVD]** (A3) Open an exercise whose load step is 1 (or set Load step
  to `Other` = 1): the step caption reads `1 step`, not `1 steps`. In a plan
  editor row with one warm-up set the row reads `1 warm-up`; with two,
  `2 warm-ups`.
- **R2-A4 [AVD]** (A4) Exercise editor (`Add exercise`) and plan editor (`New
  plan`): on open, **no** name error is shown. Tap the name field and leave it
  empty (or type then clear): the error appears. A duplicate name shows its
  error after typing.
- **R2-A5 [BOTH]** (A5) Progress -> an exercise with volume in the thousands:
  figures are grouped (`12,450` or the locale's separator), including the
  signed delta (`+1,250`) and the `Best:` line.
- **R2-A6 [AVD]** (A6) Home -> `Log recovery` on a day with nothing logged: all
  six scales start **unset** (no value pre-chosen), `Save entry` is disabled
  and the hint `Choose a value on each of the six scales to save.` shows. Choose
  each scale: Save enables once all six are set, then reads `Saved`. **WRITES**
  one check-in (AVD).
- **R2-J1 [BOTH]** (J1) Home -> recovery card -> `Details ›`. Intro reads "Your
  check-in adds up to one score: a read on how recovered you are today. It never
  limits what you can do."; gate note reads "Pain while walking counts the most.
  If it is 3 or more, or heel stiffness is 4 or more, today is Protect whatever
  the score." Each factor's dots fill to its `n/5` value (a 3/5 factor shows 3
  filled dots). Dark and light.
- **R2-J2 [BOTH]** (J2) Workout -> an exercise with history -> `Why ›`. Choose
  screen line: "Pick what you'll do next session. RepFlow saves your choice next
  to its suggestion."; after choosing: "Your choice is saved next to the
  suggestion." The reason after the dash starts lower-case. Then finish a workout
  that includes an exercise with **no working set** (only added, or warm-ups
  only): its done-screen suggestion row is absent; an exercise with one working
  set still shows `Not enough data yet`. **WRITES** a finished workout (AVD).
- **R2-J3 [AVD]** (J3) Workout board -> `Add exercise` -> `Create a new exercise`
  -> fill and `Save`. Expect: back on the board with the new exercise **already
  added** to the running workout. From a plan editor the same route simply
  returns to the plan (no auto-add). **WRITES** an exercise.
- **R2-J4 [AVD]** (J4) Finish a workout where an exercise used the same load as
  last time with more reps. Done screen `Versus last time` reads `same load, +2
  reps` (or the count you did) and counts as a gain; same load and reps reads
  `same load`. **WRITES** a finished workout.
- **R2-J5 [BOTH]** (J5) History -> a workout -> `⋮` -> `Invalidate workout`. The
  dialog reads "It's hidden from History and stops counting toward progress and
  suggestions. This can't be undone. Nothing is deleted — turn on Show
  invalidated to see it." **Look only: tap `Keep it`.** (Confirming writes; do it
  on the AVD only.)
- **R2-J6 [BOTH]** (J6) Progress -> an exercise whose best set has more than 12
  reps -> `Est. 1RM`: the card says "Est. 1RM needs a set of 12 reps or fewer."
  An exercise with sets of 12 or fewer still shows an estimate as before.
- **R2-J7 [BOTH]** (J7) History -> open a workout -> **system Back**
  (gesture/button). Expect: the detail closes and History stays; a second Back
  leaves History as usual. Read-only, safe on the phone.
- **R2-J8 [BOTH]** (J8) Plans -> Open an existing plan -> Back **with no edit**:
  it leaves with **no** `Discard changes?` prompt. Edit a name or a stepper, then
  Back: `Discard changes?` appears. Change a value and change it back: no prompt.
  Look only on the phone (do not Save).
- **R2-J9 [AVD]** (J9) Fresh install (uninstall `com.repflow.app` first, so the
  one-time prompt state and the permissions reset): log a first set. On
  API 33+ the notification prompt comes first; after it is answered (**allow or
  deny**) the explanation `Allow exact rest alerts?` appears **once** with
  `Allow` / `Not now`. `Allow` opens the system `Alarms & reminders` screen for
  RepFlow; toggle it and return. `Not now` closes it and rest alerts still work.
  The explanation does not return on the next rest. **WRITES** a workout.
- **R2-J9b [AVD]** (review fix I1) With the rest running, background the app,
  let it end (one alert), reopen the app: **no second alert** fires for the
  finished rest. Reopening while a rest is still counting down keeps its alert.
- **R2-J9c [AVD]** (review fix I2) Deny the notification permission, then reach
  the first rest: the exact-alarm explanation **still shows** (it waits only
  until the notification request is answered).
- **R2-DASH [AVD]** (dash nit) Settings -> `Erase all data`: the body reads
  "...recommendation on this device — including a workout that is still
  running...", an em dash, not a hyphen. **Look only: `Keep my data`.**

### R2-B. Review fix on the done screen

- **R2-D1 [AVD]** (XI2-I1) Finish a workout that has the **same exercise twice**:
  the first entry untrained, a later entry with a working set. The done screen
  keeps the suggestion for that exercise (one row, not lost). **WRITES** a
  finished workout.

### R2-C. Short regression pass (flows the fixes touched)

- **R2-R1 [AVD]** Plan editor: add, reorder, expand a row, change steppers and
  `Save` (new version) work; Back with edits asks `Discard changes?`.
- **R2-R2 [AVD]** Workout: board -> focus -> log a set (entry clears) -> `Undo
  last` -> `Finish` -> done -> `Back to Home`; no workout left running.
- **R2-R3 [AVD]** Recovery entry: set six scales, futsal toggles still work,
  `Save entry` -> `Saved`; the recovery history chart shows the new day.
- **R2-R4 [AVD]** History detail: sets, PR medal, zero-set `No sets logged`, `⋮`
  absent on an invalidated workout.
- **R2-R5 [AVD]** Progress: chips, the three metrics, the empty-trend copy for an
  exercise with under two sessions.
- **R2-R6 [AVD]** Exercise editor: presets, save, duplicate name error.
- **R2-R7 [AVD]** Rest timer: strip, `-15s`/`+15s`, `Skip rest`, `Rest done`;
  `Auto-start` off starts no strip.

### For the user (phone)

Only these need the real phone. The phone holds real data; nothing below
restores, erases or exports.

1. **[PHONE] J1 / J2 / J5 / J6 wording, look only.**
   - Home -> recovery card -> `Details ›` (needs today's check-in; otherwise
     skip and say so): read the intro and gate note, check the dots match `n/5`.
   - A workout in progress is **not** needed: History -> any workout -> `⋮` ->
     `Invalidate workout`: read the dialog, then tap **`Keep it`**.
   - Progress -> a high-rep exercise -> `Est. 1RM`: read the new line.
   - Recommendation wording (J2) needs a workout: **skip on the phone**, covered
     on the AVD (R2-J2).
2. **[PHONE] J7 History Back (safe).** History -> a workout -> system Back:
   the detail closes and you stay in History.
3. **[PHONE] J8 plan editor (only if a plan exists; safe).** Plans -> Open a plan
   -> Back with no edit: no `Discard changes?`. Edit something and Back: the
   prompt appears -> `Discard`. Do not tap `Save`.
4. **[PHONE] J9 exact-alarm flow. WRITES data: it needs a workout and logs a
   real set.** Only do this if you accept one real session (finish or abandon it
   afterwards; `Abandon` removes it). Alternative: the AVD (R2-J9, R2-J9b,
   R2-J9c) covers the same logic; the phone is for the real lock-screen timing.
   - Expect the one-time `Allow exact rest alerts?` explanation after the first
     rest on a build where `Alarms & reminders` is not yet allowed; `Allow` opens
     the system `Alarms & reminders` screen -> allow or deny -> return.
   - Then lock the screen during a rest: **exactly one** alert at rest end (not
     two), and none when you reopen the app afterwards.

### R2 known deferred (not findings)

- **Group B (B1–B6) went to the grandchild
  `repflow-redesign-visual-foundation-remediation-1-remediation-1`**
  (phase `PLANNING`): Settings 5c/5d, Progress 5b, set entry keeping its values
  (D60), and the three empty-state / search-field / ordering defects. Do not
  report them against this item. That child also owns the Settings entry point
  for the exact-alarm prompt (J9's "offered once" is the recorded judgement
  call, review finding O3).
- Review observations left as-is by decision: O3 (prompt offered once), O4
  (font scale for `Skip rest`), O5 (formatting).
- Everything round 1 listed as deferred or accepted stays so (see round 1's
  "Known limitations" and "Functional review round 1 — outcome").

### R2 expected result and what happens next

Every step above behaves as stated, nothing crashes, and no round-1 area
regressed. If clean: `/accept-milestone
repflow-redesign-visual-foundation-remediation-1` is the only acceptance
command; it needs every checkpoint in this item's registry `COMPLETE` (all
sixteen are) and, per the workflow, the group-B child must also reach
completion before this item can reach `MILESTONE_COMPLETE`. A checkpoint still
outstanding goes to `/milestone-implement`; no command records acceptance of a
partial round. Findings go to the file above and then
`/apply-functional-review repflow-redesign-visual-foundation-remediation-1`
(bounded branch for a same-scope fix, broad branch for a
`...-remediation-<n>` child).

### Functional review round 2 — outcome (2026-10-02)

Round 2 returned **FAIL** (AVD: 23 of 25 steps passed; phone: J5, J6, J7, J9
passed, one new Important). The user decided to fix all six findings in this
item. `/apply-functional-review` judged all six bounded (none is multi-checkpoint
or cross-cutting), so nothing was routed to
`repflow-redesign-visual-foundation-remediation-1-remediation-1`, which stays
with group B only. Technical approval was marked `STALE` first (`62fd98a`),
before any source edit. One commit per finding; every new test was run against
the pre-fix code where it could be (P-1, R2-F-5, R2-F-2, R2-F-3 fail there;
R2-F-1 and R2-F-4 fail at compile or on assertion against it).

| Finding | Class | Disposition | Commit |
|---|---|---|---|
| P-1 duplicate rest-end alert on re-entry | defect | the `LaunchedEffect(restEndAt)` path scheduled a past-due exact alarm. It is now `RestAlarmEffect`, behind the same `shouldRearmRestAlarm` guard as `ON_RESUME`; it still cancels when there is no rest. `RestAlarmEffectTest` (5, instrumented) fails 3 of 5 with the guard removed | `f89f3c5` |
| R2-F-1 name error on focus-and-leave | defect | both editors mark the name touched when the field loses focus after having had it (`onNameFocusLost`) | `62044cc` |
| R2-F-2 duplicate-name error invisible | usability | the plan editor now reports it under the name field (was the foot of the list); both editors scroll the name field into view when Save is refused for it | `7cce1f2` |
| R2-F-3 recovery hint under Save | usability | the hint is inside the pinned bar, directly above Save (`D113` amended) | `f1bba20` |
| R2-F-4 wrong text on a new plan row | defect | the `Required` field error reads "Enter a value." (it was mapped to "Add at least one exercise.") | `8b92020` |
| R2-F-5 notification prompt mid-rest | defect | the permission effect is keyed on whether a rest is running, so `+/-15s` never re-asks; it asks again once the rest has gone away and come back (`Skip rest`, or leaving and re-entering), not when a new set replaces a rest still in the session (`D114` amended; wording corrected in round 6, O-1) | `0e591a4` |

Judgement calls, for the reviewer. P-1: the expiry handler was left as it is -
the defence-in-depth idea (ignore a rest whose `endAt` is long past) was not
taken, because the handler's documented behaviour is to alert for the rest it
finds, and the scheduling guard closes every path that creates a past-due alarm.
A `-15s` tap that moves the end into the past neither schedules nor cancels; the
alarm already pending for the old end is left alone. (Superseded by the
implementation review round 6, I-1 below: that case now fires one alert, within about 5 seconds (round 3, R3-F-1).) R2-F-4: the message is
corrected rather than hidden until touched. J9's decisions (exact alarm, the
one-time explanation, the inexact fallback, the `rest_timer` sound, explicit
vibration) are unchanged.

**Gate (AVD `RepFlow_S24Ultra_384dp_API36`, `emulator-5554`):** `spotlessCheck`,
`detekt`, `lintDebug` clean; JVM unit tests 643, 0 failures; instrumented 305,
0 failures (data+infrastructure 85, workout 69, presentation backup/design
system/exercise/history/home 64, navigation/progress/progression/recovery/
settings/trainingplan 78, `MainActivityNavHostSmokeTest` 9).

### Implementation review of revision 6 - outcome (2026-10-02)

`/review-implementation` returned **REVISE: 0 Blocking, 1 Important, 3
Optional** on the round 2 fixes. The orchestrator chose the behaviour for I-1
(fire at once); the user may overrule it.

| Finding | Disposition | Commit |
|---|---|---|
| I-1 `-15s` that ends the rest leaves the old alarm pending (alert up to 15 s late) | validated: `RestTimer.withRemovedSeconds` clamps to the domain clock, so by the time the effect runs the new end is past and the old alarm (for the old, future end) stayed. `RestAlarmEffect` now remembers the previous end within the composition; when it was still in the future and the new end is not, it reschedules the single alarm to the past end, which fires once, within about 5 seconds because of Android's alarm minimum (revision 5's behaviour; wording corrected in round 3, R3-F-1). Re-entry has no previous end and still schedules nothing (P-1). `RestAlarmEffectTest` asserts both (6 tests); the I-1 test fails on the previous code (`D114` amended) | `982868c` |
| O-1 "the next rest start asks again" overstated | reworded in the route KDoc, `D114` and the test name: a new set replaces a rest that is still in the session, so the ask repeats only after the rest has gone away and come back (`Skip rest`, or leaving and re-entering the workout) | `946c8f5` |
| O-2 exercise-editor scroll test could not fail | the screen is composed in a 260 dp box so the name scrolls away; verified to fail with `bringIntoViewWhen` removed | `8c4698f` |
| O-3 P-1 test covers the effect, not the route's use of it | no change, as the reviewer says; round 3's phone re-test covers the real path | - |

**Gate (AVD `RepFlow_S24Ultra_384dp_API36`):** `spotlessCheck`, `detekt`,
`lintDebug` clean; JVM unit tests 643, 0 failures (one earlier run had a
Turbine timeout in `TrainingPlanListViewModelTest`, a file this round did not
touch, and passed on rerun - the same flake class already disclosed); instrumented
306, 0 failures (305 plus the new `RestAlarmEffectTest` case).

**Re-test (round 3):** [PHONE] P-1: with `Rest done` showing, open `Why ›` and
come back, and `Leave` then `Resume` - no second alert; [AVD] a plan or exercise
with the name field tapped and left empty; a duplicate plan name saved from the
bottom of a long plan; Log recovery at 384dp with Save disabled (the hint above
the bar); a new plan row's blank fields; a notification denial followed by `-15s`
taps.

**Next action.** Technical approval is `STALE`, so the item re-enters
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` with a regenerated post-fix bundle:
review it, then `/apply-implementation-review
repflow-redesign-visual-foundation-remediation-1` (or `/approve-review
implementation repflow-redesign-visual-foundation-remediation-1` on a clean
APPROVE), then `/prepare-functional-review
repflow-redesign-visual-foundation-remediation-1` for round 3.

### Functional review round 3 — outcome (2026-10-03)

Round 3 returned **FAIL** (AVD: 16 of 18 steps passed; phone: the single-alert
steps passed, R3-I1a and R3-F1b failed on timing). Every rest-alert fix from
round 2 held. The user decided: R3-F-1 wording only (accept the delay), and fix
R3-F-2 to R3-F-5 in this item. `/apply-functional-review` judged all five
bounded (each is a contained change to the two editors or a wording correction),
so nothing was routed to
`repflow-redesign-visual-foundation-remediation-1-remediation-1`, which stays
with group B only. Technical approval was marked `STALE` first (`df20b79`),
before any source edit.

| Finding | Class | Disposition | Commit |
|---|---|---|---|
| R3-F-4 name field loses focus when its error toggles (Important, older bug) | defect | cause found by bisecting on the AVD: the name's error string was read with `stringResource` inside the right-hand side of an elvis (`fieldErrorText(..) ?: if (duplicate) stringResource(..)`), so that composable call came and went with the error, shifted the slots after it and recreated the text field - focus, keyboard and the next keystroke were lost. A bare Material 3 field with the same error toggling keeps focus, which ruled out the library. The string is now read unconditionally in both editors. One Compose test per editor (focus, clear, still focused, type `P`, still focused) fails on the previous code | `b2cc2cd` |
| R3-F-3 a repeated refused Save does not scroll to the name | defect | the scroll trigger was the boolean "duplicate-name error is showing", which does not change between two refusals once the intermediate `isSaving` state is conflated away. The submit error now carries a refusal count (`attempt`, one per refused save), and `bringIntoViewWhen` also keys on it, so every refusal scrolls. Compose tests repeat the refusal three times in a short viewport; ViewModel tests assert two refusals differ | `6209ecd` |
| R3-F-5 duplicate-name error stays after the name is edited | defect | `onNameChanged` clears a `DUPLICATE_NAME` submit error in both ViewModels (other kinds are kept); Save checks again. ViewModel tests, both editors | `6209ecd` (shares the commit with R3-F-3: one ViewModel change and one test cover both) |
| R3-F-2 touch and Done cannot take focus off the name field | usability | a tap that is not a drag and does not land on a text field clears focus in both editors, observed at the initial pointer pass without consuming it, so chips, steppers and buttons still get their click; the name and the exercise editor's other text fields keep focus when tapped (a marker set by each field before the form's handler reads it). The name and `Other` fields' IME action is Done and clears focus. The R2-F-1 touched-on-blur rule now works by touch, including on an empty `New plan`. Compose tests in both editors (tap on a label, a chip or `Add exercise`, Done, and tapping the field itself) | `faec518` |
| R3-F-1 `-15s` alert "at once" (wording only) | no behaviour change | every claim that the alert fires "at once" or "immediately" now says within about 5 seconds (Android's alarm minimum): the `RestAlarmEffect` comment, the `RestAlarmEffectTest` name and comment, `D114`, and this file's checklist lines (the round-2 outcome's "now fires the alert at once" and the round-6 I-1 row). The test was renamed `...FiresTheAlertOnce` | `2eca96d` |

Judgement calls, for the reviewer. R3-F-2: a screen-level tap observer was chosen
over per-control `clearFocus` calls because the finding names every control
(chips, steppers, `Add exercise`, adding a row) and new controls would
otherwise each need the call; a drag, which scrolls the form, does not clear
focus. The notes field is multi-line, so its keyboard action stays a newline.
R3-F-4 was not fixed by dropping `isError` or the supporting text, which the
bisect showed were not the cause, so the field's look is unchanged.

**Gate (AVD `RepFlow_S24Ultra_384dp_API36`, `emulator-5554`):** `spotlessCheck`,
`detekt`, `lintDebug` clean; JVM unit tests 644, 0 failures; instrumented 312, 0
failures (data+infrastructure 85, presentation backup/design
system/exercise/history/home/navigation/progress/progression/recovery/settings/
trainingplan 148, workout 70, `MainActivityNavHostSmokeTest` 9). The AVD was
stopped afterwards; the phone was not used.

**Re-test (round 4):** [AVD] in both editors: delete the name one character at a
time and type again - the keyboard stays up and no key is lost; tap blank space,
a chip, a stepper, `Add exercise` and the keyboard's Done with the name focused
(the field loses focus, and an empty name then shows its error, including on an
empty `New plan`); a duplicate name saved repeatedly from the bottom of a long
form scrolls to the name every time; editing a refused duplicate name clears the
error and Save checks again. [BOTH] `-15s` that ends the rest: one alert within
about 5 seconds.

**Next action.** Technical approval is `STALE`, so the item re-enters
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` with a regenerated post-fix bundle:
review it, then `/apply-implementation-review
repflow-redesign-visual-foundation-remediation-1` (or `/approve-review
implementation repflow-redesign-visual-foundation-remediation-1` on a clean
APPROVE), then `/prepare-functional-review
repflow-redesign-visual-foundation-remediation-1` for round 4.

---

## `repflow-redesign-visual-foundation-remediation-1` — Functional review checklist, round 1 (implementation revision 2; superseded by round 2 above for the changed areas)

Round 1 of this checklist returned FAIL and its findings are applied (see
"Functional review round 1 — outcome" below); the item is back in external
implementation review. Technical approval had been recorded (commit `20a8d1f`,
basis `EXTERNAL_APPROVE`, plan revision 20) and is now `STALE`. Findings go to
`.ai-review/repflow-redesign-visual-foundation-remediation-1/feedback/FUNCTIONAL_REVIEW.md`.
Every command names the child id explicitly (`active_work_item_id` still points
at the parent). The deviation register is
`docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`
section 4; "register rows" below are its `D<n>` ids.

**Where each step can be verified** (the tag in front of every step):

- **[AVD]** the AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`), loaded with
  sample data through Settings -> Restore from a file. Anything that writes data,
  restores or erases is AVD-only.
- **[PHONE]** the physical SM-S928B (`RFCXA0RLSVT`). It holds **real data**:
  only look, and only do what is marked non-destructive. **Never** use Restore
  from a file, Erase all data, or Export-then-restore on it, and avoid starting
  or finishing workouts there unless you accept new real rows.
- **[BOTH]** reachable on either; run on the AVD first.

### Setup

- **Build and install on the AVD.** Start the AVD, then
  `./gradlew installDebug` with `ANDROID_SERIAL=emulator-5554` (or
  `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
  after `./gradlew assembleDebug`). Nothing was run against a device by the
  command that wrote this checklist.
- **Phone:** the same APK is yours to install, or not, when you choose. The
  full instrumented suite ran on this phone earlier in CP16 (see "Automated
  verification").
- **Theme.** The app follows the system theme (there is no in-app Theme setting;
  that is `5c`, deferred). Switch with
  `adb -s <serial> shell cmd uimode night yes|no`. Check **both** dark and light.
- **Schema.** This build migrates Room 7 -> 8 (a new `settings` table). An
  upgrade over an older install keeps all data; the phone upgrade is covered by
  `RepFlowDatabaseMigrationTest`.
- No feature flags, no network.

### Automated verification (current, deliberately not re-run)

Nothing under `app/` changed since the last forced full run: `git diff 093956d
HEAD -- app` is empty (the commits since are docs and `WORKFLOW_STATE.json`
only) and the working tree was clean. Evidence from CP16:

```
./gradlew --rerun-tasks spotlessCheck detekt lintDebug testDebugUnitTest \
  assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

BUILD SUCCESSFUL, 96/96 tasks; **621 JVM tests (101 classes), 0 failures**;
**269 instrumented tests on `RepFlow_S24Ultra_384dp_API36`, 0 failures**; lint 0
errors (21 warnings, 1 hint, the standing baseline). The physical phone ran the
full suite earlier in CP16 (268/269, the one failure fixed) and then the changed
classes. The three last presentation fixes (scroll padding, two strings, the
empty state) ran on the AVD only.

### Test data

Seed the AVD, not the phone. Generate a backup file (`BackupSnapshot`, the app's
versioned transfer schema) with: three exercises, one per tracking type (`Bench
Press` weight and reps with a load step, `Pull Up` reps only, `Plank` duration),
one archived exercise, two active plans and one archived plan (one plan with a
reps row and a duration row, one warm-up set), at least **six completed
workouts across at least two months** (so History month sections, PR badges,
Progress bars and the done screen's "Versus last time" have content; include one
invalidated workout and one with a heavier set than before), recovery check-ins
for the last ~14 days with a gap or two, and two futsal sessions. `adb -s
emulator-5554 push <file> /sdcard/Download/`, then Settings -> Restore from a
file (steps A8-A9). Also keep a **fresh install** (no data) for the empty
states (step B1).

---

### A. The shell, navigation and Home (F1: four-tab IA, Home) — CP2, CP4, CP5

- **A1 [BOTH]** Launch. Bottom bar shows exactly **Home, Plans, History,
  Progress**; Home is the start tab; the selected tab uses the filled glyph.
  Expect: no Exercises or Recovery tab.
- **A2 [BOTH]** Home header: the date in small caps over "Ready when you are", and
  a round gear button (`Open settings`) top right -> Settings. Expect: no tab
  bar on Settings; back arrow returns.
- **A3 [AVD]** (with data, no running workout) Start card: "Today", a plan, `N
  exercises · N working sets`, `Start workout`, and `Train something else ›` ->
  a sheet "Your plans" with every active plan then `Empty workout`. Expect the
  plan shown is the one last trained.
- **A4 [AVD]** Recovery card (with today's check-in): score in a band colour and
  word, `Details ›` opens the readiness sheet (score, bar, driver sentence, one
  row per factor with `v/5 · pulling the score down | fine`, gate note,
  `Close`). Without a check-in: the empty state and `Log recovery`.
- **A5 [AVD]** "Last workout" card: name, weekday and minutes, `N load
  increases` or `No load increases`.
- **A6 [AVD]** Start a workout, then go Home (leave it running): the **resume
  card** (accent, "<plan> - still running", elapsed and sets logged ticking,
  `Resume`, `Finish it`, trash). Trash -> `Abandon this workout?` (`Keep it` /
  `Abandon`).
- **A7 [BOTH]** Light theme: Home, the start card, readiness sheet are legible; the
  accent resume card stays dark in light theme (a parent decision, not a bug).
- **A8 [AVD]** Settings -> Data -> `Restore from a file` with the generated backup:
  confirm sheet, then `Backup restored`. Expect the message stays on screen long
  enough to read (a CP16 fix) and the tabs fill with the sample data.
- **A9 [AVD]** Run A3-A5 again after the restore.

### B. Empty states and first run — CP5, CP10, CP12, CP15 (`1d`)

- **B1 [AVD, fresh install]** With no data: Home shows the first-run card (`Create a
  plan`, `Empty workout`); Plans, History, Progress and the library each show
  the empty treatment (26dp glyph at low opacity over one line). Expect no
  crash, no blank screen.

### C. Workout mode: board, focus, rest, finish, done — CP6-CP9, CP14

- **C1 [AVD]** Home -> `Start workout`. The **board**: `X` (Leave workout), plan
  name, clock, `Finish`, progress `N of M exercises · S/T sets`, one row per
  exercise with `UP NEXT` on the first unfinished and a status chip that always
  carries a word and a glyph. **No tab bar** during the workout.
- **C2 [AVD]** `Add exercise` -> picker sheet with search; pick one. An exercise
  added here (not from a plan) shows `No sets yet`, then `N sets` once logged
  (register row D55).
- **C3 [AVD]** Tap a row -> **focus mode**: `Board`, elapsed, `Finish`; `Exercise N
  of M`, the name, `X of Y sets done`, collapsed technique notes (if any),
  `Last: ...` and `Undo last`, pending rows `Set N: not logged · Target: ...`.
- **C4 [AVD]** Steppers: weight steps by the exercise's load step (2.5 kg if none),
  reps by 1, seconds by 5; tap a value -> keypad (`1-9 . 0 backspace`, max 6
  characters). Open the extra-detail disclosure: RPE 0-10, pain 0-5, technique
  0-5 scale rows, tap the chosen cell to clear; warm-up chip.
- **C5 [AVD]** `Log set`: the entry **clears** to `—` afterwards (register row D60,
  deferred carry-over, see Known limitations). `Log warm-up` does not count
  toward the set count.
- **C6 [AVD]** Pencil on the **last** set only -> correction sheet (weight / reps /
  seconds); `Undo last` removes it.
- **C7 [AVD]** Rest: after a set a strip appears (`Resting`, `-15s`, `+15s`, `Skip
  rest`, dismiss). Let it hit zero: it says `Rest done`. With the app in the
  background the end-of-rest notification and buzz fire (the notification
  permission prompt appears the first time, API 33+).
- **C8 [AVD]** Suggestion strip + `Why ›` (needs a prior session of that exercise):
  opens the recommendation screen. Check the three states: suggestion
  (`Go with the suggestion`, `Pick another load`, `Keep the same load`),
  `Your call` (Increase / Maintain / Reduce, selected one marked), and the
  recorded state (`You overrode this` / `You went with the suggestion`, `Change
  my mind`). Also `Not enough data yet`.
- **C9 [AVD]** `X` -> leave sheet: `Leave it running and go Home`, `Finish and save it
  now`, `Abandon this workout` (asks to confirm), `Keep training`. Leaving lands
  on Home with the resume card.
- **C10 [AVD]** `Finish` -> `Finish this workout?` sheet: elapsed + progress,
  `N still unfinished` list, `Finish and save`, `Keep training`, `Leave it
  running and go Home`. `Next ›` on the last unfinished exercise raises it too.
  Home's `Finish it` raises the same sheet; dismissing it returns Home.
- **C11 [AVD]** Confirm -> **done screen**: `<day> · finished`, name, Time / Sets /
  Trained tiles, best-set cards, `Versus last time` rows (`+2.5 kg`, `same
  load`, `first time`, ...), `Suggestions for next time` with `Why ›`, `Saved to
  History as ...`, `Back to Home`. Back Home leaves no workout on the back stack.
- **C12 [PHONE, non-destructive]** If you want a device look at workout mode
  without data risk: open the workout screen only if a workout is already running;
  otherwise skip. Starting one on the phone writes a real session.
- **C13 [AVD]** Light theme pass over the board, focus, sheets and the done screen.

### D. Exercises (library and editor) — CP10

- **D1 [AVD]** Settings -> `Exercise library`: back arrow, a 48 search field,
  `Active` / `Archived` chips, rows `<type> · rest m:ss · in N plans | not in
  any plan`, archived rows dimmed with an `archived` badge, `⋮` -> row sheet
  (Edit; Archive or Restore), snackbar with `Undo` after archiving.
- **D2 [AVD]** Search a name with no match: `Not here? Create "<query>"` opens the
  editor with the name prefilled. While searching, `N matches` shows.
- **D3 [AVD]** `Add exercise` (bottom bar) -> editor: name field, tracking-type
  three-segment control, `Default rest` presets 1:00 1:30 2:00 3:00 Other, `Load
  step` presets 1.25 2.5 5 Other, technique notes, `Save` on the bottom bar.
  A duplicate name shows an inline error under the field. Tap a selected preset
  again to clear it (flagged in the register, see the judgement section).

### E. Plans — CP11

- **E1 [AVD]** Plans tab: `Active` / `Archived` pills; a card per plan (`N exercises ·
  vN`), `Archive`/`Restore` on the card face, `Start workout` (active) and
  `Open`; footnote `Archiving never touches completed workouts.`; `New plan` on
  the bottom bar.
- **E2 [AVD]** `Start workout` on a card starts that plan's workout and opens the
  board. With one already running it says `A workout is already running.`
  (register row D77).
- **E3 [AVD]** Open a plan: name field; rows with stacked up/down carets, `3 x 8-12
  reps · 1 warm-up · 90s rest`, `optional` badge, trash. Tap a row to expand
  steppers (working sets, warm-up sets, reps or seconds, rest with presets,
  `Optional`, `Change exercise`). Values open the keypad. `Add exercise` opens
  the picker sheet (`Add to plan`, search, `Create a new exercise`).
- **E4 [AVD]** Existing plan note: "Changes apply to the next session you start
  from this plan. Past workouts keep the version they were run on." Save writes
  a new version; Back with edits asks `Discard changes?`.

### F. History and workout detail — CP12

- **F1 [AVD]** History: `N workouts · newest first` (the order word toggles), filter
  chips (plan sheet, `Show invalidated`, from / to date sheets, exercise sheet),
  month section labels, rows with `PR` and `invalidated` badges and the meta
  line.
- **F2 [AVD]** Open a workout: `<plan> · version N`, Time / Volume / Sets tiles,
  per-exercise blocks with the change against last time, a medal for a personal
  best, every set (warm-ups marked), a timed set reads `45 s`; a zero-set
  exercise reads `No sets logged`.
- **F3 [AVD]** Detail `⋮` -> `Invalidate workout` -> `Invalidate this workout?` (`Keep
  it` / `Invalidate workout`). After invalidating, the detail closes and
  `Show invalidated` shows it with its badge; the `⋮` is absent on it.

### G. Recovery — CP13

- **G1 [AVD]** Reach Recovery from Home's recovery card (`Log ›`): back arrow, a date
  row with `Change` (sheet, today or earlier only), six scale rows each with end
  labels (DOMS, heel stiffness, pain and heavy legs run None -> Severe; sleep
  Terrible -> Great, energy Flat -> Fresh), futsal toggles, minutes and RPE
  steppers while `Played in last 24h` is on, `Training load N`, notes.
- **G2 [AVD]** `Save entry` is the one pinned save: reads `Saved` with a check until
  the next edit. With `Played` on and both futsal fields empty it saves the
  check-in alone; one filled without the other shows "Some values need attention
  before saving."
- **G3 [AVD]** Recovery history (`History` link on the entry): `Sleep & energy · 14
  days` chart, gaps break the lines, tap a day for its readout, futsal dots,
  `Entries` rows, `Futsal sessions`.

### H. Settings and the behaviours it gates — CP14

- **H1 [BOTH]** Settings screen: `Library`; `Rest timer` (Auto-start, Vibrate,
  Notification); `During a workout` (Keep screen awake, Confirm before
  finishing); `Data` (`Export a backup`, `Restore from a file`, `Workout history
  as CSV`); `Irreversible` card with `Erase all data`; footer. Each row toggles
  as a whole and announces as a switch. The top bar title no longer has content
  running through it (a CP16 fix); scroll to check.
- **H2 [AVD]** Turn `Auto-start` off: logging a set starts no rest strip. On again:
  it does. `Confirm before finishing` off: every finish entry point (board,
  focus, leave sheet, `Next ›`, Home's `Finish it`) completes at once and lands
  on the done screen. Turn it back on.
- **H3 [AVD]** `Keep screen awake` on: the screen stays on during a workout (set the
  screen timeout to 15 s to see).
- **H4 [AVD]** `Export a backup` writes a file (`Saved`); `Restore from a file` on a
  corrupt file shows the failure message and changes nothing; `Workout history
  as CSV` exports.
- **H5 [AVD only, never the phone]** `Erase all data` -> typed confirmation (`Type ERASE
  to confirm`, `Erase everything`, `Keep my data`) -> `All data erased`.
  Settings survive; every training table is empty; exported files untouched.

### I. Progress — CP15

- **I1 [AVD]** Progress: title, sideways-scrolling exercise chips (most recently
  trained first), a Top set / Est. 1RM / Volume control (reps-only and timed
  exercises offer Top set alone), a card with a signed delta, the value, bars
  (latest in the accent, month labels), `Best: N kg · N-session window`, and the
  note "Only valid sessions count...". An exercise with under two sessions shows
  "Not enough sessions yet. A trend needs at least two." Invalidating a workout
  removes its bar.

### J. Side-by-side with the design (F1's headline criterion)

- **J1 [BOTH]** Compare each converted screen to its Claude Design artboard
  (`4a` Home/Plans/History/Progress/Settings and workout mode, `6a`/`6c`
  recommendation, `6b` rules, `3a`/`3b` History, `3c`/`3d` Recovery, `2a`-`2c`
  Plans and library, `1d` empty and light states). CP16's contact sheets are
  at `.ai-review/repflow-redesign-visual-foundation-remediation-1/cp16-side-by-side/`
  (`INDEX.txt`, gitignored, local). Expect the same composition apart from the
  registered deviations; anything else is a finding.
- **J2 [BOTH]** Touch targets (>= 44dp), "never colour alone" (every state has a
  word or glyph), dark and light both legible.
- **J3 [PHONE]** The physical device is the authoritative width (384dp); anything
  clipped or cramped there but not on the AVD is a finding.

---

### For the user's judgement

Items the plan or the checkpoints flagged for you. None is accepted or reworded
by the milestone itself. Reach each on the AVD after restoring the sample data
unless stated.

**Copy (accept or reword)**

- **D42** readiness sheet title "How today's score was set" and its two lines
  ("...never a limit on what you are allowed to do"; gate note names heel
  stiffness >= 4). Home -> recovery card -> `Details ›`.
- **D47** recovery card `Details ›` and the empty state "Nothing logged today.
  Pain while walking and heavy legs shape the load suggestions." / `Log
  recovery`. Home with and without today's check-in.
- **D52** recommendation screen's footnote, "Whatever you pick is kept on record
  as your final say...", "Your choice wins...", `You went with the
  suggestion`, and `Not enough data yet`. Focus mode -> `Why ›`.
- **D67** done-screen `Versus last time` words (`+2.5 kg`, `same load`, `first
  time`, `N warm-ups only`, `no sets recorded`, ...) and the label `Suggestions
  for next time`. Finish a workout.
- **D93** the detail `⋮` is named `Invalidate workout` and opens the dialog
  ("It leaves History and stops counting toward progression. Nothing is
  deleted - turn on Show invalidated to see it again."). History -> a workout.
- **D105** the Erase all data dialog's wording (blast radius, what stays, `Type
  ERASE to confirm`, `Couldn't erase. Nothing was changed.`). Settings (AVD).

**Product calls (accept or reject)**

- **D55** an exercise added during a workout has no set target: `No sets yet` /
  `N sets`, finished after one working set. Board -> `Add exercise`.
- **D56** `Create a new exercise` from the workout picker opens the full
  exercise editor instead of the design's inline sheet. Board -> `Add
  exercise` -> `Create a new exercise`.
- **D57** the board keeps the `Heavy legs: n/5 · Leg DOMS: n/5 · Futsal in the
  last 24h (load n)` context line the design does not draw. Board, with a
  check-in or futsal entry for today.
- **D73** the library's second filter chip (`Weight & reps`) is not built; only
  `Active` / `Archived`. Library.
- **D77** `Start workout` on every active plan's card starts it directly (design
  has `Start day 2` / `Make active`). Plans tab.
- **D82** plan-editor row: trash and reorder carets stay on the collapsed row;
  the carets are 44 x 32, below the 44 floor. Plan editor.
- **D98** one `Save entry` on Recovery; futsal optional; `Saved` replaces the
  snackbar. Recovery entry.
- **D101** the futsal insight card is not built (its claim is false against the
  policy). Recovery history.
- **D109** Est. 1RM uses Brzycki with a 12-rep ceiling (sets over 12 reps give no
  estimate). Progress -> Est. 1RM.
- **D110** Progress window (last up to 12 valid sessions with a value), chips,
  delta and the empty-state copy "Not enough sessions yet. A trend needs at
  least two." Progress.

**Other register rows flagged by their checkpoints** (not in your minimum list,
still waiting): **D38** (`1b` plan detail and `1c` exercise detail not built),
**D76** (plan cards have no "last used" line), **D85** (plan editor `Save` bar
and the version note instead of "Save as version N"), **D103** (Backup screen
retired, its rows inline in Settings, CSV row kept). Reach each on the screen
named in its row.

**Platform behaviour: system Back**

- **System back in workout mode (plan-flagged for explicit sign-off).** On the
  board and in focus mode the system back gesture/button opens the leave sheet
  instead of leaving. [AVD] Start a workout, press Back. Accept or reject.
- **Detail-screen system back.** In History's workout detail, the system Back
  leaves History altogether; only the bar's back arrow closes the detail
  (pre-existing, not intercepted). [AVD] History -> a workout -> system Back.

**CP16 out-of-scope observations (reported, not changed)**

- **Plan editor always asks `Discard changes?` on Back**, even with no edit, when
  editing an existing plan (edit-mode `isDirty` is true once loaded; unchanged
  since Milestone 2). [AVD] Plans -> Open a plan -> Back.
- **Rest-end alarm can be ~2 minutes late on a fresh install** (inexact
  `setAndAllowWhileIdle` unless exact alarms are allowed; Milestone 4's
  scheduler). [AVD or PHONE] fresh install, log a set, leave the app, time the
  notification.
- **Double top-padding check.** Settings and Progress apply the scaffold padding
  outside their scroll (fixed in CP16). Look for any other screen with a double
  gap under the top bar or content scrolling under it, especially Library, Plans,
  History and Recovery. [BOTH]

---

### Expected result

Every step above behaves as described; every difference from the design is a
register row (section "For the user's judgement" or the inventory); nothing
crashes; data written by one step is visible in the others (a finished workout
appears in History, Progress and Home's last workout).

### Known limitations and deferred items (not new findings)

- **Deferred by you to a later remediation child** (`docs/improvements/IMPROVEMENT_ROADMAP.md`
  section 9.8): `5c`/`5d` Settings (Theme System/Light/Dark, app-level Default
  rest, Extra set fields, `Archived exercises and plans`, a dedicated Backup and
  restore screen with metadata); `5b` Progress (exercise picker sheet, line chart,
  `3m`/`6m`/`All`, `Sessions`/`Avg RPE` tiles, `Training frequency`, `Records`);
  **D60** set-entry carry-over (weight and reps staying in place after `Log set`,
  `Last time: 82.5 kg x 8`, which also delivers "Previous performance").
- **The five deferred capabilities** (IMPROVEMENT_ROADMAP section 9): 9.1
  supersets, 9.2 exercise swap / substitute (and skip), 9.3 per-exercise and
  per-session notes, 9.4 an active plan and multi-day plans, 9.5 kg / lb
  switching. Also 9.6 other no-domain design surfaces and 9.7 `docs/UX_FLOWS.md`
  rows kept as declared intent (pre-start preview, previous performance, suggested
  load figure, reuse the previous set, recovery or pain warnings, workout notes).
- **Not built, by register row:** the readiness chips on set entry and decision
  list (`D29`), no session note on the done screen (`D3`), no set-edit pencil
  on History detail (`D7`), no rename of an ad-hoc workout (`D30`), no `Units`
  group (`D5`), no override-streak line (`D33`), a per-set delete (`D66`).
- **The physical phone** has real data and is not used by the command that
  wrote this checklist.

### Functional review round 1 — outcome (2026-10-02)

Round 1 returned **FAIL**: F1 itself is met (the screens follow the Claude
Design layouts apart from registered deviations; nothing crashed; data shows
everywhere), but the user decided every item below. `/apply-functional-review`
classified each finding and took two branches.

**Bounded branch — fixed in this item** (group A; technical approval marked
`STALE` first, in `7b35f12`, before any source edit). Every fix has its own
tests; all 16 are one commit each except A1–A3, which share one because they
share test files:

| Finding | Class | Disposition | Commit |
|---|---|---|---|
| A1 plan picker leaves a blank row | defect | dismissing the picker, or leaving for Create a new exercise, removes the blank row | `ebc7498` |
| A2 `Skip rest` wraps at 384dp | defect | Skip takes 1.7x the width of the two nudges; a test asserts one line at 384dp | `ebc7498` |
| A3 `1 steps` / `2 warm-up` | defect | singular/plural for the step caption and the warm-up count | `ebc7498` |
| A4 errors before input | usability | the name error shows only once the field is touched (both editors) | `32061c0` |
| A5 no thousands separator on Progress | defect | `plainNumber`/`signedNumber` group by locale | `8344b8f` |
| A6 recovery starts at 2 | defect (data quality) | scales start unset; Save needs all six; no schema change (`D113`) | `29026eb` |
| J1 readiness copy and dots (D42) | usability | reviewed wording; dots fill to the `n/5` value | `af3ed7d` |
| J2 recommendation copy (D52) | usability | reviewed wording; reason lower-cased after the dash; no done-screen suggestion for an exercise with no working set | `27cc1eb` |
| J3 Create a new exercise (D56) | usability | the new exercise is added to the running workout on Save | `1ab0e6a` |
| J4 Versus last time (D67) | usability | same load compares reps: `same load, +2 reps` as a gain | `cec87b3` |
| J5 Invalidate copy (D93) | usability | reviewed wording | `c3e279b` |
| J6 Est. 1RM above 12 reps (D109) | usability | "Est. 1RM needs a set of 12 reps or fewer."; formula and ceiling unchanged | `c553c34` |
| J7 History detail system Back | defect | `BackHandler` closes the detail | `0f9049b` |
| J8 Discard changes on an unchanged plan | defect | dirty only when the draft differs from the loaded plan | `507b712` |
| J9 exact rest alerts | missing requirement | one-time explanation and system-screen hand-off, inexact fallback kept (`D114`) | `4d645cb` |
| dash nit (D105) | enhancement, trivial | `-` becomes `—` in the Erase dialog | `eb07ac1` |

Judgement calls made while fixing, for the reviewer: J2's "once per exercise
that needs it" is read as "no suggestion row for an exercise with no working
set in that workout" (an exercise with one working set still shows its `Not
enough data yet`); A6 makes Save wait for all six scales rather than saving a
partial entry, because `RecoveryEntry` requires all six; J9's explanation is
shown once, with no Settings row (a Settings entry point is left to the B1
Settings work).

**Broad branch — deferred, group B:** B1–B6 (Settings 5c/5d, Progress 5b, set
entry keeping its values, the three empty-state/search-field/ordering
defects) go to the new child
**`repflow-redesign-visual-foundation-remediation-1-remediation-1`**
(`create_remediation_child_work_item` with this item as parent, so the id is a
child of this item; registered in `7b35f12`, phase `PLANNING`, base commit
`389608e`). Its next action is `/milestone-plan
repflow-redesign-visual-foundation-remediation-1-remediation-1`. This item
cannot reach `MILESTONE_COMPLETE` until that child does, and the parent
(`repflow-redesign-visual-foundation`) not until this item does.

**Accepted as built, no change:** D47, D55, D57, D73, D76, D77, D82, D85, D98,
D101, D105 (apart from the dash), D110, D38, D103, the workout-mode system Back
behaviour, and the double top-padding check. **Not graded:** the five
observations in the review.

**Re-test after the fix round** (the AVD, then the phone for the first four):
the rest strip's `Skip rest` on a real rest; Home -> Log recovery on a day
with nothing logged (all six unset, Save disabled until each is chosen); the
readiness sheet's wording and dots; History -> a workout -> system Back;
Plans -> Open -> Back with no edit; Plans -> Add exercise -> back out and
Create a new exercise; workout -> Add exercise -> Create a new exercise ->
Save; Progress -> a high-rep exercise -> Est. 1RM, and the grouped figures;
the done screen with a same-load rep gain; the Invalidate dialog. J9 needs
the phone: a first rest on a build without `Alarms & reminders` shows the
explanation once.

**Next action.** Technical approval went `STALE`, so the item re-enters
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` with a regenerated post-fix bundle:
review it, then `/apply-implementation-review
repflow-redesign-visual-foundation-remediation-1` (or `/approve-review
implementation repflow-redesign-visual-foundation-remediation-1` on a clean
APPROVE), then `/prepare-functional-review
repflow-redesign-visual-foundation-remediation-1` for round 2. The group-B
child is planned separately, naming its own id on every command.

#### Implementation review of revision 3 - dispositions (2026-10-02)

`/review-implementation` on revision 3: REVISE, 0 Blocking, 2 Important, 5 Optional.

| Finding | Disposition | Commit |
|---|---|---|
| I1 resume re-arms a finished rest (`D114`) | fixed: `shouldRearmRestAlarm` re-arms only while `endAt` is in the future; JVM test | `5fa3456` |
| I2 exact-alarm explanation never shown after a notification denial | fixed: `isNotificationAskPending` waits only until the request is answered; JVM tests include the denied case | `5fa3456` |
| O1 no test for J3's id hand-back | fixed: `ReturnCreatedExerciseTest` (workout hands the id back, plan editor is a plain pop) | `0f2c812` |
| O2 two `UseKtx` lint warnings | fixed in `RestTimerAlarmScheduler.kt`; lint 23 -> 21 warnings | `5fa3456` |
| O3 prompt only offered once | not changed: within the recorded J9 judgement call; left to the B1 Settings work | - |
| O4 font scale not covered for `Skip rest` | not changed: design-row level, no defect reproduced | - |
| O5 leftover formatting | not changed: formatting only, spotless-clean | - |

### Reporting

Put findings in
`.ai-review/repflow-redesign-visual-foundation-remediation-1/feedback/FUNCTIONAL_REVIEW.md`,
one per item, naming the step id (for example `C10`) or the register row, the
device, the theme and what you saw. If testing is clean, `/accept-milestone
repflow-redesign-visual-foundation-remediation-1` is the only acceptance command
and it needs every checkpoint in this item's registry to be `COMPLETE` (all
sixteen are). A checkpoint still outstanding goes back to `/milestone-implement`.
No command records acceptance of a partial round. Findings go to
`/apply-functional-review repflow-redesign-visual-foundation-remediation-1`,
which routes each to its bounded branch (same-scope fix) or its broad branch (a
`repflow-redesign-visual-foundation-remediation-1-remediation-<n>` child).

---

## Parent milestone

The sections below are the parent's own record, unchanged except for the
superseded next-action note.

## Milestone

**`repflow-redesign-visual-foundation`** — the bounded visual-foundation
milestone that establishes the production Compose design system (colour,
type, shape and spacing tokens; a local icon set; a small primitive set)
and applies it to three representative surfaces to prove it. Governed by
Workflow v2.1; plan approved at revision 19.

Roadmap milestones 0-8 remain complete. Milestone 8's closed record is
preserved verbatim under "Milestone 8 — accepted and closed" below.

## Goal

Establish the production Compose design foundation derived from the
RepFlow Claude Design / Nocturne target, then apply it to the app shell
(bottom nav), the Exercise list, and the Active Workout set-entry + rest
timer — enough surface to prove the system without becoming a whole-app
visual rewrite. No navigation/IA change, no domain or application-layer
change, no schema change, no new runtime dependency.

## Current checkpoint

**CP7 — Verification, dark/light check, Open-decision + status doc updates:
complete.** CP1–CP6 remain complete below. **All seven checkpoints are done.**

- **Full local suite, every task force-executed** (`--rerun-tasks`, so nothing
  was reported green off a stale up-to-date marker):
  `spotlessCheck`, `detekt`, `lintDebug`, `testDebugUnitTest`,
  `assembleDebug`, `assembleDebugAndroidTest` — `BUILD SUCCESSFUL`,
  95/95 actionable tasks executed. **427 JVM unit tests, 0 failures, 0
  errors, 0 skipped** across 76 suites. `LayerBoundaryTest` (6 tests) passes
  as a matter of record, per the plan's domain-purity invariant.
- **`connectedDebugAndroidTest` ran for real this time** — the gate CP1–CP6
  each had to defer for want of a device. Headless AVD
  `RepFlow_S24_Ultra_API_37` (Android 17, API 37, x86_64):
  **155 instrumented tests, 0 failures, 0 skipped**, `BUILD SUCCESSFUL` in
  2m05s. That includes every Compose UI test over a reskinned surface —
  `ExerciseListScreenTest`, `ActiveWorkoutScreenTest`,
  `MainActivityNavHostSmokeTest` — plus the migration and DAO suites.
- **Verification greps, re-run this session rather than re-cited** (CP1's own
  self-review flag, and CP3's reason for pinning the three sanctioned radii):
  zero `Color(...)` literals anywhere under `presentation/` outside
  `designsystem/`, so the "the cascade reaches every screen" assumption holds
  as stated; zero literal `RoundedCornerShape(...)` radii outside
  `designsystem/` — the one shape token used at a call site is
  `ActiveWorkoutExerciseCard.kt`'s `CircleShape` set marker, which is a shape,
  not a radius literal.
- **Manual dark/light pass, driven on the same emulator** (17 screenshots
  under `.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/`,
  gitignored alongside the bundle). Every surface CP4–CP6 touched was
  actually exercised, not type-checked: bottom nav in both themes, the
  Exercise list empty state and a populated row, the Active Workout set-entry
  card (`WEIGHT_AND_REPS` branch), a recorded set, the live rest strip, and
  the collapsible detail disclosure expanded. What it confirmed:
  - Bottom nav reads correctly on both themes — CP2's glyphs, the selected
    accent pill, and unselected labels that stay legible (the corrected `.66`
    light alpha is visibly grey-on-light, not washed out).
  - The `surface`/`background` split is visible on the top app bar and the
    Exercise list rows in both themes, as round 9's I1(b) and round 10's I2
    disclosed. It reads as a deliberate band, not as a rendering artefact.
  - Light `control` — the plan's flagged judgment call — has two live
    renders here: the rest-timer progress track and the set marker's fill.
    Both read as an intentional neutral step against the card, so the
    placeholder value looks defensible; still the reviewer's call.
  - Dark `DropdownMenu` (Start-workout menu) does read as a *recessed* panel
    rather than an elevated one, exactly the disclosed
    `surfaceContainer`-darker-than-`surface` inversion. Light's menu
    (Exercise list kebab) is delimited only by its shadow, also as disclosed —
    over `background` it still reads as a floating panel.
  - `error` at the corrected light/dark values is legible in its live context
    (the exercise editor's `isError` "Name is required." message).
  - The untouched `FilterChip` selected state (exercise editor tracking-type
    row) still renders Material 3's own pair, confirming round 15's B2
    scoping actually took effect.
- **Two items CP6 left open are unchanged and go to the reviewer as-is**: the
  unconsumed `RepFlowStepper`, and CP2's four unconfirmed nav-glyph judgment
  calls. Neither is a defect; both are the "areas the reviewer should
  specifically challenge" list doing its job.
- `docs/TECHNICAL_DECISIONS.md`: the "Exact UI design system and visual
  identity" row moves from `Open` to resolved, pointing at a new
  "UI design system and visual identity" section that names this milestone
  and the concrete token set under `presentation/designsystem/`.
  **"Navigation structure" is untouched**, still
  `Open (D-1 approved for M1 only)` — this milestone does not advance it.

### CP7 addendum — device-coverage correction and revalidation (2026-09-06)

The CP7 bullets above are left as written; this addendum corrects and
extends them rather than rewriting them.

**Correction to the manual-pass claim above.** The original CP7 manual pass
ran on **one** AVD, `RepFlow_S24_Ultra_API_37`, whose hardware profile is
actually `pixel_9_pro_xl`: 1344×2992 px @ 480 dpi = **448 dp wide**. That is
the roomiest common phone width, so the pass above overstates its coverage —
it establishes that the surfaces render correctly at 448 dp, not that they
render correctly at the widths real users have. No claim above is false at
448 dp; the gap is that only 448 dp was exercised.

**360 dp follow-up.** Forcing that AVD to 360 dp (`wm size 1080x2400`,
density 480) exposed two narrow-width issues, both confirmed **pre-existing**
by reading the pre-checkpoint sources rather than assumed: bottom-navigation
label truncation (`maxLines = 1` + `TextOverflow.Ellipsis` were already in
`RepFlowBottomNavigationBar.kt` before CP4, `git show 83c4132`), and the
Active Workout "Load (kg, optional)" field wrapping to two lines while the
adjacent "Reps" field does not — the two fields have shared a `Row` with
`Modifier.weight(1f)` since before CP6 (`git show 5e0870d`). Its **3**
dark-theme screenshots are under
`.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/narrow-360dp/`
(gitignored). That count is stated here from review round 4 onward: the probe
was always described, but its shots were never counted in the bundle's own
evidence table, which now enumerates four passes and **108** PNGs in total.

**Revalidation at the real target width.** Both findings were then re-checked
on the **physical Samsung SM-S928B** (`RFCXA0RLSVT`, Android 16 / API 36,
1080×2340 @ 450 dpi, `sw384dp w384dp h832dp`, 3-button nav, 135 px bottom
inset) and on a matching **384 dp AVD** (`RepFlow_S24Ultra_384dp_API36`,
`emulator-5554`, same reported configuration). Geometry was confirmed
comparable from `am get-config`, `wm size`, `wm density` and the
`navigationBars` inset on both before any comparison was drawn. The same
debug APK was installed on both. **The physical device is authoritative
wherever the two disagree.** Full device metadata, the evidence index, and
**39** screenshots (both themes, both devices) are under
`.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/revalidation/`,
alongside `DEVICE_METADATA.md`.

Results at 384 dp:

- **Bottom-nav truncation does *not* reproduce.** All six labels render in
  full on both devices, in both themes. The labels do sit flush against the
  screen edges with no horizontal breathing room, so the margin before
  truncation is thin — but nothing is clipped at the target width.
  Width-specific, pre-existing, **Optional**.
- **The Load/Reps height mismatch *does* reproduce**, on the physical device
  and the AVD, in both themes: "Load (kg, optional)" wraps to two lines and
  its field is visibly taller than "Reps" beside it. Pre-existing (the layout
  predates CP6), reproduces at the real target width, and sits on a surface
  this milestone restyled. **Important, not Blocking** — and not attributable
  to this milestone.
  - For contrast, the expanded Set-details pair ("Pain during this set…" /
    "Technique quality…") *both* wrap to three lines, so that row stays
    visually matched. The Load/Reps row is the asymmetric case.
- Everything else in the original list re-checked clean on both devices in
  both themes: bottom nav, Exercise list empty and populated, the set-entry
  card, recorded-set presentation with the circular set marker, the live rest
  strip over its neutral `control` track, the expanded detail disclosure, top
  app bars, the `surface`/`background` list-row band, and both `DropdownMenu`
  sites (dark still reads as the disclosed recessed panel; light is still
  delimited only by its shadow).
- AVD-vs-physical differences observed were confined to system UI — One UI's
  status bar, navigation glyphs and permission-dialog styling versus AOSP's.
  Per the validation rule these are not product defects; the physical device
  was correct in every case.

### CP7 addendum 2 — the enumerated regression checks, completed (2026-09-06)

Implementation review round 1 (`LOCAL_MODEL_IMPLEMENTATION_REVIEW`, finding
I1) was right that the two records above discharge only part of CP7 step 1.
CP7 step 1 does not ask for "a manual dark/light pass" in the abstract; it
enumerates, screen by screen and with the plan round that added each one, a
specific list of regression checks. **Those checks have now been run.** Nothing
above is retracted — this addendum completes it.

**How.** The app was seeded from empty through its own UI on the physical
**Samsung SM-S928B** (`RFCXA0RLSVT`, Android 16 / API 36, `sw384dp`, the
authoritative device): three exercises covering all three tracking types
(`Bench Press` WEIGHT_AND_REPS, `Pull Up` REPS_ONLY, `Plank` DURATION), a
two-row training plan (`Push Day`: a reps-tracked row and a duration-tracked
row), two completed workout sessions, and a recovery entry plus a futsal
session. That state was then exported and **restored on the 384 dp AVD**
(`RepFlow_S24Ultra_384dp_API36`) through the app's own backup/restore, which
also exercised the restore confirmation end to end. **49** screenshots are
under
`.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/round2-enumerated/`
(43 `phys/`, 6 `avd/`; gitignored). Themes were switched with
`adb shell cmd uimode night yes|no`.

**Results, by the check CP7 step 1 names.**

- **The four `TextButton`-based dialogs and the date picker's "today" marker**
  (round 10's B1, upgrading round 5's I1 from a trade-off to a regression
  check). All four exercised, both themes: the `DatePickerDialog`
  (`HistoryScreen.kt:361`) and its `Clear`/`Cancel`/`OK` `TextButton`s; the
  Recovery date picker (`RecoveryFutsalScreen.kt:207`, dark — see the gap
  note below); the invalidate-session `AlertDialog` (`HistoryScreen.kt:176`);
  and the restore `AlertDialog` (`BackupScreen.kt:62`). **All render legibly
  on the now-lighter dark dialog container**; every dialog label is `primary`
  on `surfaceContainerHigh` and reads comfortably. The **"today" marker
  (`HistoryScreen.kt:388`) was checked on dark with today deliberately *not*
  selected** (a later date was chosen first), which is the only way the
  outlined-today treatment is visible rather than subsumed by the selection
  fill: it renders as a `primary` ring with a `primary` numeral, clearly
  legible. **Retiring the "raised tier" (dark `surfaceContainerHigh` now
  equals dark `surface`) does not read as a regression** — a dialog is always
  drawn over its own scrim, so it separates from the page regardless.
- **`TrainingPlanEditorFormFields.kt:123-165`'s bare `Card` on dark**
  (round 15's B1 — one of the two the reviewer named as minimum). Exercised on
  both devices, both themes, with both a first row (move-up disabled) and a
  second row (move-up enabled) so the enabled/disabled `TextButton` pair is
  visible at once. **The reassigned `surfaceContainerHighest` does not read as
  a regression against the `background` it sits on**: at the disclosed 1.16:1
  dark / 1.05:1 light the card is a narrow but genuinely perceptible panel,
  its edge traceable along its full height, and two stacked cards stay
  separable from each other. `primary` on that card (the "Pull Up"/"Plank"
  titles, at the fixed 4.71:1) is comfortable. The `TextButton`s at
  `:228`/`:258`/`:263`/`:268` all read as `primary`, and the disabled
  move-up is unmistakably dimmer than the enabled move-down. The `Checkbox`
  at `:161` was exercised **both** unchecked (outline square, clearly visible
  on the card) and checked (solid `primary` fill with its check glyph).
- **`TargetRangeFields`' duration-tracked vs reps-tracked rows** (round 16's
  I6). Both branches rendered **in the same editor at the same time** — row 1
  `Min reps`/`Max reps`, row 2 `Min duration (s)`/`Max duration (s)` — so all
  seven focused `OutlinedTextField` labels this card can render were reached.
  Both themes.
- **`RecoveryFutsalScreen.kt:255`'s `Switch` on dark** (round 16's I2).
  Exercised. **The off state still reads as off, not as disabled.** At the
  reassigned 1.16:1 the unchecked track is close to the page ground, but the
  `Outline`-coloured boundary (4.80:1 against the track) draws a crisp ring
  that keeps the control's extent legible, and the thumb sits clearly to the
  left in a distinctly lighter grey. Same verdict for the second stock
  `Switch` site, `ActiveWorkoutExerciseCard.kt:129`'s warm-up toggle, on both
  themes.
- **`RecoveryFutsalScreen.kt:93`'s "View history" `TopAppBar` `TextButton`**
  (round 16's B1). Exercised on both themes. It renders `primary` on the
  bar's `surface` fill and **reads consistently with the row card's other
  `TextButton`s** — same colour, same weight, no sense that the bar action is
  a different tier.
- **`TrainingPlanListScreen.kt:98`'s untouched `FloatingActionButton`
  alongside CP5's restyled one** (round 6's I1). Both exercised, both themes.
  The divergence is real and is **more pronounced on light**: the Exercise
  list FAB is a solid accent fill with a white glyph, the Plans FAB is a pale
  `primaryContainer` tint with a small purple glyph. On dark they converge
  somewhat (both read as purple squares) but still differ in fill lightness
  and glyph colour. They never appear side by side, only one tab apart.
  **Verdict: an acceptable disclosed limitation for a milestone that reskins
  three surfaces on purpose, and the most visible touched-vs-untouched
  inconsistency in the app** — the next application milestone should close it.
- **The five untouched stock `ListItem` rows** (round 9's I1(b)) — all five:
  `HistoryScreen.kt:143` (two rows), `HistoryDetailScreen.kt:49`,
  `RecoveryHistoryScreen.kt:87` and `:104` (both populated for this pass), and
  `TrainingPlanListScreen.kt:164`. Both themes. **The newly-visible
  `surface`/`background` band reads as intended** — a deliberate row band with
  a hairline divider, not a rendering artefact — on both themes, and it is the
  same band the reskinned Exercise list already shows.
- **All ten top app bars** (round 10's I2). Exercises, Workout, Plans,
  Recovery, Recovery history, History, History detail, Backup, New/Edit plan,
  New/Edit exercise — all seen across this pass, both themes. Same verdict as
  the `ListItem` band: an intended, consistent band.
- **The light selected `FilterChip`** (round 16's I2 / round 10's I2) on
  `HistoryScreen.kt:245` and `TrainingPlanListScreen.kt:108,114`. Exercised.
  On `TrainingPlanListScreen` the selected/unselected distinction is carried
  by **two** signals, not the fill alone — selected has a fill and no border,
  unselected has a border and no fill — so the 1.05:1 fill-vs-ground
  narrowing is not the only cue. On `HistoryScreen` the chip is the only one
  in its row, so there is nothing to confuse it with. In both cases the fill
  is a pale *pink*-lilac against a cool blue-lilac ground, so it separates by
  hue as well as by luminance and reads more clearly than 1.05:1 alone
  suggests. **Thin but legible; no regression** — and it is the thinnest of
  all the checks in this list.
- **`ActiveWorkoutExerciseCard`'s `REPS_ONLY` and `DURATION` branches**
  (round 14's I1 — the second of the two the reviewer named as minimum). Both
  exercised on both devices and both themes, with the set-detail disclosure
  expanded so the RPE/pain/technique fields render too. `REPS_ONLY` shows only
  `Reps`; `DURATION` shows only `Duration (s)`; the warm-up `Switch` and all
  the `OutlinedTextField` resting borders render at Material 3's own unchanged
  default and are legible on the card in both themes. Combined with the
  `WEIGHT_AND_REPS` branch already recorded above, **all seven
  `OutlinedTextField` call sites and the `Switch` have now been seen.**
- **Five more `DropdownMenu` sites**, beyond the two already recorded:
  `ExerciseListScreen.kt:249` (kebab), `ActiveWorkoutScreen.kt:128`
  (start-workout), `TrainingPlanEditorFormFields.kt:233` (exercise picker),
  `HistoryScreen.kt:263` and `:299`. **All seven are now exercised.** One
  nuance worth recording: the disclosed `surfaceContainer` "recessed panel"
  reading on dark only appears where the menu overlays `surface` (a card or
  app bar). Over `background` — which is where `HistoryScreen`'s two menus
  open — `surfaceContainer` is *lighter* than the ground and the menu reads as
  elevated, exactly as expected. Both readings follow from the disclosed role
  values; neither is a defect.

**Two gaps, stated rather than implied.**

1. `RecoveryFutsalScreen.kt:207`'s date picker was exercised on **dark only**.
   It is the same Material 3 `DatePickerDialog` composable reading the same
   roles as `HistoryScreen.kt:361`'s, which *was* exercised on both themes, so
   the light-theme rendering is covered by an identical instance rather than
   by this exact call site. Carried to the functional-review checklist.
2. Light-theme `HistoryDetailScreen.kt:49` was captured after the dark run
   rather than alongside the rest of the light pass; it is included and clean,
   but the two device screenshots for it are from different sessions of this
   same pass.

**One observation, not a milestone defect.** On the History detail screen a
DURATION set renders as `Set 0:  kg x ` with empty values above its correct
`Duration: 45s` line. `HistoryDetailScreen.kt` is **not** in this milestone's
diff (`git diff b39af90..HEAD -- app/.../presentation/` lists 18 files; that
file is not one of them), so this is pre-existing and outside scope. Recorded
here so it reaches the functional review rather than being lost.

### Implementation review round 5 — what was applied (2026-09-07)

`LOCAL_MODEL_IMPLEMENTATION_REVIEW`, status `REVISE`, **0 Blocking, 1
Important, 1 Optional**, against bundle `e1062e8e…` / `review_content_id`
`d169c9bb…` (implementation revision 5). Nothing above is retracted. **Both
findings were reproduced against the actual code before anything was changed,
and both were accepted — nothing is rejected this round.** Unlike rounds 3 and
4, this round's finding is *not* about a grep counting its own prose: it lands
on `ROLE_AUDIT.md`'s substance, in a file that ships in the source tree and is
the document that designates itself the procedure to re-run before any token
change.

Exactly one commit carries content and it changes one markdown file:
`git diff 76f6257..HEAD -- app/` is `…/designsystem/ROLE_AUDIT.md` and nothing
else, and `git diff 76f6257..HEAD -- '*.kt'` is empty.

- **I1 — the audit's stock-consumer inventory was still the pre-milestone one
  where this milestone had moved it, and the right number was already in the
  same bundle.** The `onSurfaceVariant` row claimed "**6** `ListItem`
  supporting lines". Reproduced by counting rather than by reading: `ListItem(`
  composable call sites are **6** at `b39af90` and **5** at HEAD, and each of
  the five carries exactly one `supportingContent` holding one `Text`
  (`HistoryScreen.kt:143`, `HistoryDetailScreen.kt:49`,
  `RecoveryHistoryScreen.kt:87` and `:104`, `TrainingPlanListScreen.kt:164`).
  The sixth was `ExerciseListScreen.kt:218`, which CP5 replaced with
  `RepFlowCard` + `ExerciseRow` — **so the line did not disappear, it moved
  from the stock bucket to the direct bucket**, and only its arrival at `:355`
  was recorded. `docs/ACTIVE_MILESTONE.md`'s CP7 addendum 2 above already
  enumerates exactly those five. Corrected, with the five sites named in the
  audit so the figure is checkable rather than trusted.
  - **The audited-imports list was byte-for-byte the `b39af90` component set**,
    so a maintainer re-running the method the file states produced a different
    input list than the one recorded. Both components this milestone newly
    imports are now in it and both are audited from `material3-android-1.4.0`'s
    own token files rather than waved through: **`LinearProgressIndicator`**
    would default to `ProgressIndicatorTokens.ActiveIndicatorColor` = `Primary`
    and `TrackColor` = **`SecondaryContainer`** — the second being a role CP4
    deliberately leaves at the Material 3 baseline — and reaches **neither**,
    because `color`, `trackColor` and `drawStopIndicator` are all supplied at
    its only call site (`ActiveWorkoutScreen.kt:293,300,301,304`);
    **`Icon`** reads no `ColorScheme` role at all (its `tint` defaults to
    `LocalContentColor`, confirmed in `IconKt`'s bytecode), and every container
    feeding its seven untinted call sites is already in the audit. The **16**
    non-component `material3` imports are listed too, so the cross-reference
    now reproduces the exact **42**-name input set this table was built from —
    26 components + 16 non-components, verified to match the tree with no
    overlap and no omission.
  - **A third stale figure in the same cell, found by re-deriving all of it
    rather than only the flagged half, and fixed here.** "5 `OutlinedButton`
    labels" is still **5**, but `grep -c` now returns **7** call sites. The two
    new ones are CP3's own primitives (`RepFlowButtons.kt:145`, `:170`) and
    **neither is a consumer** — both pass
    `ButtonDefaults.outlinedButtonColors(contentColor = …)` explicitly, so
    `OutlinedButtonTokens.LabelTextColor` is never reached. Recorded with the
    five stock sites named, because a maintainer re-running the grep gets 7 and
    would otherwise read the audit as stale. ("4 dialog bodies" was re-derived
    too and is right: exactly four `AlertDialog(` sites, each with a `text =`
    slot, at both commits.)
- **O1 — accepted rather than rejected, and re-deriving it found more than the
  finding claimed.** The counting basis is now stated once at the top of the
  file. But "**23** `OutlinedTextField` labels" needed more than a basis line:
  of the 23 `onSurfaceVariant` text slots, **22 are labels and one is the
  Exercise-list search field's placeholder** (`ExerciseListScreen.kt:211`),
  which carries no `label` at all — at HEAD *and* at `b39af90`. It is a
  consumer through `InputPlaceholderColor`, which is the same role as
  `LabelColor` (read out of `OutlinedTextFieldTokens`, not assumed). So 23 is
  the right total under the reading that matters and was never right under the
  word "labels". Call sites are **17** at HEAD against **23** at `b39af90`
  because CP6 collapsed `ActiveWorkoutExerciseCard.kt`'s seven inline blocks
  into one `NumericEntryField` (`:468`) invoked seven times.

**No colour, ratio, override or test changed**, which is what the finding's own
acceptance criterion 1 asks for; the load-bearing **14** direct reads outside
`designsystem/` are unmoved at this HEAD. The file's own prose greps do move,
as the file says they must: the unscoped wildcard count over `presentation/`
goes 32 → **33** and the literal-dot count 30 → **31**, because this round's
edit adds one prose mention. `--include='*.kt'` is unmoved at **29** — a `.md`
edit cannot reach it.

**Device evidence for this round, stated plainly.** The physical **Samsung
SM-S928B is still not attached** — `adb devices` lists only `emulator-5554`,
the 384 dp AVD `RepFlow_S24Ultra_384dp_API36`. The full gate
(`spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug
assembleDebugAndroidTest connectedDebugAndroidTest --rerun-tasks`) is
`BUILD SUCCESSFUL`, **96/96 tasks executed**, **428 JVM tests / 76 suites / 0
failures** and **162 instrumented tests on the AVD, 0 failures, 0 skipped**.
**No physical run is claimed for this round.** Round 4's rule — a change under
`app/` does not inherit the documentation-only exemption — is engaged again and
earned on the file again: `ROLE_AUDIT.md` is neither a Kotlin compile input nor
a packaged resource, and `unzip -l` over the debug APK that run built returns
**zero** `ROLE_AUDIT` entries and exactly one `.md` entry
(`META-INF/NOTICE.md`, from a dependency). Every byte that reaches a device is
byte-identical to revision 5, and to revision 4 before it. If technical
acceptance wants a physical pass over the shipped code, the moment for it is
`/approve-review implementation` with the phone attached.

**Round 5's acceptance criterion 4 is respected**: rounds 1–4's dispositions are
untouched — the stepper deviation (`IMPROVEMENT_ROADMAP.md` §8.1) is not
re-litigated, the unconsumed foundation ships, the `"Archived"` string and
`MainActivityNavHostSmokeTest` rejections hold, the Load/Reps deferral stands,
round 4's measured rejection of the first-row chip assertion stands, and the
four unconfirmed nav glyphs and light `control` remain the reviewer's judgment
calls.

### Implementation review round 4 — what was applied (2026-09-07)

`LOCAL_MODEL_IMPLEMENTATION_REVIEW`, status `REVISE`, **0 Blocking, 2
Important, 1 Optional**, against bundle `de81985b…` / `review_content_id`
`4d83e3b5…` (implementation revision 4). No finding is a code defect; all
three are wrong figures in round 4's own evidence documents, and the round's
own summary says so. Nothing above is retracted. **All three were reproduced
before anything was changed, and all three were accepted — nothing is rejected
this round.**

Only one commit carries content, and it changes one markdown file:
`git diff 817cbd1..HEAD -- app/` is `…/designsystem/ROLE_AUDIT.md` and nothing
else, and `git diff 817cbd1..HEAD -- '*.kt'` is empty. I1 and I2 are
corrections to the bundle's own author-written documents, which live under the
gitignored `.ai-review/` and produce no commit at all.

- **I1 — the "check this statically" grep was itself off by one, in exactly
  the way its own sentence warned about.** `TEST_RESULTS.md` §5 claimed
  `grep -rn "MaterialTheme(" app/src/main` "returns exactly two hits" while
  congratulating itself on naming the one documentation hit it had caught.
  Reproduced: it returns **three**. The missed one is `ROLE_AUDIT.md:50` —
  prose added by round 3's own I1 fix, in a `.md` file that lives under
  `app/src/main/kotlin/…/designsystem/`, which an unscoped `grep -rn` counts.
  **Fixed by anchoring the command to code rather than repointing the count**:
  `grep -rnE --include='*.kt' '^[[:space:]]*MaterialTheme\(' app/src/main`
  returns exactly **one** hit, `RepFlowTheme.kt:39`, and can match neither a
  markdown file nor a KDoc line (they begin with `*`). The conclusion is
  unaffected and was re-verified: one `MaterialTheme` call site, one
  `LocalRepFlowExtraColors` provider, **0** `@Preview` composables anywhere in
  `app/src`, and `RepFlowTheme { … }` entered only from `MainActivity.kt:17`
  and the two instrumented tests. **No manual pass and no device rerun is owed
  on account of I1.**
- **I2 — two counts had been carried rather than re-derived.**
  (1) "286 commits sit between the base and HEAD" was round 3's figure;
  `git rev-list --count b39af90..817cbd1` is **292**, and the bundle's own
  `COMMITS.txt` already had 292 lines. Both statements are now re-derived
  after this round's last commit and cross-checked against `COMMITS.txt`'s
  line count, which the generator writes at the same HEAD.
  (2) The unscoped grep's move from **30** at `543ce13` to **32** at `817cbd1`
  was attributed to "three more mentions". Diffing the match sets, the delta
  is **+2**: `ROLE_AUDIT.md:50` and `RepFlowColor.kt:153` were added, while
  `ROLE_AUDIT.md:35` was already a match at `543ce13` — round 3 rewrote that
  line, it did not create one. Corrected in all three bundle documents, and
  `TEST_RESULTS.md` §3 now states the figures as a delta between two *fixed*
  commits so neither end can drift again. **The 14 direct reads outside
  `designsystem/` are unmoved**, at both commits and at this HEAD.
- **O1 — accepted and fixed, not rejected.** `ROLE_AUDIT.md`'s paragraph
  explaining why no `presentation/`-wide total is stated enumerated its own
  hits — "this paragraph and the one above it are two of its hits" — and the
  enumeration is right under neither grep it discusses: under the literal-dot
  grep the paragraph recommends, "this paragraph" (`:50`) is **not** a hit,
  and under the wildcard grep there are **six** prose hits, not four. Fixed
  the same way round 3's I1 was: the enumeration is dropped and the paragraph
  now says why it enumerates nothing. The literal-dot advice, the
  `--include='*.kt'` caveat and the load-bearing **14** are unchanged, and the
  greps were re-run after the edit (wildcard **32**, literal dot **30**).
- **A third instance of the same class, found while checking O1 and recorded
  rather than quietly fixed.** `REVIEW_REQUEST.md`'s challenge item 1 said
  `--include='*.kt'` gives "28" with no commit anchor. That was the value at
  `543ce13`; at round 4's own HEAD it was already **29**, because round 3's O1
  fix added `RepFlowColor.kt:153`. Neither round 4 nor its review caught it.
  It is now anchored to both commits.

**Device evidence for this round, stated plainly.** The physical **Samsung
SM-S928B is not attached to this session** — `adb devices` lists only
`emulator-5554`, the 384 dp AVD `RepFlow_S24Ultra_384dp_API36`. The full gate
(`spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug
assembleDebugAndroidTest connectedDebugAndroidTest --rerun-tasks`) is
`BUILD SUCCESSFUL`, 96/96 tasks executed, **428 JVM tests / 76 suites / 0
failures** and **162 instrumented tests on the AVD, 0 failures, 0 skipped**.
**No physical run is claimed for this round.** Round 4's review set the rule
that a change under `app/` does not inherit the documentation-only exemption;
this round earns it on the file instead: `ROLE_AUDIT.md` is neither a Kotlin
compile input nor a packaged resource, and `unzip -l` over the debug APK that
run built returns **zero** `ROLE_AUDIT` entries. Every byte that reaches a
device is byte-identical to revision 4, which the phone tested at 162/0/0/0.
If technical acceptance wants a physical pass over the shipped code, the
moment for it is `/approve-review implementation` with the phone attached.

**Round 4's acceptance criterion 4 is respected**: rounds 1–3's dispositions
are untouched — the stepper deviation (`IMPROVEMENT_ROADMAP.md` §8.1) is not
re-litigated, the unconsumed foundation ships, the `"Archived"` string and
`MainActivityNavHostSmokeTest` rejections hold, the Load/Reps deferral stands,
round 4's measured rejection of the first-row chip assertion stands (its
review accepted it and withdrew the note), and the four unconfirmed nav glyphs
and light `control` remain the reviewer's judgment calls.

### Implementation review round 3 — what was applied (2026-09-06)

`LOCAL_MODEL_IMPLEMENTATION_REVIEW`, status `REVISE`, **0 Blocking, 2
Important**, against bundle `29cc074d…` / `review_content_id` `9423b43d…`
(implementation revision 3). Neither Important finding is a code defect;
both are stale numbers in evidence documents, and the round's own summary
says so. Nothing above is retracted. **Both were reproduced before anything
was changed, and both were accepted.** The one Optional finding was
accepted too.

- **I1 — the role-read total is wrong and cannot be made right where it
  stands.** Reproduced: the obvious grep over `presentation/` returns **30**,
  not `ROLE_AUDIT.md`'s 29. The reviewer's diagnosis is exactly right — the
  sentence counts itself. Four of the 30 are prose, not reads:
  `ROLE_AUDIT.md:24` and `:35` (round 2's own two sentences) plus
  `RepFlowColor.kt:76,78`'s KDoc. So the total was correct until the round-2
  edit added two mentions, and repointing it to 30 would break again on the
  next edit to its own paragraph. **Fixed by dropping the total, not
  repointing it**, keeping only the exact, load-bearing half: **14** direct
  reads outside `designsystem/`, which is what the `Consumers` column is
  checked against and which no edit to a designsystem document can move. The
  paragraph now says why no total is stated, so it does not get helpfully
  added back.
  - **One correction to the finding, on the record.** Its first fix option —
    scope the grep with `--include=*.kt` "which gives a stable 26" — is off
    by two: that scope returns **28**, because `RepFlowColor.kt`'s two KDoc
    mentions are in a `.kt` file. 26 is the count of actual reads, which no
    single grep produces. The second option was taken instead.
  - **Two further defects in the same sentence, found while reproducing it
    and fixed here.** "1 at `b39af90`" was a regex artifact: an unescaped `.`
    is a wildcard, and the one match was `MaterialTheme(colorScheme =
    colorScheme` in the old `RepFlowTheme.kt`. `git grep -F` at `b39af90`
    returns **0**, which is what the replaced premise had actually claimed.
    All 14 citations were re-verified file:line against the worktree; no
    contrast value, ratio or `Consumers` entry changed.
- **I2 — the screenshot-count correction reached one document of three.**
  Reproduced by counting the directories again rather than trusting either
  document: revalidation **39** PNGs plus `DEVICE_METADATA.md`;
  enumerated pass **49** PNGs (43 `phys/`, 6 `avd/`). Round 2 reported this
  as "counted from the directories", which was true of the bundle's
  `TEST_RESULTS.md` §5 and of nothing else, so `IMPLEMENTATION_SUMMARY.md`
  stated both 45 and 49 for one pass and both 40 and 39 for the other. All
  four remaining figures are corrected — two here, two in the bundle
  document. **The evidence is unchanged and unweakened**; what was wrong was
  a completeness claim.
- **O1 — the theme was detected two ways, and the KDoc's own scenario was the
  one where they disagreed. Accepted and fixed, not narrowed.** Every
  primitive asked `isDarkColorScheme(scheme)`, but `RepFlowTheme` resolved
  `LocalRepFlowExtraColors` from its own `isSystemInDarkTheme()` call
  *outside* `MaterialTheme` — so that local was the second system-theme read
  the KDoc says the system does not perform, and a nested light-scheme
  override under a dark system would have drawn a light `surface` fill inside
  a dark border. No live defect existed (`RepFlowTheme` is the app's only
  `MaterialTheme` call site), so nothing rendered changes. New
  `repFlowExtraColors(scheme)` resolves the pair through `isDarkColorScheme`,
  and `RepFlowTheme` provides it inside its own `MaterialTheme` from the
  scheme it just applied. `RepFlowThemeTest` gains
  `theExtrasAreSelectedByTheAppliedSchemeNotBySomeOtherSignal` (14 tests in
  that class now) so reintroducing a system-theme read fails.
  - Acceptance criterion 3 asks which behaviour is intended when a scheme
    override and the system theme disagree, and `isDarkColorScheme`'s KDoc now
    answers it: **the applied scheme wins**, and the guarantee stops at a
    *nested* `MaterialTheme` that swaps only the scheme — a composition local
    keeps the enclosing theme's `control`/`hairline` until something
    re-provides them, which `RepFlowTheme` does and a bare
    `MaterialTheme(colorScheme = …)` does not.
  - The scheme is read from the local `RepFlowTheme` hands `MaterialTheme`
    rather than back out of `MaterialTheme.colorScheme`: identical by
    construction, and it keeps `ROLE_AUDIT.md`'s direct-read inventory a list
    of role reads on the three reskinned surfaces rather than gaining a
    fifteenth entry for the theme's own plumbing.

**The one missing-test note is rejected with evidence, and the reviewer had
already said it was not worth a round.** The suggestion was to fold the
first `PlannedTargetSummary` row's two chips into
`everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth`, since only
the second row is asserted. It was attempted, not declined on paper: the two
`weight(1f, fill = false)` modifiers on that row were removed and the
extended test run against the broken build on the 384 dp AVD. **It passed** —
so the assertion would have been a tautology. Measured on device to find out
why: with `Int.MAX_VALUE` in both `PlannedTargetUi` fields (the longest
labels those two `Int`s can produce, the done-counts being 0 with no recorded
sets) the two chips measure **408 px and 313 px** inside a **1080 px** root at
384 dp — the row fits with room to spare and cannot be made to overflow
through the UiState at any supported width. The reviewer's underlying concern
is a *localized* label length, which no test can vary. The first row's guard
therefore stays unasserted deliberately, which is better than an assertion
that cannot fail. The change was reverted; `ActiveWorkoutExerciseCard.kt` and
`ActiveWorkoutScreenTest.kt` are byte-identical to round 3's bundle.

**Acceptance criterion 4 is respected**: rounds 1 and 2's dispositions are
untouched — the stepper deviation (`IMPROVEMENT_ROADMAP.md` §8.1) is not
re-litigated, the unconsumed foundation ships, the `"Archived"` string and
`MainActivityNavHostSmokeTest` rejections hold, the Load/Reps deferral stands,
and the four unconfirmed nav glyphs and light `control` remain the reviewer's
judgment calls.

### Implementation review round 2 — what was applied (2026-09-06)

`LOCAL_MODEL_IMPLEMENTATION_REVIEW`, status `REVISE`, **0 Blocking, 3
Important**, against bundle `5e6d7053…` / `review_content_id` `5fd8dd90…`
(implementation revision 2). Nothing above is retracted. Every Important
finding was reproduced against the actual code before anything was changed,
and **all three were accepted — none was rejected this round.**

- **I1 — the set-detail disclosure reached no accessibility service.**
  `SetDetailSection`'s header is a `Row` carrying `clickable(role =
  Role.Button)`, which merges its descendants; both glyphs are
  `contentDescription = null`, so the merged node read exactly
  `"Set details", button` whether the section was open or closed. The caret,
  the only signal of the state, is invisible to the semantics tree. Fixed:
  the header now carries `stateDescription`, resolved from two new strings.
  This is the codebase's first `stateDescription` — introduced for a
  user-facing defect, not for a test. `ActiveWorkoutScreenTest` gains a
  fourth disclosure test asserting the merged node's state description flips
  on tap.
- **I2 — `ROLE_AUDIT.md`'s completeness premise was false.** It claimed
  `presentation/**` held no colour literal and no direct
  `MaterialTheme.colorScheme` read, "so this table is the whole story". At
  HEAD there are **14** direct reads under `presentation/` outside
  `designsystem/` (zero anywhere under `presentation/` at `b39af90`), and
  `RepFlowColor.kt` is itself a file of literals. The sentence described the
  pre-milestone codebase. Replaced with what is true, and all 14 direct reads
  are now named in the `Consumers` column of the role they read, with the
  ground each renders on. **No contrast value changed and none is breached** —
  each was recomputed independently before the edit. (Round 3's I1 corrected
  the two figures this bullet originally carried; see "Implementation review
  round 3" below.)
- **I3 — this round's evidence documents carried the previous round's
  numbers.** All three corrected: the path-classification count (recomputed
  through `classify_path_implementation_stage`: **58 protected, 111
  excluded, 0 unclassified**); the `ActiveWorkoutScreenTest:182` citation,
  which had moved to `:252` and has since moved again to `:311` — now cited
  **by test name** (`tappingAddSetClearsTheEntryFields`) so no future test
  addition can stale it; and the self-review section, which still said "56
  protected paths" and still listed the CP7 addendum as an uncommitted
  working-tree change after `b50bc65` committed it. The two screenshot
  counts are corrected too (**49** for the enumerated pass, **39** PNGs plus
  `DEVICE_METADATA.md` for the revalidation).

**Optional and missing-test findings.** O1 (detekt's restated excludes were
an older default) and O2 (`ROLE_AUDIT.md`'s dark-only `on control` figure —
light computes to **7.03**, and both halves were already pinned by
`RepFlowPrimitivesTest`) are fixed. O3 (`RepFlowTagTone.Outline` and
`.UpNext` resolving to identical colours) is **recorded on the member rather
than reshaped**: `UpNext` has no consumer, so splitting the enum now would be
churn with no call site to validate it against. Missing tests 1 and 2 are
both closed — the chip-overflow guard and the search-clear button now have
instrumented coverage, and each was run against a deliberately broken build
first to confirm it is a regression test rather than a tautology. Missing
test 3 is **already satisfied** by the pre-existing
`ExerciseListScreenTest.filterChipClickInvokesOnFilterChanged`, which drives
`RepFlowPillPicker` end to end now that CP5 replaced the `FilterChip` row
with it; last round's deferral is dropped rather than carried.

**Three disclosed limitations became named follow-ups**, per the round's
acceptance criterion 4: the unconsumed `RepFlowStepper`, the two diverging
FABs and the light selected-pill value are now
`docs/improvements/IMPROVEMENT_ROADMAP.md` §8.1–8.3, with scope,
preconditions and acceptance criteria, instead of standing "Known
limitations". The stepper deviation itself is **not re-opened** — the round
ruled it accurately stated against the plan text that applies, its
justification checked out leg by leg, and REQ-3 is satisfied by authoring
the primitive.

**CP6 — Apply foundation to Active Workout set-entry + rest timer: complete.**
CP1–CP5 remain complete below.

- Both files the plan names, and only those two:
  `presentation/workout/ActiveWorkoutExerciseCard.kt` (the whole set-entry
  surface) and `presentation/workout/ActiveWorkoutScreen.kt` — in the latter,
  **only `RestTimerBar`**, per the plan's own narrowing. `LoadingIndicator`,
  `NoActiveSessionState`, `ActiveSessionState`, `AddExercisePicker`,
  `RecommendationRow` and `FailureState` are untouched; this file's private
  loading/failure copies stay, exactly as the plan's CP3 note says they should.
- **Zero logic change, re-checked line by line against the diff.** Every
  callback keeps its signature, its argument order and the condition that
  gates it: `onRecordSet`/`onEditLastSet` still receive the same eight
  values built from the same `toDoubleOrNull()`/`toIntOrNull()` calls,
  `clearEntryFields()` still runs immediately after both, undo/edit still
  appear only when `exercise.sets.isNotEmpty()`, and
  `ActiveWorkoutViewModel`/the use cases are not touched at all (14
  `ActiveWorkoutViewModelTest` tests still green, unchanged).
- **The rest timer's tick loop is byte-identical.** The `remember(timer.endAt)`
  seed and the `LaunchedEffect` that re-derives `remainingSeconds` from
  `timer.endAt.epochSecond - Instant.now().epochSecond` on every tick are
  copied through unedited — the preserved invariant the plan names explicitly.
  The new progress bar is a second *reading* of that same number
  (`restTimerProgress(remainingSeconds, timer.totalDurationSeconds)`), not a
  stored countdown; `totalDurationSeconds` was already on `RestTimerUi`, so no
  UI-state shape changed.
- **Rest strip**: a `RepFlowCard` carrying a 24sp tabular countdown, a
  `LinearProgressIndicator` tinted `primary` over `RepFlowColor.control` at
  `RepFlowShapes.pill`, and the controls beneath it. `-15s`/`+15s` are
  `RepFlowNeutralOutlineButton`s that keep their words *and* gain CP2's
  `minus`/`plus` — the step size is the entire content of those two buttons,
  so replacing it with a bare glyph would be a usability regression, not a
  reskin. Skip becomes the design's `ph-x` `IconButton` (48dp), with "Skip"
  preserved as its accessible name.
- **Set-entry pad**: the card is a `RepFlowCard`; the header pairs a
  `titleMedium` name with an `Outline` chip carrying CP2's `info` and the
  exercise's tracking-type label; planned targets are status chips
  (`Done`/`Pending` by whether the quota is met, warm-up carrying `fire`);
  logged sets get the design's 26dp circular marker (`fire` for a warm-up set,
  otherwise the set number in tabular figures) beside a tabular summary line;
  RPE/pain/technique move behind the design's own collapsible detail
  disclosure (`sliders` + `caretUp`/`caretDown`); Add set is
  `RepFlowPrimaryButton` and undo/edit are `RepFlowNeutralOutlineButton`s
  carrying `arrowCounterClockwise`/`pencilSimple`.
- **Input affordances are deliberately unchanged — the checkpoint's one
  significant deviation, flagged not buried.** The design draws weight/reps as
  steppers and RPE/pain/technique as horizontal pill rows; this checkpoint
  keeps all seven fields as `OutlinedTextField`s and the warm-up flag as a
  `Switch`, restyling only their chrome.

  **Restated after implementation review (LOCAL_MODEL_IMPLEMENTATION_REVIEW
  round 1, I2): the plan clause originally cited does not reach load/reps, and
  the plan text that does reach them says the opposite of what was built.**
  CP6's preserving clause
  (`docs/milestones/repflow-redesign-visual-foundation-execution.md:3097-3098`)
  enumerates only "the RPE/warm-up-flag/optional-pain/optional-technique fields
  and their existing input affordances" — load, reps and duration are not in
  that list. Meanwhile CP3 (`:2873`) introduces the stepper as the primitive
  "used by CP6's weight/reps entry" and sizes its default "since that's what
  CP6 actually consumes", and CP6's own file description (`:3084`) names "the
  load/reps/duration steppers" as part of the surface being reskinned. **So on
  load/reps the approved plan specifies the stepper and this checkpoint did not
  build it.** That is the honest shape of the deviation.

  What still justifies it, on the plan text that actually applies:
  (1) the plan's "Preserved invariants" require `ActiveWorkoutScreenTest` to
  keep passing, and its `tappingAddSetClearsTheEntryFields` does
  `performTextInput("60")` on the load field, which a stepper cannot satisfy —
  changing that assertion is changing a preserved invariant, not implementing
  one. (The citation is by test name deliberately: it was written as
  `ActiveWorkoutScreenTest:182`, and every round that adds a test to that file
  moves the line — it was `:252` after round 2's three disclosure tests and is
  `:311` after round 3's two. The test name does not move.);
  (2) load is decimal and unbounded, so a stepper needs a step size, and the
  design states none. The nearest existing candidate is **not** invented: the
  domain already carries `defaultLoadIncrementGrams` per exercise (it is
  rendered on the Exercise list today, `ExerciseListScreen.kt:439`, from
  `ExerciseListItem.defaultLoadIncrementGrams`). But it is not on
  `ActiveExerciseUi` (`ActiveWorkoutUiState.kt:77-84`), so consuming it would
  mean adding a field to a UiState, plumbing it through
  `ActiveWorkoutViewModel` and its use case — a UiState/ViewModel change, which
  this milestone's non-goals put out of scope ("no domain or application-layer
  change"; the plan's CP6 text is a reskin of an existing composable). It is
  also only a *default* per exercise and nullable, so a stepper would still
  need an invented fallback for every exercise that has none;
  (3) the plan's non-goals say set entry is "functionally identical before and
  after this milestone — only their rendering changes", and typing → tapping is
  not only rendering.

  **Consequence, stated plainly: `RepFlowStepper` now has no consumer anywhere
  in this milestone** (CP3 authored it for exactly this surface), and the pill
  picker's only consumer remains CP5's filter row. REQ-3 names the stepper and
  the scale row as deliverables in their own right, so authoring them satisfies
  that requirement independently of CP6's consumption — but the gap against the
  plan is real and belongs in front of the reviewer. A later redesign milestone
  that is allowed to change set-entry interaction, and to plumb
  `defaultLoadIncrementGrams` onto `ActiveExerciseUi`, is where the stepper and
  the pill rows land — **now a named follow-up with its own scope and
  acceptance criteria, `docs/improvements/IMPROVEMENT_ROADMAP.md` §8.1**, not a
  standing "Known limitation" (implementation review round 2, acceptance
  criterion 4).
- **Two smaller deviations.** (1) The collapsible detail section *is* adopted
  (the plan's reference names it), defaulting to collapsed, so RPE/pain/
  technique start hidden — the values still live in `ExerciseCard`'s own state,
  are still submitted when set, and `clearEntryFields` still resets them after
  every recorded set, so nothing can be carried into the next set unseen.
  (2) The planned-target chips are split across two rows rather than one
  flowing row: four chips on one line overflow a 360dp screen, and a clipped
  chip is both unreadable and invisible to `assertIsDisplayed`. `FlowRow` is
  still experimental API and was not worth adopting for this.
- **Why adopting the disclosure and declining the steppers is not two
  standards** (implementation review round 1, O1 — the reviewer is right that
  the two decisions need one rule between them, so here it is). The rule this
  checkpoint applied is: **CP6's preserving clause names four fields by name,
  and it is those four fields' *input affordance* that must not change.** The
  disclosure changes none of them: RPE, pain and technique are still three
  `OutlinedTextField`s, entered by typing, submitted identically, reset
  identically — only whether they are on screen before you ask for them
  changes, which is layout. Replacing the load field with a stepper changes
  what a field *is*: typing becomes tapping, a decimal becomes a quantised
  step, and a preserved test assertion (`performTextInput`) stops being
  satisfiable. Disclosure is chrome; affordance is not. The three fields the
  disclosure hides are also exactly the three the plan calls "optional",
  which is why the design puts them behind one, and
  `ActiveWorkoutScreenTest`'s three new disclosure tests now pin the whole
  contract (collapsed by default, expanding reveals all three, a value typed
  while expanded still reaches `onRecordSet` after collapsing).
- Of CP2's ten Active-Workout glyphs, **all ten are consumed**: `minus`,
  `plus`, `x` (rest strip), `fire`, `info`, `sliders`, `caretUp`, `caretDown`,
  `arrowCounterClockwise`, `pencilSimple` (set-entry pad).
- Pre-edit self-review flag from the plan discharged: `ActiveWorkoutScreenTest`
  (androidTest) was read first, and every node its nine tests resolve survives.
  The three field labels stay `OutlinedTextField` labels on nodes that still
  accept `performTextInput`; the logged-set summary stays **one** text node
  carrying exactly `primary + warm-up suffix + extra suffix` (which is why the
  two suffixes were *not* promoted into chips — `onNodeWithText` matches that
  concatenation exactly); the three planned-target strings and the `Add set`
  label are unchanged. The new type chip's three labels ("Weight & reps",
  "Reps only", "Duration") are all distinct from the field labels ("Load (kg,
  optional)", "Reps", "Duration (s)") under `onNodeWithText`'s exact match, so
  it cannot shadow one. `MainActivityNavHostSmokeTest` asserts nothing on this
  screen.
- **Two additions beyond the plan's "Files modified" list**, on the same
  footing as CP1–CP5's own test files: one new string
  (`workout_active_set_detail_toggle`, the disclosure's label) and
  `ActiveWorkoutScreenWiringTest` (8 tests). The device test never constructs a
  `RestTimerUi`, so the rest strip has no instrumented coverage at all — the
  wiring test is what pins `restTimerProgress`, this checkpoint's one new
  derivation, including the two cases that would otherwise ship silently
  broken: `+15s` pushing remaining past the original total (clamped to full,
  not 1.4) and a zero/negative total (reads elapsed, never divides by zero).
  It also pins `setsWithExtraFlag`'s separate warm-up/working quotas (made
  `internal` for this; it decides row *content*, not appearance) and that the
  surface's tap targets clear the design's 44dp floor.
- Verified: `testDebugUnitTest --tests "...presentation.workout.*" --tests
  "...presentation.designsystem.*"` (56 tests, 0 failures), plus
  `spotlessCheck`, `detekt`, `lintDebug` and `assembleDebugAndroidTest` all
  pass. `connectedDebugAndroidTest` was **not** run — no device/emulator in
  this session; it is CP7's own gate.

**CP5 — Apply foundation to Exercise list: complete.** CP1–CP4 remain complete
below.

- The plan's one named file, `presentation/exercise/list/ExerciseListScreen.kt`,
  is rewritten to render through CP1 tokens, CP2 glyphs and CP3 primitives.
  No `ExerciseListViewModel`/`ExerciseListUiState` change, no callback added
  or removed, no change to the message-queue snackbar wiring — every edit is
  "this composable now renders differently", never "does something
  different".
- **The four private state composables are gone**, replaced by CP3's shared
  ones: `LoadingIndicator` → `RepFlowLoadingIndicator`, `EmptyState` →
  `RepFlowEmptyState`, `FailureState(onRetry)` → `RepFlowFailureState`
  (retry callback preserved, now rendered as the accent-outline button —
  which is how the design draws exactly this affordance). This is the
  de-duplication CP3 was built for; `ActiveWorkoutScreen.kt`'s and
  `TrainingPlanListScreen.kt`'s private copies stay, per the plan's own note
  that this de-duplication does not complete within this milestone.
- **Rows are `RepFlowCard`s**: 64dp minimum height (the design's own value,
  inside its stated 56–68 range), a `titleMedium` name over a `bodySmall`
  meta line, and the kebab as an `IconButton` carrying CP2's
  `dotsThreeVertical` instead of a `TextButton` with a literal `"⋮"`. The
  meta line's content is byte-identical to before —
  `trackingTypeLabel(...) + summarySuffix(...)`, both functions untouched.
  The name gets two lines rather than one before ellipsis: the design's own
  `2c` calls out long exercise names as a case this screen handles, and 64dp
  is a minimum rather than a fixed height.
- **The filter row is CP3's `RepFlowPillPicker`, not a clickable
  `RepFlowStatusChip`** — a deliberate pick, not a convenience. The chip is
  28dp; making it the tap target would break the ≥44dp floor
  `RepFlowPrimitivesTest` already holds every interactive primitive to. The
  picker is the one CP3 pill primitive that is interactive *and* clears the
  floor, and "one primitive rather than four bespoke pickers" is exactly what
  it exists for. The design's own `ph-funnel` sits in front of it as the
  row's decorative marker.
- **FAB**: 60×60dp at `RepFlowShapes.fab` (18dp), `primary`/`onPrimary` fill
  and glyph, carrying CP2's `plusBold` — the bold weight CP2 bundled
  specifically because the design draws this one plus bold and every other
  plus regular. `RepFlowButtons.kt` has no FAB tier, so this stays a Material 3
  `FloatingActionButton` with the design's shape and colours applied to it.
- **Two new string resources**, both for affordances the design confirms:
  `exercise_list_search_clear_content_description` (the `ph-x-circle` clear
  button, which CP2's own `RepFlowIcons.xCircle` KDoc already named as "clear
  the Exercise list search field") and `exercise_list_row_archived_badge`.
  The clear button routes through the existing `onQueryChanged("")` — no new
  event, no state-shape change — and only renders once there is something to
  clear, so the search-field test's empty-query path is untouched.
- **Three deviations from the design's `2c`, flagged not buried.** (1) The
  archived badge is drawn as an `Outline`-tone chip with the `ph-archive`
  glyph rather than at the design's literal reduced opacity: the tone is
  already the quietest chip treatment, and an unverified alpha on its label
  is exactly the kind of value the rest of this milestone refuses to ship
  without a contrast number behind it. (2) The picker's cells are
  equal-weight, so the two filters read as a segmented control rather than
  the design's content-sized pills — the cost of using the shared primitive
  instead of a fourth bespoke one. (3) The design's query-aware
  `Create "<query>"` empty state is **not** implemented: pre-filling the
  create form with the query needs a route argument and a ViewModel change,
  both of which this milestone's scope forbids, and a button that said
  `Create "bench"` while creating a blank exercise would be worse than its
  absence. The three existing empty-state strings are unchanged, which is
  also what keeps `ExerciseListScreenTest`'s three empty-state assertions
  green.
- Of CP2's eight Exercise-list glyphs, six are consumed here
  (`magnifyingGlass`, `xCircle`, `funnel`, `dotsThreeVertical`, `archive`,
  `plusBold`). `arrowLeft` is not: the Exercise list is a top-level
  destination with no back affordance, and the design's back arrow belongs to
  an IA this milestone explicitly does not change. `arrowCounterClockwise` is
  not: the plan states the snackbar's own appearance is untouched by this
  checkpoint.
- **Two additions beyond the plan's "Files modified" list**, on the same
  footing as CP1–CP4's own test files. `ExerciseListLabels.kt` holds the two
  non-composable label mappings and the filter row's order — split out both
  because detekt's `TooManyFunctions` threshold (11) is reached otherwise and
  because, being non-composable, it is the only part of this checkpoint a
  plain-JVM test can reach at all. `ExerciseListScreenWiringTest` (5 tests)
  is that test: it pins that the index-based filter row's order, labels and
  emitted enum agree (an inverted order still renders two plausible pills and
  still fires `onFilterChanged` — with the wrong value, invisible to the
  device test), that the three empty reasons stay three distinct messages,
  and that the row/FAB sizes and the 44dp tap-target floor hold.
- Pre-edit self-review flag from the plan discharged: `ExerciseListScreenTest`
  (androidTest) was read first, and every node it asserts on survives — the
  search-hint placeholder, the two filter labels, the three empty-state
  strings, the failure message and `Retry`, the FAB and row-menu content
  descriptions, the three dropdown items, and both snackbar messages.
  `MainActivityNavHostSmokeTest`'s own search-hint assertion is likewise
  untouched. `Archive` and `Archived` remain distinct nodes — `onNodeWithText`
  matches exactly, so the new badge cannot shadow the menu item.
- Verified: `testDebugUnitTest --tests
  "...presentation.exercise.list.*" --tests "...presentation.designsystem.*"
  --tests "...presentation.navigation.*"` (56 tests, 0 failures), plus
  `spotlessCheck`, `detekt`, `lintDebug` and `assembleDebugAndroidTest` all
  pass.

**CP4 — Apply foundation to app shell (bottom nav): complete.** CP1, CP2 and
CP3 remain complete below.

- Two files touched, exactly the two the plan names:
  `presentation/navigation/RepFlowDestinations.kt` and
  `RepFlowBottomNavigationBar.kt`. Same six routes, same order, same
  `selected`/`onClick`/`contentDescription` wiring — visual only.
- `TopLevelDestination.icon` changes from a literal single-letter `String`
  glyph to a `@DrawableRes Int` fed from CP2's `RepFlowIcons.Nav`, which is
  why CP2 exposed resource ids rather than `Painter`s: the destination list
  stays plain, non-composable data. The bar renders it as
  `Icon(painter = painterResource(...), contentDescription = null)` — the
  accessible name is already set once on the `NavigationBarItem`'s own
  `Modifier.semantics` block, and a second description would land on that
  same merged node, which is how `MainActivityNavHostSmokeTest.kt` resolves
  its navigation targets.
- **Two roles arrive through the cascade, three are overridden here.**
  Re-confirmed against Material 3 1.4.0's own `NavigationBarTokens` rather
  than assumed: `ContainerColor` = `SurfaceContainer` and
  `ItemActiveLabelTextColor` = `Secondary`, both assigned globally by CP1,
  so the bar fill and the selected label need nothing. The other three
  (`onSurfaceVariant` unselected icon+label, `secondaryContainer` pill,
  `onSecondaryContainer` selected icon) stay at the M3 baseline globally —
  they have consumers the design's values would break — and are applied
  through this file's own `NavigationBarItemDefaults.colors(...)`. Also
  re-confirmed from the 1.4.0 source: every parameter of that overload
  defaults to `Color.Unspecified` and merges via `copy`/`takeOrElse`, so
  the four parameters passed here leave `selectedTextColor` reading
  `secondary` exactly as before.
- **The per-theme pair is resolved from the applied scheme**, via
  `repFlowNavColors(scheme)` + CP3's `isDarkColorScheme(scheme)` — the same
  shape as CP3's `repFlowAccentOutlineColors`. Dark: `onSurface` @
  `navUnselectedAlphaDark` (.60), `primary` @
  `navSelectedIndicatorAlphaDark` (.20), `accent300` label. Light:
  `onSurface` @ `navUnselectedAlphaLight` (.66 — not the design's literal
  .55, which composites to 3.34:1 against the light bar), `primary` @
  `navSelectedIndicatorAlphaLight` (.16), `primary` label.
- **One deviation, flagged not buried.** The plan writes these values as
  `RepFlowDarkColorScheme.onSurface.copy(...)` /
  `RepFlowLightColorScheme.primary`; the implementation reads `onSurface`/
  `primary` off the *applied* scheme instead. For the app's two real
  schemes the resulting colours are identical — the branch still selects
  the theme's own alpha and its own selected-icon step — but a preview or a
  future override that supplies the light scheme now gets light's values
  rather than colours disagreeing with everything around them. This is
  CP3's established pattern, not a new one.
- **One addition beyond the plan's "Files modified" list**, on the same
  footing as CP1/CP2/CP3, whose own file lists likewise omitted the test
  classes they added: `RepFlowBottomNavigationBarTest` (5 tests). It pins
  what `RepFlowThemeTest` structurally cannot see — that test proves the
  *values* clear their floors, but would stay green if the bar swapped the
  two alphas or applied dark's `accent300` in light. The new tests assert
  `repFlowNavColors`' render inputs against those same named constants,
  that the two themes really do get different pairs, the six
  destination → glyph mappings, and that the routes and their order are
  unchanged. The colours' actual application to `NavigationBarItem`
  remains composable-only and is left to CP7's manual dark/light pass —
  adding Robolectric for it would be a new dependency category.
- Pre-edit self-review flag from the plan discharged: `grep` confirms no
  test anywhere asserts against the old literal `"E"`/`"W"`/… glyph text,
  and `BasicText` now has no remaining use in the codebase.
- Verified: `testDebugUnitTest --tests
  "...navigation.RepFlowBottomNavigationBarTest" --tests
  "...designsystem.icons.RepFlowIconsTest"` (10 tests, 0 failures), plus
  `spotlessCheck`, `detekt`, `lintDebug` and `assembleDebugAndroidTest` all
  pass.

**CP3 — Core reusable primitive components: complete.**

- Five new files under `presentation/designsystem/components/`, no existing
  file touched: `RepFlowButtons.kt`, `RepFlowCard.kt`, `RepFlowTag.kt`,
  `RepFlowStepper.kt`, `RepFlowStateComposables.kt`.
- **Buttons — three tiers**, as the plan corrected them: primary (56dp,
  12dp radius, `primary`/`onPrimary` fill+label, 16sp at weight 600 — the
  design's single confirmed weight-600 usage, and CP1 bundled the font for
  exactly this), accent-outline (48dp, 10dp radius, per-theme accent pair),
  neutral-outline (44dp, 8dp radius, `RepFlowColor.hairline` border, label
  at 80%). The two outline tiers draw `hairline`/the accent ramp directly,
  never `ColorScheme.outline`.
- **Per-theme accent pair, resolved from the applied scheme**, not from a
  second `isSystemInDarkTheme()` call: `isDarkColorScheme(scheme)` reads
  `surface`'s luminance, so a preview or a future theme override that
  supplies the light scheme gets light's values instead of disagreeing with
  the colours around it. Dark reads accent-700 border/accent-300 label,
  light accent-600/accent-700 — the ramp steps differ per theme, and dark's
  pair in light measures 1.38:1.
- **Cards** apply fill and border literally
  (`Modifier.background`/`.border`), so `RepFlowCard` never performs
  Material 3's own container lookup and never reads
  `surfaceContainerHighest` (which CP1 assigns for the app's one bare
  `Card(`). Radius is the shape scheme's own `large` (16dp, the top of the
  design's confirmed 14–16dp card range) rather than a fourth one-off
  token. The accent-tinted tone keeps the plan's theme-independent
  `accent900` fill/`accent700` border and therefore carries its own light
  content colour (the dark scheme's `onSurface`, 11.6:1 on that fill):
  light's `onSurface` would measure ~1:1 on it. Nothing consumes the accent
  tone in this milestone; light theme's treatment of it is undecided the
  same way the light selected pill is — one more item for CP7's light pass.
- **Tags/pills are one file because they are one pattern**: a static
  status chip (done/pending/outline/up-next, label always required so state
  is never colour alone) and `RepFlowPillPicker`, the single primitive
  covering the 0–5 recovery scale and the RPE/pain/technique rows rather
  than four bespoke pickers. The dark accent-tinted state is the design's
  own `primary` @ 22% + accent-600 border + accent-300 label, shared by the
  selected pill and the "done" chip; light reuses the confirmed
  accent-outline pair with no fill — the disclosed judgment call the plan's
  "Known limitations" already names. Neither reads
  `secondaryContainer`/`onSecondaryContainer`.
- **Stepper** is parameterised over the design's two sizes, defaulting to
  the in-context 44dp/`RepFlowShapes.stepper`/24sp pair CP6 consumes; the
  larger 48dp/10dp/32sp spec-sheet variant is reachable from the same
  component. The value renders in `RepFlowNumericTextStyle` so a changing
  digit does not reflow the row.
- **State composables** consolidate the loading/empty/failure trio,
  `FailureState`'s `onRetry` preserved (rendered as the accent-outline
  button, which is how the design draws exactly this affordance). They are
  `RepFlow`-prefixed because Material 3 now ships its own
  `LoadingIndicator`. No screen adopts them yet — CP5 removes the Exercise
  list's private copies; `ActiveWorkoutScreen.kt`'s and
  `TrainingPlanListScreen.kt`'s stay, per the plan's own note that this
  de-duplication does not complete within this milestone.
- **Three deviations, flagged not buried.** (1) The plan enumerated four
  files but specified five primitive families; the stepper had no assigned
  home, so it got its own file rather than being wedged into
  `RepFlowButtons.kt`. (2) Pill cells are 44dp, not the design's literal
  42dp: the design's own layout rules also state "every tap target is at
  least 44", the two disagree by 2dp, and the rule that protects the user
  wins. (3) No `enabled` parameter on the hand-drawn stepper/pill picker —
  the design specifies no disabled treatment, and a flag that renders
  identically either way is worse than its absence; a consumer that needs
  one adds it with a real visual.
- `RepFlowPrimitivesTest` (16 tests) pins what a plain-JVM test can reach:
  the per-theme accent pair, the selected-pill fallback, every contrast
  floor the primitives own (accent-outline and neutral-outline labels,
  primary labels, the dark selected pill over both grounds its translucent
  fill sits on, pending chips on `control`, the accent card's content
  colour), the tap-target minimum across all seven interactive sizes, the
  three tiers' heights/label sizes, and that the button and card radii
  reuse CP1's shape scheme wherever it already has the value.
- Verified: `testDebugUnitTest --tests
  "...designsystem.components.RepFlowPrimitivesTest"` (16 tests, 0
  failures), plus `spotlessCheck`, `detekt` and `lintDebug` all pass.

**CP2 — Bounded icon approach (local vector assets): complete.**

- 23 Phosphor glyphs bundled as local vector drawables
  (`res/drawable/ic_ph_*.xml`), path data copied verbatim from the
  upstream SVGs on Phosphor's own 256x256 grid — programmatically
  re-verified byte-identical against the source for all 23, which also
  confirms every file name resolves to the glyph it claims. Phosphor is
  MIT-licensed; license text bundled at `app/licenses/phosphor/LICENSE`,
  matching CP1's `app/licenses/inter/OFL.txt` pattern.
- No new Gradle dependency: `material-icons-core`/`-extended` stay
  absent, so AGENTS.md's "new dependency categories" stop condition is
  not triggered. The bounded-set-vs-icon-library decision remains
  surfaced for review, not silently finalized (execution plan CP2).
- `presentation/designsystem/icons/RepFlowIcons.kt` exposes the set as
  `@DrawableRes Int`s named after their upstream glyph. Resource ids
  rather than `Painter`/`ImageVector` so a non-composable holder can
  carry one — CP4 changes `TopLevelDestination.icon` from a `String`
  glyph to exactly this.
- The glyph set is the one the plan enumerates, no more: 8 for the
  Exercise list (CP5), 9 for Active Workout set-entry + rest timer
  (CP6), 6 for the bottom nav (CP4). Both plus weights are bundled
  (`ic_ph_plus`, `ic_ph_plus_bold`) because the design draws the FAB's
  plus bold and every other plus regular. The reference doc's `ph-pulse`
  is deliberately *not* bundled — it belongs to the progression-suggestion
  banner, which that doc itself marks out of scope.
- **Nav picks — four are judgment calls, flagged not claimed.**
  `RepFlowIcons.Nav` records the per-destination mapping here (picking it
  is CP2's decision; CP4 only consumes it). Design-confirmed: Plans =
  `list-checks`, History = `clock-counter-clockwise`. **Unconfirmed
  judgment calls**, because the target design has a four-destination bar
  and this milestone keeps the app's existing six: Exercises = `books`,
  Workout = `barbell`, Recovery = `moon-stars` (the design's only
  Recovery glyph, but drawn as an empty-state illustration, not a nav
  tab), Backup = `cloud-arrow-up`. A later design pass may replace all
  four; nothing else depends on the specific choice. CP7's manual pass
  should weigh in on them.
- `RepFlowIconsTest` (5 tests) pins the catalogue against the drawables:
  no two glyph names share one drawable, the catalogue and the bundled
  `ic_ph_*` set are exactly equal (so neither an orphan drawable nor a
  stray entry survives), the bundled set is the plan's enumerated set,
  each of the six destinations gets its own glyph from the catalogue, and
  the two design-confirmed nav picks are the glyphs the design names.
- Verified: `testDebugUnitTest --tests
  "...designsystem.icons.RepFlowIconsTest"` (5 tests, 0 failures), plus
  `spotlessCheck`, `detekt` and `lintDebug` all pass.

**CP1 — Design tokens & Compose theme foundation: complete.**

- Spike (step 0) confirmed the plain-JVM `app/src/test` suite constructs
  and reads a real Material 3 `ColorScheme` and does alpha-composite
  arithmetic with no Robolectric — so no new test-scope dependency
  category was needed. Deleted once `RepFlowThemeTest` subsumed it.
- Step 1 re-derived every colour value independently before authoring:
  all three OKLCH→sRGB conversions (`#9184d9`, `#eb827b`, `#a74541`)
  reproduce the plan's hexes exactly, as do all 32 role/context contrast
  ratios, both nav alpha composites and both selected-pill composites —
  zero mismatches against the plan's own table.
- Added `presentation/designsystem/`: `RepFlowColor.kt`,
  `RepFlowTypography.kt`, `RepFlowShapes.kt`, `RepFlowSpacing.kt`, plus
  `ROLE_AUDIT.md` (step 5's role → consumer → value → ratio table, kept
  under `app/` rather than in the plan doc, which is a protected path).
- Inter 4.1 bundled as local static weights 400/500/600 under
  `res/font/` (SIL OFL 1.1, license text at `app/licenses/inter/OFL.txt`)
  — no downloadable-font provider, no new Gradle dependency.
- `RepFlowTheme.kt` now builds `MaterialTheme` from the real schemes,
  typography and shapes, and provides the two composition locals for the
  tokens Material 3 has no slot for.
- `RepFlowThemeTest` (13 tests) pins every assigned role's hex in both
  themes, the accent ramp, the two per-theme extras, the three one-off
  radii, and nine computed contrast floors whose inputs are all read from
  production rather than restated in the test.
- `config/detekt/detekt.yml` scopes `MagicNumber`/`MayBeConst` off
  `**/presentation/designsystem/**`: a token file is definitionally a list
  of transcribed literals, and its members must stay plain `val`s (some
  are `@Composable` getters; the rest keep the identifiers the plan and
  CP4's call site name).
- Verified: `spotlessCheck`, `detekt`, `lintDebug`,
  `testDebugUnitTest` (388 tests, 0 failures) and
  `assembleDebugAndroidTest` all pass.

Remaining: none. CP1–CP7 are all complete.

## Current blockers

None.

## Active plan

`docs/milestones/repflow-redesign-visual-foundation-execution.md`
(revision 19), with
`docs/milestones/repflow-redesign-visual-foundation-reference.md` as its
source-of-truth reference. Checkpoint registry:
`docs/ai-workflow/registry/repflow-redesign-visual-foundation-registry.json`.

## Next action

All seven checkpoints are complete and **technical approval is recorded**:
`/approve-review implementation` wrote `technical_approval` `CURRENT` with
basis `EXTERNAL_APPROVE` against reviewed content commit `5cad0a1` (bundle
`c1f6e9fd…` / `review_content_id` `ba49e077…`) on 2026-09-07.

**Functional review round 1 returned `FAIL`.** Its single Gating finding (F1
— the app was not converted closely enough to the approved Claude Design
screens) is deferred in full to the remediation child
**`repflow-redesign-visual-foundation-remediation-1`**; see "Functional review
round 1 — outcome" in the checklist section below for the disposition and its
reasoning. No source or test file was edited, and `technical_approval` stays
`CURRENT`.

*(Superseded: the child's plan is approved at revision 20 and the child is in
implementation — see "In implementation" at the top of this file.)* **The
next action was `/milestone-plan
repflow-redesign-visual-foundation-remediation-1`** — after `/design-login`,
since the Claude Design project is that plan's source of truth and this
session could not read it. Every command in the child's cycle must **name the
child id explicitly**: `active_work_item_id` deliberately still points at this
parent, so an unnamed command would drive the wrong item. This parent stays at
`AWAITING_FUNCTIONAL_REVIEW` and cannot be accepted until the child reaches
`MILESTONE_COMPLETE`.

The judgment calls this milestone deliberately leaves to the reviewer, all
disclosed rather than silently resolved: CP2's four unconfirmed nav glyphs;
light `control`, the light scale-row selected state and the light status-chip
accent tint; and the disclosed `surfaceContainer` menu-elevation inversion.
See the plan's "Known limitations" and "Areas the reviewer should
specifically challenge". The unconsumed `RepFlowStepper`, the two diverging
FABs and the light selected-pill value have moved off that list and into
`docs/improvements/IMPROVEMENT_ROADMAP.md` §8.1–8.3 as named follow-ups.

---

## `repflow-redesign-visual-foundation` — functional review checklist (implementation revision 6)

The milestone's hard functional-review gate. Everything below is a **manual**
pass; the automated gate is already green and is not re-run by walking this
list.

Findings go to **`.ai-review/feedback/FUNCTIONAL_REVIEW.md`**. If it is clean,
`/accept-milestone` is the only acceptance command.

### Functional review round 1 — outcome: FAIL, deferred to a remediation child (2026-09-07)

The user walked this checklist and returned **`FAIL — functional remediation
required`** with a single Gating finding, **F1**. Read `/apply-functional-review`
classified it as a **missing requirement** and routed it through
`D-Functional-Remediation`'s **broad** branch. **No source or test file in this
work item was edited**, and `technical_approval` is deliberately left `CURRENT`
— the broad branch stales nothing, because nothing in the parent's tree
changed.

**F1 — the app was not converted closely enough to the approved Claude Design
screens.** The finding is not about isolated spacing, colour or icon
discrepancies. It is that the implementation applied a design *foundation* to
the existing RepFlow screens rather than converting those screens to the
Claude Design compositions: screen structure, layout, region placement,
navigation treatment, control choice and interaction shape all remain
recognizably the pre-redesign app. The reviewer's own summary — "existing
RepFlow UI + redesigned styling" rather than "RepFlow rebuilt to closely
reproduce the approved Claude Design experience" — is accepted as the finding
of record.

**Deferred in full to `repflow-redesign-visual-foundation-remediation-1`**
(created by this command; `phase: PLANNING`, governing version `2.1`,
`base_commit` `3257ee0`). Nothing about F1 is fixed inline: the conversion
spans multiple screens, would restructure Compose hierarchies rather than
restyle them, and reaches areas this milestone's approved plan put out of
scope — so it is planned and reviewed as its own work item, through the full
independent cycle.

**The scope tension behind F1, recorded because it is what the child must
resolve — not to dispute the finding.** The approved plan (revision 19) scoped
this milestone deliberately as **R0** of a multi-milestone redesign
decomposition and stated the deferral explicitly: the design's 4-tab
Home/Plans/History/Progress IA is "a *later* milestone, R1 in the redesign
decomposition, and is explicitly out of scope here," the workout-mode
full-screen takeover is "the same kind of out-of-scope IA change," and set
entry is to be "functionally identical before and after this milestone — only
their rendering changes." Those exclusions are why the disclosed CP5/CP6
deviations (the untouched input affordances, the unconsumed `RepFlowStepper`,
the query-aware empty state not built) exist at all. F1 measures the milestone
against the *intended product outcome* rather than against that plan text, and
the functional gate is exactly where that judgment belongs. The consequence is
a routing fact, not an argument: converging to the design requires the IA,
layout and interaction changes R0 excluded, which is precisely why this is
broad rather than bounded. **The child's planning stage owns deciding how much
of R1's scope it absorbs** — that decision is not made here, and must not be
assumed from this note.

**What this means for the checklist below.** It stays as written, unchanged,
and is *not* re-run now. The parent is blocked at
`AWAITING_FUNCTIONAL_REVIEW` until the child reaches `MILESTONE_COMPLETE`
(`IncompleteChildWorkItemError`). When the child's work lands, the surfaces it
restructures will need a **new** checklist written against the converted
screens — the flows below describe the current, unconverted layouts, so they
will not survive the conversion intact. The parts that will still carry over
are the regression checks that are about the *theme cascade* rather than
layout (flows 17–23) and the two pre-existing issues and one coverage gap
recorded under "Known limitations" — none of which F1 rests on, and all of
which keep their existing dispositions unless the conversion supersedes them.

**Two preconditions for the child's planning, found while routing this
finding.** (1) The Claude Design project is the child's stated source of
truth, and **this session cannot read it** — the Claude Design MCP returned
`needs_design_scopes`. Run **`/design-login`** before `/milestone-plan
repflow-redesign-visual-foundation-remediation-1`, or the plan will be drafted
against `docs/milestones/repflow-redesign-visual-foundation-reference.md`'s
transcribed values instead of the live designs, which is exactly the
second-hand reading F1 objects to. (2) F1's acceptance criterion 4 requires
**side-by-side** validation of design against running app, and criterion 6
names the physical **SM-S928B** as the primary target with the 384 dp AVD as
supporting evidence — both need to be reflected in the child's own plan and
verification steps, not just in its functional checklist.

**What this milestone actually changed, so the pass can be aimed.** Three
surfaces are reskinned on purpose — the bottom nav (CP4), the Exercise list
(CP5), and Active Workout's set-entry card + rest timer (CP6). Every *other*
screen is untouched markup that nonetheless renders through the new theme, so
its colours, type and container fills move even though its layout does not.
Flows 1–13 exercise the reskinned surfaces; 14–16 are the judgment calls the
plan leaves open for the reviewer; 17–23 are regression checks on the
untouched screens the cascade reaches.

### Setup

- **Device.** Physical **Samsung SM-S928B** (`RFCXA0RLSVT`, Android 16 / API
  36, 1080×2340 @ 450 dpi = **384 dp** wide). This is the milestone's
  authoritative device — where it and an emulator disagree, the phone is
  right. The 384 dp AVD `RepFlow_S24Ultra_384dp_API36` (`emulator-5554`) is
  attached too and is fine as a cross-check, but no verdict should rest on it
  alone.
- **Build and install.** Already done this session against this HEAD, with a
  clean working tree:

  ```
  ./gradlew assembleDebug
  adb -s RFCXA0RLSVT install -r app/build/outputs/apk/debug/app-debug.apk
  ```

  Both succeeded, so the app on the phone is built from the exact content this
  checklist describes. Re-run them only if the tree changes.
- **App data starts empty.** `adb -s RFCXA0RLSVT shell run-as com.repflow.app
  ls` shows no `databases/` directory, so the empty states in flows 2 and 7
  are reachable immediately, with nothing to clear first. Seed after checking
  them.
- **Theme switching.** `adb -s RFCXA0RLSVT shell cmd uimode night yes` (dark)
  / `no` (light), or Settings → Display → Dark mode. The device is currently
  on **dark**. There is **no in-app theme setting** — `RepFlowTheme` follows
  `isSystemInDarkTheme()`, which is a stated non-goal, not a defect.
- **No feature flags, no network.** RepFlow is offline-first; nothing here
  needs connectivity.

### Automated verification (current — deliberately not re-run)

Nothing under `app/` has changed since the last forced full run:
`git log 5cad0a1..HEAD -- app/` is empty, the only diff since is
`docs/ai-workflow/WORKFLOW_STATE.json`, and the working tree is clean. So the
run below is still the current evidence rather than a stale one:

```
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest \
  assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest --rerun-tasks
```

**`BUILD SUCCESSFUL`, 96 actionable tasks / 96 executed** (`--rerun-tasks`, so
nothing was reported green off an `UP-TO-DATE` marker); **428 JVM unit tests
across 76 suites, 0 failures / 0 errors / 0 skipped**; **162 instrumented
tests, 0 failures / 0 skipped**.

**One caveat, stated rather than implied.** The instrumented half of that run
is the **384 dp AVD only** — the physical phone was not attached for
implementation revisions 4, 5 or 6. It **is** attached now. Those three
revisions changed only markdown under `app/`, which reaches no APK entry
(`unzip -l` returns zero `ROLE_AUDIT` hits), so the shipped bytes are
identical to the revision the phone did test at 162/0/0/0 — but if you want a
physical instrumented pass on the record before accepting, this is the moment:

```
adb -s emulator-5554 emu kill      # leave only the phone attached
./gradlew connectedDebugAndroidTest
```

That is optional. This gate does not require it.

### Test data

Seed through the app's own UI, from the empty state, after flows 2 and 7 have
been checked empty:

1. **Three exercises, one per tracking type** — these are what make all seven
   `OutlinedTextField` call sites on the set-entry card reachable:
   `Bench Press` (Weight & reps), `Pull Up` (Reps only), `Plank` (Duration).
2. **A fourth exercise you then archive** (any tracking type), so the
   `Archived` filter and the archived badge have a row to render.
3. **One training plan, `Push Day`, with two rows** — two, so the first row's
   move-up is disabled while the second's is enabled, and so the plan editor
   card renders both its reps pair and its duration pair:
   - row 1 `Pull Up` — sets 3, warm-up sets 1, min/max reps 8/12, rest 60;
   - row 2 `Plank` — sets 2, min/max duration 30/45, rest 45.
4. **One finished workout session** started from `Push Day`, with at least one
   warm-up set and one working set recorded, so History, History detail and
   the "Done"/"Pending" planned-target chips all have real content.
5. **One recovery/futsal entry**, so Recovery history has rows.

The rest timer needs no plan rest value to appear: recording *any* set starts
it, at the plan's rest if there is one and **90 s** otherwise.

### Flows to exercise manually

Walk every flow on **both themes** unless it says otherwise. "Expect" is the
pass condition for that flow.

**The three reskinned surfaces**

1. **Bottom navigation (CP4).** Tap through all six destinations —
   Exercises, Workout, Plans, Recovery, History, Backup — and back again.
   **Expect:** six glyphs and six labels, no truncation at 384 dp; the
   selected item sits in an accent pill with its own label/glyph tint; the
   unselected labels stay comfortably legible on **both** themes (light's
   unselected alpha was corrected to `.66` specifically so it is grey-on-light
   rather than washed out — this is the one nav value with a numeric floor
   behind it, ~4.55:1). Selection follows the screen you are on, and no tap
   loses your place.
2. **Exercise list, empty (CP5).** Before seeding.
   **Expect:** "No exercises yet. Tap + to add one." in the shared empty-state
   primitive, the search field and filter row still present above it, and the
   FAB bottom-right as a **solid accent square** with a bold white `+`.
3. **Exercise list, populated (CP5).** After seeding.
   **Expect:** each row is a card at least 64 dp tall — name on top, tracking
   type + summary beneath — with a `⋮` kebab as a proper icon button, not a
   text glyph. A long exercise name wraps to **two** lines before ellipsis
   rather than one. Rows read as a band lifted off the page ground (this is
   the deliberate `surface`/`background` split; flow 17 checks it elsewhere).
4. **Exercise list, search (CP5).** Type into the search field, then use the
   `⊗` clear button rather than backspacing.
   **Expect:** the clear button appears **only** once there is something to
   clear; tapping it empties the field and restores the full list. Searching
   for something that matches nothing gives "No exercises match your search."
5. **Exercise list, filter, archive and undo (CP5).** Switch between `Active`
   and `Archived`; archive an exercise from its kebab; use the snackbar's
   `Undo`; restore from the `Archived` filter.
   **Expect:** the two filters read as a segmented control (equal-width cells,
   a deliberate deviation — see limitations); the archived row carries an
   `Archived` badge chip with an archive glyph; the snackbar's `Undo` actually
   restores; with no archived exercises the `Archived` filter shows "No
   archived exercises."
6. **Exercise list → editor.** Tap the FAB, save with a blank name, then
   fill it in and save.
   **Expect:** the validation message **"Name is required."** renders in the
   destructive colour and is legible on **both** themes — light's `error` was
   darkened one OKLCH step specifically to clear its floor here, so this is
   the live check on that decision. The tracking-type `FilterChip` row in this
   editor is deliberately **untouched** Material 3 (see flow 16).
7. **Active Workout with no session (CP6).** Before starting anything.
   **Expect:** "No active workout." plus `Start workout`. Open the
   start-workout menu.
   **Expect:** the menu lists your plans and `Start without a plan`.
8. **Set entry — Weight & reps (CP6).** Start an ad-hoc workout, add
   `Bench Press`, type a load and reps, tap `Add set`.
   **Expect:** the card is a RepFlow card with the exercise name beside a
   quiet outline chip carrying an info glyph and the tracking-type label;
   `Add set` is the solid accent button; the fields **clear immediately**
   after recording; the recorded set appears with a **26 dp circular marker**
   carrying the set number in tabular figures, beside a tabular summary line.
   Typing still works exactly as before — these are text fields, not steppers,
   deliberately (see limitations).
9. **Set entry — Reps only and Duration (CP6).** Add `Pull Up` and `Plank`
   to the same session and record a set on each.
   **Expect:** `Pull Up` shows **only** `Reps`; `Plank` shows **only**
   `Duration (s)`. Flip the `Warm-up set` switch on and record.
   **Expect:** the warm-up set's marker carries a **flame** glyph instead of
   a number, and the summary line gains its `(warm-up)` suffix. Together with
   flow 8 this reaches every field the card can render.
10. **Set-detail disclosure (CP6).** Tap `Set details`.
    **Expect:** it starts **collapsed**; expanding reveals RPE, pain and
    technique; the caret flips. Type an RPE, collapse the section, then
    `Add set` — **the RPE must still be recorded**, and the fields must reset
    afterwards so nothing carries into the next set unseen. With TalkBack on,
    the header should announce its expanded/collapsed state, not just "Set
    details, button".
11. **Recorded sets, undo and edit (CP6).** With at least one set recorded,
    use `Undo` and `Edit`.
    **Expect:** both appear **only** when the exercise has recorded sets; both
    are neutral outline buttons carrying their own glyphs; `Undo` removes the
    last set and `Edit` reopens it with its values.
12. **Rest timer strip (CP6).** Record any set and watch the strip that
    appears.
    **Expect:** a large tabular `M:SS` countdown that **ticks down in real
    time**; a progress bar that drains left-to-right over a neutral track;
    `-15s` and `+15s` keeping their words (not bare glyphs) and actually
    moving the countdown; `+15s` past the original total leaves the bar
    **full** rather than overflowing; `Skip` is an X icon button that
    dismisses the strip. Background the app for ~20 s and return — the
    countdown must reflect **wall-clock** elapsed time, not resume where it
    paused.
13. **Planned targets (CP6).** Start a workout from `Push Day` and record
    sets against a planned row.
    **Expect:** target chips render per row (target reps/duration, rest,
    warm-up/working progress), split across two lines rather than clipped;
    a chip flips from `Pending` to `Done` as its quota is met; the warm-up
    chip carries the flame glyph. Nothing is cut off at 384 dp.

**The judgment calls the plan leaves to you** — these are opinions being
asked for, not pass/fail defects.

14. **Light `control`.** On **light**, look at the rest-timer progress
    **track** (flow 12) and the set marker's fill (flow 8). This neutral has
    no design-confirmed light value; `#b2b6ca` is a placeholder.
    **Question:** does it read as an intentional neutral step against the
    card, or does it need to be quieter/stronger?
15. **The four unconfirmed nav glyphs.** Exercises, Workout, Recovery and
    Backup have no design-confirmed icon; Plans and History do.
    **Question:** are those four reasonable, or should they stay generic
    until the navigation-IA milestone settles which destinations survive?
16. **Light selected pill and accent-tinted chips.** On **light**, compare
    the selected vs unselected state on the Exercise list filter row, on
    History's filter chip, and on the Plans filter chips.
    **Question:** the selected-vs-ground separation is thin by design here
    (it leans on hue, and on Plans also on a border-vs-fill difference).
    Is that enough, or does the selected state need a stronger value?

**Regression checks on the untouched screens the theme cascade reaches** —
these screens were *not* rebuilt; the question is only whether the new colour
roles broke anything.

17. **The `surface`/`background` band.** On both themes, look at all ten top
    app bars (Exercises, Workout, Plans, Recovery, Recovery history, History,
    History detail, Backup, plan editor, exercise editor) and the stock list
    rows on History, History detail, Recovery history and Plans.
    **Expect:** a faint but deliberate band separating bar/row from the page.
    It is subtle (1.13:1 light / 1.16:1 dark) and it is intended.
    **Question:** does it read as intentional, or as a rendering artefact?
18. **Dialogs and date pickers.** Exercise, on both themes: History's date
    picker (including its `Clear`/`Cancel`/`OK`), Recovery's date picker,
    History's invalidate-session confirmation, and Backup's restore
    confirmation.
    **Expect:** every label legible on the dialog container. On **dark**,
    check the date picker's "today" ring with today *not* selected (pick a
    later date first) — it should be a clearly legible accent ring.
19. **Dropdown menus.** Open all seven: the Exercise list kebab, the
    start-workout menu, Active Workout's exercise picker, the plan editor's
    exercise picker, Plans' row menu, and History's two filter menus.
    **Expect:** every menu readable. Two disclosed, non-defect readings: on
    **dark**, a menu over a card or app bar reads as a *recessed* panel rather
    than a raised one; on **light**, a menu is delimited by its shadow alone.
    Over the page ground on dark the menu reads as elevated, normally.
20. **Plan editor card on dark.** Open `Push Day` for editing.
    **Expect:** each exercise row's card is a perceptible panel against the
    page (narrow — 1.16:1 dark, 1.05:1 light — but its edge traceable, and two
    stacked cards separable). Row titles and every `TextButton` read as the
    accent; the **disabled** move-up on row 1 is unmistakably dimmer than the
    enabled move-down; the `Optional` checkbox is clearly visible both
    unchecked and checked. Both target pairs (`Min/Max reps` on row 1,
    `Min/Max duration (s)` on row 2) render and are legible.
21. **Switches on dark.** Active Workout's `Warm-up set` switch and
    Recovery's switch.
    **Expect:** the **off** state still reads as *off*, not as *disabled* —
    the track sits close to the page ground and it is the outlined boundary
    plus the light thumb that carry it.
    **Question:** does that hold, or does off read as greyed-out?
22. **The two FABs.** Compare the Exercise list FAB (restyled, solid accent)
    with the Plans FAB (untouched Material 3, pale tint), one tab apart, on
    both themes.
    **Expect:** they diverge — this is a known, accepted limitation and the
    most visible touched-vs-untouched inconsistency in the app. Confirm you
    accept it for this milestone.
23. **Backup round-trip.** Export a backup, then restore it.
    **Expect:** the restore confirmation dialog reads clearly, the restore
    completes, and the seeded data comes back intact. This is a **data**
    regression check — the milestone touches no persistence, and nothing here
    should have changed.

### Expected result

The milestone passes functional review if, on the physical SM-S928B, on both
themes:

- flows 1–13 render as described, with **no functional change** to anything —
  every field still takes the same input, every callback still fires, undo,
  edit, archive/restore, snackbars, the rest timer's wall-clock behaviour and
  backup/restore all behave exactly as they did before this milestone;
- nothing is clipped, truncated or unreadable at 384 dp;
- flows 17–23 show no *new* illegibility on the untouched screens;
- you have formed and recorded an opinion on flows 14, 15, 16 and 21 — these
  are the questions the milestone deliberately did not answer for you.

Anything failing that, or any opinion you want acted on, goes to
`.ai-review/feedback/FUNCTIONAL_REVIEW.md`.

### Known limitations / out of scope for this review

Disclosed before testing, so they are not reported as discoveries:

- **Set entry stays typed, not stepped.** The design draws load/reps as
  steppers and RPE/pain/technique as pill rows; all seven fields remain text
  fields and the warm-up flag remains a switch. `RepFlowStepper` is built and
  ships **unconsumed**. Reasons and follow-up:
  `docs/improvements/IMPROVEMENT_ROADMAP.md` §8.1.
- **Only three surfaces are reskinned.** Plans, Recovery, History, Backup and
  both editors keep their current layout and their own bespoke
  loading/empty/error composables. Their colours and type move with the theme;
  their structure does not. Not a defect — the milestone's stated scope.
- **Eight of the ten top app bars** get no markup change beyond that cascade;
  there is no shared top-bar primitive yet.
- **The two FABs diverge** (flow 22) — `IMPROVEMENT_ROADMAP.md` §8.2.
- **The light selected-pill value** is a placeholder — §8.3.
- **No theme setting.** Light/dark follows the system only.
- **The `surface`/`background` band** (flow 17) and the **dark menu-elevation
  inversion** (flow 19) are disclosed consequences of the new role values, not
  bugs. Weigh them; don't file them as surprises.
- **Two pre-existing issues, confirmed pre-existing by reading the
  pre-milestone sources, and explicitly *not* attributable to this
  milestone.** Report them if you want them fixed, but they are not
  regressions:
  1. On Active Workout, **"Load (kg, optional)" wraps to two lines** while
     "Reps" beside it does not, so the two fields are visibly different
     heights. Reproduces at 384 dp on the phone and the AVD, both themes. The
     shared-`Row` layout predates CP6 (`git show 5e0870d`).
  2. On History detail, a **DURATION set renders as `Set 0:  kg x `** with
     empty values above its correct `Duration: 45s` line.
     `HistoryDetailScreen.kt` is not in this milestone's diff at all.
- **Bottom-nav label truncation does not reproduce at 384 dp** — all six
  labels render in full, though flush to the screen edges with little margin.
  It reproduces only when forced to 360 dp, and the `maxLines = 1` +
  ellipsis that cause it predate CP4 (`git show 83c4132`).
- **One coverage gap from the implementation pass**, carried here so it is
  closed by a human rather than assumed: Recovery's date picker was exercised
  on **dark only**. Flow 18 asks for it on light too.

---

## Milestone 8 — accepted and closed

All roadmap milestones (0-8) are **complete**. Milestone 8 (post-MVP
functional usability stabilization) is accepted and closed under an
explicit user waiver of its planned functional-review gate — see
"Functional review disposition" below.

### Goal

Milestone 8 closed the functional/usability gaps in
`docs/milestones/completed/milestone-8-reference.md`'s "Goals" section:
Recovery/futsal date/scale/history/save-feedback fixes, workout/plan
set-classification and start-from-plan wiring, History filtering and safe
accidental-workout removal, training-plan archive/restore, navigation
consistency, and backup hardening.

### Final state

**Milestone 8 accepted and complete.** All checkpoints (P0, CP0-CP16)
implemented and committed; full checkpoint-by-checkpoint detail is
preserved in git history for this file up to `dc4381a` and in the
archived plan docs.

- Implementation review: **approved** (round 4, `APPROVE` — no new
  blocking/important finding; rounds 1-3 findings all resolved).
- Integration: **complete** — `dc4381a` (last implementation-review-round
  commit) is reachable from `main` via merge `b39af90`, verified by
  `git merge-base --is-ancestor` before this doc update.
- Functional review: **waived** by explicit user decision — not
  performed, and not claimed to have passed. See "Functional review
  disposition" below.
- Milestone status: **accepted/complete**.

Milestone 8's plans are archived at
`docs/milestones/completed/milestone-8-{execution,reference}.md`.
Milestones 1-7 remain archived alongside them.
`docs/improvements/IMPROVEMENT_ROADMAP.md` §2.1 (`ReturnCount` tuning)
was deferred pending Milestone 8's acceptance and is now unblocked, but
has not been started or scheduled.

### Functional review disposition

The manual functional-review checklist prepared at CP16 (23 numbered
flows covering navigation, Recovery/futsal, workout/plan wiring, History,
archive/restore, and backup) was written and is preserved in git history
at `dc4381a`, but was never walked by the user on a device.

On 2026-07-31 the user explicitly accepted and closed Milestone 8 while
waiving that review, stating explicitly this is a user-authorized waiver,
not a claim that functional review passed. Stated reasons:

- technical implementation and external implementation review are
  complete;
- the implementation is integrated into `main`;
- the user is postponing detailed UI/UX validation;
- future UI/UX work will be handled as a separate, later, Figma-led
  redesign milestone.

---

## `workflow-v2-1-core` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above. It
tracks the separate, non-product process work item `workflow-v2-1-core`
(see `docs/ai-workflow/WORKFLOW_V2_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`).

**Status note (2026-08-04)**: the checklist below (five checks) was
independently re-verified and the user gave explicit functional
acceptance of it in conversation — but before that acceptance could be
recorded, a separate finding was discovered
(`docs/ai-workflow/dry-run/WF8B_FINDING_continued_scope_remediation_no_nonterminal_return_path.md`):
the workflow tooling had no safe, non-terminal way to record acceptance
of a continued-scope round (like this one) while the item's own last
checkpoint (`WF8b`) remains incomplete. A plan revision
(`D-Scoped-Remediation-Acceptance` in `docs/ai-workflow/WORKFLOW_V2_PLAN.md`)
fixing that gap went through six external plan-review rounds (22 through
27), each round's findings applied in place within the same section —
`GPT-R36-001`/`-002`/`-003` (fail-open registry guard, uncommitted
acceptance, unbound evidence), `GPT-R37-*`, `GPT-R38-*`, `GPT-R39-*`, and
`GPT-R40-001`/`-002` (the required evidence-binding: a `scoped_remediation`
confirmation must now name the exact `/prepare-functional-review`-reported
checklist-evidence commit SHA and blob) — until Revision 27 came back
`Status: APPROVE` with zero blocking/important findings and was recorded
as `plan_approval` (commit `c132185`). This revision's own implementation
(the `complete_work_item` own-registry guard, the functional-checklist
evidence trailer/guard machinery, the new `/accept-scoped-remediation`
command, and the extensive test suite covering every named scenario) has
now landed as continued `WF4c` scope and is awaiting its own external
implementation review — `phase` remains `IMPLEMENTING` until that review
and `/approve-review implementation` complete; `AWAITING_FUNCTIONAL_REVIEW`
is not yet re-entered. The checklist below is preserved unchanged; the
user's acceptance of it has **not** been recorded anywhere yet
(deliberately).

**Correction (2026-08-26)**: the status note above is preserved as the
historical record of what was true on 2026-08-04. It is no longer live
guidance. `workflow-v2-1-core` subsequently reached `MILESTONE_COMPLETE`
through `/accept-milestone` (see `docs/ai-workflow/WORKFLOW_STATE.json`),
and `/accept-scoped-remediation` has since been **retired** — its entry
precondition had no producer in any supported lifecycle, so it could only
ever refuse (ledger `I10` in
`docs/ai-workflow/audit/WORKFLOW_DEFECT_LEDGER.md`). Do not look for that
command; the supported ways forward from a non-terminal registry are
`/milestone-implement`, or `/apply-functional-review`'s bounded/broad
branches.

**Context**: checkpoint `WF8b` (manual multi-session dry run) began
against the synthetic work item `v2-1-dry-run`, but its first scenario
(S1) blocked before any mutation on a real defect:
`scripts/workflow_fingerprint.py`'s `--work-item-id` flag affected only
which bundle directory was written to — every actual identity/content
computation silently hardcoded `workflow-v2-1-core`'s own metadata
regardless of the flag (see
`docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md`).
That defect was fixed as continued WF8b scope under a revised plan
(Revision 21), went through five rounds of external implementation
review (`GPT-R30` through `GPT-R34`, all resolved), and now has
`technical_approval` recorded for implementation revision 4
(`8f9ea8cc60192bbbecfb7ed092c1dc79739047c7616ea4e2cddd8b68ecd0c9ed`,
commit `7bef596`). `WF8b` itself is **not yet complete** — this
functional review gates only the fingerprint-generalization fix, which
must pass before scenario S1 can be safely rerun.

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `scripts/prepare-ai-review.sh`,
`docs/ai-workflow/*`, `.claude/commands/*.md`). No build/install step is
needed; everything below runs with `python3` from the repo root.

### Automated verification (already re-confirmed this session, current)

All green, re-run independently right before this checklist was written
(no source/test file has changed since `technical_approval` was
recorded — the last commit, `ae51770`, is metadata-only):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 51/51 |
| `scripts/workflow_state_test.py` | 221/221 |
| `scripts/workflow_integration_test.py` | 35/35 |
| `scripts/workflow_test_harness_test.py` | 19/19 |

Total: 448/448. (`workflow_fingerprint_demo_test.py` has pre-existing,
unrelated failures against real-repository paths outside this item's
scope — documented as such since the `GPT-R32` round; not part of this
review.)

### Test data

None to seed — all checks below read this repository's own real,
already-committed state (`workflow-v2-1-core` and the dormant
`v2-1-dry-run`/`milestone-8` entries already in
`docs/ai-workflow/WORKFLOW_STATE.json`).

### Flows to exercise manually

1. **Fix confirmed — generic CLI now resolves the real item correctly.**
   Run:
   ```
   python3 scripts/workflow_fingerprint.py --work-item-id workflow-v2-1-core 162154d3e5e10eb65e109833acae4b4fb01fc5d6
   ```
   Expected: prints `work_item_id: workflow-v2-1-core`,
   `review_content_id: b6d4ea6a8778321526fa5a3a6d2af17801f6187fd137680bbef8cce008ef95c0`
   (matches `plan_approval.approved_review_content_id` exactly). Already
   re-run this session — passed.
2. **Fix confirmed — no more silent cross-item fallback.** Run:
   ```
   python3 scripts/workflow_fingerprint.py --work-item-id v2-1-dry-run
   ```
   Expected: fails closed with `MissingPlanStageMetadataError:
   v2-1-dry-run.registry_path is null` — naming `v2-1-dry-run` itself,
   not silently substituting `workflow-v2-1-core`'s own data (the
   original S1 defect). Already re-run this session — passed.
3. **Default resolution still targets the live active item.** Run the
   same command with no `--work-item-id` at all; expect the identical
   error (since `active_work_item_id` is currently `v2-1-dry-run`),
   confirming the documented "omitted resolves to
   `active_work_item_id`" default. Already re-run this session — passed.
4. **Re-run the automated suite yourself** (table above) and confirm the
   same 448/448 result independently, rather than trusting this
   document's claim alone.
5. **Spot-check the migration**: confirm
   `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json` is
   `schema_version: 2` with both `plan_stage` and `implementation_stage`
   keys present.

### Expected result

All five checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- This review does **not** cover `WF8b` itself — the dry run's S1
  through S17 scenarios remain unexecuted and are explicitly deferred to
  a fresh session per `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`.
- Item 160 (see `IMPLEMENTATION_SUMMARY.md`'s "Accepted, not fully
  closed" section) is the process obligation this review partially
  discharges (suite green, confirmed above) — the other half (a
  fresh-session S1 re-attempt) happens after this review, not during it.
- A handful of named test sub-cases remain partial, not absent (see
  `IMPLEMENTATION_SUMMARY.md`'s "Accepted, not fully closed this pass"
  list) — not blocking for this functional review.

Findings go in `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`.

### `workflow-v2-1-core` — MILESTONE_COMPLETE (real, 2026-08-21)

Functional review for implementation revision 17 was completed and
recorded clean: `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`
(gitignored, not itself a repository artifact) records `Result: PASS`
against the revision-17 checklist above, citing evidence commit `805b966`
/ blob `8b0a49ba217d62ff8bafcf0638876dfd564babca`, with no functional
defect, usability issue, or missing requirement found blocking.

The user then gave literal `/accept-milestone`-stage confirmation for
`workflow-v2-1-core` ("I confirm acceptance of workflow-v2-1-core."),
validated for real by `workflow_state.validate_user_confirmation`. The
advisory terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True` from phase
`AWAITING_FUNCTIONAL_REVIEW`) and the authoritative
`workflow_state.complete_work_item` call both succeeded: no
`IncompleteChildWorkItemError` (no work item declares
`workflow-v2-1-core` as its `parent_work_item_id` — only `milestone-8`
exists alongside it, and is unrelated), no `IncompleteOwnCheckpointsError`
(all 18 registry checkpoints `COMPLETE`), and no
`UnsatisfiedCompletionObligationError` (both declared completion
obligations, `WFO-LEDGER-COVERAGE` and `WFO-STATE-SERIALIZATION`,
resolved `PASS`, recorded in
`work_items["workflow-v2-1-core"].completion_obligations_accepted`).
`work_items["workflow-v2-1-core"].phase` is now `MILESTONE_COMPLETE`;
`active_work_item_id` reset to `null` (it pointed here).

**Steps 3/5 do not apply — the same gap the `v2-1-dry-run` dry run
already predicted this item would face** (see that item's own S16
`MILESTONE_COMPLETE` note further below): `grep` confirms zero mentions
of `workflow-v2-1-core` anywhere in `docs/ROADMAP.md` (step 3 has no
entry to mark). `docs/ai-workflow/WORKFLOW_V2_PLAN.md` is **not**
archived to `docs/milestones/completed/` (step 5) — unlike a product
milestone's execution/reference plan, which really is finished once its
feature ships, this document remains the live design reference for the
workflow tooling itself, still linked from
`docs/ai-workflow/REVIEW_PROTOCOL.md`, `MILESTONE_WORKFLOW.md`, and this
repository's `CLAUDE.md`; archiving it would misfile still-authoritative
documentation as historical. Step 4 is honored via this note itself,
placed in this item's own section rather than the top-level "Active
plan" section, which belongs to Milestone 8 alone and is untouched by
this completion. Step 7 ("Next action") is unaffected for the same
reason `v2-1-dry-run`'s was: it already correctly points at the real
`docs/ROADMAP.md`, which `workflow-v2-1-core` was never part of.

---

## `workflow-v2-1-core` — functional review checklist (implementation revision 17)

This section is unrelated to the roadmap/Milestone 8 content above. It is
the same process work item as the revision-4 checklist above, re-entering
`AWAITING_FUNCTIONAL_REVIEW` for the first time since then.

**Context**: between revision 4 and now, this item stayed under `WF8b`
(later `WF8c`) as continued implementation scope through 13 further
implementation rounds — the item never left `IMPLEMENTING`/
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`/`APPLYING_REVIEW_FEEDBACK` in
that span, so no intermediate functional-review checkpoint exists to
report on. `WF8c` (the item's own last registry checkpoint) is now
`COMPLETE` — all 18 registry checkpoints are `COMPLETE`
(`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`) — and
`technical_approval` was just recorded for implementation revision 17
(commit `838a523`, `Workflow-Technical-Approval:
a44d912afd81fa757d2b9893e77a0d8990bce05ed78ad487352d3e849efe9143`, basis
`EXTERNAL_APPROVE`, round GPT-R137).

Re-walking all 18 checkpoints' behavior in one manual pass is not
practical, so this checklist targets two things instead: (1) the exact
commands this same session already exercised for real, end to end
(the most direct functional test available for process tooling — there
is no separate UI to click through), and (2) the specific behavioral
fixes revision 17 itself introduced (`GPT-R136-001`,
`OPUS-R136-M01`/`-M03`/`-M04`), since those are what round GPT-R137
actually reviewed and approved this round.

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since the last full run at `894dd94`):

```
python3 -m unittest workflow_integration_test workflow_state_test \
  workflow_state_completion_obligations_test workflow_fingerprint_test \
  workflow_test_harness_test workflow_fingerprint_generalization_test
```

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | part of 1097 |
| `scripts/workflow_fingerprint_generalization_test.py` | part of 1097 |
| `scripts/workflow_state_test.py` | part of 1097 |
| `scripts/workflow_state_completion_obligations_test.py` | part of 1097 |
| `scripts/workflow_integration_test.py` | part of 1097 |
| `scripts/workflow_test_harness_test.py` | part of 1097 |

Total: **1097/1097**, zero failures/errors/skips (matches
`TEST_RESULTS.md`'s recorded result exactly). Includes six new regression
tests added this round for the four fixes below; all six were also
independently confirmed to fail against revision 16's pre-fix code and
pass against revision 17 (`TEST_RESULTS.md`'s own "Confirmed non-vacuous"
section).

### Test data

None to seed — every check below reads this repository's own real,
already-committed state.

### Flows to exercise manually

1. **Re-run the automated suite yourself** (command above, from
   `scripts/`) and confirm 1097/1097 independently, rather than trusting
   this document's claim alone.
2. **`GPT-R136-001` — evidence clone no longer shares donor objects.**
   The pinned-evidence materializer (`workflow_state._materialize_pinned_
   worktree_at_commit`) now clones with `--dissociate` whenever the
   source repository itself borrows objects through an alternate.
   `TEST_RESULTS.md`'s "Additional checks run this session" section
   documents a hand reproduction (donor/source fixture, materialize,
   delete the donor, confirm the resulting clone still reads clean); the
   corresponding automated case is
   `test_evidence_clone_has_no_alternates_when_source_repository_borrows_objects`
   in `scripts/workflow_state_completion_obligations_test.py`. Run it in
   isolation to confirm it passes on its own:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_evidence_clone_has_no_alternates_when_source_repository_borrows_objects
   ```
3. **`OPUS-R136-M01` — an inherited global `core.hooksPath` no longer
   fires during evidence materialization.** Same test class, run:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_materialization_clone_step_ignores_an_inherited_global_hooks_path
   ```
4. **`OPUS-R136-M03` — the cleanliness walk now catches a nested `.git`
   payload and an untracked empty directory.** Same test class, run:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_verify_pinned_worktree_clean_catches_nested_dot_git_and_untracked_empty_directory
   ```
5. **`OPUS-R136-M04` — an omitted `--work-item-id` no longer silently
   accepts a stale `IMPLEMENTATION_SUMMARY.md` revision.** Run:
   ```
   python3 -m unittest workflow_fingerprint_test.TestGeneratorSideStageDocumentBinding.test_finalize_bundle_generation_omitted_work_item_id_reports_mismatch_on_stale_revision
   ```
6. **The actual `/approve-review implementation` flow, exercised for
   real this session** (not simulated): gate-reachability check,
   fresh `bundle_id`/`review_content_id` recomputation, `resolve_approval_
   basis` → `EXTERNAL_APPROVE`, `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`838a523`) carrying
   the `Workflow-Technical-Approval`/`Workflow-Work-Item` trailers.
   Confirm independently:
   ```
   git show --stat 838a523
   git log -1 --format=%B 838a523
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   a44d912afd81fa757d2b9893e77a0d8990bce05ed78ad487352d3e849efe9143` /
   `Workflow-Work-Item: workflow-v2-1-core`.
7. **Ledger coverage still holds end to end.** The broadest single
   "everything is still wired together" signal this item has:
   ```
   python3 -c "
   import sys; sys.path.insert(0, 'scripts')
   import workflow_state as ws
   print(ws.verify_wfo_ledger_coverage('.', 'HEAD'))
   "
   ```
   (run from the repo root — `repo_root` must be the real repository path,
   not a relative `..` from inside `scripts/`, or the internal evidence-
   clone step fails closed with a `git clone` error). Expected:
   `{'status': 'PASS', ...}`, all 211 reconciliation-table items accounted
   for — matches `TEST_RESULTS.md`'s own re-run of this check (~52.5s).

### Expected result

All seven checks above pass exactly as described. Checks 1-5 and 7 are
read-only (safe to repeat freely); check 6 inspects a commit that already
exists in history rather than creating one.

### Known limitations / out of scope for this review

- This is **not** a re-walk of all 18 checkpoints' own original
  functional behavior — revision 4's checklist above already covered
  `WF4a-i`'s fix in detail, and no separate functional checkpoint exists
  for the 13 rounds between revision 4 and 17 (see "Context" above). A
  reviewer wanting deeper coverage of a specific checkpoint should read
  `IMPLEMENTATION_SUMMARY.md`'s per-checkpoint sections and re-run that
  checkpoint's own named test classes directly.
- Three further Optional-severity findings from round GPT-R137 remain
  open but are explicitly not approval gates (see
  `.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md`'s summary):
  an untracked symlink-to-directory is still skipped by the cleanliness
  walk; the omitted-id path propagates raw `FileNotFoundError`/
  `JSONDecodeError` for missing/malformed state instead of a typed
  mismatch result; and the no-replacement-object correction has no
  dedicated regression test despite being behaviorally observable.
- `workflow_state_demo_test`/`workflow_fingerprint_demo_test` (the two
  real-repository demo modules) are deliberately excluded from this
  item's declared six-module suite (documented rationale unchanged since
  `GPT-R9-002`) — not re-run as part of this checklist.

Findings go in `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`.

---

## `v2-1-dry-run` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core` section above it. It is the synthetic
`v2-1-dry-run` work item's **own** functional-review checklist — evidence
for `WF8b`'s S9 scenario (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`),
proving `/prepare-functional-review`'s real checklist-writing path for a
`"2.1"`-governed, state-tracked work item. `v2-1-dry-run` has no product
surface of its own: its "implementation" is three trivial scratch-file
checkpoints (`S-CP1`/`S-CP2`/`S-CP3`,
`docs/ai-workflow/registry/v2-1-dry-run-registry.json`), created solely to
exercise the real checkpoint/approval/review command surface end to end.

**Context**: `technical_approval` for `v2-1-dry-run` implementation
revision 4 is now recorded (`basis: USER_OVERRIDE`, commit `7c1032c`,
`review_content_id`
`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96`),
landed via a real `/approve-review implementation v2-1-dry-run` invocation
(commit `8b72452`) after S9's provenance-gate remediation (see
`docs/ai-workflow/dry-run/WF8B_S9_FINDING_provenance_gate_never_implemented.md`).
`phase` transitioned to `AWAITING_FUNCTIONAL_REVIEW` as part of that same
approval. Per `WF8B_SCENARIOS.md`'s own reordering note, S10 (a
functional-review finding + `/apply-functional-review` bounded
remediation) is exercised next, ahead of S9's final `/accept-milestone`
step.

### Setup

No Android app / Gradle changes are involved. Process tooling only. No
build/install step is needed; everything below runs with `python3` from
the repo root.

### Automated verification (re-confirmed this session, current)

Re-run independently immediately before this checklist was written (no
source/test file has changed since `technical_approval`'s
`reviewed_content_commit`, `7c1032c` — only metadata-only commits since):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 60/60 |
| `scripts/workflow_state_test.py` | 408/408 |
| `scripts/workflow_test_harness_test.py` | 19/19 |
| `scripts/workflow_integration_test.py` | 46/47 |

The one `workflow_integration_test.py` failure is the same pre-existing,
unrelated WFR-row-count staleness already documented across this dry
run's own outcome notes (a static assertion on
`docs/ai-workflow/WORKFLOW_V2_PLAN.md`'s requirements-table row count,
last synced at a much earlier plan revision; not a functional regression
and not in scope for this review).

### Test data

None to seed — this checks the repository's own real, already-committed
state (`v2-1-dry-run`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, and the three scratch files its
checkpoints created).

### Flows to exercise manually

1. **Scratch checkpoints exist as committed.** Confirm
   `docs/ai-workflow/dry-run/scratch/a.txt` and `scratch/b.txt` exist and
   are tracked (`git ls-files` shows both); `scratch/c.txt` exists but is
   deliberately **not** tracked yet — it is live S13-S15 dirty-worktree
   fixture state, not a defect.
2. **`v2-1-dry-run`'s own state is internally consistent.** Confirm
   `docs/ai-workflow/WORKFLOW_STATE.json`'s `work_items["v2-1-dry-run"]`
   shows `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `last_completed_checkpoint_id: "S-CP3"`, and `technical_approval.status:
   "CURRENT"` with `reviewed_content_commit: "7c1032c..."`.
3. **The real command surface, not just the hermetic fixtures, drove this
   round.** Confirm `8b72452` (the technical-approval commit) carries a
   `Workflow-Technical-Approval` trailer and a `Workflow-Work-Item:
   v2-1-dry-run` trailer, and that `bc0770b` (this outcome's own
   documentation commit) is metadata-only (`docs/` only).
4. **Re-run the automated suite yourself** (table above) and confirm the
   same result independently, rather than trusting this document's claim
   alone.

### Expected result

All four checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- `v2-1-dry-run` has no product surface — this review exercises process
  tooling only, per `WF8b`'s own purpose.
- `WF8b` itself is **not** complete: S10 through S17 remain unexecuted as
  of this checklist. This review covers only S9's implementation-approval
  and functional-review-preparation halves.
- `scratch/c.txt` and `docs/ai-workflow/dry-run/verify_review_content_id.py`
  are deliberately left uncommitted — do not commit or clean them up; they
  are live fixture state for the not-yet-run S13/S14/S15 scenarios.

Findings go in `.ai-review/v2-1-dry-run/feedback/FUNCTIONAL_REVIEW.md`.

---

## `v2-1-dry-run` — functional review checklist (implementation revision 6)

This section supersedes the implementation-revision-4 section immediately
above for review purposes — that section is left in place as historical
evidence for `WF8b`'s S9 scenario, not edited. This section is the same
synthetic `v2-1-dry-run` work item's checklist for `WF8b`'s **S10** scenario
(`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`): a real functional-review
finding was filed against revision 4's content, `/apply-functional-review`'s
bounded-remediation branch fixed it, and a fresh implementation-review round
plus a repeat, user-gated `/approve-review implementation v2-1-dry-run`
carried `technical_approval` back to `CURRENT` at implementation revision 6.

**Context**: `docs/ai-workflow/dry-run/scratch/b.txt` (`S-CP2`'s marker) was
deliberately corrupted to "checkpoint 1" (commit `3d2f3fd`) to give S10's
functional-review finding real content to point at. Per
`D-Functional-Remediation`'s bounded branch:
`workflow_state.mark_technical_approval_stale` flipped
`technical_approval.status` to `STALE` *before* the fix landed (verified in
the fix commit `fae7420` itself); the fix restored `scratch/b.txt` to
"checkpoint 2", byte-identical to its original completion commit `8375b64`;
a post-fix bundle was regenerated (`implementation_revision` `4` → `5`,
`review_content_id` unchanged at
`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96` — the
fix restored, not changed, protected content). A fresh implementation-review
round then returned a clean `APPROVE` (zero findings) against round 5's
bundle; a provenance-gap-closure round 6 (`record_bundle_generation`, commit
`c11ec01`) advanced `reviewed_implementation_head` to `c916ead` /
`implementation_revision` to `6` with no content change, closing the same
`WF8B-003`-pattern gap S9 already documented once. The user then
re-invoked `/approve-review implementation v2-1-dry-run` for real:
`technical_approval` is now `status: CURRENT`, `basis: USER_OVERRIDE`,
`reviewed_content_commit: c916ead`, recorded by the metadata-only approval
commit `9fd3c72`. `phase` remains `AWAITING_FUNCTIONAL_REVIEW` throughout
(nothing downstream reads `phase` to decide this gate, per S7's own
established finding).

### Setup

No Android app / Gradle changes are involved. Process tooling only. No
build/install step is needed; everything below runs with `python3` from
the repo root.

### Automated verification (re-confirmed this session, current)

Re-run independently immediately before this checklist section was written
(no source/test file has changed since `technical_approval`'s
`reviewed_content_commit`, `c916ead` — only metadata-only commits since):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 60/60 |
| `scripts/workflow_state_test.py` | 408/408 |
| `scripts/workflow_test_harness_test.py` | 19/19 |
| `scripts/workflow_integration_test.py` | 46/47 |

The one `workflow_integration_test.py` failure is the same pre-existing,
unrelated WFR-row-count staleness already documented across this dry run's
own outcome notes (`test_every_wfr_row_description_matches_json_exactly`:
67 actual table rows vs. an assertion still pegged at 60 from a much
earlier plan revision) — not a functional regression and not in scope for
this review.

### Test data

None to seed — this checks the repository's own real, already-committed
state (`v2-1-dry-run`'s own entry in `docs/ai-workflow/WORKFLOW_STATE.json`,
and the three scratch files its checkpoints created).

### Flows to exercise manually

1. **Scratch checkpoints exist as committed, all three, all correct.**
   Confirm `docs/ai-workflow/dry-run/scratch/a.txt`, `scratch/b.txt`, and
   `scratch/c.txt` all exist and are tracked (`git ls-files` shows all
   three — unlike the implementation-revision-4 checklist above, `c.txt`
   was already tracked by this point, via `S-CP3`'s own completion commit
   `8b70136`, well before S7/S8; that earlier section's "not tracked yet"
   note describes an even-earlier point in the dry run's own timeline, not
   this one). Confirm their content reads "checkpoint 1", "checkpoint 2",
   "checkpoint 3" respectively — `scratch/b.txt` in particular, since it is
   the file S10's finding and fix both touched.
2. **`v2-1-dry-run`'s own state is internally consistent.** Confirm
   `docs/ai-workflow/WORKFLOW_STATE.json`'s `work_items["v2-1-dry-run"]`
   shows `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `last_completed_checkpoint_id: "S-CP3"`,
   `implementation_revision: 6`, and `technical_approval.status: "CURRENT"`
   with `reviewed_content_commit: "c916ead..."`.
3. **The stale-before-edit ordering actually happened.** Confirm commit
   `fae7420` (`git show fae7420:docs/ai-workflow/WORKFLOW_STATE.json`)
   itself carries `technical_approval.status: "STALE"` for `v2-1-dry-run` —
   the intermediate state, not just today's end state — alongside the
   `scratch/b.txt` fix, in the same commit.
4. **The real command surface, not just the hermetic fixtures, drove the
   repeat approval.** Confirm `9fd3c72` (the second technical-approval
   commit) carries a `Workflow-Technical-Approval` trailer and a
   `Workflow-Work-Item: v2-1-dry-run` trailer, and that `c11ec01`
   (round 6's provenance-gap-closure commit) carries a
   `Workflow-Bundle-Generation-Record: v2-1-dry-run/6` trailer.
5. **Re-run the automated suite yourself** (table above) and confirm the
   same result independently, rather than trusting this document's claim
   alone.

### Expected result

All five checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- `v2-1-dry-run` has no product surface — this review exercises process
  tooling only, per `WF8b`'s own purpose.
- `WF8b` itself is **not** complete: S11 through S17 remain unexecuted as
  of this checklist. This review covers S9's still-deferred
  functional-review-preparation half (this section) and S10's bounded
  remediation, not the final `/accept-milestone` step.
- `docs/ai-workflow/dry-run/verify_review_content_id.py` remains
  deliberately uncommitted — do not commit or clean it up; it answers a
  read-only review question (`GPT-DRY-R1-002`) and is not itself dry-run
  scratch fixture state.

Findings go in `.ai-review/v2-1-dry-run/feedback/FUNCTIONAL_REVIEW.md`.

### `v2-1-dry-run` — MILESTONE_COMPLETE (S16, real, 2026-08-15)

The user gave literal `/accept-milestone`-stage confirmation for
`v2-1-dry-run` ("I confirm acceptance of v2-1-dry-run."), validated for
real by `workflow_state.validate_user_confirmation`. The advisory
terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True`) and the authoritative
`workflow_state.complete_work_item` call both succeeded — no
`IncompleteChildWorkItemError` (no remediation child of `v2-1-dry-run`
exists) and no `IncompleteOwnCheckpointsError` (`S-CP1`-`S-CP3` all
`COMPLETE`), proving S16's own pass/fail evidence exactly.
`work_items["v2-1-dry-run"].phase` is now `MILESTONE_COMPLETE`;
`active_work_item_id` reset to `null` (it pointed here).

**A genuine gap in `/accept-milestone`'s steps 3/5, recorded rather than
worked around**: those steps ("update `docs/ROADMAP.md` to mark the
milestone complete"; "archive this milestone's execution/reference plans
to `docs/milestones/completed/`") are written for a real product/process
milestone with a `ROADMAP.md` entry and a top-level "Active plan" section
in this file. `v2-1-dry-run` has neither — `grep` confirms zero mentions
of it (or of `workflow-v2-1-core`) anywhere in `docs/ROADMAP.md`, and its
own plan doc (`docs/ai-workflow/dry-run/v2-1-dry-run-plan.md`) was never
linked from this file's top "Active plan" section, which belongs to
Milestone 8 alone. Steps 3 and 5 were therefore **not** performed:
step 3 has no entry to mark, and step 5 would misfile a throwaway
synthetic item's dry-run plan into the real completed-milestones
archive, contradicting `WF8B_SCENARIOS.md`'s own "Isolation" discipline
("The real `workflow-v2-1-core` record is untouched by any scenario
here"). Step 4 is honored narrowly and correctly instead: this note *is*
that section's move into a factual "complete" state — there was never a
top-level "Active plan" entry for this item to clear. Step 7 ("Next
action") is unaffected: it already correctly points at the real
`docs/ROADMAP.md`, which `v2-1-dry-run` was never part of. This is filed
as an observation for `workflow-v2-1-core`'s own eventual, real
`/accept-milestone` invocation to account for (it likely faces the same
question, since it also has no `ROADMAP.md` entry), not as a blocking
defect — nothing about it left real repository state incorrect.

Not yet run: **S12** (approval/content-drift detection) and **S17**
(pointer restoration — `active_work_item_id` back to
`workflow-v2-1-core`, dry-run cleanup). `S13`-`S15` already ran earlier
in this dry run, interleaved with checkpoint implementation. **S11**
(legacy import/adoption) ran for real immediately below.

## `v2-1-dry-run-legacy` — functional review checklist (legacy adoption, S11)

This is a **throwaway synthetic item** created solely to exercise `D-Legacy`
phase 2 (`WF-M8b`)'s adoption transition for real
(`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`'s S11). It has no product
surface and no real implementation content — its `technical_approval`
(`basis: LEGACY_V1`) is fabricated evidence, not a real review, per S11's
own locked-in design decision. This item never adopts, reopens, or changes
`milestone-8`'s entry, which remains untouched throughout.

Per S11's own text, a full acceptance cycle for this item is optional and
is abbreviated here: the pass condition is the `LEGACY_READY` →
`AWAITING_FUNCTIONAL_REVIEW` transition itself
(`workflow_state.promote_legacy_work_item`, run for real), not a genuine
functional-review pass.

### Setup

None — no Android app / Gradle involvement, no build/install step.

### Automated verification

Not applicable. This item never ran `/milestone-implement` or
`/apply-implementation-review` — its `technical_approval` was established
directly by legacy import (`import_legacy_work_item`), not by that
pipeline, so there is no implementation-stage test suite scoped to it to
re-confirm.

### Test data

None to seed — this checks the repository's own real, already-committed
`docs/ai-workflow/WORKFLOW_STATE.json` state for this item.

### Flows to exercise manually

1. **Adoption transitioned the right fields, nothing else.** Confirm
   `work_items["v2-1-dry-run-legacy"]` now shows
   `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `governing_workflow_version: "2.1"`, and `active_work_item_id` ==
   `"v2-1-dry-run-legacy"`; confirm `technical_approval` is byte-identical
   to its state immediately after import (`basis: "LEGACY_V1"`,
   `reviewed_content_commit`/`approved_review_content_id` unchanged).
2. **Milestone 8 is untouched.** Confirm `work_items["milestone-8"]` is
   byte-identical to its state before this scenario ran — same
   `governing_workflow_version: "1"`, same `phase: "LEGACY_READY"`... no,
   `phase` for `milestone-8` is not `LEGACY_READY` (it was already
   promoted/accepted separately) — confirm whatever its actual current
   phase is remains unchanged by this scenario, since adoption ran only
   against `v2-1-dry-run-legacy`'s own resolved target.

### Expected result

Both checks above pass; no step requires any write beyond the one
adoption transition already performed (real, not simulated) and this
checklist section itself.

### Known limitations / out of scope for this review

- This item is fabricated evidence for a workflow-mechanism dry run, not a
  real product milestone — no functional flows exist to exercise beyond
  the state-transition checks above.
- **Discovered edge case, recorded rather than worked around**: this
  item's `implementation_revision` is `null` (a `LEGACY_V1`-imported item
  never passes through the ordinary `PLANNING`→`IMPLEMENTING` cycle that
  sets it). `/prepare-functional-review`'s step 3a reads
  `implementation_revision` live and folds it into both the
  `discover_current_functional_checklist_evidence` round-scope prefix and
  the `Workflow-Functional-Checklist` commit trailer; for this item that
  produces the literal prefix `v2-1-dry-run-legacy/None/` — mechanically
  functional (verified: `discover_current_functional_checklist_evidence`
  returns `None` cleanly with no exception when queried with
  `implementation_revision=None`, and its own round-scoped-prefix
  contract is symmetric enough that a repeat lookup with the same `None`
  value stays self-consistent), but semantically wrong for any
  `LEGACY_V1` item's evidence trailer. Not a blocker for S11's own pass/fail
  evidence (the adoption transition itself, checked above), and not
  fixed ad hoc here — filed for the same remediation routing S11's other
  discovered gap uses.

---

## `workflow-v2-3` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core`/`v2-1-dry-run`/`v2-1-dry-run-legacy` sections
above it. It tracks the separate, non-product process work item
`workflow-v2-3` (see `docs/ai-workflow/WORKFLOW_V2_3_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`), entering
`AWAITING_FUNCTIONAL_REVIEW` for the first time.

**Context**: `workflow-v2-3` adds two optional, report-only,
model-independent "second opinion" slash commands —
`/review-implementation` (usable at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`)
and `/review-functional` (usable at `AWAITING_FUNCTIONAL_REVIEW`, i.e. the
gate this checklist itself belongs to) — that give an operator a
repository-local advisory review before handing a stage to its real gate.
Neither command writes any state (`WORKFLOW_STATE.json`,
`REVIEW_FEEDBACK.md`, `FUNCTIONAL_REVIEW.md`, this file), approves a
stage, applies a finding, or advances `phase`; the hard-gate count stays
exactly 6 (`docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard gates
summary"). Two checkpoints (`CP1`, `CP2`), both `COMPLETE` — no
continued-scope remediation round is open, so this item's eventual
acceptance goes through `/accept-milestone` — which, since ledger `I10`
retired `/accept-scoped-remediation`, is the only acceptance command
there is.

Four implementation-review rounds ran before technical approval:
`GPT-IR1` (`REVISE`, 2 Important — both fixed), a second round whose
authoritative feedback was `/review-implementation`'s **own advisory
report, hand-copied for real** by the operator to
`.ai-review/feedback/REVIEW_FEEDBACK.md` (`RI2`, `REVISE`, 2 Important/3
Optional — one Important deferred with recorded reasoning, the rest
fixed), a third round using the same hand-copy mechanism (`RI3`,
`APPROVE`, 4 Optional, all fixed), and a final post-fix round (`APPROVE`,
0 Blocking/0 Important/2 Optional). `technical_approval` is now recorded
(commit `08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5`,
`Workflow-Technical-Approval:
b4ec046e8a2acc7144135a242c96b2d609a3a144ed844e7493cb04d3e4b8a7a9`, basis
`EXTERNAL_APPROVE`, generation-record commit
`096052650d6247b7c9ecdde4a35b0f0f54ddf7cf`).

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since the last full run at the round-4 generation-record
commit `0960526`, via the metadata-only technical-approval commit
`08c87ab`):

```
python3 -m unittest workflow_fingerprint_test workflow_fingerprint_generalization_test \
  workflow_state_test workflow_state_completion_obligations_test \
  workflow_integration_test workflow_test_harness_test
```

Result: **1115 tests, all green** (`OK`), 27.8s — matches
`TEST_RESULTS.md`'s round-4 total exactly.

```
python3 workflow_fingerprint_demo_test.py   # real-repository suite
python3 workflow_state_demo_test.py         # real-repository suite
```

Result: **15/15 green** (4 skipped, pre-existing/unrelated) and **45/45
green** respectively — both matching `TEST_RESULTS.md`'s round-4 result
exactly.

### Test data

None to seed — every check below reads this repository's own real,
already-committed state (`workflow-v2-3`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, the two new command files, and
this milestone's own real review history).

### Flows to exercise manually

1. **Re-run the automated suite yourself** (commands above, from
   `scripts/`) and confirm the same 1115/15/45 result independently,
   rather than trusting this document's claim alone.
2. **`/review-functional` — run it live, right now, against this exact
   checklist.** From the repo root, invoke `/review-functional
   workflow-v2-3` (or with no argument, since `active_work_item_id` is
   currently `workflow-v2-3`). Expected: a printed
   checklist-completeness/evidence-reproducibility advisory report (never
   a `Status: APPROVE | REVISE | BLOCK` verdict), which independently
   re-runs the automated-verification commands cited above and assesses
   manual-flow coverage against the CP1/CP2 diff. Confirm afterward, via
   `git status --porcelain`, that `docs/ai-workflow/WORKFLOW_STATE.json`,
   `.ai-review/feedback/REVIEW_FEEDBACK.md`,
   `.ai-review/feedback/FUNCTIONAL_REVIEW.md`, and this file are all
   byte-identical to before the command ran — the command's own
   write-nothing guarantee, exercised for real rather than taken on
   faith.
3. **`/review-implementation` — already exercised for real, twice, during
   this very milestone.** Unlike `/review-functional` above, no work item
   currently sits at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` to
   re-demonstrate it live against today. Instead, confirm the historical
   record: `IMPLEMENTATION_SUMMARY.md`'s "Review round 2 remediation" and
   "Review round 3 remediation" sections both state that round's
   authoritative feedback was `/review-implementation`'s own advisory
   report, hand-copied by the operator into
   `.ai-review/feedback/REVIEW_FEEDBACK.md` — i.e. this milestone's own
   `RI2`/`RI3` review rounds were real, substantive uses of the exact
   command being functionally reviewed here, not a simulation.
4. **Command surface is fully registered.** Confirm `.claude/commands/`
   contains exactly 16 files and `CLAUDE.md`'s "Slash commands" list names
   all 16, including `review-implementation` and `review-functional`
   (`grep -c '^' <(ls .claude/commands/*.md)` and a visual diff against
   `CLAUDE.md:85-86`).
5. **Hard-gate count is unchanged.** Confirm
   `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard gates summary" still
   lists exactly six gates and explicitly states that
   `/review-implementation`/`/review-functional` add no new one.
6. **The real `/approve-review implementation` flow, exercised for real
   this session**: gate-reachability check (worktree/HEAD match,
   `REJECTED`-marker absence, `bundle_id`/`review_content_id`
   recomputation, provenance-interval verification), `resolve_approval_basis`
   → `EXTERNAL_APPROVE`, a `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`08c87ab`) carrying
   the `Workflow-Technical-Approval`/`Workflow-Work-Item` trailers.
   Confirm independently:
   ```
   git show --stat 08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5
   git log -1 --format=%B 08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   b4ec046e8a2acc7144135a242c96b2d609a3a144ed844e7493cb04d3e4b8a7a9` /
   `Workflow-Work-Item: workflow-v2-3`.

### Expected result

All six checks above pass exactly as described. Checks 1, 3, 4, 5, and 6
are read-only or inspect commits that already exist (safe to repeat
freely); check 2 runs a report-only command and then verifies, rather
than assumes, that it wrote nothing.

### Known limitations / out of scope for this review

- No live re-demonstration of `/review-implementation` against a work
  item actually sitting at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`
  exists today (see check 3) — this milestone's own two real uses of it
  during rounds 2/3 are the available evidence instead.
- The plan-conformance gap in `/review-implementation` (no read of
  `<bundle_dir>/PLAN.md`/the item's `plan_path`) is a known, already-filed
  limitation (`docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md` item 4;
  `IMPLEMENTATION_SUMMARY.md`'s `RI2-002`) — not a defect introduced by
  this diff, and not blocking for this functional review.
- `docs/ai-workflow/WORKFLOW_V2_1_OPERATOR_REFERENCE.md`,
  `docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md`, and
  `docs/ai-workflow/diagrams/` are untracked, concurrent, out-of-scope
  scratch content — explicitly classified `excluded` in this item's own
  `implementation_stage` artifacts declaration; leave them unmodified
  during this review.
- Both new commands are advisory only and cannot replace this checklist's
  own manual walkthrough; `/review-functional` states this explicitly in
  its own printed report.

Findings go in `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.

### `workflow-v2-3` — MILESTONE_COMPLETE (real, 2026-08-23)

The user gave literal `/accept-milestone`-stage confirmation for
`workflow-v2-3` ("I confirm acceptance of workflow-v2-3."), validated for
real by `workflow_state.validate_user_confirmation`, after confirming they
had walked the functional-review checklist above. No finding was filed —
`.ai-review/feedback/FUNCTIONAL_REVIEW.md` was never created, unlike
`workflow-v2-1-core`'s own acceptance (which did have a written `Result:
PASS` record); this item's acceptance rests on the user's in-conversation
confirmation alone, per this command's own preamble, not on a separate
written artifact.

The advisory terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True` from phase
`AWAITING_FUNCTIONAL_REVIEW`) and the authoritative
`workflow_state.complete_work_item` call both succeeded: no
`IncompleteChildWorkItemError` (no work item declares `workflow-v2-3` as
its `parent_work_item_id`), no `IncompleteOwnCheckpointsError` (both
registry checkpoints, `CP1`/`CP2`, `COMPLETE`), and no
`UnsatisfiedCompletionObligationError` (`workflow-v2-3-registry.json`
declares no completion obligations — `resolve_completion_obligations`
returns `{}`, vacuously satisfied, same as `v2-1-dry-run`'s own
acceptance). `work_items["workflow-v2-3"].phase` is now
`MILESTONE_COMPLETE`; `active_work_item_id` resets to `null` (it pointed
here).

**Steps 3/5 do not apply** — the same gap `workflow-v2-1-core`'s and
`v2-1-dry-run`'s own completions already documented: `grep` confirms zero
mentions of `workflow-v2-3` anywhere in `docs/ROADMAP.md` (step 3 has no
entry to mark). `docs/ai-workflow/WORKFLOW_V2_3_PLAN.md` is **not**
archived to `docs/milestones/completed/` (step 5) — that directory holds
only real product-milestone execution/reference plan pairs (milestones
1-8); a process work item's own plan document was never the kind of
artifact it exists to hold, and `WORKFLOW_V2_3_PLAN.md` is referenced only
from this file's own `workflow-v2-3` section and this item's registry/
artifacts declarations, not linked as ongoing operator-facing guidance the
way `WORKFLOW_V2_PLAN.md` is. Step 4 is honored via this note itself,
placed in this item's own section rather than the top-level "Active plan"
section, which belongs to Milestone 8 alone and is untouched by this
completion. Step 7 ("Next action") is unaffected for the same reason both
prior completions' was: it already correctly points at the real
`docs/ROADMAP.md`, which `workflow-v2-3` was never part of, and explicitly
defers the next roadmap milestone (the Figma-led redesign) until the user
initiates it.

---

## `workflow-v2-3-followups` — functional review checklist (implementation revision 1)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core`/`v2-1-dry-run`/`v2-1-dry-run-legacy`/`workflow-v2-3`
sections above it. It tracks the separate, non-product process work item
`workflow-v2-3-followups` (see
`docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`), entering `AWAITING_FUNCTIONAL_REVIEW`
for the first time.

**Context**: a focused maintenance milestone closing three deferred
workflow-tooling defects recorded in `docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md`
after `workflow-v2-3` reached `MILESTONE_COMPLETE`, plus one explicitly
opportunistic fourth item. No `app/` code is touched at all — every change
lands in `.claude/commands/`, `docs/ai-workflow/`, and `scripts/`. Four
registry checkpoints (`CP1`-`CP4`), all `COMPLETE`, one commit each
(`570adc2`, `bac9237`, `5442ea6`, `5b5b9c7`), plus one self-review fix commit
(`994608f`) and this round's bundle-generation record (`54befc7`).

One external implementation-review round: `.ai-review/feedback/REVIEW_FEEDBACK.md`
returned `Status: APPROVE` (0 Blocking, 0 Important, 5 Optional — O1
through O5, all recorded as accepted trade-offs or documentation
observations, none a required acceptance criterion).
`technical_approval` was just recorded this session (commit `5ab2c99`,
`Workflow-Technical-Approval:
e8a4a76420995dc9eca3bd015267de890b32dc5d75684bd379833440816f2435`, basis
`EXTERNAL_APPROVE`).

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently, live, right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since `technical_approval.reviewed_content_commit`, `994608f`,
via the bundle-generation-record commit `54befc7` and this session's own
metadata-only approval commit `5ab2c99`; `git log 994608f..HEAD -- .
':!docs/ai-workflow/WORKFLOW_STATE.json'` is empty):

```
python3 -m unittest workflow_fingerprint_test workflow_fingerprint_generalization_test \
  workflow_state_test workflow_state_completion_obligations_test \
  workflow_integration_test workflow_test_harness_test
```

Result: **1146 tests, OK**, 27.973s.

```
python3 -m unittest workflow_state_demo_test         # 45/45, OK
python3 -m unittest workflow_fingerprint_demo_test    # 15/15, OK (skipped=4)
```

Total **1206**, zero failures/errors beyond the 4 pre-existing/unrelated
skips — matches `TEST_RESULTS.md`'s recorded result exactly.

### Test data

None to seed — every check below reads this repository's own real,
already-committed state (`workflow-v2-3-followups`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, the changed command/doc/script
files, and this milestone's own real review history).

### Flows to exercise manually

1. **Re-run the automated suite yourself** (commands above, from
   `scripts/`) and confirm the same 1146/45/15 result independently,
   rather than trusting this document's claim alone.
2. **CP1's merged staging-and-pin fix.** No live plan-stage approval with
   a fresh conditional fifth member (`<work_item>-artifacts.json`, both
   pending and fresh) is available to re-demonstrate right now — this
   item's own plan-stage approval already consumed the one naturally
   occurring opportunity, reproducing the pre-fix bug live before CP1
   landed (`IMPLEMENTATION_SUMMARY.md`'s CP1 section: "This work item's own
   plan-stage approval reproduced it live."). Confirm the fix instead
   through its own non-mocked regression coverage, run from `scripts/`:
   ```
   python3 -m unittest workflow_integration_test.TestPlanApprovalPermanentSiteEndToEnd.test_five_member_fixture_succeeds_through_the_real_merged_step_5
   ```
   Expected: `OK` (re-confirmed live for this checklist). The test drives
   a genuine five-member fixture through the real primitives to a
   committed, materialized, journal-closed outcome, not a mock.
3. **CP1's trailer final-paragraph requirement, stated in all three
   places it should be.** Confirm:
   ```
   grep -ln "OPUS-R129-001" .claude/commands/approve-review.md \
     .claude/commands/milestone-implement.md .claude/commands/bootstrap-workflow-v2.md
   ```
   Expected: all three files listed (the milestone added the sentence,
   citing this same marker, to `approve-review.md`; the other two already
   stated it citing the same marker). A plain `grep "final paragraph"`
   under-matches two of the three files, since the phrase wraps across a
   line break there — use the marker, not the phrase.
4. **CP2's real, live deliverable — the exact review that authorized this
   round.** Read `.ai-review/feedback/REVIEW_FEEDBACK.md` directly: confirm
   its `Status: APPROVE`, `Reviewed bundle ID:`, `Reviewed base commit:`,
   and `Work item:` fields, and that its "Independent verification
   performed" section documents real re-execution (fresh `bundle_id`/
   `review_content_id` recomputation, a full independent test re-run) —
   this file is itself `/review-implementation`'s own write for this
   round, not a simulation. No live cross-work-item ownership collision
   exists today to re-trigger the new `FeedbackOwnedByOtherWorkItemError`
   guard; its regression coverage
   (`test_different_work_item_refuses_naming_both`,
   `test_refused_run_for_b_leaves_as_own_feedback_byte_identical_and_creates_no_scoped_dir`,
   `test_a_may_overwrite_its_own_feedback_at_the_same_flat_path`) is the
   available evidence instead.
5. **CP3's canonical casing, live in this exact item's own record.**
   Confirm `docs/ai-workflow/WORKFLOW_STATE.json`'s
   `work_items["workflow-v2-3-followups"].plan_review_stages` shows
   `LOCAL_MODEL_PLAN_REVIEW` and `MANUAL_EXTERNAL_PLAN_REVIEW` as the
   literal keys (canonical `SCREAMING_SNAKE_CASE`, not the old lowercase
   tokens) — this is real live state the migration/normalization produced,
   not a fixture.
6. **CP4's cross-cutting sweep, re-run yourself.** Confirm no scope creep:
   ```
   grep -rn "AWAITING_LOCAL_IMPLEMENTATION_REVIEW\|quorum\|Reviewer role:" \
     .claude/commands/ docs/ai-workflow/ scripts/*.py
   ```
   Expected: every hit is prose explicitly declining the concept, or
   pre-existing plan-review-stage vocabulary (`Reviewer role:` in
   `record-manual-plan-review.md`) — none introduces a new lifecycle state
   or reviewer-quorum mechanism. Then confirm the hard-gate count is
   still exactly six: `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard
   gates summary" section.
7. **The self-review fix (`994608f`).** Read
   `docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Local reviewer commands"
   section: confirm it now states `/review-functional`'s report has no
   authoritative round to become and is not destined for
   `REVIEW_FEEDBACK.md`, pointing the operator at revising
   `FUNCTIONAL_REVIEW.md` by hand instead — not the pre-fix wording that
   let this sentence read as true of `/review-functional` when it was
   only ever true of `/review-implementation`.
8. **The real `/approve-review implementation` flow, exercised for real
   this very session.** Gate-reachability check (worktree/HEAD match,
   `REJECTED`-marker absence, `bundle_id`/`review_content_id`
   recomputation matching `REVIEW_FEEDBACK.md` exactly, implementation
   provenance-interval verification), `resolve_approval_basis` →
   `EXTERNAL_APPROVE`, a `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`5ab2c99`) carrying
   the trailers. Confirm independently:
   ```
   git show --stat 5ab2c99
   git log -1 --format=%B 5ab2c99
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   e8a4a76420995dc9eca3bd015267de890b32dc5d75684bd379833440816f2435` /
   `Workflow-Work-Item: workflow-v2-3-followups`.
9. **Command surface is fully registered.** Confirm `.claude/commands/`
   contains exactly 16 files and `CLAUDE.md`'s "Slash commands" list names
   all 16.

### Expected result

All nine checks above pass exactly as described. Checks 1, 3, 5, 6, 7, 8,
and 9 are read-only or inspect commits/state that already exist (safe to
repeat freely); checks 2 and 4 point at the strongest available evidence
(a real non-mocked regression test; this round's own real review artifact)
where a fresh live re-demonstration of the exact original trigger is not
available today.

### Known limitations / out of scope for this review

- No live re-demonstration of CP1's five-member plan-stage-approval branch
  or CP2's cross-work-item ownership-guard collision exists today — see
  checks 2 and 4 above for the available evidence instead.
- Required follow-up #8 (operator reference / lifecycle diagram sync,
  `WORKFLOW_V2_3_FOLLOWUPS.md` item 2) remains deferred. The two artifacts
  involved — `docs/ai-workflow/WORKFLOW_V2_1_OPERATOR_REFERENCE.md` and
  `docs/ai-workflow/diagrams/` — are untracked working-tree leftovers,
  declared `excluded` in this item's own artifacts declaration and left
  unmodified throughout this review, per `CLAUDE.md`'s "don't touch
  unrelated working-tree changes".
- The five Optional findings in `REVIEW_FEEDBACK.md` (O1-O5) are
  non-blocking commentary, not defects requiring a fix before functional
  review; none is a required acceptance criterion for this round. O1 in
  particular (the implementation-review gate's now-self-satisfiable
  character) was explicitly considered and accepted at plan stage
  (`LPR-R2-B01`).

Findings go in `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.

---

## `workflow-v2-3-followups` — functional review checklist (implementation revision 4)

This section supersedes the implementation-revision-1 section immediately
above for review purposes -- that section is left in place as historical
record, not edited. This is the same work item re-entering
`AWAITING_FUNCTIONAL_REVIEW` after a real functional-review finding was
filed against revision 1's content, worked through three further
implementation-review rounds, and re-approved.

**Context**: while walking revision 1's checklist above (specifically,
running `/accept-milestone`'s own advisory pre-flight), a real defect was
found and filed as `.ai-review/feedback/FUNCTIONAL_REVIEW.md`'s Finding 1:
both `plan_approval.review_content_manifest` and
`technical_approval.review_content_manifest` had been stored as the
*entire* `compute_review_content_id_*_stage` projection object instead of
that object's own inner flat `review_content_manifest` list, so
`resolve_own_registry_completion_status` crashed with a bare
`AttributeError: 'str' object has no attribute 'get'` instead of a typed
refusal. Per `D-Functional-Remediation`'s bounded-fix branch, three
Optional findings from this item's own round-1 implementation review (O2,
O3, O5) were folded into the same round at the user's direction; O1 and O4
were explicitly declined as out of scope.

That single finding drove three further implementation-review rounds
(`implementation_revision` `1` -> `2` -> `3` -> `4`), each fixing what the
previous round's own review found still incomplete:

- **Round 2** added the shape check to `_validate_work_item`
  (`validate_state`'s own call path) -- exactly what the finding asked for
  on its face.
- **Round 3**'s review (Blocking) found that `validate_state`/
  `_validate_work_item` has **no production caller anywhere in this
  repository** -- not `state_transaction`, not any `.claude/commands/*.md`
  step -- so the read-side crash the finding actually reproduced was still
  live. Round 3 corrected two overstated claims but had not yet reached
  round 4's real fix.
- **Round 4** (this revision) closed it for real, following
  `OPUS-R25-007`'s precedent: the shape-check logic was factored into one
  shared helper, `_describe_malformed_review_content_manifest_shape`, used
  at all three sites -- `validate_approval_record` (the write chokepoint,
  unchanged behavior), `_assert_registry_covered_by_current_plan_approval`
  (the live-state consumer, the exact `/accept-milestone` step 2a entry
  point, now raising `StalePlanApprovalRegistryReadError`), and
  `_resolve_one_obligation` (the committed-blob consumer -- `durable_record`
  is read from immutable Git history, so a manifest malformed at approval
  time reaches it regardless of any live-file fix -- now returning
  `ObligationVerdict("VERIFIER_UNAPPROVED", ...)`). Commit `6827417` (B1).
  A small, unrelated documentation reword (item 313's stale
  three-combination count language) landed alongside it as commit `2302cc4`
  (O1, from round 3's own review).

A fresh `/review-implementation` round against round 4's bundle returned
`Status: APPROVE` -- 0 Blocking, 0 Important, 2 Optional (both explicitly
non-blocking; see "Known limitations" below).
`technical_approval` is now recorded (commit
`e84da52d153390d41c423b98dd04eb8123b1e798`, `Workflow-Technical-Approval:
55b3f4d0322dccc7e2ece1ab3fd6ca17e0ec62b1bbc9b7bd14e240ac476cb5c8`, basis
`EXTERNAL_APPROVE`), preceded by the bundle-generation-record commit
`e121d2d91537a76abd0453180d56fc58eb8ef594`
(`Workflow-Bundle-Generation-Record: workflow-v2-3-followups/4`).

**Checklist correction (2026-08-24, same round -- `implementation_revision`
stays `4`, `technical_approval` untouched)**: an independent `/review-functional`
advisory pass over this exact section found it under-covered five
behaviorally significant fixes that landed between the round-1 and round-4
bundles -- all real, all inside `technical_approval`'s own reviewed content
(`2302cc4`), none previously given their own flow here -- plus four wording/
evidence defects in the checklist prose itself. Both are corrected in place
below rather than superseding this section with a new one, since no new
implementation round ran: six new flows (2 through 7) were added for
`c41eaf8` (O5, `/review-implementation` pre-write self-validation), `31dabbd`
(the self-discovered stage-aware `record_bundle_generation` fix), `910afd7`
(B1, WF8c evidence-pointer repair), `5b2ca7e`/`be748c3` (I1, the
`approve-review.md`/`recover-implementation-provenance.md` bundle-generation
contract reword), `bb7ecb7` (I2, persisted-approval-record shape
validation), and `20206a6` (O3, `FUNCTIONAL_REVIEW.md` consumed-marking);
the automated-verification freshness claim above, Setup's toolchain claim
above, the old flow 3 grep command (now flow 10, after the second
correction pass below inserted a further flow ahead of it), and the old
flow 8 command surface/hard-gate check (now flow 15) are each corrected
below at the point they appear, with the reason noted inline. No `app/`,
`scripts/`, or `.claude/commands/` file changed in this pass -- confirmed
by the scope-discipline flow (now flow 14) and by
`workflow_state.approval_is_current` staying `True` throughout (see
"Automated verification" below); only this file changed.

**Checklist correction, pass 2 (2026-08-24, same round -- `implementation_revision`
stays `4`, `technical_approval` untouched)**: a follow-up pass added one
further flow and fixed one further wording defect, neither found by
re-litigating anything the first pass above already settled. **New flow 8**
verifies the real *data*-repair half of the original Finding 1 --
`b0ee6a9`'s live, already-persisted `plan_approval`/`technical_approval`
manifests, and the real `resolve_own_registry_completion_status` pre-flight
primitive against them -- since every flow the first pass added (6 and, at
the time, 8) only exercised the *code*-level guard against a hand-built
malformed fixture, never the live record itself; inserting it ahead of the
old flow 8 shifts flows 9 through 14 down to 10 through 15 (cross-references
throughout this section are updated to match). **Flow 7** (unaffected by
the renumbering -- it already sat at position 7 before and after) is
corrected so each of its three commands resolves the repository root itself
(`git rev-parse --show-toplevel`) rather than relying on the operator's
current shell location, which a prior version left ambiguous across three
inconsistent directory instructions. Every command below -- new, renumbered,
or reworded -- was re-run live and produced its stated result before this
paragraph was written.

### Setup

No Android app / Gradle changes are involved in this round's own diff --
this is process tooling only (`scripts/*.py`, `.claude/commands/*.md`,
`docs/ai-workflow/*`). Every manual flow below (2 through 15) runs with
`python3` from the repo root (`scripts/` for the checks that `cd` there);
none needs an Android app install or a device/emulator. **This does not
mean the checklist has no build-toolchain requirement at all** (a prior
version of this section claimed "everything below runs with `python3`",
which was true of the manual flows but not of the checklist as a whole):
the "Automated verification" section immediately below re-runs
`./gradlew spotlessCheck detekt lintDebug testDebugUnitTest` as one of its
required commands, and that needs this repository's normal Gradle/Android/
JDK toolchain (`AGENTS.md`'s "Build/test/lint commands") installed and on
`PATH`, even though this round touches no `app/` file -- the gate still
runs, it is just expected to report every task `UP-TO-DATE` (or, on a
worktree where the daemon has not run these tasks yet in this exact
session, a fast no-op rebuild) rather than recompiling anything.

### Automated verification (re-confirmed live this session, current)

**Freshness claim, corrected**: the prior wording here (`git diff --stat
2302cc4..HEAD`) was unstable -- `HEAD` moves every time a further
checklist-evidence commit lands (this correction's own commit included),
so the same command produces a different, increasingly misleading answer
each time it is re-run, even though nothing implementation-stage has
actually changed. The stable, reproducible replacement is the real
production freshness check `/approve-review implementation` and
`/accept-milestone`'s own pre-flight both use --
`workflow_state.approval_is_current` -- which recomputes the
implementation-stage `review_content_id` fresh from live `HEAD`'s
committed content (scoped to this item's own declared protected/excluded
paths, `docs/ai-workflow/registry/workflow-v2-3-followups-artifacts.json`)
and compares it to `technical_approval.approved_review_content_id`, rather
than diffing against a moving target:

```
python3 -c "
import sys, pathlib, json
sys.path.insert(0, 'scripts')
import workflow_state as ws
repo_root = pathlib.Path('.').resolve()
wi = json.loads((repo_root/'docs/ai-workflow/WORKFLOW_STATE.json').read_text())['work_items']['workflow-v2-3-followups']
print(ws.approval_is_current(repo_root, wi, stage='implementation', base_commit=wi['base_commit'], head='HEAD'))
"
```

Expected/observed: `True` (re-confirmed live immediately before writing
this correction) -- proving no protected implementation-stage content has
changed since `technical_approval.reviewed_content_commit`, `2302cc4`,
regardless of how many further checklist-only commits (`e121d2d`,
`e84da52`, `77060f3`, and this correction's own) have since advanced
`HEAD`. This command remains valid to re-run at any later point for the
same reason: it always answers the live question, never a stale range.

```
python3 -m unittest discover -s scripts -p "workflow_*_test.py" -t scripts
```

Result: **1230 tests, OK (skipped=4)**, 121.7s -- matches
`TEST_RESULTS.md`'s round-4 total exactly. Re-run live for this correction,
not carried over from the prior session's claim.

```
python3 -m unittest workflow_state_demo_test        # from scripts/: 46/46, OK, 93.1s
python3 -m unittest workflow_fingerprint_demo_test   # from scripts/: 15/15, OK (skipped=4)
```

```
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
```

Result: **BUILD SUCCESSFUL**, 45 actionable tasks (1 executed -- `lintDebug`
itself, not previously run in this exact session/daemon lifetime -- 44
up-to-date) -- fully up-to-date in substance, as expected, since this round
(and this correction) touches no `app/` file; the exact executed/up-to-date
split depends on this machine's own Gradle daemon/build-cache state at the
moment the gate runs, not on any repository content change, so a different
split (e.g. all 45 up-to-date) on a fresh checkout is not itself a defect.

### Test data

None to seed -- every check below reads this repository's own real,
already-committed state (`workflow-v2-3-followups`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, the changed script/test/registry
files, and this round's own real review history). Flows 3 and 7 below
additionally point at existing `ScratchRepo`-isolated regression tests
(real Git commits inside a disposable temporary repository, never mocks)
for the one behavior neither can safely re-demonstrate live against this
item's own real state without actually mutating it -- consistent with how
checks 2/4 in the revision-1 checklist above already handle the same
"no live opportunity exists today" situation.

### Flows to exercise manually

1. **Re-run the automated suite yourself** (commands above, from the repo
   root and from `scripts/` as noted) and confirm the same 1230/46/15
   result and a successful Gradle gate independently, rather than trusting
   this document's claim alone.
2. **O5 -- `/review-implementation` now self-checks its own write before
   making it.** `c41eaf8` closed round-1's own Optional finding O5: step 6
   called the binding-field shape (`bundle_id`/`base_commit`/`work_item_id`)
   a "hard precondition" of step 7's write that step 7 never actually
   enforced. Confirm the command file states the enforcement, immediately
   before the write, not merely the precondition:
   ```
   grep -n "Self-check the composed text" .claude/commands/review-implementation.md
   grep -c "parse_review_feedback_binding_fields" .claude/commands/review-implementation.md
   ```
   Expected: one hit for the first (step 7's own heading, line 237) and
   `2` for the second (step 6's precondition statement plus step 7's actual
   enforcement call -- both now present, closing the gap between them).
   Then confirm the two primitives step 7 calls
   (`parse_review_feedback_binding_fields`/`assert_feedback_matches_bundle`)
   are themselves correct, from `scripts/`:
   ```
   python3 -m unittest workflow_fingerprint_test.TestFeedbackBindingFields
   ```
   Expected: `OK` (7 tests). This self-check has also already run for real,
   live, every time `/review-implementation` wrote this exact round's own
   `.ai-review/feedback/REVIEW_FEEDBACK.md` (rounds 2 through 4, all after
   `c41eaf8` landed) -- had it failed, that file would never have been
   written with matching binding fields, which check 12 below independently
   confirms it was.
3. **The self-discovered fix that made Finding 1's own bounded-fix branch
   reachable at all -- `record_bundle_generation`'s legality is now
   stage-aware.** `31dabbd` is not a review-finding fix like the others in
   this list; it was discovered *while literally executing*
   `/apply-functional-review`'s bounded branch for this item's own
   Finding 1 (`OPUS-R101-001`'s phase-transition contract, landed after the
   only prior real exercise of this branch, had made
   `record_bundle_generation(stage="post-fix", ...)` structurally
   unreachable from `AWAITING_FUNCTIONAL_REVIEW` -- the *only* phase that
   branch is ever invoked from). Re-demonstrating this live against this
   item's own real state is not safe (it would require staling a `CURRENT`
   technical approval this checklist itself depends on), so this exercises
   the same sequence end-to-end inside an isolated `ScratchRepo` fixture --
   real Git commits, the real production functions, not mocks. From
   `scripts/`:
   ```
   python3 -m unittest workflow_state_test.TestFunctionalReviewBoundedFixReachesRecordBundleGeneration
   ```
   Expected: `OK` (4 tests) --
   `test_bounded_fix_from_awaiting_functional_review_reaches_external_review_durably`
   drives the full sequence for real (`AWAITING_FUNCTIONAL_REVIEW` with
   `CURRENT` technical_approval -> `mark_technical_approval_stale` ->
   a real protected-content commit -> `record_bundle_generation(stage=
   "post-fix", ...)` -> `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`, closed
   by a real durability commit);
   `test_post_fix_from_awaiting_functional_review_refused_unless_stale` and
   `test_post_fix_from_awaiting_functional_review_with_no_technical_approval_refused`
   prove the `STALE` gate is a hard precondition, not merely the phase;
   `test_ordinary_implementation_stage_generation_still_refused_from_awaiting_functional_review`
   proves this widening never legalizes `stage="implementation"` from the
   same phase. No new lifecycle state, review stage, or phase was added by
   this fix -- confirm check 15 below still finds exactly six hard gates.
4. **B1 (round 2) -- the WF8c evidence pointers `31dabbd`'s own class
   rename broke are repaired.** `31dabbd` renamed
   `workflow_state_test.TestRecoveredRoleThreeCombinationValidation` to
   `TestRecoveredRoleLegalParentPhaseCombinations` (a fourth legal parent
   phase made the count-based name stale), but left four evidence
   identifiers in `workflow-v2-1-core-wf8c-evidence.json` (items
   292/293/294/313) pointing at the old name -- a real regression for a
   different, already-`MILESTONE_COMPLETE` item's own evidence, not a
   hypothetical one. Confirm the pointers now read the current class name:
   ```
   grep -c '"evidence": "workflow_state_test.TestRecoveredRoleLegalParentPhaseCombinations' docs/ai-workflow/registry/workflow-v2-1-core-wf8c-evidence.json
   grep -c '"evidence": "workflow_state_test.TestRecoveredRoleThreeCombinationValidation' docs/ai-workflow/registry/workflow-v2-1-core-wf8c-evidence.json
   ```
   Expected: `4` and `0`. Note: the bare (unscoped) form of these two greps
   returns `5`/`1`, not `4`/`0` -- the evidence file's own explanatory note
   for item 292 legitimately mentions the old class name once, narrating
   that it was "renamed from `TestRecoveredRoleThreeCombinationValidation`"
   (historical prose, not a stale pointer); scoping to `"evidence": "..."`
   lines isolates the four pointers that actually matter. Then confirm the
   pointed-at tests still resolve and pass for real against a pinned
   worktree -- the same real production consumer
   (`workflow_state_demo_test.TestReconciliationTableLedgerStatusAgreement`)
   that this repair keeps from failing, already re-run as part of the
   46/46 `workflow_state_demo_test` total in check 1 above; to isolate just
   this piece (from `scripts/`, ~90s):
   ```
   python3 -m unittest workflow_state_demo_test.TestReconciliationTableLedgerStatusAgreement
   ```
   Expected: `OK` (4 tests).
5. **I1 -- the bundle-generation contract's command-file prose no longer
   describes a stale two-phase model.** `5b2ca7e` (further reworded by
   `be748c3` to avoid perturbing an exact-occurrence-count regression, see
   below) corrected `approve-review.md`'s step-4a1 note and three spots in
   `recover-implementation-provenance.md`, all of which still described
   `record_bundle_generation` as legal from only two source phases after
   `31dabbd` (flow 3 above) added the third. Confirm the stale phrasing is
   gone and the corrected, stage-aware phrasing is present in both files:
   ```
   grep -n "own two entry phase\|own two entry point" .claude/commands/approve-review.md .claude/commands/recover-implementation-provenance.md
   grep -c "legal source phases\|legality is stage-specific" .claude/commands/approve-review.md .claude/commands/recover-implementation-provenance.md
   ```
   Expected: the first produces no output at all (both stale phrasings
   fully replaced, not merely supplemented); the second reports at least
   one hit in each file. Then confirm `be748c3`'s own reason for existing
   still holds -- I1's first draft accidentally named
   `assert_bundle_not_rejected` inline in `approve-review.md`, bumping that
   literal's occurrence count from 2 (its real call sites) to 3 and
   breaking the regression that counts it as a call-site proxy; the reword
   cites the guard by name (`WFR-67`) instead:
   ```
   python3 -m unittest workflow_state_demo_test.TestReviewSubjectDeclarationsLive.test_every_non_exempt_file_calls_the_shared_assertion_the_expected_number_of_times
   python3 -m unittest workflow_integration_test.TestGoldenCommandFileHashes.test_every_roster_command_file_matches_its_recorded_hash
   ```
   Expected: both `OK` -- the second additionally confirms `approve-review.md`'s
   own golden hash was updated to match, so this wording is exactly what
   `technical_approval` was recorded against, not a later untracked edit.
6. **I2 -- `validate_state` itself now rejects a malformed persisted
   `plan_approval`/`technical_approval`, not just a newly constructed
   one.** `bb7ecb7` added the shape check to `_validate_work_item`
   (`validate_state`'s own call path), so a malformed record already
   sitting in `WORKFLOW_STATE.json` (an old backup, import, hand edit, or
   pre-fix tooling -- not merely one built fresh via `build_approval_record`,
   which `validate_approval_record` already guarded) is now rejected by
   `validate_state` with a typed `InvalidApprovalRecordError`. **Scope
   correction, since `bb7ecb7`'s own commit message overstates this**: its
   message claims this closes the live `/accept-milestone` crash path at
   `_assert_registry_covered_by_current_plan_approval` -- round 3's own
   review (Blocking, see "Context" above) found this **false**:
   `validate_state`/`_validate_work_item` has no production caller
   anywhere in this repository, so `_assert_registry_covered_by_current_plan_approval`
   (confirmed: defined independently at a different line, calls neither
   function) never runs this check at all. That live crash path was closed
   for real only by flow 9 below (`6827417`, round 4). I2's own real,
   narrower value -- `validate_state` itself behaving correctly whenever
   something does call it (tests, a future migration/import tool, or a
   manual sanity check) -- is what this flow verifies, not the production
   crash path. From `scripts/`:
   ```
   python3 -m unittest workflow_state_test.TestStateValidation.test_valid_persisted_plan_approval_passes_state_validation workflow_state_test.TestStateValidation.test_valid_persisted_technical_approval_passes_state_validation workflow_state_test.TestStateValidation.test_malformed_persisted_plan_approval_manifest_rejected_by_state_validation workflow_state_test.TestStateValidation.test_malformed_persisted_technical_approval_manifest_rejected_by_state_validation
   ```
   Expected: `OK` (4 tests) -- the first two prove a well-formed persisted
   record still passes unchanged; the second two construct the exact
   historical malformed shape (the whole compute-function projection
   object, not a synthetic stand-in) directly inside a work item's
   `plan_approval`/`technical_approval` and assert `validate_state` raises
   `InvalidApprovalRecordError` -- proving `validate_state`'s own
   read/validation path, distinct from flow 9 below's two real production
   consumer sites. Confirm the two are genuinely separate call paths:
   ```
   grep -n "^def _assert_registry_covered_by_current_plan_approval\|^def _validate_work_item\|^def validate_state" scripts/workflow_state.py
   ```
   Expected: three well-separated `def` lines (currently 6494, 11093,
   11148) -- `_assert_registry_covered_by_current_plan_approval` is not
   nested inside, and does not call, `_validate_work_item`/`validate_state`.
7. **O3 -- an applied `FUNCTIONAL_REVIEW.md` is now marked consumed in a
   content-hash-bound way, so it is never re-read as fresh findings on a
   later pass, and this checklist introduces no new lifecycle state or
   stage to do it.** `20206a6` closed round-1's own Optional finding O3:
   before this fix, `FUNCTIONAL_REVIEW.md` remained in place after its
   findings were applied, so a later return to `AWAITING_FUNCTIONAL_REVIEW`
   could re-read already-remediated findings as though they were fresh --
   exactly what happened once already during this item's own first bounded
   fix (Finding 1). Live, real, read-only evidence exists for this today,
   using this item's own actual `.ai-review/feedback/FUNCTIONAL_REVIEW.md`
   (Finding 1's real content, still on disk) and its real marker file.
   **Every command below is self-contained**: each resolves the repository
   root itself via `git rev-parse --show-toplevel` and `cd`s there (or into
   `scripts/` beneath it) inside its own subshell, so it runs correctly
   regardless of whether the operator's shell happens to be at the repo
   root, inside `scripts/`, or anywhere else in the working tree when it is
   pasted -- a prior version of this flow instead narrated "run from the
   repo root" for one command and "from `scripts/`" for another, then gave
   a third with no directory statement at all, which silently broke if
   pasted right after the second (looking for the nonexistent
   `scripts/scripts/workflow_fingerprint.py`):
   ```
   (cd "$(git rev-parse --show-toplevel)" && python3 -c "
   import sys, pathlib
   sys.path.insert(0, 'scripts')
   import workflow_fingerprint as wf
   repo_root = pathlib.Path('.').resolve()
   try:
       wf.assert_functional_review_not_already_consumed(repo_root, 'workflow-v2-3-followups')
       print('did not raise -- unexpected')
   except wf.FunctionalReviewAlreadyAppliedError as e:
       print('raised as expected:', str(e)[:80])
   ")
   ```
   Expected: `raised as expected: ...` -- this is the exact production
   primitive `/apply-functional-review` step 1 calls, run live against this
   item's own real, already-applied `FUNCTIONAL_REVIEW.md`/`.consumed`
   marker pair, proving both that the marker is genuinely content-hash-bound
   (`git hash-object .ai-review/feedback/FUNCTIONAL_REVIEW.md` matches the
   marker file's own recorded hash) and that this exact content would be
   correctly refused as stale findings if `/apply-functional-review` were
   invoked again right now. This call only reads and raises -- it writes
   nothing, safe to repeat freely. For the negative path (genuinely new
   findings must *not* be refused) and the full set of marker invariants,
   which cannot safely be demonstrated against this item's own live,
   already-consumed file without overwriting it, run the isolated
   regression suite instead:
   ```
   (cd "$(git rev-parse --show-toplevel)/scripts" && python3 -m unittest workflow_fingerprint_test.TestFunctionalReviewConsumedMarker)
   ```
   Expected: `OK` (7 tests), including
   `test_passes_when_content_changed_since_the_marker_was_written` (a
   genuinely new round's findings are never refused as stale) and
   `test_marking_twice_for_the_same_content_stays_idempotent`. Confirm no
   new lifecycle state was added to do any of this:
   ```
   (cd "$(git rev-parse --show-toplevel)" && grep -n "phase" scripts/workflow_fingerprint.py | grep -i "consumed\|functional_review")
   ```
   Expected: no output -- `resolve_functional_review_consumed_marker_path`/
   `assert_functional_review_not_already_consumed`/
   `mark_functional_review_consumed` touch a plain sentinel file only, never
   `phase` or any other state field (also confirmed structurally by check 15
   below still finding exactly six hard gates).
8. **The real data-repair half of Finding 1 -- the live
   `review_content_manifest` shape actually holds today, in this item's
   own real `WORKFLOW_STATE.json`, not only in a synthetic fixture.**
   Flows 6 and 9 (I2 and B1 round 3) each prove the *code* now rejects a
   hand-built malformed shape; neither touches this item's own real,
   already-persisted `plan_approval`/`technical_approval` records, so
   neither actually confirms the live data was repaired. `b0ee6a9` is that
   repair: it unwrapped both fields from the full
   `compute_review_content_id_*_stage` projection object down to that
   object's own inner flat `review_content_manifest` list, verified
   byte-identical `path`/`exists`/`mode`/`blob` values throughout (diffed
   against the pre-repair file) -- not a new approval, no
   `Workflow-Plan-Approval`/`Workflow-Technical-Approval` trailer. Confirm
   the live shape directly, and that the real `/accept-milestone`
   pre-flight primitive the original crash came from now succeeds against
   it, run from the repo root:
   ```
   python3 -c "
   import sys, pathlib, json
   sys.path.insert(0, 'scripts')
   import workflow_state as ws
   repo_root = pathlib.Path('.').resolve()
   wi = json.loads((repo_root/'docs/ai-workflow/WORKFLOW_STATE.json').read_text())['work_items']['workflow-v2-3-followups']

   def is_flat_manifest(m):
       return isinstance(m, list) and all(
           isinstance(e, dict) and {'path', 'exists', 'mode', 'blob'}.issubset(e.keys()) for e in m
       )

   pm = wi['plan_approval']['review_content_manifest']
   tm = wi['technical_approval']['review_content_manifest']
   print('plan_approval.review_content_manifest: list of', len(pm), '-- flat shape:', is_flat_manifest(pm))
   print('technical_approval.review_content_manifest: list of', len(tm), '-- flat shape:', is_flat_manifest(tm))

   result = ws.resolve_own_registry_completion_status(repo_root, wi)
   print('resolve_own_registry_completion_status:', result)
   assert result == (True, None), result
   print('all checks passed')
   "
   ```
   Expected: `plan_approval.review_content_manifest: list of 3 -- flat
   shape: True`; `technical_approval.review_content_manifest: list of 18
   -- flat shape: True`; `resolve_own_registry_completion_status: (True,
   None)`; `all checks passed` -- the exact real production pre-flight
   primitive `complete_work_item` and `/accept-milestone`'s own advisory
   pre-flight both call, invoked against this item's own real, live work
   item dict (not a hand-built fixture), returning cleanly rather than
   crashing with the original bare `AttributeError`. This call only reads
   `WORKFLOW_STATE.json` and the item's own registry file -- it writes
   nothing, safe to repeat freely.
9. **B1 (round 3) -- the actual `/accept-milestone`-crash defect is closed
   at both real production consumers.** Run, from `scripts/`:
   ```
   python3 -m unittest workflow_state_test.TestRegistryReadBoundToCurrentPlanApproval.test_malformed_live_plan_approval_manifest_rejected_cleanly
   python3 -m unittest workflow_state_completion_obligations_test.TestResolveCompletionObligationsPipeline.test_malformed_committed_review_content_manifest_rejected_cleanly
   ```
   Expected: both `OK`. The first plants the malformed shape in a live-state
   work item and calls `resolve_own_registry_completion_status` directly
   (the exact `/accept-milestone` step 2a entry point that originally
   crashed); the second commits the malformed shape for real, with a
   `Workflow-Technical-Approval` trailer, and calls
   `resolve_completion_obligations` against that committed blob -- proving
   the fix holds even for a manifest that was already malformed in
   immutable history, not just in the live file.
10. **No caller downgrades the new refusal -- command corrected to count
    only the four `raise` sites, not every mention of the class.** The prior
    wording here (a bare `grep -n "StalePlanApprovalRegistryReadError"`)
    under-specifies its own expected result: that pattern also matches the
    class definition itself and one docstring reference, so a plain hit
    count is `6`, not `4`, and the operator was left to manually pick the
    four `raise` sites out by eye. Scope the grep to what actually matters:
    ```
    grep -n "raise StalePlanApprovalRegistryReadError" scripts/workflow_state.py
    grep -c "raise StalePlanApprovalRegistryReadError" scripts/workflow_state.py
    grep -n "except.*StalePlanApprovalRegistryReadError" scripts/workflow_state.py
    ```
    Expected: the first lists exactly four lines (currently 6530, 6538, 6547,
    6559), the second reports `4`, and the third produces no output (the
    class appears in no `except` clause anywhere in the file -- both call
    sites in `complete_work_item`/`work_item_completion_status` let it
    propagate uncaught). Confirm all four listed lines fall between
    `_assert_registry_covered_by_current_plan_approval`'s own `def` line and
    the next top-level `def` after it.
11. **The two round-4 Optional findings are correctly left open, not
    silently dropped.** Read `.ai-review/feedback/REVIEW_FEEDBACK.md`'s
    "Optional findings" section: confirm O1 (the shape helper does not
    constrain the `path` value's own type, a narrow residual gap in the
    committed-blob consumer) and O2 (a tautological
    `assertNotIsInstance` in one regression test) are both still named
    there, and that "Required acceptance criteria" reads "None. This bundle
    is approvable as it stands."
12. **The real `/approve-review implementation` flow, exercised for real
    this session.** Confirm independently:
    ```
    git show --stat e84da52d153390d41c423b98dd04eb8123b1e798
    git log -1 --format=%B e84da52d153390d41c423b98dd04eb8123b1e798
    ```
    Expected: exactly one file changed
    (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
    `Workflow-Technical-Approval:
    55b3f4d0322dccc7e2ece1ab3fd6ca17e0ec62b1bbc9b7bd14e240ac476cb5c8` /
    `Workflow-Work-Item: workflow-v2-3-followups`.
13. **The bundle-generation-record commit is likewise metadata-only.**
    Confirm:
    ```
    git show --stat e121d2d91537a76abd0453180d56fc58eb8ef594
    ```
    Expected: exactly one file changed
    (`docs/ai-workflow/WORKFLOW_STATE.json`), trailer
    `Workflow-Bundle-Generation-Record: workflow-v2-3-followups/4`.
14. **Scope discipline for this specific remediation round.** Confirm:
    ```
    git diff 3b05bf8..HEAD -- app/
    git diff 3b05bf8..HEAD -- .claude/commands/
    ```
    Expected: both empty -- round 4 (`3b05bf8`..`2302cc4`) touches only
    `scripts/workflow_state.py`, its two test files, and one registry JSON
    reword (O1), matching `TEST_RESULTS.md`'s own account exactly. This
    correction pass itself (`2302cc4`..current) touches neither `app/` nor
    `.claude/commands/` either -- confirm the same two commands against
    `2302cc4..HEAD` report the same two empty results.
15. **Command surface and hard-gate count are unchanged -- now with
    concrete, runnable commands rather than narrative alone.** Run, from
    the repo root:
    ```
    ls .claude/commands/*.md | wc -l
    diff <(ls .claude/commands/*.md | xargs -n1 basename -s .md | sort) \
         <(sed -n '80,86p' CLAUDE.md | grep -o '`[a-z0-9-]*`' | tr -d '`' | sort)
    grep -c '^[0-9]\. `AWAITING' docs/ai-workflow/MILESTONE_WORKFLOW.md
    ```
    Expected: `16`; an empty diff (exit `0`) -- the command-file roster and
    `CLAUDE.md`'s "Slash commands" list name exactly the same 16 commands;
    and `6`. Do **not** grep for the phrase "hard gate count stays exactly"
    to confirm the six-gate total -- `docs/ai-workflow/MILESTONE_WORKFLOW.md`
    wraps that exact phrase across a line break (`... The hard\ngate count
    stays exactly **6**.`), so a plain phrase grep under-matches it, the
    same wrapping gotcha the revision-1 checklist above already documented
    for a different marker; the numbered-list count above is the
    unambiguous, non-wrapping signal.

### Expected result

All fifteen checks above pass exactly as described. None writes to the
real repository: checks 1, 5 (second half), 8, and 9 through 15 are
read-only greps/diffs/live-primitive calls or inspect commits/state/files
that already exist (check 8's `resolve_own_registry_completion_status`
call reads `WORKFLOW_STATE.json` and this item's own registry file only);
checks 2, 3, 4, 6, and 7 run regression tests either directly against real
production consumers/primitives (read-only) or inside an isolated
`ScratchRepo` fixture (writes only inside a disposable temporary
repository); check 7's live `assert_functional_review_not_already_consumed`
call against this item's own real `FUNCTIONAL_REVIEW.md` also only reads
and raises. All fifteen are safe to repeat freely.

### Known limitations / out of scope for this review

- This is **not** a re-walk of CP1-CP4's own original checkpoint behavior
  -- revision 1's checklist above already covers that ground in full; this
  section targets what changed between revision 1 and revision 4 (Finding
  1's fix and the three-round remediation arc it drove), now including the
  five previously-uncovered behavioral fixes from that arc's own round-2
  review (flows 2 through 7 above) that this correction pass added.
- O1 and O2 from round 4's own `REVIEW_FEEDBACK.md` remain open,
  explicitly non-blocking, and may be declined or deferred without
  weakening this round's approval -- not required acceptance criteria for
  this functional review. The same is true of round 2's own O1/O2 (folded
  in as `0a677b9`/`0736956`, both test-only and already landed) and round
  3's O1 (folded in as `2302cc4`, already landed) -- none of the three
  rounds' Optional findings are open today; this bullet names only the
  final round's, since those are the only ones still listed as open in the
  currently-authoritative `REVIEW_FEEDBACK.md`.
- Required follow-up #8 (operator reference / lifecycle diagram sync,
  `WORKFLOW_V2_3_FOLLOWUPS.md` item 2) remains deferred, unchanged since
  revision 1. `docs/ai-workflow/WORKFLOW_V2_1_OPERATOR_REFERENCE.md` and
  `docs/ai-workflow/diagrams/` are still untracked working-tree leftovers,
  declared `excluded` in this item's own artifacts declaration, and are
  left unmodified throughout this review (including this correction pass).
- Both correction passes are checklist/evidence-only changes: neither
  reopens, re-litigates, or alters `technical_approval` (still `CURRENT`,
  still bound to `2302cc4`), `plan_approval`, `phase` (still
  `AWAITING_FUNCTIONAL_REVIEW`), or any `REVIEW_FEEDBACK.md` verdict. No
  new functional defect was found while adding flows 2 through 7 (pass 1)
  or flow 8 (pass 2), or while correcting the old flows 9/14 (renumbered by
  pass 2 to 10/15) or flow 7's working-directory ambiguity (pass 2) -- every
  command in this section, at both passes, was re-run live and produced its
  stated result before the corresponding paragraph was written.

Findings go in `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.

### `workflow-v2-3-followups` — MILESTONE_COMPLETE (real, 2026-08-24)

The user gave literal `/accept-milestone`-stage confirmation for
`workflow-v2-3-followups` ("I confirm acceptance of
workflow-v2-3-followups."), validated for real by
`workflow_state.validate_user_confirmation`. This was given in direct
reply to this session's own explicit question asking whether the
revision-4 checklist (as corrected across its three passes above) had
been walked and requesting exactly this confirmation sentence if so — per
this command's own preamble ("ask for it before proceeding" when
acceptance hasn't happened yet in the conversation); as with
`workflow-v2-3`'s own acceptance above, there is no separate written
functional-review-pass narration beyond the confirmation itself.
`.ai-review/feedback/FUNCTIONAL_REVIEW.md` on disk still holds Finding 1's
content from the revision-1→4 remediation arc, already recorded consumed
(flow 7 above); no new finding was filed against the revision-4 checklist's
own three correction passes.

The advisory terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `(True, None)`,
`milestone_complete_gate_reachable` → `True` from phase
`AWAITING_FUNCTIONAL_REVIEW`) and the authoritative
`workflow_state.complete_work_item` call both succeeded: no
`IncompleteChildWorkItemError` (no work item declares
`workflow-v2-3-followups` as its `parent_work_item_id`), no
`IncompleteOwnCheckpointsError` (all four registry checkpoints,
`CP1`-`CP4`, `COMPLETE`), and no `UnsatisfiedCompletionObligationError`
(`workflow-v2-3-followups-registry.json` declares no completion
obligations — `resolve_completion_obligations` returns `{}`, vacuously
satisfied, same as `workflow-v2-3`'s and `v2-1-dry-run`'s own acceptances).
`work_items["workflow-v2-3-followups"].phase` is now `MILESTONE_COMPLETE`
(`state_revision` `58` → `59`); `active_work_item_id` resets to `null` (it
pointed here).

**Steps 3/5 do not apply** — the same gap `workflow-v2-1-core`'s,
`v2-1-dry-run`'s, and `workflow-v2-3`'s own completions already documented:
`grep` confirms zero mentions of `workflow-v2-3-followups` anywhere in
`docs/ROADMAP.md` (step 3 has no entry to mark).
`docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS_PLAN.md` is **not** archived to
`docs/milestones/completed/` (step 5) — that directory holds only real
product-milestone execution/reference plan pairs (milestones 1-8); a
process work item's own plan document was never the kind of artifact it
exists to hold, and `WORKFLOW_V2_3_FOLLOWUPS_PLAN.md` is referenced only
from this file's own `workflow-v2-3-followups` sections, `WORKFLOW_STATE.json`,
and this item's own registry/mapping/ledger files, not linked as ongoing
operator-facing guidance the way `WORKFLOW_V2_PLAN.md` is. Step 4 is
honored via this note itself, placed in this item's own section rather
than the top-level "Active plan" section, which belongs to Milestone 8
alone and is untouched by this completion. Step 7 ("Next action") is
unaffected for the same reason every prior process-item completion's was:
it already correctly points at the real `docs/ROADMAP.md`, which
`workflow-v2-3-followups` was never part of, and explicitly defers the next
roadmap milestone (the Figma-led redesign) until the user initiates it.
