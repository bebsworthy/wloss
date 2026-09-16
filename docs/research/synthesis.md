# WLO Competitive Research — Synthesis

*Derived from 19 deep-dive analyses in `docs/research/` (18 apps + 1 category study).
Date: 2026-09-10. Companion doc: [`../objective.md`](../objective.md).*

---

## 0. The field at a glance

| # | App / doc | Category | One line |
|---|-----------|----------|----------|
| 1 | [MyFitnessPal](myfitnesspal.md) | Mainstream calorie counter | Cloud incumbent; AI Coach + GLP-1 tracking; barcode-paywall backlash |
| 2 | [Lose It!](loseit.md) | Mainstream calorie counter | Value alternative; proven AI-voice logging; calorie cycling |
| 3 | [Yazio](yazio.md) | Mainstream calorie counter | EU fair-tracker; **"no sign-up required"** as marketed feature; free AI photo |
| 4 | [Cronometer](cronometer.md) | Nutrient-geek tracker | Lab-verified DB, ~95-nutrient microscope; "verify-then-log" AI |
| 5 | [MacroFactor](macrofactor.md) | Nutrient-geek tracker | Adaptive-TDEE-as-algorithm; weekly coached check-in; published math |
| 6 | [Zolt](zolt.md) | Adaptive-TDEE coach | AI-native energy coach; TDEE status states; iMessage/MCP agent |
| 7 | [Cal AI](cal-ai.md) | AI-photo tracker | Photo-first viral tracker (now MFP-owned); text-steerable correction |
| 8 | [SnapCalorie](snapcalorie.md) | AI-photo tracker | Research-grade CV; LiDAR volume; 100+ nutrients from a photo |
| 9 | [Foodvisor](foodvisor.md) | AI-photo tracker | European ecosystem; structured correction modal; pet gamification |
| 10 | [Mealime](mealime.md) | Meal planner + shopping | Auto plan→aisle-sorted list pipeline; **shutting down Oct 2026** |
| 11 | [Paprika](paprika.md) | Recipe manager + shopping | Best-in-class unit-aware list, pantry, aisle learning; one-time price |
| 12 | [Hevy](hevy.md) | Exercise tracker | Friction-free strength logbook; PR banners; muscle heatmap |
| 13 | [Fitbod](fitbod.md) | Exercise planner | Per-muscle recovery model; one-tap-confirm generated workouts |
| 14 | [Happy Scale](happy-scale.md) | Weight trend/forecast | Trend-weight smoothing + goal-date prediction; 4 selectable smoothers |
| 15 | [MeThreeSixty](methreesixty.md) | 3D body scan | 2-photo scan → avatar, 14 circumferences; silhouette-not-photo privacy |
| 16 | [Gyroscope](gyroscope.md) | Quantified-self design | The design benchmark: shareable cards, report cards, Health Score |
| 17 | [Poop trackers](poop-trackers.md) | Category study | PooLog, Bowelle, Poop Map, Poo Keeper, plop, Happy Poop, Cara Care, PoopCheck |
| 18 | [Waistline](waistline.md) | FOSS local-first | Open Food Facts/USDA, IndexedDB-only; zero polish, zero AI |
| 19 | [openScale](openscale.md) | FOSS smart scales | ~68 Bluetooth scale drivers; body-fat formulas; "no internet permission" |

---

## 1. Features similar to the ones WLO already plans — validation

Every v1 feature survives contact with the market. What changes after research is
**where the quality bar is** and what each incumbent got wrong.

### 1.1 Multi-diet plan via templates ✅ validated
- Present as: Yazio programs + fasting plans, Lose It! calorie schedule/cycling,
  Cronometer diet templates + Highlights, Mealime's 8-diet preference filtering,
  MacroFactor program styles (coached/collaborative/manual).
- **Bar to clear:** templates must be *first-class editable artifacts*, not marketing
  funnels. No incumbent fuses a user-editable template with an adaptive engine —
  MacroFactor adapts targets but its "program" is not user-authorable; Paprika has
  authorable "menus" but zero nutrition intelligence. WLO owns that fusion.
- Lesson from Lose It!: weekday/weekend calorie scheduling is loved and cheap to build.

### 1.2 Meal planning & tracking with nutrient estimation ✅ validated
- Table stakes everywhere; the bar is Cronometer: lab-verified sources (NCCDB/USDA),
  full amino-acid + micronutrient panels, nutrient Balances/Highlights, and a
  "Suggest Food" engine that fills *remaining* daily targets.
