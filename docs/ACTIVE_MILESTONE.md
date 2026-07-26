# Active Milestone

## Milestone

Milestone 3 — Active Workout (CP1–CP4 complete, CP5 in progress)

## Goal

Support starting/resuming a workout from a training plan (or ad hoc), fast
per-set entry with immediate persistence, and edit/undo of the most
recently recorded set, as the third complete vertical slice.

## Current checkpoint

CP3+CP4 complete: Room persistence for active workouts. New entities
`WorkoutSessionEntity`/`WorkoutExerciseEntity`/`WorkoutSetEntity`, DAOs
(`WorkoutSessionDao`, `WorkoutExerciseDao`, `WorkoutSetDao`), additive
`MIGRATION_2_3` (schema v2→v3, exported and tracked as
`app/schemas/.../3.json`), a pure-Kotlin `WorkoutEntityMapper` (with a
round-trip unit test), and `LocalWorkoutRepository` implementing
`WorkoutRepository` (delete-then-reinsert on `update`, narrow SQLite
exception translation mirroring the training-plan repository). Hilt bindings
(`DatabaseModule`, `RepositoryModule`) were wired alongside so the DB layer
compiles and can be exercised end to end. Verified with JVM unit tests
(mapper round-trip) and real-device instrumented tests: `WorkoutDaoTest`
(session/exercise/set CRUD + cascade delete) and two new
`RepFlowDatabaseMigrationTest` cases for `MIGRATION_2_3`.

Milestone 2 (Training Plans) remains complete and committed; see
`docs/milestones/completed/milestone-2-execution.md` and
`-reference.md`.

## Checkpoint checklist (Milestone 3)

- [x] CP1 — Domain model + pure JVM tests
- [x] CP2 — Application contracts + use cases + tests
- [x] CP3 — Room entities, DAOs, mapper, migration 2→3, repository
- [x] CP4 — DAO + repository + migration instrumented tests on device
- [x] CP5 — Hilt bindings
- [ ] CP6 — Current-workout screen (start/resume) end to end
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

None. Milestone 3 CP1–CP5 are complete and verified. CP6 (current-workout
screen) is the next unstarted checkpoint.

## Active plan

- `docs/milestones/active/milestone-3-execution.md`
- `docs/milestones/active/milestone-3-reference.md`
- Prior milestone docs: `docs/milestones/completed/milestone-2-execution.md`,
  `docs/milestones/completed/milestone-2-reference.md`

## Last verified state (Milestone 3, CP1–CP5)

- Unit tests: `./gradlew testDebugUnitTest` — all pass (including new
  domain/application/mapper workout tests)
- Static checks: `./gradlew spotlessCheck detekt` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (filtered to the
  workout DAO package and `RepFlowDatabaseMigrationTest`) — all pass, run on
  the `Pixel_9_Pro_XL` (API 37) emulator; migration test XML confirms
  `tests="4" failures="0" errors="0"` for `RepFlowDatabaseMigrationTest`
  (2 pre-existing 1→2 cases + 2 new 2→3 cases)
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/3.json` generated and tracked
