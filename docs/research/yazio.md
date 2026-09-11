# Yazio — Feature Analysis

**Category:** Health & Fitness — calorie counter + intermittent fasting + AI food tracker (2026 flagship rebrand: **"AI Calorie Tracker by Yazio"**; Play listing: "Yazio: AI Calorie Tracker")
**Platforms:** iOS (4.6–4.7★, ~50K US ratings), Android `com.yazio.android` (4.5★, ~859K ratings, 50M+ downloads; #3 top-grossing health & fitness app; Google Play "Android Excellence" selection), plus a separate fasting-tracker app; web tools (BMI/ideal weight/calorie/burned calculators). Fitbit/Garmin sync (Pro).
**Pricing model:** Freemium with a **fairly useful free tier** (diary, barcode, recipes + grocery lists, fasting timer, steps, water; **no sign-up required**). **Yazio Pro** subscription: IAP tiers seen at **3 months $23.99, semi-annual $34.99, 12 months $23.90–$47.90** (heavy promo framing: "63% OFF Yazio Pro"). Pro adds custom meal plans, in-depth analysis, body/mood/symptom tracking over time, long-term trends, wearable sync, ad-free.
**AI usage:** Cloud AI. **AI photo tracking** (snap a meal → instant calories/nutrition; launched 2025, marketed as free), plus per-meal AI "tips"/reviews generated from logged foods. No on-device AI.
**Local & privacy posture:** Cloud app by YAZIO GmbH (Erfurt, Germany); GDPR-bound, strong EU positioning and DACH market strength; still account/cloud-centric, not local-first.

## Overview

Yazio is the biggest calorie-counting success story of the post-MFP-backlash era: 100M+ users claimed, 50M+ Android downloads, ~859K Play ratings, and top-3 grossing in its category — built largely on European markets by pairing a genuinely functional free tier with intermittent-fasting tracking and a friendly, colorful design. In 2025-2026 it rebranded its flagship to "AI Calorie Tracker by Yazio" and shipped free AI photo tracking, repositioning from "calorie counter + fasting" to "AI-first tracker."

The product is diet-agnostic and goal-driven: onboarding ("Get your custom plan in minutes") covers lose weight, build muscle, eat balanced, track macros, mindful eating, cravings, routines, maintain — with IF plans (16:8, 5:2, 6:1) as first-class citizens, timers integrated with the food diary. Content is a differentiator: 2,900–3,000+ goal-friendly recipes updated weekly, with filters (low-carb, vegetarian, vegan), a smart grocery list, and a step-by-step cooking mode. A curated food database (4M+ items, "95% of all food items" claimed) uses color-coded **food ratings** to steer choices.

Market position: the "fair" mainstream tracker — the app people name as where they fled after MFP's barcode paywall — now fighting the AI-tracker wave (Cal AI etc.) by bundling AI photo logging, and monetizing via Pro plans/insights rather than crippling capture.

## Feature inventory

### Onboarding & diet-plan selection
- Multi-step wizard: goal → body data (Mifflin-St Jeor for targets) → diet preference → plan generation; produces a daily calorie/macro budget and, with Pro, a **custom meal plan**.
- Explicit goal catalog: lose weight, build muscle, eat balanced, track macros, eat mindfully, keep cravings in check, build healthy routines, maintain weight.
- **Intermittent fasting plans built in** (16:8, 5:2, 6:1) with timers and reminders; education on ketosis, autophagy, energy use.
- Notable: **"Instant access with no sign-up required"** — the app is usable before creating an account.

### Food logging & nutrient estimation
- Database: **4M+ searchable items**; claims "95% of all food items"; team-curated with quality control, but user corrections are hard (a 2026 Pro reviewer notes fixing bad entries requires manually entering nutrition facts per 100 g and doing "numerous manual calculations"; Yazio confirmed no photo recognition for database fixes).
- Three capture paths: **AI photo tracking** (free), **barcode scanner** (free), manual entry. AI reviews/tips are auto-generated after logging a meal (some users find the unskippable 3-tip screen annoying; developer says AI camera tracking is optional).
- **Food ratings**: color-coded circles ("traffic-light"-style) marking healthier choices — instant visual guidance beyond raw numbers.
- Nutrients: calories, macros, key nutritional values on free; deeper "in-depth food and calorie analysis" under Pro. Macronutrient targets customizable.

### Meal planning, recipes & shopping list
- **2,900–3,000+ recipes**, updated weekly, filters (low-carb, vegetarian/vegan, goal-friendly), described as "easy to track" (ingredients map to DB entries).
- **Smart grocery list** generated from planned meals/recipes; **step-by-step cooking mode**; Pro adds plans "tailored to your goals" and budget options. This is the strongest mainstream recipe→shopping pipeline of the three apps.

### Exercise planning & tracking
- Integrated step counter + activity logging with calorie burn, symptom logging, water tracker with reminders; fitness-tracker sync (Fitbit, Garmin, more) is a **Pro** feature. No workout-plan builder ("N/A — not offered" beyond logging/planning of activities).

### Weight & body metrics, forecasting
- Weight logging with a **Trends chart that projects weeks-to-goal** based on the logged deficit and trend line (long-time user favorite; Reddit threads track its relocation to the profile page).
- Pro: "track your mood, symptoms and body metrics over time" + "long-term trend insights" — body measurements, mood, GI symptoms (unusual breadth for a calorie app).
- No on-device statistical smoothing (trend weight à la Happy Scale) — projection is the headline analytic.

### Photos / progress / silhouette
- N/A — no progress-photo or silhouette tracking. (Photos exist only as inputs to AI meal logging.)

### Statistics, visualization & gamification
- Visual calorie/nutrient analysis and history charts; calorie-deficit view; dashboard ring for consumed/remaining/burned calories.
- **Streak feature** (recently introduced) for logging consistency, explicitly framed as gamification ("more fun and rewarding").
- Pro unlocks "long-term trend insights" and advanced analysis. Light on badges/challenges compared to Lose It!; no community/social layer (cleaner, but fewer engagement hooks).

### AI features
- **AI photo tracking** (2025): snap a meal → instant calorie/nutrition estimate; promoted as free; cloud-based computer vision.
- **AI meal reviews/tips**: automatically generated feedback ("3 tips") after adding a food; users report the reviews "tell you the same thing over and over again" and one reports the photo analysis "only analyses the left half of the picture" (Play review, Aug 2026).
- No conversational coach tab (unlike MFP's AI Coach), no on-device inference.

### Input-minimization techniques
- AI photo (zero typing), barcode, "no sign-up required" start, reminders for meal timing, recent/frequent foods, copy meals. Team curation keeps search results relevant so users pick the first hit rather than comparing entries.

### Design & UX / micro-interactions
- Widely praised as the best-looking of the trio: colorful, friendly, clean cards, smooth ring animations, food-rating dots, intuitive fasting timer. "Simply amazing… everything in one place" (Play review). Negatives are monetization feel (constant "63% OFF" banners) and unskippable AI-tip interstitials after logging — UX friction added by monetization, not layout.

## Strengths & differentiators
- Best free-tier-to-value ratio among the big three; capture (barcode, AI photo) is free; monetization targets plans/analysis/sync instead.
- Intermittent fasting as a first-class, integrated module (timers + diary + education).
- Recipe → smart grocery list → step-by-step cooking mode pipeline.
- Curated DB + color-coded food ratings = decision support, not just record-keeping.
- No-sign-up onboarding; strong EU/GDPR trust posture; 100M+ user scale.
- AI photo tracking priced into free tier — 2026 table stakes, matched early.

## Weaknesses & user complaints (cited)
- AI quality is shallow: repetitive AI meal reviews; photo analysis errors ("only analyses the left half of the picture" — Pro user review, Aug 2026); AI-tip screen after every add annoyed users enough to demand a toggle (Play review, May 2026).
- Database corrections require painful per-100 g manual math; no photo recognition for fixes (Play review, Apr 2026).
- Aggressive upsell atmosphere: perpetual discount banners; several formerly-free conveniences (wearable sync, in-depth analysis, body metrics over time) moved behind Pro over the years.
- Gamification thin (streaks only); no challenges/community; numbers-geek depth limited (no smoothing, no micronutrient deep-dives on free).
- No progress-photo/silhouette tracker; no conversational AI coach; cloud AI only.
- Pro pricing is confusingly fragmented (3-mo $23.99 vs 12-mo $23.90–$47.90 depending on offer/persona).

## What WLO should learn
1. **No account, no cloud, no sign-up is a feature people notice.** Yazio markets "instant access with no sign-up required" and it converts. WLO is local-first: make "works fully offline from first launch, account optional and late (if ever)" the headline onboarding claim — Yazio proves it sells.
2. **AI capture belongs in the free tier; monetize analysis, not capture.** Yazio (photo free) vs MFP (photo+barcode paid) is a live A/B across the market; the goodwill differential is measurable in review text. WLO: all capture free; sell deep analytics, forecasting, plans, exports.
3. **Don't interject AI where the user didn't ask.** Yazio's unskippable "3 tips" screen after every food entry and repetitive AI reviews are cited friction — even paying users complain. WLO: AI insights live in a dedicated surface the user opens, are varied (rotate templates, reference actual history), and are dismissible. On-device generation makes variation cheap (no per-call cost).
4. **Adopt food ratings as a second visualization layer.** Color-coded dots on foods turn nutrient data into instant decisions — a numbers-geek app can go deeper: nutrient-density score, per-100-kcal protein/fiber badges, trend of meal quality across the week.
5. **Steal the fasting-module integration pattern** (timers + diary + education + plans 16:8/5:2/6:1) for WLO's multi-diet templates: a diet template should bundle targets + timing rules + relevant tracker surfaces (e.g., keto template → carb-limit ring + ketosis notes; IF template → timer widget).
6. **Recipe → grocery list → cooking mode is the validated loop** for WLO's meal-planning core: shopping list aggregated across a week plan with quantity consolidation, plus a hands-free step view. Yazio shows content quantity matters (2,900+ recipes, weekly updates) — but WLO can lean on AI-generated plans from user-supplied models instead of a content team.
7. **Protect the trends/forecast discoverability.** Yazio's weeks-to-goal projection is beloved, yet moving it ("Where has the Trends bit gone?" — r/yazio) triggered confusion. WLO: forecast + trend weight permanently on the main weight screen; never relocate user-learned UI.
8. **Local-first closes the DB-correction wound.** Yazio's fix-a-bad-entry flow (manual per-100 g math, no photo recognition) is exactly where WLO's architecture wins: every entry carries its photo; one tap re-estimates or lets the user type a correction that is stored locally and reuses it forever — no "database maintainer" bottleneck.
9. **Simple but honest pricing.** Yazio's tier list is confusing ($23.99/3-mo vs $23.90/12-mo promo lattices). WLO: one clear price or lifetime; numbers geeks do the math and resent dark-pattern pricing.

## Sources
- [Yazio official site](https://www.yazio.com/)
- [Google Play listing — Yazio: AI Calorie Tracker](https://play.google.com/store/apps/details?id=com.yazio.android)
- [Apple App Store listing — AI Calorie Tracker by Yazio](https://apps.apple.com/us/app/ai-calorie-tracker-by-yazio/id946099227)
- [Yazio Help Center: What is the AI Calorie Tracking feature and how does it work](https://help.yazio.com/hc/en-us/articles/39137901903889-What-is-the-AI-Calorie-Tracking-Feature-and-how-does-it-work)
- [Yazio Help Center: Streak feature — functionality and boost for your goals](https://help.yazio.com/hc/en-us/articles/24629053698833-Streak-feature-Functionality-and-boost-for-your-goals)
- [Reddit r/yazio: "Where has the Trends bit gone?"](https://www.reddit.com/r/yazio/comments/1metz6f/where_has_the_trends_bit_gone_the_one_that_let/)
- [Darwin Nutrition dietitian review of Yazio](https://www.darwin-nutrition.fr/en/brand-tests/yazio-review/)
- [Nutrola (2026): Is Yazio still good?](https://nutrola.app/en/blog/is-yazio-still-good-2026)
