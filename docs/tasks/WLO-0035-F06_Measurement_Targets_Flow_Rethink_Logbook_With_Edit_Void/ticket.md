---
id: WLO-0035
title: 'F06 measurement + targets flow rethink: logbook with edit/void, back-dating, full-depth chart windows, body-fat as a series, goals/profile editor after onboarding'
status: done
theme:
release:
created: 2026-09-14T12:57:04Z
modified: 2026-09-14T15:03:09Z
closed: 2026-09-14T15:03:09Z
revision: a952243640a47213
blocks: []
related: []
---

# Description

# Goal

Owner dogfooding review (2026-09-14): the measure-and-targets entry flow needs a rethink, not point fixes. Every reported gap was verified in code. Two of them are live data-integrity hazards, not just missing UI. Owner rulings are in (see "Rulings" — all three questions answered + resolved same day).

# Verified findings (owner-reported, confirmed)

1. **No goal/preference adjustment after the wizard.** `ProfileRepository` (core/data Spine.kt) has create/archive/setUnitPreference only — no update of sex/birthYear/height/activityLevel. Goals live in the versioned Targets store, whose only writers are STUDIO_F01 (v1 at onboarding) and APPLY_F07 (R-B2); the F01 plan-studio edit surface is unbuilt (F03Routes.STUDIO maps to the plain planner tab). A mistyped height permanently skews RFM/Navy/BMR math.
2. **Weight screen shows today only.** `WeightScreen` day card renders `dayWeighIns(profileId, today)`; there is no logbook. The F06 spec (§5) and DESIGN-SYSTEM inventory already prescribe the raw logbook with provenance column and inline edit — unbuilt.
3. **Chart pinned to 90 days.** `WeighInViewModel.CHART_WINDOW_DAYS = 90`, header literally "90 days". Spec/design say 30d/90d/1y/all. Store supports arbitrary ranges (`dailyScalars(from, to)`); UI-only constant.
4. **No delete/correct of a wrong entry — data hazard.** Nothing above the append-only store offers edit, delete, or void. The outlier "Correct" flow (`CorrectFlagged`) only reopens the sheet prefilled; the flagged event stays AND still wins lowest-of-day (`dailyScalars` takes minOf all events, flagged or not — "projections still count it"). One fat-fingered 5.2-instead-of-95.2 poisons the daily scalar, the trend, and the F07 engine until Fresh Start. The spec's promise "an admitted typo is fixed, not judged" (F06 §4) is currently unimplementable.
5. **No back-dating.** The weigh-in sheet (`SheetUi`) carries only weightText; Save hardcodes today (`DayBoundary.epochDay(clock.now())`). Spec: number pad "back-datable" + long-press chart back-fill. The store takes arbitrary dayEpochDay/capturedAt — UI-only gap.
6. **Body fat is a one-shot calculator, not a series.** `BodyFatScreen` computes an estimate and can append one BODY_FAT event. Nothing anywhere reads BODY_FAT back — no history, no chart. The method chip is session-local UI state; no persisted headline method (spec: "the user picks a headline method; all are chartable together"). Method is actually a per-entry attribute (it already rides the event as an EAV attr).
7. **Tape inputs are discarded.** Waist/neck/hip are read once to compute the estimate, then dropped — never saved as girth events. This kills the spec's flagship "weight flat but jeans fit" story (girth series are core metrics per F06 §3) and forces re-typing the tape every month. Owner: recording measurements matters for motivation.
8. **Body-fat entry point buried.** The only route in is a "Body-fat methods" secondary button at the bottom of the "Today's weigh-ins" card — discovered by nobody, semantically inside the wrong card.

# Owner rulings (WLO-0035 questions, answered + resolved 2026-09-14)

