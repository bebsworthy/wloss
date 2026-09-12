# ADR-005: Module graph & dependency rules (ratifies ARCHITECTURE.md §2)

**Status:** Accepted (implementation kickoff, WLO-0023, 2026-09-12)
**Decides:** ARCHITECTURE.md §3 open items 1 and 2 (module graph ratification; application id)

## Context

ARCHITECTURE.md §2 proposed a code organization whose entire point is that WLO's
product constitution (no egress without consent, provenance on every derived number,
manual path parity) is **compiler-enforced, not promised**. Before the first commit of
code, that proposal needs ratification as an ADR, and the application id must be fixed
(it bakes into the package namespace, the Play Store listing, and `wlo://` URIs).

## Decision

### 1. Module graph — ratified as proposed, starting lean

The §2.2 module graph and §2.3 dependency rules (D1–D8) are normative from the first
commit. M1 lands the **lean subset**:

```
:core:model  :core:common  :core:engines  :core:ports  :core:consent
:core:database  :core:documents  :core:datastore  :core:testing (test-only)
:app        (+ :core:designsystem for the shell)
```

`build-logic` convention plugins (`wlo.kmp.library`, `wlo.android.library`,
`wlo.feature`, `wlo.kotlin.multiplatform` variants) plus a version catalog keep module
build files near-constant-size as modules are added per milestone. `:core:data`,
`restricted` impls (`:core:network`/`ai`/`media`/`vault`), `:core:widget`, and feature
modules join in later milestones **without rule changes**.

Non-negotiables held from day one (D1–D8, see ARCHITECTURE.md §2.3 for the table):
feature/restricted isolation by classpath (D1), no feature→feature deps (D2),
`commonMain` Android-import ban via the JVM purity target + source scan (D3),
INTERNET only in `:app` via merged-manifest check (D4), `explicitApi()` on core
modules (D5), provenance-chip rendering discipline with a lint demonstration (D6),
clock-free engines (D7), no exceptions across module boundaries (D8).

### 2. KMP targets posture

Core modules compile `commonMain` against a **JVM target (the purity target)** plus an
**Android (library) target** where the module touches the platform (`:core:database`
for Room's Android driver wiring, `:core:designsystem` for Compose). Pure modules
(`:core:model`, `:core:engines`, `:core:ports`, `:core:documents`) may be JVM-only
until a second platform needs them. No iOS target is declared now (ADR-001).

### 3. Application id / package namespace

**`app.wlo`** — application id `app.wlo`, packages `app.wlo.core.*`,
`app.wlo.feature.*`, `app.wlo.app`, deep links `wlo://…`.

Rationale: short, unambiguous, brandable, and namespace-safe (`app.` is a controlled
reverse-domain style that cannot collide with an existing company domain). It does not
assert a domain the project doesn't own. Cheap to change until the first Play upload;
expensive after — the owner is asked to confirm via ticket question, with `app.wlo`
standing as the confirmed default meanwhile.

## Consequences

- Every later milestone inherits enforcement: a violation fails the build, not a review.
- The lean set means `:core:data` (repository layer) arrives in M2 together with the
  first real schema — repositories are not stubbed in M1.
- `checkArchitecture` is a Gradle task (resolved-configuration graph + source scans)
  with a **self-test**: the build can demonstrate each rule failing on a synthetic
  violation, and CI runs that demonstration so the checks themselves can't rot.
- If the owner later renames the application id before first release, it is a
  mechanical refactor (one catalog property + package moves); after first Play upload
  it would require a new listing — hence the standing ticket question.
