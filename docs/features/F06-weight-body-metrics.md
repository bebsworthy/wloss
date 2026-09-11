# F06 — Weight & Body Metrics — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F06 — Weight & Body Metrics |
| **Provides** | The weigh-in ritual and the body's full measurement store: trend-first weight framing with documented smoothers, provenance-tagged body-fat and girth numbers, arbitrary custom metrics — the clean data foundation F07's engine computes on. |
| **User problems solved** | • The scale says +0.8 kg today; WLO answers "Trend 179.1, down 0.6" — fluctuation stops being demoralizing • Body-fat % from a scale is a guess; WLO labels *how* every body number was obtained • "Weight flat but jeans fit" is finally visible: weight vs. circumference side by side • Geeks track neck, ketones, wake-time, anything — without waiting for a developer |
| **AI consent category** | Owns none. The scale-display photo path uses on-device OCR (classical text recognition); no cloud call exists, so no consent category is consumed. Everything is local math. |
| **Primary evidence** | `docs/research/happy-scale.md` (deep: 4 smoothers, lowest-of-day rule, progress ribbon, 10-day-best, Fresh Start, "How we got here"), `docs/research/openscale.md` (formula registry with citations, EAV schema, ~68 scale drivers), `docs/research/gyroscope.md` (photo-of-display input), `docs/research/methreesixty.md` (moving-average trend, circumference semantics), `docs/research/synthesis.md` §1.5, §2 Tier A #2 |

## 1. Purpose & Core Objectives

Weight is the noisiest signal in the entire app and the one users check most often.
F06's job is to turn that noise into a trustworthy, emotionally survivable trend —
and to be the canonical store for *every* number a tape, scale, caliper, or lab can
produce. It is deliberately the "dumb, honest" layer: pure measurement and
smoothing, no energy math (F07's job), no coaching narrative (F10/F11's job).
Every value it emits carries provenance — measured / estimated / derived — and a
"how we got here" trail.

Core objectives:

