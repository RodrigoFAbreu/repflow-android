# Milestone 5 Execution — Recovery and Futsal

Reference: [milestone-5-reference.md](milestone-5-reference.md)

## Checkpoints

1. **Domain** — `domain/recovery/RecoveryEntry.kt`,
   `domain/recovery/FutsalSession.kt` + unit tests.
   - Verify: `./gradlew testDebugUnitTest --tests "*recovery*"`
2. **Application** — `application/recovery/RecoveryRepository.kt`,
   `FutsalRepository.kt` (ports), `RecordRecoveryEntry`,
   `RecordFutsalSession`, `ObserveWorkoutDayContext` use cases + tests.
   - Verify: `./gradlew testDebugUnitTest --tests "*recovery*"`
3. **Persistence** — `RecoveryEntryEntity`, `FutsalSessionEntity`,
   `RecoveryDao`, `FutsalDao`, `RecoveryEntityMapper`,
   `FutsalEntityMapper`, `MIGRATION_4_5`, DB version 5, Hilt bindings.
   - Verify: `./gradlew testDebugUnitTest spotlessCheck detekt`
4. **Instrumented tests** — migration 4→5 test, DAO tests.
   - Verify: `./gradlew connectedDebugAndroidTest --tests "*Recovery*"
     --tests "*Futsal*" --tests "*Migration*"`
5. **Presentation entry screens** — Recovery entry screen, Futsal entry
   screen, ViewModels, navigation destinations + tests.
   - Verify: `./gradlew testDebugUnitTest spotlessCheck detekt lintDebug`
6. **Workout-day context banner** — `ActiveWorkoutViewModel`/`Screen`
   surface `WorkoutDayContext`.
   - Verify: `./gradlew testDebugUnitTest spotlessCheck detekt lintDebug`
7. **Docs + final review** — full verification suite, manual smoke test,
   update `docs/ACTIVE_MILESTONE.md` / `docs/ROADMAP.md`, archive plan
   docs, completion commit.
   - Verify: full suite per Definition of Done in the reference doc.

## Completion criteria

See "Definition of Done" in
[milestone-5-reference.md](milestone-5-reference.md).
