# WLO — Application Architecture

**Status:** §1 is a ratified summary (normative sources: ADR-001…004, FEATURES.md §3
rulings, DECISION-SPACE.md). §2–§3 are the **proposed** code organization — to be
ratified as ADR-005 (module graph & dependency rules) when implementation starts.
Reading order for engineers: this doc → the ADRs → the feature spec you're touching.

---

## 1. The decided stack (summary)

### 1.1 Platform & build

| Area | Decision | Source |
|---|---|---|
| Language | Kotlin 2.4.x (2.4.20), KSP2 | ADR-001 |
| Posture | **JVM-family shared core, Android-first UI** — `commonMain` is compiled for Android/JVM and bans Android APIs, but non-JVM portability is claimed only for modules proven by a future target | ADR-001; WLO-0065 |
| UI toolkit | Jetpack Compose (1.12.x line), Material 3 semantics per DESIGN-SYSTEM.md; Navigation-Compose 2.10.x in the Android source set | DESIGN-SYSTEM; ADR-001 |
| DI | Koin 4.2.x + annotations, compile-safety checking on; Hilt stays out of `commonMain` | ADR-001 |
| Modules | Gradle multi-module, `build-logic` convention plugins, version catalogs | §2 (proposed) |
| minSdk / target | **API 29** / latest stable | ADR-002 (owner q-000023) |

### 1.2 Data & documents

| Area | Decision | Source |
|---|---|---|
| Relational store | **Room 3.0.x (`androidx.room3`)** — Kotlin-first, KSP-only, suspend-only DAOs, SQLiteDriver (`BundledSQLiteDriver`), `@Fts5` for food search; schema export + migration tests | ADR-003 |
| KV/settings | DataStore (Preferences) 1.2.1 | ADR-003 |
| JSON | kotlinx.serialization 1.11.x; versioned-document house rules (schemaVersion envelope, defaults, pinned discriminators, transforming-serializer migrations, CI round-trip fixtures) | ADR-004 |
| Storage model | Event-level storage, day-level rendering (R-B8); EAV sidecar for custom metrics; provenance table per derived number; archive-don't-delete catalog | F13 §3; FEATURES Appendix A |
| Sensitive attachments | Encrypted vault partitions, per-file AES-GCM, opaque filenames, MediaStore exclusion, FLAG_SECURE surfaces | F08/F09 §9; F13 §3 |

### 1.3 Network, consent & AI policy

| Area | Decision | Source |
|---|---|---|
| Egress | **Single `NetworkDispatcher` choke point** — feature code cannot reach a socket; every call needs a consent grant (or the OFF/USDA allowance) and issues a hash-chained receipt | F12 §3.8; C3 |
| HTTP | Ktor client 3.5.x + SSE (BYOK streaming); OkHttp at most as the Android engine (no native targets) | ADR-004 |
| Consent | Six frozen AI categories + integration toggles; append-only consent ledger; revocation cancels in-flight calls | F12 §3; R-C1 |
| Proprietary SDKs | Allowed when free + materially better: **on-device** ⇒ data-flow audit card; **transmitting** ⇒ consent toggle + receipts (R-S13) | R-S13 |
| AI defaults | On-device first, airplane-mode parity, named fallback per capability; cloud = BYOK only | F12 §3.1 |
| Model zoo | **Download-on-first-use only — the APK ships no zoo models**; hash-pinned URLs, model cards, storage accounting, one-tap reclaim | R-S14 |
| Local LLM | llama.cpp embedded (portable path) with a Gemini Nano/AICore tier on supported flagships; both local ⇒ receipts marked `local`, no consent | F12 §3/§8; T-E6 |

### 1.4 Product-engineering invariants (the "constitution" §2 must enforce)

