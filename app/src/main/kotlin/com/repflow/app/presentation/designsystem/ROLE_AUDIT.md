# ColorScheme role → consumer → value → contrast audit

Re-run this table before changing any value in `RepFlowColor.kt`. A newly
discovered stock consumer, or a ratio that drops below its floor, is a token
finding — fix the value (or scope the treatment to the one component that
wants it) rather than shipping the role.

Method: every `androidx.compose.material3` import under `app/src/main/kotlin`
cross-referenced against `material3-android-1.4.0`'s token files to find which
`ColorScheme` role each component reads; ratios are WCAG relative-luminance,
computed (not eyeballed) against every surface the role actually renders on.
Alpha roles get one ratio per background, not one overall. A value scoped to
one component still owes its floor inside that component.

**Counting basis, stated once so every figure below is reproducible.** Counts
are *rendered call sites at HEAD* — a composable invoked seven times through a
shared private helper counts seven, not one, because seven labelled fields
reach the screen. The alternative basis, `grep -c` over the component's own
name, disagrees wherever this milestone consolidated call sites, and is not
what any figure here uses.

Imported Material 3 **components**, audited: `AlertDialog`, `Button`, `Card`,
`Checkbox`, `CircularProgressIndicator`, `DatePicker`, `DatePickerDialog`,
`DropdownMenu`, `DropdownMenuItem`, `FilterChip`, `FloatingActionButton`,
`HorizontalDivider`, `Icon`, `IconButton`, `LinearProgressIndicator`,
`ListItem`, `NavigationBar`, `NavigationBarItem`, `OutlinedButton`,
`OutlinedTextField`, `Scaffold`, `Snackbar`, `Switch`, `Text`, `TextButton`,
`TopAppBar`.

Two of those 26 are this milestone's own new imports, and both are audited
here rather than assumed benign:

- **`LinearProgressIndicator`** (`ActiveWorkoutScreen.kt:293`, CP6's rest
  timer — its only call site). Its defaults would read two roles:
  `ProgressIndicatorTokens.ActiveIndicatorColor` = `Primary` and
  `TrackColor` = **`SecondaryContainer`**, the role CP4 deliberately leaves at
  the Material 3 baseline. **Neither default is reached**: `color` is set to
  `MaterialTheme.colorScheme.primary` (`:300`) and `trackColor` to
  `RepFlowColor.control` (`:301`), and `drawStopIndicator = {}` (`:304`)
  suppresses the third (`StopColor` = `Primary`). So it adds no consumer to
  any row below.
- **`Icon`** (7 files, 14 call sites) reads **no `ColorScheme` role of its
  own**: its `tint` defaults to `LocalContentColor`, so it renders whatever
  its container provides. Seven of the 14 take that default, and every
  container supplying them already appears in this audit — `NavigationBarItem`
  (`RepFlowBottomNavigationBar.kt:58`; its colours are CP4's own override,
  tabulated in the bottom-nav section), `FloatingActionButton` at an explicit
  `contentColor = onPrimary` (`ExerciseListScreen.kt:191`, the direct read
  already named at `:189`), `OutlinedTextField`'s leading and trailing icon
  slots (`:218`, `:230` — `LeadingIconColor`/`TrailingIconColor` are both
  `OnSurfaceVariant`, the row this milestone leaves at baseline), `IconButton`
  at `:391` and `ActiveWorkoutScreen.kt:286` (itself `LocalContentColor.current`,
  not a role — both sit inside a `RepFlowCard`, which provides `onSurface`),
  and `Button`/`OutlinedButton` via `RepFlowButtons.kt:203`, each at the
  explicit `contentColor` its tier sets. The other seven pass an explicit
  `tint`, and all seven are already named in the `Consumers` column below.

The remaining `androidx.compose.material3` imports are not components and read
no role: `ButtonDefaults`, `ColorScheme`, `ExperimentalMaterial3Api`,
`LocalContentColor`, `MaterialTheme`, `NavigationBarItemDefaults`,
`SelectableDates`, `Shapes`, `SnackbarDuration`, `SnackbarHost`,
`SnackbarHostState`, `SnackbarResult`, `Typography`, `darkColorScheme`,
`lightColorScheme`, `rememberDatePickerState`. They are listed so a maintainer
re-running the import cross-reference gets the same input set this table was
built from.

**Two paths, not one.** The sentence that used to stand here said
`presentation/**` contained no `Color(...)` literal and no direct
`MaterialTheme.colorScheme` read, so stock component defaults were the whole
story. That was true of the pre-milestone codebase - `b39af90` has **zero**
direct reads under `presentation/` - and is false of the code this file now
ships beside. What is true:

