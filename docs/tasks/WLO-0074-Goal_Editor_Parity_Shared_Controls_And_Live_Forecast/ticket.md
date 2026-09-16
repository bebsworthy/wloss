---
id: WLO-0074
title: 'Goal editor parity: shared controls and live forecast'
status: done
theme: weight-experience
release: 1
created: 2026-09-15T21:28:24Z
modified: 2026-09-15T23:52:42Z
closed: 2026-09-15T23:52:26Z
revision: 22e6c089fd4dfef5
blocks: [WLO-0045]
related: [WLO-0079, WLO-0083]
---

# Description

Implemented WLO-0074 goal-editor parity.

Implementation:

- Added one pure `WeightGoalPreview` boundary shared by Plan Studio onboarding and Settings Goals. It owns date-to-implied-pace conversion, `WeightGoalSafety` eligibility, `ForecastEngine` quality-state mapping, and gain-date withholding (WLO-0083).
- Settings Goals reads the global mass unit, canonical trend context, actual `DayProjection` evidence, and renders developing/held/available/withheld semantics before save. Developing shows only a provisional outer range; held/unsupported paths produce no new date.
- Shared the safety-question controls/copy across onboarding and Settings using standard Material 3-backed controls.
- Added version-scoped canonical `GoalsEditorDraft` persistence, so unsaved target/pace/date/budget/mode/safety inputs survive process recreation and unit switches without conversion drift. Successful save retires the draft and writes exactly one Targets revision through `STUDIO_F01`.
- Registered the previously missing F01 Goals destination in the app `NavHost`, making Settings → Goals reachable.
- Preserved direction-safe gain support: gain goals remain eligible while forecast date remains withheld.

Evidence:

- `:feature:f01-onboarding:testDebugUnitTest` — PASS (identical-input parity, date-implied pace, held no-date, gain withholding, draft round-trip, kg/lb canonical conversion, safety cases).
- `:feature:f01-onboarding:detekt` — PASS.
- `:feature:f01-onboarding:ktlintCheck` — PASS.
- `:app:compileDebugAndroidTestKotlin` — PASS.
- `:app:detekt` — PASS.
- Focused `GoalsEditorParityTest` connected test — PASS on emulator-5554 / API 29 (Settings → Goals, lb display, safety-held copy, no forecast card).
- `checkArchitecture` — PASS (D1–D7/D9, 28 projects).
- `git diff --check` — PASS.

Note: whole-app ktlint is currently blocked only by a pre-existing import-order issue in concurrently edited `M3WeighInTest.kt`; the new `GoalsEditorParityTest` import order is corrected and compiles/runs.