| Invariant | Consequence in code | Source |
|---|---|---|
| No egress without consent | Silhouette & gut feature modules must be **unable to see** any network-capable module | C3; F08/F09 §9 |
| Provenance on every derived number | A `DerivedValue<T>` type; UI renders it only via provenance-chip components; lint-enforced | C6; rule 6 |
| Weak data is `held`, never guessed | Data-quality states (`developing/updating/held`) are part of domain types, not UI strings | FEATURES §2.1 |
| Assists never gate | Every AI capture port has an equal-status manual path in the same flow | R-U15 |
| Silhouette = vector only | Camera frames are memory-only in the capture pipeline; the persistence path accepts only vector records | R-U16 |
| One of each shared thing | One capture/scan stack (F02/F04/F09), one widget framework (F10), one heatmap, one constants registry (7,700 kcal/kg …) | R-U12; R-D4; R-A1 |
| Targets written by exactly two writers | F01 Studio and F07 Apply — a sealed writer API, not a public setter | R-B2; Appendix A |
| No telemetry | No analytics/crash SDK by default; opt-in content-free ACRA only (T-K4), riding the same dispatcher discipline | F13 §9; q-000026 |
| License hygiene | Apache-2.0; GPL/AGPL components ineligible; third-party license inventory + SDK audit cards ship with releases | R-S1/R-S13 |

---

## 2. Code organization (proposed)

### 2.1 Layering model

Classic layers, adapted to WLO's reality: the **domain layer already exists as the pure
engines**, so we don't bolt on a ceremonial use-case layer everywhere. Orchestration
logic (e.g. "photo → scan → sanity rails → diary entry") lives in small, injectable
interactors inside feature modules.

```
┌───────────────────────────────────────────────────────────┐
│ :app  (composition root: nav graph, deep links, Koin wiring, │
│        manifest — the ONLY module declaring INTERNET)        │
└───────────────┬───────────────────────────────────────────┘
                │ binds ports → implementations
┌───────────────▼───────────────┐   ┌──────────────────────┐
│ :feature:f01…f13              │   │ restricted impls      │
│ UI (Compose) · state holders  │   │ :core:network (Ktor)  │
│ interactors · DI modules      │   │ :core:ai (LiteRT, …)  │
└───────┬───────────────────────┘   │ :core:media (CameraX) │
        │ depends only on           │ :core:vault (SAF/keys)│
        ▼ model · engines · ports · │ :core:backup-sched    │
          data interfaces           └──────────▲───────────┘
┌────────────────────────────────┐             │ implements ports
│ commonMain core:               │─────────────┘
│ :core:model (DerivedValue,     │
│  capabilities, constants)      │
│ :core:engines (F03/F05/F07/F11)│  pure, deterministic, golden-tested
│ :core:ports (interfaces only)  │
│ :core:consent (ledger, gate)   │
│ :core:data (repositories)      │
│ :core:database (Room 3)        │
│ :core:datastore, :core:documents│
└────────────────────────────────┘
```

### 2.2 Module graph

**Common (KMP `commonMain`):**

| Module | Contents | Depends on |
|---|---|---|
| `:core:model` | Domain types; `DerivedValue<T>` + provenance; `ConsentCapability`; data-quality states; versioned constants registry (R-A1 7,700 kcal/kg etc.) | nothing |
| `:core:common` | `Clock`/dispatcher providers (ports), `AppError` sealed hierarchy, logging port, `DayBoundary` & unit utilities (R-D10) | `:core:model` |
| `:core:engines` | F03 planner, F05 exercise rules, F07 energy/forecast, F11 stats/correlations — pure functions, instant-in/value-out; golden-tested | `:core:model` |
| `:core:ports` | Interfaces only: `EgressPort`, `PhotoAnalyzer`, `BarcodeScanner`, `SpeechTranscriber`, `OcrReader`, `BodyVectorizer`, `StoolClassifier`, `ModelManager`, `BackupScheduler`, `BiometricGate` | `:core:model` |
| `:core:consent` | Ledger (append-only), grant state, gate API, hash-chained receipt writer | `:core:model` |
| `:core:database` | Room 3 entities/DAOs/DB, migrations, FTS5 food index, EAV + provenance tables | `:core:model` |
| `:core:documents` | Document envelope, schema-version migrations, serialization house rules, export-bundle assembly | `:core:model` |
| `:core:datastore` | Settings + per-profile document stores | `:core:model`, `:core:documents` |
| `:core:data` | Repository interfaces + Room-backed implementations; day-projection read API (Appendix A.3); diary/event queries | database, datastore, documents, engines (read-only feeds) |
| `:core:testing` | Fake clock, fake dispatcher, fixture builders, engine golden harness | test-only |

**Android (`androidMain` / Android library):**

