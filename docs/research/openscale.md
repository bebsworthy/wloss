# openScale — Feature Analysis

**Category:** Open-source weight & body-metrics tracker with Bluetooth smart-scale integration (plus a DIY open-hardware scale sub-project).
**Platforms:** Android. Current stable v3.1.3 requires Android 12+ (29 MiB APK; arm64-v8a, armeabi-v7a, x86, x86_64). Distributed via F-Droid, Google Play (open beta), and GitHub dev builds. No iOS.
**License & source:** GNU GPLv3 — https://github.com/oliexdev/openScale (author "olie.xdev"; copyright notice updated to 2025+). Includes an `arduino_mcu` folder for a custom open-hardware Bluetooth scale.
**Pricing model:** Free, no ads, no account, no IAP. Companion app `openScale-sync` is separate and also open source.
**AI usage:** None. All body-composition math is deterministic — published scientific formulas and per-vendor impedance decoding algorithms. No ML, no on-device model, no remote inference.
**Local & privacy posture:** Hard local-first. The README's privacy guarantee is structural: "openScale doesn't send any data to a cloud and not having permission to access the internet is a strong guarantee of that." No account; location permission only for Bluetooth discovery and revocable afterwards; cloud-style sync exists only as an opt-in, separately-installed companion app.

## Overview

openScale is the canonical FOSS solution for liberating body-metric data from cheap Bluetooth bathroom scales. Its core loop: pair a supported BIA scale, step on, and openScale captures weight plus whatever body-composition values the scale transmits (body fat, water, muscle, bone, etc.) directly into a local database — no vendor cloud, no vendor account. For scales that only transmit raw impedance (or none at all), openScale computes body fat, body water, and lean mass from published anthropometric equations, and it tracks far beyond weight: BMI, visceral fat, BMR/TDEE, circumferences (waist/hip/chest/thigh/biceps/neck), waist-to-height and waist-hip ratios, caliper body fat, ECW/ICW/protein/BCM, metabolic age, and fully custom user-defined measurement types.

