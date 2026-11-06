# timed-entry-mmss — Execution Plan (Revision 2)

- Work item: `timed-entry-mmss` (backlog order 26, size 2, `docs/items/timed-entry-mmss.md`)
- Governing workflow version: `2.2`
- Base commit: `adec3533d6ffeb70cbc0a6fd070fdeb713b1937b`
- Plan revision: 2

Pull request title: `feat: timed sets and rest use m:ss while logging and in History, and the number pad opens on the current value`

## Goal and scope

Less friction during a workout. 9 of the owner's 34 exercises are timed, and holds run to 300 s and
900 s. Today a timed set is typed and shown as raw seconds (`900 s`), rest is typed in seconds, and
every number pad opens empty, so changing a value means retyping it. This item does two things:

1. **Timed sets and rest read and type as m:ss** (`15:00`, `1:30`, `0:45`) in the workout, the plan
   editor, Settings' `Other` pad, History and the workout summary.
2. **Every number pad opens on the current value, selected**: the first key replaces it, Backspace
   clears it. The pad is one shared component, so this is app-wide (weight, reps, plan targets, rest).

**Stored data does not change.** A timed set is still whole seconds in Room, `DurationTarget` and
`RestDuration` are still seconds, backup/restore and CSV keep raw seconds (data, not display). No
schema change, no new dependency.

**Out of scope**, each with what happens to it:

- **Progress**: its chart, readout, delta and the `Longest hold` line (`ProgressStatsSection.kt:195`).
  `progress-recovery-polish` (order 66) already owns "timed Progress shows seconds", so nothing needs
  queueing. The wrap-up says so.
- **The out-of-range line** (board 50h, spec 60a, D133): a limit under the value and a disabled `Set`.
  R2 keeps today's silent clamp, because showing a limit means passing the range into the pad, a second
  mechanism. No item owns this, so the wrap-up names it as follow-up `number-pad-range-line`.
- **The exercise editor's `Other`** (A9, `ExerciseEditorFormFields.kt:277-285`). It is a typed text
  field, not this pad, and it stays in seconds, so this item defers A9 for that one field. Its label says
  `Default rest (seconds, optional)`, so it is not the silent trap the pads were. No item owns this, so
  the wrap-up names it as follow-up `exercise-rest-pad`.
- **The rest of the plan row's rest**: the −/+ stepper going away, the `0:45` preset, `Other` and the
  rest's source all belong to `plan-editor-rows` (order 60).
- **Row layout and wording**: `Set 1: 1:30` and `Target: 0:30-0:45` keep their wording, where board 20g
  draws `0:45` and `0:30–0:45`; only the values change here. The new rows belong to `focus-just-logged`.
- The type note 20g drops (`small-polish`), the clock on 20o and 20p (`timed-set-clock`), and the
  correction sheet's scale rows on 20i (`set-edit-fields`).
- `+15s` and `-15s` on the rest strip, as board 20a draws them.
- Changing the 5 s step of the timed stepper, or the 1 s to 2 h plan range.

## Design (one new mechanism: the m:ss duration)

