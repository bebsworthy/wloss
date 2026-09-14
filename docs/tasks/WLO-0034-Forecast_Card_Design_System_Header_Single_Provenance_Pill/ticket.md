---
id: WLO-0034
title: 'Forecast card: design-system header + single provenance pill, informative arrival copy, shared-time-axis chart with month ticks and legend, measured-mode wiring'
status: doing
theme:
release:
created: 2026-09-14T11:09:16Z
modified: 2026-09-14T11:09:24Z
closed:
revision: aedc8f495bc89a4b
blocks: []
related: []
---

# Description

## Goal

Fix the Hub forecast card (`WloForecastCard`, consumed by f10 Hub + f01
onboarding preview) — owner-flagged acceptance failures. The card pre-dates
the atom enforcement (WLO-0031) and dropped most of the ratified flow-07
cone mock ("Forecast bloom", 07-onboarding-f01.html frame 3).

## Owner-flagged defects

1. Non-standard header — raw `wloType.title` Text instead of the design-system
   card header every other card uses.
2. Hand-built "ESTIMATED" pill (`EstimatedStamp`) top-right, visually unlike
   the provenance pill on the Weight trend / other cards.
3. Duplicated "estimated" pill at the bottom (mock pin 12: the hedge appears
   exactly once).
4. Dead-end copy "beyond the horizon" + subtext — conveys nothing.
5. Chart communicates nothing: no legend, no time axis, no scale; you cannot
   tell what the line/area are or the timeframe.

## Additional defects found in recon (fix in the same pass)

6. **Per-band time scales (geometry bug)** — `WloForecastChart.series()`
   normalizes EACH band to its own point count, so bands reaching the goal at
   different dates are stretched to the same width; the finish-date ticks use
   a different (horizon-based) scale again. One shared time axis is required.
7. **Horizon = 260 weeks** (`ConstantsRegistry.FORECAST_HORIZON_WEEKS`) — the
   un-reached-goal path integrates 5 years of steps, which is why no timeframe
   is guessable. The displayed window must be derived from the data (start ..
   last band finish, like the mock's Sep'26→May'27 for an 8-month plan), with
   a sane cap when the goal is not reached.
8. **Cold-start always** — HubViewModel only ever calls
   `ForecastEngine.coldStart`; `ForecastEngine.measured()` exists but is never
   called, so with the adaptive engine live the card can project a GAIN
   (formula TDEE < budget: demo = 1,772 est vs 2,000 intake) while the trend
   card above shows −0.5 kg / 7 d. This is the "counter-intuitive
   presentation" at root. Onboarding preview legitimately stays cold-start.

## Work items

A. **Header + single pill**: `WloCardHeader` micro-label (mock wording
   "Forecast to 74.0 kg" acceptable; match the Weight trend card's header
   pattern) with the SAME `ProvenanceChip` the trend card uses in its header
   slot (one pill, top-right). Delete `EstimatedStamp` and the bottom chip.
   Keep exactly one hedge line ("an estimate, not a promise — it sharpens as
   you log" may stay as the single hedge; chip word "estimated"/"derived"
   flips automatically with the provenance type).
B. **Arrival row must inform**: dates known → keep "on trend <date> · range
   <fast> – <slow>". Goal not reached in the horizon → say the fact and why in
   user words, e.g. that the planned intake is above the estimated burn, the
   trend gains ~N kg/week, and the window length (render 260 weeks as "5
   years"); zero-guilt, no dead end.
C. **Chart rework (shared time axis + mock parity)**:
   - One shared x-scale (start .. last relevant day, capped per work item 7);
     all bands and finish ticks on that scale.
   - Time axis: month tick labels along the bottom like the mock
     ("Sep '26 · Jan '27 · May '27", tabular 10sp chrome style).
   - Weight scale: keep the goal-line label + "today · N kg" annotation
     (mock has exactly these two); orientation consistent with the trend
     chart (heavier = up).
   - Legend row under the chart (mock CSS `.sch-legend` pattern: swatch +
     word, caption style): expected line, range band, goal hairline — user
     words only (R-D11).
   - Bands stay tappable → "how we got here" (mock pin 12).
D. **Measured-mode wiring**: in HubViewModel use `ForecastEngine.measured()`
   when the adaptive engine has a measured TDEE (see `TargetsRecord` /
   `TargetsRepository.measuredTdeeKcal`, TargetsRepository.kt:44; pace
   percentiles may be null — measured() already falls back to cold-start band
   factors), else coldStart. Chip word flips estimated→derived via provenance.
   Explainer rows gain the mode/inputs. F01 preview unchanged (cold-start).
E. **Tests**: JVM test for the pure window/scale helper if extracted; update
   androidTests referencing the card (f01 onboarding forecast preview, f10 Hub
   assertions); keep 58/0 baseline; new screenshot evidence of the Hub card
   (measured mode if the seed reaches it, else document) + onboarding preview.

## Gates

checkArchitecture enforce 0 violations · ktlint + detekt green ·
connectedDebugAndroidTest ≥ 58/0 · screenshots vs flow-07 frame 3 filed as
evidence.
