# docs-refresh

Bring the product docs up to date, including the pages-win rule

**Complexity:** low. **Tier:** 5. Clean-up from your decisions.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Product docs are out of date** [A-39, A-40, D-32] · S. Settings groups and several flows in `UX_FLOWS.md`; settled items still "Open" and DataStore in `TECHNICAL_DECISIONS.md`. Update `UX_FLOWS.md` as each item is built.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**7. Who wins on visual values, the pages or the code?** [D-3]
- *Today:* `DESIGN_SYSTEM.md:3-5` says code; your handoff says pages.
- *Recommendation:* **the pages say where it's going, and the code records what's built.** One line changes. Both agree. App item 12 follows.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
