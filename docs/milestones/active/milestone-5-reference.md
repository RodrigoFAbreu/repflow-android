# Milestone 5 Reference — Recovery and Futsal

## Goals

- Let the user record a quick recovery entry (sleep quality, energy, leg
  DOMS, heel stiffness, pain while walking, heavy legs, futsal in the
  previous/next 24h, optional notes) for a given date.
- Let the user record a futsal session (duration + session RPE) and derive
  its training load (`duration_minutes * session_rpe`), per
  `docs/DOMAIN_GLOSSARY.md`.
- Surface the most relevant recovery/futsal context on the active-workout
  screen (a "workout-day context" banner), per `docs/UX_FLOWS.md`'s
  "Recovery entry" and "Complete workout" sections ("Recovery or pain
  warnings").

## Non-goals

- No progression-recommendation engine yet (Milestone 6); this milestone
  only *records* recovery/futsal data and *surfaces* it as read-only
  context - it does not yet feed any recommendation logic.
- No historical recovery/futsal trend charts (Milestone 7, history).
- No editing of past recovery/futsal entries beyond the most recent one
  (mirrors the "edit/undo of the most recent" precedent from Milestone 3;
  full history editing is out of scope here).

## Domain invariants

- `RecoveryEntry` and `FutsalSession` are independent, date-scoped
  aggregates - **not** tied to a `WorkoutSession` (a user may log recovery
  or futsal on a rest day). Pure Kotlin, immutable, validated at
  construction (mirrors `Exercise`'s and `WorkoutSet`'s shape).
- Recovery scale fields (`sleepQuality`, `energy`, `legDoms`,
  `heelStiffness`, `painWhileWalking`, `heavyLegs`) are each an `Int` in
  `0..4` (a 5-point scale), per "quick... scales or selectable options" in
  `docs/UX_FLOWS.md`. Out-of-range values are rejected.
- `futsalInPrevious24h` / `futsalExpectedNext24h` are plain booleans.
- `FutsalSession.durationMinutes` must be positive; `sessionRpe` is a
  `Double` in `0.0..10.0` (matches `WorkoutSet.rpe`'s existing range).
  `load` is *always derived* (`durationMinutes * sessionRpe`), never
  stored as an independent mutable field, mirroring the "never store a
  derived value as an independent mutable field" style already used by
  `RestTimer.remainingSeconds`.
- At most one `RecoveryEntry` and one `FutsalSession` may exist per
  calendar date (re-recording the same date replaces/updates it, rather
  than accumulating duplicates) - this keeps "workout-day context" lookup
  simple (single row per date).

## Architecture decisions

- New top-level domain package `domain/recovery/` (siblings to
  `domain/workout/`, `domain/exercise/`), keeping with the existing
  per-feature domain package convention.
- `RecoveryRepository` and `FutsalRepository` application ports, each with
  `observeForDate`/`findForDate`/`upsert`, mirroring
  `WorkoutRepository`'s shape closely enough to stay consistent, but
  simpler (single-row-per-date, no aggregate-of-aggregates).
- A single new application use case,
  `ObserveWorkoutDayContext`, combines the latest `RecoveryEntry` (most
  recent by date) and any `FutsalSession` within the last 24 hours into a
  small `WorkoutDayContext` read model for the active-workout screen -
  this is the only cross-aggregate composition in this milestone.
- Presentation: two new simple entry screens (recovery, futsal) reachable
  from a new top-level navigation destination, plus a small context
  banner added to `ActiveWorkoutScreen`.

## Data model / migration

- Room schema 4 → 5: two new additive tables, `recovery_entries` and
  `futsal_sessions`, each keyed by an ISO date string (`entry_date TEXT
  NOT NULL UNIQUE`) rather than a random UUID, since at most one row may
  exist per date. No changes to any existing table. `MIGRATION_4_5` is
  added and instrumented-tested the same way as `MIGRATION_3_4`.

## Presentation / UX behaviour

- A "Recovery" screen: quick-entry sliders/segmented buttons for the six
  0-4 scale fields, two toggle switches for the futsal-in-window fields,
  and an optional free-text notes field, for "today" by default.
- A "Futsal" screen: duration (minutes) and session RPE (0-10) inputs,
  showing the computed load live.
- `ActiveWorkoutScreen` gains a small, optional context banner (shown only
  when a recent recovery/futsal entry exists) summarizing recovery
  concerns (e.g. "Heavy legs recorded" or "Futsal in the last 24h") -
  read-only, no interaction.

## Test strategy

- Domain: `RecoveryEntry`/`FutsalSession` construction/validation unit
  tests (JVM, pure Kotlin).
- Application: `RecoveryRepository`/`FutsalRepository`-backed use case
  tests against in-memory fakes, plus `ObserveWorkoutDayContext` tests
  covering "no data", "recovery only", "futsal only", "both", and
  "futsal older than 24h is excluded".
- Persistence: mapper round-trip tests; `MIGRATION_4_5` instrumented test;
  DAO instrumented tests for both new tables.
- Presentation: ViewModel tests for both entry screens (save then reload)
  and for the context banner appearing/disappearing correctly.

## Checkpoint definitions

1. CP1 — Domain: `RecoveryEntry`, `FutsalSession` + tests.
2. CP2 — Application: repositories' ports + record/observe use cases +
   `ObserveWorkoutDayContext` + tests.
3. CP3 — Persistence: migration 4→5, entities/mapper, DAOs, Hilt bindings.
4. CP4 — Instrumented tests (migration + DAOs) on a real device/emulator.
5. CP5 — Presentation: recovery entry screen + futsal entry screen +
   navigation wiring.
6. CP6 — Presentation: workout-day context banner on
   `ActiveWorkoutScreen`.
7. CP7 — Docs + final review, full verification suite, milestone
   completion commit.

## Definition of Done

- All CP1–CP7 checkpoints implemented and their narrow verification
  commands actually run and passed.
- Full milestone verification (`testDebugUnitTest`, `spotlessCheck`,
  `detekt`, `lintDebug`, `connectedDebugAndroidTest`) passes.
- Manual smoke test on a real emulator: record a recovery entry, record a
  futsal session, start a workout and see the context banner reflect
  them; confirm no crash.
- `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` updated; plan docs
  archived under `docs/milestones/completed/`.

## Unresolved decisions / explicit stopping conditions

- The exact 0-4 scale labels (e.g. "Poor"..."Great") are not specified in
  any doc; placeholder numeric-only UI is used for this milestone rather
  than inventing specific wording, which is a low-risk, easily
  revisited detail rather than a blocker.
- "Workout-day context" banner content/thresholds (e.g. what exactly
  counts as a "recovery warning") are not specified; a conservative,
  clearly-labeled summary of raw recorded values is shown rather than any
  inferred "warning" logic, since inferring warnings would overlap with
  Milestone 6's recommendation engine scope.
