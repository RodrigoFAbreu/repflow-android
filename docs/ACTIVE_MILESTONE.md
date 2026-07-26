# Active Milestone

## Milestone

All roadmap milestones (0-7) are **complete**. RepFlow's MVP feature set per
`docs/ROADMAP.md` is fully implemented, verified, and committed.

## Goal

Milestone 7 - History and backup - let the user browse completed workout
history and provide a versioned, explicit backup export/import (plus CSV
export), per `docs/milestones/completed/milestone-7-reference.md`.

## Current checkpoint

Milestone 7 complete. All six checkpoints implemented, narrow- and
full-verified, and committed:

- CP1 (domain): `BackupSnapshot`, `BackupValidationError`, repository
  "read all" methods - `3edb4f7`.
- CP2 (application): `ObserveWorkoutHistory`, `ExportBackup`,
  `RestoreBackup`, `ExportWorkoutHistoryCsv` - `1f3379f`.
- CP3 (data): `BackupJsonMapper`, `LocalBackupRepository` - `7795bad`.
- CP4 (instrumented): `replaceAll` atomicity test on a real Room
  database - `2c56d27`.
- CP5 (presentation): History screen + Backup screen with SAF
  (Storage Access Framework) wiring - `52556c8`.
- CP6 (verification): export -> restore smoke test on a real database,
  full unit + instrumented suites, static checks - `160e65a`.

## Checkpoint checklist (Milestone 7)

- [x] CP1 — Domain: `BackupSnapshot`, `BackupValidationError` + tests
- [x] CP2 — Application: history/backup use cases + tests
- [x] CP3 — Data: JSON mapper, `LocalBackupRepository.replaceAll`, CSV formatter
- [x] CP4 — Instrumented: `replaceAll` atomicity test
- [x] CP5 — Presentation: History screen + Backup screen with SAF wiring
- [x] CP6 — Full verification, manual smoke test, docs + archival

## Verification actually run at completion

- `./gradlew testDebugUnitTest` - full suite passed.
- `./gradlew connectedDebugAndroidTest` - full suite passed on
  `emulator-5554` (Pixel 9 Pro XL AVD), including the new
  `LocalBackupRepositoryAtomicityTest` (3 tests: persist-on-success,
  roll-back-on-failure, export-then-restore round trip through real
  JSON and a real Room database).
- `./gradlew spotlessApply detekt lintDebug` - clean.
- One real defect was found and fixed during CP6: the smoke test's
  helper originally gave every seeded exercise the same name, which
  collided with the `exercises.name_key` UNIQUE constraint - a test
  bug, not a production bug. Root-caused via temporary logcat
  instrumentation of `LocalBackupRepository`'s catch block, then
  reverted; the fix was to give each fixture a distinct name.

## Current blockers

None. The roadmap has no further milestones.

## Active plan

None active. Milestone 7's plans are archived at
`docs/milestones/completed/milestone-7-{execution,reference}.md`.

All of Milestones 1-7 are complete and committed; see
`docs/milestones/completed/`.

## Next action

Per the user's explicit instructions for "after all roadmap milestones are
complete": conduct the post-MVP engineering review and produce
`docs/improvements/POST_MVP_ENGINEERING_REVIEW.md` and
`docs/improvements/IMPROVEMENT_ROADMAP.md`, then commit them as a single
documentation commit. Do not perform broad refactoring during that review
unless a small correction is necessary to complete or accurately assess it.
