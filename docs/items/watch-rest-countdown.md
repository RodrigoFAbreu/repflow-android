# watch-rest-countdown

A Galaxy Watch app shows the rest countdown and buzzes when rest ends

**Complexity:** high. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `rest-notification-foreground`.

## What to do

A small Wear OS app for the owner's Galaxy Watch 6: while a workout runs, it shows the rest countdown and buzzes at the end. The phone stays the source of truth, and the workout works with no watch.

It's a new Gradle module, which is the concrete need that rule asks for, and the release must also publish the watch APK, signed with the same key and verified, within the CI budgets. Split the build and release setup into its own item if the plan needs two mechanisms. Update `PROJECT_BRIEF.md` and `TECHNICAL_DECISIONS.md`, which list smartwatch support as deferred.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
