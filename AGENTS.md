# RepFlow Agent Instructions

RepFlow is a native, offline-first Android workout tracker.

## Always read

Before non-trivial work, read only:

- `docs/ACTIVE_MILESTONE.md`
- `docs/ROADMAP.md`
- the active milestone execution guide referenced by
  `docs/ACTIVE_MILESTONE.md`

Follow `.github/copilot-instructions.md`.

## Read conditionally

Read only the documentation relevant to the task:

- Full decision text, invariants, or test details not in the execution file:
  `docs/milestones/active/milestone-1-reference.md`
- Domain models or business rules:
  `docs/DOMAIN_GLOSSARY.md`
- UI, navigation or user interaction:
  `docs/UX_FLOWS.md`
- Architecture, boundaries or modularization:
  `docs/adr/0003-layered-modular-architecture.md`
- Room, persistence, migrations or backup:
  `docs/adr/0002-offline-first-local-database-source-of-truth.md`
- Toolchain or dependency decisions:
  `docs/TECHNICAL_DECISIONS.md`
- Product-scope ambiguity:
  `docs/PROJECT_BRIEF.md`

Do not read every document by default.

## Workflow

For the active milestone:

1. Inspect only the files relevant to the next incomplete checkpoint.
2. Implement that checkpoint.
3. Run its narrowest relevant checks.
4. Review the resulting diff.
5. Update `docs/ACTIVE_MILESTONE.md`.
6. Continue unless there is a blocker.

Stop for:

- ambiguous product behavior;
- architecture changes;
- new dependency categories;
- schema migrations;
- destructive data operations;
- repeated verification failures;
- unrelated working-tree changes.

Never use destructive Room migrations.
Never claim a test passed unless it actually ran.

Commits are normally prohibited.

An active milestone prompt may explicitly authorize one completion commit after
all Definition of Done requirements and verification gates pass.

Never push, merge, rebase, force-push, or create a pull request.