2025–2026 was a renaissance: after years on an aging Java codebase, **v3.0 (Nov 2025) was a complete ground-up rewrite in Kotlin with Jetpack Compose and a new Bluetooth stack**, solicited via the "Beta Test openScale 3.0" issue (#1139) explicitly to fix the dated foundation. Since then the cadence has been roughly monthly: 3.0.3 added the US Navy body-fat formula and Material polish, 3.1.0 brought Material 3 theming, 3.1.1 added an Insights screen and ECW/ICW/protein/BCM metrics, 3.1.2 added a scientific Mi Scale body-composition algorithm and undo for deletions, and **v3.1.3 (Sep 6, 2026) added segmental body fat/muscle per arm/leg/torso, metabolic age, custom per-scale measurement types with CSV columns, and a new sync API** (requires openScale-sync 0.6.3+). ~3,000 commits, ~2.5k stars, and a very low open-issue count (2 open at time of research) — this is one of the healthiest solo-lead projects in the space.

## Feature inventory

### Local-first architecture (storage, export/backup formats, account-free operation)

- **Database:** Room (SQLite) in a clean-architecture core (`database`, `data`, `model`, `usecase`, `bluetooth`, `facade`, `service`, `worker`). Notably the schema is an **EAV-style measurement model**: `MeasurementEntity` + `MeasurementTypeEntity` + `MeasurementValueEntity` (DAOs: `MeasurementDao`, `MeasurementTypeDao`, `MeasurementValueDao`, `UserDao`, `UserGoalsDao`). Users can define arbitrary measurement types (each gets its own CSV column since 3.1.3) instead of the schema being hardcoded to weight.
- **Use-case layer worth copying:** `AutoBackupUseCases`, `BackupRestoreUseCases`, `ImportExportUseCases`, `MeasurementAggregationUseCase`, `MeasurementInsightsUseCase` (27 KB — powers the Insights screen), `MeasurementSmoothingUseCases`, `MeasurementTransformationUseCase`, `MeasurementEvaluationUseCases` (evaluation vs recommended ranges from age/sex/height), `ReminderUseCase`, `SyncUseCases`, plus a `MeasurementDemoUseCase` that generates demo data.
- **Export/import:** CSV import and export (covering internal types since 3.1.1). Since 3.1.1, **backups are staged and validated before restore** — a restore-safety pattern rare even in commercial apps.
- **Auto-backup:** dedicated use case; plus optional autosync to Nextcloud (documented on the wiki) and the opt-in `openScale-sync` companion app (new API in 3.1.3).
- **Account-free:** fully; no internet permission at all in the main app. Home-screen resizable widget shows the latest measurement.

### Food logging & nutrient estimation

N/A — not offered. openScale is deliberately single-purpose (weight/body metrics). There is no food database, no Open Food Facts integration, no calorie tracking. (TDEE/BMR are *computed outputs* from body metrics, not intake tracking.)

### Weight & body metrics (scale models supported, body-composition formulas, trend charts)

- **Supported scales — the deepest list in open source.** The v3 Bluetooth layer has adapters for GATT, broadcast, and SPP protocols and **~68 scale driver implementations** (`core/bluetooth/scales/*.kt`), including: Beurer (BF450, Sanitas shared stack), Xiaomi Mi Scale v1/v2/S400/S800, Huawei (AH100/CH100/CH100S, Hagrid WSP), Etekcity (ESF-551, Fit 8S), Eufy (C20, P2), Yunmai (Mini/SE/X), iHealth HS3, PICOOC (incl. broadcast protocol), Omron (HBF-702T, WLC), Renpho (ES-26BB + generic), Dr.Trust (505/532), FitTrack Dara / Hume Dara 2.0, Runstar (R5/R6), Keep S3, RyFit, Cult Smart Scale Pro, Vitafit VT701, QN, Sinocare, Soehnle, Taylor BIA, Trisa Body Analyze, Weight Gurus A3, Realme Smart Scale, Medisana BS44x, Digoo DG-S038H, Excelvan CF36x, Exingtech Y1, Hesley/Yunchen, MGB, Silvercrest, Sanitas SBF70/72, EasyHome 64050, Senssun, OKOK-protocol devices, and a `CustomOpenScaleHandler` for DIY scales (the Arduino MCU project), plus a `DebugGattHandler` for development.
- **Body-composition estimation — two tiers:**
  1. *Per-vendor impedance decoding:* reverse-engineered algorithms converting BIA impedance to fat/water/muscle per scale family (e.g., the "scientific body-composition algorithm for Mi Scale" added in 3.1.2; dual-impedance Mi Scale 2 handling fixed in 3.1.1).
  2. *Published anthropometric fallback formulas* (documented on the wiki "Body metric estimations" page with citations):
     - Body fat: Deurenberg et al. 1991 and Deurenberg 1992 (BMI-based, age/sex), Eddy et al. 1976, Gallagher et al. 2000 (separate Asian and non-Asian equations).
     - Body water: Behnke et al. 1963, Delwaide-Crenier et al. 1973, Hume & Weyers 1971, Lee et al. 2001 (validated against bioimpedance).
     - Lean body mass: Boer 1984, Hume 1966, or the subtraction method `weight − body fat`.
     - Circumference-based: **US Navy body-fat formula** (added 3.0.3) usable with the app's body-site measurements.
- **Metrics tracked:** weight, BMI, body fat, body water, muscle, LBM, bone mass, visceral fat, BMR, TDEE, waist/hip/chest/thigh/biceps/neck circumferences, waist-to-height ratio, waist-hip ratio, caliper fat, ECW/ICW/protein/BCM, metabolic age, segmental fat/muscle (arms/legs/torso, 3.1.3), free-text comments, custom measurement types.
- **Multi-user & edge cases:** automatic recognition of *who* is stepping on the scale (a repeatedly praised feature on Reddit), assisted weighing for babies/pets, amputation adjustments, kg/lb/st units.
- **Charts/trends:** graph + table views, resizable charts, **chart projections/forecasts** (3.0.1), measurement smoothing, aggregation (3.1.0), evaluation against recommended ranges, time-range-limited Insights (3.1.1/3.1.3).

### Meal planning / shopping list / exercise

N/A — not offered. No meal planning, no shopping list, no exercise logging. (Sync to external ecosystems exists via the openScale-sync API and Nextcloud autosync, but content is body metrics only.)

### Statistics, visualization & gamification

- **Insights screen (3.1.1):** data-driven history analysis — computed observations over selectable time ranges (largest single use case in the codebase), a genuinely quantified-self feature rather than a vanity dashboard.
- Chart projections to goal (forecast line to target weight), trend smoothing, aggregation, goal tracking with evaluation against healthy ranges, tabular data view, resizable home-screen widget.
- **Gamification: none.** No streaks, badges, or social layer. Undo for accidental deletes (3.1.2) is the closest thing to interaction forgiveness.

### AI features

N/A — not offered, and arguably a deliberate posture: every estimate traces to a named publication or a documented per-vendor algorithm.

### Input-minimization techniques

- Zero-entry capture: the measurement arrives over Bluetooth the moment the user steps off the scale; manual entry exists only for unsupported scales.
- Multi-user auto-detection removes per-weighing user selection.
- New entries inherit waist/hip values (3.1.2) so circumference entry is incremental, not from-scratch.
- Reminder use case (weighing reminders) nudges rather than demands.

### Design & UX / micro-interactions

- **Honest history:** pre-3.0 openScale was the archetypal "engineer UI" — functional, dense, visually dated. The 2025 **Kotlin + Jetpack Compose rewrite** (v3.0) with **Material 3 theming (3.1.0)** modernized it substantially, and the rewrite was explicitly motivated by the desire for a "modern, stable, and faster foundation" (beta-test issue #1139).
- Post-rewrite UX is clean Material 3 but **utilitarian**: standard charts, forms, and dialogs. Micro-interaction depth is thin — no animated goal celebrations, no rich data-viz beyond line charts (no distribution views, no correlation heatmaps), no gesture-driven chart scrubbing beyond stock Compose behavior, minimal delight layer.
- Strengths it does have: resizable widget, dark theme, wide language coverage (19+ languages via Weblate, incl. Hindi and Bulgarian added 3.1.2), good empty-state handling via the demo-data generator.
- For WLO: openScale proves a Compose/M3 rewrite resets a FOSS app's design credibility — but it still leaves "emotionally engaging data visualization" unclaimed.

## Strengths & differentiators

- **Broadest open Bluetooth-scale support in existence** (~68 drivers, three transport protocols, DIY custom-scale path), with rapid 2025–2026 expansion (PICOOC, Huawei, Omron, Eufy, Etekcity all landed within months).
- **Scientifically grounded body-composition math** — every formula cited to a publication; per-vendor impedance decoders; US Navy method; new Mi Scale scientific algorithm; segmental analysis.
- **Structural privacy:** no internet permission in the main app is a *provable* privacy claim, stronger than any policy document.
- **Flexible EAV measurement schema** — users invent their own tracked metrics with first-class CSV support.
- **Multi-user auto-detection and assisted weighing** — unique among weight trackers.
- **Restorable-with-confidence backups** (staged + validated) and chart forecasting to goal.
- Very active maintenance: 7 stable releases Jan–Sep 2026.

## Weaknesses & user complaints (cited)

- **Scale-compatibility lottery:** support "depends 100% on the scale" (r/bluetooth developer thread); users with unsupported scales are limited to manual entry, and connectivity bugs dominated several 3.1.x patch releases (Huawei, QN/Renpho, Beurer/Sanitas fixes in 3.1.1/3.1.2). [r/bluetooth](https://www.reddit.com/r/bluetooth/comments/iaylc3/bluetooth_scale_custom_app/), [releases](https://github.com/oliexdev/openScale/releases)
- **No cloud/sync story beyond a second app:** cross-device and backup-sync require the separate `openScale-sync` companion (0.6.3+ for the 3.1.3 API); r/selfhosted users wanting server-side data must roll their own (Nextcloud script/wiki page). [r/selfhosted](https://www.reddit.com/r/selfhosted/comments/1mokhzf/selfhostable_wifi_bathroom_scales/), [wiki](https://github.com/oliexdev/openScale/wiki)
- **Single-purpose scope:** no nutrition, exercise, or lifestyle correlation — Quantified-Self users stitch it together with other apps; Home Assistant users export manually. [r/QuantifiedSelf](https://www.reddit.com/r/QuantifiedSelf/comments/16bjz1h/what_is_the_best_smart_scale_to_measure_and/), [r/homeassistant](https://www.reddit.com/r/homeassistant/comments/1i3yecg/how_i_added_an_unsupported_smart_scale_to_ha/)
- **Utilitarian visuals even after the M3 rewrite:** charts and insights are functional rather than motivating; no gamification layer to sustain daily engagement (visible across its issue tracker feature requests and the project's own "easy UI with graphs" framing). [GitHub repo](https://github.com/oliexdev/openScale), [beta issue #1139](https://github.com/oliexdev/openScale/issues/1139)
- **Rising minimum OS:** v3.1.x requires Android 12+, stranding older-device users on 2.5.4. [F-Droid](https://f-droid.org/en/packages/com.health.openscale/)
- **Estimate accuracy caveats:** BIA body-composition values (vendor or formula) are approximations — the wiki's reliance on 1970s–90s anthropometric equations underscores that these are estimates, and users comparing vendor-app vs openScale numbers see discrepancies.

## What WLO should learn

1. **Adopt the EAV measurement schema.** openScale's `Measurement / MeasurementType / MeasurementValue` model is exactly how WLO should store weight, body metrics, poop logs, mood, or any future metric: one engine, arbitrary user-defined measurement types, automatic CSV column mapping. It is the single most transferable architectural idea in either app.
2. **Make privacy provable, not promised.** "No internet permission" is openScale's strongest marketing line. WLO needs network for remote AI/OFF, but can copy the pattern: a **hard-offline build flag or module split** where the network-enabled layer is optional and auditable, and the default install ships no analytics/telemetry code paths.
3. **Copy the two-tier estimation pattern with citations.** Per-device decoder first, published-formula fallback second, every value labeled with provenance (measured vs estimated vs derived). WLO's nutrient estimation and body-fat features should render provenance badges — openScale computes this but barely surfaces it in UI, which is a UX gap WLO can close.
4. **Copy the trust mechanics:** staged-and-validated backup restore, undo for destructive actions, demo-data generator for exploring UI without real data, and chart projections to goal. All four are cheap, high-trust features absent from most commercial trackers.
5. **Exploit the multi-user auto-detection niche (or integrate with it):** household weight logging with automatic who's-on-the-scale detection is a beloved feature WLO could offer in its weight module — or at minimum support import of openScale CSV exports.
6. **Win the visualization battle.** openScale's charts stop at line charts + projections. For a numbers-geek audience WLO can add distribution/histogram views (weigh-in time-of-day variance), metric correlations (sleep/eating windows vs weight deltas), silhouette-photo-to-metric overlays, and animated goal-progress storytelling — none of which openScale has.
7. **Beat it on the emotional layer without betraying privacy:** openScale proves users accept formula-driven estimates when methodology is transparent; pair that transparency with streaks, milestone celebrations, and micro-interaction polish (haptics on weigh-in capture, animated forecast cone) to own "quantified-self that feels alive."

## Sources

- [openScale GitHub repository (oliexdev/openScale)](https://github.com/oliexdev/openScale)
- [openScale releases (v3.0 rewrite → v3.1.3, 2025–2026)](https://github.com/oliexdev/openScale/releases)
- [openScale on F-Droid (com.health.openscale)](https://f-droid.org/en/packages/com.health.openscale/)
- [openScale wiki — Body metric estimations (formula list with citations)](https://github.com/oliexdev/openScale/wiki/Body-metric-estimations)
- [openScale wiki (supported-scale pages, Nextcloud autosync, FAQ)](https://github.com/oliexdev/openScale/wiki)
- [openScale v3 Bluetooth scale drivers (source tree, ~68 handlers)](https://github.com/oliexdev/openScale/tree/master/android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales)
- [openScale core use-case layer (backup/insights/smoothing/sync source)](https://github.com/oliexdev/openScale/tree/master/android_app/app/src/main/java/com/health/openscale/core/usecase)
- [openScale database layer (Room schema source)](https://github.com/oliexdev/openScale/tree/master/android_app/app/src/main/java/com/health/openscale/core/database)
- [Beta Test openScale 3.0 — issue #1139 (rewrite motivation)](https://github.com/oliexdev/openScale/issues/1139)
- [r/QuantifiedSelf — best smart scale to extract data](https://www.reddit.com/r/QuantifiedSelf/comments/16bjz1h/what_is_the_best_smart_scale_to_measure_and/)
- [r/selfhosted — Self-hostable WiFi bathroom scales](https://www.reddit.com/r/selfhosted/comments/1mokhzf/selfhostable_wifi_bathroom_scales/)
- [r/homeassistant — adding an unsupported smart scale (multi-user praise)](https://www.reddit.com/r/homeassistant/comments/1i3yecg/how_i_added_an_unsupported_smart_scale_to_ha/)
- [r/bluetooth — Bluetooth scale custom app (compatibility reality)](https://www.reddit.com/r/bluetooth/comments/iaylc3/bluetooth_scale_custom_app/)
