# `workflow-v2-3-followups` Requirements Ledger

Mutable, human-readable execution record for the `workflow-v2-3-followups`
work item. Physically separate from the immutable, machine-readable
`workflow-v2-3-followups-mapping.json` in this same directory (D4b): the
mapping file is the approved requirement↔checkpoint binding and is
plan-stage protected (editing it changes `review_content_id`); this ledger
is never a fingerprint input — it resolves under
`docs/ai-workflow/requirements/` in `PLAN_STAGE_EXCLUDED_PREFIXES` and in
this item's own plan-stage approval's `excluded_prefixes`. Editing this
file never stales a plan approval or a technical approval and never
requires a fresh review round.

Each checkpoint appends one section here as it completes: implementation
evidence, verification results, review findings, and (where applicable)
functional-verification outcome. This is a log, not a status source —
`WORKFLOW_STATE.json`'s `checkpoints[id]` remains the sole record of
checkpoint status (D-Registry); nothing here is re-derived from or
overrides it.

## `CP1` — Fix /approve-review plan fifth-member staging bug

- **Implementation evidence, grouped by sub-obligation**
  (`GPT-FUP-R6-O02`):
  - **Fifth-member staging fix (REQ-1/REQ-11)**: merged
    `.claude/commands/approve-review.md` steps 5 and 6.1 into a single
    guarded window (new step, `"Plan stage only — stage every non-state
    approval member, then pin the fifth, if resolved"`) that stages the
    plan doc, registry JSON, mapping file, and the fifth member (if
    resolved) in **one** call to
    `workflow_state.stage_plan_approval_commit_paths`, then pins the
    fifth member's SHA256 inside the same window if one was resolved —
    the exact safe workaround `WORKFLOW_V2_3_FOLLOWUPS.md` documents
    using live, now the command's own documented sequence. Step 6's own
    sub-steps renumbered to two (6.2 state-pin, 6.3 staged-set assertion,
    6.4 commit) plus the unguarded structural assertion; step 6a's
    amend-recovery paragraph reworded to name the merged staging step
    instead of the retired "6.1's staging"; two now-stale numeric ranges
    (`4b/4c/5/6.1-6.4/...` and "hook re-staging a file after 6.1-6.3
    ran") corrected to `6.2-6.4` and "step 5 through 6.3" respectively.
  - **New guard-step label (REQ-1)**: added `"step-5-stage-and-pin"` to
    `PLAN_APPROVAL_ORDINARY_STEPS` in `scripts/workflow_state.py`
    (`LPR-R3-B02`) — a new classified entry, not a repurposed one;
    `"step-5-declaration-pin"`/`"step-6.2-stage-ordinary"` are
    deliberately left in the frozenset, unacquired by any real command
    step, per the plan's stated minimal-change disposition (matching
    `workflow_integration_test.py`'s existing direct-literal call sites
    for both labels).
  - **Trailer final-paragraph sentence (REQ-14 via "Executing this
    plan"/`LPR-R3-B01`)**: added the same "these two lines must be the
    commit message's own final paragraph" sentence
    `milestone-implement.md`/`bootstrap-workflow-v2.md` already state
    verbatim to `approve-review.md` step 6.4's commit instruction, naming
    `discover_plan_approval_commit` as the exact mechanism at risk.
  - **Baseline housekeeping (REQ-15 via `LPR-R3-B01`/`LPR-R3-I01`)**:
    added `fb134ac4f7cdabb7861d170bb61331bb8d9f5a14`
    (`workflow-v2-3`'s own `/accept-milestone` commit, this item's own
    `base_commit`) to `_GRANDFATHERED_WORKFLOW_TRAILER_LOOKALIKE_VIOLATIONS`
    in `scripts/workflow_state_demo_test.py`, with a rationale comment
    matching the set's existing `workflow-v2-1-core` entry's convention;
    added `docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS_PLAN.md` to
    `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`'s
    `implementation_stage.excluded_paths`, matching commit `aaa1247`'s
    precedent for the three sibling paths already there.
  - **Conformance surface (`LPR-R1-B02`)**: updated
    `_GOLDEN_COMMAND_FILE_SHA256["approve-review.md"]` in
    `scripts/workflow_integration_test.py` to the new post-edit hash,
    with a rationale comment naming CP1 and the specific hunks.
  - **Regression coverage**: added
    `TestPlanApprovalCommitTrailerFinalParagraphConformance` (three
    tests: `approve-review.md`/`milestone-implement.md`/
    `bootstrap-workflow-v2.md` each state the final-paragraph
    requirement — new coverage for all three, not just
    `approve-review.md`). In `TestPlanApprovalPermanentSiteEndToEnd`:
    updated the existing happy-path test to drive the merged
    `"step-5-stage-and-pin"` step instead of the retired
    `"step-6.2-stage-ordinary"` (no-fifth-member path unaffected, proven
    unchanged in behavior); added
    `test_five_member_fixture_succeeds_through_the_real_merged_step_5`
    (a genuine five-member fixture — fifth member pending and fresh —
    driven through the real merged step 5 all the way to a committed,
    materialized, journal-closed outcome, including the fifth member's
    committed-blob verification); added
    `test_merged_step_5_still_refuses_on_unrelated_dirty_index` (unrelated
    pre-staged content still raises `DirtyIndexBeforeStagingError` before
    the merged call runs, the guard releases without advancing progress,
    and step 6b's rollback then resets cleanly); added
    `test_new_step_label_classifies_ordinary_and_acquires_the_guard`
    (`plan_approval_step_class("step-5-stage-and-pin")` returns
    `"ordinary"` and `plan_approval_guarded_mutation` successfully
    acquires under it — the pre-existing generic frozenset-iteration test
    covers exhaustiveness but not this specific label).
  - Created this ledger file itself (did not exist before this
    checkpoint).
- **Verification results:** `python3 -m unittest workflow_integration_test
  workflow_state_test workflow_state_demo_test workflow_fingerprint_test
  workflow_fingerprint_demo_test workflow_fingerprint_generalization_test
  workflow_state_completion_obligations_test workflow_test_harness_test`
  — 1181 tests, all green (4 skipped, pre-existing/unrelated).
  `workflow_state_demo_test.py` run standalone — 45 tests, all green: the
  plan's own documented pre-existing red baseline
  (`test_no_new_workflow_trailer_lookalike_violations_beyond_the_grandfathered_set`
  and
  `test_real_implementation_stage_classification_has_no_unclassified_dirty_path`)
  is now clean, as this checkpoint's own baseline-housekeeping sub-
  obligation requires — a plain full pass, no baseline-relative carve-out.
- **Review findings:** none yet — pending this checkpoint's own review
  round.
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).
