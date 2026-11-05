# fix-restore-damaged-file — Execution Plan (Revision 2)

- Work item: `fix-restore-damaged-file` (backlog order 20, size 1, `docs/items/fix-restore-damaged-file.md`)
- Governing workflow version: `2.2`
- Base commit: `132aa9a0965ff948fbf027dd4bcadc27945b1835`
- Plan revision: 2

Pull request title: `fix: show an error when a restored backup file is damaged`

## Goal and scope

Don't lose data, don't hide failures. Issue #29, from `fix-backup-all-workouts`' review,
re-checked against the base commit:

- **A damaged backup file closes the app.** `Restore` reads the file with
  `LocalBackupRepository.parseSnapshot`, which calls `BackupJsonMapper.parse` (catches only
  `JSONException`, `BackupJsonMapper.kt:87`) and then `toDomainSnapshot`
  (`LocalBackupRepository.kt:112`), which calls the mappers. Four kinds of bad value throw there:
  - a zero-length rest timer: `RestTimer` (`require(totalDurationSeconds > 0)`, `RestTimer.kt:23`,
    reached from `WorkoutEntityMapper.toRestTimer`, `:112`);
  - a blank workout id: `WorkoutSessionId` (`require(value.isNotBlank())`, `WorkoutSessionId.kt:9`,
    reached at `WorkoutEntityMapper.kt:101`, or first at `:137` when exercise rows point at the
    blank id);
  - a malformed date: `LocalDate.parse` (`FutsalSessionMapper.kt:33`, `RecoveryEntryMapper.kt:45`);
  - an unknown progression key: `BackupJsonMapper.kt:346` and `:350` accept any string, and
    `ProgressionRecommendationMapper.toResult` ends in `else -> error(...)` (`:80`), reached at
    `:52` for `result` and at `:43` for `overrideResult` when `overrideAt` is set.

  These throw `IllegalArgumentException`, `DateTimeParseException` or `IllegalStateException` out
  of `parseSnapshot`, and nothing catches them, so the app closes. No stored data is lost, because
  the parse runs before `replaceAll` clears anything. The failure the user should see already
  exists: `BackupRestoreError.InvalidBackup` is mapped by `BackupViewModel.kt:182` to the message
  "That file isn't a valid RepFlow backup." (`strings.xml:389`), drawn by `BackupScreen.kt:150`.

**Out of scope:** the export-side dead end recorded on the issue (one unreadable saved workout
makes every export fail). The item says to check it only if the fix is one mechanism; this
fix is on the restore parse path and shares nothing with the export read, so that note stays
on the issue (R3). Also out of scope: validating each field with its own message, a new
error type, and any change to `BackupJsonMapper`'s format or the schema version. An unknown
`overrideResult` with no `overrideAt` doesn't throw (`ProgressionRecommendationMapper.kt:42`
skips it) and stays as it is.

## Design (no new mechanism)

`LocalBackupRepository.parseSnapshot` already turns every expected parse problem into
`DomainResult.Failure(BackupValidationError.Malformed(...))`. The gap is that three exception
types escape it on values that come from the file's own text. Two small edits close it.

First, in that one function:

```kotlin
override fun parseSnapshot(json: String): DomainResult<BackupSnapshot, BackupValidationError> =
    try {
        when (val parsed = BackupJsonMapper.parse(json)) { ...unchanged... }
    } catch (expected: IllegalArgumentException) {
        DomainResult.Failure(BackupValidationError.Malformed(expected.message ?: "invalid value"))
    } catch (expected: DateTimeException) {
        DomainResult.Failure(BackupValidationError.Malformed(expected.message ?: "invalid date"))
    }
```

Second, `ProgressionRecommendationMapper.kt:80` throws the type the catch expects, with the same
message:

```kotlin
else -> throw IllegalArgumentException("Unknown ProgressionResult storage key: $this")
```

Why the catch is here and not in each mapper: the domain constructors' `require` is their contract
and stays (domain stays pure Kotlin, and normal in-app code never builds a bad value); the
backup file is the one place untrusted text becomes a domain value, so the boundary that
reads the file is where a thrown precondition becomes a failure. It covers those two exception
types for every table, including a future field that throws one of them. It does not cover any
other type, so a future mapper that throws something else on bad text would close the app again.

Why the mapper changes instead of `parseSnapshot` also catching `IllegalStateException`: an
unknown key is a bad argument, so `IllegalArgumentException` is the accurate type, and catching
`IllegalStateException` would also hide real program bugs on this path, which should crash visibly.
The mapper's only other read is `LocalProgressionRecommendationRepository.kt:59`. Its callers (the
recommendation and workout screens, `ComputeProgressionRecommendation`, `RecordManualOverride` and
export) behave the same, because nothing in `app/src/main` catches or tests for either exception
type by name. `ProgressionRecommendationMapperTest` asserts no exception today.

What else was searched: `data/` and `domain/` for `error(`, `check(`, `checkNotNull`,
`requireNotNull`, `!!`, `throw `, `.valueOf(`, `.first()`, `.first {`, `.last()`, `.single(`,
`.getValue(` and unchecked casts. On the restore path, `:80` is the only throw besides `require`
and `LocalDate.parse`. The `*MappingException` classes are thrown only by `Local*Repository` reads,
and the two unchecked casts (`ExerciseEntityMapper.kt:74`, `TrainingPlanEntityMapper.kt:158`) run
only after the field-error check has returned.

`parseSnapshot` is a plain non-suspend function, so no `CancellationException` can pass through
these two catches, and `IllegalArgumentException` and `DateTimeException` are narrow (not
`Exception`/`Throwable`), so the rule against broad `catch` around suspend work is not touched.
`NumberFormatException` is an `IllegalArgumentException` and is covered too.

