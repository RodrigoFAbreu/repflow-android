# Active Milestone

## Milestone

Milestone 1 — Exercise Library

## Goal

Deliver custom exercise creation, listing, search, editing, archive and restore
as the first complete vertical slice.

## Current checkpoint

**Milestone complete.** All CPs 1–10 implemented and verified; CP11 deferred
per plan; CP12 (docs + final review) done in this session.

## Checkpoint checklist

- [x] CP1 — M0 cleanup
- [x] CP2 — Dependencies + schema export config
- [x] CP3 — Domain model + pure JVM tests
- [x] CP4 — Application contracts + use cases
- [x] CP5 — Room entity, DAO, mapper, repository
- [x] CP6 — DAO + repository instrumented tests on device
- [x] CP7 — Hilt bindings
- [x] CP8 — Exercise list end to end
- [x] CP9 — Exercise editor end to end
- [x] CP10 — Archive / restore + snackbar undo
- [ ] CP11 — Optional built-in catalog (deferred to M1.1)
- [x] CP12 — Docs + final review

## Approved decisions (quick reference)

- Tracking types: `WEIGHT_AND_REPS`, `REPS_ONLY`, `DURATION`
- Name key: NFKC → trim → collapse whitespace → `lowercase(Locale.ROOT)`
- Search: normalized substring `LIKE`, bound param, accent-sensitive
- Uniqueness spans active **and** archived rows
- Archive/restore only; permanent deletion deferred
- Room schema version: 1; no destructive migration fallback
- Duplicate check on update excludes the exercise being edited (§P-1)
- Archive undo is idempotent (§P-8)
- Save unchanged draft performs no DB write (§P-9)

## Current blockers

None. Milestone 1 Definition of Done is met.

## Active plan

- Execution guide: `docs/milestones/active/milestone-1-execution.md`
- Full reference (all decisions, invariants, DoD): `docs/milestones/active/milestone-1-reference.md`

## Verification for CP3–5

```bash
./gradlew testDebugUnitTest
./gradlew spotlessCheck
./gradlew detekt
```

## Schema verification (CP5) — schema currently untracked

```bash
./gradlew kspDebugKotlin
git status --short -- app/schemas
find app/schemas -type f -name '*.json' -print
```

Confirm the expected JSON exists, inspect its content, and confirm
`app/schemas` is not git-ignored. Report whether the schema is untracked,
modified, or clean. `git diff --exit-code -- app/schemas` is insufficient
while the schema is untracked: it exits 0 without detecting the file.

Once the schema is tracked, also verify no new untracked schema appeared:

```bash
./gradlew kspDebugKotlin
git diff --exit-code -- app/schemas
git status --short -- app/schemas
```

## Verification for CP6

```bash
./gradlew connectedDebugAndroidTest
```

Device or emulator required. `assembleDebugAndroidTest` compiles but does
not execute. Do not report CP6 as passing unless this connected task
actually completes successfully.

## Last verified state

- Unit tests: `./gradlew testDebugUnitTest` — 109 tests, 0 failures
- Static checks: `./gradlew spotlessCheck detekt lintDebug` — all pass
- Schema: `app/schemas/com.repflow.app.infrastructure.database.RepFlowDatabase/1.json`
  tracked in git; `kspDebugKotlin` produced no drift
- Instrumented tests: `./gradlew connectedDebugAndroidTest` — 46 tests, 0
  failures, 0 skipped, run on physical device (SM-S928B, Android 16)
- Manual smoke test: app installed and launched on the same device; app
  starts on the Exercise list, created "BenchPress", verified it appears in
  the list, force-stopped the app and relaunched — exercise persisted. No
  crashes observed in logcat. Test app then uninstalled to reset device state.
