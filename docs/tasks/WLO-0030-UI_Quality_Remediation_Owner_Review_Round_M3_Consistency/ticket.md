---
id: WLO-0030
title: 'UI quality remediation — owner review round: M3 consistency, trend panel parity, number integrity'
status: doing
theme:
release:
created: 2026-09-13T18:55:33Z
modified: 2026-09-13T18:57:22Z
closed:
revision: d472a0ae022746e3
blocks: []
related: []
---

# Description

Owner review (2026-09-12, emulator screenshots vs day-loop mock frames) rejected the delivered UI quality. All findings verified in code.

DESIGN/SYSTEMIC
1. Not consistent with mockup; custom theme fights M3 instead of using standard Material 3 components.
2. Padding/spacing inconsistent page-to-page.
3. Low-quality custom chips, bad alignment.
4. Fake buttons: bordered Surface with plain left-aligned text instead of real buttons — "weigh in", "how the math works", "body fat methods" (WeightScreen.kt:356 PrimaryRow).
5. Duplicated text: card header "trend" then stat label "trend now" (WeightScreen.kt:120-141; also WloTrendChart.kt:88).
6. Card titles not properly capitalized ("weigh in", "trend now", "90 days") — mock uses uppercase letterspaced card headers.
7. Charts have no legible scale (axis numerals 10sp chrome inside canvas, no unit, no date range) — WloTrendChart.kt.
8. Internal rationale leaked into UI: "lower reads calmer, higher reads faster — the formulas live behind 'how the math works'" (WeightScreen.kt:274).

FUNCTIONAL
9. Trend number inconsistent: Hub shows 77.7 (day projection, HubViewModel.kt:208), Weight page 77.6 (repository recompute with user-tunable alpha, WeighInViewModel.kt:244). Two sources of truth.
10. Last raw 74.8 kg vs trend 77.7 kg — seeder's steep descending series makes EWMA lag ~3 kg; reads as nonsense.
11. No chip with an info mark opens anything: ProvenanceChip onClick defaults null and WloStat/WloStatRow never pass it; only the forecast card wires an explainer.
12. Hub "Diary · today" shows a month heatmap that was never in the mockup — OWNER OVERRULES heatmap on this card; mock's week dots instead.

WEIGHT TREND PANEL (priority; mock ann. 2/3)
13. Must show current weight WITH trend line + scale.
14. Font wrong (card header, hero numeral, unit symbol) — "kg" baked into the hero string (HubViewModel.kt:212 unit.format) so numeral+unit render as one run instead of display-hero + small unit.
15. Derived chip must sit top-right of the card, single icon, and NOT repeat the value (ProvenanceChip.kt:53-84 stacks kind glyph + word + value + info icon).
16. Info tap must open real "how we got here" content.

OWNER DECISIONS superseding earlier flags: no heatmap on Hub diary card (supersedes R-D4 application on the Hub — design agent to amend REVIEW.md/IA); provenance chip anatomy per mock (kind badge top-right, no repeated value).

SCOPE
Part A (:core:designsystem): ProvenanceChip anatomy + corner badge variant; real M3 button wrappers replace PrimaryRow; SelectChip on M3 FilterChip; typography audit vs DESIGN-SYSTEM.md §2 (hero 56-64sp w600+ Inter Display, unit de-emphasized, card-header micro-label uppercase letterspaced); WloTrendChart legible scale (min/max + unit, date range, text-secondary 11-12sp).
Part B (surfaces): Hub trend card mock parity (header + badge top-right, hero numeral + unit, delta chip, sparkline + last-30-days caption, tap-through); single shared trend source for Hub + F06 default view (alpha-tuner output labeled preview); weight page restructure (kill duplication, real buttons, delete leaked line); realistic seed series (trend within ~0.5 kg of recent raw); Hub diary card week dots replace heatmap; wire every info chip to explainer content; capitalization + page padding consistency sweep.

VERIFY: build gates, connected tests f06/f10, emulator screenshots Hub + Weight, visual judge vs mock frames.
