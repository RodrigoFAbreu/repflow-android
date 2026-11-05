# futsal-fix-entry

A futsal session can be removed, and a morning entry defaults to yesterday

**Complexity:** low. **Tier:** 2. Make suggestions trustworthy.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **A futsal session can't be removed** [A-41; decision 11] · S. Turning off "Played in last 24h" and saving leaves the session, which stays in Recovery history and can hold back suggestions. Evidence: `RecoveryFutsalViewModel.kt:127`; no delete in the recovery tables. Both reviewers recommend deleting it when the switch is turned off and saved.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**11. Futsal date and removal** [A-17, A-41]
- *Today:* a game logged the next morning is dated that morning, futsal can't be saved without all six scales, and a session can't be removed.
- *Options:* (a) leave; (b) default the date to yesterday in the morning, or let it be picked, and delete on switch-off; (c) also allow a futsal-only save, which goes against the one Save drawn.
- *Recommendation:* **(b).** Deleting a whole check-in isn't needed. Both agree.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
