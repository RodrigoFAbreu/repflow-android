# restore-safety-snapshot

One automatic snapshot before a restore, restorable from Backup

**Complexity:** medium. **Tier:** 1. Don't lose data, don't hide failures.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `fix-backup-all-workouts`, `design-3-redraws`.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**5. Safety before a restore, and a backup reminder?** [N-4; reopens your earlier decision Q5]
- *Today:* restore can't be undone, you do use it to load plans, and a lost phone loses everything since the last manual export. `PROJECT_BRIEF.md:86-88` is stale.
- *Options:* (a) keep Q5 and fix the brief; (b) one automatic snapshot before a restore; (c) also a reminder when the last backup is old.
- *Recommendation:* **(b) and (c).** Needs a new board. Both agree.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
