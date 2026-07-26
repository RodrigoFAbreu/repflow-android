# Active Milestone

## Milestone

Milestone 1 — Exercise Library

## Goal

Deliver custom exercise creation, listing, search, editing, archive and restore
as the first complete vertical slice.

## Current checkpoint

**Verify CPs 3–5 unit tests, then CP6 instrumented tests on device.**

Implementation code for CPs 3–10 exists as untracked files.
Last confirmed build: `./gradlew assembleDebug`. No unit or instrumented tests
have been verified yet. CP6 requires a physical device or emulator.

## Checkpoint checklist

- [x] CP1 — M0 cleanup
- [x] CP2 — Dependencies + schema export config
- [ ] CP3 — Domain model + pure JVM tests (code present)
- [ ] CP4 — Application contracts + use cases (code present)
- [ ] CP5 — Room entity, DAO, mapper, repository (code present)
- [ ] CP6 — DAO + repository instrumented tests on device ← **next verification**
- [ ] CP7 — Hilt bindings (code present)
- [ ] CP8 — Exercise list end to end (code present)
- [ ] CP9 — Exercise editor end to end (code present)
- [ ] CP10 — Archive / restore + snackbar undo (code present)
- [ ] CP11 — Optional built-in catalog (deferred)
- [ ] CP12 — Docs + final review

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

None. CP6 requires a device or emulator.

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

- Last passing command: `./gradlew assembleDebug`
- Unit tests: not yet verified
- Instrumented tests: not yet executed
- Schema: `app/schemas/.../1.json` present (untracked)
