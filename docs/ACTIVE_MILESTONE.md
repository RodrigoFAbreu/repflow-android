# Active Milestone

## Milestone

Milestone 5 — Recovery and Futsal — **COMPLETE**

## Goal

Let a user record daily recovery signals (sleep quality, energy, leg
DOMS, heel stiffness, pain while walking, heavy legs — each 0-4) and
futsal sessions (duration + session RPE, with a derived load), and
surface a read-only summary of that context on the active-workout
screen. Recovery/futsal entries are independent, date-scoped records
(not tied to a workout session) with at most one row per calendar date.

## Current checkpoint

CP7 complete: Milestone 5 is fully implemented, verified and documented.
Full verification suite passed: `testDebugUnitTest` (full suite, 249
tests — see "Known pre-existing flaky test" below), `spotlessCheck`,
`detekt`, `lintDebug`, `connectedDebugAndroidTest` (full instrumented
suite on `emulator-5554`, 0 failures). Manual smoke test: installed APK,
launched app, ran a 300-event `monkey` fuzz pass over the UI (covering
the new Recovery/Futsal screen and the active-workout banner) with no
crashes in logcat. Room schema `5.json` is exported and tracked.

Milestones 3 and 4 remain complete and committed; see
`docs/milestones/completed/`.

## Checkpoint checklist (Milestone 5)

- [x] CP1 — Domain: `RecoveryEntry` + `FutsalSession` + tests
- [x] CP2 — Application: `RecordRecoveryEntry`/`RecordFutsalSession`/`GetWorkoutDayContext` + tests
- [x] CP3 — Room persistence: migration 4→5, entities/DAOs/mappers/repositories
- [x] CP4 — Instrumented migration + DAO tests on device
- [x] CP5 — Presentation: combined recovery/futsal entry screen
- [x] CP6 — Workout-day context banner on the active-workout screen
- [x] CP7 — Full verification, manual smoke test, docs + archival

## Approved decisions (quick reference, Milestone 5)

- Recovery scale fields (`sleepQuality`, `energy`, `legDoms`,
  `heelStiffness`, `painWhileWalking`, `heavyLegs`) are `Int` in `0..4` —
  an assumption since `docs/UX_FLOWS.md` doesn't specify exact bounds;
  low-risk, easily revisited
- `FutsalSession.load` is a derived property (`durationMinutes *
  sessionRpe`), never stored independently — same pattern as
  `RestTimer.remainingSeconds`
- At most one `RecoveryEntry`/`FutsalSession` per calendar date;
  re-recording the same date replaces it via `update()`, enforced by a
  unique Room index on `entry_date` plus `OnConflictStrategy.REPLACE`
- Recovery-entry and futsal-session flows were combined into a single
  screen/ViewModel/route (`RecoveryFutsalScreen`) rather than two
  destinations, as a deliberate cost-reduction measure
- `GetWorkoutDayContext` returns the latest recovery entry (any age) and
  any futsal session from the last 24h, as a literal read-only summary —
  it does **not** infer "warnings"; that's explicitly Milestone 6 scope
- The workout-day-context banner is exposed as its own `StateFlow` on
  `ActiveWorkoutViewModel` (not folded into the reactive `uiState`
  combine), to avoid a one-shot async update racing with the session
  observation stream

## Current blockers

None. Milestone 5 is complete, verified and committed.

## Known pre-existing flaky test (not caused by Milestone 5)

`ActiveWorkoutViewModelTest.adding an exercise then recording, editing
and undoing a set updates the active session` intermittently fails when
run in isolation or occasionally in the full suite (turbine/
`UnconfinedTestDispatcher` timing race, unrelated to CP6's changes).
Confirmed present, identically, on commit `52efdae` (Milestone 4
completion, before any Milestone 5 work) — this predates this milestone
and is not a regression from it. Recommended follow-up: investigate
`ActiveWorkoutViewModelTest`'s turbine/dispatcher setup for a real fix
(out of scope for Milestone 5; flagged for a future maintenance pass or
the post-MVP review).

## Active plan

Milestone 5 plan docs have been archived:
`docs/milestones/completed/milestone-5-execution.md`,
`docs/milestones/completed/milestone-5-reference.md`.

## Last verified state (Milestone 5, full CP1–CP7)

- Unit tests: `./gradlew testDebugUnitTest` (full suite) — 249 tests,
  passes cleanly in most runs; one known pre-existing flaky test (see
  above)
- Static checks: `./gradlew spotlessCheck detekt lintDebug` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (full suite)
  on `emulator-5554` — 0 failures/errors
- Manual verification: installed latest debug APK, launched app, ran
  `adb shell monkey` fuzz test (300 events) with no crashes in logcat
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/5.json` generated and tracked

## Known deferred scope (documented, not a defect)

- No inference of recovery "warnings"/recommendations from recorded
  values — deferred to Milestone 6 (progression recommendations)
- Recovery/futsal entry combined into a single screen rather than two
  separate destinations (cost-reduction decision, revisit if UX flows
  demand separation)
