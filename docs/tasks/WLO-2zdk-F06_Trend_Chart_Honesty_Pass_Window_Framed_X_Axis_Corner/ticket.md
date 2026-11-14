---
id: WLO-2zdk
was: WLO-0053

title: 'F06 trend chart honesty pass: window-framed x-axis, corner-label fix, window-aware empty state, trend-gate re-scope'

status: done
closed: 2026-09-15T23:58:17Z
status_changed: 2026-09-15T23:58:17Z

theme: weight-core

release: 1

created: 2026-09-14T17:06:57Z

blocks: [WLO-11439q, WLO-1hw7, WLO-x78q]

related: [WLO-07e3, WLO-11439q, WLO-5v58, WLO-d4a6, WLO-jn1r, WLO-zehq]

parent:

rank:
---

# Description

# Goal

The trend card must not lie: the same data must render differently per
window, empty windows must explain themselves, and the trend-line gate must
not punish lapsed/back-filled history. Found in the 2026-09-14 screenshot
review (evidence attached); tuner/preview mechanics split to WLO-5v58.

# References

- Work spec: this ticket's spec (scope A–E, tests, acceptance criteria)
- Code: feature/f06-weight/.../ui/WeightScreen.kt · core/designsystem/.../WloTrendChart.kt · feature/f06-weight/.../state/WeighInViewModel.kt
- Spec to amend: docs/features/F06-weight-body-metrics.md §4 gate sentence (pending q-000035)
- Related: WLO-5v58 (tuner), WLO-11439q (ribbon/compare-modes — fenced off)
