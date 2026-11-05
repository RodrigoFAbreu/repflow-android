# rest-notification-tap

Tapping the rest notification opens the workout, with the right icon and text

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Tapping the rest notification does nothing** [A-8, D-24, D-15; design item 12] · S. Evidence: no `setContentIntent` in the app; `RestTimerExpiryHandler.kt` also uses a system icon. Open the workout on tap, and align the icon and text with the design's notification board (20l).

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
