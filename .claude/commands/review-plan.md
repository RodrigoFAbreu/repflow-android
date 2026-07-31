---
description: Independently review the current plan bundle as the local_model_plan_review stage of the two-stage plan-review protocol ("2.1" work items only).
argument-hint: "[work-item-id]"
---

Enter the `AWAITING_LOCAL_PLAN_REVIEW` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md` (`docs/ai-workflow/WORKFLOW_V2_PLAN.md`'s
`D-Plan-Review-Stages`). Implements the `local_model_plan_review` **role**,
not a specific model: nothing in this contract, the written feedback
schema, or the state transition it produces names a model. Running it from
any capable Claude model produces the same schema and the same transition.
Recommended in a **fresh session** for genuine independence from the
session that wrote the plan — strongly recommended operational guidance,
not a verified precondition; no check here depends on session freshness.

`<bundle_dir>`/`<feedback_dir>` below resolve per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_bundle_dir`/`resolve_feedback_dir`).

1. **Resolve the work item**: `$ARGUMENTS`, if given, names the
   `work_item_id`; otherwise use `active_work_item_id`
   (`docs/ai-workflow/WORKFLOW_STATE.json`).
2. **Governing-version guard**: if the resolved item's
   `governing_workflow_version` is not `"2.1"`, refuse cleanly, naming the
   actual version — `"1"` items have no local-review stage to run; use the
   existing single-stage `AWAITING_EXTERNAL_PLAN_REVIEW` flow instead
   (`workflow_state.validate_local_plan_review_preconditions` raises
   `WrongGoverningVersionForPlanReviewStageError`).
3. **Phase guard**: if the item's `phase` is not
   `AWAITING_LOCAL_PLAN_REVIEW`, refuse cleanly, naming the actual phase —
   including "already completed this round" (`WrongPhaseForPlanReviewStageError`).
   Never silently re-run.
4. **Read**: the authoritative plan doc (`plan_path`), `<bundle_dir>/REVIEW_REQUEST.md`,
   `<bundle_dir>/MANIFEST.md`, the required-context file list, any
   prior `<feedback_dir>/REVIEW_FEEDBACK.md` (for continuity across
   rounds), and `docs/ai-workflow/REVIEW_PROTOCOL.md`'s feedback-structure
   contract.
5. **Recompute fresh, before writing anything**: the current `bundle_id`
   and the plan-stage `review_content_id`
   (`scripts/workflow_fingerprint.py`). Refuse, naming both a recomputed
   and a stale value, if the bundle directory does not match what
   `MANIFEST.md`/`REVIEW_REQUEST.md` claim (the same staleness discipline
   `/approve-review` applies, run one stage earlier) — a missing/unreadable
   bundle, or a work item resolved from the wrong worktree
   (`WORKTREE_IDENTITY.json`'s existing local-staleness check), are the
   same class of refusal. This command also runs inside a real, current
   worktree, so also call
   `workflow_fingerprint.assert_local_generation_matches(repo_root,
   <bundle_dir>/MANIFEST.md)` and stop, naming both, on a
   `WorktreeOrHeadMismatchError` (`D-Bundle-Manifest`, `WFR-17`).
6. **Independently verify** every finding the plan document claims as
   addressed against the actual repository state — never take the
   disposition table's word for it — and search for new findings, exactly
   as thoroughly as `/apply-plan-review`'s own validation requirement.
7. **Decide the verdict** (`Status: APPROVE | REVISE | BLOCK`) and write
   `<feedback_dir>/REVIEW_FEEDBACK.md` per
   `docs/ai-workflow/REVIEW_PROTOCOL.md`'s required structure, **plus**
   these provenance fields this role always includes:
   - `Reviewer role: local_model_plan_review` (never a model name here);
   - the three binding fields `docs/ai-workflow/REVIEW_PROTOCOL.md` now
     requires on every round (`Reviewed bundle ID:`, `Reviewed base
     commit:`, `Work item:`), stated with the recomputed `bundle_id`,
     `base_commit`, and `work_item_id` from step 5 (`WFR-03`), plus the
     recomputed plan-stage `review_content_id` as its own labelled line;
   - the round/sequence number (one more than the highest prior
     `local_model_plan_review` round on record, or `1` if none);
   - a completion timestamp.
   Malformed prior feedback is reported and stops rather than guessed at.
8. **Write set, exact**:
   - `APPROVE`: `REVIEW_FEEDBACK.md`, plus — via
     `workflow_state.record_local_plan_review(..., verdict="APPROVE", ...)`
     — the resolved work item's `local_model_plan_review` ledger fields and
     its phase transition to `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` in
     `WORKFLOW_STATE.json`.
   - `REVISE`: `REVIEW_FEEDBACK.md`, plus the phase transition to
     `REVISING_PLAN` (`record_local_plan_review(..., verdict="REVISE", ...)`
     — no ledger entry).
   - `BLOCK`: `REVIEW_FEEDBACK.md` only — `record_local_plan_review(...,
     verdict="BLOCK", ...)` is a true no-op; the work item stays at
     `AWAITING_LOCAL_PLAN_REVIEW`.
   Never the plan, registry, mapping, command, product, or bundle-content
   files, and never another work item's fields.
9. **Report and stop.** For an `APPROVE`: state the exact bundle path,
   `bundle_id`, and `review_content_id` the user must hand to the manual
   external reviewer (recommended: ChatGPT) — the same values just
   recorded in the ledger, so the user is never guessing which artifact to
   upload — and that `/record-manual-plan-review` is the next command,
   after the user pastes that reviewer's feedback into
   `REVIEW_FEEDBACK.md`. For a `REVISE`: state that `/apply-plan-review` is
   next. For a `BLOCK`: state that explicit user resolution is required
   before any further command runs. **Never** auto-continue to
   `/apply-plan-review` or to the manual-external stage in this same
   invocation.
