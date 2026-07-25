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

## Architecture

Preserve these conceptual layers:

- Domain
- Application
- Data
- Infrastructure
- Presentation

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
