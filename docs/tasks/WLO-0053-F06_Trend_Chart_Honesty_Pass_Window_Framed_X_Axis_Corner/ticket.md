---
id: WLO-0053
title: 'F06 trend chart honesty pass: window-framed x-axis, corner-label fix, window-aware empty state, trend-gate re-scope'
status: todo
theme:
release:
created: 2026-09-14T17:06:57Z
modified: 2026-09-14T17:10:52Z
closed:
revision: ca8f542ea2a076a3
blocks: []
related: [WLO-0042, WLO-0054]
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
