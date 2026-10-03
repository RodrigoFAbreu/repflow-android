# RepFlow Roadmap

Every roadmap milestone (0-8) is complete, and the Figma-led redesign
(`repflow-redesign-visual-foundation`, below) is accepted and closed. Milestone 8 (post-MVP functional
usability stabilization, created from the user's own hands-on findings) is
accepted and closed under an explicit user waiver of its planned
functional-review gate. See [status](ACTIVE_MILESTONE.md) for the full
acceptance record.

## Milestone 0 — Project foundation

- [x] Confirm generated Android project builds
- [x] Establish package naming and build conventions
- [x] Add repository instructions and architecture documentation
- [x] Configure formatting, linting, and baseline tests
- [x] Define the incremental modularization plan

## Milestone 1 — Exercise library vertical slice ✓ complete

- [x] Define exercise domain model
- [x] Define exercise repository capability
- [x] Add application use cases
- [x] Add Room persistence
- [x] Add exercise list and editor UI
- [x] Add unit, repository, and basic UI tests

## Milestone 2 — Training plans ✓ complete

- [x] Versioned training plan domain
- [x] Plan creation and editing
- [x] Ordered exercises and targets
- [x] Historical version preservation

## Milestone 3 — Active workout ✓ complete

- [x] Start and resume workouts
- [x] Exercise progression
- [x] Fast set entry
- [x] Immediate persistence
- [x] Editing and undo

## Milestone 4 — Rest timer ✓ complete

- [x] Absolute end timestamps
- [x] Background and process recovery
- [x] Notifications and haptics
- [x] Timer-related tests

## Milestone 5 — Recovery and futsal ✓ complete

- [x] Recovery entry
- [x] Futsal load
- [x] Workout-day context

## Milestone 6 — Progression recommendations ✓ complete

- [x] Deterministic progression policy
- [x] Explainable recommendation reasons
- [x] Recovery adjustments
- [x] Manual overrides
- [x] Policy versioning

## Milestone 7 — History and backup ✓ complete

- [x] Workout and exercise history
- [x] Basic progress views
- [x] Versioned JSON backups
- [x] CSV exports
- [x] Restore validation and safety snapshots

## Milestone 8 — Post-MVP functional usability stabilization ✓ complete

Created from the user's own hands-on functional findings after Milestone 7,
not from the original MVP goal list. See
`docs/milestones/completed/milestone-8-reference.md` for full scope.
Implementation and all four external implementation-review rounds are
complete and approved (round 4: `APPROVE`, HEAD `dc4381a`, confirmed
reachable from `main`). The planned manual functional-review pass was
**explicitly waived by the user** on 2026-07-31 — not performed, and not
claimed to have passed. Detailed UI/UX validation is deferred to a future,
separate Figma-led redesign milestone (not yet planned). See
[status](ACTIVE_MILESTONE.md) for the full disposition.

- [x] P0 crash fix: missing `@HiltViewModel` on 3 ViewModels
- [x] Navigation: bottom-navigation redesign (user-approved)
- [x] Recovery/futsal: past-date entry, 0-5 scale, history visibility, save feedback
- [x] Workout/plans: start-from-plan, warm-up/working classification, RPE/duration/pain/technique entry
- [x] History: filtering/sorting (exercise, date, plan), safe accidental-workout removal, detail consistency
- [x] Plans/exercises: training-plan archive/restore, exercise archive-snackbar fix
- [x] Backup: edge-case hardening + new-field schema coverage + v1-backward-compatibility
- [x] Full verification + external implementation review; user functional review waived (see ACTIVE_MILESTONE.md)

## Redesign — Visual foundation ✓ complete

The Figma-led redesign deferred from Milestone 8
(`repflow-redesign-visual-foundation`), with its two remediation children
(`-remediation-1`, `-remediation-1-remediation-1`), is complete. The user
accepted it after functional-review round 3 (re-test of the five round-2
failures: PASS, no Blocking or Important findings). Plans:
`docs/milestones/completed/repflow-redesign-visual-foundation-execution.md` and
`-reference.md`. Open follow-ups, deliberately not built here: `D149`-`D162`
(newer-design differences) and `P3-F-1` (History detail stat-tile values
truncated at font scale 2.0). See [status](ACTIVE_MILESTONE.md).

- [x] Visual foundation, theme, components and screen redesign
- [x] Technical approval (implementation revision 9, `EXTERNAL_APPROVE`)
- [x] Functional review accepted by the user (round 3)
