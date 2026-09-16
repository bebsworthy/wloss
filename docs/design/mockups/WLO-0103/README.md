# Weight overview — proposed redesign

WLO-0103 · 16 September 2026. Implements the owner's latest design direction as an interactive HTML mockup, not production Compose code. Open `weight-overview.html`. Images: `overview.png`, `selected-point.png`, `settings.png`.

## Design decision

One overview, one graph, one primary action. Replace the stacked hero/trend/settings cards with a continuous screen hierarchy: current trend → selected-period change and goal → period selector → graph → history. Keep Weigh in in a reserved action region above bottom navigation. The graph is not wrapped in a large outlined card. Remove the inline legend, View data/detail/arrow block, smoothing controls, prominent math button and “consistent window” text.

The illustrative data begins 3 August at78.3kg and ends16 September at77kg. The 90-day view shows a1.3kg reduction across the available data; the 30-day view yields0.8kg with this illustrative series. These are mock data, not a reconstruction or verification of the screenshot dataset. The74kg goal is illustrative.

## Interaction contract

- 30d/90d/1y/All crop the same stable trend series; they never re-seed it. The change compares the first and last valid trend values inside that range. Display their actual dates directly below the result. Today’s hero is independent of viewport; a preview changes it only with a visible Preview badge and Reset.
- A yellow dashed horizontal goal line and direct label always remain inside the plot whenever an active target exists. Compute y-domain from union of visible values and goal, with label padding; no broken axes or offscreen goal indicator. This can compress the visible fluctuations. Do not secretly stretch the curve separately from the target. Production should choose readable ticks in the chosen unit. Without a goal, do not invent one; show a secondary Set goal route. A maintenance range requires explicit band design rather than silently treating one boundary as the target.
- Owner refinement: retain individual recorded weights as muted, unconnected dots behind the teal trend. Their visible fluctuations explain why the smoother exists, without an inline legend. Plot actual raw events, including multiple readings per day, rather than daily medians presented as measurements. History remains the full record. The page-based composition is the preferred direction.
- Tap a dot to select its recorded value, or near the line to select its trend value; use two-dimensional hit testing, retaining the selected series while scrubbing. drag along it to scrub. Show only date and formatted weight in a compact anchored tooltip. No content is inserted below the graph. Outside tap or Escape dismisses. Tooltip stays on screen and avoids covering the selected point. Do not select the first sample when tapping blank months far from data.
- Accessibility is retained without a permanent visual data block: focusable chart with named value, date and unit, previous/next custom accessibility actions and keyboard arrows/Home/End. Up/down switches between trend and recorded weight in this prototype; spoken values identify the series. Production must also preserve event identity/time for multiple same-day weigh-ins. Announce changes politely; expose role as smoothed trend without cluttering the visual tooltip. Provide a screen-reader-accessible alternative if the production chart cannot support this equivalently. Source identity remains available through settings/help/history.
- Header settings icon opens a modal bottom sheet or dedicated chart settings screen in Android. Three standard radio rows explain methods. Do not use a side navigation drawer for this small local settings task. Method changes are explicit previews under the existing owner ruling, with Reset and clear difference from saved calculations. The mock uses illustrative alternative curves, not the production algorithms.
- Alpha, if retained, belongs in advanced settings and appears only for applicable methods. Include a reliable Restore defaults action. No alpha slider is required on the routine screen.
- “How the trend is calculated” lives inside settings as a low-emphasis expandable/help row. Full source traceability and formula detail remain available there, not serialized into the dashboard. It describes the daily selection policy, coverage, initialization, parameters and contributing records. Weak data is held, not invented.
- Keep the latest raw reading as supporting information in the history row. Avoid adding a second large competing weight value. Body metrics remain reachable elsewhere in the actual app; this weight-focused concept does not delete them or redesign those flows.

## M3 implementation map

Use Material3 Scaffold, TopAppBar with named IconButton, SingleChoiceSegmentedButtonRow/SegmentedButton (standard selected check/state), Text typography roles, ListItem for history/settings, RadioButton, ModalBottomSheet, filled Button for Weigh in, and NavigationBar/NavigationBarItem. HTML approximates their appearance; it is not a new component library. The bespoke chart is justified by data visualization requirements and must supply accessible selection semantics.

