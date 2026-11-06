# set-edit-fields — Execution Plan (Revision 2)

- Work item: `set-edit-fields` (backlog order 28, size 1, `docs/items/set-edit-fields.md`)
- Governing workflow version: `2.2`
- Base commit: `0d3250b2098bdb6450337ff314823d1c57bc31eb`
- Plan revision: 2

Pull request title: `feat: the set correction sheet also edits RPE, technique and pain`

## Goal and scope

Don't lose data, don't hide failures. Today the correction sheet changes only weight, reps or
seconds, so a wrong RPE, technique or pain value on the last logged set can't be fixed. The sheet
gets the same three scale rows the logging screen has, filled with the set's own values. The
backend already carries all three: `EditLastWorkoutSetCommand` has `rpe`, `pain` and
`techniqueQuality`, and `SetCorrectionSheet` passes them back unchanged. So this item is the sheet
and its call site; no use case, schema, backup or dependency changes.

The design pass for this (`design-5-workout-entry`, item 1) is done: the sheet is board `20i` on the
Claude Design page `2 Active workout`, with its warm-up variant on `20n`. That page says a 20x
drawing wins over an older artboard, and today's sheet comes from the older `4a`. This plan follows
`20i` and departs from it nowhere.

**Out of scope:** correcting any set but the last (`set-edit-any-set`, which also adds the pencil to
every row on `20n`), the warm-up flag, the timed-set clock, any change to how RPE, pain or technique
are logged, and the `Extra set fields` setting and its wording.

## Design (one mechanism: the logging screen's scale rows, shared)

**The sheet, as `20i` draws it.**

| | Today | After |
|---|---|---|
| Title | `Set 2`, or `Warm-up` on a warm-up set | `Correct this set` |
| Subtitle | `<name> · correct what you actually lifted` | `Set 2 · <name>`, or `Warm-up set · <name>` on a warm-up set |
| Body | The stepper cards | The stepper cards, then `Effort (RPE)`, `Pain` and `Technique`, always open |
| Hint | None | `Optional. Tap a chosen number again to clear it.` |
| Buttons | `Save the correction` | `Cancel` and `Save`, each 48 dp: side by side, stacked with `Cancel` on top above font scale 1.1 |
| Scroll | None | All of it scrolls, with `Save` at the foot |

**Rows.** `SetDetailSection` (`WorkoutFocusEntry.kt`) draws the disclosure and the three rows, and
takes a `SetEntryState`, which belongs to the logging flow. The rows move out into a new
`SetDetailRows` in the same file, taking `rpe: Int?`, `pain: Int?`, `technique: Int?` and three
change callbacks. `SetDetailSection` keeps its signature, its disclosure, its summary and its hint,
and calls `SetDetailRows` with `entry`'s fields, so the logging screen doesn't change. The sheet
calls `SetDetailRows` directly and has no disclosure or summary. Tapping the chosen cell again
clears it, as when logging.

**`Extra set fields` doesn't reach the sheet.** `20i` draws the rows open in every variant and never
mentions the setting, and Settings describes the setting as being about logging: "while you log",
"Hidden while you log. Anything you've already recorded still shows." With `Off`, a wrong value
stays visible on the set row, and the sheet is the only way to fix it. So someone on `Off` meets
these rows only when they open this sheet. `SetCorrectionSheet` takes no setting.

**Untouched values go back exactly as they were.** The rows are `Int` cells but a set's RPE is a
`Double`: the existing test logs `rpe = 7.5`. So the sheet holds the set's own `Double?` and replaces
it only when the user taps an RPE cell; a half value selects no cell and is saved as it was unless
touched. Pain and technique are `Int?` already. A cleared row saves `null`.

**Saved state.** The three new values use `rememberSaveable(set.id.value)`, like the sheet's load,
reps and seconds. `correcting` is saved, so the sheet comes back after a rotation, and a tap held in
plain `remember` would revert with no sign and `Save` would store the old value.

