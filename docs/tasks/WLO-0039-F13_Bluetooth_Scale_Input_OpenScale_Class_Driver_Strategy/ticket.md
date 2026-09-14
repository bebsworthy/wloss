---
id: WLO-0039
title: 'F13 Bluetooth scale input: openScale-class driver strategy spike + first-driver decision'
status: idea
theme:
release:
created: 2026-09-14T13:37:55Z
modified: 2026-09-14T14:02:46Z
closed:
revision: d5a6271238240eef
blocks: []
related: []
---

# Description

# Goal

Scope the Bluetooth scale input (F06 §3; research openscale.md's driver strategy) without committing to a 68-driver zoo prematurely. Deliverable: a decision record + a working spike, plus an honest milestone recommendation (Health Connect covers the ecosystem path for most scales meanwhile — WLO-0038).

# Work items

- ADR: port openScale drivers vs clean-room BLE GATT for 1–2 families; licensing/credit per openScale; per-device decoder pattern vs published-formula fallback.
- Pick the first target scale family (most common BLE weight profile in the wild).
- Debug-flag spike: scan → connect → read one measurement → appendWeighIn(source=BLUETOOTH) → outlier guard as usual.
- Recommendation: v1 vs v1.x placement, driver-expansion story.

# Gates

ADR merged; spike demo on real hardware (owner's scale); nothing mainline without the flag.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) inputs (Bluetooth scale telemetry via F13, openScale-class driver strategy)
- Research: [openscale.md](docs/research/openscale.md) (driver port strategy, per-device decoders, formula registry, licensing/credit)
- Companion: [F13-data-vault.md](docs/features/F13-data-vault.md); outlier guard on import = [F06 §4](docs/features/F06-weight-body-metrics.md)
