# Milestone 1 — Exercise Library (first complete vertical slice) — revision 2

**Status: approved. All decisions D-1 through D-28 are resolved (see §N) and
15 additional implementation corrections are locked in (see §P). Execution is
in progress, checkpoint by checkpoint (see §L and the `todos` table).**

## Purpose

Deliver the **smallest useful Exercise Library** that still crosses every
documented layer, so the architecture is validated by a real feature rather
than by scaffolding.

**In scope:** list active exercises, search by normalized name, create, edit,
archive, view archived, restore, Room persistence, navigation, the five layer
boundaries, and domain / use-case / DAO / repository / ViewModel / stateless
Compose tests.

**Removed or deferred (see §K for the rationale):** permanent deletion,
reference-aware deletion extension points, a large built-in catalog,
application-start seeding, a version-1 migration test, and emulator-backed CI
as a blocking requirement.

Archive and restore are the **only** removal behavior in Milestone 1.

---

## Baseline (already verified, read-only)

`JAVA_HOME=/opt/android-studio/jbr ./gradlew --offline spotlessCheck detekt testDebugUnitTest`
→ **BUILD SUCCESSFUL**.

Room, Navigation Compose, Turbine and coroutines-test are **not** in the local
Gradle cache, so step 2 requires network access.

---

## A. Milestone 0 issues to correct first (separate reviewed commit)

| #     | Issue                                                                                                                                                   | Correction                                                                                                         |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| M0-1  | `README.md` ends with leftover Portuguese draft notes and an **unterminated code fence**                                                                | Remove the draft block; link the ADRs                                                                              |
| M0-2  | Uncommitted `gradle.properties` change (`org.gradle.tooling.parallel=true`) and untracked `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`) | Decide explicitly (D-19): commit both or revert both. Do not leave them dangling                                   |
| M0-3  | **JVM inconsistency**: daemon toolchain 21, CI `setup-java` 17, `compileOptions` 17, Kotlin `jvmTarget` unset                                           | Pin one story and set an explicit Kotlin `jvmTarget`. Becomes load-bearing the moment KSP generates code           |
| M0-4  | `android:allowBackup="true"` with no `dataExtractionRules` / `fullBackupContent`                                                                        | Set `allowBackup="false"` **before** Room lands (D-18). Auto Backup restoring a stale Room DB contradicts ADR 0002 |
| M0-5  | `themes.xml` parents `android:Theme.Material.Light.NoActionBar` — forced-light window under an edge-to-edge, dark-aware Compose theme                   | Switch to `android:Theme.Material.DayNight.NoActionBar` (no new dependency)                                        |
| M0-6  | Manifest missing `android:supportsRtl="true"`                                                                                                           | Add                                                                                                                |
| M0-7  | `ProjectSetupSmokeTest` self-documented as temporary                                                                                                    | Delete when real unit tests land (step 3)                                                                          |
| M0-8  | `MainActivitySmokeTest` asserts the literal placeholder text `"RepFlow"`                                                                                | Replace when the NavHost lands (step 8)                                                                            |
| M0-9  | CI compiles but never executes instrumented tests                                                                                                       | §I — compile in CI, execute locally at least once, emulator CI optional                                            |
| M0-10 | ADRs not linked from `README.md`                                                                                                                        | Add links                                                                                                          |

---

## B. User-visible behavior

**Entry point.** No Home screen in Milestone 1 (Home depends on plans, workouts
and recovery). The app starts on the exercise list; Home replaces it later.

**List**

- Status filter: **Active** (default) / **Archived**.
- Search: normalized substring match (§D), empty query = no filter.
- Rows sorted by `name_key` ascending (D-27), showing display name,
  tracking-type label, and a compact default rest / load increment summary.
- Row tap → editor. Row overflow → Edit, Archive (or Restore).
- FAB → new exercise.
- Distinct empty states: no exercises at all / no search results / no archived
  exercises.
- **No delete anywhere in Milestone 1.**

**Create & edit**

- Fields: name (required), tracking type (required, defaults to
  weight-and-reps), instructions (optional multi-line), default rest duration
  (optional; presets 60/90/120/180 s + custom), default load increment
  (optional; presets 1.25/2.5/5 kg + custom, shown only for load-capable
  tracking types).
- Inline per-field errors; Save disabled while invalid.
- Switching to a tracking type that cannot carry load clears the load
  increment with a visible hint, never silently.
- Back with unsaved changes → discard confirmation.

**Archive / restore**

- Archive: one tap, no confirmation, snackbar with **Undo**. The archive is
  persisted immediately; Undo issues a real `RestoreExercise` (offline-first —
  nothing is held in memory pending a snackbar timeout).
- Restore is also available from the Archived filter.
- Archiving never touches historical data and never removes a row.

---

## C. Domain model and invariants

```kotlin
Exercise(
    id: ExerciseId,
    name: ExerciseName,                    // holds display + normalized key
    trackingType: ExerciseTrackingType,
    instructions: ExerciseInstructions?,   // blank -> null
defaultLoadIncrement: LoadIncrement?,
defaultRestDuration: RestDuration?,
origin: ExerciseOrigin,                // BUILT_IN | CUSTOM
archivedAt: Instant?,                  // null == active
createdAt: Instant,
updatedAt: Instant,
)
```

Invariants enforced in the domain:

1. Display name non-blank after trimming, at most 80 characters.
2. The normalized key (§D) is non-empty — a name consisting only of characters
   that normalize away is rejected.
