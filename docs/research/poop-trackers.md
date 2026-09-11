# Poop Tracker Apps — Category Feature Analysis

**Category**: Bowel-movement / stool trackers ("poop trackers"), overlapping with IBS & gut-health food-symptom diaries. The Bristol Stool Scale (Lewis & Heaton, Bristol Royal Infirmary, 1997) is the category's shared clinical backbone.
**Apps covered**: Poo Log (classic iOS, discontinued), PoopLog (classic Android, discontinued), Bowelle, Poo Keeper, Poop Map (the 1M+-install Android rep), plop, Happy Poop, PoopCheck (AI), plus brief coverage of Cara Care, Poop Tracker – Toilet Log (Appstronaut), PCal, UNCHIKUN, Happy Poo.
**Typical pricing**: Free + ads is the norm; monetization via subscription (plop ~$5/mo, Poop Map sub) or one-time lifetime unlock (PCal $7.99); the classic era sold $0.99 upfront. Paid apps without IAP are nearly extinct in this category.
**AI usage**: Emerging and mostly shallow. Two real AI stories: (1) plop — statistical correlation/"trigger detection" engine that runs fully on-device; (2) photo-based AI stool classification (PoopCheck "AI scans using the Bristol Stool Scale", Happy Poop's "Zeps" AI assistant, Poopie's AI photo rating) — cloud or undisclosed processing, no published accuracy numbers, real user complaints about misclassification. Cara Care is the only CE-certified medical-device product in the space.
**Local & privacy posture**: Genuinely split. Health-first apps advertise on-device storage (Bowelle: "stored on your device and not on any servers without your explicit consent"; plop: local-first, no third-party tracking SDK, optional encrypted cloud; Happy Poo: local-only storage; Poo Keeper: on-device with photo concealment). Novelty/social apps (Poop Map, Poopie) are cloud accounts that collect location and personal info. A large share of listings claim "no data shared with third parties" in Play data-safety labels.

## Category overview

Poop trackers exist at the intersection of a medical instruction and a taboo. Gastroenterologists routinely ask patients to "keep a bowel diary" using the Bristol Stool Scale — 7 types from Type 1 (separate hard lumps, constipation) to Type 7 (entirely liquid, diarrhea), with 3–4 ideal. The scale is part of the IBS diagnostic workup (Rome criteria subtyping) and is used to evaluate laxatives, probiotics, and transit-time effects of medication. The category was effectively created in 2010 by two apps born the same way — a doctor telling a patient to track stool form on paper:

- **Poo Log** (iOS, AvatarLabs, 2010, $0.99) — commercialized the idea as a tie-in to the book *"What's Your Poo Telling You?"* (Josh Richman & Anish Sheth, MD), blending "bathroom humor and legitimate medical information."
- **PoopLog** (Android, 2010) — built by a developer whose doctor asked him to track stools on the Bristol scale; he went from paper to spreadsheet to an app.

Typical users: (1) IBS/IBD/Celiac patients building a diary for a gastroenterologist appointment — the single most-cited use case in Reddit threads (r/ibs, r/IBD, r/CrohnsDisease, r/UlcerativeColitis); (2) people investigating food intolerances, often overlapping with low-FODMAP dieting (Monash University's FODMAP app has its own diary and is the community's gold standard); (3) novelty/social trackers (Poop Map's 1M+ installs prove the "numbers-geek gamification" audience overlaps strongly with WLO's target user). For weight-loss apps the connection is intrinsic: diet changes → stool changes, and fiber/hydration are the two levers both categories share.

The category's core tension: the people who need it most (chronic GI patients) demand medical rigor and doctor-share, while the biggest installs come from novelty/social angles. Almost no app serves both well.

## App-by-app analysis

