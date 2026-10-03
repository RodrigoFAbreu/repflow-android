# RepFlow UX Flows

This document describes the flows the app delivers. A requirement the app
does not yet meet is **kept** and marked *declared intent*, with a pointer
to the `docs/improvements/IMPROVEMENT_ROADMAP.md` entry that owns it — a
requirement is never removed by rewriting the document around it. The
visual source of truth is the Claude Design project; every place the built
app differs from it, and why, is in the deviation register of
`docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`.

## UX principles

RepFlow is primarily used during workouts, often while standing, tired, and
holding the phone with one hand.

The UI should prioritize:

- Fast interaction
- Large touch targets
- Minimal typing
- Clear current-workout context
- Immediate feedback
- Safe recovery from interruptions
- Avoiding accidental destructive actions

Important workout actions should normally require no more than two or three
taps.

## Navigation

The bottom navigation carries four top-level destinations, in this order:
**Home** (where the app opens), **Plans**, **History** and **Progress**
(`docs/TECHNICAL_DECISIONS.md`, "Navigation structure").

- **Settings** opens from Home's gear. It is not a tab and shows no bottom
  navigation. The **exercise library** opens from Settings. Settings is
  grouped `Library`, `Units and appearance` (`Theme`: System, Light, Dark),
  `Rest timer` (including the app-wide `Default rest`), `During a workout`
  (including `Extra set fields`: Always shown, Collapsed, Off) and `Data`.
  `Data` holds `Archived exercises and plans`, a screen listing archived
  exercises and plans with `Restore`, and `Backup and restore`, a dedicated
  screen with `Export backup now` (the hero shows `Last backup <when>`),
  `Workout history as CSV` and `Restore from a backup`; both open from
  Settings only and show no bottom navigation.
- **Recovery entry** opens from Home's recovery card (`Log`); recovery and
  futsal history open from the entry screen.
- **Workout mode replaces the navigation.** The workout board, focus mode
  and the done screen draw no bottom navigation; leaving (`X`) or `Finish`
  are the only ways out.

## Home

The home screen shows:

1. The recommended workout for today — a plan, with its exercise and
   working-set counts and `Start workout` — or, with no plan yet, `Create a
   plan` and `Empty workout`. `Train something else` opens a sheet listing
   every active plan and `Empty workout`.
2. Resume active workout, when one exists — a resume card with `Resume`,
   `Finish it` and abandon, replacing the start card.
3. Recent workout summary — the last workout and its recent load increases.
4. Relevant recovery context — today's readiness score and band from the
   recovery check-in, with `Details` (how the score was set) and `Log`.
5. Quick access to plans, history and progress (the bottom navigation) and
   settings (the gear).

The active workout action has the strongest visual priority: while a
workout is running, `Resume` is the screen's one primary action.

## Start workout

