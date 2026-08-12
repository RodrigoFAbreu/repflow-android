# v2-1-dry-run — Scratch plan (Revision 5)

Status: `AWAITING_LOCAL_PLAN_REVIEW` — the phase `/apply-plan-review`'s
`"2.1"` exit step moves this item to once revision 5's edits land. Revision 1
was externally reviewed and returned `REVISE`; revisions 2, 3 and 4 were each
reviewed by the `local_model_plan_review` stage and each returned `REVISE` —
revision 3 with one blocking finding against the plan-approval commit's own
membership, revision 4 with no blocking finding and three important findings
against how this document *describes* the gate mechanisms it invokes (see all
four "Review feedback disposition" sections below). Not approved; revision 5
must still pass a fresh `local_model_plan_review` round (`/review-plan`) and
then a manual external review before any approval is recorded.

## Purpose

This is **not a product milestone**. `v2-1-dry-run` is the synthetic,
throwaway `governing_workflow_version: "2.1"` work item WF8b (see
`docs/ai-workflow/WORKFLOW_V2_PLAN.md`'s checkpoint registry) uses to prove
the real command surface — `/milestone-plan`, `/review-plan`,
`/record-manual-plan-review`, `/approve-review`, `/milestone-implement`,
`/apply-implementation-review`, `/prepare-functional-review`,
`/apply-functional-review`, `/accept-milestone` — end to end, against real
repository state, with real git commits and real approval gates, rather than
only the hermetic function-level coverage already in
`scripts/workflow_state_test.py`/`workflow_fingerprint_test.py`/
`workflow_integration_test.py`.

The full scenario checklist this plan feeds is
`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`. This plan document exists
because S1 (`/milestone-plan v2-1-dry-run`) needs a real plan doc + registry
+ mapping to review, exactly as any real work item would.

## Scope

Three deliberately trivial checkpoints, each creating one throwaway file
under `docs/ai-workflow/dry-run/scratch/`. Two of them are deliberately
left `IN_PROGRESS`/uncommitted at the end of a session, each owning a
distinct set of scenarios, because a checkpoint that has been completed
cannot also be the `IN_PROGRESS` subject of a later one:

- **checkpoint 2** is left dirty at the end of S6's second session and is
  the subject of **S13** (same-worktree resume) only. S13 resumes and
  **completes** it; it stays `COMPLETE` from that point on and is never
  reopened.
- **checkpoint 3** is started in worktree A after S13 and left
  `IN_PROGRESS` with no completion commit. It is the subject of **S14**
  (wrong-worktree refusal — both its missing-identity and
  mismatched-identity sub-cases) and then, still unchanged, of **S15**
  (interrupted-checkpoint recovery in a fresh session). The same
  checkpoint legitimately serves both because S14 is required to make
  zero state/worktree mutation — verified, not assumed — so it leaves
  checkpoint 3 exactly as it found it. S15's recovery decision completes
  it, so all three checkpoints are `COMPLETE` before S7.

No fourth checkpoint is required. The sequence above is
`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`'s own executable order; that
checklist, this plan, the registry and the requirements mapping all state
it identically.

No product code, no `docs/ROADMAP.md`/`docs/ACTIVE_MILESTONE.md` changes —
this item is fully contained under `docs/ai-workflow/dry-run/`,
`docs/ai-workflow/registry/v2-1-dry-run-*`, and
`docs/ai-workflow/requirements/v2-1-dry-run-mapping.json`.

## Checkpoint registry

Generated view of `docs/ai-workflow/registry/v2-1-dry-run-registry.json` —
never hand-edited, never itself hashed.

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| S-CP1 | Scratch checkpoint 1: create dry-run scratch file A | - | 1 | 1 |
| S-CP2 | Scratch checkpoint 2: create dry-run scratch file B (left dirty for S13) | S-CP1 | 1 | 1 |
| S-CP3 | Scratch checkpoint 3: create dry-run scratch file C (left IN_PROGRESS in worktree A for S14, then S15) | S-CP2 | 1 | 1 |

Each checkpoint's own acceptance is trivial: create its named file under
`docs/ai-workflow/dry-run/scratch/` with a one-line marker, commit with the
required `Workflow-Checkpoint: <id>` + `Workflow-Work-Item: v2-1-dry-run`
trailers. No tests beyond confirming the file exists with the expected
content — there is no real logic here to test; the point is proving the
command/state-machine wiring, not the payload.

## Requirements traceability

Generated view of
`docs/ai-workflow/requirements/v2-1-dry-run-mapping.json` — see that file
for the authoritative `{description, checkpoint_ids}` content; not restated
here to avoid a second, driftable copy.

| Requirement | Checkpoint(s) |
| --- | --- |
| WFR-DRY-1 | S-CP1 |
| WFR-DRY-2 | S-CP2 |
| WFR-DRY-3 | S-CP3 |

## Self-review notes (`SELF_REVIEWING_PLAN`)

- **Missing requirements**: none — this item's only purpose is to exist and
  be walked through the real command surface; it has no functional
  requirements of its own beyond "each scratch checkpoint's file lands."
- **Migration risk**: none — no schema, no Room, no product code touched.
- **Usability gaps**: none applicable — no end user.
- **Unnecessary complexity**: deliberately minimized to 3 single-file
  checkpoints, per `WF8B_SCENARIOS.md`'s S1 recommendation.
- **Missing tests**: none beyond the trivial file-existence check noted
  above — this plan's entire purpose is a manual/real-command dry run, not
  automated test coverage (that's what `scripts/workflow_*_test.py` already
  provide, at the function level). Revision 2 adds one executable artifact,
  `docs/ai-workflow/dry-run/verify_review_content_id.py`, but it is a
  reviewer-facing verification tool rather than a checkpoint deliverable; it
  is validated by use (it reproduces this bundle's own `review_content_id`,
  and reproduced revision 1's `861c9ab1…` from the revision-1 archive before
  any revision-2 edit landed), not by a separate unit test. Its three
  recomputed Git blob ids were also cross-checked against `git hash-object`
  directly.
- **Classification audit, both stages**
  (`docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`). Revision 2's
  version of this bullet audited only the plan stage and concluded "No gap
  found"; the implementation-stage half of the confirmation
  `generate_artifacts_declarations`'s own docstring asks for was never
  performed, and revision 2's `local_model_plan_review` correctly blocked on
  it (`LOCAL-DRY-R2-001`). Revision 3 audits both stages, and both are now
  stated positively rather than by inheritance:

  - **Plan stage.** `plan_stage.protected_paths` is exactly this plan doc +
    the registry + mapping JSON (this item's own three declared paths).
    The exclusion sets began as `workflow-v2-1-core`'s, inherited verbatim
    by `generate_artifacts_declarations`, and revision 3 adds three exact
    paths the generator structurally could not supply. The set now covers:
    this item's own scratch deliverables and dry-run evidence
    (`docs/ai-workflow/dry-run/` prefix, which also covers
    `WF8B_SCENARIOS.md` and `verify_review_content_id.py`); runtime state
    and config (`WORKFLOW_STATE.json`, `WORKFLOW_CONFIG.json`);
    `workflow-v2-1-core`'s deliverable surface (`scripts/`,
    `.claude/commands/`, `.github/`, `MILESTONE_WORKFLOW.md`,
    `REVIEW_PROTOCOL.md`, `PLAN_REVIEW_WORKFLOW.md`); registry/requirements/
    archive prefixes; and repository-wide docs and product trees (`CLAUDE.md`,
    `AGENTS.md`, `README.md`, `docs/PROJECT_BRIEF.md`,
    `docs/DOMAIN_GLOSSARY.md`, `docs/UX_FLOWS.md`, `docs/ACTIVE_MILESTONE.md`,
    `docs/ROADMAP.md`, `app/`, `gradle/`, `config/`, `docs/adr/`,
    `docs/agent-context/`, `docs/improvements/`, `docs/milestones/`).
  - **Implementation stage.** Was `{self-referential protected entry, three
    empty sets}` — the generator's own output, which classifies nothing else
    and therefore fails closed on every path. It now declares
    `docs/ai-workflow/dry-run/scratch/` as a **protected prefix** (this
    item's only real implementation deliverable: the three checkpoint marker
    files), keeps the self-referential declarations-file protection, and
    carries the same exclusion coverage enumerated for the plan stage above.
    The protected prefix is checked before the broader
    `docs/ai-workflow/dry-run/` exclusion — `classify_path_implementation_stage`
    tests protected paths, then protected prefixes, then excluded paths, then
    excluded prefixes — the same "the immutable artifact is protected by its
    own entry, checked first" pattern the plan stage already uses.

  Fail-closed is preserved, not traded away: no set is a catch-all, and
  repository-root build files (`build.gradle.kts`, `settings.gradle.kts`,
  `gradle.properties`, `gradlew`) and any new top-level directory still raise
  `UnclassifiedPathError` at both stages. Verified by direct probe, not
  asserted.

  Evidence for the whole bullet is in "Classification evidence (revision 3)"
  below.

## S5 plan-approval commit membership

Normative for this work item, decided here rather than improvised at S5
(`LOCAL-DRY-R3-001`). The plan-approval commit `/approve-review plan
v2-1-dry-run` creates contains **exactly these five paths**:

1. `docs/ai-workflow/dry-run/v2-1-dry-run-plan.md` — the approved plan doc
2. `docs/ai-workflow/registry/v2-1-dry-run-registry.json` — the registry
3. `docs/ai-workflow/requirements/v2-1-dry-run-mapping.json` — the mapping
4. `docs/ai-workflow/WORKFLOW_STATE.json` — the approval record itself
5. `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` — **this item's
   own artifact declaration**

Members 1-4 are the installed `/approve-review` step 6's plan-stage set
verbatim, minus the "generated Markdown view" it also names: this item has
no separate generated view file, its registry table is rendered inline in
this document's own "Checkpoint registry" section.

### Member 5 is conditional, not unconditional (`LOCAL-DRY-R4-003`)

The authorization for member 5 is `D-Approval-Commits`' "Conditional fifth
commit member" bullet, and that contract carries two conditions **checked in
order** plus a refusal rule. This item imports the contract whole, conditions
included — "exactly these five paths" states the set S5 is *expected* to
produce, not a licence to stage the declaration's then-current bytes
unchecked:

1. **Pending-change condition** — the declaration's working-tree bytes differ
   from its content at HEAD. An unchanged declaration is never added; the
   commit is then the four-member set.
2. **Freshness condition** — the declaration's working-tree bytes are
   byte-identical to the copy the just-recomputed current `bundle_id` already
   captured, i.e. `<bundle_dir>/files/docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`.
   If they differ, S5 **refuses outright, naming the stale path**, rather
   than committing bytes no reviewer ever saw. This condition is why the
   check is needed at all: the declaration is the one member of the commit
   set that is plan-stage *excluded*, so the plan-stage `review_content_id`
   match does not already cover it.

Both conditions hold as of revision 5 and were verified, not assumed: `git
cat-file -e HEAD:docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`
reports the path absent at HEAD (condition 1; `git ls-files -s` shows the
intent-to-add empty blob `e69de29b…`), and the worktree file and the bundle's
captured copy are byte-identical at sha256
`02fef08e858008441e2dc8c326c26331c6507a15d37e7cd58049ad3a484c740e`
(condition 2). They must nevertheless be **re-checked at S5 execution time**,
not read from here: S5 runs several scenarios later, and S12's own procedure
deliberately edits a protected path outside the normal command flow, so a
hand-edit to this file between now and then is a live possibility rather
than a hypothetical. `WF8B_SCENARIOS.md`'s S5 carries the byte-comparison as
an explicit pre-staging step, and its result is recorded as S5 evidence.

### Why member 5 is a deliberate deviation, not an error

The installed command's step 6 names four members and does not include the
artifact declaration. Following that text literally is not merely
suboptimal for this item — it aborts the dry run at S5, **after** the
approval state has been written and the commit created:

- `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` has never been
  committed. It is intent-to-add only (` A` in `git status --short`, via
  S1's `git add -N`), exactly like the plan doc, registry and mapping. No
  step before S5 commits it — S1 writes no commit, and the checkpoint
  commits that could carry it are at S6, after S5.
- A plain `git commit` omits an intent-to-add path whose content was never
  staged, so a four-member staging produces a commit without it.
- `resolve_plan_stage_metadata` (`scripts/workflow_fingerprint.py:858-861`)
  resolves the declaration **at the commit**, not from the worktree, and
  raises `MissingWorkItemArtifactsDeclarationError` when it is absent. Step
  6a's `verify_post_approval_manifest_match` therefore raises, and so does
  `approval_is_current` — which means `implementing_entry_reachable`,
  `/milestone-implement`'s own entry condition, **raises rather than
  returning `False`**, so S6 cannot start either.

The failure lands after `apply_plan_approval` has persisted `status:
CURRENT` and `phase: IMPLEMENTING` and after the commit exists, leaving a
durable, self-contradicting state that the installed command defines no
recovery for. That is the same failure-atomicity class
`WORKFLOW_V2_PLAN.md` revisions 51-56 exist to eliminate; this plan
declines to walk into it knowingly.

Adding member 5 is provably digest-neutral, so the deviation costs nothing
that was reviewed: the declaration is plan-stage **excluded** (it falls
under this item's own `docs/ai-workflow/registry/` excluded prefix), so it
never enters the plan-stage projection's manifest. What the evidence below
demonstrates is exactly that: the **five**-member commit's commit-source
`review_content_id`, recomputed at that commit, equals the pre-commit
worktree-source value written to `plan_approval` — so adding member 5 moves
no approved value. The four-member commit is not a second data point for
this claim and never could be: its plan-stage recompute does not produce a
digest at all, it **raises**
`MissingWorkItemArtifactsDeclarationError`, which is the whole reason member
5 exists (evidence item 1 below). The only four-member recompute reported
below is the *implementation*-stage one, an unrelated fact recorded for the
second-order consequence in the next subsection
(corrected in revision 5, `LOCAL-DRY-R4-002`).

The deviation is authorized by `D-Approval-Commits`' own conditional fifth
member (`GPT-R67-001`, `WORKFLOW_V2_PLAN.md` revision 50), which requires
exactly this file in exactly this case. That contract postdates the
installed command file, which is why `WORKFLOW_V2_PLAN.md` carries a
separate "Bootstrap plan-approval procedure" for its own approvals. This
section is the same correction, scoped to this item, decided in advance and
written down — which is what `GPT-DRY-R1-001` demanded instead of ad hoc
mid-run widening.

### The alternative that is not available

Committing the declaration **before** S5 does not work, and a later session
should not rediscover this the expensive way. There are two distinct costs,
one per branch, and neither is a closed gate — stated precisely here because
revision 4 got the second one backwards and a dry run whose purpose is
proving the approval gates behave as documented cannot ship a
document that describes them wrongly (`LOCAL-DRY-R4-001`).

**Branch 1 — pre-S5 commit, bundle left un-regenerated: a hard refusal.**
Any pre-S5 commit moves live HEAD past `MANIFEST.md`'s recorded
`generation_head` (`e75a756d42751ef18eee842a958cc2c888086772`, currently
equal to live HEAD), so `/approve-review` step 2's
`assert_local_generation_matches` refuses before doing anything. This branch
is genuinely fail-closed.

**Branch 2 — pre-S5 commit, then regenerate to restore that equality: a
silent basis downgrade, not a closed gate.** Regeneration produces a new
`bundle_id`, and the cost of that is `EXTERNAL_APPROVE`:
`resolve_approval_basis` compares the feedback's `Reviewed bundle ID`
against the freshly recomputed `bundle_id` and, on a mismatch, returns
`USER_OVERRIDE` instead (`workflow_state.py:2083-2111`; only a `BLOCK` status
raises). The approval still lands — at a weaker basis, which is precisely the
outcome this dry run should not record by accident.

The approval gate itself stays **open** across that regeneration.
`plan_approval_gate_reachable` never sees a `bundle_id`: its signature is
`(latest_round_status, governing_workflow_version, plan_review_stages,
current_review_content_id)` (`workflow_state.py:2025-2049`), and its `"2.1"`
branch keys the ledger binding on `review_content_id` alone. Nothing anywhere
reads a `plan_review_stages` entry's recorded `bundle_id` for a gating
decision — the three readers are `plan_approval_gate_reachable`,
`validate_manual_plan_review_preconditions` and `_validate_plan_review_stages`,
and none of them touches the field; the manual stage's own `bundle_id` check,
`check_manual_stage_bundle_id_advisory`, is advisory by explicit design
(`GPT-R11-006`: a wrapper-only regeneration must *not* invalidate the manual
stage). Nor would a pre-S5 commit move `review_content_id` in the first
place: the plan-stage projection is a function of `base_commit`, the
protected paths' worktree bytes and the resolved metadata — not of HEAD —
so the S2/S4 ledger rounds stay bound either way.

If this document wants a gate-closure claim anywhere, it belongs to
`review_content_id`, the only input `plan_approval_gate_reachable` keys on.
All three of the above are reproduced under "Gate-behavior evidence
(revision 5)" below.

The declaration must therefore still enter the durable history *in* the
approval commit or not at all before S6 — for the reasons above, not the
ones revision 4 gave.

### Second-order consequence, recorded either way

`v2-1-dry-run-artifacts.json` is this item's own implementation-stage
`protected_paths` entry — its stated purpose being that no one may widen an
exclusion and re-bless the digest in the same session. That protection is
**inert while the file stays uncommitted**: it is never a changed path in
`base..commit`, so it never enters the implementation-stage manifest.
Verified at the four-member commit: the implementation-stage recompute
succeeds and returns an empty manifest. Committing it at S5 makes the
protection real — at the five-member commit the same recompute lists it as
a manifest member and the digest differs accordingly. This is an argument
for member 5 beyond mere executability.

## Open decisions (`docs/TECHNICAL_DECISIONS.md`)

None of this repository's "Open decisions" rows are touched by this
scratch item — it introduces no new toolchain/dependency/architecture
choice.

## Known limitations

- This is throwaway content, deleted in full at WF8b cleanup (S17). It must
  never be treated as a real product milestone.
- **Pre-existing, deliberately not fixed here**: `workflow-v2-1-core`'s
  `implementation_stage` classification has no entry for the
  `docs/ai-workflow/dry-run/` prefix (it excludes
  `docs/ai-workflow/registry/`, `requirements/` and `archive/`, but not
  `dry-run/`), so `scripts/workflow_fingerprint_demo_test.py`'s
  exhaustive-classification check already fails against that item's own base
  commit `162154d`. Revision 2 stated the count as three existing files, four
  once `verify_review_content_id.py` is added; **`LOCAL-DRY-R2-002` is
  correct that the real number is six**, and revision 3 restates it from a
  fresh run rather than from the earlier estimate. The six are five under the
  prefix — `WF8B_SCENARIOS.md`,
  `WF8B_S1_FINDING_review_content_id_not_generalized.md`,
  `WF8B_FINDING_continued_scope_remediation_no_nonterminal_return_path.md`,
  this item's own `v2-1-dry-run-plan.md`, and
  `verify_review_content_id.py` — plus one outside it,
  `docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md`, which revision 2 did not
  mention at all and which no `docs/improvements/` entry exists for in that
  item's implementation-stage sets. Revision 2's count also missed that this
  item's own plan document lands under the unclassified prefix. Fixing any of
  this means editing
  `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`, which is
  pinned by `workflow-v2-1-core`'s **current** `technical_approval` manifest
  (blob `acf3c418`); doing so from this synthetic item would stale another
  work item's durable approval. Out of scope — recorded, not silently
  absorbed. **This is not the same defect as `LOCAL-DRY-R2-001`**: that one
  is *this* item's own empty implementation-stage sets, which revision 3
  fixes because this item's declarations file is bound by no
  `technical_approval` yet. The same run confirmed two further pre-existing
  failures unrelated to this item (`workflow_integration_test.py`'s hardcoded
  60-vs-63 WFR row count, and `workflow_state_demo_test.py`'s
  `reviewed_implementation_head` assertion, which is blocking decision
  `WF8B-003` itself).
- **Routed out, not fixed here**: `generate_artifacts_declarations`
  (`scripts/workflow_state.py:1552-1564`) emits an implementation-stage
  classification that cannot classify **any** of a new work item's own
  artifacts, and copies only `workflow-v2-1-core`'s plan-stage *exclusion*
  sets — so a new item is also unable to classify the three documents that
  item holds plan-stage *protected* (`WORKFLOW_V2_PLAN.md`,
  `WORKFLOW_V2_AUDIT.md`, `docs/TECHNICAL_DECISIONS.md`). Every future
  `"2.1"` work item created through `/milestone-plan` inherits both gaps.
  This item works around them in its own declaration (see the classification
  audit above); the generator fix belongs to `workflow-v2-1-core` and must
  not be made from here, since `scripts/workflow_state.py` is pinned by that
  item's current `technical_approval` at blob `7643da59`. Route it as a
  generator/plan-template finding once WF8b completes.

  **Third instance, found by revision 3's review** (`LOCAL-DRY-R3-001`): the
  generator writes `<work_item_id>-artifacts.json` as a working-tree file,
  and **nothing in the plan-stage lifecycle commits it** — while
  `resolve_plan_stage_metadata` requires it to exist *at the approval
  commit*. So every future `"2.1"` item created through `/milestone-plan`
  whose first durable commit is its own plan approval aborts at
  `/approve-review` step 6a, after the state write and the commit.
  `workflow-v2-1-core` is immune only because its declaration was committed
  earlier as a WF4a-i deliverable. This item works around it in its own
  "S5 plan-approval commit membership" section; the general fix belongs to
  `workflow-v2-1-core` (it is `.claude/commands/approve-review.md`'s step 6
  text, or the generator, or both) and must not be made from here. Route it
  with the two gaps above.
- **Routed out, not fixed here** (`LOCAL-DRY-R4-001`'s general half, revision
  5): `record_local_plan_review`/`record_manual_plan_review` write a
  per-stage `bundle_id` into the `plan_review_stages` ledger
  (`scripts/workflow_state.py:2775-2781`, `:2896-2899`) that **no gate ever
  reads** — grepped exhaustively: the ledger's three readers
  (`plan_approval_gate_reachable`, `validate_manual_plan_review_preconditions`,
  `_validate_plan_review_stages`) all key on `review_content_id` and none
  touches the field. That is what made revision 4 reasonably assume it was
  load-bearing and describe the approval gate as closing on a `bundle_id`
  change. Either the field is documentation-only and should say so where it
  is written, or the intended binding is missing. `GPT-R11-006` establishes
  the advisory reading as deliberate for the *manual* stage
  (`check_manual_stage_bundle_id_advisory`); whether the same is intended for
  the local stage is a question for `workflow-v2-1-core`'s own design, not
  this synthetic item's. Fixing it here would edit
  `scripts/workflow_state.py`, pinned by that item's current
  `technical_approval` at blob `7643da59`. Route it with the gaps above.
- **`docs/ai-workflow/dry-run/verify_review_content_id.py` stays
  untracked** through the dry run, and is deleted with the rest of this
  item's content at S17. It is reviewer-facing evidence, not a deliverable:
  its exact bytes are already pinned in the bundle
  (`current/files/docs/ai-workflow/dry-run/verify_review_content_id.py`,
  sha256 `65ed2419…`) and in the archive, it classifies `excluded` at both
  stages, so tracking it would move no digest, and every gate that
  enumerates a commit's paths — the plan-approval commit's five-member set
  (see "S5 plan-approval commit membership"), each checkpoint commit's own
  set — has an exact declared membership that does not include it. Adding a
  member to one of those sets for no identity benefit is the larger change.
  It does classify, so it never blocks a bundle: confirmed as one of the ten
  paths in the classification evidence below.

  **The one file that set does exist for is a different case, and revision 3
  left it unstated** (`LOCAL-DRY-R3-001`).
  `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` is equally
  untracked today, but unlike the verifier it is **required to exist at the
  approval commit** by `resolve_plan_stage_metadata`, so leaving it out is
  not a neutral choice — it aborts S5 after the state write. It is therefore
  member 5 of the approval commit, not a "left untracked" file. The two
  dispositions are opposite because the gates treat them oppositely: nothing
  resolves the verifier at a commit; `resolve_plan_stage_metadata` resolves
  the declaration at one on every plan-stage recompute.
- The checkpoint "implementation" is intentionally trivial (one marker file
  per checkpoint) — WF8b's own purpose is to prove the *workflow* commands,
  not to exercise interesting product logic.

## Unresolved questions for the reviewer

- None specific to this plan's own content. The interesting open question
  this dry run exists to answer — whether `AWAITING_LOCAL_PLAN_REVIEW` is
  reachable directly from a first-ever plan bundle, or only via a prior
  `REVISING_PLAN` round — was resolved by reading
  `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s own state-edge summary before
  running this scenario (see "Hard gates summary": the `"2.1"` two-stage
  local/manual-external protocol is stated as *"a refinement of the
  existing `REVISING_PLAN` → `AWAITING_PLAN_APPROVAL` edge, not two
  additional hard gates layered on top of it"* — i.e. it is reachable only
  after at least one `REVISE` round, never directly from this first bundle).
  This plan's own first review round is therefore expected to land at the
  plain `AWAITING_EXTERNAL_PLAN_REVIEW` gate, identical in kind to a
  `governing_workflow_version: "1"` item's first round, exactly as
  `/milestone-plan`'s step 6 states with no `[2.1]` override present at
  that step.

## Review feedback disposition (revision 1 → 2)

Revision 1's external plan review returned `REVISE` with zero blocking and
two important findings. Both were validated against the actual repository
before being applied; both were accepted.

### GPT-DRY-R1-001 — checkpoint/scenario traceability (accepted, applied)

**Validated.** Revision 1's Scope paragraph, the registry's `S-CP2` name and
`WFR-DRY-2` all claimed checkpoint 2 was the dirty subject of S13, S14 *and*
S15, while `WF8B_SCENARIOS.md` already described a different sequence: S13
"completes checkpoint 2 normally," and S14's own authorization text requires
"a dummy checkpoint 3 ... (not checkpoint 2)". Those cannot both hold — after
S13, checkpoint 2 is `COMPLETE`.

Applied as the reviewer proposed, keeping three checkpoints: checkpoint 2
serves S13 only; checkpoint 3 is started and left `IN_PROGRESS` in worktree A
and serves S14 then S15. Scope, the registry, `WFR-DRY-2`/`WFR-DRY-3` and
`WF8B_SCENARIOS.md` now state that one sequence identically.

Two further inconsistencies were found during validation that the finding did
not itself name, and are corrected in the same revision:

- S14's own rationale for using checkpoint 3 — *"to avoid disturbing
  checkpoint 2's real in-progress work from S6/S13"* — was itself stale. By
  the time S14 runs, S13 has already completed checkpoint 2, so there is no
  in-progress work to disturb. The real reason is that checkpoint 2 is
  `COMPLETE` and no longer resumable at all.
- S15's initial-state text (*"rather than finishing S13's resume"*) and the
  execution-ordering note (*"S15 (interrupted recovery, separate dummy
  checkpoint)"*) pointed at two different checkpoints, and the latter could
  be read as demanding a fourth. Both now name checkpoint 3, the one S14
  provably left untouched.

Validating the corrected sequence by executing it (see "Lifecycle validation"
below) surfaced two further defects in S14, both corrected in this revision:

- **S14 named the wrong refusal class.** Its Purpose claimed the scenario
  proves `WorktreeIdentityMismatchError`. But `.ai-review/` is gitignored
  (`.gitignore:1`) and nothing under it is tracked, so a worktree created by
  the `git worktree add` command S14's own authorization text specifies has no
  `WORKTREE_IDENTITY.json` at all, and `verify_dirty_resume_safety` raises
  `WorktreeIdentityMissingError` instead — reproduced against real Git
  worktrees. S14 now specifies both refusals: **S14a** (missing identity, what
  the described setup actually produces) and **S14b** (mismatched identity,
  reached by copying worktree A's gitignored identity file into B). Both
  refuse before any mutation, so the reviewer's own required check — S14
  changes neither checkpoint 3 nor authoritative state — holds for each.
- **S14's pass/fail evidence asked for something the errors do not contain.**
  It required "the error names both the expected and actual
  worktree/`WORKTREE_IDENTITY.json` values"; both messages are in fact purely
  descriptive and name neither value. S14 now says so explicitly and specifies
  what to record instead.

### Lifecycle validation

The corrected sequence was executed as a state-machine walkthrough against the
real registry and the real `workflow_state.py` transitions
(`select_next_checkpoint`, `transition_checkpoint_in_progress`,
`complete_checkpoint`, `verify_dirty_resume_safety`), with S14's refusal
exercised against two genuine Git worktrees rather than simulated. All six
checks the finding required pass: CP2 `IN_PROGRESS` at S13 entry and `COMPLETE`
at S13 exit; CP3 — not CP2 — `IN_PROGRESS` at S14 entry; S14's refusal leaving
the state deep-equal to its before-state; CP3 still `IN_PROGRESS` when S15
begins; CP3 completing normally after the recovery decision; and all three
checkpoints `COMPLETE`, with no fourth selectable, before S7.

The walkthrough also confirmed the step this revision adds is genuinely
necessary: after S13 completes CP2, no checkpoint is left `IN_PROGRESS`, so
S14 and S15 have nothing to act on until the new "S14/S15 setup step" starts
CP3. Revision 1 had no such step, which is the concrete executability hole
behind GPT-DRY-R1-001.

### GPT-DRY-R1-002 — external-review reproducibility (accepted, applied)

**Validated mechanically.** `tar -tzf` on the delivered archive confirms it
carries no `scripts/` entry at all, while `REVIEW_REQUEST.md` instructed the
reviewer to run `python3 scripts/workflow_fingerprint.py ... --work-item-id
v2-1-dry-run` "against the extracted archive." That instruction could not be
followed from the delivered artifact.

The reviewer offered two remedies. **Option 1 (ship
`scripts/workflow_fingerprint.py` plus an archive-safe mode) was rejected on
repository evidence**, and option 2 taken instead:

- that module resolves its own `repo_root` via `git rev-parse
  --show-toplevel` and reaches Git again in `resolve_base` and
  `assert_all_changed_paths_classified_worktree`, so shipping the file
  unchanged does not make it runnable in an extraction directory — and, worse,
  extracting *inside* any checkout would silently resolve to that checkout,
  the exact "silent dependency on the source checkout" this finding is about;
- adding an archive-safe mode means editing `scripts/workflow_fingerprint.py`,
  which is an `implementation_stage.protected_paths` entry of
  `workflow-v2-1-core` and is pinned by that item's **current**
  `technical_approval` manifest at blob `5ac15afb`. Editing it to fix a
  synthetic dry-run's review packaging would stale a durable approval of a
  different work item — out of scope and explicitly out of bounds for WF8b.

Applied instead: `docs/ai-workflow/dry-run/verify_review_content_id.py`, a
self-contained, stdlib-only, read-only recomputation script shipped in the
bundle as review context (listed in `CONTEXT_FILES.txt`, so it lands at
`current/files/docs/ai-workflow/dry-run/`). It resolves `--work-item-id` from
the **bundled** `WORKFLOW_STATE.json` and `<id>-artifacts.json`, recomputes
each protected path's Git blob SHA-1 and the plan-stage projection digest
from bundled bytes alone, and compares the result against the bundled
`MANIFEST.md` and `REVIEW_REQUEST.md`. It needs no Git, no network, no
`PYTHONPATH`, and no repository checkout, and writes nothing.

It is deliberately **not** promoted to plan-stage protected content: it lives
under the already-excluded `docs/ai-workflow/dry-run/` prefix, so adding it
leaves `review_content_id` a function of exactly this item's own plan,
registry and mapping — which is the property under review. The reviewer's own
note ("the verifier/context does not need to become plan-stage protected
merely to be included as review evidence") agrees.

## Review feedback disposition (revision 2 → 3)

Revision 2's `local_model_plan_review` round returned `REVISE` with one
blocking finding and one optional finding. Both were validated against the
actual repository — code read, failure reproduced — before being applied;
both were accepted. No finding was rejected this round.

### LOCAL-DRY-R2-001 — empty `implementation_stage` classification (accepted, applied)

**Validated, and reproduced exactly as described.** Every premise checks out
against the code rather than against the finding's own account of it:

- `generate_artifacts_declarations` (`scripts/workflow_state.py:1543-1564`)
  does emit the plan stage with `workflow-v2-1-core`'s exclusion sets copied
  verbatim and the implementation stage with one self-referential protected
  entry plus three empty dicts;
- `classify_path_implementation_stage`
  (`scripts/workflow_fingerprint.py:1450-1457`) raises
  `UnclassifiedPathError` for any path no set names, and
  `_implementation_stage_protected_changed_paths` (line 1478) runs it over
  `_changed_tracked_paths(base) | _untracked_paths()` — the whole changed
  set, not merely the protected subset;
- loading this item's own declaration and classifying the live changed set
  against `base_commit e75a756` raised
  `UnclassifiedPathError: docs/ai-workflow/WORKFLOW_STATE.json`, with **6 of
  7 paths unclassified** — exactly the six the finding names;
- `artifacts_path_for_work_item` (`workflow_fingerprint.py:759-766`) is pure
  templating with no fallback to another item's declaration, and
  `scripts/prepare-ai-review.sh:355-356` passes `--work-item-id
  "$WORK_ITEM_ID" --stage implementation --write-manifest`, so nothing would
  have rescued the run.

**One detail in the finding is wrong, and correcting it does not weaken
it.** The finding attributes the first implementation-stage bundle to S6
("S6's own stated outcome already includes generating the
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` bundle via
`record_bundle_generation`"). `WF8B_SCENARIOS.md`'s S6 states no such
outcome — it stops at one checkpoint commit per session and its "Expected
files/commits" names only the trailer-carrying commit and
`WORKTREE_IDENTITY.json`. The first implementation-stage bundle is **S7's**,
whose own text names `record_bundle_generation` explicitly. So the abort
point is S7, one scenario later; S6 itself completes unaffected. The
consequence the finding draws is unchanged: S7 aborts, and S8, S9, S10, S12
and S16 fall with it, since each depends on an implementation-stage bundle
existing. Revision 3 puts the precondition note on S7, where the failure
actually lands, and says why S6 does not need one.

Applied as all three of the finding's required corrections:

1. `v2-1-dry-run-artifacts.json`'s `implementation_stage` is populated —
   `docs/ai-workflow/dry-run/scratch/` protected (the deliverable), full
   exclusion coverage otherwise. Note the finding's own warning is right:
   inheriting `workflow-v2-1-core`'s implementation-stage sets would *not*
   have been sufficient, and would additionally have been wrong in kind,
   since those sets hold `scripts/`, `.claude/commands/`, `app/`, `gradle/`,
   `config/`, `.github/workflows/ci.yml`, `MILESTONE_WORKFLOW.md` and
   `REVIEW_PROTOCOL.md` **protected** — content this synthetic item is
   forbidden to touch. Inheriting them would have bound this item's
   `technical_approval` to another item's deliverables. They are declared
   `excluded` here instead, each with that reason stated.
2. The `SELF_REVIEWING_PLAN` bullet now audits both stages and no longer
   claims "No gap found" from a plan-stage-only review.
3. `verify_review_content_id.py`'s disposition is stated explicitly under
   "Known limitations": **left untracked**, with the reasoning there.

**A second gap of the same family was found while performing correction 2,
and is fixed in the same revision.** Auditing the plan stage properly — not
just re-reading revision 2's claim about it — showed that
`generate_artifacts_declarations` copies only `workflow-v2-1-core`'s
plan-stage *exclusion* sets and replaces its protected set with the new
item's three paths. The three documents that item holds plan-stage
**protected** — `docs/ai-workflow/WORKFLOW_V2_PLAN.md`,
`docs/ai-workflow/WORKFLOW_V2_AUDIT.md`, `docs/TECHNICAL_DECISIONS.md` — are
therefore in neither of this item's plan-stage sets, and each probed
`UNCLASSIFIED`. This is live, not theoretical: `compute_review_content_id_plan_stage`
(`workflow_fingerprint.py:1265`) calls
`assert_all_changed_paths_classified_worktree` over the same whole changed
set, and WF8b records this dry run's own progress in
`WORKFLOW_V2_PLAN.md` — so the first time that file is dirty, **this item's
own plan bundle** stops generating. It happens not to be dirty right now,
which is the only reason revision 2's bundle built at all. All three are now
declared `excluded` at both stages.

### LOCAL-DRY-R2-002 — "Known limitations" undercount (accepted, applied)

**Validated by running the test.**
`python3 scripts/workflow_fingerprint_demo_test.py` reports exactly six
unclassified paths, five under `docs/ai-workflow/dry-run/` and
`docs/improvements/FUNCTIONAL_FEATURE_AUDIT.md` outside it — the finding's
count and its list are both right, and revision 2's "three, four with the
verifier" was wrong on both. Corrected in place under "Known limitations",
with the sixth path named and with the reason revision 2 missed this item's
own plan document stated. Left explicitly out of scope, unchanged: the fix
is `workflow-v2-1-core`'s.

### Architecture observation (recorded, not fixed)

The reviewer's `generate_artifacts_declarations` observation is accepted and
recorded under "Known limitations" as a route-out item, extended with the
plan-stage half this round's audit found. Fixing it from here would edit
`scripts/workflow_state.py`, pinned by `workflow-v2-1-core`'s current
`technical_approval` at blob `7643da59`, so it is not fixed here — the same
boundary revision 2 applied to `workflow_fingerprint.py`.

## Review feedback disposition (revision 3 → 4)

Revision 3's `local_model_plan_review` round returned `REVISE` with one
blocking and one important finding. Both were validated against the actual
repository — code read, failure reproduced end to end in a real Git
repository — before being applied; both were accepted. No finding was
rejected this round.

### LOCAL-DRY-R3-001 — plan-approval commit omits this item's own declaration (accepted, applied)

**Validated, and independently reproduced rather than taken on the
finding's word.** Every premise holds:

- `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` is intent-to-add
  only — `git ls-files -s` shows it at the empty blob
  `e69de29bb2d1d6434b8b29ae775ad8c2e48c5391`, and `git cat-file -e
  HEAD:<path>` reports "exists on disk, but not in 'HEAD'";
- the installed `.claude/commands/approve-review.md` step 6 (lines 113-116)
  states the plan-stage commit as "the approved plan doc(s), registry JSON +
  generated Markdown view, `WORKFLOW_STATE.json`, and the requirements
  mapping file together" — four members, no declaration — and step 0's
  `"2.1"` branch adds no fifth;
- `resolve_plan_stage_metadata` resolves the declaration at the commit
  (`workflow_fingerprint.py:858-861`, `_path_exists_at_source(...,
  at_commit)`) and raises `MissingWorkItemArtifactsDeclarationError`.

The reproduction (full detail under "S5 commit-membership evidence" below)
confirms the consequence exactly as the finding stated it, including the
part that makes it blocking rather than a dry-run discovery: the four-member
commit really does omit the declaration, and step 6a, `approval_is_current`
and `implementing_entry_reachable` all **raise** on that commit — the last
of these meaning `/milestone-implement` cannot even answer "no" cleanly.
The failure lands after the state write and the commit.

Applied as all four required corrections:

1. New **"S5 plan-approval commit membership"** section states the exact
   five-path set in protected plan content, names member 5 as a deliberate
   deviation from the installed command's four-member text, and gives the
   reason (`D-Approval-Commits`' conditional fifth member, `GPT-R67-001`,
   which postdates the installed command file).
2. `WF8B_SCENARIOS.md`'s S5 "Purpose", "Expected files/commits" and
   "Pass/fail evidence" now state the five-member set and the checks that
   actually hold, replacing the four-member restatement and the
   `verify_post_approval_manifest_match`-raises-nothing assertion that would
   not have held. That file is excluded at both stages, so the edit stales
   nothing.
3. The unavailable alternative is stated in the same section: a pre-S5
   commit moves HEAD past `MANIFEST.md`'s `generation_head` and trips
   `assert_local_generation_matches`, and regenerating to fix that changes
   `bundle_id`, unbinding the S2/S4 ledger rounds and closing
   `plan_approval_gate_reachable`'s `"2.1"` branch. **Superseded by revision
   5**: the first clause holds, the second does not — `bundle_id` unbinds no
   ledger round and closes no gate; the real cost is an `EXTERNAL_APPROVE` →
   `USER_OVERRIDE` basis downgrade. See `LOCAL-DRY-R4-001`'s disposition and
   the rewritten "The alternative that is not available". Recorded as
   superseded rather than rewritten, so the correction is visible as a
   correction.
4. The second-order consequence is recorded in the same section and
   verified both ways: while uncommitted the declaration never enters the
   implementation-stage manifest, leaving its own self-referential
   protection inert; committing it at S5 makes that protection live.

The finding's own framing was accurate throughout; nothing in it needed
correction this round.

### LOCAL-DRY-R3-002 — stale feedback path and superseded state in `WF8B_SCENARIOS.md` (accepted, applied)

**Both halves validated.**

- `resolve_feedback_dir(repo_root, "v2-1-dry-run")` returns
  `.ai-review/v2-1-dry-run/feedback` (run directly), while the status block
  and S2's "Exact next required action" both named the flat
  `.ai-review/feedback/REVIEW_FEEDBACK.md`.
  `/record-manual-plan-review` step 4 reads `<feedback_dir>/REVIEW_FEEDBACK.md`
  through that same resolution, so a verdict pasted at the flat path would
  not be seen. Confirmed the flat file still holds this item's **revision-1**
  external verdict (`Work item: v2-1-dry-run`, `Reviewed bundle ID:
  d4a12154…`, `Plan revision: 1`) — note this is itself newer than what the
  S2 note claims is there (a `workflow-v2-1-core` revision-21 verdict), so
  that historical sentence had gone stale twice over.
- The status block reported `AWAITING_EXTERNAL_PLAN_REVIEW` with revision
  1's `bundle_id d4a12154…`/`review_content_id 861c9ab1…` as the artifact
  awaiting review, against a live phase of `AWAITING_LOCAL_PLAN_REVIEW` at
  plan revision 3 with one external and two local rounds since.

Applied as the finding asked, taking its "prefer deriving the path"
guidance: the status block is replaced with a current-position block that
names the resolved `<feedback_dir>` rather than a literal, and states the
identity fields in a table explicitly labelled as history that must be
recomputed, never copied. S2's "Exact next required action" now names the
resolved path and the then-current recomputed binding values. The two dated
"Resolved (2026-08-11)" analysis paragraphs are kept — they are genuine
read-only evidence from that session — but are explicitly marked superseded,
with current state pointed back to the status block, rather than silently
rewritten into a history that never happened.

### Architecture observation (recorded, routed out, not fixed here)

The reviewer's observation is accepted and is a second instance of the
generator gap already routed out under "Known limitations":
`generate_artifacts_declarations` creates `<work_item_id>-artifacts.json` as
a working-tree file, nothing in the plan-stage lifecycle commits it, and
`resolve_plan_stage_metadata` requires it **at the approval commit**. Every
future `"2.1"` work item created through `/milestone-plan` whose first
durable commit is its own plan approval inherits the same abort;
`workflow-v2-1-core` is immune only because its declaration was committed
earlier as a WF4a-i deliverable. Not fixed from here: the fix is in
`.claude/commands/approve-review.md` or `scripts/workflow_state.py`, both
pinned by that item's current `technical_approval`. Recorded under "Known
limitations" alongside the existing generator route-out.

## Review feedback disposition (revision 4 → 5)

Revision 4's `local_model_plan_review` round returned `REVISE` with **zero
blocking findings**, three important findings and one optional finding. All
four were validated against the actual repository — signatures read, every
reader of the field in question grepped, and each finding's own "Required
tests / dry-run evidence" reproduced independently rather than taken on the
finding's word — and all four were accepted. No finding was rejected this
round.

All three important findings share a shape worth naming: revision 4's
*disposition* (the five-member commit set) was correct and is unchanged, and
the reviewer explicitly re-verified it rather than reopening it. What was
wrong was how revision 4 **described** the mechanisms it invokes. That is
still worth a revision here, because this item's entire purpose is proving
the `"2.1"` gates behave as documented — a plan that records a gate's key
backwards would have taught the manual external reviewer, and any later
session, the wrong model of the system.

### LOCAL-DRY-R4-001 — the unavailable-alternative subsection described a fail-open outcome as fail-closed (accepted, applied)

**Validated, and all three of the finding's required tests reproduced
independently** (see "Gate-behavior evidence (revision 5)" below). Every
premise holds:

- `plan_approval_gate_reachable`'s signature is `(latest_round_status,
  governing_workflow_version, plan_review_stages, current_review_content_id)`
  (`scripts/workflow_state.py:2025-2049`) — `bundle_id` is not an argument,
  so a changed `bundle_id` cannot close the gate;
- no reader of `plan_review_stages` consults a stage entry's recorded
  `bundle_id`. Grepped every reference in `scripts/` and `.claude/commands/`:
  the readers are `plan_approval_gate_reachable`,
  `validate_manual_plan_review_preconditions` (`workflow_state.py:2833-2848`)
  and `_validate_plan_review_stages` (`:3117-3141`). None touches the field.
  `check_manual_stage_bundle_id_advisory` (`:2850-2866`) is advisory by
  explicit design and its docstring says so;
- a pre-S5 commit does not move the plan-stage `review_content_id` either:
  `compute_review_content_id_plan_stage` hashes `base_commit`, the protected
  paths' **worktree** bytes and the resolved metadata
  (`workflow_fingerprint.py:1266-1281`) — HEAD is not an input. Reproduced in
  an isolated real repository: digest identical before and after committing
  the declaration;
- what actually changes is the approval *basis*: `resolve_approval_basis`
  returns `USER_OVERRIDE` on a feedback/current `bundle_id` mismatch
  (`workflow_state.py:2083-2111`), and only a `BLOCK` status raises.

So the system fails **open at a weaker basis** where revision 4 told a future
executor it fails closed. Applied as the finding's required correction:
"The alternative that is not available" is rewritten into two explicitly
separated branches — the un-regenerated branch's genuine
`assert_local_generation_matches` refusal (revision 4's first clause, which
was correct and is kept) and the regenerated branch's `EXTERNAL_APPROVE` →
`USER_OVERRIDE` downgrade — with the ledger-unbinding and gate-closing claims
removed and the gate-closure claim reattached to `review_content_id`, the
only input the gate keys on. `LOCAL-DRY-R3-001`'s disposition item 3, which
restated the same false claim, is marked superseded in place rather than
silently rewritten.

The finding's own framing was accurate throughout; nothing in it needed
correction.

### LOCAL-DRY-R4-002 — digest-neutrality claim contradicted by this document's own evidence section (accepted, applied)

**Validated by reading both sections against each other and against the
code.** The claim was that "both the four-member and five-member commits
recompute to the identical worktree-source digest — demonstrated … in 'S5
commit-membership evidence' below". The four-member commit does not recompute
to a digest: `resolve_plan_stage_metadata` raises
`MissingWorkItemArtifactsDeclarationError` when the declaration is absent at
the commit (`workflow_fingerprint.py:858-861`), which is exactly what
evidence item 1 reports three paragraphs later and exactly what
`LOCAL-DRY-R3-001` exists about. The evidence section demonstrates the
plan-stage digest only at the five-member commit (item 2); its only
four-member recompute is the *implementation*-stage one.

Applied as the required correction: the sentence now claims only what the
evidence shows — the five-member commit's commit-source digest equals the
pre-commit worktree-source value, so member 5 moves no approved value — and
states explicitly that the four-member commit raises rather than recomputing,
with the implementation-stage four-member recompute identified as the
unrelated fact it is. The underlying claim being defended (the declaration is
plan-stage excluded, so it never enters the projection's manifest) was true,
is unchanged, and is now the sentence that carries the argument.

### LOCAL-DRY-R4-003 — member 5 stated unconditionally, dropping its authorizing contract's own conditions (accepted, applied)

**Validated against `D-Approval-Commits` directly** (`WORKFLOW_V2_PLAN.md`,
"Conditional fifth commit member", lines 14665-14703): the contract states a
pending-change condition and a bundle-freshness condition, checked in order,
plus an explicit refusal rule — `/approve-review plan` "refuses outright,
naming the stale path, rather than silently committing bytes the external
reviewer never saw". Revision 4 imported member 5 and neither condition, so
"exactly these five paths, no more and no fewer" instructed an executor to
stage the declaration's then-current bytes with no check that the reviewer
ever saw them.

Both conditions were verified to hold right now, and the verification is
recorded rather than asserted: `git cat-file -e
HEAD:docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` reports the path
absent at HEAD and `git ls-files -s` shows the intent-to-add empty blob
`e69de29b…` (condition 1); the worktree file and the bundle's captured copy
are byte-identical at sha256 `02fef08e…3a484c740e` (condition 2). The
exposure is real rather than theoretical because S5 runs several scenarios
after this revision and S12's own procedure deliberately edits a protected
path outside the normal command flow.

Applied as the required correction: a new "Member 5 is conditional, not
unconditional" subsection states both conditions and the refusal rule in the
same normative section, records the current verification, and requires
re-checking at S5 execution time rather than reading the result from this
document. `WF8B_SCENARIOS.md`'s S5 gains the byte-comparison as an explicit
pre-staging step and as pass/fail evidence; that file is excluded at both
stages (`docs/ai-workflow/dry-run/` is an excluded prefix in both
`plan_stage` and `implementation_stage` of this item's own declaration), so
the edit stales nothing.

### Optional finding — fixture-scoped clean-tree claim (accepted, applied)

Validated: evidence item 4's "`git status --short` afterwards was empty" is
true of the isolated fixture, whose working tree held only the five staged
paths, and false of the live worktree at S5, where `WF8B_SCENARIOS.md` is
modified and `verify_review_content_id.py` is untracked — both deliberately.
`WF8B_SCENARIOS.md`'s S5 already scoped its own check correctly ("no leftover
` A` entry for the declaration"). Applied as the finding suggested: one
clarifying clause in evidence item 4 marking the clean-tree observation as a
fixture property and pointing at S5's actual, narrower exit condition, so the
two documents cannot be read as setting different exit conditions.

### Architecture observation (recorded, routed out, not fixed here)

The reviewer's observation — that `plan_review_stages` records a per-stage
`bundle_id` no gate ever reads, which is what made revision 4 reasonably
assume it was load-bearing — is accepted and explicitly **not** fixed from
here, as the reviewer asked. Either the field is documentation-only and
should say so where it is written, or the intended binding is missing;
`GPT-R11-006` establishes the advisory reading as deliberate for the manual
stage, and whether the same holds for the local stage is a question for
`workflow-v2-1-core`'s own design. Fixing it here would edit
`scripts/workflow_state.py`, pinned by that item's current
`technical_approval` at blob `7643da59`. Recorded under "Known limitations"
alongside the existing route-outs.

## Gate-behavior evidence (revision 5)

`LOCAL-DRY-R4-001`'s three required tests, run against the real
`scripts/workflow_state.py`/`workflow_fingerprint.py` in this worktree — the
first and third as direct calls, the second in an isolated real Git
repository built to the same standard as the S5 fixture below.

1. **`plan_approval_gate_reachable` returns `True` across a `bundle_id`
   change.** Called with a `plan_review_stages` ledger whose two stages both
   record `bundle_id 76b5fd49…996b34` and a live recomputation carrying a
   different one, at an unchanged `review_content_id 0652c528…c6ad5f`:
   returned `True`. The live `bundle_id` is not even an argument to the
   function, which is the point. Called with the same ledger and a *changed*
   `current_review_content_id`: returned `False` — confirming
   `review_content_id`, and only `review_content_id`, closes this gate.
2. **A pre-S5 commit leaves the worktree-source `review_content_id`
   unchanged.** Fixture: a real `git init` repository carrying this
   repository's actual `scripts/`, this item's four artifacts at their live
   bytes, a `WORKFLOW_STATE.json` whose `v2-1-dry-run` entry is repointed at
   the fixture base, and all five paths made intent-to-add (` A` status
   reproduced exactly). `compute_review_content_id_plan_stage_for_work_item`
   returned `8d4a8d03…b5bdb464` before committing the declaration alone, HEAD
   moved `a8176b1b` → `31ec8fbd`, and the same call returned
   `8d4a8d03…b5bdb464` after. Identical — the ledger stays bound.
3. **`resolve_approval_basis` returns `USER_OVERRIDE` on a `bundle_id`
   mismatch.** With `latest_round_status="APPROVE"` and a valid
   `user_confirmation`: feedback `bundle_id 76b5fd49…996b34` against a
   differing current value returned `USER_OVERRIDE`; the same call with the
   two equal returned `EXTERNAL_APPROVE`. The downgrade is silent — no
   exception, no refusal.

The fixture digest (`8d4a8d03…`) is fixture-local and is **not** this item's
real identity, for the same reason the S5 evidence below states: this item's
live values must be recomputed, never copied from here.

## S5 commit-membership evidence (revision 4)

`LOCAL-DRY-R3-001`'s four required tests, run in an isolated real Git
repository — the same standard revision 3's required test 3 used, and for
the same reason: the live worktree cannot run S5 without performing the
very approval this plan is not yet approved for. What was staged is stated
in full.

**Fixture.** A real `git init` repository carrying this repository's actual
`scripts/` (copied verbatim), a `.gitignore`, and a base commit whose
`docs/ai-workflow/WORKFLOW_STATE.json` is this repository's own HEAD
version. Its working tree then received this item's four artifacts as live
bytes, with `WORKFLOW_STATE.json`'s `v2-1-dry-run` entry repointed at the
fixture's base commit, and all four made intent-to-add via `git add -N` —
reproducing this worktree's exact ` A` status. Real script, real Git, real
declaration; only the surrounding lifecycle is staged.

1. **The four-member commit reproduces the abort.** Staging exactly the
   plan doc, registry, mapping and `WORKFLOW_STATE.json` and committing with
   a plain `git commit` produced a commit whose path set is those four;
   `git status --short` afterwards still showed ` A` for
   `v2-1-dry-run-artifacts.json`, confirming a plain commit omits an
   intent-to-add path whose content was never staged. On that commit:
   - `verify_post_approval_manifest_match` (step 6a) raised
     `MissingWorkItemArtifactsDeclarationError:
     docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`;
   - `approval_is_current(..., stage="plan")` raised the same error;
   - `implementing_entry_reachable(...)` raised it too — it does not return
     `False`, so `/milestone-implement` aborts rather than reporting an
     unmet entry condition.

   All three raised **after** `plan_approval` had been written with
   `status: CURRENT` and `phase: IMPLEMENTING`, and after the commit
   existed — the durable partial state the finding describes.
2. **The five-member commit passes, and the approved digest does not
   move.** Same fixture, staging additionally
   `docs/ai-workflow/registry/v2-1-dry-run-artifacts.json`:
   - `verify_post_approval_manifest_match` raised nothing;
   - the commit-source `review_content_id` recomputed at that commit equals
     the worktree-source value recorded in `plan_approval` **exactly** (both
     `5586f09a7018ea7ef8f77a9bb57afb6dbbc286912b7d30c502981e77e9fe5091` in
     the fixture — the declaration is plan-stage excluded, so it never
     enters the projection's manifest). Adding member 5 changes no approved
     value.
3. **S6 can start.** At that same commit, `approval_is_current(...,
   stage="plan")` and `implementing_entry_reachable(...)` both returned
   `True`.
4. **The commit's path set is exactly the declared membership.** `git show
   --name-only --format=` on the five-member commit listed precisely the
   five paths of "S5 plan-approval commit membership" and nothing else — no
   `verify_review_content_id.py`, no `WF8B_SCENARIOS.md`, no scratch files —
   and `git status --short` afterwards was empty. That last clause is a
   property of the **fixture**, whose working tree held nothing but the five
   staged paths; it is not S5's exit condition. In the live worktree
   `WF8B_SCENARIOS.md` is modified and `verify_review_content_id.py` is
   untracked, both deliberately, and both are still there after S5. S5's
   actual check is the narrower one `WF8B_SCENARIOS.md` already states: no
   leftover ` A` entry for the declaration (clarified in revision 5, this
   round's optional finding).

Additionally, for the second-order consequence recorded above: the
implementation-stage recompute at the **four**-member commit succeeded with
an empty `review_content_manifest` (the declaration absent), while at the
**five**-member commit the same recompute listed
`docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` as a manifest member
and produced a correspondingly different digest.

The fixture's digests (`5586f09a…` and the implementation-stage values) are
fixture-local and are **not** this item's real identities — they are stated
only to show the two commit-source/worktree-source values matching each
other. This item's live values must be recomputed at S5, never copied from
here.

## Classification evidence (revision 3)

Run against real repository state at `base_commit e75a756`, not asserted.
Items 1-4 are `LOCAL-DRY-R2-001`'s own "Required tests / dry-run evidence"
list, in order.

1. **Live changed set classifies.** Loading this item's own
   `implementation_stage` sets and classifying
   `_changed_tracked_paths(e75a756) | _untracked_paths()` — 7 paths — raises
   no `UnclassifiedPathError`. One classifies `protected`
   (`v2-1-dry-run-artifacts.json`, its own self-referential entry); the other
   six classify `excluded`. The plan stage classifies the same 7 with no
   error.
2. **With the scratch deliverables present.** Creating
   `docs/ai-workflow/dry-run/scratch/{a,b,c}.txt` takes the changed set to 10
   paths, all classifying, with the three scratch files classifying
   `protected` — the protected prefix beating the broader `dry-run/`
   exclusion, as intended. The three files were removed again after the run.
3. **`prepare-ai-review.sh … implementation v2-1-dry-run` completes and
   writes `MANIFEST.md`** — reported
   `review_content_id (write -> recompute -> equal):
   1c1b351fa8b6f3dccc1c28d530e42d0aa6a6795cb8e1e1046a6dcfa58d07c6d9` and
   `bundle_id (write -> recompute -> equal):
   27a0c72a8b58b35eb625df5a389c3482148215cc92622098c4e93070d2a5b011`, with
   the manifest's protected-path/prefix/exclusion sections rendering this
   item's own declaration.

   This one **cannot** be run against this worktree at this point in the
   lifecycle, and was run in an isolated real Git repository instead. Two
   independent reasons, both verified rather than assumed: (a)
   `prepare-ai-review.sh`'s round-identity preflight (lines 172-234) requires
   `work_items["v2-1-dry-run"].reviewed_implementation_head` to equal the
   generation head, and it is `null` here — satisfying it would mean calling
   `record_bundle_generation` on an item that has no plan approval and no
   implementation, an illegitimate state write; (b) the implementation-stage
   bundle directory for this item **is** `.ai-review/v2-1-dry-run/current`,
   the plan bundle currently under review, which the run would overwrite. The
   isolated repository is a real `git init` checkout carrying this
   repository's actual `scripts/`, `docs/ai-workflow/` and `.gitignore`, with
   a real base commit, a real checkpoint commit adding the three scratch
   files, and `WORKFLOW_STATE.json` set to the state S7 would legitimately
   have reached. Real script, real Git, real declaration — only the
   surrounding lifecycle is staged.
4. **The digest is this item's own, and cannot silently be
   `workflow-v2-1-core`'s.** In the same isolated repository, with the
   implementation-stage manifest listing exactly
   `docs/ai-workflow/dry-run/scratch/{a,b,c}.txt`:
   - perturbing content `workflow-v2-1-core` holds implementation-stage
     protected (`scripts/workflow_fingerprint.py` and
     `docs/ai-workflow/MILESTONE_WORKFLOW.md`) left the digest **unchanged**
     at `1c1b351f…`;
   - perturbing this item's own `scratch/b.txt` **changed** it to
     `114d2d93…` (which is also what S8's planted-defect step needs in order
     to move the digest at all);
   - computing the same tree under `workflow-v2-1-core`'s declaration does
     not merely give a different digest — it raises
     `UnclassifiedPathError: docs/ai-workflow/dry-run/scratch/a.txt`, since
     that item's own sets cannot classify this item's deliverables. The two
     identities are structurally incapable of collapsing into one.
   - every perturbation was reverted and the digest re-derived back to
     `1c1b351f…`.
5. **Fail-closed survives the widened sets.** `build.gradle.kts`,
   `gradle.properties` and an invented `some/new/toplevel.txt` all still
   raise `UnclassifiedPathError` at both stages.
6. **Revision 1's two findings remain applied** (acceptance criterion 5).
   `WF8B_SCENARIOS.md`'s CP2/CP3 ownership is untouched by this revision —
   the only edit to that file is S7's new precondition note — and
   `verify_review_content_id.py` is unmodified, still bundled, still
   byte-identical at sha256 `65ed2419…`.

## Areas the reviewer should challenge

- Whether declaring `docs/ai-workflow/dry-run/scratch/` implementation-stage
  **protected** is the right call, versus excluding it and leaving the
  declarations file as this item's only protected content. Protecting it
  makes `technical_approval` bind to the content the implementation review
  actually reads and gives S8's planted defect something to move; excluding
  it would make the implementation-stage digest nearly content-free. The
  cost is that every scratch-file edit restales the approval — which for a
  three-marker-file item is the intended behaviour, but is worth a second
  opinion.
- Whether the isolated-repository evidence for required test 3 is
  acceptable, given the two reasons a live run is impossible before S7. If
  not, the alternative is to defer that evidence to S7 itself and accept
  plan approval without it.
- Whether `verify_review_content_id.py`'s independently-written recomputation
  is a genuine check or merely restates the producer's own logic. It is
  written against the projection's *definition* (field set, canonical JSON
  encoding, Git blob hashing) rather than by importing the producer, so a
  regression in `workflow_fingerprint.py` would surface as a disagreement —
  but it shares that definition by construction, and that limit is real.
- Whether checkpoint 3 serving both S14 and S15 is genuinely safe. It rests
  entirely on S14 making zero mutation; `WF8B_SCENARIOS.md`'s S14 already
  requires that to be *verified* immediately after the refusal (its
  authorization point 3), not inferred from the error message.
- Whether 3 trivial checkpoints is still enough surface once S8's "seed a
  defect, remediate" step is reached, given checkpoints 2 and 3 now both
  carry dedicated lifecycle roles.
- **New in revision 5**: whether importing `D-Approval-Commits`' two
  conditions into this item's S5 as a *manually executed* pre-staging check
  is the right shape, given that the installed `/approve-review` command
  implements neither (they postdate the command file, which is the same
  reason member 5 needs a written decision at all). The alternative is to
  state the conditions as documentation only and rely on the five-member
  `git show` assertion after the fact — which would catch a wrong path set
  but not stale *bytes* at a correct path, the exact case the freshness
  condition exists for.
- **New in revision 5**: whether recording the `EXTERNAL_APPROVE` →
  `USER_OVERRIDE` downgrade as merely "the cost" of branch 2 understates it.
  S5 is a user-gated scenario, so a downgrade would be visible in the
  approval record's own `basis` field — but nothing *stops* it, and this dry
  run has no scenario that deliberately exercises the downgrade path to
  confirm it is observable in practice.
