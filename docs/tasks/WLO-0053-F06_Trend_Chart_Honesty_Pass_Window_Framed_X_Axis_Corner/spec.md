## Context

Trend-block review (2026-09-14, screenshots attached as assets) of the F06
trend card — `WeightScreen.kt` (`f06-trend-card`) → `WloTrendChart.kt` →
`WeighInViewModel.reload()`. Three screenshots, same run, only the window
chip changed, and the block renders three different failures:

- **90 d and 1 y are pixel-identical.** The x-axis is anchored to the DATA's
  extent — `WloTrendChart.kt:157-161` maps x over `[firstDay..lastDay]` of
  samples/trend — not the selected window. Window chips only filter
  (`WeighInViewModel.kt:482`); they never frame. Seven dots spanning
  1–8 Aug stretch edge-to-edge in both windows, so switching intervals
  visibly does nothing.
- **30 d renders a ~160 dp void.** The window [16 Aug–14 Sep] holds no rows
  (seed data ends 8 Aug — rows stamped under M1's frozen DEMO_NOW, i.e. the
  WLO-0049 residue), so `samples` is empty and `drawWeightChart`
  early-returns (`WloTrendChart.kt:142`) while the Canvas keeps its fixed
  160 dp height (`WloTrendChart.kt:87`). No in-chart message; the corner
  labels and the date caption vanish too.
- **"78.3 kg" appears twice.** The top-right numeral is defined as the
  LATEST trend value but falls back to `maxV` when the trend list is empty
  (`WloTrendChart.kt:238`) — silently becoming "max", duplicating the
  top-left scale label. Reads as "start = end, no change", which is false.

Underlying gate defect: `trendLineVisible` counts scalars in
`[today-6, today]` (`WeighInViewModel.kt:616-617`). With data ending 8 Aug
and today = 14 Sep the count is 0, so a user with 8 days of history gets
dots with no line and the copy "Keep weighing — the trend forms in a few
days" — false on both halves: they DID weigh, and the trend over 1–8 Aug is
computable. The gate (F06 §4: "≥ 3 points in the trailing 7 days") conflates
"new user warming up" with "lapsed user", stays closed after any ≥2-week
break until 3 fresh days exist, and punishes the sheet's own back-dating.

This ticket makes the chart honest about time, values, and emptiness. The
smoother tuner + preview mechanics are WLO-0054; ribbon scrubber and
compare-modes stay in WLO-0042.

## Scope

### A. Window-framed x-axis (the chart must not lie about time)
1. x-domain spans the SELECTED window: `[from..today]` for 30/90/1 y;
   `[firstSample..today]` for all. Old data clusters left — honestly.
2. Sparse x ticks beneath the canvas (month labels; ~3 ticks at 90 d, ~12 at
   1 y, adaptive for all; 30 d may label week starts) in `textTertiary`
   11 sp tabular, same chrome as the axis numerals. Chart height budget may
   grow by the tick lane — keep the total card delta small.
3. Date caption becomes the WINDOW range ("16 Aug – 14 Sep 2026"), with the
   data extent appended when it does not reach today ("· data 1–8 Aug").
   The year renders whenever the range crosses (or could cross) a year
   boundary — 1 y and all always carry it.

### B. Corner numerals + scale (the scale must not lie about values)
4. Kill the `?: maxV` fallback (`WloTrendChart.kt:238`). A "latest" numeral
   renders only from a real latest value and never equals a neighboring
   label's number (guard + unit test).
5. Owner question pending (q-2): drop the top-right numeral entirely —
   high/low only on the canvas, "latest" stays the hero card's job
   (recommended) — or bind it to the latest SAMPLE when the trend is hidden.
6. Nice-tick scale: round hi/lo to sane steps for the visible span (≤1 kg
   span → 0.1 steps; larger spans → 0.5/1) instead of `maxV ± 8 %` raw.
   Fix `format1` truncation for AXIS labels only (`WloFormat.kt:13` floors
   via `.toLong()`: a gridline at 77.544 currently labels "77.6"): add a
   rounding variant for the chart; receipt/logbook formatting unchanged.
