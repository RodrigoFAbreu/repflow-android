# WF8b manual dry-run scenario checklist

**Status:** approved by the user. Not yet executed — S1 is the first
scenario to run, in a later, genuinely fresh session.

**Scope note:** this document is dry-run *execution evidence* for the
`workflow-v2-1-core` checkpoint `WF8b`. It records what will be manually
exercised and why. It does not revise `docs/ai-workflow/WORKFLOW_V2_PLAN.md`,
`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`,
`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`, or any
command file's contract. If a scenario surfaces a real defect in those
artifacts, that is a finding to report back through the normal review
channel — this file only records dry-run intent and, once run, outcomes.

**Isolation:** every scenario below runs against the synthetic work item
`v2-1-dry-run` (`work_item_kind: "synthetic"`, `work_item_type: "process"`,
`governing_workflow_version: "2.1"`), already entered in
`docs/ai-workflow/WORKFLOW_STATE.json` by the prior session's commit
`9317b1c` (`active_work_item_id` repointed here, prior value
`workflow-v2-1-core` saved in `.ai-review/runtime/DRY_RUN_RESUME.json`). The
real `workflow-v2-1-core` record is untouched by any scenario here — the
byte-identical check at cleanup (scenario 17) is what verifies that.

**Legend** (per-scenario "Executed mode"):

- **Real** — actually invoked as a real command in a real session against
  real repository state; this is the behavior WF8b exists to prove, since
  `scripts/workflow_integration_test.py`/`workflow_state_test.py` already
  cover the same transitions as hermetic function calls, not as real
  command invocations with real approval gates and real git commits.
- **Fixture-covered** — noted where a hermetic test already exercises the
  same logic path, so the manual scenario is confirming *real invocation*
  behavior on top of, not instead of, that coverage.
- **User-gated** — requires the user's own literal confirmation text
  (`/approve-review`, `/accept-milestone`) or an externally-pasted review
  verdict (`/record-manual-plan-review`); Claude cannot supply these.
- **Fresh-session-required** — the scenario's own contract (see command
  file) recommends or requires a new session for genuine independence or
  to prove resumability; noted per-scenario.

## Mode summary (all 17 scenarios at a glance)

| # | Scenario | Mode | User-gated | Fresh session |
|---|---|---|---|---|
| S1 | New synthetic work item planning | Real | no | no |
| S2 | Local plan review → APPROVE | Real | no | recommended |
| S3 | Local plan review → REVISE, apply-plan-review, re-review | Real | no | optional |
| S4 | Manual external plan-review recording | Real | no (paste is manual, command itself isn't a gate) | no |
| S5 | Explicit user plan approval | Real | **yes** | no |
| S6 | Checkpoint implementation + fresh-session resume | Real | no | **required** |
| S7 | Implementation review → APPROVE (bundle) | Real | no | recommended |
| S8 | Implementation review → REVISE + remediation | Real | no | no |
| S9 | Functional-review prep + acceptance | Real | **yes (twice)** | no |
| S10 | Functional finding + bounded remediation | Real | **yes** (forced repeat approval) | no |
| S11 | Legacy import/adoption (throwaway item only) | Real | no | no |
| S12 | Approval/content drift detection | Real | n/a (refusal path) | no |
| S13 | Dirty resume, same worktree | Real | no | **required** |
| S14 | Dirty resume, mismatched worktree, refused | Real | no | not required |
| S15 | Interrupted checkpoint recovery | Real | effectively yes (recovery choice) | **required** |
| S16 | Final synthetic work-item completion | Real | **yes** | no |
| S17 | Pointer restoration + cleanup | Real | confirm before commit | no |

Every scenario above is genuinely **Real** — none are satisfied by the
existing hermetic fixtures in `scripts/workflow_state_test.py`/
`workflow_fingerprint_test.py`/`workflow_integration_test.py`, which already
cover the same *logic* as direct function calls. What those fixtures cannot
prove, and what WF8b exists specifically to prove, is that the **real
command surface** (invoked as `/milestone-plan`, `/approve-review`, etc., in
a real session) drives that same logic correctly end to end, including the
three genuinely user-gated approval commands (`disable-model-invocation:
true` on `/approve-review` and `/accept-milestone`) and real session
boundaries. Where a scenario's design intentionally avoids mutating
production state (S11, S14), that constraint is called out in its own
section below, not left implicit.

## Decisions locked in for execution (user-approved, do not revisit silently)

1. **S11 — Milestone 8 stays untouched.** S11 runs only against the
   throwaway synthetic item `v2-1-dry-run-legacy`. Adopting, reopening, or
   changing `milestone-8`'s `governing_workflow_version` is out of scope for
   all of WF8b — Milestone 8 remains accepted and closed under its existing
   functional-review waiver, full stop.
2. **S14 — temporary worktree authorized, with conditions.** Creating a
   second Git worktree solely to test mismatched-worktree refusal is
   approved, provided: an isolated temporary branch/path is used; nothing in
   that worktree modifies the real work item; the command is verified to
   fail closed **before** any mutation; the temporary worktree and branch
   are removed during WF8b cleanup; and creation, refusal evidence, and
   cleanup are all recorded in S14's own section (updated below to reflect
   this explicitly).
