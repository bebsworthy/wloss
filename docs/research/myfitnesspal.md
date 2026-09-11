# MyFitnessPal — Feature Analysis

**Category:** Health & Fitness — calorie counting / nutrition + fitness tracking ("food & fitness tracker", self-described "AI-powered nutrition tracker")
**Platforms:** iOS (4.71★, ~2.36M US ratings), Android (4.3★, ~2.9M ratings, 100M+ downloads), web, Apple Watch, Wear OS; syncs with 40+ apps/devices (Fitbit, Garmin, Samsung Health, Google Fit, Health Connect, Withings/smart scales)
**Pricing model:** Freemium, aggressive. Free tier = basic calorie/macro/water/exercise/weight logging + food search, with ads. Two paid tiers (US App Store IAPs): **Premium $19.99/mo or $79.99/yr**, and newer **Premium+ (~€99.99/yr / €8.34 per mo annual; ~$99.99/yr US)** which adds the Meal Planner, 1,500+ recipes, automated grocery lists, grocery-delivery-app sync, recipe/grocery sharing, and automatic meal-plan logging. 7-day free trial. HSA/FSA-eligible via Truemed.
**AI usage:** All cloud/server-side. Meal Scan (photo → database match), Voice Log, and a new conversational **AI Coach** (launched June 10, 2026) grounded in the user's logged history and "20 years of nutritional data," designed with a registered-dietitian team. No on-device AI.
**Local & privacy posture:** Account-required cloud service. 2018 data breach (150M+ accounts) is part of its history. Data is inherently server-side; health data policies shared with ad partners historically. Opposite of local-first.

## Overview

MyFitnessPal (MFP), launched 2005, is the incumbent giant of calorie tracking: 280M+ registered users, ~20 years of nutrition data, self-claimed "#1 nutrition and calorie tracking app in the U.S." and 5.5M five-star store reviews. It was bought by Under Armour in 2015 ($475M) and sold to Francisco Partners in 2020 ($345M); under PE ownership it has pursued aggressive monetization — moving features (barcode scanning, custom macros, net-carb mode, insights) behind a paywall and raising Premium from $49.99/yr to $79.99/yr, then adding a second, more expensive Premium+ tier in 2025-2026.

Its core value is the world's largest crowd-sourced food database (~20.5M items including restaurant menus) plus ecosystem breadth: wearables, steps, recipes, community forums, challenges. Logging spans search, barcode, photo (Meal Scan), voice, and meal copy/streak conveniences. In 2026 it layered on two trend features: GLP-1 medication tracking (dose logging, reminders, side-effect notes) and a conversational AI Coach tab that answers nutrition questions using your own diary data.

Market position: the default, most-known brand — which now works against it. The barcode paywall (Oct 2022) and repeated free-tier cuts generated lasting resentment, a May 2026 class action over paywall changes, and a steady stream of one-star reviews citing ads, price, and bloat; competitors (Yazio, Cronometer, Lose It!) explicitly market against MFP's paywall.

## Feature inventory

### Onboarding & diet-plan selection
- Classic wizard: height/weight/age/sex/activity level → goal weight + rate of loss; BMR via Mifflin-St Jeor; BMI-based guardrails block unsafe goals; ~500 kcal/day per pound/week math for the deficit.
- Free users get fixed calorie/macro goals; **custom macro goals, by-meal calorie goals, net-carbs/keto mode are Premium**.
- No real "diet template" selection (keto, vegetarian, etc. are implemented as macro splits, not diet plans with meal plans). Diet-type meal plans live only in the Premium+ Meal Planner.

### Food logging & nutrient estimation
- Database: **20.5M foods incl. restaurant items** (official); large but notoriously noisy — duplicates, stale user entries, wrong serving sizes. "Verified/Contributor" badges partially mitigate. Editing bad entries is painful.
- Barcode scanning: **Premium-only since Oct 1, 2022** (was free for a decade) — the single most-hated MFP decision.
- Photo: Meal Scan (Premium) matches camera images to database items; works best for packaged/single foods, weaker for mixed plates.
- Voice logging (Premium): speak foods + portions, review, tap to log; multi-day logging (Premium).
- Nutrients: calories, macros free; deeper targets (protein/sodium/fiber custom), micronutrient views, net carbs, per-meal macro breakdowns increasingly Premium; weekly nutrient "Insights" reports (Premium) and downloadable progress reports (Premium).
- Quick-add calories/macros, copy meal/day, recent/frequent foods, saved meals/recipes.

