# Milestone 8 execution — Post-MVP functional usability stabilization

See `milestone-8-reference.md` for full rationale, the functional audit
evidence, the "Decisions" (approved v1 assumptions), the navigation
redesign's approved requirements, the recommendation-reason scope, and the
consolidated migration plan. **Revised in round 3** of plan review: added
a P0 crash fix, expanded CP1 to the approved bottom-nav redesign,
consolidated four migrations into one, split start-from-plan from
set-entry UI, broadened History filtering and snackbar coverage, added
checkpoint-level device verification, and made the external
implementation-review gate explicit.

## Checkpoints

0. **P0 - Crash fix**: add `@HiltViewModel` to `RecoveryFutsalViewModel`,
   `HistoryViewModel`, `BackupViewModel` (confirmed missing by direct
   comparison against `ActiveWorkoutViewModel`, which has it — a real,
   likely-crashing defect, not a hypothetical one). Round 4 guardrail: at
   this checkpoint, re-confirm via `grep -rL "@HiltViewModel"` across
   every `*ViewModel.kt` under `presentation/` that no other
   `hiltViewModel()`-constructed ViewModel has the same omission (already
   checked once during round-3 planning — `ExerciseListViewModel`,
   `TrainingPlanListViewModel`, `ExerciseEditorViewModel`,
   `TrainingPlanEditorViewModel`, `ActiveWorkoutViewModel` all correctly
   have it — re-verify at implementation time since this is a one-line,
   scoped fix, not a broader DI refactor). Install and launch on a
   connected emulator/device; open all six top-level destinations;
   capture any crash with `adb logcat`; fix root causes; add a Compose UI
   regression test per screen asserting it composes without throwing.
   Manually reopen each destination after the fix. Verify:
   `./gradlew testDebugUnitTest`, `connectedDebugAndroidTest`, plus the
   manual device pass above — do not rely on route-registration alone
   (round-3 finding: registration is not crash evidence).
1. **CP0 - Audit + docs**: `docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md`,
   this execution/reference pair, `docs/ACTIVE_MILESTONE.md`,
   `docs/ROADMAP.md` (Milestone 8, not yet complete) — already drafted
   during planning; correct the audit's "No destination crashes" row to
   reflect P0's finding. No code change.
2. **CP1 - Bottom navigation redesign [user-approved]**: replace the
   `TextButton`s in `ExerciseListScreen.kt`'s `TopAppBar.actions` with a
   Material 3 `NavigationBar` (single source of truth for the six
   top-level destinations, selected state, `launchSingleTop` +
   `popUpTo`/state restoration, no Up action at top level, accessible
   labels/touch targets, no label wrap/overflow). Nested editors/detail
   screens keep their existing Up button (already correct). Device check:
   phone-sized emulator + increased font scale. Add a navigation
   regression test where practical. Verify: `testDebugUnitTest`,
   `connectedDebugAndroidTest --tests "*Nav*"`, manual device pass.
3. **CP2 - Save-feedback hardening (Exercise + Recovery/futsal)**: finite
   `SnackbarDuration` for the exercise-archive Undo snackbar (currently
   implicitly `Indefinite`); for Recovery/Futsal, add an `isSaving` guard
   *and* dismiss/replace any showing snackbar before presenting a new one,
   ensure no snackbar survives a top-level destination switch (relevant
   now that CP1 makes switching tabs cheap), and confirm Undo still
   targets the exact archived item. Device check: rapid double-tap Save
   and rapid tab-switch-while-saving on an emulator. Verify:
   `testDebugUnitTest --tests "*Exercise*" --tests "*Recovery*"`.
4. **CP3 - Recovery/futsal usability**: widen `SCALE_RANGE`/`SCALE_MIN/MAX`
   to `0..5`; add a date field to `RecoveryFutsalUiState` + a date picker,
   wire `RecoveryFutsalViewModel` to use the selected date instead of
   `clock.now()`. No schema change (existing columns). Device check:
   record an entry for a past date, confirm it persists correctly. Verify:
   `testDebugUnitTest --tests "*Recovery*"`.
5. **CP4 - Recovery/futsal history screen**: new read-only list screen +
   route rendering `RecoveryRepository.findAll()`/`FutsalRepository.findAll()`,
   reachable from the new bottom nav's Recovery tab. Presentation-only, no
   schema. Verify: `testDebugUnitTest`, `lintDebug`, manual device pass.
