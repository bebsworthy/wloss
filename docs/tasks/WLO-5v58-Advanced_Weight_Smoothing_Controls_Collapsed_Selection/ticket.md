---
id: WLO-5v58
was: WLO-0054

title: 'Advanced weight smoothing controls: collapsed selection, reset, and honest preview'

status: backlog
closed:
status_changed: 2026-09-16T07:55:13Z

theme: weight-advanced

release: 3

created: 2026-09-14T17:06:57Z

blocks: [WLO-dtsr]

related: [WLO-11439q, WLO-2zdk, WLO-4de0, WLO-5d13, WLO-xydc]

parent:

rank: 5242880
---

# Description

# Goal

The smoother tuner is honest about the math but not about the state, and it
costs a repository round-trip per drag frame: preview values swap the hero
while the banner claims otherwise, α can practically never return to
exactly 0.15, and reloads can land out of order. Companion to WLO-2zdk
(chart rendering); compare-modes overlay stays in WLO-11439q.

# References

- Work spec: this ticket's spec (scope A–D, tests, acceptance criteria)
- Code: feature/f06-weight/.../ui/WeightScreen.kt (SmootherTuner) · .../state/WeighInViewModel.kt (method/alpha events, reload)
- Ruling: FEATURES.md §3 R-A2 + WLO-dj08 defect 9 — saved trend keeps the default; unchanged unless re-ruled (q-000037)
- Related: WLO-2zdk (chart), WLO-11439q (analysis pack)
