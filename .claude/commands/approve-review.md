---
description: Approve the plan or implementation stage for the active (or named) work item. User-only -- never invoked by Claude autonomously.
argument-hint: <plan|implementation> [work-item-id]
disable-model-invocation: true
state_writer: true
---

**State-writer discipline (D1, item 354):** every `docs/ai-workflow/WORKFLOW_STATE.json` write this command performs -- everywhere a step below says "persist the returned state" -- is performed by calling `workflow_state.state_transaction(repo_root, mutator)`, never by a separate read-then-write: `state_transaction` holds `.ai-review/runtime/WORKFLOW_STATE.lock` (`workflow_state.state_lock`, `fcntl.flock(LOCK_EX)`) across the complete re-read -> apply-the-named-function -> canonical-serialize -> atomic-publish sequence in one process invocation, so `mutator` is the exact transition function each step below names (e.g. `lambda state: workflow_state.<fn>(state, ...)`), applied to freshly re-read state rather than to a snapshot taken before the lock was acquired.

Enter `AWAITING_PLAN_APPROVAL` or `AWAITING_TECHNICAL_APPROVAL`
(`docs/ai-workflow/MILESTONE_WORKFLOW.md`) for the stage named in
`$ARGUMENTS` (`plan` or `implementation`), for the work item also named in
`$ARGUMENTS` or, if omitted, `active_work_item_id`.