3. **S11 — the `/prepare-functional-review` → `docs/ACTIVE_MILESTONE.md`
   concern is not to be silently worked around.** Run the real command
   contract as written. If it incorrectly writes, or attempts to write, the
   real `docs/ACTIVE_MILESTONE.md` for the synthetic item, that is a genuine
   WF8b failure, not a detail to paper over:
   - stop before any destructive or unrelated mutation where that's still
     possible;
   - capture the exact evidence (the attempted write, the diff, the exact
     command step that produced it);
   - create a remediation finding rather than hand-editing around it;
   - fix it through the approved remediation cycle (the same
     validate-then-fix discipline `/apply-plan-review`/
     `/apply-implementation-review` already use elsewhere in this workflow),
     never an ad-hoc workaround applied only for the dry run.

---

## S1 — New synthetic work item planning

- **Purpose**: prove `/milestone-plan`'s `[2.1]` branch (step 1) drives
  `workflow_state.route_work_item` and produces a real plan doc + registry
  + mapping for a `"2.1"` item, then exits to
  `AWAITING_EXTERNAL_PLAN_REVIEW` with a real bundle.
- **Initial phase/state**: `v2-1-dry-run`, `phase: "PLANNING"`,
  `plan_revision: 1`, `checkpoints: {}`, `base_commit: null` (the shell WF8b's
  entry step created — no plan doc exists yet at `plan_path`).
- **Command invoked**: `/milestone-plan v2-1-dry-run` (real).
- **Note on route_work_item branch actually exercised**: because the entry
  already exists (created directly by WF8b's entry commit for
  pointer-persistence, not by a prior `/milestone-plan` run), this invocation
  exercises `route_work_item`'s "advance an existing non-terminal entry"
  branch, not "create fresh." That is itself real, valid coverage — it's
  the same code path `/milestone-plan` takes resuming any milestone already
  present in `work_items` — but it does not independently prove first-time
  creation. First-time creation is already exercised for real by
  `workflow-v2-1-core`'s own history (`WF1a`'s bootstrap) and is
  fixture-covered in `workflow_state_test.py`; not re-derived here.
- **Expected state transition**: `PLANNING` → `SELF_REVIEWING_PLAN` (internal)
  → `AWAITING_EXTERNAL_PLAN_REVIEW`. `plan_revision` stays `1` or advances to
  `2` depending on whether `route_work_item` treats a first real plan write
  as a revision bump — record whichever it actually does as the observed
  behavior, don't presume.
- **Expected files/commits**: `docs/ai-workflow/dry-run/v2-1-dry-run-plan.md`
  created, containing a **small, deliberately scratch checkpoint registry**
  (recommend 3 trivial checkpoints — e.g. touching a throwaway file under
  `docs/ai-workflow/dry-run/scratch/` — sequenced so checkpoint 2 is the one
  later left dirty for S13-S15); `docs/ai-workflow/registry/v2-1-dry-run-registry.json`
  and `docs/ai-workflow/requirements/v2-1-dry-run-mapping.json` created;
  `.ai-review/v2-1-dry-run/` (or flat-path fallback) bundle written;
  `docs/ai-workflow/WORKFLOW_STATE.json` updated. No commit yet — planning
  bundles are not committed until plan approval (S5).
- **Real approval gate**: none yet — this stops at the hard gate for
  external plan review, same as any other `/milestone-plan` run.
- **Fresh-session boundary**: none required for this step itself.
- **Pass/fail evidence**: bundle exists at the reported path; `MANIFEST.md`'s
  recorded `bundle_id`/`review_content_id` match a fresh recomputation;
  registry JSON is a valid topological order (`D-Selection` rule 3).
- **Cleanup**: none this scenario — artifacts feed S2 onward.

## S2 — Local model-independent plan review returning APPROVE

- **Purpose**: prove `/review-plan`'s `local_model_plan_review` role: reads
  the bundle from S1, recomputes fresh, writes `REVIEW_FEEDBACK.md` with the
  required provenance fields, and transitions the ledger.