### Meal planning, recipes & shopping list
- Free: 2K+ recipes, save own foods/recipes, custom meals.
- **Premium+ Meal Planner**: customized weekly meal plans, 1,500+ goal-friendly recipes, budget-friendly options, auto-generated ingredient lists (shopping lists) with **sync to grocery-delivery apps**, plan-to-diary one-tap logging, recipe/grocery sharing.

### Exercise planning & tracking
- Cardio/strength logging, calorie burn added to budget; steps via phones/wearables; 40+ device syncs; Wear OS app with tiles/complications. No workout-plan builder or progressive training programs — it's a logger, not a planner.

### Weight & body metrics, forecasting
- Weight check-ins with optional progress photo, measurements, dashboard charts (free basics; deeper trends Premium).
- Beloved micro-forecast: completing your diary shows "If every day were like today, you'd reach your goal weight by \<date\>" — a 5-week projection line, one of the most-cited motivating features.
- New: **GLP-1 tracker** — log doses + reminders + side effects/symptoms, view patterns alongside nutrition.

### Photos / progress / silhouette
- Basic weight check-in progress photos only. **No silhouette/pose-based tracker, no body-composition visualizer** — an open space.

### Statistics, visualization & gamification
- Free: daily dashboard ring (calories remaining), weekly bar chart, basic nutrient panels.
- Premium: weekly "Insights" deep dives (e.g., nutrient distributions, logging-consistency effects), printable/downloadable progress reports, macro-trend charts.
- Gamification: logging **streaks**, community **challenges** (step/logging challenges), community forums + activity feed with reactions; 5.5M five-star-review social proof used in-app. Comparatively shallow by numbers-geek standards: no goal-date forecasting curves, no trend-weight smoothing (users export to Libra/Happy Scale for that).

### AI features
- **AI Coach** (June 2026): dedicated tab; open-ended nutrition Q&A grounded in remaining calories, macro gaps, logging history, saved meals and goals; suggests swaps, portions, recipes, meal pairings; menu-navigation help ("best option at this restaurant"); designed with RDs. Cloud LLM; no on-device processing; included with paid tiers per launch materials.
- Meal Scan + Voice Log are ML-driven capture, not conversation. AI is never on-device; privacy story contradicts local-first users.

### Input-minimization techniques
- Search autocomplete with matching serving sizes; Quick-add calories; copy yesterday/favorite meals; multi-day logging (Premium); voice logging (Premium); Meal Scan (Premium); nutrition-label photo auto-fill for custom foods. Barcode was the flagship "zero typing" input and it is the one they paywalled.

### Design & UX / micro-interactions
- 2025-2026 redesign: card-based dashboard, larger calorie-remaining number, bottom-sheet logging. 2026 Play reviews complain the redesign buried nutrient info "three levels deep," hid day-view items "in sub menus," removed swipe-to-previous-day navigation, and introduced diary-order and random-exit bugs. Historically the interaction model (fast search → add → green ring) set the genre standard.

## Strengths & differentiators
- Largest food database (20.5M) incl. restaurant menus; decades of brand trust and social proof.
- Ecosystem: 40+ integrations, Watch/Wear OS apps, Health Connect — the "hub" play.
- AI Coach grounded in *your own* logged data (right idea, even if cloud-only), designed with RDs.
- GLP-1 module (doses, reminders, side-effect patterns) — 2026 table-stakes feature, well executed.
- Premium+ meal-planner → grocery-delivery pipeline is a genuinely useful closed loop.

