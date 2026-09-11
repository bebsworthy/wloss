# Lose It! — Feature Analysis

**Category:** Health & Fitness — calorie counting, budget-based weight loss ("weight loss that fits")
**Platforms:** iOS (4.77★, ~776K US ratings), Android `com.fitnow.loseit` (4.5★, ~180K ratings, 10M+ downloads), web diary, Apple Health / Google Fit / Fitbit / Garmin / Withings / Misfit syncs (several syncs listed under Premium)
**Pricing model:** Freemium, positioned as the friendly one. Free tier includes calorie tracking, weight plan, and (unlike MyFitnessPal) usable barcode scanning and challenges. **Premium ≈ $39.99/yr** (IAP variants $19.99–$79.99 by offer; ~$9.99–$11.99/mo; **Lifetime $49.99–$79.99**), roughly half of MFP Premium. 7-day trial.
**AI usage:** Cloud ML/AI. "Say It!" (AI voice logging) and "Snap It!" (photo logging) match inputs to a 63M-item database; own materials state there is **no on-device AI**. GLP-1 module computes estimated medication level curves.
**Local & privacy posture:** Account-based cloud app (FitNow, Inc., a Ziff Davis company). Consumer Health Data privacy policy; no local-first or on-device claims.

## Overview

Lose It! (2008, Boston-based FitNow, acquired by Ziff Davis) is the "people's calorie counter": simpler and cheaper than MyFitnessPal, with a goal-driven model — set a target weight, get a personalized daily calorie budget and a **weight-loss schedule**, log against it. It claims 57M+ users and 150M+ lbs lost.

Its 2024-2026 strategy is AI input speed ("Say It. Snap It. Scan It."): AI voice logging that parses a spoken sentence ("I had 2 eggs, toast with butter and jam") into database-matched entries, and a revamped Snap It photo model. Its April 2025 press release claims users of these AI tools see **6% more weight loss, 3.5× faster meal logging, and 2× more foods logged**. It also shipped GLP-1 support (medication logging with estimated GLP-1 level curves) and a calorie-schedule/cycling model that lets power users shift calories between weekdays and weekends.

Market position: the value alternative to MFP — "a better-value MyFitnessPal" per 2026 reviews — winning on price, free tier usability, and clean UX, while sharing MFP's structural weakness: logging mixed meals still means searching and selecting database entries, and Snap It leans on database matching rather than true portion estimation.

## Feature inventory

### Onboarding & diet-plan selection
- Concise wizard: profile → goal weight → personalized calorie budget + weight-loss schedule. Markets "choose from over 25 health goals" (weight, macros, sodium, sugar, water, exercise, fasting...).
- Diet templates: supports IF, low-carb, high-protein, calorie cycling as *goal configurations* (macro splits + fasting plans), not full meal-plan programs. Named "Weight Loss Diet Plans" (Premium) identify which foods/recipes help or hinder progress.
- Distinctive: **Calorie Schedule** — plan to eat more on weekends / less on weekdays while keeping the weekly deficit (calorie cycling), a power-user favorite.

### Food logging & nutrient estimation
- Database: App Store says **56M+ foods & recipes**; April 2025 release says **63M items** (largest claim in the trio). Includes restaurant and grocery chains. Same crowd-source noise problem as MFP, but generally regarded as having a cleaner search experience.
- Barcode scanning of packaged foods — available and usable on the free tier (a core anti-MFP positioning point; one 2024 Play review still complains barcode options were pushed toward paywall, so the boundary is blurry), with **nutrition-label photo auto-fill** when creating custom foods.
- Snap It (Premium): photo → database match; reviewed as good for packaged/single items, "hit-or-miss" for mixed/home-cooked plates; not a portion-estimation model.
- Say It! (Premium): AI voice phrase parsing → matched foods; loss-media-darling feature.
- Nutrients: free covers calories + 3 macros; **Premium unlocks full macro breakdown (fat types, sugar, fiber), micronutrients (vitamins/minerals), and health-metric tracking** (blood pressure, glucose, cholesterol, sleep, body measurements).

### Meal planning, recipes & shopping list
- Premium "Meal Planning & Targets": suggested nutrient/calorie targets per meal; recipe database with search; custom recipes & foods; "Weight Loss Diet Plans" analysis of helpful/hindering foods. Shopping-list generation is not a flagship feature (weaker than Yazio/MFP Premium+).

### Exercise planning & tracking
- Exercise log with large activity list + net-calorie budgeting, steps, and device syncs (Apple Health, Google Fit, Fitbit, Garmin, Withings, Misfit — several listed under Premium). No workout-plan builder ("N/A — not offered" beyond logging).

### Weight & body metrics, forecasting
- Core strength: the plan itself is a forecast — onboarding produces a **day-by-day weight-loss schedule** with weekly calorie budgets; weight chart overlays progress vs. plan.
- Custom goals beyond weight: body measurements (neck/waist/hips...), body fat, blood pressure, glucose, cholesterol, sleep (mostly Premium "Advanced Tracking").
- Milestones/achievements for weight lost and logging consistency.

### Photos / progress / silhouette
- N/A — no progress-photo or silhouette tracker (only profile photo). (Lose It! famously had a shrinking avatar in its early years; long retired.)

### Statistics, visualization & gamification
- Daily budget ring, daily/weekly/monthly calorie charts, nutrient panels (Premium depth).
- **Patterns & Reports (Premium):** behavior/progress reports, including emailed digests.
- **Challenges:** community challenges (e.g., step/loss challenges), support groups, friends feed, sharing; logging streaks; "start seeing results in just three days" habit framing.
- Numbers-geek depth is moderate: no trend-weight smoothing, limited forecasting beyond the schedule line.

