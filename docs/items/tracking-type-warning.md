# tracking-type-warning

Warn when changing the tracking type of an exercise in a plan or with history

**Complexity:** low. **Tier:** 5. Clean-up from your decisions.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**12. Changing an exercise's tracking type** [A-46]
- *Today:* allowed with no warning. A plan row can then show "Target: 8-12 reps" beside a seconds stepper, and Progress drops the old sessions.
- *Options:* leave, warn or block.
- *Recommendation:* **warn.** Both agree.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
