# overnight-workout

After 4 h a workout shows its start time, and Finish ends at the last set

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `finish-empty-workout`.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**4. Workout left running overnight?** [N-3, D-12, A8; "owner to confirm" on board 10d]
- *Today:* no rule, so a forgotten workout records many hours, and it can't be edited afterwards. A restored running session (app item 2) meets the same case.
- *Options:* (a) no rule; (b) after 4 h show "Started yesterday 18:02", Finish ends at the last set, and with no sets use the "Nothing logged yet" sheet; (c) auto-finish.
- *Recommendation:* **(b).** Sessions of 60 minutes or less won't misfire. Both agree.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
