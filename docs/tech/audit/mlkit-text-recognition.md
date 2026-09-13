# SDK data-flow audit card — ML Kit Text Recognition v2, latin (bundled)

Ruling context: R-S13 (on-device ⇒ in-repo audit card), C5 (cards ship with
releases), R-S14 (bundled models = tracked distribution exception). Facts verified
2026-09-12 (Google Maven metadata, ML Kit docs + Terms).

| Field | Value |
|---|---|
| SDK producer | Google LLC |
| Artifact | `com.google.mlkit:text-recognition:16.0.1` (bundled latin), base AAR 1,383,148 B; ~4 MB per script per architecture in the APK |
| minSdk | 23 per docs body (page auto-summary incorrectly says 21) — ≤ WLO minSdk 29 |
| License | Proprietary (Google ML Kit Terms); free, no key/fee |
| Purpose in WLO | Nutrition-label OCR → prefilled custom-food draft (manual confirm before save) |
| Input data | Camera/still images of nutrition labels |
| Data flow — content | Fully on-device per ML Kit Terms; inputs and recognized text are not sent to Google servers |
| Data flow — telemetry | Same performance/utilization metrics transmission as ML Kit generally (see barcode card); **opt-out verification (2026-09-12): none available** — same finding as the barcode card (googlesamples/mlkit#593 open; no in-app disable). |
| Model distribution | Statically bundled (R-S14 tracked exception) |
| Update mechanism | App releases (bundled) |
| Equal-status manual twin | R-U15: manual custom-food entry always available; OCR output is a draft only — never saved without explicit confirmation |
| Note | Only the latin script is bundled; other scripts would each add ~4 MB and are not adopted |
