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

## GitHub Actions entry points

CI runs Test for pull requests and main pushes, then calls Build and publish
after successful main validation. CI, Test and Build and publish each support
manual dispatch. Test and Build and publish also support reusable calls.
CI dispatches from main validate first and then publish. Direct Build and publish
dispatch performs publication work on main. The existing
Controller release transaction and APK signing policy apply. Manual publication
uses the repository's REPFLOW_DEBUG_KEYSTORE_B64 Secret.

PR titles use the shared action-workflows title workflow at main together with
this project's release classifier. Android validation retains its Gradle checks,
unit tests, APK builds, failure reports and existing time budgets.
