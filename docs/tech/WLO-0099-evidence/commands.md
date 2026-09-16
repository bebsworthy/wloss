# WLO-0099 command log

Run 2026-09-16 from `/Users/boyd/wip/wloss` with synthetic test data.

## Host suites

```text
./gradlew :core:data:jvmTest :core:engines:jvmTest :core:database:jvmTest \
  :core:vault:jvmTest :core:designsystem:testDebugUnitTest \
  :feature:f06-weight:testDebugUnitTest \
  :feature:f01-onboarding:testDebugUnitTest \
  :feature:f13-vault:testDebugUnitTest :app:testDebugUnitTest
```

Result: Pass.

```text
./gradlew :app:assembleDebug checkArchitecture ktlintCheck detekt
```

Initial result: Fail. Detekt found WLO-0098 line length, unused compatibility
parameter, and expanded loader length suppressions. These were corrected with
format-only wrapping and narrow documented suppressions. Final result: Pass.

## Device suite

Device: `wlo-api29`, Android 10/API 29, 1080×2400 px, 420 dpi, headless.

```text
./gradlew :app:connectedDebugAndroidTest
```

Result: **Fail** after 17m29s. 78 tests ran: 37 passed, 41 failed, 0 errors,
0 skipped. Full machine-readable result:
`app/build/outputs/androidTest-results/connected/debug/TEST-wlo-api29(AVD) - 10.xml`
(generated build output, not committed).

Failure clusters:

- Legacy Hub-first expectations after WLO-0096 intentionally made Weight the
  start destination (`expected hub`, received `weight`/`f06/weight`).
- Tests waiting for removed duplicate-title tags such as `f06-title` while the
  Weight content itself is visibly rendered.
- Onboarding/resume expectations that no longer match the simplified WLO-0097
  copy/step sequence; these require separate triage rather than blind retries.

The suite was run once. No failures were hidden with a green rerun.

## Not run

- Current available Android API with runtime notification permission
- Manual TalkBack and keyboard journeys
- 320dp/360dp/landscape/600dp/840dp and hinge matrix
- 100%/200% font and IME intersections
- Light/dark and comma-locale manual screenshots
- Animation scale 0/1x recordings
- Physical haptics
- 60-second frame trace and prechange comparison
