# WLO-0067 — Weight-management product review

Date: 2026-09-15

## Product verdict

WLO's strongest product is not the full thirteen-feature health suite. It is a
private, explainable Android weight tracker: effortless capture, an emotionally
useful trend, and an honest goal forecast. The event-preserving data model,
canonical trend read, provenance rules, local-first posture, and range-based
forecast are unusually strong foundations.

The current experience does not yet expose that strength. Weight is an indirect
Hub destination, onboarding is a seven-step diet-planning funnel, and the live
Weight screen gives substantial space to an empty chart and smoothing controls
before the first weigh-in works reliably.

Recommended launch promise:

> The most trustworthy weight trend and goal forecast on Android — private,
> explainable, and free forever.

Food, exercise, planning, digestion, silhouettes, and generalized custom
metrics can improve that product later; they should not be launch dependencies.

## Evidence from the live app

API 29 review of the current debug build confirmed:

- Reaching the app requires progressing past goal, forecast, diet, adjustment,
  preference, schedule, and review steps. This is too much before the first
  weight entry.
- The Hub gives food and digestion the first actions; Weight is a tappable card,
  not a primary navigation destination.
- The nested Weight screen repeats the `Weight` title already supplied by the
  app bar.
- A new user sees an empty 90-day chart occupying most of the screen, followed
  by three smoothing choices and an alpha slider. The primary information need
  is instead a clear first-weigh-in state.
- The weigh-in sheet is an empty kilogram field plus editable ISO date and time
  strings. It does not match the prototype's fast prefilled entry or the stated
  zero-typing common case.

These observations agree with the existing WLO-0051 and WLO-0053 diagnoses and
with `WeightScreen.kt` / `WeighInViewModel.kt`.

## What is already architecturally sound

- Every weigh-in remains a source event; daily scalar and trend values are
  derived views. Atomic mutation and suffix repair are centralized in
  `WeighInRepository`.
- Hub and F06 share the canonical `currentTrend` repository read.
- Cross-feature navigation remains in the app shell; F06 exposes routes rather
  than owning navigation.
- The custom trend chart is justified because Material 3 provides no chart.
  Surrounding controls generally compose standard Material 3 components.
- Local-first ownership, provenance, uncertainty, and shame-free copy are real
  differentiators and should remain non-negotiable.

## Product and specification corrections

1. Freeze a weight-first release contract. The master v1 currently spreads
   quality across all thirteen domains.
2. Make Weight a primary surface, or temporarily make the Hub the Weight home.
3. Reduce first-run to unit choice, optional goal, import choice, and the first
   weigh-in. Diet planning must be skippable as a later capability.
4. Decide whether launch supports loss only or also maintenance and gain.
   Current non-loss forecast behavior silently disappears; maintenance is part
   of credible long-term weight management.
5. Benchmark daily-scalar policies. Lowest-of-day plus explicit support for
   repeated attempts to “get the win” biases the signal. Compare first-of-day,
   consistent-time-window, median, and minimum rules against synthetic and
   dogfood series before treating minimum as truth.
6. Add forecast validation criteria before milestone dates or celebratory UI:
   holdout error, interval coverage, minimum data, missing-data behavior,
   revision stability, and published failure cases.
7. Correct the plateau claim. Flat trend at steady intake means expenditure is
   approximately intake over that window; it does not necessarily mean measured
   TDEE rose.
8. Add explicit safety eligibility for adults, pregnancy/breastfeeding,
   eating-disorder concerns, medically influenced weight, and unsafe requested
   rates.
9. Define Health Connect identity using source record ID, origin, client
   version, recording method, time, and zone offset. Minimum-of-day belongs in
   the derived view, never in import deduplication.
10. Reconcile stale docs: F06 still says profile-sticky kg/lb/st and v1
    multi-profile; flow 04 repeats the old unit rule; the objective contradicts
    the vector-only silhouette ruling; F06's trend gate is under reconsideration.

## Current ToDo critique and disposition

### Immediate hygiene

- **WLO-0066:** implement and close immediately. It is a small deterministic
  test fix and should not pollute later validation.
- Reconcile implemented tickets WLO-0050, WLO-0055, and WLO-0056 that still
  appear as doing before using board state for planning.

### P0 — make the core dependable

- **WLO-0052 — promote, amend, then implement.** Unit correctness is a body-data
  integrity issue. Include the Hub's current suffix-only lb bug. Choose one
  authoritative preference API and keep canonical SI storage. Resolve the
  ticket's conflict with the governing settings-driven length-unit ruling;
  avoid a risky storage migration solely for single-profile v1.
