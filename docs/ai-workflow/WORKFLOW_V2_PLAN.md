# Workflow v2.1 core — Refined Plan (Revision 27)

Status: plan review complete — round 20 (`OPUS-R20-*`) returned
`Status: APPROVE`, no blocking or important findings, against revision 14.
Per `/apply-plan-review`'s own step 7 (`governing_workflow_version: "1"`
path), this revision is reported ready for implementation; the command
does not auto-start `/milestone-implement` and no further plan-review
round is required. Process/tooling milestone only — no product code, no
`docs/ROADMAP.md`/`docs/ACTIVE_MILESTONE.md` changes, no Milestone 9.

**Revision 22** (see "WF8b finding disposition (revision 21 → 22)"
below): self-discovered, continued `WF8b` scope, exactly like revisions
16 and 21 before it. Fixes
`docs/ai-workflow/dry-run/WF8B_FINDING_continued_scope_remediation_no_nonterminal_return_path.md`:
a continued-scope implementation round that leaves its parent checkpoint
(`WF8b`) incomplete had no safe, non-terminal way to be functionally
accepted — the documented `AWAITING_FUNCTIONAL_REVIEW → AWAITING_USER_ACCEPTANCE
→ /accept-milestone → MILESTONE_COMPLETE` path, walked as written, would
mark the whole 17-checkpoint item complete while `WF8b` itself remains
unexecuted. See the decision section `D-Scoped-Remediation-Acceptance`
below for the full design, which critically revises the finding's own
proposed mechanism (no new phase is introduced — see that section's
"Departures from the finding's proposed design" subsection for why).
External plan review of this revision (`GPT-R36-*`) returned `Status:
REVISE`, two blocking and one important finding.

**Revision 23** (see "WF8b finding disposition (revision 22 → 23)"
below): applied `GPT-R36-001`/`-002`/`-003` to
`D-Scoped-Remediation-Acceptance`: `complete_work_item`'s registry guard
became fail-closed against an omitted or foreign registry argument (not
merely a caller convention); `/accept-scoped-remediation` gained its own
dedicated, metadata-only provenance commit before reporting success,
matching the durability precedent `plan_approval`/`technical_approval`/
`WF-Activate` already set, rather than a plain, uncommitted state write;
and the `scoped_remediation_acceptance` evidence schema was bound directly
to the accepted round's `implementation_revision`,
`reviewed_implementation_head`, and a committed digest of the
functional-review checklist actually walked, not only the
technical-approval content id. External plan review of this revision
(`GPT-R37-*`) returned `Status: REVISE`, three blocking and one important
finding.

**Revision 24** (see "WF8b finding disposition (revision 23 → 24)"
below): applied `GPT-R37-001`/`-002`/`-003`/`-004` to
`D-Scoped-Remediation-Acceptance`: `complete_work_item` no longer accepts
a caller-supplied `registry` dict at all — it resolves and loads the work
item's own `registry_path` itself (reusing `D3`'s existing
safe-path/tracked/parse registry loader), so a fabricated matching-ID
dict can no longer substitute for the authoritative file;
`/accept-scoped-remediation`'s replay identity is now keyed per round
(`outstanding_checkpoint_id`/`implementation_revision`, discovered via a
new `Workflow-Scoped-Remediation-Acceptance` trailer search) instead of
per checkpoint, so an exact replay is idempotently reported ahead of the
ordinary phase guard while a distinct, later-reviewed round against the
same still-outstanding checkpoint is permitted rather than permanently
refused; every "nine-field"/"nine fields" reference to the
`scoped_remediation_acceptance` entry schema is corrected to the ten
fields the schema has actually always shown; and the pre-commit evidence
guard now refuses an uncommitted (staged or unstaged) edit to the
functional-review checklist path before computing or recording its blob,
checked both at first read and again immediately before the provenance
commit. External plan review of this revision (`GPT-R38-*`) returned
`Status: REVISE`, two blocking and one important finding.

**Revision 25** (see "WF8b finding disposition (revision 24 → 25)"
below): applied `GPT-R38-001`/`-002`/`-003` to
`D-Scoped-Remediation-Acceptance`: `/prepare-functional-review` now
creates a dedicated, metadata-only checklist-evidence commit (a new
`Workflow-Functional-Checklist` trailer, idempotent by content) before
stopping for the user, so the documented preparation flow actually
produces the clean, committed checklist `/accept-scoped-remediation`'s
pre-commit evidence guard requires, rather than leaving that guard
permanently unsatisfiable; replay/duplicate classification for
`/accept-scoped-remediation` is now performed by one shared resolver
function, called identically by the entry guard and the provenance-commit
step, that always loads and compares the discovered commit's own recorded
round fields before ever reporting a replay — a matching trailer key
alone is no longer sufficient; and the entry guard's ordering is now
stated explicitly: current-turn user confirmation is validated before any
replay classification runs, for both a first execution and an exact
replay alike. External plan review of this revision (`GPT-R39-*`)
returned `Status: REVISE`, two blocking findings.

**Revision 26** (see "WF8b finding disposition (revision 25 → 26)"
below): applies
`GPT-R39-001`/`-002` to `D-Scoped-Remediation-Acceptance`: the
`Workflow-Functional-Checklist` trailer's value now embeds the committed
checklist blob itself (`<work_item_id>/<implementation_revision>/<blob>`),
so a legitimately revised checklist for the same round produces a
distinct, unambiguous trailer value instead of colliding with the
generic exactly-one-match contract that a same-round content revision
would otherwise trip; discovery becomes a round-scoped enumeration that
selects the first-parent-nearest-to-`HEAD` evidence commit as the round's
current evidence, with genuine duplicate-content ambiguity still refused
exactly as every other trailer scheme here already does; and
`resolve_scoped_remediation_round` now loads and compares the full
canonical set of round-binding identity fields — including the live
active work-item pointer, the checklist path, and the acceptance-record
schema version/shape — rather than the three-field subset revision 25
compared, returning a dedicated malformed-record result when the schema
itself is unsupported or incomplete. External plan review of this
revision (`GPT-R40-*`) returned `Status: REVISE`, one blocking and one
important finding.

**Revision 27 (this revision, pending its own external plan review — see
"WF8b finding disposition (revision 26 → 27)" below)**: applies
`GPT-R40-001`/`-002` to `D-Scoped-Remediation-Acceptance`: acceptance is
no longer bound to whichever checklist evidence commit happens to be
newest at accept time. The required `scoped_remediation` user
confirmation must now explicitly name the exact
`Workflow-Functional-Checklist` evidence commit SHA and checklist blob
`/prepare-functional-review` reported to the user; `/accept-scoped-remediation`
resolves that named commit, verifies it belongs to the exact live round
and its trailer blob matches its own committed blob, and refuses outright
— naming both the confirmed and the current evidence — when a newer
evidence commit for the round exists, rather than silently substituting
it. The `scoped_remediation_acceptance` schema grows an eleventh field,
`functional_checklist_evidence_commit`, alongside the existing
`functional_checklist_blob`, and `acceptance_record_version` advances to
`2`; `resolve_scoped_remediation_round`'s canonical comparison set grows
to eight fields to match. No automatic migration of an earlier
confirmation to newer evidence exists anywhere in this design: a
corrected checklist always requires a fresh `/prepare-functional-review`
report and a fresh, explicitly-bound user confirmation. Not yet
implemented; not yet approved. No other design choice from revision 22,
23, 24, 25, or 26 is reopened.

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

## Round 25 finding disposition (revision 17) — self-discovered origin, first external review round (`OPUS-R25-*`, `Status: REVISE`)

Unlike rounds 6-20 (external reviewer findings, `OPUS-R*`/`GPT-R*`/`PROTO-R*`
prefixes) and the out-of-band `OPUS-R24-*` classification-only remediation
recorded directly in `WORKFLOW_STATE.json`'s `plan_approval.user_confirmation`
(which widened `PLAN_STAGE_EXCLUDED_PREFIXES` with `docs/ai-workflow/dry-run/`
— a code-only change to an already-excluded path, requiring no plan-text
amendment and provably no `review_content_id` change to any *design*
content, so it was correctly never given its own round-disposition section
here even though, in hindsight, a one-line cross-reference here would have
made this document and the code agree on what `docs/ai-workflow/dry-run/`'s
exclusion actually is — not fixed retroactively in this revision, out of
scope for this finding), this round originates from Claude's own attempt to
execute WF8b's first scenario (S1, `docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`)
against the isolated synthetic `v2-1-dry-run` work item. S1 was **not
executed**: no plan, registry, mapping, or bundle file was created for
`v2-1-dry-run`, and repository/workflow state is confirmed unchanged. Full
reproduction, root cause, and impact analysis:
`docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md`.

**Note on this section's own numbering**: revision 16's design (below,
superseded by the corrected version this revision carries) was itself then
put through this work item's ordinary single-stage
`AWAITING_EXTERNAL_PLAN_REVIEW` gate. The external reviewer's own feedback
independently chose the prefix `OPUS-R25-*` for that pass — the first
externally *reviewed* round this finding has had, even though it is
sequentially the same "Round 25" this section was already using for the
self-discovery narrative below. Both are documented in this one section
rather than split across two, since they describe the same finding's two
stages (discovery, then review) rather than two different findings.

**Finding, `WF8B-S1-001`**: `scripts/workflow_fingerprint.py --work-item-id
v2-1-dry-run` (read-only, no `--write-manifest`) reported
`workflow-v2-1-core`'s own `plan_revision`, `protected_paths`, and
`review_content_id` verbatim, not `v2-1-dry-run`'s. Root cause:
`PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES`
are module-level constants naming exactly `workflow-v2-1-core`'s own five
files, used as the *default* parameter value of
`compute_review_content_id_plan_stage`/`compute_review_content_id_plan_stage_at_commit`/
`write_manifest_with_verified_identifiers` — and every real caller (the
CLI's `__main__`, `scripts/workflow_fingerprint.py:1747-1752`/`:1771-1775`;
and `scripts/workflow_state.py`'s `approval_review_content_id`,
`:654-660`/`:681-685` — the **sole** call site in that module touching any
of `fingerprint.PLAN_STAGE_*`/`compute_review_content_id_plan_stage_at_commit`,
confirmed by exhaustive grep, not an open-ended surface) accepts these
defaults rather than resolving a set specific to the `work_item_id` it was
actually given. `approval_review_content_id` gates `approval_is_current`,
`implementing_entry_reachable`, and `verify_post_approval_manifest_match` —
i.e. `IMPLEMENTING` entry and `/approve-review plan`'s durability check, for
**every** work item, not only the CLI's own diagnostic output. `--work-item-id`
is threaded only into `resolve_bundle_dir` (which bundle *directory* to
read/write), never into which content is protected.
`WORKFLOW_STATE.json`'s per-item `plan_path`/`registry_path` fields (D3,
populated for every work item since `WF1b`) are consequently dead data as
far as plan-stage fingerprinting is concerned today — stored, but never
read by any fingerprint call site.

| ID | Disposition | Resolved in | Evidence |
|---|---|---|---|
| `WF8B-S1-001` | **Accepted, design corrected this revision after external `REVISE`; implementation still deferred to a dedicated fix session, tracked within `WF8b`'s own continued scope, before S1 is re-attempted** | D-Fingerprint-Generalization (rewritten this round), D3 (`mapping_path` field, four validator rules — two new this round), D-Registry/D4b (cross-reference correction), Missing tests (items 141-160) | Confirmed directly against `scripts/workflow_fingerprint.py:468-469` (`DEFAULT_REGISTRY_PATH`/`DEFAULT_PLAN_PATH`), `:515` (`PLAN_STAGE_PROTECTED`), `:1730` (`--work-item-id` argparse default), `:1749-1750`/`:1773` (hardcoded `work_item_id="workflow-v2-1-core"` literals in `__main__`), and `scripts/workflow_state.py:654-660`/`:681-685` (`approval_review_content_id`'s own hardcoded-default parameters, never overridden by any of its three callers `approval_is_current`/`implementing_entry_reachable`/`verify_post_approval_manifest_match`) — full detail in `docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md` and `.ai-review/feedback/REVIEW_FEEDBACK.md` (`OPUS-R25-*`). |

**Why this is a plan revision, not a code-only fix (unlike `OPUS-R24-*`)**:
`OPUS-R24-*` added one classification-list entry to already-designed
machinery; nothing about *how* plan-stage identity resolves per work item
changed. This finding requires a genuinely new mechanism — a per-work-item
metadata source, a resolution algorithm, and a fail-closed validation
matrix that did not exist in any prior revision's design — so it is new
design content belonging in D-Fingerprint/D3, not an operational data
change to an already-approved mechanism. Per this document's own
governance (any edit to `WORKFLOW_V2_PLAN.md` is plan-stage protected),
that content requires the same review-and-approval cycle as any other
decision here, run exactly like every prior mid-implementation plan
revision this work item has already been through (revisions 12 through 15
all landed while later checkpoints — `WF-M8a` onward — were already
`COMPLETE`).

**Relationship to `WF4a-i`**: `WF4a-i` (`COMPLETE`) already named this exact
gap as deferred, unfinished scope — its own registry description ends
"...generalize protected-path derivation beyond this one process plan," and
D-Registry's revision-9 future-work note independently commits to the same
generalization (`GPT-R9-011`). `WF4a-i` was marked `COMPLETE` without it
landing. This revision does not reopen `WF4a-i` (checkpoint completion is
not revoked; its own commit and trailer remain valid history) — it
specifies the previously-deferred design so a dedicated implementation
session can execute it under the ordinary implementation-review/
technical-approval cycle before S1 is retried. No new checkpoint ID is
created; the work is tracked as continued `WF8b` scope, the same checkpoint
whose own S1 scenario surfaced the gap (`WF8b` is explicitly "multiple
sessions, deliberately not 1" and already tracks scenario-blocking findings
this way under `docs/ai-workflow/dry-run/`).

### `OPUS-R25-*` external review disposition (revision 16 → 17, `Status: REVISE`)

The first externally reviewed round of this finding's design (revision 16,
bundle `bundle_id: c508f5b7…`, `review_content_id: f7aeff4985…`) came back
`REVISE`: sound direction, five `HIGH`-severity defects and ten further
gaps, none requiring reopening the frozen identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` (the reviewer's own explicit scope confirmation). All fifteen
findings validated against the actual repository before disposition —
independently confirmed, not taken on the reviewer's word: the live
`plan_revision: 15` (state) vs. `16` (registry) divergence `OPUS-R25-002`
named was reproduced directly; `render_manifest_md`'s missing
`work_item_id`/`plan_revision`/`base_commit` fields (`OPUS-R25-005`);
`approval_is_current`/`verify_post_approval_manifest_match` both passing
`plan_revision=work_item.get("plan_revision")` (`OPUS-R25-002`); `scripts/
prepare-ai-review.sh` containing no reference to `workflow_fingerprint`,
`MANIFEST`, or `--write-manifest` anywhere (`OPUS-R25-012`); `resolve_bundle_dir`'s
silent flat-path fallback (`OPUS-R25-005`); `workflow_fingerprint.py:361-365`'s
"synthetic … borrows the real process item's" comment (`OPUS-R25-011`);
and `workflow_test_harness.py`'s existing `plan_stage_protected_paths`
workaround and `write_plan_docs`' five-fixed-path fixture shape
(`OPUS-R25-014`) — all confirmed by direct read/grep, all accurate.

| ID | Severity | Disposition | Resolved in |
|---|---|---|---|
| `OPUS-R25-001` | HIGH | **Accepted.** Acceptance criterion 1 (and test 142, WFR-50) pinned revision 15's approved digest as a literal, which revision 16's own protected-content changes make unreproducible by construction — the criterion demanded the impossible. Restated mechanism-relatively: the migrated JSON sets and the pre-migration Python constants must produce byte-identical digests on the same tree, compared directly, never against a hardcoded literal. | D-Fingerprint-Generalization "Backward compatibility" paragraph; acceptance criterion 1; missing tests 141-142, 150; WFR-50 |
| `OPUS-R25-002` | HIGH | **Accepted.** `plan_revision` had two sources (registry JSON per the design; `WORKFLOW_STATE.json` per the actual `approval_review_content_id` call sites) and they were already divergent in the reviewed bundle (`15` vs. `16`). Collapsed to one: the registry JSON alone; `WORKFLOW_STATE.json`'s field becomes a validated, non-authoritative mirror. Corrected the live divergence in this same commit. | Authoritative-source table; resolution algorithm; D3 (new mirror-consistency validator rule); migration steps 1, 6; `WORKFLOW_STATE.json` |
| `OPUS-R25-003` | HIGH | **Accepted.** `plan_path`/`registry_path`/`mapping_path` were resolved from state independently of the protected-path set loaded from the artifacts file, with no check that the two agree — a work item could name one file as its plan while a different file was actually hashed. Added a path-to-role binding check (resolution step 11); `mapping_path` gains a real consumer. | Resolution algorithm step 11; new `PlanStageMetadataNotProtectedError`; fail-closed matrix condition 7; acceptance criterion 3; missing tests 143, 146; WFR-47, WFR-48 |
| `OPUS-R25-004` | HIGH | **Accepted.** No writer or ordering was specified for a new work item's `plan_path`/`registry_path`/`mapping_path`/artifacts-declarations file, so S1 would still fail after the fix, one error deeper. Added a "Creation path" block naming both writers, the default artifacts template, and the ordering that avoids circularity; conceded `/milestone-plan` needs a text change after all. | New "Creation path" subsection; `route_work_item`/`default_work_item` gain `mapping_path`; acceptance criterion 4; missing tests 143, 149, 151; WFR-47, WFR-49 |
| `OPUS-R25-005` | HIGH | **Accepted.** `MANIFEST.md` recorded no `work_item_id`; the CLI's `--work-item-id` default stayed the literal `"workflow-v2-1-core"`; `resolve_bundle_dir` falls back to the flat path silently — once `--work-item-id` is made meaningful, a mis-invocation becomes destructive cross-item manifest overwriting instead of harmless. Added the three fields, a `BundleWorkItemMismatchError` write-time check, and a live-`active_work_item_id` CLI default. | "Manifest/bundle bound to an explicit work item" subsection; new `BundleWorkItemMismatchError`; fail-closed matrix condition 12; acceptance criterion 5; missing tests 147, 149; WFR-49 |
| `OPUS-R25-006` | MEDIUM-HIGH | **Accepted.** Migrating the classification sets into `<work_item_id>-artifacts.json` moved them from implementation-stage-protected (`scripts/`) to excluded-at-both-stages, silently removing their approval binding and falsifying the file's own stated invariant. Carved the file out as implementation-stage protected by exact path; migration corrects the stale invariant text. | "Artifacts declarations file gets a real approval binding" subsection; migration steps 2, 7; missing test 141 (extended); WFR-48, WFR-50 |
| `OPUS-R25-007` | MEDIUM | **Accepted.** Duplicate-metadata detection lived only in `validate_state`, a write-time check the resolution algorithm's own read path never calls, and matched only exact-triple equality. Moved into `resolve_plan_stage_metadata` itself (resolution step 9), widened to per-field uniqueness; `validate_state` keeps the same widened rule as a write-time backstop. | Resolution algorithm step 9; fail-closed matrix condition 8; missing test 146 |
| `OPUS-R25-008` | MEDIUM | **Accepted.** `plan_path`/`registry_path`/`mapping_path` were consumed with no grammar — an absolute value silently replaces `repo_root` under `pathlib` join semantics; `../` escapes the worktree; `work_item_id` read from a state key was never independently checked against its own grammar. Added a shared path-grammar validator (resolution step 6) and an explicit `validate_work_item_id` call (step 2). | Resolution algorithm steps 2, 6; new `InvalidPlanStageMetadataPathError`; fail-closed matrix condition 4; acceptance criterion 8; missing test 146 |
| `OPUS-R25-009` | MEDIUM | **Accepted.** The nine-condition matrix and its "nine sub-cases" test item actually described ten sub-cases, and conditions 7-8 (per-item revision mismatch; per-item unclassified-path fail-closed) had no test anywhere. Matrix rebuilt to twelve conditions given the three genuine additions above; every condition now has a named test. | Fail-closed matrix (rebuilt); missing tests 141-160 (renumbered/added); WFR-48 |
| `OPUS-R25-010` | MEDIUM | **Accepted.** Placing the resolver in `workflow_state.py` (which already imports `workflow_fingerprint`) would have created an import cycle; the design also stated the hardcoded `PLAN_STAGE_*` defaults would remain live on the public functions, unchanged — the exact root cause this finding fixes, left standing as a silent-fallback trap for any future caller. Relocated to `workflow_fingerprint.py`; the three classification parameters become required once the migration's test suite is green. | "Module placement" paragraph; migration steps 4-6; acceptance criterion 7; missing tests 141, 150; WFR-47 |
| `OPUS-R25-011` | MEDIUM | **Accepted.** The resolution algorithm never consulted `work_item_kind`, while acceptance criteria required a `"synthetic"`-kind item to get its own distinct digest — directly contradicting `scripts/workflow_fingerprint.py:361-365`'s standing comment that a synthetic item "borrows the real process item's" fingerprint. Stated explicitly: `work_item_kind` is not consulted; superseded code comment rewritten in the same migration commit. | Resolution algorithm step 4; migration step 7; acceptance criteria 4, 11; missing test 151 |
| `OPUS-R25-012` | MEDIUM | **Accepted.** The affected-commands audit checked five `.claude/commands/*.md` files but not `scripts/prepare-ai-review.sh`, the actual code every plan-stage bundle generation runs — which contains no reference to the fingerprint module at all, so `MANIFEST.md` was only ever written by a separate, easy-to-omit manual CLI step. Specified the fix: `prepare-ai-review.sh` gains the `--write-manifest` call for the `plan` stage. | "`prepare-ai-review.sh` shares the same authoritative path" subsection; migration step 8; acceptance criterion 5; missing tests 147, 149, 151; WFR-47 |
| `OPUS-R25-013` | MEDIUM | **Accepted, resolved by clarification, not new mechanism.** Commit-source resolution at any commit predating the schema-version-2 migration was unaddressed, and the durability guarantee `D-Approval-Commits` rests on implicitly assumes it always works. Confirmed the already-specified fail-closed conditions (3, 5) correctly and deliberately raise for any such commit — no command needs pre-migration commit-source recomputation in practice; stated as an explicit, deliberate boundary rather than left silent. | Fail-closed matrix condition 11; "Backward compatibility" point 1; missing test 145 (extended) |
| `OPUS-R25-014` | LOW | **Accepted.** Migration steps named the production modules but not `workflow_test_harness.py`, which encodes the exact hardcoded-default workaround (`plan_stage_protected_paths`) this revision retires, and whose fixtures cannot express a second work item's own artifacts file today. Added a migration step extending the harness. | Migration step 9; acceptance criterion 10; missing tests 143, 144, 149, 150 |
| `OPUS-R25-015` | LOW | **Accepted.** Migration step 8 described a second `/approve-review plan` round happening after implementation, contradicting the "Restart discipline for S1" paragraph's statement that approval happens immediately on this bundle's own `APPROVE`. Deleted the contradictory step; the migration's final step now correctly points at the implementation-review/technical-approval cycle. | Migration step 11 (renumbered); "Restart discipline for S1" paragraph |

No finding required reopening the identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` — confirmed by direct inspection of every corrected section
against those five, not merely by repeating the reviewer's own scope
claim: none of the fifteen corrections above touches `compute_bundle_id`,
`_canonical_json`, any `_snapshot_*` function, `D-Plan-Review-Stages`,
`D-Registry`'s file-format text, `D-Bootstrap`, or `D-Legacy`.

**Scope discipline**: this revision touches D-Fingerprint-Generalization
(rewritten in full, incorporating all fifteen corrections above), D3 (the
`mapping_path` field, now four validator rules — the two revision 16 added
plus the mirror-consistency and per-field-uniqueness rules `OPUS-R25-002`/
`-007` require), D-Registry/D4b (unchanged from revision 16's
cross-reference correction), Missing tests (items 141-160, corrected and
extended), Requirements traceability (WFR-47 through WFR-50, wording
corrected to match), Self-review notes, and — as a live data fix, not new
design — `WORKFLOW_STATE.json`'s `plan_revision` field for
`workflow-v2-1-core` (corrected to match the registry, per `OPUS-R25-002`).
It still does not reopen the identity algorithm's own
hashing/canonicalization, the two-stage plan-review protocol, the
registry/mapping *file format*, `D-Bootstrap`, or `D-Legacy`.

## Round 26 finding disposition (revision 17 → 18) — second external review round (`OPUS-R26-*`, `Status: REVISE`)

Revision 17's own bundle (`bundle_id: 56e66705…`, `review_content_id:
c517f260…`) went through this work item's ordinary single-stage
`AWAITING_EXTERNAL_PLAN_REVIEW` gate a second time. The verdict: sound,
substantial improvement over revision 16 — all fifteen `OPUS-R25-*`
findings independently re-verified against the actual repository, not
merely against the disposition table's own claims — but still not safe to
implement, because the defect class `WF8B-S1-001` exists to eliminate *an
identity-bearing fact resolved from a literal, or from a second,
uncrosschecked rule, rather than from the requested work item* survives in
three concrete places the round-17 corrections introduce or leave
standing. Three `HIGH`, two `MEDIUM`, two `LOW` findings; zero require
reopening the identity algorithm, the two-stage plan-review protocol, the
registry/mapping file formats, `D-Bootstrap`, or `D-Legacy` (confirmed by
inspection, not by repeating the reviewer's own scope claim).

| ID | Severity | Disposition | Resolved in |
|---|---|---|---|
| `OPUS-R26-001` | HIGH | **Accepted.** Matrix condition 12's manifest-binding check fired only when an existing `MANIFEST.md` *names a different* `work_item_id` — every manifest in this repository today names none, so the check was inert on exactly the first post-migration write it exists to protect. Split condition 12 into three named outcomes (absent → binds; present, names the resolved item → agrees; present, names no `work_item_id` or a different one → refuses), added the one-time rebinding migration step for `.ai-review/current`, and specified the comparison parses the manifest's own `field: value` header lines rather than substring-scanning (avoiding the false-positive trap in this bundle's own exclusion-justification prose). | "Manifest/bundle bound to an explicit work item" subsection (corrected); fail-closed matrix condition 12 (split); migration (new rebinding step); missing test 152 (extended), new test 161; acceptance criterion 5; WFR-52 |
| `OPUS-R26-002` | HIGH | **Accepted.** `prepare-ai-review.sh` was specified to resolve the bundle *directory* and the manifest's *work item* by two different rules in the same invocation (`--work-item-id <work-item-id-or-live-active>` — a hyphenated "or", not one rule — while the script's own `ROOT_DIR` choice reads only the explicit third argument). Under this remediation's own commitment to keep `active_work_item_id` at `v2-1-dry-run` throughout, the ordinary flat invocation `./scripts/prepare-ai-review.sh <base-sha> plan` would bind `workflow-v2-1-core`'s bundle to `v2-1-dry-run`'s manifest. Corrected to one rule: the script resolves the work-item id exactly once (its own third argument if given, else the live `active_work_item_id`) and uses that single resolved value for both `ROOT_DIR` and `--write-manifest`. | "`prepare-ai-review.sh` shares the same authoritative path" subsection (corrected); acceptance criterion 5; missing tests 149, 153 (extended), new test 162; WFR-49, WFR-52 |
| `OPUS-R26-003` | HIGH | **Accepted.** The CLI's `--work-item-id` literal default was retired, but the `base` positional argument kept a hardcoded literal of `workflow-v2-1-core`'s own base commit as its default — and `base_commit` is a hashed member of the plan-stage projection, so a second item's `review_content_id` would silently incorporate core's base commit whenever `base` was omitted. Compounded by `base_commit`'s writer being explicitly out of scope (revision 17's own self-review note), leaving no resolved per-item value to fall back to. Closed both halves together: `base_commit` gains a named writer (`route_work_item`/`default_work_item` gain the parameter, populated at creation time, mirroring `plan_path`/`registry_path`/`mapping_path`'s own ordering), the `base` positional resolves from the requested item's own `base_commit` (required, fails closed when `null`, never a literal), and `MANIFEST.md`'s `base_commit` field is checked against the resolved item's declared value at write time, the same pattern condition 12 already uses for `work_item_id`. | "Creation path" subsection (extended: `base_commit` added as a third sole-writer fact); "Effect on `--work-item-id`" subsection (corrected: `base` positional literal retired); new fail-closed matrix condition 13; acceptance criteria 3, 4, 5, 9, 11; missing tests 149, 151, 154 (extended), new test 163; WFR-47, WFR-49, WFR-51 |
| `OPUS-R26-004` | MEDIUM | **Accepted.** `OPUS-R25-006`'s fix was specified as an exact-path `protected_paths` entry containing a literal `<work_item_id>` placeholder — but `classify_path_implementation_stage` (`workflow_fingerprint.py:967`) tests `if path in protected_paths`, a plain exact-match dictionary with no templating, so a key spelled with the literal characters `<work_item_id>` matches nothing and the file falls through to the `docs/ai-workflow/registry/` excluded prefix, exactly as unprotected as before. Corrected: each work item's own `<id>-artifacts.json` declares, within its own `implementation_stage.protected_paths`, the concrete literal path to itself (e.g. `workflow-v2-1-core`'s own file names `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`, spelled out); `generate_artifacts_declarations` emits that self-referential concrete entry automatically for every new item as part of the default template — general by construction, not by a placeholder the exact-match classifier cannot read. | "Artifacts declarations file gets a real approval binding" subsection (corrected); migration step 2 (corrected); acceptance criterion 1; missing tests 155, 156 (corrected); WFR-50 |
| `OPUS-R26-005` | MEDIUM | **Accepted.** Cross-referencing every WFR row's evidence column against missing-test items 141-160 found three items (145, 157, 159) mapped to no requirement — not cosmetic in these three cases: 145 is the sole coverage for fail-closed matrix condition 11 and `OPUS-R25-013`'s pre-migration boundary disposition; 157 is the sole coverage for `OPUS-R25-011`'s `work_item_kind: "synthetic"` correction; 159 is the sole coverage for acceptance criterion 10's harness change. Added to the evidence columns: 145 → WFR-50, 157 → WFR-49, 159 → WFR-47. `docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`'s own schema (D4b) carries only `{description, checkpoint_ids}` per requirement, no per-test evidence field — the traceability table above is, and has been since revision 9, the sole locus of requirement↔test-item mapping; adding a test-evidence field to the mapping file's format would reopen D4b's frozen file format, which this finding does not ask for and this revision's own scope discipline (confirmed above) does not touch. ~~The mapping file's `WFR-47`/`-49`/`-50` **descriptions** already state the same requirement content the corrected table rows describe, so no JSON edit is needed to satisfy this finding.~~ **This last sentence was false and is struck rather than silently corrected: `OPUS-R27-001` found six table rows rewritten this round (`WFR-47` through `WFR-52`, not only the three named here) against a `workflow-v2-1-core-mapping.json` left completely untouched — `WFR-47`'s JSON description did not mention `base_commit` at all. The per-test-evidence reasoning immediately above is unaffected and remains correct; only the description-content claim was wrong. See "Round 27 finding disposition" below.** | Requirements traceability table (WFR-47, WFR-49, WFR-50 evidence columns corrected); no `workflow-v2-1-core-mapping.json` change (reasoned above, corrected `OPUS-R27-001`) |
| `OPUS-R26-006` | LOW | **Accepted.** Missing-test item 146 enumerated seventeen sub-cases while its own summary said "sixteen" — the exact defect class `OPUS-R25-009` raised against revision 16, reproduced in the corrected text — and sub-case group (4a-c) was ambiguous between three *fields* and three *rule violations*. Corrected: "sixteen" → "seventeen"; (4a-c) restated as three fields, each independently exercised against the same representative rule violation (absolute path), with the three rule violations (absolute, `../` traversal, non-tracked/symlinked target) named as the minimum vector set per field rather than a separate 3×3 matrix. | Missing test 146 (corrected) |
| `OPUS-R26-007` | LOW | **Accepted.** Fail-closed matrix condition 11 and "Backward compatibility" point 1 cross-referenced each other for the same statement, which lives in neither — the substantive disposition is the `OPUS-R25-013` row of the Round 25 disposition table above. Corrected: both now point directly at that row instead of at each other. | Fail-closed matrix condition 11 (corrected); "Backward compatibility" point 1 (corrected) |

No finding required reopening the identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` — confirmed by direct inspection of every corrected section
against those five: none of the seven corrections above touches
`compute_bundle_id`, `_canonical_json`, any `_snapshot_*` function,
`D-Plan-Review-Stages`, `D-Registry`'s file-format text, `D-Bootstrap`, or
`D-Legacy`.

**Scope discipline**: this revision touches D-Fingerprint-Generalization
(the manifest-binding subsection, the `prepare-ai-review.sh` subsection,
the "Effect on `--work-item-id`" subsection, the "Creation path"
subsection, the artifacts-declarations subsection, the fail-closed matrix,
the migration steps, and the acceptance criteria — all corrections above),
Requirements traceability (WFR-47, WFR-48, WFR-49, WFR-50, WFR-51, WFR-52
evidence columns/wording corrected — six rows, corrected `OPUS-R27-009`;
this list previously omitted WFR-48), Missing tests (items 146, 149,
151-156, 161-163 corrected/added), and Self-review notes. It still does not reopen the
identity algorithm's own hashing/canonicalization, the two-stage
plan-review protocol, the registry/mapping *file format*, `D-Bootstrap`, or
`D-Legacy`. No file under `scripts/` or `.claude/commands/` is edited by
this revision — every correction above is design text; the dedicated fix
session that follows approval is where the code actually changes, exactly
as revision 17's own self-review notes already stated for its own scope.

**Restart discipline for S1** (consolidated into one paragraph revision 19,
`OPUS-R27-008`; the two versions revision 18 carried side by side had
drifted — one pointed "above" at the other, which was actually below it,
and the other still said "`plan_revision` itself moves 16 → 17," revision
17's own transition, not revision 18's — both defects were resolved by
merging into the single statement below rather than correcting each copy
separately a third time; kept as one paragraph through this revision's own
`20 → 21` bump, updated in place, `OPUS-R28-012`'s standing-invariant
convention): once this revision is reviewed and approved (a fresh
`/approve-review plan` round for `workflow-v2-1-core`, producing a new
`plan_approval` record against this revision's own `review_content_id` —
which necessarily differs from every prior revision's, since
`plan_revision` itself moves 20 → 21 and this document's own protected
bytes changed, exactly as expected for any revision bump, and exactly the
point `OPUS-R25-001`'s correction makes explicit) and the
deferred implementation lands and independently passes its own
`AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`/`AWAITING_TECHNICAL_APPROVAL`
cycle, S1 is re-attempted **from the beginning, in a fresh session** —
never resumed from `v2-1-dry-run`'s current `PLANNING`/`plan_revision:
1`/empty-`checkpoints` state, even though that state is confirmed
untouched by this finding's own discovery and by every REVISE round since
alike — exactly as
`docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md`'s
own "Next step" section requires. `active_work_item_id` remains
`v2-1-dry-run` throughout this remediation's own review/implementation
(D1: a resume-focus pointer, not an execution lock — this remediation is
process work on `workflow-v2-1-core` itself, not on the dry-run item, so it
never needs to repoint the active pointer). `WF8b` remains `IN_PROGRESS`;
S1 remains unexecuted; no `v2-1-dry-run` plan artifact was created or
touched by applying this REVISE, exactly as confirmed against the working
diff of `WORKFLOW_STATE.json` before this round's own review began.

## Round 27 finding disposition (revision 18 → 19) — third external review round (`OPUS-R27-*`, `Status: REVISE`)

Revision 18's own bundle (`bundle_id: 46cf3d02…`, `review_content_id:
05c89e24…`) went through this work item's ordinary single-stage
`AWAITING_EXTERNAL_PLAN_REVIEW` gate a third time. The verdict: all seven
`OPUS-R26-*` findings independently re-verified — three fully resolved
(`-003`, `-006`, `-007`), three resolved in design text but not yet holding
end to end (`-001`, `-002`, `-004`), one resolved on a claim about
`workflow-v2-1-core-mapping.json`'s contents that was factually false
(`-005`) — still not safe to implement, for three concrete `HIGH` reasons,
plus four further gaps. Zero require reopening the identity algorithm, the
two-stage plan-review protocol, the registry/mapping file formats,
`D-Bootstrap`, or `D-Legacy` (confirmed by inspection).

| ID | Severity | Disposition | Resolved in |
|---|---|---|---|
| `OPUS-R27-001` | HIGH | **Accepted.** Six traceability table rows (`WFR-47` through `WFR-52`) were rewritten in revision 18 and `workflow-v2-1-core-mapping.json` was not touched, on an explicit claim about that file's contents (Round 26 disposition row `OPUS-R26-005`; the revision-18 self-review notes) that was false — the JSON's `WFR-47` did not mention `base_commit` at all. Synced all six `description` values in the mapping JSON to their revision-18 table-row content (backticks and round-citation parentheticals stripped, matching the style every other requirement's `description` already uses); struck and corrected the false sentence in the `OPUS-R26-005` disposition row and in the revision-18 self-review notes; added a grep-based conformance test (item 166) enforcing the "generated view" claim going forward rather than merely asserting it. | `workflow-v2-1-core-mapping.json` (`WFR-47`-`WFR-52` descriptions); `OPUS-R26-005` disposition row (corrected); Self-review notes (corrected); missing test 166 |
| `OPUS-R27-002` | HIGH | **Accepted.** `approval_review_content_id`'s `artifacts_path` parameter still defaulted to `fingerprint.DEFAULT_ARTIFACTS_PATH` (a literal naming `workflow-v2-1-core-artifacts.json`) for `stage="implementation"`, and `approval_is_current` never passed it — the same "one literal retired while a structurally identical sibling survived" pattern `OPUS-R26-003` named for the `base` positional, recurring a third time, this time for the implementation stage. A second work item's `technical_approval` would durability-check against `workflow-v2-1-core`'s own protected/excluded sets, not its own. Resolved by bringing it in (the finding's option (a)): `artifacts_path` for the implementation stage is now resolved per work item via `fingerprint.artifacts_path_for_work_item(work_item_id)` — the same helper the plan-stage resolver already introduces — for every caller that actually reaches `approval_review_content_id` with `stage="implementation"` (corrected, `OPUS-R28-001`: that is `approval_is_current` and `verify_post_approval_manifest_match`, not `implementing_entry_reachable`, which only ever calls `approval_is_current(..., stage="plan", ...)` and never needs `artifacts_path`; `promote_legacy_work_item` calls `load_implementation_stage_classification` directly and is likewise unaffected); the literal default is retired in the same migration step as the plan-stage ones. | "Artifacts declarations file gets a real approval binding" subsection (extended); migration steps 4, 6, 7 (extended); acceptance criteria 14, 15 (extended); new acceptance criterion 20; missing test 156 (extended), new test 164; WFR-47, WFR-50 |
| `OPUS-R27-003` | HIGH | **Accepted.** The single-resolution rule fixed `OPUS-R26-002`'s "or" but left three consequences unstated: the flat `.ai-review/current` layout would be silently retired the first time the corrected script ran (since the resolved value would never be empty), migration step 6a's in-place rebinding would then be rebinding a directory nothing writes to again, and — independent of both — the bundle's *content* (derived from `<base-sha>` and the working tree) was still bound to nothing. Resolved: the work-item id is now a **required** third argument to `prepare-ai-review.sh` for `stage == "plan"` specifically (never falling back to the live `active_work_item_id` for this stage; the flat, optional-third-argument layout is explicitly retained, unchanged, for every other stage, where `MANIFEST.md` is never written — the one legitimate "explicitly authorized" exception). The resolved item's own declared `base_commit` is cross-checked against the `<base-sha>` positional *before* any bundle content is generated — applying fail-closed matrix condition 13 (which already names "CLI-argument" among the sources that must agree) as an early script-level refusal, not only a downstream manifest-write check — which is the fact that ties content, directory, and manifest to one source. Migration step 6a is retimed into a physical one-time relocation (`.ai-review/current` → `.ai-review/workflow-v2-1-core/current`, `.ai-review/feedback` → `.ai-review/workflow-v2-1-core/feedback`, the latter closing the `resolve_feedback_dir` scoped-directory gap the review also named) followed by the same rebinding write, now at the relocated path; `MILESTONE_WORKFLOW.md`/`REVIEW_PROTOCOL.md`'s printed paths are updated in the same commit. | "One authoritative work-item selection rule, not two" subsection (corrected); migration steps 6a (retimed), 8 (corrected); acceptance criterion 13 (corrected), new acceptance criterion 21; missing tests 153 (corrected), 162 (corrected), new test 165; WFR-27, WFR-49, WFR-52 |
| `OPUS-R27-004` | MEDIUM | **Accepted.** The "absent `MANIFEST.md` → binds" first-write branch is unreachable as specified: `resolve_bundle_dir` returns the scoped path only if it *already exists*, so the CLI's `--write-manifest` path (which resolves through it) hard-errors on a genuinely new item's directory instead of creating and binding it — the exact invocation shape `WF8B-S1-001` itself used. Resolved: `write_manifest_with_verified_identifiers`, given an explicit, resolved `work_item_id`, now creates its own scoped directory (`mkdir -p`, mirroring `prepare-ai-review.sh`'s own `FILES_DIR` creation for the driven case) rather than deferring to `resolve_bundle_dir`'s existence-gated fallback; `resolve_bundle_dir`'s own scoped-else-flat resolution is retained unchanged for its original read-only/inspection use, where it is harmless. A fallback into another item's flat bundle is no longer reachable from the write path at all. | "Manifest/bundle bound to an explicit work item" subsection (corrected, first outcome); "Creation path" subsection's bundle-directory-identity paragraph (corrected); fail-closed matrix condition 12 (precondition stated); new acceptance criterion 22; missing test 161 (extended); WFR-52 |
| `OPUS-R27-005` | MEDIUM | **Accepted.** Missing-test item 162 still accepted either the single-resolution design or the two-rule "or" design `OPUS-R26-002` explicitly rejected, so it could not fail under a regression to the rejected shape. Superseded by the `OPUS-R27-003` fix itself: with the work-item id now a required argument for the plan stage, there is no longer a fallback branch for `ROOT_DIR` and `--write-manifest` to disagree about — item 162 is restated as the simpler, unambiguous assertion that the omitted-argument invocation refuses outright. | Missing test 162 (restated); acceptance criterion 13 (corrected, folded into `OPUS-R27-003`'s resolution above); WFR-49, WFR-52 |
| `OPUS-R27-006` | MEDIUM | **Accepted.** `WFR-48`'s evidence column named items 146-148 and 158 — conditions 1-10 — but conditions 11, 12, and 13 (items 145, 152/161, and 163) had their sole coverage recorded under `WFR-50`, `WFR-49`, and `WFR-49` respectively, never under `WFR-48`, the requirement that actually owns the thirteen-condition matrix. Added items 145, 152, 161, 163 to `WFR-48`'s evidence column (the requirement's own JSON `description` was already corrected as part of `OPUS-R27-001`'s fix above; this is the table's verification-column half). | Requirements traceability table, `WFR-48` verification column |
| `OPUS-R27-007` | LOW | **Accepted.** Fail-closed matrix condition 12's own sentence said "all **three** disagreeing sub-cases" while enumerating two (12a, 12b) — the three-outcome prose subsection above it is correct (absent/agrees/disagrees, of which exactly two disagree); only the matrix restatement conflated the counts. Corrected to name the two disagreeing sub-cases explicitly, of the three named outcomes. | Fail-closed matrix condition 12 (count corrected) |
| `OPUS-R27-008` | LOW | **Accepted.** Two stale statements: (a) the `OPUS-R25-002` resolution evidence still said "both now read `17`" after two further revision bumps; (b) two consecutive "Restart discipline for S1" paragraphs had drifted — one's own cross-reference pointed the wrong direction, the other still described revision 17's `16 → 17` transition. (a) corrected to the current value; (b) resolved by merging the two paragraphs into one, per the finding's own offered alternative, rather than correcting each copy separately a third time. | `OPUS-R25-002` resolution evidence (value corrected); "Restart discipline for S1" (consolidated, above) |
| `OPUS-R27-009` | LOW | **Accepted.** Three different lists of which requirement rows changed in revision 18 (`REVIEW_REQUEST.md`, plan line ~3780, the Round 26 scope-discipline paragraph), none matching the actual six-row diff (`WFR-47` through `WFR-52`, all omitting `WFR-48`); separately, the `WORKFLOW_STATE.json` bundle-narrative claim of "no other field touched" omitted that `state_revision`/`last_transition` also changed (both on an excluded path, no approval implication, but the claim as written didn't say so). Reconciled all three lists to name all six rows; the `REVIEW_REQUEST.md` regenerated for this round states the `WORKFLOW_STATE.json` change accurately. | Round 26 scope-discipline paragraph (corrected, above); Requirements traceability intro paragraph (corrected, below); `REVIEW_REQUEST.md` (regenerated this round) |

No finding required reopening the identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` — confirmed by direct inspection of every corrected section
against those five, the same standard applied in rounds 25 and 26.

**Scope discipline**: this revision touches `workflow-v2-1-core-mapping.json`
(six `description` values synced, `OPUS-R27-001`), `D-Fingerprint-Generalization`
(the artifacts-declarations subsection extended for the implementation
stage, `OPUS-R27-002`; the `prepare-ai-review.sh` subsection and migration
step 6a corrected, `OPUS-R27-003`; the manifest-binding subsection's
first-outcome precondition and the "Creation path" subsection's
bundle-directory-identity paragraph corrected, `OPUS-R27-004`; fail-closed
matrix condition 12's count corrected, `OPUS-R27-007`), acceptance criteria
(14, 15, 13 extended/corrected; 19-25 added), Requirements traceability
(`WFR-48` verification column, intro paragraph reconciled), Missing tests
(items 153, 161, 162 corrected in place; 164-166 added), and Self-review
notes. It does not reopen the identity algorithm's own hashing/canonicalization,
the two-stage plan-review protocol, the registry/mapping *file format*,
`D-Bootstrap`, or `D-Legacy`. No file under `scripts/` or
`.claude/commands/` is edited by this revision — every correction above is
design text; the dedicated fix session that follows approval is where the
code actually changes.

## Round 28 finding disposition (revision 19 → 20) — fourth external review round (`OPUS-R28-*`, `Status: REVISE`)

Revision 19's own bundle (`bundle_id: b3896c42…`, `review_content_id:
9a5df32a…`) went through this work item's ordinary single-stage
`AWAITING_EXTERNAL_PLAN_REVIEW` gate a fourth time. The verdict: six of
nine `OPUS-R27-*` findings fully and correctly resolved (`-005` through
`-009`, and `-001` for the six rows it named), three resolved in direction
but each leaving a reachable hole (`-002`, `-003`, `-004`) — still not safe
to implement, for three concrete `HIGH` reasons (two of them the same
"literal retired, structurally identical sibling survived" defect one
layer further out), plus seven further `MEDIUM`/`LOW` gaps. Zero require
reopening the identity algorithm, the two-stage plan-review protocol, the
registry/mapping file formats, `D-Bootstrap`, or `D-Legacy` (confirmed by
inspection, the same standard applied in rounds 25 through 27).

| ID | Severity | Disposition | Resolved in |
|---|---|---|---|
| `OPUS-R28-001` | HIGH | **Accepted.** The implementation-stage `artifacts_path` caller list `OPUS-R27-002` gave named `implementing_entry_reachable`, which never reaches `approval_review_content_id` with `stage="implementation"` at all — it only calls `approval_is_current(..., stage="plan", ...)`, hardcoded — and omitted `verify_post_approval_manifest_match`, the caller `/approve-review` step 6a actually invokes post-commit for either stage. Corrected the caller list everywhere it appeared (artifacts-declarations subsection, migration step 6, disposition row, acceptance criterion 20, test 164) to the two callers that actually reach it, derived mechanically (grep for every function whose body reaches `approval_review_content_id`) rather than re-enumerated by inspection a fifth time. | Artifacts-declarations subsection (corrected); migration step 6 (corrected); `OPUS-R27-002` disposition row (corrected); acceptance criterion 20 (corrected); missing test 164 (extended) |
| `OPUS-R28-002` | HIGH | **Accepted — the finding that decides whether S1 can run at all.** `route_work_item`'s resume branch (`workflow_state.py:1377-1380`) writes only `plan_revision`/`state_revision`/`last_transition`; `plan_path`/`registry_path`/`mapping_path`/`base_commit` are written only by `default_work_item`, called only from the creation branch. `v2-1-dry-run` is a pre-existing non-terminal entry, so a fresh S1 attempt after this design's own implementation would still take the resume branch and still fail at `MissingPlanStageMetadataError`, one layer deeper, exactly as `OPUS-R25-004` originally described. Resolved by the finding's preferred option (a): the resume branch gains the same four writers, per-field, write-if-null-else-conflict-if-disagreeing-else-leave, with a new `WorkItemDeclarationFactConflictError` on genuine disagreement — generalizing to every resumed non-terminal entry, not special-casing `v2-1-dry-run`. Option (b) (hand-backfill `v2-1-dry-run` in the migration) rejected: smaller for this one item, but leaves the same gap for the next process work item created the same way. | "Creation and resume path" subsection (retitled and extended); acceptance criterion 4 (corrected); missing test 154 (corrected) |
| `OPUS-R28-003` | HIGH | **Accepted.** `OPUS-R27-001` synced six mapping-JSON `description` rows to their table content; 15 further rows had diverged, some substantively — `WFR-24`'s two versions stated opposite properties (mechanism-specific vs. mechanism-independent), a live contradiction across two protected, hashed artifacts. Diffed all 52 rows under test 166's own stated normalization and synced the 15; independently re-verified afterward (zero mismatches under the same normalization, script-checked, not eyeballed). The Checkpoint column is explicitly excluded from the "generated view" claim and from test 166's scope: several rows cite a design-doc section (`D3`, `D2`, `D-Registry`, `D4b`, `D-Legacy`) that `checkpoint_ids`'s validator-enforced, checkpoint-only schema cannot represent — stated as a scope decision in the Requirements traceability intro paragraph, not left silently inconsistent. Acceptance criterion 19 extended from six rows to all 52; test 166's ownership reconciled with criterion 19's (data fix owed to this revision, automated regression test still owed to `WF8a-ii`). | `workflow-v2-1-core-mapping.json` (15 further `description` values synced); Requirements traceability intro paragraph (Checkpoint-column scope stated); acceptance criterion 19 (extended); missing test 166 (scope/ownership reconciled) |
| `OPUS-R28-004` | MEDIUM | **Accepted.** Migration step `6a`'s relocation was numbered between steps 6 and 7 while its own text said it ran after step 8; ran entirely in gitignored space with no atomicity, no collision rule (a second bundle generated between step 8 and `6a` would nest under itself rather than fail), no resumability (a partial move binds as authoritative per the first-write rule), and under-enumerated `.ai-review/`'s actual six entries. Renumbered `8a`, moved to its actual execution position; every entry under `.ai-review/` named explicitly with its disposition; non-empty-destination hard stop; post-move file-set verification before rebinding, run before, not folded into, the rebinding write. | Migration step (renumbered `6a` → `8a`, rewritten); acceptance criterion 21 (corrected); missing test 165 (extended) |
| `OPUS-R28-005` | MEDIUM | **Accepted.** Step `6a`/`8a` relocated `.ai-review/feedback` — stage-agnostic, no stage argument on `resolve_feedback_dir` — into `workflow-v2-1-core`'s scope, while step 8 explicitly retains the flat layout for every non-`plan` stage; the split would strand the implementation/post-fix/functional-review stages' feedback from their own still-flat bundles the moment the step ran. Took the finding's smaller option (b): `feedback/` stays flat, dropped from the relocation; `resolve_feedback_dir`'s scoped branch remains correctly unreachable until a future revision scopes every stage, which this one does not attempt. | Migration step 8a (feedback exclusion stated); acceptance criterion 21 (corrected) |
| `OPUS-R28-006` | MEDIUM | **Accepted.** The `base_commit` cross-check named a file-and-key-path, not a reader — an inline JSON read would bypass every validation `resolve_plan_stage_metadata` performs; named no exception a shell script can raise for condition 13; and compared against the raw requested argument rather than the resolved commit, so an abbreviated SHA or symbolic ref (which `git rev-parse` has always accepted) would have falsely refused. Named the reader (`fingerprint.resolve_plan_stage_metadata` via `python3 -c`), the comparison basis (resolved `BASE_SHA`, both forms named on refusal), and the two enforcement points condition 13 now names explicitly (early script-level exit; downstream `BundleWorkItemMismatchError`). | "Bundle content is bound to the resolved item too" subsection (corrected); migration step 8 (corrected); fail-closed matrix condition 13 (enforcement points named); acceptance criterion 13 (corrected); missing test 165 (extended) |
| `OPUS-R28-007` | MEDIUM | **Accepted.** The "no text change" audit was already wrong for `/apply-plan-review` under this revision's own required-argument change (two call sites, one printing the invocation with no work-item id at all) and never audited `/prepare-review`'s own third, unaudited call site. Moved `/apply-plan-review` to the changed list; added `/prepare-review`, stating the id is required only when `<stage>` is `plan`; folded both into migration step 8 and named all three files in acceptance criterion 21. | "Affected commands, corrected" paragraph (rewritten); migration step 8 (extended); acceptance criterion 21 (extended) |
| `OPUS-R28-008` | MEDIUM | **Accepted.** The first-write precondition ("the resolved directory is the resolved item's own scoped path, or a verified-empty target") was prose: `write_manifest_with_verified_identifiers`'s signature still took a caller-supplied `bundle_dir`, and "verified empty" named no verifier or definition, leaving the one case a reader most wants specified — present, non-empty, no manifest — falling under the first, unconditional-bind disjunct. Made the precondition a check: `bundle_dir` resolved internally from `work_item_id` (caller-supplied value kept only as an assert-equal cross-check); bind only when just-created or already containing a complete generation's required file set (the same set `MissingRequiredBundleFileError` enforces); refuse otherwise, naming the missing file. | Manifest-binding subsection's first outcome (corrected); acceptance criterion 22 (extended); missing test 161 (extended) |
| `OPUS-R28-009` | MEDIUM | **Accepted.** Migration step 10's green-suite gate still named "items 141-163," unchanged since round 26, while revision 19 added items 164 and 165 with no step requiring either to run before implementation is declared complete. Extended the range to 141-165, explicitly excluding item 166 (owed to `WF8a-ii`, not this fix session, per `OPUS-R28-003`'s ownership reconciliation); reconciled item 160's own parenthetical to the corrected range. | Migration step 10 (range corrected); missing test item 160 (parenthetical corrected) |
| `OPUS-R28-010` | MEDIUM | **Accepted.** The CLI's `--work-item-id` live-`active_work_item_id` default, safe on the read-only inspection path (`OPUS-R18-002`'s no-mutation invariant), was left unexamined on the `--write-manifest` path after `OPUS-R27-004`'s own fix turned that same path into a directory creator — an omitted flag during the fix session's own work (which runs with `active_work_item_id` pinned at `v2-1-dry-run`) would silently create and bind `v2-1-dry-run`'s directory. Made `--work-item-id` required whenever `--write-manifest` is passed; kept the live default for the read-only path only. | "Effect on `--work-item-id`" subsection (corrected); migration step 6 (corrected); acceptance criterion 9 (extended); missing test 149 (extended) |
| `OPUS-R28-011` | LOW | **Accepted.** `DEFAULT_REGISTRY_PATH`/`DEFAULT_PLAN_PATH` were named as confirmed defects in the original `WF8B-S1-001` disposition row, alongside `PLAN_STAGE_PROTECTED` and the CLI argparse defaults — every other literal in that list was explicitly retired by a migration step; these two were not, and remain live parameter defaults on `load_plan_revision` today. Added to migration step 6: `load_plan_revision`'s `registry_path`/`plan_path` parameters become required, retired in the same pass as the other literals; named in acceptance criterion 14's explicit list. | Migration step 6 (extended); acceptance criterion 14 (extended) |
| `OPUS-R28-012` | LOW | **Accepted.** Three "this revision specifies but does not implement" paragraphs and two "`active_work_item_id` remains `v2-1-dry-run`" paragraphs, within one self-review notes section, resolved to three and two different revisions respectively — the same duplication-and-drift pattern `OPUS-R27-008(b)` found in the "Restart discipline for S1" paragraphs, which revision 19 had already correctly merged. Restated the oldest "specifies but does not implement" occurrence as the standing invariant it actually is (true of every revision, not re-dated at each bump); merged the two duplicated `active_work_item_id` paragraphs into one, stating the invariant has held across every round from 17 through the current one; extended acceptance criterion 25 with the general property. | Self-review notes (oldest occurrence restated as standing invariant; two duplicated paragraphs merged into one); acceptance criterion 25 (extended) |

No finding required reopening the identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` — confirmed by direct inspection of every corrected section
against those five, the same standard applied in rounds 25 through 27.

**Scope discipline**: this revision touches `D-Fingerprint-Generalization`
(the implementation-stage caller list, `OPUS-R28-001`; the "Creation and
resume path" subsection retitled and extended, `OPUS-R28-002`; the
migration relocation step renumbered `6a` → `8a` and rewritten,
`OPUS-R28-004`/`-005`; the `base_commit` cross-check subsection corrected,
`OPUS-R28-006`; the "Affected commands" paragraph rewritten, `OPUS-R28-007`;
the manifest-binding first-outcome precondition corrected, `OPUS-R28-008`;
the "Effect on `--work-item-id`" subsection corrected, `OPUS-R28-010`;
migration step 6 extended, `OPUS-R28-011`), `workflow-v2-1-core-mapping.json`
(15 further `description` values synced, `OPUS-R28-003`), acceptance
criteria (4, 9, 13, 14, 19, 20, 21, 22, 25 extended/corrected), Requirements
traceability (intro paragraph, Checkpoint-column scope stated), Missing
tests (items 149, 154, 161, 164, 165, 166 extended/corrected in place; item
160 reconciled), fail-closed matrix conditions 12 (cross-reference) and 13
(enforcement points named), and Self-review notes (de-duplicated). It does
not reopen the identity algorithm's own hashing/canonicalization, the
two-stage plan-review protocol, the registry/mapping *file format*,
`D-Bootstrap`, or `D-Legacy`. No file under `scripts/` or
`.claude/commands/` is edited by this revision — every correction above is
design text; the dedicated fix session that follows approval is where the
code actually changes.

## Round 29 finding disposition (revision 20 → 21) — fifth external review round (`GPT-R29-*`, `Status: REVISE`)

Revision 20's own bundle (`bundle_id: eb4ec6cb…`, `review_content_id:
9c9799ca…`) went through this work item's ordinary single-stage
`AWAITING_EXTERNAL_PLAN_REVIEW` gate a fifth time, this time by a different
reviewer (`ChatGPT`, `GPT-R29-*`). The verdict: two `HIGH`-severity defects
that each make part of the design as written unimplementable rather than
merely incomplete, one `MEDIUM-HIGH` ownership gap, and two `LOW` stale/
inaccurate statements. Zero require reopening the identity algorithm, the
two-stage plan-review protocol, the registry/mapping file formats,
`D-Bootstrap`, or `D-Legacy` (confirmed by direct inspection against each
of the five, the same standard applied in rounds 25 through 28).

| ID | Severity | Disposition | Resolved in |
|---|---|---|---|
| `GPT-R29-001` | HIGH | **Accepted.** The resolution algorithm's step 5 read `base_commit` as one of four nullable declaration facts, but step 13's return value — a bare tuple — never included it, so `resolve_plan_stage_metadata`'s only actual output could not supply the field that later sections (the CLI's `base` positional, `prepare-ai-review.sh`'s cross-check, the manifest writer's comparison) all describe obtaining "through `resolve_plan_stage_metadata`." Every one of those consumers was consequently either unimplementable as specified, or implementable only by adding a second, independent read of `WORKFLOW_STATE.json` — exactly the second-source-of-truth problem this whole design exists to eliminate. Fixed by adding `base_commit` to the returned value and, per the review's own preference, replacing the growing positional tuple with a named `PlanStageMetadata` result object (ten fields, up from nine) — an added field is now a loud attribute-access error at a stale call site, not a silent positional misread. `compute_review_content_id_plan_stage_for_work_item`/`_at_commit_for_work_item`'s own `base` parameter is now optional, defaulting to `metadata.base_commit` from the one resolver call these functions already make, never a second read. | Resolution algorithm steps 5 and 13 (`base_commit` added; tuple replaced with named result object); `compute_review_content_id_plan_stage_for_work_item`/`_at_commit_for_work_item` signatures (corrected); "base positional" subsection (corrected, single-resolver-call stated explicitly) |
| `GPT-R29-002` | HIGH | **Accepted.** `OPUS-R28-008`'s own "just created by this same call" bind-precondition disjunct specified that a standalone `--write-manifest --work-item-id <new-item>` invocation could `mkdir -p` an empty directory and then bind it — but `compute_bundle_id` hashes the bundle's required generation files (`REVIEW_REQUEST.md`, `PLAN.md`, `DIFF.patch`, `TEST_RESULTS.md`), none of which a bare `mkdir -p` creates, making acceptance criterion 22/missing test 161's own asserted "creates and binds successfully" outcome impossible for the algorithm as specified to actually produce. Fixed by retiring the "just created" disjunct entirely and collapsing the bind precondition to the one check that was always actually sound: bind only when the resolved directory **already** contains the complete required generation file set. An absent directory is simply the limiting case of that same check — every required file is trivially missing from a directory that does not exist — so it raises the existing `MissingRequiredBundleFileError` exactly as an existing-but-incomplete directory does, with no new exception type needed. `write_manifest_with_verified_identifiers` no longer creates a bundle directory at all; that remains `prepare-ai-review.sh`'s (or an equivalent full-generation step's) sole responsibility, as in every revision before 19's write-path-creator addition — only the templated (never existence-gated-fallback) directory-path *resolution* `OPUS-R27-004` introduced survives this correction. | "No `MANIFEST.md` present" outcome (bind precondition collapsed to one check; directory-creation retracted); "Bundle-directory identity for the write path" subsection (corrected: resolver, not creator); "Effect on `--work-item-id`" subsection (failure-shape caveat added); acceptance criterion 22 (corrected); missing test 161 (corrected, positive case stated explicitly) |
| `GPT-R29-003` | MEDIUM-HIGH | **Accepted.** `OPUS-R28-003` split test 166's ownership from criterion 19's: the one-time 52-row data sync stayed with this revision, but the automated regression test that keeps the two in sync going forward stayed assigned to `WF8a-ii` — already `COMPLETE`, with checkpoint completion never reopened to attach a new obligation (the same standing rule `WF4a-i`'s own deferred-generalization history already establishes). That left item 166 with no reachable owner: this remediation's own implementation could pass migration step 10's full required-test range while never adding the one test the review found missing during self-review in the first place. Reassigned item 166 to this same `WF8b` continued-scope set items 141-165 already belong to, and added it to migration step 10's required-green range (now 141-166) — the smaller of the finding's two offered fixes, and the one consistent with `WF8b`'s own `OPUS-R28-003` fix being what populated the 52 rows this test verifies. | Acceptance criterion 19 (ownership parenthetical corrected); migration step 10 (range extended to 141-166); missing tests 160 and 166 (ownership corrected) |
| `GPT-R29-004` | LOW | **Accepted.** The `OPUS-R25-002` resolution evidence's "both now read" sentence had already drifted once (`OPUS-R27-008` caught it stuck at `17`) and had drifted again, silently, to `19` while the registry and state files had both already moved to `20`. Corrected to the current value (`21`) and, this time, verified by direct comparison against both files rather than hand-incremented, so the same drift is at least caught mechanically rather than trusted by inspection next time it is touched. | `OPUS-R25-002` resolution evidence (value corrected) |
| `GPT-R29-005` | LOW | **Accepted.** Migration step 8a's own heading claimed the relocation was "atomic, collision-safe, resumable-or-fail-closed," but the described mechanism is two sequential `mv -n` operations plus a post-move verification — collision-safe and fail-closed after an interruption, genuinely, but neither atomic (an interruption between the two moves is a real, named, manually-resolved state) nor resumable in the sense of an automated retry completing the job. Retitled to "collision-safe, fail-closed-after-interruption," matching the guarantee the rest of the bullet already correctly describes, rather than widening the mechanism to earn the stronger label. | Migration step 8a heading (retitled) |

No finding required reopening the identity algorithm, the two-stage
plan-review protocol, the registry/mapping file formats, `D-Bootstrap`, or
`D-Legacy` — confirmed by direct inspection of every corrected section
against those five, the same standard applied in rounds 25 through 28.

**Scope discipline**: this revision touches `D-Fingerprint-Generalization`
(the resolution algorithm's steps 5/13 and the `compute_*_for_work_item`
signatures, `GPT-R29-001`; the manifest-binding first-outcome precondition
and the write-path bundle-directory-identity subsection, `GPT-R29-002`;
the "Effect on `--work-item-id`" subsection's failure-shape caveat,
`GPT-R29-002`; migration step 8a's heading, `GPT-R29-005`), migration step
10 (range extended to 141-166, `GPT-R29-003`), acceptance criteria (19, 22
corrected), missing tests (items 160, 161, 166 corrected), and the
`OPUS-R25-002` resolution evidence's stale value (`GPT-R29-004`). It does
not reopen the identity algorithm's own hashing/canonicalization, the
two-stage plan-review protocol, the registry/mapping *file format*,
`D-Bootstrap`, or `D-Legacy`. No file under `scripts/` or
`.claude/commands/` is edited by this revision — every correction above is
design text; the dedicated fix session that follows approval is where the
code actually changes.

## WF8b finding disposition (revision 21 → 22) — continued-scope remediation had no non-terminal return path (self-discovered, `WF8B-002`)

**Origin**: discovered while closing out the functional review of
`WF8B_S1_FINDING_review_content_id_not_generalized.md`'s own remediation
(implementation revision 4), in the gap between "the user has just
functionally accepted this round's five checks" and "record that
acceptance and resume `WF8b`." Full analysis:
`docs/ai-workflow/dry-run/WF8B_FINDING_continued_scope_remediation_no_nonterminal_return_path.md`.

**The defect, restated precisely**: `apply_technical_approval`
(`scripts/workflow_state.py:1820-1831`) unconditionally sets `phase =
"AWAITING_FUNCTIONAL_REVIEW"` — correct and unchanged for an ordinary,
terminal implementation round, but it has never distinguished that case
from a **continued-scope** round (like the `WF8B-S1-001` fix just
approved) that leaves the item's own last checkpoint (`WF8b`) still
incomplete. Walking the documented path from there —
`AWAITING_FUNCTIONAL_REVIEW → AWAITING_USER_ACCEPTANCE → /accept-milestone
→ MILESTONE_COMPLETE` — as written, with nothing in code refusing it,
would mark all 17 checkpoints of `workflow-v2-1-core` complete while
`WF8b`, the manual multi-session dry run this whole document exists to
gate, has never been executed. `complete_work_item`
(`scripts/workflow_state.py:1538-1563`) checks only for incomplete child
work items — it performs no check at all against the item's **own**
registry/checkpoint completeness. No `*_gate_reachable` function exists
for `AWAITING_USER_ACCEPTANCE` (unlike the plan/technical-approval gates),
so nothing in code today enforces that `/accept-milestone` is even called
from the phase the workflow document names as its precondition.

**This is not hypothetical for this milestone specifically**: `WF8b`'s own
scenarios S8/S10 (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`) already
model further continued-scope remediation rounds happening mid-dry-run —
every one of them would hit this identical gap without this fix.

### Critical evaluation of the finding's own proposed design

The finding document proposes (§§1-7 of its "Minimum required remediation
scope"): a new phase `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE`, entered
directly from `apply_technical_approval` in place of
`AWAITING_FUNCTIONAL_REVIEW` whenever the round is non-terminal, plus a
new `/accept-scoped-remediation` command and matching `/accept-milestone`
guard. Evaluated against the repository's actual, current behavior rather
than accepted as written, that design has two real problems:

1. **It silently removes functional review for every future continued-
   scope round.** Placing the terminal/non-terminal branch inside
   `apply_technical_approval` itself means a non-terminal round would
   transition straight from technical approval to "scoped remediation
   acceptance," **never passing through `AWAITING_FUNCTIONAL_REVIEW` at
   all** — no functional-review checklist would ever be written or walked
   for that round. This directly contradicts what already, correctly,
   happened for the very fix this finding is about: a five-check manual
   functional-review checklist for `WF8B-S1-001`'s remediation was written
   at `AWAITING_FUNCTIONAL_REVIEW` and independently walked by the user
   before this finding was even discovered (see
   `docs/ACTIVE_MILESTONE.md`'s "`workflow-v2-1-core` — functional review
   checklist" section, still current). A design that would have prevented
   that from happening again is a regression, not a fix.
2. **Its own `/accept-milestone` guard ("refuse unless phase ==
   `AWAITING_USER_ACCEPTANCE`") is unreachable as written.** Grep across
   `scripts/workflow_state.py` confirms `AWAITING_USER_ACCEPTANCE` is
   named only in exception/error-message text and docstrings — no
   function anywhere ever writes it to a work item's `phase`. This is a
   real, pre-existing gap (it predates this entire Workflow v2.1 project;
   `AWAITING_FUNCTIONAL_REVIEW`/`AWAITING_USER_ACCEPTANCE` are both
   inherited, unchanged, from the original `MILESTONE_WORKFLOW.md`). Gating
   `/accept-milestone` on a phase value nothing ever produces would make
   the command permanently unreachable — for every terminal item, not
   only `WF8b`'s — the moment this fix landed. Fixing that separate,
   decade-old gap (giving `AWAITING_USER_ACCEPTANCE` a real writer) is out
   of scope for this finding and not attempted here; instead, the guard
   below is written against the phase value this repository's tooling
   actually produces today.

### Simplified design: no new phase

Both problems above trace to the same root choice: deciding
terminal-vs-non-terminal **once, early, at `apply_technical_approval`
time**, and encoding the answer as a phase. This revision instead defers
that decision to **the moment a human actually tries to accept** —
inside each accept command's own entry guard — recomputing
`select_next_checkpoint(work_item, registry)` fresh every time, exactly
the discriminator the finding itself identifies as "already answering
this question correctly and for free." This eliminates the new phase
entirely:

- `apply_technical_approval` is **not modified at all** — not merely
  "byte-identical on the terminal branch" (the finding's own regression
  bar), but literally untouched, for every round, terminal or not.
  Functional review always runs, exactly as today, for a continued-scope
  round exactly as for a terminal one.
- `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` is not added to `KNOWN_PHASES`,
  `MILESTONE_WORKFLOW.md` gains no new state section, and the hard-gate
  count stays at **6**, unchanged.
- Both new commands below operate directly from the phase this repository
  actually writes and has always written at this point —
  `AWAITING_FUNCTIONAL_REVIEW` — discriminated by a freshly recomputed
  terminal/non-terminal check, not by which phase value happens to be on
  disk.

**New helper** (`scripts/workflow_state.py`, alongside `select_next_checkpoint`):

```python
def registry_completion_status(work_item: dict, registry: dict) -> tuple[bool, str | None]:
    """Returns (is_terminal, outstanding_checkpoint_id). is_terminal is
    True iff select_next_checkpoint(work_item, registry) is None (every
    registry checkpoint COMPLETE); outstanding_checkpoint_id is None in
    that case, else the checkpoint id select_next_checkpoint would
    return, or -- when it instead raises NoCheckpointReadyError -- the
    specific blocked checkpoint's id (NoCheckpointReadyError gains a
    structured `checkpoint_id` attribute alongside its existing message,
    so callers never parse prose to recover it). The single call site
    complete_work_item, milestone_complete_gate_reachable's caller,
    scoped_remediation_gate_reachable's caller, and
    apply_scoped_remediation_acceptance all share -- never four
    independent re-derivations of the same terminal/non-terminal
    question."""
```

**Two new, non-circular gate-reachability functions**, mirroring
`approval_gate_reachable`/`technical_approval_gate_reachable`'s existing
pattern (D-States) exactly — precomputed booleans in, no field read
twice:

```python
def milestone_complete_gate_reachable(*, phase: str, is_terminal: bool) -> bool:
    """/accept-milestone's entry condition. Reachable when is_terminal
    (this item's own registry has no incomplete checkpoint left) and
    phase is AWAITING_FUNCTIONAL_REVIEW or AWAITING_USER_ACCEPTANCE.
    AWAITING_FUNCTIONAL_REVIEW is included deliberately, not loosely: no
    function in this codebase has ever written AWAITING_USER_ACCEPTANCE
    (confirmed by grep -- a pre-existing gap, not introduced or widened
    here), so gating solely on the latter would make /accept-milestone
    permanently unreachable for every item, not only WF8b's. If a future,
    separate fix ever gives AWAITING_USER_ACCEPTANCE a real writer, this
    function already accepts it without change."""
    return phase in ("AWAITING_FUNCTIONAL_REVIEW", "AWAITING_USER_ACCEPTANCE") and is_terminal


def scoped_remediation_gate_reachable(*, phase: str, is_terminal: bool) -> bool:
    """/accept-scoped-remediation's mirror-image entry condition:
    reachable only from AWAITING_FUNCTIONAL_REVIEW (functional review has
    already run for this round, exactly as for a terminal one) while
    is_terminal is False -- an incomplete checkpoint remains. A terminal
    registry refuses here, naming /accept-milestone as the correct command
    instead of silently accepting a scoped acceptance nothing needs."""
    return phase == "AWAITING_FUNCTIONAL_REVIEW" and not is_terminal
```

**`complete_work_item` gains the registry-based guard the finding's root-
cause analysis names** (defense in depth alongside `/accept-milestone`'s
own guard, the same "same defect class, fix both sides" reasoning
`D-Functional-Remediation`'s existing parent-completion block already
established for `GPT-R9-016`):

```python
def complete_work_item(state: dict, work_item_id: str, now: str, *, registry: dict | None = None) -> dict:
    """... unchanged incomplete_children check ...
    New: if registry is not None (the caller loads it from
    work_item['registry_path'] whenever that field is non-null; a
    registry-less item, e.g. the legacy milestone-8 shape, passes None and
    skips this check entirely -- nothing to be incomplete against), compute
    is_terminal via registry_completion_status and raise
    IncompleteOwnCheckpointsError, naming the outstanding checkpoint, if
    not is_terminal. This closes the exact gap the finding names: nothing
    today checks this item's own registry/checkpoint completeness before
    MILESTONE_COMPLETE."""
```

**`/accept-milestone`** gains one new step: compute `registry` (from
`registry_path`, or `None`) and `is_terminal` via
`registry_completion_status`, and refuse via
`milestone_complete_gate_reachable` before doing anything else, naming
the actual phase and the outstanding checkpoint on refusal. Every other
step is unchanged.

**New command, `/accept-scoped-remediation [work_item_id]`** — mirrors
`/accept-milestone`'s user-only construction exactly (`disable-model-invocation:
true`; refuses without literal current-turn confirmation text naming the
work item and a new, third stage string):

- `APPROVAL_STAGES` (`scripts/workflow_state.py:145`) extends from
  `frozenset({"plan", "implementation", "acceptance"})` to add
  `"scoped_remediation"` — a fourth, textually non-interchangeable stage
  keyword. Confirmation text written for `stage="acceptance"` (the real,
  terminal milestone acceptance) can never satisfy
  `stage="scoped_remediation"`, and vice versa — this is what makes
  "reuse of terminal milestone acceptance as scoped acceptance" (the
  finding's own replay-safety requirement) structurally impossible, not
  merely discouraged.
- **Entry guard**: resolve the target item, load its registry, compute
  `is_terminal` fresh (never threaded from an earlier call), and refuse
  via `scoped_remediation_gate_reachable` unless it returns `True`. Also
  refuse unless `work_item["technical_approval"]["status"] == "CURRENT"`
  — if a bounded-fix round happened after technical approval but before
  this command ran, `mark_technical_approval_stale` would already have
  flipped this to `STALE`, and a superseded revision must never be
  scoped-accepted (the finding's own first replay-safety requirement).
- **New function**, `apply_scoped_remediation_acceptance(state,
  work_item_id, registry, *, user_confirmation, now)`: builds and appends
  one entry to `work_item["scoped_remediation_acceptance"]` (a list,
  created empty if absent — a single checkpoint may need more than one
  continued-scope round, matching `WF8B-S1-001`'s own five-round
  implementation-review history):

  ```json
  {
    "outstanding_checkpoint_id": "WF8b",
    "active_work_item_id_at_acceptance": "v2-1-dry-run",
    "technical_approval_review_content_id": "8f9ea8cc60192bbbecfb7ed092c1dc79739047c7616ea4e2cddd8b68ecd0c9ed",
    "user_confirmation": "<verbatim>",
    "recorded_at": "<timestamp>"
  }
  ```

  Deliberately smaller than the finding's own proposed shape: no `status`
  field (the finding's schema included one, always `"CURRENT"` — every
  entry here is an immutable historical fact the moment it is appended,
  nothing later mutates it, so an enum with one ever-observed value adds
  surface with no invariant to enforce); `active_work_item_id_at_open`
  renamed to `active_work_item_id_at_acceptance` (there is no longer a
  separate "open" moment to distinguish from "acceptance," since the
  branch decision itself moved to acceptance time). Sets `phase =
  "IMPLEMENTING"`. Leaves `checkpoints`/`current_checkpoint_id`/
  `active_work_item_id`/`plan_approval`/`technical_approval`/
  `functional_acceptance_status` completely untouched — `technical_approval`
  and `functional_acceptance_status` remain reserved for the item's own
  eventual, genuinely terminal round, exactly as the finding specifies.
  No dedicated provenance commit is required (unlike `plan_approval`/
  `technical_approval`/`WF-Activate`'s trailer-carrying commits) — this
  follows the same lighter precedent already used for
  `mark_technical_approval_stale`/`record_bundle_generation`/checkpoint
  transitions: a plain `WORKFLOW_STATE.json` write, swept into whatever
  commit follows.
- Report and stop: name the outstanding checkpoint and that a fresh
  session should resume it via that checkpoint's own driver (for `WF8b`,
  `/bootstrap-workflow-v2`).

**Why this satisfies every replay/staleness requirement the finding lists,
without a bespoke check for most of them**:

- *Superseded implementation revision*: refused by the `technical_approval.status
  == "CURRENT"` check above.
- *Acceptance after the checkpoint has changed*: not a special case —
  `is_terminal` is recomputed fresh at accept time, so if the outstanding
  checkpoint was completed out-of-band before this command ran,
  `is_terminal` is already `True` and `scoped_remediation_gate_reachable`
  refuses, redirecting to `/accept-milestone` instead. Stronger than the
  finding's own design, which threads a value computed back at
  `apply_technical_approval` time and would need a separate staleness
  check to catch this.
- *Duplicate acceptance with different evidence*: free from the phase
  guard alone — a successful call flips `phase` to `IMPLEMENTING`
  immediately, so a second attempt fails
  `scoped_remediation_gate_reachable` on phase alone, no bespoke
  duplicate-detector needed.
- *Acceptance when no incomplete parent checkpoint remains*: exactly the
  `is_terminal == True` refusal above.
- *Reuse of terminal milestone acceptance as scoped acceptance*: refused
  structurally by the `APPROVAL_STAGES` stage-string separation.

**Resume behavior** (finding item 5): after `/accept-scoped-remediation`
succeeds, `WF8b` remains absent from `checkpoints` (never touched by this
mechanism), `phase` is `IMPLEMENTING`, and `active_work_item_id` is
untouched. `/bootstrap-workflow-v2` — which hardcodes its target to
`workflow-v2-1-core` regardless of `active_work_item_id` (D-Bootstrap) —
re-derives `WF8b` as the next checkpoint via its own existing step 3
(`select_next_checkpoint`'s identical rule), in a fresh session, from
disk alone, with **no new pointer or marker file**. This is materially
simpler than the finding's own §5 proposal (which would have introduced a
distinct "interrupted synthetic item" bookkeeping concept) precisely
because nothing about `WF8b`'s own registry/checkpoint state was ever
touched by the detour in the first place.

### Departures from the finding's proposed design (summary)

1. No new `AWAITING_SCOPED_REMEDIATION_ACCEPTANCE` phase — the terminal/
   non-terminal decision moves from `apply_technical_approval` (early,
   threaded) to each accept command's own entry guard (late, recomputed).
2. `apply_technical_approval` is untouched, not merely "byte-identical on
   the terminal branch" — this also means functional review is never
   skipped for a continued-scope round, closing the regression problem 1
   above found in the finding's own design.
3. `/accept-milestone`'s new guard is written against
   `AWAITING_FUNCTIONAL_REVIEW` (the phase this repository's code actually
   produces), not solely `AWAITING_USER_ACCEPTANCE` (which no code writes
   — a separate, pre-existing, explicitly out-of-scope gap, flagged here
   rather than silently worked around).
4. `scoped_remediation_acceptance` entries drop the `status` field and
   rename `active_work_item_id_at_open` to `..._at_acceptance` (no
   separate "open" moment exists once the decision point moved).
5. No dedicated provenance commit for `/accept-scoped-remediation`
   (lighter precedent, matching `mark_technical_approval_stale`).
6. Four of the finding's five replay/staleness requirements fall out of
   the design above for free, rather than needing dedicated checks.

**Scope discipline**: this revision adds `D-Scoped-Remediation-Acceptance`
(new decision, below); extends `D-Functional-Remediation`'s existing
"parent acceptance blocks on an incomplete child" reasoning to the item's
own registry (a natural continuation of the same defect class,
`GPT-R9-016`); amends `MILESTONE_WORKFLOW.md`'s `MILESTONE_COMPLETE`
section with an "Own-checkpoint-completion block" paragraph mirroring its
existing "Parent-completion block" one, and its `AWAITING_FUNCTIONAL_REVIEW`
section with a note on the two distinct exits; adds five new requirements
(`WFR-53`-`WFR-57`); adds missing-test items 167-179. It does not reopen
the identity algorithm, the two-stage plan-review protocol, the registry/
mapping file formats, `D-Bootstrap`, or `D-Legacy`. No file under
`scripts/` or `.claude/commands/` is edited by this revision — every
change above is design text; the dedicated fix session that follows
approval is where the code actually changes (this revision explicitly
does not implement the mechanism, per the same discipline every prior
plan-only revision in this document has followed).

**Known, expected, temporary test breakage from adding `WFR-53`-`WFR-57`
alone** (owed to `WF8b`'s own continued scope, same owner as item 166
itself, `GPT-R29-003`): item 166's own conformance test
(`workflow_integration_test.py::test_every_wfr_row_description_matches_json_exactly`)
hardcodes a sanity assertion that exactly 52 `WFR-*` rows exist —
verified, by direct re-run, to be the **only** new failure this revision
introduces (`workflow_state_test.py`/`workflow_state_demo_test.py`/
`workflow_fingerprint_test.py`/`workflow_fingerprint_generalization_test.py`/
`workflow_test_harness_test.py` all remain green; the demo suite's two
pre-existing, unrelated real-repository-timing failures — `plan_approval`'s
discoverable-commit-body check and `reviewed_implementation_head`'s
live-HEAD check, both already failing identically before this revision,
confirmed by re-running against a stash of this revision's own changes —
are untouched by it). The per-row content comparison itself (the actual
regression guard item 166 exists for) was independently re-verified,
outside the test file, to pass cleanly for all 57 rows including the five
new ones — only the hardcoded `52` literal is stale. Updating it is
scripts/ code, deferred to the dedicated implementation session per the
"no file under scripts/ is edited by this revision" rule stated above,
the same standing precedent `WFR-47`-`WFR-52` themselves already
established when they were first plan-drafted.

## WF8b finding disposition (revision 22 → 23) — external plan review found two blocking gaps in the acceptance-time split (`GPT-R36-001`/`-002`/`-003`)

**Origin**: independent external plan review of revision 22
(`GPT-R36-*`), verdict `REVISE`. Full feedback:
`.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md` (bound to plan
revision 22, `review_content_id` `1fc2357e04e929bc240c925c3870bf5a085e38bdf1dc9c05318cb6665765218f`,
`bundle_id` `c8f2c8a2b61043f4c7206fc9620f6204aaf7ef4ff6dd5357648dc9bbdd3e2b1f`).
All three findings validated against the actual repository before being
applied (not accepted on the finding's own premise alone) — see below per
finding. None of revision 22's other design choices are reopened; the
review's own "Confirmed strengths" section endorses them unchanged.

### GPT-R36-001 — `complete_work_item`'s registry guard was fail-open, not fail-closed (blocking, accepted)

Confirmed against the repository: revision 22's text specified only a
*caller convention* ("the caller loads it from `work_item["registry_path"]`
whenever that field is non-null"), with nothing inside `complete_work_item`
itself preventing a direct call from passing `registry=None` for a
registry-backed item. `scripts/workflow_state.py`'s current
`complete_work_item(state, work_item_id, now)` (line 1538) confirms there
is no registry parameter at all today, so nothing pre-existing narrows
this. The failure scenario is real: any caller bypassing or predating
`/accept-milestone`'s own command-level guard reaches `complete_work_item`
directly with `registry=None`, and the intended check silently no-ops.

**Fix adopted**: `complete_work_item` itself, not only its callers, now
distinguishes three cases from its own parameters alone, fail-closed:

1. `work_item["registry_path"]` is `None` — genuinely registry-less (e.g.
   `milestone-8`'s legacy shape). `registry` is not inspected; behavior is
   unchanged, vacuously terminal (item 178, unchanged).
2. `work_item["registry_path"]` is not `None` and `registry is None` — the
   omitted-registry case the finding names. Raises a new
   `RegistryCoverageError`, naming the work item and its declared
   `registry_path`, instead of silently falling into case 1's treatment.
3. `work_item["registry_path"]` is not `None`, `registry` is provided, but
   `registry.get("work_item_id") != work_item_id` — a foreign or
   mismatched registry (every registry JSON file already declares its own
   `work_item_id` at the top level — confirmed against
   `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`'s own
   `work_item_id` field). Raises the same `RegistryCoverageError`, naming
   both the expected and the foreign registry's declared id.

Only once none of the above fire does `registry_completion_status` run and
(unchanged) raise `IncompleteOwnCheckpointsError` for a non-terminal
registry.

The finding's fourth named case — "an item whose registry cannot be
loaded or validated" — is a caller-side (command-level) concern:
`complete_work_item` only ever receives an already-parsed dict, never a
path; a load/parse failure surfaces at whichever command resolves
`registry_path` from disk (`/accept-milestone`'s own step, unchanged from
revision 22), the same place `D3`'s existing whole-state registry
validation already surfaces an unreadable or malformed registry file.
Stated explicitly here rather than left implicit, closing the gap without
adding file I/O to `complete_work_item`'s own signature.

### GPT-R36-002 — `/accept-scoped-remediation`'s acceptance must be a committed, not a pending, fact (blocking, accepted)

Confirmed: revision 22 explicitly chose "a plain `WORKFLOW_STATE.json`
write... swept into whatever commit follows," citing
`mark_technical_approval_stale`/`record_bundle_generation`/checkpoint
transitions as precedent. That precedent is real (confirmed by reading
those functions), but it does not fit here: every one of those writes is
either reversible, recomputed idempotently from other durable facts, or
immediately superseded by the very next step in the same session — none
of them is the durable record of a **user-authorized gate**, unlike
`plan_approval`/`technical_approval`/`WF-Activate`, which this same
document already describes as carrying "trailer-carrying commits" for
exactly that reason (confirmed: `.claude/commands/approve-review.md`
writes a dedicated commit with `Workflow-Plan-Approval`/
`Workflow-Technical-Approval` + `review_content_id` + `Workflow-Work-Item`
trailers). `/accept-scoped-remediation`'s user confirmation is exactly
that class of fact, not a checkpoint-transition or a staleness flag, so
the lighter precedent does not apply and the finding's durability concern
is accepted.

**Fix adopted**: `/accept-scoped-remediation` now creates its own
dedicated, metadata-only provenance commit — no production/test changes,
mirroring the existing plan/technical-approval commit shape — carrying a
`Workflow-Scoped-Remediation-Acceptance: <work_item_id>/<outstanding_checkpoint_id>`
trailer, before reporting success. The command:

- refuses to report success if the commit cannot be created — the state
  write and the commit happen together or not at all, a single
  all-or-nothing step, never two independently-observable ones;
- verifies the worktree is clean immediately after the commit;
- is idempotent for a replay of the identical accepted round: if a
  matching `Workflow-Scoped-Remediation-Acceptance` trailer for this exact
  `work_item_id`/`outstanding_checkpoint_id`/`implementation_revision`
  combination is already the most recent one reachable, it reports the
  existing commit rather than creating a duplicate;
- refuses a conflicting duplicate: a second attempt naming a *different*
  accepted round against the same still-outstanding checkpoint (before
  the first is resumed) is rejected, naming the already-recorded commit.

Fresh-session bootstrap (`/bootstrap-workflow-v2`) resumes an outstanding
checkpoint only by reading the committed `WORKFLOW_STATE.json` — its
existing, unchanged behavior; it has never read uncommitted working-tree
state. This is what makes "crash before the commit" and "crash after the
commit" both safe by construction, rather than by a new bespoke recovery
path: before the commit, the prior phase (`AWAITING_FUNCTIONAL_REVIEW`) is
still what a fresh session's committed state shows, so a retry from there
is exactly the ordinary, already-defined guard path; after the commit, the
resumed phase (`IMPLEMENTING`) is what a fresh session reads, and
`select_next_checkpoint` re-derives `WF8b` from the committed registry
state exactly as revision 22 already specified.

### GPT-R36-003 — the acceptance record must bind to the exact evidence accepted, not just the technical approval (important, accepted)

Confirmed: revision 22's schema (five fields) names only
`technical_approval_review_content_id`, not the round's
`implementation_revision`, `reviewed_implementation_head`, or any identity
for the functional-review checklist itself — which this same document's
own `AWAITING_FUNCTIONAL_REVIEW` section places at
`docs/ACTIVE_MILESTONE.md`, a file with no dedicated approval record of
its own. Unlike `technical_approval`, nothing already stales this schema
if the checklist changes after the user walks it.

**Fix adopted**: the `scoped_remediation_acceptance` entry schema gains
four fields, becoming:

```json
{
  "outstanding_checkpoint_id": "WF8b",
  "active_work_item_id_at_acceptance": "v2-1-dry-run",
  "implementation_revision": 4,
  "reviewed_implementation_head": "7bef596dd95c7f35f1dcc0eedd9a91d98d592ab8",
  "technical_approval_review_content_id": "8f9ea8cc60192bbbecfb7ed092c1dc79739047c7616ea4e2cddd8b68ecd0c9ed",
  "functional_checklist_path": "docs/ACTIVE_MILESTONE.md",
  "functional_checklist_blob": "<git blob sha of functional_checklist_path at the acceptance commit>",
  "user_confirmation": "<verbatim>",
  "recorded_at": "<timestamp>",
  "acceptance_record_version": 1
}
```

`implementation_revision` and `reviewed_implementation_head` are read live
from the work item's own top-level fields at acceptance time (both
already have a single existing writer each — `apply_technical_approval`
and the bundle generator respectively, `WFR-22` — this adds no second
writer for either). `functional_checklist_blob` is computed via
`git rev-parse HEAD:<functional_checklist_path>` immediately before the
acceptance commit — a committed blob identity, not a working-tree hash,
so it cannot be produced by an uncommitted edit and is stable once
recorded. `acceptance_record_version` is `1` for every entry this design
produces; it exists so a future schema change can distinguish old- and
new-shape entries without guessing from field presence alone.

The entry guard gains one more refusal, alongside the existing
phase/terminal/`technical_approval.status` checks (all still recomputed
fresh, never threaded): immediately before recording, it re-reads
`work_item["reviewed_implementation_head"]` and
`git rev-parse HEAD:<functional_checklist_path>` and refuses — naming both
the expected and the current value — if either has changed since the
values about to be recorded were computed earlier in the same invocation.
This guards the single-invocation window between "read the evidence" and
"commit the acceptance," the same class of check `WFR-21`'s existing
plan-approval durability guard already performs for a different stage.
It is deliberately not a cross-invocation staleness check against a
previously recorded entry (there is no prior entry to be stale against on
a first acceptance for a given round); the cross-invocation guarantee is
instead the ordinary phase-guard duplicate-refusal `GPT-R36-002`'s fix
above already establishes.

**Scope discipline (revision 23)**: this revision fixes exactly the two
blocking and one important finding above, entirely within
`D-Scoped-Remediation-Acceptance`. It does not reopen the "no new phase"
decision, `apply_technical_approval`'s untouched status, the
`MILESTONE_COMPLETE` own-checkpoint block's location, or any other design
choice from revision 22 — all endorsed unchanged by `GPT-R36-*`'s own
"Confirmed strengths" section. `WFR-53` and `WFR-56` are amended in
place; `WFR-58` and `WFR-59` are added (no requirement is removed or
renumbered). Missing-test items 180-186 are added. No file under
`scripts/` or `.claude/commands/` is edited by this revision either — same
discipline as revision 22, deferred to the same future dedicated
implementation session.

## WF8b finding disposition (revision 23 → 24) — external plan review found the registry trust boundary, the replay/phase-guard ordering, the schema field count, and the checklist cleanliness precondition all still unsafe (`GPT-R37-001`/`-002`/`-003`/`-004`)

**Origin**: independent external plan review of revision 23 (`GPT-R37-*`),
verdict `REVISE`. Full feedback:
`.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md` (bound to plan
revision 23, `review_content_id`
`8e3464bdf5c7c2920bd47fd77f16cdda97bb7d9c929b04f53fd77b17dd8ac4ba`,
`bundle_id`
`ebcae14f2185676791a1499269cb6338617c7a37f228a7035cec155bfab98dd0`). All
four findings validated against the actual repository before being
applied — see below per finding. None of revision 22/23's other design
choices are reopened; the review's own "Confirmed strengths" section
endorses them unchanged.

### GPT-R37-001 — `complete_work_item` still trusted an unproven caller-supplied registry (blocking, accepted)

Confirmed against the repository: revision 23's own three-case guard
(quoted in the review) decides everything from `registry.get("work_item_id")
== work_item_id` — a property any in-memory dict can declare regardless of
its provenance. Nothing in revision 23's text, and nothing `complete_work_item`
itself does, ever reads `work_item["registry_path"]` from disk or compares
the supplied dict's identity against that file. A caller (direct or via a
bug in `/accept-milestone` predating its own gate) can therefore construct
`{"work_item_id": "workflow-v2-1-core", ...all checkpoints marked
COMPLETE...}` by hand and pass it straight through, even while the real
`docs/ai-workflow/registry/workflow-v2-1-core-registry.json` on disk still
shows an incomplete `WF8b` — exactly the failure scenario the finding
walks. This is the same defect class `GPT-R36-001` fixed one layer up
(fail-open on an omitted argument); this time the gap is one layer deeper
(fail-open on the argument's own unverified *content*).

**Fix adopted**: remove the trust boundary instead of tightening a check
on it. `complete_work_item` no longer accepts a `registry` argument at
all — see the amended `D-Scoped-Remediation-Acceptance` text below for the
full `repo_root`-based contract, which reuses `D3`'s own existing
whole-state registry loader rather than inventing a second one. A
fabricated dict is no longer representable as an input to this function in
the first place, which is a stronger guarantee than any content check on a
caller-supplied one could give.

### GPT-R37-002 — replay semantics contradicted the phase guard and over-refused legitimate later rounds (blocking, accepted)

Confirmed against the repository: revision 23 specified, in the same
section, that (a) success sets `phase = "IMPLEMENTING"`, (b) the entry
guard requires `phase == "AWAITING_FUNCTIONAL_REVIEW"`, and (c) "a replay
naming the identical ... round ... reports that existing commit instead of
creating a duplicate." Read in the only order revision 23's own prose
presents them (entry guard first, replay-detection as part of the
provenance-commit step after), an identical replay is a second invocation
arriving with `phase == IMPLEMENTING` — it fails (b) before ever reaching
(c), so (c) describes behavior no invocation could actually exercise. This
is a real ordering bug, not a documentation nitpick: it means the "replayed
acceptance is idempotent" guarantee `WFR-58`/item 182 promise was
unimplementable as revision 23 specified it. Separately, revision 23's
provenance-commit paragraph refused "a second attempt naming a *different*
accepted round against the same still-outstanding checkpoint" unconditionally
— but `WF8b` (this very milestone's own long-running checkpoint, per its
own dry-run history) can legitimately need more than one reviewed
remediation round while still incomplete, exactly as revision 22's
original list-shaped `scoped_remediation_acceptance` schema already
assumed. Revision 23's own trailer, keyed only by
`<work_item_id>/<outstanding_checkpoint_id>`, made every round after the
first collide with it by construction, forcing the over-broad refusal.

**Fix adopted**: two changes, both in `D-Scoped-Remediation-Acceptance`
below. First, the entry guard now performs replay detection *before* the
ordinary phase/terminal/technical-approval-status checks, keyed off
`registry_completion_status`'s output alone (computable independent of
`phase`) — an exact-round replay is recognized and reported idempotently
regardless of the live phase, resolving the ordering contradiction.
Second, the trailer's discovery key becomes
`<outstanding_checkpoint_id>/<implementation_revision>` instead of
`<work_item_id>/<outstanding_checkpoint_id>` — since `implementation_revision`
has exactly one existing writer that only ever advances
(`apply_technical_approval`, `WFR-22`), each genuinely new round gets a
distinct, non-colliding key automatically, so accepting a second
legitimate round for the same checkpoint needs no bespoke permission and a
same-key collision (which can now only mean corrupted or hand-edited
state, never two legitimate rounds) is the only case still refused as a
conflicting duplicate.

### GPT-R37-003 — the acceptance schema's "nine-field" text was internally inconsistent with its own displayed ten-field JSON (blocking specification defect, accepted)

Confirmed against the repository: the JSON block revision 23 itself
displays for `scoped_remediation_acceptance` (`outstanding_checkpoint_id`,
`active_work_item_id_at_acceptance`, `implementation_revision`,
`reviewed_implementation_head`, `technical_approval_review_content_id`,
`functional_checklist_path`, `functional_checklist_blob`,
`user_confirmation`, `recorded_at`, `acceptance_record_version`) has ten
keys, counted directly. `WFR-56`'s test column and missing-test items 171
and 185 all instead say "exact nine-field set"/"full nine-field set." A
test written against the schema and a test written against the stated
count cannot both pass against a single implementation.

**Fix adopted**: the displayed ten-field schema is the correct, canonical
one (it is what revision 22's original five fields plus revision 23's four
added fields plus `acceptance_record_version` actually sum to); every
"nine-field" reference is corrected to "ten-field" — `WFR-56`'s test
column and missing-test items 171 and 185 below, plus the schema
paragraph itself, which now states the five-plus-four-plus-one arithmetic
explicitly so a future count drift is a visible arithmetic error, not a
silent one-off.

### GPT-R37-004 — the evidence guard could bind to a committed blob while the user reviewed dirty working-tree content (important, accepted)

Confirmed against the repository: revision 23's pre-commit evidence guard
computes `git rev-parse HEAD:<functional_checklist_path>` — a committed
blob lookup that does not consult the working tree at all — and its
single-invocation re-read check compares that same `HEAD:path` value to
itself a second time. If `docs/ACTIVE_MILESTONE.md` has an uncommitted
edit, both reads return the identical, older committed blob; the
re-read check cannot distinguish "nothing changed" from "the user reviewed
content this command never looked at." The failure scenario is real and
not covered by any existing check: a checklist edited-but-not-committed,
reviewed and accepted by the user in that state, is recorded under the
older committed identity, with no record anywhere of what was actually
walked.

**Fix adopted**: the pre-commit evidence guard gains a cleanliness
precondition — refuse outright, before computing or recording any blob,
whenever `functional_checklist_path` has a staged or unstaged change
relative to `HEAD`. This is checked twice, matching the existing
single-invocation-window pattern: once at the guard's first read, and
again immediately before the provenance commit, since a dirty edit landing
in that gap is exactly as unreviewed as one present from the start.

**Scope discipline (revision 24)**: this revision fixes exactly the three
blocking and one important finding above, entirely within
`D-Scoped-Remediation-Acceptance`. It does not reopen the "no new phase"
decision, `apply_technical_approval`'s untouched status,
`GPT-R36-002`'s dedicated-provenance-commit requirement, the
`MILESTONE_COMPLETE` own-checkpoint block's location, or any other design
choice from revision 22/23 — all endorsed unchanged by `GPT-R37-*`'s own
"Confirmed strengths" section. `WFR-53`, `WFR-55` (test column only),
`WFR-56` (test column only), `WFR-58`, and `WFR-59` are amended in place
(no requirement is removed, added, or renumbered). Missing-test items 170,
171, 176, 178, 182-186 are amended and items 187-190 are added. No file
under `scripts/` or `.claude/commands/` is edited by this revision either
— same discipline as revisions 22/23, deferred to the same future
dedicated implementation session.

## WF8b finding disposition (revision 24 → 25) — external plan review found the checklist-evidence precondition unsatisfiable by any documented command, the replay short-circuit unvalidated against its own recorded fields, and the confirmation/replay ordering unspecified (`GPT-R38-001`/`-002`/`-003`)

**Origin**: independent external plan review of revision 24 (`GPT-R38-*`),
verdict `REVISE`. Full feedback:
`.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md` (bound to plan
revision 24, `review_content_id`
`bc9af716cd19b2c72bdebd1815e73bf91d959b73201c4e31238bba4821a0bc8f`,
`bundle_id`
`7440c19ff3d49db241c5645b3a541b44be65036cb0ea087c2458e19783a586e5`). All
three findings validated against the actual repository before being
applied — see below per finding. None of revision 22/23/24's other design
choices are reopened; the review's own "Confirmed strengths" section
endorses them unchanged.

### GPT-R38-001 — the clean-checklist precondition made the documented functional-review flow unreachable (blocking, accepted)

Confirmed against the repository, two ways. First, textually:
`.claude/commands/prepare-functional-review.md` writes the checklist into
`docs/ACTIVE_MILESTONE.md` (step 3) and stops at the user gate (step 5) —
no step in that file creates a commit, and no other documented command is
authorized to commit `docs/ACTIVE_MILESTONE.md` on this item's behalf
between preparation and acceptance. Second, live in this very working
tree: `git status` at the start of this revision showed
`docs/ACTIVE_MILESTONE.md` modified and uncommitted — the exact
functional-review checklist for this item's own `WF8b` continued-scope
round, written by a real `/prepare-functional-review` invocation, sitting
exactly in the state revision 24's own pre-commit evidence guard would
refuse. This is not a hypothetical failure scenario; it is this session's
starting state, reproducing the finding directly.

**Fix adopted**: give the checklist a real writer instead of only a
reader-side guard. `/prepare-functional-review` gains a required,
dedicated, metadata-only checklist-evidence commit — see the amended
`D-Scoped-Remediation-Acceptance` text below for the full trailer/
idempotency contract, modeled directly on the existing
`Workflow-Scoped-Remediation-Acceptance` trailer scheme rather than a new
mechanism. `/accept-scoped-remediation`'s own pre-commit evidence guard is
unchanged in its dirty-check policy (still correct, per the review's own
"Confirmed strengths" note) and gains one additional check: a discoverable
checklist-evidence commit must exist for the exact round being accepted,
naming `/prepare-functional-review` as the remedy when it does not.

### GPT-R38-002 — replay was reported before the plan proved the existing acceptance record matched the live round (blocking, accepted)

Confirmed against the repository: revision 24's entry guard (quoted in the
review) stops and reports success the moment a trailer for
`<outstanding_checkpoint_id>/<implementation_revision>` is *discovered* —
before any comparison of that commit's own recorded
`reviewed_implementation_head`/`technical_approval_review_content_id`/
`functional_checklist_blob` against the live values ever runs. The
"conflicting duplicate" comparison revision 24 also specifies exists only
in the provenance-commit paragraph, restated there as "case 1's own
lookup" — but case 1's own lookup, as the entry guard actually specifies
it, already reports success and stops. A round key
(`outstanding_checkpoint_id`/`implementation_revision`) proves only that a
commit exists for this pairing, never that its recorded evidence agrees
with what is about to be recorded again — a hand-edited or corrupted
acceptance record with a matching key would be silently treated as a
valid idempotent replay.

**Fix adopted**: collapse the two descriptions into one function,
`resolve_scoped_remediation_round`, called identically by both the entry
guard and the provenance-commit step (see the amended
`D-Scoped-Remediation-Acceptance` text below) — there is no longer a
cheaper "key exists" path that skips the comparison; only a full
load-and-compare against the discovered commit's own recorded fields may
report `ExactReplay`.

### GPT-R38-003 — replay ordering did not clearly preserve the user-only confirmation requirement (important, accepted)

Confirmed against the repository: `/accept-scoped-remediation` is
specified as user-only via `validate_user_confirmation(...,
stage="scoped_remediation")`, but revision 24's own entry-guard ordering
places replay detection ahead of any stated confirmation check, and no
sentence in that revision says whether current-turn confirmation is
validated before or after the replay short-circuit. A command described
as user-only whose replay path never actually says a confirmation check
runs is a real specification gap, not merely stylistic: an implementation
written literally from revision 24's ordering could report a replay's
existing commit as success without ever calling
`validate_user_confirmation` for that invocation.

**Fix adopted**: state the ordering explicitly. Current-turn user
confirmation for `stage="scoped_remediation"` is validated first, before
target resolution's replay classification runs at all — identically for a
first execution and an exact replay. A separate, explicitly out-of-scope
read-only status command (not built by this revision, no finding requires
it) is named as the correct home for confirmation-free replay inspection,
if that UX is ever wanted.

**Scope discipline (revision 25)**: this revision fixes exactly the two
blocking and one important finding above, entirely within
`D-Scoped-Remediation-Acceptance` (extended to include
`/prepare-functional-review`'s own checklist-evidence commit, since
`GPT-R38-001`'s fix has no other coherent home). It does not reopen the
"no new phase" decision, `apply_technical_approval`'s untouched status,
`GPT-R36-002`/`GPT-R37-002`'s per-round trailer keying, the ten-field
acceptance schema, the `MILESTONE_COMPLETE` own-checkpoint block's
location, or any other design choice from revision 22/23/24 — all
endorsed unchanged by `GPT-R38-*`'s own "Confirmed strengths" section.
`WFR-58` and `WFR-59` are amended in place; a new `WFR-60` is added
(no existing requirement is removed or renumbered). Missing-test items
176, 182, 186, 190 are amended and items 191-196 are added. No file under
`scripts/` or `.claude/commands/` is edited by this revision either — same
discipline as revisions 22/23/24, deferred to the same future dedicated
implementation session.

## WF8b finding disposition (revision 25 → 26) — external plan review found the checklist-evidence trailer scheme self-contradictory and the replay resolver's comparison incomplete (`GPT-R39-001`/`-002`)

**Origin**: independent external plan review of revision 25 (`GPT-R39-*`),
verdict `REVISE`. Full feedback:
`.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md` (bound to plan
revision 25, `review_content_id`
`fe68a6f53699c848c6ad140d3c874ea2586282396f1facfa7c905ceb745c6379`,
`bundle_id`
`4093d80f9770ecab318d52204b5a18cae3063f485d3ce32ba3db2914e9396c9c`). Both
findings validated against the actual repository before being applied —
see below per finding. Neither reopens any earlier revision's other design
choices; the review's own "Confirmed strengths" section endorses them
unchanged.

### GPT-R39-001 — the checklist-evidence trailer scheme specified two incompatible outcomes for the same history (blocking, accepted)

Confirmed against the plan text itself: revision 25 specifies checklist
evidence keyed by the bare round value
`<work_item_id>/<implementation_revision>` (old §"`/prepare-functional-review`
gains a required checklist-evidence commit"), with a changed checklist for
the same round producing a *new, distinct* commit carrying that *same*
value — while also specifying that discovery "reuses `_discover_trailer_commits`'s
existing generic machinery... mirroring `discover_scoped_remediation_commits`/
`AmbiguousScopedRemediationTrailerError` exactly." But `D-Commit-Provenance`'s
own generic contract (unchanged since revision 6) requires *exactly one*
first-parent-reachable match per (trailer key, value, work-item) triple, and
its tie-break exists only for a legitimate rebase/cherry-pick/merge
duplicating one logical event onto a second commit — content-identical to
the original, never a genuine revision. A corrected checklist is not that:
it is a second, content-*different* commit sharing the first's exact
trailer value, which is precisely the history the generic contract's own
"more than one commit still qualifies... this is genuine ambiguity, not a
duplicate to resolve automatically" clause stops on. The plan asserted both
"discovery selects the newer commit" and "mirrors a contract that refuses
on exactly this history" for the same event — mutually incompatible, exactly
as the review found.

**Fix adopted**: give checklist-evidence commits a content-scoped trailer
value instead of a bare round value, so a content revision is a different
*key*, not a second match for the same key — the generic exactly-one-match
contract no longer needs to treat it as ambiguous, because it no longer
looks ambiguous at the Git level. See the amended
`D-Scoped-Remediation-Acceptance` text below for the full trailer/discovery
contract.

### GPT-R39-002 — exact-replay classification compared three fields out of the acceptance record's ten (blocking, accepted)

Confirmed against the plan text: revision 25's `resolve_scoped_remediation_round`
step 3 compares only `reviewed_implementation_head`,
`technical_approval_review_content_id`, and `functional_checklist_blob`
against `live_fields` before reporting `ExactReplay`. The
`scoped_remediation_acceptance` entry itself (`D-Scoped-Remediation-Acceptance`'s
ten-field schema) also records `outstanding_checkpoint_id`,
`active_work_item_id_at_acceptance`, `implementation_revision`,
`functional_checklist_path`, and `acceptance_record_version` — none of
which the resolver's comparison ever reads. `outstanding_checkpoint_id`/
`implementation_revision` are implicitly matched only insofar as they form
the discovery trailer's own key (trusting the trailer's word, not the
recorded entry's own fields, for exactly the kind of hand-edited-record
case `GPT-R38-002` already established must not be trusted); the live
active work-item pointer, the checklist path, and the record's own schema
version/shape are never checked at all. A hand-edited or corrupted entry
whose `active_work_item_id_at_acceptance`, `functional_checklist_path`, or
`acceptance_record_version` disagreed with what a genuine replay would
produce right now would still be reported `ExactReplay` — the same class
of false-positive-idempotency gap `GPT-R38-002` closed for the
three-field subset, left open for the other seven fields.

**Fix adopted**: compare the full canonical set of round-binding identity
and schema fields, not a subset — see the amended
`D-Scoped-Remediation-Acceptance` text below for the complete field list
and the new malformed-record result. "Exact replay" now means what it
says: every field the operation would write if it ran again right now
matches every field already recorded, not merely the three fields revision
25 happened to check.

**Scope discipline (revision 26)**: this revision fixes exactly the two
blocking findings above, entirely within `D-Scoped-Remediation-Acceptance`.
It does not reopen the "no new phase" decision, `apply_technical_approval`'s
untouched status, the per-round trailer keying's *checkpoint*/*revision*
axes (only the checklist trailer's *value* shape changes), the ten-field
acceptance schema (unchanged in field count — comparison coverage changes,
not the schema itself), the `MILESTONE_COMPLETE` own-checkpoint block's
location, the confirmation-before-replay ordering, or any other design
choice from revision 22/23/24/25 — all endorsed unchanged by `GPT-R39-*`'s
own "Confirmed strengths" section. `WFR-58`, `WFR-59`, and `WFR-60` are
amended in place (`WFR-56`'s ten-field schema itself is unchanged — only
comparison/discovery coverage changes, not what is recorded; no existing
requirement is removed or renumbered). Missing-test item 189 is amended
and items 197-206 are added. No file under `scripts/` or
`.claude/commands/` is edited by this revision either — same discipline as
revisions 22/23/24/25, deferred to the same future dedicated
implementation session.

## WF8b finding disposition (revision 26 → 27) — external plan review found acceptance bound to the newest checklist evidence rather than the evidence the user actually reviewed (`GPT-R40-001`/`-002`)

**Origin**: independent external plan review of revision 26 (`GPT-R40-*`),
verdict `REVISE`. Full feedback:
`.ai-review/workflow-v2-1-core/feedback/REVIEW_FEEDBACK.md` (bound to plan
revision 26, `review_content_id`
`5f03a65075e531f3883e47590714dab23e40a8a946b524a161e0a000384dca9c`,
`bundle_id`
`541a8927e0635ab47bc3b2fa64991dfb85e58b88f6391c86ad1c32f55244bc65`). Both
findings validated against the actual repository before being applied —
see below per finding. Neither reopens any earlier revision's other design
choices; the review's own "Confirmed strengths" section endorses them
unchanged.

### GPT-R40-001 — acceptance is bound to the newest checklist evidence, not to the checklist evidence the user actually reviewed (blocking, accepted)

Confirmed against the plan text itself. Revision 26's own
`discover_current_functional_checklist_evidence` always resolves the
round's *current* (first-parent-nearest) evidence commit, and the
pre-commit evidence guard binds `functional_checklist_blob` to whatever
that lookup returns at accept time — there is no reader anywhere in
revision 26's design that compares the discovered evidence against
anything the user was actually shown. The `scoped_remediation`
confirmation itself is validated only by the generic
`validate_user_confirmation` — it must literally name the work item and
the stage, nothing more specific. Missing-test item 200 states the
consequence plainly: "a `Workflow-Functional-Checklist` evidence commit
created after `/prepare-functional-review` already reported an earlier
commit's SHA to the user becomes the round's current evidence for a
subsequent `/accept-scoped-remediation` invocation — discovery always
binds to what is live at accept time, never a stale pointer to what was
originally reported." That is exactly the gap the review names: a
user who reviewed and confirmed checklist A can have their confirmation
silently applied to a later, un-reviewed checklist B, if B's evidence
commit lands before `/accept-scoped-remediation` runs. Visibility of a
new `/prepare-functional-review` report is not evidence the user walked
and accepted that new report, and the earlier confirmation text remains
textually valid against `validate_user_confirmation`'s generic check
either way.

**Fix adopted**: bind the user gate to an explicit checklist-evidence
identity, per the review's required contract. `/prepare-functional-review`
already reports the exact evidence commit SHA and checklist blob to the
user (revision 25, `GPT-R38-001`, unchanged). The required
`scoped_remediation` confirmation text must now additionally contain that
exact evidence commit SHA and blob as explicit, parsed binding fields —
mirroring the binding-field convention `REVIEW_FEEDBACK.md`'s own
`Reviewed bundle ID:`/`Reviewed base commit:`/`Work item:` fields already
establish for external review feedback, and that `plan_approval`/
`technical_approval`'s own recorded `user_confirmation` text already
follows informally. `/accept-scoped-remediation` parses those fields,
resolves the named commit via
`discover_functional_checklist_commits`, verifies it belongs to the exact
live round and that its own committed blob matches the named blob, and
then compares it against the round's current evidence
(`discover_current_functional_checklist_evidence`): if they disagree, the
confirmation is stale and acceptance refuses outright, naming both the
confirmed and the current evidence identity and instructing the user to
review the newer report and reconfirm — never silently substituting the
newer evidence for the reviewed one. See the amended
`D-Scoped-Remediation-Acceptance` text below for the full parser,
verification, and refusal contract.

### GPT-R40-002 — store the checklist evidence commit identity, not only its blob (important, accepted)

Confirmed against the plan text: the ten-field
`scoped_remediation_acceptance` schema (`D-Scoped-Remediation-Acceptance`,
revision 23 `GPT-R36-003`) records `functional_checklist_blob` but no
field for the `Workflow-Functional-Checklist` evidence commit SHA. Once
revision 26 introduced multiple, distinct, coexisting checklist-evidence
commits per round (an older, superseded one and a newer, corrected one,
each with its own distinct trailer value), a bare blob no longer
identifies which of those commits — which provenance event, which
trailer, which position in the round's supersession history — was
actually selected for review. Two genuinely different evidence commits
could in principle carry the same checklist bytes at different points in
a round's history (a revert back to earlier content), making the blob
alone ambiguous as a provenance pointer even though it remains sufficient
as a content check.

**Fix adopted**: add `functional_checklist_evidence_commit` as an
eleventh recorded field, alongside the unchanged
`functional_checklist_blob`, to the exact schema, live first-write
validation, exact-replay comparison, and conflicting-duplicate
classification alike — never only to a subset of those four surfaces.
`acceptance_record_version` advances from `1` to `2` to mark the accepted
record shape change, per the review's own suggestion; no version-1 record
exists anywhere (nothing implementing `D-Scoped-Remediation-Acceptance`
has shipped yet — `scripts/workflow_state.py`'s `APPROVAL_STAGES` does not
yet include `"scoped_remediation"` at all), so no migration path is
needed. See the amended `D-Scoped-Remediation-Acceptance` text below.

**Scope discipline (revision 27)**: this revision fixes exactly the one
blocking and one important finding above, entirely within
`D-Scoped-Remediation-Acceptance`'s evidence-binding contract. It does
not reopen the "no new phase" decision, `apply_technical_approval`'s
untouched status, the per-round trailer keying, the content-scoped
checklist-trailer value shape (`GPT-R39-001`, unchanged), the schema/
version malformed-record check's own structure (only the version number
and field count it checks change), the `MILESTONE_COMPLETE`
own-checkpoint block's location, the confirmation-before-replay ordering,
or any other design choice from revision 22/23/24/25/26 — all endorsed
unchanged by `GPT-R40-*`'s own "Confirmed strengths" section. `WFR-56`,
`WFR-58`, `WFR-59`, and `WFR-60` are amended in place; no existing
requirement is removed or renumbered. Missing-test items 199, 200, 205,
and 206 are amended and new items are added covering the stale-
confirmation refusal path. No file under `scripts/` or `.claude/commands/`
is edited by this revision either — same discipline as revisions
22/23/24/25/26, deferred to the same future dedicated implementation
session.

## Decisions (revision 22)

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

### D-Fingerprint-Generalization — per-work-item plan-stage metadata resolution (revised this round, resolves `WF8B-S1-001` and `OPUS-R25-001` through `-015`; executes `WF4a-i`'s/D-Registry's deferred generalization)

**Revision-17 disposition note, read this first**: the version of this
subsection revision 16 shipped was reviewed (`OPUS-R25-*`, `Status:
REVISE`, see "Round 25 finding disposition" above) and found structurally
sound but not yet safe to implement: five `HIGH`-severity defects and ten
further gaps, all now corrected below. The corrections are additive to the
same design, not a different one — the metadata source, the JSON
declarations file, and the fail-closed philosophy are unchanged; what
changes is closing every place the revision-16 text left two sources of
truth, an unbound reference, or an unspecified writer.

**Authoritative metadata source, per field — corrected**. For any
`work_item_id`, every plan-stage-identity-bearing fact is sourced from
exactly one place, never duplicated or re-derived elsewhere:

| Field | Source |
|---|---|
| `work_item_type`, `plan_path`, `registry_path`, `mapping_path`, `base_commit` | `WORKFLOW_STATE.json`'s `work_items[work_item_id]` entry (`base_commit` added, corrected `GPT-R29-001`: revision 18 already named `base_commit` as identity-bearing and gave it a writer, but the resolution algorithm below never actually read or returned it alongside the other three, leaving every downstream consumer that "obtains the item's declared `base_commit` through `resolve_plan_stage_metadata`" — the CLI's `base` positional, `prepare-ai-review.sh`'s cross-check, the manifest writer — with nothing to obtain) |
| `plan_revision` | **the registry JSON at `registry_path` alone** (`plan_revision` key), cross-checked against the plan document at `plan_path`'s own declared `(Revision N)` title (`load_plan_revision`, unchanged mechanism). `WORKFLOW_STATE.json`'s own `plan_revision` field is **not** a second source — resolved per `OPUS-R25-002` below, it is a non-authoritative display mirror only, and the fingerprint functions no longer accept it as a parameter at all. |
| protected-path set, excluded-path set, excluded-prefix set (plan stage) | `docs/ai-workflow/registry/<work_item_id>-artifacts.json`'s `plan_stage` key |
| the registry/mapping/artifacts files' own claimed identity | each file's own `work_item_id` field, cross-checked against the map key that resolved it |
| that `plan_path`/`registry_path`/`mapping_path` actually name protected content | membership in the resolved protected-path set, checked at resolution time (`OPUS-R25-003` below) — not merely assumed from the fact that a path was named |

**`OPUS-R25-002` — `plan_revision` collapsed to one source.**
`approval_review_content_id` (`scripts/workflow_state.py:654-696`) loses its
`plan_revision` parameter entirely for `stage="plan"` — the resolved value
comes from `resolve_plan_stage_metadata` (below), never from a caller.
`approval_is_current` and `verify_post_approval_manifest_match` stop
passing `plan_revision=work_item.get("plan_revision")`; that call-site
change is part of migration step 6. `WORKFLOW_STATE.json`'s own
`plan_revision` field is retained (existing readers outside the fingerprint
path — reporting, `docs/ACTIVE_MILESTONE.md` narrative — still want a cheap
value to display) but downgraded to a **non-authoritative mirror**: `D3`
gains a validator rule that it must equal the registry JSON's `plan_revision`
whenever `registry_path` is non-null (`PlanRevisionMirrorMismatchError`
otherwise) — the same shape as the two rules revision 16 already added for
`mapping_path`'s nullability and path-triple duplication. **This bundle's
own repository state was already divergent** (`WORKFLOW_STATE.json` said
`15`, the registry JSON said `16`) — corrected in the same commit as
revision 17 (an excluded-path edit, no approval implication); kept in sync
through every revision bump since, including this one (corrected,
`OPUS-R27-008`, which found this sentence still said "both now read `17`"
two revision bumps later; corrected again, `GPT-R29-004`, which found this
sentence still said "both now read `19`" two further revision bumps later
— restated once more here, and this time cross-checked directly against
both files rather than hand-incremented): both now read `21`.

**Resolution algorithm** (`fingerprint.resolve_plan_stage_metadata(repo_root,
work_item_id, *, at_commit=None)` — relocated from `workflow_state.py`,
see `OPUS-R25-010` below):

1. Load `WORKFLOW_STATE.json` — from the live working tree when
   `at_commit` is `None`, or via `git show <at_commit>:docs/ai-workflow/WORKFLOW_STATE.json`
   when given. This choice of source is threaded through every subsequent
   read in this algorithm (registry/mapping/artifacts files too) — a
   commit-source computation never reads the live working tree or the live
   `WORKFLOW_STATE.json` for any of these facts, only what was actually
   committed at `at_commit` (missing-test item 145).
2. `validate_work_item_id(work_item_id)` — stated explicitly as this
   algorithm's own precondition (`OPUS-R25-008`); unlike the CLI flag, a
   `work_item_id` read as a `work_items` map key has never otherwise passed
   this check. `entry = work_items.get(work_item_id)` — absent →
   `UnknownWorkItemError`.
3. `entry["work_item_id"] == work_item_id` — re-asserted defensively even
   though `D3`'s validator already enforces it structurally.
4. `entry["work_item_type"] == "process"` — else `PlanStageNotApplicableError`.
   **`work_item_kind` is not consulted** (`OPUS-R25-011`, corrected):
   a `"process"`-typed item resolves its own plan-stage projection
   regardless of `work_item_kind` (`"process"` or `"synthetic"`) — this
   supersedes `scripts/workflow_fingerprint.py:361-365`'s current comment
   ("'synthetic' … is deliberately not identity-bearing … it borrows the
   real process item's"), which is now wrong and must be rewritten in the
   same commit as migration step 9 states. `v2-1-dry-run` (`work_item_type:
   "process"`, `work_item_kind: "synthetic"`) resolves and fingerprints its
   own plan-stage content exactly like any other process item, once it has
   the metadata below to do so.
5. `plan_path`, `registry_path`, `mapping_path`, **and `base_commit`**
   (corrected, `GPT-R29-001`: read alongside the other three declaration
   facts here, not left for a caller to obtain some other way) read from
   the entry; any one of the four being `None` → `MissingPlanStageMetadataError`,
   naming which field.
6. **Path grammar** (new, `OPUS-R25-008`): each of the three, independently,
   must be repo-relative (no leading `/`), use POSIX separators, contain no
   `.`/`..` path component, name no symlink component, and exist as a
   tracked regular file at the resolved source — else
   `InvalidPlanStageMetadataPathError`, naming the field and the failing
   rule. Closes the traversal/aliasing gap `load_plan_revision`'s bare
   `repo_root / registry_path` join would otherwise leave open (an absolute
   value silently replaces `repo_root`; a `../` value escapes the worktree).
7. `artifacts_path = <work_item_id>-artifacts.json` under
   `docs/ai-workflow/registry/` (pure string templating, no I/O — collision
   between two different `work_item_id`s is structurally impossible, since
   `work_items` map keys are already unique). Absent at the resolved source
   → `MissingWorkItemArtifactsDeclarationError`.
8. Load the registry, mapping, and artifacts JSON files from the resolved
   source; assert each file's own `work_item_id` field equals the key that
   resolved it — else `RegistryWorkItemIdMismatchError`/
   `MappingWorkItemIdMismatchError`/`ArtifactsWorkItemIdMismatchError`
   respectively, naming both the expected and the found value.
9. **Uniqueness, relocated and widened** (`OPUS-R25-007`, corrected — this
   step, not `validate_state`, is now the enforcement point the read path
   actually runs; step 1 already loaded the whole `work_items` map, so the
   cost is one pass already paid): no non-null `plan_path`, `registry_path`,
   or `mapping_path` may be claimed by any *other* work item, checked
   independently per field, not only as a triple — else
   `DuplicateWorkItemArtifactPathError`, naming the field, both work-item
   ids, and the shared value. `validate_state` keeps the same rule as a
   write-time, whole-map structural check (belt-and-suspenders — a
   hand-edited or half-written state file that never passed through a
   writer bypasses validator-only enforcement entirely, per `OPUS-R25-007`'s
   own failure scenario), now restated to match this widened, per-field
   shape rather than triple-equality.
10. `protected_paths, excluded_paths, excluded_prefixes =
    fingerprint.load_plan_stage_classification(<source>, artifacts_path)`.
11. **Path-to-role binding** (new, `OPUS-R25-003`): `plan_path`,
    `registry_path`, and `mapping_path` must each be a member of
    `protected_paths`, and the three must be pairwise distinct — else
    `PlanStageMetadataNotProtectedError`, naming the field(s) and both
    values on a collision. This is what makes `mapping_path` a materially
    consumed, validated fact rather than a resolved-and-ignored one (it was
    revision 16's own unaddressed gap: resolved at former step 10, fed to
    nothing) — the reviewed digest is now provably a hash *of* the files
    the item claims as its own plan/registry/mapping, not merely a hash
    computed *alongside* that claim.
12. `plan_revision = fingerprint.load_plan_revision(<source>, registry_path, plan_path)`.
13. Return a named, immutable `PlanStageMetadata` result object — **not a
    positional tuple** (corrected, `GPT-R29-001`: a bare tuple had already
    grown three fields since revision 15 with no rename, becoming exactly
    the kind of thing an implementer or a later revision misdescribes; a
    named object makes an added or reordered field a loud attribute-access
    error at every existing call site instead of a silent positional
    misread) — with fields `work_item_id`, `work_item_type`, `plan_path`,
    `registry_path`, `mapping_path`, **`base_commit`**, `plan_revision`,
    `protected_paths`, `excluded_paths`, `excluded_prefixes`. `base_commit`
    joining this return value (corrected, `GPT-R29-001`) is the fix this
    finding requires: step 5 above already reads it as one of the four
    nullable declaration facts, but revision 20's own text stopped short of
    also returning it, leaving every downstream consumer that is specified
    elsewhere in this section to "obtain the item's declared `base_commit`
    through `resolve_plan_stage_metadata`" — the CLI's `base` positional
    below, `prepare-ai-review.sh`'s cross-check, `write_manifest_with_verified_identifiers`'s
    own comparison — with no field on the actual return value to obtain it
    from, forcing exactly the second read, silent caller-supplied fallback,
    or unimplementable contract this finding's failure scenario describes.

`fingerprint.compute_review_content_id_plan_stage_for_work_item(repo_root,
work_item_id, *, base=None)` (worktree-source) and
`compute_review_content_id_plan_stage_at_commit_for_work_item(repo_root,
work_item_id, commit, *, base=None)` (commit-source) call steps 1-13 above
**exactly once** with `at_commit=None`/`at_commit=commit` respectively,
obtaining the resolved `PlanStageMetadata` (including its `base_commit`
field, per the `GPT-R29-001` correction above), then call
`compute_review_content_id_plan_stage`/`_at_commit` with the resolved
values — `base if base is not None else metadata.base_commit` as the
digest's `base_commit` input (corrected, `GPT-R29-001`: `base` is now an
optional override on these two functions, resolved from the one
`resolve_plan_stage_metadata` call already made here, never a second,
independent read of `WORKFLOW_STATE.json` by the caller before invoking
either function — closing the exact ambiguity the finding's failure
scenario describes). `MissingPlanStageMetadataError` (condition 3) already
fires inside step 5 if `base` is omitted and the resolved item's own
`base_commit` is `null`; no separate null check is needed here.

**Module placement, stated explicitly** (`OPUS-R25-010`, corrected): both
functions above and `resolve_plan_stage_metadata` live in
**`scripts/workflow_fingerprint.py`** — not `workflow_state.py` as revision
16 said. `workflow_fingerprint.py` gains a minimal `WORKFLOW_STATE.json`
reader (parse the JSON, index `work_items[id]`; it does not import or reuse
`workflow_state.validate_state`, which stays `workflow_state.py`'s own,
heavier concern). This direction avoids a real import cycle revision 16's
placement would have created: `workflow_state.py` already imports
`workflow_fingerprint` (`workflow_state.py:106-107`); `workflow_state.py`'s
`approval_review_content_id` now simply calls
`fingerprint.compute_review_content_id_plan_stage_at_commit_for_work_item(...)`,
preserving the existing one-way import direction. **Fail-open defaults
retired, not kept** (corrected — revision 16 said the opposite):
`compute_review_content_id_plan_stage`/`_at_commit`'s `protected`/
`excluded_paths`/`excluded_prefixes` parameters become **required** (no
default) once migration step 8 (full suite green) completes.
`PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES`
are retired as live call defaults and kept only as a named
migration-comparison fixture for missing-test item 141 — the exact class of
defect this whole finding is about (`--work-item-id` silently meaning
"core") cannot survive if nothing calls the plan-stage functions without an
explicit, resolved argument, defaults included. The 81 pre-existing
hermetic tests are updated to pass the fixture explicitly at their call
sites — a mechanical test-call-site edit, not a behavior change to what
they assert.

**Effect on `--work-item-id`** (corrected, `OPUS-R25-005`; write path
corrected, `OPUS-R28-010`). The CLI no longer defaults `--work-item-id` to
the literal `"workflow-v2-1-core"`. For the **read-only inspection path**
(`__main__`'s default display, no `--write-manifest`), an omitted
`--work-item-id` resolves to the live `active_work_item_id`
(`docs/ai-workflow/WORKFLOW_STATE.json`), matching how `/review-plan`/
`/record-manual-plan-review` already resolve an omitted work-item argument,
and may still be passed explicitly to target a non-active item — a wrong
guess here is harmless, per `OPUS-R18-002`'s no-mutation invariant: the
worst case is printing the wrong item's identifiers, not writing anything.
**For the `--write-manifest` write path, `--work-item-id` is required —
never defaulted at all** (corrected, `OPUS-R28-010`: revision 19 kept the
live-`active_work_item_id` default for both call sites, but
`OPUS-R27-004`'s own fix made this same write path bind a directory the
resolved `work_item_id` alone selects — before that fix the default was
comparatively inert, since `--write-manifest` hard-errored on a
non-existent resolved directory; after it, an omitted `--work-item-id`
during the fix session's own work, which runs with `active_work_item_id`
pinned at `v2-1-dry-run` throughout, would resolve to `v2-1-dry-run` and
bind whatever `.ai-review/v2-1-dry-run/current` already held — the same
"one instance retired while a structurally identical sibling survived"
pattern this revision's own self-review note already names as recurring.
**The exact failure shape is narrower after `GPT-R29-002`'s correction**
(the write path no longer creates a directory or silently binds a
near-empty one, since binding now requires a pre-existing complete
generation — see the "No `MANIFEST.md` present" outcome above), **but the
underlying risk is not**: if a complete, unbound generation for
`v2-1-dry-run` already exists at that path (the ordinary case immediately
after its own `prepare-ai-review.sh` run finishes), an omitted flag would
still silently bind the wrong item's directory rather than
`workflow-v2-1-core`'s own. Requiring the argument on the write path
mirrors `prepare-ai-review.sh`'s own plan-stage rule and closes the gap for
the same reason). A literal default naming one work item is exactly the
shape of bug this revision exists to remove; neither a literal nor a
silently-resolved live default is safe on a path that binds a directory to
an identity. The CLI's two call
sites (`__main__`'s read-only display, live-default; and
`write_manifest_with_verified_identifiers` under `--write-manifest`,
required-argument) both resolve through
`compute_review_content_id_plan_stage_for_work_item`/`resolve_plan_stage_metadata`,
never a hardcoded literal, and never an implicit default on the write
path.

**The `base` positional loses its own hardcoded literal too** (new,
`OPUS-R26-003`; revision 17 retired only the `--work-item-id` literal and
left a second one standing). Today `base`'s `argparse` default is
`workflow-v2-1-core`'s own base commit
(`workflow_fingerprint.py:1721-1724`), and `base_commit` is not a
diagnostic — it is a hashed member of the plan-stage projection
(`"base_commit": base_full`, `workflow_fingerprint.py:844` and its
`_at_commit` counterpart), exactly as identity-bearing as `plan_revision`
or the path sets. Leaving it a literal would mean a second item's
`review_content_id` silently incorporates `workflow-v2-1-core`'s base
commit whenever `base` is omitted — the same defect class `--work-item-id`'s
correction removes, reintroduced through the sibling argument. Corrected,
both halves together (this closes the gap revision 17's own self-review
notes named but deliberately left open):

- **`base_commit` gains a named writer** (the writer revision 17 scoped
  out): `workflow_state.route_work_item`/`default_work_item` gain a
  `base_commit: str | None` parameter, the same mechanism `mapping_path`
  already uses, written into the entry at creation time — the third fact
  the "Creation path" subsection below now names as a sole-writer
  obligation, alongside `plan_path`/`registry_path`/`mapping_path`.
- **The `base` positional resolves from the requested work item's own
  `base_commit`** when the argument is omitted — required, not defaulted;
  `MissingPlanStageMetadataError` if the resolved item's `base_commit` is
  `null` (new fail-closed matrix condition 13 below) — never a fallback to
  any other item's value. **Resolved from the same single
  `resolve_plan_stage_metadata` call `compute_review_content_id_plan_stage_for_work_item`/
  `_at_commit_for_work_item` already perform** (corrected, `GPT-R29-001`:
  step 13's returned `PlanStageMetadata` now carries `base_commit`
  explicitly, so this resolution is reading a field off that one result,
  never a second, independent read of `WORKFLOW_STATE.json`). An explicit
  `base` argument remains a validated override (used for `_at_commit`-style
  historical recomputation), never a silent default.
- **`MANIFEST.md`'s `base_commit` field is cross-checked, not merely
  recorded**: `write_manifest_with_verified_identifiers` asserts the
  resolved item's declared `base_commit` equals the value about to be
  rendered before writing, the same pattern condition 12's `work_item_id`
  check already uses — a disagreement between the CLI argument, the
  resolved state, and the manifest fails closed rather than silently
  picking one source.

A second item can therefore never inherit `workflow-v2-1-core`'s base
commit: either it has its own resolved, non-null `base_commit` (written at
creation, per the "Creation path" subsection), or the computation fails
closed naming the missing field — there is no third path through which a
literal or another item's value could reach the hash.

**Manifest/bundle bound to an explicit work item** (new, `OPUS-R25-005`).
Three additions, all to `MANIFEST.md`'s content and the write path guarding
it — none to the frozen hashing algorithm itself:

- `render_manifest_md` gains `work_item_id`, `work_item_type`,
  `plan_revision`, and `base_commit` as new rendered fields (plain lines,
  alongside the existing `worktree_root`/`generation_head` diagnostics) —
  ordinary hashed `bundle_id` content like every other bundle file, never
  part of `review_content_id` (that digest is computed before rendering,
  over the protected-path manifest only, unchanged). Today's `MANIFEST.md`
  records none of these four fields at all (confirmed directly against
  `render_manifest_md`'s current field list) — a bundle with no
  self-declared owner is exactly what let `-005`'s failure scenario stay
  silent. `work_item_type` joins the set this revision (`OPUS-R26-003`,
  acceptance criterion 18): it is exactly as identity-bearing a hashed
  projection field (`workflow_fingerprint.py:840`) as the other three, and
  its omission from revision 17's field list was a usability gap, not a
  deliberate scoping decision.
- **New fail-closed condition, matrix item 12 below, corrected to a
  first-write-safe rule** (`OPUS-R25-005`, corrected `OPUS-R26-001`; the
  bundle directory the write targets is always the one resolved from the
  explicitly-resolved work item — the same resolution
  `resolve_plan_stage_metadata`/the "one authoritative work-item selection
  rule" below already performs — never re-derived from the manifest being
  read):
  `write_manifest_with_verified_identifiers` reads an *existing*
  `MANIFEST.md` at that resolved `bundle_dir`, if one is present, before
  writing, and parses its `work_item_id:` **header line** specifically
  (never a substring scan — this bundle's own current `MANIFEST.md` names
  `work_item_id` only inside an unrelated exclusion-justification sentence
  on line 21, a decoy a naive substring reader would false-positive on).
  Three outcomes, every one of them named, not two with a gap between:
  - **No `MANIFEST.md` present**: the directory is unbound. **`write_manifest_with_verified_identifiers`
    never creates the bundle directory or any bundle content itself**
    (corrected, `GPT-R29-002`, retracting the "just created by this same
    call" disjunct `OPUS-R28-008` added: that disjunct let a standalone
    `--write-manifest --work-item-id <new-item>` invocation `mkdir -p` an
    empty directory and then bind it, but `compute_bundle_id` hashes the
    bundle's required generation files — `REVIEW_REQUEST.md`, `PLAN.md`,
    `DIFF.patch`, `TEST_RESULTS.md` — none of which a bare `mkdir -p` can
    create; the disjunct therefore specified an outcome the algorithm it
    shares a function with cannot produce, exactly the contradiction
    `GPT-R29-002` names). Directory creation and content generation remain
    exactly one function's job, unchanged from every earlier revision:
    `prepare-ai-review.sh` (or, for a harness-created fixture, an
    equivalent full-generation step) creates the scoped directory and
    writes its complete required file set; `write_manifest_with_verified_identifiers`
    only ever computes identifiers over files that already exist and writes
    `MANIFEST.md` next to them. Given an explicit, resolved `work_item_id`,
    it **resolves its own `bundle_dir` internally as
    `.ai-review/<work_item_id>/current`, dropping the caller-supplied
    parameter** (`OPUS-R28-008`'s surviving contribution, kept unchanged:
    only as an assert-equal cross-check against the internally resolved
    value for any caller that still passes one, rather than trusting it) —
    **never** `resolve_bundle_dir`'s existence-gated scoped-else-flat
    fallback, so the write path can never target the wrong (flat) directory
    for a new item, the one part of `OPUS-R27-004`'s original fix that this
    correction keeps. `resolve_bundle_dir`'s own scoped-else-flat resolution
    remains unchanged for its original read-only/inspection use, where
    falling back to a plausible existing directory to *display* is
    harmless. **The bind precondition is one check, not two** (corrected,
    `GPT-R29-002`, collapsing `OPUS-R28-008`'s two disjuncts — "just
    created" or "already complete" — into the single one that was always
    actually sound): bind only when the resolved directory **already**
    contains a complete generation's required file set — the same set
    `compute_bundle_id`'s existing `MissingRequiredBundleFileError` already
    enforces. A resolved directory that does not exist at all is simply the
    limiting case of this same check, not a distinct mechanism or a new
    exception type: every required file is trivially absent from a
    nonexistent directory, so `MissingRequiredBundleFileError` fires
    exactly as it would for a directory that exists but is missing one file
    (an interrupted prior generation run) — naming the first missing file
    (or, for an absent directory, its path) either way, and never binding a
    partial or nonexistent bundle as authoritative. The ordinary first-write
    case this outcome exists for is therefore: `prepare-ai-review.sh`
    finishes writing `.ai-review/v2-1-dry-run/current`'s complete content
    first, and only then does its own final `--write-manifest` step (or a
    manual follow-up invocation) bind it — content generation strictly
    precedes identifier computation strictly precedes the manifest write,
    with no step requiring a later step's output to already exist. A
    standalone `--write-manifest --work-item-id <new-item>` invocation,
    run **before** any bundle content has been generated for that item,
    therefore refuses with the same `MissingRequiredBundleFileError`, naming
    the resolved (possibly nonexistent) directory and the missing required
    file(s) — it does not, and by construction cannot, "create and bind" a
    bundle out of nothing.
  - **`MANIFEST.md` present and its `work_item_id:` line equals the
    resolved item**: agreement; the write proceeds as an ordinary
    refresh.
  - **`MANIFEST.md` present and either declares no `work_item_id:` line at
    all (revision 17's own corrected text left exactly this branch
    unhandled — every manifest in this repository today is in this state,
    since `render_manifest_md` never emitted the field before this
    revision) or declares one that disagrees with the resolved item**:
    refuse with `BundleWorkItemMismatchError`, naming the resolved item,
    the directory, and either "unbound" or the disagreeing value found.
    Absence of the field is a distinct, explicitly fail-closed outcome —
    never treated as "no information, proceed" — because that is
    precisely the state that let `OPUS-R25-005`'s failure scenario survive
    the first correction: an unbound manifest was neither "not present"
    nor "names a different id," so neither branch fired and the write
    proceeded unconditionally.
  - **The one legitimate exception, named explicitly and scoped to
    migration**: rebinding an existing, currently-unbound bundle directory
    to the work item it has always actually belonged to is performed only
    by the one-time migration step below (a separately-named, explicit
    opt-in argument, never the ordinary write path's default behavior) —
    so the first post-migration write to `.ai-review/current` cannot
    silently overwrite a foreign identity, and no later invocation can
    invoke the rebinding opt-in against an already-bound directory (that
    case is the ordinary disagreement outcome above, refused).

  A bundle directory's first-ever bound manifest write (whether by the
  ordinary "absent" path or by the one-time migration rebinding) is what
  binds it; every later write to that same directory must agree or fail
  closed — foreign, partial, legacy, or ambiguous manifests all fail
  closed by construction, since the only non-refusing outcomes are
  "genuinely absent" and "present and agreeing." This directly prevents
  the failure scenario `OPUS-R25-005` names — `--work-item-id v2-1-dry-run
  --write-manifest`, run before `.ai-review/v2-1-dry-run/current` exists,
  would otherwise overwrite `workflow-v2-1-core`'s own live
  `.ai-review/current/MANIFEST.md` with a foreign digest — now refused,
  naming the mismatch, instead of silently destructive; and it closes the
  gap `OPUS-R26-001` found in revision 17's own version of this same fix.
- The CLI's own read-only inspection path (no `--write-manifest`) prints
  the same comparison as a warning rather than a hard refusal — read-only
  mode already never mutates anything (`OPUS-R18-002`'s invariant,
  unchanged), so there is nothing to protect there beyond an honest report.

**`prepare-ai-review.sh` shares the same authoritative path** (new,
`OPUS-R25-012`, resolves the affected-commands audit gap; corrected
`OPUS-R26-002`). Revision 16's audit concluded no `.claude/commands/*.md`
text needed to change because every command delegates by name to
`scripts/workflow_fingerprint.py`/`workflow_state.py` — true, but
incomplete: `scripts/prepare-ai-review.sh`, which is what `/milestone-plan`
step 6 and `/apply-plan-review` step 7'.1 actually *run*, contains no
reference to `workflow_fingerprint`, `--write-manifest`, or `MANIFEST`
anywhere (confirmed by grep over the whole script) — `MANIFEST.md` is only
ever produced by a separate, manual CLI invocation nothing forces to carry
a matching `--work-item-id`.

**One authoritative work-item selection rule, not two — and the work-item
id is now required, not resolved, for the plan stage** (corrected,
`OPUS-R26-002`; corrected again, `OPUS-R27-003`, which found the
single-resolution rule right as far as it went but its consequences
unstated). Revision 17's own fix specified the new step as `python3
scripts/workflow_fingerprint.py <base-sha> --work-item-id
<work-item-id-or-live-active> --write-manifest` — that hyphenated "or" is
two rules, not one, and the script's own pre-existing directory choice
(`WORK_ITEM_ID=${3:-}` at `:24`, then `ROOT_DIR=".ai-review/$WORK_ITEM_ID"`
if set, else `ROOT_DIR=".ai-review"` at `:78-84`) reads only the first —
so when the optional third argument is omitted, the bundle directory
resolves one way and the manifest's `--work-item-id` resolves another.
Revision 18 fixed the "or" by resolving the work-item id exactly once
(third argument if given, else the live `active_work_item_id`) for both
`ROOT_DIR` and `--write-manifest` — but `OPUS-R27-003` found three
unstated consequences: because the resolved value is now never empty,
`ROOT_DIR` could never take the flat branch again, silently retiring
`.ai-review/current` and stranding migration step 6a; and, independent of
both, the rule binds the *directory* and the *manifest* to each other
while leaving the bundle's *content* — which `prepare-ai-review.sh`
derives from `<base-sha>` and the whole working tree, never from the work
item — bound to neither.

Resolved by going one step further than "resolve once": **for `stage ==
"plan"`, the work-item id is a required third argument to
`prepare-ai-review.sh`, never resolved from the live `active_work_item_id`
for this stage** — the two call sites that invoke it
(`/milestone-plan` step 6, `/apply-plan-review` step 7'.1) always know the
work item they're bundling, so requiring it costs nothing and removes the
fallback branch entirely: there is no longer an "else" for `ROOT_DIR` and
`--write-manifest` to disagree about, by construction, not by a
post-hoc cross-check. `ROOT_DIR` for the plan stage is therefore always
`.ai-review/<the-required-argument>/current`. **The flat,
optional-third-argument layout is explicitly retained, unchanged, for
every other stage** (`implementation`/`post-fix`/`functional-review`),
where `MANIFEST.md` is never written and no directory/manifest binding
ambiguity exists to resolve — this is the one legitimate case in which the
flat branch remains reachable, named explicitly rather than left as an
accidental survival.

**Bundle content is bound to the resolved item too**, closing the third
consequence: before generating any bundle content, `prepare-ai-review.sh`
cross-checks the resolved item's own declared `base_commit`
(`WORKFLOW_STATE.json`'s `work_items[<id>].base_commit`) against the base
commit it was actually handed, and refuses on disagreement, naming both
values. Three things this revision left unstated, corrected here
(`OPUS-R28-006`):

1. **Who reads it.** The read goes through a named helper, never a second,
   ad hoc metadata reader: `python3 -c 'import workflow_fingerprint as
   fingerprint; ...'` invoking
   `fingerprint.resolve_plan_stage_metadata(repo_root, work_item_id)` — the
   same resolver every other plan-stage read in this design routes through,
   so the same `work_item_id` validation, `entry["work_item_id"]`
   self-consistency assertion (step 3), path-grammar checks (step 6), and
   uniqueness check (step 9) all run before the comparison is even made. An
   inline `python3 -c 'json.load(...)'` reading `WORKFLOW_STATE.json`
   directly would be exactly the second reader of plan-stage metadata this
   design's own opening paragraph forbids.
2. **What is compared.** `prepare-ai-review.sh` already distinguishes the
   raw argument from the resolved commit: `REQUESTED_BASE_SHA=$1` (`:23`)
   and `BASE_SHA=$(git rev-parse --verify "${BASE_SHA}^{commit}")` (`:47`).
   The comparison is between the resolved item's declared `base_commit` and
   the **resolved** `BASE_SHA` — never the raw `REQUESTED_BASE_SHA` — so an
   abbreviated SHA (`162154d`) or a symbolic ref (`HEAD~14`) that `git
   rev-parse` has always accepted still passes when it denotes the correct
   commit; only a genuine disagreement between the two resolved, full
   commit hashes refuses. The refusal message names both the requested form
   and the resolved form, plus the declared value, so an operator who typed
   a ref can see why it disagreed rather than being shown two full hashes
   with no link back to what they typed.
3. **What error.** The refusal exits non-zero with a distinct, greppable
   message before any bundle content is written — a shell exit, not a
   Python exception, since a shell script cannot raise
   `BundleWorkItemMismatchError` directly. Condition 13's matrix row
   (below) is amended to name both enforcement points explicitly: this
   early script-level refusal (a non-zero exit, no exception object) and
   the downstream `--write-manifest` check inside
   `write_manifest_with_verified_identifiers` (which does raise
   `BundleWorkItemMismatchError`) — the same underlying disagreement,
   caught at two points, each emitting the form appropriate to where it
   runs, not two different exception conditions.

With the work-item id required and the base commit cross-checked against
it through the one shared resolver, content, directory, and manifest all
derive from and are checked against the one same resolved value.

Concretely: for `stage == "plan"` only, `prepare-ai-review.sh` gains a
final step invoking `python3 scripts/workflow_fingerprint.py <base-sha>
--work-item-id <the-required-argument> --write-manifest` — the exact same
CLI entry point, not a reimplementation — after the rest of the bundle is
written, against the same required value `ROOT_DIR` already used earlier
in the same script invocation. This is the one place the "no command-file
text change" conclusion was actually wrong; corrected here rather than
left standing. (`implementation`/`post-fix`/`functional-review` stages are
unaffected — `MANIFEST.md` is a plan-stage-only artifact today, unchanged
scope.)

**Artifacts declarations file gets a real approval binding** (new,
`OPUS-R25-006`). Today `scripts/workflow_fingerprint.py` (where the
plan-stage sets currently live) is implementation-stage *protected*
(`scripts/` is a `workflow-v2-1-core-artifacts.json` `protected_prefixes`
entry) — changing the sets is bound by `technical_approval`. After
migration, they live in `docs/ai-workflow/registry/<id>-artifacts.json`,
which is a plan-stage *excluded* prefix and — unless carved out — would
also be an implementation-stage excluded prefix (the file's own
`excluded_prefixes` entry for `docs/ai-workflow/registry/` currently says
"including this file itself"). Since the resolved `plan_stage` sets are
hashed into `review_content_id` (unchanged, `OPUS-R8-014`'s original
invariant), an editable-under-no-approval declarations file would let
someone widen an exclusion and re-bless the resulting digest in the same
session, with no gate ever having seen the classification change — the
narrower, certain harm is that the file's own stated invariant ("editing
this file never changes `review_content_id` or stales the plan approval")
would simply be **false** for its new `plan_stage` key while remaining true
for `implementation_stage`.

**Fixed as a concrete, per-item self-referential entry, not a shared
placeholder** (corrected, `OPUS-R26-004`; revision 17's own version of this
fix specified the carve-out as an exact-path entry keyed literally
`docs/ai-workflow/registry/<work_item_id>-artifacts.json` — but
`load_implementation_stage_classification` loads `protected_paths` from
**each work item's own** `<id>-artifacts.json` file
(`workflow_fingerprint.py:915-947`; `DEFAULT_ARTIFACTS_PATH` is a per-file
default, not a shared cross-item map), and `classify_path_implementation_stage`
tests membership with a plain `if path in protected_paths` exact-match
dictionary lookup, with no templating step anywhere
(`workflow_fingerprint.py:939-947`, `:967`) — a key containing the literal
characters `<work_item_id>` matches nothing real, so the file would fall
through to the `docs/ai-workflow/registry/` excluded prefix and remain
exactly as unprotected as before the fix, the correction silently absent):
each work item's own `<id>-artifacts.json` declares, within its own
`implementation_stage.protected_paths` section, the **concrete literal
path to itself** — `workflow-v2-1-core`'s own file names
`docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`, spelled out
in full, not a template — checked first, before any prefix, the same
pattern the immutable registry/mapping files already use.
`generate_artifacts_declarations` (the "Creation path" subsection below)
emits that self-referential concrete entry automatically as part of the
default template for every new item, so the protection is general *by
construction* — every item's own file protects itself without needing a
placeholder the exact-match classifier could never resolve — rather than
general by a shared literal that happens to be spelled with angle
brackets. Migration step 2 transcribes `workflow-v2-1-core`'s own concrete
entry (not the placeholder form); migration step 7 corrects the file's own
`_comment` field and `load_implementation_stage_classification`'s
docstring to state plainly that `plan_stage` edits *do* change
`review_content_id` (bound by `plan_approval`) while `implementation_stage`
edits do not (bound by `technical_approval` instead, via this exact-path,
self-referential protection).

**The implementation-stage `artifacts_path` gets the same generalization as
the plan stage, not a surviving literal** (new, `OPUS-R27-002`; the same
"one literal retired while a structurally identical sibling survived"
pattern `OPUS-R26-003` named for the `base` positional, found a third time).
`approval_review_content_id`'s `artifacts_path: Path =
fingerprint.DEFAULT_ARTIFACTS_PATH` parameter (`scripts/workflow_state.py:654-660`)
still defaults to the literal `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`
for `stage="implementation"`, and `approval_is_current` — the durability
check for **every** work item's `technical_approval` — calls it with no
`artifacts_path` argument at all (`:715-719`); `promote_legacy_work_item`
carries the same literal default (`:2079`) while its own docstring already
claims the opposite ("scoped to `work_item_id`'s own `artifacts_path`,"
`:2095`). Since the implementation-stage projection hashes its own path
sets exactly as the plan-stage one does, this reaches a hashed field for an
arbitrary work item — `OPUS-R26-003`'s own escalation criterion. Corrected:
`artifacts_path`, for `stage="implementation"`, is resolved per work item
via `fingerprint.artifacts_path_for_work_item(work_item_id)` — the same
helper the plan-stage resolver (above) already introduces, pure string
templating, no new I/O path — for every caller (corrected, `OPUS-R28-001`;
`OPUS-R27-002`'s own list named `implementing_entry_reachable`, which never
reaches `approval_review_content_id` with `stage="implementation"` at all —
it only calls `approval_is_current(..., stage="plan", ...)`, hardcoded — and
omitted `verify_post_approval_manifest_match`, which does reach it, called
with `stage=...` for either stage by `/approve-review` step 6a immediately
after the technical-approval commit lands). The list is derived mechanically,
not by inspection: every function whose body reaches
`approval_review_content_id` resolves and passes it explicitly. Today that is
exactly two call sites — `approval_is_current` (`workflow_state.py:715`) and
`verify_post_approval_manifest_match` (`:760`) — both gain the explicit
resolve-and-pass; `promote_legacy_work_item` is unaffected, since it calls
`load_implementation_stage_classification` directly (`:2128`) and never goes
through `approval_review_content_id`; `implementing_entry_reachable` is
unaffected for the same reason, listed here only to state explicitly that it
is plan-stage-only and needs no `artifacts_path` at all. The parameter's
literal default is retired in the same migration step (step 6 below) as the
plan-stage CLI literals. A second work item's
`implementation_stage.protected_paths` — including the self-referential
entry `OPUS-R26-004` just added above — is now actually read by something:
editing `workflow-v2-1-core`'s own artifacts file leaves a second item's
`technical_approval` untouched, and editing the second item's own file
stales only its own approval, never `workflow-v2-1-core`'s.

**Creation and resume path for a work item's declaration facts** (new,
`OPUS-R25-004`, closes the ordering gap that left WF8b's S1 blocked one
error deeper after the originally-specified fix; extended `OPUS-R26-003`
to bring `base_commit` into scope; **retitled and extended, `OPUS-R28-002`
— see the resume-branch bullet below, without which `v2-1-dry-run` itself,
the very item S1 exercises, can never acquire these facts**). Three
writers, one ordering, stated explicitly — this is the piece revision 16
omitted entirely:

- **`plan_path`/`registry_path`/`mapping_path`/`base_commit`, sole
  writer**: `workflow_state.route_work_item`/`default_work_item` gain
  `mapping_path: str | None` and `base_commit: str | None` parameters,
  written into the entry alongside the existing `plan_path`/`registry_path`
  ones — the same mechanism, not a new one, for both. `/milestone-plan`
  step 1 `[2.1]` (which already derives a `work_item_id` slug and calls
  `route_work_item`) is the one place all four facts are decided, before
  anything downstream reads them. **This is the one command-file text
  change this revision concedes is needed** — revision 16's "no
  command-file text change" audit conclusion was wrong for
  `/milestone-plan` specifically; corrected here rather than left standing.
  (Revision 17 scoped `base_commit`'s writer out entirely — `route_work_item`
  did not take it as a parameter, and `workflow-v2-1-core`'s own value was
  set outside that function entirely — reasoning that it touched only the
  four facts `WF8B-S1-001` was actually about. `OPUS-R26-003` found that
  gap load-bearing, not merely deferred: with no resolved per-item
  `base_commit`, the CLI's `base` positional had nowhere non-literal to
  fall back to, so the literal default survived. Brought into scope here
  rather than left open a second revision running.)
- **The resume branch gains the same four writers, not just the creation
  branch** (new, `OPUS-R28-002`; this is the finding's option (a), the
  preferred, smallest, generalizing fix). `route_work_item`'s docstring
  states plainly today that an id naming an existing non-terminal entry
  resumes it and only `plan_revision`/`state_revision`/`last_transition`
  advance (`workflow_state.py:1342-1345`); `default_work_item`, which
  writes `plan_path`/`registry_path` (and, after this revision,
  `mapping_path`/`base_commit`), is only ever called from the
  `existing is None` branch (`:1370-1376`). `v2-1-dry-run` is exactly the
  case this misses: it exists today (`WORKFLOW_STATE.json`, written by
  commit `9317b1c`) with `phase: "PLANNING"` (non-terminal, so the resume
  branch is taken), `registry_path: null`, `base_commit: null`, and no
  `mapping_path` key — a pre-declared entry with no declaration facts yet,
  which is precisely what WF8b's own entry step intentionally created it
  as. Corrected: for each of `plan_path`/`registry_path`/`mapping_path`/
  `base_commit`, `route_work_item`'s existing-entry branch (`:1377-1380`)
  now takes each field as an optional argument and, per field
  independently: if the stored value is `null` and a non-null argument is
  supplied, writes it; if the stored value is non-null and the supplied
  argument disagrees, raises `WorkItemDeclarationFactConflictError` (a
  declaration fact is immutable once set, consistent with the existing
  identity-field immutability rule at `:1344-1345`); if no argument is
  supplied for a field, that field is left untouched. This makes
  `/milestone-plan`'s step 1 `[2.1]` call the same for a fresh id and a
  pre-declared non-terminal one — it always passes all four facts it just
  derived, and the resume branch either accepts them (first time) or
  silently no-ops on an exact repeat (idempotent re-run), and only raises
  on a genuine conflict. `v2-1-dry-run` is not special-cased; it is simply
  the first non-terminal entry this branch is ever exercised against for
  real. (The finding's rejected alternative, option (b) — having migration
  step 1 backfill `v2-1-dry-run`'s three null facts concretely as a one-off
  — is not taken: it would close S1 specifically while leaving the general
  resumed-item gap open for the next process work item created the same
  way `v2-1-dry-run` was, which is worse, not smaller.)
- **`<work_item_id>-artifacts.json`, sole writer and default template**:
  written by `/milestone-plan` step 3 `[2.1]`, in the same pass as
  `workflow_state.generate_registry`/`generate_mapping`/
  `write_registry_and_mapping` already run, via a new
  `workflow_state.generate_artifacts_declarations(work_item_id, plan_path,
  registry_path, mapping_path)` helper. Default `plan_stage` template: the
  item's own three artifact paths as `protected_paths` (satisfying step 11's
  binding check by construction at creation time), plus
  `workflow-v2-1-core`'s *current* `excluded_paths`/`excluded_prefixes` sets
  inherited verbatim as a starting point — not because they are correct for
  every future item unmodified (`OPUS-R25-004`'s own evidence: this
  repository's real set took five review rounds to converge), but because
  an inherited, previously-reviewed starting point fails closed on any
  genuinely novel path exactly as before, and is strictly safer than an
  empty or hand-authored one. **Stated obligation, not silently assumed
  sufficient**: `/milestone-plan`'s own `SELF_REVIEWING_PLAN` step must
  explicitly confirm the inherited set fits the new item's own plan (most
  process items' plan-stage footprint is a strict subset of
  `workflow-v2-1-core`'s, since they name fewer or no forward-looking
  implementation-phase paths yet — but this is a review-time check, not an
  automated one this revision specifies further).
- **Ordering that breaks the circularity** (the review brief's own
  question, answered directly): `/milestone-plan` step 1 `[2.1]` *declares*
  the three paths (writes them into `WORKFLOW_STATE.json`, no file content
  yet); step 3 `[2.1]` *populates* real content at those paths (registry,
  mapping, and now the artifacts declarations file, all written before the
  bundle exists); step 6 (bundle generation) is the first point anything
  computes a plan-stage fingerprint. No step ever needs the fingerprint to
  produce a path, and no step ever needs a path to already be fingerprintable
  before it is written — the dependency graph is a straight line, not a
  cycle, once "declare" and "populate" are named as two distinct,
  ordered `/milestone-plan` actions instead of left implicit.
- Bundle-directory identity for the *write* path is templated, never
  existence-gated, but has **no creator other than the bundle-generation
  step itself** (corrected, `OPUS-R27-004`, itself corrected `GPT-R29-002`:
  revision 18's claim that `resolve_bundle_dir`'s existing scoped-else-flat
  resolution was "sufficient" on its own was wrong for a different reason
  than "it needs a creator" — that resolution only returns the scoped path
  if it already exists, which would make the write path silently fall back
  to the flat `.ai-review/current` for a genuinely new item; revision 19
  then over-corrected by making `write_manifest_with_verified_identifiers`
  itself a directory *creator*, which `GPT-R29-002` found impossible to
  reconcile with the required-file bind precondition — a `mkdir -p`'d
  directory has no `REVIEW_REQUEST.md`/`PLAN.md`/`DIFF.patch`/`TEST_RESULTS.md`
  to compute `bundle_id` over). The corrected shape keeps only the part
  that was actually right: `write_manifest_with_verified_identifiers`
  **resolves** its own `bundle_dir` internally as
  `.ai-review/<work_item_id>/current` by direct templating — never
  `resolve_bundle_dir`'s existence-gated fallback — so the write path can
  never target the wrong (flat) directory for a new item. It does **not**
  create that directory or anything inside it; the directory and its
  complete required-file content are `prepare-ai-review.sh`'s (or an
  equivalent full bundle-generation step's) sole responsibility, exactly as
  in every revision before 19's write-path-creator addition.
  `resolve_bundle_dir`'s own scoped-else-flat resolution remains
  sufficient, unchanged, for its original read-only/inspection use — the
  bundle directory a work item's bundle lives in is still derived, not
  stored state.

**Fail-closed matrix, corrected and complete** (thirteen conditions, up
from nine — the three revision-17 additions were `-003`'s path-binding,
`-008`'s path grammar, and `-005`'s manifest binding; condition 8's
uniqueness check was relocated and widened per `-007`; revision 18 adds
condition 13 (`OPUS-R26-003`'s `base_commit` cross-check) and splits
condition 12 into its three named outcomes rather than leaving the
"present but unbound" case unhandled (`OPUS-R26-001`); every condition
below has a named, independently exercised test, closing `OPUS-R25-009`'s
original gap and `OPUS-R26-001`'s regression of it):

1. Unknown `work_item_id` → `UnknownWorkItemError`.
2. `work_item_type` other than `"process"` → `PlanStageNotApplicableError`.
3. `plan_path`/`registry_path`/`mapping_path`/`base_commit` null, independently → `MissingPlanStageMetadataError` (extended, `OPUS-R26-003`: `base_commit` joins the other three nullable identity facts checked here, rather than having no fail-closed home of its own).
4. `plan_path`/`registry_path`/`mapping_path` fails the shared path-grammar validator, independently → `InvalidPlanStageMetadataPathError`.
5. No `<work_item_id>-artifacts.json` at the resolved source → `MissingWorkItemArtifactsDeclarationError`.
6. Registry/mapping/artifacts file's own `work_item_id` disagreeing with the resolving key, independently → `RegistryWorkItemIdMismatchError`/`MappingWorkItemIdMismatchError`/`ArtifactsWorkItemIdMismatchError`.
7. `plan_path`/`registry_path`/`mapping_path` not a member of the resolved protected-path set, or the three not pairwise distinct → `PlanStageMetadataNotProtectedError`.
8. A non-null `plan_path`/`registry_path`/`mapping_path` claimed by more than one work item, independently per field → `DuplicateWorkItemArtifactPathError`.
9. Registry/plan-title `plan_revision` disagreement → `PlanRevisionMismatchError` (existing mechanism, now exercised per item).
10. A changed/untracked path outside the resolved work item's own protected/excluded sets → `UnclassifiedPathError` (existing mechanism, now correctly scoped per item).
11. Commit-source resolution at a commit predating the schema-version-2 migration → correctly manifests as condition 3 or 5 above (pre-migration `WORKFLOW_STATE.json` genuinely has no `mapping_path` key; pre-migration `<id>-artifacts.json` genuinely has no `plan_stage` key) — **no new mechanism needed**; this is the deliberate, stated boundary the `OPUS-R25-013` row of the Round 25 disposition table above resolves, not a gap (corrected, `OPUS-R26-007`: this condition and "Backward compatibility" point 1 below previously cross-referenced each other for the substantive reasoning instead of both pointing at the disposition table row where it actually lives).
12. `--write-manifest` targets a bundle directory whose existing `MANIFEST.md` disagrees with the resolved work item — corrected, `OPUS-R26-001`, to name the outcome revision 16/17 left unhandled; corrected again, `OPUS-R27-007`, which found "all **three** disagreeing sub-cases" an overcount against the two actually enumerated (the *three* count belongs to the outcomes in "Manifest/bundle bound to an explicit work item" above — absent/agrees/disagrees — of which exactly two disagree and fail closed here): (12a) present, declares a different `work_item_id`; (12b) present, declares **no** `work_item_id` at all (an unbound/legacy manifest — every manifest in this repository today, before the one-time migration rebinding step runs); both raise `BundleWorkItemMismatchError`, naming the resolved item and either the disagreeing value or "unbound". (An absent `MANIFEST.md` is not a fail-closed case at all — it is the ordinary first-write outcome, described in "Manifest/bundle bound to an explicit work item" above, not part of this matrix.)
13. **(new, `OPUS-R26-003`; enforcement points named, `OPUS-R28-006`)** The resolved work item's own declared `base_commit` disagreeing with the resolved commit content is bound to — checked at **two** named points, both catching the same underlying disagreement: (a) `prepare-ai-review.sh`'s own early, script-level refusal, before any bundle content is generated, comparing against the resolved `BASE_SHA` (never the raw requested form) — a non-zero shell exit with a distinct, greppable message, not a Python exception; and (b) `MANIFEST.md`'s `base_commit` field, at write time inside `write_manifest_with_verified_identifiers`, disagreeing with the resolved work item's own declared `base_commit` (state/registry/CLI-argument/bundle/manifest sources must all agree) → `BundleWorkItemMismatchError` (the same exception condition 12 raises — a manifest whose declared identity, in any bound field, disagrees with the resolved item is one failure mode, not several). (a) is reachable first for any normal invocation; (b) remains the fail-closed backstop for any write path that reaches `write_manifest_with_verified_identifiers` without going through (a) first.

**Backward compatibility, `workflow-v2-1-core` — corrected, mechanism-relative
(`OPUS-R25-001`)**. Revision 16 required
`compute_review_content_id_plan_stage_for_work_item` to reproduce
`2b4d2e3b89c2b8f…` — revision 15's approved digest — "immediately after the
migration." That is impossible by construction: `plan_revision` is a hashed
field, `WORKFLOW_V2_PLAN.md` is protected, and this very revision changes
both, so the reproducible digest the moment revision 17 is approved is
necessarily this revision's own new value, never revision 15's or 16's.
Requiring the old and new digest to be equal was the defect; the corrected
invariant, stated in three parts:

1. **The pre-revision approval remains historically discoverable, unchanged,
   forever**: `WORKFLOW_STATE.json`'s `plan_approval` history is never
   rewritten by this migration — the record documented in
   `plan_approval.user_confirmation` (the `OPUS-R24-*` remediation note) and
   every earlier approval remain exactly as committed, discoverable by the
   same commit-trailer search `D-Commit-Provenance` already specifies. No
   acceptance criterion or test requires recomputing an *old* approval's
   digest from *new* code — the `OPUS-R25-013` row of the Round 25
   disposition table above states why that would be undefined by design,
   not merely unverified (corrected, `OPUS-R26-007`: this point and
   fail-closed matrix condition 11 above previously cross-referenced each
   other rather than both pointing at that table row, where the
   substantive reasoning actually lives).
2. **The migration's own correctness is proven mechanism-relatively, not by
   literal digest**: on the same tree, at the same base commit, *before* the
   `plan_revision` bump and any other content edit, the resolved-from-JSON
   `plan_stage` sets and the pre-migration `PLAN_STAGE_PROTECTED`/
   `PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES` Python
   constants must produce byte-identical digests when compared directly
   (compute both, assert equal, only then retire the constants as live
   defaults). This is strictly stronger against a transcription error than
   pinning a literal: a transcription mistake that happened to reproduce
   `2b4d2e3b…` by coincidence would pass the old criterion and fail this
   one.
3. **`plan_approval` is updated only after binding approval, never by the
   migration itself**: the migration (data + resolver code) lands and its
   own tests (item 2 above) pass first; `WORKFLOW_STATE.json`'s
   `plan_approval.approved_review_content_id` is written exactly once, by
   `/approve-review plan`, against whatever `review_content_id` is current
   at that moment — unchanged mechanism, the same one every prior revision
   bump already used. No acceptance criterion states or implies the old and
   new `approved_review_content_id` must ever be equal.

**Affected commands, corrected** (`OPUS-R25-004`/`-012`; audit corrected
and extended, `OPUS-R28-007`). Three of the five commands the finding
originally named (`/review-plan`, `/record-manual-plan-review`,
`/approve-review plan`) still require no text change — confirmed unchanged
from revision 16's audit for those three. **`/milestone-plan`, `/apply-plan-review`,
and `/prepare-review` all need text changes** — the "no text change" list
was wrong for `/apply-plan-review` under this revision's own
`prepare-ai-review.sh` change (step 8: the work-item id becomes required
for `stage == "plan"`), and `/prepare-review` was never audited at all:

- **`/milestone-plan`** (corrected, see "Creation path" above): step 1
  `[2.1]` gains the three-path declaration, step 3 `[2.1]` gains the
  artifacts-declarations-file write, **and** step 6's own
  `prepare-ai-review.sh` invocation syntax
  (`.claude/commands/milestone-plan.md:84`) changes `[work_item_id]`
  (bracketed, reads as optional) to `<work_item_id>` (required) for the
  plan stage.
- **`/apply-plan-review`** (new to the changed list, `OPUS-R28-007`):
  `.claude/commands/apply-plan-review.md:38`'s step 5 invocation gains the
  same `[work_item_id]` → `<work_item_id>` correction; step 7'.2
  (`.claude/commands/apply-plan-review.md:55`) prints
  `./scripts/prepare-ai-review.sh <base-sha> plan` with **no** work-item id
  at all — under this revision, that exact invocation refuses outright
  (missing test 162's own asserted behaviour) — so it gains the required
  argument too. This is an *implementation* edit owed to the fix session
  that lands this revision's design, not a plan-text edit; it does not
  reopen this revision's own "no `.claude/commands/` file is edited by this
  revision" scope statement (below), which is about this plan-writing
  session's own diff, not the deferred fix session's.
- **`/prepare-review`** (new, unaudited call site found this round,
  `OPUS-R28-007`): `.claude/commands/prepare-review.md:27`'s
  `./scripts/prepare-ai-review.sh <base-sha> <stage> [work-item-id]`
  states, in its own step 3, that the id is **required when `<stage>` is
  `plan`** and remains optional for every other stage — this is the one
  call site where an operator may genuinely have no work item in mind, so
  it gets a stage-conditional statement rather than an unconditional
  required argument.

`scripts/prepare-ai-review.sh` itself (not a `.claude/commands/*.md` file,
but the actual manifest-writing entry point every plan-stage bundle goes
through) gains the `--write-manifest` call described above, plus the
required-argument and `base_commit`-cross-check behavior migration step 8
now specifies. `docs/ai-workflow/MILESTONE_WORKFLOW.md`
(`MILESTONE_WORKFLOW.md:45`) and `docs/ai-workflow/REVIEW_PROTOCOL.md`
(`REVIEW_PROTOCOL.md:11`), which also print bare `plan`-stage invocations,
are covered by acceptance criterion 21 above, not restated here.

**Migration, exact steps** (implementation, deferred to the dedicated fix
session named in "Round 25 finding disposition" — not executed by this plan
revision):

1. Add `mapping_path` to `WORKFLOW_STATE.json`'s per-item schema (`D3`
   below); backfill the three existing entries as revision 16 specified.
   `route_work_item`/`default_work_item` gain the `mapping_path` **and**
   `base_commit` parameters (extended, `OPUS-R26-003`: `base_commit` is
   already a per-item schema field today — `workflow-v2-1-core`'s and
   `v2-1-dry-run`'s entries both carry it, the latter `null` — only its
   writer was missing; no schema addition needed, unlike `mapping_path`)
   (see "Creation path" above). `WORKFLOW_STATE.json`'s `plan_revision`
   field is declared a non-authoritative mirror (`D3` below) and its value
   for `workflow-v2-1-core` is corrected to match the registry JSON's
   whenever the two are found to disagree (this repository's own state,
   right now, is such a case — corrected as part of this same migration
   commit, not deferred).
2. Migrate `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`
   exactly as revision 16 specified (`schema_version` 1 → 2,
   `implementation_stage`/`plan_stage` sections), **plus**: add
   `docs/ai-workflow/registry/workflow-v2-1-core-artifacts.json`'s own
   **concrete, spelled-out path** (corrected, `OPUS-R26-004`: not the
   `<work_item_id>` placeholder revision 17 specified, which the exact-match
   classifier cannot resolve) as an implementation-stage `protected_paths`
   exact-path entry, checked before the `docs/ai-workflow/registry/`
   prefix — closing `OPUS-R25-006`. `generate_artifacts_declarations`
   (step 8 below) emits the same self-referential concrete-path pattern for
   every future item's own file, generalizing by construction rather than
   by a template key.
3. `load_implementation_stage_classification` reads from the
   `implementation_stage` sub-key — unchanged from revision 16.
4. Add `fingerprint.load_plan_stage_classification`,
   `fingerprint.artifacts_path_for_work_item`, and the minimal
   `WORKFLOW_STATE.json` reader `resolve_plan_stage_metadata` needs (all in
   `workflow_fingerprint.py`, per the module-placement correction above).
   `artifacts_path_for_work_item` is shared by both stages (extended,
   `OPUS-R27-002`): the plan-stage resolver (step 5) and
   `approval_review_content_id`'s `stage="implementation"` branch (step 6)
   both call it, rather than the implementation stage keeping its own
   separate literal default.
5. Add `resolve_plan_stage_metadata`,
   `compute_review_content_id_plan_stage_for_work_item`,
   `compute_review_content_id_plan_stage_at_commit_for_work_item`, and the
   fail-closed exception classes for all thirteen matrix conditions (nine
   new classes total, unchanged in count from revision 17 — condition 13
   reuses `BundleWorkItemMismatchError` rather than introducing a tenth:
   `InvalidPlanStageMetadataPathError`, `PlanStageMetadataNotProtectedError`,
   `BundleWorkItemMismatchError`, `PlanRevisionMirrorMismatchError`, plus the
   five already named in revision 16 — `UnknownWorkItemError`,
   `PlanStageNotApplicableError`, `MissingPlanStageMetadataError`,
   `MissingWorkItemArtifactsDeclarationError`,
   `RegistryWorkItemIdMismatchError`/`MappingWorkItemIdMismatchError`/
   `ArtifactsWorkItemIdMismatchError`/`DuplicateWorkItemArtifactPathError`,
   unchanged).
6. Rewire the CLI's two `__main__` call sites (default `--work-item-id` now
   `active_work_item_id`, not a literal, **for the read-only path only —
   `--write-manifest` requires `--work-item-id` explicitly and refuses if
   omitted, corrected `OPUS-R28-010`**; the `base` positional now resolves
   from the requested item's own `base_commit`, required and fails closed
   when `null` — corrected, `OPUS-R26-003`, retiring the second literal
   default revision 17 left standing); `load_plan_revision`'s
   `registry_path`/`plan_path` parameters become required, with no default,
   retiring `DEFAULT_REGISTRY_PATH`/`DEFAULT_PLAN_PATH` as live defaults in
   the same pass as `PLAN_STAGE_*` and `DEFAULT_ARTIFACTS_PATH` below (new,
   `OPUS-R28-011`: these two literals were already named as confirmed
   defects in the `WF8B-S1-001` disposition row above, alongside
   `PLAN_STAGE_PROTECTED` and the `--work-item-id`/`base` argparse defaults,
   but no migration step ever retired them — they remain live parameter
   defaults on `workflow_fingerprint.py:474-475` today, and `__main__:1741`
   calls `load_plan_revision(repo_root)` relying on them; kept only as
   named migration-comparison fixtures if any test needs them, never as a
   live fallback for any caller this migration reaches),
   `write_manifest_with_verified_identifiers` (the `BundleWorkItemMismatchError`
   check for both `work_item_id`, per condition 12's three named outcomes,
   and `base_commit`, per new condition 13 — corrected, `OPUS-R26-001`/
   `-003`), and `approval_review_content_id`'s `stage="plan"` branch
   (dropping `protected`/`excluded_paths`/`excluded_prefixes` **and**
   `plan_revision` — corrected from revision 16, which only dropped the
   first three) to the new entry points. **The `stage="implementation"`
   branch, `approval_is_current`, and `verify_post_approval_manifest_match`
   are rewired in this same step** (corrected, `OPUS-R28-001`; `OPUS-R27-002`
   named `implementing_entry_reachable` instead of
   `verify_post_approval_manifest_match` — `implementing_entry_reachable`
   only ever calls `approval_is_current(..., stage="plan", ...)`, hardcoded,
   and never reaches `approval_review_content_id` with
   `stage="implementation"`, so it needs no `artifacts_path` rewiring at all;
   `verify_post_approval_manifest_match` does reach it and was omitted):
   each of the two actual callers resolves `artifacts_path` via
   `fingerprint.artifacts_path_for_work_item(work_item_id)` and passes it
   explicitly, rather than relying on `artifacts_path`'s
   `DEFAULT_ARTIFACTS_PATH` literal default, which is retired as a live
   default for every caller in this same step (alongside the plan-stage
   literals it already retires), not kept as a fallback for any caller
   this migration reaches. `promote_legacy_work_item` needs no rewiring
   either — it calls `load_implementation_stage_classification` directly
   (`workflow_state.py:2128`), never through `approval_review_content_id`.
7. Correct `workflow-v2-1-core-artifacts.json`'s own `_comment` field and
   `load_implementation_stage_classification`'s docstring (`OPUS-R25-006`);
   rewrite `scripts/workflow_fingerprint.py:361-365`'s "synthetic … borrows
   the real process item's" comment (`OPUS-R25-011`); correct
   `promote_legacy_work_item`'s own docstring, which already (falsely,
   until step 6 above lands) claimed to be "scoped to `work_item_id`'s own
   `artifacts_path`" (`OPUS-R27-002`).
8. Extend `scripts/prepare-ai-review.sh`'s `plan`-stage path with the
   `--write-manifest` call (`OPUS-R25-012`), resolving the work-item id
   **exactly once** and using that single value for both `ROOT_DIR` and
   `--write-manifest` (corrected, `OPUS-R26-002`, replacing revision 17's
   two-rule "`<work-item-id-or-live-active>`" specification) — **the
   work-item id becomes the script's required third argument for `stage ==
   "plan"`, never resolved from the live `active_work_item_id` for this
   stage** (corrected, `OPUS-R27-003`, closing the flat-layout-retirement
   and stranded-step-8a gaps the "resolve once, else live-active" version
   left open; the flat, optional-argument layout is unchanged for every
   other stage), **and the resolved item's own declared `base_commit` is
   cross-checked, via `fingerprint.resolve_plan_stage_metadata`, against the
   resolved `BASE_SHA` — never the raw `<base-sha>` argument as typed —
   before any bundle content is generated, refusing with a distinct,
   greppable message on disagreement and naming both the requested and
   resolved forms plus the declared value** (`OPUS-R27-003`, corrected
   `OPUS-R28-006`, applying fail-closed matrix condition 13 as an early
   script-level refusal); extend `/milestone-plan` step 1 `[2.1]`/step 3
   `[2.1]` per "Creation path" above, including the new `base_commit`
   parameter. **Also, in this same step** (folded in, `OPUS-R28-007`):
   `.claude/commands/milestone-plan.md:84` and
   `.claude/commands/apply-plan-review.md:38` change their
   `prepare-ai-review.sh <base-sha> plan [work_item_id]` invocation syntax
   to `<work_item_id>` (required); `apply-plan-review.md:55`'s step 7'.2
   gains the argument outright; `.claude/commands/prepare-review.md:27`
   states the id is required when `<stage>` is `plan`, optional otherwise —
   see "Affected commands, corrected" above for the full per-file audit.
8a. **One-time relocation and rebinding for the existing, currently-flat,
    currently-unbound bundle directory** (new, `OPUS-R26-001`; retimed and
    extended, `OPUS-R27-003`; **renumbered from `6a` to `8a` and rewritten,
    `OPUS-R28-004`/`-005`, resolving five independent gaps the `6a` numbering
    and text left open** — its own prose already said "runs after step 8
    lands," contradicting its position between steps 6 and 7; the relocation
    was unversioned, non-atomic, had no collision rule, no resumability, and
    under-enumerated `.ai-review/`'s actual contents; and relocating
    `.ai-review/feedback` conflicted with step 8's own flat-retention
    carve-out for non-plan stages). Runs once, immediately after step 8
    lands, because step 8 is what makes `workflow-v2-1-core`'s own
    plan-stage `ROOT_DIR` resolve to `.ai-review/workflow-v2-1-core/current`
    instead of the flat `.ai-review/current`.
    - **Every entry under `.ai-review/` today, named explicitly, with its
      disposition** (closing `OPUS-R28-004`'s under-enumeration gap — the
      six entries this repository's own `.ai-review/` holds as of this
      revision): `current/` — **moves**, to
      `.ai-review/workflow-v2-1-core/current`; `feedback/` — **stays flat**
      (corrected, `OPUS-R28-005`: `feedback/` is stage-agnostic —
      `resolve_feedback_dir` takes no stage argument and step 8 retains the
      flat layout, unchanged, for every stage but `plan`'s bundle directory;
      relocating it would split the implementation/post-fix/functional-review
      stages' feedback from their own still-flat bundles the moment this step
      runs. This is the finding's option (b): the smaller edit, since it
      reopens neither step 8's carve-out nor `resolve_feedback_dir`'s
      contract. `resolve_feedback_dir`'s scoped branch remains correctly
      unreachable until a future revision scopes every stage's bundle
      directory, which this revision does not attempt); `review-bundle.tar.gz`
      — **moves**, to `.ai-review/workflow-v2-1-core/review-bundle.tar.gz`
      (it is written by `prepare-ai-review.sh`'s own `$ROOT_DIR/review-bundle.tar.gz`
      line, so it follows `current/`'s relocation by construction once
      `ROOT_DIR` itself resolves scoped — no separate move command needed);
      `source/` — **stays**, unmoved (hardcoded as `.ai-review/source/*` in
      `prepare-ai-review.sh`, named by `WFR-16`; moving it would require a
      script change this revision does not make); `runtime/` — **stays**
      (work-item-keyed internally, e.g. `WORKTREE_IDENTITY.json`/
      `DRY_RUN_RESUME.json`, never flat-bundle content); any other
      work-item-named directory already present (e.g. this repository's own
      ad hoc `wf8b-fingerprint-remediation/`, created by a `/prepare-review`
      invocation outside the milestone-workflow gates) — **stays**, unmoved
      and untouched by this migration, which relocates only the one flat,
      unbound `current/`+`review-bundle.tar.gz` pair this step exists for.
    - **Collision-safe, fail-closed-after-interruption relocation** (label
      corrected, `GPT-R29-005`: "atomic" and "resumable" overstated the
      actual guarantee below — two separate `mv -n` operations are neither
      atomic as a pair, nor resumable in the sense of an automated retry
      completing the job; what the procedure actually guarantees is that an
      interruption at any point leaves a state the next run's own
      preconditions refuse to silently paper over, requiring the named
      manual diagnosis this same bullet already describes, never an
      automatic resume) (closing `OPUS-R28-004` items 3-4): before moving anything, verify the
      destination (`.ai-review/workflow-v2-1-core/`) does not already exist
      as a non-empty directory — a non-empty destination is a hard stop
      requiring operator resolution, naming both paths, never a silent
      nest-under-itself (`mv`'s default behavior when the destination
      exists). `mkdir -p .ai-review/workflow-v2-1-core`, then move `current/`
      and `review-bundle.tar.gz` into it with `mv -n` semantics (refuse
      rather than overwrite if a same-named entry somehow already exists at
      the destination). Immediately after the move, a verification sub-step
      — run before the rebinding write below, not folded into it — asserts
      the destination contains the same file set the source had (byte-count
      or path-set comparison, not merely "exists") and that the source
      directory is now absent; if either check fails, the migration stops
      before the rebinding write, leaving the operator a diagnosable partial
      state to resolve by hand rather than a bound-but-corrupt bundle. This
      is what makes an interrupted move fail closed: per the first-write
      rule (`OPUS-R28-008` below), an unbound directory with no `MANIFEST.md`
      would otherwise bind on the very next write, including a partial one.
    - **Rebinding**, unchanged in mechanism from the prior text:
      `write_manifest_with_verified_identifiers` gains a separately-named,
      explicit rebinding argument (never the default write path's behavior)
      used exactly once, here, at the relocated path, invoked with
      `--work-item-id workflow-v2-1-core`, binding the relocated, verified
      `MANIFEST.md` — which, like every manifest in this repository today,
      still declares no `work_item_id`, and has in fact always belonged to
      `workflow-v2-1-core` — to its true owner before condition 12's
      default-refuse-on-unbound rule would otherwise block every subsequent
      write to it.
    - Both `MILESTONE_WORKFLOW.md`'s and `REVIEW_PROTOCOL.md`'s printed
      paths/invocation examples are updated in the same commit (mechanical,
      not a design change) to show the scoped `current/`/`review-bundle.tar.gz`
      paths and the still-flat `feedback/` path, so the two documents do not
      themselves start claiming a layout this step didn't actually produce.
      No other bundle directory exists yet to need this step; a future
      migration that finds another pre-existing unbound directory repeats
      this same one-time verify-move-verify-rebind procedure for it, naming
      the directory and its true owner explicitly, never inferring one.
9. Extend `scripts/workflow_test_harness.py` (`OPUS-R25-014`):
   `write_plan_docs` also emits a per-item `<id>-artifacts.json` matching
   its existing five-path fixture shape; `plan_stage_protected_paths` is
   retired in favour of it; `workflow_test_harness_test.py`'s
   "`workflow-v2-1-core` needs no override" case is restated in resolver
   terms (the harness's existing three-fixed-plus-two-templated fixture
   shape is the prior art this resolver design already follows).
10. Run the full existing regression suite plus the new tests — **every
    Missing-tests item owed to `WF8b`, items 141-166** (corrected,
    `OPUS-R28-009`: revision 19 left this range at "141-163," unchanged
    since round 26, while adding items 164 and 165 — the tests for
    `OPUS-R27-002`/`OPUS-R28-001`'s and `OPUS-R27-003`/`OPUS-R28-004`/`-006`'s
    own corrections — with no green-suite gate requiring either to run;
    **item 166 joins this range this revision** (corrected, `GPT-R29-003`,
    reversing `OPUS-R28-003`'s ownership split: item 166 was excluded on
    the reasoning that a criterion and its sole automated-regression
    verification need not land at the same checkpoint when the criterion
    itself — item 166's data half — is independently satisfied by this
    revision's own one-time sync; but `WF8a-ii`, the checkpoint the
    verification half was assigned to, is already `COMPLETE`, and
    checkpoint completion is never revoked to reopen it — see the
    "Backward compatibility" discussion above's own precedent for `WF4a-i`.
    That left a mandatory regression test with no reachable owner: the
    remediation implementation this checkpoint-continuation work produces
    could pass every other required test while never adding item 166 at
    all, exactly the gap `GPT-R29-003`'s failure scenario describes.
    Reassigned to this same continued `WF8b` scope instead of reopening
    `WF8a-ii` — the smaller of the finding's two offered options, and the
    one that matches the fact that this revision's own `OPUS-R28-003` fix
    is what edited all 52 mapping rows the conformance test verifies in the
    first place) — all green, with `workflow-v2-1-core`'s mechanism-relative
    durability guard (backward-compatibility criterion 2 above) re-verified
    immediately before and immediately after every step.
11. Only then: implementation is complete, and this checkpoint-continuation
    work (tracked as `WF8b`'s own continued scope, no new checkpoint ID)
    enters its own `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`/
    `AWAITING_TECHNICAL_APPROVAL` cycle — the ordinary implementation-review
    gate every checkpoint's code already goes through. (Corrected,
    `OPUS-R25-015`: revision 16's migration step 8 described a second
    `/approve-review plan` round happening *after* implementation, directly
    contradicting the "Restart discipline for S1" paragraph's own statement
    that `/approve-review plan` runs immediately on **this** bundle's
    `APPROVE`. There is exactly one plan-approval round for this design —
    the one this bundle itself is seeking — and one implementation-review
    round after the code lands; deleted the contradictory step rather than
    reword it, since the restart-discipline paragraph already states the
    correct sequence in full.)

**Rollback.** Unchanged from revision 16: before the plan-approval commit,
this is an in-progress, unapproved edit like any other — reverting it is a
plain revert. After approval, no dedicated plan-revision-rollback mechanism
exists (none has ever been needed across 16 prior revisions); if one is
ever needed, it is a fresh plan revision that reverts this one's content,
through the same review-and-approval cycle again. `D-Self-Governance`'s
`Workflow-Rollback` trailer governs *activation* rollback (`"2.1"` →
`"1"`), a different concern, unaffected by this section.

**Acceptance criteria, exact — corrected and expanded:**

1. **(corrected, `OPUS-R25-001`)** On the same tree, at the same base
   commit, before the `plan_revision` bump or any other content edit, the
   migrated `<work_item_id>-artifacts.json`'s `plan_stage` section, loaded
   via `load_plan_stage_classification`, produces byte-identical
   `protected_paths`/`excluded_paths`/`excluded_prefixes` to
   `PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES`,
   verified by direct comparison, not by matching a hardcoded digest
   literal.
2. **(corrected, `OPUS-R25-002`)** After migration, `WORKFLOW_STATE.json`'s
   `plan_revision` for every work item with a non-null `registry_path`
   equals that registry's own `plan_revision` field exactly;
   `approval_review_content_id` no longer accepts a `plan_revision`
   parameter for `stage="plan"`.
3. **(new, `OPUS-R25-003`)** A second synthetic work item's `plan_path`,
   `registry_path`, and `mapping_path` are each a member of its own
   resolved protected-path set, and the three are pairwise distinct;
   mutating which file `plan_path` names (while leaving the protected set
   unchanged) raises `PlanStageMetadataNotProtectedError` rather than
   silently fingerprinting the wrong file. **(extended, `OPUS-R26-003`)**
   The same second work item's `base_commit` is independently resolved,
   non-null, and distinct from `workflow-v2-1-core`'s; a `null` value
   raises `MissingPlanStageMetadataError` rather than falling back to any
   other item's value.
4. **(new, `OPUS-R25-004`; corrected, `OPUS-R28-002`)** `/milestone-plan
   v2-1-dry-run` — naming, specifically, the **pre-existing non-terminal
   entry** case: an id already present in `work_items` with `plan_path`
   set but `registry_path`/`mapping_path`/`base_commit` still `null`,
   exactly `v2-1-dry-run`'s own recorded state, which takes
   `route_work_item`'s resume branch, not its creation branch — run end to
   end through step 6, succeeds in generating a plan-stage bundle with no
   `MissingPlanStageMetadataError`/`MissingWorkItemArtifactsDeclarationError`.
   A separate, freshly-created process work item (creation branch) is a
   distinct, independently-required case, not a substitute for this one —
   the two branches write the four declaration facts through different code
   paths and a fresh-item pass alone does not exercise the resume branch's
   new per-field write-or-conflict rule. **(extended, `OPUS-R26-003`)** Both
   runs also resolve a real, non-null `base_commit` for their respective
   item, with no `MissingPlanStageMetadataError` raised for that field
   either. **(extended, `OPUS-R28-002`)** Re-running the same
   `/milestone-plan v2-1-dry-run` step 1 a second time with identical
   derived facts is a no-op (idempotent); supplying a disagreeing value for
   any already-non-null field raises `WorkItemDeclarationFactConflictError`,
   naming the field and both values.
5. **(new, `OPUS-R25-005`/`-012`)** `MANIFEST.md` records `work_item_id`,
   `plan_revision`, `base_commit`, and `work_item_type` **(extended,
   `OPUS-R26-003`, criterion 7 below)**; `--write-manifest` against a
   bundle directory whose existing `MANIFEST.md` disagrees with the
   resolved work item — whether it names a different `work_item_id`, names
   none at all, or names a different `base_commit` — refuses with
   `BundleWorkItemMismatchError`, naming the disagreement **(corrected,
   `OPUS-R26-001`/`-003`: the "already bound to a different work item"
   phrasing revision 17 used covered only one of these three cases)**;
   `prepare-ai-review.sh`'s `plan`-stage invocation writes a `MANIFEST.md`
   with no separate manual CLI step, resolving the work-item id **exactly
   once** for both the bundle directory and the manifest **(corrected,
   `OPUS-R26-002`)**.
6. A second synthetic work item computes a `review_content_id` distinct
   from `workflow-v2-1-core`'s at the same base commit, whose manifest
   lists only its own three files.
7. Mutating either fixture's own protected file changes only that
   fixture's `review_content_id`; mutating the other's, or any
   excluded/untouched path, changes neither.
8. Every condition in the thirteen-condition fail-closed matrix above is
   independently exercised and independently distinguishable by exception
   type and message **(corrected, `OPUS-R26-001`/`-003`: twelve → thirteen,
   condition 12 split into its three named outcomes)**.
9. The CLI, invoked with `--work-item-id <second-item>`, prints that
   item's own `plan_revision`/`protected_paths`/`review_content_id` — never
   `workflow-v2-1-core`'s; **for the read-only inspection path**, omitting
   the flag resolves to live `active_work_item_id`, never a hardcoded
   literal. **(extended, `OPUS-R26-003`)** The same invocation, with no
   `base` argument, computes against that item's own resolved
   `base_commit` — never `workflow-v2-1-core`'s base commit, and never any
   other literal. **(extended, `OPUS-R28-010`)** `--write-manifest` with
   `--work-item-id` omitted refuses outright — a usage error, before
   creating or writing anything — never silently resolving to the live
   `active_work_item_id` and creating/binding that item's directory; the
   live-default behavior above is scoped to the read-only path only.
10. The six existing test files this finding names, plus
    `workflow_test_harness_test.py`, all pass with no existing assertion
    weakened or deleted to make this land; `plan_stage_protected_paths` is
    retired without loss of coverage.
11. S1, re-attempted from the beginning in a fresh session after this
    revision and its implementation are both approved, produces a
    `v2-1-dry-run`-specific `review_content_id`/manifest/bundle distinct
    from `workflow-v2-1-core`'s, and the false-positive scenario the
    finding describes no longer reproduces.

**Acceptance criteria, revision 18 additions (`OPUS-R26-*`, resolving the
review's own seven required-criteria list in full — each restates or
cross-references one of criteria 1-11 above rather than introducing
independent scope, so both numbering schemes stay traceable to the same
design):**

12. **(new, `OPUS-R26-001`)** Fail-closed matrix condition 12 has a stated,
    independently tested outcome for an existing `MANIFEST.md` present but
    declaring no `work_item_id` (refuses, naming "unbound" — never treated
    as absent), and migration step 6a names the one-time rebinding for
    `.ai-review/current` to `workflow-v2-1-core` explicitly.
13. **(new, `OPUS-R26-002`; corrected, `OPUS-R27-003`/`OPUS-R28-006`)**
    `prepare-ai-review.sh` resolves exactly one work-item id and uses it for
    both the bundle directory and `--write-manifest` — stated and
    implemented as one rule, never an "or" between an explicit argument and
    the live active item. **For `stage == "plan"`, that one id is a
    required argument, never resolved from the live `active_work_item_id`**;
    the resolved item's own `base_commit`, read via
    `fingerprint.resolve_plan_stage_metadata`, is cross-checked against the
    resolved `BASE_SHA` (not the raw `<base-sha>` argument, so an
    abbreviated SHA or symbolic ref still passes when correct) before any
    bundle content is generated, refusing with a distinct exit and message
    naming both forms; the flat layout remains reachable, unchanged, only
    for every other stage.
14. **(new, `OPUS-R26-003`; extended, `OPUS-R27-002`, `OPUS-R28-011`)** No
    hardcoded literal naming one work item's identity fact remains
    reachable by a computation for a different item — explicitly including
    the `base` positional's former default, `approval_review_content_id`'s
    `artifacts_path` default for `stage="implementation"`, **and
    `load_plan_revision`'s `DEFAULT_REGISTRY_PATH`/`DEFAULT_PLAN_PATH`
    parameter defaults** (new, `OPUS-R28-011`: named as confirmed defects
    since the original `WF8B-S1-001` disposition row alongside
    `PLAN_STAGE_PROTECTED` and the CLI argparse defaults, but never
    actually retired by any migration step until this one) — and
    `base_commit`'s writer (`route_work_item`/`default_work_item`) is
    brought into scope, reconciling criteria 4, 5, and 11's own requirement
    that a second item produce a real, reproducible bundle.
15. **(new, `OPUS-R26-004`)** The artifacts-file approval binding is
    specified as a concrete, per-item self-referential path the exact-match
    classifier can actually match, emitted by the default template
    (`generate_artifacts_declarations`) — never a `<work_item_id>`
    placeholder.
16. **(new, `OPUS-R26-005`)** Missing-test items 145, 157, and 159 are
    mapped to requirements in the traceability table's evidence columns
    (WFR-50, WFR-49, WFR-47 respectively).
17. **(new, `OPUS-R26-006`/`-005`)** Items 161-163 are added; item 146's
    stated sub-case count is corrected to seventeen and sub-case group
    (4a-c) is disambiguated as three fields; items 152 and 153 gain the
    sub-cases named above.
18. **(new, `OPUS-R26-003`)** `MANIFEST.md` records `work_item_type`
    alongside `work_item_id`/`plan_revision`/`base_commit` — it is exactly
    as identity-bearing a hashed projection field
    (`workflow_fingerprint.py:840`) as the other three, and its omission
    was a usability gap the review found rather than a deliberate scoping
    decision.

**Acceptance criteria, revision 19 additions (`OPUS-R27-*`):**

19. **(new, `OPUS-R27-001`; extended to all 52 rows, `OPUS-R28-003`)**
    `workflow-v2-1-core-mapping.json`'s **every** `WFR-01`-`WFR-52`
    `description` value matches its own table row (backtick markup, `**`
    bold markup, and round-citation parentheticals stripped, em dash
    normalized to `--`) — not only the six `WFR-47`-`WFR-52` rows revision
    19 originally synced; 15 further rows were found diverged this round
    (some substantively, not cosmetically — `WFR-24`'s two versions stated
    opposite properties) and are corrected in this same revision. The
    Checkpoint column is explicitly excluded from this criterion (see the
    Requirements traceability intro paragraph's `OPUS-R28-003` note): it may
    cite design-doc sections that `checkpoint_ids` cannot represent, so
    criterion and test both scope to `description` only. A grep-based
    conformance test (item 166, reassigned from `WF8a-ii` to this same
    `WF8b` remediation, `GPT-R29-003` — `WF8a-ii` is already `COMPLETE` and
    its completion is not reopened to give item 166 a home; see migration
    step 10's corrected range) enforces this going forward, and the two
    false "no JSON edit needed" claims (`OPUS-R26-005` disposition row;
    revision-18 self-review notes) are corrected in place, not silently
    superseded.
20. **(new, `OPUS-R27-002`; caller list corrected, `OPUS-R28-001`)**
    `approval_review_content_id`'s `artifacts_path` parameter for
    `stage="implementation"` — and every caller that actually reaches it
    (`approval_is_current`, `verify_post_approval_manifest_match` — not
    `implementing_entry_reachable`, which only calls
    `approval_is_current(..., stage="plan", ...)` and never needs
    `artifacts_path`; `promote_legacy_work_item` calls
    `load_implementation_stage_classification` directly and is likewise
    unaffected) — resolves per work item via
    `fingerprint.artifacts_path_for_work_item(work_item_id)`, never
    `DEFAULT_ARTIFACTS_PATH`; a second work item's own
    `implementation_stage.protected_paths` (including its self-referential
    entry) is durability-checked against, and stales only, that item's own
    `technical_approval`.
21. **(new, `OPUS-R27-003`; corrected, `OPUS-R28-004`/`-005`)**
    `prepare-ai-review.sh`'s `stage == "plan"` path requires an explicit
    work-item-id argument (never resolves it from `active_work_item_id`),
    and refuses before generating bundle content when the resolved item's
    declared `base_commit` disagrees with the `<base-sha>` argument, naming
    both — bundle content, directory, and manifest are all bound to the one
    same resolved value; migration step 8a physically relocates only
    `.ai-review/current` and `.ai-review/review-bundle.tar.gz` to
    `workflow-v2-1-core`'s own scoped paths before rebinding — **not**
    `.ai-review/feedback`, which stays flat (corrected, `OPUS-R28-005`: it
    is stage-agnostic and step 8 retains the flat layout for every
    non-`plan` stage, so relocating it would split feedback from the still-flat
    bundles those stages generate) — with a non-empty-destination hard stop,
    a post-move file-set verification before rebinding, and all six
    `.ai-review/` entries' dispositions named explicitly (`source/`,
    `runtime/`, and any other work-item-scoped directory stay; `feedback/`
    stays; `current/` and `review-bundle.tar.gz` move) — closing
    `OPUS-R28-004`'s atomicity/collision/resumability/enumeration gaps;
    `MILESTONE_WORKFLOW.md`/`REVIEW_PROTOCOL.md` are updated to print the
    relocated `current/`/`review-bundle.tar.gz` paths, the still-flat
    `feedback/` path, and the required argument; **and** (new,
    `OPUS-R28-007`) `.claude/commands/milestone-plan.md`,
    `.claude/commands/apply-plan-review.md`, and
    `.claude/commands/prepare-review.md` are updated per the "Affected
    commands, corrected" audit above — the first two to `<work_item_id>`
    (required) for the plan stage, the third to state the id is required
    only when `<stage>` is `plan`.
22. **(new, `OPUS-R27-004`; extended, `OPUS-R28-008`; corrected,
    `GPT-R29-002`)** `write_manifest_with_verified_identifiers` resolves
    `bundle_dir` internally from `work_item_id` rather than trusting a
    caller-supplied path (never silently resolving into the flat
    `.ai-review/current`), but is **not** a bundle-directory or
    bundle-content creator — that remains `prepare-ai-review.sh`'s (or an
    equivalent full-generation step's) sole responsibility, unchanged from
    every revision before 19. **The bind precondition is one check, not
    two** (corrected, `GPT-R29-002`, retracting the "just created by this
    same call" disjunct, which specified an outcome — binding a directory
    with no required files in it — the manifest algorithm cannot actually
    produce, since `compute_bundle_id` hashes those same required files): a
    standalone `--write-manifest --work-item-id <new-item>` invocation
    against a resolved directory that does **not** already contain the
    complete required generation file set refuses with
    `MissingRequiredBundleFileError`, naming the missing file — whether
    that directory is entirely absent (every required file trivially
    missing) or exists but incomplete, e.g. left behind by an interrupted
    prior generation run (some required files missing) — both the same
    named exception, never a distinct "creates it" outcome and never a
    partial bundle bound as authoritative. The **only** binding outcome is
    a resolved directory that already contains the complete required file
    set and has no `MANIFEST.md` yet — produced by `prepare-ai-review.sh`
    (or an equivalent full-generation step) strictly before this
    invocation, never by this invocation itself.
23. **(new, `OPUS-R27-005`)** Missing test 162 asserts, without an
    "either/or," that `scripts/prepare-ai-review.sh <base> plan` with no
    third argument refuses outright for the plan stage.
24. **(new, `OPUS-R27-006`)** `WFR-48`'s evidence column names a test for
    each of the thirteen fail-closed matrix conditions, including 11
    (item 145), 12 (items 152, 161), and 13 (item 163) — not only
    conditions 1-10.
25. **(new, `OPUS-R27-007`/`-008`/`-009`; extended, `OPUS-R28-012`)**
    Fail-closed matrix condition 12's stated sub-case count matches its own
    enumeration (two, not three); the "Restart discipline for S1" paragraph
    exists exactly once, naming this revision's own current transition; the
    `OPUS-R25-002` resolution evidence, the Round 26 scope-discipline
    paragraph, the Requirements traceability intro paragraph, and
    `REVIEW_REQUEST.md` all name the same set of changed requirement rows
    and `WORKFLOW_STATE.json` fields as the actual diff. **(extended,
    `OPUS-R28-012`)** No self-review note states a per-revision fact (what a
    revision changed, what it did or did not implement, what invariant held
    across it) using the bare phrase "this revision" outside a context that
    unambiguously names which revision it means — either a `**Revision N's
    own notes**` block heading or explicit in-sentence naming; a fact true
    of every revision alike (e.g. the standing plan/implementation
    separation) is stated once, as a standing invariant, not re-asserted
    with "this revision" at every bump.

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
generalization — deriving the *protected set itself* from validated
work-item metadata rather than the hardcoded constant this milestone's own
plan stage used through revision 15 — is now specified in full in
`D-Fingerprint-Generalization`, resolving `WF8B-S1-001`; the corresponding
per-work-item artifacts-declaration file lives at the same
`docs/ai-workflow/registry/<work_item_id>-artifacts.json` path this
section's naming pattern already established for the implementation-stage
declarations, now carrying a sibling `plan_stage` section too).

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
- **`mapping_path`** (new field, resolves `WF8B-S1-001`, full detail in
  `D-Fingerprint-Generalization`/D3): the immutable mapping file's path is
  now a stored per-item `WORKFLOW_STATE.json` field, mirroring
  `registry_path` field-for-field, including its nullability — a work item
  has a non-null `mapping_path` if and only if it has a non-null
  `registry_path` (a work item without a checkpoint registry has no
  requirement mapping either). Naming pattern unchanged:
  `docs/ai-workflow/requirements/<work_item_id>-mapping.json`.

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
  mapping_path, governing_workflow_version, phase, plan_revision, implementation_revision,
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
  `current_head_commit` (always derived live). **`plan_revision`, authority
  clarified (revision 17, resolves `OPUS-R25-002`)**: this field is a
  non-authoritative display mirror of the registry JSON's own
  `plan_revision` (at `registry_path`), never an independent source — the
  plan-stage fingerprint functions read `plan_revision` from the registry
  alone (`D-Fingerprint-Generalization`) and no longer accept it as a
  caller-supplied parameter. The mirror is kept (cheap to read for
  reporting/narrative purposes) and validated to agree with the registry
  whenever `registry_path` is non-null, rather than removed outright,
  since removing it would be a larger, unrelated schema change this
  revision does not need to make.
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
`/record-manual-plan-review`, or hand-edited. **New (revision 16),
resolves `WF8B-S1-001`, corrected this revision**: `mapping_path` and
`registry_path` must agree on nullability — both non-null, or both null;
never one without the other (revision 16's own wording of this rule stated
the inverse condition by mistake — "`mapping_path` non-null when
`registry_path` is null" — caught and fixed while extending this paragraph
for `OPUS-R25-*`, not itself a reviewer finding). **New (revision 17,
resolves `OPUS-R25-002`)**: `work_item["plan_revision"]` disagreeing with
its own `registry_path`'s registry JSON `plan_revision` field, whenever
`registry_path` is non-null (`PlanRevisionMirrorMismatchError` —
`WORKFLOW_STATE.json`'s `plan_revision` is a non-authoritative display
mirror, per `D-Fingerprint-Generalization`; the registry JSON alone is
authoritative). **New (revision 17, resolves `OPUS-R25-007`, widened from
revision 16's triple-equality wording)**: any non-null `plan_path`,
`registry_path`, or `mapping_path` claimed by more than one `work_items`
entry, checked independently per field rather than only as an identical
three-field triple (`DuplicateWorkItemArtifactPathError`) — this is the
write-time counterpart of the same check `resolve_plan_stage_metadata`
itself now runs on the read path (`D-Fingerprint-Generalization`
resolution step 9); the two are deliberately redundant, since a
hand-edited or half-written state file that never passed through a state
writer bypasses validator-only enforcement entirely.

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

### D-Scoped-Remediation-Acceptance — non-terminal acceptance path for continued-scope remediation (new, resolves `WF8B-002`; hardened, resolves `GPT-R36-001`/`-002`/`-003`, `GPT-R37-001`/`-002`/`-003`/`-004`, `GPT-R38-001`/`-002`/`-003`, `GPT-R39-001`/`-002`, `GPT-R40-001`/`-002`)

See "WF8b finding disposition (revision 21 → 22)" above for the full
critical evaluation and rationale (including why this design deliberately
does not add the phase the originating finding proposed), "WF8b finding
disposition (revision 22 → 23)" above for the three corrections external
plan review found necessary before this design was safe to implement,
"WF8b finding disposition (revision 23 → 24)" above for the further four
corrections that same review round found still unsafe, "WF8b finding
disposition (revision 24 → 25)" above for the two blocking and one
important correction found after that, "WF8b finding disposition
(revision 25 → 26)" above for the two blocking trailer/replay-comparison
corrections found after that, and "WF8b finding disposition (revision
26 → 27)" above for the one blocking and one important
evidence-binding correction found after that. This section is the
authoritative, standalone spec, revisions 23, 24, 25, 26, and 27's
corrections folded directly in rather than left as separate patches to
read alongside it.

**No new phase.** `AWAITING_FUNCTIONAL_REVIEW` continues to be entered
unconditionally by `apply_technical_approval` for every implementation
round, terminal or not — that function is untouched by this decision.
Terminal-vs-non-terminal is decided at acceptance time, not approval time,
by recomputing `select_next_checkpoint(work_item, registry)` fresh inside
each accept command's own entry guard.

**New helper**, `registry_completion_status(work_item, registry) ->
(is_terminal, outstanding_checkpoint_id)` — the sole place this
terminal/non-terminal question is answered; every other function below
consumes its result rather than re-deriving it. `NoCheckpointReadyError`
gains a structured `checkpoint_id` attribute so this helper never parses
its own exception's message text.

**Two new gate functions**, mirroring `approval_gate_reachable`/
`technical_approval_gate_reachable`'s existing non-circular pattern
(D-States): `milestone_complete_gate_reachable(*, phase, is_terminal)` —
`True` iff `is_terminal` and `phase in ("AWAITING_FUNCTIONAL_REVIEW",
"AWAITING_USER_ACCEPTANCE")` (the latter included for forward
compatibility only — no function in this codebase has ever written it,
confirmed by grep, a pre-existing gap this decision does not attempt to
close); `scoped_remediation_gate_reachable(*, phase, is_terminal)` —
`True` iff `phase == "AWAITING_FUNCTIONAL_REVIEW"` and `not is_terminal`.

**`complete_work_item` gains a `repo_root` parameter and loses its
`registry` parameter entirely, closing the trust gap `GPT-R37-001` found**
(revision 24, correcting revision 23's `GPT-R36-001` fix: a fail-closed
check on a *caller-supplied* dict still trusts that dict's own claimed
`work_item_id` — a direct caller can fabricate an in-memory registry
declaring the right `work_item_id` with every checkpoint marked complete,
and nothing in the revision-23 contract could tell that fabrication apart
from the real file. The fix is not a stricter check on the argument; it is
removing the argument). `complete_work_item(state, work_item_id, now, *,
repo_root: Path)` now resolves its own authority, reusing exactly the same
loader `D3`'s whole-state mirror check already established (`GPT-R32-001`/
`GPT-R33-002`/`-004`, `fingerprint._validate_plan_stage_metadata_path` +
read + `json.loads` + `work_item_id`-field check) rather than inventing a
second one:

1. `work_item["registry_path"]` is `None` (a registry-less item, e.g. the
   legacy `milestone-8` shape) — no load is attempted; unchanged,
   vacuously terminal.
2. `registry_path` is not `None`. Resolve it via the shared safe-path/
   tracked-file validator; read and `json.loads` it. A failure at any of
   safe-path resolution, existence/readability, or JSON-object parsing
   raises `RegistryCoverageError`, naming the work item and its declared
   `registry_path` and the specific failure — the exact same failure modes
   `D3`'s mirror check already fails closed on, now also enforced at the
   one place that actually gates `MILESTONE_COMPLETE`.
3. The loaded registry's own `work_item_id` field disagrees with the
   `work_item_id` being completed — the same `RegistryCoverageError`,
   naming both the expected and the foreign registry's declared id. Since
   the registry was loaded from the item's own declared `registry_path`,
   not supplied by the caller, this case can now only mean the on-disk
   file itself is misconfigured or cross-linked — never a caller-side
   substitution, which is no longer representable at all.

Only when none of the above fire does it compute `is_terminal` via
`registry_completion_status` and raise `IncompleteOwnCheckpointsError`,
naming the outstanding checkpoint, when `not is_terminal` — independent
of, and in addition to, the existing `incomplete_children` check. This is
the direct fix for both the original finding's root cause (nothing
previously checked an item's own registry/checkpoint completeness before
`MILESTONE_COMPLETE`) and `GPT-R37-001`'s root cause (nothing today can
prove a caller-supplied dict *is* the authoritative file, so the helper
must stop accepting one): the loaded registry is now provably the item's
own declared file, not merely a dict that claims to be.

**`/accept-milestone`** gains one new step: resolve the item's own
`registry`/`is_terminal` (loaded the same way, for its own early,
user-facing refusal message — this pre-flight load is advisory only, not
a security boundary, since `complete_work_item` itself re-resolves and
re-validates the authoritative file independently before ever writing
`MILESTONE_COMPLETE`) and refuse via `milestone_complete_gate_reachable`
before any other action, naming the actual phase and the outstanding
checkpoint on refusal; its later call to `complete_work_item` passes
`repo_root`, not a `registry` argument. Every other step of the command is
unchanged.

**`/prepare-functional-review` gains a required checklist-evidence
commit** (revision 25, `GPT-R38-001`, closing the gap that made
`/accept-scoped-remediation`'s pre-commit evidence guard permanently
unsatisfiable: nothing in the documented flow ever committed the checklist
that guard requires clean; trailer value shape corrected revision 26,
`GPT-R39-001`, below). After writing the checklist into
`functional_checklist_path` (`docs/ACTIVE_MILESTONE.md`, that command's
existing step 3, unchanged) and before stopping at the user gate (step
5), the command creates one dedicated, metadata-only commit — containing
only that file's change, no production/test changes — carrying a new
`Workflow-Functional-Checklist: <work_item_id>/<implementation_revision>/<checklist_blob>`
trailer plus the ordinary `Workflow-Work-Item` trailer
(`implementation_revision` read live from `work_item['implementation_revision']`
at generation time; `checklist_blob` the full `git hash-object` blob SHA of
the checklist content about to be committed).

**Content-scoped trailer value, not a bare round value** (revision 26,
`GPT-R39-001`, correcting revision 25's design: a bare
`<work_item_id>/<implementation_revision>` value made a legitimate
same-round content revision indistinguishable, at the Git level, from the
genuine duplicate-commit ambiguity `D-Commit-Provenance`'s generic
exactly-one-match contract exists to catch — the plan could not
simultaneously claim "a changed checklist produces a new commit for this
round" and "discovery mirrors a contract that refuses more than one
reachable match for the same value"). Embedding the checklist's own
committed blob in the trailer value makes each distinct piece of content
its own exact key: two commits genuinely share a trailer *value* only when
they carry byte-identical checklist content, which is exactly the
duplicate-event case `D-Commit-Provenance`'s tie-break already exists to
resolve (a rebase/cherry-pick copying the same content onto a second
commit) — a real content revision is now a different key outright, never a
second match for the same key. This is what makes `AmbiguousFunctionalChecklistTrailerError`
(below) a genuine ambiguity signal again, not a routine outcome of ordinary
use.

**Round-scoped discovery, two layers** (revision 26, `GPT-R39-001`):
- `discover_functional_checklist_commits(repo_root, work_item_id,
  base_commit, head) -> {"<work_item_id>/<implementation_revision>/<blob>":
  commit_sha}`: the existing generic, unmodified
  `_discover_trailer_commits`/`AmbiguousFunctionalChecklistTrailerError`
  machinery (mirroring `discover_scoped_remediation_commits`/
  `AmbiguousScopedRemediationTrailerError` exactly), operating on the full,
  now content-scoped trailer value — raises only for a genuine
  identical-content duplicate reachable by more than one first-parent path
  after the existing tie-break, never for an ordinary content revision.
- **New function**, `discover_current_functional_checklist_evidence(repo_root,
  work_item_id, base_commit, head, implementation_revision) ->
  {"commit_sha": ..., "blob": ...} | None`: enumerates every
  `Workflow-Functional-Checklist` trailer whose value starts with
  `<work_item_id>/<implementation_revision>/` (a round-scoped prefix scan,
  not a single exact-key lookup), resolving each distinct value through
  `discover_functional_checklist_commits` above, and returns the entry
  whose commit is first-parent-nearest to `head` — the round's *current*
  evidence. Multiple historical evidence commits for the same round (an
  older, superseded checklist plus a newer, corrected one) coexist without
  ambiguity by construction, since each has its own distinct trailer value;
  "current" is simply the most recent one reachable, exactly the ordinary
  tie-break semantics every ordinal-keyed trailer scheme here already uses,
  now applied across a family of keys sharing a round prefix instead of to
  one key. `None` if no evidence commit exists for the round at all.

Idempotent by *content*, not merely by round key — this is what makes the
mechanism safe to rerun after an interruption or a genuine checklist
revision: compute the intended checklist content's blob and call
`discover_current_functional_checklist_evidence` for this exact round.
- No result, or the result's `blob` differs from the freshly written
  content's blob: create the commit normally, carrying the new
  content-scoped trailer value. A checklist genuinely revised after the
  user requests changes produces a new, authoritative evidence commit,
  discoverable ahead of the earlier one, without any special-casing.
- The result's `blob` already matches the freshly written content's blob
  exactly: nothing to commit — report the existing commit idempotently
  rather than create an empty one. This is also what makes an
  *interrupted* preparation safe to retry: if step 3's file write already
  landed but the commit step did not complete, rerunning the command
  reproduces identical content and either finds nothing new to commit
  (already committed by a prior partial run) or completes the commit that
  didn't happen yet — never a duplicate.

Report the exact commit SHA and its committed blob hash to the user
alongside the existing `<feedback_dir>/FUNCTIONAL_REVIEW.md` instruction
(step 4), so the user knows precisely which committed content they are
being asked to review — not merely "the current file," which could
otherwise drift before or after that message is read, **and instruct the
user that their `scoped_remediation` confirmation to
`/accept-scoped-remediation` must name this exact commit SHA and blob**
(revision 27, `GPT-R40-001`, below — the reported identity is not merely
informational; it is what the user is required to cite back). After a
successful preparation, `functional_checklist_path` is clean at `HEAD` by
construction (the write and the commit happen in the same invocation, and
no later step touches the file), which is what makes
`/accept-scoped-remediation`'s pre-commit evidence guard satisfiable by
the normal, documented flow for the first time. A checklist that changes
after the user has reviewed one reported commit produces a new evidence
commit that becomes the round's *current* evidence for discovery purposes
(unchanged from revision 26) — but, as of revision 27, current-ness alone
is never sufficient to bind an existing confirmation to it: see
`/accept-scoped-remediation`'s confirmation-evidence-binding check below
for why visibility of a new report is not, by itself, proof the user
reviewed it.

`/accept-milestone`'s own terminal functional-review evidence lifecycle
is unaffected and out of scope here: it does not read
`functional_checklist_blob` today, this revision does not add that read,
and `functional_acceptance_status` remains unwritten by any function in
this codebase — a pre-existing gap noted since `D-States`, not one this
revision's own finding requires closing.

**New command, `/accept-scoped-remediation [work_item_id]`** —
`disable-model-invocation: true`, mirroring `/accept-milestone`'s
construction exactly:

- `APPROVAL_STAGES` extends to `frozenset({"plan", "implementation",
  "acceptance", "scoped_remediation"})` — a fourth stage keyword,
  textually non-interchangeable with `"acceptance"` in
  `validate_user_confirmation`'s existing exact-substring check. This
  alone makes "reuse of terminal milestone acceptance as scoped
  acceptance" structurally impossible.
- **New function**, `parse_scoped_remediation_confirmation_binding_fields(text)
  -> {"functional_checklist_evidence_commit": str, "functional_checklist_evidence_blob":
  str}`** (revision 27, `GPT-R40-001`)**: the `scoped_remediation` stage's
  own binding-field parser, mirroring the pattern
  `REVIEW_PROTOCOL.md`'s `Reviewed bundle ID:`/`Reviewed base commit:`/
  `Work item:` fields already establish for external review feedback
  (`parse_review_feedback_binding_fields`). Requires two explicit fields
  in the confirmation text — `Functional checklist evidence commit:
  <sha>` and `Functional checklist evidence blob: <blob>` — each a
  full, well-formed 40-hex Git object ID; missing or malformed:
  `UserConfirmationRejectedError`, naming which field is missing or
  malformed. This is what makes the confirmation itself name a specific
  reviewed evidence identity rather than only the work item and stage
  `validate_user_confirmation`'s existing generic check already requires
  — `/prepare-functional-review`'s reported commit SHA and blob (above)
  are exactly what the user is instructed to copy into these two fields.
- **New function**, `resolve_scoped_remediation_round(repo_root,
  work_item_id, base_commit, head, outstanding_checkpoint_id,
  implementation_revision, live_fields) -> RoundResolution` (revision 25,
  `GPT-R38-002`, replacing revision 24's two separate descriptions of the
  same classification — an entry-guard short-circuit on key existence
  alone, and a provenance-commit "three outcomes" contract that a
  key-existence short-circuit could never actually reach; comparison
  coverage completed revision 26, `GPT-R39-002`, below): the **single**
  place replay/duplicate classification happens, called identically by the
  entry guard below and the provenance-commit step, never a second copy of
  the logic. It always performs the full lookup-and-compare in one pass —
  there is no cheaper "key exists" path that skips the comparison. Using
  `discover_scoped_remediation_commits(repo_root, work_item_id,
  base_commit, head)` to look up
  `f"{outstanding_checkpoint_id}/{implementation_revision}"`:
  1. No commit found: `NoExistingRound()`.
  2. `AmbiguousScopedRemediationTrailerError` raised by the discovery call
     itself (more than one first-parent-reachable commit for the same
     round key — a pre-existing generic failure mode every other trailer
     scheme here already has): `AmbiguousHistory(round_key)`, propagated as
     a named result rather than left to raise uncaught, so both call sites
     handle it identically.
  3. A commit is found: load that commit's own committed
     `WORKFLOW_STATE.json` at that SHA (`git show
     <commit>:docs/ai-workflow/WORKFLOW_STATE.json`), locate the
     `scoped_remediation_acceptance` list entry whose own recorded
     `outstanding_checkpoint_id`/`implementation_revision` fields equal the
     round key's own two components — never merely trusting the trailer
     value alone for the fields the entry itself also records (revision 26,
     `GPT-R39-002`: the trailer proves a commit exists for this round key;
     it does not prove the committed entry's own fields agree with that
     key, which is exactly the gap a hand-edited or corrupted record could
     exploit).
     - **Schema/version check, first** (revision 26, `GPT-R39-002`;
       version and field count updated revision 27, `GPT-R40-002`): if no
       entry matches by round key, if `acceptance_record_version` is
       not `2` (the only version this design produces as of revision 27
       — no version-1 record exists anywhere, since nothing implementing
       this mechanism has shipped yet, so no migration path is needed),
       or if the entry's key set is not exactly the eleven documented
       fields (`WFR-56`) — no fewer, no unexpected extra — or if
       `recorded_at`/`user_confirmation` are missing or not well-formed
       non-empty strings:
       `MalformedAcceptanceRecord(commit_sha, reason)`, naming which check
       failed. This is checked before any field-by-field comparison below,
       since an unsupported or incomplete schema shape cannot be compared
       against `live_fields` meaningfully at all.
     - **Full canonical field comparison** (revision 26, `GPT-R39-002`,
       replacing revision 25's three-field subset; widened to eight fields
       revision 27, `GPT-R40-002`): compare the entry's own
       `outstanding_checkpoint_id`, `implementation_revision`,
       `reviewed_implementation_head`, `technical_approval_review_content_id`,
       `functional_checklist_path`, `functional_checklist_blob`,
       `functional_checklist_evidence_commit`, and
       `active_work_item_id_at_acceptance` against `live_fields`'s
       corresponding eight keys (`live_fields` gains
       `outstanding_checkpoint_id`, `implementation_revision`,
       `functional_checklist_path` — always the fixed constant
       `docs/ACTIVE_MILESTONE.md` — `functional_checklist_evidence_commit`
       — the confirmation-bound evidence commit SHA the entry guard's
       confirmation-evidence-binding check below already resolved and
       verified equals the round's current evidence (`GPT-R40-001`) — and
       `active_work_item_id` — the live global pointer read from
       `WORKFLOW_STATE.json` at classification time, *not* the resolved
       target argument, so that a replay genuinely reproduces what the
       operation would write if it ran again right now; both call sites
       already have all eight values in scope). `recorded_at` is validated
       as present/well-formed above but never compared to a live value (it
       is a historical timestamp by definition); the entry's own
       `user_confirmation` is validated as a well-formed prior acceptance
       record above, never re-validated against the *current* invocation's
       confirmation text, which `validate_user_confirmation` already
       checks separately as a current-turn authorization gate (unchanged
       from revision 25). Every one of the eight compared fields agrees:
       `ExactReplay(commit_sha)`. At least one disagrees:
       `ConflictingDuplicate(commit_sha, differing_fields)`, naming which
       field(s) — this is also how a changed `active_work_item_id`
       (`GPT-R39-002`'s own failure scenario: the live pointer has since
       moved to a different work item while the same round remains
       discoverable), a wrong `functional_checklist_path`, and a
       confirmation now bound to a different evidence commit than what
       was originally recorded (`GPT-R40-002`'s own failure scenario) are
       refused, rather than silently reported as replay.
- **Entry guard, confirmation-first, then evidence-binding, then
  replay-first** (revision 25, `GPT-R38-002`/`-003`, replacing revision
  24's ordering, which never stated whether current-turn confirmation was
  validated before the replay short-circuit and whose replay short-circuit
  itself trusted a matching key alone; evidence-binding step inserted
  revision 27, `GPT-R40-001`): resolve the target item (named argument, or
  `active_work_item_id`). **Validate current-turn user confirmation**
  (`validate_user_confirmation(text, work_item_id=..., stage=
  "scoped_remediation")`) **before anything else below** — identically for
  a first execution and an exact replay, so a replay can never report
  success without the same user-only gate a first execution requires
  (`GPT-R38-003`; a confirmation-free, read-only inspection of an
  already-accepted round is explicitly out of scope for this command — it
  would need its own separate, non-accepting status command, not built by
  this revision). `UserConfirmationRejectedError` stops here exactly as
  `/accept-milestone`'s own guard does.

  **Then, before the registry loads** (revision 27, `GPT-R40-001`): call
  `parse_scoped_remediation_confirmation_binding_fields(text)` — missing
  or malformed evidence-identity fields stop here, identically for a first
  execution and a replay, for the same reason confirmation validation
  itself runs first. This step and the "confirmation-evidence-binding
  check" the pre-commit evidence guard below performs are the same check,
  described once there and referenced here — see that guard for the full
  discoverability/currency verification. Only once the confirmation's
  named evidence is verified current does the registry load proceed.

  Only then: load the registry;
  compute `is_terminal`/`outstanding_checkpoint_id` fresh via
  `registry_completion_status` — always computable regardless of the
  current `phase`, since it depends only on the registry and
  `select_next_checkpoint`, never on `phase` itself. Then:
  1. If `is_terminal`, refuse, naming `/accept-milestone` as the correct
     command instead (unchanged from revision 22/23).
  2. Otherwise, call `resolve_scoped_remediation_round(...)` with the live
     round identity (`outstanding_checkpoint_id`, `implementation_revision`)
     and the full `live_fields` set the resolver now compares (revision 26,
     `GPT-R39-002`; widened revision 27, `GPT-R40-002`): `reviewed_implementation_head`,
     `technical_approval_review_content_id`, `functional_checklist_blob`
     (computed the same way the pre-commit evidence guard below computes
     them), `functional_checklist_evidence_commit` (the confirmation-bound
     evidence commit SHA the evidence-binding check above already verified
     equals the round's current evidence), `functional_checklist_path`
     (the fixed constant), `active_work_item_id` (the live global pointer,
     read fresh here — not the resolved target argument), plus
     `outstanding_checkpoint_id`/`implementation_revision` themselves for
     the entry's own field comparison:
     - `ExactReplay(commit_sha)`: report `commit_sha` as the idempotent
       result and stop, before the phase/`technical_approval.status`
       checks below ever run — a replay's own `phase` is `IMPLEMENTING`,
       not `AWAITING_FUNCTIONAL_REVIEW`, by construction, so it must never
       reach an ordinary-entry-guard phase check at all. Provably a
       replay, not merely key-matched, because the resolver already
       compared the discovered commit's own recorded fields, the full
       canonical set (`GPT-R39-002`), against the live ones.
     - `ConflictingDuplicate(commit_sha, differing_fields)`: refuse,
       naming `commit_sha` and the specific disagreeing field(s) — never
       silently treated as success. This is also how a changed
       `active_work_item_id` or `functional_checklist_path` is refused
       (revision 26, `GPT-R39-002`), not silently reported as replay.
     - `MalformedAcceptanceRecord(commit_sha, reason)` (revision 26,
       `GPT-R39-002`, new): refuse, naming `commit_sha` and the specific
       schema defect — an unsupported `acceptance_record_version`, a
       missing/extra field, or a missing/malformed `recorded_at`/
       `user_confirmation` — never treated as either a replay or an
       ordinary conflicting duplicate, since the record's own shape cannot
       be trusted enough to compare field values from at all.
     - `AmbiguousHistory(round_key)`: refuse, naming the round key and
       that manual history inspection is required — the same failure mode
       every other trailer scheme here already has.
     - `NoExistingRound()`: proceed to step 3.
  3. (No matching round recorded — a first attempt at this round, or a
     genuinely new round for a checkpoint that was scoped-accepted before
     under a different `implementation_revision`): refuse via
     `scoped_remediation_gate_reachable` unless it returns `True`, and
     refuse unless `work_item["technical_approval"]["status"] ==
     "CURRENT"` (a `STALE` technical approval — a bounded-fix round landed
     after approval but before this command ran — must never be
     scoped-accepted). A distinct new round reaches this branch with
     `phase == AWAITING_FUNCTIONAL_REVIEW` precisely because
     `apply_technical_approval` and a fresh functional review already ran
     again for it — the ordinary gate accepts it exactly as it would any
     other round, with no separate "is this checkpoint's second round"
     special case.
- **Pre-commit evidence guard** (`GPT-R36-003`, extended `GPT-R37-004`,
  extended `GPT-R38-001`, discovery call corrected revision 26
  `GPT-R39-001`, confirmation-evidence-binding check added revision 27
  `GPT-R40-001`): immediately before building the entry below (and once
  more, per the "repeats" paragraph below), four checks run in order.

  1. **Discoverability**: require a `Workflow-Functional-Checklist`
     evidence commit to be discoverable for the exact live round via
     `discover_current_functional_checklist_evidence` (the round-scoped,
     content-identity-aware lookup — not a single exact-key lookup, since
     the trailer's value now embeds the checklist blob, `GPT-R39-001`) —
     refuse, naming the missing round key and `/prepare-functional-review`
     as the remedy, if none is found (`GPT-R38-001`: this proves the
     checklist evidence about to be bound was actually produced by that
     command's own dedicated commit, not merely whatever happens to be
     clean at `HEAD`).
  2. **Confirmation-evidence binding, new** (`GPT-R40-001`): let `current`
     be the discoverability check's own result (`{"commit_sha": ...,
     "blob": ...}`). Compare it against the confirmation's parsed
     `functional_checklist_evidence_commit`/`functional_checklist_evidence_blob`
     (parsed by the entry guard's own confirmation-evidence-binding step
     above, or re-parsed identically here on the pre-provenance-commit
     repeat). Refuse — `StaleFunctionalChecklistConfirmationError`, naming
     both the confirmed identity and `current`'s identity, and instructing
     the user to review the newer `/prepare-functional-review` report and
     reconfirm — unless every one of the following holds:
     - the confirmed commit SHA equals `current["commit_sha"]` exactly;
     - the confirmed blob equals `current["blob"]` exactly;
     - `git rev-parse <confirmed_commit>:<functional_checklist_path>`
       (the commit's own actually-committed content, read independently of
       the trailer value) also equals the confirmed blob — a defense
       against a hand-crafted or corrupted trailer whose embedded blob
       component disagrees with what that commit actually committed,
       refused as `MalformedFunctionalChecklistEvidenceError` naming the
       commit and both blob values if it does not.
     There is deliberately no path that accepts a confirmation naming an
     earlier, superseded evidence commit, even though that commit remains
     independently discoverable and was genuinely what an earlier
     `/prepare-functional-review` invocation reported — this is the
     review's own required contract (`GPT-R40-001`). A corrected checklist
     always requires a fresh report and a fresh, explicitly re-bound
     confirmation; there is no automatic migration of an existing
     confirmation onto newer evidence anywhere in this design.
  3. **Clean working tree**: require `functional_checklist_path` to have
     no staged or unstaged working-tree change relative to `HEAD` (`git
     status --porcelain -- <path>` empty) — refuse outright, naming the
     path, if it is dirty; a working-tree edit the user may have just read
     and accepted is never silently replaced by an older committed blob.
  4. **Cross-invocation value agreement**: only once all three checks
     above pass, re-read `work_item["reviewed_implementation_head"]` and
     compute `git rev-parse HEAD:<functional_checklist_path>`
     (`functional_checklist_path` fixed at `docs/ACTIVE_MILESTONE.md`, the
     path `MILESTONE_WORKFLOW.md`'s `AWAITING_FUNCTIONAL_REVIEW` section
     already names). Refuse, naming both the expected and current value,
     if either has changed since first read earlier in this same
     invocation — the single-invocation window `WFR-21`'s existing
     plan-approval durability guard already treats the same way for a
     different stage.

  **All four checks repeat immediately before the provenance commit**
  (not only at this first read): a working-tree edit, or a superseding
  `Workflow-Functional-Checklist` commit, landing in the gap between the
  first read and the commit is exactly as unreviewed as one present from
  the start — including a superseding commit that would make the
  confirmation's own, previously-verified-current evidence identity stale
  by the time of the actual write (`GPT-R40-001`'s own race window,
  closed by re-running check 2 a second time, not only checks 1/3/4) — and
  the cross-invocation value-agreement check alone (comparing `HEAD:path`
  to `HEAD:path`) cannot detect a dirty tree either, since both reads ignore
  the working tree by construction.
- **New function**, `apply_scoped_remediation_acceptance(state,
  work_item_id, registry, *, user_confirmation, now)`: appends one entry
  to `work_item["scoped_remediation_acceptance"]` (a list, created empty
  if absent):

  ```json
  {
    "outstanding_checkpoint_id": "<from registry_completion_status>",
    "active_work_item_id_at_acceptance": "<live active_work_item_id>",
    "implementation_revision": "<live work_item['implementation_revision']>",
    "reviewed_implementation_head": "<live work_item['reviewed_implementation_head']>",
    "technical_approval_review_content_id": "<technical_approval.approved_review_content_id>",
    "functional_checklist_path": "docs/ACTIVE_MILESTONE.md",
    "functional_checklist_blob": "<git rev-parse HEAD:<functional_checklist_path> at the acceptance commit>",
    "functional_checklist_evidence_commit": "<the confirmation-bound Workflow-Functional-Checklist evidence commit SHA, verified current by the pre-commit evidence guard>",
    "user_confirmation": "<verbatim>",
    "recorded_at": "<timestamp>",
    "acceptance_record_version": 2
  }
  ```

  Eleven fields total (`GPT-R37-003` originally corrected a stale
  nine-field count to ten; `GPT-R40-002` adds an eleventh,
  `functional_checklist_evidence_commit`, revision 27): the five
  revision 22 originally defined
  (`outstanding_checkpoint_id`/`active_work_item_id_at_acceptance`/
  `technical_approval_review_content_id`/`user_confirmation`/
  `recorded_at`), plus four fields revision 23 added (`GPT-R36-003`):
  `implementation_revision`, `reviewed_implementation_head`,
  `functional_checklist_path`, `functional_checklist_blob` —
  `implementation_revision`/`reviewed_implementation_head` each already
  have exactly one existing writer (`apply_technical_approval`/the bundle
  generator, `WFR-22`); reading them here adds no second writer for
  either. `functional_checklist_blob` is a *committed* blob identity
  (`git rev-parse HEAD:...`), not a working-tree hash, so it cannot be
  produced by an uncommitted edit — and, as of revision 24 (`GPT-R37-004`),
  the pre-commit evidence guard above refuses outright rather than compute
  it at all while the path is dirty. Plus one field revision 23 also added,
  `acceptance_record_version` (`2` for every entry this design produces as
  of revision 27, `GPT-R40-002` — `1` was never produced by any shipped
  code, since nothing implementing this mechanism exists yet), so a future
  schema change can distinguish shapes without guessing from field
  presence. Plus one final field revision 27 adds
  (`GPT-R40-002`): `functional_checklist_evidence_commit` — the exact
  `Workflow-Functional-Checklist` evidence commit SHA the pre-commit
  evidence guard's confirmation-evidence-binding check (above) verified
  the user's confirmation named and that this acceptance is binding to; a
  blob alone no longer identifies which of possibly several
  content-distinct evidence commits for the round was the one actually
  reviewed, once revision 26 made multiple such commits coexist without
  ambiguity. Five plus four plus one plus one is eleven, not ten.

  Sets `phase = "IMPLEMENTING"`. Leaves `checkpoints`/
  `current_checkpoint_id`/`active_work_item_id`/`plan_approval`/
  `technical_approval`/`functional_acceptance_status` completely
  untouched.
- **Dedicated provenance commit, required** (`GPT-R36-002`, replacing
  revision 22's "no dedicated commit" choice; trailer keying corrected
  revision 24, `GPT-R37-002`): the state write above and a metadata-only
  commit — no production/test changes — carrying
  `Workflow-Scoped-Remediation-Acceptance: <outstanding_checkpoint_id>/<implementation_revision>`
  (plus the ordinary `Workflow-Work-Item: <work_item_id>` trailer every
  provenance commit already carries) happen together or not at all;
  success is reported only once the commit exists and the worktree is
  verified clean. Revision 22's `mark_technical_approval_stale`/
  `record_bundle_generation` precedent does not transfer here: unlike
  those recomputable/superseded writes, this is a user-authorized gate's
  durable record — the same class `plan_approval`/`technical_approval`/
  `WF-Activate` already commit via a dedicated trailer, not the lighter
  class.

  **The trailer's discovery key is `<outstanding_checkpoint_id>/<implementation_revision>`,
  not `<work_item_id>/<outstanding_checkpoint_id>`** (revision 23's shape,
  corrected): keying purely by checkpoint made every later round for the
  same still-incomplete checkpoint collide with the first, which is what
  forced revision 23 to refuse them outright — the wrong fix for a real
  requirement (`WF8b` may legitimately need more than one reviewed
  remediation round, revision 22's own list-based
  `scoped_remediation_acceptance` schema already assumed this). Keying by
  `implementation_revision` instead — a field with exactly one existing
  writer, `apply_technical_approval`, that only ever advances, `WFR-22` —
  makes each round's trailer value distinct by construction, reusing
  `_discover_trailer_commits`'s existing generic exactly-one-match-per-
  value/first-parent-tie-break machinery (`discover_checkpoint_commits`/
  `discover_approval_commits`'s own shared helper) unmodified, with a new
  `discover_scoped_remediation_commits(repo_root, work_item_id,
  base_commit, head) -> {"<checkpoint_id>/<implementation_revision>":
  commit_sha}` wrapper alongside them and a new
  `AmbiguousScopedRemediationTrailerError` mirroring
  `AmbiguousApprovalTrailerError`.

  **Immediately before creating the commit, call
  `resolve_scoped_remediation_round(...)` again with the same live
  values** (revision 25, `GPT-R38-002`, replacing revision 24's separate
  "three outcomes" restatement, which described the same classification a
  second time rather than sharing the entry guard's own call — the exact
  duplication that let the entry guard's cheaper, unvalidated
  short-circuit silently determine behavior on its own). The entry guard's
  own call above may be arbitrarily far in wall-clock time before this
  actual write — the same single-invocation-window concern
  `WFR-21`/the pre-commit evidence guard above already treat this way for
  other fields:
  1. `NoExistingRound()`: create the commit normally. This is what makes
     multiple legitimate rounds for the same checkpoint possible — each
     genuinely new `implementation_revision` gets its own distinct round
     key, so nothing about accepting round two ever references or
     conflicts with round one's own commit.
  2. `ExactReplay(commit_sha)`: a round accepted by a concurrent or prior
     invocation landed in the gap since the entry guard's own check —
     report `commit_sha`, create nothing, exactly as the entry guard's
     equivalent case, never silently duplicated.
  3. `ConflictingDuplicate`/`AmbiguousHistory`/`MalformedAcceptanceRecord`
     (the last new, revision 26, `GPT-R39-002`): refuse, exactly as the
     entry guard's equivalent case above — never silently overwritten.

  There is exactly one classification contract, `resolve_scoped_remediation_round`'s
  own, stated once and referenced by name at both call sites — not narrated
  twice with room for the two narrations to disagree.
- **Exit**: report the outstanding checkpoint, the acceptance commit, and
  that a fresh session should resume it via that checkpoint's own driver —
  stop.

**Resume**: `checkpoints`/`active_work_item_id` were never touched by
this mechanism, so the outstanding checkpoint's own driver (e.g.
`/bootstrap-workflow-v2` for `WF8b`) re-derives it deterministically, in a
fresh session, from disk alone, via its own existing selection logic — no
new pointer or marker file. Because the acceptance is now a required
commit (`GPT-R36-002`), a fresh session reading only committed state can
never observe an acceptance that "almost happened": before the commit,
committed state still shows `AWAITING_FUNCTIONAL_REVIEW`, the same
already-defined guard path; after it, committed state shows `IMPLEMENTING`
and the outstanding checkpoint is re-derived exactly as before.

**Replay/staleness safety**: superseded-revision, duplicate-acceptance,
changed-checkpoint, and terminal-reuse cases are each covered without a
bespoke check per case — see "WF8b finding disposition (revision 21 →
22)" above for the point-by-point reasoning. Omitted/foreign-registry
coverage, commit durability, and evidence-binding are each covered by the
corresponding `GPT-R36-*` fix above — see "WF8b finding disposition
(revision 22 → 23)" for that point-by-point reasoning. Registry-trust,
per-round replay/duplicate-refusal ordering, the schema field count, and
dirty-checklist evidence are each covered by the corresponding `GPT-R37-*`
fix above — see "WF8b finding disposition (revision 23 → 24)" for that
point-by-point reasoning. Checklist-evidence durability, replay
classification's own correctness (not just its ordering), and
confirmation-before-replay ordering are each covered by the corresponding
`GPT-R38-*` fix above — see "WF8b finding disposition (revision 24 → 25)"
for that point-by-point reasoning. Checklist-evidence trailer ambiguity
and exact-replay comparison completeness are each covered by the
corresponding `GPT-R39-*` fix above — see "WF8b finding disposition
(revision 25 → 26)" for that point-by-point reasoning. Time-of-review/
time-of-acceptance evidence substitution and the missing evidence-commit
provenance field are each covered by the corresponding `GPT-R40-*` fix
above — see "WF8b finding disposition (revision 26 → 27)" for that
point-by-point reasoning.

**Dependency**: extends `WF4c` (`D-Functional-Remediation`,
`complete_work_item`'s existing home), `WF4a-ii` (`/accept-milestone`'s
existing home), and, as of revision 25, `WF-M8b`
(`/prepare-functional-review`'s existing home, already extended once for
`D-Legacy` phase 2's adoption-selector logic); `WF2`'s existing
`D-Selection`/`select_next_checkpoint` machinery is reused unmodified,
never re-implemented.

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

## Requirements traceability (revision 23 — clean, self-contained; see the note under "Round 6 finding disposition" above for why this replaces rather than extends the old WFR-1–61 numbering)

**WFR-53 through WFR-57 were added in revision 22** (`WF8B-002`,
`D-Scoped-Remediation-Acceptance`), covering `complete_work_item`'s new
own-registry-completeness guard, the two new gate-reachability functions,
the new `/accept-scoped-remediation` command and its
`scoped_remediation_acceptance` evidence schema, and the fresh-session
resume guarantee — owned by `WF4c` (extends `D-Functional-Remediation`'s
existing "parent acceptance blocks on an incomplete child" reasoning to
the item's own registry), `WF4a-ii` (`/accept-milestone`'s existing
home), and `WF2` (`select_next_checkpoint`'s existing home, reused
unmodified) respectively — no new checkpoint, matching the precedent
`WFR-47`-`WFR-52` already set of attributing continued-`WF8b`-scope
requirements to whichever already-complete checkpoint actually owns the
touched subsystem. **`WFR-53` and `WFR-56` were amended, and `WFR-58`/
`WFR-59` added, in revision 23** (`GPT-R36-001`/`-002`/`-003`,
`D-Scoped-Remediation-Acceptance`'s hardening): `WFR-53` gained the
fail-closed omitted/foreign-registry guard, `WFR-56` gained the four
additional evidence-binding fields, `WFR-58` covers the required
provenance commit and its idempotency/duplicate-refusal, and `WFR-59`
covers refusing acceptance against stale checklist/head evidence — all
four owned by `WF4c`, the same checkpoint `WFR-53`/`-55`/`-56` already
belong to; no new checkpoint added. **`WFR-53`, `WFR-55` (test column),
`WFR-56` (test column), `WFR-58`, and `WFR-59` were further amended in
place in revision 24** (`GPT-R37-001`/`-002`/`-003`/`-004`): `WFR-53` now
describes `complete_work_item` loading its own registry from `repo_root`
rather than trusting a caller-supplied one, `WFR-58` now describes
per-round (not per-checkpoint) replay identity checked ahead of the
ordinary phase guard, and `WFR-59` now includes the outright dirty-
checklist refusal — no requirement added, removed, or renumbered.
**`WFR-58` and `WFR-59` were further amended in place, and a new `WFR-60`
added, in revision 25** (`GPT-R38-001`/`-002`/`-003`,
`D-Scoped-Remediation-Acceptance`'s further hardening): `WFR-58` now
describes replay/duplicate classification as one shared resolver function
that always loads and compares a discovered commit's own recorded fields
— never a matching trailer key alone — called identically by both the
entry guard and the provenance-commit step, plus current-turn
confirmation validated before that classification runs for a first
execution and a replay alike; `WFR-59` now also requires a discoverable
checklist-evidence commit for the exact round, not only a clean working
tree; `WFR-60` is new, covering `/prepare-functional-review`'s own
checklist-evidence commit — owned by `WF4c` (`WFR-58`/`-59`, same
checkpoint as before) and `WF4c`/`WF-M8b` jointly (`WFR-60`,
`/prepare-functional-review`'s existing home); no requirement removed or
renumbered. **`WFR-58`, `WFR-59`, and `WFR-60` were further amended in
place in revision 26** (`GPT-R39-001`/`-002`): `WFR-58` now describes the
shared resolver comparing the full seven-field canonical set (not the
three-field subset revision 25 checked) plus the acceptance record's own
schema version/shape, refusing an unsupported or incomplete record as a
dedicated malformed-record result; `WFR-59` now states that acceptance
always binds to a round's *current* checklist evidence, never an earlier
one a user may have been shown first; `WFR-60` now describes the
checklist-evidence trailer's value as content-scoped
(`<work_item_id>/<implementation_revision>/<checklist_blob>`), so a
legitimate same-round content revision is a distinct trailer key rather
than a second match the generic exactly-one-match contract would refuse
as ambiguous — owned by the same checkpoints as before; no requirement
added, removed, or renumbered. **`WFR-56`, `WFR-58`, `WFR-59`, and
`WFR-60` were further amended in place in revision 27** (`GPT-R40-001`/
`-002`): `WFR-56` now includes an eleventh recorded field,
`functional_checklist_evidence_commit`; `WFR-58` now describes the shared
resolver comparing the full eight-field canonical set (adding
`functional_checklist_evidence_commit` to revision 26's seven); `WFR-59`
is corrected to state the opposite of revision 26's binding claim —
acceptance binds only to the exact evidence commit the current-turn
confirmation itself names, verified equal to the round's current
evidence, and refuses as a stale confirmation rather than silently
rebinding when a newer evidence commit exists; `WFR-60` now states that
the reported evidence identity is what the required confirmation must
cite back, not merely informational — owned by the same checkpoints as
before (`WF4c`; `WF4c`/`WF-M8b` jointly for `WFR-60`); no requirement
added, removed, or renumbered.

Every requirement below is current as of this revision. Superseded
requirements from earlier revisions (the pre-split single-`bundle_id`
design, rounds 1-4) are not restated — see the note above for why. This
table's **Requirement column is a generated, human-readable view of**
`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`'s own
`description` field per row (corrected, `OPUS-R28-003`, which found 15 of
52 rows diverged, some substantively — `WFR-24`'s two versions stated
opposite properties — despite `OPUS-R27-001`'s own conformance-test claim;
all 52 synced this revision, verified by direct comparison under the exact
normalization missing test 166 states, not merely asserted). **The
Checkpoint column is explicitly not part of that generated-view claim and
is out of scope for missing test 166**, which compares `description`
fields only: several Checkpoint-column entries name a design-doc section
(`D3`, `D2`, `D-Registry`, `D4b`, `D-Legacy`) rather than, or alongside, a
registry checkpoint id — `WFR-29`'s "D3, D2" is the sharpest example — and
`docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json`'s own
`checkpoint_ids` field cannot represent a design-doc reference at all: D3's
validator requires every member to be a real registry checkpoint id (the
bidirectional registry × mapping coverage check, `OPUS-R10-006`), so
literally syncing the Checkpoint column into `checkpoint_ids` would either
fail that validator or silently drop the non-checkpoint references. The
table's Checkpoint column may therefore cite design sections for reader
context beside, or instead of, the checkpoint(s) that actually implement a
requirement; `checkpoint_ids` remains the sole authoritative,
validator-enforced source for "which checkpoint owns this requirement," per
`WFR-25`. (created
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
today, the same class as WFR-42/-45. Revision 15 added no new requirement:
`OPUS-R20-001`/`-002`/`-003` extended WFR-45/-46's evidence with
forward-looking obligations owed to `WF4a-i` (missing-test items 138-140)
rather than adding new requirements. **WFR-47 through WFR-50 were added in
revision 16** (`WF8B-S1-001`), covering `D-Fingerprint-Generalization`'s
per-work-item metadata resolution, its fail-closed matrix, and
`workflow-v2-1-core`'s own backward-compatibility guarantee — **wording
corrected in revision 17** (`OPUS-R25-001`/`-002`) where revision 16's text
depended on the now-corrected backward-compatibility criterion or the now-
single-sourced `plan_revision`. **WFR-51 and WFR-52 were added in revision
17** (`OPUS-R25-004`/`-005`/`-008`), covering the creation-path ordering
for a new work item and the manifest/bundle work-item binding — both real
gaps revision 16's own requirement set left uncovered, not merely
under-specified. **Revision 18 adds no new requirement** (`OPUS-R26-*`):
every round-26 correction is a within-scope fix to `D-Fingerprint-Generalization`'s
existing mechanism, not a new obligation class — WFR-47's, WFR-49's, and
WFR-50's own evidence columns gain missing-test items 145/157/159 (three
items that existed since revision 16/17 but traced to no requirement,
`OPUS-R26-005`), and WFR-47, WFR-48, WFR-49, WFR-50, WFR-51, and WFR-52's
descriptions are extended to name the `base_commit`/`prepare-ai-review.sh`
single-rule corrections (`OPUS-R26-002`/`-003`) their own checkpoint
(`WF4a-i`) already owned — **six rows, corrected `OPUS-R27-009`: this
paragraph, `REVIEW_REQUEST.md`, and the Round 26 scope-discipline paragraph
each previously named a different subset (five rows, omitting WFR-48, or
just WFR-49/WFR-52) of the same actual six-row diff**. **Revision 19
likewise adds no new requirement** (`OPUS-R27-*`): `WFR-47`-`WFR-52`'s
table-row *content* was already correct as of revision 18; what revision 19
fixes is that `workflow-v2-1-core-mapping.json`'s own `description` values
had never been synced to it (`OPUS-R27-001` — the `OPUS-R26-005` disposition
row's and the revision-18 self-review notes' claim that they already were
was false, corrected in place, not silently superseded), and `WFR-48`'s
evidence column, which owns the thirteen-condition fail-closed matrix, gains
missing-test items 145, 152, 161, and 163 — coverage for conditions 11-13
that existed since revision 17/18 but had been recorded only under
`WFR-49`/`WFR-50`, never under the requirement that actually owns the
matrix (`OPUS-R27-006`).
Every requirement now maps to at least one checkpoint and every checkpoint
owns at least one requirement — verified by direct query against the JSON
files, not by inspection (re-run and confirmed clean this round: zero
unmapped requirements, zero unowned checkpoints, zero dangling
`depends_on`, topological order still valid).

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
| WFR-47 | Plan-stage `work_item_type`, `plan_path`, `registry_path`, `mapping_path`, `base_commit`, the protected/excluded path sets, and `plan_revision` are all resolved per `work_item_id` from one authoritative, validated source each (`WORKFLOW_STATE.json` and `<work_item_id>-artifacts.json`), never a hardcoded default naming a single work item, and never from two disagreeing sources (corrected `OPUS-R25-002`/`-010`: `plan_revision` is registry-only; the resolver lives in `workflow_fingerprint.py`, not `workflow_state.py`, so no call site can silently keep the old defaults; extended `OPUS-R26-003`: `base_commit` joins the resolved-per-item fact set, closing the one field revision 17 still let fall back to a literal) | WF4a-i | `compute_review_content_id_plan_stage_for_work_item`/`_at_commit_for_work_item` resolve every value from `resolve_plan_stage_metadata`; the CLI's `--work-item-id` (defaulting to live `active_work_item_id`) and its `base` positional (defaulting to the resolved item's own `base_commit`, never a literal) and `workflow_state.approval_review_content_id`'s `stage="plan"` branch (no `plan_revision` parameter) all route through it (`WF8B-S1-001`, `OPUS-R25-002`/`-010`, `OPUS-R26-003`) — **owed**, items 141, 147, 149-150, 159, 163-164 |
| WFR-48 | Unknown, incomplete, mismatched, duplicated, or cross-wired work-item plan-stage metadata fails closed, independently distinguishable by exception type — including a declared path that is not actually a member of the protected set it claims, and a path that fails a repo-relative/no-traversal/no-symlink grammar check (corrected/extended `OPUS-R25-003`/`-008`/`-009`) | WF4a-i | Each of the thirteen fail-closed matrix conditions in `D-Fingerprint-Generalization` raises its own named exception (`WF8B-S1-001`, `OPUS-R25-003`/`-008`/`-009`, `OPUS-R26-001`/`-003`) — **owed**, items 145-148, 152, 158, 161, 163 (corrected `OPUS-R27-006`: conditions 11-13's own sole coverage had been recorded only under `WFR-50`/`WFR-49`, never under this requirement, which actually owns the matrix) |
| WFR-49 | A second, real work item computes a plan-stage `review_content_id` distinct from `workflow-v2-1-core`'s — including its own `base_commit`, never core's — whose manifest, `MANIFEST.md`'s own `work_item_id`/`base_commit` fields, and `--write-manifest` output all contain/name only its own declared files/identity; mutating either work item's protected content changes only that item's identifier; a `--write-manifest` invocation targeting a bundle directory whose existing manifest disagrees with the resolved item (a different `work_item_id`, no `work_item_id` at all, or a different `base_commit`) refuses; `prepare-ai-review.sh` resolves the work-item id once and uses it consistently for the directory and the manifest it writes (corrected/extended `OPUS-R25-004`/`-005`/`-012`, `OPUS-R26-001`/`-002`/`-003`) | WF4a-i | Cross-item fixture pair, both directions, plus creation-path/manifest-binding coverage (`WF8B-S1-001`, `OPUS-R25-004`/`-005`/`-012`, `OPUS-R26-001`/`-002`/`-003`) — **owed**, items 143-144, 149, 151-155, 157, 161-163, 165 |
| WFR-50 | `workflow-v2-1-core`'s own plan-stage protected/excluded sets, and the migrated `<work_item_id>-artifacts.json`'s own approval binding, are reproduced/preserved exactly after the generalization — verified mechanism-relatively (migrated sets equal the pre-migration Python constants) rather than against an unreproducible literal digest, without a fresh review round required for the migration's data alone; commit-source resolution predating the schema-version-2 migration is a deliberate, tested boundary rather than a silent fallback (corrected `OPUS-R25-001`/`-006`/`-013`) | WF4a-i | Migrated `plan_stage` JSON equals the pre-migration Python constants item for item; `<work_item_id>-artifacts.json` classifies implementation-stage protected by exact path via its own concrete self-referential entry, not a placeholder (`WF8B-S1-001`, `OPUS-R25-001`/`-006`/`-013`, `OPUS-R26-004`) — **owed**, items 141-142, 145, 156, 160, 164 |
| WFR-51 | A new process work item's `plan_path`/`registry_path`/`mapping_path`/`base_commit` and `<work_item_id>-artifacts.json` each have a named sole writer and a stated ordering (declare paths, then populate registry/mapping/artifacts content, then compute a fingerprint) that avoids circular dependency and fails closed on a partial definition (new, `OPUS-R25-004`; extended `OPUS-R26-003` to include `base_commit` among the sole-writer facts) | WF4a-i | `/milestone-plan v2-1-dry-run` end to end succeeds with no missing-metadata error, including a real, non-null `base_commit`; the default artifacts template satisfies the protected-set binding check by construction (`OPUS-R25-004`, `OPUS-R26-003`) — **owed**, items 154-155 |
| WFR-52 | `MANIFEST.md` and every plan-stage bundle it accompanies are bound to an explicit `work_item_id` and `base_commit`; no command or script path (including `scripts/prepare-ai-review.sh`, which resolves the work-item id exactly once for both the bundle directory and the manifest) can silently write or overwrite a bundle/manifest belonging to a different or unspecified work item — including a bundle directory whose existing manifest declares no `work_item_id` at all, which fails closed rather than binding silently (new, `OPUS-R25-005`/`-012`; corrected/extended `OPUS-R26-001`/`-002`/`-003`) | WF4a-i | `BundleWorkItemMismatchError` on a cross-item write attempt, an unbound-manifest write attempt, and a `base_commit` disagreement; `prepare-ai-review.sh`'s `plan`-stage path writes `MANIFEST.md` itself, sharing one single resolved work-item id with its own bundle-directory choice (`OPUS-R25-005`/`-012`, `OPUS-R26-001`/`-002`/`-003`) — **owed**, items 152-153, 161-163, 165 |
| WFR-53 | `complete_work_item` refuses to reach `MILESTONE_COMPLETE` while any of the work item's own registry checkpoints remain incomplete or `IN_PROGRESS`, independent of, and in addition to, the existing incomplete-child-work-item check; for a registry-backed item it loads that item's own `registry_path` itself from `repo_root` (the same safe-path/tracked/parse validation `D3`'s whole-state mirror check already performs), never accepting a caller-supplied registry object, and refuses when that file cannot be resolved, read, or parsed, or when its declared `work_item_id` does not match the item being completed | WF4c | `IncompleteOwnCheckpointsError` raised when the on-disk registry has one incomplete, dependency-satisfied checkpoint and, separately, when one is blocked on unmet dependencies; a fully-complete on-disk registry (and a registry-less item, `registry_path: null`) still completes; `RegistryCoverageError` raised when `registry_path` fails safe-path resolution or the file is missing/unreadable/malformed, and separately when the loaded registry declares a foreign `work_item_id`; a fabricated in-memory dict can no longer be substituted for the file at all, since `complete_work_item` no longer accepts one — **owed**, items 168-170, 178, 183-184, 187 |
| WFR-54 | `/accept-milestone` and `/accept-scoped-remediation` each have a named, non-circular gate-reachability guard (`milestone_complete_gate_reachable`/`scoped_remediation_gate_reachable`) computed from the item's own phase and a freshly recomputed terminal/non-terminal check, never from an intermediate phase value written earlier | WF4a-ii | `/accept-milestone` refused, naming the actual phase and the outstanding checkpoint, when the registry is non-terminal (both the selectable and `NoCheckpointReadyError`-blocked cases); succeeds unchanged when terminal (regression guard) — **owed**, items 167-169 |
| WFR-55 | A continued-scope implementation round that leaves its parent checkpoint incomplete can be functionally accepted via `/accept-scoped-remediation`, distinctly and non-interchangeably from `/accept-milestone`'s terminal acceptance, and returns the item to `IMPLEMENTING` without disturbing `checkpoints`/`current_checkpoint_id`/`active_work_item_id`/`plan_approval`/`technical_approval`/`functional_acceptance_status`; the acceptance is durably recorded by the command's own dedicated, metadata-only provenance commit before success is reported, never left as an uncommitted state write | WF4c | Happy path appends exactly one `scoped_remediation_acceptance` entry, flips `phase`, and creates the provenance commit; wrong-phase refusal (`IMPLEMENTING`, `AWAITING_TECHNICAL_APPROVAL`, `MILESTONE_COMPLETE`); refusal when the registry is actually terminal; refusal when `technical_approval.status != CURRENT`, each for a genuinely new attempt; replay/duplicate semantics themselves are `WFR-58`'s own scope, not this row's — **owed**, items 171-176, 179-180 |
| WFR-56 | `scoped_remediation_acceptance` is an additive, append-only evidence list distinct from `technical_approval`/`functional_acceptance_status`, naming the outstanding checkpoint, the accepted round's `implementation_revision` and `reviewed_implementation_head`, the functional-review checklist's path, a committed content digest, and the exact `Workflow-Functional-Checklist` evidence commit SHA that digest was drawn from (revision 27, `GPT-R40-002`), `technical_approval.approved_review_content_id`, the verbatim user confirmation, the live `active_work_item_id` at acceptance time, and an acceptance-record schema version | WF4c | Schema assertion on the appended entry's exact eleven-field set (field count corrected revision 24, `GPT-R37-003`; eleventh field added revision 27, `GPT-R40-002`); `technical_approval`/`functional_acceptance_status` byte-identical before/after a successful scoped acceptance — **owed**, items 171, 185 |
| WFR-57 | A fresh session invoking a checkpoint's own driver after scoped-remediation acceptance deterministically re-selects the same outstanding checkpoint via the existing `select_next_checkpoint` algorithm, with no new pointer or marker file | WF2 | This exact finding's own reproduction, end to end: an item with `technical_approval` freshly `CURRENT` while a registry checkpoint remains incomplete can never reach `MILESTONE_COMPLETE` via any documented command sequence without first passing through `/accept-scoped-remediation` and returning to `IMPLEMENTING`, from which a fresh, disk-only session re-selects `WF8b` — **owed**, item 177, 179 |
| WFR-58 | `accept-scoped-remediation` creates its own dedicated, metadata-only provenance commit -- naming the work item, outstanding checkpoint, and accepted round's `implementation_revision` in a discoverable trailer -- before reporting success, never a plain state write swept into whatever commit follows; replay/duplicate classification is performed by one shared resolver function, called identically by the entry guard and the provenance-commit step, that loads and compares the discovered commit's own recorded round fields -- the full canonical set (`outstanding_checkpoint_id`, `implementation_revision`, `reviewed_implementation_head`, `technical_approval_review_content_id`, `functional_checklist_path`, `functional_checklist_blob`, `functional_checklist_evidence_commit`, `active_work_item_id_at_acceptance`), not a subset (widened to eight fields revision 27, `GPT-R40-002`), plus the record's own schema version/shape (revision 26, `GPT-R39-002`; version number updated revision 27, `GPT-R40-002`) -- before ever reporting a replay -- a matching trailer key alone is never sufficient; current-turn user confirmation for the `scoped_remediation` stage is validated before that classification runs, identically for a first execution and an exact replay; an exact-round replay reports the existing commit idempotently regardless of the current phase, a distinct later-reviewed round against the same still-outstanding checkpoint is permitted through the ordinary entry guard rather than refused, a same-round collision whose recorded fields disagree (including a changed active work-item pointer, checklist path, or checklist evidence commit, `GPT-R40-002`) is refused as a conflicting duplicate, an unsupported acceptance-record schema version or shape is refused as a dedicated malformed-record result (revision 26, `GPT-R39-002`), and an ambiguous multi-commit history for the same round key is refused rather than guessed; fresh-session bootstrap resumes only from the committed acceptance, never from an uncommitted working-tree state, and a crash before the commit leaves the prior phase authoritative | WF4c | Provenance commit created and its trailer named (including `implementation_revision`) before success is reported; a simulated crash leaving only an uncommitted state write is treated as if the acceptance never happened; an exact replay of the identical accepted round is recognized only after the shared resolver loads and compares the discovered commit's own recorded fields, the full canonical set including `functional_checklist_evidence_commit`, against the live ones, reporting the existing commit rather than duplicating it, even though the live phase is no longer `AWAITING_FUNCTIONAL_REVIEW`; a distinct, later-reviewed round against the same still-outstanding checkpoint (a new `implementation_revision` reached via a fresh technical approval and functional review) is accepted, not refused; a same-round collision whose recorded fields disagree with what is being recorded is refused as a conflicting duplicate, naming the already-recorded commit, including a changed live `active_work_item_id`, a wrong `functional_checklist_path`, or a disagreeing `functional_checklist_evidence_commit`; an unsupported `acceptance_record_version` or an incomplete/extra field set is refused as `MalformedAcceptanceRecord`, never silently compared as if well-formed; an ambiguous multi-commit history for the same round key is refused; missing or stale current-turn confirmation is refused identically for a first execution and an exact replay — **owed**, items 180-182, 188-189, 191-193, 196, 201-205, 213 |
| WFR-59 | The `scoped_remediation_acceptance` record binds to the exact functional-review checklist content and reviewed implementation head the user actually accepted: acceptance is refused outright, before any digest is computed or recorded, unless a `Workflow-Functional-Checklist` evidence commit is discoverable for the exact live round, and while the checklist path has a staged or unstaged working-tree change (checked at first read and again immediately before the provenance commit); once clean, acceptance is further refused when the current checklist's committed digest, the current `reviewed_implementation_head`, or the current `technical_approval` no longer match what is being recorded, rather than silently accepting against stale evidence; **the evidence bound is always the exact evidence commit the current-turn `scoped_remediation` confirmation itself names, verified reachable for the exact round and content-consistent with its own trailer -- never merely "whichever evidence commit happens to be current" (revision 27, `GPT-R40-001`, reversing revision 26's "always binds to current, never an earlier one" claim): if the confirmation names an earlier, superseded evidence commit while a newer one exists, acceptance refuses as a stale confirmation, naming both identities, rather than silently rebinding to the newer evidence; a confirmation naming a commit that does not belong to the round, or whose own committed content disagrees with its trailer's embedded blob, is refused identically to a missing-evidence confirmation; a corrected checklist always requires a fresh `/prepare-functional-review` report and a fresh, explicitly re-bound confirmation before acceptance can proceed** | WF4c | The recorded entry's `functional_checklist_blob`/`functional_checklist_evidence_commit`/`reviewed_implementation_head` match what was live at acceptance time; acceptance is refused, naming the missing round key, when no `Workflow-Functional-Checklist` evidence commit is discoverable for the exact round; a staged or unstaged edit to the checklist path is refused outright, both at first read and if introduced immediately before the commit; a simulated checklist edit or new implementation head landing between functional review and acceptance is refused, naming the expected and current value; a confirmation naming the round's current evidence commit succeeds; a confirmation naming a superseded evidence commit is refused as stale, naming both identities, and only a fresh confirmation naming the new current commit succeeds; a confirmation naming a wrong-round, foreign, or trailer-inconsistent commit is refused — **owed**, items 185-186, 190, 194, 199-200, 207-212 |
| WFR-60 | `/prepare-functional-review` creates a dedicated, metadata-only checklist-evidence commit before stopping for the user, carrying a discoverable `Workflow-Functional-Checklist: <work_item_id>/<implementation_revision>/<checklist_blob>` trailer -- the checklist's own committed blob embedded in the trailer value, not a bare round value (revision 26, `GPT-R39-001`, so that a legitimate same-round content revision is a distinct trailer key, never a second match for the bare round key the generic exactly-one-match contract would otherwise treat as ambiguous); it is idempotent by content -- an unchanged checklist re-commits nothing, a changed checklist for the same round produces a new, distinct, discoverable commit under its own trailer value -- so an interrupted preparation is always safe to retry; round-scoped discovery resolves the most recent reachable evidence commit for a round as current, while a genuine identical-content duplicate for the same round (two commits, the same blob) is still refused as ambiguous exactly as every other trailer scheme here already does; **the reported commit SHA and blob are not merely informational — the command instructs the user that their `scoped_remediation` confirmation to `/accept-scoped-remediation` must name this exact identity (revision 27, `GPT-R40-001`)** | WF4c, WF-M8b | A first preparation creates the checklist-evidence commit and reports its SHA/blob; rerunning with identical content reports the existing commit and creates nothing new; rerunning with changed content creates a new, distinct commit under its own trailer value for the same round, discoverable ahead of the earlier one, with the earlier commit still separately discoverable by its own value; two commits sharing the exact same trailer value (identical checklist content) reachable via more than one first-parent path are refused as ambiguous; a simulated interruption between the checklist write and the commit is safe to retry with no duplicate commit; the exact `WF8b` checklist flow, including a mid-flow checklist correction requiring a second explicit confirmation, reaches scoped acceptance from a clean tree with no manual out-of-contract commit — **owed**, items 191, 195, 197-198, 206, 214 |

## Checkpoint registry (revision 26: 17 checkpoints, unchanged count — `WF4a-iv` (added revision 10) had its session target widened in revision 11; revisions 12 through 26 (`OPUS-R14-*`/`OPUS-R16-*`/`OPUS-R18-*`/`OPUS-R20-*`/`WF8B-S1-001`/`OPUS-R25-*`/`OPUS-R26-*`/`OPUS-R27-*`/`OPUS-R28-*`/`GPT-R29-*`/`WF8B-002`/`GPT-R36-*`/`GPT-R37-*`/`GPT-R38-*`/`GPT-R39-*`) touched no checkpoint's size or dependency, only `PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_*` classification (and, since revision 16, how that classification is *derived* per work item — `D-Fingerprint-Generalization`), the registry JSON's own `plan_revision` field, and requirements owned by existing checkpoints; complexity scale defined; this table is a generated view of `docs/ai-workflow/registry/workflow-v2-1-core-registry.json`, D-Registry). **Revision 22** (`D-Scoped-Remediation-Acceptance`) adds `WFR-53`-`WFR-57`, owned by `WF4c`/`WF4a-ii`/`WF2` — same pattern as `WF8B-S1-001`'s own `WFR-47`-`WFR-52` (owned by `WF4a-i`). **Revision 23** (`GPT-R36-001`/`-002`/`-003`) amends `WFR-53`/`WFR-56` and adds `WFR-58`/`WFR-59`, all owned by `WF4c`. **Revision 24** (`GPT-R37-001`/`-002`/`-003`/`-004`) amends `WFR-53`/`WFR-55`/`WFR-56`/`WFR-58`/`WFR-59` in place (no new requirement added or renumbered), all owned by `WF4c`. **Revision 25** (`GPT-R38-001`/`-002`/`-003`) amends `WFR-58`/`WFR-59` in place and adds `WFR-60`, owned by `WF4c` (`WFR-58`/`-59`) and `WF4c`/`WF-M8b` jointly (`WFR-60`) — no checkpoint's own name/scope text changes, including `WF8b`'s own row below, unchanged. **Revision 26** (`GPT-R39-001`/`-002`) amends `WFR-58`/`WFR-59`/`WFR-60` in place (no new requirement added or renumbered; `WFR-56`'s schema itself is untouched), owned by `WF4c` (`WFR-58`/`-59`) and `WF4c`/`WF-M8b` jointly (`WFR-60`) — no checkpoint's own name/scope text changes.

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

## Missing tests (revision 26 — clean, continuous numbering; items 1-44 from revision 7, 45-54 from revision 8, 55-78 from revision 9, 79-93 from revision 10, 94-99 from revision 11, 100-116 from revision 12, 117-124 from revision 13, 125-137 from revision 14, 138-140 from revision 15, 141-160 from revision 16/17, 161-163 from revision 18, 164-166 from revision 19, 167-179 from revision 22 (`WF8B-002`), 180-186 from revision 23 (`GPT-R36-*`), 187-190 from revision 24 (`GPT-R37-*`), 191-196 from revision 25 (`GPT-R38-*`), 197-206 from revision 26 (`GPT-R39-*`) — no new numbered items in revision 20 or 21; items 161, 166 corrected/extended in revision 21 (`GPT-R29-*`); items 170, 171, 176, 178, 182-186 amended in revision 24; item 189 amended in revision 26 — forward-looking obligations owed to `WF8b`'s continued scope, now including items 167-206; see the note under "Round 6 finding disposition" above)

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

Revision 16/17's items (`WF8B-S1-*`/`OPUS-R25-*`), continuing the
numbering, all owed to the dedicated fix session tracked within `WF8b`'s
own continued scope (unit, integration, negative, cross-item, manifest,
bundle, approval-binding, and regression coverage, per
`D-Fingerprint-Generalization`'s acceptance criteria). **Corrected this
revision** (resolves `OPUS-R25-009`): items 141-151 as revision 16 stated
them either pinned an unreproducible literal (142), undercounted the
fail-closed matrix (146), or omitted coverage revision 17's own additions
require; restated below in full rather than patched in place, so the list
reads as one coherent set instead of a base list plus scattered
corrections:

141. `workflow-v2-1-core-artifacts.json`'s migrated `plan_stage` section, loaded via `load_plan_stage_classification`, equals `PLAN_STAGE_PROTECTED`/`PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES` exactly, item for item, compared directly rather than via a hardcoded digest literal, before either Python constant is retired as a live default (`WF8B-S1-001`, `OPUS-R25-001`/`-010`, unit/regression) → **owed to `WF8b`**;
142. **(corrected, `OPUS-R25-001`)** `compute_review_content_id_plan_stage_for_work_item(repo_root, base, "workflow-v2-1-core")`, at the *same* base/content item 141 verifies, reproduces the *migrated* digest — never a hardcoded `2b4d2e3b89c2b8f...`/`f7aeff4985...` literal, which this revision's own approval necessarily invalidates the moment it is recorded (`WF8B-S1-001`, `OPUS-R25-001`, regression/approval-binding) → **owed to `WF8b`**;
143. a second fixture work item (its own `plan_path`/`registry_path`/`mapping_path`, each a member of its own `<id>-artifacts.json` `plan_stage.protected_paths`, per resolution step 11) computes a `review_content_id` distinct from `workflow-v2-1-core`'s, whose manifest contains only its own declared files (`WF8B-S1-001`, `OPUS-R25-003`, integration/cross-item) → **owed to `WF8b`**;
144. mutating fixture A's protected file changes only fixture A's `review_content_id`; mutating fixture B's, or an unrelated/excluded path, changes neither (`WF8B-S1-001`, cross-item/negative) → **owed to `WF8b`**;
145. **(extended, `OPUS-R25-013`)** a commit-source computation (`compute_review_content_id_plan_stage_at_commit_for_work_item`) resolves metadata from the given commit, not from a subsequently-edited live `WORKFLOW_STATE.json` — committed one metadata state, edited `WORKFLOW_STATE.json` afterward to point elsewhere, and asserted the commit-source recomputation is unaffected; **and** invoked against a commit predating the schema-version-2 migration raises `MissingPlanStageMetadataError`/`MissingWorkItemArtifactsDeclarationError` (not a crash, not a silent fallback), confirming the deliberate pre-migration boundary rather than an unhandled case (`WF8B-S1-001`, `OPUS-R25-013`, unit/negative, the worktree/commit-source metadata-pinning invariant) → **owed to `WF8b`**;
146. **(corrected, `OPUS-R26-006`, replacing revision 17's own "sixteen"/`(4a-c)` defects — the same class of error `OPUS-R25-009` raised against revision 16, reproduced in the round-17 correction)** each of the first eight fail-closed matrix conditions raises the documented exception, naming the offending work item and field, in independent sub-cases: (1) unknown work item; (2) non-`"process"` type; (3a-c) null `plan_path`/`registry_path`/`mapping_path`, independently; (4a-c) each of the three **fields** (`plan_path`/`registry_path`/`mapping_path`), independently, failing the path-grammar validator against the same representative rule violation, with the minimum rule-violation vector set named separately (absolute path, `../` traversal, non-tracked/symlinked target — at minimum, exercised at least once across the three fields, not a full 3×3 matrix); (5) missing artifacts-declarations file; (6a-c) registry/mapping/artifacts self-declared `work_item_id` mismatch, independently; (7a-b) a declared path not a member of the resolved protected set, and the three paths not pairwise distinct; (8a-c) a non-null `plan_path`/`registry_path`/`mapping_path` claimed by a second work item, independently per field — **seventeen** independent sub-cases total for conditions 1-8 (`WF8B-S1-001`, `OPUS-R25-003`/`-008`/`-009`, `OPUS-R26-006`, negative/exhaustive) → **owed to `WF8b`**;
147. **(extended, `OPUS-R25-002`/`-012`)** condition 9 (registry/plan-title `plan_revision` disagreement) fails closed per work item, independent of any other item's own `plan_revision`; and a `WORKFLOW_STATE.json`/registry `plan_revision` mirror disagreement is rejected by `validate_state` (`PlanRevisionMirrorMismatchError`) before any fingerprint call runs (`OPUS-R25-002`/`-009`, negative) → **owed to `WF8b`**;
148. **(extended, `OPUS-R25-009`)** condition 10 (`UnclassifiedPathError`) fails closed for a genuinely novel path scoped to a *second* fixture item specifically — not only re-verified against `workflow-v2-1-core`'s own existing regression fixtures — confirming the classifier is scoped per item rather than globally (`OPUS-R25-009`, negative) → **owed to `WF8b`**;
149. the CLI's `--work-item-id <second-item>` prints that item's own resolved values end to end, with no `workflow-v2-1-core` literal appearing anywhere in output scoped to a different item; for the **read-only** path, omitting the flag resolves to the live `active_work_item_id`, never a hardcoded default; **and** (extended, `OPUS-R28-010`) `--write-manifest` with `--work-item-id` omitted refuses with a usage error before any directory is created or bound — never silently resolving to and writing the live `active_work_item_id`'s directory (`WF8B-S1-001`, `OPUS-R25-005`, `OPUS-R28-010`, integration/CLI/manifest + negative) → **owed to `WF8b`**;
150. `approval_is_current`/`implementing_entry_reachable`/`verify_post_approval_manifest_match`, exercised against a second fixture item with its own `plan_approval` record, gate independently of `workflow-v2-1-core`'s record and content, with no `plan_revision` parameter passed by either caller (`WF8B-S1-001`, `OPUS-R25-002`, integration/approval-binding) → **owed to `WF8b`**;
151. `write_manifest_with_verified_identifiers` invoked for a second fixture item writes a `MANIFEST.md` whose `work_item_id`/`plan_revision`/`base_commit`/`review_content_id`/`protected_paths` are that item's own, and the bundle's `files/` copies contain only that item's declared protected files (`WF8B-S1-001`, `OPUS-R25-005`, bundle) → **owed to `WF8b`**;
152. **(extended, `OPUS-R25-005`, `OPUS-R26-001`)** `--write-manifest` invoked with `--work-item-id v2-1-dry-run` against a bundle directory whose existing `MANIFEST.md` already names `workflow-v2-1-core` raises `BundleWorkItemMismatchError`, naming both, and leaves the existing `MANIFEST.md` untouched (`OPUS-R25-005`, negative/manifest); **and**, the distinct sub-case `OPUS-R26-001` added: the same invocation against a bundle directory whose existing `MANIFEST.md` is present but declares **no** `work_item_id` at all (this repository's own current `.ai-review/current/MANIFEST.md`, before the one-time migration rebinding step runs) likewise raises `BundleWorkItemMismatchError`, naming "unbound" rather than silently proceeding — see also new test 161, which exercises this same sub-case as a standalone negative test against a byte-copy of the real file → **owed to `WF8b`**;
153. **(extended, `OPUS-R25-012`, `OPUS-R26-002`; corrected, `OPUS-R27-003`/`-005`, replacing the argument-omitted sub-case, which described the pre-`OPUS-R27-003` "resolve once, else live-active" design rather than the required-argument design revision 19 adopts — see test 162, which now exercises the omitted-argument case standalone)** `scripts/prepare-ai-review.sh <base> plan <work-item-id>`, run end to end with its required third argument and no separate manual CLI invocation, writes `ROOT_DIR=.ai-review/<work-item-id>/current` and a `MANIFEST.md` naming that exact `work-item-id` and its own `review_content_id` — both derived from the one value resolved at the top of the script's own logic, never two different rules (`OPUS-R25-012`, `OPUS-R26-002`, integration/bundle) → **owed to `WF8b`**;
154. **(new, `OPUS-R25-004`; corrected, `OPUS-R28-002`)** `/milestone-plan v2-1-dry-run` itself — the **pre-existing non-terminal entry** case, `registry_path`/`mapping_path`/`base_commit` still `null`, `route_work_item`'s resume branch — run end to end through bundle generation, succeeds with no `MissingPlanStageMetadataError`/`MissingWorkItemArtifactsDeclarationError`: the resume branch's new per-field write-or-conflict rule actually populates the four facts a pre-declared entry never got at creation time. **Separately**, a freshly-created process work item (creation branch) run the same way, as an independent case — the two are not interchangeable, since only the second exercises `default_work_item`'s existing write path and only the first exercises the new resume-branch write path. The creation-path ordering (declare paths, then populate registry/mapping/artifacts content, then fingerprint) actually closes the gap that left S1 blocked one error deeper for both cases (`WF8B-S1-001`, `OPUS-R25-004`, `OPUS-R28-002`, integration/end-to-end) → **owed to `WF8b`**;
155. **(new, `OPUS-R25-004`)** the default `plan_stage` template `generate_artifacts_declarations` writes for a fresh work item names that item's own three artifact paths as `protected_paths`, satisfying condition 7 (matrix item 146) by construction at creation time, with no additional edit required before the item's own first plan-stage computation succeeds (`OPUS-R25-004`, unit) → **owed to `WF8b`**;
156. **(new, `OPUS-R25-006`)** `docs/ai-workflow/registry/<work_item_id>-artifacts.json` classifies as implementation-stage *protected* (exact path, checked before the `docs/ai-workflow/registry/` prefix); editing its `plan_stage` key changes plan-stage `review_content_id`; editing its `implementation_stage` key does not — all three asserted directly, correcting the file's own stale `_comment` invariant claim (`OPUS-R25-006`, unit/regression) → **owed to `WF8b`**;
157. **(new, `OPUS-R25-011`)** a `work_item_kind: "synthetic"` item (`v2-1-dry-run`) with `work_item_type: "process"` computes its own distinct plan-stage `review_content_id`, exercising the corrected resolution algorithm against the exact item the superseded code comment named (`WF8B-S1-001`, `OPUS-R25-011`, integration/regression) → **owed to `WF8b`**;
158. **(new, `OPUS-R25-007`)** two work items declaring the same `plan_path` but different `registry_path`/`mapping_path` (a partial, not full-triple, collision) raise `DuplicateWorkItemArtifactPathError` from `resolve_plan_stage_metadata` itself, not only from `validate_state` — exercising the read-path relocation, not merely the write-path backstop (`OPUS-R25-007`, negative) → **owed to `WF8b`**;
159. **(new, `OPUS-R25-014`)** `workflow_test_harness.py`'s `write_plan_docs` emits a valid per-item `<id>-artifacts.json` alongside its five fixture files, consumed successfully by `resolve_plan_stage_metadata` with no override needed for any harness-created item, including `workflow-v2-1-core` itself (`OPUS-R25-014`, unit/harness) → **owed to `WF8b`**;
160. the full existing regression suite (`workflow_fingerprint_test.py`, `workflow_fingerprint_demo_test.py`, `workflow_state_test.py`, `workflow_state_demo_test.py`, `workflow_integration_test.py`, `workflow_test_harness_test.py`) passes unchanged in assertion count, plus items 141-159 above, and S1 (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`), re-attempted from the beginning in a fresh session after this revision and its implementation are both independently reviewed and approved, produces a `v2-1-dry-run`-specific `review_content_id`/manifest/bundle distinct from `workflow-v2-1-core`'s, and the false-positive scenario `docs/ai-workflow/dry-run/WF8B_S1_FINDING_review_content_id_not_generalized.md` describes no longer reproduces (`WF8B-S1-001`, full-suite regression + end-to-end) → **owed to `WF8b`** (items 161-166 below, added by `OPUS-R26-*`/`OPUS-R27-*`/`OPUS-R28-*`/`GPT-R29-*`, are additional independent obligations, not folded into this item's own restated scope — the full-suite pass this item requires additionally includes them, per migration step 10 above, which is now explicit that its own range is 141-166; item 166 was `WF8a-ii`'s own obligation through revision 20, reassigned to this same `WF8b` scope by `GPT-R29-003` since `WF8a-ii` is already `COMPLETE` and cannot be reopened to give it a reachable owner).

Revision 18's items (`OPUS-R26-*`), continuing the numbering — three new
obligations the round-26 review named explicitly, none folded into any
existing item above because each exercises a sub-case distinct from what
141-160 already cover:

161. **(new, `OPUS-R26-001`; extended, `OPUS-R27-004`/`OPUS-R28-008`; corrected, `GPT-R29-002`)** `--write-manifest` against a bundle directory whose existing `MANIFEST.md` is present but declares no `work_item_id` refuses (`BundleWorkItemMismatchError`, naming "unbound") and never silently overwrites, asserted against a byte-copy of this repository's own current `.ai-review/current/MANIFEST.md` — including that the check does not false-positive on the `work_item_id` substring inside line 21's own exclusion-justification prose, confirming the comparison parses the manifest's `field: value` header lines rather than substring-scanning (`OPUS-R26-001`, negative/manifest); **and**, the sub-case corrected `GPT-R29-002` (replacing `OPUS-R27-004`'s "creates and binds" sub-case, which asserted an outcome the manifest algorithm cannot produce — see acceptance criterion 22): `--write-manifest --work-item-id <new-item>`, invoked for a work item whose own scoped bundle directory (`.ai-review/<new-item>/current`) does not yet exist, refuses with `MissingRequiredBundleFileError`, naming the resolved directory and never silently creating it, resolving into, or writing `.ai-review/current` instead; **and** the sub-case `OPUS-R28-008` restated under the same single check, `GPT-R29-002`: `--write-manifest --work-item-id <new-item>` against a scoped bundle directory that already exists, is non-empty, and is missing a required generation file (simulating an interrupted prior `prepare-ai-review.sh` run) likewise refuses with `MissingRequiredBundleFileError`, naming the missing file — the same exception and the same check as the wholly-absent case, not a second mechanism, rather than binding the partial content; **and** the positive case, stated explicitly for the first time (`GPT-R29-002`): `--write-manifest --work-item-id <new-item>` against a scoped bundle directory that already contains the complete required generation file set (produced by a prior `prepare-ai-review.sh` run, or an equivalent full-generation fixture, that did not itself call `--write-manifest`) and has no `MANIFEST.md` yet binds it successfully → **owed to `WF8b`**;
162. **(new, `OPUS-R26-002`; restated, `OPUS-R27-003`/`-005`, replacing the "either writes a matching manifest, or refuses naming both" phrasing, which accepted two mutually exclusive designs and could not fail under a regression to the rejected one)** `scripts/prepare-ai-review.sh <base> plan`, invoked with **no** third argument, refuses immediately — a usage/argument error, before writing any bundle content, `ROOT_DIR`, or manifest — because the plan stage's work-item id is a required argument, never resolved from the live `active_work_item_id`; there is therefore no fallback path left for `ROOT_DIR` and `--write-manifest` to disagree about, asserted as a positive, unambiguous property rather than an "or" (`OPUS-R26-002`, `OPUS-R27-003`/`-005`, integration/bundle) → **owed to `WF8b`**;
163. **(new, `OPUS-R26-003`)** the CLI invoked for a second work item with no `base` positional does not compute against `workflow-v2-1-core`'s base commit: it resolves that item's own `base_commit`, or fails closed (`MissingPlanStageMetadataError`) when it is `null`; and `MANIFEST.md`'s `base_commit` field equals the resolved item's declared value, checked at write time (`OPUS-R26-003`, unit + manifest) → **owed to `WF8b`**.

Round 27's items (`OPUS-R27-*`), continuing the numbering — three new
obligations named explicitly, none folded into any existing item above
because each exercises a scope distinct from what 141-163 already cover:

164. **(new, `OPUS-R27-002`; extended, `OPUS-R28-001`)** `approval_is_current(stage="implementation")` **and** `verify_post_approval_manifest_match(stage="implementation")`, each exercised against a second work item, load that item's own `<id>-artifacts.json` — via `fingerprint.artifacts_path_for_work_item(work_item_id)`, never `DEFAULT_ARTIFACTS_PATH`: deleting the second item's own self-referential `implementation_stage.protected_paths` entry changes only its own implementation-stage `review_content_id` and stales only its own `technical_approval`; editing `workflow-v2-1-core`'s own artifacts file leaves the second item's approval untouched. `verify_post_approval_manifest_match` is the caller with no other coverage in this item — it is the one `/approve-review` step 6a actually invokes post-commit, for either stage (`OPUS-R27-002`, `OPUS-R28-001`, integration/approval-binding) → **owed to `WF8b`**;
165. **(new, `OPUS-R27-003`; extended, `OPUS-R28-004`/`-006`)** `scripts/prepare-ai-review.sh <base> plan <id>` refuses, before generating any bundle content, when the resolved item's own declared `base_commit` (`WORKFLOW_STATE.json`'s `work_items[<id>].base_commit`) disagrees with the resolved `BASE_SHA` (not the raw `<base>` argument — an abbreviated SHA or symbolic ref that `git rev-parse` resolves correctly must still pass; only a genuine disagreement between the two resolved commits refuses), naming both resolved values — the content↔identity binding fail-closed matrix condition 13 already defines, applied here as an early script-level refusal via `fingerprint.resolve_plan_stage_metadata` (never a second, ad hoc metadata reader) rather than only a downstream manifest-write check. **Extended, `OPUS-R28-004`**: migration step 8a's relocation, run against a byte-copy of this repository's own real `.ai-review/current`/`review-bundle.tar.gz`, (a) refuses with both paths named when the destination already exists non-empty rather than nesting silently; (b) the post-move verification catches a simulated partial move (destination missing an expected file) and stops before the rebinding write, leaving the source-or-partial-destination state diagnosable rather than binding it (`OPUS-R27-003`, `OPUS-R28-004`, `OPUS-R28-006`, integration/bundle + negative) → **owed to `WF8b`**;
166. **(new, `OPUS-R27-001`; scope reconciled with criterion 19, `OPUS-R28-003`; ownership reassigned, `GPT-R29-003`)** A grep-based conformance test over `docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json` and the Requirements traceability table above asserts that every `WFR-*` requirement's JSON `description` — **the description field only, never the Checkpoint column, which may legitimately cite a design-doc section instead of or alongside a registry checkpoint id and is explicitly out of scope for this test** — matches its rendered table row (backtick markup, `**` bold markup, em dash normalized to `--`, and trailing `(corrected/extended/new OPUS-R.../GPT-R...)` historical-citation parentheticals stripped from the table row before comparison, case-insensitive — the same normalization every other requirement's JSON description already applies implicitly, confirmed by inspection: no existing `description` field embeds a round citation). All 52 rows are already synced and independently verified by this same round (`OPUS-R28-003`) as part of criterion 19's data fix; this item is the **separate, ongoing** automated regression test that keeps them synced going forward — it does not re-do the one-time sync, which criterion 19 already requires and this fix session already performs — enforcing the "generated view" claim rather than merely asserting it, the same class as items 108/109/123, and what would have caught `OPUS-R27-001` during self-review (`OPUS-R27-001`, `OPUS-R28-003`, documentation-consistency lint) → **owed to `WF8b`** (corrected, `GPT-R29-003`: `OPUS-R28-003` split ownership between this item, kept with `WF8a-ii`, and criterion 19's data-correctness half, satisfied by this revision — but `WF8a-ii` is already `COMPLETE`, and checkpoint completion is never reopened to attach a new obligation to it, leaving this item with no reachable owner at all, exactly the gap the current review's own failure scenario describes: a remediation implementation could pass every other required test while this one is never added, since nothing scheduled it. Reassigned to this same `WF8b` continued-scope set that items 141-165 already belong to and included in migration step 10's required-green range alongside them — the smaller of the finding's two offered fixes, and consistent with `WF8b`, not `WF8a-ii`, being the checkpoint whose own `OPUS-R28-003` fix populated the 52 rows this test verifies).

**Items 167-179 (new, revision 22, `WF8B-002`, `D-Scoped-Remediation-Acceptance`)**:

167. `/accept-milestone` succeeds unchanged when `is_terminal` (`select_next_checkpoint` returns `None`) and phase is `AWAITING_FUNCTIONAL_REVIEW` — the regression guard proving the new guard does not disturb the one path that must never break → WF4a-ii;
168. `/accept-milestone` refuses, naming the actual phase and the outstanding checkpoint, when `select_next_checkpoint` returns a selectable checkpoint id (non-terminal, not blocked) — this exact finding's own reproduction shape → WF4a-ii;
169. `/accept-milestone` refuses identically when `select_next_checkpoint` instead raises `NoCheckpointReadyError` (blocked, non-terminal) — the outstanding checkpoint named is the blocked one, not a selectable one → WF4a-ii;
170. `complete_work_item` itself (not only the `/accept-milestone` command layer) raises `IncompleteOwnCheckpointsError` directly when its own on-disk registry load is non-terminal, independent of `incomplete_children` — a direct unit test, defense-in-depth from the command-level guard → WF4c;
171. `/accept-scoped-remediation` happy path: non-terminal registry, `technical_approval.status == CURRENT`, correct confirmation text → appends exactly one `scoped_remediation_acceptance` entry with the exact ten fields (revision 23, `GPT-R36-003`, field count corrected revision 24, `GPT-R37-003`), sets `phase` to `IMPLEMENTING`, and leaves `checkpoints`/`current_checkpoint_id`/`active_work_item_id`/`plan_approval`/`technical_approval`/`functional_acceptance_status` byte-identical to before the call → WF4c;
172. `/accept-scoped-remediation` refuses when phase is not `AWAITING_FUNCTIONAL_REVIEW` — three distinct negative cases: `IMPLEMENTING`, `AWAITING_TECHNICAL_APPROVAL`, `MILESTONE_COMPLETE` → WF4c;
173. `/accept-scoped-remediation` refuses when the registry is actually terminal (`select_next_checkpoint` returns `None`), naming `/accept-milestone` as the correct command instead — the "reuse of terminal acceptance as scoped acceptance" case, refused structurally by `scoped_remediation_gate_reachable` alone → WF4c;
174. `/accept-scoped-remediation` refuses when `technical_approval.status != "CURRENT"` (a bounded-fix round landed after technical approval but before this command ran) — the superseded-implementation-revision case → WF4c;
175. `validate_user_confirmation`'s `stage="scoped_remediation"` text is non-interchangeable with `stage="acceptance"` text naming the same `work_item_id` — a string satisfying one stage keyword never satisfies the other, extending the existing non-novelty-check property (`OPUS-R10-010`/`GPT-R11-004`) to the fourth `APPROVAL_STAGES` member → WF4a-ii;
176. a second invocation of `/accept-scoped-remediation` immediately after a first success, naming the identical round, with correct current-turn `scoped_remediation` confirmation supplied, is recognized as an exact-round replay before the ordinary phase guard runs (`phase` is now `IMPLEMENTING`) and idempotently reports the existing acceptance commit rather than erroring or duplicating it (revision 24, `GPT-R37-002`, correcting revision 22's "refuses via the ordinary phase guard alone" framing, which never actually reaches a duplicate-detection question since the phase guard alone would already refuse; revision 25, `GPT-R38-002`, further correcting that the replay is recognized only via `resolve_scoped_remediation_round`'s own load-and-compare, never a bare trailer-key match) → WF4c;
177. fresh-session resume: after `/accept-scoped-remediation`, a brand-new process re-reading `WORKFLOW_STATE.json`/the registry from disk alone (no in-memory carryover) re-selects the same outstanding checkpoint via `select_next_checkpoint`/`/bootstrap-workflow-v2`'s own step 3 — no new pointer or marker file exists to read → WF2;
178. `complete_work_item` performs no registry load at all for a work item with `registry_path: null` (the legacy `milestone-8` shape) — vacuously terminal, unchanged behavior from before this fix → WF4c;
179. this exact finding's own end-to-end integration test: an item with `technical_approval` freshly `CURRENT` while a registry checkpoint remains incomplete can never reach `MILESTONE_COMPLETE` via any documented command sequence without first passing through `/accept-scoped-remediation` and returning to `IMPLEMENTING` → WF4c, WF2.

**Items 180-186 (new, revision 23, `GPT-R36-001`/`-002`/`-003`,
`D-Scoped-Remediation-Acceptance`'s hardening; 182-186 amended and
187-190 added in revision 24, `GPT-R37-001`/`-002`/`-003`/`-004`; 176
(above), 182, and 186 further amended and 191-196 added in revision 25,
`GPT-R38-001`/`-002`/`-003`; 189 further amended and 197-206 added in
revision 26, `GPT-R39-001`/`-002`; 185, 199, 200 (replaced), 203, 205,
and 206 further amended and 207-214 added in revision 27, `GPT-R40-001`/
`-002`, the same decision's further hardening)**:

180. `/accept-scoped-remediation` creates a dedicated, metadata-only provenance commit before reporting success, carrying a discoverable `Workflow-Scoped-Remediation-Acceptance: <outstanding_checkpoint_id>/<implementation_revision>` trailer (plus the ordinary `Workflow-Work-Item` trailer naming the work item, revision 24 correction, `GPT-R37-002`); the state write and the commit succeed together or not at all — simulated commit failure leaves neither in effect → WF4c;
181. a fresh session's `/bootstrap-workflow-v2` resumes an outstanding checkpoint only from a *committed* scoped-remediation acceptance — a simulated crash leaving the acceptance as an uncommitted working-tree write is treated exactly as if the acceptance never happened, not as a completed one → WF4c, WF2;
182. `/accept-scoped-remediation` is idempotent for a replay of the exact same accepted round, keyed by `outstanding_checkpoint_id`/`implementation_revision` (a matching trailer is discovered *and* its own recorded fields agree with the live values, per `resolve_scoped_remediation_round`: no new commit created, the existing commit reported, no error), regardless of the current phase — revision 24, `GPT-R37-002`, replacing the checkpoint-only keying and unconditional "differently-accepted round" refusal revision 23 specified (see items 188-189 below for the corrected, round-scoped behavior); revision 25, `GPT-R38-002`, further correcting that a matching trailer alone, without the field comparison, is never sufficient → WF4c;
183. `complete_work_item` raises a dedicated `RegistryCoverageError` (not `IncompleteOwnCheckpointsError`) when called for a registry-backed work item (non-null `registry_path`) whose own registry file fails safe-path resolution, is missing/unreadable, or is malformed/non-object JSON → WF4c;
184. `complete_work_item` raises the same `RegistryCoverageError` when the loaded on-disk registry's own declared `work_item_id` does not match the work item being completed — the foreign/mismatched-registry case → WF4c;
185. the `scoped_remediation_acceptance` entry records `implementation_revision`, `reviewed_implementation_head`, the functional-review checklist's path, a committed blob digest of that checklist, and the exact `Workflow-Functional-Checklist` evidence commit SHA that digest was drawn from, alongside the five fields revision 22 already defined and the new `acceptance_record_version` — a schema assertion on the full eleven-field set (count corrected revision 24, `GPT-R37-003`; eleventh field added revision 27, `GPT-R40-002`) → WF4c;
186. `/accept-scoped-remediation` refuses when the live `reviewed_implementation_head` or the checklist's current committed digest disagree with the values about to be recorded (simulating a checklist edit or a new implementation head landing between functional review and acceptance), naming the expected and current value — this check is reachable at all only once a discoverable checklist-evidence commit for the round exists (revision 25, `GPT-R38-001`; see item 194 below for the refusal when it does not) → WF4c;
187. `complete_work_item`'s signature carries no `registry` parameter at all — a caller cannot substitute a fabricated in-memory dict with a matching `work_item_id` for the authoritative on-disk file, closing the exact bypass class `GPT-R37-001` demonstrated → WF4c;
188. a genuinely new, later-reviewed remediation round (a distinct `implementation_revision`, reached via a fresh `apply_technical_approval` and functional review) against the same still-outstanding checkpoint `/accept-scoped-remediation` already accepted once is permitted, appending a second `scoped_remediation_acceptance` entry and creating a second provenance commit, rather than refused as a conflicting duplicate → WF4c;
189. a same-round collision — the discovered trailer's `outstanding_checkpoint_id`/`implementation_revision` match the round about to be recorded, but the existing commit's recorded `reviewed_implementation_head`, `technical_approval_review_content_id`, `functional_checklist_path`, `functional_checklist_blob`, or `active_work_item_id_at_acceptance` disagree with the live values (revision 26, `GPT-R39-002`, widened from the three-field subset revision 25 compared) — is refused as a conflicting duplicate, naming the already-recorded commit and the specific disagreeing field, distinct from the exact-replay case in item 182 above → WF4c;
190. `/accept-scoped-remediation` refuses outright, before computing or recording any blob, when the functional-review checklist path has a staged or unstaged working-tree change relative to `HEAD` — both at the guard's first read and, separately, immediately before the provenance commit if the tree became dirty in between → WF4c;
191. end to end: `/prepare-functional-review`'s checklist-evidence commit followed by `/accept-scoped-remediation` lets the exact `WF8b` checklist flow — write checklist, commit it, walk it, accept it — reach scoped acceptance starting from a clean tree, with no manual out-of-contract commit anywhere in the sequence → WF4c, WF-M8b;
192. `resolve_scoped_remediation_round` reports `ExactReplay` only after loading and comparing the discovered commit's own recorded `scoped_remediation_acceptance` entry against the live round's fields — a hand-edited or corrupted state entry whose trailer key matches but whose recorded fields disagree is refused as a conflicting duplicate, not silently treated as an idempotent replay → WF4c;
193. `resolve_scoped_remediation_round` reports `AmbiguousHistory` when `discover_scoped_remediation_commits` itself raises `AmbiguousScopedRemediationTrailerError` (more than one first-parent-reachable commit for the same round key), refused identically whether encountered by the entry guard's own call or the provenance-commit step's pre-write re-check → WF4c;
194. `/accept-scoped-remediation` refuses outright, naming the missing round key and `/prepare-functional-review` as the remedy, when no `Workflow-Functional-Checklist` evidence commit is discoverable for the exact live round — checked, and failing, before the plain dirty-working-tree refusal (item 190) is ever reached → WF4c;
195. `/prepare-functional-review`'s checklist-evidence commit is idempotent by content: rerunning with an unchanged checklist creates nothing new and reports the previously-created commit; rerunning with a changed checklist for the same round creates a new, distinct commit, discoverable ahead of the earlier one; a simulated interruption between the checklist file write and the commit is safe to retry with no duplicate commit produced → WF4c, WF-M8b;
196. current-turn user confirmation for `stage="scoped_remediation"` is validated before any replay classification runs — missing, stale, wrong-work-item, or wrong-stage (e.g. `"acceptance"`) confirmation text is refused identically whether the live round would otherwise resolve to `ExactReplay` or proceed through the ordinary entry guard as a new acceptance → WF4c;
197. two `Workflow-Functional-Checklist` evidence commits for the same round sharing byte-identical checklist content (a genuine duplicate, not a revision) both first-parent reachable are refused as ambiguous by `discover_functional_checklist_commits`/`discover_current_functional_checklist_evidence`, exactly as `discover_scoped_remediation_commits` refuses its own duplicate trailer, never silently picked between (revision 26, `GPT-R39-001`) → WF-M8b;
198. two `Workflow-Functional-Checklist` evidence commits for the same round with genuinely distinct checklist content (an older commit, then a corrected, newer commit) coexist without ambiguity — each discoverable by its own distinct trailer value — and `discover_current_functional_checklist_evidence` reports the newer as the round's current evidence (revision 26, `GPT-R39-001`) → WF-M8b;
199. `/accept-scoped-remediation`'s pre-commit evidence guard and `apply_scoped_remediation_acceptance`'s recorded `functional_checklist_blob`/`functional_checklist_evidence_commit` bind to the round's current checklist-evidence commit (item 198's newer one) **only when the current-turn `scoped_remediation` confirmation itself names that current commit** (revision 27, `GPT-R40-001`, narrowing revision 26's unconditional binding claim); a confirmation still naming a stale, superseded evidence commit for the same round is refused rather than silently upgraded → WF4c;
200. **(replaced, revision 27, `GPT-R40-001`, reversing revision 26's claim)** a `Workflow-Functional-Checklist` evidence commit created after `/prepare-functional-review` already reported an earlier commit's SHA to the user, and after the user already gave a `scoped_remediation` confirmation naming that earlier commit, does **not** become authorized for that confirmation: a subsequent `/accept-scoped-remediation` invocation supplying the same, unmodified confirmation text refuses as a stale confirmation, naming both the confirmed and the current evidence identity, rather than silently binding to the newer evidence — discovery may still identify the newer commit as the round's *current* evidence for `/prepare-functional-review`'s own idempotency purposes (item 195, unchanged), but current-ness alone never authorizes an existing confirmation to it → WF4c, WF-M8b;
201. `resolve_scoped_remediation_round` refuses as `ConflictingDuplicate` when the discovered commit's recorded `active_work_item_id_at_acceptance` disagrees with the live global `active_work_item_id` at classification time even though the round's other fields all agree — the changed-active-pointer case (revision 26, `GPT-R39-002`) → WF4c;
202. `resolve_scoped_remediation_round` refuses as `ConflictingDuplicate` when the discovered commit's recorded `functional_checklist_path` disagrees with the live fixed constant — the wrong-checklist-path case (revision 26, `GPT-R39-002`) → WF4c;
203. `resolve_scoped_remediation_round` reports `MalformedAcceptanceRecord` when the discovered commit's own `scoped_remediation_acceptance` entry declares an `acceptance_record_version` other than `2` (version updated revision 27, `GPT-R40-002`; no version-1 record exists anywhere, since nothing implementing this mechanism has shipped yet), is missing a documented field or carries an unexpected extra one, or has a missing/malformed `recorded_at`/`user_confirmation` — never silently treated as a replay or an ordinary conflicting duplicate (revision 26, `GPT-R39-002`) → WF4c;
204. `resolve_scoped_remediation_round` refuses as `ConflictingDuplicate` when the discovered commit's own recorded `outstanding_checkpoint_id`/`implementation_revision` fields disagree with the round key that discovered it — the hand-edited-entry-vs-trailer-key mismatch case (revision 26, `GPT-R39-002`) → WF4c;
205. an exact replay with every one of the eight canonically compared fields (`outstanding_checkpoint_id`, `implementation_revision`, `reviewed_implementation_head`, `technical_approval_review_content_id`, `functional_checklist_path`, `functional_checklist_blob`, `functional_checklist_evidence_commit`, `active_work_item_id_at_acceptance`) matching the live values is still reported `ExactReplay` — a regression guard proving the widened comparison does not turn a genuine replay into a false conflicting-duplicate refusal (revision 26, `GPT-R39-002`; widened to eight fields revision 27, `GPT-R40-002`) → WF4c;
206. this exact `WF8b` continued-scope flow, end to end, with a checklist correction between preparation and acceptance (two `/prepare-functional-review` invocations producing two distinct evidence commits for the same round): the user's first confirmation, naming the original evidence commit, is refused once the corrected commit becomes current; only after a second `/prepare-functional-review` report and a second, explicit `scoped_remediation` confirmation naming the corrected commit does scoped acceptance succeed, bound to the corrected evidence — no manual out-of-contract commit, no false ambiguity, no false replay, and no silent binding to unreviewed evidence (revision 26, `GPT-R39-001`/`-002`; corrected revision 27, `GPT-R40-001`, to require the second confirmation rather than assume discovery alone suffices) → WF4c, WF-M8b.

**Items 207-214 (new, revision 27, `GPT-R40-001`/`-002`, `D-Scoped-Remediation-Acceptance`'s evidence-binding hardening)**:

207. `/accept-scoped-remediation`'s confirmation-evidence-binding check succeeds, and acceptance proceeds, when the `scoped_remediation` confirmation names evidence commit A's exact SHA and blob and A remains the round's current (only) checklist-evidence commit — the ordinary happy path, unaffected by this revision → WF4c;
208. a second, distinct `Workflow-Functional-Checklist` evidence commit B, created for the same round after the user's confirmation named commit A, makes that confirmation stale: `/accept-scoped-remediation` refuses, naming both A (confirmed) and B (current), rather than silently binding to B — this exact finding's own reproduction (`GPT-R40-001`) → WF4c;
209. after the user receives a fresh `/prepare-functional-review` report for commit B and supplies a new `scoped_remediation` confirmation naming B's exact SHA and blob, `/accept-scoped-remediation` succeeds and binds to B — proving item 208's refusal is not a permanent block, only a requirement for a fresh, explicitly re-bound confirmation → WF4c;
210. `parse_scoped_remediation_confirmation_binding_fields` refuses a confirmation missing either the `Functional checklist evidence commit:` or `Functional checklist evidence blob:` field, or containing a malformed (non-40-hex) value for either, naming which field failed — checked before the registry loads, identically for a first execution and a replay → WF4c;
211. the pre-commit evidence guard refuses when a confirmation names a commit SHA that is discoverable for the round but whose actual `git rev-parse <commit>:<functional_checklist_path>` content disagrees with both the confirmed blob and the trailer's own embedded blob component — the hand-crafted-trailer case (`GPT-R40-001`) → WF4c;
212. the pre-commit evidence guard refuses when a confirmation names an evidence commit SHA that does not belong to the exact live round at all (wrong `work_item_id`, wrong `implementation_revision`, or simply not a `Workflow-Functional-Checklist` commit) — refused identically to the missing-evidence case, naming the confirmed commit and the expected round key → WF4c;
213. `resolve_scoped_remediation_round`'s exact-replay comparison validates the recorded `functional_checklist_evidence_commit` as an eighth canonical field, not only `functional_checklist_blob` — a hand-edited or corrupted record whose `functional_checklist_evidence_commit` disagrees with what a genuine replay would record right now, while every other field agrees, is refused as a conflicting duplicate rather than reported `ExactReplay` (`GPT-R40-002`) → WF4c;
214. this exact `WF8b` continued-scope flow's own end-to-end reproduction of the finding's failure scenario: a checklist correction landing between the user's review and `/accept-scoped-remediation`'s invocation is refused rather than silently accepted, and only a second, fresh confirmation explicitly naming the corrected commit reaches scoped acceptance — the full `GPT-R40-001` failure scenario, defeated → WF4c, WF-M8b.

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

## Self-review notes (revision 19)

- **This revision's migration step 2** (transcribing `PLAN_STAGE_PROTECTED`/
  `PLAN_STAGE_EXCLUDED_PATHS`/`PLAN_STAGE_EXCLUDED_PREFIXES`'s current
  members into the migrated `workflow-v2-1-core-artifacts.json`) is a
  manual transcription, not a mechanically generated one — a transcription
  error would silently produce a *different* resolved set for
  `workflow-v2-1-core`, changing its `review_content_id` without any design
  intent behind the change. This is exactly why acceptance criterion 1 and
  missing-test items 141-142 are the fix session's own **first**, blocking
  step, verified before any other code change lands, rather than assumed
  safe because it "should just be a copy."
- **This revision does not retroactively document `OPUS-R24-*`'s
  `docs/ai-workflow/dry-run/` exclusion in D-Fingerprint's own prose** (see
  the "Round 25 finding disposition" note above) — that gap predates this
  revision, is unrelated to `WF8B-S1-001`'s root cause, and fixing it here
  would blur this revision's own scope-discipline boundary. Flagged
  explicitly so it is not mistaken for an oversight of this revision
  specifically, and left for whichever future revision next touches
  D-Fingerprint's prose for an unrelated reason.
- **(standing invariant, first stated revision 16, corrected from "this
  revision" phrasing, `OPUS-R28-012`) Every revision of `WF8B-S1-001`'s
  design, from revision 16 through the current one, specifies but does not
  implement the fix** — consistent with this document's own
  plan/implementation separation (`CLAUDE.md`'s git restrictions,
  `MILESTONE_WORKFLOW.md`'s `PLANNING` vs. `IMPLEMENTING` states): every
  file each such revision edits (`WORKFLOW_V2_PLAN.md`, the registry's
  `plan_revision` field, the requirements mapping) is plan-stage content;
  no file under `scripts/` or `.claude/commands/` changes. The dedicated
  fix session that follows approval is where
  `scripts/workflow_fingerprint.py`/`scripts/workflow_state.py` actually
  change, under the ordinary implementation-review cycle. (This bullet
  previously read "this revision," ambiguous as to which — the same
  drift the two duplicated `active_work_item_id` paragraphs below had;
  restated once, here, as the standing property it actually is, rather
  than re-dated at every revision bump.)
- **`base_commit`'s own writer, named as a deliberately out-of-scope gap in
  revision 17, is closed in revision 18** (superseded note, kept for the
  historical record rather than deleted — the original text is quoted
  below): `OPUS-R26-003` found this gap load-bearing, not merely deferred
  — with no resolved per-item `base_commit` to fall back to, the CLI's
  `base` positional kept a hardcoded literal naming `workflow-v2-1-core`'s
  own base commit, exactly the defect class this whole finding exists to
  remove. `route_work_item`/`default_work_item` now take `base_commit` as
  a parameter (the "Creation path" subsection), and the `base` positional
  resolves from it (the "Effect on `--work-item-id`" subsection). Revision
  17's original note: "`base_commit`'s own writer is a real, pre-existing
  gap this revision does not close (`D-Fingerprint-Generalization`'s
  'Creation path' section, resolving `OPUS-R25-004`, states this
  explicitly rather than silently leaving it implied): `route_work_item`
  does not take `base_commit` as a parameter today, and
  `workflow-v2-1-core`'s own value was set outside that function's normal
  path. Scoped out deliberately — the follow-up review request's own item
  4 lists `plan_path`/`registry_path`/`mapping_path`/`plan_revision`/the
  artifacts declaration/bundle-directory identity as what `/milestone-plan`
  must initialize, and does not list `base_commit`. Naming the gap here
  rather than quietly working around it keeps a future reviewer from
  assuming it was already handled." — that last sentence's own judgment
  proved correct: naming it here is exactly what let `OPUS-R26-003` find it
  rather than have to rediscover it.
- **This revision found and corrected a genuine wording bug in its own
  prior text while extending it, not one the external review named**:
  revision 16's `D3` validator-rules paragraph stated the `mapping_path`/
  `registry_path` nullability-agreement rule backwards ("`mapping_path`
  non-null when `registry_path` is null, or null when `registry_path` is
  non-null" describes disagreement, not the agreement the rule actually
  requires). Caught during this revision's own re-reading of the paragraph
  it was extending, not flagged by `OPUS-R25-*` — recorded here because a
  self-caught defect in already-"approved-pending" text is exactly the
  kind of thing a later reader would otherwise have to rediscover the hard
  way.
- **The corrected design is materially larger than revision 16's** (twelve
  fail-closed conditions instead of nine, nine new exception classes
  instead of five, twenty test items instead of eleven, two new
  requirements). This is not scope creep relative to `WF8B-S1-001` — every
  addition traces to a specific `OPUS-R25-*` finding validated against the
  actual repository (see the disposition table above), not to a
  speculative hardening pass. Worth stating plainly rather than letting the
  size increase go unremarked: a reviewer comparing revision 16 and 17 side
  by side should expect roughly double the design text, all of it load-
  bearing.
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

**Revision 18's own notes** (`OPUS-R26-*`):

- **The pattern across all three `HIGH` findings this round is the same
  one**: a corrected mechanism left exactly one branch of its own fail-closed
  matrix unhandled (absent, differently-named, but not "present and
  unbound" — `-001`), one resolution rule stated as two ("or" — `-002`), or
  one literal retired while a structurally identical sibling literal (the
  `base` positional) survived unexamined (`-003`). None of the three
  required reopening any mechanism revision 16/17 froze; each is exactly
  the class of gap this whole finding exists to eliminate, recurring one
  layer deeper than the layer already fixed. Worth stating plainly, since
  it is the same shape `WF8B-S1-001` itself named and the same shape
  `OPUS-R25-005`/`-012` already partially closed: a corrected default is
  not the same claim as an exhaustively corrected default, and this
  revision's own job was to find the remaining exceptions to that claim,
  not to re-litigate the claim itself.
- **`OPUS-R26-005`'s "mirror the same additions into
  `workflow-v2-1-core-mapping.json`" instruction was reasoned as satisfied
  without a JSON edit — and that reasoning was half right, half wrong,
  corrected this revision (`OPUS-R27-001`)**: the mapping file's own schema
  (`D4b`, unchanged since revision 8) carries only `{description,
  checkpoint_ids}` per requirement — no per-test evidence field exists for
  any requirement, not only WFR-47/-49/-50 — so there was, and is, nothing
  in that file's current format for a *test-item* addition to mirror into;
  that half of the reasoning is correct and unchanged. What was wrong was
  the separate, unstated assumption that the JSON's `description` values
  were therefore already current: `OPUS-R27-001` found six table rows
  rewritten in revision 18 (`WFR-47` through `WFR-52`) against a JSON left
  completely untouched — `WFR-47`'s JSON description did not mention
  `base_commit` at all. Revision 19 syncs all six `description` values to
  their table-row content and adds a grep-based conformance test (item 166)
  so the two kinds of "mirroring" (test-evidence, which the schema has no
  field for; description content, which it does) are never conflated
  again. Recorded here, rather than silently corrected, so a future reader
  sees both what was right and what was wrong in the original reasoning.
- **This revision, like revision 17, specifies but does not implement**
  every `OPUS-R26-*` correction: no file under `scripts/` or
  `.claude/commands/` is edited. The dedicated fix session that follows
  approval implements all of revision 17's and revision 18's corrections
  together, as one coherent design, under the ordinary
  implementation-review/technical-approval cycle — there is no partial
  implementation of revision 17 to reconcile against, since revision 17
  itself was never approved.
**Revision 19's own notes** (`OPUS-R27-*`):

- **Two of this round's three `HIGH` findings (`-002`, `-003`) are the same
  "one literal retired while a structurally identical sibling survived
  unexamined" pattern `OPUS-R26-003` first named for the `base` positional**
  — this time for `approval_review_content_id`'s implementation-stage
  `artifacts_path` default, and for `prepare-ai-review.sh`'s own directory
  resolution once its "or" was fixed but its consequences weren't traced
  through. Worth stating plainly a second time: retiring a literal at one
  call site does not retire the *class* of literal, and this revision's own
  job, like revision 18's, was to keep looking for the remaining instances
  rather than declare the class closed after the first fix.
- **This revision chose a structurally different fix for `OPUS-R27-003`
  than the "resolve once, else live-active" shape revision 18 used**: the
  work-item id is now a *required* argument for the plan stage, not a
  resolved one with a live-state fallback. The review itself offered this
  as an equivalent option ("make the work-item id a required argument …
  or, equivalently, cross-check …"); this revision does both — required
  argument *and* the `base_commit` cross-check — because the two close
  different halves of the same gap (which item; which content) and neither
  alone closes both. This is a larger change to `prepare-ai-review.sh`'s
  own contract than a minimal patch would have made, so it is called out
  explicitly rather than left for a reader to notice by diffing against
  revision 18's text.
- **Missing test 162's restatement is a direct consequence of the
  `OPUS-R27-003` design choice above, not an independent judgment call**:
  the review's own suggested test text assumed the "resolve once, else
  live-active" shape was kept and asked for a positive-plus-negative
  assertion of single resolution. Once the work-item id became a required
  argument instead, the fallback branch the suggested test was written to
  distinguish no longer exists, so item 162 is restated as the simpler
  property that actually corresponds to what was built — recorded here so
  a future round doesn't read the divergence from the review's literal
  suggested wording as an oversight.
- **This revision, like revisions 17 and 18, specifies but does not
  implement** every `OPUS-R27-*` correction: no file under `scripts/` or
  `.claude/commands/` is edited. The dedicated fix session that follows
  approval implements revisions 17, 18, and 19's corrections together, as
  one coherent design, under the ordinary implementation-review/technical-
  approval cycle.
- **`active_work_item_id` remains `v2-1-dry-run`, `WF8b` remains
  `IN_PROGRESS`, and S1 remains unexecuted** — corrected, `OPUS-R28-012`,
  from two separately-drifting paragraphs (one under "Revision 18's own
  notes," one here) that said the same thing twice, into this single
  statement of the invariant: it has held, unchanged, across every review
  and revision round from 17 through the current one (verified against the
  working diff of `WORKFLOW_STATE.json` before each round's own review
  began, not merely asserted each time) — `v2-1-dry-run`'s `phase:
  "PLANNING"`, `plan_revision: 1`, empty `checkpoints`, `base_commit: null`
  entry, exactly as WF8b's own entry step (`9317b1c`) created it.

**Revision 20's own notes** (`OPUS-R28-*`):

- **This round's dominant pattern was the same "one instance retired while
  a structurally identical sibling survived" shape `OPUS-R26-003` first
  named, recurring twice more**: `OPUS-R28-001` (the implementation-stage
  `artifacts_path` caller list named the wrong sibling —
  `implementing_entry_reachable` instead of
  `verify_post_approval_manifest_match`) and `OPUS-R28-010` (the CLI's
  `--work-item-id` live default, safe on the read-only path, was left
  unexamined on the write path after `OPUS-R27-004` turned that same write
  path into a directory creator). Recorded here because it is now the
  fourth and fifth instance across four consecutive review rounds
  (`OPUS-R26-003`, `-27-002`, `-27-003`, `-28-001`, `-28-010`) — worth
  treating as a standing review heuristic for the fix session, not just a
  pattern to note in passing: whenever a literal or a default is retired at
  one call site, grep for every other caller of the same underlying
  function before declaring the class closed.
- **`OPUS-R28-002` is the finding that actually decides whether S1 can run**
  and is resolved by generalizing `route_work_item`'s resume branch (the
  finding's own preferred option (a)), not by hand-backfilling
  `v2-1-dry-run`'s three null facts (option (b)). The smaller-looking option
  (b) was rejected specifically because it is smaller only for this one
  item — it leaves the same gap open for the next process work item created
  the same way, which is a worse outcome than the larger, generalizing fix.
- **`OPUS-R28-003`'s mapping-JSON sync was extended from the six rows
  `OPUS-R27-001` covered to all 52** — 15 further rows had diverged,
  independently verified by direct comparison against the table under the
  exact normalization missing test 166 states (not merely re-asserted). The
  Checkpoint column was deliberately left out of scope: several rows cite a
  design-doc section instead of, or alongside, a registry checkpoint id
  (`WFR-29`'s "D3, D2" being the sharpest example), which
  `checkpoint_ids`'s own validator-enforced, checkpoint-only schema cannot
  represent — stated as a scope decision, not silently left inconsistent.
- **`OPUS-R28-004`/`-005`'s migration step (renumbered `6a` → `8a`) took the
  finding's option (b) for the feedback-directory question**: `.ai-review/feedback`
  stays flat rather than being relocated alongside `.ai-review/current`,
  because it is stage-agnostic and step 8 retains the flat layout for every
  non-`plan` stage — relocating it would have split feedback from the still-flat
  implementation/post-fix/functional-review bundles the moment the step
  ran. The larger option (a), scoping every stage's bundle directory, is
  not taken; `resolve_feedback_dir`'s scoped branch remains correctly
  unreachable until a future revision scopes every stage, which this one
  does not attempt.
- **This revision specifies but does not implement** any `OPUS-R28-*`
  correction, consistent with the standing invariant restated once, above,
  rather than re-asserted per revision: no file under `scripts/` or
  `.claude/commands/` is edited by this revision itself — the dedicated fix
  session that follows approval implements revisions 17 through 20's
  corrections together, as one coherent design, under the ordinary
  implementation-review/technical-approval cycle.
- `active_work_item_id` remains `v2-1-dry-run`, `WF8b` remains
  `IN_PROGRESS`, and S1 remains unexecuted — covered by the merged
  standing-invariant statement above (`OPUS-R28-012`), not restated a third
  time here.

**Revision 21's own notes** (`GPT-R29-*`):

- **Both `HIGH` findings this round are the same underlying shape as the
  ones the last several rounds already named — a corrected mechanism whose
  own internal pieces disagreed with each other**: `GPT-R29-001`'s resolver
  read a fact in step 5 it never returned in step 13, so every consumer
  described elsewhere in this same section as reading that fact through the
  resolver had nothing to actually read; `GPT-R29-002`'s bind precondition
  asserted an outcome ("creates and binds a brand-new directory") that the
  very same function's required-file check makes impossible to reach. In
  both cases the fix was not new mechanism but internal consistency: make
  the resolver's own return value match what its own prose already claimed
  it did, and make the bind precondition a single check instead of two
  that quietly contradicted each other.
- **`GPT-R29-002`'s fix removes capability revision 19 (`OPUS-R27-004`)
  added, rather than repairing it** — worth stating plainly, since every
  other correction across rounds 25-28 extended or narrowed a mechanism
  without retracting one outright. A standalone `--write-manifest` can no
  longer conjure a new, empty, bound bundle directory into existence; it
  can only bind a directory some other step already fully populated. This
  is a smaller, not a larger, surface than revision 19 specified — the
  finding's own alternative (making `--write-manifest` "own the complete
  bundle-generation operation") was available and was not taken, since it
  would blur the generator/binder separation every revision since the
  original prototype has kept.
- **`GPT-R29-003` is resolved by moving an obligation, not by adding
  mechanism**: item 166 already existed, already had a clear specification,
  and already had a clear original owner (`WF8a-ii`) — the defect was
  purely that the owner had since become unreachable (`COMPLETE`, never
  reopened) while nothing updated the assignment to match. The same
  "checkpoint completion is not revoked" rule this design already applies
  to `WF4a-i` (see "Relationship to `WF4a-i`" above) is what makes
  reassignment, not reopening, the correct fix here too.
- **This revision, like revisions 17 through 20, specifies but does not
  implement** any `GPT-R29-*` correction: no file under `scripts/` or
  `.claude/commands/` is edited. The dedicated fix session that follows
  approval implements revisions 17 through 21's corrections together, as
  one coherent design, under the ordinary implementation-review/
  technical-approval cycle.
- `active_work_item_id` remains `v2-1-dry-run`, `WF8b` remains
  `IN_PROGRESS`, and S1 remains unexecuted — covered by the merged
  standing-invariant statement above (`OPUS-R28-012`), verified again
  against this round's own bundle before this review began, not restated a
  fourth time here.

**Revision 22's own notes** (`WF8B-002`, self-discovered):

- **This revision is deliberately smaller than the finding it fixes
  proposed** — see "WF8b finding disposition (revision 21 → 22)"'s own
  "Critical evaluation of the finding's own proposed design" subsection
  above for the full reasoning. In short: the finding's own proposed new
  phase, entered directly from `apply_technical_approval`, would have
  silently skipped functional review for every future continued-scope
  round — the opposite of what actually, correctly happened for the very
  remediation this finding is about. No new phase is added here; the
  terminal/non-terminal decision moves to each accept command's own entry
  guard instead, recomputed fresh every time from `select_next_checkpoint`
  — the same discriminator the finding itself names.
- **The finding's own `/accept-milestone` guard proposal (`phase ==
  AWAITING_USER_ACCEPTANCE`) is flagged, not adopted as written**: no
  function in this codebase has ever written that phase value (confirmed
  by grep) — a real, pre-existing gap predating this entire project, not
  introduced or widened here. Adopting the finding's guard literally would
  have made `/accept-milestone` permanently unreachable for every terminal
  item the moment this fix landed, not only `WF8b`'s. The guard specified
  here checks the phase this repository's tooling actually produces
  (`AWAITING_FUNCTIONAL_REVIEW`), while still accepting
  `AWAITING_USER_ACCEPTANCE` for forward compatibility should a future,
  separate fix ever give it a real writer.
- **This revision, like revisions 16-21, specifies but does not implement**
  the fix: no file under `scripts/` or `.claude/commands/` is edited. The
  dedicated fix session that follows approval implements it under the
  ordinary implementation-review/technical-approval cycle.
- `active_work_item_id` remains `v2-1-dry-run`, `WF8b` remains absent from
  `checkpoints` (not `COMPLETE`), and S1 remains unexecuted —
  `docs/ai-workflow/WORKFLOW_STATE.json`'s `technical_approval`/
  `checkpoints`/`functional_acceptance_status` are untouched by this
  revision; only `phase` (to `AWAITING_EXTERNAL_PLAN_REVIEW`, for this
  plan revision's own review cycle), `plan_revision`, `state_revision`,
  and `last_transition` change, exactly mirroring how revisions 16-21
  themselves cycled the item's `phase` through a fresh plan-review round
  while `WF8b` sat untouched throughout.

## Explicit non-goals (unchanged from round 5)

No metrics/hooks/status-line/context-governance/subagent-routing work in
this milestone (deferred to Workflow v2.2, now with an explicit handoff);
no `BOOTSTRAP` approval basis.
