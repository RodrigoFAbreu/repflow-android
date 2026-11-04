# suggest-empty-state

The 'not enough data' screen matches the design, with `Keep the same load`

**Complexity:** low. **Tier:** 2. Make suggestions trustworthy.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `suggest-load-only`, `design-1-wrong-claims`.

## App findings

- **Recommendation screen when there's not enough data** [A-26, D157; design item 3] · S. Offers `Done` and `Pick another load`, where the design has only `Keep the same load`, which is buildable. Evidence: `ProgressionRecommendationScreen.kt:538-552`.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
