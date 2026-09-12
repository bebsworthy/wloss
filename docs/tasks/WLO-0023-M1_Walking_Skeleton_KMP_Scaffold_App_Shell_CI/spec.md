# M1 — Walking skeleton

## Objectives
1. Ratify **ADR-005** (module graph & dependency rules) and confirm the application id
   (`app.wlo` placeholder) before first commit of code.
2. Scaffold the Gradle KMP multi-module project per `docs/tech/ARCHITECTURE.md` §2
   (lean subset is allowed; the dependency rules do not change).
3. Ship a launching app shell implementing the design system's structural decisions.
4. CI green: build + unit + lint + architecture checks, emulator-only per WLO-0014.

## Contents
- `build-logic` convention plugins (kmp-library, android-library, feature, koin) +
  version catalog; Kotlin 2.4.x, KSP2; Room Gradle plugin.
- Modules (lean start): `:core:model`, `:core:common`, `:core:engines`,
  `:core:ports`, `:core:consent`, `:core:database`, `:core:documents`,
  `:core:datastore`, `:app`. Feature modules added per milestone.
- Room 3 (`androidx.room3`) + `BundledSQLiteDriver` + schema export wired; DataStore
  1.2.x; Koin 4.2 with compile-safety checking; kotlinx.serialization 1.11.x with the
  document envelope + house rules (ADR-004); exported-schema migration harness.
- `:app` shell: single activity, R-D1 dark-first theme, R-D2 five-tab bottom nav
  (Hub · Plan · Insights · Archive · Digestion) with placeholder screens, Inter +
  tabular figures (R-D3), `DerivedValue<T>` + provenance chip + haptics wrapper,
  `wlo://` deep-link scaffold.
- Enforcement from day one: `explicitApi()` on core modules, JVM purity target in CI,
  `checkArchitecture` graph check, merged-manifest INTERNET check, detekt + ktlint.

## Relevant documentation
- `docs/tech/ARCHITECTURE.md` (§2 module graph, §2.3 rules D1–D8, §2.5 DRY anchors)
- `docs/tech/adr/ADR-001…004` (stack), `docs/tech/DECISION-SPACE.md` (T-ids)
- `docs/design/DESIGN-SYSTEM.md` §2 tokens, §4 motion, §5 haptics; `docs/design/IA.md` §2–3
- Rulings: R-D1, R-D2, R-D3, R-D10 (metric default); constraints: emulator-only (WLO-0014)

## Acceptance criteria
1. App installs and launches on the API 29 AVD; five placeholder tabs navigate;
   `wlo://hub` deep link resolves.
2. CI is green and fails deliberately on: feature→network dep, Android import in
   commonMain, INTERNET outside `:app`, derived number rendered without chip (lint demo).
3. Room schema export + one example migration test pass; JVM purity target compiles.
4. A dummy `DerivedValue` renders through the provenance chip end-to-end.
