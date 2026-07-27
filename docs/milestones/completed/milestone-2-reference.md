# Milestone 2 — Training Plans: Reference

Full decision text for the execution guide. Read only when the execution
guide points here for a specific decision or invariant.

## Scope

In scope: versioned training-plan domain, plan creation, plan editing
(produces a new immutable version), ordered planned exercises with targets
(target sets, rep or duration range, rest duration, optional flag), list and
detail/editor UI, Room persistence with a real additive migration.

Out of scope (deferred, with reason):

| Deferred | Reason | Returns in |
|---|---|---|
| Alternatives / substitutions | Meaningless without active workouts (carried from M1 D-3) | M3 |
| Progression-policy configuration per planned exercise | No progression engine exists yet | M6 |
| Plan archive / delete | Not in the roadmap checklist for M2; nothing yet references a plan destructively | Later, alongside exercise deletion |
| Reordering via drag-and-drop | Up/down move buttons satisfy "ordered exercises" without a new Compose dependency | Never required unless UX feedback demands it |
| Viewing/browsing older plan versions in the UI | Persisted and never destroyed, but no history viewer needed until workouts reference versions | M3/M7 |

## Domain model (package `domain.trainingplan`)

- `TrainingPlanId`, `TrainingPlanVersionId`, `PlannedExerciseId` — `@JvmInline value class(String)`, blank-guarded, same shape as `ExerciseId`.
- `TrainingPlanName` — mirrors `ExerciseName` exactly: NFKC → trim → collapse
  whitespace → lower-case key. `MAX_LENGTH = 80`. Uniqueness (`name_key`)
  spans all plans (no archive concept in M2, so no "active and archived"
  nuance).
- `TargetSets` — `@JvmInline value class(Int)`, `1..20`.
- `RepRange(min: Int, max: Int)` — `1 <= min <= max <= 999`.
- `DurationTarget(minSeconds: Long, maxSeconds: Long)` — `1 <= min <= max <= 7_200`.
- `PlannedExerciseTarget` — `sealed interface` with `Reps(RepRange)` and
  `Duration(DurationTarget)` cases. A sealed type expresses "exactly one of
  reps or duration" without a nullable-pair XOR check.
- `PlannedExercise(id, exerciseId: ExerciseId, order: Int, targetSets: TargetSets, target: PlannedExerciseTarget, restDuration: RestDuration?, isOptional: Boolean)`.
  Reuses `domain.exercise.ExerciseId` and `domain.exercise.RestDuration`
  directly — cross-package reuse **within** the domain layer is fine; it is
  crossing into application/data/infrastructure that is not.
- `TrainingPlanVersion(id, planId: TrainingPlanId, versionNumber: Int, plannedExercises: List<PlannedExercise>, note: String?, createdAt: Instant)`.
  Factory validates: at least one planned exercise; `order` values are
  exactly `0 until size` with no gaps or repeats; `versionNumber >= 1`.
  **Immutable once created** — there is no "edit a version" operation, only
  "create a new version" (`ReviseTrainingPlan`).
- `TrainingPlan(id, name, createdAt, updatedAt)` — private constructor +
  `create`/`reconstruct`, same shape as `Exercise` minus archival. Does
  **not** hold a pointer to its current version; "current version" is a
  query concept (`max(version_number)` for a `plan_id`), not domain state,
  so the aggregate cannot be constructed inconsistently with its versions.
- `TrainingPlanValidationError` — flat sealed interface (same rationale as
  `ExerciseValidationError`): `NameBlank`, `NameTooLong`, `NoPlannedExercises`,
  `InvalidOrderSequence`, `TargetSetsOutOfRange`, `RepRangeInvalid`,
  `DurationRangeInvalid`, `UpdatedBeforeCreated`, `VersionNumberInvalid`.

## Application layer (package `application.trainingplan`)

- `TrainingPlanOverview(plan: TrainingPlan, latestVersion: TrainingPlanVersion)`
  — an application-level read model joining a plan with its current version.
  Not a Room type; still a plain composite of two domain aggregates, so it
  does not violate the "no Room type crosses this boundary" rule.
