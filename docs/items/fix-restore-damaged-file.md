# fix-restore-damaged-file

Restoring a damaged backup file shows an error instead of closing the app.

**Complexity:** low. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

Issue https://github.com/RodrigoFAbreu/repflow-android/issues/29, from `fix-backup-all-workouts`' review. Restoring a damaged or hand-edited file with a zero-length rest timer or a blank workout id closes the app: `BackupJsonMapper.parse` catches only `JSONException`, while `RestTimer` and `WorkoutSessionId` throw `IllegalArgumentException`. No data is lost, because the file is parsed before anything is cleared. Backups of a running workout now carry a rest timer, so this path is slightly more reachable.

The same issue also records a dead end: if one saved workout is unreadable, every export fails and only `Erase all data` clears it. Nobody found a normal way to reach it. Check the export side only if the fix is one mechanism; otherwise leave it on the issue.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
