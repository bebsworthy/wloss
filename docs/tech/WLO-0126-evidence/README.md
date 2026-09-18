# WLO-0126 — food intake editor verification

Completed 2026-09-18. Entry: Weight → Food intake, after setting a weight goal.

## Automated checks

167 tests passed: documents 32, data 84, F01 27, app 24.

- `:app:assembleDebug`
- `:feature:f01-onboarding:testDebugUnitTest`, `:core:data:jvmTest`, `:core:documents:jvmTest`, `:app:testDebugUnitTest`
- `:feature:f01-onboarding:detekt`
- `ktlintCheck` for F01, core:data, core:designsystem, F06, app
- `git diff --check`

Tests cover candidate population/direction, goal distance not increasing the deficit,
mode equivalence, exact entry and slider steps, weekly totals, manual floor exemption,
F07 retaining its floor checks, version conflicts, nutrient edits in the same immutable
revision, invalid splits, screening unknowns and Targets v1/v2 compatibility.

## Emulator checks

Installed the debug APK on emulator-5554, retaining the demo fixture. No crash-buffer entries.

- Switched Adjustment ↔ Intake: −500 ↔ 1,276, with intake and slider position preserved.
- Saved 500 kcal below the configured floor; returned to Weight and reopened the same value.
- Entered zero adjustment: intake matched 1,776 maintenance, with no goal date.
- Cancelled that edit: the saved 500 kcal remained unchanged.
- Answered screening with four **No** responses for the QA fixture, then selected the
  available −500 candidate. The production forecast displayed developing uncertainty
  bands; saving/reopening retained the target and screening.
- Edited fiber 25 → 30 g; saved and reopened 30 g.
- Enabled weekly allocation at 1,276 kcal average with +200 per weekend day. The weekday
  amount falls below the forecast floor, so the chart was held while saving remained
  available. Saved and reopened the weekly configuration.
- Changed weekend extra to +100: the forecast returned; saved and reopened successfully.
- Final fixture: trend 77.1 kg, goal 74 kg, maintenance estimate 1,776, intake average
  1,276, weekday/weekend redistribution +100, fiber 30 g. These are emulator test values,
  not a personal recommendation.

## Visual evidence

- [Final Adjustment screen](adjustment-final.png)
- [Intake mode with slider](intake-mode.png)
- [Final comparison viewer](comparison-final/index.html)
- [Comparison report](comparison-final/report.json)

Generated with `tools/screenshot-compare/agent.py`; inspected the side-by-side PNG and
report. Anchors use the first visible glyph in the maintenance numeral. The reference's
maintenance, intake and weight-summary text were adapted in browser memory to the QA
fixture; its illustrative chart still uses the original 84 → 78 kg example. The app
uses the real decelerating forecast for 77.1 → 74 kg. Chart paths/date windows therefore
intentionally differ. Standard native M3 controls, app-bar metrics, source chips and
typography also differ from HTML. This verifies hierarchy and behavior, not pixel identity.
The earlier comparison directory records the intermediate layout before final density
and numeric-input typography adjustments.

## Approved-layout correction

The former `fixed-review` approximation was rejected. The final comparison is
[approved-layout-final/index.html](approved-layout-final/index.html), with a
second [Intake-mode comparison](approved-layout-intake-final/index.html).
See [final findings](approved-layout-final/findings.md) for the measured layout,
owner-approved Inter font repair, engine-data boundaries, expanded-control QA
and original-viewport verification. Both the mockup and Android now load the
same bundled Inter font instances.