### Poo Log (classic, iOS — discontinued)
- **Platforms**: iPhone/iPod touch/iPad (App ID 323438754); separate "Poo Log HD" for iPad (ID 386460777). No longer on the App Store — 2026 iTunes API searches (US, GB) return no match; last successful archive capture of its listing is 2015–2017. A user review on a successor app confirms: "It was discontinued and when I upgraded to iOS 26, it no longer worked."
- **Pricing**: $0.99 (upfront).
- **Key features** (from archived App Store listing, v1.1, Sept 2010, by AvatarLabs): a **Poo Timer** (session stopwatch), a **Poo Log** journal, "Graph Your Poos" statistics, and "Poo Pastimes" trivia — content licensed from the book *What's Your Poo Telling You?* Version 1.1 added the ability to backfill date/time per entry. Rated 12+ for crude humor.
- **Strengths**: Category creator; proved that humor + medical framing (the book's humor-meets-gastroenterology tone) could sell a stool diary; its "graph your poos" and timer features are still the core of every modern app.
- **Weaknesses**: Dead — a cautionary tale for a category where users build multi-year longitudinal data: when the app died, users' histories died with it (and 32-bit/64-bit breakage stranded remaining users). No Bristol education depth, no food correlation, no export (email-your-log era).
- **Ratings**: 222 ratings across versions at time of archival (too few to display an average for the current version).

### PoopLog (classic, Android — discontinued)
- **Platforms**: Android only ("When asked about iOS, the answer is simply: 'No.'"). Listed as "Currently Unavailable" on Google Play as of May 2024; final release v2.52 (Oct 26, 2016).
- **Pricing**: Free.
- **Key features**: Bristol Stool Scale logging with **photo, location, and note** per entry plus **20+ options** (color, blood/mucus, urgency, pain, medication, nausea, fever, cramps, even corn content); pie/bar/scatter charts, calendar and list history; a **3-step report wizard** exporting plain-text, HTML (with embedded photos), or CSV; photos saved to `/PoopLog/Photos/` and **hidden from the device gallery by default**; optional passkey lock; opt-in anonymous analytics; explicit promise that location/entry data never transmitted unless the user shares.
- **Strengths**: The most complete "patient diary" of the classic era — photo capture, doctor-report wizard, and privacy posture (local-first, gallery-hiding, passcode) a decade before "privacy-first" became marketing. Origin story is exactly WLO's target workflow: doctor instructs → Bristol scale → digital diary.
- **Weaknesses**: Abandoned (major overhaul took 3+ years, then maintenance stopped); utilitarian UI; single-platform.

### Bowelle — "The IBS tracker" (iOS)
- **Platforms**: iPhone only. **Alive and current**: v1.26.1 released 2026-08-27. Developer: Bowelle AB (Sweden).
- **Pricing**: Free (no IAP surfaced on the US listing).
- **Ratings**: 4.74★ (2,240 US ratings).
- **Key features**: Marketed as a *fast food & symptom diary for IBS*. Tracks **how you feel, food, stress levels, bowel movements, water intake, and custom user-defined values**; charts to "discover patterns"; configurable daily averages (14/30/60 days); filterable history; **email export / doctor reports**; **Apple Health sync** (sleep, steps, water, weight); save commonly used foods for faster entry; custom categories (users track headaches, temperature, etc.).
- **Privacy**: "Personal data is stored on your device and not on any servers without your explicit consent."
- **Strengths**: Best-in-class *logging speed* reputation ("clean and well-designed", "fast for daily logging" — user reviews); the custom-fields system lets users extend it far beyond poop; water intake and stress are first-class loggable dimensions; community favorite on r/CrohnsDisease ("not perfect, but it does what I need"). A few weeks of charts was enough for one reviewer to identify intolerant foods.
- **Weaknesses**: iOS-only; single-user local data (no cloud backup story beyond Health sync); the marketing site doesn't even name the Bristol scale (bowel entries are more free-form feeling/type); no photo logging, no AI, no correlation engine beyond eyeballing charts; no Android means no play in the largest market.

### Poo Keeper ◎ Log bowels & IBS (iOS + Android)
- **Platforms**: iOS and Google Play. Developer: Just2us (Singapore). **Alive**: iOS v2.1 released 2026-07-25.
- **Pricing**: Free.
- **Ratings**: iOS 4.54★ (665 US ratings); 13 languages; launched **2014**, claims **300,000+ downloads**.
- **Key features**: The category's definitive **photo-first** workflow — "use the toilet, snap a photo using the built-in **auto timer**" (hands-free rear-camera capture with countdown), log "in under 10 seconds"; **7 stool varieties** (Bristol) plus **speed and amount**; **reminders when you're "long overdue"**; **PDF/HTML export for doctor visits**; a **last-7-days widget**; and a privacy feature to **conceal photos** when showing the app to others.
- **Strengths**: Photo-capture with a self-timer is a genuinely clever input-minimization technique nobody else does well; the overdue reminder reframes regularity as a nudge; veteran app with 12 years of trust; export to both PDF and HTML (HTML carries embedded photos — inherited from the classic PoopLog idea).
- **Weaknesses**: Small team, modest ratings (4.5★ band — the lowest of the health-first apps surveyed); no food/diet correlation, no stats depth beyond the 7-day widget; photo management is the core rather than an aid to classification.

### Poop Map — Pin and Track (Android + iOS) — the large-install Android rep
- **Platforms**: Android (1M+ installs) + iOS. Developer: Poop OÜ (Tallinn, Estonia). **Alive**: Play update Jan 14, 2026; iOS v5.3.0 Aug 15, 2026.
- **Pricing**: Free with ads and in-app purchases (subscription; reviewers ask for a one-time purchase).
- **Ratings**: Play 4.8★ (~16.4K reviews), 1M+ downloads; iOS 4.77★ with **63,748 ratings** — by far the biggest install base of anything surveyed.
- **Key features**: **Not a health app** — category Entertainment, rated Everyone. One-tap "Drop the poop" pins your GPS location; builds a personal map/"conquests"; **leagues and leaderboards** with friends; follower feed with likes; poop **statistics and yearly calendar totals**; public/private sharing toggle.
- **Data safety**: **Collects location, personal info, and 4 other data types**; shares app info/performance with third parties; encrypted in transit; deletion on request.
- **Strengths**: Demonstrates the power of one-tap logging + gamification (leagues, streak-style yearly totals, social sharing) — it turned the least-discussable bodily function into a social game with a million installs. For WLO the lesson is about engagement mechanics, not features.
- **Weaknesses**: Zero health value (no Bristol scale, no symptoms, no diary); cloud-first with location collection is the exact opposite of WLO's posture; reviewer gripes: GPS inaccuracy, subscription resentment.

### plop — poop tracker & analyzer (iOS + Android)
- **Platforms**: iOS + Android with optional cross-device sync. Developer: The Plop Company LLC (Sacramento, CA). **Alive**: Play updated Sep 3, 2026; iOS v3.1.0 Sep 2, 2026.
- **Pricing**: Free core logging; premium subscription (~$5/mo per reviews) unlocks advanced analytics, correlation insights, unlimited history, and **PDF/CSV export**. Contains ads on the free tier.
- **Ratings**: Play 4.4★ (583 reviews, 10K+ installs); iOS 4.65★ (1,481 ratings).
- **Key features**: The most technically ambitious health-first app in the category, and the closest existing thing to WLO's philosophy:
  - **Logging <10s**: Bristol type (1–7) via illustrations + color, consistency, frequency, duration, urgency, abdominal pain (0–10), bloating/gas.
  - **Factor logging**: meals/foods, **FODMAP categories**, medications/supplements, hydration, sleep, stress, mood, energy, custom factors, optional photos with zoom.
  - **On-device correlation engine**: "statistical pattern detection" that "flags which items consistently precede your worst digestive days"; **flare-up early warnings**; unusual-day flags vs. your personal transit timeline; **baseline alerts** (e.g., "frequency down 73% vs. your personal 1.6/day average"); explicitly personal-baseline-based rather than population averages. No named ML model and **no clinical validation claims** — it's honest statistics.
  - **10+ interactive charts**; caregiver mode (log for children/parents); logging of diagnosed GI conditions including **gluten-reaction analysis for celiac**; quick-log widget; health-metric sync (Apple Health on roadmap).
  - **Privacy-first**: local storage, works fully offline, reports generated locally, "no active third-party tracking SDK" in core build, optional encrypted cloud backup that is wipeable, full in-app data deletion. **No name/email/location required.**
- **Strengths**: The category's clearest articulation of "correlate stool with food/lifestyle and tell me my triggers"; on-device engine is the right architecture and a real differentiator; personal baselines ("vs. your 1.6/day") is exactly the numbers-geek framing; PDF/CSV doctor-share.
- **Weaknesses**: Monetization resentment is loud — "It shouldn't be locked behind a subscription, let alone at the cost of a streaming service"; free tier's ads in a health app erode its privacy-first branding; correlation engine needs **2–3 weeks of data before patterns appear** (cold-start problem it handles only with messaging); export paywalled annoys the doctor-visit use case.

### Happy Poop: Toilet Journal Log (iOS + Android)
- **Platforms**: iOS + Android (Casa do Zéps / Gilson Nascimento). **Alive**: iOS v3.16.8, 2026-07-10.
- **Pricing**: Free with IAP; optional cloud backup is opt-in.
- **Ratings**: iOS 4.85★ (2,622 ratings) — highest-rated health-first app surveyed; Play 4.8★.
- **Key features**: Bristol scale + duration, quantity, color, mood; notes/photos; **pee and menstrual cycle** logging alongside; **custom habit tracking** (hydration, sleep, exercise, diet) to "see how routines affect gut health"; charts and shareable history for doctor visits; **AI assistant mascot "Zeps"** that reviews logged info and trends (explicitly disclaimed as not medical advice); optional social features; **family profiles including kids and pets**; biometric/PIN lock; **discreet "Happy Diary" icon mode**; **Apple Watch app**; **data import from other poop-tracking apps**.
- **Strengths**: Broadest body-context integration (pee, cycle, habits) — treats the gut as one signal among many, closest to WLO's whole-body vision; tasteful tone ("Your gut has a story to tell — Happy Poop helps you listen"); the discreet icon mode and biometric lock are the category's best taboo-UX handling; data import acknowledges lock-in fears (rare!).
- **Weaknesses**: Feature breadth risks a cluttered experience vs. Bowelle's speed focus; AI assistant is rule-based trend narration, not real analysis; social features sit oddly in a health journal.

### PoopCheck: AI Stool Tracker (Android)
- **Platforms**: Android (SoftAllThings LLC). **Alive**: updated Sep 3, 2026. 5K+ installs, 4.1★ (67 reviews).
- **Pricing**: Free with IAP.
- **Key features**: "Snap a photo or log manually → **instant AI scans** with insights"; "Our AI scans your poop using the **Bristol Stool Scale** and advanced pattern recognition" (consistency, color, shape); daily **gut score**; AI chat assistant "SOFTie"; custom reports; symptom tracking (IBS, FODMAP, constipation, bloating); user community. Data-safety label claims no data collected/shared.
- **Strengths**: The purest expression of WLO's "photo + recognition over manual input" principle; the daily gut score is a good single-number gamification device.
- **Weaknesses**: **The cautionary tale for AI stool classification**: users report the AI **misidentifies stool types with no way to manually correct the statistics**; a reviewer notes AI "cannot diagnose just based on a photo"; the developer acknowledged AI errors and shipped a fix. Whether processing is on-device or cloud is **not disclosed** — a trust gap for an intimate photo category. Small, low-rated, unvalidated.

### Brief notes on other notable players
- **Cara Care** (DE): pivoted from a well-liked free consumer food-symptom diary into a **CE-certified IBS therapy app (medical device)**, "free on prescription" via German statutory insurers; therapy content is **German-only**. Features: food & symptom tracking, gut-friendly recipes, gut-directed hypnosis audio, chat support; claims 70.2% of users achieve clinically relevant symptom reduction. Reddit's standout memory of it: a **shareable link so your doctor can view your log directly**.
- **Poop Tracker – Toilet Log / "Gut Health"** (Appstronaut Studios, iOS+Android, Play 4.6★): Bristol type, color, photos, urgency, size, bloody stool, pain; **medication tracking with effect analysis on bowel habits**; calendar/list/graph views; **CSV export AND import**; marketed as privacy-focused; "Perfect for managing IBS, Crohn's, and Colitis."
- **PCal – Poop Tracker, Calendar** (iOS, 4.51★, 1,275): calendar-first log; Bristol + color + urgency + size + **bloody stool flag**; reminders; notes/photos; **Excel export**; **$7.99 lifetime** premium (a pricing model users explicitly ask plop for). Last updated June 2024 — going stale.
- **UNCHIKUN** (iOS, Japan, TsuzuKit, v2.14.8 Sep 2026): gamification benchmark — one-tap logging, **37+ characters that react to your count**, streaks, widgets, and "Poop Buddies" friend sharing.
- **Happy Poo – Daily Log** (iOS): 7 Bristol types + 5 colors, one-touch logging, motivational feedback, **local-only storage**, private share links, a 100-item poop collection game, and a health-education library — education + gamification + local-first in one tiny app.
- **Monash University FODMAP Diet** (community gold standard, not a poop tracker per se): its built-in diary logs **food + Bristol rating + symptoms together** and encodes FODMAP stacking — the benchmark WLO's meal-plan ↔ poop correlation will be measured against by IBS users.

## Feature inventory

### Bristol Stool Scale implementation & education
- **Universal**: every health-first app uses the 7-type scale; it is the category's lingua franca and its main medical credibility anchor (plop's site calls it "an exceptionally reliable clinical proxy" for transit time).
- **Beyond the type**: PCal adds color/urgency/size/bloody-stool; PoopLog (classic) added 20+ attributes incl. blood/mucus; plop adds color, consistency, duration, urgency, pain 0–10; Poo Keeper adds speed and amount; Happy Poo adds 5 colors; Happy Poop adds duration/quantity/color/mood.
- **Education**: weakest part of the category. Happy Poo has an explicit health-education library; Poo Log's trivia was book content; most apps just show 7 illustrations with one-line descriptors and stop. None surveyed explain what a *change* in your pattern means, when to see a doctor (red-flag symptoms like blood → clinical referral), or connect types to fiber/water intake educationally. Wikipedia-level knowledge (transit time, Rome criteria subtyping) is untapped in-app.

