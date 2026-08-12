# WF8b finding — `/approve-review plan`'s installed four-member commit set omits the required artifact declaration

**Status:** BLOCKING for WF8b, discovered by a real attempt; **generic fix
implemented and hermetically tested in the same investigation** (see
"Remediation" below). Not yet re-verified by a real `/approve-review plan
v2-1-dry-run` retry — that retry is user-only and intentionally not
performed by this session (see `WF8B_SCENARIOS.md`'s S5 section).

**Scenario:** S5 — Explicit user plan approval (`/approve-review plan
v2-1-dry-run`), attempted for real after the user supplied the required
literal confirmation text, against live repository state, real Git
history, and the then-installed `.claude/commands/approve-review.md`.

**Outcome of the attempt:** Steps 1–5 of the installed command (gate
reachability, fresh `bundle_id`/`review_content_id` recomputation,
worktree/HEAD staleness check, basis resolution, state write) all passed.
Step 6 created a commit containing the installed command's literal
four-member plan-stage set (plan doc, registry JSON, mapping JSON,
`WORKFLOW_STATE.json`). Step 6a's mandatory post-commit verification —
`workflow_state.verify_post_approval_manifest_match` — then raised.

## Reproduction

Commit created (four members, matching the installed command's pre-fix
step 6 text exactly):

```
docs/ai-workflow/WORKFLOW_STATE.json
docs/ai-workflow/dry-run/v2-1-dry-run-plan.md
docs/ai-workflow/registry/v2-1-dry-run-registry.json
docs/ai-workflow/requirements/v2-1-dry-run-mapping.json
```

Post-commit verification:

```
workflow_state.verify_post_approval_manifest_match(repo_root, work_item,
    stage="plan", base_commit=<base>, commit=<new commit>)
  -> workflow_fingerprint.MissingWorkItemArtifactsDeclarationError:
     docs/ai-workflow/registry/v2-1-dry-run-artifacts.json
```

The same failure independently reproduces with
`compute_review_content_id_plan_stage_at_commit_for_work_item(repo_root,
"v2-1-dry-run", <that commit>, base=<base>)` called directly — not an
artifact of the verification wrapper.

## Root cause

`docs/ai-workflow/registry/v2-1-dry-run-artifacts.json` had never been
committed at all — not at `base_commit`, not anywhere in history — only
ever present in the working tree (`git add -N` intent-to-add, per S1's own
documented staging step). `resolve_plan_stage_metadata`
(`scripts/workflow_fingerprint.py`) resolves this declaration **at the
commit being examined**, not from the live working tree, for any
commit-source call (`at_commit` not `None`):

```python
if not _path_exists_at_source(repo_root, artifacts_rel, at_commit):
    raise MissingWorkItemArtifactsDeclarationError(artifacts_rel)
```

The installed `.claude/commands/approve-review.md`'s (pre-fix) step 6
named exactly four plan-stage commit members and never included this
declaration. `D-Approval-Commits`' "Conditional fifth commit member"
contract (`GPT-R67-001`, `WORKFLOW_V2_PLAN.md` revision 50) already
specifies exactly this requirement — an artifact declaration that is
pending (differs from `HEAD`) and fresh (matches the reviewed bundle) must
join the commit — but that contract had only ever been implemented as a
one-off, self-contained "Bootstrap plan-approval procedure" scoped
explicitly to `workflow-v2-1-core`'s own approvals (`WORKFLOW_STATE.json`
being permanently `governing_workflow_version: "1"` for that item), never
generalized into the installed command or the shared library for any
other `"process"` work item. `v2-1-dry-run` is the first other such item
to reach a real `/approve-review plan` attempt, and reproduced exactly the
failure mode `D-Approval-Commits`' own text predicts for an unfixed
four-member commit: the approval-state write and the commit both already
durable, with no defined recovery.

The failure was **not silent**: step 6a's mandatory post-commit check
caught it immediately, exactly as designed, before the failure could ever
be reported as a successful approval.

## Confirmation: rollback left no partial state

