# WLO-0091 — Material 3 foundations and accessible semantic tokens

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A05/A07: replace lookalike control styling with standard M3 structure, and make the semantic color/type system work in light and dark themes. This is a shared foundation, not a mass layout rewrite.

Primary files: `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/{WloTheme,WloTokens,WloTypography,WloButtons,WloChips,WloCard,WloCardHeader,WloSheet,WloDialog,WloListRow,WloIconAction,WloSwitch,WloScreenTitle}.kt`; `app/src/main/kotlin/app/wlo/app/MainActivity.kt`; `docs/design/DESIGN-SYSTEM.md`. Audit callers before changing wrapper signatures. WLO-0088 owns number/provenance semantics, WLO-0092 overview, WLO-0093 forms, WLO-0096 shell, and WLO-0098 chart/motion. Supply compatible primitives first; coordinate migrations instead of editing all screens concurrently.

## Theme contract

1. Provide complete explicit semantic role sets for primary/secondary/tertiary and their on/container pairs, surface/container/onSurface/onSurfaceVariant, outline/outlineVariant, error/onError/errorContainer/onErrorContainer, inverse roles and scrim. Brand remains muted wellness palette, Inter/tabular numeric treatment and no alarm red per R-D1/R-D3. Use accessible amber error foreground/container pairs; do not put a pale amber status color directly on a light surface.
2. Normal text must meet 4.5:1; large text 3:1; essential component boundaries/indicators 3:1 against adjacent colors. Test effective blended colors where alpha is used. Decorative dividers need not pretend to be essential boundaries. Disabled controls follow native M3 states and remain identifiable; do not apply active-text thresholds indiscriminately to disabled content.
3. Existing light secondary `#9AA6B5` on white (~2.47:1), held `#E8B34B` (~1.91:1), developing `#7FA6C9` (~2.56:1) cannot remain normal text pairings. Replace semantic foregrounds, not each screen with local hex patches. Text/icon labels must also convey held/developing/error; color is supplementary.
4. Keep the current product theme-selection policy unless explicitly amended; MainActivity currently forces dark. Deliver light/dark previews and tests even if only dark is currently the release default. Do not silently switch to dynamic color or add a settings preference. Document this limitation clearly.

## Component and typography contract

| Purpose | Required implementation |
|---|---|
| Primary transaction | M3 Button, documented content/shape/color parameters |
| Secondary action | OutlinedButton or TextButton by emphasis |
| Binary preference | M3 Switch with labeled ListItem |
| Mutually exclusive short choices | SingleChoiceSegmentedButtonRow/SegmentedButton where available in installed M3 |
| Optional filters | FilterChip; not generic navigation/action |
| Static grouped content | Card/OutlinedCard/ElevatedCard by semantic role |
| Row | ListItem with headline/supporting/leading/trailing slots |
| Modal edit | ModalBottomSheet; M3 AlertDialog for confirmation |
| Icon action | IconButton with name and full touch target |

Keep custom Canvas charts, approved release-gated swipe reveal, and genuinely noninteractive provenance/status surfaces where justified. A static status pill is not a disabled button. Preserve Wlo wrappers when they provide typed domain semantics; they must delegate metrics/layout to standard components. Remove global 8dp button/chip shape overrides unless a documented component-specific reason exists; use M3 shape roles. Do not flatten every control to one visual style.

Map actual Material typography roles distinctly. Screen/app-bar title, sheet title, card title, body, supporting text and dense numeric stat must not all reuse uppercase 11sp labels. Keep small uppercase text only for nonessential overlines; user-entered text and action labels use sentence case. Define aliases in the design-system doc and preserve font scaling; no fixed-height text containers or essential-value ellipsis as the only rendering.

## Acceptance scenarios

- A91-01: a token contrast test enumerates every active role pairing used by weight surfaces in both themes, reports actual ratios and passes the thresholds above. Test composites, not only palette swatches.
- A91-02: a component gallery shows default, pressed, focused, selected, disabled, busy/error where applicable. M3 owns ripple, focus indication, padding and semantics. TalkBack identifies button/switch/selection roles correctly.
- A91-03: at 320dp/200% font, labeled buttons, a long ListItem, sheet heading, stat with lb value and error supporting text wrap/reflow without losing value/action. Interactive targets measure at least 48dp, including icon/info buttons.
- A91-04: no weight UI renders an info mark with a missing action; no static badge is made focusable merely to imitate a chip. WLO-0088's provenance tests remain green.
- A91-05: source audit records every retained custom wrapper and its standard delegate or justified exception; no hand-rolled button, list row or switch is retained solely for pixel matching.
- A91-06: screenshots of overview, capture, Goals, logbook and import in both themes show one coherent hierarchy. Regressions in onboarding/Hub caused by shared wrappers are corrected within this ticket's primitive scope.

## Validation and boundaries

Add focused contrast/semantics/layout tests, not brittle snapshots of every pixel. Use repository Compose previews/instrumentation for actual rendered states and record the M3 skill self-audit (0/1/2 across task clarity, hierarchy, semantics, tokens, adaptive layout, states, accessibility, restraint). Any zero in semantics/states/accessibility prevents sign-off. Consult official M3 and Android Compose documentation for API behavior; do not upgrade libraries to chase an Expressive API.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :core:designsystem:testDebugUnitTest :app:testDebugUnitTest
```
