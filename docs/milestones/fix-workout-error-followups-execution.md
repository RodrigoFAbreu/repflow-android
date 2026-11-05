# fix-workout-error-followups — Execution Plan (Revision 1)

- Work item: `fix-workout-error-followups` (backlog order 21, size 2, `docs/items/fix-workout-error-followups.md`)
- Governing workflow version: `2.2`
- Base commit: `0ce635925d97dff123e892a3eb9fa93b1e7586f7`
- Plan revision: 1

Pull request title: `fix: clear workout errors after a retry, survive a failed read, and make Try again restore the picker`

## Goal and scope

Don't lose data, don't hide failures. Issue #26, from `fix-workout-errors`' review, re-checked
against the base commit (all line numbers read on `0ce6359`):

1. **A stale error can outlive a successful retry.** `ActiveWorkoutViewModel.launchAction`'s
   success path (`ActiveWorkoutViewModel.kt:496-505`) runs `onSuccess()` and leaves `error`
   alone, and so do the success branches of `onCompleteWorkout` and `onAbandonWorkout`. The
   screen clears the field only after its snackbar finishes (`onErrorShown`), so a message from a
   failed `Log set` is still on screen when a second press succeeds. Someone who reads "Couldn't
   complete that" and presses again can log the set twice.
2. **A failed read in a workout action closes the app.** `RecordWorkoutSet` and nine other use
   cases start with `repository.findById(...)` (or `findActiveSession()`), and
   `LocalWorkoutRepository` (`:47-49`) lets any `SQLiteException` out of it. `launchAction`,
   `onCompleteWorkout` and `onAbandonWorkout` have no catch, so the exception reaches
   `viewModelScope` and the app closes. Writes already translate (`insert`/`update` return
   `WorkoutPersistenceError.Unavailable`); the reads are the gap.
3. **`Try again` doesn't restore the picker or the last-session seeds.** `onRetryObservation`
   (`:462`) bumps `retryTrigger`, which only the `content` flow listens to. `availableExercises`
   (`:231-241`) ends in `.catch { emit(emptyList()) }`, so after one failure it is empty for
   good, and `refreshLastPerformances()` runs only at creation and after a finish, so the
   steppers' seeds stay empty after a failed first read.

**Out of scope:** a per-reason message (the one generic message stays); a corrupt row that fails
to map (`WorkoutMappingException`, an `IllegalStateException`) — it is a data fault, not a
transient read failure, and still crashes visibly; read failures outside the workout
repository (`settingsRepository.get()` in `onRecordSet`, `getWorkoutDayContext` in `init`);
any other screen; a visible error for a picker read that fails without a retry button on screen
(it still degrades to an empty list, as today).

## Design (one new mechanism)

The one new mechanism is a **read-failure exception** that carries a failed workout read from the
data layer to the ViewModel, which reports it with the existing error path. Items 1 and 3 reuse
what exists.

**Item 2 — a failed read becomes the existing generic error.**

- New `WorkoutReadFailedException : RuntimeException` in `application/workout` (pure Kotlin).
  `WorkoutRepository.findById` and `findActiveSession` document that they throw it when the
  store can't be read. Returning a result type instead would change the signature of ten use
  cases and their fakes for an event that needs a failing disk; the exception keeps the change at
  the two reads and one catch.
- `LocalWorkoutRepository.findById` and `findActiveSession` catch `SQLiteException` (a narrow
  type that covers its subtypes; `CancellationException` is not one, and there is no broad
  `catch`) and rethrow `WorkoutReadFailedException` with the original as cause.
  `WorkoutMappingException` is not caught (out of scope above).
- In the ViewModel, one private helper wraps a use-case call:

  ```kotlin
  private suspend fun guardedRead(call: suspend () -> DomainResult<*, WorkoutOperationError>) =
      try { call() } catch (_: WorkoutReadFailedException) {
          DomainResult.Failure(WorkoutOperationError.PersistenceUnavailable)
      }
  ```

  `launchAction`, `onCompleteWorkout` and `onAbandonWorkout` call the use case through it, so a
  failed read reaches the same `toReason()` → `PERSISTENCE_UNAVAILABLE` → the existing snackbar
  as a failed write. A failed `Complete` also returns `_finish` to `Idle`, as any failure does.
- `onRecordSet` runs `startRestTimer` after the set is saved. A read failure there must not turn
  a saved set into a reported failure (the entry would stay filled and a retry would log it
  twice), so that one call is wrapped in its own `try`/`catch (WorkoutReadFailedException)` and
  the failure is ignored, exactly as a `Failure` result from `startRestTimer` already is. The set
  is saved; the rest timer is a convenience.

**Item 1 — clear on success.** `launchAction`'s success path, and the success branches of
`onCompleteWorkout` and `onAbandonWorkout`, set `error` to `null` first. The screen's
`LaunchedEffect(uiState.errorMessage)` restarts on `null`, which cancels the showing snackbar, so
the old message disappears the moment a retry succeeds. A later failure still shows normally.

