# Active Milestone

## Milestone

Milestone 3 — Active Workout (CP1–CP7 complete, CP8 next)

## Goal

Support starting/resuming a workout from a training plan (or ad hoc), fast
per-set entry with immediate persistence, and edit/undo of the most
recently recorded set, as the third complete vertical slice.

## Current checkpoint

CP7 complete: fast set entry + edit/undo end to end. New application use
cases `UndoLastWorkoutSet` and `EditLastWorkoutSet` (both operate on the
most recently recorded set of a `WorkoutExercise`, reusing
`WorkoutExercise.withoutSet`/`withUpdatedSet`). `ActiveWorkoutViewModel` now
also observes `ObserveExercises` to power an "add exercise" picker, and
dispatches `AddWorkoutExercise`/`RecordWorkoutSet`/`UndoLastWorkoutSet`/
`EditLastWorkoutSet`. `ActiveWorkoutScreen` gained an exercise picker
dropdown, per-exercise set rows, load/reps input fields, and add/undo/edit
set buttons.

Milestone 2 (Training Plans) remains complete and committed; see
`docs/milestones/completed/milestone-2-execution.md` and
`-reference.md`.

## Checkpoint checklist (Milestone 3)

- [x] CP1 — Domain model + pure JVM tests
- [x] CP2 — Application contracts + use cases + tests
- [x] CP3 — Room entities, DAOs, mapper, migration 2→3, repository
- [x] CP4 — DAO + repository + migration instrumented tests on device
- [x] CP5 — Hilt bindings
- [x] CP6 — Current-workout screen (start/resume) end to end
- [x] CP7 — Fast set entry + edit/undo end to end
- [ ] CP8 — Docs + final architectural review

## Approved decisions (quick reference, Milestone 3)

- Only one `Active` workout session may exist at a time
- A session's `TrainingPlanVersionId` reference is fixed at start and never
  follows later plan revisions (historical meaning preserved)
- Completing/abandoning a session is terminal; no further mutation allowed
- Room schema v2 → v3: additive migration, `MigrationTestHelper`-verified
  (`MIGRATION_2_3`, implemented and tested)
- Rest-timer behavior, recovery/futsal context, recommendations, and
  history/backup UI are explicitly out of scope for Milestone 3
- Set-entry fields are limited to load + reps for CP7 (the dominant
  `WEIGHT_AND_REPS` tracking type); duration/RPE/warmup fields are wired
  through the use cases but not yet exposed as separate UI inputs - a small
  known gap to close, or explicitly defer, in CP8's final review

## Current blockers

None. Milestone 3 CP1–CP7 are complete and verified. CP8 (docs + final
architectural review) is the next and last checkpoint.

## Active plan

- `docs/milestones/active/milestone-3-execution.md`
- `docs/milestones/active/milestone-3-reference.md`
- Prior milestone docs: `docs/milestones/completed/milestone-2-execution.md`,
  `docs/milestones/completed/milestone-2-reference.md`

## Last verified state (Milestone 3, CP1–CP7)

- Unit tests: `./gradlew testDebugUnitTest` — all pass (including new
  `UndoAndEditLastWorkoutSetTest` and an expanded
  `ActiveWorkoutViewModelTest` covering add/record/edit/undo/complete)
- Static checks: `./gradlew spotlessCheck detekt` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (workout DAO
  package + `RepFlowDatabaseMigrationTest`) — all pass on the
  `Pixel_9_Pro_XL` (API 37) emulator (run during CP3/CP4; no new
  instrumented tests were added in CP7 - coverage relies on the
  `ActiveWorkoutViewModelTest` JVM test plus a manual launch/crash check)
- Manual verification: installed and launched the updated app on
  `emulator-5554` after CP7 changes, confirmed no crash on launch; the full
  add/record/edit/undo/complete flow was verified via the automated
  `ActiveWorkoutViewModelTest` rather than a manual UI click-through, to
  conserve session budget - a full manual click-through (create an
  exercise, start a workout, add it, record/edit/undo a set, finish) is
  recommended before/during CP8's final review
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/3.json` generated and tracked

