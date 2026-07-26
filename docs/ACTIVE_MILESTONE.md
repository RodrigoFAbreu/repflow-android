# Active Milestone

## Milestone

Milestone 6 — Progression recommendations — **PLANNING COMPLETE, CP1 IN PROGRESS**

## Goal

After a workout is completed, compute a deterministic, local, explainable
`ProgressionRecommendation` (increase/maintain/reduce load, recovery
adjustment, or wait for more data) per exercise, storing the result,
reason, policy version, and any manual override.

## Current checkpoint

Planning complete: `docs/milestones/active/milestone-6-{execution,reference}.md`
created. Note: "Exact progression formulas and thresholds" is listed as
an explicit **Open** decision in `docs/TECHNICAL_DECISIONS.md`. Per the
reference doc's "Unresolved decision" section, this milestone ships a
clearly-labelled, isolated **Policy v1** with placeholder thresholds
(documented assumption, not a silent finalization) — mirrors how
Milestone 4's rest duration and Milestone 5's 0-4 scale bounds were
handled. CP1 (domain) starting next.

## Checkpoint checklist (Milestone 6)

- [ ] CP1 — Domain: `ProgressionRecommendation`, `ManualOverride`, `ProgressionPolicyV1` + tests
- [ ] CP2 — Application: use cases + tests
- [ ] CP3 — Room persistence: migration 5->6
- [ ] CP4 — Instrumented migration + DAO tests
- [ ] CP5 — Presentation: surface recommendation + override control
- [ ] CP6 — Full verification, manual smoke test, docs + archival

## Current blockers

None yet. The progression-formula "Open" decision is being handled per
the reference doc (explicit, flagged v1 assumption), not treated as a
blocker.

## Active plan

`docs/milestones/active/milestone-6-execution.md`,
`docs/milestones/active/milestone-6-reference.md`.

Milestones 3, 4, and 5 remain complete and committed; see
`docs/milestones/completed/`.
