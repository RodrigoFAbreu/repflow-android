# rest-notification-foreground

No heads-up notification with the app open, and a stale one clears

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `rest-notification-tap`.

## App findings

- **Rest notification appears with the app open, and an old one doesn't clear** [A-9, D-24; decision 6] · S. Evidence: no foreground check in `RestTimerExpiryHandler.kt`; the cancel call runs only on finish, abandon, erase and restore. Needs device check.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**6. Sound when rest ends with the app open?** [D-14, A-9]
- *Today:* boards 20b and 20l say sound, buzz and strip, but the app plays no in-app sound and has no switch. The notification sound in the background needs a device check.
- *Options:* (a) buzz and strip in front, notification sound in background; (b) add an in-app sound with a switch.
- *Recommendation:* **(a).** Both agree. Fix the two boards.

## Keep the watch alert

The owner's Galaxy Watch 6 can show phone notifications. Whatever this item changes, a rest ending while the phone is locked or in a pocket must still post the notification, so the watch buzzes. Only skip the heads-up when the app is really on screen.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
