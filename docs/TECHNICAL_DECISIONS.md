# RepFlow Technical Decisions

## Current platform

RepFlow is initially a native Android application.

Selected technologies:

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines and Flow
- ViewModel
- Navigation Compose
- Hilt
- Room
- DataStore
- Gradle Kotlin DSL

## Android SDK

Initial configuration:

- minSdk: 28
- compileSdk: 37
- targetSdk: 37

The minimum SDK is intentionally lower than the compile and target SDKs.

This allows RepFlow to use current Android APIs while retaining reasonable
compatibility without supporting very old Android versions.

## Offline-first

The local database is the source of truth for the MVP.

The application must not depend on network access during a workout.

Backend, accounts, and synchronization are deferred.

## Architecture style

RepFlow uses layered modular architecture with the following conceptual layers:

- Domain
- Application
- Data
- Infrastructure
- Presentation

This architecture was selected because it:

- Matches the developer's existing experience
- Keeps business rules independent from Android and storage technologies
- Allows infrastructure to change later
- Avoids unnecessary complexity from a stricter ports-and-adapters structure

## Incremental modularization

The project should not create every future Gradle module immediately.

Modules should be introduced when they contain meaningful implementation and
clear dependency boundaries.

The first vertical slice should prove the architecture before broad expansion.

## Domain purity

The domain layer is pure Kotlin.

It must not depend on:

- Android
- Compose
- Room
- Hilt
- Serialization libraries
- Networking libraries

## Persistence

Room is the selected local relational database.

DataStore is intended for lightweight preferences and settings, not structured
workout history.

Important workout actions must be persisted immediately.

## Repository design

Repository interfaces represent application or domain capabilities.

They must not expose Room entities, cursors, database-specific query types, or
future backend DTOs.

Concrete implementations may coordinate multiple data sources.

## Historical immutability

Completed workout records preserve the values and context needed to interpret
the workout historically.

Editing exercises or training plans must not reinterpret or mutate completed
sessions.

## Training-plan versioning

Training-plan edits produce a new version or equivalent immutable historical
representation.

A completed workout references the plan version or snapshot that was actually
used.

## Recommendation engine

The MVP recommendation engine is deterministic, local, explainable, and
versioned.

It must not depend on an LLM or remote AI service.

## Rest timer

The rest timer stores an absolute end timestamp.

An in-memory countdown is only a UI representation and must not be the source
of truth.

## Backup

Backups use an explicit versioned transfer schema.

Room entities must not be serialized directly as the public backup format.

Restore must validate the backup before replacing current data.

## Future backend

A future backend must be accessed through an API.

The Android application must never contain long-lived AWS credentials or
connect directly to DynamoDB.

## Dependency policy

Add libraries only when they solve a concrete requirement.

Avoid introducing:

- Backend SDKs
- Firebase
- Synchronization frameworks
- Kotlin Multiplatform
- Health Connect
- Smartwatch integrations

until those capabilities are explicitly prioritized.

## Open decisions

The following decisions are intentionally unresolved:

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

Agents must not silently finalize these decisions during unrelated tasks.