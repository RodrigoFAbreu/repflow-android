# Review Bundle Protocol

Authoritative reference for how review bundles are built, what they must
contain, how external feedback comes back in, and how to keep context usage
low across the milestone workflow. State transitions that use this protocol
are defined in `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

## Generating a bundle

```bash
./scripts/prepare-ai-review.sh <base-sha> <stage> [work-item-id]
```

`<stage>` is one of: `plan`, `implementation`, `post-fix`,
`functional-review`.

`<base-sha>` is the commit the diff should be computed from — normally the
milestone's starting commit (the last commit before this milestone's work
began, e.g. the completion commit of the previous milestone). For the
`plan` stage, this must agree with the resolved work item's own declared
`base_commit` (`WORKFLOW_STATE.json`'s `work_items[<id>].base_commit`) —
the script refuses before generating any content on a genuine
disagreement, naming both (`D-Fingerprint-Generalization`).

The work-item-id argument is **required when `<stage>` is `plan`** — never
resolved from the live `active_work_item_id` for that stage, since
`MANIFEST.md`'s identity binding depends on it
(`D-Fingerprint-Generalization`). It remains optional for every other
stage (`implementation`/`post-fix`/`functional-review`): passing it writes
the bundle under the per-work-item layout (`.ai-review/<work-item-id>/`,
see below); omitting it writes the flat compatibility layout
(`.ai-review/current/`, `.ai-review/feedback/`) directly under
`.ai-review/`. For the `plan` stage, the script's own final step also
writes `MANIFEST.md` (`scripts/workflow_fingerprint.py --write-manifest`,
the same CLI entry point every other invocation uses, never a
reimplementation) — no separate manual step is needed.

The script is deterministic and safe to rerun: it always regenerates the
git-derived files from current repository state, and leaves author-written
files untouched if they already exist (it only creates empty stubs for
missing ones, so the bundle structure stays stable at every stage).

### Bundle location: `.ai-review/<work_item_id>/` layout, with a compatibility fallback

The canonical layout is per-work-item:

```text
.ai-review/<work_item_id>/
├── current/            # the bundle currently under review (see structure below)
├── feedback/            # external feedback, placed here by the reviewer/user
└── review-bundle.tar.gz # archive of current/
```

For any work item that has never had this layout created yet, commands
read the flat compatibility path instead: `.ai-review/current/` and
`.ai-review/feedback/` directly under `.ai-review/` (no work-item
subdirectory). The resolution rule (implemented in
`scripts/workflow_fingerprint.py`'s `resolve_bundle_dir`/
`resolve_feedback_dir`, not left to prose alone) is: prefer
`.ai-review/<work_item_id>/{current,feedback}/` if that directory already
exists, else fall back to the flat path. `feedback/` is stage-agnostic and
always follows this same scoped-else-flat rule, for every stage alike.

For the **plan stage specifically**, the bundle directory is never the
flat fallback: the work-item-id argument is required (above), so
`ROOT_DIR`/`<bundle_dir>` always resolve to
`.ai-review/<work_item_id>/current/` by construction
(`D-Fingerprint-Generalization`). `workflow-v2-1-core`'s own historical
flat `.ai-review/current/`/`.ai-review/review-bundle.tar.gz` were
relocated to `.ai-review/workflow-v2-1-core/` as a one-time migration when
this rule landed; `.ai-review/feedback/` was deliberately left flat (it is
stage-agnostic and every non-plan stage's bundle is still flat too).

`.ai-review/` is entirely gitignored, including `.ai-review/source/` —
files a human places there as raw proposal material for Claude to read,
never review-bundle content. `CONTEXT_FILES.txt` must never list a path
under `.ai-review/source/`; `scripts/prepare-ai-review.sh` skips (and
warns on) any such entry as defense in depth.

### Bundle structure

```text
<bundle_dir>/                  # .ai-review/<work_item_id>/current/, or .ai-review/current/ (compatibility)
├── REVIEW_REQUEST.md          # author-written, see below
├── PLAN.md                    # author-written; plan-stage content
├── IMPLEMENTATION_SUMMARY.md  # author-written; implementation-stage content
├── TEST_RESULTS.md            # author-written; tests actually run
├── CHANGED_FILES.txt          # generated: metadata + diff --stat + name-status + git status
├── COMMITS.txt                # generated: git log base..HEAD
├── DIFF.patch                 # generated: full diff from base to working tree
├── CONTEXT_FILES.txt          # author-written: list of unchanged context files to include
├── MANIFEST.md                # generated (scripts/workflow_fingerprint.py --write-manifest only):
│                               # bundle_id, review_content_id, protected/excluded path lists,
│                               # and diagnostic-only worktree_root/generation_head (see below)
└── files/                     # generated: final copies of changed + listed context files
```

### Generation diagnostic metadata (`worktree_root`/`generation_head`)

`MANIFEST.md` records the absolute worktree root and HEAD SHA the bundle
was generated from, as plain diagnostic lines — never hashed into
`review_content_id`, and not part of any identity-bearing field contract.
This is **portability vs. local staleness, split by consumer**:

- A **repository-local command** — today `/approve-review`, `/review-plan`,
  and `/review-implementation`, all three calling this at its default,
  permissive `require_metadata=False` — runs inside a real, current
  worktree and can
  meaningfully ask "is this the same worktree and HEAD I'm sitting in
  right now": it calls
  `workflow_fingerprint.assert_local_generation_matches(...)` and stops,
  naming both values, on a mismatch. This is the actual first-party
  Milestone-8 incident (a stale bundle read from a different worktree) the
  mechanism exists to catch. A third, stricter `require_metadata=True`
  mode also exists on the same function (`GPT-R62-001`) for a caller that
  cannot tolerate a metadata-less legacy bundle at all, but no such caller
  is live in this repository today.
- An **external reviewer** receiving the bundle via the archive is, by
  design, outside the generating worktree — that is not staleness, it is
  the archive doing its job. External review validates the exact
  `bundle_id` and the reported Git metadata (base/head SHAs, branch)
  instead; it never checks `worktree_root`/`generation_head` for equality.

### Author-written files — what belongs in each

- **REVIEW_REQUEST.md** — the index. See "Review request format" below.
  Must state `review_content_id: <hex>` as a plain labelled line, agreeing
  with `MANIFEST.md` (`OPUS-R18-005`).
- **PLAN.md** — populated at the `plan` stage: the actual execution plan
  being reviewed. Leave empty (or omit updating it) at later stages. Must
  state the plan's current `(Revision N)` marker — a bundle whose `PLAN.md`
  disagrees with the authoritative plan document's own declared revision
  fails the stage-completeness check (`assert_stage_completeness`).
- **IMPLEMENTATION_SUMMARY.md** — populated at `implementation`/`post-fix`
  stages: what was built, checkpoint by checkpoint, and why. Must state
  `implementation_revision: <N>` matching the work item's current counter
  (`WORKFLOW_STATE.json`'s `implementation_revision`) — same
  stage-completeness discipline as `PLAN.md`'s revision marker.
- **TEST_RESULTS.md** — exact commands run and their real outcome. Never
  state a check passed unless it actually ran in this session. **At the
  `plan` stage**, must additionally open with a `stage: plan (revision N)`
  line (`N` matching the plan document's current revision) and a
  `head: <sha>` line (matching this generation's own HEAD) — a bundle
  whose `TEST_RESULTS.md` is missing, empty, names a different round, a
  different HEAD, or carries forward a previous implementation round's
  evidence unexamined fails the closing consistency check (item 272,
  `assert_test_results_consistent_with_plan_review_request`,
  `finalize_bundle_generation`) and is withdrawn rather than published.
- **CONTEXT_FILES.txt** — one repo-relative path per line, no comments. Only
  the files a reviewer genuinely needs beyond the diff itself (e.g. the ADR
  a decision follows, the domain glossary entry a rule depends on). The
  script copies every listed file into `files/` verbatim. Keep this short —
  it is not a dump of the whole `docs/` tree. Never list a path under
  `.ai-review/source/` (source-proposal material, not review content).

### What the script does NOT do

- It does not judge relevance — `CONTEXT_FILES.txt` is a human/Claude
  decision.
- It does not include build outputs, `.gradle/`, APKs, generated binaries,
  secrets, or the full repository — only files that are part of the diff or
  explicitly listed as context.
- It does not commit anything or touch git state beyond reading it.

## Review request format

`REVIEW_REQUEST.md` must state:

- review stage;
- objective;
- branch, base SHA, HEAD SHA;
- active milestone and checkpoint;
- summary of proposed or implemented behavior;
- architecture decisions;
- schema/migration implications;
- backup compatibility implications;
- UI/UX decisions;
- tests run and results;
- known limitations;
- unresolved questions;
- specific areas the reviewer should challenge.

Keep it concise — it points at `DIFF.patch` and `files/`, it does not repeat
their content.

For a **plan** review, there may be no production diff yet. Still include the
plan itself (`PLAN.md`) and directly relevant domain/architecture files via
`CONTEXT_FILES.txt`.

For an **implementation** review, `DIFF.patch` must be the real milestone
diff and `files/` must hold the final changed files, not a summary.

## Feedback protocol

External plan/implementation feedback is placed at:

```text
<feedback_dir>/REVIEW_FEEDBACK.md
```

(`.ai-review/<work_item_id>/feedback/`, or the flat compatibility
`.ai-review/feedback/` — see "Bundle location" above.)

Required structure:

```markdown
# Review Decision

