# Improvement Roadmap (Post-MVP)

Prioritized, actionable follow-up work identified in
`POST_MVP_ENGINEERING_REVIEW.md`. Nothing in this document has been
implemented as part of this review — it is a plan for future sessions.

## 1. Critical correctness / data-integrity work

None identified. The one known integrity risk (transactional
`clearAllTables()` during backup restore) has been instrumented-tested
and confirmed safe. *(Since `repflow-redesign-visual-foundation-remediation-1`
CP14 the restore clears the ten training tables through
`TrainingDataRepository.clearTrainingData()` instead, so the settings row
survives; `LocalBackupRepositoryAtomicityTest` was retargeted to that clear.)*

## 2. High-value maintainability improvements

### 2.1 Revisit the `ReturnCount` detekt threshold

- **Problem:** 33 separate `@Suppress("ReturnCount")` annotations across
  the codebase, the single most common suppression.
- **Evidence:** `grep -rhoE '@Suppress\("[^"]*"\)' app/src/main/kotlin`
  count, this session.
- **Expected benefit:** Removing 30+ per-function suppressions in favor of
  one tuned threshold in `config/detekt/detekt.yml` reduces noise and
  makes future *real* long-function violations easier to spot.
- **Scope:** `config/detekt/detekt.yml` (one value), plus removing the
  now-unnecessary per-function `@Suppress` annotations.
- **Dependencies:** none.
- **Risk:** low — purely a lint-config and annotation cleanup, no
  behavior change.
- **Estimated effort:** small.
- **Recommended order:** early — cheap and improves signal-to-noise for
  everything after it.
- **Acceptance criteria:** `detekt` passes with the guard-clause style
  intact and materially fewer `@Suppress("ReturnCount")` annotations
  (target: at or near zero, unless a specific function is genuinely
  excessive).
- **Validation:** `./gradlew detekt`.
- **Changes:** tooling config only, no architecture/behavior/data change.

### 2.2 Consolidate `LongParameterList` sites into parameter data classes

- **Problem:** 12 suppressions for functions/constructors with too many
  parameters.
- **Evidence:** same grep count, this session.
- **Expected benefit:** Introducing small request/params data classes
  where a function takes 5+ related primitives improves call-site
  readability and reduces the chance of accidental argument-order bugs.
- **Scope:** the ~12 flagged use cases/mappers (would need to be
  individually located via `grep -rn '@Suppress("LongParameterList"'`).
- **Dependencies:** none.
- **Risk:** low-medium — touches call sites, needs care not to break
  existing tests.
- **Estimated effort:** medium (12 sites, one at a time).
- **Recommended order:** after 2.1.
- **Acceptance criteria:** each converted call site keeps its existing
  unit tests green with no logic change, and the suppression is removed.
- **Validation:** targeted `testDebugUnitTest` per touched module, then
  full suite before commit.
- **Changes:** refactoring only, no behavior/data change; touches
  Application/Data function signatures (not domain models).

## 3. Test and reliability improvements

### 3.1 Add Compose UI instrumentation tests for History and Backup screens

- **Problem:** Milestone 7's "export → wipe → restore → data intact" DoD
  was verified via a repository-level smoke test, not real UI automation,
  because no UI-automation tooling was available in this session's
  environment.
- **Evidence:** `LocalBackupRepositoryAtomicityTest` docstring/history,
  this session.
- **Expected benefit:** closes the gap between "the repository layer
  works" and "the user can actually tap through the real flow and it
  works," including SAF (Storage Access Framework) picker interaction.
- **Scope:** new `app/src/androidTest/.../presentation/history/` and
  `.../presentation/backup/` Compose test files using
  `createAndroidComposeRule`.
- **Dependencies:** a real or emulated SAF picker interaction strategy
  (may need `Intents.intending(...)` stubbing for the
  `CreateDocument`/`OpenDocument` contracts).
- **Risk:** low — additive tests only.
- **Estimated effort:** medium.
- **Recommended order:** next concrete test-quality improvement after 2.x.
- **Acceptance criteria:** at least one instrumented test taps through
  "view history list → open a session detail" and one taps through
  "trigger export → trigger restore" with the SAF contract stubbed, both
  passing on a real emulator.
