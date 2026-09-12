---
id: WLO-0024
title: 'M2 · Data spine + onboarding (F01, F07 cold-start)'
status: done
theme:
release:
created: 2026-09-12T10:00:53Z
modified: 2026-09-12T16:48:20Z
closed: 2026-09-12T16:48:20Z
revision: 40eb6c2d7965df83
blocks: [WLO-0025]
related: [WLO-0014]
---

# Description

**Milestone 2 (owner-approved 2026-09-12). Depends on M1 (WLO-0023).**

**Objective:** build the real data spine every feature stands on, then F01 onboarding:
a fresh install is onboarded in under 3 minutes, fully offline, and lands on a real,
Targets-backed 3-band cold-start forecast.

**Scope:** Room 3 schema (profiles, event-level measurements, day records, provenance,
consent-ledger table, EAV sidecar) with the migration harness; day-projection read API;
versioned Targets document with the sealed two-writer API; DietPlan/Recipe document
envelopes; F01 onboarding flow (Mifflin-St Jeor, plan templates as inspectable JSON,
deterministic constraint→field applier); F07 "Transparent" engine + cold-start forecast
as pure Kotlin with golden tests.

**Relevant documentation:** `docs/features/F01-onboarding-diet-plans.md`,
`docs/features/F07-energy-engine.md`, `docs/features/F13-data-vault.md` §3 (schema),
`FEATURES.md` Appendix A (Targets schema + invariants + day projection),
`docs/tech/ARCHITECTURE.md` §2.4 (patterns); rulings R-A1/A3/A5/A6, R-B2, R-B8, R-B9,
R-S10, R-U13.
**Full plan & acceptance criteria:** see this ticket's spec.
