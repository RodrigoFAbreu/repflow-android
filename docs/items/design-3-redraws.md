# design-3-redraws

Redraw only what's needed: full Settings, the workout board's lost Add exercise, permission rows and sheets, filter-empty and chart-empty states, the before-restore snapshot board

**Complexity:** medium. **Tier:** Design passes: run by me in Claude Design, no app change, checked by a reviewer.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

**Needs first:** `design-1-wrong-claims`.

## Design findings

- **The Settings board is only half a screen** [D-9; app item 31] · M. "During a workout", "Your data", Erase and the footer exist only on an archive board (8c), and there's no back arrow. Evidence: 40e, `SettingsScreen.kt`.
- **The workout board lost `Add exercise` and `Up next`** [D-10] · S. A working feature could be read as removed under "the page wins". Evidence: 20a against `WorkoutBoard.kt:107-118` and old 4a.
- **The restore confirmation's final text** [A-2, D-43] · S. Draw this in place of design pass 1's always-on sentence. When a workout is running, and only then, this paragraph goes first: "A workout is running on this phone. Restoring deletes it, with every set you've logged." The existing body follows it unchanged: "Every exercise, plan, workout, recovery entry and recommendation on this device is replaced by the file's contents. Your settings stay as they are. This can't be undone." The text scrolls at large font sizes. Evidence: `fix-backup-all-workouts`, `BackupRestoreConfirmText` in `BackupFileActions.kt`.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**10. Which unaccounted boards get redrawn?** [D-41, O-15]
- *Recommendation:* **redraw only full Settings and the permission slot's second state, the two permission sheets, and the filter-empty and chart-empty states.** Move the rest to text in the spec sheet. The date sheet is optional, by tie-break. Boards 1b and 1c stay Future. Both agree on the date sheet; the rest is the design reviewer's list, and the app side has no objection.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
