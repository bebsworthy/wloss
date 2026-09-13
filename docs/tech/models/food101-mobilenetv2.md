# Model card — `mobilenet_v2_food101` (zoo id `food-classifier/1`)

R-S12 requires every zoo model to ship a user-visible card naming its license
and training data; this is that card for the F12 zoo's first entry (ADR-007).
Facts re-verified 2026-09-12 against the upstream repo (Hugging Face
`AlexKoff88/mobilenet_v2_food101`, commit 7d05d38).

| Field | Value |
|---|---|
| Name | `mobilenet_v2_food101` |
| Zoo id | `food-classifier/1` |
| Publisher | Alexander Koff (GitHub `AlexKoff88`), Hugging Face |
| Task | Single-label food-photo classification → top-5 suggestions with confidences |
| Architecture | MobileNetV2 (torchvision), ImageNet-initialized, fine-tuned |
| Format | ONNX fp32, single input `input` (1×3×224×224 float32 NCHW), output `output1` (101 logits) |
| Size | 9,835,830 B (9.4 MB) |
| sha256 | `af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890` (published upstream; verified before every install) |
| URL | `https://huggingface.co/AlexKoff88/mobilenet_v2_food101/resolve/main/mobilenet_v2_food101.onnx` (stable resolve URL, anonymous HTTPS) |
| License | Apache-2.0 (model weights and card) |
| Training data | **Food-101** (ETH Zurich — 101,000 images, 750 train / 250 test per class; in research use; classes and meta published with the dataset) + **ImageNet** initialization via torchvision's pretrained MobileNetV2 (1k-class pretraining, then 30-epoch Food-101 fine-tune). Both datasets are named here per R-S12. |
| Published benchmark | Top-1 76.3 % on the Food-101 test split (upstream README); top-5 not published by upstream |

## Preprocessing (pinned — the runtime MUST match training)

torchvision eval transforms: resize short side to 256 (aspect preserved) →
center crop 224×224 → scale to [0,1] → ImageNet normalize
(mean 0.485 / 0.456 / 0.406, std 0.229 / 0.224 / 0.225) → float32 NCHW.
Implemented in `:core:ai` `OnnxPhotoAnalyzer` (androidMain); the transform math
is exercised by instrumented tests against the real artifact.

## Class list (101, pinned in-repo)

`Food101Catalog` (:core:ai, commonMain) pins all 101 labels in TRAINING index
order — Python `sorted()` (codepoint) over the canonical Food-101 class names,
matching torchvision `datasets.Food101` (`classes = sorted(metadata.keys())`).
Note the codepoint trap: `cheese_plate` sorts BEFORE `cheesecake`. CI asserts
count = 101 and sort order. The list is fixed at adoption (ADR-007); changing
models re-runs the ADR because it changes provenance and behavior users saw.

## How WLO uses it (and is honest about it)

Output logits → softmax → top-5 `FoodSuggestion`s (label, confidence, class
priors). Below the analyzer hold threshold (0.35 top-1 — OWNER FLAG, M4
default) the scan ships `held` and the UI says "rough guess — adjust what's
wrong"; nothing ever auto-saves (F02 §1; R-U15). Class labels are snapped to
the local food DB when possible; otherwise the class's coarse per-100 g
energy prior feeds the estimate, which the F02 sanity rails
(:core:engines `FoodRails`) clamp per class (≤9 kcal/g fats, ≤4 cooked
starches…) and by the mass × density ceiling. Every save records provenance:
model id, confidence, consent state (on-device inference needs none), held
flag (F02 §5; the diary provenance row names the model).

## Limitations (user-visible on the model card)

- **Western-dish skew.** Food-101 is 101 mostly-Western dish classes; real
  plates (mixed dishes, non-Western cuisines, drinks, plain ingredients) fall
  outside the label space. WLO does not hide this: out-of-distribution plates
  show low confidence → held → the manual twin is one tap away.
- **Single-label, no detection.** The model sees ONE dominant class per photo;
  multi-item plates are approximated by the top-k chips the user edits, not
  by bounding boxes. (ADR-007 names a WLO-trained detector as future work —
  no permissive on-device food detector with a stable URL exists as of
  2026-09-12.)
- **76.3 % top-1 on its own benchmark** — every fifth test image is wrong even
  in-distribution. The correction loop, the accuracy page's published ±%, and
  the correction cache (R-B6) are the compensating design, not an apology.
- **Volume is not measured.** Portions come from class priors and user
  correction; the model itself cannot weigh a plate.

## Runtime context

Executed fully on-device by ONNX Runtime Mobile 1.29.0 (ADR-006; audit card
`docs/tech/audit/onnxruntime.md`). Download-on-first-use only (R-S14): the APK
ships no model; the zoo manager fetches from the pinned URL, verifies the
sha256 before install, discloses size before download, and supports one-tap
reclaim. Frames are memory-only (R-U14 pipeline): EXIF-free, ≤1024 px, no
persistence by default — the classifier receives 224 px pixels and nothing
else ever leaves the device.
