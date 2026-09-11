# F02 — Food Logging & AI Nutrient Estimation — Functional Specification

> F02 is WLO's front door: any meal becomes a trusted, provenance-tagged
> nutrient record in seconds — photo first, user-corrected, clamped by physics.
> The correction loop is the hero flow, not an apology for the AI.

---

## Identity

| | |
|---|---|
| **Feature ID** | F02 — Food Logging & AI Nutrient Estimation |
| **Provides** | Multi-modal meal capture (photo > voice > barcode/label > text hint > search), AI nutrient estimation with per-scan confidence, an editable-before-save correction loop, Food Memory one-tap re-log cards, and the food diary that feeds the whole app. |
| **User problems solved** | Logging takes seconds, not minutes, with zero mandatory typing. • AI estimates are drafts: editable, clamped, confidence-scored, explained — never absurd. • Repeat meals become one-tap cards. • Everything works offline; cloud is a booster, never a requirement. |
| **AI consent category** | Consumes F12 toggles: `food-photo` (cloud vision for photo/label/screenshot estimation and hint re-estimation), `voice-input` (cloud speech parse). On-device recognition, STT, OCR and DB lookups need no consent and no network. |
| **Primary evidence** | `docs/research/snapcalorie.md` (published 15% error, depth portions, hints, voice), `cal-ai.md` (Food Memory, text-steerable correction, 27M-kcal anti-pattern), `foodvisor.md` (five-rung ladder, structured correction modal), `cronometer.md` (verify-then-log, ~95-nutrient bar), `waistline.md` (OFF + USDA FDC dual backend, offline cache), `loseit.md` (Say It! voice, 3.5x-faster evidence), `docs/research/synthesis.md` §1.8, §2 (items 4, 11, 12), §5. |

## 1. Purpose & Core Objectives

F02 converts eating into data with the least friction ever shipped in a calorie
tracker, and treats every AI output as a draft — never a decision. It owns the
input ladder, estimation, the correction loop, the food DB, and the diary.
Everything downstream (F07, F03, F09, F11) eats what F02 produces, so F02's
contract is: **trustworthy numbers, honestly labeled, cheap to correct.**

Core objectives, verifiable:

- A meal logs in ≤10 s median with zero mandatory text input; a Food Memory
  re-log takes one tap (≈2 s).
- 100% of AI-estimated values are editable before save; nothing auto-commits.
- No displayed estimate can exceed physically plausible bounds for its food
  class — the "27M-kcal candy bar" cannot render, even as a glitch.
- Every entry carries a confidence badge and a provenance chain
  (measured-from-label / DB-verified / AI-estimated / user-entered).
- Full function with airplane mode on; corrections persist as ground truth
  and visibly improve future estimates.

## 2. User Moments — when and how it is used

- **Cadence:** 2–6 captures a day (meals, snacks, "what was in that sauce?"),
  plus episodic deep-edits (recipe building, back-dated entries).
- **Context:** kitchen counter (hands dirty → voice), plated meal (photo),
  couch (Food Memory card), pantry (barcode), restaurant (text hint). Mild
  time pressure — any flow longer than the meal loses. Tone: numbers, never
  judgment.

**Most common flow (photo):** tap the shutter on F10 Daily Hub → frame the
plate (depth mesh shimmers on) → shutter tap → result card ~1 s later with
items, macros, confidence ring → optionally drag a slider or tap a chip →
Save; the card arcs into the day's budget ring, which animates to its new
value. Total user actions: 2 taps.

## 3. How It Works — functional mechanics

**Inputs:** camera frames + ARCore depth; microphone audio; barcode/label
image; typed or dictated text; touches (chips, sliders); context (time, F03
planned meal, Food Memory); on-device food DB cache; optional cloud models
under F12 consent.

**Processing — the input ladder, best-rung-first:**

