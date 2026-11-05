# fix-workout-errors — Execution Plan (Revision 3)

- Work item: `fix-workout-errors` (backlog order 18, size 1, `docs/items/fix-workout-errors.md`)
- Governing workflow version: `2.2`
- Base commit: `408bc4e1a8abb5e7592fd2d8bb41b06eb882a3a2`
- Plan revision: 3
- Pull request title (conventional commit, squash-merged): `fix: show workout action failures and make Retry work`

## Goal and scope

Don't lose data, don't hide failures. Two findings from the 2026-11-04 council,
re-checked against today's code (all line numbers below were re-read on the base
commit):

- **A-1** A failed workout action shows no message. `ActiveWorkoutUiState.errorMessage`
  is set by `ActiveWorkoutViewModel.launchAction`, `onCompleteWorkout` and
  `onAbandonWorkout`, but nothing draws it (`ActiveWorkoutUiState.kt:27` is its only
  use). Worse, `WorkoutFocus.kt`'s `Log set` handler calls `entry.clearAfterSet()`
  straight after `onRecordSet(...)`, before the save result exists, so a failed save
  also wipes RPE, pain, technique and the warm-up flag the user just entered.
- **A-12** The `Retry` button on the workout's load-failure state calls
  `viewModel::onErrorShown` (`ActiveWorkoutRoute.kt:141`), which only clears the
  action-error field. The `content` flow has already `catch`-ed into
  `ObservationFailed` and completed, so nothing re-observes: the button does nothing.
  The five `*_retry` strings also say "Retry"; the product wording is "Try again".

**Out of scope:** any other screen's error handling, the domain/application layers
(the failure already arrives as `WorkoutOperationError` → `ActiveWorkoutErrorReason`),
a per-reason message (the reason stays deliberately generic), and retrying a failed
action automatically.

## Design (one new mechanism)

The one new mechanism is a **re-observe trigger** for `Retry`. Everything else reuses
what exists, apart from a small value the `Log set` callback holds on to (design
item 2): no new state, flow or UI.

1. **Show the error.** `ActiveWorkoutScreen` holds a `SnackbarHostState` and a
   `LaunchedEffect(uiState.errorMessage)`, the same shape as `HomeScreen.kt:95-101`.
   The effect shows one generic message (new string `workout_active_action_failed`:
   "Couldn't complete that. Try again.", in the style of the app's existing
   `*_operation_failed` strings) with `SnackbarDuration.Short` and no action, so it
   can't block later messages (the Indefinite default recorded at
   `ExerciseListScreen.kt:163` comes only with an action label). It then calls a new
   required `onErrorShown` callback, next to `onRetry`, so the ViewModel clears the
   field and the same failure can show again. The card is the design's existing
   `ExerciseListSnackbar` (`ExerciseListScreen.kt:206`, already reused by
   `BackupScreen.kt:97` and `ArchivedScreen.kt:71`), not the stock inverse `Snackbar`.

   **Where it's drawn.** Not in the root `Box`. That `Box` knows neither the rest
   strip's height (absent, the done strip, or a `FlowRow` that wraps with font scale,
   `ActiveWorkoutScreen.kt:514`) nor the focus bar's (about 85dp), both drawn inside
   `WorkoutFocus`'s `Column` (`WorkoutFocus.kt:175-176`), so bottom-aligning there
   would cover `Log set` just as the message says "Try again". Instead, `WorkoutMode`
   passes the `SnackbarHostState` to `WorkoutFocus` and `WorkoutBoard`, and each wraps
   its `weight(1f)` scroller in `Box(Modifier.weight(1f).fillMaxWidth())`: the scroller
   fills that `Box` and the `SnackbarHost` is its second child, at
   `Alignment.BottomCenter`. The snackbar then sits on the scroll area's bottom edge,
   directly above `restStrip()` whatever the strip's height, and can't cover `Log set`,
   `Next` or the strip's controls. It's an overlay, so nothing moves when it appears;
   it hides the bottom of the scrolled content for the few seconds it's up.

   The state lives above both modes, so a message carries across a switch between
   board and focus. The finish sheet, the abandon dialog, the picker and the set
   correction sheet all close before they act (`ActiveWorkoutScreen.kt:357`, `:371`,
   `:383`, `WorkoutFocus.kt:206`), so no sheet covers a failure from those.
