# Objective
Make repeat manual weighing fast, native, reachable, and accessible after correctness is established.

# Prerequisites
WLO-0052 and WLO-0051.

# Scope
- Prefill from the latest reliable reading/trend in the active unit; make one-tap save possible without forcing the keyboard open.
- Use Material 3 date and time pickers; free-text ISO/HH:MM is not the primary path.
- Remove explanatory clutter superseded by picker behavior and show neutral same-day context when useful.
- Keep content reachable with IME, compact height, landscape, and 200% font scaling.
- Give all fields, steppers, errors, and state transitions correct TalkBack names/state/live-region semantics.

# Acceptance
First daily entry prefills when history exists; empty history remains obvious; date/time require no typing; Save is reachable in supported configurations; accessibility and screenshot tests cover compact and large-font states.


# Connected-suite evidence (2026-09-16)

During the WLO-0066 full API 29 run, `M3WeighInTest.logbookEdit_tapOpensSheet_saveReplacesTheEntry` failed because Save surfaced `check the date and time — YYYY-MM-DD and HH:MM, today or earlier`; the expected `80.4 kg` replacement never appeared. The other 64 tests, including WLO-0066, passed. Cover this date/time entry regression when replacing free-text date/time with Material 3 pickers and keep deterministic test-time handling.


## Implementation evidence (2026-09-16)

- Manual entry now prefills from the canonical trend, falling back to the latest reliable stored reading, converted into the active kg/lb display unit. Empty history stays blank with explicit neutral context. An initially open deep-link/quick-action sheet is backfilled after async load only while untouched; typed input is never overwritten.
- Both create and logbook-edit sheets replace free-text date/time entry with standard Material 3 `DatePickerDialog` + `DatePicker` and `TimePickerDialog` + `TimePicker`. Existing ISO/24-hour strings remain only as the small ViewModel parsing boundary. The WLO sheet title slot supplies pane-title semantics.
- Feature-local scroll/IME padding keeps actions reachable without changing shared sheet behavior. Numeric fields do not request focus, use decimal input and a Done action that dismisses focus. Date/time controls stack at full width for large text and compact widths.
- TalkBack semantics name date/time controls with their selected values, name steppers as increase/decrease with unit and current-value state, and announce field/time/save errors via polite live regions. Logbook edit validation now renders inside its modal sheet instead of behind it.
- Deterministic ViewModel tests cover kg/lb canonical-trend prefill, explicit empty-history state, async initial-sheet backfill, and protection of typed input from late reloads.
- Focused API 29 device acceptance passed: `M3WeighInTest#prefilledEntry_usesMaterialPickers_andSavesWithoutTypingWeight`, `#logbookEdit_tapOpensSheet_saveReplacesTheEntry` (the recorded WLO-0066 regression), and `#compactLargeFontSheet_keepsSaveReachableWithIme_andCapturesEvidence`. The last case used 200% font, landscape/compact height, and an open IME; Save remained scroll-reachable and `/sdcard/wlo0069-weighin-large-font-landscape-ime.png` was captured. Device font scale was restored to 1.0.
- Verification passed: `:feature:f06-weight:testDebugUnitTest`, `ktlintCheck`, `detekt`, `lintDebug`; `:app:ktlintAndroidTestSourceSetCheck`; `:app:compileDebugAndroidTestKotlin`; `checkArchitecture` (D1-D7 + D9 clean); the three isolated connected tests above; and scoped `git diff --check`.
