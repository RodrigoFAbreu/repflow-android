# Benchmark Scenarios (static context-routing evaluation)

Each scenario lists the minimal files an agent should load, files it must avoid,
critical rules that must stay available, stopping conditions, and a context
efficiency target (approx. words of context actually loaded). These are
routing evaluations only — scenarios are not executed.

## 1. Pure domain model change
- Load: `AGENTS.md`, `docs/ACTIVE_MILESTONE.md`, active execution guide,
  `.github/instructions/domain.instructions.md`; `docs/DOMAIN_GLOSSARY.md` only
  if terminology is ambiguous.
- Avoid by default: UX_FLOWS.md, ADR 0002/0003, gradle/persistence/presentation
  instructions, milestone reference doc.
- Critical rules: domain purity (no Android/Room/Hilt imports), deterministic
  clocks/ids, dependency direction.
- Stop conditions: business-rule ambiguity not resolved by glossary.
- Efficiency target: ≤ 900 words loaded (router + status + domain rules).

## 2. Room entity/DAO/repository change
- Load: `AGENTS.md`, `ACTIVE_MILESTONE.md`, execution guide,
  `.github/instructions/persistence.instructions.md`; ADR 0002 only if the
  persistence *strategy* (not a routine schema addition) is in question.
- Avoid: UX_FLOWS.md, domain/presentation instructions, ADR 0003.
- Critical rules: Room is source of truth, no destructive migration, enums
  persisted as strings, version increment + migration test on schema change.
- Stop conditions: schema migration not pre-approved; destructive fallback
  would be required.
- Efficiency target: ≤ 900 words (add ~1400 only if ADR 0002 truly needed).

## 3. Compose/ViewModel UX change
- Load: `AGENTS.md`, `ACTIVE_MILESTONE.md`, execution guide,
  `.github/instructions/presentation.instructions.md`; `UX_FLOWS.md` only for
  actual UX/navigation decisions.
- Avoid: ADRs, persistence/domain/gradle instructions, milestone reference doc.
- Critical rules: UDF, ViewModel never touches Room/infrastructure directly,
  string resources for user text.
- Stop conditions: ambiguous UX flow not covered by UX_FLOWS.md.
- Efficiency target: ≤ 900 words (add ~500 only if UX_FLOWS needed).

## 4. Gradle dependency or toolchain change
- Load: `AGENTS.md`, `.github/instructions/gradle.instructions.md`,
  `docs/TECHNICAL_DECISIONS.md` (dependency rationale authority).
- Avoid: ACTIVE_MILESTONE checkpoint detail, ADRs, domain/persistence/
  presentation instructions, milestone reference doc.
- Critical rules: version catalog only, JDK 17 pin unless recorded decision
  changes it, no unrelated upgrades, narrowest Gradle task.
- Stop conditions: new dependency *category* not already justified in
  TECHNICAL_DECISIONS.md.
- Efficiency target: ≤ 600 words.

## 5. Database migration
- Load: `AGENTS.md`, `ACTIVE_MILESTONE.md`, `.github/instructions/
  persistence.instructions.md`, ADR 0002 (migration policy is the core
  question here, so full ADR load is justified).
- Avoid: UX_FLOWS.md, domain/presentation/gradle instructions, ADR 0003.
- Critical rules: no destructive migration ever; version increment + migration
  + migration test required; schema changes need explicit approval per
  AGENTS.md stop conditions.
- Stop conditions: any destructive-migration path; migration not yet approved
  in ACTIVE_MILESTONE.md decisions.
- Efficiency target: ≤ 2300 words (ADR load dominates; justified, not routine).

## 6. Active milestone checkpoint continuation
- Load: `AGENTS.md`, `ACTIVE_MILESTONE.md`, active execution guide; the
  milestone reference doc only for the specific checkpoint's decision detail.
- Avoid: other milestones' docs, ADR 0001, README.
- Critical rules: work only the next incomplete checkpoint; run narrowest
  checks; update ACTIVE_MILESTONE.md; commits stay prohibited unless the
  active prompt explicitly authorizes a completion commit.
- Stop conditions: repeated verification failures; untracked/uncommitted
  unrelated changes present; instrumented tests cannot run (no device).
- Efficiency target: ≤ 1500 words (router + status + execution guide),
  + reference doc only per-checkpoint as needed.

## 7. Transition to the next milestone
- Load: `AGENTS.md`, `docs/ROADMAP.md`, current `ACTIVE_MILESTONE.md` (to
  confirm current milestone is actually Definition-of-Done complete — status
  must be factual, never inferred from plan text alone).
- Avoid: current milestone's detailed reference doc (only its DoD/checklist
  section matters at this point), unrelated ADR detail.
- Critical rules: never begin the next milestone until current DoD and
  verification gates pass; milestone status is factual, not inferred.
- Stop conditions: DoD incomplete; verification gate not actually executed.
- Efficiency target: ≤ 800 words to decide go/no-go before drafting a new
  execution guide.

## 8. Bug fix with no architecture implications
- Load: `AGENTS.md`, `.github/copilot-instructions.md`, the single
  path-specific instructions file matching the touched layer.
- Avoid: ACTIVE_MILESTONE.md detail beyond confirming scope, ADRs, other
  layers' instructions, milestone reference doc, ROADMAP.md.
- Critical rules: don't touch unrelated working-tree changes; report test
  results honestly; run the narrowest relevant check.
- Stop conditions: fix requires touching a layer boundary or dependency
  direction.
- Efficiency target: ≤ 500 words.
