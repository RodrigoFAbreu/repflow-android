# plan-editor-rows

Plan editor rows: drag handle, menu, rest format and presets

**Complexity:** medium. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Plan editor rows** [A-21, D-30, D153] · M. No drag handle or ⋮, rest shown as `90s rest` with no source, a rest stepper, and presets of 60 to 180 s that differ from the design's 0:45 to 3:00. Evidence: `TrainingPlanEditorRows.kt`, `TrainingPlanEditorRowModel.kt`, boards 10i, 70f.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
