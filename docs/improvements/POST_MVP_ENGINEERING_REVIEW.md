# Post-MVP Engineering Review

Date: end of Milestones 0-7 (roadmap complete).
Scope: `app/src/main` (Domain / Application / Data / Infrastructure /
Presentation), `app/src/test`, `app/src/androidTest`, Gradle build config,
Room schema/migrations, and repository documentation.

Snapshot metrics gathered for this review:

- Domain: 44 Kotlin files. Application: 56. Data: 18. Presentation: 48.
  Infrastructure: 28.
- Unit tests: 73 files. Instrumented tests: 16 files.
- `@Suppress` annotations in `app/src/main`: 68 (mostly `ReturnCount` (33)
  and `LongParameterList` (12), a handful of `MagicNumber`,
  `TooManyFunctions`, one `LongMethod`/`CyclomaticComplexMethod`
  combination, one `TooGenericExceptionCaught`).
- Room database version: 6, with 5 explicit `MIGRATION_x_y` objects in
  `RepFlowMigrations.kt`. No destructive migration fallback used anywhere.
- Zero `TODO`/`FIXME` markers in `app/src/main`.
- DI: Hilt, three modules (`SystemModule`, `DatabaseModule`,
  `RepositoryModule`).
- Navigation: single `RepFlowNavHost` + `RepFlowDestinations` object
  (string-route based, no type-safe nav graph library).

## Strengths

- **Layering is real, not decorative.** Domain (44 files) has zero
  Android/Room/Compose imports across all seven milestones; every
  milestone's checkpoint plan explicitly separated Domain → Application →
  Data → Presentation work, and this was verified checkpoint-by-checkpoint
  rather than assumed. Confidence: high.
- **Migrations are consistently explicit and tested.** Every schema change
  across Milestones 0-7 added a numbered `MIGRATION_x_y` in
  `RepFlowMigrations.kt` (5 total, version now at 6) with matching
  migration tests, and destructive fallback was never used. Confidence:
  high (verified directly during this session and prior milestones).
- **Backup design avoids the classic serialize-the-Room-entity trap.**
  `BackupSnapshot` (domain) and `BackupJsonMapper` (data) form a versioned,
  hand-rolled JSON schema independent of Room entity shape, per ADR
  guidance. Confidence: high — read and exercised directly this session.
- **Atomicity of destructive operations is tested on a real database, not
  assumed.** `LocalBackupRepositoryAtomicityTest` verifies
  `clearAllTables()` inside `withTransaction` rolls back correctly on
  failure — a specific, previously-flagged risk that could easily have
  shipped unverified. Confidence: high.
- **Rest timers use an absolute end-timestamp**, not a countdown counter,
  per the documented invariant — avoiding the common bug where a timer
  drifts or resets on process death/backgrounding. Confidence: high
  (implemented and tested in Milestone 4/5).
- **Test suite is proportionate, not just present.** 73 unit test files
  against 56 application + 44 domain files indicates real per-use-case
  coverage rather than a handful of smoke tests bolted on at the end.
  Confidence: medium (file count is a proxy, not a coverage measurement —
  no line/branch coverage tool is configured).
- **String resources are used consistently for user-facing text** (verified
  directly while adding ~24 new strings for History/Backup in Milestone 7;
  no hardcoded UI text was found needing extraction). Confidence: medium.

## Correctness risks

- **No coverage tooling (Jacoco/Kover) is configured.** Test file counts
  are a weak proxy for actual coverage; some ViewModels or edge-case
  branches may be under-tested despite a "test exists" appearance.
  Affected: whole project. Severity: medium. Confidence: high (no
  coverage plugin found in `app/build.gradle.kts` during this session's
  dependency inspection). Defer — valuable but not urgent given the
  layered test discipline observed.
- **No UI/Compose instrumentation tests exist for the new History/Backup
  screens beyond ViewModel-level tests.** The Milestone 7 DoD's "export →
  wipe → restore → data intact" requirement was verified via a
  repository-level smoke test (`LocalBackupRepositoryAtomicityTest`), not
  by tapping through the actual Compose UI, because no UI-automation
  tooling was available in this environment. Affected:
  `presentation/history`, `presentation/backup`. Severity: medium.
  Confidence: high (directly encountered this session). Should be fixed
  before relying on the UI layer for release-quality confidence — add
  Compose UI tests (`createAndroidComposeRule`) for at least the
  primary History and Backup user flows.

## Maintainability issues

- **`ReturnCount` is suppressed 33 times**, the single most common
  suppression in the codebase. This is a real signal that many functions
  (likely validation/mapping functions with multiple early-return guard
  clauses) are structured in a way the linter consistently flags.
  Affected: broadly across Domain/Application validation logic. Severity:
  low-medium. Confidence: high (directly counted). This is very likely a
  detekt threshold tuned too strictly for the guard-clause style favored
  in this codebase (a legitimate, readable pattern) rather than 33
  separate real problems — worth revisiting the `ReturnCount` threshold in
  `config/detekt/detekt.yml` rather than continuing to suppress function by
  function.
