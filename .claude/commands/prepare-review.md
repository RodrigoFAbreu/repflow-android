Prepare the current work for external architectural and code review.

Determine the correct base commit for this review stage.

Create `.ai-review/current/` containing:

- REVIEW_REQUEST.md
- PLAN.md, when reviewing a plan
- IMPLEMENTATION_SUMMARY.md
- TEST_RESULTS.md
- CHANGED_FILES.txt
- COMMITS.txt
- DIFF.patch
- final copies of all changed files under `files/`
- any directly relevant unchanged context files needed to understand the change

REVIEW_REQUEST.md must state:

- branch, base SHA and HEAD SHA;
- review stage;
- goal;
- implementation decisions;
- schema or backup implications;
- tests actually run;
- known risks;
- unresolved questions;
- specific areas the reviewer should challenge.

Do not claim checks passed unless they actually ran.

Generate `.ai-review/review-bundle.tar.gz`.

Do not commit `.ai-review/`.