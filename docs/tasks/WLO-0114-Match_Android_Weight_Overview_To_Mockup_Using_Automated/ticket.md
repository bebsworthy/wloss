---
id: WLO-0114
title: Match Android weight overview to mockup using automated glyph overlays
status: done
theme:
release:
created: 2026-09-16T18:32:07Z
modified: 2026-09-16T18:43:01Z
closed: 2026-09-16T18:43:01Z
revision: 457526894ebf82da
blocks: []
related: []
---

# Description

# WLO-0114 — Match the app to the original Inter mockup

## Delivered

Replaced the screenshot-scale-driven typography reductions with measured reference roles: 56sp medium hero, 24sp change, 17sp goal, 13sp full date, 12sp supporting copy, 14sp range labels and 15sp history/action. Explicit line-height trimming prevents Compose from collapsing the intended leading. The summary centers the two value/caption groups, and the goal stays a standard M3 TextButton. Restored the reference's header/content gutters and section spacing. Native segmented buttons now have the reference's 44dp visible outline with preserved touch targets and no checkmark.

Rebuilt the chart geometry from the actual SVG proportions rather than a fixed 220dp height: matching plot aspect, gutter, grid spacing and date-label position; larger latest-trend marker; three short-window ticks or calendar-month quarter ticks. Axis bounds remain dynamic and include the goal. Restored the sparse-window “readings from…” caption. Data calculation logic is unchanged.

The final comparison caught a tabular-numeral advance mismatch on the hero's “1”. The page hero now uses the mockup's proportional lining figures; the governing R-D3/design-system docs record this narrow exception to satisfy the owner's request to match the approved reference. Delta/table/axis roles retain their existing numeric contracts.

## Automated visual evidence

The agent captured the mockup and emulator itself with the new comparison tool, using the actual first 7 as anchor. `matching-data.js` applies the existing synthetic emulator readings and persisted trend to browser memory only; the original HTML is not changed. `--prepare-script` was added to the generic capture tool for this repeatable fixture setup.

- `pass-01`: reference-sized type and expanded chart; exposed selector offset.
- `pass-02`: corrected selector and calendar ticks; chart grid now nearly coincident.
- `final`: caught tabular-numeral advance mismatch; retained as intermediate evidence.
- `verified`: final installed build with matching hero advances and corrected geometry.
- `point.png`, `settings.png`, `30-days.png`, `90-days.png`: device QA.
- `large-text.png`, `large-chart.png`: 200% text, including tooltip and scroll-to-goal/history review.

At equal width the reference first 7 is 47px tall; app ink 107px × 470/1080 = 46.57px. Glyph alignment needs about 1.3px horizontal and 0.1px vertical translation. The chart and summary grids now largely coincide. This is not a claim of identical rasterization or identical platform chrome.

Android's system navigation bar is preserved. The prototype does not model that bar, so the native footer sits higher and the main content may need scrolling to reach history on this three-button-navigation emulator. Do not draw the action under OS controls to hide that difference. The user's full date without comma remains, and platform date abbreviations can differ. Reference/demo history metadata may differ from the app's actual entry metadata.

## Validation

The complete run passed 89 unit tests (design system 24, weight 42, app 23), debug build, lintDebug, ktlintCheck, detekt and checkArchitecture. Two new tests verify calendar-month quarter ticks and short-window endpoints/midpoint. Subsequent small spacing/label/hero refinements passed build, ktlintCheck and detekt again; design-system tests were rerun after chart-label refinement. The comparison tool's four Python tests also pass.

Device checks passed: 30/90-day switching, selecting an actual measurement and showing its date/weight tooltip, settings opening, 200% text and vertical scrolling to goal/history. Font scale restored to 1.0. APK installed in place; no data cleared. Existing unrelated working-tree changes preserved.
