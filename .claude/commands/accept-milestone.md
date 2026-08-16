---
description: Close out an explicitly accepted milestone and prepare the next one for planning.
disable-model-invocation: true
state_writer: true
review-subject: none
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Enter the `MILESTONE_COMPLETE` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`. Only run this after the user has
explicitly accepted the milestone (`AWAITING_USER_ACCEPTANCE` exit
condition) — if that acceptance hasn't happened in this conversation, ask for
it before proceeding.

**This command is user-only by construction** (`docs/ai-workflow/WORKFLOW_V2_PLAN.md`
D2, resolves `OPUS-R6-010`), the same two mechanisms as `/approve-review`:
(1) `disable-model-invocation: true` (primary, harness-enforced — see
`approve-review.md` for this installation's verified/unverified coverage of
each exposure path); (2) refuse to write anything unless the user's own
current-turn message supplies literal confirmation text naming the exact
`work_item_id` and the `acceptance` stage
(`workflow_state.validate_user_confirmation(text, work_item_id=..., stage="acceptance")`).
Never fabricate, infer, or carry over this text from a prior turn. If it is
missing, ask for it and stop — do not proceed on an inferred "yes."

0. **Dual-mode branch** (Workflow v2.1, `WF4a-ii`, extended `WF4c`): read
   the target work item's `governing_workflow_version` from
   `docs/ai-workflow/WORKFLOW_STATE.json`. Both `"1"` and `"2.1"` items run
   steps 1-8 identically, including the new step 2a below —
   `AWAITING_USER_ACCEPTANCE`/`MILESTONE_COMPLETE` gain exactly one shared
   condition from `D-Functional-Remediation`: acceptance blocks while any
   work item names this one as its own `parent_work_item_id` and has not
   itself reached `MILESTONE_COMPLETE` — version-independent, since a
   remediation child can exist under either governing version. This step
   exists so the command's own dual-mode structure is explicit and
   testable per `D-Self-Governance`'s "every command this milestone
   modifies" enumeration.
1. Run the user-only guard: call
   `workflow_state.validate_user_confirmation(text, work_item_id=..., stage="acceptance")`
   against this turn's literal user text. `UserConfirmationRejectedError`
   stops the command here — report the concrete reason and ask for the
   missing/corrected confirmation; do not proceed without it.
2. Confirm final verification already passed (from the last
   `/milestone-implement` or `/apply-implementation-review` run); rerun only
   if the working tree changed since.
2a. **Terminal-reachability pre-flight, then parent-/own-checkpoint-
    completion block** (`D-Functional-Remediation`, `WF4c`, resolves
    `GPT-R9-016`; extended `D-Scoped-Remediation-Acceptance`, resolves
    `WF8B-002`, hardened `GPT-R37-001`): if this work item has a
    `docs/ai-workflow/WORKFLOW_STATE.json` entry:
    - First, an **advisory, user-facing pre-flight refusal** (not a
      security boundary — `complete_work_item` itself re-resolves and
      re-validates the authoritative registry independently below, so this
      is purely for a clearer early message): call
      `workflow_state.resolve_own_registry_completion_status(repo_root,
      work_item)` to get `(is_terminal, outstanding_checkpoint_id)`, then
      `workflow_state.milestone_complete_gate_reachable(phase=work_item["phase"],
      is_terminal=is_terminal)`. `False` stops here — report the actual
      `phase` and, if `not is_terminal`, name `outstanding_checkpoint_id`
      and point at `/accept-scoped-remediation` as the correct command
      instead; do not proceed past this point.
    - Otherwise, call `workflow_state.complete_work_item(state, work_item_id,
      now=<now>, repo_root=<repo_root>)` — note the parameter change: this
      no longer accepts a caller-supplied `registry` dict at all; it
      resolves and loads the item's own `registry_path` itself,
      authoritatively, from disk. `IncompleteChildWorkItemError` stops here
      — report every named still-incomplete child work item verbatim.
      `RegistryCoverageError` stops here — the item's own declared
      registry failed to resolve, read, parse, or declare the expected
      `work_item_id`; report the specific failure verbatim, this is a data-
      integrity defect, never silently treated as "nothing to check."
      `IncompleteOwnCheckpointsError` stops here — report the named
      outstanding checkpoint and point at `/accept-scoped-remediation`.
      None of these three stops perform any `ROADMAP.md`/`ACTIVE_MILESTONE.md`
      update or completion commit. On success, persist the returned state
      (the work item's `phase` is now `MILESTONE_COMPLETE`;
      `active_work_item_id` resets to `null` if it pointed here) as part
      of the completion commit in step 6 — the original item's own
      registry/mapping/completed-checkpoint history is untouched by this
      call, by construction. A work item with no state entry (an ordinary
      `"1"` item that never got one) has no children and no registry by
      construction — skip this entire step.
3. Update `docs/ROADMAP.md` to mark the milestone complete.
4. Update `docs/ACTIVE_MILESTONE.md`: move this milestone's summary into the
   factual "complete" state, clear the active plan section.
5. Archive this milestone's execution/reference plans to
   `docs/milestones/completed/`.
6. Create the final completion commit if verification/doc updates are not
   already committed.
7. Set `docs/ACTIVE_MILESTONE.md`'s "Next action" to point at the next
   incomplete milestone in `docs/ROADMAP.md`, ready for `PLANNING`.
8. Report the milestone as complete and the next action as
   `/milestone-plan`. Do not begin implementing the next milestone in this
   command.
