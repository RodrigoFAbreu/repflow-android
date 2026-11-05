# fix-workout-actions-in-order — Execution Plan (Revision 2)

- Work item: `fix-workout-actions-in-order` (backlog order 23, size 1, `docs/items/fix-workout-actions-in-order.md`)
- Governing workflow version: `2.2`
- Base commit: `f4e4c6e6aa2b31ef8d783e0c2ee01e6cc3088022`
- Plan revision: 2

Pull request title: `fix: run workout actions one after another so a set logged during a restore isn't lost`

## Goal and scope

Don't lose data, don't hide failures. Issue #34, item 1, re-checked against the base commit.

Each use case is a read-modify-write of the whole session: `RecordWorkoutSet` calls
`repository.findById` (`RecordWorkoutSet.kt:36`), builds the session with its set, and calls
`repository.update` (`:71`). While a restore runs, writes wait for it. Reads depend on the phone:
on a normal phone, in WAL, they return the old data at once, and on a one-connection journal they
wait too (`restore-safety-snapshot-findings.md`, R3-O1 and R2-O2; the KDoc of
`PreRestoreSnapshotRoundTripTest`). So on a normal phone two workout actions during a restore both
read the old session and both wait at `update`, and the second write replaces the session without
the first one's set. Both report success; the first set is in neither the restored data nor the
safety copy. A set followed by Finish loses the set the same way.

The cause is in `ActiveWorkoutViewModel`: `launchAction` (`:511`), `onCompleteWorkout` (`:429`)
and `onAbandonWorkout` (`:449`) each start their own `viewModelScope.launch`, so nothing orders
them. The same overlap can happen without a restore whenever an action reads before the previous
one has written; a restore makes it certain.

**Out of scope:** the other items on issue #34; making use cases atomic in the repository (a lock
in the ViewModel covers every action this screen starts, and the one other writer of a running
workout, Home's resume card, `HomeViewModel.onAbandonWorkout` (`HomeViewModel.kt:191`), doesn't
normally overlap with it: leaving the workout pops its entry (`RepFlowNavigationActions.kt:17`),
which clears this ViewModel); the Home screen's start button, which has its own in-flight guard.

## Design (one new mechanism)

One `Mutex` in `ActiveWorkoutViewModel`, `actionLock`. `launchAction`, `onCompleteWorkout` and
`onAbandonWorkout` take it around the use-case call and the handling of its result:

```kotlin
viewModelScope.launch {
    actionLock.withLock {
        when (val result = guardedRead(action)) { /* unchanged */ }
    }
}
```

- Order: coroutines started by `viewModelScope.launch` reach `withLock` in call order, and
  `Mutex` is fair (first waiter first), so actions run in the order the user pressed.
- The lock is held while the action waits, at its write on a normal phone and at its read on a
  one-connection journal, so in both cases the next action reads only after this one has written.
- The lock covers the use case, the error update and `onSuccess`, so the "success clears the
  error" rule (`fix-workout-error-followups` R1) holds in order: an earlier action's late failure
  can no longer overwrite a later action's success.
- Every rest-timer write takes the lock: `onAddRestTime`, `onRemoveRestTime` and `onSkipRestTimer`
  go through `launchAction` (`:372-385`), and the auto-started timer runs inside `onRecordSet`'s
  action (`:365`). `RestTimerExpiryHandler` only reads.
- A cancelled coroutine releases the lock (`withLock` is cancellation-safe), and
  `CancellationException` is not caught.
- `onSuccess` callbacks (`onSaved`) only clear the screen's entry and cannot start an action
  synchronously, so the non-reentrant lock cannot deadlock on them.
- `onCompleteWorkout`'s existing in-flight guard (`_finish`) is unchanged.

No Room entity, schema or migration change; no new dependency or module; nothing touches the
network. No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | Workout actions started from the workout screen run one after another in the order they were started, so two actions queued during a restore both keep their effect: a set then another set, and a set then Finish. |
| R2 | Ordering also holds for the error message: a failed earlier action cannot overwrite a later success's cleared state. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Run workout actions one after another (R1, R2) | - | 1 | 1 |

<!-- CP1 -->
### CP1 — Run workout actions one after another (R1, R2)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/test/kotlin/com/repflow/app/application/workout/InMemoryWorkoutRepository.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutActionOrderTest.kt` (new).

Work: the design above. The ViewModel gains `actionLock` and three `withLock` blocks.

