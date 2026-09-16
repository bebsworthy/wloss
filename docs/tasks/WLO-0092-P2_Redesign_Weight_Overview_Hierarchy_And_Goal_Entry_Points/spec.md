# WLO-0092 — A clear Weight overview and direct goal/history entry points

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A06/A08: answer “Where am I, how is it changing, what can I do next?” before advanced analysis. The chart and logging action must remain easy to find, including after saving.

Work in `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt` (`WeightTrendCard`, `WeighInConfirmationCard`, `GoalProgressCard`, `HistoryRow` and `WeighInSheetContent`), `state/{WeighInViewModel,GoalProgressLoader}.kt`, `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/F06Routes.kt`, and app routing in `app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt`. Shared components live in `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/`. Read the review's proposed overview hierarchy and existing `docs/design/flows/04*` prototype as context, not a pixel target.

## Dependencies and ownership

Integrate after WLO-0091 primitives and WLO-0089 stable receipt state; consume WLO-0088 canonical values. WLO-0090 owns section/body state, WLO-0093 the Goals destination, WLO-0094 history filter semantics, WLO-0096 shell/panes and WLO-0098 chart exploration. This ticket owns compact overview composition and feature-level callbacks, not a second implementation of these functions. Publish callback payloads before editing app routing.

## Required hierarchy

The compact screen uses one vertically scrollable content region, with saved list state. In order:

1. One app-bar title supplied by the shell contract; no duplicate large “Weight” heading below it. Weight/Body fat single-choice segmented control follows.
2. Trend hero: explicit “Weight trend”, visible value/unit, measured/forming/held status and working explanation. Latest raw reading is subordinate and labeled, not substituted for trend. Show one selected comparison interval (default 7 days), with signed delta and period. Never color loss universally good or gain bad.
3. Visible period selector (30d/90d/1y/all or the existing supported equivalents), default 90d, then chart. Keep period after save, sheet close and back navigation. The chart appears before the full goal ladder and history content. At 360×800dp, default font and seeded ordinary data, the chart begins within the initial viewport below system/app/navigation bars; at large fonts preserve order/legibility rather than force this viewport target.
4. Compact goal summary: target with unit, mode, one next meaningful milestone and a clearly labeled “Edit goal”. With no goal: neutral explanation and “Set a goal” opening the same editor in creation mode. Held forecasts show the reason/action; no fake ETA, zero or unlabeled profile-weight fallback. Expand “Milestones” explicitly for the complete ladder; remember expansion in screen state.
5. Recent/history summary with a labeled “View logbook”. History buckets carry their time range into the destination. Keep remaining existing educational/advanced content later in the scroll order. R-A2's smoothing tuner stays visible as required until WLO-0054 resolves its separate contract; do not silently collapse/remove it here.

Use one primary “Weigh in” ExtendedFloatingActionButton in the Scaffold FAB slot above bottom navigation/insets. In constrained height/large text where it obscures chart controls or content, use a full-width filled action in a dedicated bottom action region instead; never render both simultaneously. The last scroll item must clear that region. Hide/disable duplicate capture triggers in the same viewport; historic deep links may still open capture directly.

## Interaction and route contract

Use callbacks, not cross-feature imports: proposed `onEditGoal()`, `onOpenLogbook(range: HistoryRange?)`, `onExplain(metricId, sourceIds)`. `HistoryRange` uses inclusive start epoch-day and exclusive end epoch-day, nullable for all history. A month such as September 2026 passes [2026-09-01,2026-10-01); a week passes its actual displayed boundaries. The logbook title names the filter and provides Clear filter. Preserve existing unfiltered route/deep links. WLO-0094 consumes the range; WLO-0096 owns route encoding and Back restoration.

Compose the dashboard independently of receipt presence. Use static values on entry; no count-up numbers. Native ripple/selection feedback is sufficient; WLO-0098 owns additional transitions. Empty: concise explanation plus capture/import entry. Loading: stable placeholder hierarchy without manufactured data. Error: retain last valid content when available, mark it as stale with Retry. Section-specific failure must not erase unrelated usable data.

## Acceptance scenarios

- A92-01: seeded history and active goal show hero → period/chart → compact goal → history in semantic/visual order. The full ladder does not precede the chart.
- A92-02: no goal “Set a goal” opens creation; active goal “Edit goal” opens existing values. Return preserves scroll/section/period. Safety-held and maintenance goals have coherent summaries without loss-specific copy.
- A92-03: tap September history bucket: destination shows only its half-open range, title and Clear filter. Return restores the exact originating overview state.
- A92-04: after save, the chart and provenance remain mounted, no full-screen confirmation replaces them, and the selected period remains unchanged.
- A92-05: at 320dp/200% font and with IME open in capture, every field/action is reachable, FAB/action region never hides content, and exactly one primary capture action is present.
- A92-06: loading, empty, failed read, forming trend, held forecast, loss, maintenance and gain screenshots/semantics demonstrate truthful states with no fabricated value/date.

## Validation

Extend ViewModel state tests and `M3WeighInTest` for hierarchy/actions; add route-payload tests for range boundaries. Record compact screenshots and a save→receipt→edit-goal→Back recording. Do not change formulas, target writes, chart selection or top-level destination identities here.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f06-weight:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.M3WeighInTest
```