- **Validation:** `./gradlew connectedDebugAndroidTest`.
- **Changes:** test-only, no production behavior change.

### 3.2 Add a code-coverage tool (Kover)

- **Problem:** No coverage measurement exists; file-count proportion is
  the only proxy currently available for judging test thoroughness.
- **Evidence:** no coverage plugin found in `app/build.gradle.kts`, this
  session.
- **Expected benefit:** objective visibility into untested branches,
  especially in Application-layer use cases with multiple validation
  paths.
- **Scope:** `app/build.gradle.kts`, `gradle/libs.versions.toml` (new
  plugin), CI config if applicable.
- **Dependencies:** none.
- **Risk:** low.
- **Estimated effort:** small (tool setup) + ongoing (acting on findings
  is separate future work).
- **Recommended order:** can run in parallel with 3.1.
- **Acceptance criteria:** `./gradlew koverHtmlReport` (or equivalent)
  produces a report; no enforcement threshold is added yet (that would be
  separate, later work once a baseline is known).
- **Validation:** run the report generation task once and inspect output
  exists.
- **Changes:** build tooling only, no architecture/behavior/data change.

## 4. Readability and project-structure improvements

### 4.1 Adopt type-safe Compose Navigation routes

- **Problem:** `RepFlowDestinations` uses raw string route constants;
  typos are a runtime crash, not a compile error.
- **Evidence:** direct inspection of
  `presentation/navigation/RepFlowDestinations.kt`, this session.
- **Expected benefit:** compile-time safety for navigation arguments and
  destinations; the `navigation-compose` version already in
  `gradle/libs.versions.toml` supports `@Serializable` route objects.
- **Scope:** `presentation/navigation/RepFlowNavHost.kt`,
  `RepFlowDestinations.kt`, and every call site that navigates
  (`navController.navigate(...)`).
- **Dependencies:** none new (already on a compatible Navigation Compose
  version).
- **Risk:** medium — touches every navigation call site; needs careful,
  incremental migration with tests re-run after each screen.
- **Estimated effort:** medium.
- **Recommended order:** after test/reliability work in section 3, since
  UI tests from 3.1 would help catch regressions during this migration.
- **Acceptance criteria:** all navigation uses typed route objects; no
  raw string route literals remain in `presentation/navigation`; full
  unit + instrumented suites pass.
- **Validation:** `./gradlew testDebugUnitTest connectedDebugAndroidTest`.
- **Changes:** refactoring, not behavior change (user-visible navigation
  behavior stays identical); no data change.

## 5. Performance and UX polish

### 5.1 Accessibility audit pass

- **Problem:** No systematic TalkBack/content-description audit has been
  performed; only incidental fixes (e.g. `HistoryScreen`'s back button
  content description) happened as a side effect of detekt lint fixes.
- **Evidence:** absence of a dedicated accessibility test/checklist in
  the repository, this session.
- **Expected benefit:** confidence that the app is usable with
  screen readers and meets minimum touch-target sizing.
- **Scope:** all `presentation/**/*Screen.kt` composables.
- **Dependencies:** none.
- **Risk:** low.
- **Estimated effort:** medium (manual audit across ~15+ screens).
- **Recommended order:** optional polish, after correctness/test work
  above.
- **Acceptance criteria:** a documented pass/fail checklist per screen,
  with any found issues filed as follow-up items (not silently fixed
  without review, since some may be intentional design choices).
- **Validation:** manual TalkBack walkthrough; no automated command
  exists for this in the current toolchain.
- **Changes:** may touch presentation-layer composables (content
  descriptions, touch targets); no architecture/data change.

## 6. Future scalability preparation

### 6.1 Document a sync/multi-device seam (no implementation)

- **Problem:** No sync abstraction exists yet (by design), but if a
  future milestone adds backend sync, the repository interfaces are the
  natural seam and should be evaluated before that work starts, not
  during it.
