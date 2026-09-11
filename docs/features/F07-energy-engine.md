# F07 — Energy & Metabolism Engine — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F07 — Energy & Metabolism Engine |
| **Provides** | An on-device adaptive-TDEE engine: it *measures* the user's real energy expenditure from intake vs. weight-trend, closes the energy-balance triangle, forecasts goal dates in three honest bands with deceleration, and runs the weekly check-in ritual where — and only where — targets change. |
| **User problems solved** | • "Why am I stalling at 1,800 kcal?" → because 1,800 was a formula's guess; the engine measures *your* burn (2,410) and adapts the plan to it • Plateaus stop being failures: a flat trend *raises* measured TDEE, and the plan follows upward • Goal dates stop lying: every competitor extrapolates a straight line; WLO models deceleration near goal • Targets never change behind the user's back: the engine proposes, the human applies |
| **AI consent category** | Owns none — **the engine is 100% deterministic local math over local data**. No ML model, no cloud call, no consent needed; it works identically in airplane mode. This is deliberate: the flagship feature of a privacy-first app must be provably local. The optional **Discuss** path in check-ins hands off to F11's `insights-chat` (consent owned there/F12). |
| **Primary evidence** | `docs/research/macrofactor.md` (deep: V3 spline expenditure, adaptive per-user time constant, published accuracy gains, weekly check-in, Optimistic ETA), `docs/research/zolt.md` (deep: published closed-form TDEE, developing/updating/held states, Apply/Keep/Discuss, breakdown terms, calorie floor, no-eat-back), `docs/research/happy-scale.md` (forecast gap — no deceleration model), `docs/research/synthesis.md` §1.5, §2 Tier A #1/#3/#7, §4 (eat-back rejection) |

## 1. Purpose & Core Objectives

Every diet app asks "how many calories should you eat?" and answers with a formula
written decades ago. MacroFactor and Zolt proved the better question — *how many
do you actually burn?* — solvable from two signals WLO already has: logged intake
(F02/F03) and the weight trend (F06). F07 solves it continuously, on device, with
published math, and turns the answer into a weekly ritual: one card, one decision.
It is the keystone that turns three separate trackers (food, exercise, weight)
into one energy model — the synthesis neither diet apps nor gym apps ship.

Core objectives:

- **Measured, not formula-fed:** TDEE is reverse-engineered from the user's own intake-vs-trend data, converging in ~2–3 weeks of ordinary logging.
- **Published and auditable:** every constant, formula, and state rule lives on an in-app "Algorithms" page; every displayed number opens a "How we got here" trail down to its raw inputs.
- **Refuses to guess:** data-quality states (developing / updating / held) gate everything; weak data *holds* the estimate, never silently publishes a shaky number.
- **Targets change only by explicit Apply:** F01 sets initial targets; F07 proposes adjustments weekly; nothing writes without the user's tap.
- **100% local:** the entire engine is a pure function over the local vault — the flag-bearer of the privacy pitch ("your metabolism, computed on your phone, forever, free").

## 2. User Moments — when and how it is used

- **Cadence:** continuous background recompute (cheap, incremental); one **weekly check-in ritual** (default Sunday 19:00 — the number a geek checks every Sunday); episodic deep-dives at goal-setting (with F01), plateau, and diet-break moments.
- **Physical/emotional context:** couch, end of week, coffee or wine — reflective, not evaluative. The check-in must feel like a coach's debrief the user *runs*, not a report card they receive. Mid-week, the engine is invisible: it never nags about calories.
- **Episodic moments:** *first convergence* (~week 2–3, DEVELOPING → UPDATING — celebrated quietly: "your burn is now measured, not guessed"); *first hold* (explained, never alarming); *plateau inflection* (TDEE rises — reframed as adaptation); *goal approach* (deceleration becomes visible on the cone and the copy switches to range-speak).
- **The single most common flow (check-in, 60–90 s):**
  1. Sunday evening card surfaces (F10 Hub + soft notification): status chip, trend, pace, adherence, measured TDEE vs. last week.
  2. The proposed new target row shows the delta ("2,150 kcal, +50 · 165P/215C/70F").
  3. User taps **Apply** (targets update with a commit haptic) or **Keep** (engine still updates; targets roll over) or **Discuss** (why-this-change trail, optional F11 chat).
  4. The decision is appended to the check-in ledger. Done — next decision in seven days.

## 3. How It Works — functional mechanics

**Inputs**

