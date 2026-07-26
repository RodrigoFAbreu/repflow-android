---
applyTo: "app/src/main/**/domain/**/*.kt,app/src/test/**/domain/**/*.kt"
---

- Domain code must be pure Kotlin.
- Do not import Android, AndroidX, Room, Hilt, javax.inject or coroutines.
- Enforce invariants in domain factories or value objects when appropriate.
- Use deterministic clocks and identifiers; tests must be fully deterministic.
- Consult `docs/DOMAIN_GLOSSARY.md` only when terminology is ambiguous.