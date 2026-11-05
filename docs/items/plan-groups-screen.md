# plan-groups-screen

Create a plan, choose its workouts and days, and choose the active plan

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `plan-groups-data`.

On screen, a **plan** is the group ("4x a week"), and a **workout** is the list of exercises ("Legs"). A past one is still a workout, shown in History. Today's "Plans" become Workouts. Names in the code and in backup files don't change.

## What to do

Build the screens drawn in `design-6-plans-and-days`. Say when a workout is used by more than one plan, since an edit changes it in all of them. History isn't affected: workouts are already versioned.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
