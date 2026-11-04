# timed-entry-mmss

Timed sets use m:ss everywhere, and the shared number pad opens on the current value

**Complexity:** medium. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Timed sets use raw seconds, and the number pad opens empty** [A-13, D-25, O-f4-3; decision 8; design item 20] · M. 9 of your 34 exercises are timed, and holds run to 300 s and 900 s. The design has an m:ss pad and shows durations as m:ss everywhere, and the pad opens on the current value, selected. Evidence: `WorkoutFocusEntry.kt:640-643`, `RepFlowNumericKeypad.kt` note. It's one shared pad, so the change is app-wide.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
