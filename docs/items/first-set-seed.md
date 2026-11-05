# first-set-seed

A fixed target seeds the first set, and the first `+` starts at the bottom of the range

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `design-2-rules`.

## App findings

- **First `+` on empty reps starts at 1, not the bottom of the plan range** [A-16; design item 20] · S. Evidence: `RepFlowStepper.kt:241`, board 20m.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**8. Seed a first set from a plan target?** [A-13; reopens Q8]
- *Today:* never. The plan is only a caption, so a first 300 s hold needs the keypad. 24 of 51 rows are fixed.
- *Options:* (a) keep; (b) seed fixed targets only, because the number can't be wrong; (c) always the bottom of the range.
- *Recommendation:* **(b).** Both agree.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
