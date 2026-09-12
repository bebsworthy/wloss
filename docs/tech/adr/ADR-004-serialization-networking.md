# ADR-004: Serialization = kotlinx.serialization; Networking = Ktor 3.4.x

**Status:** Accepted (research + decision 2026-09-11)
**Decides:** T-D1, T-H1

## Context

The KMP core needs JSON for versioned documents (Targets, DietPlan, export bundle,
model cards, price tables) and HTTP for the single `NetworkDispatcher` choke point
(BYOK AI streaming incl. SSE, OFF/USDA lookups, R-S14 model downloads). Apache-2.0-only.

## Decision

- **JSON: kotlinx.serialization 1.11.x** (latest stable; 1.12.0-RC in flight — stay on
  stable) in `commonMain`.
- **HTTP: Ktor client 3.5.x** (current 3.5.2, Jul 2026) in `commonMain`, with the SSE
  plugin for BYOK streaming; Android engine (or OkHttp engine) lives in `androidMain`
  only. Raw OkHttp cannot anchor the common core: OkHttp 5.5.0 publishes JVM and JS
  KMP-style artifacts but **no native targets** (no iOS/macOS), so it can't back a
  native-capable `commonMain`; it survives at most as Ktor's Android engine.
- **House rules for versioned documents** (kotlinx.serialization ships *no* schema
  versioning — we own it, feeding the T-C6 contract):
  1. Every document carries an explicit `schemaVersion` envelope (per F13 §3).
  2. New fields always get defaults; `ignoreUnknownKeys = true`; renames use
     `@JsonNames`; old versions normalized via `JsonTransformingSerializer` before
     decode.
  3. Polymorphic document ASTs use **sealed** hierarchies (closed polymorphism,
     compile-checked) with **pinned** `classDiscriminator` values — never class-name
     defaults.
  4. Round-trip + old-version fixtures decoded in CI (export bundle, Targets, DietPlan).
- **Dispatcher wiring:** `HttpTimeout` — for SSE/LLM streams set
  `requestTimeoutMillis` high/null (it covers the whole call) and guard with
  inactivity-based `socketTimeoutMillis`; consent revocation cancels the call's scope
  mid-flight (F12 §3.5), which composes with Ktor's structured-concurrency session
  cancellation.

## Evidence (Sept 2026)

- Ktor **3.5.2** stable (Jul 31, 2026, per Maven Central metadata); SSE stable since 3.0
  with Flow-based `incoming`, typed deserialization, reconnection policy; duplex streaming
  on the OkHttp engine since 3.4.0; the KTOR-9023-era SSE reordering bug was fixed in
  3.3.x — stay current on 3.5.x.
  (ktor.io/docs/client-server-sent-events.html; blog.jetbrains.com Jan 2026;
  repo1.maven.org/io/ktor/ktor-client-core.)
- OkHttp **5.5.0** (Aug 2026) — JVM + JS artifacts only, no native targets
  (repo1.maven.org/com/squareup/okhttp3/).
- kotlinx.serialization **1.11.0** stable (1.12.0-RC, Sept 4, 2026); no built-in schema
  versioning by design — github.com/Kotlin/kotlinx.serialization/issues/2585;
  polymorphism rules in the official docs.

## Consequences

- All egress code reviews happen in `:core:network` only; feature modules declare
  capabilities (C3 compiler-enforced module split per DECISION-SPACE T-B4).
- The per-request audit hooks (R-C4) implement as a Ktor client plugin inside the
  dispatcher — single place, testable.
