---
description: Implement the approved plan checkpoint by checkpoint, then stop for external implementation review.
argument-hint: [work-item-id]
---

Enter the `IMPLEMENTING` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.
Requires an approved plan (from `/milestone-plan` + `/apply-plan-review`).

0. **Dual-mode branch** (Workflow v2.1, `WF2`): resolve the target work
   item -- the id named in `$ARGUMENTS`, or `active_work_item_id` from
   `docs/ai-workflow/WORKFLOW_STATE.json` if omitted (refuse with a named
   error if neither resolves to an existing, non-terminal `work_items`
   entry -- never guess) -- and read its `governing_workflow_version`.
   - **`governing_workflow_version: "1"`**: steps 1-5 below execute
     exactly as written, with no `WORKFLOW_STATE.json`/
     `WORKFLOW_CONFIG.json` reads or writes beyond the one just
     performed -- this branch is v1-inert by construction, verified by a
     golden-output test (`WF8a-ii`).
   - **`governing_workflow_version: "2.1"`**: step 1 is replaced entirely
     by the resumable, one-checkpoint-per-invocation sequence marked
     **[2.1 step 1]** below -- Workflow v2.1 core's own
     "one-resumable-checkpoint session model", the same discipline the
     bootstrap command uses for its own one work item. Steps 2-5 execute
     unchanged, entered only on whichever invocation first observes every
     registry checkpoint `COMPLETE` -- never in the same invocation that
     completed the last checkpoint (this command never loops across a
     checkpoint-vs-wrap-up boundary any more than it loops across
     checkpoints).

1. For each checkpoint in the approved plan, in order:
   - implement it, following `CLAUDE.md`/`AGENTS.md`/`.github/copilot-instructions.md`/
     `.github/instructions/*` for layer boundaries, migrations, and enum
     persistence rules;
   - run the narrowest relevant check (single test class/method, not the
     full suite);
   - review the resulting diff and fix confirmed defects;
   - update `docs/ACTIVE_MILESTONE.md` (checkpoint complete, verified state);
   - create the authorized intermediate commit for that checkpoint;
   - continue to the next checkpoint without stopping, unless a stop
     condition from `AGENTS.md` applies (ambiguous product behavior,
     architecture change, new dependency category, unapproved schema
     migration, destructive operation, repeated verification failure,
     unrelated working-tree changes).

**[2.1 step 1] Resumable single-checkpoint implementation** (D-Selection,
D3's `IN_PROGRESS`/`COMPLETE` state writers, D3's worktree-scoped
dirty-resume rule, `WF2`):

1a. **Entry validation**: call
    `workflow_state.implementing_entry_reachable(repo_root, work_item,
    base_commit)`. `False` stops here -- report whether `plan_approval` is
    missing/`STALE`, or the approval commit is not an ancestor of HEAD;
    never proceed on a stale or unreachable plan approval. Runs on every
    invocation, not only the first (missing-test item 9: reachability
    must hold identically at checkpoints 1, 2, and N).
1b. **Select the checkpoint**: load
    `docs/ai-workflow/registry/<work_item_id>-registry.json` and call
    `workflow_state.select_next_checkpoint(work_item, registry)`
    (D-Selection's rules 1/2/4 -- pure and deterministic, missing-test
    item 28).
    - Returns `None`: every registry checkpoint is already `COMPLETE`.
      Nothing to implement this invocation -- skip straight to step 2
      below (do not re-implement anything, do not re-select).
    - Raises `NoCheckpointReadyError`: stop, report the named blocked
      checkpoint and its unmet dependencies verbatim -- never silently
      idle (rule 4).
    - Otherwise: the returned id is this invocation's checkpoint. If it
      equals `current_checkpoint_id` with status `IN_PROGRESS`, this is a
      **resume** (rule 1); otherwise it is a **fresh start**.
1c. **Resume-safety** (resume only): call
    `workflow_state.verify_dirty_resume_safety(repo_root, work_item_id)`
    before touching anything else. `WorktreeIdentityMissingError`/
    `WorktreeIdentityMismatchError` stop immediately -- report the
    concrete mismatch and require the user to either resume from the
    worktree that actually started this checkpoint's uncommitted work, or
    explicitly discard it and restart from the checkpoint's own
    `start_commit`; never guessed either way (D3, missing-test items 31,
    43, 71).
