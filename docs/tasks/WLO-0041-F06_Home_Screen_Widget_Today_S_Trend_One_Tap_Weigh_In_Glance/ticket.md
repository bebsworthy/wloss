---
id: WLO-0041
title: 'F06 home-screen widget: today''s trend + one-tap weigh-in (Glance)'
status: backlog
theme: weight-experience
release: 2
created: 2026-09-14T13:37:55Z
modified: 2026-09-16T05:29:16Z
closed:
revision: 60bce0db9203b5b6
blocks: []
related: []
---

# Description

# Goal

The "today's trend" home-screen widget (F06 §4 entry points; IA.md widget surfaces): trend hero + weekly delta (+ last raw reading where size allows); tap → wlo://weight/log.

# Work items

- Glance appwidget in :app; small/flexible sizes (single-cell = trend number + delta only, honest at a glance).
- Updates on data change (observe the canonical currentTrend flow → AppWidgetManager) + day rollover; coalesce to avoid update storms.
- Empty states: no weigh-ins → kind "weigh in" prompt; pre-onboarding → widget inert.
- Screenshot proofs with the demo seed (WLO-0036 seeder).

# Gates

assembleDebug + widget live on the emulator launcher showing seeded data.

## References

- Spec: [F06 §4](docs/features/F06-weight-body-metrics.md) entry points ("home-screen widget: today's trend")
- Design: [IA.md](docs/design/IA.md) widget zones + R-U12 (one widget framework) + deep-link registry (zone-scoped targets)
- Data: canonical current trend = WeighInRepository.currentTrend (core/data/WeighInRepository.kt — the single shared answer)
