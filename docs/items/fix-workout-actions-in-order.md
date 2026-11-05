# fix-workout-actions-in-order

Workout actions run one after another, so a set logged during a restore can't be lost.

**Complexity:** low. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

Issue https://github.com/RodrigoFAbreu/repflow-android/issues/34, item 1, reproduced on the emulator with a restore held. While a restore runs, logging a set and then another set, or a set and then Finish, both report success, but the first set is gone afterwards and isn't in the safety copy either. The workout screen's `launchAction` doesn't run actions in order, so two queued behind the restore both read the old workout and the second overwrites the first.

The fix is to run workout actions one after another. Prove it with a test that fails today: two actions queued behind a held restore both keep their effect.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
