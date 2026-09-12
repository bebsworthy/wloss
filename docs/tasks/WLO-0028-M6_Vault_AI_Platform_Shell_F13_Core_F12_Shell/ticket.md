---
id: WLO-0028
title: 'M6 · Vault + AI platform shell (F13 core, F12 shell)'
status: todo
theme:
release:
created: 2026-09-12T10:02:49Z
modified: 2026-09-12T10:02:51Z
closed:
revision: 7a2477ac746d8df9
blocks: []
related: [WLO-0014]
---

# Description

**Milestone 6 (owner-approved 2026-09-12). Depends on M5 (WLO-0027).**

**Objective:** make the trust promises real and visible: "your data outlives the app"
(auto-backup, staged restore, versioned exports) and the AI platform's consent
machinery (ledger, receipts, model manager, egress monitor) — even though cloud BYOK
calls themselves are v1.x.

**Scope:** F13 core: SAF auto-backup (AES-GCM passphrase-encrypted, R-U5, rotation 7),
staged-and-validated restore, export bundle v1 (JSON + CSV, schema_version,
secret-blanking), biometric app-lock + FLAG_SECURE, storage dashboard; F12 shell:
AI Studio settings, consent toggles on the M2 ledger, receipt log viewer, model manager
UI, debug egress monitor; ACRA research spike (opt-in content-free, T-K4) lands here.

**Relevant documentation:** `docs/features/F13-data-vault.md` (§3, §9 invariants),
`docs/features/F12-ai-platform.md` §3–4, `docs/design/IA.md` (settings surfaces);
rulings R-U5, R-U7, R-U18, R-B9; DECISION-SPACE T-F1–F4, T-K4, T-C3, T-C6;
ADR-002 Play-policy flags (release checklist, not this milestone's blocker).
**Full plan & acceptance criteria:** see this ticket's spec.
