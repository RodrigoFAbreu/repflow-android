# AGENTS.md

## Repository purpose

RepFlow is a native, offline-first Android workout tracker.

Before substantial work, read:

- `.github/copilot-instructions.md`
- `docs/PRODUCT_AND_ARCHITECTURE.md`
- `docs/TECHNICAL_DECISIONS.md`
- `docs/DOMAIN_GLOSSARY.md`
- `docs/UX_FLOWS.md`
- `docs/ROADMAP.md`

## Required workflow

For non-trivial tasks:

1. Inspect the current repository.
2. Identify the relevant documentation and existing code.
3. State assumptions.
4. Present a small ordered plan.
5. Wait for approval when architecture, dependencies, persistence, migrations,
   or broad multi-module changes are involved.
6. Implement only the approved scope.
7. Run relevant checks.
8. Summarize changed files and results.

## Scope control

Do not:

- Implement future roadmap milestones without being asked.
- Generate large amounts of empty scaffolding.
- Introduce a backend or authentication.
- Add dependencies merely because they are popular.
- Change architectural decisions silently.
- Commit, push, rebase, or force-push without explicit approval.
- Delete or overwrite unrelated local changes.
- Use destructive Room migrations.

## Definition of done

A change is complete when:

- It matches the requested behavior.
- Layer boundaries remain valid.
- Relevant tests are present and passing.
- Formatting and static checks pass.
- Failure and interruption behavior has been considered.
- Documentation is updated when a decision or public behavior changes.
- No unrelated files were modified.

## Commands

Prefer the Gradle wrapper:

```bash
./gradlew tasks
./gradlew build
./gradlew test
./gradlew lint
````

Use narrower module-specific tasks when practical.

Do not assume a command passed. Report its actual result.