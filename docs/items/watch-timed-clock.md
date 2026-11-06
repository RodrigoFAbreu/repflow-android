# watch-timed-clock

The watch app shows the timed-set clock

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `watch-rest-countdown`, `timed-set-alert`.

## What to do

While a timed set runs, the watch shows the clock and buzzes at the target. Add Start and Stop from the watch only if the first version shows it's needed.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
