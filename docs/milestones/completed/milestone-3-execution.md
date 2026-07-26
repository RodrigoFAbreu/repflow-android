# Milestone 3 Execution — Active Workout

Reference: `docs/milestones/completed/milestone-3-reference.md`

## Checkpoints

1. **CP1 — Domain model.** `domain/workout/` value objects and entities
   (`WorkoutSession`, `WorkoutExercise`, `WorkoutSet`, ids, validation
   errors). Pure Kotlin. Verify: `./gradlew testDebugUnitTest --tests
   "com.repflow.app.domain.workout.*"`.
2. **CP2 — Application layer.** `WorkoutRepository` port + use cases
   (start, resume, record/edit/remove set, add ad hoc exercise, complete,
   abandon). Verify: `./gradlew testDebugUnitTest --tests
   "com.repflow.app.application.workout.*"`.
3. **CP3 — Room persistence.** Entities/DAOs/mapper for sessions, workout
   exercises, sets; migration 2→3 (additive only); repository impl. Verify:
   `./gradlew testDebugUnitTest`, inspect exported schema JSON.
4. **CP4 — Instrumented tests.** DAO + repository + migration tests on a
   real device/emulator. Verify: `./gradlew connectedDebugAndroidTest`.
5. **CP5 — Hilt bindings.** Wire repository + use cases into DI graph.
   Verify: `./gradlew assembleDebug`.
6. **CP6 — Current-workout screen.** Start-or-resume UI end to end.
   Verify: unit/UI tests + manual smoke test on emulator.
7. **CP7 — Fast set entry + edit/undo.** End-to-end set recording, edit,
   and undo of the most recent set. Verify: unit/UI tests + manual smoke
   test, including full app-process restart to confirm resume works.
8. **CP8 — Docs + review.** Update `docs/ACTIVE_MILESTONE.md`,
   `docs/ROADMAP.md`; archive plan docs under
   `docs/milestones/completed/`; run full verification suite; final diff
   review; completion commit.

## Files/layers expected to change

- `app/src/main/kotlin/com/repflow/app/domain/workout/**` (new)
- `app/src/test/kotlin/com/repflow/app/domain/workout/**` (new)
- `app/src/main/kotlin/com/repflow/app/application/workout/**` (new)
- `app/src/test/kotlin/com/repflow/app/application/workout/**` (new)
- `app/src/main/kotlin/com/repflow/app/infrastructure/database/**`
  (new workout entities/DAOs, migration 2→3, repository impl)
- `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/3.json`
  (new, generated)
- `app/src/androidTest/**/workout/**` (new DAO/migration/UI tests)
- `app/src/main/kotlin/com/repflow/app/presentation/**` (new
  current-workout screen + set-entry components)
- `docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md` (status updates)

## Completion criteria

See "Definition of Done" in the reference guide. Do not mark Milestone 3
complete until all CP1–CP8 items and their verification commands have
actually been run and passed.
