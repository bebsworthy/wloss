# MacroFactor — Feature Analysis

**Category:** Adaptive macro coaching app ("dynamic macro coaching" — science-backed algorithmic diet coach); companion MacroFactor Workouts app (Jan 2026)
**Platforms:** Android, iOS only — **no web app** (deliberate; frequent complaint). Built by the Stronger by Science team (Greg Nuckols et al.); strong SBS content/educational ecosystem.
**Pricing model:** Subscription-only, no free tier (7-day free trial). Nutrition app: $11.99/month, $47.99/6-month, $71.99/year (~$5.99/mo). Workouts app $11.99/mo or $71.99/yr; Nutrition+Workouts bundle $89.99/year (for subscribers starting on/after Jan 1, 2026). Cloud account required.
**AI usage:** Cloud AI, opt-in. "MacroFactor AI" photo food logging (beta April 2025, v5.0.0; major upgrades through May 2026: multi-photo, photo+text, custom instructions; "Plate Stack AI" in beta July 2026). No conversational coach AI — the "coach" is the deterministic adaptive algorithm, which they market as science, not AI.
**Local & privacy posture:** Cloud-synced, account required — not local-first. Privacy policy / health-data notices; positions data as never sold. All history and computation live on their servers; no offline story beyond basic caching.

## Overview

MacroFactor (launched 2020-2021 by the Stronger by Science team) is the purest expression of "coaching as an algorithm." Instead of a static calorie target, it treats your energy expenditure as an unknown to be *measured*: it continuously compares your logged calorie intake against your smoothed weight trend and reverse-engineers your true TDEE, then rewrites your weekly macro targets to keep you on pace. It is explicitly adherence-neutral — no red numbers, no "over budget" shame; the coach adjusts to reality instead of scolding you for it. The team publishes its methodology openly ("MacroFactor's Algorithms and Core Philosophy," V3 expenditure paper, algorithm-accuracy page), which is catnip for the numbers-geek audience and fuels a uniquely technical user community (staff answer algorithm questions in r/MacroFactor).

The philosophy: successful programs are built on three levers — energy flux (expenditure-aware targets), weight trending (filtering water/glycogen/gut noise), and *behavioral sustainability*. Everything in the UX serves those: a fast timeline-based logger, weekly check-ins, a weight-trend-first dashboard, and macro targets that change weekly without the user doing math. Micronutrients and food-database depth are deliberately secondary; this is a tool for goal pursuit (cutting/bulking/maintenance), not a clinical nutrient microscope like Cronometer.

Market position 2026: the premium "it just adapts" pick, repeatedly rated the best adaptive-TDEE app in comparisons; credible science branding via Stronger by Science and athlete ambassadors (Jeff Nippard). Its two structural weaknesses: no free tier (trial-only) and a feature set deliberately narrowed to coaching — no meal planner, no shopping list, no web app, shallow micronutrients.

## Feature inventory

### Onboarding & diet-plan selection (diet templates)
- Guided questionnaire → **initial macro targets computed from MacroFactor's own custom BMR equations + custom activity multipliers** (built in-house from their data; explicitly *not* Mifflin-St Jeor/Harris-Benedict). Accurate-enough starting point; expected to converge to truth over 2–3 weeks of data.
- **Program types:** weight loss / weight gain / maintenance, with rate-of-change expressed as % of body weight per week (with recommended pace guidance) or absolute, or a custom program. Goal weight editable in scale-weight terms; program start/duration flexible.
- **Program styles:** *Coached* (app sets and adjusts everything weekly), *Collaborative* (app sets weekly calorie budget; you split it into daily macro targets), *Manual* (full control; expenditure estimate still shown).
- No diet templates per se (no "keto preset") — you choose your own macro split; goal framing is coaching, not diet ideology.