1d. **Fresh-start bookkeeping** (fresh start only): call
    `workflow_state.transition_checkpoint_in_progress(state,
    work_item_id, checkpoint_id, start_commit=<current HEAD>, now=<now>)`
    and persist the returned state to `docs/ai-workflow/WORKFLOW_STATE.json`,
    then call `workflow_state.write_worktree_identity(repo_root,
    work_item_id, now=<now>)` -- D3's named writer for
    `.ai-review/runtime/WORKTREE_IDENTITY.json`, creating or refreshing
    only this work item's own keyed entry at exactly this transition
    (`OPUS-R6-026`, missing-test item 43).
1e. **Implement exactly that one checkpoint**: follow
    `CLAUDE.md`/`AGENTS.md`/`.github/copilot-instructions.md`/
    `.github/instructions/*` for layer boundaries, migrations, and enum
    persistence rules; run the narrowest relevant check (single test
    class/method, not the full suite); review the resulting diff and fix
    confirmed defects; update the work item's own narrative record
    (`docs/ACTIVE_MILESTONE.md` for a product item; the requirements
    ledger, `WF4b`, for a process item) -- unless a stop condition from
    `AGENTS.md` applies (ambiguous product behavior, architecture change,
    new dependency category, unapproved schema migration, destructive
    operation, repeated verification failure, unrelated working-tree
    changes), in which case stop here instead of committing.
1f. **Commit**: one commit for this checkpoint's changes, carrying
    `Workflow-Checkpoint: <id>` + `Workflow-Work-Item: <work_item_id>`
    trailers (D-Commit-Provenance's exact trailer shape -- this command
    only writes the trailer, it never needs to search for one itself; the
    exact scoped lookup later checks use is
    `workflow_state.discover_checkpoint_commits`, `WF4a-iii`). In the same
    commit, call `workflow_state.complete_checkpoint(state, work_item_id,
    checkpoint_id, registry, now=<now>)` and persist the returned state to
    `docs/ai-workflow/WORKFLOW_STATE.json` -- checkpoint-complete-vs-
    all-complete semantics: `phase` stays `IMPLEMENTING` unless every
    registry checkpoint is now `COMPLETE`, in which case it becomes
    `SELF_REVIEWING_IMPLEMENTATION` as part of this same write. The state
    file is the sole writable record of checkpoint status from this point
    on; the trailer is verification evidence, never a second source of
    truth.
1g. **Stop immediately** -- never continue to the next checkpoint, and
    never continue into step 2 in the same invocation even when this was
    the last checkpoint (a later invocation observes the phase change and
    proceeds from step 2, per this command's own dual-mode note above).
    Report the checkpoint just completed, its commit SHA, and the
    resulting phase. Continuing requires invoking this command again.

2. When all checkpoints are implemented, enter
   `SELF_REVIEWING_IMPLEMENTATION`: review the full milestone diff for
   correctness, layer-boundary violations, missing tests, and
   maintainability. Fix all blocking and important findings.
3. Run the full required verification for the milestone (narrow checks are
   not sufficient at this point): `./gradlew spotlessCheck detekt lintDebug
   testDebugUnitTest`, plus `connectedDebugAndroidTest` if a device/emulator
   is available and the plan touches persistence/migrations. Report exactly
   what ran and its real result — never claim a check passed that did not
   run.
4. Enter `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`:
   - write `<bundle_dir>/IMPLEMENTATION_SUMMARY.md` (what was built,
     per checkpoint, and why);
   - write `<bundle_dir>/TEST_RESULTS.md` (exact commands + results);
   - write `<bundle_dir>/CONTEXT_FILES.txt` with only the unchanged
     docs a reviewer needs;
   - write `<bundle_dir>/REVIEW_REQUEST.md` per
     `docs/ai-workflow/REVIEW_PROTOCOL.md` (stage: `implementation`);
   - run `./scripts/prepare-ai-review.sh <base-sha> implementation
     [work_item_id]`, where `<base-sha>` is the milestone's starting
     commit; `<bundle_dir>` here resolves per
     `docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Bundle location"
     (`workflow_fingerprint.resolve_bundle_dir`);
   - if this work item has a `docs/ai-workflow/WORKFLOW_STATE.json` entry:
     call `workflow_state.record_bundle_generation(state, work_item_id,
     stage="implementation", head=<current HEAD SHA>, now=<now>)` (`WF4c`,
     D-Approval-Commits' sole writer of `reviewed_implementation_head`) and
     persist the returned state — this is what later makes
     `AWAITING_TECHNICAL_APPROVAL` reachable at all; skip this call
     entirely for a work item with no state entry (nothing to track).
5. Report the bundle location and **stop**. This is a hard gate — do not
   mark the milestone accepted, do not start the next milestone.
