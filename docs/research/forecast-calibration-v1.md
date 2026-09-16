# Weight forecast calibration v1

Status: Release 1 engineering evidence for WLO-0073. This validates the
existing loss forecast and its eligibility boundary; it does not validate
weight-gain forecasting, which remains explicitly withheld for WLO-0083.

## Release decision

The transparent three-band loss forecast is release-eligible only through the
quality-state boundary below. Point error and interval coverage pass, but the
cold-start-to-measured point-date revision fails the pre-run stability gate.
Consequently, `Developing` may expose a neutral provisional outer range but is
structurally ineligible to expose the central/on-trend date; only measured
`Available` output may expose that point date.

| Result | Minimum evidence | What UI may show |
| --- | --- | --- |
| `Withheld` | WLO-0080 safety eligibility does not allow goal math, including gain mode | No target-derived bands or dates |
| `Developing` | Fewer than 10 usable paired days in 21, or no measured-TDEE solve | Neutral provisional outer range only; central/on-trend date suppressed; no measured claim |
| `Held` | Three trailing unmarked intake days, weigh-in gap over four days, atypical-week flag, or failed outlier screen | No newly calculated dates; an optional last-good forecast is visibly frozen with its reason |
| `Available` | WLO-0080 eligible, `EngineState.Updating` with at least 10 usable paired days, and a measured-TDEE solve | Measured three-band forecast with range-first copy |

`ForecastEngine.evaluate` owns this mapping. F10 now consumes that result
instead of falling back from held measured data to a fresh cold-start date.
Safety eligibility is evaluated first, so good logging can never override a
pregnancy, breastfeeding, eating-disorder concern, medically influenced
weight, energy-floor, pace, age, direction, or input-validity hold.

## Reproduce

The versioned fixture is
`core/engines/src/commonTest/resources/golden/forecast_calibration_v1.json`.
It contains four synthetic profiles: steady loss, higher-deficit loss,
shallow loss, and measured plateau. The independent truth simulator updates
weight daily from each fixture's actual starting TDEE, weight coefficient,
and explicit adaptation term. It does not call the forecast integrator.

Run:

```sh
./gradlew :core:engines:jvmTest \
  --tests 'app.wlo.core.engines.ForecastCalibrationBenchmarkTest' \
  --console=plain
```

Each profile is evaluated at day 0 in cold-start/developing mode and again at
day 14 in measured/available mode. Holdouts are scored 28 and 56 days after
each cutoff. The executable snapshot also tests missing-data holds, outlier
sensitivity, a 9→10 usable-day backfill transition, and gain withholding.

## Inputs and metrics

Forecast inputs are sex (optional), age, height, current trend weight, loss
goal, planned intake, activity class for cold start, measured TDEE for
available mode, optional fast/slow 28-day pace estimates, start day, and the
WLO-0080 eligibility result. Constants remain the published 7,700 kcal/kg,
Mifflin–St Jeor BMR, seven-day integration step, and current band factors.

- **Point error:** predicted expected-band weight minus simulated truth.
- **MAE and bias:** aggregate absolute and signed point error at each horizon.
- **Interval coverage:** fraction of holdouts between optimistic and
  pessimistic trajectory weights, inclusive.
- **Revision stability:** absolute shift in the expected finish day from the
  initial cold-start estimate to the day-14 measured estimate, where both
  produce a finish date.
- **Missing/outlier sensitivity:** a triggered quality hold must return
  `Held`, even when a numerical measured input is supplied.

## Release gates and measured result

These are engineering quality gates for this deterministic regression corpus,
not population accuracy claims.

| Gate | Threshold | Result |
| --- | ---: | ---: |
| 28-day MAE | ≤ 0.75 kg | **0.4733 kg** |
| 28-day absolute bias | ≤ 0.50 kg | **0.4150 kg** |
| 28-day band coverage | ≥ 80% | **87.5% (7/8)** |
| 56-day MAE | ≤ 1.50 kg | **0.9154 kg** |
| 56-day absolute bias | ≤ 1.00 kg | **0.7835 kg** |
| 56-day band coverage | ≥ 80% | **87.5% (7/8)** |
| Maximum initial→measured finish-date revision | ≤ 28 days | **39 days — FAIL** |

Mean finish-date revision is 22.5 days. The 39-day maximum exceeds the
pre-run four-week gate; the threshold and fixture stay unchanged. This failed
diagnostic makes cold-start point dates release-ineligible. It does not prevent
the product from showing the two outer bounds as a neutral provisional range.
Early copy must not imply a stable central date.

The two comparable cases revise by 6 days (female steady loss) and 39 days
(male higher deficit). In the failing case the cold-start activity formula
estimates roughly 2,654 kcal/day while the synthetic physiology starts at
2,810 kcal/day; once measured evidence arrives, the larger deficit moves the
finish substantially earlier. This is cold-start formula error, not numerical
instability in repeated integration. The implemented product treatment is to
withhold the central “on trend” date during `Developing` and show only its
provisional outer range. `GoalForecastResult.Developing.pointDateEligible` is
always false; F10 erases the central date before creating its UI model, and the
design-system component omits both central-date copy and its chart tick. The
numeric failure remains recorded and a future cold-start model must earn point
eligibility against the unchanged gate rather than relabel this result.

## Known failures and honest copy

Both uncovered holdouts are the shallow-loss profile's day-0 cold-start
forecast, at 28 and 56 days. Its formula TDEE is below planned intake while
the synthetic physiology has a small deficit. The cold-start band therefore
holds weight flat and misses a slow loss that the measured solve detects at
day 14. This is precisely why `Developing` says “formula estimate — will
sharpen as you log” and must not be styled as a measured forecast.

Other limitations:

- Four synthetic profiles and 16 holdouts are a regression corpus, not a
  clinical or population validation. Coverage changes in 6.25-point steps.
- Truth uses an independent but still simplified energy-balance simulator;
  water, adherence changes, composition, medication, illness, and real-world
  intake error are not modeled.
- Positive bias at both horizons means the expected path is conservative in
  this corpus (predicts a higher weight than truth); that is measured, not
  presented as a universal property.
- Finish-date stability is assessed only where both snapshots reach the goal
  inside the model horizon. A missing finish is not coerced into a date.
- Bands are heuristic pace crossings, not probability intervals. “87.5%
  coverage” describes this fixture only; UI must say range, never 80% or 95%
  confidence.

Required range copy:

> Estimated range, not a promise. Early dates can move by several weeks as
> your measured pattern forms.

## Plateau wording

Flat trend alone means estimated expenditure is approximately matching
average intake over a quality-passing window. It does not prove burn rose.
`ForecastEngine.interpretPlateau` compares two explicit TDEE intervals:

- if either window is not `Updating`, the result is insufficient evidence;
- overlapping intervals produce `ApproximateBalance`, with no direction;
- only non-overlapping intervals may produce a supported increase or decrease.

Approved neutral copy for the common overlapping case:

> Your trend is roughly flat, so estimated burn and logged intake are close
> over this window. The change from the prior estimate is still inside the
> uncertainty range.

This replaces any unconditional “plateau means TDEE rose” claim.

## Migration and next evidence

No stored raw event or WLO-0072 daily-scalar row changes. Existing consumers
should migrate from nullable/screen-local fallbacks to `ForecastEngine.evaluate`.
Changing gates, horizons, band factors, simulator cases, or eligibility rules
requires a new frozen snapshot and report revision. Before claiming real-world
accuracy, add separately consented dogfood fixtures with de-identified holdouts
and preserve this synthetic corpus as the mechanical regression suite.
