# WLO-0086 — Weight-management UX/UI inventory

This is the review map for the current weight-management experience. It is
implementation-backed: every current surface below is linked to its Compose
owner, and planned or canceled ideas are separated from the UI that actually
ships.

## 1. Review boundary

The core loop is **setup → weigh → interpret the trend → manage the record →
adjust the goal**. The complete review also includes the Hub entry points,
unit/reminder/profile settings, Health Connect and file import, app lock, and
weight-data portability because those surfaces change whether the core loop is
discoverable, trustworthy, and recoverable.

The governing product sources are:

- [`F06-weight-body-metrics.md`](../features/F06-weight-body-metrics.md)
- [`F01-onboarding-diet-plans.md`](../features/F01-onboarding-diet-plans.md)
- [`F07-energy-engine.md`](../features/F07-energy-engine.md)
- [`F10-daily-hub.md`](../features/F10-daily-hub.md)
- [`F13-data-vault.md`](../features/F13-data-vault.md)
- [`DESIGN-SYSTEM.md`](../design/DESIGN-SYSTEM.md)
- [`IA.md`](../design/IA.md)
- [`04-weigh-in-trend-f06-f08.html`](../design/flows/04-weigh-in-trend-f06-f08.html)
- [`WLO-0067-WEIGHT-MANAGEMENT-REVIEW.md`](WLO-0067-WEIGHT-MANAGEMENT-REVIEW.md)

## 2. Screen and surface inventory

### A. Core weight surfaces

| ID | Surface | Route / entry | What must be reviewed | Implementation |
|---|---|---|---|---|
| W01 | Weight-first setup | App launch while no completed profile exists; hosted at the Weight root | Welcome, kg/lb choice, optional loss/maintenance/gain goal, target and pace, goal-safety hold copy, first-reading source, manual first reading, skip, Back/Next/Finish | [`WeightFirstOnboardingScreen.kt`](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/WeightFirstOnboardingScreen.kt) |
| W02 | Weight overview — Weight segment | Default app destination `f06/weight`, deep link `wlo://weight` | Screen title, Weight/Body fat segment control, trend-first hero, 7-day delta, raw last reading, provenance, Weigh in CTA, goal progress, trend card, compressed history | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) |
| W03 | Manual weigh-in sheet | Weigh in CTA, Hub quick action/card, reminder deep link `wlo://weight/log` | Prefill explanation, localized kg/lb entry, date and time, ±0.1 steppers, validation, keyboard/IME, saving and failure state, dismissal | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt), [`WeightDateTimePickers.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightDateTimePickers.kt) |
| W04 | Post-save confirmation | Successful manual save | Established-trend variant, sparse-data “still learning” variant, trend hero, 7-day change, raw reading and source, one-shot haptic, accessible live announcement, Done | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) |
| W05 | Outlier decision | A saved reading outside the residual guard | Neutral explanation, Keep, Correct; Correct retires the entry and reopens capture with a good prefill | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) |
| W06 | Trend exploration | Trend card on Weight | 30d/90d/1y/all windows, raw dots, canonical trend, honest axes and empty windows, wider-window action, warm-up/lapse copy, neutral 30-day change, smoother selector, alpha slider, preview labeling, math link | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt), [`WloTrendChart.kt`](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt) |
| W07 | Trend math documentation | `f06/math`, from provenance or “How the math works” | EWMA, zero-phase, 7-day average, outlier guard, formula provenance, plain-language limitations | [`MathDocsScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/MathDocsScreen.kt) |
| W08 | Compressed history | History card on Weight | Daily/weekly/monthly/quarterly buckets, empty buckets, weigh-in counts, deltas, closing value, row tap and Full logbook CTA | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt), [`WeightHistory.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeightHistory.kt) |
| W09 | Full logbook | `f06/logbook` | Total count, newest-first raw events, sticky month summaries, source-state labels, lowest/flagged/edited marks, three-month paging, row accessibility | [`LogbookScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt) |
| W10 | Logbook edit sheet | Tap a raw logbook row | Prefilled value/date/time, validation, replace-not-mutate semantics, save failure, dismissal | [`LogbookScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt) |
| W11 | Logbook delete and undo | Swipe or accessibility action on a raw row | Release-gated delete, armed state, haptic, no velocity delete, inline undo in the correct month, countdown and failure copy | [`LogbookScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt), [`WloSwipeRevealRow.kt`](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloSwipeRevealRow.kt) |
| W12 | Weight overview — Body fat segment | Body fat segment on `f06/weight` | Body-fat/waist series switch, empty and populated charts, ratios, calculator and save result | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) |
| W13 | Body-fat calculator | Embedded in W12; legacy internal route `f06/bodyfat` also exists | Navy/RFM selection, profile-derived height, waist/neck/hip inputs, Estimate, provenance and uncertainty, Save measurement, validation/notices | [`BodyFatScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/BodyFatScreen.kt) |
| W14 | Derived ratios | Body fat segment | Waist-to-height, waist-to-hip, range-not-verdict copy, provenance, empty state, BMI reveal-on-request | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) |
| W15 | Goal progress | Card on Weight | No goal, unavailable, trend forming, loss, maintenance and gain states; current trend, target, remaining amount, completed/next milestone rungs and date-range availability | [`WeightScreen.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt), [`GoalProgressLoader.kt`](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/GoalProgressLoader.kt) |
| W16 | Goals editor | Settings → Goals, `f01/studio` | Current trend, mode, target, pace, optional date, kcal budget, implied pace, safety questionnaire, eligible/held copy, forecast states, save-as-version, version history and restore | [`GoalsEditorScreen.kt`](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/GoalsEditorScreen.kt) |
| W17 | Goal forecast | Embedded in Goals and, when eligible, Hub | Developing, available, held, withheld and missing-input states; three bands, arrival range, point-date eligibility, provenance and assumptions | [`GoalsEditorScreen.kt`](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/GoalsEditorScreen.kt), [`WloForecastCard.kt`](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloForecastCard.kt) |

