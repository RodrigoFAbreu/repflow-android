# Context Inventory

Word counts are approximate (`wc -w`). Estimated tokens ≈ words × 1.3 (rough).

| File | Purpose | Load mode | Words | Duplicated topics | Authoritative topic(s) | Referenced by | Risk if shortened/removed |
|---|---|---|---|---|---|---|---|
| `AGENTS.md` | Context router: what to read, workflow, stop conditions | Always | 220 | Stop/commit rules (also in copilot-instructions.md, prompt) | Read-routing table, workflow loop | prompt, copilot-instructions | Losing routing forces agents to guess which docs apply → over-reading or under-reading |
| `.github/copilot-instructions.md` | Universal non-negotiable rules | Always | 119 | Stop/commit/migration rules overlap AGENTS.md | Short authoritative rule list (applies repo-wide, no `applyTo`) | AGENTS.md ("Follow…") | Losing it removes the only rule file with no path scoping — safety net for any file type |
| `.github/instructions/domain.instructions.md` | Domain-layer rules | Conditional (`applyTo` domain paths) | 52 | Purity/dependency direction (also copilot-instructions.md) | Domain purity specifics, deterministic test rule | domain.instructions rules file: `docs/DOMAIN_GLOSSARY.md` | Domain purity violations (Android imports leaking in) |
| `.github/instructions/persistence.instructions.md` | Room/persistence rules | Conditional | 69 | Destructive-migration ban (also copilot-instructions.md) | Migration/versioning rule, enum-as-string rule, cancellation rule | ADR 0002 | Silent destructive migrations or ordinal enum persistence bugs |
| `.github/instructions/presentation.instructions.md` | Compose/ViewModel rules | Conditional | 60 | — | UDF pattern, ViewModel boundary rule | `docs/UX_FLOWS.md` | ViewModels reaching into infrastructure directly |
| `.github/instructions/gradle.instructions.md` | Build/toolchain rules | Conditional | 49 | — | Version catalog, JDK 17 pin | — | Unpinned/duplicate dependency versions, accidental toolchain drift |
| `.github/prompts/run-active-milestone.prompt.md` | Reusable "resume milestone" runbook | Conditional (invoked prompt) | 197 | Stop conditions overlap AGENTS.md | Checkpoint loop procedure, honesty-in-reporting instruction | AGENTS.md, ACTIVE_MILESTONE.md | Losing it removes the only executable step-by-step resume procedure |
| `docs/ACTIVE_MILESTONE.md` | Live status: current checkpoint, verified state, active plan links | Always (part of startup path) | 471 | — | Ground-truth milestone status (never inferred from plan text) | AGENTS.md, ROADMAP.md, prompt | Losing it removes the only reliable resume-from-repo-state anchor |
| `docs/ROADMAP.md` | All-milestone index, high-level scope per milestone | Always | 307 | — | Milestone list/order | AGENTS.md, README | Losing it removes future-milestone visibility |
| `docs/milestones/active/milestone-1-execution.md` | Step-by-step execution guide for current milestone | Always (via ACTIVE_MILESTONE link) | 772 | — | Per-checkpoint tasks/verification for M1 | ACTIVE_MILESTONE.md | Losing it removes actionable steps; would force reconstructing from reference doc every time |
| `docs/milestones/active/milestone-1-reference.md` | Full decisions/invariants/DoD for M1 | Conditional (archival detail) | 5521 | Overlaps ACTIVE_MILESTONE "approved decisions" summary | Full invariant/DoD text | execution guide, AGENTS.md (generic pointer) | Losing detail risks re-deriving business rules incorrectly |
| `docs/DOMAIN_GLOSSARY.md` | Domain terminology/business rules | Conditional | 460 | — | Ubiquitous language | domain.instructions.md | Ambiguous terms reintroduced inconsistently |
| `docs/UX_FLOWS.md` | UX/navigation decisions | Conditional | 496 | — | Screen flows, interaction rules | presentation.instructions.md | Re-litigating UX decisions per session |
| `docs/adr/0002-...local-database...md` | Persistence ADR | Conditional/archival | 1396 | — | Why Room is source of truth, migration policy rationale | persistence.instructions.md, AGENTS.md | Losing rationale risks reversing a settled architecture decision |
| `docs/adr/0003-layered-modular-architecture.md` | Architecture ADR | Conditional/archival | 2242 | Dependency-direction rule (also copilot-instructions.md) | Layering rationale, module boundaries | AGENTS.md | Losing rationale risks boundary erosion over time |
| `docs/adr/0001-stack.md` | Stack choice ADR | Archival | 123 | — | Why Kotlin/Compose | README | Low — historical only |
| `docs/TECHNICAL_DECISIONS.md` | Toolchain/dependency decisions | Conditional | 466 | — | Dependency/tooling rationale | AGENTS.md, README | Re-adding rejected dependencies |
| `docs/PROJECT_BRIEF.md` | Product scope/vision | Conditional | 454 | — | Product scope authority | AGENTS.md, README | Scope drift without a check |
| `README.md` | Human-facing overview | Archival (not agent-routed) | 122 | — | Project intro/links | — | Low — onboarding only |
| `docs/PRODUCT_AND_ARCHITECTURE.md` | **Removed** — orphaned redirect stub, zero inbound links, content fully superseded by PROJECT_BRIEF/ADR 0003/TECHNICAL_DECISIONS | n/a | 26 | Pure duplication (redirect only) | none | none | None — safe removal, no references existed |

## Default-read chain (startup path)
`AGENTS.md` → `docs/ACTIVE_MILESTONE.md` → `docs/ROADMAP.md` → milestone execution guide (path read from ACTIVE_MILESTONE.md, not hardcoded) → `.github/copilot-instructions.md`.
Path-specific `.github/instructions/*.instructions.md` load only when editing matching file globs (Copilot native `applyTo` mechanism), not part of the always-loaded chain.
