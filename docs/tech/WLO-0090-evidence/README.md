# WLO-0090 implementation evidence

Synthetic test data only. No health records or credentials are included.

## Acceptance mapping

| Scenario | Evidence |
| --- | --- |
| A90-01 | `WeighInViewModelReliabilityTest`: section changes are immediate and `SavedStateHandle` restores the selected section. |
| A90-02 | `WeighInViewModelReliabilityTest`: independent Weight and Body fat windows survive state restoration. |
| A90-03 | `BodyFatViewModelTest`: edits invalidate the revisioned estimate and prevent a stale save. Method/version are carried by the command. |
| A90-04 | `BodyFatViewModelTest`: comma decimal input and 40 in → 101.6 cm conversion. Fields expose their active unit and stack at compact widths. |
| A90-05 | `BodyMeasurementRepositoryTest`: injected failures at every mutation stage roll back all rows; same-token retries are idempotent and retry after rollback succeeds. |
| A90-06 | `BodyMeasurementRepositoryTest`: method ID/version, source and canonical explanation inputs survive the read model. The screen groups chart points into named method series and labels legacy rows “Method not recorded”. Export preservation remains owned by WLO-0095. |
| A90-07 | A committed operation ID triggers a parent refresh without navigation; failed writes retain the draft and computed result for retry. Parent read failures use the existing retry state. |

## Commands run

```text
./gradlew :feature:f06-weight:testDebugUnitTest --tests '*BodyFatViewModelTest*' --tests '*WeighInViewModelReliabilityTest*' :core:data:jvmTest --tests '*BodyMeasurementRepositoryTest*'
BUILD SUCCESSFUL

./gradlew :core:data:ktlintCheck :core:data:detekt :feature:f06-weight:ktlintCheck :feature:f06-weight:detekt
BUILD SUCCESSFUL

./gradlew :feature:f06-weight:testDebugUnitTest :core:data:jvmTest :core:database:jvmTest :core:vault:jvmTest :app:assembleDebug checkArchitecture
BUILD SUCCESSFUL
```

## Unverified here

- Compose instrumentation at 320 dp and 200% font, screenshot/recording, TalkBack focus order and announcement behavior.
- Physical-device haptics.
- JSON export/restore preservation, which is implemented and validated by WLO-0095.