- **`LongParameterList` suppressed 12 times.** Often signals a
  constructor or use-case function that would benefit from a small
  parameter data class. Affected: various use cases/mappers. Severity:
  low. Confidence: medium (not each site individually re-inspected this
  session).
- **Navigation uses raw string routes** (`RepFlowDestinations`) rather than
  a type-safe navigation API (e.g. Compose Navigation's
  `@Serializable` route objects, available in the `navigation-compose`
  version already in use). Affected: `presentation/navigation`. Severity:
  low (works correctly today, but every new destination is one
  string-typo away from a silent runtime crash instead of a compile
  error). Confidence: medium.

## Architectural coupling

- **No feature/module boundaries below the single `app` Gradle module.**
  All layers live in one module; the Domain→Application→Data→Presentation
  discipline is enforced by convention and code review, not by the build
  graph (no Gradle module could physically prevent Presentation from
  importing a Room entity). Affected: whole project structure. Severity:
  low for current size (~194 main-source files), rising if the project
  grows substantially. Confidence: high. This is an explicitly-deferred,
  reasonable choice for an MVP-sized app per the "no speculative
  modularization" guardrail — flagged here as a scalability note, not a
  current defect.
- **DI is centralized in three Hilt modules** (`SystemModule`,
  `DatabaseModule`, `RepositoryModule`) rather than per-feature modules.
  This is appropriate at current scale but will become a wide,
  high-churn file if the app grows past its current feature set.
  Severity: low. Confidence: medium.

## Scalability limitations

- **No local/remote sync abstraction exists** (by design — Room is the
  documented MVP source of truth and network access is explicitly not
  required during workouts). If a future milestone introduces backend
  sync, the current repository interfaces (Domain-layer, already
  independent of Room) are a reasonable seam, but conflict
  resolution/sync-state modeling does not exist yet and would be new
  design work, not a refactor. Severity: informational, not a defect.
  Confidence: high.
- **Backup format versioning exists** (`BackupSnapshot` has an explicit
  schema version) but there's only ever been one version in production
  use so far; the upgrade-path code (parsing older versions forward) is
  unexercised in practice. Severity: low. Confidence: medium.

## Testing gaps

- No Compose UI instrumentation tests for History/Backup screens (see
  Correctness risks above — duplicated here because it's simultaneously a
  correctness and a coverage-gap concern).
- No coverage tooling configured, so gaps elsewhere in the codebase cannot
  be measured objectively, only estimated from file-count proportion.
- No emulator-based test verifying process-death recovery of the Backup
  screen's in-flight SAF (Storage Access Framework) picker state (the
  Active Workout milestone's process-death recovery was tested; Backup's
  transient UI state was not, though it is not persistence-critical
  domain data).

## UX/accessibility gaps

- No explicit accessibility audit (TalkBack traversal order, content
  description completeness beyond what detekt's `UnusedParameter`
  incidentally caught for `HistoryScreen`'s back button) was performed
  during this session. Severity: unknown/unverified. Confidence: low
  (not measured — flagged as an open question, not a confirmed defect).
- One-handed usability and screen-size adaptivity were not specifically
  reviewed in this session.

## Performance concerns

- No profiling was performed. Room queries observed during this session
  (history list, backup read-all) are simple, unindexed-beyond-PK selects
  over what is expected to be a small-to-moderate local dataset (a single
  user's workout history) — unlikely to be a near-term concern, but not
  measured.

## Data-integrity concerns

- None currently open. The one integrity-adjacent risk this project
  identified early (`clearAllTables()` inside `withTransaction` during
  backup restore) was specifically instrumented-tested this session and
  confirmed safe (see Strengths).

## Build/tooling opportunities

- No coverage tool (Jacoco/Kover) configured.
- No CI workflow was inspected as part of this review (out of scope
  unless `.github/workflows` exists and is enforcing these same gradle
  tasks — not verified this session).
- `org.json:json:20231013` was added as a `testImplementation` dependency
  specifically to work around Android's stubbed `org.json` in plain JVM
  unit tests. This is a narrow, low-risk fix but is worth a one-line note
  in `docs/TECHNICAL_DECISIONS.md` if that file doesn't already document
  it, so a future contributor doesn't wonder why a real JSON parser is a
  test-only dependency.

## Documentation and onboarding gaps

- Milestone execution/reference docs are thorough and consistently
  archived (`docs/milestones/completed/`), which is a strength, not a
  gap — noted here for completeness since the review categories request
  it.
- No top-level "how to run tests/build/lint locally" quick-reference was
  reviewed as part of this session; `AGENTS.md`/`.github/copilot-
  instructions.md` cover agent workflow but a human-facing README quick
  start was not verified as present or current.
