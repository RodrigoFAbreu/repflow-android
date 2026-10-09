# RepFlow Design System

Read this before any UI work. The source of truth is code under
`app/src/main/kotlin/com/repflow/app/presentation/designsystem/`; this page is
the map and the rules. When the two disagree, the code wins -- fix this page.

## Golden rules

1. **Tokens only.** No literal colours, dp spacing, radii or text sizes in
   screens. Use `MaterialTheme.colorScheme`, `RepFlowColor`, `RepFlowSpacing`,
   `RepFlowShapes`, `MaterialTheme.typography`, `RepFlowNumericTextStyle`.
2. **Shared components first.** Build from `designsystem/components/` before
   writing a new composable. Add to that folder only when a second screen needs it.
3. **Contrast is a hard floor.** Text owes 4.5:1 on every surface it renders on.
   Changing a colour value or adding/restyling a consumer means re-running
   `designsystem/ROLE_AUDIT.md`.
4. **Large fonts must work.** No `maxLines = 1` on values or captions that can be
   truncated; wrap, stack (`FlowRow`, column fallback) and test at font scale 1.3
   and 2.0 (history: `D163`, `P3-F-1`).
5. **Workout-first ergonomics.** 44dp minimum touch targets, one-handed reach,
   no network, no blocking UI during a set.

## Tokens

| Area | API | Values |
|---|---|---|
| Colour (Material slots) | `MaterialTheme.colorScheme` | Dark `background` `#161826`, `surface` `#232532`, bar `#1B1D2B` |
| Colour (extras) | `RepFlowColor` (`control`, `hairline`, accent ramp, alphas) | accent 300/600/700/900; secondary text 55% dark / 70% light |
| Secondary text | `repFlowSecondaryTextColor(scheme)` | never hand-pick an alpha |
| Theme detection | `isDarkColorScheme(scheme)` | read from the applied scheme, not `isSystemInDarkTheme()` |
| Spacing | `RepFlowSpacing` | gaps 6/8/10/12, card padding 14-18, screen padding 16 |
| Shape | `MaterialTheme.shapes`, `RepFlowShapes` | 8 controls, 12 cards/buttons, 16 sheets, 9 stepper, 18 FAB, pill |
| Type | `MaterialTheme.typography`, `RepFlowNumericTextStyle` | Inter 400/500; display 32, title 25, body 15, meta 12.5, label 11 caps; numeric 32 tabular |
| Icons | `designsystem/icons/RepFlowIcons.kt` | add icons here, not inline |

Type weight 600 exists for the primary button label only. Do not use it elsewhere.
Numbers that change (load, reps, timer, volume) use the tabular numeric style so
digits do not reflow.

## Components (`designsystem/components/`)

`RepFlowScreenScaffold`, `RepFlowCard`, `RepFlowListRow`, `RepFlowButtons`,
`RepFlowBottomActionBar`, `RepFlowSheet`, `RepFlowStatTile`, `RepFlowStepper`,
`RepFlowNumericKeypad`, `RepFlowSearchField`, `RepFlowSectionLabel`, `RepFlowTag`,
`RepFlowStateComposables` (loading / empty / error).

Patterns:
- Every screen is a `RepFlowScreenScaffold`; every sheet is a `RepFlowSheet`.
- Empty and error states use `RepFlowStateComposables`, with one clear action.
- Lists use `RepFlowListRow` with hairline dividers, not nested cards.
- One primary action per screen region; secondary actions use outline or text buttons.

## Look and feel (anti-"AI look")

RepFlow is a calm, dark-first, data-dense tool, not a marketing page. Avoid:
- gradients, glows, glassmorphism, decorative blobs, emoji as icons;
- cards inside cards inside cards -- prefer a section label plus rows;
- centered hero stacks, oversized rounded everything, drop shadows for hierarchy;
- more than one accent colour; hierarchy comes from size, weight, and the
  secondary-text tier, not extra colour;
- placeholder or invented copy. Labels are short, literal, and in the voice of
  `docs/DOMAIN_GLOSSARY.md`.

## Motion

Subtle and functional (rule set from `emil-design-eng`):
- Animate only to explain a state change (set logged, rest done, sheet in/out).
- 150-250 ms, ease-out for entrances; never animate things used many times a
  session (set entry) beyond a quick confirmation.
- Respect the system "remove animations" setting.
- Never delay input to finish an animation.

## Audit workflow

| Need | Use |
|---|---|
| Review a screen for UX/accessibility compliance | `web-design-guidelines` (web-oriented; apply the accessibility and interaction rules, ignore HTML-specific ones) |
| Critique, polish, simplify a screen's design | `impeccable` (`/audit`, `/polish`, `/critique`) |
| Motion and micro-interaction decisions | `emil-design-eng` |
| Visual regression on a running app | emulator screenshots (see `AGENTS.md` and the AVD memory limits) |

Note: `design-taste-frontend`, `image-to-code` and most of `impeccable` target web
output (HTML/CSS). For this Compose app use them for *direction and critique*,
and translate to tokens and components above -- never paste web code or styles.

## Checklist before finishing UI work

- [ ] Only tokens and shared components; no new literal colour/dp/radius.
- [ ] Light and dark both checked; contrast floor met (`ROLE_AUDIT.md` if a value changed).
- [ ] Font scale 1.3 and 2.0: nothing clipped, wrapped mid-word, or unreachable.
- [ ] Touch targets >= 44dp; content descriptions on icon-only controls.
- [ ] Empty, loading and error states exist.
- [ ] No Room entities or business logic leaked into composables.
