# Active Milestone

## Milestone

Milestone 3 — Active Workout (planning complete, implementation not started)

## Goal

Support starting/resuming a workout from a training plan (or ad hoc), fast
per-set entry with immediate persistence, and edit/undo of the most
recently recorded set, as the third complete vertical slice.

## Current checkpoint

CP2 complete: application layer for active workouts
(`application/workout/WorkoutRepository` port, `WorkoutOperationError`,
`WorkoutPersistenceError`, and use cases `StartWorkoutSession`,
`ObserveActiveWorkoutSession`, `AddWorkoutExercise`, `RecordWorkoutSet`,
`CompleteWorkoutSession`, `AbandonWorkoutSession`), with unit tests using an
`InMemoryWorkoutRepository` fake, mirroring the training-plan application
layer's conventions. CP3 (Room persistence) not started yet.

Milestone 2 (Training Plans) remains complete and committed; see
`docs/milestones/completed/milestone-2-execution.md` and
`-reference.md`.

## Checkpoint checklist (Milestone 3)

- [x] CP1 — Domain model + pure JVM tests
- [x] CP2 — Application contracts + use cases + tests
- [ ] CP3 — Room entities, DAOs, mapper, migration 2→3, repository
- [ ] CP4 — DAO + repository + migration instrumented tests on device
- [ ] CP5 — Hilt bindings
- [ ] CP6 — Current-workout screen (start/resume) end to end
- [ ] CP7 — Fast set entry + edit/undo end to end
- [ ] CP8 — Docs + final architectural review

## Approved decisions (quick reference, Milestone 3)

- Only one `Active` workout session may exist at a time
- A session's `TrainingPlanVersionId` reference is fixed at start and never
  follows later plan revisions (historical meaning preserved)
- Completing/abandoning a session is terminal; no further mutation allowed
- Room schema v2 → v3: additive migration + `MigrationTestHelper` test
  planned (not yet implemented)
- Rest-timer behavior, recovery/futsal context, recommendations, and
  history/backup UI are explicitly out of scope for Milestone 3

## Current blockers

None. Milestone 3 CP1–CP2 (domain model + application layer) are complete
and verified. CP3 (Room persistence + migration 2→3) is the next unstarted
checkpoint.

## Active plan

- `docs/milestones/active/milestone-3-execution.md`
- `docs/milestones/active/milestone-3-reference.md`
- Prior milestone docs: `docs/milestones/completed/milestone-2-execution.md`,
  `docs/milestones/completed/milestone-2-reference.md`

## Last verified state (Milestone 2, still current — no Milestone 3 code exists yet)

- Unit tests: `./gradlew testDebugUnitTest` — 163 tests, 0 failures
- Static checks: `./gradlew spotlessCheck detekt lintDebug` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` — 90 tests, 0
  failures, run on `RepFlow_S24_Ultra_API_37` emulator
- Manual smoke test on emulator: created a plan with 2 exercises (Squat +
  Deadlift, mixed sets/rep-range targets), edited it (removed a row), saved
  a new version, confirmed the list reflects the new exercise count, and
  confirmed a separately created plan survives a full app-process restart
  (Room persistence)
- Two real defects found and fixed during manual verification (not caught
  by prior automated tests):
  1. `TargetRangeFields` used `Modifier.fillMaxWidth()` for both fields in a
     `Row` instead of `Modifier.weight(1f)`, collapsing the second field
     (max reps / duration max) to near-zero width and breaking the layout.
  2. Editing an existing plan showed "Select an exercise" instead of the
     actual exercise name, because `TrainingPlanEditorViewModel.toRow` never
     backfilled `exerciseName`/`trackingType` from the loaded exercise
     options; fixed via a `backfillExerciseDetails` step in `revalidate`.
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/2.json` tracked, no drift
