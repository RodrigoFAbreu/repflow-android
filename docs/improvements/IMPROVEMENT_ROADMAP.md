# Improvement Roadmap (Post-MVP)

Prioritized, actionable follow-up work identified in
`POST_MVP_ENGINEERING_REVIEW.md`. Nothing in this document has been
implemented as part of this review — it is a plan for future sessions.

## 1. Critical correctness / data-integrity work

None identified. The one known integrity risk (transactional
`clearAllTables()` during backup restore) has been instrumented-tested
and confirmed safe.

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
