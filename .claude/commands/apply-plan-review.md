---
description: Apply external plan-review feedback and revise the plan.
---

Enter the `REVISING_PLAN` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

0. **Dual-mode branch** (Workflow v2.1, `WF4a-ii`/`WF4a-iv`): read the
   target work item's `governing_workflow_version` from
   `docs/ai-workflow/WORKFLOW_STATE.json` (missing entirely is equivalent to
   `"1"`).
   - **`governing_workflow_version: "1"`**: steps 1-7 execute exactly as
     written, exiting to `AWAITING_PLAN_APPROVAL`.
   - **`governing_workflow_version: "2.1"`**: steps 1-6 execute identically;
     step 7's exit target is `AWAITING_LOCAL_PLAN_REVIEW`, not
     `AWAITING_PLAN_APPROVAL` — the two-stage local-then-manual-external
     plan-review protocol (`D-Plan-Review-Stages`, `/review-plan`,
     `/record-manual-plan-review`) is owned by `WF4a-iv`, not yet built;
     this branch is inert until then, since this repository's own work item
     is fixed at `"1"` for its entire execution.
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
7. Otherwise, per step 0's branch: report the plan as ready and that
   `AWAITING_PLAN_APPROVAL` (`"1"`) / `AWAITING_LOCAL_PLAN_REVIEW` (`"2.1"`)
   is the next state, and stop — do not auto-run `/approve-review` or
   `/milestone-implement`. Only the user invokes `/approve-review plan`;
   let the user decide when to proceed.

Do not implement product code in this command.
