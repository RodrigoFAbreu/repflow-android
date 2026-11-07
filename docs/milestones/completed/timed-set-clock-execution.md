# timed-set-clock — Execution Plan (Revision 2)

- Work item: `timed-set-clock` (backlog order 30, size 2, `docs/items/timed-set-clock.md`)
- Governing workflow version: `2.2`
- Base commit: `8cc7e5a4cc5c63cd68ba5f7939b88cdcb9bf8375`
- Plan revision: 2

Pull request title: `feat: a timed set has a clock, Start counts up and Stop fills in the time`

## Goal and scope

Don't lose data, don't hide failures. Today a timed exercise (a plank, a hold) is logged by typing or
stepping the seconds, so the user times the hold on another device and then types it in. After this
item a timed exercise has a `Clock` card above its Time card, as boards `20o` and `20p` draw it: `Start`
counts up in m:ss with the target in view, the card changes when the target is reached, and `Stop`
writes the elapsed time into the entry. The entry stays as editable as before, and typing a time by hand
still works.

The one new mechanism: the entry holds a clock **start moment**, never a running count. The readout is
derived from the wall clock on every tick, the way the rest timer derives its remaining time from an
end timestamp and the top bar derives the workout's elapsed time (`rememberElapsedSeconds`). So locking
the phone, rotating, visiting the Board or losing the process does not lose it.

**Where the start moment lives.** In `SetEntryState`, next to the `seconds` it will fill: that class is
already per exercise, already held above focus mode (`FocusEntries`, GF-1) and already saved across a
rotation and process death. The time the clock stopped at lives beside it. Neither is written to Room.
They belong to the unlogged entry, which is not stored either. This is where the clock differs from the
rest timer: the rest timer's end is in Room (`workout_sessions.rest_timer_end_at_epoch_ms`), so it
survives everything, and this clock survives only what keeps the saved state. "Known limitations" lists
what loses it, and "Decisions to confirm" puts the choice to the owner.

**Out of scope:** the buzz, the sound and the notification at the target (`timed-set-alert`), a clock for
reps-only or weight-and-reps exercises, a pause, lap or countdown mode, cancelling a `Start`, showing the
clock on the Board, and any schema, backup or dependency change.

## Design

### What the boards draw

Claude Design page `2 Active workout`, read at etag 1793938492642248. The page says "Where a 20x drawing
differs from an older artboard, the 20x drawing wins." The implementer re-reads the page before CP2, and
records any change since this etag in the milestone file.

`20g`: "the clock with Start and Stop is on 20o and 20p".

`20o` (fixed target): "**Before Start:** 0:00, "Target 1:00" and a Start button (accent outline, 52dp). The
Time box is already seeded with 1:00 (20m, fixed target), so a time can still be typed or stepped by hand
without ever using the clock, and Log set works as today. **Running:** the time counts up and the button
is Stop; Log set is dimmed until Stop. **Past the target:** the time turns accent, the card gets an accent
edge and the line reads "Target 1:00 reached · still counting", with a check mark so colour is not the
only cue. The clock keeps counting. **Stopped:** the time stays on screen ("Stopped at 1:07") and the Time
box now reads 1:07 with "Filled in from the clock. Change it here before Log set.". It can still be stepped
or typed, then Log set. The button reads Start again; starting again counts from 0:00 and fills the box
again on Stop. The clock keeps its start time, so locking the phone does not lose it."

`20p` (target range): "Before Start and Running read "Target 0:30–0:45 · alerts at 0:30". Once the clock
passes the bottom of the range the line reads "Passed 0:30 · still counting", also while it is still inside
the range. The Time box shows — until Stop, because a range has no single number to seed (the first + would
start at 0:30, as in 20m), and Log set stays dimmed while it shows —. After Stop at 0:38 the box reads 0:38.
Stepping or typing by hand works in every state."

`20q` (the alert): "With RepFlow in front there is no notification: the buzz and the change to the
past-target card (20o, 20p) are the alert."

`20r` (Start during a rest): "**Rest running, Start tapped:** Start ends the rest (the strip goes and its
alert is cancelled, so no "Rest done" arrives later) and the clock begins at 0:00. **After Log set:** the
rest starts then, as it does today, from the plan or default rest; the clock is back at 0:00 and Start is
offered for the next set. Stop never starts a rest; it only fills the time in. The Time box keeps the logged
time for the next set, as steppers do (20m)."

Each state as the boards draw it, in the board's own structure (`clockCard` in the page's script): a card
labelled `Clock`, then the time at 40 sp, then one line with a glyph, then one button, directly above the
`Time (m:ss)` card.

