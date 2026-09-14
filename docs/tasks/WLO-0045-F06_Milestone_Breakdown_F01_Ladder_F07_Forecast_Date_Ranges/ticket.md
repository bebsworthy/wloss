---
id: WLO-0045
title: 'F06 milestone breakdown: F01 ladder + F07 forecast date ranges + milestone-reached moment'
status: todo
theme:
release:
created: 2026-09-14T13:38:34Z
modified: 2026-09-14T13:52:56Z
closed:
revision: 912eeafaf262e3d2
blocks: []
related: []
---

# Description

# Goal

F06 §5's milestone ladder rendered on the weight surface: goal split into 4–8 rungs, each with a forecast DATE RANGE from F07 (optimistic/expected/pessimistic — ranges, never one promise; §4 gating: F06 refuses to invent dates from thin data), plus the milestone-moment celebration.

# Work items

- Rung derivation against the live series (F01 Milestones.kt logic currently lives in onboarding — lift to a shared door).
- Per-rung date ranges from ForecastEngine bands (WLO-0034's forecast card work); cold-start/insufficient-data → ranges-only or honest empty state.
- Milestone moment (§4): full-bleed card, giant numeral count-up, distinct two-note celebration haptic when a weigh-in crosses a rung; persisted fired-markers so each rung celebrates exactly once.
- Emit clean milestone-reached events for F11 (badges/streak decisions stay F11's).
- Tests: rung math, crossing detection, once-only firing.

# Gates

Seeded demo shows the ladder; crossing demo fires the moment once; arch/ktlint/detekt green.

## References

- Spec: [F06 §4](docs/features/F06-weight-body-metrics.md) (milestone moment micro-interaction + gating: F06 refuses to invent dates from thin data) · §5 (milestone breakdown with per-milestone date RANGES from F07)
- Companions: [F07-energy-engine.md](docs/features/F07-energy-engine.md) (3-band forecast = the date ranges) · F01 milestone ladder (feature/f01-onboarding domain/Milestones.kt)
- Ruling: milestone "single dates will never exist" — ranges only ([flows/07-onboarding-f01.html](docs/design/flows/07-onboarding-f01.html) milestone-ladder frame)
- Mockup: [flows/04-weigh-in-trend-f06-f08.html](docs/design/flows/04-weigh-in-trend-f06-f08.html) — placement on the weight surface
