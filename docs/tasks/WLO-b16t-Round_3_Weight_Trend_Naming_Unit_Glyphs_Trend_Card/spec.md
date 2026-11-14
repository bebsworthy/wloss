Owner walkthrough of 01-day-loop-f10.html (see chat) surfaced four fixes; owner approved ("go"):

1. Rename "Trend" → "Weight trend" everywhere (flow 01 cards, flow 03 check-in
   rows + hero, flow 04 history apphead + confirmation labels, flow 06 dimmed
   Hub cards). The engine trend is weight-specific (F06/F07); only weight has
   a Trend — calories = ring/left, burn = Measured burn, silhouette = cm deltas.
2. Units on numbers (extends R-D10): heroes render "81.2 kg" with a small
   settings-driven unit glyph; deltas "↓ 0.3 kg"; bare weight receipts get kg.
   kcal rows keep receipt style where the card establishes the unit once.
3. Trend card keeps its 36 px sparkline in every state (m2 regains the graph;
   visual continuity over height economy).
4. Check-in day dedup (R-D11): check-in card keeps number + pace; the trend
   card below becomes series-only (graph, no duplicate number).

Record as ruling R-D12 in FEATURES §3 (naming + units). Verify: leak scanner
0 hits; re-render affected frames.


## Executed 2026-09-11

- "Weight trend" rename applied in flows 01 (3 card titles + check-in row),
  03 (3 check-in rows + hero card), 04 (history apphead, 3 confirmation
  labels), 06 (2 dimmed Hub cards).
- Unit glyphs: .unit token in _wlo.css; heroes/deltas now render 81.2 kg /
  ↓ 0.3 kg etc. across flows 01/03/04/06; bare weight receipts got kg
  (Aug 12 · 82.5 kg, 10-day best 80.9 kg, raw 80.9/81.4/85.4 kg).
- m2 trend card regained its 36 px sparkline; m4 check-in day trend card is
  series-only (graph + "the series · today's number up top"), number lives
  once in the check-in card (R-D11 dedup).
- R-D12 recorded in FEATURES §3.
- Leak scanner: 0 hits. Re-rendered 10 frames in-browser and inspected;
  two follow-ups fixed (03 check-in row wrap, 04 pin 6 overlap); 06 dimmed
  title "clipping" confirmed as opacity artifact, markup fine.