**Saving and cancelling.** `onSave` gains `rpe: Double?`, `pain: Int?` and `technique: Int?`;
`WorkoutFocus.kt` passes them to `onEditLastSet` in place of `lastSet.rpe`, `lastSet.pain` and
`lastSet.techniqueQuality`. `lastSet.isWarmup` stays as it is. `onEditLastSet`'s signature, the view
model and `EditLastWorkoutSet` are unchanged. `Cancel` is `RepFlowNeutralOutlineButton` and
`onDismissRequest`, so it saves nothing, like dismissing the sheet today. `Save` keeps
`RepFlowPrimaryButton`. Stacking uses the check `HomeResumeCard` and `BackupFileActions` use,
`LocalDensity.current.fontScale > 1.1f`.

**Fitting and a seam for tests.** The sheet's content gets `verticalScroll`, and the buttons scroll
with it. `SetCorrectionSheet` becomes a `RepFlowSheet` around a new internal `SetCorrectionContent`
holding the state, the scroll and the buttons. `ModalBottomSheet` opens its own window, and no
existing test puts a `LocalDensity` override inside one, so the font-scale and restore tests mount
`SetCorrectionContent` directly. The real sheet at those sizes and across a rotation is looked at on
the AVD, under CP1's done.

**The item's check: a corrected value reaches the next suggestion.** The progression recommendation
reads RPE from the persisted sets when the workout completes
(`CompleteWorkoutSession.computeRecommendations`, `workingSetRpe`). `EditLastWorkoutSet` rewrites the
same set in the same repository through `WorkoutSet.create`, the path `RecordWorkoutSet` uses. CP2
runs that whole path and reads the stored recommendation back.

Pain is different, and the item's check doesn't quite hold for it. No suggestion reads a set's pain:
`ProgressionPolicyV1` takes `latestPainWhileWalking` from the recovery check-in. So a corrected pain
value doesn't change a suggestion, and neither does a logged one. That is "the same as a value
entered when logging", and this plan doesn't change the policy. Corrected pain and technique are
stored and shown on the set row and in History as logged ones are. See "Known limitations".

No Android type enters `domain` or `application`; `LayerBoundaryTest` must stay green. No new
dependency or module, no network, no schema change. No "Open decision" row in
`docs/TECHNICAL_DECISIONS.md` is decided here.

## Review decisions (Revision 2)

Round 1 was the local review of Revision 1. Every finding was checked against the code and the board,
and all are accepted.

- **I1, the sheet differed from `20i`.** Accepted. The board was read through the Claude Design
  tools, and its text is what the table above quotes. The plan now follows it for rows, hint,
  buttons, title and subtitle. That removes the planned `extraSetFields` parameter and the
  change to `SetDetailSection`'s signature. Existing tests that press `Save` gain a scroll, listed
  under CP1.
- **I2, CP2 never ran completion.** Accepted. `workingSetRpe = emptyList()` in
  `CompleteWorkoutSession` passed all of Revision 1's CP2. New tests 4 and 5 run log, correct,
  complete and read back. The old test 3, which only restated the argument, is gone.
- **O1, the summary leaving out RPE 7.5.** Resolved by I1: the sheet has no summary, and the logging
  flow's values are whole numbers.
- **O2, restore.** Accepted: see "Saved state" and CP1 test 10.
- **O3, `createdAt` couldn't fail.** Accepted: CP2 test 1's edit runs on a later clock, and the
  mutation is added.
- **O4, pain and the suggestion.** Accepted: see "Known limitations".

## Requirements

| Id | Requirement |
|---|---|
| R1 | The correction sheet reads as `20i` draws it: title `Correct this set`, subtitle `Set N · <name>` or `Warm-up set · <name>`, then the steppers and the RPE, pain and technique scale rows, open whatever `Extra set fields` says and filled with the set's own values |
| R2 | `Save` stores the values as changed: a tapped cell replaces the value, tapping the chosen cell clears it, and a value the user didn't touch (an RPE of 7.5) is saved exactly as it was. `Cancel` saves nothing. A tapped value survives a restore |
| R3 | A corrected RPE, pain or technique value is stored and read back like one entered when logging, and a corrected RPE changes the recommendation workout completion stores |
| R4 | The hint reads `Optional. Tap a chosen number again to clear it.`, `Cancel` and `Save` sit at the foot and stack above font scale 1.1, and the sheet scrolls so nothing is cut off at font scale 2.0 or in landscape, in light and dark; the logging screen's rows and hint are unchanged |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | The correction sheet edits RPE, pain and technique (R1, R2, R4) | - | 2 | 1 |
| CP2 | A corrected value is stored and fed to the next suggestion like a logged one (R3) | CP1 | 1 | 1 |

