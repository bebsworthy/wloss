## Execution record (2026-09-12)

Owner pivot mid-round: "a general sport tracker is non-negotiable; cardio is
just as important as strength training." Round re-scoped accordingly —
spec first, then mock.

**Spec amendments**
- FEATURES §3 R-S11 amended: cardio first-class, co-equal with strength;
  native live sessions (timer, on-device GPS distance, HR zones via F13)
  with pace/split/zone summaries are v1; the minimal manual entry and HC
  import remain as fallback/complement. F05 feature-map row updated.
- F05 rewritten: identity/Provides ("general movement tracker"), new core
  objective (cardio co-equal), cardio contexts in §2 + "the cardio twin"
  flow, cardio session engine in §3 (GPS on-device, auto-pause, HR via F13,
  split/zone formulas shown), "Cardio & steps (first-class)" section
  replacing "second-class by design", cardio happy path in §4, pace/zone/
  GPS provenance formulas in §5, per-activity pace/distance visualizations,
  location-stays-home guardrail in §9 (routes never in notifications,
  excluded from share receipts by default), §8/[future] reshuffle,
  §10 resolution note updated.
- DESIGN-SYSTEM §7.6: cardio component inventory added. KICKOFF item 8 +
  flows/index.html row updated.

**Flow 08 (08-exercise-f05.html, new)**
- 4 wireframes: Hub entry (lift + cardio day variants), strength logging
  loop, cardio live loop, shared receipt summary.
- 5 strength mocks: session preview (engine adjustments as sentences,
  editable), live squat (prefilled sets, rest ring, PR banner e1RM
  126.7→129.8, RPE chips), honest plate fallback ("Closest possible:
  130.0 kg"), ranked substitutions, receipt summary (12,580 kg tonnage,
  expenditure est. 310 kcal ±30% — context only, never eat-back).
- 2 cardio mocks (owner ruling stage): live outdoor run (elapsed hero,
  GPS distance/pace/zone with inline provenance, route trace on-device,
  auto-pause) pins 15-17; week timeline + manual "Log activity" sheet
  (3-field fallback, HC imports provenance-stamped on one timeline,
  steps never logged) pins 18-20.
- Self-review updated: cardio parity coverage, location privacy bar,
  cross-flow notes; deliberate omissions listed.

**Verification**: leak scanner 52 phones / 0 hits; every mock captured and
inspected in the browser (three pin-placement fixes and two run-on
provenance lines repaired during review); persona consistent with flows
01/03/04/06 (kg, Sep 8-12 week, 81.x kg, Lower A Tue + Sat).