3. Instructions at most 2000 characters; blank becomes `null`.
4. `defaultLoadIncrement` must be `null` when
   `trackingType.supportsLoad == false`; when present, `> 0` and `<= 100 kg`.
5. `defaultRestDuration`, when present, `> 0 s` and `<= 30 min`.
6. `updatedAt >= createdAt`; `archivedAt >= createdAt` when present.
7. `archive(at)` / `restore(at)` return new instances, bump `updatedAt`, and
   never change `createdAt` or `origin`.
8. Construction goes through a validating factory returning
   `DomainResult<Exercise, List<ExerciseValidationError>>`; the raw constructor
   is not the public creation path.

`ExerciseId` is a value class over a UUID string, produced by an
application-owned `IdentifierGenerator` so tests are deterministic. `Instant`
and `Duration` come from `java.time` / `kotlin.time` — pure JVM, available at
minSdk 28, so domain purity holds.

---

## D. Name normalization, uniqueness and search (Unicode-safe)

**One normalization policy, implemented once in `ExerciseName`, in Kotlin.
SQLite `COLLATE NOCASE` is never used for identity, uniqueness or search** — it
only case-folds ASCII and would silently disagree with the Kotlin rule.

Normalization pipeline producing `nameKey`:

1. `Normalizer.normalize(input, Form.NFKC)` — `java.text`, pure JVM.
2. `trim()`
3. collapse internal whitespace with `Regex("[\\s\\p{Z}]+")` → single `U+0020`
   (plain `\s` misses Unicode separators such as `U+00A0`).
4. `lowercase(Locale.ROOT)` — `Locale.ROOT` avoids the Turkish dotless-i trap.

> **NFKC is applied first**, before trim/collapse. Your specified order was
> trim → collapse → NFKC → lowercase; NFKC-first is strictly stronger because
> NFKC maps `U+00A0` and other Unicode spaces to `U+0020`, which the collapse
> step would otherwise miss entirely. Flagged as **D-25**.

Persisted columns:

- `name` — user-facing display value (trimmed and whitespace-collapsed, but
  **not** case-folded and **not** NFKC-transformed, so the user's characters are
  preserved as typed — **D-26**).
- `name_key` — normalized identity and search value.

**Uniqueness:** `UNIQUE INDEX index_exercises_name_key ON exercises(name_key)`,
looked up with exact equality (`WHERE name_key = :key`, SQLite BINARY
collation). Uniqueness spans active **and** archived rows (D-9).

