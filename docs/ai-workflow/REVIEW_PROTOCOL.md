# Review Bundle Protocol

Authoritative reference for how review bundles are built, what they must
contain, how external feedback comes back in, and how to keep context usage
low across the milestone workflow. State transitions that use this protocol
are defined in `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

## Generating a bundle

```bash
./scripts/prepare-ai-review.sh <base-sha> <stage>
```

`<stage>` is one of: `plan`, `implementation`, `post-fix`,
`functional-review`.

`<base-sha>` is the commit the diff should be computed from — normally the
milestone's starting commit (the last commit before this milestone's work
began, e.g. the completion commit of the previous milestone).

The script is deterministic and safe to rerun: it always regenerates the
git-derived files from current repository state, and leaves author-written
files untouched if they already exist (it only creates empty stubs for
missing ones, so the bundle structure stays stable at every stage).

### Bundle structure

```text
.ai-review/current/
├── REVIEW_REQUEST.md          # author-written, see below
├── PLAN.md                    # author-written; plan-stage content
├── IMPLEMENTATION_SUMMARY.md  # author-written; implementation-stage content
├── TEST_RESULTS.md            # author-written; tests actually run
├── CHANGED_FILES.txt          # generated: metadata + diff --stat + name-status + git status
├── COMMITS.txt                # generated: git log base..HEAD
├── DIFF.patch                 # generated: full diff from base to working tree
├── CONTEXT_FILES.txt          # author-written: list of unchanged context files to include
└── files/                     # generated: final copies of changed + listed context files
```

`.ai-review/review-bundle.tar.gz` is produced from the whole `current/`
directory.

### Author-written files — what belongs in each

- **REVIEW_REQUEST.md** — the index. See "Review request format" below.
- **PLAN.md** — populated at the `plan` stage: the actual execution plan
  being reviewed. Leave empty (or omit updating it) at later stages.
- **IMPLEMENTATION_SUMMARY.md** — populated at `implementation`/`post-fix`
  stages: what was built, checkpoint by checkpoint, and why.
- **TEST_RESULTS.md** — exact commands run and their real outcome. Never
  state a check passed unless it actually ran in this session.
- **CONTEXT_FILES.txt** — one repo-relative path per line, no comments. Only
  the files a reviewer genuinely needs beyond the diff itself (e.g. the ADR
  a decision follows, the domain glossary entry a rule depends on). The
  script copies every listed file into `files/` verbatim. Keep this short —
  it is not a dump of the whole `docs/` tree.

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
.ai-review/feedback/REVIEW_FEEDBACK.md
```

Required structure:

```markdown
# Review Decision

Status: APPROVE | REVISE | BLOCK

## Blocking findings

## Important findings

## Optional findings

## Missing tests

## Architecture and maintainability concerns

## Migration and data-integrity concerns

## Usability concerns

## Required acceptance criteria
```

User functional-testing feedback is placed at:

```text
.ai-review/feedback/FUNCTIONAL_REVIEW.md
```

(Free-form findings; `/apply-functional-review` classifies each one.)

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
