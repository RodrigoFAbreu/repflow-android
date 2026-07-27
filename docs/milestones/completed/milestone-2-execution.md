# Milestone 2 — Training Plans: Execution Guide

**Full reference (decisions, invariants, schema, tests, DoD):**
[milestone-2-reference.md](milestone-2-reference.md)

## Goal and scope

Deliver the smallest Training Plans vertical slice crossing every layer:
versioned plan domain, create/edit (edit = new version, history preserved),
ordered planned exercises with targets, Room persistence via a real additive
migration (schema v1 → v2), list + editor UI.

**Deferred:** alternatives/substitutions, progression-policy configuration,
plan archive/delete, drag-and-drop reordering, a version-history viewer.

## Checkpoint status

| # | Checkpoint | Status |
|---|---|---|
| 1 | Domain model + pure JVM tests | done |
| 2 | Application contracts + use cases + tests | done |
| 3 | Room entities, DAOs, mapper, migration 1→2, repository | done |
| 4 | DAO + repository + migration instrumented tests on device | done |
| 5 | Hilt bindings | done |
| 6 | Training plan list end to end | done |
| 7 | Training plan editor end to end (create + revise) | done |
| 8 | Docs + final architectural review | done |

## Verification commands

```bash
# Unit tests (CP1-3)
./gradlew testDebugUnitTest
# Schema verification (CP3) - schema currently at v1, expect a new v2 file
./gradlew kspDebugKotlin
git status --short -- app/schemas
find app/schemas -type f -name '*.json' -print
# Instrumented tests (CP4, CP6-7) - device or emulator required
./gradlew connectedDebugAndroidTest
# Static checks (every checkpoint)
./gradlew spotlessCheck detekt lintDebug
```

## Key carried-over decisions (from Milestone 1, still binding)

- `DomainResult<T, E>` for expected outcomes; no Kotlin `Result`, no Arrow.
- Name normalization: NFKC → trim → collapse whitespace → lower-case key.
- Ordering/search always on the `_key` column, never `COLLATE NOCASE`.
- No destructive Room migration fallback, ever.
- Enum-shaped columns persisted by stable string, never ordinal.
- Domain stays pure Kotlin; no Room/Hilt/Android types cross into
  application, domain or presentation.
- Single `NavHost`, plain string routes, no type-safe routes.
- Units: kilograms only. Load increments: integer grams. Timestamps:
  `Instant` in domain, epoch millis in Room.

## New decisions for Milestone 2 (see reference for full text)

| Decision | Outcome |
|---|---|
| Plan "current version" | A query concept (`max(version_number)`), not domain state on `TrainingPlan` |
| Editing a plan | Always creates a new immutable `TrainingPlanVersion`; never mutates an existing version |
| Plan archive/delete | Out of scope for M2 |
| Reordering UI | Move-up/move-down buttons; no drag-and-drop dependency |
| `target_kind` vocabulary | `"REPS"` / `"DURATION"` only - distinct from `ExerciseTrackingType`'s three values |
| Migration 1→2 | Real, additive `Migration` object + `MigrationTestHelper` test (first real migration in this project) |
| Referenced exercises | Must exist (`ExerciseRepository.findById`); target kind must match `ExerciseTrackingType` |

## Stopping conditions

Stop and report when:

- a product or design decision is needed that is not covered in the reference doc;
- an architecture boundary change is required beyond what is described here;
- the migration's data-preservation behavior becomes unclear at implementation time;
- a destructive operation would be required;
- unrelated working-tree changes are present;
- instrumented tests cannot run because no device/emulator is available;
- the same failure recurs three times.

## Concise Definition of Done

See the reference doc's "Definition of Done" section for the full checklist.
