# Workflow v2.1 core — Refined Plan (Revision 15)

Status: plan review complete — round 20 (`OPUS-R20-*`) returned
`Status: APPROVE`, no blocking or important findings, against revision 14.
Per `/apply-plan-review`'s own step 7 (`governing_workflow_version: "1"`
path), this revision is reported ready for implementation; the command
does not auto-start `/milestone-implement` and no further plan-review
round is required. Process/tooling milestone only — no product code, no
`docs/ROADMAP.md`/`docs/ACTIVE_MILESTONE.md` changes, no Milestone 9.

Base commit: `162154d`.

## Round 6 disposition — `BLOCK`, pivot to prototype-first (historical, read this first)

Round 6's external review (`Status: BLOCK`, `OPUS-R6-001` … `-028`) found 8
blocking + 15 important + 5 optional findings, and made an explicit,
unusual, and well-justified process demand: **do not produce a revision 7
of the full prose plan** until `compute_bundle_id()`/`compute_review_content_id()`
existed as working code with real tests. Three of the eight blocking
findings (`OPUS-R6-001`, `-002`, `-008`) were executable propositions about
the fingerprint algorithm specifically, found by literally running
`git diff --raw` in a scratch repository and against this repository's own
working tree — not by reading more carefully than rounds 4-5 had. This was
the fourth consecutive round with a real finding against the
identity/fingerprint mechanism, matching this plan's own pre-committed
escape hatch (see "On repeated growth across rounds" below). The pivot was
accepted without reservation; round 7 built the prototype instead of
revision 7.

## Round 7 — prototype-correction pass (historical, `REVISE`)

The first prototype submission was reviewed narrowly (scope:
`scripts/workflow_fingerprint.py`/`_test.py` and `TEST_RESULTS.md` only —
`OPUS-R6-003` through `-023` explicitly not re-reviewed). Verdict `REVISE`,
4 blocking + 7 important + 2 optional findings (`PROTO-R7-001` … `-013`),
all applied: fail-closed classification actually invoked by the entry
point (`PROTO-R7-001`); every Git object ID fully resolved via
`resolve_base()`/`hash-object`/`ls-tree`, never a possibly-abbreviated
diff value (`PROTO-R7-002`); the manifest redesigned around **snapshots**
(`{exists, mode, blob}` per protected path) instead of diff *status*, so
worktree-source and commit-source reads are genuinely comparable
(`PROTO-R7-003`); `compute_bundle_id()` run as the true last step, with
`MANIFEST.md`'s exclusion scoped to its own field, not the whole file
(`PROTO-R7-004`); plus seven important findings on normalization scope,
bundle-entry mode/symlink handling, test-naming accuracy, suite splitting,
dead-code removal, and a technical-decision entry. Real output is
preserved in `.ai-review/current/TEST_RESULTS.md`'s history; the mechanism
itself is what round 8 then re-examined.

## Round 8 disposition — second prototype-correction pass, this round's actual work

Round 8 (`Status: REVISE`, `OPUS-R8-001` … `-019`) found round 7's fixes
real but incomplete at their boundaries — every remaining defect was a
**boundary** defect (bytes-vs-text, filesystem-vs-Git, ordering, prefix-
vs-exact matching, defaults-vs-required), not a design defect — and
explicitly declined to ask for a third prototype-only round: "the
remaining defects can be fixed and verified without external
adjudication... the next bundle should contain both the prototype
corrections... and revision 7." This document, together with the rewritten
`scripts/workflow_fingerprint.py`, is that merged bundle.

