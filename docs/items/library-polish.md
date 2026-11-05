# library-polish

Exercise library title and create label, and no `Create` on an exact match

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Exercise library title and create label** [A-27, D-30, D158] · S. "Exercises" and "Add exercise", against "Exercise library" and "Create exercise". The bottom bar itself is built. Evidence: `strings.xml:4,9`, board 70g.
- **Library search offers `Create` even on an exact match** [A-44] · S. Saving then fails with "already exists". Evidence: `ExerciseListScreen.kt`, boards 50f, 70g.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
