# set-edit-fields

The set correction sheet also edits RPE, technique and pain

**Complexity:** low. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-5-workout-entry`.

## What's wrong

The correction sheet changes only weight, reps or seconds. A wrong RPE, technique or pain value can't be fixed. `EditLastWorkoutSetCommand` already carries all three, and `SetCorrectionSheet` passes them back unchanged, so this is mostly the sheet.

## Check

A corrected RPE or pain value must reach the next suggestion, the same as a value entered when logging.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