7. Collision guard: corner numerals never overlap on narrow widths (measure
   before draw; drop the right-hand label first).

### C. Empty + sparse states
8. Empty window: compact message INSIDE the chart bounds naming the window
   and where the data went, with a one-tap jump to the smallest window that
   has data — "No weigh-ins in the last 30 days · latest entries 1–8 Aug →
   Show 90 d". Reserved height shrinks (no 160 dp void); the window caption
   stays. The smoother tuner hides while the chart is empty (hiding rule
   defined here; the tuner's own rework is WLO-0054).
9. State-specific copy, replacing the single "Keep weighing…" string
   (which also serves as the canvas `describe` today — the two must keep
   mirroring):
   - warm-up (few points, line not yet allowed): "Keep weighing — the trend
     forms in a few days."
   - lapsed (data stale, line gated off): "Last entry 8 Aug — the trend
     resumes when you do."
   - empty window: the §C.8 message.

### D. Trend gate re-scope (F06 §4 — owner question pending, q-1)
10. Gate the line on the DISPLAYED data, not today: render when the window
    holds ≥3 daily scalars (recommended), restoring the line for back-filled
    and lapsed history while keeping the warm-up rule for genuinely new
    users. Copy per §C.9.
11. Amend F06 §4's gate sentence in
    `docs/features/F06-weight-body-metrics.md` to the approved wording; rename/adjust
    `TREND_GATE_POINTS` / `TREND_GATE_WINDOW_DAYS` in `WeighInViewModel`
    accordingly; update the `trendLineVisible` derivation and its tests.

### E. Consistency + reach
12. The dashed 30-days-ago reference line gates identically to the trend
    line — today `reference` is computed regardless of the gate
    (`WeighInViewModel.kt:595-599`) and the chart draws it alone
    (`WloTrendChart.kt:170-177`). Never a floating chrome line.
13. Chart a11y floor: canvas semantics carry the real state — window,
    point count, high/low/latest, and the §C.9 state copy as one summarized
    description; values must be real numbers, not static boilerplate.

## Out of scope

- Smoother tuner mechanics, α slider, preview banner, reload coalescing —
  WLO-0054.
- Progress-ribbon thickness scrubber, compare-modes overlay, 10-day-best —
  WLO-0042 (keep the `reference` parameter shape so its ribbon input
  survives).
- Long-press back-fill on the chart (DESIGN-SYSTEM F06 row) — entry UX that
  needs its own ticket; not here.
- Tap/scrub per-dot readout — candidate after WLO-0042 lands its overlay;
  not here.
- Demo-data reseeding (the screenshots' staleness is stale seed stamps, not
  a code bug).

## Tests

- `WloTrendChart` geometry: x-mapping pins window edges (first dot ≠ left
  edge when data is old; today = right edge); corner no-duplicate + no-fallback
  + collision; nice-tick rounding table.
- ViewModel: gate re-scope (lapsed data yields trend points in-window;
  <3 scalars stays dots-only), state-specific copy selection, reference
  gating parity.
- Caption strings: window range + data-extent suffix + year rules.

## Acceptance criteria

- 30 d / 90 d / 1 y produce visibly different charts for the same data.
- A window with no rows shows the in-chart message + jump action, never a
  160 dp void; the tuner is hidden there.
- The same number never renders in two labels; "latest" only ever renders
  from a real value.
- Lapsed/back-filled history shows the trend line (per the gate ruling) or
  the lapsed copy — never the warm-up line to someone who already has data.
- Axis numerals match their gridline values (rounded, not floored).
- Caption reads the window range; data extent + year appended per rules.
- The reference line never renders without the trend line.
- TalkBack announces window, point count, range, and the state copy.
