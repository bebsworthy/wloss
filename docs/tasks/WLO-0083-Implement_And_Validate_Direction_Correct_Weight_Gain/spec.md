# Objective

Ship an honest numerical forecast for supported adult gain goals without weakening the WLO-0080 safety boundary.

# Scope

- Define a direction-correct gain energy/weight model and its assumptions from authoritative primary evidence.
- Keep WeightGoalSafety as the single eligibility gate and retain pace caps/held states.
- Add golden, property, and benchmark scenarios covering gain direction, already-met targets, zero/surplus energy, deceleration/acceleration behavior, and finite termination.
- Add provenance/copy that distinguishes product estimates from medical advice.
- Replace GAIN_FORECAST_UNAVAILABLE only after quality gates pass.

# Acceptance

An eligible gain goal produces monotonic direction-correct bands and a date only through the gated API; held/unsupported inputs never do; benchmarks, architecture, lint, and focused UI/device coverage pass.


## Implementation evidence (2026-09-16)

- Replaced `GAIN_FORECAST_UNAVAILABLE` with a dedicated adult gain integrator using the published linearized expenditure feedback of 22 kcal/day/kg gained. Loss keeps its calibrated BMR/Adjustment/Activity path; gain is not sign-reversed loss.
- Versioned the model as `forecast/directional-3band-v2`, retained R-A6's explicit 7,700 kcal/kg product approximation, and published primary evidence, assumptions, equilibrium behavior, and non-medical-advice boundary in `docs/research/weight-gain-forecast-v1.md`.
- Preserved `WeightGoalSafety` as the single gate. Developing forecasts remain range-only; held/unsupported inputs expose no new bands or dates; measured gain requires the existing Updating quality state and solve.
- Added frozen gain golden data, generated property checks, release benchmark coverage, and focused cases for direction, band order, already-met target, zero surplus, unreachable equilibrium, deceleration, signed pace, and finite horizon.
- Removed stale UI bypasses so onboarding, Settings Goals, Hub consumers, and Weight progress use the same gated path. API 29 instrumentation proves an eligible gain goal renders its provisional forecast card.
- Verification passed: engine/model/onboarding/weight tests, focused connected UI tests, aggregate lint, ktlint, detekt, architecture, and `git diff --check`.
