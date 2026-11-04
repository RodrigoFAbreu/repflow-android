# accessibility-audit

Accessibility pass, including the rest-end announcement

**Complexity:** medium. **Tier:** Ideas: kept, not scheduled.

Kept for later. Not scheduled.

## App findings

- **Steppers have no hold-to-repeat, and rest end isn't announced to screen readers** [A-47] · M. Empty to 60 kg at 2.5 kg steps is 24 taps. Evidence: `RepFlowStepper.kt`, no `liveRegion` in the workout code; accessibility board 60b. Do the hold first; the announcement goes with roadmap item 5.1.

This finding spans more than one item. Do only the part this item's title names.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
