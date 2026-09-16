# Weight UX implementation handoffs — WLO-0088–0099

Prepared 2026-09-16 under WLO-0100, against source baseline `66fd2c5`. This document is the dispatch index for twelve implementation specifications. **The specifications are ready; the application changes are not implemented.** All twelve implementation tickets are in todo. Read a ticket with `ticket task WLO-0088` and its full handoff with `ticket task WLO-0088 spec get` (substitute the ID).

## Start an implementation task

Give the agent the repository and one ticket ID. Use this prompt:

> Implement WLO-00XX in this repository. Read AGENTS.md, the ticket's complete specification using the Ticket CLI, and docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md. Verify that blocking dependencies have landed, inspect the named source contracts, and move the ticket to doing. Implement its bounded scope, preserve unrelated work and frozen rulings, and run its numbered acceptance scenarios and required checks. Write evidence and a completion report with exact commands/results. Do not mark it done while a required check is unverified or a material failure remains. Update ticket state only through the CLI.

Each spec repeats the necessary product/architecture context, so the agent does not need this conversation. Source paths identify the current implementation, while names prefixed “proposed” define new contracts rather than claiming those APIs already exist. Re-resolve symbols if prior implementation tickets move them. References to UI and state files under F01/F06/F13 use their module's `src/main/kotlin/app/wlo/feature/...` package root.

## Specifications and integration order

The following blocking relationships are also recorded in Ticket. They are conservative integration prerequisites, not an instruction to start parallel agents. An isolated prototype may be prepared earlier, but do not close a ticket until the prerequisite behavior is integrated and tested.

| Specification | Blocking prerequisites |
|---|---|
| [WLO-0088 — Daily selection and provenance](../tasks/WLO-0088-P1_Reconcile_Weight_Daily_Scalar_Policy_And_Truthful/spec.md) | None |
| [WLO-0089 — Capture/correction transactions](../tasks/WLO-0089-P1_Make_Weigh_In_Correction_Transactional_And_Preserve/spec.md) | None |
| [WLO-0090 — Section state and body measurements](../tasks/WLO-0090-P1_Repair_Weight_Section_State_And_Body_Measurement/spec.md) | WLO-0091 |
| [WLO-0091 — Material 3 foundations](../tasks/WLO-0091-P1_Rebase_WLO_Theme_And_Shared_Controls_On_Material_3/spec.md) | None |
| [WLO-0092 — Weight overview](../tasks/WLO-0092-P2_Redesign_Weight_Overview_Hierarchy_And_Goal_Entry_Points/spec.md) | WLO-0088, WLO-0089, WLO-0090, WLO-0091, WLO-0093, WLO-0094 |
| [WLO-0093 — Goals and profile forms](../tasks/WLO-0093-P1_Make_Goals_And_Profile_Forms_Labeled_And_Recoverable/spec.md) | WLO-0088, WLO-0091 |
| [WLO-0094 — Logbook actions and Undo](../tasks/WLO-0094-P1_Make_Logbook_Editing_Deletion_And_Undo_Accessible/spec.md) | WLO-0088, WLO-0091 |
| [WLO-0095 — Import/export recovery](../tasks/WLO-0095-P1_Repair_CSV_Mapping_And_Data_Movement_Recovery_UX/spec.md) | WLO-0088, WLO-0090, WLO-0091 |
| [WLO-0096 — Navigation and adaptive layouts](../tasks/WLO-0096-P2_Align_Weight_First_Navigation_And_Adaptive_Layouts/spec.md) | WLO-0092, WLO-0094 |
| [WLO-0097 — Setup, settings and reminders](../tasks/WLO-0097-P2_Simplify_Reminder_Settings_And_First_Run_Handoffs/spec.md) | WLO-0093, WLO-0095, WLO-0096 |
| [WLO-0098 — Chart accessibility and motion](../tasks/WLO-0098-P2_Add_Accessible_Trend_Exploration_And_Restrained_Motion/spec.md) | WLO-0088, WLO-0090, WLO-0091, WLO-0092 |
| [WLO-0099 — Integration and release validation](../tasks/WLO-0099-P1_Validate_Weight_UX_Against_The_Full_Review_Acceptance/spec.md) | WLO-0088, WLO-0089, WLO-0090, WLO-0091, WLO-0092, WLO-0093, WLO-0094, WLO-0095, WLO-0096, WLO-0097, WLO-0098 |

