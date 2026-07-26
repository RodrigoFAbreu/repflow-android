# Milestone 4 Reference — Rest Timer

## Goals

- After a working set is recorded, start a rest timer with a configured
  duration.
- Store an **absolute end timestamp** (not a countdown duration) so the
  timer survives backgrounding and full process death, per
  `docs/UX_FLOWS.md` and `docs/TECHNICAL_DECISIONS.md`.
- Allow adding/removing time and skipping the timer.
- Notify the user (vibration + system notification) when rest ends,
  where OS permission allows.

## Non-goals

- No per-exercise/per-set configurable default rest duration UI (a single
  session-level default is enough for this milestone; a fixed constant is
  used, matching "avoid speculative abstractions").
- No background *service*; scheduling uses `AlarmManager` + a
  `BroadcastReceiver`, which is standard platform API, not a new
  dependency category.
- No changes to recovery, futsal, recommendations, or history/backup.

## Domain invariants

- A rest timer belongs to the single `ACTIVE` `WorkoutSession` (at most
  one active session exists at a time, per Milestone 3).
- `RestTimer` stores `endAt: Instant` and `totalDurationSeconds: Int`.
  Remaining time is *always* derived as `endAt - now`; it is never stored
  as a mutable countdown value.
- Adding/removing time shifts `endAt` directly; it never resets
  `totalDurationSeconds` retroactively (that field is informational, for
  UI progress display only).
- A rest timer can only exist while the session is `ACTIVE`; completing or
  abandoning a session implicitly clears it (no dangling timer state on a
  terminal session).
- Skipping clears the timer (`restTimer = null`); this is not a terminal
  action on the session itself.

## Architecture decisions

- `RestTimer` is a new pure-Kotlin domain value class next to
  `WorkoutSession`, `WorkoutExercise`, etc.
- `WorkoutSession` gains a nullable `restTimer: RestTimer?` field and three
  new methods: `withStartedRestTimer`, `withAdjustedRestTimer`,
  `withClearedRestTimer` — mirroring the existing
  `withAddedExercise`/`withUpdatedExercise` style.
- Three new application use cases (`StartRestTimer`, `AdjustRestTimer`,
  `SkipRestTimer`) reuse the existing `WorkoutRepository.update` method;
  no new repository methods are needed.
- Presentation owns the actual countdown tick (a `Flow` ticking once per
  second while a timer is active) and the `AlarmManager` scheduling for
  the "notify when expired even if the app is backgrounded" requirement.
  The domain/application layers only track the absolute end timestamp;
  they know nothing about `AlarmManager` or notifications (dependency
  direction preserved).

## Data model / migration

- Room schema 3 → 4: add two **nullable** columns to the existing
  `workout_sessions` table: `rest_timer_end_at_epoch_ms INTEGER` and
  `rest_timer_total_duration_seconds INTEGER`. Purely additive; no
  destructive migration; a `MIGRATION_3_4` is added and instrumented-tested
  the same way as `MIGRATION_2_3`.
- `WorkoutSessionEntity` gains the two matching nullable fields; the
  mapper round-trips `RestTimer?` from/to them.

## Presentation / UX behaviour

- After tapping "Add set" (recording a set) on `ActiveWorkoutScreen`, a
  rest timer section appears showing remaining `mm:ss`, and
  add-time/remove-time (+15s/-15s) and "Skip" buttons.
- The timer is derived from the persisted absolute `endAt` on every
  recomposition/tick — reopening the app (even after process death)
  reconstructs the correct remaining time with no drift.
- A default rest duration constant is used when starting a timer (no
  per-exercise configuration UI in this milestone).
- On Android 13+ (`POST_NOTIFICATIONS` runtime permission), the app
  requests the permission once, lazily, the first time a rest timer is
  started. If the user denies it, the timer still works fully in-app;
  only the OS notification/vibration on expiry is skipped. This resolves
  the previously "Open" decision in `TECHNICAL_DECISIONS.md`
  ("Notification behavior when permission is denied") for this milestone:
  **silently degrade to in-app-only timer; never block or nag the user.**

## Test strategy

- Domain: `RestTimer` unit tests (remaining-time math, add/remove
  clamping, expiry) + `WorkoutSession` tests for the three new methods
  (JVM, pure Kotlin).
- Application: unit tests for `StartRestTimer`/`AdjustRestTimer`/
  `SkipRestTimer` against `InMemoryWorkoutRepository`.
- Persistence: mapper round-trip unit test; `MIGRATION_3_4` instrumented
  test (mirroring `MIGRATION_2_3`); DAO instrumented test extended to
  cover the new columns.
- Presentation: `ActiveWorkoutViewModel` test covering starting a timer
  after recording a set, adjusting it, and skipping it (using a fake/
  fixed clock, not real `AlarmManager`/notifications, which are
  integration-only and verified via manual smoke test).

## Checkpoint definitions

1. CP1 — Domain: `RestTimer` + `WorkoutSession` integration + tests.
2. CP2 — Application: three use cases + tests.
3. CP3 — Persistence: migration 3→4, entity/mapper, DAO changes,
   instrumented tests.
4. CP4 — Hilt bindings (if any new use cases need explicit provision;
   likely automatic via `@Inject constructor`).
5. CP5 — Presentation: countdown UI + add/remove/skip actions wired into
   `ActiveWorkoutScreen`/`ActiveWorkoutViewModel`.
6. CP6 — Notifications: `AlarmManager` scheduling + `BroadcastReceiver` +
   notification channel + runtime permission request, degrading
   gracefully when denied.
7. CP7 — Docs + final review, full verification suite, milestone
   completion commit.

## Definition of Done

- All CP1–CP7 checkpoints implemented and their narrow verification
  commands actually run and passed.
- Full milestone verification (`testDebugUnitTest`, `spotlessCheck`,
  `detekt`, `lintDebug`, `connectedDebugAndroidTest`) passes.
- Manual smoke test on a real emulator: record a set, see the rest timer
  start, add/remove time, let it (or skip it to) expire, confirm no
  crash; confirm the timer survives a full app process restart mid-countdown.
- `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` updated; milestone
  plan docs archived under `docs/milestones/completed/`.

## Unresolved decisions / explicit stopping conditions

- Exact default rest duration value is not specified anywhere in the
  docs. **Assumption made**: 90 seconds, a common strength-training
  default, used only as a placeholder constant — not a hard product
  decision. Flagged here for human confirmation/adjustment later; not a
  blocker for this milestone since it is trivially changeable.
- If real on-device notification delivery cannot be verified (e.g. no
  emulator notification shade access), this is documented honestly rather
  than claimed as tested.
