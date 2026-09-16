# WLO — Technology Decision Space

**Status: living inventory.** The foundation stack is **researched, decided, and verified**
(ADR-001…004, `docs/tech/adr/`); proposed code organization in ARCHITECTURE.md (ADR-005
pending). Research for everything else is **just-in-time** — pulled into the feature epic
that needs it (§6, revised 2026-09-12 per owner call). Companion reading:
`docs/design/DESIGN-SYSTEM.md` (which already names Compose),
`docs/features/F12-ai-platform.md` (AI architecture), `docs/features/F13-data-vault.md`
(data spine).

> **Owner policy reframe, 2026-09-11 (R-S1 amended, R-S13 added):** F-Droid is not a
> requirement; the license is **Apache-2.0**; free proprietary SDKs are acceptable when
> materially better (on-device, or consent-gated if they transmit); the "WLO Pure"
> no-INTERNET flavor is dropped. The hard line is unchanged: **no data leaves the device
> without explicit consent.** This doc's §1, §3 T-A4/T-E4/T-E6/T-E8/T-K2/T-K4 and §4
> reflect that; where spec text elsewhere still says "F-Droid/Pure/telemetry-never",
> R-S13 governs.

Sources swept: `objective.md`, `FEATURES.md` (+ Appendix A), all 13 feature specs,
`DESIGN-SYSTEM.md`, `IA.md`, `KICKOFF.md`, `design/research/synthesis.md`, plus a repo-wide
keyword grep for pre-existing tech statements.

---

## 1. Non-negotiable inputs (already frozen — these *are* the filter for every choice below)

| # | Constraint | Source |
|---|---|---|
| C1 | **Apache-2.0 license** *(owner amendment 2026-09-11, R-S1/R-S13 — originally GPLv3)*; open-source code, permissive license, proprietary on-device SDKs permitted under R-S13 | FEATURES.md §3 |
| C2 | **No backend, no account**; data egress only with explicit consent (BYOK AI calls + OFF/USDA lookups are the consented defaults); telemetry/analytics only ever behind an explicit consent toggle | F13 §9; F12 §3.8; R-C4; R-S13 |
| C3 | **Single `NetworkDispatcher` choke point**; feature code cannot reach a socket without a consented capability | FEATURES §2.1; F12 §3.8 |
| C4 | **Airplane-mode parity**: every AI capability works offline; every cloud one has a named on-device fallback | F12 §1, §3.1 |
| C5 | **Every third-party SDK gets a data-flow audit card** (what it collects, when, where it goes); on-device-only SDKs need no consent, transmitting ones are consent-gated *(the "WLO Pure" no-INTERNET flavor was dropped 2026-09-11)* | R-S13; F12 §3.8 |
| C6 | Provenance chip on every derived number; weak data `held`, never guessed | FEATURES §2.1 |
| C7 | R-U16: silhouette = vector outlines only, camera frames memory-only; R-U14: photos default discard-at-save | FEATURES §3 |
| C8 | Model zoo: open-licensed training data only, named on model cards (R-S12); hash-pinned downloads from public URLs (HF) — no WLO backend | FEATURES §3; F12 §3.2 |
| C9 | Interaction bar: ~80 spec-verbatim animations, 60 fps as an engineering value, custom Canvas objects, haptics that survive reduced-motion | DESIGN-SYSTEM §4–6 |
| C10 | Per-profile partition-ready schema (`profileId` on every row, R-B9); event-level storage (R-B8) | FEATURES §3 |

