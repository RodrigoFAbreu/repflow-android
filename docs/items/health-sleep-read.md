# health-sleep-read

The check-in shows last night's sleep from Samsung Health

**Complexity:** medium. Owner's request of 2026-11-05.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `health-connect-probe`.

## What to do

Read sleep through Health Connect, with the user's permission, and show it in the morning check-in, or pre-fill what it can. Read-only. It's optional: refusing the permission, or having no data, leaves the check-in as it is today. It doesn't change suggestions yet.

Health Connect runs on the phone, so the app stays usable offline. Update `PROJECT_BRIEF.md` and `TECHNICAL_DECISIONS.md`, which list it as deferred. Further metrics are separate items, chosen from what the probe finds.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `feat: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