**Independently re-verified before accepting each finding** (this
command's own validation requirement): every `OPUS-R8-*` finding below was
reproduced against the actual repository or a disposable scratch repo
before being applied; see the corresponding docstring/comment in
`scripts/workflow_fingerprint.py` and the test named after each finding in
`scripts/workflow_fingerprint_test.py` for the executable evidence. Applied
in full:

- **`OPUS-R8-001`** (bundle_id self-reference/missing manifest,
  confirmed: `.ai-review/current/MANIFEST.md` did not exist, and the
  reported ID in `TEST_RESULTS.md` did not recompute). The self-reference
  exclusion is now one field-pattern (`^bundle_id: [0-9a-f]{64}$`),
  normalized out of **every** bundle file, not just `MANIFEST.md`;
  `MANIFEST.md` is now a required file, and a bundle missing it fails
  validation; `MANIFEST.md` is generated for real this round (see
  `.ai-review/current/MANIFEST.md`).
- **`OPUS-R8-002`** (byte-level collisions, confirmed by reproducing three
  distinct-input/same-hash cases against the prior code): the two
  normalizers now split only on `b"\n"`, never decode, never use
  `str.splitlines()`. A test exists per collision class (CRLF-vs-LF,
  trailing-newline presence, one non-UTF-8 byte), each asserting
  inequality.
- **`OPUS-R8-003`** (`core.fileMode=false` parity break, confirmed by
  reproducing the exact scratch-repo scenario): worktree mode is now
  derived from `core.fileMode` semantics (`git config --type=bool
  core.fileMode`), matching exactly what Git itself would record on
  commit, instead of raw `os.access()`.
- **`OPUS-R8-004`** (symlinked-directory bypass, confirmed: `is_dir()`
  matched before `is_symlink()` ran): bundle-entry classification now
  checks `is_symlink()` first, and rejects any non-regular, non-directory
  entry (FIFOs, sockets) too.
- **`OPUS-R8-005`** (commit-source path skips classification, confirmed by
  direct code read): `compute_review_content_id_plan_stage_at_commit` now
  calls `assert_all_changed_paths_classified_commit`, scoped to
  `base..commit`.
- **`OPUS-R8-006`** (prefix-matched exclusions over-excluding siblings,
  confirmed by reproducing the four example paths): the exclusion list is
  now split into `PLAN_STAGE_EXCLUDED_PATHS` (exact match) and
  `PLAN_STAGE_EXCLUDED_PREFIXES` (directory prefixes, each validated at
  import to end in `/`).
- **`OPUS-R8-007`** (gitignored paths invisible, dead `.ai-review/`
  exclusion entry, confirmed: `.gitignore:1` already ignores `.ai-review/`,
  so the entry could never match): the dead entry is removed; gitignored
  paths are stated explicitly as categorically outside plan-stage identity.
- **`OPUS-R8-008`** (exclusion has no cost, `docs/TECHNICAL_DECISIONS.md`
  wrongly excluded): every remaining exclusion entry now carries a
  mandatory one-line justification, checked by a test.
  `docs/TECHNICAL_DECISIONS.md` moved from excluded to **protected** — see
  round 8's own bonus-answer 5: a technical-decision entry created
  specifically to satisfy a review finding is durable design content the
  plan-stage reviewer is binding, not incidental housekeeping.
- **`OPUS-R8-009`** (timestamp normalization silently no-ops, confirmed by
  reproducing a shifted header): the header block is now searched for the
  `generated:` field; zero or more than one match fails closed instead of
  silently leaving the line unnormalized.
- **`OPUS-R8-010`** (identity scalars defaulted): `work_item_type`/
  `work_item_id`/`plan_revision` have no defaults; the caller must supply
  them, and `work_item_id` is validated against the slug grammar.
- **`OPUS-R8-011`** (decision entry names a nonexistent script, no CI,
  confirmed: `ls scripts/validate_workflow_state.py` → no such file):
  `docs/TECHNICAL_DECISIONS.md`'s entry now names only files that exist;
  `.github/workflows/ci.yml` gained a step running both Python test files
  on every PR.
- **`OPUS-R8-012`** (coverage claims overstate the suite, confirmed by
  enumerating test names against the docstring's claim): both test files'
  docstrings now state exactly which items are covered; this document's
  "Round 7" section above is now historical narrative (past tense), not a
  present-tense description of a superseded artifact.
- **`OPUS-R8-013`** (`MANIFEST.md` field contract unenforced): the field
  contract (`^bundle_id: [0-9a-f]{64}$`, exactly one match) is now the same
  regex the self-reference stripper uses, so a non-conforming spelling is
  simply ordinary content (participates in the hash) and a duplicated
  conforming line fails closed.
- **`OPUS-R8-014`** (protected/excluded sets outside the projection): both
  sets are now part of the hashed `review_content_id` projection.
- **`OPUS-R8-015`/`-016`/`-017`/`-018`/`-019`** (optional): shared defaults
  are now `frozenset`/`MappingProxyType` (immutable); the misleadingly-named
  parity test is renamed to state what it actually proves; bundle entry
  keys use `Path.as_posix()`; empty-directory invisibility is documented
  and tested rather than silently true; the bytes-vs-text policy split
  (paths vs. bundle content) is stated once in the module docstring.

All 55 hermetic tests and both real-repository demonstration assertions
pass; see `.ai-review/current/TEST_RESULTS.md` for real output.

**One process observation from round 8, addressed structurally rather than
by exhortation** (`OPUS-R8-008`): exclusion had been the cheap default
twice in a row (`.gitignore`, then `docs/TECHNICAL_DECISIONS.md`). Giving
every exclusion entry a mandatory justification string, checked by a test,
means a future author adding a ninth exclusion has to write down *why* —
the cost round 8 asked for, made structural rather than a norm to remember.

## Round 9 disposition — a second, independent reviewer, this round's actual work

Round 9 (`Status: REVISE`, `GPT-R9-001` … `-017`, a different reviewer
from rounds 6-8) independently reproduced the round-8 fixes before finding
new problems: it recomputed `bundle_id` and got the exact reported value,
and independently ran the hermetic suite (55/55). Its own words: "the
prototype is close enough that another standalone prototype-only round is
not necessary." The blocking findings were split roughly evenly between
the prototype's own remaining boundary defects and revision 7's prose
having solved the *shape* of several problems (Milestone 8 adoption, the
bootstrap path, activation rollback) without specifying how they actually
execute.

**Independently re-verified before accepting each finding**: `GPT-R9-001`
was reproduced directly — the exact stale `bundle_id: ca9f1f...` line
round 8's own submission had left in `TEST_RESULTS.md` was confirmed
present, and confirmed invisible to the old code's recomputation.
`GPT-R9-002` was reproduced by re-reading `workflow_fingerprint_demo_test.py`'s
own docstring against `.github/workflows/ci.yml`'s actual steps — the
contradiction was real. `GPT-R9-003` was reproduced by `ls
docs/ai-workflow/registry/ docs/ai-workflow/requirements/` — both
directories did not exist. `GPT-R9-009` was reproduced by reading
`compute_bundle_id`'s mode line directly. `GPT-R9-010` is a first-party
report from the reviewer's own verification run, taken at face value
(re-running `python3 -c "import workflow_fingerprint"` from inside a copied
bundle directory does create `__pycache__/` without `sys.dont_write_bytecode`
set — confirmed by inspection of Python's default import behavior). Each
remaining finding was checked against the actual plan text cited before
being accepted.

Applied in full — code-level fixes (`scripts/workflow_fingerprint.py`,
`.github/workflows/ci.yml`, `docs/TECHNICAL_DECISIONS.md`):

- **`GPT-R9-001`**: the `bundle_id` self-reference exclusion is narrowed
  from "every bundle file" back to exactly `MANIFEST.md`; the same
  pattern in any other file now raises `ForeignBundleIdFieldError`.
- **`GPT-R9-002`**: `.github/workflows/ci.yml` now runs only the hermetic
  `workflow_fingerprint_test.py`; the real-repository demo is explicitly
  documented as local/opt-in, never CI-wired.
- **`GPT-R9-009`**: mode detection (`compute_bundle_id`, `_snapshot_worktree`)
  reads the owner-execute stat bit directly, not `os.access()`.
- **`GPT-R9-010`**: `sys.dont_write_bytecode = True` set unconditionally
  at module import.
- **`GPT-R9-014`**: `work_item_type` validated against `{"process",
  "product"}` before hashing.

Applied in full — plan-doc design fixes (`docs/ai-workflow/WORKFLOW_V2_PLAN.md`,
see "Round 9 finding disposition" below for the complete table with
evidence per finding): `GPT-R9-003` (registry/mapping JSON created and
protected), `GPT-R9-004` (new `D-Bootstrap`), `GPT-R9-005` (`D-Legacy`
dormant-to-active redesign), `GPT-R9-006` (backfill removed from `D2`),
`GPT-R9-007` (durable rollback trailer + WF8b pointer persistence in
`D-Self-Governance`), `GPT-R9-008` (guard simplified in `D2`), `GPT-R9-011`
(generalization deferral restated precisely, `D-Registry`), `GPT-R9-012`
(audit updated, see below), `GPT-R9-013` (`work_item_kind` schema field,
`D3`), `GPT-R9-015` (portability split, `D-Bundle-Manifest`), `GPT-R9-016`
(child-work-item remediation versioning, `D-Functional-Remediation`),
`GPT-R9-017` (this bundle's own narrative regenerated once, with no
self-referential ID embedded outside `MANIFEST.md` — see
`.ai-review/current/TEST_RESULTS.md`).

## Round 10 disposition — the identity subsystem is frozen; this round is prose-only

Round 10 (`Status: REVISE`, `OPUS-R10-001` … `-017`, independently
recomputed `bundle_id`/`review_content_id` from both the bundle directory
and the extracted `review-bundle.tar.gz`, matching exactly) re-ran every
one of round 8's own blocking regression probes and confirmed all five
genuinely fixed by execution, not by reading the disposition table. Its
own words: **"the identity subsystem is finished and should be frozen…
the remaining work is wiring it into commands (WF4a-i), not refining
it."** This round makes no further changes to `compute_bundle_id()`/
`compute_review_content_id()` beyond the one it required
(`OPUS-R10-001`'s classification widening) — the algorithm itself is
untouched.

Round 10 named a single coherent class of defect across all five blocking
findings: **the design was specified for the steady state and not for the
transition into it** — correct once everything exists, undefined while it
is being built. Concretely: the classifier was scoped to today's working
tree, not to the paths this milestone's own checkpoints would create
(`OPUS-R10-001`); the bootstrap command's retirement point preceded the
checkpoint that builds its replacement (`OPUS-R10-002`); the replacement
it was retired in favor of would have routed this milestone's own
checkpoints to the v1 branch, back into Milestone 8 (`OPUS-R10-003`); the
bootstrap handoff reconstructed checkpoint status but not the fields
`IMPLEMENTING`'s entry condition actually reads (`OPUS-R10-004`); and
Milestone 8's adoption transition had no way to be invoked at all
(`OPUS-R10-005`).

**Independently re-verified before accepting each finding**: `OPUS-R10-001`
was reproduced directly — `classify_path` on
`.claude/commands/bootstrap-workflow-v2.md` against the then-current lists
raised `UnclassifiedPathError`, confirmed by direct interpreter
invocation, and the reviewer's own end-to-end scratch-repository
simulation (WF0 commit, then a WF1a-shaped commit adding the file) was
reproduced as a new test (`test_055`). `OPUS-R10-002`/`-003` were
confirmed by re-reading D-Bootstrap's and D-Self-Governance's actual text
side by side: the two propositions ("fixed at `'1'` for this milestone's
entire execution" and "`/milestone-implement` drives the remaining
checkpoints normally") are jointly unsatisfiable exactly as described, no
reproduction beyond careful reading was needed. `OPUS-R10-004` was
confirmed against D3's actual per-item field list, which the described
handoff did not populate. `OPUS-R10-005` was confirmed by tracing the
adoption trigger: no section named a selector, and `active_work_item_id`
being null for a dormant item was independently verified against D1's own
completion/reset rule.

The fix adopted for `OPUS-R10-002`/`-003` together is not the smallest
one the reviewer offered — it is the other option the reviewer named as
valid: the bootstrap command never hands off at all, remaining
`workflow-v2-1-core`'s sole driver for its entire lifecycle, retired only
at `MILESTONE_COMPLETE`. This was chosen because it eliminates both
findings structurally (there is no transfer point to get wrong, and no
version gate for this one item to route around) rather than patching each
with a named exemption, at the cost of the bootstrap command existing
longer — a cost the reviewer's own alternative (b) explicitly accepted as
smaller than option (a)'s added interactions.

Applied in full — see "Round 10 finding disposition" below for the
complete table with evidence per finding: `OPUS-R10-001` (classification
widened, `D-Fingerprint`), `OPUS-R10-002`/`-003` (permanent bootstrap
driver, `D-Bootstrap`), `OPUS-R10-004` (full field reconstruction via
continuous state-sync, `D-Bootstrap`), `OPUS-R10-005` (explicit selector
argument, `D-Legacy`), `OPUS-R10-006` (bidirectional registry × mapping
coverage, `docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`),
`OPUS-R10-007` (synthetic items get `work_item_type: "process"`, `D3`),
`OPUS-R10-008` (`expected_dirty_paths` keyed by work item, `D3`),
`OPUS-R10-009` (`D-Selection` names the JSON, not the Markdown view),
`OPUS-R10-010` (confirmation specificity, `D2`), `OPUS-R10-011`
(`governing_workflow_version: "1"` at import, `D-Legacy`), `OPUS-R10-012`
(bootstrap-window plan-content guard, `D-Bootstrap`), `OPUS-R10-013`
(WF-M8 split into WF-M8a/WF-M8b), `OPUS-R10-014` (`no_content_id` waiver
removed, `D2`), `OPUS-R10-015` (validator rule restated, `D3`),
`OPUS-R10-016` (bootstrap command conformance test, `D-Bootstrap`),
`OPUS-R10-017` (trailing-slash validation confirmed load-bearing,
`D-Fingerprint`).

All 66 hermetic tests and 3 real-repository/integration tests pass; see
`.ai-review/current/TEST_RESULTS.md` for real output.

## Round 11 disposition — a manual multi-model plan-review enhancement, layered on the frozen identity subsystem

Round 11 (a manual external reviewer, `Status: REVISE`, `GPT-R11-001`
through `-012`) reviewed revision 9 together with a separate, out-of-band
request: harden this repository's own plan-review gate into an explicit
**local-model-then-manual-external** sequence (recommended tools: Opus for
the local round, ChatGPT for the manual external round), so that a plan
cannot reach `AWAITING_PLAN_APPROVAL` on the strength of only one reviewer
role having seen the current content. The reviewer's own framing: "the new
manual multi-model review enhancement is compatible with the overall
architecture, but it cannot be added only as a command and user guide: the
existing state machine currently treats one matching external review as
sufficient to reach plan approval." All four blocking findings are
instances of the same gap — the enhancement was specified as a
*procedure* (run Opus, then run ChatGPT) without a corresponding change to
the *state machine and durable record* that would actually enforce the
sequence, so a single `/apply-plan-review` round could still declare the
plan ready, and a single matching `APPROVE` from either role alone could
still satisfy `/approve-review`.

**Independently re-verified before accepting each finding**: `GPT-R11-001`
was confirmed directly against `D-States`' actual text (below) —
`AWAITING_PLAN_APPROVAL`'s entry condition reads only "the most recently
reviewed round's status," which is satisfied by a single `APPROVE`
regardless of reviewer role, and no state distinguishes a local-model
round from a manual-external one. `GPT-R11-002` was confirmed against
`D3`'s per-item schema (below) — no field records which reviewer role
last wrote `REVIEW_FEEDBACK.md`, so a second reviewer's feedback
physically overwrites the first's with no trace. `GPT-R11-003` was
confirmed against this very command, `.claude/commands/apply-plan-review.md`
(step 7): "otherwise, report the plan as ready for implementation," gated
only on `Status != BLOCK` and a subjective "major structural change"
judgment — exactly the self-declaration path the finding names, and the
exact command instance producing this revision. `GPT-R11-004` was
confirmed by reading missing-test items 26 and 74 side by side (below):
item 26 was never updated when `OPUS-R10-010` replaced the novelty check
with a specificity check, and directly contradicts item 74's own positive
case. `GPT-R11-009`/`-010` were confirmed by reading the checkpoint
registry/mapping JSON files directly and grepping missing-test items
18/19a/19b/24: all four still read `WF-M8`, a checkpoint ID that no
longer exists after `OPUS-R10-013`'s split into `WF-M8a`/`WF-M8b`.

Applied in full — see "Round 11 finding disposition" below for the
complete table: `GPT-R11-001`/`-002`/`-003` (resolved together, a new
`D-Plan-Review-Stages` section: two new states inserted between
`REVISING_PLAN` and `AWAITING_PLAN_APPROVAL`, a durable per-work-item
stage ledger in `WORKFLOW_STATE.json`, and a dual-mode-scoped rewrite of
`/apply-plan-review`'s exit step that never self-declares readiness under
`governing_workflow_version: "2.1"`), `GPT-R11-004` (missing-test item 26
restated to match `D2`'s actual specificity rule and item 74),
`GPT-R11-005`/`-011`/`-012` (a full command contract for the new,
model-independent `/review-plan`, in role-oriented terminology),
`GPT-R11-006` (the bundle/rebinding rule, keyed by `review_content_id` not
`bundle_id`), `GPT-R11-007` (the post-edit return path stated as an
explicit sequence, not "repeat the relevant steps"), `GPT-R11-008` (the
guide's final path named and pre-declared protected — see round 10's
disposition table above for the one, explicitly-endorsed classification
addition this required), `GPT-R11-009` (a new checkpoint, `WF4a-iv`, owns
the protocol/command/guide as a unit; `WF8a-ii` gains it as a dependency;
four new `WFR` entries), `GPT-R11-010` (missing-test items 18/19a/19b/24
corrected to `WF-M8a`/`WF-M8b`), `GPT-R11-011`/`-012` (folded into the new
section's terminology and session-boundary wording directly, no separate
disposition needed).

**One deliberate scope decision, stated plainly**: the concise operator
guide's *content* is not written this round. Its final path
(`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`) is named and protected now
(the classification addition round 10 already anticipated this pattern
for), but the guide documents commands (`/review-plan`, the revised
`/apply-plan-review`) that do not exist until `WF4a-iv` builds them —
writing its prose against not-yet-built commands would either restate this
plan's own design a second time (redundant, and a second place for the two
copies to drift) or guess at implementation details this plan
deliberately leaves to the checkpoint. `WF4a-iv`'s own scope includes
writing the guide against the commands it just built, which is also the
only point the guide's content can be verified against real behavior
rather than prose intent.

Zero product "Open decision" rows beyond this milestone's own toolchain
entry remain touched. No change to `compute_bundle_id()`/
`compute_review_content_id()` beyond `GPT-R11-008`'s one classification
addition; the 67 hermetic tests and 3 real-repository/integration tests
all pass — see `.ai-review/current/TEST_RESULTS.md`.

## Provenance

Source proposals live at `.ai-review/source/` (gitignored, context-only,
never committed). They are not included in normal review bundles (resolves
R5-PLAN-007 — see D-Bundle-Manifest); this file and `WORKFLOW_V2_AUDIT.md`
are the sole authoritative, committed design and are sufficient on their
own.

## Scope note

This is **Workflow v2.1 "core"**: state/identity/approval correctness and
the Milestone-8 unblock. Metrics/hooks/status-line, context-governance
thresholds, and subagent-routing experiments are deferred to a follow-up
milestone, **"Workflow v2.2 — efficiency instrumentation"** (see "Deferred
to Workflow v2.2" below).

## Revision history

- **Revisions 1-3**: git history is not a valid pointer for this document
  (round 5 correctly caught this — see R5-PLAN-003 disposition below);
  summarized instead. Revision 1: initial audit + plan. Revision 2: added
  `/approve-review`, 2 new states, `WORKFLOW_STATE.json`, a bundle-identity
  concept, WF-M8. Revision 3: reversed a wrong hook-API correction after
  primary-source verification; first fingerprint redesign; split portable
  vs. local-runtime state; added approval-commit semantics; added the
  Milestone-8 legacy path and the functional-remediation cycle.
- **Revision 4**: second fingerprint redesign after finding the first
  self-invalidated on checkpoint completion and on its own approval-commit
  sequence; moved gitignored/local-only data out of tracked state;
  collapsed six approval fields into one `basis`-discriminated record;
  wrote (incomplete) self-governance framing and full state definitions
  for the two new gates.
- **Revision 5**: third fingerprint redesign after a second, independent
  reviewer found `workflow_phase`/`selected_checkpoint_id` still mutating
  the identity, the identity never covering authoritative reviewed content
  (only bundle wrapper files), and multiple tracked fields asking to
  contain the SHA of their own containing commit. Fixed via commit
  trailers + live derivation, a `governing_workflow_version` field, and the
  scope cut to "core" vs. "v2.2." Checkpoint count 14 → 13.
- **Revision 6**: a third, independent reviewer found the actual root
  cause behind three straight rounds of fingerprint churn: one identifier
  serving two purposes (uniquely identifying the exact bundle a reviewer
  saw, and remaining stable while non-protected bundle evidence changes).
  Split into `bundle_id` and `review_content_id`. Also restored `D1`/
  `D-States` (revision 5's plan doc was never committed and had gone
  missing), added `WORKFLOW_CONFIG.json`, and fixed trailer-lookup scoping.
  Reviewed at `BLOCK` (`OPUS-R6-001`…`-028`): the split was right, but the
  worktree-source manifest was content-blind (destination blob always
  `0000000`, untracked files invisible) and the manifest had no path
  scope — both **executable** defects, triggering this plan's own
  pre-committed prototype-first escape hatch instead of a seventh prose
  round.
- **Revision 6 + prototype interlude (2 rounds)**: `scripts/workflow_fingerprint.py`
  built and corrected twice (`PROTO-R7-*`, `OPUS-R8-*`) against real Git
  repositories, not prose. `OPUS-R6-003` through `-023` — 21 findings about
  the surrounding state machine, approval lifecycle, and bundle mechanics,
  independent of the fingerprint algorithm itself — were deliberately left
  open through both prototype rounds, as the reviewer's own scoping
  requested.
- **Revision 7**: resolved `OPUS-R6-003` through `-023` in full (see
  "Round 6 finding disposition" below), merged into one bundle with the
  round-8 prototype corrections, per round 8's explicit instruction not to
  split the two into further rounds. Checkpoint count 13 → 14 (WF8a split
  into two sub-checkpoints per `OPUS-R6-023`).
- **Revision 8**: a second, independent reviewer (`GPT-R9-*`)
  found round 8's prototype fixes real but incomplete at a different
  boundary (`GPT-R9-001`/`-009`/`-010`/`-014`, applied at the code level —
  see the "Round 8/9 disposition" sections above), and found revision 7's
  prose plan had solved the *shape* of several problems while leaving real
  gaps in how they actually execute: no bootstrap path from the current,
  unmodified Workflow v1 into this self-modifying milestone at all
  (`GPT-R9-004`, new `D-Bootstrap` section, new WF0 checkpoint); a terminal
  Milestone-8 import phase unreachable by any later command
  (`GPT-R9-005`, `D-Legacy` redesigned around a dormant `LEGACY_READY` →
  explicit-adoption transition); an undefined plan-approval commit-SHA
  backfill (`GPT-R9-006`, removed — `reviewed_content_commit` stays
  permanently `null` at plan stage); an unimplementable invocation-origin
  guard (`GPT-R9-008`, removed in favor of named supported controls only);
  a non-durable activation rollback and an underspecified multi-session
  synthetic-item pointer (`GPT-R9-007`); the registry/mapping JSON files
  claimed authoritative while not existing in the reviewed bundle
  (`GPT-R9-003`, created this round and protected); and a worktree/HEAD
  staleness check that would have blocked the external reviewer it exists
  to serve (`GPT-R9-015`, split into diagnostic-for-external vs.
  enforced-for-local). See "Round 9 finding disposition" below for the
  full disposition table. Checkpoint count 14 → 15 (WF0 added).
- **Revision 9 (this revision)**: a third, independent reviewer
  (`OPUS-R10-*`) confirmed the identity subsystem finished and correct —
  every one of round 8's own regression probes re-verified by execution —
  and named one coherent class of remaining defect across all five
  blocking findings: the design was specified for the steady state and
  left undefined for the transition into it. The classifier covered only
  today's working tree, not the paths this milestone's own checkpoints
  would create, making plan-approval durability unevaluable from the first
  implementation-phase commit (`OPUS-R10-001`); the bootstrap command's
  stated retirement point preceded the checkpoint that builds its
  replacement, and that replacement would have routed this milestone's own
  checkpoints back to the v1 branch and Milestone 8 (`OPUS-R10-002`/`-003`,
  resolved together by making the bootstrap command
  `workflow-v2-1-core`'s permanent driver, never handing off); the handoff
  reconstructed checkpoint status but not the fields `IMPLEMENTING`'s
  entry condition reads (`OPUS-R10-004`, replaced with a continuous
  per-invocation state-sync); and Milestone 8's adoption transition had no
  selector to invoke it with (`OPUS-R10-005`, fixed via an explicit
  work-item-id argument). See "Round 10 finding disposition" below for the
  full table, including 12 further important/optional findings. Checkpoint
  count 15 → 16 (WF-M8 split into WF-M8a/WF-M8b, `OPUS-R10-013`).
- **Revision 10**: a manual external reviewer (`GPT-R11-*`)
  evaluated a proposed local-model-then-manual-external plan-review
  enhancement alongside revision 9 and found it under-specified at exactly
  the boundary between procedure and enforcement: the state machine had no
  representation of two required reviewer roles (`GPT-R11-001`), no
  durable record proving both had run against the same content
  (`GPT-R11-002`), and `/apply-plan-review`'s own exit step could still
  self-declare the plan ready on a subjective judgment
  (`GPT-R11-003`) — the exact command instance that produced revision 9.
  A fourth blocking finding was unrelated to the enhancement: missing-test
  item 26 still described the novelty check `OPUS-R10-010` had already
  replaced, directly contradicting item 74's own positive case
  (`GPT-R11-004`). Fixed via a new `D-Plan-Review-Stages` section (two new
  states, a durable per-work-item stage ledger, a dual-mode-scoped
  `/apply-plan-review` rewrite), a full command contract for the new
  `/review-plan`, and item 26's correction. Six further important/optional
  findings resolved: the guide's path named and pre-declared protected
  (`GPT-R11-008`), a new checkpoint (`WF4a-iv`) and four `WFR` entries
  (`GPT-R11-009`), and stale `WF-M8` references in missing-test items
  18/19a/19b/24 corrected to `WF-M8a`/`WF-M8b` (`GPT-R11-010`). See "Round
  11 finding disposition" below for the full table. Checkpoint count 16 →
  17 (`WF4a-iv` added — new capability, not a resizing split).
- **Revision 11**: a manual external reviewer (`GPT-R12-*`)
  found revision 10's own new two-stage plan-review protocol "not yet
  internally executable": `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`'s entry
  condition accepted a local `REVISE` exactly as it did an `APPROVE`
  (`GPT-R12-001`); `/review-plan`'s stated write set directly contradicted
  its own "records the completed stage in the ledger" exit condition, and
  no command was named as the manual stage's writer at all, leaving both
  new phases unreachable via the documented happy path (`GPT-R12-002`);
  the manual stage's own exit named no writer, destination, `BLOCK`
  handling, or malformed-feedback rejection (`GPT-R12-003`). Fixed via
  verdict-specific exits for both stages, an explicit six-row verdict/state
  transition table naming the writer and validation preconditions for
  every row, a corrected `/review-plan` write set, and a new, small,
  mechanical `/record-manual-plan-review` command that is the manual
  stage's sole writer and explicitly not a user-authority gate. Three
  further important findings resolved: the ledger restated explicitly as a
  current-content gate record, not an audit trail (`GPT-R12-004`); the
  "fresh session required" wording made consistent with its own
  "unverifiable" clause (`GPT-R12-005`); `WF4a-iv`'s session target widened
  1 → 1-2 with a mandatory internal commit boundary, rather than a
  checkpoint split (`GPT-R12-006`). See "Round 12 finding disposition"
  below for the full table. Checkpoint count unchanged at 17 — `WF4a-iv`
  resized, not split, per `GPT-R12-006`'s own accepted alternative.
- **Revision 12**: round 13 approved revision 11's bundle;
  an independent staff-level reviewer (`OPUS-R14-*`) then found that
  approval could not stand, for four mechanical reasons rather than
  disagreement with round 13's own substantive judgment (which round 14
  re-confirms and does not reopen): the approved plan could not be
  implemented without invalidating its own approval, since a protected
  path (`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`) that `WF4a-iv` must
  create sat inside `PLAN_STAGE_PROTECTED`, contradicting `D-States`' own
  "checkpoints never touch protected documents" invariance claim
  (`OPUS-R14-001`); the approving feedback carried none of the three
  fields (`Reviewed bundle ID:`/`Reviewed base commit:`/`Work item:`)
  needed to bind an approval to a specific artifact, and nothing in
  `D-Bootstrap` required them for this milestone's own v1-native approval
  (`OPUS-R14-002`); a required post-approval edit (missing-test item 85)
  targeted the same protected document the approval binds to, with no
  state-machine path for "approved with corrections" (`OPUS-R14-003`); and
  the document's own title/status line still said "Revision 10 / round 12
  bundle" while every other section had already moved on to revision
  11/round 13, an oversight from the prior round's own edit
  (`OPUS-R14-004`). Fixed: the operator guide moved from
  `PLAN_STAGE_PROTECTED` to `PLAN_STAGE_EXCLUDED_PATHS` (`OPUS-R14-001`/
  `-009`, also closing the "approved sight-unseen" concern together);
  `D-Bootstrap` now requires the three binding fields and refuses a
  `"2.1"`-only reviewer role for this permanently-`"1"` item
  (`OPUS-R14-002`/`-008`); item 85 restated to agree with item 96
  word-for-word (`OPUS-R14-003`/`-007`); the title/status line corrected
  (`OPUS-R14-004`). Two further important findings resolved: manual
  ingestion's `bundle_id` precondition downgraded from hard to advisory,
  matching `GPT-R11-006`'s already-established tolerance
  (`OPUS-R14-005`); plan-stage computation now fails closed on an absent
  protected path via a new `AbsentProtectedPathError`, scoped to plan
  stage only (`OPUS-R14-006`). See "Round 14 finding disposition" below
  for the full table, including the "decisions not taken" note on
  `OPUS-R14-004`'s machine-readability suggestion. Checkpoint count
  unchanged at 17; three new requirements (`WFR-42`–`WFR-44`) added, all
  owned by existing checkpoints.
- **Revision 13**: round 15 approved revision 12's bundle;
  an independent staff-level reviewer (`OPUS-R16-*`, the same reviewer as
  round 14) then found one remaining blocking issue and retracted one of
  its own round-14 suggestions, re-confirming every round-14 fix as
  genuinely resolved by execution (not disposition-table wording) without
  reopening any of them. Blocking: the classification lists are
  work-item-scoped, but the classifier's *input* is repository-wide —
  `docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md`, `docs/milestones/`, and
  `docs/ai-workflow/archive/` all classified as `UnclassifiedPathError`,
  and D1 explicitly sanctions the concurrent operation (Milestone 8
  adoption) that would write the first of them while `workflow-v2-1-core`
  is still mid-implementation (`OPUS-R16-001`). Important, and a genuine
  self-correction: revision 12's own `OPUS-R14-012` disposition scheduled
  the disposition-table archival for "immediately after this document's
  next approval" — the same defect class `OPUS-R14-003` had just fixed for
  item 85, since `docs/ai-workflow/archive/` was itself unclassified and
  `WORKFLOW_V2_PLAN.md` is protected, so that scheduled edit would have
  staled its own approval (`OPUS-R16-002`, the reviewer's own explicit
  retraction of its prior suggested timing). Fixed: four new exclusion
  entries (`docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md`,
  `docs/milestones/`, `docs/ai-workflow/archive/`), a second explicit
  D-Fingerprint classification obligation stated alongside `OPUS-R14-001`'s
  ("no path that any concurrent operation may write may be unclassified"),
  and the archival retimed to at-or-after `MILESTONE_COMPLETE`. One new
  requirement (`WFR-45`); checkpoint count unchanged at 17; missing-test
  items 117-124 added, all implemented against the frozen prototype this
  round. See "Round 16 finding disposition" below for the full table.

- **Revision 14 (this revision)**: round 17's bundle (built from revision
  13) was reviewed by round 18, a fourth independent staff-level reviewer
  (`OPUS-R18-*`, same reviewing standard as `OPUS-R14-*`/`OPUS-R16-*`), who
  confirmed revision 13 internally sound (protected paths, exclusion
  generality, D-Fingerprint's two obligations, archival retiming) but
  found the identity subsystem's *algorithm* was frozen and correct while
  the *generator* around it was not: `MANIFEST.md`'s `review_content_id`
  did not recompute from the bundle it described, and the one documented
  inspection command mutated the artifact it was meant to verify, silently
  rewriting both identifiers to values nobody reviewed
  (`OPUS-R18-001`/`-002`). Both trace to the same root cause round 8 fixed
  once for `bundle_id` and never generalized: `plan_revision` was a
  caller-supplied literal, hardcoded and stale in the one place that
  writes the authoritative manifest (`OPUS-R18-003`). Also found: the
  concurrent-write classification obligation `OPUS-R16-001` stated
  correctly was applied only to the four paths that finding named, and
  the same sanctioned scenario (Milestone 8 adoption while this item is
  mid-implementation) continues past the checklist write into
  `FIXING_FUNCTIONAL_FINDINGS`, which touches product code and product
  documentation the classifier had never seen (`OPUS-R18-004`); and no
  second, checkable copy of `review_content_id` existed outside the one
  file capable of being silently mutated (`OPUS-R18-005`). Fixed:
  `plan_revision` is now sourced from the registry JSON (already
  protected, already machine-written) and cross-checked against the plan's
  own title, reversing round 15's deferral now that `__main__` is the real
  caller that deferral was waiting for; the CLI is read-only by default,
  with manifest writing moved behind an explicit `--write-manifest`
  invocation that computes both identifiers last and asserts idempotence
  for both before returning (previously only `bundle_id` got that
  discipline); the exclusion lists gained `app/`, `docs/adr/`,
  `docs/agent-context/`, `gradle/`, `config/`, `.github/`, and five
  top-level product docs, all under one shared justification, with
  fail-closed preserved for any path outside this named set; and
  `REVIEW_REQUEST.md` now states `review_content_id` directly, with
  generation asserting it agrees with `MANIFEST.md`. No design decision
  changes; the identity algorithm itself is untouched. See "Round 18
  finding disposition" below for the full table.

- **Revision 15 (this revision)**: round 19's bundle (built from revision
  14) was reviewed by round 20, a fifth independent staff-level reviewer
  (`OPUS-R20-*`), who found **no blocking or important issues** —
  `Status: APPROVE`. All five round-18 fixes were re-verified by execution
  (idempotence for both identifiers, the read-only/write split,
  registry-sourced `plan_revision`, the widened exclusion lists, and
  `REVIEW_REQUEST.md`'s second checkable copy), and the reviewer
  explicitly looked for a blocking defect and did not find one. Three
  optional findings, none of which blocks implementation: the manifest
  write sequence is not itself atomic across the idempotence check
  (`OPUS-R20-001`); root-level build files (`build.gradle.kts`,
  `settings.gradle.kts`, `gradlew`, `.editorconfig`, `Makefile`) are not
  named in any classification list, so they fail closed rather than being
  excluded (`OPUS-R20-002`); and the plan-stage exclusion constants must
  not be reused unmodified for the implementation-stage manifest WF4a-i
  still owes, since the two stages' protected/excluded sets are
  near-inverses (`OPUS-R20-003`). All three are recorded as forward-looking
  obligations for `WF4a-i` rather than fixed against the frozen prototype
  now — none has a real caller yet (the deferred implementation-stage
  manifest), the same reasoning that has governed every other
  not-yet-built-command deferral in this document (e.g. `OPUS-R14-011`).
  No design decision changes and no checkpoint resizing; the identity
  algorithm and generator are both untouched this round. See "Round 20
  finding disposition" below for the full table.

## Correcting my own round-2 correction (kept, independently re-verified across three rounds — do not reopen)

- **`PostToolUse` on a completed `Agent` tool call DOES carry usage
  telemetry** for foreground/completed calls, Claude Code ≥ v2.1.174
  (environment: v2.1.220 — satisfied). Now moot for this plan's own scope
  (metrics deferred to v2.2) but kept correct for that future milestone to
  start from.
- **`PreCompact`/`PostCompact` do NOT support `additionalContext`
  injection.** Same status — correct, deferred to v2.2.

## Round-5 finding disposition (historical, unchanged from revision 6)

Round 5's disposition table (18 IDs) is preserved in the conversation
record that produced revision 5 and not repeated here (see the note on
"git history" above — this doc, not a prior revision, is now the single
source of truth for what's settled). Every round-6 (`R5-PLAN-*`) finding
was checked against this plan's actual text or the live repository before
being applied.

| ID | Disposition | Evidence |
|---|---|---|
| R5-PLAN-001 | **Accepted — root-cause fix, not another field patch** | `author_file_hashes` was listed inside the hashed projection while the same section said it "never gates approval validity by itself" — both cannot be true of a field inside a hash. Split into `bundle_id` (exact-bundle identity) and `review_content_id` (stable protected-content identity). |
| R5-PLAN-002 | **Accepted** | `governing_workflow_version` was defined as strictly per-work-item, and `WF-Activate` was asked to flip it "for the next work item" — a field on a record that doesn't exist yet cannot be written. Added `docs/ai-workflow/WORKFLOW_CONFIG.json` as a repository-level default. |
| R5-PLAN-003 | **Accepted, confirmed directly** | `git log --oneline --all -- docs/ai-workflow/WORKFLOW_V2_PLAN.md` returns empty — the file has never been committed. Full D1 and D-States text restored, in this document. |
| R5-PLAN-004 | **Accepted** | Trailer lookup used unscoped `git log --grep` against a prefix. Fixed via exact trailer parsing, work-item scoping, full identifiers, ancestry-limited search. |
| R5-PLAN-005 | **Accepted** | Manifest hashed each changed path's content at the reviewed head, with no blob for a deleted path and no rename/mode/symlink handling. Replaced with a manifest built from Git's own change representation. |
| R5-PLAN-006 | **Accepted** | The freshness check recomputed at the old, immutable reviewed head — could never detect drift. Fixed via a freshness rule recomputing from current content against an explicit allowlist. |
| R5-PLAN-007 | **Accepted** | Source proposals removed from the bundle's context list. |
| R5-PLAN-008 | **Accepted** | Real risk, same class as the first-party Milestone-8 bundle-mismatch incident. Added pointer-vs-tracked-state cross-validation. |
| R5-PLAN-009 | **Accepted** | Split into a machine-readable immutable mapping file (hashed) and a separate mutable ledger doc (never hashed). |
| R5-PLAN-010 | **Accepted** | Deduplicated checkpoint-status fields; standardized on `reviewed_implementation_head`. |
| R5-PLAN-011 | **Accepted** | WF-M8/WF4c dependencies corrected to WF4a-ii/-iii, where the record-writer/Git-lifecycle mechanics actually live. |
| R5-PLAN-012 | **Accepted** | Added an explicit Workflow v2.2 handoff subsection. |
| R5-PLAN-013 | **Accepted** | Naming applied consistently: `bundle_id`, `review_content_id`, `reviewed_bundle_id`, `approved_review_content_id`. |
| R5-PLAN-014 | **Accepted** | Test-vector table added. |
| R5-PLAN-015 | **Acknowledged, no action needed** | Already in agreement; WF5's stage-completeness check stays in scope. |

## Round 6 finding disposition (revision 7)

Every `OPUS-R6-*` finding this document deliberately left open through the
prototype rounds is resolved here. Each row names the concrete section
below that resolves it; "Evidence" states what was independently confirmed
against the repository or the design's own text before accepting the
finding, per this command's validation requirement.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R6-003 | **Accepted** | D-States, D-Approval-Commits | Confirmed: `IMPLEMENTING`'s entry condition recomputed `review_content_id` over an unfiltered, growing `base..HEAD` range, so the first checkpoint commit made it unequal to the value computed when only the plan documents existed. Fixed by stating the recomputation rule per stage: plan-stage durability recomputes only the plan-stage (protected-doc-scoped) projection, which is invariant under checkpoint commits because those never touch the protected plan/audit/decisions documents. |
| OPUS-R6-004 | **Accepted** | D-States, D2 | Confirmed: both new gates' entry conditions required the approval record that only their own exit action (`/approve-review`) could write — unreachable by construction. Fixed by splitting entry (requires a decidable, non-`BLOCK` external status) from the basis choice (`EXTERNAL_APPROVE` vs. `USER_OVERRIDE`, decided inside `/approve-review`, never a precondition to enter the state). |
| OPUS-R6-005 | **Accepted** | D-States, D3 | Confirmed: `WORKFLOW_STATE.json` is tracked and must be written on almost every transition, making "the working tree is clean" unreachable without an authorized commit for those writes. Fixed by replacing the clean-tree condition with "no protected path is dirty," with `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` explicitly excluded. |
| OPUS-R6-006 | **Accepted** | D3, D-Legacy, D-Self-Governance | Confirmed: a single-work-item state file cannot hold Milestone 8's legacy approval while Workflow v2.1's own process item occupies it, and the `v2.1-dry-run` synthetic item already needed a second concurrent record. Fixed via a multi-item `work_items` map with an `active_work_item_id` pointer; Milestone 8's import writes a terminal `LEGACY_IMPORTED` entry, never touching the active item. |
| OPUS-R6-007 | **Accepted, via a different mechanism than the literal suggestion — see the note directly below the table** | Requirements traceability, Missing tests | Confirmed: `git log --oneline --all -- docs/ai-workflow/WORKFLOW_V2_PLAN.md` still returns empty, and this document's own prior revisions (5 and 6) both referenced "round 4's items"/"round 5's table" without ever inlining them — a pattern that predates and survives revision 6. |
| OPUS-R6-008 | **Fixed at the code level, prototype rounds** | `scripts/workflow_fingerprint.py` | Resolved by `PROTO-R7-004` and `OPUS-R8-001`; see the Round 7/8 disposition sections above. Not re-litigated here. |
| OPUS-R6-009 | **Accepted** | D-Legacy | Confirmed: `LEGACY_V1`'s `approved_review_content_id: null` makes the freshness rule either permanently stale (comparing a real hash to `null`) or requires a `basis`-branch special-case. Fixed by backfilling a real `review_content_id`, computed over Milestone 8's actual content at import time, so the freshness rule has no `basis` branch at all. |
| OPUS-R6-010 | **Accepted** | D2 | Confirmed: user-only enforcement rested solely on `disable-model-invocation: true`, unverified against both the SlashCommand and Skill exposure paths the audit itself documents as dual. Fixed by adding a mechanism-independent guard: `/approve-review`/`/accept-milestone` refuse to write unless literal `user_confirmation` text was supplied in the current turn, verified independent of the frontmatter flag. |
| OPUS-R6-011 | **Accepted** | D-States | Confirmed: approval required an external status of exactly `APPROVE` for the *current* `bundle_id`, but applying any feedback produces a new bundle and therefore a new `bundle_id`, for which no `APPROVE` can exist — `USER_OVERRIDE` was the only reachable path even for a `REVISE` round with zero blocking findings. Fixed: entry requires the *latest reviewed round's* status to be `REVISE` or `APPROVE` (not `BLOCK`) with all blocking/important findings resolved or evidence-rejected; the current (possibly regenerated) bundle's `bundle_id` need not itself have been the one reviewed. |
| OPUS-R6-012 | **Accepted** | D-Selection (new) | Confirmed: WF2's scope names "selection algorithm" but the document contained none, and the registry is a dependency DAG, not a chain, so multiple checkpoints can be simultaneously ready. Fixed by stating a deterministic four-rule algorithm and requiring the registry's row order to be a valid topological order. |
| OPUS-R6-013 | **Accepted** | D-Functional-Remediation | Confirmed: no section stated who writes `reviewed_implementation_head`, and the remediation cycle's own steps never re-pointed it at a post-fix commit, so `/approve-review implementation`'s HEAD-equality precondition could never be satisfied again after a single fix. Fixed by naming the bundle generator (at `implementation`/`post-fix` stage) as the field's sole writer. |
| OPUS-R6-014 | **Accepted** | D3 | Confirmed: checkpoint completion was recorded in up to four places (state file, `ACTIVE_MILESTONE.md`, the registry doc, the commit trailer) with a reconciliation rule for only two of them. Fixed by declaring `WORKFLOW_STATE.json` the sole writable record; the registry carries no status column; `ACTIVE_MILESTONE.md`'s narrative is explicitly derived/non-authoritative for status. |
| OPUS-R6-015 | **Accepted** | D-Self-Governance | Confirmed: "missing config fails safe to `'1'`" is the right rule before activation and the wrong rule after it — a lost `WORKFLOW_CONFIG.json` post-activation silently disables every v2.1 gate. Fixed by recording activation as a commit trailer (`Workflow-Activation: 2.1`), discoverable independent of the config file's survival. |
| OPUS-R6-016 | **Accepted** | D-Bundle-Manifest | Confirmed against the actual incident (`WORKFLOW_V2_AUDIT.md:99`): the incident was a stale bundle in a *different worktree* whose own state file agreed with its own stale bundle — the proposed `ACTIVE_WORK_ITEM` cross-check only catches intra-worktree disagreement, which was never the failure mode. Fixed by recording the absolute `worktree_root` and generation HEAD inside the bundle itself and stopping if the consuming worktree's root or HEAD differs. |
| OPUS-R6-017 | **Accepted** | (already resolved, prototype rounds) | `docs/TECHNICAL_DECISIONS.md` now carries the toolchain row (stdlib-only Python), and CI wiring landed this round (`OPUS-R8-011`). |
| OPUS-R6-018 | **Accepted** | D-Registry | Confirmed: R5-PLAN-009's own rationale against parsing one Markdown doc's hashed/unhashed sections applies with more force to selective *column* extraction from a table. Fixed by mirroring D4b: a tracked, machine-readable JSON registry file (hashed), with the Markdown table as a generated, never-hashed human view. |
| OPUS-R6-019 | **Accepted** | Requirements traceability (WFR row), D5 (WF5 scope) | Confirmed: round 5's binaries/unusual-path acceptance criteria were dropped from `WFR-49`'s restatement, and `prepare-ai-review.sh`'s `IFS=$'\t' read` loop is not NUL-safe. Restored to WF5's scope explicitly below. |
| OPUS-R6-020 | **Accepted** | D-Self-Governance | Confirmed: the dual-mode command enumeration omitted `/milestone-plan`, `/apply-plan-review`, `/apply-implementation-review`. All three added, with which of their steps are v1-inert stated. |
| OPUS-R6-021 | **Accepted** | D-Bundle-Manifest | Confirmed: `scripts/prepare-ai-review.sh`, all eight command files, and `REVIEW_PROTOCOL.md` all hardcode `.ai-review/current`/`.ai-review/feedback` today, and this milestone's own in-flight review rounds run out of exactly those paths. Fixed by stating WF5's migration explicitly, including a compatibility read path for the duration of this milestone. |
| OPUS-R6-022 | **Accepted** | D-Commit-Provenance | Confirmed: `milestone8-cp11-work` (`dc4381a`) has diverged from `162154d`, so its integration will be a merge, rebase, or cherry-pick — two of which duplicate trailers onto new commits. Fixed via a documented tie-break (prefer a first-parent ancestor of HEAD whose state/content verification passes) and a named recovery (`Workflow-Supersedes:` trailer or explicit state-file annotation) for genuine ambiguity. |
| OPUS-R6-023 | **Accepted** | Checkpoint registry | Confirmed: WF8a depended on all ten other checkpoints and carried the entire missing-test list, sized at one session with no stated basis for that estimate. Split into WF8a-i (harness/CI/fixtures for the identity subsystem) and WF8a-ii (cross-checkpoint integration and dual-mode conformance); complexity-scale units stated; session targets marked as estimates with no baseline, per the audit's own Determination 2. |
| OPUS-R6-024 | **Accepted (optional)** | D-Self-Governance | `supported_versions` now gates work-item creation: an out-of-range `governing_workflow_version` is rejected. |
| OPUS-R6-025 | **Accepted (optional)** | D2 | `waived_guarantees` is now a validated enum (`no_bundle_id`, `no_content_id`, `no_telemetry`). |
| OPUS-R6-026 | **Accepted (optional)** | D3 | `WORKTREE_IDENTITY.json`'s writer is named: the command that transitions a checkpoint to `IN_PROGRESS` (WF2) creates/refreshes it. |
| OPUS-R6-027 | **Accepted (optional)** | D3 | `branch` and `last_updated` removed; every remaining tracked field has a documented reader. |
| OPUS-R6-028 | **Superseded by events** | — | This finding asked the plan to adopt its own prototype-first escape hatch. It was adopted the same round, independently, before this finding could be applied as a separate action — see "Round 6 disposition" above. |

**Note on `OPUS-R6-007`'s disposition.** The finding's literal suggestion
was "inline the complete WFR-1–61 table and the complete missing-test list
1–88." That table's rows 1-44 and list items 1-45 were never actually
inlined by *any* prior revision of this document — revision 5's own text
says "Round-4's 34 IDs kept where not superseded" without restating them,
and revision 6 inherited that same unresolved pointer. Chasing the exact
historical wording one or two revisions further back would restate
requirements written against a single-`bundle_id` design that has since
been redesigned four times (per "Revision history" above) — presenting
them as current would misrepresent a superseded mechanism, which is a
worse defect than a missing citation. This revision instead replaces both
lists with a **clean, fully current, self-contained restatement** (see
"Requirements traceability" and "Missing tests" below): every requirement
and test obligation that holds *today*, renumbered from 1, with no pointer
to git history, a conversation record, or a gitignored path anywhere in
either list. This satisfies `OPUS-R6-007`'s actual objective acceptance
criteria (every requirement resolves to a row in the plan; no
implementation-critical text is shorter than its cross-reference; WF4b's
mapping file can be written from the plan alone) without fabricating
historical text this document does not actually have.

## Round 9 finding disposition (revision 8)

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| GPT-R9-001 | **Accepted** | `scripts/workflow_fingerprint.py` | Confirmed: the exact stale `bundle_id: ca9f1f...` line left in round 8's own `TEST_RESULTS.md` was invisible to recomputation. Narrowed the self-reference exclusion to `MANIFEST.md` only; any other file with a conforming line now fails closed. |
| GPT-R9-002 | **Accepted** | `.github/workflows/ci.yml`, `docs/TECHNICAL_DECISIONS.md` | Confirmed: the demo test's own docstring says it must not run against arbitrary checkouts, while CI ran it on every PR/push. CI now runs only the hermetic suite. |
| GPT-R9-003 | **Accepted** | D-Registry, D4b, `docs/ai-workflow/registry/`, `docs/ai-workflow/requirements/` | Confirmed: `ls docs/ai-workflow/registry/ docs/ai-workflow/requirements/` returned "no such file or directory." Both files created this round, populated from the plan's own checkpoint/requirements tables, and added to `PLAN_STAGE_PROTECTED`. |
| GPT-R9-004 | **Accepted** | D-Bootstrap (new), Checkpoint registry (WF0), Usability | Confirmed: no `/approve-review` exists yet, and `/milestone-implement` selects work via `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`, which this milestone must not touch — there was genuinely no path from today's v1 commands into this plan's own implementation. Fixed via a one-time bootstrap checkpoint (WF0) and a temporary, one-checkpoint-per-invocation bootstrap command, retired at handoff. |
| GPT-R9-005 | **Accepted** | D-Legacy, D1 | Confirmed: `LEGACY_IMPORTED` was terminal, and no command read the multi-item state to select a terminal entry — Milestone 8's later functional review had no path back into the state machine. Redesigned around a dormant `LEGACY_READY` phase (import) and an explicit, later, user-invoked adoption transition (`/prepare-functional-review`'s v2.1 extension) that activates it. |
| GPT-R9-006 | **Accepted** | D2, D-Approval-Commits | Confirmed: `reviewed_content_commit` was described as "backfilled after the plan-approval commit exists," with no writer, second commit, or dirty-state transition defined. Removed: permanently `null` at plan stage; the approval commit is discovered via its trailer, never stored self-referentially. |
| GPT-R9-007 | **Accepted** | D-Self-Governance | Confirmed: rollback was described as a bare file edit with no trailer, and missing-config recovery would have reactivated `2.1` after a rollback if the config were later lost. Fixed via a durable `Workflow-Rollback` trailer and a "latest event by ancestry" recovery rule; WF8b's synthetic-item pointer now has an explicit save/restore/resume/interrupted-recovery design. |
| GPT-R9-008 | **Accepted** | D2 | Confirmed: the proposed "detects it is not being invoked as a step of another command" self-check named no actual Claude Code mechanism. Removed; the guard is now exactly two named, real controls (`disable-model-invocation` + non-empty, non-reused `user_confirmation`). |
| GPT-R9-009 | **Fixed at the code level** | `scripts/workflow_fingerprint.py` | See Round 9 disposition above; not re-litigated here. |
| GPT-R9-010 | **Fixed at the code level** | `scripts/workflow_fingerprint.py` | See Round 9 disposition above; not re-litigated here. |
| GPT-R9-011 | **Accepted, deferral restated precisely** | D-Registry | Confirmed: the plan-stage protected set is still a hardcoded constant. Round 9 is right that full generalization (deriving it from validated work-item metadata) remains real future work — restated explicitly as WF4a-i's remaining scope rather than left implicit. The two new JSON files are added to the concrete set now, satisfying the immediate acceptance criterion (they are protected) without pretending the general mechanism exists yet. |
| GPT-R9-012 | **Accepted** | `docs/ai-workflow/WORKFLOW_V2_AUDIT.md` | Confirmed: the audit's CI row and scope framing predated the v2.2 deferral and the hermetic-only CI decision. Updated (see below). |
| GPT-R9-013 | **Accepted (optional)** | D3, D-Self-Governance | Confirmed: `kind: "synthetic"` appeared only in prose, never in D3's actual field list. Added `work_item_kind` as a real, validated schema field. |
| GPT-R9-014 | **Fixed at the code level** | `scripts/workflow_fingerprint.py` | See Round 9 disposition above; not re-litigated here. |
| GPT-R9-015 | **Accepted** | D-Bundle-Manifest | Confirmed: "any consumer, a reviewer or `/approve-review`, stops" would block the external reviewer the bundle exists to serve. Split: worktree/HEAD recorded as diagnostic metadata for external review; equality enforced only by repository-local commands. |
| GPT-R9-016 | **Accepted** | D-Functional-Remediation, D3 (`parent_work_item_id`) | Confirmed: "draft a remediation plan, route through review" left unstated whether the original approved registry/mapping get mutated. Fixed via a child work item with its own registry/mapping and an explicit parent-acceptance-blocks-on-incomplete-child rule. |
| GPT-R9-017 | **Accepted** | `.ai-review/current/TEST_RESULTS.md`, `REVIEW_REQUEST.md` | Confirmed directly (the same instance `GPT-R9-001` cites). Both files regenerated this round with no self-referential ID embedded outside `MANIFEST.md`. |

## Round 10 finding disposition (revision 9)

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R10-001 | **Accepted** | `scripts/workflow_fingerprint.py` (`PLAN_STAGE_EXCLUDED_PATHS`/`PREFIXES`), D-Fingerprint | Confirmed by direct interpreter invocation: `classify_path` on `.claude/commands/bootstrap-workflow-v2.md` raised `UnclassifiedPathError` against the then-current lists. Widened both lists to cover every path this milestone's own checkpoints are known to create; added `test_055`/`056`/`057` reproducing the exact end-to-end scenario. |
| OPUS-R10-002 | **Accepted, via the plan's own alternative (b)** | D-Bootstrap | Confirmed: WF2 (the checkpoint building the stated replacement) is two checkpoints after the stated retirement point. Fixed by never retiring the bootstrap command until `MILESTONE_COMPLETE` — no transfer point to get wrong. |
| OPUS-R10-003 | **Accepted, resolved by the same fix as `-002`** | D-Bootstrap, D-Self-Governance | Confirmed: the dual-mode branch keyed on `governing_workflow_version` would route this milestone's own permanently-`"1"` item to the v1 branch. Since the bootstrap command never hands off, `/milestone-implement` is never invoked for this item at all — the version gate is never reached. |
| OPUS-R10-004 | **Accepted** | D-Bootstrap | Confirmed against D3's actual per-item field list, which the described one-time handoff did not populate. Replaced with a continuous per-invocation state-sync that reconstructs every required field, including `plan_approval` derived from WF0's trailer with `basis: USER_OVERRIDE`. |
| OPUS-R10-005 | **Accepted** | D-Legacy | Confirmed: no section named a selector, and a dormant item with `active_work_item_id` unset had no way to be targeted. Added an explicit, optional work-item-id argument to `/prepare-functional-review`; the `LEGACY_READY` scan runs before version branching, against the resolved target. |
| OPUS-R10-006 | **Accepted** | `docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`, D3 (validator rule) | Confirmed by machine query: `WFR-15`/`WFR-28` unmapped, `WF8a-i` unowned. Reassigned `WFR-15`→WF1b, `WFR-28`→WF0, added `WFR-36` for WF8a-i; re-queried, coverage now bidirectionally complete; added a validator rule enforcing it going forward. |
| OPUS-R10-007 | **Accepted** | D3 | Confirmed: `validate_work_item_type` unconditionally rejects anything outside `{"process","product"}`, and D3 assigned synthetic items `null`. Synthetic items now get `work_item_type: "process"`; `work_item_kind: "synthetic"` alone determines routing. |
| OPUS-R10-008 | **Accepted** | D3 (`WORKTREE_IDENTITY.json`) | Confirmed: a single flat `expected_dirty_paths` list would be overwritten by a second work item's own `IN_PROGRESS` transition. Keyed by work item: `expected_dirty_paths_by_work_item`. |
| OPUS-R10-009 | **Accepted** | D-Selection | Confirmed: rule 2 named "registry table order" while D-Registry makes the JSON authoritative and the table an unhashed view. Selection now names the JSON array explicitly; a view/JSON agreement conformance check added. |
| OPUS-R10-010 | **Accepted** | D2 | Confirmed: the novelty check would reject a legitimate identical repeat and pass any one-character-varied automated string. Replaced with a specificity check (confirmation must name the exact work item and stage). |
| OPUS-R10-011 | **Accepted** | D-Legacy | Confirmed: the imported entry's unset `governing_workflow_version` would either fail the creation gate or require a silent exception. Set to `"1"` at import (factually correct); adoption transitions it to `"2.1"`. |
| OPUS-R10-012 | **Accepted** | D-Bootstrap | Confirmed: the one-commit justification ("both would capture the same reviewed tree") is inaccurate — the approved content is five protected paths, the commit contains more. Added the per-invocation plan-content snapshot comparison as the bootstrap-window's durability guard. |
| OPUS-R10-013 | **Accepted** | Checkpoint registry, `docs/ai-workflow/registry/workflow-v2-1-core-registry.json` | Confirmed: WF-M8 combined a data import with a command-behavior change at complexity 6/one session, with genuinely different dependencies. Split into `WF-M8a` (import, depends on WF1b only) and `WF-M8b` (adoption, depends on WF-M8a/WF4a-ii/WF4a-iii). |
| OPUS-R10-014 | **Accepted (optional)** | D2 | Confirmed: `LEGACY_V1` always backfills a content ID, so `no_content_id` is unreachable. Removed from the vocabulary. |
| OPUS-R10-015 | **Accepted (optional)** | D3 | Confirmed: `work_items` is keyed by ID, so "two work items with the same ID" cannot survive parsing. Restated as the meaningful check: an entry's inner `work_item_id` must equal its map key. |
| OPUS-R10-016 | **Accepted (optional)** | D-Bootstrap | Confirmed: the dual-mode exemption left the bootstrap command with no conformance coverage despite driving six-plus checkpoints. Added to WF8a-ii's scope: a dedicated test for its one-checkpoint-and-stop, never-reads-`ACTIVE_MILESTONE.md` behavior. |
| OPUS-R10-017 | **Accepted (optional)** | `scripts/workflow_fingerprint_test.py` | Confirmed `PLAN_STAGE_EXCLUDED_PREFIXES` was empty pre-`OPUS-R10-001`. Now populated; `test_037`'s trailing-slash validation confirmed still passing against the real, non-empty constant. |

## Round 11 finding disposition (revision 10)

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| GPT-R11-001 | **Accepted** | D-Plan-Review-Stages (new), D-States | Confirmed: `AWAITING_PLAN_APPROVAL`'s entry condition reads only "the most recently reviewed round['s]... status," satisfied by a single `APPROVE` regardless of reviewer role. Fixed by inserting `AWAITING_LOCAL_PLAN_REVIEW`/`AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` between `REVISING_PLAN` and `AWAITING_PLAN_APPROVAL`, scoped to `governing_workflow_version: "2.1"` work items only. |
| GPT-R11-002 | **Accepted** | D-Plan-Review-Stages, D3 (`plan_review_stages`) | Confirmed: `REVIEW_FEEDBACK.md` is a single mutable file with no field recording reviewer role; a second reviewer's feedback overwrites the first's with no trace of the first having occurred. Fixed via a durable per-work-item ledger keyed by `review_content_id`, recording both roles' `bundle_id`/verdict/round/timestamp, valid only while its stored `review_content_id` matches the freshly recomputed one. |
| GPT-R11-003 | **Accepted** | D-Plan-Review-Stages, `/apply-plan-review` (WF4a-iv scope) | Confirmed directly against `.claude/commands/apply-plan-review.md`'s own step 7 — the exact command that produced this revision — which reports the plan ready for implementation whenever `Status != BLOCK` and no "major structural change" was judged. Fixed: under `governing_workflow_version: "2.1"` only, the command never self-declares readiness; it always returns to `AWAITING_LOCAL_PLAN_REVIEW` after an accepted plan edit. The v1 branch (this command's current, unmodified behavior) is unchanged. |
| GPT-R11-004 | **Accepted** | Missing tests (item 26) | Confirmed by reading items 26 and 74 side by side: item 26 still said "an empty or reused `user_confirmation` is rejected," describing the novelty check `OPUS-R10-010` replaced two rounds ago; item 74 already states and requires the replacement (specificity) rule, creating a direct, uncaught contradiction. Item 26 restated to match. |
| GPT-R11-005 | **Accepted** | D-Plan-Review-Stages (`/review-plan` command contract) | Confirmed: no prior text specified `/review-plan` beyond the round's own procedural request. Full contract written: resolution, phase precondition, reads, identity recomputation, independent verification, sole write target, provenance fields, non-modification guarantee, and named failure-mode behavior. |
| GPT-R11-006 | **Accepted** | D-Plan-Review-Stages (bundle rebinding rule) | Confirmed: no rule stated whether a bundle regenerated after local approval is "the same" artifact the manual reviewer must see. Fixed: the ledger keys stage validity by `review_content_id`, not `bundle_id`; a wrapper-only regeneration (new `bundle_id`, unchanged `review_content_id`) does not invalidate the local stage, but the manual-external stage must be performed against the bundle actually uploaded, whose `bundle_id`/`review_content_id` `/review-plan`'s own report displays for the user to hand off. |
| GPT-R11-007 | **Accepted** | D-Plan-Review-Stages ("after any subsequent plan edit") | Confirmed: "repeat the relevant review steps" named no destination state. Fixed: stated as a literal sequence (edit → `AWAITING_LOCAL_PLAN_REVIEW` → `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` → `AWAITING_PLAN_APPROVAL`), with no path that re-enters manual-external review without a fresh local-review pass first. |
| GPT-R11-008 | **Accepted** | `scripts/workflow_fingerprint.py` (`PLAN_STAGE_PROTECTED`), D-Plan-Review-Stages | Confirmed: the guide's path did not exist in any classification list. Named (`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`) and added to `PLAN_STAGE_PROTECTED` this round — the one classification addition round 10's freeze instruction explicitly permits (its own acceptance criterion 13). Content deferred to `WF4a-iv` (see the Round 11 disposition narrative above for why). |
| GPT-R11-009 | **Accepted** | Checkpoint registry (`WF4a-iv`, new), Requirements traceability (`WFR-37`–`WFR-40`) | Confirmed: no checkpoint owned the protocol/command/guide as a unit. New checkpoint `WF4a-iv` (depends on `WF4a-ii`) owns all of it; `WF8a-ii` gains `WF4a-iv` as a dependency for its dual-mode/integration coverage. Four new requirements added, each mapped to `WF4a-iv` (or `WF8a-ii` for the cross-checkpoint tests), keeping bidirectional coverage complete. |
| GPT-R11-010 | **Accepted** | Missing tests (items 18, 19a, 19b, 24) | Confirmed by direct grep: all four items still read `→ WF-M8`, a checkpoint ID `OPUS-R10-013` removed by splitting into `WF-M8a`/`WF-M8b`. Corrected: 18/24 (import/coexistence assertions) → `WF-M8a`; 19a/19b (adoption assertions) → `WF-M8b`. |
| GPT-R11-011 | **Accepted (optional)** | D-Plan-Review-Stages | Role-oriented terms (`local_model_plan_review`, `manual_external_plan_review`) used in every state/schema/test reference; "Opus"/"ChatGPT" appear only as the recommended tools in narrative prose, never in a state name, field name, or validator rule. |
| GPT-R11-012 | **Accepted (optional)** | D-Plan-Review-Stages | The correctness gate (ledger entries, `review_content_id` match) is entirely repository-state-based; "run this in a fresh session" is stated as operational guidance for genuine independence, not a claim any mechanism verifies or depends on. |

## Round 12 finding disposition (revision 11)

Round 12 (a manual external reviewer, `Status: REVISE`, `GPT-R12-001`
through `-006`) reviewed revision 10's own new `D-Plan-Review-Stages`
section and found it "not yet internally executable": the state machine's
entry conditions and the actual command write-sets it described did not
agree with each other, so the two-stage protocol revision 10 introduced
could not actually run end to end without an out-of-band edit. Every
finding was independently re-verified against this document's own then-
current text before being accepted, per this command's validation
requirement.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| GPT-R12-001 | **Accepted** | D-Plan-Review-Stages (`AWAITING_LOCAL_PLAN_REVIEW`'s exit, transition table) | Confirmed directly against revision 10's own text: `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`'s entry condition read "a `local_model_plan_review` stage completed... with a status of `REVISE` or `APPROVE`" — a local `REVISE` satisfied entry to manual-external review exactly as an `APPROVE` did, contradicting the operator guide's own "local review repeats before manual external review" framing. Fixed: verdict-specific exits stated for both stages; only a local `APPROVE` writes a ledger entry or transitions to `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`; `REVISE` transitions to `REVISING_PLAN`; `BLOCK` stays put. |
| GPT-R12-002 | **Accepted** | D-Plan-Review-Stages (`/review-plan`'s write set, new `/record-manual-plan-review` command) | Confirmed by reading revision 10's own text side by side: `AWAITING_LOCAL_PLAN_REVIEW`'s exit said `/review-plan` "records the completed stage in the ledger" (a `WORKFLOW_STATE.json` write), while `/review-plan`'s own contract said it "writes only... `REVIEW_FEEDBACK.md`... never... state" — directly contradictory, and no command was ever named as the manual stage's writer at all, leaving both new phases unreachable via the documented happy path. Fixed: `/review-plan`'s write set corrected to include its own local-stage ledger fields and phase transition for an `APPROVE` verdict; a new, small, mechanical, model-independent command, `/record-manual-plan-review`, named as the manual stage's sole writer, explicitly not a user-authority gate and explicitly not folded into `/approve-review plan`. |
| GPT-R12-003 | **Accepted** | D-Plan-Review-Stages (verdict/state transition table) | Confirmed: `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`'s exit named no writer, no per-verdict destination, no `BLOCK` handling, and no malformed-feedback rejection — three plausible, mutually-inconsistent implementations could all claim conformance. Fixed: an explicit six-row transition table (both stages × `APPROVE`/`REVISE`/`BLOCK`) naming the writer and validation preconditions for every row, exactly as requested. |
| GPT-R12-004 | **Accepted** | D3 (`plan_review_stages` schema note) | Confirmed: the ledger was described both as invalidated-by-recomputation (implying only current-content validity matters) and, via `/review-plan`'s report requirement, as evidence "the ordered review occurred" (implying audit history) — and `REVIEW_FEEDBACK.md` is confirmed gitignored (`.gitignore:1`), so no git history exists to fall back on for either role's per-round feedback. Fixed: the ledger is now stated explicitly as a current-content gate record only (only `APPROVE` is ever recorded, at either stage); round-by-round `REVISE`/`BLOCK` history is stated as a deliberate v2.1-core scope cut, the same kind of deferral D3 already makes for between-checkpoint attribution. |
| GPT-R12-005 | **Accepted** | D-Plan-Review-Stages (`AWAITING_LOCAL_PLAN_REVIEW`'s stop condition) | Confirmed: the same subsection said both "a fresh session is required to start `/review-plan` meaningfully" and, two paragraphs later (`GPT-R11-012`'s own resolution), "not a mechanism anything here verifies" — an unenforceable precondition stated as a hard requirement. Fixed: restated consistently as strongly-recommended operational guidance; no command contract or validator rule depends on session freshness. |
| GPT-R12-006 | **Accepted** | Checkpoint registry (`WF4a-iv`) | Confirmed: `WF4a-iv` already owned two states, the ledger schema/validator, `/review-plan`, the revised `/apply-plan-review` exit step, the operator guide, and 11 missing-test items at complexity 6/1 session before this round; resolving `GPT-R12-001`–`-003` added a new command, a transition table, and 6 more test items. Fixed via the plan's own stated alternative to a checkpoint split: session target widened to 1-2 (the same target `WF8a-ii` already uses at the same complexity) with a mandatory internal commit boundary between the state/ledger/validator/transition-table layer and the command/guide layer, rather than a new checkpoint ID that would also require re-plumbing registry/mapping bidirectional coverage for no new subsystem. |

**Scope discipline confirmed**: per round 12's own instructed scope limit,
only the two-stage plan-review protocol, its command ownership, tests,
traceability entries, and checkpoint sizing were touched this round. No
change to `scripts/workflow_fingerprint.py`'s identity algorithm or
`PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_*` beyond what round 10 already
added; no fingerprint, migration, legacy-import, implementation-review, or
product-workflow design reopened. The 67 hermetic tests and 3
real-repository/integration tests all still pass — see
`.ai-review/current/TEST_RESULTS.md`.

**Round 13 (not separately dispositioned)**: an external reviewer approved
the round-13 bundle (this document's then-current revision 11). Its
`REVIEW_FEEDBACK.md` was overwritten on disk by round 14's feedback before
this command ran, so its exact original text is not independently
restatable here — consistent with this document's own standing practice
(see the note under "Round 6 finding disposition" above) of never
fabricating historical text it does not actually have. What survives is
round 14's own account of it (quoted and addressed in the disposition
below): round 13's substantive judgment that the state-machine blockers
`GPT-R12-*` raised were resolved is independently re-confirmed by round 14
and not reopened here; round 13's approval itself is superseded by round
14's overturn, for the four mechanical reasons in `OPUS-R14-001`–`-004`
below.

## Round 14 finding disposition (revision 12)

Round 14 (an independent staff-level reviewer, `Status: REVISE`,
`OPUS-R14-001` through `-012`) re-verified every prior round's blocking
finding as a regression (all confirmed still resolved — `OPUS-R10-001`
through `-017`, spot-checked directly against the current text and, for
`-001`, replayed end to end in a scratch repository) and then found the
round-13 approval itself could not stand, for reasons the reviewer
characterizes as "mechanical rather than matters of judgement": the
approved plan could not be implemented without invalidating its own
approval (`OPUS-R14-001`), the approving feedback bound to no verifiable
artifact (`OPUS-R14-002`), the approval's own mandated post-approval edits
would have staled it with no state-machine path back (`OPUS-R14-003`), and
the document disagreed with itself about its own revision number
(`OPUS-R14-004`). Every finding was independently re-verified against this
document's and the fingerprint prototype's actual text/behavior before
being accepted, per this command's validation requirement.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R14-001 | **Accepted** | D-Plan-Review-Stages (operator guide reclassified), D-States (invariance restated as an obligation), `scripts/workflow_fingerprint.py` (`PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_PATHS`) | Confirmed directly: `D-States` (`:1245–1246`) stated plan-stage durability "invariant under checkpoint commits, because checkpoints never touch those documents," while `PLAN_STAGE_PROTECTED` included `docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md` and `WF4a-iv`'s own registry entry named creating that exact file as a deliverable — jointly unsatisfiable, reproduced by running the hermetic suite's `ScratchRepo` sequence before and after simulating the guide's creation. Fixed: the guide moved from `PLAN_STAGE_PROTECTED` to `PLAN_STAGE_EXCLUDED_PATHS` (same treatment as `MILESTONE_WORKFLOW.md`/`REVIEW_PROTOCOL.md` — documentation of the approved design, not the design itself), so `WF4a-iv` writing it never changes `review_content_id`. `test_079` corrected; `test_101` (new) simulates a full checkpoint-artifact sequence and asserts `review_content_id` is unchanged after every step. |
| OPUS-R14-002 | **Accepted** | D-Bootstrap (new binding-field requirement) | Confirmed: `D-Bundle-Manifest` states `/approve-review` "rejects feedback whose bundle ID is missing... naming both," but `/approve-review` doesn't exist during this milestone's own bootstrap window, and nothing in `D-Bootstrap` required the v1-native approving feedback to carry `Reviewed bundle ID:`/`Reviewed base commit:`/`Work item:` at all — confirmed by grep, the round-13 feedback (before it was overwritten) carried none of the three, per round 14's own quoted evidence. Fixed: `D-Bootstrap` point 3 (new) requires all three fields before WF0 creates its commit, refusing and naming which is missing/mismatched otherwise; `reviewed_bundle_id` is now explicitly sourced from that field, never invented from whatever bundle is on disk. |
| OPUS-R14-003 | **Accepted** | Missing-test item 85 (corrected to match item 96) | Confirmed: item 85 still read "writes only the resolved work item's `REVIEW_FEEDBACK.md`," the pre-`GPT-R12-002` claim, directly contradicting item 96's already-corrected write set two items later — an oversight from applying `GPT-R12-002` to the prose and the transition table but not to this one leftover missing-test item. Fixed: item 85 restated word-for-word against item 96's write set rather than left as a second, driftable copy. |
| OPUS-R14-004 | **Accepted** | Title/status line | Confirmed directly: the title read "Revision 10" and the status line "round 12 bundle" while every other section (revision history, decisions, traceability, registry, missing tests) already said revision 11/round 13 — an oversight in the prior round's own edit (the title/status line at the very top of the document was never touched when the rest was bumped). Fixed: now "Revision 12"/"round 15 bundle," matching this round's own bump. The "make `plan_revision` machine-readable, sourced from the registry JSON rather than caller-supplied" half of the suggested fix is **not applied** — see "Decisions not taken" below for the evidence-based reason. |
| OPUS-R14-005 | **Accepted** | D-Plan-Review-Stages (transition table, `/record-manual-plan-review` contract) | Confirmed: the manual-`APPROVE` row required "`bundle_id`/`review_content_id` match the current recomputed values," while `GPT-R11-006`'s own resolution (`:676`, unchanged text) states "a wrapper-only regeneration... does not invalidate the local stage" — the manual stage is the one with an unavoidable multi-hour-plus gap between upload and pasted feedback, making it the most exposed to exactly this contradiction. Fixed: `review_content_id` equality is hard (blocks ingestion); `bundle_id` equality is advisory (warns, naming both, never blocks); the ledger records the feedback's actual `bundle_id` regardless, so the audit trail keeps what the reviewer really saw. |
| OPUS-R14-006 | **Accepted** | `scripts/workflow_fingerprint.py` (`AbsentProtectedPathError`, both plan-stage manifest builders) | Confirmed: `_snapshot_worktree`/`_snapshot_commit` return `{"exists": False, ...}` for a missing path and `compute_review_content_id_plan_stage` completed normally over it — demonstrated live against the then-unwritten guide path, and every *other* unsupported condition in the module fails closed by contrast (`UnclassifiedPathError`, `UnsupportedPathTypeError`, etc.). Fixed: both plan-stage manifest builders now raise `AbsentProtectedPathError`, naming the path, when a protected path doesn't exist — scoped to plan stage only, since the (not yet built) implementation-stage projection legitimately represents deletions as tombstones. `test_050` restated (Case 2 now raises instead of tombstoning); `test_102` (new) covers both the worktree- and commit-source builders independently. |
| OPUS-R14-007 | **Accepted, resolved together with `OPUS-R14-003`** | Missing-test item 85 | Same fix as `OPUS-R14-003` — item 85, item 96, and the transition table now state the same write set. |
| OPUS-R14-008 | **Accepted** | D-Bootstrap (new role-rejection requirement) | Confirmed: the round-13 approving feedback (per round 14's quoted evidence) declared `Reviewer role: manual_external_plan_review`, a `"2.1"`-only vocabulary term (`D-Plan-Review-Stages`), for a work item permanently fixed at `governing_workflow_version: "1"` (`D-Self-Governance`) — a role this item can never actually have a corroborating local-stage ledger entry for. Fixed: `D-Bootstrap` point 4 (new) has WF0 refuse a feedback file declaring a `"2.1"`-only stage role for this permanently-v1 item, naming the item's actual governing version. |
| OPUS-R14-009 | **Accepted, resolved together with `OPUS-R14-001`** | D-Plan-Review-Stages, `scripts/workflow_fingerprint.py` | Same fix as `OPUS-R14-001` — excluding rather than protecting the guide means its content is no longer part of what a plan approval binds to at all, so there is nothing left to "approve sight-unseen." The deferred-content decision itself (`WF4a-iv` writes it, not this round) is unchanged and still deliberate — see the Round 11 disposition narrative. |
| OPUS-R14-010 | **Already resolved, re-confirmed** | D3 (validator rule, added `GPT-R12-001`/`-003`) | Confirmed: D3's validator rejects list (`:1783` area) already states "a stage sub-record whose `verdict` is anything other than `APPROVE`... is corrupt," added last round specifically because only `APPROVE` is ever legitimately recorded. The optional finding's own concern (a future contributor writing `REVISE` into the field) is exactly what that rule catches; no further action needed. |
| OPUS-R14-011 | **Accepted (optional), deferred to `WF4a-iv`** | Missing-test item 111 (below) | The suggestion (source `round` from the registry JSON alongside a machine-readable `plan_revision`) is reasonable but is itself a `WF4a-iv`-scope mechanism — the ledger's `round` field is written by `/review-plan`/`/record-manual-plan-review`, neither of which exists yet. Recorded as a missing-test obligation (item 111) rather than implemented now, consistent with every other not-yet-built-command obligation in this list. |
| OPUS-R14-012 | **Accepted (optional), deferred to post-approval** | — | Reasonable, and the finding's own suggested timing ("once approved, move rounds 5–11 into `docs/ai-workflow/archive/`") is the right one: archiving mid-review would change the protected artifact's content for reasons unrelated to any open finding, adding review surface this round doesn't need. Deferred to immediately after this document's next approval, tracked here so it isn't lost. |

**Decisions not taken, with evidence**: `OPUS-R14-004`'s suggested
follow-on — sourcing `plan_revision` from the registry JSON instead of a
caller-supplied argument — is not applied this round. The mandatory half
(fixing the label so every revision statement agrees) is applied in full.
The mechanization half would change `compute_review_content_id_plan_stage`'s
calling convention, which is itself part of the frozen identity subsystem
round 10 explicitly instructed not to reopen, restated by round 14's own
closing paragraph: "do not reopen the frozen identity subsystem... all are
sound as reviewed." The only place `plan_revision` is currently supplied
is the demonstration harness (`scripts/workflow_fingerprint.py`'s
`__main__` block and `workflow_fingerprint_demo_test.py`, both inside the
excluded `scripts/` prefix) — a real production caller (a future
`/apply-plan-review`) doesn't exist yet to source it from anywhere else,
so there is no live inconsistency to fix by mechanizing this today, only
a discipline to maintain by hand (as this round does) until one exists.
Revisiting this is appropriately `WF4a-i`'s scope (the checkpoint that
already owns wiring the prototype into real commands) once that caller is
real.

**Update (revision 14, `OPUS-R18-003`)**: this deferral is reversed, not
because the reasoning above was wrong at the time, but because its own
stated condition stopped holding — `__main__` became a real caller that
writes the authoritative manifest with the stale literal this paragraph
predicted would eventually need fixing. `plan_revision` is now sourced from
the registry JSON (`load_plan_revision()`); see "Round 18 finding
disposition" and D-Fingerprint's "Generator contract" above. This
paragraph is left otherwise unchanged as the historical record of why
revision 12 made the choice it did.

**Scope discipline confirmed**: per round 14's own instructed scope limit,
only the findings above were addressed. `D-Plan-Review-Stages`' transition
table structure, the registry/mapping structure, the Milestone 8 migration
path, and the fingerprint identity algorithm itself are all unchanged
beyond the two named, narrowly-scoped fixes above (`PLAN_STAGE_PROTECTED`/
`PLAN_STAGE_EXCLUDED_PATHS` reclassification, `AbsentProtectedPathError`).
69 hermetic tests (67 → 69: `test_079` corrected in place, `test_101`/
`test_102` new) and 3 real-repository/integration tests all pass — see
`.ai-review/current/TEST_RESULTS.md`.

**Round 15 (not separately dispositioned)**: an external reviewer approved
the round-15 bundle (revision 12), carrying all three binding fields
(`OPUS-R14-002`'s own requirement, satisfied in practice). Its substantive
judgment — that the `OPUS-R14-*` fixes were resolved and no design round
was needed — is independently re-confirmed by round 16 below and not
reopened. The approval itself is superseded by round 16's overturn, for
the one blocking reason in `OPUS-R16-001`.

## Round 16 finding disposition (revision 13)

Round 16 (the same independent staff-level reviewer as round 14, `Status:
REVISE`, `OPUS-R16-001` through `-005`) re-verified every round-14 fix by
execution — recomputing both identifiers from the directory and the
extracted archive, probing `AbsentProtectedPathError` directly in both
plan-stage builders, cross-reading items 85/96 against the transition
table, and checking all 17 checkpoints' declared deliverables against the
protected set — and found one genuine reachability/approval-validity
contradiction the design still had, plus retracted its own round-14
suggestion for `OPUS-R14-012`'s archival timing after tracing it into the
same defect class as `OPUS-R14-003`. Both findings were independently
re-verified against this document's and the fingerprint prototype's actual
text/behavior before being accepted.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R16-001 | **Accepted** | `scripts/workflow_fingerprint.py` (`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES`), D-Fingerprint (second classification obligation stated) | Confirmed directly: `classify_path("docs/ACTIVE_MILESTONE.md", ...)` raised `UnclassifiedPathError` against the pre-fix lists — reproduced live. `D1`'s own text confirms `active_work_item_id` is "a resume-focus pointer, not an execution lock" and adoption may proceed while `workflow-v2-1-core` is mid-implementation; `.claude/commands/prepare-functional-review.md`/`accept-milestone.md` confirm the actual write targets (`docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md`, `docs/milestones/completed/`). Fixed: four new exclusion entries with justifications in the existing form, plus the general obligation stated explicitly in D-Fingerprint so the next work item inherits it rather than rediscovering it by incident. `test_117_120_...` (new) simulates all four while this work item is itself mid-sequence. |
| OPUS-R16-002 | **Accepted, self-correction acknowledged** | Self-review notes (archival retimed) | Confirmed directly: the round-14 disposition table's own `OPUS-R14-012` row scheduled the archival for "immediately after this document's next approval" against a protected path (`WORKFLOW_V2_PLAN.md`), which the unconditional recomputation rule (`D-Plan-Review-Stages`, `D-Approval-Commits`) would have staled the moment it ran — the same shape as `OPUS-R14-003`, which this same revision had just fixed for item 85. The reviewer's own round-14 suggestion produced the defect; the round-16 finding retracts it. Fixed: retimed to at-or-after `MILESTONE_COMPLETE`, where no live approval depends on the plan doc's content. |
| OPUS-R16-003 | **Accepted (optional), recorded, no action** | D-Fingerprint (implicit in the plan-stage/implementation-stage split already stated) | The finding's own conclusion: "No change required now." `compute_review_content_id_plan_stage_at_commit(base_commit)` correctly has no legitimate caller yet — the post-approval parity check compares against the *approval* commit, not the base, where all protected paths already exist by construction. Noted here for WF4a-i to read before generalizing protected-path derivation. |
| OPUS-R16-004 | **Accepted (optional), deferred to WF0** | — | Reasonable; the finding's own suggested mitigation (note which sections are historical-only) is cheap and belongs at WF0's own commit, alongside the plan/audit pair it commits — not a mid-review edit to protected content for a documentation-quality reason unrelated to any open finding, the same discipline `OPUS-R14-012`'s original mistake should have followed. |
| OPUS-R16-005 | **Accepted (optional), deferred to WF5** | — | Reasonable; `REVIEW_PROTOCOL.md` is excluded, not protected (WF5's bundle-mechanics scope, D-Bundle-Manifest), so adding the three binding-field lines to its template is real future work but not this round's — WF5 owns that file. |

**Scope discipline confirmed**: per round 16's own instructed scope limit
("a two-list-entry and one-sentence round"), only `OPUS-R16-001`/`-002`
were addressed as required fixes; `-003`/`-004`/`-005` are recorded and
deferred, not implemented. `D-Plan-Review-Stages`, the registry/mapping
structure, the Milestone 8 migration path, the bootstrap design, checkpoint
sizing, and the identity algorithm itself are all unchanged beyond the
classification-list additions and the general rule statement above. 70
hermetic tests (69 → 70: `test_117_120_...` new, `test_096`'s known-path
list extended) and 5 real-repository/integration tests all pass — see
`.ai-review/current/TEST_RESULTS.md`.

## Round 18 finding disposition (revision 14)

Round 18 (a fourth independent staff-level reviewer, `OPUS-R18-*`,
`Status: REVISE`) re-verified revision 13's round-16 fixes as genuinely
resolved — all four exclusion entries classify correctly, `bundle_id`
reproduces from the extracted archive, the registry remains a valid
topological order — and found two blocking problems in the *generator*
around the frozen identity algorithm, plus three important gaps in its
application and completeness.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R18-001 | **Accepted** | `scripts/workflow_fingerprint.py` (`write_manifest_with_verified_identifiers`, `ReviewContentIdNotIdempotentError`) | Confirmed directly: recomputing `review_content_id` against the round-17 bundle at `plan_revision=13` yielded `a0bbfff2...`, not the manifest's stated `9144ddb7...`, and no integer or string revision reproduced it — while `bundle_id` reproduced exactly, proving the mismatch was in `review_content_id`'s own generation discipline, not bundle truncation. Root cause: `__main__` computed `review_content_id` once and never re-verified it, unlike `bundle_id`'s existing write-then-recompute check. Fixed: `write_manifest_with_verified_identifiers` computes both identifiers, writes, then recomputes **both** and raises (`ReviewContentIdNotIdempotentError`/`BundleIdNotIdempotentError`) on any mismatch before returning — the same discipline round 8 established for `bundle_id`, now applied symmetrically. |
| OPUS-R18-002 | **Accepted** | `scripts/workflow_fingerprint.py` (`__main__` split into read-only default + `--write-manifest`) | Confirmed directly: running `python3 scripts/workflow_fingerprint.py <base>` rewrote `.ai-review/current/MANIFEST.md` twice as an unconditional side effect of `__main__`, changing both identifiers with no warning — the reviewer had to restore the file from the archive. Fixed: `__main__` now contains no unconditional `.write_text()` call at all (`test_130`); the default invocation only computes and prints, including a read-only recomputed `bundle_id` for an *existing* bundle directory compared against what `MANIFEST.md` already states; writing requires the explicit `--write-manifest` flag, which routes to the one function (`write_manifest_with_verified_identifiers`) allowed to write. |
| OPUS-R18-003 | **Accepted** | `scripts/workflow_fingerprint.py` (`load_plan_revision`, `PlanRevisionMismatchError`), registry JSON (`plan_revision` field) | Confirmed directly: `__main__`'s hardcoded `plan_revision=12` (line 878 of the pre-fix source) was already one revision stale against the plan's own "Revision 13" title, and `__main__` is exactly the real caller round 15's deferral ("no real caller exists yet") was waiting for. Fixed: `docs/ai-workflow/registry/workflow-v2-1-core-registry.json` (already protected, already machine-written) gained a `plan_revision` field; `load_plan_revision()` reads it and asserts it matches the plan title's `(Revision N)` marker, raising `PlanRevisionMismatchError` on disagreement. Round 15's deferral is reversed, not reopened on general principle — the fact it rested on (no real caller) no longer holds. |
| OPUS-R18-004 | **Accepted** | `scripts/workflow_fingerprint.py` (`PRODUCT_SCOPE_JUSTIFICATION`, new exact/prefix exclusion entries) | Confirmed directly: probing `app/src/main/Foo.kt`, `docs/DOMAIN_GLOSSARY.md`, `docs/adr/0003-...md`, `AGENTS.md`, `.github/copilot-instructions.md`, `README.md`, `gradle/libs.versions.toml`, and `config/detekt/detekt.yml` against the round-17 lists all raised `UnclassifiedPathError` — exactly the paths `FIXING_FUNCTIONAL_FINDINGS` (a real `MILESTONE_WORKFLOW.md` state) writes when Milestone 8 is adopted mid-implementation, one step past the checklist write `OPUS-R16-001` already fixed. Fixed: `app/`, `docs/adr/`, `docs/agent-context/`, `gradle/`, `config/`, `.github/` (prefixes) and `AGENTS.md`, `README.md`, `docs/PROJECT_BRIEF.md`, `docs/DOMAIN_GLOSSARY.md`, `docs/UX_FLOWS.md` (exact paths) excluded under one shared `PRODUCT_SCOPE_JUSTIFICATION` string rather than per-entry justifications, per the finding's own suggested fix. Fail-closed is preserved: `test_057`/`test_034`/`test_035`/`test_095`, which had used `app/...` as their novel-path fixture, now use a path outside every named set instead, and still raise. `test_133`/`test_134`/`test_135` (new) cover concurrent product-code, product-doc, and genuinely-novel paths respectively. |
| OPUS-R18-005 | **Accepted** | `scripts/workflow_fingerprint.py` (`assert_review_request_states_review_content_id`), `.ai-review/current/REVIEW_REQUEST.md` | Confirmed: neither `TEST_RESULTS.md` nor `REVIEW_REQUEST.md` stated a `review_content_id` value anywhere in the round-17 bundle — `MANIFEST.md` was the only copy, which is exactly why `OPUS-R18-001`'s drift went undetected through generation and into external review. Fixed: `REVIEW_REQUEST.md` now states `review_content_id: <hex>` as a plain labelled line (not a `bundle_id:`-shaped one — `GPT-R9-001`'s rule is specifically about that field pattern and is not violated), and `write_manifest_with_verified_identifiers` asserts it agrees with the computed value, raising `ReviewContentIdMismatchError`/`MissingReviewContentIdStatementError` otherwise. |
| OPUS-R18-006 | **Accepted (optional)** | `scripts/workflow_fingerprint.py` (`__main__` output rewording) | Reasonable and applied: now that write and read are split, the write path's output states plainly "review_content_id (write -> recompute -> equal)" and "bundle_id (write -> recompute -> equal)" rather than a single overloaded "idempotent" sentence. |
| OPUS-R18-007 | **Accepted (optional), no action needed now** | — | Correct as stated by the finding itself: `OPUS-R16-004`'s WF0-commit-time historical-section marker remains the right deferral, and the archival retiming (`OPUS-R16-002`) means the size persists through implementation — noted, not actioned, consistent with `OPUS-R16-004`'s own disposition. |

**Scope discipline confirmed**: per round 18's own explicit instruction
("do not reopen the identity algorithm, `D-Plan-Review-Stages`, the
registry/mapping structure, `D-Bootstrap`, the Milestone 8 migration path,
or checkpoint sizing"), this revision's changes are confined to the
generator (`__main__`, manifest-writing, `plan_revision` sourcing) and the
classification lists. `compute_bundle_id`/`compute_review_content_id_plan_stage`
are byte-for-byte unchanged from revision 6; only their *callers* changed.
70 → 81 hermetic tests (`test_057`/`test_034`/`test_035`/`test_095`
corrected in place, eleven new for items 126-135/137) and 6 → 8
real-repository/integration tests — see `.ai-review/current/TEST_RESULTS.md`.

## Round 20 finding disposition (revision 15)

Round 20 (a fifth independent staff-level reviewer, `OPUS-R20-*`,
`Status: APPROVE`) re-verified every round-18 fix by execution rather than
by reading the disposition table, found none of them regressed, and
reported zero blocking or important findings. The three items below are
optional and are deferred to `WF4a-i` rather than fixed now, since none has
a real caller yet — the same reasoning applied to every other
not-yet-built-command deferral in this document.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| OPUS-R20-001 | **Accepted (optional), deferred to `WF4a-i`** | `scripts/workflow_fingerprint.py` (`write_manifest_with_verified_identifiers`) | Confirmed directly: the function (`:1064-1077`) writes `MANIFEST.md` twice — a placeholder without `bundle_id`, then with it — and only afterwards recomputes and asserts idempotence for both identifiers (`:1079-1089`). A protected-path edit landing in that window raises loudly but leaves `MANIFEST.md` on disk stating an identifier the function itself just declared wrong. Not fixed now: the window is sub-second, the raise is loud, and the read-only inspection path already reports "DIFFERS" against a stale manifest, so the defect is detectable and recoverable without a corrective release. Recorded as missing-test item 138, owed to `WF4a-i`: write to a temporary file in the bundle directory and `os.replace()` it into place only after both idempotence assertions pass, so a simulated protected-path edit between compute and recompute raises **and** leaves `MANIFEST.md` byte-identical to its pre-invocation state. |
| OPUS-R20-002 | **Accepted (optional), decision recorded, deferred to `WF4a-i`** | D-Fingerprint (classification decision stated explicitly) | Confirmed directly: `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`, `.editorconfig`, and `Makefile` are named in neither `PLAN_STAGE_EXCLUDED_PATHS` nor `PLAN_STAGE_EXCLUDED_PREFIXES`, so each raises `UnclassifiedPathError` under `classify_path`, while `app/` and `gradle/` are already excluded prefixes. **Decision, recorded here rather than left implicit**: root-level build files stay unclassified and fail closed. They change rarely, the error already names the path, and — per the finding's own reasoning — they sit closer to design content than `app/**`/`gradle/**` do, so widening the exclusion set is not free: every additional excluded path is one more path whose change can no longer stale an approval. If a future round needs them excluded (e.g. a real concurrent write raises and blocks unrelated work), add them under `PRODUCT_SCOPE_JUSTIFICATION` then, with the concrete write that forced it as the justification. Recorded as missing-test item 139, owed to `WF4a-i`: a test pinning that a root build-file write still raises `UnclassifiedPathError`, so the fail-closed choice is enforced rather than merely documented. |
| OPUS-R20-003 | **Accepted (optional), deferred to `WF4a-i`** | D-Fingerprint ("Implementation-stage manifest" paragraph) | Confirmed directly: `PLAN_STAGE_EXCLUDED_PREFIXES` excludes `app/`, `scripts/`, `gradle/`, `config/`, `.github/` as *not* approval-critical at the plan stage, while `D-Commit-Provenance`'s freshness rule names "source, test, build, migration, workflow-command file" as exactly the content the implementation-stage manifest exists to protect — the plan-stage set is a near-inverse of what the implementation stage must protect, not a reusable one. Fixed: a sentence added to D-Fingerprint's "Implementation-stage manifest" paragraph (below) stating the two stages' sets must be derived independently, not adapted from one another. Recorded as missing-test item 140, owed to `WF4a-i`: a test asserting the two stages classify a representative source file (e.g. `app/src/main/...`) oppositely — excluded (not approval-critical) at the plan stage, protected at the implementation stage. |

**Scope discipline confirmed**: per round 20's own explicit instruction
("do not reopen the identity algorithm, the read/write split, the
classification lists beyond `OPUS-R20-002`'s single decision,
`D-Plan-Review-Stages`, the registry/mapping structure, `D-Bootstrap`,
`D-Legacy`, or checkpoint sizing"), this revision's changes are confined to
recording the `OPUS-R20-002` classification decision in D-Fingerprint, one
clarifying sentence for `OPUS-R20-003`, and three new forward-looking
missing-test obligations, all owed to `WF4a-i`. No source in
`scripts/workflow_fingerprint.py` changed this revision; the 81 hermetic +
8 real-repository/integration tests are unchanged from revision 14.

## Decisions (revision 15)

### D-Bootstrap — one-time Workflow-v1-to-v2.1 transition (new, resolves GPT-R9-004)

Every other section in this document describes behavior `/approve-review`,
multi-item `WORKFLOW_STATE.json`, and the rest of v2.1's own machinery will
have once built. None of it exists yet in this repository: today's
`/milestone-plan`/`/milestone-implement`/`/apply-plan-review` are the
unmodified v1 commands, `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md` still
point at Milestone 8, and this milestone's own working tree already
contains fully-reviewed, not-yet-committed content (the prototype scripts,
CI wiring, the technical-decision entry, the registry/mapping JSON files,
and this plan/audit pair themselves) with no commit lifecycle defined for
any of it — round 9 correctly found this the actual precondition gap
before implementation can start at all, not a detail deferrable into WF1a.

**Why this cannot be solved by extending an existing command.**
`/milestone-implement` (v1, today) selects work by reading
`docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`, which this milestone is an
explicit non-goal to touch. Modifying `/milestone-implement` itself to
recognize the process plan would require exactly the work-item routing
(D1) that doesn't exist until WF1b lands — circular. The bootstrap path is
therefore necessarily a separate, temporary, narrowly-scoped mechanism,
retired once real infrastructure exists.

**WF0 — the bootstrap checkpoint** (first in the registry, depends on
nothing, complexity 2/Small):

1. **One commit** containing every already-reviewed, currently-uncommitted
   artifact together: the plan/audit pair, the prototype scripts, the CI
   step, the technical-decision entry, and the registry/mapping JSON
   files — carrying both `Workflow-Plan-Approval: <review_content_id>`
   (D-Approval-Commits' normal trailer shape; nothing about the trailer
   mechanism requires `WORKFLOW_STATE.json` to exist, since trailers are
   plain Git metadata) and `Workflow-Checkpoint: WF0` +
   `Workflow-Work-Item: workflow-v2-1-core`. This is a deliberate
   simplification of an earlier two-commit sketch (a separate
   plan-approval commit, then a separate prototype-checkpoint commit):
   both would capture the same already-static, already-reviewed working
   tree with no meaningful boundary between them, so one commit is
   simpler and equally auditable.
2. **The user's explicit go-ahead is this milestone's own plan approval.**
   There is no `/approve-review` to invoke it with; the existing v1
   `apply-plan-review.md` flow this very conversation has been running for
   fourteen-plus rounds — external review, findings resolved, explicit
   user confirmation to proceed — **is** the v1-native equivalent, and
   WF0's commit is the artifact that makes it durable and
   later-discoverable via the trailer, exactly as D-Approval-Commits
   specifies for every subsequent plan approval this milestone's own
   machinery will handle.
3. **The approving feedback must still carry the three binding fields, and
   WF0 refuses without them** (resolves `OPUS-R14-002`): D-Bundle-Manifest
   states that `/approve-review` "rejects feedback whose bundle ID is
   missing... naming both," but that check belongs to a command that
   doesn't exist during the bootstrap window — nothing today mechanically
   enforces it for this milestone's own v1-native approval. That is a
   legitimate exemption from *automation*, not from the *content
   requirement* itself: WF0's commit carries the `Workflow-Plan-Approval`
   trailer that every later `/approve-review` run will discover and trust,
   so the approving `REVIEW_FEEDBACK.md` must name `Reviewed bundle ID:`
   (equal to the recomputed `bundle_id` of the bundle it approved),
   `Reviewed base commit:`, and `Work item: workflow-v2-1-core` before WF0
   creates its commit — WF0 refuses, naming which field is missing or
   mismatched, otherwise. `plan_approval.reviewed_bundle_id` (D2) is
   populated directly from the named field, never invented from whatever
   bundle happens to be on disk at commit time.
4. **The approving feedback may not declare a `"2.1"`-only reviewer role**
   (resolves `OPUS-R14-008`): `governing_workflow_version` is fixed at
   `"1"` for this work item's entire execution (D-Self-Governance), so it
   has no `local_model_plan_review`/`manual_external_plan_review` stage to
   run — `D-Plan-Review-Stages` scopes that entire vocabulary to `"2.1"`
   items only. A feedback file declaring either role for this work item is
   refused by WF0, naming the item's actual governing version, rather than
   silently accepted and left to imply a two-stage review that never
   happened and that no later record can corroborate.
5. **WF0 also creates `.claude/commands/bootstrap-workflow-v2.md`** — a
   small, temporary, bootstrap-only command, itself part of WF0's commit.
   Its entire job: find the first checkpoint in
   `docs/ai-workflow/registry/workflow-v2-1-core-registry.json` not yet
   `COMPLETE` (queried via `git log --grep`-free, exact
   `Workflow-Checkpoint`/`Workflow-Work-Item: workflow-v2-1-core` trailer
   search — the same mechanism D-Commit-Provenance defines, usable before
   `WORKFLOW_STATE.json` exists because it only reads Git history),
   implement **exactly that one checkpoint**, commit it with the trailer,
   and **stop** — never loop, unlike v1's `/milestone-implement`. It never
   reads `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`; its target work item
   is hardcoded to `workflow-v2-1-core`, so it cannot accidentally resume
   Milestone 8 (resolves the specific risk `GPT-R9-004` named).

**Fresh session per bootstrap checkpoint, by construction, not by
reminder.** Because the bootstrap command always stops after one
checkpoint, and a stopped command naturally ends the turn, continuing
requires the user to invoke it again — the natural point to start a fresh
conversation. This is what actually prevents a session from acting on
stale in-memory command text after a checkpoint that edits
`.claude/commands/*.md` files the current session itself loaded: the
session that edited the file never continues past that checkpoint's own
commit, so it never has an opportunity to act on the stale copy. The
**Usability** section below states this as the exact three-step sequence
the user is given after approval.

**The bootstrap command drives this one work item permanently — there is
no handoff to `/milestone-implement`, ever, for `workflow-v2-1-core`
itself** (resolves `OPUS-R10-002`/`-003` together, by removing the thing
both findings correctly showed was broken: a transfer point). Revision 8
described a handoff at WF4a-iii that deleted the bootstrap command and
switched to `/milestone-implement`'s dual-mode branch — round 10 found
two independent problems with that: the replacement driver (`/milestone-implement`'s
resumable v2.1 behavior) isn't built until WF2, two checkpoints later
(`OPUS-R10-002`), and even once built, the dual-mode branch keys off
`governing_workflow_version`, which is permanently `"1"` for this
milestone's own work item — so the "replacement" would route back to the
**v1** branch, which reads `docs/ACTIVE_MILESTONE.md` and targets
Milestone 8 (`OPUS-R10-003`). Both findings are instances of the same
mistake: designing a transfer of control this milestone's own governance
rules make impossible to land safely. The fix is to not transfer control
at all:

- The bootstrap command remains `workflow-v2-1-core`'s sole driver from
  WF0 through WF8b, its entire lifecycle. `/milestone-implement` is never
  invoked for this work item, at any point — not because of a version
  check, but because nothing ever asks it to. (`/milestone-implement`
  remains fully available, unmodified in its relevant behavior, for
  *other* work items — Milestone 8 via its own adoption path, D-Legacy,
  and any future product/process item — this exemption is scoped to
  exactly one work item, this milestone's own.)
- Once `WORKFLOW_STATE.json` exists (after WF1a), the bootstrap command's
  job grows by one step, performed at the **start** of every invocation,
  not once: read the current `work_items["workflow-v2-1-core"]` entry if
  present, or initialize it if this is the first invocation after WF1a
  landed, with the full field set `OPUS-R10-004` requires (below) — then
  proceed to select and implement the next checkpoint exactly as before.
  This replaces the one-time "handoff" with a continuous state-sync,
  which has no timing dependency on any other checkpoint's completion.
- **Full field reconstruction/maintenance** (resolves `OPUS-R10-004`):
  `work_item_type: "process"`, `work_item_kind: "process"`, `plan_path:
  "docs/ai-workflow/WORKFLOW_V2_PLAN.md"`, `registry_path:
  "docs/ai-workflow/registry/workflow-v2-1-core-registry.json"`,
  `governing_workflow_version: "1"` (fixed, D-Self-Governance),
  `base_commit: 162154d3e5e10eb65e109833acae4b4fb01fc5d6` (hardcoded by
  the bootstrap command until `WORKFLOW_STATE.json` exists to read it
  from — D-Selection's ancestry-limited trailer search needs this value
  and cannot get it from the state file before WF1a), `plan_revision`
  (the authoritative plan's current revision number), `phase:
  "IMPLEMENTING"`, `checkpoints` (populated by discovering every
  already-completed checkpoint's `Workflow-Checkpoint` trailer, scoped to
  `Workflow-Work-Item: workflow-v2-1-core`, marked `COMPLETE` with real
  commit SHAs), and `plan_approval`: `{status: "CURRENT", basis:
  "USER_OVERRIDE"` — the honest basis; the user's conversational
  go-ahead across many review rounds is what actually authorized this,
  there being no `/approve-review` to record `EXTERNAL_APPROVE`
  through — `reviewed_bundle_id`: the approving `REVIEW_FEEDBACK.md`'s own
  `Reviewed bundle ID:` field, verbatim (never re-derived from whatever
  bundle happens to be on disk — resolves `OPUS-R14-002`, point 3 above),
  `approved_review_content_id`: WF0's own `Workflow-Plan-Approval`
  trailer value (read directly, not re-derived), `review_content_manifest`:
  the plan-stage manifest at WF0's commit, `reviewed_content_commit: null`
  (D2's rule, unchanged), `user_confirmation`: the user's actual go-ahead
  text, `recorded_at`: WF0's commit timestamp`}`. `active_work_item_id` is
  set to `"workflow-v2-1-core"` once WF1a exists, and left alone
  thereafter (no other work item exists yet to compete for it in the
  bootstrap window).
- **The bootstrap window's durability guard** (resolves `OPUS-R10-012`):
  every bootstrap invocation, before selecting the next checkpoint,
  recomputes the plan-stage `review_content_id` against WF0's
  `Workflow-Plan-Approval` trailer value — the same comparison
  `IMPLEMENTING`'s entry condition performs once `plan_approval` exists,
  performed manually before it does. A mismatch (someone edited the
  authoritative plan documents between two bootstrap invocations) stops
  immediately, naming both values, exactly like the post-WF1a case. This
  is not a separate mechanism invented for the bootstrap window; it is
  the same check, run by the bootstrap command directly instead of by
  reading a field, for exactly as long as the field doesn't exist yet.
  Once `plan_approval` exists (after WF1a), the bootstrap command's
  per-invocation state-sync step (above) performs the identical
  comparison through the normal field, and the manual check is
  redundant, not contradictory — no dual-source conflict.
- **Retirement**: the bootstrap command is deleted only at this work
  item's own `MILESTONE_COMPLETE` — the same point any command's
  usefulness for a finished work item ends. Until then it is the answer
  to "what command implements the next `workflow-v2-1-core` checkpoint,"
  unconditionally, at every point in the registry order.

**Dual-mode exemption, stated explicitly**: WF0 and the bootstrap command
are not subject to D-Self-Governance's dual-mode branching requirement —
they have no v1 behavior to preserve, since they do not exist in v1 at
all, and they drive exactly one work item (`workflow-v2-1-core`) whose
`governing_workflow_version` is permanently `"1"` by construction, so
there is no second version to branch on. `/milestone-implement`'s own
dual-mode branching is unaffected and continues to serve every other work
item normally; this is a one-item routing exemption (this milestone never
appears as a target of `/milestone-implement` at all), not a change to
how dual-mode branching itself works elsewhere. **Exempt from dual-mode,
not from conformance** (resolves `OPUS-R10-016`, which found the
dual-mode exemption correctly excused the bootstrap command from
`WFR-26`'s golden-output requirement but left it with no conformance
coverage at all, despite driving six-plus checkpoints): WF8a-ii's scope
includes a dedicated test asserting the bootstrap command stops after
exactly one checkpoint and never reads `docs/ACTIVE_MILESTONE.md`/
`docs/ROADMAP.md` — the same property `WFR-26` verifies for dual-mode
commands, verified directly instead of through that mechanism.

### D-Fingerprint (prototype now built and twice-corrected; design unchanged from revision 6)

**`bundle_id`** and **`review_content_id`** remain two identifiers for two
purposes (unchanged from revision 6's split, resolves R5-PLAN-001/005/013/014):
`bundle_id` identifies the complete, exact logical bundle a reviewer saw
for one round; `review_content_id` identifies stable, approval-critical
content only. Both are now implemented, not merely specified —
`scripts/workflow_fingerprint.py`, corrected against real Git repositories
across two review rounds (`PROTO-R7-*`, `OPUS-R8-*`; see the disposition
sections above). The test-vector table from revision 6 stands unchanged
and is now backed by executable tests rather than assertion.

**Native-git manifest, worktree-source algorithm** (resolves `OPUS-R6-001`,
fixed at the prototype level): candidate paths are the union of
`git diff --name-only -z <base>` and `git ls-files --others
--exclude-standard -z`; every protected path's blob is computed via
`git hash-object`/`git ls-tree` directly, never read from `git diff --raw`'s
possibly-`0000000` destination. See `_snapshot_worktree`/`_snapshot_commit`
in the prototype.

**Path scope** (resolves `OPUS-R6-002`, fixed at the prototype level):
`PLAN_STAGE_PROTECTED` (exhaustive, positively stated) and
`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES` (each entry
justified, per `OPUS-R8-006/008`) are both part of `classify_path`'s
precondition and (per `OPUS-R8-014`) part of the hashed projection itself.
An unclassified path fails closed via `UnclassifiedPathError` at both the
worktree and commit entry points (`OPUS-R8-005`). **Widened this round to
cover the implementation phase, not just today's working tree** (resolves
`OPUS-R10-001`, which found the previous scope — sized only for the
current diff — made plan-approval durability unevaluable from the moment
`WF0`'s own commit created the first implementation-phase file):
`PLAN_STAGE_EXCLUDED_PREFIXES` now names `.claude/commands/`, `scripts/`,
`docs/ai-workflow/requirements/`, and `docs/ai-workflow/registry/`, and
`PLAN_STAGE_EXCLUDED_PATHS` gained `CLAUDE.md`, `REVIEW_PROTOCOL.md`, and
`MILESTONE_WORKFLOW.md`. This is the first round the prefix mechanism
carries real entries (it shipped empty in revision 8), so the
trailing-slash validation `OPUS-R8-006` added is now genuinely
load-bearing rather than exercised only by synthetic tests — confirmed
still passing (`OPUS-R10-017`). **Future work, unchanged in scope**: WF1b's
registry generator should derive this list from the registry's own
per-checkpoint declarations instead of it being hand-maintained, so the
two cannot drift (the same move D-Registry and D4b already made for the
plan-defining data itself).

**Two classification obligations, now both stated explicitly** (the
second new this round, resolves `OPUS-R16-001`): the classifier's
*input* is repository-wide — the union of `git diff --name-only <base>`
and every untracked path, over the **whole working tree**, not scoped to
any one work item — while `PLAN_STAGE_PROTECTED`/`EXCLUDED_*` are
necessarily one work item's own lists. Two rules follow, and both must
hold for every path any command this repository's workflow tooling is
documented to write, not only this work item's own:
1. **No path may simultaneously be a protected plan-stage input and a
   checkpoint deliverable** that the approved implementation must create
   or edit (`OPUS-R14-001`'s rule, restated in D-States above).
2. **No path that any concurrent operation may write may be
   unclassified.** A path this work item's own plan declares out of
   scope — another work item's product narrative, roadmap, milestone
   archive, or this work item's own eventual process-completion archival —
   must be **explicitly excluded, not merely unmentioned**: the classifier
   cannot distinguish "deliberately out of scope" from "nobody thought
   about it," and only the former is safe once concurrent work items are
   the normal case (`OPUS-R6-006`'s multi-item schema, D1's
   `active_work_item_id` as "resume-focus pointer, not an execution
   lock"). Confirmed as a real, reachable gap (`OPUS-R16-001`): Milestone
   8 adoption (D-Legacy phase 2) writes `docs/ACTIVE_MILESTONE.md` while
   `workflow-v2-1-core` may still be mid-implementation, a state D1
   explicitly sanctions — that write, and `/accept-milestone`'s writes to
   `docs/ROADMAP.md`/`docs/milestones/`, are now excluded with
   justifications in the same form as every other entry; this work item's
   own future process-completion archival under
   `docs/ai-workflow/archive/` is excluded too, closing the loop
   `OPUS-R16-002` opens on retiming it. `test_117_120_...` simulates all
   four while this work item is itself mid-sequence and confirms neither a
   raise nor an identity change.

**Closure widened to the rest of the repository a concurrent work item may
touch** (resolves `OPUS-R18-004`, found the moment the same sanctioned
scenario continued one step further than the four paths above): rule 2
covers "every path any command this repository's workflow tooling is
documented to write," which is not limited to this work item's own
process artifacts — `FIXING_FUNCTIONAL_FINDINGS` (a real
`MILESTONE_WORKFLOW.md` state reachable once Milestone 8 is adopted
mid-implementation) writes product code, product tests, and product
documentation, none of which the round-16 lists named.
`PLAN_STAGE_EXCLUDED_PREFIXES` gained `app/`, `docs/adr/`,
`docs/agent-context/`, `gradle/`, `config/`, `.github/`, and
`PLAN_STAGE_EXCLUDED_PATHS` gained `AGENTS.md`, `README.md`,
`docs/PROJECT_BRIEF.md`, `docs/DOMAIN_GLOSSARY.md`, `docs/UX_FLOWS.md` —
all under one shared `PRODUCT_SCOPE_JUSTIFICATION` string rather than a
justification repeated per entry, per the finding's own acceptance
criterion. Fail-closed is unchanged for any path outside this named set:
`test_057`/`test_034`/`test_035`/`test_095` (which had used `app/...` as
their "genuinely novel path" fixture, now itself excluded) were corrected
to use a path outside every named set instead, and still raise;
`test_133`/`test_134`/`test_135` (new) cover a concurrent product-code
write, a concurrent product-doc write, and a still-novel path
respectively.

**Implementation-stage manifest** remains deferred to WF4a-i, alongside
real implementation-stage fixtures — there is no implementation diff yet
to validate an algorithm against (unchanged from the prototype's own
scoping note). **Recorded this round, not actioned (`OPUS-R20-003`):**
`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES` are a
plan-stage projection — `app/`, `scripts/`, `gradle/`, `config/`,
`.github/` are excluded because they are *not* approval-critical before
implementation exists. `D-Commit-Provenance`'s freshness rule names
"source, test, build, migration, workflow-command file" as exactly what
the implementation-stage manifest must protect once that content exists —
the two stages' protected/excluded sets are near-inverses of each other,
not one reusable set. WF4a-i must derive the implementation-stage set
independently from the checkpoint registry's own artifact declarations
(the same generalization D-Registry's future-work note already commits
to), not adapt the plan-stage constants by inspection.

**Root-level build files, classification decision recorded
(`OPUS-R20-002`, not actioned):** `build.gradle.kts`, `settings.gradle.kts`,
`gradlew`, `gradlew.bat`, `.editorconfig`, and `Makefile` are named in
neither exclusion list and therefore fail closed via
`UnclassifiedPathError`, the same as any other unnamed path. This is
deliberate, not an oversight: these files change rarely, the resulting
error already names the offending path, and — unlike `app/**`/`gradle/**`,
which are large and routinely touched by unrelated product work — a
root-level build-tooling change is closer to the design surface this
milestone protects than to incidental concurrent noise. Widening the
exclusion set has a real cost (every additional excluded path is one the
classifier can no longer use to detect a stale approval), so the set is
not widened speculatively. If a genuine concurrent write to one of these
paths is later observed to raise and block unrelated work, add it under
`PRODUCT_SCOPE_JUSTIFICATION` at that point, with the concrete write as
the justification.

**Generator contract, now made explicit** (new this round, resolves
`OPUS-R18-001`/`-002`/`-003`/`-005`): round 18 found the identity
*algorithm* (`compute_bundle_id`/`compute_review_content_id_plan_stage`)
frozen and correct exactly as round 10 left it, but the *generator*
around it — `__main__` and the manifest-writing sequence — unsafe in three
independent ways, all now fixed without touching the algorithm itself:
- **Read-only by default.** `__main__` previously wrote `MANIFEST.md`
  unconditionally whenever `.ai-review/current/` existed — the one
  documented command a reviewer would run to *verify* a bundle silently
  *mutated* it, changing both identifiers with no warning
  (`OPUS-R18-002`). `__main__` now contains no unconditional write at all;
  the default invocation computes and prints both identifiers, including a
  read-only recomputed `bundle_id` for an existing bundle directory
  compared against what `MANIFEST.md` already states. Writing requires the
  explicit `--write-manifest` flag, which is the only path into
  `write_manifest_with_verified_identifiers` — the one function allowed to
  write `MANIFEST.md`.
- **Both identifiers computed last and asserted idempotent.** `bundle_id`
  already had a write-then-recompute check; `review_content_id` did not,
  which is exactly how the round-17 bundle's manifest carried a
  `review_content_id` that no longer reproduced (`OPUS-R18-001`) — a
  protected-path edit landed after it was computed and nothing re-verified
  it. `write_manifest_with_verified_identifiers` now recomputes **both**
  immediately after writing and raises
  (`ReviewContentIdNotIdempotentError`/`BundleIdNotIdempotentError`) on any
  mismatch before returning.
- **`plan_revision` sourced from the registry, not a literal.** The one
  real caller (`__main__`) had a hardcoded `plan_revision=12` already
  stale against the plan's own "Revision 13" title (`OPUS-R18-003`).
  `load_plan_revision()` reads `plan_revision` from
  `docs/ai-workflow/registry/workflow-v2-1-core-registry.json` (already
  protected, already machine-written) and asserts it matches the plan's
  declared `(Revision N)`, raising `PlanRevisionMismatchError` otherwise.
  This reverses round 15's explicit deferral (`OPUS-R14-004`'s
  not-taken half) — not a reopening on general principle, but because the
  premise it rested on ("no real caller exists yet") stopped being true the
  moment `__main__` started writing the authoritative manifest.
- **A second, checkable copy of `review_content_id`.** `MANIFEST.md` was
  previously the sole statement of the value that gates approval durability
  (`OPUS-R18-005`); `REVIEW_REQUEST.md` now states
  `review_content_id: <hex>` as a plain labelled line (not a `bundle_id:`
  -shaped one — `GPT-R9-001`'s one-location rule is specifically about
  that field pattern), and generation asserts the two agree
  (`assert_review_request_states_review_content_id`) **immediately after
  the first `review_content_id` computation, before any write happens** —
  found necessary during this round's own bundle regeneration, where an
  initial last-checked ordering left `MANIFEST.md` holding a fresh,
  self-consistent but unreviewed identifier pair the moment a stale
  `REVIEW_REQUEST.md` failed the check, the same partial-write hazard
  `OPUS-R18-002` is about, one level in.
- **Not yet fixed, recorded (`OPUS-R20-001`):** the write sequence itself
  is not atomic across the idempotence check — `MANIFEST.md` is written
  (twice: a placeholder, then with `bundle_id`) *before* the
  recompute-and-assert step, so a protected-path edit landing in that
  sub-second window raises correctly but leaves `MANIFEST.md` on disk
  stating an identifier the function itself just declared wrong. Deferred
  to `WF4a-i` (missing-test item 138) rather than fixed now: write to a
  temporary file in the bundle directory and `os.replace()` it into place
  only after both assertions pass, making the whole function atomic
  instead of only its `REVIEW_REQUEST.md` pre-check.

### D-Selection — checkpoint-selection algorithm (new, resolves OPUS-R6-012)

WF2's "selection algorithm" was named in scope but never specified, and
the checkpoint registry is a dependency DAG (after WF1b completes, WF4a-i
and WF4b are both ready; after WF4a-iii, WF-M8a/WF2/WF4c are all ready) —
nothing declared an order deterministic across independent sessions. The
algorithm, stated in full:

1. If `current_checkpoint_id` is non-null and its status is `IN_PROGRESS`,
   resume it, subject to the worktree-identity check (D3's dirty-resume
   rule).
2. Otherwise, select the first checkpoint in **the order of the
   `checkpoints` array in `docs/ai-workflow/registry/<work_item_id>-registry.json`**
   (corrected per `OPUS-R10-009`, which found rule 2 named "registry table
   order" while D-Registry makes the JSON authoritative and the Markdown
   table an unhashed generated view — a hand-edit, a regeneration bug, or
   a readability reordering of the view could drift from the JSON with
   nothing to detect it, defeating the determinism guarantee this
   algorithm exists to provide) whose status is not `COMPLETE` and all of
   whose dependencies are `COMPLETE`.
3. The JSON array's order is normative and **must** be a valid topological
   order of the `depends_on` column — the validator rejects a registry
   whose order violates its own dependencies (this is what makes rule 2
   deterministic across independent sessions without a tie-break rule: at
   most one checkpoint can ever be "the first ready one" for a given
   state). A conformance check (WF8a-i) additionally asserts the generated
   Markdown view's row order matches the JSON array order exactly, so
   drift between the two is caught rather than silently tolerated.
4. If no such checkpoint exists and any checkpoint is still incomplete,
   stop and report the specific blocked dependency (never silently idle).

### D-Registry — checkpoint registry format (revised, resolves OPUS-R6-018)

The Markdown registry table is no longer the input to plan-stage identity.
Mirroring D4b's own resolution of the same class of problem (R5-PLAN-009):
the plan-defining columns live in a tracked, machine-readable file,
**`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`
— created and populated this round, not aspirational (resolves
`GPT-R9-003`)** — an array of `{id, name, depends_on, complexity,
session_target}` objects. The Markdown table in this document (see
"Checkpoint registry" below) is a generated, human-readable view of that
same file and is never itself hashed. The naming pattern for any future
work item's own registry is `docs/ai-workflow/registry/<work_item_id>-registry.json`
— this round's concrete file is that pattern applied to
`workflow-v2-1-core`, not a one-off exception (`GPT-R9-011`'s
generalization remains WF4a-i's future scope: deriving the *protected set
itself* from validated work-item metadata rather than the hardcoded
constant this milestone's own plan stage still uses).

**Simplified from revision 7's design, and stated precisely** (revision 7
described a separate `checkpoint_registry_hash` projection field, hashed
as canonical JSON; that was never actually implemented and round 9 found
the plan claiming it as if it were): the registry file is simply a
**protected path** (`scripts/workflow_fingerprint.py`'s
`PLAN_STAGE_PROTECTED`), covered by the same per-path blob-hash manifest
mechanism as every other protected document — no separate normalization
step. This is deliberately byte-for-byte, not canonical-JSON-normalized:
the file should only ever be machine-written by WF1b's own generator, so
there is no legitimate independent reformatting to be robust against, and
a bespoke normalization step would just be one more thing to get wrong
(the exact lesson `OPUS-R8-002` already taught this design once). A
reformatted-but-content-equivalent registry file therefore *does* change
`review_content_id` — this is intentional: the plan-stage reviewer is
approving these exact bytes, and "the JSON means the same thing" is a
claim `/approve-review` should never have to evaluate. Editing the
registry (a real dependency change, not just formatting) changes
`review_content_id`, satisfying `checkpoint_registry_hash`'s original
intent without a distinct field. Status never lives in the registry file
at all — status lives exclusively in `WORKFLOW_STATE.json` (D3) — so no
progress write can ever touch it.

### D4b — Requirements mapping and ledger (revised: files created this round, resolves GPT-R9-003)

- **`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`**
  (tracked, machine-readable): the immutable, approved requirement↔checkpoint
  mapping — **created and populated this round**, protected the same way
  as the registry file above (a plain protected path, byte-for-byte, no
  separate `requirements_mapping_hash` field — same simplification and
  same reasoning as D-Registry).
- **`docs/ai-workflow/requirements/workflow-v2-1-core-ledger.md`** (tracked,
  human-readable, **never hashed** — not created yet; a living document
  WF4b initializes and implementation sessions append to): implementation
  evidence, verification results, review findings, functional-verification
  outcomes.

### D1 — Process vs. product routing (extended: multi-item schema, resolves OPUS-R6-006)

`WORKFLOW_STATE.json`'s top-level shape is now `{schema_version,
active_work_item_id, work_items: {<work_item_id>: {...per-item fields,
unchanged from revision 6's D3 shape...}}}` (full per-item field list
restated in D3 below). `work_item_type` (`"process"`/`"product"`) is a
per-item field, governing routing exactly as before.

- **Product/process-milestone initialization**: unchanged mechanism
  (`/milestone-plan` creates or updates the item under `work_items[id]`,
  `governing_workflow_version` copied from `WORKFLOW_CONFIG.json` at
  creation time), now writing into the multi-item map rather than a
  singleton.
- **`active_work_item_id`**: names the one work item autonomous commands
  (`/milestone-implement`, a resumed `/milestone-plan`, etc.) operate on by
  default without an explicit work-item argument. Exactly one value at a
  time (or `null` if none active). This is the *only* concurrency
  invariant the schema enforces structurally — it replaces
  `.ai-review/ACTIVE_WORK_ITEM` entirely (see D-Bundle-Manifest for why
  that pointer's cross-check didn't address the real incident it cited).
- **Other work items may exist in `work_items` regardless of the active
  pointer**, including in non-terminal phases, when that reflects a real,
  independent lifecycle waiting on something other than autonomous
  continuation. Concretely: Milestone 8's legacy import (WF-M8a, a
  checkpoint of the *active* `workflow-v2-1-core` item) writes
  `work_items["milestone-8"] = {phase: "LEGACY_READY", technical_approval: {...}}`
  — `LEGACY_READY` is a **dormant, non-terminal** phase (revised from
  revision 7's terminal `LEGACY_IMPORTED`, per `GPT-R9-005`: a terminal
  phase can never again become `active_work_item_id`, which made Milestone
  8's later functional review unreachable through any command). Full
  design in D-Legacy: import (phase 1, dormant) and adoption (phase 2, a
  later, explicit, user-invoked transition to `AWAITING_FUNCTIONAL_REVIEW`
  that also sets `active_work_item_id` and `governing_workflow_version` at
  that point) are deliberately separate steps.
- **This makes two persisted non-terminal entries the normal case, not an
  edge case** — `workflow-v2-1-core` stays `IMPLEMENTING` (active) while
  `milestone-8` sits at `LEGACY_READY` (dormant, inactive) for however long
  the user defers it, possibly past `workflow-v2-1-core`'s own
  `MILESTONE_COMPLETE`. `active_work_item_id` is a **resume-focus
  pointer, not an execution lock**: adoption may happen while
  `workflow-v2-1-core` is still mid-implementation, in which case adoption
  simply repoints `active_work_item_id` at `"milestone-8"` — exactly the
  same save/restore-by-repointing operation WF8b already performs for the
  synthetic dry-run item (below), generalized rather than special-cased.
  `workflow-v2-1-core`'s own progress persists untouched in its own
  `work_items` entry regardless of which item is currently active. The
  `v2.1-dry-run` synthetic item (WF8b) is additionally, transiently
  non-terminal during its own multi-session execution, per its documented
  save/restore pointer (D-Self-Governance).
- **Validator invariant** (replaces round 6's unqualified "one non-terminal
  item" framing, which Milestone 8's dormant-adoption design shows was
  never actually the right constraint): `active_work_item_id`, if
  non-null, must name an entry in `work_items` whose phase is not
  terminal — this is the *entire* structural invariant. The schema places
  no limit on how many *other*, non-active entries may simultaneously be
  non-terminal; a dormant or newly-adopted-but-not-yet-active item is a
  legitimate, expected state, not a validator error.
- **Cross-validation tie-break** (unchanged): for product work items,
  `docs/ACTIVE_MILESTONE.md`'s prose remains authoritative for narrative
  status; `WORKFLOW_STATE.json` is authoritative for machine-checked
  fields. A detected disagreement is a stop condition.
- **Completion/reset**: on a work item's `MILESTONE_COMPLETE` (product) or
  process-completion archival, its entry's phase becomes terminal and, if
  it was `active_work_item_id`, that pointer resets to `null` so the next
  `/milestone-plan` creates a fresh entry and claims the pointer.
- **Work-item-ID grammar** (unchanged): `^[a-z0-9][a-z0-9_-]{0,63}$`,
  matching `scripts/workflow_fingerprint.py`'s `WORK_ITEM_ID_RE`.

### D2 — Explicit approval, unified record (revised: entry/exit split, mechanism-independent guard, waiver enum)

```text
{
  status:                     CURRENT | STALE
  basis:                      EXTERNAL_APPROVE | USER_OVERRIDE | LEGACY_V1
  reviewed_bundle_id:          string | null   # null only for LEGACY_V1
  approved_review_content_id:  string | null   # null only for LEGACY_V1 no
                                                # longer applies -- see
                                                # D-Legacy: a real value is
                                                # backfilled at import
  review_content_manifest:     object | null   # the full manifest, stored
                                                # inline — null only for
                                                # LEGACY_V1
  reviewed_content_commit:     string | null   # a PRE-EXISTING commit —
                                                # == reviewed_implementation_head
                                                # for implementation/legacy
                                                # approval, where the
                                                # reviewed commit already
                                                # exists at write time.
                                                # PERMANENTLY null for plan
                                                # stage (GPT-R9-006): no
                                                # post-commit backfill --
                                                # the plan-approval commit
                                                # is discovered via its
                                                # exact scoped trailer
                                                # (D-Commit-Provenance),
                                                # never stored here
  legacy_evidence:             object | null   # LEGACY_V1 only
  user_confirmation:           string          # literal text the user
                                                # typed back in the current
                                                # turn — every basis
  waived_guarantees:           string[]        # LEGACY_V1 only; each entry
                                                # is one of a controlled
                                                # vocabulary (resolves
                                                # OPUS-R6-025): no_bundle_id,
                                                # no_telemetry -- no_content_id
                                                # removed (OPUS-R10-014): the
                                                # only legacy path always
                                                # backfills a real content ID,
                                                # so no reachable state can
                                                # ever waive it
  recorded_at:                 string
}
```

- **Gate entry vs. basis choice, split** (resolves `OPUS-R6-004`): entry to
  `AWAITING_PLAN_APPROVAL`/`AWAITING_TECHNICAL_APPROVAL` never reads this
  record — see D-States. Inside the state, `/approve-review` decides the
  basis: `EXTERNAL_APPROVE` when the current `REVIEW_FEEDBACK.md`'s status
  is exactly `APPROVE` and its `Reviewed bundle ID:` equals the
  just-recomputed `bundle_id` exactly (**for plan approval on a `"2.1"`
  item, additionally**: the `plan_review_stages` ledger records both
  `local_model_plan_review` and `manual_external_plan_review` completed
  against the current `review_content_id`, per `D-Plan-Review-Stages` —
  entry to `AWAITING_PLAN_APPROVAL` already required this, so this is a
  restated invariant `/approve-review` re-checks, not a new precondition);
  otherwise `/approve-review` refuses
  to write anything until the user supplies literal override text in the
  same turn, then writes `USER_OVERRIDE` with that text as
  `user_confirmation`. `BLOCK` never reaches either basis (the round's
  findings are, by definition, not resolved). `USER_OVERRIDE` remains
  available for a `"2.1"` item exactly as for a `"1"` item — the two-stage
  requirement is an entry condition on `AWAITING_PLAN_APPROVAL`, not a
  restriction on the user's existing override prerogative once inside it.
- **User-only guard, revised to named supported controls only** (resolves
  `OPUS-R6-010`, corrected by `GPT-R9-008`): revision 7 additionally
  proposed a command-internal check that detects whether `/approve-review`
  is "being invoked as a step of another command's own execution." Round 9
  correctly found this unspecified in implementable terms — no Claude Code
  input, environment field, hook, or nonce actually carries that signal,
  so the check could only ever be a brittle prompt convention pretending to
  be a control. **Removed.** The guard is now exactly two named, real
  mechanisms: (1) `disable-model-invocation: true` (the primary,
  harness-enforced control — WF4a-ii's scope includes verifying its actual
  coverage against both the SlashCommand and Skill exposure paths on the
  installed Claude Code version, since `WORKFLOW_V2_AUDIT.md:76` documents
  the dual exposure, and recording the observed result directly in this
  plan once checked, not asserted in advance); (2) `/approve-review`/
  `/accept-milestone` refuse to write unless `user_confirmation` **names
  the exact `work_item_id` and the exact stage (`plan`/`implementation`)
  being approved** (revised per `OPUS-R10-010`, which found the previous
  "not carried over unchanged from a prior invocation" novelty check
  neither necessary — a user typing the same literal confirmation text for
  two genuinely different, legitimate approvals would be refused for a
  reason unrelated to authorization — nor sufficient — any automated
  caller varying the string by one character would pass). Specificity is
  still only a trust boundary, not authorization, but unlike novelty it
  cannot be satisfied by a generic carried-over string, and it never
  rejects a legitimate repeat. If the installed version's coverage of (1)
  turns out to be incomplete, the documented defense in depth is a
  supported Claude Code permission rule or hook restricting the command,
  added at that point with the specific gap named — not invented
  speculatively now.
- **`waived_guarantees` controlled vocabulary** (resolves `OPUS-R6-025`,
  optional; narrowed per `OPUS-R10-014`): `{no_bundle_id, no_telemetry}` —
  `no_content_id` removed, since the only basis that uses waivers
  (`LEGACY_V1`) always backfills a real content ID at import (D-Legacy),
  so no reachable state can ever emit it. An
  unrecognized string is rejected by the validator.
- **Trust boundary** (unchanged): `user_confirmation` is a trust boundary,
  not a cryptographic guarantee.

### D-States — state definitions for the two new gates (revised: non-circular entry, non-clean-tree condition)

**`AWAITING_PLAN_APPROVAL`** (inserted between `REVISING_PLAN` and
`IMPLEMENTING`):

- **Entry** (resolves `OPUS-R6-004`/`-011`): `REVISING_PLAN`'s exit
  condition is met (all blocking/important findings from the **most
  recently reviewed round** resolved or rejected with evidence) **and**
  that round's external status was `REVISE` or `APPROVE` (never `BLOCK`).
  This condition reads only the review-round artifact, never
  `plan_approval` — there is nothing circular to satisfy, and a `REVISE`
  round with zero blocking findings left reaches this state exactly as
  readily as an `APPROVE` round. **For a `governing_workflow_version:
  "2.1"` work item, one further condition applies** (new, resolves
  `GPT-R11-001`/`-003`, see `D-Plan-Review-Stages` immediately below for
  the full mechanism): the work item's `plan_review_stages` ledger must
  additionally record both `local_model_plan_review` and
  `manual_external_plan_review` completed, in that order, against the
  **current** plan-stage `review_content_id` — a single reviewed round
  satisfying the paragraph above is necessary but no longer sufficient for
  a `"2.1"` item. A `governing_workflow_version: "1"` item's entry
  condition is exactly the paragraph above, unchanged.
- **Allowed actions**: run `/approve-review plan`, which chooses the basis
  as described in D2. No plan edits, no implementation.
- **Artifacts**: none new until `/approve-review plan` runs.
- **Exit**: `/approve-review plan` writes `plan_approval` and creates the
  plan-approval commit (D-Approval-Commits).
- **Stop for user/reviewer?** Yes — hard gate. `/approve-review` carries
  the guard described in D2; only the user can invoke it.

**`AWAITING_TECHNICAL_APPROVAL`** (inserted between
`APPLYING_REVIEW_FEEDBACK` and `AWAITING_FUNCTIONAL_REVIEW`):

- **Entry** (resolves `OPUS-R6-004`/`-005`/`-011`, same structure as
  above): `APPLYING_REVIEW_FEEDBACK`'s exit condition is met, **no
  protected path is dirty** (D3 defines "protected" for the implementation
  stage; `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` dirtiness never
  blocks this), current committed content matches
  `reviewed_implementation_head` exactly (D-Functional-Remediation names
  the sole writer of that field), and the most recently reviewed round's
  status was `REVISE` or `APPROVE`.
- **Allowed actions**: run `/approve-review implementation`.
- **Artifacts**: none new until the command runs.
- **Exit**: `/approve-review implementation` writes `technical_approval`
  and creates the metadata-only technical-approval commit
  (D-Approval-Commits).
- **Stop for user/reviewer?** Yes — hard gate, same enforcement as above.

**Recomputation rule, stated per stage** (resolves `OPUS-R6-003`, the
self-invalidation defect): `plan_approval` durability is checked by
recomputing the **plan-stage** projection (protected plan/audit/decisions
documents only) at current HEAD — invariant under checkpoint commits,
because checkpoints never touch those documents. `technical_approval`
durability is checked by recomputing the **implementation-stage**
projection (source/test/build/migration/workflow-command files — WF4a-i's
scope). `implementation_revision` is never part of the plan-stage
projection (it describes implementation progress, meaningless before
implementation starts and mutating during it).

**This invariance is a classification obligation, not just an
observation** (resolves `OPUS-R14-001`, which found it stated as fact
while `PLAN_REVIEW_WORKFLOW.md` sat in `PLAN_STAGE_PROTECTED` as a
checkpoint deliverable, making the two claims jointly false the moment
that checkpoint ran): a path belongs in `PLAN_STAGE_PROTECTED` only if it
is content the reviewer is approving; a path any checkpoint in the
registry is declared to create or edit belongs in
`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES` instead, even
when — especially when — it documents the approved design, exactly the
reasoning `MILESTONE_WORKFLOW.md`/`REVIEW_PROTOCOL.md` already use
(D-Bundle-Manifest). No path may be both a checkpoint deliverable and
protected at once; `test_096`/`test_101` (missing-test item 101) check
this holds across every checkpoint the registry currently declares.

**Edges**: `REVISING_PLAN → AWAITING_PLAN_APPROVAL → IMPLEMENTING`;
`APPLYING_REVIEW_FEEDBACK → AWAITING_TECHNICAL_APPROVAL →
AWAITING_FUNCTIONAL_REVIEW`. `apply-plan-review.md`'s steps and
`apply-implementation-review.md`'s exit step are amended in WF4a-ii to
name the new gates as their exit target. Hard-gate count: 4 → **6**,
stated once, in `MILESTONE_WORKFLOW.md` only. **For a `governing_workflow_version:
"2.1"` work item, `REVISING_PLAN → AWAITING_PLAN_APPROVAL` is further
refined by `D-Plan-Review-Stages` immediately below into
`REVISING_PLAN → AWAITING_LOCAL_PLAN_REVIEW → AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW
→ AWAITING_PLAN_APPROVAL`** — this is a refinement of the existing edge,
not two additional hard gates layered on top of it: for a `"1"` item the
edge is exactly as stated above, unchanged.

### D-Plan-Review-Stages — two-stage local-then-manual-external plan review (revised: verdict-specific transitions, named ledger writers for every row, current-content-gate scope stated, fresh-session wording made internally consistent; resolves GPT-R11-001/002/003/004/005/006/007/008/009/010/011/012 and GPT-R12-001/002/003/004/005/006)

A user-requested enhancement, evaluated by round 11 alongside revision 9:
harden the plan-review gate so a plan cannot reach `AWAITING_PLAN_APPROVAL`
on the strength of a single reviewer having seen the current content.
Round 11's own framing, confirmed directly against this repository:
`AWAITING_PLAN_APPROVAL`'s entry condition (above) and
`/apply-plan-review.md`'s own exit step (`GPT-R11-003`, the exact command
that produced revision 9) both treat "the most recently reviewed round was
`REVISE`-with-findings-resolved or `APPROVE`" as sufficient — true today,
and wrong once two *distinct, ordered* reviewer roles are required for the
same content. **Scoped entirely to `governing_workflow_version: "2.1"`
work items** (this milestone's own remaining plan-review rounds, this
document included, continue exactly as today — the v1-native
external-review-plus-explicit-approval sequence D-Bootstrap already names;
this section describes capability future v2.1-governed work items get,
not a retroactive requirement on this one).

**Two new states, inserted between `REVISING_PLAN` and
`AWAITING_PLAN_APPROVAL`** (resolves `GPT-R11-001`):

**`AWAITING_LOCAL_PLAN_REVIEW`**:

- **Entry**: `REVISING_PLAN`'s exit condition is met (as stated above), or
  `/apply-plan-review` has just applied an accepted plan edit (see below).
- **Allowed actions**: run `/review-plan` (full contract below) —
  recommended in a fresh session, since genuine independence from the
  session that wrote the plan is the property this stage exists to add.
- **Artifacts**: `REVIEW_FEEDBACK.md` (role: `local_model_plan_review`);
  for an `APPROVE` verdict only, a new `plan_review_stages` ledger entry
  (D3, below).
- **Exit, verdict-specific** (resolves `GPT-R12-001`, replacing the prior
  single "`REVISE` or `APPROVE`" exit condition, which let a `REVISE`
  satisfy the same entry condition as `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`
  required — see the transition table below for the full matrix):
  - **`APPROVE`**: `/review-plan` records the completed
    `local_model_plan_review` stage (`bundle_id`, `verdict: APPROVE`,
    round, `completed_at`) against the current `review_content_id` and
    transitions the work item to `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
    **Only a current local `APPROVE` may satisfy that transition** — no
    other verdict writes a ledger entry or reaches that state.
  - **`REVISE`**: `/review-plan` writes `REVIEW_FEEDBACK.md` with its
    required provenance fields (below); no ledger entry is written (a
    `REVISE` is not a completed stage in the ledger's sense — see the
    current-content-gate scope note below). The work item transitions to
    `REVISING_PLAN`; `/apply-plan-review` is then required.
  - **`BLOCK`**: no ledger write, no phase transition; the work item
    remains at `AWAITING_LOCAL_PLAN_REVIEW`, exactly like today's
    single-gate `BLOCK` handling — explicit user resolution is required
    before any further command runs.
- **Stop for user/reviewer?** Yes — the current session's turn ends here,
  the same "stop, do not auto-continue" pattern D-Bootstrap already
  established for its own checkpoint boundaries. A fresh, independent
  session is strongly recommended before running `/review-plan`, for
  genuine independence from the session that wrote the plan — but this is
  operational guidance, not a verified precondition (resolves
  `GPT-R12-005`, correcting this bullet's own prior "a fresh session is
  required to start `/review-plan` meaningfully" wording, which
  contradicted `GPT-R11-012`'s already-stated "not a mechanism anything
  here verifies" clause two paragraphs below it): no command contract,
  state transition, or validator rule depends on detecting session
  freshness; the sole correctness gate is the ledger entry and the
  `review_content_id` match.

**`AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`**:

- **Entry**: the ledger records a `local_model_plan_review` stage
  completed with `verdict: APPROVE` against the **current** plan-stage
  `review_content_id` (by construction, this is the only way to reach this
  state — see `AWAITING_LOCAL_PLAN_REVIEW`'s exit above).
- **Allowed actions**: the user uploads the bundle to a manual external
  reviewer (recommended: ChatGPT) and pastes its feedback into
  `REVIEW_FEEDBACK.md` (role: `manual_external_plan_review`) — this stage
  is manual end-to-end, identical in mechanism to today's single external
  review, only gated on the local stage having completed first.
  `/review-plan`'s own report (below) names the exact bundle path,
  `bundle_id`, and `review_content_id` to hand off, so the user is never
  guessing which artifact to upload (resolves `GPT-R11-006`). Once
  feedback is pasted, run `/record-manual-plan-review` (new command, full
  contract below; resolves `GPT-R12-002`/`-003`) to ingest it.
- **Artifacts**: `REVIEW_FEEDBACK.md` (role: `manual_external_plan_review`);
  for an `APPROVE` verdict only, the ledger's second stage entry.
- **Exit, verdict-specific** (resolves `GPT-R12-003`, naming the writer,
  destination, and validation this state previously left unstated — see
  the transition table below for the full matrix):
  - **`APPROVE`**: `/record-manual-plan-review` records the completed
    `manual_external_plan_review` stage (`bundle_id`, `verdict: APPROVE`,
    round, `completed_at`) against the current `review_content_id` and
    transitions the work item to `AWAITING_PLAN_APPROVAL`.
  - **`REVISE`**: `/record-manual-plan-review` records nothing in the
    ledger and transitions the work item to `REVISING_PLAN`;
    `/apply-plan-review` is then required (identical mechanism to today's
    single-stage `REVISE` handling — `/apply-plan-review` reads and
    applies `REVIEW_FEEDBACK.md`'s findings exactly as it does today,
    regardless of which stage produced them).
  - **`BLOCK`**: no ledger write, no phase transition; the work item
    remains at `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` — explicit user
    resolution is required before any further command runs.
- **Stop for user/reviewer?** Yes — hard gate, identical in kind to
  today's single `AWAITING_EXTERNAL_PLAN_REVIEW`.

**Verdict/state transition table** (resolves `GPT-R12-003`'s explicit
request for a table naming the writer and validation preconditions for
every row; restates the bulleted exits above in one place):

| Current state | Verdict | Writer | Next state/action | Validation preconditions |
|---|---|---|---|---|
| `AWAITING_LOCAL_PLAN_REVIEW` | `APPROVE` | `/review-plan` | record local stage (`verdict: APPROVE`) against current `review_content_id` → `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` | item is `"2.1"`; `phase == AWAITING_LOCAL_PLAN_REVIEW`; recomputed `bundle_id`/`review_content_id` match `MANIFEST.md`/`REVIEW_REQUEST.md` (not stale) |
| `AWAITING_LOCAL_PLAN_REVIEW` | `REVISE` | `/review-plan` | write `REVIEW_FEEDBACK.md` only, no ledger write → `REVISING_PLAN`; `/apply-plan-review` required | same as above |
| `AWAITING_LOCAL_PLAN_REVIEW` | `BLOCK` | `/review-plan` | no ledger write, no transition; remains `AWAITING_LOCAL_PLAN_REVIEW` | same as above; explicit user resolution required before any further command |
| `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` | `APPROVE` | `/record-manual-plan-review` | record manual stage (`verdict: APPROVE`, plus the feedback's own `bundle_id`) against current `review_content_id` → `AWAITING_PLAN_APPROVAL` | item is `"2.1"`; `phase == AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`; a current `local_model_plan_review` `APPROVE` recorded for the same `review_content_id` (restated invariant, already guaranteed by this state's own entry condition — not a new precondition, same pattern D2 uses for `/approve-review`); `REVIEW_FEEDBACK.md`'s `Reviewer role:` is exactly `manual_external_plan_review`; its `review_content_id` matches the current recomputed value (**hard**, blocks ingestion) — its `bundle_id` matching the current recomputed value is **advisory only** (warns, naming both, but never blocks — corrected per `OPUS-R14-005`, matching `GPT-R11-006`'s already-established rule that a wrapper-only bundle regeneration between upload and paste, new `bundle_id`/unchanged `review_content_id`, must not invalidate the manual stage); no `manual_external_plan_review` stage already recorded against this `review_content_id` (rejects duplicate ingestion) |
| `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` | `REVISE` | `/record-manual-plan-review` | no ledger write → `REVISING_PLAN`; `/apply-plan-review` required | same as above |
| `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` | `BLOCK` | `/record-manual-plan-review` | no ledger write, no transition; remains `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` | same as above; explicit user resolution required |

Malformed, missing, or unparseable `REVIEW_FEEDBACK.md` content at either
row is refused before any state change, naming what failed to parse,
identical in kind to `/review-plan`'s own malformed-prior-feedback handling
below.

**Durable stage ledger, scope stated explicitly** (resolves `GPT-R11-002`,
`GPT-R12-004`; a new per-work-item field in `WORKFLOW_STATE.json`, full
schema in D3 below): `plan_review_stages: {review_content_id,
local_model_plan_review: {bundle_id, verdict, round, completed_at} | null,
manual_external_plan_review: {bundle_id, verdict, round, completed_at} |
null}`. **This is a current-content gate record, not an audit trail**
(resolves `GPT-R12-004`, which correctly found the prior text ambiguous
between the two): by the transition table above, only an `APPROVE` verdict
is ever recorded at either stage, so `verdict` is `APPROVE` whenever the
sub-record is non-null — stored anyway as a real schema field rather than
an implicit constant, so the validator can positively reject a corrupt
ledger entry recording anything else (D3, below). Round-by-round history
of `REVISE`/`BLOCK` rounds is deliberately out of scope for v2.1 core, the
same kind of deferral D3's "between-checkpoint attribution" already makes
for v2.2 — `REVIEW_FEEDBACK.md` is gitignored and mutable (confirmed:
`.gitignore` excludes all of `.ai-review/`), so no git history exists for
prior rounds' feedback regardless of what the ledger stores, and this
document does not claim otherwise. **Validity is by recomputation, not by
an active clear step** — the same pattern `plan_approval`/`technical_approval`
already use (D-Approval-Commits): the ledger is meaningful only while its
stored `review_content_id` equals the freshly recomputed current one; the
moment plan content changes, the stored value no longer matches, and both
stages read as absent regardless of their stored sub-fields, with no
separate invalidation write required. This is what makes "any protected
plan edit clears both stages" true by construction rather than by a step
`/apply-plan-review` could forget to perform.

**`/apply-plan-review`'s exit step, revised for `"2.1"` only** (resolves
`GPT-R11-003`/`-007`): the v1 branch (today's unmodified behavior,
including this milestone's own remaining plan-review rounds) is untouched
— step 7's "otherwise, report the plan as ready for implementation"
stands exactly as today. For a `"2.1"` item, step 7 is replaced: the
command never self-declares plan readiness. After every accepted plan
edit, regardless of how large or small a "structural change" judgment
would call it:

1. the recomputed `review_content_id` differs from whatever the ledger
   last recorded, so both stages read as absent (by the recomputation rule
   above — no explicit clear needed);
2. the bundle is regenerated (`scripts/prepare-ai-review.sh <base-sha>
   plan`, unchanged mechanism);
3. the command reports the work item's phase as
   `AWAITING_LOCAL_PLAN_REVIEW` and stops — the same "stop, do not
   auto-continue" behavior D-Bootstrap already established for its own
   checkpoint boundaries, applied here to the plan-review loop.

**No path re-enters manual-external review without a fresh local pass
first** (resolves `GPT-R11-007`, replacing "repeat the relevant review
steps" with a literal sequence): whether the edit was driven by a
local-model `REVISE` or a manual-external `REVISE`, the very next required
stage is always `AWAITING_LOCAL_PLAN_REVIEW`. A prior local approval
cannot survive a manual-external-driven edit, and a prior manual-external
approval cannot survive a local-driven edit — both are already impossible
under the recomputation rule above, since either edit changes
`review_content_id`.

**`/review-plan` — full command contract** (new, model-independent;
resolves `GPT-R11-005`/`-011`):

- **Reviewer role, not a model.** The command implements the
  `local_model_plan_review` role. It is documented as recommended to run
  from a fresh Opus session for genuine independence, but nothing in its
  contract, its written feedback schema, or the state transition it
  produces names a specific model — running it from any capable Claude
  model produces the same schema and the same transition (missing-test
  item 84, below).
- **Resolution**: resolves the target work item using the same
  explicit-argument-or-`active_work_item_id` rule every other v2.1 command
  uses (D-Legacy's `/prepare-functional-review milestone-8` is the
  existing precedent). Refuses with a named error if the resolved item's
  `governing_workflow_version` is not `"2.1"` (v1 items have no local-review
  stage to run) or its `phase` is not `AWAITING_LOCAL_PLAN_REVIEW`.
- **Reads**: the authoritative plan doc, `REVIEW_REQUEST.md`,
  `MANIFEST.md`, the required-context file list, prior
  `REVIEW_FEEDBACK.md` (if any, for continuity across rounds), and
  `REVIEW_PROTOCOL.md`'s feedback-structure contract.
- **Recomputes** the current `bundle_id` and plan-stage `review_content_id`
  before writing anything, and refuses (naming both a recomputed and a
  stale value) if the bundle directory does not match what
  `MANIFEST.md`/`REVIEW_REQUEST.md` claim — the same staleness discipline
  `/approve-review` already applies (D2), run one stage earlier.
- **Independently verifies** every finding the plan document claims as
  addressed against the actual repository state (not the disposition
  table's word for it — this command's own validation requirement mirrors
  `/apply-plan-review`'s), and searches for new findings.
- **Write set, corrected** (resolves `GPT-R12-002`, replacing the prior
  "writes only `REVIEW_FEEDBACK.md`" claim, which directly contradicted
  this same section's own "records the completed stage in the ledger"
  exit condition): `/review-plan` writes `REVIEW_FEEDBACK.md` always, and
  — for an `APPROVE` verdict only — the resolved work item's
  `local_model_plan_review` ledger fields and its phase transition to
  `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` in `WORKFLOW_STATE.json`. A
  `REVISE`/`BLOCK` verdict writes `REVIEW_FEEDBACK.md` only, plus the
  phase transition to `REVISING_PLAN` for `REVISE` (none for `BLOCK`).
  Never the plan, registry, mapping, command, product, or bundle-content
  files, and never another work item's fields. A fixture asserts the exact
  tracked-file write set for each verdict (missing-test items 96/98,
  below).
- **Required provenance fields** in the written feedback: `Reviewer role:
  local_model_plan_review` (never a model name in this field), the
  recomputed `bundle_id`/`review_content_id`, the round/sequence number,
  and a completion timestamp — the same fields the ledger stores for an
  `APPROVE` verdict (`GPT-R11-002`).
- **Named failure modes**: a missing or unreadable bundle; a bundle whose
  recomputed identifiers don't match its own manifest (stale); a work item
  in the wrong worktree (D-Bundle-Manifest's existing local-staleness
  check, reused unchanged); a work item not at
  `phase: AWAITING_LOCAL_PLAN_REVIEW` (including "already completed this
  round" — refuses rather than silently re-running); malformed prior
  feedback (reports and stops rather than guessing an interpretation).
- **Stops** after writing and validating feedback — never auto-continues
  to `/apply-plan-review` or to the manual-external stage.

**`/record-manual-plan-review` — full command contract** (new,
model-independent, mechanical; resolves `GPT-R12-002`/`-003`, the missing
writer for the manual stage): a small command whose sole job is ingesting
an already-pasted manual verdict and advancing state — it never evaluates
plan content itself (the external reviewer already did that) and never
edits the plan (that remains `/apply-plan-review`'s job, unchanged,
exactly as it reads and applies today's single-stage external feedback).

- **Not a user-authority gate** (resolves the risk `GPT-R12-003` named of
  `/apply-plan-review` or `/approve-review` "secretly" performing this
  ingestion): it carries no `disable-model-invocation` guard and no
  `user_confirmation` requirement, since it grants no approval itself —
  it only records a verdict the user already obtained externally and
  already pasted into `REVIEW_FEEDBACK.md` in the current turn.
  `/approve-review plan` remains the sole, separate user-authority gate
  (D2), entirely unchanged by this command's existence: its own
  `plan_review_stages` check (D2, above) re-verifies the same ledger
  invariant this command writes, as a restated invariant, not a second
  ingestion path.
- **Resolution, phase precondition, recomputation, and staleness
  handling**: identical in mechanism to `/review-plan`'s own (above), but
  gated on `phase == AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` instead of
  `AWAITING_LOCAL_PLAN_REVIEW`.
- **Reads**: `REVIEW_FEEDBACK.md` (must declare `Reviewer role:
  manual_external_plan_review`), `MANIFEST.md`, `REVIEW_REQUEST.md`, and
  the ledger's existing `local_model_plan_review` entry.
- **Validates before writing anything**: the feedback's declared role is
  exactly `manual_external_plan_review` (rejects a local-role or
  unlabeled feedback file, naming which); its `review_content_id` matches
  the current recomputed value — **hard**, blocks ingestion (rejects
  protected-content-stale feedback, naming both); its `bundle_id` matching
  the current recomputed value is **advisory only** — a mismatch is
  reported as a warning naming both values, never blocks ingestion
  (corrected per `OPUS-R14-005`: the manual stage has an unavoidable
  multi-hour-or-longer gap between bundle upload and pasted feedback, the
  stage most likely to hit an unrelated wrapper-only regeneration in the
  meantime — exact `bundle_id` equality here would contradict
  `GPT-R11-006`'s already-established rule that a wrapper-only
  regeneration, new `bundle_id`/unchanged `review_content_id`, must not
  invalidate a completed or in-progress review stage); the ledger already
  records a current `local_model_plan_review` `APPROVE` for the same
  `review_content_id` (a restated invariant, since entry to this phase
  already required it — defense against a corrupted or hand-edited state
  file, not a new precondition); no `manual_external_plan_review` stage is
  already recorded against the current `review_content_id` (rejects
  duplicate ingestion — a second invocation after a completed
  `APPROVE`/`REVISE` already finds the phase has moved on and fails the
  phase precondition instead, so this check only fires for a hand-edited
  or race-condition state).
- **Write set**: for `APPROVE`, the resolved work item's
  `manual_external_plan_review` ledger fields — including the feedback's
  own `bundle_id` verbatim, regardless of whether it matches the current
  recomputed one, so the ledger records what the reviewer actually saw
  (`OPUS-R14-005`) — and its phase transition to `AWAITING_PLAN_APPROVAL`;
  for `REVISE`/`BLOCK`, only the phase transition (to `REVISING_PLAN` for
  `REVISE`, none for `BLOCK`). Never `REVIEW_FEEDBACK.md` (already
  user-authored), the plan, registry, mapping, command, product, or
  bundle-content files. A fixture asserts this (missing-test item 98,
  below).
- **Named failure modes**: missing/unreadable `REVIEW_FEEDBACK.md`; wrong
  declared reviewer role; stale `review_content_id` (blocking) or
  mismatched `bundle_id` (advisory, reported but not blocking); wrong
  work item; wrong phase (including "already ingested this round" and "no
  local `APPROVE` on record"); malformed feedback (reports and stops).
- **Stops** after writing and validating — never auto-continues to
  `/apply-plan-review` or `/approve-review`.

**Concise operator guide** (resolves `GPT-R11-008`; content deferred to
`WF4a-iv`, see the Round 11 disposition narrative above for why; **excluded,
not protected, as of this revision** — resolves `OPUS-R14-001`/`-009`,
which found the original pre-declared-protected classification made
`D-States`' "checkpoints never touch protected documents" invariance false
the moment `WF4a-iv` actually wrote the file, since that write is itself a
checkpoint touching what was then a protected path: writing it would have
changed `review_content_id` mid-implementation and staled the plan
approval, with no transition anywhere back to a valid state):
`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`, excluded now
(`PLAN_STAGE_EXCLUDED_PATHS`, `scripts/workflow_fingerprint.py`) — same
treatment as `MILESTONE_WORKFLOW.md`/`REVIEW_PROTOCOL.md` above: it
documents the already-reviewed design for operators, it is not itself the
design, so `WF4a-iv` can write it without staling anything. It documents,
concisely, the normal flow — updated per `GPT-R12-002`/`-003` to name the
manual-ingestion step the prior revision's diagram omitted —

```text
Sonnet: /milestone-plan
Opus (fresh session): /review-plan
  REVISE → Sonnet: /apply-plan-review → Opus (fresh session): /review-plan (repeat)
  APPROVE → continue below
User: upload the exact approved current bundle to ChatGPT
User: paste ChatGPT's feedback into REVIEW_FEEDBACK.md
Sonnet: /record-manual-plan-review
  REVISE → Sonnet: /apply-plan-review → Opus (fresh session): /review-plan (return to the top)
  APPROVE → continue below
User: /approve-review plan only after the complete sequence approves
```

— and states, for every stop: the current work item, review stage, bundle
path, both identifiers, and the exact next command or manual action. It
recommends fresh sessions for reviewer independence (operational guidance,
per `GPT-R11-012`) without duplicating any command's detailed contract,
which lives in this document and the command files themselves.

**Dual-mode enumeration** (extends D-Self-Governance's existing list,
below): `/review-plan` and `/record-manual-plan-review` are both new,
`"2.1"`-only commands (no v1 behavior to preserve — each refuses cleanly
outside a `"2.1"`-governed work item at its own required phase,
missing-test items 86/97); `/apply-plan-review`'s v1 branch is unchanged,
its `"2.1"` branch is the revised exit step above.

### D-Approval-Commits (revised: freshness rule per stage, reviewed_implementation_head writer named, no post-commit backfill)

- **Plan approval writes exactly one commit and nothing after it**
  (resolves `GPT-R9-006`): `plan_approval.reviewed_content_commit` is
  never written or backfilled — it stays permanently `null` for the plan
  stage (D2). The plan-approval commit is discovered when needed (e.g. by
  `IMPLEMENTING`'s entry check or a fresh-session resume) via its exact
  `Workflow-Plan-Approval: <review_content_id>` trailer
  (D-Commit-Provenance's scoped, ancestry-limited, exactly-one-match
  lookup), which requires no stored self-reference and is discoverable
  from any fresh session. The working tree is clean immediately after
  `/approve-review plan` returns — there is no second, dirty-tree-inducing
  write to reconcile.
- **`/approve-review plan`**: (1) recomputes `bundle_id` over the current
  bundle and the plan-stage `review_content_id` over the working-tree plan
  content, confirms both, displays them, **and** displays the protected
  and excluded path lists (resolves round 5's undispositioned usability
  requirement — see "Usability" below); (2) resolves the basis per D2;
  (3) writes `plan_approval`; (4) creates **one** commit — approved plan
  doc(s), registry JSON + generated Markdown view, `WORKFLOW_STATE.json`,
  requirements mapping file together — carrying an exact
  `Workflow-Plan-Approval: <full review_content_id>` trailer plus
  `Workflow-Work-Item: <id>` (D-Commit-Provenance). `IMPLEMENTING`'s entry
  condition: current HEAD (derived live) is that commit or a
  checkpoint-commit descendant of it, **and** `plan_approval.status ==
  CURRENT`, **and** a freshly recomputed **plan-stage** `review_content_id`
  matches `plan_approval.approved_review_content_id`.
- **`/approve-review implementation`**: requires no protected path dirty
  and (derived-live) HEAD `== reviewed_implementation_head` before
  running. Writes `technical_approval` and creates a metadata-only commit
  (zero production/test changes) carrying `Workflow-Technical-Approval:
  <full review_content_id>` + `Workflow-Work-Item: <id>`.
- **`reviewed_implementation_head`'s sole writer** (resolves
  `OPUS-R6-013`): the bundle generator, at `implementation`/`post-fix`
  stage only, sets it to the HEAD the bundle was generated from. Nothing
  else ever writes it — in particular, the functional-remediation cycle's
  bounded-change branch (D-Functional-Remediation) regenerates a
  `post-fix` bundle specifically so this field advances, closing the loop
  `/approve-review implementation` depends on.
- **Checkpoint completion** (resolves `OPUS-R6-014`): the checkpoint's own
  commit carries `Workflow-Checkpoint: <id>` + `Workflow-Work-Item: <id>`
  and updates `checkpoints[id].status = COMPLETE` in `WORKFLOW_STATE.json`
  — the **sole** writable record of checkpoint status. The registry file
  (D-Registry) carries no status column; `ACTIVE_MILESTONE.md`'s
  per-checkpoint narrative is explicitly derived/non-authoritative for
  status and is updated at milestone boundaries, not required per
  checkpoint, so an interrupted session (state written, narrative not)
  resumes without a manual reconciliation stop. The commit trailer remains
  verification evidence — cross-checked against the state record, never
  trusted as a second source of truth.

### D-Commit-Provenance (revised: duplicate-trailer tie-break and recovery, resolves OPUS-R6-022)

Exact, scoped trailer lookup (unchanged from revision 6): full identifiers
only, `Workflow-Work-Item`-scoped, ancestry-limited to `base_commit..HEAD`,
requiring exactly one match — **with one addition**. A legitimate rebase,
cherry-pick, or merge can copy a trailer onto a new commit while the
original remains reachable, producing two matches for what is really one
logical event (confirmed against this repository's actual state:
`milestone8-cp11-work` (`dc4381a`) has diverged from `162154d`, so its
required user-performed integration will be exactly such an operation):

- **Tie-break** (not a silent pick): among multiple matches for the same
  (trailer key, value, work-item) triple, prefer the one that is (a) a
  first-parent ancestor of HEAD, **and** (b) whose recorded state/content
  change verification (unchanged from revision 6 — the matched commit is
  checked against what it claims, not trusted on the trailer's word alone)
  passes. If more than one commit still qualifies after both filters, stop
  — this is genuine ambiguity, not a duplicate to resolve automatically.
- **Recovery for genuine ambiguity**: a user-approved `Workflow-Supersedes:
  <sha>` trailer on a new commit, or an explicit annotation in
  `WORKFLOW_STATE.json` naming which match is authoritative — either way a
  named, git-tracked, user-approved action (consistent with this
  repository's git restrictions), never a Claude-autonomous pick.

### D3 — Workflow state file (revised: multi-item wrapper, field cleanup, WORKTREE_IDENTITY.json writer named)

- **`docs/ai-workflow/WORKFLOW_CONFIG.json`** (tracked, repository-level):
  `{schema_version, default_workflow_version, supported_versions: ["1",
  "2.1"]}`. Missing or corrupt **before** activation → validator fails
  closed to `default_workflow_version: "1"`. **After** activation (see
  D-Self-Governance's activation trailer), a missing/corrupt config is a
  **hard stop**, not a silent downgrade (resolves `OPUS-R6-015`).
  `supported_versions` now gates work-item creation directly (resolves
  `OPUS-R6-024`, optional): a `governing_workflow_version` outside this
  list is rejected at the moment a work item is created.
- **`docs/ai-workflow/WORKFLOW_STATE.json`** — tracked, portable, now
  **multi-item** (resolves `OPUS-R6-006`): `{schema_version,
  active_work_item_id, work_items: {<id>: {work_item_type, work_item_kind,
  work_item_id, parent_work_item_id (null unless this is a broad-remediation
  child item, D-Functional-Remediation), plan_path, registry_path,
  governing_workflow_version, phase, plan_revision, implementation_revision,
  functional_review_round,
  base_commit, reviewed_implementation_head, current_checkpoint_id,
  last_completed_checkpoint_id, checkpoints: {checkpoint_id: {status,
  start_commit}}, current_bundle_id, plan_approval, technical_approval,
  plan_review_stages, functional_acceptance_status, blocking_decisions,
  state_revision, last_transition}}}`. **`plan_review_stages`** (new field,
  `"2.1"`-only, resolves `GPT-R11-002`, full mechanism in
  `D-Plan-Review-Stages`): `{review_content_id, local_model_plan_review:
  {bundle_id, verdict, round, completed_at} | null,
  manual_external_plan_review: {bundle_id, verdict, round, completed_at} |
  null}` — `null` (the whole field) for a `"1"` item, which has no
  two-stage requirement to record. **Current-content gate record, not an
  audit trail** (resolves `GPT-R12-004`, see `D-Plan-Review-Stages`'s scope
  note for the full reasoning): each sub-record is written only for a
  completed `APPROVE`, never for `REVISE`/`BLOCK`, and is only meaningful
  while its `review_content_id` matches the freshly recomputed current
  one — round-by-round history of prior `REVISE`/`BLOCK` verdicts is
  deliberately not preserved here or anywhere else durable, the same kind
  of explicit v2.1-core scope cut as "between-checkpoint attribution"
  below. **`work_item_kind`** (new field, resolves
  `GPT-R9-013`, optional): `"process" | "product" | "synthetic"`, a
  controlled vocabulary the validator enforces — distinct from
  `work_item_type` (`"process" | "product"`, D1's routing field). **A
  synthetic item's `work_item_type` is `"process"`, not `null`/unused**
  (resolves `OPUS-R10-007`, correcting revision 8's "`null`/unused for
  `kind: "synthetic"`" clause: the prototype's own
  `validate_work_item_type` rejects anything outside `{"process",
  "product"}` unconditionally, so a synthetic item with `type: null`
  cannot compute an identity at all, and WF8b's dry run exists precisely
  to compute one). `work_item_kind: "synthetic"` is what actually makes an
  item route nowhere in D1 — `work_item_type` continues to describe what
  the item *would* route as if it were real, which for the dry run is
  correctly "process" (it exercises the process-item code path). **Removed
  from the per-item shape, resolves `OPUS-R6-027`** (optional): `branch`
  (derivable from `git branch --show-current`, was already advisory-only
  and read by nothing) and `last_updated` (redundant with
  `last_transition`, read by nothing). Every remaining field has at least
  one documented reader elsewhere in this document. **Not tracked**:
  `current_head_commit` (always derived live).
- **`.ai-review/runtime/WORKTREE_IDENTITY.json`** — gitignored,
  local-only: `repo_root`, `git_common_dir`, `worktree_root`,
  **`expected_dirty_paths_by_work_item: {work_item_id: {path, sha256}[]}`**
  (keyed by work item, resolves `OPUS-R10-008`: a single flat list meant
  repointing `active_work_item_id` to a second item while a first item had
  `IN_PROGRESS` dirty work — a normal operation per D1's "resume-focus
  pointer, not an execution lock" — overwrote the first item's expected
  set with the second's, so returning to the first reported a false
  inconsistency for work that was never lost), `generated_at`. **Writer
  named** (resolves `OPUS-R6-026`, optional): the command that transitions
  a checkpoint to `IN_PROGRESS` (WF2's `/milestone-implement`, or the
  bootstrap command for `workflow-v2-1-core`'s own checkpoints) creates or
  refreshes **its own work item's entry** in this file at that transition,
  never touching another work item's entry; a checkpoint found
  `IN_PROGRESS` with no matching entry for its own work item present stops
  and reports the inconsistency rather than silently re-deriving an empty
  expected set (unchanged from revision 5/6 in spirit, now keyed
  correctly).

Validator rejects: unknown phase/status/`work_item_kind`/`work_item_type`
values, invalid transitions, multiple `IN_PROGRESS` checkpoints within one
work item, a later checkpoint `COMPLETE` while an earlier dependency
isn't, completion without a reachable commit (D-Commit-Provenance),
corrupt/unparseable JSON (fails closed), `active_work_item_id` naming a
terminal-phase or nonexistent entry, **an entry's inner `work_item_id`
field disagreeing with its own `work_items` map key** (corrected per
`OPUS-R10-015`, which found the previous wording — "two work items with
the same `work_item_id`" — describes a state a JSON object's own keys
cannot represent in the first place; this restates the check as the one
that is actually meaningful), and (new, resolves `OPUS-R10-006`) **every
requirement in the requirements-mapping JSON having at least one
checkpoint, and every checkpoint in the registry JSON owning at least one
requirement** — the coverage query WF1b's own registry/mapping generator
runs before writing either file, so an unmapped requirement or an
unowned checkpoint fails at generation time, not at review time. **New,
resolves `GPT-R11-001`/`-002`**: a non-null `plan_review_stages` on a
`governing_workflow_version: "1"` item (the field has nothing to record
for a `"1"` item); `manual_external_plan_review` recorded while
`local_model_plan_review` is absent or `null` for the same
`review_content_id` (the manual stage can never complete before the local
stage, by construction of the state machine, so a record showing otherwise
is corrupt, not a legitimate fast path); either stage's stored
`review_content_id` disagreeing with the field's own top-level
`review_content_id`. **New, resolves `GPT-R12-001`/`-003`**: a stage
sub-record whose `verdict` is anything other than `APPROVE` — by the
transition table in `D-Plan-Review-Stages`, only a completed `APPROVE` is
ever recorded at either stage, so a stored `REVISE` or `BLOCK` can only
mean the record was written by something other than `/review-plan`/
`/record-manual-plan-review`, or hand-edited.

**Dirty in-progress resume, worktree-scoped** (unchanged): a clean
(`COMPLETE`) checkpoint is portable anywhere; an `IN_PROGRESS` checkpoint's
uncommitted work is worktree-local — resume requires matching local
`WORKTREE_IDENTITY.json`, else stop and report.

**Checkpoint-complete continuation** (unchanged): `current_checkpoint_id`
→ `null` between checkpoints while `phase` stays `IMPLEMENTING`; `phase` →
`SELF_REVIEWING_IMPLEMENTATION` only when all checkpoints are `COMPLETE`.

**Between-checkpoint attribution** (unchanged, deferred-scope constraint
for v2.2): explicit categories, never a forced fallback.

**Session handoff / generated summary** (unchanged): both gitignored,
generated on demand, never authoritative.

### D-Self-Governance (revised: durable rollback trailer, missing-config recovery uses the latest event, WF8b pointer persistence, work_item_kind, dual-mode enumeration completed)

- `docs/ai-workflow/WORKFLOW_CONFIG.json.default_workflow_version` is the
  repository-level default, changed only by `WF-Activate`.
- Every work item's `governing_workflow_version` is fixed at creation from
  the then-current repository-level default, never re-read afterward.
  Workflow v2.1's own work item is created while the default is still
  `"1"`, so it is fixed at `"1"` for this milestone's entire execution.
- **Dual-mode command enumeration, completed** (resolves `OPUS-R6-020`):
  every command this milestone modifies carries an explicit two-branch
  structure keyed off the current work item's `governing_workflow_version`:
  `/milestone-implement` (WF2), `/approve-review`/`/accept-milestone`
  (WF4a-ii), `/prepare-functional-review` (WF-M8b's selector-argument and
  adoption extension, and WF4c), the bundle scripts (WF5), and —
  previously omitted — `/milestone-plan` (WF1b: its
  `WORKFLOW_STATE.json`/`WORKFLOW_CONFIG.json` initialization steps are
  v1-inert, since a v1-governed run only reads/writes the pre-existing
  singleton-shaped behavior it already has today, verified by a
  golden-output test), `/apply-plan-review` (WF4a-ii for the exit-target
  naming; **WF4a-iv, new this round, additionally gives it a `"2.1"`-only
  revised exit step per `D-Plan-Review-Stages` — its v1 behavior is
  untouched by either checkpoint**), and
  `/apply-implementation-review` (amended only in its exit-target naming,
  in WF4a-ii — its v1 behavior is otherwise untouched). **New this round,
  `WF4a-iv`, resolves `GPT-R11-005`/`-011`**: `/review-plan` — a
  `"2.1"`-only command with no v1 counterpart at all; invoked against a
  `"1"` item, or with no `"2.1"` item resolvable, it refuses cleanly rather
  than branching, since there is no v1 behavior to preserve. **Likewise
  `/record-manual-plan-review` (new, resolves `GPT-R12-002`/`-003`)**: a
  `"2.1"`-only command with no v1 counterpart, refusing cleanly outside a
  `"2.1"`-governed work item at phase `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`.
- **`WF-Activate`** (checkpoint, after `WF8a-ii`, before `WF8b`): validates
  WF8a-ii's conformance suite passes, then writes
  `WORKFLOW_CONFIG.json.default_workflow_version = "2.1"` **and** commits
  that write carrying a `Workflow-Activation: 2.1` trailer (resolves
  `OPUS-R6-015`) — activation is now recorded somewhere the config file
  itself cannot take with it.
- **Rollback is a durable commit event, not a bare file edit** (resolves
  `GPT-R9-007`, correcting revision 7's "edit the field back" framing,
  which left no trailer trail and — combined with the missing-config
  recovery rule below — would have silently *reactivated* `2.1` the next
  time the config went missing, even after an explicit rollback): if the
  dry run fails, the user-approved rollback is a commit that sets
  `default_workflow_version` back to `"1"` and carries a
  `Workflow-Rollback: 2.1` trailer. This reverts the repository-level
  default only; it does not touch any work item created while the default
  was `"2.1"`, since `governing_workflow_version` is fixed at creation
  (stated explicitly here so `WFR`-level "re-verify v1 behavior resumes"
  claims are understood to apply to *new* items only).
- **Missing-config recovery uses the latest event, not just "any
  activation trailer"** (resolves `GPT-R9-007`): the ancestry-limited
  search (D-Commit-Provenance) looks for both `Workflow-Activation` and
  `Workflow-Rollback` trailers reachable from HEAD and takes whichever is
  **most recent by first-parent ancestry**. Config missing and the latest
  such event is a rollback (or none exists) → default to `"1"`, no error.
  Config missing and the latest such event is an activation → hard stop
  with an explicit error naming the recovery (restore or recreate the
  config file from that activation commit's own tree). This is what makes
  rollback actually durable against a subsequently lost config file,
  rather than reactivating on the next lookup.
- **`work_item_kind` schema field** (resolves `GPT-R9-013`, optional):
  `WF8b`'s dry run uses a value (`"synthetic"`) that revision 7's prose
  never added to D3's actual per-item field list — an unknown field
  appearing only in examples. D3 (below) now names `work_item_kind:
  "process" | "product" | "synthetic"` as a real, validated schema field;
  unknown values are rejected by the validator.
- **`WF8b`'s multi-session synthetic-item pointer, made explicit**
  (resolves `GPT-R9-007`): the dry run runs against an isolated synthetic
  work item (`work_item_id: "v2.1-dry-run"`, `work_item_kind: "synthetic"`
  — see D1/D3), and because it is deliberately multi-session, the pointer
  handling must survive session boundaries and interruption:
    1. On entry, WF8b records the prior `active_work_item_id` (the real
       process item) in a small gitignored local marker
       (`.ai-review/runtime/DRY_RUN_RESUME.json`: `{prior_active_work_item_id,
       started_at}`), then sets `active_work_item_id` to `"v2.1-dry-run"`
       and commits that state change — this is what makes the dry run's
       own state resumable across the "start a fresh conversation between
       scenarios" pattern the round-1 scenarios already require.
    2. A fresh session finding `active_work_item_id == "v2.1-dry-run"` and
       the marker file present resumes the dry run in place, exactly like
       any other `IN_PROGRESS` checkpoint resume (D3's worktree-scoped
       dirty-resume rule applies identically).
    3. On successful completion, WF8b restores `active_work_item_id` to
       `prior_active_work_item_id` from the marker, archives/removes the
       synthetic item's `work_items` entry and its `.ai-review/v2.1-dry-run/`
       bundle directory, deletes the marker file, and commits the restore
       — verified by asserting the real process item's own record is
       byte-identical to its pre-dry-run state.
    4. **Interrupted-or-failed recovery**: if the dry run is abandoned
       before step 3 (marker file present, no completion commit), the next
       session finds the marker, reports the dry run's last known state,
       and requires an explicit user decision — resume it, or manually
       restore `active_work_item_id` from the marker's
       `prior_active_work_item_id` and discard the synthetic item. Never
       silently guessed either way.
- **Partial-implementation recovery** (unchanged in spirit): because every
  modified command is dual-mode from the commit that introduces it, a
  session resuming after any subset of WF2/WF4a-ii/WF-M8b/WF5/WF1b/
  WF4a-ii's apply-review amendments have landed still executes correctly.

### D-Legacy — Milestone 8 import (revised: dormant LEGACY_READY → explicit adoption transition, resolves GPT-R9-005)

Revision 7 wrote Milestone 8's import as an immediately-**terminal**
`LEGACY_IMPORTED` entry. Round 9 correctly found this unreachable by any
later command: `/prepare-functional-review`'s entry condition
(`technical_approval.status == CURRENT`) requires *some* command to
actually select Milestone 8 and read its record, and the current v1
`/prepare-functional-review` has no notion of the multi-item state at all
— a terminal, never-`active` entry has no path back into the state
machine. Fixed with an explicit two-phase model:

**Phase 1 — import (WF-M8a, runs during Workflow v2.1's own
implementation)**: creates `work_items["milestone-8"]` (resolves
`OPUS-R6-006`) with **`phase: "LEGACY_READY"`** (dormant, not terminal —
it is a real, addressable, waiting state, distinct from `LEGACY_IMPORTED`)
and one `technical_approval`:

- `basis: LEGACY_V1`, `reviewed_bundle_id: null` (genuinely absent — no
  bundle was ever generated for the four pre-v2 review rounds).
- **`approved_review_content_id`, backfilled with a real value** (resolves
  `OPUS-R6-009`): computed at import time over Milestone 8's actual
  content at `reviewed_content_commit` using the implementation-stage
  projection (WF4a-i's mechanism) — never `null`. The freshness rule
  (D-Approval-Commits) therefore applies to `LEGACY_V1` with **no
  `basis`-branch special-case**: a later commit touching Milestone 8's
  protected paths stales it exactly as it would any other technical
  approval; a metadata-only commit does not.
- `reviewed_content_commit`: the reviewed Milestone 8 head commit (a
  genuine pre-existing reference — no self-reference problem, since this
  commit already exists at import time).
- `legacy_evidence`: the 4 historical v1 review-round summaries.
- `user_confirmation`: the user's explicit acceptance statement.
- `waived_guarantees`: `["no_bundle_id", "no_telemetry"]` (validated enum,
  D2) — **not** `no_content_id`, since a real content ID is now backfilled.
- `work_item_type: "product"`, `work_item_kind: "product"`,
  **`governing_workflow_version: "1"`** (resolves `OPUS-R10-011`: revision
  8 left this unset, which would either fail `supported_versions`'
  creation-time gate outright or require a silent exception to it. `"1"`
  is also factually correct — Milestone 8 was built entirely under v1 —
  and makes adoption an ordinary, auditable version *transition*
  (`"1"` → `"2.1"`) rather than a first-time assignment with no prior
  state to transition from).

`work_items["milestone-8"]` at this point is *not* `active_work_item_id`
(the active item stays `workflow-v2-1-core`, still implementing) — it
simply exists, addressable by ID, dormant, exactly analogous to a paused
process.

**Phase 2 — adoption (a real, user-invoked transition, with an explicit
selector, resolves `OPUS-R10-005`)**: revision 8 gated the `LEGACY_READY`
scan on "before its normal v1 behavior" while separately stating the
dual-mode branch is keyed off "the current work item's
`governing_workflow_version`" — for a dormant item with no
`active_work_item_id` pointing at it, neither reading supplies a way to
*reach* the scan at all, which round 10 correctly named as the actual gap
(a non-terminal phase is necessary but not sufficient; it also needs a
selector). Fixed: `/prepare-functional-review` takes an **explicit,
optional work-item-id argument**, defaulting to `active_work_item_id`
when omitted — `/prepare-functional-review milestone-8`. The
`LEGACY_READY` scan is a **repository-level lookup, not a per-item
behavior**: it runs first, against the *named* item (or the default),
before any version branching — version branching only applies once the
named item is resolved, using *that* item's `governing_workflow_version`,
which by phase 1 is always defined (`"1"`, never absent). Concretely:

1. Resolve the target: the supplied argument, or `active_work_item_id` if
   none given. Refuse with a named error if neither resolves to an
   existing entry.
2. If the target's `phase` is `"LEGACY_READY"`, perform adoption before
   anything else:
   1. Validate the branch-reconciliation precondition below is still true
      (the integrated head is still reachable, `ACTIVE_MILESTONE.md`
      still agrees) — a dormant item could in principle sit for a long
      time, so this is re-checked at adoption, not trusted from import
      time.
   2. **If Milestone 8's `technical_approval` freshness check (recomputed
      against current content) reports `STALE`** — content changed
      between import and adoption — **stop and require a fresh
      implementation-review round**, per the normal `technical_approval`
      lifecycle (D-Approval-Commits); do **not** re-import. A stale legacy
      approval is handled exactly like a stale ordinary one.
   3. Set `active_work_item_id` to the target's ID.
   4. Transition `governing_workflow_version` from `"1"` to `"2.1"` — an
      ordinary, auditable version transition (resolves `OPUS-R10-011`),
      not a first-time assignment. Legal because adoption can only run
      once Workflow v2.1 is fully built (`/prepare-functional-review`'s
      v2.1 branch is what performs it), so the repository default is
      always `"2.1"` by the time this step executes.
   5. Transition `phase` to `AWAITING_FUNCTIONAL_REVIEW`, preserving
      `technical_approval` (`basis: LEGACY_V1`) exactly as imported —
      adoption changes *routing*, never the approval record itself.
3. Proceed with `/prepare-functional-review`'s normal job (write the
   functional-review checklist) against the now-active, now-`CURRENT`
   `technical_approval` — the entry condition genuinely resolves, for the
   first time, against a command that actually reads it.
4. From here, Milestone 8 follows the **normal, unmodified v2.1 path**:
   functional review, `FIXING_FUNCTIONAL_FINDINGS` if needed (broad
   remediation per D-Functional-Remediation's child-work-item rule, below),
   `AWAITING_USER_ACCEPTANCE`, `MILESTONE_COMPLETE` — no special-casing
   past this point, and the original completed Milestone 8 checkpoint
   registry is never mutated or repeated.

Adoption never runs implicitly: a command invoked for a *different*
work-item ID (or the current `active_work_item_id`, if it is not the
dormant item) never touches Milestone 8's entry, regardless of its phase.

**Branch-reconciliation precondition** (unchanged, now explicitly
re-checked at both phases): (1) user integrates `milestone8-cp11-work`
into `main` themselves; (2) Claude verifies branch-head reachability and
that the reachable `docs/ACTIVE_MILESTONE.md` is the branch's version,
refusing with both values named if either check fails; (3) only then
records the `LEGACY_READY` entry (phase 1) or proceeds with adoption
(phase 2).

**Split into two checkpoints** (resolves `OPUS-R10-013`, which found the
combined checkpoint at complexity 6/one-session covering both a data
import and a command-behavior change with genuinely different
dependencies): **`WF-M8a`** (phase 1, import: the `LEGACY_READY` entry,
the backfilled `technical_approval`, the branch-reconciliation checklist)
depends on `WF1b` only — it needs the multi-item schema to write into and
nothing from the approval/commit-lifecycle checkpoints, since importing
writes a record, it does not create a commit through the normal approval
path. **`WF-M8b`** (phase 2, adoption: the selector argument and the
promotion logic on `/prepare-functional-review`) depends on `WF-M8a`
(needs the import's record shape to promote), `WF4a-ii` (the dual-mode
branching framework it extends), and `WF4a-iii` (the freshness
recomputation it calls before promoting). This also makes `WFR-34`'s
verification cleaner: import and adoption are independently testable.

### D-Functional-Remediation (revised: closes the reviewed_implementation_head loop, resolves OPUS-R6-013)

- **No code change**: return directly to `AWAITING_FUNCTIONAL_REVIEW`;
  `technical_approval` untouched.
- **Bounded code change**: mark `technical_approval.status = STALE`
  *before* the first edit, persist that write, make the fix, commit, bump
  `implementation_revision`, **regenerate a `post-fix` bundle** — this is
  the step that writes the new `reviewed_implementation_head` (D-Approval-
  Commits names the bundle generator as its sole writer, at exactly this
  stage) — require a fresh implementation-review round and a fresh
  `/approve-review implementation`, which can now succeed because HEAD
  equals the freshly-written `reviewed_implementation_head`.
- **Broad/multi-finding remediation, as a child work item** (resolves
  `GPT-R9-016`, which correctly found revision 7's "draft a remediation
  plan" left unstated whether the already-approved immutable registry/
  mapping get mutated, replaced, or versioned): a **new, separate work
  item** is created — `work_item_id: "<original-id>-remediation-<n>"`,
  same `work_item_type` as the original, carrying a new field
  `parent_work_item_id: "<original-id>"` — with its **own** registry.json/
  mapping.json (scoped only to the remediation checkpoints), its own
  `plan_approval`/`technical_approval` lifecycle, and its own base commit
  (the original item's final implementation head). It routes through the
  full normal cycle: `AWAITING_EXTERNAL_PLAN_REVIEW` →
  `AWAITING_PLAN_APPROVAL` → implement → `AWAITING_TECHNICAL_APPROVAL` →
  functional retest → `MILESTONE_COMPLETE`, exactly like any other work
  item, because it *is* one.
- **The original item's registry, mapping, and completed-checkpoint
  history are never mutated** — they remain the immutable, approved
  record of what was originally reviewed and built. Nothing about broad
  remediation touches them; the child item's own registry names only the
  new remediation checkpoints.
- **Final acceptance aggregates both**: the original item's own
  `AWAITING_USER_ACCEPTANCE`/`MILESTONE_COMPLETE` transition additionally
  requires every child item naming it as `parent_work_item_id` to have
  reached its own `MILESTONE_COMPLETE` first (a straightforward reverse
  lookup over `work_items`, no new field needed on the parent). A parent
  with an incomplete child cannot be accepted.

**Dependency** (unchanged): WF4c depends on WF4a-iii.

### D-Bundle-Manifest (revised: real worktree-staleness fix, portability vs. local staleness split, NUL-safe parsing, binaries/unusual paths, relayout migration)

`REVIEW_PROTOCOL.md`'s feedback structure requires `Reviewed bundle ID:`
(the `bundle_id`), `Reviewed base commit:`, `Work item:`. The approval gate
rejects feedback whose bundle ID is missing or doesn't match the current
recomputed `bundle_id`, naming both.

**Worktree/HEAD staleness check, replacing `ACTIVE_WORK_ITEM`** (resolves
`OPUS-R6-016`): the first-party Milestone-8 incident was a stale bundle in
a *different worktree*, whose own state file agreed with its own stale
bundle — an `ACTIVE_WORK_ITEM` pointer cross-checked against
`WORKFLOW_STATE.json` cannot catch this, because both files in the stale
worktree were internally consistent with each other. The bundle itself now
records the absolute `worktree_root` (from `.ai-review/runtime/
WORKTREE_IDENTITY.json`, D3) and the HEAD SHA it was generated from, inside
`MANIFEST.md`. `active_work_item_id` (D1) already tracks which work item is
active portably; no second pointer file is needed.

**Portability vs. local staleness, corrected** (resolves `GPT-R9-015`,
which found round 6/7's original phrasing — "any consumer, a reviewer or
`/approve-review`, stops" — would block the one consumer the bundle exists
to serve): an **external reviewer** necessarily receives the bundle
outside the generating worktree, by design — that is not staleness, it is
the archive doing its job. External review validates the exact `bundle_id`
and the reported Git metadata (base/head SHAs, branch), never local
absolute-path equality. The recorded `worktree_root`/HEAD are **diagnostic
metadata only** for that consumer. The worktree/HEAD equality check is
enforced **exclusively by repository-local commands** — specifically
`/approve-review`, which runs inside a real, current worktree and can
meaningfully ask "is this the same worktree and HEAD I'm sitting in right
now" — the actual first-party incident (a *local* command reading a stale
*local* bundle) this mechanism exists to catch.

**`.ai-review/<work_item_id>/` relayout, with a stated migration**
(resolves `OPUS-R6-021`): the new layout
(`.ai-review/<work_item_id>/{current,feedback}/`) is created by WF5, which
owns updating all eight `.claude/commands/*.md` files and
`REVIEW_PROTOCOL.md`'s path references. For the duration of this
milestone's own remaining execution (which is still running out of
`.ai-review/current/`/`.ai-review/feedback/` today), the resolver reads a
**compatibility path**: if `.ai-review/<work_item_id>/` doesn't exist,
fall back to the flat `.ai-review/current`/`.ai-review/feedback` layout. A
grep-based conformance test (WF8a-i) asserts no stale `.ai-review/current`
literal remains outside that one compatibility branch.

**NUL-safe bundle-content parsing, binaries and unusual paths restored**
(resolves `OPUS-R6-019`): `scripts/prepare-ai-review.sh`'s
`git diff --name-status` + `IFS=$'\t' read -r status path` loop is not
NUL-delimited and breaks on paths containing newlines or non-ASCII paths
under `core.quotePath`. WF5's scope includes converting that loop to `-z`
NUL-delimited parsing, matching the manifest's own `-z` usage. Restored to
`WFR`-level acceptance criteria (see "Requirements traceability"):
additions, deletions, renames, mode changes, symlinks, **binaries**, and
**unusual-but-supported paths** (newline-in-path, non-ASCII path) all have
deterministic test vectors, and the bundle's `files/` copy matches the
manifest entry for all of them.

**`.ai-review/` scoping** (unchanged from revision 6): the only physical
review-artifact location (plus the compatibility path above during this
milestone's own transition).

**Work-item-ID grammar** (unchanged): `^[a-z0-9][a-z0-9_-]{0,63}$`.

**`prepare-ai-review.sh` detached-HEAD branch bug** (unchanged):
`BRANCH=$(git branch --show-current); BRANCH=${BRANCH:-"(detached)"}`.

**Stage-completeness / revision-consistency check** (unchanged): `PLAN.md`/
`IMPLEMENTATION_SUMMARY.md`'s stated revision must agree with the
authoritative doc's before a bundle is complete.

## Preserved ownership (unchanged, extended)

`CLAUDE.md`'s gate-count section points at `MILESTONE_WORKFLOW.md`;
`WORKFLOW_V2_AUDIT.md`'s CI row corrected to match WF8a-i/-ii's scope, and
the whole document carries a revision-8 scope note distinguishing audit-of-
repository-state from deferred-to-v2.2 recommendations (`GPT-R9-012`).
`REVIEW_PROTOCOL.md`'s context-efficiency rules carry the source-proposal-
exclusion line (R5-PLAN-007). `docs/ai-workflow/WORKFLOW_CONFIG.json` is
owned by WF1b (creation) and `WF-Activate` (sole writer of
`default_workflow_version` past its initial value, plus the durable
rollback trailer). This round's concrete
`docs/ai-workflow/registry/workflow-v2-1-core-registry.json` and
`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json` were
created directly as reviewed bundle content (`GPT-R9-003`); WF1b's future
scope is the *generator* that produces the Markdown view and creates
these files for any future work item, not this round's own concrete
instance. `.claude/commands/bootstrap-workflow-v2.md` is owned by WF0
(creation) and remains the sole driver of `workflow-v2-1-core`'s own
checkpoints for this work item's entire lifecycle, deleted only at its
`MILESTONE_COMPLETE` (D-Bootstrap, revised per `OPUS-R10-002`/`-003`).
`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`'s path is excluded, not
protected (`GPT-R11-008`'s original protected classification corrected to
excluded per `OPUS-R14-001`/`-009`), and the file itself,
`.claude/commands/review-plan.md`, `.claude/commands/record-manual-plan-review.md`,
and `/apply-plan-review`'s `"2.1"` branch are all owned and created by
`WF4a-iv` — the exclusion classification exists ahead of the content
precisely so `WF4a-iv`'s later creation of the guide never stales a plan
approval, the same proactive-classification discipline
`OPUS-R10-001`/`GPT-R11-008` established, now applied to the correct list.

## Deferred to Workflow v2.2 (not planned here)

Unchanged scope from revision 5: metrics (hooks, status-line, per-session
storage, retention, the background-`SubagentStop` empirical check),
context governance, subagent routing.

### Workflow v2.2 handoff (unchanged from revision 6)

- **Deferred requirements**: the full metrics/hooks/status-line, context-
  governance, and subagent-routing scope from revision 4's checkpoint
  registry (formerly WF3/WF6/WF7).
- **Existing spec to start from**: `.ai-review/source/WORKFLOW_METRICS_SPEC.md`
  (gitignored provenance, still readable, not bundled by default).
- **Prerequisites v2.1 core supplies**: `review_content_id`/`bundle_id` as
  stable identifiers; `WORKFLOW_CONFIG.json` for version-gating; the
  commit-trailer mechanism as a reusable attribution scheme.
- **Recommendation**: collect at least one real product-milestone baseline
  under Workflow v2.1 core's own one-resumable-checkpoint session model
  before changing session boundaries or routing further in v2.2.
- **Explicit user decision required**: whether Workflow v2.2 planning
  starts immediately after `MILESTONE_COMPLETE` or waits for a
  product-milestone baseline first — the user's call, not assumed.

## Requirements traceability (revision 15 — clean, self-contained; see the note under "Round 6 finding disposition" above for why this replaces rather than extends the old WFR-1–61 numbering)

Every requirement below is current as of this revision. Superseded
requirements from earlier revisions (the pre-split single-`bundle_id`
design, rounds 1-4) are not restated — see the note above for why. This
table is a generated, human-readable view of
`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json` (created
revision 8, `GPT-R9-003`) — WFR-32 through WFR-35 were added then; WFR-36
closed WF8a-i's coverage gap (`OPUS-R10-006`); WFR-37 through WFR-40 were
added in revision 10, covering the two-stage plan-review protocol
(`GPT-R11-009`); WFR-41 was added in revision 11, covering the
verdict/state transition table and the manual-ingestion command
`GPT-R12-001`/`-002`/`-003` required; WFR-42 through WFR-44 were added in
revision 12; WFR-45 was added in revision 13, covering the repository-wide
classification obligation `OPUS-R16-001` required; WFR-46 was added in
revision 14, covering the generator-contract fixes `OPUS-R18-001`/`-002`/
`-003`/`-005` required — already implemented against the frozen prototype
today, the same class as WFR-42/-45. **No new requirement this round**:
`OPUS-R20-001`/`-002`/`-003` extend WFR-45/-46's evidence with
forward-looking obligations owed to `WF4a-i` (missing-test items 138-140)
rather than adding new requirements. Every requirement now maps to at
least one checkpoint and every checkpoint owns at least one requirement —
verified by direct query against the JSON files, not by inspection
(re-run and confirmed clean this round: zero unmapped requirements, zero
unowned checkpoints, zero dangling `depends_on`, topological order still
valid).

| Req ID | Requirement | Checkpoint(s) | Verification |
|---|---|---|---|
| WFR-01 | `bundle_id` and `review_content_id` are distinct, independently computed, and independently testable | WF4a-i | Test-vector table implemented as literal test cases — done, `scripts/workflow_fingerprint_test.py` |
| WFR-02 | Wrapper-only bundle changes produce a new `bundle_id` but an unchanged `review_content_id` | WF4a-i | `test_wrapper_word_edit_changes_bundle_id` |
| WFR-03 | External feedback is matched against `bundle_id` exactly; stale/missing feedback is rejected naming both values | WF5, WF4a-ii | Round-N feedback replayed against a round-N+1 bundle rejected |
| WFR-04 | Approval records store both `reviewed_bundle_id` and `approved_review_content_id`; only the latter gates future durability | WF4a-ii | Approving, then regenerating a bundle for unrelated reasons, does not stale an existing approval |
| WFR-05 | The native-git manifest represents additions, modifications, deletions, renames, mode changes, symlinks, **binaries**, and **unusual-but-supported paths** deterministically; unsupported cases fail closed | WF4a-i, WF5 | Test vectors for each case, including newline-in-path/non-ASCII-path/binary fixtures (`OPUS-R6-019`) |
| WFR-06 | The committed plan exactly matches the reviewed working-tree content after the plan-approval commit | WF4a-iii | Worktree-source and commit-source manifests compared post-commit, zero delta — done, `test_complete_manifests_are_equal_after_approval_commit` |
| WFR-07 | Commit-trailer lookup is exact, full-identifier, work-item-scoped, ancestry-limited, requires exactly one match, with a stated tie-break for legitimate duplicates | WF4a-iii | Two work items sharing a checkpoint ID cannot cross-match; a cherry-picked/merged duplicate resolves via the tie-break; a genuine ambiguity stops with a named recovery (`OPUS-R6-022`) |
| WFR-08 | Approval freshness is checked against current committed content, per stage, with no `basis` branch | WF4a-iii, D-Legacy | An unreviewed protected-path commit after approval is detected stale; the `LEGACY_V1` record stales on a protected-path commit exactly like any other (`OPUS-R6-009`) |
| WFR-09 | `WORKFLOW_CONFIG.json`'s repository-level default is separate from any work item's `governing_workflow_version`; missing/corrupt config fails safe to `"1"` **only pre-activation** | WF1a, WF-Activate | Pre-activation missing config → `"1"`, no error; post-activation missing config → hard stop (`OPUS-R6-015`) |
| WFR-10 | `WF-Activate` changes only the repository-level default and records activation in a commit trailer, never Workflow v2.1's own work-item state | WF-Activate | After activation, this milestone's own state still reads `governing_workflow_version: "1"`; the activation trailer is discoverable independent of the config file |
| WFR-11 | The manual dry run exercises real v2.1 command behavior via an isolated synthetic work item, without touching the real process milestone's state | WF8b | Manual scenario; both real work items byte-identical before/after |
| WFR-12 | A failed or interrupted dry run has a defined, user-approved, durably-recorded rollback and resumption path that does not retroactively affect items created under `"2.1"` | WF-Activate/WF8b | A `Workflow-Rollback` trailer reverts the repository default; missing-config recovery after rollback stays on `"1"`; a pre-existing `"2.1"` item's `governing_workflow_version` is unchanged; an interrupted dry run resumes from its saved pointer (`GPT-R9-007`) |
| WFR-13 | Immutable requirement mapping lives in a separate machine-readable file from the mutable execution ledger | WF4b | Mapping file is valid JSON, hashed directly; ledger doc is never a fingerprint input |
| WFR-14 | Checkpoint status/start-commit exist in exactly one writable place (`WORKFLOW_STATE.json`'s `checkpoints[id]`); the registry carries no status column | WF1a, D-Registry | Static schema test; an interrupted checkpoint (state written, `ACTIVE_MILESTONE.md` narrative not) resumes without manual reconciliation (`OPUS-R6-014`) |
| WFR-15 | WF-M8a/WF-M8b and WF4c depend on the checkpoints that build the schema/writer they use | WF1b | Reviewed directly in the checkpoint registry's dependency column; the bidirectional registry × mapping coverage check (below) is itself validator-enforced (`OPUS-R10-006`) |
| WFR-16 | Source proposal files are excluded from normal review bundles | WF5 | `CONTEXT_FILES.txt` generation never includes `.ai-review/source/*` by default |
| WFR-17 | The bundle records its own generation `worktree_root` and HEAD as diagnostic metadata; only repository-local commands (`/approve-review`) stop when their own worktree/HEAD differs — an external reviewer consuming a portable extracted archive is never blocked by local-path equality | WF5 | Two-worktree replay of the Milestone-8 incident stops correctly when triggered by a local command (`OPUS-R6-016`); a simulated external review from an extracted archive with a different path succeeds while local `/approve-review` still rejects the same staleness (`GPT-R9-015`) |
| WFR-18 | `WORKFLOW_STATE.json` is genuinely multi-item: Milestone 8's dormant `LEGACY_READY` entry and the active work item coexist, both non-terminal, and resolve independently by ID; a later explicit adoption transitions the dormant entry to active without disturbing the other | WF-M8a, WF1a | Concurrent dormant + active item persists correctly; `active_work_item_id` naming a terminal or nonexistent entry is rejected; adoption is idempotent and preserves `technical_approval` (`OPUS-R6-006`, `GPT-R9-005`) |
| WFR-19 | Neither new approval gate's entry condition reads the approval record its own exit action writes | WF4a-ii | State-machine conformance: `AWAITING_PLAN_APPROVAL`/`AWAITING_TECHNICAL_APPROVAL` reachable with no prior approval record in existence, for both `REVISE`-with-findings-resolved and `APPROVE` (`OPUS-R6-004`/`-011`) |
| WFR-20 | No gate requires a clean tree that tracked workflow state itself makes unreachable | WF4a-iii | Gate entry succeeds with only `WORKFLOW_STATE.json` dirty; fails with any protected path dirty (`OPUS-R6-005`) |
| WFR-21 | `plan_approval` durability survives checkpoint commits; recomputation is scoped per stage | WF4a-iii | Entry to `IMPLEMENTING` succeeds at checkpoints 1, 2, and N in a fresh session; a plan-document edit after a checkpoint commit still stales it (`OPUS-R6-003`) |
| WFR-22 | `reviewed_implementation_head` has exactly one writer (the bundle generator, implementation/post-fix stage) | WF4c, WF4a-i | Full remediation cycle reaches re-approval; field-writer exclusivity assertion (`OPUS-R6-013`) |
| WFR-23 | The checkpoint-selection algorithm is deterministic across independent fresh sessions | WF2 | Two independent sessions against identical state select the same checkpoint; registry topological-order validation (`OPUS-R6-012`) |
| WFR-24 | User-only enforcement of `/approve-review`/`/accept-milestone` has a mechanism-independent guard, verified against both exposure paths | WF4a-ii | Model-initiated invocation refused independent of the frontmatter flag; empty/reused `user_confirmation` rejected (`OPUS-R6-010`) |
| WFR-25 | The checkpoint registry and requirements mapping are tracked, machine-readable, byte-protected JSON files, created this round (not aspirational); the Markdown views are generated and never separately hashed | WF1b, D-Registry, D4b | The real files exist in the reviewed bundle (`GPT-R9-003`); editing either changes `review_content_id`; reformatting the Markdown view does not (`OPUS-R6-018`) |
| WFR-26 | Every command this milestone modifies appears in the dual-mode enumeration, including `/milestone-plan`, `/apply-plan-review`, `/apply-implementation-review` | WF8a-ii | Golden-output test per modified command under `governing_workflow_version: "1"` (`OPUS-R6-020`) |
| WFR-27 | The `.ai-review/<work_item_id>/` relayout has a stated migration covering this milestone's own in-flight bundle | WF5 | Grep-based conformance test: no stale `.ai-review/current` literal outside the compatibility branch (`OPUS-R6-021`) |
| WFR-28 | `docs/TECHNICAL_DECISIONS.md` carries the fingerprint toolchain decision; CI runs the **hermetic** conformance suite on every PR, never the real-repository demo | WF0 | `.github/workflows/ci.yml`'s "Workflow fingerprint conformance suite" step runs only `workflow_fingerprint_test.py`; CI red on an injected fault; a simulated unrelated future product PR does not fail it (`OPUS-R6-017`, `OPUS-R8-011`, `GPT-R9-002`) |
| WFR-29 | `supported_versions` gates work-item creation; `waived_guarantees` and `work_item_kind` are validated enums | D3, D2 | Out-of-range governing version rejected at creation; unrecognized waiver string rejected; unrecognized `work_item_kind` rejected (`OPUS-R6-024`/`-025`, `GPT-R9-013`, optional) |
| WFR-30 | `WORKTREE_IDENTITY.json` has a named writer and refresh point | WF2 | `IN_PROGRESS` transition creates the file; resume with a missing file stops (`OPUS-R6-026`, optional) |
| WFR-31 | Every tracked `WORKFLOW_STATE.json` field has at least one documented reader | D3 | Schema assertion; `branch`/`last_updated` removed (`OPUS-R6-027`, optional) |
| WFR-32 | A one-time bootstrap path commits the already-reviewed prototype/CI/decision artifacts and the bootstrap continuation command under Workflow v1, without touching `docs/ACTIVE_MILESTONE.md` or `docs/ROADMAP.md` | WF0 | WF0's commit carries both the plan-approval and checkpoint trailers; the bootstrap command never reads either file (`GPT-R9-004`) |
| WFR-33 | The bootstrap continuation command implements exactly one checkpoint per invocation and never loops, so no session relies on command text it edited earlier in that same session | WF0 | Bootstrap invocation stops after one commit; a fresh session is required to continue (`GPT-R9-004`) |
| WFR-34 | A dormant Milestone-8 legacy item transitions to an active, `2.1`-governed item only via an explicit, selector-argument-driven adoption step that consumes the imported technical approval, never runs implicitly, and re-validates branch reconciliation | WF-M8b | Adoption sets `active_work_item_id`/`governing_workflow_version`/`phase`, preserves `technical_approval` unchanged, re-checks the branch-reconciliation precondition, and never fires for a command invoked against a different work item (`GPT-R9-005`, `OPUS-R10-005`) |
| WFR-35 | Broad functional remediation creates a distinct child work item with its own registry/mapping/approval lifecycle; the parent's own registry/mapping/completed-checkpoint history is never mutated; parent acceptance blocks on an incomplete child | WF4c | A child item's registry names only remediation checkpoints; the parent's original registry is byte-identical before/after; parent `MILESTONE_COMPLETE` fails while a child is incomplete (`GPT-R9-016`) |
| WFR-36 | WF8a-i's fixture harness scaffolding is itself verifiable: a documented fixture format that WF8a-ii's cross-checkpoint tests consume | WF8a-i | Every checkpoint has at least one owned requirement, closing the coverage gap `OPUS-R10-006` found (`OPUS-R10-006`) |
| WFR-37 | `/review-plan` implements the local-plan-review role independent of any specific Claude model, in role-oriented state/schema terms, and refuses cleanly outside a `"2.1"`-governed work item at the correct phase | WF4a-iv | Run from at least two supported Claude models, produces the same feedback schema and state transition; refuses against a `"1"` item or the wrong phase, naming why (`GPT-R11-005`, `-011`) |
| WFR-38 | For a `"2.1"` item, plan approval is unreachable without both plan-review stages recorded in order against the same `review_content_id`; any accepted plan edit invalidates both and returns to `AWAITING_LOCAL_PLAN_REVIEW`, never self-declared ready by `/apply-plan-review` | WF4a-iv, WF8a-ii | A single-stage `APPROVE` cannot reach `AWAITING_PLAN_APPROVAL`; a plan edit after either or both stages clears both; `/apply-plan-review`'s `"2.1"` branch always stops at `AWAITING_LOCAL_PLAN_REVIEW` after an edit (`GPT-R11-001`/`-002`/`-003`/`-007`) |
| WFR-39 | The exact bundle path, `bundle_id`, and `review_content_id` handed to the manual external reviewer are displayed by `/review-plan`; a wrapper-only bundle regeneration after local approval does not invalidate the completed local-review stage | WF4a-iv | The displayed identifiers match the completed local-review stage's recorded values; a wrapper-only regeneration (new `bundle_id`, unchanged `review_content_id`) leaves the local stage valid (`GPT-R11-006`) |
| WFR-40 | The concise plan-review operator guide (`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`) is an excluded (not protected) plan-stage artifact, authored against the commands `WF4a-iv` actually builds, so its creation never stales a plan approval (corrected per `OPUS-R14-001`/`-009`, replacing the original "pre-declared protected" requirement, which made `D-States`' checkpoint-invariance claim false the moment `WF4a-iv` wrote it) | WF4a-iv | The path classifies as excluded, not protected (**done**, `test_079`); creating/editing it never changes `review_content_id` (`OPUS-R14-001`) |
| WFR-41 | Each of the six local/manual verdict × entry-state cells in `D-Plan-Review-Stages`'s transition table has a named sole writer, a named destination (or explicit non-transition for `BLOCK`), and stated validation preconditions; a local `REVISE` can never enter `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW`; `/record-manual-plan-review` is the sole writer of the manual-stage ledger and refuses stale, wrong-role, wrong-work-item, wrong-phase, or duplicate ingestion | WF4a-iv | Every transition-table row reproduced by a passing test naming its writer; a local `REVISE` verdict followed by an attempted manual-stage action is refused; `/record-manual-plan-review` rejects each named failure mode independently (`GPT-R12-001`/`-002`/`-003`) |
| WFR-42 | Every path in `PLAN_STAGE_PROTECTED` is present in the working tree at bundle-generation time; plan-stage computation fails closed, naming the path, if one is absent | WF4a-i | `test_100` (real-repo), `test_050`/`test_102` (hermetic, both worktree- and commit-source), `test_101` (full simulated checkpoint sequence leaves the set unchanged) — all **done** (`OPUS-R14-001`/`-006`) |
| WFR-43 | WF0 refuses to create its commit unless the approving feedback carries `Reviewed bundle ID:`/`Reviewed base commit:`/`Work item:`, each matching the bundle it approved; WF0 refuses feedback declaring a `"2.1"`-only reviewer role for this permanently-`"1"` work item | WF0 | WF0 refuses on each of the three missing/mismatched binding fields independently, naming which; WF0 refuses a `local_model_plan_review`/`manual_external_plan_review` role declaration, naming the item's actual governing version; WF0 proceeds on conforming feedback (`OPUS-R14-002`/`-008`) |
| WFR-44 | Manual-ingestion validation is `review_content_id`-equality-hard, `bundle_id`-equality-advisory; the ledger records the feedback's actual `bundle_id` in both cases | WF4a-iv | A wrapper-only regeneration between upload and paste does not block ingestion (warns only); a `review_content_id` change does block it; the ledger's recorded `bundle_id` matches the feedback's, not the current recomputed one, when they differ (`OPUS-R14-005`) |
| WFR-45 | Plan-stage classification covers every path any concurrent operation (not only this work item's own checkpoints) may write; a path this work item's plan declares out of scope is explicitly excluded, never merely unmentioned | WF4a-i | A write to `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`/`docs/milestones/`/`docs/ai-workflow/archive/` while this work item is mid-sequence neither raises nor changes `review_content_id`; a genuinely novel path still fails closed (`OPUS-R16-001`) — **done**, `test_117_120_...`, `test_096` (extended); widened to `app/`, `docs/adr/`, `docs/agent-context/`, `gradle/`, `config/`, `.github/`, and five top-level product docs (`OPUS-R18-004`) — **done**, `test_133`/`test_134`/`test_135`; a genuinely novel path still fails closed, `test_057`/`test_034`/`test_035`/`test_095` (corrected to a path outside every named set); root-level build files deliberately remain unclassified/fail-closed (`OPUS-R20-002`) — **owed**, item 139; the implementation-stage projection must classify a representative source file oppositely from the plan-stage one, derived independently rather than adapted (`OPUS-R20-003`) — **owed**, item 140, `WF4a-i` |
| WFR-46 | `MANIFEST.md` writing is read-only by default and requires an explicit, separately-named invocation; both `bundle_id` and `review_content_id` are computed last and asserted idempotent before the manifest is considered final; `plan_revision` is sourced from the registry JSON, not a caller-supplied literal, and cross-checked against the plan document's declared revision; `REVIEW_REQUEST.md` states `review_content_id` and bundle generation asserts it agrees with `MANIFEST.md` | WF4a-i | `__main__` contains no unconditional write (`test_130`); a read-only invocation leaves `.ai-review/current/` byte-identical (`test_128`); a protected-path edit between compute and recompute is caught (`test_126`); double generation is idempotent for both identifiers (`test_127`); `load_plan_revision` reads the registry and rejects a title/registry disagreement (`test_131`/`test_132`); `REVIEW_REQUEST.md`/`MANIFEST.md` agreement is enforced (`test_137`) — all **done** (`OPUS-R18-001`/`-002`/`-003`/`-005`); the write sequence itself is atomic across the idempotence check, not only across the pre-write `REVIEW_REQUEST.md` check (`OPUS-R20-001`) — **owed**, item 138, `WF4a-i` |

## Checkpoint registry (revision 15: 17 checkpoints, unchanged count — `WF4a-iv` (added revision 10) had its session target widened in revision 11; revisions 12 through 15 (`OPUS-R14-*`/`OPUS-R16-*`/`OPUS-R18-*`/`OPUS-R20-*`) touched no checkpoint's size or dependency, only `PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_*` classification, the registry JSON's own `plan_revision` field, and requirements owned by existing checkpoints; complexity scale defined; this table is a generated view of `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`, D-Registry)

**Complexity scale** (resolves the undefined-units half of `OPUS-R6-023`):
1-2 = Small (single, narrow file change, no cross-checkpoint coordination);
3-4 = Medium-low (one subsystem, straightforward tests); 5-6 = Medium
(multiple files/subsystems, moderate test surface); 7 = Medium, upper end
(multi-session by design, cross-cutting). **Session target is an estimate
with no empirical baseline** (per `WORKFLOW_V2_AUDIT.md`'s own Determination
2 — no prior instrumented milestone exists to calibrate against), stated
here once rather than re-asserted per row.

| ID | Name | Depends on | Complexity | Session target (estimate, no baseline) | Driven by |
|---|---|---|---|---|---|
| WF0 | Bootstrap: commit the already-reviewed prototype/CI/decision artifacts under Workflow v1, create the one-checkpoint bootstrap continuation command (D-Bootstrap) | none | 2 (Small) | 1 | `GPT-R9-004` |
| WF1a | State file schema: multi-item `WORKFLOW_STATE.json` (`active_work_item_id` + `work_items` map, `work_item_kind` field, `checkpoints` map as sole status source, `governing_workflow_version`, no `branch`/`last_updated`) + `WORKFLOW_CONFIG.json` (repository-level default, pre-activation fail-safe, `supported_versions` enforcement) + local-only `WORKTREE_IDENTITY.json` + validator core | WF0 | 5 (Medium) | 1 | round 1-6 + `OPUS-R6-006/015/024/027` + `GPT-R9-013` |
| WF1b | Work-item routing (D1, full text), product/process-milestone init/reset writing into the multi-item map, machine-readable registry file (D-Registry), work-item-ID slug grammar, dual-mode branch for `/milestone-plan` | WF1a | 5 (Medium) | 1 | round 1-6 + `OPUS-R6-018/020` |
| WF4a-i | Canonical fingerprint helpers (**built and twice-corrected as a prototype**: `compute_bundle_id()`, `compute_review_content_id()`, native-git manifest, path classification, test-vector table) — this checkpoint's remaining scope is wiring the already-working prototype into `prepare-ai-review.sh`/`/approve-review`, plus the deferred implementation-stage manifest and its real fixtures | WF1b | 6 (Medium/high) | 1 | round 1-8 + all `PROTO-R7-*`/`OPUS-R8-*` |
| WF4a-ii | Approval states (full D-States blocks applied to `MILESTONE_WORKFLOW.md`), entry/exit split for both new gates, mechanism-independent `/approve-review`/`/accept-milestone` guard (verified against both exposure paths), `REVISE`-with-resolved-findings-can-approve gate, dual-mode branching for `/approve-review`/`/accept-milestone`/`/apply-plan-review`/`/apply-implementation-review`, `CLAUDE.md` gate-count depointer | WF4a-i | 6 (Medium/high) | 1 | round 1-6 + `OPUS-R6-004/010/011/020` |
| WF4a-iii | Approval/checkpoint-completion Git-lifecycle: exact scoped commit-trailer helper with the duplicate-trailer tie-break and recovery (D-Commit-Provenance), per-stage approval-freshness rule, post-approval manifest-match verification | WF4a-ii | 6 (Medium/high) | 1 | round 1-6 + `OPUS-R6-003/022` |
| WF4a-iv | Two-stage local-then-manual-external plan-review protocol (D-Plan-Review-Stages): `AWAITING_LOCAL_PLAN_REVIEW`/`AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` states, durable `plan_review_stages` ledger, verdict/state transition table, model-independent `/review-plan` and `/record-manual-plan-review` commands, `"2.1"`-only revised `/apply-plan-review` exit step, concise `docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md` operator guide. **Mandatory internal commit boundary** (resolves `GPT-R12-006`): states/ledger schema/validator/transition-table write-set land and pass tests first; `/review-plan`'s feedback-writing contract, `/record-manual-plan-review`, the operator guide, and their integration tests land second — a session may end at that boundary without leaving the checkpoint half-done | WF4a-ii | 6 (Medium/high) | 1-2 | `GPT-R11-001/002/003/005/006/007/008/011/012`, `GPT-R12-001/002/003/004/005/006` (session target widened from 1, was undersized once the manual-ingestion mechanism was added) |
| WF-M8a | Milestone 8 legacy import (D-Legacy phase 1): dormant `LEGACY_READY` work-item entry, `LEGACY_V1` record with a real backfilled `approved_review_content_id` and `governing_workflow_version: "1"`, branch-reconciliation precondition, user-performed branch integration checklist | WF1b | 4 (Medium-low) | 1 | round 1-6 + `OPUS-R6-006/009` + `GPT-R9-005` + `OPUS-R10-011/013` |
| WF-M8b | Milestone 8 adoption (D-Legacy phase 2): explicit work-item-id selector argument and dormant-to-active promotion logic on `/prepare-functional-review`, version transition `"1"` → `"2.1"`, stale-legacy-approval-requires-fresh-review handling | WF-M8a, WF4a-ii, WF4a-iii | 4 (Medium-low) | 1 | `OPUS-R10-005/013` |
| WF2 | Resumable `/milestone-implement`: the stated four-rule selection algorithm (D-Selection), entry validation, worktree-scoped dirty-in-progress resume safety, `WORKTREE_IDENTITY.json` writer, checkpoint-complete-vs-all-complete semantics, dual-mode branching, checkpoint-completion trailers via WF4a-iii's helper | WF1b, WF4a-iii | 6 (Medium/high) | 1 | round 1-6 + `OPUS-R6-012/014/026` |
| WF4b | Requirements: immutable mapping file + mutable ledger doc physically separated (D4b), functional-resolution ledger | WF1b | 4 (Medium-low) | 1 | round 1-6 |
| WF4c | General functional-remediation cycle (3-way branch; stale-before-edit ordering; regenerates the post-fix bundle that advances `reviewed_implementation_head`) | WF4a-iii | 5 (Medium) | 1 | round 1-6 + `OPUS-R6-013` |
| WF5 | Review bundle v2 hardening: manifest (both fingerprint fns, never themselves inputs; worktree_root/HEAD recorded for staleness), `.ai-review/<work_item_id>/` relayout with a stated compatibility migration, NUL-safe `-z` parsing in `prepare-ai-review.sh`, binaries/unusual-path test vectors, detached-HEAD + base-SHA script fixes, feedback bundle-ID/base-commit/work-item fields + stale-feedback rejection, source-proposal exclusion, stage-completeness validation | WF4a-i, WF4b | 6 (Medium/high) | 1 | round 1-6 + `OPUS-R6-016/019/021` |
| WF8a-i | Automated conformance/fixture suite, part 1: CI wiring for the existing prototype suite (**done** — `.github/workflows/ci.yml`), plus fixture harness scaffolding for the remaining checkpoints' own test needs | WF1a, WF1b, WF4a-i | 4 (Medium-low) | 1 | `OPUS-R6-017/023` |
| WF8a-ii | Automated conformance/fixture suite, part 2: cross-checkpoint integration fixtures, dual-mode command behavior under both `governing_workflow_version` branches for every command in the completed enumeration, including the bootstrap command's own conformance test (`OPUS-R10-016`) and the two-stage plan-review protocol's integration coverage (`GPT-R11-009`) | WF8a-i, WF2, WF4a-ii, WF4a-iii, WF4a-iv, WF4b, WF4c, WF5, WF-M8a, WF-M8b | 6 (Medium/high) | 1-2 | `OPUS-R6-023` (split from the prior single WF8a) |
| WF-Activate | Sole activation boundary: validates WF8a-ii's conformance suite, flips `WORKFLOW_CONFIG.json`'s repository-level default to `2.1`, commits with a `Workflow-Activation` trailer; also owns the durable `Workflow-Rollback` trailer path and the latest-event missing-config recovery rule (D-Self-Governance) | WF8a-ii | 3 (Small) | 1 | round 1-6 + `OPUS-R6-015` + `GPT-R9-007` |
| WF8b | Manual multi-session dry run: all round-1 scenarios + legacy-import + remediation-cycle + dirty-worktree-resume, run against the isolated synthetic `v2.1-dry-run` work item after activation | WF-Activate | 7 (Medium, upper end) | multiple sessions, deliberately not 1 | round 1-6 |

After WF8b: existing, unmodified `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` →
`AWAITING_FUNCTIONAL_REVIEW` → `AWAITING_USER_ACCEPTANCE` →
`MILESTONE_COMPLETE` gate sequence.

## Missing tests (revision 15 — clean, continuous numbering; items 1-44 from revision 7, 45-54 from revision 8, 55-78 from revision 9, 79-93 from revision 10, 94-99 from revision 11, 100-116 from revision 12, 117-124 from revision 13, 125-137 from revision 14, 138-140 new this round, forward-looking obligations owed to `WF4a-i`; see the note under "Round 6 finding disposition" above)

Items already implemented and passing (prototype rounds, `scripts/workflow_fingerprint_test.py`/`_demo_test.py`) are marked **done**; the rest are checkpoint obligations.

1. plan-stage manifest over untracked authoritative files is non-empty and carries real blob SHAs → **done**;
2. plan-stage manifest is identical whether or not `git add -N` was applied by a prior caller → **done**;
3. a one-byte plan edit changes `review_content_id` → **done**;
4. worktree-source and commit-source manifests are equal under the documented normalization, and the test fails if the `hash-object` substitution is removed → **done**;
5. writing `plan_approval` into `WORKFLOW_STATE.json` does not change `review_content_id` → **done**;
6. a ledger edit does not change `review_content_id` → WF4b;
7. an unclassified path halts manifest computation, naming the path → **done**;
8. every path this milestone creates is classified by exactly one of the protected/excluded lists → **done**;
9. entry to `IMPLEMENTING` succeeds at checkpoints 1, 2, and N in a fresh session → WF2, WF4a-iii;
10. a plan-document edit after a checkpoint commit stales `plan_approval` → WF4a-iii;
11. an `implementation_revision` bump does not stale `plan_approval` → WF4a-iii;
12. `AWAITING_PLAN_APPROVAL`/`AWAITING_TECHNICAL_APPROVAL` are reachable with no approval record in existence, for a `REVISE`-with-findings-resolved round and for an `APPROVE` round → WF4a-ii;
13. `/approve-review` with matching `APPROVE` feedback writes `EXTERNAL_APPROVE` without prompting for override text → WF4a-ii;
14. `/approve-review` without matching feedback refuses to write until literal override text is supplied → WF4a-ii;
15. a `BLOCK` status cannot reach either approval state by any path → WF4a-ii;
16. a gate is reachable with only `WORKFLOW_STATE.json` dirty, and blocked with any protected path dirty → WF4a-iii;
17. a full plan→implement→approve pass creates exactly the three authorized commit kinds and no others → WF8a-ii;
18. Milestone 8's dormant `LEGACY_READY` entry and Workflow v2.1's active item coexist and resolve per work item → WF-M8a (corrected from `WF-M8` per `GPT-R11-010`, following `OPUS-R10-013`'s split);
19. the validator accepts two simultaneously non-terminal, non-active entries (e.g. `LEGACY_READY` alongside an active `IMPLEMENTING` item), and rejects only `active_work_item_id` naming a terminal or nonexistent entry → WF1a;
19a. `/prepare-functional-review` invoked against a `LEGACY_READY` item performs the full adoption transition (active pointer, `governing_workflow_version`, phase → `AWAITING_FUNCTIONAL_REVIEW`) and preserves `technical_approval` unchanged → WF-M8b (corrected from `WF-M8` per `GPT-R11-010`);
19b. adoption re-validates the branch-reconciliation precondition rather than trusting the phase-1 import → WF-M8b (corrected from `WF-M8` per `GPT-R11-010`);
20. the synthetic dry-run item's creation and cleanup leave both real work items byte-identical → WF8b;
21. bundle generation is idempotent: two runs with no repository change produce the same `bundle_id` → **done**;
22. deleting `MANIFEST.md` and recomputing fails closed (required-file check), not silently succeeding → **done**;
23. the recovery path when a bundle is regenerated after feedback arrives is exercised end to end → WF5;
24. the legacy record carries a non-null `approved_review_content_id`, and a post-import protected commit stales it while an allowlisted one does not → WF-M8a (corrected from `WF-M8` per `GPT-R11-010`);
25. model-initiated invocation of `/approve-review` and `/accept-milestone` is refused, independently of the frontmatter flag → WF4a-ii;
26. an empty or nonspecific `user_confirmation`, including one that does not name the exact work item and stage, is rejected (corrected per `GPT-R11-004`, which found this item still described the novelty check `OPUS-R10-010` had already replaced, directly contradicting item 74's positive case below) → WF4a-ii;
27. a `REVISE` round with zero blocking findings reaches approval without `USER_OVERRIDE` → WF4a-ii;
28. two independent fresh sessions against identical state select the same checkpoint → WF2;
29. the validator rejects a registry whose row order violates its dependency column → WF1a;
30. after a post-fix bundle, `reviewed_implementation_head == HEAD` and `/approve-review implementation` is reachable → WF4c;
31. an interrupted checkpoint with state written but narrative not updated resumes without human reconciliation → WF2;
32. all four combinations of (config present/absent) × (activation trailer present/absent) behave per the documented rule → WF-Activate;
33. a bundle generated in worktree A and consumed from a different worktree/HEAD stops, naming both → WF5;
34. CI executes the conformance suite and fails on a deliberately broken fixture → **done** (`.github/workflows/ci.yml`);
35. `review_content_id` is invariant under registry/mapping Markdown-view reformatting and under status writes (status lives only in `WORKFLOW_STATE.json`), and changes on a real registry/mapping edit → WF1b;
36. newline-in-path, non-ASCII-path, and binary fixtures round-trip through both the manifest and `files/` → WF4a-i, WF5;
37. golden-output test per modified command under `governing_workflow_version: "1"`, asserting behaviour identical to today's, for every command in the completed enumeration → WF8a-ii;
38. no stale `.ai-review/current` literal remains outside the documented compatibility shim → WF5;
39. cherry-picked and merged duplicate trailers resolve via the tie-break; a genuinely ambiguous duplicate stops with a named recovery → WF4a-iii;
40. WF8a is split and no checkpoint depending on more than six others is targeted at one session → (registry review itself, verified structurally);
41. an out-of-range `governing_workflow_version` is rejected at work-item creation → WF1a (optional, `OPUS-R6-024`);
42. an unrecognized `waived_guarantees` entry is rejected → D2/WF-M8a (optional, `OPUS-R6-025`);
43. `IN_PROGRESS` transition creates `WORKTREE_IDENTITY.json`; resume with a missing file stops → WF2 (optional, `OPUS-R6-026`);
44. schema assertion that every tracked `WORKFLOW_STATE.json` field has a documented reader → WF1a (optional, `OPUS-R6-027`).

Round 9's items (`GPT-R9-*`), continuing the numbering:

45. only `MANIFEST.md` may report a conforming `bundle_id:` line; the same line in any other bundle file fails closed (`GPT-R9-001`) → **done**;
46. CI runs only the hermetic suite; a simulated future unrelated product PR does not fail it (`GPT-R9-002`) → **done**;
47. the registry and requirements-mapping JSON files exist in the reviewed bundle and are protected; editing either changes `review_content_id` (`GPT-R9-003`) → **done**;
48. WF0's bootstrap commit carries both the plan-approval and checkpoint trailers and is discoverable by the same trailer-search mechanism later checkpoints use (`GPT-R9-004`) → WF0;
49. the bootstrap command implements exactly one checkpoint and stops; it never reads `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md` (`GPT-R9-004`) → WF0;
50. the bootstrap command's per-invocation state-sync (once WF1a exists) reconstructs every already-completed checkpoint's real commit SHA, discovered via trailer search alone, with no dependency on a later checkpoint completing first (`GPT-R9-004`, revised `OPUS-R10-002`) → WF1a;
51. bundle mode is invariant under effective-user execute access — constructed via `st_mode` directly, not `os.access` (`GPT-R9-009`) → **done**;
52. verifying a bundle (importing the module, recomputing) creates no new files inside the bundle directory (`GPT-R9-010`) → **done**;
53. an invalid `work_item_type` is rejected before hashing (`GPT-R9-014`) → **done**;
54. broad remediation creates a distinct child work item with its own registry/mapping, never mutating the parent's; parent acceptance blocks on an incomplete child (`GPT-R9-016`) → WF4c.

Round 10's items (`OPUS-R10-*`), continuing the numbering:

55. plan-stage `review_content_id` recomputes without raising after a checkpoint commit that adds a command file, a script, and the ledger (`OPUS-R10-001`) → **done**;
56. every artifact path declared by any registry checkpoint classifies as protected or excluded (`OPUS-R10-001`) → **done**;
57. a genuinely unknown path still fails closed after the lists are widened (`OPUS-R10-001`) → **done**;
58. at every point in the registry order, exactly one command can select and implement the next incomplete checkpoint (`OPUS-R10-002`) → WF0/WF8a-ii;
59. the bootstrap command is never retired before `workflow-v2-1-core`'s own `MILESTONE_COMPLETE` (revised from a WF2-descendant check per the permanent-driver redesign, `OPUS-R10-002`) → WF0/WF8a-ii;
60. `/milestone-implement` is never invoked for `workflow-v2-1-core` at any point, and the bootstrap command never reads `docs/ACTIVE_MILESTONE.md` (`OPUS-R10-003`) → WF0/WF8a-ii;
61. Milestone 8 cannot be selected by any command before adoption (`OPUS-R10-003`, `-005`) → WF-M8b;
62. the bootstrap command's per-invocation state-sync produces a schema-valid `work_items` entry with no missing fields (`OPUS-R10-004`) → WF1a;
63. `IMPLEMENTING`'s entry condition evaluates true immediately after the state-sync step first runs (`OPUS-R10-004`) → WF1a;
64. a plan-document edit between two bootstrap invocations is detected by the pre-WF1a manual guard and, after WF1a, by the reconstructed `plan_approval` (`OPUS-R10-004`, `-012`) → WF0/WF1a;
65. adoption succeeds from a null `active_work_item_id` in a fresh session, given an explicit work-item-id argument (`OPUS-R10-005`) → WF-M8b;
66. adoption while a different work item is active does not disturb that item (`OPUS-R10-005`) → WF-M8b;
67. the adopted item's `technical_approval` is byte-identical before and after adoption (`OPUS-R10-005`) → WF-M8b;
68. no command performs adoption implicitly for an unrelated target (`OPUS-R10-005`) → WF-M8b;
69. bidirectional registry × mapping coverage: no unmapped requirement, no unmapped checkpoint (`OPUS-R10-006`) → **done** (`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`, `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`);
70. a synthetic work item computes both identifiers without raising, with `work_item_type: "process"` (`OPUS-R10-007`) → WF8b;
71. two work items with interleaved `IN_PROGRESS` dirty work each resume against their own keyed expected set (`OPUS-R10-008`) → WF2;
72. the generated Markdown registry view's row order matches the JSON array order (`OPUS-R10-009`) → WF8a-i;
73. a non-topological registry JSON order is rejected (`OPUS-R10-009`) → WF1a;
74. a confirmation naming the wrong work item or stage is rejected; a correct confirmation repeated across two different approvals is accepted (`OPUS-R10-010`) → WF4a-ii;
75. the imported Milestone 8 entry passes the `supported_versions` creation gate with `governing_workflow_version: "1"`, and adoption transitions it to `"2.1"` (`OPUS-R10-011`) → WF-M8a/WF-M8b;
76. a mid-bootstrap plan edit is detected before the next checkpoint proceeds (`OPUS-R10-012`) → WF0;
77. the bootstrap command stops after exactly one checkpoint and never reads `docs/ACTIVE_MILESTONE.md`, verified by its own conformance test (`OPUS-R10-016`) → WF8a-ii;
78. an excluded prefix without a trailing slash is rejected at import, exercised against the now-populated real prefixes (`OPUS-R10-017`) → **done**.

Round 11's items (`GPT-R11-*`), continuing the numbering:

79. the plan-review-workflow guide's path (`docs/ai-workflow/PLAN_REVIEW_WORKFLOW.md`) classifies as excluded, not protected (corrected per `OPUS-R14-001`/`-009`, replacing the original "protected before it exists" claim) → **done** (`test_079`); creating or editing it never changes `review_content_id`, at any point, before or after `WF4a-iv` writes it → **done** (`test_079`, `test_demonstration_against_real_repo`);
80. a `local_model_plan_review` `APPROVE` alone, with no `manual_external_plan_review` stage recorded, cannot reach `AWAITING_PLAN_APPROVAL` for a `"2.1"` item (`GPT-R11-001`) → WF4a-iv;
81. a `manual_external_plan_review` stage recorded without a current `local_model_plan_review` stage for the same `review_content_id` is rejected (`GPT-R11-001`) → WF4a-iv;
82. both stages recorded, in order, against the same `review_content_id` reaches `AWAITING_PLAN_APPROVAL` (`GPT-R11-001`/`-002`) → WF4a-iv;
83. any protected plan edit after either or both stages complete invalidates both (by the recomputation rule, not an explicit clear) and the work item reads as back at `AWAITING_LOCAL_PLAN_REVIEW` (`GPT-R11-002`/`-003`) → WF4a-iv;
84. `/review-plan`, run from at least two supported Claude models against identical repository state, produces the same feedback schema and the same state transition (`GPT-R11-005`/`-011`) → WF4a-iv;
85. `/review-plan`'s write set is exactly as item 96 states — for `APPROVE`, `REVIEW_FEEDBACK.md` plus its own local-stage ledger fields and phase transition; for `REVISE`/`BLOCK`, `REVIEW_FEEDBACK.md` plus (for `REVISE` only) the phase transition; never the plan, registry, mapping, command, product, or bundle-content files, and never another work item's fields; a fixture asserting no unauthorized tracked file changes detects a deliberately-introduced violation of that set (corrected per `OPUS-R14-003`/`-007`, which found this item still stated the pre-`GPT-R12-002` "writes only `REVIEW_FEEDBACK.md`, never state" claim, directly contradicting item 96's own corrected write set — restated here to agree with it word for word rather than merely cross-referencing it, since two items describing the same write set is exactly the kind of drift that let the contradiction happen the first time) (`GPT-R11-005`, `GPT-R12-002`) → WF4a-iv;
86. `/review-plan` rejects a missing, malformed, stale, wrong-work-item, or wrong-phase bundle/manifest, naming which condition failed, for each case independently (`GPT-R11-005`) → WF4a-iv;
87. the bundle path/`bundle_id`/`review_content_id` `/review-plan` reports for manual upload match the values the completed local-review stage recorded (`GPT-R11-006`) → WF4a-iv;
88. a wrapper-only bundle regeneration after local approval (new `bundle_id`, unchanged `review_content_id`) leaves the local stage valid; a protected-content regeneration (both identifiers change) does not (`GPT-R11-006`) → WF4a-iv;
89. after a manual-external `REVISE` and an accepted `/apply-plan-review` edit, the work item's next required stage is `AWAITING_LOCAL_PLAN_REVIEW` — never directly back to manual-external review or to `AWAITING_PLAN_APPROVAL` (`GPT-R11-007`) → WF4a-iv;
90. Workflow v1 golden outputs for `/milestone-plan` and `/apply-plan-review` are byte-identical to today's under `governing_workflow_version: "1"`; `/review-plan` invoked against a `"1"` item refuses cleanly, naming why (`GPT-R11-005`, v1 compatibility) → WF8a-ii;
91. a confirmation naming the wrong work item or the wrong stage is rejected; a correct confirmation legitimately repeated across two different approvals is accepted — the same rule items 26 and 74 state, restated here as this round's own acceptance check that the two are no longer in conflict (`GPT-R11-004`) → WF4a-ii;
92. bidirectional registry × mapping coverage includes `WF4a-iv` and `WFR-37`–`WFR-40`: no unmapped requirement, no unmapped checkpoint (`GPT-R11-009`) → **done** (`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`, `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`, re-queried above);
93. interruption/resumption succeeds at every manual boundary the two-stage protocol introduces: before local review starts, after local feedback is written, after Sonnet's revision, before the manual bundle upload, and after manual feedback is saved (`GPT-R11`, missing-test item 15) → WF4a-iv;
94. a local `REVISE` verdict transitions the work item to `REVISING_PLAN` and writes no ledger entry; it can never enter `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` (`GPT-R12-001`) → WF4a-iv;
95. a local `BLOCK` verdict leaves the work item at `AWAITING_LOCAL_PLAN_REVIEW` with no ledger write and no transition (`GPT-R12-001`) → WF4a-iv;
96. `/review-plan`'s tracked-file write set for an `APPROVE` verdict is exactly `REVIEW_FEEDBACK.md` plus the resolved work item's local-stage ledger fields and phase transition in `WORKFLOW_STATE.json`; for `REVISE`/`BLOCK` it is `REVIEW_FEEDBACK.md` plus (for `REVISE` only) the phase transition — never the plan, registry, mapping, command, product, or bundle-content files, and never another work item's fields (corrected per `GPT-R12-002`, replacing the prior "writes only `REVIEW_FEEDBACK.md`" claim, which contradicted its own "records the completed stage in the ledger" clause) → WF4a-iv;
97. `/record-manual-plan-review` refuses to write unless a current local `APPROVE` is recorded for the same `review_content_id`; refuses stale, wrong-role, wrong-work-item, or wrong-phase feedback, naming which condition failed; and refuses a second ingestion against an already-recorded manual stage for the same `review_content_id` (`GPT-R12-002`/`-003`) → WF4a-iv;
98. `/record-manual-plan-review`'s tracked-file write set for an `APPROVE` verdict is exactly the resolved work item's manual-stage ledger fields and phase transition; for `REVISE` it is the phase transition only; for `BLOCK` it is nothing — never `REVIEW_FEEDBACK.md`, the plan, registry, mapping, command, product, or bundle-content files (`GPT-R12-002`) → WF4a-iv;
99. a manual `APPROVE` transitions to `AWAITING_PLAN_APPROVAL`, a manual `REVISE` to `REVISING_PLAN`, and a manual `BLOCK` leaves the work item at `AWAITING_MANUAL_EXTERNAL_PLAN_REVIEW` with no ledger write (`GPT-R12-003`) → WF4a-iv;
100. every path in `PLAN_STAGE_PROTECTED` exists at bundle-generation time (`OPUS-R14-001`/`-006`/`-009`) → **done** (`test_demonstration_against_real_repo`, real-repo half; the hermetic half is item 102 below, since the property only has teeth against an absence, which the real repo by construction never has);
101. simulating a full checkpoint-artifact sequence (one commit per excluded prefix/path this milestone's own checkpoints are known to populate) leaves the plan-stage `review_content_id` equal to its approval-time value after every single commit (`OPUS-R14-001`) → **done** (`test_101`);
102. a plan-stage computation with an absent protected path fails closed, naming the path, for both the worktree-source and commit-source manifest builders independently (`OPUS-R14-006`) → **done** (`test_050`, `test_102`);
103. an implementation-stage computation still represents a deletion as a tombstone, not a fail-closed error — the fail-closed rule is plan-stage only (`OPUS-R14-006`) → WF4a-i (the implementation-stage manifest doesn't exist yet; this is a negative constraint on its future design, not testable before it's built);
104. WF0 refuses to create its commit when the feedback lacks `Reviewed bundle ID:`, `Reviewed base commit:`, or `Work item:`, each independently (`OPUS-R14-002`) → WF0;
105. WF0 refuses on a bundle-ID mismatch, naming both values (`OPUS-R14-002`) → WF0;
106. `plan_approval.reviewed_bundle_id` is non-null and equals the value the approving feedback named (`OPUS-R14-002`) → WF0;
107. the approved bundle contains no instruction to correct a protected path after approval (`OPUS-R14-003`) → WF8a-ii (a bundle-completeness/consistency lint, the same class as the existing stage-completeness checks WF5 owns);
108. items 85, 96, and the transition table state identical write sets (`OPUS-R14-003`/`-007`) → **done by hand this round** (item 85 restated to match item 96 verbatim); WF8a-ii owns turning this into an automated documentation-consistency lint so a future edit to one can't silently drift from the other again;
109. every revision statement across the title, body, wrapper, and manifest names the same number (`OPUS-R14-004`) → **done** (`test_plan_title_revision_matches_declared_plan_revision`, real-repo, new — a concrete regression guard against exactly this round's own defect, grepping the title's declared `Revision N` against the demo test's own `plan_revision` argument);
110. `plan_revision` is read from a tracked machine-readable file (the registry JSON), not supplied by a caller (`OPUS-R14-004`) → **not applied this round** (see "Decisions not taken" above) → WF4a-i, once a real production caller exists to source it from anywhere but a hardcoded demonstration default;
111. a deliberately mismatched revision label fails bundle generation (`OPUS-R14-004`) → WF4a-i, same dependency as item 110 (a mismatch can't mechanically fail closed before there's a mechanized source of truth to mismatch against);
112. a wrapper-only regeneration between manual-stage upload and paste does not block ingestion (warns only); a `review_content_id` change does block it (`OPUS-R14-005`) → WF4a-iv;
113. the ledger records the reviewed feedback's actual `bundle_id` in both the matching and mismatched cases (`OPUS-R14-005`) → WF4a-iv;
114. a v1-governed work item's feedback declaring a `"2.1"` stage role is refused, naming the governing version; a v1 item's feedback declaring no role is accepted; a v2.1 item's feedback declaring the correct role is accepted (`OPUS-R14-008`) → WF0 (v1 case), WF4a-iv (v2.1 case);
115. every protected path has a `files/` copy in the generated bundle (`OPUS-R14-009`) → **done** (`test_every_protected_path_has_a_files_copy_in_the_bundle`, real-repo, new);
116. the state-file validator rejects a `plan_review_stages` sub-record whose `verdict` is anything but `APPROVE` (`OPUS-R14-010`) → WF1a (the validator core doesn't exist yet; the rule itself is already stated in D3's validator-rejects list, added last round);
117. a write to `docs/ACTIVE_MILESTONE.md` while the active work item is mid-sequence does not raise and does not change plan-stage `review_content_id` (`OPUS-R16-001`) → **done** (`test_117_120_concurrent_work_item_writes_mid_sequence_do_not_raise_or_change_id`);
118. the same for `docs/ROADMAP.md` (`OPUS-R16-001`) → **done** (same test);
119. the same for a `docs/milestones/` path (`OPUS-R16-001`) → **done** (same test);
120. the same for a `docs/ai-workflow/archive/` path (`OPUS-R16-001`) → **done** (same test);
121. every path named as a write target in any `.claude/commands/*.md` file classifies as protected or excluded (`OPUS-R16-001`) → **done** (`test_096`, extended this round with `docs/ACTIVE_MILESTONE.md`/`docs/ROADMAP.md`/`docs/milestones/completed/…`/`docs/ai-workflow/archive/…`, cross-checked directly against `prepare-functional-review.md`/`accept-milestone.md`'s actual write targets);
122. a genuinely novel path still fails closed after the list is widened (`OPUS-R16-001`) → **done** (`test_057`, re-verified passing against the widened lists — no new test needed, the existing regression already covers this);
123. a conformance check confirms the archival's governing timing is at-or-after `MILESTONE_COMPLETE`, not a scheduled post-approval edit to a protected path (`OPUS-R16-002`) → **done** (`test_archive_prefix_exclusion_is_timed_at_or_after_milestone_complete`, new, real-repo — checks the exclusion justification string itself, the current governing artifact, rather than grepping the plan's own prose, which legitimately quotes the retracted timing in its historical disposition tables);
124. no action the plan schedules between approval and the final checkpoint changes `review_content_id` (`OPUS-R16-002`) → **done by combination**: `test_101` already covers every checkpoint-declared artifact, `test_117_120_...`'s `docs/ai-workflow/archive/` case covers the one action revision 12 had scheduled (now retimed to at-or-after `MILESTONE_COMPLETE`, i.e. never *between* approval and the final checkpoint), and item 123's lint catches a future scheduling mistake before it reaches this state again.
125. `MANIFEST.md`'s `review_content_id` recomputes exactly from the final submitted bundle and working tree (`OPUS-R18-001`) → **done**, `test_125_real_bundle_review_content_id_recomputes_to_its_own_reported_value` (real-repo, new — the `review_content_id` counterpart of item 24's `bundle_id` check);
126. a protected-path edit made after the content ID is computed is caught by a recompute-and-assert step before the manifest is considered final (`OPUS-R18-001`) → **done**, `test_126_protected_path_edit_between_compute_and_recompute_is_caught` (hermetic, new — simulates the mid-flight edit via a mocked `compute_review_content_id_plan_stage` call);
127. double bundle generation with no content change is idempotent for **both** identifiers, not only `bundle_id` (`OPUS-R18-001`) → **done**, `test_127_double_generation_idempotent_for_both_identifiers`;
128. a read-only inspection invocation leaves every file under `.ai-review/current/` byte-identical, verified by hashing the directory before and after (`OPUS-R18-002`) → **done**, `test_128_read_only_inspection_leaves_bundle_directory_byte_identical`;
129. manifest writing requires an explicit, separately-named invocation (`OPUS-R18-002`) → **done**, `test_129_manifest_writing_requires_explicit_named_invocation` (checks the `--write-manifest` flag directly against the source, the governing artifact, per item 123's own precedent);
130. `__main__` contains no unconditional write to the bundle directory (`OPUS-R18-002`) → **done**, `test_130_main_contains_no_unconditional_write`;
131. identity is computed from the registry-declared `plan_revision` with no caller-supplied literal (`OPUS-R18-003`) → **done**, `test_131_plan_revision_loaded_from_registry_not_a_literal`;
132. a disagreement between the registry's `plan_revision` and the plan's title fails bundle generation (`OPUS-R18-003`) → **done**, `test_132_registry_title_disagreement_fails_generation`;
133. a concurrent product-code write (`app/**` plus a regression test) mid-sequence does not raise and does not change `review_content_id` (`OPUS-R18-004`) → **done**, `test_133_concurrent_product_code_write_mid_sequence_does_not_raise_or_change_id`;
134. a concurrent product-documentation write behaves the same (`OPUS-R18-004`) → **done**, `test_134_concurrent_product_doc_write_mid_sequence_does_not_raise_or_change_id`;
135. a genuinely novel path still fails closed after the exclusion list is broadened (`OPUS-R18-004`) → **done**, `test_135_novel_path_still_fails_closed_after_widened_lists` (plus `test_057`/`test_034`/`test_035`/`test_095`, corrected to use a path outside every named set);
136. an exhaustiveness assertion over every path any `.claude/commands/*.md` documents writing (`OPUS-R18-004`) → **done**, `test_136_every_path_named_in_a_command_doc_classifies` (real-repo, new — a best-effort superset scan over every backtick-quoted, existing, non-gitignored path any command doc mentions, stated explicitly as over-inclusive of true write targets rather than a formal per-command writes manifest);
137. `REVIEW_REQUEST.md` and `MANIFEST.md` state the same `review_content_id`, and a deliberate disagreement fails generation (`OPUS-R18-005`) → **done**, `test_137_review_request_and_manifest_review_content_id_agreement`;
138. a protected-path edit injected between the write and the recompute-and-assert step raises **and** leaves `MANIFEST.md` byte-identical to its pre-invocation state (`OPUS-R20-001`) → **owed to `WF4a-i`**;
139. a root-level build file (`build.gradle.kts`/`settings.gradle.kts`/`gradlew`/`Makefile`) still raises `UnclassifiedPathError`, pinning the deliberate fail-closed decision recorded in D-Fingerprint (`OPUS-R20-002`) → **owed to `WF4a-i`**;
140. the implementation-stage classification projection and the plan-stage projection classify a representative source file (e.g. `app/src/main/...`) oppositely — excluded at the plan stage, protected at the implementation stage (`OPUS-R20-003`) → **owed to `WF4a-i`**.

## Usability concerns (resolves round 5/6's undispositioned per-gate reporting requirement, and GPT-R9-004's bootstrap-sequence requirement)

**Bootstrap sequence, exact** (resolves `GPT-R9-004`): once the user gives
explicit go-ahead on this plan bundle, the first three actions are:

1. Claude creates WF0's single commit (plan/audit pair, prototype scripts,
   CI step, technical-decision entry, registry/mapping JSON,
   `.claude/commands/bootstrap-workflow-v2.md`), carrying
   `Workflow-Plan-Approval`/`Workflow-Checkpoint: WF0` trailers. **This is
   the one commit in the entire design created directly on the user's
   conversational go-ahead rather than by a command with its own defined
   authorization gate** — consistent with `CLAUDE.md`'s git restrictions
   ("commit only when a milestone command or prompt explicitly authorizes
   it after verification gates pass"), since the external-review-plus-
   explicit-approval sequence this conversation has been running *is* that
   authorization, and stated here explicitly rather than left implicit.
2. The user runs `/bootstrap-workflow-v2` (or equivalent explicit
   invocation) to implement WF1a, the first real checkpoint. It stops
   after that one checkpoint's commit.
3. The user starts a **fresh session** and runs `/bootstrap-workflow-v2`
   again for WF1b, and so on — one checkpoint, one fresh session, all the
   way through WF8b. `/bootstrap-workflow-v2` remains the sole driver for
   this milestone's own checkpoints throughout (D-Bootstrap, revised per
   `OPUS-R10-002`/`-003`); it is retired only at this work item's own
   `MILESTONE_COMPLETE`, never handed off to `/milestone-implement`.

Because this sequence repeats once per checkpoint across as many as 15
otherwise-identical invocations, `/bootstrap-workflow-v2` states, on every
invocation, which checkpoint it is about to implement and which one it
just completed last time (read from the discovered trailer set) — the
per-gate reporting block immediately below already carries
`current_checkpoint_id` for exactly this purpose; the bootstrap command is
required to actually print it, not merely have it available.

**Per-gate reporting** (resolves round 5's finding, never dispositioned in
revision 6 because it was prose, not a numbered ID — the disposition
gap that finding itself was about): every hard-gate stop and every
`/approve-review`/bootstrap-command invocation reports, together, not
scattered:

- the exact reviewed `bundle_id` and the currently recomputed one, named
  separately, with a stated match/mismatch;
- `approved_review_content_id` (once an approval exists) and the current
  `review_content_id`, with current/stale stated explicitly;
- the repository's default `governing_workflow_version` and the active
  work item's own (fixed-at-creation) value, named separately;
- `active_work_item_id` and its current `phase`/`current_checkpoint_id`;
- whether the current session is running the bootstrap path or normal
  v2.1 commands;
- whether any local-only worktree/HEAD restriction applies to the action
  just taken (D-Bundle-Manifest);
- for a `"2.1"` item mid-plan-review, the `plan_review_stages` ledger's
  current state (which roles have completed against the current
  `review_content_id`, if any) and which stage is next (new, resolves
  `GPT-R11-005`'s per-stop reporting requirement, restated once here
  rather than only in the deferred operator guide);
- the exact next command the user should run.

**Fail-closed error messages name both the check and the fix**
(unchanged expectation from revision 6, now with concrete cases):
`UnclassifiedPathError` names the path **and which of the two lists it
most plausibly belongs in and why** (a `.claude/commands/*`/`scripts/*`
path suggests the corresponding excluded prefix; anything else suggests
checking whether it's new approved design content that belongs in
`PLAN_STAGE_PROTECTED`) — not only the two lists it was checked against,
since this error is expected to fire routinely during the bootstrap
window and the user should not need to read the source to resolve it
(round 10's usability finding); a worktree/HEAD staleness stop names both
the bundle's recorded values and the current ones; a missing-config-after-
activation stop names the recovery (restore from the activation commit's
tree).

## On repeated growth across rounds (self-review, honest — updated)

Checkpoint count: 8 → 11 → 13 → 14 → 13 (round 5) → 13 (round 6) → 14
(revision 7, WF8a split per `OPUS-R6-023`) → 15 (revision 8, WF0
bootstrap checkpoint added per `GPT-R9-004` — a genuinely new capability,
not a resizing split: nothing in revisions 1-7 addressed how this
milestone's own implementation begins under the v1 commands that exist
today) → **16** (revision 9, WF-M8 split into WF-M8a/WF-M8b per
`OPUS-R10-013` — a resizing split this time, not new scope: import and
adoption always had different dependencies, the combined checkpoint just
hadn't been decomposed yet). Round 9 found revision 7's prose plan had
solved several mechanisms' *shape* without specifying execution
end-to-end (execution-completeness). Round 10 (a third, independent
reviewer) confirmed the identity subsystem itself finished — every round-8
regression probe re-verified by execution — and found revision 8's fixes
for round 9's gaps had the *same* class of problem one level up: each new
mechanism (the bootstrap driver, the handoff, Milestone 8's adoption) was
specified for after it finishes existing, not for the transition into
existing. That is a strictly later-stage instance of the same underlying
pattern (design converges toward the steady state before it converges on
how to get there), not a new category of churn — round 10's own framing:
"a converging sequence — each round is finding a strictly later-stage
problem." The correct response, same as rounds 8 and 9: fix what's real,
freeze what's finished (the identity subsystem, per round 10's explicit
instruction), and stop.

**→ 17** (revision 10, `WF4a-iv` added for the two-stage plan-review
protocol per `GPT-R11-009` — genuinely new scope, not a resizing split:
nothing in revisions 1-9 addressed enforcing two distinct reviewer roles
against the same content, because the enhancement itself was proposed for
the first time this round). Unlike rounds 9 and 10, round 11's findings
were not later-stage instances of the identity subsystem's own past
churn — they were gaps in a **newly proposed** capability layered on an
otherwise-stable design, found on its first review pass rather than its
fifth. The identity subsystem itself required no further change beyond
`GPT-R11-008`'s one classification addition, consistent with round 10's
freeze instruction holding.

**Still 17** (revision 11, round 12): round 12 found the two-stage
protocol `WF4a-iv` itself now owns was specified for the steady state
without its own internal transitions actually agreeing with each other —
the same **later-stage-instance** pattern rounds 9 and 10 already named,
this time one layer inside the newly-added `D-Plan-Review-Stages` section
rather than in the identity subsystem it was layered on. Resolved by
widening `WF4a-iv`'s own session target and adding an internal commit
boundary (the plan's own stated alternative to a split, since the missing
piece was one small, mechanical command, not a second coherent
subsystem) rather than adding an eighteenth checkpoint — a deliberate,
smaller-blast-radius choice, recorded here so a later round doesn't
mistake it for the checkpoint split it could equally well have been.

**Still 17** (revision 12, round 14): round 14's findings were about
*scope and binding* — which paths belong in the reviewed/approved set, and
what an approval must name to be traceable — not about the shape of any
checkpoint's own work. `OPUS-R14-001`'s fix (reclassifying one path) and
`OPUS-R14-002`/`-008`'s fixes (two new `D-Bootstrap` requirements) add no
new deliverable to any checkpoint's scope; they correct which existing
deliverable owns which classification. Three new requirements (`WFR-42`–
`WFR-44`) were added without needing a new checkpoint to own them, the
same pattern `OPUS-R10-006`'s original fix used.

**Still 17** (revision 13, round 16): the same shape again — four
classification-list entries and one requirement (`WFR-45`) own no new
deliverable; `OPUS-R16-002`'s retiming touches when an already-scheduled,
already-excluded action happens, not what any checkpoint builds. Revisions
11, 12, and 13 have now each added zero checkpoints while resolving real
defects, which is itself worth noting: the design's remaining churn is
entirely in classification/binding correctness, not in the shape of the
work.

**Still 17** (revision 14, round 18): the pattern breaks for the first
time in the direction of "the generator, not the design" rather than
"classification/binding correctness" — `OPUS-R18-001`/`-002`/`-003`/`-005`
are all fixes to `__main__` and the manifest-writing sequence, none of
which is a checkpoint deliverable at all (`WF4a-i` already owns "wire the
prototype into real commands," which is exactly what a safer generator
is); `OPUS-R18-004` is the same classification-list widening shape as
`OPUS-R16-001`, one requirement (`WFR-46`) added the same way `WFR-45`
was. Four consecutive revisions (11-14) have now added zero checkpoints;
the design's shape has been stable since revision 10's freeze, and this
round's evidence is that the freeze is holding under a genuinely different
kind of adversarial pressure (implementation-level generator safety, not
identity-algorithm correctness) than the three rounds before it.

**Still 17** (revision 15, round 20): the first round with zero blocking
or important findings. The three optional items (`OPUS-R20-001`/`-002`/
`-003`) add no new checkpoint and no new requirement — two are recorded as
forward-looking missing-test obligations against `WF4a-i`'s existing scope
(items 138, 140) and the third is a classification decision stated in
D-Fingerprint's prose plus one pinning test (item 139), not a new
mechanism. Five consecutive revisions (11-15) have now added zero
checkpoints. Round 20's own framing applies directly: the defect
locations have moved steadily outward across rounds — the identity
algorithm (6-8), its inputs and scope (10, 14, 16), the generator around
it (18), and now the generator's own atomicity plus two forward-looking
scope notes for a stage that does not exist yet (20) — which is the
signature of a design that has converged, not one still churning. No
further plan-review round is required; the next external review is the
implementation-review gate.

## Self-review notes (revision 15)

- `docs/ai-workflow/WORKFLOW_V2_PLAN.md` and `WORKFLOW_V2_AUDIT.md` remain
  uncommitted through sixteen review rounds — correct per this
  milestone's own gate sequence (nothing commits before `/approve-review
  plan`, which does not exist yet — see `D-Bootstrap`). Revision 7
  recovered two previously-lost historical lists (round 6's full finding
  text, revision 5's plan text) only by reading prior session transcripts
  and replaced both with clean, self-contained restatements specifically
  so that recovery would never need to happen again. Neither revision 8
  through 12 needed transcript archaeology — rounds 9 through 16's full
  feedback all arrived through the normal review-bundle channel, with two
  exceptions recorded here rather than left implicit: round 13's own
  `REVIEW_FEEDBACK.md` was overwritten on disk by round 14's before that
  command ran, and round 15's own was likewise overwritten by round 16's
  before this one ran — in both cases the exact original text is not
  independently restatable, only the overturning round's own quoted
  account of it (see the notes under "Round 14" and "Round 16 finding
  disposition" above).
- This revision chose the *smaller* of two valid fixes for `GPT-R12-006`
  (widening `WF4a-iv`'s own session target and adding an internal commit
  boundary, over splitting it into `WF4a-iv-a`/`-b`) because the missing
  piece — one small, mechanical ingestion command — is not a second
  coherent subsystem, and a split would have required re-plumbing
  registry/mapping bidirectional coverage for no new capability boundary;
  worth recording as a deliberate judgment call, since `GPT-R12-006` itself
  offered both as equally acceptable.
- `/record-manual-plan-review` was deliberately kept as its own small
  command rather than folded into `/apply-plan-review` or
  `/approve-review plan` (`GPT-R12-002`/`-003`'s own explicit warning
  against the latter): the local stage already separates review
  (`/review-plan`) from editing (`/apply-plan-review`) cleanly, and the
  manual stage now mirrors that same separation — the new command records
  a verdict and transitions state, `/apply-plan-review` still does the only
  thing it has ever done (read feedback, apply findings, edit the plan).
- Revision 13 deliberately did **not** mechanize `plan_revision`'s source
  (`OPUS-R14-004`'s optional second half) — see the "Decisions not taken"
  note under "Round 14 finding disposition" above, and its own "Update
  (revision 14, `OPUS-R18-003`)" addendum. **This revision reverses that
  deferral**: `OPUS-R18-003` found `__main__` had become the real caller
  the deferral was waiting for, so `plan_revision` now comes from the
  registry JSON (`load_plan_revision`), cross-checked against the plan's
  own title. Recorded here so a later round reads this as a premise
  changing, not a second-guess of revision 12's original judgment.
- This revision deliberately did **not** archive rounds 5–11's disposition
  tables into `docs/ai-workflow/archive/` (`OPUS-R14-012`, optional) —
  **retimed this round from "immediately after this document's next
  approval" to "at or after this work item's own `MILESTONE_COMPLETE`"**
  (resolves `OPUS-R16-002`, which correctly found the original timing
  recreated the exact defect `OPUS-R14-003` fixed for item 85: scheduling
  an edit to `WORKFLOW_V2_PLAN.md`, a protected path, between an approval
  and the next commit stales that same approval, with no "approved with
  corrections" transition in the state machine to survive it). No live
  approval depends on the plan doc's content once `MILESTONE_COMPLETE` is
  reached, so archiving there is free in exactly the way archiving
  immediately-post-approval was not.
- This revision chose the *larger* of two valid fixes for `OPUS-R10-002`/
  `-003` (the permanent bootstrap driver, over a named version-gate
  exemption) because it removes an entire class of future finding (any
  interaction between this milestone's own execution and the transfer
  point) rather than patching the specific interaction found — worth
  recording as a deliberate judgment call, not the reviewer's literal
  "smallest corrective change," should a later round need to know why.
- This revision deliberately did **not** write the concise operator
  guide's content, only its path and classification (excluded, not
  protected, as of `OPUS-R14-001`/`-009` — corrected here from earlier
  rounds' "protected" framing, now stale) — see the Round 11 disposition
  narrative above for why the content itself is deferred (documenting
  commands that don't exist yet either restates this plan a second time or
  guesses at implementation detail this plan leaves to `WF4a-iv`).
  Recorded here so a later round doesn't mistake the empty guide for an
  oversight.
- Re-confirmed zero product "Open decision" rows (`docs/TECHNICAL_DECISIONS.md`)
  beyond the one this milestone itself added are touched.
- This revision stated `OPUS-R16-001`'s classification obligation as a
  general rule in D-Fingerprint, not only as four new list entries,
  because the finding's own framing was general ("the classifier's input
  is repository-wide") and a future work item hitting the same class of
  gap under a different path name is exactly the failure mode a rule
  (not just an instance) prevents — the same judgment `OPUS-R14-001`'s own
  restated invariance obligation already made one round earlier.
- This revision also fixed one stale claim outside the two required
  findings: an earlier self-review bullet still said the operator guide's
  path was given a "protected classification," which became false the
  moment `OPUS-R14-001` reclassified it excluded and was never corrected
  in this section specifically. Judged in-scope despite round 16's own
  "nothing else should change" instruction, since leaving a live,
  self-contradicting claim in the document is the same class of defect
  `OPUS-R14-004` was about — a one-word fix, not a redesign.
- This revision applied the write-then-recompute-and-assert discipline to
  `bundle_id` too (`BundleIdNotIdempotentError`), not only the
  `review_content_id` fix `OPUS-R18-001` explicitly required — `bundle_id`
  previously only printed a boolean rather than failing closed on a
  mismatch. Judged in-scope as the smaller, more consistent fix (both
  identifiers now get the same treatment) rather than leaving a stated
  asymmetry the finding itself called out as backwards ("if anything the
  asymmetry should run the other way").
- **Deliberate choice**: `write_manifest_with_verified_identifiers`
  requires `REVIEW_REQUEST.md` to already state the correct
  `review_content_id` *before* it is called, rather than writing that
  field itself. This follows `scripts/prepare-ai-review.sh`'s own existing
  contract for author-written bundle files ("create empty stubs only if
  missing, never overwrite") — `REVIEW_REQUEST.md` is a human-authored
  narrative document, and the identifier line is one more fact the author
  states there, verified rather than auto-populated. The intended
  operator flow: run the read-only default to see the identifier, state
  it in `REVIEW_REQUEST.md`, then run `--write-manifest`, which recomputes
  and asserts the two still agree.
- This revision did **not** widen `PLAN_STAGE_EXCLUDED_PREFIXES`/`_PATHS`
  by deriving them from a formal per-work-item write-set declaration —
  `OPUS-R18-004`'s own suggested fix explicitly offered the closed-set
  exclusion as "the right move now" and the structurally cleaner
  alternative as `WF4a-i`-sized. Recorded here so `WF4a-i` inherits the
  finding's own framing rather than rediscovering it.
- **Deliberate choice (`OPUS-R20-002`)**: root-level build files
  (`build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `.editorconfig`,
  `Makefile`) are left unclassified and fail closed rather than added to
  `PRODUCT_SCOPE_JUSTIFICATION` alongside `app/`/`gradle/`. The finding
  itself offered both options as acceptable; this revision took the
  smaller one, on the reasoning that these files change rarely, the
  failure is loud and names the path, and widening the exclusion set has
  a real ongoing cost (each entry is a path whose future change can no
  longer stale an approval). Recorded here, with the reversal condition
  stated explicitly (a genuine concurrent write is later observed to
  raise and block unrelated work), so a later round reads this as a
  stated decision, not an oversight `OPUS-R18-004`'s pattern would
  otherwise suggest fixing reflexively.
- Round 20's three optional findings were folded into this revision as
  documentation (D-Fingerprint prose, one classification decision, three
  missing-test obligations) rather than as source changes to
  `scripts/workflow_fingerprint.py` — consistent with round 20's own
  instruction that none blocks implementation and each is "small enough
  to fold into the checkpoint that already owns the file." No test in
  `scripts/workflow_fingerprint_test.py` changed this revision; items
  138-140 are owed to `WF4a-i`, the checkpoint that will give
  `write_manifest_with_verified_identifiers` and the classification
  constants their first real implementation-stage caller.

## Explicit non-goals (unchanged from round 5)

No metrics/hooks/status-line/context-governance/subagent-routing work in
this milestone (deferred to Workflow v2.2, now with an explicit handoff);
no `BOOTSTRAP` approval basis.
