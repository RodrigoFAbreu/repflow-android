# Milestone Workflow (Semi-Autonomous)

Operational state machine for planning, implementing, and closing out a
milestone with an external reviewer and the user in the loop. This document
is the authoritative owner of the workflow states and gates. It does not
duplicate architecture, migration, or testing rules — see `CLAUDE.md` for
routing to those.

Commands in `.claude/commands/` implement these states. Bundle mechanics are
owned by `docs/ai-workflow/REVIEW_PROTOCOL.md` and
`scripts/prepare-ai-review.sh`.

## State reference

For every state: entry condition, allowed actions, required artifacts, exit
condition, and whether Claude stops.

### PLANNING

- **Entry**: `docs/ACTIVE_MILESTONE.md` names an incomplete milestone/checkpoint,
  or the user asks to start the next one.
- **Allowed actions**: read `docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md`, and
  only the milestone/reference/ADR docs relevant to the target checkpoint;
  draft or update the execution/reference plan.
- **Artifacts**: draft execution plan (and reference plan, if new decisions
  are introduced).
- **Exit**: a concrete, checkpointed plan exists.
- **Stop for user/reviewer?** No.

### SELF_REVIEWING_PLAN

- **Entry**: a draft plan exists.
- **Allowed actions**: critically review the plan for missing requirements,
  migration risk, usability gaps, unnecessary complexity, missing tests;
  revise the plan in place.
- **Artifacts**: revised plan with self-review notes folded in (not a
  separate journal file).
- **Exit**: plan reflects the self-review; no known gaps left unaddressed or
  unflagged.
- **Stop for user/reviewer?** No.

### AWAITING_EXTERNAL_PLAN_REVIEW

- **Entry**: self-review is complete.
- **Allowed actions**: run `scripts/prepare-ai-review.sh <base-sha> plan` to
  export the bundle. No implementation.
- **Artifacts**: `.ai-review/current/` (plan stage) and
  `.ai-review/review-bundle.tar.gz`.
- **Exit**: external reviewer places feedback at
  `.ai-review/feedback/REVIEW_FEEDBACK.md`.
- **Stop for user/reviewer?** Yes — hard gate. Claude must stop here.

### REVISING_PLAN

- **Entry**: `.ai-review/feedback/REVIEW_FEEDBACK.md` exists.
- **Allowed actions**: validate every finding against the repository; apply
  accepted findings to the plan; document evidence-based rejections.
- **Artifacts**: revised plan; rejection rationale (inline in the plan or
  review bundle, not a new standalone doc).
- **Exit**: all blocking/important findings resolved or rejected with
  evidence, **and** the most recently reviewed round's status was `REVISE`
  or `APPROVE` (never `BLOCK`) — proceeds to `AWAITING_PLAN_APPROVAL`. A
  `REVISE` round with zero blocking findings left reaches that gate exactly
  as readily as an `APPROVE` round; this condition reads only the
  review-round artifact, never any existing approval record (non-circular
  by construction — see `AWAITING_PLAN_APPROVAL` below).
- **Stop for user/reviewer?** Only if a rejection or major plan change needs
  reviewer sign-off before implementation; otherwise proceed.

### AWAITING_PLAN_APPROVAL

- **Entry**: `REVISING_PLAN`'s exit condition is met. For a
  `governing_workflow_version: "2.1"` work item, one further condition
  applies: the work item's `plan_review_stages` ledger must additionally
  record both `local_model_plan_review` and `manual_external_plan_review`
  completed, in that order, against the *current* plan-stage
  `review_content_id` (the two-stage local-then-manual-external plan-review
  protocol, `/review-plan`/`/record-manual-plan-review`) — a single
  reviewed round is necessary but no longer sufficient for a `"2.1"` item. A
  `"1"` item's entry condition is exactly `REVISING_PLAN`'s exit condition,
  unchanged.
- **Allowed actions**: run `/approve-review plan`, which chooses the
  approval basis (`EXTERNAL_APPROVE` when the current
  `REVIEW_FEEDBACK.md`'s status is exactly `APPROVE` and its bundle ID
  matches the just-recomputed one exactly; otherwise `USER_OVERRIDE`, which
  requires literal override text naming the exact work item and stage in
  the same turn). `BLOCK` never reaches either basis. No plan edits, no
  implementation.
