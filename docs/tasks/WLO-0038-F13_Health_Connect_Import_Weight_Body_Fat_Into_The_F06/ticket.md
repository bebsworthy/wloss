---
id: WLO-0038
title: 'F13 Health Connect import: weight + body-fat into the F06 store with dedup + visible import log'
status: backlog
theme:
release:
created: 2026-09-14T13:37:55Z
modified: 2026-09-14T13:52:36Z
closed:
revision: 4589454048c567ac
blocks: []
related: []
---

# Description

# Goal

Health Connect weight + body-fat records import into the measurement store (F06 §3 inputs via F13): source=HEALTH_CONNECT events under R-B8 event-level semantics, silent dedup, import log visible. Data import, not AI — no F12 consent category consumed.

# Work items

- HC client (androidx.health.connect) in F13's port layer: permission request, read WeightRecord / BodyFatRecord since a cursor.
- Import via appendWeighIn / measurements.append with a HEALTH_CONNECT source constant; capturedAt from the record.
- Dedup: (source, timestamp±window, value) already stored → skip + count; import-log rows surfaced on the vault surface (the F06 logbook consumes them once WLO-0035 W2 lands).
- Manual "import now" entry on the vault surface first; background sync later.
- Fake-provider tests for dedup edges: same instant, overlapping windows, lb→kg unit conversion.

# Gates

Instrumented/robolectric tests on a fake HC provider; import log visible in a demo; arch enforce 0; ktlint + detekt.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) inputs (Health Connect via F13) · §5 (logbook + import log the dedup report feeds)
- Ruling: [FEATURES.md §3](docs/features/FEATURES.md) R-B8 (event-level storage — imports keep every reading, dedup visible in the import log)
- Companion: [F13-data-vault.md](docs/features/F13-data-vault.md) (vault import/export doors; wlo://vault registry in [IA.md §3](docs/design/IA.md))