- **R1 · Delete (q-000031):** HARD DELETE, no tombstone/void ceremony — "Why do we need an audit trail for a weight monitoring app? Let the user delete." R-B8 amended accordingly (see FEATURES.md §3): the verbatim/never-overwritten rule governs ingestion and automatic processing; a user-initiated delete of their own entry is an explicit act, not silent collapsing.
- **R2 · Body surface (q-000032):** weight and body-fat live on the SAME screen, as different series (no separate Body tab/route). Tape measurements are recorded as girth events every time. Design forward-compat for BF from a connected scale (F13 impedance decode → per-method series, already F06 §3's registry shape).
- **R3 · Goals writer (q-000033):** writer-id taxonomy is a non-question — reuse STUDIO_F01 for post-wizard goal edits. The ledger already records what changed and when; no third writer id, no provenance-of-screen tracking.

# Plan (four work items, ruling-shaped)

## W1 · Delete door + scalar exclusion (smallest, stops active data poisoning)
- `MeasurementRepository.delete(eventId)` (+ WeighInRepository passthrough or fold into weight flows); Room: hard row delete, cascade attrs, refresh day projection.
- Exclude deleted events everywhere by construction (they're gone): daily scalars, trend, BODY_FAT series, exports.
- Outlier flow becomes honest: Keep / **Delete** — "Correct" = delete + sheet reopens with a sane prefill (last good reading, NOT the bad value, which is today's behavior).
- Logbook rows get a delete affordance with a one-tap undo notice (in-memory undo, no trash table — R1 says no audit machinery).
- Delete removes the "flagged — kept" counting hazard entirely.

## W2 · History-first weight surface
- Logbook card: all weigh-in events (scrollable, day-grouped, newest first), provenance/flag marks, tap → detail (value, time, source, flag) with delete; today's card becomes the logbook's head.
- Chart window selector 30d/90d/1y/all (DESIGN-SYSTEM inventory); header stops lying "90 days".
- Weigh-in sheet gains a date field (default today, picker limited to past) + optional time; long-press chart back-fill per F06 §4.

## W3 · Same-screen body composition (R2)
- The weight surface gains a series switcher (Weight / Body-fat / girths), one shared chart + window control; BF series per method with the method shown per point; persisted headline-method preference (profile or settings store).
- Tape entry (waist/neck/hip first, spec's 10 sites eventually): saves MEASURED girth events; Navy/RFM calculator prefills from the latest tape and saves its result as an ESTIMATED BODY_FAT event with method attr — tape and estimate both persist.
- Kill the "Body-fat methods" button in the weigh-ins card; BF connected-scale input is a F13 concern later (R2 forward-compat note only).

## W4 · Goals + profile facts editor (R3)
- Goals editor surface: edits goal weight/pace/target-date → new Targets version via STUDIO_F01 (R3: no new writer), diff ribbon from `TargetsWriteOutcome.Written.diff`, history/revert already implemented. Wizard = first run of the same editor; optional "adjust plan" entry from Settings.
- `ProfileRepository` update door for sex/birthYear/height/activityLevel + Settings→Profile section.

# Sequencing

W1 (poisoning stops) → W2 (biggest daily value) → W3 → W4. Each independently shippable.

# Spec/doc deltas (done with the rulings)

- FEATURES.md R-B8 amended: user-initiated delete is out of R-B8's scope (ingestion semantics only).
- F06 §3/§4/§5 lines updated: delete door named, outlier flow wording, logbook delete, same-screen series note.

## References

- Spec: [F06 §3](docs/features/F06-weight-body-metrics.md) (import semantics + body-fat method registry) · §4 (outlier guard, back-fill, entry model) · §5 (logbook, charts)
- Rulings: [FEATURES.md §3](docs/features/FEATURES.md) R-B2 (targets writers) · R-B8 + 2026-09-14 amendment (user-initiated delete)
- Design: [DESIGN-SYSTEM.md F06 inventory](docs/design/DESIGN-SYSTEM.md) (logbook, 30d/90d/1y/all chart, back-datable pad, BF registry, girth entry)
- Mockup: [flows/04-weigh-in-trend-f06-f08.html](docs/design/flows/04-weigh-in-trend-f06-f08.html) — weigh-in moment + trend-history frames (logbook, ribbon, 10-day-best, body-fat)
- Store: [TargetsRepository.kt](core/data/src/commonMain/kotlin/app/wlo/core/data/TargetsRepository.kt) (versioned writes, diff ribbon — W4's door already built)
