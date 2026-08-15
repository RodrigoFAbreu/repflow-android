---
description: Record the user's functional acceptance of a continued-scope remediation round while the work item's own last checkpoint remains outstanding, then return to IMPLEMENTING.
argument-hint: [work-item-id]
disable-model-invocation: true
state_writer: true
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Enter the non-terminal acceptance path of `D-Scoped-Remediation-Acceptance`
(`docs/ai-workflow/WORKFLOW_V2_PLAN.md`, resolves `WF8B-002`), the second,
mutually exclusive command reachable from `AWAITING_FUNCTIONAL_REVIEW`
alongside `/accept-milestone` (`docs/ai-workflow/MILESTONE_WORKFLOW.md`).
Use this instead of `/accept-milestone` exactly when the work item's own
registry still has an incomplete checkpoint — a fix landed as extra scope
on an already-`COMPLETE` checkpoint while the item's own last checkpoint
(e.g. `WF8b`) is still outstanding. **No new phase is introduced**:
terminal-vs-non-terminal is decided at acceptance time by recomputing the
registry fresh, never read from a stored `phase` value.

**This command is user-only by construction**, the same two mechanisms as
`/accept-milestone`/`/approve-review`: (1) `disable-model-invocation: true`;
(2) refuse to write anything unless the user's own current-turn message
supplies literal confirmation text naming the exact `work_item_id` and the
`scoped_remediation` stage
(`workflow_state.validate_user_confirmation(text, work_item_id=...,
stage="scoped_remediation")`) — a keyword textually non-interchangeable
with `/accept-milestone`'s `"acceptance"`, so reuse of a terminal
milestone-acceptance confirmation as scoped acceptance is structurally
impossible. Never fabricate, infer, or carry over this text from a prior
turn.

0. **Resolve the target**: the work-item id named in `$ARGUMENTS`, or
   `active_work_item_id` from `docs/ai-workflow/WORKFLOW_STATE.json` if
   omitted. Refuse with a named error if neither resolves to an existing
   `work_items` entry — never guess. If the resolved item has no
   `docs/ai-workflow/WORKFLOW_STATE.json` entry at all, refuse: an
   ordinary `"1"` item that never got one has no registry to check, so
   `/accept-milestone` is the only reachable command for it — this
   command has nothing to do.

**Entry guard, confirmation-first, then evidence-binding, then
replay-first** (revision 25, `GPT-R38-002`/`-003`; evidence-binding step
inserted revision 27, `GPT-R40-001`):

1. **Validate current-turn user confirmation** —
   `workflow_state.validate_user_confirmation(text, work_item_id=<target>,
   stage="scoped_remediation")` — identically for a first execution and an
   exact replay, so a replay can never report success without the same
   user-only gate a first execution requires. `UserConfirmationRejectedError`
   stops here — report the concrete reason and ask for the missing/
   corrected confirmation; do not proceed without it. A confirmation-free,
   read-only inspection of an already-accepted round is out of scope for
   this command.
2. **Parse the confirmation's binding-evidence fields** —
   `workflow_state.parse_scoped_remediation_confirmation_binding_fields(text)`
   — missing or malformed `Functional checklist evidence commit:`/
   `Functional checklist evidence blob:` fields stop here, identically for
   a first execution and a replay.
3. **Capture the cross-invocation reference snapshot**, once, before the
   registry loads: `workflow_state.build_scoped_remediation_live_snapshot(
   repo_root, work_item)`. Keep this exact value; it is passed unchanged
   into both evidence-guard calls below (step 4 and step 9).
4. **Pre-commit evidence guard, first call**:
   `workflow_state.verify_functional_checklist_evidence(repo_root,
   work_item, base_commit=<work_item["base_commit"]>, head=<current HEAD>,
   confirmed_commit=<step 2's commit field>, confirmed_blob=<step 2's blob
   field>, expected_live_snapshot=<step 3's snapshot>)`. Only once this
   succeeds does the registry load proceed.
   - `MissingFunctionalChecklistEvidenceError`: stop, naming the missing
     round key and `/prepare-functional-review` as the remedy.
   - `StaleFunctionalChecklistConfirmationError`: stop, naming both the
     confirmed and current evidence identity, instructing the user to
     review the newer `/prepare-functional-review` report and reconfirm.
     There is no automatic migration onto newer evidence.
   - `MalformedFunctionalChecklistEvidenceError`: stop, naming the commit
     and both blob values — the trailer's embedded blob disagrees with
     what that commit actually committed.
   - `DirtyFunctionalChecklistPathError`: stop, naming the path.
   - `ScopedRemediationLiveValueChangedError`: stop (should not fire on
     this first call in practice, since the snapshot was just captured,
     but is checked identically both times regardless).
   Keep this call's return value (`{"commit_sha", "blob"}`) — this is the
   round's current evidence, and it equals the confirmed identity exactly
   at this point.
