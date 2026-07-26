# RepFlow — Project Brief

## Product vision

RepFlow is a native Android workout tracker designed to replace fragile
spreadsheet-based workout logging with a fast, reliable, offline-first
experience.

The initial product is personalized for its owner, but its architecture allows
future evolution without coupling business rules to Android, Room, a specific
backend, or a particular UI implementation.

## Primary user and device

- Initial user: the repository owner
- Primary device: Samsung Galaxy S24 Ultra
- Primary activity: gym training combined with futsal
- Expected use: quick one-handed logging during workouts
- Network availability must not be required during a workout

## MVP goals

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

An exercise may define name, instructions, tracking type, default load
increment, default rest duration, alternatives, and archived status.

Milestone 1 tracking types: `WEIGHT_AND_REPS`, `REPS_ONLY`, `DURATION`.

Deferred tracking types (planned): `DISTANCE_AND_DURATION`,
`BODYWEIGHT_WITH_OPTIONAL_LOAD`.

## Training plans

Training plans are versioned. Editing a plan must not rewrite completed
workout history. A plan defines exercises, order, target sets, rep/duration
ranges, rest periods, optional exercises, substitutions, and progression
configuration.

## Active workout

Must prioritize speed and one-handed use. The user can record weight,
reps, RPE, technique quality, pain, warm-up and working sets, skip or
substitute exercises, and undo actions. Important state is persisted
immediately after each meaningful action.

## Rest timer

Stores an absolute end timestamp. Survives backgrounding and process
recreation. Supports vibration and notifications where permitted.

## Recovery and futsal context

Recovery inputs: sleep quality, energy, leg DOMS, heel stiffness, pain
while walking, heavy legs, futsal context (previous and upcoming 24 h).

Futsal training load: duration × session RPE.

## Progression recommendations

Deterministic, local, and explainable. Considers sets completed, rep range,
RPE, technique, pain, recovery, futsal proximity, and load increment.
Every recommendation stores result, reason, policy version, and manual
override flag. No LLM or remote AI dependency.

## History

View records by workout, exercise, training plan, date or period.
Completed workout records are immutable from future plan edits.

## Backup and restore

Complete versioned JSON backup, CSV exports, backup validation before
restore, safety snapshot before restore, and multiple recent backup
snapshots retained. Backup schema is explicitly versioned and does not
expose Room entities directly.

## Architecture and technical decisions

See [ADR 0003 — Layered modular architecture](adr/0003-layered-modular-architecture.md)
and [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md).

## Deferred features

Outside the initial MVP unless explicitly reprioritized:

- User accounts, cloud backend, multi-device synchronization
- Web or iOS clients, social features, payments
- AI/LLM integration, smartwatch support, Health Connect
- Exercise videos, nutrition tracking
