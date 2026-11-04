# suggest-today-checkin

Only today's check-in can hold back suggestions

**Complexity:** low. **Tier:** 2. Make suggestions trustworthy.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **A check-in of any age can hold back suggestions, and shows on the workout board as current** [A-6; design item 23] · S. One bad check-in followed by weeks without one blocks every exercise. Evidence: `GetWorkoutDayContext.kt` (`findLatest`, "regardless of age"); Home uses that day's entry only. Use today's check-in.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
