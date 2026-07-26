# Milestone 4 Execution — Rest Timer

Reference: `docs/milestones/active/milestone-4-reference.md`

## Checkpoints

1. **CP1 — Domain model.** `domain/workout/RestTimer.kt` (new) +
   `WorkoutSession` gains `restTimer` field and
   `withStartedRestTimer`/`withAdjustedRestTimer`/`withClearedRestTimer`.
   Verify: `./gradlew testDebugUnitTest --tests "com.repflow.app.domain.workout.*"`.
2. **CP2 — Application layer.** `StartRestTimer`, `AdjustRestTimer`,
   `SkipRestTimer` use cases (reuse `WorkoutRepository.update`).
   Verify: `./gradlew testDebugUnitTest --tests "com.repflow.app.application.workout.*"`.
3. **CP3 — Room persistence.** Migration 3→4 (additive: two nullable
   columns on `workout_sessions`), entity + mapper updates. Verify:
   `./gradlew testDebugUnitTest`, inspect exported schema JSON.
4. **CP4 — Instrumented tests.** Migration + DAO tests on a real
   device/emulator. Verify: `./gradlew connectedDebugAndroidTest`.
5. **CP5 — Presentation: countdown UI.** `ActiveWorkoutScreen`/
   `ActiveWorkoutViewModel` gain a rest-timer section (remaining time,
   +15s/-15s, skip) driven by a 1s-tick `Flow` derived from the persisted
   absolute end timestamp. Verify: unit/UI tests + manual smoke test,
   including a full app-process restart mid-countdown.
6. **CP6 — Notifications.** `AlarmManager` scheduling + `BroadcastReceiver`
   + notification channel + `POST_NOTIFICATIONS` runtime permission
   request (Android 13+), degrading gracefully when denied. Verify:
   manual smoke test on emulator (schedule, expire, check notification
   shade or graceful skip if permission denied).
7. **CP7 — Docs + review.** Update `docs/ACTIVE_MILESTONE.md`,
   `docs/ROADMAP.md`; archive plan docs under
   `docs/milestones/completed/`; run full verification suite; final diff
   review; completion commit.

## Files/layers expected to change

- `app/src/main/kotlin/com/repflow/app/domain/workout/RestTimer.kt` (new)
- `app/src/main/kotlin/com/repflow/app/domain/workout/WorkoutSession.kt`
  (modified)
- `app/src/test/kotlin/com/repflow/app/domain/workout/**` (new tests)
- `app/src/main/kotlin/com/repflow/app/application/workout/**` (3 new
  use cases)
- `app/src/test/kotlin/com/repflow/app/application/workout/**` (new tests)
- `app/src/main/kotlin/com/repflow/app/infrastructure/database/**`
  (migration 3→4, entity/mapper changes)
- `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/4.json`
  (new, generated)
- `app/src/androidTest/**/workout/**` (migration/DAO test additions)
- `app/src/main/kotlin/com/repflow/app/presentation/workout/**`
  (countdown UI, alarm scheduling, notification receiver)
- `app/src/main/AndroidManifest.xml` (receiver + permission declarations)
- `docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md` (status updates)

## Completion criteria

See "Definition of Done" in the reference guide. Do not mark Milestone 4
complete until all CP1–CP7 items and their verification commands have
actually been run and passed.
