# M5 — Plan → shop pipeline

## Objectives
1. F03 deterministic planner engine (pure Kotlin, golden-tested, ≤100 ms live re-runs).
2. F04 shopping list generation with unit-aware consolidation + pantry basics.
3. The canonical item space shared by F03/F04 (single owner: `:core:model`).

## Contents
- Planner: filter (diet/rules/exclusions) → macro-fit scoring → greedy + pairwise
  refinement → leftover slotting; versioned recipes; plan slot state machine projecting
  into the F02 day record (R-B1: `planned/replaced` links); adherence metrics defined
  here, consumed by F11 later (R-B4); fit-badge ±5% (R-S7).
- Seed content: ~50 CC0 recipes authored per R-S3 (content pipeline: JSON in the
  recipe schema, size-normalized).
- Live UI: week grid, slot swap/replace with debounced re-deal (≤100 ms budget —
  Macrobenchmark on emulator as regression baseline, absolute number deferred per
  WLO-0014).
- F04: plan→list expansion with unit-aware consolidation + delta reconciliation
  (servings-change prototype — FEATURES §6 item 8); aisle engine v1 (shipped taxonomy +
  learned per-item corrections); check-off, partial-stock deduction default-off (R-S5);
  pantry stock levels; list export text/CSV/JSON.
- Pantry check-in reuses the M4 barcode port.

## Relevant documentation
- `docs/features/F03-meal-planning.md` (§3 engine, §4 UI, §8), `docs/features/F04-shopping-pantry.md`
- `docs/design/` plan-shop flow prototype (flows/05)
- Rulings: R-B1, R-B3 (fiber target), R-B4, R-S3, R-S5, R-S7, R-S8 (6-tag FODMAP v1),
  R-U15 (manual editing everywhere)
- DECISION-SPACE: canonical item space (§2.5 DRY anchors), T-E7 embeddings deferred

## Acceptance criteria
1. From any M2 template, "plan week" produces a macro-fitting plan <30 s; slot edits
   re-deal <100 ms (emulator Macrobenchmark regression-tracked).
2. List generation <1 s, unit-consolidated ("300 g rice + 200 g rice → 500 g");
   servings change reconciles deltas.
3. Pantry deduction + check-off flows work offline; export round-trips (import own CSV).
4. Golden tests: planner scenarios (families of constraints) + property tests on
   consolidation math.
5. Planned-slot → diary replacement preserves provenance links (R-B1) — verified by test.