2. **Clear the entry only on success, and only what that set saved.**
   `ActiveWorkoutViewModel.onRecordSet` gains a trailing `onSaved: () -> Unit = {}` that
   `launchAction`'s success path invokes (on the main dispatcher, where the ViewModel's
   `viewModelScope` runs). On failure the entry is left exactly as typed, so the user
   can press `Log set` again. The callback type threaded through `ActiveWorkoutRoute`,
   `ActiveWorkoutScreen`, `WorkoutMode`/`WorkoutFocus` grows by that one parameter.

   The entry stays editable while the save suspends, so the callback can't clear
   unconditionally: with RPE 7 submitted and changed to 8 during the save, a plain clear
   would wipe the 8, which nothing has saved. So `SetEntryState` gains
   `submittedDetail()`, returning a small `SubmittedDetail(rpe, pain, technique,
   isWarmup)`, and `clearAfterSet` takes it: each of the four fields clears only if it
   still equals its value in the snapshot. `WorkoutFocus`'s `Log set` handler takes the
   snapshot first, before `onRecordSet`, passes `{ entry.clearAfterSet(submitted) }` as
   `onSaved`, and no longer clears inline. The snapshot reads the entry's own fields,
   not the `takeIf { extraFieldsOn }` values the handler submits, so a value hidden by
   `Off` still clears after the set, as it does today.
3. **Make `Retry` work.** The ViewModel's `content` becomes
   `retryTrigger.flatMapLatest { <existing combine …onStart…catch> }`, with
   `retryTrigger` a `MutableStateFlow<Int>`; new `onRetryObservation()` increments
   it, so a failed observation is re-subscribed and shows `Loading` again. The
   route wires `onRetry = viewModel::onRetryObservation`; `onErrorShown` keeps its
   one job (clearing the action error).
4. **Wording.** In `strings.xml` change the value `Retry` to `Try again` for the five
   `*_retry` strings (`exercise_list_retry`, `training_plan_list_retry`,
   `progression_retry`, `workout_active_retry`, `home_history_retry`). Resource names
   stay (renaming them is churn). `archived_retry` already reads `Try again`. No test
   asserts the literal text: they resolve the resource (for example
   `ExerciseListScreenTest.kt:211`), so none changes for this.

No Room, domain or navigation change; no schema change, so no migration. Domain stays
pure Kotlin; no new dependency or module; nothing touches the network. There is no
"Open decision" row in `docs/TECHNICAL_DECISIONS.md` that this plan decides.

## Requirements

| Id | Requirement |
|---|---|
| R1 | A failed workout action (`Log set`, undo, edit, add exercise, `+15s`, `-15s`, `Skip rest`, finish, abandon) shows a generic error snackbar on the workout screen, above the rest strip and `Log set`; the same failure can show again afterwards. |
| R2 | `Log set` clears RPE, pain, technique and the warm-up flag only after the save succeeds, and leaves any of them changed since the tap; on failure the entry is unchanged. |
| R3 | `Retry` on the workout's load-failure state re-observes the session and leaves `ObservationFailed` when the read recovers. |
| R4 | The five `*_retry` strings read "Try again". |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Show a failed action and clear the entry only on success (A-1) | - | 2 | 1 |
| CP2 | Make Retry re-observe and say Try again (A-12) | CP1 | 2 | 1 |


<!-- CP1 -->
### CP1 — Show a failed action and clear the entry only on success (A-1; R1, R2)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRoute.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutBoard.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModelTest.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/SetEntryStateTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutSetEntryTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/KeepScreenAwakeTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutPreferencesTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutErrorRouteTest.kt` (new).

Work: design items 1 and 2. The four `androidTest` files that build
`ActiveWorkoutScreen` gain `onErrorShown` and the ninth `onRecordSet` parameter.
Fakes that only read the recorded values ignore that parameter
(`KeepScreenAwakeTest.kt:68`, `ActiveWorkoutPreferencesTest.kt:83`,
`ActiveWorkoutScreenTest.kt:358`). Two must call it, because they model a successful
save:

- `ActiveWorkoutScreenTest.tappingLogSetKeepsWeightAndRepsAndClearsTheRest` (`:417`)
  asserts that the warm-up flag clears. Without the call it compiles and fails on a
  device.
- `ActiveWorkoutSetEntryTest.kt:96`, behind a switch that the new failure and
  deferred-success tests below set: call `onSaved`, drop it, or hold it for the test to
  call.

`SetEntryStateTest`'s existing `clearAfterSet` test moves to the new signature.