| State | Time at 40 sp | The line | The button | The Time card and `Log set` |
|---|---|---|---|---|
| Before Start | `0:00` | timer glyph, `Target 1:00` | play glyph, `Start`, accent outline, 52 dp | Hint `Steps of 0:05. Tap the value to type m:ss.` `Log set` as today |
| Running | counts up | timer glyph, `Target 1:00 · counting up` | stop glyph, `Stop`, accent outline, 52 dp | Same hint, still editable. `Log set` disabled |
| Past the target | counts up, in accent | check glyph, `Target 1:00 reached · still counting` or `Passed 0:30 · still counting`; the card has an accent edge | as Running | as Running |
| Stopped | stays, `1:07` | stop glyph, `Stopped at 1:07` | `Start again`, neutral outline, 48 dp, no glyph | Reads `1:07`, hint `Filled in from the clock. Change it here before Log set.` `Log set` enabled |
| After a saved set | `0:00` | as Before Start | as Before Start | Keeps the logged time, normal hint |

**Deliberate differences from the boards:**

| The board | This plan | Why |
|---|---|---|
| `20p`: Before Start and Running read `Target 0:30–0:45 · alerts at 0:30` | `Target 0:30–0:45` before Start, `Target 0:30–0:45 · counting up` while running, which is `20o`'s wording | No alert exists until `timed-set-alert` ships, so the line would be false in this release. CP2 writes the line onto that item's page |
| `20o`: the Time box is "already seeded with 1:00 (20m, fixed target)" | A never-done exercise's Time card stays `—` until a time is typed or `Stop` fills it | The seed from the plan belongs to the backlog item `first-set-seed`; `entrySeedOf` says the plan's target is "never a seed" today |
| The card's radius is 12 | `RepFlowCard`'s 16 | The same as the Time card under it and every stepper card today |
| The line's glyphs are `ph-bold` | The app's existing `timer` and `check`, at their regular weight | The app already draws them on small lines like this one. CP2 adds bold play and stop for the button and the stopped line |

Older differences this item leaves alone: the set list sits above the entry rather than below it, the type
note `20g` drops is still shown, and the Time card's label is centred. The clock card follows the board and
is left-aligned; the wrap-up reports how that sits above the centred label.

**Cases the boards do not draw:**

- **A stop under one second:** the card goes back to Before Start and the entry stays as it was. `Stopped at
  0:00` would claim a time that was never filled in.
- **No target** (an ad-hoc timed exercise, `durationRangeSeconds` null): no line before Start or while
  running, and Past the target never happens. Stopped reads as drawn.
- **The Time box changed after Stop:** `Stopped at 1:07` stays, because it is still true. The hint goes back
  to the normal one, because the box no longer holds the clock's time.
- **`Rest done` showing when Start is tapped:** it clears too. The rest is over and the next set has started,
  and `20b` already clears it when the next set is logged.

**Reached means reached.** A fixed target's card changes at exactly the target and a range's at exactly its
bottom (`elapsed >= durationRangeSeconds.first`), the moment `20q`'s alert will fire.

### Decided, not open

- **`Log set` is disabled until `Stop`.** `20o`: "Log set is dimmed until Stop". This follows the board and
  is no longer a decision to confirm. `canLog` is the one rule, so a warm-up's button follows it as well.
- **Start ends a running rest.** The owner decided on 2026-11-06: "Start ends a running rest and cancels its
  alert; the next rest starts after `Log set`; Stop never starts a rest." That is `20r`'s drawing, whose
  heading still reads "owner to choose" and is out of date. The decision is recorded in the lane's notes
  (`owner_features_2026_11_05`), not in the repository, so this plan states it in full.
- **Past the target is done here.** `timed-set-alert` asks only for buzz and sound, and the card's change
  derives from the same start moment and target this item already has. CP2 records on that item's page that
  the card is done.

### The clock state (CP1)

`SetEntryState` gains `clockStartedAtMillis: Long?` (epoch milliseconds, `null` when no clock runs) and
`clockStoppedSeconds: Long?` (the time the clock last stopped at, `null` when it has not, or has since started
again or been logged), both read-only from outside, and:

- `startClock(nowMillis: Long)` sets the start moment and clears the stopped time. Calling it while a clock
  runs changes nothing.
- `elapsedSeconds(nowMillis: Long): Long` is `(nowMillis - start) / 1000` floored and never negative (a wall
  clock set back can make the difference negative), or `0` when no clock runs.
- `stopClock(nowMillis: Long)` clears the start moment and, if at least one whole second has passed, calls
  `enterSeconds` with the elapsed seconds, capped at `Int.MAX_VALUE` because the value is later read with
  `toInt()`, and records them as the stopped time. `enterSeconds` also sets `touched`, so the seed never
  replaces the filled-in time. Under one second it only clears the clock and leaves the entry as it was.
