# Weight daily-scalar and smoothing benchmark v1

Status: reproducible product-engineering evidence for WLO-0072. This report
recommends a release policy; it does **not** change a governing ruling or the
production repository implementation.

## Decision

Use a **consistent-time-window daily scalar** as the Release 1 candidate:
choose the active reading closest to the profile's fixed local-time anchor
inside a four-hour window, and use the day's median when the window is empty.
For the v1 corpus the anchor is 07:00 and the half-open window is 05:30–09:30.
The anchor must be fixed for a policy version; silently moving it as new data
arrives would rewrite history.

Keep trailing **EWMA α = 0.15** as the release smoother pending forecast
calibration (WLO-0073). It is causal, had the lowest noise metric and the
smallest mean historical revision in this corpus. Its cost is material lag:
nine days to get within 0.2 kg of a 1 kg step. Zero-phase EWMA and MA7 remain
documented advanced comparisons, not headline defaults. This is a deliberate
retention of R-A2, not evidence that α = 0.15 is universally optimal.

Proposed explainer copy:

> Your daily weight uses the reading closest to your usual weigh time. If none
> is in that window, it uses the middle reading for the day. Every original
> reading stays in your logbook and export. Your weight trend is a past-only
> weighted average, so it moves more calmly and follows a real change over the
> next several days.

## Corpus and reproducibility

The versioned fixture is
`core/engines/src/commonTest/resources/golden/weight_policy_benchmark_v1.json`.
It has 54 immutable event rows across two synthetic scenarios and explicit
standardized-morning truth values. It covers:

- three same-day attempts, including a post-bathroom low and an evening high;
- a low outlier, a missing day, and an evening-only day;
- a late backfill and explicit effective local day;
- travel from UTC+01:00 to UTC+10:00;
- a superseded typo and a user-deleted bad reading; and
- a stable series followed by a known 1 kg step for lag measurement.

The `validFromRevision` / `validUntilRevisionExclusive` fields describe
benchmark snapshots. They do not model a production tombstone. All 54 rows
remain in the fixture so before/after behavior can be reproduced; the test
asserts that policy evaluation does not mutate them. Production remains bound
by R-B8: raw events are event-level, and an explicit user delete removes that
event.

Run:

```sh
./gradlew :core:engines:jvmTest \
  --tests 'app.wlo.core.engines.WeightPolicyBenchmarkTest' \
  --console=plain
```

`WeightPolicyBenchmarkTest` freezes the entire output as a snapshot. A corpus,
metric, policy, or smoother change therefore requires an explicit test and
report update.

## Metrics

- **Bias:** mean selected-or-smoothed value minus synthetic truth.
- **MAE / RMSE:** absolute and root-mean-square error against truth.
- **Stability RMS:** RMS of consecutive error changes. Lower means less
  day-to-day noise after the known truth movement is removed.
- **Revision mean / max:** absolute change at days present in both the initial
  and final snapshots. A newly backfilled day is counted as coverage gain, not
  as a numeric revision from zero.
- **Edit/delete sensitivity:** selected scalar movement on the two explicit
  correction cases.
- **Lag:** days after the known step until the smoothed value is within 0.2 kg
  of the new level.
- **30-day forecast impact:** absolute endpoint error when an ordinary
  least-squares slope over the last 14 available values is projected 30 days.
  This is a sensitivity probe, not WLO's release forecast model.

## Daily-scalar results

All values are kg; lower is better except bias, whose ideal is zero.

| Policy | Bias | MAE | RMSE | Stability RMS | Revision mean | Forecast impact |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Minimum | -0.0145 | 0.1010 | 0.2579 | 0.4695 | 0.2902 | 0.6273 |
| First reading | -0.0002 | 0.0916 | 0.2448 | 0.4238 | 0.2902 | 0.6273 |
| Median | +0.0149 | 0.0765 | 0.1671 | 0.2896 | 0.2484 | 0.3102 |
| Consistent window | +0.0300 | **0.0635** | **0.1352** | **0.1864** | **0.2067** | **0.0632** |

The 91.0 → 81.08 kg typo correction moves every policy by 9.92 kg because
there is only one active row in either snapshot. On the explicit 77.0 kg
deletion day, minimum and first revise by 4.01 kg, median by 2.005 kg, and the
consistent-window result does not revise because it already selected the
07:07 reading closest to the anchor. Every policy gains the late backfill as
one new covered day.

The result rejects minimum as the default for this corpus. Minimum grants a
systematic advantage to opportunistic lows and is maximally exposed to a low
outlier. First reading is order-sensitive. Median is the strongest simple,
schedule-free fallback. The consistent window wins accuracy, stability, and
forecast sensitivity here, while preserving every same-day event.

## Smoother results

Each smoother consumes the final consistent-window scalar series.

| Smoother | Bias | MAE | Stability RMS | Step lag | Revision mean | Revision max | Forecast impact |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| EWMA α=0.15 | +0.2909 | 0.2909 | **0.1351** | 9 d | **0.1745** | 1.5400 | **0.9319** |
| Zero-phase EWMA | +0.0681 | 0.1891 | 0.1391 | 6 d | 0.1895 | **0.8704** | 0.9796 |
| MA7 | +0.1866 | **0.1874** | 0.1434 | **5 d** | 0.2151 | 1.5571 | 1.1357 |

EWMA's higher bias/MAE is mostly lag on the deliberately abrupt step, while
its stability and causal revision behavior are best. Zero-phase reduces lag
and peak revision size, but new future samples revise prior values by design.
MA7 reaches the step threshold fastest in this finite example but has the
worst forecast sensitivity and mean revision.

## Known failure modes and limits

- **Synthetic truth:** a person's physiological “true daily weight” is not
  observable. The standardized truth here is a controlled test oracle, not a
  clinical validation.
- **Schedule dependence:** a 07:00 anchor disadvantages night-shift workers or
  anyone with a different routine. Release code needs a user-visible fixed
  anchor or an explicitly versioned initialization rule; it must never infer a
  moving anchor behind the user's back.
- **Window fallback:** an evening-only day falls back to median and may still
  be hydration-biased. The explainer must say this rather than imply a morning
  measurement existed.
- **Missing days:** the smoothers operate on available samples, so “7-day MA”
  currently means seven observations, not seven calendar days, when gaps
  exist. UI and formula documentation must use the precise term.
- **EWMA initialization:** seeding from the first value makes early output
  sensitive to that value. Long gaps do not currently decay or reset α.
- **Zero phase:** the current forward/backward implementation is endpoint
  sensitive and revises history. It is unsuitable for a value described as
  final or for notification triggers.
- **Forecast proxy:** the OLS projection intentionally amplifies slope
  differences. WLO-0073 must benchmark the real three-band forecast and set
  release gates before dates ship.
- **Small corpus:** two scenarios expose mechanics, not population-level
  performance. Add opt-in anonymized or manually curated fixtures before a
  future policy version, without replacing this regression corpus.

## Versioning and migration implications

The daily scalar needs a formula/version identity independent of the smoother.
A production switch from lowest-of-day must be a new derived-policy version,
followed by deterministic recomputation of scalar, trend, residual σ,
coverage, and forecasts from untouched events. Existing exported raw rows do
not migrate. Cached derived rows must not be relabeled in place.

Before activation, separately review the governing F06/F07 contract, define
how the profile anchor is established and edited, decide whether existing
users opt in or migrate at a release boundary, and show a one-time “trend
recomputed with the new daily-weight method” note. This report makes no silent
change to R-B8, R-B5, R-A2, or production behavior.
