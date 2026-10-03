# repflow-redesign-visual-foundation — Execution Plan (Revision 19)

Restarted under Workflow v2.3.1 tooling, 2026-08-27.

Work item: `repflow-redesign-visual-foundation` · `work_item_type: product` ·
`governing_workflow_version: 2.1` · base commit `b39af90c98fda382bd4923aa919058d7343e57a5`
(Milestone 8's completion/merge-into-`main` commit — the previous
milestone's completion commit, per `/milestone-plan`'s no-argument base
resolution convention; confirmed an ancestor of the commit this revision
was declared against).

This is the first milestone in the Claude-Design-informed RepFlow redesign
(see the current-vs-target gap analysis performed earlier in the original
conversation, recommendation R0). Deeper rationale, confirmed design-token
values, and source citations live in the companion reference doc,
`docs/milestones/repflow-redesign-visual-foundation-reference.md`.

## Provenance note (revision 2)

Revision 1 of this plan was produced under a since-discarded Workflow
v2.3.0 planning cycle: its registry, mapping, artifacts declaration,
approvals, and review state were intentionally discarded and are not
reused. This revision 2 independently re-verifies every claim revision 1
made — against (1) the current repository (confirmed untouched under
`app/src/main/kotlin` since Milestone 8 closed: no `presentation/
designsystem/` package exists, `RepFlowTheme.kt` is still the original
placeholder, no font/icon resources exist yet), (2) revision 1's own text,
and (3) a fresh, full read of the live Claude Design project. The
checkpoint structure, non-goals, and overall shape below are **unchanged**
from revision 1 — they held up. The token *values* CP1/CP2/CP3 will author
from are **corrected** in several concrete places; see the reference doc's
"Deltas from the original (discarded-cycle) read" for the full list. The
checkpoint text below already reflects the corrected values, not revision
1's.

**Convention for in-body "this round" markers.** Inside a checkpoint body
or "Known limitations", a parenthetical such as "corrected this round
(B2)" or "new this round (I2)" names the round in which that specific
marker was introduced, not the plan's current revision — it is not updated
as later rounds land. The authoritative per-round record, including which
revision each marker corresponds to, is the corresponding "Round N
plan-review decisions" section below; consult that section rather than
inferring recency from an in-body marker's wording alone.

## Round 1 plan-review decisions (revision 3)

`LOCAL_MODEL_PLAN_REVIEW` round 1 returned `REVISE` (1 Blocking, 5
Important, 3 Optional). Every finding was independently re-verified
against the repository before being applied — dispositions below, with the
evidence. Checkpoint/registry/mapping text throughout this document already
reflects these decisions; this section exists so the next reviewer does not
have to re-derive what changed or why.

- **B1 (blocking) — accepted, resolved by scope reduction, not by adding a
  shared top-bar primitive.** Confirmed `RepFlowNavHost.kt:47-61`'s
  `Scaffold` has no `topBar` — every one of the app's ten screens owns its
  own `topBar = ` block (`grep -rn "topBar = " app/src/main/kotlin/` — ten
  files, top-level destinations included, not just nested ones), and no
  `RepFlowTopAppBar`/`RepFlowTopBar` composable exists anywhere. CP4 drops
  "top app bars" from its name and scope; see CP4 below and REQ-4 in the
  mapping. A shared top-bar primitive is real, defensible work, but it is a
  structural refactor across ten files, not a complexity-1 checkpoint
  touching two navigation files — out of bounded scope for this milestone.
- **I1 — accepted for the required rename; the finding's own secondary
  claim about duplication is rejected.** `ErrorState` does not exist;
  confirmed via `grep -rn "ErrorState" app/src/` (zero hits). The real
  composable is `private fun FailureState(onRetry: () -> Unit)`. The
  review claimed this composable "exists exactly once in the codebase, so
  it is not 'currently-duplicated' at all" — that claim is incorrect:
  `FailureState(onRetry: () -> Unit)` is defined identically (same
  signature and structure, only the string resource differs) in three
  files — `ExerciseListScreen.kt:312`, `ActiveWorkoutScreen.kt:312`, and
  `TrainingPlanListScreen.kt:241` — confirmed by reading all three. CP3's
  original "replacing the currently-duplicated per-screen versions" framing
  was accurate and is kept unchanged; only the composable's real name
  (`FailureState`) and its `onRetry` parameter are corrected in CP3/CP5/
  REQ-3 below.
- **I2 — accepted.** `RepFlowShapes.kt` now carries named tokens for the
  9dp stepper radius, the 18dp FAB radius, and the 999dp/`CircleShape`
  pill radius, alongside the 8/12/16 small/medium/large set — see CP1 step
  4, CP3, and CP5 below.
- **I3 — accepted.** Both `error` OKLCH values are resolved to concrete
  sRGB hex below (CP1 step 2) and in the reference doc's Color section:
  dark `oklch(0.72 0.13 25)` → `#eb827b`, light `oklch(0.55 0.13 25)` →
  `#b14e49`. Computed via the standard OKLCH→linear-sRGB matrices (Björn
  Ottosson's OKLab transform) plus sRGB gamma companding, and
  cross-checked against a value this document already asserts as ground
  truth: the same pipeline resolves the documented accent
  `oklch(0.660 0.125 289.2)` to `#9184d9` exactly, matching this document's
  own stated accent hex — confirming the conversion before trusting it for
  the two new values.
- **I4 — accepted.** `RepFlowColor.control` gets an explicit light-theme
  value, `#cfd3e5` (neutral-300) — see CP1 step 2 below. This is the same
  step already used for light's `outline`; reused here as a fill rather
  than a border because the light-theme derivation, like dark's, only
  defines background/surface/outline/text from the ramp and has no other
  named step to draw a distinct "raised control" fill from. Kept distinct
  from `background` (neutral-200) so stepper/chip fills remain visually
  distinguishable from the page background, matching the dark theme's
  control tier being distinguishable from its background/surface.
- **I5 — accepted.** Both protected documents now state four affected
  destinations (Exercises, Workout, Recovery, Backup) consistently — see
  the reference doc's "Icons" section and delta #7.
- **O1 — accepted.** CP3's rationale sentence gets one clause noting the
  de-duplication does not fully complete within this milestone (see CP3
  below); `LoadingIndicator` and the newly-corrected `FailureState` both
  remain duplicated in `TrainingPlanListScreen.kt` afterwards (and
  `ActiveWorkoutScreen.kt`'s `FailureState`, since CP6's scope is the
  set-entry/rest-timer surface specifically, not the top-level
  `ObservationFailed` branch).
- **O2 — accepted.** `repflow-redesign-visual-foundation-artifacts.json`'s
  `plan_stage.excluded_paths` rationale strings are verbatim
  `workflow-v2-1-core` template text (confirmed: every rationale describes
  a process work item — "WF4a-ii's gate-count depointer", "this
  milestone's own tooling scripts", etc.) — functionally correct
  classification for a product work item, prose not re-authored. Noted
  here so a future reader does not mistake the prose for this item's own
  reasoning; no change to the JSON file itself is needed.
- **O3 — no plan change.** Both of this document's own "Unresolved
  questions for the reviewer" (reference-doc protection, `base_commit`
  choice) were independently re-verified as correct this round; see
  `REVIEW_REQUEST.md`'s "Unresolved questions" section for the recorded
  verification, forwarded for the external reviewer's own sign-off rather
  than re-litigated here.
- **Missing tests, item 2 — accepted.** CP4 now states the replacement nav
  icon renders with `contentDescription = null`, per
  `RepFlowBottomNavigationBar.kt`'s current structure (confirmed: the
  accessible name is already set once, on the `NavigationBarItem`'s own
  `Modifier.semantics` block, not on the icon).
- **Missing tests, items 1 and 3** — covered by I3 and I2 above
  respectively (CP1 step 6 now asserts concrete hex, not a self-referential
  conversion, and asserts the new radius tokens alongside the colors).

## Round 2 plan-review decisions (revision 4)

`LOCAL_MODEL_PLAN_REVIEW` round 2 returned `REVISE` (0 Blocking, 5
Important, 2 Optional). Every finding was independently re-verified against
the repository (and, for I2, the live Claude Design project directly)
before being applied — dispositions below, with the evidence.

