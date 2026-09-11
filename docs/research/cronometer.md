# Cronometer — Feature Analysis

**Category:** Precision nutrition tracking / micronutrient analysis app (self-described "the most accurate nutrition tracking app")
**Platforms:** Android, iOS, Web (full web dashboard); separate "Cronometer Pro" web platform for dietitians/researchers/clinics
**Pricing model:** Freemium. Free tier (ad-supported, full core logging, 7-day trends window). "Gold" subscription: ~US$10.99/month billed monthly, ~US$4.99/month billed annually (~$59.99/year, ~54% discount). Gold gates AI photo/voice logging, Crono Coach, custom charts, fasting timer, macro scheduler, custom biometrics, PDF reports, ad removal. Cloud account required; claims 15M+ users.
**AI usage:** Cloud AI. Photo Logging (launched Sept 2025, Gold-only), Voice Logging (Gold, incl. Siri), and "Crono Coach" AI assistant (Gold) that analyzes diary/nutrition-report snapshots for patterns. Explicit "people first" AI stance: AI only *recognizes*; user confirms each suggestion against verified database entries before logging.
**Local & privacy posture:** Cloud-synced, account required — not local-first. States data is encrypted, strict access controls; markets "we don't sell your data." Canadian company; GDPR-compliant. No offline-first architecture story; AI runs server-side.

## Overview

Cronometer (originally "CRON-o-Meter," built in 2005 by Aaron Davidson for the calorie-restriction community) is the nutrition tracker you use when you care about *data quality* more than convenience. Its core differentiator is a lab-verified food database — sourced from NCCDB (Nutrition Coordinating Center), USDA FoodData Central (incl. Foundation Foods), and national databases (Health Canada, CIQUAL/France, AUSNUT, NZ FoodFiles) rather than crowd-sourced entries — letting users track up to ~95 nutrients and compounds (all amino acids, omegas, vitamins, minerals), where competitors like MyFitnessPal typically expose a dozen or fewer. Every user-submitted food is moderated before it can be shared publicly.

Its market position is "the accuracy pick" in nearly every 2025-2026 comparison: the app dietitians recommend, the app people migrate to when MyFitnessPal/Lose It paywalled barcode scanning. It targets health optimizers, keto/carnivore dieters monitoring micronutrient gaps, athletes, and people with medical needs (diabetes, deficiency correction). A second audience is monetized through Cronometer Pro, a practitioner platform that syncs client diaries for professional dietary analysis.

Its weakness is the flip side of its strength: energy-balance math is *static* (formula-based BMR × activity level + logged exercise — no adaptive learning from intake vs. weight trend), and the UI has grown cluttered and feature-gated. Compared to MacroFactor's adaptive coaching, Cronometer is a microscope, not a coach — it shows you exactly what you ate, but doesn't *learn* your metabolism.

## Feature inventory

### Onboarding & diet-plan selection (diet templates)
- Questionnaire-based setup (sex, age, height, weight, activity level, goal).
- **Custom Diets system:** preset nutrition targets for keto/low-carb/high-protein/etc., fully editable as macro percentage splits or gram amounts. Targets configurable per-meal, per-day, or as weekly averages.
- **Target Wizard:** guided walkthrough for setting energy/macro/micronutrient targets; can auto-populate recommended targets (RDA/AMDR) and scale them to goals.
- Weight goal setting: choose goal weight + rate of change; Cronometer automatically back-computes the calorie target from BMR/activity math.
- No adaptive "coach" — targets are set once and only change when weight entry changes BMR inputs or the user edits them.

### Food logging & nutrient estimation
- **Database:** 1M+ foods; lab/analysis-derived sources (NCCDB, USDA FDC, CIQUAL, etc.) for the majority; branded products from label data (Nutritionix feed + verified label submissions). User-submitted foods require moderation before public sharing — no instant crowd-sourced junk.
- **Nutrient depth:** the killer feature — up to ~95 nutrients/compounds (official copy varies ~82–95 by platform/tier): all essential amino acids, omega-3/-6, full vitamin/mineral panels, caffeine, etc. % of target shown per nutrient; "Complete" vs "Spotlight" nutrient views.
- **Logging methods (4):** AI Photo Logging (Gold), Voice Logging (Gold), text search, **barcode scanning (free** — a major acquisition hook after MFP/Lose It paywalled it).
- Quick-add calories/macros; copy previous day/meals; recent & frequent foods; timestamps (Gold); portion units incl. grams and household measures.
- Known weak spot: AI photo recognition accuracy is middling (one 2026 review measured ~65% item accuracy, ±22% portion error); Cronometer's mitigation is forcing human confirmation against verified DB entries.