Status: APPROVE | REVISE | BLOCK

Reviewed bundle ID: <the exact bundle_id this feedback reviewed>
Reviewed base commit: <the exact base_commit this feedback reviewed>
Work item: <the exact work_item_id this feedback reviewed>

## Blocking findings

## Important findings

## Optional findings

## Missing tests

## Architecture and maintainability concerns

## Migration and data-integrity concerns

## Usability concerns

## Required acceptance criteria
```

The three binding fields (`Reviewed bundle ID:`, `Reviewed base commit:`,
`Work item:`) are required on every ordinary review round, not only WF0's
one-time bootstrap check (`OPUS-R14-002`, generalized here). Feedback
missing any of the three, or whose values disagree with the bundle
actually being approved against, is rejected naming both the feedback's
own value and the current one
(`workflow_fingerprint.parse_review_feedback_binding_fields`/
`assert_feedback_matches_bundle`, `WFR-03`) — never applied at face value.

User functional-testing feedback is placed at:

```text
<feedback_dir>/FUNCTIONAL_REVIEW.md
```

(Free-form findings; no binding-field requirement, since functional review
has no `bundle_id`/`review_content_id` of its own to bind against —
`/apply-functional-review` classifies each finding.)

**Claude must validate feedback, never apply it blindly.** Every blocking and
important finding must end up either resolved, or explicitly rejected in the
plan/summary with repository evidence (file, line, test, or doc reference)
and a clear explanation.

## Context-efficiency rules

- Load only the active status (`docs/ACTIVE_MILESTONE.md`), `docs/ROADMAP.md`,
  the active execution guide, and files relevant to the current checkpoint.
- Load full milestone reference docs only when a detail is genuinely
  unresolved from the execution guide.
- Do not load completed milestone docs (`docs/milestones/completed/`) unless
  historical decisions are needed to understand the current change.
- Do not load `docs/agent-context/` during feature planning or
  implementation — it is a meta-analysis of the context system itself, not
  product or architecture context.
- Do not reread the same large file repeatedly within one session without a
  new reason to.
- Keep `docs/ACTIVE_MILESTONE.md` factual and concise — current state, not a
  chronological journal.
- Keep execution guides operational (steps, checks) and reference guides
  detailed but conditional (full rationale, invariants).
- Summarize command output in docs/bundles rather than pasting full logs.
- Use the git diff and review bundle as the review artifact — do not
  substitute a prose-only summary for the real diff.
- Compact the conversation or start a fresh session at milestone/review
  boundaries when the session has grown large.
- Never list a `.ai-review/source/` path in `CONTEXT_FILES.txt` or treat it
  as review content — it is raw proposal material a human places there for
  Claude to read, not part of any bundle (`WFR-16`).
