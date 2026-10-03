# repflow-redesign-visual-foundation-remediation-1 — Execution Plan (Revision 20)

Remediation child of `repflow-redesign-visual-foundation`, created by
`/apply-functional-review`'s broad branch to discharge functional-review
finding **F1**: *the application has not been converted closely enough to the
approved Claude Design designs.*

- Work item: `repflow-redesign-visual-foundation-remediation-1`
- Parent: `repflow-redesign-visual-foundation` (blocked at
  `AWAITING_FUNCTIONAL_REVIEW` until this item is `MILESTONE_COMPLETE`)
- Governing workflow version: `2.1`
- Base commit: `3257ee03d3d12c7b1412cf3a09e2cbda2179b868`
- Plan revision: 20 (see "Plan review dispositions" for what local rounds 1,
  2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18 and 19 and
  manual-external rounds 1, 2, 3 and 4 changed)

## Goal

Converge RepFlow materially onto the approved Claude Design product — its
information architecture, screen compositions, controls and interaction
shapes — using the live Claude Design project as the product and visual
source of truth. The existing Compose screens are **implementation
substrate, not a constraint to preserve**.

The parent milestone built the design *foundation* (tokens, type, shapes,
icons, primitives) and applied it to three surfaces. That foundation is
correct and is kept in full. What F1 rejected is that the app still renders
the pre-redesign screens with new paint on them. This milestone replaces the
screens.

## Scope decision, and its authorization

The design describes an app that RepFlow does not yet implement. Converting
to it "closely" therefore ranges from a presentation-only restructure to a
multi-milestone product programme. The scope boundary was put to the user at
the planning gate, with three options, and the user chose the middle one and
stated it explicitly:

> Choose **1 — IA + screens with domain backing**. The objective of this
> remediation is to converge RepFlow materially toward the Claude Design
> product, not just restyle the existing application. That includes adopting
> the design's 4-tab IA — Home / Plans / History / Progress — plus Settings,
> moving Exercises / Recovery / Backup inward as shown by the design, and
> implementing the Home, Settings, Progress, readiness-score and
> progression-recommendation surfaces where the existing domain already
> provides the necessary backing. You are authorized to finalize the open
> **Navigation structure** decision in `docs/TECHNICAL_DECISIONS.md`
> accordingly. Do **not** expand remediation-1 into capabilities for which
> RepFlow currently has no domain support […] Capture those as future product
> milestones instead. Plan this as a true design-convergence milestone […]
> Any material deviation from the design should be explicit and justified.

Two consequences are load-bearing for everything below.

1. **The "Navigation structure" open decision is closed by this milestone**,
   on that explicit authorization, in favour of the design's four-destination
   bottom nav. CP16 writes it. This is the only open decision this plan
   finalizes; see "Open decisions this plan touches" for the ones it must
   *not*.
2. **Domain backing is the scope test.** A design surface is in scope when
   the domain and application layers already carry what it needs. A surface
   that would require a new domain concept is out, named as a future
   milestone, and recorded in the deviation register rather than
   half-built.

## What the repository already says it wants

This is not a new product direction being introduced at remediation time.
`docs/UX_FLOWS.md` — a protected, pre-existing document, unchanged since long
before the redesign — already specifies the design's shape, and the *current*
app is what diverges from it:

| `docs/UX_FLOWS.md` says | Design shows | RepFlow today |
|---|---|---|
| Home shows recommended workout, resume-active, recent summary, recovery context, quick access to plans/history/settings | `4a` Home | **no Home screen at all** |
| "The active workout action must have the strongest visual priority" | Home's `Start workout` primary | buried in a tab |
| "The primary screen focuses on **one exercise at a time**" | `4a` focus mode | one flat list of every exercise |
| "Increase or decrease load / repetitions" | steppers | free-text `OutlinedTextField`s |
| Record RPE, technique quality, pain | `4a` scale rows | text fields |
| Complete workout shows "relevant progression recommendations" | `6a`/`6c` | **no progression UI exists** |
| Interrupted workout: home offers Resume / End / Discard | Home resume card | no such affordance |
| Recovery input "should use scales or selectable options" | `3c` scale rows | `-`/value/`+` stepper rows with no end labels (and free-text number fields for the futsal figures) |

So the conversion this milestone performs moves the app *towards* its own
documented UX contract, not away from it. Where this plan and `UX_FLOWS.md`
disagree, that is called out explicitly below (there is exactly one such
place: "Skip or substitute the exercise", deferred with the other
no-domain-backing capabilities).

## Source-of-truth discipline

The live Claude Design project is authoritative for the intended product and
visual design:

- project **`Repflow mobile app design`**, id
  `9e1d47b2-48ae-45bc-a101-c59c37c49550`;
- file `RepFlow.dc.html` — a canvas of six turns; the newest turn wins where
  two turns draw the same surface;
- the design-system bundle
  `_ds/nocturne-3f5c4219-8153-42d4-afb3-9364451b6478/styles.css`;
- `RepFlow.dc.html`'s own component spec, artboard **`6b`**, which states it
  is the Compose token source: *"Values are the literal ones used in the
  prototype — a Compose theme should carry them as tokens rather than
  re-deriving them per screen."*

This repository is authoritative for implementation reality — current
architecture, the existing domain and application layers, and current test
assertions.

**Reading the design is a per-checkpoint obligation, not a one-time
transcription.** Every checkpoint that builds a screen begins by reading that
screen's artboard from the live project, not from this plan's summary of it.
This plan names artboards so a reader can find them; it does not replace
them. CP1 exists to make that cheap and checkable.

### Artboard inventory

| Artboard | Subject | Status for this milestone |
|---|---|---|
| `6b` | Component spec — tokens, primitives, layout rules | Authoritative for all structural values |
| `6a`, `6c` | Progression recommendation, and its other two outcomes | CP6 |
| `5a` | Plans — filters, archive/restore | CP11 |
| `5b` | Progress — one exercise, one metric | CP15 |
| `5c` | Settings — grouped by when you'd change it | CP14 |
| `5d` | Backup and restore | CP14 |
| `4a` | **Full flow** — Home, tabs, preview, board, focus, finish, sheets | CP5, CP7, CP8, CP9, and the nav in CP2; the pre-start preview alone is not converted (deviation `D12`) |
| `3a`, `3b` | History list, workout detail | CP12 |
| `3c`, `3d` | Recovery + futsal entry, recovery history | CP13 |
| `2a` | Plan/workout builder | CP11 |
| `2b` | New exercise | CP10 |
| `2c` | Exercise library | CP10 |
| `1a`–`1c` | Earlier core flow, plan detail, exercise detail | Superseded by `4a`/`5a`; consult only where `4a` is silent |
| `1d` | Light theme, empty and error states | Light-theme reference for every checkpoint |

### `6b`'s layout rules, quoted, because they bind every checkpoint

- Screen padding 16 · card padding 14–18 · gaps 6 / 8 / 10 / 12
- Radii: 8 controls · 10–12 cards and buttons · 14–16 sheets
- Rows are 56–68 tall; every tap target is at least 44
- Elevation is a hairline plus ambient shadow, never a lighter fill alone
- **One primary action per screen, pinned to a bottom bar**
- **Sheets for choices, dialogs only for destructive confirmation**
- **Bottom nav on the four top-level destinations only**
- **Workout mode replaces the nav — an X or Finish is the only way out**
- Type: display 32/500 · title 25/500 · numeric 32/500 · body 15/400 ·
  meta 12.5/400 · label 11/500; Inter, never heavier than 600; every number
  the user compares is tabular
- "Destructive and accent are never the only signal — every state also
  carries an icon or a word"

The last four are the ones the current app breaks most often, and they are
what turn this from a restyle into a conversion.

## Explicit non-goals

Carried directly from the user's scope decision. Each is a **future product
milestone**, to be recorded in `docs/improvements/IMPROVEMENT_ROADMAP.md` by
CP16, not a limitation to be worked around here:

- **Supersets** (design shows `Superset A`/`B` grouping on the board, in the
  plan editor and in focus mode). No domain concept exists.
- **Exercise swap / substitute** (design's swap sheet with same-muscle-group
  alternatives). No domain concept exists; also blocked on the open decision
  "How substitutions affect progression history".
- **Per-exercise and per-session notes** (design's note sheet, note chips on
  board rows, session note on the finish screen and in workout detail). No
  domain field exists.
- **An "active plan" concept and multi-day plans** (design's `Active plan`
  badge, `Day 1..4` tabs, "day 2 of 4", "2 of 4 sessions this week").
  `TrainingPlan` has no day dimension and no active flag.
- **kg/lb unit switching** (design's Settings `Units` group). Every load in
  the domain is kilograms.

Additionally out of scope, for the same "no domain backing" reason:

- **Deload planning** (design's "Plan a deload week" card and the deload
  state that rewrites targets to 85%/3 sets). Requires a programme-level
  concept the domain does not have.
- **Editing a completed workout's sets** (design's pencil on set rows in
  workout detail, artboard `3b`). This is blocked by an open decision —
  "Whether completed workouts may be manually corrected" — which this plan
  must not finalize.
- **Muscle-group taxonomy** (design filters the exercise library by `Chest`,
  `Back`, … and shows `Chest · Weight & reps` as row meta). `Exercise` has
  no group field.
- **First-run onboarding**, which the design project lists as a "try next"
  and never draws.

Not goals, but not deferrals either — these stay exactly as they are:

- No change to `ProgressionPolicyV1`'s formulas, thresholds or reason
  strings. CP6 renders its existing output; it does not tune it.
- No change to set classification, undo/edit scope, session invalidation,
  plan-version immutability, or single-active-session enforcement.
- No change to the backup transfer schema's existing shape beyond the one
  additive concern CP14 raises and resolves (see "Backup compatibility
  impact").

## Preserved invariants

Re-verified against the tree at the base commit, not carried over:

- **Domain purity.** `LayerBoundaryTest` must keep passing. **Two** new pure
  computations land in `domain/`, both plain Kotlin with no Android import:
  the readiness score (`domain/recovery/ReadinessScore.kt`, CP4 item 1 — which
  is also this plan's self-declared single largest piece of invented product
  logic, see "Areas the reviewer should specifically challenge") and estimated
  1RM (CP15). Everything else this milestone adds is presentation or
  application-layer read models over existing repositories, **with one
  checkpoint deliberately excepted**: CP14 adds a new application-layer port
  (`TrainingDataRepository`, item 6), a new `EraseAllData` use case, a
  `SettingsRepository` (item 7), a Room table and `MIGRATION_7_8`. None of
  those is a read model over an existing repository; each is stated in CP14's
  own items and in "Migration and schema impact", and this bullet names them
  so the layer summary is not read as covering them.
- **Dependency direction** Domain → Application → Data/Infrastructure →
  Presentation, unchanged.
- **Rest timer reconstruction.** `RestTimer`'s absolute `endAt` and
  `ActiveWorkoutScreen`'s `Instant.now()`-derived tick loop are carried
  through the board/focus rebuild unchanged. CP8 re-renders the timer; it
  must never replace the derivation with a stored countdown.
  `docs/UX_FLOWS.md`'s "Returning to the application must reconstruct the
  timer from the stored end timestamp" is the same requirement.
- **Undo/edit scope** stays the most-recently-recorded set per exercise
  (`UndoLastWorkoutSet`/`EditLastWorkoutSet`).
- **Session invalidation stays soft**, never a hard delete; invalidated
  sessions stay excluded from progression and, newly, from CP15's charts —
  which is what the design's own note demands ("Only valid sessions count").
- **Warm-up/working classification** (`WorkoutSet.isWarmup`) and its
  progression filter, untouched.
- **Offline-first.** No network dependency anywhere, and nothing in a
  workout may require one.
- **Room stays at version 7 unless CP14 needs a table** — see "Migration and
  schema impact", which resolves this one way and states the test it owes.

**One invariant on this list is deliberately narrowed rather than preserved,
and is called out here so it is not read as an oversight.** Restore's
"replaces all local data atomically" invariant stays *atomic* but stops
covering *all* tables: CP14 item 9 scopes the clear to training data so the
preferences row survives a restore. The narrowing is the point of the fix,
and CP14 owes both halves of making it honest — rewriting
`LocalBackupRepository`'s KDoc, which states the wider claim today, and
retargeting `LocalBackupRepositoryAtomicityTest`, which proves it.

## Checkpoints

Generated from
`docs/ai-workflow/registry/repflow-redesign-visual-foundation-remediation-1-registry.json`
— do not hand-edit this table.

| id | name | depends_on | complexity | session_target |
| --- | --- | --- | --- | --- |
| CP1 | Design inventory and deviation register (side-by-side baseline) | - | 2 | 2 |
| CP2 | Navigation: four-tab IA, Settings route, workout-mode nav suppression | CP1 | 3 | 2 |
| CP3 | Structural primitives the design needs beyond the visual foundation | CP1 | 3 | 3 |
| CP4 | Readiness score derivation and readiness detail surface | CP3 | 3 | 2 |
| CP5 | Home screen | CP2, CP3, CP4 | 3 | 3 |
| CP6 | Progression recommendation surface on the existing policy | CP3, CP4 | 3 | 2 |
| CP7 | Workout board screen | CP2, CP3 | 3 | 3 |
| CP8 | Workout focus mode: steppers, keypad, scale rows, bottom action bar | CP3, CP7 | 3 | 3 |
| CP9 | Workout finish and session summary screen | CP6, CP7, CP8 | 2 | 1 |
| CP10 | Exercise library conversion | CP2, CP3 | 2 | 2 |
| CP11 | Plans list and plan editor conversion | CP2, CP3 | 3 | 3 |
| CP12 | History and workout detail conversion | CP2, CP3 | 2 | 2 |
| CP13 | Recovery entry and recovery history conversion | CP3, CP4 | 2 | 2 |
| CP14 | Settings screen, preference persistence, and the behaviours it gates | CP2, CP3 | 3 | 3 |
| CP15 | Progress tab | CP2, CP3 | 3 | 3 |
| CP16 | Verification, side-by-side design validation, and decision/doc updates | CP5, CP9, CP10, CP11, CP12, CP13, CP14, CP15 | 2 | 2 |

**The execution order is fully determined, and the checkpoints below state it
as fact rather than branching on it.** `depends_on` constrains the *graph*; it
is the registry array's own order that decides what runs when.
`workflow_state.select_next_checkpoint` (`scripts/workflow_state.py:3093`,
rule 2) returns "the first checkpoint in the registry JSON array's own order
whose status is not `COMPLETE` and whose every `depends_on` entry is
`COMPLETE`", and `validate_registry_topological_order` guarantees at
registry-write time that the array order is itself a valid topological order.
The array is `CP1 … CP16`, so replaying rule 2 against this registry yields
exactly **CP1 → CP2 → CP3 → … → CP16**. Rule 1 only resumes a checkpoint
already `IN_PROGRESS` and rule 4 raises rather than skipping, so neither
reorders anything.

Three consequences the checkpoint text below relies on, stated once here
instead of being re-hedged at each site:

- **CP6 lands before CP7 and CP8.** The recommendation screen exists before
  the picker sheet conversion and before the focus screen.
- **CP9 lands before CP14.** The finish confirmation exists before the
  preference that gates it.
- **CP2 lands before CP5, CP7, CP14 and CP15 — every checkpoint it hands work
  to.** CP2 changes the destination set for the whole app while the screens
  that own the new destinations are still four, five and twelve checkpoints
  away, so it carries more forward references than any other checkpoint here
  and every one of them runs through one of its placeholders. Those
  references are enumerated as a contract in CP2 item 4 rather than left in
  prose, for the same reason this paragraph exists.
- **Where two checkpoints each build half of one user-facing path, the second
  to land wires them together and owns the test for it** — that is the first
  point at which both halves exist. This puts CP6/CP8's `Why ›` route on
  **CP8** (CP8 item 5), CP9/CP14's confirmation gate on **CP14** (CP14
  item 4), CP5/CP9's Home `Finish it` → finish sheet on **CP9** (CP9 item 1),
  CP7/CP9's leave-sheet `Finish and save it now` on **CP9** (CP9
  item 1), and the workout-mode back-gesture interception on **CP7** (CP7
  item 1), because the leave sheet it opens is CP7's and CP2 has none to
  intercept into. CP5/CP7's leave-and-resume path is CP7's for the same
  reason (CP7 item 1).

A pair the dependency graph leaves unordered is therefore still ordered in
practice, and adding an edge that merely restates the array order would change
the graph without changing execution.

### CP1 — Design inventory and deviation register

The baseline every later checkpoint is checked against, and the artefact F1's
acceptance criteria 3 and 4 are ultimately graded on.

1. Read every artboard in the inventory table above from the **live**
   project, in full.
2. Produce `docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md`
   containing, per screen:
   - the artboard id(s) that define it;
   - its regions top-to-bottom, with the design's own values for padding,
     gap, radius, row height and type slot, cited from `6b` where `6b`
     states them and from the artboard's own markup where it does not;
   - every interactive element and what it does;
   - the Compose file(s) that will own it after conversion;
   - **the existing tests that read what conversion will change** — the test
     files and, where it is a subset, the method names that depend on this
     surface's nodes, labels, content descriptions, destination entries or
     input affordances, each found by grepping for the subject rather than by
     recall, and each marked *survives* or *rewritten*;
   - **every deviation**, each with: what the design shows, what will be
     built, and the reason — one of `no domain backing`, `blocked by open
     decision`, `platform convention`, or `deliberate product call`.
3. Seed the register with the deviations this plan already names (see
   "Deviation register"), so a later checkpoint adds to a list rather than
   inventing a format.
4. No production code changes in this checkpoint.

**Why the inventory carries a tests column**, added in revision 8: the plan's
two existing tools — the execution-order paragraph and CP2's
placeholder-contract table — both answer *what does this checkpoint hand
forward?*. Neither answers *what existing test reads what this checkpoint
changes?*, and that second question is what produced findings I6, I8, I14 and
I15 across four separate rounds, each time one checkpoint at a time. CP1 is
already the checkpoint that reads every surface once and writes down what will
own it; adding the tests that pin each surface makes the whole class visible in
one pass, before any conversion starts, rather than per-checkpoint in review.
It is a column on a document CP1 already produces — no new artefact, no
registry change, and no change to CP1's `session_target`.

**What seeds the column, corrected in revision 9.** Revision 8 seeded it from
Verification strategy's list, which at that point was CP2's and CP8's
enumerations plus CP14's — three checkpoints' worth of answers to a question
the column asks of every screen. Round 8 pointed out that the seed therefore
inherited the scope the column exists to escape. The seed is now **the
per-checkpoint enumerations themselves** — CP2 item 7, CP8's "Every method in
`ActiveWorkoutScreenTest`" table, CP10 item 6, CP11 item 5, CP12 item 6, CP13
item 6 and CP14 item 9 — which between them already cover every screen this
milestone converts, so CP1 starts from a list whose shape matches its own
column rather than from a subset of it. The deviation register seeds the
deviation column the same way.

**Why a document and not just "read the design each time":** F1's finding is
precisely that deviations accumulated invisibly. A register makes each one a
line an external reviewer can accept or reject, which is acceptance criterion
3 ("any material remaining deviation is explicitly identified, justified, and
accepted").

### CP2 — Navigation: four-tab IA, Settings route, workout-mode nav suppression

The change F1 names first and the parent milestone could not make.

1. `RepFlowDestinations.TOP_LEVEL_DESTINATIONS` goes from six entries to
   **four**: `Home`, `Plans`, `History`, `Progress`, with the design's own
   glyphs — `ph-house`, `ph-list-checks`, `ph-clock-counter-clockwise`,
   `ph-chart-line-up` — filled when selected and regular when not, which is
   the design's own selected/unselected treatment (`nTabs`: `'ph-fill '` vs
   `'ph '`). **This retires all four of the parent's unconfirmed
   judgment-call nav glyphs**: every destination they were guessing for —
   Exercises (`books`), Workout (`barbell`), Recovery (`moon-stars`), Backup
   (`cloud-arrow-up`) — stops being a tab, and the two that survive as tabs
   (Plans = `list-checks`, History = `clock-counter-clockwise`) are exactly
   the two the parent recorded as design-confirmed
   (`docs/ACTIVE_MILESTONE.md:1078–1088`). The four retired glyphs are not
   deleted: they move out of `RepFlowIcons.Nav` to the surfaces that now
   reach those destinations, **each with a consumer at CP2 rather than
   three checkpoints later**:
   - `books` → the Settings placeholder's `Library` row (item 4), carried
     into CP14's grouped screen;
   - `moon-stars` → the Home placeholder's recovery row (item 4), carried
     into CP5's recovery card;
   - `cloud-arrow-up` → the Settings placeholder's `Backup` row (item 4),
     carried into CP14's `Data` group;
   - `barbell` → **the Home placeholder's start affordance (item 4)**, carried
     into CP5's start card and then worn by CP7's board/start affordances. It
     is named as a CP2 destination deliberately: "each with a consumer at CP2"
     is a claim about all four, and pointing `barbell` at CP7 alone would make
     it three of four. Nothing breaks either way — `barbell` stays a top-level
     `RepFlowIcons` entry whatever happens to `Nav.workout`, so
     `catalogueCoversExactlyTheBundledIconSet` is unaffected — but the set is
     only complete if the fourth glyph lands where the other three do.
2. **The fill-on-select treatment is a model change, not just new assets.**
   `TopLevelDestination` carries a single `@DrawableRes icon`
   (`RepFlowDestinations.kt:93`) and `RepFlowBottomNavigationBar.kt:55–62`
   renders that one painter for both states, distinguishing selected from
   unselected **by colour alone** (`selectedIconColor`/`unselectedIconColor`,
   `:45–48`). So CP2 adds a second `@DrawableRes selectedIcon` field to
   `TopLevelDestination` and has the bottom bar choose between the pair on
   `selected`, keeping the existing colour treatment on top of it (`6b`:
   "never colour alone" — the fill *is* the second signal). Four
   destinations × two variants = **eight** drawables; the repo has one
   variant each of `ic_ph_list_checks.xml` and
   `ic_ph_clock_counter_clockwise.xml` and neither `house` nor
   `chart-line-up` at all, so this is **six** new local vector assets
   (`house` regular + fill, `chart-line-up` regular + fill, `list-checks`
   fill, `clock-counter-clockwise` fill), added exactly as CP2 of the parent
   did.

   **`RepFlowIconsTest` is a file CP2 deliberately changes, and the mechanism
   that keeps it green is not the glyph relocation in item 1.** `catalogue()`
   reflects over `RepFlowIcons::class.java.declaredMethods`
   (`RepFlowIconsTest.kt:20–23`), and `Nav` is a nested object whose getters
   live on `RepFlowIcons$Nav` — so **`Nav` has never been in the catalogue at
   all**. The proof is in the currently green suite: `Nav.exercises = books`
   aliases the same drawable id as top-level `books`, and
   `everyGlyphNameResolvesToADistinctDrawable` (`:30–35`) asserts no two
   catalogue names share a drawable; if `Nav` were in the catalogue that test
   would already be red. What actually keeps
   `catalogueCoversExactlyTheBundledIconSet` (`:37–40`) green is that CP2 adds
   **six top-level `RepFlowIcons` entries, one per new drawable**. Three of
   the file's five tests are affected, two of them requiring a deliberate
   rewrite rather than an incidental fix-up:
   - `everyTopLevelDestinationGetsItsOwnGlyph` (`:76–89`) enumerates all six
     `Nav` members by name (`exercises`, `workout`, `plans`, `recovery`,
     `history`, `backup`). Once CP2 leaves `Nav` with four, **this file does
     not compile** — it is not a failing assertion that restating the
     expectation fixes. It is rewritten to the four surviving destinations
     and, because the model now carries a pair per destination, to assert
     that each destination's regular and fill glyphs are distinct and that
     no two destinations share either.
   - `bundledIconSetIsTheGlyphSetThePlanEnumerates` (`:43–73`) asserts the
     bundled `ic_ph_*` set **exactly equals** a hardcoded 23-name literal, so
     six new drawables fail it until the literal is updated to 29. The
     rewrite names the six additions explicitly rather than pasting in
     whatever the drawable folder happens to hold — an equality against a
     hardcoded literal is exactly the test that gets "fixed" without anyone
     confirming the additions were the intended ones, and confirming that is
     the whole reason the assertion exists.
   - `designConfirmedNavGlyphsAreTheOnesTheDesignNames` (`:91–95`) asserts
     **two** equalities, not one — `RepFlowIcons.listChecks ==
     RepFlowIcons.Nav.plans` (`:93`) and `RepFlowIcons.clockCounterClockwise ==
     RepFlowIcons.Nav.history` (`:94`), which are exactly the two
     design-confirmed destinations that survive as tabs. Whichever fields
     `Nav.plans` and `Nav.history` become must each stay the
     **regular**-weight glyph with the fill as the second field, or both
     assertions are updated to name the pairs.

   **`RepFlowIcons.Nav`'s own KDoc is a CP2 rewrite too, and it is the one
   thing here that cannot fail.** `RepFlowIcons.kt:131–153` is a doc block
   that enumerates and justifies all six destinations, names `exercises`,
   `workout`, `recovery` and `backup` as "unconfirmed judgment calls", and
   states that "this milestone keeps the app's existing six". CP2 falsifies
   every one of those sentences. Because it is documentation rather than an
   assertion, nothing in the suite goes red when it goes stale — which is
   precisely the kind of text that survives a conversion untouched and then
   misleads the next reader, so CP2 rewrites it to the four-destination bar
   and its regular/fill pairs in the same edit that changes the object.
3. New routes: `HOME` (start destination, replacing `EXERCISES`), `PROGRESS`,
   `SETTINGS`.
4. **What renders at the new routes from the moment CP2 lands.** CP2 changes
   the destination set before CP5 (Home), CP14 (Settings) and CP15 (Progress)
   exist, so it ships each new route's first content itself, as an explicitly
   named placeholder:
   - `HOME` — a placeholder scaffold carrying exactly three things. The
     **resume / start affordances carried over from today's `WORKOUT` tab
     entry point**, so an active session and the start-workout action stay
     reachable the whole way from CP2 to CP5; a **recovery entry row
     (`Log ›`, wearing the `moon-stars` glyph item 1 retires from `Nav`)**,
     because `RECOVERY` stops being a tab the moment CP2 lands and both
     item 5's relocation and item 7's acceptance test require that path to
     exist from CP2, not from CP5 — without it, recovery logging would be
     unreachable for three checkpoints; and a **gear / `Settings ›`
     affordance**, which is the same argument applied one level further
     out and is the reason it is here rather than at CP5.

     `EXERCISES`, `WORKOUT` and `BACKUP` all have **no inbound navigation
     edge in the tree today**: `grep -n "navigate(" RepFlowNavHost.kt`
     returns six call sites, and `:53` is the bottom bar's own switch over
     `TOP_LEVEL_DESTINATIONS`; the other five are exercise-edit/new
     (`:70`, `:71`), plan-edit/new (`:91`, `:92`) and recovery-history
     (`:114`), none of which targets any of the three. So all three are
     reachable *only* as tabs (`TOP_LEVEL_DESTINATIONS`,
     `RepFlowDestinations.kt:45`), and `EXERCISES` is additionally today's
     `startDestination` (`RepFlowNavHost.kt:65`). **`WORKOUT`'s carry-over
     is therefore the same obligation as the other two, not a
     convenience**: from the moment item 1 lands, this placeholder's
     resume / start affordance is the app's only inward path to an active
     session, which is why it is the first row of the contract table and
     why item 7 walks to it. Item 1 retires both of the other tabs and
     item 5
     relocates both behind `SETTINGS`, so the gear is what makes `SETTINGS`
     itself reachable. Without it the chain stops one link short and
     **backup/restore — the app's only data-safety feature — and the whole
     exercise library are unreachable from CP2 through CP5**, because the
     design's other inward path to the library is CP7's exercise picker,
     five checkpoints later still. The header gear is CP5 item 1's, so this
     is exactly the carry-forward the other two affordances already make.
     Nothing else — no readiness score, no cards, no summaries. It is
     replaced wholesale by CP5, which absorbs the recovery row into its
     recovery card and the gear into its header.
   - `SETTINGS` — a placeholder scaffold carrying the entry rows CP2's own
     relocation asserts (`Library`, `Backup`), so the relocations in item 5
     are true when CP2 lands rather than three checkpoints later. Replaced by
     CP14, which owns the grouped screen.
   - `PROGRESS` — a placeholder scaffold with the design's empty-state
     treatment. Replaced by CP15.

   Each placeholder is named `…Placeholder` in source with a one-line comment
   naming the checkpoint that replaces it, so an unreplaced one is
   greppable at CP16. This is the deliberate alternative to resequencing:
   the dependency graph stays as the registry has it, and no checkpoint in
   CP2 → CP5 launches the app into an unbuilt route.

   **What CP2 ships, and what each placeholder owes its successor.** CP2 is
   the checkpoint that changes the destination set for the whole app, so it
   carries more forward references than any other checkpoint in the plan,
   and every one of them runs through a placeholder. The contract is
   therefore stated as a list rather than left implicit in the prose above,
   so that an affordance missing from a placeholder is visible while the
   placeholder is being written — the service the execution-order paragraph
   performs for ordering, performed here for entry points:

   **One row per affordance, and every row names the assertion that covers
   it.** The table is written this way rather than one row per placeholder
   because the coverage question it exists to answer is per-affordance: a
   placeholder row listing three affordances beside a checkpoint that
   absorbs them says nothing about whether each is *asserted*, and an
   affordance that is built but not asserted is the failure this table was
   added to make visible. The third column is therefore the item 7 bullet
   that walks to it, and an empty cell in that column is the gap.

   | Placeholder | Affordance it must carry from CP2 | Inward path item 7 walks to it | Absorbed by |
   |---|---|---|---|
   | `HomePlaceholder` | resume / start (carried from today's `WORKOUT` tab entry point) | `Workout` from `Home` | CP5 item 3 — start card; CP5 item 2 — resume card |
   | `HomePlaceholder` | recovery `Log ›` (wearing `moon-stars`) | `Recovery` from `Home` | CP5 item 4 — recovery card |
   | `HomePlaceholder` | gear → `SETTINGS` | `Settings` from `Home` | CP5 item 1 — header gear |
   | `SettingsPlaceholder` | `Library` row → `EXERCISES` | `Exercises` from `Settings` (chained behind `Settings` from `Home`) | CP14 item 2 — `Library` row |
   | `SettingsPlaceholder` | `Backup` row → `BACKUP` | `Backup` from `Settings` (chained behind `Settings` from `Home`) | CP14 item 5 — `Data` group |
   | `ProgressPlaceholder` | none; nothing is relocated into `PROGRESS`, so no retired tab depends on it | — (nothing to walk to) | CP15 |

   The rule the table encodes, and the one item 7 asserts: **every
   destination item 1 stops being a tab has a working inward path in the
   same checkpoint that retires its tab, walked from the start
   destination** — not merely an inner link whose own entry point arrives
   later. Item 1 retires **four** tabs — `EXERCISES`, `WORKOUT`, `RECOVERY`
   and `BACKUP` (`TOP_LEVEL_DESTINATIONS`, `RepFlowDestinations.kt:45`,
   holds exactly six: `EXERCISES` `:48`, `WORKOUT` `:54`, `PLANS` `:60`,
   `RECOVERY` `:66`, `HISTORY` `:72`, `BACKUP` `:78`) — so the rule
   quantifies over four destinations and the third column above must carry
   four walks. It does: `Workout`, `Recovery` and `Settings` from `Home`,
   and `Exercises` and `Backup` behind the third of those.
5. **Relocation, as the design shows it**, not as an arbitrary reshuffle:
   - `Exercises` → reached from the workout board's exercise picker and from
     **Settings' `Library` row (CP14 item 2, placeholder in item 4 above)**;
     it stops being a top-level tab. The library screen itself survives in
     full (CP10). **The picker is CP7's**, so from CP2 until CP7 the row
     behind item 4's gear is the library's only inward path — which is why
     that gear is part of item 4's contract rather than a CP5 nicety.
   - `Recovery` → reached from Home's recovery card (`Log ›`), which is
     exactly where `3c`'s own label says it is reached from; until CP5 lands,
     from the Home placeholder.
   - `Recovery history` → reached from the recovery screen, unchanged.
   - `Backup` → moves under Settings' `Data` group (CP14); until then it is
     reached through the Settings placeholder's `Backup` row, itself reached
     through item 4's gear. Settings is its **only** inward path at every
     point in this milestone, so backup and restore are exactly as reachable
     as `SETTINGS` is — the reason the gear lands with the tab retirement
     and not after it.
6. **Workout mode replaces the nav — and at CP2 that is the whole of it.**
   `6b`: "Workout mode replaces the nav — an X or Finish is the only way
   out."

   **The nav-suppression half costs CP2 nothing, and saying so is what makes
   the item's real work visible.** `RepFlowNavHost.kt:49` renders the bar
   only `if (currentRoute in TOP_LEVEL_ROUTES)`, and `TOP_LEVEL_ROUTES` is
   derived from `TOP_LEVEL_DESTINATIONS` (`:26`). So the moment item 1 drops
   `WORKOUT` from that list, today's workout route stops showing the nav
   with no further code — and CP7's board and CP8's focus routes inherit the
   same behaviour for the same reason when they land, without either
   checkpoint doing anything for it.

   **The back-gesture interception is not CP2's work, and moves to CP7 item
   1.** Under the execution order stated above, CP2 runs five checkpoints
   before CP7, and at CP2 neither side of "intercepted to the same exit
   confirmation the `X` opens" exists: the `X` and its leave sheet are
   CP7 item 1's, the board and focus routes are CP7's and
   CP8's, and there is no exit confirmation anywhere in the tree to reuse —
   `BackHandler` appears in exactly two files, both editors
   (`ExerciseEditorScreen.kt:54`, `TrainingPlanEditorScreen.kt:44`), and
   `presentation/workout/` contains no dialog or confirm affordance at all.
   Keeping the interception here would mean CP2 building a throwaway
   confirmation for CP7 to replace, and the plan's own second-half-owns-it
   rule decides it the other way: **the interception belongs with the
   confirmation it opens, and the confirmation lands at CP7.** CP7 owns the
   behaviour and its assertion; item 7's list below no longer carries
   either.

   Intercepting system back is still a real platform behaviour change and it
   is the design's, stated twice (`6b`'s rule and `4a`'s `nShowNav`). It
   remains flagged for explicit sign-off — now against CP7, which is where
   it ships.
7. **The existing tests CP2 changes, enumerated rather than counted.** CP2
   changes the destination set for the whole app, so it changes every existing
   test that reads that set. The list below was produced by enumerating the
   suite's dependants rather than by carrying forward the plan's own earlier
   count, and the enumeration is closed on both sides:
   `grep -rl "createAndroidComposeRule<MainActivity>" app/src/` returns
   **exactly three** instrumented classes — every other UI test uses
   `createAndroidComposeRule<ComponentActivity>` and sets its own content, so
   the destination change cannot reach it — and
   `R.string.exercise_list_backup_content_description`, the `BACKUP` tab's own
   `contentDescriptionRes` (`RepFlowDestinations.kt:80`), is referenced under
   `app/src/` by exactly those three tests and by that one destination entry,
   and by nothing else.

   **CP2 changes five test files: three rewritten and two re-routed.** That
   is CP2's own count, and the two greps above are what close it.

   **It is not the milestone's count, and revision 8 stated it as one.**
   Through revision 8 this paragraph added CP8's `ActiveWorkoutScreenTest`
   and CP14's `LocalBackupRepositoryAtomicityTest` to CP2's five and called
   the result "**seven** test files this milestone knowingly rewrites rather
   than preserves". Every one of the seven is real and each is still named
   below, but the enumeration behind them covers exactly three mechanisms —
   the destination set (CP2), `performTextInput` (CP8) and `clearAllTables()`
   (CP14) — while CP8's own file has fourteen methods and the conversion
   checkpoints CP10–CP13 read six further instrumented classes at which no
   grep in this plan had ever been pointed. **Seven is the number of files
   those three mechanisms reach**, and it is stated that way from revision 9
   on. It was also internally inconsistent: two of the seven are re-routed
   rather than rewritten, which the sentence four lines above already said.
   The milestone-wide roll-up now lives in "Verification strategy" and is
   assembled from each checkpoint's own enumeration instead of extrapolated
   from this one.

   **(a) `MainActivityNavHostSmokeTest`** is rewritten to the new destination
   set, because it asserts the six-tab IA that is being retired: it reaches
   `Recovery` (`:38–50`), recovery history behind `Recovery` (`:52–65`),
   `History` (`:67–76`) and `Backup` (`:78–87`) by clicking tabs — three of
   those four tabs are ones item 1 drops — and its first test asserts the
   exercise list as the start destination (`:28–36`), which item 3 replaces
   with `HOME`. The rewrite asserts:
   - the four tabs and the `HOME` start destination;
   - that **each relocated destination is reachable by its new inward path,
     walked from the start destination rather than from the middle of the
     chain** — one walk per destination item 1 stops being a tab, which is
     **four**: `Workout` from `Home` (the placeholder's resume / start
     affordance); `Recovery` from `Home`; `Settings` from `Home`, and then
     `Exercises` and `Backup` from `Settings`. The `Settings` link is named
     before its two rows deliberately: an assertion that starts at the
     `SETTINGS` route and checks them **passes against a route no user can
     open**, which is this bullet's own warning displaced one level up. A
     test that asserted only the tabs would pass while a relocation shipped
     with no entry point at all — and `Workout` is the case where that
     would cost the most, since starting and resuming a workout is the
     primary action of the whole app (CP5 item 3, `UX_FLOWS.md`'s
     "strongest visual priority") and the affordance being walked to is the
     one carried over from today's `WORKOUT` tab entry point
     (`ActiveWorkoutScreen.kt:92–93`, `:130–157`). The `Workout` walk is
     written against whatever route the start affordance navigates to, the
     same latitude the other three bullets take;
   - that the nav is absent in workout mode — asserted against today's
     `WORKOUT` route, the only workout surface that exists when CP2 runs,
     and satisfied by `RepFlowNavHost.kt:49`'s existing `TOP_LEVEL_ROUTES`
     gate the moment item 1 lands. **On its own this assertion is
     satisfiable by a route no user can reach**, since the gate is a
     property of the route rather than of any path into it; it is the
     `Workout` walk in the bullet above that makes it an assertion about a
     surface a user can actually get to, which is why the two are written
     as a pair rather than the second alone.

   The back-gesture assertion this list carried through revision 5 has moved
   to CP7 with the behaviour it is about (item 6).

   **(b) `RepFlowBottomNavigationBarTest`** (JVM,
   `app/src/test/kotlin/…/presentation/navigation/`) is `RepFlowIconsTest`'s
   sibling from the same parent checkpoint, and it fails the same two ways for
   the same two reasons. **Two of its five tests do not survive CP2; the other
   three are untouched**, and stating both halves is what scopes the rewrite to
   methods rather than to the file:
   - `everyTopLevelDestinationCarriesItsOwnNavGlyph` (`:71–86`) builds a
     hardcoded six-entry map naming `RepFlowIcons.Nav.exercises`, `.workout`,
     `.plans`, `.recovery`, `.history` and `.backup` (`:75–80`) and asserts it
     equals `TOP_LEVEL_DESTINATIONS.associate { it.route to it.icon }`
     (`:82–85`). Item 1 moves `exercises`, `workout`, `recovery` and `backup`
     **out of** `Nav`, so **this file does not compile** — the identical
     mechanism item 2 records for `RepFlowIconsTest`'s
     `everyTopLevelDestinationGetsItsOwnGlyph`, which enumerates the same six
     members, and not a failing assertion that restating the expectation
     fixes. Item 2's second field lands on it too: `it.route to it.icon` is
     the single-glyph model, so the rewrite asserts the four surviving
     destinations **and** their `icon`/`selectedIcon` pairs, the same shape
     item 2's rewrite takes.
   - `topLevelDestinationsKeepTheirRoutesAndOrder` (`:89–102`) asserts
     `TOP_LEVEL_DESTINATIONS.map { it.route }` equals the exact six-element
     list `EXERCISES, WORKOUT, PLANS, RECOVERY, HISTORY, BACKUP`. This one
     **compiles** — the route constants survive item 1, only the tab set
     changes — so it is a plain failing assertion, and it is a *deliberate
     route-order pin being retired*, which is the case item 2's own rule says
     must be rewritten by naming what replaces it rather than "fixed" by
     pasting in the new list. Its KDoc (`:88`) reads "This checkpoint is
     visual only: same six routes, same order" — a sentence CP2 falsifies and
     that nothing in the suite reports, the `RepFlowIcons.Nav` KDoc situation
     of item 2 reproduced inside a test file. It is rewritten in the same edit
     as the assertion beneath it.
   - **Untouched**, and named so the rewrite does not spread:
     `darkNavColorsReadTheDarkDesignValues` (`:29–41`),
     `lightNavColorsReadTheLightDesignValues` (`:43–55`) and
     `eachThemeGetsItsOwnPairRatherThanOneSharedSet` (`:62–69`) read
     `repFlowNavColors`' colour roles, not the destination list, so item 1
     cannot reach them.

   **(c) `BackupRouteSafCancellationTest` and
   `BackupRouteUnreadableRestoreFileTest` are not rewritten but re-routed**,
   and they are the reason the enumeration above had to be a `grep` rather
   than a recollection. Both launch the real app and reach the Backup screen
   by clicking the tab item 1 retires —
   `BackupRouteSafCancellationTest.kt:51–54` and
   `BackupRouteUnreadableRestoreFileTest.kt:53–56`, both
   `onNodeWithContentDescription(getString(R.string.exercise_list_backup_content_description)).performClick()`
   against the `BACKUP` tab's own `contentDescriptionRes`. From the moment
   item 1 drops `BACKUP` from `TOP_LEVEL_DESTINATIONS`, that node is not on
   the start screen and both tests fail at `performClick()` with no matching
   node: **a runtime instrumented failure at CP2, twelve checkpoints before
   CP14 owns the real Settings screen.** Each test's *subject* — SAF
   cancellation clearing busy without a failure message; an unreadable restore
   file reporting failure without staging a restore — is untouched by this
   milestone and is real regression coverage from the parent's Milestone 8
   review, so **only their opening navigation changes**: they walk item 4's
   gear to the Settings placeholder and then its `Backup` row — the `Backup`
   from `Settings` walk (a)'s second bullet already asserts, so the re-route
   costs no new path and introduces no path the rewritten smoke test does not
   already cover. Stated here rather than left
   to the device run because the instrumented suite is the slowest signal in
   the gate and these two failures would arrive last.
8. Deep-link/route-string stability is not a concern: routes are internal
   (`D-1`, plain string routes, no external entry points).

### CP3 — Structural primitives the design needs beyond the visual foundation

The parent built colour/type/shape/icon tokens and a small primitive set. The
design's compositions need structural primitives it did not build. Building
them once here is what stops CP5–CP15 from re-inventing layout per screen —
the root cause F1 identified.

New under `presentation/designsystem/components/`:

- `RepFlowScreenScaffold` — screen padding 16, the design's title treatment,
  and a **pinned bottom action bar** slot, implementing `6b`'s "one primary
  action per screen, pinned to a bottom bar".
- `RepFlowBottomActionBar` — 56dp primary + optional secondary, hairline top
  edge.
- `RepFlowSheet` — the design's 14–16 radius bottom sheet, for `6b`'s "sheets
  for choices". Every picker, menu and chooser this milestone builds is a
  sheet; `DropdownMenu` is retired from the converted screens. (Dialogs
  survive only for destructive confirmation, which is the other half of the
  same rule.)
- `RepFlowStatTile` / `RepFlowStatRow` — the `Time · Volume · Sets` triple in
  `3b` and the summary figures in `4a`'s finish screen.
- `RepFlowSectionLabel` — the `label 11/500` uppercase section header used
  throughout `5c`, `5b` and `4a`.
- `RepFlowNumericKeypad` — `6b`: "The value itself is a button — it opens the
  keypad." The design's keypad is `1-9 . 0 ⌫` with Cancel/Done.
- `RepFlowListRow` — the 56–68dp row archetype with leading slot, title,
  meta, trailing caret/action, used by the library, plans, history and
  settings.

Reworked:

- `RepFlowStepper` — **finally gets its consumers.** The parent authored it
  and shipped it unconsumed, which was `IMPROVEMENT_ROADMAP.md` §8.1. It
  gains the keypad-on-value-tap behaviour `6b` requires and a step size
  parameter, and CP8/CP11 consume it.
- `RepFlowPillPicker` — generalized into the design's **scale row** (`6b`:
  "Scale row — 0–5 and RPE", "Always labelled at both ends"), with end
  labels, and used for RPE, pain, technique and every recovery scale.