- **Evidence:** repository interfaces already live in Domain,
  independent of Room, per the layered-architecture ADR.
- **Expected benefit:** a documented decision point (not code) that saves
  design time when/if sync is prioritized.
- **Scope:** a short ADR draft only if/when this becomes an active
  roadmap item — not created speculatively now, per the "no speculative
  abstractions" guardrail.
- **Dependencies:** a concrete product decision to pursue sync (currently
  none exists).
- **Risk:** none (documentation-only, and deferred).
- **Estimated effort:** small, when needed.
- **Recommended order:** deferred until a sync milestone is actually
  proposed.
- **Acceptance criteria:** N/A until triggered.
- **Validation:** N/A.
- **Changes:** none now; future ADR only.

## 7. Optional polish

### 7.1 Note the `org.json` test-only dependency rationale in
    `docs/TECHNICAL_DECISIONS.md`

- **Problem:** A future contributor may not know why a real JSON parser
  (`org.json:json:20231013`) is a `testImplementation`-only dependency
  when Android already ships `org.json` classes.
- **Evidence:** added this session to fix JVM unit tests, since
  Android's `org.json` is stubbed (throws "not mocked") outside
  Robolectric/instrumented environments.
- **Expected benefit:** avoids future confusion or accidental removal.
- **Scope:** one short paragraph in `docs/TECHNICAL_DECISIONS.md`.
- **Dependencies:** none.
- **Risk:** none.
- **Estimated effort:** small.
- **Recommended order:** anytime, low priority.
- **Acceptance criteria:** the rationale is documented.
- **Validation:** none needed (docs-only).
- **Changes:** documentation only.

## 8. Visual-foundation follow-ups (next application milestone)

Added after `repflow-redesign-visual-foundation`'s implementation review
round 2, which asked that three of that milestone's disclosed limitations
stop being standing "Known limitations" and become named, scoped follow-up
items instead. They are grouped here because they share one precondition:
a milestone that is *allowed* to change set-entry interaction and to reskin
surfaces beyond the three the visual foundation deliberately bounded itself
to. None of them is a defect in the shipped foundation.

### 8.1 Consume `RepFlowStepper` on Active Workout set entry