<!-- CP1 -->
### CP1 — The correction sheet edits RPE, pain and technique (R1, R2, R4)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusSets.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`docs/UX_FLOWS.md`

What: `SetDetailRows`, `SetCorrectionContent` with its rows, hint, buttons and scroll, the new
`onSave`, the call site, the sheet's KDoc, and the correct-the-last-set line in `docs/UX_FLOWS.md`.
Strings: `workout_focus_correct_title` becomes `Correct this set` with no placeholder,
`workout_focus_correct_subtitle` becomes `Set %1$d · %2$s`, `workout_focus_correct_save` becomes
`Save`, and `workout_focus_correct_subtitle_warmup`, `workout_focus_correct_hint` and
`workout_focus_correct_cancel` are new.

Existing tests: only `correctingTheLastSetKeepsItsOtherFieldsAndReachesOnEditLastSet`,
`correctATimedSetOf45Seconds`, `correctingATimedSetTakesMinutesAndSeconds` and
`correctingATimedSetWithSetUnchangedKeepsItsSeconds` change. Their check that the sheet is open reads
the new title (`ActiveWorkoutScreenTest.kt:746`, `:971`), and their press of `Save` gains
`performScrollTo()` (`:751`, `:984`, `:995`). Every value they assert stays as it is, including
`listOf(60.0, 9, null, 7.5, false, 1, null)`.

New tests, in `ActiveWorkoutScreenTest`, whose `setContent` gains an `extraSetFields` argument:

1. All three filled in, with `Extra set fields` at `Collapsed` and again at `Off`: the rows are open
   with no disclosure on the sheet, those cells are selected, and `Save` returns the three unchanged.
2. Nothing filled in: the rows are open, no cell is selected, and `Save` returns `null` for all three.
   A timed set shows the same rows below its time card.
3. The title reads `Correct this set` and the subtitle `Set 1 · <name>`; on a warm-up set the subtitle
   reads `Warm-up set · <name>`, and `Save` returns the warm-up flag unchanged.
4. Tapping a new RPE cell, a new pain cell and a new technique cell reaches `onEditLastSet`.
5. Tapping the chosen pain cell again saves `null` for it; RPE and technique stay.
6. A set with RPE 7.5 selects no RPE cell. Saved after changing only the weight it returns 7.5, and
   tapping cell 8 returns 8.0.
7. `Cancel` after tapping cells doesn't reach `onEditLastSet` and closes the sheet, and reopening it
   shows the set's own values.
8. The sheet shows its own hint and not the logging one; the logging screen's open disclosure shows
   its own hint and not the sheet's.
9. Mounting `SetCorrectionContent` in a short box under a `LocalDensity` override: at font scale 2.0
   `Save` can be scrolled to and clicked, with `Cancel` above it; at 1.0 the two sit side by side.
10. Mounting `SetCorrectionContent` under `StateRestorationTester`: a tapped RPE, pain and technique
    survive `emulateSavedInstanceStateRestore()`, and `Save` returns them.

Mutations, one per run, each caught by a test above: the sheet keeping `set.rpe` whatever is tapped
(4); the pain row writing technique (4); a half RPE rounded to a whole on an unrelated save (6); the
rows hidden on `Off` (1); the disclosure drawn on `Collapsed` (1); the old title left in place (3);
`Cancel` calling `onSave` (7); the logging hint on the sheet (8); the scroll removed (9); the stacking
removed (9); the RPE, then pain, then technique value held in plain `remember` (10).

