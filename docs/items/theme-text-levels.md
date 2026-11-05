# theme-text-levels

Text levels and control colours match the spec, including the light-theme pill

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Text levels and control colours differ from the design's spec** [D-28; decision 7; design item 10] · S. Evidence: `RepFlowColor.kt:42,55,153,156`. One theme change after decision 7, then re-run the role audit.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**7. Who wins on visual values, the pages or the code?** [D-3]
- *Today:* `DESIGN_SYSTEM.md:3-5` says code; your handoff says pages.
- *Recommendation:* **the pages say where it's going, and the code records what's built.** One line changes. Both agree. App item 12 follows.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
