# WLO-0096 — One navigation model and adaptive weight layouts

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A15: navigation communicates real destinations, each screen has one title, Back restores context, and wider windows use useful supporting panes instead of stretched phone cards.

Read `app/src/main/kotlin/app/wlo/app/navigation/{WloApp,WloTabs}.kt`, the F06 route definitions (locate `F06Routes.kt`), `core/designsystem/src/main/kotlin/app/wlo/core/designsystem/{WloBottomBar,WloScreenTitle}.kt`, `docs/design/IA.md`, and FEATURES §2.0/R-D2. Current implementation has Weight/Hub/Plan/Insights/More; Insights is a stub and the shell/title arrangement duplicates screen headings. Existing shell/deep-link tests are `app/src/androidTest/kotlin/app/wlo/app/{WloShellTest,WloDeepLinkTest}.kt`.

## Product decision and dependencies

For this implementation proposal, retain the working top-level destinations Weight, Hub, Plan and More in that order. Remove the unimplemented Insights destination from primary navigation until it has a usable landing screen. Do not hide the working Plan feature or rename route identities. Explicitly amend R-D2 and IA to distinguish this release configuration from the future five-destination model; never claim that the old frozen tab list already matches it. An old Insights deep link lands on More with a neutral “Insights is not available yet” explanation and relevant available links, not a blank screen or crash.

Depend on WLO-0091 primitives and coordinate WLO-0092 overview callbacks/WLO-0094 history filter handling. Own app-shell routing, app-bar actions, saved navigation state and adaptive composition. Feature modules expose callbacks/content slots; no feature imports another feature's destinations. WLO-0097 uses the resulting onboarding/settings navigation contracts.

## Required routing and layout

1. Define one route metadata table: destination ID, localized label, top-level owner, app-bar title, Up behavior and pane title. Shell owns top app bars. Feature content removes redundant screen titles; sheets retain their own titles. A title cannot be both a top app-bar title and an extra first content heading. Preserve accessibility pane announcements without reading the same title twice.
2. Use M3 NavigationBar for compact width and NavigationRail for expanded available width. For this project choose a documented 600dp available-width breakpoint, evaluated after window insets, not physical device identity. Keep destination IDs, selected state and navigation stacks stable when resizing. Labels and icons expose selected semantics; no custom row of action chips as navigation.
3. Compact Weight remains single-column. At >=840dp available width, introduce a supporting pane only when each pane can retain at least 320dp content width after spacing; otherwise fall back to one column. Primary pane contains hero/chart/capture context; supporting pane contains goal/recent summary. Keep reading order primary then supporting, avoid duplicating the same actionable content in both panes. Bound chart/content width and let text wrap. Fold/hinge occlusion must not split an interactive component; fall back to one unobscured pane when layout information is unavailable.
4. Expanded logbook is list-detail: selected event remains selected while a detail/edit pane opens. Compact uses the existing detail sheet/route. Preserve selected event ID, list scroll, period/filter and draft when changing width. Only one editable instance for an event may exist; if a modal edit is already active during resize, keep that modal until dismissed before adopting pane editing.
5. Encode overview history filters as optional epoch-day start/end route arguments with start inclusive/end exclusive. Reject malformed or reversed ranges to an explained unfiltered destination; never crash or accidentally show a misleading subset. Existing `f06/weight`, capture/log and logbook deep links continue to resolve. Route callback payloads carry stable event IDs, not row positions.
6. Back hierarchy: close a modal/editor with its dirty-state policy; then leave selected detail where applicable; then pop nested destination; top-level Back follows platform behavior. Top-level switches use save/restore state and single-top navigation. Returning from Goals/logbook/settings restores overview section, period and scroll; it does not push a duplicate Weight route.
7. Apply Scaffold content padding, navigation/system bars and IME insets once. Avoid doubled bottom padding and actions under gesture bars. Keyboard focus traverses navigation then primary/supporting content logically. The layout must support 200% text and landscape without clipping persistent actions.

## Acceptance scenarios

- A96-01: first launch after completed onboarding selects Weight. Four functioning top-level destinations appear; Insights stub is absent. Existing Plan route remains usable. Old Insights link yields the documented fallback.
- A96-02: every W01–W17/S01–S09 destination has one visible screen title, correct selected top-level owner and correct Up action; modal titles remain independent.
- A96-03: resize 599→600→839→840dp and back with Body fat selected and history scrolled. State persists; no duplicate destination, overlapping pane or half-width unreadable form appears.
- A96-04: tap a history range, edit an event, press Back and return to overview. Range, selected event and overview scroll/period are preserved at compact and expanded widths.
- A96-05: malformed deep-link ranges and missing event IDs show recoverable fallback; valid historical deep links remain compatible. App-lock gate is not bypassed.
- A96-06: landscape with IME, 320dp width and 200% font leave all actions reachable. Keyboard and TalkBack order are logical; target sizes remain valid.

## Validation

Add route metadata/argument and state-restoration tests; extend WloShellTest/WloDeepLinkTest and screenshot compact/rail/two-pane breakpoints. Record actual available dimensions/insets. Do not claim foldable validation from a tablet screenshot: mark simulated hinge testing explicitly. No new navigation framework or library upgrade is required.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :app:testDebugUnitTest :feature:f06-weight:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.WloShellTest,app.wlo.app.WloDeepLinkTest
```
