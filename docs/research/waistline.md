# Waistline — Feature Analysis

**Category:** FOSS calorie counter, food diary & weight tracker
**Platforms:** Android 5.0+ (also has a `cordova-browser` build target, but Android is the shipped product). Distributed via F-Droid and Google Play (`com.waist.line`). No iOS.
**License & source:** GNU GPLv3 — https://github.com/davidhealey/waistline (F-Droid package page: https://f-droid.org/packages/com.waist.line/)
**Pricing model:** Free, no ads, no in-app purchases, no account, no paid tier of any kind.
**AI usage:** None. No AI features shipped; the app's own docs describe AI meal-photo scanning as "Not shipped." The maintainer has publicly stated disapproval of GitHub Copilot and withholds consent to training on the codebase.
**Local & privacy posture:** 100% on-device storage in IndexedDB. No login, no cloud sync, no analytics. Network is used only for: Open Food Facts (OFF) barcode/search/upload, USDA FoodData Central queries (opt-in), and intervals.icu exercise sync (opt-in, API key). BACKUP_KEYS (OFF password, USDA key, intervals.icu key) are actively blanked out of settings exports — a deliberate secret-hygiene detail.

> **Maintainer correction:** the brief said "David Heiko / dheidemann". The actual author/maintainer is **David Healey** (`davidhealey`), a UK-based solo developer. Repo is `github.com/davidhealey/waistline` (the `dheidemann/waistline` URL 404s). Donation link paypal.me/healeyd confirms identity. Note: `waist-line.com` is an *unofficial affiliate* page, not the project's site — used here only as corroboration, never as a primary source.

## Overview

Waistline is a libre calorie counter and weight tracker that has become the default FOSS answer on r/fossdroid whenever people complain that MyFitnessPal-class apps have become subscriptionware. It keeps a day-by-day food diary organized into configurable meal categories, draws food data from a local database that can be populated by manual entry, barcode scanning (Open Food Facts), or online search (OFF + USDA FoodData Central), and pairs the diary with a weight tracker and a Chart.js statistics page. It is deliberately positioned against coaching/gamification — the project describes itself as bare-bones: no coach, no mascot, no badges.

Architecturally it is unusual among Android trackers: it is a **Cordova (v12) + Framework7 + jQuery** hybrid app, with all storage in **IndexedDB** (`waistlineDb`, schema version 34) accessed through a single `db-handler.js`. The food list, diary (one entry per *day* containing item references + daily stats), meals, and recipes are object stores; diary entries store references to food/recipe objects rather than copies, which is why foods can only be archived, never hard-deleted, without breaking history.

Maintenance status in 2026: **actively maintained by one person**. F-Droid shipped 3.10.0 (Jun 2025), 3.10.1 (Mar 2026), and 3.11.1 (added Aug 19, 2026; tagged Aug 15). The 3.11.x cycle added intervals.icu sync, OFF dietary filters, arbitrary statistics date ranges, and a moving-average chart option. ~3,400 commits, ~740 stars, 170 open issues, translations via Crowdin (~28 languages). Bus factor is effectively 1.

## Feature inventory

### Local-first architecture (storage, export/backup formats, account-free operation)

- **Storage:** IndexedDB database `waistlineDb` (version 34) with object stores: `foodList` (indexed on dateTime, barcode, name, brand, multiEntry categories), `diary` (one row per day; `stats` + `items[]` arrays — a v30 migration merged per-item rows into daily documents and folded legacy weight logs into day stats), `meals`, `recipes`. A legacy `log` store is migrated then dropped.
- **Diary-by-reference:** diary entries hold IDs/references to foodList objects. Consequence: foods are *archived*, not deleted ("Clone item and archive original" workflow for changed nutrition); archived items are recoverable via a search-bar filter. This preserves historical integrity but confuses users.
- **Export:** full-database JSON export (`waistline_export.json`) — one array per store plus a `version` stamp, enabling forward migrations on import. **CSV diary export** (`diary_export.csv`, semicolon-delimited, includes nutriments and body stats). Both support Android native share.
- **Backup:** `auto-backup` toggle writes JSON into a dedicated app backup directory (`writeFileInBackupDir`, with an Android 13+ storage path special case); 3.9.0 added sharing the backup file via the Android share sheet.
- **Import:** JSON database import (with confirmation + settings migration) and a separate **bulk food-list JSON import** (`foodList` entries with `name`, `portion`, `unit`, optional `brand`/`uniqueId` for dedup/overwrite; imported items hidden by default). This is the escape hatch from MFP/LoseIt-exports via community converters.
- **Account-free:** fully operational offline with no account; OFF sign-in exists solely for *attributed product uploads*, not for using the app.
- **Schema migration discipline:** `upgradeDatabase` handles v24→v34 (UTC-midnight date normalization, barcode zero-stripping with `originalBarcode` preservation, `fdcId_` prefixed USDA ids, nutriment key renames). Legacy-format imports are inferred when no version stamp exists.

