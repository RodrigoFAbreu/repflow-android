# fix-workout-error-followups

Workout errors: no stale message after a retry, no crash on a failed read, and Try again restores the picker.

**Complexity:** low. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

Issue https://github.com/RodrigoFAbreu/repflow-android/issues/26, from `fix-workout-errors`' review:

1. A stale error message can outlive a successful retry, so someone could log a duplicate. `launchAction`'s success path leaves `error` alone.
2. A failed read in `RecordWorkoutSet` can crash the app. `LocalWorkoutRepository.findById` doesn't translate SQLite errors, and `launchAction` has no catch.
3. `Try again` brings back the session, not the exercise picker or the last-session seeds. `availableExercises` ends in `.catch { emit(emptyList()) }`, and `refreshLastPerformances()` runs only at creation and after a finish.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
