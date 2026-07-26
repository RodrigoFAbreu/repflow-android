# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project summary

RepFlow is an offline-first Android workout tracker.

Before implementing any feature:

- Preserve architecture.
- Preserve offline-first behavior.
- Avoid introducing unnecessary abstractions.

## Required reading before non-trivial work

Per `AGENTS.md`, always read first:
- `docs/ACTIVE_MILESTONE.md` — current state/checkpoint
- `docs/ROADMAP.md`

Read conditionally, only what's relevant to the task:
- `docs/adr/0003-layered-modular-architecture.md` — architecture/layer boundaries
- `docs/adr/0002-offline-first-local-database-source-of-truth.md` — Room/persistence/backup strategy
- `docs/DOMAIN_GLOSSARY.md` — domain terminology and business rules
- `docs/UX_FLOWS.md` — navigation/UI/interaction decisions
- `docs/TECHNICAL_DECISIONS.md` — toolchain/dependency decisions, including the "Open decisions" table — do not silently finalize these
- Per-milestone execution/reference docs in `docs/milestones/completed/` for historical context on a specific feature

## Hard rules (from `.github/copilot-instructions.md` and `.github/instructions/*.instructions.md`)

- Preserve dependency direction: Domain → Application → Data/Infrastructure → Presentation. Domain must stay pure Kotlin — no Android, AndroidX, Room, Hilt, `javax.inject`, or coroutines imports (enforced on `**/domain/**/*.kt`).
- Never expose Room entities or DAO types outside `infrastructure`/`data`. ViewModels call application use cases, never Room/DAOs/infrastructure directly.
- **Never use destructive Room migrations** (`fallbackToDestructiveMigration`). Any schema change past version 1 needs a version bump, an explicit `MIGRATION_x_y` in `RepFlowMigrations.kt`, and a migration test.
- Persist enum values by stable string, never ordinal.
- Do not swallow `CancellationException`; no broad `catch` around suspend work.
- Room is the local source of truth; the app must not depend on network access during a workout (offline-first, no backend/sync/accounts).
- Backup/restore uses an explicit versioned transfer schema (`BackupSnapshot`/`BackupJsonMapper`) — never serialize Room entities directly as the backup format.
- Do not add speculative abstractions, unused dependencies, or new Gradle modules without a concrete need — modularization is intentionally incremental (see ADR 0003).
- Commits are normally prohibited for agents; only make one if a milestone prompt explicitly authorizes a completion commit after all verification gates pass. Never push, merge, rebase, force-push, or open a PR.
- Don't touch unrelated working-tree changes.

## Commands

```bash
# Full build (compiles, runs default checks per module wiring)
./gradlew build

# Formatting check / auto-fix (Spotless + ktlint)
./gradlew spotlessCheck
./gradlew spotlessApply

# Static analysis (Detekt) — config at config/detekt/detekt.yml, maxIssues: 0
./gradlew detekt

# Android Lint
./gradlew lintDebug

# Unit tests (JVM, app/src/test)
./gradlew testDebugUnitTest

# Single test class / method
./gradlew testDebugUnitTest --tests "com.repflow.app.application.workout.RecordWorkoutSetTest"
./gradlew testDebugUnitTest --tests "com.repflow.app.application.workout.RecordWorkoutSetTest.records a set for an active session"

# Instrumented tests (requires a connected device/emulator; app/src/androidTest)
./gradlew connectedDebugAndroidTest
./gradlew assembleDebugAndroidTest   # compile only, no device needed

# Assemble debug APK
./gradlew assembleDebug
```

CI (`.github/workflows/ci.yml`) runs, in order: `spotlessCheck`, `detekt`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` (instrumented tests are compiled but not run in CI — no device available). Always run the narrowest relevant check for a change, per `AGENTS.md`; run the full suite before considering work done.

## Architecture

Layered architecture inside the single `:app` module (ADR 0003), organized as package-by-layer at the top level and package-by-feature underneath each layer:

```
app/src/main/kotlin/com/repflow/app/
  domain/{exercise,trainingplan,workout,recovery,progression,backup,common}
  application/{...same feature packages...}
  data/{...same feature packages...}
  infrastructure/{database/, di/, id/, time/}
  presentation/{...same feature packages.../navigation}
