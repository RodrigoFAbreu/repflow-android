# Active Milestone

## Milestone

Roadmap milestones 0-7 (`docs/ROADMAP.md`) are complete. The post-MVP
engineering review (`docs/improvements/POST_MVP_ENGINEERING_REVIEW.md`,
`IMPROVEMENT_ROADMAP.md`) is also complete and committed (`48da0b0`).
**Milestone 8 - Post-MVP functional usability stabilization** is now
active, created from the user's own hands-on functional findings after
Milestone 7 (not a resumption of any prior work — none existed).

## Goal

Close the specific functional/usability gaps in
`docs/milestones/active/milestone-8-reference.md`'s "Goals" section:
Recovery/futsal date/scale/history/save-feedback fixes, workout/plan
set-classification and start-from-plan wiring, History filtering and safe
accidental-workout removal, training-plan archive/restore, navigation
consistency, and backup hardening — then a full verification pass and a
user functional-review checklist. `docs/improvements/IMPROVEMENT_ROADMAP.md`
§2.1 (`ReturnCount` tuning) is deferred until this milestone is accepted.

## Current checkpoint

**Implementing.** P0, CP0-CP6 committed. Next: CP7 (warm-up toggle +
RPE/duration entry UI).

## Checkpoint checklist (Milestone 8, revised round 3)

