# Foodvisor — Feature Analysis

**Category:** All-in-one AI nutrition & weight-loss coach: photo calorie tracker + personalized program + recipes + workouts (Health & Fitness)
**Platforms:** iOS (App Store Editors' Choice badge), Android. Google Fit and Apple Health sync. B2B Vision API also offered.
**Pricing model:** Free tier (limited features, contains ads) + Premium subscription with unusually many SKUs: US App Store shows 1 month $14.99–$39.99, 3 months $23.99–$89.99, trial SKUs $59.99–$71.99, "Premium Bundle" $29.99+; third-party reviews put annual at roughly $60–$84 (~$5–$7/month effective) vs. ~$11–$15 month-to-month. Frequent aggressive in-app discount prompts. 14-day free trial exists but is only surfaced after signup and declining the subscription ([FeastGood](https://feastgood.com/foodvisor-review/)). A human-nutritionist coaching add-on sits at the top tier.
**AI usage:** Proprietary cloud computer-vision food recognition (also licensed as the Foodvisor Vision API); AI-assisted nutrition coaching blended with human dietitian chat; AI-generated personalized workout plans (coach videos). No on-device inference claims.
**Local & privacy posture:** Cloud-first, ad-supported free tier. Play data safety: may share personal info, messages, and 3 other types with third parties; may collect personal info, messages, and 4 other types; encrypted in transit; deletion supported. French company (Foodvisor SAS, Levallois-Perret) — GDPR jurisdiction, EEA/UK/CA user rights documented.

## Overview

Foodvisor is the European veteran of this comparison: a French startup (15M+ users claimed, 10M+ Android downloads) that has evolved from a pure photo-recognition calorie counter (circa 2017) into an all-in-one "no restrictive diet" weight-loss ecosystem. Its App Store positioning — Editors' Choice, "#1 nutrition app, supercharged with AI" — and a 4.7/5 Play rating across 199K reviews make it one of the highest-rated large nutrition apps, standing out in a field dominated by MyFitnessPal and Yazio.

Unlike Cal AI (numbers-only coach) and SnapCalorie (engine-only tracker), Foodvisor bundles the full journey: onboarding survey → personalized calorie/macro program → photo/voice/barcode/text logging → micronutrient analysis → recipes → workout videos → weight/hydration/fasting tracking → education library (500+ articles, daily lessons) → community, plus a real human nutritionist via 1:1 chat at the top tier. Its engagement layer is distinctive: a **virtual "Seed" — a garden creature that grows as habits accrue**, with daily quests and events ("See Seed's world come to life as it changes throughout the day") — the most explicit gamification of the three apps, closer to Duolingo than to MacroFactor.

It also validates its outcome claims with self-reported data ("users lose an average of 15.83 lb in 3 months," internal study of 4,419 users, Jan 2026, average BMI 34.72) and its recognition stack with third-party peer-reviewed data: in a 2020 dietary-assessment study (185 images, Belgian diet), Foodvisor achieved 46.2% top-1 / 71.5% top-5 recognition and the best "totality" score for mixed-dish components (70.8%, 119/168), though "none of the platforms were capable of estimating the amount of food" — a weakness its portion-adjustment correction flow exists to paper over.

## Feature inventory

### Onboarding & diet-plan selection
- Survey (age, gender, height, weight, goal, goal speed, dietary preferences, environment, struggles) → personalized daily calorie and macro targets, displayed front-and-center on the dashboard.
- Personalized "nutrition programme" designed "with nutrition experts"; explicit anti-diet positioning: "No restrictive diet. No yo-yo effect."
- Supports varied regimens as tracking contexts (keto, fasting, high-protein) but the product identity is a flexible balanced program, not named diet templates.
- **Targets do NOT auto-adjust as weight changes** — a documented drawback vs. MacroFactor/Carbon; the human nutritionist fills that role manually for coaching subscribers.

### Food logging & nutrient estimation
- Pipeline: point camera or upload photo → cloud AI recognizes items on the plate → portion estimate → **"a new window opens where I can individually select the foods (and respective servings)"** ([FeastGood](https://feastgood.com/foodvisor-review/)) → swap foods for alternatives, adjust servings, optionally send feedback on the guess → logged. Marketing: "log your meals in 5 seconds flat."
- Tracks calories, macros, **vitamins and minerals** (full micronutrient analysis gated to Premium).
- Fallback inputs: **five ways to log** — AI photo, voice logging, smart text search, barcode scanner, favorites/recents. Barcode scanner described as "reliable" in hands-on testing (in contrast to Cal AI's EU coverage complaints).
- Accuracy reality: hands-on testing verdict "good (not perfect), and generally guesses what's on my plate"; handles a diced-steak-potatoes-vegetables plate correctly; **struggles with complex bowls where ingredients aren't visible** (e.g., oatmeal). Peer-reviewed 2020 data: 46.2% top-1 / 71.5% top-5; best-in-class mixed-dish component coverage (70.8% totality); zero portion-size estimation ability (pre-depth-sensor era).
- Google Play users have noted the scanner "doesn't work nearly as well as advertised" in some cases, but recent top reviews largely praise the camera; complaints concentrate on search and paywalling instead.

### Meal planning, recipes & shopping list
- 300+ recipes, each with photo, prep time, difficulty, ingredients, step-by-step directions, and full nutrition; the app computes a recipe's share of daily targets (e.g., "Chocolate Oatmeal = 8% of calories, 7 g of 167 g protein"). Recipes can be added to the diary but **cannot be modified** — critics note the library is thin ("a bowl of fruit is not a recipe") and inflexible.
- No auto-generated shopping list. N/A — not offered.
- Premium unlocks "hundreds of personalized recipes" and "tailored advice"; daily lessons draw from a 500+ article education library.

### Exercise planning & tracking
- Personalized workout plan generated from the onboarding survey: **coach-led videos with easy/hard modifications**, followable at home ([official guide](https://www.foodvisor.io/en/guides/article/what-is-foodvisor-and-how-can-it-help-you-lose-weight/); Reddit consensus: "fairly basic").
- Manual exercise logging via a large alphabetized activity catalog with effort levels (light/moderate/vigorous) and sub-types (boxing: bag work vs. sparring; cycling by speed); "Sports mode" adds burned calories to the daily budget and can be switched off.
- Premium gates "unlimited workout sessions."
- Activity/hydration/fasting integrated into the same daily dashboard; Google Fit / Apple Health sync.

### Weight & body metrics, forecasting
- Daily weigh-in prompt on the dashboard; weight rendered in what a reviewer called a "beautiful graph"; weekly weight-progress view.
- **Body weight only** — no body-fat %, girth measurements, progress photos, or clinical metrics (blood pressure/glucose) — an explicit limitation for a "body-metrics" audience.
- **No forecasting**: no goal-date projection, no plateau-adaptive target adjustment. Reminders promote daily weigh-ins, which some users found obsessive (see Weaknesses).
- Hydration logging in 0.25 L increments with reminders; intermittent-fasting timer.

### Photos / progress / silhouette
- N/A — not offered. No progress-photo tracker, no silhouette alignment, despite photography being the input medium for food. (A reviewer requested a mood button; photo-based body tracking is absent.)

### Statistics, visualization & gamification
- Detailed habit reports; macro/vitamin/mineral dashboards; weight and hydration graphs.
- **Gamification: the "Seed"** — a virtual plant/creature that grows with logged habits; daily quests; seasonal events; the pet changes appearance throughout the day; "Add Your Friends" community events; recipe/progress sharing. Duolingo-style engagement loop.
- Motivational reminders and immediate "next action" prompts on login (weigh in, log meal, drink water).
- Dark side: the pet is **forced** — a 1-star Aug 2026 Play review calls its long animations unskippable; multiple users complain of guilt-toned alerts ("Great design, not so great for psyche").

### AI features (CRITICAL)
- **Model choice:** proprietary in-house computer-vision models (the same stack licensed as the [Foodvisor Vision API](https://apis.io/providers/foodvisor/)), trained on an in-house dataset; recognition returns food classes plus nutrition via its database. Coaching layer mixes scripted/lessons, AI-generated plans, and human dietitians; some users report the "nutritionist" chat feeling AI-like. No LLM-brand claims.
- **On-device vs cloud:** fully cloud. No offline recognition; no on-device claims anywhere.
- **Accuracy:** no self-published percentage; relies on third-party validation (2020 study above — best-in-class mixed-dish coverage among 7 platforms, but pre-modern). Current-gen accuracy is marketed anecdotally ("in 5 seconds flat") rather than quantified.
- **Limitations:** mixed/occluded bowls (oatmeal-type) fail silently; no depth-sensor portion estimation documented — portion accuracy rests on the manual serving-selection correction screen; search (the non-photo fallback) is algorithmically weak ("only uses one or two of the keywords," "terrible search algorithm" reviews).
- **Outcome claim:** "average 15.83 lb lost in 3 months" from an internal (not peer-reviewed) study — better than nothing, self-selected though.

### Input-minimization techniques
- Input hierarchy: **photo > voice > barcode > text search > favorites/recents** — the complete ladder, the only app of the three with all five implemented.
- Correction-after-recognition is structured: per-food serving selection in one modal window, food swap alternatives, feedback channel — minimizing re-entry without hiding control.
- Favorites/recents and saved meals reduce repeat cost; food/recipe/meal saving is well supported (though users report search won't reliably resurface frequent foods — undermining the recents shortcut).
- Dashboard prompts convert "I opened the app" into a logged action (weigh-in, water, meal) — opportunistic micro-entry.
- Gaps: no text-hint channel into the photo estimate (unlike SnapCalorie), no screenshot import, no speaking weights, and gram-only defaults hamper non-metric users.

### Design & UX / micro-interactions
- Clean, colorful, beginner-friendly design repeatedly praised ("great design," "beginner-friendly"); goals front and center; simple non-intrusive interface (FeastGood).
- Gamified micro-interactions around Seed's growth, quests, and daily-changing states; streak-like habit reinforcement via quests rather than hard streaks.
- Anti-patterns: forced pet animations on core flows; guilt-based food alerts; frequent discount/paywall interruptions ("It legit feels like harassment"); duplicate/missing notification bugs; Android freezing and false "No internet connection" errors reported by users.

## Strengths & differentiators
- **The most complete ladder of logging inputs** (photo, voice, barcode, text, favorites) of any app studied, in one coherent 5-second loop.
- **Structured post-recognition correction**: per-food serving selection + swap alternatives + feedback — the best-defined "what happens after the photo" flow of the three.
- **Full-journey bundle**: program + recipes + workouts + education + community + human coaching — one subscription replaces three apps; the reason for its Editors' Choice badge and 4.7/5 at 10M+ downloads.
- **Best-in-study recognition breadth** for mixed dishes (2020 peer-reviewed comparison) and a B2B Vision API proving the tech beyond the app.
- **Gamification with personality** (Seed/quests) that demonstrably drives habit logging — the closest analog to WLO's gamification ambition, with hard-won lessons about making it optional.
- Outcome-oriented credibility attempt (internal weight-loss study) and GDPR-grade data-rights hygiene.

## Weaknesses & user complaints
- **Paywall aggressiveness is the #1 complaint:** near-total feature gating, constant discount prompts perceived as harassment, trial only revealed after signup, and a Premium Bundle that "did not deliver what was advertised" (recipes with smiley faces, guilt-based alerts).
- **Forced gamification:** the Seed pet cannot be disabled and its long animations block core flows — the strongest evidence in this research that gamification must be opt-out-able.
- **Search quality:** keyword search matching only 1–2 terms, poor memory of frequent foods — the non-photo fallbacks lag far behind the photo path.
- **No auto-adjustment of targets on plateaus**; no weight forecasting; body metrics limited to weight only; no progress photos.
- **Recipes rigid and shallow** (unmodifiable, thin content); workout videos "fairly basic"; nutritionist chat sometimes reads as AI, with limited hours.
- **Stability/notification bugs on Android** (duplicate reminders, missing ones, freezes, false offline errors); gram-only units; restaurant meals hard to log; photo recognition weaker than advertised in some user reports on complex meals.

## What WLO should learn
1. **Build the five-rung input ladder, but keep it local:** Foodvisor proves photo + voice + barcode + text + favorites in one app is the right shape ("log in 5 seconds" is credible). WLO's twist: photo and voice can run on-device, and the ladder must degrade gracefully offline — Foodvisor's cloud-only recognition leaves a hole WLO can own.
2. **Steal the structured correction window.** One modal after recognition with per-food serving steppers, swap-alternatives, and a feedback button is the best post-photo UX pattern found in this research. WLO should add the missing fourth element: a free-text hint that re-estimates (SnapCalorie's lesson) — Foodvisor's swap-without-re-estimate stops halfway.
3. **Gamification must be opt-out and shallow-persistent.** Foodvisor's Seed drives daily logging — and its forced, unskippable animations generate 1-star reviews. WLO: make the companion delightful but dismissible, with an accessibility setting for animation length, and tie rewards to *streaks of logged effort* rather than perfect compliance to avoid the guilt-alert backlash.
4. **Never guilt-trip the user.** "Guilt-based food alerts" and mandatory daily weigh-ins drove a Premium customer to delete the app. For weight loss especially, WLO's tone engine should be neutral-analytic (numbers, trends) — which conveniently matches the numbers-geek target.
5. **Fix the fallbacks, not just the AI.** Foodvisor's photo path is good while its text search and recents are weak — users notice that asymmetry. WLO should treat search/recents as first-class: fuzzy multi-keyword matching, "log this again" one-tap from history, and learned per-user food aliases, all trivially local.
6. **Forecasting is an open goal.** None of the three apps does transparent weight forecasting well (Foodvisor: none; Cal AI: opaque goal-date). WLO can lead with local trend models (moving-average/exponential-smoothing projection to goal weight with confidence bands, plus target auto-adjustment on plateaus — Foodvisor's admitted gap and the exact reason people pay for MacroFactor).
7. **Body metrics should go beyond weight.** Foodvisor tracks weight only — no measurements, no photos, no body-fat. WLO's body-metric + silhouette tracker is a genuine differentiator vs. all three; add forecasting and photo-alignment there and WLO covers the quadrant every incumbent leaves empty.
8. **Price honestly.** Foodvisor's SKU zoo ($14.99–$89.99 overlapping tiers, hidden trial, harassment-grade upsells) is the opposite of trust. A local-first WLO can win on one clear price and "your data never leaves your phone" — and should say so in exactly those words at onboarding, not after it.

## Sources
- [Foodvisor — Google Play listing](https://play.google.com/store/apps/details?id=io.foodvisor.foodvisor&hl=en_US) (features, data safety, reviews incl. Seed/pet and search complaints)
- [Foodvisor — Apple App Store listing](https://apps.apple.com/us/app/foodvisor-ai-calorie-counter/id1064020872) (Editors' Choice, IAP price table, weight-loss study claim, reviews)
- [iTunes Search API record — Foodvisor](https://itunes.apple.com/search?term=foodvisor&entity=software&country=us) (4.59/5, 17.3K ratings, v9.23.1 description)
- [FeastGood — Foodvisor 1-month hands-on review](https://feastgood.com/foodvisor-review/) (correction flow, accuracy tests, recipes, workouts, weight-only tracking, human nutritionist chat, pricing)
- [Garage Gym Reviews — Foodvisor review](https://www.garagegymreviews.com/foodvisor-review) (coaching, daily lessons, ~$5/mo annual pricing)
- [Foodvisor official guide — What is Foodvisor](https://www.foodvisor.io/en/guides/article/what-is-foodvisor-and-how-can-it-help-you-lose-weight/) and [What is included in Foodvisor Premium](https://www.foodvisor.io/en/guides/article/what-is-included-in-foodvisor-premium/)
- [Foodvisor — Food image recognition explained](https://www.foodvisor.io/en/guides/article/food-image-recognition-explained/)
- [PMC (JMIR 2020) — Food image recognition platforms study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7752530/) (Foodvisor 46.2% top-1 / 71.5% top-5 / 70.8% totality; no platform estimated portion size)
- [APIs.io — Foodvisor Vision API provider profile](https://apis.io/providers/foodvisor/) and [Foodvisor API launch announcement](https://www.linkedin.com/posts/foodvisor_breaking-news-foodvisor-launches-api-activity-7120421955962318848-aOPJ)
- [Nutrola — Foodvisor Free vs Premium](https://nutrola.app/en/blog/foodvisor-free-vs-premium-what-do-you-actually-get) and [Reddit r/diet — Foodvisor thread](https://www.reddit.com/r/diet/comments/1bsb8eb/foodvisor/) (workout quality, pricing context)