- From **F02/F03:** daily intake energy + macros (every day carries a status: **Logged / Skipped / Fasted** — one tap in the day header; the cleanest fix for missing-data corruption, Zolt pattern).
- From **F06:** daily scalar weight series, trend weight series, residual σ, coverage % — under F06's frozen import semantics (lowest-of-day, noon-normalized).
- From **F05/F13:** session expenditure estimates and steps — *context only* (see below).
- From **F01:** initial targets, goal, pace, calorie floor; from profile: sex/age/height/weight.

**Processing — the math, in the open**

- **Trend consumption:** F07 never smooths weight itself; it consumes F06's trend series (one smoother, one owner). All pace math (`kg/week`) is computed on the trend.
- **TDEE solve — v1 engine ("Transparent mode"):** Zolt's published closed form on a rolling 14-day window:
  `TDEE ≈ avg_daily_intake − (weekly_trend_change_kg × 7700 ÷ 7)`
  (7,700 kcal ≈ 1 kg; 3,500 ≈ 1 lb — constants shown, unit-aware). Simple, verifiable, correct in expectation; it is the engine the user can check with a calculator.
- **TDEE solve — v1.x engine ("Adaptive mode"):** MacroFactor-V3-lineage upgrade — spline-based smoothing of intake and trend-delta with a **per-user adaptive time constant** (the filter's memory fits the user's actual signal dynamics instead of a fixed window), degrading by *widening uncertainty* when days are missing rather than collapsing. Shipped as a selectable second mode; both engines always run, and a geek can overlay them on one chart. WLO's stance: publish expected convergence (~2–3 weeks), publish accuracy philosophy, and version every algorithm change in the Algorithms page — MacroFactor's trust flywheel, made fully local.
- **Auditable TDEE breakdown (frozen decomposition):**
  `Measured TDEE = BMR + Adjustment + Activity + Exercise context`
  - **BMR** — Mifflin-St Jeor on current weight (documented, cited).
  - **Adjustment** — the personal correction term: `measured − (BMR + Activity + Exercise-context rolling means)`. It absorbs formula error, metabolic adaptation, and untracked activity; watching it grow from 0 to +190 over a cut is the app's best story.
  - **Activity** — steps → kcal via a published mass-scaled linear fit (documented).
  - **Exercise context** — F05 session estimates averaged over the window, deliberately small weight.
  - **Hard rule — context, never credit:** steps and exercise *refine interpretation* of the solve, but the target is never increased by "calories earned" — no eat-back, ever. The intake-vs-trend solve already contains activity; crediting it again double-counts (Zolt and MacroFactor both reject it; synthesis §4).