- [x] P0 — Crash fix: add missing `@HiltViewModel` (Recovery/History/Backup) — `80ec209`. Verified on a real connected device (uninstall/reinstall, tapped all six destinations, no crash) and via 3 new instrumented regression tests (`connectedDebugAndroidTest`, `MainActivityNavHostSmokeTest`, 4/4 passed on `SM-S928B`).
- [x] CP0 — Audit doc + this doc set + roadmap update — `0fb165f`.
- [x] CP1 — Bottom navigation redesign. Material 3 `NavigationBar` with the 6 top-level destinations, single source of truth (`RepFlowDestinations.TOP_LEVEL_DESTINATIONS`), `launchSingleTop`/`popUpTo`/`restoreState` (no duplicate back-stack entries, per-tab state preserved), removed the old ad-hoc `TextButton`s and the incorrect Up-arrow on Recovery/History. Verified on a real device: all 6 destinations tap through with no crash, correct selected-state, and (at 1.3x font scale) labels ellipsize instead of wrapping/overflowing. Full instrumented suite (113 tests) and unit suite green. Found and noted (not fixed here, out of CP1's scope): `ExerciseListViewModelTest`'s "undo archive" test is genuinely flaky (Turbine timeout, ~1-2 of 7 reruns), unrelated to CP1 — flagged for CP15.
- [x] CP2 — Save-feedback hardening. Exercise archive/Undo snackbar now uses an explicit finite `SnackbarDuration.Long` instead of the implicit `Indefinite` default; Recovery/futsal gained `isSavingRecovery`/`isSavingFutsal` guards (Save button disabled + a second call is a no-op while a save is in flight) and the snackbar now explicitly dismisses any still-showing snackbar before presenting a new one. Verified on a real device: triple-tapping Save produces exactly one "Saved" snackbar (not stacked), and switching tabs mid-save leaves no stuck snackbar and doesn't crash. Full instrumented suite (113 tests) and unit suite green (2 new regression tests for the double-tap guard).
- [x] CP3 — Recovery/futsal usability. Widened `RecoveryEntry.SCALE_RANGE`/`RecoveryFutsalUiState.SCALE_MAX` from 0-4 to 0-5 (fixed the now-stale "/4" in `ProgressionPolicyV1`'s recovery-reason strings to "/5" too); added a date field + Material3 `DatePickerDialog` (future dates disabled) wired through `RecoveryFutsalViewModel.onDateChanged`, which reloads/resets the screen for the selected date and threads it into both save use cases instead of always using "today". No schema change. Verified on a real device: picked a past date (20 Jul), set Sleep quality to 5, saved, switched tabs and back — date and value both persisted correctly for that specific date. Full instrumented suite (113 tests) and unit suite green (5 new regression tests: date load/reset, widened-scale persistence). Note: this device-verification method (tab-switch only) turned out to prove in-memory ViewModel-state continuity, not a genuine disk re-read — CP4 caught this and used a stronger method (force-stop + relaunch) instead.
- [x] CP4 — Recovery/futsal history screen. New nested `RecoveryHistoryScreen`/`Route`/`ViewModel` (`recovery/history` route, reachable via a "View history" action on the Recovery screen's app bar; keeps an Up action since it's nested, consistent with the navigation rules) rendering `RecoveryRepository.findAll()`/`FutsalRepository.findAll()` - both already existed, previously only consumed by backup export. Presentation-only, no schema. Verified on a real device with a genuine cold-process restart (force-stop + relaunch, not just a tab switch) between saving and checking history, confirming a real disk round-trip rather than leftover in-memory state; also incidentally discovered the device's app data had been wiped since CP3 (`firstInstallTime == lastUpdateTime`, empty DB, cause not identified - re-verified CP1-CP4 all still work correctly with fresh data). Full instrumented suite (114 tests, 1 new) and unit suite green (2 new ViewModel tests).
- [x] CP5 — Precise progression reasons. Split `ProgressionPolicyV1`'s single OR'd insufficient-data message into three individually-detectable reasons, checked in order of specificity: only-warm-up-sets recorded (new `ProgressionPolicyInput.hadOnlyWarmupSets`, threaded from `CompleteWorkoutSession`'s already-computed `workingSets`/`exercise.sets`), too-few-working-sets, no-planned-rep-range. Explicitly did not add an "insufficient eligible completed history" reason - confirmed the policy has no access to prior occurrences, so that would be a real logic expansion, not a wording fix. Domain/application only, no schema, no threshold change. Full instrumented suite (114 tests) and unit suite green (3 new/updated policy tests). Incidentally found and confirmed (via 4 reruns, 2 failed) a second genuinely flaky pre-existing test, `ActiveWorkoutViewModelTest`'s set-edit test (`expected:<70.0> but was:<60.0>`) - unrelated to CP5, this is the specific flakiness the disputed round-2/3 review feedback referenced; now independently confirmed with real evidence rather than taken on faith. Both known flakes flagged for CP15.
- [x] CP6 — Start-from-plan. `ActiveWorkoutViewModel.onStartWorkout` now takes an optional `TrainingPlanVersionId`; a new plan-picker dropdown (mirroring the existing "Add exercise" picker) replaces the plain Start button, offering "Start without a plan" (ad-hoc, unchanged) plus every plan from the new `ObserveTrainingPlans`-backed `availablePlans` list. Starting from a plan seeds every one of its `PlannedExercise`s into the new session right after it's created, each carrying its `plannedExerciseId` so `CompleteWorkoutSession` can later resolve a planned rep range for progression. No schema change (fields already existed, just never wired). Fixed a real bug found while writing the test: a nested `stateIn(WhileSubscribed)` for the plan list raced the outer `uiState` combine's own subscription; replaced with an eagerly-collected plain `MutableStateFlow` (mirroring the existing `dayContext` pattern) for reliable synchronous `.value` reads. Verified on a real device end-to-end: created a real exercise and plan through the UI, started a workout from that plan, confirmed the planned exercise was auto-seeded, then force-stopped and relaunched to confirm it persisted to disk (not just in-memory), then cleaned up via Abandon. Full instrumented suite (114 tests) and unit suite green (1 new test, confirmed non-flaky across 3 reruns) — only the two already-known pre-existing flakes (CP1, CP5) failed intermittently, unrelated to CP6.
- [ ] CP7 — Warm-up toggle + RPE/duration entry UI
- [ ] CP8 — Consolidated schema migration (`MIGRATION_6_7`, 5 new columns)
- [ ] CP9 — WorkoutSet pain + technique-quality UI
- [ ] CP10 — Planned warm-up/working structure UI
- [ ] CP11 — Completed-workout invalidation UI
- [ ] CP12 — Training-plan archive/restore UI
- [ ] CP13 — History field-consistency + filtering/sorting (incl. plan)
- [ ] CP14 — Backup hardening + v1-backward-compatibility
- [ ] CP15 — Full verification
- [ ] External implementation review (`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`)
- [ ] CP16 — Functional-review checklist prep

## Current blockers

None for planning. Plan is approved; implementation has not started.
Round 3 (`REVISE`) added a P0 crash fix (missing `@HiltViewModel` on 3
ViewModels, verified directly against the code) and — after the user was
asked directly and approved it themselves — a full bottom-navigation
redesign; consolidated four planned migrations into one; and broadened
several checkpoints (snackbar coverage, recommendation reasons, History
filters, backup backward-compatibility). Round 4 (`APPROVE`) added
implementation guardrails (exhaustive `@HiltViewModel` re-check, Room
schema-export commit discipline, explicit commit policy, living
functional-audit discipline) — all folded into
`milestone-8-execution.md`. Nothing is deliberately left open.

## Active plan

`docs/milestones/active/milestone-8-execution.md` and
`milestone-8-reference.md`. Milestones 1-7 remain archived at
`docs/milestones/completed/`.

## Next action

Plan is approved. Waiting for the user to invoke `/milestone-implement`
when ready — implementation does not start automatically.
