# settings-alert-rows

Settings says when alerts are limited, with a way back after `Not now`

**Complexity:** medium. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-3-redraws`.

## App findings

- **Settings gives no hint when alerts are limited** [A-32] · S. No "Notifications are off" row, no exact-alerts row, and no "Off: no rest timer" subtitle. Saying `Not now` to exact alarms is final, with no way back in the app. Evidence: `strings.xml:651`, `ActiveWorkoutRoute.kt`, boards 40e, 8c.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
