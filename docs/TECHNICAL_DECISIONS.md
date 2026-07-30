# RepFlow Technical Decisions

## Platform and stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Async | Coroutines + Flow |
| ViewModel | AndroidX ViewModel |
| Navigation | Navigation Compose |
| DI | Hilt |
| Persistence | Room + DataStore |
| Build | Gradle Kotlin DSL |

## Android SDK

- minSdk: 28 / compileSdk: 37 / targetSdk: 37
- Minimum SDK is lower than compile/target to retain reasonable compatibility.

## Architecture

Layered modular architecture (Domain → Application → Data/Infrastructure →
Presentation). See [ADR 0003](adr/0003-layered-modular-architecture.md).

## Persistence

Room is the local source of truth for the MVP. DataStore for lightweight
preferences only. See [ADR 0002](adr/0002-offline-first-local-database-source-of-truth.md).

## JVM alignment

Local runtime: Android Studio bundled JBR 21. CI: JDK 21.
Java source/target compatibility: 17. Kotlin JVM bytecode target: 17 (explicit).

## Offline-first

Application must not depend on network access during a workout. Backend,
accounts, and synchronization are deferred.

## Incremental modularization

Introduce Gradle module boundaries through complete vertical slices, not
upfront scaffolding. Milestone 1 keeps all code in `:app`.

## Domain purity

Domain layer is pure Kotlin. No Android, Compose, Room, Hilt, serialization,
or networking dependencies.

## Repository design

Interfaces represent application/domain capabilities. Must not expose Room
entities, cursors, or future backend DTOs.

## Historical immutability

Completed workout records are immutable from future plan or exercise edits.

## Training-plan versioning

Plan edits produce a new version. Completed workouts reference the plan
version or snapshot actually used.

## Recommendation engine

Deterministic, local, explainable, versioned. No LLM or remote AI.

## Rest timer

Stores an absolute end timestamp. In-memory countdown is UI only.

## Backup

Explicit versioned transfer schema. Room entities are never serialized
directly as the public backup format. Restore validates before replacing data.

`BackupSnapshot.CURRENT_SCHEMA_VERSION` only needs a bump, and
`BackupJsonMapper` only needs a null-safe optional read (no
version-branching logic), for additive nullable fields whose absence
already has valid null/default semantics (Milestone 8, CP14: five such
fields landed this way). This convention is **not** sufficient on its own
for a field rename, a field removal, a field becoming required, or any
change where a missing value has no valid meaning - those need explicit
version-aware conversion in the mapper, decided case by case when they
come up.

## Future backend

Must be accessed through an API. Never embed long-lived AWS credentials or
connect directly to DynamoDB from the Android client.

## Dependency policy

Add libraries only when they solve a concrete requirement. Avoid Firebase,
synchronization frameworks, KMP, Health Connect, and smartwatch integrations
until explicitly prioritized.

## Open decisions

| Decision | Status |
|---|---|
| Exact initial Gradle module split | Open |
| Final Room entity schema | Open |
| Exact progression formulas and thresholds | Open |
| Navigation structure | Open (D-1 approved for M1 only) |
| Backup file location and retention count | Open |
| Notification behavior when permission is denied | Open |
| Whether active workouts may span calendar days | Open |
| How substitutions affect progression history | Open |
| Whether completed workouts may be manually corrected | Open |
| Exact UI design system and visual identity | Open |

Agents must not silently finalize these decisions during unrelated tasks.
