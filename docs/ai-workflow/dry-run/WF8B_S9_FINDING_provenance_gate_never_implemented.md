# WF8b finding — `/approve-review implementation`'s installed gate still used the bare-equality rule `WF8B-003` proved self-invalidating

**Status:** BLOCKING for WF8b, discovered by a real attempt; **generic fix
implemented and hermetically tested in the same investigation** (see
"Remediation" below). Not yet re-verified by a real `/approve-review
implementation v2-1-dry-run` retry at the time this file was written — that
retry is user-only and is the very next step after this remediation lands.

**Scenario:** S9 — Explicit user implementation approval (`/approve-review
implementation v2-1-dry-run`), attempted for real after the user invoked
`/approve-review implementation v2-1-dry-run` directly (satisfying the
command's own literal-confirmation-text guard), against live repository
state and the then-installed `.claude/commands/approve-review.md`.

**Outcome of the attempt:** Step 1's gate-reachability check was evaluated
by hand against the installed command's own documented rule before any
write was attempted (no state was mutated by this investigation). The
installed rule was exactly `work_item["reviewed_implementation_head"] ==
<live HEAD SHA>`. `approval_gate_reachable("REVISE")` was `True` and
`protected_path_dirty` was `False` (every changed/untracked path
classified `excluded`), but `reviewed_implementation_head` (`ae7ef4c...`,
set by S8's bundle regeneration) did not equal live HEAD (`34ce1dd...`,
one commit further — the docs commit recording S8's own outcome). The gate
was therefore unreachable; the attempt stopped here, before any write.

## Reproduction

```
work_item["reviewed_implementation_head"] = "ae7ef4c250a638fe358bd89d54bc3271bc401bd2"
live HEAD                                 = "34ce1dd3dc0316c5aac20124827905660058248d"
git log --oneline: 34ce1dd -> ae7ef4c -> b8d4899 -> 5ab830d -> 8b70136 -> ...
git show --stat 34ce1dd:
  docs/ai-workflow/WORKFLOW_STATE.json
  docs/ai-workflow/dry-run/WF8B_SCENARIOS.md
  docs/ai-workflow/requirements/v2-1-dry-run-ledger.md
-> all three classify "excluded" under v2-1-dry-run-artifacts.json's
   implementation-stage declaration (verified directly via
   workflow_fingerprint.classify_path_implementation_stage before
   concluding this was the sole blocker)
```

## Root cause

This is a live recurrence of `WF8B-003`
(`docs/ai-workflow/WORKFLOW_STATE.json`'s own `blocking_decisions` entry
for `workflow-v2-1-core`, first discovered investigating why round-8/round-9
durability commits (`20f89de`, `4d7b3cf`) immediately re-staled themselves).
That finding proved the pre-revision-28 `D-Approval-Commits` text
structurally self-invalidating: `record_bundle_generation`'s state write
must become a durable commit for fresh-session resumption, and that
durability commit is, by definition, one commit ahead of whatever
`reviewed_implementation_head` itself names — so a bare
`reviewed_implementation_head == HEAD` equality check is permanently
re-broken by the very next commit, on every round, forever.

`WORKFLOW_V2_PLAN.md`'s revision 28 (`WF8B-003`'s own disposition) already
designed and got externally approved a fix: splitting
`reviewed_implementation_head` from `generation_head`, moving the
durability commit to *before* generation (carrying a dedicated
`Workflow-Bundle-Generation-Record: <work_item_id>/<implementation_revision>`
trailer, touching only `WORKFLOW_STATE.json`), and relaxing
`/approve-review implementation`'s gate to a bounded, fully-enumerated
"provenance interval" check (`D-Commit-Provenance`, hardened through
revisions 29–38) — never a bare SHA comparison again. That design was
carried forward, referenced, and re-affirmed at every one of the plan's
subsequent ~50 revisions, through the currently-approved Revision 80.

**But it was never implemented in code.** A full grep of
`scripts/workflow_state.py`/`scripts/workflow_fingerprint.py` and the
three command files that generate or consume `reviewed_implementation_head`
found zero references to `Workflow-Bundle-Generation-Record` anywhere
outside the plan document itself. `record_bundle_generation` (`scripts/
workflow_state.py`) still only mutated `reviewed_implementation_head` in
memory for the caller to fold into whatever commit came next; the
installed `.claude/commands/approve-review.md` still documented the exact
bare-equality rule revision 29 (`GPT-R45-003`) explicitly removed from the
plan text. `WFR-61`/`WFR-62`'s owning checkpoints (`WF4c`, `WF5`) were
marked `COMPLETE` against an earlier plan revision, before `WF8B-003`
forced this redesign — and the plan's own row text for both requirements
explicitly defers the revision-28-through-38 missing-test items (215–313)
to `WF8b`, the checkpoint this dry run is executing. `v2-1-dry-run`'s S9 is
the first scenario in this whole work item's history to actually invoke
`/approve-review implementation` for real, so it is the first place this
gap could ever surface as a live blocker outside `workflow-v2-1-core`'s own
round-8/round-9 history.

The failure was **not silent**: the installed gate's own "unmet additional
condition, stop here" instruction was followed; no write was attempted.

## Remediation (generic, not scoped to `v2-1-dry-run`)

Implemented and hermetically tested in the same investigation, as ordinary
`workflow-v2-1-core` `WF8b` checkpoint implementation work under the
already-CURRENT Revision 80 plan approval (no new plan revision: the
design being implemented was already fully specified and approved through
revisions 28–38; only its implementation was missing):

- `scripts/workflow_state.py` — seven new exceptions
  (`AmbiguousBundleGenerationRecordTrailerError`,
  `BundleGenerationRecordNotFoundError`,
  `HeadPastBundleGenerationRecordError`,
  `ReviewedImplementationHeadNotAncestorError`,
  `NonFirstParentProvenanceIntervalError`,
  `ProtectedPathInProvenanceIntervalError`,
  `MalformedBundleGenerationRecordCommitError`);
  `discover_bundle_generation_record_commits`/
  `discover_current_bundle_generation_record_commit` (the
  `Workflow-Bundle-Generation-Record` trailer's own discovery, reusing the
  existing generic scoped-trailer-search helper exactly like every other
  D-Commit-Provenance trailer type); `validate_bundle_generation_record_commit`
  (the terminal commit's ordinary-role contract: touches only
  `WORKFLOW_STATE.json`, its own field changes are a non-empty subset of
  `{phase, reviewed_implementation_head, implementation_revision,
  state_revision, last_transition}`, exactly the two-trailer ordinary set);
  `verify_implementation_provenance_interval` (the full interval check:
  live HEAD equals the discovered terminal commit exactly, every commit
  strictly between `reviewed_implementation_head` and it is
  first-parent-enumerable and classifies implementation-stage
  excluded-only, the terminal commit passes its own role contract); and
  `implementation_provenance_interval_reachable` (the boolean wrapper for
  `technical_approval_gate_reachable`'s existing
  `head_matches_reviewed_implementation_head` argument).
- `.claude/commands/approve-review.md` step 1 — rewritten to call the new
  interval check instead of documenting bare equality, and to report the
  concrete raised-exception reason on refusal rather than an opaque
  boolean.
- `.claude/commands/milestone-implement.md` step 4 and
  `.claude/commands/apply-implementation-review.md` step 7 — reordered so
  `record_bundle_generation`'s state write is committed **alone**, as its
  own dedicated `Workflow-Bundle-Generation-Record`-trailered commit,
  *before* any bundle file is written or `prepare-ai-review.sh` runs
  (previously the state write was folded into whatever later commit
  happened to record the round's outcome — exactly the shape that produced
  this finding).
- `.claude/commands/apply-functional-review.md` step 6.4 — same reordering
  fix; this file's pre-fix text additionally had the
  `prepare-ai-review.sh`-before-`record_bundle_generation` ordering bug S7
  already flagged as a documentation gap for the other two command files
  (never corrected there until now).
- 8 new hermetic tests (`workflow_state_test.py`,
  `TestImplementationProvenanceInterval`): exact reviewed HEAD (zero-gap);
  one excluded-only commit between `reviewed_implementation_head` and the
  terminal commit; a further unrecorded commit landing past the terminal
  commit (refuses); a misspelled/wrong trailer key (never discovered,
  refuses); `reviewed_implementation_head` naming a commit that isn't an
  ancestor at all (refuses); two commits sharing the same trailer value
  with no resolving supersession (genuine ambiguity, refuses); a merge
  putting `reviewed_implementation_head` on a non-first-parent path
  (refuses); and a direct reproduction of this finding's own real shape
  (protected fix commit, excluded docs commit, dedicated record commit) —
  the gate becomes reachable. Full suite green (408/408 in
  `workflow_state_test.py`, all of `workflow_fingerprint_test.py` and
  `workflow_integration_test.py` except one pre-existing, unrelated
  failure — `test_every_wfr_row_description_matches_json_exactly` expects
  60 WFR rows, the live document already has 67, untouched by this fix,
  confirmed pre-existing by re-running against a stash of this change).
  `workflow_fingerprint_demo_test.py`/`workflow_state_demo_test.py`'s own
  pre-existing golden-value staleness against the real repository's
  ever-advancing HEAD (confirmed present with this change stashed out too)
  is unrelated and out of this remediation's scope.

**Explicit scope boundary.** Only the "ordinary" generation-record role
(a single dedicated commit, two trailers, five allowed fields) is
implemented. The "recovered"/superseded role
(`Workflow-Supersedes`-chained same-content republication,
`/recover-implementation-provenance`, multi-recovery supersession-chain
walking) described in later plan revisions is **not** built — a commit
that isn't a clean ordinary-role match is rejected outright rather than
silently treated as recovered. This closes the acute gap S9 exercised (a
work item's very first generation-record commit, and any number of
excluded-only commits after it) without attempting the full recovery
mechanism's own, considerably larger, surface.

## Next step

A fresh operator session may retry `/approve-review implementation
v2-1-dry-run` directly, once a legitimate ordinary `Workflow-Bundle-
Generation-Record` commit exists for `v2-1-dry-run`'s current
`implementation_revision` (S9's own resumption creates exactly this commit
as its first action, per the corrected `.claude/commands/
apply-implementation-review.md`/`milestone-implement.md` ordering — not a
`v2-1-dry-run`-specific workaround). This session does not execute that
retry itself within the same turn this file was written unless the
gate-unblocking commit and the retry both land in one continuous session
with the user's own literal confirmation already supplied.
