# fix-plan-editor-bugs

Plan editor: a new exercise joins the plan, archived exercises keep their name, and an unchanged save adds no version

**Complexity:** low. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **A new exercise made from the plan editor isn't added to the plan** [A-42] · S. Evidence: `RepFlowNavigationActions.kt`, board 50g.
- **A plan row with an archived exercise shows no name, and a timed one blocks saving** [A-43; design item 27] · S. Evidence: `TrainingPlanEditorViewModel` loads active exercises only. App reviewer only; needs device check.
- **Saving a plan with no changes adds a version** [A-48] · S. Evidence: `TrainingPlanEditorViewModel.submitEdit`. App reviewer only.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
