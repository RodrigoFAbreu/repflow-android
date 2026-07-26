---
mode: agent
---

Read `AGENTS.md`, then `docs/ACTIVE_MILESTONE.md`, then the active milestone
execution guide referenced there.

For each incomplete checkpoint in order:

1. Inspect only the files relevant to that checkpoint.
2. Implement it.
3. Run the narrowest verification command listed in the execution guide.
4. Review the resulting diff. Fix any confirmed defects.
5. Update `docs/ACTIVE_MILESTONE.md` (mark checkpoint complete, update
   last verified state).
6. Continue to the next checkpoint.

Load additional documentation only when AGENTS.md routes the task to it.

Keep progress output concise. Never commit or push.

Stop and report when:
- a blocking product or design decision is needed;
- an architecture boundary change is required;
- a schema migration has not been approved;
- a destructive operation would be required;
- working-tree changes unrelated to this autonomous session are present
  (changes created by earlier checkpoints in this session are expected);
- required instrumented tests cannot run on a device or emulator;
- the same failure recurs three times without resolution.

After all checkpoints complete, run the full milestone verification suite
defined in the execution guide. Report results honestly, including any tests
that did not actually run.

Never begin work on the next milestone.