```

Test trees (`app/src/test`, `app/src/androidTest`) mirror this structure 1:1.

- **Domain** — pure Kotlin business vocabulary and rules (`Exercise`, `WorkoutSession`, `TrainingPlan`+versioning, `ProgressionRecommendation`, `RestTimer`, `BackupSnapshot`). No framework dependencies; testable with plain JVM tests. Repository *interfaces* consumed by the application layer are also defined here/in `application`, never in `data`.
- **Application** — use cases named after business operations, not CRUD (`RecordSet` not `InsertWorkoutSetRow`; `StartWorkout`, `RestoreBackup`, `CalculateProgressionRecommendation`). Orchestrates domain rules + repository contracts. Owns the repository interfaces implemented by `data`.
- **Data** — implements the repository interfaces (e.g. `application.workout.WorkoutRepository` → `data.workout.LocalWorkoutRepository`), maps Room entities to domain models. Never leaks Room types across the boundary.
- **Infrastructure** — Room database/DAOs/migrations (`infrastructure/database`), Hilt modules (`infrastructure/di`), system adapters (`infrastructure/time` clock, `infrastructure/id` UUID generation).
- **Presentation** — Compose screens, `@HiltViewModel`s, navigation. Strict unidirectional flow: `UI event → ViewModel → application use case → domain/repository → StateFlow<UiState> → Compose`. ViewModels combine one or more observing `Flow`s into a single `StateFlow` via `stateIn(WhileSubscribed(5000))`; UI states use sealed types (e.g. `ActiveWorkoutContent`) for mutually exclusive screen states rather than nullable/boolean flag piles.

The Domain→Application→Data→Presentation boundary is enforced by convention/code review only — there is no Gradle module boundary yet (deliberate, per ADR 0003's incremental-modularization principle; not an oversight).

### Dependency injection

Hilt, three `SingletonComponent` modules in `infrastructure/di`:
- `DatabaseModule` — provides the Room `RepFlowDatabase` singleton and all DAOs.
- `RepositoryModule` — `@Binds` each application-layer repository interface to its `data`-layer `Local*Repository` implementation.
- `SystemModule` — system-level adapters (clock, ID generation, IO dispatcher).

### Database

Room, single database (`repflow.db`), schema currently at version 6 with explicit `MIGRATION_1_2` … `MIGRATION_5_6` objects in `infrastructure/database/RepFlowMigrations.kt`. `ksp { arg("room.schemaLocation", ...) }` exports schema JSON to `app/schemas/`, used by instrumented migration tests. No destructive-migration fallback exists anywhere — this is a hard rule, not an oversight.

### Navigation

Single `NavHost` (`presentation/navigation/RepFlowNavHost.kt`) with plain string route constants (`RepFlowDestinations`) — not type-safe `@Serializable` routes, despite the library version supporting them (a known, tracked improvement — see `docs/improvements/IMPROVEMENT_ROADMAP.md` §4.1).

## Toolchain notes

- Kotlin compiled via AGP's built-in Kotlin compiler (no separate `org.jetbrains.kotlin.android` plugin). JVM bytecode target is Java 17, set once via `compileOptions` in `app/build.gradle.kts` — do not change without a recorded decision.
- Gradle daemon JDK is pinned separately to 21 (`gradle/gradle-daemon-jvm.properties`); CI also uses JDK 21.
- `kotlinx-serialization-core`/`-json` are force-resolved to 1.8.1 in `app/build.gradle.kts` to work around a conflicting `strictly 1.7.3` constraint from other AndroidX artifacts that otherwise breaks `room-testing`'s schema-bundle deserialization at runtime — see the comment above `configurations.all { resolutionStrategy { force(...) } }` before touching dependency versions.
- Detekt config: `config/detekt/detekt.yml`, `buildUponDefaultConfig = true`, **`maxIssues: 0`**. `LongParameterList`/`LongMethod` are exempted for `@Composable` functions (idiomatic Compose "state in, events out" functions are naturally longer/wider than typical functions).
- Use the version catalog (`gradle/libs.versions.toml`) for all dependencies; add a dependency only for a current concrete requirement (no speculative additions — Firebase, sync frameworks, KMP, Health Connect, smartwatch integration are explicitly out of scope until prioritized).
