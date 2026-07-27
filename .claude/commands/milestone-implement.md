---
description: Implement the approved plan checkpoint by checkpoint, then stop for external implementation review.
---

Enter the `IMPLEMENTING` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.
Requires an approved plan (from `/milestone-plan` + `/apply-plan-review`).

1. For each checkpoint in the approved plan, in order:
   - implement it, following `CLAUDE.md`/`AGENTS.md`/`.github/copilot-instructions.md`/
     `.github/instructions/*` for layer boundaries, migrations, and enum
     persistence rules;
   - run the narrowest relevant check (single test class/method, not the
     full suite);
   - review the resulting diff and fix confirmed defects;
   - update `docs/ACTIVE_MILESTONE.md` (checkpoint complete, verified state);
   - create the authorized intermediate commit for that checkpoint;
   - continue to the next checkpoint without stopping, unless a stop
     condition from `AGENTS.md` applies (ambiguous product behavior,
     architecture change, new dependency category, unapproved schema
     migration, destructive operation, repeated verification failure,
     unrelated working-tree changes).
2. When all checkpoints are implemented, enter
   `SELF_REVIEWING_IMPLEMENTATION`: review the full milestone diff for
   correctness, layer-boundary violations, missing tests, and
   maintainability. Fix all blocking and important findings.
3. Run the full required verification for the milestone (narrow checks are
   not sufficient at this point): `./gradlew spotlessCheck detekt lintDebug
   testDebugUnitTest`, plus `connectedDebugAndroidTest` if a device/emulator
   is available and the plan touches persistence/migrations. Report exactly
   what ran and its real result — never claim a check passed that did not
   run.
4. Enter `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW`:
   - write `.ai-review/current/IMPLEMENTATION_SUMMARY.md` (what was built,
     per checkpoint, and why);
   - write `.ai-review/current/TEST_RESULTS.md` (exact commands + results);
   - write `.ai-review/current/CONTEXT_FILES.txt` with only the unchanged
     docs a reviewer needs;
   - write `.ai-review/current/REVIEW_REQUEST.md` per
     `docs/ai-workflow/REVIEW_PROTOCOL.md` (stage: `implementation`);
   - run `./scripts/prepare-ai-review.sh <base-sha> implementation`, where
     `<base-sha>` is the milestone's starting commit.
5. Report the bundle location and **stop**. This is a hard gate — do not
   mark the milestone accepted, do not start the next milestone.