Suggested landing order:

1. WLO-0088, WLO-0089, WLO-0091: data contract, safe correction, M3 foundations.
2. WLO-0090, WLO-0093, WLO-0094: body state, Goals, logbook.
3. WLO-0092, WLO-0095: composed overview and data movement.
4. WLO-0096, WLO-0098: shell/adaptive layouts and chart exploration.
5. WLO-0097: setup/settings/reminder integration.
6. WLO-0099: full release evidence.

WLO-0088's portable metadata must be specified before WLO-0095, but WLO-0088 does not wait for all F13 UI work to merge. Its core round-trip contract is tested in WLO-0088; F13 integration is an additional release gate in WLO-0095/WLO-0099. This avoids a dependency cycle. WLO-0092's Goals/history entry points wait for WLO-0093/WLO-0094 so they can be verified end to end.

## Decisions the implementation must not guess

| Topic | Proposed contract and owner |
|---|---|
| Daily weight | WLO-0088: versioned consistent window [05:30,09:30), closest to 07:00, deterministic ties; median fallback. Preserve raw events, fixed profile policy timezone and stored day buckets. Explicitly amend R-B5/F06 before changing production. |
| Canonical smoothing | EWMA alpha 0.15 remains. No preview value becomes canonical. WLO-0054 owns the outstanding advanced-controls decision; its open question is not resolved by this work. |
| Values/provenance | Visible numeric value and unit are separate from the status chip; no dead info affordance or starting-weight-as-trend substitution. |
| Correction | WLO-0089: no write when opening/cancelling correction; one atomic replacement on Save, with idempotent lifecycle recovery. |
| Body calculation | WLO-0090: invalidate stale result after input/method change; persist one complete measurement bundle atomically; preserve method metadata. |
| No-target Goals | WLO-0093: explicit F01 `writeFirst`, optional null calorie budget, existing floors/invariants and safety eligibility; no forced diet wizard or third writer. |
| Delete recovery | WLO-0094: one pending deletion, persistent bounded recovery record, scaffold snackbar, accessibility-adjusted foreground timeout, no competing visual timer. |
| CSV | WLO-0095: staged source retained through mapping/review; explicit units/date assumptions, 20 MiB bounded read, idempotent commit and useful IO errors. |
| Fresh Start | WLO-0095: remove release entry/guard route until reversibility is specified and implemented. Document R-B7 as deferred; do not execute current archive/reset under a backup promise. |
| Release navigation | WLO-0096: Weight, Hub, Plan, More; defer empty Insights primary destination while preserving compatible fallback. Explicitly amend R-D2/IA for this release; retain working Plan. |
| Theme | WLO-0091: full accessible light/dark role sets, brand no-alarm-red rule preserved. No unrequested theme-preference or dynamic-color change. |
| Reminders | WLO-0097: local approximate WorkManager schedule, time picker, truthful effective permission/channel state and time-neutral copy. |
| Motion | WLO-0098: meaningful, interruptible, respects duration scale and haptic preferences; no numeric count-up, loss celebration or animation-dependent persistence. |

These are specifications for proposed implementation. No frozen ruling or product code was changed while preparing them. Where the table names a contract amendment, the implementation must make that amendment explicit and reviewable in the same change. If another task has since ratified a conflicting decision, reconcile the ticket before coding rather than silently overwriting the newer ruling.

## Shared-file ownership and interface seams

