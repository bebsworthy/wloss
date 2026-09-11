# F05 — Exercise Planning & Tracking — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F05 — Exercise Planning & Tracking |
| **Provides** | A friction-free strength/cardio logbook (Hevy-class logging loop) with deterministic, explainable workout adaptation — feeding energy expenditure to F07 as *context, never credit*. |
| **User problems solved** | • Logging a set takes 3 taps instead of 30 keystrokes, even with sweaty hands mid-rest • "What do I do today?" is answered by a visible, verifiable rule — not a black box • Progress is legible: PRs, e1RM, volume, and a muscle heatmap that shows its own formula • Exercise finally connects to diet and weight — without the "eat back your burn" trap |
| **AI consent category** | Owns none. Consumes none in v1 — the adaptation engine is deterministic rules over local data (explainability is the feature). [future]: may consume the `insights-chat` toggle for natural-language program discussion. |
| **Primary evidence** | `docs/research/hevy.md` (logging loop, rest timer, plate calculator, PR banners), `docs/research/fitbod.md` (recovery model, equipment profiles, one-tap-confirm), `docs/research/macrofactor.md` (exercise-as-context, no eat-back), `docs/research/synthesis.md` §1.4, §2 Tier A #6 |

## 1. Purpose & Core Objectives

Exercise in WLO has two jobs: keep the user *training consistently* (the strongest
lean-mass preserver during a cut), and *measure* what training costs energetically
so F07 can interpret the trend. F05 is the plan-log-progress loop: routine
templates as first-class artifacts the user authors and owns, a logging loop fast
enough for month-three adherence ("friction is the silent killer of programs"),
and an adaptation engine whose every suggestion can be read as a sentence.

Core objectives:

- **A working set is logged in ≤ 3 taps** (open set → confirm prefilled weight/reps → done), zero mandatory keyboard.
- **Routine templates are first-class artifacts**: create, duplicate, version, export/import (via F13), and instantiate into sessions — never a marketing funnel.
- **Every suggestion is explainable in one line** ("all sets hit top of 8–10 range → +2.5 kg next session"); the user can always trace suggestion → rule → inputs.
- **Expenditure flows to F07 as context only.** WLO never adds "exercise calories" back to the eating target (Zolt and MacroFactor both reject this; it double-counts what the adaptive TDEE already absorbs).
- **All history, forever, on device** — the anti-Hevy guarantee: no 3-month graph cap, ever.

## 2. User Moments — when and how it is used

- **Cadence:** 3–5 sessions/week, each 30–75 min, touched between every set (~every 90 s). Plus one planning moment/week (couch, Sunday) and micro-glances at PRs/heatmap after sessions.
- **Physical/emotional context:** gym floor or living room — standing, gloves on, phone propped, rest clock running. The user is *mid-task*: every interaction must survive one thumb and 5 seconds of attention. Planning context is the opposite: relaxed, coffee, forward-looking.
- **The single most common flow:**
  1. F10 Daily Hub card "Push A — 6 exercises, ~45 min" (or widget) → one tap starts the session, prebuilt from the routine template.
  2. Every set arrives prefilled with last session's weight × reps → one tap per set confirms.
  3. Rest timer auto-starts; a plate calculator sits above the keyboard when load changes; a PR banner fires mid-workout when a set beats a best.
  4. Finish → 5-second summary (tonnage count-up, PRs, muscles hit) → session saved locally and handed to F07 as expenditure context.

## 3. How It Works — functional mechanics

**Inputs**

- User: routine templates, set logs (weight/reps/RPE/set-type), rest-time and plate-inventory settings, equipment profiles (Home / Gym / Travel), bodyweight-entry shortcut.
- From features: F06 current bodyweight (scales bodyweight-relative lifts and volume-load math); F13 Health Connect steps, cardio workouts, and heart-rate streams; F10 placement and nudges.
- Device sensors (optional): heart rate via connected monitor through F13.

**Processing — all on-device, all deterministic**

