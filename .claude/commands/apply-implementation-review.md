---
description: Apply external implementation-review feedback and prepare for another review round if needed.
---

Enter the `APPLYING_REVIEW_FEEDBACK` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

`<bundle_dir>`/`<feedback_dir>` below resolve per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_bundle_dir`/`resolve_feedback_dir`).

0. **Dual-mode branch** (Workflow v2.1, `WF4a-ii`): read the target work
   item's `governing_workflow_version` from
   `docs/ai-workflow/WORKFLOW_STATE.json`. Both `"1"` and `"2.1"` items run
   steps 1-8 identically — this command's exit target
   (`AWAITING_TECHNICAL_APPROVAL`) is amended only in its naming, per
   `D-Self-Governance`; there is no other version-specific behavior here.
   This step exists so the command's own dual-mode structure is explicit
   and testable per that enumeration.
1. Read `<feedback_dir>/REVIEW_FEEDBACK.md`. If it does not exist, stop
   and say so. Validate its binding fields
   (`workflow_fingerprint.parse_review_feedback_binding_fields`/
   `assert_feedback_matches_bundle` against the current recomputed
   `bundle_id`/`base_commit`/`work_item_id`, `WFR-03`) — stale or
   mismatched feedback is a reason to stop and say so, not to apply.
2. Reproduce and validate every Blocking and Important finding against the
   actual code/tests before changing anything. Do not apply a finding you
   cannot reproduce or verify — reject it with evidence instead.
3. Fix all validated Blocking and Important findings. Consider Optional
   findings and Missing Tests findings; apply them if cheap and low-risk,
   otherwise note why not.
4. For any rejected finding, record the rejection with concrete evidence
   (file/line/test/doc reference) in `<bundle_dir>/IMPLEMENTATION_SUMMARY.md`.
5. Rerun the relevant narrow tests for each fix, then the full suite used in
   `/milestone-implement` step 3 before closing this pass.
6. Commit coherent fixes (one commit per coherent fix, not one giant
   catch-all commit).
7. Regenerate the bundle at the `post-fix` stage:
   `./scripts/prepare-ai-review.sh <base-sha> post-fix [work_item_id]`. If this work item
   has a `docs/ai-workflow/WORKFLOW_STATE.json` entry: call
   `workflow_state.record_bundle_generation(state, work_item_id,
   stage="post-fix", head=<current HEAD SHA>, now=<now>)` (`WF4c`,
   D-Approval-Commits' sole writer of `reviewed_implementation_head`) and
   persist the returned state — required before `AWAITING_TECHNICAL_APPROVAL`
   can be reachable again; skip this call entirely for a work item with no
   state entry.
8. If any Blocking finding remains unresolved, or the fix was structurally
   significant, stay in `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` and stop
   for another review round. Otherwise report readiness and that
   `AWAITING_TECHNICAL_APPROVAL` is the next state, and stop — do not
   auto-run `/approve-review` or `/prepare-functional-review`. Only the user
   invokes `/approve-review implementation`; let the user decide when to
   proceed.
