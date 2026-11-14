---
id: WLO-5zrh
was: WLO-0027

title: 'M5 · Plan → shop pipeline (F03, F04)'

status: done
closed: 2026-09-13T06:56:36Z
status_changed: 2026-09-13T06:56:36Z

theme:

release:

created: 2026-09-12T10:02:19Z

blocks: [WLO-jgvj]

related: [WLO-a2rd, WLO-yt93]

parent:

rank:
---

# Description

**Milestone 5 (owner-approved 2026-09-12). Depends on M4 (WLO-4vr6).**

**Objective:** the plan→shop differentiator loop: deterministic meal planning (no AI —
R-S10 keeps v1 deterministic), week plans, and a unit-aware shopping list with pantry.

**Scope:** F03 planner engine (filter → macro-fit → greedy + pairwise refinement →
leftover slotting) with ≤100 ms live re-runs; ~50 CC0 seed recipes (R-S3); plan slots
projecting into the F02 diary (R-B1); F04 unit-aware list consolidation, aisle engine
v1, check-off, pantry, exports.

**Relevant documentation:** `docs/features/F03-meal-planning.md`,
`docs/features/F04-shopping-pantry.md`, `docs/design/` flows (plan-shop prototype);
rulings R-B1/B3/B4, R-S3/S5/S7/S8, R-U15.
**Full plan & acceptance criteria:** see this ticket's spec.
