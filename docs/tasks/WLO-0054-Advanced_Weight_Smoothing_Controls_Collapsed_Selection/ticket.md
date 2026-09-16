---
id: WLO-0054
title: 'Advanced weight smoothing controls: collapsed selection, reset, and honest preview'
status: backlog
theme: weight-advanced
release: 3
created: 2026-09-14T17:06:57Z
modified: 2026-09-15T21:29:21Z
closed:
revision: 4e72a2900eb77bc3
blocks: [WLO-0078]
related: [WLO-0042, WLO-0053, WLO-0062, WLO-0087, WLO-0098]
---

# Description

# Goal

The smoother tuner is honest about the math but not about the state, and it
costs a repository round-trip per drag frame: preview values swap the hero
while the banner claims otherwise, α can practically never return to
exactly 0.15, and reloads can land out of order. Companion to WLO-0053
(chart rendering); compare-modes overlay stays in WLO-0042.

# References

- Work spec: this ticket's spec (scope A–D, tests, acceptance criteria)
- Code: feature/f06-weight/.../ui/WeightScreen.kt (SmootherTuner) · .../state/WeighInViewModel.kt (method/alpha events, reload)
- Ruling: FEATURES.md §3 R-A2 + WLO-0030 defect 9 — saved trend keeps the default; unchanged unless re-ruled (q-000037)
- Related: WLO-0053 (chart), WLO-0042 (analysis pack)
