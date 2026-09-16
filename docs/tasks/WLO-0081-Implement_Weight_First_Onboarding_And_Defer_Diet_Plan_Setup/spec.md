# Objective
Implement the WLO-0068 first-run contract without deleting the existing optional Plan Studio.

# Scope
- First run: welcome/privacy → required global kg|lb choice → optional loss/maintenance/gain goal → Health Connect/file import or manual first weigh-in → Weight.
- Every step after unit is skippable; missing values remain absent/held, never guessed.
- Reuse existing F01 state/engine/domain doors where useful, but separate onboarding completion from creation of Diet Plan v1.
- Preserve resumability and process-death recovery.
- Offer the current diet-plan wizard later from Plan/Settings; it is no longer an app-entry gate.
- No permission prompt before point of use.

# Acceptance
Fresh install reaches a useful Weight surface without configuring food or planning; kg/lb and optional goal/import/manual paths persist correctly; skip and resume paths are deterministic; existing Plan Studio remains reachable; focused Compose/instrumentation, accessibility, architecture, lint, and formatting gates pass.

# Implementation evidence (2026-09-16)

- Replaced the fresh Weight/Hub gate with a four-step resumable weight-first flow: privacy, required global unit, optional loss/maintenance/gain intent, then manual/file/Health Connect-later/skip. Completion is independent of Diet Plan creation.
- Persisted step, unit, optional goal fields, source choice, and manual reading in the atomic JSON document store. Completion creates a minimal profile and optional verbatim WEIGHT event, writes the completion flag last, and retains the goal intent separately.
- Made birth year, height, and starting weight nullable through Profile/NewProfile/Room schema v7 and backup shape; added 6→7 migration coverage. No fake health facts are created for skipped fields.
- Added the former diet wizard as the optional Settings → Diet Plan Studio route.
- Wired first-run goal intent to WeightGoalSafety. Removed all production calls to ungated ForecastEngine coldStart/measured paths from legacy F01 and Hub. Added explicit GAIN_FORECAST_UNAVAILABLE handling so gain remains a supported goal direction without a false date from the loss-only integrator.
- Updated Shell wording and shared onboarding/shell instrumentation helpers for completion-without-plan semantics.
- Focused JVM gates pass: F01 unit tests, core model safety tests, engine eligibility tests, and database migration tests. Production and androidTest Kotlin compile, app/F01/model/engine/database ktlint, and checkArchitecture pass.

## Remaining acceptance verification

Keep DOING until the focused device classes run on a cleared emulator: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.OnboardingFlowTest,app.wlo.app.OnboardingResumeTest,app.wlo.app.WloShellTest`. Health Connect activation itself remains owned by WLO-0038; this flow persists the choice and requests no permission early.


## Final device acceptance (2026-09-16)

- API 29 `OnboardingFlowTest`: 3/3 passed after correcting the canonical Weight route assertion to `f06/weight`; covers manual useful-first-weight, global pound choice, Settings-only unit switch, and gain-without-false-date.
- API 29 `OnboardingResumeTest`: passed, covering persisted draft/process resume behavior.
- API 29 `WloShellTest`: 5/5 passed, covering fresh setup, completion without Diet Plan, useful raw trend, no ungated forecast, and access to the optional Plan surface.
- Final app ktlint and repository diff formatting pass.