- `TrainingPlanRepository`:
  - `fun observeOverviews(): Flow<List<TrainingPlanOverview>>` — ordered by
    `name_key ASC, id ASC` (never `COLLATE NOCASE`, matching D-27's rationale).
  - `suspend fun findPlanIdByNameKey(nameKey: String): TrainingPlanId?`
  - `suspend fun findOverviewByPlanId(id: TrainingPlanId): TrainingPlanOverview?`
  - `suspend fun createPlanWithFirstVersion(plan: TrainingPlan, version: TrainingPlanVersion): DomainResult<Unit, TrainingPlanPersistenceError>`
  - `suspend fun addVersion(plan: TrainingPlan, version: TrainingPlanVersion): DomainResult<Unit, TrainingPlanPersistenceError>`
    — atomically (one `@Transaction`) updates the plan row (name/updatedAt)
    and inserts the new version + its planned exercises. Never touches an
    existing version row.
- `TrainingPlanPersistenceError`: `DuplicateName`, `Unavailable` (mirrors
  `ExercisePersistenceError`).
- `TrainingPlanOperationError`: `NotFound`, `DuplicateName`,
  `ValidationFailed(List<TrainingPlanValidationError>)`,
  `PlannedExerciseInvalid(List<PlannedExerciseValidationError>)`,
  `PersistenceUnavailable`.
- `PlannedExerciseValidationError`: `ExerciseNotFound(index: Int)`,
  `TargetKindMismatch(index: Int)` — raised when a `REPS`/`DURATION` target
  is supplied for an exercise whose `ExerciseTrackingType` does not support
  it (`WEIGHT_AND_REPS`/`REPS_ONLY` → `Reps`; `DURATION` → `Duration`).
- `PlannedExerciseInput` (raw, UI-shaped, one per row in the editor):
  `exerciseId: String, order: Int, targetSets: Int, targetKind: PlannedExerciseTargetKind, repMin: Int?, repMax: Int?, durationMinSeconds: Long?, durationMaxSeconds: Long?, restSeconds: Long?, isOptional: Boolean`.
- Use cases: `CreateTrainingPlan`, `ReviseTrainingPlan`, `ObserveTrainingPlans`,
  `GetTrainingPlanDetail`. Both `Create`/`Revise` inject `ExerciseRepository`
  to confirm every referenced exercise exists and matches its declared
  target kind — this is the one place application code legitimately depends
  on two repositories at once. Duplicate-name check on revise excludes the
  plan being edited (same pattern as `UpdateExercise`).

## Persistence (Room schema v1 → v2, additive only)

New tables, all under `infrastructure.database.trainingplan`:

- `training_plans(id TEXT PK, name TEXT, name_key TEXT, created_at INTEGER, updated_at INTEGER)`
  + unique index on `name_key`.
- `training_plan_versions(id TEXT PK, plan_id TEXT, version_number INTEGER, note TEXT NULL, created_at INTEGER)`
  + unique index on `(plan_id, version_number)`, plus a plain index on `plan_id`.
  `@ForeignKey(entity = TrainingPlanEntity::class, ... onDelete = CASCADE)` —
  no plan delete exists yet, but the constraint documents intent for when it
  does, matching how `exercises` already anticipates future needs.
- `planned_exercises(id TEXT PK, version_id TEXT, exercise_id TEXT, sort_order INTEGER, target_sets INTEGER, target_kind TEXT, rep_min INTEGER NULL, rep_max INTEGER NULL, duration_min_seconds INTEGER NULL, duration_max_seconds INTEGER NULL, rest_seconds INTEGER NULL, is_optional INTEGER)`
  + unique index on `(version_id, sort_order)`, plain indices on `version_id`
  and `exercise_id`. FK to `training_plan_versions.id` `ON DELETE CASCADE`;
  FK to `exercises.id` with default (`NO ACTION`) — exercises are never
  hard-deleted in the MVP (D-12), so no cascade behavior is needed there.

`RepFlowDatabase` bumps to `version = 2` with one real `Migration(1, 2)`
object executing the `CREATE TABLE` / `CREATE INDEX` statements above. No
`fallbackToDestructiveMigration` anywhere (unchanged project-wide rule). A
`RepFlowDatabaseMigrationTest` using `MigrationTestHelper` runs the 1→2
migration against a v1-schema database and validates the result against the
exported v2 schema — this is the first **real** migration test the project
has, unlike the v1 placeholder that M1 explicitly declined to write.

`ExerciseTrackingType` name reused as the `target_kind` string set is
**not** shared: `target_kind` only ever stores `"REPS"` or `"DURATION"`
(the two `PlannedExerciseTarget` cases), a different, smaller vocabulary
than `ExerciseTrackingType`'s three values — do not conflate them.

## Presentation

- New route `plans` (start destination stays `exercises` per D-1/D-2 — a
  Home screen is still out of scope). Bottom or top-level navigation between
  "Exercises" and "Plans" is out of scope; add a plain text/icon nav action
  from the exercise list screen's top bar to `plans`, matching the "no bottom
  navigation until Home exists" carried decision.
- `TrainingPlanListRoute`/`Screen`/`ViewModel`/`UiState` — mirrors the
  exercise list slice: observes `ObserveTrainingPlans`, shows plan name +
  latest version's exercise count, a create FAB, and a retry-capable loading
  state.
- `TrainingPlanEditorRoute`/`Screen`/`ViewModel`/`UiState` — mirrors the
  exercise editor slice for the plan name field plus a mutable, in-memory
  ordered list of planned-exercise rows built from `ObserveExercises` (active
  only) for exercise selection. Reordering uses move-up/move-down icon
  buttons on each row (no drag-and-drop dependency). Each row's target
  fields (reps vs. duration) are chosen from the selected exercise's
  `ExerciseTrackingType`, not typed freely, so `TargetKindMismatch` should be
  structurally unreachable from the UI and is a defense-in-depth check.
- No `WorkoutExercise`/`ActiveWorkout` UI exists yet (M3), so there is
  nothing to navigate to from a plan besides its own editor.

## Testing

- Domain: pure JVM tests for every new value object and factory, following
  the existing `ExerciseTest`/`RestDurationTest` style (boundary values,
  invariant violations, `reconstruct` failure cases).
- Application: use-case tests against an `InMemoryTrainingPlanRepository`
  (and the existing `InMemoryExerciseRepository`), following
  `CreateExerciseTest`/`UpdateExerciseTest` style — one test per branch,
  deterministic `FixedClock` + `SequentialIdentifierGenerator`.
- Data: `TrainingPlanEntityMapper` pure-JVM round-trip tests
  (`ExerciseEntityMapperTest` style) plus the new
  `RepFlowDatabaseMigrationTest`.
- Instrumented (device/emulator): DAO tests for the three new DAOs, a
  repository test for `LocalTrainingPlanRepository`, and Compose screen
  tests for the list and editor screens, mirroring the M1 instrumented
  suite. Actually run before the milestone is declared complete — a
  compiled-only `assembleDebugAndroidTest` is never reported as passing.

## Definition of Done

- A training plan can be created with an ordered, non-empty set of planned
  exercises and their targets, and listed.
- Editing a plan creates a new version; the previous version's row (and its
  planned exercises) is never mutated or deleted — verified by an
  instrumented test that revises a plan and then reads back version 1
  unchanged directly via the DAO.
- Duplicate normalized plan names are rejected (precheck + `UNIQUE`
  constraint), matching the exercise pattern.
- No Room type crosses into `application`, `domain`, or `presentation`.
- Room schema v1 → v2 migration is real, additive, non-destructive, and
  covered by a passing `MigrationTestHelper` test.
- `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` pass.
- `connectedDebugAndroidTest` actually runs on a device/emulator and passes.
- Feature manually smoke-tested: create a plan with 2+ exercises, edit it
  (adding/removing/reordering a row), confirm the edit is visible and the
  app does not crash across a restart.