### B. Supporting and entry-point surfaces

| ID | Surface | Weight-management role | Implementation |
|---|---|---|---|
| S01 | Daily Hub | Morning weigh-in card when not yet logged, quick-action Weigh button, trend hero/card with 30-day chart and explainer, optional goal forecast | [`HubScreen.kt`](../../feature/f10-daily-hub/src/main/kotlin/app/wlo/feature/f10/hub/ui/HubScreen.kt) |
| S02 | Settings | Weight unit, weigh-in reminder and notification permission; entry to Goals, Profile and Data Vault; app-lock controls | [`SettingsScreen.kt`](../../app/src/main/kotlin/app/wlo/app/ui/settings/SettingsScreen.kt) |
| S03 | Profile facts | Sex, birth year, height and activity facts used by ratio, goal-safety and forecast calculations | [`ProfileFactsScreen.kt`](../../app/src/main/kotlin/app/wlo/app/ui/settings/ProfileFactsScreen.kt) |
| S04 | Data Vault — Health Connect | Availability, no/partial/full read access, permission request, weight/body-fat import-now action, last sync counts/outcome | [`VaultDashboardScreen.kt`](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/VaultDashboardScreen.kt) |
| S05 | File import | File picker, CSV column mapping for day/weight/trend/body fat, validation report, staged confirmation, applying, done and failed states | [`ImportScreen.kt`](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/ImportScreen.kt) |
| S06 | Weight-data export/backup/restore | Portability and recovery of weight history and goals; review the explicit file handoff and success/failure states as supporting trust flows | [`feature/f13-vault/ui`](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui) |
| S07 | Manual Fresh Start | Data Vault action that currently archives history/retire profile/restarts onboarding; it is not the canceled automatic 14-day prompt | [`VaultDashboardScreen.kt`](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/VaultDashboardScreen.kt) |
| S08 | Reminder notification | Soft daily notification; tap opens `wlo://weight/log` directly into capture | [`WeighInReminder.kt`](../../app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt) |
| S09 | Adaptive app shell | Weight is the start destination; compact bottom navigation, expanded navigation rail, nested M3 top app bar and Up behavior | [`WloApp.kt`](../../app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt), [`WloTabs.kt`](../../app/src/main/kotlin/app/wlo/app/navigation/WloTabs.kt) |

## 3. Component inventory

### Weight-specific components

- Weight/Body fat segment chips.
- Trend-first hero: `WloCard`, `WloCardHeader`, `WloHeroStat`,
  `WloDeltaChip`, `ProvenanceChip` and Weigh in button.
- `GoalProgressCard` and milestone `WloListRow`s.
- `WloTrendChart`: custom chart canvas, axes, raw dots, trend line, empty
  message/action and accessibility description.
- Window chips: 30 d, 90 d, 1 y and all.
- `SmootherTuner`: EWMA, zero-phase and 7-day-average chips plus alpha
  `Slider` and preview disclosure.
- Compressed-history tier labels, dividers and tappable history rows.
- Manual entry `WloSheet`, weight `OutlinedTextField`, standard M3
  `DatePickerDialog`/`DatePicker` and `TimePickerDialog`/`TimePicker`, ±0.1
  buttons, validation and save action.
- Post-save receipt card, sparse receipt variant and polite live-region
  announcement.
- Outlier banner and Keep/Correct action pair.
- Body-fat/waist series chips and charts.
- Navy/RFM method chips, tape fields, estimate result, uncertainty/provenance
  and Save measurement action.
- Ratios card and Show BMI disclosure.
- Logbook `LazyColumn`, sticky month header, raw event row, state labels,
  Load earlier action and edit sheet.
