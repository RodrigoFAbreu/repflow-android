# set-pain-holds-suggestion

Pain logged on a set holds back that exercise's next suggestion

**Complexity:** low. Owner's request and test follow-up of 2026-11-06.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

The owner decided this on 2026-11-06, from item 1 of issue https://github.com/RodrigoFAbreu/repflow-android/issues/47. No suggestion reads the pain on a set, logged or corrected. Only the check-in's pain while walking can hold a suggestion back, so a painful set can still be followed by a suggestion to add weight. The plan's own rule is that sharp pain means reduce or stop.

## What to do

When a working set in the workout has pain at or above the threshold the check-in already uses (`PAIN_THRESHOLD` in `ProgressionPolicyV1`), the exercise's next suggestion doesn't add load, and its reason says why. Other exercises aren't affected. A corrected pain value counts the same as a logged one. Say in the plan if a different threshold fits a set's scale better.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