Tests (a failing test is written first for each):
- Entry unit, `SetEntryStateTest`: with all four fields set, `clearAfterSet(entry.submittedDetail())`
  clears them and keeps weight, reps, seconds and `touched`. For each of RPE, pain and
  technique in turn, a value changed after the snapshot stays and the other fields still
  clear. A field empty in the snapshot and set afterwards stays, and so does the warm-up
  flag switched on afterwards. One cleared or switched off afterwards stays that way.
- ViewModel unit: with `workoutRepository.nextUpdateFailure =
  WorkoutPersistenceError.Unavailable`, `onRecordSet` sets `errorMessage` to
  `PERSISTENCE_UNAVAILABLE` and does **not** invoke `onSaved`; a succeeding save invokes
  it exactly once; `onErrorShown` clears the field; a failed `onCompleteWorkout` and
  `onAbandonWorkout` also set it.
- Instrumented screen, message: with `errorMessage` set the snackbar text is displayed
  and `onErrorShown` is called; with the field null no snackbar. The same failure shows
  twice: error set, shown, cleared by `onErrorShown`, same error set again, shown again.
- Instrumented screen, position (`getUnclippedBoundsInRoot`): in focus mode the
  snackbar's bottom is at or above `Log set`'s top, with no rest strip and with one; with
  one it's also at or above `Skip rest`'s top. On the board with a rest strip it's at or
  above `Skip rest`'s top.
- Instrumented set entry: enter RPE, pain, technique, warm-up, tap `Log set` with an
  `onRecordSet` that does not call `onSaved` → the values stay; with one that does →
  they clear and weight and reps stay (the existing rule).
- Instrumented set entry, deferred success: with an `onRecordSet` that holds `onSaved`,
  enter RPE 7 and warm-up, tap `Log set`, change RPE to 8, then call the held callback.
  RPE 8 stays selected and the warm-up flag clears. This fails with an unconditional
  clear, and fails if the handler takes the snapshot when the callback runs rather than
  at the tap.
- Instrumented route, new `ActiveWorkoutErrorRouteTest`: builds `ActiveWorkoutRoute`
  with a real `ActiveWorkoutViewModel`, as `ActiveWorkoutLeaveRouteTest.kt:398` and
  `:492` do, over a test-local `WorkoutRepository` that delegates to a
  `LocalWorkoutRepository` on an in-memory database and can fail its next `update` with
  `WorkoutPersistenceError.Unavailable`. A failed `Log set` shows the snackbar and keeps
  the entered RPE and warm-up flag. Once the message has gone, a second failure shows it
  again, which fails if `onErrorShown` is miswired. A later successful `Log set` records
  the set and clears the entry, which fails if `onSaved` isn't threaded through.
<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Make `Retry` re-observe and say "Try again" (A-12; R3, R4)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRoute.kt`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModelTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutErrorRouteTest.kt`.

Work: design items 3 and 4.

Tests:
- ViewModel unit: over a small delegating `WorkoutRepository` (`InMemoryWorkoutRepository`
  has no observation-failure hook) whose observation fails once and then succeeds, the
  state goes `ObservationFailed` → (after `onRetryObservation`) `Active`. The `Loading`
  in between isn't asserted, because `uiState` is a `StateFlow` and can conflate it. A
  second call while `Active` does not break the stream.
- Instrumented screen: the load-failure state shows a button labelled `Try again`
  and tapping it calls `onRetry`.
- Instrumented route: `ActiveWorkoutErrorRouteTest`'s repository also fails its first
  `observeActiveSession` subscription. The load-failure state shows, tapping `Try again`
  shows the board with the session, which fails while the route still wires `onRetry` to
  `onErrorShown`.
- A grep in the verification step shows no remaining `>Retry<` string value.

Verification for the whole item (run once, after CP2), using `AGENTS.md`'s commands:
`./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug
assembleDebugAndroidTest`, then the full `./gradlew connectedDebugAndroidTest` on the
AVD (via `start-avd.sh`, within the memory caps). CI only compiles instrumented tests,
so this device run is the only one that executes them. There is no manual forced-failure
check: the app has no debug source set and no fault hook in `app/src/main`, so
`ActiveWorkoutErrorRouteTest` forces those failures instead.
<!-- /CP2 -->

## Review decisions

Plan review round 1 (`LOCAL_MODEL_PLAN_REVIEW`, verdict `REVISE`). Each finding was
checked against the code on the base commit. All are accepted and none is rejected.

