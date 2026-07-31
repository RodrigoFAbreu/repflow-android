# WF8b finding — plan-stage `review_content_id`/`MANIFEST.md` path is not generalized per work item

**Status:** BLOCKING for WF8b. Discovered while attempting S1 (`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`).
**Scenario:** S1 — New synthetic work item planning (`/milestone-plan v2-1-dry-run`).
**Outcome of the attempt:** S1 was **not executed**. No plan document, registry,
mapping, bundle, or `MANIFEST.md` was created for `v2-1-dry-run`. This finding
was captured before any file under `docs/ai-workflow/dry-run/scratch/`,
`docs/ai-workflow/registry/`, or `docs/ai-workflow/requirements/` was written,
and before `./scripts/prepare-ai-review.sh` was invoked.

## Reproduction

```
python3 scripts/workflow_fingerprint.py 162154d3e5e10eb65e109833acae4b4fb01fc5d6 --work-item-id v2-1-dry-run
```

Read-only invocation (no `--write-manifest`), run against the repository at
its current, clean HEAD (`4a8a973`).

### Observed output

```
=== compute_review_content_id_plan_stage ===
base_commit (resolved, full): 162154d3e5e10eb65e109833acae4b4fb01fc5d6
plan_revision (from registry JSON, cross-checked against plan title): 15
review_content_id: 2b4d2e3b89c2b8f243d89fcfb90fded67cc9cd003ba5feff3751d455fb3e49bf
protected_paths: ['docs/TECHNICAL_DECISIONS.md', 'docs/ai-workflow/WORKFLOW_V2_AUDIT.md', 'docs/ai-workflow/WORKFLOW_V2_PLAN.md', 'docs/ai-workflow/registry/workflow-v2-1-core-registry.json', 'docs/ai-workflow/requirements/workflow-v2-1-core-mapping.json']
excluded_paths: [... workflow-v2-1-core's own excluded set ...]
excluded_prefixes: ['.claude/commands/', '.github/', 'app/', 'config/', 'docs/adr/', 'docs/agent-context/', 'docs/ai-workflow/archive/', 'docs/ai-workflow/dry-run/', 'docs/ai-workflow/registry/', 'docs/ai-workflow/requirements/', 'docs/improvements/', 'docs/milestones/', 'gradle/', 'scripts/']
```

Despite passing `--work-item-id v2-1-dry-run`, every printed value is
`workflow-v2-1-core`'s own: the same `plan_revision: 15`, the same 5
`protected_paths`, the same `review_content_id` value already on record as
`workflow-v2-1-core`'s current approved plan-stage identifier
(`plan_approval.approved_review_content_id` in
`docs/ai-workflow/WORKFLOW_STATE.json`).

## Root cause — exact hardcoded constants and CLI flow

`scripts/workflow_fingerprint.py`:

- **L468–469** — `DEFAULT_REGISTRY_PATH`/`DEFAULT_PLAN_PATH` are literal
  paths to `workflow-v2-1-core`'s own registry and plan document; used as
  the defaults for `load_plan_revision()` (L472–503), which nothing in
  `__main__` overrides.
- **L515 (`PLAN_STAGE_PROTECTED`)** — a frozen `frozenset` naming exactly
  `workflow-v2-1-core`'s own 5 protected files (`WORKFLOW_V2_PLAN.md`,
  `WORKFLOW_V2_AUDIT.md`, `TECHNICAL_DECISIONS.md`, its registry JSON, its
  mapping JSON). This is the default `protected` parameter for
  `compute_review_content_id_plan_stage`/`write_manifest_with_verified_identifiers`,
  and nothing outside the hermetic test suite ever passes a different value.
- **L1730** — the CLI defines `--work-item-id` with
  `default="workflow-v2-1-core"`, which reads as a generalized per-item
  selector.
- **L1749–1750** — but the CLI's own read-only call into
  `compute_review_content_id_plan_stage` hardcodes
  `work_item_type="process", work_item_id="workflow-v2-1-core"` literally,
  never `args.work_item_id`.
- **L1773** — the `--write-manifest` call into
  `write_manifest_with_verified_identifiers` hardcodes the same two literals
  again.
- The **only** place `args.work_item_id` is actually threaded through is
  `resolve_bundle_dir(repo_root, args.work_item_id)` — which bundle
  *directory* to read/write, not which content is protected.