Use existing dark semantic surfaces/text/teal primary. Introduce a dedicated `chartGoal` yellow role rather than reusing the held/warning token; combine dashed geometry and direct label so color is not the only distinction. Validate a darker yellow-family role in light theme. Neutral change text remains identical for gain/loss: no moral coloring. No celebratory animation for lower values.

Production compact layout scrolls only the content; the reserved bottom action and navigation remain reachable without covering it. Scale chart label padding with text size. At wide widths, use a supporting pane for goal/history while preserving chart proportions; do not stretch this phone mock across a tablet. Choose calendar tick boundaries and suppress collisions. Dates localize, units follow preferences, and all displayed differences use unrounded source values before presentation rounding.

## Motion

Use immediate range selection feedback; modest opacity transition for tooltip, respecting reduced motion. Avoid morphing between unrelated points when changing range or method. Selection does not change page height or scroll position. No haptic on every drag pixel; optional subtle selection tick only when the selected day changes, subject to user settings. Settings follows standard M3 sheet motion and focus restoration.

## Verifiable acceptance

1. Same date has identical trend value in every viewport. Summary equals last minus first within the selected range, with exact coverage dates. 78.3→77.0 displays−1.3kg, independent of any unrelated30-day metric.
2. A valid goal remains visible for all ranges, above/below/current data, in kg/lb and with outliers. No nonlinear or separately scaled target positioning.
3. Routine screen has no debug/provenance serialization, inline data controls, smoothing panel, large math CTA or consistent-window copy.
4. Selection exposes date/weight only visually, without layout movement; keyboard and screen-reader selection provide equivalent access. Tooltip is clamped at every edge.
5. 320dp and200% text: no clipped options, overlapping axis labels, obscured controls or FAB collisions. Text/action rows may wrap; plot maintains readable minimum geometry.
6. Settings owns method/help, gives clear descriptions, and never changes saved data by merely previewing. Reset restores canonical values before/after refresh.
7. Zero/one/sparse/stale readings and missing target have truthful states; no fake trend/delta. Gaps and coverage are explained with concrete dates, not engine jargon.
8. Production date/goal/current/change all derive from one documented calculation contract. Fix calendar MA7 and window-seeding issues before treating the design as evidence of numeric correctness.

## Validation and limits

Rendered in headless Chromium at412px and visually inspected. Tested90d/30d summaries, settings opening and keyboard first-point inspection. Screenshots regenerated after fixing a collision between19Jun and1Jul ticks. This is a concept review, not Android runtime or TalkBack validation. Sample goal/data, decorative status bar and alternate-mode curves are illustrative. Entry is a non-saving layout preview; Hub/Plan are outside prototype scope; More opens settings for demonstration.

M3 self-audit: task clarity2; hierarchy2; component semantics1 (HTML prototype, map supplied); token discipline1 (new goal role needs production validation); adaptive behavior1 (phone designed, tablet contract only); states/feedback1 (key mock interactions present, edge states specified); accessibility1 (keyboard implemented, native verification outstanding); expressive restraint2. Ready for design feedback, not production acceptance.

Owner refinement validated:45 recorded-weight dots visible in90d,30 in30d; pointer selection reports the recorded value and keyboard can switch series. Raw sample noise is illustrative, not used to claim the mock trend reproduces production EWMA.

## Typeface alignment (WLO-0107)

The HTML now loads the app’s bundled Inter Regular, Medium, Semibold and Bold directly from `core/designsystem/src/main/res/font` using relative @font-face URLs. No network font dependency. Hero and change figures use Medium (500); supporting text uses Regular (400). Intermediate 550/650 requests were normalized to the bundled Semibold (600). `inter-overview.png` is the refreshed capture; older captures may show the original system font. Keep the repository-relative layout when opening this HTML.

## Emulator display size (WLO-0110)

The original HTML defaults to the owner-measured 470 × 1045 desktop screen points at 100% browser zoom. It scales the entire 411.43-unit logical layout uniformly and scrolls main content inside the fixed viewport. An external width field supports later emulator resizing and remembers the setting. Reload the HTML after updating. Android code and typography are unchanged.
