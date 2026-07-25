# RepFlow — Product and Architecture

## Product vision

RepFlow is a native Android workout tracker designed to replace fragile
spreadsheet-based workout logging with a fast, reliable, offline-first
experience.

The initial product is personalized for its owner, but its architecture should
allow future evolution without coupling business rules to Android, Room, a
specific backend, or a particular UI implementation.

## Primary user and device

- Initial user: the repository owner
- Primary device: Samsung Galaxy S24 Ultra
- Primary activity: gym training combined with futsal
- Expected use: quick one-handed logging during workouts
- Network availability must not be required during a workout

## MVP goals

The MVP must support:

1. Exercise management
2. Training plan management
3. Starting and resuming workouts
4. Fast set logging
5. Rest timers
6. Workout history
7. Recovery and futsal context
8. Basic progression recommendations
9. Reliable local persistence
10. Versioned backup and restore

## Exercise library

Exercises may be built-in or user-created.

An exercise may define:

- Name
- Instructions or technique tips
- Tracking type
- Default load increment
- Default rest duration
- Alternatives or substitutions
- Archived status

Possible tracking types include:

- Weight and repetitions
- Repetitions only
- Duration
- Distance and duration
- Bodyweight with optional additional load

## Training plans

Training plans are versioned.

Editing a plan must not rewrite completed workout history.

A plan defines:

- Exercises and their order
- Target sets
- Repetition or duration ranges
- Rest periods
- Optional exercises
- Suggested substitutions
- Progression configuration

## Active workout

The active workout flow must prioritize speed and one-handed use.

The user must be able to:

- View the previous performance
- Record weight and repetitions
- Record RPE
- Optionally record technique quality
- Optionally record local pain
- Increase or decrease values through buttons
- Reuse the previous set
- Mark warm-up and working sets
- Add optional or extra sets
- Skip or substitute exercises
- Edit, delete, and undo relevant actions
- Pause and resume an interrupted workout

Important state must be persisted immediately after meaningful actions.

## Rest timer

The rest timer must:

- Store an absolute end timestamp
- Survive backgrounding and process recreation
- Show the correct remaining time when the app resumes
- Support vibration and notifications where permitted
- Avoid relying exclusively on an in-memory countdown

## Recovery and futsal context

Recovery inputs may include:

- Sleep quality
- Energy
- Leg DOMS
- Heel stiffness
- Pain while walking
- Heavy-leg sensation
- Futsal during the previous 24 hours
- Futsal expected during the following 24 hours

Futsal training load may initially be represented by:

duration in minutes × session RPE

## Progression recommendations

The initial recommendation engine should be deterministic and explainable.

Possible outcomes:

- Increase load
- Maintain load
- Reduce load
- Apply a recovery adjustment
- Insufficient data

Recommendations may consider:

- Completion of all working sets
- Target repetition range
- RPE
- Technique quality
- Pain
- Recovery status
- Proximity to futsal
- Configured load increment

Every recommendation should store or expose:

- The result
- The reason
- The policy or algorithm version
- Whether the user manually overrode it

Do not introduce an LLM or remote AI dependency into the MVP recommendation
engine.

## History

History must support viewing records by:

- Workout
- Exercise
- Training plan
- Date or period

Completed workout records should remain immutable from the perspective of
future training-plan edits.

## Backup and restore

The application must support:

- Complete versioned JSON backup
- Useful CSV exports
- Backup validation before restore
- A safety snapshot before restore, import, or risky migration
- Retaining multiple recent backup snapshots

The backup schema must be explicitly versioned and must not simply expose Room
entities as the public backup format.

## Architecture

RepFlow uses layered modular architecture.

### Domain

Contains pure business concepts and rules.

Examples:

- Exercise
- TrainingPlan
- WorkoutSession
- WorkoutSet
- RecoveryEntry
- ProgressionRecommendation

The domain layer has no Android or framework dependency.

### Application

Contains use cases and orchestration of domain capabilities.

Examples:

- StartWorkout
- RecordSet
- CompleteWorkout
- CalculateProgressionRecommendation
- RestoreBackup

### Data

Contains repository implementations, mappings, and coordination between data
sources.

### Infrastructure

Contains technology-specific adapters such as:

- Room database
- DataStore preferences
- File-based backup
- Notification scheduling
- Future HTTP clients

### Presentation

Contains:

- Jetpack Compose UI
- ViewModels
- Navigation
- UI state
- UI events
- Presentation-specific models

The UI follows unidirectional data flow:

UI event → ViewModel → use case → repository/domain → state → Compose UI

## Model separation

Do not use one model type for every layer.

Keep explicit boundaries between:

- Domain models
- Room entities
- Serialization or backup models
- Future API DTOs
- UI models where required

Mappings should be explicit and testable.

## Initial modules

Begin with the smallest structure that preserves the intended boundaries.

Expected eventual modules:

- `:app`
- `:domain`
- `:application`
- `:data`
- `:infrastructure:database`
- `:infrastructure:preferences`
- `:infrastructure:backup`
- `:presentation:designsystem`
- `:feature:home`
- `:feature:workout`
- `:feature:plans`
- `:feature:history`
- `:feature:recovery`
- `:feature:settings`

Do not create every module merely to produce empty scaffolding. Introduce module
boundaries incrementally through complete vertical slices.

## Quality

Use:

- JUnit for unit tests
- kotlinx-coroutines-test for coroutine tests
- Turbine where Flow testing benefits from it
- Compose UI tests for critical interactions
- Android Lint
- Detekt
- A consistent Kotlin formatter

Business rules, progression logic, migrations, backup conversion, and
historical-data preservation require strong automated tests.

## Deferred features

The following are outside the initial MVP unless explicitly reprioritized:

- User accounts
- Cloud backend
- Multi-device synchronization
- Web or iOS clients
- Social features
- Payments
- AI/LLM integration inside the application
- Smartwatch support
- Health Connect
- Exercise videos
- Nutrition tracking
