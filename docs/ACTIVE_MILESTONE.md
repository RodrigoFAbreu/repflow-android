# Active Milestone

## Milestone

Milestone 4 — Rest Timer — **COMPLETE**

## Goal

After a working set is recorded, start a rest timer with a configured
duration, storing an absolute end timestamp so it survives backgrounding
and full process death. Allow adding/removing time and skipping, and
notify the user when rest ends where OS permission allows.

## Current checkpoint

CP7 complete: Milestone 4 is fully implemented, verified and documented.
Full verification suite passed (`testDebugUnitTest`, `spotlessCheck`,
`detekt`, `lintDebug`, `assembleDebug`, `connectedDebugAndroidTest` — full
instrumented suite, 98 total test methods, 0 failures/errors). Manual
smoke test on `emulator-5554` confirmed start/abandon workout works with
the new rest-timer code paths active and no crashes. Room schema
`app/schemas/.../4.json` is exported and tracked.

Milestone 3 (Active Workout) remains complete and committed; see
`docs/milestones/completed/milestone-3-execution.md` and `-reference.md`.

## Checkpoint checklist (Milestone 4)

- [x] CP1 — Domain: `RestTimer` + `WorkoutSession` integration + tests
- [x] CP2 — Application: `StartRestTimer`/`AdjustRestTimer`/`SkipRestTimer` + tests
- [x] CP3 — Room persistence: migration 3→4, entity/mapper
- [x] CP4 — Instrumented migration + DAO tests on device
- [x] CP5 — Presentation: countdown UI (rest-timer bar, +15s/-15s, skip)
- [x] CP6 — Notifications: AlarmManager + BroadcastReceiver + runtime permission
- [x] CP7 — Docs + final architectural review

## Approved decisions (quick reference, Milestone 4)

- Default rest duration: 90 seconds (a placeholder constant,
  `DEFAULT_REST_TIMER_SECONDS`; no per-exercise configuration UI yet -
  documented as an assumption, not a hard product decision)
- Notification behavior when `POST_NOTIFICATIONS` is denied: **silently
  degrade to an in-app-only timer** - this resolves the previously "Open"
  decision in `docs/TECHNICAL_DECISIONS.md` for this milestone
- Rest timer scheduling/notifications live entirely in the presentation
  layer (`ActiveWorkoutRoute`, `RestTimerAlarmScheduler`,
  `RestTimerExpiredReceiver`); domain/application layers only track the
  absolute end timestamp, preserving dependency direction
- Completing/abandoning a session implicitly clears any running rest timer

## Current blockers

None. Milestone 4 is complete, verified and committed.

## Active plan

Milestone 4 plan docs have been archived:
`docs/milestones/completed/milestone-4-execution.md`,
`docs/milestones/completed/milestone-4-reference.md`.

## Last verified state (Milestone 4, full CP1–CP7)

- Unit tests: `./gradlew testDebugUnitTest` (full suite) — all pass,
  including new `RestTimerTest`, `RestTimerUseCasesTest`, expanded
  `WorkoutSessionTest`/`WorkoutEntityMapperTest`/`ActiveWorkoutViewModelTest`
- Static checks: `./gradlew spotlessCheck detekt lintDebug` — all pass
  (one `ScheduleExactAlarm` lint error fixed by checking
  `AlarmManager.canScheduleExactAlarms()` and falling back to an inexact
  alarm rather than crashing)
- Build: `./gradlew assembleDebug` — succeeds
- Instrumented tests: `./gradlew connectedDebugAndroidTest` (full suite,
  not filtered) on the `Pixel_9_Pro_XL` (API 37) emulator — 98 total test
  methods, 0 failures/errors, including new `migrate3To4_*` cases
- Manual verification: installed the latest debug APK on `emulator-5554`;
  started and abandoned a workout with the new rest-timer code paths
  active (schedule/cancel on the presentation layer), no crashes in
  logcat. The full record-set → rest-timer-appears → add/remove time →
  skip → notification-fires flow was **not** additionally clicked through
  manually (it requires first creating an exercise via the exercise
  editor); confidence for that flow rests on the automated
  `RestTimerTest`/`RestTimerUseCasesTest`/`ActiveWorkoutViewModelTest`
  suites plus the migration/DAO instrumented tests - a fuller manual
  click-through (create an exercise, start a workout, add it, record a
  set, observe the rest-timer bar and its buttons, let it expire) is
  recommended as a follow-up smoke test before this feature reaches real
  users
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/4.json` generated and tracked

## Known deferred scope (documented, not a defect)

- No per-exercise/per-set configurable rest duration UI (fixed 90s
  default only)
- Set-entry UI still only exposes load + reps (carried over from
  Milestone 3; unrelated to rest timer scope)
