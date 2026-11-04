# design-4-spec

Make the spec sheet the single source: move rules out of the archive, add the board map, fix contrast and faint outlines, run the colour-blind check, archive old boards

**Complexity:** low. **Tier:** Design passes: run by me in Claude Design, no app change, checked by a reviewer.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

**Needs first:** `design-2-rules`.

## Design findings

- **The spec sheet says its values are the app's own, and they aren't** [D-2; app item 12; decision 7] · S. The kit's radii also disagree with the sheet. Evidence: board 60a, `RepFlowColor.kt`.
- **Add the full old-to-new board map** [D-40] · S. The superseded board (60d) is incomplete. The map is in `round1-design.md`, Ask 1. Repoint new milestone input only, and leave history in code comments alone.
- **Invalidated rows fail contrast in light theme** [D-19] · S. Computed 3.62:1 and 2.77:1 on 30f, 2.96:1 on the "In plan" rows in 50g. Design reviewer's figures only.
- **Move rules that live only in archive boards into the spec sheet** [D-35] · S. Three earlier answers were lost in the redraw (D-4, D-5, D-16). Lets boards 8a and 7a retire.
- **Faint input and off-state outlines** [D-33] · S. Computed 1.45:1 to 1.76:1. In a bright gym, field edges and off switches are easier to find if stronger. Design reviewer's figures only.
- **Colour-blind checks not run** [D-34] · S. Run on all four bands, both themes, once item 1 is fixed.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
