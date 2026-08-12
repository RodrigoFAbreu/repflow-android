# `v2-1-dry-run` Requirements Ledger

Mutable, human-readable execution record for the synthetic `v2-1-dry-run`
work item (WF8b). Physically separate from the immutable, machine-readable
`v2-1-dry-run-mapping.json` in this same directory (D4b): the mapping file
is the approved requirement↔checkpoint binding and is plan-stage protected
(editing it changes `review_content_id`); this ledger lives under
`docs/ai-workflow/requirements/`, plan-stage and implementation-stage
excluded for this item — see
`docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`. Editing this file
never stales either approval.

Each checkpoint appends one section here as it completes, per
`/milestone-implement`'s `[2.1 step 1e]` narrative-record requirement for a
process item. `WORKFLOW_STATE.json`'s `checkpoints[id]` remains the sole
record of checkpoint status (D-Registry); nothing here overrides it.

## `S-CP1` — Scratch checkpoint 1: create dry-run scratch file A

- **Implementation evidence:** created
  `docs/ai-workflow/dry-run/scratch/a.txt`, a single-line marker file, per
  the plan's checkpoint acceptance criterion (`v2-1-dry-run-plan.md`'s
  registry table row, WFR-DRY-1).
- **Verification results:** file existence and one-line content confirmed
  directly (`cat docs/ai-workflow/dry-run/scratch/a.txt`); no automated
  test beyond this, per the plan's own "Missing tests: none beyond the
  trivial file-existence check" self-review note — this item's purpose is
  proving the real command surface, not payload logic.
- **Review findings:** none yet — pending this checkpoint's own review
  round (folded into S7's first implementation-stage bundle, once all
  three scratch checkpoints are `COMPLETE`).
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).

## `S-CP2` — Scratch checkpoint 2: create dry-run scratch file B (left dirty for S13)

- **Implementation evidence:** created
  `docs/ai-workflow/dry-run/scratch/b.txt`, a single-line marker file, per
  the plan's checkpoint acceptance criterion (`v2-1-dry-run-plan.md`'s
  registry table row, WFR-DRY-2). The file and this checkpoint's
  `IN_PROGRESS` transition were left uncommitted at the end of a prior
  session (WF8b S6's second session) specifically to set up **S13**
  (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`) — same-worktree dirty
  resume. This session resumed it: `[2.1 step 1a]`
  `implementing_entry_reachable` returned `True`; `[1b]`
  `select_next_checkpoint` returned `S-CP2`, matching the already-`IN_PROGRESS`
  `current_checkpoint_id` (resume, not fresh start); `[1c]`
  `verify_dirty_resume_safety` returned with no exception (same worktree
  that started the work, matching `WORKTREE_IDENTITY.json` entry) —
  S13's own success-path claim.
- **Verification results:** file existence and one-line content confirmed
  directly (`cat docs/ai-workflow/dry-run/scratch/b.txt`); no automated
  test beyond this, per the plan's own "Missing tests: none beyond the
  trivial file-existence check" self-review note.
- **Review findings:** none yet — pending this checkpoint's own review
  round (folded into S7's first implementation-stage bundle, once all
  three scratch checkpoints are `COMPLETE`).
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).