| Shared area | Owner / integration rule |
|---|---|
| `WeighInRepository` and canonical reducers | WLO-0088; coordinate WLO-0089 replacement/idempotence and WLO-0094 recovery operations. Preserve transactional contracts and use separate focused commits. |
| Room schema/migrations | Each domain ticket owns its additions. Allocate the next schema version after rebasing; do not independently reuse version numbers. Migration tests include all predecessor schemas. |
| `ProvenanceChip` / `WloStat` | WLO-0088 semantics; WLO-0091 style and standard component delegation. Agree on signatures before editing. |
| `WeightScreen` / `WeighInViewModel` | WLO-0089 capture/receipt; WLO-0090 body/section state; WLO-0092 final composition; WLO-0098 chart controls. Land changes sequentially in shared files or reconcile deliberately. |
| Body read model | WLO-0090 supplies method/source/event IDs to WLO-0098; WLO-0095 preserves them in backup. |
| Goals | WLO-0093 owns F01 persistence/forms. WLO-0092 only invokes callbacks; WLO-0096 routes them; WLO-0097 stores setup intent, not active Targets. |
| History range | WLO-0092 emits inclusive start/exclusive end epoch-day; WLO-0094 filters; WLO-0096 encodes/restores routes. |
| `WloApp` | WLO-0096 owns shell and navigation metadata; WLO-0097 integrates durable post-setup intents after it. |
| Motion/haptics | WLO-0098 defines common behavior; success event owners emit once after commit. Do not add a second haptic in a shared visual wrapper. |
| Validation evidence | Every implementation ticket proves its own scenarios; WLO-0099 verifies integration and missing matrix intersections. |

Proposed operation tokens must be stable per submission, durable where process recovery is required, and scoped to profile plus operation kind. A retry reuses the token; a deliberate new measurement uses a new token. Physical value/date equality is not an idempotency key. Each owner must document token lifecycle, transactional storage and cleanup, and add a failure-after-commit test. Use existing repository mechanisms when they satisfy these guarantees rather than adding a generic workflow framework.

## Verifiable objectives and testing

There are 79 numbered scenarios across the twelve specifications. Each has an observable result, not merely “looks polished”. Implementation reports should list each scenario ID with test name/evidence and Pass/Fail/Not run. In addition to normal paths, the specs explicitly cover failed writes, cancel/back, duplicate submission, process recreation, units/locales, empty/forming/held data, 200% text, keyboard/TalkBack and reduced motion where relevant.

The referenced Gradle tasks were checked with `--dry-run`: core data/engines/database/vault JVM tests, design-system/F06/F01/F13/app unit tests, app instrumentation, assembleDebug and checkArchitecture all resolve. This verifies command availability only; **no implementation tests were run or passed by this documentation task**. Run the actual ticket-specific commands after implementing. The listed affected modules' `ktlintCheck` and `detekt` tasks also resolve in a separate dry-run. Use those tasks for style checks after implementation. Do not treat a dry-run as test evidence.

Instrumentation uses Test Orchestrator with `clearPackageData=true`. Use disposable emulators with synthetic data. Do not run the clearing `just demo`/`just fresh` workflows against a user's installed data. Physical haptics, TalkBack traversal and perceptual motion require explicitly documented manual checks; screenshots alone are insufficient.

## Specification-readiness verification

- Twelve full specs written through Ticket CLI and read back from the generated mirror; each approximately 1,100–1,300 words.
- Each includes product/architecture onboarding, concrete source entry points, bounded ownership, required behavior, data/state/failure contracts, numbered acceptance scenarios, commands and definition of done.
- Ticket descriptions point to the handoff; dependencies are recorded with no cycles; implementation status is todo.
- Source symbol/path correction performed for F06 routes and overview components; exact Gradle task availability verified.
- Governing-contract proposals, unchanged smoothing decision, deferred Fresh Start and retained theme policy are explicit.
- This index links to the exact CLI-generated spec files; no task mirror was edited directly.
