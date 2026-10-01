# repflow-redesign-visual-foundation-remediation-1 — Design inventory and deviation register

CP1's artefact (execution plan revision 20, "CP1 — Design inventory and
deviation register"). It is the baseline every later checkpoint is checked
against, and the register F1's acceptance criterion 3 ("any material
remaining deviation is explicitly identified, justified, and accepted") is
graded on. **No production code changed in CP1.**

How to use it: a checkpoint that builds a screen starts at that screen's
section in §3, **re-reads the named artboard from the live project** (this
document points at the design; it does not replace it), builds what the
"Interactive" list names, keeps or rewrites exactly the tests the "Tests"
entry names, and appends to the deviation register (§4) rather than
inventing its own format. Items in §5 are *not* decided here: each names the
checkpoint that must either build the element or add a register row for it.

---

## 1. Source, and how it was read

- **Project** `Repflow mobile app design`
  (`9e1d47b2-48ae-45bc-a101-c59c37c49550`), file **`RepFlow.dc.html`**
  (385 337 bytes, 4 723 lines, etag `1786483024007421` on 2026-10-01).
- **Read in full on 2026-10-01**: every artboard's markup (`:38–3040`) *and*
  the prototype's component script (`:3044–4720`), which is where the copy,
  option lists, labels, formulas and state rules live. The file was fetched
  from the live project and compared with an earlier `read_file` copy: the
  only difference was the preview host's injected header, so every `:NNNN`
  below is a line of the live file, and the plan's existing citations
  (`:1437`, `:3142–3166`, `:3713–3718`, `:3931`) resolve to the same lines.
- The design-system bundle (`_ds/nocturne-…/styles.css`) was not re-read: it
  was the parent milestone's input and is already transcribed into the
  Compose tokens, and `6b` is the authoritative Compose token source.
- **Turn precedence.** The canvas has six turns; turn 6 is the newest and sits
  at the top of the file. Where two turns draw the same surface the newer one
  wins: `6b` > `5a`–`5d` > `4a` > `3a`–`3d` > `2a`–`2c` > `1a`–`1d`. Each
  place this rule decides something is called out in the screen's section.
  Two conflicts it does **not** settle on its own, because the plan text
  follows the older turn, are §5's `O11` and `O12`.

## 2. Global values (`6b`, `:253–333`)

These bind every section below; a section repeats a value only where its
artboard states something more specific.

| Group | Value (source) |
|---|---|
| Colour | background `#161826`, surface `#232532`, control `#292b31`, accent `#9184d9`, accent surface `#2b2741` / `#5d5294`, destructive `oklch(.72 .13 25)` (`:261–266`); text primary `#e9e9ed`, 55% secondary, 45% tertiary and labels; hairline `#3f424d`; divider `rgba(233,233,237,.09)` (`:268`) |
| Type | display 32/500 · title 25/500 · numeric 32/500 tabular · body 15/400 · meta 12.5/400 · label 11/500 uppercase, tracking .09em (`:272–277`); Inter, never heavier than 600; every compared number tabular (`:279`) |
| Buttons | 56 primary (radius 12), 48 secondary (radius 10), 44 minimum (radius 8) (`:284–287`) |
| Stepper | 48×48 −/+ on `control` with hairline; the value is a button at numeric 32/500 that **opens the keypad**; step from the exercise's load increment (`:290–296`) |
| Scale row | cells 0–5 (and RPE), min-height 44, radius 8, gap 5; selected `rgba(145,132,217,.22)` + `#796cbf`; **labelled at both ends** (`:299–308`) |
| Status chips | 12px, padding 4/9, pill; every tone carries an icon or a word: `4/4 sets` (fill check), `2/4 sets` (dot-outline), `3 sets` (circle), `skipped` (prohibit), `up next` (uppercase word) (`:311–318`) |
| Layout | screen padding 16 · card padding 14–18 · gaps 6/8/10/12 · radii 8 controls, 10–12 cards and buttons, 14–16 sheets · rows 56–68 · tap target ≥ 44 · elevation = hairline + ambient shadow · **one primary action per screen, pinned to a bottom bar** · **sheets for choices, dialogs only for destructive confirmation** · **bottom nav on the four top-level destinations only** · **workout mode replaces the nav — an X or Finish is the only way out** (`:324–331`) |

**Recurring structures**, drawn identically wherever they appear, so CP3's
primitives own them:

| Structure | Values (first occurrence) | CP3 primitive |
|---|---|---|
| Sub-screen top bar | padding 10/8/6/4; 44×44 back (`ph-arrow-left`, 21px); title 17/500 (`:47–50`) | `RepFlowScreenScaffold` |
| Top-level title | 25/500, tracking −.015em, under padding 18/16/0 (`:349–350`) | `RepFlowScreenScaffold` |
| Pinned bottom bar | padding 10–12/16/16, top border `1px rgba(233,233,237,.12)`, fill `#1b1d2b`; primary 52–56 (`:179–185`, `:1155–1157`) | `RepFlowBottomActionBar` |
| Bottom sheet | fill `#232532`, top radius 16, shadow `0 -12px 40px`, grabber 32×4 radius 2 `rgba(233,233,237,.25)`, scrim `rgba(15,17,28,.6–.66)` (`:1407–1410`) | `RepFlowSheet` |
| Section label | 11/500 uppercase, 45% alpha, tracking .09em (`:53`) | `RepFlowSectionLabel` |
| List row | 56–68 tall, title 15–15.5/500 + meta 12.5, trailing caret at 30%, divider .07–.09 (`:958`) | `RepFlowListRow` |
| Stat tile | radius 12, surface + hairline, padding 12; label 11 uppercase; figure 19–21/500 tabular (`:980`) | `RepFlowStatTile` / `RepFlowStatRow` |
| Keypad | sheet; title + value 30/500; 3-column grid `1 2 3 4 5 6 7 8 9 . 0 ⌫`, keys 56 tall radius 10 on `control`; **Cancel** + **Set** at flex 1 : 2 (`:2826–2843`, keys `:4616`) | `RepFlowNumericKeypad` |
| Destructive dialog | radius 16, `#232532`, ring `#9397ab`, max-width 320; the safe action first as primary, the destructive one outlined in the destructive tone with an icon (`:1942–1954`) | Material `AlertDialog`, kept for destructive confirmation only |

---

## 3. Per-screen inventory

Each section lists: artboards · regions top-to-bottom · interactive elements
· Compose owner after conversion · existing tests that read what the
conversion changes, each marked **survives** or **rewritten** · register
rows. An owner that does not exist yet is created by the named checkpoint;
where the plan does not name a package, that checkpoint fixes it.

**How the tests column was produced** (by grep, not recall):
instrumented screen tests per surface with
`grep -rlE '<Composable>\(' app/src/androidTest app/src/test`; the
destination set with `grep -rl 'createAndroidComposeRule<MainActivity>'
app/src/` (exactly 3 files) and
`grep -rn exercise_list_backup_content_description app/src/` (those 3 plus
the destination entry `RepFlowDestinations.kt:80`); the JVM second pass by
listing each converted package's `app/src/test` directory; method counts are
`grep -c '@Test'`. The roll-up is §6.

### 3.1 App shell — bottom navigation and workout-mode suppression (CP2)

- **Artboards:** `4a` nav (`:1114–1123`; `nTabs` `:3590–3603`); drawn
  statically in `5a` (`:406`), `3a` (`:1881`), `1a` (`:2585`), `1d` light
  (`:3029`).
- **Regions:** bar top border `1px rgba(.12)`, fill `#1b1d2b`, padding
  6/4/4; four equal cells, min-height 56, gap 3; icon 19px inside a pill
  padding 3/18 — selected pill `rgba(145,132,217,.20)` with icon `#d2cefd`;
  label 11.5 (selected `#e9e9ed`, otherwise 60%).
- **Destinations, in order:** Home `ph-house`, Plans `ph-list-checks`,
  History `ph-clock-counter-clockwise`, Progress `ph-chart-line-up`; the
  selected one uses the **fill** weight (`'ph-fill '` vs `'ph '`, `:3597`).
- **Suppression:** `nShowNav = nTab !== 'settings'` (`:3589`) — Settings is
  not a tab and shows no nav; preview, board, focus and done render outside
  the tab host entirely (`:1127`, `:1161`, `:1240`, `:1364`).
- **Interactive:** each cell → its destination; Home's gear → Settings
  (`:735`).
- **Owner:** `presentation/navigation/RepFlowDestinations.kt`,
  `RepFlowBottomNavigationBar.kt`, `RepFlowNavHost.kt`;
  `designsystem/icons/RepFlowIcons.kt`; six new `ic_ph_*` drawables; the
  three `…Placeholder` scaffolds (CP2 item 4).
- **Tests:**

| File | Methods | Verdict |
|---|---|---|
| `MainActivityNavHostSmokeTest` (androidTest, 5) | all five — they assert the six-tab IA and the `EXERCISES` start destination | **rewritten** (CP2 item 7a): four tabs, `HOME` start, the four inward walks, nav absent on the workout route |
| `RepFlowIconsTest` (JVM, 5) | `everyTopLevelDestinationGetsItsOwnGlyph` (compile failure), `bundledIconSetIsTheGlyphSetThePlanEnumerates` (23 → 29 literal), `designConfirmedNavGlyphsAreTheOnesTheDesignNames` | **rewritten** (CP2 item 2); the other two survive |
| `RepFlowBottomNavigationBarTest` (JVM, 5) | `everyTopLevelDestinationCarriesItsOwnNavGlyph` (compile failure), `topLevelDestinationsKeepTheirRoutesAndOrder` (and its KDoc `:88`) | **rewritten** (CP2 item 7b); the three colour tests survive |
| `BackupRouteSafCancellationTest`, `BackupRouteUnreadableRestoreFileTest` (androidTest, 1 each) | the opening navigation (`:51–54`, `:53–56`) clicks the retired `BACKUP` tab | **re-routed** through the gear → Settings placeholder → `Backup` (CP2 item 7c); their subjects survive — see also §3.17 |

- **Register:** none for the bar itself. Documentation CP2 rewrites in the
  same edit: `RepFlowIcons.Nav`'s KDoc (`RepFlowIcons.kt:131–153`).

### 3.2 Home (CP5)

- **Artboards:** `4a` Home tab (`:728–822`, newest); `1a` Home
  (`:2504–2595`) and `1d` light Home with its empty, error and first-run
  states (`:2978–3036`) where `4a` is silent.
- **Regions** (padding 18/16/8):
  1. Header, margin-bottom 18: date as an 11px uppercase label (`Tuesday, 11
     Aug`) over the greeting at 25/500 (`Ready when you are`, `:733`); a
     44×44 round gear button on `#232532` with a hairline ring, `ph-gear-six`
     20px (`:735`).
  2. **Resume card** (only with an active session, `:738–753`): radius 14,
     `#2b2741`, ring `#5d5294`, padding 14/16; `ph-fill ph-record` +
     `<title> — still running` + meta `mm:ss elapsed · N sets logged`
     (`:4325`); `Resume` (primary, 48), `Finish it` (accent outline, 48),
     trash (destructive, 48, `aria-label` "Discard workout") (`:748–750`).
  3. **Start card** (`:754–761`): radius 14, gradient `160deg #262a60 →
     #232532`, ring `#423a6a`, padding 18; label `Today · day 2 of 4` (`D4`);
     plan-day title 25/500; meta `N exercises · N working sets · ~N min`
     (`:3551–3555`, `O1`); `Start workout` 56 primary with `ph-fill ph-play`
     (`:759`); `Train something else ›` 48 text button (`:760`).
  4. **Recovery card** (`:763–783`): radius 14, surface, padding 14/16; label
     `Recovery today` + `Log ›` (36 tall, `:766`); a score button — score
     38/500 tabular in the band colour, band label 14/500, "readiness score"
     12.5, `What it changes ›`; a 4px bar at the score's percentage; the
     readiness line at 13; a chip row `Sleep N · Energy N · DOMS N` (`:779`,
     `:3773`). Empty state (`1d`, `:3002–3008`): `ph-moon-stars`, "Nothing
     logged today. Sleep and leg soreness shape the load suggestions.", `Log
     recovery — 20 seconds`.
  5. Deload pattern card and deload-on banner (`:785–797`) — `D6`.
  6. Two-up grid, gap 12: `This week` bars with `2 of 4 sessions` (`:801`,
     `D9`); `Last workout` — name 15/500, `Mon · 61 min`, accent `2 load
     increases` (`:814`).
  7. `1d` only: error card "Couldn't load your history" + `Retry`
     (`:3010–3018`); first-run card "Build a plan, or just start an empty
     workout and add exercises as you go." + `Create a plan` / `Empty
     workout` (`:3020–3028`).
- **Start sheet** (`nStartSheet`, `:1407–1435`): label `From <plan>`; one
  60-tall row per plan day (`:1412`, `D4`); a divider; `Empty workout`
  (`ph-lightning`, "No plan — add exercises as you go", `:1422`); `A
  different plan ›` (`O2`).
- **Interactive:** gear → Settings; `Resume` → workout; `Finish it` → the
  finish sheet (`D16`); trash → abandon (`D17`, `D18`); `Start workout` →
  start; `Train something else` → start sheet; `Log ›` → recovery entry
  (`D26`); score → readiness sheet (§3.3); chips → recovery entry.
- **Owner:** new `presentation/home/` (Route, Screen, ViewModel, UiState);
  replaces CP2's `HomePlaceholder`. **Built by CP5:** `HomeRoute`,
  `HomeScreen` (header, overlays, abandon confirmation), `HomeResumeCard`,
  `HomeStartCard` (start card, `1d` first-run card, start sheet),
  `HomeRecoveryCard`, `HomeCards` (`Last workout`, `1d` history error),
  `HomeViewModel`/`HomeUiState`/`HomeFormatting`; the history read model
  `application/history/ObserveRecentTraining`. `O1` → `D44`, `O2` → `D45`.
- **Tests:** none exist — there is no Home today. CP5 item 4 owes new ones
  (ViewModel reactivity, the midnight case, the sleep/`onForeground` case,
  and the instrumented `HomeRoute` lifecycle test). The start path's existing
  coverage is `ActiveWorkoutViewModelTest`'s seven start methods
  (`:150–:319`, `:439`): **survives** if CP5's sheet calls the existing
  `onStartWorkout`; a CP5 that moves the start path adds that file to the
  roll-up (plan, CP8's JVM second pass).
- **Register:** `D4`, `D6`, `D9`, `D16`, `D17`, `D18`, `D19`, `D26`,
  `D44`–`D48`; `O1` and `O2` closed by CP5 (`D44`, `D45`).

### 3.3 Readiness detail sheet (CP4; hosted by Home, CP5)

- **Artboard:** `4a` `nRdySheet` (`:1489–1547`); engine `:3142–3166`;
  factors and drivers `:3797–3815`.
- **Regions:** a tall sheet (top inset 56), padding 4/16/16. Title `How today
  was adjusted` 22/500 (`:1494`); line "Your check-in sets one score. The
  score changes what the app proposes — never what you are allowed to do.";
  score 44/500 in the band colour + band label 15/500 + "out of 100"; 4px bar;
  driver sentence at 13; label `The inputs and what they weigh`; six rows
  (`:1508`): label 13.5, five 7px dots filled to `n`, weight `×1.2` at 12px
  tabular; the gate note "Pain while walking carries the most weight and acts
  as a gate…" (`:1519`); label `What it proposes, exercise by exercise` and
  its decision rows (`:1521–1536`, `D29`); bottom bar: the override button
  (`:1538`, `D29`) or "Nothing held back — there is nothing to override."
  (`D29`), then `Close` at 48.
- **Band colours** (`:3153–3158`): Ready `#b5abfc`, Hold `oklch(.80 .10 85)`,
  Back off `oklch(.76 .12 55)`, Protect `oklch(.72 .13 25)`. Every band is
  also a word (`6b`, never colour alone).
