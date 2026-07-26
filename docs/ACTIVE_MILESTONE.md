# Active Milestone

## Milestone

Milestone 3 — Active Workout (CP1–CP6 complete, CP7 next)

## Goal

Support starting/resuming a workout from a training plan (or ad hoc), fast
per-set entry with immediate persistence, and edit/undo of the most
recently recorded set, as the third complete vertical slice.

## Current checkpoint

CP6 complete: current-workout screen end to end. New
`presentation/workout` package: `ActiveWorkoutUiState`/`ActiveWorkoutContent`,
`ActiveWorkoutViewModel` (observes `ObserveActiveWorkoutSession`, dispatches
`StartWorkoutSession`/`CompleteWorkoutSession`/`AbandonWorkoutSession`),
`ActiveWorkoutScreen` (stateless Compose UI) and `ActiveWorkoutRoute`. Wired
into `RepFlowNavHost` via a new `WORKOUT` destination, reachable from a
"Workout" button on the exercise list top bar.

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
- [ ] CP7 — Fast set entry + edit/undo end to end
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

## Current blockers

None. Milestone 3 CP1–CP6 are complete and verified. CP7 (fast set entry +
edit/undo) is the next unstarted checkpoint.

## Active plan

- `docs/milestones/active/milestone-3-execution.md`
- `docs/milestones/active/milestone-3-reference.md`
- Prior milestone docs: `docs/milestones/completed/milestone-2-execution.md`,
  `docs/milestones/completed/milestone-2-reference.md`

## Last verified state (Milestone 3, CP1–CP6)

- Unit tests: `./gradlew testDebugUnitTest` — all pass (including new
  domain/application/mapper/ViewModel workout tests)
- Static checks: `./gradlew spotlessCheck detekt` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (filtered to the
  workout DAO package and `RepFlowDatabaseMigrationTest`) — all pass, run on
  the `Pixel_9_Pro_XL` (API 37) emulator
- Manual smoke test on emulator: tapped the "Workout" nav button, started a
  workout from the empty state, confirmed the active-session screen showed
  "0 exercises / 0 sets", force-stopped and relaunched the app and confirmed
  the active session was still shown (full process-death persistence),
  then tapped "Finish workout" and confirmed the screen returned to the
  empty "no active workout" state
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/3.json` generated and tracked
