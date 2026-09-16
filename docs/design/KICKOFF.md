# WLO UI/UX phase — kickoff prompt

*This file is the prompt. Start a session in the repo root and say:
"Read `docs/design/KICKOFF.md` and execute it." Everything the executing
agent needs is below — do not work from memory of past conversations.*

---

## Mission

Research and design the UI/UX for WLO's v1: turn 13 interaction-rich
functional specs into a design system, an information architecture, and
prototyped key flows — without contradicting a single frozen ruling.
Research first, then create. Deliverables are documents and prototypes,
not app code (Compose implementation is a later phase).

## Read first, in this order

1. `AGENTS.md` — workspace rules, doc layout, ticket CLI etiquette.
2. `docs/objective.md` — the product's principles and the design bar.
3. `docs/features/FEATURES.md` — §1 feature map, §2 data spine + loops
   (the Day loop §2.2 and Week loop §2.3 are the two rhythms everything
   serves), §3 rulings (binding), Appendix A (Targets schema).
4. `docs/research/synthesis.md` — the "why" behind the feature set.
5. Feature specs on demand — every spec's §4 (User Interaction Model) and
   §8 (Blue Sky version tags) is UI source-of-truth. Especially:
   - **F07 §4** — the check-in card anatomy is *frozen, top to bottom*.
   - **F10** — the Hub is the home surface; the Nudge Contract is app-wide.
   - **F02 §4** — the correction loop is the hero flow of the whole app.
   - **F06 §4** — the trend-first weigh-in moment (highest emotional tension).
   - **F08/F09 §4 + §9** — discretion rules (naming, locks, FLAG_SECURE).

## The design bar (non-negotiables — check every artifact against these)

- **Minimalist, dense with stats, rich micro-interactions, zero
  guilt/shame copy.** Density is a feature for this audience; whitespace
  is spent deliberately, not by default.
- **Provenance chips are mandatory**: "a number rendered without its chip
  is a spec bug" (FEATURES §2.1). Every derived number in every mock
  carries measured/estimated/derived.
- **Tone & banned words**: no "failed / cheat / missed / overdue /
  abnormal / you should"; deltas are described, never judged. Each spec's
  tone-rules section has its own banned list — collect them into one
  copy-checklist.
- **R-U7 naming**: nav says "Archive" (F08) and "Digestion" (F09),
  renamable; the words "body photo" / "poop" never appear in nav,
  notifications, or widgets.
- **Color discipline**: red is reserved for nothing (F07); weight gains
  render neutral (F06); no alarm-red anywhere; urgency is communicated by
  position and copy, not hue.
- **R-U13**: gut entries and silhouette captures never render as gaps,
  misses, or incomplete days. No day-completeness shame for irregular
  rhythms.
- **R-U15**: every capture assist (OCR, recognition, classifier, barcode)
  has an equal-status manual path one tap away, visually first-class.
- **R-U16**: silhouette UI is vector outlines only — no photograph
  placeholders in any mock, ever, including empty states.
- **Local-first visuals**: no spinner that implies a server; offline is a
  non-state (F10 §9); empty states teach the next action (F02/F06).
- **Discretion**: FLAG_SECURE surfaces (F08/F09), silent shutter, no sound
  by default in bathroom contexts (F09), notification copy generic
  (R-U7 + F08/F09 guardrails).

## Phase A — research (deliverable: `docs/design/research/`)

1. **Mine the existing research** (`docs/research/*.md`) for *UI-level*
   patterns — the deep-dives were content-level; re-read them asking "what
   does the screen look like": Happy Scale's trend-first framing, Hevy's
   set-logging loop and PR banners, Gyroscope's card-as-artifact and
   pyramid structure, MacroFactor's check-in card, MeThreeSixty's capture
   ritual, Mealime's plan→list pipeline, Zolt's state chips. Output a
   patterns-and-anti-patterns table with citations.
2. **External research** (web): Material 3 foundations (note: M3 defaults
   are roomy — WLO's density requirement means deliberate deviations,
   documented); dynamic color vs. branded palette for a health app;
   number-forward typography (tabular figures, variable fonts — license
   must be GPLv3-compatible per R-S1); data-viz patterns for dense
   personal stats (sparklines, bands, heatmaps — dark-mode-proof);
   Android haptic vocabulary (Compose `HapticFeedback` types) mapped to
   the specs' haptic intents; charting approach (custom Canvas vs
   library) for the forecast cone and energy-balance chart; Glance widget
   constraints (R-U12: one framework, F10 owns it).
3. Write `docs/design/research/synthesis.md` — pattern → source → where
   it lands in WLO. Flag anything that collides with a ruling.

## Phase B — foundations (deliverables: `docs/design/DESIGN-SYSTEM.md`, `docs/design/IA.md`)