### Logging UX (speed, one-tap logging, discreet mode)
- Speed is the deciding factor: Poo Keeper "<10 seconds"; plop "<10s per entry" via illustrations; UNCHIKUN is pure one-tap; Poop Map is literally one button; Bowelle's brand is "fast food & symptom diary" with saved common foods; r/WholeGutHealing's top recommendation for poop tracking is simply "when you just want to track BMs quickly with no fuss."
- Two under-used accelerants: **Poo Keeper's hands-free auto-timer photo** (captures evidence without a fiddly in-the-moment UI) and **backdating** (Poo Log v1.1's headline feature; Tract offers 7-day backfill).
- Discreet mode: Happy Poop's **"Happy Diary" disguised app icon** + biometric/PIN lock (also PoopLog classic's passkey) + Poo Keeper's **photo concealment toggle** for showing the screen to others. Poopie/Poop Map lean the opposite way — loud social sharing.

### Photo logging & AI stool recognition (who does it, on-device vs cloud, accuracy claims)
- **Photo logging**: Poo Keeper (auto-timer, photo concealment), PoopLog classic (embedded in HTML reports, gallery-hidden), Appstronaut Poop Tracker, PCal, Happy Poop, plop (optional, with zoom). Photo logging is mainstream; **AI recognition is not**.
- **AI recognition**: PoopCheck is the only app whose core is AI photo classification ("Bristol Stool Scale + advanced pattern recognition"); processing on-device vs cloud **undisclosed**; **no accuracy claims**; users report misclassification with no manual correction path. Poopie (iOS) advertises "AI photo rating." Happy Poop's "Zeps" is an AI *assistant* over logged data, not a photo classifier. UNCHIKUN markets "AI Health" but is essentially gamification.
- **The honest AI**: plop deliberately does **not** claim image AI — its engine is on-device statistical correlation over structured entries. No app in the category publishes accuracy numbers, references a validated dataset, or has any clinical validation of its AI. This is an open field.

