# fixed-target-text

Fixed targets read `8 reps` and `5:00`, not `8-8` and `300-300s`

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Fixed targets read as ranges: `8–8 reps`, `300-300s`** [A-14, O-f4-1; design item 21] · S. 24 of your 51 rows. Evidence: `WorkoutFocusSets.kt:161-162`.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