### Meal planning, recipes & shopping list
- Custom foods, custom meals, custom recipes with per-serving nutrient rollups; recipe importer (Gold, imports URL and parses nutrition).
- **"Suggest Food" engine (Gold):** recommends foods from a curated list to fill *remaining* nutrient targets for the day — a genuinely numbers-geek feature.
- Diary supports meal groupings and copying/planning future days.
- **Shopping list: N/A — not offered** as a first-class feature; auto meal-plan generation and grocery lists are perennial top forum/Reddit feature requests (users work around it with copied diary days or third-party apps like Paprika). Cronometer Pro practitioners can build client meal plans, but consumers cannot self-generate plans.

### Exercise planning & tracking
- Exercise diary with calorie burn per entry; custom exercises; per-exercise MET/energy settings.
- Device/wearable sync: Garmin, Fitbit, Strava, Polar, Apple Health, Health Connect — imported active energy counts toward the day's energy balance (toggleable net-vs-gross burn behavior).
- **Exercise *planning* (workout programs/sets/reps): N/A — not offered.** It logs burned energy, not training.
- Exercise burn feeds the static "Energy Expenditure" circle (BMR + activity level + exercise/tracker burn) in the diary.

### Weight & body metrics, forecasting
- Weight entry, charts over time, plus custom biometrics (Gold): blood pressure, blood glucose, ketones, sleep, body fat, etc.; lab-test tracking categories.
- **Trend smoothing: primitive.** Charts offer an optional *trend line = rolling (moving) average* of logged data (historically on metric/nutrient charts, later on weight). No exponentially-weighted trend weight, no noise filtering rationale, no confidence handling.
- **Energy expenditure: static formulas, not adaptive.** BMR from age/sex/height/weight (Mifflin-St Jeor default, with alternatives such as Harris-Benedict/Katch-McArdle and manual entry) × activity-level multiplier + logged/synced exercise. It never reverse-engineers TDEE from actual intake vs. weight change — the single biggest algorithmic gap vs. MacroFactor. (User confusion about double-counting activity level + exercise is a recurring Reddit theme.)
- **Forecasting: no goal-date projection.** Weight goal progress shows % toward goal; estimated completion date is not modeled (community feature request).
- Auto-behavior: new weight entries recompute BMR and thus calorie targets — a slow, dumb approximation of adaptivity.

### Photos / progress / silhouette
- **Progress/body photos: N/A — not offered.** Photo capture exists only as an AI *food-logging* input. No silhouette comparison, no photo timeline. Open lane for WLO.

### Statistics, visualization & gamification
- **Dashboard:** customizable cards/modules; Diary shows the signature concentric energy circles (consumed vs. burned) and macro rings with exact grams + % targets.
- **Trends tab:** Charts (line graphs of any nutrient/biometric over custom ranges, with rolling-average trend line toggle and 7/30/90-day averages), Nutrition Report (daily averages vs. targets per nutrient over any window — Gold extends beyond 7 days), Highlights, and Balances (e.g., omega-3:omega-6 ratios) — extremely numbers-dense.
- **Custom Charts (Gold):** build your own multi-nutrient charts.
- **Nutrition Scores (Gold, 2025):** up to 8 composite scores of diet quality — Cronometer's first real gamification layer.
- **Gamification:** logging streaks (shareable), daily target completion, Insights feed, Nutrition Scores. Underpowered relative to the data density — the numbers *are* the game, which suits the geek audience.
- PDF/print report export (Gold); full CSV export.

