# Objective
End a successful weigh-in with a trustworthy, emotionally neutral result.

# Prerequisites
WLO-0051, WLO-0053, and WLO-0071.

# Scope
- Build confirmation only from the persisted event and recomputed canonical trend.
- Show trend as hero, raw reading as supporting data, and an honest sparse-history fallback.
- Show delta in the active unit; upward movement is neutral, never failure copy.
- Emit one soft haptic exactly once after a successful commit.
- Return cleanly to the Weight home/Hub per WLO-0068.

# Acceptance
Failed/cancelled saves yield no confirmation or haptic; recomposition cannot repeat the haptic; kg/lb and sparse-data cases are tested; TalkBack announces the result in a useful order.

## Implementation evidence (2026-09-16)

- Added a post-commit confirmation state built from the repository-returned persisted event and a fresh canonical `currentTrend` read after projection rebuild.
- Added a Material 3 confirmation card that is the sole hero while active: trend and neutral 7-day delta first, raw/source supporting data second, explicit sparse-history copy below three canonical samples, and a Done return to Weight.
- Added one-shot soft-tick consumption keyed by persisted event id; failed and cancelled saves never create confirmation state, recomposition cannot replay the haptic, and correcting a flagged event removes its now-invalid receipt.
- Added ordered live-region semantics plus kg/lb, sparse, success/failure/cancel, and haptic-consumption coverage.
- Verification passed: `:feature:f06-weight:testDebugUnitTest`, F06/app `ktlintCheck`, F06 `detekt` and `lintDebug`, `:app:compileDebugAndroidTestKotlin`, `checkArchitecture`, and `git diff --check`.
- API 29 focused acceptance passed for trend-first confirmation, pounds with canonical-kilogram storage, first-reading sparse fallback, rapid double-save, same-day re-weigh, smoothing controls after Done, and outlier correction.
