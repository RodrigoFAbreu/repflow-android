# Context Evaluation

> **Snapshot metadata** (point-in-time; re-run before trusting this after any
> merge or milestone change):
> - Evaluation date: 2026-07-26
> - Branch: `improving-docs-and-prompts`
> - Evaluated commit: `0cecefa` (`milestone 1.1 completed`)
> - Active milestone per this branch's docs: Milestone 1 — Exercise Library
>   (per `docs/ACTIVE_MILESTONE.md` / `docs/ROADMAP.md` as checked out here)
> - Working tree during evaluation: documentation-only changes uncommitted
>   (`AGENTS.md` modified, `docs/PRODUCT_AND_ARCHITECTURE.md` deleted,
>   `docs/agent-context/` untracked) — no application/test/Gradle/schema/CI
>   files touched
> - This audit describes **this branch at this commit only**. It does not
>   reflect any later milestone state that may exist on `main` or elsewhere,
>   and must be refreshed after the next merge.
>
> `docs/agent-context/` itself is **diagnostic/archival**: normal feature-work
> routing (`AGENTS.md`) does not instruct agents to load it. Read it only when
> auditing or modifying the context system.

All word counts via `wc -w`; estimated tokens ≈ characters / 4 (rough proxy,
not a measured value). AI-credit figures are filled in only where the user
supplies them; otherwise marked `not programmatically available`.

**On the scorecard below:** treat the 0–5/40 figures as a qualitative
heuristic only, not a measured implementation-quality improvement. The
reproducible metrics further down (hardcoded paths, broken links,
contradictions, orphaned files, default-context size) are the stronger
evidence and should be weighted more heavily than the score.

## Scorecard (0–5 each; 40 total)

| Dimension | Baseline | After Iteration 1 |
|---|---|---|
| Correctness | 4 | 5 |
| Context efficiency | 4 | 4 |
| Task relevance | 4 | 4 |
| Maintainability | 3 | 4 |
| Milestone reusability | 3 | 5 |
| Resumption reliability | 4 | 4 |
| Contradiction resistance | 4 | 4 |
| Implementation guidance quality | 4 | 4 |
| **Total /40** | **30** | **34** |

Notes: Baseline lost points on *milestone reusability* (hardcoded
`milestone-1-reference.md` path in `AGENTS.md`) and *maintainability* (one
orphaned redirect file). Iteration 1 fixes both; other dimensions were already
strong (concise router, `applyTo`-scoped instructions, factual status file,
consistent stop/commit/test rules) so they hold steady rather than jump.

## Always-loaded context (AGENTS.md + copilot-instructions.md)

| | Baseline | After Iteration 1 |
|---|---|---|
| Words | 341 (222 + 119) | 341 (edit reworded, not shortened) |
| Est. tokens | ~1130 | ~1130 |

## Active-milestone startup path (AGENTS.md → ACTIVE_MILESTONE.md → ROADMAP.md → execution guide → copilot-instructions.md)

| | Baseline | After Iteration 1 |
|---|---|---|
| Words | 1891 | 1891 |
| Est. tokens | ~6260 | ~6260 |
| Documents loaded by default | 5 | 5 |

Word count is unchanged because the fix corrected a *reference*, not the
included text — the improvement is in correctness/reusability, not size.

## Duplication, contradictions, staleness

| | Baseline | After Iteration 1 |
|---|---|---|
| Duplicated rules (topic repeated across ≥2 files, same substance) | 3 (stop/commit rules across AGENTS.md/copilot-instructions.md/prompt; destructive-migration ban across copilot-instructions.md + persistence.instructions.md; dependency-direction rule across copilot-instructions.md + ADR 0003) | 3 (unchanged — these are intentional, non-contradictory reinforcements at different scopes: universal vs. path-specific vs. rationale; not flagged for removal) |
| Contradictions detected | 0 | 0 |
| Stale/broken links | 0 | 0 |
| Milestone-specific hardcoded paths in reusable instructions | 1 (`AGENTS.md` → `milestone-1-reference.md`) | 0 |
| Task categories with path-specific instructions | 4 (domain, persistence, presentation, gradle) | 4 |
| Orphaned/dead context files | 1 (`docs/PRODUCT_AND_ARCHITECTURE.md`) | 0 |

The 3 duplicated rules are intentionally layered (universal rule restated
briefly at the path-specific or rationale level for a reader who only loads
that narrower file) rather than copy-paste drift, so they were not removed —
doing so would risk an agent loading only the narrow file and missing the
rule.

## Benchmark scenario context estimates (see BENCHMARK_SCENARIOS.md for detail)

| Scenario | Baseline est. words | After Iteration 1 | Target met? |
|---|---|---|---|
| 1. Domain model change | ~900 | ~900 | Yes |
| 2. Room entity/DAO/repo change | ~900 (+1400 if ADR needed) | same | Yes |
| 3. Compose/ViewModel UX change | ~900 (+500 if UX_FLOWS needed) | same | Yes |
| 4. Gradle/toolchain change | ~600 | same | Yes |
| 5. Database migration | ~2300 | same | Yes |
| 6. Checkpoint continuation | ~1500 (+ reference doc as needed) | same | Yes |
| 7. Next-milestone transition | ~800, but router pointed at a **stale hardcoded path** once M2 starts | ~800, router now resolves correctly regardless of milestone | **Improved** — was the one scenario baseline would have mis-routed |
| 8. Bug fix, no architecture impact | ~500 | same | Yes |

Only scenario 7 changes numerically-invisibly but qualitatively: baseline's
router would have pointed to a nonexistent/wrong reference file as soon as
Milestone 2 becomes active, forcing a manual fallback; Iteration 1 removes
that failure mode.

## Quality-risk assessment

- **Low risk overall.** The one behavior-changing edit (AGENTS.md wording) is
  a strict generalization of already-correct current behavior — verified by
  confirming `ACTIVE_MILESTONE.md`'s existing "Active plan" section already
  names the reference file, so today's routing outcome is identical.
- Removing the orphaned stub carries no risk: confirmed zero inbound
  references repo-wide before deletion.
- No always-loaded content was shortened, so no critical-rule availability
  was traded for size reduction this round.

## Unresolved issues / deferred candidates (not attempted, budget-limited)

- `docs/milestones/active/milestone-1-reference.md` (5521 words) could
  potentially be trimmed or restructured once Milestone 1 closes and moves to
  an "archive" directory, but that is a milestone-transition action, not a
  context-quality defect today — deferred to the M1→M2 handoff itself.
- The 3 intentionally-duplicated rule statements (see above) could be
  consolidated to single-owner text with cross-references, but the current
  layering is not contradictory and was judged safer to leave than to risk
  breaking narrow-file self-sufficiency.
- No further iterations were run due to session budget constraints (see
  CONTEXT_CHANGELOG.md stopping rationale).

## AI-credit tracking (to be filled in by the user; not programmatically available to this agent)

| Checkpoint | Plan AIC |
|---|---|
| Session start | not programmatically available |
| After baseline | not programmatically available |
| After Iteration 1 | not programmatically available |
| Session end | not programmatically available |
| Session compaction events | not programmatically available |
| Accepted iterations | 1 (Iteration 1) |
| AIC spent per accepted improvement | not programmatically available |

This agent has access only to a remaining-session-credit estimate surfaced by
the environment (observed ~2992 at task start, ~2957 after inventory/baseline
work), not an authoritative Plan/Session AIC counter; the table above should
be completed by the user from their CLI/plan dashboard if exact figures are
needed.
