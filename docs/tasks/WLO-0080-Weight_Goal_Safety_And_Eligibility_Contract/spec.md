# Objective
Define who the weight-goal and forecast system supports and refuse unsafe certainty respectfully.

# Prerequisite
WLO-0068.

# Scope
- Define adult eligibility and handling for minors, pregnancy/breastfeeding, eating-disorder concern, medically influenced weight, and unsafe requested rates.
- Define supported loss, maintenance, and gain modes from the WLO-0068 decision.
- Enforce pace caps, calorie floors, and held/unsupported states at the domain boundary.
- Provide neutral guidance without diagnosis, alarmism, or false precision.
- Review onboarding, goal editing, forecast, milestones, and copy against the contract.

# Acceptance
Unsupported or unsafe inputs cannot produce confident targets or dates; every refusal/held state is tested and accessible; UI and engines consume the same eligibility result; the governing docs cite the safety contract.


# Implementation evidence (2026-09-16)

- Added the authoritative primary-source review at `docs/research/weight-goal-safety-contract.md`, explicitly separating WLO product policy from medical advice.
- Added one shared `WeightGoalSafety.evaluate` domain boundary in `:core:model` covering tracking-only, adult loss/maintenance/gain, minors, unanswered screening, pregnancy, breastfeeding, eating-disorder concern, medically influenced weight, mode/target mismatch, mode-specific pace envelopes, configured calorie floors, and invalid inputs.
- Added neutral accessible copy from the same eligibility result; no copy diagnoses, moralizes, or calls an individual safe/unsafe.
- Added a safety-gated `ForecastEngine.coldStart(input, eligibility)` result. Held/unsupported eligibility returns `Withheld` and cannot contain forecast bands or dates.
- Aligned governing FEATURES, F01, F06, and F07 docs with the contract and linked the cited evidence review.
- Focused tests pass: `:core:model:jvmTest` and `:core:engines:jvmTest`. `checkArchitecture`, focused production ktlint, and `git diff --check` pass. Aggregate `:core:engines:ktlintCheck` is currently blocked only by pre-existing/in-flight WLO-0072 `WeightPolicyBenchmarkTest.kt` formatting violations, not WLO-0080 files.

## Remaining acceptance integration

WLO-0080 intentionally remains **DOING**: the existing pre-weight-first onboarding still calls the legacy numerical forecast entry point and does not collect the four explicit safety answers. WLO-0081 must wire first-run/goal UI to this eligibility result and remove that bypass before the acceptance statement “unsupported or unsafe inputs cannot produce confident targets or dates” is true end-to-end. The contract, engine gate, copy, tests, and governing policy are ready for that integration.

# WLO-0081 integration update (2026-09-16)

- First-run goal intent now consumes WeightGoalSafety; missing current weight/profile/screening returns a single held result.
- No production feature calls the raw one-argument coldStart or measured forecast primitives. Legacy Diet Plan and Hub forecast surfaces hold rather than bypass eligibility.
- Eligible gain goals are explicitly converted to GAIN_FORECAST_UNAVAILABLE at the engine gate because the numerical integrator is loss-only; tests prove that no bands/date escape.

WLO-0080 remains DOING: the existing Goals Editor can still write a Targets goal without collecting/passing the shared safety result, and milestone consumers have not yet been migrated. WLO-0074 owns that editor parity. The end-to-end acceptance statement is therefore not yet true for every goal entry surface.

Follow-up WLO-0083 tracks the evidence, model, benchmark, and UI work required to replace the temporary gain-date hold.

# Final safety migration (2026-09-16)

- Goals Editor now collects the four explicit screening answers and goal mode, evaluates the shared `WeightGoalSafety` result against the canonical current trend, and disables save/revert plus forecast dates for held or unsupported inputs. The UI uses standard M3 controls and renders neutral accessible policy copy; gain goals remain supported while gain dates use the shared `GAIN_FORECAST_UNAVAILABLE` hold.
- Diet Plan Studio persists the screening answers in its resumable draft, disables Start until eligible, and re-evaluates the exact Targets payload at `FinishOnboarding` before any goal-bearing document is written. The accepted safety input is stored beside the profile.
- Hub re-evaluates the stored safety attestation against current trend, target, pace, intake, age, and configured floor before either measured or cold-start math. Missing, stale, held, unsupported, or malformed attestations render no forecast and therefore no date. Eligible adult loss preserves measured/cold-start behavior.
- Milestone consumers remain empty unless gated forecast bands exist. A production search confirms the only raw forecast calls are inside the Hub path after its shared gate; direct engine calls elsewhere are numerical tests only.
- Focused coverage includes every core refusal/held policy, editor adaptation, missing/held/stale Hub attestations, eligible Hub loss, engine withholding, and gain-date withholding.

Verification passed: `:core:model:jvmTest`, `:core:engines:jvmTest` (including a clean `--rerun-tasks` run), `:feature:f01-onboarding:testDebugUnitTest`, `:feature:f10-daily-hub:testDebugUnitTest`, F01/F10/engine `ktlintCheck`, F01/F10 `detekt`, `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `checkArchitecture`, and `git diff --check`. No device-only behavior is required for this domain boundary; Android test sources compile and the standard M3 controls expose their labels and selected/enabled state through Compose semantics.

Acceptance is met end to end. WLO-0083 remains the intentionally separate follow-up for evidence-backed direction-correct gain forecasting.
