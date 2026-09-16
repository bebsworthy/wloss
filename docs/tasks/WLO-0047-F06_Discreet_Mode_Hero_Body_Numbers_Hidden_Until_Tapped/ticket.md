---
id: WLO-0047
title: 'F06 discreet mode: hero body numbers hidden until tapped'
status: canceled
theme: weight-experience
release: 2
created: 2026-09-14T13:38:55Z
modified: 2026-09-16T05:29:17Z
closed: 2026-09-16T05:29:17Z
revision: 4d975d4355f45e78
blocks: []
related: []
---

# Description

# Goal

F06 §9 privacy: weight history is the most sensitive data in the app — discreet mode hides hero numbers (trend, raw reading, body-fat) until deliberately revealed.

# Work items

- Settings toggle (app-wide in v1 single-profile; per-profile arrives with multi-profile v1.x).
- WloHeroStat obscured variant: masked glyphs/blur + tap-to-reveal, auto re-hide after a few seconds.
- Applied on Hub + all F06 surfaces; the widget (WLO-0041) must respect it.
- Orthogonal to the app lock (both can be on; lock gates entry, discreet gates shoulder-surfing).
- Screenshot tests for both states.

# Gates

Screenshots both states on Hub + weight surface; arch/ktlint/detekt green.

## References

- Spec: [F06 §9](docs/features/F06-weight-body-metrics.md) (discreet mode: hero number hidden until tapped; biometric lock context)
- Design: [DESIGN-SYSTEM.md F06 inventory](docs/design/DESIGN-SYSTEM.md) ("Discreet mode (hero hidden until tapped)")
- Surfaces: Hub hero (f10) + F06 hero (feature/f06-weight/ui/WeightScreen.kt) + widget when built (WLO-0041)

## Product decision (2026-09-16)

Canceled by owner: concealing weight hero numbers behind an extra reveal interaction is not useful for this product. Do not implement or carry this behavior into the widget. Existing app-lock and sensitive photo/archive protections remain in scope.
