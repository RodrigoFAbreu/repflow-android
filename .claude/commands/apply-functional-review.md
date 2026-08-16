---
description: Classify and fix user functional-testing findings, then return to the functional-review gate.
state_writer: true
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Enter the `FIXING_FUNCTIONAL_FINDINGS` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

`<feedback_dir>` below resolves per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_feedback_dir`).

0. **Dual-mode branch** (Workflow v2.1, `WF4c`, `D-Functional-Remediation`):
   resolve the target work item — the id named in `$ARGUMENTS`, or
   `active_work_item_id` from `docs/ai-workflow/WORKFLOW_STATE.json` if
   omitted (refuse with a named error if neither resolves to an existing,
   non-terminal `work_items` entry — never guess).
   - **No `docs/ai-workflow/WORKFLOW_STATE.json` entry exists** for this
     work item (an ordinary `"1"` item never routed through
     `workflow_state.route_work_item`): steps 1-7 below execute exactly as
     written, with no state reads or writes — this branch is inert by
     construction.
   - **An entry exists** (either governing version — `D-Functional-
     Remediation` applies uniformly, it is not a `"2.1"`-only mechanism):
     also read `docs/ai-workflow/WORKFLOW_CONFIG.json`
     (`workflow_state.load_config`). Step 4 is replaced by the per-finding
     three-way branch below; steps 1-3 and 5-7 execute unchanged.

1. Read `<feedback_dir>/FUNCTIONAL_REVIEW.md`. If it does not exist,
   stop and say so.
2. Classify each finding as one of: **defect**, **usability issue**,
   **missing requirement**, **enhancement**, or **expected behavior**. State
   the classification and reasoning for each.
3. Reproduce each defect/usability finding where practical before fixing it.
4. Fix defects, usability issues, and missing requirements. Add a regression
   test for each defect. Do not silently implement enhancements — flag them
   for the user to prioritize instead, unless trivially in-scope.

**[state-tracked items only] Step 4, replaced — the three-way branch**
(`D-Functional-Remediation`): decide, *before* touching any source/test
file, which branch each defect/usability-issue/missing-requirement finding
falls into. The three branches are per-finding-group, not mutually
exclusive within one round — a single round can resolve some findings as
"no code change," fix others as "bounded," and defer the rest as "broad,"
all in the same invocation.

- **No code change**: the finding needs no source/test edit at all (e.g.
  resolved by a narrative-only checklist correction, or turns out on
  reproduction to already be correct). `technical_approval` is left
  completely untouched — there is nothing to stale. Proceed straight to
  step 5 for this finding.

- **Bounded code change**: a normal, contained fix — comparable in size to
  an ordinary implementation-review remediation round.
  1. **Stale-before-edit ordering, hard requirement**: before the first
     source/test edit for this finding lands, call
     `workflow_state.mark_technical_approval_stale(state, work_item_id,
     now=<now>)` and persist the result to
     `docs/ai-workflow/WORKFLOW_STATE.json` immediately — on disk before
     any edit, not merely decided — so an interrupted session still shows
     `STALE` rather than a `CURRENT` record whose reviewed content no
     longer matches the working tree. If an edit already landed before
     this call ran, that is a process violation: make the stale write
     now, before continuing, rather than backfilling it after the fact.
  2. Make the fix (step 4's normal work: regression test included).
  3. Commit the fix (one coherent commit; do not bundle it with an
     unrelated finding's fix).
  4. Regenerate the bundle at the `post-fix` stage. **First**, before
     writing any bundle file, call `workflow_state.resolve_bundle_generation_outcome(
     repo_root, work_item, base_commit=<base-sha>, head=<current HEAD SHA>)`
     (WF8c (c), D-Commit-Provenance "Same-content post-fix republication")
     to learn which of the two legal outcomes applies — `("ordinary", None)`
     when the fix genuinely changed protected implementation-stage content,
     `("same_content", t)` when the fix nets out byte-identical to the
     currently-reviewed round (e.g. reverted before publication). Then call
     `workflow_state.record_bundle_generation(state, work_item_id,
     stage="post-fix", head=<current HEAD SHA>, now=<now>, outcome=<the
     resolved outcome>)` — this is the step that writes the new
     `reviewed_implementation_head` for the `"ordinary"` outcome
     (D-Approval-Commits' sole writer; left unchanged for `"same_content"`);
     nothing else makes `/approve-review implementation` reachable again —
     persist the returned state to `WORKFLOW_STATE.json`, and commit it
     **alone**: stage exactly that one path (never a broader `git add`) and
     create one commit carrying, for `"ordinary"`,
     `Workflow-Bundle-Generation-Record: <work_item_id>/<implementation_revision>`
     + `Workflow-Work-Item: <work_item_id>` trailers, no other trailer; for
     `"same_content"`, that same `Workflow-Bundle-Generation-Record` value
     (unchanged) + `Workflow-Work-Item: <work_item_id>` +
     `Workflow-Supersedes: <t>`, no other trailer. This durability commit
     must land *before* generation, never after (`WF8B-003`, resolved
     `D-Approval-Commits` revision 28) — a durability commit made after
     generation is by definition one commit ahead of the value it just
     wrote, permanently re-breaking `/approve-review implementation`'s
     provenance-interval check on every round. Then run
     `./scripts/prepare-ai-review.sh <base-sha> post-fix [work_item_id]`.
  5. This finding now requires a fresh implementation-review round: report
     readiness and **stop** — this re-enters
     `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` exactly like an ordinary
     post-fix round (`/apply-implementation-review` drives it from here).
     A fresh `/approve-review implementation` must succeed before
     `AWAITING_FUNCTIONAL_REVIEW` is reachable again for this item. Do not
     proceed to step 6/7 in this same invocation once any finding takes
     this branch — the stop is for the whole round, not just this finding.

- **Broad/multi-finding remediation**: the fix spans substantially more
  work than a single contained round — multiple checkpoints' worth, work
  that cuts across unrelated areas, or genuinely uncertain in scope. Do
  not guess past the point of confidence; a genuinely ambiguous case is
  treated as broad, letting the child item's own planning stage resolve
  the scope properly rather than under-scoping a fix here.
  1. Do not edit source/test files for this finding in the parent's
     working tree at all — broad remediation happens entirely inside the
     child work item, planned and reviewed on its own.
  2. Call `workflow_state.create_remediation_child_work_item(state,
     config, parent_work_item_id=<this item's id>, plan_path=<a fresh
     plan-doc path following this item's own naming convention, e.g.
     `docs/milestones/<child-id>-execution.md` for a product item>,
     registry_path="docs/ai-workflow/registry/<child-id>-registry.json",
     base_commit=<current HEAD>, now=<now>)` and persist the returned
     state. The child id is derived deterministically
     (`<parent-id>-remediation-<n>`, never caller-numbered) — report it
     back verbatim. This call never touches the parent's own
     `registry_path`/`plan_path` files or `active_work_item_id`.
  3. Note the deferral in the functional-review checklist (step 6): which
     findings, and the exact child work-item id they were routed to —
     this is what satisfies "addressed or explicitly deferred with
     rationale" for these specific findings.
  4. Tell the user the next action for that scope is `/milestone-plan
     <child-id>`, which drafts and routes the remediation plan through the
     full independent review cycle — never implemented inline in this
     command. The parent's own registry, mapping, and completed-checkpoint
     history are never touched by this branch.

5. Commit coherent fixes for any "no code change"/narrative-only findings
   resolved in step 4 (the bounded branch already committed its own fix
   above; a round that is entirely bounded- or broad-branch findings may
   have nothing left to commit here).
6. Update the functional-review checklist in `docs/ACTIVE_MILESTONE.md` to
   reflect what changed, what was deferred to a remediation child (name the
   child work-item id), and what still needs re-testing.
7. Report and return to `AWAITING_FUNCTIONAL_REVIEW` — stop for the user to
   re-test, unless the user explicitly waives another round. If any finding
   this round took the bounded branch, that stop already happened above
   instead (a fresh implementation-review round is required first); do not
   report both stops as satisfied by the same invocation.
