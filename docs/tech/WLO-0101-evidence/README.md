# WLO-0101 review evidence

Synthetic API29 `wlo-api29` fixture; reviewed HEAD d41a60a. Installed and built APK SHA-256 match: `e35b6af9b92cbbecafa541405d0672dfc6bc61c97b66d4fb812474ef352062aa`.

- `01-initial.png`: initial compact overview (1080×2400px, 420dpi, font1.0).
- `03-font-200.png`: same overview at font2.0; default sizing restored afterward.
- `04-goal-draft-160lb.png` and `05-goal-draft-reopened.png`: 160 lb becomes 352.7 lb after toolbar Up/reopen. Draft discarded afterward; `restored-goal.xml` confirms the original 74.0 kg target remains.
- `08-imperial-selection.png`: lb hero/axes versus raw unrounded kg selected value.
- `09-wide-1080dp.png`: 2160×1800px at 320dpi, giving 1080dp full window width; rail but no supporting pane. Density/size restored to physical 420dpi/1080×2400px.
- UI XML files substantiate text, disabled previous/next parent semantics, duplicate headings and the discard action. These are not substitutes for a manual TalkBack test.
- `host-checks.log`: successful existing host tests/build/architecture task invocation; up-to-date/cache results reused where applicable.
- `ImplementationReviewRegressionTest.kt`, `policy-regression-result.xml`, `policy-regression.log`: temporary real-Room test exposing stored79/canonical80 mismatch. Test copied out of the module and removed afterward. To reproduce, copy it into `core/data/src/jvmTest/kotlin/app/wlo/core/data/`, run `./gradlew :core:data:jvmTest --tests app.wlo.core.data.ImplementationReviewRegressionTest`, then remove the copied file. It intentionally fails until R02 is fixed.
- `final-checks.log`: normal core-data test task plus root ktlint/detekt successful after temporary test removal.
- `prior-device-failures.json`: compact preserved failure messages from WLO-0099's earlier 78-case/41-failure run. WLO-0101 did not rerun that suite or clear emulator data.
- `findings.json`: machine-readable 21 findings, owners and source anchors.

No measurements, Targets versions or imports were committed during UI inspection. The temporary goal draft and setting changes were restored; navigation/selection state may differ. Full assistive traversal, current API, exhaustive failure injection and frame-timing evidence remain explicitly unverified.
