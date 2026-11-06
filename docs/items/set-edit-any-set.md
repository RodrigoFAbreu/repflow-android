# set-edit-any-set

Any set in the running workout can be corrected, not just the last one

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `set-edit-fields`.

## What's wrong

Only the most recently logged set can be corrected (`EditLastWorkoutSet`, design rule D31).

## What to do

The correction names the set by its id, so it always changes the set the user opened. That also closes issue #34's note about a correction changing the wrong set, if `safety-copy-polish` hasn't. `Undo last` stays as it is. Past workouts are `set-edit-past-workouts`.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
