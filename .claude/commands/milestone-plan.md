---
description: Plan the next incomplete milestone/checkpoint and stop for external plan review.
---

Enter the `PLANNING` state of `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

Optional argument: `$ARGUMENTS` may name a base commit SHA to diff from. If
omitted, use the milestone's starting commit (the completion commit of the
previous milestone, from `docs/ACTIVE_MILESTONE.md`/`git log`).

1. Inspect Git state (`git status --short`, `git log --oneline -10`) and read
   `docs/ACTIVE_MILESTONE.md` and `docs/ROADMAP.md` to identify the next
   incomplete milestone/checkpoint.
2. Load only documentation relevant to that milestone: the linked execution
   guide, the reference guide only for unresolved detail, and any of
   `docs/DOMAIN_GLOSSARY.md`, `docs/UX_FLOWS.md`,
   `docs/adr/0002-offline-first-local-database-source-of-truth.md`,
   `docs/adr/0003-layered-modular-architecture.md`,
   `docs/TECHNICAL_DECISIONS.md` only as the task actually touches those
   areas. Do not read `docs/agent-context/` or completed-milestone docs
   unless a specific historical decision is unresolved.
3. Produce or update the execution/reference plan for this milestone
   (checkpoints, files touched, tests to add, migration needs if any).
4. Enter `SELF_REVIEWING_PLAN`: critically check the plan for missing
   requirements, migration risk, usability gaps, unnecessary complexity, and
   missing tests. Revise the plan in place — do not write a separate
   self-review journal.
5. Check the plan against every "Open decision" row in
   `docs/TECHNICAL_DECISIONS.md` it touches — flag any it would silently
   finalize instead of deciding for the user.
6. Enter `AWAITING_EXTERNAL_PLAN_REVIEW`:
   - write/refresh `.ai-review/current/PLAN.md` with the actual plan;
   - write `.ai-review/current/CONTEXT_FILES.txt` listing only the docs a
     reviewer genuinely needs beyond the plan itself;
   - write `.ai-review/current/REVIEW_REQUEST.md` per the format in
     `docs/ai-workflow/REVIEW_PROTOCOL.md` (stage: `plan`);
   - run `./scripts/prepare-ai-review.sh <base-sha> plan`.
7. Report the bundle location and **stop**. Do not implement anything. This
   is a hard gate — wait for `.ai-review/feedback/REVIEW_FEEDBACK.md`.