1. **Design tokens**: type scale (numbers are the hero — tabular figures,
   odometer-roll friendly), color system (neutral core, semantic states
   without moral valence, dark theme first-class — the weigh-in happens
   at 6 a.m. in a dark bathroom), spacing/elevation for dense layouts,
   motion curves + durations, haptic vocabulary. **Mine every spec's
   "Micro-interactions" section and consolidate all ms values, springs,
   and haptic types into one motion table** — the specs are unusually
   specific (e.g., F02's 600–900 ms shimmer, F07's 500 ms check-in
   reveal, F06's 400 ms odometer); the design system is where they
   become coherent, not where they get re-invented.
2. **Component inventory** — sweep all 13 specs for named components and
   spec them as a table: component, owning spec, states, version tag,
   provenance/tone rules. Known residents: provenance chip, confidence
   ring (F02: green ≥ 0.7 / amber 0.4–0.7 / grey < 0.4), sanity-rail
   marker, Food Memory card, day-status dots, forecast cone + 3-band
   dates, check-in card (frozen anatomy, F07 §4), delta chips, fit
   badges, adherence strip, variety heatmap, tupperware leftover
   connector, aisle-grouped list + reconciliation delta chips, progress
   ribbon, milestone ladder, trend confirmation card, quiet-scale card,
   Bristol carousel, unlock progress ring, report-card grade stamp,
   streak chip, share-card renderer, consent sheet with payload preview
   (F12 §3.5 — a *signature* component), receipt-log ledger view, storage
   dashboard tiles.
3. **Information architecture** (`IA.md`): surface map and navigation
   model (bottom nav vs hub-centric; F10 is the default surface), deep-
   link registry alignment (F10 §3), widget surfaces, where Archive and
   Digestion live given R-U7, settings/AI Studio placement, first-run
   (F01 wizard is a flow, not a modal gauntlet).
4. **Accessibility & pragmatics**: WCAG AA contrast for the neutral
   palette, one-handed reach zones (in-store F04, gym F05), touch target
   floor for sweaty-hands contexts (F05), sensitive archive/photo-surface
   protection, RTL note, locale-sensitivity where specs flag it (F04 aisles).

## Phase C — key flows as prototypes (deliverable: `docs/design/flows/`)

Medium: **static-first HTML/CSS phone-frame pages** (390×844), clickable
where cheap — reviewable in a browser, no build chain. Compose translation
happens at implementation. Each flow ships as: wireframe → annotated
mock → self-review against the design bar + the spec's micro-interaction
list.

Priority order (each maps to a signature moment in FEATURES §2.4):
1. **The Day loop** (F10): hero trend card, calorie ring vs adaptive
   target, quick-action rail, today's-plan card, Adaptive Day Model
   morning/evening reorder.
2. **The photo ladder + correction loop** (F02): viewfinder → result card
   (chips, confidence ring, hint field) → save arc; low-confidence and
   offline variants.
3. **The Sunday check-in** (F07, frozen anatomy) → Apply commit → F01
   plan-diff view. The Week loop's anchor.
4. **Weigh-in + trend moment** (F06): confirmation card, progress ribbon,
   outlier guard, trend history. (The F08 capture ritual that followed here
   moved to its own flow 09 in round 10.)
5. **Plan → shop** (F03/F04): week grid with fit badges, swap deltas,
   aisle-ordered in-store list, sweep-to-pantry.
6. **Report card + Bristol sheet** (F11/F09): grade stamps with held
   states, share composer privacy checklist.
7. **Onboarding wizard** (F01): welcome → goal dials → forecast bloom →
   template gallery → swipe quiz → schedule bars → milestones → start.
8. **The session** (F05, added round 9 — owner-prioritized above further
   digestion coverage; same round, cardio ruled first-class and R-S11
   amended): strength preview with visible progression rules → prefilled
   set logging + rest ring → live cardio (GPS distance, HR zones, route
   trace) → honest plate fallback → substitutions → receipt summary.
9. **The Archive** (F08, split from flow 04 and promoted first-class in
   round 10): lock gate → pose-gated capture ritual → delta card →
   timeline → compare studio → width profiles + trust chart — vector
   outlines only (R-U16).

## Working rules

- **Ticket**: `ticket task new "UI/UX phase: research → design system →
  IA → flow prototypes"`; move `doing` on start; batch owner questions
  via `question add` (JSONL, suggestions with a recommended option,
  `{"text": "…"}` objects) — never poll.
- **Rulings are binding.** If research or a design need collides with an
  R-* ruling, stop and raise an owner question proposing a master-doc
  amendment; never design around it silently. Design decisions that
  deserve freezing get proposed as new **R-D*** rulings (a new "Design"
  subsection of FEATURES §3) after owner approval.
- **Git**: commit at each milestone (Phase A, Phase B, each flow batch)
  with descriptive messages.
- Update `AGENTS.md`'s documentation-layout section when `docs/design/`
  lands, following the same reading-order discipline as the other trees.
- Specs' §8 version tags (`[v1]`/`[v1.x]`/`[future]`) apply to UI too:
  v1 prototypes cover v1 scope only; tag anything beyond it.

## Definition of done (phase gate)

- [ ] Research synthesis reviewed, all collisions with rulings escalated.
- [ ] `DESIGN-SYSTEM.md` with tokens + consolidated motion/haptics table.
- [ ] Component inventory complete against all 13 specs (nothing named in
      a spec missing from the table).
- [ ] `IA.md` with nav model + surface map.
- [ ] All seven flow prototypes pass the design-bar checklist (provenance
      chips on every derived number, tone check, no-red check, R-U7
      naming, R-U13 no-gap-shaming, R-U15 manual paths visible).
- [ ] Owner questions resolved; any new R-D* rulings recorded in
      FEATURES.md §3.
- [ ] Milestones committed.
