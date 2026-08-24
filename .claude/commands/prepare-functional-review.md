---
description: Prepare a manual functional-review checklist for the user and stop.
argument-hint: [work-item-id]
state_writer: true
review-subject: none
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Enter the `AWAITING_FUNCTIONAL_REVIEW` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

`<feedback_dir>` below resolves per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_feedback_dir`).

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
3a. **Checklist-evidence provenance commit, required** (`D-Scoped-
    Remediation-Acceptance`, revision 25 `GPT-R38-001`, content-scoped
    trailer value revision 26 `GPT-R39-001`): if this work item has a
    `docs/ai-workflow/WORKFLOW_STATE.json` entry, create or reuse a
    dedicated, content-idempotent provenance commit for the checklist just
    written — the evidence `/accept-scoped-remediation`'s confirmation
    guard requires, closing the gap that would otherwise make that guard
    permanently unsatisfiable.
    1. Compute the checklist file's intended committed blob:
       `git hash-object docs/ACTIVE_MILESTONE.md` (the content just written
       in step 3, not yet committed).
    2. Read `implementation_revision` live from
       `work_item["implementation_revision"]`.
    3. Call `workflow_state.discover_current_functional_checklist_evidence(
       repo_root, work_item_id, base_commit, head=<current HEAD>,
       implementation_revision=<that value>)`.
       - No result, or its `"blob"` differs from step 1's freshly computed
         blob: stage and commit **only** `docs/ACTIVE_MILESTONE.md` (no
         production/test changes) with a commit body carrying
         `Workflow-Functional-Checklist: <work_item_id>/<implementation_revision>/<checklist_blob>`
         + the ordinary `Workflow-Work-Item: <work_item_id>` trailer, where
         `<checklist_blob>` is step 1's computed blob (which, once
         committed, is exactly the commit's own `HEAD:docs/ACTIVE_MILESTONE.md`
         blob). **These two lines must be the commit message's own final
         paragraph** — after any `Co-Authored-By:`/`Claude-Session:` lines,
         never before them (`OPUS-R129-001`, the same rule
         `milestone-implement.md` step 1f, `bootstrap-workflow-v2.md` step
         6, `approve-review.md` step 6.4, and `accept-milestone.md` step 6
         already state): Git's `git interpret-trailers --parse`, the exact
         mechanism `discover_current_functional_checklist_evidence` uses,
         treats only the message's last paragraph as trailers, so a blank
         line after these two lines (e.g. one followed by
         `Co-Authored-By:`) silently discards both and makes the checklist
         evidence undiscoverable. This is what makes an interrupted
         preparation safe to retry: rerunning after the file write already
         landed but the commit did not either finds nothing new to commit
         (already committed by the prior partial run) or completes the
         commit that didn't happen yet — never a duplicate.
       - The result's `"blob"` already equals step 1's freshly computed
         blob exactly: nothing to commit — this is the existing, current
         evidence commit; do not create an empty commit.
    4. Record the resulting (existing or newly created) commit SHA and its
       committed blob as this invocation's checklist-evidence identity.
4. State clearly that findings should be placed at
   `<feedback_dir>/FUNCTIONAL_REVIEW.md`, and report the exact checklist
   evidence commit SHA and blob from step 3a to the user, so they know
   precisely which committed content they are reviewing (never merely "the
   current file," which could otherwise drift before or after this
   message is read). **Instruct the user explicitly**: if this work item's
   own registry still has an incomplete checkpoint (a continued-scope
   remediation round), their `scoped_remediation` confirmation to
   `/accept-scoped-remediation` must name this exact commit SHA and blob
   verbatim (`Functional checklist evidence commit: <sha>` /
   `Functional checklist evidence blob: <blob>`) — a confirmation naming
   an earlier, superseded evidence identity is refused as stale, even
   though that earlier commit remains independently discoverable; there is
   no automatic migration of an existing confirmation onto newer evidence.
5. Report and **stop**. This is a hard gate for the user to perform manual
   testing.
