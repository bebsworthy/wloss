Owner critique of the "Today's plan" card (screenshots in chat); approved ("go ahead"):

1. One card anatomy everywhere: header "Meals · today/tomorrow" + right
   count; left block = next open meal (name, planned kcal + provenance)
   with its CTA beside it; confirmed meals are never listed - the count is
   their only trace here, the diary is their home (kills the plan-vs-diary
   duplication structurally, R-D11).
2. States: m1 morning "1 of 3 confirmed" (breakfast logged - ring already
   said 380 in; mock data made honest) + one-row diary card; m2 midday
   "2 of 3 confirmed" (was "1 of 3" while the diary showed two entries),
   lunch row dropped, dinner becomes the Next row; m3 evening Tomorrow ->
   "Meals · tomorrow | 3 planned" with the same skeleton (summary line +
   Review).
3. Naming: "plan" reserved for the F03 week plan (Plan tab, plan vN); a
   future sport schedule (F05) earns its own surface and never shares the
   meals card. Recorded as ruling R-D13 in FEATURES §3; DESIGN-SYSTEM
   component list updated.
Verify: leak scanner 0 hits; render m1/m2/m3 and check m1 still fits
without scrolling (first-screen contract).


## Executed 2026-09-11

- Unified meals-card anatomy in flow 01: m1 "Meals · today | 0 of 3" with
  Breakfast · oats & kiwi (380 kcal · planned); m2 "Meals · today | 2 of 3
  confirmed" with Dinner next (count corrected — the diary showed two
  entries while the header claimed one); m3 "Meals · tomorrow | 3 planned"
  on the same skeleton. Logged meals no longer appear in the plan card —
  the diary is their only home (R-D11 dedup).
- Data honesty: m1 re-based to pre-breakfast (ring 0 of 1,900, macros 0,
  next meal breakfast, no diary) — the old mock showed 380 kcal in the ring
  while claiming nothing was logged; the walkthrough's "diary wakes with
  the first entry" is now actually true.
- First-screen contract holds: rail bottom 748 px vs navbar top 757 px in
  the 842 px screen; the "Next ·" row prefix was dropped (the CTA carries
  it — long names stay on one line).
- R-D13 recorded in FEATURES §3; DESIGN-SYSTEM component list and F10 spec
  card name synced. Leak scanner: 0 hits.