- **Interactive:** `Close`; the override (`D29`).
- **Owner:** `domain/recovery/ReadinessScore.kt`,
  `application/recovery/ObserveReadiness`; the sheet composable under
  `presentation/home/` (CP4 builds the derivation, CP5 hosts the sheet).
  **Built by CP4:** `presentation/home/ReadinessSheet.kt`
  (`ReadinessSheet` = `ReadinessDetail` inside `RepFlowSheet`) and
  `ReadinessBandStyle.kt` (band word + colour per theme); no host yet.
- **Tests:** `InMemoryRecoveryRepository` (JVM fixture) gains
  `observeForDate` — **edited, no assertion changes**; `RecoveryDaoTest`
  (androidTest, 4) — **survives**, plus one new method (CP4 item 5). New
  `ReadinessScoreTest` and `ObserveReadinessTest`.
- **Register:** `D19`, `D29`, `D41`, `D42`, `D43`.

### 3.4 Recovery entry (CP13)

- **Artboards:** `3c` (`:2017–2092`), labelled "reached from Home 'Log ›'";
  `4a` also draws a check-in **sheet** with the six scales only
  (`nRecSheet`, `:1790–1814`) — `D26`.
- **Regions (`3c`):** top bar `Recovery` with a trailing `History` text action
  (13.5 accent, 44 tall); a date row (radius 10, surface,
  `ph-calendar-blank`, `Today · 11 Aug 2026`, `Change`); six scale rows
  (`:2034`), each a 14.5/500 label with its end labels `low → high` (12px)
  on the right, then cells 0–5 at min-height 46, radius 8, gap 6; label
  `Futsal`; two 52-tall toggles `Played in last 24h` / `Playing in next 24h`
  (`:2050`); the futsal block, shown only while "played" is on: two steppers
  `Minutes` (±5) and `Session RPE` (±1, 0–10) with 44 buttons and the value
  as a button ("Type minutes" / "Type session RPE"), the hint "Step with −/+,
  or tap a number to type it." (`:2074`) and `Training load <N> — minutes ×
  RPE` (`:2076`); label `Notes` + a 64-tall multiline field (`:2082`); a
  pinned `Save entry` that becomes `Saved` with a check (`:2086–2088`).
- **End labels** (`SCALES`, `:3126–3133`): Sleep quality Terrible → Great;
  Energy Flat → Fresh; Leg DOMS, Heel stiffness, Pain while walking, Heavy
  legs None → Severe (inverse). These are the polarity CP4 normalizes (CP13
  item 5).
- **Interactive:** back; `History` → recovery history; `Change` → date
  picker (the existing past-date entry from Milestone 8); scale cells; futsal
  toggles; steppers and keypad; notes; save.
- **Owner:** `presentation/recovery/RecoveryFutsalScreen.kt` (Route and
  ViewModel contract unchanged).
- **Tests:** `grep -rlE 'RecoveryFutsalScreen\(|RecoveryHistoryScreen\('` over
  both test trees returns **nothing**. `RecoveryFutsalViewModelTest` (JVM, 9)
  — **survives** (it pins persisted values, not the input affordance). CP13
  item 6 owes new tests (polarity and end labels; futsal inputs).
- **Register:** `D26`; open `O10`.

### 3.5 Recovery history (CP13)

- **Artboard:** `3d` (`:2095–2203`).
- **Regions:** top bar `Recovery history`; a chart card (radius 14, padding
  14/14/10): label `Sleep & energy · 14 days` and `avg 3.6 / 3.1`; a
  selected-day readout (radius 9, 5% fill, `:2110`): the day, Sleep N/5 (solid
  accent swatch), Energy N/5 (dashed `#b2b6ca`), a futsal icon; Y axis 5/3/1;
  sleep line in accent at 2.4, energy dashed at 1.8, futsal dots on the
  baseline; 20-wide tap targets per day; footer "Tap any day for its values"
  plus the futsal legend. An insight card on accent surface (`:2160`): "Leg
  DOMS runs 1.4 higher in the two days after futsal. Progression on leg work
  is held back automatically on those days." Label `Entries` — rows `Today |
  Sleep 4 · Energy 3 · DOMS 2 | ⚽`; label `Futsal sessions` (`:2187`) —
  rows `10 Aug | 50 min · RPE 8 | load 400`.
- **Interactive:** back; tap a day to select it.
- **Owner:** `presentation/recovery/RecoveryHistoryScreen.kt`; chart in
  Compose `Canvas` (CP13 item 4).
- **Tests:** no instrumented test (grep above). `RecoveryHistoryViewModelTest`
  (JVM, 2) — **survives**.
- **Register:** open `O10`.

### 3.6 Workout board (CP7)

- **Artboard:** `4a` `nBoard` (`:1161–1238`), with turn 6's rest strip.
- **Regions:**
  1. Top bar: a 44 `X` ("Leave workout", `:1164`); the title button — title
     16/500 (with a pencil when ad hoc, `D30`) over `ph-timer` and the
     elapsed `mm:ss` at 12px (`:1165`); `Finish`, 40 tall, accent outline.
  2. Progress line, 12.5 tabular: `N of M exercises · S/T sets` (`:1177`,
     `:3957`).
  3. Board rows (`:1188–1215`), min-height 64, divider 9%: name 15.5/500, an
     `up next` word chip, `moved` (`D13`), a superset tag (`D1`); the status
     chip (§2: `N/T sets`, `T sets`, `skipped`; `statusOf`, `:3515`); a note
     line (`D3`); a readiness adjustment chip (`:1209`, `D28`); a trailing 44
     `⋮` (`:1213`; `D13`–`D15`, CP7 item 7).
  4. Empty board (`:1180–1186`): radius 14 hairline card, `ph-barbell` 28,
     `Empty workout` 18/500, "The clock is already running. Add whatever
     machine is free — you can keep adding as you go."
  5. `Add exercise`, 52, accent outline (`:1217`).
  6. Rest strip (`:1220–1237`): radius 14, margin 0/12/10, ring `#5d5294`
     (lit to `#9184d9` on `#2b2741` when done); time 24/500 tabular (`Rest
     done` at zero); "Resting — <exercise>" / "Next set is ready"; a 4px
     progress bar; a 40 dismiss `X` (`:1228`); then `−15s` / `+15s` / `Skip
     rest` (primary), each 44 (`O5`).
