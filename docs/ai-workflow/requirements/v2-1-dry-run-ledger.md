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
- **Review findings:** none — covered by S7's first implementation-stage
  self-review (2026-08-15): file content confirmed correct, commit trailer
  uniquely discoverable via `workflow_state.discover_checkpoint_commits`,
  no blocking/important findings.
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
- **Review findings:** none — covered by S7's first implementation-stage
  self-review (2026-08-15): file content confirmed correct, commit trailer
  uniquely discoverable via `workflow_state.discover_checkpoint_commits`,
  no blocking/important findings.
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).

## `S-CP3` — Scratch checkpoint 3: create dry-run scratch file C (left `IN_PROGRESS` in worktree A for S14, then S15)

- **Implementation evidence:** created
  `docs/ai-workflow/dry-run/scratch/c.txt`, a single-line marker file, per
  the plan's checkpoint acceptance criterion (`v2-1-dry-run-plan.md`'s
  registry table row, WFR-DRY-3). The file and this checkpoint's
  `IN_PROGRESS` transition were left uncommitted across two prior sessions
  specifically to set up **S14** (mismatched-worktree refusal, run from a
  temporary worktree B against this checkpoint's real ownership claim,
  refused before any mutation) and **S15** (interrupted checkpoint
  recovery, `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`). This session
  resumed it under `D-Checkpoint-Ownership`: `[2.1 step 1a]`
  `implementing_entry_reachable` returned `True`; `[1b]`
  `select_next_checkpoint` returned `S-CP3`, matching the already-
  `IN_PROGRESS` `current_checkpoint_id`; `[1c]`
  `resolve_checkpoint_ownership` returned `RESUME` with the owner token
  this worktree's own claim record already held (published by the prior
  session's explicit takeover) — proving S14's refusal was specific to
  the foreign worktree, not a general lock, exactly as this scenario's
  own "ownership half" sub-bullets require. `1d` was skipped entirely
  (resume, not fresh start/continue-claim). Per the user's explicit,
  user-gated recovery choice (resume, not discard — S15's own
  effectively-user-gated decision), `1f` then committed `S-CP3` complete
  inside the same `"destructive"` `owner_mutation` guard window
  `complete_checkpoint`'s state write ran in, verified the completion
  durable at `HEAD` via `committed_checkpoint_status`, and only then
  released the claim via `release_checkpoint` — confirmed afterwards that
  no claim record remains for `v2-1-dry-run` under
  `.git/ai-workflow/checkpoint-claims/`.
- **Verification results:** file existence and one-line content confirmed
  directly (`cat docs/ai-workflow/dry-run/scratch/c.txt`); no automated
  test beyond this, per the plan's own "Missing tests: none beyond the
  trivial file-existence check" self-review note.
- **Review findings:** S7's first implementation-stage self-review
  (2026-08-15) found no blocking/important findings, content confirmed
  correct at that time. S8 (2026-08-15, real, `/apply-implementation-review`)
  then exercised a genuine `REVISE` round against this exact file: a
  deliberately-planted defect was committed on top (`b8d4899`, "checkpoint
  2" instead of "checkpoint 3"), reproduced directly against that commit,
  and fixed (`ae7ef4c`), restoring content byte-identical to this
  checkpoint's own original completion commit `ac1df00`. Full record in
  `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`'s S8 outcome note.
- **Functional-verification outcome:** not applicable (process checkpoint,
  no product-facing behavior).
