# Active Milestone

## Milestone

All roadmap milestones (0-8) are **complete**. Milestone 8 (post-MVP
functional usability stabilization) is accepted and closed under an
explicit user waiver of its planned functional-review gate — see
"Functional review disposition" below.

## Goal

Milestone 8 closed the functional/usability gaps in
`docs/milestones/completed/milestone-8-reference.md`'s "Goals" section:
Recovery/futsal date/scale/history/save-feedback fixes, workout/plan
set-classification and start-from-plan wiring, History filtering and safe
accidental-workout removal, training-plan archive/restore, navigation
consistency, and backup hardening.

## Current checkpoint

**Milestone 8 accepted and complete.** All checkpoints (P0, CP0-CP16)
implemented and committed; full checkpoint-by-checkpoint detail is
preserved in git history for this file up to `dc4381a` and in the
archived plan docs.

- Implementation review: **approved** (round 4, `APPROVE` — no new
  blocking/important finding; rounds 1-3 findings all resolved).
- Integration: **complete** — `dc4381a` (last implementation-review-round
  commit) is reachable from `main` via merge `b39af90`, verified by
  `git merge-base --is-ancestor` before this doc update.
- Functional review: **waived** by explicit user decision — not
  performed, and not claimed to have passed. See "Functional review
  disposition" below.
- Milestone status: **accepted/complete**.

## Functional review disposition

The manual functional-review checklist prepared at CP16 (23 numbered
flows covering navigation, Recovery/futsal, workout/plan wiring, History,
archive/restore, and backup) was written and is preserved in git history
at `dc4381a`, but was never walked by the user on a device.

On 2026-07-31 the user explicitly accepted and closed Milestone 8 while
waiving that review, stating explicitly this is a user-authorized waiver,
not a claim that functional review passed. Stated reasons:

- technical implementation and external implementation review are
  complete;
- the implementation is integrated into `main`;
- the user is postponing detailed UI/UX validation;
- future UI/UX work will be handled as a separate, later, Figma-led
  redesign milestone.

## Current blockers

None. Milestone 8 is accepted and closed. No roadmap milestone is
currently active.

## Active plan

None active. Milestone 8's plans are archived at
`docs/milestones/completed/milestone-8-{execution,reference}.md`.
Milestones 1-7 remain archived alongside them.

## Next action

No further roadmap milestone is currently defined — `docs/ROADMAP.md`
0-8 are all complete. `docs/improvements/IMPROVEMENT_ROADMAP.md` §2.1
(`ReturnCount` tuning) was deferred pending Milestone 8's acceptance and
is now unblocked, but has not been started or scheduled. The
user has indicated the next substantive product work will be a separate
Figma-led UI/UX redesign milestone; it has not been planned or scoped in
this repository. Do not begin it and do not run `/milestone-plan` until
the user explicitly initiates that work.

---

## `workflow-v2-1-core` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above. It
tracks the separate, non-product process work item `workflow-v2-1-core`
(see `docs/ai-workflow/WORKFLOW_V2_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`).

**Status note (2026-08-04)**: the checklist below (five checks) was
independently re-verified and the user gave explicit functional
acceptance of it in conversation — but before that acceptance could be
recorded, a separate finding was discovered
(`docs/ai-workflow/dry-run/WF8B_FINDING_continued_scope_remediation_no_nonterminal_return_path.md`):
the workflow tooling had no safe, non-terminal way to record acceptance
of a continued-scope round (like this one) while the item's own last
checkpoint (`WF8b`) remains incomplete. A plan revision
(`D-Scoped-Remediation-Acceptance` in `docs/ai-workflow/WORKFLOW_V2_PLAN.md`)
fixing that gap went through six external plan-review rounds (22 through
27), each round's findings applied in place within the same section —
`GPT-R36-001`/`-002`/`-003` (fail-open registry guard, uncommitted
acceptance, unbound evidence), `GPT-R37-*`, `GPT-R38-*`, `GPT-R39-*`, and
`GPT-R40-001`/`-002` (the required evidence-binding: a `scoped_remediation`
confirmation must now name the exact `/prepare-functional-review`-reported
checklist-evidence commit SHA and blob) — until Revision 27 came back
`Status: APPROVE` with zero blocking/important findings and was recorded
as `plan_approval` (commit `c132185`). This revision's own implementation
(the `complete_work_item` own-registry guard, the functional-checklist
evidence trailer/guard machinery, the new `/accept-scoped-remediation`
command, and the extensive test suite covering every named scenario) has
now landed as continued `WF4c` scope and is awaiting its own external
implementation review — `phase` remains `IMPLEMENTING` until that review
and `/approve-review implementation` complete; `AWAITING_FUNCTIONAL_REVIEW`
is not yet re-entered. The checklist below is preserved unchanged; the
user's acceptance of it has **not** been recorded anywhere yet
(deliberately) and will be, via `/accept-scoped-remediation`, once this
round's technical approval lands.

**Context**: checkpoint `WF8b` (manual multi-session dry run) began
against the synthetic work item `v2-1-dry-run`, but its first scenario
(S1) blocked before any mutation on a real defect:
`scripts/workflow_fingerprint.py`'s `--work-item-id` flag affected only
which bundle directory was written to — every actual identity/content
computation silently hardcoded `workflow-v2-1-core`'s own metadata
regardless of the flag (see
`docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md`).
That defect was fixed as continued WF8b scope under a revised plan
(Revision 21), went through five rounds of external implementation
review (`GPT-R30` through `GPT-R34`, all resolved), and now has
`technical_approval` recorded for implementation revision 4
(`8f9ea8cc60192bbbecfb7ed092c1dc79739047c7616ea4e2cddd8b68ecd0c9ed`,
commit `7bef596`). `WF8b` itself is **not yet complete** — this
functional review gates only the fingerprint-generalization fix, which
must pass before scenario S1 can be safely rerun.

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `scripts/prepare-ai-review.sh`,
`docs/ai-workflow/*`, `.claude/commands/*.md`). No build/install step is
needed; everything below runs with `python3` from the repo root.

### Automated verification (already re-confirmed this session, current)

All green, re-run independently right before this checklist was written
(no source/test file has changed since `technical_approval` was
recorded — the last commit, `ae51770`, is metadata-only):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 51/51 |
| `scripts/workflow_state_test.py` | 221/221 |
| `scripts/workflow_integration_test.py` | 35/35 |
| `scripts/workflow_test_harness_test.py` | 19/19 |

Total: 448/448. (`workflow_fingerprint_demo_test.py` has pre-existing,
unrelated failures against real-repository paths outside this item's
scope — documented as such since the `GPT-R32` round; not part of this
review.)

### Test data

None to seed — all checks below read this repository's own real,
already-committed state (`workflow-v2-1-core` and the dormant
`v2-1-dry-run`/`milestone-8` entries already in
`docs/ai-workflow/WORKFLOW_STATE.json`).

### Flows to exercise manually

