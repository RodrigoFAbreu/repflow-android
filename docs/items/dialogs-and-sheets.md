# dialogs-and-sheets

Leave, Finish, Abandon and Discard: wording and button style; drop `Irreversible`

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Leave and Finish sheets** [A-23, D155] · S. Wording differs, and the Finish sheet has an extra "Leave it running" action. Evidence: `WorkoutFinishSheet.kt:96-103`, boards 20j, 20k.
- **Drop the `Irreversible` label on the Erase card** [A-29, D160] · S. Settled by tie-break. Evidence: `strings.xml:660`, board 8c.
- **Dialog style** [A-30, D161] · S. Abandon uses plain text buttons, and Discard uses filled buttons, against "safe left, destructive outlined, never filled". Evidence: `AbandonWorkoutDialog.kt`, `TrainingPlanEditorScreen.kt:202`, board 20k.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
