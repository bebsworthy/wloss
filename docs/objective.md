# WLO — Objective

## Project

WLO ("wloss") is an **Android weight-management companion app**, **fully local-first**: all
user private data lives on the device. No cloud account, no server. Remote AI is
opt-in per AI capability, with a user-provided API key and maximum provider
compatibility.

## License & sustainability model

- **Open source and completely free, forever.** No paywall, no ads, no
  subscription, no "pro" tier — no feature is ever gated behind payment.
- **License: Apache-2.0** *(owner decision 2026-09-11, amending the original
  GPLv3 plan; see FEATURES.md R-S1/R-S13)*. Open-source code, permissive
  license: the app may bundle free proprietary **on-device** SDKs where they
  are materially better than the open alternative (e.g. ML Kit barcode/OCR).
- **Data leaves the device only with explicit consent — the one hard line.**
  On-device processing (proprietary or not) transmits nothing and needs no
  consent; anything that transmits (BYOK AI, food-DB lookups, any opt-in
  cloud service) goes through F12's consent gate and receipt ledger. Default:
  nothing is shared.
- **No SaaS, by design.** The project is unpaid, so it cannot fund servers:
  there is no WLO backend. Everything runs on device; any cloud AI is accessed
  **BYOK** (bring your own key), directly from the app to the provider the
  user chose.

## Target user

A **geek motivated by numbers**. The app treats self-quantification as a game:
rich statistics, data visualizations, trends, forecasting, and tasteful
gamification. Design must be **beautiful, minimalist, and dense with rich
micro-interactions** — polish is a feature, not a garnish.

## Launch promise and first release

WLO launches first as the most trustworthy weight tracker on Android: raw
measurements remain inspectable, the weight trend is explainable, and goal
progress and forecast ranges never pretend to know more than the data supports.
Loss, maintenance, and gain are first-class goal modes. Weight is the default
and primary app destination; broader planning and health features grow around
that complete raw weight → trend → goal loop.

First run asks only for the body-mass unit, an optional goal, and a first
measurement or import. Diet planning is an optional follow-on, not a gate to
using the weight tracker. The authoritative release boundary and capability
matrix are in `docs/features/FEATURES.md` §2.0.

## Broader product feature set

1. **Multi-diet plan via templates** — pick/adapt diet templates (e.g. calorie
   deficit, keto, mediterranean, high-protein…), with per-diet rules.
2. **Meal planning & tracking** — nutrient estimation (calories, macros,
   micros), automatic shopping list generation from the plan.
3. **Exercise planning & tracking** — workouts, sessions, progression.
4. **Weight & measurement tracking** — metrics (weight, body circumference,
   body-fat %, …), plus **forecasting** (goal-date projection, trend lines).
5. **Silhouette tracker** — periodic in-memory camera capture converted to
   vector outlines for aligned comparison. Body photographs are never stored.
6. **Poop tracker** — bowel-movement logging (e.g. Bristol scale) correlated
   with diet.
7. **More to be discovered via competitive research** — see `docs/research/`.

## Product principles

- **Local AI whenever possible** — on-device models for photo recognition
  (food, portion estimation), classification (poop scale), and assistive text.
- **Remote AI only with per-capability user authorization** — each AI feature
  category has its own independent opt-in (e.g. meal planning, food-photo
  recognition, silhouette analysis, poop-photo classification, insights/chat).
  A user may allow a cloud provider for meal planning while silhouette
  analysis stays strictly on-device. Consent is granular, revocable anytime,
  and surfaced at the point of use; on-device is the default, remote is always
  the exception. When remote is allowed, the user pastes their own API key
  (BYOK); provider-agnostic adapter (OpenAI-compatible, Google, Anthropic,
  local runtimes like Ollama/llama.cpp, etc.).
- **Minimize user input** — photo + recognition over manual entry, everywhere.
  Every manual field is a design failure to be reduced.
- **Privacy by architecture** — local-only storage; exports are explicit;
  nothing leaves the device without the matching per-capability consent being
  on. Android system cloud backup and implicit device-to-device transfer are
  disabled; portability uses only a user-selected SAF export/backup location.

## Current phase

Implementation is in progress. Competitive and product research remains in
`docs/research/`; the weight-first release contract in `FEATURES.md` governs
sequencing when older research or prototypes describe a broader v1.