So `--work-item-id` currently controls **only** where the manifest file is
written, never what it means. It reads as a generalized flag and behaves as
a no-op for the one thing (`review_content_id`) the manifest exists to bind.

## Why the resulting manifest would bind to `workflow-v2-1-core`, not `v2-1-dry-run`

If S1 had proceeded — creating
`docs/ai-workflow/dry-run/v2-1-dry-run-plan.md` plus
`docs/ai-workflow/registry/v2-1-dry-run-registry.json` and
`docs/ai-workflow/requirements/v2-1-dry-run-mapping.json` — then, from
`workflow-v2-1-core`'s own hardcoded protected/excluded sets:

- `docs/ai-workflow/dry-run/` is (as of `4a8a973`, the immediately prior
  commit) a plan-stage **excluded prefix**.
- `docs/ai-workflow/registry/` and `docs/ai-workflow/requirements/` are
  **excluded prefixes** too (only the two named `workflow-v2-1-core` files
  under them are individually protected).

So all three newly created files would classify as `excluded`, not
`protected` — `classify_path` would not raise `UnclassifiedPathError`, and
`--write-manifest` would succeed and produce a `MANIFEST.md` whose
`review_content_id` is **byte-identical** to `workflow-v2-1-core`'s current
value (`2b4d2e3b89c2b8f...`), because the hashed projection never touched
any of the three new files at all.

## The false-positive success scenario

S1's own stated pass/fail evidence
(`docs/ai-workflow/dry-run/WF8B_SCENARIOS.md`) is: *"bundle exists at the
reported path; `MANIFEST.md`'s recorded `bundle_id`/`review_content_id`
match a fresh recomputation."* With the current tool, that check would
**trivially pass** — the fresh recomputation and the recorded value would
agree — while proving nothing about `v2-1-dry-run`'s actual plan content,
because neither ever depended on it. A reviewer approving the bundle would
be approving a `review_content_id` that does not cover the plan document,
registry, or mapping they are meant to be reviewing.

This is not merely an inert bug: every downstream consumer of this
identifier (`/review-plan`'s freshness check, `/record-manual-plan-review`'s
hard block on mismatch, `/approve-review plan`'s staleness check and
approval-commit trailer, `/apply-plan-review`'s "did the reviewed content
actually change" gate) would silently accept a `v2-1-dry-run` plan edit —
including a substantive one — as unchanged, because the digest they compare
against never depended on `v2-1-dry-run`'s files in the first place.

## Security/integrity impact

This is a fail-open defect in the one mechanism (`review_content_id`) this
entire workflow's design (`D-Fingerprint`, `OPUS-R8-*`/`OPUS-R18-*`) exists
to make fail-closed: "a protected-path edit between computation and
approval is caught, never silently absorbed." For any work item other than
`workflow-v2-1-core` itself, the opposite currently holds — a protected-path
edit is **never** caught, because no path is protected. Concretely: a plan
document could be substantively rewritten after a reviewer's `APPROVE`, and
`/approve-review plan`'s staleness check (which compares the *current*
recomputed `review_content_id` against the *approved* one) would not detect
it, because both values come from `workflow-v2-1-core`'s unrelated files.

## Affected commands

Every command whose contract calls into the plan-stage
`review_content_id`/`MANIFEST.md` machinery for a `work_item_id` other than
`workflow-v2-1-core`:

- `/milestone-plan` (`.claude/commands/milestone-plan.md`, step 6 — bundle
  generation implicitly depends on this to populate `REVIEW_REQUEST.md`'s
  stated `review_content_id` and to write `MANIFEST.md`)
