# Roadmap

<!-- Generated from docs/backlog.yaml by scripts/workflow_backlog.py. Do not edit by hand: edit the backlog file and run `python3 scripts/workflow_backlog.py generate`. -->

| Order | Item | Status | Size | Depends on |
| --- | --- | --- | --- | --- |
| 1 | Project foundation `milestone-0` | Done | - | - |
| 2 | Exercise library vertical slice `milestone-1` | Done | - | - |
| 3 | Training plans `milestone-2` | Done | - | - |
| 4 | Active workout `milestone-3` | Done | - | - |
| 5 | Rest timer `milestone-4` | Done | - | - |
| 6 | Recovery and futsal `milestone-5` | Done | - | - |
| 7 | Progression recommendations `milestone-6` | Done | - | - |
| 8 | History and backup `milestone-7` | Done | - | - |
| 9 | Post-MVP functional usability stabilization `milestone-8` | Done | - | - |
| 10 | Redesign: visual foundation `repflow-redesign-visual-foundation` | Done | - | - |
| 11 | [Move the backlog into \`docs/backlog.yaml\` with a generated roadmap page, run milestones on branches, and publish a GitHub Release with the APK for every \`feat:\` or \`fix:\` merge](items/chore-backlog-file.md) `chore-backlog-file` | Done | 1 | - |
| 12 | [Retire what the council marked outdated: old audits, finished roadmap items, D163-D165, old notes, dead code, the stale brief promise; retire the rest-after-single-set note](items/chore-archive.md) `chore-archive` | Idea | 1 | - |
| 13 | [Fix what the design gets wrong: the missing Hold band, recommendation text the app can't back, the empty recommendation state, restore dialog text, sample errors](items/design-1-wrong-claims.md) `design-1-wrong-claims` | Idea | 1 | - |
| 14 | [Fill in missing rules: pending set rows, fixed targets, first \`+\` on a timed set, context line with no check-in, archived exercises, notification text, loading shapes](items/design-2-rules.md) `design-2-rules` | Idea | 1 | design-1-wrong-claims |
| 15 | [Redraw only what's needed: full Settings, the workout board's lost Add exercise, permission rows and sheets, filter-empty and chart-empty states, the before-restore snapshot board](items/design-3-redraws.md) `design-3-redraws` | Idea | 2 | design-1-wrong-claims |
| 16 | [Make the spec sheet the single source: move rules out of the archive, add the board map, fix contrast and faint outlines, run the colour-blind check, archive old boards](items/design-4-spec.md) `design-4-spec` | Idea | 1 | design-2-rules |
| 17 | [A failed workout action shows a message, entry clears only on success, and \`Retry\` works](items/fix-workout-errors.md) `fix-workout-errors` | Next | 1 | - |
| 18 | [Backups include running and abandoned workouts, and restore warns about a running workout](items/fix-backup-all-workouts.md) `fix-backup-all-workouts` | Next | 2 | design-1-wrong-claims |
| 19 | [One automatic snapshot before a restore, restorable from Backup](items/restore-safety-snapshot.md) `restore-safety-snapshot` | Next | 2 | fix-backup-all-workouts, design-3-redraws |
| 20 | [A reminder on Home when the last backup is old](items/backup-reminder.md) `backup-reminder` | Next | 1 | restore-safety-snapshot |
| 21 | [An exercise you didn't train keeps its real suggestion](items/suggest-keep-untouched.md) `suggest-keep-untouched` | Next | 1 | - |
| 22 | [Top of the range with no RPE gives Maintain with the reason shown, and the \`NaN\` text goes](items/suggest-no-rpe.md) `suggest-no-rpe` | Next | 1 | - |
| 23 | [No suggestion for timed, reps-only and single-set work](items/suggest-load-only.md) `suggest-load-only` | Next | 1 | - |
| 24 | [Only today's check-in can hold back suggestions](items/suggest-today-checkin.md) `suggest-today-checkin` | Next | 1 | - |
| 25 | [Futsal becomes a note; only pain or heavy legs hold back, with a true 24 h window](items/futsal-not-hold.md) `futsal-not-hold` | Next | 1 | design-1-wrong-claims |
| 26 | [A futsal session can be removed, and a morning entry defaults to yesterday](items/futsal-fix-entry.md) `futsal-fix-entry` | Next | 2 | - |
| 27 | [The 'not enough data' screen matches the design, with \`Keep the same load\`](items/suggest-empty-state.md) `suggest-empty-state` | Next | 1 | suggest-load-only, design-1-wrong-claims |
| 28 | [Home offers the next plan in your list, not the one you just did](items/home-next-plan.md) `home-next-plan` | Next | 1 | - |
| 29 | [Tapping the rest notification opens the workout, with the right icon and text](items/rest-notification-tap.md) `rest-notification-tap` | Next | 1 | - |
| 30 | [No heads-up notification with the app open, and a stale one clears](items/rest-notification-foreground.md) `rest-notification-foreground` | Next | 1 | rest-notification-tap |
| 31 | [Timed sets use m:ss everywhere, and the shared number pad opens on the current value](items/timed-entry-mmss.md) `timed-entry-mmss` | Next | 2 | - |
| 32 | [Fixed targets read \`8 reps\` and \`5:00\`, not \`8-8\` and \`300-300s\`](items/fixed-target-text.md) `fixed-target-text` | Next | 1 | - |
| 33 | [A fixed target seeds the first set, and the first \`+\` starts at the bottom of the range](items/first-set-seed.md) `first-set-seed` | Next | 1 | design-2-rules |
| 34 | [Focus mode puts entry above the set list, and adds a previous-exercise button](items/focus-entry-first.md) `focus-entry-first` | Next | 1 | - |
| 35 | [Focus mode gets the 'Just logged' bar with Undo, the new row format, and the RPE panel closing after \`Log set\`](items/focus-just-logged.md) `focus-just-logged` | Next | 2 | focus-entry-first, design-2-rules |
| 36 | [Finishing with nothing logged asks first, instead of saving an empty workout](items/finish-empty-workout.md) `finish-empty-workout` | Next | 1 | - |
| 37 | [After 4 h a workout shows its start time, and Finish ends at the last set](items/overnight-workout.md) `overnight-workout` | Next | 1 | finish-empty-workout |
| 38 | [Hold a stepper to repeat](items/stepper-hold-repeat.md) `stepper-hold-repeat` | Next | 1 | - |
| 39 | [Plan editor: a new exercise joins the plan, archived exercises keep their name, and an unchanged save adds no version](items/fix-plan-editor-bugs.md) `fix-plan-editor-bugs` | Next | 2 | - |
| 40 | [Text levels and control colours match the spec, including the light-theme pill](items/theme-text-levels.md) `theme-text-levels` | Next | 1 | - |
| 41 | [Home header and cards as drawn](items/home-cards.md) `home-cards` | Next | 2 | home-next-plan |
| 42 | [Readiness sheet as drawn, with one scale order](items/readiness-sheet.md) `readiness-sheet` | Next | 1 | - |
| 43 | [Plans list: one list, Archived section, \`last run\`, Duplicate](items/plans-list.md) `plans-list` | Next | 2 | - |
| 44 | [Plan editor rows: drag handle, menu, rest format and presets](items/plan-editor-rows.md) `plan-editor-rows` | Next | 2 | - |
| 45 | [Workout board rows: \`Last time\` and logged summaries](items/workout-board-rows.md) `workout-board-rows` | Next | 1 | design-3-redraws |
| 46 | [Done screen tiles as drawn, and stat values no longer cut off at large font](items/done-and-stat-tiles.md) `done-and-stat-tiles` | Next | 1 | - |
| 47 | [Leave, Finish, Abandon and Discard: wording and button style; drop \`Irreversible\`](items/dialogs-and-sheets.md) `dialogs-and-sheets` | Next | 1 | - |
| 48 | [Exercise library title and create label, and no \`Create\` on an exact match](items/library-polish.md) `library-polish` | Next | 1 | - |
| 49 | [History: \`Clear filters\`, row and detail differences, exercise filter search](items/history-polish.md) `history-polish` | Next | 1 | - |
| 50 | [Progress timed units, and tappable Recovery history rows](items/progress-recovery-polish.md) `progress-recovery-polish` | Next | 1 | - |
| 51 | [Settings says when alerts are limited, with a way back after \`Not now\`](items/settings-alert-rows.md) `settings-alert-rows` | Next | 1 | design-3-redraws |
| 52 | [Recovery chart picks a day by dragging](items/recovery-chart-scrub.md) `recovery-chart-scrub` | Next | 1 | - |
| 53 | [Placeholder shapes instead of spinners](items/loading-placeholders.md) `loading-placeholders` | Next | 2 | design-2-rules |
| 54 | [Rest strip spacing, Home \`Log\` flicker, Progress check marks, small visual leftovers](items/small-polish.md) `small-polish` | Next | 1 | - |
| 55 | [Remaining copy differences](items/copy-pass.md) `copy-pass` | Next | 1 | - |
| 56 | [Warn when changing the tracking type of an exercise in a plan or with history](items/tracking-type-warning.md) `tracking-type-warning` | Next | 1 | - |
| 57 | [Remove the unused \`Optional\` switch from the plan editor](items/remove-optional-switch.md) `remove-optional-switch` | Next | 1 | - |
| 58 | [Bring the product docs up to date, including the pages-win rule](items/docs-refresh.md) `docs-refresh` | Next | 1 | - |
| 59 | [Remove an exercise added by mistake from a running workout (needs a small board)](items/workout-remove-exercise.md) `workout-remove-exercise` | Idea | 1 | - |
| 60 | [Per-exercise and per-session notes](items/exercise-notes.md) `exercise-notes` | Idea | 2 | - |
| 61 | [\`Add reps\` and \`Add time\` suggestions, if you want progression on foot and mobility work](items/suggest-reps-time.md) `suggest-reps-time` | Idea | 3 | - |
| 62 | [Accessibility pass, including the rest-end announcement](items/accessibility-audit.md) `accessibility-audit` | Idea | 2 | - |