- **Artifacts**: none new until `/approve-review plan` runs.
- **Exit**: `/approve-review plan` writes `plan_approval` and creates the
  one plan-approval commit.
- **Stop for user/reviewer?** Yes — hard gate. Only the user can invoke
  `/approve-review` (mechanism-independent guard: `disable-model-invocation:
  true`, plus a specificity check on the literal `user_confirmation` text).

### IMPLEMENTING

- **Entry**: current HEAD is the plan-approval commit or a checkpoint-commit
  descendant of it, `plan_approval.status == CURRENT`, and a freshly
  recomputed plan-stage `review_content_id` matches
  `plan_approval.approved_review_content_id`.
- **Allowed actions**: implement checkpoint by checkpoint; run the narrowest
  relevant checks per checkpoint; review each coherent diff; create
  authorized intermediate commits; update `docs/ACTIVE_MILESTONE.md` as
  checkpoints complete.
- **Artifacts**: source/test changes; updated `docs/ACTIVE_MILESTONE.md`; commits.
- **Exit**: all plan checkpoints implemented.
- **Stop for user/reviewer?** No — continue across checkpoints without
  stopping, subject to the stop conditions in `AGENTS.md`.

### SELF_REVIEWING_IMPLEMENTATION

- **Entry**: all checkpoints implemented.
- **Allowed actions**: full internal review of the diff; fix blocking and
  important findings; run required verification (narrow, then full suite
  per `CLAUDE.md` commands).
- **Artifacts**: fixed diff; verification results (actually run, not
  assumed).
- **Exit**: no known blocking/important self-review findings remain open.
- **Stop for user/reviewer?** No.

### AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW

- **Entry**: self-review and verification are complete.
- **Allowed actions**: run
  `scripts/prepare-ai-review.sh <base-sha> implementation` to export the
  bundle. No further implementation.
- **Artifacts**: `.ai-review/current/` (implementation stage) with the real
  diff, changed files, tests run, decisions; `.ai-review/review-bundle.tar.gz`.
- **Exit**: external reviewer places feedback at
  `.ai-review/feedback/REVIEW_FEEDBACK.md`.
- **Stop for user/reviewer?** Yes — hard gate. Claude must stop here. Do not
  mark the milestone accepted.

### APPLYING_REVIEW_FEEDBACK

- **Entry**: implementation review feedback exists.
- **Allowed actions**: reproduce and validate each finding; fix all
  blocking/important findings; explain evidence-based rejections; rerun
  relevant tests; commit coherent fixes; regenerate the bundle
  (`post-fix` stage) if another review round is needed.
- **Artifacts**: fixed diff; updated `.ai-review/current/` (post-fix stage);
  commits.
- **Exit**: all blocking/important findings resolved or rejected with
  evidence, **and** the most recently reviewed round's status was `REVISE`
  or `APPROVE` (never `BLOCK`) — proceeds to `AWAITING_TECHNICAL_APPROVAL`.
- **Stop for user/reviewer?** Only if unresolved blocking findings or major
  rework remain; otherwise proceed.

### AWAITING_TECHNICAL_APPROVAL

- **Entry**: `APPLYING_REVIEW_FEEDBACK`'s exit condition is met, no
  protected path is dirty (`WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json`
  dirtiness never blocks this), and current committed content matches
  `reviewed_implementation_head` exactly (the bundle generator's own,
  sole-writer field — set at `implementation`/`post-fix` stage).
- **Allowed actions**: run `/approve-review implementation`, which chooses
  the approval basis exactly as `AWAITING_PLAN_APPROVAL` does above. No
  further implementation changes.
- **Artifacts**: none new until the command runs.
- **Exit**: `/approve-review implementation` writes `technical_approval`
  and creates a metadata-only technical-approval commit (zero production/
  test changes).
- **Stop for user/reviewer?** Yes — hard gate, same enforcement as
  `AWAITING_PLAN_APPROVAL`.

### AWAITING_FUNCTIONAL_REVIEW

- **Entry**: `technical_approval.status == CURRENT`.
- **Allowed actions**: confirm automated verification state; write a concise
  manual functional-review checklist (setup, test data, exact flows,
  expected results, known limitations); update `docs/ACTIVE_MILESTONE.md`.
