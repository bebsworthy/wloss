# Hevy — Feature Analysis

**Category** | Gym workout tracker / strength-training logger (logging-first, social layer optional)
---|---
**Platforms** | iOS, Android, Apple Watch (incl. custom watch faces), Wear OS (full standalone watch app since April 2024), web app at hevy.com (routine planning + analytics)
**Pricing model** | Freemium. Free: unlimited workout logging, max 4 routines, max 7 custom exercises, stats history capped at 3 months. Pro: $2.99/month, $23.99/year, or $74.99 lifetime. No ads on free tier.
**AI usage** | No LLM in the core product. "Hevy Trainer" (Pro) is explicitly "an adaptive strength programming system based on a sophisticated algorithm" informed by exercise science, not generative AI. HevyGPT is a separate ChatGPT custom-GPT integration that generates programs from prompts and imports them into the account.
**Local & privacy posture** | Cloud-synced account model (not local-first). Reviewers note Hevy claims it does not use cookies for personalized advertising; free tier is permanent with no trial deadline. Data export (CSV) and a public developer API exist, which reduce lock-in. Progress photos are stored privately even on public profiles.

## Overview

Hevy, built by Hevy Studios S.L. (Barcelona, launched 2019), is one of the two dominant "log every set" gym apps of 2025–2026, alongside Strong and JEFIT. It markets itself as being for "16+ million athletes" with a 4.9 average rating across ~590,000 store ratings (~335k Apple, ~259k Google). Its position in the market is the friction-free logger: pre-filled sets from your previous session, an automatic rest timer, native supersets, and a watch app — with social features (feeds, followers, leaderboards, shareable workout images) that you can ignore entirely.

The product family has three legs: (1) the consumer tracker (this analysis), (2) Hevy Coach, a SaaS platform for personal trainers to assign programs and monitor clients, and (3) a public API that lets third parties read/write workout data — which is also how the HevyGPT ChatGPT integration works. The web app is a first-class citizen: routine building and progress analysis are explicitly marketed as desktop activities.

Hevy deliberately positions its programming engine as algorithmic rather than "AI": Hevy Trainer generates a full periodized program from an onboarding questionnaire and then adapts weights to performance with a transparent rule (hit the top of the rep range on every set before load increases). The generative-AI experience is delegated to HevyGPT, a ChatGPT integration that writes programs from freeform prompts and saves them into Hevy as editable routines.

## Feature inventory

### Exercise planning & program templates (routine builder, AI program generation, periodization)

- **Routine Planner**: build, duplicate, and edit saved routines on phone or web; routines sync to the watch ("Sync App Routines"). You can save other athletes' routines from the feed into your library, and share whole folders of routines.
- **Hevy Trainer (Pro)**: onboarding asks experience level, goal, available equipment, weekly frequency, session length, and focus muscles; later settings add rest-timer preference, cardio preference, injuries, and program style — explicit choice between "6-week blocks" (periodization), continuous training, or weekly exercise variation. Output: full program with exercises, sets, target rep ranges, and automatic rest times; a "recommended starting weight if you've logged the exercise at least once." Each session shows an overview (exercise count, estimated duration).
- **Trainer adaptation**: "Automatic weight adjustments based on your performance," plateau reminders, how-to instructions and demo animations, and periodic progress reports (consistency, time spent, volume load, sets, per-exercise progress, muscle distribution, body weight).
- **Progression rule is transparent and mechanical**: you must hit the upper end of the target rep range on all sets before weight increases. Reviewers call this a "mechanical ceiling" versus real coaching, but it is fully predictable for the user.
- **HevyGPT**: freeform ChatGPT prompts ("existing injuries, exercises you like or dislike, available equipment, goals, preferred workout duration, weekly training frequency, warm-up sets, and more") generate programs that import directly as editable routines in a dedicated folder. With the integration connected, ChatGPT can also review your training history for load/progression advice. Limitation: free Hevy accounts are capped at 4 routines, so larger imports fail; the flow doesn't work inside the Android ChatGPT app (must use ChatGPT web).

### Workout logging UX (CRITICAL: set entry, rest timer, plate calculator, supersets, watch support)

Hevy's core loop is best-in-class and the reference implementation for this genre:

