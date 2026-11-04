# remove-optional-switch

Remove the unused `Optional` switch from the plan editor

**Complexity:** low. **Tier:** 5. Clean-up from your decisions.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**13. The `Optional` switch** [N-5]
- *Today:* it's saved, but no workout code reads it, and none of the 51 rows use it.
- *Options:* (a) remove; (b) leave optional exercises out of progress and finish warnings.
- *Recommendation:* **(a).** Both agree.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