**Consequence worth stating loudly (post-reframe):** the binding filter is no
longer "F-Droid-clean" but **consent-clean**: an SDK or service is eligible iff
it is free, materially better than the open alternative, and either
(a) provably on-device (data never transits — no consent needed, still
audited) or (b) transmitting behind an explicit consent toggle + receipts.
GPLv3-only components (e.g. openScale's scale drivers) are now *ineligible*
instead. Play Store and GitHub APKs are the distribution channels.

---

## 2. Already decided (do not re-litigate; go read the citation)

| Area | Decision | Citation |
|---|---|---|
| UI toolkit | **Jetpack Compose** ("Implementation is Compose") | DESIGN-SYSTEM.md header; KICKOFF.md |
| Charts | **Vico (Apache-2.0)** standard line/bar; **custom Compose Canvas** for forecast cone / progress ribbon / rings / odometer; YCharts rejected as dormant (R-D4) | FEATURES §3; DESIGN-SYSTEM §6 |
| Heatmaps | **kizitonwose Calendar (MIT, HeatMap)** or ~150-line Canvas grid | DESIGN-SYSTEM §6 |
| Widgets | **Glance**, one stack owned by F10 (R-U12); no Canvas in Glance — sparklines pre-render to bitmaps | IA §4 |
| Background | **WorkManager** (auto-backup writes, widget refresh ≥15 min floor) | F13 §3; IA §4 |
| Storage | **Room/SQLite relational core**; EAV sidecar; plain-SQLite export mode [future] | F13 §3, §8 |
| License | **Apache-2.0** *(amended 2026-09-11, R-S1/R-S13 — originally GPLv3)*; Inter font OFL-1.1 (R-D3) | FEATURES §3 |
| Type | **Inter**, single family, tabular figures mandatory | R-D3 |
| Local LLM runtime | **llama.cpp / Ollama-class** runtimes named (in-process vs server is open — T-E6) | objective.md; F12 §3.3, §10 |
| Depth | **ARCore** for portion/depth helper, with a no-depth fallback tier (open) | F02 §3, §10 |
| Pose | "MediaPipe/ML Kit-**class** model" — a model class, not a committed library | F08 §3 |
| BYOK providers | OpenAI-compatible endpoint / Gemini / Anthropic / Ollama-llama.cpp local | F12 §3.3 |
| Key storage | **Android Keystore** for BYOK keys; BiometricPrompt + PIN fallback | F12 §3.3; F13 §3 |
| Backups | **AES-GCM**, passphrase-derived, encrypted by default (R-U5); SAF folder; rotation 7 | F13 §3, §8 |
| Vault partitions | AES at rest, opaque filenames, FLAG_SECURE screens, MediaStore exclusion | F08 §9; F09 §9; F13 §3 |
| Food data | **Open Food Facts + USDA FDC**, the only non-AI network services (R-C4) | F02 §3; F13 §9 |
| Models hosting | Hash-pinned public URLs (Hugging Face) | F12 §3.2 |
| Bluetooth scales | ~~openScale GPLv3 driver reuse~~ → **clean-room drivers** for popular protocols, credited upstream knowledge *(Apache-2.0 blocks GPL reuse — R-S1 amendment)*; Health Connect bridges vendor apps meanwhile | F13 §3; R-S1/R-S13 |
| Health Connect | Two-way sync, per-datatype consent, single profile (R-S9, R-B9) | F13 §3 |
| Egress proof | Debug-build egress monitor; no telemetry code in tree; per-SDK data-flow audit cards (R-S13); *(Pure flavor dropped 2026-09-11)* | F12 §3.8 |
| Ed25519 / CRDT | Signed exports + CRDT multi-device sync ratified as [future] direction | F13 §8 |

Explicitly banned: YCharts (dormant, R-D4); third-party SDKs in the silhouette module (F08 §9);
delivery-app integrations (F04 §9); social layer (F05/F11 §9); Health Connect stool sync (F09 §9);
Monash FODMAP data (R-S8); proprietary training data (R-S12).

---

## 3. Decision inventory

Decisions are grouped in eight domains, `T-A`…`T-H`. Each entry: the question, candidate
options, and what constrains it. "▸ leans" = a provisional default to validate, not a decision.

### T-A. Platform & language posture

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-A1 | **Language** | Kotlin is implied by Compose but never named in-repo. ▸ Kotlin 2.x stable, official Compose compiler. Sub-choice: JVM target & language-version pinning. |
| T-A2 | **KMP posture** | ✅ *Resolved 2026-09-11 (owner, q-000025):* **KMP-ready core, Android UI** — domain engines + data layer in common Kotlin Multiplatform code; UI stays Android-first Compose (Navigation-Compose etc. live in the Android source set). Consequences researched in ADR-001/003/004: common core restricted to KMP-compatible libraries (DI: Koin/kotlin-inject — Hilt is Android-only; HTTP: Ktor; DB: Room-KMP vs SQLDelight — ADR-003). F05/F07/F11 math engines stay dependency-free pure Kotlin regardless. |
| T-A3 | **minSdk / targetSdk** | ✅ *Resolved 2026-09-11 (owner, q-000023):* **API 29 (Android 10)**. Haptics degrade gracefully below 30/34 (DESIGN-SYSTEM §5); notification runtime permission only exists at 33+ (in-context ask per F10 §9 covers all levels). Coverage cost + feature validation researched in ADR-002. |
| T-A4 | **Distribution channels** | ✅ *Resolved 2026-09-11 (R-S13):* **Google Play + GitHub-Release APKs**; F-Droid optional later via a clean-stack audit, not a requirement. Play means: developer account, Play policies (health/AI content), Play App Signing, and Play-vended SDKs (Play Services components) now *eligible* where they beat open ones — still subject to the C5 audit card. Update checking: Play handles it for Play installs; GitHub installs can poll GitHub Releases (the only new non-consented egress candidate — needs an explicit allowance ruling alongside R-C4). |
| T-A5 | **License** | ✅ *Resolved 2026-09-11 (R-S1 amended):* **Apache-2.0.** Consequences: proprietary SDKs may be bundled (R-S13 conditions apply); GPLv3-only components (openScale's scale drivers) may **not** — Bluetooth-scale drivers become clean-room implementations of popular protocols, with Health Connect bridging vendor apps meanwhile; NOTICE/third-party-license inventory becomes a release artifact. |

### T-B. App architecture

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-B1 | **Pattern** | UDF with ViewModel state holders (MVVM) vs MVI-style single state machine. The specs already describe UDF (Targets two-writer API, ledgered Applies). ▸ MVVM+Flow with MVI discipline inside engines. |
| T-B2 | **Navigation + deep links** | Navigation-Compose vs Voyager/Manual. Must implement the `wlo://` registry (19+ routes, prefill payloads, lock-gated surfaces) — IA §3. Deep-link dispatch needs a first-class registry since F10's Day Model deep-links every card. |
| T-B3 | **DI** | KMP core rules out Hilt for shared code → **Koin vs kotlin-inject** (decided with ADR-001; ▸ kotlin-inject for compile-time safety if the team tolerates it, Koin for speed); the Android-only UI layer may still use Hilt or manual wiring. |
| T-B4 | **Modularization** | Single module vs feature-per-module. Notable: F08/F09 demand *zero network code paths in the module* — a `:core:network` module that sensitive feature modules cannot see makes that **compiler-enforced**. ▸ multi-module with `:core:{network,data,ai,design,engine-*}` + `:feature:f01…f13`. |
| T-B5 | **Background & processes** | WorkManager (decided) + exact-alarm question for nudge timing/quiet hours (R-U1), foreground service for BLE HR sessions (F05), single process assumed. |

### T-C. Persistence & data spine

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-C1 | **DB layer** | ✅ *Resolved 2026-09-11 (ADR-003, revised same day after version verification):* **Room 3.0.x (`androidx.room3`) from day one** — Kotlin-first (Kotlin-only codegen, KSP-only, suspend-only DAOs), SQLiteDriver-native, `@Fts5` for the food-search plan; 3.0.0 stable Jul 2026. Legacy `androidx.room` 2.8.5 rejected (maintenance-mode Java-codegen line); SQLDelight 2.x rejected (bus factor, no advantage). Schema/migration house rules in the ADR. |
| T-C2 | **KV + documents** | ✅ *Resolved 2026-09-11 (ADR-003):* **DataStore (Preferences) 1.2.1** in `commonMain` for settings (expect/actual paths). Versioned JSON documents (Targets/DietPlan/Recipe/export bundle) as kotlinx.serialization documents with the T-C6 evolution rules (ADR-004); ledger/receipt table design is implementation-phase (T-C4). |
| T-C3 | **Attachment vault** | Per-partition encrypted file store (`archive/`, `gut/`): SQLCipher vs per-file AES-GCM via Keystore-wrapped keys. Per-file keeps backups/export blanking simple (R-U18 excludes photos by default). Storage-accounting queries. |
| T-C4 | **Provenance + EAV implementation** | `Measurement/MeasurementType/MeasurementValue` EAV (F06 §3) + provenance table (formula version + inputs per derived number). Decide query patterns for day-scalars + CSV export columns. |
| T-C5 | **Search / Food Memory** | SQLite FTS5 for food search vs custom; on-device embedding model + vector similarity for Food Memory priors & F04 aisle engine (which embedder, which runtime — ties to T-E1). |
| T-C6 | **Schema versioning contract** | One versioning scheme shared by: Room schemas, export bundle `schema_version` (F13 §3), Targets/DietPlan doc versions, model zoo versions. Migration-on-import chain must be tested. |

### T-D. Serialization & documents

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-D1 | **JSON library** | ✅ *Resolved 2026-09-11 (ADR-004):* **kotlinx.serialization 1.11.x** (latest stable; 1.12.0-RC held back). Document-evolution house rules in the ADR (schemaVersion envelope, defaults + `ignoreUnknownKeys`, pinned discriminators, `JsonTransformingSerializer` migrations, CI round-trip fixtures) — the library ships no versioning, so the T-C6 contract is code + tests we own. |
| T-D2 | **Document diffing** | F01 wants human-readable DietPlan diffs + revert (§3). Candidates: custom structural diff over the frozen schema vs generic JSON-diff lib. Small, contained — decide during F01 implementation research. |

### T-E. On-device AI & capture stack (largest cluster)

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-E1 | **Inference runtime** | ✅ *Resolved 2026-09-12 (M4 spike → **ADR-006**):* **ONNX Runtime Mobile 1.29.0 now** (day-one fit with the only permissive/stable/hash-published food model, MIT, session-from-path); **LiteRT 2.2.0 tracked as end-state** (smaller, Kotlin-first, zero telemetry) behind explicit migration triggers (release channel for mirrored TFLite + in-repo conversion task or a native .tflite model of equal provenance). MediaPipe Tasks excluded (duplicate native runtime); Play-services TFLite excluded (telemetry). Inference stays behind expect/actual in `:core:ai` regardless. |
| T-E2 | **Food-photo model** | ✅ *Resolved 2026-09-12 (M4 spike → **ADR-007**):* **MobileNetV2 Food-101** (AlexKoff88 ONNX, 9.4 MB, sha256-pinned upstream URL, Apache-2.0, Food-101+ImageNet provenance, top-1 76.3%) — top-5 suggestions into the user-corrected save loop, `held` below confidence floor. Fallback: Google AIY Dish Classifier (post-mirror + license re-verify). WLO-trained detector (Nutrition5k/UEC provenance) = future work; no permissive on-device food detector exists today. |
| T-E3 | **Silhouette vision** | Pose (alignment scoring) + foreground segmentation → Bézier contour + 64-value width profile; <2 s/angle budget (F08 §3, §10). Candidates: MediaPipe pose/selfie-seg `.task` vs standalone TFLite (BlazePose/MoveNet + DeepLab-class). Frames memory-only (R-U16) — verify no SDK path persists frames. |
| T-E4 | **OCR** | Label photos, scale-display photos (F06 §3), receipts (F04, R-C3 consent-free). ✅ *Default candidate post-R-S13:* **ML Kit Text Recognition v2** (free, proprietary, fully on-device, markedly better than open options on microprint) — verify via its data-flow audit card that bundled-variant inference transmits nothing. Open fallback (Tesseract / ONNX-ported PaddleOCR-class) kept as a research check and for a hypothetical F-Droid build. Accuracy on nutrition-label microprint is the acceptance test either way. |
| T-E5 | **STT + intent parsing** | Streaming, 45–85 MB optional download (F12 §3.2); R-C6 exposes transcript-vs-raw-audio payload modes. Candidates: whisper.cpp (Whisper tiny/base) vs Vosk vs sherpa-onnx. Decision bundle-vs-download is an F12 §10 open question. **Owner question Q4 (app-size budget).** |
| T-E6 | **Local LLM** | [v1.x] embedded llama.cpp. Open: in-process JNI binding vs Ollama-style local server (F12 §10); model choice under redistributable licenses (Gemma's terms vs Qwen/Apache vs Llama license); acceleration (CPU threads vs GPU vs NPU delegates); RAM tiers (4 GB devices → server-only note already in F12 §10). Affects `insights-chat` + [v1.x] `meal-planning` local fallback. *Post-R-S13 addition:* a **Gemini Nano via AICore** tier (free, proprietary, on-device) on supported flagship devices, with llama.cpp as the portable path — both are local inference, so no consent needed and receipts mark them `local`; AICore device coverage and model quality for food/silhouette-adjacent prompts are Phase-B research items. |
| T-E7 | **Embeddings** | Food Memory matching + aisle classification (F02 §3, F04 §3): small on-device embedder (e.g., MobileBERT/gte-small class) sharing T-E1 runtime. |
| T-E8 | **Barcode** | ✅ *Default candidate post-R-S13:* **ML Kit Barcode** bundled variant (free, proprietary, on-device, fast on damaged/curved codes; also covers QR for F01's signed template sharing, T-F3) — pending its C5 audit card. **ZXing** (Apache-2.0) stays as the documented open fallback. Phase B benchmarks scan success-rate on mid-range cameras to confirm the choice, not to pick between two tiers anymore. |
| T-E9 | **Camera & depth** | CameraX capture pipelines for three flows (food, silhouette memory-only, poop) + ARCore depth where available (F02 §3) + reference-object heuristics fallback tier (F02 §10 open). Camera-preview-in-Compose story + viewfinder overlay rendering (depth-mesh shimmer at 60 fps). |
| T-E10 | **Zoo delivery** | ✅ *Resolved 2026-09-11 (owner, q-000024 → R-S14):* **download-on-first-use only; the APK ships no zoo models.** Needs: download manager under NetworkDispatcher, checksum verification, per-model storage accounting + one-tap reclaim, before-download size disclosure, first-use flows that degrade to R-U15 manual paths until the model is present. Vendor-SDK built-in models (ML Kit libs) are the tracked exception pending the Phase-B size audit. |
| T-E11 | **BYOK adapter layer** | OpenAI-compatible streaming (SSE) + Gemini + Anthropic + Ollama; per-category model routing, connection test, live model listing, price-table JSON import (F12 §3.3). Key handling per C-security below. Redaction ladder (EXIF strip, ≤1024 px, mask-instead-of-photo) lives in this layer (F12 §3.7). |

### T-F. Security & crypto

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-F1 | **At-rest crypto** | Keystore-wrapped per-partition keys + AES-GCM files (T-C3) vs SQLCipher. ▸ per-file AES-GCM (aligns with partition model + export blanking). |
| T-F2 | **Backup KDF** | Argon2id vs PBKDF2 for passphrase-derived backup encryption (R-U5). Argon2id preferred; needs an Android-viable impl. |
| T-F3 | **Signing** | ed25519 for [future] signed exports + [v1.x] signed DietPlan templates (F01 §8): Tink vs lazysodium vs BouncyCastle. QR generation rides ZXing (T-E8). |
| T-F4 | **Crypto library policy** | Tink (Apache-2.0, Google-maintained) as the single facade vs raw javax.crypto/Keystore. ▸ Tink for envelope ops, Keystore for key custody. |

### T-G. Documents & rendering

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-G1 | **PDF generation** | Doctor reports, journey reports, password-protectable PDF (F09 §8, F13 §3). Android `PdfDocument` has **no encryption** → need a lib: **PDFBox-Android (Apache-2.0) is now the default candidate** — iText (AGPL) is *ineligible* under Apache-2.0 (rule 2) unless commercially licensed. Rendering fully on-device. |
| T-G2 | **Share-card rendering** | Fixed-aspect PNG ~200 ms (F11 §3): Compose UI recorded to Bitmap (graphicsLayer→Picture). Decide approach + test harness. |
| T-G3 | **Image pipeline** | Coil for loading/caching; EXIF/GPS stripping utility; thumbnail strategy (R-U14 retention opt-ins); MediaStore exclusion enforcement. |

### T-H. Networking, integrations, platform surfaces

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-H1 | **HTTP client** | **Ktor client 3.5.x** (KMP, per T-A2) inside the single `NetworkDispatcher` (C3) — confirmed in ADR-004. Needs: SSE streaming for BYOK, timeouts/cancellation tied to consent revocation mid-flight (F12 §3.5), audit hooks per request (R-C4 per-lookup audit). |
| T-H2 | **Health Connect** | SDK version strategy, per-datatype consent mapping (R-S9), min-of-day dedup write logic (R-B9), no stool sync ever (F09 §9). |
| T-H3 | **Bluetooth** | Android BLE stack + scale-driver architecture (F13 §3, as amended): **clean-room** drivers for popular BIA scales (protocol knowledge credited to openScale; GPL code not reusable under Apache-2.0), Health Connect as the interim bridge to vendor apps; driver interface shape; foreground-service lifecycle for HR straps (F05). |
| T-H4 | **SAF / storage** | Backup-folder persistence, photo offload [v1.x], document-tree integrity manifest writing (F13 §3). |
| T-H5 | **Notifications** | Single-channel discipline per R-U1 (silent, ≤2 actions, self-dismissing), Android-13 permission ask-in-context-once (F10 §9), quiet-hours enforcement. |
| T-H6 | **Biometric / lock gate** | BiometricPrompt + PIN fallback, lock-timeout, FLAG_SECURE surfaces, app-switcher protection (F13 §3; IA §6) — implementation surface for `wlo://archive/capture` gate. |

### T-J. Quality engineering

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-J1 | **Unit-test stack** | JUnit4/5 vs Kotest; Turbine for Flow; injected fake clock (the engines are time-sensitive: EWMA windows, 14-day TDEE, quiet hours 21:30–07:30). Deterministic engines (F05/F07/F11) demand pure-function testability — golden-scenario files for forecast bands. |
| T-J2 | **Property-based testing** | Kotest property/arbitrary for the math cores: F04 unit/delta reconciliation, F07 write-path invariants (floor, pace cap, no-eat-back — Appendix A.2), F06 smoother bounds. ▸ yes for engines only. |
| T-J3 | **Android-scope tests** | Robolectric coverage policy vs instrumented-only; Compose UI test rules; Room migration tests (T-C1). |
| T-J4 | **Screenshot testing** | Roborazzi vs Paparazzi under current Compose. High value here: the design system is spec-verbatim (~80 animations, exact tokens) — component-level screenshot tests guard R-D fidelity. |
| T-J5 | **Performance validation** | Macrobenchmark + Baseline Profiles; jank budget CI check for the perf-bearing interactions (F03 ≤100 ms re-run, F08 <2 s derivation, share-card ~200 ms, 60 fps ambient loops). |
| T-J6 | **Static analysis** | ktlint/detekt/ktfmt; custom lint for house rules — e.g. rule 6 above (derived number without provenance chip), sockets outside `:core:network`, missing model-card references. |

### T-K. Build, CI, release

| ID | Decision | Candidates / considerations |
|---|---|---|
| T-K1 | **Build setup** | Gradle version catalogs + convention plugins; Kotlin 2.x Compose plugin; R8 full mode; module graph per T-B4. |
| T-K2 | **Flavors & F-Droid** | ✅ *Resolved 2026-09-11 (R-S13):* single release flavor; **"WLO Pure" dropped** — consent gate + dispatcher + egress monitor + audit cards are the enforcement. F-Droid-specific reproducible-build engineering descoped (reproducible builds stay a [moonshot] supply-chain nicety, F12 §8). Play App Signing + a GitHub-APK upload key; Play policies review for health/AI claims is a release checklist item. |
| T-K3 | **CI** | GitHub Actions: unit + Robolectric, screenshot diffs, emulator instrumented matrix, Macrobenchmark spot-runs, determinism/reproducibility check, APK artifact signing. |
| T-K4 | **Crash & feedback policy** | ✅ *Resolved 2026-09-11 (owner, q-000026):* **opt-in, content-free ACRA reports** (open-source crash reporter; off by default; explicit consent toggle; reports scrubbed of all user content; transmits ⇒ rides the NetworkDispatcher with receipts + audit card). Phase-D research: endpoint selection (ACRA's HTTP/email targets — no WLO backend exists) and the content-scrubbing pipeline. |
| T-K5 | **Versioning & updates** | SemVer discipline for app + export `schema_version` (T-C6); update discovery via GitHub Releases check (only non-BYOK egress besides OFF/USDA — needs a C3-compatible ruling) vs manual. |
| T-K6 | **Device matrix** | *Emulator-only for now (owner, 2026-09-12):* dev/CI matrix = an API 29 AVD (the min-spec floor) + one current-image AVD. Covers functional, UI, screenshot, and instrumented testing. Emulator-untestable by nature: Bluetooth scales/HR straps (no BT on emulators), ARCore depth (forces the F02 no-depth fallback tier to be built early — good insurance), and Gemini Nano/AICore (device-gated; the llama.cpp path is the verifiable one). First-real-device checklist: re-run ML/LLM spikes for absolute budgets, exercise BT + depth paths. |

---

## 4. Cross-cutting decision rules (proposed — ratify with the first ADRs)

1. **Consent filter (replaces the old Pure-flavor filter, R-S13):** a dependency is eligible
   iff it is free, materially better than the open alternative, and either provably
   on-device (documented on its audit card) or transmitting only behind an explicit consent
   toggle + receipts through the `NetworkDispatcher`. GPLv3-only components are ineligible
   (Apache-2.0 app) — flag any GPL dependency for clean-room reimplementation.
2. **License discipline (post-Apache):** prefer Apache-2.0/MIT/BSD libraries; GPL/AGPL
   code cannot be bundled (link it out or reimplement); LGPL avoided on Android
   (static-link ambiguity). Every bundled SDK's license + data-flow audit card lands in
   a third-party inventory that ships with the app (Play data-safety form draws from it).
3. **KMP fork first:** T-A2 gates T-C1, T-D1, T-H1. Resolve before the data/network layers exist.
4. **Every zoo model gets a model card** (purpose, benchmark, license, version, hash, named
   training data — F12 §3.2, R-S12) before it enters the tree.
5. **Assists never gate** (R-U15): each capture tech must ship with its equal-status manual path
   in the same epic — a UX-and-tech acceptance criterion, not just UX.
6. **Provenance is a compile-time concern:** rendering a derived number without a chip must be a
   lint/test failure, not a review catch (C6).

---

## 5. Owner questions (block research priority, not the inventory)

| # | Question | Why it matters |
|---|---|---|
| ~~Q1~~ | ~~Is F-Droid a hard v1 requirement?~~ | ✅ *Resolved 2026-09-11:* **No** — owner reframe; Play + GitHub APKs (R-S13, T-A4). |
| ~~Q2~~ | ~~Play Store ever?~~ | ✅ *Resolved 2026-09-11:* **Yes, primary** alongside GitHub APKs (T-A4). |
| ~~Q3~~ | ~~minSdk floor?~~ | ✅ *Resolved:* **API 29 (Android 10)** (T-A3; validation → ADR-002). |
| ~~Q4~~ | ~~App-size budget?~~ | ✅ *Resolved:* **download-on-first-use only — APK ships no zoo models** (R-S14, T-E10). |
| ~~Q5~~ | ~~iOS/KMP ambition?~~ | ✅ *Resolved:* **KMP-ready core, Android UI** (T-A2; stack → ADR-001/003/004). |
| ~~Q6~~ | ~~Crash/feedback channel?~~ | ✅ *Resolved:* **opt-in content-free ACRA** (T-K4; endpoint/scrubbing in Phase D). |

---

## 6. Research plan (revised 2026-09-12: just-in-time, per owner call)

Research is **pulled into the epic that needs it** — each feature starts with a small
"spike → ADR → build" step, not a monolithic up-front research phase. This doc stays the
checklist so nothing is forgotten at feature time; ADRs remain the ratification path.
Phase A is complete (foundation stack decided & verified); everything in T-E, T-F, T-G,
T-J, T-K researches inside its feature epic: barcode/OCR with F02/F04/F06, STT with F02
voice, the LLM with F12/F11 chat, the zoo runtime with the first model-consuming feature,
PDF generation with F09/F13 exports, the crypto vault with F13 attachments, the ACRA
endpoint with release engineering, and the heavyweight UI-tooling research (screenshot
tests, benchmarks) when the first perf-bearing UI lands.

Three exceptions must land **before the first on-device model ships** (whichever feature
that is — currently expected to be F02):

| Item | Why it can't wait for its own feature |
|---|---|
| **T-E1 inference runtime** | One decision shared by every analyzer behind `:core:ports`; nothing else in the zoo is choosable without it. |
| **R-S14 zoo delivery plumbing** | Downloader-under-NetworkDispatcher, hash verification, storage accounting is shared infrastructure built once, not per feature. |
| **Testing hardware: emulator-only for now** *(owner, 2026-09-12)* | Spikes and all testing run on the Android emulator (min-spec AVD: API 29). Emulators cannot reproduce phone-class ML performance (host CPU/GPU, no NPU/DSP delegates, no Bluetooth, no ARCore depth), so spike numbers count as **functional verification only**. Absolute budgets (<2 s silhouette derivation, tokens/s, 60 fps) become tracked guardrails with a "validate on first real device" gate before public release; Macrobenchmark on emulator still catches *relative* regressions against its own baseline. |

Feature order drives research order (suggested start: project skeleton → F01 onboarding →
F02 food logging, whose epic opens with the T-E1/T-E2 spike). One discipline worth
keeping: for features whose *core promise* is AI (F02 photo-first, F08 silhouette), the
spike runs before their detailed UI work locks, because benchmark results can change
scope (what degrades to manual, what ships v1 vs v1.x) — still just-in-time, only
sequenced inside the epic.

### ADR index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-kmp-posture.md) | KMP-ready core, Android-first UI, Koin DI | Accepted |
| [ADR-002](adr/ADR-002-min-sdk-api-29.md) | minSdk API 29; Play health/AI policy flags | Accepted |
| [ADR-003](adr/ADR-003-persistence.md) | **Room 3.0 (`androidx.room3`, Kotlin-first)** + DataStore 1.2.x | Accepted (revised same day: 2.8.x → 3.0 after verification) |
| [ADR-004](adr/ADR-004-serialization-networking.md) | kotlinx.serialization 1.11 + Ktor 3.5/SSE | Accepted (versions verified) |

*All versions verified 2026-09-11 against Google Maven (`dl.google.com/android/maven2`)
and Maven Central (`repo1.maven.org`) metadata — see the ticket WLO-0014 verification
comment for the full table.*

**Code organization:** proposed module graph, dependency rules, and pattern inventory live
in [ARCHITECTURE.md](ARCHITECTURE.md) — to be ratified as ADR-005 at implementation
kickoff.

Each decision graduates via a one-page ADR (options → evidence → choice → consequences) collected
in `docs/tech/adr/`, then a ruling line in `FEATURES.md` §3 (R-T*).
