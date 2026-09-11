# Zolt — Feature Analysis

**Category**: Adaptive TDEE / metabolic coach + macro & body-composition tracker ("adaptive body coach"; energy-balance engine that recalculates maintenance calories from real intake-vs-weight data)
**Platforms**: iOS only (iPhone + iPad; released 2025-05-01; v2.6.7 as of 2026-09-08; requires iOS 16.4+). Official /android page (checked 2026-09-10): "Zolt is iPhone only today. An Android build is on the roadmap, and there is no Play Store listing to send you to until it ships." Also delivered as an **iMessage agent** ("Zolt Coach") and an **MCP server** (mcp.zolthealth.com) for external AI agents (Claude, ChatGPT, Cursor); Apple Watch app for sleep/recovery data.
**Pricing model**: Freemium with subscription tiers: Zolt Pro ~$4.99–$5.99/month, ~$19.99–$39.99/year (legacy tiers visible: $34.99 annual, $19.99 promo annual), and "Zolt Pro Plus" at $9.99/month / $79.99/year. MCP/AI-agent access requires Pro. Free web tools (adaptive-TDEE calculator, spreadsheets) act as funnel.
**AI usage**: Pervasive and cloud-based. iMessage/text-first AI coach with switchable personalities (Pro, Hype, Bestie, Roast, Villain, Philosopher); AI food logging from natural-language text and photos; AI food search; LLM weekly check-in analysis; per-user "Coach memories" stored as embeddings; an official MCP server exposing ~40 read/prepare/confirm tools so the user's own AI agents can query and write health data with a staged, confirm-before-write protocol. Nutrition data licensed from the FatSecret Platform API.
**Local & privacy posture**: NOT local-first — account-based, cloud-synced, server-side AI. The July 2026 privacy policy discloses collection of body weight, body-fat %, measurements, progress photos, food logs, sleep/HRV/RHR/SpO2, journal entries, AI prompts/responses, Coach memories, device identifiers, analytics, attribution, and crash data; integrations with Apple HealthKit, Android Health Connect / Google Health API, and Fitbit/Pixel services. Consumer-health-data notice (Washington My Health My Data-style) is published. MCP API keys are hashed, revocable; agent writes are staged and explicitly confirmed. By WLO's standards this is the opposite posture — a privacy-conscious user hands Zolt their full health record in exchange for the adaptive engine.

## Overview

Zolt (Zolt Labs LLC) is a young, AI-native answer to the adaptive-nutrition category occupied by MacroFactor, Carbon Diet Coach, and the community-built nSuns/3-Suns spreadsheet. Its thesis, stated in its manifesto: "Most people know when they have energy. Almost no one knows why." Zolt's engine "watches how your weight actually changes against what you actually eat, week over week, and recalibrates your true calorie target around your real life… It isn't based on a formula written in 1919." Where Happy Scale smooths weight to answer "when will I hit my goal?", Zolt closes the loop: it uses the smoothed weight trend *plus* logged intake to solve for the user's actual energy expenditure, then sets daily/weekly calorie and protein targets that adapt as metabolism shifts during a cut, bulk, or maintenance.

The product ships in three surfaces that share one context: (1) the iOS app (dashboard, food logging, trend charts, TDEE status, sleep/recovery scores, "Zolt Score"), (2) **Zolt Coach in iMessage** — "The calorie target that keeps up with you… Text it to log. Get the app. Free to start. Works in Messages. The app is optional." — which handles thirteen log types conversationally (food, photos, bodyweight, goals, measurements, workouts, injuries, water, journal, recipes, custom foods, edits, reminders), and (3) an MCP server that lets Claude/ChatGPT/Cursor read and (confirmably) write the same data. It is one of the most aggressive "agent-first" health products on the market as of 2026.

Market position: early-stage and small (≈62 US App Store ratings, 4.16 average as of Sept 2026) but actively developed (multiple releases per month, v2.6.7 on 2026-09-08), with a public Learn documentation set, an official subreddit (r/ZoltHealth), and comparison pages that position it directly against MacroFactor ("more mature nutrition-first logger"), Carbon ("deliberate weekly check-in and compliance-led structure"), Cronometer, MyFitnessPal, Cal AI, RP Diet Coach, and Bevel. Its claimed edge over those incumbents: the adaptive engine plus a conversational agent that "can take action for you."

## Feature inventory