### AI features
- **Say It!** (voice) and **Snap It!** (photo): both cloud AI that match to the 63M-item database rather than generatively estimating nutrition. April 2025 company data: 3.5× faster logging, 2× more foods logged, 6% more weight loss among users of these features.
- **GLP-1 module:** log medication, view **estimated GLP-1 level curve**, align hunger/eating patterns with phases (2024-2025 addition).
- No conversational coach (no ChatGPT-style tab — that's MFP's 2026 differentiator), no on-device AI.

### Input-minimization techniques
- The trio "Say It / Snap It / Scan It" + search with instant serving matches; copy previous meals; recent/frequent lists; nutrition-label photo auto-fill for new foods; recipe import via URL (web). The company explicitly re-oriented the product around "log a meal in seconds" as the retention lever.

### Design & UX / micro-interactions
- Clean, bright, approachable UI widely rated friendlier than MFP; budget ring as the central object. A May 2026 Play reviewer says a recent update "is NOT nearly as easy to use" — specifically the option to mark the day's logging complete got harder to find — enough that she'd switch despite a paid year. So its UX lead is real but fragile under redesigns.

## Strengths & differentiators
- Friendlier, genuinely usable free tier (barcode scanning) at ~half MFP's price; lifetime purchase option.
- AI voice logging ("Say It!") is the best-in-class speech → multi-food parse, with published outcome data (3.5× faster logging, 6% more weight loss).
- Calorie schedule/cycling (weekday/weekend budgets) — rare, power-user-loved planning feature.
- Plan-as-forecast model: weight-loss schedule overlay on the weight chart.
- GLP-1 estimated-level curve visualization — more analytical than MFP's dose log.
- Largest claimed DB (56–63M items).

## Weaknesses & user complaints (cited)
- Snap It is database-matching, not estimation: weak on mixed/restaurant meals; users fall back to manual search (2026 reviews).
- Free/Premium boundary drift: official listing puts photo logging, AI voice, barcode, advanced nutrients under Premium; users complain about upsells even inside Premium and trial auto-renewal prompts ("typical scam" review, 2021).
- May 2026 update broke usability for existing users (Play review: plans to switch despite paying for a year).
- No conversational AI coach; no on-device AI; account + cloud required.
- No progress-photo/silhouette tracking; measurement tracking gated to Premium.
- Weaker shopping-list/meal-plan pipeline than Yazio and MFP Premium+.

## What WLO should learn
1. **Publish your input-speed evidence.** Lose It!'s "3.5× faster, 2× more logging, 6% more weight loss" framing proves AI-capture quality correlates with outcomes. WLO is photo-first by design; measure and surface the same stats (e.g., median seconds-to-log) as an in-app "streak efficiency" flex for numbers geeks.
2. **Copy "Say It!" parsing style, not database matching, for photos.** Voice logging that turns one sentence into N matched foods is great; photo logging that only matches DB entries fails on mixed plates. WLO should do generative on-device estimation *with* local DB fallback + confidence display, and attach the photo to the entry for later correction.
3. **Adopt calorie scheduling/cycling natively.** Weekday/weekend budget shapes ("eat more on weekends") is a small feature with outsized love — perfect for a numbers-geek audience and trivial to render as a 7-bar budget chart.
4. **The plan *is* the forecast.** Lose It! bakes a weight-loss schedule into onboarding and overlays actual vs. planned weight. WLO should go further with smoothed trend lines and goal-date ranges, but the "plan overlay" pattern is the right spine for the weight module.
5. **Free barcode scanning is the trust anchor.** Lose It! beats MFP on goodwill simply by keeping scan capture free. WLO: capture free forever; a lifetime purchase option (Lose It! sells one, $49.99–$79.99) suits local-first users better than subscriptions.
6. **Nutrition-label photo auto-fill** for custom/local foods (no internet, no DB) is an ideal on-device-OCR feature for WLO — capture once, reuse forever, data stays local.
7. **Weekly emailed "Patterns" reports show recurring digests work** — WLO's local equivalent: a beautiful on-device weekly digest with charts, computed and rendered locally, exportable as PDF/image.
8. **GLP-1 estimated-level curves** are a great example of turning a med log into an analytical chart — pair that curve with WLO's poop tracker (GI side effects are a top GLP-1 complaint) for a correlation view nobody else ships.

## Sources
- [Lose It! official site](https://www.loseit.com/)
- [Apple App Store listing — Lose It!](https://apps.apple.com/us/app/lose-it-calorie-counter/id297368629)
- [Google Play listing — Lose It!](https://play.google.com/store/apps/details?id=com.fitnow.loseit)
- [Press release (Apr 22, 2025): AI-Powered Logging Boosts Weight Loss Success](https://www.newswire.com/view/content/lose-it-finds-ai-powered-logging-boosts-weight-loss-success-and-22557702)
- [Bento Bunny (June 2026): Lose It! App Review — features, cost, Snap It](https://www.bentobunny.app/reviews/lose-it-review)
- [NutriScan (2026): Lifesum vs Lose It — photo, voice logging and meal plans](https://nutriscan.app/blog/posts/lifesum-vs-lose-it-2026-photo-voice-logging-meal-plans-473027de34)
- [Reddit r/loseit / r/CICO discussions on Lose It! Premium](https://www.reddit.com/r/loseit/)