6. **CP5 - Precise recommendation reasons**: split
   `ProgressionPolicyV1.kt`'s insufficient-data branch into three
   detectable reasons — too-few-sets, no-planned-rep-range, and (new)
   only-warm-up-sets-recorded, the last requiring a small
   `ProgressionPolicyInput` addition populated from data
   `CompleteWorkoutSession.kt` already has. Explicitly does **not** add an
   "insufficient eligible completed history" reason — not detectable by
   the current policy without a real logic expansion (see reference doc).
   Domain only, no schema, no threshold change. Add a test per reason and
   one plan-linked path producing a real actionable recommendation.
   Verify: `testDebugUnitTest --tests "*Progression*"`.
7. **CP6 - Start-from-plan**: add a plan picker to the start-workout flow;
   wire real `trainingPlanVersionId`/`plannedExerciseId` into
   `StartWorkoutSession`/`AddWorkoutExercise` instead of hardcoded `null`.
   Ad-hoc (no plan) remains available as an explicit choice. No schema
   change (fields already exist). Device check: start a workout from a
   plan, confirm the session records the plan-version link. Verify:
   `testDebugUnitTest --tests "*StartWorkout*"`.
8. **CP7 - Warm-up toggle + RPE/duration entry**: separate commit from
   CP6 (round-3 split). Add a warm-up/working toggle and RPE/duration
   inputs to set entry, wiring real values instead of hardcoded
   `false`/`null`. No schema change. Device check: record a warm-up set
   and a working set with RPE/duration in one session, confirm both
   persist distinctly. Verify: `testDebugUnitTest --tests "*ActiveWorkout*"`.