1. **Fix confirmed — generic CLI now resolves the real item correctly.**
   Run:
   ```
   python3 scripts/workflow_fingerprint.py --work-item-id workflow-v2-1-core 162154d3e5e10eb65e109833acae4b4fb01fc5d6
   ```
   Expected: prints `work_item_id: workflow-v2-1-core`,
   `review_content_id: b6d4ea6a8778321526fa5a3a6d2af17801f6187fd137680bbef8cce008ef95c0`
   (matches `plan_approval.approved_review_content_id` exactly). Already
   re-run this session — passed.
2. **Fix confirmed — no more silent cross-item fallback.** Run:
   ```
   python3 scripts/workflow_fingerprint.py --work-item-id v2-1-dry-run
   ```
   Expected: fails closed with `MissingPlanStageMetadataError:
   v2-1-dry-run.registry_path is null` — naming `v2-1-dry-run` itself,
   not silently substituting `workflow-v2-1-core`'s own data (the
   original S1 defect). Already re-run this session — passed.
3. **Default resolution still targets the live active item.** Run the
   same command with no `--work-item-id` at all; expect the identical
   error (since `active_work_item_id` is currently `v2-1-dry-run`),
   confirming the documented "omitted resolves to
   `active_work_item_id`" default. Already re-run this session — passed.
4. **Re-run the automated suite yourself** (table above) and confirm the
   same 448/448 result independently, rather than trusting this
   document's claim alone.
5. **Spot-check the migration**: confirm
   `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json` is
   `schema_version: 2` with both `plan_stage` and `implementation_stage`
   keys present.

### Expected result

All five checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- This review does **not** cover `WF8b` itself — the dry run's S1
  through S17 scenarios remain unexecuted and are explicitly deferred to
  a fresh session per `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`.
