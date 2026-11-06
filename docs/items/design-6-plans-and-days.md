# design-6-plans-and-days

Design: the new names, plans with days, and Home's workout for today

**Complexity:** medium. Owner's request of 2026-11-05.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished.

On screen, a **plan** is the group ("4x a week"), and a **workout** is the list of exercises ("Legs"). A past one is still a workout, shown in History. Today's "Plans" become Workouts. Names in the code and in backup files don't change.

Draw on the Claude Design pages:

1. Every screen and message that says "plan" with the new names, and which existing boards change. The queued `plans-list` and `plan-editor-rows` items need re-reading against this.
2. A plan's screen: its workouts and their days, using a workout in a second plan, and creating, renaming and archiving a plan.
3. Choosing the active plan.
4. Home with today's workout, on a rest day, with today's already done, and with no plan set.
5. The note that editing a workout changes it in every plan that uses it.