- **Sheets:** the leave sheet `nExitSheet` (`:1437–1448`) — title `Leave this
  workout?` 19/500, the line "Leaving is not finishing — the session stays
  open and the clock keeps running from its start time.", `Leave it running
  and go Home` (accent outline, `ph-house`), `Finish and save it now` (CP9),
  `Discard everything logged` (destructive; `D17`, `D18`), `Keep training`.
  The picker sheet `nPickSheet` (`:1705–1734`): tall (top inset 72); a 48
  search field "Search exercises or muscle group" (`D8`) with a clear button
  (`:1713`); `Create a new exercise`, 48, accent outline (`O4`); the empty
  line "Nothing matches. Create it as a new exercise instead." (`:1720`);
  60-tall rows with name, meta and `ph-plus`. Not built: the row sheet
  `nRowSheet` (`:1689–1703`; `D1`–`D3`, `D13`–`D15`), the swap sheet
  (`:1765–1788`, `D2`), the note sheet (`:1450–1459`, `D3`), the rename sheet
  (`:1673–1687`, `D30`); the new-exercise sheet (`:1612–1633`) is `O4` with
  `D8`.
- **Interactive:** `X` → leave sheet; system back → leave sheet (CP7 item
  1); title → rename (`D30`); `Finish` → finish sheet (CP9); a row → focus
  mode; `Add exercise` → picker sheet, which carries CP6's recommendation row
  and its `Why ›` (CP7 item 6); the rest strip's controls (`O5`).
- **Owner:** `presentation/workout/` — a board composable replacing the top
  half of `ActiveWorkoutScreen.kt`; `ActiveWorkoutRoute.kt`.
- **Tests:** `ActiveWorkoutScreenTest` (androidTest, 14) — the whole file is
  accounted for in §3.7. JVM second pass: `ActiveWorkoutScreenWiringTest` (8)
  **survives** (`restTimerProgress`, `setsWithExtraFlag` and
  `ControlRowMinHeight` stay `internal` and consumed);
  `ActiveWorkoutViewModelTest` (14) **survives** until CP14 (§3.16). New
  CP7 tests: leave lands on Home with no board entry on the back stack; back
  in workout mode opens the sheet; abandon happens only after its
  confirmation; the persisted `ABANDONED` session keeps its sets; a row has
  no overflow trigger.
- **Register:** `D1`–`D3`, `D8`, `D13`–`D15`, `D17`, `D18`, `D28`, `D30`;
  CP7 added `D55`–`D59` and closed `O4` (`D56`) and `O5` (`D58`).
- **Built (CP7):** the board in `WorkoutBoard.kt` (+ `WorkoutBoardModel.kt`
  for the row/progress/search rules), the leave and picker sheets in
  `WorkoutSheets.kt`, the shared `AbandonWorkoutDialog.kt`, and the rest
  strip restyled in place in `ActiveWorkoutScreen.kt`. A row opens that
  exercise's set entry, which is the existing card under a back-to-board bar
  until CP8 converts it. `ActiveWorkoutScreenTest`'s helper now opens the
  first exercise's set entry by default (`openFirstExercise`) so its
  fourteen set-entry methods reach the surface they always tested, and
  CP6's picker method renders the board; CP7 adds three board methods (18
  in all). The fourteen set-entry methods are otherwise unchanged and stay
  CP8's per §3.7.

### 3.7 Workout focus mode (CP8)

- **Artboard:** `4a` `nFocus` (`:1240–1362`); the keypad comes from `1a`
  (`:2826–2843`). `6b` makes the keypad normative ("the value itself is a
  button — it opens the keypad"), although `4a`'s own focus steppers draw a
  plain value — `6b` is the newer turn and wins.
- **Regions:**
  1. Top bar: `Board` (`ph-list-bullets`, 44 tall, `:1243`), a centred
     `ph-timer` with the elapsed time, `Finish` as accent text.
  2. Header: label `Exercise N of M` (`:1248`, `:4116`); the name at 26/500;
     `X of Y sets done` at 13 tabular (`:1250`, `:4117`).
  3. Superset hint (`:1252–1258`, `D1`).
  4. Set rows (`:1260–1270`; `nFocusRows` `:4118–4141`), min-height 48: a
     26px status disc (logged: fill check on accent tint; warm-up: `ph-fire`
     on a hairline; pending target: `ph-circle` with the text `not logged`),
     an index (`W` for warm-ups, otherwise the working-set number), the
     summary at 14.5 tabular (`82.5 kg × 8 · RPE 8 · <pain> · <technique> ·
     warm-up · edited`), a pencil on logged rows (`D31`).
  5. `Last: <set>` hint at 12.5 and `Undo last` at 36 (`:1272–1276`).
  6. Suggestion strip (`:1278–1284`; `nSugLabel` `:4161–4174`) — `D27`.
  7. Two stepper cards (radius 12, surface, padding 12/10, `:1286–1306`):
     `Weight` (Weight & reps only, `:1287`) with 44 −/+, value 24/500 and the
     caption `kg · 2.5 steps` (`:4207`); `Reps` or `Seconds` with the caption
     `target 6–8` or `seconds held` (`nRepTarget`, `:4142`).
  8. Detail disclosure, 48 (`:1310–1337`): `ph-sliders`, the summary `RPE 8 ·
     pain: niggle · grinder` or "RPE, pain, technique" (`:4179`), a caret;
     it reveals `Effort (RPE)`, `Pain` and `Technique` option rows (`:1319`,
     `:1325`, `:1331`; `D10`, `D11`) and the line "Optional. Whatever you set
     applies to the next set you log, then clears." (`O6`).
  9. Type note (`:1339–1341`, `:4209`): `Timed hold — no load recorded for
     this one.` / `Body-weight exercise — reps only.`
  10. Warm-up chip, 44, `ph-fire`, `Warm-up set` (`:1344`), and its hint
      "Logs outside the set count, 60s rest." / "Working set — Ns rest after
      it." (`:4260`, `O6`).
  11. Slim rest strip (`:1348–1355`): time 22/500, bar, `+15s`, `Skip`
      (`O5`).
  12. Pinned bar (`:1357–1360`): `Log set` / `Log warm-up`, 56 primary with a
      fill check (`:1358`), and `Next ›`, 56 neutral outline (`:1359`).
- **Sheets:** the keypad (§2); the set-edit sheet `nSetEditOpen`
  (`:1549–1581`) — `D31`; the rest-complete banner `nAlertOpen`
  (`:1661–1671`, `:4250–4256`) — `O5`.
- **Interactive:** `Board` → board; `Finish` → finish sheet (CP9); a set-row
  pencil → edit (the last set only, `D31`); `Undo last`; steppers −/+ (step
  = the exercise's load increment, CP8 item 6); the value → keypad; the
  disclosure; the scale rows; the warm-up chip; `Log set`; `Next ›` (on the
  last unfinished exercise → board with the finish sheet, CP9); `Why ›` →
  CP6's recommendation screen (`D27`, CP8 item 5).
- **Also built here, though drawn in `2b`:** technique notes from
  `Exercise.instructions` ("Technique notes — shown during the workout",
  `:2411`) — CP8 item 11.
- **Owner:** `presentation/workout/` — a focus composable replacing
  `ActiveWorkoutExerciseCard.kt`'s flat card; `ActiveWorkoutUiState.kt`
  (`ActiveExerciseUi` gains `defaultLoadIncrement` and `instructions`, both
  defaulted); CP3's `RepFlowStepper`, `RepFlowNumericKeypad` and scale row.
- **Tests:** `ActiveWorkoutScreenTest` (androidTest, 14), every method
  (CP8's table, re-checked against the file):

| `@Test` | Method | Verdict |
|---|---|---|
| `:106` | `weightAndRepsExerciseShowsLoadAndRepsFieldsButNotDuration` | survives (labels carried onto the stepper captions) |
| `:115` | `repsOnlyExerciseShowsOnlyTheRepsField` | survives (same) |
| `:124` | `durationExerciseShowsOnlyTheDurationField` | survives (same) |
| `:133` | `aDurationSetSummaryShowsItsDurationNotAMisleadingZeroLoadRow` | survives (summary formats carried) |
| `:142` | `aWeightAndRepsSetWithoutARecordedLoadShowsTheRepsOnlyFormatInsteadOfAZeroLoad` | survives (same) |
| `:151` | `aPlannedExerciseShowsWarmupAndWorkingProgress` | **rewritten** against the `X of Y sets done` header |
| `:167` | `everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth` | **rewritten** — the guard is retargeted onto the set rows |
| `:196` | `anAdHocExerciseShowsNoPlannedTargetSummary` | **rewritten** (silent-pass risk) |
| `:205` | `aSetBeyondThePlannedWorkingCountIsMarkedExtra` | survives (the `extra` marker is an invariant) |
| `:239` | `theSetDetailFieldsAreHiddenUntilTheDisclosureIsExpanded` | survives |
| `:249` | `expandingTheDisclosureRevealsAllThreeOptionalFields` | survives |
| `:260` | `aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing` | **rewritten** — drives the scale rows |
| `:284` | `theDisclosureHeaderReadsItsExpandedStateToAccessibilityServices` | survives |
| `:303` | `tappingAddSetClearsTheEntryFields` | **rewritten** — drives the stepper and keypad |

  Plus the block comment at `:217–226` (rewritten). Production KDoc that goes
  stale: `PlannedTargetSummary` (`ActiveWorkoutExerciseCard.kt:212–221`);
  the live constraint that stays: `LoggedSetRow` (`:287–291`).
- **Register:** `D1`, `D10`, `D11`, `D27`, `D31`; CP8 added `D60`–`D66` and
  closed `O6` (`D63`); `O5` was closed by CP7 (`D58` - the shared rest strip
  is the board's, so focus mode draws it rather than `4a`'s slim variant).
- **Built (CP8):** focus mode in `WorkoutFocus.kt` (top bar, header,
  technique notes, `Last:` / `Undo last`, suggestion strip, type note, pinned
  `Log set` / `Next ›`), `WorkoutFocusSets.kt` (the set list and the
  correction sheet), `WorkoutFocusEntry.kt` (stepper cards, the RPE / pain /
  technique disclosure, the warm-up chip) and `WorkoutFocusModel.kt` (rows,
  header counts, `Next ›`, the hint's rest, the load step; `setsWithExtraFlag`
  moved here). `ActiveWorkoutExerciseCard.kt` is deleted; `ControlRowMinHeight`
  lives in `WorkoutFocusEntry.kt`. `ActiveExerciseUi` gains `exerciseId`,
  `defaultLoadIncrement` (kg) and `instructions`, all defaulted.
  `ActiveWorkoutScreenTest`: the five methods the table names rewritten, the
  nine others unchanged, plus six focus-mode methods;
  `ProgressionRecommendationRouteTest` gains the strip's inward path.

### 3.8 Finish sheet and done screen (CP9)

- **Artboards:** `4a` `nFinishSheet` (`:1736–1763`) and `nDone`
  (`:1364–1405`).
- **Finish sheet:** title `Finish this workout?` 20/500 (`:1740`); meta
  `<elapsed> elapsed · <progress line>` (`:1741`); an unfinished box
  (hairline, radius 12): `N still unfinished` and rows `○ <name> … N sets
  left` (`:1746`, `:4334`); `Finish and save` (primary 52, `:1757`), `Keep
  training` (neutral 52), `Leave it running and go Home` (text 48, `:1759`);
  the footnote "Leaving keeps the session open — the clock runs from its start
  time, everything logged is already saved, and Home shows a Resume banner
  until you finish or discard it." (`:1761`).
- **Done screen** (padding 24/16/8): label `Tuesday, 11 Aug · finished`;
  title 30/500; three tiles `Time` / `Sets` / `Trained` (`N of M`) at 21/500
  (`:1370`); a PR card on accent surface, `Best set on <exercise>` with "82.5
  kg × 7 — previous best 80 kg × 7 on 5 Aug." (`:1374`); label `Versus last
  time` and recap rows — name, `N sets · load × reps…`, a delta `+2.5 kg` /
  `partial` / `—`, skipped rows dimmed to `no sets recorded` (`:1381–1391`,
  `:4349`); a session note (`:1394`, `D3`); the line "Saved to History as
  18:12 → <end>." (`:1399`); a pinned `Back to Home`, 56 (`:1402`).
- **Interactive:** the sheet's confirm (the only caller of
  `CompleteWorkoutSession`); `Keep training`; `Leave it running` → Home;
  `Back to Home`.
- **Owner:** `presentation/workout/` (one sheet behind one finish request,
  hosted by the workout surface — CP9 item 1); the done screen's route (CP9
  fixes the file).
- **Tests:** no existing test reaches either surface — today there is no
  confirmation at all (`ActiveWorkoutScreen.kt:200` is a bare button).
  `ActiveWorkoutViewModelTest`'s `:650` (back to no active session)
  **survives**. New CP9 test: Home's `Finish it` opens the sheet, and
  dismissing it returns to Home with no board entry behind it.
- **Register:** `D3`, `D16`, `D37`.

### 3.9 Progression recommendation (CP6)

- **Artboards:** `6a` (`:43–196`) and `6c` (`:198–251`); copy and options in
  the script (`:3462–3505`).
- **`6a`:** top bar `Next session`; an uppercase label with the exercise.
  *Suggestion state:* the outcome row (`ph-trend-up` 22 + `Increase load`
  26/500); a value card (radius 14, gradient, ring `#423a6a`): `80 kg → 82.5
  kg` with the target at 34/500 tabular, and `+2.5 kg`; label `Why` and one
  row per reason (icon 16 + 13.5 text, `:70`); label `What it looked at`
  (`:77`) and a key/value table (Last session, Average RPE, Planned range,
  Load step, Recovery; `:3501`); the footnote "Policy v1 · calculated on this
  device from your own logs. Nothing is applied until you choose."; a pinned
  bar with `Use 82.5 kg` (54 primary, `:180`) over `Pick another load` and
  `Keep 80 kg` (48). *Choosing state:* `Your call` 24/500 (`:92`) and four
  68-tall option rows — `Increase to …`, `Stay at …`, `Drop to …`, `Something
  else…` (`:3462–3467`, `D32`) — and `Back`. *Applied state:* a
  confirmation card, `Next session starts as` with a set list (`:114`),
  `Earlier suggestions` (`:3490`), and "Recorded as: policy v1 suggested
  +2.5 kg, accepted 11 Aug."; a bar with `Change my mind` and `Done`.
  *Overridden state:* a `You overrode this` card with Suggested / Chosen /
  Policy lines, and the streak line (`:173`, `D33`).
- **`6c`:** one card per outcome — `Recovery adjustment` (`ph-heartbeat`),
  `Reduce load` (`ph-trend-down`, destructive tint), `Not enough data yet`
  (`ph-hourglass-medium`, hairline only); each has a label, a 20/500 title,
  reason rows and two actions (primary + `Override`); and the note "Each
  state uses the same card: an outcome label, the plain-language reasons
  behind it, and two actions. Only the icon and the accent treatment change
  — never the layout." (`:246`).
- **Interactive:** accept the suggestion; pick another (override through
  `RecordManualOverride`); keep (override to maintain); `Back`; `Done`;
  `Change my mind`; `RecoveryAdjustment` links to §3.3 (CP6 item 4).
- **Owner:** a new recommendation route and screen (CP6 fixes the package:
  `presentation/progression/`, route `progression/{exerciseId}`).
  Entry points: the picker row's `Why ›` (CP6, carried by CP7), the focus
  strip (CP8), the done screen (CP9).
- **Tests:** `grep -rlE 'onOverrideRecommendation|RecommendationRow|progression_result'`
  finds only `ActiveWorkoutScreenTest`, which passes a no-op
  `onOverrideRecommendation` at `:64` and asserts nothing about it —
  **survives**, which is why CP6 owes new tests (an override reaches
  `RecordManualOverride` and re-renders; one state test per
  `ProgressionResult`; the picker row reaches the screen).
- **Register:** `D32`, `D33`; CP6 added `D49`–`D54` and closed `O3`.

### 3.10 Exercise library (CP10)

- **Artboard:** `2c` (`:2418–2487`).
- **Regions:** a header row — a 40 back arrow and the title `Exercises` at
  25/500 (`:2423–2426`); a 48 search field (radius 10, surface, hairline,
  `ph-magnifying-glass`, `ph-x-circle` clear; `:2427–2431`); filter chips
  (12.5, padding 6/12; `:2432–2437`): `Active` (selected), `Archived`,
  `Chest` with a funnel (`D8`), `Weight & reps` (`O7`); a count label `8
  matches` (`:2440`, `O7`); rows at min-height 64, padding 12/16: the name
  15/500, wrapping for long names, and the meta `<group> · <type> · rest 2:30
  · in 2 plans` or `not in any plan` (`D8` for the group); an archived row at
  60% opacity with a `ph-archive` `archived` badge; a trailing 40×40 `⋮`
  (`:2443`…`:2472`, `D36`); the footer `Not here? Create "press"` (`:2474`);
  a snackbar `Exercise archived.` with `Undo` (`:2479`); a 60×60 FAB, radius
  18, `ph-bold ph-plus` (`:2482`, `D35`).
- **Interactive:** back; search and clear; filter chips; a row → edit; `⋮` →
  a row-action **sheet** (edit / archive / restore, CP10 item 2); `Create
  "<query>"` → the editor with the name prefilled (CP10 item 3); `Undo`;
  create.
- **Owner:** `presentation/exercise/list/ExerciseListScreen.kt` (with
  `ExerciseListLabels.kt`, `ExerciseListUiState.kt`, and a ViewModel that
  gains the plan-usage dependency); the usage query on the existing
  `TrainingPlanRepository` port (CP10 item 5).
- **Tests** (CP10 item 6, re-checked):

| File | Methods | Verdict |
|---|---|---|
| `ExerciseListScreenTest` (androidTest, 15) | `rowMenuEditItemInvokesOnExerciseClick`, `rowMenuShowsArchiveWhenViewingActiveExercisesAndInvokesOnArchiveClicked`, `rowMenuShowsRestoreWhenViewingArchivedExercisesAndInvokesOnRestoreClicked` | **rewritten** — the second click targets the sheet; the trigger's content description is kept |
| | `rendersTheNoSearchResultsEmptyState` | **rewritten** — the new copy, and the tap reaches the prefilled create route |
| | `createFabClickInvokesOnCreateClick` | **rewritten** — create on `RepFlowBottomActionBar`, same content description |
| | `rendersContentRows` | **rewritten** — compile failure on the no-default `planUsageCount` |
| | `searchFieldInputInvokesOnQueryChanged` (`performTextInput`, `:174`) and the other eight | **survive** (strings carried; search stays a text field) |
| `ExerciseListScreenWiringTest` (JVM, 5) | `rowAndFabSizesAreTheDesignsOwn` (row half kept, renamed), `everyTapTargetClearsTheMinimum` | **rewritten** (compile failures on `ExerciseFabSize`) |
| | `filterOrderCoversEveryStatusFilterExactlyOnce`, `eachFilterCarriesItsOwnDistinctLabel`, `everyEmptyReasonExplainsItselfDifferently` | **survive** |
| `ExerciseListViewModelTest` (JVM, 12) | the shared `viewModel` initializer | **fixture edit**; all 12 survive; +1 new |
| `InMemoryTrainingPlanRepository` (JVM fixture) | — | **fixture edit** (the new port query) |

- **Register:** `D8`, `D35`, `D36`; CP10 added `D73`–`D75` and closed `O7`.

### 3.11 Exercise editor (CP10)

- **Artboard:** `2b` (`:2373–2416`, keyboard up). `4a`'s inline new-exercise
  sheet (`:1612–1633`) belongs to the workout picker — `O4`.
- **Regions:** top bar `New exercise` with a text action `Save` (disabled
  tone, `O7`); a field label at 11.5 and a 52 name field (radius 10; a 2px
  error ring `oklch(.6 .13 25)`) with the inline error `ph-warning-circle`
  "An exercise with this name already exists." (`:2385`); `Tracking type` as
  a 46-tall three-segment control whose selected segment carries a check
  (never colour alone); `Default rest` presets `1:00 1:30 2:00 3:00 Other`
  (44, radius 8); `Load step` presets `1.25 kg 2.5 kg 5 kg Other`
  (`:2403`); `Technique notes — shown during the workout` as a 72-tall
  multiline field (`:2411`).
- **Interactive:** back (today's discard rule), `Save`, name, tracking type,
  rest preset, load-step preset, `Other` (typed value), notes.
- **Owner:** `presentation/exercise/editor/ExerciseEditorScreen.kt`,
  `ExerciseEditorFormFields.kt`; the ViewModel's input contract is unchanged
  (CP10 item 4); the optional prefill route argument (CP10 item 3).
- **Tests:**

| File | Methods | Verdict |
|---|---|---|
| `ExerciseEditorScreenTest` (androidTest, 13) | `loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad` | **rewritten** (silent-pass risk) |
| | `restDurationPresetClickInvokesOnRestSecondsChanged` | **rewritten by CP10** — `2b` labels the presets `1:00 1:30 2:00 3:00`, so the method clicks `1:30` and still asserts `"90"` (the plan had it surviving on the old `90` label) |
| | `trackingTypeChipClickInvokesOnTrackingTypeChanged`, `restDurationPresetClickInvokesOnRestSecondsChanged`, `nameFieldInputInvokesOnNameChanged` (`performTextInput`, `:109`) and the other nine | **survive** |
| `ExerciseEditorViewModelTest` (JVM, 16) | all | **survive**; +3 new (the prefill) |

- **Register:** CP10 added `D70`–`D72` and closed `O7`.

### 3.12 Plans list (CP11)

- **Artboards:** `5a` (`:345–409`, turn 5) and `4a`'s Plans tab
  (`:825–882`, turn 4). `5a` is newer; the two differ in the row's secondary
  button (`Open` in `5a`; `Edit` with `ph-sliders-horizontal` in `4a`) and in
  the active plan's primary (`Start day 2` / `Start a day`) — `O8`.
- **Regions:** title `Plans` at 25/500; filter pills `Active` / `Archived`
  (12.5, min-height 36–38, `:352`); an empty card (`ph-list-checks` 26, "No
  active plans. Restore one, or create a plan." / "Nothing archived.",
  `:3432`); plan cards (radius 14, padding 16, gap 12; `:364`): an `Active
  plan` badge (`D4`), the name 17/500, the meta `N days · N exercises · vN`
  (`D4` for days), a note (`Last used 12 Jun` / `Archived 2 Feb`), a trailing
  40-tall `Archive` / `Restore` outline (`:375`); the action row: the primary
  start or `Make active` (`D4`) and the secondary; the info line "Only one
  plan is active — it's the one Home offers each day. Archiving never touches
  completed workouts." (`:390`, `D4`); a snackbar `Plan list updated.` with
  `Undo`; `New plan`, 52, accent outline (`:403`).
- **Interactive:** filter; archive and restore on the card face (CP11 item
  1); start (CP11 item 1, `O8`); open or edit; `Undo`; `New plan`.
- **Owner:** `presentation/trainingplan/list/TrainingPlanListScreen.kt`.
- **Tests:**

| File | Methods | Verdict |
|---|---|---|
| `TrainingPlanListScreenTest` (androidTest, 11) | `rowMenuShowsArchiveWhenViewingActivePlansAndInvokesOnArchiveClicked`, `rowMenuShowsRestoreWhenViewingArchivedPlansAndInvokesOnRestoreClicked` | **rewritten** — click the card's own action |
| | `rowMenuEditItemInvokesOnPlanClick` | **retired or rewritten** — CP11 records which (`rendersContentRowsAndInvokesOnPlanClick` already covers the row tap) |
| | `createFabClickInvokesOnCreateClick` | **rewritten** — `RepFlowBottomActionBar`, same content description |
| | the empty, error, filter and snackbar methods (`:70–146`, `:213–260`) | **survive**; +1 new (the start action) |
| `TrainingPlanListViewModelTest` (JVM, 11) | all | **survive** |

- **Register:** `D4`; CP11 added `D76`–`D80` and closed `O8` with the editor's rows below.

### 3.13 Plan editor (CP11)

- **Artboards:** `4a`'s plan edit (`:884–950`, turn 4) and `2a`
  (`:2212–2370`, turn 2). `4a` is newer and wins on composition — rows
  **expand in place** — which is what CP11 item 2 adopts; `2a` supplies what
  `4a` does not draw.
- **Regions (`4a`):** top bar with back ("Back to plans"), the name at
  16/500 (`:888`) and the meta `N days · N exercises · vN`; day tabs (`:893`,
  `D4`); the day name at 21/500 and the count `N exercises · N working sets`;
  rows (`:905`) at min-height 64: index, name 15/500, meta `3 × 8–10 reps ·
  120s rest`, a superset tag (`D1`), a caret; expanded, three stepper lines
  `Working sets` / `Rep range` (`Hold` for duration) / `Rest`, each with 44
  −/+ and the value at 16 tabular; `Superset with the next exercise` (`D1`);
  `Remove from this day` (destructive text, `ph-trash`); `Add exercise to
  this day`, 52 (`:947`); the note "Changes apply to the next session you
  start from this day. Past workouts keep the version they were run on."
  (`:948`, CP11 item 3).
- **From `2a`:** a header `Edit workout` with `X` and `⋮`; a `Workout name`
  card with a pencil; up/down reorder carets, 36×28; a per-row `optional`
  badge; the target sheet (`:2275–2333`) with a `Warm-up sets` stepper,
  min/max rep (or seconds) steppers, rest presets `1:00 1:15 1:30 2:00 2:30
  3:00` (`:2323`, `:4541`), an `Optional` toggle (`:2329`), `Remove` and
  `Done`; a `Progression — Automatic, per exercise` card (`:2257`); a pinned
  bar "Saving creates **version 5**. Completed workouts stay on version 4."
  (`:2267`) with `Cancel` and `Save as version 5` (`O8`).
- **Picker sheet:** `4a`'s `nPlanPickOpen` (`:1635–1659`) — `Add to <day>`, a
  48 search field, `Create a new exercise`, 60-tall rows with `ph-plus`;
  `2a`'s (`:2336–2368`) adds group chips (`D8`) and `Added` / `Add` badges.
- **Interactive:** back or cancel (the existing discard dialog), name, row
  expand/collapse, steppers, warm-up sets and optional (the domain already
  carries `PlannedExercise.targetWarmupSets` and `isOptional`), reorder,
  remove, add → picker sheet, save.
- **Owner:** `presentation/trainingplan/editor/TrainingPlanEditorScreen.kt`,
  `TrainingPlanEditorFormFields.kt` (its `ExercisePicker` becomes a sheet),
  `TrainingPlanEditorRowActions.kt`.
- **Tests:**

| File | Methods | Verdict |
|---|---|---|
| `TrainingPlanEditorScreenTest` (androidTest, 15) | `removeRowActionInvokesOnRemove`, `moveDownActionOnTheFirstOfTwoRowsInvokesOnMoveDown`, `moveUpActionOnTheSecondOfTwoRowsInvokesOnMoveUp` | **survive, as a constraint**: the expanded row must keep the three content descriptions reachable |
| | `addExerciseButtonClickInvokesOnAddRowClicked` | survives if the trigger keeps its label |
| | `nameFieldInputInvokesOnNameChanged` (`performTextInput`, `:123`) and the other ten | **survive**; +1 new (the picker sheet) |
| `TrainingPlanEditorViewModelTest` (JVM, 8) | all | **survive** |

- **Register:** `D1`, `D4`, `D8`; CP11 added `D81`–`D87` and closed `O8`.

### 3.14 History list (CP12)

- **Artboards:** `3a`'s list (`:1834–1888`, turn 3) and `4a`'s History tab
  (`:954–972`, turn 4). `4a` is newer but draws a strict subset (title,
  count line, rows with a PR badge); `3a` supplies the filters, month
  sections and the invalidated state.
- **Regions:** title `History` at 25/500; filter chips (36 tall, `:1839`)
  `All`, `This plan`, `Invalidated` (`:4380`), `Any date`
  (`ph-calendar-blank`), `Any exercise` (`ph-barbell`, `:1843`); the count
  line `N workouts · newest first`; a month section label (`August 2026`,
  `:1849`); rows (`:1850`) at min-height 68: the name 15.5/500 with a `PR`
  badge (`ph-fill ph-medal`) and an `invalidated` badge (`ph-prohibit`), the
  meta `<when> · <duration> · N sets · <volume>`, a caret.
- **Interactive:** filter chips (the exercise and date choosers open as
  **sheets**, CP3); a row → detail.
- **Owner:** `presentation/history/HistoryScreen.kt` (`HistoryFilters`
  unchanged).
- **Tests:**

| File | Methods | Verdict |
|---|---|---|
| `HistoryScreenTest` (androidTest, 9) | `rendersContentRows`, `invalidateActionShowsConfirmationDialogBeforeInvoking`, `confirmingTheDialogInvokesOnInvalidateClicked`, `cancelingTheDialogDoesNotInvokeOnInvalidateClicked` | **rewritten** against the invalidate trigger's new home. **The design's answer to CP12 item 6's question: the detail screen's `⋮`** (`3a`, `askInvalidate`, `:1895`) |
| | `exerciseFilterMenuInvokesOnExerciseFilterChanged` | **rewritten** against the sheet |
| | `rowClickInvokesOnSessionClick`, `invalidatedMessageShowsSnackbarAndConsumesIt`, `sortOrderButtonTogglesBetweenNewestAndOldestFirst`, `showInvalidatedChipInvokesOnShowInvalidatedChanged` | **survive** |
| `HistoryUiStateTest` (JVM, 9), `HistoryViewModelTest` (JVM, 8) | all | **survive** |

- **Register:** open `O9`.

### 3.15 Workout detail (CP12)

- **Artboards:** `3b` (`:1961–2014`, static) and `3a`'s live detail
  (`:1890–1956`); `4a`'s detail (`:974–1011`) adds the set-edit pencil
  (`D7`) and the "logged before set-by-set detail was kept" line.
- **Regions:** top bar with back and `⋮` (→ invalidate, `:1895`); label `Sat
  8 Aug · 10:20 → 11:12`; title 28/500; `<plan> · version N` (`:1900`);
  three tiles `Time` / `Sets` / `Volume` at 20/500; per-exercise blocks
  (`:1908`; padding 14/0/12, divider): the name 15.5/500, a warm-up line at
  12 (`2 warm-up sets`), a delta at 12.5 (`+2.5 kg` with a medal for a PR,
  `same`, `+1 rep`), set rows `N  70 kg × 10  RPE 8` at 14.5 tabular; the
  skipped line (`D20`); a session note card (`D3`); the invalidate dialog
  (`:1942–1954`): `Invalidate this workout?`, "It leaves History and stops
  counting toward progression. Nothing is deleted — you can bring it back
  from the Invalidated filter.", `Keep it` (primary) and `Invalidate
  workout` (destructive, `ph-prohibit`).
- **Interactive:** back; `⋮` → invalidate (CP12 decides the home, §3.14);
  the dialog's actions.
- **Owner:** `presentation/history/HistoryDetailScreen.kt` (fixes the
  DURATION `Set 0:  kg x ` defect, CP12 item 5).
- **Tests:** `HistoryDetailScreenTest` (androidTest, 3):
  `rendersRpeDurationWarmupPainAndTechniqueQuality` and
  `rendersDurationForADurationTrackedSet` **rewritten**;
  `backButtonInvokesOnBackClick` **survives** if the back label is kept; plus
  `aZeroSetExerciseShowsNoSetsLoggedAndNoDelta` and the DURATION defect test,
  both new.
- **Register:** `D3`, `D7`, `D20`.

### 3.16 Settings (CP14)

- **Artboards:** `5c` (`:536–616`, turn 5) and `4a`'s Settings
  (`:1052–1111`, turn 4). `5c` is newer; the plan's CP14 text follows `4a`'s
  row copy (`D23`, `D24`) and `4a`'s inline Data group — `O11`.
- **Regions (`5c`):** top bar `Settings`; label `Units and appearance` —
  `Weight unit` kg/lb (`:549`, `D5`) and `Theme` System / Light / Dark
  (`:557`, `O11`); label `Rest timer` (`:563`) — `Start rest automatically`
  (subtitle `restTimerSub`, `:3450`, `D23`), `Default rest` `2:00 ›`
  (`:569`, `O11`), `Vibrate when rest ends` (`:573`), `Notify when rest
  ends` with "Works with the screen off" (`:577`; `4a`'s `Rest timer
  notification` reads "Shows on the lock screen with ±15s and Skip",
  `:3931`, `D24`); label `During a workout` (`:581`) — `Keep the screen on`
  (`:583`; `4a`: `Keep screen awake in a workout`, "Ends when you finish";
  `D21`), `Confirm before finishing` with "Warns about unfinished exercises"
  (`:586`), `Extra set fields` `Collapsed ›` (`:591`, `O11`); label `Data`
  (`:595`) — `Backup and restore` (`ph-database`, "Last backup 2 days ago",
  a caret → §3.17; `:598`) and `Archived exercises and plans` (`ph-archive`,
  `:603`, `O11`); the `Irreversible` card (`:608`; destructive ring
  `rgba(216,138,126,.28)`, `ph-warning`, "Erasing removes every workout,
  plan and exercise on this device. Export a backup first.", `Erase all data`
  at 46); the footer `RepFlow 1.0 · offline, no account` (`:613`).
- **Switches** (`tog`, `:3350–3356`): a 46×26 track with a 22 knob; on
  `#9184d9`, off at 16–18%; rows 56–60 tall with an 8% divider. The whole
  row is the tap target, and the state is also announced as a word.
