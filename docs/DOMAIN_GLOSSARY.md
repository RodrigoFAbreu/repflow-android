# RepFlow Domain Glossary

## Exercise

A reusable definition of a physical exercise.

An Exercise is not a recorded performance. It describes what may be performed,
how it is tracked, and its default configuration.

## Exercise tracking type

Defines the values recorded for an exercise.

Milestone 1 supports:

- Weight and repetitions (`WEIGHT_AND_REPS`)
- Repetitions only (`REPS_ONLY`)
- Duration (`DURATION`)

Planned but deferred (not yet implemented):

- Distance and duration (`DISTANCE_AND_DURATION`)
- Bodyweight with optional added load (`BODYWEIGHT_WITH_OPTIONAL_LOAD`)

## Training plan

A reusable workout template containing ordered planned exercises and their
targets.

Training plans are versioned.

## Training plan version

An immutable version of a training plan used to preserve historical meaning.

Editing a plan creates or derives a new version rather than changing the plan
definition associated with completed workouts.

## Planned exercise

An exercise configured inside a training-plan version.

It may include:

- Order
- Target sets
- Repetition or duration range
- Rest duration
- Suggested alternatives
- Optional status
- Progression policy configuration

## Workout session

A concrete workout occurrence.

A session may be:

- Active
- Completed
- Abandoned

An active session must be resumable.

## Workout exercise

The occurrence of a planned or manually added exercise inside a workout
session.

It preserves enough information to remain historically meaningful even when
the source training plan changes later.

## Workout set

A recorded set performed during a workout.

Possible fields include:

- Load
- Repetitions
- Duration
- Distance
- RPE
- Technique quality
- Pain
- Warm-up or working-set classification
- Creation and modification timestamps

## Working set

A set that counts toward the planned training stimulus and progression
evaluation.

## Warm-up set

A preparation set that normally does not count toward progression evaluation.

## RPE

Rate of Perceived Exertion, represented on a scale used to estimate how close a
set was to maximum effort.

## Recovery entry

A record of the user's relevant recovery status for a date or workout context.

## Futsal load

An estimate of futsal training load.

The initial calculation may use:

duration in minutes × session RPE

## Progression recommendation

An explainable result suggesting whether the next workout should:

- Increase load
- Maintain load
- Reduce load
- Apply a recovery adjustment
- Wait for more data

## Progression policy

The deterministic and versioned set of rules used to calculate a progression
recommendation.

## Manual override

A user decision to perform something different from the system recommendation.

The original recommendation and the override should both be preserved.

## Completed history

Historical workout data that must remain accurate even when exercises,
training plans, or progression policies change.

## Backup schema version

The explicit version of the exported backup format.

It is independent from the Room database version and application version.