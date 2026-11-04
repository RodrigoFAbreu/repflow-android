# suggest-no-rpe

Top of the range with no RPE gives Maintain with the reason shown, and the `NaN` text goes

**Complexity:** low. **Tier:** 2. Make suggestions trustworthy.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **With no RPE logged, a suggestion can read "Average RPE NaN", and a lifter at the top of the range stalls with no explanation** [A-5; design item 2] · S. The owner has decided: all sets at the top of the range with no RPE gives Maintain, with the reason shown ("log RPE to get an Increase"). Fix the `NaN` text, and write reasons in plain words the policy can back, with no readiness claim. Evidence: `ProgressionPolicyV1.kt:93,108`.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
