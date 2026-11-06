# set-edit-past-workouts

Sets in past workouts can be corrected

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `set-edit-any-set`.

## What to do

Open a past workout in History and correct a set, using the same sheet as a running workout. History shows that it was edited.

## Check

A past edit can change later suggestions and Progress, so test both, and confirm a backup and restore keep the edit. Draw it first if the sheet needs anything new.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
