# WLO-0093 — Labeled, recoverable Goals and profile forms

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A09/A10: a person can create or revise a goal without completing a diet wizard, understand every populated field, and recover from validation/storage conflicts without losing work.

Read `feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/{ui/GoalsEditorScreen.kt,state/GoalsEditorViewModel.kt,domain/GoalsEditorDraft.kt}`, shared `WeightGoalSafetyControls` (locate with `rg`), `app/src/main/kotlin/app/wlo/app/ui/settings/ProfileFactsScreen.kt`, `core/data/src/commonMain/kotlin/app/wlo/core/data/{TargetsWriter,TargetsRepository}.kt`, and `core/documents/src/commonMain/kotlin/app/wlo/core/documents/{TargetsDocument,TargetsEnvelope}.kt`. Existing tests include `GoalsEditorDraftTest`, `GoalEditorSafetyTest`, `WeightGoalPreviewTest` and app `GoalsEditorParityTest`.

## Contract and ownership

Depend on WLO-0091 form primitives and WLO-0088 truthful trend semantics. Own Goals creation/editing and profile form behavior; WLO-0097 owns onboarding and settings grouping. Do not add a third Targets writer or let F06 persist goals. `TargetsWriters.studio().writeFirst` is the only creation route; `writeRevision(profileId, baseVersion, document)` remains optimistic-concurrency editing. All versions remain immutable, and all current safety/floor/pace validation remains active.

## Required implementation

1. Use persistently labeled M3 OutlinedTextFields for target weight, pace, optional date, optional calorie budget, height and birth year. Labels include units; placeholders only give examples. Read-only current trend shows value and date/provenance, with “Starting weight” explicitly labeled when that is the only available reference. Never use the starting weight as a trend silently. Group goal mode/weight, pace/date and optional energy settings separately; make optionalness explicit.
2. Goal mode is a standard single-choice control. Target date uses M3 DatePicker with a readable formatted value and Clear; do not require ISO typing on a decimal keyboard. Store the domain's ISO date and explain advisory implied pace using existing engines. Preserve current safety questions and held reasons; no preselected consent answers or inferred eligibility.
3. Parse decimals locale-aware using a shared validated parser consistent with weigh-in behavior. Canonical kg/cm persist at repository boundaries; display uses the current app units. Drafts retain invalid text for correction. Reject nonfinite numbers, impossible parse and existing domain violations with field-level supporting text, `isError` and accessible error semantics. Focus/scroll the first invalid field after submission. For birth year reject a future year using an injected clock; retain existing supported lower bound. Do not invent clinical eligibility from age/height here.
4. Support a profile with no Targets. Prefill from saved weight-first goal intent only as an editable draft, then require explicit Save. Create v1 via `writeFirst` with the chosen goal; DAILY energy with null budget, registry floor and no override acknowledgement; balanced macro schema default; retain existing document defaults for other fields. No arbitrary calorie target or calculated diet is introduced. If the user explicitly adds an energy budget, validate through the writer. Document this weight-only creation path in F01/FEATURES §2.0 and test that downstream consumers handle null budget honestly.
5. Use form states Loading, Editing(dirty/baseVersion), Saving, SaveError, Conflict and Saved. Freeze submission values and block double-submit during Saving. A successful revision emits exactly one new version and one receipt. On version conflict retain draft, display that the goal changed, and offer Reload current or Review differences; never silently overwrite. Review differences requires an explicit new Save against the refreshed version.
6. Persist recoverable drafts with a schema version, profile ID and base Targets version. Restore after rotation/process death; never restore one profile's draft into another. Back with unsaved edits offers Keep editing/Discard in an M3 dialog; an unchanged form exits directly. Failed writes retain all fields and error. Remove the draft only after durable success.
7. Goals currently persist target revision and safety metadata separately. Make a save recoverable as one logical operation: durably associate the operation ID with the written target version and pending safety metadata, then retry remaining metadata without creating another Targets version. Prefer an atomic core-data transaction when both data sets live in Room; otherwise journal the sequence. Do not display full success until both complete. Profile save similarly has a busy guard, surfaced repository errors and readback before dismissing.

## Acceptance scenarios

- A93-01: populate every field, remove focus and inspect at 200% font. Labels/units remain visible, date is readable, supporting errors are not clipped; TalkBack names each field independently.
- A93-02: a weight-first profile with zero Targets opens Set a goal and saves valid maintenance/loss/gain examples through v1. Budget remains null unless explicitly entered; no diet wizard is required. Ineligible goal cases are held by existing safety logic.
- A93-03: enter `80,5` in a comma-decimal locale; save canonical 80.5 kg. Toggle units without changing physical value. Invalid text stays editable and cannot silently become zero.
- A93-04: save concurrently from two editors with the same baseVersion. One succeeds; the other shows Conflict and retains draft. Explicitly reviewing current data and resaving creates one subsequent version.
- A93-05: inject failure before Targets write, after Targets commit/before safety completion, and during profile write. Retry produces neither duplicate versions nor false success. Rotate/recreate while Saving and reconcile the operation.
- A93-06: Back with dirty draft, then Keep editing preserves all values; Discard removes only this draft. Successful save clears it and returning shows committed values.
- A93-07: profile birth year beyond injected current year is rejected; unchanged supported bounds and all existing safety tests remain green. A held/absent trend is never replaced with a fabricated current value.

## Validation

Add first-goal, conflict, locale, journal-recovery and profile-save tests. Exercise each existing safety fixture rather than checking only one happy path. Update Goals parity instrumentation for persistent labels, date selection and no-target creation. No budget algorithm, safety threshold, floor override rule or unrelated onboarding redesign.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f01-onboarding:testDebugUnitTest :core:data:jvmTest :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.GoalsEditorParityTest
```