The user selects or accepts a training plan (Home's start card, the start
sheet, or a plan's `Start workout` on the Plans tab), or starts an empty
workout and adds exercises as they go.

Before starting, RepFlow may show *(declared intent — not built; see
`IMPROVEMENT_ROADMAP.md` §9.7)*:

- Planned exercises *(Home's start card shows only their count)*
- Expected duration
- Recovery warning
- Recent futsal context *(shown once the workout has started, on the board)*
- Suggested adjustments

Starting a workout creates and immediately persists an active workout session.

## Active workout

The workout opens on a **board**: every exercise with its set status, the
next one marked `Up next`, progress across the session, recent recovery and
futsal context, and `Add exercise`. Tapping an exercise opens **focus
mode**, which works on one exercise at a time.

Focus mode shows:

- Exercise name
- Technique notes
- Current set number, and each planned set's target and rest
- Target sets and repetition range
- The progression suggestion for the exercise — the outcome (increase,
  maintain or reduce the load, or not enough data yet) with its top reason,
  and `Why ›` to the full recommendation
- Current rest status
- Completed sets, and the set just logged
- Previous performance: `Last time: 80 kg × 8`, from the last working set of
  the most recent valid session that logged the exercise, shown until the
  first set of this session is logged
- Suggested load as a figure *(declared intent — the suggestion names an
  outcome, not a load; `IMPROVEMENT_ROADMAP.md` §9.7)*

The user can:

- Increase or decrease load (a stepper by the exercise's load step, or the
  keypad)
- Increase or decrease repetitions (or seconds, for a timed exercise)
- Log the next set from the numbers already shown: after `Log set` the
  weight and reps (or seconds) stay and only RPE, pain, technique and the
  warm-up mark clear. A new exercise starts from last session's last working
  set (empty for one never done). `Log set` stays disabled until reps (or
  seconds) are present. `Undo last` corrects a mistaken set
- Record RPE
- Record optional technique quality
- Record optional pain
- Save the set (`Log set`)
- Mark a warm-up set
- Add an extra set beyond the plan's target
- Undo or correct the most recently logged set
- Skip or substitute the exercise *(declared intent — no domain backing;
  `IMPROVEMENT_ROADMAP.md` §9.2)*

Saving a set immediately persists the result.

## Rest timer

After saving a set, while Settings' `Start rest timer automatically` is on:

1. Start the rest timer for the plan row's rest, else the exercise's own
   `Default rest`, else Settings' `Default rest` (initially 1:30).
2. Store the absolute timer end timestamp.
3. Display the remaining duration, on the board and in focus mode.
4. Allow adding or removing time (`+15s` / `−15s`).
5. Allow skipping the timer.
6. When rest ends, notify the user (while `Rest timer notification` is on,
   and only where the notification permission is granted) and vibrate
   (while `Vibrate when rest ends` is on).

Returning to the application reconstructs the timer from the stored end
timestamp.

## Interrupted workout

When the application is reopened and an incomplete workout exists, the home
screen prominently offers:

- Resume workout (`Resume`)
- End workout (`Finish it`, which opens the finish sheet)
- Abandon this workout, with confirmation — the session is marked abandoned
  and its logged sets stay stored; nothing is deleted

The workout resumes at the correct exercise, set, values and rest state.

## Complete workout

`Finish` opens a sheet listing anything unfinished (while Settings'
`Confirm before finishing` is on), then saves. On completion, the done
screen shows:

- Duration
- Exercises completed
- Total working sets
- Each exercise's change versus the last comparable session, and any best
  set
- Relevant progression recommendations, each with `Why`
- Recovery or pain warnings *(declared intent — no warning rule exists;
  `IMPROVEMENT_ROADMAP.md` §9.7)*
- Optional workout notes *(declared intent — no notes field exists;
  `IMPROVEMENT_ROADMAP.md` §9.3)*

The completed workout becomes historical data and must not be changed by later
training-plan edits.

## Progress

The Progress tab shows one exercise at a time, most recently trained first.
A full-width exercise picker opens the `Track an exercise` sheet; three
metric buttons (`Top set`, `Est. 1RM`, `Volume`) and the range pills `3m`,
`6m`, `All` (by date, default `All`) drive a line chart whose readout follows
a tap or drag. Below it are `Sessions` and `Avg RPE` tiles for the range,
`Training frequency` (sessions per week over the last 8 weeks) and `Records`
(`Heaviest set`, `Best est. 1RM`; all-time, each dated). An exercise counts
as trained in a session only if it has at least one set there.

## Recovery entry

Recovery input is quick and uses `0–5` scale rows and toggles. One screen
records, for today or a chosen past date:

- Sleep quality
- Energy
- Leg DOMS
- Heel stiffness
- Pain while walking
- Heavy legs
- Futsal in previous 24 hours, and that session's minutes and RPE
- Futsal expected in next 24 hours

Free text (notes) remains optional. The check-in sets that day's readiness
score (`docs/TECHNICAL_DECISIONS.md`, "Readiness score").

## Error and destructive actions

Destructive actions:

- Are clearly labeled
- Require confirmation when data loss is possible — abandoning a workout,
  invalidating a completed workout, restoring a backup, and erasing all data
  (which also asks the user to type a confirmation word)
- Offer undo when practical (archiving an exercise or a plan)
- Never silently remove completed workout history — invalidating keeps the
  workout stored and hides it from History unless `Show invalidated` is on
