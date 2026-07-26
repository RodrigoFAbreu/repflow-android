# Context Changelog

Each entry is a complete optimization iteration: hypothesis, files changed,
expected reduction, risk, validation, decision.

## Iteration 1 — De-hardcode milestone path in AGENTS.md + remove orphaned stub

- **Hypothesis:** `AGENTS.md`'s "read conditionally" table hardcodes
  `docs/milestones/active/milestone-1-reference.md`, so the reusable router
  itself goes stale the moment Milestone 2 starts (violates "reusable
  instructions do not hardcode a specific milestone"). Separately,
  `docs/PRODUCT_AND_ARCHITECTURE.md` is a redirect stub with zero inbound
  references anywhere in the repo — pure dead weight in `docs/`.
- **Files changed:**
  - `AGENTS.md` — reference the reference file "linked from the Active plan
    section of `docs/ACTIVE_MILESTONE.md`" instead of a literal milestone-1
    path.
  - `docs/PRODUCT_AND_ARCHITECTURE.md` — removed (orphaned redirect stub).
- **Expected context reduction:** Default-loaded words unchanged (stub was
  never in a default-load path). Benchmark 7 (next-milestone transition)
  context becomes correct instead of stale once M2 begins — this is a
  correctness fix, not a raw word-count win. Directory listing noise reduced
  by one dead file (~26 words removed from `docs/`).
- **Quality/safety risk:** Low. The new wording still resolves to the same
  file today (`ACTIVE_MILESTONE.md`'s "Active plan" section already lists the
  reference doc), so current-session behavior is unchanged; behavior improves
  only when the milestone changes. Stub had no inbound links, confirmed by
  repo-wide grep before removal.
- **Validation:**
  - `grep -rn "milestone-1" AGENTS.md .github/copilot-instructions.md
    .github/instructions/*.md .github/prompts/*.md` → no matches (was 1
    before).
  - `grep -rln "PRODUCT_AND_ARCHITECTURE" --include="*.md" .` → no matches
    before or after removal (confirms it was unreferenced, safe to delete).
  - Markdown link scan across all `.md` files for `docs/*.md` targets → no
    broken links.
  - `git diff --check` → clean (no whitespace errors).
- **Decision:** **Accepted.** Small, low-risk, directly resolves the one
  concrete "milestone-specific hardcoded path in reusable instructions" and
  one dead file found during baseline inspection.

## Finalization pass (2026-07-26)

- **Snapshot:** Iteration 1 above was evaluated against branch
  `improving-docs-and-prompts` at commit `0cecefa` (Milestone 1 active, no
  Milestone 2 work present on this branch). This finalization pass re-verified
  that snapshot rather than reconciling with any newer state on `main`.
- **Action taken:** Added explicit snapshot metadata (date, branch, commit,
  active milestone, working-tree state) to the top of
  `CONTEXT_EVALUATION.md`, and reconfirmed via repo-wide grep that
  `docs/PRODUCT_AND_ARCHITECTURE.md` still has zero inbound references before
  retaining its deletion. No new routing change was necessary — the
  `AGENTS.md` fix from Iteration 1 already resolves correctly from
  `ACTIVE_MILESTONE.md`'s "Active plan" section with no hardcoded milestone
  path.
- **Not done:** No redesign of the context system, no Milestone 2 content
  added (that belongs to a future merge from `main`), no new benchmark
  scenarios.

## Stopping rationale

Only one iteration was run. The baseline inventory found the context system
already well-factored (concise router, `applyTo`-scoped path-specific
instructions, live status file separate from static roadmap, archival ADRs
kept out of the default-load path, honest test-reporting and stop-condition
language already consistent across `AGENTS.md` / `copilot-instructions.md` /
the prompt). The only concrete defects found were the one hardcoded path and
one orphaned file, both fixed in Iteration 1. Remaining candidate changes
(e.g. further trimming `ACTIVE_MILESTONE.md` or the milestone reference doc)
were judged likely to reduce word count without a proportional routing or
quality benefit, and the remaining session budget was assessed as
insufficient to run and validate a second full iteration plus final
deliverables — per the task's explicit stopping rule ("remaining session
budget is too small for a full iteration and final validation"). No
iterations were reverted.
