# ADR-006: On-device inference runtime (decides T-E1)

**Status:** Accepted (WLO-0024 milestone-4 spike, 2026-09-12; research verified against
Google Maven / Maven Central / vendor artifacts with download-inspection, checked
2026-09-12 — see WLO-0026 research record)

## Context

M4 needs an on-device runtime for food-photo analysis (classification now; detection /
segmentation later for F08/F09), loading models downloaded at runtime from arbitrary
file paths (R-S14 zoo). Emulator-only verification (WLO-0014): the decision optimizes
for functional fit, policy fit, and distribution reality — not device perf.

Candidates verified: **LiteRT** (`com.google.ai.edge.litert:litert:2.2.0`, Apache-2.0,
11.2 MB AAR, Kotlin CompiledModel API, `create(filePath)`, no telemetry), **ONNX
Runtime Mobile** (`com.microsoft.onnxruntime:onnxruntime-android:1.29.0`, MIT, 49.5 MB
AAR with 4 ABIs, session-from-path), **MediaPipe Tasks Vision** (`tasks-vision:1.0.0` +
tasks-core, 22.5 MB natives, highest-level API, expects TFLite+metadata). None ship a
KMP artifact — inference stays behind expect/actual in `:core:ai` regardless.

The binding constraint turned out to be the **model**, not the runtime: the only food
model that is permissively licensed, anonymously downloadable from a stable HTTPS URL,
hash-published, and provenance-clean (R-S12) today is **MobileNetV2 Food-101 in ONNX
format** (ADR-007). LiteRT cannot execute ONNX; converting to TFLite requires a
toolchain run plus a WLO-owned release channel to host the mirrored artifact — neither
exists yet (repo has no public remote; TF wheels unavailable in the build env, Python
3.14).

## Decision

1. **Adopt ONNX Runtime Mobile 1.29.0** as the inference runtime for M4, behind the
   `PhotoAnalyzer`/`BarcodeScanner`-style ports in `:core:ports` (expect/actual; all
   inference code confined to `:core:ai`).
2. **LiteRT is the tracked end-state runtime** (smaller, Kotlin-first, zero telemetry,
   Apache-2.0, `CompiledModel.create(filePath)` fits the zoo). Migration triggers,
   any two of: (a) WLO has a public release channel to host mirrored TFLite artifacts;
   (b) an in-repo, reproducible ONNX→TFLite conversion task exists and is CI-verified;
   (c) a food model publishes natively in .tflite with equivalent license/provenance.
   Re-evaluate at first release cut.
3. **Excluded:** MediaPipe Tasks (would duplicate the native runtime; revisit only as a
   replacement layer if built-in detector/segmenter plumbing is ever wanted), the
   Play-services TFLite variant (transmits usage telemetry to Google — policy-invalid),
   ONNX Runtime ≥1.29.1 (AARs not yet on Maven Central at check date).
4. Emulator delegates: CPU EP (+XNNPACK if enabled by default in the chosen EP set).
   No perf claims recorded (WLO-0014); absolute budgets remain tracked guardrails.

## Consequences

- ~49.5 MB AAR lands in the dependency graph until the LiteRT migration; mitigate at
  release time with per-ABI APK splits / App Bundle (per-device delivery is one ABI).
- The zoo's first real artifact is the upstream ONNX file itself (URL + published
  sha256 pinned) — the R-S14 pipeline is exercised end-to-end against a real,
  production-grade artifact with no self-hosting prerequisite.
- Swapping runtimes later is contained in `:core:ai` (ports + `Analysis<T>` shapes are
  runtime-agnostic by design).
