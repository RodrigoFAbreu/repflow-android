# Milestone 3 Reference — Active Workout

## Goals

- Start a workout from a training plan version (or ad hoc, with no plan).
- Resume an in-progress (active) workout after app restart or process death.
- Record sets quickly against each workout exercise (fast set entry).
- Persist important state immediately (no explicit "save" step).
- Support editing/undoing a just-recorded set within the active session.
- Complete or abandon a session, after which its data is historically frozen.

## Non-goals (deferred to later milestones)

- Rest timer UI/behavior (Milestone 4) — sets may still record a rest
  duration value, but no countdown/notification logic is added here.
- Recovery/futsal load context (Milestone 5).
- Progression recommendations (Milestone 6).
- History browsing UI, exports, backup (Milestone 7).

## Domain invariants

- A `WorkoutSession` is `Active`, `Completed`, or `Abandoned`. Only an
  `Active` session may be mutated (add/edit/remove exercises or sets).
- A `WorkoutSession` references the `TrainingPlanVersionId` it was started
  from (nullable — a session may be started without a plan). It never
  references the mutable `TrainingPlanId`'s "current" version; the version
  reference is fixed at session start and never changes even if the plan is
  later revised. This preserves historical meaning per ADR/domain glossary.
- A `WorkoutExercise` copies enough exercise identity/config (name, tracking
  type) at creation time so history remains meaningful if the source
  `Exercise` or plan changes later. It still keeps a reference to the
  `ExerciseId` for cross-session aggregation, but display/history rendering
  must not depend on the exercise still existing/unchanged.
- A `WorkoutSet` records values consistent with its `WorkoutExercise`'s
  tracking type (weight+reps, reps only, or duration), mirroring
  `ExerciseTrackingType`.
- Completing or abandoning a session is terminal: no further mutation of
  that session's exercises/sets is allowed afterward.
- Only one `Active` session may exist at a time (single in-progress
  workout), matching the "resume" UX — starting a new workout while one is
  active requires explicitly finishing/abandoning the existing one first.

## Architecture

- Domain: `domain/workout/` — `WorkoutSession`, `WorkoutSessionId`,
  `WorkoutSessionStatus`, `WorkoutExercise`, `WorkoutExerciseId`,
  `WorkoutSet`, `WorkoutSetId`, value objects for recorded values, and a
  `WorkoutValidationError` sealed type — pure Kotlin, no Android/Room deps,
  mirroring the `trainingplan` package's structure and validation style.
- Application: use cases for starting a session (from a plan version or ad
  hoc), recording/editing/removing a set, adding an ad hoc exercise,
  completing/abandoning a session, and resuming the current active session,
  plus a repository port (`WorkoutRepository`) in `application` following
  the existing `TrainingPlanRepository` pattern.
- Data/Infrastructure: Room entities/DAOs for session, workout-exercise, and
  set tables; mappers; repository implementation. Requires a schema
  migration (v2 → v3), additive only, plus a `MigrationTestHelper` test.
- Presentation: a "current workout" screen (resume-or-start), fast set
  entry rows per exercise, and edit/undo for the most recent set. Reuses
  existing navigation/ViewModel conventions from the training-plan editor.

## Data model / migration requirements

New tables (exact column sets to be finalized during CP3, following the
`training_plan_versions`/`planned_exercises` precedent):

- `workout_sessions(id, training_plan_version_id NULL, status, started_at,
  ended_at NULL)`
- `workout_exercises(id, session_id, exercise_id, order_index,
  exercise_name_snapshot, tracking_type_snapshot, planned_exercise_id NULL)`
- `workout_sets(id, workout_exercise_id, order_index, load NULL,
  reps NULL, duration_seconds NULL, rpe NULL, is_warmup, created_at,
  updated_at)`

Migration: Room schema version 2 → 3, additive `CREATE TABLE` only, no
destructive fallback, exported schema JSON committed, migration test added
alongside the existing v1→v2 test.

## Presentation / UX behaviour

- Entry point: a persistent way to resume an active session if one exists,
  otherwise start a new one from a plan or ad hoc (exact navigation entry
  TBD at CP6 based on `docs/UX_FLOWS.md`).
- Fast set entry: minimal-tap input per set (numeric fields sized for
  tracking type), immediate persistence per set (no batch save).
- Edit/undo: the most recently recorded set for an exercise can be edited
  or removed while the session is still active.

## Test strategy

- Domain: JVM unit tests for session/exercise/set construction, status
  transitions, and validation errors (mirrors `trainingplan` domain tests).
- Application: unit tests per use case (fake repository), covering start,
  resume, record/edit/remove set, add ad hoc exercise, complete, abandon,
  and the "only one active session" invariant.
- Data: DAO tests + migration test on-device/emulator (instrumented).
- Presentation: minimal Compose smoke tests for the current-workout screen
  and set-entry row, matching Milestone 2's UI test depth.

## Checkpoint definitions

- CP1 — Domain model + pure JVM tests.
- CP2 — Application contracts + use cases + tests.
- CP3 — Room entities, DAOs, mapper, migration 2→3, repository.
- CP4 — DAO + repository + migration instrumented tests on device.
- CP5 — Hilt bindings.
- CP6 — Current-workout screen: start/resume end to end.
- CP7 — Fast set entry + edit/undo end to end.
- CP8 — Docs + final architectural review.

## Definition of Done

- All CP1–CP8 checkpoints implemented.
- Unit tests pass (`./gradlew testDebugUnitTest`).
- Static checks pass (`spotlessCheck`, `detekt`, `lintDebug`).
- Instrumented tests pass on a real device/emulator
  (`./gradlew connectedDebugAndroidTest`), including the new migration test.
- Manual smoke test: start a workout from a plan, record sets for each
  exercise, edit/undo the last set, complete the session, restart the app
  process, confirm no active session remains and history is intact
  (deep history browsing itself is out of scope until Milestone 7).
- `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` updated; plan docs
  archived under `docs/milestones/completed/`.

## Unresolved decisions / explicit stopping conditions

- Exact table/column names are finalized during CP3 implementation, not
  fixed in advance, to match Room/mapper conventions discovered while
  writing the entities.
- Exact navigation entry point (tab vs. banner vs. FAB) is finalized during
  CP6 by consulting `docs/UX_FLOWS.md` if ambiguous; if `UX_FLOWS.md` does
  not give a clear answer, stop and ask before choosing UI structure.
- If "only one active session" conflicts with any documented product
  requirement discovered later, stop and ask rather than silently allowing
  concurrent active sessions.