- **Colour literals live in `designsystem/` by design.** `RepFlowColor.kt` is
  a token file — definitionally a list of transcribed `Color(0x…)` literals.
  The one-way rule is that no *other* file under `presentation/` carries one,
  and it is re-verified by grep every round (currently 0 hits outside
  `designsystem/`), not assumed.
- **The three reskinned surfaces read `ColorScheme` roles directly.** **14**
  direct `MaterialTheme.colorScheme` reads sit under `presentation/` outside
  `designsystem/` at HEAD: `RepFlowBottomNavigationBar.kt:42` (covered by
  the bottom-nav section below), `ExerciseListScreen.kt:188,189,266,355`,
  `ActiveWorkoutScreen.kt:278,300` and
  `ActiveWorkoutExerciseCard.kt:327,338,354,360,511,574,585`.

  **No total for `presentation/` as a whole is stated here, on purpose.** Grep
  for these reads with a literal dot; an unescaped `.` is a wildcard that also
  matches `MaterialTheme(colorScheme = ...` constructor arguments, so the two
  spellings return different sets. Either spelling also matches prose —
  sentences in this very file, and KDoc in `RepFlowColor.kt` — so any total
  written into this paragraph changes the number it is stating and is stale the
  moment it is written. The prose hits are deliberately not enumerated here
  either: an enumeration is one more self-counting figure, and one the two
  spellings would not even agree on. Scoping to `--include='*.kt'` does not
  repair the total — it still counts `RepFlowColor.kt`'s KDoc. The count
  *outside* `designsystem/` has no such problem, because every self-referential
  mention lives inside it, and it is the half the `Consumers` column is
  actually checked against.

So the table is complete only because the `Consumers` column below carries
*both* — the stock components a role reaches through Material 3's own
defaults, **and** this milestone's own direct reads, each with the ground it
actually renders on. A maintainer re-running this table must re-run both
greps, not just the import cross-reference.

## Assigned roles

| Role | Consumers | Dark | Light | Context → ratio | Floor |
| --- | --- | --- | --- | --- | --- |
| `primary` | `Button` fill; `TextButton` label; `CircularProgressIndicator`; `Switch` selected track; `Checkbox` selected container; `OutlinedTextField` focus outline/label/caret. **Direct reads:** CP5's FAB container (`ExerciseListScreen.kt:188`); CP6's rest-timer progress indicator (`ActiveWorkoutScreen.kt:300`) | `#9184d9` | `#5d5294` | label on `surface` 4.71 / 6.23; on `background` 5.45 / 5.50; both direct reads are fill, over `background` and over `control` respectively | 4.5 text, 3 fill |
| `onPrimary` | `Button` content; `Switch` selected handle; `Checkbox` selected icon. **Direct read:** CP5's FAB glyph (`ExerciseListScreen.kt:189`) | `#161826` | `#f5f4ff` | on `primary` 5.45 / 6.22 | 4.5 |
| `secondary` | `NavigationBarItem` selected label; 13 component families' focus ring | `#e9e9ed` | `#292b31` | on nav container 13.79 / 13.01 | 4.5 |
| `background` | `Scaffold` canvas | `#161826` | `#e4e7f5` | fill only | n/a |
| `onBackground`/`onSurface` | default body text. **Direct reads:** CP6's logged-set summary line (`ActiveWorkoutExerciseCard.kt:354`) and rest countdown (`ActiveWorkoutScreen.kt:278`), both on card `surface`; the set marker's numeral and warm-up glyph (`:327,338`) on `control`. `RepFlowStatusChip`'s `Pending` label and `RepFlowStepper`'s value/glyphs also render on `control` | `#e9e9ed` | `#292b31` | on `background` 14.54 / 11.49; on `surface` 12.55 / 13.01; on `control` 11.69 / 7.03 | 4.5 |
| `surface` | `ListItem` container; `TopAppBar` container | `#232532` | `#f3f5fe` | fill only | n/a |
| `surfaceContainer` | `NavigationBar` bar fill; incidentally 7 `DropdownMenu`s | `#1b1d2b` | `#f3f5fe` | fill only | n/a |
| `surfaceContainerHigh` | 4 `AlertDialog`s, 2 `DatePickerDialog`s | `#232532` | `#f3f5fe` | `primary` action label 4.71 / 6.23; `onSurface` headline 12.55; `onSurfaceVariant` supporting 8.91 | 4.5 |
| `surfaceContainerHighest` | the one bare `Card(` (`TrainingPlanEditorFormFields.kt:123`) | `#232532` (reassigned) | unassigned `#E6E0E9` | `primary` card content 4.71 / 5.23; `error` supporting text 5.80 / 4.54 | 4.5 |
| `error` | `OutlinedTextField` `isError` label + supporting text | `#eb827b` | `#a74541` | on `background` 6.72 / 4.77; on `surface` 5.80; on the light card 4.54 | 4.5 |

