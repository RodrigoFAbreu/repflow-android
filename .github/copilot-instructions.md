# RepFlow Repository Rules

- Work only on the active milestone and checkpoint in
  `docs/ACTIVE_MILESTONE.md`.
- Preserve Domain → Application → Data/Infrastructure → Presentation
  dependency direction.
- Domain must remain pure Kotlin.
- Do not expose Room entities or DAOs outside infrastructure/data.
- The local Room database is the MVP source of truth.
- Never use destructive database migrations.
- Do not add speculative abstractions or unused dependencies.
- Do not modify unrelated working-tree changes.
- Do not commit or push.
- Run the narrowest relevant checks after each checkpoint.
- Report failures honestly and concisely.
- Update `docs/ACTIVE_MILESTONE.md` after completing a checkpoint.
- Read additional documentation only when directed by `AGENTS.md` or when a
  concrete ambiguity requires it.