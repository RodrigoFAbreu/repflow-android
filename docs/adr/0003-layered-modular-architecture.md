# ADR 0003: Use a layered modular architecture

## Status

Accepted

## Context

RepFlow contains more than a simple collection of CRUD screens.

Its expected behavior includes:

- Versioned training plans
- Resumable active workouts
- Fast and reliable set logging
- Rest timers that survive process recreation
- Recovery and futsal context
- Deterministic progression recommendations
- Historical-data preservation
- Versioned backup and restore
- Future replacement or extension of infrastructure technologies

These capabilities contain business rules that should not be coupled directly
to:

- Android framework classes
- Jetpack Compose
- Room
- DataStore
- Hilt
- File APIs
- Notification APIs
- A future HTTP backend
- A particular serialization format

The project owner is familiar with traditional layered architectures through
Java, Quarkus, Spring Boot, Angular, and related application-development
patterns.

A strict ports-and-adapters or hexagonal implementation was considered, but it
would introduce terminology and indirection that are not currently necessary
for the project.

At the same time, placing all code inside a single Android application module
would make business logic difficult to test and increase coupling between UI,
persistence, and domain behavior.

The architecture should prioritize maintainability and technology
replaceability rather than hypothetical internet-scale requirements.

## Decision

RepFlow will use a layered modular architecture with the following conceptual
layers:

1. Domain
2. Application
3. Data
4. Infrastructure
5. Presentation

The layers express dependency direction and responsibilities.

They do not require every conceptual layer to immediately exist as a separate
Gradle module.

Modularization will be incremental. A new module should be introduced when it
contains meaningful behavior and enforces a useful dependency boundary.

## Dependency direction

Dependencies must point toward business logic rather than toward infrastructure.

The intended conceptual direction is:

```text
Presentation
    ↓
Application
    ↓
Domain

Data
    ↓
Application / Domain contracts

Infrastructure
    ↓
Data / Application contracts
````

The domain layer must not depend on outer layers.

The application layer may depend on domain concepts and contracts.

Presentation may depend on application use cases and presentation-specific
models.

Data and infrastructure provide concrete implementations of contracts required
by inner layers.

Circular dependencies between modules or layers are not allowed.

## Domain layer

The domain layer contains the business vocabulary, rules, and policies of
RepFlow.

Examples include:

* Exercise
* Exercise tracking type
* Training plan
* Training plan version
* Planned exercise
* Workout session
* Workout exercise
* Workout set
* Recovery entry
* Progression policy
* Progression recommendation
* Manual override

The domain layer must remain pure Kotlin.

It must not depend on:

* Android classes
* Jetpack Compose
* ViewModel
* Room annotations
* Hilt annotations
* Retrofit or HTTP clients
* Firebase
* JSON serializers
* Database entities
* API DTOs
* UI strings or resources

Domain behavior should be expressible and testable with ordinary JVM unit
tests.

Domain models should protect meaningful invariants where practical.

Examples include:

* A completed set cannot have a negative repetition count.
* A load cannot be negative.
* A repetition range must have a valid minimum and maximum.
* A completed workout must have a completion timestamp.
* A progression recommendation must include a policy version and reason.
* A training-plan version should not be silently mutated after being used by
  completed history.

The domain layer should not become a collection of passive data classes when
business behavior naturally belongs with a domain concept or policy.

However, it should also avoid excessive domain-driven-design ceremony where a
simple value object or pure function is sufficient.

## Application layer

The application layer contains use cases and orchestration.

Examples include:

* CreateExercise
* UpdateExercise
* StartWorkout
* ResumeWorkout
* RecordSet
* EditRecordedSet
* SubstituteWorkoutExercise
* CompleteWorkout
* RecordRecoveryEntry
* CalculateProgressionRecommendation
* ExportBackup
* ValidateBackup
* RestoreBackup

Application use cases coordinate:

* Domain rules
* Repository contracts
* Transactions or consistency boundaries
* Application-level validation
* Authorization in a possible future version
* Side effects represented through abstractions

The application layer should not know how Room, notifications, files, or
networking are implemented.

Use cases should expose meaningful application operations rather than
database-shaped CRUD operations.

For example:

```text
RecordSet
```

is preferable to:

```text
InsertWorkoutSetRow
```

The application layer may define contracts for capabilities that it requires,
such as:

* ExerciseRepository
* WorkoutRepository
* TrainingPlanRepository
* RecoveryRepository
* BackupStore
* Clock
* IdentifierGenerator
* NotificationScheduler

Contracts should be owned by the layer that consumes the capability whenever
practical.

## Data layer

The data layer coordinates persistence models, mappings, and repository
implementations.

Responsibilities may include:

* Implementing repository contracts
* Coordinating one or more data sources
* Mapping between persistence and domain models
* Applying local data access strategies
* Defining query-oriented data models where needed
* Preparing for a future local-and-remote coordination strategy

The data layer must not leak technology-specific types through inner-layer
contracts.

Repository APIs must not expose:

* Room entities
* Room DAO types
* Database cursors
* Retrofit response types
* Firebase snapshots
* DynamoDB records
* JSON-specific models

Mappings should be explicit and testable.

Separate types may exist for:

* Domain models
* Room entities
* Backup models
* Future API DTOs
* UI models

A single universal model shared by every layer is explicitly discouraged.

## Infrastructure layer

The infrastructure layer contains technology-specific adapters.

Examples include:

* Room database configuration
* Room DAOs and entities
* DataStore implementations
* File-based backup storage
* Android notification scheduling
* Haptic feedback
* WorkManager jobs
* System clocks
* UUID generation
* Future HTTP clients
* Future synchronization workers

Infrastructure implements capabilities required by inner layers but does not
define business policy.

For example:

* Infrastructure may schedule a rest-timer notification.
* Domain or application logic decides when a notification should be scheduled.
* Infrastructure may write a backup file.
* Application logic decides what constitutes a valid backup and when restoration
  may proceed.

Android-specific code should remain outside pure domain and application logic
unless a specific application contract necessarily represents Android
behavior.

## Presentation layer

The presentation layer contains:

* Jetpack Compose screens
* Reusable UI components
* ViewModels
* Navigation
* UI state
* UI events
* UI-specific formatting
* Presentation models
* Android resource integration

The presentation layer uses unidirectional data flow:

```text
UI event
    ↓
