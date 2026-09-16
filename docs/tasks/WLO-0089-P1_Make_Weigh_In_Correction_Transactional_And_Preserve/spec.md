# WLO-0089 — Transactional weigh-in correction and continuous feedback

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A02/A06: correcting a flagged reading must never delete it before a replacement successfully commits. A save receipt must not replace the Weight dashboard.

Start at `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt` (`CorrectFlagged`, submission state, reload and delete path), `ui/WeightScreen.kt` (capture sheet, confirmation and dashboard branch), and `core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt` (`replaceWeighIn`, `ReplacedWeighIn`, mutation probes). Extend `feature/f06-weight/src/test/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModelReliabilityTest.kt` and `core/data/src/jvmTest/kotlin/app/wlo/core/data/WeighInEditReplaceTest.kt`. Use existing atomic replace, not a UI delete+append sequence.

## Contract and ownership

This ticket owns capture/correction state and receipt placement. WLO-0092 owns the complete overview composition and consumes this state; agree on its receipt slot before editing the same screen. WLO-0088 may update repository internals but must preserve transactional replacement. It is possible to implement against the existing repository API independently; integrate/retest after WLO-0088.

Represent edit intent explicitly: `NewReading` or `CorrectReading(originalEventId, originalSnapshot)`; the snapshot contains original date/time, kg, note and source metadata needed for the UI. Draft text is separate from committed values. Use states Editing → Validating → Saving → Saved, with recoverable ValidationError/SaveError. A flag is an acknowledgement step, not an automatic deletion. Confirmation to keep an unusual but valid reading and correction remain available without lowering plausibility/safety checks.

## Required behavior

1. `CorrectFlagged` loads the original into a draft and opens the sheet. It performs zero persistence mutations. Cancel, Back, scrim dismissal and rotation while Editing leave the original intact. Restore draft/intent through `SavedStateHandle`; if the original was changed/deleted elsewhere, show a conflict and reload rather than append a duplicate.
2. Validate a frozen draft snapshot before saving. A correction calls `replaceWeighIn` exactly once. New capture calls append exactly once. Disable Save and draft-mutating controls while Saving; ignore repeated keyboard/button submissions. Dismissal/back while Saving must not masquerade as cancellation: keep the sheet until the bounded repository call resolves, with progress and an accessible status. A failed call returns to the same draft and announces a retryable error.
3. Replace must atomically write replacement+attrs, remove original and repair derived rows. On injected failure, original and all derivatives remain as before. Preserve intended metadata according to existing replacement semantics and visibly distinguish an edited manual value from a still-unaltered external reading. Never repurpose old IDs to conceal edits.
4. On successful save close the sheet, update data from the repository and show a compact receipt/snackbar naming saved value and timestamp. Keep trend, chart, period selection and scroll position mounted. A receipt is transient UI state, never the source of the data. A refresh failure after commit says “Saved; unable to refresh” with Retry; it must not encourage resaving the same input.
5. Success haptic occurs once after commit and respects WLO haptic settings. No haptic on validation failure/cancel or repeated recomposition. Use standard M3 sheet, labeled field, filled Save and text Cancel; native IME/insets/focus behavior. On close return focus to the action that opened the sheet; announce success once.
6. Process death must not blindly replay an in-flight save. On recreation reconcile committed result using an operation token persisted with the write, or an equivalent repository-supported idempotent key. Extend the repository only if required for this guarantee; a volatile boolean is insufficient for replay prevention. Preserve operation identity until commit/explicit discard; do not use value+date as a uniqueness key because legitimate repeated weigh-ins exist.

## Acceptance scenarios

- A89-01: start with one flagged 120 kg event. Open Correct, type 82, then Cancel/Back/scrim dismiss. Original count/value/attrs and trend remain unchanged for each path.
- A89-02: correct successfully to 82. Exactly one replacement remains, original is removed, derivatives match a clean recomputation; receipt is visible with the chart still mounted and the prior 30-day period retained.
- A89-03: fail at each repository mutation probe (raw write, attrs, original deletion, trend/projection rebuild). The transaction rolls back, draft=82 remains and Retry yields one replacement.
- A89-04: block repository response, tap Save twice and press IME Done. One call is issued; fields cannot change the in-flight value. Resolve success: one receipt/announcement/haptic.
- A89-05: rotate in Editing and Saving; recreate after commit but before receipt delivery. Original intent/draft is restored appropriately and no automatic duplicate is written. Explicit second new capture is still allowed.
- A89-06: a committed write followed by read failure is reported as saved with refresh Retry; Retry rereads and does not append. Empty/forming/held dashboards remain visible behind the receipt.

## Validation

Use fake dispatchers/deferred repository calls for races, real Room tests for rollback, and an instrumentation test extending `app/src/androidTest/kotlin/app/wlo/app/M3WeighInTest.kt` for sheet focus, state continuity and Back. Test both kg/lb and comma decimal parsing through the existing input parser. No unrelated formula, navigation or goal change.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f06-weight:testDebugUnitTest :core:data:jvmTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.M3WeighInTest
```
