# RepFlow UX Flows

## UX principles

RepFlow is primarily used during workouts, often while standing, tired, and
holding the phone with one hand.

The UI should prioritize:

- Fast interaction
- Large touch targets
- Minimal typing
- Clear current-workout context
- Immediate feedback
- Safe recovery from interruptions
- Avoiding accidental destructive actions

Important workout actions should normally require no more than two or three
taps.

## Home

The home screen should show:

1. Recommended workout for today
2. Resume active workout, when one exists
3. Recent workout summary
4. Relevant recovery or futsal context
5. Quick access to plans, history, and settings

The active workout action must have the strongest visual priority.

## Start workout

The user selects or accepts a training plan.

Before starting, RepFlow may show:

- Planned exercises
- Expected duration
- Recovery warning
- Recent futsal context
- Suggested adjustments

Starting a workout creates and immediately persists an active workout session.

## Active workout

The primary screen focuses on one exercise at a time.

It should show:

- Exercise name
- Technique notes
- Previous performance
- Current set number
- Target sets and repetition range
- Suggested load
- Current rest status
- Completed sets

The user should be able to:

- Increase or decrease load
- Increase or decrease repetitions
- Reuse the previous set
- Record RPE
- Record optional technique quality
- Record optional pain
- Save the set
- Mark a warm-up set
- Add an extra set
- Skip or substitute the exercise

Saving a set should immediately persist the result.

## Rest timer

After saving a working set:

1. Start the configured rest timer.
2. Store the absolute timer end timestamp.
3. Display the remaining duration.
4. Allow adding or removing time.
5. Allow skipping the timer.
6. Notify the user when rest ends, where permissions allow.

Returning to the application must reconstruct the timer from the stored end
timestamp.

## Interrupted workout

When the application is reopened and an incomplete workout exists, the home
screen should prominently offer:

- Resume workout
- End workout
- Discard workout, with confirmation

The workout must resume at the correct exercise, set, values, and rest state.

## Complete workout

On completion, show:

- Duration
- Exercises completed
- Total working sets
- Relevant progression recommendations
- Recovery or pain warnings
- Optional workout notes

The completed workout becomes historical data and must not be changed by later
training-plan edits.

## Recovery entry

Recovery input should be quick and use scales or selectable options where
possible.

Possible fields:

- Sleep quality
- Energy
- Leg DOMS
- Heel stiffness
- Pain while walking
- Heavy legs
- Futsal in previous 24 hours
- Futsal expected in next 24 hours

Free text should remain optional.

## Error and destructive actions

Destructive actions should:

- Be clearly labeled
- Require confirmation when data loss is possible
- Offer undo when practical
- Never silently remove completed workout history