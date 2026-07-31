---
description: Close out an explicitly accepted milestone and prepare the next one for planning.
disable-model-invocation: true
---

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
2a. **Parent-completion block** (`D-Functional-Remediation`, `WF4c`,
    resolves `GPT-R9-016`): if this work item has a
    `docs/ai-workflow/WORKFLOW_STATE.json` entry, call
    `workflow_state.complete_work_item(state, work_item_id, now=<now>)`.
    `IncompleteChildWorkItemError` stops here — report every named
    still-incomplete child work item verbatim and do not proceed past this
    point (no `ROADMAP.md`/`ACTIVE_MILESTONE.md` update, no completion
    commit); the user must drive each named child through its own full
    cycle to `MILESTONE_COMPLETE` before re-invoking this command. On
    success, persist the returned state (the work item's `phase` is now
    `MILESTONE_COMPLETE`; `active_work_item_id` resets to `null` if it
    pointed here) as part of the completion commit in step 6 — the
    original item's own registry/mapping/completed-checkpoint history is
    untouched by this call, by construction. A work item with no state
    entry (an ordinary `"1"` item that never got one) has no children by
    construction — skip this call entirely.
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
