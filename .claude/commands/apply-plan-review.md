---
description: Apply external plan-review feedback and revise the plan.
---

Enter the `REVISING_PLAN` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

1. Read `.ai-review/feedback/REVIEW_FEEDBACK.md`. If it does not exist, stop
   and say so — do not proceed on assumed feedback.
2. For every Blocking, Important, and Optional finding: validate it against
   the actual repository (read the relevant code/docs, do not take the
   finding's premise on faith).
3. Apply accepted findings to the plan (`.ai-review/current/PLAN.md` and the
   real execution/reference plan doc).
4. For any finding you reject, write the rejection with concrete repository
   evidence (file path, line, existing test, or doc reference) directly in
   the plan doc's decisions section — not a separate rebuttal file.
5. Update `.ai-review/current/REVIEW_REQUEST.md` to reflect the revision and
   rerun `./scripts/prepare-ai-review.sh <base-sha> plan` to refresh the
   bundle.
6. If the `Status` was `BLOCK`, or if you made major structural changes to
   the plan, stay in `AWAITING_EXTERNAL_PLAN_REVIEW` and stop for another
   review round.
7. Otherwise, report the plan as ready for implementation and stop — do not
   auto-start `/milestone-implement`. Let the user decide when to proceed.

Do not implement product code in this command.
