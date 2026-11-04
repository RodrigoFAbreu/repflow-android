# chore-backlog-file

Move the backlog into `docs/backlog.yaml` with a generated roadmap page, run milestones on branches, and publish a GitHub Release with the APK for every `feat:` or `fix:` merge

**Complexity:** low. **Tier:** Setup: small chores, one PR each, no milestone.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**14. Deferred features** [N-6]
- *Today:* the current pages draw none of them. An exercise added by mistake can't be removed from a workout.
- *Recommendation:* **retire supersets, active plan and days, and lb units. Keep "remove from this workout" (needs a small board) and notes as candidates.** Both agree.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