**Item 3 — `Try again` re-reads everything the screen shows.** `availableExercises` is wrapped
in `retryTrigger.flatMapLatest { ... }` with its `.catch` kept inside, so each retry subscribes
afresh, and `onRetryObservation` also calls `refreshLastPerformances()`. No new state or flow.

No Room entity, schema or migration change; no new dependency or module; nothing touches the
network. No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | After a failed workout action, a later successful action clears the error, so the old message does not stay on screen. |
| R2 | A SQLite failure while reading a workout for an action shows the generic action-failed message and does not close the app; a saved set is never reported as failed because the rest timer's read failed. |
| R3 | `Try again` on the load-failure state also re-reads the exercise picker and the last-session seeds. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | A failed workout read shows the error instead of closing the app (R2) | - | 2 | 1 |
| CP2 | Clear the error on success and make Try again re-read the picker and seeds (R1, R3) | CP1 | 2 | 1 |

<!-- CP1 -->
### CP1 — A failed workout read shows the error instead of closing the app (R2)

**Files:**
`app/src/main/kotlin/com/repflow/app/application/workout/WorkoutReadFailedException.kt` (new),
`app/src/main/kotlin/com/repflow/app/application/workout/WorkoutRepository.kt` (KDoc),
`app/src/main/kotlin/com/repflow/app/data/workout/LocalWorkoutRepository.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/test/kotlin/com/repflow/app/application/workout/InMemoryWorkoutRepository.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModelTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/workout/LocalWorkoutRepositoryReadFailureTest.kt` (new).

Work: the item 2 design above.

Tests:

1. `InMemoryWorkoutRepository` gains a switch that makes `findById` and `findActiveSession`
   throw `WorkoutReadFailedException`. `ActiveWorkoutViewModelTest`: with the switch on, each of
   `onRecordSet`, `onCompleteWorkout` and `onAbandonWorkout` ends with
   `errorMessage == PERSISTENCE_UNAVAILABLE` and no exception out of the ViewModel scope; a failed
   `onCompleteWorkout` leaves `finish` at `Idle`. Each must fail on the base commit (the
   exception escapes), which the implementer confirms before the edit.
2. A set saved while only the rest timer's read fails: with auto-start on, the switch turns on
   after `recordWorkoutSet`'s own read, so `startRestTimer` is the call that throws; the test
   asserts the set is stored, `errorMessage` is `null` and `onSaved` ran.
3. `LocalWorkoutRepositoryReadFailureTest` (instrumented, in-memory Room): a database closed
   under the repository makes `findById` and `findActiveSession` throw
   `WorkoutReadFailedException` (not a raw `SQLiteException`), and `CancellationException` is not
   swallowed. Confirm on the base commit that the raw exception comes out.

Verified with the new instrumented class on `emulator-5554`, `ActiveWorkoutViewModelTest`, then
the CP2 full runs below.

<!-- /CP1 -->

<!-- CP2 -->
### CP2 — Clear the error on success and make Try again re-read the picker and seeds (R1, R3)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModel.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutViewModelTest.kt`.

Work: the item 1 and item 3 designs above.

Tests (`ActiveWorkoutViewModelTest`, each failing on the base commit):

1. A failed action sets `errorMessage`; a following successful action (and a successful
   complete, and a successful abandon, each in their own test) leaves it `null` without
   `onErrorShown` being called. A new failure afterwards shows again.
2. The picker: the exercise source throws on first subscription, so `exercises` is empty; after
   `onRetryObservation()` with the source healthy, `exercises` holds the library's rows.
3. The seeds: the first `observeCompletedSessions` read fails, so the entry has no last
   performance; after `onRetryObservation()` the next read succeeds and the seed appears.

Verified with `ActiveWorkoutViewModelTest`, then the full JVM run, `spotlessCheck`, `detekt`,
`lintDebug`, and the full `connectedDebugAndroidTest` suite on `emulator-5554`
(`AGENTS.md` requires the full suite before a milestone is done).

<!-- /CP2 -->

## Self-review notes

- *Is an exception the right shape?* The codebase uses `DomainResult` for write failures, but
  changing `findById` touches ten use cases, their fakes and tests to cover one transient
  fault. The exception is documented on the interface and caught in one helper; it is the
  smaller change. `WorkoutMappingException` already makes these reads throw, so the contract
  was never "never throws".
- *Does the catch swallow cancellation?* `SQLiteException` and `WorkoutReadFailedException` are
  narrow; `CancellationException` passes through both catches.
- *Clearing on success could hide a real concurrent failure.* Two actions in flight (a set and
  a rest adjustment) can finish in either order, and a success after a failure clears the
  message. The failed action's own state (the entry stays filled) still tells the user, and a
  message that outlives a success is the worse error (R1). Accepted.
- *Picker failure stays silent without a retry button.* Out of scope; unchanged from today.
- *Usability:* no new strings or screens; the user sees the one existing message.
