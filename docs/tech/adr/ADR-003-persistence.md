# ADR-003: Persistence — Room 3.0 (`androidx.room3`) + DataStore 1.2.x

**Status:** Accepted (2026-09-11; revised same day after version verification — original
draft targeted Room 2.8.x on stale "3.0 is alpha" research; owner challenged it correctly)
**Decides:** T-C1, T-C2

## Context

T-A2 resolved to a KMP-ready core. F13 §3 names "Room/SQLite" as the relational core.
Apache-2.0-only dependency policy (R-S13). WLO has no code yet, so there is no migration
cost — the only question is which Room line to start on.

## Decision

- **Relational core: Room 3.0.x from day one** — new coordinates **`androidx.room3`**:
  `room3-runtime` + `room3-compiler` (KSP) + the `androidx.room3` Gradle plugin
  (`room3 { schemaDirectory(...) }`). 3.0.0 went stable **July 1, 2026**; the legacy
  `androidx.room` 2.x line (2.8.5) is maintenance-mode and Java-codegen-era — irrelevant
  for a greenfield Kotlin-first project.
- Why 3.0 fits this project specifically:
  - **Kotlin-first**: Kotlin-only codegen, KSP-only, suspend-only DAOs (blocking DAO
    functions disallowed) — matches the coroutines-first KMP core.
  - **SQLiteDriver-native**: built on `androidx.sqlite` driver APIs (`BundledSQLiteDriver`)
    — exactly the hedge the original ADR was planning for; we now just start there.
  - **Genuinely KMP**: Android, iOS, JVM desktop, JS/WasmJS; builders need no Android
    `Context` in common code.
  - **`@Fts5` support** lands the food-search full-text plan (T-C5) natively.
  - The separate `androidx.room3` group coexists safely with Room-2.x-dependent libraries.
- **KV/settings: DataStore (Preferences) 1.2.1** in `commonMain` (latest stable;
  1.3.x line is alpha), constructed via `DataStoreFactory` with expect/actual paths.
- **House rules (unchanged):** every schema change lands with a migration + exported-schema
  test in the same PR; migrations receive `SQLiteConnection`; invalidation flows via
  `invalidationTracker.createFlow(...)` (the 2.x observer API is gone).
- Known API renames to bake into the code style: `@ColumnTypeConverter` (was
  `@TypeConverter`), `withWriteTransaction`/`useReaderConnection`, explicit
  `@DaoReturnTypeConverter` registration for Paging/LiveData interop.

## Alternatives considered

- **SQLDelight 2.x (2.3.2, Mar 2026):** viable, but bus-factor risk (single core
  maintainer, org spin-out) and no advantage over Kotlin-first Room for this codebase.
- **Room 2.8.x:** would only make sense to keep legacy SupportSQLite/kapt compatibility —
  which a greenfield project doesn't have. Original draft of this ADR picked 2.8.5 because
  research predated the 3.0 stable cut; corrected after Maven Central/release-page
  verification.

## Evidence (verified 2026-09-11 against primary sources)

- developer.android.com/jetpack/androidx/releases/room3 — 3.0.0 stable Jul 1, 2026;
  coordinates and API notes as listed above.
- android-developers.googleblog.com "Room 3.0: Modernizing Room" (Mar 13, 2026) —
  Kotlin-only codegen, KSP-only, SupportSQLite removal, 2.x maintenance-mode policy.
- dl.google.com maven-metadata: `androidx.room:room-runtime` latest = 2.8.5 (legacy line).
- DataStore metadata: latest stable 1.2.1 (Mar 2026), 1.3.0-alpha11 in flight.

## Consequences

- Young major version: fewer community answers and third-party integrations; mitigated by
  first-party Google support, a stable (not experimental) API, and our DAO layer isolating
  swap risk (SQLDelight remains the escape hatch).
- KSP2 and Kotlin 2.4.x alignment required (see ADR-001 version pins).
- Paging (if ever needed) arrives via `room3-paging` with a registered
  `PagingSourceDaoReturnTypeConverter` — not needed for v1 data surfaces.