### AI features
- **Photo Logging** (Sept 2025, Gold): snap a meal → cloud vision model identifies ingredients → each suggestion is *matched to verified database entries* and must be confirmed/edited by the user before logging. Positioning: "AI recognizes, database guarantees accuracy."
- **Voice Logging** (Gold): speak meals (incl. Siri shortcut).
- **Crono Coach** (Gold, 2026): LLM assistant over a snapshot of your diary + Nutrition Report; surfaces patterns and suggestions ("sodium trending high"), not free-form chat; also usable by Pro practitioners on client data. Framed by an official "AI starts with people, not technology" post; users raised privacy questions about what's sent.
- No on-device AI; no AI-generated meal plans or adaptive targets.

### Input-minimization techniques
- Barcode scanning (free) remains the workhorse; photo and voice logging for hands-free entry; Siri/shortcuts; recent foods; copy-day; timestamped back-dated entries; Wear OS companion; device auto-sync for weight/exercise/biometrics removes manual entry entirely for integrated hardware.
- Philosophy is still "log precisely"; there is no attempt to infer meals from context (e.g., habitual-meal prediction).

### Design & UX / micro-interactions
- Information-dense, utilitarian Material-style UI; the 2023 redesign modernized visuals but split the community — top Reddit comments: "new design is terrible if you like data at a glance," "it feels too clunky… can't just open and input."
- Diary energy circle animation is the main signature interaction; otherwise micro-interactions are sparse. Learnability is a known pain point (deep menus, web/app parity gaps, double-counting confusion).

## Strengths & differentiators
- **Data integrity as a moat:** lab-verified + moderated database, all-amino-acid/micronutrient depth — no consumer competitor matches it; the reason dietitians cite it.
- **Micronutrient-first tracking** with balances/highlights (deficiency-spotting) is unique at consumer price.
- **Free barcode scanning + generous free tier** as competitive weapons (post-MFP-paywall migration wave).
- **Professional channel:** Cronometer Pro/Practitioner features create credibility and B2B revenue.
- Deep reporting (any nutrient × any window, custom charts, exports) — the numbers-geek paradise when tolerated.

## Weaknesses & user complaints (cited)
- **Cluttered, high-friction UX** — "too clunky," redesign backlash on r/cronometer ("New design is terrible if you like data at a glance"); steep learning curve.
- **Paywall creep:** Gold now gates trends beyond 7 days, photo/voice logging, custom charts, even removes ads only at Gold; "Is Gold worth it?" threads are mixed.
- **No adaptive energy expenditure or trend-weight science** — static BMR formulas; frequent Reddit confusion over energy expenditure/double-counting (r/cronometer "Confused with energy expenditure").
- **No meal-plan generator, no shopping list** — top feature-request forum threads remain unimplemented.
- **Weak AI accuracy** — 2026 third-party review measured ~65% photo-logging accuracy with large portion errors.
- Occasional sync/backup complaints; web UI considered clunkier than mobile.

## What WLO should learn
1. **Verify-then-log is the right AI-photo pattern:** AI proposes items, but every proposal resolves to *curated, verified* database entries and requires one-tap user confirmation. Copy Cronometer's "AI recognizes, database guarantees accuracy" contract — it converts AI slop into trustworthy data and is exactly the guardrail an on-device model needs for WLO's photo-first logging.
2. **Lab-grade data depth is a defensible identity:** a small curated database with full micronutrient panels (and visible source labels: "NCCDB / USDA Foundation / label") beats a huge crowd-sourced one for WLO's geek audience. Show *where* each number came from.
3. **Don't ship static TDEE math:** Cronometer's formula-based expenditure is its biggest flaw and users notice (double-counting confusion). Implement MacroFactor-style trend-weight + adaptive expenditure from day one (see macrofactor.md) — for a numbers-geek app this is table stakes, not a premium gimmick.
4. **Rolling-average "trend line" is insufficient smoothing:** the upgrade path is exponentially-weighted trend weight with explicit noise handling; WLO's forecasting should project goal *dates* from the trend, which Cronometer never does.
5. **Nutrient-gap surfacing = engagement:** Highlights/Balances ("your omega-3:6 ratio," "selenium at 42% for 14 days") and a "Suggest Food"-style engine that fills *remaining targets* are cheap-to-build, deeply geeky features that drive daily opens. Pair them with WLO's poop tracker (fiber ↔ Bristol correlations!) for insights no competitor can make.
6. **Free barcode, paid intelligence:** Cronometer acquires users with free barcode scanning and monetizes coaching/AI/reports. For WLO (local-first, no server costs for barcode DB lookups aside), keep capture free and gate advanced *analytics/insights*, not basic input.
7. **Anti-pattern — paywall creep and feature gating of *trends*: users notice when previously free analytics (7-day window limits) become paid; it generated the most bitter Cronometer threads. WLO's privacy pitch should make "all your data, all your history, forever, on-device" a free guarantee.**
8. **Streaks + scores are enough gamification only if the numbers are the game:** Cronometer proves geeks need little extrinsic gamification — but WLO can out-craft it with beautiful animated rings, haptics, and shareable score cards that Cronometer's dated UI lacks.

