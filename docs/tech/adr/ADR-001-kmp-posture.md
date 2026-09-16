# ADR-001: JVM-family shared core, Android-first UI

**Status:** Accepted (owner decision q-000025, 2026-09-11; stack validated by research same day)
**Decides:** T-A2, T-B3

## Context

WLO benefits from sharing deterministic core code between Android and JVM tests/tools,
but has no funded or scheduled non-JVM product target. Calling every `commonMain` source
portable obscures real JVM APIs used by storage, crypto, locale, and time code.

## Decision

- **Structure:** Gradle Kotlin Multiplatform remains the source-set/build mechanism.
  `commonMain` means shared by the current Android and JVM targets; it is not, by itself,
  a promise that code compiles for iOS/JS/Native. `androidMain`
  holds UI (Jetpack Compose), Navigation, widgets (Glance), camera/ARCore, background
  work, and all platform integrations.
- **Purity check:** CI keeps a JVM target compiling shared sources as protection against
  accidental Android coupling. This proves JVM-family reuse, not platform neutrality.
- **DI:** **Koin 4.2.x** (core + annotations) in `commonMain`, with the Koin
  compiler-plugin compile-safety checking enabled to catch missing definitions at build
  time (it is RC — if it proves immature, fall back to enabling Koin's
  verify-at-startup in debug builds, or move to kotlin-inject 0.9/kotlin-inject-anvil
  0.1.7). **Hilt stays out of `commonMain`** (Android-only per Google's docs); the
  Android-only UI layer may use Hilt or manual wiring later if it earns its keep.
- **Non-JVM path:** deferred until there is a concrete consumer. A future target starts by
  selecting genuinely portable modules (`model`, pure engines, documents, consent) and
  introducing platform boundaries only where compilation proves they are required.

## Evidence (Sept 2026)

- Room/DataStore/Ktor/Koin all ship stable KMP artifacts; KMP is Google's officially
  encouraged Android-first on-ramp (developer.android.com/kotlin/multiplatform).
- Cost is front-loaded Gradle/source-set wiring (expect/actual for DB driver, DataStore
  paths, dispatchers); no macOS/Xcode/CI-for-iOS requirement while shipping Android-only.
- Stack details: see ADR-003/ADR-004 tables.

## Consequences

- Android-only APIs may not enter `commonMain`; the JVM target and source scan enforce
  that useful boundary. JVM APIs are allowed until a real non-JVM target is ratified.
- Slight, permanent build complexity. Accepted as the price of the owner's optionality call.
- Kotlin/language version pinned to what the KMP library matrix tolerates, verified
  2026-09-11: **Kotlin 2.4.x** (2.4.20 stable, Sept 2026), serialization 1.11.x,
  Room 3 requires KSP2 — exact pins live in the version catalog and are bumped together.
