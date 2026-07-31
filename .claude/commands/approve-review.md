---
description: Approve the plan or implementation stage for the active (or named) work item. User-only -- never invoked by Claude autonomously.
argument-hint: <plan|implementation> [work-item-id]
disable-model-invocation: true
---

Enter `AWAITING_PLAN_APPROVAL` or `AWAITING_TECHNICAL_APPROVAL`
(`docs/ai-workflow/MILESTONE_WORKFLOW.md`) for the stage named in
`$ARGUMENTS` (`plan` or `implementation`), for the work item also named in
`$ARGUMENTS` or, if omitted, `active_work_item_id`.

**This command is user-only by construction.** `disable-model-invocation:
true` is the primary, harness-enforced control (blocks the SlashCommand
tool). Claude must never invoke it on the user's own behalf, including as a
step of another command's execution, independent of that flag.

**User-only guard, mechanism (2)**: refuse to write anything unless the
user's own current-turn message supplies literal confirmation text naming
the exact `work_item_id` and the exact stage (`plan`/`implementation`)
being approved (`workflow_state.validate_user_confirmation`). Never
fabricate, infer, or carry over this text from a prior turn.

**Verified coverage of mechanism (1)** (`docs/ai-workflow/WORKFLOW_V2_PLAN.md`
D2, resolves `OPUS-R6-010`): this installation's own bundled frontmatter
reference documents `disable-model-invocation: true` as blocking only "the
SlashCommand tool" -- it does not name the Skill exposure path at all. The
installed Claude Code changelog separately confirms skill-aware handling of
`disable-model-invocation: true` (a fix for skills with that flag failing
when invoked via `/<skill>` mid-message), suggesting real coverage there
too. A same-session empirical test was inconclusive: this harness's model
tool surface exposes commands to Claude exclusively through the `Skill`
tool (no distinct `SlashCommand` tool is present in the available tool
set), but the skill-routing table is fixed at session start, so a
newly-flagged command reliably returns "Unknown skill" whether or not the
flag actually gates it -- a decisive test requires invoking a flagged
command via `Skill` from a **fresh session** after this file lands. Until
that fresh-session result is recorded, treat mechanism (2) above as the
actually load-bearing control for the Skill exposure path, not mechanism
(1) alone.

0. **Dual-mode branch** (Workflow v2.1, `WF4a-ii`): resolve the target work
   item (named argument, or `active_work_item_id`) and read its
   `governing_workflow_version` from `docs/ai-workflow/WORKFLOW_STATE.json`.
   - **`governing_workflow_version: "1"`**: steps 1-7 execute exactly as
     written.
   - **`governing_workflow_version: "2.1"`**: steps 1-7 execute identically;
     for the plan stage only, step 1's gate-reachability check additionally
     requires `workflow_state.plan_approval_gate_reachable(...)`'s `"2.1"`
     branch (the `plan_review_stages` ledger, populated by `/review-plan`/
     `/record-manual-plan-review`, `D-Plan-Review-Stages`) — fully live;
     inert only in the sense that this repository's own work item is fixed
     at `"1"` for its entire execution and so never exercises it.
1. **Confirm gate reachability**: read `.ai-review/feedback/REVIEW_FEEDBACK.md`'s
   most recently reviewed round status and bundle ID. Call
   `workflow_state.approval_gate_reachable(status)` for the plan stage on a
   `"1"` item (`plan_approval_gate_reachable(...)` on a `"2.1"` item), or
   `workflow_state.technical_approval_gate_reachable(...)` for the
   implementation stage. The latter's `protected_path_dirty` argument is
   `workflow_state.any_protected_path_dirty(...)` (`WF4a-iii`), called with
   the implementation-stage classification
   (`workflow_fingerprint.load_implementation_stage_classification(...)`) —
   `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` dirtiness never blocks this,
   by construction of that classification. Its
   `head_matches_reviewed_implementation_head` argument
   (`WF4c`, D-Approval-Commits) is exactly `work_item["reviewed_implementation_head"]
   == <live HEAD SHA>` — `reviewed_implementation_head`'s sole writer is the
   bundle generator (`workflow_state.record_bundle_generation`, called by
   `/milestone-implement` at the `"implementation"` stage and
   `/apply-implementation-review`/`/apply-functional-review`'s bounded-fix
   branch at the `"post-fix"` stage); a `None` value (nothing has ever
   generated a bundle for this work item) compares unequal to any HEAD, so
   the gate is correctly unreachable until at least one bundle exists. A
   `BLOCK` status, or an unmet additional condition, stops here — report
   why, do not proceed.
2. **Recompute fresh**: `bundle_id` over the current bundle and the
   stage-appropriate `review_content_id` (`scripts/workflow_fingerprint.py`)
   over the working tree. Display both, and the protected/excluded path
   lists, to the user.
3. **Resolve the basis**: call `workflow_state.resolve_approval_basis(...)`
   with the feedback round's status/bundle_id, the freshly recomputed
   current bundle_id, this turn's literal `user_confirmation` text (if the
   user has not supplied it this turn, ask for it naming the exact
   `work_item_id` and stage, then stop and wait — never guess it),
   `work_item_id`, and `stage`. `BlockCannotApproveError`/
   `UserConfirmationRejectedError` stop the command; report the concrete
   reason.
4. **Build the record**: `workflow_state.build_approval_record(...)`, with
   `reviewed_content_commit` left unset for the plan stage (permanently
   null, `D-Approval-Commits`/`GPT-R9-006`) and set to the current
   `reviewed_implementation_head` for the implementation stage.
5. **Write it**: `workflow_state.apply_plan_approval(...)` or
   `apply_technical_approval(...)`, and persist the returned state to
   `docs/ai-workflow/WORKFLOW_STATE.json`.
6. **Create the approval commit**:
   - Plan stage: one commit containing the approved plan doc(s), registry
     JSON + generated Markdown view, `WORKFLOW_STATE.json`, and the
     requirements mapping file together, carrying `Workflow-Plan-Approval:
     <full review_content_id>` + `Workflow-Work-Item: <id>` trailers.
   - Implementation stage: a metadata-only commit (zero production/test
     changes) carrying `Workflow-Technical-Approval: <full
     review_content_id>` + `Workflow-Work-Item: <id>`.
   This command only writes the trailer; it never needs to search for one
   itself. The exact scoped trailer *lookup* later durability/freshness
   checks use is `workflow_state.discover_plan_approval_commit`/
   `discover_technical_approval_commit` (`WF4a-iii`).
6a. **Verify the commit** (`WF4a-iii`, WFR-06): immediately after the
    commit lands, call
    `workflow_state.verify_post_approval_manifest_match(repo_root,
    work_item, stage=..., base_commit=..., commit=<the new commit's SHA>)`.
    `PostApprovalManifestMismatchError` means the committed content does
    not match what was reviewed — stop and report it; never silently
    accept it, and never let a mismatch reach the user as a successful
    approval.
7. Report the new phase (`IMPLEMENTING` or `AWAITING_FUNCTIONAL_REVIEW`) and
   **stop**. Never chain into the next state's actions in the same
   invocation.
