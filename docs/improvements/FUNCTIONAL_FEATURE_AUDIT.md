# Functional Feature Audit — Milestone 8 baseline

Evidence-based classification of every gap the user reported after
hands-on use of the app post-Milestone 7, checked directly against the
current code (not assumed, not inferred from docs). This is the canonical
audit; `docs/milestones/active/milestone-8-reference.md` cites it rather
than duplicating it.

Classification legend: **Implemented** (works today) / **Unreachable**
(built below the UI, never wired up) / **Partial** / **Absent** (no such
capability exists).

## Recovery / futsal

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Past-date entry | Absent | `RecoveryEntry.date`/`FutsalSession.date` exist, but `RecoveryFutsalViewModel.kt:45,107,139` always use `clock.now()`; no date field in `RecoveryFutsalUiState.kt`; no date picker in `RecoveryFutsalScreen.kt`. | CP3 |
| 0-5 scale range | Absent (currently 0-4) | `RecoveryEntry.kt:63,82-84` (`SCALE_RANGE = 0..4`); `RecoveryFutsalUiState.kt:40-41` (`SCALE_MIN/MAX = 0/4`). | CP3 |
| Recovery/futsal visible in history | Unreachable | `RecoveryRepository.findAll()/findLatest()`, `FutsalRepository.findSince()/findAll()` exist, used only by `ExportBackup.kt`/`GetWorkoutDayContext.kt`; no list UI, no route. | CP4 |
| Save feedback doesn't block/overlap | Partial | No `isSaving` flag; Save buttons never disabled (`RecoveryFutsalScreen.kt:115,143`); double-tap can fire concurrent coroutines (`RecoveryFutsalViewModel.kt:106-127,138-149`). Snackbar itself is M3 default `Short` (no `actionLabel`), so not indefinite — round 3 broadens this beyond just an `isSaving` guard to full snackbar hardening. | CP2 |

## Workout / plans

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Start workout from a plan | Unreachable | `StartWorkoutSession`/`WorkoutSession.trainingPlanVersionId`, `WorkoutExercise.plannedExerciseId` already support this; `ActiveWorkoutViewModel.kt:123,135` hardcode `null`; no plan-picker UI. | CP6 |
| Ad-hoc workouts without a plan | Implemented | Same nullable path, exercised today as the only path in use. | — |
| Warm-up/working classification reachable | Unreachable | `WorkoutSet.isWarmup` exists (domain, entity, `MIGRATION_2_3`) and is already used downstream (`CompleteWorkoutSession.kt:48`), but `ActiveWorkoutViewModel.kt:157,200` hardcode `isWarmup = false`; no UI toggle. | CP7 |
| Planned warm-up/working-set structure | Absent | `PlannedExercise.targetSets: TargetSets` is a single int documented as "target working sets" (`TargetSets.kt:5-21`); no warm-up field anywhere. | CP8 (schema), CP10 (UI) |
| RPE/duration/pain/technique on set entry | Partial/Absent | RPE and `durationSeconds` exist domain+entity-side but hardcoded to `null` in the UI (`ActiveWorkoutViewModel.kt:155-156,198-199`); no input fields in `ActiveWorkoutScreen.kt`. Pain-per-set and technique quality don't exist as `WorkoutSet` fields at all (`RecoveryEntry.painWhileWalking` is a separate, session-level concept). | CP7 (RPE/duration), CP8+CP9 (pain/technique) |
| History shows recorded values | Partial | See History section below. | CP13 |
| Precise recommendation explanations | Partial | `ProgressionPolicyV1.kt:45-49`'s insufficient-data branch returns one OR'd sentence for two different conditions; every other branch already builds specific reasons. Round 3 adds a third detectable reason (only-warm-up-sets); explicitly declines a 4th ("insufficient eligible completed history") as not detectable by the current policy. | CP5 |
| Recommendations use eligible plan-linked working sets | Implemented (starved of real inputs) | `CompleteWorkoutSession.kt:48-54` already filters `isWarmup` and requires `plannedExerciseId → plannedRepRange`; rarely exercised today only because nothing is plan-linked/warm-up-classified from the UI yet (fixed by CP6/CP7). | — |

## History

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Consistent, complete detail | Partial | Date formatting matches between `HistoryScreen.kt` and `HistoryDetailScreen.kt`; but list shows date+count+duration only, detail shows raw load/reps only (`HistoryDetailScreen.kt:62-63`) with no RPE/warm-up/units — disjoint, not complete. | CP13 |
| Filtering/sorting | Absent | `HistoryUiState.kt:7-14` has no filter/sort state; DAO query is fixed `ORDER BY ended_at DESC` (`WorkoutSessionDao.kt:27-28`). Round 3 expands the approved dimensions to include training plan, not just exercise/date. | CP13 |
| Safe removal of an accidental workout | Partial/Absent for the reported case | `AbandonWorkoutSession` only ends an *in-progress* session (`ActiveWorkoutViewModel.kt:211`), correctly excluded from progression via `WorkoutSessionDao.kt:27`. Once `COMPLETED`, a session is permanent today (`WorkoutSession.kt:113`) — no removal path for an already-completed accidental workout. | CP8 (schema), CP11 (UI) |

