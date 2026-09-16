# WLO-0093 implementation evidence

Synthetic test data only.

## Acceptance coverage

- A93-01: Goals and Profile numeric fields have persistent labels, unit text,
  supporting errors and reflow vertically; goal mode uses M3 segmented buttons
  and the date uses M3 DatePicker with Clear.
- A93-02/A93-04: the two-writer repository tests cover v1 and stale-version
  conflicts; a new case proves a balanced weight-only v1 retains a null budget.
- A93-03: shared `DecimalInputTest` covers comma/dot parsing and nonfinite input;
  drafts retain raw strings and the display unit.
- A93-05: the save-journal round-trip test covers committed-version and safety
  metadata recovery. The UI freezes submission values and blocks double submit.
- A93-06: profile/base-version-scoped drafts survive recreation; dirty Back
  offers Keep editing/Discard, and durable success removes the draft.
- A93-07: `ProfileFactsViewModelTest` covers the 1900 lower bound and rejection
  of a future year. Starting weight is labeled rather than presented as trend.

## Unverified here

- Disposable-emulator parity instrumentation, TalkBack focus/announcements,
  200% font screenshots and physical haptics.

## Commands run

```text
./gradlew :feature:f01-onboarding:testDebugUnitTest :core:common:jvmTest :core:data:jvmTest :app:testDebugUnitTest :app:assembleDebug checkArchitecture
BUILD SUCCESSFUL

./gradlew :core:common:ktlintCheck :feature:f01-onboarding:ktlintCheck :feature:f01-onboarding:detekt :app:ktlintCheck :app:detekt
BUILD SUCCESSFUL
```
