# Functional Feature Audit — Milestone 8

Evidence-based classification of every gap the user reported after
hands-on use of the app post-Milestone 7, checked directly against the
current code (not assumed, not inferred from docs). This is the canonical
audit; `docs/milestones/active/milestone-8-reference.md` cites it rather
than duplicating it. This is a living document, kept in sync with
implementation through CP0-CP14 (see `docs/ACTIVE_MILESTONE.md` for the
full per-checkpoint verification detail each row below summarizes).

Classification legend: **Implemented** (works today) / **Unreachable**
(built below the UI, never wired up) / **Partial** / **Absent** (no such
capability exists). Each row also states its verification depth:
**automated-test-verified** (unit and/or real-device instrumented tests)
and/or **device-verified** (a human, or this agent, manually exercised the
flow on a real connected device).

## Recovery / futsal

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Past-date entry | Implemented, device-verified | `RecoveryFutsalUiState`/`Screen` gained a date field + M3 `DatePickerDialog` (future dates disabled), wired through `RecoveryFutsalViewModel.onDateChanged`. Verified on a real device: picked a past date, saved, switched tabs and back, value persisted for that date. | CP3 |
| 0-5 scale range | Implemented, device-verified | `RecoveryEntry.SCALE_RANGE`/`RecoveryFutsalUiState.SCALE_MAX` widened 0-4 → 0-5. Verified on a real device (Sleep quality = 5 saved and persisted). | CP3 |
| Recovery/futsal visible in history | Implemented, device-verified | New `RecoveryHistoryScreen`/`Route`/`ViewModel` (`recovery/history` route) renders `RecoveryRepository.findAll()`/`FutsalRepository.findAll()`. Verified on a real device with a genuine cold-process restart (force-stop + relaunch) between saving and checking history. | CP4 |
| Save feedback doesn't block/overlap | Implemented, device-verified | `isSavingRecovery`/`isSavingFutsal` guards disable Save and no-op a second call while in flight; snackbar dismisses any still-showing one first. Verified on a real device: triple-tapping Save produces exactly one snackbar, no stuck snackbar on tab switch. | CP2 |