Done when: the new and changed tests pass on `emulator-5554`, `spotlessCheck` and `detekt` pass, and
the real sheet has been compared with `20i` on the AVD, light and dark, at font scales 1.0, 1.3 and
2.0 and in landscape. That look includes a set whose RPE, pain and technique are changed and saved,
a tap kept across a rotation, and the rows with `Extra set fields` at `Off`.
<!-- /CP1 -->

<!-- CP2 -->
### CP2 — A corrected value is stored and fed to the next suggestion like a logged one (R3)

**Files:**
`app/src/test/kotlin/com/repflow/app/application/workout/UndoAndEditLastWorkoutSetTest.kt`,
`app/src/test/kotlin/com/repflow/app/application/workout/CorrectedSetRecommendationTest.kt`

What: tests only, unless one finds a defect, which is then fixed in `EditLastWorkoutSet.kt`.

Tests in `UndoAndEditLastWorkoutSetTest` (JVM, against the in-memory repository):

1. A set logged with RPE 6, pain 1 and technique 3, then edited on a later clock to RPE 8, pain 0 and
   technique 4: the stored set holds 8, 0 and 4, with the same id and order, the original `createdAt`
   and the later `updatedAt`.
2. An edit that sets all three to `null` stores `null`.
3. An out-of-range RPE, pain or technique fails with `ValidationFailed` and leaves the stored set
   as it was.

Tests in the new `CorrectedSetRecommendationTest`, through the real `RecordWorkoutSet`,
`EditLastWorkoutSet`, `CompleteWorkoutSession` and `ComputeProgressionRecommendation`. The exercise
has a planned rep range of 8-12, seeded through `InMemoryTrainingPlanRepository`'s
`createPlanWithFirstVersion` and passed as `plannedExerciseId`, because without a range the policy
returns `WaitForMoreData` whatever the RPE. The recovery and futsal fakes stay empty, so no recovery
adjustment applies. Both tests log two working sets of 12 reps at RPE 7.

4. Completed with no correction, `findLatestForExercise` gives `IncreaseLoad` (average 7, ceiling 7.5).
5. With the last set corrected to RPE 10 before completing, it gives `MaintainLoad` (average 8.5).

Mutations, one per run: the edit dropping `rpe` (1, 5), then `pain` (1), then `techniqueQuality` (1)
from `WorkoutSet.create`; the edit using `createdAt = clock.now()` (1); `workingSetRpe = emptyList()`
in `CompleteWorkoutSession` (4: with no RPE the average isn't a number, so the policy falls through to
`MaintainLoad`).

Done when: the JVM tests pass, plus `spotlessCheck` and `detekt`.
<!-- /CP2 -->

## Known limitations, for the functional review

The functional review's checklist must say these plainly, because the owner may expect otherwise.

- **Pain doesn't change a suggestion.** The item's check asks that a corrected pain value reach the
  next suggestion. No suggestion reads a set's pain, logged or corrected; the only pain the policy
  reads is `Pain while walking` from the recovery check-in. Making set pain count would be a new
  policy rule and a separate item.
- **The rows show with `Extra set fields` at `Off`.** This follows `20i`. The setting still hides them
  while logging.
- **A half RPE.** See the note below.

## Risks and notes

- **Sheet height.** Handled by the scroll above; the font scales and landscape are part of CP1's
  done. `Save` is at the foot, so at 1.3 and 2.0 it can be off-screen until scrolled to, as `20i`
  says.
- **Half-step RPE.** A set can hold 7.5 although the cells are whole; the sheet selects no cell for it
  but keeps it unless touched, and the set row still shows the value. `20i` doesn't draw this case.
  This is stated so a reviewer doesn't read the empty row as a bug.
- **Seen and left as it is.** The set row reads `RPE: 8.0 · Pain during set: 1/5 · Technique quality:
  4/5`, where the boards draw `RPE 8 · pain 0 · technique 4`. That text is shared with logged sets and
  this item doesn't touch it; a corrected value shows in it the same way.