| Module | Contents | Notes |
|---|---|---|
| `:core:network` | Ktor client, SSE, audit plugin, BYOK provider adapters (OpenAI-compatible/Gemini/Anthropic/Ollama), OFF/USDA clients, R-S14 model downloader | **implements `EgressPort` + network ports; visible only to `:app`** |
| `:core:ai` | LiteRT runtime, zoo manager (verify/account/reclaim), analyzer implementations of the AI ports; Gemini Nano tier behind the same ports | on-device only; audit cards live here |
| `:core:media` | CameraX pipelines (food/stool/silhouette), EXIF strip, memory-only frame contract for R-U16 | no persistence deps |
| `:core:vault` | Keystore-wrapped per-file AES-GCM partitions, SAF backup writer, storage accounting | implements `BackupScheduler` |
| `:core:designsystem` | Tokens, two springs + two easings, haptics wrapper (SDK-gated fallbacks), provenance chip, signature charts (forecast cone, ribbon, rings, odometer), shared month heatmap | implements R-D4/D6 |
| `:core:widget` | Glance stack (hero widget, sparkline pre-render), R-U12 single framework | F10-owned API |

**Features (`:feature:f01-onboarding` … `:feature:f13-vault`)** — each Android, with the
same internal shape:

```
:feature:f02-food/
  ui/        screens + Compose components
  state/     ViewModels: StateFlow<UiState>, MVI-lite events
  domain/    interactors ("scanAndLog", "correctEntry") — orchestration only
  di/        Koin module
  + routes.kt  public route contracts (deep-link targets, arg schemas)
```

- Feature → **feature** dependencies are forbidden. Cross-feature navigation goes through
  route contracts; shared UI belongs in `:core:designsystem`.
- `:app` wires: navigation graph, `wlo://` deep-link registry (tested), Koin composition
  root (all port bindings), WorkManager/AlarmManager setup, the merged manifest.

**Start-lean note:** the graph can begin as `:core:*` (5 modules) + `:app` + the first two
features, growing per feature — the dependency rules don't change.

### 2.3 Dependency rules — enforced, not aspirational

| # | Rule | Enforcement |
|---|---|---|
| D1 | Features never depend on `:core:network`, `:core:ai`, `:core:media`, `:core:vault` | Gradle classpath: those modules are **not on feature modules' compile classpath**; only `:app` sees them. CI `checkArchitecture` task asserts the resolved-graph edges. |
| D2 | Feature → feature deps forbidden | Same CI graph check. |
| D3 | `commonMain` never imports Android | JVM host target plus source scan compile in CI. This proves Android independence, not iOS/Native portability (ADR-001). |
| D4 | INTERNET permission declared only in `:app` | CI checks the merged manifest + greps module manifests; features physically can't add it back because of D1. |
| D5 | Core modules use Kotlin `explicitApi()` | Compiler-enforced public-surface discipline. |
| D6 | Derived numbers render only via provenance components | `DerivedValue<T>` has no `toString`-to-UI path; custom lint fails on raw rendering; Compose overloads only accept `DerivedValue` for stat displays. |
| D7 | Engines take `Instant`/values as parameters — no `Clock.now()`, no IO, no globals | detekt rules + purity of `:core:engines` deps. |
| D8 | No exceptions across module boundaries | Domain errors are sealed `AppError` subtypes in `Result`-style returns; libs' exceptions are wrapped at repository edges. |
| D9 | Networking stacks exist only in `:core:network` | `checkArchitecture` rejects Ktor/OkHttp/Retrofit declarations and resolved production-classpath transitives elsewhere; `:app` is the intentional composition-root recipient and localhost-only test servers are exempt. |
| D10 | Android system backup and implicit device transfer are disabled | Debug and release merged manifests must set the fail-closed attributes; legacy and Android 12+ rule files must exclude every credential- and device-protected storage domain (WLO-0058). |

This is **dependency inversion made structural**: features and core own the *ports*
(`:core:ports`), the restricted modules *implement* them, and `:app` (composition root)
does the binding via Koin. The consent gate is the flagship example — `EgressPort` plus
the ledger means "no consent, no socket" is true by construction, and the silhouette
module's "zero network code paths" (F08 §9) is compiler-checked, not promised.

### 2.4 Key patterns

- **Repositories + projections.** Repositories expose `Flow`s of *event-level* records
  (R-B8) and derived *day views*; rendering code never re-implements aggregation (the
  day-projection API is the only door — Appendix A.3 discipline generalized).
