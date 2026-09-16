# Objective
Orchestrate the weight-first implementation in dependency-safe waves and keep verification evidence attached to the owning tickets.

# Wave order
0. Hygiene: WLO-0066 and tracker reconciliation.
1. Product contract: WLO-0068; answer its launch-mode/navigation questions and WLO-0053's chart questions.
2. Trust research in parallel: WLO-0072 daily-scalar benchmark and WLO-0080 safety/eligibility after the contract.
3. Foundation: WLO-0052 → WLO-0051.
4. Capture reliability in parallel: WLO-0069 and WLO-0071 after foundation.
5. Truthful feedback: WLO-0053 → WLO-0070.
6. Android ingestion: amended WLO-0038.
7. Forecast evidence: WLO-0073 after scalar and safety contracts.
8. Goal loop: WLO-0074 → WLO-0045.
9. Release hardening/dogfood: WLO-0029 after release-1 gates and owner publication decisions.
10. Differentiation: WLO-0042.
11. Advanced: WLO-0054, WLO-0075, WLO-0078, WLO-0044, WLO-0039.

# Orchestration rules
- One agent owns each ticket; parallelize only disjoint modules or agreed interfaces.
- Start from `ticket task <ID> brief`; move to doing before edits and done only after acceptance.
- Treat governing-doc changes and unresolved ticket questions as dependency gates.
- Preserve user changes and the existing uncommitted remediation work.
- Every product-facing wave runs focused unit/Compose tests, architecture checks, lint/formatting, and an API 29 emulator flow; release gates run the full suite.
- Material 3 standard components remain the default; every custom exception needs the required local KDoc justification.

# Completion
Release 1 is ready to dogfood when capture, units, state, charts, Health Connect identity, forecast eligibility/calibration, goal editing/progress, backup/export, accessibility, and deterministic tests all meet their tickets without unresolved P0 questions.

## Contract-to-code gap closed (2026-09-16)

WLO-0068 exposed two implementation seams that had no owning ticket:
- WLO-0081 implements the short weight-first onboarding and preserves Diet Plan setup as an optional later flow.
- WLO-0082 makes Weight the default/first top-level destination with standard adaptive Material 3 navigation.

Schedule WLO-0081 after WLO-0051 and WLO-0080 have frozen entry/safety behavior. Schedule WLO-0082 after WLO-0068; it may proceed in parallel with research if its shell files do not overlap another active ticket. Both block WLO-0029 dogfooding.


## Wave progress (2026-09-16)

Completed with ticket evidence: WLO-0066 deterministic Hub test; WLO-0068 product contract; WLO-0052 global units; WLO-0051 entry correctness; WLO-0071 state reliability; WLO-0072 scalar/smoothing benchmark; WLO-0080 safety boundary; WLO-0081 weight-first onboarding; WLO-0082 Weight-first shell/navigation.

Current parallel wave is dependency-safe: WLO-0069 owns entry ergonomics in F06 UI while WLO-0073 owns forecast calibration in core engines/research. WLO-0074 remains gated on WLO-0073; WLO-0083 keeps direction-correct gain forecasting out of Release 1 and explicitly withheld meanwhile.


## Wave progress (continued, 2026-09-16)

Completed with device/static evidence: WLO-0069 M3 weigh-in ergonomics, WLO-0053 honest selected-window chart, WLO-0073 calibrated forecast quality contract with cold-start point-date suppression, and WLO-0074 shared goal-editor parity. The unchanged ≤28-day cold-start revision gate remains an explicit 39-day failure; Release 1 mitigates it by making developing forecasts range-only until measured availability.

Next dependency-safe order: WLO-0070 post-save feedback, then WLO-0045 shared goal/milestone progress (both touch F06 and should not run concurrently); amended WLO-0038 Health Connect ingestion may run in parallel because it owns Android ingestion/data boundaries. WLO-0083 remains Release 2.


## Release 1 implementation checkpoint (2026-09-16)

Completed with isolated commits and ticket evidence: WLO-0070 trend-first post-save confirmation; WLO-0045 shared goal and milestone progress; WLO-0038 durable Health Connect weight/body-fat synchronization; WLO-0084 haptic permission contract; and WLO-0083 direction-correct gain forecasting. The final aggregate gate passed lint, ktlint, detekt, architecture, focused unit/benchmark/property/golden tests, and API 29 UI coverage.

The scoped Release 1 weight-core implementation is now complete. The remaining Release 1 program step is WLO-0029: establish signed alpha/stable distribution and begin real-device dogfooding without breaking data continuity. Findings from dogfooding should create focused defects rather than reopening completed contracts.

After dogfood distribution, the recommended Release 2 order is WLO-0042 progress ribbon. Advanced tickets remain gated on dogfood evidence.
