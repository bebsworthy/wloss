# WLO-0095 — Recoverable import/export and honest data-management actions

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A13/A14: a person can map a CSV at phone width, correct mistakes without reopening the file, understand what will change, and recover from IO failures. The app must not promise a reversible reset it cannot provide.

Read `feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt` and its `ui/ImportScreen.kt`, export/backup/restore screens; `core/ports/src/commonMain/kotlin/app/wlo/core/ports/DataVaultPort.kt`; `core/vault/src/commonMain/kotlin/app/wlo/core/vault/{MetricCsv,CsvImportCommitter}.kt`; `app/src/main/kotlin/app/wlo/app/di/VaultAdapters.kt` (`freshStartPreview/freshStartHide`). Locate exact screen filenames with `rg --files feature/f13-vault`. Existing parser currently accepts ISO-ish day values and metric-unit mapping but does not justify guessing arbitrary locale dates.

## Scope and dependencies

Own file-pick IO, mapping/review/commit recovery, data-operation copy and unsafe Fresh Start exposure. Consume WLO-0091 form primitives; integrate WLO-0088 policy and WLO-0090 method metadata in backup round trips. Preserve existing staged-restore journal, encryption, consent, app-lock and FLAG_SECURE behavior. Do not replace F13 storage engines or broaden permissions to all files.

## Required state machine

Use Pick → Reading → Mapping → Reviewing → Applying → Done, with operation-specific RecoverableError states retaining the previous stage. Bundle restore follows its existing validated staging path, not CSV mapping. “Back to mapping” edits the same staged source; “Choose another file” explicitly discards it. Only final commit mutates app data. Mapping changes invalidate the previous review token; commit accepts only the current staged token. Repeated Apply taps or resumed process must never duplicate rows.

Move URI reading off the main thread through an injected IO reader. Copy picked bytes to a bounded app-private staging file, with an explicit documented maximum of 20 MiB for this release; fail before unbounded allocation. Larger input receives a clear split-file instruction. Use persisted URI access only when the provider grants it; private staging supports process recovery without depending on a still-readable URI. Delete staging after success/explicit discard and clean orphaned expired sessions. No raw file content in logs.

## Mapping and feedback contract

1. Replace the non-scroll eight-chip row with one ListItem per source column: header + example values, exposed dropdown for destination, labeled unit selector for numeric measurements. Destinations include Ignore, Date, supported metrics and Custom. Require exactly one Date mapping and at least one measurement; prevent conflicting duplicate destination mappings unless explicitly supported by the parser. Unknown columns default Ignore with visible explanation, never silent guessed health values.
2. Weight offers kg/lb and converts once to canonical kg before persistence. Body fat is percent; custom measurements retain explicit units. Date format is explicitly ISO `YYYY-MM-DD` (or existing epoch-day mode if exposed and labeled); ambiguous `01/02/2026` is rejected with correction guidance rather than guessed. Do not advertise timestamp-column mapping unless implemented end-to-end; disclose the current importer capture-time assumption in Review.
3. Review shows file name, mapping summary, accepted/rejected/skipped counts, unit/date assumptions, duplicate policy and concrete row-level errors with original row numbers. Imported derived trend must not override WLO's computed canonical trend; ignore/reject such a column with an explanation. Define duplicate handling from existing staged-import semantics and expose it honestly; do not silently deduplicate legitimate equal-value readings by value/date alone.
4. Mapping edits retain source bytes, sample and all other mappings. IO/staging/commit failure has an in-context Retry or Back action and never says “Done”. Applying shows progress/busy state and prevents Close/Back from pretending to cancel a committed transaction. After a durable commit, a UI refresh failure is distinct from import failure; Retry must not recommit.
5. Use standard M3 top bar, ListItems, dropdowns, labeled fields, progress indicator and primary Review/Import action. Close is a secondary action outside Applying. Announce meaningful state changes once; row errors and mappings must work at 320dp/200% font and with keyboard/TalkBack. No horizontal scrolling required to discover mapping choices.
6. Export reports success only after the destination write closes successfully. Destination cancellation returns to a usable form; write failure retains format/options and offers Retry. Restore reports journal recovery with explicit next action. Health Connect unavailable/update-required/permission-denied states provide the appropriate provider/settings action when supported, otherwise explain the manual route; never claim connected when unavailable.
7. Release disposition for Fresh Start: remove its actionable entry from release UI and guard direct routing until a separately specified reversible workflow exists. Remove “relapse” and guaranteed-backup/reversibility claims. Do not invoke current `freshStartHide`, which archives diary/profile and clears onboarding without a guaranteed backup. Document this release deferral in F13/FEATURES where needed, explicitly preserving R-B7 as an unimplemented requirement rather than claiming compliance. Existing ordinary export, restore and explicit record deletion remain available. Do not implement an unreviewed archive/reset redesign inside this ticket.

## Acceptance scenarios

- A95-01: map a CSV with 12 columns at 320dp/200% font. All targets/units and example values are reachable. Review→Back retains bytes/mappings without opening the picker again.
- A95-02: import 220.46226218487757 lb; canonical value is 100 kg within 1e-6. Export/restored raw event reflects the correct canonical unit; no double conversion. An ambiguous date is rejected with its row number.
- A95-03: picker cancellation, read permission loss, 20 MiB limit exceedance, malformed CSV, parser error and destination-write failure each show the appropriate recovery without mutation or false success.
- A95-04: duplicate Apply, rotation, process recreation after commit and a readback failure yield one committed batch. Old review tokens cannot commit after mapping changes.
- A95-05: new policy timezone/version and body method metadata survive encrypted/JSON backup round trips; legacy bundles migrate deterministically. CSV preserves raw rows and documents its intentionally narrower metadata fidelity.
- A95-06: Fresh Start cannot be reached or executed in the release flow; no UI promises a backup that was not created. Existing staged restore/app-lock tests remain green.

## Validation

Extend MetricCsv/CsvImportCommitter/BackupDocumentIO/ExportBundle/StagedRestoreJvm tests plus F13 ViewModel fake-reader tests and disposable-device restore/import instrumentation. Verify cancellation and non-English number/date handling with explicit supported formats. No instrumentation against real personal backups.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :core:vault:jvmTest :feature:f13-vault:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.M6RestoreWizardTest
```