## Deliberately unassigned (Material 3 baseline is the accessible choice)

| Role | Why | Baseline ratio |
| --- | --- | --- |
| `outline` | Also every `OutlinedTextField`/`Switch` resting border; the design's hairline measures 1.2–1.8:1 there. Exposed as `RepFlowColor.hairline` instead. | dark 4.80 / 5.56, light 4.19 / 3.70 (3:1 floor) |
| `onSurfaceVariant` | Also 23 `OutlinedTextField` text slots (22 labels + the Exercise-list search field's placeholder, `ExerciseListScreen.kt:211`, which carries no label — `LabelColor` and `InputPlaceholderColor` are the same role), 5 `ListItem` supporting lines, 4 dialog bodies, 5 `OutlinedButton` labels; the design's 55–60% value composites to 3.20–3.34:1 there. Applied via CP4's per-item override instead. **Direct reads (baseline value, not the design's):** the filter-row funnel tint (`ExerciseListScreen.kt:266`, on `background`); the Exercise-list row meta line (`:355`) and CP6's logged-set detail line (`ActiveWorkoutExerciseCard.kt:360`), both on card `surface`; three in-card glyph tints (`:511,574,585`, on card `surface`). All six are covered by the baseline ratios beside this row. | dark 8.91 / 10.33, light 8.59 / 7.59 |
| `secondaryContainer`/`onSecondaryContainer` | Also `FilterChip`'s selected state on three untouched screens, where the nav's own pair measures 4.41:1. Applied via CP4's per-item override instead. | Material 3's opaque pair clears AA |
| `outlineVariant` | `FilterChip` unselected outline and 3 `HorizontalDivider`s; the design's own divider value has no consumer this milestone builds. | decorative, no floor owed |
| `primaryContainer`/`onPrimaryContainer` | Both `FloatingActionButton`s. Disclosed limitation: CP5 restyles one FAB directly, the other keeps the baseline. | not computed |
| `inverse*` | `Snackbar` only; unchanged by this milestone. | not computed |

**How the `onSurfaceVariant` counts moved, since this milestone moved three of
them and a raw `grep -c` now disagrees with all three.** The role's own values
and ratios are untouched; only the inventory is.

- **`ListItem` supporting lines: 6 at `b39af90`, 5 at HEAD.** CP5 replaced
  `ExerciseListScreen.kt:218`'s `ListItem` with `RepFlowCard` + `ExerciseRow`,
  so the sixth line did not disappear — it **moved from the stock bucket to
  the direct bucket**, and is the `:355` direct read named in the same cell.
  The five that remain are `HistoryScreen.kt:143`,
  `HistoryDetailScreen.kt:49`, `RecoveryHistoryScreen.kt:87` and `:104`, and
  `TrainingPlanListScreen.kt:164`, each with exactly one `supportingContent`
  holding one `Text`.
- **`OutlinedTextField` text slots: 23, unchanged — but `grep -c` returns 17.**
  CP6 collapsed `ActiveWorkoutExerciseCard.kt`'s seven inline blocks into one
  private `NumericEntryField` (`:468`) invoked seven times (`:420,427,438,448,
  590,598,605`), so under the counting basis stated at the top of this file the
  figure holds where the call-site count does not.
- **`OutlinedButton` labels: 5, unchanged — but `grep -c` returns 7.** CP3 added
  two `OutlinedButton` call sites of its own (`RepFlowButtons.kt:145`, `:170`),
  and **neither is a consumer**: both pass
  `ButtonDefaults.outlinedButtonColors(contentColor = …)` explicitly — the
  accent-ramp label and `onSurface` at 80% respectively — so
  `OutlinedButtonTokens.LabelTextColor` is never reached. The five stock sites
  are `TrainingPlanEditorFormFields.kt:108`, `ExerciseEditorFormFields.kt:158`
  and `:184`, and `ActiveWorkoutScreen.kt:203` and `:367`.

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

## Known decorative narrowings

These carry no text floor, and are recorded so they are not rediscovered as
defects: dark unchecked `Switch` track on `background` 1.16 (its `outline`
boundary still clears at 4.80); light `Card` container on `background` 1.05;
`FilterChip` unselected outline 1.88 dark / 1.38 light.
