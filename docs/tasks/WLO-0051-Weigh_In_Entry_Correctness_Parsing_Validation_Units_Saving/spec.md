# Objective
Make manual weigh-in persistence unambiguously correct before adding convenience or delight.

# Scope
- Parse trimmed decimal input in the active global mass unit; accept locale comma and convert once to canonical kilograms.
- Validate finite values and the repository's 30–300 kg canonical range before launch of a write.
- Put field-specific errors inside the open sheet with Material 3 supporting/error text and polite live-region semantics.
- Represent idle, invalid, saving, success, and failure explicitly; disable repeated submission while saving and prove one tap sequence creates one event.
- Replace the current outlier actions with Keep and Correct; Keep is primary, Correct deletes the flagged event and reopens entry with a safe prior value.
- Keep storage errors visible and actionable; never turn them into empty content or a silent return.

# Out of scope
Prefill/pickers/IME (WLO-0069), post-save confirmation/haptic (WLO-0070), OCR/Bluetooth/Health Connect, and live trend preview.

# Acceptance
- Empty, malformed, comma-decimal, NaN/infinite, under-range, over-range, kg, and lb cases are covered by deterministic tests.
- Save never silently no-ops and cannot double-submit.
- An lb entry round-trips through canonical kg without display-string writeback.
- Keep/Correct copy and action priority match the correction model.
- Focused unit, ViewModel, Compose, architecture, lint, and formatting gates pass.


# Connected-suite evidence (2026-09-16)

During the WLO-0066 full API 29 run, `M3WeighInTest.weighIn_twoEntriesSameDay_showsLowestAndListRows_thenDeleteOne` failed after saving the second value: expected `76.8`, while the UI remained at `77.0 kg`. The other 64 tests, including WLO-0066, passed. Reconcile this regression while implementing the unit/entry correctness work; do not weaken the assertion without proving canonical kg and display-unit behavior.

## Implementation evidence — 2026-09-16

Implemented locale-tolerant kg/lb parsing with one canonical conversion, finite 30–300 kg validation, field-local Material 3 errors/live regions, explicit idle/invalid/saving/success/failure state, duplicate-submit suppression, visible persistence failures, and primary Keep / secondary Correct outlier actions. Correct deletes the flagged event, reloads canonical state, and reopens from the safe remaining value. Logbook replacement now accepts an unchanged stored timestamp under deterministic clocks while still rejecting newly changed future times.

Deterministic coverage added for trimmed dot/comma input, kg/lb round trips, malformed/non-finite values, inclusive bounds, invalid-sheet feedback, lb persistence, rapid duplicate submit, safe outlier correction, same-day reweigh, and the logbook unchanged-timestamp regression.

Device evidence: the API 29 `M3WeighInTest` full class reached 9/10 passing. Every WLO-0051 entry-correctness, save-state, unit, outlier-correction, same-day reweigh, and logbook regression case passed. The sole failure was the adjacent pre-existing smoother-preview case (`smoothingControls_moveTheLine_asLabeledPreview`), not an entry-correctness acceptance case. Its stale async reload race has since been replaced with synchronous pure smoothing over the already-loaded samples, and the assertion now uses the production unit rounding contract; this follow-up awaits a focused device rerun after the shared emulator is released by WLO-0082.

Static evidence: `:feature:f06-weight:ktlintCheck`, `:feature:f06-weight:detekt`, and `:app:ktlintAndroidTestSourceSetCheck` pass; `checkArchitecture` reports D1–D7 + D9 clean. The combined JVM/unit and Android-test compile invocation is temporarily blocked outside this ticket by a concurrent non-exhaustive `when` in `core/model/WeightGoalSafety.kt` for newly added enum branches.

Follow-up gates: `:feature:f06-weight:testDebugUnitTest` and `:feature:f06-weight:lintDebug` pass after the synchronous preview correction. `:app:compileDebugAndroidTestKotlin` progressed through the F06 unit suite but is currently blocked by a separate concurrent nullable arithmetic error in `app/ui/settings/ProfileFactsScreen.kt:98`; no WLO-0051 source failed compilation.

The concurrent settings compile error was resolved; `:app:compileDebugAndroidTestKotlin` now passes, including `M3WeighInTest.kt`.

Final device acceptance: on API 29 (`wlo-api29`, emulator-5554), `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.M3WeighInTest#smoothingControls_moveTheLine_asLabeledPreview` passes after the synchronous preview and production-rounding correction (`BUILD SUCCESSFUL`, 2026-09-16). Together with the prior 9/10 class run—where every WLO-0051 entry-correctness case passed—this closes the remaining adjacent regression. All acceptance evidence is green.
