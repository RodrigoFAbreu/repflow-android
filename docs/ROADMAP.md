# RepFlow Roadmap

Every roadmap milestone (0-7) is complete. Milestone 8 (post-MVP functional
usability stabilization, created from the user's own hands-on findings) is
in progress. See [status](ACTIVE_MILESTONE.md) for the current state.

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

## Milestone 8 — Post-MVP functional usability stabilization (in progress)

Created from the user's own hands-on functional findings after Milestone 7,
not from the original MVP goal list. See
`docs/milestones/active/milestone-8-reference.md` for full scope.

- [ ] P0 crash fix: missing `@HiltViewModel` on 3 ViewModels
- [ ] Navigation: bottom-navigation redesign (user-approved)
- [ ] Recovery/futsal: past-date entry, 0-5 scale, history visibility, save feedback
- [ ] Workout/plans: start-from-plan, warm-up/working classification, RPE/duration/pain/technique entry
- [ ] History: filtering/sorting (exercise, date, plan), safe accidental-workout removal, detail consistency
- [ ] Plans/exercises: training-plan archive/restore, exercise archive-snackbar fix
- [ ] Backup: edge-case hardening + new-field schema coverage + v1-backward-compatibility
- [ ] Full verification + external implementation review + user functional review