> **Delivered** by `repflow-redesign-visual-foundation-remediation-1` CP8:
> focus mode logs weight, reps and seconds with steppers (step from the
> exercise's `defaultLoadIncrement`, falling back to 2.5 kg) and the keypad,
> and RPE, pain and technique with scale rows. Kept for its history.

- **Problem:** the approved visual-foundation plan specifies steppers for
  load/reps (CP3 introduces `RepFlowStepper` as the primitive "used by CP6's
  weight/reps entry"; CP6's file description names "the load/reps/duration
  steppers"), and CP6 shipped `OutlinedTextField`s instead. The primitive is
  therefore authored, tested and audited but has no consumer anywhere.
- **Evidence:** `docs/ACTIVE_MILESTONE.md`'s CP6 deviation note, restated
  against the plan text that applies; review round 2 confirmed each leg of
  the justification independently and ruled the deviation accepted-as-
  disclosed rather than re-openable.
- **Why it could not land in that milestone:**
  `ActiveWorkoutScreenTest.tappingAddSetClearsTheEntryFields` does
  `performTextInput` on the load field and is a plan-listed preserved
  invariant a stepper cannot satisfy; load is decimal and unbounded, and the
  only non-invented step size (`defaultLoadIncrementGrams`) is not on
  `ActiveExerciseUi`, so consuming it means a UiState + ViewModel + use-case
  change that milestone's non-goals exclude; and it is nullable, so a
  fallback would still have to be invented.
- **Scope:** plumb `defaultLoadIncrementGrams` onto `ActiveExerciseUi`
  through `ActiveWorkoutViewModel` and its use case; decide the fallback for
  exercises that carry none; replace the load/reps fields with
  `RepFlowStepper`; rewrite the `performTextInput` assertions the change
  invalidates. Consider the RPE/pain/technique pill rows
  (`RepFlowPillPicker`) in the same pass — the design draws those as pills
  too.
- **Dependencies:** a product decision that tapping may replace typing on
  set entry. That is exactly what makes it out of scope for a reskin.
- **Risk:** medium — it changes how a set is logged mid-workout, the app's
  hottest path.
- **Estimated effort:** medium.
- **Recommended order:** first item of the next application milestone.
- **Acceptance criteria:** `RepFlowStepper` has a real consumer; the step
  size is sourced, not invented; the replaced test assertions assert the new
  affordance rather than being deleted.
- **Validation:** `connectedDebugAndroidTest` on a real device, plus a
  manual pass — this is an interaction change, not a rendering one.
- **Changes:** presentation + application layers (UiState/ViewModel/use
  case). No domain or schema change: `defaultLoadIncrementGrams` already
  exists.

### 8.2 Bring the remaining surfaces onto the foundation, starting with the second FAB

> **Delivered** by `repflow-redesign-visual-foundation-remediation-1`
> CP10–CP14: every listed surface was converted to the design's composition
> on the foundation's primitives; both FABs gave way to
> `RepFlowBottomActionBar`, and the Backup screen was folded into Settings.
> `ROLE_AUDIT.md` was re-run at that milestone's CP16. Kept for its history.

- **Problem:** the visual foundation deliberately reskins three surfaces, so
  touched and untouched surfaces now diverge. The most visible instance is
  the pair of `FloatingActionButton`s: `ExerciseListScreen`'s is CP5's solid
  `primary` fill with a white `plusBold` glyph, `TrainingPlanListScreen.kt:98`
  keeps Material 3's pale `primaryContainer` tint with a small purple glyph.
- **Evidence:** measured, not assumed, in `docs/ACTIVE_MILESTONE.md`'s
  "CP7 addendum 2" — both exercised on both devices and both themes; the
  divergence is more pronounced on light. Review round 2 accepted one tab
  apart as the right mitigation for a bounded milestone and named this as
  the follow-up.
- **Scope:** the untouched screens — Plans, Recovery, Recovery history,
  History, History detail, Backup and the two editors — reskinned through
  the existing tokens/primitives. `ActiveWorkoutScreen.kt`'s and
  `TrainingPlanListScreen.kt`'s private loading/empty/failure copies are
  removed in favour of `RepFlowStateComposables` in the same pass, which is
  the de-duplication CP3 was built for and which the visual foundation
  explicitly did not complete.
- **Dependencies:** none — the foundation is already in place.
- **Risk:** low per screen; the volume is the cost.
- **Estimated effort:** medium-large.
- **Recommended order:** after 8.1, or alongside it.
- **Acceptance criteria:** no screen still reads Material 3's baseline where
  a token exists; `ROLE_AUDIT.md` re-run and its `Consumers` column extended
  to the newly reskinned direct reads.
- **Validation:** the existing instrumented suites plus a dark/light manual
  pass on the physical device at `sw384dp`.
- **Changes:** presentation only.

### 8.3 Confirm a light-theme selected value for the pill/chip accent

- **Problem:** `repFlowSelectedPillColors` gives light a `Color.Transparent`
  fill, so a selected filter pill differs from an unselected one only by
  border (`accent600` vs `hairline`) and label (`accent700` vs `onSurface`).
  On the Exercise list that is the sole visual signal of which filter is
  active. The light selected `FilterChip` and the light accent-tinted card
  are the same undecided value.
- **Evidence:** the visual foundation's own disclosed judgment call — the
  design confirms no light value, and inventing one is precisely what the
  rest of that milestone refused to do. `Role.RadioButton` +
  `selectableGroup()` keep assistive technology correct meanwhile, so this is
  a visual-signal gap, not an accessibility-semantics one.
- **Scope:** one confirmed value from the design source, applied to
  `repFlowSelectedPillColors`, `RepFlowTagTone.Done` and
  `RepFlowCardTone.Accent`'s light treatment, with its contrast recomputed
  into `ROLE_AUDIT.md`.
- **Dependencies:** a design decision. It cannot be settled in code.
- **Risk:** low.
- **Estimated effort:** small once the value exists.
- **Recommended order:** the first thing the next design pass resolves.
- **Acceptance criteria:** light selected state is carried by more than the
  border, and the value has a measured ratio behind it like every other token
  in `RepFlowColor.kt`.
- **Validation:** `RepFlowPrimitivesTest` extended with the new floor; manual
  light-theme check on the Exercise list filter row.
- **Changes:** `presentation/designsystem/` only.

## 9. Deferred product capabilities (future milestones)

Added by `repflow-redesign-visual-foundation-remediation-1` CP16. The
Claude Design project draws each of these, and that milestone deliberately
did not build them because the domain has no concept to back them (the
user's scope decision at its planning gate: "Do not expand remediation-1
into capabilities for which RepFlow currently has no domain support …
Capture those as future product milestones instead"). Each item names its
deviation-register rows in
`docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`.
None is a defect in the shipped app. Every one needs a domain change, and
every domain change that touches persistence needs a Room migration with a
migration test and, where it adds training data, a backup-schema decision.

### 9.1 Supersets

- **Design:** `Superset A`/`B` grouping on the workout board, in focus mode
  and in the plan editor; the board row sheet's `Superset with the next
  exercise` (`D1`).
- **Scope:** a grouping concept on planned exercises (and its snapshot on a
  workout's exercises); board, focus and editor rendering; rest between
  grouped exercises.
- **Preconditions:** a product decision on how rest and set order work
  inside a group; a schema change (new column or table) under an explicit
  migration; backup schema version bump.

### 9.2 Exercise swap / substitute (and skip)

- **Design:** the swap sheet with same-muscle-group alternatives, and the
  board row sheet's `Swap for another exercise`, `Skip for today`, `Do this
  later` and `Remove from this workout` (`D2`, `D13`, `D14`, `D15`, `D20`);
  `docs/UX_FLOWS.md`'s "Skip or substitute the exercise".
- **Scope:** a status on a workout's exercise (skipped), a substitution
  record, a reorder and a removal operation, each with a use case.
- **Preconditions:** the open decision **"How substitutions affect
  progression history"** (`docs/TECHNICAL_DECISIONS.md`) must be settled
  first; same-muscle-group alternatives also need 9.6's taxonomy.

### 9.3 Per-exercise and per-session notes

- **Design:** the note sheet, note chips on board rows, the session note on
  the finish sheet and in workout detail (`D3`); `docs/UX_FLOWS.md`'s
  "Optional workout notes" on completion.
- **Scope:** a nullable note on a workout's exercise and on a session;
  entry on the board/finish sheet; display in History.
- **Preconditions:** additive nullable columns under an explicit migration;
  backup schema bump with a null-safe optional read (the convention in
  `TECHNICAL_DECISIONS.md` "Backup").

### 9.4 An active plan and multi-day plans

- **Design:** the `Active plan` badge, `Day 1..4` tabs, "day 2 of 4",
  "Week 3", `Make active`, `Start day 2`, and Home's "This week — 2 of 4
  sessions" (`D4`, `D9`, `D45`, `D77`).
- **Scope:** a day dimension inside a plan version, one active plan, a
  weekly target; Home's start card and the start sheet offering the next
  day.
- **Preconditions:** a product decision on how days interact with plan
  versioning (a new version per day edit, or per plan); schema change and
  backup schema bump; Home's recommended-workout rule rewritten around it.

### 9.5 kg / lb unit switching

- **Design:** Settings' `Units` group (`D5`).
- **Scope:** a display-unit preference (an additive column on the CP14
  `settings` row, persisted by stable string), conversion at every load
  display and entry point, and a lb load step.
- **Preconditions:** a decision that storage stays in kilograms (only
  display converts); every load-formatting site found and routed through one
  formatter first.

### 9.6 Other design surfaces with no domain backing

Recorded together because each is smaller or blocked on a decision:

- **Deload planning** — the "Plan a deload week" card and the deload state
  (`D6`). Needs a programme-level concept.
- **Editing a completed workout's sets** — `3b`'s set pencil (`D7`).
  Blocked by the open decision **"Whether completed workouts may be
  manually corrected"**.
- **Muscle-group taxonomy** — the library's group filter and
  `Chest · Weight & reps` meta (`D8`). Needs a field on `Exercise`.
- **First-run onboarding** — drawn nowhere, listed by the design as a "try
  next".
- **Ad-hoc workout naming** (`D30`) and **backup history / safety snapshot /
  restore undo** (`D34`, `D104`).

### 9.7 `docs/UX_FLOWS.md` rows kept as declared intent

`docs/UX_FLOWS.md` keeps every requirement the app does not yet meet,
marked *declared intent* and pointing here. The ones not already covered
by 9.1–9.6 or 9.8:

- **Pre-start preview** — planned exercises, expected duration, recovery
  warning, recent futsal context and suggested adjustments before starting
  (`D12`, `D44`). "Suggested adjustments" is the per-exercise readiness
  decision list (`D27`–`D29`), which needs a rule that acts on the
  readiness band; none exists.
- **Suggested load as a figure** — the recommendation names an outcome
  (increase / maintain / reduce), not a load (`D49`); a figure would be a new
  `ProgressionPolicyV1` output.
- **Recovery or pain warnings on the completion screen** — no warning rule
  exists in the domain or application layers.

### 9.8 Next remediation child: `5b`, `5c` / `5d`, and set-entry carry-over

Decided by the user on 2026-10-01 during
`repflow-redesign-visual-foundation-remediation-1`: its CP14 and CP15 built
the plan's `4a` Settings and Progress, and the newer design turns go to a
later remediation child.

- **`5c` / `5d` Settings** (`O11`): `Theme` System / Light / Dark, an
  app-level `Default rest`, `Extra set fields` (always / collapsed / off),
  `Archived exercises and plans`, and `Backup and restore` as its own screen
  with backup metadata. Each preference is an additive column on the
  `settings` row (a `MIGRATION_8_9`).
- **`5b` Progress** (`O12`): exercise picker, line chart with selectable
  points, `3m` / `6m` / `All` range, `Sessions` / `Avg RPE` tiles, `Training
  frequency`, `Records`.
- **Set-entry carry-over** (`D60`): weight and reps staying in place after
  `Log set` and starting from the last session's values, with "Last time:
  82.5 kg × 8" before the first set — which also delivers
  `docs/UX_FLOWS.md`'s "Previous performance" and "Reuse the previous set".
  Precondition: revisit the Milestone 8 rule that recording a set clears
  the entry (`tappingAddSetClearsTheEntryFields`), and add a read model for
  the previous session's sets.

---

## Prioritization summary (recommended execution order)

1. 2.1 — Revisit `ReturnCount` threshold (small, cheap, improves signal).
2. 3.2 — Add coverage tooling (small, gives visibility before further
   test investment).
3. 3.1 — Compose UI tests for History/Backup (medium, closes a real
   verification gap).
4. 2.2 — Consolidate `LongParameterList` sites (medium, benefits from
   test safety net already in place from 3.1/3.2).
5. 4.1 — Type-safe navigation (medium, benefits from UI test coverage
   added in 3.1 as a regression safety net).
6. 5.1 — Accessibility audit (medium, optional but valuable).
7. 7.1 — Document the `org.json` test dependency (trivial, anytime).
8. 6.1 — Sync seam ADR (deferred, no current trigger).

This order favors low-risk, high-signal cleanups first, builds a test
safety net before touching call-site-heavy refactors, and defers
speculative or currently-untriggered work (sync) to the end.

Section 8 sits outside that order: 8.1 and 8.2 are the next *application*
milestone's own scope rather than standalone cleanups, and 8.3 is gated on a
design decision. Within section 8 the order is 8.3 (unblocks nothing but is
cheapest once the value exists), then 8.1, then 8.2.

Section 9 is product scope, not cleanup: each item is its own future
milestone, ordered by the product owner. 9.8 is the one already decided to
come next, as a remediation child of the redesign.
