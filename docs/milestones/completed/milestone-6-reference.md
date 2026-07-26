# Milestone 6 reference — Progression recommendations

## Goals

- After a workout is completed for a given training-plan exercise, compute
  a deterministic, local, explainable `ProgressionRecommendation`:
  increase load, maintain load, reduce load, apply a recovery adjustment,
  or wait for more data (per `docs/DOMAIN_GLOSSARY.md`).
- Every recommendation stores: the result, a human-readable reason (list
  of contributing factors), the policy version that produced it, and
  whether the user manually overrode it.
- Recommendations are versioned so historical recommendations remain
  meaningful even if the policy changes later (mirrors training-plan
  versioning's "completed history stays accurate" invariant).
- Surface the latest recommendation for an exercise wherever it's useful
  (e.g. next time that exercise appears in an active workout).

## Non-goals

- No LLM or remote AI/network dependency (`docs/TECHNICAL_DECISIONS.md`).
- No "technique quality" input — the domain model has no such field
  today (`WorkoutSet` tracks load/reps/duration/RPE only); adding one
  would be a domain-model change outside this milestone's scope. Deferred
  to a future milestone/decision.
- No change to how substitutions affect progression history (explicitly
  "Open" in `docs/TECHNICAL_DECISIONS.md`; recommendations here are
  scoped to a single `Exercise`, not plan substitutions).
- No UI for editing/configuring the policy's thresholds — v1 ships one
  fixed, versioned policy.

## Unresolved decision — exact progression formulas and thresholds

`docs/TECHNICAL_DECISIONS.md` explicitly lists "Exact progression
formulas and thresholds" as **Open**, with the instruction that "Agents
must not silently finalize these decisions during unrelated tasks."
Milestone 6's entire purpose, however, is to build this policy, so some
concrete v1 thresholds must exist to have a working, testable feature.

**Decision for this milestone (explicit assumption, not a silent
finalization):** ship a conservative, clearly-labelled **Policy v1**
with the thresholds below, structured so they are the *only* thing that
changes if/when product direction refines them (isolated in one
`ProgressionPolicyV1` object, easy to replace with a `V2` without
touching callers). This mirrors how Milestone 4 shipped a placeholder
90-second default rest duration and Milestone 5 shipped an assumed 0-4
recovery scale — both documented as assumptions rather than blocking
the milestone.

Policy v1 rules (evaluated in this order for a completed exercise
occurrence, using its planned `PlannedExerciseTarget.Reps` target where
available):

1. **Wait for more data** — fewer than `MIN_SETS_FOR_RECOMMENDATION` (2)
   working (non-warmup) sets recorded, or no planned rep range exists
   for the exercise (e.g. logged ad-hoc, outside any training plan).
2. **Recovery adjustment** — if the most recent `RecoveryEntry` (per
   `GetWorkoutDayContext`) has `painWhileWalking >= PAIN_THRESHOLD` (3)
   or `heavyLegs >= HEAVY_LEGS_THRESHOLD` (3), or a `FutsalSession` was
   recorded in the prior 24h — recommend a recovery adjustment
   regardless of performance, with the reason naming which signal
   triggered it.
3. **Reduce load** — otherwise, if the average RPE across working sets
   is `>= RPE_REDUCE_THRESHOLD` (9.0) or fewer than half the working
   sets reached the bottom of the planned rep range.
4. **Increase load** — otherwise, if every working set reached the top
   of the planned rep range and average RPE is `<= RPE_INCREASE_CEILING`
   (7.5).
5. **Maintain load** — otherwise (performance was within range but not
   clearly at either extreme).

These constants live in one place (`ProgressionPolicyV1`) and are
explicitly flagged in code comments and this doc as v1 placeholders for
product review, not a final design.

## Domain invariants

- `ProgressionRecommendation` is an immutable, date/exercise-scoped
  record: `id`, `exerciseId`, `result` (sealed:
  `IncreaseLoad`/`MaintainLoad`/`ReduceLoad`/`RecoveryAdjustment`/
  `WaitForMoreData`), `reasons` (non-empty list of factor strings for
  `WaitForMoreData`... actually always non-empty — every result has at
  least one contributing reason), `policyVersion` (Int), `computedAt`
  (Instant), `manualOverride: ManualOverride?` (nullable — absent until
  the user overrides it).
- `ManualOverride` stores the user's chosen alternative result and the
  Instant it was recorded — the original recommendation is never
  mutated or deleted, only annotated (glossary: "the original
  recommendation and the override should both be preserved").
- A recommendation is generated once per (exercise, completed workout)
  and is immutable thereafter — the *reasons the workout produced that
  recommendation* must stay historically accurate even if the exercise
  or training plan is edited later.

## Architecture decisions

- `ProgressionPolicyV1` is pure Kotlin (domain layer), a stateless
  function of its inputs — no repository access — so it's trivially
  unit-testable and has zero infra dependencies, consistent with domain
  purity.
- `ComputeProgressionRecommendation` (application layer) is the use case
  that gathers the inputs (recent `WorkoutSession`, planned target from
  `TrainingPlanVersion`, recovery/futsal context via
  `GetWorkoutDayContext`) and calls the pure policy function, then
  persists the result via a new `ProgressionRecommendationRepository`
  port.
- Persistence follows the same Room entity/mapper/repository pattern as
  Milestone 5 (new table, additive migration, no destructive changes).
- Presentation: recommendations surface as a read-only badge/summary
  next to an exercise the next time it's added to an active workout
  (reusing the `ExercisePickerItem`/`ActiveExerciseUi` shapes established
  in Milestone 3), plus a manual-override action.

## Data model / migration

New table `progression_recommendations`:
`id (PK), exercise_id (FK-like, indexed), result, reasons (delimited
text or JSON), policy_version, computed_at, override_result (nullable),
override_at (nullable)`. Migration `5 -> 6`, additive only.

## Presentation and UX behaviour

- When adding an exercise to an active workout, if a recommendation
  exists for that exercise, show its result + top reason inline (e.g.
  "Suggested: increase load — hit top of rep range at RPE 7").
- A manual-override control lets the user tap an alternative
  (increase/maintain/reduce) which is stored via `ManualOverride`
  without altering the original recommendation.
- No new full-screen destination needed for v1; this is an
  augmentation of the existing exercise-picker/active-workout flow.

## Test strategy

- Domain: `ProgressionPolicyV1` unit tests covering each of the 5 rule
  branches with representative input combinations (mirrors
  `RestTimerTest`/`RecoveryEntryTest` style).
- Application: `ComputeProgressionRecommendationTest` using in-memory
  fakes for workout/training-plan/recovery/futsal repositories.
- Persistence: mapper round-trip test + migration test
  (`migrate5To6_addsProgressionRecommendationsTable`) + instrumented DAO
  test.
- Presentation: ViewModel test verifying the recommendation surfaces
  and overrides are recorded without mutating the original.

## Checkpoint definitions

1. CP1 — Domain: `ProgressionRecommendation`, `ManualOverride`,
   `ProgressionPolicyV1` + unit tests.
2. CP2 — Application: `ProgressionRecommendationRepository` port,
   `ComputeProgressionRecommendation`, `RecordManualOverride` use cases +
   tests.
3. CP3 — Room persistence: migration 5->6, entity/DAO/mapper/repository.
4. CP4 — Instrumented migration + DAO tests on device.
5. CP5 — Presentation: surface recommendation + override control in the
   exercise picker / active-workout flow.
6. CP6 — Full milestone verification, manual smoke test, docs + archival.

## Definition of Done

- All CP1-CP6 checkpoints complete, committed.
- `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` pass.
- `connectedDebugAndroidTest` full suite passes on a real
  emulator/device.
- Room schema `6.json` exported and tracked; migration is additive,
  non-destructive.
- Manual smoke test performed (record sets meeting/missing a planned
  rep range, confirm a recommendation appears and an override can be
  recorded, no crash).
- `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` updated; plan docs
  archived to `docs/milestones/completed/`.

## Explicit stopping conditions for this milestone

- If instrumented tests cannot run (no emulator), document that
  explicitly rather than claiming they passed.
- If exact rep-range/RPE thresholds need product refinement, that is
  explicitly **not** a blocker for v1 per the "Unresolved decision"
  section above — ship the documented placeholder policy and flag it,
  do not stop.
- If UI presentation should become a dedicated screen with richer
  history, treat that as a genuinely open product/UX question and
  raise it rather than silently building a large new screen.
