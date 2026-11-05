# fix-workout-errors

A failed workout action shows a message, entry clears only on success, and `Retry` works

**Complexity:** low. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **A failed workout action shows no message, including a failed `Log set`** [A-1] · S. The screen's error field is never drawn, and RPE, pain and technique clear before the save result is known. Evidence: `ActiveWorkoutUiState.kt:27` is the only use; `WorkoutFocus.kt:191`. Show the generic error snackbar, and clear the entry only on success.
- **The workout's `Retry` button does nothing** [A-12] · S. Also change "Retry" to "Try again" in 5 strings. Evidence: `ActiveWorkoutRoute.kt:141`.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
