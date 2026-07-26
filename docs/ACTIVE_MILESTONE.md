# Active Milestone

## Milestone

Milestone 7 — History and backup — **PLANNING COMPLETE, CP1 STARTING**

## Goal

Let the user browse completed workout history and provide a versioned,
explicit backup export/import (plus CSV export), per
`docs/milestones/active/milestone-7-reference.md`.

## Current checkpoint

Planning complete: `docs/milestones/active/milestone-7-{execution,reference}.md`
created. "Backup file location and retention count" (an explicit Open
decision in `docs/TECHNICAL_DECISIONS.md`) is resolved via a documented
v1 assumption: Storage Access Framework picker per export/import, no
automatic retention. CP1 (domain) starting next.

## Checkpoint checklist (Milestone 7)

- [ ] CP1 — Domain: `BackupSnapshot`, `BackupValidationError` + tests
- [ ] CP2 — Application: history/backup use cases + tests
- [ ] CP3 — Data: JSON mapper, `LocalBackupRepository.replaceAll`, CSV formatter
- [ ] CP4 — Instrumented: `replaceAll` atomicity test
- [ ] CP5 — Presentation: History screen + Backup screen with SAF wiring
- [ ] CP6 — Full verification, manual smoke test, docs + archival

## Current blockers

None yet.

## Active plan

`docs/milestones/active/milestone-7-execution.md`,
`docs/milestones/active/milestone-7-reference.md`.

Milestones 3, 4, 5, and 6 remain complete and committed; see
`docs/milestones/completed/`.
