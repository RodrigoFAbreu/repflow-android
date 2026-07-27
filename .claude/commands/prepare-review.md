---
description: Ad-hoc review bundle for work outside the milestone workflow gates.
---

For milestone-gated reviews (plan, implementation, post-fix, functional),
prefer `/milestone-plan`, `/milestone-implement`, `/apply-implementation-review`,
or `/prepare-functional-review` instead — they populate the review-bundle
content correctly for each state in `docs/ai-workflow/MILESTONE_WORKFLOW.md`.

Use this command only for a one-off review of work that isn't part of a
tracked milestone checkpoint.

1. Determine the correct base commit for what's being reviewed (usually the
   commit before this work started).
2. Write `.ai-review/current/REVIEW_REQUEST.md` per the format in
   `docs/ai-workflow/REVIEW_PROTOCOL.md`, and `IMPLEMENTATION_SUMMARY.md`/
   `TEST_RESULTS.md`/`CONTEXT_FILES.txt` as applicable. Never claim a check
   passed unless it actually ran.
3. Run `./scripts/prepare-ai-review.sh <base-sha> <stage>` (pick the closest
   matching stage: `plan`, `implementation`, `post-fix`, or
   `functional-review`) to generate `.ai-review/current/` and
   `.ai-review/review-bundle.tar.gz`.
4. Report the bundle location. Do not commit `.ai-review/`.
