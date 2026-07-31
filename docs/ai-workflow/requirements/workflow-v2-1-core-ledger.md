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