- Item 160 (see `IMPLEMENTATION_SUMMARY.md`'s "Accepted, not fully
  closed" section) is the process obligation this review partially
  discharges (suite green, confirmed above) — the other half (a
  fresh-session S1 re-attempt) happens after this review, not during it.
- A handful of named test sub-cases remain partial, not absent (see
  `IMPLEMENTATION_SUMMARY.md`'s "Accepted, not fully closed this pass"
  list) — not blocking for this functional review.

Findings go in `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`.

### `workflow-v2-1-core` — MILESTONE_COMPLETE (real, 2026-08-21)

Functional review for implementation revision 17 was completed and
recorded clean: `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`
(gitignored, not itself a repository artifact) records `Result: PASS`
against the revision-17 checklist above, citing evidence commit `805b966`
/ blob `8b0a49ba217d62ff8bafcf0638876dfd564babca`, with no functional
defect, usability issue, or missing requirement found blocking.

The user then gave literal `/accept-milestone`-stage confirmation for
`workflow-v2-1-core` ("I confirm acceptance of workflow-v2-1-core."),
validated for real by `workflow_state.validate_user_confirmation`. The
advisory terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True` from phase
`AWAITING_FUNCTIONAL_REVIEW`) and the authoritative
`workflow_state.complete_work_item` call both succeeded: no
`IncompleteChildWorkItemError` (no work item declares
`workflow-v2-1-core` as its `parent_work_item_id` — only `milestone-8`
exists alongside it, and is unrelated), no `IncompleteOwnCheckpointsError`
(all 18 registry checkpoints `COMPLETE`), and no
`UnsatisfiedCompletionObligationError` (both declared completion
obligations, `WFO-LEDGER-COVERAGE` and `WFO-STATE-SERIALIZATION`,
resolved `PASS`, recorded in
`work_items["workflow-v2-1-core"].completion_obligations_accepted`).
`work_items["workflow-v2-1-core"].phase` is now `MILESTONE_COMPLETE`;
`active_work_item_id` reset to `null` (it pointed here).

**Steps 3/5 do not apply — the same gap the `v2-1-dry-run` dry run
already predicted this item would face** (see that item's own S16
`MILESTONE_COMPLETE` note further below): `grep` confirms zero mentions
of `workflow-v2-1-core` anywhere in `docs/ROADMAP.md` (step 3 has no
entry to mark). `docs/ai-workflow/WORKFLOW_V2_PLAN.md` is **not**
archived to `docs/milestones/completed/` (step 5) — unlike a product
milestone's execution/reference plan, which really is finished once its
feature ships, this document remains the live design reference for the
workflow tooling itself, still linked from
`docs/ai-workflow/REVIEW_PROTOCOL.md`, `MILESTONE_WORKFLOW.md`, and this
repository's `CLAUDE.md`; archiving it would misfile still-authoritative
documentation as historical. Step 4 is honored via this note itself,
placed in this item's own section rather than the top-level "Active
plan" section, which belongs to Milestone 8 alone and is untouched by
this completion. Step 7 ("Next action") is unaffected for the same
reason `v2-1-dry-run`'s was: it already correctly points at the real
`docs/ROADMAP.md`, which `workflow-v2-1-core` was never part of.

---

## `workflow-v2-1-core` — functional review checklist (implementation revision 17)

This section is unrelated to the roadmap/Milestone 8 content above. It is
the same process work item as the revision-4 checklist above, re-entering
`AWAITING_FUNCTIONAL_REVIEW` for the first time since then.

**Context**: between revision 4 and now, this item stayed under `WF8b`
(later `WF8c`) as continued implementation scope through 13 further
implementation rounds — the item never left `IMPLEMENTING`/
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`/`APPLYING_REVIEW_FEEDBACK` in
that span, so no intermediate functional-review checkpoint exists to
report on. `WF8c` (the item's own last registry checkpoint) is now
`COMPLETE` — all 18 registry checkpoints are `COMPLETE`
(`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`) — and
`technical_approval` was just recorded for implementation revision 17
(commit `838a523`, `Workflow-Technical-Approval:
a44d912afd81fa757d2b9893e77a0d8990bce05ed78ad487352d3e849efe9143`, basis
`EXTERNAL_APPROVE`, round GPT-R137).

Re-walking all 18 checkpoints' behavior in one manual pass is not
practical, so this checklist targets two things instead: (1) the exact
commands this same session already exercised for real, end to end
(the most direct functional test available for process tooling — there
is no separate UI to click through), and (2) the specific behavioral
fixes revision 17 itself introduced (`GPT-R136-001`,
`OPUS-R136-M01`/`-M03`/`-M04`), since those are what round GPT-R137
actually reviewed and approved this round.

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since the last full run at `894dd94`):

```
python3 -m unittest workflow_integration_test workflow_state_test \
  workflow_state_completion_obligations_test workflow_fingerprint_test \
  workflow_test_harness_test workflow_fingerprint_generalization_test
```

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | part of 1097 |
| `scripts/workflow_fingerprint_generalization_test.py` | part of 1097 |
| `scripts/workflow_state_test.py` | part of 1097 |
| `scripts/workflow_state_completion_obligations_test.py` | part of 1097 |
| `scripts/workflow_integration_test.py` | part of 1097 |
| `scripts/workflow_test_harness_test.py` | part of 1097 |

Total: **1097/1097**, zero failures/errors/skips (matches
`TEST_RESULTS.md`'s recorded result exactly). Includes six new regression
tests added this round for the four fixes below; all six were also
independently confirmed to fail against revision 16's pre-fix code and
pass against revision 17 (`TEST_RESULTS.md`'s own "Confirmed non-vacuous"
section).

### Test data

None to seed — every check below reads this repository's own real,
already-committed state.

### Flows to exercise manually

1. **Re-run the automated suite yourself** (command above, from
   `scripts/`) and confirm 1097/1097 independently, rather than trusting
   this document's claim alone.
2. **`GPT-R136-001` — evidence clone no longer shares donor objects.**
   The pinned-evidence materializer (`workflow_state._materialize_pinned_
   worktree_at_commit`) now clones with `--dissociate` whenever the
   source repository itself borrows objects through an alternate.
   `TEST_RESULTS.md`'s "Additional checks run this session" section
   documents a hand reproduction (donor/source fixture, materialize,
   delete the donor, confirm the resulting clone still reads clean); the
   corresponding automated case is
   `test_evidence_clone_has_no_alternates_when_source_repository_borrows_objects`
   in `scripts/workflow_state_completion_obligations_test.py`. Run it in
   isolation to confirm it passes on its own:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_evidence_clone_has_no_alternates_when_source_repository_borrows_objects
   ```
3. **`OPUS-R136-M01` — an inherited global `core.hooksPath` no longer
   fires during evidence materialization.** Same test class, run:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_materialization_clone_step_ignores_an_inherited_global_hooks_path
   ```
4. **`OPUS-R136-M03` — the cleanliness walk now catches a nested `.git`
   payload and an untracked empty directory.** Same test class, run:
   ```
   python3 -m unittest workflow_state_completion_obligations_test.TestPinnedEvidenceWorktreeIsolation.test_verify_pinned_worktree_clean_catches_nested_dot_git_and_untracked_empty_directory
   ```
5. **`OPUS-R136-M04` — an omitted `--work-item-id` no longer silently
   accepts a stale `IMPLEMENTATION_SUMMARY.md` revision.** Run:
   ```
   python3 -m unittest workflow_fingerprint_test.TestGeneratorSideStageDocumentBinding.test_finalize_bundle_generation_omitted_work_item_id_reports_mismatch_on_stale_revision
   ```
6. **The actual `/approve-review implementation` flow, exercised for
   real this session** (not simulated): gate-reachability check,
   fresh `bundle_id`/`review_content_id` recomputation, `resolve_approval_
   basis` → `EXTERNAL_APPROVE`, `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`838a523`) carrying
   the `Workflow-Technical-Approval`/`Workflow-Work-Item` trailers.
   Confirm independently:
   ```
   git show --stat 838a523
   git log -1 --format=%B 838a523
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   a44d912afd81fa757d2b9893e77a0d8990bce05ed78ad487352d3e849efe9143` /
   `Workflow-Work-Item: workflow-v2-1-core`.
7. **Ledger coverage still holds end to end.** The broadest single
   "everything is still wired together" signal this item has:
   ```
   python3 -c "
   import sys; sys.path.insert(0, 'scripts')
   import workflow_state as ws
   print(ws.verify_wfo_ledger_coverage('.', 'HEAD'))
   "
   ```
   (run from the repo root — `repo_root` must be the real repository path,
   not a relative `..` from inside `scripts/`, or the internal evidence-
   clone step fails closed with a `git clone` error). Expected:
   `{'status': 'PASS', ...}`, all 211 reconciliation-table items accounted
   for — matches `TEST_RESULTS.md`'s own re-run of this check (~52.5s).

### Expected result

All seven checks above pass exactly as described. Checks 1-5 and 7 are
read-only (safe to repeat freely); check 6 inspects a commit that already
exists in history rather than creating one.

### Known limitations / out of scope for this review

- This is **not** a re-walk of all 18 checkpoints' own original
  functional behavior — revision 4's checklist above already covered
  `WF4a-i`'s fix in detail, and no separate functional checkpoint exists
  for the 13 rounds between revision 4 and 17 (see "Context" above). A
  reviewer wanting deeper coverage of a specific checkpoint should read
  `IMPLEMENTATION_SUMMARY.md`'s per-checkpoint sections and re-run that
  checkpoint's own named test classes directly.
- Three further Optional-severity findings from round GPT-R137 remain
  open but are explicitly not approval gates (see
  `.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md`'s summary):
  an untracked symlink-to-directory is still skipped by the cleanliness
  walk; the omitted-id path propagates raw `FileNotFoundError`/
  `JSONDecodeError` for missing/malformed state instead of a typed
  mismatch result; and the no-replacement-object correction has no
  dedicated regression test despite being behaviorally observable.
- `workflow_state_demo_test`/`workflow_fingerprint_demo_test` (the two
  real-repository demo modules) are deliberately excluded from this
  item's declared six-module suite (documented rationale unchanged since
  `GPT-R9-002`) — not re-run as part of this checklist.

Findings go in `.ai-review/workflow-v2-1-core/feedback/FUNCTIONAL_REVIEW.md`.

---

## `v2-1-dry-run` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core` section above it. It is the synthetic
`v2-1-dry-run` work item's **own** functional-review checklist — evidence
for `WF8b`'s S9 scenario (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`),
proving `/prepare-functional-review`'s real checklist-writing path for a
`"2.1"`-governed, state-tracked work item. `v2-1-dry-run` has no product
surface of its own: its "implementation" is three trivial scratch-file
checkpoints (`S-CP1`/`S-CP2`/`S-CP3`,
`docs/ai-workflow/registry/v2-1-dry-run-registry.json`), created solely to
exercise the real checkpoint/approval/review command surface end to end.

**Context**: `technical_approval` for `v2-1-dry-run` implementation
revision 4 is now recorded (`basis: USER_OVERRIDE`, commit `7c1032c`,
`review_content_id`
`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96`),
landed via a real `/approve-review implementation v2-1-dry-run` invocation
(commit `8b72452`) after S9's provenance-gate remediation (see
`docs/ai-workflow/dry-run/WF8B_S9_FINDING_provenance_gate_never_implemented.md`).
`phase` transitioned to `AWAITING_FUNCTIONAL_REVIEW` as part of that same
approval. Per `WF8B_SCENARIOS.md`'s own reordering note, S10 (a
functional-review finding + `/apply-functional-review` bounded
remediation) is exercised next, ahead of S9's final `/accept-milestone`
step.

### Setup

No Android app / Gradle changes are involved. Process tooling only. No
build/install step is needed; everything below runs with `python3` from
the repo root.

### Automated verification (re-confirmed this session, current)

Re-run independently immediately before this checklist was written (no
source/test file has changed since `technical_approval`'s
`reviewed_content_commit`, `7c1032c` — only metadata-only commits since):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 60/60 |
| `scripts/workflow_state_test.py` | 408/408 |
| `scripts/workflow_test_harness_test.py` | 19/19 |
| `scripts/workflow_integration_test.py` | 46/47 |

The one `workflow_integration_test.py` failure is the same pre-existing,
unrelated WFR-row-count staleness already documented across this dry
run's own outcome notes (a static assertion on
`docs/ai-workflow/WORKFLOW_V2_PLAN.md`'s requirements-table row count,
last synced at a much earlier plan revision; not a functional regression
and not in scope for this review).

### Test data

