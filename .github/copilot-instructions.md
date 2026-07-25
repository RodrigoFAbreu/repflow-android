# RepFlow Copilot Instructions

Read @docs/PRODUCT_AND_ARCHITECTURE.md before planning or implementing
architectural, domain, persistence, navigation, or cross-cutting changes.

Read @docs/ROADMAP.md before proposing the next milestone.

## Project

RepFlow is a native, offline-first Android workout tracking application.

Primary technologies:

- Kotlin
- Jetpack Compose and Material 3
- Coroutines and Flow
- ViewModel and unidirectional data flow
- Room
- DataStore
- Hilt
- Navigation Compose
- WorkManager when background work is required
- Gradle Kotlin DSL

## Repository status

This repository currently contains only documentation and instructions
(`docs/`, `AGENTS.md`, this file) — there is no Gradle wrapper, `build.gradle.kts`,
or Kotlin source yet. The project is at Milestone 0 of `docs/ROADMAP.md`
("Project foundation"). The first real implementation slice is the exercise
library (Milestone 1). Do not assume `./gradlew` or any module exists until it
has actually been generated in this repo — check before running build/test/lint
commands.

## Commands

Once the Android project exists, prefer:

```bash
./gradlew tasks          # discover available tasks after project generation
./gradlew build
./gradlew test           # or a module-qualified variant, e.g. ./gradlew :domain:test
./gradlew lint
./gradlew detekt          # once Detekt is configured (see Quality below)
```

Use module- or class-qualified task names for a single test once modules exist,
e.g. `./gradlew :domain:test --tests "com.repflow.domain.SomeClassTest"`.

## Architecture

RepFlow uses a layered modular architecture (see `docs/adr/0003-layered-modular-architecture.md`
and `docs/PRODUCT_AND_ARCHITECTURE.md` for full detail). Preserve these
conceptual layers:

- Domain
- Application
- Data
- Infrastructure
- Presentation

Dependency direction (inner layers must never depend on outer layers):

```text
Presentation → Application → Domain
Data → Application/Domain contracts
Infrastructure → Data/Application contracts
```

UI event flow is unidirectional:

```text
UI event → ViewModel → use case → repository/domain → state → Compose UI
```

Layer responsibilities:

- **Domain**: pure Kotlin business concepts and rules (e.g. `Exercise`,
  `TrainingPlan`, `TrainingPlanVersion`, `WorkoutSession`, `WorkoutSet`,
  `RecoveryEntry`, `ProgressionRecommendation`). Protects invariants (e.g. no
  negative reps/load, valid rep ranges, completed sessions have a completion
  timestamp, recommendations carry a policy version and reason).
- **Application**: use cases that orchestrate domain rules and repository
  contracts (e.g. `StartWorkout`, `RecordSet`, `CompleteWorkout`,
  `CalculateProgressionRecommendation`, `ExportBackup`, `RestoreBackup`). Owns
  repository/contract interfaces (`ExerciseRepository`, `WorkoutRepository`,
  `Clock`, `IdentifierGenerator`, `NotificationScheduler`, etc.). Use cases
  should express meaningful operations (`RecordSet`), not CRUD wrappers
  (`InsertWorkoutSetRow`).
- **Data**: repository implementations, coordination between data sources, and
  explicit/testable mappings between domain models and persistence models.
  Must never leak Room entities, DAOs, cursors, or future API/DTO types
  through repository contracts.
- **Infrastructure**: technology-specific adapters (Room DB/DAOs/entities,
  DataStore, file-based backup, notification scheduling, WorkManager, system
  clock, UUID generation, future HTTP clients). Implements capability
  contracts; does not decide business policy (e.g. infra schedules a
  notification, application logic decides when one is needed).
- **Presentation**: Compose UI, ViewModels, navigation, UI state/events,
  presentation-specific models. Must not directly touch Room, DataStore,
  files, network clients, or Firebase. ViewModels hold `MutableStateFlow`
  privately and expose read-only `StateFlow`.

Model separation: never share one model type across layers. Keep explicit,
testable mappings between domain models, Room entities, backup/serialization
models, future API DTOs, and UI models.

Expected eventual modules (introduce incrementally, only with real
implementation — do not scaffold empty modules):
`:app`, `:domain`, `:application`, `:data`, `:infrastructure:database`,
`:infrastructure:preferences`, `:infrastructure:backup`,
`:presentation:designsystem`, `:feature:home`, `:feature:workout`,
`:feature:plans`, `:feature:history`, `:feature:recovery`, `:feature:settings`.

The domain layer must remain pure Kotlin.

Domain code must not depend on:

- Android framework classes
- Jetpack Compose
- Room
- Hilt
- networking libraries
- JSON or serialization frameworks
- database entities
- API DTOs

Presentation code must not directly access Room, DataStore, files, network
clients, Firebase, or other infrastructure.

Repository interfaces should express domain or application capabilities.
Concrete repository implementations may coordinate local and future remote
data sources.

Do not introduce a backend, authentication, synchronization, Firebase, or
DynamoDB unless explicitly requested.

## Data and compatibility

- The application is offline-first.
- Persist important workout actions immediately.
- Never use destructive production database migrations.
- Completed workout history must remain historically accurate.
- Training plans must be versioned rather than mutating past workout records.
- Backups must use an explicit, versioned schema.
- Use stable UUIDs for persisted domain records.
- A completed workout references the training-plan version/snapshot actually
  used; plan edits create a new version rather than mutating history.
- The rest timer's source of truth is an absolute end timestamp, not an
  in-memory countdown; the countdown is UI-only.
- Room is for structured workout data; DataStore is only for lightweight
  preferences/settings.
- Errors are represented at the layer that produces them (domain validation
  results, application-level result types, infrastructure exceptions
  translated before crossing inner boundaries); inner layers never return
  localized Android strings.

## Quality tooling

Planned/expected tooling (add only once actually configured in the project):
JUnit, kotlinx-coroutines-test, Turbine for Flow tests, Compose UI tests for
critical interactions, Android Lint, Detekt, and a consistent Kotlin
formatter. Business rules, progression logic, migrations, backup conversion,
and historical-data preservation require strong automated tests.

## Open decisions — do not silently finalize

Per `docs/TECHNICAL_DECISIONS.md`, these are intentionally unresolved; do not
decide them as a side effect of unrelated tasks — flag them for the user
instead:

- Exact initial Gradle module split
- Final Room entity schema
- Exact progression formulas and thresholds
- Navigation structure
- Backup file location and retention count
- Notification behavior when permission is denied
- Whether active workouts may span calendar days
- How exercise substitutions affect progression history
- Whether completed workouts may be manually corrected after completion
- Exact UI design system and visual identity

## Development workflow

Before modifying files:

1. Inspect the relevant existing implementation.
2. Explain assumptions and unresolved decisions.
3. Propose a focused implementation plan.
4. Do not make broad architectural changes unless explicitly approved.

While implementing:

- Prefer small, complete vertical slices over broad unfinished scaffolding.
- Do not add abstractions without a concrete current purpose.
- Avoid premature backend and synchronization infrastructure.
- Keep changes limited to the requested scope.
- Add or update tests for business rules and regressions.
- Do not silently replace existing architectural decisions.

After implementing:

- Run the most relevant Gradle build, test, and lint tasks available.
- Report failed checks honestly.
- Summarize every materially changed file.
- Mention any deferred work or technical debt introduced.
- Do not commit or push unless explicitly instructed.

## Git

- Never force-push unless explicitly instructed.
- Never discard unrelated working-tree changes.
- Use conventional commit messages when asked to create a commit.