- `RepFlowStatusChip` — must satisfy `6b`'s "never colour alone": every tone
  carries an icon or a word. The audit for this lands in the deviation
  register.

**`RepFlowPrimitivesTest` is extended in two of its three regions, not one.**
The file is organised as `// region` blocks — *"Which theme a primitive thinks
it is in"* (`:38`), *"Contrast floors the primitives own"* (`:61`, seven tests
at `:63–146`) and *"Dimensions and radii"* (`:150`, seven tests at
`:152–218`) — and through revision 8
CP3's sentence here enumerated only the third. That is the region with no WCAG
floor in it, so a new primitive would have inherited a tap-target and radius
obligation and no contrast obligation at all.

- **Dimensions and radii**: each new primitive is held to the ≥44dp tap-target
  floor and to the design's stated radii and row heights, the same way the
  region already holds the existing set.
- **Contrast floors**: each new primitive that renders text names the ground
  it renders it on and clears the 4.5:1 text floor in both themes, by the same
  `assertClears(floor, foreground, background)` helper the region already
  uses. The primitives that owe one, and why each is a *new* pair rather than
  a measured one: `RepFlowStatTile`/`RepFlowStatRow` (figure and caption on
  the tile ground), `RepFlowSectionLabel` (`label 11/500` — the smallest type
  slot in `6b`, and an always-visible one), `RepFlowListRow`'s meta line, and
  `RepFlowNumericKeypad`'s digit glyphs. `RepFlowBottomActionBar`'s primary
  label is the one that does **not** owe a new measurement — it is
  `RepFlowButtons`' primary tier, already pinned by
  `primaryButtonLabelsClearTheTextFloorInBothThemes` — but the bar's own fill
  and hairline edge are a new ground for any secondary label beside it, and
  that pair is measured.
- **`RepFlowSheet` is the one that needs a role decision before it can be
  measured, and CP3 makes it.** `BottomSheetDefaults.ContainerColor` resolves
  to `SheetBottomTokens.DockedContainerColor`, which is
  `ColorSchemeKeyTokens.SurfaceContainerLow` in `material3-android-1.4.0` —
  and `surfaceContainerLow` is assigned by neither `RepFlowDarkColorScheme`
  nor `RepFlowLightColorScheme` and has a row in neither of `ROLE_AUDIT.md`'s
  two tables. So the design's sheets would render on a Material 3 baseline
  value nothing in this codebase has measured, on a surface `6b` makes
  load-bearing ("sheets for choices"). CP3 either assigns the role and records
  the assignment by the audit's own method, or leaves it at the baseline and
  records the baseline ratios — the same two outcomes `ROLE_AUDIT.md`'s
  "Deliberately unassigned" table already carries for six other roles. CP16
  item 8 is where the audit itself is brought back into agreement.

A new primitive that genuinely owes no floor — one that renders no text —
is listed here as owing none, with that reason, rather than left out. This is
the checkpoint the plan itself calls the one "whose under-scoping would
reproduce F1"; a test obligation that covers half of what its own sentence
claims is the same failure in miniature.

**Scoping rule, because this is the checkpoint whose under-scoping would
reproduce F1.** CP3 introduces seven primitives and reworks three, and every
one of CP5–CP15 is a consumer; it is therefore targeted at **3 sessions**,
not the 2 a single-screen checkpoint gets. And when a later checkpoint
discovers a structural primitive this list is missing, that primitive is
**added to `presentation/designsystem/components/` under CP3's own
conventions and tests**, never defined locally in the consuming screen's
file. Re-inventing layout per screen is precisely the root cause F1 named;
the rule is what keeps a mid-milestone discovery from quietly restoring it.

### CP4 — Readiness score derivation and readiness detail surface

