# readiness-sheet

Readiness sheet as drawn, with one scale order

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Readiness sheet ("How today's score was set")** [A-19, D151; design item 19] · S. Shows weights and "pulling the score down", where the design shows values as entered with a key. Its scale order differs from the check-in screen, and both differ from owner decision A12. Evidence: `ReadinessScore.kt`, `RecoveryScales.kt`, board 10e.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
