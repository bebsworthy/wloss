# WLO-0098 — Accessible chart exploration and purposeful motion

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A03/A17: charts must answer questions for touch, keyboard and screen-reader users, identify the actual daily selection/method, and use motion to explain changes without altering numbers or recovery timing.

Read `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/{WloTrendChart,ChartPoint,WloForecastBands,WloMotion,WloHaptics}.kt`, `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt`, its ViewModel/read models and existing `TrendChartGeometryTest`/`ForecastTimeWindowTest`. The Canvas chart is a valid custom rendering need; document its semantics/interaction limitation and compose standard controls around it. Reconcile design docs mentioning Vico with the actual retained Canvas implementation; this ticket does not require replacing the renderer.

## Dependencies and ownership

Integrate WLO-0088 daily selection/provenance, WLO-0090 method-aware body data, WLO-0091 tokens and WLO-0092 hierarchy. Own chart selection, accessible alternate representation and weight-specific motion/haptic bindings. WLO-0054 owns smoothing control collapse/reset/preview decisions, including its open question. Keep canonical hero/derived values unaffected by temporary tuner previews and do not resolve that ticket silently.

## Chart data and interaction contract

1. Use a typed point model with stable key, epoch-day/capture time, canonical value and display unit, series role (raw/daily/trend/forecast/body method), source event IDs and provenance/hold reason. Do not label daily-selected scalars as every raw weigh-in. A median derived from two events must expose both contributors. Missing days are missing, not zero. Different body methods are named separate/segmented series, with no misleading interpolated line across methods.
2. A tap selects the closest rendered sample in time within the chart's plot region, with deterministic earlier-point tie-breaking. Selection highlights the sample and renders a persistent adjacent detail region: date, value/unit, series/method, status, source count and “Explain”. Do not require sustained touch or a tooltip under the finger. A drag may scrub only after horizontal intent; ordinary vertical scrolling and Back remain available. Selection never mutates measurements.
3. Provide visible “View data” action opening a standard sheet/list for the current period. Rows expose date, daily value, trend when available, status/method and explanation; include a route to raw readings. TalkBack need not traverse thousands of invisible Canvas nodes. The chart itself has a concise summary with period, available-data count and meaningful endpoints, not a wall of repeated values.
4. Provide accessible Previous/Next sample IconButtons with names and disabled-at-boundary semantics. Keyboard Left/Right changes sample while focused; Home/End reaches boundaries; Enter opens explanation. Selected detail is announced once per completed selection, throttled during dragging. Do not steal focus on background data refresh. After a period change, retain selected key if present; otherwise clear it and announce the new period rather than selecting an unrelated point silently.
5. Render measured points, trend and forecast with non-color distinctions (point/line style, labels/legend); use semantic contrast-compliant colors from WLO-0091. Forecast bands identify uncertainty and do not promise a precise date. Held/missing forecast has explanatory text; never draw a zero-width “certain” band as fallback. Out-of-range/degenerate geometry must remain finite for 0, 1, equal-value and sparse samples.

## Motion/haptic contract

Create one documented table in DESIGN-SYSTEM with trigger, visual transition, duration policy, haptic and reduced-motion behavior. Use native M3 ripple/sheet/dialog behavior first. For project-owned transitions, target roughly 150–200ms for small selection/content changes, using installed theme/motion tokens; this is a project tuning range, not a claimed universal M3 requirement.

- Capture/save: native sheet transition; one success haptic after committed write, owned by WLO-0089. Never count up saved/trend weight or replay success on recomposition.
- Section/period: short content/indicator transition preserving selected values and stable layout; disabled animation scale applies the final state immediately.
- Milestone disclosure: bounded size transition without jumping scroll/focus; reduced motion changes state directly.
- Chart selection: small indicator movement only; no continuous haptic scrubbing, no chart entrance animation that invents intermediate data.
- Delete/Undo: row placement may animate, but transaction and accessibility timeout are independent of animation and owned by WLO-0094.

Respect Android animation duration scale and WLO haptics preference. No pulsating held state, confetti for weight loss, looping attention animation or daily success animation dependent on a lower number. Handle cancellation/interruption by resolving to the current state, never replaying a stale transition.

## Acceptance scenarios

- A98-01: fixture with two raw readings and a median daily sample shows the correct series label/contributing sources in detail and View data. Chart and accessible list give the same values and period.
- A98-02: a keyboard/TalkBack user reaches every sample through controls/list, opens explanation, and returns without losing selection. Boundary controls are disabled and clearly named.
- A98-03: vertical scrolling initiated over the chart still scrolls; horizontal exploration selects deterministically; an equal-distance tap picks the earlier point. Selection survives resize when its key exists.
- A98-04: change period excluding the selected point: selection clears honestly. Empty, one-point, constant, sparse and mixed-method data never crashes or creates NaN geometry.
- A98-05: grayscale/high-contrast inspection still distinguishes measured/trend/forecast; light/dark legend/detail text pass WLO-0091 thresholds. 200% font detail/list remains readable.
- A98-06: animation scale 0 yields correct final UI immediately; 1× animation stays interruptible; haptics off yields no vibration. Saving, Undo time and canonical numeric values are identical in both modes.
- A98-07: record a 60-second representative chart/scroll/section-switch session on the named emulator/device using frame timing. No repeated main-thread computation per draw or unbounded recomposition. Report frame counts/jank versus the prechange same-fixture baseline; a regression requires analysis/fix, not an unsupported “smooth” claim.

## Validation

Add pure geometry/selection tests plus Compose semantics/keyboard tests and accessible-list parity assertions. Record normal/reduced-motion videos and manual TalkBack exploration. Physical haptics remain a separate device check. Profile only synthetic data; no renderer/library migration, engine tuning or new goal forecast formula.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :core:designsystem:testDebugUnitTest :feature:f06-weight:testDebugUnitTest :app:testDebugUnitTest
```