`RestoreBackup` returns `InvalidBackup` for the failure, the ViewModel shows the existing
message, and `replaceAll` is never called, so stored data is untouched.

No Room, navigation or format change, and no domain behavior change (one KDoc edit); no schema
change, so no migration. No new dependency or module; nothing touches the network. There is no
"Open decision" row in `docs/TECHNICAL_DECISIONS.md` that this plan decides.

## Requirements

| Id | Requirement |
|---|---|
| R1 | Restoring a file with a zero-length rest timer, a blank workout id, a malformed date, or an unknown progression `result` or `overrideResult` (with `overrideAt` set) returns `InvalidBackup` and does not crash. |
| R2 | A rejected damaged file leaves the stored data exactly as it was. |
| R3 | The export-side dead end is not changed here; it stays recorded on issue #29. |

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Turn a damaged backup file into InvalidBackup (R1, R2, R3) | - | 1 | 1 |

<!-- CP1 -->
### CP1 — Turn a damaged backup file into InvalidBackup (R1, R2, R3)

**Files:**
`app/src/main/kotlin/com/repflow/app/data/backup/LocalBackupRepository.kt`,
`app/src/main/kotlin/com/repflow/app/data/progression/ProgressionRecommendationMapper.kt`,
`app/src/main/kotlin/com/repflow/app/domain/backup/BackupSnapshot.kt` (KDoc only),
`app/src/test/kotlin/com/repflow/app/data/progression/ProgressionRecommendationMapperTest.kt`,
`app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalBackupRepositoryDamagedFileTest.kt` (new).

Work: the design above, plus two comment edits. Add a KDoc on `parseSnapshot` saying a value the
mappers reject counts as malformed. Extend `BackupValidationError.Malformed`'s KDoc
(`BackupSnapshot.kt:94`), which lists only JSON-shape causes, to include a value the domain or a
mapper rejects.

Tests (new `LocalBackupRepositoryDamagedFileTest`, in-memory Room like
`LocalBackupRepositoryVersionCompatibilityTest`; each test starts from a valid file that
restores, so a failing parse is the file's damage and nothing else). The valid file includes a
valid `progressionRecommendations` row (`"result": "increase_load"`, no override). Each test seeds
one existing exercise first, then restores the damaged file through `RestoreBackup`:

1. Zero-length rest timer (`restTimerTotalDurationSeconds: 0` with an end time) on an
   `ACTIVE` session → `Failure(InvalidBackup(Malformed))`, no throw.
2. Blank workout id (`"id": ""` on the session, with its exercise rows pointing at it) →
   same.
3. Malformed futsal date (`"entryDate": "not-a-date"`) → same.
4. Unknown progression `result` (`"result": "INCREASE_LOAD"`, no override) → same.
5. Unknown `overrideResult` (`"overrideResult": "INCREASE_LOAD"`, `"overrideAt": 2`) with a valid
   `result` → same.
6. After each of the five, the seeded exercise is still in the database and nothing from
   the file was inserted (R2).
7. Control: the same file with the damage removed restores successfully, including its
   recommendation row, so the five failures are the damage and not the fixture.

Each of tests 1-5 must fail on the base commit (the call throws) and pass after the change;
the implementer confirms that by running them before the edit.

`ProgressionRecommendationMapperTest` gains two tests, using
`@Test(expected = IllegalArgumentException::class)` as `TrainingPlanIdTest.kt:6` does: an unknown
`result`, and an unknown `overrideResult` with `overrideAt` set. They pin the exception type
`parseSnapshot`'s catch relies on, and they are the only coverage of this fix that CI runs, because
CI doesn't run instrumented tests (`AGENTS.md:86`). No JVM test covers `parseSnapshot` itself,
because `LocalBackupRepository` needs a `RepFlowDatabase` and the project has no mocking library.

Verified with `./gradlew connectedDebugAndroidTest` limited to this class on `emulator-5554`, then
the full `connectedDebugAndroidTest` suite on `emulator-5554` (`AGENTS.md:87-88` requires the full
suite before a milestone is done, and this is the only checkpoint), then `spotlessCheck`, `detekt`,
`lintDebug` and the JVM test run.

<!-- /CP1 -->

## Self-review notes

- *Is the catch too broad?* The pair matches what the mappers throw on a bad value once `:80` is
  changed: `require` and `:80` → `IllegalArgumentException`; `LocalDate.parse` →
  `DateTimeParseException`, a `DateTimeException`. Other exception types are deliberately left to
  crash visibly. The cost is that a future mapper throwing another type on bad text closes the app
  until it is fixed; hiding every failure would be worse.
- *Hidden duplicates:* `JSONException` is not an `IllegalArgumentException` (it extends
  `Exception`), so `BackupJsonMapper.parse`'s own catch is unchanged and still first.
- *Usability:* the user sees the same "That file isn't a valid RepFlow backup." message as for a
  non-JSON file; a per-field message would need new strings and states for an event that needs a
  hand-edited file, so it is not worth the size.

## Review decisions (local plan review, round 1)

All findings were accepted; none was rejected.

- **B1:** accepted with the reviewer's recommended fix (the mapper throws
  `IllegalArgumentException`). R1, the design, the tests and the self-review are corrected.
- **O1:** the plan now quotes the message the user sees.
- **O2:** the comment edits now name their places.
- **O3:** CP1 now names the full instrumented run.
- **Added beyond the findings:** the two `ProgressionRecommendationMapperTest` tests. The catch now
  depends on that exception type and CI doesn't run the instrumented tests, so without them a
  change back to `error(...)` would pass CI and bring the crash back.