`<bundle_dir>`/`<feedback_dir>` below resolve per
`docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
(`workflow_fingerprint.resolve_bundle_dir`/`resolve_feedback_dir`).

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
1. **Confirm gate reachability**: read `<feedback_dir>/REVIEW_FEEDBACK.md`'s
   most recently reviewed round status and bundle ID
   (`workflow_fingerprint.parse_review_feedback_binding_fields`) —
   missing/mismatched `Reviewed bundle ID:`/`Reviewed base commit:`/
   `Work item:` fields are not fatal to reading the file (an
   `EXTERNAL_APPROVE` basis simply becomes unreachable, per step 3), but
   report the mismatch naming both values (`WFR-03`).
   **Implementation stage only — durable `BLOCK`-verdict pin, defense in
   depth** (`D2a`, `WF8c` item (a)): recompute the current implementation-
   stage `bundle_id` now (the same recomputation step 2 repeats and
   displays; reuse this value through steps 1-3 of this invocation rather
   than recomputing it a third time). If the feedback's `status` is
   exactly `BLOCK`: call `workflow_state.state_transaction(repo_root,
   lambda state: workflow_state.record_technical_review_block_pin(state,
   work_item_id, bundle_id=<the current bundle_id>,
   review_content_id=<the current implementation-stage review_content_id>,
   now=<now>))` and persist the returned state — idempotent, exactly as
   `/apply-implementation-review` step 1's own writer call, present here
   only as a second, independent recording path in case that command's
   own write was never reached. Then compute `pinned =
   workflow_state.is_technical_review_block_pinned(work_item, <the current
   bundle_id>)` (re-reading the work item after the call above, so a pin
   just recorded is already reflected) and pass it as `pinned_block` to
   both `technical_approval_gate_reachable` below and `resolve_approval_basis`
   in step 3. Plan stage: `pinned_block` is never computed or passed — D2a
   is an implementation-stage-only mechanism.
   Call
   `workflow_state.approval_gate_reachable(status)` for the plan stage on a
   `"1"` item (`plan_approval_gate_reachable(...)` on a `"2.1"` item), or
   `workflow_state.technical_approval_gate_reachable(...,
   pinned_block=pinned)` for the
   implementation stage. The latter's `protected_path_dirty` argument is
   `workflow_state.any_protected_path_dirty(...)` (`WF4a-iii`), called with
   the implementation-stage classification
   (`workflow_fingerprint.load_implementation_stage_classification(...)`) —
   `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` dirtiness never blocks this,
   by construction of that classification. Its
   `head_matches_reviewed_implementation_head` argument (`WF4c`,
   D-Approval-Commits, revised `WF8B-003`) is **never** a bare
   `work_item["reviewed_implementation_head"] == <live HEAD SHA>` equality
   — that bare form was proven permanently self-invalidating (a mandatory
   post-generation durability commit is always one commit ahead of the
   value it just wrote, re-breaking the equality on every round; see
   `docs/ai-workflow/WORKFLOW_V2_PLAN.md`'s "WF8b finding disposition
   (revision 27 → 28)"). Call
   `workflow_state.implementation_provenance_interval_reachable(repo_root,
   work_item, base_commit)`, which is `True` exactly when
   `workflow_state.verify_implementation_provenance_interval(...)` finds a
   valid interval: live HEAD is exactly the discovered current
   `Workflow-Bundle-Generation-Record: <work_item_id>/<implementation_revision>`
   commit `T` (never merely a descendant of it), `reviewed_implementation_head`
   is reachable from `T` via `T`'s own first-parent chain, every commit
   strictly between them classifies implementation-stage excluded-only,
   and `T` itself passes its own ordinary-role commit contract (touches
   only `WORKFLOW_STATE.json`, changes only the five allowed fields,
   carries exactly the two-trailer ordinary set). On refusal, call
   `verify_implementation_provenance_interval` directly and report its
   raised exception's message — it names the concrete reason (no record
   commit found, a further unrecorded commit landed past `T`,
   `reviewed_implementation_head` not an ancestor, a merge/non-first-parent
   interval, a protected path inside the interval, or a malformed record
   commit) rather than a bare boolean. A `None` `reviewed_implementation_head`
   (nothing has ever generated a bundle for this work item) is caught the
   same way, by `BundleGenerationRecordNotFoundError`. A `BLOCK` status, or
   an unmet additional condition, stops here — report why, do not proceed.
   *Recovered/superseded (`Workflow-Supersedes`) generation-record commits
   are not yet implemented — only the ordinary (single ordinary-role
   terminal commit) case is reachable; a work item that needs to recover
   from an ambiguous or malformed record commit requires a fresh
   `record_bundle_generation` round, not an automatic recovery path.*
2. **Recompute fresh**: `bundle_id` over the current bundle and the
   stage-appropriate `review_content_id` (`scripts/workflow_fingerprint.py`)
   over the working tree. Display both, and the protected/excluded path
   lists, to the user. **Local worktree/HEAD staleness check**
   (`D-Bundle-Manifest`, resolves `OPUS-R6-016`): this command runs inside
   a real, current worktree, so call
   `workflow_fingerprint.assert_local_generation_matches(repo_root,
   <bundle_dir>/MANIFEST.md)` and stop, naming both the recorded and
   current worktree_root/HEAD, on a `WorktreeOrHeadMismatchError` — this
   is the actual first-party Milestone-8 incident (a stale bundle read
   from a different worktree). Never skip this because the recomputed
   `bundle_id` happens to still match; the two checks catch different
   failure modes.
3. **Resolve the basis**: call `workflow_state.resolve_approval_basis(...)`
   with the feedback round's status/bundle_id, the freshly recomputed
   current bundle_id, this turn's literal `user_confirmation` text (if the
   user has not supplied it this turn, ask for it naming the exact
   `work_item_id` and stage, then stop and wait — never guess it),
   `work_item_id`, `stage`, and — implementation stage only — step 1's
   `pinned_block` (`D2a`, `WF8c` item (a)): the same positive-membership
   fact already passed to `technical_approval_gate_reachable`, so a
   pinned bundle refuses through this call too even if some other path
   ever reached step 3 without step 1's own gate check. Plan stage: never
   passed (D2a is implementation-stage-only). `BlockCannotApproveError`/
   `UserConfirmationRejectedError` stop the command; report the concrete
   reason.
4. **Build the record**: `workflow_state.build_approval_record(...)`, with
   `reviewed_content_commit` left unset for the plan stage (permanently
   null, `D-Approval-Commits`/`GPT-R9-006`) and set to the current
   `reviewed_implementation_head` for the implementation stage.
4a. **Plan stage only — resolve the complete commit member set, before any
    durable mutation** (`D-Approval-Commits`' "Conditional fifth commit
    member", `GPT-R67-001`; generalized beyond `workflow-v2-1-core`'s own
    case). **Scope note, read before assuming this retires anything**:
    steps 4a/6/6a/6b below generalize the "Conditional fifth commit
    member" contract's three numbered conditions plus the specific
    hardening properties `D-Approval-Commits` revision 54/56 named
    (Git index-isolation, pinned-blob verification, a committed-path-set
    assertion, a plain pathspec-free commit, and a defined rollback) —
    they do **not** carry the complete failure-atomicity transaction
    (a durable, crash-resumable journal; the three-way `COMMITTED`/
    `NOT_COMMITTED`/`AMBIGUOUS` outcome classifier sourced from durable
    Git state rather than process exit status; index-pinned-blob
    materialization) that missing-test item 347 additionally requires,
    by its own explicit text, *before* `workflow-v2-1-core`'s own
    "Bootstrap plan-approval procedure" may be retired. That procedure's
    own stated condition — "until the command file is updated" — is
    therefore **not yet satisfied by this revision**: it remains the
    sole sanctioned path for `workflow-v2-1-core`'s own plan-stage
    approvals. This revision closes the acute defect item 347 was
    already tracking (a real, reproduced `/approve-review plan
    v2-1-dry-run` failure — see `docs/ai-workflow/dry-run/
    WF8B_SCENARIOS.md`'s S5 section) for every *other* `"process"` work
    item, and narrows, but does not close, item 347 itself. Call
    `workflow_fingerprint.resolve_plan_stage_approval_commit_paths(repo_root,
    work_item_id, state_path=Path("docs/ai-workflow/WORKFLOW_STATE.json"))`.
    This returns the plan doc, registry JSON, mapping file and
    `WORKFLOW_STATE.json` (four members) plus, conditionally, this work
    item's own `<work_item_id>-artifacts.json` declaration as a fifth —
    included exactly when it is both pending (its working-tree bytes
    differ from `HEAD`) and fresh (byte-identical to the copy the
    just-recomputed bundle already captured). `StaleArtifactsDeclarationError`
    means the declaration changed again after the bundle was generated —
    stop and report it, naming both paths; do not stage, do not commit,
    do not write approval state (identical in kind to a mismatched
    `bundle_id`/`review_content_id` refusal one step earlier).
    `MissingWorkItemArtifactsDeclarationError` here means this work item's
    own declaration is genuinely absent from the working tree, not merely
    uncommitted — the same fail-closed error step (2)'s recomputation
    already raises for that case, never masked. If a fifth member is
    resolved, capture its pinned sha256
    (`resolve_plan_stage_approval_commit_paths`'s own return value) for
    step 6's re-verification. Implementation stage: unchanged, no
    resolution step — its four members are fixed.
5. **Write it**: capture `docs/ai-workflow/WORKFLOW_STATE.json`'s current
   working-tree bytes first (`pre_write_bytes` — needed only if step 6/6a
   fails and step 6b's rollback runs). Then call
   `workflow_state.apply_plan_approval(...)` or `apply_technical_approval(...)`,
   and persist the returned state to `docs/ai-workflow/WORKFLOW_STATE.json`.
   This is this invocation's own first durable mutation — step 4a already
   ran and passed, so a plan-stage approval never reaches this write with
   an unresolved or stale fifth member.
6. **Create the approval commit**:
   - Plan stage: stage *exactly* step 4a's resolved member set via
     `workflow_state.stage_plan_approval_commit_paths(repo_root, paths)`
     — never `git add -A`/`git add .`. This call itself checks, in order:
     `DirtyIndexBeforeStagingError` if the Git index already differs from
     `HEAD` *before* it stages anything of its own (an unrelated
     already-staged path with real changed content — this work item's own
     leftover, or a concurrent work item's write, D1 — caught earlier and
     with a clearer diagnostic than discovering it only after staging;
     never this work item's own legitimate pending intent-to-add paths
     from `/milestone-plan`'s own staging step, and never a re-staged
     path whose content is byte-identical to `HEAD`, since neither
     produces a distinguishable index state at all); then, after staging,
     `UnexpectedStagedPathSetError` if the resulting staged diff still
     names any path outside the resolved set. Either means stop, report
     it, and run step 6b's rollback rather than let a pathspec-free
     commit absorb the unrelated path silently. If a fifth member was
     resolved, immediately call
     `workflow_state.verify_staged_blob_sha256(repo_root, path,
     pinned_sha256)` to close the race window between resolution and
     staging — `StagedBlobMismatchError` triggers the same stop-and-
     rollback. Then create **one plain, pathspec-free `git commit`** (no
     trailing `-- <paths>`: the whole point of the staged-set assertion
     just above is that the index is now verified exactly right, and a
     pathspec-limited commit would instead re-read current working-tree
     bytes for those paths rather than the verified staged/pinned ones)
     carrying `Workflow-Plan-Approval: <full review_content_id>` +
     `Workflow-Work-Item: <id>` trailers.
   - Implementation stage: unchanged — a metadata-only commit (zero
     production/test changes) carrying `Workflow-Technical-Approval:
     <full review_content_id>` + `Workflow-Work-Item: <id>`.
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
    approval. Plan stage: also call
    `workflow_state.assert_committed_path_set_matches(repo_root, commit,
    paths)` (step 4a's resolved set) — the commit's own changed-path set
    relative to its parent, a structural check distinct from step 6's
    pre-commit staging checks: a pre-commit/commit-msg hook editing and
    re-staging a file *after* those checks ran but before `git commit`
    wrote the final tree would defeat them without this.
    `CommittedPathSetMismatchError` runs step 6b. Plan stage, fifth
    member present: also call
    `workflow_state.verify_committed_blob_sha256(repo_root, commit, path,
    pinned_sha256)` as further defense in depth, isolating exactly which
    member diverged if it ever disagrees with the checks above. Any
    failure here runs step 6b.
6b. **Recovery, plan stage only, on any failure from step 6 onward**
    (`workflow_state.rollback_plan_approval_write(repo_root, state_path,
    pre_write_bytes, commit_created=...)`): `git reset` back to the
    commit immediately before this invocation's own commit if one was
    created (`commit_created=True` — undoes exactly that one commit,
    never an earlier one), or a bare `git reset` if no commit was created
    yet (`commit_created=False`); then restore
    `docs/ai-workflow/WORKFLOW_STATE.json`'s working-tree bytes to
    `pre_write_bytes` exactly. Leaves the repository byte-identical to
    its state immediately before step 5's write — no partial commit, no
    partial state write, safe to retry from a fresh operator session.
    Report the original failure, not a generic one, after recovery
    completes. Implementation stage has no equivalent step — its write
    set was never widened by step 4a, so its existing failure surface is
    unchanged.
7. Report the new phase (`IMPLEMENTING` or `AWAITING_FUNCTIONAL_REVIEW`) and
   **stop**. Never chain into the next state's actions in the same
   invocation.
