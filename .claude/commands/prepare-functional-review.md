---
description: Prepare a manual functional-review checklist for the user and stop.
argument-hint: [work-item-id]
---

Enter the `AWAITING_FUNCTIONAL_REVIEW` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

0. **Resolve the target** (D-Legacy phase 2, `WF-M8b`): the work-item id
   named in `$ARGUMENTS`, or `active_work_item_id` from
   `docs/ai-workflow/WORKFLOW_STATE.json` if none is given. Refuse with a
   named error if neither resolves to an existing `work_items` entry —
   never guess or fall back to a different item.
0a. **`LEGACY_READY` adoption scan, before any version branching**
    (`D-Legacy` phase 2): this is a repository-level lookup against the
    *resolved target only* — a command invoked for a different work-item
    id, or for the current `active_work_item_id` when it is not this
    dormant item, never touches Milestone 8's (or any other legacy item's)
    entry, regardless of its phase. If the resolved target's `phase` is
    **not** `LEGACY_READY`, skip straight to step 1 with that target.
    Otherwise, perform adoption before anything else:
    1. Call `workflow_state.promote_legacy_work_item(state, repo_root,
       work_item_id=<target>, required_active_milestone_substring=<the
       narrative confirming the branch is integrated and accepted, e.g.
       "accepted and closed">, artifacts_path=<this item's own
       artifact-declarations file, not `workflow-v2-1-core`'s>, now=<now>)`.
    2. `LegacyReconciliationError` (re-checked here, never trusted from
       import time — a dormant item can sit for a long time): stop, name
       both the reviewed commit and what `docs/ACTIVE_MILESTONE.md`
       actually contains.
    3. `LegacyAdoptionStaleApprovalError` (the recomputed
       `technical_approval` freshness check reports `STALE` — protected
       implementation-stage content changed since import): stop, report
       it, and require a fresh implementation-review round through the
       normal `technical_approval` lifecycle
       (`/apply-implementation-review` + `/approve-review
       implementation`). **Never re-import** — a stale legacy approval is
       handled exactly like any other stale technical approval.
    4. On success, persist the returned state to
       `docs/ai-workflow/WORKFLOW_STATE.json`. `active_work_item_id` now
       points at the target, `governing_workflow_version` has transitioned
       `"1"` → `"2.1"`, and `phase` is now `AWAITING_FUNCTIONAL_REVIEW` —
       `technical_approval` itself (`basis: LEGACY_V1`) is unchanged.
       Proceed to step 1 for this same target; do not stop here — adoption
       is not itself the functional-review checklist, step 1 still runs in
       this same invocation.
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
