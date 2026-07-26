# Milestone 1 — Exercise Library: Execution Guide

**Full reference (decisions, invariants, tests, DoD):**
[milestone-1-reference.md](milestone-1-reference.md)

## Goal and scope

Deliver the smallest Exercise Library crossing every layer: list, search,
create, edit, archive, restore. Room persistence, navigation, five layer
boundaries, and domain / use-case / DAO / repository / ViewModel / Compose
tests.

**Deferred:** permanent deletion, built-in catalog (M1.1), emulator CI gate,
`DISTANCE_AND_DURATION`, `BODYWEIGHT_WITH_OPTIONAL_LOAD` tracking types.

---

## Checkpoint status

| # | Checkpoint | Status |
|---|---|---|
| 1 | M0 cleanup | ✓ verified |
| 2 | Dependencies + schema export config | ✓ verified |
| 3 | Domain model + pure JVM tests | code present, tests not yet run |
| 4 | Application contracts + use cases | code present, tests not yet run |
| 5 | Room entity, DAO, mapper, repository | code present, tests not yet run |
| 6 | DAO + repository instrumented tests on device | **next to run** |
| 7 | Hilt bindings | code present |
| 8 | Exercise list end to end | code present |
| 9 | Exercise editor end to end | code present |
| 10 | Archive / restore + snackbar undo | code present |
| 11 | Optional built-in catalog (if D-10 approved) | deferred |
| 12 | Docs + final architectural review | pending |

> **Factual note:** Implementation files for CPs 3–10 exist as untracked.
> Last confirmed build: `./gradlew assembleDebug`. No unit or instrumented tests
> have been verified yet.

---

## Current action: verify CPs 3–5 unit tests, then CP6 on device

### Files involved (CPs 3–5 already written)

- `domain/common/DomainResult.kt`
- `domain/exercise/{Exercise, ExerciseId, ExerciseName, ExerciseInstructions,`
  `ExerciseTrackingType, ExerciseOrigin, LoadIncrement, RestDuration, ExerciseValidationError}.kt`
- `application/common/{Clock, IdentifierGenerator}.kt`
- `application/exercise/{ExerciseRepository, ExerciseQueryCriteria,`
  `ExerciseOperationError, ExercisePersistenceError, ObserveExercises, GetExercise,`
  `CreateExercise, UpdateExercise, ArchiveExercise, RestoreExercise}.kt`
- `infrastructure/database/{RepFlowDatabase, exercise/ExerciseEntity, exercise/ExerciseDao}.kt`
- `data/exercise/{ExerciseEntityMapper, ExerciseSearchPattern, LocalExerciseRepository}.kt`

### Verification commands

```bash
# Unit tests (CPs 3–5)
./gradlew testDebugUnitTest
# Schema verification (CP5) — schema is currently untracked
# Run kspDebugKotlin, then inspect status; git diff --exit-code alone is
# insufficient while app/schemas is untracked (exits 0, misses new files).
./gradlew kspDebugKotlin
git status --short -- app/schemas
find app/schemas -type f -name '*.json' -print
# Confirm JSON exists, is not git-ignored, and report tracked/untracked/clean.
# After the schema is tracked, also run:
#   git diff --exit-code -- app/schemas && git status --short -- app/schemas
# Instrumented tests (CP6) — device or emulator required
# --tests is not supported by connectedDebugAndroidTest (DeviceProviderInstrumentTestTask).
./gradlew connectedDebugAndroidTest
```

---

## Key approved decisions (summary)

| Decision | Outcome |
|---|---|
| Tracking types (D-21) | `WEIGHT_AND_REPS`, `REPS_ONLY`, `DURATION` only |
| Name normalization (D-25/26) | NFKC → trim → collapse whitespace → `lowercase(Locale.ROOT)` for key; original caps for display |
| Search (D-20) | Normalized substring `LIKE`, escaped, bound param, accent-sensitive |
| Ordering (D-27) | `name_key ASC`, `id ASC` tiebreaker, never `COLLATE NOCASE` |
| Uniqueness (D-9) | Spans active **and** archived rows |
| Unknown enum (D-28) | Mapping failure → retryable observation error, never silent default |
| Archive undo (§P-8) | Idempotent — already-active exercise treated as neutral no-op |
| Duplicate check (§P-1) | Exclude the exercise being edited by id |
| Save unchanged draft (§P-9) | No DB write, no `updatedAt` bump |
| JVM (D-19) | Source/target 17, Kotlin JVM target 17 explicit, daemon JBR 21 |
| Deletion | None in M1; archive/restore only |
| Instrumented tests | Must actually run on device before M1 is complete |

For full decision text see §N and §P in [milestone-1-reference.md](milestone-1-reference.md).

---

## Stopping conditions

Stop and report when:

- a product or design decision is needed that is not covered in §N/§P;
- an architecture boundary change is required;
- a schema migration has not been approved;
- a destructive operation would be required;
- unrelated working-tree changes (pre-existing before this session) are present;
- CP6 instrumented tests cannot run because no device or emulator is available;
- the same failure recurs three times.

---

## Concise Definition of Done

- Custom exercises created, listed, searched, edited, archived and restored.
- Data survives app restart.
- Duplicate normalized names rejected (precheck + UNIQUE constraint).
- No Room types cross application or presentation boundaries.
- App starts on exercise list.
- Unit tests pass.
- DAO and repository instrumented tests **actually ran** on device — result recorded.
- Compose UI tests pass.
- Room v1 schema committed; schema drift verified (see CP5 schema verification procedure).
- No destructive migration fallback.
- `spotlessCheck`, `detekt`, `lintDebug` pass.
- Feature manually smoke-tested on S24 Ultra or documented emulator.