- **Set entry**: set-by-set logging with weight and reps **pre-filled from the previous session** — a repeat workout is essentially one tap per set. Warm-up, drop, and failure set types are first-class; per-set RPE is supported; free-text notes per exercise.
- **Rest timer**: triggered automatically when a set is marked complete; range 5 s–5 min; in-rest **±15 s adjustment buttons**; per-exercise override including "off" (used for supersets); app-wide default in Settings; notification fires at zero.
- **Plate calculator**: toggled on in workout settings; a calculator button appears "on the bottom left, just above the keyboard" when entering a load. Computes plates from target weight + **user-configurable plate inventory and custom bar weights** (Manage > Available Equipment), supports kg/lb and any barbell/EZ/short bar, and degrades gracefully: "Closest possible weight is 135kg."
- **Supersets/giant sets**: native grouping of exercises; the rest timer can be disabled per movement inside a superset.
- **Live PR banners**: the moment a completed set beats a personal best (1RM, heaviest weight for N reps, best single-set volume = weight × reps × sets, most reps, or longest duration e.g. plank), a banner is presented mid-workout; toggleable in preferences.
- **Bodyweight shortcut**: during a live session, three-dot menu on a bodyweight exercise > "Update Bodyweight" — your current bodyweight is stored into the Measurements tab.
- **Watch**: full standalone Apple Watch and Wear OS apps — log complete workouts, sync routines, work offline, and log heart rate on supported watches; custom Apple Watch faces. iOS Live Activity shows the running workout on the lock screen; Android home-screen widgets exist.
- **Conveniences**: start-empty-workout mode, warm-up set calculator, exercise swap mid-workout, Strong CSV import, data export.

### Exercise library (count, videos, instructions, muscle mapping)

- **400+ built-in exercises** with demonstrational **animations** (the homepage also advertises "High Quality Exercise Videos"; the in-app default is animated demos rather than filmed video), plus step-by-step setup/execution instructions for every movement.
- **Muscle mapping is two-level**: every exercise has a primary muscle target and secondary muscle targets; search filters by muscle and equipment. This mapping is what powers all per-muscle statistics.
- **Custom exercises**: create your own with image (or duplicate a library exercise and swap in a photo/video/GIF), equipment, primary/secondary muscles, and exercise type — weight & reps, bodyweight reps, or duration. Free tier caps at 7 custom exercises.
- Coverage spans barbell, machines, dumbbells, kettlebells, bands, suspension, bodyweight, plus a thin slice of cardio activities.

### Progress metrics & statistics (1RM e1RM, volume tonnage, muscle distribution heatmap, PR detection, charts)

- **1RM**: estimated one-rep max per exercise, surfaced during logging and in exercise history.
- **Muscle "heatmap"**: a body diagram on the Profile > Statistics screen highlights trained muscles in blue — "Last 7 Days Body Graph" (rolling window) and "Body Distribution" (training volume per muscle group as **total sets**, navigable calendar-week by calendar-week). Neither uses color intensity by volume; it's binary highlighting plus charts.
- **Sets per muscle group per week** (advanced statistics): set counts per muscle at week/month/year granularity, toggleable per muscle, preset ranges (last 30 days, 3 months, year, all time). Free tier sees only 3 months of history; Pro unlocks all time. **No custom date ranges** — presets only.
- **Monthly Report** (free): prior-month recap with workouts, time, volume load, sets, muscle distribution vs. the prior month, PR list, sets per muscle, most-logged exercises, and a workout-day calendar; shareable image.
- **Year in Review** (free, every December; requires ≥10 logged workouts and first workout before November): most productive month, most-trained body parts, most-logged exercises, PRs, biggest supporters (social), shareable highlights.
- **Exercise charts**: per-exercise advanced charts (e1RM, volume, etc.) and workout comparison of the same exercise across sessions.
- Everything renders as shareable images — distribution, graphs, PRs.

### AI adaptation (how it adjusts next workouts; recovery/fatigue modeling)

- **Hevy Trainer** adapts weights automatically between sessions based on whether you hit your target reps; the progression rule (all sets to top of rep range → add weight) is deterministic. It does **not** ingest sleep, HRV, resting heart rate, or readiness — reviewers flag this as the main gap versus recovery-driven rivals.
- There is **no muscle-recovery/fatigue percentage model** anywhere in Hevy (N/A — not offered). Muscle distribution stats tell you what you trained, not what has recovered.
- **HevyGPT** can review your training history via the API and suggest loads/progression, but this is a manual, chat-driven flow — not continuous in-app adaptation.

