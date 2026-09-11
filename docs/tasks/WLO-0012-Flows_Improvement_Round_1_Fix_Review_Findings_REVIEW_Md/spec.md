## Fix round from the full-screen browser review (2026-09-11)

All 88 frames in `docs/design/flows/` were captured and reviewed individually;
findings + rationale live in `docs/design/flows/REVIEW.md` §2. This ticket
executes §3. Do not redesign what the review marked as strengths.

## P0 — correctness
- [ ] Ghost-numeral overlap on ring cards (02 m6, 03 m2/m4/m5): single hero layer.
- [ ] Offline screen luminance (02 m5): full theme brightness; re-place pins.
- [ ] Truncated copy: 02 m4 explainer; 05 m3 header; 01 m1 report-ready pill; 04 m4 logbook caption.
- [ ] One persona/unit story across flows (lb vs kg split; stray "92.1" in 04 m1; budget reconciliation 02 vs 01; localize 04 m3 guard to lb).

## P1 — compliance & comprehension
- [ ] Archive nav icon → R-D9 stacked-outline glyph in all navs/surfaces.
- [ ] Check-in card 390 dp density pass (03 m2) + "on pace" collision (03 m1/m2). Frozen row order untouched.
- [ ] Systemic bottom-clip fix in `_wlo.css` (nav underlap fade + scroll padding); audit ~13 frames; restore quick-action rail in 01 m1 (first-screen contract).
- [ ] Copy: "AIry influence"→"AI influence" (05 m1); "Log again · 2 tap"→"1 tap" (02 m6); 01 m4 ring wording ← 03 m2; label 01 m3 "1,880" as plan sum.
- [ ] Pantry badge "127" off the Digestion tab (05 m5).

## P2 — polish ("majestic" pass)
- [ ] Pin discipline: ~20 pins overlap content — whitespace or leader lines, never on glyphs/numerals.
- [ ] Sparse-frame composition: 01 m3/m5, 02 m5 bottom halves.
- [ ] Amber budget audit (06 m6 ×4; 04 m5 lock glyph → neutral).
- [ ] Viewfinder food composition (02 m1); widget wallpaper hint (01 m5).
- [ ] Flow 07 nits: High-Protein macro values; wall card push-state only; quiz crop; ladder band sort; Studio ghost clip; "shimmer" watermark.
- [ ] 04 m8: legend for dashed cell + amber dot (or drop).

## Acceptance
- [ ] Re-render every changed frame; no overlap, no clipped content, no truncation.
- [ ] Design-bar sweep still passes (provenance, tone, no-red, R-U7/U13/U15/U16).
- [ ] REVIEW.md §2 findings each marked resolved or consciously deferred.
