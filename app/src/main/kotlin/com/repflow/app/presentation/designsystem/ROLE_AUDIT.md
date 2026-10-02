# ColorScheme role → consumer → value → contrast audit

Re-run this table before changing any value in `RepFlowColor.kt`, **and
after any change that adds, removes or restyles a consumer** — a screen
conversion changes this inventory without touching a single value, which is
exactly how the previous edition went stale. A newly discovered stock
consumer, or a ratio that drops below its floor, is a token finding — fix the
value (or scope the treatment to the one component that wants it) rather than
shipping the role.

**Last re-run:** `repflow-redesign-visual-foundation-remediation-1` CP16, over
the converted tree (every screen in the four-destination navigation). Values
and ratios are unchanged from the parent milestone and CP3; the `Consumers`
column is re-derived.

Method: every `androidx.compose.material3` import under `app/src/main/kotlin`
cross-referenced against `material3-android-1.4.0`'s token files to find which
`ColorScheme` role each component reads; ratios are WCAG relative-luminance,
computed (not eyeballed) against every surface the role actually renders on.
Alpha roles get one ratio per background, not one overall. A value scoped to
one component still owes its floor inside that component.

**Counting basis, stated once so every figure below is reproducible.** Counts
are *rendered call sites at HEAD*: a component's call sites under
`app/src/main/kotlin`, excluding imports and declarations. A call site that
passes the colour parameter a role would otherwise default to is **not** a
stock consumer of that role — it is listed under the direct reads instead,
because the colour it renders is the one it names.

Imported Material 3 **components**, audited (17): `AlertDialog`, `Button`,
`CircularProgressIndicator`, `DatePicker`, `HorizontalDivider`, `Icon`,
`IconButton`, `LinearProgressIndicator`, `ModalBottomSheet`, `NavigationBar`,
`NavigationBarItem`, `OutlinedButton`, `OutlinedTextField`, `Scaffold`,
`Snackbar`, `Text`, `TextButton`.

**Retired by the conversion, no longer imported anywhere:** `Card`,
`Checkbox`, `DatePickerDialog`, `DropdownMenu`, `DropdownMenuItem`,
`FilterChip`, `FloatingActionButton`, `ListItem`, `Switch`, `TopAppBar`. Every
consumer they contributed to the previous edition is gone with them — the
seven `DropdownMenu`s on `surfaceContainer`, the bare `Card` on
`surfaceContainerHighest`, both FABs on `primaryContainer`, `FilterChip` on
`secondaryContainer`/`outlineVariant`, the `ListItem` supporting lines on
`onSurfaceVariant`, and the `Switch`/`Checkbox` rows on `primary`/`onPrimary`.
Settings' switches are RepFlow's own (`SettingsRows.kt`, announced as
`Role.Switch`) and read `primary`/`onPrimary`/`onSurface` directly.

Components that read **no `ColorScheme` role of their own**:

- **`Icon`** (87 call sites) and **`Text`** render `LocalContentColor` unless
  given an explicit `tint`/`color`, so they show whatever their container or
  their own argument provides; each explicit one is a direct read below.
- **`IconButton`** (4: `ExerciseListScreen.kt:289`, `HomeScreen.kt:223`,
  `ActiveWorkoutScreen.kt:494`, `WorkoutSheets.kt:177`) defaults its content
  to `LocalContentColor` as well.
- **`LinearProgressIndicator`** (`ActiveWorkoutScreen.kt:480`, the rest
  strip — its only call site) sets `color = primary` and
  `trackColor = RepFlowColor.control`, so neither `ActiveIndicatorColor` nor
  `TrackColor` (`SecondaryContainer`) is reached.
- **`HorizontalDivider`** (17) — every call site passes `color`, so
  `outlineVariant` is never reached.
- **`ModalBottomSheet`** (`RepFlowSheet.kt:79`, the only one) passes
  `containerColor`, `contentColor` and its scrim explicitly; see
  `surfaceContainerLow` below.

The remaining `androidx.compose.material3` imports are not components, or
host one without drawing a colour, and read no role: `ButtonDefaults`,
`ColorScheme`, `ExperimentalMaterial3Api`, `LocalContentColor`,
`MaterialTheme`, `NavigationBarItemDefaults`, `OutlinedTextFieldDefaults`,
`SelectableDates`, `Shapes`, `SnackbarData`, `SnackbarDuration`,
`SnackbarHost`, `SnackbarHostState`, `SnackbarResult`, `Typography`,
`darkColorScheme`, `lightColorScheme`, `rememberDatePickerState`,
`rememberModalBottomSheetState`. They are listed so a maintainer re-running
the import cross-reference gets the same input set this table was built from.

**Two paths, not one.** Stock component defaults are only half the inventory:

