# F12 — AI Platform: On-device Models, BYOK & Consent — Functional Specification

> F12 is WLO's AI heart and its privacy constitution: a zoo of on-device models
> that work with airplane mode on, a BYOK layer for cloud bursts, and a frozen
> six-category consent taxonomy every other feature consumes. Nothing leaves
> the device without its consent toggle on — every departure gets a receipt.

---

## Identity

| | |
|---|---|
| **Feature ID** | F12 — AI Platform: On-device Models, BYOK & Consent |
| **Provides** | The on-device model zoo (download, versioning, storage accounting), the BYOK provider layer (OpenAI-compatible, Google, Anthropic, Ollama/llama.cpp) with connection test / model listing / per-call cost estimation, the six-category consent matrix, point-of-use consent prompts, the AI receipt log, and the redaction + offline-degradation rules all features inherit. |
| **User problems solved** | Every commercial AI tracker is cloud-only; WLO's AI works offline and provably keeps data local. • Cloud AI normally means a vendor account and bundled consent; here the user decides per capability, pays with their own key, and sees exactly what was sent and what it cost. • "Trust us" privacy is unverifiable; here it is architectural and auditable. |
| **AI consent category** | **Owns all six**: `food-photo`, `voice-input`, `meal-planning`, `silhouette`, `poop-photo`, `insights-chat`. No other feature may define or rename a category; features consume them via the F12 contract (§3.1). |
| **Primary evidence** | `docs/research/openscale.md` ("no internet permission" as provable privacy), `snapcalorie.md` (published accuracy, corrections-personalization loop, cloud-only + incoherent-privacy anti-patterns), `cal-ai.md` (unpublished accuracy, absurd outputs), `cronometer.md` (verify-then-log), `waistline.md` (secret-blanking in exports, pluggable backends), `docs/research/synthesis.md` §5, `docs/objective.md`. |

## 1. Purpose & Core Objectives

F12 concentrates everything about AI that is *not* a specific feature: models,
keys, network egress, consent semantics, audit trail. A feature declares "I
need `food-photo` via provider X"; F12 answers allowed / denied / offline and
redacts, dispatches, receipts, degrades — so no feature re-invents plumbing.

Core objectives, verifiable:

- Zero egress unless the calling feature's consent category is on, a provider
  + key is configured, and the call passes redaction — all checkable in code,
  demonstrable in the receipt log.
- Every AI capability works in airplane mode; every cloud-capable one has a
  named on-device fallback.
- Consent is revocable in one tap, surfaced at the point of use, never
  Settings-only; every cloud call writes a receipt; "what left my phone this
  month?" is answerable in under 10 seconds.

## 2. User Moments — when and how it is used

- **Point of use (the critical moment):** F02 just hit a hard plate,
  `food-photo` is off → inline sheet: "Want a cloud boost for this scan?" with
  the exact payload preview. One decision, in context, fully informed. First
  run is covered by F01 onboarding; nothing cloud is configured or implied.
- **Geek ritual (weekly):** reviewing receipts and costs like a bank
  statement — "4 cloud calls this month, 38 KB, $0.11".

## 3. How It Works — functional mechanics

### 3.1 The consent taxonomy (FROZEN)

Exactly six categories. Names are API constants; features must not invent
more. Toggles govern **cloud AI only** — on-device inference is always
allowed, never gated. Revocation is immediate: in-flight calls cancelled.

| Category (constant) | Governs cloud… | Consumed by | On-device fallback |
|---|---|---|---|
| `food-photo` | food photo recognition/estimation, crumpled-label OCR, screenshot-import analysis, hint re-estimation | **F02** (F04 cloud-assist if ever shipped — R-C3) | on-device recognizer + ARCore depth + DB snap + clamps |
| `voice-input` | cloud STT or semantic parse of spoken logs | **F02** (F05/F06/F10 may consume for voice quick-logs) | on-device STT + multi-item parser |
| `meal-planning` | generative diet/meal-plan and recipe creation or refinement | **F01** (plan studio), **F03** (planner/recipes) | template + rules engine over the local DB |
| `silhouette` | cloud body-photo analysis (pose, composition) | **F08** | on-device pose model + alignment + F06 measurements |
| `poop-photo` | cloud stool-image classification | **F09** | on-device Bristol classifier + one-tap manual override |
| `insights-chat` | chat/Q&A over personal data, insight narratives, coach copy | **F11** (insights & chat), **F10** (nudge copy, opt-in — default is local templates) | rule-based local insights + embedded local LLM if installed |

Future consumers already sanctioned by existing categories (no taxonomy
change; the table above lists v1 traffic): F03 photo-to-recipe → `food-photo`
[future] · F03 voice planning → `voice-input` [v1.x] · F05 natural-language
program discussion → `insights-chat` [future].

Plain DB lookups (OFF/USDA) are *not* AI and need no category, but all egress
flows through F12's dispatcher and connection log. A capability that fits no
category is a master-doc decision, not a feature-level new toggle.

### 3.2 On-device model zoo