ViewModel
    ↓
Application use case
    ↓
Domain and repository operations
    ↓
Observable state
    ↓
Compose UI
```

Presentation code must not directly access:

* Room DAOs
* Database instances
* DataStore
* File storage
* HTTP clients
* Firebase
* DynamoDB
* Infrastructure implementations

ViewModels should coordinate UI-related state and use cases, but they should not
contain core progression rules or persistence logic.

Composable functions should primarily render state and emit user events.

Business decisions should not depend on the lifecycle of a Composable or
ViewModel.

## Feature modules

As the application grows, presentation and feature-specific coordination may be
organized into feature modules such as:

* `:feature:home`
* `:feature:workout`
* `:feature:plans`
* `:feature:history`
* `:feature:recovery`
* `:feature:settings`

Shared presentation elements may live in:

* `:presentation:designsystem`

Technology-specific implementations may live in modules such as:

* `:infrastructure:database`
* `:infrastructure:preferences`
* `:infrastructure:backup`

Core business layers may eventually become:

* `:domain`
* `:application`
* `:data`

The application entry point and dependency assembly remain in:

* `:app`

This is an intended direction rather than a requirement to create all modules
at project initialization.

## Incremental modularization

RepFlow will not create every possible Gradle module before implementing actual
features.

The preferred approach is:

1. Establish clear package-level boundaries.
2. Implement a small complete vertical slice.
3. Identify boundaries that provide actual value.
4. Extract meaningful code into modules.
5. Enforce dependencies through Gradle.

The first vertical slice is expected to cover the exercise library, including:

* Domain model
* Repository contract
* Use cases
* Room persistence
* Repository implementation
* ViewModel
* Compose UI
* Tests

This slice should validate the architecture before broader module expansion.

Empty modules and speculative abstractions should be avoided.

## Dependency injection

Hilt is the selected dependency-injection framework for Android integration.

Hilt may be used to assemble concrete implementations at the application edge.

Domain models and pure domain services should not require Hilt annotations.

Where possible:

* Constructors should express dependencies explicitly.
* Application and domain types should remain usable in plain unit tests.
* Framework-specific modules should bind interfaces to implementations.
* Dependency injection should assemble the architecture rather than define it.

Hilt must not be used as a service locator from arbitrary code.

## State and data exposure

Repositories may expose `Flow` where observable data is meaningful.

Use cases and ViewModels should expose only the state required by their
consumers.

Presentation should not receive entire database tables merely to derive a small
piece of UI state when a focused query or application operation is more
appropriate.

Mutable state should remain private to its owner.

For example, a ViewModel may expose:

```kotlin
val uiState: StateFlow<WorkoutUiState>
```

while retaining an internal:

```kotlin
private val _uiState: MutableStateFlow<WorkoutUiState>
```

Domain code should not depend on Android lifecycle-aware state containers.

## Error handling

Errors should be represented at the appropriate layer.

Examples:

* Domain validation failures belong to domain concepts or results.
* Use-case failures belong to application-level result types.
* Room exceptions belong to infrastructure and should be translated before
  crossing inner boundaries.
* User-facing error messages belong to presentation.

Inner layers should not return localized Android strings.

Exceptions should not be used as the only representation for expected business
outcomes.

## Testing strategy

The architecture should enable tests at multiple levels.

### Domain tests

Pure JVM tests for:

* Progression policies
* Validation
* Value objects
* Historical invariants
* Recovery adjustments

### Application tests

Use-case tests using fake or in-memory implementations for:

* Starting workouts
* Recording sets
* Completing sessions
* Applying recommendations
* Backup validation

### Data and infrastructure tests

Tests for:

* Room mappings
* DAO queries
* Repository implementations
* Database migrations
* Transactions
* Backup serialization
* Restore behavior

### Presentation tests

Tests for:

* ViewModel state transitions
* UI event handling
* Critical Compose interactions
* Resume-workout behavior
* Fast set-entry flows

Tests should validate behavior and boundaries rather than reproduce
implementation details unnecessarily.

## Consequences

### Positive

* Business rules can be tested without Android.
* Compose and Room can evolve independently from domain logic.
* Infrastructure technologies can be replaced behind contracts.
* The project structure matches the developer's existing mental model.
* The architecture supports future backend integration without requiring it
  now.
* Feature boundaries can be introduced incrementally.
* Progression and historical rules have clear ownership.
* AI coding agents receive clearer implementation constraints.

### Negative

* Explicit mappings create additional code.
* More concepts and files exist than in a single-module CRUD application.
* Layer boundaries require discipline.
* Small features may appear verbose when they cross several layers.
* Incremental modularization requires periodic architectural refactoring.
* Poorly designed repository contracts could still create unnecessary
  abstraction.

### Risks

* Creating interfaces for every class without a concrete need.
* Treating layers as folders while ignoring dependency direction.
* Moving business logic into ViewModels or repository implementations.
* Exposing Room entities to presentation.
* Creating all future modules as empty scaffolding.
* Introducing circular Gradle dependencies.
* Allowing use cases to become thin wrappers around DAO operations.
* Building a generic framework instead of the actual RepFlow product.

## Implementation guidance

* Prefer meaningful use cases over generic service classes.
* Keep domain code free of Android and persistence dependencies.
* Use explicit mappings between layer-specific models.
* Add abstractions when there is a capability boundary, not merely to increase
  indirection.
* Start with packages where separate modules would be premature.
* Extract modules when doing so enforces a meaningful dependency rule.
* Implement complete vertical slices.
* Keep UI state separate from domain entities.
* Do not place progression rules inside ViewModels.
* Do not inject DAOs directly into ViewModels.
* Do not serialize Room entities as backup contracts.
* Run dependency checks and tests after modularization changes.

## Alternatives considered

### Single Android application module with package organization

Rejected as the long-term architecture because Gradle cannot enforce package
boundaries and infrastructure dependencies could spread into business logic.

Package-level separation may still be used temporarily during incremental
modularization.

### Strict hexagonal or ports-and-adapters architecture

Not selected as the primary terminology because the additional conceptual
structure is not currently required and is less familiar to the project owner.

The selected architecture still adopts the useful principle that external
technologies should be accessed through inner-layer contracts.

### Clean Architecture with a large number of use-case classes and modules from

day one

Rejected because it risks excessive boilerplate, empty scaffolding, and
abstractions without current value.

### Feature-first architecture without shared domain and application boundaries

Rejected because progression, workout history, recovery, and training-plan
versioning cross multiple screens and require shared business rules.

### Kotlin Multiplatform shared domain

Deferred because RepFlow currently targets Android only. Introducing
multiplatform constraints would increase Gradle and dependency complexity
without serving an MVP requirement.

## Review triggers

Revisit this decision when:

* Module build times become problematic.
* A second platform is approved.
* A backend introduces substantial shared contracts.
* Feature teams or multiple contributors require stronger ownership boundaries.
* Existing module dependencies become circular or unclear.
* The cost of mappings and abstraction materially exceeds their benefit.
* A new capability does not fit cleanly within the current layers.

Any revision should preserve the core requirement that domain business rules
remain independent from Android UI, persistence, and backend technologies.

