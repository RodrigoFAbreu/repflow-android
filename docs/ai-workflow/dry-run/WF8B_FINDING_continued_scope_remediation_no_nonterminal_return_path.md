# WF8b finding — continued-scope remediation has no non-terminal return path

**Status:** BLOCKING for WF8b. Discovered while closing out the functional
review of the `WF8B_S1_FINDING_review_content_id_not_generalized.md`
remediation (implementation revision 4), immediately after the user gave
explicit functional acceptance of that remediation's five checks and
before any state was written to record that acceptance.

**Not tied to a numbered scenario.** This was found in the gap between
"remediation for S1's blocking defect is technically approved and
functionally clean" and "resume S1," not during execution of any of the 17
scenarios in `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`.

## Context

`WF8B_S1_FINDING_review_content_id_not_generalized.md` required (its item
9) only that its own fix be "independently reviewed and approved" before
S1 reruns. That fix was implemented as continued `WF8b` scope (no new
checkpoint id), and — because it was driven through the ordinary
plan-review → implementation-review → technical-approval machinery
`workflow-v2-1-core` already uses for every other checkpoint — it
automatically walked `workflow-v2-1-core`'s own `phase` field through
`IMPLEMENTING` → `SELF_REVIEWING_IMPLEMENTATION` →
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` → (five `GPT-R30`–`GPT-R34`
rounds) → `AWAITING_TECHNICAL_APPROVAL` → (`/approve-review
implementation`) → `AWAITING_FUNCTIONAL_REVIEW`, exactly like a genuine
terminal implementation round would.

A functional-review checklist for that fix was then prepared (five
checks, all independently re-verified live), and the user has now
explicitly accepted those functional results. The next state-machine step
as currently documented (`docs/ai-workflow/MILESTONE_WORKFLOW.md`) is:

```
AWAITING_FUNCTIONAL_REVIEW → AWAITING_USER_ACCEPTANCE
                             → /accept-milestone → MILESTONE_COMPLETE