- 2026 reality: **photo-AI logging is now the standard entry point** (Yazio made it
  free; MFP, Cal AI, SnapCalorie, Foodvisor, MacroFactor all ship it). A 2026 tracker
  without camera-first logging feels dead on arrival.
- Anti-pattern learned: MFP's crowd-sourced DB noise; Cal AI shipping unclamped
  outputs (a 27M-kcal candy bar); category-wide underestimation on mixed dishes.

### 1.3 Auto-generated shopping list ✅ validated — and the market just got emptier
- Paprika defines the data model: unit-aware consolidation (2+3 eggs = 5 eggs),
  auto-aisle assignment that **learns from user corrections**, pantry with
  expiry/out-of-stock that auto-deducts staples, per-item recipe provenance, scaling
  that propagates into the list.
- Mealime defines the automation: preference-filtered plan → one consolidated
  aisle-sorted check-off list. **Mealime shuts down Oct 21, 2026** — its audience and
  its exact job-to-be-done are up for grabs.
- Nobody combines: auto list from a *nutrition-targeted* plan + pantry deduction +
  on-device nutrition estimation. All three is WLO's lane.

### 1.4 Exercise planning & tracking ✅ validated
- Hevy sets the logging bar: prefill weight/reps from last session (one tap per set),
  auto rest timer with ±15s nudges, configurable plate calculator above the keyboard,
  live PR banners, supersets, per-set RPE, watch apps.
- Fitbod sets the adaptation bar: per-muscle recovery % (~6-day decay), volume-weighted
  heatmap, equipment profiles, and "log = confirm the suggestion" UX — the exact loop
  WLO wants for photo logging.
- The gap neither touches: **exercise is disconnected from nutrition and weight.**
  Neither computes energy balance. WLO's intake+output+trend loop is unique in this set.

### 1.5 Weight & metrics tracking with forecasting ✅ validated — the launch core

Research evidence describes competitor behavior, not WLO's storage contract.
In particular, Happy Scale's lowest-of-day rule is one scalar candidate to
benchmark; it is not a deduplication rule. WLO preserves distinct source events
and Health Connect identity, then derives a separately versioned daily series.
The weight-first Release 1 boundary is authoritative in `FEATURES.md` §2.0.
- Happy Scale: trend weight with **4 selectable smoothers** (EWMA, 7-day MA,
  proprietary zero-phase smoother, Holt double-exponential), lowest-of-day import rule,
  goal-date predictions, named predictions ("Wedding Day") with a "How we got here"
  explainer, progress ribbon (green/red band = change vs. N days ago), Fresh Start mode.
- MacroFactor: EWMA-style trend → spline-based adaptive expenditure (V3, per-user time
  constant, published accuracy gains), weekly check-in ritual, "Optimistic ETA".
- Zolt: `TDEE = avg_daily_calories − (weekly_weight_change × 3500 ÷ 7)`, TDEE status
  states (developing/updating/held) that **refuse to publish from weak data**,
  auditable TDEE breakdown, weekly check-in card with Apply/Keep/Discuss.
- **Common gap:** no one models deceleration near goal — projections stay naively
  linear and turn optimistic. Optimistic/expected/pessimistic bands from an adapting
  rate is an open win.

### 1.6 Silhouette tracker via pictures ✅ validated
- MeThreeSixty: 2-photo scan (front+side) → 3D avatar, 14+ circumferences,
  live pose-skeleton alignment feedback, reference-scan diff, FutureMe goal slider.
  Its privacy *marketing* ("silhouette-not-photo") is undermined by a Facebook SDK —
  WLO can win by making on-device processing architecturally verifiable.
- Hevy ships private daily progress photos + side-by-side compare inside a gym app.
- **Gap:** nobody does local-only, grid/timeline silhouette comparison with pose
  alignment *and* measurement correlation. V1 grid + later pose-guided capture is enough.

### 1.7 Poop tracker ✅ validated — the most open niche in the study
- Bristol Stool Scale is the universal backbone; doctor-visit diaries are the #1 need
  (r/IBS, r/IBD). Category leaders are dead products or tiny; Poop Map wins installs on
  novelty, not health.
- plop is the reference for WLO's values: on-device statistics, personal baselines
  ("73% frequency drop vs your 1.6/day"), FODMAP-category correlation, flare
  early-warnings, PDF/CSV doctor reports, no tracking SDK.