## Plans / exercises

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Training-plan archive/restore | Absent | `TrainingPlan.kt:20-25` has no status field (contrast `Exercise.kt`'s `archivedAt`/`isArchived`/`archive()`/`restore()`); no use case exists. | CP8 (schema), CP12 (UI) |
| Archived plans excluded from workout start | N/A until archive exists | Nothing to filter yet. | CP12 |
| Exercise archive snackbar correctness | Partial | Undo-targeting correct and stable (`ExerciseListViewModel.kt:133-134`); no overlap possible (serialized queue + M3 guarantee); but no finite timeout — non-null `actionLabel` makes M3 default to `Indefinite` (`ExerciseListScreen.kt:89`), which also blocks the next snackbar. | CP2 |

## Navigation / UI

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Up-arrow consistency | Partial (inconsistent) | Plans/Workout/Backup correctly show no Up at top level; Recovery/History inconsistently show a back `TextButton` (`RecoveryFutsalScreen.kt:71-78`, `HistoryScreen.kt:51-56`). Nested editors always correctly show Up. | CP1 (subsumed into the bottom-nav redesign) |
| No destination crashes | **Corrected in round 3 — Unverified, likely broken** | Original classification ("Implemented") was based only on route registration, which is not crash evidence. Direct comparison confirms `RecoveryFutsalViewModel`, `HistoryViewModel`, and `BackupViewModel` are all missing `@HiltViewModel` (unlike `ActiveWorkoutViewModel`, which has it), while their routes obtain them via `hiltViewModel()` (`RecoveryFutsalRoute.kt`) — this almost certainly throws at runtime for 3 of 6 top-level destinations. No instrumented test exercises these routes via real Hilt injection, so this was never caught. | P0 |
| Phone-layout usability | Unverified by static reading | No hardcoded-width red flags; needs an actual device/emulator pass. | CP15, plus CP1's device check |
| Real bottom navigation bar | **Approved in round 3 (directly by the user, not inferred from review feedback)** | No `NavigationBar`/`BottomNavigation` composable exists today; top-level switching is via `TextButton`s in `ExerciseListScreen.kt`'s `TopAppBar.actions`. Originally deferred pending an explicit decision (`docs/TECHNICAL_DECISIONS.md`'s "Navigation structure" was Open beyond M1) — the user was asked directly and approved a Material 3 `NavigationBar` for the six top-level destinations with concrete requirements (see reference doc's "Navigation redesign"). | CP1 |

## Backup

| Item | Status | Evidence | Checkpoint |
|---|---|---|---|
| Invalid/corrupt JSON | Covered | `BackupJsonMapperTest` ("fails with Malformed..."), `RestoreBackupTest` (fake-repository level). | — |
| Unsupported schema version | Partial | Covered at `BackupSnapshot.create` (`BackupSnapshotTest`), not through the real end-to-end restore path. | CP14 |
| SAF cancellation | Absent (untested) | `BackupRoute.kt:34-42,53-63` no-ops on null URI; no `BackupRouteTest`/`BackupScreenTest` exists at all. | CP14 |
| Uniqueness conflict on restore | Partial | `LocalBackupRepositoryAtomicityTest` only exercises a PK collision that incidentally shares `nameKey`; no distinct-id/same-`nameKey` test. | CP14 |
| Transaction rollback on failure | Covered | `LocalBackupRepositoryAtomicityTest.replaceAll_rollsBackClearAllTablesWhenAMidTransactionInsertFails`. | — |
| Failed restore preserves current data | Covered | Same test as above. | — |
| Full field round-trip today | Covered, no gap | `BackupJsonMapperTest` round-trips every current field of `RecoveryEntry`, `FutsalSession`, `WorkoutSet`. New fields land in CP8 (schema); CP14 adds their backup coverage plus a real v1-backward-compatibility restore test (round-3 finding #10). | CP14 |

## Method

Each row above was produced by five parallel, read-only codebase
investigations (one per functional area) that cited file:line evidence for
every claim, cross-checked against `docs/DOMAIN_GLOSSARY.md`,
`docs/PROJECT_BRIEF.md`, and prior milestone reference docs where relevant.
No item was classified from documentation or memory alone.