## Weaknesses & user complaints (cited)
- Barcode scanner paywalled Oct 2022 → mass backlash and migrations (XDA coverage; MFP community forum threads; users "abandoning the app" waiting for the feature to return).
- Free-tier erosion under private equity: Dec 2021 cuts (by-meal macros, certain diary views), 2025-2026 further gating; price hikes $49.99 → $79.99/yr; ads pushed into free tier. **May 2026 class action** over paywall/deceptive-"free" allegations (ConsumerTechWire).
- Database quality: duplicates, wrong entries, clunky correction (long-running complaint in reviews and r/MyFitnessPal).
- 2026 UI regressions: nutrient info buried in sub-menus, no swipe navigation, diary order bugs, app exiting to home mid-log (April 2026 Play reviews).
- Recipe save errors persisted "over a year" for one reviewer before a fix (Play review, Apr 2026).
- No on-device AI; account mandatory; 2018 breach legacy makes privacy-sensitive users wary.

## What WLO should learn
1. **Never paywall the capture layer.** Barcode/photo/voice input must be free forever in WLO. The barcode paywall is the single biggest self-inflicted wound in this market and directly created space for Yazio/Cronometer/Cronometer-style rivals — and the May 2026 class action shows the legal risk too. Monetize depth (advanced analytics, meal-plan generation), not the act of logging.
2. **AI Coach grounded in the user's own data is the winning coaching pattern** — MFP's "Coach reads your diary and answers in context" is exactly what WLO can build locally with an on-device LLM (or a user-keyed remote model) over the local diary: remaining calories, macro gaps, "what should I eat tonight" — but with answers computed on-device, no account required.
3. **Don't bury the numbers.** MFP's 2026 redesign enraged its core (numbers-geek) users by putting nutrients "three levels deep" and removing swipe navigation. WLO should make a dense, customizable nutrient dashboard + swipe-between-days the *default*, and treat progressive disclosure as opt-in, not mandatory.
4. **Keep the projection hook.** "If every day were like today → goal date" is the most emotionally quoted stat in the genre. WLO should go further: smoothed trend weight, goal-date range under uncertainty, per-diet adherence forecasts.
5. **GLP-1 support is now mainstream** — and WLO can uniquely fuse it with its poop/symptom tracker: dose ↔ hunger ↔ GI-symptom correlation charts no competitor offers (MFP only logs side effects; Lose It shows estimated GLP-1 level curves).
6. **Local verification of food data beats crowd-sourced quantity.** WLO's photo-first entries (photo attached to every log) create an audit trail MFP can't match; allow one-tap correction stored locally, and treat each correction as training signal for the on-device estimator.
7. **Community ≠ cloud account for WLO:** streaks and challenges work locally (personal bests, self-set challenges); skip the forum/newsfeed overhead that adds server cost and moderation burden.

## Sources
- [MyFitnessPal official site](https://www.myfitnesspal.com/)
- [MyFitnessPal Premium / Premium+ pricing page](https://www.myfitnesspal.com/premium)
- [Google Play listing — MyFitnessPal](https://play.google.com/store/apps/details?id=com.myfitnesspal.android)
- [Apple App Store listing — MyFitnessPal](https://apps.apple.com/us/app/myfitnesspal-calorie-counter/id341232718)
- [MFP press release: "Twenty Years of Nutrition Data, and One AI Coach" (June 10, 2026)](https://news.myfitnesspal.com/twenty-years-of-nutrition-data-and-one-ai-coach-that-puts-it-all-to-work-for-you/)
- [MFP Help: Introducing Nutrition Coach](https://support.myfitnesspal.com/hc/en-us/articles/45212266254221-Introducing-Nutrition-Coach-Your-Nutrition-Assistant)
- [XDA: MyFitnessPal puts barcode scanner behind paywall (Sept 2022)](https://www.xda-developers.com/myfitnesspals-barcode-scanner-behind-a-paywall/)
- [ConsumerTechWire: MFP class action over May 2026 paywall changes](https://consumertechwire.com/news/myfitnesspal-class-action-may-2026-paywall-changes/)
- [Reddit r/Myfitnesspal: "Free version just became even more useless"](https://www.reddit.com/r/Myfitnesspal/comments/1p2k56b/)
- [MFP Community forum: Barcode scan for non-premium users](https://community.myfitnesspal.com/en/discussion/10928711/barcode-scan-for-non-premium-users)