| Model | Serves | Size | Distribution |
|---|---|---|---|
| Food recognizer (detector + class head) | F02 | 20–40 MB | bundled |
| Portion/depth helper (ARCore) + label OCR | F02 | 15–25 MB | bundled |
| Speech-to-text (streaming) + intent parser | F02 + voice quick-logs | 45–85 MB | optional download |
| Poop classifier (Bristol 1–7) | F09 | 8–12 MB | bundled |
| Pose guidance (silhouette alignment) | F08 | 8 MB | bundled |

Every model has a tappable **model card**: purpose, published benchmark +
methodology, license, version, hash, and training data (open-licensed
datasets only, each named — R-S12). Downloads are hash-pinned, fetched from
pinned public URLs (e.g. Hugging Face) — WLO runs no backend. The manager
shows per-model/total storage with one-tap reclaim, plus version eval deltas.

### 3.3 BYOK provider layer

- **Providers:** any OpenAI-compatible endpoint (custom base URL covers
  OpenRouter, LiteLLM, self-hosted gateways), Google (Gemini), Anthropic
  (Claude), and local runtimes — Ollama or llama.cpp on-device or on the
  user's network. Local runtimes need no consent category (nothing leaves
  the device) but still get receipts (cost $0, marked `local`).
- **Setup:** pick provider → paste key / endpoint URL → **connection test**
  (one live call, round-trip ms with a pulse animation) → **model listing**
  (live, searchable) → default model per category.
- **Cost estimation (geek candy):** editable per-model price table (importable
  as community JSON); a cost band before each cloud call ("≈ $0.004"); a
  dashboard aggregating actuals per category per month.
- **Key hygiene:** keys live in Keystore-encrypted storage, blanked from every
  export, never logged, wiped by "revoke everything".

### 3.4 Consent matrix UI

One screen — "AI Studio": six rows (one per category), each with a status
glyph (on-device / cloud: provider+model / off), model-card link, toggle,
provider and model pickers, a "preview payload" action, and a receipts
filter. A global kill switch ("Cloud: OFF — nothing can leave the device")
sits above the rows: one tap, no maze, never disabling on-device features.
Enabling cloud requires a configured provider; the sheet routes to setup
inline if none exists.

### 3.5 Point-of-use consent prompts

When a feature wants cloud and consent is off, F12 presents an in-context
sheet, never a Settings redirect: what the feature wants, **the exact payload
preview** (the actual downscaled image or transcript, with byte count and
estimated cost), and three buttons — "just this once" / "always for
<category>" / "keep it on-device" (default-highlighted). "Just this once"
expires after 10 minutes; prompts fire at most once per feature per day,
never interrupting a flow with a working on-device result; "never ask"
silences them permanently. The decision happens where the data is, informed
by what will actually be sent.

### 3.6 The AI receipt log

Append-only ledger, one entry per AI call (on-device calls included, marked
`local`): timestamp, calling feature + category, provider + model, payload
summary (type, dimensions, redactions, byte count), cost, duration, outcome.
Entries are **hash-chained** (each embeds the previous hash) — tamper-evident:
a nerd trust gimmick that is also a real audit guarantee. Filterable by
category, exportable via F13, with a monthly summary — "38 KB left your phone
this month. Here is every byte's paperwork."

### 3.7 Redaction, minimality & offline degradation

Payload ladder — always send the least sensitive sufficient payload: derived
text (transcript) > downscaled cropped image > raw media. Audio is never sent
unless the user explicitly opts into cloud STT for `voice-input`. Before any
cloud call: EXIF/GPS/filename stripping, downscale to model needs (≤1024 px),
crop toward the subject, no user identifiers or diary content beyond the
declared minimum; poop photos add a send-the-mask-instead
option (silhouette has no cloud path in v1 at all — R-U16 keeps body pixels
memory-only). Airplane mode (or no provider) with consent on behaves exactly like
consent off: each category falls back per §3.1, results badged with degraded
provenance ("on-device estimate", lower confidence in F02; template plan in
F03); "re-check this scan when you're back online" queues re-analysis, gated
by consent re-checked at send time.

### 3.8 Provable-privacy posture (and how BYOK coexists)

openScale's "no internet permission" is the gold standard because it is
architectural, not a promise. WLO needs network (BYOK, OFF/USDA), so it copies
the structure, not the letter: **all** egress funnels through one audited
`NetworkDispatcher` that refuses any call without an active consent grant or a
DB-lookup allowance; no telemetry/analytics code exists in the tree; a debug
build renders a live egress monitor; and a separate F-Droid **"WLO Pure"
flavor** ships with the INTERNET permission stripped (BYOK and online DB off,
all else identical). Provable-ness = open code + single choke point + receipt
ledger + optional no-network build.

## 4. User Interaction Model

**Entry points:** AI Studio (Settings → AI); point-of-use sheets from any
consuming feature; model-manager deep links; a receipt-log shortcut from every
feature's provenance sheet.

**Flows:** setup and consent as above; *provider error path* — 401/429/quota:
toast with the provider's words, auto-fallback on-device, the receipt marks
the failure, the AI Studio row shows amber; *revocation path* — toggle off
mid-anything: in-flight calls cancelled, the feature continues on-device.