9. **CP8 - Consolidated schema migration**: `MIGRATION_6_7` adding all
   five new nullable columns at once (`workout_sets.pain`,
   `workout_sets.technique_quality`, `planned_exercises.target_warmup_sets`,
   `workout_sessions.invalidated_at`, `training_plans.archived_at`) —
   schema-only, no UI yet. `exportSchema = true` is already set
   (`RepFlowDatabase.kt:50`), with schemas at
   `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/`
   (currently `1.json`-`6.json`) — building after the version bump must
   produce a new `7.json`, which gets committed alongside the migration
   (round-4 guardrail: don't miss committing the generated schema file).
   Migration test: every pre-migration row across all four tables survives
   with all five columns `NULL`; each column round-trips a written value
   post-migration. Verify: `testDebugUnitTest`,
   `connectedDebugAndroidTest --tests "*Migration*"`.
10. **CP9 - WorkoutSet pain + technique-quality UI**: domain + entity +
    mapper wiring for the two new columns (schema already landed in CP8);
    set-entry UI inputs; clear UI labeling distinguishing per-set pain
    from Recovery's `painWhileWalking` (round-3 finding #9). Device check:
    record a set with pain + technique quality, confirm History shows
    both distinctly from Recovery's pain field. Verify: `testDebugUnitTest`.
11. **CP10 - Planned warm-up/working structure UI**: domain + entity +
    mapper wiring for `targetWarmupSets`; plan-editor UI. Document
    explicitly (round-3 finding #8): existing `targetSets` continues to
    mean "planned working sets" post-migration (no rename, no semantic
    change to existing plans); `targetWarmupSets` defaults to `null`
    ("no warm-up guidance") for every plan that predates this milestone;
    validation range mirrors `TargetSets`'s existing non-negative-int
    rule; backup/restore already covers this field via CP8's schema work
    plus the backup-mapper update in CP12. Verify: `testDebugUnitTest`.
12. **CP11 - Completed-workout invalidation UI**: application use case
    (mirrors `AbandonWorkoutSession`'s shape) + History UI action +
    `WorkoutSessionDao` query update excluding `invalidated_at IS NOT NULL`
    from `observeCompletedSessions()`, exactly like `ABANDONED`. Device
    check: invalidate a completed session from History, confirm it
    disappears from History and from a recomputed progression
    recommendation. Verify: `testDebugUnitTest --tests "*Workout*"`,
    `connectedDebugAndroidTest --tests "*Workout*"`.
13. **CP12 - Training-plan archive/restore UI**: mirror `Exercise`'s
    existing pattern (`archivedAt`/`isArchived`/`archive()`/`restore()`,
    schema already landed in CP8); `ArchiveTrainingPlan`/
    `RestoreTrainingPlan` use cases; exclude archived plans from CP6's
    plan picker. Device check: archive a plan, confirm it's excluded from
    the start-workout picker; restore it, confirm it reappears. Verify:
    `testDebugUnitTest`.
14. **CP13 - History field-consistency + filtering/sorting**: surface
    RPE, warm-up indicator, duration, pain, and technique quality in
    `HistoryDetailScreen.kt` (recordable as of CP7/CP9); align which
    fields the list vs. detail screens show. Add filtering by exercise,
    date range, and training plan; sort by date asc/desc, newest-first
    default; add an invalidated-session show/hide filter (falls out of
    CP11's field, not a new decision). Presentation + ViewModel/query
    level, no new schema. Verify: `testDebugUnitTest --tests "*History*"`.
    Round-4 guardrail: if this grows too large for one commit, split into
    independently-verified sub-commits (e.g. invalidation-filter display,
    complete detail presentation, filters/sorting) — approved in advance,
    no new plan-review round needed for that split as long as approved
    product behavior doesn't change.
15. **CP14 - Backup hardening + backward compatibility**: add tests for
    SAF cancellation (`Intents.intending` stub), the unsupported-version
    path through the real `LocalBackupRepository`/`RestoreBackup` (not
    just `BackupSnapshot.create`), a distinct-id/same-`nameKey` uniqueness
    conflict, and — new in round 3 — restore from a real v1-shaped backup
    fixture (asserting the five new fields come back `null`, everything
    else round-trips) using null-safe optional reads in
    `BackupJsonMapper` rather than version-branching logic. Bump
    `BackupSnapshot.CURRENT_SCHEMA_VERSION` to 2. Verify:
    `testDebugUnitTest`, `connectedDebugAndroidTest --tests "*Backup*"`.
16. **CP15 - Full verification**: full `testDebugUnitTest`,
    `spotlessCheck`, `detekt`, `lintDebug`, `connectedDebugAndroidTest`
    (full suite), `assembleDebug`, `assembleRelease`; re-run
    `ActiveWorkoutViewModelTest` in isolation several times to
    characterize the reported flakiness one way or the other (record the
    outcome, don't assume from one run). **Also** characterize
    `ExerciseListViewModelTest`'s `undo archive restores the exercise via
    RestoreExercise"` test, found genuinely flaky during CP1 (failed 2 of
    ~7 reruns with `TurbineAssertionError: No value produced in 3s`,
    unrelated to CP1's own changes — `ExerciseListViewModel.kt` was not
    touched by CP1). Root-cause or document as a known flake with
    evidence; do not silently ignore it. Update
    `docs/ACTIVE_MILESTONE.md`.
17. **External implementation review**: run
    `scripts/prepare-ai-review.sh <base-sha> implementation`, stop at
    `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` per
    `docs/ai-workflow/MILESTONE_WORKFLOW.md`. Resolve all
    blocking/important findings before proceeding.
18. **CP16 - Functional-review prep**: only after implementation review is
    resolved, prepare the manual functional-review checklist for the user
    covering every checkpoint above. The user remains the sole milestone
    acceptance authority — do not self-accept.

## Commit policy (approved round 4)

A stable commit per coherent checkpoint or independently-working
sub-checkpoint; checkpoint boundaries may be refined during
implementation (split for size/safety, per CP13's note) without another
plan-review round, as long as approved product behavior doesn't change.
Every commit: builds and passes its own focused checks, avoids known
broken intermediate behavior, contains no unrelated changes, uses a
descriptive conventional-commit message. Never squash the whole milestone
into one commit. No push/merge/rebase/force-push/PR at any point.

## Functional audit discipline (approved round 4)

`docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md` is a living document
through implementation, not a one-time snapshot: update each item's
status as it moves from Absent/Unreachable/Partial to
implemented → automated-test-verified → device-verified, or to
explicitly, permanently deferred with a stated reason. The milestone is
never marked complete merely because every checkpoint's code is
committed — completion requires the audit reflecting reality, full
verification (CP15), external implementation review, and the user's own
functional-review acceptance.

## Completion criteria

Every checkpoint committed independently (P0 and CP8's schema migration
each as their own commit given crash/migration risk), narrow checks green
at each step (including the device checks noted per checkpoint — not
deferred to the end), full verification green at CP15, external
implementation review resolved at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`
(not skipped en route to functional review), functional-review checklist
ready, docs current. Milestone acceptance is gated on the user's
functional review, never on automated checks, review-bundle approval, or
all-checkpoints-committed alone.
