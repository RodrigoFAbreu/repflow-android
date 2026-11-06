# set-edit-any-set — Execution Plan (Revision 2)

- Work item: `set-edit-any-set` (backlog order 29, size 2, `docs/items/set-edit-any-set.md`)
- Governing workflow version: `2.2`
- Base commit: `ebf6135c77336e62f9bc79e41e123a364df9329f`
- Plan revision: 2

Pull request title: `feat: any set in the running workout can be corrected, not just the last one`

## Goal and scope

Don't lose data, don't hide failures. Today only the most recently logged set shows the pencil and can
be corrected, so a wrong weight or RPE on set 2 of 4 can only be fixed by undoing the sets after it
and logging them again. After this item every logged set in the running workout opens the same
correction sheet (board `20i`, with the warm-up variant on `20n`), and the correction names the set
by its id, so it always changes the set the user opened.

The one new mechanism: the correction carries a set id. The use case finds that set instead of
`maxByOrNull { it.order }`, and the sheet is opened for a set id instead of for "the last set". The
sheet itself, the stepper cards, the RPE, pain and technique rows, and the save rules are
`set-edit-fields`' and don't change.

`safety-copy-polish`'s wrap-up lists issue #34's note about a correction changing the wrong set as
closed, because its overlay stops anything waiting behind a restore. A correction could still wait
briefly behind another action and land on whichever set was last when it ran. It now names its set,
so that ends too (CP1 test 9). The wrap-up reads the issue and says which this is: closing a note
still open, or tightening one already closed.

**Out of scope:** sets in past workouts (`set-edit-past-workouts`), deleting a set (`Undo last` stays
as it is and still removes only the most recent set), changing a set's order, the warm-up flag, the
timed-set clock, the `Extra set fields` setting, and any schema, backup or dependency change.

## Design

**The use case names the set.** `EditLastWorkoutSet` becomes `EditWorkoutSet`, and its command
becomes `EditWorkoutSetCommand` with a new `setId: WorkoutSetId`. It finds the exercise as before,
then the set with that id in the exercise's sets; no such set is `WorkoutOperationError.NotFound`
and nothing is written. The rest is unchanged: the set is rebuilt through `WorkoutSet.create` with
the found set's own `id`, `order` and `createdAt`, so only the values the command carries change,
and `updatedAt` moves. The rename is mechanical, because the old name would say something the code
no longer does; the type has no other behaviour to change.

**The view model and the callback.** `ActiveWorkoutViewModel.onEditLastSet` becomes `onEditSet`, and
the screen's `onEditLastSet` callback becomes `onEditSet`, both with the set id as a new second
parameter after the exercise id. It still goes through `launchAction`, so it keeps `actionLock`'s
order and the error path (`Couldn't complete that. Try again.`). `onUndoLastSet` is untouched.

**Every logged row opens the sheet.** `FocusSetRow.Logged.isLast` goes: its only two readers are the
conditions in `LoggedSetRow` this item removes, and `focusSetRows` stops computing it. Every logged
row is clickable with the existing `Correct this set` click label and shows the pencil, and a pending
row still doesn't. The callback becomes `onCorrect: (WorkoutSetId) -> Unit`. The pencil has no content
description, so it has no semantics node of its own, and it carries a `testTag`
(`SET_ROW_PENCIL_TAG`) so a test can see it, as `CHOICE_CHECK_TAG` does at `SettingsChoices.kt:121`.

**What board `20n` settles.** `20n` (page `2 Active workout`) was read for this revision. It says every
logged set has a pencil on its row, working sets and warm-ups alike, and that this replaces `D31`.
`dp-kit.js`'s `ib` draws that pencil as a 20 dp glyph in `T.ink`, in a 44 dp box. So:

- **The glyph follows the board:** 20 dp in `onSurface`, the colour of the row's summary and what
  `T.ink` draws, instead of today's 14 dp in the secondary tint. It is shorter than the row's 26 dp
  disc, so the row's height doesn't change.
- **The target differs from the board, on purpose:** the whole row stays the one target, with no
  separate 44 dp button. The row is at least 48 dp tall, so it passes the 44 dp minimum the box is
  there to meet. One target keeps one screen-reader stop, with its `Correct this set` label, and the
  locators of the existing tests, which tap the row. A 44 dp box inside the row's 11 dp padding would
  also make every logged row 66 dp tall.
- **A warm-up row keeps today's look:** the flame disc and the `warm-up` suffix, where the board draws
  a `W` marker and a `Warm-up` line. That difference is older than this item and this item doesn't
  touch it.

