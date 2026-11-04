# ci-pipeline

One orchestrator workflow calling reusable ones, with time budgets: under 20 min for a pull request and 25 min for a push to main

**Complexity:** low. **Tier:** Setup: small chores, one PR each, no milestone.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
