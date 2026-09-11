# WLO UI/UX Research — Synthesis

*Phase A of the UI/UX design phase (`docs/design/KICKOFF.md`). Companion to the
feature-level synthesis [`../../research/synthesis.md`](../../research/synthesis.md),
which asked "what does the app do"; this one asks "what does the screen look
like." Date: 2026-09-11. Sources: the 19 competitive deep-dives in
`docs/research/`, re-read at the UI level, plus external research (Material 3,
typography, data-viz, haptics, charting, widgets) with links inline.*

**Method.** Every competitive doc was mined for screen-level patterns
(layout, typography, color, charts, motion, capture moments, gamification
visuals, discretion UI). Nothing here re-litigates *features* — that verdict
already lives in the feature-level synthesis. Each pattern below carries:
source (file + near-verbatim quote), and **where it lands in WLO** (the
spec/surface that should adopt it). Collisions with frozen R-\* rulings are
flagged in §3 — none required stopping work; three design-level calls are
proposed as new R-D\* rulings (§4).

---

## 1. Patterns the market validated (adopt)

### 1.1 The house style of the category's best screens

Across Happy Scale, MacroFactor, Zolt, Gyroscope and Hevy, one visual language
recurs: **dark-first, card-based, one oversized hero numeral per card,
chart-forward tiles that drill down, visible uncertainty bands, and
trend-smoothing as the emotionally protective default view.**

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P1 | **Trend-first framing on every entry** — "the reply always reframes the raw number as the trend delta" (logged 178.4 → "Trend's 179.1, down 0.6") | zolt.md; identical ritual in happy-scale.md ("trend weight" as headline) | F06 confirmation card (frozen: "Trend 179.1 ↓ 0.6", raw small beneath); F10 hero card |
| P2 | **One hero number per card** — "dark, cinematic presentation… glanceability of a single hero number per card" | gyroscope.md | F10 card anatomy rule; every F11 domain card |
| P3 | **Card-as-artifact / screenshottability** — "vivid, chart-led, self-contained tiles users screenshot and share"; fixed-aspect, oversized numeral, wordmark | gyroscope.md; hevy.md ("everything renders as shareable images") | F11 share-card renderer (already spec'd); F05 session receipt; F01 forecast card ("should survive being screenshotted") |
| P4 | **Uncertainty drawn, not hidden** — expenditure "confidence/uncertainty wideness"; "uncertainty grows when you stop logging… rather than collapsing" | macrofactor.md | F07 forecast cone (bands widen with horizon, "the widening is itself displayed"); F08 noise band |
| P5 | **Status before number** — data-quality chips developing/updating/held; "read the status before the number" | zolt.md; macrofactor.md missing-data tolerance | F07 status chip (frozen anatomy row 1); F03 adherence strip; F11 grade gating |
| P6 | **Composite numbers decompose** — TDEE rendered as named additive terms ("1,690 + 120 + 350 + 480 = 2,640"); "what-moved-it" chips | zolt.md | F07 four-term breakdown (frozen tap-through from expenditure row) |
| P7 | **Monochrome restraint + typography as the design** — "minimalist black-and-white aesthetic, excellent typography and chart design" (review-verified) | macrofactor.md | WLO neutral core + tabular figures (DESIGN-SYSTEM §type); restraint is the bar, accents are earned |
| P8 | **Monospaced data accents + oversized display numerals** — "oversized display type, gradient heroes, numbered chapters, monospaced data accents" | gyroscope.md | F11 report cards, Wrapped-style recaps [v1.x]; DESIGN-SYSTEM numeric styles |
| P9 | **60fps motion as an engineering value** — "count-up numerals, smooth transitions… part of the premium feel" | gyroscope.md | Design-system motion table; F10's 5-second doctrine pairing with polish |
| P10 | **User-owned semantic colors** — gain/loss colors user-configurable, "applied consistently app-wide" | happy-scale.md; F06 §4 adopts explicitly | F06/F10 delta chips — **see collision C1 (§3)** |

### 1.2 Capture & correction (the hero-flow grammar)

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P11 | **Result card with chip-style confirm/correct** — "result card with calories/protein/carbs/fat → user confirms or corrects"; numbers update live while steering | cal-ai.md | F02 result card (items, macros, confidence ring — Save live, editing optional) |
| P12 | **Text-steerable re-estimation** — "'it was effortless to give it input and steer it'… numbers update in place" | cal-ai.md; snapcalorie.md hints ("adapts to repeat meals") | F02 hint field (plate-level free text, no word limit — SnapCalorie's limit is a named anti-pattern) |
| P13 | **Structured correction modal** — "a new window opens where I can individually select the foods (and respective servings)" | foodvisor.md | F02 per-item chips: tap → swap, portion slider, "not this" |
| P14 | **Verify-then-log** — "AI recognizes, database guarantees accuracy… must be confirmed/edited before logging" | cronometer.md | F02 DB-verified provenance tier; never auto-commit an AI decision |
| P15 | **Analyzing state as crafted motion** — "an analyzing animation while the model runs" | cal-ai.md | F02 600–900 ms shimmer + chips "dealt" in one by one (spec'd) |
| P16 | **Viewfinder recognition made visible** — segmentation mask, depth capture; per-segment alignment gating with named fixes | cal-ai.md (depth viewfinder); methreesixty.md ("stick figure lines" turn green per segment); F08 spec adopts | F02 viewfinder desaturation + depth mesh; F08 12-segment skeleton red→amber→green |
| P17 | **Food Memory one-tap re-log** — "corrected/logged meals become named, one-tap re-loggable items" — "statistically the most used feature in every tracker" | cal-ai.md; foodvisor favorites/recents | F02 Food Memory cards (spec'd [v1]) |
| P18 | **Prefill-from-last-time** — "pre-filled from the previous session — a repeat workout is essentially one tap per set"; girth entries prefill last values | hevy.md; openscale.md ("new entries inherit waist/hip values") | F05 set rows (spec'd); F06 girth "same as yesterday" |
| P19 | **One-tap confirm-the-prescription** — "one-tap set logging when the performed weight matches the prescription"; deviation is the secondary path | fitbod.md | F05 logging loop; the same grammar as F03 "log as planned" and F09 classifier pre-select |
| P20 | **Auto rest timer with in-flight ± controls** — "in-rest ±15 s adjustment buttons" | hevy.md | F05 rest timer chip (spec'd: ring drain, T−3 s ticks) |
| P21 | **Keyboard-adjacent specialist tool** — plate calculator appears "just above the keyboard"; honest fallback chip "Closest possible: 135 kg" | hevy.md | F05 plate calculator; generalize: tools appear at the point of input, never in a menu |
| P22 | **Hands-free capture for awkward moments** — auto-timer photo capture ("use the toilet, snap a photo using the built-in auto timer") | poop-trackers.md (Poo Keeper); mealime.md touch-free cook mode | F09 3 s auto-timer (spec'd); F02 hands-free cook mode [future] |
| P23 | **Input ladders with graceful landing** — "photo > voice > barcode/label scan > free-text hint > search"; every rung lands on manual without loss | foodvisor.md (five ways in one loop); R-U15 codifies it | F02 ladder (spec'd); F06 OCR-above-numpad; F09 carousel override — **manual path is never "advanced" (PoopCheck's fatal omission, inverted)** |

### 1.3 Charts & density

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P24 | **Progress ribbon** — "green area above / red area below the line whose thickness shows how much you've lost" — hidden progress as literal geometry | happy-scale.md; F06 spec adopts (band above/neutral below, N-day gear) | F06 chart + F10 hero strip |
| P25 | **Plan-vs-actual overlay** — weight chart overlays "progress vs. plan" schedule line | loseit.md | F06 chart (F01 plan curve as overlay); F03 planned-vs-actual deltas |
| P26 | **7-bar budget-shape chart** — weekday/weekend calorie cycling "trivial to render as a 7-bar budget chart," a power-user favorite | loseit.md | F01 schedule bars (spec'd: pinned weekly total invariant) |
| P27 | **Any-metric × any-window** — "line graphs of any nutrient/biometric over custom ranges," rolling-average toggle, averages of visible points | cronometer.md; macrofactor.md ("chart pages display an average of all visible points") | F11 stats hub (spec'd: free range + presets, overlay compare) |
| P28 | **Fan/cone forecast** — central line, bands widening with horizon, darkest at center (Bank of England fan chart; external research, see §2.5) | external; the "gap in all 19 apps" per feature-level synthesis | F07 3-band decelerating forecast cone (spec'd, custom Canvas per §2.7) |
| P29 | **Year heatmap** — GitHub-style calendar "delight even novelty users… ours carries health meaning" | poop-trackers.md (Poop Map); waistline/hevy analogues | F09 regularity heatmap (ink-bloom stagger); F05 training heatmap |
| P30 | **Volume-weighted beats binary heatmaps** — "genuinely volume-weighted" recovery map vs Hevy's "binary highlighting" (named under-serve) | fitbod.md vs hevy.md | F05 muscle heatmap (intensity-graded), F11 weekday heatmaps |
| P31 | **Tap-to-explain every viz element** — "tappable muscle in the heatmap revealing which exercises hit it" | fitbod.md | Rule: every chart element is a drill-in trigger into "how we got here" |
| P32 | **Reference-band context without judgment** — "evaluation vs recommended ranges from age/sex/height" | openscale.md | F06 optional healthy-range shading; F09 personal-baseline band ("0.4/day vs your 1.6 — a quiet week") |
| P33 | **Graph + table dual view** — "the spreadsheet view, not just the pretty charts"; resizable | openscale.md | F05 logbook, F06 logbook (spec'd); F11 table toggles |
| P34 | **Event annotation on longitudinal charts** — "journal-entry markers ('Started running', 'Sick week') annotated on the timeline" | zolt.md | F07 TDEE longitudinal chart (spec'd); F06/F11 chart annotations |
| P35 | **Small multiples & sparklines** — Tufte: same-scale micro-charts beat animation for comparison; sparklines = "word-sized graphics" with min/max marks, no axes | external (§2.4); Waistline trend overlays as baseline | F10 sparkline strips; F04 expiry/staple sparklines [v1.x]; DESIGN-SYSTEM chart atoms |

### 1.4 Ritual, motivation, gamification

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P36 | **Check-in as a designed micro-event** — "the check-in moment is a designed micro-event… polished animations on rings and check-ins"; one weekly cadence beats daily guilt loops | macrofactor.md | F07 500 ms check-in reveal sequence (frozen); Sunday ritual (R-U2) |
| P37 | **Decision card with three big buttons** — "YES Apply it / KEEP Roll over / ASK Talk it through" | zolt.md | F07 Apply/Keep/Discuss (frozen anatomy row 6) |
| P38 | **Letter-grade report cards** — "letter grades per body system"; stamps in sequence, heavier haptic for an A | gyroscope.md | F11 report card + grade stamp (spec'd) |
| P39 | **Recap ladders: weekly → monthly → annual** — "annual Spotify-Wrapped-style report"; monthly recap cards; weekly digest | macrofactor.md, hevy.md ("December Year in Review"), loseit.md | F11 report card [v1] → monthly recap → Wrapped [v1.x] (R-U4 install-anniversary) |
| P40 | **PR banners: the number is the celebration** — "the moment a completed set beats a personal best… a banner"; "no confetti — the *number* is the celebration" | hevy.md; F05 spec adopts verbatim | F05 PR banner; F11 PR shimmer (particle, not confetti) |
| P41 | **Named milestones as forecast anchors** — "predictions can carry labels ('Wedding Day')"; auto-split milestone ladders with per-rung mini-forecasts | happy-scale.md; F01/F06 adopt | F01 milestone ladder; F06 milestone moment card |
| P42 | **Milestone/badge sets with concrete triggers** — "'Bullseye' (30-day calorie goal), 'Heavy Exit' (lose 50 lb)" | cal-ai.md | F11 badge gallery (visual direction open → FEATURES §6.7; proposal in §4) |
| P43 | **Progress that accumulates as area/geometry** — "the user literally watches progress accumulate as area"; "the trend line ticking steadily downward *is* the reward" | happy-scale.md | F06 ribbon breathe; F07 adjustment-term glow ("the app's quiet applause") |
| P44 | **Multi-oracle forgiving streaks** — streaks from any of several log types; freeze/restore free; "held days neither advance nor break" | cal-ai streak flame + reviled $0.99 restore (anti-pattern) inverted; R-U10 codifies | F11 streak chip + freeze-token UI (spec'd) |

### 1.5 Trust, explainability, privacy

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P45 | **"How we got here" as a first-class atom** — "a 'How we got here' section explaining exactly how the prediction was calculated"; published math (MacroFactor), auditable breakdowns (Zolt) | happy-scale.md, macrofactor.md, zolt.md | The provenance-chip system (FEATURES §2.1): every derived number chips → explainer sheet |
| P46 | **Accuracy published with methodology** — "~15% mean caloric error, versus ~40% error for dietitians"; "publish accuracy *with methodology* or be dismissed" | snapcalorie.md; synthesis §5 | F02 personal accuracy stat ("your scans are typically ±14%"); F12 accuracy page |
| P47 | **Quantified privacy as UI** — "no internet permission is the gold standard of provable privacy"; "'0 bytes left your phone' as a screen, not a blog post" | openscale.md; F12/F13 adopt | F12 receipt log + kill switch; F13 network audit page; F10 "Computed on your device" on the check-in card |
| P48 | **Silhouette-not-photo framing, made architectural** — "scans your silhouette, not your image"… but a Facebook SDK "destroys its own privacy pitch" | methreesixty.md (anti-pattern half) | R-U16 is the moat: vector-only by construction; WLO states it once, proves it in the audit page |
| P49 | **Explainable adaptation vs black box** — "readable rule engine" as anti-Fitbod positioning; Hevy praised because "its progression rule is verifiable" | f05 spec; hevy.md, fitbod.md | F05 progression rules as editable sentences; F07 Algorithms page ("publishing the recipe is the trust feature") |
| P50 | **Receipt-formatted data density** — "Coach replies are formatted as receipt-like lines: 785 cal · 52g P · 96g C · 21g F → day 1,205/2,180" | zolt.md | Copy style for stat lines app-wide (dry, numbers-forward — F03 house style codifies it) |
| P51 | **Consent that happens where the data is** — payload preview before send; "the decision happens where the data is"; confirm-before-write drafts (Zolt MCP pattern) | f12 spec (ratified by zolt.md/snapcalorie.md anti-patterns) | F12 consent sheet with payload preview — the signature component (F12 §3.5) |
| P52 | **Demo data as empty state** — "good empty-state handling via the demo-data generator" | openscale.md | Candidate for F11 stats empty states [v1.x] — teach the next action first (F02/F06 rule), demo data second |
| P53 | **Coverage framing for irregular rhythms** — "11 of the last 12 weeks captured — a *coverage* streak, not a duty streak"; "a quiet day renders as a quiet day, never a gap" | f08/f09 specs (validated by poop-trackers baseline framing) | R-U13 rendering rule app-wide: gaps are faint dots, never shamed |

### 1.6 Discretion UI

| # | Pattern | Source | Lands in WLO |
|---|---------|--------|--------------|
| P54 | **Photo concealment one tap away** — "a privacy feature to conceal photos when showing the app to others"; hidden-from-gallery by default | poop-trackers.md (Poo Keeper, PoopLog) | F09 hold-to-reveal + conceal toggle (spec'd) |
| P55 | **Disguised entry + biometric lock** — "'Happy Diary' decoy icon mode… biometric/PIN lock"; per-profile passcode | poop-trackers.md (Happy Poop); happy-scale.md | F13 biometric app lock [v1]; decoy icon = candidate [v1.x] owner call, not v1 |
| P56 | **Silent shutter & generic notifications** — camera sound is "a discretion leak"; notification copy generic, app icon only | f08 spec; poop-trackers category norms | F08/F09 discretion set (FLAG_SECURE surfaces, silent shutter, no sound by default) |
| P57 | **Blurred-by-default history with hold-to-reveal** — "history lists blur photos by default with a hold-to-reveal" | f09 spec; Poo Keeper pattern | F09 entry lists; F02 diary thumbnails stay visible (food photos are not taboo — retention is the only default-off part, R-U14) |

---

## 2. External research (what the platform gives us)

### 2.1 Material 3 — and the density deviation, documented

M3 lays out on a 4 dp grid; default touch target 48×48 dp (Compose enforces a
48 dp minimum interactive area even on smaller visuals); type ramp runs 15
baseline styles from Display ~57 sp to Label ~11 sp — **roomy by default**.
WLO's density requirement means deliberate, documented deviations:

- M3 defines a **numbered density scale (0, −1, −2, −3)**, each step removing
  ~4 dp from vertical padding, and explicitly blesses higher density "when
  people need to scan, view, or compare a lot of information" — WLO's case.
- **Deviation recipe adopted for WLO** (DESIGN-SYSTEM §spacing): compress
  spacing tokens (8 dp gaps standard, 4 dp tight); density −1/−2 allowed on
  display-only stat rows; **primary daily-use targets (weigh-in, shutter,
  check-in Apply) stay ≥48 dp**; secondary repeated controls ≥40 dp in dense
  lists; nothing below 24 dp (WCAG 2.2 §2.5.8 AA floor). Gym/in-store
  contexts (F04/F05): sweaty-hands floor stays 48 dp on flow-critical rows.

### 2.2 Dynamic color vs fixed palette

Material You maps wallpaper → HCT tonal palettes → color roles; opting out is
legitimate (supply your own scheme), and the documented hybrid is "dynamic for
surfaces, fixed brand colors for key actions and emphasis."
**Recommendation adopted:** WLO's semantic palette is *reserved* (no alarm-red,
held=amber, trend-neutral) — a wallpaper-driven hue shift can drift accents
toward alarm-adjacent territory. **Dark-first fixed scheme is the base;
dynamic tint at most opt-in and surfaces-only** (never semantic roles, never
data-viz hues). Rationale recorded in DESIGN-SYSTEM §color; proposed as R-D1.

### 2.3 Number-forward typography

- **Tabular figures (`tnum`) are the core mechanism** — equal digit advance
  widths stop live-updating numbers from jittering; pair with `lnum`,
  `zero` (slashed zero) for 0/O contexts, `frac` for recipe fractions.
  Proportional figures only in running prose.
- **Variable fonts:** Inter 4.x carries an `opsz` axis (text→display) — one
  family covers 11 sp stat labels and 64+ sp hero numerals. Roboto Flex adds
  `GRAD` (weight grade without reflow) — useful for emphasized numerals.
- **License check (R-S1 GPLv3):** Inter (OFL-1.1), Roboto Flex (OFL-1.1),
  Source Sans 3, IBM Plex Sans, Space Grotesk, Archivo, Public Sans — all
  OFL-1.1, all GPLv3-compatible per FSF's license list (OFL's bundling clause
  is font-scoped and harmless; Reserved-Font-Name applies only to modified
  redistributions).
- **Adopted:** Inter as the single UI family (tnum/opsz/zero/frac), display
  optical size for hero numerals; no second typeface. Proposed as R-D3.
- **Odometer roll guidance:** roll once per value change (200–300 ms small
  tiles, 400–600 ms hero; ≤1 s cap), decelerating ease, digits must be
  tabular or layout jitters mid-roll; honor the Android "remove animations"
  scale (values settle instantly, haptics remain — haptics are unaffected by
  reduced motion, which makes them the fallback feedback channel).

### 2.4 Data-viz for dense personal stats

- **Sparklines:** word-sized, axis-free, min/max marked, placed inline
  (Tufte). WLO: behind delta chips in cards/lists.
- **Confidence cones = fan charts** (Bank of England pattern): central
  forecast, bands widening with horizon, opacity stepping outward — maps 1:1
  onto F07's 3-band forecast. No chart library models per-x band pairs
  cleanly → custom Canvas (§2.7).
- **Calendar heatmaps:** month-paginated grids, 4–5 lightness steps, never
  full-year squeezed to phone width; sequential scales from viridis/magma
  family (perceptually uniform, CVD-safe, CC0).
- **Rings vs bars:** rings for closed daily targets (max ~3 on screen, always
  paired with the number — never color-only); bars where the residual must be
  read precisely (macro grams). WLO's budget ring + three macro dots fits.
- **Categorical color:** Okabe-Ito palette (vermilion/bluish-green instead of
  red/green; explicit avoidance of red-green confusable pairs). **Adopted as
  WLO's data-series palette basis** — it also naturally enforces the no-red
  discipline.
- **Dark-mode-proofing:** desaturate accents to hold WCAG 4.5:1 (text) /
  3:1 (graphics, WCAG 1.4.11) on dark elevations; limited accents on dark
  surfaces (Material dark theme guidance).
- **Chart accessibility:** contentDescription states purpose *and result*
  ("Weight, last 30 days: 80.2 → 78.1 kg, down 2.1"), unique per chart node;
  decorative chrome gets `null`.

### 2.5 Haptics (Compose vocabulary) — the "kind haptic" mapping

Verified current `HapticFeedbackType` set (Compose 1.8): `LongPress`,
`TextHandleMove`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`,
`KeyboardTap`, `SegmentTick`, `SegmentFrequentTick`, `ToggleOn`, `ToggleOff`,
`VirtualKey`, `Confirm`, `Reject`. View-level constants add `CLOCK_TICK`,
`GESTURE_START/END`, `DRAG_START`, `GESTURE_THRESHOLD_(DE)ACTIVATE`;
SDK 30+ primitives (`PRIMITIVE_TICK/CLICK/QUICK_RISE/SLOW_RISE/QUICK_FALL/
LOW_TICK/SPIN/THUD`) via `VibrationEffect.Composition`, availability-checked.
Official intent guidance: "CONFIRM… short and light; REJECT… stronger, signal
failure."

**WLO ruling adopted (DESIGN-SYSTEM §haptics):** every data-outcome haptic is
a `Confirm`-class (short and light) regardless of whether the news is good —
"the direction is celebrated, magnitude never compared." `Reject` is reserved
for true blocking errors only (BYOK key rejected, corrupt import), *never*
for data outcomes. The F01 "haptic wall" (calorie-floor stop) is the one
deliberate `Reject`-weight interaction, and it blocks a *setting*, never
judges a *result*. Spec-level haptic words (tick, double-tick, triple-pulse,
thock, deep impact, two-note, "heavier for an A") map onto this vocabulary in
the consolidated motion/haptics table (DESIGN-SYSTEM §5).

### 2.6 Motion system (M3 tokens + springs)

Duration scale: Short 50/100/150/200 · Medium 250/300/350/400 · Long
450/500/550/600 · ExtraLong 700/800/900/1000 ms. Easings: Emphasized
(0.2, 0, 0, 1), EmphasizedDecelerate (0.05, 0.7, 0.1, 1) for enters,
EmphasizedAccelerate (0.3, 0, 0.8, 0.15) for exits. Springs via
`motionScheme` (Expressive: default spatial damping 0.8 / stiffness 380,
fast 0.6/800, slow 0.8/200). **The specs' unusually specific values
(F02's 600–900 ms shimmer, F07's 500 ms reveal, F06's 400 ms odometer) are
consolidated — not re-invented — in DESIGN-SYSTEM §5**, snapped to the M3
scale where they land within tolerance.

### 2.7 Charting approach (Compose, 2026)

Survey: **Vico** (Apache-2.0, active, line/column/candlestick layers,
markers) · ComposeCharts (Apache-2.0, lighter) · YCharts (dormant — do not
adopt) · kizitonwose/Calendar (MIT, has a HeatMap mode). **Adopted split
(proposed as R-D4):**
- Vico: energy-balance bar/line combo, sparkline hosts, generic line charts.
- Custom Canvas (`DrawScope`): the forecast cone (per-x band pairs +
  animated widening), macro/budget rings (trivial arcs), the progress ribbon
  (area-geometry between trend and N-day-ago line).
- kizitonwose Calendar or a ~150-line Canvas grid: month heatmaps.
All licenses GPLv3-compatible.

### 2.8 Glance widgets (constraints that shape F10's widget)

Glance runs on Compose but with its own restricted composable set — **no
`Canvas`** (the sparkline must be pre-rendered to a bitmap and shown via
`Image(ImageProvider)`), system fonts only in `TextStyle`, taps are broadcast
round-trips (`actionRunCallback`) so quick actions must be few and coarse,
`SizeMode.Responsive` recommended, **update floor 30 min** (`updatePeriodMillis`
silently coerced) with WorkManager 15-min floor — so the widget is
event-driven (refresh on weigh-in save) plus a daily schedule, not a poller.
One framework, F10-owned (R-U12 satisfied); the F04 list widget rides the
same Glance stack [v1.x]. Lock-screen redaction ("hide values" → dots) is a
required widget state.

---

## 3. Collisions with rulings — escalation log

Per the working rules, research findings were checked against FEATURES §3.
Result: **no pattern adopted here contradicts a frozen ruling.** Two tensions
found; both are design-level (not ruling-level) and are escalated as owner
questions with recommended options rather than designed around silently:

- **C1 — user-selectable gain/loss palette vs "no alarm-red anywhere."**
  F06 §4 explicitly adopts Happy Scale's user-configurable delta colors
  ("green/red/blue/purple selectable"), while the design bar and F07 reserve
  red for nothing and ban alarm-red. Resolution proposed (R-D5): WLO's own
  default rendering is a neutral, CVD-safe delta pair; the user may choose an
  accent pair from a curated palette; red is never a WLO default, never used
  by WLO's own urgency/anomaly rendering, and urgency is always
  position+copy, never hue. This honors both the spec's personalization
  clause and the no-red discipline.
- **C2 — MeThreeSixty's photo-compare pattern vs R-U16.** The category's
  compare-studio grammar (reference diff, wipe divider, synchronized scrub)
  is worth adopting, but its artifact is a photo. **Resolved by the ruling
  itself, no amendment needed:** WLO renders the identical interaction
  grammar on vector outlines. Nothing to escalate; recorded so no one
  "restores" photo placeholders into compare mocks (including empty states —
  R-U16 bans them there too).

Also verified non-collisions: dynamic color (surfaces-only, opt-in — no
ruling constrains theming); Inter under OFL (R-S1-compatible); M3 density
deviation (design-bar item, not a ruling); mealime cook mode / delivery
hand-off (rejected features — no UI adoption).

---

## 4. Design decisions proposed for freezing (R-D\*, pending owner approval)

These emerged from research as load-bearing, cross-feature, and worth the
same immutability as R-\* rulings (recorded in FEATURES §3 only after owner
approval; batched on ticket WLO-0011):

1. **R-D1 (candidate) — Theming:** dark-first fixed scheme is the base;
   Material You dynamic tint is opt-in and surfaces-only; semantic and
   data-viz colors are always WLO-owned.
2. **R-D2 (candidate) — Navigation:** five-tab bottom navigation —
   **Hub · Plan · Insights · Archive · Digestion** — with the F02 shutter as
   the Hub's primary action (F10 quick-action rail), and Settings (AI Studio,
   Data Vault, Scales) behind a single Settings entry. (Alternative considered
   and rejected: burying Archive/Digestion in a "more" sheet — the plan↔gut
   correlation and Archive are flagship surfaces, and both are already
   discretion-gated; hiding them reads as shame, which R-U7's naming ruling
   exists to prevent.)
3. **R-D3 (candidate) — Type:** Inter (OFL-1.1) as the single UI family,
   tabular figures mandatory wherever numbers change or align, display
   optical size for hero numerals.
4. **R-D4 (candidate) — Charts:** Vico for standard line/bar; custom Canvas
   for the forecast cone, rings, and progress ribbon; one heatmap
   implementation shared by F05/F09/F11.
5. **R-D5 (candidate) — Delta colors:** default neutral CVD-safe pair for
   gains/losses; curated user-selectable accents; red never a default and
   never an alarm (resolves C1).
6. **R-D6 (candidate) — Haptics:** the "kind haptics" rule — data outcomes
   always Confirm-class; Reject reserved for blocking errors (settings/
   integrity), never data results; haptics remain the feedback channel when
   reduced-motion is on.

Additionally fed to the owner-question batch (not ruling-grade): badge visual
direction (FEATURES §6.7 — recommendation: minimal geometric, numbers-first,
no illustration), F09 pain-input granularity (0–10 slider vs 4-step chips),
F08 Archive icon discretion review, and the F06×F07 milestone-range boundary.

---

## 5. Anti-patterns — the "never" list (enforced at design review)

Collected from all 19 docs; each is named in the specs' own anti-pattern
sections or design bar. These are review gates for every prototype:

1. **Shame colors/copy** — red "over budget" numerals, "you missed/failed/
   cheated," guilt alerts (MacroFactor's explicit rejection is its moat;
   Foodvisor's guilt alerts drove deletions).
2. **Paywalling capture or history** — MFP barcode wall (class action), Hevy
   3-month cap, MeThreeSixty scan-history paywall, SnapCalorie 3-scans/day,
   plop paywalled doctor export. WLO: capture and history free forever.
3. **Black-box adaptation** — numbers change with no visible why (Fitbod's
   named weakness). WLO: provenance + Algorithms page + decision ledger.
4. **Streak punishment / monetized repair** — Cal AI's $0.99 restore.
5. **Forced, unskippable gamification** — Foodvisor's pet blocking logging.
   WLO: number-native only, celebration confined to F11 moments.
6. **Burying the numbers** — MFP 2026 "nutrients three levels deep"; moving
   learned features (Yazio Trends churn); redesigning against data-at-a-glance
   (Cronometer 2023). WLO: any metric ≤3 taps (F11 objective).
7. **Unclamped / uncorrectable AI output** — the 27M-kcal candy bar;
   PoopCheck's no-override classifier. WLO: sanity rails + equal-status
   manual path (R-U15).
8. **Interruptive prompts** — upsells mid-flow, review prompts, unskippable
   AI interstitials after logging (Yazio's "3 tips" screen).
9. **False precision** — single promised dates, no uncertainty (MFP
   projection line, naive Happy Scale predictions). WLO: bands or ranges,
   "an estimate, not a promise."
10. **State loss** — check-offs lost on plan edit (Mealime's bug — WLO's
    reconciliation contract exists because of it), progress lost on navigate.
11. **Trap ergonomics** — bottom-anchored Cancel/Next mis-taps (Paprika
    review), rigid servings (Mealime 2/4/6), multi-step everything.
12. **Spinner-that-implies-a-server / cloud-only paths** — offline must be a
    non-state (F10 §9); Mealime's cloud death is the cautionary tale.
13. **Opaque capture failures** — guards that reject without naming the fix
    (MeThreeSixty "refused to start… says there's clutter"). WLO: every
    rejection names guardrail + fix (F08 spec).
14. **Silent data mutation** — collapsing weigh-ins, rewriting history.
    WLO: event-level storage (R-B8), visible re-settles (F06 300 ms ease).

---

## 6. Where it all lands — the one-paragraph take

The category's best-reviewed screens already agree on WLO's design bar — dark,
dense, typographic, trend-first, uncertainty-honest — and **nobody ships it on
Android with local-first architecture, published math, or a real correction
loop**; the polish lane (M3 motion, haptics, forecast cones, goal celebrations)
is, per the Waistline/openScale analyses, WLO's clearest differentiation axis.
The design system therefore treats three things as sacred: the number (tabular,
provenance-chipped, never judged), the moment (capture, weigh-in, check-in —
each a crafted, sub-10-second ritual with kind haptics), and the exit
(every path lands on manual, on-device, on-data — never on a wall, a spinner,
or a verdict).
