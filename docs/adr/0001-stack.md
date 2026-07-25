# ADR 0001: Use native Android with Kotlin and Jetpack Compose

## Status

Accepted

## Context

RepFlow is initially intended for a Samsung Galaxy S24 Ultra and is being built
as a personal Android workout tracker.

The application requires strong Android integration, reliable offline
persistence, notifications, background behavior, and a fast one-handed UI.

## Decision

Use native Android development with Kotlin, Jetpack Compose, and Material 3.

## Consequences

Positive:

- Direct access to Android APIs
- Strong tooling in Android Studio
- Good Compose integration
- Straightforward Room and notification support
- No cross-platform abstraction cost

Negative:

- No direct iOS client
- Android-specific presentation and infrastructure code
- A future iOS application would require a separate client or later shared
  domain strategy