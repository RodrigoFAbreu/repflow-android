# WF8b manual dry-run scenario checklist

**Status:** approved by the user. S1's first attempt was blocked before any
mutation by a real tooling defect (see S1's own "Outcome (first attempt,
blocked)" note and `WF8B_S1_FINDING_review_content_id_not_generalized.md`);
that finding's remediation landed (revision 21, commit `584fc87`) and was
independently reviewed through the plan's subsequent 41 further external
review rounds (nothing in those rounds touched `D-Fingerprint-Generalization`
itself again). S1 was rerun from the beginning in a fresh session on
2026-08-11, after `workflow-v2-1-core`'s plan revision 62 became durably
approved (commit `e75a756`), and reached its own defined stopping point —
see S1's "Outcome (rerun, 2026-08-11)" note below. No scenario has yet
reached full completion (S1 stops at a hard gate, `AWAITING_EXTERNAL_PLAN_REVIEW`,
per `/milestone-plan`'s own step 7).

**Current position as of plan revision 5 (2026-08-11).** The S1 gate named
above is no longer the blocker: one genuine external plan-review round and
three `local_model_plan_review` rounds have since completed, each returning
`REVISE` and each applied through `/apply-plan-review`. Live identity, and
the only identity any scenario may act on:

| field | value |
| --- | --- |
| `phase` | `AWAITING_LOCAL_PLAN_REVIEW` (`/apply-plan-review`'s `"2.1"` exit) |
| `plan_revision` | 5 |
| `bundle_id` | recompute — revision 4's was `76b5fd49…996b34`, superseded by revision 5's regeneration |
| `review_content_id` | recompute — revision 4's was `0652c528…c6ad5f`, superseded |
| `plan_review_stages` | `null` (a `REVISE` verdict writes no ledger entry) |

Never act on a `bundle_id`/`review_content_id` copied from this document:
recompute both from the live bundle before every scenario, exactly as
`/review-plan` and `/approve-review` do. The values above are labelled
history, not inputs.

**Feedback path — always resolved, never a literal.** Every verdict for
this item goes to `<feedback_dir>/REVIEW_FEEDBACK.md`, where `<feedback_dir>`
is `workflow_fingerprint.resolve_feedback_dir(repo_root, "v2-1-dry-run")`.
That currently resolves to `.ai-review/v2-1-dry-run/feedback/`, because the
scoped directory now exists; `/record-manual-plan-review` step 4 and
`/apply-plan-review` step 1 read it through the same resolution, so a
verdict pasted at the flat `.ai-review/feedback/` path would simply not be
seen. The flat path still holds this item's superseded revision-1 external
verdict and is read by nothing.

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
  later left dirty for S13, and checkpoint 3 the one later left `IN_PROGRESS`
  for S14 then S15); `docs/ai-workflow/registry/v2-1-dry-run-registry.json`
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

- **Outcome (rerun, 2026-08-11, real, reached the defined stopping point)**:
  ran for real, in a fresh session, against live repository state, after
  independently confirming (`python3 scripts/workflow_fingerprint.py
  <base> --work-item-id v2-1-dry-run`, read-only) that the revision-21
  remediation holds: `protected_paths` correctly names `v2-1-dry-run`'s own
  three files (`docs/ai-workflow/dry-run/v2-1-dry-run-plan.md`, its
  registry, its mapping), never `workflow-v2-1-core`'s — the exact defect
  below is fixed. Real work performed: `workflow_state.route_work_item(...)`
  called with all four declaration facts (`plan_path`/`registry_path`/
  `mapping_path`/`base_commit`, base pinned at live HEAD
  `e75a756d42751ef18eee842a958cc2c888086772`), persisted to
  `WORKFLOW_STATE.json`; a real 3-checkpoint scratch registry
  (`S-CP1`→`S-CP2`→`S-CP3`, each a single throwaway file under
  `docs/ai-workflow/dry-run/scratch/`, checkpoint 2 earmarked for S13's
  dirty-resume scenario and checkpoint 3 for S14/S15's refusal and
  interrupted-recovery scenarios) and its requirements mapping built via
  `generate_registry`/`generate_mapping` (both validated D-Selection's
  topological order and D3's bidirectional coverage at generation time) and
  written via `write_registry_and_mapping`;
  `generate_artifacts_declarations` written to
  `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`; the plan doc
  written at its declared path embedding `render_registry_markdown`'s
  generated table. **Resolved ambiguity flagged in S2 below**: read
  `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s own "Hard gates summary"
  before assuming anything — the `"2.1"` two-stage local/manual-external
  protocol is stated there as "a refinement of the existing `REVISING_PLAN`
  → `AWAITING_PLAN_APPROVAL` edge, not two additional hard gates layered on
  top of it," and `AWAITING_LOCAL_PLAN_REVIEW`'s own entry condition
  (`MILESTONE_WORKFLOW.md` line 81) is exactly "`REVISING_PLAN`'s exit
  condition is met, or `/apply-plan-review` has just applied an accepted
  plan edit" — no third entry from a first-ever plan bundle. So `phase` was
  set to the literal `AWAITING_EXTERNAL_PLAN_REVIEW` (matching
  `/milestone-plan`'s step 6 exactly as written, no `[2.1]` override exists
  at that step), identical in kind to a `"1"` item's first round. This
  means the checklist's own recommended order (S1 → S3 → S2 → S4) is
  confirmed correct: S2 (`/review-plan`) is genuinely unreachable until a
  `REVISE` round (S3) has run at least once. Bundle written and verified
  real: `docs/ai-workflow/dry-run/v2-1-dry-run-plan.md` (registry/mapping
  JSON tracked via `git add -N` so the fingerprint tool's tracked-path check
  passes without committing anything — no command file states this staging
  step explicitly, worth a plan/command-doc note but not itself a blocking
  defect), `.ai-review/v2-1-dry-run/current/{PLAN.md,REVIEW_REQUEST.md,
  CONTEXT_FILES.txt,CHANGED_FILES.txt,COMMITS.txt,DIFF.patch,MANIFEST.md,
  files/}` all written by a real `./scripts/prepare-ai-review.sh
  e75a756d42751ef18eee842a958cc2c888086772 plan v2-1-dry-run` invocation,
  `review_content_id`/`bundle_id` both self-verified equal across
  write/recompute/archive-extraction (the script's own built-in
  reproducibility check, exit 0, no mismatch reported).
  `docs/ai-workflow/WORKFLOW_STATE.json`'s `v2-1-dry-run` entry now records
  `phase: AWAITING_EXTERNAL_PLAN_REVIEW`, `state_revision: 3`. **No
  commit** — per this scenario's own "Expected files/commits" note above,
  planning bundles are not committed until plan approval (S5). Stopped
  here, at S1's own defined hard gate, per this session's own scope (one
  checkpoint-slice of `WF8b` per session; S3/S2/S4 are next, none
  user-gated, but require a genuine review verdict — real or realistically
  simulated — which this session did not attempt to fabricate).

- **Outcome (first attempt, blocked before mutation)**: `/milestone-plan
  v2-1-dry-run`'s step 6 depends on `scripts/workflow_fingerprint.py` to
  compute `review_content_id` and write `MANIFEST.md`. That script's
  `--work-item-id` flag affects only `resolve_bundle_dir` (which directory
  to write to) — every call into `compute_review_content_id_plan_stage`/
  `write_manifest_with_verified_identifiers` hardcodes
  `work_item_id="workflow-v2-1-core"` and its own 5-file protected-path set
  regardless of the flag. Confirmed by a real, read-only invocation
  (`python3 scripts/workflow_fingerprint.py <base> --work-item-id
  v2-1-dry-run`): it printed `workflow-v2-1-core`'s own `plan_revision`,
  protected paths, and `review_content_id`, ignoring the argument entirely.
  Proceeding would have produced a `MANIFEST.md` whose `review_content_id`
  never depends on `v2-1-dry-run`'s own plan/registry/mapping content — a
  false-positive pass against S1's own stated evidence criterion, and a
  fail-open gap in every downstream freshness/staleness check
  (`/review-plan`, `/record-manual-plan-review`, `/approve-review plan`,
  `/apply-plan-review`). Stopped before creating any plan doc, registry,
  mapping, bundle, or manifest — full detail, reproduction, and required
  remediation scope in
  `docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md`.
  This is the deferred "generalize protected-path derivation beyond this
  one process plan" scope from `WF4a-i`, surfacing for real. Remediated at
  revision 21 (commit `584fc87`); see the "Outcome (rerun, 2026-08-11...)"
  note above for the successful rerun.

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

  **Resolved (2026-08-11, real, read-only verification, no state mutation)**:
  they are **not** the same state, and `/review-plan` is genuinely
  unreachable from `v2-1-dry-run`'s current phase. `MILESTONE_WORKFLOW.md`'s
  "Hard gates summary" states explicitly: "For a
  `governing_workflow_version: \"2.1\"` work item, the edge from
  `REVISING_PLAN` to `AWAITING_PLAN_APPROVAL` is further refined into
  `REVISING_PLAN → AWAITING_LOCAL_PLAN_REVIEW → ...` — a refinement of the
  existing edge, not two additional hard gates layered on top of it." That
  refinement applies only to the `REVISING_PLAN → AWAITING_PLAN_APPROVAL`
  edge, never to `SELF_REVIEWING_PLAN → AWAITING_EXTERNAL_PLAN_REVIEW`.
  `.claude/commands/milestone-plan.md` step 6/7 confirms this at the
  command-contract level: no `[2.1]` sub-step exists at step 6 or 7, so a
  `"2.1"` item's very first plan round enters `AWAITING_EXTERNAL_PLAN_REVIEW`
  — hard gate 1 of 6, identical mechanism to a `"1"` item — exactly as S1's
  own "Real approval gate" line already said ("this stops at the hard gate
  for external plan review, same as any other `/milestone-plan` run"). The
  only way to reach `AWAITING_LOCAL_PLAN_REVIEW` is via `REVISING_PLAN`'s
  exit, which itself requires `<feedback_dir>/REVIEW_FEEDBACK.md` to
  exist (`AWAITING_EXTERNAL_PLAN_REVIEW`'s own exit condition) — i.e. a
  genuine external-review round against `v2-1-dry-run`'s current bundle,
  not yet obtained. So **S2 and S3 both remain blocked on the same,
  still-open S1 hard gate**, not on each other; S3 is not "next" in the
  sense of being independently runnable right now.

  **Superseded as of plan revision 4** (the analysis above stands; its
  "not yet obtained" conclusion does not). That external round was
  subsequently obtained and applied, and two `local_model_plan_review`
  rounds have run since. The paragraphs below record what a 2026-08-11
  session observed at plan revision 1 — dated evidence, not current state.
  For current state see the status block at the top of this document.

  Verified read-only, no mutation: live HEAD (`e75a756d42751ef18eee842a958cc2c888086772`)
  still equals `v2-1-dry-run`'s recorded `base_commit`; a fresh
  `scripts/workflow_fingerprint.py e75a756d42751ef18eee842a958cc2c888086772
  --work-item-id v2-1-dry-run --stage plan` recompute reproduces
  `MANIFEST.md` exactly — `bundle_id
  d4a121549aca4dbf5b362545a63dc931623585b93415d0fa246e5fccd599f473`,
  `review_content_id
  861c9ab11f4b60004d5eba0c294af0434f423115d877f6381cf0ebaa5b00437c`,
  `plan_revision 1`, `worktree_root`/`generation_head` both match current
  state; `docs/ai-workflow/WORKFLOW_STATE.json`'s `v2-1-dry-run` entry is
  unchanged (`phase: AWAITING_EXTERNAL_PLAN_REVIEW`, `state_revision: 3`).
  `.ai-review/feedback/REVIEW_FEEDBACK.md` currently holds unrelated, stale
  content — a real prior `external_plan_review` verdict for
  `workflow-v2-1-core` revision 21 (`Work item: workflow-v2-1-core`,
  `Reviewed bundle ID: fa03d434...`), not `v2-1-dry-run` — confirming no
  external review of `v2-1-dry-run`'s bundle has been recorded yet under
  any name. `workflow-v2-1-core`'s own Revision 62 plan-approval state
  (`state_revision: 75`) was not read for any decision here beyond
  confirming it is untouched, and was not modified.

  **Exact next required action** (not performed this session — it is
  user-gated in substance, even though no named command enforces
  `disable-model-invocation` at this exact step): obtain a genuine external
  plan-review verdict for the bundle at
  `.ai-review/v2-1-dry-run/current/` (upload path, `bundle_id`, and
  `review_content_id` as recomputed above) and place it at
  `<feedback_dir>/REVIEW_FEEDBACK.md` — resolved via
  `workflow_fingerprint.resolve_feedback_dir(repo_root, "v2-1-dry-run")`,
  currently `.ai-review/v2-1-dry-run/feedback/`, never the flat
  `.ai-review/feedback/` literal this note originally named — with
  `Reviewer role: external_plan_review`, `Work item: v2-1-dry-run`, and the
  three binding fields matching the **then-current recomputed** values, not
  the revision-1 values recorded above. Only once that file exists does
  `REVISING_PLAN`'s entry condition become reachable, and only then does
  S3's own precondition (`REVISE` round from a fresh
  `AWAITING_LOCAL_PLAN_REVIEW`) become satisfiable. Per this document's own
  "Decisions locked in for execution" discipline, this session did not
  fabricate or simulate that verdict.

  (This required action was performed for plan revision 1 and has since
  been superseded twice — see the status block. It is retained because the
  *procedure* it states is the one every later external round follows.)
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
  staleness check, `resolve_approval_basis`, the approval commit (the
  member set below, `Workflow-Plan-Approval` + `Workflow-Work-Item`
  trailers), and the post-approval manifest-match verification.
- **Initial phase/state**: `AWAITING_PLAN_APPROVAL` (S4's exit).
- **Command invoked**: `/approve-review plan v2-1-dry-run` — **user-only**,
  `disable-model-invocation: true`; requires the user's own literal
  confirmation text naming `v2-1-dry-run` and `plan` in the same turn.
- **Real approval gate**: **yes — user-gated.** Claude cannot supply this;
  the user must type the actual approval text when this scenario is
  reached.
- **Expected state transition**: → `IMPLEMENTING`.
- **Expected files/commits**: **exactly five paths, no more and no fewer**,
  in one commit carrying `Workflow-Plan-Approval: <review_content_id>` +
  `Workflow-Work-Item: v2-1-dry-run`:

  1. `docs/ai-workflow/dry-run/v2-1-dry-run-plan.md`
  2. `docs/ai-workflow/registry/v2-1-dry-run-registry.json`
  3. `docs/ai-workflow/requirements/v2-1-dry-run-mapping.json`
  4. `docs/ai-workflow/WORKFLOW_STATE.json`
  5. `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`

  Member 5 is **resolved by the installed command itself**, not by an
  operator-run manual procedure: the first real S5 attempt (see "Outcome"
  below) reproduced exactly the failure the four-member set alone
  produces, and the fix landed generically in
  `scripts/workflow_fingerprint.py`
  (`resolve_plan_stage_approval_commit_paths`) and
  `.claude/commands/approve-review.md` (steps 4a/6/6a/6b) rather than as a
  `v2-1-dry-run`-specific workaround — see
  `WF8B_S5_FINDING_missing_artifacts_declaration_commit_member.md` for the
  full root-cause/remediation record. There is no generated Markdown
  registry view to commit for this item — the registry table lives inline
  in the plan doc — so member 2 is the JSON alone.

  The installed command's own step 4a now performs both of
  `D-Approval-Commits`' "Conditional fifth commit member" conditions
  itself, before any durable mutation — an operator retrying S5 does not
  need to run either check by hand:

  1. **Pending change** — the declaration's working-tree bytes differ from
     its content at `HEAD` (or it is absent at `HEAD`). If unchanged, the
     commit is the four-member set.
  2. **Bundle freshness** — the working-tree declaration is byte-identical
     to the copy the current bundle already captured
     (`<bundle_dir>/files/docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`).
     A mismatch refuses outright (`StaleArtifactsDeclarationError`, naming
     both paths) before staging, committing, or writing approval state —
     committing bytes the reviewer never saw is the exact failure this
     condition exists to prevent, and the declaration is the one member of
     this set that is plan-stage *excluded*, so nothing else catches it.

  The command also stages exactly the resolved set itself
  (`workflow_state.stage_plan_approval_commit_paths`) — never a broad
  `git add -A` — with its own index-isolation precondition and post-
  staging assertion, so `verify_review_content_id.py` (deliberately
  untracked) and this file (modified) are never swept in by construction,
  not merely by operator care.
- **Fresh-session boundary**: none required.
- **Pass/fail evidence**:
  - `resolve_plan_stage_approval_commit_paths` resolved the five-member
    set (member 5 = `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`,
    pinned sha256 `02fef08e858008441e2dc8c326c26331c6507a15d37e7cd58049ad3a484c740e`),
    or refused with a named stale path — recorded **before** staging;
  - `verify_post_approval_manifest_match` raises nothing;
  - `assert_committed_path_set_matches` and `verify_committed_blob_sha256`
    (fifth member) both raise nothing;
  - the commit's trailers are discoverable by
    `workflow_state.discover_plan_approval_commit`;
  - `git show --name-only --format= <commit>` lists exactly the five paths
    above, and `git status --short` afterwards shows no leftover ` A` entry
    for the declaration;
  - the commit-source `review_content_id` recomputed at that commit equals
    the `approved_review_content_id` just written to `plan_approval`;
  - `approval_is_current(..., stage="plan", head=<commit>)` and
    `implementing_entry_reachable(...)` both return `True` — S6 can start.
- **Cleanup**: none.

- **Outcome (first attempt, 2026-08-12, real, blocked after the commit,
  safely rolled back)**: ran for real against live repository state, after
  the user supplied the literal confirmation text. Steps 1–5 of the
  then-installed (four-member) `/approve-review` all passed; step 6
  created the literal four-member commit; step 6a's
  `verify_post_approval_manifest_match` raised
  `MissingWorkItemArtifactsDeclarationError` for
  `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` — never
  committed at any point in this item's history, only ever present in the
  working tree. Per step 6a's own "never let a mismatch reach the user as
  a successful approval" rule, the commit was reset (`git reset HEAD~1`)
  and `WORKFLOW_STATE.json` restored to its exact pre-attempt bytes
  (byte-diffed against a saved copy, not merely inspected) — full record
  in `WF8B_S5_FINDING_missing_artifacts_declaration_commit_member.md`.
  Per the user's explicit instruction, this was **not** worked around with
  a manual `git add`/commit or a `v2-1-dry-run`-specific bootstrap
  procedure: the generic defect was fixed in
  `scripts/workflow_fingerprint.py`/`scripts/workflow_state.py`/
  `.claude/commands/approve-review.md` instead (12 new hermetic tests,
  `workflow_integration_test.py`'s `TestPlanStageApprovalCommitMembership`;
  full suite green except one pre-existing, unrelated failure). This was
  done as ordinary `workflow-v2-1-core` `WF8b` checkpoint implementation
  work under the already-CURRENT Revision 62 plan approval — the design
  being implemented (`D-Approval-Commits`' "Conditional fifth commit
  member") was already fully specified and approved; only its
  generalization beyond `workflow-v2-1-core`'s own one-off bootstrap
  procedure was missing. **This does not close missing-test item 347** —
  see the finding file's own explicit scope-boundary section — and does
  not retire `workflow-v2-1-core`'s own Bootstrap plan-approval procedure.
  `v2-1-dry-run` was left at `AWAITING_PLAN_APPROVAL`,
  `plan_approval: null`, exactly as it was before this attempt; Revision
  5's local/manual plan-review approvals are unaffected (the fix touches
  no path in this item's own plan-stage protected or bundle-relevant
  set). S5 was **not** forced through — a fresh operator session may
  retry `/approve-review plan v2-1-dry-run` directly.

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

- **Outcome (checkpoint 1, 2026-08-12, real, reached its own defined stop
  boundary)**: ran for real in a fresh session (this session began via a
  real `/clear`, resuming purely from repository state), against live HEAD
  `96dcf8769e854e4a63a59e826ce046ac200671ca` — S5's approval commit itself,
  confirming `IMPLEMENTING`'s entry condition holds at checkpoint 1 exactly
  as it did at S5's exit. Real work performed, `[2.1 step 1]` in full:
  **1a** `workflow_state.implementing_entry_reachable(repo_root, work_item,
  base_commit)` returned `True`; **1b**
  `workflow_state.select_next_checkpoint(work_item, registry)` returned
  `"S-CP1"` (fresh start — `current_checkpoint_id` was `null`); **1c**
  skipped (fresh start, not resume); **1d**
  `workflow_state.transition_checkpoint_in_progress(...)` persisted to
  `WORKFLOW_STATE.json` (`state_revision` 13 → 14) and
  `workflow_state.write_worktree_identity(repo_root, "v2-1-dry-run",
  now=...)` created this work item's first keyed entry in
  `.ai-review/runtime/WORKTREE_IDENTITY.json`'s
  `expected_dirty_paths_by_work_item` (gitignored, local-only, not
  committed); **1e** created
  `docs/ai-workflow/dry-run/scratch/a.txt` (the plan's own named checkpoint-1
  deliverable, `docs/ai-workflow/dry-run/v2-1-dry-run-plan.md` line 1060's
  `scratch/{a,b,c}.txt` naming) and
  `docs/ai-workflow/requirements/v2-1-dry-run-ledger.md` (this process
  item's `WF4b`-pattern mutable execution ledger, physically separate from
  the immutable mapping file, created here since no prior checkpoint had
  needed it yet), no test beyond file-existence per the plan's own
  self-review note; **1f** one commit,
  `ac1df008953b1ced594095a7034a6f2ec51799a4`, carrying
  `Workflow-Checkpoint: S-CP1` + `Workflow-Work-Item: v2-1-dry-run`,
  staging exactly the three checkpoint-owned paths (`git add --
  <three paths>`, never `-A`) so the pre-existing untracked
  `docs/ai-workflow/dry-run/verify_review_content_id.py` (present before
  this session started, unrelated to this checkpoint) was correctly left
  unstaged and uncommitted — confirmed by `git status --short` immediately
  after; in the same commit, `workflow_state.complete_checkpoint(...)`
  persisted `checkpoints["S-CP1"].status = "COMPLETE"`,
  `current_checkpoint_id = null`, `last_completed_checkpoint_id = "S-CP1"`,
  `phase` unchanged at `IMPLEMENTING` (correct — checkpoints 2/3 remain).
  **1g**: stopped immediately, per this step's own instruction, without
  touching checkpoint 2 or step 2 onward.

  **One real defect caught and self-corrected before it reached a commit**:
  the first `WORKFLOW_STATE.json` write used
  `json.dumps(new_state, indent=2)` with its default `ensure_ascii=True`,
  which re-escaped the file's existing literal `→` (U+2192) characters
  elsewhere in the document (unrelated `blocking_decisions` prose) into
  `→` — a real unrelated-content mutation this repo's own discipline
  (`CLAUDE.md`: "Don't touch unrelated working-tree changes") forbids. Not
  yet committed when noticed (`git diff -U0` review caught it); reverted
  with `git checkout --` before any commit, and redone with
  `ensure_ascii=False`, which reproduced a minimal, correctly-scoped diff
  (verified with `git diff -U0` a second time: exactly the three
  `v2-1-dry-run` fields this step intends to change, nothing else in the
  6900-line state file touched). Recorded here rather than silently fixed,
  per this document's own "capture the exact evidence... create a
  remediation finding rather than hand-editing around it" discipline for
  genuine defects — though this one was caught pre-commit by the operator
  procedure itself (matching `git diff` output against intent before
  committing), not by any gap in `workflow_state.py`'s own writers, so no
  separate finding file is warranted: the generic lesson (never call
  `json.dumps` against this repo's JSON files without `ensure_ascii=False`)
  is noted here for any future WF8b session performing a raw state write
  the same way.

  **Verification, real and read-only, after the commit**:
  `workflow_state.discover_checkpoint_commits(repo_root, "v2-1-dry-run",
  base_commit)` returned exactly `{"S-CP1":
  "ac1df008953b1ced594095a7034a6f2ec51799a4"}` — the trailer-discovery
  mechanism finds this checkpoint's commit correctly, not merely that the
  commit exists. `git show --name-only` on the commit lists exactly the
  three intended paths.

  **Not yet run**: checkpoint 2 (`S-CP2`) — its own fresh-session boundary
  (this scenario's explicit "do not run both checkpoints in one
  conversation" instruction) means it is the next scenario slice, from a
  separate session, not a continuation of this one.

## S7 — External implementation review returning APPROVE

- **Purpose**: prove `/milestone-implement` step 2-5 (self-review, full
  verification, `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` bundle,
  `record_bundle_generation` writing `reviewed_implementation_head`) once
  every scratch checkpoint is `COMPLETE` — checkpoint 1 and 2 from S6/S13,
  and checkpoint 3 from S15's recovery decision, which is the last of the
  three to close.
- **Initial phase/state**: `IMPLEMENTING`, all scratch checkpoints
  `COMPLETE`.
- **Precondition (implementation-stage classification)**: this is the dry
  run's **first** implementation-stage bundle, so it is the first point at
  which `v2-1-dry-run`'s own `implementation_stage` classification is
  exercised. `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` must
  classify every changed/untracked path — `classify_path_implementation_stage`
  fails closed, and `_implementation_stage_protected_changed_paths` runs it
  over the whole changed set, so a single unnamed path aborts generation
  before any bundle file is written. The declaration was populated for this
  in plan revision 3 (`LOCAL-DRY-R2-001`); the scratch marker files are
  protected, everything else this item can touch is excluded. If this step
  aborts with `UnclassifiedPathError`, the named path is a genuine gap in
  that declaration — record it and re-plan, do not widen the declaration
  ad hoc mid-run. **S6 has no such precondition**: it stops at one
  checkpoint commit per session and generates no bundle, so nothing before
  this scenario touches the implementation-stage classifier.
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

- **Outcome (2026-08-15, real, reached its own defined stop boundary)**: ran
  for real, in a fresh session (this session began via a real `/clear` +
  `/bootstrap-workflow-v2`, resuming purely from repository state), against
  live HEAD `910c2ccba95c3fc7b6faa0eace9e3b0181bd9e9f` — the same commit
  `S-CP3`'s completion (S15) left `HEAD` at. `phase` was already
  `SELF_REVIEWING_IMPLEMENTATION`, written by `complete_checkpoint` itself
  as part of `S-CP3`'s completing commit once every registry checkpoint
  became `COMPLETE` — confirmed this is the correct, only writer: no
  function anywhere in `workflow_state.py` ever sets `phase` to the literal
  string `"AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW"` (grepped for
  `["phase"] = ` across the whole module). `KNOWN_PHASES` lists it as a
  valid allowlist entry, but `technical_approval_gate_reachable` — the
  actual entry-condition function S9 depends on — never reads `phase` at
  all; it composes purely from `latest_round_status`, `protected_path_dirty`,
  and `head_matches_reviewed_implementation_head`. So "entering
  `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`" is real (the bundle exists,
  `reviewed_implementation_head` is set, the gate is genuinely awaited) but
  is never represented as a literal `phase` write for a `"2.1"` item —
  `phase` stays `SELF_REVIEWING_IMPLEMENTATION` through this whole window,
  by design, matching the module's own "allowlist, not a transition graph"
  comment. Not a defect: nothing downstream reads `phase` to decide this
  gate; recorded here because it is exactly the kind of easy-to-assume
  gap this dry run exists to surface.

  **Precondition** confirmed first, read-only: all 46 paths changed or
  newly untracked since `base_commit` (`e75a756d42751ef18eee842a958cc2c888086772`)
  classify successfully under `v2-1-dry-run-artifacts.json`'s
  `implementation_stage` declaration (`classify_path_implementation_stage`,
  direct probe, zero `UnclassifiedPathError`) — exactly 4 `protected`
  (`scratch/{a,b,c}.txt` plus the declarations file itself), 42 `excluded`
  (all concurrent `workflow-v2-1-core` `D-Checkpoint-Ownership` work in the
  same commit range).

  **Step 2 (self-review)**: reviewed the three scratch deliverables, the
  ledger, the registry, and the mapping — no missing requirements,
  migration risk, usability gap, or missing test beyond what the plan's own
  self-review already scoped. `workflow_state.discover_checkpoint_commits`
  confirmed exactly one commit per checkpoint id, matching
  `D-Commit-Provenance`'s exact-scoped-match requirement. No blocking or
  important findings.

  **Step 3 (verification)**: `git diff --name-only <base>...HEAD -- app/
  gradle/ build.gradle.kts settings.gradle.kts` returned empty — no
  Android/product code touched by any of the three checkpoints, so
  `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest`/
  `connectedDebugAndroidTest` do not apply and were not run (stated
  explicitly in `TEST_RESULTS.md`, not silently skipped). Ran the
  verification the plan's own self-review notes actually call for instead:
  file-existence/content check on all three scratch markers (pass) and the
  checkpoint-commit trailer-discovery check above.

  **Step 4 (bundle generation) — real ordering subtlety caught before any
  bad write**: `.claude/commands/milestone-implement.md` step 4 lists
  `record_bundle_generation` as its *last* bullet, after
  `prepare-ai-review.sh`. Running the script first for a scoped
  implementation-stage bundle fails closed instead:
  `scripts/prepare-ai-review.sh`'s round-identity preflight (`GPT-R42-001`/
  `GPT-R43-001`) requires `work_items[work_item_id].reviewed_implementation_head`
  to already equal the generation `HEAD_SHA` *before* the script runs, for
  any work item with a `WORKFLOW_STATE.json` entry — the script's own
  comment states this explicitly ("must already have been called and
  persisted... BEFORE this script runs, never after"). Confirmed by direct
  code read before attempting the wrong order (not by a failed run): the
  correct real sequence is `record_bundle_generation` **first** (persisted
  to `WORKFLOW_STATE.json`: `reviewed_implementation_head` `null` → HEAD,
  `implementation_revision` `null` → `1`, `state_revision` 19 → 20, diffed
  `-U0` to confirm exactly those three `v2-1-dry-run` fields changed,
  nothing else), *then* the four author-written files
  (`REVIEW_REQUEST.md`/`IMPLEMENTATION_SUMMARY.md`/`TEST_RESULTS.md`;
  `CONTEXT_FILES.txt` left as previously recorded — still accurate), *then*
  `./scripts/prepare-ai-review.sh e75a756... implementation v2-1-dry-run`.
  This is a genuine documentation gap in the command file (the step-4
  bullet order is misleading for any scoped `"2.1"` implementation/post-fix
  bundle), not a script defect — the script's own behavior is correct and
  intentional, and its guard is what caught the ordering before any wrong
  write happened. Filed as an observation here rather than a blocking
  finding, since this session avoided the wrong order by reading the code
  first; a future session following the command file's bullets literally,
  top to bottom, would hit `GUARD_STATUS: mismatch` and stop with a clear
  named error, not a silent corruption — worth a real command-doc fix
  eventually, out of scope to silently patch mid-dry-run per this
  document's own discipline.

  Ran real: `review_content_id` computed independently first
  (`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96`) and
  stated in `REVIEW_REQUEST.md` before generation, then
  `prepare-ai-review.sh` reproduced the identical value
  (write → recompute → equal) and `bundle_id`
  `618657d3533947fbf22cccf8ec243dce3109c58827d8f5815bb855b24f0297cf`
  (write → recompute → equal); rerunning the script a second time for the
  same HEAD reproduced both identifiers unchanged (idempotent
  regeneration, `GPT-R43-001`'s "same head, no bump" branch), exit 0 both
  times, reproducibility check (on-disk vs. archive-extracted) passed
  silently both runs. Bundle at `.ai-review/v2-1-dry-run/current/`
  (archive: `.ai-review/v2-1-dry-run/review-bundle.tar.gz`).

  **Not yet run**: an actual external implementation-review verdict — S7's
  own stopping point is the bundle existing and `reviewed_implementation_head`
  matching live HEAD, per its own "Pass/fail evidence" line; obtaining a
  real `REVISE` verdict with a genuine planted defect (S8's own setup) is
  next, from a later session.

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

- **Outcome (2026-08-15, real, reached its own defined stop boundary)**: ran
  for real, continuing the same session that executed S7, against live HEAD
  `910c2ccba95c3fc7b6faa0eace9e3b0181bd9e9f` (S7's own bundle-generation
  head, unchanged since).

  **Seeding a genuine defect, honestly**: a transient working-tree-only edit
  (plant then immediately revert) was tried first and rejected on review —
  it would leave the "fix" byte-identical to the never-touched committed
  content, with no diff for step 6's required fix commit to carry, and it
  would make the finding's own claim ("the bundle contains this defect")
  false, since a bundle's `files/` snapshot is frozen at generation time and
  cannot retroactively pick up a later uncommitted edit. Resolved by
  actually **committing** the defect first (`b8d4899`, "plant a real S8
  defect in v2-1-dry-run's S-CP3 marker") — `docs/ai-workflow/dry-run/scratch/c.txt`
  changed from "checkpoint 3" (correct, `S-CP3`'s own original completion
  commit `ac1df00`) to "checkpoint 2" (a plausible copy-paste mistake from
  `b.txt`'s text). `<feedback_dir>/REVIEW_FEEDBACK.md` was written
  referencing S7's already-generated bundle identity exactly
  (`bundle_id 618657d3...`, `base_commit e75a756d...`,
  `work_item v2-1-dry-run` — all still valid, since bundle identity binds
  to captured content and metadata, not to what happens at later commits)
  and states explicitly, in its own "Verification" section, that the S7
  bundle's frozen snapshot does **not** contain this defect — so the
  finding cannot be misread as evidence the archived, already-reviewed
  bundle was itself wrong.

  **Step 0** (dual-mode branch): `governing_workflow_version: "2.1"`
  confirmed from `WORKFLOW_STATE.json`; no version-specific behavior beyond
  the exit-state naming, per the command's own text.

  **Step 1** (binding-field validation): `parse_review_feedback_binding_fields`
  + `assert_feedback_matches_bundle`, called for real against the live
  feedback file and the current `MANIFEST.md`'s recorded identifiers — no
  exception, `status: REVISE`.

  **Step 2** (reproduce before fixing): `git show b8d4899:docs/ai-workflow/dry-run/scratch/c.txt`
  confirmed the committed defect directly, before changing anything — not
  the reviewer's word taken at face value.

  **Step 3** (fix): `docs/ai-workflow/dry-run/scratch/c.txt` corrected back
  to "checkpoint 3", byte-identical to `ac1df00`'s content.

  **Step 4**: no rejected findings — the one Important finding was
  validated and fixed as-is.

  **Step 5** (verification): `git diff --name-only b8d4899 -- app/ gradle/
  build.gradle.kts settings.gradle.kts` empty, so the standard Android
  suite (`/milestone-implement` step 3) does not apply and was not run,
  same reasoning as S7; narrow verification is the direct content re-check
  above.

  **Step 6** (commit): one coherent fix commit, `ae7ef4c250a638fe358bd89d54bc3271bc401bd2`
  ("fix(wf8b): correct v2-1-dry-run S-CP3 scratch marker per S8 finding"),
  no special trailer (matching the precedent of every real
  `workflow-v2-1-core` implementation-review fix commit in this
  repository's own history — e.g. `c98e7e6`, `7bef596` — neither of which
  carries a `Workflow-Checkpoint` trailer, since a review-feedback fix
  commit is not itself a checkpoint-completion event).

  **Step 7** (bundle regeneration, correct order per S7's own established
  finding): `record_bundle_generation(state, "v2-1-dry-run",
  stage="post-fix", head="ae7ef4c...", now=...)` called and persisted
  first (`reviewed_implementation_head` `910c2ccb...` → `ae7ef4c...`,
  `implementation_revision` `1` → `2`, `state_revision` `20` → `21`, `git
  diff -U0` confirmed exactly those three `v2-1-dry-run` fields changed);
  the four author-written files updated to describe this round; then
  `./scripts/prepare-ai-review.sh e75a756... post-fix v2-1-dry-run`, run
  twice for idempotency. **Real, independently interesting result**: this
  round's `review_content_id` is **unchanged** from S7's
  (`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96`) —
  independently recomputed via `compute_review_content_id_implementation_stage`
  before writing `REVIEW_REQUEST.md`, then reproduced identically by the
  script's own write→recompute check, because the fix restored the
  protected content to be byte-identical to what S7 already hashed. Only
  `bundle_id` advances (`618657d3...` → `b98f2461...`, since it also binds
  `reviewed_implementation_head`/`generation_head`/`implementation_revision`).
  This is a real, exercised instance of `D-Commit-Provenance`'s
  "Same-content post-fix republication" branch, not a hermetic-test-only
  claim.

  **Step 8** (readiness report): `reviewed_implementation_head` (`ae7ef4c...`)
  now equals live HEAD exactly — independently re-verified read-only after
  the state write. `AWAITING_TECHNICAL_APPROVAL` is reachable; per the
  command's own text, `/approve-review implementation` was **not**
  auto-invoked — that remains the user's own call, for a later session.

  **Not yet run**: `/approve-review implementation v2-1-dry-run` (**S9**'s
  own first user-gated action) — next, from a later session.

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

- **Outcome (2026-08-15, real, blocked before mutation, then remediated)**:
  the user invoked `/approve-review implementation v2-1-dry-run` for real.
  Step 1's gate-reachability check refused: the then-installed
  `head_matches_reviewed_implementation_head` argument was a bare
  `reviewed_implementation_head == HEAD` equality
  (`ae7ef4c...` vs live HEAD `34ce1dd...`, one commit further — the docs
  commit recording S8's own outcome) — a live recurrence of the already-
  tracked `WF8B-003` finding, whose plan-approved revision-28 fix
  (splitting `reviewed_implementation_head`/`generation_head` via a
  dedicated `Workflow-Bundle-Generation-Record` provenance commit) had
  never actually been implemented in code. No write was attempted. Full
  detail, reproduction, and remediation in
  `WF8B_S9_FINDING_provenance_gate_never_implemented.md`: the provenance-
  interval mechanism was implemented for real in `scripts/workflow_state.py`
  (8 new hermetic tests, `TestImplementationProvenanceInterval`, full
  suite green except the same pre-existing unrelated failures every prior
  session has already flagged), and `.claude/commands/approve-review.md`/
  `milestone-implement.md`/`apply-implementation-review.md`/
  `apply-functional-review.md` were updated to build and consume it. S9
  was **not** forced through by resyncing state to fake the old
  equality rule — the generic mechanism the plan already specified was
  built instead, per the user's own explicit instruction. A fresh
  operator session may retry `/approve-review implementation
  v2-1-dry-run` once the remediation's own dedicated
  `Workflow-Bundle-Generation-Record` commit exists for `v2-1-dry-run`'s
  current `implementation_revision` (created by the corrected
  `/apply-implementation-review`/`/milestone-implement` ordering, not a
  manual workaround).

- **Outcome (2026-08-15, real, `/approve-review implementation` succeeded)**:
  the user re-invoked `/approve-review implementation v2-1-dry-run` in the
  same session, after the remediation above landed. Two further real gaps
  surfaced and were fixed in the same investigation, each discovered only
  by actually attempting the next step, never by inspection alone:
  `scripts/prepare-ai-review.sh`'s own round-identity preflight
  (`GPT-R42-001`/`GPT-R43-001`) independently assumed the same bare
  `reviewed_implementation_head == head_sha` equality the gate itself no
  longer requires — fixed to fall back to the same interval check, with a
  new positive hermetic test (`7c1032c`); and every commit landing after a
  round's own generation-record commit (including the remediation commits
  themselves) reopens the gap by construction, requiring a fresh, real
  `record_bundle_generation` round each time rather than a resync — rounds
  3 and 4 (`70d3559`, `74e001a`) were both genuine, not fabricated. The
  command's own steps were then executed by hand (real function calls,
  since `/approve-review` is `disable-model-invocation: true` and user-only
  by construction): step 1's gate returned reachable for the first time;
  step 2's `assert_local_generation_matches` raised nothing (bundle
  regenerated immediately beforehand, no intervening commit); step 3
  resolved `USER_OVERRIDE` (S8's `REVISE` round was never superseded by a
  formal `APPROVE`, matching S5's own plan-approval precedent); step 5
  persisted `technical_approval` (`basis: USER_OVERRIDE`,
  `reviewed_content_commit: 7c1032c...`) and transitioned `phase` to
  `AWAITING_FUNCTIONAL_REVIEW`; step 6 created the metadata-only approval
  commit `8b72452` (`Workflow-Technical-Approval` +
  `Workflow-Work-Item` trailers, `WORKFLOW_STATE.json` alone); step 6a's
  `verify_post_approval_manifest_match` raised nothing. Full suite green
  (637 tests) except the same pre-existing, unrelated WFR-row-count
  failure. **S9's implementation-approval half is complete for real.**
  Not yet run: `/prepare-functional-review v2-1-dry-run` and the final
  `/accept-milestone` — per this document's own reordering note, S10
  (functional finding + `apply-functional-review` remediation) should run
  next, ahead of final acceptance.

- **Outcome (2026-08-15, real, `/prepare-functional-review v2-1-dry-run`
  succeeded)**: run for real in the same session. Step 0 resolved the
  named target `v2-1-dry-run`; step 0a's `LEGACY_READY` adoption scan was
  skipped (`phase` was already `AWAITING_FUNCTIONAL_REVIEW`, not
  `LEGACY_READY` — S9's own `/approve-review implementation` had already
  performed that transition). Step 1's automated-verification recheck ran
  independently: 655/656 across the five hermetic suites, the one failure
  being the same pre-existing, unrelated `workflow_integration_test.py`
  WFR-row-count staleness (67 vs. an assertion still pegged at 60) this
  document has already flagged repeatedly — no source/test regression.
  Steps 2-3 wrote a new, distinct `## v2-1-dry-run — functional review
  checklist (implementation revision 4)` section into the real
  `docs/ACTIVE_MILESTONE.md`, appended after the existing Milestone 8 and
  `workflow-v2-1-core` sections without touching either — this is the
  documented, plan-approved (`D-Scoped-Remediation-Acceptance`) shared use
  of that one fixed `functional_checklist_path`, not the S11-flagged
  cross-item collision (S11's own concern is about a *different*,
  unrelated throwaway item's adoption path incorrectly touching this same
  file; `v2-1-dry-run`'s own functional review genuinely belongs in this
  file by the plan's own design). Step 3a's provenance commit ran by hand
  (real function calls, since `/prepare-functional-review` is a real,
  non-gated command but has no direct CLI entry point here): computed
  blob `01fc1f317354619db04fca72a6db41a0a8e6a412` via `git hash-object`;
  `workflow_state.discover_current_functional_checklist_evidence(...,
  "v2-1-dry-run", base_commit, head=bc0770b, implementation_revision=4)`
  returned `None` (no prior evidence for this round); staged **only**
  `docs/ACTIVE_MILESTONE.md` (confirmed via `git status --short` that the
  pre-existing, deliberately-uncommitted `scratch/c.txt` and
  `verify_review_content_id.py` were left untouched) and committed
  `d22bdf4` carrying `Workflow-Functional-Checklist:
  v2-1-dry-run/4/01fc1f317354619db04fca72a6db41a0a8e6a412` +
  `Workflow-Work-Item: v2-1-dry-run`. **S9's functional-review-preparation
  half is complete for real.** `v2-1-dry-run` has no outstanding
  checkpoint at this point (`S-CP1`-`S-CP3` all `COMPLETE`), so step 4's
  `/accept-scoped-remediation` evidence-binding instruction does not apply
  to this invocation. Not yet run: S10 (functional finding +
  `/apply-functional-review` bounded remediation, per this document's own
  reordering note) and S9's own final `/accept-milestone` step, both
  deferred to a following session.

- **Outcome (2026-08-15, real, `/prepare-functional-review v2-1-dry-run`
  rerun for real after S10's bounded remediation, in a fresh
  `/bootstrap-workflow-v2` session)**: continuing from S10's own second
  outcome block's stop point, real HEAD `c6704d7...`. `technical_approval`
  had gone `STALE` → `CURRENT` again (S10's forced repeat approval,
  `reviewed_content_commit: c916ead`, approval commit `9fd3c72`), so this
  invocation's step 0/0a resolved `v2-1-dry-run` at
  `phase: AWAITING_FUNCTIONAL_REVIEW` (never `LEGACY_READY`), skipping
  straight to step 1 exactly as the first S9 run did. Step 1's
  automated-verification recheck ran independently, for real, all five
  hermetic suites individually: 122/122, 60/60, 408/408, 19/19, 46/47 —
  655/656 total, the one failure the same pre-existing, unrelated
  `test_every_wfr_row_description_matches_json_exactly` staleness (67
  actual table rows vs. an assertion pegged at 60) every prior session has
  already flagged, independently re-confirmed via `-v` traceback, not a
  new regression. Steps 2-3 wrote a new, distinct
  `## v2-1-dry-run — functional review checklist (implementation revision
  6)` section into the real `docs/ACTIVE_MILESTONE.md`, appended after the
  existing revision-4 section without editing it — including a
  correction, made honestly rather than silently: the revision-4 section's
  own flow 1 had claimed `scratch/c.txt` was "deliberately not tracked
  yet," which a direct `git ls-files` check at this session's start showed
  is no longer (and, checked against the commit graph, was never actually
  true at revision-4's own write time either — `S-CP3`'s completion commit
  `8b70136` predates every commit that section's own text was written
  against) an accurate description; the new revision-6 section states the
  corrected fact directly rather than perpetuating it, and the revision-4
  section itself was left untouched, per this document's own
  "append, don't edit" discipline. Step 3a's provenance commit ran by hand
  (same reasoning as S9's first run — no direct CLI entry point here):
  computed blob `f3e0648caf54969dc07e93b21a1ed0e5005fac16` via
  `git hash-object`; `workflow_state.discover_current_functional_checklist_evidence(...,
  "v2-1-dry-run", base_commit, head=c6704d7, implementation_revision=6)`
  returned `None` (no prior evidence for this round); `git status --short`
  confirmed only `docs/ACTIVE_MILESTONE.md` was modified (the pre-existing,
  deliberately-uncommitted `verify_review_content_id.py` untouched); staged
  and committed **only** that path as `1440ec0`, carrying
  `Workflow-Functional-Checklist:
  v2-1-dry-run/6/f3e0648caf54969dc07e93b21a1ed0e5005fac16` +
  `Workflow-Work-Item: v2-1-dry-run`. **S9's functional-review-preparation
  half is now complete for real at implementation revision 6.** Per step 5,
  reported and stopped — this is a hard gate for the user's own manual
  testing. Not yet run: S9's own final `/accept-milestone` step (user-gated,
  **deliberately not attempted by this session** — S11-S12 are unaffected
  by acceptance ordering and could run first, but S9's own scenario text
  scopes acceptance to *after* S10/S12, so this session did not reorder
  further on its own judgment) and S11-S17, deferred to a following
  session.

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

- **Outcome (2026-08-15, real, reached its own defined stop boundary)**: ran
  for real, continuing the same session that finished S9, against live HEAD
  `63b47f0...` (S9's own last commit).

  **Seeding a genuine defect, honestly** (same discipline as S8): a
  committed defect, not a transient edit. `docs/ai-workflow/dry-run/scratch/b.txt`
  (`S-CP2`'s own marker, correct since its completion commit `8375b64`)
  changed from "checkpoint 2" to "checkpoint 1" — a plausible copy-paste
  mistake from `a.txt`'s text, committed at `3d2f3fd` ("plant a real S10
  defect in v2-1-dry-run's S-CP2 marker"). `<feedback_dir>/FUNCTIONAL_REVIEW.md`
  was written as a real, free-form functional-review finding (role
  `functional_review`, no binding-field requirement per
  `docs/ai-workflow/REVIEW_PROTOCOL.md` — functional review has no
  `bundle_id`/`review_content_id` of its own), classified as one Important
  **defect** finding against flow 1 of `v2-1-dry-run`'s own functional
  checklist in `docs/ACTIVE_MILESTONE.md`.

  **`D-Functional-Remediation`'s bounded branch, exercised for real**:
  1. **Stale-before-edit ordering**: `workflow_state.mark_technical_approval_stale(state,
     "v2-1-dry-run", now=...)` called and persisted to
     `docs/ai-workflow/WORKFLOW_STATE.json` on disk — `technical_approval.status`
     `CURRENT` → `STALE`, `state_revision` `25` → `26` — **before** touching
     `scratch/b.txt`. Reproduced the actual committed defect first (`git
     show 3d2f3fd:docs/ai-workflow/dry-run/scratch/b.txt`) before making
     any edit.
  2. Fixed `scratch/b.txt` back to "checkpoint 2", verified byte-identical
     to `8375b64`'s original content.
  3. Committed the stale `WORKFLOW_STATE.json` write and the fix together,
     one coherent commit, `fae7420` ("fix(wf8b): correct v2-1-dry-run
     S-CP2 scratch marker per S10 finding") — `git show
     fae7420:docs/ai-workflow/WORKFLOW_STATE.json` independently confirmed
     `technical_approval.status` reads `STALE` in that exact commit,
     satisfying this scenario's own pass/fail evidence line.
  4. Bundle regeneration, post-fix: `record_bundle_generation(state,
     "v2-1-dry-run", stage="post-fix", head="fae7420...", now=...)` called
     and persisted **first**, in its own dedicated commit touching only
     `WORKFLOW_STATE.json` (`0f3ef83`, `Workflow-Bundle-Generation-Record:
     v2-1-dry-run/5` + `Workflow-Work-Item: v2-1-dry-run`) — before
     regenerating, per S7's already-established correct ordering.
     `reviewed_implementation_head` `7c1032c...` → `fae7420...`,
     `implementation_revision` `4` → `5`. `technical_approval.status`
     remains `STALE` (this call never writes it, `D-Approval-Commits`'
     sole-writer split confirmed for real). The four author-written files
     were rewritten to describe this round's real finding/fix (unlike
     rounds 3/4, which had no new content to describe); `./scripts/prepare-ai-review.sh
     e75a756... post-fix v2-1-dry-run` then run twice for idempotency —
     both runs reproduced `review_content_id` `33139aaf...` (**unchanged**,
     a second real "same-content post-fix republication" instance,
     independently recomputed before either run) and the same `bundle_id`
     `6b884729...` both times.

  **Not yet run**: per the command's own step 5 ("do not proceed to
  step 6/7... the stop is for the whole round"), this invocation stops
  here. A fresh implementation-review round (`REVIEW_FEEDBACK.md` +
  `/apply-implementation-review v2-1-dry-run`) against this new
  `post-fix` round-5 bundle, then the repeat, user-gated
  `/approve-review implementation v2-1-dry-run` this scenario's own
  "Expected state transition" line requires, are both deferred to a
  following session — `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` is
  reachable, not yet `AWAITING_TECHNICAL_APPROVAL`. S10's own bounded
  branch is otherwise complete for real.

- **Outcome (2026-08-15, real, S10's deferred items completed for real, in a
  fresh `/bootstrap-workflow-v2` session)**: continuing from the previous
  session's stop point, real HEAD `c916ead...`.

  **Fresh implementation-review round, real**: `<feedback_dir>/
  REVIEW_FEEDBACK.md` was written against round 5's bundle (`bundle_id
  6b884729...`, `generation_head 0f3ef83...`) — direct re-inspection of
  all three scratch markers (`a.txt`/`b.txt`/`c.txt`, all confirmed
  correct), `Status: APPROVE`, zero Blocking, zero Important findings.
  Validated for real: `workflow_fingerprint.parse_review_feedback_binding_fields`
  + `assert_feedback_matches_bundle`, no exception. `/apply-implementation-review`'s
  steps 2-4 (reproduce/fix/reject) were no-ops (nothing to reproduce, fix,
  or reject); step 5's full hermetic suite re-run: 655/656 green, the one
  failure the same pre-existing, unrelated `workflow_integration_test.py`
  WFR-row-count staleness every prior session has already flagged; step 6
  (commit fixes): none, there was no fix.

  **Provenance-gap closure, round 6**: round 5's own outcome-recording docs
  commit (`c916ead`, excluded-path) had landed after round 5's own
  `Workflow-Bundle-Generation-Record` commit (`0f3ef83`), reopening the
  `WF8B-003` interval gap by construction — the exact recurring pattern S9's
  own outcome text already documented. `record_bundle_generation(state,
  "v2-1-dry-run", stage="post-fix", head="c916ead...", now=...)` called and
  persisted first, alone, in its own commit (`c11ec01`,
  `Workflow-Bundle-Generation-Record: v2-1-dry-run/6` +
  `Workflow-Work-Item: v2-1-dry-run`) — `reviewed_implementation_head`
  `fae7420...` → `c916ead...`, `implementation_revision` `5` → `6`. The
  four author-written bundle files were updated to describe this round;
  `./scripts/prepare-ai-review.sh e75a756... post-fix v2-1-dry-run` then
  run twice for idempotency — both runs reproduced `review_content_id`
  `33139aaf...` (**unchanged**, a third real "same-content post-fix
  republication" instance) and the same `bundle_id` `ba997396...` both
  times. Independently verified, live, with zero commits between the
  record commit and generation: `implementation_provenance_interval_reachable`
  → `True`, `any_protected_path_dirty` → `False`,
  `technical_approval_gate_reachable` → `True`.

  **`/approve-review implementation v2-1-dry-run`, real, user-invoked**: the
  user directly invoked the installed command this session (literal
  confirmation `/approve-review implementation v2-1-dry-run`, satisfying
  `validate_user_confirmation`'s named-item/named-stage requirement). Ran
  by hand (real function calls, `disable-model-invocation: true`,
  user-only by construction): step 1's gate check confirmed reachable
  (identical to the live check above); step 2's
  `assert_local_generation_matches` raised nothing (zero intervening
  commits since round 6's regeneration); step 3's `resolve_approval_basis`
  → `USER_OVERRIDE` (round 6's recomputed `bundle_id` `ba997396...`
  differs from `REVIEW_FEEDBACK.md`'s reviewed `bundle_id` `6b884729...`,
  since that feedback reviewed round 5's bundle and round 6 exists purely
  to close the provenance gap, not to re-review new content — same pattern
  as S9's own approval); step 5 persisted `technical_approval`
  (`basis: USER_OVERRIDE`, `reviewed_content_commit: c916ead...`,
  `status: STALE → CURRENT`), `phase` stays `AWAITING_FUNCTIONAL_REVIEW`
  (unchanged, per S7's own established finding — nothing downstream reads
  `phase` to decide this gate); step 6 created the metadata-only approval
  commit `9fd3c72` (`Workflow-Technical-Approval` + `Workflow-Work-Item`
  trailers, `WORKFLOW_STATE.json` alone); step 6a's
  `verify_post_approval_manifest_match` raised nothing. **S10's own
  "Expected state transition" line is now fully satisfied for real —
  `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` → (fresh review round) →
  `AWAITING_TECHNICAL_APPROVAL` → `/approve-review implementation` →
  `AWAITING_FUNCTIONAL_REVIEW`.**

  Not yet run: S9's own still-deferred final steps
  (`/prepare-functional-review v2-1-dry-run` for this new revision, and the
  final `/accept-milestone`), and S11-S17 — next, from a following session.

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
  completes checkpoint 2 normally into S6/S7's flow. **Checkpoint 2 is
  `COMPLETE` from the end of this scenario onward** and is never the subject
  of a later scenario — S14 and S15 use checkpoint 3, which this invocation's
  own one-checkpoint hard stop leaves untouched.
- **Real approval gate**: none.
- **Fresh-session boundary**: **required** — this is the actual claim being
  tested.
- **Pass/fail evidence**: no `WorktreeIdentityMismatchError`; the
  uncommitted work from before the session boundary is intact and gets
  committed as checkpoint 2's completion commit.
- **Cleanup**: none.

### S14/S15 setup step (run after S13, in worktree A)

S13 leaves no checkpoint `IN_PROGRESS`, so the state S14 and S15 both need is
created here, once, by a real command: from a fresh session **in worktree A**,
run `/milestone-implement v2-1-dry-run`. It fresh-starts checkpoint 3
(`IN_PROGRESS`, `WORKTREE_IDENTITY.json` refreshed for worktree A) — then stop
the session deliberately without completing it, exactly as S6 did for
checkpoint 2.

That single interrupted checkpoint 3 is the subject of S14 and then, unchanged,
of S15. Reusing it across both is sound precisely because S14 is required to
mutate nothing (its authorization point 3, verified immediately after the
refusal). Record checkpoint 3's `status`/`start_commit` and
`WORKTREE_IDENTITY.json` bytes here, so S14's no-mutation claim can be checked
against a recorded before-state rather than asserted.

**Additional setup action, added by `workflow-v2-1-core` revision 63
(`D-Checkpoint-Ownership`): adopt the claim, in worktree A, before S14 runs.**
Checkpoint 3 was started before the cross-worktree ownership contract existed,
so no shared claim was published for it. Without one, worktree B would still
classify it as a fresh start — the adoption path exists precisely for
checkpoints interrupted before the mechanism landed, and this is the one in
this repository. Once `WF8b` has implemented the contract, run the adoption
operation for `v2-1-dry-run` **from worktree A**:

- it is guarded so only the originating worktree can run it — it requires the
  local state to record checkpoint 3 `IN_PROGRESS` and
  `verify_dirty_resume_safety` to pass, so a foreign worktree cannot adopt;
- it writes **no** authoritative state: `WORKFLOW_STATE.json`,
  `.ai-review/runtime/WORKTREE_IDENTITY.json`, `scratch/c.txt`, `HEAD` and
  `git status` must all be byte-identical across it, and that must be
  **verified**, not assumed. Checkpoint 3 stays `IN_PROGRESS` at
  `state_revision` 18 exactly as recorded above;
- the only thing it creates is the shared claim record under
  `.git/ai-workflow/checkpoint-claims/`, which lies outside every worktree's
  working tree and therefore appears in no `git status`, no bundle, and no
  content identity;
- it implements nothing. This is deliberate: S15 still needs checkpoint 3
  interrupted, so adoption must protect the checkpoint **without** resuming it.
  Running `/milestone-implement` from worktree A to obtain the claim would
  complete checkpoint 3 and destroy S15's premise.

Record the claim record's path and bytes here too, so S14 can check them
unchanged afterwards.

- **Outcome (real, ran, deliberately left interrupted)**: a prior session
  fresh-started checkpoint 3 in worktree A, per this step's own procedure:
  `current_checkpoint_id` → `"S-CP3"`, `checkpoints["S-CP3"] = {"status":
  "IN_PROGRESS", "start_commit": "8375b64f9ad9ad44afe7574841a62457f5d83cea"}`
  (`WORKFLOW_STATE.json` `state_revision` 17 → 18), `.ai-review/runtime/WORKTREE_IDENTITY.json`
  refreshed with a `v2-1-dry-run` entry pointing at worktree A
  (`generated_at: "2026-08-12T12:55:14+01:00"`, sha256
  `8482417e1dcfa8ca85f00b64224fb9a79c4805bdd05727b4ac1897efd4b4515e`), and
  `docs/ai-workflow/dry-run/scratch/c.txt` created (uncommitted). All left
  exactly there, deliberately, with no completion commit — confirmed
  independently by a fresh session (this one): `git show
  8375b64:docs/ai-workflow/WORKFLOW_STATE.json`'s `v2-1-dry-run` entry has
  no `S-CP3` key at all (fresh-start bookkeeping is a working-tree-only
  write, never committed by design at this step), and `git status --short`
  in worktree A shows exactly the modified `WORKFLOW_STATE.json` plus the
  untracked `scratch/c.txt` (and the pre-existing, unrelated
  `verify_review_content_id.py`). Before-state recorded here for S14/S15 to
  check against.

- **Additional setup action, outcome (real, 2026-08-15, sixth `D-Checkpoint-Ownership`
  session, worktree A)**: confirmed first, read-only, that ordinary
  `adopt_claim` genuinely cannot protect the real checkpoint 3, exactly as
  the fourth session's own demo-test evidence predicted:
  `checkpoint_origination_provable(repo_root, "v2-1-dry-run", "S-CP3", ...)`
  raised `CheckpointOriginationUnprovableError` with `{"route": "observed",
  "commit": "8f8d878c0985da96d9b462703b6c88ec5b3ab07b", "status":
  "IN_PROGRESS", "examined_commits": 32}` -- inspecting that commit
  (`docs(workflow-v2): record Revision 80 plan approval`) confirms it swept
  the *whole* `WORKFLOW_STATE.json` file into a `workflow-v2-1-core`-scoped
  docs commit and incidentally captured `v2-1-dry-run`'s then-dirty
  `S-CP3: IN_PROGRESS` bookkeeping in real committed history, even though
  that write was always meant to stay working-tree-only. So this setup step
  used **explicit takeover**, not adoption, per the fourth session's own
  conclusion that only takeover can protect this specific checkpoint.
  `takeover_evidence(repo_root, "v2-1-dry-run")` was computed read-only:
  no claim, no guard held (`claim_observation_id: "absent"`), this
  worktree's own `local_identity` valid and matching the setup step's
  recorded `WORKTREE_IDENTITY.json` sha256 (`8482417e...4b4515e`) exactly.
  `takeover_authorization_literal("v2-1-dry-run", evidence, "S-CP3")`
  resolved to `"take over v2-1-dry-run claim absent holding none as
  S-CP3"`; the evidence was shown to the user and the user typed that exact
  literal back (asked once to select authorize/decline, then asked again
  to actually supply the literal itself, since a selection alone is not
  the reviewed-evidence confirmation this gate requires).
  `take_over_claim(repo_root, "v2-1-dry-run", "S-CP3",
  now="2026-08-15T01:49:38+01:00", user_authorization=<that literal>)`
  ran for real and returned a fresh claim record
  (`owner_token 7e6e5521e8609cbb2e8b97265063f376`, `takeover_count: 1`,
  `adopted: false`) published at
  `.git/ai-workflow/checkpoint-claims/ee28c6f2...329a182.json` -- outside
  every worktree's working tree, confirmed by `git check-ignore` reporting
  the path as not inside this repository at all.

  Verified by exact byte comparison, before vs. after: `docs/ai-workflow/
  WORKFLOW_STATE.json` (`eff46735...9f91`), `docs/ai-workflow/dry-run/
  scratch/c.txt` (`e9131917...96385`), `git status --short`, and `git
  rev-parse HEAD` (`d5ad89d...`) are all byte-identical -- no authoritative
  state moved. **One deviation from this step's own description, recorded
  rather than smoothed over**: `.ai-review/runtime/WORKTREE_IDENTITY.json`
  is *not* byte-identical (`8482417e...` -> `a52fad7a...`), because
  `take_over_claim` (unlike `adopt_claim`) calls
  `_establish_or_repair_identity` unconditionally to establish the taking
  worktree's own identity record -- this step's text assumed ordinary
  adoption, which never touches that file, and did not anticipate takeover
  doing so. The diff is a `generated_at` refresh plus a
  `expected_dirty_paths_by_work_item["v2-1-dry-run"]` recompute against
  *current* real dirty state (`scratch/c.txt` + `verify_review_content_id.py`,
  dropping the now-clean `WORKFLOW_STATE.json` entry) rather than the
  values recorded when the checkpoint started -- content
  `verify_dirty_resume_safety` never inspects per its own docstring, so
  this does not affect worktree A's own future resumability, but it is a
  genuine, if harmless, behavioral gap between this step's specified
  contract and `take_over_claim`'s actual one, worth a plan/command-doc
  note, not itself a blocking defect for S14/S15.

## S14 — Dirty IN_PROGRESS resume attempted from a mismatched worktree, refused

- **Purpose**: prove that a genuine linked worktree B, possessing only
  committed repository state plus the shared coordination record, **refuses
  before it can start the checkpoint or mutate any authoritative state** — and
  that `verify_dirty_resume_safety`'s failure path stops cleanly rather than
  silently resuming or silently discarding.

  **This remains an end-to-end `/milestone-implement` test.** It is not
  narrowed into a direct call of `verify_dirty_resume_safety`, and it does not
  copy worktree A's `WORKFLOW_STATE.json` into B. Both of those were considered
  as repairs when this scenario was found unexecutable, and both were rejected:
  the first tests a function instead of the command's actual routing, which is
  exactly where the defect lived, and the second manufactures the very state
  whose absence is the point. Under `workflow-v2-1-core` revision 63's
  `D-Checkpoint-Ownership`, B reaches the refusal on its own — the claim
  adopted in worktree A during the setup step lives in the **shared Git common
  directory**, which B resolves to the same absolute path by construction, so
  B's own step 1c discovers that the work item is claimed and routes into
  `verify_dirty_resume_safety` **before** the fresh-start branch it would
  otherwise have taken. B's `WORKFLOW_STATE.json` stays byte-identical to its
  own committed bytes throughout.

  **Two distinct refusals, both required.** `.ai-review/` is gitignored
  (`.gitignore:1`) and nothing under it is tracked, so a worktree created by
  `git worktree add` has no `.ai-review/runtime/WORKTREE_IDENTITY.json` at
  all. Run exactly as described below, worktree B therefore raises
  **`WorktreeIdentityMissingError`**, not the mismatch error — verified
  mechanically against real Git worktrees. Prove both:
  - **S14a — missing identity**: invoke from worktree B as
    `git worktree add` leaves it. Expect `WorktreeIdentityMissingError`
    ("…not found — cannot resume … from a worktree with no local identity
    record").
  - **S14b — mismatched identity**: copy worktree A's
    `.ai-review/runtime/WORKTREE_IDENTITY.json` into worktree B (the
    realistic copied-workspace case), then invoke again. Expect
    **`WorktreeIdentityMismatchError`** ("…records a different worktree's
    identity than this one…"). Writing that one gitignored file inside the
    throwaway worktree B is not a repository mutation and is removed with B
    at S17; point 2's no-mutation rule is about authoritative state and
    worktree A, both untouched here.
- **Authorization (locked in, user-approved — see "Decisions locked in for
  execution" above)**: creating a second real Git worktree solely for this
  scenario is approved, subject to all of the following, each of which must
  be recorded in this scenario's outcome notes when run:
  1. **Isolation**: an isolated temporary branch and path — e.g.
     `git worktree add /tmp/<scratch-path>/wf8b-s14-worktree -b
     wf8b-s14-scratch <current HEAD>` — never the branch or worktree any
     other scenario uses.
  2. **No mutation from that worktree**: the only actions taken from worktree
     B are the two refused `/milestone-implement v2-1-dry-run` invocations
     (S14a and S14b) against checkpoint 3 — the one the "S14/S15 setup step"
     above left `IN_PROGRESS` in worktree A — plus, between them, copying
     worktree A's gitignored `WORKTREE_IDENTITY.json` into B to set up S14b.
     Not checkpoint 2: S13 has already completed checkpoint 2, so it is
     `COMPLETE` and not resumable at all by this point, which is why a second
     dirty checkpoint exists. No commits, no tracked-file edits, no writes of
     any kind outside worktree B, and nothing else run from worktree B.
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
- **Initial phase/state**: checkpoint 3 `IN_PROGRESS` with
  `WORKTREE_IDENTITY.json` pointing at worktree A (the primary worktree), per
  the "S14/S15 setup step" above; command invoked from worktree B (the
  temporary one).
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real) from
  worktree B, resolving to checkpoint 3.
- **Expected state transition**: none — hard stop, no state mutation.
  Checkpoint 3 must still be `IN_PROGRESS`, byte-for-byte as the setup step
  recorded it, when this scenario ends; S15 depends on that.
- **Real approval gate**: none.
- **Fresh-session boundary**: not required (a fresh session in a different
  worktree already gets there naturally).
- **Pass/fail evidence**: the expected refusal class is raised for each of
  S14a and S14b; no commit, no state-file write — verified per point 3 above,
  not just claimed; and checkpoint 3 is still `IN_PROGRESS`, byte-identical to
  the setup step's recorded before-state, afterwards.

  **Additional evidence required by revision 63's architecture**, each checked
  rather than asserted:
  - B never reached the fresh-start branch: B's own `WORKFLOW_STATE.json` has
    no `S-CP3` entry afterwards and is byte-identical to
    `git show HEAD:docs/ai-workflow/WORKFLOW_STATE.json` in B;
  - B created no `.ai-review/runtime/WORKTREE_IDENTITY.json` of its own in the
    S14a case (the S14b case has only the one copied in, unchanged);
  - the shared claim record is byte-identical before and after both
    invocations — a refusal must neither release, refresh, nor overwrite it —
    and no stray staging temp file appears beside it;
  - the refusal names the claim holder (worktree A's path and checkpoint 3) and
    points at the explicit takeover as the only escape. This is the ownership
    evidence the refusal carries; record the exact text, and note that the
    underlying error *class* is still `D3`'s, which is what keeps S14a and S14b
    distinguishable.

  Note the error *messages* name neither worktree's concrete identity — both
  are descriptive ("a different worktree's identity than this one"). Do not
  record "the error names both values" as evidence; it does not. To evidence
  the identity comparison itself, print worktree B's
  `.ai-review/runtime/WORKTREE_IDENTITY.json` alongside `git rev-parse
  --show-toplevel --git-common-dir` from both worktrees and record those.
- **Cleanup**: `git worktree remove` the temporary worktree and delete its
  branch (point 4 above), performed during S17; confirm `v2-1-dry-run`'s
  `WORKTREE_IDENTITY.json` entry for the primary worktree is unchanged from
  before this scenario.

- **Outcome (blocked before mutation)**: worktree B was created
  (`git worktree add /tmp/.../wf8b-s14-worktree -b wf8b-s14-scratch HEAD`,
  at `8375b64f9ad9ad44afe7574841a62457f5d83cea`), isolated path/branch per
  the authorization above. Before invoking `/milestone-implement
  v2-1-dry-run` from it, tracing the command's own `[2.1 step 1]` procedure
  against worktree B's actual checked-out state surfaced a real defect in
  this scenario's own design: worktree B's `WORKFLOW_STATE.json` (checked
  out at `HEAD`, ordinary Git worktree semantics) has no `S-CP3` entry at
  all — that transition is deliberately uncommitted, worktree-A-local
  content — so `select_next_checkpoint` would classify checkpoint 3 as a
  **fresh start** from worktree B, never reaching `verify_dirty_resume_safety`
  (resume-only) at all, and a literal invocation would instead perform a
  real, forbidden mutation (fresh-start bookkeeping) inside worktree B's
  own tracked `WORKFLOW_STATE.json`. Neither `/milestone-implement`'s
  procedure nor any function call against it was executed from worktree B
  — stopped at this analysis, before any mutation, per this document's own
  "capture the exact evidence... create a remediation finding rather than
  hand-editing around it" discipline. Full reproduction, root cause and
  three proposed (undecided) resolution options in
  `docs/ai-workflow/dry-run/WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md`.
  Worktree B and its branch were removed again immediately (not deferred to
  S17, since nothing was ever run from it); checkpoint 3 in worktree A
  confirmed unchanged, byte-identical to the setup step's recorded
  before-state. **S15 is blocked on this finding's resolution** (same
  checkpoint-3 dependency the setup step's own text describes).

- **Outcome, follow-up analysis (2026-08-12, second session)**: the finding was
  classified, its root cause established mechanically, and a repair designed and
  stress-tested — still with zero mutation of `v2-1-dry-run` state and no
  `/milestone-implement` invocation from any worktree. **The defect is not only
  a defect in this scenario's specification: it is a genuine workflow
  architecture defect.** `D3`'s dirty-resume rule protects only the worktree
  that already knows it is resuming; the `IN_PROGRESS` transition is never
  committed (confirmed: no commit reachable from `HEAD` has ever carried a
  `v2-1-dry-run` checkpoint `IN_PROGRESS`), so no other worktree can discover
  the claim, and `verify_dirty_resume_safety` is never routed to. Running the
  real command from worktree B today would select `S-CP3`, start it as a fresh
  checkpoint, and commit it `COMPLETE` while worktree A still holds it
  `IN_PROGRESS` — verified against the real `workflow_state.py` functions. The
  proposed repair records the *claim* in the shared Git common directory so
  step 1b can route a foreign worktree into the existing, unchanged step 1c;
  under it, **`S14a` and `S14b` become executable exactly as written above**,
  with the same two error classes and no `WORKFLOW_STATE.json` copy. Five
  bounded stress passes, 77 checks green, two defects found in the draft design
  and fixed. Full analysis, routing and the required review path in
  `docs/ai-workflow/dry-run/WF8B_S14_FINDING_worktree_b_invisible_to_uncommitted_checkpoint.md`
  (follow-up section); executable evidence in
  `docs/ai-workflow/dry-run/wf8b-s14-repro/`. **S14 and S15 remain blocked**,
  now specifically on a `workflow-v2-1-core` plan revision (63) and its plan-review
  gate — `v2-1-dry-run`'s own Revision 5 plan needs no change.

- **Outcome, routing (2026-08-12, third session)**: `workflow-v2-1-core` plan
  **Revision 63** was authored, adding `D-Checkpoint-Ownership` and amending
  `D-Selection` rule 1 and `D3`'s dirty-resume paragraph, and this scenario's
  text above was corrected to match that architecture and no further. Four
  bounded author-side design/stress passes over the candidate design found and
  fixed seven further defects in it before any scenario text was written; 171
  checks are green across nine passes. **One correction to the second session's
  own conclusion**: it recorded that under the proposed fix "`S14a` and `S14b`
  become executable exactly as written above". They do not, quite — every
  stress fixture through pass 5 had arranged the interrupted checkpoint *with*
  a claim already taken, whereas the real checkpoint 3 was started before the
  mechanism existed and therefore has none. In that configuration worktree B
  still fresh-starts, which pass 6 reproduces directly. S14 therefore needs the
  one **additional setup action** now recorded in the "S14/S15 setup step"
  above (claim adoption in worktree A, implementing nothing and mutating no
  authoritative state); with it, S14a and S14b are executable as written, still
  end to end, still with no `WORKFLOW_STATE.json` copy into B. **S14 and S15
  remain blocked** on Revision 63 completing its plan-review and approval path
  and on `WF8b` then implementing the contract; nothing in this scenario has
  been executed, and checkpoint 3 is untouched.

- **Outcome (real, executed, 2026-08-15, sixth `D-Checkpoint-Ownership` session)**:
  ran for real against live repository state, with worktree A's checkpoint 3
  protected by the explicit takeover recorded in the setup step above.
  Worktree B created exactly per the authorization: `git worktree add
  /home/rodrigo/.claude/jobs/ef13a727/tmp/wf8b-s14-worktree -b
  wf8b-s14-scratch d5ad89d67d7c31485a2630a160bf92d044fe20b1` -- an isolated
  temporary path and branch, never reused from any other scenario.

  **S14a (missing identity)**: from worktree B, with no
  `.ai-review/runtime/` directory at all (confirmed before running), the
  `[2.1 step 1]` procedure was followed exactly as `.claude/commands/
  milestone-implement.md` states it -- `implementing_entry_reachable`
  (`True`), `select_next_checkpoint` (returned `"S-CP3"`), then
  `resolve_checkpoint_ownership(repo_root, work_item, "v2-1-dry-run",
  "S-CP3", now=...)`. It raised `WorktreeIdentityMissingError` exactly as
  required (never reaching a fresh-start branch), carrying
  `.ownership_evidence` naming the claim (`worktree_root` = worktree A's
  real path, `checkpoint_id: "S-CP3"`), `holder` = worktree A's path,
  `claimed_checkpoint: "S-CP3"`, and `escape` naming both "resume it
  there" and the explicit takeover. `git status --short` in worktree B
  immediately after: empty -- no `.ai-review/runtime/` directory was
  created, no tracked file touched.

  **S14b (mismatched identity)**: worktree A's real, current
  `.ai-review/runtime/WORKTREE_IDENTITY.json` was copied byte-for-byte
  into worktree B (sha256 `a52fad7a...5303d5a6caa`, the post-takeover
  value). Re-running the same `[2.1 step 1]` sequence from worktree B now
  raised `WorktreeIdentityMismatchError` instead -- confirming the two
  refusal classes really are distinguishable, not both collapsing to the
  same error -- with the identical `.ownership_evidence` (same claim,
  holder, claimed checkpoint, escape). `git rev-parse --show-toplevel
  --git-common-dir` from worktree B printed worktree B's own path plus the
  one shared `git_common_dir`, confirming the shared-claims resolution
  mechanism reads the same absolute path from both worktrees by
  construction. `git status --short` in worktree B: still empty (the
  copied identity file is gitignored, confirmed via `git check-ignore -v`
  matching `.gitignore:1`'s `.ai-review/` pattern) -- no tracked mutation
  either.

  **Verification, both invocations**: the shared claim record
  (`.git/ai-workflow/checkpoint-claims/ee28c6f2...329a182.json`) is
  byte-identical before and after both S14a and S14b, with no stray
  sibling temp file beside it; worktree A's own `docs/ai-workflow/
  WORKFLOW_STATE.json`, `docs/ai-workflow/dry-run/scratch/c.txt`, and
  `HEAD` are all byte-identical to their state immediately after the
  setup step's takeover -- neither invocation from worktree B touched
  worktree A in any way. Checkpoint 3 remains `IN_PROGRESS` in worktree A
  throughout, unchanged, exactly as S15 requires.

  **Cleanup deferred, per this scenario's own text**: worktree B and its
  `wf8b-s14-scratch` branch are left in place (nothing was run from B
  beyond the two authorized refused invocations plus the one identity-file
  copy between them), removal deferred to S17's overall cleanup as this
  scenario's own "Cleanup" line specifies -- not immediate, since real
  invocations *were* run from it this time (unlike the earlier
  blocked-before-mutation attempts, which removed B immediately because
  nothing had been run).

## S15 — Interrupted checkpoint recovery

- **Purpose**: prove the "interrupted-or-failed recovery" rule
  (`D-Self-Governance`'s WF8b pointer-persistence text, point 4, and D3's
  general `IN_PROGRESS`-with-no-completion-commit case): a session finding
  a checkpoint `IN_PROGRESS` with no completion commit reports last known
  state and requires an explicit user decision, never guessing.
- **Initial phase/state**: checkpoint 3 left `IN_PROGRESS` with uncommitted
  or partially-committed work and no completion commit — the state the
  "S14/S15 setup step" created and S14 provably did not disturb. No new setup
  is performed here; that is the point of ordering S15 after S14.
- **Command invoked**: `/milestone-implement v2-1-dry-run` (real) from a
  fresh session, in worktree A.
- **Expected state transition**: reports the interrupted checkpoint 3 and
  waits — does not auto-resume, does not auto-discard. Once the user chooses
  resume, checkpoint 3 completes normally, leaving all three scratch
  checkpoints `COMPLETE` for S7.

  **Under revision 63's `D-Checkpoint-Ownership`**, the same recovery must also
  demonstrate the ownership half, since S14 has just proved the refusal half:
  - worktree A resolves ownership as a **resume** — it holds the claim adopted
    at setup, and `verify_dirty_resume_safety` passes for it — so the refusal
    S14 produced is proved to be specific to the foreign worktree, not a
    general lock;
  - on the **resume** path, the claim is released only **after** the
    checkpoint commit exists, and the release must be observed in that order;
  - on the **discard** path, the explicit discard releases the claim as well
    as reverting the checkpoint to its `start_commit`, so the work item is left
    genuinely unclaimed rather than locked;
  - either way, confirm afterwards that no claim record remains for
    `v2-1-dry-run` under `.git/ai-workflow/checkpoint-claims/`.
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
→ S13 (resume and complete checkpoint 2, same worktree) → **S14/S15 setup**
(fresh session in worktree A: start checkpoint 3, leave it `IN_PROGRESS`) →
S14 (mismatched worktree, refused against that same checkpoint 3, then remove
the temp worktree) → S15 (interrupted recovery of that same still-`IN_PROGRESS`
checkpoint 3, completing it) → S7 → S8 → S9's approve-review implementation →
S10 (functional finding, bounded remediation, forces a repeat of S8/S9's
implementation-approval step) → S9's prepare-functional-review + accept →
S11 (separate throwaway legacy item, any point after S1) → S12 (drift
detection, any point after S5) → S16 → S17**.

Each `→` that crosses a "Fresh-session boundary: required" scenario above is
a real stopping point across separate `/bootstrap-workflow-v2` (or direct
command) invocations, not a single conversation.