| Finding | Decision and evidence | Applied in |
|---|---|---|
| I-1 snackbar placement | Accepted. The strip and bar are drawn inside the modes' `Column`s (`WorkoutFocus.kt:175-176`, `WorkoutBoard.kt:120`), and the only `Box` precedent (`HomeScreen.kt:142`) has no bar to avoid. | Design item 1, CP1 position test |
| I-2 route wiring untested | Accepted. CP1 and CP2 tested the ViewModel and the screen, never the route line that holds A-12. The manual check is dropped rather than specified, because nothing in `app/src/main` can force a failure. | `ActiveWorkoutErrorRouteTest` in CP1 and CP2 |
| I-3 verification commands | Accepted. `ktlintCheck` doesn't exist (`app/build.gradle.kts:132`, `AGENTS.md:73`), and `AGENTS.md:84-88` calls for the full suite. The test at `ActiveWorkoutScreenTest.kt:417` does need its fake to call `onSaved`. | CP1 Work, CP2 Verification |
| I-4 bundle contents | Accepted. Also added `WorkoutBoard.kt`, `ExerciseListScreen.kt` and `ActiveWorkoutLeaveRouteTest.kt`, which this revision now relies on. | The bundle's context list |
| O-1 snackbar look | Accepted: `ExerciseListSnackbar`. | Design item 1 |
| O-2 test files in `**Files:**` | Accepted. | CP1 Files |
| Missing tests (repeat failure, `nextUpdateFailure`) | Accepted. | CP1 Tests |
| Usability (R1's list) | Accepted. `launchAction` also carries `+15s`, `-15s` and `Skip rest`. | R1, self-review notes |

The revision also corrects two things from revision 1 that the review didn't raise:
design item 4 no longer expects test changes for the wording, and CP2's ViewModel test
no longer asserts a `Loading` state that a `StateFlow` can conflate.

Plan review, manual external round 1 (`MANUAL_EXTERNAL_PLAN_REVIEW`, verdict `REVISE`,
on revision 2). Its finding was checked against the code on the base commit and is
accepted.

| Finding | Decision and evidence | Applied in |
|---|---|---|
| I-1 a delayed clear erases newer input | Accepted. Today's handler clears synchronously in the tap (`WorkoutFocus.kt:178-193`), so later edits survive. Revision 2's `entry::clearAfterSet` would run after the suspending save, while the fields stay editable (`WorkoutFocusEntry.kt:491`, `:495`, `:503`, `:601`). The finding offered two remedies, and this plan keeps the edits. Blocking them instead would need an in-flight flag through the ViewModel, the UI state and every entry control, which is a second mechanism under the item's rule. | Design item 2, R2, CP1 Files and Tests |
| Missing test (deferred success) | Accepted. | CP1 Tests, set entry and entry unit |

## Self-review notes (SELF_REVIEWING_PLAN)

- **Size:** two checkpoints, one mechanism, one pull request: within the item's
  "split it if it needs two mechanisms" rule, since the snackbar and the callback
  reuse existing patterns, and the snapshot is one local value that a callback holds.
- **Data-loss risk:** the clear-on-success callback runs on the ViewModel's scope; a
  rotation between tap and result leaves the (saved-state) entry uncleared, which is
  the safe direction. An edit made during the save survives, field by field. A field
  changed and changed back before the save returns counts as unchanged and clears; that
  value is the one the set saved.
- **Double tap:** a second tap before the first result logs two sets, as it does today.
  The second set now carries the same RPE, pain, technique and warm-up flag as the
  first, because the entry isn't cleared until the first save returns. Each tap holds
  its own snapshot, so with RPE changed between the taps, the first save leaves the new
  value and the second clears it. An in-flight guard would be a second mechanism, so
  this plan leaves it.
- **Snackbar queue:** clearing the field after display lets an identical repeated
  failure re-trigger the effect.
- **Message with no host:** `showSnackbar` waits for a composed host, and only `Active`
  draws one. Actions start only from `Active` (`activeSessionId() ?: return`), so a
  message can wait only if the observation fails while an action is in flight. It then
  shows after `Try again`.
- **Known limits, unchanged by this plan:**
  - `entries` live in `WorkoutMode`, so a mid-workout observation failure followed by
    `Try again` loses whatever was typed but not logged. Today the user can't recover
    from that state at all.
  - `AdjustRestTimer` returns `NotFound` when no timer is running (`AdjustRestTimer.kt:26`),
    so a `+15s` landing just after `Skip rest` could show the message. The strip is gone
    by then, so the window is small, and hiding it would mean filtering by reason.
  - `onRecordSet` ignores `startRestTimer`'s result, so a failed rest start after a saved
    set shows nothing. The set is saved, so the entry still clears.
- **Offline-first:** no network involved.
