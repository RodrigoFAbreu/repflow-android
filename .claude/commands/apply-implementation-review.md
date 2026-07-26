---
description: Apply external implementation-review feedback and prepare for another review round if needed.
---

Enter the `APPLYING_REVIEW_FEEDBACK` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`.

1. Read `.ai-review/feedback/REVIEW_FEEDBACK.md`. If it does not exist, stop
   and say so.
2. Reproduce and validate every Blocking and Important finding against the
   actual code/tests before changing anything. Do not apply a finding you
   cannot reproduce or verify — reject it with evidence instead.
3. Fix all validated Blocking and Important findings. Consider Optional
   findings and Missing Tests findings; apply them if cheap and low-risk,
   otherwise note why not.
4. For any rejected finding, record the rejection with concrete evidence
   (file/line/test/doc reference) in `.ai-review/current/IMPLEMENTATION_SUMMARY.md`.
5. Rerun the relevant narrow tests for each fix, then the full suite used in
   `/milestone-implement` step 3 before closing this pass.
6. Commit coherent fixes (one commit per coherent fix, not one giant
   catch-all commit).
7. Regenerate the bundle at the `post-fix` stage:
   `./scripts/prepare-ai-review.sh <base-sha> post-fix`.
8. If any Blocking finding remains unresolved, or the fix was structurally
   significant, stay in `AWAITING_EXTERNAL_IMPLEMENTATION_REVIEW` and stop
   for another review round. Otherwise report readiness and proceed to
   `/prepare-functional-review`.