CP2 re-reads `20n` before it draws anything, and records anything that has changed since.

**The sheet is opened by id and survives a restore.** In `WorkoutFocus`, `correcting: Boolean` becomes
`correctingSetId: String?`, held with `rememberSaveable(exercise.id.value)` like today's flag, so the
sheet comes back after a rotation or process death on the same set. The sheet's set is looked up in
`exercise.sets` by that id on every composition. If the set is no longer there (it was undone, or the
workout changed under a restored sheet), no sheet is drawn and the id is cleared in a
`LaunchedEffect`, so a stale id doesn't stay saved and can't reopen a sheet if a set with that id
came back. `Save` passes the opened set's id and its own `isWarmup`, then closes the sheet as it does
today.

**Each set gets its own saved values.** `rememberSaveable` finds a restored value by composite key
hash alone and compares its inputs only within one composition (`RememberSaveableKt` and
`SaveableHolder` in `runtime-saveable` 1.11.4, read from the bytecode). So the `set.id.value` inputs
in `SetCorrectionContent` don't protect a restored value. If a sheet on set A is saved and the state
comes back with A gone, A's values stay unconsumed in the registry and the next sheet opened from
that call site consumes them: set B opens with A's numbers, and `Save` writes them to B with no
message. It takes a set vanishing between the save and the restore, for example a queued `Undo last`
finishing across a rotation. Today's code has the same shape, so this isn't a regression, but it
stores wrong values silently, and this item makes every row a way in.

So the call site in `WorkoutFocus` becomes `key(set.id.value) { SetCorrectionSheet(...) }`, which gives
each set its own hash. It is a `key` rather than an explicit `key =` on the six calls because the
sheet holds other saved state under it: `keypadOpen` (`RepFlowStepper.kt:216`), the pad's text and
selection (`RepFlowNumericKeypad.kt:269-270`) and the scroll position. A's leftovers stay in the
registry under a hash no other set reaches, because set ids aren't reused.

CP2 test 8 is written first and run without the `key`. If it passes, the milestone file records that
and the `key` and its mutation are left out. The inputs in `SetCorrectionContent` stay, since they
still guard that content when a test mounts it alone, but nothing in the app relies on them: the sheet
is modal and `correctingSetId` changes only from a row, so `set` never changes under a mounted sheet.
That is why no mutation is listed for them.

**A saved correction leaves the steppers alone.** `20n` says saving a correction "changes that set's
row in place and leaves the steppers, the rest and the suggestion alone". The rest and the suggestion
already hold: a correction doesn't touch the rest timer, and a suggestion is computed only on
completion. The steppers don't hold today. Correcting the last working set changes `entrySeedOf`
(`WorkoutFocusModel.kt:169`), and `LaunchedEffect(entry, exercise.seed)` (`WorkoutFocus.kt:113`)
applies that to an untouched entry, so a stepper reading 82.5 would jump to 80.

This plan follows the board. Before calling `onEditSet`, `Save` calls a new
`SetEntryState.holdNumbers()`, which sets `touched`. `touched` is saved state, and that is why it is
used: the seed effect restarts on a trip to the Board and on a restore, so a narrower change, such as
a different key on the effect, would still apply the corrected seed then. The cost is that after a
saved correction the exercise's steppers stop following the seed, as they already do once a value has
been typed, so a later `Undo last` doesn't re-seed them (see the known limitations). A `Save` that
fails holds them too, which moves nothing.

**Nothing else moves.** A corrected RPE reaches the next suggestion through the same completion path
as before, which reads every working set's stored RPE, so correcting an earlier set changes it too
(CP1 test 6 runs it). No Android type enters `domain` or `application`; `LayerBoundaryTest` must stay
green. No new dependency or module, no network, no schema change. No "Open decision" row in
`docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | Every logged set of the exercise in focus shows the pencil and opens the correction sheet for that set, filled with that set's own values; pending rows don't |
| R2 | Saving changes only the set that was opened, found by its id: the other sets are stored as they were, and the set keeps its id, order, `createdAt` and warm-up flag. A set that no longer exists changes nothing and reports the failure |
| R3 | A corrected RPE on an earlier set changes the recommendation workout completion stores, as a logged one does |
| R4 | The sheet comes back on the same set after a rotation, never opens on another set or with another set's values, and closes if its set is gone; `Undo last` still removes only the most recent set, and apart from the pencil the rows' look is unchanged |
| R5 | Saving a correction leaves the steppers, the rest timer and the suggestion strip as they are |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | The correction names the set it changes (R2, R3, R4, R5) | - | 2 | 1 |
| CP2 | Every logged row opens the sheet for its set (R1, R2, R4, R5) | CP1 | 3 | 1 |

<!-- CP1 -->
### CP1 — The correction names the set it changes (R2, R3, R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/application/workout/EditLastWorkoutSet.kt`,
`app/src/main/kotlin/com/repflow/app/application/workout/EditWorkoutSet.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRoute.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/test/kotlin/com/repflow/app/application/workout/UndoAndEditLastWorkoutSetTest.kt`,
`app/src/test/kotlin/com/repflow/app/application/workout/CorrectedSetRecommendationTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutActionOrderTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutActionErrorTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutFocusPlumbingTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutObservationRetryTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRestNotificationTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRestPrecedenceTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutSeedTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutErrorRouteTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutLeaveRouteTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutSetEntryTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutPreferencesTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/KeepScreenAwakeTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/progression/ProgressionRecommendationRouteTest.kt`