### Food logging & nutrient estimation (Open Food Facts, barcode, offline DB)

- **Local food database first:** foods must exist in the local DB before diary use; populated by manual entry, barcode scan, or online search.
- **Open Food Facts:** built-in search (language/country selectable), barcode scanning with automatic prompt to add missing products to OFF, optional OFF account for attributed uploads, upload-country setting, dietary filter on results (3.11.0). Barcode scan UX has flashlight + beep toggles.
- **USDA FoodData Central:** opt-in (disabled by default) with user-supplied API key, online key validation, and distinct handling of rate-limit vs invalid-key errors (3.11.0). Targets generic/branded US foods that OFF lacks.
- **Nutrients:** calories + fat/carbs/protein core; the visible nutriment list is **user-configurable and reorderable** (Settings > Nutriments), including custom entries; energy unit switchable kcal/kJ; alcohol grams conversion exists in utils.
- **Meals vs recipes:** a *meal* groups foods logged as separate diary entries; a *recipe* is logged as one entry. Both are first-class saved objects with categories and images.
- **Portion handling:** serving size vs number-of-servings semantics; a "chain" toggle scales all nutrient fields proportionally; **math expressions allowed in portion sizes** (3.10.0, e.g. `1.5*60`); optional "prompt for quantity when adding" to cut a dialog round-trip.
- **Quick add:** long-press the diary "+" to enter bare calories (+ optional description) — negative calories for exercise burns.
- **Photos:** meal/recipe photos via camera, cropped and compressed client-side (max 640×640, binary-search JPEG compression) since 3.8.0; thumbnails with Wi-Fi-only loading option.
- **TTS read-aloud** of nutrition data (cordova-plugin-tts-advanced) — an accessibility-era feature almost no tracker has.
- **No offline food DB:** there is no bundled offline database; OFF lookups require connectivity. (Community mitigations exist, e.g. the waistline-ciqual French food database built from ANSES-CIQUAL open data, imported via the JSON food import.)

### Weight & body metrics

- Weight is entered via a "scale" button in the diary and stored in the day's stats; body stats beyond weight are **user-definable** (e.g., water, blood pressure) with configurable visibility/order.
- No Bluetooth scale integration, no body-composition estimation, no forecasting — only trend visualization on the Statistics page.

### Meal planning / shopping list / exercise

- **Meal planning:** diary categories are configurable and reorderable (rename meals, add snacks); saved meals/recipes are re-added in one tap. No forward *planning* of future days, no calendar drag-and-drop.
- **Shopping list:** N/A — not offered.
- **Exercise:** minimal by design. Exercises are negative-calorie diary entries that raise the day's remaining budget (2000 kcal goal − 100 kcal entry = 2100 kcal budget). Since 3.11.0 Waistline **synchronizes with intervals.icu** (user API key + athlete ID, Basic auth) so workout burns flow in automatically. No exercise library, timers, or volume tracking.

### Statistics, visualization & gamification