## Sources
- [cronometer.com](https://cronometer.com/) — official site (features, "1M verified foods", 95 nutrients)
- [Cronometer Gold — pricing & feature comparison](https://cronometer.com/gold/)
- [Cronometer launches premium Photo Logging (PR Newswire, Sept 2025)](https://www.prnewswire.com/news-releases/cronometer-launches-premium-photo-logging-fast-verified-nutrition-tracking-for-real-life-302549752.html)
- [Cronometer blog: Photo Logging](https://cronometer.com/blog/photo-logging/) / [4 ways to log food](https://cronometer.com/blog/4-ways-to-log-food-on-cronometer/)
- [Cronometer blog: Meet Crono Coach](https://cronometer.com/blog/crono-coach/) / [Cronometer's AI approach ("people first")](https://cronometer.com/blog/technology-changes-responsibility-doesnt-why-cronometers-approach-to-ai-starts-with-people-not-technology/)
- [Support: Energy Expenditure](https://support.cronometer.com/hc/en-us/articles/31974307318420-Energy-Expenditure) / [Energy Summary explained](https://cronometer.com/blog/cronometers-energy-summary/)
- [Support: Charts (rolling-average trend line)](https://support.cronometer.com/hc/en-us/articles/360018298812-Charts) / [What do our Trends tell us](https://cronometer.com/blog/what-do-our-trends-in-cronometer-tell-us/) / [Support: Dashboard (streaks)](https://support.cronometer.com/hc/en-us/articles/10570212821268-Dashboard)
- [Support: Suggest Food](https://support.cronometer.com/hc/en-us/articles/31295132424212-Suggest-Food)
- [Forum: meal plan generator / shopping list requests](https://forums.cronometer.com/discussion/3237/recipe-management-meal-plan-generator-shopping-sharing) / [Meal plan builder request](https://forums.cronometer.com/discussion/2815/meal-plan-builder)
- [kcalm.app: MFP vs Cronometer accuracy research review (NCCDB/USDA sourcing)](https://kcalm.app/blog/myfitnesspal-vs-cronometer-accuracy-research-review/)
- [ProMealPlan: Cronometer Review 2026](https://www.promealplan.com/en/blog/cronometer-review-2026)
- [ai-calorie-tracker.com: Cronometer AI accuracy measurements](https://www.ai-calorie-tracker.com/reviews/cronometer)
- [Reddit r/cronometer: redesign backlash ("data at a glance")](https://www.reddit.com/r/cronometer/comments/yvgjq6/new_design_is_terrible_if_you_like_data_at_a/) / [energy expenditure confusion](https://www.reddit.com/r/cronometer/comments/1sqk61w/confused_with_energy_expenditure/) / [Gold worth it threads](https://www.reddit.com/r/cronometer/comments/1p4khkr/gold_users_worth_it/) / [barcode-paywall migration](https://www.reddit.com/r/cronometer/comments/1ok3nku/paid_cronometer_vs_paid_lose_it/)
- [Google Play listing — Cronometer](https://play.google.com/store/apps/details?id=com.cronometer.android.gold) (platform/store data)
