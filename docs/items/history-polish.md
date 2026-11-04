# history-polish

History: `Clear filters`, row and detail differences, exercise filter search

**Complexity:** low. **Tier:** 4. Match the approved design.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **History with no filter matches has no `Clear filters`** [A-28, D159] · S. Evidence: `HistoryScreen.kt`, `strings.xml:351`; only the old 7c board draws it. See decision 10.
- **History, Progress and Recovery history differences** [A-45] · M. History rows carry a set count, detail time is in minutes, there's no invalidated note, Invalidate is a direct icon, the exercise filter has no search, timed Progress shows seconds, and Recovery history rows aren't tappable. Evidence: `HistoryScreen.kt`, `ProgressUnits.kt`, boards 30f, 30g, 70b, 70d, 70e, 40d.

This finding spans more than one item. Do only the part this item's title names.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
