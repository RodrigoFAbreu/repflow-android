# suggest-load-only

No suggestion for timed, reps-only and single-set work

**Complexity:** medium. **Tier:** 2. Make suggestions trustworthy.

One milestone and one squash-merged pull request, releasable alone: the plan's pull request title must be a conventional commit (`fix:` for a correction, `feat:` for something new). The merge then publishes a GitHub Release with the APK; nothing in the repository holds a version number.

## App findings

- **Suggestions are wrong for timed and reps-only exercises** [A-4; decision 2; design item 3] · M. Three timed sets read "Fewer than 2 working sets", and a reps-only exercise can be told "Increase load". Single-set rows can never get past "Fewer than 2". Evidence: `CompleteWorkoutSession.kt:58`; the policy gets no tracking type. Both reviewers recommend no suggestion for these for now.

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**2. What should a suggestion say for timed, reps-only and single-set work?** [N-1, A-4]
- *Today:* wrong or useless advice on 16 of 34 exercises, and 6 single-set rows can never qualify.
- *Options:* (a) no suggestion, as the focus board for these types (20g) already draws; (b) new "add reps" and "add time" outcomes, with a new policy and boards.
- *Recommendation:* **(a) now,** then (b) only if you want progression on foot and mobility work. Both agree.

## Rules

The plan's header must carry exactly one line of this form, with nothing after the closing backtick: ``Pull request title: `fix: what changed` ``. See `AGENTS.md`, "Running under the Controller".

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
