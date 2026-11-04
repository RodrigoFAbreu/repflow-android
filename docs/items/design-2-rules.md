# design-2-rules

Fill in missing rules: pending set rows, fixed targets, first `+` on a timed set, context line with no check-in, archived exercises, notification text, loading shapes

**Complexity:** low. **Tier:** Design passes: run by me in Claude Design, no app change, checked by a reviewer.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

**Needs first:** `design-1-wrong-claims`.

## Design findings

- **Boards disagree on what a pending set row shows** [D-5; app item 10] · S. A carried-over rule says load from the seed; the focus board shows only "× 6–8". Recommendation: show the load. Evidence: 70j against 20c.
- **Progress range chips draw a check against the recorded rule** [D-4; app item 41] · S. Evidence: 30h, 70a, 70b; rule in 8a.
- **Notification text and icon differ across boards** [D-15; app item 9] · S. 20l against 7b. State which wins, or revert.
- **Empty-state glyph colour regressed** [D-16] · S. 50b uses a stronger colour than the recorded 35%, which the code uses.
- **Loading shapes** [D-17; app item 43] · S. "Exercise detail" doesn't exist, so rename it "History detail", and add a Progress shape.
- **History plan filter lost "No plan"** [D-18] · S. Evidence: 70d, `HistoryFilterChips.kt`.
- **First `+` on an empty timed set isn't defined** [D-44; app items 11, 30] · S. Recommendation: bottom of the plan range. Depends on decision 8.
- **No board shows a fixed target** [D-45; app item 29] · S. One line in the spec: min equals max reads "8 reps" or "5:00".
- **"Just logged" breaks inside the value at 1.3x** [D-46; app item 10] · S. Keep the value together, and apply it when building the app.
- **No rule for the workout context line with no check-in today** [D-52; app item 6] · S. No check-in, no readiness part.
- **One line for an archived exercise in a plan** [A-43; app item 36] · S. If the app fix goes ahead, add it to the plan editor states (10k).

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
