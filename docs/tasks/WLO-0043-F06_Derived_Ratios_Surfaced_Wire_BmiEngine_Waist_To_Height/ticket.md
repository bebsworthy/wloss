---
id: WLO-0043
title: 'F06 derived ratios surfaced: wire BmiEngine + waist-to-height / waist-to-hip, provenance-badged, on request'
status: todo
theme:
release:
created: 2026-09-14T13:38:34Z
modified: 2026-09-14T13:52:56Z
closed:
revision: ec59ed66477bb271
blocks: []
related: []
---

# Description

# Goal

F06 §3's derived ratios exist only as an unconsumed engine: BmiEngine has zero feature consumers, and waist-to-height / waist-to-hip are unimplemented. Surface them honestly: computed, badged derived, evaluated against published ranges — shown on request only (§6: ranges not verdicts, BMI never a hero number).

# Work items

- Wire BmiEngine into a ratios read (height from profile, weight = lowest-of-day scalar).
- Add waist-to-height + waist-to-hip as engines in the same DerivedValue/provenance shape.
- Render: a "Ratios" block in the math/how-we-got-here surface; a small surface row once girth series exist (WLO-0035 W3 supplies the tape data).
- Range copy per openScale pattern — published healthy bands, no idealizing, no population shaming.
- Unit tests vs ConstantsRegistry.

# Gates

Unit tests green; ktlint + detekt; BMI renders only behind a tap.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) derived ratios (computed, never entered; published healthy ranges per openScale pattern) · §6 tone rules (BMI shown on request only, ranges not verdicts)
- Engine: [BmiEngine.kt](core/engines/src/commonMain/kotlin/app/wlo/core/engines/BmiEngine.kt) (exists, zero consumers — wire it, don't rewrite)
- Research: [openscale.md](docs/research/openscale.md) (derived-ratio evaluation pattern)
- Mockup: [flows/09-silhouette-f08.html](docs/design/flows/09-silhouette-f08.html) — waist/neck sites (girth inputs the waist ratios consume)
