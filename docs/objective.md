# WLO — Objective

## Project

WLO ("wloss") is an **Android weight-loss companion app**, **fully local-first**: all
user private data lives on the device. No cloud account, no server. Remote AI is
opt-in per AI capability, with a user-provided API key and maximum provider
compatibility.

## License & sustainability model

- **Open source and completely free, forever.** No paywall, no ads, no
  subscription, no "pro" tier — no feature is ever gated behind payment.
- **No SaaS, by design.** The project is unpaid, so it cannot fund servers:
  there is no WLO backend. Everything runs on device; any cloud AI is accessed
  **BYOK** (bring your own key), directly from the app to the provider the
  user chose.

## Target user

A **geek motivated by numbers**. The app treats self-quantification as a game:
rich statistics, data visualizations, trends, forecasting, and tasteful
gamification. Design must be **beautiful, minimalist, and dense with rich
micro-interactions** — polish is a feature, not a garnish.

## Core feature set (v1 scope as described by the product owner)

1. **Multi-diet plan via templates** — pick/adapt diet templates (e.g. calorie
   deficit, keto, mediterranean, high-protein…), with per-diet rules.
2. **Meal planning & tracking** — nutrient estimation (calories, macros,
   micros), automatic shopping list generation from the plan.
3. **Exercise planning & tracking** — workouts, sessions, progression.
4. **Weight & measurement tracking** — metrics (weight, body circumference,
   body-fat %, …), plus **forecasting** (goal-date projection, trend lines).
5. **Silhouette tracker** — periodic body photos, aligned/side-by-side
   comparison over time, all stored locally.
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
  on.

## Current phase

Phase 0 — **Design & research**. Competitive analysis of 10–20 diet/food/
shopping/fitness/health tracker apps lives in `docs/research/`, with a
synthesis mapping their features onto: (a) already planned, (b) good fit to
add, (c) plan for a future version, (d) redundant/skip.
