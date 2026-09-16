---
id: WLO-0053
title: 'F06 trend chart honesty pass: window-framed x-axis, corner-label fix, window-aware empty state, trend-gate re-scope'
status: done
theme: weight-core
release: 1
created: 2026-09-14T17:06:57Z
modified: 2026-09-16T00:00:01Z
closed: 2026-09-15T23:58:17Z
revision: e3070eb6ffdf05c1
blocks: [WLO-0042, WLO-0070, WLO-0076]
related: [WLO-0042, WLO-0052, WLO-0054, WLO-0071, WLO-0079, WLO-0084]
---

# Description

# Goal

The trend card must not lie: the same data must render differently per
window, empty windows must explain themselves, and the trend-line gate must
not punish lapsed/back-filled history. Found in the 2026-09-14 screenshot
review (evidence attached); tuner/preview mechanics split to WLO-0054.

# References

- Work spec: this ticket's spec (scope A–E, tests, acceptance criteria)
- Code: feature/f06-weight/.../ui/WeightScreen.kt · core/designsystem/.../WloTrendChart.kt · feature/f06-weight/.../state/WeighInViewModel.kt
- Spec to amend: docs/features/F06-weight-body-metrics.md §4 gate sentence (pending q-000035)
- Related: WLO-0054 (tuner), WLO-0042 (ribbon/compare-modes — fenced off)