What: `git mv` of `EditLastWorkoutSet.kt` to `EditWorkoutSet.kt`, the use case and command renamed and
given `setId`, the lookup by id, the view model's `onEditSet`, and the callback carried through the
route and the screen to `WorkoutFocus.kt`, which for now passes the last set's id so the screen
behaves as before. Every other file in the list changes only where it names the old identifiers or
passes the callback (a lambda gains one ignored parameter, a call gains an id); no assertion in
them changes. `UndoAndEditLastWorkoutSetTest` keeps its file name to keep the history readable and
its tests keep their meaning, but they construct `EditWorkoutSetCommand` with the id of the set
they mean.

Test names that say the old thing go in the same pass, with their assertions unchanged:
`correctingTheLastSetKeepsItsOtherFieldsAndReachesOnEditLastSet` becomes
`correctingASetKeepsItsOtherFieldsAndReachesOnEditSet`, `tappedCellsReachOnEditLastSet` becomes
`tappedCellsReachOnEditSet`, and `edit replaces the most recently recorded set's values in place`
becomes `edit replaces the named set's values in place`.

New tests, JVM, in `UndoAndEditLastWorkoutSetTest` unless noted, against the in-memory repository,
each with three sets logged:

1. Editing the first set changes its weight, reps, RPE, pain and technique on a later clock, and
   the other two sets are stored exactly as they were. The edited set keeps its id, order and
   `createdAt`, and `updatedAt` is the later clock.
2. Editing the middle set leaves the last set as it was, so a correction no longer falls on the
   most recent set.
3. A warm-up set edited keeps `isWarmup` as the command says, and a command that clears it stores
   it cleared, on that set only.
4. A set id that is not in the exercise returns `NotFound` and writes nothing; so does an id that
   belongs to a set of another exercise in the same session.
5. A value out of range for the chosen set fails with `ValidationFailed` and leaves every stored
   set as it was.
6. In `CorrectedSetRecommendationTest`: with a planned range of 8-12 and two sets of 12 reps, the
   first logged at RPE 10 and the second at RPE 7 (average 8.5), completing with no correction gives
   `MaintainLoad`, and correcting the **first** set to RPE 7 before completing gives `IncreaseLoad`
   (average 7). `ProgressionPolicyV1` averages, so the test has to tell the two sets apart: with the
   lookup back on the last set the average stays 8.5 and the result stays `MaintainLoad`. After the
   correction and before completing, `findLatestForExercise` still returns nothing, so the suggestion
   doesn't move until completion.
7. In `ActiveWorkoutViewModelTest`: `onEditSet` with the first set's id changes that set in the
   repository and no other, including its pain and technique, which extends the existing edit
   test of the view model. A running rest timer is the same before and after.
8. `Undo last` after a correction: correcting the first set on a later clock and then undoing removes
   the third set, the first keeps its correction and the second is as it was.
9. In `ActiveWorkoutActionOrderTest`: with one set logged and writes held, `onRecordSet` and then
   `onEditSet` naming the first set both wait. Once released, the first set has the correction, the
   new set is stored as logged, and no error shows.

Mutations, one per run, each caught by a test above: the lookup back to `maxByOrNull { it.order }`
(1, 2, 6, 7); the lookup ignoring the exercise (4); the rebuilt set taking a new id (1); `createdAt`
taken from the clock (1); a missing id silently succeeding (4); the view model dropping the set id
and passing the last set's (7, 9); `workingSetRpe = emptyList()` in `CompleteWorkoutSession` (6);
`UndoLastWorkoutSet` choosing by the latest `updatedAt` instead of `order` (8).

