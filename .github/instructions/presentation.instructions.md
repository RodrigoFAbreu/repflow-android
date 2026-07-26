---
applyTo: "app/src/main/**/presentation/**/*.kt,app/src/androidTest/**/presentation/**/*.kt"
---

- Use unidirectional data flow.
- Composables render state and emit events.
- ViewModels call application use cases, never Room or infrastructure directly.
- Use string resources for user-facing text.
- Prioritize one-handed use, large touch targets and minimal typing.
- Keep persistent state separate from one-off effects.
- Consult `docs/UX_FLOWS.md` only for actual UX decisions.