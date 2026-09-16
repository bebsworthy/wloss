# Objective
Validate the daily scalar and smoothing defaults instead of inheriting competitor behavior as truth.

# Scope
- Build a versioned deterministic corpus covering multiple same-day attempts, low outliers, missing days, mixed times, backfills, travel/timezones, edits, and deletions.
- Compare minimum, first reading, median, and consistent-time-window daily scalars.
- Measure bias, stability, lag, revision size, correction sensitivity, and downstream forecast impact.
- Compare the currently supported smoother defaults on the same corpus and publish known failure modes.
- Recommend a default, explainer language, and migration implications; do not silently change R-B8.

# Acceptance
A reproducible report and executable benchmark tests exist; the recommendation is evidence-backed; raw events remain untouched; any governing-ruling change is explicit and separately reviewed.


## Implementation evidence (2026-09-16)
- Added versioned deterministic fixture `weight-policy-benchmark-v1` with 54 immutable rows across noisy-decline/mutation and known-step scenarios. It covers same-day attempts, low outlier, missing day, mixed time, backfill, travel timezone, edit, and delete snapshots.
- Added executable `WeightPolicyBenchmarkTest`: compares minimum, first, median, and fixed consistent-window candidates plus all three supported smoothers; freezes bias, MAE/RMSE, stability RMS, lag, revision mean/max, correction sensitivity, backfill coverage, and 30-day slope sensitivity.
- Published `docs/research/weight-policy-benchmark-v1.md` with method definitions, exact results, failure modes, explainer copy, migration implications, and explicit no-change treatment for R-B8/R-B5/R-A2.
- Recommendation: consistent-time-window scalar (fixed/versioned profile anchor; median fallback); retain causal EWMA α=0.15 for Release 1 pending WLO-0073 calibration.
- Verification: `:core:engines:jvmTest` and `:core:engines:detektJvmTest` pass; `git diff --check` passes for the scoped paths.


## Formatting follow-up (2026-09-16)
- Applied ktlint-only formatting to `WeightPolicyBenchmarkTest.kt`; benchmark semantics and frozen output are unchanged.
- Verification: `:core:engines:ktlintCheck` and full `:core:engines:jvmTest` pass together.