5. **Load the registry, authoritatively**:
   `workflow_state.resolve_own_registry_completion_status(repo_root,
   work_item)` → `(is_terminal, outstanding_checkpoint_id)` — always
   computable regardless of the current `phase`, since it depends only on
   the registry and `select_next_checkpoint`, never on `phase` itself.
   `RegistryCoverageError` stops here — report the specific failure; a
   registry-backed item's own declared registry must resolve, exist, be
   tracked, and declare this exact `work_item_id`, or nothing past this
   point can be trusted.
   1. If `is_terminal`: refuse, naming `/accept-milestone` as the correct
      command instead (unchanged from revision 22/23).
   2. Otherwise, build `live_fields`:
      `workflow_state.build_scoped_remediation_live_fields(state,
      work_item_id, outstanding_checkpoint_id=<from step 5>,
      functional_checklist_evidence_commit=<step 4's commit_sha>,
      functional_checklist_blob=<step 4's blob>)`, then call
      `workflow_state.resolve_scoped_remediation_round(repo_root,
      work_item_id, base_commit=<work_item["base_commit"]>,
      head=<current HEAD>, outstanding_checkpoint_id=<from step 5>,
      implementation_revision=work_item["implementation_revision"],
      live_fields=<above>)`:
      - `ExactReplay(commit_sha)`: report `commit_sha` as the idempotent
        result and **stop** — before the phase/`technical_approval.status`
        checks below ever run. A replay's own `phase` is already
        `IMPLEMENTING`, not `AWAITING_FUNCTIONAL_REVIEW`, by construction.
      - `ConflictingDuplicate(commit_sha, differing_fields)`: refuse,
        naming `commit_sha` and the specific disagreeing field(s) — never
        silently treated as success.
      - `MalformedAcceptanceRecord(commit_sha, reason)`: refuse, naming
        `commit_sha` and the specific schema defect — never treated as
        either a replay or an ordinary conflicting duplicate.
      - `AmbiguousHistory(round_key)`: refuse, naming the round key and
        that manual history inspection is required.
      - `NoExistingRound()`: proceed to step 6.
6. **No matching round recorded** (a first attempt at this round, or a
   genuinely new round for a checkpoint scoped-accepted before under a
   different `implementation_revision`): refuse via
   `workflow_state.scoped_remediation_gate_reachable(phase=work_item["phase"],
   is_terminal=is_terminal)` unless it returns `True`, and refuse unless
   `work_item["technical_approval"]["status"] == "CURRENT"` — a `STALE`
   technical approval (a bounded-fix round landed after approval but
   before this command ran) must never be scoped-accepted.
7. **Pre-commit evidence guard, second call — repeats before building the
   entry**: call `workflow_state.verify_functional_checklist_evidence(...)`
   again, identical arguments to step 4 (the **same**
   `expected_live_snapshot` from step 3), immediately before building the
   acceptance entry. A working-tree edit or a superseding
   `Workflow-Functional-Checklist` commit landing in the gap since step 4
   is exactly as unreviewed as one present from the start; the same five
   exceptions as step 4 apply identically.
8. **Build the acceptance entry**:
   `workflow_state.apply_scoped_remediation_acceptance(state,
   work_item_id, outstanding_checkpoint_id=<from step 5>,
   functional_checklist_evidence_commit=<step 7's commit_sha>,
   functional_checklist_blob=<step 7's blob>,
   user_confirmation=<this turn's verbatim text>, now=<now>)` — sets
   `phase = "IMPLEMENTING"`; leaves `checkpoints`/`current_checkpoint_id`/
   `active_work_item_id`/`plan_approval`/`technical_approval`/
   `functional_acceptance_status` completely untouched.
9. **Pre-commit evidence guard, third call — repeats once more,
   immediately before the provenance commit**: call
   `workflow_state.verify_functional_checklist_evidence(...)` a final
   time, same arguments, same `expected_live_snapshot`, right before the
   commit below actually runs.
10. **Dedicated provenance commit, required**: persist the state from step
    8 to `docs/ai-workflow/WORKFLOW_STATE.json` and create one
    metadata-only commit — no production/test changes — carrying
    `Workflow-Scoped-Remediation-Acceptance: <outstanding_checkpoint_id>/<implementation_revision>`
    + `Workflow-Work-Item: <work_item_id>` trailers, together or not at
    all. Do not report success until the commit exists and `git status`
    confirms a clean worktree.
11. Report the new `phase` (`IMPLEMENTING`), the commit SHA, and the
    outstanding checkpoint (`WF8b` or otherwise) now resumable, and
    **stop**. Do not resume the checkpoint in this same invocation — a
    later `/milestone-implement` invocation drives that.