- Statistics page built on **Chart.js + chartjs-plugin-annotation**: per-nutriment and body-stat time series, daily overview pie chart of the Fat:Carbs:Protein split, per-meal macro breakdowns (tap a meal's energy total).
- Trend tooling has improved sharply in 2026: **linear-regression trend line** (3.10.1), **moving average** option (3.11.0), goal line annotation, and **arbitrary date ranges** (3.11.0).
- Per-nutriment "show in diary / show in statistics" toggles; "average-goal-base" and "first-day-of-week" options feed goal math; goal types include shared-goal, auto-adjust (goal adapts to your rolling average), minimum-goal, percent-goal.
- **Gamification: none, by explicit design.** No streaks, badges, or mascots — this is a stated identity, and it is also the single biggest emotional gap for retention-focused users.

### AI features

N/A — not offered. No on-device model, no remote AI hook, no photo recognition (photos are attachments only, never parsed).

### Input-minimization techniques

- Barcode scan → instant product pull (the primary fast path).
- Long-press "+" quick-add calories.
- "Prompt for quantity when adding items" collapses add+edit into one dialog.
- Chain-linked proportional nutrient scaling when editing portions.
- Math expressions in the portion field (no calculator round-trip).
- Re-add of saved meals/recipes in one tap; OFF auto-add prompt for missing products.
- Diary thumbnails so users recognize foods visually instead of reading names.

### Design & UX / micro-interactions

- Framework7 gives a functional but **visibly non-native, pre-Material-3 look**; the "Animations toggle" exists largely because Cordova transitions can misbehave — the project's own limitations list admits "Cordova build causes occasional GUI quirks."
- Theming: system/dark/light mode plus selectable color themes (default `color-theme-red`); a 3.7.3 release was needed just to fix "follow the system theme."
- Micro-interactions are sparse: toasts/notifications via Framework7, no haptics-by-design, no animated number counters, no chart transition polish, no confetti/goal-celebration moments.
- External review verdict (PeakD): "The User Interface of Waistline is not badly designed. It's not so great either. It's just the perfect design for the app." Reddit guidance for new users is essentially "read the FAQ" — the FAQ/feature depth is high but undiscoverable in-UI.
- A small onboarding bright spot WLO can still learn from: Waistline ships a **setup wizard activity** (first-run personal-data/goals flow) rather than dumping users into an empty diary.

## Strengths & differentiators

- **Exemplary data ownership:** plain JSON + CSV exports, versioned schema with migration-on-import, auto-backup, secret-blanking in exports, bulk food import format for third-party DBs.
- **Dual food-data backends** (OFF + USDA FDC) with user-supplied key — the strongest FOSS food coverage story, plus contribution loop back into OFF.
- **Diary-by-reference data model** keeps history consistent when foods change (archive, never orphan).
- **Deep customization:** configurable nutriment list, custom body stats, custom categories with emoji, per-stat diary/statistics visibility, kcal/kJ, cm/in.
- **Respect for the numbers-geek:** regression trend line, moving average, free date ranges, goal auto-adjust from rolling averages — closer to a quantifier tool than a diet coach.
- Unusual niceties: TTS nutrition read-aloud, intervals.icu sync, recipe-vs-meal semantics.

## Weaknesses & user complaints (cited)

- **Dated, non-native UI with Cordova quirks** — acknowledged by the project's own limitation notes ("occasional GUI quirks"); theme-follow-system needed a dedicated fix release (3.7.3). [GitHub repo/README](https://github.com/davidhealey/waistline), [releases](https://github.com/davidhealey/waistline/releases)
- **No gamification/streaks/coaching at all** — by design ("deliberately bare-bones"), a retention gap repeatedly visible in threads where users bounce off FOSS trackers. [waist-line.com (unofficial)](https://waist-line.com/), [r/loseit premium-fatigue thread](https://www.reddit.com/r/loseit/comments/1duhmji/calorie_trackers_are_all_premium_now_and_its_so/)
- **Learning curve; features hidden behind the FAQ** — r/fossdroid's "How to use Waistline?" thread and the FAQ's size both signal discoverability problems. [r/fossdroid](https://www.reddit.com/r/fossdroid/comments/f1nj39/food_tracking_app_how_to_use_waistline/), [FAQ.md](https://github.com/davidhealey/waistline/blob/master/FAQ.md)
- **No offline food DB** — search fails offline; irrelevant results happen on OFF lookups (project limitation notes). [waist-line.com (unofficial)](https://waist-line.com/), [README](https://github.com/davidhealey/waistline)
- **No shopping list, no forward meal planning, no forecasting, no body-composition, no AI/photo recognition** — all absent from the codebase (activities tree: about, diary, foodlist, foods-meals-recipes, goals, meals, recipes, settings, setup-wizard, statistics). [repo tree](https://github.com/davidhealey/waistline)
- **Metric-first friction for US users** (unit conversion manual per unofficial site) and **no iOS**. [waist-line.com (unofficial)](https://waist-line.com/)
- **Bus factor 1** — solo maintainer, 170 open issues. [GitHub](https://github.com/davidhealey/waistline)

## What WLO should learn

1. **Copy the versioned JSON export contract.** Waistline's export = `{store: [...], version}` with migration-on-import is the reason its users trust it with years of data. WLO should ship a documented, versioned JSON export + CSV per-domain export *from v1*, plus auto-backup to a user-visible folder — and blank API keys out of exports like Waistline does.
2. **Copy "reference + archive" instead of "copy + delete."** Diary entries pointing at food objects (with archive/clone semantics) keep history honest when a product reformulates. This is a cheap architectural pattern with outsized data-integrity payoff.
3. **Beat it on onboarding and discoverability:** Waistline's power (configurable nutriments, goal math, statistics toggles) is buried in docs. WLO can win with progressive disclosure, inline tips, and a setup wizard that *shows* the resulting goal math (BMR/TDEE transparency) instead of a static 2000 kcal default.
4. **Own the gamification vacuum.** Waistline explicitly refuses streaks/badges/coach. WLO's target user (numbers-geek) wants *quantified* gamification: streaks, PRs, trend-based achievements, micro-celebrations on goal-line crossings — none of which exist in Waistline.
5. **Beat it on micro-interactions and native polish.** Framework7 look-and-feel, an animations kill-switch, and "GUI quirks" in the project's own notes leave the entire beautiful-minimalist lane open: native Material 3/Compose-quality motion, animated counters, haptic ticks on entry, springy charts. This is WLO's clearest differentiation axis.
6. **Treat food-DB integration as pluggable providers.** The OFF + USDA (+ community CIQUAL) pattern with user-supplied keys validates WLO's provider-agnostic architecture — go further with an offline-cached OFF subset and on-device photo recognition, both of which Waistline lacks entirely.
7. **Steal the small delight features:** math expressions in quantity fields, proportional "chain" nutrient scaling, long-press quick-add, and TTS read-aloud are near-zero-cost affordances that power users rave about; WLO should have equivalents from day one.

## Sources

- [Waistline GitHub repository (davidhealey/waistline)](https://github.com/davidhealey/waistline)
- [Waistline on F-Droid (com.waist.line)](https://f-droid.org/packages/com.waist.line/)
- [Waistline releases / changelog](https://github.com/davidhealey/waistline/releases)
- [Waistline FAQ.md (user guide)](https://github.com/davidhealey/waistline/blob/master/FAQ.md)
- [Waistline db-handler.js (IndexedDB schema, export/import)](https://github.com/davidhealey/waistline/blob/master/www/assets/js/db-handler.js)
- [Waistline settings.js (themes, auto-backup, CSV export, OFF/USDA/intervals.icu)](https://github.com/davidhealey/waistline/blob/master/www/activities/settings/js/settings.js)
- [Waistline utils.js (units, image pipeline, backup paths)](https://github.com/davidhealey/waistline/blob/master/www/assets/js/utils.js)
- [waist-line.com (unofficial affiliate page — corroboration only)](https://waist-line.com/)
- [Waistline on Google Play](https://play.google.com/store/apps/details?id=com.waist.line)
- [r/fossdroid — Food tracking app? (How to use Waistline?)](https://www.reddit.com/r/fossdroid/comments/f1nj39/food_tracking_app_how_to_use_waistline/)
- [r/loseit — Calorie trackers are all premium now…](https://www.reddit.com/r/loseit/comments/1duhmji/calorie_trackers_are_all_premium_now_and_its_so/)
- [PeakD review of Waistline (UI verdict)](https://peakd.com/@harry-heightz/simple-mobile-apps-to-help-you-manage-your-activities-resources-and-healthpart-4-contd)
