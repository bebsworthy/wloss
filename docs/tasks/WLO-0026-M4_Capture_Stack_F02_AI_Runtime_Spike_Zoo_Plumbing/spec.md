# M4 — Capture stack (F02 AI)

## Objectives
1. Decide T-E1 (inference runtime) and T-E2 (food model) via spike → ADR before
   building (JIT research sequenced here by plan).
2. Build the R-S14 zoo infrastructure ONCE: download-under-NetworkDispatcher,
   hash-pin verification, before-download size disclosure, per-model storage accounting,
   one-tap reclaim.
3. Photo → scan → correct → save loop with provenance, confidence, and `held` honesty.
4. Barcode + label OCR assists with equal-status manual twins (R-U15).

## Contents
- Spike (step 0): runtime matrix (LiteRT vs ONNX Runtime Mobile vs MediaPipe Tasks) on
  the API 29 AVD + a food-detector model shortlist benchmarked functionally (accuracy
  spot-checks on a fixed image set; sizes; license + training-data names per R-S12).
  Output: ADR-006 (runtime) + ADR-007 (food model) + audit cards.
- `:core:ai` module: `PhotoAnalyzer` port implementation; result shape
  `Analysis<T>` = items + confidence + provenance + `held`; model manager (zoo UI deep
  link `wlo://ai/models`).
- `:core:media`: CameraX capture flow (viewfinder, silent shutter where specced), EXIF +
  GPS + filename stripping, ≤1024 px processing, discard-at-save default with opt-in
  retention (R-U14), thumbnails only.
- Barcode: ML Kit Barcode (bundled) behind `BarcodeScanner` port with audit card; ZXing
  fallback; OFF lookup through the NetworkDispatcher allowance (R-C4) with cache +
  per-lookup audit rows; USDA fallback stub.
- Label OCR: ML Kit Text Recognition behind `OcrReader` (audit card); parse → prefilled
  custom-food draft (manual-confirm).
- Correction loop: scan result screen (per-item chips, confidence, edit/merge/split,
  swap food) — everything editable before save; correction stored as prior signal
  (F02 §3) — correction cache shared with F09 later (R-B6 mechanism).
- Sanity rails: per-class energy-density clamps + mass×density ceiling, pure Kotlin,
  tested without any model.

## Relevant documentation
- `docs/features/F02-food-logging.md` §3, §5, §9, §10; `docs/features/F12-ai-platform.md`
  §3.1 (food-photo category), §3.2, §3.6 (receipts — writer lands here), §3.7 (payload
  ladder), §3.8 (dispatcher)
- Rulings: R-S12, R-S13, R-S14, R-U14, R-U15, R-C4; DECISION-SPACE C5 (audit cards),
  T-C5 (embeddings deferred), T-E10
- Emulator note: functional only; no perf claims (WLO-0014)

## Acceptance criteria
1. ADR-006/007 accepted; every bundled proprietary SDK has an audit card in-repo.
2. On the emulator: photo of test meals → items with confidence → corrected → saved into
   the M3 diary with derived provenance; `held` appears when confidence is low.
3. Zoo: model downloads once, hash-verified, shows size before download, reclaim works;
   airplane-mode (emulator network off) after download keeps everything working.
4. Barcode scan → OFF hit → product logged, cached offline, audit row written; no-OFF
   path degrades to manual search.
5. Debug egress monitor shows exactly: model download (once) + OFF lookups (on demand) —
   nothing else, each receipted.