- `filledFromClock: Boolean`, a property: a stopped time exists and the entry's seconds still equal it.

`canLog` returns `false` while a clock runs, whatever the tracking type.

**`clearAfterSet` now touches the clock.** `SubmittedDetail` gains the stopped time, and `clearAfterSet` clears
it if it still holds what was submitted, the same rule RPE, pain and technique follow. So the clock goes back
to `0:00` once a set has saved, a failed save clears nothing (the existing rule), and a `Start again` during
the save is not undone. `Log set` cannot run while a clock does, so `clearAfterSet` never meets a running one
from the same tap. A clock started during the save is left running.

Both values are part of `toSaved()`/`fromSaved()`, appended as new last elements and read with `getOrNull`, so
a list saved by an older build restores with no clock. The class goes from 9 functions to 12, over detekt's
`TooManyFunctions` limit of 11, so it takes `@Suppress("TooManyFunctions")`, as `WorkoutSession.kt:23` does.

### The card (CP2)

**The clock is its own card, drawn by `WorkoutFocus`.** A new `TimedSetClockCard` in a new file,
`TimedSetClock.kt` (`WorkoutFocusEntry.kt` already has 10 top-level functions and detekt's limit is 11), sits
directly above `EntrySteppers`, only for `ExerciseTrackingType.DURATION`. Weight-and-reps and reps-only
exercises are drawn as today.

**The correction sheet is untouched** because the clock doesn't go through `EntrySteppers`. Both
`SetCorrectionContent` and `EntryStepperWidthTest` call `EntrySteppers`, so they cannot gain a `Start`. `20i`'s
`Timed set` variant draws no clock, and a CP2 test holds that.

**`EntrySteppers` and `SecondsCard` change in two ways only.** They lose `durationRange` and the lowercase
`target 0:30–1:30` caption it draws, because the target moves to the clock's line and the boards' Time card has
no caption. Only `WorkoutFocus` passed a range, so no other screen loses anything, and a fixed target stops
reading `target 1:00–1:00`. They gain `filledFromClock: Boolean = false`, which picks the hint. `SetCorrectionContent`
and `EntryStepperWidthTest` drop their `durationRange = null` argument and nothing else.

**The readout** comes from `rememberElapsedSeconds(Instant.ofEpochMilli(start)).coerceAtLeast(0)`: the helper
the top bar uses, reused as it is, and clamped at this call site because the helper does not clamp
(`WorkoutBoard.kt:396`). Without the clamp a clock set back past the start moment would read `0:-5`. The other
callers of the helper are not this item's. It composes only while a clock runs.

**The buttons and the edge use what exists.** `RepFlowAccentOutlineButton` floors at 48 dp, so `Start` and
`Stop` take `Modifier.heightIn(min = 52.dp)`, as the Board's `Add exercise` does (`WorkoutBoard.kt:122`), and
`Start again` is a `RepFlowNeutralOutlineButton` with 48 dp the same way. All three take the card's width. The
past-target edge is a `Modifier.border` in `repFlowSelectedPillColors`' border over the card's hairline, the
time is `repFlowAccentOutlineColors`' label, and `RepFlowCard` does not change. The wrap-up compares both with
the board, in both themes.

**Accessibility.** The readout is not a live region: a value that changes every second would make a screen
reader announce itself continuously. It has a content description in words (`spokenDuration`, from
`timed-entry-mmss`), read when the user focuses it, as the top bar's elapsed time does (`WorkoutFocus.kt:293`).
The line carries the same words (`Stopped at 1 minute 7 seconds`) from the same string with the spoken form as
its argument. `Start`, `Stop` and `Start again` are ordinary labelled buttons. The check mark and the words mean
colour is never the only cue. Reaching the target is not announced; the buzz is `timed-set-alert`'s.

**The tap's time is passed in.** `WorkoutFocus` gains `nowMillis: () -> Long = System::currentTimeMillis`, read
at each tap, so a test can hold it. The readout stays on the real clock, so a test's assertions on the readout
are bounds and its assertions on what `Stop` writes are exact.

**New strings:** `Clock`, `Start`, `Stop`, `Start again`, `Target %1$s`, `Target %1$s–%2$s`, `%1$s · counting up`,
`Target %1$s reached · still counting`, `Passed %1$s · still counting`, `Stopped at %1$s` and the Time card's
filled-in hint. `workout_focus_seconds_target` goes, since nothing draws it any more.

### Leaving and resting (CP3)

**`Leave it running` keeps the clock.** Today `FocusEntries` is a `rememberSaveable` inside the workout
destination (`ActiveWorkoutScreen.kt:312`), `leaveWorkoutForHome` pops that destination without saving its state
(`RepFlowNavigationActions.kt:17-22`), and Home's `Resume` navigates to a new entry (`RepFlowNavHost.kt:88`). So
a plank started and then left through that sheet would come back with `Start` offered and the time gone, right
after the sheet promised "the session stays open and the clock keeps running from its start time"
(`strings.xml:574`).

The holder moves above the `NavHost`. `RepFlowNavHost` holds `rememberSaveable(saver = FocusEntries.Saver)` and
passes it to `ActiveWorkoutRoute`, as a required parameter so a host can't forget it, and on to
`ActiveWorkoutScreen`, where it defaults to the screen's own `rememberSaveable` so the screen's many tests don't
change. Both functions become `internal`, as `FocusEntries` is. This moves an existing holder; it adds no
mechanism, column or Gradle module. Navigation's own `saveState`/`restoreState` was the other way, but it would
also bring back `overlay`, which is `LEAVE_SHEET` at that moment, and the focused exercise.

`FocusEntries.retainOnly(ids)`, run from `WorkoutMode` in a `LaunchedEffect(content.sessionId)`, drops the entries
of earlier sessions, so the holder doesn't grow for as long as the activity's saved state lives. Exercise ids are
generated per session (`StartWorkoutSessionFromPlan.kt:83`), so an old entry could never reach a new session in
any case.

**What the leave sheet tells the user:** nothing new. Its line is now true of the set clock as well, apart from
what discards saved state, listed in the known limitations. `Resume` opens the Board as it does today, and opening
the exercise shows the clock still counting. A side effect, and the one `20m` asks for ("Leaving and coming back"):
typed but unlogged numbers now survive the same path.

**Start ends a rest.** `WorkoutFocus` gains `onClockStarted: () -> Unit = {}`, called on `Start` and `Start again`.
`ActiveWorkoutScreen` wires it to `onSkipRestTimer` when `content.restTimer != null`. `SkipRestTimer` is a no-op
with no rest, but that would still write the session. The strip goes because `restTimer` goes, and the existing
`RestAlarmEffect` cancels the alert when `restEndAt` turns null (`ActiveWorkoutRoute.kt:182`), so no `Rest done`
arrives later. `Stop` calls neither, and the next rest starts through `onRecordSet` as today. A failed skip shows
the app's one generic message and leaves the rest running, and the clock keeps counting.

### What does not change

`Log set` stores the entry's seconds through the same `onRecordSet` as today. The correction sheet, the plan editor,
History and the suggestion are untouched. No Android type enters `domain` or `application`, and `LayerBoundaryTest`
stays green. No "Open decision" row in `docs/TECHNICAL_DECISIONS.md` is decided here.

## Requirements

| Id | Requirement |
|---|---|
| R1 | A timed exercise shows a `Clock` card above its Time card, reading `0:00`, the target line and `Start`; a weight-and-reps or reps-only exercise shows none and looks as before, and a logged timed set's correction sheet shows no clock |
| R2 | `Start` makes the card count up in m:ss from a stored start moment, with the target line in view and `Stop` in `Start`'s place; on reaching the target the time turns accent, the card gets an accent edge, and a check mark and words say so while it keeps counting; the readout never shows a negative time |
| R3 | `Stop` writes the elapsed whole seconds into the entry, where the stepper and pad can still change them before `Log set`; the card keeps the time with `Stopped at` and offers `Start again`, and the Time card says it was filled in; once a set has saved the clock is back at `0:00` with `Start` offered and the Time card keeps its value, and a failed save changes nothing; a stop under one second leaves the entry as it was and goes back to before Start; stopping never logs a set |
| R4 | The running clock survives a rotation, a trip to the Board, switching exercise, process death, and `Leave it running` followed by `Resume`, because it is derived from the start moment; `Log set` is disabled while it runs, so a set can't be logged with a stale time |
| R5 | `Start` ends a running rest and cancels its alert, `Stop` never starts a rest, and the next rest starts after `Log set` as today |
| R6 | Typing or stepping a time by hand works as before; nothing is stored in Room, the backup or the CSV; the clock is readable by a screen reader and fits at font scale 2.0 |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | The entry holds the clock's start moment and stopped time (R2, R3, R4) | - | 2 | 1 |
| CP2 | The Clock card draws the boards' states (R1, R2, R3, R4, R6) | CP1 | 3 | 1 |
| CP3 | The clock survives leaving, and Start ends a rest (R4, R5) | CP2 | 2 | 1 |

<!-- CP1 -->
### CP1 — The entry holds the clock's start moment and stopped time (R2, R3, R4)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/SetEntryStateTest.kt`

What: the clock state above, the `canLog` rule, `clearAfterSet` and the saved form. The screen does not use them
yet, so the screen behaves as before.

New JVM tests in `SetEntryStateTest`, each against a plain `SetEntryState` with the time passed in:

1. `elapsedSeconds` is derived from the start moment and the time given, not counted: started at `T`, it reads 0
   at `T + 999`, 1 at `T + 1000` and 90 at `T + 90_500`, and the same call twice gives the same answer.
2. `stopClock` at `T + 90_500` leaves the entry's seconds at 90, `touched` true, no clock running, a stopped time of
   90 and `filledFromClock` true. An entry seeded at 30 and untouched holds 90 afterwards and a later `applySeed`
   does not replace it.
3. `stopClock` under a second (`T + 999`) leaves the seconds as they were (a seeded 30 stays 30, an empty entry stays
   empty), `touched` as it was, the clock cleared and no stopped time.
4. A wall clock set back (`now` before the start) reads 0 and stops without writing.
5. `canLog(DURATION)` is false while a clock runs even with seconds filled in, and true again after `stopClock`. It is
   true for the other tracking types' own rules, which are unchanged.
6. `startClock` while a clock runs keeps the first start moment. `startClock` after a stop clears the stopped time and
   leaves the seconds until the next stop replaces them.
7. Entering seconds after a stop makes `filledFromClock` false and keeps the stopped time.
8. `clearAfterSet` with what was submitted clears the stopped time and leaves the seconds. A stopped time that changed
   since the tap stays, and a clock started since the tap keeps running.
9. The saved form round-trips a running clock with its exact start moment, and a stopped time. A list saved without
   the new elements restores with neither. `FocusEntries.Saver` round-trips them for the right exercise.

Mutations, one per run, each caught by a test above: `elapsedSeconds` rounding up instead of down (1), a stored running
count in place of the start moment (1), `stopClock` not calling `enterSeconds` (2), `stopClock` not recording the
stopped time (2), `stopClock` writing 0 under a second (3), a negative elapsed written (4), `canLog` ignoring the clock
(5), `startClock` replacing a running start (6), `startClock` keeping the old stopped time (6), `filledFromClock`
ignoring the seconds (7), `clearAfterSet` leaving the stopped time (8), `clearAfterSet` clearing a changed one (8), and
the clock left out of `toSaved()` (9). A control run of the same tests passes before and after, and the files' bytes
are restored.
<!-- /CP1 -->

<!-- CP2 -->
### CP2 — The Clock card draws the boards' states (R1, R2, R3, R4, R6)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/workout/TimedSetClock.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusSets.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/icons/RepFlowIcons.kt`,
`app/src/main/res/drawable/ic_ph_play_bold.xml`,
`app/src/main/res/drawable/ic_ph_stop_bold.xml`,
`app/src/main/res/values/strings.xml`,
`app/src/test/kotlin/com/repflow/app/presentation/designsystem/icons/RepFlowIconsTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreenTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/EntryStepperWidthTest.kt`,
`docs/UX_FLOWS.md`,
`docs/items/timed-set-alert.md`

What: the card, the Time card's two changes and the strings above. The two glyphs are copied verbatim from Phosphor's
upstream SVGs, as `RepFlowIcons` requires, and `RepFlowIconsTest`'s expected set gains them. The existing test
`theTimeCardsTargetReadsAsClocks` moves to the new line's string. `docs/UX_FLOWS.md` says a timed exercise has a clock,
what `Stop` does, that `Log set` waits for `Stop`, and what happens after `Log set`. `docs/items/timed-set-alert.md`
gains: the past-target card is done, and this item adds the buzz, the sound and the notification, and puts `· alerts at
0:30` back on the range's line as `20p` draws it.

New instrumented tests in `ActiveWorkoutScreenTest`, mounting `WorkoutFocus` with an entry the test builds, as the
file's existing tests do at the line that calls `WorkoutFocus(`. A test seeds an elapsed time by starting the entry's
clock in the past, so nothing sleeps. The time a tap reads is held through `nowMillis`, so what `Stop` writes is exact.
The readout stays on the real clock, so **no test asserts an exact readout**: each reads it through a test tag and
asserts a bound.

1. A timed exercise with a fixed target shows `Clock`, `0:00`, `Target 1:00` and `Start`, with the card above the Time
   card. A range shows `Target 0:30–0:45`, and `alerts at` appears nowhere. With no target it shows `0:00` and `Start`
   and no line. A weight-and-reps and a reps-only exercise show no `Clock` and no `Start` (R1).
2. With seconds filled in first, so that a disabled `Log set` can only mean the clock, tapping `Start` shows `Stop` in
   its place, a readout under a minute and `Target 1:00 · counting up`, and `Log set` is disabled. The stepper still
   changes the entry (R2, R4).
3. A clock started 67 s ago on a fixed 60 s target reads `Target 1:00 reached · still counting`, its readout is at least
   `1:07`, and `Stop` is offered. A clock started 38 s ago on 30–45 reads `Passed 0:30 · still counting`. A clock under
   the target reads `· counting up`. The card's top edge, read from a captured image, is the accent border past the
   target and the hairline before it (R2).
4. A clock whose start moment is in the future reads `0:00`, and no text on the card holds a minus sign (R2).
5. With the clock started 90 s before the held tap time, `Stop` makes the stepper read `1:30`, the card reads
   `Stopped at 1:30`, `Start again` replaces `Stop`, and the hint reads `Filled in from the clock. Change it here before
   Log set.` `Log set` is enabled and nothing has been reported. Tapping it reports exactly 90 seconds through
   `onRecordSet`. When the save succeeds the readout is `0:00`, `Start` is offered, the stepper still reads `1:30`, and the
   hint is the normal one. When it fails, `Stopped at 1:30` and `Start again` stay (R3).
6. After `Stop`, tapping the stepper's value opens the pad on `1:30` and a typed `1 4 0` makes the entry 100, which `Log
   set` reports. `Stopped at 1:30` stays and the hint goes back to the normal one. `Start again` then reads a bounded
   readout under a minute and refills on `Stop`. Without any `Start`, the pad and the stepper's buttons still work as
   before (R3, R6).
7. `Stop` 999 ms after the start goes back to `0:00` and `Start`, shows no `Stopped at`, and leaves the entry's seconds
   as they were (R3).
8. Recreating the activity (`recreateOnto`) with a clock started 90 s ago keeps `Stop` and a readout of at least `1:30`,
   and with a stopped clock keeps `Stopped at 1:30` and `Start again`. The initial lambda of `setFocus`'s `SetEntryState`
   seeds them (R4).
9. The readout's content description is the spoken form (`1 minute 30 seconds` on a stopped clock), it is not a live
   region, the stopped line's description is `Stopped at 1 minute 30 seconds`, and `Start`, `Stop` and `Start again` are
   buttons with their labels (R6).
10. At font scale 2.0 the card's `Start`, then the readout and `Stop`, then `Stopped at` and `Start again`, are fully on
    screen and not clipped. `Start` and `Stop` are at least 52 dp tall and `Start again` at least 48 dp. Both light and
    dark compose without error (R6).
11. The correction sheet of a logged timed set, opened while the entry's clock runs, shows no `Clock` and no `Start`, and
    its Time card keeps the normal hint (R1).

Mutations, one per run, each caught by a test above: a clock on a weight-and-reps exercise (1), `alerts at` back on the
line (1), `Log set` enabled while running (2), the past-target line never shown (3), the accent edge left out (3), the
clamp removed (4), `Stop` not filling the entry (5), `Stop` also logging the set (5), `Start` offered at once after `Stop`
instead of `Start again` (5), the stopped state not cleared after a saved set (5), the stopped state cleared before the
save succeeds (5), the filled-in hint kept after the value changes (6), `Stopped at 0:00` under a second (7), the readout
counting from composition instead of the start moment (8), the readout a live region (9), the spoken description removed
(9), the 52 dp height removed, which floors at the button's own 48 (10), and the clock drawn inside `EntrySteppers` (11). A
control run of the same tests passes before and after, and the production files are confirmed byte-identical by hash after
the batch.
<!-- /CP2 -->

<!-- CP3 -->
### CP3 — The clock survives leaving, and Start ends a rest (R4, R5)

**Files:**
`app/src/main/kotlin/com/repflow/app/presentation/navigation/RepFlowNavHost.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutRoute.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutScreen.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocus.kt`,
`app/src/main/kotlin/com/repflow/app/presentation/workout/WorkoutFocusEntry.kt`,
`app/src/test/kotlin/com/repflow/app/presentation/workout/SetEntryStateTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutSetEntryTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutLeaveRouteTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/workout/ActiveWorkoutErrorRouteTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/presentation/progression/ProgressionRecommendationRouteTest.kt`,
`docs/UX_FLOWS.md`

What: the holder above the `NavHost`, `retainOnly`, and `Start` ending a rest. `docs/UX_FLOWS.md`'s rest section says
`Start` ends a running rest, and its interrupted-workout section says leaving for Home keeps a running set clock and what
loses it.

New tests. Tests 1 to 3 drive the real `ActiveWorkoutScreen` in `ActiveWorkoutSetEntryTest`, whose harness counts
`onSkipRestTimer` and can hold a rest. Each waits with `waitUntil` for the readout to reach `0:02` and compares it
afterwards, so no time is asserted exactly.

1. **Switching exercise.** On a timed exercise and a second one, `Start`, wait, `Next`, then back through the Board
   (`goToBoardAndReopen`). `Stop` is there and the readout has not dropped. The second exercise shows no `Stop` (R4).
2. **A trip to the Board.** `Start`, wait, the Board and back. `Stop` is there and the readout has not dropped (R4).
3. **Rest.** With a rest running, `Start` calls `onSkipRestTimer` once. With none running it doesn't call it. `Stop` calls
   neither `onSkipRestTimer` nor `onRecordSet`. `Start again` with a rest running calls it once more (R5).
4. **Leaving.** In `ActiveWorkoutLeaveRouteTest`, over real Room and the real routes, with its host holding `FocusEntries`
   above its `NavHost` as `RepFlowNavHost` does: open a timed exercise, `Start`, wait, back, `Leave it running and go Home`,
   `Resume`, open the exercise. `Stop` is there and the readout has not dropped. `Stop` and `Log set` store that time in Room
   (R4).
5. **JVM,** in `SetEntryStateTest`: `FocusEntries.retainOnly` keeps a listed exercise's entry with its running clock and
   drops the others.

Mutations, one per run, each caught by a test above: a new entry made on each focus (1, 2), `Start` not ending the rest (3),
`Start` skipping with no rest running (3), `Stop` skipping the rest (3), the test host's holder moved back inside the workout
destination (4), and `retainOnly` dropping the listed entries (5). `RepFlowNavHost`'s own wiring is held by the wrap-up below,
which walks the real `NavHost`. A control run of the same tests passes before and after, and the production files are
confirmed byte-identical by hash after the batch.

**The wrap-up** runs in the real app on the AVD, with a `Plank` made through the app, and reports each step:

1. **The hold:** `Start`, wait for the readout to pass a few seconds, then lock and unlock the screen (the AVD's power key),
   rotate, visit the Board and come back, and switch exercise and come back. The clock keeps counting through each.
2. **Leaving:** back twice, `Leave it running and go Home`, `Resume`, open the exercise. The clock is still counting.
3. **Process death:** Home, `am kill`, reopen. The clock is still counting. It has to be `am kill`, because a force stop drops
   saved state. Then a force stop, which shows what the known limitations say.
4. **The target:** let it pass the target on a fixed target and on a range, then `Stop` and `Log set`. The database holds the
   seconds logged, the clock is back at `0:00`, and `Start` is offered.
5. **The rest:** with a rest running, `Start`: the strip goes, and no `Rest done` or notification arrives after the rest's old
   end. `Stop` starts no rest. `Log set` does.
6. **The look:** every state in light and dark at font scales 1.0, 1.3 and 2.0, compared with `20o` and `20p`, including how the
   left-aligned card sits above the centred Time label. If TalkBack can be started, read the speech for the readout, `Start`,
   `Stop`, `Stopped at` and the past-target line.
<!-- /CP3 -->

## Known limitations

- **What loses a running clock.** The start moment is in the activity's saved state, not in Room. It survives a rotation, locking
  the phone, switching apps, the system ending the process, and `Leave it running` followed by `Resume`. It does not survive
  whatever discards the saved state: swiping the app away from the recents list, a force stop or a device restart, and it may not
  survive a crash or an app update. The workout itself is restored from Room and `Start` is offered again, with no message. The
  rest timer survives all of these because its end is in Room; see "Decisions to confirm".
- **`Finish` with a clock running drops the hold without a word.** So does `Next ›` on the last exercise, once its finish sheet
  is confirmed. `WorkoutFinishSheet` takes only `content`. A typed but unlogged entry goes the same way today, but a running
  clock is easier to forget.
- **A mis-tapped `Start` can't be cancelled.** `Log set` is disabled until `Stop`, and a `Stop` after a second replaces the
  entry's time with those few seconds, so a time typed or stopped earlier has to be entered again. It also ends a running rest.
  A cancel is a design question, not this item's.
- A clock that is left running for days reads in minutes beyond 59 (`120:00`), and `Stop` caps the value at the largest the
  entry can hold.
- One clock per exercise, held with that exercise's entry. Two timed exercises can each have a clock running, and neither shows
  on the Board.
- Logging a set on another exercise while a clock runs starts a rest as usual. Only `Start` ends one.
- The buzz, the sound and the notification at the target are `timed-set-alert`'s. Until then the card's change is the only sign,
  and only with the screen in view.
- The wall clock is the source: a time zone or daylight saving change does not move it (epoch milliseconds), but the user setting
  the device clock back shortens the readout, down to `0:00`.
- Stepping or typing while a clock runs changes the entry's value, which `Stop` then replaces.

## Risks and notes

- **A hoisted holder.** `FocusEntries` now outlives the workout destination. `retainOnly` bounds it, and per-session exercise ids
  keep an old entry from reaching a new workout. CP3's test 4 and the wrap-up walk the path it exists for.
- **`internal` on two screens' entry points.** Nothing outside the single `:app` module uses them, and the tests sit in the same
  module.
- **Process death.** `SetEntryState.Saver` and `FocusEntries.Saver` already carry the entry across it, so the clock rides on tested
  ground. `recreate()` goes through the Bundle but kills nothing, so CP1's test 9 and CP2's test 8 hold the saved form and the
  wrap-up's `am kill` holds the real thing.
- **Connected test flakes.** Tests 1, 2 and 4 of CP3 wait on real time, and only with `waitUntil`, so a slow AVD makes them slower
  and doesn't fail them.
- **Tick cost.** `rememberElapsedSeconds` ticks once a second while the card shows a running clock, and composes nothing when none
  runs. The screen already stays on during a workout (`ActiveWorkoutScreen.kt:212`).

## Decisions to confirm

1. **The clock is not stored in the database.** This is the owner's call, and the default is no stored column. What it costs is in
   the first known limitation: a clock running when the app is swiped away, force-stopped or the phone restarts comes back with
   `Start` offered, the time gone and no message, where the rest timer would have survived. What a column would cost: a nullable
   column, a migration and a migration test, a decision on the backup format, and a write on each `Start` and `Stop`. That is a second
   mechanism, and the item page's rule is to split it, so it would be a follow-up item and this one would still ship as planned.

## Decisions from plan review, round 1

All findings were checked against the repository and the design page, and all are accepted. I1 and part of B1 are accepted with the
adjustments their rows state.

| Finding | Resolution |
|---|---|
| B1, the boards | The statement that no board draws the clock was false, and it is gone from the plan, its risks and the review request. "What the boards draw" quotes `20g`, `20o`, `20p`, `20q` and `20r`, the differences have their reasons, and R1-R3, the mapping and CP2's tests and mutations describe the drawn states |
| B1, the stopped state | New state in `SetEntryState`, cleared through `clearAfterSet` by the existing rule. It is the one clock, not a second mechanism |
| B1, past the target | Done here. CP2 records it on `timed-set-alert`'s page |
| B1, `· alerts at 0:30` | Left out and recorded. The finding names `20o` and `20p`, but only `20p` draws it: the board's script takes that text only for a range, and `20o` reads `Target 1:00` and `Target 1:00 · counting up` |
| B1, undrawn cases | "Cases the boards do not draw" covers these two and adds a changed Time box and the `Rest done` strip |
| B1, 52 dp | `Modifier.heightIn`, as `Add exercise` does. `RepFlowAccentOutlineButton` needs no new parameter, because an outer minimum wins over its own 48 |
| I1 | Accepted, with one adjustment: it is not put to the owner again. The owner decided on 2026-11-06 what `20r` draws, and the board's heading is out of date. CP3 wires the skip path, with its tests and files. Evidence: the lane's record `owner_features_2026_11_05`, and `repflow-lane/design-passes/pass7.md` for the question as asked |
| I2 | The reviewer's cheaper option is taken: the holder moves above the `NavHost`, so the sheet's promise holds and it needs no new line. The first known limitation and the remaining decision state the true cost, and the wrap-up walks the path. Decision 1 in revision 1 is removed, per the usability finding |
| I3 | Clamped at the call site, with CP2's test 4 |
| I4 | `nowMillis` holds the tap, so what `Stop` writes is exact, and readout assertions are bounds. Test 5's "agree" is exact against the stepper, `Stopped at` and `onRecordSet` |
| I5 | The clock is `20o`'s own card in `WorkoutFocus`, so `EntrySteppers` gets no clock parameter, and CP2's test 11 holds it. `EntrySteppers` still changes in two small ways, stated above |
| O1 | `@Suppress("TooManyFunctions")` |
| O2, O3 | In the known limitations. `docs/UX_FLOWS.md` carries what loses a clock |
| Missing tests | R4's cases, each held by a named test or step: rotation, CP2 test 8; the Board, CP3 test 2; switching exercise, CP3 test 1; process death, CP1 test 9, CP2 test 8 and the wrap-up's `am kill`; leaving, CP3 test 4 and the wrap-up. CP2 test 2 fills the seconds first, and the 52 dp mutation is now real |

Reading the boards added R5 and the `Start again` and after-`Log set` states.
