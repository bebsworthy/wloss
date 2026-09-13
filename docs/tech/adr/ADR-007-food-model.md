# ADR-007: Food-photo model (decides T-E2)

**Status:** Accepted (WLO-0024 milestone-4 spike, 2026-09-12; all URLs/hashes/licenses
re-verified 2026-09-12 — see WLO-0026 research record)

## Context

R-S12 requires every model to ship with named license + training-data provenance;
R-S14 requires download-on-first-use from hash-pinned URLs (no models in the APK);
R-U15 makes user correction before save the safety net, so top-k suggestion quality is
acceptable to start. Requirements: anonymous stable HTTPS download, published or
computable sha256, permissive license (no GPL/AGPL, no non-commercial), ≤ ~30 MB,
class coverage of common meals; detection (bounding boxes) is a plus, not a must.

Verified shortlist: MobileNetV2 Food-101 (AlexKoff88, ONNX, 9.4 MB, sha256 published,
Apache-2.0, Food-101 + ImageNet-init provenance, top-1 76.3%); Google AIY Dish
Classifier food_V1 (2,023 dishes, ~4.6 MB, fetch auth-gated post-TFHub-sunset, license
+ dataset unverifiable anonymously); maia2000 food/non-food binary gate (.tflite,
published hashes, Apache-2.0, unnamed dataset); larger Swin-Food101 (rejected >30 MB);
8-class EfficientNet (rejected: coarse + unnamed dataset). All YOLO-family food
detectors are ultralytics AGPL-3.0 weights — ineligible. **No permissive on-device
food detector with a stable URL exists (checked 2026-09-12).**

## Decision

1. **Primary: `mobilenet_v2_food101`** (Hugging Face `AlexKoff88/mobilenet_v2_food101`,
   ONNX fp32, 9,835,830 B, sha256 `af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890`).
   Zoo entry points at the **upstream URL directly** (stable resolve URL, published
   hash — no mirror prerequisite). Preprocessing: torchvision eval transforms — resize
   256 short side, center-crop 224, ImageNet mean/std normalization; output = 101
   Food-101 logits → top-5 mapped to food-item suggestions with confidences.
2. **Confidence → honesty mapping (R-U15/F02 §3):** suggestions render as editable
   per-item chips; below the analyzer's low-confidence threshold the result is
   `held` (DataQuality) and the UI says so; nothing auto-saves. Model card + class
   list ship in-repo (`docs/tech/models/`).
3. **Fallbacks, in order:** (a) Google AIY Dish Classifier food_V1 — only after a
   one-time authenticated fetch to a WLO mirror, license re-verification on the Kaggle
   card, and disclosure of its unnamed training set on the R-S12 card (broader 2,023
   dish coverage); (b) manual path (always present, equal status).
4. **Future work, not blocking:** a WLO-trained food **detector** (bbox per item)
   trained on Nutrition5k/UEC-FOOD provenance under Apache-2.0 (fills the detection
   gap the ecosystem leaves); the maia2000 binary gate as an optional pre-filter;
   LiteRT-mirrored TFLite conversion once ADR-006's migration triggers fire.
5. **Embeddings / learned aliases stay deferred** (T-C5, F02 §8): correction cache
   (R-B6 mechanism) lands in M4 as structured data only.

## Consequences

- Food-101 is Western-dish-skewed and 76.3% top-1 on its own benchmark — real-world
  plated meals will misfire; this is accepted because the loop corrects before save
  and every correction is stored as prior signal (F02 §3).
- The zoo's first entry is a real upstream artifact: AC "download once, hash-verify,
  size-disclose, reclaim, airplane-mode-after" is exercisable without any WLO hosting.
- Class list (Food-101) is fixed at adoption; changing models re-runs ADR-007 review
  because it changes provenance + behavior users saw ( Transparent-engine principle
  applies to AI: the model card is user-visible).
