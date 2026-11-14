# UI/UX phase (per docs/design/KICKOFF.md)

Research and design WLO v1's UI/UX: turn the 13 feature specs into a design
system, an information architecture, and prototyped key flows. Documents and
static HTML prototypes only — no Compose code.

## Deliverables
1. **Phase A** — `docs/design/research/synthesis.md`: UI-level patterns mined
   from `docs/research/*.md` (patterns + anti-patterns, cited) + external
   research (M3 deviations for density, dynamic color, number typography,
   data-viz, haptics, charting, Glance) mapped pattern → source → WLO surface.
2. **Phase B** — `docs/design/DESIGN-SYSTEM.md` (tokens: type/color/spacing/
   motion/haptics; consolidated motion table mined from all spec micro-
   interaction sections; component inventory swept against all 13 specs) and
   `docs/design/IA.md` (nav model, surface map, deep links, widget, R-U7
   placement, first-run).
3. **Phase C** — `docs/design/flows/`: 7 flow prototypes (390×844 static-first
   HTML/CSS phone frames): Day loop F10 · photo ladder F02 · Sunday check-in
   F07→F01 · weigh-in+trend F06 (+F08 lock gate) · plan→shop F03/F04 · report
   card+Bristol F11/F09 · onboarding F01. Each: wireframe → annotated mock →
   self-review vs design bar.
4. AGENTS.md doc-layout update; git commits per milestone.

## Acceptance criteria (= KICKOFF definition of done)
- [ ] Research synthesis written; ruling collisions escalated as owner questions.
- [ ] DESIGN-SYSTEM.md with tokens + consolidated motion/haptics table.
- [ ] Component inventory complete vs all 13 specs.
- [ ] IA.md with nav model + surface map.
- [ ] 7 flow prototypes pass design-bar checklist (provenance chips, tone,
      no-red, R-U7 naming, R-U13 no-gap-shaming, R-U15 manual paths).
- [ ] Owner questions batched via `question add` (never poll).
- [ ] Milestones committed.


## Delivery status (2026-09-11)

- **Phase A** committed (197f272): `docs/design/research/synthesis.md` — 57 cited
  UI patterns + anti-pattern list; ruling-collision log; platform research.
- **Phase B** committed (b76fe6a): `docs/design/DESIGN-SYSTEM.md` (tokens,
  consolidated motion/haptics table, full component inventory) + `IA.md`.
- **Phase C** committed (ed53113): `docs/design/flows/` — 7 prototypes, each
  wireframe → annotated mock → self-review; design-bar sweep clean.
- **Blocked on owner:** 9 open questions (q-000011…q-000019): six R-D* freezing
  proposals, badge direction, F09 pain input, Archive icon. R-D* entries are
  recorded in FEATURES §3 only after answers; ticket remains `doing` until then.

## Close-out (2026-09-11)

- All 9 questions answered: each question's recommended option (suggestion 1)
  was approved. R-D1–R-D9 are recorded in FEATURES.md §3 "Design"; FEATURES §6
  item 7 marked resolved; F09's pain-input and F08's icon open items now point
  at their rulings.
- Design docs updated from "proposed/pending" to ratified (synthesis §4,
  DESIGN-SYSTEM, IA §1, flows/index).
- KICKOFF definition of done: all items met. Ticket moved to done.
