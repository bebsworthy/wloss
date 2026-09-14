---
id: WLO-0042
title: 'F06 analysis pack: 10-day-best headline, compare-modes overlay, progress ribbon, time-of-day lens + weigh-count stats'
status: todo
theme:
release:
created: 2026-09-14T13:38:34Z
modified: 2026-09-14T13:52:56Z
closed:
revision: fdeadb889ab8ccbb
blocks: []
related: []
---

# Description

# Goal

The chart/headline depth F06 §3–5 promises on the weight surface, beyond the basic dots+trend line that ships today.

# Work items

- User-selectable progress number (§5): trend (default) vs 10-day-best — settings-stored, feeds the F06 hero and the Hub.
- Compare-modes overlay (§3): all three smoothers on one chart — extend WloTrendChart for multi-series with a legend.
- Progress ribbon proper (§4): band above (green) / below (neutral) the trend line; thickness = change vs N-days-ago (default 30, scrubber); 200 ms thickness ease on new data.
- Time-of-day lens + weigh-count stats (R-B8 rendering): per-day weigh-count histogram over the trailing window + a "conditions drift" note placeholder when the series mixes morning/evening entries (full consistency lens is v1.x).
- Unit tests: 10-day-best selection, ribbon geometry, stats aggregation.

# Gates

Screenshot parity against the flow prototypes; unit tests green; arch enforce 0; ktlint + detekt.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) (smoothers + "compare modes" overlay) · §4 (progress ribbon micro-interaction, thickness ease) · §5 (charts 30d/90d/1y/all, 10-day-best, time-of-day lens)
- Ruling: [FEATURES.md §3](docs/features/FEATURES.md) R-B8 (time-of-day lens is the derived-view rendering rule)
- Design: [DESIGN-SYSTEM.md F06 inventory](docs/design/DESIGN-SYSTEM.md) (progress ribbon, compare-modes overlay, weigh-count stats)
- Mockup: [flows/04-weigh-in-trend-f06-f08.html](docs/design/flows/04-weigh-in-trend-f06-f08.html) — ribbon beneath the trend + 10-day-best frames