## Workout / plans

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Start workout from a plan | Implemented, device-verified | `ActiveWorkoutViewModel.onStartWorkout` takes an optional `TrainingPlanVersionId`; new plan-picker dropdown seeds every `PlannedExercise`. Verified on a real device end to end, including a force-stop + relaunch to confirm the seeded exercise persisted to disk. | CP6 |
| Ad-hoc workouts without a plan | Implemented (unchanged) | Same nullable path, still the default. | — |
| Warm-up/working classification reachable | Implemented, device-verified | `ExerciseCard` gained a "Warm-up set" `Switch` threaded to `onRecordSet`/`onEditLastSet` → `WorkoutSet.isWarmup`. Verified on a real device: recorded distinct warm-up and working sets on the same exercise. | CP7 |
| Planned warm-up/working-set structure | Implemented, device-verified | `PlannedExercise.targetWarmupSets: Int?` (nullable, `TargetSets` keeps meaning working sets only) with a new "Warm-up sets (optional)" plan-editor field. Verified on a real device: created and reopened a plan, all four fields (incl. warm-up sets) round-tripped from disk. | CP8 (schema), CP10 (UI) |
| RPE/duration/pain/technique on set entry | Implemented, device-verified | `ExerciseCard` gained Duration/RPE fields (CP7); `WorkoutSet.pain`/`techniqueQuality` (`0..5`, distinct string keys from Recovery's `painWhileWalking`) added with their own set-entry fields (CP9). Verified on a real device both checkpoints: distinct warm-up/RPE/duration set persisted (CP7); Pain=3/Technique=4 recorded and shown in History, visibly distinct from Recovery's own pain value (CP9). | CP7 (RPE/duration), CP9 (pain/technique) |
| History shows recorded values | Implemented, device-verified | `HistoryDetailScreen` shows pain/technique (CP9) plus RPE/duration/warm-up (CP13). See History section below. | CP9, CP13 |
| Precise recommendation explanations | Implemented, automated-test-verified | `ProgressionPolicyV1`'s insufficient-data branch split into three individually-detectable reasons (only-warm-up-sets, too-few-working-sets, no-planned-rep-range), checked in order of specificity. Covered by unit tests; not independently device-verified (recommendation text, no dedicated UI flow to walk). A fourth reason ("insufficient eligible completed history") was explicitly declined as undetectable by the current policy. | CP5 |
| Recommendations use eligible plan-linked working sets | Implemented, device-verified (via CP6+CP7) | `CompleteWorkoutSession` already filtered `isWarmup` and required `plannedExerciseId → plannedRepRange`; CP6 (plan-linking) and CP7 (warm-up classification) supply the real inputs this needed, both device-verified independently. | CP6, CP7 |

## History

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Consistent, complete detail | Implemented, device-verified | `HistoryDetailScreen` now shows RPE, duration, warm-up indicator alongside the pain/technique fields CP9 already added. Verified on a real device across two clean reruns. | CP13 |
| Filtering/sorting | Implemented, device-verified | Full client-side filtering (exercise, plan identity across versions, date range, show/hide invalidated) and newest/oldest sort in `HistoryUiState.visibleSessions`. A real device-only bug (filter/sort controls scrollable off-screen and untappable) was found and fixed by switching to a wrapping `FlowRow`. | CP13 |
| Safe removal of an accidental workout | Implemented, automated-test-verified (real-device instrumented suite); not manually walked through live | Per-row "Invalidate" action + confirmation dialog → `InvalidateWorkoutSession` (`WorkoutSession.invalidate()`, mirrors `Exercise.archive()`, "excluded never deleted"). Covered by unit tests (domain invariants, NotFound/AlreadyInvalidated) and a real-device Compose instrumented suite (`HistoryScreenTest`, row action + dialog + snackbar). No live human create-then-invalidate walkthrough was performed this session — stated explicitly rather than implied. | CP8 (schema), CP11 (UI) |

## Plans / exercises

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Training-plan archive/restore | Implemented, device-verified | `TrainingPlan.archive()`/`restore()` mirror `Exercise`'s pattern exactly (same `ArchivedBeforeCreated` invariant); `TrainingPlanListScreen` gained the same Active/Archived filter + one-tap-archive-plus-Undo snackbar as `ExerciseListScreen`. Verified on a real device (full instrumented suite, 10 new tests). | CP8 (schema), CP12 (UI) |
| Archived plans excluded from workout start | Implemented, automated-test-verified | `ObserveTrainingPlans`/`observeOverviews()` take an explicit `TrainingPlanStatusFilter`; `ActiveWorkoutViewModel`'s plan picker always passes `ACTIVE`. Covered by `ActiveWorkoutViewModelTest`; not independently device-verified as its own flow (archiving itself was device-verified in CP12). | CP12 |
| Exercise archive snackbar correctness | Implemented, device-verified | Archive/Undo snackbar now uses explicit finite `SnackbarDuration.Long`, dismisses any still-showing snackbar first. Verified on a real device (part of CP2's triple-tap + tab-switch check). | CP2 |

## Navigation / UI

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Up-arrow consistency | Implemented, device-verified | Bottom-nav redesign removed the old ad-hoc back `TextButton`s on Recovery/History; nested screens (editors, Recovery history) correctly keep Up. Verified on a real device across all 6 destinations. | CP1 |
| No destination crashes | Implemented, device-verified | Missing `@HiltViewModel` on Recovery/History/Backup ViewModels fixed (P0). Verified on a real device (uninstall/reinstall, tapped all six destinations, no crash) plus 3 new instrumented regression tests confirming real Hilt injection, not just route registration. | P0 |
| Phone-layout usability | Implemented, device-verified | CP1's device pass confirmed labels ellipsize instead of wrapping at 1.3x font scale; CP13 found and fixed a real device-only bug where History's filter controls scrolled off-screen and became untappable (switched to `FlowRow`), reverified on-device afterward. | CP1, CP13 |
| Real bottom navigation bar | Implemented, device-verified | M3 `NavigationBar` with the 6 top-level destinations, single source of truth, `launchSingleTop`/`popUpTo`/`restoreState`. Approved directly by the user (round 3). Verified on a real device: all 6 destinations tap through, correct selected-state. | CP1 |

## Backup

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Invalid/corrupt JSON | Covered (unchanged) | `BackupJsonMapperTest` ("fails with Malformed..."), `RestoreBackupTest` (fake-repository level). | — |
| Unsupported schema version | Implemented, automated-test-verified (real device) | `LocalBackupRepositoryVersionCompatibilityTest.rejectsABackupNewerThanThisAppUnderstandsWithoutTouchingStoredData` exercises the real end-to-end path through `LocalBackupRepository`/`RestoreBackup`, not just `BackupSnapshot.create` in isolation; confirms stored data is untouched. | CP14 |
| SAF cancellation | Implemented, automated-test-verified (real device) | New `BackupRouteSafCancellationTest` uses `Intents.intending` to stub the `CreateDocument` picker returning `RESULT_CANCELED`; confirms busy state clears with no failure message. Export success is now reported only after the real SAF write completes (`BackupViewModel.onExportWriteSucceeded`/`onExportWriteCancelled`/`onExportWriteFailed`), not the moment JSON/CSV text is built in memory. | CP14 |
| Uniqueness conflict on restore | Implemented, automated-test-verified (real device) | New `LocalBackupRepositoryAtomicityTest.replaceAll_rollsBackOnADistinctIdSameNameKeyConflict` covers the distinct-id/same-`nameKey` case (a different `SQLiteConstraintException` path than the existing PK-collision test), confirming rollback either way. | CP14 |
| v1-backward-compatibility | Implemented, automated-test-verified (unit + real device) | `BackupSnapshot.CURRENT_SCHEMA_VERSION` bumped 1→2; the five new nullable fields (CP8-CP12) are read via existing null-safe optional helpers, no version-branching logic. `BackupJsonMapperTest` (hand-built v1 JSON, JVM) and `LocalBackupRepositoryVersionCompatibilityTest` (real device, through the actual restore path) both confirm a v1-shaped backup restores with all five fields `null` and everything else round-tripping. | CP14 |
| Transaction rollback on failure | Covered (unchanged) | `LocalBackupRepositoryAtomicityTest.replaceAll_rollsBackClearAllTablesWhenAMidTransactionInsertFails`. | — |
| Failed restore preserves current data | Covered (unchanged) | Same test as above. | — |
| Full field round-trip | Covered, no gap | `BackupJsonMapperTest` round-trips every field of every backed-up table, including all five CP8-CP12 fields. | — |

## Method

The original classification (rows' "Absent"/"Unreachable"/"Partial"
baseline) was produced by five parallel, read-only codebase investigations
(one per functional area) that cited file:line evidence for every claim,
cross-checked against `docs/DOMAIN_GLOSSARY.md`, `docs/PROJECT_BRIEF.md`,
and prior milestone reference docs. This document has since been updated
checkpoint-by-checkpoint as CP0-CP14 landed, each update reflecting the
real verification performed at that checkpoint (see
`docs/ACTIVE_MILESTONE.md`) rather than being re-inferred from memory.
