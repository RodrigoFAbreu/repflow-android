# Active Milestone

## Milestone

Milestone 2 — Training Plans (complete and committed)

## Goal

Deliver versioned training-plan creation and editing (ordered exercises with
targets), with historical version preservation, as the second complete
vertical slice.

## Current checkpoint

All checkpoints (CP1–CP8) complete. Milestone 2 is fully implemented,
verified, and ready for its completion commit.

## Checkpoint checklist

- [x] CP1 — Domain model + pure JVM tests
- [x] CP2 — Application contracts + use cases + tests
- [x] CP3 — Room entities, DAOs, mapper, migration 1→2, repository
- [x] CP4 — DAO + repository + migration instrumented tests on device
- [x] CP5 — Hilt bindings
- [x] CP6 — Training plan list end to end
- [x] CP7 — Training plan editor end to end (create + revise)
- [x] CP8 — Docs + final architectural review

## Approved decisions (quick reference)

- Plan "current version" is a query concept, not state stored on `TrainingPlan`
- Editing a plan always creates a new immutable version; never mutates one
- Plan archive/delete out of scope for M2
- Reordering via move-up/move-down buttons, no drag-and-drop
- Room schema v1 → v2: real, additive migration + `MigrationTestHelper` test
- Referenced exercises must exist and match the declared target kind

## Current blockers

None. Milestone 2 is done; Milestone 3 planning has not yet started (deferred
for the next session per credit-budget guidance).

## Active plan

- Completed docs: `docs/milestones/completed/milestone-2-execution.md`,
  `docs/milestones/completed/milestone-2-reference.md`

## Last verified state

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