- **Artifacts**: functional-review checklist (in `docs/ACTIVE_MILESTONE.md` or a
  file it links to).
- **Exit**: user performs functional testing and places findings at
  `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.
- **Stop for user/reviewer?** Yes — hard gate. Claude must stop here.

### FIXING_FUNCTIONAL_FINDINGS

- **Entry**: `.ai-review/feedback/FUNCTIONAL_REVIEW.md` exists.
- **Allowed actions**: classify each finding (defect, usability issue,
  missing requirement, enhancement, expected behavior); reproduce where
  practical; fix defects/usability issues/missing requirements; add
  regression tests; commit coherent fixes; prepare a revised checklist.
- **Artifacts**: fixed diff with regression tests; revised functional
  checklist; commits.
- **Exit**: all findings addressed or explicitly deferred with rationale.
- **Stop for user/reviewer?** Returns to `AWAITING_FUNCTIONAL_REVIEW` for
  another pass unless the user explicitly waives it.

### AWAITING_USER_ACCEPTANCE

- **Entry**: functional review is clean (or remaining items are explicitly
  deferred/waived by the user).
- **Allowed actions**: none besides answering questions — no further code
  changes.
- **Artifacts**: none new.
- **Exit**: user explicitly accepts the milestone.
- **Stop for user/reviewer?** Yes — hard gate. Claude must stop here.

### MILESTONE_COMPLETE

- **Entry**: explicit user acceptance received.
- **Allowed actions**: final verification confirmation; update
  `docs/ROADMAP.md` and `docs/ACTIVE_MILESTONE.md`; archive the milestone's
  plans to `docs/milestones/completed/`; create the final completion commit
  if one is still needed; prepare `docs/ACTIVE_MILESTONE.md` for the next
  milestone's `PLANNING` state.
- **Artifacts**: updated roadmap/status docs; archived plans; completion
  commit.
- **Exit**: next milestone is ready for `PLANNING`.
- **Stop for user/reviewer?** No further action — do not begin implementing
  the next milestone. Wait for the user to invoke `/milestone-plan`.

## Hard gates summary

Claude must stop and wait for a human/external input at exactly six points:

1. `AWAITING_EXTERNAL_PLAN_REVIEW`
2. `AWAITING_PLAN_APPROVAL`
3. `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`
4. `AWAITING_TECHNICAL_APPROVAL`
5. `AWAITING_FUNCTIONAL_REVIEW`
6. `AWAITING_USER_ACCEPTANCE`

`AWAITING_PLAN_APPROVAL` and `AWAITING_TECHNICAL_APPROVAL` are each enforced
by `/approve-review`'s mechanism-independent user-only guard
(`disable-model-invocation: true`, plus a literal, specificity-checked
`user_confirmation`) — only the user can exit either gate, never Claude
autonomously. `/accept-milestone` carries the same guard for
`AWAITING_USER_ACCEPTANCE`.

For a `governing_workflow_version: "2.1"` work item, the edge from
`REVISING_PLAN` to `AWAITING_PLAN_APPROVAL` is further refined into
`REVISING_PLAN → AWAITING_LOCAL_PLAN_REVIEW → AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW
→ AWAITING_PLAN_APPROVAL` (two-stage local-then-manual-external plan
review) — a refinement of the existing edge, not two additional hard gates
layered on top of it. This repository's own workflow-v2-1-core work item is
fixed at `governing_workflow_version: "1"` for its entire execution, so the
refined edge does not apply to it; see `docs/ai-workflow/WORKFLOW_V2_PLAN.md`
(`D-Plan-Review-Stages`) for the full `"2.1"`-only mechanism, owned by a
later checkpoint.

Between gates, Claude may work autonomously, subject to the stop conditions
already defined in `AGENTS.md` (ambiguous product behavior, architecture
changes, new dependency categories, schema migrations, destructive data
operations, repeated verification failures, unrelated working-tree changes).

## Feedback file locations

- Plan/implementation review: `.ai-review/feedback/REVIEW_FEEDBACK.md`
- Functional review: `.ai-review/feedback/FUNCTIONAL_REVIEW.md`

Both are read, never written, by Claude — see
`docs/ai-workflow/REVIEW_PROTOCOL.md` for the required feedback structure.