None to seed — this checks the repository's own real, already-committed
state (`v2-1-dry-run`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, and the three scratch files its
checkpoints created).

### Flows to exercise manually

1. **Scratch checkpoints exist as committed.** Confirm
   `docs/ai-workflow/dry-run/scratch/a.txt` and `scratch/b.txt` exist and
   are tracked (`git ls-files` shows both); `scratch/c.txt` exists but is
   deliberately **not** tracked yet — it is live S13-S15 dirty-worktree
   fixture state, not a defect.
2. **`v2-1-dry-run`'s own state is internally consistent.** Confirm
   `docs/ai-workflow/WORKFLOW_STATE.json`'s `work_items["v2-1-dry-run"]`
   shows `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `last_completed_checkpoint_id: "S-CP3"`, and `technical_approval.status:
   "CURRENT"` with `reviewed_content_commit: "7c1032c..."`.
3. **The real command surface, not just the hermetic fixtures, drove this
   round.** Confirm `8b72452` (the technical-approval commit) carries a
   `Workflow-Technical-Approval` trailer and a `Workflow-Work-Item:
   v2-1-dry-run` trailer, and that `bc0770b` (this outcome's own
   documentation commit) is metadata-only (`docs/` only).
4. **Re-run the automated suite yourself** (table above) and confirm the
   same result independently, rather than trusting this document's claim
   alone.

### Expected result

All four checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- `v2-1-dry-run` has no product surface — this review exercises process
  tooling only, per `WF8b`'s own purpose.
- `WF8b` itself is **not** complete: S10 through S17 remain unexecuted as
  of this checklist. This review covers only S9's implementation-approval
  and functional-review-preparation halves.
- `scratch/c.txt` and `docs/ai-workflow/dry-run/verify_review_content_id.py`
  are deliberately left uncommitted — do not commit or clean them up; they
  are live fixture state for the not-yet-run S13/S14/S15 scenarios.

Findings go in `.ai-review/v2-1-dry-run/feedback/FUNCTIONAL_REVIEW.md`.

---

## `v2-1-dry-run` — functional review checklist (implementation revision 6)

This section supersedes the implementation-revision-4 section immediately
above for review purposes — that section is left in place as historical
evidence for `WF8b`'s S9 scenario, not edited. This section is the same
synthetic `v2-1-dry-run` work item's checklist for `WF8b`'s **S10** scenario
(`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`): a real functional-review
finding was filed against revision 4's content, `/apply-functional-review`'s
bounded-remediation branch fixed it, and a fresh implementation-review round
plus a repeat, user-gated `/approve-review implementation v2-1-dry-run`
carried `technical_approval` back to `CURRENT` at implementation revision 6.

**Context**: `docs/ai-workflow/dry-run/scratch/b.txt` (`S-CP2`'s marker) was
deliberately corrupted to "checkpoint 1" (commit `3d2f3fd`) to give S10's
functional-review finding real content to point at. Per
`D-Functional-Remediation`'s bounded branch:
`workflow_state.mark_technical_approval_stale` flipped
`technical_approval.status` to `STALE` *before* the fix landed (verified in
the fix commit `fae7420` itself); the fix restored `scratch/b.txt` to
"checkpoint 2", byte-identical to its original completion commit `8375b64`;
a post-fix bundle was regenerated (`implementation_revision` `4` → `5`,
`review_content_id` unchanged at
`33139aaf7e637fc32dfd86a31f73c6153e32d2dc0a4ff8c4fa46ee2afe131a96` — the
fix restored, not changed, protected content). A fresh implementation-review
round then returned a clean `APPROVE` (zero findings) against round 5's
bundle; a provenance-gap-closure round 6 (`record_bundle_generation`, commit
`c11ec01`) advanced `reviewed_implementation_head` to `c916ead` /
`implementation_revision` to `6` with no content change, closing the same
`WF8B-003`-pattern gap S9 already documented once. The user then
re-invoked `/approve-review implementation v2-1-dry-run` for real:
`technical_approval` is now `status: CURRENT`, `basis: USER_OVERRIDE`,
`reviewed_content_commit: c916ead`, recorded by the metadata-only approval
commit `9fd3c72`. `phase` remains `AWAITING_FUNCTIONAL_REVIEW` throughout
(nothing downstream reads `phase` to decide this gate, per S7's own
established finding).

### Setup

No Android app / Gradle changes are involved. Process tooling only. No
build/install step is needed; everything below runs with `python3` from
the repo root.

### Automated verification (re-confirmed this session, current)

Re-run independently immediately before this checklist section was written
(no source/test file has changed since `technical_approval`'s
`reviewed_content_commit`, `c916ead` — only metadata-only commits since):

| Suite | Tests |
|---|---|
| `scripts/workflow_fingerprint_test.py` | 122/122 |
| `scripts/workflow_fingerprint_generalization_test.py` | 60/60 |
| `scripts/workflow_state_test.py` | 408/408 |
| `scripts/workflow_test_harness_test.py` | 19/19 |
| `scripts/workflow_integration_test.py` | 46/47 |

The one `workflow_integration_test.py` failure is the same pre-existing,
unrelated WFR-row-count staleness already documented across this dry run's
own outcome notes (`test_every_wfr_row_description_matches_json_exactly`:
67 actual table rows vs. an assertion still pegged at 60 from a much
earlier plan revision) — not a functional regression and not in scope for
this review.

### Test data

None to seed — this checks the repository's own real, already-committed
state (`v2-1-dry-run`'s own entry in `docs/ai-workflow/WORKFLOW_STATE.json`,
and the three scratch files its checkpoints created).

### Flows to exercise manually

1. **Scratch checkpoints exist as committed, all three, all correct.**
   Confirm `docs/ai-workflow/dry-run/scratch/a.txt`, `scratch/b.txt`, and
   `scratch/c.txt` all exist and are tracked (`git ls-files` shows all
   three — unlike the implementation-revision-4 checklist above, `c.txt`
   was already tracked by this point, via `S-CP3`'s own completion commit
   `8b70136`, well before S7/S8; that earlier section's "not tracked yet"
   note describes an even-earlier point in the dry run's own timeline, not
   this one). Confirm their content reads "checkpoint 1", "checkpoint 2",
   "checkpoint 3" respectively — `scratch/b.txt` in particular, since it is
   the file S10's finding and fix both touched.
2. **`v2-1-dry-run`'s own state is internally consistent.** Confirm
   `docs/ai-workflow/WORKFLOW_STATE.json`'s `work_items["v2-1-dry-run"]`
   shows `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `last_completed_checkpoint_id: "S-CP3"`,
   `implementation_revision: 6`, and `technical_approval.status: "CURRENT"`
   with `reviewed_content_commit: "c916ead..."`.
3. **The stale-before-edit ordering actually happened.** Confirm commit
   `fae7420` (`git show fae7420:docs/ai-workflow/WORKFLOW_STATE.json`)
   itself carries `technical_approval.status: "STALE"` for `v2-1-dry-run` —
   the intermediate state, not just today's end state — alongside the
   `scratch/b.txt` fix, in the same commit.
