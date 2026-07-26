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

**Implementing.** P0 and CP0 committed. Next: CP1 (bottom navigation
redesign).

## Checkpoint checklist (Milestone 8, revised round 3)

- [x] P0 — Crash fix: add missing `@HiltViewModel` (Recovery/History/Backup) — `80ec209`. Verified on a real connected device (uninstall/reinstall, tapped all six destinations, no crash) and via 3 new instrumented regression tests (`connectedDebugAndroidTest`, `MainActivityNavHostSmokeTest`, 4/4 passed on `SM-S928B`).
- [x] CP0 — Audit doc + this doc set + roadmap update — `0fb165f`.
- [ ] CP1 — Bottom navigation redesign (user-approved Material 3 `NavigationBar`)
- [ ] CP2 — Save-feedback hardening (exercise-archive + Recovery/futsal snackbars)
- [ ] CP3 — Recovery/futsal: 0-5 scale, past-date entry
- [ ] CP4 — Recovery/futsal history screen
- [ ] CP5 — Precise progression reasons (3 detectable conditions)
- [ ] CP6 — Start-from-plan
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