- `WloSwipeRevealRow`, delete action, armed transition/haptic, inline undo row
  and countdown indicator. This is the intentional custom-M3 exception.
- Math method cards and provenance chips.
- `WloForecastCard`/`WloForecastChart`, legend, arrival range and held or
  developing banners.

### Shared shell and adjacent components in scope

- M3 `Scaffold`, `TopAppBar`, adaptive bottom navigation/navigation rail and
  Up action.
- Hub weigh-in card, quick action, trend card, chart, provenance explainer
  sheet and forecast card.
- Settings `WloListRow`, unit `SelectChip`s, reminder/app-lock
  `WloSwitchRow`s and reminder-time chips.
- Profile fields and choice chips.
- Health Connect status card, permission/import actions and sync receipt.
- Import file picker, column-mapping rows, staged report, progress, completion
  and failure cards.
- Global feedback primitives used by the flow: `WloBanner`, `WloBadge`,
  `WloProgress`, `WloButton`, `WloSecondaryButton`, `WloSheet`,
  `CircularProgressIndicator` and inline notices.

## 4. End-to-end user-flow inventory

### F1 — First launch to a usable tracker

`Launch → weight-first setup → unit → optional goal and safety state → first
reading source (manual / file / Health Connect later / skip) → Finish → Weight`

Review branches: skip goal, skip reading, manual reading validation, loss vs
maintenance vs gain, ineligible/held goal, Back/Next preservation, process
recreation, and whether the file/Health Connect choices set an honest next
step rather than implying an import already happened.

### F2 — Fast manual weigh-in

`Weight (default launch) → Weigh in → prefilled value/date/time → adjust or
type → Save → trend-first confirmation → Done → refreshed Weight`

Alternative entrances: `Hub quick action`, `Hub weigh-in card`, and reminder
notification all deep-link to the same sheet.

Review branches: kg/lb, comma/dot decimal, 30–300 kg equivalent range, today
vs back-date, future time rejection, blank or malformed input, duplicate tap
while saving, storage failure, keyboard/IME and sheet dismissal.

### F3 — Outlier correction

`Save unusual reading → confirmation/outlier explanation → Keep` or
`Correct → retire unusual event → reopen sheet with last good prefill → Save`

Review the fact that the event is committed before the question, whether the
simultaneous confirmation and outlier states are understandable, and whether
Correct feels reversible and nonjudgmental.

### F4 — Understand the trend

`Weight hero/provenance → Trend → choose time window → inspect raw dots and
trend → optionally preview smoother/alpha → How the math works → Up`

Review branches: zero readings, 1–2 readings, established trend, lapsed recent
window, empty selected window with wider-window action, full 30-day delta,
long histories, large changes, same-day re-weighs and non-default preview.

### F5 — Inspect and correct history

`Weight → compressed history row or Full logbook → month/raw entry → edit
sheet → Save changes`

`Logbook → drag and hold past delete threshold (or accessibility action) →
delete → inline Undo before timeout`

Review branches: empty history, multiple readings in one day, flagged and
edited rows, more than three months, Load earlier, edit validation, delete
failure, undo failure, TalkBack custom action, reduced motion and interrupted
gestures.

### F6 — Set or revise a goal

`More/Hub settings → Settings → Goals → choose loss/maintenance/gain → target
and pace/date → answer safety questions → inspect forecast → Save as new
version → Weight goal-progress card`

`Goals → Versions → Restore → goal progress and forecast refresh`

Review branches: no plan, missing profile/current weight/budget, invalid date,
safety-held math, developing forecast, held data quality, eligible bands,
maintenance and gain copy, completed/next milestones, unavailable dates and
version diff notices.

### F7 — Record body composition

`Weight → Body fat → choose Body fat % or Waist series → choose Navy/RFM →
enter tape values → Estimate → inspect provenance/uncertainty → Save
measurement → charts and ratios refresh`

`Ratios → Show BMI` is an explicit secondary disclosure.

Review branches: missing profile height/sex, female Navy hip input, invalid or
impossible tape data, no series, populated series, method disagreement,
save failure and whether the standalone legacy `f06/bodyfat` route should
remain discoverable at all.

### F8 — Import from Health Connect

`More → Data Vault → Health Connect → request read permission → Import now →
sync receipt → Weight/Body fat refresh`

Review branches: unavailable, provider update required, no access, partial
weight/body-fat access, denial, first full read, incremental update, source
record update/delete, empty import and sync error.

### F9 — Import an existing history file

`More → Data Vault → Import → choose file → map date/weight/body-fat columns →
Validate rows → staged report → Apply → Import complete → Weight`

Review branches: WLO bundle vs generic CSV, kg/lb source semantics, missing
date, skipped weak rows, duplicate records, mapping correction, apply failure
with and without recovery pending, and visible refresh after returning.