### Food logging & nutrient estimation
- **Database:** curated/verified model — ~1.36M verified foods in search (~4M total incl. archived), from "highly vetted research databases" + verified label data; 54 nutrient fields per food. **No instant crowd-sourcing:** since v2.7.0 (2024) users may *opt in* to submit branded foods, which are vetted before joining the verified pool; custom foods are private by default; "copy to custom" to fix any entry privately.
- **Depth vs. Cronometer:** primarily macros + calories + fiber/sugar/sodium/water etc.; a **Micronutrients + Nutrient Explorer** feature (2023) tracks a curated set of micronutrients with dedicated explorer UI, but coverage depends heavily on label data and regional databases — much shallower than Cronometer's 95.
- **Logging:** search, barcode scan, quick add, saved meals, recipes, planned/logged timeline entries; claims "the fastest food logging system on the market." Barcode/regional coverage weaker outside the US/Canada — top international complaint.
- **MacroFactor AI photo logging** (see AI section) added as a first-class capture path from 2025.

### Meal planning, recipes & shopping list
- Recipes with scaling; saved meals; a **timeline-based food logger** with planned entries ("log tomorrow's food today"), which is their answer to planning needs.
- **Meal planner/calendar: not offered.** "Lack of a meal planner section is the only thing I don't like" (r/MacroFactor); users run a second app alongside.
- **Shopping list: N/A — not offered.**
- Nutrient targets are *outputs* of the coach, so pre-planning days against fixed targets is less central than in Cronometer/WLO's model.

### Exercise planning & tracking
- **By design, the nutrition app adds no "exercise calories"** — the expenditure algorithm already accounts for activity, avoiding the double-count trap that plagues Cronometer/MFP users. Steps sync (HealthKit/Health Connect) feeds the expenditure estimate (2025 "Expenditure Modifiers" update adds step-informed adjustments).
- **MacroFactor Workouts** (separate app, Jan 2026): strength-training logger with programs, rest timers, expanded cardio support, iOS Live Activity; bundled pricing with Nutrition. Nutrition-app-native exercise *planning*: effectively N/A — delegated to the Workouts app.

### Weight & body metrics, forecasting
This is MacroFactor's core science; WLO's most relevant benchmark.

