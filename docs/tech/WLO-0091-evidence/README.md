# WLO-0091 implementation evidence

Synthetic preview content only; no health records or credentials are included.

## Acceptance coverage

- **A91-01:** `ThemeContrastTest` enumerates and prints every active text/container pairing used by the shared weight primitives in dark and light schemes. It also tests the effective 16% selected-chip composite and the essential outline boundary.
- **A91-02:** `WloComponentGallery` provides interactive dark/light previews for default, selected, disabled, busy, and error states. Android Studio interactive preview supplies pressed/focused state inspection. Buttons, chips, switches, list rows, cards, sheets, dialogs, and icon actions delegate to Material 3.
- **A91-03:** the gallery includes a 320 dp, 200% font preview with a long button, long `ListItem`, long supporting text, lb value, and error supporting text. Shared interactive row/button/icon floors are 48 dp.
- **A91-04:** provenance remains a static semantic surface and does not expose a dead info action. `WloBadge` remains non-clickable/non-focusable.
- **A91-05:** the source audit below records each retained shared wrapper and exception.
- **A91-06:** light/dark and large-font preview entry points compile. Runtime screenshot capture is deferred to WLO-0099's disposable-emulator validation; the release continues to force dark by policy.

## Wrapper source audit

| WLO primitive | Standard delegate / exception |
|---|---|
| `WloButton` | Material 3 `Button`, native shape/colors/semantics |
| `WloSecondaryButton` | Material 3 `OutlinedButton` |
| `SelectChip` | Material 3 `FilterChip` |
| `WloCard` | Material 3 `Card`, clickable overload when interactive |
| `WloCardHeader` | Standard `Text` in a slot-only `Row`; sentence-case title role |
| `WloSheet` | Material 3 `ModalBottomSheet` with title slot and pane semantics |
| `WloDialog` | Material 3 `AlertDialog`, native dialog shape |
| `WloListRow` | Material 3 `ListItem`; optional row-level click modifier |
| `WloIconAction` | Material 3 `IconButton` / `FilledIconButton` |
| `WloSwitchRow` | Material 3 `ListItem` + `Switch`, one merged switch target |
| `WloScreenTitle` | Material `Text` with heading semantics |
| Charts/rings/heatmaps | Retained custom Canvas data visualizations; M3 has no equivalent chart components |
| `WloSwipeRevealRow` | Retained release-gated, velocity-blind custom gesture; its KDoc records the stock swipe limitation and ticket |
| Provenance/status surfaces | Retained noninteractive domain semantics; they are not disabled control lookalikes |

## Material 3 self-audit (0–2)

| Area | Score | Evidence |
|---|---:|---|
| Task clarity | 2 | Shared contracts and labels remain explicit |
| Hierarchy | 2 | Separate screen/sheet/card/body/supporting roles |
| Semantics | 2 | Standard controls and heading/pane roles |
| Tokens | 2 | Complete schemes plus extended light/dark semantic foregrounds |
| Adaptive layout | 1 | Primitives reflow and large-font preview; shell adaptation belongs to WLO-0096 |
| States | 2 | Gallery covers rest states; M3 owns interaction states |
| Accessibility | 2 | Contrast automation, 48 dp targets, wrapping content |
| Restraint | 2 | No dynamic color, new preference, or screen rewrite |

## Commands and results

- `./gradlew :core:designsystem:testDebugUnitTest :app:testDebugUnitTest` — passed.
- `./gradlew :core:designsystem:ktlintCheck :core:designsystem:detekt :app:assembleDebug checkArchitecture` — passed after preview lint cleanup.

## Unverified checks

- Physical TalkBack traversal, switch announcements, and focus rings were not manually verified on hardware.
- No runtime screenshots were captured in this ticket; the compiled light/dark previews are the visual comparison source until WLO-0099's validation pass.
