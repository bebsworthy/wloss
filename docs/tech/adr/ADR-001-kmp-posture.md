# ADR-001: KMP-ready core, Android-first UI

**Status:** Accepted (owner decision q-000025, 2026-09-11; stack validated by research same day)
**Decides:** T-A2, T-B3

## Context

Owner wants the optionality of non-Android targets without paying for a second UI today.
All WLO engines (F05/F07/F11 math) must already be pure, offline, deterministic Kotlin.

## Decision

- **Structure:** Gradle Kotlin Multiplatform project. `commonMain` holds domain engines,
  data layer, document/serialization logic, and the consent/egress contract. `androidMain`
  holds UI (Jetpack Compose), Navigation, widgets (Glance), camera/ARCore, background
  work, and all platform integrations.
- **Purity check:** CI keeps a JVM (desktop) target compiling `commonMain` as cheap
  insurance that the core stays platform-free.
- **DI:** **Koin 4.2.x** (core + annotations) in `commonMain`, with the Koin
  compiler-plugin compile-safety checking enabled to catch missing definitions at build
  time (it is RC — if it proves immature, fall back to enabling Koin's
  verify-at-startup in debug builds, or move to kotlin-inject 0.9/kotlin-inject-anvil
  0.1.7). **Hilt stays out of `commonMain`** (Android-only per Google's docs); the
  Android-only UI layer may use Hilt or manual wiring later if it earns its keep.
- **iOS path (future, no work now):** Compose Multiplatform for iOS has been
  production-ready since CMP 1.8.0 (May 2025); Navigation Compose is mirrored for
  non-Android targets, so a future iOS shell can reuse both core and navigation API.

## Evidence (Sept 2026)

- Room/DataStore/Ktor/Koin all ship stable KMP artifacts; KMP is Google's officially
  encouraged Android-first on-ramp (developer.android.com/kotlin/multiplatform).
- Cost is front-loaded Gradle/source-set wiring (expect/actual for DB driver, DataStore
  paths, dispatchers); no macOS/Xcode/CI-for-iOS requirement while shipping Android-only.
- Stack details: see ADR-003/ADR-004 tables.

## Consequences

- Android-only staples (Hilt, niche AndroidX/Play libs) may not enter `commonMain` —
  lint/enforce via the desktop purity target.
- Slight, permanent build complexity. Accepted as the price of the owner's optionality call.
- Kotlin/language version pinned to what the KMP library matrix tolerates, verified
  2026-09-11: **Kotlin 2.4.x** (2.4.20 stable, Sept 2026), serialization 1.11.x,
  Room 3 requires KSP2 — exact pins live in the version catalog and are bumped together.