The design's Home leads with a readiness score; the detail sheet
(`4a`'s `nRdySheet`) explains what drove it and what it changed.

**This is buildable with no new domain concept, and that is why it is in
scope.** `RecoveryEntry` already carries exactly the six 0–5 scales the
design's readiness model consumes: `sleepQuality`, `energy`, `legDoms`,
`heelStiffness`, `painWhileWalking`, `heavyLegs` — all non-null `Int`s on
`0..5` (`RecoveryEntry.kt`). The entry's `futsalInPrevious24h` /
`futsalExpectedNext24h` flags are **not** inputs: the design's engine does not
read them.

1. **The model is the design prototype's own readiness engine, transcribed,
   not invented** (revision 14, on the user's decision of 2026-09-30).
   Source: `RepFlow.dc.html:3142–3166` (`RDY`, `RDY_BANDS`, `readiness()`),
   with the factor notes and driver sentence from `nRdyFactors` /
   `nRdyDrivers` (`~:3790–3810`). New pure-Kotlin
   `domain/recovery/ReadinessScore.kt` implements exactly this:
   - **Weights, in the design's `RDY` order** (which is also the driver
     order): sleep quality **1.0**, energy **1.2**, leg DOMS **1.0**
     (inverted), heavy legs **0.8** (inverted), heel stiffness **0.7**
     (inverted), pain while walking **1.5** (inverted). Σw = 6.2.
   - **Normalization**: `n = v` for sleep and energy; `n = 5 − v` for the
     four inverted scales, where high is bad (`6b`'s "a high number is good
     on sleep and bad on pain").
   - **Score**: `round(100 × Σ(n / 5 × w) / Σw)`, on `0..100`, rounded to
     nearest (JS `Math.round`). Computed in integer tenths of a weight (10,
     12, 10, 8, 7, 15; Σ = 62) so a boundary score is exact rather than
     subject to floating-point drift. **Ties cannot occur** (revision 15):
     the exact score is `10·S/31` with `S = Σ n·w₁₀` an integer, and it is
     never `x.5`, so the half-up/half-even question has no input to decide.
   - **Pain gate, applied first**: `painWhileWalking ≥ 3` or
     `heelStiffness ≥ 4` → band `Protect`, whatever the score.
   - **Bands** otherwise: score **≥ 75 `Ready`**; **58–74 `Hold`**;
     **42–57 `Back off`**; **≤ 41 `Protect`**.
   - **Drivers**: a factor is flagged when `n ≤ 2` (the sheet's "pulling the
     score down"; otherwise "fine"). The driver sentence names the first
     three flagged factors in the order above as `<label> <v>/5`, with the label
     lowercased whole as `nRdyDrivers` does (`x.label.toLowerCase()`) —
     "Driven by leg doms 3/5, heavy legs 4/5." (revision 15: the
     prototype's casing is the intended one, and `ReadinessScoreTest` pins
     that literal; the sheet's per-factor rows keep the label's own casing)
     — or, when none is flagged, "Nothing is flagged — every input is in the
     top half of its scale."
   - Worked example, the prototype's own seed check-in (sleep 4, energy 3,
     DOMS 2, heel 1, pain 0, heavy legs 2): 75.16 → **75, `Ready`**.
2. **Date selection: today's entry only.** Today is
   `clock.now().atZone(ZoneId.systemDefault()).toLocalDate()`, the derivation
   `RecoveryFutsalViewModel.kt:47` already uses. The score is computed from
   the entry for that date; **an earlier day's entry is never carried
   forward**. No entry for today → no score, and Home renders the design's
   own empty state (artboard `1d`, "Nothing logged today") with the log
   prompt instead of a number. `RecoveryRepository` holds at most one row per
   date (`RecoveryRepository.kt:13–15`), so there is nothing to choose
   between within a day. **"Today" is re-derived when the date changes**
   (revision 17, X2-I2; mechanism revised in revision 19, X3-I1): Home does
   not compute it once. Home's ViewModel holds today's date in a
   `MutableStateFlow<LocalDate>` read from the injected `Clock`, and applies
   `flatMapLatest` into `ObserveReadiness(date)`, so a date change drops
   yesterday's subscription and shows the new day's entry, or the empty
   state. Two things re-read the clock into that flow. **While Home's state
   is collected**, a wait until the next local midnight re-reads it, which
   covers a screen left on across midnight. **On every return to the
   foreground**, Home's route calls the ViewModel's `onForeground()` from
   `LifecycleEventEffect(Lifecycle.Event.ON_START)` (`lifecycle-runtime-compose`,
   already a dependency, `app/build.gradle.kts:71`), which pushes the clock's
   current date into the flow. That call does not depend on the upstream
   having restarted: the midnight wait uses uptime, which stops in deep
   sleep, and a return inside the 5-second `WhileSubscribed` timeout
   (`ActiveWorkoutViewModel.kt:154`) never restarts the upstream. `StateFlow`
   drops an equal value, so a same-day return does not resubscribe. Home's
   route still collects with `collectAsStateWithLifecycle`, as every existing
   route does (revision 18). CP5 item 1's header date reads the same date
   flow, so the header and the readiness card cannot disagree after midnight.
3. **Reactivity — a read path only.** `RecoveryRepository` exposes only
   suspend lookups today (`RecoveryRepository.kt:17–26`), and
   `RecoveryEntryDao` has no observable query. CP4 adds
   `fun observeForDate(date: LocalDate): Flow<RecoveryEntry?>` to the port,
   backed by a new `RecoveryEntryDao` query over the same `entry_date`
   predicate `findForDate` uses, returning `Flow<RecoveryEntryEntity?>`, and
   implemented in `LocalRecoveryRepository` — the Room-`Flow` pattern
   `WorkoutRepository.observeActiveSession` already uses
   (`WorkoutRepository.kt:20`). No write path, table, column or migration
   changes. New application-layer `ObserveReadiness(date)` maps that flow
   through `ReadinessScore` to the score, band, driver sentence and
   per-factor breakdown, or to "none". Room re-emits on the `upsert`
   `RecordRecoveryEntry` already performs, so a check-in saved on the
   recovery screen updates Home on return with no refresh call.
4. The readiness detail sheet: per factor, its value (`v/5`), its weight
   (`×1.2`), five dots filled to `n`, and its flagged/fine note; then the
   driver sentence. **The design's per-band advice line
   (`RDY_BANDS[…].line`, e.g. "Repeat last session's loads instead of
   adding.") is not rendered** — nothing in this milestone acts on the band
   (see "Not in scope" below; CP6 renders `ProgressionPolicyV1` unchanged),
   so advice the app does not follow would be false. The band label and the
   driver sentence are the "readiness line" CP5 item 4 renders. Deviation
   `D19`.
5. **Tests.**
   - `ReadinessScoreTest` (JVM) pins: the six weights and the inversion set;
     each band threshold from both sides (**75/74, 58/57, 42/41** — every
     score from 0 to 100 except 1 and 99 is reachable, so each is hit
     exactly); the pain gate at its edges (pain while walking 2 vs 3, heel
     stiffness 3 vs 4, each against an otherwise-`Ready` input); all-best =
     100 and all-worst = 0; nearest rounding both ways — the seed example
     rounds down (75.16 → 75 `Ready`) and the seed with heavy legs 3 rounds
     up (72.58 → 73 `Hold`); driver flagging at `n` = 2 vs 3, the three-driver cap and its
     order; and that the futsal flags do not change the result.
   - `ObserveReadinessTest` (JVM, over `InMemoryRecoveryRepository`, which
     gains `observeForDate`): no entry → none; an entry dated yesterday only
     → still none; upserting today's entry emits a score without
     resubscribing.
   - One new `RecoveryDaoTest` method (instrumented): `observeForDate`
     re-emits after `upsert` for that date.
   - The Home half — the card itself updating — is CP5's (CP5 item 4),
     because Home does not exist until CP5.
6. **The model is no longer an open proposal.** The user adopted the
   prototype's numbers on 2026-09-30; CP16 records that as a resolved
   open-decisions row citing `RepFlow.dc.html:3142–3166`, rather than
   leaving it as folklore in a Kotlin file.

**Not in scope here:** the readiness sheet's "what it changes" *decision
list* per exercise (`nRdyDecisions`, the prototype's `decide()`), and the
override toggle, both of which restate targets the deload/programme model
would own. CP6 renders the progression recommendation, which is the part
with real domain backing.

### CP5 — Home screen

New `presentation/home/`. Renders, per `4a` and `docs/UX_FLOWS.md`:

1. Header: date (from CP4 item 2's date flow), greeting, gear → Settings — **absorbing CP2 item 4's Home
   placeholder gear**, which has carried this link since the checkpoint that
   retired the tabs, exactly as the recovery card below absorbs that
   placeholder's `Log ›` row.
2. **Resume card**, when an active session exists: title, elapsed and set
   count, `Resume` / `Finish it` / abandon (trash, calling the same
   `AbandonWorkoutSession` behind the same `Abandon this workout?`
   confirmation and copy as CP7 item 1, `D18`). This is `UX_FLOWS.md`'s "Interrupted workout" section
   rendered for the first time. **`Finish it` never calls
   `CompleteWorkoutSession` from Home** (revision 12): `complete` is terminal
   (`WorkoutSession.kt:126–127`), and the design's `Finish it` opens the same
   finish sheet as every other finish (`nAskFinish`). CP9's sheet does not
   exist yet when CP5 lands, so at CP5 `Finish it` opens the active workout
   surface — the same destination as `Resume` — and CP9 re-wires it into the
   finish sheet as the second half to land (CP9 item 1).
3. **Start card**: the primary action of the whole app, per `UX_FLOWS.md`'s
   "must have the strongest visual priority" — `Start workout` as the
   design's solid accent button, plus `Train something else ›` opening the
   start sheet (plan list + `Start without a plan`), which is the existing
   start-workout menu converted from a `DropdownMenu` into a sheet.
4. **Recovery card**: `Recovery today` + `Log ›` → the recovery entry screen;
   the readiness score button → CP4's sheet; the readiness line (band label
   and driver sentence, CP4 item 4) — or, with no entry for today, the
   design's `Nothing logged today` empty state (CP4 item 2); and the three
   recovery chips. **CP5 owes the Home half of CP4's reactivity test**: a
   Home ViewModel JVM test that saving today's check-in through
   `RecordRecoveryEntry` replaces the log prompt with the score on the same,
   still-subscribed ViewModel. **It also owes the midnight case** (revision
   17, X2-I2). A fake `Clock` reads the test scheduler's virtual time,
   starting at 23:59 local on a day with a saved entry. The test asserts the
   score, advances virtual time past midnight, and asserts that the same
   ViewModel now shows `Nothing logged today`. It then saves the new day's
   entry and asserts the score returns. **It also owes the sleep case, where
   the subscription never lapses** (revision 19, X3-I1). The collector stays
   subscribed, and the fake `Clock` is set directly to 07:00 the next day
   while virtual time stays short of the pending midnight wait, as in deep
   sleep. The test calls `onForeground()`, as the route does on `ON_START`,
   and asserts that the same ViewModel shows `Nothing logged today`. **The
   route's half is asserted too** (revision 20, local round 19's O1). An
   instrumented `createAndroidComposeRule<ComponentActivity>()` test renders
   `HomeRoute(viewModel = …)` with a ViewModel built over an in-memory
   `RepFlowDatabase` (as the DAO tests open one) and a settable `Clock` at
   10:00 on a day with a saved entry, and asserts the score. It sets the
   clock to 07:00 the next day, moves the activity to `CREATED` and back to
   `RESUMED` within the 5-second timeout, and asserts `Nothing logged today`.
   Without the route's `LifecycleEventEffect` it fails, because the upstream
   stays subscribed and the midnight wait is hours away in real time.
5. **Last workout** summary card: name, when, duration, and the count of
   load increases, all derived from existing history.

Deviations from `4a`'s Home, all from the excluded capabilities, each
recorded: no `day 2 of 4` plan-day line (no multi-day plans), no
`This week — 2 of 4 sessions` progress dots (needs a weekly target), and no
deload card. The start card falls back to "the plan you last trained" rather
than "today's programmed day".

### CP6 — Progression recommendation surface on the existing policy

`6a` and `6c`. The domain and application layers are already complete —
`ProgressionPolicyV1`, `ProgressionRecommendation`, `ProgressionResult`,
`ManualOverride`, `ComputeProgressionRecommendation`, `RecordManualOverride`
— and CP6 adds no domain or application concept.

**What exists on screen today, corrected.** An earlier revision of this plan
claimed the policy has "no UI whatsoever". That is false, and the correction
matters for sequencing. There is a minimal progression surface today,
including a working override path:

- `ActiveWorkoutScreen.kt:392–415` — a private `RecommendationRow` rendering
  the result label, `recommendation.topReason`, an `isOverridden` marker, and
  three override `TextButton`s (`INCREASE_LOAD`, `MAINTAIN_LOAD`,
  `REDUCE_LOAD`);
- `ActiveWorkoutScreen.kt:376–377` — rendered per exercise **inside the
  `Add exercise` `DropdownMenu`**, reached from
  `ActiveWorkoutScreen.kt:197`'s `AddExercisePicker`;
- `ActiveWorkoutViewModel.kt:312–325` — `onOverrideRecommendation` already
  writes through `RecordManualOverride` and refreshes via
  `recommendationRefreshTrigger`.

So CP6 **replaces and relocates an existing capability** rather than
introducing one, and the host it currently lives in is exactly what CP7 item
6 converts into a sheet. CP6 runs first, and replaces that row's three inline
override buttons with a `Why ›` link into this screen; CP7 then carries the
row into the sheet exactly as CP6 left it (CP7 item 6). The capability is
continuous across the move, never interrupted, and no re-ordering is needed —
the registry array order already puts CP6 ahead of CP7.

1. A recommendation screen showing the proposed action, the value, **every
   reason the policy used** (the design's own emphasis: "it shows what the
   policy proposes, every reason it used"), and the user's final say.
2. An override path that writes through the existing `RecordManualOverride`
   — the same call `ActiveWorkoutViewModel.kt:312–325` already makes — so the
   override is on record exactly as the design promises.
3. **All five `ProgressionResult` cases are rendered**, each as its own
   state rather than as an error: `IncreaseLoad`, `MaintainLoad`,
   `ReduceLoad` (`6c`'s reduce-load outcome), `WaitForMoreData` (`6c`'s
   not-enough-data outcome), and **`RecoveryAdjustment`**. The screen CP6
   replaces already renders all five
   (`ActiveWorkoutScreen.kt:422`, `ProgressionResultUi.RECOVERY_ADJUSTMENT →
   R.string.progression_result_recovery_adjustment`), so omitting one would
   be a regression, not a non-implementation.
4. **`RecoveryAdjustment` is why CP6 depends on CP4.** It is the one outcome
   whose explanation is a recovery fact, so its state links to CP4's
   readiness detail sheet and reuses `ObserveReadiness`'s driver list for the
   "why" line rather than inventing a second explanation of the same input.
   No other part of CP6 consumes readiness, and CP6 changes no policy input.
5. **Entry points, and who builds each one.** CP6 lands before CP7 and CP8
   (see "The execution order is fully determined" above), so the entry points
   are stated against the order that actually runs rather than hedged across
   orders that cannot:
   - the finish screen (CP9), which is where `UX_FLOWS.md`'s "Complete
     workout … relevant progression recommendations" already said it belongs.
     CP9 depends on CP6, so this one is ordered by the graph as well and needs
     nothing further;
   - **the exercise picker's recommendation row — the entry point CP6 has the
     moment it lands.** That row already exists
     (`ActiveWorkoutScreen.kt:392–415`, inside the `Add exercise` menu), so
     CP6 replaces its three inline override buttons with a `Why ›` link into
     this screen and is never a screen without a way in. CP7 then converts
     that menu into the picker sheet and carries across whatever CP6 left
     there — the `Why ›` link, not the three buttons (CP7 item 6);
   - **the focus screen's suggestion strip is CP8's to wire, not CP6's.** CP8
     runs after CP6, so at CP6 time there is no suggestion strip in
     `presentation/workout/` to wire anything into, and at CP8 time this
     screen and its route already exist. **CP8 therefore ships the strip with
     `Why ›` already routed here and owns the inward-path assertion** (CP8
     item 5) — the second-half-owns-it rule stated with the execution order
     above, the same one CP14 item 4 applies to CP9/CP14.
6. No policy change of any kind. If a reason string reads badly on screen,
   that is recorded, not rewritten.

**Tests CP6 owes.** Nothing pins the override path today —
`ActiveWorkoutScreenTest.kt:64` passes `onOverrideRecommendation = { _, _ -> }`
and asserts nothing about the row, which is how CP7 could delete the existing
affordance with the suite green. CP6 adds:

- an instrumented test that an override **reaches `RecordManualOverride`** and
  that the recommendation then re-renders as overridden;
- one state test per `ProgressionResult` case, `RecoveryAdjustment`
  included;
- **an inward-path assertion that the picker's recommendation row reaches this
  screen** — CP6's own entry point, and the only one that exists when CP6
  runs. This is the same discipline CP2 item 7 applies to each relocated
  destination: a test that asserts only that the screen renders would pass
  while every route into it was missing.

The inward-path assertion for the **focus screen's** suggestion strip is
deliberately **not** on this list: it is CP8's, because CP8 is the checkpoint
running when both the strip and this screen exist (item 5's third bullet, CP8
item 5).

### CP7 — Workout board screen

`4a`'s board. Replaces the top half of today's `ActiveWorkoutScreen`.

1. Top bar: `X` (opens the leave sheet below), title with elapsed timer,
   `Finish`. No bottom nav, and it costs nothing: the board route is not a
   top-level destination, so `RepFlowNavHost.kt:49`'s `TOP_LEVEL_ROUTES`
   gate suppresses the bar with no code (CP2 item 6).

   **The `X` opens the design's leave sheet** (revision 13; `nExitSheet`,
   `RepFlow.dc.html:1437–1447`), titled "Leave this workout?" with the
   design's "Leaving is not finishing" line, not a bare destructive
   confirmation. Its actions, in the design's order:
   - **`Leave it running and go Home`** — the primary. Navigation to `HOME`
     with `popUpTo(HOME)` and `launchSingleTop = true` — explicit stack
     removal, the idiom `RepFlowNavHost.kt:53–57` already uses for
     top-level switches — so the board entry is popped rather than left
     under a second Home, and Back from Home cannot reopen the board
     (revision 14). It calls no use case, so the session stays active in
     Room and its clock keeps running from its stored start time. Home's resume card
     (CP5 item 2) appears on arrival with no restart, because Home observes
     the active session through `ObserveActiveWorkoutSession`, a Room `Flow`
     (`LocalWorkoutRepository.kt:44–45`).
   - **`Finish and save it now`** — **not rendered at CP7.** CP9 adds it to
     this sheet, raising CP9's single finish request (CP9 item 1), so no
     finish option ever exists outside that request.
   - **`Abandon this workout`** — destructive styling, and it opens a
     destructive confirmation dialog whose confirm is the only caller of the
     existing `AbandonWorkoutSession` path (`ActiveWorkoutViewModel.kt:305`),
     **with today's behaviour exactly** (revision 14, the user's decision of
     2026-09-30): the session is marked `ABANDONED`
     (`AbandonWorkoutSession.kt:16–24`, `WorkoutSession.kt:129–130`) and
     `LocalWorkoutRepository.update` re-inserts its sets (`:80–88`), so
     **nothing is deleted** and no write path is added. The copy says so
     rather than the design's `Discard everything logged`: the dialog is
     titled `Abandon this workout?`, says the workout is marked abandoned
     and left out of History (which lists `COMPLETED` sessions only,
     `WorkoutSessionDao.kt:39`), that the sets logged so far stay stored on
     this device, and that this cannot be undone; its confirm reads
     `Abandon`. The label and copy difference is deviation `D18`; the
     design abandons straight from the sheet, and the extra dialog is
     deviation `D17`.
   - **`Keep training`** — dismisses the sheet.

   **CP7 also owns the system-back interception, handed over from CP2 item
   6**, because CP7 is the checkpoint where both halves exist: back in
   workout mode opens *this* leave sheet rather than popping the back stack.
   **This deliberately replaces today's unconfirmed exit** — the bare
   `OutlinedButton(onClick = { onAbandonWorkout(content.sessionId) })` at
   `ActiveWorkoutScreen.kt:203–205`, which abandons a session with no
   confirmation of any kind, the same gap CP9 item 1 names for the bare
   finish `Button` at `:200`. Intercepting system back is the plan's
   flagged-for-sign-off platform behaviour change, and this is the
   checkpoint it ships in. The leave path adds no domain or application
   write path. **CP7 owes three instrumented tests**, the second being the
   assertion CP2 item 7 listed through revision 5, moved here with the
   behaviour:
   - `Leave it running and go Home` lands on Home with the session still
     active and Home's resume card visible, **and the back stack then holds
     no board entry, so Back from Home does not return to the board**;
   - the system back gesture in workout mode opens the leave sheet rather
     than popping the back stack;
   - `Abandon this workout` does not abandon the session until its
     destructive confirmation is confirmed.

   **CP7 also owes the persisted outcome of that confirm**, in a new
   instrumented test over Room (the retention is `LocalWorkoutRepository`'s,
   which the in-memory fakes do not exercise): after logging sets and
   confirming, the session reads back `ABANDONED` with every logged set
   still present.
2. Progress line: `N of M exercises · S/T sets`.
3. One row per exercise: name and status chip carrying **an icon and a
   word** (`4/4 sets`, `2/4 sets`, `3 sets`, `up next`). Tapping the row opens
   focus mode for that exercise. No `⋮` (item 7).
4. **Out-of-order work is the point of the board** — the design's whole
   framing is "work the board", and the row's `up next` marker is a hint, not
   a lock. This is already true of the domain (sets attach to a
   `WorkoutExercise`, in any order).
5. Empty board state: `4a`'s own copy — the clock is already running, add
   whatever machine is free.
6. `Add exercise` opens the picker **sheet** (converted from today's
   `DropdownMenu`). **The per-exercise recommendation row inside that menu
   (`ActiveWorkoutScreen.kt:392–415`) is carried into the sheet exactly as it
   stands when CP7 runs.** CP6 lands first (see the execution order above), so
   what CP7 carries is the row's result label, `topReason` and `isOverridden`
   marker plus CP6's `Why ›` link into the recommendation screen — **not** the
   three inline override `TextButton`s, which CP6 has already replaced. The
   rule is "carry across whatever is there", so the item holds unchanged if
   CP6's replacement is for any reason not in place. This is stated because
   the conversion deletes that menu: without the carry-across the app would
   lose the recommendation screen's only entry point until CP8 lands, and no
   current test would catch it (`ActiveWorkoutScreenTest.kt:64` passes a no-op
   `onOverrideRecommendation` and asserts nothing).
7. **No row sheet and no `⋮` trigger** (revision 12, on the user's product
   decision of 2026-09-30). The design's row sheet (`nRowSheet`) has six
   options — `Do this later`, `Swap for another exercise`, `Superset with the
   next exercise`, `Skip for today`, `Add a note`/`Edit the note` and `Remove
   from this workout` — and **none has domain or application backing**:
   `WorkoutSession` (`WorkoutSession.kt:102–175`) has no remove or reorder
   operation, `WorkoutExercise` has no skipped status, and no use case in
   `application/workout/` reorders, removes or skips an exercise. CP7 renders
   none of the six and adds **no domain or application write path**; each is
   a deviation-register row with the reason `no domain backing` (`D1`–`D3`
   for superset, swap and notes; `D13`–`D15` for do-later, skip and remove).
   With no option left, the sheet and its `⋮` are **not rendered** — a
   trigger that opens an empty sheet is worse than its absence, the same
   reason an unbacked option is left out rather than disabled — so the row's
   only action is item 3's tap into focus mode. `Remove` would also have
   needed a data-safety rule this plan does not make:
   `LocalWorkoutRepository.update` (`:80–88`) deletes and re-inserts a
   session's exercise rows from the aggregate, so dropping an exercise would
   delete its recorded sets. **CP7 owes a state test** that a board row
   exposes no overflow trigger and no row-sheet option.
8. The rest strip renders here too, as the design shows, so the timer keeps
   running while the user browses other exercises.

### CP8 — Workout focus mode

`4a`'s focus screen. The conversion F1 cares most about, and the one the
parent explicitly declined.

1. Top bar: `Board` (back to CP7), elapsed, `Finish`.
2. Header: `Exercise N of M`, name, `X of Y sets done`.
3. Logged-set rows: status icon (filled check / flame for warm-up / open
   circle for a not-yet-logged target set), number, summary text, and edit
   affordance. Pending target sets render as placeholder rows, which is how
   the design shows the target without a separate chip.

   **This deletes today's planned-target chip row, and one of the chips'
   tests is a regression guard whose subject goes with them.** The chips are
   `workout_active_plan_warmup_progress`, `workout_active_plan_working_progress`,
   `workout_active_plan_rep_range`, `workout_active_plan_duration_range` and
   `workout_active_plan_rest`; item 2's `X of Y sets done` header is what
   replaces the first two, and the placeholder rows are what replace the
   rest. `everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth`
   (`ActiveWorkoutScreenTest.kt:167–194`) exists to prove that a chip row too
   wide for the screen **degrades visibly instead of pushing its last chip off
   the right edge** — its own comment (`:169–176`) records that the guard is
   `weight(1f, fill = false)` per chip plus `RepFlowStatusChip`'s
   `maxLines = 1`/`Ellipsis`, that without the weight the third chip measures
   to zero width and stops being displayed, and that this was "the silent
   clipping the round-1 fix exists to prevent, and which was otherwise
   verified only by eye". It is parent-milestone regression coverage, so
   deleting its subject without saying where the guard goes would retire a
   fix rather than convert a screen.

   **The guard is retargeted, not retired.** The placeholder rows and the
   logged-set summary rows this item builds carry the same risk in the same
   place — a rep range, a duration range and a rest value rendered on one row
   at a phone width — so CP8 rewrites the test against the rows that replace
   the chips, keeping its method: deliberately absurd values that cannot fit
   at any phone width, then `performScrollTo().assertIsDisplayed()` on every
   value the row is supposed to show. If the converted row genuinely cannot
   overflow — because item 3's rows stack rather than lay out horizontally —
   that is the one case where retirement is correct, and CP8 records it in the
   deviation register with the measurement that shows it, never by deleting
   the method silently.

   **Two pieces of production documentation sit in the same file, and item 3
   must handle each differently.** `PlannedTargetSummary`'s KDoc
   (`ActiveWorkoutExerciseCard.kt:212–221`) states the clipping guard from the
   production side — *"Each chip takes `weight(1f, fill = false)` … a row that
   outgrows the screen degrades visibly rather than clipping off the right
   edge"* — and goes stale with the chips it describes, reporting nothing when
   it does. `LoggedSetRow`'s KDoc (`:287–291`) is **not** stale text but a
   live constraint on the rows item 3 builds: *"The summary line stays one node
   carrying exactly `primary + warm-up + extra`: `ActiveWorkoutScreenTest`
   matches that concatenation by exact text, so the two suffixes cannot be
   promoted into chips of their own."* That is why
   `aSetBeyondThePlannedWorkingCountIsMarkedExtra` survives only if the
   converted row keeps the concatenation in one node — and if item 3 decides
   the design wants separate chips, the KDoc, the method and the design call
   move together rather than the method being patched alone.
4. `Last: …` hint plus `Undo last`.
5. **Suggestion strip when a recommendation exists, with `Why ›` wired.** CP6
   lands before CP8 (see the execution order above), so CP6's recommendation
   screen and its route already exist when CP8 builds this strip: **CP8 ships
   the strip as the proposed action plus the policy's top reason, with a
   `Why ›` affordance routed into CP6's recommendation screen.** CP8 is the
   second half of the pair to land, so by the rule stated with the execution
   order it wires the two halves together — the same rule CP14 item 4 applies
   to CP9/CP14. A focus screen shipping a `Why` that leads nowhere would be
   B1's "no checkpoint launches the app into an unbuilt route" in the one
   checkpoint this milestone exists for; here the route is built two
   checkpoints earlier, so that outcome is unreachable rather than guarded
   against. Shipping the strip *without* `Why ›` would be the mirror failure —
   a missing affordance rather than a broken one, on the surface F1 cares most
   about, with no checkpoint after CP8 assigned to add it. That is why `Why ›`
   ships with the strip rather than after it.

   **No registry edge is added for this.** `CP8 depends_on CP6` would make the
   graph restate what the array order already guarantees and would change no
   execution order — CP4 sits at array index 4 and is selected long before CP8
   either way, so the edge costs nothing and buys nothing. It is left out
   because the graph should carry real constraints rather than restatements of
   the selection rule.

   CP7 item 6's carry-across does not cover any of this: it keeps the picker
   sheet's entry point continuous and says nothing about a route out of focus
   mode.

   **CP8 owes two assertions here**: a state test that the strip renders the
   recommendation summary when one exists and is absent when none does, and
   **the inward-path assertion that the strip actually reaches CP6's
   recommendation screen**.
6. **Steppers replace the text fields.** Weight and reps/duration each become
   `RepFlowStepper`: minus, value-as-button (opens CP3's keypad), plus, with
   the design's caption beneath (unit and step size for weight; rep target
   for reps). `6b`: "Step size comes from the exercise's load increment" —
   and `Exercise.defaultLoadIncrement` **already exists** and is already
   rendered on the library screen today. CP8 plumbs it onto
   `ActiveExerciseUi`. This is the exact plumbing the parent milestone
   declined as out of its scope, and it is the reason `RepFlowStepper` has
   sat unconsumed.
7. Detail disclosure (`sliders` + summary + caret) revealing **scale rows**,
   not text fields: RPE, Pain, Technique. **Four existing
   `ActiveWorkoutScreenTest` methods sit behind this disclosure, and the swap
   splits them three-and-one** — the distinction matters because three of them
   are about the disclosure and the fourth is about the values it reveals:
   - **Three survive unchanged**, and they are the parent's accessibility
     work: `theSetDetailFieldsAreHiddenUntilTheDisclosureIsExpanded`
     (`:239–247`), `expandingTheDisclosureRevealsAllThreeOptionalFields`
     (`:249–258`) and
     `theDisclosureHeaderReadsItsExpandedStateToAccessibilityServices`
     (`:284–301`). The first two locate the three fields by their label text
     and assert presence or absence, which a scale row satisfies exactly as a
     text field does; the third asserts the header's `stateDescription`
     contract, which the affordance swap does not touch. **That behaviour is
     unchanged by the swap, and CP8 keeps it.**
   - **One does not survive**, and it is the reason this item cannot say the
     disclosure's tests are simply kept:
     `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing`
     (`:260–282`) expands the disclosure and then drives the very fields this
     item replaces with `performTextInput` —
     `node(R.string.workout_active_rpe_label)…performTextInput("8")` and the
     Pain equivalent (`:273–274`) — before collapsing, tapping `Add set` and
     asserting `recordedRpe == 8.0` and `recordedPain == 2`. That is the same
     mechanism the set-entry note below is about: **a scale row cannot satisfy
     `performTextInput` any more than a stepper can**, so this method fails at
     CP8 by construction. It is **rewritten to drive the scale rows and assert
     the same contract** — values entered in the disclosure survive a collapse
     and reach `onRecordSet`, the property the test's own inline comment names
     ("the values live in `ExerciseCard`'s own state, not in the section, so
     hiding them must not drop them", `:275–276`) and the only place in the
     file that asserts it. The assertion *values* need no change, since `D11`
     keeps the RPE row on the domain's `0.0..10.0` range; only the input
     mechanism moves, and saying so is what stops the rewrite quietly
     narrowing what it asserts.

   **The block comment above those four tests goes stale in the same edit,
   and it is on the edit list.** `ActiveWorkoutScreenTest.kt:217–226` reads
   *"The **four** tests below pin the whole contract … that a value **typed
   into** them reaches `onRecordSet` whether the section is open or closed at
   submit time"*. This item splits those four three-and-one and replaces the
   typing with scale rows, so both the count and the mechanism the sentence
   names become false, and **nothing in the suite reports a comment** — the
   `RepFlowIcons.Nav` KDoc situation of CP2 item 2, and the
   `topLevelDestinationsKeepTheirRoutesAndOrder` KDoc of CP2 item 7(b), inside
   the file this item is already editing. It is rewritten in the same edit as
   the method beneath it, not left for a later reader to disbelieve.

   The milestone's file count is unaffected: both this method and
   `tappingAddSetClearsTheEntryFields` live in `ActiveWorkoutScreenTest`,
   which CP8 already owns. **CP8 rewrites two methods in one file**, and the
   second of them is named in the set-entry note below.
8. Warm-up toggle as the design's chip with the flame glyph and its hint
   line, replacing the `Switch`.
9. Pinned bottom bar: `Log set` (primary, becomes `Log warm-up` when the
   warm-up chip is on) and `Next ›`. On the last unfinished exercise, `Next ›`
   returns to the board; CP9 makes that return raise the finish sheet, as the
   design's `nNextExercise` does (CP9 item 1).
10. Rest strip, with the tick loop carried through byte-for-byte.
11. **Technique notes** — `docs/UX_FLOWS.md:54`'s row in the active-workout
    "It should show" list, rendered when the exercise has them.
    `Exercise.instructions` (`ExerciseInstructions?`) already exists and is
    already authored in the exercise editor, but `instructions` appears
    nowhere in `presentation/workout/` today. CP8 plumbs it onto
    `ActiveExerciseUi` beside `defaultLoadIncrement` (item 6) and renders it
    under the header (item 2) as its own collapsed note row — **not** inside
    item 7's detail disclosure, which carries the parent's `stateDescription`
    contract and the three instrumented tests item 7 keeps unchanged, and
    which adding unrelated content would put at risk for no reason. Absent entirely when the field is null, with a
    state test pinning that. This is the plan's own domain-backing test
    applied honestly: a read-only render of a field the domain already
    stores, at the cost of one more field on an object CP8 is already
    extending. It is why `:54` is not on CP16 item 5's declared-intent list.

**Both new `ActiveExerciseUi` fields carry defaults, so the file count stays
exact.** Items 6 and 11 each add a field to `ActiveExerciseUi`
(`ActiveWorkoutUiState.kt:77–84`), which outside its own file is constructed
in exactly three places: `ActiveWorkoutViewModel`, `ActiveWorkoutScreenTest`
(instrumented, and already CP8's to change) and `ActiveWorkoutScreenWiringTest`
(JVM). Giving both fields a default — as `plannedTarget` already does
(`:83`) — leaves `ActiveWorkoutScreenWiringTest` compiling untouched, so CP8
changes one existing test file rather than two.

**Test consequence, stated because it is a preserved-invariant collision.**
`ActiveWorkoutScreenTest.tappingAddSetClearsTheEntryFields` (`:303–316`)
drives the load field with `performTextInput` (`:311`). A stepper cannot
satisfy that, and the parent milestone correctly refused to change it under a
plan whose non-goals forbade an affordance change. **This plan's scope
explicitly permits it**, so the test is rewritten to drive the stepper and the
keypad, and to assert the same underlying contract: that recording a set
clears the entry state. The rewrite is named here so it is reviewed as a
deliberate decision, not discovered in a diff.

**This is the second of CP8's two `performTextInput` rewrites in that file,
not the only method the checkpoint reaches.** Item 7 owns the first —
`aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing`,
which drives the RPE and Pain fields the same way and fails for the same
reason once they become scale rows. The two are stated in the items that
change their subjects rather than together here.

**Every method in `ActiveWorkoutScreenTest`, accounted for.** Through
revision 8 this paragraph closed with "`ActiveWorkoutScreenTest` is rewritten
in two named places and **preserved everywhere else**". The file holds
**fourteen** `@Test` methods (`grep -c "@Test"`), and the two named rewrites
plus item 7's three survivors are five of them. The other nine all render
through `ActiveWorkoutExerciseCard`'s flat one-exercise-card list — the top
half of which CP7 replaces and the whole of which CP8 replaces — so
"preserved" was a positive claim about nine methods this checkpoint had never
been pointed at. Three of the nine do not survive item 3 as written, and one
of those three is the failure mode this plan calls the one most worth naming
in advance. The table is the account, and it replaces the claim:

| `@Test` | Method | What it reads | Owner | Mode |
|---|---|---|---|---|
| `:106` | `weightAndRepsExerciseShowsLoadAndRepsFieldsButNotDuration` | `workout_active_load_label` / `_reps_label` present, `_duration_label` absent | item 6 | Depends on whether the stepper keeps a labelled node. Item 6 keeps the three label strings on the steppers' captions, so the assertions hold as written; if a caption is dropped the method is rewritten against the caption that replaces it, never deleted |
| `:115` | `repsOnlyExerciseShowsOnlyTheRepsField` | same three labels, REPS_ONLY | item 6 | as above |
| `:124` | `durationExerciseShowsOnlyTheDurationField` | same three labels, DURATION | item 6 | as above |
| `:133` | `aDurationSetSummaryShowsItsDurationNotAMisleadingZeroLoadRow` | `workout_active_set_row_duration` | item 3 | Summary format pinned by a parent-milestone defect fix — a DURATION set must not render a zero-load row. Item 3's rows **carry the same three formats forward**; the method survives unchanged, and if the conversion renders the summary differently the format is changed in `strings.xml` and the method rewritten against it, with the defect it guards restated |
| `:142` | `aWeightAndRepsSetWithoutARecordedLoadShowsTheRepsOnlyFormatInsteadOfAZeroLoad` | `workout_active_set_row_reps` | item 3 | as above — the second of the same pair of defect fixes |
| `:151` | `aPlannedExerciseShowsWarmupAndWorkingProgress` | the three planned-target chips | items 2, 3 | **Failing assertion.** The chips are gone; `Warm-up n/m` and `Working n/m` become item 2's `X of Y sets done` header. Rewritten against the header |
| `:167` | `everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth` | the chip row's overflow behaviour | item 3 | **Failing assertion, and a regression guard whose subject is deleted.** Retargeted onto item 3's rows — see item 3 |
| `:196` | `anAdHocExerciseShowsNoPlannedTargetSummary` | `workout_active_plan_working_progress` **does not exist** for an ad-hoc exercise | item 3 | **No failure of any kind.** Once the planned-target summary is gone for *every* exercise the assertion passes vacuously and its subject no longer exists. This is the second instance in this milestone of `LocalBackupRepositoryAtomicityTest`'s mode (Verification strategy), and the first inside a screen conversion. CP8 rewrites it to assert what actually distinguishes an ad-hoc exercise on the converted screen — no `X of Y sets done` target in the header — or retires it in the deviation register with that reason |
| `:205` | `aSetBeyondThePlannedWorkingCountIsMarkedExtra` | `workout_active_set_row_weight_reps` + `workout_active_set_extra_suffix` | item 3 | Set classification is on the "Preserved invariants" list, so item 3's rows **must still carry the `extra` marker**; the method survives, and a conversion that dropped the marker would be changing an invariant rather than a rendering |
| `:239` | `theSetDetailFieldsAreHiddenUntilTheDisclosureIsExpanded` | disclosure hides three fields | item 7 | **Survives unchanged** (item 7) |
| `:249` | `expandingTheDisclosureRevealsAllThreeOptionalFields` | disclosure reveals them | item 7 | **Survives unchanged** (item 7) |
| `:260` | `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing` | `performTextInput` on RPE and Pain | item 7 | **Rewritten** to drive the scale rows and assert the same contract (item 7) |
| `:284` | `theDisclosureHeaderReadsItsExpandedStateToAccessibilityServices` | header `stateDescription` | item 7 | **Survives unchanged** (item 7) |
| `:303` | `tappingAddSetClearsTheEntryFields` | `performTextInput` on the load field (`:311`) | item 6 | **Rewritten** to drive the stepper and the keypad (the set-entry note above) |

Plus the block comment at `:217–226`, which item 7 rewrites, and which is not
a method and therefore not in the count.

**So CP8's obligation on this file is: two rewrites forced by the affordance
swap (`:260`, `:303`), two failing assertions and one silent pass forced by
the chip removal (`:151`, `:167`, `:196`), and nine methods it must keep
passing** — three by design (item 7's survivors, `:239`, `:249`, `:284`) and
six because their subjects are invariants or summary formats item 3 carries
forward (`:106`, `:115`, `:124`, `:133`, `:142`, `:205`). Two plus three plus
nine is the file's fourteen. The file is *changed* by CP8, not merely edited in two
places; stating that is what stops the implementation reading "preserved
everywhere else" and patching only the two compile-or-assert failures it
happens to hit.

**JVM second pass over `presentation/workout/`, for CP5, CP7, CP8 and CP9**
(revision 12). The package's two JVM test files invoke no composable, so the
instrumented enumeration above cannot see them:

- **`ActiveWorkoutScreenWiringTest`** (8 `@Test`) reads three `internal`
  declarations in files CP7 and CP8 replace: `restTimerProgress`
  (`ActiveWorkoutScreen.kt:332`, four rest-strip methods), `setsWithExtraFlag`
  (`ActiveWorkoutExerciseCard.kt:665`, three quota methods) and
  `ControlRowMinHeight` (`:710`,
  `everyTapTargetOnTheSetEntrySurfaceClearsTheFloor`). **Each stays
  `internal` in `presentation.workout` and stays consumed by the converted
  surface:** the rest strip keeps `restTimerProgress` (item 10), item 3's rows
  keep `setsWithExtraFlag` (the `extra` marker is a preserved invariant), and
  `ControlRowMinHeight` becomes the floor of the focus screen's control rows.
  All 8 survive unchanged, and no green test is left pinning a dead constant.
- **`ActiveWorkoutViewModelTest`** (14 `@Test`) drives the ViewModel's entry
  points, which the conversions feed rather than change: CP7/CP8's steppers,
  keypad and scale rows call the same record/undo/edit entry points (six
  methods, `:379`–`:586`); CP9's sheet confirms through the same
  `onCompleteWorkout` and the done screen takes the session id from the
  finish action, so `:650`'s return to no active session holds; CP5's start
  sheet calls the existing `onStartWorkout` path with its archived-plan
  exclusion unchanged (seven methods, `:150`–`:319` and `:439`). A CP5 that
  moved the start path elsewhere would move those seven with it and add this
  file to the roll-up. All 14 survive.

Neither file is edited, so the milestone's file count is unchanged.

### CP9 — Workout finish and session summary

`4a`'s finish sheet and done screen.

1. **Finish confirmation lists anything unfinished**, per the design. Today
   there is no confirmation at all — `ActiveWorkoutScreen.kt:200` is a bare
   `Button(onClick = { onCompleteWorkout(…) })` — so CP9 *builds* this
   behaviour; CP14 item 4's "confirm before finishing" toggle later *gates*
   it. CP9 lands before CP14 (see the execution order above), and there is no
   `SettingsRepository` or preferences table in the tree today, so: **CP9
   ships the confirmation unconditionally, and CP14 makes it conditional once
   the preference exists.** Between the two, the user gets the design's
   confirmation with no way to turn it off — the design's own default. The
   gating clause belongs to CP14 rather than here because CP14 is the second
   half to land; a toggle shipped before the confirmation existed would gate
   nothing, which is the outcome CP14 item 3 calls "a worse outcome than not
   building it". CP9's own assertion is that the confirmation lists unfinished
   work; CP14 owes the assertion that the toggle actually turns it off (CP14
   item 4).

   **Every finish entry point goes through this one sheet** (revision 12).
   The design opens the same sheet from each (`nAskFinish`, and
   `nNextExercise` when nothing unfinished remains), and only its confirm
   finishes. CP9 builds one sheet, hosted by the workout surface, behind one
   finish request that every entry point raises; the sheet's confirm is the
   only caller of `CompleteWorkoutSession`:
   - the board top bar's `Finish` (CP7) and the focus top bar's `Finish`
     (CP8);
   - the leave sheet's `Finish and save it now` (CP7 item 1), **which CP9
     adds to that sheet** as the second half to land — CP7 omits it because
     this request does not exist yet;
   - focus mode's `Next ›` when no unfinished exercise remains, which returns
     to the board with the sheet raised (CP8 item 9);
   - **Home's `Finish it` (CP5 item 2), which CP9 re-wires** as the second
     half to land: it opens the workout surface with the sheet raised, and
     dismissing the sheet returns to Home by the same `popUpTo(HOME)` stack
     removal as CP7 item 1's leave path, so no board entry is left behind
     Home. The design draws that sheet over
     Home instead — deviation `D16`, kept so the unfinished-work list is
     derived in the one place planned targets are resolved today
     (`ActiveWorkoutViewModel.kt:384`) and CP14's gate has one site.

   **CP9 owes an instrumented test that Home's `Finish it` opens the finish
   sheet rather than completing the session, and that dismissing the sheet
   returns to Home with the session still active and no board entry on the
   back stack, so Back from Home does not return to the board** (revision
   13, pinning `D16`'s dismiss half; the back-stack clause is revision 14's).
2. Summary: elapsed, working sets, exercises completed, and a per-exercise
   recap with its delta.
3. **Progression recommendations surface here** (CP6), which is
   `UX_FLOWS.md`'s own requirement for this screen.
4. PR detection is derived from existing history; if it cannot be computed
   cheaply and correctly it is omitted and recorded, never faked.
5. Session notes are **not** built (deferred capability).

### CP10 — Exercise library conversion

`2c`, plus `2b` for the editor.

1. Search, and the design's `Active` / `Archived` filter, converted to the
   design's own composition. The inline comment in
   `ExerciseListScreenWiringTest.everyTapTargetClearsTheMinimum` (`:74–76`,
   "which is why the filter row uses the pill picker instead of a clickable
   chip") is this item's to restate, on CP2 item 2's terms for
   `RepFlowIcons.Nav`'s KDoc (revision 11): it is rewritten to name the
   control the converted filter uses, and if that control is tappable its
   height joins that method's assertions against the same 44dp floor.
2. Rows: name over meta, with the archived state as the design draws it, and
   the row action as a sheet rather than a `DropdownMenu`. **This item owns
   `ExerciseRowMinHeight`'s fate** (`ExerciseListScreen.kt:447`, `= 64.dp`,
   consumed at `:329` as the row card's `heightIn(min = …)`), decided in
   revision 11: it **survives and stays the converted row card's
   `heightIn(min = …)`**, taking `2c`'s drawn row height if that differs from
   64 but never a value outside the design's 56–68 range. So the row-height
   transcription `rowAndFabSizesAreTheDesignsOwn` pins is neither left
   unguarded nor left green on a dead constant (item 6).
3. **The `Create "<query>"` empty state is built.** The design's `Not here?
   Create "press"` is one of the parent milestone's three named CP5
   deviations, declined then because it needed a route argument and a
   ViewModel change. Both are in scope now, and **which way each goes is
   decided here (revision 11)**, because item 6's JVM second pass depends on
   it:
   - **The route argument is optional and never touches the mode.**
     `EXERCISE_NEW` is `"exercises/new"` today (`RepFlowDestinations.kt:15`),
     navigated to bare (`RepFlowNavHost.kt:71`), and `ExerciseEditorViewModel`
     derives its mode from `EXERCISE_EDIT_ARG` alone (`:50–51`, `:65`). The
     create route gains one optional prefill-name argument, absent by default,
     so the bare route keeps resolving; the mode stays derived from
     `EXERCISE_EDIT_ARG` only.
   - **The prefill seeds the draft and never overrides it.** `initialState`
     reads `KEY_NAME` from the `SavedStateHandle` and falls back to `""`
     (`:67`); the fallback becomes the prefill, so a restored draft (D-22)
     always wins.
   - **An untouched prefill is not a change.** `isDirty`'s `Create` branch
     tests `state.name.isNotBlank()` (`:271`); its name clause becomes
     `state.name.trim() != prefill.trim()`, with the prefill `""` when absent,
     which is exactly that blank check in that case — so a whitespace-only
     name still dismisses without the discard dialog, as today.
   - **`ExerciseListContent.Empty` keeps its shape**
     (`ExerciseListUiState.kt:47–49`). The query is already on
     `ExerciseListUiState.query` (`:15`) and the screen reads it there; the
     screen gains one `(String) -> Unit` create-from-query callback beside the
     existing `onCreateClick: () -> Unit`, which is not re-typed.
4. `2b`'s new-exercise screen: presets instead of typing where the design
   uses them, inline validation, and the tracking-type row as a scale-row
   style picker rather than a stock `FilterChip`. **The ViewModel's input
   contract does not change (revision 11):** presets call the same
   `String`-typed setters a typed value does
   (`ExerciseEditorViewModel.kt:132–140`), and `onTrackingTypeChanged`'s
   clear-the-load-and-queue-a-message rule (`:147–166`) is kept as is. Item 4
   changes the affordances, not what they feed.
5. Deviation: no muscle-group filter or group in the row meta — `Exercise`
   has no group field. Meta becomes tracking type · rest · plan usage. The
   design's `in 2 plans` **is** buildable and is built. **What that takes,
   stated in revision 11 because item 6's JVM second pass reaches it:**
   nothing supplies per-exercise plan usage today — `ExerciseListItem`
   (`ExerciseListItem.kt:11–17`) has no such field, `ExerciseListViewModel`
   takes only `ObserveExercises`, `ArchiveExercise` and `RestoreExercise`, and
   `TrainingPlanRepository` has no usage query. Item 5 adds one read query to
   that existing port (implemented by `LocalTrainingPlanRepository` over the
   existing tables, no schema change), a use case observing it, a fourth
   `ExerciseListViewModel` dependency, and a `planUsageCount` field on
   `ExerciseListItem` **with no default**, so every construction site is a
   compile failure rather than a silent zero. The count is of non-archived
   plans whose latest version holds the exercise. This is a read model over
   an existing repository, inside "Preserved invariants"' layer summary as
   written.
6. **The existing tests CP10 changes, enumerated on CP2 item 7's terms.**
   `grep -rln "ExerciseListScreen(\|ExerciseEditorScreen(" app/src/androidTest/ app/src/test/`
   returns exactly two classes: `ExerciseListScreenTest` (15 `@Test`) and
   `ExerciseEditorScreenTest` (13). Both use
   `createAndroidComposeRule<ComponentActivity>` and set their own content, so
   CP2's destination change cannot reach them — which is exactly why neither
   appeared in any count before revision 9. **What this grep can and cannot
   see, stated on CP2 item 7's terms and corrected in revision 10:** a pattern
   matching a composable *invocation* finds only files that invoke the
   composable — instrumented screen tests — and by construction cannot find a
   JVM test that pins the same screen's non-composable wiring instead. One
   exists for the library and revision 9's enumeration did not name it; it is
   added below on its own terms rather than folded into the two classes above.
   **Revision 10 ran that JVM second pass over the one file round 9 named;
   revision 11 runs it over both of CP10's packages**, on CP13 item 6's terms:
   `ls app/src/test/kotlin/com/repflow/app/presentation/exercise/list/
   app/src/test/kotlin/com/repflow/app/presentation/exercise/editor/` returns
   `ExerciseListScreenWiringTest`, `ExerciseListViewModelTest` and
   `ExerciseEditorViewModelTest`, and item 5's port query reaches one JVM
   fixture outside them. Each is recorded below, including where the result is
   "survives".
   - **Three methods rewritten by item 2.**
     `rowMenuEditItemInvokesOnExerciseClick` (`:240–257`),
     `rowMenuShowsArchiveWhenViewingActiveExercisesAndInvokesOnArchiveClicked`
     (`:259–280`) and
     `rowMenuShowsRestoreWhenViewingArchivedExercisesAndInvokesOnRestoreClicked`
     (`:282–303`) each click
     `R.string.exercise_list_row_menu_content_description` and then a
     `DropdownMenuItem` by text (`exercise_list_row_menu_edit` / `_archive` /
     `_restore`). Item 2 replaces that menu with CP3's sheet, and CP3 retires
     `DropdownMenu` from every converted screen. **Item 2 keeps the trigger's
     content description**, so the three change only in what they click
     *second* — the sheet's rows rather than the menu's items — and each keeps
     asserting the same callback with the same id. Dropping the trigger
     instead would fail all three at `performClick()` with no matching node,
     which is CP2 item 7(c)'s runtime mode reproduced eight checkpoints later;
     saying which way it goes is what stops that being discovered on a device.
   - **One method rewritten by item 3.**
     `rendersTheNoSearchResultsEmptyState` (`:98–111`) asserts
     `exercise_list_empty_no_search_results` is displayed. Item 3 replaces that
     empty state with the design's `Create "<query>"`, so the method is
     rewritten to assert the new copy **and** that tapping it reaches the
     create route carrying the query — the behaviour item 3 adds, which
     otherwise ships with no assertion at all.
   - **One method whose answer is CP3's, and CP10 states it rather than
     inheriting it.** `createFabClickInvokesOnCreateClick` (`:224–238`) clicks
     `exercise_list_add_content_description` on the FAB at
     `ExerciseListScreen.kt:188`. `6b`'s "one primary action per screen, pinned
     to a bottom bar" is what CP3's `RepFlowBottomActionBar` exists for, so
     item 1's conversion moves create onto that bar and the method is rewritten
     against it, keeping the same content description so the change is the
     container and not the affordance's identity.
   - **One method rewritten by item 5 (revision 11).** `rendersContentRows`
     (`:69–82`) constructs an `ExerciseListItem` inline (`:72–78`), so item 5's
     no-default `planUsageCount` makes it a **compile failure**; it is
     rewritten to pass a count and assert the `in N plans` meta it renders,
     keeping its name-text assertion (`:81`). The private `exerciseItem`
     helper (`:354–361`) gains the field — a fixture edit, not a method
     rewrite — and the `setContent` helper (`:39–67`) gains item 3's
     create-from-query callback with an empty default, as it already defaults
     every other callback.
   - **Untouched, and named so the rewrite does not spread**: the remaining
     nine — `rendersTheNoExercisesEmptyState` (`:84–96`),
     `rendersTheNoArchivedEmptyState` (`:113–126`),
     `rendersTheObservationFailedPanelAndInvokesRetry` (`:128–147`, the shared
     error panel, untouched by this checkpoint),
     `filterChipClickInvokesOnFilterChanged` (`:149–162`),
     `searchFieldInputInvokesOnQueryChanged` (`:164–177`),
     `theSearchClearButtonAppearsOnlyForANonEmptyQuery` (`:187–201`),
     `tappingTheSearchClearButtonInvokesOnQueryChangedWithAnEmptyQuery`
     (`:203–222`), `archivedMessageShowsSnackbarWithUndoAndInvokesOnUndoArchiveClicked`
     (`:305–329`) and `operationFailedMessageShowsSnackbarAndConsumesItWithoutUndo`
     (`:331–352`). All nine locate their subject by a string item 1 carries
     across, and **item 1 carries the strings across**: converting the search
     and filter row to the design's composition changes the container, not the
     copy. Where the design's own empty-state copy genuinely differs from
     `2c`, the string changes in `strings.xml` and the method changes with it,
     named in the checkpoint's own notes — never patched silently.
   - **`ExerciseListScreenWiringTest` (JVM,
     `app/src/test/…/presentation/exercise/list/`), on its own terms — new in
     revision 10, not reachable by the grep above.** Its own KDoc states why
     it exists: `ExerciseListScreenTest` proves each state renders and each
     affordance fires its callback, but cannot see a mapping that is silently
     wrong in a state it never constructs. Five `@Test` methods, three
     surviving and two rewritten as **compile failures**:
     - `filterOrderCoversEveryStatusFilterExactlyOnce` and
       `eachFilterCarriesItsOwnDistinctLabel` read `ExerciseListFilterOrder`
       and `exerciseListFilterLabelRes` (`ExerciseListLabels.kt:20`, `:25–29`).
       Item 1's filter-row conversion changes the row's composition, not this
       list or this mapping, so both survive unchanged.
     - `everyEmptyReasonExplainsItselfDifferently` reads
       `exerciseListEmptyMessageRes(NO_SEARCH_RESULTS) ==
       R.string.exercise_list_empty_no_search_results`
       (`ExerciseListLabels.kt:33–37`). Item 3's `Create "<query>"` empty state
       keeps this same string resource id and changes its **text** in
       `strings.xml` to the create copy, on the same "container changes, not
       the copy" rule item 1's carried-across strings already use above — the
       id-equality assertion this method makes is unaffected by a text change,
       so it survives unchanged, and `rendersTheNoSearchResultsEmptyState`
       above is what actually pins the new copy and the new tap behaviour.
     - `rowAndFabSizesAreTheDesignsOwn` and `everyTapTargetClearsTheMinimum`
       both read `ExerciseFabSize` (`ExerciseListScreen.kt:450`, consumed at
       `:185` and `:293`). Item 1's conversion deletes the FAB along with both
       call sites — CP3's `RepFlowBottomActionBar` replaces it, per the same
       "one primary action per screen, pinned to a bottom bar" answer this
       item already gives `createFabClickInvokesOnCreateClick` above — so
       `ExerciseFabSize` is retired with it rather than left behind as a dead
       constant. Both methods fail to **compile**, which is the mode this plan
       prefers whenever the choice is open (`RepFlowIconsTest`, CP2 item 7):
       it cannot be missed and cannot be deferred, unlike the silent pass the
       alternative would add as a fourth instance of Verification strategy's
       fourth failure mode. **They are not one rewrite (corrected in
       revision 11):** the compile failure reaches only the FAB half of the
       first.
       - `rowAndFabSizesAreTheDesignsOwn` (`:59–63`) is two assertions: a
         56–68 range for the row against `ExerciseRowMinHeight` (`:61`) and an
         exact 60f for the FAB (`:62`). The FAB half goes with the FAB; **the
         row half is carried into the rewrite unchanged**, against the
         constant item 2 keeps as the converted row's `heightIn`. The method
         is renamed to the row alone and its KDoc (`:58`) drops "the FAB
         60x60".
       - `everyTapTargetClearsTheMinimum` (`:69–78`) is rewritten against the
         bottom action bar's own size constant, asserting the same 44dp floor
         (`:71–72`). Its `RepFlowTagDefaults.minHeight < minimum` assertion
         (`:77`) stays, since item 2 keeps the archived badge
         non-interactive; its inline comment's pill-picker clause is item 1's
         to restate.
     This file adds a third file to CP10's own count — three survive from the
     grep's blind spot, two are rewritten (one keeping its row-height
     assertion) — see "Verification strategy" for the milestone-wide total
     this moves.
   - **`ExerciseListViewModelTest` (JVM, 12 `@Test`), second pass (revision
     11).** Item 5's fourth constructor dependency makes the shared
     `viewModel` field initializer (`:35–40`) a **compile failure** for the
     whole file; it gains the argument, backed by
     `InMemoryTrainingPlanRepository` — a fixture edit, not a method rewrite.
     After it **all twelve survive unchanged**: `a query with no matches
     reports NO_SEARCH_RESULTS` (`:161–179`) and every other whole-value
     `ExerciseListContent.Empty` comparison survive **because** item 3 keeps
     `Empty`'s shape, and every `Content` assertion reads
     `items.map { it.name }` only, never a whole `ExerciseListItem`, so item 5's
     field does not reach it. CP10 adds one test: an exercise held by a
     non-archived plan reports it in its usage count, and an archived plan
     does not.
   - **`ExerciseEditorViewModelTest` (JVM, 16 `@Test`), second pass (revision
     11).** Its `viewModel` helper (`:50–61`) sets only `EXERCISE_EDIT_ARG`, so
     item 3's prefill is absent in every existing test. The methods that read
     something CP10 changes, and their mode:
     - `create mode starts ready with save disabled until a name is entered`
       (`:89–101`) reads `initialState`'s `Create` branch — **survives**: with
       no prefill the name is still `""`, save still disabled, mode still
       `Create`.
     - `a draft survives recreation from the same SavedStateHandle (D-22)`
       (`:322–336`) — **survives**: it never sets the prefill, and item 3's
       "restored draft wins" rule is the property it already asserts.
     - The three back/discard methods (`:280–289`, `:291–306`, `:308–320`)
       read `isDirty`'s `Create` branch — **all survive**, since the name
       clause reduces to today's blank check when the prefill is absent.
     - `switching to a tracking type that cannot carry load clears it and
       queues a message` (`:140–167`) and `switching tracking type does not
       clear an already-blank load increment or queue a message` (`:169–180`)
       pin the rule item 4 keeps — **both survive**, and unlike their
       instrumented twin below neither is an assert-does-not-exist.
     - The other nine (name and rest validation, the three saves, the two
       edit-mode loads, the unchanged-edit no-write) read setters and edit
       mode, which items 3 and 4 leave alone. **All sixteen survive.**
     CP10 adds three tests pinning item 3's decisions: a prefilled create
     route starts with that name and save enabled; a prefilled name the user
     then edits survives recreation as the edit; backing out of an untouched
     prefilled editor dismisses without the discard dialog.
   - **`InMemoryTrainingPlanRepository` (JVM fixture,
     `app/src/test/…/application/trainingplan/`, revision 11)** is the only
     `TrainingPlanRepository` implementation in either test tree
     (`grep -rn ": TrainingPlanRepository" app/src/test app/src/androidTest`
     returns it alone, `:25`), so item 5's port query is a compile failure
     there and nowhere else; it gains the method and changes no assertion.
     `LocalTrainingPlanRepositoryTest` (androidTest) uses the real repository
     and is unaffected by an added query; CP10 tests the query in a new
     instrumented class beside it rather than editing that file.
   - **`ExerciseEditorScreenTest`: one method rewritten by item 4, twelve
     untouched.** `loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad`
     (`:140–147`) asserts `exercise_editor_load_increment_label` does not exist
     for `REPS_ONLY`; item 4 replaces typed increments with presets, so the
     label it looks for is the one being replaced — **and it is an
     assert-does-not-exist, so if the label simply disappears the method passes
     while its subject is gone**, the `anAdHocExerciseShowsNoPlannedTargetSummary`
     mode (CP8) on this screen. It is rewritten against whatever the preset row
     renders. `trackingTypeChipClickInvokesOnTrackingTypeChanged` (`:128–138`)
     is the one that looks like it should change and does not: it clicks the
     *text* `exercise_tracking_type_reps_only`, which item 4's scale-row-style
     picker still carries, so swapping the `FilterChip` for the picker leaves
     it passing. `restDurationPresetClickInvokesOnRestSecondsChanged`
     (`:149–157`) already clicks a preset ("90"). The other ten are titles,
     name-field validation, save/discard/back and the submit error, none of
     which item 4 touches.

### CP11 — Plans list and plan editor conversion

`5a` and `2a`.

1. Plans list: the design's filter row, empty states, and row cards with
   archive/restore and a start action.
2. Plan editor: the design's row cards with expand-to-edit, steppers for
   sets / rep range / rest (consuming CP3's stepper), the rest presets row,
   and the exercise picker as a sheet.
3. The plan-version note the design shows — "Changes apply to the next
   session you start from this day. Past workouts keep the version they were
   run on" — is rendered, because it states an invariant RepFlow **already
   enforces** (plan-version immutability) and never told the user about.
4. Deviations: no day tabs, no `Active plan` badge, no `Make active`, no
   superset toggle. The list is a flat set of plans, as today.
5. **The existing tests CP11 changes, enumerated on CP2 item 7's terms.**
   `grep -rln "TrainingPlanListScreen(\|TrainingPlanEditorScreen(" app/src/androidTest/ app/src/test/`
   returns exactly two classes: `TrainingPlanListScreenTest` (11 `@Test`) and
   `TrainingPlanEditorScreenTest` (15). Both are `ComponentActivity`-rooted,
   so CP2 cannot reach them. **What this grep can and cannot see, on CP10 item
   6's terms:** it is closed on composable invocations, so it finds every
   instrumented test that renders either screen and nothing else — the JVM
   second pass below is what checks the rest.
   - **Three methods rewritten by item 1, and this is the checkpoint where the
     trigger genuinely goes away.** `rowMenuEditItemInvokesOnPlanClick`
     (`:148–165`),
     `rowMenuShowsArchiveWhenViewingActivePlansAndInvokesOnArchiveClicked`
     (`:167–188`) and
     `rowMenuShowsRestoreWhenViewingArchivedPlansAndInvokesOnRestoreClicked`
     (`:190–211`) are `ExerciseListScreenTest`'s three against
     `training_plan_list_row_menu_content_description` and
     `training_plan_list_row_menu_edit` / `_archive` / `_restore`
     (`TrainingPlanListScreen.kt:199`'s `DropdownMenu`). Item 1 puts
     "archive/restore and a start action" **on the row card itself**, which is
     not a menu behind a trigger, so unlike CP10 item 6 the content
     description has nothing to survive on: all three are rewritten to click
     the card's own actions. The edit case is the one to state explicitly —
     with `Edit` off the menu, `rowMenuEditItemInvokesOnPlanClick` becomes the
     row-tap assertion `rendersContentRowsAndInvokesOnPlanClick` (`:55–68`)
     already makes, so it is **retired rather than rewritten** unless item 1
     gives edit its own affordance, and CP11 records which.
   - **One method rewritten by item 1's start action.** Item 1 adds an
     affordance no test covers, so `TrainingPlanListScreenTest` gains an
     assertion that the row's start action invokes its callback — the same
     obligation CP10 item 3 takes for `Create "<query>"`.
   - **Item 2's steppers do not reach `TrainingPlanEditorScreenTest`, and
     saying so is the point.** Its three row-action methods —
     `removeRowActionInvokesOnRemove` (`:163–177`),
     `moveDownActionOnTheFirstOfTwoRowsInvokesOnMoveDown` (`:179–199`) and
     `moveUpActionOnTheSecondOfTwoRowsInvokesOnMoveUp` (`:201–221`) — click
     `training_plan_editor_row_remove_content_description` /
     `_row_move_down_content_description` / `_row_move_up_content_description`,
     which are `IconButton`s on the row (`RowMoveAndRemoveActions`), **not**
     the `DropdownMenu` at `TrainingPlanEditorFormFields.kt:233` — that one is
     the private `ExercisePicker`, which is exactly what item 2 converts to a
     sheet, and **no existing test drives it**: nothing under
     `app/src/androidTest/` or `app/src/test/` references
     `training_plan_editor_select_exercise_placeholder` (under `app/src/` its
     only hits are its render site, `TrainingPlanEditorFormFields.kt:230`, and
     its declaration in `strings.xml`). So the
     picker conversion breaks nothing and is also covered by nothing, and CP11
     owes it a new assertion rather than inheriting one. Item 2's
     expand-to-edit row cards must **keep those three content descriptions
     reachable**, or all three methods fail at `performClick()`; that is a
     constraint on item 2, recorded here rather than discovered on a device.
     `addExerciseButtonClickInvokesOnAddRowClicked` (`:151–161`) clicks
     `training_plan_editor_add_exercise` by text, which is what opens item 2's
     picker sheet, so it survives if the trigger keeps its label.
   - **One method rewritten, answered the same way CP10 item 6 answers it —
     corrected in revision 10, not left untouched.**
     `createFabClickInvokesOnCreateClick` (`:117–131`) clicks
     `training_plan_list_add_content_description` on a genuine
     `FloatingActionButton` (`TrainingPlanListScreen.kt:98`, carrying the
     description declared at `:67`) — structurally identical to
     `ExerciseListScreen.kt`'s FAB. `6b`'s "one primary action per screen,
     pinned to a bottom bar" applies here for the same reason it applies to
     the library, so item 1's conversion moves this create action onto
     `RepFlowBottomActionBar` too, and the method is rewritten against it,
     keeping the same content description. Revision 9 named this same fact —
     "carrying the same bottom-bar question CP10 item 6 answers for the
     library, and answered the same way" — while listing the method as
     **untouched**, which is a contradiction: CP10 item 6's answer for that
     question is a rewrite. It is not untouched.
   - **Untouched**: `TrainingPlanListScreenTest`'s empty/error/filter and
     snackbar methods (`:70–79`, `:81–94`, `:96–115`, `:133–146`, `:213–237`,
     `:239–260`); and `TrainingPlanEditorScreenTest`'s other eleven — the two
     title methods (`:71–79`, `:81–88`), loading (`:90–100`), not-found
     (`:102–114`), the two name-field methods (`:116–126`, `:128–140`), the
     no-exercises hint (`:142–149`), save (`:223–239`), discard (`:241–257`),
     back (`:259–269`) and the submit error (`:271–282`). The hint is the one
     worth naming: it asserts `training_plan_editor_no_exercises` by text, so
     it survives only because item 2 carries the copy across, on the same
     rule CP10 item 6 states for the library's empty states.
   - **The JVM second pass, on `ExerciseListScreenWiringTest`'s terms — new
     in revision 10.** `presentation/trainingplan/list/` holds
     `TrainingPlanListViewModelTest` (JVM) and
     `presentation/trainingplan/editor/` holds `TrainingPlanEditorViewModelTest`
     (JVM); neither renders a composable or drives an affordance, both test
     ViewModel state and event handling, and this checkpoint changes neither
     screen's ViewModel contract, so both survive unexamined by this
     checkpoint's conversion. Unlike `presentation/exercise/list/`, the plans
     list has no `ExerciseListLabels.kt`-shaped file of pure, non-composable
     mapping — `TrainingPlanListItem`, `TrainingPlanListRoute`,
     `TrainingPlanListScreen` and `TrainingPlanListUiState` are its only other
     files — so there is no analogous wiring test this checkpoint could break
     the way CP10 item 6 could have broken `ExerciseListScreenWiringTest`; the
     result of the second pass is genuinely empty, and it is recorded as a
     result rather than left as an absence, on CP13 item 6's own terms.

### CP12 — History and workout detail conversion

`3a` and `3b`.

1. History: count line, rows with name, meta and caret, PR badge where
   derivable, and the existing filters converted to the design's chips.
2. Workout detail: the `Time · Volume · Sets` stat triple, then per-exercise
   cards with delta, warm-up line and set rows. **No "skipped" state**
   (revision 14): the design labels an exercise `skipped` whenever it has no
   sets (`nHistEx`, `skipped: e.sets.length === 0`,
   `RepFlow.dc.html:3713–3718`), but `WorkoutExercise` stores sets and no
   status (`WorkoutExercise.kt:20–28`, `D14`), so an explicit skip and an
   exercise never attempted are indistinguishable. A zero-set exercise
   renders the honest derived label `No sets logged`, with no delta —
   deviation `D20` — asserted by the new `HistoryDetailScreenTest` method
   item 6 names.
3. Session invalidation keeps its destructive confirmation dialog — this is
   `6b`'s sanctioned dialog case.
4. **Deviation, and it is the one blocked by an open decision:** the set-row
   edit pencil in `3b` is not built. Editing a completed workout's sets is
   the open decision "Whether completed workouts may be manually corrected",
   which this plan must not finalize.
5. This screen also carries the parent milestone's one recorded pre-existing
   defect — a DURATION set rendering as `Set 0:  kg x ` — which this
   conversion fixes as a side effect of rebuilding the row, and CP12 asserts
   the fix. The defect is `HistoryDetailScreen.kt:60–66`: `history_detail_set_row`
   is `"Set %1$d: %2$s kg x %3$s"` and is rendered **unconditionally**, with
   `set.load?.toString().orEmpty()` and `set.reps?.toString().orEmpty()`, so a
   DURATION set supplies neither and the row degrades to literal text with the
   unit still in it.
6. **The existing tests CP12 changes, enumerated on CP2 item 7's terms.**
   `grep -rln "HistoryScreen(\|HistoryDetailScreen(" app/src/androidTest/ app/src/test/`
   returns exactly two classes: `HistoryScreenTest` (9 `@Test`) and
   `HistoryDetailScreenTest` (3). Both are `ComponentActivity`-rooted. **What
   this grep can and cannot see, on CP10 item 6's terms:** closed on
   composable invocations, so it is exhaustive over instrumented tests of
   either screen and blind to any JVM-level state test — the second pass
   below is what checks those.
   - **The one item 1 has to decide before it converts anything: where the
     invalidate action lives.** `rendersContentRows` (`:75–82`) asserts
     `history_session_invalidate_action` is **displayed on the row**, and
     `invalidateActionShowsConfirmationDialogBeforeInvoking` (`:99–116`),
     `confirmingTheDialogInvokesOnInvalidateClicked` (`:118–135`) and
     `cancelingTheDialogDoesNotInvokeOnInvalidateClicked` (`:137–157`) each
     reach the dialog by clicking it there. Item 1's row is "name, meta and
     caret" — the design's composition has no destructive action on the row
     face — so **four of this file's nine methods depend on an affordance
     item 1 as drawn does not have a place for.** Item 3 already keeps the
     confirmation dialog; what it does not say is what opens it. CP12 states
     the trigger's new home (the design's row sheet, or the detail screen) and
     rewrites those four against it. This is not a cosmetic rewrite: session
     invalidation is on the "Preserved invariants" list, and an affordance that
     quietly stops being reachable is a lost capability rather than a converted
     one.
   - **One method rewritten by item 1's filter conversion, two of the three
     candidates surviving instead — corrected in revision 10.**
     `sortOrderButtonTogglesBetweenNewestAndOldestFirst` (`:178–191`) clicks
     `history_filter_sort_newest`, `showInvalidatedChipInvokesOnShowInvalidatedChanged`
     (`:193–206`) clicks `history_filter_show_invalidated`, and
     `exerciseFilterMenuInvokesOnExerciseFilterChanged` (`:226–240`) clicks
     `history_filter_exercise_all` and then an exercise name inside one of
     `HistoryScreen.kt`'s two `DropdownMenu`s (`:263`, `:299`) — the last two
     of the seven CP3 retires. The first two locate their subject by a label
     item 1 carries across and **survive unchanged**, on the same rule CP10
     item 6 states for the library's untouched ten; only the third is
     rewritten, against the sheet that replaces the menu. Revision 9 headed
     this bullet "Three methods rewritten" while its own closing sentence said
     two of the three survive — that header is corrected, not the analysis
     beneath it.
   - **Untouched**: `rowClickInvokesOnSessionClick` (`:84–97`, a row tap
     survives any row composition),
     `invalidatedMessageShowsSnackbarAndConsumesIt` (`:159–176`, a snackbar
     this checkpoint does not touch),
     `sortOrderButtonTogglesBetweenNewestAndOldestFirst` and
     `showInvalidatedChipInvokesOnShowInvalidatedChanged` (named above).
   - **`HistoryDetailScreenTest`'s three, and what item 2 owes each.**
     `rendersRpeDurationWarmupPainAndTechniqueQuality` (`:50–87`) asserts
     `history_detail_set_warmup_suffix`, `_set_rpe`, `_set_pain` and
     `_set_technique_quality`; `rendersDurationForADurationTrackedSet`
     (`:89–117`) asserts `history_detail_set_duration`. Item 2 rebuilds every
     row those five strings render on, so both are rewritten against the
     converted rows, asserting the same five facts. `backButtonInvokesOnBackClick`
     (`:119–150`) asserts the `history_detail_back` label, not a set row — it
     survives if CP3's scaffold keeps that label, and CP12 says so rather than
     leaving the file described as three set-row tests. **Plus one new
     method** (revision 15), `aZeroSetExerciseShowsNoSetsLoggedAndNoDelta`,
     asserting item 2's `No sets logged` label and the absent delta for an
     exercise with no sets.
   - **None of the three guards item 5's defect** — the file has no assertion
     that a DURATION set omits the `kg x` row — which is why item 5 owes a
     *new* test and not a corrected one.
   - **The JVM second pass, on `ExerciseListScreenWiringTest`'s terms — new in
     revision 10.** `HistoryUiStateTest` (9 `@Test`, JVM) pins
     `visibleSessions`' filtering (invalidated, exercise, ad-hoc-only, plan
     identity, date range) and sorting, plus `availableExerciseOptions` and
     `availablePlanOptions` — pure state-derivation functions this checkpoint
     does not touch, since it converts the screen's composition, not
     `HistoryUiState`'s filtering or sorting logic. `HistoryViewModelTest` (JVM)
     drives `onSessionClick`, `onInvalidateClicked`,
     `onShowInvalidatedChanged`, `onMessageShown` and friends directly on the
     ViewModel, none of it through a composable, so item 1's relocation of the
     invalidate trigger changes nothing this file asserts — it tests that
     invalidating changes state, not where the button that starts it lives.
     Both survive this checkpoint unexamined, and the result is recorded
     because it is a result, on CP13 item 6's own terms.

### CP13 — Recovery entry and recovery history conversion

`3c` and `3d`.

1. Recovery entry: every 0–5 scale becomes a **scale row labelled at both
   ends**, per `6b`. This is `docs/UX_FLOWS.md:118`'s "should be quick and use
   scales or selectable options where possible".

   **What is there today, corrected in revision 9.** Revisions 1–8 described
   the current 0–5 inputs as "numeric text fields" in this item and in the
   `UX_FLOWS.md` table above. They are not. All six are
   `RecoveryFutsalScreen.kt`'s private `ScaleStepperRow` (`:233–245`), called
   at `:112–132` for sleep quality, energy, leg DOMS, heel stiffness, pain
   while walking and heavy legs: a label, a `-` `TextButton`, the value as
   plain `Text`, a `+` `TextButton`. The free-text number fields on that screen
   are the *futsal* ones — duration (`:148`), session RPE (`:157`) and notes
   (`:135`) — which item 2 converts. The requirement is still unmet either
   way: a `-`/`+` stepper is neither a scale nor a set of selectable options,
   and it carries no end labels at all, which is precisely what `6b`'s "always
   labelled at both ends" and item 5's polarity argument are about. But the
   plan was naming the wrong control, and a checkpoint that starts by
   replacing a text field it will not find is a checkpoint that starts wrong.
2. The futsal block, its toggles and its load figure, converted.
3. Recovery history: `3d`'s trend-first composition — the chart above,
   entries below.
4. The chart is drawn with Compose `Canvas`; no charting dependency is added.
5. **Why CP13 depends on CP4.** The six 0–5 scales this screen writes are
   exactly the six `ReadinessScore` consumes, and CP4 item 1 is what fixes
   which of them are **inverted** (DOMS, pain, stiffness, heavy legs — where
   a high number is bad). `6b`'s scale-row note is the same distinction ("a
   high number is good on sleep and bad on pain"), so the end labels item 1
   writes must be the polarity CP4 normalized, or the entry screen and the
   readiness detail sheet disagree about which end of the same scale is good.
   That is the whole of the link: CP13 reads no score, changes no readiness
   input, and adds nothing CP4 consumes.
6. **The existing tests CP13 changes: none, and that is the result of a grep
   rather than an absence of one.**
   `grep -rln "RecoveryFutsalScreen(\|RecoveryHistoryScreen(" app/src/androidTest/ app/src/test/`
   returns **nothing**. **What this grep can and cannot see, on CP2 item 7's
   terms:** it is closed on composable invocations, exactly like every other
   checkpoint's, and the reason it returns nothing is that no instrumented
   test invokes either composable at all — not that the grep's shape missed
   one. There is no instrumented test for either recovery
   screen: `app/src/androidTest/…/presentation/` holds
   `MainActivityNavHostSmokeTest`, the two backup route tests, and one class
   each for the exercise list and editor, the plan list and editor, history,
   history detail and the active workout — and no recovery screen at all.
   `presentation/recovery/` is covered only by `RecoveryFutsalViewModelTest`
   (9 `@Test`) and `RecoveryHistoryViewModelTest` (2), both JVM ViewModel
   tests that render no composable and touch none of the affordances items 1–3
   replace. `RecoveryFutsalViewModelTest` does pin the **domain** side items 1
   and 5 must preserve — `onSaveRecovery persists the current scale values`
   and `onSaveRecovery persists a scale value up to the widened maximum of 5`
   — and both keep passing through a pure affordance swap, since neither
   observes how a value is entered.

   **So CP13 is the one conversion checkpoint with no existing test to
   rewrite, and it therefore owes new ones**: the scale rows' polarity and end
   labels (item 5), and the futsal figures' converted inputs (item 2). An
   empty grep is a reason to add coverage, not a reason to skip the
   enumeration — which is why it is recorded here rather than left as silence,
   on the same terms CP2 item 7 states for a grep that returned three.

### CP14 — Settings, preference persistence, and the behaviours it gates

`5c` and `5d`.

**"The behaviours it gates" is shorthand, and the five preferences do not all
gate an existing behaviour.** Verified against the tree, they split three
ways, and the split is stated here because two of the five are behaviours
this milestone *builds* rather than switches:

- **Gates behaviour that exists today** — rest-timer auto-start (its use case
  is `application/workout/StartRestTimer.kt`), vibrate on end and
  notification (`presentation/workout/RestTimerExpiredReceiver.kt` is real,
  and the manifest already carries `VIBRATE`, `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM`, `AndroidManifest.xml:4–6`). Three of the five.
- **No behaviour exists; CP14 builds it** — `keep screen awake`. No
  `FLAG_KEEP_SCREEN_ON`, `keepScreenOn` or equivalent appears anywhere in
  `app/src/main/`, so CP14 adds the flag on the workout scaffold *and* the
  switch over it.
- **Behaviour built elsewhere in this milestone; CP14 only gates it** —
  `confirm before finishing`, built unconditionally by CP9 item 1 and made
  conditional here (item 4).

Both pass the plan's domain-backing test and neither is large — one window
flag and one clause on an existing confirmation — so CP14's `complexity: 3` /
`session_target: 3` stands rather than moving again. What the split changes is
the honesty of the count, not the size: this checkpoint carries a schema
change, a migration, a new destructive use case, a restore-path change and a
screen, and a reviewer weighing whether that is one checkpoint should be
weighing it with the two built behaviours visible rather than filed under
"toggles".

1. New `presentation/settings/`, grouped as the design groups it. This
   checkpoint replaces CP2's Settings placeholder.
2. **Library** row → the exercise library (CP10). This is the non-workout
   entry point CP2 item 5's relocation asserts, and it is owned here: without
   it, creating, editing or archiving an exercise would require starting a
   workout first, which is worse than the six-tab IA being retired, and it
   would strand CP10 item 3's `Create "<query>"` empty state behind an active
   session. It carries the `books` glyph CP2 retires from `RepFlowIcons.Nav`.
3. **Rest timer** group: auto-start after a logged set, vibrate on end,
   notification. Each toggle must actually gate its behaviour — a settings
   screen whose switches do nothing would be a worse outcome than not
   building it. **How each one gates, and what it is checked by**
   (revision 14):
   - **Auto-start.** `ActiveWorkoutViewModel.onRecordSet` starts rest after
     every successful set today (`ActiveWorkoutViewModel.kt:246`). It reads
     the preference and, when it is off, skips that `startRestTimer` call;
     nothing else changes. The ViewModel gains `SettingsRepository` as a
     dependency. **Off means no rest timer for logged sets at all**
     (revision 15): that call is the only place the app starts rest, and the
     prototype likewise starts `nRest` only in `nLogSet`, so there is no
     manual start to fall back on. The row therefore carries `4a`'s static
     subtitle `After every logged set` in both states, not `5c`'s off-state
     `Start it yourself from the workout` (`restTimerSub`), which promises a
     manual start that exists nowhere — deviation `D23`.
   - **Notification and vibration, gated independently; the Vibrate switch
     vibrates explicitly** (revision 15, the user's decision of 2026-09-30),
     **and the notification keeps today's sound** (revision 16, the user's
     later decision of 2026-09-30). Today's `rest_timer` channel is built
     with the three-argument constructor and never `enableVibration(true)`
     (`RestTimerExpiredReceiver.kt:48–56`), so it does not vibrate in normal
     ringer mode — the class KDoc's "default vibration" (`:16–17`) is wrong.
     It does play the default notification sound, the channel default.
     **No channel's vibration is relied on:**
     - **Vibration** is the handler's own one-shot call through the system
       vibrator (`VibratorManager.defaultVibrator` on API 31+, the `Vibrator`
       service below; `VIBRATE` is already declared,
       `AndroidManifest.xml:4–6`), made when Vibrate is on and never
       otherwise. It needs no channel, so it behaves the same on a fresh
       install and on an upgraded one. **It carries usage
       `USAGE_NOTIFICATION`** (revision 16): Android drops a background
       app's vibration unless it names a ringtone, notification or alarm
       usage (`Vibrator.java:449–450`), and the receiver exists for exactly
       the backgrounded, screen-off case. On API 33+ the call is
       `vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))`;
       on API 28–32 (minSdk is 28) it is the `AudioAttributes` overload,
       `vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())`.
       Notification usage means the system's own settings still apply —
       silent mode and the notification-vibration intensity — so in silent
       mode the buzz does not fire, like any notification's. The app does
       not override them.
     - **Notification** keeps posting on today's `rest_timer` channel,
       unchanged: same ID, `IMPORTANCE_HIGH`, the default notification
       sound, vibration disabled. No channel is created and none is
       deleted, so on an upgraded install any per-channel setting the user
       already changed survives. The row's subtitle is `Shows when rest
       ends`, not `nTimerRows`' `Shows on the lock screen with ±15s and
       Skip` (`RepFlow.dc.html:3931`): the notification has no actions
       (`RestTimerExpiredReceiver.kt:31–39`) and CP14 adds none — deviation
       `D24`.
     - **The four combinations**, from a pure presentation function over the
       two switches: notification on + vibrate on → the notification with
       its sound, plus the explicit buzz; notification on + vibrate off →
       the notification with its sound, no explicit buzz; notification off
       + vibrate on → the buzz only, no notification; both off → nothing.
     - **The switches are read when the alarm fires, not when it is
       scheduled** (revision 17, X2-I1). A rest can still be
       running when the user leaves the workout for Home and Settings, and
       the scheduled `PendingIntent` is retained
       (`RestTimerAlarmScheduler.kt:41–49`). `ActiveWorkoutRoute` therefore
       schedules the alarm for every running rest, whatever the switches,
       and puts no preference extras on the intent. The receiver becomes
       `@AndroidEntryPoint`, and its work moves to an injected
       `RestTimerExpiryHandler` (`presentation/workout/`). `onReceive` calls
       `goAsync()` and launches on `Dispatchers.IO` directly (revision 18):
       the `@IoDispatcher` qualifier lives in `infrastructure/di`, and
       `presentation` must stay free of `infrastructure` imports
       (`LayerBoundaryTest`). The handler
       makes one suspend read of the current values from the Room-backed
       `SettingsRepository` (item 7), then runs the notification half (the
       permission check first, as below) and the vibration half.
       `PendingResult.finish()` runs in a `finally`, with no broad `catch`.
     - **Known platform limit: vibrate ringer mode** (accepted by the user,
       2026-09-30). In vibrate ringer mode Android turns a channel's sound
       into a fallback vibration. So with notification on and Vibrate off
       the phone still buzzes, and with both on the fallback and the
       explicit buzz can both fire. Preventing it would mean a silent
       notification, which the user ruled out.
     - **Permission.** The `POST_NOTIFICATIONS` check stays first in the
       notification half and is unchanged: denied → no notification, today's
       silent no-op, so the open decision "Notification behavior when
       permission is denied" is not touched. Vibration needs no such
       permission and does not pass through that check: **with notification
       permission denied and Vibrate on, the phone still vibrates**, and
       with Vibrate off nothing happens. That differs from today, where a
       denied permission made the whole receiver a no-op; it follows from
       the switch being independent of the notification, and it adds no
       notification policy (no prompt, no in-app fallback). **CP14 rewrites
       the receiver's class KDoc** (`RestTimerExpiredReceiver.kt:13–21`):
       its "default vibration" and "otherwise this is a silent no-op" are
       both wrong after this checkpoint.
     - **The permission prompt follows the Notification switch** (revision
       19, X3-I2). Today `ActiveWorkoutRoute` requests `POST_NOTIFICATIONS`
       whenever a rest starts (`ActiveWorkoutRoute.kt:33–44`). CP14 makes
       that request only when Notification is on. The alarm is still
       scheduled for every rest, whatever the switches. The route reads the
       switch from `ActiveWorkoutViewModel`, which already gains
       `SettingsRepository` for auto-start, and decides through a pure
       presentation function over the switch, `SDK_INT` and the current
       grant. With Notification off, the user is never prompted. **No
       prompt before the switch has loaded** (revision 20, local round 19's
       O2): the ViewModel exposes Notification as a `StateFlow<Boolean?>`
       that is `null` until `SettingsRepository` first emits, never the
       stored default, and the function treats `null` as "do not prompt".
       The prompt runs in an internal composable in `ActiveWorkoutRoute.kt`,
       `RestTimerPermissionPromptEffect(restTimerEndAt, notificationEnabled,
       isPermissionGranted, requestPermission)`, keyed on the rest's end and
       the switch, so a switch that loads on after the session still prompts
       once. The route passes the real grant check and the launcher.
       Scheduling stays in the route's existing effect, keyed on the end
       alone.
     - **Visible change, named.** With the defaults, rest end now vibrates
       in normal ringer mode, where today it does not. The notification and
       its sound are unchanged.
   - **Defaults keep today's switch values** (item 7): auto-start on,
     vibrate on, notification on.
   - **Checks CP14 owes, on top of item 6's five:** two new
     `ActiveWorkoutViewModelTest` methods (auto-start on → rest starts after
     a successful set; off → it does not), which also takes the fixture edit
     for the new dependency; a JVM test of the pure function over all four
     switch combinations; a JVM test of the prompt function (Notification
     off or not yet loaded → no request, whatever the SDK and grant; on → a
     request only on API 33+ without the grant); a `createComposeRule()`
     test of `RestTimerPermissionPromptEffect` (revision 20, local round
     19's O1) with a running rest, a grant check returning false and a
     recording request: off → none, on → one on API 33+ and none below,
     not loaded → none until it loads on. The route's call into it is an
     untested pass-through. And an instrumented test of
     `RestTimerExpiryHandler` whose vibration goes through an injectable
     seam. The seam is the app-level `fun interface RestAlertVibrator`
     (`presentation/workout/`), which takes the usage as an `Int`. The handler
     passes `AudioAttributes.USAGE_NOTIFICATION` on API 28–32 and
     `VibrationAttributes.USAGE_NOTIFICATION` on API 33+; the two constants
     differ in value. The handler takes the permission result as an argument
     (revision 19, local round 18's O2): the receiver passes its existing real
     check (`RestTimerExpiredReceiver.kt:43–46`), so the module binds only the
     vibrator. Production binds the system vibrator in a
     Hilt `@Module` in `presentation/workout/`, never in `infrastructure/di`,
     which may not import `presentation` (`LayerBoundaryTest`). The production
     vibrator adapter, which maps the `Int` back into
     `VibrationAttributes.createForUsage` (33+) or
     `AudioAttributes.Builder().setUsage` (28–32), is an untested
     pass-through. The test uses a fake vibrator that records the usage each call carried.
     Per combination, and with permission granted and denied, it asserts
     whether the fake vibrator **fired** and, when it did, that the recorded
     usage is the constant for the device's `SDK_INT`; whether a notification was
     posted; that a posted notification's channel is `rest_timer` with
     `shouldVibrate() == false` and a non-null sound (the default
     notification URI); and that with a `rest_timer` channel pre-created, as
     on an upgraded install, the same outcomes hold and that channel still
     exists after the handler runs. **The same test changes each switch after
     scheduling and before expiry** (X2-I1). It sets the switches, calls
     `RestTimerAlarmScheduler.schedule` with a future end, and changes one
     switch through `SettingsRepository`. It then fires the handler as the
     alarm would and asserts that the outcome follows the new value. It
     covers each switch, on → off and off → on, and cancels the alarm
     afterwards. **The receiver's delivery is asserted too** (revision 19,
     X3-I3), because the handler test calls the handler directly and would
     pass if `onReceive` never delegated. A second instrumented test runs in
     the app's real Hilt graph, so the real receiver, handler and
     `SettingsRepository` are used. The instrumentation grants
     `POST_NOTIFICATIONS` (`UiAutomation.grantRuntimePermission`, no new
     dependency), only when `SDK_INT >= 33` (revision 20, local round 19's
     O3). It runs two cases. Each cancels any posted `NOTIFICATION_ID`
     first, because the handler test posts the same ID. Each schedules a
     future rest alarm, then flips Notification in the Settings screen
     (`createAndroidComposeRule<MainActivity>`) and waits until the switch,
     which renders from `SettingsRepository`'s flow, shows the new value.
     It gets the scheduled `PendingIntent` from
     `RestTimerAlarmScheduler.scheduledPendingIntent(context): PendingIntent?`
     (revision 20, X4-O1), an `internal`, `@VisibleForTesting` function that
     rebuilds the scheduler's intent with the same request code and
     `FLAG_NO_CREATE or FLAG_IMMUTABLE`. It asserts that it exists and sends
     it, which delivers the broadcast to the manifest-declared receiver as
     the alarm would. **Off at scheduling, on at delivery:** it waits, with
     a bound, for `NOTIFICATION_ID` on `rest_timer` among the app's active
     notifications. **On at scheduling, off at delivery** (revision 20,
     X4-I1): it waits the same bound and asserts that no `NOTIFICATION_ID`
     was posted. Today's receiver posts whenever permission is granted, so
     it fails the second case; a receiver that does nothing fails the
     first. Afterwards each case cancels the alarm and the notification and
     restores the switch. The vibrate-ringer fallback cannot be driven
     from an instrumented test; it is the documented limit above, not an
     owed assertion.
4. **During a workout** group: keep screen awake (the behaviour and the
   switch, per the split above), and confirm before finishing — **the gate
   over CP9 item 1's confirmation, which CP9 ships unconditionally**. This is
   the toggle item 3's rule bites hardest on, so CP14 owes the assertion that
   turning it off actually skips the confirmation and turning it on restores
   it. **The gate sits on CP9 item 1's single finish request, so it governs
   every entry point that item lists, Home's `Finish it` included, and the
   assertion drives both Home's `Finish it` and the board's `Finish`**
   (revision 12) — an assertion over the workout surface alone would pass
   with Home's path ungated. CP9 lands before CP14 (see the execution order above), so CP14 is the
   second half of the pair: it wires the gating clause and owns the test,
   because that is the first point at which both halves exist. The same rule
   puts CP6/CP8's `Why ›` route and its inward-path assertion on CP8 (CP8
   item 5).

   **Keep screen awake** (revision 14): while the switch is on and the
   workout surface (board or focus) is showing, that surface sets
   `keepScreenOn` on its view — the Compose-side equivalent of
   `FLAG_KEEP_SCREEN_ON` — and clears it when the surface leaves
   composition. Off sets nothing, which is today's behaviour, and off is the
   default (item 7). **CP14 owes an instrumented check of both states**:
   on → the workout surface's view has `keepScreenOn == true`; off →
   `false`.
5. **Data** group: export backup, restore from a file (with its existing
   destructive confirmation), and `Erase all data` behind the design's own
   `Irreversible` warning card and a typed confirmation.
6. **`Erase all data` is a new destructive capability, so its blast radius is
   stated here rather than discovered in a diff.** Nothing like it exists
   today: the only mass delete in the tree is `database.clearAllTables()`
   inside `LocalBackupRepository.kt:50`, a private step of the **restore**
   path (`RestoreBackup` → `replaceAll`), not a user-facing action. It is
   built rather than deferred because — unlike supersets, swap or notes — it
   needs no new domain concept; it is the plan's own domain-backing test
   applied honestly, so it gets the same discipline as everything else that
   passes that test:
   - **Scope.** Every user *training-data* table — which today is **all ten
     entity types** registered on `RepFlowDatabase`: exercises, plans, plan
     versions, planned exercises, workout sessions, workout exercises,
     workout sets, recovery entries, futsal sessions, and progression
     recommendations. (That last one is a single
     `ProgressionRecommendationEntity` whose `override_result`/`override_at`
     columns carry the manual override — one table, not two, so the erase
     implements against ten entity types rather than eleven.) **Not** CP14's
     own preferences table — erasing it would contradict this checkpoint's
     own rationale that preferences are device settings rather than user
     training data (see "Backup compatibility impact"), so the user's
     rest-timer and workout preferences survive an erase, and by item 9 they
     survive a restore too. **Not** exported backup files already written to
     disk: those are the user's own files in their own storage, and an in-app
     action must not reach outside the database to delete them. The
     confirmation copy says both.
   - **Owner, and the interface it lives on.** A new application-layer
     `EraseAllData` use case, beside the existing `ExportBackup` and
     `RestoreBackup`, over an explicit repository method that clears exactly
     the tables named above in one Room transaction. It does **not** reuse
     `clearAllTables()`, which would take the preferences table with it.
     **That same method is what item 9 hands to
     `LocalBackupRepository.replaceAll`**, so the training-data-scoped clear
     is defined once and both destructive paths share it rather than drifting
     apart.

     **Which interface owns it is named here rather than settled in a diff**,
     because the two candidates are not equivalent. It is **a new
     application-layer port — `TrainingDataRepository`, with a single
     `suspend fun clearTrainingData()`** — implemented by a new class in
     `data/` over `RepFlowDatabase`, and injected into both `EraseAllData`
     and `LocalBackupRepository`. It is **not** a method on the existing
     `BackupRepository` port (`application/backup/BackupRepository.kt`):
     that would make erasing training data depend on the backup port to do
     something that has nothing to do with backup, and would leave the
     milestone's one general-purpose destructive primitive named after the
     narrower of its two callers. The cost is the one the rejected option
     avoids — `LocalBackupRepository` gains a second constructor
     collaborator beside `database` — and it is the cheaper of the two.

     **Nesting is deliberate and is what item 9's retargeted assertion
     proves.** `replaceAll` calls `clearTrainingData()` from inside the
     `withTransaction` block it already has, and Room's `withTransaction` is
     re-entrant — an inner call joins the outer transaction rather than
     committing its own. That is the same nested-transaction question
     `LocalBackupRepositoryAtomicityTest` was written to answer for
     `clearAllTables()`, asked of a different call, which is why item 9 owes
     the answer rather than assuming it.
   - **Active session.** RepFlow enforces a single active session, and an
     active session is training data. The erase therefore **discards it as
     part of the same transaction** rather than refusing — refusing would
     leave a user who wants a clean slate having to find and discard the
     session first — and the typed confirmation names that consequence
     explicitly. Screens observing the session fall to their existing empty
     state when the flow re-emits.
   - **Test.** An instrumented/JVM test that erase empties every table in
     scope, **leaves the preferences row intact**, and discards an active
     session. It is one of **five** persistence-and-gate tests CP14 owes (items 3 and 4 add their switch checks on top): `MIGRATION_7_8`, the
     pre-milestone-backup restore (which asserts the preference outcome as
     well as the training data, see "Backup compatibility impact"), this
     erase test, item 9's retargeted scoped-clear rollback assertion, and
     item 4's confirm-before-finishing gating assertion.
7. Persistence: a Room-backed preferences table, reached through a new
   `SettingsRepository`, consistent with ADR-0002's "Room is the offline
   source of truth" rather than introducing a second persistence mechanism
   and a new dependency. This is a **schema change** — see below.
   **`MIGRATION_7_8`'s seeded row carries defaults that preserve today's
   behaviour** (revision 14): auto-start on, vibrate on, notification on
   (item 3 names the one visible change: vibration in normal ringer mode),
   confirm before finishing on (CP9's unconditional behaviour), and keep
   screen awake **off**. The design seeds that last one on
   (`setAwake: true` in the prototype's component state), which is
   deviation `D21`.
8. Deviation: no `Units` group (kg/lb excluded).
9. **The restore path must stop using `clearAllTables()`, and CP14 owns that
   change.** `LocalBackupRepository.replaceAll` opens its transaction with
   `database.clearAllTables()` (`LocalBackupRepository.kt:46–50`), which
   deletes every row of every table registered on `@Database(entities = …)` —
   and item 7 puts the preferences table in exactly that list. `replaceAll`
   then reinserts only the ten training-data collections the snapshot
   carries, and preferences are deliberately excluded from `BackupSnapshot`,
   so nothing puts them back.

   With the shape "Migration and schema impact" commits to, the consequence
   is worse than "settings reset". That section specifies **a single row
   pinned at a fixed primary key**, seeded by `MIGRATION_7_8`'s plain
   `CREATE TABLE` plus the default row. After a restore that row does not
   exist and no code path recreates it, so every preference read hits an
   absent pinned row — and item 3's requirement that "each toggle must
   actually gate its behaviour" would be reading from nothing.

   **Resolution: `replaceAll` clears exactly the training-data tables**,
   through the same `TrainingDataRepository.clearTrainingData()` item 6's
   `EraseAllData` uses, inside the `withTransaction` block it already has.
   Restore stays all-or-nothing over training data — and **that is a
   narrowing of the stated invariant, not a restatement of it**.
   `LocalBackupRepository`'s class KDoc (`LocalBackupRepository.kt:25–31`)
   today says `replaceAll` "wipes and rewrites **every table** in one
   `withTransaction` call - all-or-nothing, per the milestone reference's
   'restore replaces all local data atomically' invariant". Item 9 changes
   what "all local data" means, deliberately — that is the whole fix — so
   **CP14 rewrites that KDoc to the training-data scope** rather than citing
   it as though it already read that way. The preferences row is left
   untouched, which is what makes "a restored backup applies the receiving
   device's own settings" true rather than aspirational.

   **`LocalBackupRepositoryAtomicityTest` is a file this item changes, and it
   is the one rewrite in this milestone that no failing build would force.**
   `app/src/androidTest/kotlin/com/repflow/app/data/backup/LocalBackupRepositoryAtomicityTest.kt`
   exists precisely because of the call item 9 removes: its KDoc (`:24–35`)
   says the atomicity invariant "depends on `clearAllTables` behaving
   correctly when called from *inside* an outer `withTransaction` block …
   and is exactly what this test confirms", and
   `replaceAll_rollsBackClearAllTablesWhenAMidTransactionInsertFails`
   (`:149`) reaches a deliberate mid-transaction primary-key conflict to
   assert `"clearAllTables() must roll back with the rest of the transaction
   on failure"` (`:186–190`). Unlike CP2's
   `everyTopLevelDestinationGetsItsOwnGlyph`, this is neither a compile
   failure nor an assertion failure: all four tests in the file assert data
   outcomes, so **every one of them stays green while its subject silently
   disappears**. So CP14:
   - retargets the file's KDoc and that test's name and assertion message to
     the scoped clear, keeping the mid-transaction-conflict mechanism as it
     is (the fourth test,
     `replaceAll_rollsBackOnADistinctIdSameNameKeyConflict`, needs no change
     — its message names the conflict, not the clear);
   - **owes the assertion that the scoped clear rolls back with the
     transaction on failure.** This is the existing third test retargeted,
     not a new one, and it is what keeps the property proven for the new
     mechanism rather than assumed of it. A repository method issuing its own
     `DELETE`s inside an outer transaction will almost certainly roll back
     correctly; "almost certainly" is the standard this file was created to
     replace, and it is the same nested-transaction question in a new place.

   `LocalBackupRepositoryAtomicityTest` is **one of CP14's two entries** in
   the milestone-wide roll-up (Verification strategy) — the other,
   `ActiveWorkoutViewModelTest`, is item 3's auto-start fixture edit, added
   in revision 14 — and the first of the three whose
   staleness the suite would not report at all. Revisions 7 and 8 called it the "seventh and
   last"; both halves of that phrase were an artefact of counting from the
   three mechanisms the plan had enumerated at the time, and neither survives
   revision 9's per-checkpoint roll-up. What is unchanged is why this file is
   the sharpest instance: the other two silent passes are `assertDoesNotExist`
   assertions that go vacuous, while this one keeps asserting a real outcome
   through a call site that no longer exists.

   The two alternatives are rejected on the record. **Re-seeding the default
   row after the clear** works, but it silently resets preferences the user
   set on the receiving device, contradicting the same rationale it was meant
   to preserve. **Accepting that a restore resets preferences to defaults**
   would force the backup-exclusion rationale to be rewritten into something
   the design does not want. Keeping `clearAllTables()` is the weakest option
   of all, because it is indiscriminate by construction: the next non-training
   table added to this database inherits the same defect with no new code and
   no failing test.

   **Sequencing.** This is a change to an existing file in the same
   checkpoint that creates the table making it necessary. Nothing before CP14
   is affected: until item 7 lands, `clearAllTables()` and "clear exactly the
   training-data tables" name the same set.

### CP15 — Progress tab

`5b`. Entirely new, entirely derived from existing data.

1. Exercise chips → metric chips (`Top set`, `Est. 1RM`, `Volume`) → a card
   with the current value, the delta since the window start, a bar chart over
   the last N sessions, and the best-in-window line.
2. New pure-Kotlin estimated-1RM computation in `domain/progression/`, with
   its formula named and unit-tested; no Android import.
3. New application-layer read model over existing workout history.
4. **Invalidated sessions are excluded**, and the design's own note saying so
   is rendered. A test pins that invalidating a session removes its point.
5. Chart drawn with Compose `Canvas`; no new dependency.
6. **Metric availability follows the exercise's tracking type**
   (revision 14). `ExerciseTrackingType` has three values
   (`ExerciseTrackingType.kt:14–19`), and `WorkoutSet` forbids a load on
   `DURATION` and on any type without `supportsLoad`
   (`WorkoutSet.kt:91–101`). Only working sets (`isWarmup == false`) count.
   - `WEIGHT_AND_REPS`: all three. `Top set` = the heaviest load among
     working sets with a load; `Est. 1RM` = the best per-set estimate from
     item 2 over working sets with a load; `Volume` = Σ load × reps over
     working sets with a load. A session with no loaded working set
     contributes no point to any of the three.
   - `REPS_ONLY`: `Top set` only, as the most reps in one working set.
   - `DURATION`: `Top set` only, as the longest single working set in
     seconds.
   For `REPS_ONLY` and `DURATION`, the `Est. 1RM` and `Volume` chips are
   **not rendered**, and the card carries one line saying they need a
   recorded load — deviation `D22`. **This is distinct from item 7's empty
   state**: an unsupported metric is never offered however many sessions
   exist, while an offered metric with too few points shows the empty
   state.
7. Empty state for an *offered* metric with fewer than two data points.
8. **Test:** a JVM test of item 3's read model over all three tracking
   types — the offered metric set per type; each metric's value; a
   `REPS_ONLY` and a `DURATION` exercise with many sessions still offering
   `Top set` only (unsupported, not empty); a `WEIGHT_AND_REPS` exercise with
   one session showing the empty state; and a session whose working sets
   carry no load contributing no point.

### CP16 — Verification, side-by-side validation, and decision updates

1. Full local gate, every task force-executed (`--rerun-tasks`):
   `spotlessCheck`, `detekt`, `lintDebug`, `testDebugUnitTest`,
   `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`.
   With item 2's device run, this is what discharges F1's **acceptance
   criterion 5** — "the resulting implementation continues to pass the
   required automated and device verification after the structural visual
   changes".
2. **Side-by-side validation on the physical Samsung SM-S928B**
   (`RFCXA0RLSVT`, `sw384dp`), the authoritative device, with the 384 dp AVD
   as supporting evidence only. Every converted screen is captured next to its
   artboard, both themes. **This discharges three separately numbered F1
   obligations that sit on two different lists**, and they are cited by list
   name because F1 (`.ai-review/feedback/FUNCTIONAL_REVIEW.md`) carries
   **required remediation 1–6** (`:56`–`:79`) and **acceptance criteria 1–5**
   (`:101`–`:109`):
   - **required-remediation item 5** — "perform a new side-by-side functional
     validation after remediation, using the Claude Design screens and the
     running Android application together" (`:77`). This is the item's
     *primary* obligation, the side-by-side pass itself, and it is named
     first because a citation list that skipped it while naming the
     device-target requirement beside it would describe the item's
     conditions and not its subject;
   - **required-remediation item 6** — "validate the result on the physical
     Samsung SM-S928B as the primary phone target, with the matched 384 dp
     AVD as supporting evidence" (`:79`);
   - **acceptance criterion 4** — "side-by-side manual review of the design
     and the running application shows that the intended redesign has been
     substantially realized" (`:107`).

   There is no acceptance criterion 6. Required remediation 1–4 are
   discharged jointly by CP1's inventory and deviation register and by the
   conversion checkpoints as a body rather than by any single checkpoint, so
   they are not cited against one; item 3 of the deviation register (CP1)
   and CP16 item 3 are where they are answered.
3. The deviation register (CP1) is reviewed end to end and every entry is
   either still justified or fixed.
4. `docs/TECHNICAL_DECISIONS.md`: **`Navigation structure` moves from `Open
   (D-1 approved for M1 only)` to resolved**, citing this milestone and the
   four-destination IA. Every other open row is left exactly as it is —
   specifically `Whether completed workouts may be manually corrected` and
   `How substitutions affect progression history`, both of which this
   milestone deliberately steps around.
5. `docs/UX_FLOWS.md` is updated to describe the delivered IA and the
   board/focus split — it currently describes an intent the app did not
   implement, and after this milestone it should describe what exists.
   **Rule for rows the app still will not implement, stated so the rewrite
   cannot silently delete a standing requirement:** a row that is
   unimplemented both before and after this milestone is **kept in the
   document and marked as declared intent, with a pointer to the
   `IMPROVEMENT_ROADMAP.md` entry item 6 files for it**. **The rule is applied
   to the document row by row, not to the list below.** The list is what
   planning found; a further row discovered at CP16 gets the rule applied to
   it, and the list is never treated as exhaustive. Known today, each
   unimplemented, built by no checkpoint here, and covered by no non-goal:
   - `docs/UX_FLOWS.md:66` — "Reuse the previous set". Appears nowhere in
     `presentation/workout/` and in no CP8 item.
   - `docs/UX_FLOWS.md:41` and `:43` — "Recovery warning" and "Suggested
     adjustments", in the pre-start preview. `4a` draws that preview and no
     checkpoint converts it (deviation `D12`). `:43` is additionally the
     per-exercise decision list CP4 explicitly puts out of scope.
   - `docs/UX_FLOWS.md:110` — "Recovery or pain warnings" on the completion
     screen. No `recoveryWarning`/`painWarning` exists anywhere in
     `app/src/main/kotlin/`, and none of CP9's items adds one.

   `docs/UX_FLOWS.md:54` — "Technique notes" — is deliberately **not** on
   that list. The domain already backs it (`Exercise.instructions`, authored
   in the exercise editor), so by this plan's own domain-backing test it is
   built rather than deferred, and **CP8 item 11 owns it**.

   Only rows this milestone actually delivers are rewritten as description of
   what exists. Nothing is deleted: the document is the requirement, and
   losing one by rewriting the document around it is the failure mode this
   rule exists to prevent. ("Skip or substitute the exercise",
   `docs/UX_FLOWS.md:73`, is the plan's one acknowledged disagreement with
   this document and is already handled as a named non-goal and deviation
   `D2`, with its skip half as deviation `D14`.)

   **`docs/UX_FLOWS.md:98` — "Discard workout, with confirmation" — is
   reconciled with `D18`** (revision 15): the delivered action is `Abandon
   this workout`, with confirmation, which marks the session abandoned and
   keeps its logged sets, and the row is rewritten to that label and
   meaning so the documented contract and the shipped copy name the action
   the same way.
6. `docs/improvements/IMPROVEMENT_ROADMAP.md` gains the five deferred
   capabilities as named future milestones with scope and preconditions.
7. `docs/ACTIVE_MILESTONE.md` updated. ADR-0003's current-state snapshot
   (`docs/adr/0003-layered-modular-architecture.md:621–624`) lists three
   `infrastructure/di` Hilt modules, so it gains CP14's
   `presentation/workout/` module (revision 19, local round 18's O1).
8. **`ROLE_AUDIT.md` is re-run by its own method, and `RepFlowColor.kt`'s KDoc
   is corrected in the same edit.**
   `app/src/main/kotlin/com/repflow/app/presentation/designsystem/ROLE_AUDIT.md`
   is the parent milestone's `ColorScheme` role → consumer → value → contrast
   audit — the only `.md` file under `app/src/`, the accessibility *evidence*
   for the visual foundation, and the artefact `RepFlowColor.kt:184`,
   `RepFlowColor.kt:20`, `RepFlowBottomNavigationBar.kt:96` and
   `RepFlowButtons.kt:41` all point at. It is accurate at the base commit and
   **this milestone falsifies most of its `Consumers` column by design**:

   - `:116` — *"`surfaceContainer` | `NavigationBar` bar fill; incidentally 7
     `DropdownMenu`s"*. `grep -rn "DropdownMenu(" app/src/main/kotlin` returns
     exactly seven, and every one is on a screen this milestone converts:
     `ActiveWorkoutScreen.kt:144` and `:370` (CP7, CP8),
     `ExerciseListScreen.kt:397` (CP10), `HistoryScreen.kt:263` and `:299`
     (CP12), `TrainingPlanListScreen.kt:199` and
     `TrainingPlanEditorFormFields.kt:233` (CP11). CP3 retires the component;
     seven becomes zero.
   - `:126` and `:144–148` — the 23 `OutlinedTextField` text slots, of which
     seven are `ActiveWorkoutExerciseCard.kt`'s `NumericEntryField` (`:468`)
     invocations at `:420,427,438,448,590,598,605`. CP8 items 6 and 7 replace
     exactly those seven with steppers and scale rows.
   - `:140–143` — the five `ListItem` supporting lines
     (`HistoryScreen.kt:143`, `HistoryDetailScreen.kt:49`,
     `RecoveryHistoryScreen.kt:87` and `:104`,
     `TrainingPlanListScreen.kt:164`), on four screens CP11, CP12 and CP13
     convert into CP3's `RepFlowListRow`.
   - `:149–156` — the five stock `OutlinedButton` labels, one of which is
     `ActiveWorkoutScreen.kt:203`, the bare abandon button CP7 item 1 replaces.
   - `:118` — the one bare `Card(` (`TrainingPlanEditorFormFields.kt:123`),
     rebuilt by CP11 item 2.
   - `:110–111` — CP5's FAB container and glyph (`ExerciseListScreen.kt:188`,
     `:189`), which `6b`'s "one primary action per screen, pinned to a bottom
     bar" moves onto CP3's `RepFlowBottomActionBar` (CP10 item 6).

   **Nothing reports any of this, and the file's own guard is scoped to the
   wrong trigger for this milestone.** `ROLE_AUDIT.md:3` says *"Re-run this
   table before changing any value in `RepFlowColor.kt`"* — and this milestone
   changes **consumers**, not values, so the trigger never fires and the next
   maintainer who does change a value re-runs an inventory that is wrong in
   most rows. It is Markdown with no test behind it, which is the
   `RepFlowIcons.Nav` KDoc situation of CP2 item 2 on the parent's own
   accessibility artefact.

   The obligation is the audit's **own method re-run over the converted tree**
   (`:8–13`: every `androidx.compose.material3` import under
   `app/src/main/kotlin` cross-referenced against `material3-android-1.4.0`'s
   token files, plus the direct-read grep, under the counting basis at
   `:15–20`), not a patch of the lines this list happens to name — the list is
   what planning found, and CP16 applies the method rather than the list.
   Contrast ratios for CP3's new primitives are **not** recomputed here: CP3
   measures them when it builds them, and CP16 folds those measurements in.
   In the same edit, `RepFlowColor.kt:182–183`'s *"`surfaceContainerHighest`
   (the plan editor's row card)"* is corrected, for the reason CP2 item 2 gives
   for the `Nav` KDoc.

   **CP16 keeps `session_target: 2`, and this is the deliberate answer to the
   sizing question the item raises.** The recomputation half — new grounds,
   new ratios — belongs to CP3, which creates them; what lands here is
   inventory maintenance driven by two greps the audit itself documents, next
   to four document updates CP16 already owns. The registry is unchanged by
   revision 9 for that reason. If the re-run turns out to need a role
   *assignment* (the `RepFlowSheet` case CP3 flags), that is CP3's decision
   arriving here as a recorded value, not new analysis at CP16.

## Requirements traceability

Generated from
`docs/ai-workflow/requirements/repflow-redesign-visual-foundation-remediation-1-mapping.json`.

| id | requirement | checkpoints |
|---|---|---|
| REQ-1 | Adopt the design's four-tab IA, Settings entry, inward relocation of Exercises/Recovery/Backup — each reachable by its new inward path from the checkpoint that changes the destination set — nav suppression in workout mode; finalize the Navigation structure decision | CP2, CP5, CP10, CP13, CP14, CP16 |
| REQ-2 | Build the Home screen the design and `UX_FLOWS.md` both specify | CP5 |
| REQ-3 | Convert the active workout to board + focus mode with steppers, keypad, scale rows and a pinned bottom bar | CP7, CP8, CP9 |
| REQ-4 | Surface readiness and the progression recommendation on existing domain/application logic | CP4, CP6, CP9 |
| REQ-5 | Build the Progress tab from existing valid history | CP15 |
| REQ-6 | Build Settings with persisted preferences that really gate their behaviours, the exercise library reachable from it, and erase-all-data scoped to training data through its own application-layer use case | CP14 |
| REQ-7 | Convert the remaining existing screens to the design's compositions | CP10, CP11, CP12, CP13 |
| REQ-8 | Hold the conversion to the design: inventory, deviation register, shared primitives, side-by-side device validation | CP1, CP3, CP16 |

## Migration and schema impact

**One schema change, in CP14 only.** Everything else in this milestone is
presentation plus read-model work over existing tables.

- A single `settings` table holding the preference rows CP14 introduces.
  Room goes **7 → 8**, with an explicit `MIGRATION_7_8` and a migration test,
  per `CLAUDE.md`'s non-negotiable rule. No destructive migration; no change
  to any existing table, column or enum persistence.
- **Its shape is one row of typed columns, not a key/value row per
  preference**, because the two have different migration and
  enum-persistence consequences and the plan must pick one. The table holds a
  single row pinned at a fixed primary key, with one typed column per
  preference, so `MIGRATION_7_8` is a plain `CREATE TABLE` plus the default
  row, the DAO boundary stays typed, and no value has to be parsed back out
  of a `TEXT` blob. The cost — a later preference is an additive migration
  rather than a new row — is the discipline `CLAUDE.md` already requires for
  every schema change, so it is a feature of the choice rather than a price
  of it.
- Enum-valued preferences persist by **stable string**, never ordinal — which
  on this shape means a `TEXT` column with an explicit converter, never an
  `Int`.
- The alternative — a DataStore-backed preference store — was considered and
  rejected: it adds a runtime dependency for data that is small, structured
  and already has a perfectly good home, and it would put a second source of
  truth beside Room, against ADR-0002.

Nothing else in this plan touches persistence. In particular readiness
(CP4), the recommendation surface (CP6) and Progress (CP15) are all
**derivations over existing rows** and add no table, column or migration.

## Backup compatibility impact

The backup transfer schema (`BackupSnapshot`/`BackupJsonMapper`) is
versioned and must never serialize Room entities directly.

- CP14's preferences are **device settings, not user training data**, and are
  deliberately **excluded** from the backup snapshot. A restored backup
  applies the receiving device's own settings. This is a decision, not an
  oversight, and CP14 states it in the snapshot's own documentation.
- **That claim is only true because CP14 item 9 changes the restore path.**
  `LocalBackupRepository.replaceAll` clears the database with
  `clearAllTables()` today (`LocalBackupRepository.kt:46–50`), which would
  delete the preferences row the moment CP14 item 7 registers the table — and
  nothing would reinsert it, precisely because it is not in the snapshot.
  Item 9 replaces that call with the training-data-scoped clear `EraseAllData`
  already owes. Excluding preferences from the snapshot and clearing the whole
  database on restore are not compatible decisions; the plan makes them
  consistent here rather than discovering the conflict in a diff.
- **Restore's atomicity invariant is narrowed by that change, and item 9 owes
  the proof.** `LocalBackupRepository`'s class KDoc is rewritten from "every
  table" to the training-data scope, and
  `LocalBackupRepositoryAtomicityTest` — the instrumented test written
  specifically to prove `clearAllTables()` rolls back from inside the outer
  `withTransaction` — is retargeted to assert the same of the scoped clear.
  Nothing in the suite would report the gap otherwise; see CP14 item 9.
- Because no existing entity changes, the snapshot's existing shape and
  version are unchanged, and restoring a backup taken before this milestone
  keeps working. **CP14 owes a test that proves exactly that, and it asserts
  the preference outcome alongside the training data** — that a backup taken
  before this milestone restores its ten entity collections *and* leaves the
  preferences row present and unchanged. Without that assertion the
  regression is invisible: no test in the suite today observes a Room table
  that is not in the backup snapshot, because until CP14 there is no such
  table.

## Open decisions this plan touches

`CLAUDE.md`: agents must not silently finalize these.

| Decision | This plan's stance |
|---|---|
| **Navigation structure** — `Open (D-1 approved for M1 only)` | **Finalized by CP16**, on the user's explicit written authorization at the planning gate (quoted above), in favour of the design's four-destination IA. This is the only one closed. |
| Whether completed workouts may be manually corrected | **Left open.** It is why `3b`'s set-edit pencil is not built (CP12). |
| How substitutions affect progression history | **Left open.** It is one of two reasons exercise swap is deferred. |
| Final Room entity schema | **Not finalized.** CP14 adds one additive table under an explicit migration; it does not settle the schema question. |
| Exact progression formulas and thresholds | **Not finalized.** CP6 renders `ProgressionPolicyV1`'s existing output and changes no formula. |
| Notification behavior when permission is denied | **Not finalized**, but CP14's rest-timer notification toggle sits next to it — CP14 must keep today's behaviour and not invent a policy. The notification half keeps today's permission-check-first no-op; the Vibrate switch's explicit vibration needs no notification permission and still fires when it is denied (CP14 item 3). The permission prompt now follows the Notification switch (CP14 item 3); what happens after a denial is unchanged. |
| Backup file location and retention count | Untouched. |
| Exact initial Gradle module split | Untouched — no new module. |
| Whether active workouts may span calendar days | Untouched. |

**A new decision this plan records rather than assumes:** the readiness
score's weights, bands, pain gate and date rule (CP4 items 1–2). It is not
currently an open-decisions row. The user resolved it on 2026-09-30 by
adopting the design prototype's own engine (`RepFlow.dc.html:3142–3166`),
and CP16 records it as a new resolved row citing that source rather than
leaving it as folklore in a Kotlin file.

## Deviation register — seeded

CP1 owns this list and extends it. Every entry is a place the built app will
differ from the design, with its reason.

| # | Design shows | Built instead | Reason |
|---|---|---|---|
| D1 | Superset grouping on board, plan editor, focus, and the board row sheet's `Superset with the next exercise` | Not rendered at all | no domain backing |
| D2 | Swap sheet with same-muscle-group alternatives, and the board row sheet's `Swap for another exercise` | Not rendered | no domain backing; also blocked by an open decision |
| D3 | Per-exercise and session notes, and the board row sheet's `Add a note`/`Edit the note` | Not rendered | no domain backing |
| D4 | `Active plan` badge, `Day 1..4` tabs, "day 2 of 4" | Flat plan list; no day dimension | no domain backing |
| D5 | Settings `Units` kg/lb | Omitted group | no domain backing |
| D6 | Deload card and deload state | Not rendered | no domain backing |
| D7 | Set-edit pencil in workout detail (`3b`) | Read-only set rows | blocked by open decision |
| D8 | Library group filter; `Chest · Weight & reps` row meta | Tracking type · rest · plan usage | no domain backing |
| D9 | Home "This week — 2 of 4 sessions" | Omitted | needs a weekly target (plan days) |
| D10 | Pain as `none / niggle / sharp`, technique as `clean / grinder / form broke` (`4a`) | 0–5 scale rows | domain stores `pain: Int?` and `techniqueQuality: Int?` on a 0–5 scale; `6b`'s own primitive is "Scale row — 0–5", and artboard `1a` draws these as 0–5. Deliberate product call: keep the stored resolution. |
| D11 | RPE pills `6,7,8,9,10` (`4a`) | RPE scale row over the domain's `0.0..10.0` | domain range is wider than the design's shorthand |
| D12 | `4a`'s pre-start preview (planned exercises, expected duration, recovery warning, futsal context, suggested adjustments) | Not converted; CP5's start sheet is the only pre-start surface | deliberate product call: `docs/UX_FLOWS.md:37` makes the preview optional ("RepFlow **may** show"), and its two readiness-derived rows are the weakest part of it — "Suggested adjustments" (`:43`) is the per-exercise decision list CP4 explicitly excludes, and "Recovery warning" (`:41`) would restate CP5's readiness line one screen earlier. Both rows stay in `UX_FLOWS.md` as declared intent (CP16 item 5). |
| D13 | Board row sheet's `Do this later` (moves the exercise to the end) | Not rendered; with all six row-sheet options out, no row sheet and no `⋮` (CP7 item 7) | no domain backing: no reorder operation or use case |
| D14 | Board row sheet's `Skip for today` (a skipped status the finish sheet's unfinished list excludes) | Not rendered (CP7 item 7) | no domain backing: `WorkoutExercise` has no status |
| D15 | Board row sheet's `Remove from this workout` | Not rendered (CP7 item 7) | no domain backing: `WorkoutSession` has no removal operation, and one would need a rule for recorded sets |
| D16 | Home's `Finish it` opens the finish sheet over Home | Opens it over the workout board; dismissing returns to Home (CP9 item 1) | deliberate product call: one host derives the unfinished-work list and carries CP14's gate |
| D17 | Leave sheet's `Discard everything logged` acts at once (`nDiscardSession`) | Opens a destructive confirmation dialog first (CP7 item 1) | deliberate product call (user, 2026-09-30): abandoning is terminal and cannot be undone; `6b` keeps dialogs for exactly this, as CP5's Home abandon does |
| D18 | Leave sheet's `Discard everything logged`, which empties the session (`nDiscardSession`) | `Abandon this workout`, with an `Abandon this workout?` confirmation saying the session is marked abandoned and its logged sets stay stored (CP7 item 1; CP5's Home trash uses the same copy) | deliberate product call (user, 2026-09-30): keep today's `AbandonWorkoutSession` exactly — status `ABANDONED`, sets retained, nothing deleted, no new write path — and make the copy say so |
| D19 | Readiness band advice line (`RDY_BANDS[…].line`, e.g. "Repeat last session's loads instead of adding.") on Home and in the sheet | Band label and driver sentence only (CP4 item 4) | nothing in this milestone acts on the band; the per-exercise decisions are out of scope (CP4), so the advice would be false |
| D20 | Workout detail's `skipped` label for an exercise with no sets (`3b`, `nHistEx`) | `No sets logged` (CP12 item 2) | no domain backing: `WorkoutExercise` has no status, so a skip and an unattempted exercise are indistinguishable (`D14`) |
| D21 | `Keep screen awake in a workout` on by default (`setAwake: true`) | Off by default (CP14 item 7) | deliberate product call (user, 2026-09-30): defaults preserve today's behaviour, and today nothing keeps the screen on |
| D22 | `Top set` / `Est. 1RM` / `Volume` offered for every exercise (`5b`) | `REPS_ONLY` and `DURATION` exercises offer `Top set` only (reps / seconds), with a line saying the other two need a load (CP15 item 6) | no domain backing: those types record no load (`WorkoutSet.kt:91–101`), so a load-based metric does not exist for them |
| D23 | Auto-start row's off-state subtitle `Start it yourself from the workout` (`5c`, `restTimerSub`) | `4a`'s static `After every logged set` in both states; off means no rest timer for logged sets (CP14 item 3) | no domain backing: nothing starts rest manually, in the app or in the prototype |
| D24 | Notification row's subtitle `Shows on the lock screen with ±15s and Skip` (`nTimerRows`, `RepFlow.dc.html:3931`) | `Shows when rest ends` (CP14 item 3) | no domain backing: the rest-timer notification has no actions (`RestTimerExpiredReceiver.kt:31–39`) and CP14 adds none |

## Verification strategy

- Every checkpoint keeps the full local gate green and adds tests for what it
  introduces: JVM tests for derivations and wiring, instrumented tests for
  the converted surfaces.
- **An existing test is read before the thing it was written about is
  changed**, exactly as the parent milestone required for converted screens,
  and every node or mechanism it depends on either survives or the test is
  rewritten deliberately and named in the checkpoint.

  **The milestone-wide count is twenty files, and it is a roll-up of the
  checkpoints' own enumerations rather than a number this section derives.**
  Each conversion checkpoint records the grep that produced its list, the
  methods affected, the failure mode and the methods explicitly untouched —
  CP2 item 7, CP8's "Every method in `ActiveWorkoutScreenTest`" table, CP10
  item 6, CP11 item 5, CP12 item 6, CP13 item 6, CP14 items 3 and 9, and
  CP4 item 5. This section
  adds them up and nothing else:

  | Checkpoint | Files | Methods it changes |
  |---|---|---|
  | CP2 | 5 | `MainActivityNavHostSmokeTest` and `RepFlowIconsTest` rewritten; 2 of `RepFlowBottomNavigationBarTest`'s 5; the two `BackupRoute…` tests re-routed, not rewritten |
  | CP4 | 2 | `InMemoryRecoveryRepository` (JVM fixture, gains the port's new `observeForDate`); one new method in `RecoveryDaoTest` (4 today, instrumented) |
  | CP8 | 1 | 5 of `ActiveWorkoutScreenTest`'s 14 |
  | CP10 | 6 | 6 of `ExerciseListScreenTest`'s 15; 1 of `ExerciseEditorScreenTest`'s 13; 2 of `ExerciseListScreenWiringTest`'s 5 (JVM, compile failures, the row-height half kept); 0 of `ExerciseListViewModelTest`'s 12 (JVM; fixture edit for the new constructor dependency, plus one new); 0 of `ExerciseEditorViewModelTest`'s 16 (JVM; three new); `InMemoryTrainingPlanRepository` (JVM fixture, gains the new port query) |
  | CP11 | 2 | 4 of `TrainingPlanListScreenTest`'s 11 plus one new; one new in `TrainingPlanEditorScreenTest`, whose 3 row-action methods are a *constraint* on the conversion rather than a rewrite |
  | CP12 | 2 | 5 of `HistoryScreenTest`'s 9; 2 of `HistoryDetailScreenTest`'s 3, plus one new |
  | CP13 | 0 | the grep returns nothing; CP13 adds coverage rather than changing it |
  | CP14 | 2 | `LocalBackupRepositoryAtomicityTest` retargeted; 0 of `ActiveWorkoutViewModelTest`'s 14 (JVM; fixture edit for the new `SettingsRepository` dependency, plus two new) |

  **Revisions 1–6 put this at four, revision 8 at seven and revision 9 at
  thirteen; each was the count of the files a named mechanism — or, at
  revision 9, a named grep shape — reached, offered as the milestone's
  total.** Revision 8's seven came from three mechanisms — the destination
  set, `performTextInput` and `clearAllTables()` — and six of the thirteen sit
  on screens none of those three touches. Revision 9's own enumerations were
  in turn closed only on composable *invocations*, which cannot by
  construction find a JVM test pinning the same screen's non-composable
  wiring; `ExerciseListScreenWiringTest` (CP10) is the one instance this milestone
  has, and revision 10 adds it as the fourteenth file. Revision 10 then ran
  that JVM second pass on CP11, CP12 and CP13 but, on CP10, over only the one
  file round 9 named; revision 11 runs it over CP10's two packages and the
  fixture item 5 reaches, adding `ExerciseListViewModelTest`,
  `ExerciseEditorViewModelTest` and `InMemoryTrainingPlanRepository` as the
  fifteenth to seventeenth. The lesson each time has been the same one and it
  is now structural: the count belongs to whoever ran the grep, the roll-up
  may only add, and a second pass is run over the layer it names on every
  checkpoint it applies to. Revision 12 runs it over `presentation/workout/`
  for CP5, CP7, CP8 and CP9 (after CP8's `ActiveWorkoutScreenTest` table); it
  edits neither file, so the count stays seventeen. Revision 14 adds three,
  each forced by a manual-external finding rather than found by a pass:
  CP4's `InMemoryRecoveryRepository` and `RecoveryDaoTest` (the port's new
  observable query, X-I4) and CP14's `ActiveWorkoutViewModelTest` (the
  auto-start gate's new dependency, X-I5). The count is twenty.

  **They still fail in four ways, but the fourth is no longer a single
  case.** The mode that reports nothing at all — a green test whose subject
  has disappeared — now has **three** instances: CP14's
  `LocalBackupRepositoryAtomicityTest` (named since revision 1), CP8's
  `anAdHocExerciseShowsNoPlannedTargetSummary` and CP10's
  `loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad`. The last
  two are both `assertDoesNotExist` against a string the conversion removes for
  every case, so they pass *more* easily after the change than before. That is
  the mode the local gate cannot report at all, and it is now the most common
  one after plain assertion failure. **The count stays at three because of a
  decision, not by default** (revision 11): `rowAndFabSizesAreTheDesignsOwn`'s
  row-height half is carried into its rewrite and `ExerciseRowMinHeight` stays
  the converted row's `heightIn` (CP10 item 2), so no green test is left
  pinning a dead row constant; revision 12 makes the same decision for
  `ControlRowMinHeight` on the workout surface (CP8's JVM second pass).
  - CP2's `MainActivityNavHostSmokeTest` — failing assertions; it asserts the
    six-tab IA being retired.
  - CP2's `RepFlowIconsTest` — `everyTopLevelDestinationGetsItsOwnGlyph` is a
    *compile* failure rather than an assertion failure, so it cannot be
    missed but also cannot be deferred. (`RepFlowIcons.Nav`'s own KDoc goes
    stale in the same edit and reports nothing at all — CP2 item 2.)
  - CP2's `RepFlowBottomNavigationBarTest` — **both modes in one file**:
    `everyTopLevelDestinationCarriesItsOwnNavGlyph` is a compile failure for
    the same reason `RepFlowIconsTest`'s is, and
    `topLevelDestinationsKeepTheirRoutesAndOrder` is a failing assertion that
    retires a deliberate route-order pin, under a KDoc that goes stale
    silently. Its three colour tests are untouched (CP2 item 7).
  - CP2's `BackupRouteSafCancellationTest` and
    `BackupRouteUnreadableRestoreFileTest` — a **runtime instrumented
    failure**: `performClick()` on the retired `BACKUP` tab's content
    description finds no matching node. Their subjects survive in full; only
    the opening navigation is re-routed through item 4's gear (CP2 item 7).
    This is the mode the local gate reports slowest, since it needs a device
    or emulator run.
  - CP8's `ActiveWorkoutScreenTest` — **three modes in one file.** Two failing
    assertions by construction from the affordance swap,
    `tappingAddSetClearsTheEntryFields` (a stepper cannot satisfy
    `performTextInput`) and
    `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing`
    (neither can a scale row); two more from item 3's chip removal,
    `aPlannedExerciseShowsWarmupAndWorkingProgress` and
    `everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth`, the second
    of which is a parent-milestone clipping guard that item 3 retargets; and
    one silent pass, `anAdHocExerciseShowsNoPlannedTargetSummary`. The other
    nine methods and the block comment at `:217–226` are accounted for in
    CP8's own table.
  - CP10's `ExerciseListScreenTest` and `ExerciseEditorScreenTest`,
    CP11's `TrainingPlanListScreenTest` and `TrainingPlanEditorScreenTest`,
    CP12's `HistoryScreenTest` and `HistoryDetailScreenTest` — **the six the
    plan had never grepped for before revision 9.** All six are
    `ComponentActivity`-rooted, which is why CP2's own enumeration correctly
    excluded them and why nothing else had looked. Their modes are runtime
    no-matching-node (the row menus CP3 retires), failing assertion (rebuilt
    rows and empty states), and one further silent pass
    (`loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad`). CP12
    also carries the one that is a *capability* question rather than a test
    question: four of `HistoryScreenTest`'s nine drive an invalidate action the
    design's row composition has nowhere to put (CP12 item 6).
  - CP10's `ExerciseListScreenWiringTest` — **the seventh, and new in
    revision 10.** Not `ComponentActivity`-rooted at all — it is JVM, so no
    grep closed on a composable invocation could ever have found it, which is
    the blind spot the revision-9 enumerations shared and revision 10 names.
    `rowAndFabSizesAreTheDesignsOwn` and `everyTapTargetClearsTheMinimum` are
    **compile** failures once `ExerciseFabSize` is retired with the FAB
    (CP10 item 6), the same mode `RepFlowIconsTest` and
    `RepFlowBottomNavigationBarTest` already carry; the first keeps its
    row-height assertion through the rewrite (revision 11), and its other
    three methods survive unchanged.
  - CP10's `ExerciseListViewModelTest`, `ExerciseEditorViewModelTest` and
    `InMemoryTrainingPlanRepository` — **new in revision 11, all JVM.** The
    first and third are **compile** failures at a shared fixture (item 5's
    fourth constructor dependency; its added port query), repaired without
    rewriting a method; the second changes only by the three tests CP10 adds.
    All twenty-eight existing methods across the two ViewModel tests survive,
    each because of a stated decision in CP10 items 3–5, so a change to any of
    those decisions during implementation is a change to this plan.
  - CP14's `LocalBackupRepositoryAtomicityTest` — **no failure of any kind.**
    Item 9 removes the `clearAllTables()` call the whole file exists to
    verify, and all four of its tests assert data outcomes, so the suite
    stays green while the file's subject disappears. Revision 8 called this
    "the one most worth naming in advance"; revision 9 keeps the naming and
    drops the "one" — it is the first of three, and it remains the clearest,
    since the other two are `assertDoesNotExist` assertions whose vacuous pass
    is easier to argue about than a deleted call site.
- `connectedDebugAndroidTest` runs on the physical SM-S928B where it is
  attached; an emulator-only run is recorded as such and never reported as
  device coverage.
- CP16's side-by-side pass is the acceptance evidence for F1.

## Areas the reviewer should specifically challenge

- **Whether 16 checkpoints in one work item is the right shape**, versus
  splitting the conversion into a sequenced set of smaller milestones (for
  example: IA + Home; workout board/focus; the remaining screens; Progress +
  Settings). The user asked for a single design-convergence milestone, and
  the registry's dependency graph does decompose cleanly — but this is a
  large item and the reviewer should say so if it should be split.
- **CP4's readiness model** — since revision 14, the design prototype's
  own engine, numbers and all (CP4 item 1, `RepFlow.dc.html:3142–3166`),
  adopted by the user on 2026-09-30 rather than proposed by the plan. It is
  still not a clinically or empirically grounded model. The reviewer should
  check that CP4 transcribes the prototype faithfully and that leaving out
  the band advice line (`D19`) is right.
- **Whether retiring `Exercises` and `Recovery` as top-level destinations is
  acceptable**, given both are frequently used today and the design reaches
  them only inward. This is the most user-visible cost of adopting the
  design's IA.
- **Workout mode's back-gesture interception (CP7 item 1; the nav
  suppression half is CP2 item 6 and is free)** — it is what `6b` states,
  but intercepting system back is a real platform behaviour change and
  deserves explicit sign-off. CP7 is also where today's unconfirmed
  `Abandon` button (`ActiveWorkoutScreen.kt:203–205`) is replaced by the
  design's leave sheet — leave running, abandon behind a destructive
  confirmation (`D17`) with copy that says the sets are kept (`D18`), keep
  training — which is a second behaviour change on the same surface.
- **CP8's two `ActiveWorkoutScreenTest` rewrites** —
  `tappingAddSetClearsTheEntryFields` (the load field becomes a stepper) and
  `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing`
  (RPE and Pain become scale rows). The plan judges both a sanctioned
  consequence of an authorized affordance change rather than a weakened
  invariant. The reviewer should confirm that reading — and, more broadly,
  that the **twenty** test files Verification strategy now rolls up are
  the right set to knowingly change. Revisions 1 through 7 offered that list
  as **four**, revision 8 as **seven**, revision 9 as **thirteen** and
  revision 10 as **fourteen**, each time with the same confidence and each
  time as the count of the files one enumerated mechanism — or, at revisions
  9 and 10, one enumeration shape — reached. The number has now moved four
  times, each time because the net changed and not because someone
  recounted; **check the per-checkpoint enumerations and the greps that
  produced them, not the total**, check what each grep can and cannot see,
  and check that each second pass covers the layer it names on every
  checkpoint rather than the one file a finding cited — the part revision 10
  got wrong on CP10.
- **CP10 items 3 and 5's product decisions, made in revision 11** so the
  ViewModel test enumeration could be closed: the create route's prefill never
  overrides a restored draft, and an untouched prefill does not count as a
  change on back; `in N plans` counts non-archived plans only. A reviewer who
  prefers the other answer to either should say so, and CP10 item 6's
  ViewModel entries move with it.
- **CP12 item 6's invalidate-action question**, which is the one place where
  reading the suite surfaced a possible lost capability rather than a test to
  rewrite: four of `HistoryScreenTest`'s nine methods drive a
  `history_session_invalidate_action` that lives on the history row today, and
  the design's row is name, meta and caret. The plan's answer is that CP12
  states the trigger's new home; the reviewer should confirm that a row sheet
  (or the detail screen) is an acceptable home for it rather than a
  regression in reach for a destructive-but-recoverable action.
- **Whether CP16 is the right owner for the `ROLE_AUDIT.md` re-run, and
  whether `session_target: 2` still covers it** (CP16 item 8). The plan splits
  the work — CP3 measures its own new primitives' contrast floors, CP16
  re-runs the consumer inventory — specifically so the sizing does not move,
  and records that as a decision rather than an omission. A reviewer who
  thinks the re-run is a checkpoint's worth of work on its own should say so.
- **CP14's schema change** — whether a Room table is the right home for
  device preferences, and whether excluding them from backup is correct.
- **D10 and D11** — keeping the domain's 0–5 pain/technique resolution and
  0–10 RPE rather than adopting the newest artboard's word chips. This
  favours the stored model over the latest drawing, and the reviewer may
  prefer the reverse (which would be a domain change, and therefore out of
  this milestone's scope).
- **Whether the deviation register is a sufficient answer to F1's acceptance
  criterion 3**, or whether each deferred capability needs its own explicit
  user sign-off before this milestone can be accepted.

## Plan review dispositions

### Round 1 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (2 Blocking, 5 Important, 3 Optional)

Every finding was validated against the tree at `7d8ae1b` before being
applied; **all ten hold and all ten are accepted**. There are no rejections
to record for this round.

| # | Finding | Disposition |
|---|---|---|
| B1 | CP2 moves the start destination to `HOME` and retires the `WORKOUT` tab before CP5/CP14/CP15 exist, so its own acceptance test cannot pass and an active session is unreachable for three checkpoints | **Accepted.** CP2 item 4 now ships named placeholder scaffolds at `HOME`, `SETTINGS` and `PROGRESS`, each naming the checkpoint that replaces it; the Home placeholder carries today's resume/start affordances so an active session stays reachable from CP2 through CP5. The dependency graph is unchanged — placeholders, not resequencing. |
| B2 | `Exercises` is relocated "to Settings" but no checkpoint builds that entry point, leaving the library reachable only from inside an active workout | **Accepted.** CP14 item 2 owns a `Library` row; CP2 item 5 points at it and CP2's placeholder Settings carries it from the moment the relocation lands; CP2 item 7 asserts each relocated destination's inward path, not just the tab set. |
| I1 | CP6's "no UI whatsoever" premise is false — `RecommendationRow` and its override buttons exist at `ActiveWorkoutScreen.kt:392–415`, inside exactly the menu CP7 converts | **Accepted.** CP6's premise is corrected with the three call sites; CP7 item 6 carries the row and its override buttons into the picker sheet unchanged until CP6 replaces them, so the capability is continuous in either checkpoint order and no re-ordering is needed. |
| I2 | The fill-on-select treatment needs an icon *pair* per destination, which `TopLevelDestination`'s single `icon` field cannot carry; and it is six new drawables, not two | **Accepted.** CP2 item 2 now states the `selectedIcon` field, the `RepFlowBottomNavigationBar` change, the eight-drawable/six-new arithmetic, and the `RepFlowIconsTest` consequence. Item 1 also states where the four retired nav glyphs move to, which is what keeps the catalogue-equals-bundled-set assertion green. |
| I3 | `ProgressionResult.RecoveryAdjustment` is a fifth outcome the plan never places, and the screen CP6 replaces already renders it | **Accepted.** CP6 item 3 renders all five cases and names the regression risk; item 4 states that `RecoveryAdjustment` is what CP6's `depends_on: CP4` is for. |
| I4 | CP1's deviation register lands under `docs/milestones/`, an implementation-stage *excluded* prefix, so post-approval edits to the artefact F1 is graded on would stale no gate | **Accepted.** `docs/milestones/repflow-redesign-visual-foundation-remediation-1-inventory.md` is moved into `implementation_stage.protected_paths` in this item's artifacts declaration, using the escape hatch that declaration's own text provides. Done in this revision, while `technical_approval` is still `null` and the repair is free. |
| I5 | `Erase all data` is a new destructive capability with no stated scope, no application-layer owner, no active-session behaviour and no test | **Accepted, and built rather than deferred** — unlike the register's other entries it needs no new domain concept, so the plan's own domain-backing test says build it. CP14 item 6 states the table scope (training data; **not** preferences, **not** exported files on disk), the `EraseAllData` use case that owns it, that it does not reuse `clearAllTables()`, that an active session is discarded in the same transaction with the confirmation saying so, and the test it owes. |
| O1 | "three of the four" retired nav glyphs should be "all four" | **Accepted.** CP2 item 1, with the `docs/ACTIVE_MILESTONE.md:1078–1088` citation and the observation that the two surviving tabs are exactly the two the parent recorded as design-confirmed. |
| O2 | CP6's `depends_on: CP4` is unexplained | **Accepted.** CP6 item 4 states the link (`RecoveryAdjustment`) and that nothing else in CP6 consumes readiness. The dependency is kept, now justified. |
| O3 | CP16's `UX_FLOWS.md` rewrite could silently delete requirements the app never implemented | **Accepted.** CP16 item 5 states the rule: unimplemented rows are kept and marked as declared intent with an `IMPROVEMENT_ROADMAP.md` pointer, never deleted. "Reuse the previous set" (`:66`) is named as the one such row not already covered by a non-goal. |

Two further round-1 observations, both adopted:

- **CP3 is the checkpoint whose under-scoping would reproduce F1** — seven
  new primitives and three reworked, consumed by every one of CP5–CP15, yet
  scoped like a single-screen checkpoint. Its `session_target` moves from 2
  to **3**, and CP3 now carries an explicit rule that a primitive discovered
  missing mid-milestone goes back into `designsystem/components/` rather than
  being defined locally.
- **The preferences table's shape was unnamed** ("a single `settings` (or
  equivalently named) table"). "Migration and schema impact" now commits to
  one row of typed columns over a key/value row-per-preference, with the
  reasoning and the enum-as-`TEXT` consequence.

**Not challenged, and recorded as such:** the review explicitly declined to
contest the 16-checkpoint shape, CP4's readiness model being surfaced as a
proposal rather than smuggled in, D10/D11's preference for the stored 0–5 /
0–10 resolution over the newest artboard's word chips, and CP8's rewrite of
`tappingAddSetClearsTheEntryFields`. Those four remain open questions for
`MANUAL_EXTERNAL_PLAN_REVIEW` — a local reviewer declining to spend a round
on them is not the user's sign-off, and the readiness weights in particular
are still a proposal awaiting acceptance or replacement.

### Round 2 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (1 Blocking, 2 Important, 3 Optional)

Round 2 confirmed all ten of round 1's applications against the tree rather
than against the disposition table above, and raised six new findings — all in
material revision 2 introduced or touched. Every one was re-validated against
the tree at `7d8ae1b` before being applied; **all six hold and all six are
accepted**. There are no rejections to record for this round either.

| # | Finding | Disposition |
|---|---|---|
| B3 | CP14 claims preferences "survive a restore", but `LocalBackupRepository.replaceAll` calls `clearAllTables()`, which deletes every registered table — so the pinned preferences row would be absent after every restore, with no code path recreating it | **Accepted, and the resolution is stated rather than the claim dropped.** New CP14 item 9 makes `replaceAll` clear exactly the training-data tables, through the same explicit repository method item 6's `EraseAllData` already owes, so the training-data-scoped clear is defined once and shared by both destructive paths. The two alternatives (re-seed after the clear; accept a reset to defaults) are rejected on the record with reasons, and keeping `clearAllTables()` is rejected as indiscriminate by construction. "Backup compatibility impact" now states that the exclusion rationale *depends on* item 9 rather than asserting the outcome, and the pre-milestone-backup restore test asserts the preference outcome alongside the training data. |
| I6 | CP2 does not count `RepFlowIconsTest` as a file it changes, and the mechanism it credits for keeping that test green is not the one that keeps it green | **Accepted.** CP2 item 2 now states that `Nav` was never in `catalogue()` (`RepFlowIconsTest.kt:20–23`; the proof is that `Nav.exercises` aliases `books` while `everyGlyphNameResolvesToADistinctDrawable` is green), that six new top-level entries are what keep `catalogueCoversExactlyTheBundledIconSet` green, and that three of the file's five tests are affected — `everyTopLevelDestinationGetsItsOwnGlyph` as a **compile** failure, the 23-name literal as an equality needing a deliberate 29-name rewrite, and `designConfirmedNavGlyphsAreTheOnesTheDesignNames` needing the regular-weight field. Item 1's false green-keeping claim is removed. CP2 item 7's "one test file" and Verification strategy's "two known cases" both become **three**. |
| I7 | CP2 item 4's Home placeholder is specified as carrying *only* resume/start, which excludes the recovery entry point items 5 and 7 both require | **Accepted.** Item 4's Home placeholder now carries the resume/start affordances **and** a recovery entry row (`Log ›`), so CP2 can pass its own acceptance test and recovery logging is not unreachable from CP2 to CP5. Item 1's glyph destinations are corrected in the same pass: all four retired glyphs now land on a CP2 placeholder and are *carried into* their CP5/CP14 homes, rather than being promised to a checkpoint three ahead. |
| O4 | CP13's `depends_on: CP4` is unexplained, exactly as CP6's was before round 1's O2 | **Accepted, and the dependency is kept rather than dropped.** New CP13 item 5 states the link: the six scales CP13's entry screen writes are the six `ReadinessScore` consumes, and CP4 item 1 fixes which are inverted — so CP13's both-ends labels must be the polarity CP4 normalized, or the entry screen and the readiness sheet disagree about which end of the same scale is good. CP13 reads no score and changes no readiness input, and item 5 says so. |
| O5 | CP16 item 5 names "Reuse the previous set" as *the* unimplemented `UX_FLOWS.md` row not covered by a non-goal; there are at least three others | **Accepted.** The **rule** is now stated as applying to the document row by row, with the list explicitly non-exhaustive, and the list extended to `:41`, `:43` and `:110` with the evidence for each. `:54` ("Technique notes") is resolved the other way: the domain already backs it (`Exercise.instructions`), so by the plan's own domain-backing test **CP8 item 11 builds it** rather than deferring it — rendered under the focus header, deliberately *not* inside item 7's `stateDescription`-pinned disclosure. The pre-start preview `:41`/`:43` belong to becomes deviation `D12`, and the artboard inventory's `4a` row names it. |
| O6 | This item's `plan_stage` classification justifications are `workflow-v2-1-core`'s, describing a process work item this one is not | **Accepted.** Every `plan_stage` justification value in `…-remediation-1-artifacts.json` is rewritten to this item's own reasoning; the classification **keys** are unchanged, so the recomputed digest is unaffected (verified) and no re-generation was needed on its account. The reviewer's point stands on why it mattered: these strings are the reasoning of record for each exclusion, and round 1's I4 was found by reading one of them. |

One round-2 observation, also adopted: `EraseAllData`'s scope named
"progression recommendations and manual overrides" as if they were two tables.
`@Database(entities = …)` carries a single `ProgressionRecommendationEntity`
whose `override_result`/`override_at` columns hold the override, so CP14 item
6's Scope bullet now enumerates all **ten** entity types and says so.

**Not challenged, again, and recorded as such:** round 2 re-confirmed it found
no cause to contest the same four items round 1 left open — the 16-checkpoint
shape, CP4's readiness weights and bands, retiring `Exercises` and `Recovery`
as tabs, and the back-gesture interception. They remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 3 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 4 Optional)

Round 3 re-verified all six of round 2's applications against the tree rather
than against the disposition table above, and found no false claim about the
tree in revision 3 — the first round for which that is true. Its six new
findings were each validated against the tree at `7d8ae1b` before being
applied; **all six hold and all six are accepted**. There are no rejections to
record for this round either.

Both Important findings are omissions of the two shapes this plan has now been
asked to close three times: an existing test whose subject a checkpoint
changes without naming the file (I6's shape, now I8), and a cross-checkpoint
consumption the registry's dependency graph does not order (B1/B2/I7's shape,
now I9).

| # | Finding | Disposition |
|---|---|---|
| I8 | CP14 item 9 removes the `clearAllTables()` call `LocalBackupRepositoryAtomicityTest` exists to verify, without naming that file, owing a replacement assertion, or admitting that it narrows the invariant it cites | **Accepted in all three parts.** CP14 item 9 now names the file, states that its KDoc (`:24–35`) and the `clearAllTables()` assertion message (`:186–190`) are retargeted to the scoped clear, owes the assertion that **the scoped clear rolls back with the transaction on failure** as the existing third test retargeted rather than a new one, and states that `LocalBackupRepository`'s class KDoc (`:25–31`) narrows from "every table" to the training-data scope. The narrowing is also called out under "Preserved invariants" and "Backup compatibility impact", because a reviewer looking for a changed invariant looks there. The headline count moves: **four** test files knowingly rewritten, updated in CP2 item 7 and in Verification strategy — where the four are now listed with their *failure modes*, because this fourth one produces no failure at all and is the only one running the suite cannot catch. **Superseded in revision 8 as to the count only — see round 7's I14. Enumerating the suite's dependants instead of counting the files the plan had already named raised the total from four to seven, five of them CP2's. This row's failure-mode framing is not superseded: it is what keeps the longer list readable, and revision 8 adds a fourth mode to it rather than replacing it.** **Superseded a second time in revision 9, again as to the count only — see round 8's I16. The total is no longer a number this row or any other single checkpoint states: Verification strategy rolls up thirteen files from seven checkpoints' own enumerations. The failure-mode framing survives both supersessions intact, and revision 9 adds two further instances to the fourth mode rather than a fifth mode.** **Superseded a third time in revision 10, again as to the count only — see round 9's I18. Revision 9's own per-checkpoint enumerations were closed only on composable invocations and missed a JVM wiring test on exactly that ground; the roll-up is now fourteen files from the same seven checkpoints' enumerations, and this row's own subject — `LocalBackupRepositoryAtomicityTest` — and its failure-mode framing are untouched by the correction.** **Superseded a fourth time in revision 11, as to the count only — see round 10's I20: seventeen files, CP10's JVM second pass now covering both of its packages. This row's subject is untouched.** |
| I9 | CP8/CP6 and CP9/CP14 each name the other across an unordered registry pair, so one execution order ships a dead affordance and the other a toggle that gates nothing | **Accepted, resolved the plan's own way rather than by re-ordering** — the same choice B1 made. The graph is unchanged and the window is stated in the checkpoint text. **(a)** CP8 item 5 ships the suggestion strip as a read-only summary with **no `Why`**; CP6 item 5 adds `Why ›` and its route, and CP6 — the checkpoint that creates the route — owes the inward-path assertion, exactly as CP2 item 7 asserts inward paths rather than only the tab set. CP6 item 5 also names the entry point it has in *any* order: the exercise picker's recommendation row, which already exists and which CP7 item 6 carries. The registry-edge alternative was rejected because `CP8 depends_on CP6` drags CP4 in front of the milestone's flagship conversion for a one-sentence problem. **(b)** CP9 item 1 ships the finish confirmation **unconditionally** (there is no confirmation at all today — `ActiveWorkoutScreen.kt:200` is a bare `Button`), and CP14 item 4 gates it; whichever of the two lands second owns the gating assertion, since that is the first point at which both halves exist. **Part (a) of this resolution was superseded in revision 5 — see round 4's I10: it was written for a CP8-before-CP6 order `select_next_checkpoint` cannot produce. Part (b) survives unchanged, because CP9-before-CP14 is the order that runs.** |
| O7 | `designConfirmedNavGlyphsAreTheOnesTheDesignNames` is cited at `:90–93` and as one assertion where it is `:91–95` and two; and `RepFlowIcons.Nav`'s KDoc is a CP2 rewrite the plan never names | **Accepted.** The citation is corrected and both equalities named (`plans` and `history`, which are exactly the two design-confirmed survivors), so the regular-weight remedy is stated for both. `RepFlowIcons.kt:131–153` — which enumerates six destinations, calls four of them unconfirmed judgment calls and says "this milestone keeps the app's existing six" — is added as a CP2 rewrite, with the reason it needs naming: it is documentation, so nothing goes red when CP2 falsifies every sentence of it. |
| O8 | CP14's "the behaviours it gates" is true of three of its five preferences; `keep screen awake` has no behaviour to gate and `confirm before finishing`'s is built by CP9 | **Accepted as stated; the sizing conclusion is recorded and rejected.** CP14 now opens with the verified three-way split — three toggles over existing behaviour, one behaviour CP14 builds *and* switches, one built by CP9 and gated here. The `complexity: 3` / `session_target: 3` sizing **stands**: a window flag and a clause on an existing confirmation are both small, and both pass the domain-backing test. What the split buys is an accurate count in front of a reviewer weighing whether a checkpoint carrying a schema change, a migration, a destructive use case, a restore-path change and a screen should be one checkpoint — that judgment is the manual external reviewer's, and it is now made on the real contents. |
| O9 | Items 6 and 9 share "the same explicit repository method" without saying which interface owns it, and the two candidates are not equivalent | **Accepted, and the choice is made on the record.** A new application-layer port **`TrainingDataRepository`** with a single `suspend fun clearTrainingData()`, implemented in `data/` over `RepFlowDatabase` and injected into both `EraseAllData` and `LocalBackupRepository`. Putting it on the existing `BackupRepository` (`application/backup/BackupRepository.kt`) is rejected: it would make erasing training data depend on the backup port and name the milestone's general-purpose destructive primitive after the narrower of its two callers. The accepted cost — `LocalBackupRepository` gains a second collaborator — is stated. Item 6 also now states that the call nests inside `replaceAll`'s existing `withTransaction` and that Room's re-entrancy is what I8's retargeted assertion is there to prove rather than assume. |
| O10 | Of the four retired nav glyphs, `barbell` is the one CP2 item 1 does not give a CP2 consumer | **Accepted.** `barbell` → the Home placeholder's start affordance (item 4), carried into CP5's start card and then CP7's board. The bullet says why it is named as a CP2 destination: "each with a consumer at CP2" is a claim about all four, and pointing it at CP7 alone would make it three of four. |

One round-3 observation, also adopted: `ActiveExerciseUi`
(`ActiveWorkoutUiState.kt:77–84`) is constructed outside its own file in
`ActiveWorkoutViewModel`, `ActiveWorkoutScreenTest` (already CP8's) and
`ActiveWorkoutScreenWiringTest`. CP8 items 6 and 11 each add a field, so both
carry defaults as `plannedTarget` already does (`:83`), leaving
`ActiveWorkoutScreenWiringTest` compiling untouched — CP8 changes one existing
test file rather than two, and its count is now exact.

**Not challenged, a third time, and recorded as such:** round 3 again found no
cause to contest the 16-checkpoint shape, CP4's readiness weights and bands,
retiring `Exercises` and `Recovery` as tabs, the back-gesture interception,
D10/D11, or CP8's rewrite of `tappingAddSetClearsTheEntryFields`, and revision
3 changed none of them. Three local rounds declining to spend a round on these
is still not the user's sign-off; they, CP14's schema change, and whether the
deviation register alone satisfies F1's acceptance criterion 3 remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

*Recorded because it is in the file, not as a finding:* round 2's feedback
carried `Completed: 2026-09-07T18:41:00+01:00`, later than this work item's own
`last_transition` at the time. Nothing in the state depends on it; the round
ordering here is 1 → 2 → 3 as the revisions record it.

### Round 4 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 1 Important, 3 Optional)

Round 4 re-verified all six of round 3's applications against the tree rather
than against the disposition table above and found all six holding as claimed —
the second consecutive round in which revision *n* contained no false statement
about the repository. Its four new findings were each validated against the
tree at `7d8ae1b` before being applied; **all four hold and all four are
accepted**. There are no rejections to record for this round either.

All four come from one place no earlier round had looked: **what execution
order the workflow will actually run these checkpoints in.** Rounds 1–3 kept
resolving cross-checkpoint contracts by *stating the window* rather than adding
a registry edge — a defensible and consistently applied choice — but stated
windows without checking which one occurs. Revision 5 stops branching and
states the order once, next to the checkpoint table, then reads every affected
site off it.

| # | Finding | Disposition |
|---|---|---|
| I10 | Round 3's I9(a) resolution is written for the CP8-before-CP6 order, which `select_next_checkpoint` makes unreachable; in the order that actually runs, nothing wires `Why ›` into the focus screen's suggestion strip | **Accepted, and verified independently rather than taken from the finding.** Replaying `select_next_checkpoint` (`scripts/workflow_state.py:3093`, rule 2) against this registry yields `CP1 → CP2 → … → CP16` exactly: rule 2 scans the registry array's own order, `validate_registry_topological_order` guarantees that order is topological, and rules 1 and 4 reorder nothing. So **CP6 always precedes CP7 and CP8**, and I9(a)'s window never opens. The resolution is the finding's option 1, the one consistent with what will happen: **CP8 ships the suggestion strip with `Why ›` already routed into CP6's screen and owns the inward-path assertion**, because CP8 is the second half of the pair to land. CP6 item 5's third bullet, CP8 item 5, CP6's "Tests CP6 owes" (which loses the assertion and the false "CP8 item 5 says so" cross-reference, and gains the picker-row inward-path assertion CP6 genuinely can write) and `TEST_RESULTS.md` §4 all move together. The `CP4`-dragging rationale is **removed**: CP4 is at array index 4 and is selected long before CP8 either way, so `CP8 depends_on CP6` would have cost nothing — the edge is still declined, now on the honest ground that it would restate the array order rather than add a constraint. |
| O11 | "Preserved invariants" undercounts what this milestone adds to `domain/` and to the application layer, and revision 4's own new port is missing from it | **Accepted.** The domain-purity bullet now names **two** new pure computations — `domain/recovery/ReadinessScore.kt` (CP4 item 1, and the plan's own "single largest piece of invented product logic") and estimated 1RM (CP15) — and states CP14 as the explicit exception to "presentation, or application-layer read models over existing repositories": a new `TrainingDataRepository` port, `EraseAllData`, `SettingsRepository`, a Room table and `MIGRATION_7_8`. The bundle's `REVIEW_REQUEST.md` already had both right; it was the authoritative document that was behind its own generated summary, in the section whose job is to say what the milestone does to the layers. |
| O12 | CP16 item 2 cites "F1's acceptance criteria 4 and 6"; that list has five entries, and item 6 belongs to a different list | **Accepted.** F1 carries two numbered lists — **required remediation 1–6** and **acceptance criteria 1–5** — and CP16 item 2 conflated them. It now cites acceptance criterion **4** (side-by-side manual review) and **required-remediation item 6** (physical SM-S928B, AVD as supporting evidence) by list name, quotes both, and states that there is no acceptance criterion 6. CP16 item 1 now also cites **acceptance criterion 5**, the one the plan never named, which item 1's forced full gate plus item 2's device run is exactly what satisfies. CP1's "acceptance criteria 3 and 4" was already correct and is unchanged. |
| O13 | Several cross-checkpoint hedges are decidable, and saying the real order would be shorter and safer than branching on it | **Accepted, and it is the fix that ends the series.** A new paragraph under the checkpoint table states the execution order, its derivation, and the rule the sites below read off it: **whichever of two checkpoints lands second wires the halves together and owns the test**. Three branches collapse to facts — CP14 item 4's dead "where CP14 lands before CP9" clause (CP9 always precedes CP14, so item 6's flat "five tests CP14 owes" was already right and the two sentences no longer disagree); CP7 item 6, which described carrying across three override buttons CP6 has already replaced by the time CP7 runs (it now carries "whatever is there", which under the real order is CP6's `Why ›` link); and CP9 item 1's two-sided window. CP6 item 5's "in any order" claim about the picker row was **correct** and is kept as a statement about the base tree (`ActiveWorkoutScreen.kt:392–415`). |

**Where this leaves the pattern.** Four consecutive rounds found a
cross-checkpoint contract the dependency graph does not enforce (B1, B2/I7, I9,
I10). Revision 5's answer is not another window: it is that the order is
knowable, stated once, and every affected item now reads it rather than
guessing at it.

**Not challenged, a fourth time, and recorded as such:** round 4 again found no
cause to contest the 16-checkpoint shape, CP4's readiness weights and bands,
retiring `Exercises` and `Recovery` as tabs, the back-gesture interception,
D10/D11, CP8's rewrite of `tappingAddSetClearsTheEntryFields`, or revision 4's
recorded rejection of round 3's O8 sizing conclusion — and it explicitly called
that rejection the external reviewer's to overturn rather than a local
reviewer's. Revision 5 changes none of them. Four local rounds declining to
spend a round on these is still not the user's sign-off; they, CP14's schema
change and its sizing, and whether the deviation register alone satisfies F1's
acceptance criterion 3 remain live for `MANUAL_EXTERNAL_PLAN_REVIEW` and for
the user.

### Round 5 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 2 Optional)

Round 5 re-verified all four of round 4's applications against the tree at
`7d8ae1b` — including re-deriving I10's execution-order premise by replaying
`select_next_checkpoint` rather than reading revision 5's account of it — and
found no false claim about the tree in revision 5. Its four new findings were
each validated the same way before being applied; **all four hold and all four
are accepted**, so there are no rejections to record for this round either.

Both Important findings are the same move applied to the one checkpoint
revision 5 did not re-read: **CP2**. Revision 5 stated the execution order as
fact and re-read CP6/CP7/CP8/CP9/CP14 off it. CP2 sits at array index 2 —
earlier than every checkpoint it names — and two of its items were written
against artefacts CP5 and CP7 build. That this is the fifth consecutive round
to find a cross-checkpoint contract the dependency graph does not enforce (B1,
B2/I7, I9, I10, now I11/I12) is the reason both fixes are structural rather
than local: item 4 gains an explicit placeholder-contract table, so the next
omission of this kind is visible while the placeholder is being written, the
way the order paragraph is now visible while an ordering claim is being
written.

| # | Finding | Disposition |
|---|---|---|
| I11 | CP2 retires `Exercises` and `Backup` as tabs and routes both through Settings, but nothing reaches Settings until CP5 (item 1's gear), so both destinations are unreachable from the running app for four checkpoints | **Accepted, and verified against the tree before applying.** `grep -n "navigate(RepFlowDestinations" RepFlowNavHost.kt` returns only exercise-edit/new (`:70`, `:71`), plan-edit/new (`:91`, `:92`) and recovery-history (`:114`): `EXERCISES` and `BACKUP` have **no inbound edge at all** and are reachable only as tabs, with `EXERCISES` additionally the current `startDestination` (`:65`). Rounds 1 and 2 (B2, I7) made the *inner* links real and neither established the outer one. The fix is the one those rounds already used, applied one level out: **CP2 item 4's Home placeholder carries a third affordance — the gear / `Settings ›` entry — carried into CP5's header**, so Home → Settings → {Library, Backup} is whole from the checkpoint that breaks the tabs; "exactly two things … Nothing else" becomes three, item 5's `Exercises` and `Backup` bullets state that Settings is their only inward path until CP7 and CP14 respectively, and item 7's assertion chain now starts at the start destination (`Settings` from `Home`) instead of at the `SETTINGS` route, where it would have passed against a route no user can open. Keeping `Backup` as a tab until CP14 was rejected: it contradicts the four-destination IA this milestone exists to deliver. **Worth recording because it sharpens what the finding caught:** the mapping's `REQ-1` already read "Settings reached from Home … every relocated destination is reachable by its new inward path from the checkpoint that changes the destination set". The requirement was right from revision 1; it was the checkpoint text that did not build the link the requirement names — which is why a mapping that maps `REQ-1` to `CP2` is not by itself evidence that `CP2` delivers it, and why item 7 asserts the path rather than the route. **Round 5's structural observation is adopted alongside the fix**: item 4 now carries a *what each placeholder owes its successor* table (three routes, the affordances each must carry, the checkpoint that absorbs each), because CP2 is structurally the checkpoint with the most forward references and the table is what makes the next dropped affordance visible in the writing rather than in review. |
| I12 | CP2 item 6 intercepts the back gesture "to the same exit confirmation the `X` opens", but the `X` and its confirmation are CP7 item 1's; at CP2 neither exists, and CP2 still owed the assertion | **Accepted; resolved as the finding's option 2, moving the behaviour to CP7.** Verified first: `BackHandler` appears in exactly two files, both editors (`ExerciseEditorScreen.kt:54`, `TrainingPlanEditorScreen.kt:44`), `presentation/workout/` has no dialog or confirm affordance of any kind, and today's exit is a bare `OutlinedButton(onClick = { onAbandonWorkout(content.sessionId) })` at `ActiveWorkoutScreen.kt:203–205` — the finding's premise holds in full. Option 1 (CP2 builds a minimal confirmation for CP7 to replace) is **rejected**: it is throwaway work whose only purpose is to give a mis-placed item a referent, and the plan's own second-half-owns-it rule — the one round 4's I10 resolution turns on — decides it the other way, since the confirmation lands at CP7. So **CP7 item 1 owns the interception, the confirmation and the assertion**, and names the deliberate replacement of today's unconfirmed `Abandon` button, which no checkpoint had claimed. CP2 item 6 keeps only the nav-suppression half **and now states that it is free**: `RepFlowNavHost.kt:49` gates the bar on `TOP_LEVEL_ROUTES`, derived from `TOP_LEVEL_DESTINATIONS` (`:26`), so dropping `WORKOUT` in item 1 suppresses the bar with no code and CP7's and CP8's routes inherit it for the same reason. Item 6 stops naming the board/focus routes and the `X` as though they exist when it runs, item 7's fourth bullet moves to CP7 with the behaviour, and the "Areas the reviewer should specifically challenge" bullet is re-pointed at CP7. |
| O14 | `TEST_RESULTS.md` §1 states a plan-stage `review_content_id` and a `state_revision` that are not this bundle's | **Accepted, and it is bundle content, so it costs no plan edit.** The diagnosis is exactly right, mtimes included: revision 5's `TEST_RESULTS.md` was written before the last plan edit of that round, so `:28` recorded a superseded digest (`c9057700…`) while the binding artefacts (`MANIFEST.md`, `REVIEW_REQUEST.md`) correctly carried `f9789ed6…`, and `:25` predicted a `state_revision` the publication had already passed. Nothing gated on either line — the guard did its job — but the point stands that a reader who lifts a digest from the evidence table and hands it to the external reviewer produces feedback that fails `assert_feedback_matches_bundle`. Revision 6's `TEST_RESULTS.md` states this round's recomputed digest and the `state_revision` actually reached, and is written **after** the final plan edit for exactly this reason. |
| O15 | CP16 item 2 discharges required-remediation item **5** as well, and O12's fix names only two of the three obligations it satisfies | **Accepted.** Required-remediation item 5 (`FUNCTIONAL_REVIEW.md:77`) — "perform a new side-by-side functional validation after remediation, using the Claude Design screens and the running Android application together" — is item 2's *primary* obligation, and O12's fix named the device-target requirement beside it while skipping it. Item 2 now cites all three by list name, quotes each, and names item 5 first, since a citation list that gives an item's conditions but not its subject is the labelling failure O12 was raised to fix. Item 2 also now records where required remediation 1–4 are answered (CP1's inventory and deviation register, CP16 item 3) rather than leaving them unattributed. |

**Recorded, on round 5's own framing of it as an observation rather than a
finding:** round 1's I1 disposition row still reads "in either checkpoint
order", which round 4's I10 superseded for the live text. It is **left as
written**. The disposition tables are the record of what each round decided
with what it then knew, not a second copy of the plan; rewriting a historical
row to match a later round's conclusion would destroy the only evidence of how
the conclusion was reached. Round 3's I9 row already carries an explicit
in-line supersession note for exactly this reason, and that is the pattern —
annotate, never restate.

**Where this leaves the pattern.** Five consecutive rounds have found a
cross-checkpoint contract the graph does not enforce, and revision 5's answer
to that (state the order once, read every site off it) was the right tool —
round 5's two findings are precisely what fell out of pointing that tool at the
checkpoint revision 5 had not re-read. Revision 6 finishes the pass and adds
the second such tool for the second such class: the placeholder-contract table,
which does for entry points what the order paragraph does for ordering.

**Not challenged, a fifth time, and recorded as such:** round 5 again found no
cause to contest the 16-checkpoint shape, CP4's readiness weights and bands,
retiring `Exercises` and `Recovery` as tabs, the back-gesture interception
itself, D10/D11, CP8's rewrite of `tappingAddSetClearsTheEntryFields`, or
revision 4's recorded rejection of round 3's O8 sizing conclusion. Revision 6
changes none of them, and moving the interception from CP2 to CP7 changes
**where** it is signed off, not **whether** — it is still the plan's flagged
platform behaviour change. Five local rounds declining to spend a round on
these is still not the user's sign-off; they, CP14's schema change and its
sizing, and whether the deviation register alone satisfies F1's acceptance
criterion 3 remain live for `MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 6 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 1 Important, 1 Optional)

Round 6 re-verified all four of round 5's applications against the tree at
`7d8ae1b` rather than against the disposition table above — re-deriving I10's
execution-order premise independently a second time and re-checking a broad
sample of citations, several unread since the revision that introduced them —
and found no false claim about the tree in revision 6. Its two new findings
were each validated the same way before being applied; **both hold and both
are accepted**, so there are no rejections to record for this round either.

I13 is the sixth consecutive round to find a cross-checkpoint contract the
dependency graph does not enforce, and it is the narrowest form the pattern
has taken yet: not a missing affordance but a **built affordance with no
assertion behind it**, on the fourth of the four tabs CP2 retires. Revision
6's own structural answer — the placeholder-contract table — is what made it
findable, and the fix is to finish that tool rather than add a third: the
table now carries one row per affordance and names the item 7 walk that
covers each, so an uncovered affordance is an empty cell rather than a
count a reader has to perform.

| # | Finding | Disposition |
|---|---|---|
| I13 | CP2 item 1 retires four tabs and item 4's rule quantifies over all four, but item 7 asserts an inward path for three; the uncovered one is `WORKOUT`, the destination behind the app's primary action | **Accepted, and verified against the tree before applying.** `TOP_LEVEL_DESTINATIONS` (`RepFlowDestinations.kt:45`) holds exactly six entries — `EXERCISES` (`:48`), `WORKOUT` (`:54`), `PLANS` (`:60`), `RECOVERY` (`:66`), `HISTORY` (`:72`), `BACKUP` (`:78`) — so item 1 retires `EXERCISES`, `WORKOUT`, `RECOVERY` and `BACKUP`, and `WORKOUT` has the same reachability structure the other two relocated destinations had: `grep -n "navigate(" RepFlowNavHost.kt` returns six call sites, `:53` being the bottom bar's own switch over `TOP_LEVEL_DESTINATIONS`, and none of `:70`, `:71`, `:91`, `:92`, `:114` targets `WORKOUT`. The active-workout screen is reachable **only as a tab** today, so from the moment item 1 lands the Home placeholder's resume / start affordance is its only inward path — an affordance item 4 already builds and nothing asserted. This is round 1's **B1 with the fix in place and the guard absent**, which is why it is Important rather than Blocking. The finding's own sharpening is adopted too: item 7's nav-absence bullet asserts a property *of* the `WORKOUT` route (satisfied by `RepFlowNavHost.kt:49`'s gate the moment item 1 drops it from the list) and would pass whether or not anything navigates there — so the two bullets are now written as a pair, with the nav-absence one stating that it is the `Workout` walk that gives it a reachable subject. **The fix moves three sites together, as I11's did:** item 7's inward-path bullet now walks four destinations and says so; item 4's prose names `WORKOUT` alongside `EXERCISES` and `BACKUP` as having no inbound edge, so the carry-over reads as the obligation it is rather than a convenience; and the contract table is restructured to one row per affordance with a third column naming the item 7 walk that covers it, plus a closing sentence tying the four retired tabs to the four walks. `TEST_RESULTS.md` §4's CP2 entry records the addition beside round 5's. The route name is deliberately left open: the assertion is written against whatever route the start affordance navigates to, the same latitude item 7's other bullets take. |
| O16 | The plan's front matter states `Plan revision: 5` and "rounds 1, 2, 3 and 4" while the title, the registry, `MANIFEST.md` and `WORKFLOW_STATE.json` all say 6 | **Accepted.** Both halves were correct in revision 5 and neither was updated when revision 6 published. The front-matter bullet now reads revision **7** and rounds 1–6, matching the title, the registry's `plan_revision`, `MANIFEST.md` and the state file. **The finding's diagnosis of why nothing caught it is exact and worth keeping on the record:** `workflow_fingerprint.load_plan_revision` cross-checks the registry against the plan document's **title line only** — it reads `splitlines()[0]` and matches `PLAN_TITLE_REVISION_RE` against it — so a revision disagreement anywhere below line 1 is invisible to bundle generation, and `TEST_RESULTS.md` §1 correctly reported that check as PASS while this line was wrong. Graded Optional on this plan's own precedent (round 4's O11, round 5's O14), and the consequence was real but bounded: `MANUAL_EXTERNAL_PLAN_REVIEW` is the next stage, and the human reviewer would have read a front matter claiming revision 5 after four rounds above a dispositions section carrying five. |

**Where this leaves the pattern.** Six consecutive rounds have found a
cross-checkpoint contract the graph does not enforce (B1, B2/I7, I9, I10,
I11/I12, now I13), and the last three have all been about CP2 — the
checkpoint that changes the destination set for the whole app while sitting
at array index 2, earlier than every checkpoint it names. The plan's answer
has been two tools, each added when the class it addresses became visible:
the execution-order paragraph for ordering, and the placeholder-contract
table for entry points. Revision 7 adds no third tool. It finishes the
second one, so that the coverage question the table exists to answer —
*which assertion covers this affordance?* — is answered in the table itself
rather than by counting item 7's bullets against item 1's retirements.

**Not challenged, a sixth time, and recorded as such:** round 6 again found no
cause to contest the 16-checkpoint shape, CP4's readiness weights and bands,
retiring `Exercises` and `Recovery` as tabs, the back-gesture interception
(now CP7's, together with the replacement of today's unconfirmed `Abandon`
exit — a second platform-visible change on the same surface), D10/D11, CP8's
rewrite of `tappingAddSetClearsTheEntryFields`, or revision 4's recorded
rejection of round 3's O8 sizing conclusion. Revision 7 changes none of them.
**Six local rounds declining to contest these is not the user's sign-off**;
they, CP14's schema change and its sizing, and whether the deviation register
alone satisfies F1's acceptance criterion 3 remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 7 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 1 Optional)

Round 7 re-verified both of round 6's applications against the tree at
`7d8ae1b` rather than against the disposition table above — re-running I13's
two premises (`TOP_LEVEL_DESTINATIONS`' six entries and the six `navigate(`
call sites) instead of reading revision 7's account of them, re-checking a
broad sample of citations, and re-rendering the registry to compare it against
the plan's embedded table — and found no false claim about the tree in
revision 7. Its three new findings were each validated the same way before
being applied; **all three hold and all three are accepted**, so there are no
rejections to record for this round either.

Round 7 is the first round to audit an input class none of the six before it
had touched: **the existing test suite itself**. Every earlier round read the
plan's *claims about* tests; this one enumerated the test files that actually
depend on what a checkpoint changes. Both Important findings fall out of that
enumeration, and both are exactly what the plan's own Verification-strategy
rule — "an existing test is read before the thing it was written about is
changed" — exists to catch. That the rule was stated correctly for six
revisions while five of its seven instances had never been looked for is the
finding under the findings, and it is why revision 8's fixes are enumerations
with their greps recorded rather than corrected counts.

| # | Finding | Disposition |
|---|---|---|
| I14 | CP2 changes what three further existing test classes depend on; the plan names one of the four, and states the milestone total as four files | **Accepted in both halves, each verified against the tree before applying.** **(a)** `RepFlowBottomNavigationBarTest` (`app/src/test/…/presentation/navigation/`) has five tests, and two do not survive CP2: `everyTopLevelDestinationCarriesItsOwnNavGlyph` (`:71–86`) hardcodes all six `RepFlowIcons.Nav` members (`:75–80`) and asserts the map equals `TOP_LEVEL_DESTINATIONS.associate { it.route to it.icon }` (`:82–85`), so item 1's move of `exercises`, `workout`, `recovery` and `backup` out of `Nav` makes the **file not compile** — the identical mechanism item 2 already documents for `RepFlowIconsTest`; and `topLevelDestinationsKeepTheirRoutesAndOrder` (`:89–102`) pins the exact six-route order under a KDoc reading "This checkpoint is visual only: same six routes, same order" (`:88`), which compiles and fails as an assertion while the KDoc goes stale silently. Its other three tests read `repFlowNavColors`' colour roles only (`:29–41`, `:43–55`, `:62–69`) and are untouched — recorded so the rewrite is scoped to two methods rather than to the file. **(b)** `BackupRouteSafCancellationTest` (`:51–54`) and `BackupRouteUnreadableRestoreFileTest` (`:53–56`) both reach Backup by clicking `R.string.exercise_list_backup_content_description`, which is the `BACKUP` tab's own `contentDescriptionRes` (`RepFlowDestinations.kt:80`); once item 1 drops `BACKUP`, both fail at `performClick()` with no matching node — a **runtime instrumented failure at CP2**, twelve checkpoints before CP14 owns Settings. Their subjects are untouched by this milestone, so they are **re-routed, not rewritten**: through item 4's gear to the Settings placeholder's `Backup` row, the walk item 7 already asserts. **The blast radius was closed rather than assumed**, and both greps are now recorded in item 7 as part of the fix: `grep -rl "createAndroidComposeRule<MainActivity>" app/src/` returns exactly three classes (every other UI test uses `ComponentActivity` and sets its own content), and `exercise_list_backup_content_description` is referenced under `app/src/` only by those three tests and the destination entry itself. **The count moves from four files to seven** — CP2 owns five (three rewritten, two re-routed), CP8 one, CP14 one — in CP2 item 7, in CP14 item 9's "seventh and last" sentence, in Verification strategy (now **seven** cases in **four** failure modes, the new one being the runtime no-matching-node failure), and in the reviewer-challenge list, which states the old count explicitly so the reviewer checks the enumeration rather than the number. Round 3's I8 row, which set the headline count at four, carries an in-line supersession note rather than being rewritten — the plan's own annotate-never-restate rule. **Superseded in revision 9 as to the headline count, on the same rule — see round 8's I16. This row's own findings all hold and none is disturbed; what does not survive is the sentence "the count moves from four files to seven", because seven was still the count of the files three named mechanisms reach and six further files sit on screens none of them touches. CP2's five, CP8's one and CP14's one are unchanged; they are now five, one and one entries in a thirteen-file roll-up rather than the whole of it.** **Superseded a second time in revision 10, again as to the count only — see round 9's I18. CP2's five and CP14's one remain exactly as this row states; the roll-up they sit in is now fourteen files, since CP10's own enumeration was blind to a JVM wiring test on grounds this row does not touch.** **Superseded a third time in revision 11, as to the count only — see round 10's I20: the roll-up is seventeen; CP2's five and CP14's one are unchanged.** |
| I15 | CP8 item 7 removes the input mechanism a fourth `ActiveWorkoutScreenTest` method depends on, while stating that the disclosure's tests are kept | **Accepted.** Verified in the file: `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing` (`:260–282`) expands the disclosure and drives the RPE and Pain fields with `performTextInput("8")` / `performTextInput("2")` (`:273–274`) before collapsing, tapping `Add set` and asserting `recordedRpe == 8.0` and `recordedPain == 2`. Item 7 replaces exactly those fields with scale rows, and a scale row cannot satisfy `performTextInput` for the same reason the plan already states a stepper cannot (`tappingAddSetClearsTheEntryFields`, `:303–316`, `performTextInput` at `:311`). The finding's narrow reading of the old sentence was also checked and is right: the other three — `theSetDetailFieldsAreHiddenUntilTheDisclosureIsExpanded` (`:239–247`), `expandingTheDisclosureRevealsAllThreeOptionalFields` (`:249–258`) and `theDisclosureHeaderReadsItsExpandedStateToAccessibilityServices` (`:284–301`) — locate fields by label text or assert the header's `stateDescription`, and all three do survive. So item 7 is **not** corrected from three to four surviving tests; it is split: three survive unchanged, one is rewritten to drive the scale rows and assert the same contract — *values entered in the disclosure survive a collapse and reach `onRecordSet`*, which the test's own inline comment names (`:275–276`) and nothing else in the file asserts. The assertion values are stated as unchanged because `D11` keeps RPE on the domain's `0.0..10.0`, so only the input mechanism moves. **What this fix is really about is the sentence, not the count**: item 7 previously told the implementer the disclosure's tests are kept *because the behaviour is unchanged* — true of the disclosure, false of this test — which is an instruction to preserve a test the checkpoint breaks, and the likely outcome is a minimal in-place patch rather than the deliberate rewrite the plan demands everywhere else. The file count is unaffected; CP8's set-entry note now states that it is the second of two named method rewrites in one file, and CP8 item 11's cross-reference to "three instrumented tests" is re-pointed at the three item 7 keeps. |
| O17 | The contract table's `Library` row cites "CP14 item 2 — grouped settings screen"; item 2 is the `Library` row, item 1 is the grouped screen | **Accepted.** Read directly: CP14 item 1 is "New `presentation/settings/`, grouped as the design groups it. This checkpoint replaces CP2's Settings placeholder"; item 2 is "**Library** row → the exercise library (CP10)"; item 5 is the "**Data** group". The item numbers in the table were right and the descriptors were not, and the `Backup` row carried no item number at all. Both cells now read "CP14 item 2 — `Library` row" and "CP14 item 5 — `Data` group". Graded and applied as Optional on this plan's precedent (O11, O14, O16), but worth the edit for the finding's own reason: the "Absorbed by" column is what revision 7 added so that *which thing absorbs this affordance?* is answered in the table rather than by reading CP14, and a descriptor naming the wrong item undercuts precisely that. |

**Round 7's structural observation is adopted, and it is the first new tool
since revision 6.** Round 7 noted that the plan's two existing tools — the
execution-order paragraph and CP2's placeholder-contract table — both answer
*what does this checkpoint hand forward?*, and neither answers *what existing
test reads what this checkpoint changes?*, which is the question behind I6, I8,
I14 and I15 across four separate rounds. It offered CP1's inventory as the
place to answer it once, and that is adopted: **CP1's per-screen inventory
gains a tests column** — the test files and, where it is a subset, the method
names that depend on each surface's nodes, labels, content descriptions,
destination entries or input affordances, each found by grepping for the
subject rather than by recall, and each marked *survives* or *rewritten*.
Verification strategy's seven named rewrites seed that column exactly as the
deviation register seeds the deviation one. It costs no new artefact, no
registry change and no `session_target` change: CP1 already reads every surface
once and already writes down what will own it after conversion.

**Where this leaves the pattern.** Six consecutive rounds found a
cross-checkpoint contract the dependency graph does not enforce (B1, B2/I7, I9,
I10, I11/I12, I13). Round 7 is the seventh consecutive round to find a contract
no artefact enforces and the **first that is not about checkpoint ordering at
all** — it is about the suite, and it was found by enumerating rather than by
reading the plan more carefully. That is worth stating plainly for the external
reviewer: the plan's prose has now been re-read seven times, and the last round
that read only the prose still missed five test files. Revision 8's answer is
therefore not another prose tool but an instruction to grep — recorded in CP2
item 7 as the two greps that close its own enumeration, in Verification
strategy as the reason the list is an enumeration rather than a recollection,
and in CP1 as a standing column covering every surface still to be converted.

**Not challenged, a seventh time, and recorded as such:** round 7 again found
no cause to contest the 16-checkpoint shape, CP4's readiness weights, bands and
inversion set, retiring `Exercises` and `Recovery` as tabs, the back-gesture
interception at CP7 together with the replacement of today's unconfirmed
`Abandon` exit, D10/D11 and CP8's rewrite of
`tappingAddSetClearsTheEntryFields`, or revision 4's recorded rejection of
round 3's O8 sizing conclusion. Revision 8 changes none of them. **Seven local
rounds declining to contest these is not the user's sign-off**; they, CP14's
schema change and its sizing, and whether the deviation register alone
satisfies F1's acceptance criterion 3 remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 8 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 0 Optional)

Round 8 re-verified all three of round 7's applications against the tree at
`7d8ae1b` rather than against the disposition table above — re-running the two
greps CP2 item 7 records, re-reading every method `RepFlowBottomNavigationBarTest`
and `ActiveWorkoutScreenTest` are cited for, re-deriving CP2 item 2's icon
arithmetic, re-rendering the registry against the plan's embedded table and
replaying `select_next_checkpoint` to exhaustion — and found no false claim
about the tree in revision 8. Its two new findings were each validated the same
way before being applied; **both hold and both are accepted**, so there are
again no rejections to record.

Round 8 continued round 7's audit of the existing test suite and turned it on
the checkpoints round 7 had not reached. Round 7 enumerated the dependants of
**CP2's destination change** and **CP8's input affordances**; revision 8 then
stated the result of that enumeration as a *milestone-wide* total. Both of
round 8's findings are the gap between those two scopes, and the second is the
same gap applied to a maintained artefact rather than to a test.

| # | Finding | Disposition |
|---|---|---|
| I16 | The "seven test files" total is CP2's and CP8's enumeration stated as a milestone total; CP8's own file is claimed "preserved everywhere else" over nine unexamined methods; CP10–CP13 name no existing test at all | **Accepted in full, every premise re-derived against the tree before applying.** **(a)** `ActiveWorkoutScreenTest` holds **14** `@Test` methods (`grep -c`), and revision 8 accounted for five. CP8's closing paragraph now carries a **row per method** — what it reads, which item changes it, and the mode — replacing "preserved everywhere else". Three of the nine unexamined methods do not survive item 3 as written, each confirmed in the file: `aPlannedExerciseShowsWarmupAndWorkingProgress` (`:151–165`) asserts all three planned-target chips; `everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth` (`:167–194`) is a parent-milestone clipping guard whose own comment (`:169–176`) names `weight(1f, fill = false)` plus `RepFlowStatusChip`'s `maxLines = 1`/`Ellipsis` as the mechanism it pins — item 3 now states where that guard goes (retargeted onto the placeholder rows, or retired in the deviation register with the measurement that justifies it); and `anAdHocExerciseShowsNoPlannedTargetSummary` (`:196–203`) asserts `workout_active_plan_working_progress` **does not exist**, so once item 3 removes the summary for every exercise it passes vacuously — **the second instance of the mode the plan calls the one most worth naming in advance**, and the reason Verification strategy's fourth mode now lists three. The block comment at `:217–226` ("The **four** tests below … a value **typed into** them") is added to item 7's edit, as item 2 already does for `RepFlowIcons.Nav`'s KDoc. **(b)** Four new per-checkpoint enumerations, each with its grep recorded: `grep -rln "<Screen>(" app/src/androidTest/ app/src/test/` returns two classes for CP10 (`ExerciseListScreenTest` 15, `ExerciseEditorScreenTest` 13), two for CP11 (11, 15), two for CP12 (9, 3) and **nothing** for CP13 — whose emptiness is now stated as a result rather than left as silence, with `RecoveryFutsalViewModelTest` (9) and `RecoveryHistoryViewModelTest` (2) recorded as the only coverage that exists. All six instrumented classes are `ComponentActivity`-rooted, which is exactly why CP2's grep correctly excluded them and why nothing else had looked. **The count is restated as what it is**: CP2 item 7 states CP2's five, Verification strategy rolls thirteen files up from seven checkpoints' own enumerations, and the reviewer-challenge list says plainly that the total has now moved twice and the enumeration is the thing to check. **Superseded in revision 10, as to the count and as to one instance of the "returns nothing" characterization of the per-checkpoint enumerations' closure — see round 9's I18. This row's own (a) and (b) both hold: CP10's, CP11's and CP12's greps return exactly what this row says. What (b) did not say is that a grep closed on composable invocations cannot see a JVM test, and CP10 owns exactly one — `ExerciseListScreenWiringTest`, not `ComponentActivity`-rooted and therefore never reachable by this row's own six-class enumeration. The roll-up is now fourteen files from the same seven checkpoints.** **Superseded a second time in revision 11, as to the count only — see round 10's I20: CP10's entry grows from three files to six once its JVM second pass covers both packages; the roll-up is seventeen. This row's (a) and (b) still hold.** |
| I17 | `ROLE_AUDIT.md` is a maintained artefact in the production tree that this milestone falsifies across most rows and no checkpoint owns; CP3's `RepFlowPrimitivesTest` extension covers only half of what that file does | **Accepted in both halves.** **(a)** Re-derived rather than read: `grep -rn "DropdownMenu(" app/src/main/kotlin` returns exactly the seven the audit's `:116` claims, and all seven are on screens CP7/CP8, CP10, CP11 and CP12 convert while CP3 retires the component; the seven `NumericEntryField` call sites (`:144–148`) are CP8 items 6 and 7's; the five `ListItem` supporting lines (`:140–143`) are on four screens CP11–CP13 convert; the bare `Card(` (`:118`) is CP11 item 2's; the FAB pair (`:110–111`) is what `6b`'s bottom bar replaces. The file's own re-run trigger (`:3`, "before changing any value in `RepFlowColor.kt`") is the wrong trigger for a milestone that changes consumers rather than values, so the staleness is silent by construction. **CP16 item 8 now owns it**, stated as the audit's own method re-run (`:8–13`) over the converted tree rather than a patch of the named lines, together with `RepFlowColor.kt:182–183`'s consumer claim. **(b)** `RepFlowPrimitivesTest`'s three regions were checked directly: *"Contrast floors the primitives own"* holds **seven** tests at `:63–146` (round 8 said six; the range is right and the count was one low) and *"Dimensions and radii"* holds seven at `:152–218`. CP3's sentence enumerated only the second of those two, so its new primitives inherited a tap-target and radius obligation and no WCAG floor. It now names both regions and the ground each new primitive's text renders on. The `RepFlowSheet` case is confirmed and is sharper than the finding stated: `BottomSheetDefaults.ContainerColor` resolves to `SheetBottomTokens.DockedContainerColor` = `ColorSchemeKeyTokens.SurfaceContainerLow` in `material3-android-1.4.0`, and `surfaceContainerLow` is set by **neither** `RepFlowDarkColorScheme` nor `RepFlowLightColorScheme` and appears in neither of the audit's tables — so CP3 must either assign the role or record the baseline, and CP3 now says so. **The sizing question the finding raises is answered rather than absorbed**: the recomputation half stays with CP3, which creates the new grounds; CP16 gets inventory maintenance driven by two documented greps; `session_target` stays at 2 and the registry is unchanged, with the call recorded in CP16 item 8 and offered to the reviewer in the challenge list. |

**One correction revision 9 makes to itself, found while writing CP13's
enumeration and recorded because acceptance criterion 3 requires every changed
claim to be re-validated against the repository.** Revisions 1–8 described the
recovery screen's six 0–5 inputs as "numeric text fields", in CP13 item 1 and
in the `docs/UX_FLOWS.md` comparison table near the top of this plan. They are
not. All six are `RecoveryFutsalScreen.kt`'s private `ScaleStepperRow`
(`:233–245`) — a label, a `-` `TextButton`, the value as plain `Text`, a `+`
`TextButton` — called at `:112–132`; the free-text number fields on that screen
are the futsal duration (`:148`), session RPE (`:157`) and notes (`:135`).
`UX_FLOWS.md:118`'s requirement is unmet either way, since a `-`/`+` stepper is
neither a scale nor a set of selectable options and carries no end labels at
all, so the checkpoint's work is unchanged — but the plan was naming the wrong
control on the one screen where no test would have caught it, which is I16's
own argument arriving from the other direction. Both places are corrected.

**Round 8's non-required observation is adopted, because I16's fix is what
makes it possible.** Round 8 noted that CP1's new tests column was seeded from
Verification strategy's list and therefore inherited the scope the column
exists to escape. With CP10–CP13 now carrying their own enumerations, the seed
is changed to the per-checkpoint enumerations themselves, which between them
cover every screen this milestone converts. The column's question and its seed
now have the same shape.

**Where this leaves the pattern.** Rounds 1–6 each found a cross-checkpoint
contract the dependency graph does not enforce. Round 7 was the first to audit
the test suite instead of the prose, and found five files. Round 8 audited the
same class one level up and found six more, plus a maintained artefact in the
production tree with no owner. The finding under both is one the plan can state
in a sentence: **it repeatedly published milestone-wide totals derived from
checkpoint-local enumerations.** "Seven test files" and "each with a consumer at
CP2" are the same shape, and so was "the seventh and last". Revision 9's answer
is structural rather than another corrected number — every conversion checkpoint
carries its own enumeration with the grep that produced it, Verification
strategy may only *add them up*, and the reviewer-challenge list says outright
that the total has moved twice and is the wrong thing to check.

**Not challenged, an eighth time, and recorded as such:** round 8 again found
no cause to contest the 16-checkpoint shape, CP4's readiness weights, bands and
inversion set, retiring `Exercises` and `Recovery` as tabs, the back-gesture
interception at CP7 together with the replacement of today's unconfirmed
`Abandon` exit, D10/D11 and CP8's two named `ActiveWorkoutScreenTest` method
rewrites, or revision 4's recorded rejection of round 3's O8 sizing conclusion.
Revision 9 changes none of them, and adds two of its own to the same list:
CP12 item 6's new home for the invalidate action, and CP16 item 8's sizing.
**Eight local rounds declining to contest these is not the user's sign-off**;
they, CP14's schema change and its sizing, and whether the deviation register
alone satisfies F1's acceptance criterion 3 remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 9 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 1 Optional)

Round 9 re-verified round 8's two applications against the tree at `7d8ae1b`
rather than against the disposition table, re-running all four new
per-checkpoint greps and re-deriving all nine new `@Test` counts; **both hold
as claimed**. Its own two Important findings and one Optional finding were
each validated the same way before being applied; **all three hold and all
three are accepted**, so there are again no rejections to record.

Round 9 turned the same audit one radius further out. Round 7 enumerated the
tests **two mechanisms** reach; round 8 enumerated the tests **three
mechanisms** don't reach and found six more, all `ComponentActivity`-rooted;
round 9's findings are the tests the round-8 grep itself — closed on
composable invocations — cannot reach by construction, because they are JVM.

| # | Finding | Disposition |
|---|---|---|
| I18 | The new per-checkpoint enumerations are grepped for composable call sites only, so CP10's conversion reaches a third test file nothing has looked at; CP13's own enumeration crossed that line and CP10–CP12's did not | **Accepted in full, every premise re-derived against the tree before applying.** `ExerciseListScreenWiringTest` (`app/src/test/…/presentation/exercise/list/`) exists exactly as the finding describes: five `@Test` methods, none reachable by a grep closed on `ExerciseListScreen(`/`ExerciseEditorScreen(` invocations because it invokes neither. CP10 item 6 now enumerates it on its own terms — `ExerciseListFilterOrder`, `exerciseListFilterLabelRes` and `exerciseListEmptyMessageRes(NO_SEARCH_RESULTS)` survive unchanged (item 1's filter-row conversion and item 3's empty-state copy both leave the underlying mapping and its resource ids alone), and `ExerciseFabSize` is retired with the FAB itself, decided here rather than left open: `rowAndFabSizesAreTheDesignsOwn` and `everyTapTargetClearsTheMinimum` become **compile failures**, the mode this plan prefers whenever the choice is open, rather than a fourth instance of the silent-pass mode. CP11 item 5 and CP12 item 6 each add the same second pass on CP13 item 6's own terms — `TrainingPlanListViewModelTest`/`TrainingPlanEditorViewModelTest` and `HistoryUiStateTest`/`HistoryViewModelTest` respectively, all four checked and confirmed to survive since none renders a composable or asserts an affordance — and CP11 additionally records that `presentation/trainingplan/list/` has no `ExerciseListLabels.kt`-shaped file for a wiring test to pin, so the second pass is genuinely empty there rather than merely unstated. Every one of CP10 item 6, CP11 item 5, CP12 item 6 and CP13 item 6 now states explicitly what its own grep can and cannot see, on CP2 item 7's terms. The roll-up moves from thirteen files to **fourteen**, and Verification strategy, the reviewer-challenge list and the historical I8/I14/I16 rows all carry the correction as an in-line supersession rather than a rewrite. **Superseded in revision 11 in two respects — see round 10's I20 and I21. (i) CP10's second pass stopped at `ExerciseListScreenWiringTest`; over both packages it adds `ExerciseListViewModelTest`, `ExerciseEditorViewModelTest` and `InMemoryTrainingPlanRepository`, and the roll-up is seventeen. (ii) The two `ExerciseListScreenWiringTest` methods are still compile failures but no longer one rewrite: the first keeps its row-height assertion.** |
| I19 | Two of the new enumerations contradict themselves about whether a method is rewritten, and both contradictions propagate into the roll-up the revision offers as its structural answer | **Accepted in both halves, each re-verified against the tree.** **(a)** `TrainingPlanListScreen.kt:98` is a genuine `FloatingActionButton` carrying `training_plan_list_add_content_description` (declared `:67`) — structurally identical to the library's own FAB and its instrumented test, confirmed by direct comparison rather than by analogy. CP11 item 5 listed `createFabClickInvokesOnCreateClick` as untouched while stating in the same breath that it carries "the same bottom-bar question CP10 item 6 answers … and answered the same way", and CP10 item 6's answer is a rewrite; the method is now moved into its own rewritten bullet, on identical terms to CP10's, and the roll-up's CP11 row becomes **4 of 11 plus one new**. **(b)** `HistoryScreen.kt`'s filter bullet was headed "Three methods rewritten" and its own body named only `exerciseFilterMenuInvokesOnExerciseFilterChanged` as rewritten, with `sortOrderButtonTogglesBetweenNewestAndOldestFirst` and `showInvalidatedChipInvokesOnShowInvalidatedChanged` surviving because item 1 keeps their chip labels — confirmed directly against `HistoryScreenTest.kt`, all nine methods and line ranges exact. The header is corrected to match the body it always had; the two surviving methods move into the untouched list beside `rowClickInvokesOnSessionClick` and `invalidatedMessageShowsSnackbarAndConsumesIt`, and the roll-up's CP12 row becomes **5 of 9**. The "Methods it changes" column now carries one meaning throughout — CP8's usage, "methods this checkpoint must rewrite or repair" — and the reviewer-challenge list states plainly that the total has now moved three times and each time because the net changed, not because anyone recounted. |
| O18 | One line citation in CP10's untouched list overshoots its method by eight lines | **Accepted.** `searchFieldInputInvokesOnQueryChanged` is `ExerciseListScreenTest.kt:164–177`; `:179–185` is the block comment introducing `theSearchClearButtonAppearsOnlyForANonEmptyQuery`, whose own citation (`:187–201`) was already exact. Corrected in CP10 item 6's untouched list. Graded on round 3's O7 and round 7's O17's precedent — a single-citation correction worth the edit. |

**Where this leaves the pattern.** Round 7 audited the test suite instead of
the prose and found five files two mechanisms reach. Round 8 audited the same
class one level up and found six more, all reachable by a composable-invocation
grep that had simply never been pointed at those six screens. Round 9's I18 is
one order further out again: the six-screen grep revision 9 itself introduced
is closed on one side only, and the JVM file it cannot see by construction is
exactly the kind of thing this plan's own domain-backing test asks for —
non-composable wiring pinned outside the instrumented suite. The structural
answer revision 9 chose — per-checkpoint enumeration, roll-up may only add —
is not replaced; it is finished, by making every enumeration state its own
grep's blind spot the way CP2 item 7 already did.

**Not challenged, a ninth time, and recorded as such:** round 9 again found no
cause to contest the 16-checkpoint shape, CP4's readiness weights, bands and
inversion set, retiring `Exercises` and `Recovery` as tabs, D10/D11, the
back-gesture interception at CP7 together with the replacement of today's
unconfirmed `Abandon` exit, CP8's two named `ActiveWorkoutScreenTest` method
rewrites, CP12 item 6's new home for the invalidate action, CP16 item 8's
sizing, or revision 4's recorded rejection of round 3's O8 sizing conclusion.
Revision 10 changes none of them. **Nine local rounds declining to contest
these is not the user's sign-off**; they, CP14's schema change and its sizing,
and whether the deviation register alone satisfies F1's acceptance criterion 3
remain live for `MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 10 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 1 Optional)

Round 10 re-verified round 9's three applications against the tree at
`7d8ae1b` and found **all three hold as claimed**. Its own two Important
findings and one Optional finding were each validated the same way before
being applied — every file, `@Test` count and line range cited was re-read in
the tree. **All three hold and all three are accepted**, so there are again no
rejections to record.

| # | Finding | Disposition |
|---|---|---|
| I20 | CP10's JVM second pass stops at the one file round 9 named; `ExerciseListViewModelTest` and `ExerciseEditorViewModelTest` are unenumerated, on the one checkpoint that says a route argument and a ViewModel change are in scope | **Accepted in full, every premise re-derived.** The two packages hold exactly three JVM files; `ExerciseListViewModelTest` has 12 `@Test` and `ExerciseEditorViewModelTest` 16, neither invoking a composable; `EXERCISE_NEW` is `"exercises/new"` (`RepFlowDestinations.kt:15`), navigated to bare (`RepFlowNavHost.kt:71`); the editor's mode derives from `EXERCISE_EDIT_ARG` (`ExerciseEditorViewModel.kt:50–51`, `:65`); `Empty` is `data class Empty(val reason)` (`ExerciseListUiState.kt:47–49`) and the query already sits on the UI state (`:15`). **Item 3 now states its decisions** (optional prefill argument that never touches the mode, never overrides a restored draft, and is the `isDirty` baseline; `Empty` keeps its shape). **Item 4 states** the ViewModel input contract and the clear-load rule are unchanged. **Item 6 runs the second pass over both packages** with a mode for every method that reads something CP10 changes: all 12 and all 16 survive, each because of a stated decision, and CP10 adds one list-ViewModel and three editor-ViewModel tests. **Found while applying this, not named by the finding:** item 5's `in N plans` meta has no data behind it today (`ExerciseListItem` has no usage field, `ExerciseListViewModel` takes three dependencies, `TrainingPlanRepository` has no usage query). Item 5 now states what building it takes — no scope change, since the plan already committed to building it — and that reaches `ExerciseListViewModelTest`'s shared field initializer (fixture edit), `rendersContentRows` (a compile failure, rewritten to assert the new meta, so `ExerciseListScreenTest` moves from 5 of 15 to 6 of 15) and `InMemoryTrainingPlanRepository`, the port's only test-tree implementation. CP10's roll-up row moves from 3 files to **6**, the total from fourteen to **seventeen**; Verification strategy, the reviewer-challenge list, CP14 item 9's ordinal and the historical I8/I14/I16/I18 rows carry the correction, the last four as in-line supersessions. |
| I21 | `rowAndFabSizesAreTheDesignsOwn` is rewritten by an instruction that fits only its FAB half, dropping the row-height design transcription | **Accepted in all three parts, verified in `ExerciseListScreenWiringTest.kt`.** The method is two assertions (`:61` row range, `:62` FAB exact), and `ExerciseRowMinHeight` (`ExerciseListScreen.kt:447`, consumed `:329`) is untouched by the FAB's removal. CP10 item 6 now splits the two methods: the first keeps its row-range assertion, drops the FAB half and is renamed; the second is rewritten against the bottom bar's size constant. Item 2 owns `ExerciseRowMinHeight`'s fate: it **survives as the converted row's `heightIn(min = …)`**, taking `2c`'s drawn value within 56–68. Item 1 owns restating `everyTapTargetClearsTheMinimum`'s pill-picker comment (`:74–76`) and adds the converted filter control's height to that method if it is tappable; the badge assertion (`:77`) stays. Verification strategy now says the silent-pass count stays at three **because of** this decision. |
| O19 | CP11 item 5's evidence sentence for the exercise picker is literally false as written | **Accepted.** `grep -rn "training_plan_editor_select_exercise_placeholder" app/src/` returns two hits, `TrainingPlanEditorFormFields.kt:230` and `strings.xml:162`; restricted to `app/src/androidTest/ app/src/test/` it returns none. The sentence now names the two test roots and records the two non-test hits. |

**Not challenged, a tenth time, and recorded as such:** the items round 10's
"Usability concerns" lists, unchanged by revision 11. **Ten local rounds
declining to contest these is not the user's sign-off**, and they remain live
for `MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 11 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 2 Optional)

Round 11 re-verified round 10's three applications and found all three hold.
Its four findings were each validated against the tree at `7d8ae1b` and, for
the design claims, against `RepFlow.dc.html` in the live project (`nRowSheet`
`:1689–1703`, Home's `Finish it` → `nAskFinish` `:749`, the leave sheet's
`Finish and save it now` `:1444`, `nNextExercise`/`nAskFinish` `~:4310–4335`).
**All four hold and all four are accepted**; there are no rejections to
record. No checkpoint, dependency or requirement changes, so the registry and
mapping move to revision 12 with their contents otherwise unchanged.

| # | Finding | Disposition |
|---|---|---|
| I22 | CP7 item 7 calls `Do later` and `Remove` domain-backed; neither is, `Remove` would delete logged sets, and `Skip for today` is a silent deviation | **Accepted; resolved by the user's product decision of 2026-09-30, recorded as the user's.** Verified: `WorkoutSession` has no remove or reorder operation (`WorkoutSession.kt:102–175`), no file in `application/workout/` reorders, removes or skips an exercise, and `LocalWorkoutRepository.update` (`:80–88`) deletes and re-inserts a session's exercise rows. The user's decision: the row sheet renders **none** of its six options, each gets a deviation row with reason `no domain backing`, and no domain or application write path is added. CP7 item 7 now says so; `D1`–`D3` name their row-sheet options and `D13`–`D15` are new for `Do this later`, `Skip for today` and `Remove`. **The decision the user left to the plan:** with no options, the sheet and its `⋮` are not rendered, and CP7 owes a state test that a board row exposes neither. "Preserved invariants" is unchanged, because nothing is added. |
| I23 | Home's `Finish it` is never routed through CP9's finish sheet, so it is an unconfirmed terminal action CP14's toggle does not govern | **Accepted, per the user's direction.** Verified: `ActiveWorkoutScreen.kt:200` is a bare finish `Button`, `complete` is terminal (`WorkoutSession.kt:126–127`), and `Finish it` appeared only in CP5 item 2. CP5 item 2 now says `Finish it` never completes from Home — at CP5 it opens the workout surface. CP9 item 1 lists every finish entry point (board and focus `Finish`, any finish option on CP7's exit confirmation, `Next ›` with nothing unfinished, Home's `Finish it`), makes the sheet's confirm the only caller of `CompleteWorkoutSession`, re-wires Home's `Finish it` as the second half to land, and owes the test that it opens the sheet. Showing Home's sheet over the board rather than over Home is deviation `D16`. CP14 item 4's gate sits on that single request and its assertion drives Home's `Finish it` as well as the board's `Finish`. CP8 item 9 and the execution-order paragraph carry the two pairings. |
| O20 | CP7–CP9 never run the JVM second pass over `presentation/workout/` that revision 11 calls structural | **Accepted, as the short pass the finding suggests.** Verified: `ActiveWorkoutScreenWiringTest` has 8 `@Test` over `restTimerProgress` (`ActiveWorkoutScreen.kt:332`), `setsWithExtraFlag` (`ActiveWorkoutExerciseCard.kt:665`) and `ControlRowMinHeight` (`:710`); `ActiveWorkoutViewModelTest` has 14. One paragraph after CP8's `ActiveWorkoutScreenTest` table covers CP5, CP7, CP8 and CP9: the three declarations stay `internal` and consumed, with `ControlRowMinHeight` as the focus control rows' floor, so all 8 survive; all 14 survive because the conversions feed the ViewModel's entry points and do not change them. No file is edited, so the count stays seventeen and the silent-pass count stays three. |
| O21 | CP10 item 3's "that same blank check" is true only for a trimmed comparison | **Accepted.** Verified at `ExerciseEditorViewModel.kt:271`. The clause is now `state.name.trim() != prefill.trim()` with `""` for an absent prefill. |

**Not challenged, an eleventh time, and recorded as such:** the items round
11's "Usability concerns" lists, unchanged by revision 12. **Eleven local
rounds declining to contest these is not the user's sign-off**, and they
remain live for `MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Round 12 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 1 Important, 1 Optional)

Round 12 re-verified round 11's four applications and found all four hold.
Both new findings were validated against the tree at `7d8ae1b` and against
`RepFlow.dc.html` in the live project (`nExitSheet` `:1437–1447`;
`nLeaveRunning`, `nDiscardSession`, `nAskExit` and `nResumeVisible`
`~:4325–4331`). **Both hold and both are accepted**; there are no rejections
to record. No checkpoint, dependency or requirement changes, so the registry
and mapping move to revision 13 with their contents otherwise unchanged.

| # | Finding | Disposition |
|---|---|---|
| I24 | CP7's `X` and system back open a destructive confirmation, but the design opens a leave sheet whose primary action keeps the session running and goes Home | **Accepted, per the user's direction.** Verified: the design's `X` opens `nExitSheet` with `Leave it running and go Home` (`nLeaveRunning` only sets `nScreen: 'home'`), `Finish and save it now`, `Discard everything logged` and `Keep training`; today's exit is the bare `Abandon` `OutlinedButton` (`ActiveWorkoutScreen.kt:203–205`); and the active session is already a Room `Flow` (`ObserveActiveWorkoutSession`, `LocalWorkoutRepository.kt:44–45`), so leaving needs navigation only. CP7 item 1 now builds the leave sheet, and system back opens it. Leaving calls no use case and lands on Home with the resume card showing. Discard keeps a destructive confirmation dialog before the existing `AbandonWorkoutSession` call, which is new deviation `D17`. `Finish and save it now` is added by CP9 on its single finish request and is not rendered at CP7. CP7 owes three instrumented tests: leave → Home with the session active and the resume card visible; back opens the sheet; discard waits for its confirmation. CP9 item 1's bullet is unconditional, and CP2 item 6, the execution-order pairings and the reviewer-challenge bullet are re-worded to match. No domain or application write path is added. |
| O22 | CP9's Home-path test covers opening the sheet, not that dismissing it returns to Home | **Accepted.** CP9's owed instrumented test now also asserts that dismissing the sheet raised from Home's `Finish it` returns to Home with the session still active. |

**Not challenged, a twelfth time, and recorded as such:** the items round
12's "Usability concerns" lists, unchanged by revision 13 except that item 5's
`Abandon` replacement is now the leave sheet. **Twelve local rounds declining
to contest these is not the user's sign-off**, and they remain live for
`MANUAL_EXTERNAL_PLAN_REVIEW` and for the user.

### Manual external round 1 — `MANUAL_EXTERNAL_PLAN_REVIEW` (Codex), `REVISE` (0 Blocking, 6 Important, 0 Optional)

Every finding was validated against the tree at `7d8ae1b` and, for the
readiness numbers, against `RepFlow.dc.html` in the live project
(`RDY`/`RDY_BANDS`/`readiness()` `:3142–3166`, the component state's
`setAwake: true`, `nHistEx` `:3713–3718`). **All six hold and all six are
accepted**; there are no rejections to record. The user decided X-I1 and
X-I4 on 2026-09-30, and those decisions are recorded as the user's. No
checkpoint, dependency or requirement changes, so the registry and mapping
move to revision 14 with their contents otherwise unchanged. D13–D16 and
D17's confirmation-first stand as the user recorded them.

| # | Finding | Disposition |
|---|---|---|
| X-I1 | "Discard everything logged" calls `AbandonWorkoutSession`, which deletes nothing, while `D17` says discard deletes logged sets | **Accepted; resolved by the user's decision.** Verified: `AbandonWorkoutSession` marks the session `ABANDONED` (`AbandonWorkoutSession.kt:16–24`) and `LocalWorkoutRepository.update` re-inserts its sets (`:80–88`). The user kept today's behaviour exactly — no deletion, no new write path. CP7 item 1's action is now `Abandon this workout`, and its confirmation says the session is marked abandoned, left out of History and keeps its sets. CP5's Home trash uses the same copy. `D17`'s reason is corrected; the copy difference is new `D18`. CP7 owes a Room-backed test that confirming leaves the session `ABANDONED` with every set retained. |
| X-I2 | "Leave it running and go Home" is a plain push, which can leave `Home → board → Home` | **Accepted.** Verified: top-level switches use explicit `popUpTo` (`RepFlowNavHost.kt:53–57`). The leave path now navigates with `popUpTo(HOME)` and `launchSingleTop`, and CP9's "dismiss returns to Home" uses the same removal. Both owed tests now assert that no board entry remains, so Back from Home does not return to the board. |
| X-I3 | CP12 promises a "skipped state" the domain cannot represent | **Accepted.** Verified: `WorkoutExercise` has no status (`WorkoutExercise.kt:20–28`). The design derives `skipped` from zero sets (`RepFlow.dc.html:3713–3718`). CP12 item 2 now renders the honest derived label `No sets logged` (`D20`), asserted in the rewritten `HistoryDetailScreenTest`. |
| X-I4 | CP4 leaves the weights and bands unspecified, and `ObserveReadiness` has no observable source | **Accepted; the numbers come from the user's decision.** CP4 item 1 now transcribes the prototype's engine: weights sleep 1.0, energy 1.2, DOMS 1.0, heavy legs 0.8, heel 0.7, pain 1.5 (the last four inverted as `5 − v`); score `round(100 × Σ(n/5·w)/Σw)`; a pain gate (pain while walking ≥ 3 or heel ≥ 4 → `Protect`); bands ≥ 75 `Ready`, 58–74 `Hold`, 42–57 `Back off`, ≤ 41 `Protect`; drivers where `n ≤ 2`. Item 2 scores today's entry only, and an earlier day's entry never carries forward. Item 3 adds a read-only `observeForDate` Flow to `RecoveryRepository`/`RecoveryEntryDao` (verified: both have suspend lookups only today). `ReadinessScoreTest` pins every threshold from both sides and the gate edges; `ObserveReadinessTest`, a `RecoveryDaoTest` method and CP5's Home ViewModel test cover the refresh path. The band advice line is left out (`D19`), because nothing acts on the band. The roll-up gains `InMemoryRecoveryRepository` and `RecoveryDaoTest`. |
| X-I5 | CP14 tests only the finish-confirmation gate, and nothing specifies how notification and vibration gate independently | **Accepted.** Verified: rest always starts after a successful set (`ActiveWorkoutViewModel.kt:246`), and the receiver posts on one channel with default vibration (`RestTimerExpiredReceiver.kt:28–56`). CP14 item 3 now gates auto-start in `onRecordSet`. It maps notification and vibration to four alerts, using today's channel, a vibration-disabled second channel, a vibrate-only buzz, or nothing. The permission check stays first, so the open decision on denied permission is untouched. Item 4 specifies keep-awake. The defaults preserve today's behaviour: keep-awake is off (`D21`) and the rest are on. New checks cover each switch in both states. `ActiveWorkoutViewModelTest` joins the roll-up. **Superseded as to the channels and the vibration mechanism by round 14's I1 and round 15's I1/I2, and as to when the switches are read by manual-external round 2's X2-I1.** |
| X-I6 | Progress offers load-based metrics for `REPS_ONLY` and `DURATION`, which record no load | **Accepted.** Verified: `ExerciseTrackingType.kt:14–19`, `WorkoutSet.kt:91–101`. CP15 item 6 now defines each metric per tracking type. `REPS_ONLY` and `DURATION` offer `Top set` only (`D22`). An unsupported metric is kept distinct from item 7's fewer-than-two empty state. Item 8's JVM test covers all three types. |

### Round 14 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 1 Important, 6 Optional)

Round 14 verified X-I1–X-I6 and found five fixed and X-I5 partly fixed. Every
finding was validated against the tree at `7d8ae1b`
(`RestTimerExpiredReceiver.kt:16–17`, `:48–56`; `ActiveWorkoutViewModel.kt:246`;
`docs/UX_FLOWS.md:98`) and, for O1, by enumerating all 6^6 inputs. **All seven
hold and all seven are accepted**; there are no rejections to record. The user
decided I1 on 2026-09-30, and that decision is recorded as the user's. No
checkpoint, dependency or requirement changes, so the registry and mapping
move to revision 15 with their contents otherwise unchanged. D13–D16, D17's
confirmation-first, `D18` and the X-I4 numbers stand as the user recorded them.

| # | Finding | Disposition |
|---|---|---|
| I1 | CP14's two-channel split cannot make the Vibrate switch do anything, because `rest_timer` never enabled vibration, and its test checks only a channel ID | **Accepted; resolved by the user's decision.** Verified: `rest_timer` is created without `enableVibration(true)` (`RestTimerExpiredReceiver.kt:48–56`); only the KDoc says "default vibration". CP14 item 3 now makes vibration the receiver's explicit one-shot system-vibrator call, made exactly when Vibrate is on, and relies on no channel's vibration. The notification posts on a new silent, non-vibrating `rest_timer_alert` channel, and the legacy `rest_timer` is deleted, so fresh and upgraded installs behave the same. The four combinations are defined. The permission check stays first for the notification, and vibration still fires when notification permission is denied. The two visible changes are named: rest end now buzzes in normal ringer mode, and the notification loses its sound. The instrumented test asserts through a fake-vibrator seam that vibration fires or not, per combination and permission state, including with a legacy channel present. **Superseded in revision 16 as to the notification channel — see round 15's I1. The user decided on 2026-09-30, after revision 15, that the notification keeps today's sound: it stays on the unchanged `rest_timer` channel, no `rest_timer_alert` is created and nothing is deleted, and the only visible change is vibration in normal ringer mode. The explicit system-vibrator decision in this row stands.** |
| O1 | CP4's "half-up rounding" case cannot be constructed | **Accepted.** CP4 item 1 now says ties cannot occur (`10·S/31` is never `x.5`). The test pins nearest rounding both ways: 75.16 → 75 and 72.58 → 73 (seed with heavy legs 3). |
| O2 | X-I3's `nHistEx` citation is one line off | **Accepted.** `:3712–3717` → `:3713–3718` at all three sites. |
| O3 | CP12 item 2's `No sets logged` assertion is not enumerated in item 6 | **Accepted.** Item 6 names a new `HistoryDetailScreenTest` method, and the roll-up row says "plus one new". The file count is unchanged, so the count stays twenty. |
| O4 | Auto-start off removes the rest timer entirely, and `5c`'s off subtitle promises a manual start | **Accepted.** CP14 item 3 states that off means no rest timer for logged sets. The row keeps `4a`'s `After every logged set` in both states, which is new deviation `D23`. |
| O5 | CP16's `UX_FLOWS.md` rewrite should reconcile `:98`'s "Discard workout" with `D18` | **Accepted.** CP16 item 5 rewrites `:98` to `Abandon this workout`, with confirmation. |
| O6 | Driver sentence casing is unstated | **Accepted.** The prototype's whole-label lowercasing is the intended one ("leg doms 3/5"), and `ReadinessScoreTest` pins that literal. |

### Round 15 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (0 Blocking, 2 Important, 2 Optional)

Every finding was validated against the tree at `7d8ae1b`
(`RestTimerExpiredReceiver.kt:13–21`, `:31–39`, `:48–56`; minSdk 28 in
`app/build.gradle.kts`) and, for O1, against `RepFlow.dc.html:3931` in the
live project. **All four hold and all four are accepted**; there are no
rejections to record. I1 carries the user's decision of 2026-09-30, recorded
as the user's. No checkpoint, dependency or requirement changes, so the
registry and mapping move to revision 16 with their contents otherwise
unchanged. D13–D16, D17's confirmation-first, `D18`, the X-I4 numbers and
the explicit-vibrator decision stand as the user recorded them.

| # | Finding | Disposition |
|---|---|---|
| I1 | CP14 item 3 makes the notification silent, contradicting the user's decision that it keeps today's sound | **Accepted; resolved by the user's decision.** Verified: today's channel uses the three-argument constructor (`RestTimerExpiredReceiver.kt:50–56`), so it has the default sound and no vibration. CP14 item 3 keeps posting on the unchanged `rest_timer` channel; `rest_timer_alert`, `setSound(null, null)` and the legacy-channel deletion are gone. The four combinations are restated with sound. The vibrate-ringer-mode fallback buzz (Vibrate off, and overlapping when Vibrate is on) is documented as a known platform limit the user accepted. One visible change remains, vibration in normal ringer mode, and item 7's parenthetical says so. The owed test asserts a `rest_timer` channel with a non-null sound and `shouldVibrate() == false`, reused rather than deleted. Round 14's I1 row carries a supersession note. |
| I2 | The explicit vibration names no usage, so Android drops it while the app is backgrounded | **Accepted.** The usage is `USAGE_NOTIFICATION`, so silent and ringer mode are respected like any notification. CP14 item 3 states the API 33+ `VibrationAttributes` call and the API 28–32 `AudioAttributes` overload. The fake vibrator records the usage, and the owed test asserts it. |
| O1 | The Notification row's design subtitle promises lock-screen `±15s` and `Skip` actions that do not exist | **Accepted.** The row ships `Shows when rest ends`, registered as new deviation `D24`. |
| O2 | Nothing assigns the receiver's KDoc rewrite | **Accepted.** CP14 item 3 now says CP14 rewrites the class KDoc, whose "default vibration" and "silent no-op" are both wrong after this checkpoint. |

### Round 16 — `LOCAL_MODEL_PLAN_REVIEW`, `APPROVE` (0 Blocking, 0 Important, 2 Optional)

Both Optional notes are applied in revision 17, as one-line fixes alongside
manual-external round 2.

| # | Finding | Disposition |
|---|---|---|
| O1 | The fake-vibrator seam and the `USAGE_NOTIFICATION` constant it records are unnamed | **Accepted.** CP14 item 3 names the seam, the app-level `fun interface RestAlertVibrator`, which takes the usage as an `Int`. It states that the handler passes `AudioAttributes.USAGE_NOTIFICATION` on API 28–32 and `VibrationAttributes.USAGE_NOTIFICATION` on API 33+, and the test asserts the constant for the device's `SDK_INT`. |
| O2 | Manual-external round 1's X-I5 row still describes the superseded two-channel design without a pointer | **Accepted.** The X-I5 row now ends with a supersession pointer to round 14's I1, round 15's I1/I2 and manual-external round 2's X2-I1. |

### Manual external round 2 — `MANUAL_EXTERNAL_PLAN_REVIEW` (Codex), `REVISE` (0 Blocking, 2 Important, 0 Optional)

Round 2 confirmed that all six round 1 findings are resolved. Both new
findings were validated against the tree at `7d8ae1b`. **Both hold and both
are accepted**; there are no rejections to record. Both are technical fixes
with no product decision. No checkpoint, dependency or requirement changes,
so the registry and mapping move to revision 17 with their contents otherwise
unchanged. D13–D16, D17's confirmation-first, `D18`, the X-I4 numbers, the
explicit system-vibrator buzz with `USAGE_NOTIFICATION`, and the notification
keeping today's sound on `rest_timer` stand as recorded.

| # | Finding | Disposition |
|---|---|---|
| X2-I1 | A pending rest alarm keeps the switch values frozen into its extras at scheduling time | **Accepted.** Verified: `ActiveWorkoutRoute.kt:33–45` schedules from the workout route and never cancels when the route leaves composition. `RestTimerAlarmScheduler.kt:41–49` keeps one retained `PendingIntent`. CP7 lets the user leave a running workout for Home, and Home leads to Settings. CP14 item 3 now reads the switches when the alarm fires. The receiver is `@AndroidEntryPoint` and delegates to an injected `RestTimerExpiryHandler`. It uses `goAsync()` and launches on `Dispatchers.IO` (revision 18, local round 17's B1) for one suspend read from the Room-backed `SettingsRepository`, then calls `finish()` in a `finally`. The permission check stays first in the notification half. The alarm is scheduled for every running rest and carries no preference extras, so the "no alarm when both off" and "missing extras" rules are removed. The owed instrumented test changes each switch, in both directions, after scheduling and before expiry. |
| X2-I2 | Home's readiness can stay on yesterday after midnight, because "today" is computed once | **Accepted.** Verified: `RecoveryFutsalViewModel.kt:47` derives today once, and `RecoveryEntryDao.kt:11` filters on a fixed `entry_date`. CP4 item 2 now has Home collect a `Flow<LocalDate>` from the injected `Clock`. The flow emits again at local midnight and re-reads the clock whenever Home resubscribes, because the uptime-based wait stops in deep sleep. Home applies `flatMapLatest` into `ObserveReadiness(date)`. CP5 item 4 owes the test: the same Home ViewModel, with a fake `Clock` on the test scheduler's virtual time, is driven across midnight from a scored day to `Nothing logged today`, and then to the new day's score. **Superseded as to the re-read by manual-external round 3's X3-I1: Home's route pushes the clock's date on every `ON_START`, and a resubscription is not relied on.** |

### Round 17 — `LOCAL_MODEL_PLAN_REVIEW`, `REVISE` (1 Blocking, 0 Important, 4 Optional)

All five findings were validated against the tree at `7d8ae1b`. **All hold
and all are accepted**; there are no rejections to record. All are technical
fixes with no product decision. No checkpoint, dependency or requirement
changes, so the registry and mapping move to revision 18 with their contents
otherwise unchanged. D13–D16, D17's confirmation-first, `D18`, the X-I4
numbers, the explicit system-vibrator buzz with `USAGE_NOTIFICATION`, and the
notification keeping today's sound on `rest_timer` stand as recorded.

| # | Finding | Disposition |
|---|---|---|
| B1 | The receiver's "existing `@IoDispatcher`" cannot be used from `presentation` | **Accepted.** Verified: the qualifier is `com.repflow.app.infrastructure.di.IoDispatcher` (`IoDispatcher.kt:1`, `:12`), and `LayerBoundaryTest.kt:63` forbids every `infrastructure` import in `presentation`. CP14 item 3 now launches on `Dispatchers.IO` directly; the X2-I1 row matches. |
| O1 | Where the production `RestAlertVibrator` binding lives | **Accepted.** Every Hilt module is in `infrastructure/di` today, which `LayerBoundaryTest.kt:104` forbids from importing `presentation`. CP14 item 3 puts the binding in a `@Module` in `presentation/workout/`. |
| O2 | Round 16 O1's second half was not carried | **Accepted.** CP14 item 3 names the production vibrator adapter as an untested pass-through. |
| O3 | The midnight repair assumes lifecycle-aware collection | **Accepted.** CP4 item 2 requires Home's route to use `collectAsStateWithLifecycle`, and CP5 item 1's header date reads the same date flow. |
| O4 | Wording that still names the receiver as the actor | **Accepted.** CP14 item 3 says vibration is the handler's call, and the test checks the channel after the handler runs. |

### Round 18 — `LOCAL_MODEL_PLAN_REVIEW`, `APPROVE` (0 Blocking, 0 Important, 2 Optional)

Both Optional notes are applied in revision 19, as one-line fixes alongside
manual-external round 3.

| # | Finding | Disposition |
|---|---|---|
| O1 | ADR-0003's module snapshot goes stale once CP14 adds a presentation-layer Hilt module | **Accepted.** Verified: `docs/adr/0003-layered-modular-architecture.md:621–624` names only the three `infrastructure/di` modules. CP16 item 7 updates that snapshot. |
| O2 | It is unsettled whether the handler takes the permission result as an argument or the module binds the real check | **Accepted.** CP14 item 3 settles it: the handler takes the result as an argument, the receiver passes its existing check (`RestTimerExpiredReceiver.kt:43–46`), and the module binds only the vibrator. |

### Manual external round 3 — `MANUAL_EXTERNAL_PLAN_REVIEW` (Codex), `REVISE` (0 Blocking, 3 Important, 0 Optional)

Round 3 confirmed X2-I1 resolved. All three findings were validated against
the tree at `7d8ae1b`. **All hold and all are accepted**; there are no
rejections to record. All are technical fixes with no product decision. No
checkpoint, dependency or requirement changes, so the registry and mapping
move to revision 19 with their contents otherwise unchanged. D13–D16, D17's
confirmation-first, `D18`, the X-I4 numbers, the explicit system-vibrator
buzz with `USAGE_NOTIFICATION`, and the notification keeping today's sound on
`rest_timer` stand as recorded.

| # | Finding | Disposition |
|---|---|---|
| X3-I1 | Home can still show yesterday after overnight sleep | **Accepted.** Verified: `Clock.kt:9–10` is wall time, and routes stop upstreams only after `WhileSubscribed(STOP_TIMEOUT_MILLIS)` (`ActiveWorkoutViewModel.kt:154`). A return inside that timeout never restarts the upstream, and the uptime wait is paused. CP4 item 2 now has Home's route call the ViewModel's `onForeground()` on every `ON_START` through `LifecycleEventEffect`, which pushes the clock's date into a `MutableStateFlow`. CP5 item 4 owes the sleep test, in which the subscription never lapses. |
| X3-I2 | Notifications-off can still trigger a notification-permission prompt | **Accepted.** Verified: `ActiveWorkoutRoute.kt:38–43` requests `POST_NOTIFICATIONS` on every rest start. CP14 item 3 now requests it only when Notification is on, through a pure function that is JVM-tested for the off case. The alarm is still scheduled for every rest. |
| X3-I3 | The pending-alarm test bypasses the receiver | **Accepted.** Verified: the scheduler targets `RestTimerExpiredReceiver` (`RestTimerAlarmScheduler.kt:41–48`), whose `onReceive` is the entry point (`RestTimerExpiredReceiver.kt:24–41`). CP14 item 3 adds an instrumented test in the real Hilt graph. It changes Notification after scheduling, sends the scheduled `PendingIntent` to the real receiver, and asserts that the notification is posted. |

### Round 19 — `LOCAL_MODEL_PLAN_REVIEW`, `APPROVE` (0 Blocking, 0 Important, 4 Optional)

All four Optional notes are applied in revision 20, alongside
manual-external round 4.

| # | Finding | Disposition |
|---|---|---|
| O1 | The route half of the Home date refresh and of the prompt gate is stated, not asserted | **Accepted.** CP5 item 4 adds an instrumented `HomeRoute` test that crosses midnight through a real `CREATED` → `RESUMED` cycle. For the prompt, a route-level test would need `ActiveWorkoutViewModel`'s eighteen dependencies built by hand (`ActiveWorkoutViewModel.kt:68–89`) or a Hilt test dependency, so CP14 item 3 moves the prompt into `RestTimerPermissionPromptEffect` and tests that with `createComposeRule()`. The route's single call into it stays an untested pass-through. |
| O2 | The prompt could fire before the switch has loaded | **Accepted.** CP14 item 3: the switch is `null` until `SettingsRepository` first emits, and `null` means no prompt. The open-decisions row notes that the prompt now follows the switch. |
| O3 | The receiver test's mechanics | **Accepted.** CP14 item 3: wait until the switch shows the persisted value, grant only on `SDK_INT >= 33`, and cancel `NOTIFICATION_ID` before sending. The lookup seam is X4-O1. |
| O4 | Bookkeeping left on superseded mechanisms | **Accepted in part.** The X2-I2 row carries a supersession pointer, and the bundle's `REVIEW_REQUEST.md` is corrected. The artifacts JSON's stale `docs/adr/` exclusion reason is left unchanged, because `/apply-plan-review` edits only the plan document. It changes no classification: the implementation stage protects `docs/adr/`. |

### Manual external round 4 — `MANUAL_EXTERNAL_PLAN_REVIEW` (Codex), `REVISE` (0 Blocking, 1 Important, 1 Optional)

Round 4 confirmed X3-I1 and X3-I2 resolved. Both findings were validated
against the tree at `7d8ae1b`. **Both hold and both are accepted**; there are
no rejections to record. Both are technical fixes with no product decision.
No checkpoint, dependency or requirement changes, so the registry and mapping
move to revision 20 with their contents otherwise unchanged. D13–D16, D17's
confirmation-first, `D18`, the X-I4 numbers, the explicit system-vibrator
buzz with `USAGE_NOTIFICATION`, and the notification keeping today's sound on
`rest_timer` stand as recorded.

| # | Finding | Disposition |
|---|---|---|
| X4-I1 | The receiver test cannot detect missing handler delegation | **Accepted.** Verified: `RestTimerExpiredReceiver.kt:28–40` posts whenever permission is granted, without reading any switch, so off-then-on passes today. CP14 item 3 adds the on-then-off case, with permission granted, and asserts that no notification is posted. The positive case stays, so a receiver that does nothing also fails. |
| X4-O1 | The test's pending-intent lookup is unnamed | **Accepted.** Verified: `REQUEST_CODE` and `pendingIntent` are private (`RestTimerAlarmScheduler.kt:17`, `:41–49`). CP14 item 3 names `RestTimerAlarmScheduler.scheduledPendingIntent(context)`, `internal` and `@VisibleForTesting`, which rebuilds the intent with `FLAG_NO_CREATE or FLAG_IMMUTABLE`. |
