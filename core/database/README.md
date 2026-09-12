# :core:database — Room 3 persistence core

Room 3 (`androidx.room3` 3.0.x) module: Kotlin-first, KSP2-only, suspend-only
DAOs, `BundledSQLiteDriver` (ADR-003). KMP targets: `jvm()` (purity + tests)
and an Android library target (AGP built-in `com.android.kotlin.multiplatform.library`).

## Layout

- `src/commonMain/kotlin` — entities, DAOs, `WloDatabase`, `Migrations`
- `src/jvmMain/kotlin` — `jvmDatabaseBuilder(path)` (name-based builder + bundled driver)
- `src/androidMain/kotlin` — `androidDatabaseBuilder(context, path)` (`:app` supplies the Context and binds via Koin in the app-shell milestone)
- `schemas/` — exported schemas, checked into the repo (fed by the
  `androidx.room3` Gradle plugin: `room3 { schemaDirectory(...) }`); they are
  the source of truth for migration tests

## Migration testing — JVM, not instrumented (decision)

**JVM unit tests.** Room 3 publishes `room3-testing-jvm` and
`androidx.sqlite:sqlite-bundled-jvm` (stable, verified against the
dl.google.com group index 2026-09-12), and the JVM `MigrationTestHelper`
constructor takes `(schemaDirectory: Path, databaseFile: Path, driver:
SQLiteDriver, databaseClass: KClass<...>)`. So `MigrationTest` creates a v1
database from the exported `1.json`, runs `Migrations.MIGRATION_1_2`, and
validates the resulting schema **without an emulator**, in `./gradlew build`.

Rationale: schema validation is pure I/O + SQL — instrumenting it buys boot
time and flake, not fidelity. The instrumented path (`androidTest`) remains
available for PART B's UI-layer tests (CI runs
`:app:connectedDebugAndroidTest` on an API 29 emulator).

## House rules (ADR-003)

- Every schema change lands with a migration + exported-schema test in the
  same PR. Bump `WloDatabase` version, export the new `schemas/**/<n>.json`,
  add the `Migration`, extend `MigrationTest`.
- API renames to keep in mind: `@ColumnTypeConverter` (was `@TypeConverter`),
  `withWriteTransaction` / `useReaderConnection`, `invalidationTracker.createFlow`
  (2.x observer API gone), suspend-only DAO functions.
