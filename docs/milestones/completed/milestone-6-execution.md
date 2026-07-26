# Milestone 6 execution — Progression recommendations

Concise, operational checklist. Full rationale, domain invariants, and the
Policy v1 threshold decision are in `milestone-6-reference.md` — read that
before implementing CP1.

## Checkpoints

### CP1 — Domain
- `domain/progression/ProgressionRecommendationId.kt`
- `domain/progression/ProgressionResult.kt` (sealed: IncreaseLoad,
  MaintainLoad, ReduceLoad, RecoveryAdjustment, WaitForMoreData)
- `domain/progression/ManualOverride.kt`
- `domain/progression/ProgressionRecommendation.kt` (factory + `withOverride()`)
- `domain/progression/ProgressionPolicyV1.kt` (pure function: inputs ->
  `ProgressionResult` + reasons)
- `domain/progression/ProgressionValidationError.kt`
- Tests: `ProgressionPolicyV1Test.kt`, `ProgressionRecommendationTest.kt`
- Verify: `./gradlew testDebugUnitTest --tests "*progression*" spotlessApply detekt`

### CP2 — Application
- `application/progression/ProgressionRecommendationRepository.kt` (port)
- `application/progression/ComputeProgressionRecommendation.kt`
- `application/progression/RecordManualOverride.kt`
- `application/progression/ProgressionOperationError.kt` /
  `ProgressionPersistenceError.kt`
- Test fakes: `InMemoryProgressionRecommendationRepository.kt`
- Tests: `ComputeProgressionRecommendationTest.kt`,
  `RecordManualOverrideTest.kt`
- Verify: `./gradlew testDebugUnitTest --tests "*progression*" spotlessApply detekt`

### CP3 — Persistence
- `infrastructure/database/progression/ProgressionRecommendationEntity.kt`
  + `ProgressionRecommendationDao.kt`
- `data/progression/ProgressionRecommendationMapper.kt` +
  `LocalProgressionRecommendationRepository.kt`
- `RepFlowMigrations.kt`: `MIGRATION_5_6` (additive table)
- `RepFlowDatabase.kt`: bump to version 6, register entity/DAO
- `DatabaseModule.kt` / `RepositoryModule.kt`: wire new bindings
- Verify: `./gradlew testDebugUnitTest spotlessApply detekt lintDebug`;
  confirm `app/schemas/.../6.json` generated

### CP4 — Instrumented tests
- `RepFlowDatabaseMigrationTest.kt`: `migrate5To6_addsProgressionRecommendationsTable`
- `ProgressionRecommendationDaoTest.kt`
- Verify: `./gradlew connectedDebugAndroidTest` filtered by package/class
  on a running emulator

### CP5 — Presentation
- Surface recommendation + override control in the exercise-picker /
  active-workout flow (extend `ExercisePickerItem`/`ActiveExerciseUi` or
  add a small adjacent composable — decide based on what's least
  invasive to existing screens)
- ViewModel wiring + test
- Verify: `./gradlew testDebugUnitTest spotlessApply detekt lintDebug compileDebugAndroidTestKotlin`

### CP6 — Milestone completion
- Full verification suite (unit, static, instrumented, manual smoke test)
- Update `docs/ACTIVE_MILESTONE.md`, `docs/ROADMAP.md`
- Archive this milestone's docs to `docs/milestones/completed/`
- Final completion commit

## Completion criteria

All CP1-CP6 done, verified, committed; Room schema 6 tracked; no
destructive migrations; policy-v1 thresholds documented as an explicit,
flagged assumption (not silently finalized) per the reference doc.
