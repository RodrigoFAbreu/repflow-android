# workout-names-on-screen

On screen, today's plans are called workouts

**Complexity:** low. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-6-plans-and-days`.

On screen, a **plan** is the group ("4x a week"), and a **workout** is the list of exercises ("Legs"). A past one is still a workout, shown in History. Today's "Plans" become Workouts. Names in the code and in backup files don't change.

## What to do

Change the wording only: `strings.xml` and any text built in code. No behaviour, data or backup change. Do this before plans exist, so "plan" has one meaning when they arrive.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
