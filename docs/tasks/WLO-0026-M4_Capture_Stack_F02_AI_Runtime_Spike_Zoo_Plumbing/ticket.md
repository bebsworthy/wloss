---
id: WLO-0026
title: 'M4 · Capture stack (F02 AI): runtime spike + zoo plumbing'
status: todo
theme:
release:
created: 2026-09-12T10:01:53Z
modified: 2026-09-12T10:01:55Z
closed:
revision: d8b88aa452c48375
blocks: [WLO-0027]
related: [WLO-0014]
---

# Description

**Milestone 4 (owner-approved 2026-09-12). Depends on M3 (WLO-0025). Opens with the
parked just-in-time research spike (DECISION-SPACE §6 exceptions).**

**Objective:** photo-first logging works on the emulator, riding the consent-shaped
infrastructure: on-device analyzer behind `:core:ports`, the R-S14 model zoo (download,
verify, account, reclaim), ML Kit barcode/OCR with their data-flow audit cards, and the
editable-before-save correction loop.

**Scope:** T-E1 runtime decision (spike), T-E2 food-model benchmark + adoption, ML Kit
Barcode/OCR adoption + audit cards (ZXing fallback behind the same ports), CameraX
pipelines with EXIF strip + discard-at-save (R-U14), correction loop with confidence +
`held` handling, model-independent sanity rails, OFF lookup as the one audited
allowance path (R-C4).

**Relevant documentation:** `docs/features/F02-food-logging.md` §3 (capture tiers),
`docs/features/F12-ai-platform.md` §3.2/§3.6/§3.7/§3.8, `docs/features/F13-data-vault.md`
§9 (egress invariants); rulings R-S12, R-S13, R-S14, R-U14, R-U15, R-C4;
DECISION-SPACE T-E1/E2/E4/E8, C5 audit-card rule; emulator-only constraints (WLO-0014):
spike numbers are functional verification, not perf proof.
**Full plan & acceptance criteria:** see this ticket's spec.