### Correlation with diet & trigger detection (FODMAP, fiber, hydration links)
- **Dedicated food+symptom diaries** (Bowelle, Cara Care, mySymptoms, Monash) do correlation best, but via generic "look at charts" — the user finds patterns manually. Reddit consensus: this combo (Bristol + food + symptoms in one timeline) is *the* reason to use an app at all.
- **plop** is the only mainstream poop tracker with an actual **automated trigger-detection engine**: correlates foods, **FODMAP categories**, meds, stress, sleep with flares; warns before flare-ups; compares you against your own baseline. Requires 2–3 weeks of data. Notably, plop's IBS positioning (explicit low-FODMAP support) shows the demand, and Happy Poop's custom habit factors (hydration, exercise, diet) show the same need met more crudely.
- **Hydration**: Bowelle logs water as a first-class field with Apple Health sync; Happy Poop via habit tracking; plop as a factor. **Fiber**: essentially nobody models fiber explicitly — it's left inside generic "food" entries. A gap.
- **No app correlates poop with a *planned* meal plan** (only with ad-hoc logged meals) — because none of them has meal planning. This is WLO's structural advantage.

### Statistics & visualization (regularity score, calendar heatmap, trends)
- Poop Map: yearly calendar totals (heatmap-adjacent) and stats — proof that simple longitudinal visuals delight even novelty users.
- plop: 10+ interactive charts, trend analysis, **personal baseline vs. population averages**, unusual-day detection, baseline-deviation alerts — the deepest stats stack.
- Bowelle: configurable daily averages (14/30/60 days), filterable history, pattern charts.
- Poo Keeper: last-7-days widget (regularity at a glance); PCal: graphs + exportable calendar.
- Gamified regularity: Poop Map leagues; UNCHIKUN streaks/characters; Happy Poo collection game. **A "regularity score" as a single number exists only in PoopCheck (daily gut score)** — and it's weak. The numbers-geek opportunity (streaks, consistency score, transit-time estimates, WLO-style forecasts) is barely claimed.

