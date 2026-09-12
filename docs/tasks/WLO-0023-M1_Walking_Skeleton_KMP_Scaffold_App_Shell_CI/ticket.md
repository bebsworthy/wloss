---
id: WLO-0023
title: 'M1 · Walking skeleton: KMP scaffold, app shell, CI'
status: done
theme:
release:
created: 2026-09-12T10:00:24Z
modified: 2026-09-12T12:43:43Z
closed: 2026-09-12T12:43:43Z
revision: 46bc7ee20f53f27f
blocks: [WLO-0024]
related: [WLO-0014]
---

# Description

**Milestone 1 of the implementation plan (owner-approved 2026-09-12).**

**Objective:** stand up the real project skeleton so that every later milestone lands on
enforced architecture, and ship a launching app shell on the emulator (dark theme,
five-tab nav, provenance chip) with green CI.

**Scope:** ratify ADR-005 (module graph) and the app id; Gradle KMP multi-module scaffold
per ARCHITECTURE.md §2 (lean subset); build-logic conventions + version catalogs;
Room 3 / DataStore / Koin / kotlinx-serialization wired with the document house rules;
JVM purity target; `checkArchitecture` + merged-manifest checks (rules D1–D8); Compose
shell per DESIGN-SYSTEM (R-D1 dark-first, R-D2 five tabs, Inter tnum, haptics wrapper);
CI pipeline (build, unit, lint) running on the API 29 AVD constraint (emulator-only).

**Depends on:** nothing (foundation decisions complete — ADR-001…004).
**Full plan & acceptance criteria:** see this ticket's spec.
