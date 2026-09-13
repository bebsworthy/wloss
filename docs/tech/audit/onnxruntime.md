# SDK audit card — ONNX Runtime Mobile (inference runtime, OSS)

Ruling context: R-S13 (audit card for capture/AI stack components), ADR-006. Facts
verified 2026-09-12 (Maven Central metadata, AAR inspection).

| Field | Value |
|---|---|
| Producer | Microsoft |
| Artifact | `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`, AAR 51,897,836 B (ABIs: arm64-v8a, armeabi-v7a, x86, x86_64) |
| minSdk | 24 (AAR manifest) — ≤ WLO minSdk 29 |
| License | MIT (OSS) |
| Purpose in WLO | Executes the R-S14 zoo models (first: ADR-007 food classifier) fully on-device |
| Data flow | None: inference is local; the runtime makes no network calls; models arrive only through the hashed zoo channel (`NetworkDispatcher` + receipts) |
| Telemetry | None in the standalone artifact |
| Model distribution | Not bundled (R-S14): downloaded on first use, sha256-pinned, size-disclosed, reclaimable |
| Update mechanism | App releases (Gradle catalog pin; bump = re-run spike spot-checks) |
| Notes | GitHub 1.29.1/1.30.0 existed at check date but Android AARs were not on Maven Central yet — pinned to 1.29.0 |
