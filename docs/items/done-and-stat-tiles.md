# done-and-stat-tiles

Done screen tiles as drawn, and stat values no longer cut off at large font

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Done screen** [A-24, D-30, D156] · S. Shows Time, Sets, Trained where the design has Time, Volume, Sets, and keeps a "Saved to History" line the design removes. Evidence: `WorkoutDoneScreen.kt:151-163`, board 30a.
- **Stat tile values cut off at large font** [A-25, P3-F-1] · S. On Done, History detail and Progress. Evidence: `RepFlowStatTile.kt:89`; the design uses full-width rows above 1.3x (30a).

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
