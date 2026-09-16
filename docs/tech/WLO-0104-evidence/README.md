# WLO-0104 implementation evidence

Implemented the approved page-based weight overview with real individual weigh-in dots, teal trend, yellow goal in the same axis domain, selected-period change with coverage dates, date/weight tooltip, chart settings/help sheet, preview reset and reserved weigh-in action. Body measurements are reachable from settings; goal detail and event history remain accessible. No app-data reset or new test measurements were made during device QA.

Calculation changes: smooth full daily-scalar history before cropping a viewport; share initialization between live/read/persisted trends; reconcile old TREND rows transactionally via `trendHistoryVersion=full-history-v1`. Suffix repair computes the series once, not once per affected day. Calendar MA7 now excludes observations outside seven calendar days; its formula version and benchmark snapshot/evidence are updated. Point EWMA provenance counts only preceding inputs.

## Host checks

`host-checks.log` records successful:

```
./gradlew :core:engines:jvmTest :core:data:jvmTest :feature:f06-weight:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt checkArchitecture
```

302 tests in final XML results: engines138, data77, weight42, design system22, app23; zero failures. This includes viewport-overlap/headline consistency, stored-trend repair, calendar-MA gaps, goal bounds and two-dimensional hit-testing regression coverage. Some unchanged tasks were up-to-date. Early runs exposed intermittent BodyFatViewModel tests using real background settings I/O, with a leaked dispatcher exception affecting the following deletion-recovery test. Isolated rerun and the final full suite passed; those unrelated test implementations were not changed.

Dedicated goal role contrast verified: dark12.90:1 on background /11.92:1 on surface; light6.12:1 on white /5.76:1 on background. `git diff --check` clean.

## Device checks

Existing emulator-5554, Android10/API29,1080×2400,420dpi. Updated debug APK installed in place. Existing synthetic data retained:46 raw readings over45 daily points. Actual canonical trend77.1kg, goal74kg. This differs from the illustrative mock dataset.

- `overview.png/xml`: page composition, goal in plot, history and reserved action.
- `30-days.png/xml`: −0.8kg over18Aug–16Sep.
- `90-days.xml`: −1.2kg over3Aug–16Sep; latest trend77.1kg unchanged across windows.
- `point.png/xml`: selected3Aug2026,78.3kg tooltip; no debug/detail block or page-height change.
- `settings.png`, `settings-expanded.xml`: radio rows, alpha, Restore defaults, body measurements, math help and Done. Selected MA7 and dismissed; Preview appeared. Reset returned to default trend/summary. Final settings rows support tapping the complete row; alpha is hidden for MA7.
- `large-text.png/xml` and `large-text-chart.png/xml`:200% text, readable wrapped summary, selectable range controls, chart/goal/tooltip/history reachable by scrolling, action reserved below content.

The first large-font capture remained in settings because Back collapsed the expanded sheet before dismissal; it was replaced by a verified overview capture. Startup captures were also replaced after loading finished. Font scale restored to1.0; size/density unchanged. The final build remains installed. No crashes in the inspected crash buffer.

## Limits

Manual TalkBack operation, physical-device haptics, performance traces, light-theme device rendering and a tablet matrix were not run. Nonvisual point actions and keyboard navigation are implemented, with visible focus and spoken series/date/value. The existing wide-screen supporting pane is retained. The new goal color has measured contrast in both themes. Numeric smoothing changes deliberately affect historical derived values; raw recorded weights remain unchanged.
