---
id: WLO-0025
title: 'M3 · Manual daily loop (F02 manual, F06, F10 minimal)'
status: done
theme:
release:
created: 2026-09-12T10:01:21Z
modified: 2026-09-12T20:39:57Z
closed: 2026-09-12T20:39:57Z
revision: 44c8fd9aed5409d5
blocks: [WLO-0026]
related: [WLO-0014]
---

# Description

**Milestone 3 (owner-approved 2026-09-12). Depends on M2 (WLO-0024).**

**Objective:** make the app daily-usable offline: hand-entered food diary, weight
tracking with trend, and the minimal Day Hub that ties them together. Manual paths are
built FIRST by design — they are the equal-status fallback every later AI assist must
have (R-U15).

**Scope:** F02 manual logging (FTS5 food search, custom foods with
archive-don't-delete, portions, diary day view, corrections with provenance); F06
weigh-in flow (multi-weigh-in days, EWMA trend R-A2 + the three documented smoothers,
outlier guard, body-fat registry stub); F10 minimal Hub (Day Model state machine,
weight+budget cards, `wlo://` deep-link registry); Vico charts per R-D4.

**Relevant documentation:** `docs/features/F02-food-logging.md` (§3 manual tiers),
`docs/features/F06-weight-body-metrics.md`, `docs/features/F10-daily-hub.md` §3,
`docs/design/IA.md` (day loop), `docs/design/DESIGN-SYSTEM.md` §6 (charts);
rulings R-U15, R-A2, R-B8, R-B4, R-D4, R-D5, R-D10; DECISION-SPACE T-C5 (FTS5).
**Full plan & acceptance criteria:** see this ticket's spec.