Done when: the JVM tests pass, the instrumented test files in the list compile and
`ActiveWorkoutScreenTest` passes on `emulator-5554` with its assertions unchanged, and
`spotlessCheck` and `detekt` pass.
<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Every logged row opens the sheet for its set (R1, R2, R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusSets.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/WorkoutFocusModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/SetEntryStateTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`docs/UX_FLOWS.md`

What: `LoggedSetRow` loses its `isLast` condition on the click and the pencil, and the pencil takes the
board's glyph and `SET_ROW_PENCIL_TAG`. `FocusSetRow.Logged.isLast` is removed and `focusSetRows` stops
computing it. `FocusSetList` takes `onCorrect: (WorkoutSetId) -> Unit`. `WorkoutFocus` holds
`correctingSetId`, looks the set up by id, clears a stale id in a `LaunchedEffect`, and wraps the sheet
in `key(set.id.value)`. `Save` calls `entry.holdNumbers()` and then passes the opened set's id and its
own `isWarmup`. The KDoc of the sheet, the row, `FocusSetRow.Logged` and `SetEntryState.touched` say
what is now true, without "the last set" or `D31`, and `SetCorrectionContent`'s no longer says a restore
can't be mounted through the sheet, since test 5 and test 8 do that. The correct-the-last-set line in
`docs/UX_FLOWS.md` says any logged set of the running workout, that past workouts remain out, and that a
correction leaves the steppers, the rest and the suggestion alone. Before drawing, the board `20n` is
re-read, and any difference from this plan is recorded in the milestone file.

Existing tests: those in `ActiveWorkoutScreenTest` that open the sheet by tapping the last row keep
working unchanged, and any that assert a row other than the last has no pencil or no click are
changed to say the opposite; the implementer lists them in the wrap-up. KDoc in that file that says the
most recently logged set or `D31` (`:733-735`, `openCorrectionSheet`) is reworded. In
`WorkoutFocusModelTest`, the expected rows lose their `isLast` argument.

New tests, in `ActiveWorkoutScreenTest` (instrumented), with three logged sets and one pending row. Those
that change the exercise under the sheet mount `WorkoutFocus` directly:

1. Three pencils exist, counted by `SET_ROW_PENCIL_TAG` with `useUnmergedTree = true` as
   `SettingsScreenTest.kt:327` does, so the pending row has none. Each of the three logged rows has
   the `Correct this set` click action, and the pending row has no click action.
2. Tapping the first row opens the sheet with `Set 1 · <name>` and that set's weight, reps, RPE,
   pain and technique filled in; `Save` after changing the weight reaches `onEditSet` with the
   first set's id and the new weight, not the last set's id.
3. Tapping the second row and then `Cancel`, then tapping the third: the third sheet shows the
   third set's values, with nothing left from the second.
4. A warm-up set that isn't the last opens as `Warm-up set · <name>` and `Save` passes
   `isWarmup = true` with its own id.
5. With the sheet open on the first set, a restore (`StateRestorationTester` around `WorkoutFocus`)
   brings it back on the first set with a tapped value kept, and `Save` carries the first set's id.
6. The sheet open on a set that is then removed from the exercise (the state changes under it)
   closes. After it has closed, a set with the same id put back draws no sheet, which is the one
   thing clearing the id changes. Opening a row afterwards opens that row's own set.
7. `Undo last` still calls `onUndoLastSet` once and nothing else changes; the `Last set` line still
   names the most recent set. CP1 test 8 runs what happens in the store.
8. Restored values don't cross sets. **Written first.** The exercise is held in a plain `var`, not
   state, as `EntryStepperWidthTest.width` is, so the save still sees the first set and only the
   composition after the restore sees it gone. As state it would recompose before the save, the sheet
   would leave composition unsaved, and the test would pass with no fix. With the sheet on the first
   set and a tapped RPE, a restore with the first set gone shows no sheet; tapping the second row opens
   it with its own values, and `Save` reaches `onEditSet` with the second set's id and those values.
9. The steppers hold. With an untouched entry seeded at 82.5 × 8 and one set logged at those values,
   correcting the set to 80 and then changing the exercise as the view model would (the row at 80, the
   seed at 80) leaves the weight stepper at 82.5 while the row reads 80. It still reads 82.5 after a
   restore, and after the set is removed with a new seed, as `Undo last` would.

In `SetEntryStateTest`: after `holdNumbers()` a later seed replaces nothing, and the numbers themselves
aren't changed by the call.

Mutations, one per run, each caught by a test above: the click back on the last row only (1, 2); the
pencil back on the last row only (1); the pencil drawn on pending rows (1); the sheet always opened on
the last set (2, 3); `Save` passing the last set's id (2); the saved id held in plain `remember` (5);
the stale id not cleared (6); the `key(set.id.value)` removed (8); `Save` not calling `holdNumbers()`
(9); `holdNumbers()` not setting `touched` (9, `SetEntryStateTest`).

Done when: the new and changed tests pass on `emulator-5554`, `spotlessCheck` and `detekt` pass, and
the real app has been looked at on the AVD with `Bench` made through the app: three sets logged, the
first corrected (weight, RPE, pain, technique) and the database holding the new values on that row
only with `created_at` unchanged, the middle set corrected after a rotation with the sheet open, a
warm-up set corrected, the last set corrected with the steppers staying put, and `Undo last` removing
only the most recent set. The look covers light and dark at font scale 1.0 and at least one larger
scale, to see the pencil and row at that size, since the pencil now takes width from every row's
summary.
<!-- /CP2 -->

## Wrap-up (after the checkpoints; not a checkpoint)

Full `spotlessCheck`, `detekt`, `lintDebug`, the JVM suite and `connectedDebugAndroidTest` on the AVD
(`AGENTS.md`). Then read issues #34 and #47, and leave the note on #34 saying whether this item closed
its note about a correction changing the wrong set or only tightened one `safety-copy-polish` had closed.

## Known limitations, for the functional review

- Only the exercise in focus and the running workout: past workouts stay as they were
  (`set-edit-past-workouts`).
- `Undo last` removes only the most recent set; no set in the middle can be deleted.
- Correcting a set doesn't move or reorder it, and doesn't touch the rest timer.
- A saved correction holds that exercise's steppers, as `20n` asks. After correcting the last set from
  82.5 to 80 the steppers still read 82.5, so the next set logs 82.5 unless it is changed, and a later
  `Undo last` doesn't re-seed them.
- The pencil is drawn as `20n` draws it, but the whole row is its target, not a 44 dp button of its own.
- The limits `set-edit-fields` listed stand: dismissing the sheet saves nothing and a hard flick can
  close a scrolled one, pain never changes a suggestion, and `Save` with a value the domain refuses
  (0 reps) closes the sheet and shows the error, losing the taps (issue #47).

## Risks and notes

- **Rename churn.** The rename touches many test files, all mechanically. If a test file needs more
  than the identifier and the callback shape changed, that is a finding, not part of the rename.
- **Set numbers.** The subtitle's `Set N` comes from the stored set number, so an earlier set shows
  its own number, and a warm-up shows no number, as today.
- **A set that vanishes under a saved sheet.** This is the one new failure path in the UI. CP2 test 6
  holds the closing and the clearing, and test 8 holds the values.
- **Issues not read yet.** `gh` was refused when this plan was reviewed and when this revision was
  written, so what issues #34 and #47 say rests on the repository's own documents. The wrap-up reads
  both.

## Decisions from plan review, round 1

All findings were checked against the repository and accepted; none is rejected.

| Finding | Resolution |
|---|---|
| I1, stale id | CP2 test 6 now puts a set with the same id back and asserts no sheet |
| I1, state keyed by set | Mutation dropped: `Cancel` takes the sheet out of composition, so `set` never changes under a mounted sheet. Replaced by the `key` mutation, which test 8 catches |
| I1, pencil | `SET_ROW_PENCIL_TAG`, counted in CP2 test 1; the click and the pencil are now separate mutations |
| I2, restored values | Confirmed in the bytecode. `key(set.id.value)` around the sheet, CP2 test 8 written first. The `key` is preferred to explicit keys because the stepper and the pad hold saved state of their own |
| I3, steppers | Follow `20n`: `holdNumbers()`, CP2 test 9, and the cost stated in the known limitations |
| I3, pencil size | `ib` draws a 20 dp glyph in a 44 dp box. The glyph follows the board and the target stays the row, recorded as a difference |
| I4, CP1 test 6 | Logs RPE 10 then 7 and corrects the first, so it fails with the lookup back on the last set |
| I4, `Undo last` | CP1 test 8, with a mutation on `UndoLastWorkoutSet` |
| O1 | `isLast` removed |
| O2 | The wording no longer claims to close #34 outright, the wrap-up says which it is, and CP1 test 9 queues a correction |
| O3 | R2 and R4 map to both checkpoints, and R4's mapping text carries the rows' look |
| O4 | Old test names go in CP1, and KDoc that describes old behaviour goes in CP2, when the behaviour changes |

Reading `20n` also added R5, for what a saved correction leaves alone, and the warm-up row's recorded
difference.