- **Interactive:** back; each switch; `Backup and restore` → the backup
  screen; `Erase all data` → its typed confirmation (CP14 item 5).
- **Owner:** new `presentation/settings/`; `SettingsRepository`, the Room
  `settings` table and `MIGRATION_7_8`; `EraseAllData`;
  `TrainingDataRepository`; `RestTimerExpiryHandler` and `RestAlertVibrator`
  (CP14 items 3–9). Replaces CP2's `SettingsPlaceholder`.
- **Tests:** `ActiveWorkoutViewModelTest` (JVM, 14) — **fixture edit** for
  the `SettingsRepository` dependency; all 14 survive; +2 new.
  `LocalBackupRepositoryAtomicityTest` (androidTest, 4) — **retargeted**
  (the silent case: `replaceAll_rollsBackClearAllTablesWhenAMidTransactionInsertFails`
  and the KDoc at `:24–35`). `grep -rlE 'RestTimerExpiredReceiver|RestTimerAlarmScheduler'`
  over both test trees returns **nothing** — no existing test reaches the
  receiver, so CP14's handler and receiver tests are all new.
- **Register:** `D5`, `D21`, `D23`, `D24`, `D25`; open `O11`.

### 3.17 Backup and restore (CP14)

- **Artboards:** `5d` (`:619–709`, turn 5), a dedicated screen; `4a` instead
  puts `Export a backup` and `Restore from a file` inline in Settings' Data
  group (`:1090–1102`) with a restore **sheet** (`:1461–1487`).
