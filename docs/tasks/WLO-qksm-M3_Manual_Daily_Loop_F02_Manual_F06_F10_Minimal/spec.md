# M3 — Manual daily loop

## Objectives
1. F02 manual-first logging: the complete non-AI input ladder (text hint → search),
   custom foods, portions, diary.
2. F06: event-level weigh-ins with smoothing and honest provenance.
3. F10 minimal Hub: the day loop exists — cards, ordering, deep links.

## Contents
- FTS5 food index (Room 3 `@Fts5`) over the food catalog + custom foods; fuzzy
  multi-keyword search v1 (exact + prefix; learned aliases/embeddings later per F02 §8).
- Diary: meal slots, per-entry quantity/unit (unit converter, R-D10), entry provenance
  (manual = `measured`-user), edit/delete with audit; day-status chip.
- Energy budget read-out from the M2 day-projection API (consumed vs target).
- F06: weigh-in sheet (lowest-of-day semantics), multiple events/day kept verbatim
  (R-B8), EWMA α=0.15 default with visible tuner (R-A2), zero-phase + 7-day MA options
  with in-app math documentation, ±3σ outlier guard → `held` flags, body-fat method
  registry (US Navy/RFM formulas; impedance decoders later).
- F10 Hub: Day Model time-of-day state machine (fixed rules v1), card order per spec,
  trend + budget cards render from projections, `wlo://` registry (19+ routes stubbed to
  real targets where they exist), notification permission NOT yet requested.
- Charts: Vico line/bar + the shared month-heatmap component (R-D4) behind feature flags
  for F05/F09 later reuse.

## Relevant documentation
- `docs/features/F02-food-logging.md` §3 (input ladder), §5 (sanity rails — manual-safe)
- `docs/features/F06-weight-body-metrics.md` §3–4, R-A2; `docs/features/F10-daily-hub.md` §3–4
- `docs/design/IA.md` §2–3 (nav + deep links); `docs/design/DESIGN-SYSTEM.md` §6
- Rulings: R-U15, R-B4 (one definition per number), R-B7 (Fresh Start hide-not-delete —
  ledger fields now), R-D4, R-D5 (CVD-safe deltas), R-D10, R-U13

## Acceptance criteria
1. A 7-day simulated diary (seeded test data) renders correctly: budget, trend, heatmaps.
2. Food search returns results <50 ms for 1k items on the emulator (FTS5).
3. Weigh-in twice in one day → both events stored; trend chart unchanged by intra-day
   noise; smoothing switchable with documented math screens.
4. Every deep link in the IA registry resolves (real screen or documented stub).
5. Corrections/change history visible per entry (provenance sheet).