1. **Photo (default).** The on-device recognizer (F12 model zoo) proposes
   items; ARCore depth + reference-object heuristics (plate, hand) estimate
   volume → grams. Each item snaps to the nearest local-DB entry (Open Food
   Facts + USDA FDC cache): match → macros come from the DB, only grams are
   estimated, provenance "DB-verified"; no match → full estimate, "AI-
   estimated". *Cloud option:* with `food-photo` consented, the EXIF-stripped,
   downscaled, cropped photo can go to the user's provider for a stronger pass
   on hard plates; otherwise the on-device result ships with its (lower)
   confidence.
2. **Voice.** On-device STT → on-device parser turns "two eggs, a tablespoon
   of butter, cup of oatmeal" into discrete items resolved against the DB.
   *Cloud option:* with `voice-input` consented, the transcript (default) or
   raw audio (explicit sub-choice, off by default) goes to a cloud model for
   messy speech; fallback is the on-device parse with tappable item chips.
3. **Barcode / label scan.** Barcode → OFF lookup (USDA FDC second), cached
   after first hit; offline hits served from cache. Label photo → on-device
   OCR auto-fills a new local food entry (captured once, reused forever).
   *Cloud option:* a crumpled label can be sent for OCR under `food-photo`;
   fallback is on-device OCR plus manual field taps.
4. **Text hint.** Free text on the result card ("half the plate is dal, cooked
   in ghee") re-estimates the whole plate — no word limit (SnapCalorie's
   truncation complaint is a named anti-pattern). On-device: the hint biases
   the recognizer and portions via per-user dish priors. *Cloud option:* hint
   + embedded image under `food-photo`.
5. **Search.** Fuzzy multi-keyword search over the local cache first (learned
   per-user aliases count), then network OFF/USDA. Plain lookups, not AI — no
   consent category needed, but they route through F12's network dispatcher
   and its connection log.

**Guardrails on every estimate:** outputs are clamped per food class by
energy-density rails (≤9 kcal/g fats, ≤4 cooked starches…) and an estimated-
mass × max-density ceiling; hitting a rail renders a "sanity rail" marker and
a confidence downgrade. Items carry per-item confidence; the card carries a
scan-level score.

