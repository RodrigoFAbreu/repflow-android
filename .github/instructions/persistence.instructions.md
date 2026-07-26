---
applyTo: "app/src/main/**/infrastructure/database/**/*.kt,app/schemas/**,app/src/androidTest/**/*DaoTest.kt"
---

- Room is the local source of truth.
- Never use destructive migration fallback.
- Keep Room entities separate from domain models.
- Persist enum values by stable string, never ordinal.
- Database schema changes after version 1 require a version increment,
  migration and migration tests.
- Read ADR 0002 before changing persistence strategy.
- Do not swallow CancellationException; no broad catch around suspend work.