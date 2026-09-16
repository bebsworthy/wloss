# WLO-0094 — Accessible logbook editing, deletion and reliable Undo

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A11: all logbook actions must be discoverable without a swipe, edits must be safe under repeated input, and deletion must have one trustworthy recovery mechanism. Empty data, loading and read failure must be distinguishable.

Read `feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/{ui/LogbookScreen.kt,state/LogbookViewModel.kt}`, `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloSwipeRevealRow.kt`, and `core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt`. Current Undo has an in-memory snapshot with a 4500ms delay and a separate visual timer; month-top placement can be offscreen. Existing repository delete/restore tests and `WeightHistoryTest` are regression starting points.

## Scope and dependencies

Own logbook state, row actions, edit sheet, date-range filtering and Undo orchestration. Consume WLO-0088 daily/provenance read model and WLO-0091 M3 primitives. WLO-0092 supplies history range; WLO-0096 encodes it in routes and owns adaptive list/detail. Preserve the existing custom release-gated, velocity-blind swipe implementation and its documented exception; do not replace it with velocity-based stock dismissal simply to claim M3 compliance.

## Required behavior

1. Rows use standard ListItem slots inside the approved reveal wrapper. Visible content names value/unit, timestamp, source and edited/provenance state as applicable. A trailing IconButton “Actions for [date/time/value]” opens a standard DropdownMenu with Edit, Explain and Delete. This visible path, TalkBack custom actions and swipe all dispatch the same commands; no gesture-only capability.
2. Keep release-gated swipe behavior: reaching the existing 90% of 112dp reveal distance only commits on release; speed alone cannot delete. Distinguish revealing the Delete affordance from committing delete. Update KDoc/tests if layout metrics change; this ticket does not authorize new thresholds.
3. Editing reuses validated date/time/unit parsing and the repository's atomic replacement. Freeze draft and ignore duplicate Save while busy. Cancel leaves original untouched; failure retains draft. Handle stale/missing event with an explicit conflict/reload. Reuse WLO-0089 operation-state principles rather than delete+append.
4. Render Loading, Ready, Empty, FilteredEmpty and Error separately. Retry retains filter and last known rows if any. Accept `HistoryRange(startInclusive,endExclusive)` from the overview; apply it to both rows and displayed aggregates. The filter title names the date range and exposes Clear filter. Daily/month summary uses the canonical selection, not a fresh minimum implementation.
5. Replace per-row/month Undo with a Scaffold-level SnackbarHost reachable above navigation/IME. One pending deletion at a time. Other deletions are disabled while this receipt is pending; editing/reading unrelated rows remains possible. An explicit Dismiss/expiry finalizes it, Undo restores the complete snapshot and derivatives. Queueing several destructive operations without an intelligible recovery model is out of scope.
6. Use one deadline/state authority for repository snapshot and visual lifetime. Ask Android accessibility timeout recommendations for the baseline 4500ms (contains text and controls); do not run a separate animation delay. Remove the shrinking countdown bar; receipt text and Undo remain stable. A zero animation duration does not shorten recovery. App background time does not consume the foreground Undo opportunity.
7. Persist the pending-deletion recovery record (snapshot including attrs, operation ID and remaining foreground duration) in app-private storage before committing deletion, ideally in the same Room transaction. Reconcile after process recreation; never show Undo when its data is gone. Undo is idempotent and clears recovery only after successful restoration. On restoration failure retain the record and offer Retry; on expired/finalized delete remove it. Internal temporary recovery data must be documented and excluded from ordinary event export. Do not turn it into indefinite deleted-health-data retention.
8. Announce deletion/restore once. On deletion focus moves to the next row or empty-state heading; Snackbar Undo is independently reachable. After Undo restore focus to the recovered row when present in the current filter. Use native ripple and restrained row placement animation, never delayed persistence.

## Acceptance scenarios

- A94-01: without swiping, a keyboard/TalkBack user can find Edit, Explain and Delete for the correct row. Adjacent equal-weight rows still have distinguishable date/time labels.
- A94-02: a fast short swipe never commits; crossing threshold then returning below it before release does not commit; releasing beyond threshold follows existing behavior. Menu Delete and swipe call one shared repository path.
- A94-03: delete the only row while scrolled at the end of a long month. Undo is visible in the Scaffold host and restores value, attrs, source and canonical aggregates exactly.
- A94-04: enable extended accessibility timeouts; Undo remains usable for the recommended interval. Disable animations: same interval. Background/recreate mid-window: recoverable receipt remains and no duplicate restore occurs.
- A94-05: inject delete and restore failures. Delete failure leaves the row; restore failure preserves Retry and snapshot. Attempt a second delete during a pending one: it is unavailable with a clear explanation.
- A94-06: September range excludes October 1, includes September 1, and Clear filter restores all rows. Loading/failure/empty cannot be mistaken for one another.
- A94-07: edit double-tap creates one replacement; Cancel and failed transaction preserve original. kg/lb, comma decimals, edited timestamps and equal-value duplicates remain supported.

## Validation

Add deterministic clock-driven Undo/recreation tests, Room recovery-record migration/atomicity tests, swipe geometry/state tests and Compose action/focus/range tests. Do not use real-time sleeps for timeout unit tests. Verify retained recovery data is bounded and backup behavior documented.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f06-weight:testDebugUnitTest :core:data:jvmTest :core:database:jvmTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest
```