- **WLO-0051 — split into three tickets.** First: parsing, locale decimal,
  active-unit conversion, finite/range validation, inline errors, in-flight
  state, and duplicate-submit prevention. Second: prefill, Material 3 date/time
  pickers, IME/small-screen behavior, and accessibility. Third: post-save
  trend/raw confirmation and haptic. Defer live trend preview and typo heuristics
  until the simple flow is proven. Do not automatically open the keyboard when
  a valid prefilled value makes one-tap save possible.
- **Add outlier interaction to the P0 entry work.** The UI asks “Keep or
  correct?” but offers secondary Keep and primary Delete. Use Keep/Correct,
  make Keep primary, and let Correct perform delete-and-reopen.
- **WLO-0053 — keep P0, resolve questions, broaden tests.** Accept the proposed
  displayed-data trend gate and remove the redundant chart “latest” numeral.
  Cover both F06 and Hub plus one-point, constant-series, sparse, old-data,
  narrow-screen, and lb cases. Call it “Weight trend,” not bare “Trend.”
- **Create a weight-state reliability ticket.** Repository failures currently
  collapse into empty content. Add explicit loading/content/recoverable-error
  states, resume/midnight/timezone refresh, and deterministic ViewModel tests.
- **Promote WLO-0038 Health Connect to P0 after manual-entry correctness.** Test
  import/update/delete, retry, permission revocation, timezone changes, and
  cross-source duplicates. This is the highest-leverage Android zero-typing
  path.

### P1 — make the loop category-leading

- Create **goal-editor parity** work: reuse onboarding's goal controls and live
  forecast instead of the current primitive Settings form.
- **Split WLO-0045.** First ship current trend → goal, remaining amount,
  milestone ladder, range/insufficient-data states, and define whether crossings
  use canonical trend. Defer full-screen celebration, fired-marker persistence,
  and F11 event plumbing.
- **Split WLO-0042.** Ship a fixed-reference progress ribbon and carefully tested
  optional 10-day-best view separately. Do not let a low outlier become the
  default success signal. Move time-of-day quality into ingestion analysis;
  defer compare-smoothers overlay.
- Add actual-input explainers: included/excluded events, daily-scalar rule,
  smoother behavior, revisions, and uncertainty.
- Promote WLO-0037 scale-display OCR after Health Connect. Follow with the
  widget; adopt Bluetooth only for a deliberately supported device set.
- Run focused usability work at 200% font scale, with TalkBack, decimal-comma
  locales, one-handed use, long histories, sparse histories, lapses, and weight
  gain days. Measure time-to-log, correction rate, import conflicts, chart
  comprehension, and forecast calibration rather than engagement vanity.

### P2 / defer

- **WLO-0054 — simplify heavily.** Keep advanced smoothing behind a disclosure
  or math sheet, use a Material 3 single-choice pattern for methods, add Reset
  and honest preview labeling, and recompute from loaded samples/on release.
  Do not introduce a cache/dispatcher subsystem merely to support a continuously
  firing alpha slider.
- **WLO-0044 — move to v1.x/backlog.** A CUSTOM enum plus attributes is not yet
  a metric registry. Before revival, specify stable IDs, rename/archive
  semantics, dimensions, precision, validation, reserved names, and export
  column identity.
- Defer compare modes, elaborate celebration choreography, generalized body
  composition registries, and wider health-suite completion until the core loop
  earns retention.

## Implementation-shape guidance

- Keep the existing repository boundary and event model.
- Split the oversized `WeighInViewModel` through small pure presentation
  assemblers/state holders as features land; do not add a use-case framework.
- Query weight by kind for long windows rather than scanning all measurement
  kinds.
- Remove duplicate screen titles under the app-owned Material 3 top app bar.
- Use `WloSheet`'s title/pane semantics and standard Material 3 single-choice,
  picker, snackbar/live-region, and list patterns.
- Refresh flow 04 only after the release contract is frozen, covering first
  entry, confirmation, honest chart states, goal progress, compressed history,
  logbook/edit, and large-font behavior.

## Recommended execution order

1. WLO-0066 and ticket-state hygiene.
2. Freeze weight-first scope; reconcile governing docs and decide loss vs.
   maintenance/gain.
3. WLO-0052 end-to-end units.
4. WLO-0051 entry correctness plus outlier semantics.
5. WLO-0051 entry ergonomics and accessibility.
6. WLO-0053 truthful charts across F06 and Hub.
7. Weight-state reliability and ViewModel tests.
8. Post-save trend confirmation.
9. Health Connect ingestion contract and WLO-0038.
10. Daily-scalar benchmark and forecast calibration/safety gates.
11. Goal-editor parity and the useful half of WLO-0045.
12. Progress ribbon and optional 10-day-best from a split WLO-0042.
13. OCR, widget, then narrowly scoped Bluetooth.
14. Simplified advanced smoothing only after dogfood evidence.
15. Custom metrics and the broader health platform later.

The strategic correction is to spend less effort exposing machinery and more
effort proving that every captured weight, chart, trend, and forecast is honest.