```

But `workflow-v2-1-core`'s checkpoint `WF8b` — the manual multi-session
dry run itself — is still `IN_PROGRESS` and unexecuted (S1 blocked before
any mutation; S2–S17 have never run). Walking the documented path to its
end would mark the entire 17-checkpoint `workflow-v2-1-core` item
`MILESTONE_COMPLETE` while its own last checkpoint has not been attempted.

## Root cause — exact code path

`scripts/workflow_state.py`:

- **`apply_technical_approval` (lines 1820–1831)** — the `/approve-review
  implementation` state-write step. Unconditionally sets `work_item["phase"]
  = "AWAITING_FUNCTIONAL_REVIEW"`. There is no branch, parameter, or check
  distinguishing "this technical approval closes out the item's own last
  checkpoint" from "this technical approval is for a continued-scope fix
  while a checkpoint remains outstanding." Every implementation round —
  ordinary or continued-scope — lands in the identical phase.
- **`complete_work_item` (lines 1538–1563)**, the `/accept-milestone`
  state-write step — checks only `incomplete_children` (other work items
  naming this one as `parent_work_item_id`). It performs **no check at all**
  against this item's own `registry`/`checkpoints` completeness before
  setting `phase = "MILESTONE_COMPLETE"`.
- **No `*_gate_reachable` function exists for `AWAITING_USER_ACCEPTANCE`**
  (confirmed by grep — `approval_gate_reachable`,
  `technical_approval_gate_reachable`, and `plan_approval_gate_reachable`
  exist for the other three hard gates; there is no fourth). Unlike every
  other hard-gate exit, nothing in code currently enforces that
  `/accept-milestone` is even called from the right phase — the only
  documented guard is `accept-milestone.md`'s own prose ("Only run this
  after the user has explicitly accepted the milestone
  (`AWAITING_USER_ACCEPTANCE` exit condition)"), which is unverified by
  any test and unenforced by any function.

Both of these are real, live, and currently reachable: `phase` for
`workflow-v2-1-core` is `AWAITING_FUNCTIONAL_REVIEW` right now, and nothing
in the existing code would refuse a `complete_work_item(state,
"workflow-v2-1-core", now=...)` call today.

## Why this matters

- `select_next_checkpoint(work_item, registry)` (same file, lines
  835–893) — the exact function `/milestone-implement`/`/bootstrap-
  workflow-v2` already use to pick the next checkpoint — already answers
  "is this item's own registry actually done?" correctly and for free:
  called against the current live state, it returns `"WF8b"` (not `None`),
  because `WF8b` is absent from the `checkpoints` map and therefore not
  `COMPLETE`. This is the discriminator the phase-transition logic is
  missing, not a new concept.
- If `/accept-milestone workflow-v2-1-core` were invoked next (a
  plausible-looking next step from `AWAITING_FUNCTIONAL_REVIEW`, since
  nothing in the tooling currently refuses it), the item would become
  `MILESTONE_COMPLETE`, `docs/ROADMAP.md`/`docs/ACTIVE_MILESTONE.md` would
  be updated to reflect a finished process milestone, and the deletion
  condition in `/bootstrap-workflow-v2`'s own header ("retired — deleted —
  only at this work item's own `MILESTONE_COMPLETE`") would become
  satisfiable while `WF8b` — the very thing that command exists to drive
  — was never executed. This would silently discard the dry run's whole
  purpose and require a manual revert to undo.
- Every future continued-scope round (this will not be the only one —
  `WF8b`'s own scenarios S8/S10 explicitly model further remediation
  rounds happening mid-dry-run) hits the identical gap.

## Confirmation: repository and workflow state unchanged while this finding was written

- `git status --short`: clean before and after, except this new file
  itself.
- `docs/ACTIVE_MILESTONE.md`: **not modified** this session (the functional
  review checklist section added in the immediately prior session is
  untouched; no "outcome"/acceptance note was added, per explicit
  instruction).
- `docs/ai-workflow/WORKFLOW_STATE.json`: **not modified.**
  `work_items["workflow-v2-1-core"].phase` remains
  `AWAITING_FUNCTIONAL_REVIEW`; `technical_approval` unchanged
  (`implementation_revision` 4, `reviewed_content_commit` `7bef596`);
  `functional_acceptance_status` remains `null`; `checkpoints.WF8b` remains
  absent (not `COMPLETE`); `active_work_item_id` remains `v2-1-dry-run`,
  untouched throughout the entire remediation (the continued-scope fix was
  driven directly against `workflow-v2-1-core` by explicit id, the same
  way `/bootstrap-workflow-v2` always does, never through the active
  pointer — so no pointer restoration was ever needed for this specific
  round; a future round that *does* need to borrow the active pointer is
  exactly why the design below records, but does not itself restore,
  `active_work_item_id` at open time).
- `/accept-milestone` was **not** invoked (it is user-only,
  `disable-model-invocation: true`, and was not run).
- `WF8b`'s scenario S1 was **not** resumed or attempted.

## Minimum required remediation scope (proposed design, not implemented this session)

### 1. A distinct, non-terminal phase

Add `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` to the phase enum (next to
`AWAITING_FUNCTIONAL_REVIEW` in the list at
`scripts/workflow_state.py:174-176` and in
`docs/ai-workflow/MILESTONE_WORKFLOW.md`'s hard-gates list). It is entered
**instead of** `AWAITING_FUNCTIONAL_REVIEW` whenever the just-approved
implementation round is not the item's terminal one.

### 2. The exact branch point

`apply_technical_approval` (`scripts/workflow_state.py:1820-1831`) gains a
`registry: dict` parameter (the caller — `/approve-review implementation`
— already has this item's registry loaded for other purposes). After
writing `technical_approval` as today, decide the phase by asking whether
every checkpoint in `registry["checkpoints"]` is `COMPLETE` in the
resulting `work_item["checkpoints"]`:

- **Terminal** (every checkpoint `COMPLETE`, i.e.
  `select_next_checkpoint(work_item, registry)` returns `None`): phase
  becomes `AWAITING_FUNCTIONAL_REVIEW`, exactly as today — no behavior
  change for every checkpoint this milestone has already closed out this
  way (regression-critical: this is the common case and must stay
  byte-identical).
- **Non-terminal** (`select_next_checkpoint` returns a checkpoint id, or
  raises `NoCheckpointReadyError` — both mean at least one checkpoint
  remains outstanding): phase becomes
  `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` instead.

This reuses `select_next_checkpoint` as the sole discriminator — no new
"is this checkpoint special" concept, no reliance on `WF8b`'s own
absent-from-map convention beyond what already exists.

### 3. A new, additive evidence record — never confusable with the real thing

New field on the work item, `scoped_remediation_acceptance` (a **list**,
since a single checkpoint like `WF8b` may need more than one continued-scope
round before it finally completes — matching `IMPLEMENTATION_SUMMARY.md`'s
own five-round history for just this one fix). Each entry:

```json
{
  "outstanding_checkpoint_id": "WF8b",
  "active_work_item_id_at_open": "v2-1-dry-run",
  "technical_approval_review_content_id": "<the technical_approval.approved_review_content_id this round approved>",
  "status": "CURRENT",
  "user_confirmation": "<verbatim>",
  "recorded_at": "<timestamp>"
}
```

`plan_approval`/`technical_approval`/`functional_acceptance_status` are
never written or touched by this mechanism — they remain reserved for the
item's own eventual, genuinely terminal round, so a reader can always tell
the two apart by which field is populated, never by phase alone.

### 4. A new command: `/accept-scoped-remediation [work_item_id]`

Mirrors `/accept-milestone`'s user-only construction exactly:

- `disable-model-invocation: true`.
- Refuses without literal current-turn confirmation text naming the exact
  `work_item_id` and a stage string distinct from every existing one —
  `stage="scoped_remediation"` — via
  `workflow_state.validate_user_confirmation(text, work_item_id=...,
  stage="scoped_remediation")`. This is what makes the two acceptances
  textually non-interchangeable: confirmation text written for
  `stage="acceptance"` (final) can never satisfy
  `stage="scoped_remediation"`, and vice versa.
- **Entry guard**: refuses unless
  `work_items[work_item_id].phase == "AWAITING_SCOPED_REMEDIATION_ACCEPTANCE"`
  exactly — the first phase-reachability check this family of commands
  would have (see item 6 below).
- **Exit**: appends the record from §3, sets `phase` back to
  `IMPLEMENTING`, leaves `current_checkpoint_id`/`checkpoints` untouched
  (the outstanding checkpoint's own status is whatever it already was —
  this command does not complete it), leaves `active_work_item_id`
  untouched (recorded as evidence in §3, not restored, since restoring is
  only needed if a future round actually repoints it).
- Report and stop: next action is resuming the outstanding checkpoint
  (named in the record) in a fresh session, per that checkpoint's own
  resumption rules (for `WF8b`, `/bootstrap-workflow-v2` with S1 rerun).

### 5. Identifying "the parent checkpoint and the interrupted synthetic item"

Both are captured as plain evidence fields at the moment
`AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` is entered (§2's branch already
has `select_next_checkpoint`'s return value in hand — thread it straight
into `technical_approval`'s caller so `/approve-review implementation` can
pass it to whatever writes §3's record), never re-derived later by
convention. This generalizes correctly even for a future case where a
continued-scope fix *does* need to temporarily repoint
`active_work_item_id` — the recorded `active_work_item_id_at_open` is what
a resume step would restore, rather than inventing a new marker-file
convention per occurrence.

### 6. Keeping the real gate distinguishable — and closing the adjacent gap

Two states, two commands, two confirmation-stage strings, and (new)
matching phase-entry guards on **both** sides:

- `/accept-scoped-remediation` requires phase ==
  `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` (new, per §4).
- `/accept-milestone` should gain the equivalent guard it currently lacks
  entirely: refuse unless phase == `AWAITING_USER_ACCEPTANCE` (a new
  `milestone_complete_gate_reachable`-style check, mirroring
  `technical_approval_gate_reachable`/`plan_approval_gate_reachable`'s
  existing pattern). This is not optional scope-creep — it is the same
  defect class (`complete_work_item` currently runs from *any* phase with
  no code-level refusal at all) and leaving it unfixed means a stray
  `/accept-milestone` invocation from `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE`
  would hit exactly the failure mode this finding exists to prevent, just
  routed around the new phase instead of through it.

### 7. Acceptance criteria and tests

- `apply_technical_approval`: existing tests (all currently-passing
  `workflow_state_test.py` cases exercising this function) must still pass
  unchanged — the terminal branch is byte-identical behavior. New tests:
  a registry with one incomplete, dependency-satisfied checkpoint routes
  to `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE`; a registry with an
  incomplete checkpoint blocked on unmet dependencies (the
  `NoCheckpointReadyError` case) also routes there, not to
  `AWAITING_FUNCTIONAL_REVIEW`; a fully-complete registry still routes to
  `AWAITING_FUNCTIONAL_REVIEW` (regression guard, explicit negative case).
- `/accept-scoped-remediation`: model-invocation refusal (mirrors
  `/accept-milestone`'s own covered case); missing/mismatched/wrong-stage
  confirmation text refusal; wrong-phase refusal (invoked from
  `IMPLEMENTING`, `AWAITING_FUNCTIONAL_REVIEW`, and
  `AWAITING_USER_ACCEPTANCE` — three distinct negative cases, not one);
  successful exit writes exactly the fields in §3 and nothing else,
  returns phase to `IMPLEMENTING`, leaves `checkpoints`/
  `current_checkpoint_id`/`active_work_item_id`/`plan_approval`/
  `technical_approval`/`functional_acceptance_status` byte-identical to
  before the call.
- `/accept-milestone`: new negative case — invoked while phase ==
  `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` is refused by the new
  `milestone_complete_gate_reachable` guard, naming the actual phase and
  the required one; existing `AWAITING_USER_ACCEPTANCE` positive-path
  tests must still pass unchanged.
- One integration/regression test reproducing this exact finding's
  original scenario: an item with `technical_approval` freshly approved
  while a registry checkpoint remains incomplete must **never** be able
  to reach `MILESTONE_COMPLETE` via any documented command sequence
  without first passing through `/accept-scoped-remediation` and
  returning to `IMPLEMENTING`.
- A fresh-session interruption case: `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE`
  is re-derivable from disk alone (phase field + the new list field) with
  no reliance on conversational memory, matching every other hard gate's
  resumability guarantee already proven elsewhere in this milestone.

## Next step

Route this design through the normal review channel — a plan revision
adding a new checkpoint (or continued scope on an existing one) for this
fix, reviewed and approved independently, before it is implemented. Once
landed and independently approved, this session's own pending functional
acceptance (already given by the user, evidenced only in this
conversation right now, not yet written to any file) should be re-recorded
through the new `/accept-scoped-remediation` mechanism rather than through
any workaround — do not backfill it by hand-editing `WORKFLOW_STATE.json`
once the mechanism exists, for the same reason no other approval record in
this workflow is ever hand-edited.
