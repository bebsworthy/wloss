# WLO-0088 — One daily-weight contract and truthful derived values

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and scope

A01/A03: every consumer must agree on the daily input to the trend, while raw weigh-ins remain individually available. A displayed derived metric must show its number, unit, state and explanation. Current `dailyScalars` takes the minimum; F06 describes a consistent window; the benchmark is a recommendation, not the active contract. Resolve this discrepancy deliberately.

## Source map and ownership

- `core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt`: `dailyScalars`, `currentTrend`, replace/delete/restore and suffix rebuilds.
- `core/engines/src/commonTest/kotlin/app/wlo/core/engines/WeightPolicyBenchmarkTest.kt` and `docs/research/weight-policy-benchmark-v1.md`: candidate algorithm/fixtures.
- `core/model/src/commonMain/kotlin/app/wlo/core/model/Measurement.kt`; `core/database/src/commonMain/kotlin/app/wlo/core/database/{WloDatabase,Daos,Migrations}.kt`: raw events, metadata, migration.
- `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/{ProvenanceChip,WloStat,WloHeroStat,WloForecastCard}.kt`: number/status/explainer composition.
- F06 `ui/{WeightScreen,MathDocsScreen}.kt`, `state/{WeighInViewModel,LogbookViewModel}.kt`; F01 `ui/GoalsEditorScreen.kt`; Hub consumers found with `rg 'dailyScalars|currentTrend|lowestOfDay|ProvenanceChip'`.

Own the daily-selection domain contract, recalculation, and the missing-number/explainer fixes. WLO-0091 owns general style; WLO-0098 owns chart interaction. WLO-0095 integrates new metadata into portable backups; coordinate the serialization contract before merging. No smoothing-tuner redesign: WLO-0054 owns it, and R-A2 remains visible until explicitly changed.

## Required design and algorithm

1. Amend FEATURES R-B5 and F06's daily policy together, documenting a versioned `consistent-window-v1` policy. Keep canonical EWMA alpha 0.15 and existing engine formulas. For each stored `dayEpochDay`, choose a reading in [05:30,09:30) nearest 07:00. Tie-break by local minute, then captured instant, then stable event ID. If none is in-window, use the median of all valid readings that day; even cardinality averages the middle two. Empty days have no sample, never zero or interpolation. Values must be finite, positive and in canonical kg before reduction. Surface invalid legacy inputs as excluded with a reason rather than silently accepting them.
2. Add a pure reducer with a typed result (proposed name `DailyWeightSelection`): day, kg, contributing event IDs, candidate count, reason (window/median), policy version and timezone. Median attribution names one middle event or both averaged events. Do not attach a synthetic median to one unrelated event. Keep `lowestOfDay` explicitly legacy if still needed; no canonical consumer may call it to select trend inputs.
3. Persist a fixed policy timezone per profile, captured from the device timezone once at profile creation/upgrade. Preserve existing day buckets; derive window minutes from capture instant in that saved timezone. Travel must not silently rewrite history. Document that preexisting timestamps without original zone are interpreted using the upgrade zone; show it in the explainer. Changes to this policy are versioned migrations, not inference from recent readings.
4. Add the next available Room schema migration, including policy metadata. Rebuild derived rows from raw events in a transaction or resumable, explicitly gated rebuild; never expose a mixture of policy versions. On failure retain the old complete version and offer Retry. Raw event IDs, timestamps, notes, sources and attrs must remain unchanged. Migration is idempotent. Show a dismissible once-per-policy-update notice explaining why historical trends may change, not a weight-change celebration.
5. Make overview, logbook aggregates, Hub, goal progress, forecasts, Math docs and derived export rows use this result. Raw export remains event-level. Update benchmark/forecast regression fixtures with an explicit old/new explanation; do not bless a failing safety test by changing its expectation without analysis.
6. Render a visible formatted value independently of `ProvenanceChip`, which is status only. Reuse/extend `WloStat` or `WloStatRow`; avoid repeating the number in a second decorative chip. An info affordance always opens a real explanation containing formula/policy version, dates/source inputs and hold/assumption reason. If no explanation exists, omit the affordance. No invented zero for missing data. Label a profile starting weight as such, never “Current trend”. Daily chart points are named “Daily selected weight”; full raw readings belong to drill-down.

## Acceptance scenarios

- A88-01: in one day, 07:00=80 kg and 18:00=79 kg produces daily 80, not 79, in every listed consumer. Both raw events remain in logbook/export.
- A88-02: only 12:00=80 and 18:00=82 produces 81 with both event IDs; a third 20:00=90 produces 82 with the middle event ID. Input permutations give identical results.
- A88-03: 06:45=80 and 07:15=81 selects 80. 05:30 is included and 09:30 excluded. Same-minute ties obey instant then ID. Change device timezone after policy creation: daily values and attribution do not change.
- A88-04: the upgraded database preserves raw row/attribute counts and content hashes; rerunning initialization changes nothing. Inject a rebuild failure and observe either entirely old or entirely new derived policy, never mixed rows.
- A88-05: replacement, deletion and Undo recompute from the earliest affected day and preserve earlier results. Existing `WeighInIntegrityTest`, edit/delete and `CurrentTrendTest` remain valid for their stated policy.
- A88-06: Goals current trend, BMI/ratios and forecasts show actual values with units, or an explicit held/unavailable state. TalkBack reads label/value/state once and can reach a working explanation. Confirmation does not remove provenance from the dashboard.
- A88-07: JSON backup/restore round-trip preserves timezone/version/attribution semantics. Legacy backup absence has one documented default/migration path. WLO-0095 must pass integration before release.

## Validation

Add pure reducer tests for boundaries, missing days, invalid values and permutation stability; Room migration/rollback tests; cross-consumer fixture assertions; Compose tests for numeric text and explainer action. Use a fixed clock/timezone and synthetic IDs. Do not assert only that a helper was called.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :core:engines:jvmTest :core:data:jvmTest :core:database:jvmTest :core:vault:jvmTest :core:designsystem:testDebugUnitTest :feature:f06-weight:testDebugUnitTest :feature:f01-onboarding:testDebugUnitTest
```
