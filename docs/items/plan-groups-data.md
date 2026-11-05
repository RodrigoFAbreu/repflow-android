# plan-groups-data

Plans can be stored: a named group of workouts with a day for each

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `workout-names-on-screen`.

On screen, a **plan** is the group ("4x a week"), and a **workout** is the list of exercises ("Legs"). A past one is still a workout, shown in History. Today's "Plans" become Workouts. Names in the code and in backup files don't change.

## What to do

Store plans, one of them active, and each plan's workouts with their weekdays. The day belongs to the plan and not the workout, since Legs can be Friday in one plan and Thursday in another. A workout can be in several plans, and a plan can use a workout on more than one day.

Needs a version bump, an explicit `MIGRATION_x_y` and a migration test. Add plans to the versioned backup format: a newer backup keeps them, and an older backup restores with no plan set. With no plan set, the app behaves as it does today. Nothing on screen changes in this item.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
