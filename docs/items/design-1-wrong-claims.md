# design-1-wrong-claims

Fix what the design gets wrong: the missing Hold band, recommendation text the app can't back, the empty recommendation state, restore dialog text, sample errors

**Complexity:** low. **Tier:** Design passes: run by me in Claude Design, no app change, checked by a reviewer.

Run by the orchestrator, not as a Workflow milestone. It stays `idea` so `/milestone-plan` never picks it, and is set to `done` by hand when finished; items that depend on it wait until then.

## Design findings

- **The fourth readiness band is missing, so 61 is labelled wrongly** [D-1] · S. The app has Ready, Hold, Back off, Protect, and 61 is Hold. Home's overnight card (10d) and the Recovery adjustment screen (30d) say "Back off", and the spec sheets list three bands. Evidence: `ReadinessScore.kt`, page 1 `BAND`. No app change.
- **Recommendation text claims reasons the app can't produce** [D-6; app item 5] · S. "…and readiness was Ready" and "you hit the top of the range, but a hold is safer". The policy doesn't read readiness, and it returns the hold before it reads reps. Evidence: `ProgressionPolicyV1.kt:16-24,89-96`.
- **The empty recommendation state is wrong** [D-7, D157; app items 4, 20] · S. Draw "Not enough data yet" properly, with its reason and action. "No suggestion yet" needs one line only, because no route reaches it without a record, and its drawn button can't work. Settled by tie-break. Evidence: `WorkoutSheets.kt:239`, `WorkoutFocus.kt:136`.
- **Restore dialog doesn't mention a running workout** [D-43; app item 2] · S. An earlier design answer says both dialogs do, but only Erase does. Evidence: boards 7b, 8a; `TrainingDataRepository.kt`.
- **Backup after a restore needs data the app doesn't store** [D-11] · S. "Restored from a file on 9 Oct" should become "No backup yet", which the app already shows; the earlier app request is withdrawn. Evidence: `AppSettings.kt`, board 40f.
- **An invalidated workout can't have a PR** [D-20] · S. Remove the badge from the sample on 30f.
- **"Bench sessions per week" needs a short name that doesn't exist** [D-21] · S. Use the app's wording. Boards 70c, 60b.
- **Sample errors** [D-22; app item 17] · S. Wrong month header, wrong weekdays, a mismatched title, "× 0:30", "Min sec", and "stored order" (40a).

## Owner decisions

The owner approved this backlog on 2026-11-04 with every recommendation below taken as decided.

**9. What does "Go with the suggestion" record?** [D-8]
- *Today:* nothing is stored, so "You went with the suggestion" only appears after a change of mind, and the text overstates what's saved. The drawn screen also repeats its choices.
- *Options:* (a) keep, and reword; (b) store acceptance as well.
- *Recommendation:* **(a),** and keep the app's two-step choice, dropping the control from the first screen. Design reviewer's recommendation; the app reviewer supplied the code evidence and made no separate choice.

## Rules

Evidence comes from the 2026-11-04 council; re-check it against today's code before relying on a line number. Ids such as `A-1` and `D-1` refer to its findings, kept in the lane's council folder. Keep the item small: one new mechanism at most, and split it before its first review if it needs two.