### Doctor-share / export
- PDF reports: Poo Keeper (PDF + **HTML with photos**), plop (PDF/CSV, generated locally), Happy Poop (shareable history), PoopCheck (custom reports), Poop Tracker Appstronaut (**CSV export AND import**), PCal (**Excel**), classic PoopLog (3-step report wizard: text/HTML/CSV). Cara Care's remembered killer feature: a **shareable doctor link** (live view rather than a static file).
- Common thread: reports are the *paywall* in plop — resented, since the doctor visit is the whole point for GI patients. The category has converged on "structured log + photos + Bristol types in a PDF" but nobody does a clinician-formatted symptom diary (e.g., Rome IV-style questionnaire output).

### Design & handling of taboo UX (tastefulness, tone, naming)
- The spectrum runs from clinical to juvenile: **medical-reassuring** (plop: pastel illustrations, "your gut has a story" tone; Bowelle: doesn't say "poop" at all — "bowel movements"; Cara Care: pure clinic); **playful-but-tasteful** (Happy Poop's mascot Zeps, Happy Poo's motivational feedback, UNCHIKUN's reacting characters); **proudly crude** (Poo Log's 12+ crude-humor branding, Poopie's toilet-rating social feed).
- Taboo-management features are real and valued: disguised app icons (Happy Diary), biometric locks, photo concealment, gallery-hiding (classic PoopLog), private/public toggles (Poop Map). The apps that normalize ("everyone poops, after all" — Poo Log, 2010) lower the shame barrier to logging, which directly improves data quality.
- Naming matters: the survivors are named after the *function* (Poo Keeper, Poop Calendar) or a neutral euphemism (Bowelle, plop's diary framing); joke names (PooPals, Poopsy) correlate with shallow health value.

## Strengths & differentiators across the category
1. **Bristol-first structure**: a shared medical vocabulary that instantly makes logs doctor-credible — and that WLO can adopt wholesale at zero design cost.
2. **Sub-10-second logging** as an explicit, engineered goal (auto-timer photos, saved foods, one-tap, backfill).
3. **plop's on-device personal-baseline correlation engine** — the category's most advanced and most privacy-aligned analysis, proving on-device "AI" (statistics) is feasible and marketable.
4. **Doctor-ready export** (PDF/HTML-with-photos/CSV/Excel) as a universal, differentiating feature for the chronic-patient segment.
5. **Gamification scales installs** (Poop Map: 1M+; UNCHIKUN; Happy Poo's collection game) — the audience for playful quantified-pooping is large and overlaps WLO's numbers-geek target.
6. **Privacy as brand**: multiple apps compete on local-first, no-SDK, disguised icons, photo concealment — an expectations baseline WLO's architecture already exceeds.
7. **Body-context breadth**: pairing poop with water (Bowelle), stress, meds (Appstronaut), pee/cycle (Happy Poop), custom fields.

## Weaknesses & common complaints
1. **Abandonment & data death**: both classics are dead (Poo Log broken by iOS updates; PoopLog delisted 2024) — users lost years of history. Community also flags stale apps (PCal last touched 2024).
2. **Subscription resentment in a health context**: plop's ~$5/mo + ads draw the loudest anger ("cost of a streaming service"); users ask for lifetime unlocks; paywalled export blocks the primary use case.
3. **AI overpromising**: PoopCheck misclassifies with no manual correction; no app discloses processing location or accuracy; Happy Poop's AI is trend narration dressed as AI.
4. **Cold-start problem**: correlations need 2–3 weeks of logging; category-wide 60% gut-app abandonment (plop's own marketing stat) means most users never reach the payoff. Nothing onboarding-wise fixes this.
5. **No real diet linkage**: poop trackers log meals as text at best; nobody links stool outcomes to planned meals, recipes, fiber targets, or shopping lists. FODMAP support without a Monash-grade food database is hand-wavy.
6. **Shallow stats for geeks**: no transit-time estimation, no forecast ("at this fiber intake, expect…"), no regularity score beyond one weak attempt, no custom dashboards.
7. **Cross-platform gaps**: Bowelle iOS-only; classic PoopLog Android-only; sync (where it exists) is bolted-on cloud (plop optional, Happy Poop optional) rather than local-first sync.
8. **Trust/verification gap**: medical disclaimers everywhere ("not FDA-certified", "not medical advice") but zero validation studies — an opening for an app that cites actual evidence (the Bristol scale's own literature) responsibly.

## What WLO should learn
1. **Make poop a first-class outcome of the meal plan, not a separate tracker.** Every app in this category correlates stool with *ad-hoc* food logs; none has a *planned* diet. WLO already has meal plans, nutrient estimation, and shopping lists — so the differentiator is closed-loop: "You switched to the high-fiber plan 10 days ago; Bristol types moved 1–2 → 4, your stated goal." Practically: reuse the meal plan's nutrient estimates (fiber grams, FODMAP-ish tags, water from logged drinks) as the X-axis against Bristol types on the Y-axis, and let the poop tracker inherit meals automatically — zero extra manual entry, which no competitor can do.
2. **Steal plop's engine, fix its cold start.** An on-device correlation engine (personal baseline, deviation alerts, "items consistently preceding your worst days") is validated demand and matches WLO's local-first AI principle exactly. But plop needs 2–3 weeks of data before saying anything and loses 60% of users by then. WLO should ship *day-one* value from a single entry (type → instant education + fiber/water nudge: "Type 1? Today's plan adds 12g fiber via oats and kiwi — want me to swap it in?") and gate correlations behind progress feedback ("2 weeks of data unlocks your trigger report") so the wait feels like leveling up, not a dead app.
3. **Photo logging yes, AI classification carefully.** Poo Keeper's auto-timer photo is the best input trick in the category; WLO's silhouette-photo pipeline could share infrastructure. For AI stool recognition, PoopCheck's failure mode (misclassification with no correction) is the lesson: any WLO photo classifier must (a) run **on-device** (WLO's architecture advantage — and a trust differentiator nobody in the category claims for photos), (b) **always allow one-tap manual override** that both fixes the stat and can fine-tune the model locally, and (c) show confidence and Bristol illustration side-by-side. Position it as "photo *assists* logging" (type pre-selected), never as diagnosis — mirroring the disclaimers every survivor carries.
4. **Nail the taboo UX from day one.** Discreet app icon/alias option (Happy Poop's "Happy Diary"), biometric lock, photo concealment when screen-sharing (Poo Keeper), neutral naming ("Bowel" not "Poop" in the primary nav), and a tone that's warm-playful not crude (Poo Log's 12+ rating is the anti-pattern). Add gallery-hiding for stool photos (classic PoopLog) so captures never surface in family photo streams — table stakes for a weight-loss app whose users share progress screenshots.
5. **Regularity nudges + a real regularity score.** Poo Keeper's "you're overdue" reminder and PoopCheck's daily gut score are the only attempts; both are primitive. WLO's numbers-geek audience deserves: a 0–100 regularity score (frequency variance + Bristol-type stability), streaks, a calendar heatmap (Poop Map proves users love the yearly view), and **forecasting** — WLO already forecasts weight; the same machinery can forecast "likely Type 1 tomorrow based on yesterday's low fiber + low water" and prescribe the fix via the meal plan.
6. **Doctor-share must be free, photo-bearing, and structured.** Export as PDF with embedded photos (Poo Keeper/PoopLog lineage), CSV for geeks (Appstronaut goes further with import — do the same for backup/restore, which doubles as local-first data ownership), and avoid plop's mistake of paywalling the report. Consider Cara Care's remembered killer feature — a shareable *live* view — implemented locally-first (e.g., a timed, password-protected export page generated on-device).
7. **Portability is existential in this category.** Both category founders are dead and took users' multi-year histories with them; Happy Poop is the only app offering *import* from competitors. WLO should treat full export/import (and open formats) as a trust cornerstone — the poop log is often the user's longest continuous health time series.
8. **Pair the log with education tied to weight loss.** Nobody teaches *why*: fiber→transit time→Bristol types, hydration's role, red-flag symptoms (blood, persistent Type 6–7) that warrant a doctor. WLO's diet plans change gut behavior by design; a small education layer ("what changed and why it's expected") turns scary transitions into reassured ones and reduces churn — while red-flag detection ("4+ days Type 1, or blood logged → see a GP") is the responsible-care feature none of these apps ships.

## Sources
- [Bowelle — official site](https://bowelle.com)
- [Bowelle – The IBS tracker — iTunes Search API listing](https://itunes.apple.com/search?term=bowelle&country=us&entity=software)
- [Poo Keeper — official page (Just2us)](https://www.just2us.com/pookeeper) and [Just2us apps](https://www.just2us.com)
- [Poo Keeper — iTunes Search API listing](https://itunes.apple.com/search?term=poo+keeper&country=us&entity=software)
- [Poop Map — official site](https://poopmap.net)
- [Poop Map — Google Play listing](https://play.google.com/store/apps/details?id=net.poopmap)
- [plop — official site](https://plopdiary.com)
- [plop — Google Play listing](https://play.google.com/store/apps/details?id=com.notiis.plop)
- [Happy Poop: Toilet Journal Log — iTunes Search API listing](https://itunes.apple.com/search?term=happy+poop&country=us&entity=software)
- [PoopCheck: AI Stool Tracker — Google Play listing](https://play.google.com/store/apps/details?id=com.softallthings.poopcheckapp)
- [Poop Tracker – Toilet Log (Appstronaut "Gut Health") — official site](https://poop-tracker.com)
- [Poo Log — archived App Store listing, Sept 2010 (Wayback Machine, ID 323438754)](https://web.archive.org/web/20100918042726/http://itunes.apple.com/us/app/poo-log/id323438754) and [archived listing captures index 2010–2022](https://web.archive.org/cdx/search/cdx?url=itunes.apple.com/us/app/poo-log&matchType=prefix&limit=20&output=json)
- [PoopLog (classic Android) — official site (origin story, features, status)](https://www.pooplog.app)
- [Cara Care — official site](https://cara.care/en)
- [Bristol stool scale — Wikipedia](https://en.wikipedia.org/wiki/Bristol_stool_scale)
- [iTunes Search API results for "poop tracker" / "poo log" (US & GB, Sept 2026) — current competitive set and delisting evidence](https://itunes.apple.com/search?term=poop+tracker&country=us&entity=software)
- [Google Play search: poop tracker apps (Sept 2026)](https://play.google.com/store/search?q=poop+tracker&c=apps)
- [Reddit r/ibs "Good poop tracker?", r/IBD "Best App for poop monitoring?", r/WholeGutHealing "Best apps for managing IBS", r/CrohnsDisease & r/UlcerativeColitis threads — community recommendations via web search](https://www.reddit.com/r/ibs/comments/13nedu1/good_poop_tracker/)
- [Bowel Movement & Poo Tracker — App Store (review citing Poo Log's discontinuation)](https://apps.apple.com/us/app/bowel-movement-poo-tracker/id6504195613)