**The formatter.** `formatDuration(seconds: Long): String` in `RepFlowDuration.kt`
(`presentation/designsystem/components`): minutes are not capped and the seconds are two digits, so
`0 → 0:00`, `45 → 0:45`, `90 → 1:30`, `900 → 15:00`, `7200 → 120:00` (the plan range's top),
`999999 → 16666:39` (a stored value; `WorkoutSet.kt:95` only requires a positive duration). Pure Kotlin,
tested on the JVM. Every new display site calls it. `restClock` and `formatRest` already give the same
text and are left alone.

**The pad's rules.** `RepFlowNumericKeypad` gains two parameters, both defaulted:

- `initialValue: BigDecimal? = null`. Non-null opens on the value, selected. The first key replaces it.
  `.` on a decimal pad gives `0.` (spec 60a, and the same on an empty pad); `.` on a whole-number pad is
  ignored and the value stays selected. Backspace clears it to empty. `Cancel` and a dismiss leave the
  value untouched. `Set` straight away confirms the same value, even one longer than the typing limit: the
  limit applies to typing, and a seed is never cut. `Set` after clearing acts as `Cancel`, as `Set` on an
  empty pad does today. `null` opens empty, as today.
- `durationEntry: Boolean = false`. Digits fill from the right like a clock: the last two are seconds and
  the rest are minutes, so `2` shows `0:02`, `2 1` shows `0:21`, `2 1 5` shows `2:15`, `1 5 0 0` shows
  `15:00`. The `.` cell is a `00` key (board 10l), so `5 00` is 5:00 and `15 00` is 15:00. Leading zeros
  are ignored: `0` or `00` on an empty pad does nothing, and as the first key on a selected value it
  empties it, showing `0:00`. Five digits at most (`120:00`), and `00` adds only the digits that fit. `Set`
  confirms minutes × 60 + seconds, so `0:90` confirms 90 and then shows `1:30`; the caller's existing clamp
  (`RepFlowStepperMath.coerce`, `PlanRowStepping.typed`, `onDefaultRestSelected`) still applies the range.
  An opening value of 90 s seeds `130`, and 999999 s seeds `1666639`; every seed confirms back to the same
  seconds.

The rules live in `RepFlowKeypadInput` (pure, JVM-tested): `keys`, `press`, `display` and `parse` gain the
selected and duration cases, and a new `seed` gives the opening text. The composable only holds the state
(`rememberSaveable`, so a rotation keeps the half-typed entry and whether the value is still selected).

**How the pad looks** (boards 50h and 10l). The header is the board's: the title at the left at 18 sp
medium, the value centred at 40 sp in a ring with a tint while selected, and one line under it at 12.5 sp:

- a decimal pad says `Selected: type to replace` while selected, then nothing, and the line keeps its
  height so the keys don't move;
- a whole-number pad says `Whole numbers only`, and its decimal key is greyed and disabled;
- an m:ss pad says `minutes and seconds`.

The selected colours are the board's `selBg` and `selBd`: `primary` at 18% in dark and 14% in light, with
a `primary` ring, from a new `RepFlowKeypadDefaults.selectedColors(scheme)`. They don't come from
`repFlowSelectedPillColors`, whose light fill is transparent (`RepFlowTag.kt:156`), so a fill alone would
show nothing there. The pad's content scrolls, because the taller header would otherwise cut `Set` off in
landscape and at font scale 2.0. While selected the value carries `selected` semantics and Backspace reads
`Clear value`, then `Delete last digit` once something is typed.

**Wiring.** `RepFlowStepper` passes its own `value` as `initialValue` and takes `durationEntry`. These use
the m:ss pad: the workout's time stepper (which the correction sheet shares through `SecondsCard`), the
plan editor's `Min time`, `Max time` and `Rest` lines, and Settings' `Default rest` > `Other`. The plan
editor's other lines pass the current value only. `PlanRowStepping.typed` and `onSelect` already take
seconds, so no caller's arithmetic changes. A plan line with blank or unparsable text opens empty.

`StepperLine`'s value cell is 64 dp at 16 sp, and by estimate (not measured) `120:00` wraps from about 1.3
and `15:00` by 2.0. The line's stepper therefore grows with the font: the value cell is 64 dp × font scale,
so 164 dp stays 164 dp at 1.0. The value stays on one line and the label takes what is left, wrapping if it
must. The workout's time stepper has no such limit, because a timed exercise's card is alone in its row.

**Showing it.** The labels follow the unit: `Seconds` becomes `Time (m:ss)`, `Min seconds`/`Max seconds`
become `Min time`/`Max time`, Settings' pad title `Rest in seconds` becomes `Rest`. Under the time stepper,
`seconds held` is replaced by `Steps of 0:05. Tap the value to type m:ss.`, which always shows, below the
target line when there is one. The targets, last values, set rows, History set value, summary recap, best
set, deltas, and the pending row, warm-up hints and plan row meta's rest show m:ss. A delta shows `+0:15`
and `−1:05`. The step buttons keep their spoken `Fewer seconds` and `More seconds`.

**Boards, and every difference from them:**

| Board | Draws | In this item |
|---|---|---|
| 50h | Opens selected with a tint and ring; title over a centred 40 sp value; a line under it; decimal greyed on whole-number pads | Built |
| 50h, spec 60a | An out-of-range value shows its limit and disables `Set` | Not built; follow-up `number-pad-range-line` |
| 50h | Keys 52 tall with a gap of 6; `Cancel` and `Set` equal, stacked above 1.1× | Kept as built: 56 tall, gap 8, 1 : 2 in a row. The targets stay larger, and nothing here depends on it |
| 10l | The m:ss pad titled `Rest`, with `00` and `minutes and seconds` | Built, for rest and for time |
| 20g | `Time (m:ss)`, the hint, m:ss on every line | Built. The row format and the type note are out of scope above. The card keeps its target line, which 20g shows in a plan line at the top that the app doesn't have |
| 20i | A timed set corrected on the same `Time (m:ss)` card | Built, through `SecondsCard`. Scale rows are out of scope above |
| 10k | `Min time` and `Max time` with m:ss values, drawn as fields | Labels and values built. The lines stay steppers, as built today |

**Decisions to confirm** (taken here so the review can overturn them, none touches stored data):

1. Timer-style entry (digits fill from the right) rather than a `:` key, as board 10l draws it.
2. Backspace on a selected value clears all of it, rather than dropping one character.
3. Rest is typed and shown as m:ss here, because the owner already chose it (A9, A14, spec 60a's "Rest is
   m:ss everywhere") and because leaving it would make `1 3 0` mean 90 s on `Max time` and 130 s on `Rest`
   on the same open row. Progress stays out, for `progress-recovery-polish`.
4. The out-of-range line stays out, and A9 stays deferred for the exercise editor's `Other`, each with the
   named follow-up above.

**Review round 1 (local), how each finding was handled.** None is rejected.

- I1 accepted: the rest pads are m:ss and titled `Rest`. Round 1's claims were wrong: only Settings' pad
  said `Rest in seconds` (`strings.xml:707`), the plan rest line says `Rest` (`strings.xml:182`), and four
  strings still showed raw seconds (`strings.xml:150,218,565,566`). Those four now show m:ss too.
- I2 accepted: `TrainingPlanEditorScreenTest` joins CP2 and CP3, and a new `WorkoutDoneScreenTest` joins CP3.
- I3 accepted: the boards are named above, and each difference is built or declared.
- O1 to O4 accepted, in the wrap-up, the tests and the title. O5: all of Progress stays together, with the
  owner named, the second of the two options it offers.
- The missing tests and the three wording fixes are in the checkpoints below.
- Added beyond the review, after reading the boards: `.` first gives `0.` (spec 60a), the pad scrolls, the
  exercise editor's `Other` is declared, and Progress's owner already exists.

## Requirements

| Id | Requirement |
|---|---|
| R1 | A timed set's duration is shown as m:ss everywhere a person reads it outside Progress: the workout's time stepper, its target and `Last` lines, the set rows, the plan editor's min/max and row meta, History's set value, and the workout summary's recap, best set and delta. |
| R2 | The timed set's pad is an m:ss pad: digits fill from the right, `1 3 0` reads `1:30`, `Set` stores whole seconds, and the stepper then shows m:ss. The field says so (`Time (m:ss)` and its hint). A value over the range is clamped as before. |
| R3 | Every number pad opens on the current value, selected: the first key replaces it (`.` on a whole-number pad is ignored), Backspace clears it, Cancel keeps it, and Set unchanged confirms the same value, even one longer than the typing limit. A pad for an empty field opens empty. A rotation keeps the entry and whether it is selected. |
| R4 | Nothing stored changes: a timed set and a rest are whole seconds in Room, backup and CSV keep raw seconds, and the plan range and the 5 s step are as before. |
| R5 | Rest is typed and shown as m:ss: the plan editor's rest line and Settings' `Other` use the m:ss pad titled `Rest`, and the pending row, the warm-up hints and the plan row meta show the clock. |
| R6 | The pad looks as boards 50h and 10l draw it, apart from the differences declared above, and the selected value is visibly selected in both themes. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | The m:ss formatter, and a pad drawn as 50h and 10l that opens on the current value (R2, R3, R6) | - | 3 | 1 |
| CP2 | Timed values and rest are typed as m:ss, and the other pads open on the current value (R2, R3, R4, R5) | CP1 | 2 | 1 |
| CP3 | Every timed duration and rest is shown as m:ss (R1, R4, R5) | CP1 | 2 | 1 |

<!-- CP1 -->
### CP1 — The m:ss formatter, and a pad drawn as 50h and 10l that opens on the current value (R2, R3, R6)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowDuration.kt` (new),
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowNumericKeypad.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowStepper.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowDurationTest.kt` (new),
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowKeypadInputTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowPrimitivesTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/designsystem/components/RepFlowStructuralPrimitivesTest.kt`

What: `formatDuration`; the `initialValue` and `durationEntry` parameters and their input rules; the
header, the selected colours, the greyed decimal key, the `00` key and the scrolling content;
`RepFlowStepper` passing its value and taking `durationEntry`. No caller opts in to the m:ss pad yet, but
every stepper pad already opens on its value. The keypad's KDoc is corrected to name 50h and 10l.

Tests: `RepFlowDurationTest` (0, 5, 45, 60, 90, 900, 7200, 999999).

`RepFlowKeypadInputTest`:

- selected: a digit replaces it; `.` on a decimal pad gives `0.`, then `5` gives `0.5`, and an empty pad does
  the same; `.` on a whole-number pad is ignored and the value stays selected; Backspace clears it; Backspace
  on typed text still drops one character; no initial value opens empty and not selected; a decimal pad opens
  on `82.5`; a seed longer than the limit isn't cut and confirms back unchanged.
- m:ss pad: `2` shows `0:02`, `21` shows `0:21`, `215` shows `2:15`, `130` shows `1:30`, `45` shows `0:45`,
  `1500` shows `15:00`, an empty pad shows `0:00`; a leading `0` is ignored; `00` on an empty pad is ignored;
  `1 00` is `1:00`; `0` and `00` on a selected value empty it; a sixth digit is ignored; `1 2 3 4 00` stops at
  `123:40`; `.` is ignored; `0:90` confirms 90, `1:30` confirms 90, and empty confirms nothing; the seeds of 0
  (empty), 45, 60, 90, 900, 7200 and 999999 each confirm back to the same seconds.
- `keys` has `.` on a plain pad and `00` on an m:ss pad, in the same cell.

`RepFlowPrimitivesTest`: the selected fill isn't transparent, in both themes, and the ring is `primary`.

`RepFlowStructuralPrimitivesTest`: the pad opens showing the stepper's value, selected (`assertIsSelected`);
a digit replaces it and it is no longer selected; Cancel leaves the value; Set unchanged confirms it;
Backspace then Set leaves it; Backspace is `Clear value` while selected and `Delete last digit` after a
digit; a half-typed entry and a still-selected value each survive a `StateRestorationTester` restore; the
decimal key is disabled on a whole-number pad and enabled on a decimal one; `Whole numbers only`,
`Selected: type to replace` and `minutes and seconds` each show on their pad; `Set` can be scrolled to
(`performScrollTo`).

Mutations to try, each caught in a file above: select state never set (`RepFlowStructuralPrimitivesTest`);
Backspace on selected drops one character, the duration seed using seconds as digits, a seed cut to five
digits, minutes and seconds swapped in `parse`, and `.` replacing a whole-number value
(`RepFlowKeypadInputTest`); minutes and seconds swapped in `formatDuration` (`RepFlowDurationTest`); the
selected fill taken from `repFlowSelectedPillColors` (`RepFlowPrimitivesTest`); `selected` not saved, and the
scroll removed (`RepFlowStructuralPrimitivesTest`).

<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Timed values and rest are typed as m:ss, and the other pads open on the current value (R2, R3, R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/trainingplan/editor/TrainingPlanEditorTargets.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/settings/SettingsChoices.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/trainingplan/editor/PlanRowSteppingTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/settings/SettingsScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/trainingplan/editor/TrainingPlanEditorScreenTest.kt`

What: the workout's `SecondsCard`, the plan editor's `Min time`, `Max time` and `Rest` lines, and Settings'
`Other` use the m:ss pad; the time stepper and the two plan duration lines show the clock (the rest line
already does); the plan editor's other lines pass their current value so they open on it; the labels, the
hint and the pad titles are reworded, with `workout_focus_seconds_held` removed; the plan line's stepper grows
with the font. The stored value stays whole seconds.

Tests:

- `ActiveWorkoutScreenTest`: `1 3 0` in the workout's time pad logs a 90 s set and the stepper reads `1:30`;
  the 5 s step from `1:30` gives `1:35`; `Time (m:ss)` and the hint show; a weight pad opens on `82.5` and `4`
  makes it `4`; on the correction sheet of a timed set, the pad opens on the value, `1 3 0` saves 90 s, and
  `Set` unchanged keeps it.
- `TrainingPlanEditorScreenTest`, with an open timed row: `1 3 0` on `Min time` and on `Max time` each report
  `"90"`; the stepper shows `1:30`; the pad opens on the current value, selected; `Set` unchanged reports the
  same value; a value above the plan range clamps to `"7200"`. `1 3 0` on `Rest` reports `"90"`, and its pad
  opens on `1:30` titled `Rest`. `Working sets` opens on its value, and a blank line opens empty. At font scale
  2.0, `120:00` stays on one line.
- `SettingsScreenTest`: `otherOpensTheKeypadAndATypedRestIsSaved` changes to `1 3 5` saving 95 s; the pad is
  titled `Rest` and opens on `2:00`; `Set` unchanged saves 120; a custom `2:15` reopens on `2:15`.
- `PlanRowSteppingTest` pins that a confirmed 90 is stored as `"90"`.

Mutations to try, each caught in a file above: the workout pad left plain (a typed `130` stores 130 s) and the
clock format dropped from the stepper (`ActiveWorkoutScreenTest`); the plan min, max and rest lines each left
plain, `StepperLine` not passing its value, and the line's stepper not growing with the font
(`TrainingPlanEditorScreenTest`); the Settings pad left plain, or opening empty (`SettingsScreenTest`).

<!-- /CP2 -->

<!-- CP3 -->
### CP3 — Every timed duration and rest is shown as m:ss (R1, R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusSets.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutDoneText.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/history/HistoryDetailScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/trainingplan/editor/TrainingPlanEditorRows.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/WorkoutDoneTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutPreferencesTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/WorkoutDoneScreenTest.kt` (new),
`app/src/androidTest/kotlin/com/repflow/app/presentation/history/HistoryDetailScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/trainingplan/editor/TrainingPlanEditorScreenTest.kt`,
`docs/UX_FLOWS.md`

What: each site in the table below shows `formatDuration`'s clock. A string left as only `%1$s` is removed
and its site uses the clock directly. `workout_done_delta_seconds_same` takes no argument and is unchanged.
The recap group joins the clocks with commas (`1:30, 1:15, 1:00`). The plan row meta's values are text that
can be blank or unparsable (`TrainingPlanEditorRows.kt:172-175`), so only a whole number becomes a clock, and
anything else keeps showing as it does today. `docs/UX_FLOWS.md` gets one line that timed values and rest are
m:ss and the pad opens on the current value. Backup, CSV and Progress are not touched, and the wrap-up says so.

| Site | String | Held by |
|---|---|---|
| Time card's target | `workout_focus_seconds_target` | `ActiveWorkoutScreenTest` |
| `Last:` line | `workout_focus_last_duration` | `ActiveWorkoutScreenTest` |
| `Last time:` line | `workout_focus_last_time_duration` | `ActiveWorkoutScreenTest` |
| Set row | `workout_active_set_row_duration` | `ActiveWorkoutScreenTest` |
| Pending row's target | `workout_active_plan_duration_range` | `ActiveWorkoutScreenTest` |
| Pending row's rest | `workout_active_plan_rest` | `ActiveWorkoutScreenTest` |
| Warm-up hints | `workout_focus_warmup_hint_on`, `_off` | `ActiveWorkoutPreferencesTest` |
| Plan row meta | `training_plan_editor_row_meta_duration`, `_rest` | `TrainingPlanEditorScreenTest` |
| History set value | `history_detail_set_duration` | `HistoryDetailScreenTest` |
| Summary recap | `workout_done_recap_seconds_group` | `WorkoutDoneScreenTest` |
| Summary best set | `workout_done_set_seconds` | `WorkoutDoneScreenTest` |
| Delta | `workout_done_delta_seconds_up`, `_down` | `WorkoutDoneScreenTest`, `HistoryDetailScreenTest` |

Tests: one case per row of the table, with a value over a minute and one under (`0:45`, `1:30`); History
shows `15:00` for 900 s. `WorkoutDoneScreenTest` renders `WorkoutDoneScreen` with the recap `1:30, 1:15, 1:00`,
a best set and its previous value over and under a minute, and a delta of +15 s and of −65 s.
`TrainingPlanEditorScreenTest`: the row meta reads `0:30–1:00` and `1:30 rest`, a blank field keeps its
placeholder, and unparsable text shows as it was. `WorkoutDoneTest` pins that the model still carries seconds.
A search of `app/src/main` finds no timed or rest string still formatting raw seconds with `s`, apart from
`+15s` and `-15s`, Progress, and the exercise editor's label.

Mutations to try, each caught by the file its row names: each call site returning to the raw number; a delta
rendered unsigned (`WorkoutDoneScreenTest`).

<!-- /CP3 -->

## Wrap-up (after the checkpoints; not a checkpoint)

Full `spotlessCheck`, `detekt`, `lintDebug`, the JVM suite and `connectedDebugAndroidTest` on the AVD
(`AGENTS.md`).

Look at it in the real app, in light and dark at font scale 1.0, 1.3 and 2.0: a timed exercise's pad opens on
its value with the selection visible in both themes, `1 3 0` gives `1:30`, and History and the summary agree;
the plan rest line and Settings' `Other` open on the value and take m:ss; on the plan editor's stepper lines,
`15:00` and `120:00` stay on one line beside a readable label. One look in landscape, to confirm `Set` can be
reached.

Listen with TalkBack to the pad's value and the stepper's value, as `safety-copy-polish` did. Nothing has
tested how `1:30`, `15:00` or `120:00` are read. If one is read as something other than a time, those two values
gain a spoken form; read-only text doesn't. The record says what was heard either way, and whether the selected
state and `Clear value` are spoken.

Name the follow-ups: Progress's seconds belong to `progress-recovery-polish`, already queued; the rest of the
plan row's rest to `plan-editor-rows`, already queued; `number-pad-range-line` and `exercise-rest-pad` have no
owner yet, so they go on one new follow-up issue for the owner's backlog.

## Self-review notes

- Checked the plan against `docs/TECHNICAL_DECISIONS.md` "Open decisions": nothing here touches a
  toolchain, dependency, notification or storage row, so none is finalised.
- The mechanism count is one (the m:ss duration and its pad). The selected behaviour and the header are part
  of the same pad change, not a second feature. The out-of-range line would be a second one, so it is out.
- The pad's six-character limit (`MAX_LENGTH`) was built for decimals; the duration pad uses its own
  five-digit limit, so a plain pad's limit is unchanged. Neither limit cuts a seed.
- The shared pad's header shows the typed text, so the selected value must render the same text the stepper
  shows (`82.5`, not `82.50`, and `1:30` on a time or rest pad); `RepFlowStepperMath.format` and
  `RepFlowKeypadInput.display` are used for those.
- Risk: three test files press the pad's keys (`RepFlowStructuralPrimitivesTest`, `ActiveWorkoutScreenTest`,
  `SettingsScreenTest`) and find keys by their text. A pad seeded with the single digit a test then presses
  would match two nodes. None does today; a new test that needs to finds the key another way.
- The scroll's only tests are `performScrollTo` and the landscape look; no test measures a cut-off `Set`.
- Decisions 1 to 4 above are the owner-visible choices; none is a data change.