- **Colour literals live in `designsystem/` by design.** `RepFlowColor.kt` is
  a token file — definitionally a list of transcribed `Color(0x…)` literals.
  The one-way rule is that no *other* file under `presentation/` carries one,
  re-verified by grep at this re-run: **0** hits outside `designsystem/`.
- **The converted screens read `ColorScheme` roles directly**, through
  `MaterialTheme.colorScheme.<role>` or a local `val scheme =
  MaterialTheme.colorScheme` alias, in 41 files outside `designsystem/`. Every
  role they read is an **assigned** one: `onSurface`, `onBackground`,
  `surface`, `background`, `primary`, `onPrimary`, `error`. No file outside
  `designsystem/` reads an unassigned role directly (the previous edition's
  six `onSurfaceVariant` direct reads went with the screens that held them).
  The per-role file lists are in the `Consumers` column; line numbers are
  deliberately not given for them — at this volume a line list is stale at
  the next edit and the role/ground pairing is what the ratios depend on.

  Reproduce both greps from `presentation/` (the literal dot matters; an
  unescaped `.` also matches `MaterialTheme(colorScheme = …)`):
  `grep -rlE "(MaterialTheme\.colorScheme|scheme)\.<role>\b" --include='*.kt' . | grep -v '^designsystem/'`
  per role, and `grep -rn 'Color(0x' --include='*.kt' . | grep -v '^designsystem/'`.

So the table is complete only because the `Consumers` column below carries
*both* — the stock components a role reaches through Material 3's own
defaults, **and** the direct reads, each with the ground it actually renders
on. A maintainer re-running this table must re-run both greps, not just the
import cross-reference.

## Assigned roles