- **Weight trend algorithm:** every scale entry is treated as a noisy measurement of true weight; the app computes a *trend weight* that "picks up the signal while filtering out the noise" of water, glycogen, and gut-content fluctuation — a sophisticated exponentially-weighted-style filter (more robust than a simple moving average; staff have described it as filter-based rather than plain EWMA). Trend weight, not scale weight, drives all coaching math. **Weight Trend V2 (July 2026 beta)** improves robustness further and can *revise past* trend values as data arrives. The help docs actively coach users not to panic about daily scale swings — trend-vs-scale divergence is visualized, not hidden.
- **Adaptive TDEE / energy expenditure estimation:** rolling-average intake vs. rate-of-trend-weight-change, with energy-partitioning adjustments by goal/body composition → daily expenditure estimate updated continuously (displayed weekly at check-in). **V3 algorithm (Feb 2024):** more responsive, more stable, *spline-based* representation of intake and weight-trend deltas, **adaptive per-user time constant** (fits the user's actual signal dynamics instead of a fixed averaging window), and graceful degradation with missing data (uncertainty grows when you stop logging weight/intake rather than collapsing). Published accuracy gains: **~7% better short-term, ~20% better long-term** vs. the prior algorithm ([algorithm-accuracy](https://macrofactor.com/algorithm-accuracy/)). Community consensus: converges within ~2–3 weeks; "extremely accurate" with consistent logging.
- **Weekly check-in:** the coaching ritual — expenditure estimate updated, macros recomputed from trend + adherence (adherence-*neutral*: advice never scolds; misses simply update the model). Check-in history is browsable.
- **Forecasting / goal-date projection:** Goal Progress panel includes an **"Optimistic ETA" — projected goal-completion date extrapolated from your current trend** — plus the ability to make completion arrive when the *trend* hits target rather than a calendar date; for hard deadlines you tune the target rate in the Strategy tab. True stochastic forecasts (confidence bands on the ETA) are not published.
- Body measurements (circumferences) tracked alongside weight; no body-fat % estimation beyond user-entered data.

### Photos / progress / silhouette
- **Progress photos: N/A — not offered** (photos exist only as an AI food-logging input). No silhouette, no body-composition photo tracking. Open lane for WLO.

### Statistics, visualization & gamification
- **Dashboard:** weight trend vs. scale weight, expenditure estimate (with trend and confidence/uncertainty wideness), intake vs. burn, goal progress ring + ETA, macro targets — a unique "physics of you" homepage no competitor has.
- **Trends tab:** line charts for weight, trend weight, expenditure, intake, macros, with selectable ranges; chart pages display an average of all visible points; weekly check-in history.
- **Nutrient Explorer** for micronutrient breakdowns; nutrition tile shows rolling averages (e.g., 7/30-day intake).
- **Gamification:** deliberately minimal — no streaks pressure, no shame colors; the "game" is watching your expenditure line and ETA update at each check-in. **Annual Reports** (Spotify-Wrapped-style yearly stats, e.g., 2023 report incl. data-export tie-ins) are their one celebratory flourish.
- **Data ownership gestures:** robust CSV data export (daily expenditure, weight trend, intake, etc.).

### AI features
- **MacroFactor AI (photo food logging):** April 2025 beta (v5.0.0) — capture a meal, AI populates editable entries. **May 2026 upgrade (v5.7.7):** stronger food identification, **multiple photos per meal**, **photo + text combination**, and user-supplied **custom instructions** to the estimator. **July 2026:** "Plate Stack AI" in beta (multi-item/stacked-meal parsing). All estimates remain user-editable before saving.
- Explicitly *not* building a chatbot coach; the deterministic adaptive algorithm is the coach. Educational AI content stays in their SBS blog/newsletter. All AI is cloud-side; no on-device inference claims.

### Input-minimization techniques
- Photo logging with multi-photo + text + instruction refinement; barcode; quick-add; saved meals/recipes; planned timeline entries (log future = zero morning friction); health-platform sync of weight and steps (no manual entry for those); weekly check-in replaces daily target fiddling — arguably the biggest input reduction of all: **you never manage targets, only inputs.**
- The philosophy article frames minimal-friction logging as the retention mechanism: the algorithm must tolerate missed days rather than demand perfection.

### Design & UX / micro-interactions
- Widely praised minimalist black-and-white aesthetic, excellent typography and chart design; multiple app themes/icons; polished animations on rings and check-ins. Reviewers (e.g., Jeff Nippard's 14-day review) call it the best-designed macro tracker.
- The check-in moment is a designed micro-event (updated expenditure + new macros + ETA), giving a weekly dopamine loop that Cronometer lacks.
- Complaints: iOS/Android parity gaps, no iPad/web, and the unfamiliar logger paradigm confuses some newcomers (subreddit FAQ dedicates a section to it).

## Strengths & differentiators
- **Adaptive TDEE + trend-weight science, openly documented** — the best energy-expenditure estimation in any consumer app; accuracy page publishes methodology and error improvements. This *is* the product.
- **Coaching psychology:** adherence-neutral, weekly cadence, program styles (coached/collaborative/manual) that meet users at different autonomy levels; strong retention by design.
- **Verified-only database** with vetted user submissions — quality control without Cronometer-scale depth.
- **Science-brand flywheel:** SBS articles, staff presence on Reddit, annual reports, roadmap transparency.
- Goal-**date projection ("Optimistic ETA")** derived from trend — exactly the forecasting geeks want.
- Best-in-class minimalist visual/interaction design; new Workouts bundle rounds out the ecosystem.

## Weaknesses & user complaints (cited)
- **No free tier** — trial-only subscription; price threads recur on r/MacroFactor.
- **No web app** — data locked to mobile.
- **No meal planner / shopping list** — "keeps me using other apps on top of MF" (r/MacroFactor, 2025).
- **Shallow micronutrients & regional database gaps** — "food coverage in my country is very bad"; scanned branded foods often lack micro data; Quick Add has no micronutrient support ([threads](https://www.reddit.com/r/MacroFactor/comments/1iknnjz/incomplete_micronutrients_data_in_app/), [1](https://www.reddit.com/r/MacroFactor/comments/171h33q/micronutrients/), [2](https://www.reddit.com/r/MacroFactor/comments/u4bmkm/micronutrients_in_quick_add/)).
- **AI photo estimation imperfect** — "not always 100% accurate"; top posts mock inaccurate AI logs; mitigated by editability.
- Algorithm takes 2–3 weeks to converge; users craving "exercise calories added back" find the model confusing at first; no progress photos/body-comp visuals.

## What WLO should learn
1. **Steal the core science wholesale:** exponentially-weighted (filter-style) trend weight → rolling intake-vs-trend-change adaptive expenditure → weekly recomputed targets. It's implementable fully **on-device** (no server needed — pure math over local data), making it the perfect flagship feature for WLO's privacy-by-architecture pitch: "your adaptive TDEE, computed on your phone, forever, free."
2. **Publish your methodology like MF does:** an "algorithms & philosophy" page + accuracy disclosures builds geek trust and community evangelism (their subreddit does their marketing). Document WLO's smoothing constants, convergence time, and missing-data behavior openly.
3. **Make the trend line, not the scale, the hero:** WLO's weight module should show scale points + smoothed trend together and actively explain divergence (water/glycogen/gut) — this reframes "bad weigh-ins" as noise and is emotionally protective. Add MF's V2-style ability to revise historical trend as data improves.
4. **Project goal *dates*, not just percentages:** MF's "Optimistic ETA" on the goal-progress card is the single most motivating stat in either app and neither competitor does true probabilistic forecasting — WLO can exceed it with confidence bands (e.g., "on trend for Oct 14; 80% range Sep 28–Nov 2").
5. **Weekly check-in as ritual:** recomputing targets in one designed weekly moment (with animation + updated expenditure + new ETA) beats daily guilt loops; keep it adherence-neutral — no red numbers, no "you failed," the model simply adapts. This aligns perfectly with WLO's gamification goals without shame mechanics.
6. **AI-photo capture UX to copy:** MF's May 2026 pattern — multiple photos per meal, photo + free-text hints, and natural-language instructions to the estimator ("no oil on the salad") — is the current state of the art for input minimization; combine it with Cronometer's verify-against-verified-DB confirm step and run the vision model **on-device**, falling back to user-supplied-key cloud providers for hard cases (WLO's provider-agnostic remote option).
7. **Tolerate imperfect logging by design:** V3's spline-based, adaptive-time-constant approach degrades gracefully with missed days — WLO should never punish logging gaps; widen uncertainty, keep coaching, and say so in the UI.
8. **Fill the gaps both apps leave open:** meal planner + auto-generated shopping list + micronutrient depth + progress/silhouette photos are *each* missing from one of the two leaders and both from MacroFactor — WLO's v1 feature set is exactly the union they refuse to build. Don't copy their narrowness; sell it ("everything Cronometer measures, coached like MacroFactor, on your device").
9. **Annual-report/"Wrapped" moment:** export-free, on-device yearly stats (days logged, weight journey, expenditure curve, Bristol history) is a cheap, viral, numbers-geek-delighting feature.

## Sources
- [macrofactor.com](https://macrofactor.com/) — official site
- [MacroFactor's Algorithms and Core Philosophy](https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/) (mirrored at [Stronger by Science](https://www.strongerbyscience.com/macrofactor-algorithms-philosophy/))
- [An In-Depth Look at MacroFactor's New V3 Expenditure Algorithm](https://macrofactor.com/expenditure-v3/)
- [How Accurate is MacroFactor's Expenditure Algorithm?](https://macrofactor.com/algorithm-accuracy/)
- [Weight trend help article](https://help.macrofactorapp.com/dashboard/weight_trend)
- [Program styles — Help Center](https://help.macrofactorapp.com/en/articles/91-program-styles)
- [AI-Powered Food Logging (April 2025)](https://macrofactor.com/ai-food-logging/) / [Help Center: AI food logging](https://help.macrofactorapp.com/en/articles/258-ai-food-logging)
- [MacroFactor Monthly — May 2026 (multi-photo AI, photo+text, instructions)](https://macrofactor.com/mm-may-2026/) / [July 2026 (Plate Stack AI, Weight Trend V2 beta)](https://macrofactor.com/mm-july-2026/)
- [Ready, Set, Goal! (goal features)](https://macrofactor.com/goal-features/) / [More options for goal completion](https://macrofactor.com/mm-february-2022/) / [Help: strict-timeline goals](https://help.macrofactorapp.com/en/articles/202-what-should-i-do-if-im-pursuing-a-goal-with-a-strict-timeline) / [Reddit: where to find goal ETA](https://www.reddit.com/r/MacroFactor/comments/11e52i3/where_can_we_find_the_expected_date_of_goal/)
- [Workouts pricing (bundle $89.99/yr, 2026)](https://macrofactor.com/workouts/price/) / [Press kit](https://macrofactor.com/press-kit/)
- [Database robustness by region — Help Center](https://help.macrofactorapp.com/en/articles/25-how-robust-is-the-database-coverage-in-my-region) / [v2.7.0 Food Database Submissions](https://macrofactor.com/version-2-7-0/) / [r/MacroFactor release thread](https://www.reddit.com/r/MacroFactor/comments/1bz8cfz/release_270_food_database_submissions/) / [Micronutrients & Nutrient Explorer](https://macrofactor.com/micronutrients-nutrient-explorer/)
- [Timeline-based food logger](https://macrofactor.com/timeline-based-food-logger/) / [Dashboard revamp](https://macrofactor.com/dashboard-revamp/) / [2023 Annual Report](https://macrofactor.com/annual-report-2023/)
- [r/MacroFactor: Coached vs Collaborative](https://www.reddit.com/r/MacroFactor/comments/1dqelzo/coached_vs_collaborative/) / [Adaptive TDEE accuracy](https://www.reddit.com/r/MacroFactor/comments/1sug0rl/adaptive_tdee/) / [Setup & FAQs sticky](https://www.reddit.com/r/MacroFactor/comments/pp6lpr/read_this_first_macrofactor_setup_and_faqs/) / [meal-planner complaint](https://www.reddit.com/r/MacroFactor/comments/1kxm4q5/recipesmeal_plan/) / [weight-trend explanation thread](https://www.reddit.com/r/MacroFactor/comments/zrkcsn/weight_trend_in_macrofactor_seems_to_track_too/)
- [Bento Bunny: MacroFactor Review 2026](https://www.bentobunny.app/reviews/macrofactor-review) / [Calorie-Trackers.com review](https://calorie-trackers.com/reviews/macrofactor/) / [Nutrola: does MF still work 2026](https://nutrola.app/en/blog/does-macrofactor-still-work-for-weight-loss) / [Nutrola: MFP vs MF database comparison](https://nutrola.app/en/blog/myfitnesspal-vs-macrofactor-which-is-better-2026)
- [Google Play listing](https://play.google.com/store/apps/details?id=com.sbs.diet) / [App Store listing](https://apps.apple.com/us/app/macrofactor-macro-tracker/id1553503471)
- [Jeff Nippard MacroFactor review (YouTube)](https://www.youtube.com/watch?v=NYy7KltyJIo)