- PoopCheck's AI photo classifier is the cautionary tale: cloud, no accuracy claim,
  misclassifies, **no manual correction path**.
- **Structural moat:** no app links stool entries to a *planned* meal plan or models
  fiber explicitly. WLO's meal planner + poop tracker correlation doesn't exist anywhere.

### 1.8 Local-first & minimal input ✅ validated as differentiators, not niche quirks
- Proven demand: Waistline/openScale thrive as FOSS; Yazio markets "no sign-up
  required"; MFP's barcode paywall caused a mass exodus and a class action; Hevy's
  3-month history paywall is its #1 complaint; Mealime's cloud death strand users.
- openScale's "**no internet permission**" is the gold standard of provable privacy.
- Input-minimization hierarchy proven across the set: **photo > voice > barcode/label
  scan > free-text hint > search**, with favorites/recents as the real workhorse
  (Cal AI "Food Memory", everyone's meal-copy). Lose It! published evidence AI input
  drives outcomes: 3.5× faster logging, 2× more entries, 6% more weight loss.

---

## 2. Features that would be a good fit to add (v1 or v1.x)

Ranked by (value to the numbers-geek) × (fit with local-first) × (cost).

### Tier A — flagship, build in v1
1. **Adaptive TDEE / energy-expenditure engine** *(MacroFactor, Zolt)* — pure on-device
   math over WLO's own intake + weight-trend data. Weekly check-in card: trend, pace,
   adherence, proposed target, Apply/Keep. Gated by data-quality states (hold on weak
   signals). This is the number a geek checks every Sunday.
2. **Trend weight with selectable smoothers** *(Happy Scale)* — EWMA default,
   zero-phase option, lowest-of-day import, "Trend 179.1, down 0.6" framing on every
   weigh-in. WLO rejected the competitor's progress ribbon as redundant visual encoding.
3. **Goal-date forecast with optimistic/expected/pessimistic bands** *(gap in all
   19 apps)* — every competitor extrapolates linearly and lies near plateaus.
4. **Photo-logging correction loop as the hero flow** *(SnapCalorie hints, Cal AI
   text-steering, Foodvisor correction modal, Cronometer verify-then-DB)* — photo
   pre-fills, chips/sliders/free-text refine, every value editable before save,
   per-scan confidence shown, outputs clamped to sane ranges.
5. **Unit-aware, learning shopping list with pantry deduction** *(Paprika + Mealime)* —
   auto-generated from the plan date-range, aisle order learned from corrections,
   check-state survives plan edits (Mealime's bug), staples auto-deducted from pantry.
6. **Exercise logging loop** *(Hevy)* — prefill from last session, rest timer,
   plate calculator, PR detection; plus **one-tap-confirm of suggestions** *(Fitbod)*
   for any generated plan.
7. **Energy-balance loop** — intake vs expenditure vs trend weight in one chart.
   No gym app and no diet app ships the full triangle; it's WLO's synthesis.

### Tier B — strong differentiators, v1 if affordable, else v1.x
8. **Poop ↔ meal correlation** *(gap everywhere)* — Bristol picker with photo
   pre-selection + one-tap override, fiber modeled explicitly, FODMAP-category
   correlation, flare early-warning, PDF doctor report *(plop)*.
9. **Silhouette capture ritual** *(MeThreeSixty)* — pose guidance with live feedback,
   fixed reference scan, side-by-side/timeline compare; photos stay on device.
10. **Voice logging** *(Lose It! "Say It!", SnapCalorie multi-ingredient voice)* —
    second rung of the input ladder; on-device STT is feasible in 2026.
11. **Barcode + label-photo scanning, free** *(Lose It!, Cronometer)* — backed by
    Open Food Facts + USDA FDC *(Waistline's dual-backend pattern)*, cached offline.
12. **Food Memory / recents as one-tap cards** *(Cal AI)* — statistically the most
    used feature in every tracker; make re-logging a single tap.
13. **Gamification for geeks** *(Gyroscope + MacroFactor + Hevy)* — forgiving streaks
    (freeze/restore, never charge for it — Cal AI's $0.99 restore is reviled),
    PR/milestone badges, weekly letter-grade report card per body system,
    **shareable single-stat cards** (Gyroscope's entire growth loop), annual
    Spotify-Wrapped-style report *(MacroFactor)*.
14. **Explainability everywhere** *(Happy Scale "How we got here", MacroFactor
    published math, Zolt auditable breakdown)* — every derived number (trend, TDEE,
    forecast, body-fat estimate) tappable to its formula and inputs; estimates carry
    measured-vs-estimated provenance *(openScale computes this but never shows it —
    surfaced provenance is a free win)*.
15. **Health Connect sync + Bluetooth scale support** *(openScale — FOSS reference for
    ~68 scale drivers and body-fat formulas; optionally reuse/credit it)*.
16. **Data portability as a feature** *(Waistline's versioned JSON export/import,
    plop's PDF doctor report)* — "Mealime is shutting down; your data isn't" is a
    marketing wedge WLO gets for free by being local.
17. **Fresh Start** *(Happy Scale)* — hide-not-delete remains a useful
    competitor pattern, but WLO rejected automatic lapse detection and the
    arbitrary 14-day return prompt (owner ruling 2026-09-16). Multi-profile is
    deferred while the schema remains partition-ready. A tap-to-reveal
    weight-number mode was also rejected as unnecessary product complexity.

---

## 3. Features to plan for a future version (post-v1)

| Feature | Source evidence | Notes |
|---|---|---|
| **GLP-1 / medication tracking** | MFP, Lose It!, MeThreeSixty (me360rx) all added it in 2025-26 | Now table-stakes-adjacent; fuses uniquely with WLO's symptom/poop tracker (side-effect correlation nobody ships) |
| **3D avatar / body-composition scan** | MeThreeSixty's 2-photo scan, FutureMe slider | Heavy CV investment; ship v1 silhouette grid first, add pose-guided capture + circumference estimation later |
| **AI coach chat over personal history** | MFP AI Coach, Crono Coach, Zolt iMessage agent | Only after remote-AI opt-in (user API key) infra exists; ground answers in local data, staged confirm-before-write *(Zolt MCP pattern)* |
| **Screenshot & recipe-URL import** | SnapCalorie screenshot import, Paprika web clipper | Great for migration/import funnels |
| **Recipe web importer + cook mode** | Paprika clipper, Mealime hands-free cook mode, multi-timer keep-awake | Natural meal-planner extension |
| **Wear OS companion** | Hevy watch parity is a top-3 praised feature | Log workouts & glance stats from the wrist |
| **Self-experiment / A-B framework** | Gyroscope Labs | Peak quantified-self; "does more protein move my trend?" with WLO's own data |
| **Population benchmarks & mini-games** | Gyroscope N-Back/plank baselines | Optional spice, strictly local stats first |
| **Grocery delivery hand-off** | Mealime ↔ Instacart | Conflicts with local-first; only as explicit remote opt-in |
| **Transit-time / regularity forecasting** | Category gap in poop trackers | "Fiber → next-day transit" prediction; needs weeks of data, pair with meal plan |
| **Home-screen widgets** | openScale | Daily hero-number widget |

---

## 4. Redundant / anti-features — deliberately skip

- **Paywalling users' own data or capture** — MFP barcode wall (class action), Hevy
  3-month graph cap (#1 complaint), MeThreeSixty scan-history paywall (most damning
  review), Fitbod trial cliff. WLO's counter-positioning: *capture and history are
  free forever; they live on your device.*
- **Human coaching / dietitian chat** *(Foodvisor, Gyroscope coaches)* — a service
  business, incompatible with local-first economics.
- **Social feed / leaderboards / location check-ins** *(Poop Map's map, Zolt group
  leaderboards, MFP friends)* — poison for a privacy-first product.
- **Ads or data-sharing of any kind** — plop's ads+subscription resentment;
  MeThreeSixty's Facebook SDK destroys its own privacy pitch; SnapCalorie's
  "public by default" policy clash.
- **Proprietary crowd-sourced food database at MFP scale** — doomed to noise; use
  Open Food Facts + USDA FDC with on-device cache *(Waistline's dual backend)*.
- **Virtual-pet / garden gamification** *(Foodvisor)* — unskippable animations on core
  flows alienate adults; WLO's gamification must be number-native (streaks, PRs,
  report cards), never a tamagotchi.
- **Cloud-only AI as the only path** *(SnapCalorie "requires internet", Zolt
  AI-as-only-interface)* — an AI-only or cloud-only input path breaks offline
  logging; local fallback is mandatory.
- **B2B vision APIs / practitioner portals** *(Foodvisor, Cronometer
  professional, Size Stream)* — different business, not this product.
- **Subscription-only pricing with serial hikes** *(Fitbod $59→$79→$96/yr)* —
  moot for WLO: it is **open source and free forever** (no paywall, no ads, no
  SaaS), so competitors' pricing misery is free marketing. Cal AI revealing its
  price only after an onboarding quiz is the cautionary tale the category told
  on itself.
- **"Eat back exercise calories" credit** *(explicitly rejected by Zolt and
  MacroFactor — wearables' burn estimates are context, not budget)*.

---

## 5. Cross-cutting lessons

**AI patterns (the 2026 rules of the category):**
- Publish accuracy *with methodology* or be dismissed *(SnapCalorie's CVPR/Nutrition5k
  credibility vs Cal AI's unpublished "80%")*; mixed dishes remain the weak spot
  everywhere (pho +49% error; dal thali misidentified) — set expectations per scan.
- Always editable-before-save; corrections should improve future personal estimates
  *(SnapCalorie's personalization loop)*.
- Depth/LiDAR volume estimation is the accuracy frontier *(SnapCalorie, Cal AI)* —
  ARCore gives Android the same capability on-device.
- Guardrails: clamp outputs, verify against trusted DB entries *(Cronometer
  verify-then-log)*, never auto-commit an AI decision without a visible confirm.
- **Per-capability consent for cloud AI** *(WLO principle, sharpened by this
  research: SnapCalorie's "requires internet" and public-by-default policy,
  MeThreeSixty's Facebook SDK)* — each AI feature category (food photo,
  meal planning, silhouette, poop photo, insights/chat) carries its own
  independent cloud opt-in, BYOK only; on-device is the default and the
  fallback when a toggle is off or the network is gone.

**Numbers presentation:**
- Trend-first framing on every entry; hero number per day → weekly report card →
  composite score on a fixed cadence *(Gyroscope's three-layer pyramid)*.
- Data-quality gating: refuse to show shaky derived stats *(Zolt's hold state,
  MacroFactor's missing-data tolerance)* — trust beats completeness.
- Card-as-artifact design: every chart should survive being screenshotted
  *(Gyroscope's entire growth loop)*; oversized typography, dark chart-forward
  aesthetic, 60fps motion as an engineering value.

**Monetization/positioning:**
- The four Wed WLO gets for free from this research: (1) on-device AI in a market
  where every AI tracker is cloud-only; (2) plan↔poop/fiber correlation that exists
  nowhere; (3) the energy-balance triangle (intake+output+trend) that neither diet
  nor gym apps close; (4) "Mealime just died — your planner shouldn't" portability.
- Yazio proved "no account needed" sells in the mainstream, not just FOSS circles.

---

## 6. Recommended v1 feature checklist (research-adjusted)

Core loop: **photo/voice/barcode → editable estimate → diary → trend + TDEE →
weekly check-in → adjusted plan → shopping list → next week.**

- [ ] Diet templates (authorable, weekday/weekend scheduling)
- [ ] Photo-first logging with correction loop, confidence, clamping, local fallbacks
- [ ] Food DB: OFF + USDA FDC, offline cache, barcode free
- [ ] Nutrients: calories/macros v1, micronutrient panels fast-follow *(Cronometer bar)*
- [ ] Shopping list: consolidated, aisle-learned, pantry-deducted, edit-proof
- [ ] Exercise: Hevy-style logging loop + templates + volume heatmap
- [ ] Weight: trend weight (2+ smoothers), named predictions,
      3-band forecast, explainers
- [ ] Adaptive TDEE with data-quality states + weekly check-in card
- [ ] Silhouette: guided capture, on-device timeline/compare, hidden gallery
- [ ] Poop: Bristol picker + photo pre-select + one-tap override, fiber & FODMAP
      correlation, doctor PDF export
- [ ] Gamification: streaks (forgiving), PRs/badges, weekly report card, share cards
- [ ] Provenance + "how we got here" on every derived number
- [ ] BYOK remote-AI layer with **per-capability consent toggles**
      (meal planning / food photo / silhouette / poop / insights), on-device default
- [ ] Health Connect + Bluetooth scale (openScale patterns)
- [ ] Versioned JSON/CSV export-import, PDF doctor report
- [ ] Fresh Start, multi-profile, biometric lock
