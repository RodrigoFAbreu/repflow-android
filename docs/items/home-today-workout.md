# home-today-workout

Home suggests the active plan's workout for today

**Complexity:** low. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `plan-groups-screen`, `home-next-plan`.

## What to do

With an active plan: Home offers today's workout. On a rest day, or with today's done, it says so and offers the next one with its day. Any other workout can still be started. With no active plan or no days set, Home keeps the `home-next-plan` behaviour.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
