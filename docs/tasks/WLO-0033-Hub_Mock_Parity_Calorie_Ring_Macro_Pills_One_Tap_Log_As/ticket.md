---
id: WLO-0033
title: 'Hub mock-parity: calorie ring + macro pills, one-tap log-as-planned, streak chip (F11 display-only)'
status: doing
theme:
release:
created: 2026-09-14T07:43:27Z
modified: 2026-09-14T07:43:35Z
closed:
revision: 304306edd45a2753
blocks: []
related: []
---

# Description

## Goal

Close the four remaining mock-parity gaps between the day-loop prototype
(`docs/design/flows/01-day-loop-f10.html`, ratified round-8) and the built Hub.
Three are fully buildable from existing data; one (workout session card) is
blocked on the F05 module and is explicitly out of scope here.

Owner approved the batch ("go") after WLO-0031/0032 made the atom set
compile-enforced. All new UI MUST use the design-system atoms — uiAtoms
arch rules run in **enforce** mode by default.

## Work item 1 — Calories card becomes the mock's ring card

Mock anatomy (flow 01, morning frame + pins 3/6/11):

- Ring (`--fill` = consumed/budget, capped at 1.0): center shows consumed kcal
  stat + small "of 1,900 kcal" line; over-budget renders "+10" in that small
  line (information, never a verdict; no red exists — R-D1/R-D5).
- Right column: "1,008 left" stat (fact copy) + macro dots
  `P 128/165 · C 171/215 · F 52/70` + "tap the ring for today's detail".
- Provenance ⓘ lives on the target line and taps through to the existing
  budget explainer ("adaptive target… last updated …"). Card header stays
  "Calories" with NO provenance in the header (R-D12 round-8).
- Ring sweeps with ~400 ms ease-out on value change (F10 motion table).

Data: consumed = `DayView.intakeKcal` (exists); budget = `DayView.budgetKcal`
(exists, already feeds budgetExplainer); consumed macros = `DayDiary.totals`
(`DiaryRepository.observeDay` — proteinG/carbG/fatG, nullable: kcal-only
quick-adds leave them null) ; macro targets = `DayView.proteinG/carbG/fatG`
(nullable). Render each macro pill only when target AND consumed both exist.

Implementation:
- New design-system atom `WloRing` (core/designsystem, sibling of
  WloProgress.kt): determinate ring, center content slot, animated sweep,
  valence-free colors from the extended palette. First consumer is the Hub.
- Rebuild `BudgetCard` in `feature/f10-daily-hub/.../ui/HubScreen.kt` to the
  mock layout. Existing stat rows (budget/burn) fold into the mock's anatomy —
  keep the burn row only if it does not fight the mock (decide in review
  against the prototype; when in doubt, mock wins).
- Ring tap → today's diary detail (same destination the Diary card uses).

## Work item 2 — Meals · today: one-tap "Log as planned"

Mock anatomy (flow 01, pins 4/7; R-D13 forward-looking only):

- Header "Meals · today" + right receipt count "0 of 3" / "2 of 3 confirmed".
- Exactly ONE row — the next open planned slot: meal name (600 weight) +
  receipt "380 kcal · planned(prov chip)" + right "Log as planned" secondary
  button (WloSecondaryButton, small).
- Card renders ONLY while an open planned slot exists (R-D14 — already true
  via planFlags; extend, don't regress).
- Button = the happy path: one tap logs the planned meal (prefill treaty
  R-B1). Row tap opens the slot's plan focus (existing route
  `f03/plan/focus?day&slot` via the existing `wlo://log/planned?slot=` deep
  link) — the full meal sheet stays on the F03 surface this ticket.

Data path — new core/data door (first real `EntryVia.PLAN` consumer):
- `PlannerRepository` gains a log-as-planned operation: read the slot (must be
  PLANNED with recipeId), create the diary entry from the slot's per-serving
  macros via the existing diary door (`EntryVia.PLAN`), then link the slot to
  the entry using the existing slot-state machine (study
  `RoomPlannerRepository` confirm/replace semantics; the diary keeps the
  single record — R-D11 dedup). Keep the whole operation inside core/data so
  both F10 and F03 can reuse it. WloResult-typed, like every door.
- HubViewModel: MealTodayUi(next open slot: name, planned kcal, slotId) +
  confirmed/total count for the header. After a successful log the card
  re-renders (next open meal, or disappears) and the ring re-sweeps — both
  fall out of existing state flows; verify.

## Work item 3 — Streak chip in the Hub header (F11 display-only)

Mock: header actions show `🔥 12` chip; display-only, no tap-through (pin 1:
"Streak chip … display-only (F11 owns)"). Midday pin: "streak ticked after
log". F11 spec rule: multi-oracle — a day counts via weigh-in OR meal-log OR
workout (F11 §gamification engine).

- New pure engine `StreakMetrics` in core/engines (sibling of
  AdherenceMetrics): consecutive multi-oracle days ending today, or ending
  yesterday when today is not yet logged (morning shows the intact streak;
  today's log ticks it). Inputs are plain per-day oracle flags — no DAO
  access in the engine.
- HubViewModel computes oracle flags from `dayProjection.range` (~90 days,
  same source as week dots): weigh-in = trendWeightKg present, meal-log =
  intakeKcal present, workout = burnKcal > 0.
- Hub header renders the count chip beside Settings (WloBadge/atom styling;
  add a flame glyph to WloIcons). No freeze tokens, no ledger, no forgiveness
  mechanics — the F11 gamification ledger supersedes this later; the chip
  shows the true count. Hide when streak is 0 (content-rendered, R-D14).
- JVM test for StreakMetrics (core/engines jvmTest, AdherenceMetrics
  precedent).

## Out of scope (documented, not forgotten)

- **Workout session card** — the mock's "Push A — 6 exercises, ~45 min" card
  needs F05's routine/session data model; the F05 module does not exist yet
  (settings.gradle.kts has no f05; only `MeasurementKind.BURN` events and a
  `stub/exercise` screen). The existing rail Workout button already matches
  the mock's rest-day frame; the PR badge-dot is F05/F11 news and cannot
  exist yet. Filing an F05 foundation ticket is the follow-up.
- Report-ready pill, meal sheet on Hub, widget, freeze tokens / F11 ledger.

## Gates

- `checkArchitecture` enforce mode 0 violations; ktlint + detekt green.
- core:engines + core:data jvmTest green (new StreakMetrics +
  log-as-planned tests).
- `connectedDebugAndroidTest` 55/0 baseline preserved or improved; Hub test
  assertions updated to the new card anatomy; a log-as-planned flow test if
  the SeedingRobot can produce planned slots (extend it if cheap).
- Screenshot of the rebuilt Hub vs flow-01 morning frame filed as evidence.
