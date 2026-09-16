# Objective
Prove forecast accuracy and uncertainty behavior before ranges drive goal UI.

# Prerequisites
WLO-0068, WLO-0072, and WLO-0080.

# Scope
- Create versioned synthetic/dogfood fixtures and holdout evaluation at defined horizons.
- Measure point error, bias, interval coverage, revision stability, missing-data behavior, and outlier sensitivity.
- Define minimum evidence and held/developing/available states consumed by UI.
- Correct the plateau claim so copy follows measured estimate changes and uncertainty.
- Publish algorithm inputs, thresholds, known failure cases, and reproducible results.

# Acceptance
Forecast-band empirical coverage and horizon error are measured against explicit thresholds; cold start, gaps, plateaus, backfills and revisions are represented; UI eligibility derives from one tested engine contract rather than screen heuristics.


## Implementation evidence (2026-09-16)
- Added `ForecastEngine.evaluate`, the single UI-facing mapping from WLO-0080 eligibility + F07 `EngineState`: safety `Withheld`; `<10` usable/no solve `Developing` with cold-start estimate; quality trigger `Held` with no newly calculated date; updating + solve `Available` measured bands. F10 Hub now consumes this contract and no longer falls back from a held measured state to a new cold-start date.
- Added uncertainty-aware plateau interpretation: a flat trend means approximate intake/burn balance; direction is supported only when two quality-passing TDEE intervals do not overlap.
- Added versioned `forecast-calibration-v1` corpus (4 independent synthetic physiology profiles, cold-start and day-14 measured cutoffs, 28/56-day holdouts) and executable snapshot/gates. Also tests cold start, three-day intake gap, >4-day weigh gap via the shared quality state, outlier hold despite a supplied numerical estimate, 9→10-day backfill transition, safety precedence, and explicit WLO-0083 gain withholding.
- Exact results: 28d MAE 0.4733 kg, bias +0.4150 kg, coverage 87.5% (7/8); 56d MAE 0.9154 kg, bias +0.7835 kg, coverage 87.5% (7/8); initial→measured finish-date revision mean 22.5 days, max 39 days.
- Release gates: 28d MAE ≤0.75 kg / |bias| ≤0.50 kg; 56d MAE ≤1.50 kg / |bias| ≤1.00 kg; both coverages ≥80%; max finish revision ≤28 days. The two misses are the shallow-loss day-0 cold-start case at both horizons; the report requires provisional range copy and does not represent coverage as a probability guarantee.
- Published `docs/research/forecast-calibration-v1.md`; corrected the governing plateau story in `FEATURES.md`; linked F07 to the evidence.
- Verification passed: `:core:model:jvmTest`, full `:core:engines:jvmTest`, `:core:engines:ktlintCheck`, `:core:engines:detektJvmTest`, `:feature:f10-daily-hub:testDebugUnitTest`, `:feature:f10-daily-hub:ktlintCheck`, `:feature:f10-daily-hub:detekt`, and scoped `git diff --check`.


## Superseding calibration status (2026-09-16)
The initial pre-run finish-date revision gate was ≤28 days. The corpus measured a 39-day maximum (22.5-day mean), so that gate FAILS. A brief attempted relaxation to 42 days was rejected as retrospective threshold tuning. The executable gate is restored to 28 days and the report marks the failure. At that checkpoint the ticket remained DOING pending a product-level eligibility mitigation. Accuracy/bias/coverage gates still pass; no corpus values or model constants were tuned.


## Final release-boundary evidence (2026-09-16)
The calibration diagnostic remains an explicit failure: maximum cold-start→measured point-date revision is 39 days against the unchanged pre-run ≤28-day gate. No model constant, corpus value, or threshold was changed. This failure now defines the product boundary rather than being relabeled: `GoalForecastResult.Developing.pointDateEligible=false`; F10 preserves the broad three-band trajectories but erases the central finish date; and the design-system card renders neutral provisional outer-range copy without a central/on-trend date or central chart tick. Only measured `Available` forecasts may expose the point date. Engine, Hub, and design-system tests cover this contract.

Acceptance evidence is complete: the versioned four-profile corpus measures 28/56-day point error, signed bias, empirical band coverage, and revision stability; deterministic cases cover cold start, quality gaps/holds, outliers, 9→10-day backfill, plateau uncertainty, safety precedence, and explicit WLO-0083 gain withholding. Exact measured results and known misses remain published in `docs/research/forecast-calibration-v1.md`.

Final verification passed: `:core:model:jvmTest`; full `:core:engines:jvmTest`; `:core:designsystem:testDebugUnitTest`; `:feature:f10-daily-hub:testDebugUnitTest`; engine/design-system/F10 `ktlintCheck`; `:core:engines:detektJvmTest`; design-system/F10 `detekt`; and `git diff --check`.
