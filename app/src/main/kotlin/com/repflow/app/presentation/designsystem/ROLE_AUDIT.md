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

Imported Material 3 components, audited: `AlertDialog`, `Button`, `Card`,
`Checkbox`, `CircularProgressIndicator`, `DatePicker`, `DatePickerDialog`,
`DropdownMenu`, `DropdownMenuItem`, `FilterChip`, `FloatingActionButton`,
`HorizontalDivider`, `IconButton`, `ListItem`, `NavigationBar`,
`NavigationBarItem`, `OutlinedButton`, `OutlinedTextField`, `Scaffold`,
`Snackbar`, `Switch`, `Text`, `TextButton`, `TopAppBar`.

**Two paths, not one.** The sentence that used to stand here said
`presentation/**` contained no `Color(...)` literal and no direct
`MaterialTheme.colorScheme` read, so stock component defaults were the whole
story. That described the pre-milestone codebase (one direct read at
`b39af90`, in `RepFlowTheme.kt`'s own scheme assignment) and is false of the
code this file now ships beside. What is true:

- **Colour literals live in `designsystem/` by design.** `RepFlowColor.kt` is
  a token file — definitionally a list of transcribed `Color(0x…)` literals.
  The one-way rule is that no *other* file under `presentation/` carries one,
  and it is re-verified by grep every round (currently 0 hits outside
  `designsystem/`), not assumed.
- **The three reskinned surfaces read `ColorScheme` roles directly.** 29
  `MaterialTheme.colorScheme` reads sit under `presentation/` at HEAD, 14 of
  them outside `designsystem/`: `RepFlowBottomNavigationBar.kt:42` (covered by
  the bottom-nav section below), `ExerciseListScreen.kt:188,189,266,355`,
  `ActiveWorkoutScreen.kt:278,300` and
  `ActiveWorkoutExerciseCard.kt:327,338,354,360,511,574,585`.

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
| `onSurfaceVariant` | Also 23 `OutlinedTextField` labels, 6 `ListItem` supporting lines, 4 dialog bodies, 5 `OutlinedButton` labels; the design's 55–60% value composites to 3.20–3.34:1 there. Applied via CP4's per-item override instead. **Direct reads (baseline value, not the design's):** the filter-row funnel tint (`ExerciseListScreen.kt:266`, on `background`); the Exercise-list row meta line (`:355`) and CP6's logged-set detail line (`ActiveWorkoutExerciseCard.kt:360`), both on card `surface`; three in-card glyph tints (`:511,574,585`, on card `surface`). All six are covered by the baseline ratios beside this row. | dark 8.91 / 10.33, light 8.59 / 7.59 |
| `secondaryContainer`/`onSecondaryContainer` | Also `FilterChip`'s selected state on three untouched screens, where the nav's own pair measures 4.41:1. Applied via CP4's per-item override instead. | Material 3's opaque pair clears AA |
| `outlineVariant` | `FilterChip` unselected outline and 3 `HorizontalDivider`s; the design's own divider value has no consumer this milestone builds. | decorative, no floor owed |
| `primaryContainer`/`onPrimaryContainer` | Both `FloatingActionButton`s. Disclosed limitation: CP5 restyles one FAB directly, the other keeps the baseline. | not computed |
| `inverse*` | `Snackbar` only; unchanged by this milestone. | not computed |

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
