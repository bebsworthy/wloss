---
id: WLO-0044
title: 'F06 custom metrics UI: define / enter / chart / export user-defined EAV metrics'
status: todo
theme:
release:
created: 2026-09-14T13:38:34Z
modified: 2026-09-14T13:52:56Z
closed:
revision: 032f7fa9d42b0595
blocks: []
related: []
---

# Description

# Goal

The EAV promise (F06 §3): arbitrary user-defined metrics (ketones, waking pulse, morning coffee…) each with entry, chart, trend stats, and a CSV column. The store already supports it (CUSTOM kind + metric= EAV attr + per-metric unit override) — this ticket is everything above the store.

# Work items

- Metric registry: list / create / archive metric types (name + unit), persisted as a versioned profile document.
- Entry sheet mirroring the weigh-in pattern (back-datable once WLO-0035 W2 lands); unit comes from the registry.
- Per-metric card: chart + last value + trend stats, reachable from the weight surface's series switcher (WLO-0035 W3).
- Export mapping: metric columns in the CSV/JSON writers + reimport roundtrip (F13).
- Delete per the R-B8 amendment applies to custom events too.

# Gates

Roundtrip test define → enter → chart → export → restore; arch enforce 0; ktlint + detekt.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) EAV metric store (openScale pattern — one engine, any metric, CSV column each)
- Design: [DESIGN-SYSTEM.md F06 inventory](docs/design/DESIGN-SYSTEM.md) ("EAV custom metrics")
- Store (already supports it): [Measurement.kt](core/model/src/commonMain/kotlin/app/wlo/core/model/Measurement.kt) CUSTOM kind + metric= attr + unit override (core/data/Spine.kt NewMeasurement.unitOverride)
- Mockup: [flows/04-weigh-in-trend-f06-f08.html](docs/design/flows/04-weigh-in-trend-f06-f08.html) — entry-sheet pattern to mirror
