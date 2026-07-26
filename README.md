# RepFlow

RepFlow is a native, offline-first Android workout tracker focused on fast set
logging, training-plan management, recovery context, and explainable
progressive-overload recommendations.

## Status

Milestone 1 — Exercise Library in progress. See [Active milestone](docs/ACTIVE_MILESTONE.md).

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Hilt
- Coroutines and Flow
- Gradle Kotlin DSL

## Documentation

- [Project brief](docs/PROJECT_BRIEF.md)
- [UX flows](docs/UX_FLOWS.md)
- [Domain glossary](docs/DOMAIN_GLOSSARY.md)
- [Technical decisions](docs/TECHNICAL_DECISIONS.md)
- [Roadmap](docs/ROADMAP.md)
- [Agent instructions](AGENTS.md)

## Build

```bash
./gradlew build
```

## Current Android configuration
minSdk 28
compileSdk 37
targetSdk 37

## Architecture decision records

- [ADR 0001: Native Android with Kotlin and Jetpack Compose](docs/adr/0001-stack.md)
- [ADR 0002: Offline-first, local database as source of truth](docs/adr/0002-offline-first-local-database-source-of-truth.md)
- [ADR 0003: Layered modular architecture](docs/adr/0003-layered-modular-architecture.md)
