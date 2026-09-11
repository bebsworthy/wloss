## Round 2 — copy & density amelioration (owner pass, 2026-09-11)

Plan: `docs/design/flows/REVIEW.md` §4. Rulings R-D10 (metric default) and
R-D11 (copy discipline) already ratified into FEATURES §3; DESIGN-SYSTEM §7.1
provenance anatomy + §8 system-leakage bans updated. Execute on owner go-ahead.

## P0 — R-D10 kg conversion (mechanical; §4.1 table)
- [ ] Main persona lb → kg across flows 01–06 (trend 81.2, raw 80.9, weekly −0.6 kg/wk, ribbon −1.3 kg, outlier 85.4/"4.5 kg", share −4.2 kg, formula → Δtrend kg × 7,700 ÷ 7; "kg mirror" line deleted; unit chip → kg).

## P1 — R-D11 copy discipline
- [ ] Provenance ⓘ affordance (`.prov .q`); "derived/AI-estimated/adaptive ⓘ" everywhere; parameters (EWMA α, window, recognizer vX, transparent-v1, F13) only in how-we-got-here explainers.
- [ ] Owner's four (01 m1): "leads until logged" removed; weigh-card yesterday/trend line removed; ring helper → "tap the ring for today's detail".
- [ ] Per-flow sweep per REVIEW §4.3: remove motion/haptic/spec strings from screens; rewrite the listed lines; move choreography beats (03 m1), prototype note (05 m4), meta captions (01 m5) into annotations.
- [ ] No-information-duplication pass (weigh card vs trend card; banner sub-lines; "Computed on your device" once per card).

## P2 — verify
- [ ] Re-render changed frames; leak scanner over `.screen` surfaces → zero hits.
- [ ] Design-bar sweep (provenance, tone, no-red, R-U7/U13/U15/U16) still passes.

Status: todo — plan proposed, awaiting owner go-ahead.