- **Regions (`5d`):** top bar `Backup and restore`; a status card
  (`ph-fill ph-shield-check`, `Last backup 2 days ago`, `9 Aug, 21:04 · 412 KB
  · schema v3`, `Export backup now` as a 52 primary, and "A single JSON file
  with every exercise, plan version, workout and recovery entry. Keep it
  wherever you like — RepFlow never uploads it."); label `Recent backups on
  this device` (`:640`) with file rows carrying `Share` and a
  safety-snapshot row; label `Export for other tools` with `Workout history
  as CSV` as a 52 row (`:657`); a `Replaces everything` destructive card
  (`:660`: "Restoring swaps all local data for the file's contents. RepFlow
  validates the file and takes a safety snapshot first, so you can undo it.",
  `Restore from a backup` at 48, `:662`); a file-chooser sheet (`:666–681`:
  a valid file, a "Not a RepFlow backup" file, `Browse this device…`); a
  confirmation dialog `Restore this backup?` with the validated counts,
  `Cancel` (primary) and `Replace my data` (destructive) (`:683–698`); a
  snackbar `Backup restored · 214 workouts` with `Undo` (`:700–707`).
- **Interactive that the domain backs:** export (system file picker); CSV
  export (`ExportWorkoutHistoryCsv` exists); restore (system file picker) →
  the destructive confirmation → restore. Everything that needs a backup
  history, a safety snapshot or an undo is `D34`.
- **Owner:** `presentation/backup/BackupScreen.kt` (Route and ViewModel
  contract unchanged); reached from Settings' Data group (CP14 item 5).
- **Tests — found by CP1, in no checkpoint's enumeration:** the two route
  tests read this screen **after** their opening navigation.
  `BackupRouteSafCancellationTest` clicks `R.string.backup_export_action`
  (`:56–57`), asserts `backup_message_operation_failed` **does not exist**
  (`:61–62`) and that the action is enabled again (`:64–65`).
  `BackupRouteUnreadableRestoreFileTest` clicks `backup_restore_action`
  (`:58–59`), asserts `backup_message_operation_failed` is displayed
  (`:63–64`) and `backup_restore_confirm_title` **does not exist**
  (`:66–67`). Both **survive only if CP14 keeps those four string ids** — the
  copy may change in `strings.xml`, the ids may not — and the two
  `assertDoesNotExist` checks are the silent-pass shape: if the conversion
  renames the confirmation title's id or stops rendering the failure
  message, they pass vacuously. `BackupViewModelTest` (JVM, 9) —
  **survives** (no ViewModel contract change is planned).
- **Register:** `D34`; open `O11`.

### 3.18 Progress (CP15)

- **Artboards:** `5b` (`:416–533`, turn 5) and `4a`'s Progress tab
  (`:1013–1050`, turn 4). They differ materially — `O12`.
- **`5b`:** title `Progress`; an exercise picker **button**, 52 (surface,
  `ph-barbell`, the name, `ph-caret-down`; `:422`) opening the sheet `Track
  an exercise` (rows 56 with a check; `:517–531`); a metric segmented row
  `Top set` / `Est. 1RM` / `Volume`, 40 tall (`:428`); a card (radius 14,
  padding 14): the metric label (`Heaviest working set` / `Estimated 1RM` /
  `Volume per session`, `:3406`) and range chips `3m` `6m` `All` (`:439`);
  the current value at 30/500 with `+N kg · +N%`; a selected-point readout
  (`Latest · 12 Aug` and its value); a **line chart** (accent line 2.4, a 12%
  area fill, a hi/mid/lo Y axis, 24-wide tap targets, a dashed selection
  line; `:467–470`); X ticks; two tiles `Sessions` and `Avg RPE`; label
  `Training frequency` (`:490`) with eight weekly bars and "3.4 sessions a
  week on average"; label `Records` (`:506`) with `Heaviest set — 82.5 kg ×
  7` and `Most reps at 80 kg — 8`.
- **`4a`:** title; exercise **chips** (`:1017`); a metric segmented control
  (`:1022`); a card with the title, the delta `+N kg since 5 May`, the value
  at 30/500 with its unit, a **bar chart** of 12 bars with the last
  highlighted and month labels (`:1036`), and `Best: N kg · 12-session
  window`; the note "Only valid sessions count. Invalidating a workout in
  History removes its points from this chart." (`:1047`, CP15 item 4).
- **Owner:** a new Progress route and screen (CP15 fixes the package);
  estimated 1RM in `domain/progression/`; the read model in `application/`.
  Replaces CP2's `ProgressPlaceholder`.
- **Tests:** none exist. CP15 item 8 owes the read-model test.
- **Register:** `D22`, `D108`–`D111`; `O12` closed (the user, 2026-10-01: CP15
  builds `4a`; `5b` is a later remediation child's).

### 3.19 Light theme, empty and error states (every checkpoint)

- **Artboard:** `1d` (`:2978–3036`). Light values: background `#e4e7f5`,
  surface `#f3f5fe`, hairline `#cfd3e5`, text `#292b31`, accent `#5d5294` as
  the primary fill, accent outline `#796cbf`, on-accent `#f5f4ff`,
  destructive `oklch(.55 .13 25)`. Empty states: a 26 icon at 35%, a 13.5
  line and one outlined action; error state: `ph-warning-circle` in the
  destructive tone, a 14.5/500 title, a reassurance line, `Retry`.
- Every converted screen is checked in both themes (CP16 item 2). No owner
  of its own: the parent's theme and `RepFlowStateComposables.kt` carry it.

### 3.20 Drawn, and deliberately not converted

| Artboard | What | Status |
|---|---|---|
| `4a` `nPreview` (`:1127–1159`) | `Before you start`, the pre-start preview | `D12` |
| `1a` (`:2497–2861`) | the earlier core flow (focus only, list sheet, exit dialog) | superseded by `4a`; consulted for the keypad only (§2) |
| `1b` (`:2864–2922`) | a read-only plan detail | `D38` |
| `1c` (`:2925–2975`) | an exercise detail (top-set chart, best set, est. 1RM, recent sessions, used in) | `D38` |

---

## 4. Deviation register

Every row is a place the built app differs from the design. **D1–D24 are
seeded verbatim from the execution plan's "Deviation register — seeded";
D25–D38 are added by CP1** from the full read above. Later checkpoints append
here — the next free id is **D112** (CP3 added D39–D40, CP4 D41–D43, CP5 D44–D48, CP6 D49–D54, CP7 D55–D59, CP8 D60–D66, CP9 D67–D69, CP10 D70–D75, CP11 D76–D87, CP12 D88–D96, CP13 D97–D102, CP14 D103–D107, CP15 D108–D111) — and never renumber. Reasons use the
plan's four categories: `no domain backing`, `blocked by open decision`,
`platform convention`, `deliberate product call`.

| # | Design shows | Built instead | Reason | Owner |
|---|---|---|---|---|
| D1 | Superset grouping on board, plan editor, focus, and the board row sheet's `Superset with the next exercise` | Not rendered at all | no domain backing | CP7, CP8, CP11 |
| D2 | Swap sheet with same-muscle-group alternatives, and the board row sheet's `Swap for another exercise` | Not rendered | no domain backing; also blocked by an open decision | CP7 |
| D3 | Per-exercise and session notes, and the board row sheet's `Add a note`/`Edit the note` | Not rendered | no domain backing | CP7, CP9, CP12 |
| D4 | `Active plan` badge, `Day 1..4` tabs, "day 2 of 4" | Flat plan list; no day dimension | no domain backing | CP5, CP11 |
| D5 | Settings `Units` kg/lb | Omitted group | no domain backing | CP14 |
| D6 | Deload card and deload state | Not rendered | no domain backing | CP5 |
| D7 | Set-edit pencil in workout detail (`3b`) | Read-only set rows | blocked by open decision | CP12 |
| D8 | Library group filter; `Chest · Weight & reps` row meta | Tracking type · rest · plan usage | no domain backing | CP7, CP10, CP11 |
| D9 | Home "This week — 2 of 4 sessions" | Omitted | needs a weekly target (plan days) | CP5 |
| D10 | Pain as `none / niggle / sharp`, technique as `clean / grinder / form broke` (`4a`) | 0–5 scale rows | domain stores `pain: Int?` and `techniqueQuality: Int?` on a 0–5 scale; `6b`'s own primitive is "Scale row — 0–5", and artboard `1a` draws these as 0–5. Deliberate product call: keep the stored resolution. | CP8 |
| D11 | RPE pills `6,7,8,9,10` (`4a`) | RPE scale row over the domain's `0.0..10.0` | domain range is wider than the design's shorthand | CP8 |
| D12 | `4a`'s pre-start preview (planned exercises, expected duration, recovery warning, futsal context, suggested adjustments) | Not converted; CP5's start sheet is the only pre-start surface | deliberate product call: `docs/UX_FLOWS.md:37` makes the preview optional ("RepFlow **may** show"), and its two readiness-derived rows are the weakest part of it — "Suggested adjustments" (`:43`) is the per-exercise decision list CP4 explicitly excludes, and "Recovery warning" (`:41`) would restate CP5's readiness line one screen earlier. Both rows stay in `UX_FLOWS.md` as declared intent (CP16 item 5). | CP5 |
| D13 | Board row sheet's `Do this later` (moves the exercise to the end) | Not rendered; with all six row-sheet options out, no row sheet and no `⋮` (CP7 item 7) | no domain backing: no reorder operation or use case | CP7 |
| D14 | Board row sheet's `Skip for today` (a skipped status the finish sheet's unfinished list excludes) | Not rendered (CP7 item 7) | no domain backing: `WorkoutExercise` has no status | CP7 |
| D15 | Board row sheet's `Remove from this workout` | Not rendered (CP7 item 7) | no domain backing: `WorkoutSession` has no removal operation, and one would need a rule for recorded sets | CP7 |
| D16 | Home's `Finish it` opens the finish sheet over Home | Opens it over the workout board; dismissing returns to Home (CP9 item 1) | deliberate product call: one host derives the unfinished-work list and carries CP14's gate | CP9 |
| D17 | Leave sheet's `Discard everything logged` acts at once (`nDiscardSession`) | Opens a destructive confirmation dialog first (CP7 item 1) | deliberate product call (user, 2026-09-30): abandoning is terminal and cannot be undone; `6b` keeps dialogs for exactly this, as CP5's Home abandon does | CP5, CP7 |
| D18 | Leave sheet's `Discard everything logged`, which empties the session (`nDiscardSession`) | `Abandon this workout`, with an `Abandon this workout?` confirmation saying the session is marked abandoned and its logged sets stay stored (CP7 item 1; CP5's Home trash uses the same copy) | deliberate product call (user, 2026-09-30): keep today's `AbandonWorkoutSession` exactly — status `ABANDONED`, sets retained, nothing deleted, no new write path — and make the copy say so | CP5, CP7 |
| D19 | Readiness band advice line (`RDY_BANDS[…].line`, e.g. "Repeat last session's loads instead of adding.") on Home and in the sheet | Band label and driver sentence only (CP4 item 4) | nothing in this milestone acts on the band; the per-exercise decisions are out of scope (CP4), so the advice would be false | CP4, CP5 |
| D20 | Workout detail's `skipped` label for an exercise with no sets (`3b`, `nHistEx`) | `No sets logged` (CP12 item 2) | no domain backing: `WorkoutExercise` has no status, so a skip and an unattempted exercise are indistinguishable (`D14`) | CP12 |
| D21 | `Keep screen awake in a workout` on by default (`setAwake: true`) | Off by default (CP14 item 7) | deliberate product call (user, 2026-09-30): defaults preserve today's behaviour, and today nothing keeps the screen on | CP14 |
| D22 | `Top set` / `Est. 1RM` / `Volume` offered for every exercise (`5b`) | `REPS_ONLY` and `DURATION` exercises offer `Top set` only (reps / seconds), with a line saying the other two need a load (CP15 item 6) | no domain backing: those types record no load (`WorkoutSet.kt:91–101`), so a load-based metric does not exist for them | CP15 |
| D23 | Auto-start row's off-state subtitle `Start it yourself from the workout` (`5c`, `restTimerSub`) | `4a`'s static `After every logged set` in both states; off means no rest timer for logged sets (CP14 item 3) | no domain backing: nothing starts rest manually, in the app or in the prototype | CP14 |
| D24 | Notification row's subtitle `Shows on the lock screen with ±15s and Skip` (`nTimerRows`, `RepFlow.dc.html:3931`) | `Shows when rest ends` (CP14 item 3) | no domain backing: the rest-timer notification has no actions (`RestTimerExpiredReceiver.kt:31–39`) and CP14 adds none | CP14 |
| D25 | Settings has no `Library` row: the exercise library's only inward path is the workout picker (`4a`), and `5c` draws `Archived exercises and plans` in that place (`:603`) | A `Library` row (`books` glyph) in CP2's Settings placeholder and in CP14's grouped screen (CP2 item 4, CP14 item 2) | deliberate product call (plan): creating, editing or archiving an exercise must not require starting a workout first | CP2, CP14 |
| D26 | `4a` logs today's recovery in a **sheet** over Home (`nRecSheet`, `:1790–1814`): the six scales, a readiness readout line, `Save today's check-in` | Home's `Log ›` opens the full recovery entry screen (`3c`), which also carries the futsal block, the date change and notes (CP2 item 5, CP5 item 4, CP13) | deliberate product call (plan): one entry surface for recovery and futsal; `3c`'s own label is "reached from Home 'Log ›'" | CP5, CP13 |
| D27 | Focus mode's suggestion strip is the **readiness** adjustment for that exercise (`nSugLabel` from `decide()`, e.g. "Held at 80 kg — the increase keeps for a fresher day"), and its `Why` opens the readiness sheet (`:1278–1284`, `:4161–4174`) | The strip shows the existing **progression** recommendation (the proposed action and its top reason), and `Why ›` opens CP6's recommendation screen (CP8 item 5) | no domain backing: per-exercise readiness decisions are out of scope (CP4 "Not in scope") | CP8 |
| D28 | Board rows carry a readiness adjustment chip (`ph-pulse`; `1 set fewer` / `held` / `swap suggested`; `adjTag`, `:1209`) and readiness-adjusted targets — a set cut, an increase held (`nSeed`, `:3226–3257`) | No adjustment chip; targets are the plan's own | no domain backing: as `D27` | CP7 |
| D29 | The readiness sheet's `What it proposes, exercise by exercise` list, its `Progress anyway — I feel fine` override, and "Nothing held back — there is nothing to override." (`:1521–1545`) | Not rendered; the sheet ends at the inputs and the gate note | no domain backing: as `D27` (CP4 "Not in scope") | CP4, CP5 |
| D30 | An ad-hoc workout's title opens `Name this workout` (an input, suggestion chips, "Optional — it shows up in History like this."; default `Untitled workout`) (`:1165`, `:1673–1687`) | No rename affordance | no domain backing: `WorkoutSession` has no name field | CP7 |
| D31 | Every logged set in focus mode opens a correction sheet with steppers, `Delete` and `Save the correction` (`:1549–1581`) | Edit and undo reach the most recently recorded set per exercise only; no per-set delete | deliberate product call: the plan's preserved invariant "Undo/edit scope stays the most-recently-recorded set per exercise" (`UndoLastWorkoutSet`/`EditLastWorkoutSet`) | CP8 |
| D32 | The recommendation's `Your call` option `Something else… — Type a load` (`:3466`) | Increase / maintain / reduce only | no domain backing: `ManualOverride` records a `ProgressionResult`, not a load | CP6 |
| D33 | The overridden state's line "Overridden three times in a row on one exercise? Progress flags it so you can adjust the plan rather than fight the suggestion." (`:173`) | Not rendered | no domain backing: no override-streak detection exists and CP15 builds none | CP6 |
| D34 | The backup screen's last-backup age and file metadata, `Recent backups on this device` with `Share`, the safety-snapshot row, an in-app file chooser with a validation preview, the restore `Undo`, and the copy "takes a safety snapshot first, so you can undo it" (`5d`; `4a` `:1461–1487`) | System file pickers for export and restore, the existing destructive confirmation, CSV export; no copy that promises a snapshot or an undo | no domain backing: no backup history is stored, and the restore path takes no safety snapshot (`grep -rni safety app/src/main/kotlin` returns nothing) | CP14 |
| D35 | `2c` creates an exercise from a 60×60 FAB (`:2482`) | Create sits on `RepFlowBottomActionBar` (CP10 item 6) | deliberate product call: `6b`, the newer turn, pins the one primary action to a bottom bar | CP10 |
| D36 | `2c`'s row overflow `⋮` is 40×40 (`:2443`) | ≥ 44 | deliberate product call: `6b`, the newer turn, sets the 44 tap-target floor | CP10 |
| D37 | The done screen (`4a` `nDone`) draws no progression recommendations | A recommendations section on the done screen (CP9 item 3) | deliberate product call: `docs/UX_FLOWS.md` requires "relevant progression recommendations" on completion | CP9 |
| D38 | `1b`'s read-only plan detail and `1c`'s exercise detail (top-set chart, best set, est. 1RM, recent sessions, used in) | Not built | deliberate product call (the plan's artboard inventory: `1a`–`1c` superseded by `4a`/`5a`). `1c` is domain-backed and `4a` never replaces it, so this row is **flagged for the reviewer to accept or reject** | — |
| D39 | `6b`'s "45% tertiary and labels" text tier (section labels, stat captions) | Dark: lifted to the secondary tier's 55%, so the two tiers coincide and are told apart by size and case; light (no design render): 70% for both | platform convention (WCAG): 45% measures 3.72–3.91:1 on `surface`/`background`/the bottom bar, below the 4.5:1 an always-visible label owes; 50% still misses on `surface` (4.25:1). Same trade as the parent's nav-label lift (`ROLE_AUDIT.md`) | CP3 |
| D40 | The keypad's `Cancel`/`Set` at 52 tall, radius 10 (`:2840–2841`) | The design-system button tiers: `Cancel` a neutral outline lifted to 56, `Set` the 56dp radius-12 primary; still 1 : 2 | deliberate product call: `6b`'s own button scale (56 primary) over a one-off 52 drawn in the older `1a` turn | CP3 |
| D41 | The readiness band colours in light theme: the design draws none (`RDY_BANDS`, `:3153–3158`, are the dark values) | Dark keeps the design's four values. Light keeps each band's hue and chroma and lowers OKLCH lightness to the first step clearing 4.5:1 on both light grounds: Ready = light `primary` (accent-700), Hold `#826210` (`oklch(.515 .10 85)`), Back off `#9E5416` (`oklch(.525 .12 55)`), Protect = light `error` | platform convention (WCAG): the dark values measure 1.73–2.41:1 on light `surface`, and the band word and a flagged factor's label are text drawn in that colour. `ReadinessBandStyleTest` pins the floor | CP4 |
| D42 | The readiness sheet's title `How today was adjusted`, its line "…The score changes what the app proposes — never what you are allowed to do." (`:1494–1495`), and the gate note "…at 3 or more, leg work is flagged regardless of the score." (`:1519`) | `How today's score was set`; "Your check-in sets one score. It is a read on today — never a limit on what you are allowed to do."; "…at 3 or more, or heel stiffness at 4 or more, the band is Protect regardless of the score." | no domain backing: nothing in the app acts on the band (`D19`, `D29`), so copy saying the score adjusts proposals or flags leg work would be false — the same reason `D19` drops the advice line. The gate note also names the heel-stiffness gate the engine applies (`readiness()`, `:3142–3166`) and the design's note omits. **Copy written by CP4 — flagged for the reviewer to accept or reword** | CP4 |
| D43 | The sheet's factor rows draw label, five dots and weight; the flagged state is carried by the label's and dots' colour alone (`:1508–1516`; `nRdyFactors` computes `value` and `note` but the markup renders neither) | Each row adds `v/5 · pulling the score down` or `v/5 · fine` under the label (plan CP4 item 4) | deliberate product call (plan): `6b`'s "destructive and accent are never the only signal — every state also carries an icon or a word" | CP4 |
| D44 | The start card's meta `N exercises · N working sets · ~N min` — the prototype estimates the minutes as `sets × 2.8 + 8` (`:3554`) | `N exercises · N working sets`; no time estimate | no domain backing: nothing in the domain estimates a session's length, and the prototype's figure is a placeholder constant, not a model. Resolves `O1` | CP5 |
| D45 | The start sheet's `From <plan>` label over one row per plan day, closing with `A different plan ›` (`:1411–1434`) | `Your plans` over one row per active plan (name, `N exercises · N working sets`), then `Empty workout`; no `A different plan ›` row | no domain backing: plans have no days (`D4`), so the rows are the plans themselves — every active plan is already listed, and the closing row would have nothing left to open. Resolves `O2` | CP5 |
| D46 | The start card stays on Home under the resume card while a session runs, `Start workout` and all (`:738–761`) | While a session is active the start card is not drawn; the resume card's `Resume` is the screen's one primary action | deliberate product call: only one session may be active (`StartWorkoutSession` refuses with `ActiveSessionAlreadyExists`), so the button could only fail; `6b` also allows one primary action per screen | CP5 |
| D47 | The recovery card's `What it changes ›` on the score (`:772`); the empty state's "Nothing logged today. Sleep and leg soreness shape the load suggestions." and `Log recovery — 20 seconds` (`1d`, `:3005–3006`) | `Details ›`; "Nothing logged today. Pain while walking and heavy legs shape the load suggestions."; `Log recovery` | no domain backing: the readiness sheet shows how the score was set, not what it changes (`D19`, `D29`, `D42`); `ProgressionPolicyV1` reads pain while walking and heavy legs from the check-in (`ProgressionPolicyV1.kt:79–88`), never sleep or DOMS; `Log` opens the full entry screen (`D26`), not a 20-second sheet. **Copy written by CP5 — flagged for the reviewer to accept or reword** | CP5 |
| D48 | The resume card's `Resume`, `Finish it` and trash at 48 tall, radius 10 (`:748–750`), and the recovery card's `Log ›` at 36 tall (`:766`) | `Resume` is the 56dp radius-12 primary tier and its two neighbours match its height; `Log ›` meets the 44 tap-target floor | deliberate product call: `6b`'s own button scale and 44 floor over one-off sizes, as `D36` and `D40` | CP5 |
| D49 | `6a`'s value card (`80 kg → 82.5 kg`, `+2.5 kg`) and every load figure in an action or a title: `Use 82.5 kg`, `Keep 80 kg`, `Increase to 82.5 kg` / `Stay at 80 kg` / `Drop to 77.5 kg` (`:3462–3467`), `6c`'s `Back Squat — hold at 110 kg`, `Overhead Press — 45 → 42.5 kg`, `Use 42.5 kg`, `Hold at 110 kg` | No value card; the outcome is the title (`Increase load`, …), the actions are named by what they do (`Go with the suggestion`, `Pick another load`, `Keep the same load`) and the `Your call` rows by outcome (`Increase load` / `Maintain load` / `Reduce load`, with `The suggestion` under the suggested one) | no domain backing: `ProgressionResult` carries no load (`ProgressionResult.kt`), and deriving one (last working load ± `defaultLoadIncrement`) would be a new policy output, which CP6 may not add ("No change to `ProgressionPolicyV1`", plan CP6 item 6). Resolves `O3`'s value row | CP6 |
| D50 | `6a`'s `What it looked at` table — Last session, Average RPE, Planned range, Load step, Recovery (`:77`, `prInputs` `:3501`) | Not rendered; the `Why` rows carry every reason the policy recorded, and those reasons already state the figures it used (average RPE, the rep range, the recovery values) | no domain backing: a `ProgressionRecommendation` stores its result and reasons, not its inputs; re-deriving them from today's history could show figures the policy never saw. Resolves `O3`'s table | CP6 |
| D51 | `6a`'s applied state (`82.5 kg it is`, `Next session starts as` with a set list, `Recorded as: policy v1 suggested +2.5 kg, accepted 11 Aug.`) and `Earlier suggestions` with an outcome per row (`Accepted` / `Overridden — held`) | `Go with the suggestion` writes nothing and leaves (the recommendation already stands); a recorded choice shows `Suggested · …`, `Chosen · …`, `Policy v1 · <date>`; no earlier-suggestions list | no domain backing: nothing seeds the next session's targets from a recommendation, and acceptance is not recorded — a `ManualOverride` records a different decision only, so an earlier suggestion with no override cannot be labelled accepted or ignored. Resolves `O3`'s applied and history rows | CP6 |
| D52 | `6a`'s copy that says a choice changes the next session or teaches the policy: the footnote "…Nothing is applied until you choose.", `Your call`'s "Whatever you pick is what next session starts with.", and the overridden line "…so the policy can learn where it's consistently wrong for you." (`:86`, `:93`, `:147`) | Footnote "Policy v1 · calculated on this device from your own logs. It never changes a load for you — you set the load while training."; "Whatever you pick is kept on record as your final say. The suggestion stays on record either way."; "Your choice wins. RepFlow keeps both the suggestion and your choice on record."; a choice back to the suggestion after an override reads `You went with the suggestion`. `Wait for more data` is renamed to `6c`'s own `Not enough data yet` everywhere it shows | no domain backing: no load is ever applied from a recommendation and `ProgressionPolicyV1` does not learn from overrides, so the design's lines would be false — the same reason as `D42` and `D47`. **Copy written by CP6 — flagged for the reviewer to accept or reword** | CP6 |
| D53 | `6c`'s `Suggestions` screen: one card per exercise, each outcome a card with two actions, and `Not enough data yet` with none (`:198–251`) | One screen per exercise, reached from that exercise's `Why ›`; each of the five outcomes is a state of it (plan CP6 item 3), with the same layout and actions: `Pick another load` is offered for every outcome, `Not enough data yet` included, and its primary is `Done`. `Keep the same load` shows only where it differs from the suggestion (increase, reduce). `Maintain load`, which neither artboard draws, takes `6a`'s value-card `ph-arrow-right` | deliberate product call: the override the workout picker offered before CP6 applied to every outcome, so the move keeps it (plan CP6 "replaces and relocates an existing capability"); the per-exercise entry points (picker row, CP8's strip, CP9's finish screen) open one exercise, not a list | CP6 |
| D54 | `6c`'s recovery-adjustment card: the policy's reasons only | The policy's reasons, then a `Today's check-in` card — score and band word in the band colour, CP4's driver sentence, `Details ›` — opening the readiness sheet; drawn only when today has a check-in | deliberate product call (plan CP6 item 4): the one outcome whose explanation is a recovery fact links to the readiness detail and reuses its driver list. The card says what today's check-in reads and claims nothing about what the policy saw, since the policy reads the latest check-in at completion time, not today's | CP6 |
| D55 | Every exercise carries a set target; one added in the workout gets the prototype's default `target: 3` (`nPickRows` `add`, `:3990`), so its chip reads `0/3 sets` and it is unfinished until three are logged | An ad-hoc exercise has no target: its chip reads `No sets yet` (`ph-circle`) or `N sets` (`ph-check-fat`, done tone), it counts as finished once one working set is logged, and it adds what it logged — not a target — to the progress line's `T`; a board with no planned exercise reads `N of M exercises · S sets` | no domain backing: `WorkoutExercise` has no target outside a plan (`PlannedExercise.targetSets`), and inventing `3` would invent outstanding work the finish sheet (CP9) would then list as unfinished | CP7 |
| D56 | The picker sheet's `Create a new exercise` opens an inline sheet — name, tracking type, muscle group — and adds the new exercise to the workout at once (`nNewEx`, `:1612–1633`; `nCreateEx`, `:4020–4031`) | `Create a new exercise` opens the existing exercise editor (`2b`); saving returns to the board, where the new exercise is picked like any other. Resolves `O4` | deliberate product call: one exercise-creation surface, the editor CP10 converts, carrying every field the domain stores (load increment, rest, instructions), rather than a second, thinner creation path inside workout mode; the muscle group is `D8` | CP7 |
| D57 | The board draws no recovery or futsal context (`nBoard`, `:1161–1238`) | The line the workout screen already showed — `Heavy legs: n/4 · Leg DOMS: n/4 · Futsal in the last 24h (load n)` — kept as a meta line under the progress line when any of the three exists | deliberate product call: no checkpoint retires this reading, `docs/UX_FLOWS.md` asks for recent futsal context around a workout, and Home's readiness card is not visible in workout mode | CP7 |
| D58 | The rest strip's `Resting — <exercise>` / `Next set is ready — <exercise>`, its lit `Rest done` state (fill `#2b2741`, ring `#9184d9`), the separate in-app `Rest complete` banner with `+30s` (`:1661–1671`), and a 40 dismiss `X` | `Resting` / `Next set is ready` with no exercise name; at zero the strip stays up and says `Rest done`, the word carrying the state without the lit fill; no banner (the system notification still fires); the dismiss `X` at the 44 floor or above (a Material `IconButton`). `−15s` / `+15s` / `Skip rest` / dismiss are built on `AdjustRestTimer` / `SkipRestTimer`. Resolves `O5` (the strip is one composable shared by the board and the set entry, so CP8 inherits it) | no domain backing: `RestTimer` records no exercise (`RestTimer.kt`), and `+30s` on an expired timer would need a restart rule `AdjustRestTimer` does not have; the lit fill would need a card tone with no light-theme render; `6b`'s 44 floor | CP7 |
| D59 | The board top bar's `Finish` at 40 tall, radius 8, 13.5 (`:1174`) | The design system's accent-outline button (48, radius 10) | deliberate product call: `6b`'s own button scale over a one-off size, as `D36`, `D40`, `D48` | CP7 |
| D60 | After `Log set` the weight and reps stay where they were for the next set, and before the first set they start from the last session (`Last time: 82.5 kg × 8`, `nLastSetHint` / `nLogSet`, `:4275–4288`, `:4262–4273`) | Every logged set clears the entry: the steppers go back to `—` and the RPE / pain / technique / warm-up choices reset. `Last: …` shows the set just logged in this session; nothing is shown before the first one | deliberate product call: the plan's preserved contract that recording a set clears the entry state (`tappingAddSetClearsTheEntryFields`, Milestone 8 finding #3: no accidental duplicate submit), and no read model gives focus mode the previous session's sets | CP8 |
| D61 | Weight steps by `2.5` kg (`5` lb) whatever the exercise (`stepW`, `nWeightUnit`, `:4207`) | Weight steps by the exercise's own `defaultLoadIncrement` (`6b`: "Step size comes from the exercise's load increment"), falling back to the prototype's 2.5 kg when the exercise sets none; reps step by 1 and seconds by 5, as the prototype; every stepper starts empty (`—`) and the first `+` steps up from zero | deliberate product call: `6b` over `4a`'s fixed step; the fallback is the design's own value | CP8 |
| D62 | The `Weight` and `Reps` cards always side by side (`:1286–1306`) | Side by side only from 400dp of content width; narrower, they stack. At a 360dp phone each half-width card leaves its value 39dp between the two 44dp buttons, and a load such as `102.5` at 24sp needs about 70 | platform convention: a value that wraps or clips inside the stepper is unreadable, and `6b`'s 44 buttons cannot shrink | CP8 |
| D63 | The warm-up hint "Logs outside the set count, 60s rest." — a fixed 60 s rest after a warm-up (`nWarmHint`, `nLogSet`, `:4260`, `:4286`) | "Logs outside the set count, Ns rest." / "Working set — Ns rest after it." with `N` the exercise's planned rest, else the app's 90 s default — the rest `onRecordSet` actually starts, warm-up or not. "Optional. Whatever you set applies to the next set you log, then clears." is kept as drawn: it is true (`D60`). Resolves `O6` | deliberate product call: the hint states today's rest rule rather than the drawing's; a separate warm-up rest would be a new rule in `ActiveWorkoutViewModel.onRecordSet`, which this milestone does not change | CP8 |
| D64 | The RPE row as one line of pills (`6,7,8,9,10` in `4a`; `6b`'s "0–5 and RPE" scale row) | RPE `0–10` (`D11`) as `6b`'s scale row in two lines, `0–5` over `6–10`, the second line keeping the first line's cell width. End labels `easy → max`; pain `none → sharp` and technique `form broke → clean` on their `0–5` rows (`D10`) | platform convention: eleven cells on one line would be about 25dp wide at a 360dp phone, under `6b`'s own 44 tap-target floor | CP8 |
| D65 | Set rows with a separate index column (`W` for a warm-up, otherwise the working-set number) and a summary such as `82.5 kg × 8 · RPE 8 · niggle` (`nFocusRows`, `:4118–4141`); pending rows read only `not logged` | The index is the summary's existing `Set N:` — every logged set counted, warm-ups included — and the summary keeps the parent milestone's three formats (`Set 2: 60.0 kg x 8 (extra)`), with RPE / pain / technique on a second line. A pending row reads `Set N: not logged · Target: 8-12 reps · Rest: 60s`: the planned target and rest the old chips carried, on one wrapping line. The plan's warm-up target, which no row shows, is beside the header's `X of Y sets done` (`0 of 2 warm-ups`) | deliberate product call: the plan's preserved summary formats (two parent defect fixes and the `extra` marker are pinned by exact text), and plan CP8 item 3, which makes the pending rows the chips' replacement | CP8 |
| D66 | The correction sheet on every logged set, with `Delete` and `Save the correction` (`nSetEditOpen`, `:1549–1581`) | The pencil, and the sheet, on the most recently logged set only (`D31`); the sheet corrects weight / reps / seconds through `EditLastWorkoutSet` and hands the set's RPE, pain, technique and warm-up flag back unchanged; no `Delete` — removing the last set is `Undo last` | deliberate product call: the preserved undo/edit scope (`D31`); a per-set delete would be a new use case | CP8 |
| D67 | The done screen's `Versus last time` delta: `+2.5 kg` whenever an exercise met its set target, `partial` when it did not, `—` when skipped; the detail line `N sets · <first set's load> × <every set's reps>` (`nRecapRows`, `:4349–4366`) | The delta compares this session's best working set with the best working set of the last earlier valid session that trained the exercise the same way: the heaviest load (`+2.5 kg` / `−2.5 kg` / `same load`), else the most reps (`+2 reps` / `−1 rep` / `same reps`), else the longest hold (`+5 s` / `−5 s` / `same time`); `first time` with nothing comparable before; `—` with no working set, the row dimmed. No `partial`. The detail counts working sets and lists runs at one load, `3 sets · 80 kg × 8, 8; 82.5 kg × 6`, so no set is shown at a load it was not lifted at; a warm-ups-only exercise reads `N warm-ups only`, an empty one `no sets recorded` | no domain backing: the prototype's `+2.5 kg` is a constant that says nothing about what was lifted, and `partial` restates the finish sheet's unfinished list as if it were a comparison. Derived by `ObserveWorkoutSummary` from valid completed sessions only, as Home's load increases are (`ObserveRecentTraining`). **Copy written by CP9 — flagged for the reviewer to accept or reword** | CP9 |
| D68 | One `Best set on <exercise>` card for the first exercise with any set, its previous best invented as 2.5 kg lighter on a fixed date (`nHasPr` / `nPrDetail`, `:4341–4348`) | One card per exercise whose best working set this session (heaviest load, then most reps at it; else most reps; else longest hold) beats every earlier valid working set of that exercise measured the same way; "previous best … on <date>" names that earlier best and the most recent session that set it. A first time is never a best set (there is nothing to beat), nor is a tie; invalidated sessions are never the record | deliberate product call (plan CP9 item 4: "derived from existing history … never faked"): computed in memory over the completed history on every emission, the same cost as Home's load increases; more than one card when more than one exercise set a record, since picking one would hide the rest | CP9 |
| D69 | The done screen's tiles at 21/500 (`:1370`); the finish sheet's `Finish and save` at 52 tall, radius 10 (`:1757`) | `RepFlowStatTile`'s 19/500 figures (the tile CP3 built for `3b`'s triple and this screen); `Finish and save` at the design system's 56dp, radius-12 primary | deliberate product call: `6b`'s own scale over one-off sizes, as `D36`, `D40`, `D48`, `D59` | CP9 |
| D70 | `2b`'s `Save` as a text action at the end of the top bar (`:2380`) | `Save` (`Saving…` while in flight) is the 56 primary on `RepFlowBottomActionBar`, enabled exactly when the draft is saveable. Closes that part of `O7` | deliberate product call: `6b`, the newer turn, pins the one primary action to a bottom bar (as `D35` for the library's create) | CP10 |
| D71 | `2b`'s field labels (`Name`, `Technique notes — shown during the workout`) drawn 11.5 above their fields (`:2383`, `:2411`) | The name, technique-notes and `Other` value fields keep Material's outlined label inside the field (it rises into the outline once filled); the tracking type, `Default rest` and `Load step` controls carry `2b`'s 11.5 labels above them. The notes field carries the design's prompt as its placeholder | platform convention: a text field's label is its own accessible name, so TalkBack (and the editor's tests, which type into the field by it) reach one node; a separate label above would leave the field itself unnamed | CP10 |
| D72 | `2b`'s duplicate-name error inline under the name while it is being typed (`:2383–2385`) | The same inline error — the field's error ring, `warning-circle` and `An exercise with this name already exists.` — under the name field, shown once `Save` is refused for a duplicate name and kept until the next save attempt (as today's submit error was) | no domain backing: there is no name-availability query while typing; uniqueness is decided at save by `CreateExercise` / `UpdateExercise`, whose input contract plan CP10 item 4 keeps | CP10 |
| D73 | `2c`'s tracking-type filter chip `Weight & reps` beside `Active` / `Archived` (`:2436`) | Not built: the filters stay `Active` / `Archived`. (`2c`'s `8 matches` count **is** built, shown while a search is active.) Closes `O7` with `D70`–`D72` | deliberate product call: plan CP10 item 1 converts the status filter only; a second filter dimension would be new behaviour in `ObserveExercises`' criteria that the plan does not ask for. **Flagged for the reviewer** | CP10 |
| D74 | `2c`'s header: a 40 back arrow beside the 25/500 `Exercises` title (`:2423–2426`) | CP3's sub-screen bar: the 44 `arrow-left` and the title at 17/500, as `2b` and every other pushed screen draw it | deliberate product call: `6b`'s recurring sub-screen bar over `2c`'s one-off header (the library is a pushed screen, reached from Settings), and the 44 tap-target floor, as `D36`, `D69` | CP10 |
| D75 | `2c` draws an archived exercise inside the `Active` results, dimmed, with its `archived` badge (`:2449–2452`) | Archived exercises appear only under `Archived`, each row dimmed to 60% with the `archived` badge exactly as drawn; `Active` lists active exercises only | deliberate product call: the preserved status-filter semantics of `ObserveExercises` (one status per list), which the plan does not change | CP10 |
| D76 | `5a`'s per-card note `Last used 12 Jun` on an active plan (`:369`, from the prototype's `PLANS[].note`) | An archived plan's card reads `Archived <d MMM yyyy>` (from `TrainingPlan.archivedAt`); an active plan's card has no note line | deliberate product call: plan CP11 item 1 does not name it, and "last used" is a join of completed sessions to plan versions the list does not observe today (Home's `ObserveRecentTraining` derives only the *latest* one). Domain-backed, so **flagged for the reviewer** | CP11 |
| D77 | `5a`'s action row: `Start day 2` (accent primary) on the one active plan, `Make active` on every other (`:378–384`); `4a`'s `Start a day` → Home's start sheet (`:3638`) | `Start workout` (the accent primary, filled `play`) on **every active plan's card**: it starts a session from that plan's latest version through `StartWorkoutSessionFromPlan` (the use case Home's start card calls) and opens the workout; with a workout already running it is refused with `A workout is already running.` and nothing opens. Archived cards have no start (Home only ever starts an active plan). Closes that part of `O8` | no domain backing: no active plan and no days (`D4`); plan CP11 item 1 asks for "a start action" on the card. The behaviour - start directly, from the card - is CP11's reading of that item, so **flagged for the reviewer** | CP11 |
| D78 | `5a`'s secondary `Open` / `4a`'s `Edit` with `sliders-horizontal` (`:386`, `:880`); `5a`'s card body is not itself a button | `Open` (`5a`, the newer turn), a 46-tall neutral outline that opens the editor - full width on an archived card, which has no start. The card body **also** opens the editor, as the old row did. Closes that part of `O8` | deliberate product call: turn precedence picks `5a`'s word; the card-wide tap is the preserved row behaviour (and `rendersContentRowsAndInvokesOnPlanClick` pins it) | CP11 |
| D79 | `5a`'s `Archive` / `Restore` card button is 40 tall (`:375`) | ≥ 44 | deliberate product call: `6b`'s 44 tap-target floor (as `D36`) | CP11 |
| D80 | `5a`'s info line "Only one plan is active — it's the one Home offers each day. Archiving never touches completed workouts." (`:390`) and its toast `Plan list updated.` (`:396`) | The footnote keeps the second sentence only, under the cards (and under the empty card). The archive snackbar is `5a`'s card (`archive` glyph, accent `Undo`) with the existing words `Training plan archived.` | no domain backing for the first sentence (`D4`); the snackbar copy is a deliberate product call - it says what happened rather than that "the list" changed | CP11 |
| D81 | `4a`'s plan-edit header: the plan name at 16/500 over `N days · N exercises · vN` (`:887–890`); `2a`'s `Workout name` card with a pencil (`:2224–2227`) | CP3's sub-screen bar titled `Edit plan` / `New plan`, then the name as `2b`'s 52-tall outlined field with its label inside (`D71`) and its inline error; `4a`'s count line becomes `2a`'s `EXERCISES` label with `N exercises · N working sets` beside it | platform convention: the name is editable here, so it is a text field whose label is its accessible name (`D71`), not a heading; no days (`D4`) | CP11 |
| D82 | `4a`'s `Remove from this day` inside the expanded row (`:939`); `2a`'s reorder carets 36×28 (`:2240–2241`) | A 44 `trash` (destructive tone, `Remove exercise`) on the **collapsed** row face, beside `2a`'s stacked up/down carets drawn **44 wide × 32 tall** so the pair fits `4a`'s 64 row; a disabled direction is dimmed and disabled, as drawn | deliberate product call: reorder and remove stay one tap away whether or not a row is open, as before the conversion, which is also plan CP11 item 5's constraint (the three row-action tests click them without expanding anything). The carets' 32 height is **below `6b`'s 44 floor - flagged for the reviewer** (the alternative, 44×44 stacked, makes every row 88 tall) | CP11 |
| D83 | `4a`'s single `Rep range` stepper that moves both ends together (`8–10` → `9–11`) and `Hold` for a duration exercise (`:925–929`, `:3672–3675`) | `2a`'s two steppers (`:2295–2318`): `Min reps` / `Max reps`, or `Min seconds` / `Max seconds` (step 5), each in its own `4a` line | no domain backing for the shift-only control: `RepRange` and `DurationTarget` store independent bounds, and a stepper that only shifts could never widen or narrow a range the editor could set before | CP11 |
| D84 | `2a`'s per-row target **sheet** (`:2275–2333`) with `Done` | `4a`'s expand-in-place (the newer turn's composition, §3.13) carrying `2a`'s controls inline: working sets, **warm-up sets**, the range, rest with `2a`'s six presets (`1:00 1:15 1:30 2:00 2:30 3:00`; tapping the selected one clears the optional rest, as CP10's presets do), the **`Optional` toggle**, and a `Change exercise` outline that re-opens the picker for the row (not drawn - the old row dropdown's job). Closes the warm-up and optional parts of `O8` | deliberate product call: turn precedence on composition; the warm-up sets and `Optional` controls are domain-backed (`PlannedExercise.targetWarmupSets`, `isOptional`) and the pre-conversion editor already edited them, so dropping them would lose behaviour; `Change exercise` keeps the row's exercise editable in place | CP11 |
| D85 | `2a`'s pinned bar "Saving creates **version 5**. Completed workouts stay on version 4." with `Cancel` and `Save as version 5` (`:2264–2272`) | `Save` (`Saving…` in flight) as the 56 primary on `RepFlowBottomActionBar` (`D70`'s answer); `Cancel` is the bar's back arrow, which keeps the discard dialog. The version fact is stated by plan item 3's note - "Changes apply to the next session you start from this plan. Past workouts keep the version they were run on." - under the rows, when editing an existing plan. Closes that part of `O8` | deliberate product call: `6b`'s bottom bar; the editor's UI state carries no version number (plan CP11 keeps the ViewModel contract), and the note states the same invariant without one. **Flagged for the reviewer** | CP11 |
| D86 | `2a`'s `Progression — Automatic, per exercise` card (`:2255–2262`) | Not built. Closes the last part of `O8` | no domain backing: progression is one global policy (`ProgressionPolicyV1`), not a per-plan setting, so the card would be a static claim whose caret leads nowhere | CP11 |
| D87 | `4a`'s plan picker sheet titled `Add to <day>` with an inline `Create a new exercise` (`:1635–1659`, `nOpenNewEx`); `2a`'s picker adds group chips and `Added` / `Add` badges and a `last` meta (`:2336–2368`) | `Add to plan`; `Create a new exercise` opens the exercise editor (`2b`), and the new exercise is in the sheet the next time it opens; rows are the name over the tracking type with an accent `plus`; no group chips (`D8`), no `Added` badges, no `last` meta | deliberate product call: one exercise-creation surface, as `D56`; a plan may repeat an exercise (the domain allows it), so `Added` would mislead; `last` needs a history join the editor does not have | CP11 |
| D88 | `3a`'s single-select `All` / `This plan` / `Invalidated` chips, where `Invalidated` lists *only* invalidated workouts (`:4380–4386`) | A plan chip (`list-checks`; `Any plan`, or the chosen plan / `No plan`) that opens a sheet - `Any plan`, `No plan`, then every plan a workout was run from - and a `Show invalidated` toggle chip (`prohibit`) that **adds** invalidated workouts to the list, each marked with the `invalidated` badge. Closes `O9`'s `This plan` | no domain backing for `This plan`: there is no active or current plan (`D4`). The include-not-isolate semantics are the preserved `HistoryFilters.showInvalidated` (plan CP12 item 1 converts the existing filters; `HistoryUiState` is unchanged) | CP12 |
| D89 | `3a`'s one `Any date` chip (`ph-calendar-blank`, `:1842`); no chooser is drawn | Two chips, `From any date` / `To any date` (`From 3 Aug 2026` once set), each opening a sheet with Material's date picker, `Clear` and `Set` - in place of the old date-picker dialog | deliberate product call: the preserved filter has independent start and end dates; `6b`'s "sheets for choices" over a dialog | CP12 |
| D90 | `3a`'s count line `N workouts · newest first` as static text (`:1845`) | The same line, where `newest first` / `oldest first` is a 44-tall button (`Change order`) that flips the existing sort - the old `Newest first` button's job | deliberate product call: keeps the preserved sort reachable in the design's own words instead of a sixth chip | CP12 |
| D91 | `3a`'s row and `3b`'s title name the plan **day** (`Push A · Upper`, `Pull A · Upper`); the meta reads `Today · 18:40 → 19:28 · 48 min · 19 sets · 7 340 kg` (`:3073–3080`) | The plan's own name, or `Untitled workout` for a workout started without a plan (Home's and the done screen's word); the meta `Sat 8 Aug · 10:20 · 52 min · 18 sets · 6,720 kg` - working sets only, the volume left out when no working set carries a load, no `Today` | no domain backing for day names (`D4`); `Untitled workout` is the app's existing word for an ad-hoc session (CP5, CP9) | CP12 |
| D92 | `3a`'s `PR` badge and `3b`'s medal on an exercise's change (`SESSIONS[].pr`, `ex[].pr`: fixed sample flags) | Derived, never stored: a session carries `PR` when one of its exercises' best working set beats the best earlier valid set of that exercise measured the same way - exactly the sessions whose done-screen summary lists a best set (`D68`), computed in one pass by `sessionsWithPersonalBests`. The detail puts the medal (`Personal best`) beside that exercise's change. An invalidated workout never carries either, and is never the record. Closes `O9`'s PR derivation | deliberate product call (plan CP12 item 1: "PR badge where derivable"): one rule for the done screen and History, so a workout never earns a badge in one place and not the other | CP12 |
| D93 | `3a`'s detail `⋮` (`aria-label` `More options`, `:1895`) opening `Invalidate this workout?`, whose copy says "you can bring it back from the Invalidated filter" and whose actions are a `Keep it` primary over an outlined `Invalidate workout` (`:1942–1954`) | The `⋮` opens the same confirmation directly and is named for its one action, `Invalidate workout`; it is absent on an already invalidated workout. The dialog is Material's `AlertDialog` (as `AbandonWorkoutDialog`): `Keep it` and `Invalidate workout` in the error tone with `prohibit`. The copy reads "It leaves History and stops counting toward progression. Nothing is deleted — turn on Show invalidated to see it again." Answers plan CP12 item 6's question: the invalidate trigger's new home is the detail | no domain backing for "bring it back": no use case reverses an invalidation. A control whose only action is destructive is named for it (TalkBack would otherwise announce `More options` and open a destructive dialog) | CP12 |
| D94 | `3b`'s tiles `Time` / `Sets` / `Volume` at 20/500, the volume as `6 720 kg` (`:1902–1906`) | Plan CP12 item 2's order, `Time` / `Volume` / `Sets` (`4a`'s), on `RepFlowStatTile`'s 19/500 (`D69`): `52 min`, the volume with its unit in the caption - `Volume (kg)` over `6,720`, or `—` with no loaded working set - and working sets | deliberate product call: a third-width tile on a 360dp phone cannot hold `11,480 kg` at 19/500 without truncating, so the unit moves to the caption | CP12 |
| D95 | `3b`'s set rows list working sets only (`N  70 kg × 10  RPE 8`), warm-ups folded into `2 warm-up sets`; the change reads `+2.5 kg` / `same` / `+1 rep` (`:1908–1931`) | Every logged set, in order: working sets numbered 1…n, a warm-up unnumbered and marked `warm-up` (the `N warm-up sets` line is kept above); the value by tracking type (`70 kg × 10`, `12 reps`, `45 s` - which fixes the parent's `Set 0:  kg x ` DURATION defect); `RPE 8` inline; `Pain 2/5` and `Technique 4/5` on a line below when recorded. The change is CP9's (`D67`): `+2.5 kg`, `same load`, `−1 rep`, `first time`; none with no working set | deliberate product call: History has shown every recorded set field since Milestone 8 (its tests pin warm-up, RPE, pain and technique), so folding warm-ups away would hide recorded data; one comparison rule with the done screen | CP12 |
| D96 | `4a`'s empty detail "This session was logged before set-by-set detail was kept. The totals above are all there is." (`:986–988`) | "No exercises were logged in this workout." on a card, for a completed workout with no exercises | no domain backing: the app has always kept set-level detail, so the only empty detail is a workout finished with nothing logged | CP12 |
| D97 | `3c`'s scale cells fill **cumulatively** - every cell up to the value tinted, the chosen one solid accent (`recRows`, `:4435–4448`) - at min-height 46, gap 6 (`:2040`) | CP3's `RepFlowScaleRow`, `6b`'s own scale row: only the chosen cell is selected (accent fill + `#796cbf` ring), cells 44 tall, gap 5; label 14.5/500 and `low → high` at 12 as `3c` draws them | deliberate product call: `6b` is the newer turn and the Compose token source, and its primitive is the one focus mode already uses (CP8); one scale control app-wide. The digit in the chosen cell is the word, so the state is never tint alone | CP13 |
| D98 | `3c`'s one `Save entry` and a futsal block drawn only while `Played in last 24h` is on (`showFutsalBlock: s.futPrev`, `:4466`); the prototype always holds a minutes and RPE value | One `Save entry` (`6b`'s one pinned primary) replaces the old screen's two saves: it writes the check-in and, while `Played in last 24h` is on, the futsal session for the same date. **Both futsal fields empty saves the check-in alone** (the old separate futsal save made the session optional); one filled without the other is rejected with "Some values need attention before saving." before anything is written. A session already stored for a day whose check-in says not played stays stored and listed in history; turning `Played` on shows it for editing. Success reads `Saved` with a check on the bar until the next edit, announced politely; the old `Saved` snackbar is retired, and only errors use the snackbar | deliberate product call: the design's composition and `6b`'s one-primary rule, with the optional-session rule kept from the screen it replaces. **Flagged for the reviewer** | CP13 |
| D99 | `3c`'s steppers start from sample values; `Minutes` ±5 from 0, `Session RPE` ±1 on 0–10 (`:4457–4462`); the value a button labelled `Type minutes` / `Type session RPE` | CP3's compact stepper with the same steps and bounds; an empty field reads `–` (no prefilled session); the value opens CP3's keypad titled `Minutes` / `Session RPE`, whole numbers for minutes and decimals for RPE (the domain's `sessionRpe` is `0.0..10.0`, and the old field took decimals); the value's own spoken label is CP3's `Type a value` | no domain backing for a default session; the RPE resolution is the domain's (as `D11`) | CP13 |
| D100 | `3d`'s chart with a check-in on every one of the 14 days (`RECOV`, `:3089–3096`), futsal flags `f` per day, and 20-wide tap targets (`:2139–2140`) | The 14 calendar days ending today; a day with no check-in **breaks** both lines (a day with no neighbour gets a small dot) and reads `No check-in` in the readout; the futsal dot marks a day with a futsal session recorded; the readout opens on the latest day with a check-in; each day is one equal column (about 20dp on a phone - the design's own width, under `6b`'s 44 floor; every day's values are also in `Entries` and each column carries a spoken description); `avg` is over the window's check-ins, to one decimal | deliberate product call: an unbridged gap is the honest drawing of a missing check-in; the tap width is the design's own, since 14 columns of 44 do not fit a 360dp phone | CP13 |
| D101 | `3d`'s insight card: "**Leg DOMS runs 1.4 higher** in the two days after futsal. Progression on leg work is held back automatically on those days." (`:2158–2161`) | Not built | no domain backing: the second sentence is false against `ProgressionPolicyV1` - a futsal session recorded in the **24 hours** before a workout (`GetWorkoutDayContext`, `RECENT_FUTSAL_WINDOW_HOURS = 24`) turns **every** exercise's recommendation into `Recovery adjustment` (`ProgressionPolicyV1.kt:86–88`), not leg work on the two days after; the first is a statistic over a handful of check-ins that the app has no rule for. Closes `O10`'s card. **Flagged for the reviewer** | CP13 |
| D102 | `3d`'s `Entries` rows `Today · Sleep 4 · Energy 3 · DOMS 2` with a futsal ball (the sample's ball does not match its own chart flags) | The same row - `Today`, `10 Aug`, or `10 Aug 2025` for an earlier year - for **every** check-in, not only the window's; the ball is the check-in's own `Played in last 24h` (spoken "Played futsal in the last 24h"). The old row's `Pain` gives way to the design's `DOMS`; pain while walking stays on the entry screen and in the readiness sheet | deliberate product call: History lists everything recorded (as Milestone 8 made it), and the row's mark has to mean one stored fact | CP13 |
| D103 | `5c`'s `Backup and restore` row leading to `5d`'s dedicated screen; `4a`'s Data group with only `Export a backup` and `Restore from a file` | `4a`'s inline Data rows **plus** `5d`'s `Workout history as CSV` (`ph-table`, meta `For spreadsheets and other tools`) as a third row; the Backup screen and its `backup` route are retired, its SAF wiring moved unchanged into Settings (`rememberBackupFileActions`), `BackupViewModel` untouched; the `cloud-arrow-up` glyph goes with the screen | deliberate product call (user, 2026-10-01: CP14 builds `4a`, `5c`/`5d` go to a later child): `4a` has no CSV row, but the CSV export exists and must stay reachable, and `5d` draws it. **Flagged for the reviewer** | CP14 |
| D104 | `4a`'s `Restore from a file` subtitle "Takes a safety snapshot first", its restore sheet (a file row with a preview, "This replaces what is on the device … so this is undoable", `Restore and take a snapshot`, a `Restored` done state) | The subtitle "Nothing is replaced until you confirm" (`4a`'s own restore-sheet line); the system file picker, then the existing destructive dialog, retitled `Restore this backup?` with `Replace my data` (`5d`'s wording) and copy that says the settings stay; success is the existing `Backup restored` toast | no domain backing (as `D34`): no safety snapshot, no undo, no pre-restore preview of the file's counts | CP14 |
| D105 | `Erase all data` with no confirmation drawn (`4a` `:1107`, not even wired) | A destructive dialog: `Erase all data?`, a body naming what goes - every exercise, plan, workout (a running one included), recovery entry and recommendation - and what stays - the settings and already-exported backup files - and a field `Type ERASE to confirm` (case and surrounding spaces ignored) that enables `Erase everything`; `Keep my data` dismisses. Then `All data erased`, or `Couldn't erase. Nothing was changed.` | deliberate product call (plan CP14 items 5-6: a typed confirmation whose copy states the blast radius). **Copy written by CP14 - flagged for the reviewer** | CP14 |
| D106 | The footer `RepFlow 1.0 · offline, no account` at 35% (`:1110`) | `RepFlow <versionName> · offline, no account` with the build's own version (`0.1` today), at the secondary text tier | no domain backing for `1.0`; platform convention for the tier (WCAG, as `D39`: 35% does not reach 4.5:1) | CP14 |
| D107 | Two switch geometries: the rest-timer rows' knob travels to 20, the workout rows' to 22 on the same 44-wide track (`nTimerRows` / `nSetRows`, `:3935`, `:3945`) | One switch: 44×26, a 22 knob at 2 / 20, on `primary`, off `onSurface` at 18% | deliberate product call: a 22 knob at 22 overruns a 44 track; one control, one geometry | CP14 |
| D108 | `4a`'s exercise chips and metric segments drawn 40 tall (`:1018`, `:1023`), the choice shown by the accent tint alone | Both 44 tall; the selected chip and segment also carry a `check-fat` and are announced as selected radio buttons; the chips still scroll sideways as drawn | platform convention: `6b`'s own "every tap target is at least 44" and "never the only signal" | CP15 |
| D109 | `Est. 1RM` with no formula stated (`4a`'s `PROGRESS` sample; `1c`'s `82.5 × 7` → `99 kg`, `:2959–2960`) | **Brzycki**, `load × 36 / (37 − reps)` (it reproduces `1c`'s 99 exactly), in `domain/progression/EstimatedOneRepMax.kt`: the best per-set estimate over a session's working sets of **1 to 12 reps**, in whole kilograms; a set of more than 12 reps has no estimate, so a session whose loaded sets are all longer gives no `Est. 1RM` point (its `Top set` and `Volume` still count) | deliberate product call (plan CP15 item 2 leaves the formula to the checkpoint): rep-max formulas lose their meaning on long sets, and a light high-rep set would otherwise out-score the heavy sets the estimate describes. **Formula and the 12-rep ceiling flagged for the reviewer** | CP15 |
| D110 | `4a`'s sample: always twelve bars, `+N kg since 5 May`, `Best: N kg · 12-session window`, three fixed exercise chips | The window is the **last up to twelve valid sessions that have a value for the metric**, one bar each; the best line names the window's real size (`4-session window`); the delta runs from the window's first session (`since 5 May`, with the year when that session was in an earlier year than the latest); chips are every trained exercise, most recently trained first, named as the latest session recorded it; a session recorded under an earlier tracking type of the same exercise gives no point. An offered metric with fewer than two points shows `1d`'s empty treatment in the card (`Not enough sessions yet. A trend needs at least two.`) above the latest value if there is one; no history at all shows `1d`'s empty state for the tab (the CP2 placeholder's words). Value `kg` / `kg total` (volume) / `reps` / `s`; the delta and best line use `kg` / `reps` / `s`, as `4a` uses `kg` for volume's | deliberate product call: the sample's fixed twelve and its three exercises have no meaning against a real history; the empty-state copy is CP15's. **Empty-state copy flagged for the reviewer** | CP15 |
| D111 | Older bars `#5d5294` under a `#9184d9` latest bar; text at 40% (month labels), 45% (best line), 50% (unit, note) and 60% (exercise name); the card at radius 14; the delta `#d2cefd` when up | Older bars are the theme's `primary` at 50% (about `#5a5485` on the dark card, and still distinct in light, where `primary` is `#5d5294`); every text tier above at the secondary tier (`D39`); the card is CP3's `RepFlowCard` (radius 16); the delta in the selected-pill label tone (`#d2cefd` dark) when up or level and `error` when down, always signed (`+10`, `−2.5`); one spoken description stands for the bars (`Top set over the last 4 sessions, from 72.5 kg to 82.5 kg`) | platform convention (WCAG, as `D39`) for the tiers; the design system's own card for the radius; a fixed `#5d5294` would equal the light theme's latest bar | CP15 |

**Status-chip audit (`6b`, "never colour alone"; CP3).** Every chip the
design draws pairs its tone with an icon or a word (§2). On the built
surfaces the ones that must hold the rule are the board's status chip
(CP7), History's `PR` and `invalidated` badges (CP12), the library's
`archived` badge (CP10), the readiness band (a word; CP4/CP5) and the scale
row's selected cell (the number is the word). CP3 adds a row here for any
primitive that fails the audit.

**CP3 audit result: no primitive fails.** `RepFlowStatusChip` takes its word
as a required, non-null `text` parameter, so every tone — `Done`, `Pending`,
`Outline`, `UpNext` — carries a word by construction, and an optional glyph
on top. `RepFlowScaleRow` takes both end labels as required parameters, so a
scale cannot render without them, and its selected cell is a digit. CP3 also
gave `UpNext` the colour `6b` draws (accent fill, accent-300 word, no ring),
settling the parent's open note that it differed from `Outline` by type
alone. No register row is added for the audit.

---

## 5. Open items — drawn, domain-backed, not decided by the plan text

None is decided here. Each owning checkpoint either builds the element or
appends a register row with its reason; an item still open at CP16 is a
review finding.

| # | Owner | Design element | Why it is open |
|---|---|---|---|
| O1 | CP5 — **closed: `D44`** | The start card's meta `N exercises · N working sets · ~N min` (the prototype's estimate is `sets × 2.8 + 8`, `:3554`) | the plan is silent on a time estimate |
| O2 | CP5 — **closed: `D45`** | The start sheet's `A different plan ›` row, against CP5 item 3's plan list + `Start without a plan` | the plan names its own sheet content |
| O3 | CP6 — **closed: `D49`, `D50`, `D51`** | `6a`'s numeric value (`80 kg → 82.5 kg`, `Use 82.5 kg`), `What it looked at`, `Next session starts as`, `Earlier suggestions`, `Recorded as …`; `6c`'s two-action card | `ProgressionResult` carries no load, so any value must be derived (last working load + `defaultLoadIncrement`) or the value row registered as a deviation; a history list needs `ProgressionRecommendationRepository.findAll` |
| O4 | CP7 — **closed: `D56`** | The picker sheet's inline `Create a new exercise` (name + tracking type, `:1612–1633`) and its empty state | the plan converts the picker but is silent on creating from it |
| O5 | CP7, CP8 — **closed: `D58`** | The rest strip's `−15s` / `+15s` / `Skip rest` / dismiss (backed by `AdjustRestTimer` and `SkipRestTimer`), its lit `Rest done` state, and the in-app `Rest complete` banner with `+30s` (`:1661–1671`) | the plan says the strip "renders here too" without enumerating its controls |
| O6 | CP8 — **closed: `D63`** | The warm-up hint "Logs outside the set count, 60s rest." (a 60 s warm-up rest, `:4260`), and "applies to the next set you log, then clears" | today's rest rule and the detail fields' lifetime may differ; CP8 states what it keeps |
| O7 | CP10 — **closed: `D70`–`D73`; `N matches` and the `Other` presets built** | `2c`'s tracking-type filter chip and `N matches` count; `2b`'s `Save` in the top bar (against `6b`'s bottom bar), its duplicate-name inline error, and its `Other` presets | CP10 item 1 names only `Active` / `Archived` |
| O8 | CP11 — **closed: `D77`, `D78`, `D84`, `D85`, `D86`** | A per-row start action with no active-plan concept; `Open` (`5a`) or `Edit` (`4a`); the warm-up sets stepper and `Optional` toggle (the domain fields exist); the "Saving creates version N" bar; the `Progression — Automatic, per exercise` card | CP11 item 2 lists sets / rep range / rest only |
| O9 | CP12 — **closed: `D88`, `D92`; month sections, the `invalidated` badge and `<plan> · version N` built** | History's `This plan` chip, month section labels, the `PR` badge derivation, the `invalidated` badge, the `<plan> · version N` line | CP12 item 1 says "PR badge where derivable" and "existing filters converted" |
| O10 | CP13 — **closed: `D101`, `D100`; the `Futsal sessions` list, the 14-day window, the selected-day readout and the notes field built** | `3d`'s futsal insight card — its second sentence ("held back automatically") is a claim about `ProgressionPolicyV1` that CP13 must verify or drop; the `Futsal sessions` list; the 14-day window and selected-day readout; `3c`'s notes field | CP13 items 3–4 name only "the chart above, entries below" |
| O11 | CP14 — **closed by the user (2026-10-01): CP14 builds `4a`; `5c`/`5d` are a later remediation child's. Built: `D103`–`D107`** | `5c` (newer) against `4a`: `Theme` System / Light / Dark (today the app follows the system theme), an app-level `Default rest`, `Extra set fields` always / collapsed / off (the prototype's `optionalFields` prop, `:3044`), the `Archived exercises and plans` row, the footer; and `5c`'s `Backup and restore` row → a dedicated screen (`5d`) against `4a`'s inline Data rows | CP14 follows `4a`, and each `5c`-only preference would be a new column in `MIGRATION_7_8`, so it must be decided before the migration is written — **a product decision for the user**, not for CP14 alone |
| O12 | CP15 — **closed by the user (2026-10-01): CP15 builds `4a`; `5b` is a later remediation child's. Built: `D108`–`D111`** | `5b` (newer) against `4a`: an exercise picker sheet or chips, a line chart with selectable points or a bar chart, a `3m` / `6m` / `All` range, the `Sessions` / `Avg RPE` tiles, `Training frequency`, `Records` | CP15's text describes `4a`'s composition, while the turn rule says `5b` wins — **a product decision for the user before CP15 starts** |

