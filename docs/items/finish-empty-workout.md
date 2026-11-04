# finish-empty-workout

Finishing with nothing logged asks first, instead of saving an empty workout

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Finishing with nothing logged saves an empty workout** [A-11] · S. Build the "Nothing logged yet" sheet. Evidence: `WorkoutSession.end` has no guard, board 20j.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
