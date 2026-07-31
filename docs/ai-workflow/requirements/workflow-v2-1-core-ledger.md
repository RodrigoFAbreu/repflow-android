# `workflow-v2-1-core` Requirements Ledger

Mutable, human-readable execution record for the `workflow-v2-1-core` work
item. Physically separate from the immutable, machine-readable
`workflow-v2-1-core-mapping.json` in this same directory (D4b): the mapping
file is the approved requirement↔checkpoint binding and is plan-stage
protected (editing it changes `review_content_id`); this ledger is never a
fingerprint input — see `docs/ai-workflow/requirements/` in
`scripts/workflow_fingerprint.py`'s `PLAN_STAGE_EXCLUDED_PREFIXES`. Editing
this file never stales a plan approval and never requires a fresh review
round.

Each checkpoint appends one section here as it completes: implementation
evidence, verification results, review findings, and (where applicable)
functional-verification outcomes. This is a log, not a status source —
`WORKFLOW_STATE.json`'s `checkpoints[id]` remains the sole record of
checkpoint status (D-Registry); nothing here is re-derived from or
overrides it.

## Checkpoints completed before this ledger existed (WF0–WF2, WF4a-i
through WF4a-iv, WF-M8a, WF-M8b)

This ledger is created at `WF4b`. The ten checkpoints already `COMPLETE` at
that point (`WF0`, `WF1a`, `WF1b`, `WF4a-i`, `WF4a-ii`, `WF4a-iii`,
`WF4a-iv`, `WF-M8a`, `WF-M8b`, `WF2`) have their implementation evidence and
verification results recorded in their own commit messages and
`Workflow-Checkpoint`/`Workflow-Work-Item` trailers (`git log --grep`
against each id remains authoritative for those). They are not
retroactively duplicated here, to avoid a second copy that could drift from
the git history that already carries them. Every checkpoint from `WF4b`
onward appends a section below as it completes.

## `WF4b` — Requirements: immutable mapping file + mutable ledger doc
physically separated (D4b)

- **Implementation evidence:** created this file, physically separate from
  the already-existing `workflow-v2-1-core-mapping.json` (created at
  `WF1b`); no change to the mapping file or the registry.
- **Verification results:** `scripts/workflow_fingerprint_test.py` passes
  unchanged; a new test confirms editing this ledger file does not change
  the plan-stage `review_content_id` (missing-test item 6).
- **Review findings:** none yet — pending this checkpoint's own review
  round.
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).

## `WF4c` — General functional-remediation cycle (D-Functional-Remediation)

- **Implementation evidence:** `scripts/workflow_state.py` gains
  `mark_technical_approval_stale` (the bounded-fix branch's
  stale-before-edit write), `record_bundle_generation`
  (`reviewed_implementation_head`'s sole writer, closing the loop
  `OPUS-R6-013` found — nothing wrote this field before this checkpoint,
  so `AWAITING_TECHNICAL_APPROVAL`'s entry condition was unreachable for
  any real item), `create_remediation_child_work_item` (the broad-
  remediation branch: a distinct `<parent>-remediation-<n>` child work
  item, `n` derived deterministically, never mutating the parent's own
  registry/mapping/checkpoint history), and `incomplete_children` (wired
  into `complete_work_item`, which now refuses outright, naming every
  still-incomplete child, resolving `GPT-R9-016`). `validate_state` gained
  a `DanglingParentWorkItemError` check. `.claude/commands/apply-
  functional-review.md` implements the three-way per-finding branch (no
  code change / bounded / broad); `.claude/commands/milestone-implement.md`
  step 4 and `.claude/commands/apply-implementation-review.md` step 7 now
  call `record_bundle_generation` at the `"implementation"`/`"post-fix"`
  bundle-generation points respectively; `.claude/commands/approve-review.md`
  step 1 now states exactly how `head_matches_reviewed_implementation_head`
  is computed; `.claude/commands/accept-milestone.md` gained step 2a,
  calling `workflow_state.complete_work_item` (the parent-completion
  block) before finalizing acceptance. `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s
  `FIXING_FUNCTIONAL_FINDINGS`/`AWAITING_USER_ACCEPTANCE`/`MILESTONE_COMPLETE`
  sections document the same mechanics. No change to the registry or
  mapping file.
- **Verification results:** `python3 scripts/workflow_state_test.py` —
  174/174 pass (17 new: `TestMarkTechnicalApprovalStale`,
  `TestRecordBundleGeneration`, `TestRemediationChildWorkItem`,
  `TestParentCompletionBlocksOnIncompleteChild`,
  `TestDanglingParentWorkItem`). `python3 scripts/workflow_fingerprint_test.py`
  — 91/91 pass, unchanged. Durability guard re-run after all edits: the
  plan-stage `review_content_id` at `base_commit` is unchanged
  (`03e9698c7c017da85e2f7845a341a55334ec8b3b79544bc9d22f057a9f90bb5a`) —
  every file this checkpoint touched is plan-stage excluded
  (`.claude/commands/`, `docs/ai-workflow/MILESTONE_WORKFLOW.md`,
  `scripts/`).
- **Review findings:** none yet — pending this checkpoint's own review
  round.
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).
