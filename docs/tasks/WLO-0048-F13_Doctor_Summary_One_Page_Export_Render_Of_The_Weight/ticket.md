---
id: WLO-0048
title: 'F13 doctor summary: one-page export render of the weight story'
status: idea
theme:
release:
created: 2026-09-14T13:38:55Z
modified: 2026-09-14T14:02:55Z
closed:
revision: dc250fdb31727551
blocks: []
related: []
---

# Description

# Goal

F06 §5's one-page doctor summary: an explicit-action export with goal, start/current/trend weight, weekly rate, milestone progress — the appointment artifact ("your data outlives the app" made tangible).

# Work items

- Content assembly from F06 series + F01 targets (+ a one-line F07 engine summary when available); provenance footnote ("how we got here" for every derived number).
- One-page render (PDF or share-image) in the receipt aesthetic; generated locally, shared via the system share sheet — no cloud path exists.
- Entry point: a row on the vault export surface (explicit user action only, §9).
- Golden test: fixed demo series → expected content lines.

# Gates

Render proof saved to /tmp from the emulator; golden test green; ktlint + detekt.

## References

- Spec: [F06 §5](docs/features/F06-weight-body-metrics.md) exports (one-page doctor summary) · §9 (exports are explicit user actions only; no cloud path)
- Mockup: [flows/06-reportcard-bristol-f11-f09.html](docs/design/flows/06-reportcard-bristol-f11-f09.html) — "doctor card is calm warm-tint" frame
- Companion: [F13-data-vault.md](docs/features/F13-data-vault.md) export surface hosts the entry row