- **Data-quality states (frozen):** see §4 gating table — developing / updating / held, with exact entry criteria. A **held** estimate is frozen at its last good value with the reason shown; holds auto-release when the trigger clears.
- **3-band goal-date forecast with deceleration (the open win):** every competitor extrapolates the historic rate linearly and turns optimistic near goal (Happy Scale's community-observed flaw). WLO's model:
  - **Deceleration core:** future TDEE is re-estimated at future bodyweight — BMR falls with weight, the Adjustment term is held, Activity follows the steps baseline — so the deficit shrinks as the user approaches goal, and the projected weekly rate *decays* along the path instead of staying constant.
  - **Expected band** integrates the decaying rate from the current trend + TDEE point estimate. **Optimistic / pessimistic** bands come from the trailing-28-day pace distribution (80th / 20th percentile) crossed with the TDEE estimate's uncertainty. Bands widen with horizon; the widening is itself displayed.
  - Rendered as a forecast cone on the weight chart plus three dates ("on trend for **Oct 14** · range **Sep 28 – Nov 2**"). Never a single promised date; Zolt's hedge is part of the copy: *an estimate, not a promise*.
- **Plateau, reframed structurally:** 14+ days of flat trend at steady intake mathematically *raises* measured TDEE. The engine surfaces this as the win it is — "Your burn measured 2,410 (+120 vs. last month). You adapted; so does the plan." — and proposes the upward target change. No shame language exists in the state machine.
- **Forecast, worked example (this is the copy shape):** trend 84.2 kg falling 0.5 kg/wk; measured TDEE 2,410; intake 1,900 → deficit 510. At +6 kg lost, BMR drops ~60 kcal → deficit ~450 → rate decays to ~0.44 kg/wk. The expected date integrates the shrinking weekly rate instead of dividing remaining weight by 0.5; optimistic/pessimistic integrate the 80th/20th-percentile pace with the TDEE uncertainty bound. Result: "Goal 78.0 kg — on trend **Mar 14** · range **Feb 24 – Apr 6**," with the cone on the chart showing why.
- **Target proposal logic:** pace error vs. F01's plan (faster/slower than intended) → proposed calorie delta, clamped by (a) the **calorie floor** (default 1,200 F / 1,500 M; user-overridable with a persistent acknowledgment — the engine proposes slower pace rather than breaching it) and (b) the **pace cap** (±1.0% bodyweight/week). Macro splits derive from the calorie delta by F01's diet-template rules. All proposals are advisory until Applied.
- **Algorithm versioning:** every estimate carries its engine version (`transparent-v1`, `adaptive-v1.x`); changing a constant ships as a new version with a changelog entry on the Algorithms page and a one-line notice on the next check-in card — MacroFactor's methodology-transparency contract, enforced in data.

**Outputs & artifacts**

- Daily expenditure series + trend; breakdown decomposition; check-in proposals; check-in decision ledger (date, input snapshot, estimate, decision — exportable via F13); forecast cone + 3 dates; energy-balance dataset.

**State owned by F07:** expenditure estimates and their states, the solve windows, forecast model, check-in schedule and ledger, adjustment history. **Never owned:** intake records (F02/F03), weight data (F06), targets *except* through the Applied-write path (see §7).

## 4. User Interaction Model

**Entry points:** F10 Daily Hub check-in card (weekly) + energy mini-card (daily, read-only); notification on check-in day; F11 report-card deep-links; F01 goal studio (initial targets reference engine state); widget ("burn 2,410 · trend ↓0.6").

**Data-quality gating — the states (frozen):**

| State | Entry criteria | Behavior |
|---|---|---|
| **DEVELOPING** | < 10 usable paired days in the trailing 21 (usable = intake Logged + weigh-in present, or the day explicitly Skipped/Fasted — and, per F02's provenance metadata, the day is not dominated by uncorrected low-confidence estimates; >50% uncorrected-estimate days are excluded from the solve and named in the explainer: "2 rough days excluded") | Estimate shown grayed with a wide range and "forming — about 2 weeks of normal logging"; no proposals; forecast disabled; check-in card still shows trend/pace |
| **UPDATING** | ≥ 10 usable paired days, no hold triggers | Normal operation: estimate updates, proposals flow, forecast live |
| **HELD** | Any trigger: ≥ 3 trailing days with no intake record *and no status mark*; weigh-in gap > 4 days; user-flagged atypical week (travel/illness marker); outlier screen fails (intake residual > 3σ) | Estimate frozen at last good value; reason chip on every surface ("3 unlogged days — can't compare like with like"); no proposals; auto-releases when the trigger clears |

**The check-in card — anatomy (frozen, top to bottom)**

1. **Status chip** — DEVELOPING / UPDATING / HELD (+ reason on hold).
2. **Trend row** — trend weight, weekly pace ("−0.6 kg/wk"), on/off-pace vs. F01's plan (neutral wording: "a touch slower than planned").
3. **Adherence row** — "6/7 days logged" with the week's day-status dots; missing-status days offer the Logged/Skipped/Fasted one-tap fix inline.
4. **Expenditure row** — measured TDEE, delta vs. last week, delta vs. the static calculator; tap-through to the four-term breakdown.
5. **Proposal row** — new calorie target + macro split, each with a delta chip ("+50"; "P 160→165 g"); tap-through to "how we got here."
6. **Actions** — **Apply** (primary) · **Keep** · **Discuss** (secondary). No dismiss-without-decision shame: swiping away equals Keep.

**Primary flows**

- *Happy path:* see §2's check-in flow. **Apply** writes targets (the only write path — atomic, ledgered, reversible via the ledger), **Keep** rolls over, **Discuss** opens the "why" trail and, if `insights-chat` consent is on, F11 chat grounded in the same numbers.
- *Missed check-in:* zero guilt. The card persists on the Hub for 7 days; the engine keeps computing; the next check-in simply covers the elapsed window. Notifications are a single soft nudge, never escalating.
- *Held state:* the user taps the reason chip → a plain-language explanation + the fastest fix ("mark those days Skipped, or log tomorrow's weigh-in"). Fixing data is one tap, never an interrogation.

**Input minimization**

- The engine demands *no new input classes*: it eats what F02/F03/F06 already collect. Its only asks are one-tap day statuses (Logged/Skipped/Fasted) and an optional atypical-week flag — both pressable from the check-in card itself.
- Never typed: TDEE, pace, adherence, forecast dates, breakdown terms.

**Micro-interactions**

- **Check-in reveal:** the card opens with a 500 ms sequence — status chip stamps in, trend numeral odometer-rolls, the expenditure line draws left-to-right, and the proposal row slides up last with a subtle rise. The rhythm says: *this week's story, then your decision.*
- **Apply commit:** double-click haptic (crisp tick-tick), the new target numerals count from old → new, and a thin ledger line items itself beneath ("Applied · +50 kcal · Week 12"). Committing feels like signing.
- **Keep:** single soft tick; the proposal row folds away with "rolling over" — no nagging shadow.
- **Hold state:** amber pulse on the status chip (once, 600 ms — attention without alarm); the estimate sits at reduced opacity. Red is reserved for nothing in this feature.
- **Forecast cone:** on first render the cone expands outward from the trend line (700 ms ease-out); switching bands highlights each date with a tick haptic.
- **Plateau moment:** when measured TDEE rises through a stall, the breakdown card's Adjustment term glows once and ticks upward — the app's quiet applause.

## 5. What the User Gets Out

- **The hero numbers:** measured TDEE (+ delta vs. last week and vs. the static calculator estimate — "measured 2,640 · calculator says 2,450"), trend weight, weekly pace, adherence fraction ("6/7 days logged"), forecast dates.
- **The energy-balance triangle (WLO's synthesis, no competitor closes it):** one chart — intake bars vs. expenditure line vs. trend-weight slope — with the running deficit shaded. The user *sees* why the trend moves.
- **The TDEE longitudinal chart:** calculator-estimate baseline vs. measured burn over weeks, annotated with the user's own event markers ("Started running," "Sick week," "Vacation") — projection as a self-experiment the user understands (Zolt's best chart, done locally).
- **Check-in ledger:** every weekly decision, browsable — a personal experiment log, exportable.
- **Provenance rule:** every number opens "How we got here" — the formula with constants, the exact input window (dates, days used, days excluded and why), the current state and its reason. The Algorithms page documents all versions, convergence expectations, and missing-data behavior — WLO's published-methodology page, in-app.
- **The Algorithms page (first-class artifact):** the two engine specs in plain math with citations (Zolt's published formula lineage, MacroFactor's V3 design goals), the state machine, the forecast model, every constant, and a "reproduce it yourself" section — a spreadsheet recipe so any user can audit a check-in by hand. Publishing the recipe is the trust feature; MacroFactor's subreddit does their marketing, WLO's math does its own.
- **Visualizations owned by F07:** energy-balance chart, TDEE chart + breakdown bars, forecast cone with 3-band dates, check-in history timeline.

## 6. Motivation & Psychology

- The weekly return: *did my number move?* — a coach's debrief, a designed micro-event with a weekly dopamine loop (MacroFactor's retention core), minus the subscription.
- Adherence-neutral by construction: missed days update the model; they never scold. There is no "over budget," no red deficit, no failing state — the worst case is a **held** estimate that says "I can't see clearly this week."
- Gamification hooks **owned here, systematized by F11:** check-in streak hook, "convergence" milestone (first UPDATING week — the engine's birthday), Adjustment-term record, forecast-band hits ("expected date within range" — the engine bragging *accurately*). The deepest reward is epistemic: watching your own physiology become measurable.
- **Tone rules:** plateaus are adaptation, never failure; targets adapt to reality, reality is never asked to justify itself to the target; dates are estimates, never promises; the body is never called stubborn.

## 7. Relations to Other Features

- **Consumes from:** **F02/F03** — daily intake energy/macros + day statuses (Logged/Skipped/Fasted); **F06** — weight/trend series under frozen semantics (the single most important import); **F05** — session expenditure estimates (context term only); **F13** — steps from Health Connect (Activity term); **F01** — initial targets, goal, pace, floor, diet-template macro-split rules; **F12** — nothing for the engine itself; `insights-chat` consent only if Discuss hands off to F11.
- **Feeds into:** **F01** — adaptive target adjustments (via Apply only); **F10** — the weekly check-in card, daily read-only mini-cards, Sunday nudge; **F11** — check-in/streak/record events, TDEE data for report cards and the annual recap; **F06** — milestone-date rendering requests (forecast values); **F13** — ledger and series export.
- **Shared concepts:** Targets (write authority split: **F01 owns initial targets; F07 owns adaptive adjustments; both write through the same auditable, user-confirmed path** — no other feature may write calorie/macro targets); Trend weight (computed in F06 only); Provenance; Day statuses (Logged/Skipped/Fasted — captured in F02's day header, consumed here).
- **Conflict/boundary notes:** F07 never edits food logs, never judges individual days, never displays guilt-framed budget states. Exercise estimates enter only as the small context term — structurally incapable of raising eating targets (enforced in the write path, not by convention). If F06's trend is gated, F07's forecast is gated — the chain refuses upstream noise.

## 8. Blue Sky Ideas

- **[v1] Three-band forecast with deceleration** — the gap in all 18 researched apps, as the default forecast. Honest dates near goal are the feature's loudest differentiator.
- **[v1.x] Dual-engine geek switch** — Transparent (closed-form) and Adaptive (spline, per-user time constant) run side by side, overlayable; trust through comparison, not authority. v1 ships Transparent only (R-A3); the switch arrives with Adaptive.
- **[v1] Decision ledger** — every check-in's inputs and decision recorded and exportable: the user's cut becomes a citable n=1 experiment.
- **[v1.x] Scenario simulator** — "what if +3,000 steps/day?" or "what if I take a diet break week?" — the model recomputes forecast bands live under edited assumptions, fully local.
- **[v1.x] Diet-break choreography** — plan a 7–14 day maintenance break; the engine walks the calorie path, predicts the water-weight bounce before it happens, and shows the post-break TDEE re-measurement as the break's payoff.
- **[v1.x] Adjustment-term storytelling** — a timeline of the personal correction term with the user's own event annotations, rendering metabolic adaptation as a visible, explainable curve over months.
- **[v1.x] Reverse check-in (bulking/maintenance parity)** — the same engine, states, and ritual for weight-gain and maintenance goals, so the tool outlives the cut; the forecast's deceleration model runs symmetrically.
- **[future] Maintenance graduation** — at goal, the engine pivots seamlessly to a maintenance program with a tight tolerance band and "stability streak" hooks for F11 — the phase every other app abandons.
- **[future] Engine telemetry page** — live internals for geeks: window contents, excluded days, uncertainty, time-constant estimate — a "flight recorder" for the algorithm.
- **[moonshot] Bayesian engine** — a particle-filter posterior over TDEE (not a point estimate): every chart shows the distribution; proposals cite probability ("90% chance your burn is 2,380–2,520"). Still 100% on-device.
- **[moonshot] Self-experiment mode** — Gyroscope-Labs-style A/B on oneself ("does more protein move my trend?") with the engine as the measuring instrument and pre-registered hypotheses in the ledger.

## 9. Guardrails, Privacy & Sensitivity

- **The privacy flagship:** zero network, zero models, zero consent surface — provable by architecture. The check-in works in a bunker. This is stated on the card itself, once: "Computed on your device."
- **Write authority is sacred:** targets change **only** through explicit Apply; Apply is always user-tapped, never scheduled, never batched; every write is ledgered and reversible. F07 proposals are advice even when the engine is certain.
- **Safety rails:** calorie floor enforced at proposal time (with medical-disclaimer-grade care if overridden); pace cap ±1% bodyweight/week; sustained under-floor or over-cap patterns surface a gentle professional-guidance card, once, dismissable — no nagging, no lockout.
- **No medical claims:** TDEE is "measured expenditure estimate," never diagnosis; metabolic language stays descriptive ("your burn," "your adaptation"), never clinical.
- **Free forever, local forever:** the engine is the feature most competitors charge monthly for; WLO gives it away with the math attached — that contrast belongs in the store listing, not behind one.

## 10. Open Questions

- **Engine-mode default:** ship Transparent as default with Adaptive opt-in [current lean], or Adaptive default for everyone at v1.x — affects the "2–3 weeks convergence" messaging and needs accuracy benchmarking first. *(Resolved for v1: R-A3 — Transparent only; Adaptive ships v1.x opt-in and becomes default only after benchmarking.)*
- **Constants:** 7,700 kcal/kg vs. 7,716 vs. tissue-composition-aware values during aggressive cuts — publish one, but which? Affects cross-app comparability (Zolt uses 3,500/7,700 convention). *(Resolved: R-A1 — 7,700 kcal/kg, 3,500/lb.)*
- **Energy partitioning:** should the forecast assume fat-loss fractions (lean-mass preservation from F05 training context) when projecting bodyweight change — more honest, but more assumptions to publish.
- **Check-in cadence choice:** weekly frozen (ritual value, Zolt/MF precedent); should power users unlock daily cadence, and does that dilute the ritual?
- **Target-contract schema:** the exact `Targets` object (fields, versioning, write API) shared by F01/F07 needs a master-doc ratification — it is the app's single most politically loaded schema.
- **F11 Discuss handoff:** when `insights-chat` is off, Discuss renders the local explainer only — confirm no F11 nudge toward enabling cloud AI is acceptable (lean: a one-line note, never a prompt). *(Resolved: R-C7 — local explainer plus one informational line; never prompts to enable cloud.)*