**Micro-interactions (specific):** consent toggle = a shield visibly closes
with a 250 ms spring and a firm double-tick haptic (closed = local-only);
connection test = sonar pulse ring with round-trip ms counting up; model
download = segmented progress ring with MB counter and a completion "click";
receipt log = ledger lines slide from a "device" glyph to a "cloud" glyph,
filing like rolodex cards (180 ms each, subtle tick); kill switch = all six
status glyphs flip to "local" in a staggered 60 ms wave. Setup is paste-key +
two taps; consent is one toggle; nothing requires typing beyond the key/URL.

**Data-quality gating:** cloud-assisted results are never silently mixed with
on-device ones — provenance badges name the path (F02's ring color differs for
DB-verified / AI-on-device / AI-cloud), and a fallback never silently inherits
a model's published benchmark.

## 5. What the User Gets Out

- The AI Studio: per-category status, model inventory, provider setup, cost dashboard, receipt log.
- A published **accuracy page** (in-app + repo) listing every model's
  benchmark and methodology — the SnapCalorie credibility play, made local.
- Provenance everywhere: any AI-derived number links to its model card, the
  consent state used, and the producing call's receipt. Charts owned here:
  cost-over-time bars by category; egress-bytes timeline; consent history.

## 6. Motivation & Psychology

The itch is *control and audit*: geeks check spend, egress, and model versions
the way they check commit history, and every payload preview reinforces that
the app is on their side against the cloud. F12 proposes to F11 (which owns
the system) a "Ghost Protocol" recognition for fully-local weeks and
"Calibrator × Ghost" combos. Tone: proud, never scaremongering.

## 7. Relations to Other Features

- **Consumes from:** nothing above it — F12 is infrastructure. It uses Android
  Keystore for key storage and F13 for receipt export (keys blanked).
- **Feeds into:** F02 (`food-photo`, `voice-input`, recognizer/STT/OCR
  models); F01 + F03 (`meal-planning`); F08 (`silhouette`, pose model); F09
  (`poop-photo`, classifier); F11 + F10 (`insights-chat`, local templates).
  F13 exports receipts and enforces key-blanking; F01 runs first-run setup.
- **Boundary notes:** features own their UX and on-device logic; F12 owns
  models, keys, egress, consent semantics, receipts — no feature may open its
  own socket for AI. Shared concepts (Consent, Provenance, Receipt, Payload)
  are defined here; category disputes go to the master doc (§10).

## 8. Blue Sky Ideas

- **[v1] Payload-preview dry run.** "Show me exactly what would be sent" — the
  real bytes, rendered — from both the consent sheet and AI Studio.
- **[v1.x] Embedded local LLM runtime (llama.cpp).** A downloadable quantized
  model enables `insights-chat` and smarter hint parsing fully offline; the
  model card shows tokens/s on this device. The move cloud apps cannot follow.
- **[v1.x] Consent schedules, provider routing, exportable "my model"
  adapters.** "Cloud only on Wi-Fi", time-boxed grants ("next hour");
  per-category model tiering ("cheap model for drafts, frontier model on
  'deep check'") with live cost deltas; F02's correction-derived priors
  become explicit, portable personalization artifacts — never uploaded.
- **[future] Egress attestation widget + community model channel.** "0 bytes
  left your phone in 34 days" on the home screen; signed third-party model
  cards (regional food recognizers, better poop classifiers) from pinned URLs
  with published evals — an ecosystem without a WLO backend.
- **[moonshot] Verifiable local-only attestation.** Reproducible builds + the
  Pure flavor + a public canary script proving a WLO install makes no
  unexpected connections — openScale's guarantee, proven continuously and socially.

## 9. Guardrails, Privacy & Sensitivity

F12 is the enforcement point for the app's hardest promises. Never: telemetry,
analytics, or any code path contacting a WLO-owned server (none exist); never
pre-checked consents, consent bundling ("enable all"), guilt copy, nag loops,
or settings-only consent. Keys: Keystore-encrypted, never logged, never
exported, wipeable in one action. Receipts: summaries only, never raw
payloads; the log is local data under F13's vault rules. "Delete receipts" and
"wipe keys" are separate, explicit, undo-protected actions.

## 10. Open Questions

- **Category boundaries for the master doc:** F05 exercise-plan generation has
  no natural category (fold under `insights-chat`, or add a seventh constant?);
  F10 nudge *generation* (vs. selection) under `insights-chat`; cloud-STT
  raw-audio sub-toggle inside `voice-input`.
  *(All resolved: R-C1 — `exercise-plan` pre-approved as the seventh if and
  when F05 cloud generation ships; F10 nudge copy sits under `insights-chat`
  per the frozen matrix; R-C6 — raw-audio cloud STT is a default-off
  sub-toggle inside `voice-input`.)*
- **Model distribution & local LLM stance:** bundle-everything (bigger APK)
  vs. download-on-first-use for STT; llama.cpp in-process vs. an Ollama-style
  local server (4 GB devices may force server-only).
