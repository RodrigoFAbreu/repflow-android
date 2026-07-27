---
description: Classify and fix user functional-testing findings, then return to the functional-review gate.
---

Enter the `FIXING_FUNCTIONAL_FINDINGS` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

1. Read `.ai-review/feedback/FUNCTIONAL_REVIEW.md`. If it does not exist,
   stop and say so.
2. Classify each finding as one of: **defect**, **usability issue**,
   **missing requirement**, **enhancement**, or **expected behavior**. State
   the classification and reasoning for each.
3. Reproduce each defect/usability finding where practical before fixing it.
4. Fix defects, usability issues, and missing requirements. Add a regression
   test for each defect. Do not silently implement enhancements — flag them
   for the user to prioritize instead, unless trivially in-scope.
5. Commit coherent fixes.
6. Update the functional-review checklist in `docs/ACTIVE_MILESTONE.md` to
   reflect what changed and what still needs re-testing.
7. Report and return to `AWAITING_FUNCTIONAL_REVIEW` — stop for the user to
   re-test, unless the user explicitly waives another round.