Tests (new `ActiveWorkoutActionOrderTest`, real use cases over `InMemoryWorkoutRepository`; a new
test class because `ActiveWorkoutViewModelTest` is over detekt's `LargeClass` limit). Every result
stated here was measured for this revision on a throwaway copy of the base commit, JVM only, and
matches the review's own measurements. Run tests 2 to 5 before the edit to confirm their failures.

1. `InMemoryWorkoutRepository` gains `holdWrites: CompletableDeferred<Unit>?`; while set, `update`
   suspends on it first, before it checks `nextUpdateFailure`, which is what a restore does to
   writes. Reads are not held: with the hold before the read, the first action reads, writes and
   finishes before the second resumes, so nothing overlaps and the tests pass without the lock.
2. `a set then a set both stay in press order`: with the hold on, `onRecordSet` twice for the same
   exercise with different reps; the hold is released; the stored session's reps are `[8, 9]`, with
   no error. On the base commit: `expected:<[8, 9]> but was:<[9]>`.
3. `a set then Finish keeps the set`: with the hold on, `onRecordSet` then `onCompleteWorkout`; the
   hold is released; the session is completed and holds the set. On the base commit:
   `expected:<1> but was:<0>`.
4. `a set then Abandon keeps the set`: with the hold on, `onRecordSet` then `onAbandonWorkout`; the
   hold is released; the session is abandoned, holds the set, and there is no error. On the base
   commit: `expected:<1> but was:<0>`.
5. `an earlier failure cannot overwrite a later success`: with the hold on, `onRecordSet`, then
   `advanceUntilIdle`, so the first action is waiting at its write. `holdWrites` goes back to
   `null`, so only that write waits. A second `onRecordSet`, then `advanceUntilIdle`: on the base
   commit it has already succeeded; with the lock it hasn't started. Then `nextUpdateFailure`
   is set, the hold is released, and `errorMessage` is `null`. On the base commit:
   `expected null, but was:<PERSISTENCE_UNAVAILABLE>`. Holding both writes wouldn't test this:
   waiters resume first in, first out, so the failure lands first and the test passes without the
   lock.
6. Without the hold, a single action behaves as before: `ActiveWorkoutActionErrorTest` and
   `ActiveWorkoutViewModelTest` stay green, unchanged. With the lock, all 107 tests in
   `presentation.workout` passed: 103 existing plus tests 2 to 5.

Mutation check, removing the lock from one entry point at a time:

| Lock removed from | Tests that fail |
|---|---|
| `launchAction` | 2, 3, 4 and 5 |
| `onCompleteWorkout` | 3 |
| `onAbandonWorkout` | 4 |

Verified with the new class, the `presentation/workout` JVM package and `LayerBoundaryTest`, then
the full JVM run, `spotlessCheck`, `detekt`, `lintDebug`, and the full `connectedDebugAndroidTest`
suite on `emulator-5554` (`AGENTS.md` requires the full suite before a milestone is done).

<!-- /CP1 -->

## Self-review notes

- *Is a ViewModel lock enough?* It orders every action this screen starts. The only other callers
  of `repository.update`'s ten use cases are Home's resume-card abandon, which doesn't normally
  overlap (see Out of scope), and `HistoryViewModel`'s invalidate, which edits only completed
  sessions. A repository-level transaction would order those too, but it would change ten use
  cases. Accepted.
- *Does the lock hide a hang?* An action waiting on a restore now also holds back later actions.
  That is the intent: running them concurrently is the bug. The screen already shows nothing
  different while an action waits.
- *Could the lock starve the rest timer?* A rest-timer write takes the lock like any other action,
  so during a restore it waits its turn, and that is what makes the auto-started timer safe.
  `RestTimerExpiryHandler` only reads, so the rest alert never waits on the lock.
- *Usability:* no new strings or screens. A user who taps quickly during a restore no longer has
  one tap silently undo another. What the restore then does to the workout stays as it is today for
  a single action (by reading, not run): with a file that doesn't hold this workout both actions
  report an error, and with a file that does, the first write replaces the restored tree with the
  pre-restore one plus the set, because `update` rewrites the whole tree.
- *A double press during a restore* now logs two identical sets, where the base code ended with
  one. The entry stays filled until the save lands (`WorkoutFocus.kt:185-204`). R1 asks for this
  and Undo corrects it, so nothing changes here; `safety-copy-polish`'s Restoring screen may narrow
  the window.
- *A set still waiting when the user leaves the workout* is lost with no message, by reading, not
  run: leaving pops the entry, which cancels `viewModelScope` and the waiting action. The base code
  does the same, so this is not a regression, but it is a gap in what the item's title promises.
  It is a follow-up, not part of this size-1 item; record it on issue #34 at wrap-up.

## Review decisions (round 1, `LOCAL_MODEL_PLAN_REVIEW`)

Checked against the repository, and all accepted. The measured results were re-run for this
revision on a throwaway copy of the base commit, now deleted.

| Finding | Decision |
|---|---|
| B1 | Confirmed by measurement and by reading: `InMemoryWorkoutRepository` has no suspension point between a read and `update`, and R3-O1 says reads go through and writes wait. The hold moved to `update`, and the premise is corrected in Goal and scope, R1, the mapping and the self-review. |
| I1 | Confirmed by measurement. Test 5 has the shape the finding asks for, with one difference: it needs no second, one-shot hold, because setting `holdWrites` back to `null` after the first action reaches its write leaves only that write waiting. Measured: base code fails with `PERSISTENCE_UNAVAILABLE`, and the full lock passes. |
| I2 | Confirmed by measurement. Test 4 asserts the set, and the mutation check covers all three entry points. |
| O1 | Confirmed: `HomeViewModel.kt:191`, `RepFlowNavigationActions.kt:17`. Out of scope and the self-review corrected. |
| O2 | Confirmed: the rest-timer calls are at `ActiveWorkoutViewModel.kt:365` and `:372-385`, and `RestTimerExpiryHandler` only reads. The out-of-scope clause and the self-review corrected, and the Design says it. |
| O3 | Confirmed by reading `LocalWorkoutRepository.update`. Usability note reworded. |
| O4 | Confirmed by reading `WorkoutFocus.kt:185-204`. Recorded in the self-review; no change. |
| O5 | Confirmed by reading. Recorded in the self-review as a follow-up for issue #34 at wrap-up; not fixed in this item. |
| Missing tests | Test 2 asserts press order through the reps. |
| Required acceptance criteria | 1: Goal and scope, R1 and the mapping. 2: test 1, with the failures in tests 2 and 3. 3: test 5. 4: test 4. 5: the mutation check table. |
