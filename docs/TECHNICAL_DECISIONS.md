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

`BackupSnapshot.CURRENT_SCHEMA_VERSION` only needs a bump, and
`BackupJsonMapper` only needs a null-safe optional read (no
version-branching logic), for additive nullable fields whose absence
already has valid null/default semantics (Milestone 8, CP14: five such
fields landed this way). This convention is **not** sufficient on its own
for a field rename, a field removal, a field becoming required, or any
change where a missing value has no valid meaning - those need explicit
version-aware conversion in the mapper, decided case by case when they
come up.

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

## UI design system and visual identity

Resolved by milestone `repflow-redesign-visual-foundation` (plan revision 19,
`docs/milestones/repflow-redesign-visual-foundation-execution.md`). The
concrete system lives in
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/`:

- `RepFlowColor.kt` — the dark/light `ColorScheme` pair plus the extra
  per-theme tokens Material 3 has no slot for (`control`, `hairline`) and the
  named accent steps. Which `ColorScheme` roles this milestone assigns, and
  which are deliberately left at the Material 3 baseline because a stock
  component elsewhere in the app reads the same role, is recorded in
  `presentation/designsystem/ROLE_AUDIT.md` — read it before adding or
  changing a role.
- `RepFlowTypography.kt`, `RepFlowShapes.kt`, `RepFlowSpacing.kt` — the type
  scale, the radius scale (8 for controls, 12 for cards/buttons, 16 for
  sheets, plus the three sanctioned one-offs `stepper`/`fab`/`pill`), and the
  spacing scale (6/8/10/12 gaps, 14-18 card padding, 16 screen padding).
- `designsystem/icons/RepFlowIcons.kt` — the bounded local Phosphor vector
  set (no icon-library dependency added).
- `designsystem/components/` — the reusable primitives (card, buttons, tag,
  stepper, state composables).

Values are transcribed from the live Claude Design RepFlow spec, recorded in
`docs/milestones/repflow-redesign-visual-foundation-reference.md`. Tokens are
reached through `MaterialTheme.colorScheme`/`.typography`/`.shapes` wherever
Material 3 has a slot; `RepFlowColor`/`RepFlowShapes`/`RepFlowSpacing` are for
the named tokens that have none. No screen hardcodes a colour literal.

This resolves *what the design system is*, not *how far it has been applied*:
only the bottom nav, the Exercise list, and the Active Workout set-entry +
rest timer were reskinned in this milestone. Every other screen still renders
through the same cascading theme but keeps its bespoke per-screen loading /
empty / error composables until a later milestone applies the primitives
there — see that milestone's "Known limitations".

*Superseded in scope by `repflow-redesign-visual-foundation-remediation-1`*:
that milestone converted every screen in the four-destination navigation
(see "Navigation structure" below) to the Claude Design compositions on this
same system, added the structural primitives the design needs
(`designsystem/components/`), and recorded every remaining difference from
the design, with its reason, in
`docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`'s
deviation register.

## Navigation structure

Resolved by milestone `repflow-redesign-visual-foundation-remediation-1`
(plan revision 20, CP2 and CP16), on the user's explicit written
authorization at that milestone's planning gate, in favour of the Claude
Design project's information architecture. It replaces the six-tab bottom
navigation Milestone 8 shipped under the earlier "D-1 approved for M1 only"
stance.

- **Four top-level destinations**, in this order, in the bottom navigation:
  **Home** (the start destination), **Plans**, **History**, **Progress**.
- **Settings** is not a tab: it opens from Home's gear and shows no bottom
  navigation.
- **Moved inward**, each reachable from the start destination: the exercise
  library from Settings (`Exercise library`); recovery entry from Home's
  recovery card (`Log`), and recovery history from the entry screen; backup
  export, restore and CSV export as rows in Settings' `Data` group (the
  dedicated Backup screen is retired).
- **Workout mode replaces the navigation**: the workout board, focus mode
  and the done screen draw no bottom navigation; the board's `X` (leave) and
  `Finish` are the only ways out.

`docs/UX_FLOWS.md` describes the delivered flows on this structure.

## Readiness score

Resolved by the user on 2026-09-30, by adopting the Claude Design
prototype's own engine (`RepFlow.dc.html:3142–3166`), and implemented by
milestone `repflow-redesign-visual-foundation-remediation-1` (CP4) in the
pure-Kotlin `domain/recovery/ReadinessScore.kt`. Recorded here so it is a
decision rather than folklore in a Kotlin file:

- **Inputs and weights:** the six recovery-entry scales (each `0–5`) —
  sleep quality ×1.0, energy ×1.2, leg DOMS ×1.0, heavy legs ×0.8, heel
  stiffness ×0.7, pain while walking ×1.5; the last four are inverted (a
  higher value is worse). The futsal flags are not read.
- **Score:** the weighted, normalized sum scaled to `0–100` and rounded.
- **Bands:** Ready ≥ 75, Hold ≥ 58, Back off ≥ 42, otherwise Protect.
- **Pain gate:** pain while walking ≥ 3, or heel stiffness ≥ 4, is Protect
  whatever the score.
- **Date rule:** a score exists only for a day with a recovery entry for
  that calendar date; there is no carry-over from an earlier day.
- **Effect:** none on any other computation. The score is shown (Home's
  recovery card and the readiness sheet); `ProgressionPolicyV1` does not
  read it, and its own formulas and thresholds stay an open decision.

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
| Navigation structure | Resolved by milestone `repflow-redesign-visual-foundation-remediation-1` — see "Navigation structure" above |
| Backup file location and retention count | Open |
| Notification behavior when permission is denied | Open |
| Whether active workouts may span calendar days | Open |
| How substitutions affect progression history | Open |
| Whether completed workouts may be manually corrected | Open |
| Exact UI design system and visual identity | Resolved by milestone `repflow-redesign-visual-foundation` — see "UI design system and visual identity" above |
| Readiness score weights, bands, pain gate and date rule | Resolved by milestone `repflow-redesign-visual-foundation-remediation-1` (user decision, 2026-09-30) — see "Readiness score" above |

Agents must not silently finalize these decisions during unrelated tasks.