- **I1 — accepted.** Confirmed directly against this machine's
  `material3-android-1.4.0-sources.jar`: `ColorScheme.kt`'s
  `darkColorScheme()` defaults `outline` to `ColorDarkTokens.Outline` =
  `PaletteTokens.NeutralVariant60` = `Color(147, 143, 153)` = `#938F99`, and
  `onPrimary` to `ColorDarkTokens.OnPrimary` = `PaletteTokens.Primary20` =
  `Color(56, 30, 114)` = `#381E72` — both exactly as the review stated.
  CP1 step 2 now assigns dark `outline` = `#3f424d` and `onPrimary` =
  `#161826` on both themes; CP1 step 6 now asserts all seven roles
  (`primary`, `onPrimary`, `background`, `surface`, `outline`, `error`,
  plus light's `RepFlowColor.control`). No other `ColorScheme` role is
  assigned, per the review's own alternative option — CP1 step 2 now says
  so explicitly.
- **I2 — accepted, with a live-design re-check.** Confirmed: round 1's I4
  fix set light `RepFlowColor.control` = `#cfd3e5`, identical to light
  `outline`. Re-read the live Claude Design project directly rather than
  guessing a replacement: `6b`'s component-spec panel is dark-theme-only
  throughout (its own header and every swatch, including `control` and the
  stepper primitive, use dark tokens only), and `1d` — the only screen
  actually rendered in light theme — has no stepper, chip, or scale-row
  element to read a light-theme `control` value from. No light-theme
  `control` value is design-confirmed either way, so this is a judgment
  call rather than a design-derived correction. CP1 step 2 now sets light
  `control` = neutral-400 `#b2b6ca` (the next ramp step past `outline`'s
  300; neutral-100 is already `surface`'s own value), flagged in CP1, the
  reference doc, and "Known limitations"/"Areas the reviewer should
  specifically challenge" the same way CP2's icon picks are. CP1 step 6
  now also asserts light `control` differs from light `outline`.
- **I3 — accepted.** Confirmed:
  `classify_path_implementation_stage("docs/TECHNICAL_DECISIONS.md", ...)`
  against the artifacts declaration as it stood at round 2 returned
  `excluded`. Moved to `implementation_stage.protected_paths` with a
  rationale naming REQ-9 specifically (the inherited `workflow-v2-1-core`
  rationale — "another work item's own... content" — was true for that
  process work item and false for this product item's own REQ-9
  dependency); re-verified the reclassification now returns `protected`.
- **I4 — accepted.** Confirmed: `repflow-redesign-visual-foundation-mapping.json`'s
  REQ-3 still read "consolidated loading/empty/error states" with no
  mention of `FailureState`/`onRetry`, though CP3/CP5 were both correctly
  updated at revision 3. REQ-3's description now names
  `FailureState(onRetry)` explicitly, matching CP3/CP5 and round 1's own
  acceptance-criterion 2.
- **I5 — accepted.** Confirmed: the reference doc's "Design-vs-repo
  screen-file correspondence" section still read "bottom nav/top bars,
  Exercise list..." — the only surviving pre-B1 occurrence (grepped both
  documents for every `top bar`/`topBar`/`top app bar` occurrence; the
  other thirteen were already consistent). "top bars" dropped from that
  sentence.
- **O1 — accepted.** Confirmed: no `androidx.compose` import exists
  anywhere under `app/src/test` (80 files, all domain/data/ViewModel), and
  `app/build.gradle.kts` has no `testOptions`/`unitTests.isReturnDefaultValues`
  block. CP1 gets a new step 0, ahead of step 1 (existing steps 1-6
  unchanged), spiking a throwaway JVM-Compose-value-class assertion before
  the token files are authored, so a Robolectric/instrumentation
  requirement surfaces as an early CP1 finding rather than a step-6
  surprise.
- **O2 — accepted.** Confirmed: delta #1 read "integer-px gaps
  6/8/10/12/14-18/16", an incoherent list; the "Spacing and radius" section
  it summarizes has it right (gaps 6/8/10/12, card padding 14–18, screen
  padding 16). Delta #1 corrected to state the three separately.
- **Missing tests items — both accepted**, applied as part of I1 (newly-
  assigned roles join CP1 step 6's assertions) and I2 (light
  `control`/`outline` distinctness asserted) above.

## Round 3 plan-review decisions (revision 5)

`LOCAL_MODEL_PLAN_REVIEW` round 3 returned `REVISE` (1 Blocking, 3
Important, 2 Optional). Every finding was independently re-verified against
the repository, the local `material3-android-1.4.0-sources.jar`, and the
live Claude Design project directly before being applied — dispositions
below, with the evidence.

- **B1 (blocking) — accepted.** Confirmed `RepFlowBottomNavigationBar.kt`
  is a bare `NavigationBar { NavigationBarItem(...) }` with no `colors =`
  argument (unchanged by CP4's declared file edits), and confirmed against
  this machine's material3 1.4.0 sources that `NavigationBarItemDefaults
  .colors()` (`NavigationBar.kt:383-397`) and `NavigationBar`'s own
  container read six roles CP1 did not assign
  (`NavigationBarTokens.kt:24-34`: `secondary`, `secondaryContainer`,
  `onSecondaryContainer`, `onSurfaceVariant` ×2, `surfaceContainer`) —
  exactly as the finding stated, including the exact dark-baseline hex
  values it quoted (re-derived independently from `PaletteTokens.kt`:
  `Secondary90` = `#E8DEF8`, `Secondary30` = `#4A4458`, `Secondary80` =
  `#CCC2DC`, `NeutralVariant80` = `#CAC4D0`, `Neutral12` = `#211F26` — all
  matched). Resolved by assigning all five roles CP4's `NavigationBarItem`
  actually reads (dropping the sixth, `onSurfaceVariant`'s label-side
  duplicate is the same role as its icon-side use), with values re-read
  directly off the live design's `1a` (dark) and `1d` (light) bottom-nav
  renders rather than invented — see CP1 step 2 below for the exact values
  and citations. This keeps CP4's own claim ("no direct per-item color
  overrides needed") **true**, rather than replacing it with per-item
  overrides — the finding's own required text allows either path; assigning
  the roles keeps one mechanism (the cascade) for every stock-component
  consumer of these roles, not just `NavigationBarItem`, and every value
  used is now pixel-confirmed rather than a plan-text quote, closing this
  finding's own root-cause diagnosis ("authored against a list of values
  the plan text happens to quote rather than against what the code will
  actually read") rather than reproducing it one role further out. `Card`'s
  `surfaceContainerHighest` and `Snackbar`'s three `inverse*` roles — the
  finding's "not confined to CP4" tail — are deliberately **not** assigned
  globally (**dark `surfaceContainerHighest` corrected round 15's B1: it
  *is* read, by the app's one bare `Card(` — see that round's disposition
  below for the reassigned value; light `surfaceContainerHighest` and both
  themes' `inverse*` remain unassigned, unaffected by that fix**); see
  CP3's `RepFlowCard`/`RepFlowTag` note and CP5's Known
  Limitations entry for why neither is actually read by any primitive this
  milestone authors.
- **I1 — accepted.** Read directly from `1d`: both of its primary-button
  renders ("Start workout", "Create a plan") use
  `background:#5d5294;color:#f5f4ff`, confirming the finding's claim
  exactly. CP1 step 2's light `onPrimary` corrected from `#161826` to
  `#f5f4ff`; CP1 step 6's assertion corrected to match; the reference doc's
  Color section now states it beside light `primary`.
- **I2 — accepted.** Confirmed: CP1 step 2 assigned no token for any of the
  accent-ramp steps CP3 quotes (`#d2cefd`/`#796cbf`/`#5d5294`/`#2b2741`), and
  its own closing access-rule paragraph explicitly excluded anything beyond
  the named handful — re-confirmed `grep -rn "Color(" .../presentation/`
  still returns zero, so the finding's "only remaining option is a
  hardcoded literal" claim held. CP1 step 2 now names
  `RepFlowColor.accent300/600/700/900` explicitly, and the closing
  access-rule paragraph is extended to cover them.
- **I3 — accepted.** Read directly from `1d`: the accent-outline button
  (three instances — "Log recovery — 20 seconds", "Retry", "Empty
  workout") uses `box-shadow:inset 0 0 0 1px #796cbf;color:#5d5294`
  (accent-600 border / accent-700 text) throughout — confirmed distinct
  from CP3's existing dark-only spec (accent-700 border / accent-300 text,
  sourced from `6b`), exactly as the finding stated, including its
  contrast-ratio math (dark spec transplanted to light: `#d2cefd` on
  `#f3f5fe` = 1.38:1; light's own values: text 6.23:1, border 4.10:1). CP3
  below now states the accent-outline spec per theme. Scale-row-selected
  and status-chip have no light-theme render in `1d` to confirm a value
  from (same situation as light `control`, round 2's I2) — both are now
  flagged explicitly as judgment calls rather than left silently
  single-valued, reusing the confirmed light accent-outline pair
  (accent-600/accent-700) by analogy, the same disclosure pattern already
  used for light `control`.
- **O1 — accepted.** Confirmed: step 2 assigns `onBackground`/`onSurface`
  (both themes) and dark `RepFlowColor.control`, and step 6's assertion
  list omitted all three. Added to CP1 step 6 below, alongside B1's five
  new roles per B1's own "Missing tests" note.
- **O2 — accepted.** Confirmed: `ColorScheme` is `@Immutable class
  ColorScheme(val primary: Color, val onPrimary: Color, ...)` at
  `ColorScheme.kt:147-148` — plain `val` properties, not snapshot-state, so
  a plain JVM read is safe, as the finding itself noted. CP1 step 0 below
  now spikes constructing a `darkColorScheme()`/`lightColorScheme()` and
  reading a role off it, the actual shape step 6 needs, rather than a bare
  `Color`/`Dp` value class.

## Round 4 plan-review decisions (revision 6)

`LOCAL_MODEL_PLAN_REVIEW` round 4 returned `REVISE` (0 Blocking, 1
Important, 0 Optional). The finding was independently re-verified against
the repository before being applied — disposition below, with the evidence.

- **I1 — accepted.** Confirmed CP1 step 2's round-3 B1 fix assigns
  `secondaryContainer`/`onSecondaryContainer` non-baseline values for both
  themes (dark: `primary` at 20% opacity / `RepFlowColor.accent300`; light:
  `primary` at 16% opacity / `primary`) — so CP3's "Cards" bullet claim that
  `FilterChipTokens`'s `secondaryContainer` default "is never read either"
  is no longer true, exactly as the finding stated. Confirmed
  `FilterChipTokens.FlatSelectedContainerColor = SecondaryContainer` and
  `.SelectedLabelTextColor = OnSecondaryContainer` against this machine's
  material3 1.4.0 sources, then confirmed all three cited stock `FilterChip`
  usages exist with no `colors =` argument: `HistoryScreen.kt:245`,
  `TrainingPlanListScreen.kt:108,114`, `ExerciseEditorFormFields.kt:116` —
  none in any checkpoint's "Files modified" list. Resolved by correcting
  CP3's "Cards" bullet to state the role is now assigned and read by these
  three files' stock chips, adding a "Known limitations" bullet parallel to
  the existing `TopAppBar`/`onSurfaceVariant` one, extending the
  global-vs-scoped "Areas the reviewer should specifically challenge" item
  to name both cascade categories, and adding these three screens to CP7's
  manual dark/light verification pass alongside the bottom nav — the same
  disposition shape as the parallel `TopAppBar` cascade this plan already
  disclosed, since the finding found no evidence of an actual rendering
  regression, only an undisclosed, plan-inconsistent side effect.

## Round 5 plan-review decisions (revision 7)

`LOCAL_MODEL_PLAN_REVIEW` round 5 returned `REVISE` (3 Blocking, 2
Important, 0 Optional). Every finding was independently re-verified
against the repository and this machine's
`material3-android-1.4.0-sources.jar` before being applied — dispositions
below, with the evidence. Contrast ratios were independently recomputed
with a standard sRGB-relative-luminance pipeline that reproduces every
ratio this plan already asserts (2.60, 6.22, 6.23, 4.10, 1.38) to the
digit, so the same pipeline is what produces every new figure below.

- **B1 (blocking) — accepted, resolved by scoping the nav's opacity
  treatment to CP4 rather than keeping `onSurfaceVariant` a global
  `ColorScheme` role.** Confirmed against this machine's material3 1.4.0
  sources: `onSurfaceVariant` is
  `OutlinedTextFieldTokens.LabelColor`/`.InputPlaceholderColor`/
  `.SupportingColor`, `ListTokens.ListItemSupportingTextColor`/
  `.ListItemOverlineColor`, `DialogTokens.SupportingTextColor`,
  `OutlinedButtonTokens.LabelTextColor`, and `AppBarTokens.TrailingIconColor`
  (**corrected round 15's B2: `TextButtonTokens.LabelColor` removed —
  confirmed grepping every `TextButtonTokens.` usage across all 249
  material3 sources outside the token file itself returns only the
  *disabled*-state fields, `Button.kt:787-788`; the enabled label reads
  `fromToken(ColorSchemeKeyTokens.Primary)` at `Button.kt:784` instead, with
  the library's own `// TODO replace with the token value once it's
  corrected` comment one line above — so `TextButtonTokens.LabelColor` is
  dead code in 1.4.0 and `TextButton`'s 34 sites are a `primary` consumer,
  not an `onSurfaceVariant` one; `AppBarTokens.TrailingIconColor` added, a
  consumer round 16's B1 removes again, below**) — confirmed
  against the actual call sites: 23 `OutlinedTextField`s, `ListItem`s with
  `supportingContent` in five files (CP5's own included — **corrected round
  15's O1, was miscounted as six files**), 4 `AlertDialog`s, and 5
  `OutlinedButton`s — 38 sites total (**corrected round 16's B1: the "10
  `TopAppBar` trailing icons" term removed — `AppBarTokens.TrailingIconColor`
  reaches only a bar's `actions` slot, and of the ten stock `topBar =
  TopAppBar(...)` sites app-wide exactly one passes `actions =`
  (`RecoveryFutsalScreen.kt:93`), wrapping a `TextButton`, itself a
  `primary` consumer per this same B2 above — so the role has zero live
  `TopAppBar` consumers**), none passing `colors =`. Independently recomputed the
  composited light-theme values: `#292b31` at 55% gives `#84868d`
  (3.34:1 on `surface`), `#7d8089` (3.20:1 on `background`), `#817f87`
  (3.22:1 on `AlertDialog`'s `surfaceContainerHigh`) — reproducing the
  finding's figures exactly, against a Material 3 baseline
  (`NeutralVariant30 #49454F`) that independently recomputes to
  8.59:1/7.59:1/7.63:1 on the same three surfaces. This is the same
  failure CP1 step 2's own light `onPrimary` rationale rejects one
  paragraph earlier — but reached through a role read by far more of the
  app's default text than `onPrimary` ever was. Resolved by **removing
  `onSurfaceVariant` from CP1 step 2's global `ColorScheme` assignment**
  (it reverts to Material 3's own baseline — **`#CAC4D0` dark / `#49454F`
  light** (`NeutralVariant80`/`NeutralVariant30` — **corrected this round,
  round 6's B2: this sentence had misstated `outline`'s own `#938F99`/
  `#79747E` baseline here instead**, six lines after correctly citing
  `NeutralVariant30 #49454F` above) — independently confirmed to clear
  both the 4.5:1 text floor and the 3:1 component floor against every
  surface/background pair in both themes: light `#49454F` gives
  8.59:1/7.59:1 on surface/background (as already stated above) and 8.59:1
  on the light dialog `surfaceContainerHigh` (= `surface`, per I2 below);
  dark `#CAC4D0` gives 8.91:1/10.33:1 on surface/background and 8.30:1 on
  the dark dialog `surfaceContainerHigh` (= `control`, per I2 below)) and
  giving CP4's `NavigationBarItem` its own scoped
  `NavigationBarItemDefaults.colors(unselectedIconColor = …,
  unselectedTextColor = …)` override carrying the design's literal
  60%/55%-opacity unselected-item treatment directly — which was always a
  fact about the bottom nav's own render, not a general text-color fact
  about the app. The other four roles round 3's B1 assigned (`secondary`,
  `secondaryContainer`, `onSecondaryContainer`, `surfaceContainer`) are
  unaffected: none has this role's app-wide fan-out, and I1/I2 below
  address their own disclosed side effects on the same footing as before.
  See CP1 step 2, CP4, and "Known limitations" below for the corrected
  text.
- **B2 (blocking) — accepted, resolved by giving CP3's decorative hairline
  its own token rather than keeping it on the `outline` role.** `outline`
  was assigned in revision 4 (round 2's I1) specifically so CP3's card/
  stepper/scale-row/neutral-outline-button borders would read
  `#3f424d`/`#cfd3e5` instead of Material 3's baseline — but `outline` is
  also `OutlinedTextFieldTokens.OutlineColor` (all 23 fields' resting
  border, `TextFieldDefaults.kt:1393`) and
  `SwitchTokens.UnselectedHandleColor`/`.UnselectedFocusTrackOutlineColor`
  (both `Switch`es — **corrected round 17's O1: the resting border is
  wired from `.UnselectedFocusTrackOutlineColor`, not
  `.UnselectedTrackOutlineColor`; both are `Outline`, so no value
  changes**). Independently recomputed: light `#cfd3e5` gives
  1.37:1 on `surface`, 1.21:1 on `background` (baseline `#79747E`
  independently recomputes to 4.19:1/3.70:1); dark `#3f424d` gives 1.52:1
  on `surface`, 1.76:1 on `background` (baseline `#938F99` independently
  recomputes to 4.80:1/5.56:1) — reproducing the finding's figures
  exactly. Unlike B1, most of `outline`'s 23 `OutlinedTextField` consumers
  sit in files this milestone never touches or verifies (only two, once
  B3 below is applied, are even reviewed in CP7's manual pass), so keeping
  a global near-invisible resting border is a worse regression than B1's
  — introduced silently into screens this milestone's own verification
  never looks at. Resolved by **removing `outline` from CP1 step 2's
  assignment** (reverts to Material 3 baseline, confirmed above to clear
  3:1 everywhere) and adding a new **`RepFlowColor.hairline`** token (dark
  `#3f424d` / light `#cfd3e5` — the same two values, just no longer routed
  through the role every stock text field and switch also reads) for
  CP3's four hairline-consuming primitives to read directly, the same
  pattern already used for `.control`/`.accent*`. See CP1 step 2 and CP3's
  "Cards" bullet below.
- **B3 (blocking) — accepted.** Confirmed: `ActiveWorkoutScreen.kt`'s own
  composables are `ActiveWorkoutScreen`/`LoadingIndicator`/
  `NoActiveSessionState`/`ActiveSessionState`/`RestTimerBar`/
  `AddExercisePicker`/`RecommendationRow`/`FailureState` — no set-entry pad
  among them. Set entry (RPE/warm-up/pain/technique fields, the load/reps/
  duration steppers, the undo/edit actions) is `ActiveWorkoutExerciseCard
  .kt`'s `internal fun ExerciseCard` (:35), invoked from
  `ActiveWorkoutScreen.kt:179` — confirmed by reading both files in full,
  including all seven `OutlinedTextField` call sites across six field kinds
  (load, reps, duration, RPE, pain, technique — `reps` rendered by two call
  sites, one per `when (exercise.trackingType)` arm), the warm-up `Switch`,
  and the undo/edit `TextButton`s.
  Resolved by adding `presentation/workout/ActiveWorkoutExerciseCard.kt`
  to CP6's "Files modified", correcting the parenthetical to name where
  set entry actually lives, and raising CP6's `session_target` from 2 to
  3 (complexity stays 3, already this plan's ceiling) to reflect the real
  two-file scope — see CP6 and the checkpoint table below. This also
  means B1's and B2's fixes now matter directly for CP6's own reskin, not
  just as a disclosed side effect: `ActiveWorkoutExerciseCard.kt`'s text
  fields and switch would otherwise have inherited both regressions on
  the app's most-used surface with no CP6 edit to notice them.
- **I1 — accepted.** Confirmed `MenuTokens.ContainerColor =
  SurfaceContainer` (`Menu.kt:193-194,402`) and all 7 `DropdownMenu` call
  sites (`HistoryScreen.kt:263,299`,
  `TrainingPlanEditorFormFields.kt:233`, `ActiveWorkoutScreen.kt:128,255`,
  `ExerciseListScreen.kt:249`, `TrainingPlanListScreen.kt:199`), none
  passing `containerColor`. Independently recomputed: light
  `surfaceContainer` = `surface` (`#f3f5fe`, CP1's own round-3 B1 value)
  gives exactly 1.000:1 against itself (Material 3's own baseline
  `#f3edf7` would give 1.057:1 against RepFlow's `surface`); dark's new
  literal `#1b1d2b` gives 1.099:1 against `surface` `#232532` and is
  measurably darker, confirming the "recessed overlay" inversion.
  Confirmed the sibling `AppBarTokens.OnScrollContainerColor` consumer is
  inert (zero `scrollBehavior` usages anywhere in
  `app/src/main/kotlin/`). Resolved by disclosing the `DropdownMenu`
  cascade in "Known limitations" alongside the `TopAppBar`/`FilterChip`
  cascades, adding the seven menu call sites to CP7's manual dark/light
  pass, and stating that light `surfaceContainer` = `surface` is a
  deliberate, disclosed choice (it is the design's own literal bottom-
  nav-bar value, reused via the shared `ColorScheme` role, not an
  oversight) while dark's overlay-inversion is noted as a purely visual,
  non-accessibility quirk of the same reuse.
- **I2 — accepted, resolved by assignment; disposition superseded, see
  round 10's B1 below.** Confirmed
  `DialogTokens.ContainerColor`/`DatePickerModalTokens.ContainerColor` =
  `SurfaceContainerHigh` (the app's 4 `AlertDialog`s and 2
  `DatePickerDialog`s), and Material 3's own baseline values
  (`PaletteTokens.Neutral17` = `#2B2930` dark, `.Neutral92` = `#ECE6F0`
  light — both confirmed against `PaletteTokens.kt`) are visibly off the
  RepFlow neutral ramp used everywhere else. Unlike
  `Snackbar`'s `inverse*` roles (genuinely unread by anything this
  milestone's primitives render — **corrected round 15's B1: at the time
  this disposition was written, `surfaceContainerHighest` was believed to
  be in the same category; it is not — the app's one bare `Card(`
  (`TrainingPlanEditorFormFields.kt:123`) reads it, and round 15's own B1
  reassigns dark's value on exactly the argument this paragraph makes next
  ("read by [...] pre-existing [components] regardless of this milestone's
  own scope")**), `surfaceContainerHigh` is read by six
  live, pre-existing dialogs regardless of this milestone's own scope —
  leaving the app's every confirmation and date-picking surface on an
  off-palette default in a milestone whose stated leverage is "one theme
  change, every screen updates" is a real, avoidable gap, not a deferred
  one. Resolved by assigning `surfaceContainerHigh` = dark
  `RepFlowColor.control` (`#292b31`, the app's existing "raised" tier) /
  light `surface` (`#f3f5fe`, matching the nav bar's own round-3 B1
  precedent), flagged as a disclosed judgment call (no dialog is rendered
  in either theme in the live design to confirm a literal value from) on
  the same footing as light `control` — see CP1 step 2 and "Known
  limitations" below.
- **Missing tests, item 1 — accepted, scoped.** Adds one relative-
  luminance contrast assertion to CP1 step 7 (renumbered) for light
  `onPrimary` against light `primary` (≥ 4.5), the one remaining
  CP1-authored value with a live accessibility rationale (round 3's I1) —
  not a blanket audit-everything test suite, since B1/B2's own fixes now
  route the accessibility-sensitive roles back to Material 3's own
  (untouched, not this milestone's to regression-test) baseline.
- **Missing tests, item 2 / Architecture — accepted.** CP1 gains a new,
  explicit step 5 (renumbering the prior steps 5/6 to 6/7) performing the
  role-consumer audit rounds 3-5 each did by hand for one role at a time:
  for every `ColorScheme` role step 2 assigns or deliberately leaves at
  baseline, grep every `androidx.compose.material3` import under
  `app/src/main/kotlin/`, cross-reference against the
  `material3-*-sources.jar` token files, and tabulate each consumer, the
  resulting value, and (for text/border roles) its contrast ratio. See
  CP1 step 5 below. (Renumbering note: this insertion shifts the former
  steps 5/6 to 6/7 — every "CP1 step 6" reference in rounds 1-4's own
  decision logs above is the `RepFlowThemeTest` step, unchanged in
  content, now step 7.)

## Round 6 plan-review decisions (revision 8)

`LOCAL_MODEL_PLAN_REVIEW` round 6 returned `REVISE` (3 Blocking, 2
Important, 0 Optional). Every finding was independently re-verified
against the repository, this machine's `material3-android-1.4.0-sources
.jar`, and an independent sRGB-relative-luminance pipeline before being
applied — dispositions below, with the evidence. The pipeline reproduces
every ratio this document already asserts (2.60, 6.22, 6.23, 4.10, 1.38,
3.20, 3.34, 1.099, 8.59, 7.59, and round 5's own new figures) to the
digit, plus round 5's OKLCH→sRGB conversions exactly, so the same pipeline
produces every new figure below.

- **B1 (blocking) — accepted, resolved by darkening light `error` from
  `oklch(0.55 0.13 25)`/`#b14e49` to `oklch(0.52 0.13 25)`/`#a74541`.**
  Confirmed against `material3-android-1.4.0`'s
  `OutlinedTextFieldTokens.kt`: `ErrorLabelColor`/`ErrorFocusLabelColor`
  and `ErrorSupportingColor`/`ErrorFocusSupportingColor`/
  `ErrorHoverSupportingColor` all resolve `ColorSchemeKeyTokens.Error` —
  `error` is the floating-label and validation-message **text** color of
  an `isError` `OutlinedTextField`, not just its border/icon
  (`ErrorOutlineColor`/`ErrorTrailingIconColor`, which only owe 3:1 and
  are unaffected). Confirmed `unfocusedContainerColor = Color.Transparent`
  (`TextFieldDefaults.kt:1385`) and 10 of the app's 12 `isError` sites pair
  `isError` with a real `supportingText` string
  (`ExerciseEditorFormFields.kt`'s name/instructions/rest-duration/
  load-increment fields, `TrainingPlanEditorFormFields.kt`'s plan-name/
  target-sets/warm-up-sets/rest/target-range fields), rendering on either
  bare `background` (`ExerciseEditorScreen.kt`/`TrainingPlanEditorScreen
  .kt`'s `Scaffold`s) or the Material-3-baseline `surfaceContainerHighest`
  card `TrainingPlanEditorFormFields.kt:123`'s bare `Card` resolves to
  (confirmed: no `colors =` argument). Independently recomputed: `#b14e49`
  gives 4.20:1 on light `background`, 4.00:1 on the card — both below the
  4.5:1 WCAG AA text floor this same step already applies to light
  `onPrimary`, reproducing the finding's figures exactly. Dark is
  unaffected (`#eb827b` gives 5.80:1/6.72:1 on surface/background, both
  well clear). This is a regression the milestone introduces relative to
  Material 3's own baseline (`#B3261E` gives 5.31:1/5.04:1 on the same two
  contexts), and the same failure shape as round 5's own B1 — a value
  confirmed for one context (the design's destructive/pain accent) applied
  unmodified to every render context the role actually reaches. Resolved
  by walking the same OKLCH lightness axis the design itself used to
  derive the light variant one step further, to the first value that
  clears both floors (`oklch(0.52 0.13 25)` → `#a74541`, 4.77:1/4.54:1) —
  not the intermediate `oklch(0.53 0.13 25)`/`#aa4844` step, which clears
  `background` (4.58:1) but not the card (4.36:1). See CP1 step 2, step 7,
  the reference doc's Color section, and CP7's manual pass for the
  corrected text.
- **B2 (blocking) — accepted.** Confirmed against `ColorLightTokens.kt`/
  `ColorDarkTokens.kt`/`PaletteTokens.kt`: `OnSurfaceVariant` resolves
  `NeutralVariant30`/`NeutralVariant80` = `#49454F` light / `#CAC4D0` dark;
  `Outline` (a different role) resolves `NeutralVariant50`/
  `NeutralVariant60` = `#79747E` light / `#938F99` dark. Both occurrences
  of this document's own onSurfaceVariant baseline claim (round 5's B1
  disposition above, and CP1 step 2's current text) had named `outline`'s
  baseline instead of `onSurfaceVariant`'s own — a transcription swap,
  six lines away from a correct citation of `NeutralVariant30 #49454F` in
  the same paragraph. Independently recomputed the actual values: light
  `#49454F` gives 8.59:1/7.59:1 on surface/background (reproducing the
  document's own existing figures for this exact hex) and 8.59:1 on the
  light dialog `surfaceContainerHigh` (= `surface`, per round 5's I2);
  dark `#CAC4D0` gives 8.91:1/10.33:1 on surface/background and 8.30:1 on
  the dark dialog `surfaceContainerHigh` (= `control`) — all comfortably
  clearing both the 4.5:1 and 3:1 floors, so round 5's B1 disposition
  (scoping the treatment to CP4, leaving the role unassigned) needed no
  change beyond the hex correction. This matters beyond bookkeeping
  because CP1's own step 5 audit tabulates contrast for every role left at
  baseline: an implementer starting from the stated (wrong) values would
  compute 4.19:1/3.70:1, conclude the baseline itself fails AA, and reopen
  a decision round 5 correctly settled. Resolved by correcting both
  occurrences to `#CAC4D0` dark / `#49454F` light with the ratios above;
  no code-affecting change, since the actual value CP1 step 6 rewires the
  theme against was never wrong, only the prose describing it.
- **B3 (blocking) — accepted.** Confirmed against
  `repflow-redesign-visual-foundation-artifacts.json`: this document
  (`docs/milestones/repflow-redesign-visual-foundation-execution.md`) is a
  `plan_stage.protected_paths` entry, while `app/` is a
  `plan_stage.excluded_prefixes` entry. CP1 step 5's "record the result
  inline in this document (or as a short table checked in alongside
  `RepFlowColor.kt`)" therefore offered a choice where only one branch is
  safe: a checkpoint commit taking the first option changes the plan-stage
  projection `compute_review_content_id_plan_stage_for_work_item` hashes,
  staling `plan_approval` mid-implementation and blocking
  `/approve-review`/`/accept-milestone` until a fresh plan-review round
  (`WORKFLOW_V2_PLAN.md`'s `D-States`, itself hardened for exactly this
  shape by `OPUS-R14-001`). The guard fails closed — nothing would ship
  wrong — but the plan as written sanctioned an action that breaks its own
  approval. Resolved by dropping the inline-in-this-document option
  entirely, leaving the `app/`-resident table as the only instruction; if
  the audit's conclusions genuinely need to reach this document, that is
  now stated explicitly as a plan revision under `/apply-plan-review`, not
  a checkpoint edit. See CP1 step 5 below.
- **I1 — accepted, resolved by disclosure.** Confirmed
  `FloatingActionButtonDefaults.containerColor`/`contentColorFor` resolve
  `FabPrimaryContainerTokens.ContainerColor`/`.IconColor` =
  `PrimaryContainer`/`OnPrimaryContainer` (`FloatingActionButton.kt:529-
  530`, `FabPrimaryContainerTokens.kt:22,28`), and the app has exactly two
  `FloatingActionButton` call sites (`ExerciseListScreen.kt:110`,
  `TrainingPlanListScreen.kt:98`), neither passing `containerColor`; zero
  hits for `primaryContainer`/`onPrimaryContainer` in either protected
  document. Unlike `surfaceContainerHighest`/`inverse*` (a pre-existing,
  undisturbed limitation nothing this milestone touches — **annotated round
  16, O3: dark `surfaceContainerHighest` is no longer in that category as
  of round 15's B1, which found the app's one bare `Card(` does read it;
  `inverse*` remains genuinely untouched**), this is a new
  divergence this milestone's own asymmetric scope introduces: CP5 turns
  `ExerciseListScreen.kt`'s FAB into a solid-accent FAB while
  `TrainingPlanListScreen.kt`'s FAB, untouched by any checkpoint, keeps
  Material 3's baseline neutral purple. Resolved by disclosure rather than
  assignment — consistent with how `outline`/`onSurfaceVariant`/the
  hairline value are already handled (a design-specific treatment for a
  narrow context gets its own scoped mechanism, not a global role with no
  other design-confirmed consumer) — naming both roles and their two FAB
  consumers in CP1's closing paragraph and a new "Known limitations"
  bullet, and adding `TrainingPlanListScreen.kt`'s FAB to CP7's manual
  pass. See CP1's closing paragraph, "Known limitations," and CP7 below.
- **I2 — accepted.** Confirmed CP1 step 2's `outline` parenthetical
  claimed clearance of "both the 4.5:1 text floor and the 3:1
  UI-component floor" — `outline` only owes 3:1 (it is a component-
  boundary role, not a text role), and the 4.5:1 claim is false: light
  `#79747E` measures 4.19:1 on `surface`, 3.70:1 on `background` (this
  document's own line 358-equivalent already recorded this exact pair for
  the same hex, in round 5's B2 disposition above). Nothing renders wrong
  because of this — `outline` clears the floor it actually owes everywhere
  (3.70:1–5.56:1 across both themes) — but it is an over-claim in a
  document whose review history is entirely about not asserting
  unverified numbers, one paragraph from B2's identical shape. The
  reference doc already states only the 3:1 claim and needs no change.
  Resolved by narrowing the parenthetical to the 3:1 floor, with the four
  measured ratios stated. See CP1 step 2 below.
- **Missing tests, item 1 — accepted.** CP1 step 7 gains a second
  relative-luminance contrast assertion, for light `error` (`#a74541`)
  against both light `background` and the light editor-card baseline
  `surfaceContainerHighest` (`#E6E0E9`), each ≥ 4.5 — the same reasoning
  round 5's own Missing-tests item 1 established for light
  `onPrimary`/`primary`, now extended to the other accessibility-sensitive
  role this round's own B1 found still failing.
- **Missing tests, item 2 — accepted, no action beyond B1's own fix.** If
  a future edit changes light `error` again, the reference doc's Color
  section must be re-resolved and restated, not just edited in CP1 — this
  round's own B1 disposition already does exactly that for `#a74541`, so
  the obligation is satisfied by construction rather than deferred.
- **Architecture — accepted as strongly recommended, not applied as a
  gate.** The review's suggestion (folding the colour half of CP1 step 5's
  role-consumer audit into step 2 itself, as a role → consumers → value →
  ratio table cited by prose rather than restated) is sound and is the
  direct cause of this being the sixth consecutive round to find a
  role-consumer gap from the same two mechanical inputs. It is not applied
  as a textual restructuring this round: B1/B2/I2's fixes already correct
  every figure the review found drifting, and folding the full audit table
  into CP1 step 2 now is a larger prose reorganization than a plan-review
  response should make unprompted alongside five substantive fixes. Left
  as an explicit "Areas the reviewer should specifically challenge" item
  is unnecessary here since the review itself already frames it as
  strongly recommended rather than a live either/or choice — noted here
  for the record instead.

## Round 7 plan-review decisions (revision 9)

`LOCAL_MODEL_PLAN_REVIEW` round 7 returned `REVISE` (2 Blocking, 2
Important, 0 Optional). Every finding was independently re-verified against
the repository, this machine's `material3-android-1.4.0-sources.jar`, and a
from-scratch sRGB relative-luminance/alpha-compositing pipeline before being
applied — dispositions below, with the evidence. The pipeline reproduces
every ratio this document already asserts (2.60, 6.22, 6.23, 4.10, 1.38,
3.20, 3.34, 4.19, 3.70, 8.59, 7.59, 8.91, 10.33, 4.20, 4.00, 4.77, 4.54,
5.31, 5.04) to the digit, so the same pipeline produces every new figure
below.

- **B1 (blocking) — accepted, resolved by raising light's CP4-scoped
  unselected-nav alpha from `.55` to `.66`.** Confirmed
  `RepFlowBottomNavigationBar.kt:36-42` (**corrected round 15's O2, was
  misstated as `:35-41`**) passes a real `label = { Text(…) }`
  to every `NavigationBarItem` and that `alwaysShowLabel` defaults to `true`
  (`NavigationBar.kt:203`, material3 1.4.0) — five unselected tab labels are
  visible on every top-level screen, not just the icon, which the 3:1
  icon-only floor does not cover. Independently recomposited: light
  `Color(0xFF292B31).copy(alpha = 0.55f)` over the nav's own container
  `#f3f5fe` gives `#84868d`, 3.34:1 — reproducing round 5's own figure for
  this exact composite, now shown to still be live after round 5 relocated
  it into CP4 rather than eliminating it. This is the same failure shape as
  round 5's B1 and round 6's B1/B2 — a value confirmed correct for one
  context (the 3:1 icon floor) silently carried into a second context (the
  4.5:1 text floor) the prose never re-checked. Resolved by raising the
  single shared literal to the first alpha step that clears 4.5:1 for the
  label: alpha `.66` composites to `#6e7077`, 4.55:1 (`.65` composites to
  ~4.42:1, still short). Dark is unaffected (`.60` already gives 5.76:1) and
  is left unchanged. This is a design-vs-accessibility deviation from the
  literal `rgba(41,43,49,.55)` `1d` renders, on the same footing as light
  `onPrimary` and light `error` above — the reference doc's design-source
  table keeps the literal `.55` as the confirmed source value; the
  corrected `.66` is stated as the applied implementation value, the same
  convention round 6's B1 established for `error`. A single shared value is
  kept for both icon and label (rather than splitting the two, the
  finding's other offered option) since the icon already clears 3:1 with
  room to spare at `.66` and one literal is simpler than two. See CP1 step
  2, CP4, CP7's manual pass, and the reference doc's bottom-nav table for
  the corrected value and threshold.
- **B2 (blocking) — accepted, resolved by scoping `secondaryContainer`/
  `onSecondaryContainer` to CP4, mirroring round 5's B1 fix for
  `onSurfaceVariant`.** Confirmed `FilterChipTokens.FlatSelectedContainerColor
  = SecondaryContainer` / `.SelectedLabelTextColor = OnSecondaryContainer`
  (`FilterChipTokens.kt:43,59`) and that all four stock `FilterChip` sites
  outside CP5's own `RepFlowTag`-based rebuild render directly on
  `Scaffold`'s `background` with no `Card`/`Surface` beneath
  (`HistoryScreen.kt:245`, `TrainingPlanListScreen.kt:108,114`,
  `ExerciseEditorFormFields.kt:116` inside `ExerciseEditorScreen.kt:69`'s
  `Scaffold`) — none passes `colors =`. Independently recomposited: light
  `primary` (`#5d5294`) at 16% over `background` (`#e4e7f5`) gives `#cecfe5`;
  the selected label `#5d5294` on that composite measures 4.41:1, below
  4.5:1 — while the same pair over the nav's own container `surface`
  (`#f3f5fe`) gives `#dbdbed`, 4.96:1, which is why the design-confirmed
  context (the nav pill) passes and the incidental cascade target (the
  chips) does not. This is the same failure shape as B1 above and round
  5/6's B1s, with the extra property an **alpha** role has: "every render
  context the role reaches" changes the composited color itself, not just
  what sits behind it. Considered three fixes (darken light
  `onSecondaryContainer` off the accent ramp; lower light's alpha to
  `.14`; scope the role out of the global cascade) and chose scoping,
  because — unlike B1, where CP4's own render is what needed correcting —
  here the nav's own pixel-confirmed pair (`#5d5294` @ 16%, `#d2cefd`/
  `#5d5294` labels) already passes everywhere it is actually
  design-confirmed (4.96:1 light; dark **corrected round 8's B1 from
  8.68:1 to 8.19:1** — this round's own reproduction had blended the dark
  composite against `background` `#161826` instead of the nav's own
  `surfaceContainer` `#1b1d2b`, its actual render ground); the failure is
  entirely the incidental cascade to three
  screens this milestone does not touch and CP3's `RepFlowTag` does not
  read `secondaryContainer` either (`RepFlowCard`/`RepFlowTag` apply their
  fill/border explicitly, confirmed in CP3's "Cards" bullet), so nothing
  this milestone's own primitives render needs the global role at all.
  Scoping requires no value change to the nav's own pixel-confirmed pair —
  it moves `secondaryContainer`/`onSecondaryContainer` out of
  `RepFlowDarkColorScheme`/`RepFlowLightColorScheme` and into CP4's existing
  `NavigationBarItemDefaults.colors(...)` override (which already carries
  `unselectedIconColor`/`unselectedTextColor` for `onSurfaceVariant`) via
  its `indicatorColor`/`selectedIconColor` parameters, both confirmed
  present on `NavigationBarItemDefaults.colors(...)`
  (`NavigationBar.kt:360-366`) and confirmed to fall back to the cascade
  default via `Color.Unspecified`/`takeOrElse` when omitted
  (`NavigationBarItemColors.copy`, `NavigationBar.kt:451-468`), so adding
  these two params does not disturb the already-correct `selectedTextColor`/
  default-indicator behavior. This retires revision 6's disclosed
  `FilterChip` cascade entirely (the three untouched screens' chips revert
  to Material 3's own opaque, high-contrast baseline pair —
  `Secondary90`/`Secondary10` light, comfortably clearing AA) and resolves
  the "global vs. scoped" question "Areas the reviewer should specifically
  challenge" has carried since revision 5 for these two roles specifically
  (the question remains open only for `secondary`/`surfaceContainer`, the
  two roles that stay global). See CP1 step 2, CP3's "Cards" bullet, CP4,
  CP7's manual pass, "Known limitations," and "Areas the reviewer should
  specifically challenge" below.
- **I1 — accepted, resolved by disclosure at the time; disposition
  superseded, see round 10's B1 below.** Confirmed
  `DatePickerModalTokens.ContainerColor = SurfaceContainerHigh` and
  `.DateTodayLabelTextColor = Primary` (`DatePickerModalTokens.kt:24,40`),
  wired through `DatePicker.kt`'s default `todayContentColor` parameter
  (`:594-595`). **Corrected round 10 (B1): the claim that follows here was
  false.** This round's own text originally read "The app's 4
  `AlertDialog`s are unaffected (`onSurface` `#e9e9ed` on dark
  `surfaceContainerHigh` `#292b31` = 11.69:1)" — true of the dialog
  *headline* (`DialogTokens.HeadlineColor = OnSurface`) and, unstated, the
  *supporting* text (`DialogTokens.SupportingTextColor = OnSurfaceVariant`,
  baseline `#CAC4D0` dark, 8.30:1 against the same container — not
  `onSurface`), but not of the *action label*
  (`DialogTokens.ActionLabelTextColor = Primary`, resolved through
  Compose's `TextButton` content color, `Button.kt:784`) — nine `TextButton`
  action labels across four dialogs (`HistoryScreen.kt:181,191`,
  `BackupScreen.kt:67,72`, `HistoryScreen.kt:364,376,382`,
  `RecoveryFutsalScreen.kt:210,223`) render `primary` directly on
  `surfaceContainerHigh` too, at the identical ratio computed below — see
  round 10's B1 for the full consumer set and the corrected disposition.
  Both `DatePicker` call sites are real and untouched by any checkpoint
  (`HistoryScreen.kt:388`, `RecoveryFutsalScreen.kt:228`). Independently
  recomputed: dark `primary` (`#9184d9`) on dark `surfaceContainerHigh`
  (`#292b31`) measures 4.38:1, 2.7% short of the 4.5:1 text floor for
  today's date number; light is unaffected (`#5d5294` on `#f3f5fe` =
  6.23:1), as are the selected-day roles in both themes (5.45:1 dark,
  6.22:1 light). This is milestone-introduced (Material 3's own dark
  baseline pairs `primary #D0BCFF` with `surfaceContainerHigh #2B2930`,
  clearing the floor). **This round resolved by disclosure rather than
  re-tuning `surfaceContainerHigh`** — a darker value (e.g. dark `surface`
  `#232532`, 4.71:1) would erase the "raised tier" distinction round 5's
  I2 deliberately introduced, for what this round believed was a single
  glyph in a modal the user opens deliberately, 2.7% short. **Round 10's
  B1 found the affected set is nine interactive labels, not one glyph, and
  re-decided: dark `surfaceContainerHigh` is now assigned `= surface`
  (`#232532`)**, the same re-tuning option this round considered and
  declined on a premise round 10 found false. See CP1 step 2's
  `surfaceContainerHigh` paragraph, "Known limitations," CP1 step 7, and
  CP7 below.
- **I2 — accepted.** Round 6's Architecture recommendation (fold the
  colour half of CP1 step 5's role-consumer audit into step 2 as a role →
  consumers → value → per-context ratio table) was deferred last round as
  "not this round," reasoning that folding it in was a larger reorganization
  than that round's five substantive fixes warranted. This round is the
  seventh consecutive round whose findings come from the same two
  mechanical inputs the table would hold, and both of this round's Blocking
  findings are cases of exactly the gap the table closes: a role's value
  stated without the ratio it produces at every surface it actually
  reaches. Applied this round: CP1 step 2 gains a role → consumers → value
  → per-context ratio table covering every role this document assigns,
  scopes, or deliberately leaves at baseline, carrying the two rules this
  round's own findings establish (an alpha value has one ratio per
  background, state each; a value scoped to a component still owes its
  floor inside that component — scoping relocates the value, it does not
  exempt it). See CP1 step 2's closing table below.
- **Missing tests, item 1 — accepted.** CP1 step 7 gains computed
  relative-luminance assertions for the CP4-scoped composited values: light
  unselected-nav-label composite (`.66` over the nav's `#f3f5fe` container)
  ≥ 4.5, and both themes' selected-nav-pill label composite (dark `#d2cefd`
  on `#9184d9` @ 20% over the nav's own `surfaceContainer` **`#1b1d2b`**
  — **corrected round 8's B1 from `background` `#161826`**; light `#5d5294`
  on `#5d5294` @ 16% over `#f3f5fe`) ≥ 4.5 — the same reasoning round 5/6's
  Missing-tests items
  established for light `onPrimary`/`error`, now extended to every
  CP4-scoped alpha composite, not just the one this round's B1 found
  failing. `secondaryContainer`/`onSecondaryContainer` join `outline`/
  `onSurfaceVariant` in the set of roles CP1 step 7 deliberately does not
  assert at the `ColorScheme` level (B2's fix moves them out of
  `RepFlowDarkColorScheme`/`RepFlowLightColorScheme` entirely), so the
  composite assertions above are their only test coverage — consistent with
  how the plan already treats every other CP4-scoped value.
- **Missing tests, item 2 — accepted.** CP1 step 0's spike now also
  constructs an alpha `Color` (`.copy(alpha = ...)`) and blends it against
  an opaque background, since step 7's real test now computes alpha
  composites for the assertions above and the spike's stated purpose is to
  prove the plain-JVM suite can do what step 7 needs before the token files
  are authored.

## Round 8 plan-review decisions (revision 10)

`LOCAL_MODEL_PLAN_REVIEW` round 8 returned `REVISE` (1 Blocking, 2
Important, 1 Optional). Every finding was independently re-verified against
the repository and this machine's `material3-android-1.4.0-sources.jar`
before being applied, per `/apply-plan-review` step 2 — every number below
was re-derived from the same straight source-over alpha-compositing/sRGB
relative-luminance pipeline round 7 used, not copied off the review's own
figures.

- **B1 (blocking) — accepted, resolved by correcting the dark
  selected-nav-pill composite's ground.** CP1 step 2 assigns the nav bar's
  own container role, `surfaceContainer`, a **new dark literal `#1b1d2b`**,
  explicitly distinct from `background` `#161826` — confirmed this is what
  actually renders: `RepFlowBottomNavigationBar.kt:28` (**corrected round
  15's O2, was misstated as `:29`**) calls bare
  `NavigationBar { … }` with no `containerColor`, so it takes
  `NavigationBarDefaults.containerColor = NavigationBarTokens.ContainerColor
  = SurfaceContainer` (`NavigationBarTokens.kt:24`), and no tonal overlay
  applies at `Elevation = Level0 = 0.dp`. Every dark figure for the
  selected-nav-pill composite (`secondaryContainer`/`onSecondaryContainer`,
  round 7's B2) had instead been computed and stated against `background`
  `#161826` in six places — round 7's own B2 disposition above, the
  Missing-tests-1 disposition above, CP1 step 2's prose and its new table,
  CP1 step 7's test-literal assertion, and the reference doc's bottom-nav
  section — while the prose at each site claimed the figure was "against
  the nav's own container." Re-derived independently: dark `primary`
  (`#9184d9`) at 20% opacity over the correct ground `#1b1d2b` composites to
  **`#33324e`**, and the selected label `#d2cefd` on it measures **8.19:1**
  (previously stated as composite `#2f2e4a`/8.68:1, which is the correct
  arithmetic for the *wrong* ground `#161826`). Light is unaffected and was
  already correct: light `surfaceContainer` = `surface` = `#f3f5fe`
  numerically, so `#5d5294` @ 16% → `#dbdbed`, 4.96:1, stands unchanged. Both
  conclusions still clear 4.5:1 comfortably, but CP1 step 7 names the wrong
  ground as a literal for a real test assertion — the single guard B2's fix
  leaves behind for these two CP4-scoped values would have been anchored to
  a color the bottom nav never renders, so a future edit to dark
  `surfaceContainer` (the one input that actually moves this ratio) would
  not be caught by the test that exists to catch exactly that. All six
  locations corrected to composite `#33324e`/8.19:1 against `surfaceContainer`
  `#1b1d2b`: round 7's own B2 disposition, the Missing-tests-1 disposition,
  CP1 step 2's prose and table row, CP1 step 7's assertion, and the
  reference doc's bottom-nav section. See CP1 step 2, CP1 step 7, and the
  reference doc for the corrected composite, value, and ground.
- **I1(a) (important) — accepted.** The round-7 role → consumers → value →
  per-context ratio table's `secondary` row stated its clearance as "by
  inheritance from `onBackground`/`onSurface`'s own already-asserted
  ratios" — but those ratios are measured on `surface`/`background`, which
  the nav container is not, in dark theme, by CP1 step 2's own
  `surfaceContainer` assignment. Measured directly against the nav's own
  container instead: dark `onBackground`/`onSurface` `#e9e9ed` on nav
  `surfaceContainer` `#1b1d2b` = **13.79:1**; light `#292b31` on nav
  `surface` `#f3f5fe` = **13.01:1** (numerically the same as light
  `surface`, since light's nav container is that role). Both clear
  comfortably — the fix is the table cell, not a value change. Applied to
  CP1 step 2's table.
- **I1(b) (important) — accepted, resolved by adding the four missing
  base-role rows rather than narrowing the table's completeness claim** (the
  finding's other offered option) — chosen because this round's own
  "Architecture and maintainability" note observes the table's entire value
  is being exhaustive and mechanical, and `background`/`surface`/
  `onBackground`/`onSurface` are the four roles every other row in the table
  is measured against, making them the rows a future edit is most likely to
  touch. Added `background` and `surface` as fill-only rows (no text renders
  directly on either without an intervening content-color role already
  covered), and a combined `onBackground`/`onSurface` row carrying every
  context it renders in: dark `#e9e9ed` on `background` `#161826` = 14.54:1,
  on `surface` `#232532` = 12.55:1, on `RepFlowColor.control` `#292b31` =
  11.69:1, on nav `surfaceContainer` `#1b1d2b` = 13.79:1 (same figure as
  I1(a)); light `#292b31` on `surface` `#f3f5fe` = 13.01:1, on `background`
  `#e4e7f5` = 11.49:1. All comfortable — no role's assigned value changes.
- **I2 (important) — accepted, resolved by correcting the "unaffected by
  this milestone" claims rather than leaving them as a silent no-op.**
  Round 7's B2 fix and its mechanism are correct — the three untouched
  screens' stock `FilterChip`s (`HistoryScreen.kt:245`,
  `TrainingPlanListScreen.kt:108,114`, `ExerciseEditorFormFields.kt:116`)
  do revert to Material 3's own opaque `Secondary90`/`Secondary10` (light)
  baseline pair, and that pair's own label-on-fill contrast is unchanged and
  comfortable (13.24:1 light / 7.19:1 dark, confirmed from
  `PaletteTokens.Secondary90`/`.Secondary10`/`.Secondary30`). What was
  inaccurate is the claim that this makes the chips "unaffected"/"unchanged
  from today": this milestone also moves `Scaffold`'s `background` (from
  Material 3's own `#FEF7FF` to `#e4e7f5`), which these four bare chips
  render directly on with no `Card`/`Surface` beneath. Independently
  recomposited: the light selected chip's fill-vs-ground separation narrows
  from `#e8def8` on `#FEF7FF` = 1.23:1 (already below WCAG 1.4.11's 3:1
  component-state floor — stock Material 3 behavior, not a regression this
  milestone introduces) to `#e8def8` on `#e4e7f5` = **1.05:1**; the
  unselected chip's 1dp outline (`FilterChipTokens.FlatUnselectedOutlineColor
  = OutlineVariant`, `:51-52`, unassigned by this plan so it stays at
  Material 3's baseline `#cac4d0` light) narrows from `#cac4d0` on `#FEF7FF`
  = 1.62:1 to `#cac4d0` on `#e4e7f5` = **1.38:1**. Confirmed none of the four
  sites passes a `leadingIcon` (`HistoryScreen.kt:245-249`,
  `TrainingPlanListScreen.kt:108-118`, `ExerciseEditorFormFields.kt:116-120`
  — all three pass only `selected`/`onClick`/`label`), and confirmed
  `FilterChipTokens.FlatSelectedOutlineWidth = 0.0.dp` (`:46`, the selected
  chip has no border at all), so on `TrainingPlanListScreen.kt` and
  `ExerciseEditorFormFields.kt` — where `TrainingPlanStatusFilter`
  (`ACTIVE`/`ARCHIVED`) and `ExerciseTrackingType.entries` respectively mean
  one chip in the row is always selected — the fill-vs-ground separation
  plus a label tint shift are the only signal carrying which filter is
  active. Not a WCAG regression from a passing state, and not fixed this
  round (disclosure, not a re-tune, on the same footing as the other
  disclosed judgment calls this document already carries) — the three
  "unaffected"/"unchanged from today" statements are corrected to state
  what actually changes (the ground, not the chips' own pair) in CP3's
  "Cards" bullet, "Known limitations," and CP7's manual pass, and CP7's
  manual pass gains a line naming the three sites and the 1.05:1 figure so
  the effect is actually looked at rather than silently skipped on the
  strength of the retired "no-op" claim.
- **O1 (optional) — accepted.** CP1 step 2's `onSurfaceVariant` paragraph
  had attributed the light `.55` → `.66` alpha correction to "B2"; that
  correction is round 7's **B1** (the `secondaryContainer`/
  `onSecondaryContainer` scoping is B2). CP4's own bullet, the round-7
  disposition above, the CP1 step 2 table cell, CP7, and the reference doc
  all already labeled it B1 correctly — this was an isolated typo two lines
  above the bullet that really is B2, now corrected.
- **Missing tests, item 1 — accepted.** CP1 step 7's assertion for the dark
  selected-nav-pill-label composite now blends against the nav's own
  `surfaceContainer` `#1b1d2b` (B1's corrected ground) rather than
  `background` `#161826`, asserting ≥ 4.5 against the resulting `#33324e`
  composite — the same computation, the correct third input, which is what
  makes the assertion able to catch a future `surfaceContainer` edit, its
  own stated reason for existing.
- **Missing tests, item 2 — no action, per the round's own reasoning.** I1's
  two sub-findings are table cells and I2 is a disclosure; none needs a new
  suite assertion, and a `FilterChip`-composite assertion is deliberately
  not added for the same reason CP1 step 7 already declines one for
  `outline`/`onSurfaceVariant`'s Material 3 baseline: the roles are no
  longer this milestone's to assert after B2, and pinning Material 3's own
  baseline pair would test the library, not this milestone's code.

## Round 9 plan-review decisions (revision 11)

`LOCAL_MODEL_PLAN_REVIEW` round 9 returned `REVISE` (0 Blocking, 2
Important, 1 Optional). Every finding was independently re-verified against
the repository and this machine's `material3-android-1.4.0-sources.jar`
before being applied, per `/apply-plan-review` step 2 — every number below
was re-derived from the same straight source-over alpha-compositing/sRGB
relative-luminance pipeline prior rounds used, not copied off the review's
own figures. Both findings live in the role → consumers → value → ratio
table's Consumers column, not in any value, composite, or test literal —
round 8's B1 fix (the dark selected-nav-pill ground) stands unchanged and
was not touched this round.

- **I1(a) (important) — accepted.** The table's `surface` row (:1459)
  named `Card` as a consumer, but `FilledCardTokens.ContainerColor =
  ColorSchemeKeyTokens.SurfaceContainerHighest`
  (`FilledCardTokens.kt:24`, independently re-confirmed) — this document
  already stated that correctly in CP3's "Cards" bullet and CP1 step 2's
  own closing paragraph, so the table contradicted the document. The
  role's real consumer, `ListItem` (`ListTokens.ListItemContainerColor =
  ColorSchemeKeyTokens.Surface`, `ListTokens.kt:27`, re-confirmed), was
  missing. Re-confirmed 6 stock `ListItem(` call sites in
  `app/src/main/kotlin` (`ExerciseListScreen.kt:218`,
  `HistoryScreen.kt:143`, `HistoryDetailScreen.kt:49`,
  `RecoveryHistoryScreen.kt:87,104`, `TrainingPlanListScreen.kt:164`), none
  passing `colors =`/`containerColor`, and confirmed no bare `Surface(`
  call site exists at all. The row now names `ListItem` and its six sites,
  drops `Card`, and keeps `Surface` only as "no call site in this app."
- **I1(b) (important) — accepted, resolved by disclosure in both places
  the finding offered.** Material 3's baseline has `Surface == Background`
  in both themes (`ColorLightTokens`/`ColorDarkTokens`, both
  `PaletteTokens.Neutral98`/`.Neutral6`), so today all six `ListItem` rows
  above render pixel-identical to the `Scaffold` behind them (1.00:1,
  re-confirmed). CP1 assigns `surface`/`background` apart, so after this
  milestone five of those six sites — every one CP5 does not already touch
  — become a visibly lifted band with no checkpoint disclosing it. Added a
  "Known limitations" bullet (:2319-2336) naming all five untouched sites
  and the measured before/after ratios (1.00:1 → 1.13:1 light / 1.16:1
  dark, both independently re-derived), and extended CP7's manual pass
  (:2112-2118) to name the same five sites so the effect is actually
  looked at rather than assumed.
- **I2(a) (important) — accepted.** `outlineVariant` had no table row,
  though CP1 step 2's closing paragraph already listed it among roles
  "deliberately left at the Material 3 baseline" once I2(b) below named it.
  Added a new row (:1463) naming both of its consumers in this app —
  `FilterChipTokens.FlatUnselectedOutlineColor` (re-confirmed
  `= ColorSchemeKeyTokens.OutlineVariant`, `FilterChipTokens.kt:51`; 4
  stock sites, already named in CP3's "Cards" bullet) and
  `DividerTokens.Color` (re-confirmed `= ColorSchemeKeyTokens.OutlineVariant`,
  `DividerTokens.kt:24`; 3 stock `HorizontalDivider()` sites, re-confirmed
  by `grep`: `HistoryScreen.kt:166`, `HistoryDetailScreen.kt:94`,
  `RecoveryFutsalScreen.kt:145`, none passing `color =`) — its baseline
  values (`NeutralVariant80`/`.NeutralVariant30` = `#cac4d0`/`#49454f`,
  re-derived from `PaletteTokens.kt`), and its measured contexts,
  independently re-derived: light `#cac4d0` on the new `background`
  `#e4e7f5` = **1.38:1** (from today's 1.62:1 on `#FEF7FF`); dark `#49454f`
  on the new `background` `#161826` = **1.88:1** (from today's 1.99:1 on
  `#141218`). Both decorative/component-boundary, not a WCAG regression.
  CP1 step 2's closing paragraph (:1424-1438) now names `outlineVariant`
  explicitly alongside `outline`/`onSurfaceVariant`/`secondaryContainer`/
  `onSecondaryContainer` rather than leaving it an implicit omission.
- **I2(b) (important) — accepted, resolved by explicit deferral rather
  than adding a new token.** The reference doc records the design's own
  divider value, `rgba(233,233,237,.09)`, as distinct from the hairline
  border, but no primitive this milestone's checkpoints build reads a
  divider value — unlike `RepFlowColor.hairline`, which CP3's primitives
  already consume. Introducing `RepFlowColor.divider` now would add a
  token with no consumer in this milestone's own scope. Added an explicit
  disposition in both places the finding offered: a "Known limitations"
  bullet (:2337-2353) and a new reference-doc paragraph (:103-113)
  stating the three `HorizontalDivider()` sites keep Material 3's
  `outlineVariant` baseline this milestone, and that the design's own
  value is deferred to a later milestone that actually touches those three
  screens — not silently dropped.
- **O1 (optional) — accepted, both cells.** `primary`'s consumer cell
  (:1456) now names `ProgressIndicatorTokens.ActiveIndicatorColor`
  (re-confirmed `= ColorSchemeKeyTokens.Primary`,
  `ProgressIndicatorTokens.kt:22`) and its 8 stock `CircularProgressIndicator`
  call sites app-wide (re-confirmed by `grep`, none passing `color =`),
  independently re-deriving the 3:1 graphical-object floor clears
  comfortably (dark 5.45:1, light 5.50:1 on `background`). `secondary`'s
  consumer cell (:1466) now names its `FocusIndicatorColor` cascade across
  13 stock component families plus `DialogTokens.IconColor`
  (`FilterChipTokens.kt:54`, `ListTokens.kt:26`, `SwitchTokens.kt:36`,
  `MenuTokens.kt:25`, `FilledCardTokens.kt:32`, `ElevatedCardTokens.kt`,
  `SearchBarTokens.kt:30`, `SuggestionChipTokens.kt`,
  `AssistChipTokens.kt`, `InputChipTokens.kt`, `NavigationDrawerTokens.kt`,
  `SheetBottomTokens.kt`, `TimeInputTokens.kt`, `DialogTokens.kt:36` —
  independently re-confirmed, exactly 13 families), and re-confirms no
  `AlertDialog` in this app passes `icon =`. Naming only — neither cell's
  Value/Context/Floor columns changed, and neither role's assigned value
  changed.
- **Missing tests — no action, per the round's own reasoning.** Both
  findings are table Consumers-column corrections/additions and disclosure
  bullets; none changes a value, composite, or assertion. No assertion is
  added pinning Material 3's own `ListItem`/`outlineVariant`/
  `CircularProgressIndicator`/focus-ring baselines, for the same reason
  CP1 step 7 already declines one for `outline`/`onSurfaceVariant`: it
  would test the library, not this milestone's code. Re-confirmed CP1 step
  7's existing suite still covers everything this round touched (no
  composite or literal it asserts on changed).

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_stage_completeness`, and
`assert_bundle_not_rejected` all pass; `TEST_RESULTS.md` opens with the
current `stage`/`head` lines; the registry stays at the CP1–CP7 set and
dependency edges, now at `plan_revision: 11`; the mapping still covers
REQ-1…REQ-9; `PLAN.md` is byte-identical to this document, and both
protected documents are byte-identical between the working tree and
`.pin/`.

## Round 10 plan-review decisions (revision 12)

`LOCAL_MODEL_PLAN_REVIEW` round 10 returned `REVISE` (1 Blocking, 1
Important, 1 Optional). Every finding was independently re-verified against
the repository and this machine's `material3-android-1.4.0-sources.jar`
before being applied, per `/apply-plan-review` step 2 — every ratio below
was re-derived from the same straight source-over alpha-compositing/sRGB
relative-luminance pipeline prior rounds used, not copied off the review's
own figures (36 values reproduced to the digit, spanning both this round's
new figures and every ratio this document already asserted). Round 10 is
the fourth consecutive round whose findings trace to the role → consumers →
value → ratio table, but B1 below is the first of the four that is a real
value change rather than a table-only correction: the table's own numbers
were never wrong, but a scope claim resting on them was, and the corrected
scope changes which disposition — fix vs. disclose — is defensible.

- **B1 (blocking) — accepted, resolved by re-tuning `surfaceContainerHigh`
  rather than by disclosure.** Round 7's I1 (see "Round 7 plan-review
  decisions" above) found dark `surfaceContainerHigh`
  (`RepFlowColor.control`, `#292b31`) drops `DatePickerModalTokens
  .DateTodayLabelTextColor` (`= Primary`) to 4.38:1, 2.7% short of 4.5:1,
  and disclosed it rather than re-tuning, stating three times across this
  document ("Round 7 plan-review decisions," CP1 step 2's
  `surfaceContainerHigh` paragraph, and "Known limitations") that "the 4
  `AlertDialog`s are unaffected (`onSurface` on `surfaceContainerHigh` =
  11.69:1 dark)." Re-confirmed independently: `DialogTokens
  .ActionLabelTextColor = ColorSchemeKeyTokens.Primary`
  (`DialogTokens.kt:26`) — Material's own spec puts a dialog's action
  labels on `primary`, not a content role — and Compose's `TextButton`
  content color resolves to `fromToken(ColorSchemeKeyTokens.Primary)`
  (`Button.kt:784`, container `Color.Transparent`, so the label renders
  directly on the dialog's own container,
  `AlertDialogDefaults.containerColor = DialogTokens.ContainerColor.value`,
  `AlertDialog.kt:225-226`). Four of the app's six dialogs use `TextButton`
  for their actions: `HistoryScreen.kt:176`'s invalidate-session
  `AlertDialog` (confirm `:181`, cancel `:191`), `BackupScreen.kt:62`'s
  restore `AlertDialog` (confirm `:67`, cancel `:72`),
  `HistoryScreen.kt:361`'s `DatePickerDialog` (confirm `:364`, clear
  `:376`, dismiss `:382`), and `RecoveryFutsalScreen.kt:207`'s
  `DatePickerDialog` (confirm `:210`, dismiss `:223`) — nine labels in
  total, all independently re-measured at the identical 4.38:1. The other
  two dialogs (`ExerciseEditorScreen.kt:161`,
  `TrainingPlanEditorScreen.kt:121`) use filled `Button`, whose
  `onPrimary`-on-`primary` label is genuinely unaffected (5.45:1 dark /
  6.22:1 light, re-confirmed). Also corrected in the same edit: the "the 4
  `AlertDialog`s are unaffected" sentence's own `onSurface`/11.69:1 figure
  covered only the dialog *headline* (`DialogTokens.HeadlineColor =
  OnSurface`); the *supporting* text
  (`DialogTokens.SupportingTextColor = OnSurfaceVariant`, `:34`, not
  `OnSurface`) was never separately stated — baseline `#CAC4D0` dark on
  `surfaceContainerHigh` measures 8.30:1, comfortable, but it is a distinct
  role from the headline's.

  **Why Blocking, not a disclosure fix.** This is milestone-introduced
  (Material 3's own dark baseline pairs `primary #D0BCFF` with
  `surfaceContainerHigh #2B2930` at 8.42:1, independently re-derived — and
  even that baseline pairing does not clear the floor against this
  milestone's own `primary`: `#9184d9` on `#2B2930` measures 4.45:1, still
  short, so "leave `surfaceContainerHigh` unassigned" is not a viable
  alternative disposition either). Two of the four affected dialogs are
  destructive confirmations — invalidating a recorded workout session, and
  restoring a backup over live data — where the confirm/cancel labels are
  the entire decision surface. The disposition this document carried for
  three rounds ("for one glyph in a modal the user opens deliberately") was
  the stated reason for accepting 4.38:1 rather than re-tuning; nine
  interactive labels across four dialogs, two of them destructive, is a
  materially different trade-off the document never actually weighed. Both
  the plan's own "Areas the reviewer should specifically challenge" (item
  restated below) and `REVIEW_REQUEST.md`'s "Unresolved questions" were
  asking the external reviewer to sign off on a disposition built on the
  false scope claim.

  **Resolution.** Dark `surfaceContainerHigh` is reassigned `= surface`
  (`#232532`), replacing `RepFlowColor.control` (`#292b31`) — light is
  unchanged (light `surfaceContainerHigh` already equalled light
  `surface`). This clears every affected label (dark `primary` on
  `#232532` = **4.71:1**) and every other consumer of the container
  (`onSurface` headline `12.55:1`; `onSurfaceVariant` supporting text
  `8.91:1`, both re-derived against the new value). The considered
  alternative — scoping a `TextButton` content-color override to just the
  four affected dialog composables, preserving `surfaceContainerHigh`'s
  distinct "raised tier" value — was not chosen: it would touch three
  files (`HistoryScreen.kt`, `BackupScreen.kt`, `RecoveryFutsalScreen.kt`)
  currently outside every checkpoint's scope, stretching this milestone's
  stated three-representative-surface boundary, where the chosen fix is a
  single value inside CP1's own token file and touches no new file. The
  cost is retiring round 5's I2 "raised tier" visual distinction between
  the dialog container and `surface` in dark theme (light never had it).
  Both options are surfaced for the external reviewer in "Areas the
  reviewer should specifically challenge" below, restating the item this
  finding corrects rather than silently re-deciding it. `RepFlowColor.control`
  itself is unchanged and still consumed by CP3's stepper/chip primitives
  (see CP1 step 2's `control`/`I4` history) — only what `ColorScheme
  .surfaceContainerHigh` is assigned to changes.

  Corrected at all three locations the false claim appeared verbatim
  (round 7's own "Round 7 plan-review decisions" entry, CP1 step 2's
  `surfaceContainerHigh` paragraph, and "Known limitations"), plus the role
  table's `surfaceContainerHigh` row, CP7's manual pass (now naming all
  four `TextButton`-based dialogs, not just the date picker), and the
  reference doc's own `surfaceContainerHigh` section.

- **Missing tests (B1's own) — accepted.** CP1 step 7 gains a computed
  relative-luminance assertion, dark `primary` against dark
  `surfaceContainerHigh` ≥ 4.5, pinning the input this finding found
  unguarded — the existing hex-equality assertion on `surfaceContainerHigh`
  catches a wrong literal but not a future edit to `primary` (or to this
  role) that keeps both hexes internally consistent while silently
  re-crossing the floor.

- **I2 (important) — accepted.** The `surface` row (`ListItem`-only since
  round 9's I1(a)) omitted its largest consumer:
  `AppBarTokens.ContainerColor = ColorSchemeKeyTokens.Surface`
  (`AppBarTokens.kt:26`), wired through `TopAppBarDefaults
  .defaultTopAppBarColors` (`containerColor = fromToken(AppBarTokens
  .ContainerColor)`, `AppBar.kt:1513`). Re-confirmed by `grep`: 10 stock
  `topBar = TopAppBar(...)` sites, one per screen
  (`ExerciseListScreen.kt:104`, `ActiveWorkoutScreen.kt:68`,
  `HistoryScreen.kt:100`, `HistoryDetailScreen.kt:37`, `BackupScreen.kt:82`,
  `RecoveryFutsalScreen.kt:91`, `RecoveryHistoryScreen.kt:42`,
  `TrainingPlanListScreen.kt:95`, `TrainingPlanEditorScreen.kt:49`,
  `ExerciseEditorScreen.kt:72`), none passing `colors =`; 8 of the 10
  untouched by any checkpoint. Added to the `surface` row's consumer cell.
  Material 3's baseline has `Surface == Background` in both themes
  (`Scaffold`'s own `containerColor` default is `MaterialTheme.colorScheme
  .background`, `Scaffold.kt:90`), so today every bar paints flush with the
  `Scaffold` behind it (1.00:1, invisible); CP1's split makes each bar a
  visibly distinct band after this milestone (**1.13:1 light / 1.16:1
  dark** — the identical pair round 9's I1(b) already measured for
  `ListItem`, because it is the same underlying role pair). No ratio
  fails: the title (`AppBarTokens.TitleColor = OnSurface`) and action
  icons (`.TrailingIconColor = OnSurfaceVariant`) are both already covered
  by existing table rows (**annotated round 17, O3: superseded by round
  16's B1 — nine of the ten bars pass no `actions` at all, and the
  tenth's `TextButton` reads `primary`, not `OnSurfaceVariant`; see the
  execution plan's CP1 step 2 role table**). Resolved by disclosure, not a fix — added to
  "Known limitations" (alongside the existing top-bar bullet, which also
  had a stale `TopAppBarTokens` object name, corrected to `AppBarTokens` —
  no such object exists in material3 1.4.0) and to CP7's manual pass. Also
  standardized the table's two inconsistent call-site counting conventions
  (see O1 below) while touching this row.

- **O1 (optional) — accepted, all three cells plus the counting
  convention.** `SwitchTokens.SelectedTrackColor = Primary` (`:54`) and
  `.SelectedHandleColor = OnPrimary` (`:43`) — re-confirmed 2 stock
  `Switch` sites (`ActiveWorkoutExerciseCard.kt:129`,
  `RecoveryFutsalScreen.kt:255`), neither passing `colors =`.
  `CheckboxTokens.SelectedContainerColor = Primary` (`:29`) and
  `.SelectedIconColor = OnPrimary` (`:51`) — re-confirmed 1 stock
  `Checkbox` site (`TrainingPlanEditorFormFields.kt:161`), no `colors =`.
  `OutlinedTextFieldTokens.FocusOutlineColor` (`:62`), `.FocusLabelColor`
  (`:60`), and `.CaretColor` (`:24`) are all `Primary` — re-confirmed
  across the already-counted 23 `OutlinedTextField`s; independently
  re-derived the focused-field ratios: dark `#9184d9` on `background`
  5.45:1 (already asserted) / on `surface` **4.71:1** (new); light
  `#5d5294` on `background` 5.50:1 (already asserted) / on `surface`
  **6.23:1** (new) — all comfortably clear the 3:1 component floor, and
  the focused label clears 4.5:1 too. `primary`'s and `onPrimary`'s
  consumer cells now name all three components. Also standardized the
  `surface` row's "6 stock call sites" (every site in the app) and the
  `outlineVariant` row's "4 stock `FilterChip` sites" (4 of the app's 6,
  post-CP5) to a consistent "N of M" form in both rows, rather than two
  silently different counting conventions in adjacent rows added the same
  revision.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_stage_completeness`, and
`assert_bundle_not_rejected` all pass; `TEST_RESULTS.md` opens with the
current `stage`/`head` lines; the registry stays at the CP1–CP7 set and
dependency edges, now at `plan_revision: 12`; the mapping still covers
REQ-1…REQ-9; `PLAN.md` is byte-identical to this document, and both
protected documents are byte-identical between the working tree and
`.pin/`.

## Round 11 plan-review decisions (revision 13)

`LOCAL_MODEL_PLAN_REVIEW` round 11 returned `REVISE` (0 Blocking, 2
Important, 1 Optional). The reviewer independently rebuilt the sRGB
relative-luminance/straight source-over alpha-compositing pipeline and
re-derived every ratio this document asserts, plus ran a mechanical
role-consumer audit against `material3-android-1.4.0-sources.jar`'s
`tokens/` package intersected with the 24 Material 3 composables this app
imports — nothing in the value column, ratio column, consumer column, or
token wiring was found wrong. Both findings are documents that did not
follow a prior round's own fix.

- **I1 (important) — accepted, both parts.** "Areas the reviewer should
  specifically challenge"'s `surfaceContainerHigh` bullet (revision 7,
  round 5's I2) still presented `RepFlowColor.control` (`#292b31`) as "this
  revision's chosen fix" and re-posed the assign-vs-baseline question that
  round 10's B1 (see above) already answered. Marked closed, stating the
  current value (dark `surfaceContainerHigh = surface`, `#232532`) and
  pointing at the restated bullet that carries the one part of the question
  still open (the cost of retiring the dark-theme "raised tier"
  distinction) — the same "no longer open" framing this section already
  uses elsewhere for roles B1/B2 settled. The round-history entry at round
  5's own I2 (revision 7, `:423`) carried the same staleness — the value
  B1 replaced, with no supersession marker — and gets the same one-clause
  marker round 7's I1 entry already has ("disposition superseded, see
  round 10's B1 below"). No value, ratio, or consumer set changes; both
  edits are prose-only, confirmed by re-reading CP1 step 2's dialog-roles
  paragraph and its restatement in "Known limitations" (where the
  assign-vs-baseline question is actually closed) and the restated bullet
  in "Areas the reviewer should specifically challenge" (the bullet that
  now carries the remaining question alone).

- **I2 (important) — declined, decision recorded.** `repflow-redesign-
  visual-foundation-artifacts.json`'s `plan_stage.excluded_paths`/
  `excluded_prefixes` rationale strings remain verbatim `workflow-v2-1-core`
  template text, as round 1's own O2 (revision 3, above) already found and
  declined to change. That decision stands, restated here with the correct
  reasoning: the strings are not part of the hashed projection —
  `compute_review_content_id_plan_stage`'s projection carries
  `sorted(excluded_paths)`/`sorted(excluded_prefixes)`, the sorted *keys*
  (bare paths), never the justification values — so re-authoring them would
  be digest-neutral, and, symmetrically, leaving them is also
  digest-neutral; neither choice is forced by cost, and cost is not the
  basis for this decision (a prior review round's feedback had framed a
  future re-authoring as costing a plan revision; that framing was
  incorrect). The classification itself is independently re-verified
  correct for this product work item regardless of the prose:
  `resolve_plan_stage_metadata` resolves both protected plan docs to
  `plan=protected`, `app/**` and the declaration file itself to
  `plan=excluded`, and `assert_all_changed_paths_classified_worktree`
  passes against `b39af90`. Left as inherited template prose because what
  the plan-stage approval binds is the set of excluded paths and the fact
  of their exclusion, not the prose explaining why — re-authoring roughly
  two dozen independent rationale strings in this round risks introducing a
  new inaccuracy for no change in reviewed content. A future revision that
  touches this file for another reason may re-author the prose
  opportunistically.

- **O1 (optional) — accepted.** `DialogTokens.SupportingTextColor` is
  `DialogTokens.kt:34`, not `:35` (`:35` is `SupportingTextFont`) —
  corrected at `…-execution.md:1082`, CP1 step 2's dialog-roles paragraph,
  and `…-reference.md:315`.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`,
and `assert_bundle_not_rejected` (run twice) all pass; the registry stays at
the CP1–CP7 set and dependency edges, now at `plan_revision: 13`; the
mapping still covers REQ-1…REQ-9; both protected documents remain
byte-identical between the working tree and `.pin/`.

## Round 12 plan-review decisions (revision 14)

`LOCAL_MODEL_PLAN_REVIEW` round 12 returned `REVISE` (0 Blocking, 1
Important, 1 Optional). The reviewer independently rebuilt the sRGB
relative-luminance/straight source-over alpha-compositing pipeline and
re-derived every ratio this document asserts against `DialogTokens.kt` in
`material3-android-1.4.0-sources.jar`, and re-verified each of round 11's
four acceptance criteria individually against the repository rather than
this document's own account of them — nothing in the value column, ratio
column, consumer column, token wiring, registry, or mapping was found
wrong, and both of round 11's findings were confirmed correctly disposed
of. Both of this round's own findings are about the reviewer-facing
artifacts revision 13 itself introduced, not about the design.

- **I1 (important) — accepted.** Revision 13 introduced this document's
  first five self-referential line citations — four in "Round 11
  plan-review decisions" above and one in "Areas the reviewer should
  specifically challenge" — copied from round 11's own feedback (which
  measured revision 12) into a file whose new decisions section shifted
  every later line by 71 (plus a further 5 for the "Areas the reviewer
  should specifically challenge" bullet's own growth). Four of the five
  pointed at the wrong passage as a result. Converted all four to the
  section-name form this document used exclusively for its first twelve
  revisions (see the two edits above and the restated bullet below); the
  one correct citation (`:423`, round 5's own I2 entry) is left as a
  number, unchanged, since it was not wrong and converting it risked
  breaking a reference that already works. This restores the property
  that no cross-reference in this document is invalidated by a later
  round's own new decisions section.
- **O1 (optional) — accepted.** `REVIEW_REQUEST.md`'s numbered item (12),
  its unnumbered "Areas the reviewer should specifically challenge"
  counterpart, and its own item 18 all said "new this round (I2)"/"New
  this round (I2)" for a question actually added at revision 12 (round
  10's I2) — the same document's own "Areas the reviewer should specifically
  challenge" section already said so, in the sentence explaining item 18's
  own provenance. Relabelled all three "added at revision 12 (round 10's
  I2)", matching the phrasing that same document's own "Architecture
  decisions" section already uses for the identical question.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`,
and `assert_bundle_not_rejected` (run twice) all pass; the registry stays at
the CP1–CP7 set and dependency edges, now at `plan_revision: 14`; the
mapping still covers REQ-1…REQ-9; both protected documents remain
byte-identical between the working tree and `.pin/`.

## Round 13 plan-review decisions (revision 15)

`LOCAL_MODEL_PLAN_REVIEW` round 13 returned `REVISE` (0 Blocking, 1
Important, 1 Optional). The reviewer independently rebuilt the sRGB
relative-luminance pipeline and re-derived all forty-five contrast figures
this document states, re-counted every component-population claim against
the working tree, and re-verified round 12's five acceptance criteria
individually — nothing in the value column, role assignment, checkpoint,
dependency edge, or requirement mapping was found wrong, and both of round
12's findings remain correctly disposed of. This round's own finding is a
numeric transposition standing since revision 8, not something revision 14
introduced.

- **I1 (important) — accepted.** CP1 step 2's `outline` parenthetical (the
  paragraph beginning "`outline` is deliberately *not* assigned (corrected
  this round, B2) and stays at Material 3's baseline") stated light
  `outline`'s two measured ratios as "3.70:1/4.19:1" at both its two
  occurrences — the reverse of the `surface`/`background` order the same
  sentence announces and dark's own figures demonstrate in the same
  clause. Independently recomputed light `#79747E` against `surface`
  `#f3f5fe` (4.19:1) and `background` `#e4e7f5` (3.70:1) using the sRGB
  relative-luminance formula before editing, confirming the reviewer's
  numbers rather than taking them on trust. Swapped both occurrences to
  read "4.19:1/3.70:1", matching round 5's B1/B2 dispositions, round 6's
  I2 disposition, and CP1 step 5's role table `outline` row, all four of
  which already stated the pair correctly and were left unchanged. Nothing
  else in the parenthetical changed: the 3:1 conclusion, the round-6-I2
  narrowing clause, and both baseline hexes were already correct.
- **O1 (optional) — accepted.** 31 of 32 in-body "this round" markers
  below `## Goal` do not name their originating round, a pattern round
  12's O1 only partially addressed (it fixed `REVIEW_REQUEST.md`'s labels
  and explicitly held the plan body's own markers out of scope). Rather
  than mass-rewriting 31 markers — the option the finding itself declined
  to request, matching revision 13's reasoning for declining round 11's I2
  — added one convention sentence to the "Provenance note (revision 2)"
  section above, stating that an in-body "this round" marker names the
  round it was introduced in and that the corresponding "Round N
  plan-review decisions" section is the authoritative per-round record.
  Also corrected `REVIEW_REQUEST.md`'s "UI/UX decisions" item (5) from
  "narrowed this round from four" to "narrowed at revision 9 (round 7's
  B2)", matching the phrasing its own "Unresolved questions" entry and
  challenge item 7 already used.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`,
and `assert_bundle_not_rejected` (run twice) all pass; the registry stays at
the CP1–CP7 set and dependency edges, now at `plan_revision: 15`; the
mapping still covers REQ-1…REQ-9; both protected documents remain
byte-identical between the working tree and `.pin/`.

## Round 14 plan-review decisions (revision 16)

`LOCAL_MODEL_PLAN_REVIEW` round 14 returned `REVISE` (0 Blocking, 1
Important, 2 Optional). The reviewer re-derived the full contrast pipeline
independently again (all forty-odd figures reproduce), re-confirmed round
13's six acceptance criteria, and re-counted every component population
**per file** rather than app-wide — that per-file pass is what surfaced
this round's one Important finding. Every value, hex, role assignment,
checkpoint, dependency edge, `session_target`, complexity value, registry
entry, and requirement mapping was confirmed correct; no process guard
failed.

- **I1 (important) — accepted, independently re-verified against the
  repository before editing (not taken on the reviewer's word).**
  `grep -n "OutlinedTextField(" app/src/main/kotlin/com/repflow/app/
  presentation/workout/ActiveWorkoutExerciseCard.kt` returns seven call
  sites (`:71`, `:80`, `:93`, `:105`, `:117`, `:132`, `:141`), all inside
  `internal fun ExerciseCard`, not six. B3's disposition (revision 7,
  `:399-401` above) undercounted because it counted six *field kinds* —
  load, reps, duration, RPE, pain, technique — but `reps` has two call
  sites in two different arms of `when (exercise.trackingType)`: `:80`
  (`WEIGHT_AND_REPS`) and `:93` (`REPS_ONLY`). Corrected the count to
  seven at all four sites the plan stated six: B3's own disposition
  (`:399-401`), CP6's "benefiting incidentally" paragraph, CP6's "Sizing
  revisited this round (B3)" paragraph, and CP7's manual-pass item. At the
  first site, distinguished the six field kinds from the seven call sites
  explicitly rather than dropping the kind breakdown. At the CP7 site,
  replaced the flat count with a branch-aware instruction: because the
  rendered field set is chosen by `when (exercise.trackingType)`, no
  single manual-pass session can see all seven sites at once (a
  `WEIGHT_AND_REPS` exercise never renders `:93`/`:105`) — the instruction
  now names all three branches (`WEIGHT_AND_REPS`: load+reps,
  `REPS_ONLY`: reps, `DURATION`: duration, plus RPE/pain/technique and the
  warm-up `Switch` common to all three) so the pass actually reaches every
  site. Nothing else in CP6 changes: the "Files modified" list,
  `session_target: 3`, `complexity: 3`, and the one-`Switch` count (`:129`)
  were already correct and stay unchanged.
- **O1 (optional) — accepted.** The "Known limitations" `ListItem` bullet
  (round 9's I1(b), `:2809-2817` before this round's edit) stated "six …
  rows, on five screens, none touched by any checkpoint" — correct for the
  six-row/five-screen set as a whole, but "none touched by any checkpoint"
  then contradicted the same bullet's own next clause, which carves out
  `ExerciseListScreen.kt:218` as inside CP5's scope. Reworded to "on six
  stock `ListItem(` rows across five screens; five of them, on four
  screens, are untouched by any checkpoint:", matching the phrasing
  `REVIEW_REQUEST.md`, round 9's I1(b) open question, challenge item 16,
  and CP7's own manual-pass item already use elsewhere in this document.
  Prose only; no site, ratio, or disposition changed.
- **O2 (optional) — accepted.** CP1's access-path rule (`:2078-2080`
  before this round's edit) named `RepFlowColor.pill`-adjacent shape
  tokens, but no such token object exists — the pill token is a *shape*,
  `RepFlowShapes.pill` (999dp/`CircleShape`, authored CP1 step 4, consumed
  by CP3's status chips), consistent with every other reference to it in
  this document. Corrected the one wrong occurrence to
  `RepFlowShapes.pill`. No value or token changed.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`
(against this item's own `resolve_plan_stage_metadata` sets, not the module
defaults), and `assert_bundle_not_rejected` (run twice) all pass; the
registry stays at the CP1–CP7 set and dependency edges, now at
`plan_revision: 16`; the mapping still covers REQ-1…REQ-9; both protected
documents remain byte-identical between the working tree and `.pin/`.

## Round 15 plan-review decisions (revision 17)

`LOCAL_MODEL_PLAN_REVIEW` round 15 returned `REVISE` (2 Blocking, 1
Important, 2 Optional). Every finding was independently re-verified
against the repository and this machine's
`material3-android-1.4.0-sources.jar` before being applied, per
`/apply-plan-review` step 2 — every figure below was re-derived from
scratch, not copied off the review's own numbers, and reproduced to the
digit in every case. Round 14's six acceptance criteria were re-confirmed,
not redone, and round 11's I2 stays settled, per the review's own
"Architecture and maintainability concerns."

- **B1 (blocking) — accepted, resolved by reassigning dark
  `surfaceContainerHighest = surface`.** The app's one bare `Card(`
  (`grep -rn "\bCard(" app/src/main/kotlin/…/presentation/` — a single
  hit, `TrainingPlanEditorFormFields.kt:123`, `PlannedExerciseRow`) is
  pre-existing and undisturbed, confirmed round 6's B1's own finding; its
  content is not. Round 15's B2 (below) establishes `TextButton`'s label
  is `primary`, and this card's body (`:123-165`, confirmed by reading it
  directly) contains all four of `TrainingPlanEditorFormFields.kt`'s
  `TextButton`s — the exercise-name picker (`:228`, always enabled), the
  ↑/↓ reorder pair (`:258`/`:263`, `enabled`-gated), and the remove control
  (`:268`, always enabled) — plus seven focused `OutlinedTextField` labels/
  carets (`:129` target sets, `:139` warm-up sets, `:150` rest seconds,
  `TargetRangeFields`' four at `:175`/`:184`/`:197`/`:206` — **corrected
  round 16's I6, was miscited as `:179`/`:189`/`:201`/`:211`, which land on
  `isError =`/`supportingText =` lines instead**) and the
  `Checkbox` fill at `:161`. Independently recomputed with the same sRGB
  relative-luminance pipeline every other figure in this document
  reproduces: dark `primary` (`#9184d9`, `RepFlowColor.accent300`'s dark
  base) on the Material 3 baseline container (`#36343B`,
  `PaletteTokens.Neutral22`, confirmed against `PaletteTokens.kt`) measures
  **3.8022 → 3.80:1**, 15.5% short of the 4.5:1 text floor — milestone-
  introduced, since Material 3's own dark pair clears at **7.1992 →
  7.20:1** (`#D0BCFF` on the same container) and `primary` is this
  milestone's own assigned value. This is round 10's B1 one container
  further out, and worse on every axis that finding weighed (15.5% short
  vs. 2.7%; four always-visible, one destructive-in-kind, row-level
  controls vs. nine modal labels). **Resolved the same way round 10's B1
  resolved `surfaceContainerHigh` one tier down**: dark
  `surfaceContainerHighest` is reassigned `= surface` (`#232532`) — dark
  `primary` on it now measures **4.7075 → 4.71:1**, and no new file is
  touched, only one more literal in CP1's own token file
  (`RepFlowColor.kt`). Dark `error` on the same container moves to
  **5.7964 → 5.80:1** (**corrected round 16's I1: the pre-fix ground was
  the Material 3 baseline `#36343B`, 4.68:1 — round 6's B1 measured only
  the light side of this container — and the reassignment moves dark's
  ground too; 5.80:1 still clears comfortably**). **Light is left unassigned**, at Material
  3's baseline `#E6E0E9` — light `primary` already measures **5.2270 →
  5.23:1** there (Material 3's own baseline `#6750A4` measures 4.9700 →
  4.97:1, also clear), so no reassignment is needed, and reassigning it
  anyway (to `surface` `#f3f5fe`) would move the ground round 6's B1
  measured light `error` against, from **4.5379 → 4.54:1** to **5.4060 →
  5.41:1** — still clear, but two stated locations (CP1 step 7's second
  `error` assertion and the `error` table row) would need restating for no
  accessibility gain, so it is left alone. See CP1 step 2, the role table,
  CP1 step 7 (new computed assertion), "Known limitations," "Areas the
  reviewer should specifically challenge," and CP7's manual pass (now
  naming this card explicitly) for the corrected text.
- **B2 (blocking) — accepted, resolved by correcting `TextButton`'s role
  attribution from `onSurfaceVariant` to `primary` everywhere it appears.**
  Grepping every `TextButtonTokens.` usage across all 249 material3 1.4.0
  sources outside the token file itself returns exactly two hits, both for
  the *disabled* state (`Button.kt:787-788`); the enabled label instead
  reads `fromToken(ColorSchemeKeyTokens.Primary)` three lines above
  (`Button.kt:784`), with the library's own `// TODO replace with the
  token value once it's corrected` comment directly above that —
  `TextButtonTokens.LabelColor` is dead code in material3 1.4.0. Round 5's
  B1 disposition (revision 7) asserted both halves of a contradiction: its
  own token-mapping list named `TextButtonTokens.LabelColor` as an
  `onSurfaceVariant` consumer, while round 10's B1 (revision 12) already
  needs `TextButton` to be `primary` for its nine dialog labels at 4.38:1
  to exist at all — the latter is the half that is right. Corrected six
  locations plus one table cell, keeping round 5's B1's disposition intact
  and re-stating its evidence on the surviving consumers: 23
  `OutlinedTextField`s (`OutlinedTextFieldTokens.LabelColor`/
  `.InputPlaceholderColor`/`.SupportingColor`), 6 `ListItem`s
  (`ListTokens.ListItemSupportingTextColor`/`.ListItemOverlineColor`), 4
  `AlertDialog`s (`DialogTokens.SupportingTextColor`), 5 `OutlinedButton`s
  (`OutlinedButtonTokens.LabelTextColor`, `:35`), and — genuinely omitted
  until now, confirmed `AppBarTokens.TrailingIconColor = OnSurfaceVariant`
  at `AppBarTokens.kt:37` — 10 `TopAppBar` trailing icons, already
  established as `onSurfaceVariant` consumers in the `surface` row's own
  text but never counted in `onSurfaceVariant`'s own row (**this addition
  does not survive — corrected round 16's B1: `AppBarTokens
  .TrailingIconColor` reaches only a bar's `actions` slot, and the app's
  one such site is a `TextButton`, itself `primary` — zero live
  consumers, see below**). The six
  locations: round 5's B1 disposition above, CP1 step 2's `onSurfaceVariant`
  bullet, the role table's `onSurfaceVariant` row, CP4's own bullet, CP7's
  manual-pass item (rewritten separately, see below), and the reference
  doc's bottom-nav section. **The `primary` row's own cell was wrong in
  the mirror direction**: `OutlinedButton` is not a `primary` consumer —
  `ColorScheme.defaultOutlinedButtonColors.contentColor =
  fromToken(OutlinedButtonTokens.LabelTextColor)` (`Button.kt:740`), and
  `OutlinedButtonTokens.LabelTextColor = OnSurfaceVariant` (`:35`) —
  removed from the `primary` row. Its `FilterChip` clause was also
  imprecise: a `FilterChip`'s **selected** icon is `OnSecondaryContainer`
  (`FilterChipTokens.kt:72`); `primary` is its **unselected**
  `LeadingIconColor` (`:77`) — corrected, though inert either way since
  round 8's I2 already confirmed none of the six stock sites passes a
  `leadingIcon`. CP7's manual-pass item is rewritten as its own item below
  rather than folded into the `onSurfaceVariant` regression check, since
  verifying `TextButton` is no longer that kind of check at all.
- **I1 (important) — accepted, resolved by wiring the CP1 step 7 composite
  assertions' inputs to production values.** Step 7's closing instruction
  claimed each CP4-scoped alpha composite is computed from "the literal
  base hex and alpha against the literal opaque background… so a future
  edit to any of the three inputs… is caught" — but with all three inputs
  typed as literals directly into the test, editing production changes
  nothing the test reads, so the conclusion did not follow from its own
  premise (round 8's B1's stated reason for these assertions existing — 
  catching a future `surfaceContainer` edit — did not actually hold as
  written). The fix differs per input: `primary` and `surfaceContainer`
  are `ColorScheme` roles the plain-JVM suite already proves it can read
  (step 0's spike, step 7's own hex assertions above) — now read directly
  off `RepFlowDarkColorScheme`/`RepFlowLightColorScheme` instead of
  restated as literals. The two accent steps were already named
  (`RepFlowColor.accent300`/light's reused `primary`) and are now
  referenced instead of restated. The four alphas (`.6`, `.66`, `.20`,
  `.16`) lived inline inside CP4's `NavigationBarItemDefaults.colors(...)`
  call, `@Composable` and unreachable from `app/src/test` — named as
  `RepFlowColor.navUnselectedAlphaDark`/`.navUnselectedAlphaLight`/
  `.navSelectedIndicatorAlphaDark`/`.navSelectedIndicatorAlphaLight` in CP1
  step 2, consumed by both CP4's override and CP1 step 7's test. No value,
  alpha, ground, or ratio changes — every composite this session
  recomputed is correct (`#6e7077` 4.545; `#97979f` 5.7612; `#33324e`
  8.1940; `#dbdbed` 4.9573) — only what the test reads changes.
- **O1 (optional) — accepted.** Round 5's B1 disposition (`:325-332`
  before this round's edit) asserted "`ListItem`s with `supportingContent`
  in five files (plus CP5's own)" — six files by that reading, when there
  are five, one of which is CP5's own (confirmed: `HistoryScreen.kt`,
  `ExerciseListScreen.kt` [CP5's], `RecoveryHistoryScreen.kt`,
  `HistoryDetailScreen.kt`, `TrainingPlanListScreen.kt` — six `ListItem(`
  sites, five files). Reworded to "five files (CP5's own included)."
- **O2 (optional) — accepted.** Two `RepFlowBottomNavigationBar.kt`
  citations were off by one, confirmed by reading the file directly:
  `NavigationBar {` is `:28`, not `:29` (`:29` is the
  `RepFlowDestinations.TOP_LEVEL_DESTINATIONS.forEach` line); the `label =
  { Text(…) }` block is `:36-42`, not `:35-41` (`:35` is the `icon = {
  BasicText(...) }` line). Both conclusions the citations supported were
  already right (the bare `NavigationBar {` call, and every item passing a
  real `label`) — corrected at all three occurrences (`:642`, `:814`,
  `:1773` before this round's edit).
- **Missing tests, item 1 (B1's own) — accepted.** CP1 step 7 gains a
  computed relative-luminance contrast assertion for dark `primary` against
  dark `surfaceContainerHighest` ≥ 4.5, the same "pin the floor, not the
  pre-fix value" convention round 5/6/7/10's analogous items already
  established. Light is left unassigned, so no assertion applies there,
  per this same step's own standing rule for `outline`/`onSurfaceVariant`.
- **Missing tests, item 2 (I1's own) — accepted, see I1 above.** The
  composite assertions' inputs are wired to production `ColorScheme`
  fields and named constants rather than narrowed.
- **Missing tests, item 3 — accepted.** `RepFlowColor.accent300`/
  `.accent600`/`.accent700`/`.accent900` had neither an assertion nor a
  recorded reason to decline one, unlike every other value CP1 step 2
  authors. CP1 step 7 gains four hex-equality assertions
  (`#d2cefd`/`#796cbf`/`#5d5294`/`#2b2741`).

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `assert_stage_completeness(stage="plan",
plan_revision=16)`, `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`
(against this item's own `resolve_plan_stage_metadata` sets), and
`assert_bundle_not_rejected` (run twice, the second immediately before this
round's first write) all pass; the registry stays at the CP1–CP7 set and
dependency edges — neither Blocking finding moves a checkpoint boundary,
confirmed against the review's own Architecture/maintainability and
Migration/data-integrity sections — now regenerated at `plan_revision: 17`;
the mapping still covers REQ-1…REQ-9; both protected documents will be
re-pinned as part of this round's bundle regeneration.

## Round 16 plan-review decisions (revision 18)

`LOCAL_MODEL_PLAN_REVIEW` round 16 returned `REVISE` (1 Blocking, 6
Important, 3 Optional). Round 15's eight acceptance criteria were
independently re-verified against the repository rather than against
revision 17's account of it, and all eight held; round 11's I2 stayed
settled. Every contrast figure this round re-derived reproduced to the
digit from a from-scratch sRGB relative-luminance pipeline, and every
material3 1.4.0 mapping this round rests on was re-resolved directly out
of this machine's `material3-android-1.4.0-sources.jar`.

- **B1 (blocking) — accepted, resolved by removing the "10 `TopAppBar`
  trailing icons" term round 15's B2 added.** `AppBarTokens
  .TrailingIconColor` reaches only a `TopAppBar`'s `actions` slot
  (`AppBar.kt:3047`, `LocalContentColor provides actionIconContentColor`,
  wrapping only the `actionIcons` box — `navigationIcon` instead gets
  `navigationIconContentColor`/`LeadingIconColor`, `AppBar.kt:3000`). Of
  the app's ten `topBar = TopAppBar(...)` sites, exactly one passes
  `actions =`: `RecoveryFutsalScreen.kt:93`, whose action (`:95-101`) is a
  `TextButton` — a `primary` consumer per round 15's own B2, not
  `onSurfaceVariant`. `AppBarTokens.TrailingIconColor` therefore has
  **zero** live consumers in this app; the corrected `onSurfaceVariant`
  population is `(23/6/4/5)`, not `(23/6/4/5/10)`. Corrected the term at
  all four locations round 15's B2 touched (round 5's B1 disposition, CP1
  step 2's `onSurfaceVariant` bullet, the role table's `onSurfaceVariant`
  row, and the reference doc's bottom-nav section), restated round 5's B1
  disposition on its surviving 23 + 6 + 4 + 5 = 38 consumers, and fixed the
  two statements this term made false: the role table's `surface` row's
  "action icons … already covered," and "Known limitations"'s "these eight
  bars' action icons stay at Material 3's own baseline tint, same as
  today" — the second no longer holds for `RecoveryFutsalScreen`, one of
  the eight, whose action *is* `primary` on this milestone's own `surface`
  fill (dark 4.71:1, light 6.23:1); disclosed there and in CP7's manual
  pass instead. The surviving "`TextButton`/`OutlinedTextField`/etc."
  clause at the same "Known limitations" bullet, still crediting
  `TextButton` as an `onSurfaceVariant` consumer after round 15's own B2
  correction, is fixed alongside it.
- **I1 (important) — accepted.** Dark `error` on the reassigned
  `surfaceContainerHighest` measures **5.7964 → 5.80:1**, not the
  **4.6818 → 4.68:1** four locations (the role table's
  `surfaceContainerHighest` row, round 15's B1 disposition, CP1 step 2's
  prose, and "Known limitations") stated for it — that figure is the
  Material 3 baseline container (`#36343B`) B1's reassignment replaces,
  not the reassigned `surface` (`#232532`) itself; the role table's own
  `error` row already states 5.80:1 for dark `error` on `surface`, since
  the two containers are now the same colour. All four restated at
  5.80:1 with "unaffected" dropped — the reassignment moves the figure,
  favourably, rather than leaving it untouched. The `error` role table
  row's card citation is also updated to note dark's card is now
  `surface`.
- **I2 (important) — accepted, disclosed.** `SwitchTokens
  .UnselectedTrackColor` (`:78`) is also `ColorSchemeKeyTokens
  .SurfaceContainerHighest`, wired straight through by
  `SwitchDefaults.colors()` (`Switch.kt:337`/`:398`) — a consumer B1's
  reassignment retints that no location named. The app's two stock
  `Switch` sites (`ActiveWorkoutExerciseCard.kt:129`,
  `RecoveryFutsalScreen.kt:255`), neither passing `colors =`, both render
  their unchecked track on `background` today; the reassignment moves dark
  from **1.4347 → 1.43:1** to **1.1588 → 1.16:1**. The track's boundary
  (`SwitchTokens.UnselectedFocusTrackOutlineColor = Outline` —
  **corrected round 17's O1, was misstated as `.UnselectedTrackOutlineColor`,
  also `Outline`, so no value changes**) still clears the
  3:1 non-text floor at 4.80:1 against the new track, so this is a visual
  change, not a WCAG failure — the same class of disclosed, reviewer-facing
  side effect rounds 9/10's `ListItem`/top-bar findings already got. Added
  to the role table's `surfaceContainerHighest` row Consumers cell with
  both figures, a "Known limitations" bullet, and a CP7 manual-pass item
  naming both switch sites (light is unaffected — light
  `surfaceContainerHighest` stays at baseline).
- **I3 (important) — accepted.** Three locations still classified
  `surfaceContainerHighest` as a role nothing reads, after round 15's B1
  established that the app's one bare `Card(` does read it: CP3's "Cards"
  bullet (which told the implementer CP1 "does not need to assign it" —
  false, and the instruction that most mattered to get right), and both
  CP1 closing paragraphs' "unread"/"no primitive … actually reads" lists,
  one of which named the correction in the same sentence it contradicted
  ("light `surfaceContainerHighest` — dark is reassigned this round, B1,
  since it *is* read"). Rewrote CP3's bullet to state what is true
  (`RepFlowCard` never reads the role because it sets its own fill; CP1
  assigns dark `surfaceContainerHighest` anyway, for the stock `Card` at
  `TrainingPlanEditorFormFields.kt:123`), and moved light
  `surfaceContainerHighest` out of the "unread" bucket into the "read, but
  the baseline value already clears" bucket in both closing paragraphs,
  alongside `outline`/`onSurfaceVariant`/`secondaryContainer`/
  `onSecondaryContainer`.
- **I4 (important) — accepted.** The `primary` role table row described
  the editor card's seven `OutlinedTextField` labels as "(unfocused)" —
  the unfocused label is `OutlinedTextFieldTokens.LabelColor =
  OnSurfaceVariant` (`:78`); `primary` is the *focused* state
  (`.FocusLabelColor`/`.CaretColor`, `:60`/`:24`), as the row's own
  `surfaceContainerHighest` counterpart, CP7's manual pass, the reference
  doc, and round 15's own section all already say. Corrected the one word.
- **I5 (important) — accepted, resolved by adding the missing composite
  assertion.** CP1 step 7's four named `nav*Alpha*` constants (round 15's
  I1) were said to let `RepFlowThemeTest` reference all four, but step 7's
  own composite-assertion list only covered three — light unselected, dark
  selected, light selected — leaving `RepFlowColor.navUnselectedAlphaDark`
  named but unread by any test, the same "coverage claim stronger than the
  coverage" shape round 7's own B1 named. Added the fourth: dark
  unselected-nav-label composite (`RepFlowDarkColorScheme.onSurface.copy(alpha
  = RepFlowColor.navUnselectedAlphaDark)` over the nav's
  `RepFlowDarkColorScheme.surfaceContainer`, `#1b1d2b`) ≥ 4.5 — currently
  **5.7612**, comfortably clear, matching the role table's own stated
  figure — so both "every CP4-scoped alpha value"/"lets … `RepFlowThemeTest`
  reference the same constant" claims now hold for all four.
- **I6 (important) — accepted.** Two problems in the editor card's
  `TargetRangeFields` citations, both from round 15. First, off-by-~5:
  read directly, its four `OutlinedTextField`s open at `:175`/`:184`/
  `:197`/`:206`, not the stated `:179`/`:189`/`:201`/`:211` (which land on
  `isError =`/`supportingText =` lines instead) — corrected in round 15's
  own section. Second, and more substantive: `TargetRangeFields` renders
  its duration pair *or* its reps pair, never both
  (`if (row.trackingType == ExerciseTrackingType.DURATION)`, `:173`) — so a
  single planned-exercise row shows five focused `OutlinedTextField`
  labels, not the static count of seven the card's subtree can render
  across both branches. CP7's manual-pass item for this card gets the same
  `row.trackingType`-branch instruction its `ActiveWorkoutExerciseCard`
  sibling item already has (round 14's I1): exercise both branches to
  actually reach every label.
- **O1 (optional) — accepted.** CP4's `secondaryContainer` bullet said
  "these four values are the only three composite inputs" — reworded to
  "these values are the composite inputs," dropping the internal
  four-vs-three contradiction.
- **O2 (optional) — accepted.** CP1 step 7's accent-hex assertion was
  labelled "New this round (I1, Missing-tests item 3)" — that is round
  15's Missing-tests item 3, not its I1 (a different finding, the
  composite-wiring one). Corrected the attribution; the values themselves
  are unchanged and already match CP1 step 2.
- **O3 (optional) — accepted.** Two comparatives described the bare-`Card`
  case as pre-existing and untouched, written before round 15's B1
  established that this milestone does touch dark `surfaceContainerHighest`.
  Round 6's I1 disposition (historical) gets the same one-line annotation
  round 15 already gave round 3's B1 and round 5's I2 for the identical
  situation; the live "Known limitations" FAB bullet's comparative is
  reworded to name `Snackbar`'s `inverse*` case specifically instead.

**Missing tests.** I5's own is resolved above by adding the fourth
composite assertion. B1's is a *removal*, not an addition — recorded
explicitly here so a later round does not read the gap as an omission:
nothing in CP1 step 7 asserts anything about `AppBarTokens
.TrailingIconColor`, and nothing should, since an unassigned role with
zero live consumers has no ratio to pin (this step's own standing rule for
`outline`/`onSurfaceVariant`).

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `assert_stage_completeness(stage="plan",
plan_revision=17)`, `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`
(against this item's own `resolve_plan_stage_metadata` sets), and
`assert_bundle_not_rejected` (run twice, the second immediately before this
round's first write) all pass; the registry stays at the CP1–CP7 set and
dependency edges — no finding moves a checkpoint boundary — now regenerated
at `plan_revision: 18`; the mapping still covers REQ-1…REQ-9; both
protected documents will be re-pinned as part of this round's bundle
regeneration.

## Round 17 plan-review decisions (revision 19)

`LOCAL_MODEL_PLAN_REVIEW` round 17 returned `REVISE` (0 Blocking, 2
Important, 3 Optional). Round 16's eleven acceptance criteria were
independently re-verified against the repository rather than against
revision 18's account of it, and all eleven held; round 11's I2 stayed
settled. Every contrast figure this round re-derived reproduced to the
digit from a from-scratch sRGB relative-luminance pipeline, and every
material3 1.4.0 mapping this round rests on was re-resolved directly out
of this machine's `material3-android-1.4.0-sources.jar`.

- **I1 (important) — accepted, disclosed.** Four locations (CP1 step 2,
  the role table's `surfaceContainerHighest` row, "Known limitations,"
  and the reference doc) framed the app's one bare `Card(`
  (`TrainingPlanEditorFormFields.kt:123`) as a container this milestone
  does not touch — true of the *call site*, not of the *value*: CP1 step
  2 changes dark `surfaceContainerHighest` from `#36343B` to `#232532`.
  Nothing measured what that reassignment (or, in light, `background`'s
  own move) does to the card's own separation from the page ground it
  sits on. `Card`'s `Surface` is called with `border = null` and
  `shadowElevation` from `FilledCardTokens.ContainerElevation =
  ElevationTokens.Level0` (`Card.kt:80-98`, `:393-408`,
  `FilledCardTokens.kt:25`), so fill contrast is the row card's only
  edge; it renders inside a plain `Column` directly on `background`
  (`TrainingPlanEditorFormFields.kt:52-54`, `:94`, `:106`, `:123`).
  Re-derived from scratch: dark **1.5151 → 1.52:1** today (`#36343B` on
  Material 3's `#141218`), **1.4347 → 1.43:1** against this milestone's
  own `background` baseline, **1.1588 → 1.16:1** after the reassignment
  (`#232532` on `#161826`) — the same pair the plan already printed for
  the `Switch`-track consumer alone; light **1.2320 → 1.23:1** today
  (`#E6E0E9` on `#FEF7FF`) → **1.0521 → 1.05:1** after this milestone's
  `background` move (`#E6E0E9` on `#e4e7f5`), unrelated to any role
  reassignment since light `surfaceContainerHighest` stays unassigned.
  No ratio fails — a decorative container edge owes no WCAG floor, the
  same reasoning the plan already applies to the light `FilterChip`'s
  1.05:1 — but it is the largest surface this milestone visually
  flattens, with no border or shadow to fall back on. Added the four
  figures to the role table's Ratio cell alongside (not folded into) the
  `Switch`-track entry; corrected the four locations' framing to say the
  call site is undisturbed and the value is not; added a "Known
  limitations" bullet on the same footing as the `Switch` bullet; and
  repointed CP7's manual-pass clause from "against the rest of the row's
  now-identical-to-`surface` fill" to the card-vs-`background` comparison
  on both themes, naming light's 1.05:1 explicitly. Declined a new open
  question: unlike round 9's I1(b)/round 10's I2, which made a previously
  invisible 1.00:1 band visible for the first time, this card's edge was
  already a visible band before this milestone and only narrows, the same
  footing as the light `FilterChip`'s already-unquestioned 1.05:1.
- **I2 (important) — accepted.** Round 16's B1 removed the "10
  `TopAppBar` trailing icons" term from the four locations it named and
  fixed the two statements it identified as falsified, but two more live
  statements carried the same claim: the round-10 top-bar-split "Known
  limitations" bullet said the action icons "stay at Material 3's
  baseline," and CP7's own round-16 B1 item said the other bars "keep" a
  trailing-icon tint. Nine of the ten `TopAppBar(` sites pass no
  `actions` at all, so there is no tint for them to keep or stay at
  anything; the tenth's action (`RecoveryFutsalScreen.kt:93`'s
  `TextButton`) reads `primary`, this milestone's own value, already
  covered by the `primary` row at 4.71:1 dark / 6.23:1 light against the
  bar's own `surface` fill. Both locations corrected; round 10's own
  historical statement of the same superseded claim gets the same
  one-line annotation convention round 3's B1, round 5's I2, and round
  6's I1 already have.
- **O1 (optional) — accepted, all four locations.** `Switch`'s resting
  border is wired from `SwitchTokens.UnselectedFocusTrackOutlineColor`
  (`Switch.kt:338`, `:399-400`), not `.UnselectedTrackOutlineColor` as
  four locations named — both tokens resolve to `ColorSchemeKeyTokens
  .Outline`, so no figure or conclusion changes. Corrected the token name
  at all four citations.
- **O2 (optional) — accepted.** CP3's "Cards" bullet called
  `RepFlowCard`'s non-consumption of `FilledCardTokens.ContainerColor`
  "distinct from CP1's `surface`" one clause before restating that CP1
  assigns dark `surfaceContainerHighest` (which *is* `surface`) anyway —
  true only of light. Corrected to "distinct from CP1's `surface` in
  light, equal to it in dark after round 15's B1."
- **O3 (optional) — accepted.** Round 10's own historical section still
  stated its pre-round-16 "action icons … already covered by existing
  table rows" claim unannotated; gets the same one-line annotation this
  round gives it (see I2 above).

**Missing tests.** None of this round's findings changes a computed
value or introduces a new floor to pin: I1's container-vs-ground figures
are not a floor (a decorative container edge owes none, and the
container's *value* is already test-enforced through
`surfaceContainerHighest`'s own hex assertion and the dark
`primary`-vs-`surfaceContainerHighest` composite assertion round 15's B1
added); I2/O1/O2/O3 are text and citation corrections in documents no
computation reads.

**Process/identity guards, re-run here and passing**, per this round's own
review: `compute_bundle_id`/`compute_review_content_id_plan_stage_for_work_item`
recompute to match `MANIFEST.md`; `assert_review_request_states_review_content_id`
confirms `REVIEW_REQUEST.md`; `assert_stage_completeness(stage="plan",
plan_revision=18)`, `validate_local_plan_review_preconditions`,
`assert_local_generation_matches`, `assert_all_changed_paths_classified_worktree`
(against this item's own `resolve_plan_stage_metadata` sets), and
`assert_bundle_not_rejected` (run twice, the second immediately before this
round's first write) all pass; the registry stays at the CP1–CP7 set and
dependency edges — no finding moves a checkpoint boundary — now regenerated
at `plan_revision: 19`; the mapping still covers REQ-1…REQ-9; both
protected documents will be re-pinned as part of this round's bundle
regeneration.

## Goal

Establish the production Compose design foundation derived from the RepFlow
Claude Design / Nocturne target — color/typography/shape/spacing tokens, an
icon approach, and a deliberately small reusable primitive set — then apply
it to enough representative existing UI/application-shell surfaces to prove
the system and provide the foundation subsequent redesign milestones build
on.

This is a **bounded visual-foundation milestone, not a whole-app visual
rewrite**. Every other existing screen keeps rendering through the same
`RepFlowTheme`/`MaterialTheme` and therefore inherits the new tokens
automatically wherever it already uses stock Material 3 components — but no
screen beyond the three named application surfaces below (bottom nav,
Exercise list, Active Workout set-entry/rest timer) has its markup touched
in this milestone. Top app bars are not a separate proven surface: no
shared top app bar exists to restyle (every screen owns its own, see CP4
below), so the only top bars this milestone's markup changes are the two
that come along for the ride inside CP5's and CP6's own file edits
(`ExerciseListScreen.kt`, `ActiveWorkoutScreen.kt`) — the other eight
inherit only the CP1 color/type cascade, same as every other unreskinned
screen.

## Explicit non-goals

- No navigation/IA change: the existing 6-destination bottom nav
  (Exercises/Workout/Plans/Recovery/History/Backup), its route strings, and
  the flat single-`NavHost` screen hierarchy are unchanged. (The target
  design's 4-tab Home/Plans/History/Progress IA — confirmed again this
  session, directly from the live project's own "bottom nav on the four
  top-level destinations only" layout rule — is a *later* milestone, R1 in
  the redesign decomposition, and is explicitly out of scope here. The
  design's "workout mode replaces the nav" full-screen takeover behavior is
  the same kind of out-of-scope IA change and is likewise not adopted by
  CP6.)
- No domain or application-layer behavior change. No new use case, no new
  domain model, no change to `ProgressionPolicyV1`, set classification,
  recovery/futsal logic, or backup/restore logic.
- No persistence or schema change. Room stays at version 7. No new
  `MIGRATION_x_y`, no new entity, no new column.
- No workout-semantics change: set entry, undo/edit-last-set scope,
  warm-up/working classification, and rest-timer behavior are functionally
  identical before and after this milestone — only their rendering changes.
- No new runtime dependency (Gradle) of any kind — see CP1/CP2 below for how
  fonts and icons are sourced without one.

## Source-of-truth discipline

Per the user's stated distinction: the live Claude Design project
(`RepFlow.dc.html`, the `nocturne-3f5c4219-8153-42d4-afb3-9364451b6478`
design-system bundle/`styles.css`, and `RepFlow.dc.html`'s own turn-6
component-spec sheet, id `6b`) is authoritative for the *intended*
visual/product design. This repository is authoritative for *implementation
reality* (current architecture, current `RepFlowTheme.kt`, current test
assertions). Where the generic Nocturne stylesheet and RepFlow's own
literal component spec disagree (confirmed this session: they do, on radii
and spacing — see the reference doc), RepFlow's own literal spec (`6b`)
wins, per its own stated intent to be the Compose token source. All token
values below are already the corrected, re-verified ones — CP1's first step
is a targeted re-confirmation against the live source (not a from-scratch
re-derivation), since the values are now known precisely rather than
approximated.

## Preserved invariants (re-verified against the current repo this session)

This milestone must not regress any of the following, confirmed present in
the current codebase (re-confirmed 2026-08-27, not carried over
unverified):

- `ProgressionPolicyV1` determinism and its reason strings (unmodified —
  this milestone never touches `domain/progression`).
- Rest timer's absolute-`endAt` reconstruction, both in
  `domain/workout/RestTimer.kt` and in `ActiveWorkoutScreen`'s tick loop —
  CP6 must re-render the timer's *display*, never replace the tick-loop's
  derivation from `Instant.now()` with a stored countdown.
- Undo/edit scoped to only the most-recently-recorded set per exercise
  (`UndoLastWorkoutSet`/`EditLastWorkoutSet`) — CP6 changes only how the
  entry pad and set list render, not which sets are editable.
- Warm-up/working classification (`WorkoutSet.isWarmup`) feeding
  `CompleteWorkoutSession`'s progression filter — untouched.
- Session invalidation (soft, never hard-delete), plan-version immutability,
  and single-active-session enforcement — untouched, since this milestone
  never modifies `application/` or `domain/`.
- Domain purity (`LayerBoundaryTest`) — this milestone adds presentation-only
  code (`presentation/designsystem/**`, plus resource files); nothing it adds
  can violate domain-layer import rules, but CP7's verification run confirms
  `LayerBoundaryTest` still passes as a matter of record, not assumption.
- Existing test-asserted content: `MainActivityNavHostSmokeTest`,
  `ExerciseListScreenTest`, `ActiveWorkoutScreenTest`, and any other Compose
  UI test touching a surface this milestone reskins must keep passing.
  Re-confirmed this session: these tests assert against string resources
  (`onNodeWithText(composeRule.activity.getString(R.string....))`), not the
  literal single-letter nav glyphs — so the CP4 icon swap is lower-risk than
  revision 1 assumed, but CP4/CP5/CP6 still each begin by reading their
  target screen's existing test file before touching markup, to catch any
  remaining literal-content dependency.

## Checkpoints

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Design tokens & Compose theme foundation | - | 3 | 2 |
| CP2 | Bounded icon approach (local vector assets) | - | 2 | 1 |
| CP3 | Core reusable primitive components | CP1, CP2 | 2 | 1 |
| CP4 | Apply foundation to app shell (bottom nav) | CP3 | 1 | 1 |
| CP5 | Apply foundation to Exercise list (representative list/CRUD surface) | CP3 | 2 | 1 |
| CP6 | Apply foundation to Active Workout set-entry + rest timer (representative core-interaction surface) | CP3 | 3 | 3 |
| CP7 | Verification, dark/light check, Open-decision + status doc updates | CP4, CP5, CP6 | 1 | 1 |

(This table is `workflow_state.render_registry_markdown`'s own generated
output, re-embedded verbatim at revision 19 — unchanged since revision 11:
no checkpoint added, removed, or renamed, and no `session_target`/
complexity change this round (round 15's B1/B2/I1, round 16's B1/I1–I6,
and round 17's I1/I2/O1/O2/O3 are all text-only fixes — round 16's I5 adds
one CP1 step 7 test assertion, not a checkpoint-boundary or complexity
change — per each round's own Architecture/maintainability note: no
Blocking finding moves a checkpoint boundary); the actual
`/milestone-plan`
declaration step generates the authoritative machine-readable
registry/mapping at
`docs/ai-workflow/registry/repflow-redesign-visual-foundation-registry.json`
and `docs/ai-workflow/requirements/repflow-redesign-visual-foundation-mapping.json`,
which is the source of truth once declared — this table should match it,
not the reverse.)

### CP1 — Design tokens & Compose theme foundation

**Files added**: `presentation/designsystem/RepFlowColor.kt`,
`RepFlowTypography.kt`, `RepFlowShapes.kt`, `RepFlowSpacing.kt`; font
resources under `app/src/main/res/font/`; `RepFlowThemeTest.kt` under
`app/src/test`.
**Files modified**: `presentation/RepFlowTheme.kt`.

0. Before authoring the four token files (i.e. before step 1 below), spike
   one throwaway JVM unit test under `app/src/test` constructing a
   `darkColorScheme(primary = Color(0xFF9184D9))` and asserting
   `.primary == Color(0xFF9184D9)` off the result, to confirm this
   repository's plain-JVM `app/src/test` suite can actually construct and
   read a real `ColorScheme` with no Robolectric/instrumentation setup —
   this repo has no existing precedent either way (all 80 current files
   under `app/src/test` are domain/data/ViewModel tests; no Compose import
   appears there yet). **New this round (round 7's Missing-tests item
   2)**: also construct an alpha `Color` in the spike (e.g.
   `Color(0xFF292B31).copy(alpha = 0.66f)`) and blend it against an opaque
   background using plain arithmetic, since step 7's real test now computes
   alpha composites (the CP4-scoped `onSurfaceVariant`/`secondaryContainer`/
   `onSecondaryContainer` assertions below) and this is the other shape that
   needs proving safe on the plain-JVM suite before the token files are
   authored, alongside the `ColorScheme` shape below. **Corrected this
   round (O2)**: a bare
   `androidx.compose.ui.graphics.Color`/`androidx.compose.ui.unit.Dp` value
   class construction would spike the wrong shape — those are inline value
   classes that always succeed, while step 7's real test builds a full
   `ColorScheme` via `darkColorScheme()`/`lightColorScheme()` and reads role
   properties off it (confirmed safe: `ColorScheme` is `@Immutable` with
   plain `val` properties in material3 1.4.0, `ColorScheme.kt:147-148`, not
   snapshot-state-backed) — the spike now exercises that same shape, so it
   can actually fail before step 7 if the risk is real. Do this now, not at
   step 7 where the real `RepFlowThemeTest` lands, so a "not mocked"/
   framework-call failure surfaces before the token files are authored, not
   after: if the spike needs Robolectric, that is a new dependency category
   under `AGENTS.md`, and REQ-8's "no new Gradle dependency" needs an
   explicit test-scope carve-out addressed here rather than discovered
   mid-checkpoint. Delete the throwaway test once step 7's real
   `RepFlowThemeTest` subsumes it. (Renumbered this round: round 5 inserts
   a new step 5, the role-consumer audit, ahead of the former steps 5/6 —
   now 6/7.)
1. Re-confirm the live Claude Design source values against the table in the
   reference doc immediately before authoring (values are already pulled
   precisely this session; this step is a final spot-check, not a
   from-scratch derivation).
2. Author `RepFlowColor.kt`: a `RepFlowDarkColorScheme` and
   `RepFlowLightColorScheme`, each a Material 3 `ColorScheme` built from the
   extracted tokens, plus a handful of extra tokens with no `ColorScheme`
   slot (`RepFlowColor.control`, `.hairline`, `.accent300`, `.accent600`,
   `.accent700`, `.accent900`).

   **Dark**: `primary` = base accent `#9184d9`, `onPrimary` = `#161826`,
   `background` = `#161826`, `surface` = `#232532`, a third `control` tier
   `#292b31` (no direct Material 3 slot — expose via `RepFlowColor.control`,
   alongside the `ColorScheme`), `onBackground`/`onSurface` = `#e9e9ed`,
   `error` = `#eb827b` (`oklch(0.72 0.13 25)`'s resolved sRGB — see the
   reference doc's Color section for the conversion). `onPrimary` =
   `#161826` matches CP3's primary-button spec ("solid accent fill,
   `#161826` text") exactly, and is assigned rather than left at Material
   3's baseline (`#381E72`) because a Material 3 `Button` reads its content
   color from `colorScheme.onPrimary` — the app's single most prominent
   control would otherwise render deep-purple-on-purple instead of the
   design's ground-colored label.

   **`outline` is deliberately *not* assigned (corrected this round,
   B2) and stays at Material 3's baseline** (`#938F99` dark / `#79747E`
   light — independently confirmed to clear the 3:1 UI-component floor it
   owes against every `surface`/`background` pair in both themes: dark
   4.80:1/5.56:1, light 4.19:1/3.70:1. **Narrowed this round, round 6's I2:
   this parenthetical had also claimed clearance of the 4.5:1 text floor —
   light's 4.19:1/3.70:1 does not meet it, but `outline` is a
   component-boundary role that only owes 3:1, so this does not change
   the token's disposition; the reference doc already stated only the 3:1
   claim.**). Revision 4 (round 2's I1) had assigned it `#3f424d`/
   `#cfd3e5` so CP3's card/stepper/scale-row/neutral-outline-button borders
   would read a hairline value instead of Material 3's default — but
   `outline` is also the resting border of all 23 `OutlinedTextField`s
   (`OutlinedTextFieldTokens.OutlineColor`) and both `Switch`es
   (`SwitchTokens.UnselectedHandleColor`/`.UnselectedFocusTrackOutlineColor`
   — **corrected round 17's O1, was misstated as `.UnselectedTrackOutlineColor`,
   also `Outline`, so no value changes**),
   at 1.2–1.8:1 in both themes — an unfocused text field's only affordance
   reduced to an all-but-invisible rectangle, across screens this
   milestone mostly never touches or verifies. `RepFlowColor.hairline`
   (dark `#3f424d` / light `#cfd3e5` — the same two values, now exposed as
   their own token rather than through a role every stock text field and
   switch also reads) is what CP3's four hairline-consuming primitives
   read instead — see CP3's "Cards" bullet below.

   **Light**: `primary` = **accent-700 `#5d5294`** (not the base accent hex
   — confirmed this session by reading the live light-theme screen
   directly), **`onPrimary` = `#f5f4ff`** — **corrected this round (I1)
   from `#161826`**: round 2's I1 fix assigned `#161826` "on both themes",
   justified by CP3's primary-button spec — but that spec (`6b`) is
   dark-theme-only (round 2's own I2 disposition established this), and
   light's `primary` is a different value (accent-700, not dark's base
   accent), so its label cannot be inherited from a dark-only source. Read
   directly from `1d`'s two primary-button renders ("Start workout" and
   "Create a plan," both `background:#5d5294;color:#f5f4ff`): `#161826` on
   `#5d5294` is 2.60:1, failing WCAG AA (4.5:1) and even the 3:1
   UI-component floor, on the app's single most prominent control;
   `#f5f4ff` on `#5d5294` (accent-100 off the already-tabulated ramp) is
   6.22:1. `background` = neutral-200 `#e4e7f5`, `surface` = neutral-100
   `#f3f5fe`, `onBackground`/`onSurface`
   = neutral-900 `#292b31`, `error` = **`#a74541`** (`oklch(0.52 0.13 25)`'s
   resolved sRGB — **corrected this round (B1) from `oklch(0.55 0.13
   25)`'s `#b14e49`**: `error` is the **text** color of an `isError`
   `OutlinedTextField`'s label/supporting line
   (`OutlinedTextFieldTokens.ErrorLabelColor`/`.ErrorSupportingColor`), not
   just its border/icon, and the app has 10 `isError`-paired
   `OutlinedTextField`s whose `supportingText` renders a real validation
   string (`ExerciseEditorFormFields.kt`, `TrainingPlanEditorFormFields.kt`)
   on either bare `background` (`#e4e7f5`) or the Material-3-baseline
   `surfaceContainerHighest` card `TrainingPlanEditorFormFields.kt`'s row
   `Card` resolves to (`#E6E0E9`, unassigned by this plan) — `#b14e49`
   measured 4.20:1 on the former and 4.00:1 on the latter, both below the
   4.5:1 WCAG AA text floor this same step already applies to light
   `onPrimary`. Walking the same OKLCH lightness axis one step further,
   `#a74541` clears both: 4.77:1 on `background`, 4.54:1 on the card. A
   distinct, lower-lightness value from dark's error
   color, not the same token reused), `RepFlowColor.control` = neutral-400
   `#b2b6ca` (unchanged from revision 4 — round 2's I2 disposition above
   still holds: no design-confirmed light-theme `control` value exists, so
   this remains a disclosed judgment call, not a design-derived fact).
   Light `outline` is likewise deliberately not assigned, for the same
   reason as dark's — see the `outline`/`RepFlowColor.hairline` note
   above.

   **The `NavigationBarItem`/`NavigationBar` roles (round 3's B1,
   corrected for `onSurfaceVariant` round 5's B1, corrected for
   `secondaryContainer`/`onSecondaryContainer` this round's B2).** CP4's
   mechanism only holds if CP1 assigns every role a stock
   `NavigationBarItem` actually reads. Re-derived from
   `material3-android-1.4.0`'s sources (`NavigationBarTokens.kt:24-34`,
   `NavigationBar.kt:383-397`) and confirmed pixel-exact against the live
   design's own bottom-nav renders (`1a` dark, `1d` light — both show the
   same six-item bar, one item selected). Two of the five roles the nav
   reads are assigned globally, via the `ColorScheme` cascade:
   - `secondary` (selected label): dark `#e9e9ed`, light `#292b31` — both
     reused from `onBackground`/`onSurface` above; `1a`'s/`1d`'s selected
     label is plain text, not accent-colored.
   - `surfaceContainer` (bar container fill): dark = **new literal
     `#1b1d2b`** (`1a`'s own bar background — distinct from `background`
     `#161826`, `surface` `#232532`, and the Material 3 baseline
     `surfaceContainer` `#211F26` alike; not derivable from any
     already-assigned token, so named as its own value here), light =
     `surface` (`#f3f5fe`) — `1d`'s bar background is literally the same
     fill as every other light-theme surface. This is a deliberate choice,
     re-confirmed round 5 (I1): light's bottom nav genuinely renders no
     visible container line against the screen behind it in the live
     design, and this value also cascades to `DropdownMenu`'s container
     (see "Known limitations" and CP7) — disclosed, not an oversight.

   The remaining three roles are scoped to CP4's own
   `NavigationBarItemDefaults.colors(...)` override, not assigned through
   the global `ColorScheme` cascade:

   - `onSurfaceVariant` (unselected icon+label) — **corrected round 5 (B1):
     scoped to CP4, not assigned globally.** Round 3's B1 originally
     assigned it globally too, on the reasoning that one cascade should
     serve every stock consumer of a role, not just `NavigationBarItem`.
     Round 5 found that reasoning does not hold for this specific role:
     `onSurfaceVariant` is also
     `OutlinedTextFieldTokens.LabelColor`/`.InputPlaceholderColor`/
     `.SupportingColor`, `ListTokens.ListItemSupportingTextColor`/
     `.ListItemOverlineColor`, `DialogTokens.SupportingTextColor`,
     `OutlinedButtonTokens.LabelTextColor`, and
     `AppBarTokens.TrailingIconColor` (**corrected round 15's B2:
     `TextButtonTokens.LabelColor` removed — it is dead code in material3
     1.4.0, referenced nowhere outside its own token file; `TextButton`'s
     enabled label reads `fromToken(ColorSchemeKeyTokens.Primary)` instead,
     `Button.kt:784` — so `TextButton` is a `primary` consumer, not an
     `onSurfaceVariant` one; `AppBarTokens.TrailingIconColor` added, a
     consumer round 16's B1 removes again below**) — the app's default label/
     supporting-text color for 23 `OutlinedTextField`s,
     every `ListItem` with `supportingContent`, every `AlertDialog`'s body
     text, and 5 `OutlinedButton`s — **corrected round 16's B1: "and 10
     `TopAppBar` trailing icons" removed, zero live consumers, see CP1 step 7
     role table below** — where the design's 60%/55%-opacity
     value composits to 3.20–3.34:1 on `surface`/`background`/dialog
     `surfaceContainerHigh` — below the 4.5:1 WCAG AA floor this same step
     already applies to light `onPrimary`. `onSurfaceVariant` is therefore
     left unassigned (Material 3 baseline: **`#CAC4D0` dark / `#49454F`
     light** — **corrected round 6's B2: this parenthetical had misstated
     `outline`'s own `#938F99`/`#79747E` baseline here instead** —
     independently confirmed to clear 4.5:1/3:1 against every
     surface/background pair in both themes: light 8.59:1/7.59:1 on
     surface/background, dark 8.91:1/10.33:1). CP4 instead applies the
     design's unselected-item treatment directly, scoped to the bottom nav,
     via `NavigationBarItemDefaults.colors(unselectedIconColor = …,
     unselectedTextColor = …)` — dark `Color(0xFFE9E9ED).copy(alpha =
     0.6f)` (`1a`: `rgba(233,233,237,.6)`), unchanged; **light corrected
     this round (B1 — corrected round 8's O1, this bullet had misattributed
     it to B2)** from `Color(0xFF292B31).copy(alpha = 0.55f)`
     (`1d`'s literal `rgba(41,43,49,.55)`) **to `Color(0xFF292B31)
     .copy(alpha = 0.66f)`** — round 5's fix relocated the design's
     55%-opacity value into CP4 rather than eliminating it, and it still
     fails there: the bar's own container is `surfaceContainer` = `surface`
     = `#f3f5fe`, and `#292b31` at 55% over `#f3f5fe` composites to
     `#84868d`, 3.34:1 against the container — clearing the 3:1 icon floor
     but not the 4.5:1 floor the always-visible label text
     (`RepFlowBottomNavigationBar.kt:36-42` (**corrected round 15's O2, was
     misstated as `:35-41`**) passes a real `label = {
     Text(…) }`; `NavigationBarItem`'s `alwaysShowLabel` defaults to `true`,
     `NavigationBar.kt:203`) owes. Alpha `.66` composites to `#6e7077`,
     4.55:1 — the first step that clears 4.5:1 (`.65` composites to
     ~4.42:1, still short). A single shared value is kept for both icon and
     label, a design-vs-accessibility deviation from the literal `.55` on
     the same footing as light `onPrimary`/`error` above (see the round 7
     disposition above and the reference doc's bottom-nav table, which
     keeps `1d`'s literal `.55` as the confirmed design source and states
     `.66` as the corrected applied value).
   - `secondaryContainer`/`onSecondaryContainer` (selected indicator pill /
     selected icon) — **corrected this round (B2): scoped to CP4, not
     assigned globally.** Values unchanged from the design-confirmed
     render: `onSecondaryContainer` dark `#d2cefd`
     (`RepFlowColor.accent300` — `1a`'s selected-icon color), light
     `#5d5294` (reused from `primary` — `1d`'s selected icon is the same
     color as the primary-button fill); `secondaryContainer` dark =
     `primary` (`#9184d9`) at 20% opacity (`1a`: `rgba(145,132,217,.20)`),
     light = `primary` (`#5d5294`) at 16% opacity (`1d`:
     `rgba(93,82,148,.16)`). These composite to **8.19:1 (dark, corrected
     round 8's B1 from 8.68:1 — the prior figure had been reproduced against
     `background` `#161826` rather than the nav's own `surfaceContainer`
     `#1b1d2b`) / 4.96:1 (light)** against the nav's own container and were
     correctly design-confirmed at revision 6 — the problem this round's B2
     found is
     not with these values but with assigning them **globally**:
     `FilterChipTokens`'s selected-state colors read the same two roles
     (`FilterChipTokens.kt:43,59`), so three untouched screens' stock
     `FilterChip`s picked up this pair too, composited against
     `background` instead of the nav's own `surface` — `#5d5294` at 16%
     over `#e4e7f5` composites to `#cecfe5`, and the selected label on it
     measures 4.41:1, below 4.5:1. Unlike `onSurfaceVariant` above, no
     value here needs correcting — the nav's own pixel-confirmed pair
     already passes everywhere it is actually design-confirmed, and CP3's
     `RepFlowTag`/`RepFlowCard` (what this milestone's own primitives
     actually use for tags/chips — see CP3's "Cards" bullet) never read
     `secondaryContainer` either, so nothing this milestone authors needs
     the role assigned globally at all. Resolved by moving both roles into
     CP4's own `NavigationBarItemDefaults.colors(...)` override (see CP4
     below) via its `indicatorColor`/`selectedIconColor` parameters —
     confirmed present (`NavigationBar.kt:360-366`) and confirmed to fall
     back to the cascade default via `Color.Unspecified`/`takeOrElse` when
     omitted (`NavigationBarItemColors.copy`, `NavigationBar.kt:451-468`),
     so this does not disturb `selectedTextColor`'s already-correct
     `secondary`-role behavior. This retires revision 6's disclosed
     `FilterChip`-cascade limitation entirely: the three untouched screens'
     chips revert to Material 3's own opaque `Secondary90`/`Secondary10`
     (light) baseline pair, comfortably clearing AA.

   See CP4 below for the resulting override.

   **`surfaceContainerHigh` — new revision 7, closes round 5's I2; value
   corrected round 10, B1.** Assigned dark `surface` (`#232532`) / light
   `surface` (`#f3f5fe`) — both themes now reuse the same value already
   assigned to `surface` — rather than left at Material 3's baseline
   (`#2B2930` dark / `#ECE6F0` light, `PaletteTokens.Neutral17`/`.Neutral92`),
   which is read by the app's 4 `AlertDialog`s and 2 `DatePickerDialog`s
   (`DialogTokens.ContainerColor`/`DatePickerModalTokens.ContainerColor`)
   regardless of this milestone's own scope — unlike `surfaceContainerHighest`/
   `inverse*` below, nothing about leaving it unassigned would be a
   pre-existing, undisturbed limitation: six live, pre-existing dialogs
   would otherwise render on an off-palette default in a milestone whose
   stated leverage is a coherent, fully re-themed app, and even Material
   3's own baseline does not clear the floor below against this
   milestone's own `primary` (`#9184d9` dark on `#2B2930` = 4.45:1,
   independently checked — still short).

   **Originally assigned dark `RepFlowColor.control` (`#292b31`, revision
   7)** as a disclosed judgment call — no dialog is rendered in either
   theme in the live design to confirm a literal value from, the same
   footing as light `control` above. Round 7's I1 found this drops
   `DatePickerModalTokens.DateTodayLabelTextColor = Primary`, wired through
   `DatePicker.kt`'s default `todayContentColor`, to 4.38:1 (dark `primary`
   `#9184d9` on `#292b31`) — 2.7% short of the 4.5:1 text floor —
   milestone-introduced (Material 3's own dark baseline pair clears the
   floor) — and, believing only the date picker's "today" label was
   affected, disclosed it rather than re-tuning.

   **Corrected round 10 (B1): the affected set is nine labels across four
   dialogs, not one glyph.** `DialogTokens.ActionLabelTextColor = Primary`
   (`DialogTokens.kt:26`) puts every dialog's `TextButton` action label on
   the same container — Compose's `TextButton` content color resolves to
   `fromToken(ColorSchemeKeyTokens.Primary)` (`Button.kt:784`) — and four
   dialogs use `TextButton` for their actions (the other two,
   `ExerciseEditorScreen.kt:161`/`TrainingPlanEditorScreen.kt:121`, use
   filled `Button`, whose `onPrimary`-on-`primary` label is unaffected):
   `HistoryScreen.kt:176`'s invalidate-session `AlertDialog` (confirm
   `:181`, cancel `:191`), `BackupScreen.kt:62`'s restore `AlertDialog`
   (confirm `:67`, cancel `:72`), `HistoryScreen.kt:361`'s
   `DatePickerDialog` (confirm `:364`, clear `:376`, dismiss `:382`), and
   `RecoveryFutsalScreen.kt:207`'s `DatePickerDialog` (confirm `:210`,
   dismiss `:223`) — all nine measuring the identical 4.38:1, two of the
   four dialogs being destructive confirmations (invalidating a recorded
   workout session; restoring a backup over live data). The dialog's other
   two text roles were also previously misstated as a single
   `onSurface`-11.69:1 figure: `HeadlineColor = OnSurface` is 11.69:1, but
   `SupportingTextColor = OnSurfaceVariant` (`DialogTokens.kt:34`) is a
   separate role, baseline `#CAC4D0` dark, measuring 8.30:1 against the
   same container — comfortable, but not `onSurface`.

   **Re-decided**: dark `surfaceContainerHigh` is reassigned `= surface`
   rather than left at `RepFlowColor.control`. This clears every affected
   label (dark `primary` on `#232532` = **4.71:1**) and every other
   consumer (`onSurface` headline `12.55:1`; `onSurfaceVariant` supporting
   text `8.91:1`, both re-derived against the new value); light is
   unchanged (light `surfaceContainerHigh` already equalled light
   `surface`, so light `primary` stays at `6.23:1`). The cost is retiring
   the "raised tier" visual distinction round 5's I2 deliberately
   introduced between the dialog container and `surface` — both themes now
   apply the identical rule. See CP1 step 7 (new computed assertion),
   "Known limitations," and CP7's manual pass (now naming all four
   `TextButton`-based dialogs, not just the date picker).

   **`surfaceContainerHighest` — dark reassigned this round (B1), light
   left unassigned.** `Card`'s call site is pre-existing and undisturbed
   (the app's one bare `Card(`, `TrainingPlanEditorFormFields
   .kt:123`) — but the container's value is not, and neither is its own
   separation from the page ground behind it (**disclosed under "Known
   limitations": a filled `Card` has no border and no shadow, so this is
   the row's only visual edge**). Its *content* is this milestone's own
   too: the row's 4
   `TextButton` labels and 7 focused `OutlinedTextField` labels/carets all
   read `primary` (round 15's B2: `TextButton`'s label is `primary`, not
   `onSurfaceVariant` — see above), and dark `primary` (`#9184d9`) on the
   Material 3 baseline container (`#36343B`, `PaletteTokens.Neutral22`)
   measures **3.80:1** — 15.5% short of the 4.5:1 text floor, milestone-
   introduced (Material 3's own dark pair clears at 7.20:1 on the same
   container). This is round 10's B1 one container further out, and the
   same argument applies: the `Card` is live and pre-existing, and this
   milestone changes what renders on it. Resolved the same way round 10's
   B1 resolved `surfaceContainerHigh` one tier down: **dark
   `surfaceContainerHighest` is reassigned `= surface` (`#232532`)** —
   `primary` then measures **4.71:1** ✓, and no new file is touched, only
   one more literal in this token file. Dark `error` on the same container
   moves to **5.80:1** (**corrected round 16's I1: was 4.68:1 against the
   Material 3 baseline this reassignment replaces; still clear**). **Light is left unassigned**,
   at Material 3's baseline `#E6E0E9` — light `primary` already measures
   5.23:1 there, clearing without a reassignment, and leaving it alone
   avoids moving the ground round 6's B1 measured light `error` against
   (`#E6E0E9`, stated explicitly at CP1 step 2's light-`error` derivation
   and CP1 step 7's assertion below — reassigning light too would require
   restating both). See CP1 step 7 (new computed assertion), "Known
   limitations," "Areas the reviewer should specifically challenge," and
   CP7's manual pass (now naming this card explicitly).

   `Snackbar`'s three `inverse*` roles remain deliberately **not**
   assigned: no primitive this milestone actually authors reads them (the
   snackbar pattern is unchanged by this milestone) — see CP3's
   `RepFlowCard`/`RepFlowTag` note and CP5's Known Limitations entry.
   Assigning global values for a role nothing here exercises would repeat
   this same class of finding one level further out, guessing at values
   with no component to verify them against.

   `RepFlowColor.accent300` = `#d2cefd`, `.accent600` = `#796cbf`,
   `.accent700` = `#5d5294`, `.accent900` = `#2b2741` — **added revision 5,
   closes round 3's I2**: the four accent-ramp steps CP3's primitives consume
   (accent-outline border/text, scale-row selected border/text, the
   accent-tinted card fill/border — see CP3) that have no `ColorScheme`
   role of their own. These are theme-independent ramp constants, not
   per-theme `ColorScheme` fields — which step a given theme's variant of a
   primitive uses is stated per-component in CP3 (see I3 below), not baked
   into the token itself.

   `RepFlowColor.navUnselectedAlphaDark = 0.6f`, `.navUnselectedAlphaLight
   = 0.66f`, `.navSelectedIndicatorAlphaDark = 0.20f`,
   `.navSelectedIndicatorAlphaLight = 0.16f` — **added this round (I1)**:
   named constants for the four opacity multipliers CP4's own
   `NavigationBarItemDefaults.colors(...)` override applies (see CP4
   below). These were previously bare literals inside that `@Composable`
   call site, which CP1 step 7's plain-JVM `RepFlowThemeTest` cannot read
   — naming them here lets both `RepFlowBottomNavigationBar.kt` and
   `RepFlowThemeTest` reference the same constant instead of each
   restating the value independently.

   Every other `ColorScheme` role — including `outline` and
   `onSurfaceVariant` (round 5's B1/B2; see above), `secondaryContainer`/
   `onSecondaryContainer` (this round's B2; see above), `outlineVariant`
   (**named explicitly starting round 9's I2(a) — was already unassigned,
   the table row and this mention are disclosure, not a value change**),
   and `inverse*` — is deliberately left at the
   Material 3 baseline: either no stock component this milestone's surfaces
   actually render reads it (`inverse*`), or (for `outline`/`onSurfaceVariant`/
   `secondaryContainer`/`onSecondaryContainer`/`outlineVariant`, and —
   **corrected round 16 (I3)** — light `surfaceContainerHighest`, which the
   app's one bare `Card(` does read, the same as dark's reassigned value
   above) the
   baseline value is itself the correct, accessible choice and the design's
   own distinct value is scoped to the one place (the bottom nav, via CP4's
   per-item override), exposed via its own token (`RepFlowColor.hairline`),
   or — for `outlineVariant`'s own design-confirmed divider value — left
   unmapped this milestone (round 9's I2(b); see "Known limitations")
   instead of the shared role.

   **Role → consumers → value → per-context ratio table — new this round
   (I2, applying round 6's deferred Architecture recommendation).** Round 6
   recommended folding the colour half of step 5's role-consumer audit into
   this step, as a table cited by prose rather than restated; this is the
   seventh consecutive review round whose findings trace to the same two
   mechanical inputs the table would hold, so it is applied now rather than
   deferred an eighth time. Two rules this table carries, both learned from
   this round's own findings: an **alpha** role has one ratio per
   background — state each, not one; a value **scoped** to a component
   still owes its floor inside that component — scoping relocates the
   value, it does not exempt it. This table restates only the roles and
   ratios already established above (and in round-history); it introduces
   no new value.

   | Role | Consumers (stock Material 3, unless noted) | Value | Context → ratio | Floor |
   | --- | --- | --- | --- | --- |
   | `primary` | `Button` fill, nav selected-icon accent, `TextButton` accent contexts elsewhere (**corrected round 15's B2: `OutlinedButton` removed — its label is `onSurfaceVariant`, `OutlinedButtonTokens.LabelTextColor`, `Button.kt:740`, see the `onSurfaceVariant` row below; the `FilterChip` clause removed too — `primary` is `FilterChipTokens.UnselectedLeadingIconColor` (`FilterChipTokens.kt:77`), not the chip's *selected* icon (`OnSecondaryContainer`, `:72`), and inert either way since none of the six stock sites passes a `leadingIcon`, round 8's I2**); `ProgressIndicatorTokens.ActiveIndicatorColor` (`CircularProgressIndicator`, 8 stock sites app-wide, none passing `color =` — added round 9's O1); **added round 10's O1**: `SwitchTokens.SelectedTrackColor` (`Switch`, 2 sites: `ActiveWorkoutExerciseCard.kt:129`, `RecoveryFutsalScreen.kt:255`), `CheckboxTokens.SelectedContainerColor` (`Checkbox`, 1 site: `TrainingPlanEditorFormFields.kt:161`), and `OutlinedTextFieldTokens.FocusOutlineColor`/`.FocusLabelColor`/`.CaretColor` (focused state of the 23 `OutlinedTextField`s already counted below); **added round 15's B1**: the same `TrainingPlanEditorFormFields.kt` row `Card`'s 4 `TextButton` labels and 7 focused `OutlinedTextField` labels (**corrected round 16's I4, was misstated as "(unfocused)"**) — see the `surfaceContainerHighest` row below | dark `#9184d9`, light `#5d5294` (accent-700) | text-role ratios below; `CircularProgressIndicator` clears the 3:1 graphical-object floor comfortably: dark on `background` 5.45:1, light on `background` 5.50:1; `Switch`/`Checkbox` track/container fills clear the same 3:1 floor; the focused-field label clears 4.5:1: dark on `background` 5.45:1/on `surface` 4.71:1, light on `background` 5.50:1/on `surface` 6.23:1; the card's `TextButton`/field labels: see the `surfaceContainerHighest` row below | n/a (fill) / 4.5:1 (focused label / card content) |
   | `onPrimary` | `Button` content color; **added round 10's O1**: `SwitchTokens.SelectedHandleColor` (`Switch`), `CheckboxTokens.SelectedIconColor` (`Checkbox`) — same sites as the `primary` row above | dark `#161826`, light `#f5f4ff` | dark on `primary`: 5.45:1; light on `primary`: 6.22:1 (handle-on-track and icon-on-container reuse this same pair) | 4.5:1 (text) |
   | `background` | `Scaffold`'s root canvas fill (global, every screen) | dark `#161826`, light `#e4e7f5` | container fill only — no text renders directly on it without an intervening content-color role already covered elsewhere | n/a (fill) |
   | `surface` | **corrected round 9's I1(a) — was `Card`, which does not read this role:** `ListItem` container fill (`ListTokens.ListItemContainerColor`, 6 of 6 stock call sites — no bare `Surface(` call site exists in this app, and `Card`'s default reads `surfaceContainerHighest` instead, per `FilledCardTokens.kt:24` and CP3's "Cards" bullet); **added round 10's I2(a):** `TopAppBar` container fill (`AppBarTokens.ContainerColor`, `AppBarTokens.kt:26`, wired through `TopAppBarDefaults.defaultTopAppBarColors`, `AppBar.kt:1513` — 10 of 10 stock `topBar = TopAppBar(...)` sites app-wide, one per screen, none passing `colors =`) | dark `#232532`, light `#f3f5fe` | container fill only — no text renders directly on it without an intervening content-color role already covered elsewhere (the `ListItem` row's own text is `onSurface`, already measured in the row below; the top bar's title is `AppBarTokens.TitleColor = OnSurface`, already covered; nine of the ten bars pass no `actions`, so `AppBarTokens.TrailingIconColor` has no live consumer at all — **corrected round 16's B1**; the tenth, `RecoveryFutsalScreen.kt:93`'s "View history" action, is a `TextButton`, reading `primary` instead — dark 4.71:1, light 6.23:1 against this same `surface` fill, already covered by the `primary` row above) | n/a (fill) |
   | `onBackground`/`onSurface` | default body-text color for any `Text` on `Scaffold`'s background or a bare `Surface`/`Card` (global) | dark `#e9e9ed`, light `#292b31` | dark on `background` `#161826`: 14.54:1; on `surface` `#232532`: 12.55:1; on `RepFlowColor.control` `#292b31`: 11.69:1; on nav `surfaceContainer` `#1b1d2b`: 13.79:1. Light on `surface` `#f3f5fe`: 13.01:1; on `background` `#e4e7f5`: 11.49:1 | 4.5:1 (text) |
   | `error` | `OutlinedTextField` `isError` label/supporting text (`ErrorLabelColor`/`.ErrorSupportingColor`) | dark `#eb827b`, light `#a74541` | light on `background` `#e4e7f5`: 4.77:1; light on card `surfaceContainerHighest` `#E6E0E9`: 4.54:1 (**dark's equivalent card is now `surface` after round 15's B1 — see the `surfaceContainerHighest` row below, corrected round 16's I1**); dark on `surface`/`background`: 5.80:1/6.72:1 | 4.5:1 (text) |
   | `outline` | `OutlinedTextField`/`Switch` resting border (component boundary only — see `RepFlowColor.hairline` below for the design's own hairline value, read by CP3's primitives instead) | Material 3 baseline: dark `#938F99`, light `#79747E` (unassigned) | dark on `surface`/`background`: 4.80:1/5.56:1; light: 4.19:1/3.70:1 | 3:1 (component boundary, not text) |
   | `outlineVariant` | **new row, round 9's I2(a).** `FilterChipTokens.FlatUnselectedOutlineColor` (4 of 6 stock `FilterChip` sites — the other 2, `ExerciseListScreen.kt:141,147`, are rebuilt as `RepFlowTag` by CP5 and no longer read this role; see CP3's "Cards" bullet); `DividerTokens.Color` (3 stock `HorizontalDivider()` sites: `HistoryScreen.kt:166`, `HistoryDetailScreen.kt:94`, `RecoveryFutsalScreen.kt:145`) | Material 3 baseline: dark `#49454F`, light `#CAC4D0` (unassigned — see "Known limitations" for the design's own distinct divider value and its disposition) | `FilterChip` unselected outline, light `#cac4d0` on `background` `#e4e7f5`: **1.38:1** (narrowed from today's 1.62:1 on `#FEF7FF` — see CP3's "Cards" bullet); dark `#49454f` on `background` `#161826`: **1.88:1** (from today's 1.99:1 on `#141218`). Both are pre-existing, decorative/component-boundary uses — 1.4.11 does not apply | n/a (decorative/component boundary) |
   | `onSurfaceVariant` | `OutlinedTextField` label+supporting/`ListItem` supporting/`AlertDialog` supporting/`OutlinedButton` label (23/6/4/5 sites — **corrected round 15's B2: `TextButton` removed, its label is `primary` (`Button.kt:784`), see that row above; the "`TopAppBar` trailing icon" term round 15's B2 added here is removed again, corrected round 16's B1: `AppBarTokens.TrailingIconColor` reaches only a bar's `actions` slot, and the one stock `topBar = TopAppBar(...)` site that passes `actions =` (`RecoveryFutsalScreen.kt:93`) wraps a `TextButton`, itself `primary` per the correction above — zero live `TopAppBar` consumers**) — **and**, scoped to CP4 only, `NavigationBarItem` unselected icon+label | Material 3 baseline (unassigned, global): dark `#CAC4D0`, light `#49454F`. CP4-scoped override (not the global role): dark `Color(0xFFE9E9ED)@.6`, light `Color(0xFF292B31)@.66` (**corrected this round from `.55`, B1**) | baseline dark on `surface`/`background`: 8.91:1/10.33:1; baseline light: 8.59:1/7.59:1. CP4 composite: dark `.6` over nav `#1b1d2b` → `#97979f`, 5.76:1; light `.66` over nav `#f3f5fe` → `#6e7077`, 4.55:1 | 4.5:1 (text) |
   | `secondaryContainer`/`onSecondaryContainer` | scoped to CP4 only: `NavigationBarItem` selected-indicator pill / selected icon (stock `FilterChip` deliberately does **not** read this role after this round's B2 — see above) | dark `primary@.20`/`#d2cefd`, light `primary@.16`/`#5d5294` | dark composite over the nav's own `surfaceContainer` `#1b1d2b`: **`#33324e`, label 8.19:1** (**corrected round 8's B1 from composite `#2f2e4a`/8.68:1**, which had blended against `background` `#161826` instead of the nav's own container); light composite `#dbdbed`, label 4.96:1 | 4.5:1 (text) |
   | `secondary` | `NavigationBarItem` selected label (global); **added round 9's O1**: also `FocusIndicatorColor` on 13 stock component families (`Switch`/`FilterChip`/`SuggestionChip`/`TimeInput`/`FilledCard`/`ElevatedCard`/`SearchBar`/`Menu`/`NavigationDrawer`/`InputChip`/`SheetBottom`/`List`/`AssistChip`) and `DialogTokens.IconColor` (no `AlertDialog` in this app passes `icon =`) | dark `#e9e9ed`, light `#292b31` (= `onBackground`/`onSurface`) | dark on nav `surfaceContainer` `#1b1d2b`: **13.79:1** (**corrected round 8's I1(a) from "clears by inheritance"**); light on nav `surface` `#f3f5fe`: **13.01:1**; focus ring: dark on `background` 14.54:1, light 11.49:1 | 4.5:1 (text) |
   | `surfaceContainer` | `NavigationBarItem` bar fill (global); incidentally, 7 stock `DropdownMenu` sites (`MenuTokens.ContainerColor`) | dark new literal `#1b1d2b`, light `surface` `#f3f5fe` | container fill only — no text renders directly on it without an intervening content-color role already covered elsewhere | n/a (fill) |
   | `surfaceContainerHigh` | 4 `AlertDialog`s, 2 `DatePickerDialog`s (`DialogTokens`/`DatePickerModalTokens.ContainerColor`) — of which 4 use `TextButton` actions (`DialogTokens.ActionLabelTextColor = Primary`) and 2 use filled `Button` actions (`onPrimary` on `primary`, unaffected) | dark `surface` `#232532` (**corrected round 10's B1 from `RepFlowColor.control` `#292b31`**), light `surface` `#f3f5fe` (unchanged) | dialog `onSurface` headline on dark: 12.55:1; `onSurfaceVariant` supporting text on dark: 8.91:1; `TextButton` action label/date-picker "today" `primary` on dark: **4.71:1 (was 4.38:1 against `RepFlowColor.control` — round 10's B1 fix)**; light `primary` on light: 6.23:1 (unchanged) | 4.5:1 (text) |
   | `primaryContainer`/`onPrimaryContainer` | `FloatingActionButton` (2 sites: `ExerciseListScreen.kt:110`, `TrainingPlanListScreen.kt:98`) | unassigned — Material 3 baseline both sites (disclosed two-FAB palette split, round 6's I1) | not computed — disclosure, not a fix, this milestone | n/a (disclosed limitation) |
   | `surfaceContainerHighest` | the app's one bare `Card(` (`TrainingPlanEditorFormFields.kt:123`, `PlannedExerciseRow`'s row card) — the call site is pre-existing and undisturbed, but **corrected round 15's B1: its content is not, and (round 17's I1) neither is the container's own value or its separation from the page ground it sits on** — the card's body (`:123-165`) contains this milestone's own `primary`-colored content: 4 `TextButton` labels (`:228` exercise picker, always enabled; `:258`/`:263` move up/down, `enabled`-gated; `:268` remove, always enabled) and 7 focused `OutlinedTextField` labels/carets (`:129`/`:139`/`:150` plus `TargetRangeFields`' four); the `Checkbox` fill at `:161` also lands here (owes only the 3:1 non-text floor, clears either way); **added round 16 (I2)**: `SwitchTokens.UnselectedTrackColor` (`:78`) — both stock `Switch`es' unchecked track (`ActiveWorkoutExerciseCard.kt:129`, `RecoveryFutsalScreen.kt:255`), a consumer this role's dark reassignment retints though it was named nowhere before this round | dark **reassigned this round (B1) to `surface` `#232532`** (was Material 3 baseline `#36343B`/`PaletteTokens.Neutral22`), light left unassigned — Material 3 baseline `#E6E0E9` (already clears, no reassignment needed) | dark `primary` on the reassigned container: **4.71:1** ✓ (was **3.80:1** ✗ against the Material 3 baseline — 15.5% short of 4.5:1, milestone-introduced: Material 3's own dark pair clears at 7.20:1 on the same container); dark `error` on the same container: **5.80:1** ✓ (**corrected round 16's I1: the ground moved with the reassignment, from the Material 3 baseline `#36343B` (4.68:1) to `surface` `#232532`; still clear, not "unaffected"**); light `primary` on the unassigned baseline: 5.23:1 ✓ (already clear); **added round 16 (I2)**: dark unchecked `Switch` track on `background`: **1.16:1** (was 1.43:1 against the Material 3 baseline — visual-only; its `Outline`-colored boundary clears the 3:1 floor at 4.80:1 against the new track; see "Known limitations"); **added round 17 (I1)**: the container's *own* separation from `background`, decorative only (no text floor owed) — dark today **1.52:1** (`#36343B` on Material 3's `#141218`), **1.43:1** against this milestone's own `background` baseline, **1.16:1** after the reassignment (`#232532` on `#161826`, the same figure the `Switch`-track entry above states for its own, separate consumer); light today **1.23:1** (`#E6E0E9` on `#FEF7FF`) → **1.05:1** after this milestone's `background` move (`#E6E0E9` on `#e4e7f5` — `surfaceContainerHighest` itself unassigned in light; the narrowing is `background`'s move, not the role's) — see "Known limitations" | 4.5:1 (text) / 3:1 (`Checkbox` fill / `Switch` outline, clears either way) |
   | `inverse*` (`inverseSurface`/`inverseOnSurface`/`inversePrimary`) | `Snackbar` (unchanged message-queue pattern) outside CP3's own primitives (pre-existing, undisturbed) | unassigned — Material 3 baseline | not computed — no primitive this milestone authors reads these | n/a (pre-existing) |

   `RepFlowTheme`'s existing `isSystemInDarkTheme()` switch between the two
   is preserved unchanged — no theme-override setting exists yet (that's
   Settings scope, a later milestone).
3. Author `RepFlowTypography.kt`: Inter, mapped from the six-slot scale
   (display 32/500, title 25/500, numeric 32/500 tabular-figure, body
   15/400, meta 12.5/400, label 11/500 uppercase-tracked) onto Material 3's
   `Typography` slots as closely as they fit, plus a small
   `RepFlowNumericTextStyle` exposed alongside `MaterialTheme.typography`
   for the "numeric" slot Material 3 has no native equivalent for (used by
   loads/reps/timers/volumes in CP6). Every slot stays at 400/500 **except
   the primary-button label, which is a confirmed, singular weight-600
   usage** (`RepFlowButtons.kt`'s primary variant only, authored in CP3) —
   do not generalize 600 into any text style. Bundle Inter as local
   static-weight files — **400, 500, and 600** (600 added in this revision
   for the primary-button exception; revision 1 only planned 400/500) —
   under `res/font/`, not Compose UI's Google Fonts downloadable-font
   provider, so there is no first-use network fetch and no new Gradle
   dependency, consistent with the offline-first architecture rule. Confirm
   Inter's license (SIL Open Font License) permits bundling before adding
   the files.
4. Author `RepFlowShapes.kt` and `RepFlowSpacing.kt` from **RepFlow's own
   literal component-spec values, not the generic Nocturne `styles.css`
   scale** (confirmed this session: no RepFlow screen actually uses the
   generic scale's 4px radius or fractional spacing — see the reference
   doc's "Spacing and radius — corrected, not just filled in"). Radii:
   8dp (`small`, controls) / 10-12dp (`medium`, cards and buttons) / 14-16dp
   (`large`, sheets) — pick single representative values within each named
   range (e.g. 8/12/16) rather than encoding a range as a single token.
   These three named ranges do not cover every radius CP3/CP5 actually
   need — three more values are confirmed, enumerated one-offs, not
   approximations within the ranges above, so name them as their own
   tokens rather than leaving them to be hardcoded per call site:
   `RepFlowShapes.stepper` = 9dp (CP3's 44×44 stepper buttons),
   `RepFlowShapes.fab` = 18dp (CP5's 60×60 FAB), `RepFlowShapes.pill` =
   999dp/`CircleShape` (CP3's status chips and interactive pill rows).
   Spacing: a small `CompositionLocal`-backed token object covering the
   integer values actually observed in the design (6/8/10/12/14/16/18dp),
   not the generic 2.8/5.6/8.4/11.2/16.8/22.4px scale.
5. **New this round (round 5's Missing-tests item 2 / Architecture
   concern).** Before wiring the theme up (step 6) or writing its test
   (step 7), perform an explicit role-consumer audit: grep every
   `androidx.compose.material3` import under `app/src/main/kotlin/`,
   cross-reference each imported component against the
   `material3-android-1.4.0-sources.jar` token files to find which
   `ColorScheme` role(s) it reads, and tabulate — for every role step 2
   assigns *or* deliberately leaves at baseline — its consumers, the
   resulting value, and (for any text or component-boundary role) its
   contrast ratio against the surfaces it renders on. This is exactly the
   audit rounds 3 through 7 each performed by hand, one role (or one
   render context) at a time (round 3's B1: `NavigationBarItem`'s six
   roles; round 4's I1: `FilterChip`'s `secondaryContainer`; round 5's
   B1/B2/I1/I2: `outline`, `onSurfaceVariant`, `surfaceContainer`,
   `surfaceContainerHigh`; round 6's B1/B2/I1/I2: light `error`'s two
   render contexts, `onSurfaceVariant`'s baseline hex, `primaryContainer`/
   `onPrimaryContainer`, `outline`'s over-claimed floor; round 7's B1/B2/
   I1: `onSurfaceVariant`'s CP4-scoped composite, `secondaryContainer`/
   `onSecondaryContainer`'s `FilterChip` cascade, `surfaceContainerHigh`'s
   date-picker composite — see this round's own Architecture finding, I2,
   for why the table below now folds this pattern into step 2 directly) —
   promoted here to an explicit step so a future token edit re-runs it
   deliberately instead of relying on the next plan-review round to find
   the next role by hand. **Corrected this round (B3): record the result
   only as a short table checked in alongside `RepFlowColor.kt` under
   `app/` — never inline in this document.** This document
   (`docs/milestones/repflow-redesign-visual-foundation-execution.md`) is
   this work item's own `plan_stage.protected_paths` entry
   (`repflow-redesign-visual-foundation-artifacts.json`); a checkpoint
   commit editing it would change the plan-stage projection
   `compute_review_content_id_plan_stage_for_work_item` hashes, staling
   `plan_approval` mid-implementation and blocking `/approve-review`/
   `/accept-milestone` until a fresh plan-review round
   (`WORKFLOW_V2_PLAN.md`'s `D-States`) — the guard fails closed, but the
   plan as originally written sanctioned an action that breaks its own
   approval. `app/` carries no such restriction at the plan stage. If the
   audit's *conclusions* genuinely need to reach this document, that is a
   plan revision under `/apply-plan-review`, not a checkpoint edit. So
   step 7's
   test assertions and CP7's manual pass are drawn from that `app/`-resident
   table directly.
   Any newly-discovered consumer or contrast failure this audit finds is a
   step-2 finding, not a step-7 one — fix the token value (or scope the
   treatment, as B1/B2 did) before wiring the theme up, not after.
6. Rewire `RepFlowTheme.kt` to build `MaterialTheme` from these real tokens
   instead of `lightColorScheme()`/`darkColorScheme()` defaults.
7. Add a small `RepFlowThemeTest` (JVM unit test, `app/src/test`) asserting
   the two `ColorScheme`s' key roles resolve to the exact hex values above
   — `primary`, `onPrimary`, `background`, `surface`, `onBackground`/
   `onSurface`, `error`, `secondary`, `surfaceContainer`,
   `surfaceContainerHigh`, for both light and dark, plus light's and dark's
   `RepFlowColor.control` and `.hairline` — this milestone's revalidation
   corrected several of these values from revision 1 (light-theme
   `primary`, both `error` variants, the `control` tier, light `onPrimary`)
   and added several more that plan review found silently defaulting to
   the Material 3 baseline (both themes' `onPrimary` — round 2's I1; both
   themes' `onBackground`/`onSurface` and dark `RepFlowColor.control` —
   round 2's O1; two of the five `NavigationBarItem`/`NavigationBar` roles
   above — round 3's B1; `surfaceContainerHigh` — round 5's I2) — none of
   these roles had any prior implementation to regress-test against, so a
   plain "it compiles" check would not catch a transcription error in the
   token file itself, and an unasserted role is invisible to the compiler
   if it silently keeps its Material 3 default instead of the intended
   value. **`secondaryContainer`/`onSecondaryContainer` are no longer in
   this list (corrected this round, B2)** — they moved out of
   `RepFlowDarkColorScheme`/`RepFlowLightColorScheme` entirely into CP4's
   own scoped override; a `ColorScheme`-level hex assertion for them would
   now just test Material 3's own baseline, not this milestone's code.
   **`outline` and `onSurfaceVariant` are likewise deliberately *not*
   asserted at the `ColorScheme` level** (round 5's B1/B2): both are
   intentionally left unassigned, so pinning them here would only test
   Material 3's own baseline. Also assert light `RepFlowColor.control`
   (`#b2b6ca`) is distinct from light `RepFlowColor.hairline` (`#cfd3e5`) —
   round 2's I2 finding was exactly this pair resolving to the same value
   undetected when both lived on `control`/`outline`; the same collision
   risk carries over to `control`/`hairline` now that `hairline` is the
   border token, so a same-value regression here is still a test-enforced
   fact, not just a doc-level one. **New this round (round 15's
   Missing-tests item 3 — corrected round 16's O2, was misattributed here
   to that round's I1, a different finding)**: also assert `RepFlowColor.accent300`/`.accent600`/`.accent700`/
   `.accent900` resolve to `#d2cefd`/`#796cbf`/`#5d5294`/`#2b2741` — the
   only four values CP1 step 2 authors that this step previously addressed
   neither by assertion nor by a recorded reason to skip one, unlike every
   other role above; a plain "it compiles" check would not catch a
   transcription error in this token file, the same reasoning this step
   already applies to every asserted `ColorScheme` role. **New in revision 7 (round 5's
   Missing-tests item 1)**: also assert a computed relative-luminance
   contrast ratio of light `onPrimary` against light `primary` is ≥ 4.5 —
   not a hex-equality check but an actual contrast computation, so a future
   edit to either value that re-crosses the WCAG AA floor round 3's I1
   established fails the suite directly, rather than only a doc-level
   claim. **New in revision 8 (round 6's Missing-tests item 1)**: the same
   reasoning now extends to light `error`, the other accessibility-sensitive
   role that round's own B1 found still failing: assert a computed
   relative-luminance contrast ratio of light `error` (`#a74541`) against
   light `background` is ≥ 4.5, and against the light editor-card baseline
   `surfaceContainerHighest` (`#E6E0E9`) is ≥ 4.5 too — the second
   assertion because B1's fix must clear both contexts the app's `isError`
   `OutlinedTextField`s actually render on, not just one. Every other
   asserted value above is a concrete, independently-resolved hex, not a
   value the test would compute from the same conversion it is meant to
   be checking — the `error` roles in particular assert against the
   `#eb827b`/`#a74541` hex resolved in step 2 (`#a74541` corrected in
   revision 8 from `#b14e49`, round 6's B1), not a fresh OKLCH→sRGB
   conversion done inside the test.
   **New this round (round 7's Missing-tests item 1): computed
   relative-luminance composite assertions for every CP4-scoped alpha
   value**, replacing the previous exact-`Color`-value assertion for
   `secondaryContainer` (no longer meaningful now that the role is
   CP4-scoped, not a `ColorScheme` field) and extending the same treatment
   to `onSurfaceVariant`'s scoped override, which previously had no test
   coverage at all beyond CP4's own manual pass:
   - light unselected-nav-label composite
     (`RepFlowLightColorScheme.onSurface.copy(alpha =
     RepFlowColor.navUnselectedAlphaLight)` over the nav's
     `RepFlowLightColorScheme.surfaceContainer`, `#f3f5fe`) ≥ 4.5 — pins
     this round's B1 fix so a later alpha edit that re-crosses 4.5:1 fails
     the suite directly, not just this document's own arithmetic.
   - **added round 16 (I5)**: dark unselected-nav-label composite
     (`RepFlowDarkColorScheme.onSurface.copy(alpha =
     RepFlowColor.navUnselectedAlphaDark)` over the nav's
     `RepFlowDarkColorScheme.surfaceContainer`, `#1b1d2b`) ≥ 4.5 — closes
     the one CP4-scoped alpha value this list left with a named constant
     but no composite assertion (`RepFlowColor.navUnselectedAlphaDark`
     composites to `#97979f`, 5.76:1, comfortably clear); without it the
     constant was unread by any test, contradicting this bullet's own
     "every CP4-scoped alpha value" claim.
   - dark selected-nav-pill-label composite (`RepFlowColor.accent300` on
     `RepFlowDarkColorScheme.primary.copy(alpha =
     RepFlowColor.navSelectedIndicatorAlphaDark)`, over the nav's own
     `RepFlowDarkColorScheme.surfaceContainer` **`#1b1d2b`** — **corrected
     round 8's B1 from `background` `#161826`**) ≥ 4.5, and light's
     equivalent (`RepFlowLightColorScheme.primary` on
     `RepFlowLightColorScheme.primary.copy(alpha =
     RepFlowColor.navSelectedIndicatorAlphaLight)` over
     `RepFlowLightColorScheme.surfaceContainer`, `#f3f5fe`) ≥ 4.5
     — both currently pass (**8.19:1**/4.96:1) but are now the *only* test
     coverage these two CP4-scoped values have, since B2's fix removed them
     from the `ColorScheme`-level hex assertions above.
   **Corrected this round (I1): each composite's three inputs are now read
   from production, not restated as test-side literals** — round 8's B1's
   own stated reason for these assertions existing ("a future edit to dark
   `surfaceContainer`… would not be caught by the test that exists to catch
   exactly that") did not actually hold as originally written, because all
   three inputs (base hex, alpha, background) were literals typed directly
   into the test: editing production changed nothing the test read. Now,
   `primary`/`surfaceContainer` are read off `RepFlowDarkColorScheme`/
   `RepFlowLightColorScheme` directly (the same two schemes step 7's other
   assertions above already read), the two accent steps are read off
   `RepFlowColor.accent300`/(light's reused `primary`), and the four alphas
   are read off the named `RepFlowColor.nav*Alpha*` constants CP4 also
   consumes (see CP1 step 2 and CP4 above) — so a future edit to any of the
   four production values (either scheme's `primary`/`surfaceContainer`, or
   either checkpoint's shared constant) is caught by the suite directly, not
   just by this document's own arithmetic. Also assert
   `RepFlowShapes.stepper`/`.fab`/`.pill` resolve to 9dp/18dp/999dp
   alongside the color assertions, for the same reason: these three are
   the only hardcoded-looking radius literals this milestone sanctions, so
   pinning them here is what lets CP7 treat any *other* literal radius
   found by its verification grep as a real regression rather than an
   ambiguous "is this one of the sanctioned ones?" judgment call. This is
   the one checkpoint that adds new JVM unit tests this milestone; CP4/
   CP5/CP6 otherwise rely on already-existing Compose UI tests continuing
   to pass (see "Preserved invariants" above).
   **New this round (round 10's B1's own Missing-tests item)**: also
   assert a computed relative-luminance contrast ratio of dark `primary`
   against dark `surfaceContainerHigh` is ≥ 4.5 — the input B1 found
   unguarded (the existing hex-equality assertion on `surfaceContainerHigh`
   above catches a wrong literal, but not a future edit to `primary` or to
   this role that keeps both hexes internally consistent while silently
   re-crossing the floor). Threshold is fixed at ≥ 4.5 per B1's re-tuned
   value (dark `primary` on dark `surfaceContainerHigh` now `4.71:1`), the
   same "pin the floor, not the pre-fix value" convention round 5/6/7's
   analogous Missing-tests items already established.
   **New this round (round 15's B1's own Missing-tests item)**: the same
   shape, one container further out — also assert a computed
   relative-luminance contrast ratio of dark `primary` against dark
   `surfaceContainerHighest` is ≥ 4.5 — a hex-equality assertion on
   `surfaceContainerHighest` alone (it is now `= surface`, already asserted
   above) would catch a wrong literal but not a later `primary` edit that
   keeps both hexes internally consistent while re-crossing the floor.
   Threshold fixed at ≥ 4.5 per B1's reassigned value (dark `primary` on
   dark `surfaceContainerHighest` now `4.71:1`). Light `surfaceContainerHighest`
   is left unassigned (Material 3 baseline), so — per this same step's own
   standing rule for `outline`/`onSurfaceVariant` above — no assertion is
   possible or appropriate for it: pinning an unassigned role would test
   the library, not this milestone's code.

Because every existing screen already reads `MaterialTheme.colorScheme`/
`MaterialTheme.typography` through stock Material 3 primitives (confirmed —
no screen hardcodes a literal color), this checkpoint alone changes the
app's rendered color and type globally with no other file touched. This is
deliberate leverage, not a scope violation: it changes only what *value* an
existing, already-global theme resolves to.

**Self-review flag**: verify this assumption directly — grep every
`presentation/**/*.kt` composable for a hardcoded `Color(...)` literal
before relying on the cascade; any screen found bypassing
`MaterialTheme.colorScheme` needs a one-line note here (or a corrective
touch, if trivial) rather than a silent gap between "the plan says this
cascades everywhere" and reality. Re-run this grep specifically, not just
re-cited from an earlier round: round 3's B1 finding showed that "the
cascade reaches everywhere" and "every role a stock component reads is
assigned" are two different claims — the grep only verifies the former.

`RepFlowColor.control`/`.hairline`/`.accent300`/`.accent600`/`.accent700`/
`.accent900`, `RepFlowShapes.pill`-adjacent shape tokens, and
`RepFlowNumericTextStyle` sit outside the Material 3
`ColorScheme`/`Typography` containers proper (there is no slot for them) —
`MaterialTheme.colorScheme`/`.typography` stays canonical for every role
Material 3 does have a slot for; `RepFlowColor`/`RepFlowShapes`-the-extra-
tokens are reached for only the handful of roles named explicitly above,
so CP3–CP6 have one unambiguous rule for which access path to use.
`ColorScheme` roles no primitive this milestone authors actually reads
(the three `inverse*` roles — **corrected round 16 (I3): light
`surfaceContainerHighest` moved out of this list, below; it does not
belong here, see that correction**) stay unassigned and at the Material 3
baseline — see CP3's `RepFlowCard`/`RepFlowTag` note and CP5's Known
Limitations entry for why that is a disclosed, pre-existing limitation.
`outline`, `onSurfaceVariant`, (**new
this round, B2**) `secondaryContainer`/`onSecondaryContainer`, and —
**corrected round 16 (I3)** — light `surfaceContainerHighest` (dark is
reassigned this round, B1: the app's one bare `Card(` reads this role in
both themes; light's Material 3 baseline value already clears, so only
dark needs the reassignment — see CP1 step 2 above) are also
unassigned, but for the opposite reason from `inverse*` above (round 5's
B1/B2, this round's B2): stock components *do* read them, and Material 3's
own baseline is the correct, accessible value — the design's own distinct
treatment for these roles is scoped (CP4's per-item override, now covering
all three) or moved to its own token (`RepFlowColor.hairline`) instead of
overriding the shared role.

**New this round (round 6's I1): `primaryContainer`/`onPrimaryContainer`
are also deliberately left unassigned, disclosed rather than the
pre-existing-and-unread case above.** `FloatingActionButtonDefaults
.containerColor`/its `contentColorFor` resolve `PrimaryContainer`/
`OnPrimaryContainer` (`FabPrimaryContainerTokens.kt:22,28`), and the app
has exactly two `FloatingActionButton` call sites, neither passing
`containerColor`: `ExerciseListScreen.kt:110` and
`TrainingPlanListScreen.kt:98`. Unlike `surfaceContainerHighest`/
`inverse*`, this is not a pre-existing, undisturbed limitation: CP5 turns
`ExerciseListScreen.kt`'s FAB into a 60×60dp solid-accent FAB (see CP5
below), while `TrainingPlanListScreen.kt`'s FAB, untouched by any
checkpoint, keeps rendering Material 3's baseline neutral purple
(`#4F378B` dark / `#EADDFF` light) — two top-level list screens, same
affordance, two palettes, after this milestone. Left unassigned rather
than assigned (consistent with `outline`/`onSurfaceVariant`/hairline
above: a design-specific treatment for one component gets its own scoped
token or literal, not a global role) — see CP5 and "Known limitations"
below for the disclosed consequence and CP7 for the added manual-pass
item.

### CP2 — Bounded icon approach

**Files added**: `presentation/designsystem/icons/RepFlowIcons.kt` plus
vector drawable resources.

The current codebase has zero icon-library dependency (bottom-nav icons are
literal letter glyphs rendered via `BasicText`); the target design uses
Phosphor throughout. Rather than add a full icon-library Gradle dependency
for a bounded milestone that only needs a handful of glyphs, bundle just the
icons this milestone's surfaces actually use, as local vector drawables
sourced from Phosphor's icon set (MIT-licensed — confirm before use),
exposed through one `RepFlowIcons` object. This is a decision surfaced for
review, not silently finalized: the alternative (a full icon-library
dependency) is available if the reviewer prefers it, but the bounded-set
approach avoids AGENTS.md's "new dependency categories" stop condition for
a milestone that needs only a handful of icons.

The exact glyph set is now enumerated from direct usage in the live design
(revision 1 left this as "enumerated precisely once CP3's consuming
components are drafted" — it's precise now):

- **Exercise list (CP5)**: `ph-arrow-left`, `ph-magnifying-glass`,
  `ph-x-circle`, `ph-funnel`, `ph-dots-three-vertical`, `ph-archive`,
  `ph-bold ph-plus` (FAB), `ph-arrow-counter-clockwise` (undo snackbar).
- **Active Workout set-entry + rest timer (CP6)**: `ph-minus`, `ph-plus`,
  `ph-fire` (warm-up), `ph-pencil-simple` (edit logged set),
  `ph-arrow-counter-clockwise` (undo last set), `ph-sliders` (expand
  detail), `ph-caret-down`/`ph-caret-up` (detail toggle), `ph-info` (type
  note), `ph-x` (dismiss rest strip).
- **Bottom nav (CP4)**: the design only confirms icons for its *target*
  four destinations (`ph-fill ph-house`, `ph-list-checks`,
  `ph-clock-counter-clockwise`, `ph-chart-line-up`) — but this milestone
  keeps the current **six**-destination nav (Exercises/Workout/Plans/
  Recovery/History/Backup), per the non-goals above. Plans and History map
  directly. Exercises, Workout, Recovery, and Backup have **no
  design-confirmed glyph** for a standalone nav-tab context (Recovery
  appears only as `ph-moon-stars` in an unrelated empty-state, not
  confirmed as its nav icon). CP2 must pick a reasonable Phosphor icon for
  each of these four and note the pick as an unconfirmed judgment call in
  its own self-review, not as a design-derived fact — this is new,
  previously-unflagged scope from this revalidation.

### CP3 — Core reusable primitive components

**Files added**: `presentation/designsystem/components/RepFlowButtons.kt`,
`RepFlowCard.kt`, `RepFlowTag.kt` (covering both static tags/chips and the
interactive full-pill picker pattern — see below), `RepFlowStateComposables.kt`
(consolidated `LoadingIndicator`/`EmptyState`/`FailureState`, replacing the
currently-duplicated per-screen versions found across `presentation/**` —
a concrete, bounded de-duplication that stays inside "a small reusable
primitive set" rather than growing into a general component library).
The real failure composable is `FailureState(onRetry: () -> Unit)` (not
"`ErrorState`" — no such name exists in the codebase); the consolidated
primitive keeps the `onRetry` parameter so the one state where the user has
no other way forward doesn't lose its retry affordance. This checkpoint
does not fully complete the de-duplication on its own: only CP5 removes a
per-screen `LoadingIndicator`/`EmptyState`/`FailureState` set (Exercise
list's); `ActiveWorkoutScreen.kt`'s and `TrainingPlanListScreen.kt`'s own
copies are untouched by this milestone (CP6's scope is the set-entry/
rest-timer surface specifically, not the top-level `ObservationFailed`
branch; `TrainingPlanListScreen.kt` isn't touched at all) — see "Known
limitations".

No existing screen is touched in this checkpoint — new files only.

Corrected/sharpened from revision 1, per the reference doc's direct read of
`6b`'s "Primitives" panel and in-context usage elsewhere.

**Corrected this round (round 5's B2)**: neutral-outline buttons, cards,
the stepper, and the scale row — the four primitives below that render the
design's literal `#3f424d`/`#cfd3e5` hairline — read the new
`RepFlowColor.hairline` token directly, **not**
`MaterialTheme.colorScheme.outline`. `outline` itself is deliberately left
at Material 3's baseline (see CP1 step 2) because it is also the resting
border of every stock `OutlinedTextField`/`Switch` in the app, where the
design's low-contrast hairline value is a real accessibility regression;
`RepFlowColor.hairline` carries the exact same two hex values without
that side effect.

- **Buttons are three tiers, not two** (revision 1 only planned
  primary/secondary): primary (56dp min-height, 12dp radius, solid accent
  fill `#9184d9`, `#161826` text, weight 600, 16sp — the confirmed
  deliberate deviation from Nocturne's outline-only default, preserved as
  the confirmed target visual, not a fresh decision this milestone makes),
  **accent-outline, stated per theme (corrected this round, I3)**: dark
  48dp/10dp radius/`RepFlowColor.accent700` border/`.accent300` text/
  14.5sp (unchanged, sourced from `6b`); light **48dp/10dp
  radius/`RepFlowColor.accent600` border/`.accent700` text/14.5sp** —
  confirmed directly from `1d` (three renders: "Log recovery — 20
  seconds", "Retry", "Empty workout," all
  `box-shadow:inset 0 0 0 1px #796cbf;color:#5d5294`; text 6.23:1, border
  4.10:1 against light `surface`). Applying dark's pair to light instead
  gives 1.38:1 text — effectively invisible; the two themes read different
  ramp steps, not the same pair reused. And neutral-outline (44dp, 8dp
  radius, `RepFlowColor.hairline` border, ~80%-opacity text, 14sp).
- **Stepper primitive** (used by CP6's weight/reps entry): parameterize
  size, since the design itself has two usages — the in-context set-entry
  pad uses 44×44 buttons/`RepFlowShapes.stepper` (9dp radius)/24sp tabular
  value, while the generic spec-panel demo shows a larger 48×48/10dp/32sp
  variant. Default to the in-context (44/24) sizing since that's what CP6
  actually consumes; expose the larger sizing as a parameter rather than
  hardcoding only one.
- **Scale-row / interactive pill picker**: one primitive covers the 0–5
  recovery scale, RPE picker, pain picker, and technique picker (all the
  same horizontal full-pill or fixed-cell row pattern with a selected-state
  accent tint) — do not build these as four bespoke composables. **Stated
  per theme (I3)**: dark selected state = `primary` at 22% opacity fill
  (`rgba(145,132,217,.22)`, per the reference doc's Primitives section)
  with `RepFlowColor.accent600` inset border and `.accent300` text
  (unchanged, sourced from `6b`); light has **no** scale-row render
  anywhere in `1d` to confirm a value from (same situation round 2's I2
  found for light `control`) — until a later milestone's design pass
  confirms one, reuse the confirmed light accent-outline pair
  (`.accent600` border/`.accent700` text, no fill) as the light selected
  state, flagged as a judgment call for the reviewer the same way light
  `control` already is, not a design-derived fact.
- **Status chips**: full-pill (`RepFlowShapes.pill`, 999dp/`CircleShape`),
  multi-state (done/pending/outline/"up next"), always icon-or-word
  alongside color, never color alone. **I3**: like scale-row, `1d` has no
  status-chip render either — the dark "done"/accent-tinted state
  (accent fill + icon, per the reference doc's Primitives section) has no
  confirmed light-theme counterpart; treat it as the same judgment call as
  scale-row's light selected state above until design-confirmed.
- **Cards**: 14-16dp radius, `RepFlowColor.hairline` inset border,
  `#232532` fill by default; the accent-tinted variant uses
  `RepFlowColor.accent900` fill
  (`#2b2741`) / `.accent700` border (unchanged from revision 1, now with
  exact radius confirmed, and the accent pair now named via CP1's new
  ramp tokens per I2). **New this round (B1's "not confined to CP4"
  tail)**: `RepFlowCard` applies these fill/border values explicitly (a
  literal `Modifier.background()`/`border()`, or a stock `Card(colors =
  CardDefaults.cardColors(containerColor = ...))` override — either way,
  never `Card`'s own default color lookup) — so `RepFlowCard` never reads
  Material 3's `surfaceContainerHighest` default (`FilledCardTokens.kt:24`,
  distinct from CP1's `surface` in light, equal to it in dark after round
  15's B1) at all. **Corrected round 16 (I3): CP1
  assigns dark `surfaceContainerHighest` anyway** (round 15's B1; see CP1
  step 2 above) — not for `RepFlowCard`, but for the app's one stock
  `Card(` (`TrainingPlanEditorFormFields.kt:123`), which does read it.
  CP5's filter chips are built on this
  checkpoint's `RepFlowTag` (the "interactive full-pill picker pattern"
  named above), not stock Material 3 `FilterChip` — see CP5, and neither
  `RepFlowTag` nor `RepFlowCard` reads `secondaryContainer`/
  `onSecondaryContainer` — both apply their fill/border explicitly, same
  as the card fill/border above. **Revision 6 (I1) had assigned
  `secondaryContainer`/`onSecondaryContainer` globally** for the bottom
  nav's selected-indicator pill, which incidentally retinted three
  untouched screens' stock `FilterChip`s too (`FilterChipTokens.kt:43`
  reads the same two roles) — `HistoryScreen.kt`, `TrainingPlanListScreen
  .kt`, `ExerciseEditorFormFields.kt`. **Corrected this round (B2): both
  roles are scoped to CP4's own `NavigationBarItemDefaults.colors(...)`
  override instead** (see CP1 step 2 and CP4), because the light
  composite of the global value against these three screens'
  `background` measured 4.41:1, below WCAG AA — and, as this bullet
  already establishes, nothing this milestone's own primitives need the
  role assigned globally for. The three untouched screens' chips now
  revert to Material 3's own baseline pair — that pair itself is unchanged
  and comfortably accessible, but **corrected round 8's I2: their
  separation from the screen behind them is not unaffected by this
  milestone.** This milestone also moves `Scaffold`'s `background`
  (Material 3's own `#FEF7FF` → `#e4e7f5`), which these bare chips render
  directly on, narrowing the light selected chip's fill-vs-ground contrast
  from 1.23:1 to **1.05:1** and the unselected chip's 1dp outline from
  1.62:1 to **1.38:1** — see "Known limitations" and CP7.

### CP4 — Apply foundation to app shell

**Files modified**: `presentation/navigation/RepFlowBottomNavigationBar.kt`,
`presentation/navigation/RepFlowDestinations.kt` (only to change each
destination's icon representation from a literal `String` glyph to a vector
icon reference — the route `String`, `titleRes`, and
`contentDescriptionRes` fields are unchanged).

Swap `NavigationBarItem`'s `icon = { BasicText(destination.icon) }` for the
CP2 icon set (including the four judgment-call icons CP2 flags), rendering
each vector icon with `contentDescription = null` — the accessible name is
already set once, on the `NavigationBarItem`'s own
`Modifier.semantics { contentDescription = ... }` block; giving the icon
its own non-null description would add a second content description onto
the same merged semantics node, one `MainActivityNavHostSmokeTest.kt`
resolves its navigation targets by. Apply CP1 tokens through
`RepFlowTheme`'s cascade for two of the five `NavigationBarItem`/
`NavigationBar` roles CP1 assigns globally (`secondary`,
`surfaceContainer`) — no per-item override needed for those two; before
round 3's fix, this claim was false, those roles were unassigned and this
checkpoint's `NavigationBarItem` would have rendered in Material 3's
baseline purple-grey regardless of CP1's other token work.

**The remaining three roles are read through this checkpoint's own
`NavigationBarItemDefaults.colors(...)` override, not the cascade**
(round 5's B1 for `onSurfaceVariant`; **this round's B2, scoping
`secondaryContainer`/`onSecondaryContainer` here too**):

- `onSurfaceVariant` (unselected icon/label) — CP1 step 2 deliberately
  leaves it at Material 3's baseline because it is also the default text
  color for 23 `OutlinedTextField`s and other stock consumers across the
  app (**corrected round 15's B2: no longer "34 `TextButton`s" — see CP1
  step 2 above**), where the design's opacity value fails WCAG
  AA. `unselectedIconColor`/`unselectedTextColor`: dark
  `RepFlowDarkColorScheme.onSurface.copy(alpha =
  RepFlowColor.navUnselectedAlphaDark)` (`#E9E9ED` @ `.6`); light
  **corrected this round (B1) from `RepFlowLightColorScheme.onSurface
  .copy(alpha = 0.55f)` to `... .copy(alpha =
  RepFlowColor.navUnselectedAlphaLight)`** (`#292B31` @ `.66`, was `.55`)
  — round 5's own fix relocated the design's literal `.55` into this
  checkpoint rather than eliminating the WCAG failure it was originally
  found to have: `.55` still composites
  to 3.34:1 against the bar's own `#f3f5fe` container, clearing the 3:1
  icon floor but not the 4.5:1 floor the always-visible label
  (`alwaysShowLabel` defaults to `true`; this file's `NavigationBarItem`
  call always passes a real `label = { Text(…) }`) owes. `.66` is the
  first step that clears 4.5:1 (composite `#6e7077`, 4.55:1) — a
  design-vs-accessibility deviation from the literal value, on the same
  footing as light `onPrimary`/`error`, applied to both icon and label
  alike (one shared literal, not split). **Named constants
  `RepFlowColor.navUnselectedAlphaDark`/`.navUnselectedAlphaLight` added
  this round (I1)** — see CP1 step 2 and step 7 below for why: a bare
  Compose-call-site literal cannot be read back by the plain-JVM
  `RepFlowThemeTest`.
- `secondaryContainer`/`onSecondaryContainer` (selected indicator pill /
  selected icon) — **new this round (B2)**, via this same `colors(...)`
  call's `indicatorColor`/`selectedIconColor` parameters: dark
  `RepFlowDarkColorScheme.primary.copy(alpha =
  RepFlowColor.navSelectedIndicatorAlphaDark)` (`#9184D9` @ `.20`) /
  `RepFlowColor.accent300` (`#d2cefd`), light
  `RepFlowLightColorScheme.primary.copy(alpha =
  RepFlowColor.navSelectedIndicatorAlphaLight)` (`#5D5294` @ `.16`) /
  `RepFlowLightColorScheme.primary` (reused, `#5d5294`) — the same
  values CP1 previously assigned globally (revision 6, round 4's I1),
  unchanged, since the nav's own render was never the problem: assigning
  them globally instead retinted three untouched screens' stock
  `FilterChip`s, whose selected label measured 4.41:1 against those
  screens' `background` — below WCAG AA — while never actually needing
  the global role (CP5's chips are `RepFlowTag`, not stock `FilterChip`;
  see CP3's "Cards" bullet). Confirmed `indicatorColor`/`selectedIconColor`
  fall back to the cascade default (`Color.Unspecified`/`takeOrElse`) when
  omitted, so adding them here does not disturb `selectedTextColor`'s
  existing `secondary`-role behavior. **Named constant
  `RepFlowColor.navSelectedIndicatorAlphaDark`/`.navSelectedIndicatorAlphaLight`
  added this round (I1)**, referencing `RepFlowColor.accent300` directly
  rather than restating its hex — same reason as `onSurfaceVariant` above:
  these values are the composite inputs (**corrected round 16's O1, was
  "the only three" of four**) CP1 step 7's test
  cannot reach through a `ColorScheme` role directly (`primary` and
  `surfaceContainer` already are `ColorScheme` fields the test reads
  today), since they live inline inside this `@Composable` call.

Same 6 routes, same order, same `selected`/`onClick`/`contentDescription`
wiring — visual only.

This checkpoint does not touch top app bars: `RepFlowNavHost.kt`'s own
`Scaffold` has no `topBar` — every one of the app's screens owns its own
per-screen `Scaffold`/`TopAppBar` instead (confirmed: ten `topBar = ` sites
across ten files, top-level destinations included, not just nested ones;
no shared `RepFlowTopAppBar`/`RepFlowTopBar` composable exists to restyle).
The only top bars this milestone's markup changes are CP5's
(`ExerciseListScreen.kt`) and CP6's (`ActiveWorkoutScreen.kt`) own, as an
incidental part of those checkpoints' file edits — the remaining eight are
untouched code that only inherit the CP1 color/type cascade, same as every
other unreskinned screen (see "Known limitations"). Do not adopt the target
design's 4-tab destination set or its "workout mode replaces the nav"
full-screen takeover — both are IA/structural changes explicitly out of
scope (see non-goals).

**Self-review flag**: re-confirmed this session —
`MainActivityNavHostSmokeTest.kt` and the other Compose UI tests touching
the bottom nav assert against string resources
(`R.string.exercise_list_search_hint` etc.), not the literal single-letter
glyph text, so this checkpoint's test risk is lower than revision 1
assumed. Still worth a final grep for any stray literal-glyph assertion
before editing, since this is a final check, not a from-scratch risk.

### CP5 — Apply foundation to Exercise list

**Files modified**: `presentation/exercise/list/ExerciseListScreen.kt`
(and its private per-file `LoadingIndicator`/`EmptyState`/
`FailureState(onRetry: () -> Unit)` composables, removed in favor of CP3's
shared ones — the retry callback is preserved unchanged, wired the same as
today).

Reskin list rows (`RepFlowCard`), the search field, the filter chips (via
CP3's `RepFlowTag` — not stock Material 3 `FilterChip`, see CP3's "Cards"
bullet), the FAB (`RepFlowIcons` + `RepFlowButtons`), and empty/error/
archived states using CP1 tokens + CP3 primitives — the live design's own
`2c` ("Exercise library — search, filter, archived state, long names") is
the direct reference: 64dp-tall rows with a title/meta-subtitle pair and a
trailing kebab menu, pill filter chips above the list, an "archived" status
badge at reduced opacity, a query-aware empty state ("Create "<query>""),
and a 60×60dp `RepFlowShapes.fab`-radius (18dp) solid-accent FAB. No
`ExerciseListViewModel`/`ExerciseListUiState` change — same message-queue
snackbar pattern, same filter/search state shape: the snackbar's own
appearance is untouched by this checkpoint (no CP5 file edit restyles it),
so it keeps rendering through Material 3's default `inverseSurface`/
`inverseOnSurface`/`inversePrimary` roles, exactly as it already does
today — not a new gap this milestone introduces (see CP1's closing
paragraph and "Known limitations").

**Self-review flag**: read `ExerciseListScreenTest.kt` first; every testTag
and content-description it asserts on must survive this checkpoint
unchanged (or, if a node's *purpose* is genuinely replaced by an equivalent
new one, the test is updated to match, never silently left to fail).

### CP6 — Apply foundation to Active Workout set-entry + rest timer

**Files modified**: `presentation/workout/ActiveWorkoutExerciseCard.kt`
(the fast set-entry surface — **corrected this round, B3**: set entry
(RPE/warm-up/pain/technique fields, the load/reps/duration steppers, the
undo/edit actions) lives in this file's `internal fun ExerciseCard`,
invoked from `ActiveWorkoutScreen.kt:179` — it is not part of
`ActiveWorkoutScreen.kt` itself, whose own composables are
`ActiveWorkoutScreen`/`LoadingIndicator`/`NoActiveSessionState`/
`ActiveSessionState`/`RestTimerBar`/`AddExercisePicker`/
`RecommendationRow`/`FailureState`), and
`presentation/workout/ActiveWorkoutScreen.kt` (the inline rest-timer
card/strip specifically, rendered by its own `RestTimerBar` — not every
sub-screen `ActiveWorkoutScreen.kt` contains).

Reskin using CP1's numeric-tabular type style (loads, reps, RPE, the rest
timer's countdown display) and CP3 primitives, preserving:

- the RPE/warm-up-flag/optional-pain/optional-technique fields and their
  existing input affordances (only their visual chrome changes) — the live
  design's own set-entry pad (inside turn `4a`) is the direct reference: a
  logged-set list with 26dp circular status glyphs, side-by-side weight/reps
  stepper cards, a collapsible detail section revealing RPE/pain/technique
  as horizontal pill rows, and a pill-shaped warm-up toggle;
- the rest timer's absolute-`endAt`-driven `remember`/`LaunchedEffect` tick
  loop, unmodified — CP6 only restyles what that loop already renders. The
  design's own inline rest strip (also in `4a`) is the reference for the
  restyled appearance: a tabular countdown, a linear progress bar tinted
  from the accent ramp, and ±15s/skip controls;
- every testTag `ActiveWorkoutScreenTest.kt` asserts on, including any
  asserted against `ExerciseCard`'s own nodes (**new this round, B3**:
  this self-review flag now explicitly covers `ActiveWorkoutExerciseCard
  .kt`, not just `ActiveWorkoutScreen.kt`'s own composables).

Also benefiting incidentally from this round's B1/B2 fixes: this file's
seven `OutlinedTextField`s and one `Switch` (the warm-up toggle) would
otherwise have silently inherited both the `onSurfaceVariant` and
`outline` regressions (round 5's B1/B2) on the app's most-used surface,
with no CP6 edit to notice them — both roles now resolve to Material 3's
accessible baseline instead.

Out of scope, confirmed again this session: the design's separate
progression-recommendation detail screen (turn `6a`, "why, and your final
say") is a distinct full screen, not part of `ActiveWorkoutScreen.kt`'s
current real surface, and stays out of this checkpoint — CP6 only restyles
the existing set-entry/rest-timer surface, it does not add a new
recommendation-detail screen.

This is the highest-risk checkpoint in this milestone precisely because it
sits closest to workout semantics without being allowed to touch them.
Self-review here is not optional: after drafting the diff, re-read it
side-by-side against the current `ActiveWorkoutViewModel`/use-case wiring
specifically to confirm zero logic change — every edit should be
explainable as "this composable now renders differently," never "this
composable now does something different." `ActiveWorkoutExerciseCard.kt`'s
`ExerciseCard` holds its entry-field state locally and forwards through
the existing `onRecordSet`/`onUndoLastSet`/`onEditLastSet` callbacks
unchanged, so this same zero-logic-change discipline applies to it as
much as to `ActiveWorkoutScreen.kt` itself.

**Sizing revisited this round (B3)**: the checkpoint table's
`session_target` is raised from 2 to 3 (complexity stays at 3, already
this plan's ceiling) now that the real scope is confirmed as two files —
`ActiveWorkoutExerciseCard.kt`'s full set-entry surface (seven
`OutlinedTextField`s, one `Switch`, several pill rows, the undo/edit
actions) plus `ActiveWorkoutScreen.kt`'s rest-timer strip — rather than
the single-file rest-timer-only scope revision 6 declared.

### CP7 — Verification, dark/light check, Open-decision + status doc updates

1. Run the full local verification suite: `spotlessCheck`, `detekt`
   (0 max issues), `lintDebug`, `testDebugUnitTest`, `assembleDebug`,
   `assembleDebugAndroidTest`. Additionally — since this milestone's entire
   point is visual and CI does not run instrumented tests — run
   `connectedDebugAndroidTest` against a real device/emulator, and manually
   exercise both dark and light theme across every surface CP4-CP6 touched
   (per this repo's own rule: UI changes should be verified by actually
   using the feature, not by type-checking/test-suite success alone). Pay
   particular attention to the light theme's accent usage (primary =
   accent-700, not the base accent hex; `onPrimary` = `#f5f4ff`, not dark's
   `#161826`), the destructive-color light variant — **narrowed this round
   (round 6's B1) to name its two live render contexts explicitly:
   `ExerciseEditorFormFields.kt`'s and `TrainingPlanEditorFormFields.kt`'s
   `isError` validation-message text, at the corrected `#a74541`** — and
   the bottom nav's
   selected/unselected states on both themes — the two globally-cascaded
   `NavigationBarItem`/`NavigationBar` roles (`secondary`,
   `surfaceContainer`) plus CP4's own three scoped overrides
   (`onSurfaceVariant`, `secondaryContainer`, `onSecondaryContainer` —
   **narrowed this round, B2, from four global roles to two**) — all
   corrections or additions made in this revision with no prior
   implementation to regress-test against. **Corrected this round (B1)**:
   confirm the light theme's unselected nav label specifically renders at
   ≥ 4.5:1 against its container (the corrected `.66`-alpha value, ~4.55:1
   computed) — named here as a concrete threshold, not just a look, per
   this round's own finding that the prior `.55` value read as solved in
   prose while still failing. **Retired this round (B2)**: revision 6's
   instruction to exercise the incidental selected-chip recolor on
   `HistoryScreen.kt`, `TrainingPlanListScreen.kt`, and
   `ExerciseEditorFormFields.kt` no longer applies — this round scopes
   `secondaryContainer`/`onSecondaryContainer` to CP4 alone, so those three
   screens' chips no longer read the moved role — their own fill/label pair
   is unchanged and comfortably accessible, but **corrected round 8's I2:
   not "unchanged from today" overall**, since this milestone also moves
   the `background` they sit on. **New this round (I2)**: weigh in on the
   light-theme selected `FilterChip` on those three screens specifically
   (`HistoryScreen.kt:245`, `TrainingPlanListScreen.kt:108,114`,
   `ExerciseEditorFormFields.kt:116`) — its fill-vs-background separation
   narrows from Material 3's own 1.23:1 to 1.05:1 under this milestone's
   `background`, on two of the three screens the only signal distinguishing
   the selected filter from the unselected one. **New this round (I1)**:
   also exercise the date picker's "today"
   marker (`HistoryScreen.kt:388`, `RecoveryFutsalScreen.kt:228`) on dark
   theme specifically. **Corrected round 10 (B1): this is no longer a
   trade-off to weigh — `surfaceContainerHigh` was reassigned to fix it —
   but a regression check, and the same check now covers all four
   `TextButton`-based dialogs, not just the date picker.** Confirm on both
   themes that: the date picker's "today" label and both `DatePickerDialog`s'
   confirm/clear/dismiss `TextButton`s (`HistoryScreen.kt:361`,
   `RecoveryFutsalScreen.kt:207`) render legibly against the now-lighter
   dark dialog container; `HistoryScreen.kt:176`'s invalidate-session
   `AlertDialog` and `BackupScreen.kt:62`'s restore `AlertDialog` — both
   destructive confirmations — read clearly with their confirm/cancel
   labels; and that retiring the "raised tier" distinction (dark
   `surfaceContainerHigh` now equals dark `surface`) does not read as a
   visual regression on its own (see "Known limitations" and "Areas the
   reviewer should specifically challenge").
   **New this round (round 5's I1)**: also exercise the seven untouched
   `DropdownMenu` sites now reading CP1's `surfaceContainer`
   (`HistoryScreen.kt:263,299`, `TrainingPlanEditorFormFields.kt:233`,
   `ActiveWorkoutScreen.kt:128,255`, `ExerciseListScreen.kt:249`,
   `TrainingPlanListScreen.kt:199`) on both themes, weighing in on light's
   deliberately-invisible menu/surface boundary and dark's
   darker-than-surface overlay (see "Known limitations"). **New this round
   (round 5's B1/B2), corrected round 15 (B2): `TextButton` moved out of
   this item — it is not an `onSurfaceVariant` regression check (see
   below)**: also confirm, on both themes, that the app's
   `OutlinedTextField`/`ListItem`/`AlertDialog`/
   `OutlinedButton` families and every `OutlinedTextField`/`Switch`
   resting border still render at Material 3's own (unchanged, accessible)
   default now that `onSurfaceVariant`/`outline` are deliberately left
   unassigned — specifically `ActiveWorkoutExerciseCard.kt`'s warm-up
   `Switch` and its seven `OutlinedTextField` call sites, now that CP6
   actually touches that file (B3). Because the rendered field set is
   chosen by `when (exercise.trackingType)`, no single session sees all
   seven — exercise all three branches to reach every site: `WEIGHT_AND_REPS`
   (load, reps), `REPS_ONLY` (reps), and `DURATION` (duration), plus
   RPE/pain/technique and the warm-up `Switch`, common to all three.
   **Corrected round 15 (B2, restated as its own item)**: the app's 34
   `TextButton`s are **not** an `onSurfaceVariant` regression check — their
   label is `primary` (`Button.kt:784`), this milestone's own value, not
   Material 3's unchanged default. Confirm instead, on both themes, that
   `TextButton` labels render legibly as `primary` against whatever
   container each sits on — most of them sit on `background`/`surface`,
   already covered by the `primary` role table row above, but
   **`TrainingPlanEditorFormFields.kt`'s bare `Card` (`:123-165`) needs its
   own look (round 15's B1)**: the always-enabled exercise-picker (`:228`)
   and remove-row (`:268`) `TextButton`s, the `enabled`-gated move-up/down
   pair (`:258`/`:263`), the row's seven focused `OutlinedTextField` labels,
   and the `Checkbox` fill at `:161`, on dark theme specifically — dark
   `primary` on this card measured 3.80:1 before CP1 step 2's B1 fix
   (`surfaceContainerHighest` reassigned to `surface`, now 4.71:1); confirm
   the reassigned container does not itself read as a visual regression
   against the `background` it actually sits on — the card renders inside
   a plain `Column` on the `Scaffold` body, not against "the rest of the
   row" — on both themes: dark narrows from 1.43:1 to 1.16:1 (a direct
   consequence of this round's reassignment), and light separately
   narrows from 1.23:1 to 1.05:1 (a consequence of this milestone's
   `background` move alone, unrelated to whether light is ever reassigned)
   (see "Known limitations" and "Areas the reviewer should specifically
   challenge"). **Corrected round 16 (I6): the row's `TargetRangeFields`
   renders its duration pair or its reps pair, never both**
   (`if (row.trackingType == ExerciseTrackingType.DURATION)`,
   `TrainingPlanEditorFormFields.kt:173`) — so a single row shows five
   focused `OutlinedTextField` labels, not seven; exercise both a
   duration-tracked and a reps-tracked planned exercise to actually reach
   all seven labels this card can render, the same branch-coverage
   instruction round 14's I1 already gave `ActiveWorkoutExerciseCard`'s
   analogous item above.
   **New this round (round 16's I2)**: also exercise, on dark theme,
   `RecoveryFutsalScreen.kt:255`'s `Switch` (the app's second stock
   `Switch` site, alongside `ActiveWorkoutExerciseCard.kt:129` above) — its
   unchecked track reads the same reassigned `surfaceContainerHighest` and
   goes from a distinguishable band (1.43:1) to nearly the page ground
   (1.16:1), staying legible only via its `Outline`-colored boundary
   (4.80:1 against the new track); weigh in on whether the off state still
   reads as off, not disabled. **Also new this round (round 16's B1)**:
   `RecoveryFutsalScreen.kt:93`'s "View history" `TopAppBar` action is a
   `TextButton`, so it renders `primary` on the bar's `surface` fill (dark
   4.71:1, light 6.23:1) rather than Material 3's baseline trailing-icon
   tint — the other nine bars pass no `actions` at all, so there is no
   tint for them to keep (see "Known limitations") — confirm it reads
   consistently with the row card's other `TextButton`s above.
   **New this round (round 6's I1)**: also exercise
   `TrainingPlanListScreen.kt:98`'s untouched `FloatingActionButton` on
   both themes alongside CP5's newly-restyled `ExerciseListScreen.kt:110`
   FAB, weighing in on whether the two FABs' now-divergent palettes
   (Material 3 baseline neutral purple vs. the design's solid-accent
   fill) are an acceptable disclosed limitation for this milestone. **New
   this round (round 9's I1(b))**: also exercise the five untouched stock
   `ListItem` rows now visibly lifted off `background` by CP1's
   `surface`/`background` split (`HistoryScreen.kt:143`,
   `HistoryDetailScreen.kt:49`, `RecoveryHistoryScreen.kt:87,104`,
   `TrainingPlanListScreen.kt:164` — 1.00:1 today in both themes, 1.13:1
   light / 1.16:1 dark after this milestone), weighing in on whether the
   newly-visible band reads as intended. **New this round (round 10's
   I2)**: also exercise all ten top app bars on both themes, for the
   identical 1.00:1 → 1.13:1 light / 1.16:1 dark container-fill split
   (`AppBarTokens.ContainerColor = Surface`, same underlying pair as the
   `ListItem` band above), weighing in on whether it reads as intended
   there too. Also
   weigh in specifically on the judgment calls this
   revision leaves undecided: light `control`, light
   scale-row-selected/status-chip-accent (see "Known limitations" and
   "Areas the reviewer should specifically challenge") — `surfaceContainerHigh`'s
   dialog/date-picker container value is no longer among them, closed by
   round 10's B1 fix rather than left as a judgment call.
2. Update `docs/TECHNICAL_DECISIONS.md`'s "Exact UI design system and
   visual identity" row from `Open` to resolved, citing this milestone and
   the concrete token set now in `presentation/designsystem/`. Leave
   "Navigation structure" exactly as-is (`Open (D-1 approved for M1 only)`)
   — this milestone does not touch it and must not be read as silently
   closing it.
3. Update `docs/ACTIVE_MILESTONE.md` to reflect this milestone's state.

## Requirements traceability

See `docs/ai-workflow/requirements/repflow-redesign-visual-foundation-mapping.json`
(REQ-1 through REQ-9, each mapped to the checkpoint(s) above; REQ-7 —
preserving every invariant listed above — is deliberately cross-cutting
against all seven checkpoints, not just the application-facing ones). This
file is authored fresh by the `/milestone-plan` declaration step under
Workflow v2.3.1 tooling — the discarded v2.3.0-era mapping, if one ever
existed on disk, is not reused (repo history confirms no such file was ever
actually committed).

## Migration and schema impact

None. This milestone adds no Room entity, column, or DAO method, and
touches no file under `infrastructure/database/`. `RepFlowMigrations.kt`
stays at `MIGRATION_6_7` (schema version 7) with no new migration.

## Backup compatibility impact

None. `BackupSnapshot`/`BackupJsonMapper` are untouched.

## Open decisions this plan touches

Per `docs/TECHNICAL_DECISIONS.md` (re-read this session — table unchanged
since revision 1):

- **"Exact UI design system and visual identity: Open"** — this milestone
  resolves it, explicitly, via CP7's doc update, once CP1-CP6 have landed
  and been verified — not silently finalized mid-plan. The reviewer should
  treat CP1's token choices (and the reference doc's confirmed-value
  tables) as the concrete proposal being reviewed for this decision.
- **"Navigation structure: Open (D-1 approved for M1 only)"** — explicitly
  **not** touched or advanced by this milestone; flagged here so it is not
  mistaken for silently resolved alongside the visual-identity decision it
  sits next to in that table.
- No other Open-decision row is touched (no schema, progression-formula,
  substitution, or workout-correction decision is implicated by a
  presentation-only milestone).

## Known limitations / explicitly deferred

- No new icon-library or font-provider dependency is added (CP1/CP2); if a
  later milestone needs materially more icons than this bounded set,
  revisit the dependency question then rather than over-provisioning now.
- Four of the six current bottom-nav destinations have no design-confirmed
  icon (see CP2) — the picks made there are a judgment call for the
  reviewer to weigh in on, not a design-derived fact, and may be revisited
  once the later navigation-IA milestone actually retires those
  destinations.
- Light theme's `control` role (`#b2b6ca`, CP1 step 2) is likewise a
  judgment call, not a design-confirmed value — the live design has no
  light-theme stepper/chip/scale-row render to draw one from (see the
  reference doc's Color section). CP7's manual light-theme pass should
  weigh in on it the same way it does on CP2's icon picks.
- **New in revision 5 (I3)**: light theme's scale-row selected state and
  status-chip accent-tinted state (CP3) are likewise judgment calls, not
  design-confirmed values, for the same reason as light `control` — `1d`
  has no scale-row or status-chip render to draw one from. Both currently
  reuse the confirmed light accent-outline pair (`RepFlowColor.accent600`
  border/`.accent700` text). CP7's manual light-theme pass should weigh in
  on these the same way it does on light `control`.
- Settings-driven theme override (light/dark/system) does not exist yet —
  out of scope; `RepFlowTheme` keeps following `isSystemInDarkTheme()`.
- No screen beyond bottom nav/Exercise list/Active Workout set-entry is
  reskinned. Every other screen (Plans, Recovery, History, Backup, both
  editors) still renders through the same cascading theme (so its *colors
  and type* already update) but keeps its current bespoke per-screen
  loading/empty/error composables and layout until a later milestone
  applies CP3's primitives there too.
- Eight of the app's ten top app bars are not restyled beyond the CP1
  color/type cascade every screen already gets — only CP5's
  (`ExerciseListScreen.kt`) and CP6's (`ActiveWorkoutScreen.kt`) own top
  bars pick up any markup change, incidentally, as part of those
  checkpoints' file edits. No shared top-bar primitive exists to apply more
  broadly (see CP4) — that is deferred, alongside the other unreskinned
  screens above, not silently dropped. These eight bars' navigation icon
  (`AppBarTokens.LeadingIconColor = OnSurface` — **corrected round 10's
  I2, was misstated as `TopAppBarTokens...`; no `TopAppBarTokens` object
  exists in material3 1.4.0**) picks up RepFlow's text tint via the
  globally-assigned `onSurface`, unchanged since revision 1. **Corrected
  round 5 (B1)**: their *action* icons (`TrailingIconColor =
  OnSurfaceVariant`) do **not** pick up a tint change — revision 6 had
  claimed they would, because CP1 then globally assigned
  `onSurfaceVariant`; round 5 found that same assignment fails WCAG AA for
  the app's default `OutlinedTextField`/`ListItem`/`AlertDialog`/
  `OutlinedButton` text (**"`TextButton`" removed from this list, corrected
  round 16's B1 — round 15's B2 already found `TextButton`'s label is
  `primary`, not `onSurfaceVariant`**) (see CP1
  step 2's B1 disposition) and scopes it to CP4's bottom nav instead.
  **Corrected round 16 (B1): this does not hold for all eight** —
  `RecoveryFutsalScreen.kt` is one of the eight, and its bar is the one
  stock `topBar = TopAppBar(...)` site app-wide that passes `actions =`
  (`:93`), a `TextButton` reading `primary` (this milestone's own value,
  not Material 3's baseline `OnSurfaceVariant` trailing-icon tint): dark
  4.71:1, light 6.23:1 against the bar's own `surface` fill, already
  covered by the `primary` role table row. The remaining seven bars' action
  slot stays empty, so for them `AppBarTokens.TrailingIconColor` still has
  no live consumer (see CP1 step 2's role table).
- **New this round (round 10's I2): the `surface`/`background` split
  becomes visible on all ten top app bars, not just the five `ListItem`
  rows round 9's I1(b) already discloses above.**
  `AppBarTokens.ContainerColor = Surface`, wired through
  `TopAppBarDefaults.defaultTopAppBarColors` (`AppBar.kt:1513`), backs
  every one of the app's 10 stock `topBar = TopAppBar(...)` sites, one per
  screen (`ExerciseListScreen.kt:104`, `ActiveWorkoutScreen.kt:68`,
  `HistoryScreen.kt:100`, `HistoryDetailScreen.kt:37`, `BackupScreen.kt:82`,
  `RecoveryFutsalScreen.kt:91`, `RecoveryHistoryScreen.kt:42`,
  `TrainingPlanListScreen.kt:95`, `TrainingPlanEditorScreen.kt:49`,
  `ExerciseEditorScreen.kt:72` — 8 of the 10 untouched by any checkpoint,
  none passing `colors =`). Material 3's baseline has `Surface ==
  Background` in both themes, so today every bar paints flush with the
  `Scaffold` behind it (`Scaffold`'s own `containerColor` default is
  `MaterialTheme.colorScheme.background`, `Scaffold.kt:90`) — 1.00:1,
  invisible. CP1's `surface`/`background` split makes each bar a visibly
  distinct band after this milestone: **1.13:1 light / 1.16:1 dark**, the
  same pair round 9's I1(b) measured for `ListItem` rows, because it is the
  same pair of roles. No ratio fails: the title (`AppBarTokens.TitleColor =
  OnSurface`) stays 12.55:1 dark, already covered above; nine of the ten
  bars pass no `actions` at all, so there is no trailing-icon tint for
  them to keep, and the tenth's action (`RecoveryFutsalScreen.kt:93`'s
  `TextButton`) reads `primary`, also already covered above, at 4.71:1
  dark / 6.23:1 light against this same bar's new `surface` fill. This is
  disclosure, not a fix — see CP7's manual pass below and
  "Areas the reviewer should specifically challenge."
- **New this round (round 5's B2)**: `outline` is likewise deliberately
  left at Material 3's baseline rather than assigned the design's
  hairline value — the design's `#3f424d`/`#cfd3e5` hairline is exposed
  instead via the new `RepFlowColor.hairline` token, read directly by
  CP3's card/stepper/scale-row/neutral-outline-button primitives (see CP1
  step 2 and CP3's "Cards" bullet). Every stock `OutlinedTextField`
  (23 sites) and `Switch` (2 sites) in the app therefore keeps its
  existing, accessible resting-state border unchanged by this milestone.
- **New this round (round 5's I1)**: `surfaceContainer`'s cascade also
  reaches all 7 stock `DropdownMenu` sites in the app
  (`HistoryScreen.kt:263,299`, `TrainingPlanEditorFormFields.kt:233`,
  `ActiveWorkoutScreen.kt:128,255`, `ExerciseListScreen.kt:249`,
  `TrainingPlanListScreen.kt:199`), none of which is in any checkpoint's
  "Files modified" list — the same "assign a role once, every consumer
  changes" cascade the retired `FilterChip` case below used to exhibit.
  Light `surfaceContainer`
  = `surface` is a deliberate, disclosed value (it is the bottom nav's own
  literal render, re-read via the shared role), which means an open
  light-theme menu is delimited only by its own shadow, with no visible
  container line against the surface behind it. Dark's `surfaceContainer`
  (`#1b1d2b`) is measurably *darker* than `surface` (`#232532`), so a dark
  open menu reads as a recessed panel rather than an elevated one — a
  purely visual, non-accessibility inversion of Material 3's usual
  elevation convention. Neither is a new implementation change (no
  checkpoint touches these seven files); both are disclosed here so CP7's
  manual pass weighs in on them explicitly (see below).
- **From round 5's I2, value corrected round 10's B1**: `surfaceContainerHigh`
  is assigned — read by the app's 4 `AlertDialog`s and 2 `DatePickerDialog`s
  regardless of this milestone's own scope, so leaving it unassigned is not
  a viable "no-op" (Material 3's own baseline, `#2B2930` dark, does not even
  clear this milestone's own `primary`: `#9184d9` on `#2B2930` = 4.45:1,
  independently checked). Originally dark `RepFlowColor.control` `#292b31`
  / light `surface` `#f3f5fe`, a disclosed judgment call (no dialog is
  rendered in either theme in the live design to confirm a literal value
  from). Round 7's I1 found this drops dark `DatePickerModalTokens
  .DateTodayLabelTextColor` (= `primary`, `#9184d9`) to 4.38:1 against it —
  2.7% short of the 4.5:1 text floor for the current-date glyph in
  `HistoryScreen.kt:388`'s and `RecoveryFutsalScreen.kt:228`'s
  `DatePicker`s — and, believing this affected only that one glyph, disclosed
  it rather than re-tuning: light was unaffected (6.23:1), as were both
  themes' selected-day roles (5.45:1 dark, 6.22:1 light), and the app's 4
  `AlertDialog`s were believed unaffected (`onSurface` headline on this same
  container = 11.69:1). **Round 10's B1 found that belief false**:
  `DialogTokens.ActionLabelTextColor = Primary` puts every dialog's
  `TextButton` action label on the same container too (Compose's
  `TextButton` content color resolves to `fromToken(Primary)`,
  `Button.kt:784`) — four dialogs use `TextButton` for their actions (the
  other two use filled `Button`, whose `onPrimary`-on-`primary` label is
  unaffected): `HistoryScreen.kt:176`'s invalidate-session `AlertDialog`
  (confirm `:181`, cancel `:191`), `BackupScreen.kt:62`'s restore
  `AlertDialog` (confirm `:67`, cancel `:72`), `HistoryScreen.kt:361`'s
  `DatePickerDialog` (confirm `:364`, clear `:376`, dismiss `:382`), and
  `RecoveryFutsalScreen.kt:207`'s `DatePickerDialog` (confirm `:210`,
  dismiss `:223`) — nine labels, all measuring the identical 4.38:1, two of
  the four dialogs being destructive confirmations (invalidating a recorded
  workout session; restoring a backup over live data). **Re-decided on the
  corrected scope**: dark `surfaceContainerHigh` is now assigned `= surface`
  (`#232532`) instead of `RepFlowColor.control` — the same re-tuning this
  milestone previously declined for "one glyph," now applied because the
  real set is nine interactive, in two cases destructive-confirmation,
  labels. This clears every affected label (dark `primary` on `#232532` =
  **4.71:1**) and every other consumer of the same container (`onSurface`
  headline `12.55:1`; `onSurfaceVariant`-baseline supporting text `8.91:1`,
  both re-derived against the new value) — light is unchanged, since light
  `surfaceContainerHigh` already equalled light `surface`. The cost is
  retiring the "raised tier" visual distinction round 5's I2 deliberately
  introduced between the dialog container and `surface`: both themes now
  apply the identical rule (`surfaceContainerHigh = surface`). CP1 step 7
  gains a computed assertion pinning dark `primary` against dark
  `surfaceContainerHigh` at ≥ 4.5:1, and CP7's manual pass now names all
  four `TextButton`-based dialogs explicitly, not just the date picker.
- **Retired this round (B2)**: revision 6's disclosed "assign a role once,
  every consumer changes" cascade to stock Material 3 `FilterChip` usages
  outside this milestone's own checkpoints (`HistoryScreen.kt:245`,
  `TrainingPlanListScreen.kt:108,114`, `ExerciseEditorFormFields.kt:116`)
  no longer applies: `secondaryContainer`/`onSecondaryContainer` are now
  scoped to CP4's own `NavigationBarItemDefaults.colors(...)` override
  (this round's B2, resolved by an independently-confirmed WCAG failure on
  these same three screens' `background` composite — see CP1 step 2 and
  CP4), not assigned through the global `ColorScheme`. These three
  screens' chips revert to Material 3's own baseline pair — that pair
  itself is unchanged from before this milestone, comfortably clearing AA
  on its own. **New this round (I2, corrected from "unaffected by this
  milestone, same as before it"):** what these chips sit on is not
  unchanged — this milestone moves `Scaffold`'s `background` (Material 3's
  own `#FEF7FF` → `#e4e7f5`), narrowing the light selected chip's
  fill-vs-ground separation from 1.23:1 (already below WCAG 1.4.11's 3:1
  component-state floor, stock Material 3 behavior) to **1.05:1**, and the
  unselected chip's 1dp outline from 1.62:1 to **1.38:1**
  (`FilterChipTokens.FlatUnselectedOutlineColor = OutlineVariant`,
  unassigned by this plan, so it stays at Material 3's baseline `#cac4d0`
  light). None of the four sites passes a `leadingIcon`
  (`HistoryScreen.kt:245-249`, `TrainingPlanListScreen.kt:108-118`,
  `ExerciseEditorFormFields.kt:116-120`), so on `TrainingPlanListScreen.kt`
  and `ExerciseEditorFormFields.kt` — where one chip in the row is always
  selected — the fill-vs-ground contrast and a label tint shift are the
  only signal carrying which filter is active. Not a regression from a
  passing state, and not fixed this milestone; disclosed here and in CP7's
  manual pass rather than left as a silent "no-op" claim.
- **New in revision 5 (B1's "not confined to CP4" tail); `surfaceContainerHighest`
  re-decided round 15's own B1, `inverse*` unaffected.** `Snackbar`'s
  `inverse*` roles — the existing message-queue snackbar pattern, unchanged
  by this milestone — still resolve at the Material 3 baseline, since CP1
  deliberately does not assign a role nothing here exercises (see CP1's
  closing paragraph): a narrower, pre-existing limitation (the same
  baseline rendering already has today), not a new gap. **`surfaceContainerHighest`
  is a different case, corrected this round (B1): the app's one bare
  `Card(` (`TrainingPlanEditorFormFields.kt:123`)'s call site is
  pre-existing and undisturbed, but its content is not, and (round 17's
  I1, see the bullet below) neither is the container's own value** — the row's 4 `TextButton` labels
  and 7 focused `OutlinedTextField` labels are this milestone's own
  `primary` (round 15's B2: `TextButton`'s label is `primary`, not
  `onSurfaceVariant`), and dark `primary` on the Material 3 baseline
  container measured **3.80:1**, 15.5% short of the 4.5:1 text floor —
  milestone-introduced, the same shape as round 10's B1 one container
  further out. **Re-decided on the corrected scope**: dark
  `surfaceContainerHighest` is now assigned `= surface` (`#232532`), the
  same fix shape round 10's B1 applied to `surfaceContainerHigh` one tier
  down. This clears every affected label (dark `primary` on `#232532` =
  **4.71:1**) and moves dark `error` on the same container to **5.80:1**
  (**corrected round 16's I1: was 4.68:1 against the container this
  reassignment replaces; still clear, not "unaffected"**); light is left
  unassigned, since light `primary`
  on the Material 3 baseline already clears (5.23:1) and reassigning it too
  would move the ground round 6's B1 measured light `error` against
  without a corresponding failure to fix. CP1 step 7 gains a computed
  assertion pinning dark `primary` against dark `surfaceContainerHighest`
  at ≥ 4.5:1, and CP7's manual pass now names this card explicitly.
- **New this round (round 16's I2)**: dark `surfaceContainerHighest`'s
  reassignment above also retints the app's two stock `Switch` sites'
  unchecked track — `SwitchTokens.UnselectedTrackColor` (`:78`) resolves
  the same role, wired straight through by `SwitchDefaults.colors()`
  (`Switch.kt:337`/`:398`), and neither `ActiveWorkoutExerciseCard.kt:129`
  nor `RecoveryFutsalScreen.kt:255` passes `colors =`. Dark unchecked track
  on `background` moves from **1.43:1** (Material 3 baseline) to
  **1.16:1** — the switch stops reading as a filled pill and becomes an
  outline; its boundary (`SwitchTokens.UnselectedFocusTrackOutlineColor =
  Outline` — **corrected round 17's O1, was misstated as
  `.UnselectedTrackOutlineColor`, also `Outline`, so no value changes**)
  still clears the 3:1 non-text floor at 4.80:1 against the new
  track, so this is a visual change, not a WCAG failure — the same class of
  disclosed, reviewer-facing side effect round 9's I1(b) and round 10's I2
  already got for the `ListItem`/top-bar container-fill split. Light is
  unaffected (light `surfaceContainerHighest` stays at baseline). See CP7's
  manual pass, which now names both switch sites.
- **New this round (round 17's I1)**: the reassignment above also
  narrows the row `Card` itself — the container the `Switch` bullet's
  own retinted track sits inside is the same one that separates a
  planned-exercise row from the screen behind it, and nothing above
  measures that separation on its own terms. A filled `Card` has
  `border = null` and `Level0` shadow elevation (`Card.kt:80-98`,
  `:393-408`, `FilledCardTokens.kt:25`), so fill-vs-ground contrast is its
  *only* edge; it renders inside a plain `Column` on the `Scaffold` body
  (`TrainingPlanEditorFormFields.kt:52-54`, `:94`, `:123`), i.e. directly
  on `background`. Dark moves from today's **1.52:1** (`#36343B` on
  Material 3's baseline `#141218`) — **1.43:1** against this milestone's
  own `background` before the reassignment — to **1.16:1** after it (the
  same pair the `Switch` bullet above states, now attributed to the
  container's own edge as well as to that second consumer). Light
  `surfaceContainerHighest` is not reassigned, but `background` itself
  moves, narrowing the card from **1.23:1** to **1.05:1** regardless — a
  consequence of this milestone's `background` value, not of any role
  reassignment. No ratio fails (a decorative container edge owes no WCAG
  floor), but this is the largest surface this milestone visually
  flattens, with no border or shadow to fall back on; see CP7's manual
  pass, which now looks at the card's edge alongside its `TextButton`/
  field-label content. **Declined a new open question for this**
  (round 17's I1 leaves it a judgment call): unlike round 9's I1(b)
  and round 10's I2, which made a previously *invisible* 1.00:1 band
  visible for the first time, this card's edge was already a visible
  band before this milestone (1.52:1 dark, 1.23:1 light) and the change
  narrows an existing edge rather than introducing one, on the same
  footing as the light `FilterChip`'s 1.05:1 narrowing (see above), which
  also carries no open question.
- **New this round (round 6's I1)**: `primaryContainer`/`onPrimaryContainer`
  back both of the app's `FloatingActionButton`s
  (`ExerciseListScreen.kt:110`, `TrainingPlanListScreen.kt:98`), neither
  passing `containerColor`. Unlike `Snackbar`'s `inverse*` case above
  (**corrected round 16's O3, was "the bare-`Card`/`Snackbar` case" — the
  bare `Card` no longer belongs in that comparison after round 15's B1**),
  this one *is* a new gap this milestone introduces: CP5 turns
  `ExerciseListScreen.kt`'s FAB into a 60×60dp solid-accent FAB while
  `TrainingPlanListScreen.kt`'s FAB, untouched by any checkpoint, keeps
  Material 3's baseline neutral purple (`#4F378B` dark / `#EADDFF`
  light) — two top-level list screens with the same affordance in two
  different palettes after this milestone. Disclosed rather than fixed by
  assignment, for the same reason `outline`/`onSurfaceVariant` are scoped
  rather than globally assigned (see CP1's closing paragraph); CP7's
  manual pass now names `TrainingPlanListScreen.kt`'s FAB explicitly so
  the divergence is a disclosed decision, not an unnoticed one.
- CP3's `LoadingIndicator`/`EmptyState`/`FailureState` de-duplication does
  not fully land this milestone: only Exercise list's per-screen copies are
  removed (CP5). `ActiveWorkoutScreen.kt`'s and `TrainingPlanListScreen.kt`'s
  own copies remain duplicated until a later milestone reskins those
  surfaces too.
- **New this round (round 9's I1(b))**: the `surface`/`background` split
  CP1 assigns (dark `#232532`/`#161826`, light `#f3f5fe`/`#e4e7f5` —
  numerically identical in Material 3's own baseline, where both are
  `PaletteTokens.Neutral6`/`.Neutral98`) becomes visible for the first time
  on six stock `ListItem(` rows across five screens; five of them, on four
  screens, are untouched by any checkpoint: `HistoryScreen.kt:143`,
  `HistoryDetailScreen.kt:49`,
  `RecoveryHistoryScreen.kt:87`, `RecoveryHistoryScreen.kt:104`, and
  `TrainingPlanListScreen.kt:164` (the sixth, `ExerciseListScreen.kt:218`,
  is inside CP5's own scope). Each row goes from pixel-identical with the
  `Scaffold` behind it today (1.00:1 in both themes) to a visibly lifted
  band after this milestone (dark `#232532` on `#161826`: 1.16:1; light
  `#f3f5fe` on `#e4e7f5`: 1.13:1) — the row's own text stays `onSurface`,
  already comfortably covered by the `onBackground`/`onSurface` table row
  above. Almost certainly the intended effect of the split (see CP7's
  manual pass, which now names these five sites explicitly), but disclosed
  here on the same footing as `surfaceContainer`'s `DropdownMenu` cascade
  and `background`'s `FilterChip` cascade above, since nothing in this
  milestone's own checkpoints otherwise calls it out.
- **New this round (round 9's I2(b))**: the reference doc's own source
  values record a divider tone distinct from the hairline border —
  `rgba(233,233,237,.09)` (reference doc, "Color" section) — but this
  milestone does not expose it as a token or map it to any role. All three
  stock `HorizontalDivider()` sites in the app (`HistoryScreen.kt:166`,
  `HistoryDetailScreen.kt:94`, `RecoveryFutsalScreen.kt:145`,
  `DividerTokens.Color = OutlineVariant`) keep reading the unassigned
  `outlineVariant` role at Material 3's own baseline instead (see the
  `outlineVariant` table row above) — a decorative, pre-existing
  presentation, not a WCAG regression. **Deliberately deferred**: unlike
  `RepFlowColor.hairline` (round 5's B2), which CP3's primitives already
  consume, no primitive this milestone builds reads a divider value, so
  introducing `RepFlowColor.divider` now would add a token with no
  consumer in this milestone's own scope — left for a later milestone that
  actually touches these three screens' dividers to add and apply
  alongside `.hairline`.

## Areas the reviewer should specifically challenge

- Whether two representative surfaces (Exercise list, Active Workout
  set-entry) are sufficient to "prove the system" per the user's framing,
  or whether a third archetype (e.g. a form/editor screen) is needed before
  subsequent milestones can build on this foundation with confidence.
- Whether bundling a hand-picked Phosphor icon subset as local vector
  assets (CP2) is the right call versus a real icon-library dependency,
  given how many more icons later redesign milestones will likely need.
- Whether mapping the six-slot Nocturne type scale onto Material 3's
  existing `Typography` slots (CP1) versus exposing a fully custom,
  non-Material-3-shaped type system is the right level of Material 3
  convention-following for this codebase going forward.
- **New in revision 2**: whether CP2's four judgment-call nav icons
  (Exercises/Workout/Recovery/Backup) are reasonable placeholders given
  they have no design-confirmed glyph, or whether the reviewer would rather
  defer the icon swap for those four specific destinations (keep them as
  literal-glyph fallback) until the navigation-IA milestone actually
  resolves the destination set.
- **New in revision 3**: whether CP4 dropping "top app bars" from its
  scope (no shared top app bar exists in the codebase to restyle — every
  screen owns its own) is the right bounded-scope call, versus introducing
  a shared `RepFlowTopAppBar` primitive now and adopting it across all ten
  `topBar = ` call sites — a structural refactor materially larger than
  this checkpoint's current complexity-1 sizing, and arguably its own
  checkpoint rather than a CP4 addition.
- **New in revision 4** (round 2's I2 finding): whether light theme's
  `control` role — neutral-400 `#b2b6ca`, CP1 step 2 — is a reasonable
  judgment-call value given the live design has no light-theme stepper/
  chip/scale-row render to confirm one from, or whether the reviewer would
  rather CP1 leave light `control` equal to light `RepFlowColor.hairline`
  (accepting a borderless light-theme stepper/chip as a disclosed
  limitation) until a later milestone's design pass actually confirms a
  light-theme value.
- **From revision 5** (round 3's B1 finding), **extended in revision 6
  (I1)**, **narrowed in revision 7 (round 5's own B1)**, **narrowed again
  this round (B2)**: whether assigning `NavigationBarItem`/
  `NavigationBar`'s remaining two globally-cascaded roles (`secondary`,
  `surfaceContainer`) in CP1's `ColorScheme` is the right call versus
  giving CP4 its own explicit per-item override for these too — the
  global-assignment path incidentally retints every untouched screen's
  stock `DropdownMenu` container (`surfaceContainer`, seven sites — see
  "Known limitations"), which a scoped per-item override would not do
  (`secondary` carries no such incidental consequence — it is reused
  plain text with no other stock consumer this milestone's surfaces
  exercise). Three of the five roles are **no longer open**: round 5's own
  B1 resolved `onSurfaceVariant` in favor of the scoped path, for the
  accessibility reason stated in CP1 step 2; **this round's B2 resolves
  `secondaryContainer`/`onSecondaryContainer` the same way**, for the same
  class of reason (an incidental cascade to untouched screens' `FilterChip`
  selected states failed WCAG AA) — this bullet now concerns only the
  two roles that don't carry either reason. Also whether the two
  light-theme judgment calls round 3's I3 finding produced (scale-row
  selected, status-chip accent-tinted — both reusing the light
  accent-outline pair) are reasonable placeholders on the same footing as
  light `control`, or whether the reviewer would rather defer them, like
  light `control`.
- **New in revision 7** (round 5's B1/B2): whether scoping `onSurfaceVariant`
  to CP4's own `NavigationBarItemDefaults.colors(...)` override (this
  revision's chosen fix) is preferable to the finding's other offered
  option — keeping it a global `ColorScheme` assignment as an explicitly
  recorded accessibility trade-off — and likewise for `outline`, whether
  splitting it into a Material-3-baseline `outline` plus a separate
  `RepFlowColor.hairline` token (this revision's chosen fix) is
  preferable to instead raising `outline` itself to a value that clears
  3:1 directly.
- **New in revision 7** (round 5's I2), **closed revision 12 (round 10's
  B1) — see the restated bullet below.** This bullet asked whether
  assigning `surfaceContainerHigh` at all was preferable to leaving it at
  Material 3's own baseline and only naming its six real consumers in
  "Known limitations," the way `surfaceContainerHighest`/`inverse*` are
  handled. That question is no longer open: round 10's B1 found Material
  3's own baseline (`#2B2930` dark) also fails this milestone's own
  `primary` at 4.45:1, short of the 4.5:1 floor, so leaving
  `surfaceContainerHigh` unassigned does not clear the floor either (see
  CP1 step 2's dialog-roles paragraph and its restatement in "Known
  limitations"). Dark `surfaceContainerHigh` is now assigned
  `= surface` (`#232532`), not `RepFlowColor.control` — the remaining open
  question, about the cost of that fix, is restated below.
- **New this round (round 6's B1)**: whether darkening light `error` from
  the design-derived `oklch(0.55 0.13 25)`/`#b14e49` to
  `oklch(0.52 0.13 25)`/`#a74541` (this revision's chosen fix, walking the
  same OKLCH lightness axis one step further to clear both 4.5:1 render
  contexts) is preferable to the finding's other offered option — keeping
  `#b14e49` as the design-confirmed value and recording an explicit,
  accepted accessibility trade-off on the app's form-validation messages
  instead.
- **New this round (round 6's I1)**: whether leaving
  `primaryContainer`/`onPrimaryContainer` unassigned and disclosing the
  resulting two-FAB palette split (this revision's chosen fix) is
  preferable to assigning them from the accent ramp (candidates:
  `RepFlowColor.accent900`/`.accent700` dark, an
  `.accent300`-ish light value), which would also retint
  `TrainingPlanListScreen.kt`'s untouched FAB to match CP5's — the same
  "assign once, every consumer changes" leverage this milestone already
  takes for `surfaceContainer`, but for a role with no design-confirmed
  value beyond the one FAB CP5 itself restyles.
- **New this round (round 7's B1)**: whether raising light's CP4-scoped
  unselected-nav alpha from the design's literal `.55` to `.66` (this
  revision's chosen fix, one shared value for icon and label) is
  preferable to the finding's other offered options — splitting icon and
  label so only the label's alpha changes, or keeping `.55` on both and
  recording the 3.34:1 label figure as an explicit, accepted accessibility
  trade-off instead.
- **New this round (round 7's B2)**: whether scoping
  `secondaryContainer`/`onSecondaryContainer` to CP4's own
  `NavigationBarItemDefaults.colors(...)` override (this revision's chosen
  fix, retiring the incidental `FilterChip` cascade entirely) is preferable
  to the finding's other offered options — darkening light
  `onSecondaryContainer` off the accent ramp (candidates:
  `RepFlowColor.accent900` at 9.31:1/10.46:1, or an intermediate
  `#453d6d` at 6.39:1/7.18:1) so the global assignment (and its
  incidental `FilterChip` retint) could stay, or lowering light's alpha to
  `.14` instead.
- **Carried from revision 9 (round 7's I1), re-decided revision 12 (round
  10's B1) — restated so the reviewer weighs the corrected scope.** Round
  7 believed only the date picker's "today" label was affected (4.38:1,
  2.7% short of 4.5:1) and disclosed it rather than re-tuning
  `surfaceContainerHigh`. Round 10's B1 found the same 4.38:1 figure also
  applied to nine `TextButton` dialog action labels across four dialogs —
  including both confirm/cancel pairs of the app's two destructive-action
  dialogs (invalidating a recorded workout session; restoring a backup over
  live data) — not one glyph. On the corrected scope, this revision
  re-tunes rather than discloses: dark `surfaceContainerHigh` is now
  assigned `= surface` (`#232532`, 4.71:1 for every affected label),
  retiring round 5's I2 "raised tier" distinction between the dialog
  container and `surface` in dark theme (light already had no such
  distinction). **Reviewer question, restated**: is retiring that
  distinction an acceptable price for closing a WCAG AA text-contrast gap
  on nine interactive controls, two of them destructive-confirmation
  actions — or would the reviewer prefer a narrower fix that preserves the
  distinction, such as scoping a `TextButton` content-color override to
  just the four affected dialog composables (leaving `surfaceContainerHigh`
  itself at `RepFlowColor.control`), at the cost of touching three files
  (`HistoryScreen.kt`, `BackupScreen.kt`, `RecoveryFutsalScreen.kt`)
  outside this milestone's stated three-surface scope?
- **New this round (round 15's B1)**: the same shape as round 10's B1
  above, one container further out. `TrainingPlanEditorFormFields.kt`'s
  bare `Card` measured dark `primary` at 3.80:1 against the Material 3
  baseline `surfaceContainerHighest` — milestone-introduced, since
  `primary` is this milestone's own value landing on a pre-existing,
  undisturbed container. This revision re-tunes rather than discloses,
  the same way round 10's B1 chose: dark `surfaceContainerHighest` is now
  assigned `= surface` (`#232532`, 4.71:1). **Reviewer question**: is this
  the right choice among the three the finding offered — (1) the chosen
  fix, one more literal in CP1's own token file, no new file touched; (2)
  scoping a `TextButton`/field color override into
  `TrainingPlanEditorFormFields.kt` itself, preserving the role's Material
  3 baseline everywhere else at the cost of touching a file outside this
  milestone's stated three-surface scope; or (3) disclosing the 3.80:1 as
  an accepted trade-off, which this milestone's own precedent (round 10's
  B1, which rejected disclosure at a less severe 4.38:1 for nine labels in
  modals) argues against for four always-visible, one destructive-in-kind,
  row-level controls on a routine editing surface? Also, is leaving light
  `surfaceContainerHighest` unassigned (light `primary` already clears at
  5.23:1) preferable to reassigning it too for parity with dark, at the
  cost of restating light `error`'s two card-context figures (currently
  `#E6E0E9`, would become `#f3f5fe`, 5.41:1 — still clear, but a value this
  plan's CP1 step 2 and step 7 both currently name explicitly)?
