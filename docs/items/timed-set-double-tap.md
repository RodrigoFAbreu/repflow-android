# timed-set-double-tap

A second quick tap on the clock's Start or Stop is ignored, so it can't store a wrong time

**Complexity:** low. Owner's request and test follow-up of 2026-11-06.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## What's wrong

From `timed-set-clock`'s review and test, item 1 of issue https://github.com/RodrigoFAbreu/repflow-android/issues/51. A second tap on `Stop` lands on `Start again`: the stopped time goes, a new clock runs, and the next `Stop` replaces the time. On the emulator, two taps 18 ms apart stored 17 s instead of 12 s. A double tap on `Start` lands on `Stop`, so the card goes back to `Start` and a running rest has ended.

## What to do

Ignore a second tap on these buttons for a moment after the first, using the elapsed clock so it can be tested without waiting. A deliberate `Stop`, then `Start again` a moment later, must still work. Test both double taps on the emulator, and read the stored seconds after `Log set`.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
