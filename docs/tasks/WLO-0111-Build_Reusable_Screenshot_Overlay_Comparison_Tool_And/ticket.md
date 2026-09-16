---
id: WLO-0111
title: Build reusable screenshot overlay comparison tool and review weight layout
status: done
theme:
release:
created: 2026-09-16T18:09:07Z
modified: 2026-09-16T18:14:22Z
closed: 2026-09-16T18:14:22Z
revision: 939707b59b914827
blocks: []
related: []
---

# Description

# WLO-0111 — Review of the corrected-size overlay

White/yellow = mockup; red = Android. This is a visual review of the owner's supplied overlay, not a pixel-perfect automated scoring result.

## Findings

1. **Content widths now match closely.** Summary dividers and selector outer edges align horizontally. The earlier alleged broad width defect should not drive app changes.
2. **Typography was reduced too far in the app.** Hero tops are close but the app numerals are shorter; captions, dates, goal text and range labels are visibly smaller. The app's date appears above the reference. Restore hierarchy from measured reference roles, rather than another blanket reduction. Different 77.0/77.1 glyphs are not themselves a geometry defect.
3. **Summary internal spacing is too tight.** The change figure and supporting caption sit above the reference, with the caption displaced more. Goal supporting text is also higher. This is a combination of smaller line boxes and missing internal space. Merely translating the whole summary cannot match both lines.
4. **Selector is higher and shorter.** Its width and segment divisions are close. Its vertical position follows the shorter summary; its visible height differs from the CSS reference. Preserve native touch targets while reviewing visible component metrics.
5. **Chart geometry is the largest discrepancy.** Red 79, 77 and 75 gridlines progressively diverge upward from the same reference labels; the red 74 goal line is substantially higher. The app has a shorter vertical plot span, not just a displaced chart. Its right axis gutter is also narrower, extending the plot and labels farther right. Reconcile plot height, axis domain/padding, top/bottom label space and gutter together. The current app uses a 220dp chart whereas the HTML SVG has a 360×285 viewBox; they are not geometrically equivalent at equal content width.
6. **Date ticks and coverage copy differ.** App uses 19 Jun / 19 Jul / 17 Aug / 16 Sep; reference uses calendar-month ticks and explains that readings begin on 3 Aug. These are semantic differences, not tracking errors. Sparse-window coverage information is useful and should remain understandable.
7. **History rises with the shorter chart.** The apparent overlay collision with mockup dates does not mean the Android screen itself overlaps. It shows the cumulative height difference through the chart.
8. **Header is independently misaligned.** App title/settings sit lower relative to the aligned hero. Review top-bar metrics separately from content spacing; do not shift the entire screen to fix one region.

The datasets differ (77.0 vs 77.1; −1.3 vs −1.2; 3.0 vs 3.1). Line position/shape and these numeric differences cannot establish a calculation defect. Future geometry comparisons should use matching data and state. The tool example uses matching data copied into the mockup only in browser memory.

## Recommended order

Keep the now-matching content width. First reconcile chart dimensions and gutters. Then align summary text line boxes and group spacing. Finally adjust visible selector height and header metrics. Retain the user's full date, no-checkmark control preference, individual measurements and goal line. No Android changes were made in WLO-0111.

## Tool validation

Delivered `tools/screenshot-compare/index.html`, `compare.js`, README and five dependency-free Node tests. All five tests pass. Chromium checks passed for arbitrary image loading, uniform width matching, anchor selection, PNG export, side-by-side/difference canvas sizes, settings save/load, and light-background mode with no JS errors. Source reference screenshot is 470×1046 due element-screenshot raster rounding; output correctly preserves that source height. `overlay.png` is the unshifted equal-width output used to test export, not a claimed exact hero alignment. `tool.png` shows the UI. Images never leave the local browser.