### F10 — Configure the daily ritual

`Hub gear or More → Settings → Weight unit` and
`Settings → Weigh-in reminder → notification permission → time chip`

Review the unit change across every existing and open surface, permission
denial/retry, disabled/enabled reminder, fixed-time choice discoverability,
notification copy and direct capture landing.

### F11 — Weight glance from the Hub

`Hub → morning weigh-in prompt` before a reading, or `Hub → trend card` after
data exists. The trend card opens Weight; its provenance opens an explainer;
an eligible goal can also render the forecast card.

Review day phases, ordering against food/check-in cards, hero vs non-hero
weight presentation, parity with F06's canonical number and units, and the
three-reading trend gate.

### F12 — Protect, export, restore, or restart

`Settings → App lock`, `More → Data Vault → Export/Backup/Restore`, and
`Data Vault → Fresh Start → confirmation → onboarding`.

These are supporting trust flows. Review app-lock return behavior around a
half-completed weigh-in, export scope/format clarity, restored unit/goal/history
parity, and the exact Fresh Start contract. The current Fresh Start UI is a
manual destructive workflow and must not be confused with the canceled
automatic returning-user prompt.

## 5. State checklist for visual and interaction review

- Shell: fresh, onboarded compact, onboarded expanded, nested with Up, deep
  link from cold start, app-lock gate.
- Weight loading: loading, empty, content, retained content with refresh error,
  retry success/failure.
- Trend: absent, 1 reading, 2 readings, established, lapsed, empty window,
  wider-window suggestion, full 30-day change, each range and each smoother.
- Entry: empty/prefilled, kg/lb, valid/invalid number, valid/invalid/future
  timestamp, saving, saved, failed, keyboard visible, date dialog, time dialog.
- Confirmation: sparse and established; with and without 7-day delta; long
  source labels; TalkBack announcement; haptic consumed after recomposition.
- Outlier: keep, correct, corrected resave and navigation away mid-decision.
- Goal progress: no goal, unavailable, trend forming, loss, maintenance, gain,
  completed rung, dated rung, undated rung.
- Forecast: missing inputs, withheld, held, developing, available, loss and
  gain, narrow and wide horizons.
- History/logbook: empty, same-day multiples, flagged, edited, long month,
  paging, edit errors, delete armed/canceled/committed, undo active/expired/
  failed.
- Body composition: no profile facts, no measurements, Navy/RFM, female/male/
  undisclosed, estimate error/result/saved, waist/body-fat charts, missing and
  present ratios, BMI hidden/revealed.
- Settings/import: unit switch, reminder permission denied/granted, Health
  Connect unavailable/update/no/partial/full access, sync result/error, CSV
  pick/map/report/apply/done/fail.
- Accessibility/adaptation: font scaling, TalkBack reading/order/actions,
  switch access, contrast, reduced motion, landscape, split screen, 600 dp+
  navigation rail, IME overlap and touch-target sizes.

## 6. Known scope and contract flags to resolve during the review

These are inventory warnings, not completed UX findings:

1. The implementation and docs still contain contradictory daily-weight copy:
   the frozen Release 1 policy is the consistent-time-window scalar, while the
   logbook and math UI still say lowest-of-day. This affects W02, W07, W08 and
   W09 and must be settled before judging the wording or chart semantics.
2. Body fat is specified as a segment of Weight, but an internal standalone
   `f06/bodyfat` route and screen remain. Treat it as a legacy/diagnostic
   surface until its necessity is decided.
3. Weight-first onboarding offers file import and Health Connect “setup later”
   choices, while the operational flows live in Data Vault. Review the handoff
   and expectation-setting as one flow, not as separate features.
4. Goals, Profile and Health Connect are several navigation levels away from
   the primary Weight surface. Their discoverability is part of the weight UX,
   even though their code lives in F01, app Settings and F13.
5. The manual Data Vault Fresh Start implementation needs a contract review of
   exactly what is hidden, archived, retained and reversible. The automatic
   ≥14-day Fresh Start prompt was canceled and is not in scope.

## 7. Explicit non-surfaces

Do not spend review time looking for these; they are not current product UI:

- Discreet/tap-to-reveal weight mode — canceled.
- Automatic returning-user Fresh Start prompt — canceled.
- Home-screen weight widget — canceled.
- Scale-display OCR — canceled.
- Weighing-consistency analytics — canceled.
- Optional 10-day-best headline — canceled.
- 30-day progress ribbon — canceled and replaced by neutral 30-day trend text.
- Custom EAV metric creation/chart UI — not present in the current UI.
- Weight/circumference correlation view — deferred.
- Bluetooth scale pairing/driver UI — not present in the current UI.
- Doctor-summary PDF surface — not part of the current core review flow.
- Milestone celebration/share card and full Insights weight experience — not
  present; Insights is currently a placeholder.

