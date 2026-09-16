# WLO-0099 — Release evidence for the complete weight UX acceptance matrix

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A18: establish evidence that the full WLO-0087 review is resolved across real journeys, adverse states, accessibility and adaptive layouts. This ticket is the release gate after WLO-0088–0098, not a substitute for their own tests.

Start at `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md` (A01–A18 and acceptance matrix), `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md` (W01–W17/S01–S09), and every implementation ticket's scenario IDs. Test source roots: `core/*/src/jvmTest`, `feature/*/src/test`, `app/src/androidTest/kotlin/app/wlo/app`. Reuse `TestNav`, `SeedingRobot` and existing synthetic debug fixtures after inspecting their behavior. The app's test orchestrator clears package data. All runs must use disposable emulator profiles, never a personal database.

## Deliverables

Create `docs/tech/WLO-0099-WEIGHT-UX-VALIDATION.md` and `docs/tech/WLO-0099-evidence/`. The report contains commit/build/device/API/window/font/locale/theme/animation configuration; fixture IDs; exact commands/results; a traceability table from every A01–A18 and W01–W17/S01–S09 to test/evidence paths and outcome; a severity-ranked open-defect list; and the M3 self-audit. Use statuses Pass, Fail, Not run or Not applicable with a reason. A screenshot does not prove transactional integrity, TalkBack usability or physical haptics.

Own integration tests and evidence, not silent product changes. File/update the responsible implementation ticket for a failure and retain evidence. Small test-fixture fixes are in scope; application behavior fixes should remain attributable to their originating ticket. Do not close this gate while a critical data/safety/semantics/accessibility failure remains.

## Deterministic fixture catalogue

- F0: completed setup, no measurements and no Targets.
- F1: one raw reading; trend forming; no invented forecast.
- F2: 90 days of ordinary synthetic data plus missed days and two same-day readings (07:00=80,18:00=79), proving policy selection.
- F3: fallback median day (12:00=80,18:00=82), selected scalar81 and two-source provenance.
- F4: loss, maintenance and gain Targets versions, plus each existing safety-held eligibility case from F01 fixtures; do not invent new safety expectations.
- F5: flagged reading, failed correction, successful correction, repeated submit and process-recovery boundaries.
- F6: body measurements with two known methods, an unknown legacy method, metric/imperial units, stale calculator draft and atomic-save failure.
- F7: long logbook spanning September/October boundary, equal-value readings, deletion/Undo and filtered-empty/error.
- F8: valid kg/lb CSV, 12-column CSV, malformed/ambiguous date, read/write failure, oversized file, legacy/current backup and staged-restore journal.
- F9: permission denied/revoked/channel disabled reminder, Health Connect unavailable/partial permission, setup manual/file/Health Connect handoffs.

Every fixture has fixed dates/clock/IDs/timezone and a reset function. Record expected row/attr counts, key canonical values and versions before/after mutations. Do not depend on today's date or network availability.

## Required journeys

Execute and record: setup→first weigh-in; unusual value→keep/correct/cancel; successful save→stable dashboard; Weight↔Body fat→save; no-target goal creation; goal revision/conflict/recovery; month bucket→filtered logbook→edit→Back; menu/swipe delete→Undo; import→mapping→review→Back→commit; export/restore/recovery; reminder permission/time repair→notification capture; overview→Goals/More→Back across resized windows. Include all alternate failure/cancel branches in the originating ticket scenarios, not just these happy-path names.

## Coverage matrix

| Axis | Minimum coverage |
|---|---|
| OS | API29 baseline and a current available emulator with runtime notification permission; name exact version |
| Windows | 320×640dp, 360×800dp, landscape, 600dp rail threshold, >=840dp supporting/list-detail; simulated hinge if available |
| Typography | 100% and 200% font; long labels and IME visible |
| Theme | Light and dark component/screen validation even if release selection remains dark |
| Units/locale | kg/cm and lb/in; English and a comma-decimal locale; 12/24-hour time |
| Inputs | Touch, keyboard, TalkBack, visible non-gesture row actions |
| Motion | System scale 1× and 0×, haptics preference on/off; physical vibration separately |
| Lifecycle | Rotation, background/resume, process recreation before/after commit; app-lock return |
| Data state | Loading, empty, sparse/forming, ready, held, error/retry, stale data, success |

Use a documented risk-based pairwise matrix rather than claiming the full Cartesian product. Mandatory intersections: 320dp+200%+IME for each form; TalkBack+delete/Undo; animation0+Undo; process death+correction/import/onboarding; lb+CSV; mixed body methods+accessible chart; wide resize+dirty editor. All combinations not run must remain visible as coverage limits.

## Verifiable release criteria

- A99-01: every scenario A88-01 through A98-07 as actually defined in its ticket maps to executable test or manual evidence; no missing scenario IDs. All inventory surfaces are accounted for, including legacy/deferred routes with reason.
- A99-02: exact expected raw/attr/version counts hold through failed transactions and retries. Daily selection is consistent across overview/Hub/logbook/Goals/export and backup restoration. No data-loss/duplicate-write defect remains.
- A99-03: contrast reports meet 4.5:1 normal text, 3:1 large text/essential visual boundaries; actionable targets >=48dp; populated labels visible; no info affordance is dead; no primary action is inaccessible under IME/system bars.
- A99-04: manually perform the named TalkBack/keyboard journeys; record traversal/actions and outcome. Automated semantics tests supplement this, not replace it. Physical haptics untested means Not run, not Pass.
- A99-05: representative frame trace reports fixture/device/duration/frame totals/jank and comparison baseline. No claim of universal 60/120fps from one emulator recording.
- A99-06: no shame copy, unconditional loss-is-good coloring, guessed held values, unsafe Fresh Start entry, empty primary destination or duplicate screen title remains in audited scope.
- A99-07: M3 self-audit scores each of the eight skill categories 0/1/2 with evidence. No zero in component semantics, states/feedback or accessibility; any remaining 1 has explicit limitation and owner. All P1 data integrity/accessibility defects are resolved before marking this ticket done.

## Execution and reporting

Run module tests first, then build/architecture checks, then instrumented suites on disposable emulators, then manual/visual/assistive checks. Do not hide flaky failures by rerunning until green; report the failure and fix/root cause. Retain logs compactly with commands and exit codes. Run the commands below; narrow class filters are useful during diagnosis, but final coverage must include the listed relevant existing suites and new tests. No UI implementation is implied complete by this specification.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :core:data:jvmTest :core:engines:jvmTest :core:database:jvmTest :core:vault:jvmTest :core:designsystem:testDebugUnitTest :feature:f06-weight:testDebugUnitTest :feature:f01-onboarding:testDebugUnitTest :feature:f13-vault:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