### Weight logging UX
- Daily weigh-in logging in-app or by text: "weighed in at 178.4" → coach replies "Trend's 179.1, down 0.6" — the *reply always reframes the raw number as the trend delta*, which is the core UX trick.
- Automatic import from Apple Health (smart scales writing to HealthKit arrive with no manual entry); Health Connect/Google Fit supported in copy/roadmap (privacy policy covers both; the Android client itself hasn't shipped).
- Reminders by text ("remind me to weigh in at 8am" → "Every morning at 8. I'll be insufferable about it.").
- The app asks users to "weigh in regularly enough for Zolt to distinguish a trend from normal daily fluctuation" and weigh "under reasonably similar conditions."
- Weight history accessible to agents: `get_bodyweight` (up to 90 days), `get_weight_progress` ("seven-day trend, goal distance, and pace").

### Trend-weight & smoothing algorithm (CRITICAL)
Zolt maintains a **trend weight** distinct from daily scale weight (homepage example: raw 178.4 → trend 179.1, "down 0.6"). The exact filter is not published, but the documented engineering requirements and lineage are clear:
- The method descends from the **nSuns/3-Suns adaptive TDEE spreadsheet** (explicitly named in the FAQ: "the gold standard in fitness communities"), which uses a short weighted moving average of daily weights and a calorie-balance regression.
- The engine "waits for a useful pattern instead of treating each day as a separate verdict": "One meal or weigh-in cannot answer much. Food logging contains estimation error, and scale weight moves for reasons that have nothing to do with body tissue."
- Weekly pace is computed from the trend ("Pace 0.6 lb / wk" shown on the Sunday check-in card), so the smoothing horizon is on the order of a week, not 30 days.
- Data-quality gating is a first-class algorithm feature: the TDEE estimate carries a **status** — *still developing* (early), *updating normally*, or *held* — and Zolt "holds the current estimate instead of reacting to a weak signal" when logs are missing, weigh-ins sparse, days ambiguous (partial days must be marked **Skipped** vs **Fasted**), a data source disconnects, or the week was atypical (travel, illness). "Read the status before the number" is the user guidance.
- Displayed TDEE is decomposed into named components: **Metabolism (BMR) + "Zolt Adjustment" + Exercise + Movement** (example screen: 1,690 + 120 + 350 + 480 = 2,640), which makes the estimate auditable — the "Zolt Adjustment" term is the personal correction vs. the formula starting point.
- Foundational math, published verbatim on the adaptive-TDEE page: **TDEE = Avg. calories − (weekly weight change × 3,500 ÷ 7)** (3,500 kcal ≈ 1 lb; 7,700 kcal ≈ 1 kg). Accuracy guidance: "reasonably accurate after 2–3 weeks of consistent daily tracking… 4–6 weeks recommended."
- Wearable active-calorie burn is deliberately **not** credited into the target: "One workout, a high step count, or the active-calorie value on Apple Watch is not copied into the Adaptive TDEE target as a matching calorie credit." The energy model is intake-vs-weight only; wearables feed recovery/sleep context instead. This is a notable design rejection of the common "eat back exercise calories" pattern.

### Forecasting & goal-date projection (CRITICAL)
- **Goal builder**: choose direction (lose/gain/maintain), target weight, and **pace**; the builder shows a **projected goal date** with explicit hedging: "Treat the projected date as an estimate, not a promise. Real progress is rarely linear, and the projection can change as your data and goal change."
- **Cadence**: targets update on a **daily or weekly** cadence (user choice). Weekly is anchored by the **Sunday check-in**, which is the product's flagship projection ritual: a card showing Trend weight, weekly pace, on/off-plan status, adherence ("6 of 7 days logged"), and a **proposed new target** ("New target 2,150 cal, +50; 165g P / 215g C / 70g F") with three buttons: **YES Apply it / KEEP Roll over / ASK Talk it through**. The user approves the adaptation — the algorithm proposes, the human disposes.
- **Context-aware holds**: during disrupted weeks the engine holds rather than cuts — the site's dialog example: user says "was sick tue through thu" → "That explains the dip. Holding you at 2,100 this week rather than cutting further."
- **Plateau handling**: "Auto-adjusts when you plateau," "guides you through every plateau, push, or reset," and accounts for "metabolic adaptation." Because TDEE is re-solved from observed data, a stall *raises the measured TDEE* (intake unchanged, weight flat → higher expenditure estimate) and the target is adjusted upward/forward rather than prescribing ever-lower calories — the structural anti-plateau mechanism of the whole category.
- **Calculator-vs-measured framing**: the hero chart shows "Measured burn 2,640 cal/day, Week 16, +190 vs the calculator 2,450" with journal-entry markers ("Started running", "Two weeks away", "Sick week") annotated on the timeline — projection is presented as a longitudinal experiment, and the user's own events explain inflections. "A calculator gives you one number in January. Zolt gives you a new one every Sunday. It notices. You say why."
- **Guardrails**: a configurable **calorie floor** prevents aggressive goals from producing unsafe targets; the goal-builder tells users to slow the pace instead of working around it.
- Check-in math is agent-accessible: `get_checkins` ("weekly check-in history & TDEE estimates"), `preview_weekly_checkin`, `prepare_weekly_checkin_commit`.

### Body metrics beyond weight
- Body-fat percentage, height, body measurements (e.g., "waist 32.5 this morning" → "Down an inch since March with weight flat"), progress photos, and composition are collected (privacy policy lists "body weight, body-fat percentage, height, body measurements, weight trends, progress photos").
- Measurements are a first-class conversational log type in iMessage, and the coach cross-references them with weight ("Down an inch since March with weight flat") — recomposition insight that weight-only apps can't produce.
- No dedicated circumferences-charts feature is advertised in the App Store description; depth here is thinner than the nutrition/TDEE core. Body-fat entry is manual/user-provided (no smart-scale composition ingestion is documented beyond generic Health-platform sync).

### Integrations
- **Apple Health (HealthKit)**: bidirectional for weight/nutrition; "Trend weight · Energy & TDEE · Widgets · Apple Watch · Apple Health · Health Connect · Barcode scanning · Photo logging · Daily Zolt Score" are listed as app capabilities.
- **Apple Watch**: sleep stages, HRV, resting HR, SpO2, respiratory rate → recovery score and readiness ("push day or back-off day"); activity/workouts/steps; explicit guidance on how Apple Watch calorie estimates are treated (context, not target credit).
- **Health Connect / Google Health API / Fitbit / Pixel**: covered in the privacy policy and site copy; Android client not shipped yet (see Platforms).
- **Nutrition database**: FatSecret Platform API ("Powered by fatsecret"); barcode scanning works well in the US per community reports; plus a licensed **restaurant menu database** with build rules (Chipotle, Panda Express, In-N-Out, Chick-fil-A, Cava, Sweetgreen, ~40 chains) so customized bowls "count correctly instead of landing as a rough guess," and SEO nutrition-calculator pages per chain.
- **iMessage (App Store iMessage app)**: full logging agent + **group accountability**: add Zolt to a group thread and it runs a scoreboard ("Dev 1,840/2,180 · 142g P; Priyam 2,010/2,400 · 158g P…"), with opt-in JOIN, per-member accounts, and "the group only ever sees what the group asked for."
- **MCP server** (mcp.zolthealth.com, Pro-only, OAuth or `zhk_…` API key): ~40 tools spanning logs, nutrition summaries, adherence, TDEE, trends, workouts/PRs, hydration, plus **staged writes** (`prepare_food_log`, `prepare_bodyweight`, `prepare_weight_goal`, `confirm_action`, `cancel_action`) — "Nothing is written until you confirm the displayed final draft; the complete meal is then saved in one atomic commit."
- No Google Fit/Android app, no web dashboard beyond marketing/calculator pages; spreadsheets (Google Sheets/xlsx) offered as the manual alternative.

### Statistics, visualization & gamification
- **Trend-first dashboard**: trend weight with weekly delta, pace in lb/wk, on-plan status, adherence fraction (days logged / 7).
- **TDEE chart** with calculator-estimate baseline vs. measured burn over 16 weeks, annotated with the user's own journal markers; TDEE status chip (developing/updating/held) and estimated breakdown (Metabolism / Zolt Adjustment / Exercise / Movement).
- **Zolt Score**: a single daily 0–100 composite of "sleep, recovery, activity, nutrition, hydration, stress, and daylight," banded with playful names: 90+ Peak Day, 75–89 High Energy Day, 60–74 Productive Day, 40–59 Steady Day, <40 Light Day. Sleep and recovery scores show a **what-moved-it decomposition** ("Sleep duration −7, Resting HR −5, HRV −4, Prior-day load −2") — explainable scoring, not a black box.
- **Training-load metrics** borrowed from endurance sports: **TSB, ATL, CTL** ("cardio load metrics… guide intensity over time").
- **Streaks**: visible logging streaks ("12-day streak" on the iMessage transcript); streaks appear 11× in the store page.
- **Group leaderboards** via the iMessage group scoreboard ("The fastest accountability system ever built is four friends who can see each other's numbers. Zolt runs the scoreboard.").
- **Coach personalities** as motivational gamification: Pro, Hype, Bestie, Roast, Villain, Philosopher — "The numbers never change. The delivery entirely does." The coach's voice is deliberately witty ("That's a quarter of your day, delivered by cookie"; "660 left and 37g of protein on the board. Dinner is carrying this one."; "You're ahead of yesterday").
- Habit "Journeys" (longevity, performance, energy) grouping steps, cardio, sleep, hydration habits.

### AI features
- **Conversational logging** (iMessage, and in-app chat): NL food logging with macro estimation, photo logging ("Salmon, rice, broccoli. Around 610."), edits ("delete the boba" → "Gone. Back to 1,350."), recipes ("save this as my usual overnight oats"), custom foods, reminders, water, journal ("slept badly, work stress" → "You run about 300 over on bad-sleep days" — AI correlational insight).
- **Coach with memory and context**: prompts carry "relevant profile, goal, nutrition, weight, activity, sleep, recovery…" context; user-visible **Coach memories** with embeddings ("I'll be insufferable about it").
- **Weekly check-in analysis**: the LLM reviews the week, proposes target changes, and explains holds ("That explains the dip. Holding you at 2,100…").
- **MCP/agent access** for power users (see Integrations) — the rare health app that is itself an MCP client surface.
- **Injury/workout awareness** from text ("left knee is acting up again" → "Pulling the running days. Same knee as February?").
- All AI is server-side; there is no on-device inference. AI usage telemetry (model, token counts, safety events) is logged per the privacy policy.

### Input-minimization techniques
- **Text-as-UI**: the iMessage agent exists precisely to remove app navigation; thirteen log types via one chat thread, "No menus."
- **Photo logging** of meals; barcode scanning; restaurant-menu build rules that make eating out a pick-from-menu instead of ingredient entry.
- **Auto-import** of weight and wearable data from HealthKit overnight ("neither needs you to log anything").
- **Recipes/"usual" shortcuts** and custom foods turn repeats into one message.
- **Skipped/Fasted day statuses** let users mark gaps honestly in one tap instead of entering fake data — protecting the engine from the most common data-corruption pattern.
- Group logging and reminders reduce the forgetting problem ("I'll be insufferable about it").

### Design & UX / micro-interactions
- Website/app aesthetic: dark-mode-first, cyan-on-dark "energy" branding, data-dense but playful.
- The **check-in card** is the signature interaction: a single decision surface (Apply / Roll over / Talk it through) with big buttons — the weekly moment of agency.
- **What-moved-it chips** on sleep/recovery scores, the TDEE status chip, and journal-annotated TDEE charts are micro-interactions aimed exactly at numbers-geeks.
- Coach replies are formatted as receipt-like lines ("785 cal · 52g P · 96g C · 21g F → day 1,205/2,180 cal") — high information density per message, zero navigation.
- Personality switching ("Text PERSONALITY to switch") is an unusual meta-interaction: same data, different voice.
- Soundness of the multi-surface model: log in iMessage, review in app — "Same log, same number, whichever one you open."

## Strengths & differentiators
- **The adaptive-TDEE loop is genuinely closed**: intake + weight-trend → measured expenditure → proposed target → human-approved commit, with data-quality gating (developing/updating/held) that few competitors expose.
- **Explainability as product surface**: TDEE breakdown terms, status states, what-moved-it score chips, journal markers on burn charts, and the "It notices. You say why." framing.
- **Agent-native distribution**: iMessage logging (lowest-friction input surface on iOS), group-chat accountability, and an MCP server with confirm-before-write semantics — ahead of essentially every competitor in 2026.
- **Rejects wearable-calorie crediting** into the energy model — methodologically cleaner than "eat back your burn" apps.
- **Trend-first communication** ("Trend's 179.1, down 0.6") baked into even the conversational replies.
- Honest hedging and guardrails (projected date "an estimate, not a promise"; configurable calorie floor; clinical-guidance prompts).
- Documentation culture: a Learn library with last-verified dates, comparison pages "built from official sources," and public math.

## Weaknesses & user complaints (cite review sources)
- **iOS-only with no Android shipping** despite Health Connect/Google Fit copy; the official /android page concedes there is "no Play Store listing" yet — the site's own integrations copy runs ahead of reality.
- **Algorithm trust complaints**: r/ZoltHealth users report "the adaptive TDEE calculation seems wrong?" after 5 weeks of consistent logging and one user "logged calories for nearly 2 months without seeing weight loss in their estimate"; the developer's own FAQ admits past TDEE-calculation bugs (v2.6.7 release notes: "fix TDEE calculation issues with recent update"). With only ~62 ratings averaging 4.16, the engine's reliability is still being proven.
- **Opacity of the core filter**: unlike Happy Scale, the trend-smoothing math is unpublished; users comparing notes on r/ZoltHealth ("Zolt vs MacroFactor") found ~500 kcal/day differences between apps and conclude "I would use MacroFactor for food tracking" (Zolt for charts/insights) — a positioning risk in its core competency.
- **Cloud-first privacy posture**: sensitive health data (photos, journals, sleep, AI prompts, embeddings of memories) processed server-side with analytics/attribution collection — a dealbreaker for the privacy-first segment WLO targets; the MCP key and staged-writes mitigations don't change the cloud dependency.
- **AI-dependence for the flagship experience**: the iMessage coach requires connectivity, an account, and (for MCP) Pro; the free tier's limits and multi-tier pricing (Pro vs Pro Plus, three visible annual prices) confuse store listings.
- **Young/small**: released May 2025, small review base, no long-term track record; r/AppleWatch users compare it to Athlytic/Bevel and praise "charts, analysis, insights, and interface," but category incumbents (MacroFactor, Carbon, Cronometer) still win on maturity, database, and structure per Zolt's own comparison pages.
- Weight/food logging fidelity still leans on AI estimation ("Around 610") — accuracy-minded users may distrust photo-estimated calories feeding the TDEE solve.

## What WLO should learn (concrete, actionable takeaways)
1. **Close the loop WLO already has the data for**: with meal logs + weigh-ins, WLO can implement Zolt's core — TDEE = avg_calories − (weekly_trend_delta × 3500/7), updated continuously — as an optional "metabolism insight" panel computed **on-device**. It's ~50 lines of math behind the same local-first wall as everything else, and it turns WLO's meal tracker and weight tracker into one energy model.
2. **Gate the estimate with visible status states** (developing / updating / held): hold the TDEE when logs are sparse, days are ambiguous, or the week is atypical, and show *why* ("3 unlogged days — can't compare like with like"). Adopt Zolt's Skipped-vs-Fasted day semantics; it's the cleanest fix for missing-data corruption without demanding perfection.
3. **Reframe every weigh-in as a trend delta**: the reply "Trend's 179.1, down 0.6" (not "logged 178.4") is the cheapest motivational device in this research — WLO's post-logging toast/animation should always celebrate the smoothed movement, never the raw bounce.
4. **Human-approved adaptation**: steal the Sunday check-in pattern — a weekly card with pace, adherence (days logged/7), on-plan flag, and a proposed calorie/target change with Apply / Keep / Discuss buttons. Even without an LLM, WLO can propose threshold-driven adjustments ("plateau detected: 14 days trend-flat at 500 kcal deficit → suggestion") and let the user commit — algorithm proposes, user disposes.
5. **Decompose every composite number**: Zolt's "what moved it" chips (Sleep duration −7, HRV −4…) and TDEE breakdown (BMR + adjustment + exercise + movement) are exactly what a numbers-geek wants; WLO's forecast and score features should render contribution terms, not just outputs. Pair with Happy Scale-style "How we got here" expanders.
6. **Show calculator-vs-measured on one chart with the user's own event markers**: plot formula-estimated TDEE as a static baseline and measured burn as a line, annotated with user events (vacation, sick, started running). Projection becomes a longitudinal experiment the user understands — and it surfaces metabolic adaptation visually, which neither app fully productizes.
7. **Explicitly model deceleration in goal-date forecasts** (both apps' shared gap): project goal dates from an adapting rate (e.g., decay the recent deficit as TDEE falls with weight, or show trend-rate percentile bands) and present optimistic/expected/pessimistic dates. Never extrapolate the raw historic rate to a single date — r/GLPGrad shows users call that out months later.
8. **Reject "eat back exercise calories"** in the forecast math: follow Zolt in using intake-vs-weight as the sole energy signal, and treat wearable burn as display context. It's both more accurate and more local (WLO doesn't need Health Connect workout calories to drive targets).
9. **Staged, confirm-before-write agent actions** are the right interaction grammar for any WLO AI feature (photo → recognized foods → editable draft → atomic commit): adopt Zolt's prepare/confirm/cancel pattern for photo-estimated meals and AI-generated plans, even fully on-device.
10. **Anti-pattern to avoid**: marketing integrations and features beyond what ships (Zolt's Health Connect copy with no Android client), multi-tier pricing confusion in store listings, and cloud AI as the only interface — for WLO, keep the deterministic engine (smoothing, projection, TDEE) on-device and account-free; if an optional remote AI layer is added (user-supplied key, provider-agnostic), it should be a conversational skin over the same local engine, exactly like Zolt's iMessage coach is a skin over its check-in engine.

## Sources
- [Zolt official site (positioning, iMessage coach, features, Zolt Score)](https://zolthealth.com/)
- [Zolt — Adaptive TDEE explainer (published formula, nSuns lineage, calculator/spreadsheet/app paths)](https://zolthealth.com/adaptive-tdee)
- [Zolt Manifesto (energy thesis, Zolt Score bands)](https://zolthealth.com/manifesto)
- [Zolt MCP server docs (tool list, staged writes, API keys, privacy notes)](https://zolthealth.com/mcp)
- [Zolt /android page (iOS-only status, Android roadmap)](https://zolthealth.com/android)
- [Zolt Learn — What Zolt does (three-part architecture, TDEE status states)](https://zolthealth.com/learn/what-zolt-does)
- [Zolt Learn — How Zolt learns your TDEE (inputs, calculator comparison, breakdown)](https://zolthealth.com/learn/what-is-adaptive-tdee)
- [Zolt Learn — What makes a TDEE estimate reliable (consistency, Skipped/Fasted, holds)](https://zolthealth.com/learn/what-makes-a-tdee-estimate-reliable)
- [Zolt Learn — Setting up your first goal (pace, projected date, cadence, calorie floor)](https://zolthealth.com/learn/setting-up-your-first-goal)
- [Zolt Learn — Why your TDEE is not updating (holding-state diagnostics)](https://zolthealth.com/learn/why-is-my-tdee-not-updating-in-zolt)
- [Zolt Learn — Connecting nutrition and weight data (HealthKit/Health Connect/smart scales, duplicate handling)](https://zolthealth.com/learn/connecting-nutrition-and-weight-data)
- [Zolt Learn — Why did my calorie target change without weight change (no wearable-calorie credit)](https://zolthealth.com/learn/why-did-my-calorie-target-change-without-weight-change)
- [Zolt Compare hub (vs MacroFactor, Carbon, Cronometer, MFP, Cal AI, RP, Bevel)](https://zolthealth.com/compare)
- [Zolt Privacy Policy (July 2026: data categories, Health Connect/Google Health/Fitbit, AI/Coach data)](https://zolthealth.com/privacy-policy)
- [Zolt: Energy and Macro Coach on the US App Store (metadata, v2.6.7, IAPs, release notes)](https://apps.apple.com/us/app/zolt-energy-and-macro-coach/id6736639311)
- [r/ZoltHealth (official subreddit)](https://www.reddit.com/r/ZoltHealth/)
- [r/ZoltHealth — "Zolt Beta, Vision, and FAQs" (98% TDEE-prediction claim, vision)](https://www.reddit.com/r/ZoltHealth/comments/1gl2os7/zolt_beta_vision_and_faqs/)
- [r/ZoltHealth — "Zolt vs MacroFactor" (community comparison, ~500 kcal discrepancy)](https://www.reddit.com/r/ZoltHealth/comments/1ox557j/zolt_vs_macrofactor/)
- [r/ZoltHealth — "Introducing Zolt: the energy app"](https://www.reddit.com/r/ZoltHealth/comments/1ox60y7/introducing_zolt_the_energy_app/)
- [r/AppleWatch — "Zolt vs Athlytic vs Bevel" (charts/insights praise)](https://www.reddit.com/r/AppleWatch/comments/1pp50k2/zolt_vs_athlytic_vs_bevel/)
- [r/ZoltHealth adaptive-TDEE complaint thread ("seems wrong" after 5 weeks)](https://www.reddit.com/r/ZoltHealth/)