- The commit was reset (`git reset HEAD~1`, undoing exactly that one
  commit).
- `docs/ai-workflow/WORKFLOW_STATE.json` was restored to its exact
  pre-attempt bytes (byte-diffed against a saved pre-write copy before
  restoring, not merely visually inspected).
- Final state re-verified: `v2-1-dry-run` at `phase:
  AWAITING_PLAN_APPROVAL`, `plan_approval: null`, `state_revision: 12` —
  identical to the state immediately after `/record-manual-plan-review`
  succeeded, one command earlier.
- No other file was touched by the failed attempt or its rollback.

## Remediation (generic, not scoped to `v2-1-dry-run`)

Implemented and hermetically tested in the same investigation, as ordinary
`workflow-v2-1-core` `WF8b` checkpoint implementation work under the
already-CURRENT Revision 62 plan approval (no new plan revision: the
design being implemented, `D-Approval-Commits`' "Conditional fifth commit
member," was already fully specified and approved through revisions
50–58; only its generalization from a one-off bootstrap into the shared
command/library was missing):

- `workflow_fingerprint.resolve_plan_stage_approval_commit_paths` —
  resolves the complete plan-stage approval-commit member set (four or
  five members) for **any** `"process"` work item, implementing the
  contract's pending-change and freshness conditions, called before any
  durable mutation.
- `workflow_state.stage_plan_approval_commit_paths` /
  `verify_staged_blob_sha256` / `verify_committed_blob_sha256` /
  `assert_committed_path_set_matches` / `rollback_plan_approval_write` —
  index-isolation precondition, pin-then-verify staging for the
  conditional member, a plain pathspec-free commit, a structural
  committed-path-set assertion (catches a hook editing the tree between
  staging verification and the commit itself), and a deterministic
  rollback for any failure from staging onward.
- `.claude/commands/approve-review.md` — new steps 4a/6b, and step 6/6a
  extended, wiring the above into the actual command text every future
  invocation (for any work item) follows.
- 12 new hermetic tests (`workflow_integration_test.py`,
  `TestPlanStageApprovalCommitMembership`), including the exact real
  defect's own fixture (proving the old four-member commit fails and the
  new five-member commit succeeds end to end), a positive four-member/
  non-applicable fixture, staleness, missing-declaration, unexpected-
  staged-path, wrong-content, rollback-before/after-commit, and a
  dedicated safety proof that the new index-isolation precondition does
  **not** reject this work item's own legitimate pending intent-to-add
  paths (the exact real-world shape that would otherwise have made this
  fix worse than the defect it closes). Full suite green except one
  pre-existing, unrelated failure (`test_every_wfr_row_description_matches_json_exactly`
  expects 60 WFR rows; the mapping already has 63 — untouched by this
  fix, flagged separately).

**Explicit scope boundary — this does not close missing-test item 347.**
Item 347's own text requires the permanent command to carry the *complete*
failure-atomicity transaction (a durable, crash-resumable journal; a
three-way `COMMITTED`/`NOT_COMMITTED`/`AMBIGUOUS` outcome classifier
sourced from durable Git state; index-pinned-blob materialization) before
`workflow-v2-1-core`'s own "Bootstrap plan-approval procedure" may be
retired. This remediation implements the contract's three numbered
conditions plus the specific hardening properties revision 54/56 named
(index isolation, pinned-blob verification, committed-path-set assertion,
pathspec-free commit, a defined rollback) — a real, substantial narrowing
of item 347's remaining scope, not its closure. The Bootstrap procedure
remains the sole sanctioned path for `workflow-v2-1-core`'s own future
plan-stage approvals.

## Next step

A fresh operator session may retry `/approve-review plan v2-1-dry-run`
directly — no ad hoc `git add`/`git commit` workaround, no
`v2-1-dry-run`-specific bootstrap procedure. This session does not execute
that retry itself: `/approve-review` is user-only by construction, and the
user's own instruction for this investigation was explicit that forcing
S5 through was out of scope.
