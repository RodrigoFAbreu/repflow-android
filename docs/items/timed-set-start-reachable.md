# timed-set-start-reachable

The clock's Start and Stop stay reachable while a rest is running

**Complexity:** low. Owner's request and test follow-up of 2026-11-06.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

From `timed-set-clock`'s test, item 5 of issue https://github.com/RodrigoFAbreu/repflow-android/issues/51. While a rest runs, the rest strip can cover `Start` and `Stop`, so it takes a scroll to reach them. `Start` is what ends a rest, so this gets in the way mid-workout. At font 2.0 the clock card also sits below the fold.

## What to do

Keep the buttons reachable with a rest running and with several set rows, at font 1.0 and 2.0. Check the design's boards 20o, 20p and 20r first, and say if a board needs changing.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
