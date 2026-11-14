# Problem

The `uiAtoms` architecture rule bans standard Material 3 imports from feature/app code while exempting `:core:designsystem`, where multiple controls are hand-built. Correct WLO-0kw9 `ListItem` work currently fails CI.

# Scope

- Remove the banned-standard-M3-import policy.
- Replace it with checks for fake/lookalike interactive controls, undersized targets, raw hard-coded tokens, missing semantics, and unjustified custom controls.
- Replace `WloSegmentedBar` with M3 segmented buttons, `WloCheckRow` with M3 Checkbox/list anatomy, and `WloPrimaryRow` with an appropriate M3 button.
- Rebase `WloSwitchRow` and `WloTemplateCard` on standard M3 primitives.
- Classify the remaining WLO atoms per the WLO-sbnk disposition; preserve charts/provenance and justified `WloSwipeRevealRow`.
- Allow direct M3 use wherever its public API is the correct API.

# Acceptance

- WLO-0kw9's standard `ListItem`/`TextButton` usages can pass architecture checks.
- No hand-built substitute remains where an M3 component fits.
- Custom controls carry KDoc naming the exact M3 limitation and the owning ticket.
- Theme/tokens remain centralized without erasing standard component slots/state.

# Evidence

WLO-sbnk report, M3 disposition; related to WLO-dj08/WLO-jdy2/WLO-0kw9.
