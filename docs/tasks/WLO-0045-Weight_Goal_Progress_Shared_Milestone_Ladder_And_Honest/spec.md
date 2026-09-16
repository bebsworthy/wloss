# Objective
Show useful, non-celebratory progress from the canonical trend to the active goal.

# Prerequisites
WLO-0073 forecast calibration/safety and WLO-0074 goal-editor parity.

# Scope
- Move the pure milestone/rung derivation to the smallest shared domain location used by onboarding and Weight.
- Render current trend, remaining amount, 4–8 rungs, and range-based dates only when forecast gating allows them.
- Define milestone state from canonical trend, not a raw low reading.
- Cover insufficient data, no goal, maintenance/gain mode, revised goal, backfill, edit, delete, and skipped-rung behavior.
- Use neutral progress UI; no full-screen moment or cross-feature event.

# Out of scope
Celebration, haptic choreography, fired-marker persistence, share cards, and F11 events (WLO-0075).

# Acceptance
Onboarding and Weight produce identical rungs from identical inputs; no single promised date is rendered; unavailable forecasts explain why without blocking ordinary progress.


## Implementation evidence (2026-09-16)

- Moved the pure 4–8 rung derivation from F01 into `core:engines` as `MilestoneLadder`; onboarding and Weight now call the same function.
- Rung completion is recomputed from the canonical trend on every load. No fired marker or celebration state is persisted, so backfills, edits, deletes, revised goals, and skipped rungs resolve from current facts.
- Added a neutral Material 3 goal-progress card with current trend, goal, remaining amount, completed/upcoming rungs, and optimistic–pessimistic date ranges only. A single promised date is never rendered.
- Reused Targets, goal-safety, EnergyEngine quality, and ForecastEngine boundaries. No goal, forming trend, maintenance, gain, held/withheld forecast, missing inputs, and projection read failures have explicit non-blocking states.
- Kept celebration, haptics, event emission, and marker persistence out of this ticket for WLO-0075.

## Verification

- `:core:engines:allTests`
- `:feature:f01-onboarding:testDebugUnitTest`
- `:feature:f06-weight:testDebugUnitTest`
- ktlint and detekt for touched modules
- `:feature:f06-weight:lintDebug`
- `:app:compileDebugAndroidTestKotlin`
- `checkArchitecture`
- API 29 instrumentation: `M3WeighInTest#weightSurface_activityRecreationRefreshesWithoutAnErrorState` passed with the goal-progress card before and after recreation.
- `git diff --check`
