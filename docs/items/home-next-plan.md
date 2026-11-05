# home-next-plan

Home offers the next plan in your list, not the one you just did

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Home offers the plan you just did, labelled "Today"** [A-7, D-23, D149; decision 1] · S. Evidence: `HomeViewModel.kt:244-245`. The fix waits for decision 1.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**1. Which plan should Home offer?** [A-7, D-13, D-23]
- *Today:* the plan just run. You accepted "least recently run" (A1) on 2026-10-09, two days before plan v4 existed.
- *Options:* (a) keep today's; (b) least recently run, never-run first: the optional `6 Sun` is offered every day until it's run; (c) next in the Plans list after the last one run: right on every normal day, and a skipped Sunday costs one tap.
- *Recommendation:* **(c).** Both agree. The notes on boards 10z, 10a and 10b need updating.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