### Nutrition / weight integration

- **Body measurements**: body weight, body-fat %, and circumference sites (neck, shoulders, chest, waist, abdomen, hips, biceps, forearms, thighs, calves), each on its own graph; entries are editable and can carry a progress photo. Guidance is prescriptive: weigh daily or 4–5×/week, log to 0.1 kg/lb, track the weekly average, circumference every 2–3 weeks, one body-fat test a month.
- **Progress photos**: one photo per day, always private even on a public profile, side-by-side comparison of any two photos, shareable export; library tagged by date. Attachable to measurement entries.
- **No nutrition tracking** of any kind (N/A — not offered); food/calories are out of scope. Bodyweight links into lifting in one direction only: updating bodyweight scales relative lifts like pull-ups.

### Statistics, visualization & gamification

- Gamification is light and mostly **social**: followers, likes/comments, a discovery feed, a gym leaderboard, PR shareables, monthly/yearly recaps. There are no badges, streaks, XP, or achievements.
- For a numbers-geek the depth is decent but bounded: e1RM, volume load, set counts per muscle, weekly/monthly trends, PR history. No RPE-trend charts, no fatigue modeling, no custom dashboards, no custom date ranges.

### Design & UX / micro-interactions

- The design language is clean, card-based, dark-mode-first; the celebrated quality is **flow**, not decoration: pre-filled inputs, auto-starting timer, one-tap set completion, mid-workout PR banners, ±15 s rest tweaks, plate calculator a keystroke away. 4.9/5 across hundreds of thousands of ratings is largely a UX verdict.
- Shareable "receipt" images for workouts and stats are a deliberate viral loop and a micro-interaction highlight.

## Strengths & differentiators

1. **Lowest-friction logging in the category** — previous-session prefill, auto rest timer, native supersets/drop sets/failure sets, watch parity (iOS + Wear OS), offline-first on watch.
2. **Honest, cheap pricing**: permanent free tier with unlimited logging; Pro at $2.99/mo / $23.99/yr / **$74.99 lifetime** — roughly a quarter of Fitbod's price, with a lifetime exit ramp.
3. **Web app + public API + CSV export** — rare openness; enables HevyGPT, third-party dashboards, and reduces lock-in.
4. **Plate calculator with configurable inventory** (custom bars and plates) and graceful "closest possible weight" fallback.
5. **Hevy Trainer's transparency**: rules-based auto-progression users can predict and verify; optional periodization styles (6-week blocks vs. continuous vs. weekly variation) and injury inputs.
6. **Body-metric module** (weight, BF%, 9 circumference sites, private daily progress photos with side-by-side compare) that plugs into the lifting context via bodyweight-relative exercises.
7. Community done as an optional layer rather than a requirement.

## Weaknesses & user complaints (cite review sources)

- **3-month history paywall** on free — the most-cited grievance (r/Hevy "free vs pro" thread; RepReturn 2026 review). Users who logged for years on free can't see their own older data.
- **4-routine and 7-custom-exercise caps** on free; the cap also silently breaks HevyGPT imports with >4 unique workouts.
- **No recovery/biometric inputs** for Trainer (no sleep/HRV/RHR); SensAI's 2026 review (8.0/10) calls the rule-based progression a "mechanical ceiling."
- **No native Garmin sync** — recurring Garmin-forum complaints; users resort to Health Sync workarounds (Hevy → Health Connect → Health Sync → Garmin Connect).
- **Cardio is an afterthought** — logging is strength-centric; cardio activities are minimal entries with no pacing/HR analysis.
- **Confusing dual monthly SKUs** ($2.99 and $3.99 appear on the App Store — SensAI).
- No custom date ranges in statistics; heatmap is binary (highlighted/not) rather than volume-weighted.
- HevyGPT is a bolt-on: broken on the Android ChatGPT app, capped by free routine limits.

## What WLO should learn (5+ concrete, actionable takeaways)

