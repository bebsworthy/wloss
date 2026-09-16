# WLO-0109 — Measured overview geometry

## Measurement finding

The user's overlay aligned the first hero digit but did not establish equal screen widths. The previous screenshot-only analysis overstated a layout-width defect. Actual measurements: Android 1080px at 420dpi = 411.43dp, 24dp content gutters, 363.43dp content width. Browser reference at 412 CSS px: 24px gutters, 364px content width. Therefore narrowing the app to match the overlay would introduce an error.

At equal widths the WLO-0108 implementation is smaller and more compact than the reference, consistent with the user's explicit request for smaller/heavier typography. Preserve that typography rather than attempting to undo the screenshot scale by changing it again.

## Changes

Adjusted overview spacers to measured reference values: hero→date 8→5dp; date→summary 24→23dp; summary→divider 20→18dp; divider→selector 20→19dp. No typography, computation, navigation or content-width changes. Standard Material controls and accessible touch targets retained.

## Evidence

`comparison.html` / `comparison.png`: identical 30-day period and data, each screen displayed at 412px width. The reference arrays were replaced in browser memory using the same emulator dataset exported during WLO-0106; the actual mock source is unchanged. Crops start at the Weight trend text box to separate screen content from system/app-bar differences. Scaling is uniform on each image. Native Material metrics remain different from CSS; this is not claimed as pixel-identical output.

Source captures: `30-days.png`, `mock-30-days.png`; 200% text review: `large-text.png`. The enlarged summary remains readable and content scrolls. Font scale restored to 1.0. No device data cleared.

Measured reference boxes at 412px: hero y128.69/h66.08, date y199.77/h18.84, summary y241.61/h71.19, selector y331.80/h44. These are CSS boxes, not glyph bounds; Android accessibility text bounds must not be mistaken for glyph baselines. No baseline-perfect claim is made from these measurements.

Validation: `:app:assembleDebug ktlintCheck detekt` passed; `git diff --check` passed. Installed the updated debug APK. No unit tests added or rerun for four reversible spacing adjustments. Earlier test results apply to earlier revisions.
