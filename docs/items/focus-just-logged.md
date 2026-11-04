# focus-just-logged

Focus mode gets the 'Just logged' bar with Undo, the new row format, and the RPE panel closing after `Log set`

**Complexity:** medium. **Tier:** 3. Less friction during a workout.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

**Needs first:** `focus-entry-first`, `design-2-rules`.

## App findings

- **Focus mode (logging one exercise) still has the old layout** [A-10, A-31, D-26, D162; design item 5] · M. The set list sits above the steppers, where owner decision A4 puts entry first. There's no previous-exercise button, no "Just logged" bar with Undo, the old "Set 2: … kg x 8" row format remains, and the RPE panel stays open after `Log set`. Evidence: `WorkoutFocus.kt:130-173`, board 20c, rule 70j. The app's stacked steppers already match the design.

This finding spans more than one item. Do only the part this item's title names.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
