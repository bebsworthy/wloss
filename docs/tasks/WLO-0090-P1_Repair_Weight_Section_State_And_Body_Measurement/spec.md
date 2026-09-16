# WLO-0090 — Immediate section state and trustworthy body measurements

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A04/A12: Weight/Body fat selection must update on the first tap without reloading. A body calculation must never save a stale estimate or a partial measurement set, and plotted values must retain method provenance.

Read F06's body-method registry, then `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/{WeighInViewModel,BodyFatViewModel}.kt`, `ui/{WeightScreen,BodyFatScreen}.kt`, `core/data/src/commonMain/kotlin/app/wlo/core/data/{Spine,RoomRepositories}.kt`, `core/model/src/commonMain/kotlin/app/wlo/core/model/Measurement.kt`, and `core/common/src/commonMain/kotlin/app/wlo/core/common/Units.kt`. Current section flow is omitted from the exposed combined state. Current body save appends tapes/estimate/attrs separately, and method/tape edits can retain the previous estimate.

Own the body domain write/read contract and section state. WLO-0091 supplies controls; WLO-0092 places them; WLO-0098 consumes method-aware chart data. Do not make F06 depend on F01 or call Room from a Composable.

## Required behavior and data contract

1. Derive one screen UI state by combining source data, selected section, selected period and relevant local state. `SectionChange` updates immediately; no repository round-trip is required. Preserve section across rotation/back. Use M3 single-choice segmented buttons with selected semantics for Weight/Body fat. Selection is not an action chip. Do not produce a blank transient frame while switching.
2. Give Body fat its own visible period selector, default 90 days. Remember Weight and Body fat periods independently in saved screen state; changing one cannot silently filter the other. Empty Body fat offers “Add body measurement” and explains accepted methods, not an empty unexplained axis.
3. Represent calculator inputs and result as a revisioned snapshot. Any formula-relevant edit (method, tape, sex/height source, units conversion affecting canonical input) invalidates the previous result. Display “Calculate” until recomputed; saving is disabled while invalid or busy. Preserve raw draft strings for editable validation; convert to canonical cm/kg once at the boundary using `Units`, not ad hoc multipliers. Select cm/in from the app's active unit setting consistently and label every field; unit switching preserves physical quantity, not text digits.
4. Add a repository operation for one body-measurement submission (proposed `saveBodyMeasurement(command)`): profile, captured timestamp/day, method ID/version, canonical inputs, estimate, notes and idempotency token. Persist tape events, estimate and all method/metric attrs in one Room write transaction. Invalid inputs or any write failure produce zero rows. Keep existing engines and validation rules; no new formula or medical inference. Return committed event IDs and a typed failure. A repeated token returns the same result without duplicate rows; a new deliberate submission may produce another reading.
5. Method/source must survive every read and export. Introduce a body chart point/read model with value, timestamp, event ID, method ID, source and explanation inputs. Manual/unknown legacy body-fat events are explicitly labeled “Method not recorded”; never infer a method from value. Do not connect different methods as one comparable continuous series: segment the line by method or render separated named series, with accessible text explaining the difference. WLO-0098 handles selection rendering.
6. On successful save, the parent observes repository changes and refreshes without leaving/reentering the screen. Dismiss with a compact receipt; failed save keeps draft+result for Retry. Lock fields during save and announce one completion. Expose read loading, truly empty, failed/retry and successful states independently. SavedStateHandle restores draft; process recreation reconciles the operation token.

## Acceptance scenarios

- A90-01: tap Body fat then Weight on a static repository. The selected content changes each time without emission/reload/resume. Rotate with Body fat selected: it remains selected.
- A90-02: Weight=30d and Body fat=1y stay independent after switching sections, navigating to a sheet and returning.
- A90-03: calculate method A, edit waist, then attempt save: no write until Calculate is pressed again. Repeat for neck, hip, method and formula-relevant profile change. Result labels identify the current method.
- A90-04: 40 in is persisted as 101.6 cm within conversion tolerance (1e-6 cm); toggling cm/in does not repeatedly round stored canonical values. Blank/NaN/invalid draft cannot be saved; comma-decimal input works in a locale using it.
- A90-05: inject failure at each event/attr write. Row counts and attributes are unchanged. Two Save attempts with one token produce one logical measurement bundle; Retry after rollback succeeds.
- A90-06: seed adjacent body-fat estimates from two methods and an unknown legacy event. Chart/detail/list names methods and does not suggest an uninterrupted comparable trend. JSON export/restore retains metadata.
- A90-07: save in the body sheet, dismiss and observe the new point on the parent without navigation. A parent read failure offers Retry without duplicating the submission.

## Validation

Add ViewModel tests for combined selection and stale-result invalidation, repository transaction/idempotency tests, and Compose tests for labels, units, period controls, success/empty/error. Exercise 320dp width and 200% font: stack tape fields instead of squeezing three illegible columns. Maintain 48dp interactive targets, focus order and visible supporting errors.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f06-weight:testDebugUnitTest :core:data:jvmTest :core:database:jvmTest :core:vault:jvmTest
```