---

## 6. Test roll-up

The inventory's own count, from the greps in §3. It adds to the plan's
"Verification strategy" roll-up (twenty files); it does not replace it.

| Surface | Files the conversion reads | New since the plan's roll-up |
|---|---|---|
| Nav (CP2) | `MainActivityNavHostSmokeTest`, `RepFlowIconsTest`, `RepFlowBottomNavigationBarTest`, the two backup route tests | — |
| Readiness (CP4) | `InMemoryRecoveryRepository`, `RecoveryDaoTest` | — |
| Workout (CP7–CP9) | `ActiveWorkoutScreenTest` (5 of 14 change); `ActiveWorkoutScreenWiringTest` and `ActiveWorkoutViewModelTest` survive | — |
| Library and editor (CP10) | 6 files | — |
| Plans (CP11) | 2 files | — |
| History (CP12) | 2 files | — |
| Recovery (CP13) | 0 — the grep is empty | — |
| Settings (CP14) | `LocalBackupRepositoryAtomicityTest`, `ActiveWorkoutViewModelTest` | **As built, eleven more files:** two compile-forced fixture edits the JVM/instrumented second pass did not list - `ActiveWorkoutFocusPlumbingTest` (JVM) and `ActiveWorkoutLeaveRouteTest` / `ProgressionRecommendationRouteTest` (instrumented) all construct `ActiveWorkoutViewModel`, and `LocalBackupRepositoryVersionCompatibilityTest` constructs `LocalBackupRepository`; `RepFlowDatabaseMigrationTest` (+2), `RepFlowIconsTest` (expected set), the smoke test's two Settings walks, and the two backup route tests' opening navigation (the Backup screen is gone - `D103` - so they click the Data rows; the four string ids are kept) |
| **Backup screen (CP14)** | the two backup route tests' **second step**: four string ids (`backup_export_action`, `backup_restore_action`, `backup_message_operation_failed`, `backup_restore_confirm_title`), two of them behind `assertDoesNotExist` | **New: no checkpoint enumerated the Backup screen's own conversion.** No new *file* (both are already counted under CP2), but a new **constraint on CP14**: keep the four ids or rewrite the two methods. `BackupViewModelTest` (9) survives. |
| Progress (CP15) | `MainActivityNavHostSmokeTest.progressTabOpensWithoutCrashing` (the placeholder's `progress_placeholder_empty`) | **As built:** the smoke walk now waits for `progress_empty` (the same words, renamed with the placeholder's retirement); new JVM `EstimatedOneRepMaxTest`, `ExerciseProgressTest`, `ProgressModelTest`, `ProgressViewModelTest`; new instrumented `ProgressScreenTest` |
| **Design system (CP3)** | `RepFlowPrimitivesTest` (16): `theStepperCarriesBothOfTheDesignsSizes` and `thePillPickerCarriesBothOfTheDesignsCellShapes` read the two primitives CP3 reworks; `RepFlowThemeTest` (14) pins the assigned colour roles that CP3's `RepFlowSheet` role decision sits beside | **New as named methods**: both survive if CP3 keeps the size and shape constants they read; CP3 extends the file regardless (plan, CP3) |
| `performTextInput` outside the workout | `ExerciseEditorScreenTest:109`, `ExerciseListScreenTest:174`, `TrainingPlanEditorScreenTest:123` | **Checked**: all three type into name or search fields that stay text fields — they survive |

`LayerBoundaryTest` must keep passing at every checkpoint.