- **`DerivedValue<T>` provenance.** `data class DerivedValue<T>(value: T, provenance:
  Provenance)` where `Provenance` = measured / estimated / derived(formulaVersion, inputs)
  / held; AI results add model id + consent state. Charts, chips, and share cards consume
  it uniformly — one rendering path, lint-enforced (D6).
- **MVI-lite state holders.** `ViewModel` exposes `StateFlow<UiState>` + a single
  `onEvent(Event)` entry point; one-offs as `Channel<Effect>`. No business logic in
  composables; no LiveData; suspend + Flow only.
- **Documents.** Targets/DietPlan/Recipe/exports are immutable versioned documents with a
  single migration funnel in `:core:documents`; Targets' two-writer rule (F01/F07, R-B2)
  is a sealed API — `TargetsWriter` implemented twice, nothing else can write.
- **Capability analyzers.** Every AI assist implements a port in `:core:ports` with the
  same result shape: `Analysis<T>` = value + confidence + provenance + `held` flag. Sanity
  rails (F02 clamps) run in the interactor *after* the port returns, so rails are
  SDK-independent and tested without any model.
- **Background work.** What to do (backup assembly, nudge arbitration per R-U1) is common
  and unit-tested; when to run (WorkManager/alarms) is Android glue implementing
  `BackupScheduler`/`NudgeScheduler` ports.
- **Error handling.** User-facing failures are `AppError` subclasses mapped to copy in one
  place; provider errors follow F12's flow (toast with provider's words, on-device
  fallback, receipt marked failed).

### 2.5 DRY anchors (single-owner list)

| Concern | Single owner |
|---|---|
| 7,700 kcal/kg, floors, pace caps, rubric versions | `:core:model` constants registry (versioned — F07 Algorithms page renders from it) |
| Day boundaries, noon-normalization, week math | `:core:common` |
| Units (metric default, R-D10) | `:core:common` unit converter |
| Capture/scan stack (camera, barcode, OCR) | `:core:media` + `:core:ai` behind shared ports (F02/F04/F06/F09 reuse) |
| Signature charts + heatmap | `:core:designsystem` (R-D4) |
| Provenance chip & quality badges | `:core:designsystem` |
| Widget framework | `:core:widget` (F10 API, R-U12) |
| Document schema versioning | `:core:documents` |
| Canonical food/item space for F03↔F04 | `:core:model` |
| Koin wiring style, ViewModel factories | `build-logic` convention plugins |

### 2.6 Testing architecture (maps to T-J)

- **Engines:** pure JVM tests + golden scenario files (forecast bands, planner outputs).
  Property tests for reconciliation/invariant math (Appendix A.2 invariants).
- **Repositories:** Room in-memory via the JVM driver; migration tests from exported
  schemas; document round-trip + old-version fixtures in CI.
- **ViewModels/interactors:** fakes from `:core:testing` (fake clock matters: EWMA
  windows, quiet hours 21:30–07:30).
- **UI:** Roborazzi screenshot tests against the design system (guards R-D fidelity);
  Compose UI tests for capture flows; deep-link registry test in `:app`.
- **Performance:** Macrobenchmark budgets for F03 ≤100 ms re-runs, F08 <2 s derivation,
  share-card ~200 ms (CI-tracked).

### 2.7 Naming & style

- Packages: `app.wlo.core.*`, `app.wlo.feature.*`, `app.wlo.app` *(final groupId/domain
  to be confirmed before first release)*.
- Style: ktlint + ktfmt, detekt with the house ruleset (D6/D7 rules live here);
  `build-logic` convention plugins so module build files stay ~20 lines.
- Feature modules named by spec id (`:feature:f02-food`) to keep doc↔code traceability
  mechanical.

---

## 3. Open items

1. ~~Ratify §2 as **ADR-005**~~ — **done 2026-09-12** (`adr/ADR-005-module-graph.md`);
   graph starts lean per §2.2, rules D1–D8 unchanged.
2. Application id / Maven group: **`app.wlo`** ratified as the standing default in
   ADR-005 (owner confirmation pending async on WLO-0023; mechanical to change until
   first Play upload).
3. What lands inside `:core:ai` (runtime + models) is **just-in-time research** done in
   the epic of the first model-consuming feature (expected F02) — the port shapes above
   are already runtime-agnostic, so the deferral is cheap (DECISION-SPACE §6).