- **Initial phase/state**: `AWAITING_LOCAL_PLAN_REVIEW` (S1's exit, per
  `/milestone-plan`'s `[2.1]` step 7' — actually verify this: `/milestone-plan`
  itself exits to `AWAITING_EXTERNAL_PLAN_REVIEW`, not
  `AWAITING_LOCAL_PLAN_REVIEW`, per its own text; only `/apply-plan-review`'s
  revised exit step transitions to `AWAITING_LOCAL_PLAN_REVIEW`. **Flag**:
  for a genuinely first plan (no prior `REVISE` round), confirm whether
  `AWAITING_EXTERNAL_PLAN_REVIEW` and `AWAITING_LOCAL_PLAN_REVIEW` are the
  same reachable state for `/review-plan`'s phase guard, or whether
  `/review-plan` is only reachable after at least one
  `/apply-plan-review` round. Resolve this from
  `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s actual state diagram before
  running S2 — do not assume.
- **Command invoked**: `/review-plan v2-1-dry-run` (real). Recommended in a
  fresh session per the command's own text (not a verified precondition).
- **Expected state transition**: → `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
- **Expected files/commits**: `REVIEW_FEEDBACK.md` with `Status: APPROVE`,
  `Reviewer role: local_model_plan_review`, the three binding fields, round
  number, timestamp; `plan_review_stages.local_model_plan_review` recorded in
  `WORKFLOW_STATE.json`. No commit (per command contract, step 8 writes only
  `WORKFLOW_STATE.json`, never the plan/registry/mapping).
- **Real approval gate**: none — this is not a user-authority gate.
- **Fresh-session boundary**: recommended, not required.
- **Pass/fail evidence**: ledger's `local_model_plan_review.review_content_id`
  matches the current recomputed value; phase is exactly
  `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
- **Cleanup**: none — feeds S4 (or S3 if a REVISE detour is inserted first).

## S3 — Local plan review returning REVISE, apply-plan-review, fresh re-review

- **Purpose**: prove the `REVISE` branch of `/review-plan` (ledger write
  skipped, phase → `REVISING_PLAN`), that `/apply-plan-review`'s `"2.1"`
  revised exit step (7') correctly returns to `AWAITING_LOCAL_PLAN_REVIEW`
  rather than the `"1"` item's `AWAITING_PLAN_APPROVAL`, and that a second
  `/review-plan` round is a genuine new round (round number increments, not
  reused).
- **Initial phase/state**: requires re-entering `AWAITING_LOCAL_PLAN_REVIEW`
  fresh — either run this *before* S2's `APPROVE` (recommended: insert S3
  first, using a deliberately seeded minor plan gap as the finding, then run
  S2's `APPROVE` pass as round 2), or accept a second synthetic plan edit
  after S2 to force re-review. Recommend running S3 before S2 to avoid
  re-deriving a second edit cycle; renumber only the execution order, not
  this document's IDs.
- **Command invoked**: `/review-plan v2-1-dry-run` (round 1, `REVISE`) →
  `/apply-plan-review` (real) → `/review-plan v2-1-dry-run` (round 2,
  `APPROVE`, folds into S2 above).
- **Expected state transition**: `AWAITING_LOCAL_PLAN_REVIEW` → (REVISE) →
  `REVISING_PLAN` → (apply-plan-review) → `AWAITING_LOCAL_PLAN_REVIEW` again.
- **Expected files/commits**: round-1 `REVIEW_FEEDBACK.md` with
  `Status: REVISE`; no `plan_review_stages` write (per `/review-plan` step 8);
  plan doc edited in place by `/apply-plan-review`; bundle regenerated
  (`bundle_id` changes, `review_content_id` changes if the plan content
  itself changed).
- **Real approval gate**: none.
- **Fresh-session boundary**: none required, but a fresh session between the
  `REVISE` verdict and `/apply-plan-review` is a reasonable place to insert
  one of the checklist's required session boundaries if you want that
  boundary exercised here rather than at S6.
- **Pass/fail evidence**: round 2's `REVIEW_FEEDBACK.md` round number is
  exactly one more than round 1's; no stale round-1 file is mistaken for
  round 2's verdict.
- **Cleanup**: none.

## S4 — Manual external plan-review recording and exact binding approval

- **Purpose**: prove `/record-manual-plan-review`'s validation chain (role
  check, `review_content_id` match — hard block on mismatch, prior local
  `APPROVE` presence, no duplicate ingestion) using a **real externally
  pasted verdict** — actually copy the current bundle content out, obtain
  (or realistically simulate obtaining) an external reviewer's `APPROVE`
  verdict with the three binding fields plus
  `Reviewer role: manual_external_plan_review`, paste it into
  `REVIEW_FEEDBACK.md`, then run the command.
- **Initial phase/state**: `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` (S2's
  exit).
- **Command invoked**: `/record-manual-plan-review v2-1-dry-run` (real).
- **Expected state transition**: → `AWAITING_PLAN_APPROVAL`.
- **Expected files/commits**: `plan_review_stages.manual_external_plan_review`
  recorded with the feedback's own verbatim `bundle_id`; no commit (this
  command never writes plan/registry/mapping/command/product files).
- **Real approval gate**: none — this command is explicitly not a
  user-authority gate per its own header, only `/approve-review plan` is.
- **Fresh-session boundary**: none stated.
- **Pass/fail evidence**: a deliberately mismatched `review_content_id`
  pasted first should hard-block with `StaleReviewContentIdError` naming
  both values — worth testing the refusal path once before the real
  matching paste, since this is the one hard validation this command owns.
- **Cleanup**: none.

## S5 — Explicit user plan approval

- **Purpose**: prove `/approve-review plan v2-1-dry-run` end to end for a
  `"2.1"` item: the `plan_approval_gate_reachable` `"2.1"` branch (requires
  the `plan_review_stages` ledger populated by S2/S4), the worktree/HEAD
  staleness check, `resolve_approval_basis`, the approval commit (plan doc +
  registry + mapping + `WORKFLOW_STATE.json` together, `Workflow-Plan-Approval`
  + `Workflow-Work-Item` trailers), and the post-approval manifest-match
  verification.
- **Initial phase/state**: `AWAITING_PLAN_APPROVAL` (S4's exit).
- **Command invoked**: `/approve-review plan v2-1-dry-run` — **user-only**,
  `disable-model-invocation: true`; requires the user's own literal
  confirmation text naming `v2-1-dry-run` and `plan` in the same turn.
- **Real approval gate**: **yes — user-gated.** Claude cannot supply this;
  the user must type the actual approval text when this scenario is
  reached.
- **Expected state transition**: → `IMPLEMENTING`.
- **Expected files/commits**: one commit containing the plan doc, registry
  JSON + generated Markdown view, mapping JSON, `WORKFLOW_STATE.json`,
  carrying `Workflow-Plan-Approval: <review_content_id>` +
  `Workflow-Work-Item: v2-1-dry-run`.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: `verify_post_approval_manifest_match` raises
  nothing; the commit's trailers are discoverable by
  `workflow_state.discover_plan_approval_commit`.
- **Cleanup**: none.

## S6 — Checkpoint implementation and fresh-session resume

- **Purpose**: prove `/milestone-implement`'s `[2.1 step 1]` resumable
  single-checkpoint sequence: entry validation, deterministic selection
  (`D-Selection`), fresh-start bookkeeping (`WORKTREE_IDENTITY.json` write),
  one commit with `Workflow-Checkpoint`/`Workflow-Work-Item` trailers, hard
  stop after exactly one checkpoint, and correct resume of the *next*
  checkpoint from a genuinely fresh session.
- **Initial phase/state**: `IMPLEMENTING` (S5's exit), checkpoint 1 of the
  scratch registry from S1 not yet started.
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real), run
  **twice from two separate fresh sessions** — once per scratch checkpoint
  1 and checkpoint 2 (leave checkpoint 2's work uncommitted/dirty
  deliberately at the end of its session, to set up S13).
- **Expected state transition**: `current_checkpoint_id` → checkpoint-1 id
  (`IN_PROGRESS`) → `COMPLETE`, `current_checkpoint_id` → `null` (phase
  stays `IMPLEMENTING`); second session: → checkpoint-2 id `IN_PROGRESS`
  (left dirty, not committed, for S13).
- **Expected files/commits**: checkpoint 1's commit carries
  `Workflow-Checkpoint: <id>` + `Workflow-Work-Item: v2-1-dry-run`;
  `.ai-review/runtime/WORKTREE_IDENTITY.json` gains/refreshes a
  `v2-1-dry-run` keyed entry at each `IN_PROGRESS` transition.
- **Real approval gate**: none.
- **Fresh-session boundary**: **required** — this is the scenario that
  actually proves resumability across a real session boundary, the core
  claim WF8b exists to test. Do not run both checkpoints in one
  conversation.
- **Pass/fail evidence**: the second session's invocation reports "resume"
  (not "fresh start") only if it targets the same checkpoint left
  `IN_PROGRESS`; a clean checkpoint 1 → checkpoint 2 transition reports
  "fresh start" correctly.
- **Cleanup**: none — checkpoint 2's dirty state is intentionally carried
  into S13.

## S7 — External implementation review returning APPROVE

- **Purpose**: prove `/milestone-implement` step 2-5 (self-review, full
  verification, `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` bundle,
  `record_bundle_generation` writing `reviewed_implementation_head`) once
  the scratch registry's remaining checkpoints (including the dirty one from
  S13, resolved first) are all `COMPLETE`.
- **Initial phase/state**: `IMPLEMENTING`, all scratch checkpoints
  `COMPLETE`.
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real; this
  invocation is the one that finds every checkpoint `COMPLETE` and proceeds
  to step 2 onward, per the command's own note that this never happens in
  the same invocation that completed the last checkpoint).
- **Expected state transition**: `IMPLEMENTING` → `SELF_REVIEWING_IMPLEMENTATION`
  → `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`.
- **Expected files/commits**: `IMPLEMENTATION_SUMMARY.md`, `TEST_RESULTS.md`,
  bundle regenerated at `implementation` stage;
  `work_items["v2-1-dry-run"].reviewed_implementation_head` set to current
  HEAD.
- **Real approval gate**: none yet.
- **Fresh-session boundary**: recommended (this invocation should itself be
  a fresh session from S6's last checkpoint, per the command's own
  never-same-invocation rule).
- **Pass/fail evidence**: `reviewed_implementation_head` equals live HEAD
  exactly.
- **Cleanup**: none.

## S8 — External implementation review returning REVISE and remediation

- **Purpose**: prove `/apply-implementation-review`'s validation
  (binding-field check, reproduce-before-fixing), fix + regenerate at
  `post-fix` stage, and `record_bundle_generation` advancing
  `reviewed_implementation_head` again.
- **Initial phase/state**: `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` (S7's
  exit). Seed a real, deliberately-planted minor defect in one scratch
  checkpoint's output before writing this round's `REVIEW_FEEDBACK.md`, so
  the "reproduce before fixing" step has something genuine to validate
  rather than a fabricated finding with nothing behind it.
- **Command invoked**: `/apply-implementation-review v2-1-dry-run` (real).
- **Expected state transition**: stays `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`
  internally during the fix, reports `AWAITING_TECHNICAL_APPROVAL` reachable
  once resolved.
- **Expected files/commits**: one coherent fix commit; bundle regenerated at
  `post-fix`; `reviewed_implementation_head` advances to the new HEAD.
- **Real approval gate**: none.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: `reviewed_implementation_head` after this round
  differs from S7's value and matches the fix commit's SHA.
- **Cleanup**: none.

## S9 — Functional-review preparation and explicit user acceptance

- **Purpose**: prove `/approve-review implementation` (user-gated,
  `technical_approval_gate_reachable`, `head_matches_reviewed_implementation_head`
  check against S8's advanced value), `/prepare-functional-review`'s
  checklist-writing path, and `/accept-milestone`'s user-only guard +
  `complete_work_item` writer.
- **Initial phase/state**: `AWAITING_TECHNICAL_APPROVAL` reachable (S8's
  exit) → `/approve-review implementation` → `AWAITING_FUNCTIONAL_REVIEW`.
- **Command invoked**: `/approve-review implementation v2-1-dry-run`
  (**user-gated**) → `/prepare-functional-review v2-1-dry-run` (real) →
  (after a genuine, even if trivial, manual check of the scratch artifacts)
  `/accept-milestone` (**user-gated**).
- **Expected state transition**: `AWAITING_TECHNICAL_APPROVAL` →
  `AWAITING_FUNCTIONAL_REVIEW` → (accept) → `MILESTONE_COMPLETE`.
- **Real approval gate**: **yes, twice** — implementation approval and final
  acceptance both require the user's literal confirmation text naming
  `v2-1-dry-run` and the respective stage.
- **Expected files/commits**: implementation-approval metadata-only commit
  with `Workflow-Technical-Approval` trailer; functional-review checklist
  section; final acceptance/completion commit.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: `verify_post_approval_manifest_match` clean for
  the implementation stage; `complete_work_item` does not raise
  (no remediation children exist yet at this point — see S10/S12, which
  should run *before* this final acceptance if you want S9 to prove the
  `IncompleteChildWorkItemError` block too; otherwise run S10/S12 first and
  fold their child-completion into this scenario's own precondition).
- **Cleanup**: none — reaching `MILESTONE_COMPLETE` here is itself part of
  scenario 16.

## S10 — Functional-review findings and apply-functional-review remediation

- **Purpose**: prove `/apply-functional-review`'s three-way branch
  (`D-Functional-Remediation`) for a state-tracked item: at minimum the
  **bounded** branch (stale-before-edit ordering, fix, post-fix bundle,
  forced return to `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`).
- **Initial phase/state**: `AWAITING_FUNCTIONAL_REVIEW` (S9's
  `/prepare-functional-review` exit, **before** running `/accept-milestone`
  — reorder execution so this runs ahead of S9's final acceptance step).
- **Command invoked**: write a real `FUNCTIONAL_REVIEW.md` finding against
  a scratch artifact, then `/apply-functional-review v2-1-dry-run` (real).
- **Expected state transition**: `mark_technical_approval_stale` fires
  before the fix lands; fix commit; post-fix bundle; **returns to
  `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`** (this is the one branch that
  requires a fresh `/apply-implementation-review` → `/approve-review
  implementation` round before `AWAITING_FUNCTIONAL_REVIEW` is reachable
  again — do not skip that repeat round).
- **Real approval gate**: the repeat `/approve-review implementation` this
  branch forces is user-gated, same as S9.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: `technical_approval.status` reads `STALE` on disk
  immediately after the finding is classified as bounded, strictly before
  any source/test edit — check the intermediate state, not just the end
  state.
- **Cleanup**: none.

## S11 — Legacy Workflow v1 import/adoption path

- **Purpose**: prove `/prepare-functional-review`'s `LEGACY_READY` adoption
  scan (D-Legacy phase 2): repository-level lookup, branch-reconciliation
  re-check, staleness re-check, `active_work_item_id` repoint,
  `governing_workflow_version` `"1"` → `"2.1"` transition, `phase` →
  `AWAITING_FUNCTIONAL_REVIEW`, `technical_approval` left untouched.
- **Design decision (locked in, user-approved — see "Decisions locked in
  for execution" above)**: run against a new throwaway synthetic dormant
  item `v2-1-dry-run-legacy` (`work_item_kind: "synthetic"`,
  `work_item_type: "product"`, `phase: "LEGACY_READY"`, a fabricated but
  well-formed `technical_approval` with `basis: LEGACY_V1`), created as part
  of this scenario's own setup. **Do not adopt, reopen, or change the
  governing workflow version of the real `milestone-8` entry at any point
  in this scenario or anywhere else in WF8b** — it remains accepted and
  closed under its existing functional-review waiver.
- **Initial phase/state**: `v2-1-dry-run-legacy` at `LEGACY_READY`;
  `active_work_item_id` currently `v2-1-dry-run` (must temporarily point at
  or explicitly name `v2-1-dry-run-legacy` for this one invocation, per the
  command's own explicit-selector-argument design — it does not require
  repointing `active_work_item_id` at all).
- **Command invoked**: `/prepare-functional-review v2-1-dry-run-legacy`
  (real).
- **Expected state transition**: `LEGACY_READY` → `AWAITING_FUNCTIONAL_REVIEW`,
  `governing_workflow_version` `"1"` → `"2.1"`.
- **Expected files/commits**: `WORKFLOW_STATE.json` updated; functional
  review checklist written.
- **`docs/ACTIVE_MILESTONE.md` concern — locked-in handling (user-approved,
  see "Decisions locked in for execution" above)**: `/prepare-functional-review`'s
  step 3, as written, names `docs/ACTIVE_MILESTONE.md` unconditionally —
  that file is `workflow-v2-1-core`'s real product doc, not
  `v2-1-dry-run-legacy`'s. Run the real command contract exactly as written;
  do **not** silently redirect the write to a different file and do not
  skip the step. If it writes, or attempts to write, into the real
  `docs/ACTIVE_MILESTONE.md` for this synthetic item:
  1. stop before any destructive or unrelated mutation lands, if that's
     still possible at the point the write is observed;
  2. capture the exact evidence in this scenario's outcome notes (the
     literal attempted content, the diff against the real file, and which
     command step produced it);
  3. treat this as a genuine WF8b failure and create a remediation finding
     — not an inline workaround applied only for the dry run;
  4. route the fix through the approved remediation cycle (the same
     validate-then-fix discipline `/apply-plan-review`/
     `/apply-implementation-review` use), which may mean the command itself
     needs a work-item-scoped narrative-record target instead of the
     hardcoded path — a real fix to the command contract, reviewed
     normally, not decided ad hoc mid-dry-run.
- **Real approval gate**: none for this step; a full acceptance cycle for
  this throwaway item is optional and can be abbreviated once adoption
  itself is proven — this scenario's pass condition is the transition, not
  a full functional-review pass.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: `technical_approval` block is byte-identical
  before/after adoption except `phase`/`governing_workflow_version` fields
  it explicitly owns.
- **Cleanup**: delete the throwaway `v2-1-dry-run-legacy` entry as part of
  overall WF8b cleanup (S17) — it must never survive into the real
  `WORKFLOW_STATE.json` post-dry-run.

## S12 — Approval/content drift remediation cycle

- **Purpose**: prove the freshness/staleness detection this repository
  already exercised for real once (`2da0b66`,
  "remediate stale plan_approval after Milestone 8 merge") — but this time
  as a deliberate, observed dry-run scenario rather than an ad hoc fix.
- **Initial phase/state**: any point after S5 where `plan_approval` or
  `technical_approval` is `CURRENT` for `v2-1-dry-run`.
- **Command invoked**: deliberately touch a plan-stage or
  implementation-stage protected path for `v2-1-dry-run` outside the normal
  command flow (e.g. hand-edit the scratch plan doc without going through
  `/apply-plan-review`), then invoke the next command in sequence
  (`/approve-review` or `/milestone-implement`) and confirm it detects and
  reports the drift rather than silently proceeding.
- **Expected state transition**: none — the point is refusal/staleness
  detection, not a successful transition.
- **Real approval gate**: n/a (the gate is what's expected to refuse).
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**: the approval-freshness check names the specific
  stale field and the mismatched `review_content_id` values, matching
  `2da0b66`'s real remediation shape.
- **Cleanup**: revert the deliberately-introduced drift before continuing
  the rest of the checklist, via a real remediation commit (mirroring
  `2da0b66`), not a silent discard.

## S13 — Dirty IN_PROGRESS worktree resume in the same worktree

- **Purpose**: prove `verify_dirty_resume_safety`'s success path: the same
  worktree that started checkpoint 2's `IN_PROGRESS` work (left dirty at the
  end of S6) resumes it correctly in a fresh session.
- **Initial phase/state**: checkpoint 2 `IN_PROGRESS`, uncommitted changes
  present, `WORKTREE_IDENTITY.json` has a matching `v2-1-dry-run` entry from
  this same worktree.
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real), from a
  fresh session, in the **same** worktree.
- **Expected state transition**: reports "resume," not "fresh start";
  completes checkpoint 2 normally into S6/S7's flow.
- **Real approval gate**: none.
- **Fresh-session boundary**: **required** — this is the actual claim being
  tested.
- **Pass/fail evidence**: no `WorktreeIdentityMismatchError`; the
  uncommitted work from before the session boundary is intact and gets
  committed as checkpoint 2's completion commit.
- **Cleanup**: none.

## S14 — Dirty IN_PROGRESS resume attempted from a mismatched worktree, refused

- **Purpose**: prove `verify_dirty_resume_safety`'s failure path:
  `WorktreeIdentityMismatchError` stops cleanly rather than silently
  resuming or silently discarding.
- **Authorization (locked in, user-approved — see "Decisions locked in for
  execution" above)**: creating a second real Git worktree solely for this
  scenario is approved, subject to all of the following, each of which must
  be recorded in this scenario's outcome notes when run:
  1. **Isolation**: an isolated temporary branch and path — e.g.
     `git worktree add /tmp/<scratch-path>/wf8b-s14-worktree -b
     wf8b-s14-scratch <current HEAD>` — never the branch or worktree any
     other scenario uses.
  2. **No mutation from that worktree**: the only action taken from worktree
     B is the single refused `/milestone-implement v2-1-dry-run` invocation
     against a dummy checkpoint 3 (not checkpoint 2, to avoid disturbing
     checkpoint 2's real in-progress work from S6/S13) — no commits, no
     file edits, nothing else run from worktree B.
  3. **Fail closed before mutation, verified**: confirm no commit and no
     `WORKFLOW_STATE.json`/`WORKTREE_IDENTITY.json` write occurred as a
     result of the refused invocation, checked immediately after the
     refusal — not assumed from the error message alone.
  4. **Cleanup, mandatory**: `git worktree remove` the temporary worktree
     and `git branch -d wf8b-s14-scratch` (or equivalent) during WF8b's own
     overall cleanup (S17), not left behind.
  5. **Record all three**: creation (the exact command run), the refusal
     evidence (the exact error, naming both worktree identities), and
     cleanup (confirmation both the worktree and branch are gone) all go in
     this scenario's outcome notes once executed.
- **Initial phase/state**: a checkpoint `IN_PROGRESS` with
  `WORKTREE_IDENTITY.json` pointing at worktree A (the primary worktree);
  command invoked from worktree B (the temporary one).
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real) from
  worktree B, targeting a dummy checkpoint 3 left `IN_PROGRESS` in worktree A
  for exactly this purpose.
- **Expected state transition**: none — hard stop, no state mutation.
- **Real approval gate**: none.
- **Fresh-session boundary**: not required (a fresh session in a different
  worktree already gets there naturally).
- **Pass/fail evidence**: the error names both the expected and actual
  worktree/`WORKTREE_IDENTITY.json` values; no commit, no state-file write —
  verified per point 3 above, not just claimed.
- **Cleanup**: `git worktree remove` the temporary worktree and delete its
  branch (point 4 above), performed during S17; confirm `v2-1-dry-run`'s
  `WORKTREE_IDENTITY.json` entry for the primary worktree is unchanged from
  before this scenario.

## S15 — Interrupted checkpoint recovery

- **Purpose**: prove the "interrupted-or-failed recovery" rule
  (`D-Self-Governance`'s WF8b pointer-persistence text, point 4, and D3's
  general `IN_PROGRESS`-with-no-completion-commit case): a session finding
  a checkpoint `IN_PROGRESS` with no completion commit reports last known
  state and requires an explicit user decision, never guessing.
- **Initial phase/state**: a checkpoint left `IN_PROGRESS` with uncommitted
  or partially-committed work, then the session ends without completing it
  (simulate by simply stopping mid-checkpoint rather than finishing S13's
  resume).
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real) from a
  fresh session.
- **Expected state transition**: reports the interrupted checkpoint and
  waits — does not auto-resume, does not auto-discard.
- **Real approval gate**: the recovery decision itself (resume vs. discard)
  is effectively user-gated by design, even though no named command
  requires literal confirmation text here — treat the user's explicit
  choice as required before proceeding either way.
- **Fresh-session boundary**: **required**.
- **Pass/fail evidence**: the report names the checkpoint id and its
  `start_commit`/dirty state accurately.
- **Cleanup**: proceed per the user's chosen recovery path; if discarded,
  confirm the checkpoint reverts cleanly to its `start_commit`.

## S16 — Final synthetic work-item completion

- **Purpose**: prove `/accept-milestone`'s `2a` parent-completion block (no
  incomplete remediation children block it once S10/S12's children, if any
  were created as real child items rather than resolved inline, are
  themselves `MILESTONE_COMPLETE`) and normal completion mechanics for a
  `"2.1"` synthetic item.
- **Initial phase/state**: `AWAITING_USER_ACCEPTANCE` (folds into S9 above
  if not already reached there).
- **Command invoked**: `/accept-milestone` (**user-gated**, if not already
  exercised as part of S9).
- **Expected state transition**: → `MILESTONE_COMPLETE`.
- **Real approval gate**: **yes** — literal user confirmation text.
- **Pass/fail evidence**: `complete_work_item` succeeds without raising
  `IncompleteChildWorkItemError`.
- **Cleanup**: none yet — this is the precondition for S17, not cleanup
  itself.

## S17 — Restoration of prior_active_work_item_id and dry-run pointer removal

- **Purpose**: prove the exit half of `D-Self-Governance`'s WF8b
  pointer-persistence design (point 3): restore `active_work_item_id` to
  `workflow-v2-1-core` from `.ai-review/runtime/DRY_RUN_RESUME.json`,
  archive/remove `v2-1-dry-run`'s (and, if created, `v2-1-dry-run-legacy`'s)
  `work_items` entries and their `.ai-review/<id>/` bundle directories,
  delete the marker file, and commit the restore.
- **Initial phase/state**: `v2-1-dry-run` at `MILESTONE_COMPLETE` (S16);
  `active_work_item_id` still `v2-1-dry-run`.
- **Command invoked**: no dedicated command exists for this exit step per
  the plan text — it is WF8b's own completion action, performed directly
  (the same way its entry step was), not through `/milestone-implement`
  et al.
- **Expected state transition**: `active_work_item_id` → `workflow-v2-1-core`.
- **Expected files/commits**: one commit removing the synthetic entries,
  restoring the pointer, deleting the marker file, carrying
  `Workflow-Checkpoint: WF8b` + `Workflow-Work-Item: workflow-v2-1-core`
  trailers (this is the completion commit the bootstrap command's step 3
  will look for on the next invocation).
- **Real approval gate**: none named, but given this is the commit that
  finally completes `workflow-v2-1-core`'s own last checkpoint, confirm with
  the user before creating it, consistent with this repo's general
  commit-authorization discipline.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence — the check the plan itself names**: diff
  `workflow-v2-1-core`'s own `work_items` record before WF8b's entry commit
  (`9317b1c`'s parent) against its state immediately after this commit —
  byte-identical. This is the actual acceptance criterion for all of WF8b,
  not just this last scenario.
- **Cleanup**: this scenario *is* the cleanup for every prior one.

---

## Execution ordering note

Scenarios are numbered to match the user's requested list, not necessarily
run order. Recommended actual order, folding in the reorderings noted
above: **S1 → S3 (REVISE round) → S2 (APPROVE round) → S4 → S5 → S6
(checkpoint 1, fresh session) → S6 (checkpoint 2, fresh session, left dirty)
→ S13 (resume, same worktree) → S14 (mismatched worktree, separate dummy
checkpoint, then remove the temp worktree) → S15 (interrupted recovery,
separate dummy checkpoint) → S7 → S8 → S9's approve-review implementation →
S10 (functional finding, bounded remediation, forces a repeat of S8/S9's
implementation-approval step) → S9's prepare-functional-review + accept →
S11 (separate throwaway legacy item, any point after S1) → S12 (drift
detection, any point after S5) → S16 → S17**.

Each `→` that crosses a "Fresh-session boundary: required" scenario above is
a real stopping point across separate `/bootstrap-workflow-v2` (or direct
command) invocations, not a single conversation.