**Day status:** every day carries a `Logged / Skipped / Fasted` marker — one tap in the day header (Zolt's missing-data fix). F02 owns the semantics (R-B1); F07 consumes the marker so unlogged days are excluded from the solve instead of being read as zero intake. **Water:** drink entries and a one-tap water quick-add land in the diary like any other entry; the water target lives in the Targets document (master §2.1), and F09 consumes the actuals for its hydration correlation.

**Outputs and artifacts:** diary entries (items, grams, kcal, macros; v1 also
fiber, sugar, sodium; micro panels fast-follow — §10), the photo per the
retention policy (R-U14: **discarded at save by default**; an opt-in
compressed-thumbnail mode feeds the diary and Food Memory; full-quality
originals are a further opt-in, with a per-capture "keep this one"),
the raw estimate + correction delta (ground-truth pair), Food Memory cards,
and per-food provenance records — consumed by F07, F11, F09, F03.

**State owned by F02:** the food diary; the local food-DB cache + sync policy;
Food Memory; per-user dish priors; the label-OCR catalog; portion calibration.

## 4. User Interaction Model

**Entry points:** shutter FAB on F10 Daily Hub (primary); quick-action tile and
widget intent (F10); "log this planned meal" from F03; "correct" on any diary
entry; notification quick-capture (photo deferred).

**Primary flows:**

- *Happy path (photo):* as in §2. Result card = item chips (tap → swap, portion
  slider, "not this"), plate-level hint field, Save/Rescan. Save is live
  immediately; editing is optional.
- *Low-confidence path:* scan confidence < 0.5 → Save still works, but an
  amber "rough guess — adjust what's wrong" strip appears and the two least
  confident chips are pre-expanded. No modal nagging. (The strip's 0.5 trigger sits mid-amber by design — a stricter bar than the ring's 0.4 color boundary.)
- *Offline path:* airplane mode → the on-device pipeline runs unchanged (an
  "offline" glyph shows); barcode misses offer on-device label OCR as fallback.

**Input minimization — never typed:** food names (recognition), quantities
(depth), macro math (clamps + DB), repeat meals (Food Memory), segmentation
while cooking (voice chips). One-tap/zero-tap: Food Memory cards, "same as
Tuesday?", copy-yesterday, long-press FAB = re-log last meal, kcal-only
quick-add for stubborn cases.

**Micro-interactions (specific):**

- Shutter: 10 ms haptic tick; the viewfinder briefly desaturates everything
  non-food (segmentation mask) — recognition made visible before it speaks.
- Analyzing: 600–900 ms shimmer as item chips spring in one by one (damping
  0.7), each landing with a soft tick — the AI "deals" the foods.
- Confidence ring: draws clockwise around the kcal number in 400 ms; green
  ≥ 0.7, amber 0.4–0.7, grey < 0.4; stroke slightly irregular at low
  confidence — precision as texture.
- Portion slider: detents every 5 g with 5 ms ticks; crossing a clamp rail
  fires a double-sided bump and the number flashes its rail glyph.
- Hint re-estimate: numbers morph with a 250 ms digit-roll; changed items re-spring.
- Save: the card folds into a 3 px chip and arcs into the diary while the F10
  budget ring sweeps to its new total with a count-up and one low "settled"
  haptic. No confetti on logging — celebration belongs to F11 milestones.
- Errors: a 2-frame 8 px horizontal shake, no red — mistakes are data. A clamp
  event shows a one-time 900 ms tooltip ("capped at a physical maximum — tap
  to override") that never repeats per food class.

**Data-quality gating:** confidence and per-item flags are mandatory metadata.
F07 refuses to fold a day into adaptive-TDEE math if its entries are >50%
uncorrected low-confidence estimates — surfaced, not hidden ("2 rough days
excluded from TDEE" in F07's explainer). Micronutrient panels stay hidden for
unverified AI-estimated items; DB-verified and label entries get full panels.

## 5. What the User Gets Out

- The food diary: day view with meal groupings, kcal/macros, photo thumbnails,
  confidence badges, provenance chips.
- Per-scan confidence (0–1 with band label) and a **personal accuracy stat** —
  rolling mean absolute error vs. the user's corrections: "your scans are
  typically ±14%, best on breakfast". The honest version of Cal AI's "80%".
- Provenance on every number: `measured (label)` / `DB-verified (USDA FDC #…)`
  / `AI-estimated (on-device, v1.4)` / `user-entered`; every chip taps open a
  "how we got here" sheet (what the model saw, what depth said, what the DB
  returned, what the user changed).
- Visualizations owned here: day/week diary timeline; per-meal macro bars;
  weekly logged-meals heatmap; a "seconds-to-log" distribution (Lose It!'s
  3.5x claim made personal); corrections-per-food chart.

## 6. Motivation & Psychology

The itch: closing the day's ring and watching the accuracy stat tick down as
corrections accumulate — the correction loop reframed from chore into
calibration play. F02 proposes to F11 (which owns the system): a "Calibrator"
badge line (1st / 50th / 500th correction), a weekly logging-efficiency stat
for report cards, and streak credit for any logged day regardless of what was
eaten. Tone rules: the app never comments on food choices ("that's a lot of
calories" is banned copy); it reports facts ("1,420 of 1,900 kcal — 540 left")
and its own uncertainty, never the user's virtue.

## 7. Relations to Other Features

- **Consumes from:** F12 — model zoo (recognizer, STT, OCR), `food-photo` /
  `voice-input` consent, cloud dispatch + receipt log; F10 — entry points,
  today context, budget-ring surface; F01 — daily targets and diet rules
  (keto carb cap on cards); F03 — planned meals ("log as planned") and
  recipes as DB entries; F13 — backup/export of diary + DB.
- **Feeds into:** F07 — intake totals with data-quality flags; F11 — statistics
  inputs and correction events; F09 — per-meal fiber/FODMAP tags; F06/F08 —
  calorie context + the portion-calibration profile for depth estimates.
- **Shared concepts:** Targets, Logs, Provenance, Confidence, Consent, Food Memory.
- **Boundary notes:** F02 owns the food diary and food DB; F10 only renders
  today's slice. Gamification logic lives in F11 — F02 emits events only.
  Exercise burns are F05/F07 territory; F02 never "eats back" exercise calories.

## 8. Blue Sky Ideas

- **[v1] Sanity rails everywhere.** Energy-density clamps, rail markers, and
  the physical-plausibility ceiling — the anti-27M-kcal guarantee, published
  in the accuracy page's methodology.
- **[v1] Food Memory auto-clustering.** Corrected meals become named cards;
  a fresh scan matching a Food Memory embedding prompts "same as Tuesday's
  lunch?" — one tap, no search.
- **[v1] Published accuracy, personally verified.** A bundled curated test set
  + methodology page (SnapCalorie's playbook), plus the personal ±% stat from
  the user's own corrections, computed fully on-device.
- **[v1.x] Visible on-device personalization loop.** Corrections tune per-user
  dish priors (usual meals, usual portions, home recipes); show it: "your
  lunch scans improved ~12% this month." The model that learns never leaves
  the phone.
- **[v1.x] Spoken-weight calibration.** "It's 340 grams on the scale" while
  the plate is framed — one sentence turns a kitchen scale into per-dish depth
  calibration, attacking portion error where it dominates.
- **[v1.x] Leftover diff.** Photograph the remains after eating; "same meal
  −20%" adjusts the entry — ends the half-eaten-plate lie everyone tells.
- **[future] Screenshot import.** Delivery-app or chat screenshots become
  entries via on-device OCR; cloud pass optional under `food-photo`.
- **[future] Menu mode.** Photograph a restaurant menu before ordering;
  per-dish pre-log estimates and a "best pick under 500 kcal" marker.
- **[future] Hands-free cook mode.** Wake-phrase voice capture ("okay WLO, two
  tablespoons of olive oil"), tick confirmation, zero screen contact.
- **[moonshot] Food X-ray grading.** Multi-frame depth + spectral cues grade
  sub-surface composition (oil absorption, sugar density) — attacking the
  category's structural blind spot (invisible ingredients) with sensors, not
  guesswork. Publish whatever accuracy we achieve, or don't ship the claim.

## 9. Guardrails, Privacy & Sensitivity

Food photos can reveal home, face, family, health status. Photos and audio
never leave the device without the matching F12 consent actively on at call
time; before any cloud call — EXIF/location stripped, images downscaled and
cropped toward the plate, transcripts preferred over audio — and every cloud
call writes an F12 receipt naming category, provider, and payload. Diary data
sits in the F13 vault; exports include it, keys never. Hard rules from the
objective: free forever — capture, correction, history, and the local DB are
never gated, quota'd, or paywalled (SnapCalorie's "3 scans/day" is a named
anti-pattern); no account; no ads; the only servers F02 talks to are Open
Food Facts, USDA FDC, and the user's own BYOK provider.

## 10. Open Questions

- **Diary ownership boundary:** F02 owning the food diary vs. a shared F10
  "day model" — needs a master-doc ruling (F02 assumes ownership).
  *(Resolved: R-B1 — F02 owns the diary and day-status semantics; F03 projects
  planned slots into it.)*
- **v1 nutrient scope:** kcal + macros + fiber/sugar/sodium in v1, full micro
  panels in v1.x — confirm the fast-follow is on the v1.x train, not v2.
  *(Resolved: R-A4 — micronutrient panels are v1.x.)*
- **ARCore floor:** the no-depth device tier and its portion fallback (reference-object heuristics vs. user-confirmed grams).
- **OFF cache budget:** full top-N products offline (~100 MB?) vs. a
  scan-history-seeded lazy cache; affects first-run prompts in F01/F12.
- **Cloud model policy:** does `food-photo` pin one model per provider, or let
  the user choose per category (F12 defaults to user choice)? And which open
  datasets/licenses are acceptable for the shipped recognizer — a project-level
  decision before model v1.