4. **The real command surface, not just the hermetic fixtures, drove the
   repeat approval.** Confirm `9fd3c72` (the second technical-approval
   commit) carries a `Workflow-Technical-Approval` trailer and a
   `Workflow-Work-Item: v2-1-dry-run` trailer, and that `c11ec01`
   (round 6's provenance-gap-closure commit) carries a
   `Workflow-Bundle-Generation-Record: v2-1-dry-run/6` trailer.
5. **Re-run the automated suite yourself** (table above) and confirm the
   same result independently, rather than trusting this document's claim
   alone.

### Expected result

All five checks above pass exactly as described; no step requires any
write to the real repository (all read-only), so this review can be
repeated freely.

### Known limitations / out of scope for this review

- `v2-1-dry-run` has no product surface — this review exercises process
  tooling only, per `WF8b`'s own purpose.
- `WF8b` itself is **not** complete: S11 through S17 remain unexecuted as
  of this checklist. This review covers S9's still-deferred
  functional-review-preparation half (this section) and S10's bounded
  remediation, not the final `/accept-milestone` step.
- `docs/ai-workflow/dry-run/verify_review_content_id.py` remains
  deliberately uncommitted — do not commit or clean it up; it answers a
  read-only review question (`GPT-DRY-R1-002`) and is not itself dry-run
  scratch fixture state.

Findings go in `.ai-review/v2-1-dry-run/feedback/FUNCTIONAL_REVIEW.md`.

### `v2-1-dry-run` — MILESTONE_COMPLETE (S16, real, 2026-08-15)

The user gave literal `/accept-milestone`-stage confirmation for
`v2-1-dry-run` ("I confirm acceptance of v2-1-dry-run."), validated for
real by `workflow_state.validate_user_confirmation`. The advisory
terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True`) and the authoritative
`workflow_state.complete_work_item` call both succeeded — no
`IncompleteChildWorkItemError` (no remediation child of `v2-1-dry-run`
exists) and no `IncompleteOwnCheckpointsError` (`S-CP1`-`S-CP3` all
`COMPLETE`), proving S16's own pass/fail evidence exactly.
`work_items["v2-1-dry-run"].phase` is now `MILESTONE_COMPLETE`;
`active_work_item_id` reset to `null` (it pointed here).

**A genuine gap in `/accept-milestone`'s steps 3/5, recorded rather than
worked around**: those steps ("update `docs/ROADMAP.md` to mark the
milestone complete"; "archive this milestone's execution/reference plans
to `docs/milestones/completed/`") are written for a real product/process
milestone with a `ROADMAP.md` entry and a top-level "Active plan" section
in this file. `v2-1-dry-run` has neither — `grep` confirms zero mentions
of it (or of `workflow-v2-1-core`) anywhere in `docs/ROADMAP.md`, and its
own plan doc (`docs/ai-workflow/dry-run/v2-1-dry-run-plan.md`) was never
linked from this file's top "Active plan" section, which belongs to
Milestone 8 alone. Steps 3 and 5 were therefore **not** performed:
step 3 has no entry to mark, and step 5 would misfile a throwaway
synthetic item's dry-run plan into the real completed-milestones
archive, contradicting `WF8B_SCENARIOS.md`'s own "Isolation" discipline
("The real `workflow-v2-1-core` record is untouched by any scenario
here"). Step 4 is honored narrowly and correctly instead: this note *is*
that section's move into a factual "complete" state — there was never a
top-level "Active plan" entry for this item to clear. Step 7 ("Next
action") is unaffected: it already correctly points at the real
`docs/ROADMAP.md`, which `v2-1-dry-run` was never part of. This is filed
as an observation for `workflow-v2-1-core`'s own eventual, real
`/accept-milestone` invocation to account for (it likely faces the same
question, since it also has no `ROADMAP.md` entry), not as a blocking
defect — nothing about it left real repository state incorrect.

Not yet run: **S12** (approval/content-drift detection) and **S17**
(pointer restoration — `active_work_item_id` back to
`workflow-v2-1-core`, dry-run cleanup). `S13`-`S15` already ran earlier
in this dry run, interleaved with checkpoint implementation. **S11**
(legacy import/adoption) ran for real immediately below.

## `v2-1-dry-run-legacy` — functional review checklist (legacy adoption, S11)

This is a **throwaway synthetic item** created solely to exercise `D-Legacy`
phase 2 (`WF-M8b`)'s adoption transition for real
(`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`'s S11). It has no product
surface and no real implementation content — its `technical_approval`
(`basis: LEGACY_V1`) is fabricated evidence, not a real review, per S11's
own locked-in design decision. This item never adopts, reopens, or changes
`milestone-8`'s entry, which remains untouched throughout.

Per S11's own text, a full acceptance cycle for this item is optional and
is abbreviated here: the pass condition is the `LEGACY_READY` →
`AWAITING_FUNCTIONAL_REVIEW` transition itself
(`workflow_state.promote_legacy_work_item`, run for real), not a genuine
functional-review pass.

### Setup

None — no Android app / Gradle involvement, no build/install step.

### Automated verification

Not applicable. This item never ran `/milestone-implement` or
`/apply-implementation-review` — its `technical_approval` was established
directly by legacy import (`import_legacy_work_item`), not by that
pipeline, so there is no implementation-stage test suite scoped to it to
re-confirm.

### Test data

None to seed — this checks the repository's own real, already-committed
`docs/ai-workflow/WORKFLOW_STATE.json` state for this item.

### Flows to exercise manually

1. **Adoption transitioned the right fields, nothing else.** Confirm
   `work_items["v2-1-dry-run-legacy"]` now shows
   `phase: "AWAITING_FUNCTIONAL_REVIEW"`,
   `governing_workflow_version: "2.1"`, and `active_work_item_id` ==
   `"v2-1-dry-run-legacy"`; confirm `technical_approval` is byte-identical
   to its state immediately after import (`basis: "LEGACY_V1"`,
   `reviewed_content_commit`/`approved_review_content_id` unchanged).
2. **Milestone 8 is untouched.** Confirm `work_items["milestone-8"]` is
   byte-identical to its state before this scenario ran — same
   `governing_workflow_version: "1"`, same `phase: "LEGACY_READY"`... no,
   `phase` for `milestone-8` is not `LEGACY_READY` (it was already
   promoted/accepted separately) — confirm whatever its actual current
   phase is remains unchanged by this scenario, since adoption ran only
   against `v2-1-dry-run-legacy`'s own resolved target.

### Expected result

Both checks above pass; no step requires any write beyond the one
adoption transition already performed (real, not simulated) and this
checklist section itself.

### Known limitations / out of scope for this review

- This item is fabricated evidence for a workflow-mechanism dry run, not a
  real product milestone — no functional flows exist to exercise beyond
  the state-transition checks above.
- **Discovered edge case, recorded rather than worked around**: this
  item's `implementation_revision` is `null` (a `LEGACY_V1`-imported item
  never passes through the ordinary `PLANNING`→`IMPLEMENTING` cycle that
  sets it). `/prepare-functional-review`'s step 3a reads
  `implementation_revision` live and folds it into both the
  `discover_current_functional_checklist_evidence` round-scope prefix and
  the `Workflow-Functional-Checklist` commit trailer; for this item that
  produces the literal prefix `v2-1-dry-run-legacy/None/` — mechanically
  functional (verified: `discover_current_functional_checklist_evidence`
  returns `None` cleanly with no exception when queried with
  `implementation_revision=None`, and its own round-scoped-prefix
  contract is symmetric enough that a repeat lookup with the same `None`
  value stays self-consistent), but semantically wrong for any
  `LEGACY_V1` item's evidence trailer. Not a blocker for S11's own pass/fail
  evidence (the adoption transition itself, checked above), and not
  fixed ad hoc here — filed for the same remediation routing S11's other
  discovered gap uses.

---

## `workflow-v2-3` — functional review checklist (implementation revision 4)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core`/`v2-1-dry-run`/`v2-1-dry-run-legacy` sections
above it. It tracks the separate, non-product process work item
`workflow-v2-3` (see `docs/ai-workflow/WORKFLOW_V2_3_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`), entering
`AWAITING_FUNCTIONAL_REVIEW` for the first time.

**Context**: `workflow-v2-3` adds two optional, report-only,
model-independent "second opinion" slash commands —
`/review-implementation` (usable at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`)
and `/review-functional` (usable at `AWAITING_FUNCTIONAL_REVIEW`, i.e. the
gate this checklist itself belongs to) — that give an operator a
repository-local advisory review before handing a stage to its real gate.
Neither command writes any state (`WORKFLOW_STATE.json`,
`REVIEW_FEEDBACK.md`, `FUNCTIONAL_REVIEW.md`, this file), approves a
stage, applies a finding, or advances `phase`; the hard-gate count stays
exactly 6 (`docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard gates
summary"). Two checkpoints (`CP1`, `CP2`), both `COMPLETE` — no
continued-scope remediation round is open, so this item's eventual
acceptance goes through `/accept-milestone`, not
`/accept-scoped-remediation`.

Four implementation-review rounds ran before technical approval:
`GPT-IR1` (`REVISE`, 2 Important — both fixed), a second round whose
authoritative feedback was `/review-implementation`'s **own advisory
report, hand-copied for real** by the operator to
`.ai-review/feedback/REVIEW_FEEDBACK.md` (`RI2`, `REVISE`, 2 Important/3
Optional — one Important deferred with recorded reasoning, the rest
fixed), a third round using the same hand-copy mechanism (`RI3`,
`APPROVE`, 4 Optional, all fixed), and a final post-fix round (`APPROVE`,
0 Blocking/0 Important/2 Optional). `technical_approval` is now recorded
(commit `08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5`,
`Workflow-Technical-Approval:
b4ec046e8a2acc7144135a242c96b2d609a3a144ed844e7493cb04d3e4b8a7a9`, basis
`EXTERNAL_APPROVE`, generation-record commit
`096052650d6247b7c9ecdde4a35b0f0f54ddf7cf`).

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since the last full run at the round-4 generation-record
commit `0960526`, via the metadata-only technical-approval commit
`08c87ab`):

```
python3 -m unittest workflow_fingerprint_test workflow_fingerprint_generalization_test \
  workflow_state_test workflow_state_completion_obligations_test \
  workflow_integration_test workflow_test_harness_test
```

Result: **1115 tests, all green** (`OK`), 27.8s — matches
`TEST_RESULTS.md`'s round-4 total exactly.

```
python3 workflow_fingerprint_demo_test.py   # real-repository suite
python3 workflow_state_demo_test.py         # real-repository suite
```

Result: **15/15 green** (4 skipped, pre-existing/unrelated) and **45/45
green** respectively — both matching `TEST_RESULTS.md`'s round-4 result
exactly.

### Test data

None to seed — every check below reads this repository's own real,
already-committed state (`workflow-v2-3`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, the two new command files, and
this milestone's own real review history).

### Flows to exercise manually

1. **Re-run the automated suite yourself** (commands above, from
   `scripts/`) and confirm the same 1115/15/45 result independently,
   rather than trusting this document's claim alone.
2. **`/review-functional` — run it live, right now, against this exact
   checklist.** From the repo root, invoke `/review-functional
   workflow-v2-3` (or with no argument, since `active_work_item_id` is
   currently `workflow-v2-3`). Expected: a printed
   checklist-completeness/evidence-reproducibility advisory report (never
   a `Status: APPROVE | REVISE | BLOCK` verdict), which independently
   re-runs the automated-verification commands cited above and assesses
   manual-flow coverage against the CP1/CP2 diff. Confirm afterward, via
   `git status --porcelain`, that `docs/ai-workflow/WORKFLOW_STATE.json`,
   `.ai-review/feedback/REVIEW_FEEDBACK.md`,
   `.ai-review/feedback/FUNCTIONAL_REVIEW.md`, and this file are all
   byte-identical to before the command ran — the command's own
   write-nothing guarantee, exercised for real rather than taken on
   faith.
3. **`/review-implementation` — already exercised for real, twice, during
   this very milestone.** Unlike `/review-functional` above, no work item
   currently sits at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` to
   re-demonstrate it live against today. Instead, confirm the historical
   record: `IMPLEMENTATION_SUMMARY.md`'s "Review round 2 remediation" and
   "Review round 3 remediation" sections both state that round's
   authoritative feedback was `/review-implementation`'s own advisory
   report, hand-copied by the operator into
   `.ai-review/feedback/REVIEW_FEEDBACK.md` — i.e. this milestone's own
   `RI2`/`RI3` review rounds were real, substantive uses of the exact
   command being functionally reviewed here, not a simulation.
4. **Command surface is fully registered.** Confirm `.claude/commands/`
   contains exactly 16 files and `CLAUDE.md`'s "Slash commands" list names
   all 16, including `review-implementation` and `review-functional`
   (`grep -c '^' <(ls .claude/commands/*.md)` and a visual diff against
   `CLAUDE.md:85-86`).
5. **Hard-gate count is unchanged.** Confirm
   `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard gates summary" still
   lists exactly six gates and explicitly states that
   `/review-implementation`/`/review-functional` add no new one.
6. **The real `/approve-review implementation` flow, exercised for real
   this session**: gate-reachability check (worktree/HEAD match,
   `REJECTED`-marker absence, `bundle_id`/`review_content_id`
   recomputation, provenance-interval verification), `resolve_approval_basis`
   → `EXTERNAL_APPROVE`, a `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`08c87ab`) carrying
   the `Workflow-Technical-Approval`/`Workflow-Work-Item` trailers.
   Confirm independently:
   ```
   git show --stat 08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5
   git log -1 --format=%B 08c87ab41f75aeda8bf9cee4f8b72dcd4e6dbcd5
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   b4ec046e8a2acc7144135a242c96b2d609a3a144ed844e7493cb04d3e4b8a7a9` /
   `Workflow-Work-Item: workflow-v2-3`.

### Expected result

All six checks above pass exactly as described. Checks 1, 3, 4, 5, and 6
are read-only or inspect commits that already exist (safe to repeat
freely); check 2 runs a report-only command and then verifies, rather
than assumes, that it wrote nothing.

### Known limitations / out of scope for this review

- No live re-demonstration of `/review-implementation` against a work
  item actually sitting at `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`
  exists today (see check 3) — this milestone's own two real uses of it
  during rounds 2/3 are the available evidence instead.
- The plan-conformance gap in `/review-implementation` (no read of
  `<bundle_dir>/PLAN.md`/the item's `plan_path`) is a known, already-filed
  limitation (`docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md` item 4;
  `IMPLEMENTATION_SUMMARY.md`'s `RI2-002`) — not a defect introduced by
  this diff, and not blocking for this functional review.
- `docs/ai-workflow/WORKFLOW_V2_1_OPERATOR_REFERENCE.md`,
  `docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md`, and
  `docs/ai-workflow/diagrams/` are untracked, concurrent, out-of-scope
  scratch content — explicitly classified `excluded` in this item's own
  `implementation_stage` artifacts declaration; leave them unmodified
  during this review.
- Both new commands are advisory only and cannot replace this checklist's
  own manual walkthrough; `/review-functional` states this explicitly in
  its own printed report.

Findings go in `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.

### `workflow-v2-3` — MILESTONE_COMPLETE (real, 2026-08-23)

The user gave literal `/accept-milestone`-stage confirmation for
`workflow-v2-3` ("I confirm acceptance of workflow-v2-3."), validated for
real by `workflow_state.validate_user_confirmation`, after confirming they
had walked the functional-review checklist above. No finding was filed —
`.ai-review/feedback/FUNCTIONAL_REVIEW.md` was never created, unlike
`workflow-v2-1-core`'s own acceptance (which did have a written `Result:
PASS` record); this item's acceptance rests on the user's in-conversation
confirmation alone, per this command's own preamble, not on a separate
written artifact.

The advisory terminal-reachability pre-flight
(`resolve_own_registry_completion_status` → `is_terminal=True`,
`milestone_complete_gate_reachable` → `True` from phase
`AWAITING_FUNCTIONAL_REVIEW`) and the authoritative
`workflow_state.complete_work_item` call both succeeded: no
`IncompleteChildWorkItemError` (no work item declares `workflow-v2-3` as
its `parent_work_item_id`), no `IncompleteOwnCheckpointsError` (both
registry checkpoints, `CP1`/`CP2`, `COMPLETE`), and no
`UnsatisfiedCompletionObligationError` (`workflow-v2-3-registry.json`
declares no completion obligations — `resolve_completion_obligations`
returns `{}`, vacuously satisfied, same as `v2-1-dry-run`'s own
acceptance). `work_items["workflow-v2-3"].phase` is now
`MILESTONE_COMPLETE`; `active_work_item_id` resets to `null` (it pointed
here).

**Steps 3/5 do not apply** — the same gap `workflow-v2-1-core`'s and
`v2-1-dry-run`'s own completions already documented: `grep` confirms zero
mentions of `workflow-v2-3` anywhere in `docs/ROADMAP.md` (step 3 has no
entry to mark). `docs/ai-workflow/WORKFLOW_V2_3_PLAN.md` is **not**
archived to `docs/milestones/completed/` (step 5) — that directory holds
only real product-milestone execution/reference plan pairs (milestones
1-8); a process work item's own plan document was never the kind of
artifact it exists to hold, and `WORKFLOW_V2_3_PLAN.md` is referenced only
from this file's own `workflow-v2-3` section and this item's registry/
artifacts declarations, not linked as ongoing operator-facing guidance the
way `WORKFLOW_V2_PLAN.md` is. Step 4 is honored via this note itself,
placed in this item's own section rather than the top-level "Active plan"
section, which belongs to Milestone 8 alone and is untouched by this
completion. Step 7 ("Next action") is unaffected for the same reason both
prior completions' was: it already correctly points at the real
`docs/ROADMAP.md`, which `workflow-v2-3` was never part of, and explicitly
defers the next roadmap milestone (the Figma-led redesign) until the user
initiates it.

---

## `workflow-v2-3-followups` — functional review checklist (implementation revision 1)

This section is unrelated to the roadmap/Milestone 8 content above and to
the `workflow-v2-1-core`/`v2-1-dry-run`/`v2-1-dry-run-legacy`/`workflow-v2-3`
sections above it. It tracks the separate, non-product process work item
`workflow-v2-3-followups` (see
`docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS_PLAN.md`,
`docs/ai-workflow/WORKFLOW_STATE.json`), entering `AWAITING_FUNCTIONAL_REVIEW`
for the first time.

**Context**: a focused maintenance milestone closing three deferred
workflow-tooling defects recorded in `docs/ai-workflow/WORKFLOW_V2_3_FOLLOWUPS.md`
after `workflow-v2-3` reached `MILESTONE_COMPLETE`, plus one explicitly
opportunistic fourth item. No `app/` code is touched at all — every change
lands in `.claude/commands/`, `docs/ai-workflow/`, and `scripts/`. Four
registry checkpoints (`CP1`-`CP4`), all `COMPLETE`, one commit each
(`570adc2`, `bac9237`, `5442ea6`, `5b5b9c7`), plus one self-review fix commit
(`994608f`) and this round's bundle-generation record (`54befc7`).

One external implementation-review round: `.ai-review/feedback/REVIEW_FEEDBACK.md`
returned `Status: APPROVE` (0 Blocking, 0 Important, 5 Optional — O1
through O5, all recorded as accepted trade-offs or documentation
observations, none a required acceptance criterion).
`technical_approval` was just recorded this session (commit `5ab2c99`,
`Workflow-Technical-Approval:
e8a4a76420995dc9eca3bd015267de890b32dc5d75684bd379833440816f2435`, basis
`EXTERNAL_APPROVE`).

### Setup

No Android app / Gradle changes are involved — this is process tooling
only (`scripts/*.py`, `.claude/commands/*.md`, `docs/ai-workflow/*`). No
build/install step is needed; everything below runs with `python3` from
the repo root (`scripts/` for the checks that `cd` there).

### Automated verification (re-confirmed this session, current)

Re-run independently, live, right before this checklist was written (only
`docs/ai-workflow/WORKFLOW_STATE.json` — implementation-stage excluded —
has changed since `technical_approval.reviewed_content_commit`, `994608f`,
via the bundle-generation-record commit `54befc7` and this session's own
metadata-only approval commit `5ab2c99`; `git log 994608f..HEAD -- .
':!docs/ai-workflow/WORKFLOW_STATE.json'` is empty):

```
python3 -m unittest workflow_fingerprint_test workflow_fingerprint_generalization_test \
  workflow_state_test workflow_state_completion_obligations_test \
  workflow_integration_test workflow_test_harness_test
```

Result: **1146 tests, OK**, 27.973s.

```
python3 -m unittest workflow_state_demo_test         # 45/45, OK
python3 -m unittest workflow_fingerprint_demo_test    # 15/15, OK (skipped=4)
```

Total **1206**, zero failures/errors beyond the 4 pre-existing/unrelated
skips — matches `TEST_RESULTS.md`'s recorded result exactly.

### Test data

None to seed — every check below reads this repository's own real,
already-committed state (`workflow-v2-3-followups`'s own entry in
`docs/ai-workflow/WORKFLOW_STATE.json`, the changed command/doc/script
files, and this milestone's own real review history).

### Flows to exercise manually

1. **Re-run the automated suite yourself** (commands above, from
   `scripts/`) and confirm the same 1146/45/15 result independently,
   rather than trusting this document's claim alone.
2. **CP1's merged staging-and-pin fix.** No live plan-stage approval with
   a fresh conditional fifth member (`<work_item>-artifacts.json`, both
   pending and fresh) is available to re-demonstrate right now — this
   item's own plan-stage approval already consumed the one naturally
   occurring opportunity, reproducing the pre-fix bug live before CP1
   landed (`IMPLEMENTATION_SUMMARY.md`'s CP1 section: "This work item's own
   plan-stage approval reproduced it live."). Confirm the fix instead
   through its own non-mocked regression coverage, run from `scripts/`:
   ```
   python3 -m unittest workflow_integration_test.TestPlanApprovalPermanentSiteEndToEnd.test_five_member_fixture_succeeds_through_the_real_merged_step_5
   ```
   Expected: `OK` (re-confirmed live for this checklist). The test drives
   a genuine five-member fixture through the real primitives to a
   committed, materialized, journal-closed outcome, not a mock.
3. **CP1's trailer final-paragraph requirement, stated in all three
   places it should be.** Confirm:
   ```
   grep -ln "OPUS-R129-001" .claude/commands/approve-review.md \
     .claude/commands/milestone-implement.md .claude/commands/bootstrap-workflow-v2.md
   ```
   Expected: all three files listed (the milestone added the sentence,
   citing this same marker, to `approve-review.md`; the other two already
   stated it citing the same marker). A plain `grep "final paragraph"`
   under-matches two of the three files, since the phrase wraps across a
   line break there — use the marker, not the phrase.
4. **CP2's real, live deliverable — the exact review that authorized this
   round.** Read `.ai-review/feedback/REVIEW_FEEDBACK.md` directly: confirm
   its `Status: APPROVE`, `Reviewed bundle ID:`, `Reviewed base commit:`,
   and `Work item:` fields, and that its "Independent verification
   performed" section documents real re-execution (fresh `bundle_id`/
   `review_content_id` recomputation, a full independent test re-run) —
   this file is itself `/review-implementation`'s own write for this
   round, not a simulation. No live cross-work-item ownership collision
   exists today to re-trigger the new `FeedbackOwnedByOtherWorkItemError`
   guard; its regression coverage
   (`test_different_work_item_refuses_naming_both`,
   `test_refused_run_for_b_leaves_as_own_feedback_byte_identical_and_creates_no_scoped_dir`,
   `test_a_may_overwrite_its_own_feedback_at_the_same_flat_path`) is the
   available evidence instead.
5. **CP3's canonical casing, live in this exact item's own record.**
   Confirm `docs/ai-workflow/WORKFLOW_STATE.json`'s
   `work_items["workflow-v2-3-followups"].plan_review_stages` shows
   `LOCAL_MODEL_PLAN_REVIEW` and `MANUAL_EXTERNAL_PLAN_REVIEW` as the
   literal keys (canonical `SCREAMING_SNAKE_CASE`, not the old lowercase
   tokens) — this is real live state the migration/normalization produced,
   not a fixture.
6. **CP4's cross-cutting sweep, re-run yourself.** Confirm no scope creep:
   ```
   grep -rn "AWAITING_LOCAL_IMPLEMENTATION_REVIEW\|quorum\|Reviewer role:" \
     .claude/commands/ docs/ai-workflow/ scripts/*.py
   ```
   Expected: every hit is prose explicitly declining the concept, or
   pre-existing plan-review-stage vocabulary (`Reviewer role:` in
   `record-manual-plan-review.md`) — none introduces a new lifecycle state
   or reviewer-quorum mechanism. Then confirm the hard-gate count is
   still exactly six: `docs/ai-workflow/MILESTONE_WORKFLOW.md`'s "Hard
   gates summary" section.
7. **The self-review fix (`994608f`).** Read
   `docs/ai-workflow/REVIEW_PROTOCOL.md`'s "Local reviewer commands"
   section: confirm it now states `/review-functional`'s report has no
   authoritative round to become and is not destined for
   `REVIEW_FEEDBACK.md`, pointing the operator at revising
   `FUNCTIONAL_REVIEW.md` by hand instead — not the pre-fix wording that
   let this sentence read as true of `/review-functional` when it was
   only ever true of `/review-implementation`.
8. **The real `/approve-review implementation` flow, exercised for real
   this very session.** Gate-reachability check (worktree/HEAD match,
   `REJECTED`-marker absence, `bundle_id`/`review_content_id`
   recomputation matching `REVIEW_FEEDBACK.md` exactly, implementation
   provenance-interval verification), `resolve_approval_basis` →
   `EXTERNAL_APPROVE`, a `state_transaction`-guarded write of
   `technical_approval`, and a metadata-only commit (`5ab2c99`) carrying
   the trailers. Confirm independently:
   ```
   git show --stat 5ab2c99
   git log -1 --format=%B 5ab2c99
   ```
   Expected: exactly one file changed
   (`docs/ai-workflow/WORKFLOW_STATE.json`), and the trailers read
   `Workflow-Technical-Approval:
   e8a4a76420995dc9eca3bd015267de890b32dc5d75684bd379833440816f2435` /
   `Workflow-Work-Item: workflow-v2-3-followups`.
9. **Command surface is fully registered.** Confirm `.claude/commands/`
   contains exactly 16 files and `CLAUDE.md`'s "Slash commands" list names
   all 16.

### Expected result

All nine checks above pass exactly as described. Checks 1, 3, 5, 6, 7, 8,
and 9 are read-only or inspect commits/state that already exist (safe to
repeat freely); checks 2 and 4 point at the strongest available evidence
(a real non-mocked regression test; this round's own real review artifact)
where a fresh live re-demonstration of the exact original trigger is not
available today.

### Known limitations / out of scope for this review

- No live re-demonstration of CP1's five-member plan-stage-approval branch
  or CP2's cross-work-item ownership-guard collision exists today — see
  checks 2 and 4 above for the available evidence instead.
- Required follow-up #8 (operator reference / lifecycle diagram sync,
  `WORKFLOW_V2_3_FOLLOWUPS.md` item 2) remains deferred. The two artifacts
  involved — `docs/ai-workflow/WORKFLOW_V2_1_OPERATOR_REFERENCE.md` and
  `docs/ai-workflow/diagrams/` — are untracked working-tree leftovers,
  declared `excluded` in this item's own artifacts declaration and left
  unmodified throughout this review, per `CLAUDE.md`'s "don't touch
  unrelated working-tree changes".
- The five Optional findings in `REVIEW_FEEDBACK.md` (O1-O5) are
  non-blocking commentary, not defects requiring a fix before functional
  review; none is a required acceptance criterion for this round. O1 in
  particular (the implementation-review gate's now-self-satisfiable
  character) was explicitly considered and accepted at plan stage
  (`LPR-R2-B01`).

Findings go in `.ai-review/feedback/FUNCTIONAL_REVIEW.md`.
