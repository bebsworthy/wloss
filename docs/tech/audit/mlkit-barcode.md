# SDK data-flow audit card — ML Kit Barcode Scanning (bundled)

Ruling context: R-S13 (on-device ⇒ in-repo audit card, no consent gate required),
C5 (audit cards ship with releases), R-S14 (ML Kit bundled models = tracked
distribution exception). Facts verified 2026-09-12 (Google Maven metadata, artifact
inspection, ML Kit docs + Terms).

| Field | Value |
|---|---|
| SDK producer | Google LLC |
| Artifact | `com.google.mlkit:barcode-scanning:17.3.0` (bundled variant), AAR 9,898,786 B; adds ~2.4 MB to the APK (model statically linked) |
| minSdk | 23 (≤ WLO minSdk 29) |
| License | Proprietary (Google ML Kit Terms); free, no key/fee |
| Purpose in WLO | Barcode → OpenFoodFacts lookup in the F02 capture ladder |
| Input data | Camera frames (barcode region), processed for 1D/2D formats (EAN-13/8, UPC-A/E, QR, Data Matrix, …) |
| Data flow — content | Per ML Kit Terms: "processing of the input data … fully happens on-device"; inputs and results are **not** sent to Google servers |
| Data flow — telemetry | The API **does transmit performance/utilization metrics** (device info, app package/version, error codes) to Google; ML Kit's terms place a disclosure duty on developers — this card is that disclosure. **Opt-out verification (M4 integration, 2026-09-12): NO disable mechanism exists** — upstream feature request googlesamples/mlkit#593 is open/unanswered; the `firebase_analytics_collection_disabled` manifest flag targets Firebase Analytics (not shipped by WLO) with no evidence it silences ML Kit's logger; community reports periodic POSTs to `firebaselogging-pa.googleapis.com`. Mitigations: this disclosure card + the Apache-2.0 ZXing fallback behind the same `BarcodeScanner` port (structural escape hatch if policy ever hardens). |
| Model distribution | Statically bundled (R-S14 tracked exception: no zoo download, counted in APK size) |
| Update mechanism | App releases (bundled) |
| Equal-status manual twin | R-U15: manual text-hint/search entry is always available in the same flow |
| Fallback | Apache-2.0 `com.journeyapps:zxing-android-embedded:4.3.0` behind the same `BarcodeScanner` port |
