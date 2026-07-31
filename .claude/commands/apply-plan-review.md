---
description: Apply external plan-review feedback and revise the plan.
---

Enter the `REVISING_PLAN` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

`<bundle_dir>`/`<feedback_dir>` below resolve per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_bundle_dir`/`resolve_feedback_dir`).

0. **Dual-mode branch** (Workflow v2.1, `WF4a-ii`/`WF4a-iv`): read the
   target work item's `governing_workflow_version` from
   `docs/ai-workflow/WORKFLOW_STATE.json` (missing entirely is equivalent to
   `"1"`).
   - **`governing_workflow_version: "1"`**: steps 1-7 execute exactly as
     written, exiting to `AWAITING_PLAN_APPROVAL`.
   - **`governing_workflow_version: "2.1"`**: steps 1-6 execute identically;
     step 7 is replaced by the revised exit step below — the two-stage
     local-then-manual-external plan-review protocol (`D-Plan-Review-Stages`,
     `/review-plan`, `/record-manual-plan-review`). This branch is inert for
     this repository's own work item, which is fixed at `"1"` for its
     entire execution, but is otherwise fully live.
1. Read `<feedback_dir>/REVIEW_FEEDBACK.md`. If it does not exist, stop
   and say so — do not proceed on assumed feedback. Validate its binding
   fields (`workflow_fingerprint.parse_review_feedback_binding_fields`/
   `assert_feedback_matches_bundle` against the current recomputed
   `bundle_id`/`base_commit`/`work_item_id`, `WFR-03`) — stale or
   mismatched feedback is a reason to stop and say so, not to apply.
2. For every Blocking, Important, and Optional finding: validate it against
   the actual repository (read the relevant code/docs, do not take the
   finding's premise on faith).
3. Apply accepted findings to the plan (`<bundle_dir>/PLAN.md` and the
   real execution/reference plan doc).
4. For any finding you reject, write the rejection with concrete repository
   evidence (file path, line, existing test, or doc reference) directly in
   the plan doc's decisions section — not a separate rebuttal file.
5. Update `<bundle_dir>/REVIEW_REQUEST.md` to reflect the revision and
   rerun `./scripts/prepare-ai-review.sh <base-sha> plan [work_item_id]` to refresh the
   bundle.
6. If the `Status` was `BLOCK`, or if you made major structural changes to
   the plan, stay in `AWAITING_EXTERNAL_PLAN_REVIEW` and stop for another
   review round.
7. **`governing_workflow_version: "1"`**: report the plan as ready and that
   `AWAITING_PLAN_APPROVAL` is the next state, and stop — do not auto-run
   `/approve-review` or `/milestone-implement`. Only the user invokes
   `/approve-review plan`; let the user decide when to proceed.
7'. **`governing_workflow_version: "2.1"`, revised exit step** (resolves
    `GPT-R11-003`/`-007`): this command never self-declares plan readiness,
    regardless of how large or small a "structural change" judgment would
    call the edit:
    1. the recomputed `review_content_id` already differs from whatever
       `plan_review_stages` last recorded, so both stages already read as
       absent (`plan_approval_gate_reachable`'s recomputation rule — no
       explicit ledger clear performed or needed);
    2. the bundle is regenerated (`./scripts/prepare-ai-review.sh <base-sha>
       plan`, same as step 5, unchanged mechanism);
    3. call `workflow_state.transition_to_awaiting_local_plan_review(state,
       work_item_id, now)` and persist the returned state to
       `docs/ai-workflow/WORKFLOW_STATE.json`;
    4. report the work item's phase as `AWAITING_LOCAL_PLAN_REVIEW` and
       stop — the same "stop, do not auto-continue" pattern step 7 uses for
       `"1"`. This is the sole path back to `AWAITING_LOCAL_PLAN_REVIEW`,
       whether the edit was driven by a local-model or a manual-external
       `REVISE` — no path re-enters manual-external review without a fresh
       local pass first.

Do not implement product code in this command.
