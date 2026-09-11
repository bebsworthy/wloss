Owner follow-up to the "adaptive" walkthrough question; approved ("go"):

- Ring/plan-target chips stop naming the mechanism and state the user fact:
  "adaptive · check-in Sep 8" -> "updates weekly · Sep 8" (same pattern for
  Sep 6 / Sep 13 variants, flows 01/02/03/05).
- The engine word "adaptive" retires to the explainer's first sentence
  ("your budget is adaptive - recalculated at each check-in from your
  measured burn and weight trend"), documented in flow 01 pin 3.
- Re-stamp helper in flow 03 m4 quotes the new short form ("Sep 6" -> "Sep 13").
- Governing docs amended: FEATURES R-D11 example chip + R-D12 sentence
  (chip words name user facts, not mechanisms); DESIGN-SYSTEM 7.1 chip row.
- Held fallback unchanged: "provisional" still names the held state.
Verify: leak scanner 0 hits; re-render affected frames.


## Executed 2026-09-11

- Chips now read "updates weekly · Sep 8 / Sep 6 / Sep 13" in flows 01 (4),
  02 (1), 03 (2), 05 (2); re-stamp helper in 03 m4 quotes the short form.
- "adaptive" retired to the explainer first sentence; recorded in flow 01
  pin 3, FEATURES R-D11 example + R-D12 sentence, DESIGN-SYSTEM 7.1 chip row.
- Held fallback "provisional" unchanged. wf-box engine language kept (spec
  by design). Leak scanner: 0 hits. 4 frames re-rendered and inspected;
  pin 16 (flow 03) nudged off the ledger text.