1. **Copy the logging loop exactly**: prefill weight/reps from the user's last performance, auto-start a rest timer on set completion with ±15 s in-rest adjustment, per-exercise timer override, and one-tap set close. Every reviewer attributes Hevy's 4.9 rating to friction removal, not features. For WLO, the same loop must coexist with meal/weight logging — so invest in per-module "prefill from last time" everywhere.
2. **Plate calculator with user-editable gym inventory** (custom bars, plate set, kg/lb) plus the "closest possible weight" fallback is a cheap, high-delight differentiator — put it in WLO's exercise module from day one, placed directly above the keyboard where load is entered.
3. **Make adaptation rules visible**: WLO's AI-assist should do what Hevy Trainer does — deterministic, explainable progression ("hit top of rep range on all sets → +2.5 kg") with optional periodization styles — and *then* add what Hevy lacks: recovery/fatigue inputs. Explainability is a feature; both apps' only praised "AI" is the kind users can verify.
4. **Never paywall a user's own history.** Hevy's single biggest complaint is the 3-month graph cap on free. A local-first WLO gets this for free and should advertise it: "all data, forever, on your device" is both an architecture fact and a marketing wedge against Hevy/Fitbod.
5. **Stats surface area beats stats depth for retention**: Hevy's monthly report, year-in-review, and weekly muscle distribution are all *derived from the same small metric set* (sets × reps × weight + muscle tags) but create 12+ moments of delight a year. WLO can do the same across modules — a monthly "weight + training + meals" recap is something neither rival can build because neither has food or body data.
6. **Two-level muscle mapping (primary/secondary) is the keystone**: every heatmap, weekly-set chart, and Trainer decision flows from muscle tags chosen at exercise creation. Design WLO's exercise schema around this from the start (and let custom exercises carry it).
7. **Bodyweight is a bridge between modules**: Hevy's "Update Bodyweight from a bodyweight exercise" pattern is exactly the cross-module trick WLO should extend — today's weigh-in updates weight-adjusted lifts, calorie estimates, and the silhouette tracker simultaneously.
8. **Export/API as trust**: CSV export and a public API turned Hevy's cloud dependency into an ecosystem (HevyGPT). A local-first app should go further — plain-file export by default — and can honestly claim superiority on data ownership.

## Sources

- [Hevy — official homepage](https://www.hevyapp.com/)
- [Hevy — features index](https://www.hevyapp.com/features/)
- [Hevy Trainer — workout plan generator](https://www.hevyapp.com/features/workout-plan-generator/)
- [HevyGPT](https://www.hevyapp.com/features/hevy-gpt/)
- [Hevy — weight plate calculator](https://www.hevyapp.com/features/weight-plate-calculator/)
- [Hevy — automatic rest timer](https://www.hevyapp.com/features/workout-rest-timer/)
- [Hevy — live PR notification](https://www.hevyapp.com/features/live-pr/)
- [Hevy — muscle group workout chart](https://www.hevyapp.com/features/muscle-group-workout-chart/)
- [Hevy — sets per muscle group per week](https://www.hevyapp.com/features/sets-per-muscle-group-per-week/)
- [Hevy — monthly report](https://www.hevyapp.com/features/monthly-report/)
- [Hevy — year in review](https://www.hevyapp.com/features/year-in-review/)
- [Hevy — exercise library](https://www.hevyapp.com/features/exercise-library/)
- [Hevy — track body measurements](https://www.hevyapp.com/features/track-body-measurements/)
- [Hevy — progress photos](https://www.hevyapp.com/features/progress-photos/)
- [Hevy — official pricing](https://hevy.com/pricing)
- [Hevy Help Center — Hevy Pro subscription](https://help.hevyapp.com/hc/en-us/articles/35119778922263-Hevy-Pro-Subscription-How-to-get-Pro-and-What-Does-It-Include)
- [Hevy Help Center — Google Fit via Health Connect](https://help.hevyapp.com/hc/en-us/articles/34204824335255-How-to-Connect-Hevy-to-Google-Fit-Using-Health-Connect)
- [SensAI — Hevy Review 2026 (8.0/10)](https://www.sensai.fit/blog/hevy-review-2026)
- [RepReturn — Hevy App Review 2026](https://repreturn.com/hevy-app-review/)
- [Reddit r/Hevy — free vs. Pro differences](https://www.reddit.com/r/Hevy/comments/10gp6kp/what_are_the_differences_between_the_free_and_pro/)
