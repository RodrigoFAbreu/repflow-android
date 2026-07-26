# Active Milestone

## Milestone

Milestone 6 — Progression recommendations — **COMPLETE**

## Goal

After a workout is completed, compute a deterministic, local, explainable
`ProgressionRecommendation` (increase/maintain/reduce load, recovery
adjustment, or wait for more data) per exercise, storing the result,
reason, policy version, and any manual override.

## Current checkpoint

All checkpoints complete and committed. Milestone 6 is done.

## Checkpoint checklist (Milestone 6)

- [x] CP1 — Domain: `ProgressionRecommendation`, `ManualOverride`, `ProgressionPolicyV1` + tests
- [x] CP2 — Application: use cases + tests
- [x] CP3 — Room persistence: migration 5->6
- [x] CP4 — Instrumented migration + DAO tests
- [x] CP5 — Presentation: surface recommendation + override control
- [x] CP6 — Full verification, manual smoke test, docs + archival

## Verification performed at milestone completion

- `./gradlew testDebugUnitTest` — full suite, 267 tests, 0 failures
  (one known pre-existing flaky test,
  `com.repflow.app.presentation.exercise.list.ExerciseListViewModelTest`,
  intermittently times out under load but passes in isolation —
  unrelated to this milestone, same class of turbine/coroutine timing
  flake documented for `ActiveWorkoutViewModelTest` in Milestone 5).
- `./gradlew spotlessApply detekt lintDebug` — clean.
- `./gradlew connectedDebugAndroidTest` (emulator-5554, full suite) —
  107 tests, 0 failures, 0 errors. Includes the new
  `migrate5To6_addsProgressionRecommendationsTable` migration test and
  `ProgressionRecommendationDaoTest`.
- Manual smoke test: `installDebug`, launched the app, ran
  `adb shell monkey` (300 events, 70% touch/10% motion/20% nav) — no
  `FATAL EXCEPTION` in logcat.

## Known pre-existing flaky tests (not fixed, out of scope)

- `ActiveWorkoutViewModelTest`'s edit-set test (documented in Milestone
  5's completion notes).
- `ExerciseListViewModelTest` (turbine `TurbineAssertionError: No value
  produced in 3s` under full-suite load; passes in isolation). Both
  appear to be the same class of coroutine/turbine timing race under
  concurrent full-suite execution, pre-dating this milestone's changes.

## Unresolved decision carried forward

`ProgressionPolicyV1`'s thresholds are an explicit, documented v1
assumption (see `docs/milestones/completed/milestone-6-reference.md`'s
"Unresolved decision" section) — future product review may refine them
via a `ProgressionPolicyV2` without touching callers.

## Active plan

None currently active. Milestones 3, 4, 5, and 6 are complete and
committed; see `docs/milestones/completed/`.

## Next milestone

See `docs/ROADMAP.md` for the next incomplete milestone.
