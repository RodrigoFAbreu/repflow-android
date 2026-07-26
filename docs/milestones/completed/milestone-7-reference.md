# Milestone 7 reference — History and backup

## Goals

- Let the user browse completed workout history (list of past sessions,
  each with its exercises/sets) without altering how completed history
  stays accurate when exercises/plans/policies later change (see
  `docs/DOMAIN_GLOSSARY.md`'s "Completed history").
- Provide a versioned, explicit backup export/import so a user's data
  survives an app reinstall or device change, per
  `docs/TECHNICAL_DECISIONS.md`'s "Backup" decision: an explicit
  versioned transfer schema, never serialized Room entities directly;
  restore validates before replacing data.
- Provide a CSV export of workout history for external use (spreadsheets).

## Non-goals

- No cloud/remote backup or sync (`docs/TECHNICAL_DECISIONS.md`'s
  "Future backend" - any future backend must go through an API; out of
  scope here).
- No automatic/scheduled backups - export and restore are explicit user
  actions in this milestone.
- No partial/selective restore - restore replaces local data wholesale
  after validation (mirrors "Room remains the local source of truth").
- No history editing from this milestone's UI (whether completed
  workouts may be manually corrected is an explicit "Open" decision in
  `docs/TECHNICAL_DECISIONS.md`; out of scope here).

## Unresolved decision — backup file location and retention

`docs/TECHNICAL_DECISIONS.md` lists "Backup file location and retention
count" as **Open**. Decision for this milestone (explicit assumption,
not a silent finalization, mirroring how Milestones 4/5/6 handled
similarly-scoped open items): the user picks the destination/source via
the platform's Storage Access Framework (`ACTION_CREATE_DOCUMENT` /
`ACTION_OPEN_DOCUMENT`) for both backup export and restore/CSV export.
The app keeps no automatic retention or history of prior backups - each
export is a single file the user is responsible for; no backups are
silently deleted or overwritten. This keeps the app free of a
file-management UI it doesn't need yet, and avoids assuming a specific
retention policy the product hasn't decided on.

## Domain invariants

- A backup snapshot has an explicit `schemaVersion: Int`, independent of
  the Room database version and app version (`docs/DOMAIN_GLOSSARY.md`'s
  "Backup schema version").
- Restore validates the entire snapshot (structurally and against domain
  invariants) before writing anything - a partially-invalid backup must
  never partially overwrite the local database.
- Restore replaces all local data atomically (a single Room transaction)
  or not at all.
- History reads never mutate data; they are pure projections over
  existing `WorkoutSession`/`WorkoutExercise`/`WorkoutSet` data.

## Architecture decisions

- New `domain.backup` package: `BackupSnapshot` (versioned aggregate: one
  schema version + every domain aggregate needed to reconstruct state -
  exercises, training plans, workout sessions, recovery entries, futsal
  sessions, progression recommendations) and `BackupValidationError`.
- New `application.backup` package: `ExportBackup` (reads every
  repository, builds a `BackupSnapshot`), `RestoreBackup` (validates,
  then atomically replaces local data via a new
  `BackupRepository.replaceAll` port), `ExportWorkoutHistoryCsv`.
- New `application.history` package: `ObserveWorkoutHistory` (completed
  sessions, newest first) built on a new `WorkoutRepository.observeCompletedSessions()`
  method (additive to the existing port).
- Serialization: plain `org.json` (`JSONObject`/`JSONArray`), already
  part of the Android SDK - no new Gradle plugin or dependency category
  needed for a one-shot export/import format. The mapping lives entirely
  in a new `data.backup.BackupJsonMapper` (data layer), never leaking
  `org.json` types into `domain`/`application`.
- CSV export is a simple manually-built CSV string (also data-layer only)
  - no new CSV library dependency needed for this scope.
- File I/O uses Android's Storage Access Framework via
  `ActivityResultContracts.CreateDocument`/`OpenDocument`, invoked from
  the presentation layer; the ViewModel receives an already-opened
  `Uri`/`OutputStream`/`InputStream` and delegates to the use cases.

## Data model / migration

No new Room schema version is required: `BackupSnapshot` is built by
reading through the existing repositories' domain objects and written
out as JSON, not stored as its own Room table. `WorkoutRepository` gains
one additive read method (`observeCompletedSessions()`); no entity or
migration changes.

## Presentation and UX behaviour

- A new History tab/screen lists completed workout sessions (date,
  duration, exercise count), tapping one shows its exercises and sets
  read-only.
- A new Backup screen (or a section of Settings) offers "Export backup",
  "Restore backup", and "Export history as CSV" actions, each launching
  the appropriate SAF picker.
- Restore shows a confirmation warning before replacing local data
  (data-loss-adjacent action).

## Test strategy

- Domain: `BackupSnapshot` validation tests (schema version bounds,
  structural invariants).
- Application: `ExportBackup`/`RestoreBackup` round-trip tests using
  in-memory fakes for every repository; `ObserveWorkoutHistory` tests;
  `ExportWorkoutHistoryCsv` formatting tests.
- Data: `BackupJsonMapper` round-trip tests (domain -> JSON -> domain).
- Instrumented: `BackupRepository.replaceAll` atomicity test against a
  real in-memory Room database (all tables replaced in one transaction).
- Presentation: `HistoryViewModel`/`BackupViewModel` unit tests.

## Checkpoint definitions

1. CP1 - Domain: `BackupSnapshot`, `BackupValidationError`, history read
   model types + tests.
2. CP2 - Application: `ObserveWorkoutHistory`, `ExportBackup`,
   `RestoreBackup`, `ExportWorkoutHistoryCsv` + tests.
3. CP3 - Data: `BackupJsonMapper`, `BackupRepository`/`LocalBackupRepository`
   (`replaceAll` in one Room transaction), CSV formatter + tests.
4. CP4 - Instrumented: `replaceAll` atomicity test on a real database.
5. CP5 - Presentation: History screen + Backup screen/section with SAF
   wiring.
6. CP6 - Full verification, manual smoke test (export, reinstall-equivalent
   restore, CSV export), docs + archival.

## Definition of Done

- All six checkpoints implemented, narrow-verified, and committed.
- Full unit + instrumented suites pass (aside from the two pre-existing,
  documented flaky tests).
- `spotlessCheck`/`detekt`/`lintDebug` clean.
- Manual smoke test of export -> wipe local data (in a throwaway
  in-memory scenario or via restore-over-itself) -> restore -> data
  intact.
- Docs updated and this milestone's plan archived.

## Stopping conditions

- If SAF wiring requires Activity-level plumbing this codebase doesn't
  yet have a precedent for, document the gap and choose the simplest
  safe integration rather than inventing a new architectural layer.
- If remaining session budget cannot safely complete a checkpoint,
  leave the repository at the last coherent, committed, verified state
  and hand off per `docs/ACTIVE_MILESTONE.md`.
