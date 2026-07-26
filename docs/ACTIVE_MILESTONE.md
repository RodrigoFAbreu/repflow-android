# Active Milestone

## Milestone

Milestone 3 — Active Workout — **COMPLETE**

## Goal

Support starting/resuming a workout from a training plan (or ad hoc), fast
per-set entry with immediate persistence, and edit/undo of the most
recently recorded set, as the third complete vertical slice.

## Current checkpoint

CP8 complete: Milestone 3 is fully implemented, verified and documented.
Full verification suite passed (`testDebugUnitTest`, `spotlessCheck`,
`detekt`, `lintDebug`, `connectedDebugAndroidTest` — full instrumented
suite, 14 test classes / 96 total test methods, 0 failures/errors). A
manual smoke test on `emulator-5554` confirmed: Workout tab navigation,
start workout → active-session UI (Add exercise / Finish workout /
Abandon workout controls render correctly), abandon workout → clean
"No active workout" state, no crashes in logcat throughout. Room schema
`app/schemas/.../3.json` is exported and tracked.

Milestone 2 (Training Plans) remains complete and committed; see
`docs/milestones/completed/milestone-2-execution.md` and `-reference.md`.

## Checkpoint checklist (Milestone 3)

- [x] CP1 — Domain model + pure JVM tests
- [x] CP2 — Application contracts + use cases + tests
- [x] CP3 — Room entities, DAOs, mapper, migration 2→3, repository
- [x] CP4 — DAO + repository + migration instrumented tests on device
- [x] CP5 — Hilt bindings
- [x] CP6 — Current-workout screen (start/resume) end to end
- [x] CP7 — Fast set entry + edit/undo end to end
- [x] CP8 — Docs + final architectural review

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

None. Milestone 3 is complete, verified and committed.

## Active plan

Milestone 3 plan docs have been archived:
`docs/milestones/completed/milestone-3-execution.md`,
`docs/milestones/completed/milestone-3-reference.md`.

## Last verified state (Milestone 3, full CP1–CP8)

- Unit tests: `./gradlew testDebugUnitTest` (full suite) — all pass
- Static checks: `./gradlew spotlessCheck detekt lintDebug` — all pass
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (full suite,
  not filtered) on the `Pixel_9_Pro_XL` (API 37) emulator — 96 total test
  methods across 14 classes, 0 failures/errors
- Manual verification: installed the latest debug APK on `emulator-5554`;
  navigated Exercises → Workout, started a workout (active-session UI
  rendered with Add exercise / Finish workout / Abandon workout controls),
  abandoned it, confirmed return to a clean "No active workout" state; no
  FATAL/AndroidRuntime crashes in logcat throughout. A full
  add/record/edit/undo/complete click-through (with an exercise present)
  was not additionally re-verified manually beyond CP6/CP7's checks;
  confidence for that flow rests on the automated
  `ActiveWorkoutViewModelTest`, which exercises the real ViewModel and real
  application use cases against in-memory fakes
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/3.json` generated and tracked

## Known deferred scope (documented, not a defect)

- Set-entry UI (CP7/CP8) only exposes load + reps input fields (the
  dominant `WEIGHT_AND_REPS` tracking type). Duration/RPE/warmup fields are
  wired through the application use cases (`RecordWorkoutSet`,
  `EditLastWorkoutSet`) as nullable parameters but are not yet surfaced as
  separate UI inputs for `DURATION`-tracked exercises. Deferred to a future
  milestone/checkpoint rather than expanding Milestone 3's scope
  unilaterally — no existing documentation approves duration-specific UI
  for this milestone.
