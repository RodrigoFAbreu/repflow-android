# timed-set-alert

A timed set alerts you when it reaches its target, even with the phone locked

**Complexity:** low. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `timed-set-clock`.

## What to do

When the clock reaches the target, buzz and sound the same way the rest timer ends, following the same Settings. For a target range, alert at the bottom of the range. The clock keeps counting after the alert. Reuse the rest timer's alarm path; don't add a second one if that path can carry it.

`timed-set-clock` already changes the card past the target (`20o`, `20p`), so that isn't this item's. This item adds the buzz, the sound and the notification, and puts `· alerts at 0:30` back on the range's line before Start and while running, as `20p` draws it. That text was left out because no alert existed yet.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
