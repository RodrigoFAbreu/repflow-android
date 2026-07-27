---
description: Prepare a manual functional-review checklist for the user and stop.
---

Enter the `AWAITING_FUNCTIONAL_REVIEW` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

1. Confirm the automated verification state is current (rerun only if the
   working tree changed since the last full run in
   `/milestone-implement`/`/apply-implementation-review`).
2. Write a concise manual functional-review checklist covering:
   - setup (build/install steps, any feature flags or data needed);
   - test data (what to seed, e.g. exercises/plans/workouts);
   - exact user flows to exercise, step by step;
   - expected result for each flow;
   - known limitations or out-of-scope behavior for this milestone.
3. Put the checklist in `docs/ACTIVE_MILESTONE.md` under a "Functional review
   checklist" section (or link to a short file from there) — do not create a
   second, separate status document.
4. State clearly that findings should be placed at
   `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.
5. Report and **stop**. This is a hard gate for the user to perform manual
   testing.
