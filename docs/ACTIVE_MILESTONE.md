# Active Milestone

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
`Modifier.weight(1f)` since before CP6 (`git show 5e0870d`).

**Revalidation at the real target width.** Both findings were then re-checked
on the **physical Samsung SM-S928B** (`RFCXA0RLSVT`, Android 16 / API 36,
1080×2340 @ 450 dpi, `sw384dp w384dp h832dp`, 3-button nav, 135 px bottom
inset) and on a matching **384 dp AVD** (`RepFlow_S24Ultra_384dp_API36`,
`emulator-5554`, same reported configuration). Geometry was confirmed
comparable from `am get-config`, `wm size`, `wm density` and the
`navigationBars` inset on both before any comparison was drawn. The same
debug APK was installed on both. **The physical device is authoritative
wherever the two disagree.** Full device metadata, the evidence index, and
40 screenshots (both themes, both devices) are under
`.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/revalidation/`.

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
also exercised the restore confirmation end to end. 45 screenshots are under
`.ai-review/repflow-redesign-visual-foundation/cp7-manual-pass/round2-enumerated/`
(`phys/`, `avd/`; gitignored). Themes were switched with
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
  HEAD there are 29 direct reads under `presentation/` (1 at `b39af90`), 14
  of them outside `designsystem/`, and `RepFlowColor.kt` is itself a file of
  literals. The sentence described the pre-milestone codebase. Replaced with
  what is true, and all 14 direct reads are now named in the `Consumers`
  column of the role they read, with the ground each renders on. **No
  contrast value changed and none is breached** — each was recomputed
  independently before the edit.
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

All seven checkpoints are complete. Implementation review round 2 returned
`REVISE` with 0 Blocking and 3 Important; all three are applied (see
"Implementation review round 2" above) and the bundle is regenerated at
implementation revision 3. The next state is `AWAITING_TECHNICAL_APPROVAL` —
only the user invokes `/approve-review implementation`.

The judgment calls this milestone deliberately leaves to the reviewer, all
disclosed rather than silently resolved: CP2's four unconfirmed nav glyphs;
light `control`, the light scale-row selected state and the light status-chip
accent tint; and the disclosed `surfaceContainer` menu-elevation inversion.
See the plan's "Known limitations" and "Areas the reviewer should
specifically challenge". The unconsumed `RepFlowStepper`, the two diverging
FABs and the light selected-pill value have moved off that list and into
`docs/improvements/IMPROVEMENT_ROADMAP.md` §8.1–8.3 as named follow-ups.

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