- **A weigh-in is captured in ≤ 5 s, zero typing in the common case** (Bluetooth scale via F13, or photo-of-display, or one number-pad entry).
- **Trend-first framing on every surface**: the raw scale number is data; the *trend* is the message ("Trend 179.1, down 0.6").
- **At least two selectable smoothers with in-app documented math**: trailing EWMA (default) and a zero-phase (centered, past+future) smoother, plus a plain 7-day MA — each with its lag and failure modes written out, Happy Scale FAQ-style.
- **Every derived number is explainable and provenance-badged**; measured vs. estimated series are never silently merged.
- **Arbitrary user-defined metrics** (openScale's EAV pattern) — one engine, any metric the geek wants.

## 2. User Moments — when and how it is used

- **Cadence:** typically a daily weigh-in (bathroom, morning, post-restroom,
  pre-breakfast — the app gently teaches *same conditions*, never demands);
  **extra same-day weigh-ins are expected and supported — each is kept as its
  own event** (R-B8); tape measurements every 2–3 weeks; body-fat method checks monthly; Bluetooth sync runs invisibly whenever the scale is stepped on.
- **Physical/emotional context:** half-awake, barefoot, slightly vulnerable. This is the highest-tension moment of the user's day — the design must remove the verdict feeling entirely.
- **Episodic:** pairing a new scale (F13), a relapse/long gap (Fresh Start), a plateau (correlation views), a doctor visit (export).
- **The single most common flow:**
  1. User steps on the Bluetooth scale → entry appears in WLO via F13 (or: user snaps a photo of the display → on-device OCR fills the field).
  2. A confirmation card animates in: **"Trend 179.1 ↓ 0.6"**, raw 178.4 shown small beneath; single soft haptic.
  3. Done — under 5 seconds, no keyboard. The chart ribbon has grown imperceptibly.

## 3. How It Works — functional mechanics

**Inputs**

- Manual entry (number pad, one decimal, back-datable); Bluetooth scale telemetry via **F13** (openScale-class driver strategy: reuse/port drivers, credit openScale); Health Connect weight/fat/girth via **F13**; **photo-of-the-scale-display** (crop + on-device OCR, Gyroscope's trick); tape-measurement entry; optional caliper entry.
- From features: **F01** goal weight and target date (milestone math); **F05** one-tap bodyweight writes; **F13** backup/export.

**The built-in metric catalog**

- **Core:** weight (kg/lb/st, profile-sticky), body-fat % (multi-method), body water, muscle mass, bone mass — whatever the scale or the user provides.
- **Girth sites (measured every 2–3 weeks, the app reminds gently):** neck, shoulders, chest, waist, abdomen, hips, biceps, forearm, thigh, calf (Hevy's 9-site practice, extended). New entries prefill last values so updating three sites takes three taps, not three forms.
- **Derived:** BMI, waist-to-height, waist-to-hip — computed from measured inputs, never entered.
- **Custom:** unlimited user-defined metric types via the EAV store (ketones, waking pulse, morning coffee, mood) — each gets entry, chart, trend stats, and a CSV column automatically.

**Processing — all on-device, all deterministic**

- **Import semantics (frozen, event-level per R-B8):** the store keeps **every**
  timestamped reading. Multiple weigh-ins per day are normal data — never
  collapsed, overwritten, or judged (owner note: people re-weigh after coffee,
  a walk, or the bathroom to "get their win"; that psychology is supported and
  recorded, and it is partly why the gut tracker exists beside the scale). The
  **daily scalar is a derived view**: for weight, lowest-of-day wins (Happy
  Scale's rule; best estimator of true morning mass), timestamp normalized to
  noon; last-in wins for girths. Raw points stay queryable (time-of-day lens,
  weigh-count stats) and are included in exports. Duplicates deduped silently,
  visible in an import log.
- **Trend weight — selectable smoothers (the math is in-app documentation):**
  - *EWMA (default):* `trend_t = α·x_t + (1−α)·trend_{t−1}` (α ≈ 0.1–0.3, user-tunable). Past-only; lags a few days; rock-stable.
  - *Zero-phase (option):* centered filter using past **and future** weights; near-zero lag; honestly documented side effect — recent values revise slightly as new data lands, and during plateaus it can briefly project below any achieved weight (Happy Scale's documented caveat, shown as a note, not hidden).
  - *7-day moving average (option):* maximum simplicity, maximum lag.
  - A "compare modes" view overlays all smoothers on one chart for the geek.
- **Body-fat % — a method registry, never one number:** US Navy (neck/waist/hip girths), RFM (height/waist, no neck tape), BMI-based (Deurenberg, labeled crude), smart-scale impedance decoded per vendor family (openScale pattern: per-device decoder first, published-formula fallback second), photo-scan estimates from **F08** [future]. Each method keeps its **own series**; the user picks a headline method; all are chartable together.
- **Derived ratios:** BMI, waist-to-height, waist-to-hip — computed, badged *derived*, evaluated against published healthy ranges (openScale pattern).
- **EAV metric store:** `Measurement / MeasurementType / MeasurementValue` — weight and the built-ins are just pre-registered types; the user can define arbitrary metrics (ketones, neck, waking pulse, coffee…) each getting charts, stats, and CSV columns. This one schema choice quietly powers F09 and any future tracker.

**Outputs & artifacts**

- Daily scalar series per metric + trend series; progress-ribbon geometry; milestone breakdown (goal split into milestone weights, each with a target date rendered from **F07**'s forecast); correlation datasets (weight ↔ any girth); import log; export-ready series (F13).

**State owned by F06:** the measurement store, smoother selection/α, body-fat method registry and headline pick, lowest-of-day + import-log policy, girth-site catalog. (Milestone definitions are F01's; Fresh Start is F01's ritual with F13's mechanics per R-B7 — F06 only renders both.)

## 4. User Interaction Model

**Entry points:** F10 Daily Hub weigh-in card (primary); home-screen widget "today's trend"; weigh-in reminder notification (soft, user-set); F07 check-in card's trend row (deep-link); F11 milestone celebrations (deep-link).

**Primary flows**

- *Happy path:* scale → auto-import → trend confirmation card → done (0 taps if auto-confirm is on; 1 tap otherwise).
- *No smart scale:* open card → **type 92.1 on the number pad — an equal,
  first-class path one tap away, not a fallback** (R-U15: some days typing is
  simply faster) → confirm. The camera/OCR assist is offered *above* the pad,
  never instead of it; OCR failure lands silently on the pad and nothing is
  lost.
- *Outlier guard:* an entry ±3σ off recent residual triggers a one-line confirm — "4.2 kg above yesterday — keep or correct?" — one tap either way; an admitted typo is fixed, not judged.
- *Back-fill:* missed a day? Long-press the chart on that date → number pad → the trend recomputes and, for the zero-phase smoother, recent values re-settle with a visible 300 ms ease. Editing history is honest and visible, never silent.
- *Long gap / relapse:* returning after ≥ 14 days, the Hub offers **Fresh Start**: hide-not-delete everything before a chosen date; old history stays exportable and reversible. No "welcome back, you gained" copy — ever.
- *Multi-profile:* per-profile stores (couples sharing a device), each with its own biometric lock; the weigh-in card is profile-aware without a picker in the common single-user case.

**Input minimization**

- Never typed in the common case: weight (scale/OCR/Health Connect), body-fat (scale decode), girth increments (new tape entries prefill last values, openScale pattern), unit (profile-level, sticky).
- One-tap shortcuts: "same as yesterday" for girths; long-press chart to back-fill a missed day from memory; re-pair scale from the failure card in one tap.

**Micro-interactions**

- **Trend confirmation haptic:** save = one soft tick. Trend delta negative = the tick plus a downward-drawing arrow that settles with a spring — the *direction* is celebrated, magnitude never compared to yesterday's raw number. Delta positive = same tick, neutral color, no sad animation; a gain day feels identical in effort, different only in the small print.
- **Odometer numerals:** trend value rolls digit-by-digit (400 ms) on the confirmation card and Hub hero number.
- **Progress ribbon:** green band above / neutral band below the trend line; thickness = change vs. N days ago (default 30, user-adjustable with a scrubber). New data makes the band *breathe* — a 200 ms thickness ease. The user literally watches progress accumulate as area.
- **Milestone moment:** full-bleed card, giant numeral count-up, distinct two-note celebration haptic, shareable card render (F11 may attach a badge).
- **OCR capture:** viewfinder brackets snap green on lock; the recognized value flies from the photo into the field.

**Data-quality gating**

- Trend line needs ≥ 3 points in the trailing 7 days before it renders (below that: dots only, "keep weighing — trend forms in a few days").
- Milestone dates and any forward projection are **F07's** to compute and gate; F06 renders them but refuses to invent dates from thin data.
- Body-fat from impedance always carries an honest uncertainty note (±3–4% typical); no false precision, MeThreeSixty-complaint-proof.

## 5. What the User Gets Out

- **Headline pair:** raw weigh-in (small) + trend weight (hero) + weekly rate ("−0.6 kg/wk"); user-selectable progress number: trend, or **best-of-window** ("10-day-best" — record-low psychology vs. steady-number calm).
- **Charts:** scale dots + trend line + progress ribbon (30d/90d/1y/all); per-metric trend charts for every girth and custom type; body-fat multi-method overlay; weight ↔ girth scatter with correlation coefficient and plain-language note ("waist −3.1 cm while weight flat — you're losing size, and the tape sees it") — **[v1.x]**; correlations stay out of v1 per the master scope.
- **The logbook:** a raw table of every entry with provenance column and inline edit — the geek's ground truth beneath every smoothed view, and the source of the import log (which device wrote what, when, under which dedup rule).
- **Milestone breakdown:** F01's auto-generated ladder (4–8 rungs) rendered with per-milestone dates (from F07's 3-band forecast — optimistic/expected/pessimistic dates shown as a range, never one promise).
- **Provenance rule:** every body number is badged **measured** (tape, scale mass), **estimated** (Navy/RFM/impedance/photo-scan — with formula + inputs + citation), or **derived** (BMI, ratios). Tapping any number opens "How we got here" — the exact inputs, formula, constants, and its data-quality state.
- **Exports:** full-history CSV/JSON via F13; a one-page doctor summary.

## 6. Motivation & Psychology

- The itch to return: daily *trend tick* — a tiny, always-kind number that moves less than the scale and means more. Same-day re-weighs (including the post-bathroom "get the win" one) are handled with a light touch: the new entry quietly joins the day view, no commentary and no duplicate-guilt, and each view of the day picks the semantics it consumes (R-B8). Happy Scale's entire 4.89★ moat is emotional relief; WLO inherits it and adds the geek layer.
- Gamification hooks **owned here, systematized by F11:** milestone-reached events, streak hook (weigh-ins logged), "record low" event, girth-record events. F11 decides badges/streaks; F06 only emits clean events and renders the milestone moment.
- **Tone rules:** the scale is a "trickster," the trend is the truth — fluctuation is reframed as physics (water, glycogen, gut), never as failure. Gains use the neutral half of the user's palette (green/red/blue/purple selectable, Happy Scale pattern); the word "over" is never applied to a body number. BMI is shown on request only, ranges not verdicts.

## 7. Relations to Other Features

- **Consumes from:** **F13** — Bluetooth scale drivers, Health Connect import/export, backup; **F01** — goal weight, start weight, target date; **F05** — explicit bodyweight writes from bodyweight exercises; **F12** — nothing (no cloud call exists in F06; OCR is on-device by design).
- **Feeds into:** **F07** — the daily scalar series and trend series: *the* core input of the entire energy engine (TDEE solve, forecast, check-in math all consume F06's frozen semantics — lowest-of-day, noon-normalized); **F05** — bodyweight for lift scaling; **F08** — weight/girth context aligned to silhouette timeline entries; **F10** — daily hero number and weigh-in reminder; **F11** — milestone/streak/record events and trend data for report cards.
- **Shared concepts:** Provenance (co-owner of the pattern), Trend weight (F06 computes; F07 consumes — smoothing lives in exactly one place), Targets (read-only here), EAV store (F06 owns the schema; F09 and custom metrics ride it).
- **Boundary notes:** F06 never computes calories, TDEE, or goal dates (F07); never judges intake (F02); never edits goals (F01). F08 handles photos/visual body analysis; F06 handles numbers — a shared timeline view links them.

## 8. Blue Sky Ideas

- **[v1] Photo-of-the-scale display** — on-device OCR input for any scale, smart or dumb; the cheapest zero-typing win for the majority without Bluetooth scales.
- **[v1] Provenance badges + "How we got here" everywhere** — openScale computes provenance but never surfaces it; WLO makes it a first-class UI atom.
- **[v1] Fresh Start mode** — hide-not-delete with reversible markers; the honest answer to relapses, Happy Scale-proven.
- **[v1.x] Weight ↔ circumference correlation view** — paired scatter + per-interval deltas ("losing size not weight" made quantitative); extends to any EAV metric the user defines.
- **[v1.x] Impedance decoder library** — port/credit openScale's per-vendor decoders and published formula set (Navy, RFM, Deurenberg, Gallagher…) with citations in-app; measured-vs-estimated overlay charts with uncertainty bands.
- **[v1.x] Named milestones** — milestones the user can label ("Beach trip") and share as a card; emotionally loaded dates beat abstract percentages.
- **[v1.x] Measurement-quality hints** — gentle capture coaching on the tape flow (same time of day, tape tension, mirror check for waist level) as dismissable one-liners, so girth series stay comparable — MeThreeSixty's environmental-guardrails idea applied to analog tools.
- **[v1.x] Weigh-time consistency lens** — a histogram of weigh-in times and a "conditions drift" note when the series mixes morning and evening entries; the data-quality story users can *see* before they wonder why the trend wobbles.
- **[future] Household auto-detection** — recognize *who* stepped on the scale from weight signatures (openScale's beloved niche); multi-profile made effortless.
- **[future] Gold-standard anchor points** — occasional DEXA/bod-pod/calistrip user entries calibrate the impedance series: WLO fits a personal bias correction per method and shows the adjusted series beside the raw.
- **[moonshot] Body-number fusion** — a small on-device Bayesian filter merging every body-fat source (impedance, girth formulas, F08 photo estimates, anchor points) into one posterior estimate with a live uncertainty band; the geek's "true BF%" with all inputs visible.

## 9. Guardrails, Privacy & Sensitivity

- **Highest-sensitivity data in the app.** Weight history can signal an eating disorder, pregnancy, or illness. Protections: biometric lock on the metrics area (per-profile, Happy Scale pattern); discreet mode (hero number hidden until tapped); exports are explicit user actions only.
- **No cloud path exists** — no consent category, no network call, no telemetry. The scale photo is processed in memory and never persisted unless the user saves it deliberately.
- **Body-image hard rules:** no ideal-weight moralizing, no BMI lectures, no red gain-alarms, no comparisons to other humans or populations; eating-disorder-adjacent patterns (rapid loss, obsessive frequency) trigger a gentle, dismissable info card with professional-resource pointers — once, never nagged.
- **Free forever:** all history, charts, smoothers, custom metrics and exports are un-gated — the anti-Happy-Scale-Deluxe, anti-MeThreeSixty-Premium guarantee (synthesis §4: never paywall a user's own data).

## 10. Open Questions

- **Zero-phase smoother spec:** centered MA vs. double-exponential-with-backward-pass vs. Happy Scale's proprietary equivalent — needs an implementation decision plus a documented-behavior spec (the "revises recent values" UX must be designed, not discovered).
- **Default α for EWMA:** 0.1 (stable, laggy) vs. 0.25 (responsive) — propose shipping 0.15 with the tuner visible; master doc should ratify. *(Resolved: R-A2 — default 0.15, tuner visible.)*
- **Import-rule edge cases:** fasting-day weigh-ins, multiple users on one scale stream via Health Connect, timezone shifts while traveling — lowest-of-day is frozen, but dedup vs. conflict policy needs a final call.
- **F07 contract:** the exact series contract (fields, gating flags, uncertainty values) F06 exports for engine consumption should be co-signed by the F07 spec — this doc assumes trend + residualσ + coverage% are sufficient; F07 should confirm. *(Resolved: R-B5 — daily scalars + trend + residual σ + coverage % + provenance flags, under lowest-of-day/noon-normalized semantics.)*
- **Milestone date rendering:** confirmed dependency on F07's forecast engine — if F07 is in DEVELOPING state, F06 milestones show ranges only; boundary behavior to be locked jointly.
