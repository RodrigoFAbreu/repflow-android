---
description: Close out an explicitly accepted milestone and prepare the next one for planning.
---

Enter the `MILESTONE_COMPLETE` state of
`docs/ai-workflow/MILESTONE_WORKFLOW.md`. Only run this after the user has
explicitly accepted the milestone (`AWAITING_USER_ACCEPTANCE` exit
condition) — if that acceptance hasn't happened in this conversation, ask for
it before proceeding.

1. Confirm final verification already passed (from the last
   `/milestone-implement` or `/apply-implementation-review` run); rerun only
   if the working tree changed since.
2. Update `docs/ROADMAP.md` to mark the milestone complete.
3. Update `docs/ACTIVE_MILESTONE.md`: move this milestone's summary into the
   factual "complete" state, clear the active plan section.
4. Archive this milestone's execution/reference plans to
   `docs/milestones/completed/`.
5. Create the final completion commit if verification/doc updates are not
   already committed.
6. Set `docs/ACTIVE_MILESTONE.md`'s "Next action" to point at the next
   incomplete milestone in `docs/ROADMAP.md`, ready for `PLANNING`.
7. Report the milestone as complete and the next action as
   `/milestone-plan`. Do not begin implementing the next milestone in this
   command.
