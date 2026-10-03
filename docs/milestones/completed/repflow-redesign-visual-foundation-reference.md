# repflow-redesign-visual-foundation — Reference

Companion to `docs/milestones/repflow-redesign-visual-foundation-execution.md`.
Deeper rationale, confirmed source values, and citations that would clutter
the execution plan's checkpoint-by-checkpoint flow.

## Provenance note (2026-08-27, Workflow v2.3.1 restart)

This document was originally produced during an earlier planning pass
(against a since-superseded Workflow v2.3.0 tooling state, which was
intentionally discarded — its registry, mapping, artifacts declaration,
approvals, and `WORKFLOW_STATE.json` entry no longer exist and are not
reconstructed). The repository's product code is unaffected: nothing under
`app/src/main/kotlin` has changed since Milestone 8 closed
(2026-08-11-era), so the discard was process/planning state only, never an
implementation.

This restart re-read the live Claude Design project directly (rather than
trusting this document's earlier numbers) and found the color, type, and
layout **role structure** below unchanged, but several **numeric/structural
claims corrected or sharpened** — see "Deltas from the original read"
at the end of this document. Every value in the sections below is the
freshly re-verified one; where the original claim was wrong, the section
says so inline rather than silently swapping the number.

## Why this milestone is first

From the current-vs-target gap analysis performed in the conversation that
produced the original plan (R0 of the recommended redesign decomposition):
a bounded visual-foundation milestone carries zero migration and zero
domain risk, resolves an already-flagged Open decision ("Exact UI design
system and visual identity") without silently finalizing it, and every
later redesign milestone's screens depend on these primitives existing —
sequencing it first avoids hand-styling screens now and retrofitting them
later. It stays fully decoupled from the navigation/IA product decisions a
later milestone (Home screen, 4-tab nav, Settings) needs settled first.
This reasoning is unchanged by the restart.

## Source-of-truth values (re-verified 2026-08-27 directly against the live project)

Pulled by reading, in full, `_ds/nocturne-3f5c4219-8153-42d4-afb3-9364451b6478/styles.css`
(the shared Nocturne design-system stylesheet) **and** the RepFlow-specific
"component spec — tokens and primitives for the Compose build" panel inside
`RepFlow.dc.html` (id `6b`, the newest turn in the document). The two
sources mostly agree; where they diverge, `6b` wins — it says explicitly
*"Values are the literal ones used in the prototype — a Compose theme
should carry them as tokens rather than re-deriving them per screen"* —
i.e. it is RepFlow's own literal usage, while `styles.css` is the generic,
shared Nocturne substrate RepFlow's screens do not actually consume classes
from (confirmed again this session: every RepFlow screen is bespoke
inline-styled).

### Color

Ground `#161826`, surface `#232532`, a third **`control`** tier `#292b31`
(stepper buttons, chip backgrounds — not previously captured), text
`#e9e9ed` (100%/55%/45% for primary/secondary/tertiary-and-labels), hairline
borders `#3f424d`, dividers `rgba(233,233,237,.09)` (**not** the generic
stylesheet's `--color-divider` at 16% — RepFlow's own screens use 9%; use
9% for divider hairlines between rows, reserve the generic token's 16% only
if a literal Nocturne-classed surface is ever consumed, which none of this
milestone's surfaces are). Single accent `#9184d9` (OKLCH hue 289.2,
L 0.660, C 0.125 — mono-accent scheme; `--color-accent-2` is a
machine-derived stand-in with no independent role, confirmed unchanged).
RepFlow-specific accent-tinted card surface `#2b2741` / border `#5d5294`
(exactly `--color-accent-900` / `--color-accent-700` off the shared ramp —
confirmed exact match). Destructive/pain color `oklch(0.72 0.13 25)` on
dark, resolved sRGB **`#eb827b`** — and, newly confirmed this session, a
distinct light-theme variant, **corrected in execution-plan round 6 (B1)
to `oklch(0.52 0.13 25)`, resolved sRGB `#a74541`** (lower lightness for
contrast against a light ground; the original reference doc only had the
dark value, and neither had a resolved hex until round 5). Round 5's
`oklch(0.55 0.13 25)`/`#b14e49` measured 4.20:1 on light `background` and
4.00:1 on the Material-3-baseline `surfaceContainerHighest` card the
app's `isError` `OutlinedTextField`s actually render on
(`TrainingPlanEditorFormFields.kt:123`), both below the 4.5:1 WCAG AA text
floor `error` owes as an `OutlinedTextField` label/supporting-text color,
not just a border color; `#a74541` clears both (4.77:1/4.54:1). Both
values resolved via the standard OKLCH→linear-sRGB
matrices (Björn Ottosson's OKLab transform) plus sRGB gamma companding;
the same pipeline resolves this document's own `oklch(0.660 0.125 289.2)`
accent value to `#9184d9` exactly, matching the accent hex already stated
above — cross-checked before trusting the pipeline for these two new
values, since a plain "it compiles" implementation would have no other way
to catch a conversion or transcription error in either.

The `control` role above sits alongside `background`/`surface` on both
themes, not dark only — see the light theme's resolved value below.

**`RepFlowColor.hairline` — new this round (round 5's B2 finding,
execution plan revision 7).** The hairline border value above
(`#3f424d` dark / `#cfd3e5` light, see the light-theme paragraph below) is
exposed as its own token, `RepFlowColor.hairline`, rather than through
`ColorScheme.outline` — `outline` is also the resting border of every
stock `OutlinedTextField`/`Switch` in the app, where this same low-contrast
value fails the 3:1 UI-component floor (1.2–1.8:1 measured, both themes).
`outline` itself is left at Material 3's own baseline instead (`#938F99`
dark / `#79747E` light), which independently confirms to clear 3:1
against both `surface` and `background` in both themes. See the execution
plan's CP1 step 2 and CP3's "Cards" bullet for which primitives read
`RepFlowColor.hairline` directly.

**Divider disposition — new, execution plan revision 11 (round 9's
I2(b)).** The divider value above (`rgba(233,233,237,.09)`, distinct from
the hairline border per the "Color" section) is **not** exposed as a
`RepFlowColor` token or mapped to any `ColorScheme` role this milestone —
unlike `.hairline`, no primitive CP3 builds reads a divider value, so
there is no consumer to wire it to yet. The app's three stock
`HorizontalDivider()` sites (`DividerTokens.Color = OutlineVariant`) keep
reading Material 3's unassigned `outlineVariant` baseline instead. See the
execution plan's CP1 step 2 (`outlineVariant` table row) and "Known
limitations" for the full disclosure — this is deliberately left for a
later milestone that actually touches those three screens.

Full 100–900 tonal ramps, read verbatim from `styles.css` (generated in
OKLCH on a shared lightness scale; the original doc did not have these to
full precision — they're exact now):

| Step | Neutral | Accent | Accent-2 |
|---|---|---|---|
| 100 | `#f3f5fe` | `#f5f4ff` | `#f5f4ff` |
| 200 | `#e4e7f5` | `#e7e5fe` | `#e7e5fe` |
| 300 | `#cfd3e5` | `#d2cefd` | `#d2cefd` |
| 400 | `#b2b6ca` | `#b5abfc` | `#b5afe8` |
| 500 | `#9397ab` | `#968ae0` | `#9690c9` |
| 600 | `#75798c` | `#796cbf` | `#7972a9` |
| 700 | `#595d6c` | `#5d5294` | `#5c5783` |
| 800 | `#3f424d` | `#423a6a` | `#423e5d` |
| 900 | `#292b31` | `#2b2741` | `#2b293a` |

(`--color-section*` tokens exist in `styles.css` too, but are explicitly
"deck-scale fills only — not interface colors"; excluded from the Compose
token set.)

**Light theme, confirmed for real this session** (read directly from
`RepFlow.dc.html`'s `1d` — "Same Home in light theme · plus empty and error
states" — the only place the light variant is actually rendered): it is
**built from the same neutral ramp, not a separate palette** —
background `#e4e7f5` (neutral-200), surface `#f3f5fe` (neutral-100),
hairline `#cfd3e5` (neutral-300 — exposed via `RepFlowColor.hairline`,
not `ColorScheme.outline`; see the hairline note above), text `#292b31`
(neutral-900). Crucially,
**the light theme's "primary" role is `accent-700` (`#5d5294`), not the raw
base accent `#9184d9`** — every accent-carrying element on light (start-
workout button fill, nav selected icon/label, outlined-button text/border)
uses the 700 step for contrast against the light ground; only the dark
theme uses the base 500-ish accent hex directly. This is a concrete,
previously-unstated mapping: `RepFlowLightColorScheme.primary` = accent-700,
`RepFlowDarkColorScheme.primary` = the base accent hex.

**Light theme's `onPrimary` is `#f5f4ff` (accent-100), not dark's
`#161826`** — **new this round (round 3's I1 finding)**. `1d`'s two
primary-button renders ("Start workout", "Create a plan") both use
`background:#5d5294;color:#f5f4ff`. Dark's `onPrimary` (`#161826`) does
not carry over: light's `primary` is a different, lighter step
(accent-700, not the base accent), so a dark-derived label fails contrast
on it (`#161826` on `#5d5294` = 2.60:1, below both WCAG AA's 4.5:1 and the
3:1 UI-component floor) where `#f5f4ff` on the same fill is 6.22:1.

The light theme's `control` role is neutral-400 `#b2b6ca` (**corrected this
round from neutral-300 `#cfd3e5`** — that value was identical to light
`outline`, so control-filled primitives lost their border entirely; see
"Deltas from the original read" below). `6b`'s component-spec panel is
dark-theme-only throughout, and `1d` (the only screen actually rendered in
light theme) has no stepper/chip/scale-row element, so no light-theme
`control` value is design-confirmed either way — this is a judgment call,
flagged for the reviewer the same way CP2's nav-icon picks are, not a
design-derived fact. Neutral-400 is the next ramp step past `outline`'s
300 (neutral-100 is already `surface`'s own value, so reusing it would
trade the `control`/`outline` collision for a `control`/`surface` one
instead). `RepFlowLightColorScheme` has no separate slot for it — expose it
the same way as dark's, via `RepFlowColor.control`.

### Accent-ramp tokens (`RepFlowColor.accent300`/`.accent600`/`.accent700`/`.accent900`)

**New this round (round 3's I2 finding).** CP3's primitives quote four
accent-ramp steps as literal hexes (accent-outline button border/text,
scale-row selected border/text, the accent-tinted card fill/border), but
neither the `ColorScheme` roles CP1 assigns nor its one extra token
(`RepFlowColor.control`) give them a name — the only remaining path for a
CP3 implementer would be a hardcoded `Color(...)` literal inside
`presentation/**`, which is exactly the anti-pattern CP1's own self-review
flag greps for. Named directly off the ramp table above:
`RepFlowColor.accent300` = `#d2cefd`, `.accent600` = `#796cbf`,
`.accent700` = `#5d5294`, `.accent900` = `#2b2741`. These are
theme-independent constants (the ramp itself doesn't vary by theme); which
step a given theme's variant of a primitive picks is stated per-component,
per theme, in the execution plan's CP3 section — see "Primitives" below
for the per-theme picks this round adds.

### Bottom-nav roles (`secondary`/`secondaryContainer`/`onSecondaryContainer`/`onSurfaceVariant`/`surfaceContainer`)

**New this round (round 3's B1 finding).** A stock `NavigationBarItem`/
`NavigationBar` reads five `ColorScheme` roles CP1 did not previously
assign (re-derived from `material3-android-1.4.0`'s
`NavigationBarTokens.kt:24-34`); left unassigned, CP4's bottom nav would
render in Material 3's baseline purple-grey regardless of every other CP1
token. Confirmed pixel-exact against the live design's own bottom-nav
renders — `1a` ("Home → workout focus mode → summary", dark) and `1d`
(light), both a six-item bar with one item selected:

| Role | Dark | Light | Applied via |
|---|---|---|---|
| `secondary` (selected label) | `#e9e9ed` (= `onBackground`/`onSurface`) | `#292b31` (= `onBackground`/`onSurface`) | global `ColorScheme` |
| `onSecondaryContainer` (selected icon) | `#d2cefd` (= `accent300`) | `#5d5294` (= `primary`) | **CP4-scoped `NavigationBarItemDefaults.colors(...)` override — not a global `ColorScheme` assignment** |
| `secondaryContainer` (selected pill) | `primary` @ 20% (`1a`: `rgba(145,132,217,.20)`) | `primary` @ 16% (`1d`: `rgba(93,82,148,.16)`) | **CP4-scoped `NavigationBarItemDefaults.colors(...)` override — not a global `ColorScheme` assignment** |
| `onSurfaceVariant` (unselected icon+label) | `onBackground`/`onSurface` @ 60% (`1a`: `rgba(233,233,237,.6)`) | `onBackground`/`onSurface` @ 55% (`1d`: `rgba(41,43,49,.55)`) — **applied value corrected to 66% (round 7's B1); see below** | **CP4-scoped `NavigationBarItemDefaults.colors(...)` override — not a global `ColorScheme` assignment** |
| `surfaceContainer` (bar container) | **new literal `#1b1d2b`** (`1a`'s own bar fill — distinct from `background`/`surface`/Material 3's own baseline `#211F26` alike) | `surface` (`#f3f5fe`) | global `ColorScheme` |

**Corrected this round (round 5's B1 finding, execution plan revision
7)**: `onSurfaceVariant`'s "Applied via" column changed from global
`ColorScheme` to a CP4-scoped override. It is also
`OutlinedTextFieldTokens.LabelColor`/
`.InputPlaceholderColor`/`.SupportingColor`, `ListTokens
.ListItemSupportingTextColor`/`.ListItemOverlineColor`,
`DialogTokens.SupportingTextColor`, `OutlinedButtonTokens
.LabelTextColor`, and `AppBarTokens.TrailingIconColor` (**corrected round
15's B2: `TextButtonTokens.LabelColor` removed — it is dead code in
material3 1.4.0, referenced nowhere outside its own token file;
`TextButton`'s enabled label reads `fromToken(ColorSchemeKeyTokens
.Primary)` instead, `Button.kt:784`, so `TextButton`'s 34 sites are a
`primary` consumer, not an `onSurfaceVariant` one — see the execution
plan's CP1 step 2 for the corrected role table; `AppBarTokens
.TrailingIconColor` added, a consumer round 16's B1 removes again below**)
— assigning it globally, as round 3 originally did,
gives every one of those stock components (23
`OutlinedTextField`s, 6 `ListItem`s, 4 `AlertDialog`s, 5 `OutlinedButton`s
— **corrected round 16's B1: "10 `TopAppBar` trailing icons" removed, zero
live consumers, see the execution plan's CP1 step 2 role table**) the same
55%-opacity value that was only ever confirmed for the nav's own unselected
state, and in light theme that
value composites to 3.20–3.34:1 against `surface`/`background` —
below the 4.5:1 WCAG AA floor. See the execution plan's CP1 step 2 and
CP4 for the corrected mechanism and the literal scoped-override values.

**Corrected this round (round 7's B1 finding, execution plan revision 9)**:
round 5's fix relocated the design's literal `.55` into CP4 rather than
eliminating the WCAG failure — the value still composites to 3.34:1
against the nav's own container (`#f3f5fe` light), clearing the 3:1 icon
floor but not the 4.5:1 floor the bar's always-visible label text owes
(`alwaysShowLabel` defaults to `true`, and every route's `NavigationBarItem`
call passes a real label). Light's **applied** value is now `.66` (the
first alpha step that clears 4.5:1, composite `#6e7077`, 4.55:1) — a
design-vs-accessibility deviation on the same footing as light
`onPrimary`/`error` below; the table above keeps `1d`'s literal `.55` as
the confirmed design source, and this paragraph states the corrected
applied value, the same convention used for `error`'s two values. Dark's
`.60` is unaffected (5.76:1, unchanged).

**Corrected this round (round 7's B2 finding, execution plan revision
9)**: `onSecondaryContainer`/`secondaryContainer`'s "Applied via" column
changed from global `ColorScheme` to a CP4-scoped override, mirroring
`onSurfaceVariant` above — no value change. `FilterChipTokens
.FlatSelectedContainerColor`/`.SelectedLabelTextColor` read these same two
roles, so assigning them globally (as revision 6 did) incidentally
retinted three untouched screens' stock `FilterChip`s
(`HistoryScreen.kt:245`, `TrainingPlanListScreen.kt:108,114`,
`ExerciseEditorFormFields.kt:116`), composited against those screens'
`background` rather than the nav's own `surface`: light `#5d5294` at 16%
over `background` (`#e4e7f5`) composites to `#cecfe5`, and the selected
label on it measures 4.41:1 — below the 4.5:1 WCAG AA floor, even though
the identical pair measures 4.96:1 over the nav's own `surface` composite
(`#dbdbed`) and **8.19:1 dark** (composite `#33324e` over the nav's own
`surfaceContainer` `#1b1d2b` — **corrected round 8's B1 from 8.68:1**,
previously reproduced against `background` `#161826` instead), where it was
actually design-confirmed. See
the execution plan's CP1 step 2 and CP4 for the corrected mechanism; the
three untouched screens' chips now revert to Material 3's own baseline
pair.

The selected label reusing plain text color (not an accent) rather than
the selected icon's accent color is a real, confirmed asymmetry in the
design, not a simplification — both `1a` and `1d` render it this way.
`Snackbar`'s three `inverse*` roles are deliberately not assigned
alongside these: they are not read by any primitive this milestone
actually authors (the snackbar pattern is unchanged by this milestone) —
see the execution plan's CP1 closing paragraph and CP5's Known
Limitations entry. **`Card`'s `surfaceContainerHighest` is a different
case, corrected round 15's B1: it *is* read** — the call site (the app's
one bare `Card(`, `TrainingPlanEditorFormFields.kt:123`) is pre-existing
and undisturbed, but neither its content nor, as of this round, its own
container value is: 4 `TextButton` labels and 7 focused
`OutlinedTextField` labels are this milestone's own `primary` (round 15's
B2: `TextButton`'s label is `primary`, not `onSurfaceVariant`). Dark
`primary` measured 3.80:1 against the Material 3 baseline container —
milestone-introduced, the same shape as `surfaceContainerHigh` below one
tier up. Dark `surfaceContainerHighest` is now assigned `= surface`
(`#232532`, 4.71:1); light is left unassigned (light `primary` already
clears at 5.23:1 there). **Corrected round 17's I1**: the reassignment
also narrows the container's own separation from the `background` it
sits on — dark from 1.43:1 to 1.16:1, light (unassigned, but `background`
itself moves) from 1.23:1 to 1.05:1; no WCAG floor is owed on a
decorative container edge, but see the execution plan's CP1 step 2 role
table and "Known limitations" for the full disclosure. See the execution
plan's CP1 step 2 for the full disposition.

### `surfaceContainerHigh` — the app's dialog/date-picker container (from round 5's I2 finding; value corrected round 10's B1)

Unlike `Snackbar`'s `inverse*` roles above, `surfaceContainerHigh`
is read by six live, pre-existing components regardless of this
milestone's own scope: `DialogTokens.ContainerColor` and
`DatePickerModalTokens.ContainerColor` (both = `SurfaceContainerHigh` in
material3 1.4.0) back the app's 4 `AlertDialog`s and 2 `DatePickerDialog`s.
Left unassigned, all six render on Material 3's own neutral-purple
baseline (`#2B2930` dark / `#ECE6F0` light,
`PaletteTokens.Neutral17`/`.Neutral92`) — a warmer, visibly off-palette
container against RepFlow's own `#232532`/`#f3f5fe` surfaces, and
(independently checked this round) that baseline does not even clear
4.5:1 against this milestone's own dark `primary`: `#9184d9` on `#2B2930`
measures 4.45:1, still short. No dialog is rendered in either theme
anywhere in the live design, so no literal value is design-confirmed
either way.

**Originally assigned (execution plan revision 7)**: dark
`RepFlowColor.control` (`#292b31`)/light `surface` (`#f3f5fe`), a disclosed
judgment call, on the same footing as light `control` above, rather than
leave the gap unassigned the way `surfaceContainerHighest` was believed to
be correctly left at the time (**corrected round 15's B1: dark
`surfaceContainerHighest` is in fact read, by the app's one bare `Card(` —
see above; this parenthetical's belief was wrong, though it did not affect
`surfaceContainerHigh`'s own disposition here**). Round 7's I1
finding (revision 9, disclosure only at the time) found this drops dark
`DatePickerModalTokens.DateTodayLabelTextColor` (= `primary`, `#9184d9`)
to 4.38:1 against dark `surfaceContainerHigh` (`#292b31`) — 2.7% short of
the 4.5:1 text floor for the date picker's current-date glyph
(`HistoryScreen.kt:388`, `RecoveryFutsalScreen.kt:228`) — and, believing
only that one glyph was affected, left it as a disclosed trade-off rather
than re-tuned.

**Corrected round 10 (B1, execution plan revision 12): the 4.38:1 figure
is not confined to one glyph.** `DialogTokens.ActionLabelTextColor =
Primary` (`DialogTokens.kt:26`) puts every dialog's `TextButton` action
label on the same container — Compose's `TextButton` content color
resolves to `fromToken(Primary)` (`Button.kt:784`) — and four of the
app's six dialogs use `TextButton` for their actions:
`HistoryScreen.kt:176`'s invalidate-session `AlertDialog` (confirm/cancel),
`BackupScreen.kt:62`'s restore `AlertDialog` (confirm/cancel),
`HistoryScreen.kt:361`'s `DatePickerDialog` (confirm/clear/dismiss), and
`RecoveryFutsalScreen.kt:207`'s `DatePickerDialog` (confirm/dismiss) —
nine labels total, all measuring the identical 4.38:1, two of the four
dialogs being destructive confirmations. (The other two dialogs use filled
`Button`, whose `onPrimary`-on-`primary` label is genuinely unaffected.)
The dialog's other two text roles were also previously conflated: the
headline (`HeadlineColor = OnSurface`, 11.69:1) and supporting text
(`SupportingTextColor = OnSurfaceVariant`, `:34`, baseline `#CAC4D0` dark,
8.30:1) are separate roles, not one `onSurface` figure.

**Re-decided**: dark `surfaceContainerHigh` is now assigned `= surface`
(`#232532`) instead of `RepFlowColor.control` — light is unchanged (light
`surfaceContainerHigh` already equalled light `surface`). This clears
every affected label (dark `primary` on `#232532` = **4.71:1**) and every
other consumer (`onSurface` headline `12.55:1`; `onSurfaceVariant`
supporting text `8.91:1`). The cost is retiring the "raised tier"
distinction this assignment previously introduced from `surface`/`control`
in dark theme — both themes now apply the identical rule
(`surfaceContainerHigh = surface`). See the execution plan's "Round 10
plan-review decisions (revision 12)," CP1 step 2 and step 7, "Known
limitations," and "Areas the reviewer should specifically challenge" for
the full disposition and the alternative (a scoped `TextButton`
color-override fix) surfaced for the external reviewer instead.

### `DropdownMenu`/`surfaceContainer` cascade (new this round, round 5's I1 finding)

`MenuTokens.ContainerColor = SurfaceContainer` backs every stock
`DropdownMenu`'s container (`Menu.kt:193-194,402`), so the bottom-nav's
`surfaceContainer` value above cascades to all 7 `DropdownMenu` call
sites in the app (`HistoryScreen.kt:263,299`,
`TrainingPlanEditorFormFields.kt:233`, `ActiveWorkoutScreen.kt:128,255`,
`ExerciseListScreen.kt:249`, `TrainingPlanListScreen.kt:199`), none of
which any checkpoint touches. Light `surfaceContainer` = `surface`
(`#f3f5fe`) means an open light-theme menu has no visible container line
against the surface it floats on — delimited by its shadow alone. Dark's
`surfaceContainer` (`#1b1d2b`) is measurably darker than `surface`
(`#232532`), so an open dark-theme menu reads as recessed rather than
elevated, inverting Material 3's usual convention. Both are visual-only
consequences of reusing the bottom nav's own literal value through the
shared role — no accessibility floor is implicated, unlike the
`onSurfaceVariant`/`outline` cases above.

### Type

Confirmed exact, direct from `6b`'s own spec table — six slots, unchanged
from the original doc:

| Slot | Size/weight | Notes |
|---|---|---|
| display | 32/500 | |
| title | 25/500 | |
| numeric | 32/500 | tabular figures — loads, reps, timers, volumes |
| body | 15/400 | |
| meta | 12.5/400 | |
| label | 11/500 | uppercase, `letter-spacing: .09em` |

**Correction**: the original doc said "never bolder than weight 500." The
`6b` spec sheet's own caption says *"Inter throughout, never heavier than
600"* — and the primary-button primitive (below) is literally set at
`font-weight: 600`. So the real rule is: every text slot above stays at
400/500, but the primary-button label is a deliberate, singular exception
at 600. `RepFlowTypography.kt` needs a 600-weight allowance for exactly
that one usage, not a blanket 500 cap.

### Spacing and radius — corrected, not just filled in

The original doc assumed CP1 would re-read `styles.css`'s `--space-*`/
`--radius-*` tokens as RepFlow's own scale. Having now read both
`styles.css` **and** `6b`'s literal "Layout rules" panel, they disagree,
and `6b` is the one that matches every actual screen:

- `styles.css`'s generic `--space-*` scale is `2.8/5.6/8.4/11.2/16.8/22.4px`
  (named `--space-1/2/3/4/6/8` — note `-5` and `-7` are not defined; it's a
  6-value scale, not 8 despite the naming) and `--radius-sm/md/lg` is
  `4/8/14px`. **RepFlow's own screens do not use these values anywhere
  observed** (no 4px radius, no fractional spacing appears in any inline
  style across the ~4,700-line document).
- `6b`'s "Layout rules" panel, which is RepFlow's own literal spec, states
  instead: **screen padding 16 · card padding 14–18 · gaps 6/8/10/12 ·
  radii 8 (controls) / 10–12 (cards and buttons) / 14–16 (sheets) · rows
  56–68 tall · every tap target ≥44.** These are the values every screen in
  the document actually uses (verified spot-check: the Exercise-list row is
  64px tall with 12px internal gaps; the stepper buttons in the set-entry
  pad are 44×44 with 9px radius; the accent-surface card in the
  recommendation screen uses 14px radius; the restore dialog uses 14px
  radius; the FAB is 60×60 with 18px radius).

**`RepFlowShapes.kt`/`RepFlowSpacing.kt` should be authored from `6b`'s
literal values (8/10-12/14-16 radii; a simple integer spacing scale
covering 6/8/10/12/14/16/18), not from `styles.css`'s generic fractional
scale.** This is the single highest-impact correction from this
revalidation — the original plan's CP1 step 4 would have produced tokens
that match no real screen in the design.

### Elevation

Unchanged from the original doc, exact: `--shadow-sm: 0 0 0 1px #3f424d`;
`--shadow-md: 0 0 0 1px #595d6c, 0 6px 18px rgba(0,0,0,.55)`;
`--shadow-lg: 0 0 0 1px #9397ab, 0 16px 40px rgba(0,0,0,.65)`. Edge-plus-
ambient-darkness, never a flood color — confirmed again in every card/sheet
instance read this session.

### Icons

Phosphor, regular/fill/bold weights, confirmed unchanged
(`@phosphor-icons/web@2.1.1`). Exact glyph names now enumerated from real
usage in the surfaces this milestone touches (previously only "a handful of
icons" was asserted, not itemized):

- Exercise list (CP5): `ph-arrow-left` (back), `ph-magnifying-glass`
  (search), `ph-x-circle` (clear search), `ph-funnel` (filter chip),
  `ph-dots-three-vertical` (row menu), `ph-archive` (archived badge),
  `ph-bold ph-plus` (FAB), `ph-arrow-counter-clockwise` (undo snackbar).
- Active Workout set-entry + rest timer (CP6): `ph-minus`/`ph-plus`
  (steppers), `ph-fire` (warm-up toggle), `ph-pencil-simple` (edit a
  logged set row), `ph-arrow-counter-clockwise` (undo last set),
  `ph-sliders` (expand RPE/pain/technique detail), `ph-caret-down`/
  `ph-caret-up` (detail toggle caret), `ph-info` (exercise-type note),
  `ph-x` (dismiss rest strip), `ph-pulse` (progression-suggestion banner,
  out of scope itself but its icon is visible from the set-entry pad).
- Bottom nav (CP4): the current app has **six** destinations
  (Exercises/Workout/Plans/Recovery/History/Backup), not the target
  design's four (Home/Plans/History/Progress) — that IA change stays out of
  scope (see execution plan's non-goals). The design only shows icons for
  the *target* four: `ph-fill ph-house` (Home), `ph-list-checks` (Plans),
  `ph-clock-counter-clockwise` (History), `ph-chart-line-up` (Progress).
  **Four** of the current app's six destinations have no design-confirmed
  glyph: Exercises and Backup (no equivalent at all in the target four),
  plus Workout (folds into Home in the target IA, no standalone glyph
  shown) and Recovery (appears only as an `ph-moon-stars` empty-state icon,
  not as a confirmed nav-tab icon). CP2 must pick reasonable Phosphor icons
  for these directly (e.g. a dumbbell/book glyph for Exercises, a
  cloud/export glyph for Backup, reusing `ph-moon-stars` for Recovery,
  something workout-shaped like `ph-barbell` for Workout) and flag the pick
  as a judgment call, not a design-confirmed one — this is new information
  from this revalidation, not present in the original doc.

### Primitives — sharper than the original doc had it

Read directly from `6b`'s "Primitives" panel plus in-context usage
elsewhere in the document:

- **Buttons are three tiers, not two.** Primary: 56 min-height, 12px
  radius, solid `#9184d9` fill, `#161826` text, weight 600, 16px — the
  confirmed deliberate deviation from Nocturne's outline-only rule,
  unchanged from the original doc. **Accent outline, stated per theme as of
  this round (round 3's I3 finding)**: dark 48 min-height, 10px radius,
  `#5d5294` (accent-700) inset border, `#d2cefd` (accent-300) text, 14.5px
  — unchanged, sourced from `6b`. Light: **same dimensions, `#796cbf`
  (accent-600) inset border, `#5d5294` (accent-700) text** — confirmed
  directly from `1d`'s three renders ("Log recovery — 20 seconds", "Retry",
  "Empty workout," all `box-shadow:inset 0 0 0 1px #796cbf;color:#5d5294`;
  text 6.23:1, border 4.10:1 against light `surface`). The two themes read
  *different* ramp steps, not the same pair — applying dark's pair to light
  gives `#d2cefd` text on `#f3f5fe`, 1.38:1, effectively invisible.
  Neutral outline: 44 min-height, 8px radius, `#3f424d` inset border,
  ~80%-opacity text, 14px. The original plan's CP3 only named "primary" and
  "secondary" — add the neutral-outline tier as a third `RepFlowButtons.kt`
  variant.
- **Stepper** (weight/reps entry): in the actual set-entry pad (not the
  generic spec-panel demo, which shows a slightly larger 48×48/32px-value
  variant) the real usage is 44×44 minus/plus buttons, 9px radius, `#3f424d`
  inset border, and a 24px/500 tabular center value — smaller than the
  spec panel's own generic demo. Build the primitive parameterized (size,
  value-text-style) so both the in-context 44/24 usage and a larger 48/32
  variant are reachable from one component, rather than hardcoding one.
- **Scale row** (0–5 recovery scale, reused pattern for RPE-style pickers):
  44px cells, 8px radius, `#3f424d` inset border default; selected state
  `rgba(145,132,217,.22)` fill (accent @ 22%) with `#796cbf` (accent-600)
  inset border and `#d2cefd` (accent-300) text — dark, sourced from `6b`,
  unchanged. **Light theme: not design-confirmed (round 3's I3 finding)**
  — `1d` has no scale-row render at all, the same situation round 2's I2
  found for light `control`. Until a later milestone's design pass
  confirms a value, reuse the confirmed light accent-outline pair
  (`#796cbf` border/`#5d5294` text, no fill) as a disclosed judgment call.
- **Status chips**: full-pill (`border-radius: 999px`), several states
  (done: accent-tinted fill + icon; pending: 10%-neutral fill; outline
  variants; an "up next" all-caps micro-pill) — never color alone, always
  paired with an icon or word (explicit rule in `6b`'s color notes). The
  "done"/accent-tinted state is sourced from `6b` and is dark-only for the
  same reason as scale-row above; its light-theme value is likewise an
  unconfirmed judgment call (round 3's I3 finding), treated the same way.
- **RPE/pain/technique pickers** (CP6): each is a horizontal row of
  full-pill buttons, 42px min-height, 13px font — same pill-button pattern
  as status chips and the warm-up toggle, not a bespoke control. This
  confirms CP3's `RepFlowTag`-adjacent primitive should cover this
  interactive-pill case too, not just static tags.
- **Cards**: 14–16px radius, `#3f424d` inset-border default, solid
  `#232532` surface fill; the accent-tinted card variant (recommendation/
  suggestion surfaces) uses `#2b2741` fill with `#5d5294` inset border —
  unchanged from the original doc, now with exact radius confirmed.

### Layout rules, verbatim from `6b`

> Screen padding 16 · card padding 14–18 · gaps 6/8/10/12 · Radii: 8
> controls · 10–12 cards and buttons · 14–16 sheets · Rows are 56–68 tall;
> every tap target is at least 44 · Elevation is a hairline plus ambient
> shadow, never a lighter fill alone · One primary action per screen,
> pinned to a bottom bar · Sheets for choices, dialogs only for destructive
> confirmation · **Bottom nav on the four top-level destinations only** ·
> Workout mode replaces the nav — an X or Finish is the only way out.

The last two bullets both describe the **target** IA (4-tab nav; a
nav-hiding full-screen workout mode). Neither is adopted by this milestone
— the execution plan's non-goals (no navigation/IA change) still hold.
CP6's Active Workout reskin keeps the existing 6-tab shell visible; it does
not adopt the design's nav-hiding "workout mode" behavior, since that is a
structural/IA change reserved for the later navigation milestone.

## Design-vs-repo screen-file correspondence

Re-read directly from the live project's `github.md` this session (not
re-derived from the old doc). Its own header states: repo
`RodrigoFAbreu/repflow-android`, branch `main`, **last sync
2026-08-11T00:30:20Z**. Repo-research this session confirms this is still
accurate as a "nothing has moved since" baseline: no commit under
`app/src/main/kotlin` postdates Milestone 8's close, `presentation/
designsystem/` does not exist, and `RepFlowTheme.kt` is still the original
`lightColorScheme()`/`darkColorScheme()` placeholder with the "open product
decision" doc comment — i.e., the design's own repo snapshot and the
current repository are still the same commit-era, so no file this milestone
touches has drifted underneath it.

`github.md`'s screen-map table is far larger than the original reference
doc represented — it names **every** screen file across the whole app
(Home, Plans, History, Progress, Settings, Recovery, Backup, both editors,
etc.), because the live design project is already a full-app redesign
target, not a single-milestone artifact. This does not expand *this*
milestone's scope (still bounded to bottom nav, Exercise list,
Active Workout set-entry+rest timer, per the execution plan's explicit
non-goals — top app bars are not a separate proven surface, see CP4) — it
just confirms later redesign milestones already have deep design fidelity
to draw on when their turn comes.

## Font and icon sourcing decisions, spelled out

Unchanged from the original doc — both decisions still stand and neither
was contradicted by this session's re-read:

- **Font**: bundle Inter as local static-weight files (400 and 500; the
  new 600-weight primary-button exception below still only needs one more
  static weight, not a variable font) under `res/font/`, rather than use
  Compose UI's Google Fonts downloadable-font provider — avoids a new
  Gradle dependency and a first-use network fetch, consistent with the
  offline-first rule. **Correction carried over from "Type" above**: since
  the primary button is confirmed at weight 600, bundle 400/500/600 (not
  400/500) — a small, still-static, still no-new-dependency change from the
  original plan.
- **Icons**: bundle a hand-picked Phosphor subset as local vector drawables
  rather than add a full icon-library Gradle dependency — unchanged
  rationale. The subset is now enumerated exactly (see "Icons" above)
  rather than left as "enumerated precisely once CP3's consuming components
  are drafted."

## Why two representative surfaces, and why these two

Unchanged from the original doc. Exercise list and Active Workout set-entry
were chosen because, together, they exercise most of the primitive types
CP3 introduces: cards, tags/pills, buttons, loading/empty/error states,
search input, and — uniquely to Active Workout — the numeric-tabular type
style. This session's fuller read of both surfaces (above) only sharpens
the primitive list, it does not change the choice of surfaces. See the
execution plan's "Areas the reviewer should specifically challenge" for the
standing invitation to contest this choice.

## Deltas from the original (discarded-cycle) read

For a reviewer who saw the earlier version of this document, exactly what
changed this session, in order of impact:

1. **Radii and spacing are corrected, not just filled in.** The generic
   Nocturne `styles.css` scale (`--radius-sm/md/lg` 4/8/14; `--space-*`
   2.8/5.6/8.4/11.2/16.8/22.4) is **not** what any RepFlow screen actually
   uses. RepFlow's own `6b` component-spec panel gives the real values
   (radii 8/10-12/14-16; screen padding 16; card padding 14-18; integer-px
   gaps 6/8/10/12) — these are the ones now in the token tables above. This
   is the change most likely to have produced visibly wrong output if
   implemented from the original doc.
2. **Light theme's primary color is `accent-700` (`#5d5294`), not the base
   accent hex** — confirmed by directly reading turn `1d`, which the
   original doc referenced but had not actually pulled numbers from.
3. **Destructive/pain color has a distinct light-theme value**
   (`oklch(0.52 0.13 25)` — **corrected in execution-plan round 6 (B1)
   from `oklch(0.55 0.13 25)`, which failed WCAG AA on the app's actual
   validation-message render contexts** — vs. dark's `oklch(0.72 0.13
   25)`) — new information; the original doc only had the dark value.
4. **Typography's weight ceiling is 600, not 500**, with the primary button
   as the sole 600-weight usage — the original doc's "never bolder than
   500" was a rounding-down of Nocturne's *generic* system rule; RepFlow's
   own spec sheet states 600 explicitly and uses it.
5. **Buttons are three tiers (primary/accent-outline/neutral-outline), not
   two** — the original doc's CP3 only planned for primary/secondary.
6. **A third surface tier, `control` (`#292b31`), exists** alongside
   background/surface — used for stepper buttons and similar chip-like
   controls; not previously captured.
7. **The exact Phosphor icon set is now enumerated** (see "Icons" above)
   rather than left for CP3 to derive; four of the current app's six nav
   destinations (Exercises, Workout, Recovery, Backup) have no
   design-confirmed icon and need a flagged judgment call.
8. **`RepFlow Roadmap.dc.html`, inspected this session, is a product
   feature roadmap (MVP/v1.1/Later), not a redesign-milestone sequencing
   document** — easy to mistake for the latter given the reference doc's
   own R0/R1 milestone-decomposition language. It describes a prototype
   feature scope (multi-day plans, editable logged sets, custom exercises,
   supersets, unit settings, etc.) that does not correspond 1:1 to this
   repository's actual implemented feature set, and says nothing about
   visual-foundation sequencing. It is **not** a source this milestone's
   planning should draw scope from — noted here only so a future reader
   doesn't mistake it for one.
9. **`android-frame.jsx` and `support.js` are Claude Design tooling
   infrastructure**, not RepFlow product or design content — a generic
   Android device-frame preview scaffold and the design canvas's own
   generated runtime bundle, respectively. Confirmed by reading both;
   neither should inform the Compose implementation.
10. **No project comments exist** (`list_comments` returned empty) — no
    pending reviewer feedback or open questions recorded on the live
    project.
11. Everything else material to this milestone — the mono-accent scheme,
    the 100-900 ramp values, the elevation model, the six-slot type scale's
    sizes, the deliberate solid-primary-button deviation, the two-
    representative-surface choice — is **confirmed unchanged**, now backed
    by exact re-reads instead of the original session's approximated/
    not-yet-pulled values.
