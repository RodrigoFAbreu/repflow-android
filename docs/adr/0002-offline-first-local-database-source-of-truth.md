# ADR 0002: Use an offline-first architecture with the local database as the source of truth

## Status

Accepted

## Context

RepFlow is primarily used during gym workouts, where network connectivity may
be unavailable, unstable, or inconvenient.

Core actions such as recording a set, updating repetitions, starting a rest
timer, substituting an exercise, and completing a workout must remain fast and
reliable regardless of connectivity.

The initial version of RepFlow is a personal Android application and does not
require:

- User accounts
- A cloud backend
- Multi-device synchronization
- Real-time collaboration
- Remote workout processing

Introducing a backend at the start would increase implementation complexity,
failure modes, development time, and operational requirements without providing
essential value for the MVP.

Workout data is also particularly sensitive to accidental loss. Important
actions should therefore be persisted immediately rather than held only in
memory until the end of a workout.

A future version may introduce accounts, cloud backups, synchronization, or
additional clients. The initial architecture should not prevent those
capabilities, but it should not implement them prematurely.

## Decision

RepFlow will use an offline-first architecture.

The local Room database will be the authoritative source of truth for
structured application data in the MVP.

The application must remain fully usable without network access for all core
workout functionality.

Important workout changes must be persisted locally immediately after a
meaningful user action.

Examples include:

- Starting a workout
- Starting or completing an exercise
- Recording a set
- Editing or deleting a set
- Skipping or substituting an exercise
- Changing the active workout position
- Starting or adjusting a rest timer
- Completing or abandoning a workout
- Recording recovery information

UI state may temporarily represent edits that have not yet been confirmed by
the user, but confirmed domain actions must not depend solely on in-memory
state.

Room will be used for structured and relational data such as:

- Exercises
- Training plans and plan versions
- Workout sessions
- Workout exercises
- Workout sets
- Recovery entries
- Progression recommendations
- Manual recommendation overrides

DataStore will be used only for lightweight application preferences and
settings, such as:

- Theme preferences
- Default units
- Notification preferences
- Onboarding completion
- Non-relational user settings

DataStore must not be used as the primary persistence mechanism for workout
history or other relational domain data.

The rest timer must store an absolute end timestamp. An in-memory countdown is
only a presentation detail and must not be treated as the authoritative timer
state.

The MVP will not introduce:

- A mandatory network connection
- Remote authentication
- Firebase
- Direct AWS access
- DynamoDB access from the Android client
- Background synchronization
- Conflict resolution
- Remote-first repositories

Repository contracts should nevertheless avoid exposing Room-specific types so
that future remote data sources can be introduced behind the existing
application and domain boundaries.

## Data flow

The normal data flow should be:

1. The user performs an action in the UI.
2. The ViewModel invokes an application use case.
3. The use case validates and applies the relevant business rules.
4. The repository persists the change locally.
5. The database emits the updated state.
6. The UI observes and renders that persisted state.

Where practical, the UI should observe database-backed `Flow` streams rather
than maintaining an independent long-lived copy of persisted state.

The application should not optimistically show an action as permanently saved
when persistence has failed.

## Failure handling

Local persistence failures must be surfaced appropriately.

The application should:

- Avoid silently losing confirmed workout data
- Keep the current user context when retrying is possible
- Show an understandable error
- Prevent duplicate writes when retrying operations
- Log enough technical information for diagnosis without exposing sensitive
  data

Critical workout actions should use transactions when multiple related records
must remain consistent.

For example, completing a workout may require atomically:

- Marking the workout as completed
- Storing its completion timestamp
- Finalizing active exercise state
- Saving generated recommendations
- Clearing active-workout references

## Future synchronization

A future synchronization feature may introduce a remote data source, but the
local database should remain the primary source used by the UI.

A possible future data flow is:

1. The UI reads from the local database.
2. User changes are written locally first.
3. A synchronization component transfers pending changes to a backend.
4. Remote updates are merged into the local database.
5. The UI receives the resulting local database state.

Future synchronization-related records should use stable identifiers and may
include metadata such as:

- UUID
- Creation timestamp
- Last modification timestamp
- Deletion timestamp
- Revision number
- Synchronization state
- Last synchronized revision

These fields should only be introduced when they serve an actual requirement.
The MVP must not build a speculative synchronization framework.

## Backup and restore

Offline-first storage makes backups important.

RepFlow will support a versioned backup format that is separate from the Room
database schema.

Backups must:

- Be exportable without a backend
- Include the domain data required to restore the application
- Declare an explicit backup schema version
- Be validated before restoration
- Avoid serializing Room entities directly as the public format
- Create a safety snapshot before replacing existing data
- Preserve several recent snapshots where practical

CSV exports may be provided for analysis and interoperability, but CSV is not
the authoritative full-fidelity backup format.

## Consequences

### Positive

- Workouts can be recorded without network access.
- User actions have low latency.
- The MVP has no backend hosting or operational dependency.
- Fewer network-related failure modes exist during workouts.
- Personal workout data remains on the device by default.
- Core development can focus on product behavior.
- A local-first UI works naturally with Room and Kotlin Flow.
- Interrupted workouts can be restored reliably.
- Future cloud features can be introduced behind repository boundaries.

### Negative

- Data initially exists only on the local device unless exported.
- Device loss may cause data loss before backup support is implemented.
- Multi-device use is not supported by the MVP.
- Future synchronization will require conflict-resolution decisions.
- Database migrations and backup compatibility become important
  responsibilities.
- Immediate persistence introduces additional error-handling requirements.

### Risks

- Treating in-memory ViewModel state as authoritative could cause data loss.
- Exposing Room entities through repository contracts could make future
  evolution difficult.
- Adding speculative synchronization metadata too early could complicate the
  data model.
- Destructive Room migrations could erase workout history.
- Backup formats tightly coupled to database entities could become impossible
  to evolve safely.

## Implementation guidance

- Use Room transactions for multi-record consistency.
- Use explicit database migrations.
- Never use destructive migrations in production.
- Observe persisted state through `Flow` where appropriate.
- Keep database entities separate from domain models.
- Keep backup models separate from Room entities.
- Persist active-workout progress after meaningful actions.
- Store timestamps using an unambiguous representation such as `Instant` or
  epoch milliseconds at persistence boundaries.
- Reconstruct timers from persisted timestamps.
- Do not add network dependencies until a remote capability is explicitly
  approved.

## Alternatives considered

### Remote-first backend

Rejected for the MVP because it would make core workout functionality dependent
on connectivity and introduce unnecessary infrastructure.

### Firebase as the initial source of truth

Rejected because the MVP does not require authentication, cloud sync, or
real-time remote data. It would also couple early application behavior to a
specific backend technology.

### Direct DynamoDB access from Android

Rejected because embedding long-lived cloud credentials in a mobile
application is unsafe and would tightly couple the client to infrastructure.

Any future DynamoDB integration must be accessed through an authenticated
backend API.

### In-memory workout state saved only at completion

Rejected because application termination, crashes, or accidental navigation
could lose an entire workout.

### DataStore for all persistence

Rejected because DataStore is not designed to model the relational and
query-heavy workout history required by RepFlow.

## Review triggers

Revisit this decision when one or more of the following becomes a concrete
requirement:

- Multi-device synchronization
- User accounts
- Shared training plans
- Remote coaching
- Web or iOS clients
- Automatic cloud backup
- Collaborative features
- Server-side recommendation processing

Adding any of these requirements does not automatically invalidate the
offline-first approach. The expected evolution is local-first synchronization,
not replacing the local database with a mandatory remote source.