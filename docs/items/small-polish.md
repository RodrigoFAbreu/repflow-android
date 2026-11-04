# small-polish

Rest strip spacing, Home `Log` flicker, Progress check marks, small visual leftovers

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Rest strip spacing** [A-34, O1] · S. Buttons hug the left at normal font, and the done strip lacks extra bottom padding at 2.0x. Evidence: `ActiveWorkoutScreen.kt:514-531`, `RestDoneStrip.kt`, boards 20a, 20b.
- **Home `Log` link flickers while readiness loads** [A-35, O2] · S. Evidence: `HomeRecoveryCard.kt:74`.
- **Small visual leftovers** [A-36] · S. Light-theme selected pill is still transparent, so roadmap item 8.3 is now unblocked; type notes in focus mode are still shown; the Recovery title differs. Evidence: `RepFlowTag.kt:154-157`.
- **Progress metric buttons draw a check** [A-49, D-4; design item 9] · S. Against the rule of tint only for on-screen choices. Evidence: `ProgressScreen.kt:210-214`.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
