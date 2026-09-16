# Problem

Nested destinations keep the five-tab bottom bar, have no TopAppBar/Up affordance, and discard tab state. The app has no adaptive navigation. Tertiary normal text has insufficient contrast, custom semantics are sparse, and 1.5x font scale visibly breaks controls/navigation labels.

# Scope

- Use one standard scaffold policy that distinguishes top-level and nested destinations.
- Add M3 top app bars and Up behavior where appropriate; preserve independent top-level back stacks/state.
- Switch bar/rail behavior by window size with Material 3 adaptive navigation.
- Correct semantic color contrast and audit dynamic type layout.
- Add heading, live-region, pane-title, progress, selection, and content-description semantics where custom composites require them.
- Test TalkBack/switch access, 1.0/1.5/2.0 font scale, narrow/wide windows, and state restoration.

# Acceptance

- Nested routes expose predictable Up behavior without irrelevant tab chrome.
- Top-level destinations restore their prior state.
- Compact and expanded windows use appropriate navigation.
- Normal text meets contrast requirements and no essential label clips or breaks unusably at supported font scales.

# Evidence

WLO-0057 report and API-29 emulator screenshots.