| Role | Consumers | Dark | Light | Context → ratio | Floor |
| --- | --- | --- | --- | --- | --- |
| `primary` | **Stock:** the 18 `TextButton`s that take the default content colour (where the label's own `Text` sets none), on `background`, `surface` and dialog containers; `Button` fill in the two editors' discard dialogs (4); `CircularProgressIndicator` (`RepFlowStateComposables.kt:35`); `OutlinedTextField` focus outline/label/caret (9); `DatePicker` selected day and today ring (2, inside `RepFlowSheet`). **Direct reads:** the rest strip's progress (`ActiveWorkoutScreen`); Settings' switch track (`SettingsRows`); Plans' `Start workout` fill and accent card (`TrainingPlanListCards`); readiness and recommendation accents (`ReadinessSheet`, `ReadinessBandStyle`, `ProgressionRecommendationScreen`); Progress bars (`ProgressCard`); the recovery trend lines and futsal dots (`RecoveryTrendChart`, `RecoveryHistoryScreen`, `RecoveryFutsalBlock`); the nav's selected pill (`RepFlowBottomNavigationBar`) | `#9184d9` | `#5d5294` | label on `surface` 4.71 / 6.23; on `background` 5.45 / 5.50; the bars, lines, track and fills are fill (3:1) | 4.5 text, 3 fill |
| `onPrimary` | **Stock:** `Button` content in the two discard dialogs. **Direct reads:** Settings' switch knob (`SettingsRows`); Plans' `Start workout` label (`TrainingPlanListCards`). `RepFlowButtons`' primary tier sets it explicitly | `#161826` | `#f5f4ff` | on `primary` 5.45 / 6.22 | 4.5 |
| `secondary` | `NavigationBarItem` selected label (CP4's override, tabulated below); 13 component families' focus ring | `#e9e9ed` | `#292b31` | on nav container 13.79 / 13.01 | 4.5 |
| `background` | **Stock:** `Scaffold` canvas (`RepFlowNavHost.kt:62`; `RepFlowScreenScaffold` sets the same role explicitly). **Direct reads:** workout mode's canvas (`WorkoutBoard`, `WorkoutFocus`, `WorkoutDoneScreen`) and the recovery notes field's container (`RecoveryFutsalScreen`) | `#161826` | `#e4e7f5` | fill only | n/a |
| `onBackground`/`onSurface` | Default body text everywhere. **Direct reads:** `onBackground` for titles and glyphs on the workout canvas and workout detail (`WorkoutBoard`, `WorkoutFocus`, `WorkoutDoneScreen`, `HistoryDetailScreen`); `onSurface` — alone and through `repFlowSecondaryTextColor` / `repFlowAccentOutlineColors` — in 31 files covering every converted screen (Home, Plans and the plan editor, History and detail, Progress, Recovery entry and history, the library, Settings, the board, focus mode, the sheets and the done screen), on `background`, `surface`, `control` and `surfaceContainer`. `RepFlowStatusChip`'s `Pending` label and `RepFlowStepper`'s value/glyphs render on `control` | `#e9e9ed` | `#292b31` | on `background` 14.54 / 11.49; on `surface` 12.55 / 13.01; on `control` 11.69 / 7.03; secondary tier (.55 / .70) per the CP3 table below | 4.5 |
| `surface` | **Direct reads:** card and sheet grounds — `RepFlowSheet`'s container (`repFlowSheetContainerColor`), `RepFlowStatTile`; the editors' field containers (`editorFieldColors`, `ExerciseEditorFieldErrorText`); snackbar cards (`ExerciseListScreen`, `HistoryScreen`, `RecoveryFutsalScreen`, `SettingsScreen`, `TrainingPlanListScreen`); Home's gear and cards (`HomeScreen`, `HomeStartCard`); Plans' cards; the trend chart's readout. No stock component reads it any more (`ListItem` and `TopAppBar` are retired) | `#232532` | `#f3f5fe` | fill only; text on it is measured in the `onSurface` row and the CP3 section below | n/a |
| `surfaceContainer` | **Stock:** `NavigationBar` bar fill (`RepFlowBottomNavigationBar.kt:51`) — the one remaining stock consumer; the seven `DropdownMenu`s are retired. **Direct read (CP3):** `RepFlowBottomActionBar`'s fill — the design's `#1b1d2b`, the same as the nav bar it stands in for | `#1b1d2b` | `#f3f5fe` | fill only; the bars' labels are measured in the nav section and the CP3 section below | n/a |
| `surfaceContainerHigh` | **Stock:** 6 `AlertDialog` containers (`ExerciseEditorScreen`, `TrainingPlanEditorScreen`, `HistoryDetailScreen`, `BackupFileActions`, `AbandonWorkoutDialog`, `SettingsErase`); `DatePicker`'s own container (2: `HistoryFilterChips`, `RecoveryFutsalScreen`), drawn inside `RepFlowSheet` on the same value | `#232532` | `#f3f5fe` | `primary` action label 4.71 / 6.23; `onSurface` headline 12.55 / 13.01; `onSurfaceVariant` supporting 8.91 | 4.5 |
| `surfaceContainerHighest` | **None.** Its only consumer, the plan editor's bare `Card(`, was rebuilt by remediation-1 CP11; no imported component reads the role. The dark reassignment is kept so a future stock reader starts from an accessible value (`RepFlowColor.kt`'s KDoc says so) | `#232532` (reassigned) | unassigned `#E6E0E9` | not rendered | n/a |
| `error` | **Stock:** `OutlinedTextField` `isError` outline/label/supporting text (the editors' name fields). **Direct reads:** destructive actions and their glyphs — the abandon, invalidate, restore and erase dialogs and the erase card (`AbandonWorkoutDialog`, `HistoryDetailScreen`, `BackupFileActions`, `SettingsErase`), Home's abandon (`HomeResumeCard`, `HomeCards`), the plan editor's remove (`TrainingPlanEditorRows`), the leave sheet's abandon (`WorkoutSheets`); a falling delta (`ProgressCard`); the `Reduce load` outcome (`ProgressionOutcomeStyle`); light `Protect` (`ReadinessBandStyle`, deviation D41) | `#eb827b` | `#a74541` | on `background` 6.72 / 4.77; on `surface` 5.80 / 4.54 | 4.5 |

## Deliberately unassigned (Material 3 baseline is the accessible choice)

| Role | Why | Baseline ratio |
| --- | --- | --- |
| `outline` | Every `OutlinedTextField`'s resting border where it is not overridden — the 4 fields at default colours (the library, plan-picker and workout-picker searches, and Erase's confirmation field); the 5 others pass `RepFlowColor.hairline`. The design's hairline measures 1.2–1.8:1 there. Exposed as `RepFlowColor.hairline` instead. | dark 4.80 / 5.56, light 4.19 / 3.70 (3:1 floor) |
| `onSurfaceVariant` | **Stock only, no direct read.** 9 `OutlinedTextField`s' label / placeholder / leading and trailing icon slots (none of them overrides those slots); 6 `AlertDialog` bodies (`textContentColor`); `DatePicker`'s weekday and navigation glyphs. The design's 55–60% value composites to 3.20–3.34:1 there. Text the converted screens draw at that tier uses `repFlowSecondaryTextColor` instead (the CP3 table). | dark 8.91 / 10.33, light 8.59 / 7.59 |
| `secondaryContainer`/`onSecondaryContainer` | **No consumer at HEAD.** `FilterChip` is retired, the nav's selected pill is CP4's own override, and the rest strip's `LinearProgressIndicator` passes its `trackColor`. Left at baseline; a future stock reader is audited here first. | Material 3's opaque pair clears AA |
| `outlineVariant` | **No rendered consumer at HEAD:** every `HorizontalDivider` passes `color`, and `FilterChip` is retired. Decorative wherever it appears. | decorative, no floor owed |
| `primaryContainer`/`onPrimaryContainer` | **No consumer at HEAD.** Both `FloatingActionButton`s gave way to `RepFlowBottomActionBar` (CP10, CP11), which closes the parent's disclosed two-FABs limitation. | not computed |
| `inverse*` | One stock `Snackbar` (`ExerciseEditorScreen.kt:89`, the exercise editor's message snackbar). The five other snackbars are the design's card and set their colours explicitly. | not computed |
| `surfaceContainerLow` | **Remediation-1 CP3's role decision for `RepFlowSheet`.** `ModalBottomSheet`'s default container is `BottomSheetDefaults.ContainerColor` → `SheetBottomTokens.DockedContainerColor` → this role (`material3-android-1.4.0`). `RepFlowSheet` passes `containerColor = surface` explicitly, so the role has **no consumer** at HEAD: no other `ModalBottomSheet`, `BottomSheetScaffold` or other reader of it is imported. Leaving it unassigned keeps one fewer token to keep in sync; a future stock consumer of it must be audited here before it ships. | `onSurface` on the baseline values: dark `#1D1B20` 14.10, light `#F7F2FA` 12.83 |

## Scoped to the bottom nav (CP4's `NavigationBarItemDefaults.colors`)

| Value | Composite | Ratio | Floor |
| --- | --- | --- | --- |
| unselected icon+label, dark: `onSurface` @ `navUnselectedAlphaDark` (.60) over `#1b1d2b` | `#97979f` | 5.76 | 4.5 |
| unselected icon+label, light: `onSurface` @ `navUnselectedAlphaLight` (.66) over `#f3f5fe` | `#6e7077` | 4.55 | 4.5 |
| selected pill, dark: `primary` @ `navSelectedIndicatorAlphaDark` (.20) over `#1b1d2b`, label `accent300` | `#33324e` | 8.19 | 4.5 |
| selected pill, light: `primary` @ `navSelectedIndicatorAlphaLight` (.16) over `#f3f5fe`, label `primary` | `#dbdbed` | 4.96 | 4.5 |

The design's literal light unselected value is 55% opacity, which composites to
3.34:1 — below the 4.5:1 an always-visible `NavigationBarItem` label owes. 66%
is the first step that clears it (65% still measures 4.42:1). A single shared
value covers both icon and label.

## Remediation-1 CP3 — structural primitives

`ModalBottomSheet` (inside `RepFlowSheet`) is the one stock sheet; its
container role is settled in the `surfaceContainerLow` row above, its scrim
is `RepFlowColor.sheetScrim` (passed explicitly, so `ScrimTokens` is not
read) and its drag handle is RepFlow's own. `HorizontalDivider` (list row,
bottom-bar edge) and `Scaffold` (`RepFlowScreenScaffold`) are given explicit
colours. Text the primitives render, per ground (pinned in
`RepFlowPrimitivesTest`'s contrast region), and the converted screens reuse
through the same helpers:

| Text | Ground | Dark | Light | Floor |
| --- | --- | --- | --- | --- |
| Secondary text — `onSurface` @ `secondaryTextAlphaDark` (.55) / `secondaryTextAlphaLight` (.70): section label, list-row meta, scale end labels, stat caption, keypad title | `background` / `surface` / `surfaceContainer` | 5.19 / 4.83 / 5.08 | 4.84 / 5.15 / 5.15 | 4.5 |
| The design's own 45% tertiary step, for the record (why the labels are lifted — deviation D39) | `surface` | 3.72 | — | 4.5 (fails) |
| Bottom-bar secondary label — neutral outline, `onSurface` @ .8 | `surfaceContainer` | 9.24 | 7.02 | 4.5 |
| Keypad digits, `onSurface` | `control` | 11.69 | 7.03 | 4.5 |
| Stat figure; sheet content, `onSurface` | `surface` | 12.55 | 13.01 | 4.5 |
| `up next` chip, accent-300 on `primary` @ .20 (dark); the selected-pill fallback (light) | `background` / `surface` | 8.73 / 7.50 | 6.23 on `surface` | 4.5 |

Measured where the checkpoint that built them introduced them, and pinned by
their own tests rather than re-derived here: the readiness band colours on
both grounds (CP4, `ReadinessBandStyleTest`, deviation D41) and the CP15
Progress tiers (deviation D111, at the secondary tier above).

Decorative, no floor owed: the list row's trailing caret (`onSurface` @ .30;
the row's title is its label), the 9% divider, the bar's 12% top edge, the
sheet grabber at 25% and the stepper value's 10% ring (the value text inside
it is the affordance's label).

## Known decorative narrowings

These carry no text floor, and are recorded so they are not rediscovered as
defects: dark off-state switch track (`onSurface` @ 18%, RepFlow's own
switch, deviation D107) on `background` — the knob and the row's label carry
the state; light card container on `background` 1.05.
