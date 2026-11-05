# safety-copy-polish

Safety copy: a Restoring screen, and the Erase and Backup wording says what really happens.

**Complexity:** low. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

From `restore-safety-snapshot`'s review and test, issue https://github.com/RodrigoFAbreu/repflow-android/issues/34:

1. A restore now takes longer, since it saves a copy first, and nothing says to keep the app open. Build the design's "Restoring..." state (item 7). It blocks the whole app, including Back and any way in from a notification, until the restore ends, whether it succeeds or fails. Then no set can wait behind a restore, which closes two notes in the issue's comment of 2026-11-05: a waiting set lost on leaving the workout, and a correction changing the wrong set. It also narrows the double press and the set refused after Finish, both noted by `fix-workout-actions-in-order`.
2. The Erase dialog doesn't say it also deletes the safety copy, though board 40k says it does.
3. The note on the Backup screen says "RepFlow keeps a copy of your current data first" even on an empty phone, where none is made.

Page 4 of the design is the source for the wording. The other items on issue 34 stay there, including the failed action hidden by a queued success, since that one doesn't need a restore. When this item is done, add a note on the issue saying which notes it closed, and how the test showed it.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
