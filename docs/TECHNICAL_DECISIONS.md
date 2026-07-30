# RepFlow Technical Decisions

## Platform and stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Async | Coroutines + Flow |
| ViewModel | AndroidX ViewModel |
| Navigation | Navigation Compose |
| DI | Hilt |
| Persistence | Room + DataStore |
| Build | Gradle Kotlin DSL |

## Android SDK

- minSdk: 28 / compileSdk: 37 / targetSdk: 37
- Minimum SDK is lower than compile/target to retain reasonable compatibility.

## Architecture

Layered modular architecture (Domain → Application → Data/Infrastructure →
Presentation). See [ADR 0003](adr/0003-layered-modular-architecture.md).

## Persistence

Room is the local source of truth for the MVP. DataStore for lightweight
preferences only. See [ADR 0002](adr/0002-offline-first-local-database-source-of-truth.md).

## JVM alignment

Local runtime: Android Studio bundled JBR 21. CI: JDK 21.
Java source/target compatibility: 17. Kotlin JVM bytecode target: 17 (explicit).

## Offline-first

Application must not depend on network access during a workout. Backend,
accounts, and synchronization are deferred.

## Incremental modularization

Introduce Gradle module boundaries through complete vertical slices, not
upfront scaffolding. Milestone 1 keeps all code in `:app`.

## Domain purity

Domain layer is pure Kotlin. No Android, Compose, Room, Hilt, serialization,
or networking dependencies.

## Repository design

Interfaces represent application/domain capabilities. Must not expose Room
entities, cursors, or future backend DTOs.

## Historical immutability

Completed workout records are immutable from future plan or exercise edits.

## Training-plan versioning

Plan edits produce a new version. Completed workouts reference the plan
version or snapshot actually used.

## Recommendation engine

Deterministic, local, explainable, versioned. No LLM or remote AI.

## Rest timer

Stores an absolute end timestamp. In-memory countdown is UI only.

## Backup

Explicit versioned transfer schema. Room entities are never serialized
directly as the public backup format. Restore validates before replacing data.

## Future backend

Must be accessed through an API. Never embed long-lived AWS credentials or
connect directly to DynamoDB from the Android client.

## Repository tooling scripts (workflow, not product)

Repository-local workflow fingerprinting scripts (`scripts/workflow_fingerprint.py`,
`scripts/workflow_fingerprint_test.py`, `scripts/workflow_fingerprint_demo_test.py`)
use Python 3 standard library only — no third-party runtime dependency, no
new package manager or lockfile. Distinct from the product's Kotlin/Gradle
stack above; these scripts never ship in the app and are not touched by
`spotlessCheck`/`detekt`. Chosen because it introduces no new dependency
category (`AGENTS.md`'s stop-condition list), supports structured JSON and
a stdlib test runner directly, and is easier to keep deterministic than
shell parsing of Git's plumbing output. `scripts/validate_workflow_state.py`
is planned future work (WF1a, per `WORKFLOW_V2_PLAN.md`'s checkpoint
registry) and does not exist yet — not named here until it does
(`OPUS-R8-011`: a decision entry must name only files that exist). CI runs
the hermetic `workflow_fingerprint_test.py` suite on every PR
(`.github/workflows/ci.yml`'s "Workflow fingerprint conformance suite"
step); there is no unpinned dependency to install, since it is stdlib-only.
`workflow_fingerprint_demo_test.py` is deliberately **not** run in CI — it
computes plan-stage classification from a fixed historical base commit, so
wiring it into generic CI would fail on any later, unrelated product PR
once this milestone's own diff is no longer current (`GPT-R9-002`); it is
a local/explicit real-repository demonstration, run on demand.

## Dependency policy

Add libraries only when they solve a concrete requirement. Avoid Firebase,
synchronization frameworks, KMP, Health Connect, and smartwatch integrations
until explicitly prioritized.

## Open decisions

| Decision | Status |
|---|---|
| Exact initial Gradle module split | Open |
| Final Room entity schema | Open |
| Exact progression formulas and thresholds | Open |
| Navigation structure | Open (D-1 approved for M1 only) |
| Backup file location and retention count | Open |
| Notification behavior when permission is denied | Open |
| Whether active workouts may span calendar days | Open |
| How substitutions affect progression history | Open |
| Whether completed workouts may be manually corrected | Open |
| Exact UI design system and visual identity | Open |

Agents must not silently finalize these decisions during unrelated tasks.
