## Fix round from the full-screen browser review (2026-09-11)

All 88 frames in `docs/design/flows/` were captured and reviewed individually;
findings + rationale live in `docs/design/flows/REVIEW.md` §2. This ticket
executed §3. Resolution notes in REVIEW.md §3 preamble.

## P0 — correctness — DONE
- [x] Ghost numerals on ring cards: root cause = `.ring-big` defined only in flow 01's local CSS → rings collapsed to 0×0 in flows 02/03. Promoted to `_wlo.css`.
- [x] Offline screen luminance (02 m5): root cause = `.sheet` painted under `.sheet-wrap`'s 60% overlay → `z-index: 45`. Full brightness verified.
- [x] Truncated copy: 02 m4 explainer completed; 05 m3 header; 01 m1 report-ready pill (icon chip); 04 m4 caption (chart trims).
- [x] Persona/unit story: lb across 01–06 (kg/wk→lb/wk, "92.1"→"178.6", guard "9.2 lb", share "−9.2 lb"); flow 07 documented as distinct metric onboarding persona; flow 02 meta documents the noon branch.

## P1 — compliance & comprehension — DONE
- [x] Archive nav icon → R-D9 stacked-outline glyph (`.ic-arch`) in all navs; F08 caption updated.
- [x] Check-in card 390 dp density pass (03 m2): engine chip relocated, chips unstacked, nowrap on value rows, "on pace" into label. Frozen row order untouched.
- [x] Bottom-clip fix: navbar pinned absolutely (no floating-nav void), 96px scroll clearance, per-frame trims (01 m1 rail fits; 02 m6; 03 m5/m6; 04 m4; 06 m3/m6; 07 m7 ghost button).
- [x] Copy: 01 m4 ring wording ← 03 m2; 01 m3 "food sum" label; nudge timeline chained to flow 02 (12:41 → 12:51 → 12:58).
- [x] Pantry badge "127" moved from Digestion to the Plan tab (05 m5).

## P2 — polish — DONE
- [x] ~25 pins moved off text onto whitespace/card corners.
- [x] Sparse frames: 01 m3 diary slice; 01 m5 wallpaper band + page dots; 02 m1/m2 plate-bowl viewfinder scene (glass blob dropped).
- [x] Amber audit: 04 m5 lock → neutral SVG; 06 m6 doctor chip → neutral (edge stays).
- [x] Flow 07: High-Protein C/F values; wall as slim push-state strip; quiz card kept readable (translateX −58→−24); ladder bands monotonic; "shimmer" watermark removed.
- [x] 04 m8 on-screen marker legend (★ / dashed / dot / faint dots).

## Review corrections (§2 artifacts)
- 02 m6 "Log again · 2 tap" — code said "1 tap" (capture misread).
- 05 m1 "AIry influence" — actually "pantry influence" under a pin.

## Acceptance
- [x] Every changed frame re-rendered and inspected in the browser: no overlap, no clipped key content, no truncation.
- [x] Design-bar sweep still passes (provenance, tone, no-red, R-U7/U13/U15/U16).
- [x] REVIEW.md §2 findings each resolved or corrected as artifacts (see §3 preamble).