- `/review-plan` (`.claude/commands/review-plan.md` — recomputes and
  compares against the bundle's stated value)
- `/record-manual-plan-review` (`.claude/commands/record-manual-plan-review.md`
  — hard-blocks on a `review_content_id` mismatch; that check is
  meaningless if the "recomputed" value never depended on the reviewed
  content)
- `/approve-review plan` (`.claude/commands/approve-review.md` — staleness
  check and the `Workflow-Plan-Approval` trailer value it commits)
- `/apply-plan-review` (re-review-after-revision exit step, `"2.1"`
  branch — relies on the same recomputation to detect that a revision
  actually changed protected content)
- `/bootstrap-workflow-v2`'s own durability guard (step 2) is unaffected —
  it only ever targets `workflow-v2-1-core`, which is the one work item this
  tool is correctly wired for today.

## Relationship to deferred scope item WF4a-i

`docs/ai-workflow/registry/workflow-v2-1-core-registry.json`'s `WF4a-i`
entry already names this exact gap as deferred, unfinished scope:
*"Canonical fingerprint helpers wired into prepare-ai-review.sh/approve-review
(the helpers themselves are built and twice-corrected as a prototype);
deferred implementation-stage manifest and its real fixtures;
**generalize protected-path derivation beyond this one process plan**."*
WF4a-i was marked `COMPLETE` without that generalization ever landing. This
finding is the concrete manifestation WF8b was designed to surface: the
first time a second, real, `"2.1"`-governed work item actually exercises
the plan-stage fingerprint path.

## Minimum required remediation scope

1. The plan-stage fingerprint API and CLI must resolve **work-item-specific**:
   `work_item_id`, `work_item_type`, plan path, registry path,
   requirements-mapping path, protected-path set, and plan revision — not a
   single hardcoded set.
2. `--work-item-id` must affect the actual protected-content projection,
   not merely bundle-directory resolution.
3. Unknown or incomplete work-item metadata must fail closed (raise, not
   silently fall back to `workflow-v2-1-core`'s own set or to an empty
   protected set).
4. `workflow-v2-1-core` must retain its existing exact fingerprint and
   approval discoverability unless an independently reviewed migration
   explicitly changes it — its current `approved_review_content_id`
   (`2b4d2e3b89c2b8f...`) must still be reproducible byte-for-byte after the
   fix.
5. A second synthetic work item must produce a **distinct**
   `review_content_id` whose manifest includes its own plan/registry/mapping
   and excludes `workflow-v2-1-core`'s equivalents.
6. Mutating any protected file of either work item must change only that
   work item's `review_content_id`.
7. Cross-item substitution, missing metadata, duplicate ids, wrong item
   type, and mismatched registry/plan ids must fail closed.
8. Existing plan-stage, implementation-stage, state, integration, bundle,
   and approval-discovery tests must remain green
   (`workflow_fingerprint_test.py`, `workflow_fingerprint_demo_test.py`,
   `workflow_state_test.py`, `workflow_state_demo_test.py`,
   `workflow_integration_test.py`, `workflow_test_harness_test.py`).
9. S1 must be **rerun from the beginning**, in a fresh session, once this
   remediation has itself been independently reviewed and approved — not
   resumed from partial state.

## Confirmation: repository and workflow state unchanged when this defect was discovered

- `git status --short` (tracked and untracked): clean, no diffs, before and
  after this investigation.
- No file under `docs/ai-workflow/dry-run/scratch/`,
  `docs/ai-workflow/registry/`, or `docs/ai-workflow/requirements/` was
  created for `v2-1-dry-run`.
- `.ai-review/v2-1-dry-run/` was never created; `./scripts/prepare-ai-review.sh`
  was never invoked.
- `docs/ai-workflow/WORKFLOW_STATE.json` untouched: `active_work_item_id`
  remains `v2-1-dry-run`; `work_items["v2-1-dry-run"].phase` remains
  `PLANNING`, `plan_revision: 1`, `checkpoints: {}`, `base_commit: null`;
  `work_items["workflow-v2-1-core"]` untouched, `checkpoints.WF8b` is absent
  (i.e. still not `COMPLETE`) — `WF8b` remains `IN_PROGRESS` by the same
  marker-file/`active_work_item_id` convention its own entry commit (`9317b1c`)
  established.
- The plan-stage durability guard was re-verified immediately before this
  finding was written: recomputed `review_content_id` for
  `workflow-v2-1-core` = `2b4d2e3b89c2b8f243d89fcfb90fded67cc9cd003ba5feff3751d455fb3e49bf`,
  matching `plan_approval.approved_review_content_id` exactly — no drift.

## Next step

Route the remediation described above through the normal review channel
(the same validate-then-fix discipline `/apply-plan-review`/
`/apply-implementation-review` already use elsewhere in this workflow),
reviewed and approved independently of this dry run, before S1 is attempted
again.
