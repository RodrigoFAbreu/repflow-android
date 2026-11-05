# timed-set-clock

A timed set has a clock: Start counts up, and Stop fills in the time

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-5-workout-entry`, `timed-entry-mmss`.

## What to do

A timed exercise gets a Start button. The clock counts up in m:ss, with the target in view. Stop fills the time into the entry, where it can still be changed before `Log set`. Typing a time by hand keeps working.

The clock keeps a start time, not a running count, the way the rest timer keeps its end time, so locking the phone or leaving the app doesn't lose it. The alert at the target is `timed-set-alert`.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