- **Progression engine (visible rules):** per-exercise rules the user can read, e.g. *double-progression*: "when all prescribed sets reach the top of the rep range at ≤ RPE 8 → add +2.5 kg (upper) / +5 kg (lower)". Rule library selectable per exercise; no opaque model.
- **Auto-adjust mode (Fitbod's UX insight):** the engine can pre-adjust the next instantiation of a template (loads, rep targets, exercise swaps) — the user experience is *one-tap confirm of a suggestion*, editable before save. Generated ≠ imposed: confirm-before-commit, always.
- **Recovery model [v1.x]:** per-muscle freshness 0–100%. Each set deducts recovery ∝ volume load × muscle-engagement coefficient (primary 1.0, secondaries 0.3–0.5); recovery regenerates exponentially toward 100% over ~6 days. The decay curve is *drawn*, with its constants shown — explainable, not mystical.
- **Muscle volume:** sets × reps × load weighted by engagement coefficients, aggregated per muscle per rolling window — the input to the heatmap.
- **e1RM:** Epley formula, per exercise, from best recent sets.
- **Expenditure estimate:** per session, from volume load, duration, body stats (F06), and HR data when present — labeled *estimated*, coarse by design, passed to F07 as context only.

**Cardio & steps (second-class by design, first-class enough)**

- Native cardio entries are minimal: type, duration, perceived effort, optional distance — three taps from the session screen. Depth comes from **F13**: Health Connect workouts, steps, and HR streams import automatically and appear on the same timeline as lifting sessions. (v1 scope ruled — R-S11: this minimal set + HC import is the whole of v1 cardio; native pacing/zone charts are later work.)
- Steps never require logging in F05; the F10 daily step ring and F07's Activity term consume them directly from F13. F05 renders them only as training context ("rest-day steps carried the week").

**Supersets & circuits**

- Exercises can be grouped into supersets (pair) or circuits (3+); grouped exercises alternate in the logging queue — completing a set of A tees up B, with the rest timer suppressed per-movement inside the group (Hevy's per-exercise override including "off").
- The group shares one rest clock; the queue animation walks A → B → A so the user never wonders what's next. Grouped volume still decomposes per muscle for the heatmap.

**Outputs & artifacts**

- Session records (sets, RPE, duration, PRs); routine template library; PR history; per-muscle weekly volume + recovery snapshots; expenditure context records for F07; shareable session "receipt" images.

**State owned by F05:** exercise library (authored minimal set for v1 per R-S2, growing via community additions; two-level primary/secondary muscle mapping — the keystone schema), templates, sessions, sets, PR records, equipment profiles, plate inventory, rest-timer config, progression rules, recovery model state.

## 4. User Interaction Model

**Entry points:** F10 Daily Hub "today's session" card; home-screen widget (start/pause); F11 post-session stats deep-links; F07 check-in card ("training context" row); notification resurrector for an in-progress session.

**Primary flows**

- *Happy path:* start prefilled session → tap-tap-tap sets → rest timer between → finish → summary. No keyboard unless the user deviates from last time.
- *Exercise unavailable (gym busy / travel):* long-press → substitutions ranked by target-muscle overlap ∩ current equipment profile; hotel-gym rerouting keeps the prescription buildable ("never ask for an unplate-able load").
- *Plate mismatch:* target load not buildable → calculator falls back with an honest chip: "Closest possible: 132.5 kg" — accept or adjust plates inventory.
- *Set-type flows:* warm-up sets log with a lighter row style and an optional warm-up ramp calculator (% ladder off the working weight); failure and drop sets are one-tap row badges; AMRAP sets prompt "reps achieved?" only when it differs from prescribed.

**Input minimization**

- Never typed in the common case: weight, reps (both prefilled from last session), rest times, cardio distance/duration (from F13), bodyweight (from F06).
- One-tap shortcuts: confirm all remaining sets as prescribed; duplicate last set; bodyweight-exercise menu → "Update bodyweight" (writes F06, updates all bodyweight lifts live).
- Per-set RPE is one tap on a 5-chip row — optional, never blocking.

**Micro-interactions**

- **Set complete:** checkbox morphs to a check with a spring squash; the row dims and contracts 4 dp; single sharp tick haptic (CLOCK_TICK-class). Feels like racking the bar.
- **Rest timer:** thin ring drains around the timer chip; at T−3 s a soft tick per second; at zero a distinct triple-pulse haptic + gentle chime; ±15 s buttons ripple on press with micro-scroll of the numeral.
- **PR banner:** slides up from the bottom of the set row, 400 ms, with a deep single impact haptic; shows old → new ("e1RM 102.5 → 105 kg"). No confetti — the *number* is the celebration. Toggleable.
- **Plate calculator:** plates drop onto a bar glyph with soft clicks, heaviest first, above the keyboard exactly where load is entered.
- **Session finish:** tonnage counts up over 800 ms; muscle glyphs pop in sequence; "Share receipt" renders the card as an image (Gyroscope card pattern).

**Data-quality gating**

- e1RM requires ≥ 3 logged sets of that exercise; recovery % requires ≥ 2 weeks of history before displayed; heatmap requires muscle-mapped exercises only; expenditure estimate hidden until bodyweight is known. Thin data → the feature holds and says so, never guesses.

## 5. What the User Gets Out

- **During:** prefilled sets, rest ring, PR banners, plate math.
- **After:** session summary; per-exercise history with e1RM and volume charts; PR history feed; sets-per-muscle-per-week (day/week/month/year granularity with preset ranges — all history, always, on device); volume-weighted muscle heatmap (7d / 30d / 90d).
- **Artifacts:** shareable session receipts; a monthly training recap (sessions, tonnage, PRs, muscle distribution vs. prior month) whose data F05 computes and F11 composes — a recap neither Hevy (no food/weight) nor Fitbod (no nutrition) can build.
- **Raw access:** the logbook is a browsable table (date, exercise, sets×reps×load, RPE) — geeks get the spreadsheet view, not just the pretty charts.
- **Provenance rule:** e1RM ("Epley: w × (1 + reps/30)"), heatmap ("Σ sets×reps×load × engagement: primary 1.0, secondary 0.3–0.5 — 30-day window"), recovery ("−X% from Monday's squats, +Y/day regeneration, ~6-day horizon"), expenditure ("estimated from volume+duration+weight; ±30% honestly") — every derived number tappable to its formula and inputs.
- **Visualizations owned by F05:** body-front/back heatmap (color intensity = volume, binary highlight is the fallback), recovery body-map with per-muscle decay curves [v1.x], per-exercise e1RM/volume lines, PR timeline.

## 6. Motivation & Psychology

- The itch: *did I get stronger?* answered within one session, plus the weekly *what should I do?* answered without decision fatigue.
- Gamification hooks **owned locally and offered to F11**: PR events, session-completion streak hook, milestone events (first 100 kg squat, 100th session), monthly recap data. F11 owns the badge/streak system; F05 only emits events and renders nothing competitive.
- **Tone rules:** no "you skipped leg day," no guilt for missed sessions — a missed week simply shifts the recovery map and the next suggestion ("Last session was 9 days ago — I dialed the loads back 5% so you come back safely"). Recovery framing, never scolding.

## 7. Relations to Other Features

- **Consumes from:** **F06** — current bodyweight (bodyweight lifts, volume-load, expenditure math) and weigh-in shortcut writes; **F13** — Health Connect steps/cardio/HR import, export of sessions, plate-inventory-free backup; **F10** — session card placement + nudges; **F01** — the cut's protein target as context for recovery messaging (display only).
- **Feeds into:** **F07** — per-session expenditure estimates and weekly training context (auditable input to the TDEE breakdown's "exercise context" term — context only, *never* eat-back credit; hard boundary); **F11** — PR/milestone/streak events and monthly recap data; **F10** — next-session suggestion and rest-day framing.
- **Shared concepts:** Provenance, Targets (read-only here), bodyweight bridge (Hevy's "Update Bodyweight" extended: today's weigh-in updates lifts, F07 context, and F08 simultaneously).
- **Boundary notes:** F05 never computes calorie targets, never edits the diet plan, and never writes to F06 except an explicit user-tapped bodyweight entry. Adaptation ends at *suggesting the next session*; F07 owns all energy math.

## 8. Blue Sky Ideas

- **[v1] Plate calculator with user-editable inventory** — bars (straight/EZ/short), plate set, kg/lb, per-equipment-profile; "closest possible weight" honesty chip. Cheap, disproportionately delightful.
- **[v1] Readable rule engine** — progression rules rendered as editable sentences; every session preview lists which rules will fire ("If you hit 3×8 @ 60 → 62.5 next"). The anti-black-box as a selling point.
- **[v1] Two-level muscle schema as the keystone** — primary/secondary muscle tags on every library and custom exercise from day one; every heatmap, recap, and suggestion derives from this one design decision, so it must be right before any stats exist.
- **[v1.x] Visible recovery decay curves** — per-muscle freshness with the exponential drawn on the body map; pinch a muscle to see its 6-day trajectory and what moved it. Beats Fitbod by *showing the math* and later accepting sleep context (its blind spot).
- **[v1.x] Template "auto-adjust" mode** — keep authored templates as the primary artifact (predictable weekly structure) and let the engine propose per-instance tweaks behind a one-tap confirm — both Hevy's and Fitbod's products in one.
- **[v1.x] Plateau dialogue** — when an exercise stalls 3 sessions, surface options as cards: deload −10%, rep-range swap, substitution — with the reasoning shown. No fixed "back off a few percent" silence.
- **[v1.x] Import bridge** — Hevy/Strong CSV import so the logger's built-up history moves in; a local-first app should honor training history that already exists.
- **[future] Wear OS companion** — set confirm, rest ring, plate glance from the wrist; Fitbod's weak Wear OS app is an active churn driver WLO can exploit on Android-first ground.
- **[future] HR-informed conditioning** — Bluetooth strap zones via F13; cardio sessions contribute zone-minutes to the heatmap's cardio ring.
- **[moonshot] On-device form check** — camera pose estimation (local model) flags depth/rom on squats; processed and discarded on-device, silhouette-grade privacy.
- **[moonshot] Fatigue-budget autoregulation** — RPE trend × recovery state → automatic daily readiness suggestion ("top sets at RPE 8 today"), fully rule-based and explainable.

## 9. Guardrails, Privacy & Sensitivity

- **Local-first hard rules:** every template, session, and PR lives in the local vault (F13); export/import is plain files; no account, no cloud component, free forever — full history is *never* paywalled (Hevy's #1 complaint is WLO's standing advertisement).
- **No social layer:** no feed, no leaderboards, no location check-ins (synthesis §4). Sharing is a user-rendered image, nothing more.
- **Safety tone:** progression suggestions cap jumps (≤ 5–10%/session); deload suggestions appear after stalled/aching patterns; no medical claims; injury flags route to gentler substitutions, not lectures.
- **Expenditure humility:** workout calories are always shown as rough estimates with provenance and are structurally incapable of increasing eating targets (enforced in the F07 write path, not just by convention).
- **Camera features** (form check [moonshot]) process frames in memory only; nothing persists without explicit opt-in; no cloud category exists for them.

## 10. Open Questions

- **Exercise library licensing/attribution:** bundle an open exercise database vs. author a minimal WLO set with animated demos — needs a master-doc decision on content provenance rules. *(Resolved: R-S2 — authored minimal set (CC0) + community additions; no proprietary bundled database.)*
- **Recovery-model calibration:** Fitbod's ~22%/exercise heuristic is community reverse-engineered; WLO needs its own default constants — published and tweakable, but which baseline?
- **Cardio depth in v1:** minimal logging + Health Connect import only, or native pacing/zone charts from day one? *(Resolved: R-S11 — minimal + HC import.)*
- **Schema sharing with F13:** should the session/set schema be designed for open export first (Hevy-compatible CSV) to enable migration tooling?
- **Who renders the muscle heatmap** — F05 owns computation and the chart, but F11 wants it inside weekly reports; propose F05 exposes a renderable component, F11 embeds it.
