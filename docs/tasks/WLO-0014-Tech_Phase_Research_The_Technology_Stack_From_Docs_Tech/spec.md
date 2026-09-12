# Tech-stack research phase

Ground zero: **docs/tech/DECISION-SPACE.md** — the full inventory of decisions (T-A…T-K),
the already-frozen choices (§2), non-negotiable constraints (§1), owner questions (§5),
and the research order (§6).

## Scope
Research every open decision T-A1…T-K6, produce one-page ADRs in `docs/tech/adr/`, then
ratify as R-T* rulings in FEATURES.md §3. Design continues in parallel (WLO-0013 /
design tree) — this phase does not touch docs/design.

## Research order (§6)
- **Phase A — foundation forks:** T-A2 (KMP posture) → T-A3 (minSdk) → T-C1/T-D1/T-H1
  (persistence, serialization, HTTP).
- **Phase B — AI/capture stack:** T-E1 runtime matrix → T-E2…E5, E7, E8 per-capability
  models (license/size/benchmark) → T-E6 local LLM. Output includes a min-spec spike
  (tokens/s, inference latency).
- **Phase C — security & documents:** T-F1–F4, T-G1, T-C3 vault design.
- **Phase D — quality & release:** T-J testing/perf tooling, T-K build/CI/flavors/
  reproducible builds, crash policy (Q6).

## Hard filters for every candidate (§1)
GPLv3 · F-Droid-clean ("WLO Pure" flavor, no Play-Services-only SDKs) · no backend ·
airplane-mode parity · single NetworkDispatcher egress · provenance chips · R-U14/U16
photo discipline · model cards with named open training data.

## Acceptance criteria
1. Every T-* decision has an ADR with options, evidence, choice, consequences.
2. Owner questions below answered (or defaulted with explicit rationale).
3. R-T* rulings merged into FEATURES.md §3.
4. Min-spec device spike numbers recorded for the model zoo.


## Amendment 2026-09-11 — owner policy reframe (R-S1 amended, R-S13 added)
- F-Droid: **not required** → Play Store + GitHub APKs (Q1/Q2 resolved).
- License: **Apache-2.0** (was GPLv3). openScale GPL scale drivers → clean-room reimpl.
- Proprietary SDKs allowed when free + materially better: on-device ⇒ audit card only;
  transmitting ⇒ explicit consent toggle + receipts via NetworkDispatcher.
- "WLO Pure" no-INTERNET flavor: **dropped**.
- Hard line unchanged: no data leaves the device without explicit consent.
- Proprietary SDKs now default candidates: ML Kit Barcode/Text (T-E8/T-E4);
  Gemini Nano/AICore joins the local-LLM tier (T-E6); iText/AGPL out of PDF (T-G1).


## Owner decisions 2026-09-11 (Q3–Q6, approved suggestions)
- **minSdk = API 29 (Android 10)** (q-000023). Haptics degrade gracefully below 30/34 per DESIGN-SYSTEM §5.
- **Model zoo: download-on-first-use only; the APK ships no zoo models** (q-000024) → R-S14.
  Airplane-mode parity holds once a model is present; capabilities degrade to R-U15 manual
  paths until then. Vendor-SDK built-in models (ML Kit libs) are a tracked exception pending
  the Phase-B size audit.
- **KMP-ready core, Android UI** (q-000025): engines + data layer in common Kotlin
  Multiplatform; UI stays Android-first Compose. Flips T-A2; persistence/HTTP/DI research
  follows the KMP fork (ADR-001/003/004).
- **Crash policy: opt-in content-free ACRA reports** (q-000026). Transmitting ⇒ consent
  toggle + receipts + audit card; endpoint selection and content-freeing are Phase-D research.


## Architecture doc — 2026-09-11
- **docs/tech/ARCHITECTURE.md** written: (§1) ratified stack summary + product-engineering
  invariants table ("constitution" with ruling refs); (§2) proposed code organization —
  layered module graph (commonMain core / Android restricted impls / features / app),
  enforced dependency rules D1–D8 (ports-and-adapters DIP: features cannot see
  :core:network/:core:ai/:core:media/:core:vault; only :app binds), DerivedValue
  provenance type, DRY single-owner anchors, testing architecture; (§3) open items.
- §2 to be ratified as **ADR-005 (module graph & dependency rules)** at implementation
  kickoff. Placeholder app id: `app.wlo` (confirm before release).


## Research model revised — 2026-09-12 (owner call: just-in-time)
Up-front Phases B/C/D are dropped. Research is pulled into the feature epic that needs
it ("spike → ADR → build"); DECISION-SPACE §6 stays the checklist, ADRs stay the
ratification path. Feature order drives research order (suggested: skeleton → F01 → F02).
Three exceptions must land before the first on-device model ships (likely with F02):
T-E1 inference runtime (one shared decision), R-S14 zoo delivery plumbing (shared infra,
built once), and procuring the min-spec Android 10 test device. For AI-as-the-promise
features (F02, F08), the spike runs before their detailed UI locks — still JIT, sequenced
inside the epic.


## Testing constraint — 2026-09-12 (owner): emulator-only for now
All development and testing happens on the Android emulator (min-spec AVD: API 29 + one
current-image AVD). Consequences recorded in DECISION-SPACE §6/T-K6:
- Emulator covers functional/UI/screenshot/instrumented tests (Roborazzi is JVM anyway).
- ML/LLM spike numbers on emulator are FUNCTIONAL verification only — no NPU/DSP
  delegates, host-class CPU/GPU. Absolute budgets (<2 s silhouette, tokens/s, 60 fps)
  become tracked guardrails with a "validate on first real device" gate before release.
- Untestable by nature until hardware exists: Bluetooth scales/HR straps, ARCore depth
  (→ the no-depth fallback tier gets built early), Gemini Nano tier (llama.cpp path is
  the verifiable one).
