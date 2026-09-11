## Deliverables
- docs/features/TEMPLATE.md — shared spec template (identity, purpose, moments, mechanics, interaction model, outputs, motivation, relations, blue sky, guardrails, open questions)
- docs/features/F01..F13 — 13 feature specs (~2,600 lines total), written by 5 parallel agents from objectives + research
- docs/features/FEATURES.md — master doc: feature map, Day/Week loops, signature flows, consent matrix, 34 frozen rulings (R-*), per-doc review verdicts, v1 roll-up, open questions

## Key architecture frozen
- Data spine: Day record (F02 owns diary/day-status), versioned Targets doc (F01 creates, F07 adapts Apply-only), Provenance, held-gating, Consent+Receipt.
- Week loop: F07 Sunday check-in -> Apply -> F01 plan version -> F03 generate -> F04 list.
- Consent: six frozen AI categories + F13-governed food-DB toggle; F05/F06/F07 are AI-free by design; GPLv3 license (R-S1) for openScale driver reuse.