**Search:** normalize the query with the same pipeline, then escape SQLite
`LIKE` metacharacters in this order — `\` first, then `%`, then `_` — and query:

```sql
WHERE (:query = '' OR name_key LIKE '%' || :query || '%' ESCAPE '\')
```

Accent handling: search is **accent-sensitive** in Milestone 1; `elevacao` will
not match `elevação`. Accent-insensitive search would require a separate
diacritic-stripped column and is a distinct product decision — **D-23**.

Tests (unit for the normalizer, instrumented for the DAO):
casing · repeated/leading/trailing whitespace · non-breaking space · accented
characters (`Elevação` vs `elevação`) · `%` in the name and in the query ·
`_` in the name and in the query · a literal backslash · empty query ·
active-vs-archived filtering · a name that normalizes to empty.

---

## E. Tracking types

**Recommended: Option A** — only the types the editor, validation, formatting
and tests fully support in Milestone 1:

```kotlin
enum class ExerciseTrackingType { WEIGHT_AND_REPS, REPS_ONLY, DURATION }
```

- `supportsLoad` is true only for `WEIGHT_AND_REPS`.
- `DISTANCE_AND_DURATION` and `BODYWEIGHT_WITH_OPTIONAL_LOAD` are **deferred**
  until a milestone actually renders and validates distance and optional added
  load. Adding enum values later needs no migration (the column is TEXT).
- Persisted by **stable `name` string**, never by ordinal, pinned by a unit
  test. An unrecognised value read from the database is a mapping failure, never
  a silent default.

Option B (all five) is only acceptable if the editor, validation, formatting
and tests cover every one within this milestone — **D-21**.

---

## F. Application layer

Contract, owned by `application/exercise`:

```kotlin
interface ExerciseRepository {
    fun observe(criteria: ExerciseQueryCriteria): Flow<List<Exercise>>
    suspend fun findById(id: ExerciseId): Exercise?
    suspend fun findIdByNameKey(nameKey: String): ExerciseId?
    suspend fun save(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError>
}

data class ExerciseQueryCriteria(
    val status: ExerciseStatusFilter = ACTIVE,   // ACTIVE | ARCHIVED
    val normalizedQuery: String = "",
)
```

No `deleteById`, no reference-checking hook — those arrive when plans and
workouts create references worth protecting.

Use cases (`@Inject constructor`, plain-constructible in tests):

| Use case           | Failures                                                                 |
|--------------------|--------------------------------------------------------------------------|
| `ObserveExercises` | surfaced through the Flow (§H)                                           |
| `GetExercise`      | `NotFound`                                                               |
| `CreateExercise`   | validation errors, `DuplicateName`, `PersistenceUnavailable`             |
| `UpdateExercise`   | `NotFound`, validation errors, `DuplicateName`, `PersistenceUnavailable` |
| `ArchiveExercise`  | `NotFound`, `AlreadyArchived`, `PersistenceUnavailable`                  |
| `RestoreExercise`  | `NotFound`, `NotArchived`, `PersistenceUnavailable`                      |

Supporting contracts, each with a concrete current consumer and a test that
depends on it: `Clock` (→ `Instant`) and `IdentifierGenerator` (→ UUID string).

Commands carry raw UI-shaped values (`name: String`, `restSeconds: Long?`, …);
the use case parses them into domain value objects, so parsing and validation
happen exactly once, at the boundary.

**Error model.** `DomainResult<out T, out E>` (`Success` / `Failure`) in
`domain/common` — no Arrow, and not Kotlin `Result` (which would force
Throwable-based modelling of expected outcomes).

---

## G. Persistence, concurrency, exceptions

**Duplicate names — two layers, one authority.**

- The use case performs a `findIdByNameKey` precheck purely so the UI can show
  a friendly, field-attached error.
- The `UNIQUE` index is the **authoritative, concurrency-safe** guarantee. The
  repository translates a constraint violation on
  `index_exercises_name_key` into `ExercisePersistenceError.DuplicateName`, and
  the use case maps that to the same user-facing error as the precheck. A lost
  race therefore produces a correct message, not a crash.

**Exception translation — narrow and explicit.**

- Translated: `SQLiteConstraintException` (→ `DuplicateName` when the message
  names the `name_key` index, otherwise `Unavailable`), `SQLiteFullException`,
  `SQLiteDiskIOException`, `SQLiteDatabaseCorruptException`, and finally
  `SQLiteException` (→ `Unavailable`).
- **Not** translated: everything else propagates.
- **`runCatching`, `catch (e: Exception)` and `catch (e: Throwable)` are
  forbidden in this slice** — they swallow `CancellationException` and break
  structured concurrency. Only the listed `SQLiteException` subtypes are caught,
  none of which can be a `CancellationException`.
- In `Flow.catch`, the handler rethrows immediately if the throwable is a
  `CancellationException` before emitting a failure state.

**Transactions.** Every Milestone 1 write touches exactly one row, so no
multi-statement transaction is required and none is invented.
`RoomDatabase.withTransaction` is introduced when a real multi-record operation
exists — the optional built-in catalog batch insert (§J) is the first candidate
and will use a single transaction.

**Persisted numeric types.** `Long` for `default_load_increment_grams`,
`default_rest_seconds`, `archived_at`, `created_at`, `updated_at`. Integer grams
rather than a floating-point kg value, because this number will later drive
progression arithmetic and must not accumulate REAL rounding drift (D-7).

**Room schema (`exercises`, v1)**

| Column                         | Type     | Notes                            |
|--------------------------------|----------|----------------------------------|
| `id`                           | TEXT     | PK, UUID                         |
| `name`                         | TEXT     | display                          |
| `name_key`                     | TEXT     | **UNIQUE index**, exact equality |
| `tracking_type`                | TEXT     | stable enum name                 |
| `instructions`                 | TEXT?    |                                  |
| `default_load_increment_grams` | INTEGER? | Long                             |
| `default_rest_seconds`         | INTEGER? | Long                             |
| `origin`                       | TEXT     | `BUILT_IN` / `CUSTOM`            |
| `archived_at`                  | INTEGER? | epoch millis, indexed            |
| `created_at`                   | INTEGER  | epoch millis                     |
| `updated_at`                   | INTEGER  | epoch millis                     |

DAO: `observe(status, query)` → `Flow<List<ExerciseEntity>>` ordered by
`name_key ASC`; `observe`; `findById`; `findIdByNameKey`; `insert`; `update`. No upsert and no delete in Milestone 1.

`RepFlowDatabase`: version 1, `exportSchema = true`, schema JSON committed.
**No `fallbackToDestructiveMigration` anywhere** — asserted by a test that the
builder configuration does not enable it, and by review.

**No migration placeholder.** There is no `RepFlowDatabaseMigrationTest` and no
`RepFlowMigrations` abstraction at version 1. If `Room.databaseBuilder` requires
it, `addMigrations()` is simply not called. The first `MigrationTestHelper` test
is written when version 2 introduces a real 1→2 migration.

**Schema verification** — not a file-existence assertion.

**While `app/schemas` is untracked** (initial generation):

```bash
./gradlew kspDebugKotlin
git status --short -- app/schemas
find app/schemas -type f -name '*.json' -print
```

Confirm the expected JSON exists, inspect it, and confirm `app/schemas` is
not git-ignored. Report whether the schema is untracked, modified, or clean.
`git diff --exit-code -- app/schemas` is insufficient while the schema is
untracked — it exits 0 without detecting the new file. Do not stage the file
automatically.

**After `app/schemas` is tracked**, verify no drift and no unexpected new
untracked file:

```bash
./gradlew kspDebugKotlin
git diff --exit-code -- app/schemas
git status --short -- app/schemas
```

A clean schema verification requires both: no tracked schema diff **and** no
unexpected untracked schema file.

This runs locally in CP5. Documented rule: any intentional schema change
requires a new database version, an explicit migration, regenerated and
reviewed exported schemas, and — once a previous version exists — migration
tests.

**Mapping.** `data/exercise/ExerciseEntityMapper` is pure Kotlin and directly
unit-testable. Entity→domain returns a result; an unrecognised `tracking_type`
or `origin` fails the whole snapshot rather than silently dropping the row, so
corrupt data is loud instead of invisible (**D-28**).

---

## H. Presentation: state, effects, failures

### Stable state versus one-off effects

```kotlin
data class ExerciseListUiState(
    val query: String,
    val filter: ExerciseStatusFilter,
    val content: ExerciseListContent,
    val messages: List<UserMessage>,      // FIFO queue, never a single slot
)

sealed interface ExerciseListContent {
    data object Loading
    data class Content(val items: List<ExerciseListItem>)
    data class Empty(val reason: EmptyReason)   // NO_EXERCISES | NO_SEARCH_RESULTS | NO_ARCHIVED
    data class ObservationFailed(val reason: FailureReason)
}

sealed interface UserMessage {
    val id: Long                                  // unique, monotonic

    data class ExerciseArchived(...) : UserMessage   // snackbar + Undo action
    data class OperationFailed(...) : UserMessage
}

fun onMessageShown(messageId: Long)   // removes exactly that id
```

`onMessageShown` takes the message id, so a newer message queued while an older
snackbar was displaying can never be discarded. There is no parameterless
consume method.

Representation of each required case:

| Case                             | Representation                                                                                                                                                                         |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Archive snackbar with undo       | `UserMessage.ExerciseArchived` in the queue; the Undo action calls `RestoreExercise`; consumed via `onMessageShown(id)` on `SnackbarResult`                                            |
| Persistence error                | `UserMessage.OperationFailed` for write failures; `ExerciseListContent.ObservationFailed` for read failures                                                                            |
| Navigation after successful save | Editor state field `savedExerciseId: ExerciseId?` plus `onSaveNavigationHandled()` — a state flag, not a `Channel`, so it is idempotent, survives recreation, and is directly testable |
| Discard-changes dialog           | `ExerciseEditorUiState.isDiscardDialogVisible: Boolean` — stable state, not an effect                                                                                                  |
| Delete                           | **Does not exist in Milestone 1**                                                                                                                                                      |

No `Channel`-based effect bus is introduced; nothing in this slice needs one.

### Observation failure and retry

```kotlin
combine(criteria, retryTrigger) { c, _ -> c }
    .flatMapLatest { c ->
        observeExercises(c)
            .map { Content(...) or Empty(...) }
            .onStart { emit(Loading) }
            .catch { t ->
                if (t is CancellationException) throw t else emit(
                    ObservationFailed(
                        translate(t)
                    )
                )
            }
    }
```

- **Loading** — before the first emission.
- **Content** — non-empty list.
- **Empty** — empty list, with the reason derived from filter + query.
- **ObservationFailed** — translated failure; the screen shows an error panel
  with a **Retry** button.
- **Retry** increments `retryTrigger`, which makes `flatMapLatest` resubscribe
  to a brand-new Room Flow. This is why the failure is a *value* and not an
  uncaught exception: a Room Flow that throws is terminated at the source, so
  visible recovery requires explicit resubscription.

### SavedStateHandle

- Route argument `exerciseId` is read from `SavedStateHandle` (never passed via
  a constructor parameter captured at composition).
- The editor writes every form field into `SavedStateHandle` on change, so a
  partially edited exercise survives configuration change **and** process death.
- This is **D-22**. If approved, it is verified by a ViewModel test that builds
  a second ViewModel from the same `SavedStateHandle` contents and asserts the
  draft is restored. **Process-death recovery will not be claimed anywhere —
  docs, summary or release notes — unless that test exists and passes.**

### Screens

`presentation/navigation/RepFlowNavHost.kt`, destinations `exercises` (start),
`exercises/new`, `exercises/{exerciseId}`; string routes (D-1). Each screen is
split into a stateful route composable (owns the ViewModel) and a stateless
screen composable (state in, events out), so Compose tests need no Hilt.
Tracking-type labels and unit formatting are resolved in Composables from string
resources; ViewModels stay resource-free.

---

## I. Testing and CI strategy

**JVM unit tests** (`app/src/test`)

1. `ExerciseNameTest` — the full normalization matrix from §D.
2. `LoadIncrementTest`, `RestDurationTest`, `ExerciseInstructionsTest`.
3. `ExerciseTest` — factory validation, archive/restore timestamps,
   load-increment/tracking-type consistency.
4. `ExerciseTrackingTypeTest` — capability flags and **pinned persisted names**.
5. Use-case tests against `InMemoryExerciseRepository` + `FixedClock` +
   `SequentialIdentifierGenerator`: create, duplicate name (case / whitespace /
   NFKC variants), duplicate reported by the repository despite a passing
   precheck, update preserving `createdAt` and `origin`, archive/restore
   idempotence errors, not-found paths.
6. `ExerciseEntityMapperTest` — round-trip plus unrecognised enum failure.
7. ViewModel tests (coroutines-test + Turbine): filter and query transitions,
   loading → content → empty, **observation failure → retry → content**,
   archive queues an undo message, `onMessageShown(id)` removes only that id and
   preserves a newer one, editor validation, dirty/discard, save-then-navigate
   flag, and the `SavedStateHandle` draft-restoration test if D-22 is approved.
8. `LayerBoundaryTest` — see below.

**Instrumented tests** (`app/src/androidTest`)

9. `ExerciseDaoTest` — real SQLite, in-memory: insert/observe ordering, status
   filter, the full search matrix including `%`, `_`, backslash and accents,
   `name_key` UNIQUE conflict.
10. `LocalExerciseRepositoryTest` — repository + mapper + exception translation
    against a real in-memory database, including the duplicate-name translation.
11. `ExerciseListScreenTest`, `ExerciseEditorScreenTest` — `createComposeRule`
    against the **stateless** screens, no Hilt: rendering, three empty states,
    observation-failure panel and retry, filter chips, archive/undo, validation
    errors, save enablement, discard dialog.
12. A NavHost smoke test replacing `MainActivitySmokeTest`.

**Layer boundary guardrail.** `LayerBoundaryTest` is documented in code and in
the docs as a *lightweight guardrail, not complete architectural enforcement* —
real enforcement arrives with Gradle modules. It:

- parses only `package` and `import` declarations, scanning from the top of the
  file and stopping at the first top-level declaration;
- tracks `/* … */` and `//` state so commented-out or string-embedded text is
  never treated as an import;
- resolves import aliases (`import a.b.C as D` → `a.b.C`);
- checks production sources (`src/main/kotlin`) separately from test sources,
  with test sources allowed to reference any layer;
- fails with `file:line — <import> violates <rule>`;
- asserts each configured layer directory exists and is non-empty, so a rule can
  never pass vacuously. **No placeholder packages are created to satisfy it** —
  all five layers contain real code in this milestone.

Rules enforced: domain imports no `android.*`, `androidx.*`, `androidx.room.*`,
`dagger.*`, `javax.inject.*`, `kotlinx.coroutines.*`, `kotlinx.serialization.*`,
nor any other `com.repflow.app` layer; presentation imports no
`com.repflow.app.infrastructure.*`, `androidx.room.*` or `android.database.*`;
infrastructure does not import presentation; data does not import presentation.

**CI and device execution**

- CI keeps compiling instrumented tests (`assembleDebugAndroidTest`) and gains
  the schema verification check (see §G schema verification procedure).
- Milestone 1 requires **at least one actual execution** of
  `connectedDebugAndroidTest` on an emulator or the S24 Ultra, with the command,
  device and result recorded in the completion summary.
  Note: `connectedDebugAndroidTest` does not support `--tests` filtering
  (it is a `DeviceProviderInstrumentTestTask`, not a JVM test task). Run the
  full suite: `./gradlew connectedDebugAndroidTest`.
- **Instrumented tests will never be reported as passing if they were only
  compiled.** If no device is available, they are reported as *written but not
  executed* and the milestone is not complete.
- Emulator-backed GitHub Actions remains an **optional** separate/manual job or
  follow-up (D-17); it is not added without explicit approval.

---

## J. Optional sub-step — built-in exercises (Milestone 1.1)

Attempted **only after** custom exercise management is complete and verified end
to end, and only if D-10 is approved.

- **Very small catalog** proposed for approval — 8 exercises with hardcoded
  stable UUIDs, as a Kotlin object (no JSON, no new dependency):
  Barbell Back Squat, Barbell Bench Press, Barbell Deadlift, Barbell Overhead
  Press, Barbell Row (all `WEIGHT_AND_REPS`); Pull-Up, Push-Up (`REPS_ONLY`);
  Plank (`DURATION`).
- Installation is idempotent: **insert only ids that are absent**, in a single
  transaction. It never overwrites a user's edits and never restores an archived
  built-in.
- **`RepFlowApplication` is not modified.** No unmanaged coroutine is started.
  Installation runs through an `ExerciseLibraryStartupCoordinator` invoked from
  `ExerciseListViewModel` inside `viewModelScope`, guarded by a one-shot flag, on
  the IO dispatcher.
- Its lifecycle is observable: `ExerciseListUiState.libraryPreparation` is
  `Preparing | Ready | Failed(retryable)`, rendered by the list screen with a
  retry affordance. Idempotence makes a retry harmless.
- Tests: running twice produces no duplicates; a user-edited built-in is not
  overwritten; an archived built-in is not resurrected; a failure surfaces as
  `Failed` and a retry succeeds.
- Built-ins are editable and archivable. Deletion does not exist in Milestone 1
  for any exercise, so "built-ins cannot be deleted" is not a special case yet.

If D-10 is declined, `ExerciseOrigin` still exists (every exercise is `CUSTOM`)
and nothing else changes.

---

## K. Scope removed or deferred, and why

| Removed                                                                | Reason                                                                                                                                                                    | Returns in                                  |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| Permanent deletion                                                     | Nothing yet references an exercise, so deletion protects nothing and would need redesign the moment plans and history exist. Archive already satisfies "remove from view" | When plan/workout references exist (M2–M3)  |
| Reference-aware deletion extension point                               | A speculative abstraction with no current consumer — exactly the ADR 0003 risk                                                                                            | With deletion                               |
| `deleteById` on the repository and DAO                                 | Same                                                                                                                                                                      | With deletion                               |
| Large built-in catalog                                                 | Content work that does not validate the architecture; risks becoming the bulk of the milestone                                                                            | M1.1 / later                                |
| Application-start seeding                                              | Would require an unmanaged coroutine in `RepFlowApplication` with no observable lifecycle                                                                                 | M1.1, via a coordinator with real lifecycle |
| `RepFlowDatabaseMigrationTest` at v1                                   | A test that pretends to verify a migration that does not exist                                                                                                            | When v2 introduces a real 1→2 migration     |
| `RepFlowMigrations` abstraction                                        | No current value                                                                                                                                                          | With v2                                     |
| Schema file-existence test                                             | Replaced by schema drift verification: `kspDebugKotlin` + `git status` + `git diff --exit-code` (see §G)                                                                  | —                                           |
| Emulator CI as a blocking gate                                         | Cost and complexity not yet approved; a real local run gives the same confidence for a single-developer project                                                           | Optional follow-up                          |
| `DISTANCE_AND_DURATION`, `BODYWEIGHT_WITH_OPTIONAL_LOAD`               | Would be enum values the editor, validation and formatting do not meaningfully support                                                                                    | When a milestone renders and validates them |
| Home screen, muscle groups, substitutions, DataStore, units preference | Not needed to validate the slice                                                                                                                                          | Later milestones                            |

---

## L. Implementation order — 12 checkpoints

Each checkpoint stops for review before the next. No commit or push unless
explicitly requested.

| #  | Checkpoint                                                         | Files                                                                                                                                                                                                                                                                                  | Narrowest check                                                                                         |
|----|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 1  | **M0 cleanup**, separate reviewed commit                           | `README.md`, `AndroidManifest.xml`, `themes.xml`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, `app/build.gradle.kts`, `.github/workflows/ci.yml`                                                                                                                       | `./gradlew assembleDebug`                                                                               |
| 2  | **Dependencies + schema export config**                            | `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.gitignore`                                                                                                                                                                                                                      | `./gradlew assembleDebug`                                                                               |
| 3  | **Domain + pure JVM tests**                                        | `domain/common/DomainResult.kt`, `domain/exercise/{Exercise, ExerciseId, ExerciseName, ExerciseInstructions, ExerciseTrackingType, ExerciseOrigin, LoadIncrement, RestDuration, ExerciseValidationError}.kt`; tests incl. `LayerBoundaryTest`; delete `ProjectSetupSmokeTest`          | `./gradlew testDebugUnitTest`                                                                           |
| 4  | **Application contracts + use cases**, fake-based tests            | `application/common/{Clock, IdentifierGenerator}.kt`, `application/exercise/{ExerciseRepository, ExerciseQueryCriteria, ExerciseOperationError, ExercisePersistenceError, ObserveExercises, GetExercise, CreateExercise, UpdateExercise, ArchiveExercise, RestoreExercise}.kt` + tests | `./gradlew testDebugUnitTest`                                                                           |
| 5  | **Room entity, DAO, mapper, repository**                           | `infrastructure/database/{RepFlowDatabase}.kt`, `infrastructure/database/exercise/{ExerciseEntity, ExerciseDao}.kt`, `data/exercise/{ExerciseEntityMapper, LocalExerciseRepository}.kt`, generated `app/schemas/.../1.json`                                                            | `./gradlew testDebugUnitTest kspDebugKotlin` then schema verification (see §G)                          |
| 6  | **Run DAO + repository tests on a real emulator or the S24 Ultra** | `androidTest/.../ExerciseDaoTest.kt`, `LocalExerciseRepositoryTest.kt`                                                                                                                                                                                                                 | `./gradlew connectedDebugAndroidTest` — result recorded verbatim (no `--tests` support)                 |
| 7  | **Hilt bindings**                                                  | `infrastructure/di/{DatabaseModule, RepositoryModule, SystemModule, IoDispatcher}.kt`, `infrastructure/time/SystemClock.kt`, `infrastructure/id/UuidIdentifierGenerator.kt`                                                                                                            | `./gradlew assembleDebug`, app boots                                                                    |
| 8  | **Exercise list end to end**                                       | `presentation/navigation/{RepFlowNavHost, RepFlowDestinations}.kt`, `presentation/exercise/list/*`, `MainActivity.kt`, `strings.xml`; replace `MainActivitySmokeTest`                                                                                                                  | `./gradlew testDebugUnitTest lintDebug`                                                                 |
| 9  | **Exercise editor end to end**                                     | `presentation/exercise/editor/*`, `presentation/exercise/components/*`, `ExerciseUiFormatting.kt`, `strings.xml`                                                                                                                                                                       | `./gradlew testDebugUnitTest`                                                                           |
| 10 | **Archive / restore + snackbar undo**                              | list ViewModel + screen, message queue                                                                                                                                                                                                                                                 | `./gradlew testDebugUnitTest`, then Compose tests                                                       |
| 11 | **Optional built-in catalog sub-step** (§J, only if D-10 approved) | `infrastructure/database/exercise/BuiltInExerciseCatalog.kt`, `application/exercise/EnsureBuiltInExercisesInstalled.kt`, `presentation/exercise/ExerciseLibraryStartupCoordinator.kt`                                                                                                  | `./gradlew testDebugUnitTest`                                                                           |
| 12 | **Docs + final architectural review**                              | `docs/ROADMAP.md`, `docs/TECHNICAL_DECISIONS.md`, `README.md`, optional new ADR                                                                                                                                                                                                        | Full suite in §M                                                                                        |

### Dependencies introduced (checkpoint 2)

| Dependency                                      | Configuration      | Reason                           |
|-------------------------------------------------|--------------------|----------------------------------|
| `androidx.room:room-runtime`, `room-ktx`        | implementation     | persistence                      |
| `androidx.room:room-compiler`                   | ksp                | codegen                          |
| `androidx.navigation:navigation-compose`        | implementation     | list ↔ editor                    |
| `androidx.hilt:hilt-navigation-compose`         | implementation     | `hiltViewModel()`                |
| `androidx.lifecycle:lifecycle-runtime-compose`  | implementation     | `collectAsStateWithLifecycle`    |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | implementation     | make the transitive dep explicit |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | testImplementation | coroutine/Flow tests             |
| `app.cash.turbine:turbine`                      | testImplementation | Flow assertions                  |

`androidx.room:room-testing` is **not** added yet — it exists to drive
`MigrationTestHelper`, which has nothing to test at version 1.

Not added: DataStore, kotlinx.serialization, any mocking library, Arrow,
ArchUnit/Konsist, Robolectric, WorkManager, material-icons-extended.

Build config:
`ksp { arg("room.schemaLocation", "$projectDir/schemas"); arg("room.generateKotlin", "true") }`,
androidTest assets source dir pointing at `schemas/`, and `.gitignore` must not
ignore `app/schemas`.

---

## M. Definition of Done

Milestone 1 is complete only when:

- custom exercises can be created, listed, searched, edited, archived and
  restored;
- data survives app restart;
- duplicate normalized names are rejected safely (precheck **and** UNIQUE
  constraint translation);
- no Room types cross the application or presentation boundaries;
- the app starts on the exercise list;
- unit tests pass;
- DAO and repository instrumented tests have **actually run at least once** on
  a device or emulator, with the result recorded;
- stateless Compose UI tests pass;
- the exported Room v1 schema is committed and schema drift verified
  (run `kspDebugKotlin`, check `git status --short -- app/schemas` and
  `git diff --exit-code -- app/schemas`; both must be clean);
- no destructive migration fallback exists;
- `spotlessCheck`, `detekt` and `lintDebug` pass;
- the feature has been installed and manually smoke-tested on the S24 Ultra or a
  documented emulator;
- deferred work (built-in catalog, emulator CI, deletion, the two deferred
  tracking types) is clearly tracked in `docs/ROADMAP.md`.

Full verification suite:

```bash
export JAVA_HOME=/opt/android-studio/jbr    # or the JDK chosen in D-19
./gradlew spotlessApply && ./gradlew spotlessCheck
./gradlew detekt
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew kspDebugKotlin
git status --short -- app/schemas          # must show no untracked/modified schema
git diff --exit-code -- app/schemas        # must be clean once schema is tracked
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest          # device/emulator required
./gradlew installDebug                       # manual smoke test
```

---

## N. Decisions — all approved

All decisions below are **final** for Milestone 1. None remain open.

### Approved with the exact wording given

| #        | Decision               | Approved outcome                                                                                                                                                                                                                                                                                                                                                                    |
|----------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **D-19** | JVM alignment          | Local Gradle runtime: Android Studio bundled JBR 21. CI runtime: JDK 21. Java source/target compatibility: 17. Kotlin JVM bytecode target: 17 (set explicitly). Commit `gradle/gradle-daemon-jvm.properties` **only if required and verified** by the current Gradle setup — checkpoint 1 verifies this rather than assuming it. Do not install or auto-provision a separate JDK 17 |
| **D-21** | Tracking types         | **Option A only**: `WEIGHT_AND_REPS`, `REPS_ONLY`, `DURATION`. `DISTANCE_AND_DURATION` and `BODYWEIGHT_WITH_OPTIONAL_LOAD` deferred until a concrete feature requires them                                                                                                                                                                                                          |
| **D-22** | Editor draft recovery  | `SavedStateHandle` for **primitive/string draft values and route arguments only** — never domain objects. Supported claim: recovery after **Activity/process recreation when Android restores saved state**. Explicitly **not** claimed: recovery after force-stop, clear-data, or uninstall. Tests cover only the supported case                                                   |
| **D-23** | Accent behavior        | Accent-sensitive in Milestone 1. No diacritic stripping, **no second accent-folded column**                                                                                                                                                                                                                                                                                         |
| **D-24** | Observation failure    | Visible `ObservationFailed` with Retry. Retry preserves the current query and filter, subscribes to a fresh repository `Flow`, and the ViewModel is never left permanently cancelled after one failure                                                                                                                                                                              |
| **D-25** | Normalization order    | (1) Unicode NFKC, (2) trim, (3) collapse `[\s\p{Z}]+` runs into one ASCII space, (4) `lowercase(Locale.ROOT)`                                                                                                                                                                                                                                                                       |
| **D-26** | Display name storage   | `name` — user-facing display value, NFKC-normalized, trimmed and with internal whitespace collapsed. Capitalization and accents are preserved. `name_key` — the display value lowercased with `Locale.ROOT`, used for identity and search.                                                                                                                                          |
| **D-27** | Ordering               | `name_key ASC`, then `id ASC` as a stable tie-breaker. Never `COLLATE NOCASE`                                                                                                                                                                                                                                                                                                       |
| **D-28** | Unknown persisted enum | Mapping failure — never a silent default or skipped row. Fails the repository emission, includes the affected exercise id in diagnostic context, surfaces a retryable observation error to presentation                                                                                                                                                                             |

### Previously recommended, now confirmed approved

| #        | Decision         | Approved outcome                                                                                                                                                                                                      |
|----------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **D-10** | Built-in catalog | Optional M1.1 sub-step, not required for the custom-exercise slice. `origin` stays in the v1 schema now because this is a concrete near-term requirement (see §P item 12)                                             |
| **D-12** | Permanent delete | Not implemented in Milestone 1 — no delete DAO method, use case, or UI. Archive/restore is the only removal behavior                                                                                                  |
| **D-17** | Emulator CI      | Optional follow-up, not a blocking gate. A real local device/emulator run is mandatory before declaring Milestone 1 complete; compiled-only instrumented tests are never reported as passing                          |
| **D-20** | Search           | Normalized substring match against `name_key`, `%`/`_`/`\` escaped, explicit `ESCAPE` clause, bound parameter (never string-interpolated SQL — see §P item 5), accent-sensitive per D-23, `COLLATE NOCASE` never used |

### Carried over, unchanged

| #        | Decision                                                                            | Recommendation                                                                                                                                                                      |
|----------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **D-1**  | Navigation structure (an open decision in `TECHNICAL_DECISIONS.md`)                 | Single NavHost, string routes, `exercises` as the temporary start destination, no bottom navigation until Home exists. Avoid type-safe routes (would pull in kotlinx.serialization) |
| **D-2**  | Home screen in Milestone 1                                                          | Out of scope                                                                                                                                                                        |
| **D-3**  | Exercise alternatives / substitutions                                               | Deferred to M2–M3; meaningless without plans and workouts                                                                                                                           |
| **D-4**  | Muscle group / equipment metadata                                                   | Excluded from M1                                                                                                                                                                    |
| **D-5**  | `javax.inject.@Inject` on application use-case constructors                         | Acceptable — a pure annotation API, and the boundary test forbids `javax.inject` only in **domain**                                                                                 |
| **D-6**  | Units                                                                               | Kilograms only; no unit preference, no DataStore                                                                                                                                    |
| **D-7**  | Load increment stored as integer grams (`Long`)                                     | Approve — avoids REAL rounding drift in a value that later drives progression                                                                                                       |
| **D-8**  | `java.time.Instant` in domain, epoch millis (`Long`) in Room                        | Approve — pure JVM, available at minSdk 28                                                                                                                                          |
| **D-9**  | Uniqueness spans active **and** archived rows                                       | Approve — otherwise restoring an archived duplicate creates a conflict                                                                                                              |
| **D-11** | Built-ins editable and archivable                                                   | Approve (no deletion exists in M1 at all)                                                                                                                                           |
| **D-13** | In-house `DomainResult<T, E>`                                                       | Approve — not Kotlin `Result`, not Arrow                                                                                                                                            |
| **D-14** | Hand-written `LayerBoundaryTest`, documented as a guardrail rather than enforcement | Approve — zero new dependency; real enforcement arrives with Gradle modules                                                                                                         |
| **D-15** | No new Gradle modules in M1                                                         | Approve — extract `:domain` / `:application` once the slice shows where the boundaries actually are                                                                                 |
| **D-16** | Compose tests target stateless screens, no Hilt test runner                         | Approve                                                                                                                                                                             |
| **D-18** | `android:allowBackup="false"` before Room lands                                     | Approve — Auto Backup restoring a stale Room DB contradicts ADR 0002; explicit versioned backup arrives in M7                                                                       |

---

## P. Additional implementation corrections (approved, binding)

1. **Duplicate-name checks on update exclude the exercise being edited**, by id
   — `findIdByNameKey` results matching the current id are not a conflict.
2. The database `UNIQUE(name_key)` constraint remains the **authoritative**
   concurrency-safe guarantee; the application-level check is only a friendly
   precheck.
3. **Create** uses **insert** semantics; the specific uniqueness constraint
   failure on `index_exercises_name_key` translates to `DuplicateName`.
4. **Update** verifies the exercise exists, then uses **update** semantics —
   **no generic upsert** that could insert a missing id.
5. **Never interpolate search input into SQL.** Build and escape the `LIKE`
   pattern in Kotlin, pass it as a bound parameter, with an explicit `ESCAPE`
   clause.
6. Escape order: backslash, then percent, then underscore.
7. Never swallow `CancellationException`; no broad `catch (e: Exception)`; no
   `runCatching` around suspend work; unrelated SQLite failures are never
   mapped to `DuplicateName`.
8. **Archive Undo is idempotent** — if the exercise is already active, the undo
   is treated as satisfied (neutral no-op result), not an error.
9. **Saving an unchanged editor draft performs no database write and does not
   bump `updatedAt`** — the editor diffs the draft against the loaded exercise
   before calling `UpdateExercise`.
10. Editor state gains `isSaving: Boolean`; the save action is a no-op while
    `isSaving` is true, preventing concurrent/double submission.
11. UI messages have stable unique ids; consumption is exclusively
    `onMessageShown(messageId)` — no parameterless consume method anywhere.
12. `origin` stays in the v1 schema **specifically because** the approved
    optional M1.1 built-in catalog is a concrete near-term requirement. The
    catalog itself is **not implemented** until the custom-exercise slice
    (checkpoints 1–10) is complete and verified.
13. No permanent deletion, no delete DAO method, no delete use case, no delete
    UI anywhere in Milestone 1.
14. No fake version-1 migration test.
15. `RepFlowApplication` is not modified for built-in seeding, now or in the
    optional M1.1 sub-step.

---

## O. Residual risks

- **Instrumented execution** is the weakest link. It is now an explicit
  Definition-of-Done gate rather than an assumption.
- **Schema drift** is guarded by regenerate-and-diff rather than by a
  file-existence assertion.
- **The boundary test is a guardrail, not enforcement.** It cannot see
  reflection, generated code, or transitive dependencies. Gradle modules remain
  the real mechanism, deferred until the slice shows where the seams are.
- **Normalization is a one-way door for stored data.** `name_key` is persisted,
  so changing the pipeline later requires a migration that recomputes every key.
  This is why D-23, D-25 and D-26 need answering before checkpoint 3.
