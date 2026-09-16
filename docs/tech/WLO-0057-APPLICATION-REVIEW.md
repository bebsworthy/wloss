# WLO application review — architecture, Android, Material 3, and logic

**Ticket:** WLO-0057
**Date:** 2026-09-15
**Scope:** read-only application review; no production implementation changes
**Priority convention:** P0 = release-blocking privacy/data exposure; P1 = correctness or architectural blocker; P2 = significant maintainability/quality risk; P3 = cleanup.

## Executive verdict

The project is not fundamentally mis-architected. Its strongest boundaries implement real product promises: restricted network/AI/media/vault modules, one application composition root, pure deterministic engines, event-level storage, typed consent/provenance, and migration/property/golden tests. Replacing Koin, Room, Compose, or the feature-module structure would add churn without solving the observed defects.

The user's Material 3 concern is correct. WLO-0031 turned a valid consistency goal into a mandatory parallel UI framework. Feature and app code are forbidden from importing common M3 components, while `:core:designsystem` is exempt and contains hand-built substitutes for segmented buttons, checkboxes, and buttons. The rule enforces ownership by the WLO wrapper package, not correct Material behavior.

The most serious risks are outside visual polish:

1. Android Auto Backup is implicitly enabled and can copy app-private health data into system cloud backup.
2. The optional app lock starts fail-open after process death and can render unlocked content while settings load.
3. Backup, restore, CSV import, and weigh-in mutations cross transactional boundaries while claiming stronger guarantees or reporting simple success/failure.
4. Backdated weigh-ins do not recompute the later suffix of path-dependent trend projections.

The recommended strategy is surgical simplification: keep the valuable module boundaries, remove the M3 import ban and fake controls, move compound mutations behind existing repository/port owners, and make privacy/build gates fail closed.

## Review evidence and baseline

- Governing sources reviewed: `docs/objective.md`, `docs/features/FEATURES.md`, feature specs for the traced flows, `docs/tech/ARCHITECTURE.md`, ADR-005, `docs/design/DESIGN-SYSTEM.md`, `docs/design/IA.md`, and the current `AGENTS.md` M3-first ruling.
- Relevant completed/in-flight tickets reviewed: WLO-0030, WLO-0031, WLO-0050, WLO-0051, WLO-0053, WLO-0054, WLO-0055, and WLO-0056.
- Repository size: approximately 56,093 production Kotlin lines and 65,238 total Kotlin lines including tests/build logic.
- Included Gradle projects: `:app`, 15 `:core:*` modules, 8 feature modules, and `:benchmarks`.
- `./gradlew test :app:assembleDebug --continue`: green.
- `./gradlew :app:connectedDebugAndroidTest --continue` on API 29: 62 tests ran; 58 passed and 4 failed. Three failures are in `M3WeighInTest` on the current WLO-0056 WIP branch (`outlierFlagged`, swipe-delete/undo, and inline edit). The fourth is `M6RestoreWizardTest.wipeAndRestoreThroughWizard_rebuildsSeedState`, whose expected report summary no longer matches the rendered per-store row counts. The branch is therefore not instrumented-test green; no failure was hidden or reclassified as a pass.
- `./gradlew checkArchitecture :app:lintDebug --continue`: Android lint completed, but `checkArchitecture` failed on three direct, standard M3 usages (`ListItem` twice and `TextButton` once). This demonstrates the policy conflict.
- Android lint: 1 error and 11 warnings. The error is a notification call not accepted as permission-safe; lint remains non-blocking because both application and library conventions set `abortOnError = false`.
- Gradle warns that `:core:vault/commonTest` is not enabled as an Android host test source set and that deprecated behavior is incompatible with Gradle 10.
- Emulator: API 29, 1080×2400. Hub and Weight were inspected through UI Automator and screenshots. At font scale 1.5, fixed-width controls visibly break `7-day avg` and bottom-navigation `Digestion` across multiple lines.

Official benchmark guidance:

- Android architecture: <https://developer.android.com/topic/architecture>
- Architecture recommendations: <https://developer.android.com/topic/architecture/recommendations>
- Modularization and its granularity pitfalls: <https://developer.android.com/topic/modularization>
- Compose state ownership: <https://developer.android.com/develop/ui/compose/state-hoisting>
- Compose accessibility semantics: <https://developer.android.com/develop/ui/compose/accessibility/semantics>
- Android Auto Backup defaults: <https://developer.android.com/identity/data/autobackup>
- M3 segmented buttons: <https://developer.android.com/develop/ui/compose/components/segmented-button>
- Adaptive navigation: <https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation>

## Findings

### P0 — Android system backup violates the intended local boundary

`app/src/main/AndroidManifest.xml:17-22` does not specify `android:allowBackup`, `android:fullBackupContent`, or `android:dataExtractionRules`. For apps targeting API 23+, Auto Backup participates by default and includes most internal files, shared preferences, and databases. WLO's Room store is ordinary bundled SQLite (`core/database/src/androidMain/kotlin/app/wlo/core/database/WloDatabases.android.kt:13-20`), not an application-encrypted database.

**Impact:** health history, settings, documents, and potentially other app-private state can leave the device through Android/Google backup without WLO's per-capability consent, receipt, or export encryption. This contradicts the product's local-first promise even though it does not pass through WLO's network stack.

**Smallest correction:** explicitly disable cloud backup and define extraction/device-transfer rules appropriate to the product. Add a merged-manifest gate and device backup/restore test. Treat cross-device transfer as a product decision, not an implicit platform default.

### P0 — App lock fails open on cold start and process death

`AppLockController` initializes `locked=false` and can only infer timeout from an in-memory background timestamp (`core/vault/src/commonMain/kotlin/app/wlo/core/vault/AppLockCommon.kt:31-55`). Process death discards that timestamp. `MainActivity` additionally collects `appLockEnabled` with `initialValue=false` and renders `WloApp` unless both flows already say locked/enabled (`app/src/main/kotlin/app/wlo/app/MainActivity.kt:124-147`).

**Impact:** after process death or a cold start, an enabled convenience lock can expose or briefly flash health UI before DataStore emits.

**Smallest correction:** model lock posture as unknown/locked/unlocked and fail closed until the persisted enabled state is known. An enabled lock should require authentication on cold start unless a durable, deliberate grace policy says otherwise. Test delayed settings emission and `am kill`/relaunch.

### P1 — The UI architecture enforces a parallel framework instead of M3-first use

`UI_ATOMS_BANNED_M3` bans `Button`, `OutlinedButton`, `TextButton`, `IconButton`, cards, switches, sheets, dialogs, navigation, list items, progress indicators, and chips from app/feature code (`build-logic/src/main/kotlin/app/wlo/buildlogic/arch/ArchRules.kt:89-109`). The scanner exempts `:core:designsystem` (`ArchRules.kt:281-343`). Current WLO-0056 work fails CI for using `ListItem` directly in `EgressMonitorScreen.kt:11` and `ReceiptsScreen.kt:11`; `LogbookScreen.kt:30` fails for a normal `TextButton`.

The exemption permits the exact defects the rule claims to prevent:

- `WloSegmentedBar` hand-rolls clickable 30dp `Surface`s instead of M3 segmented buttons and lacks selection semantics (`core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloPlanning.kt:37-84`).
- `WloCheckRow` draws a 22dp box and literal checkmark behind generic click behavior instead of `Checkbox`/toggle semantics (`WloPlanning.kt:195-270`).
- `WloPrimaryRow` is a custom clickable `Surface` button (`WloPlanning.kt:301-339`).

**Impact:** framework features, accessibility semantics, API evolution, component states, and defaults are lost behind reduced wrappers; correct M3 migrations can fail CI.

**Smallest correction:** remove the standard-component import ban. Enforce against hand-built lookalikes, undersized targets, raw hard-coded tokens, missing semantics, and custom controls without the required limitation/ticket justification. Prove the policy with the planning controls and current WLO-0056 call sites before migrating other wrappers.

### P1 — App navigation omits standard nested-destination behavior and adaptation

The sole `Scaffold` always renders the five-item bottom bar for onboarded destinations (`app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt:125-151`), including settings, vault, logbook, and editors. No `TopAppBar` exists. `WloScreenTitle` is scroll-content text rather than app chrome (`core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloScreenTitle.kt:18-27`), so nested screens lack an Up affordance. Tab switching explicitly disables save/restore and clears history (`WloApp.kt:139-143`). No window-size/adaptive navigation API is present.

**Impact:** navigation state is discarded; nested routes are hard to escape predictably; the compact bar is used on expanded windows; titles/actions do not follow platform conventions.

**Smallest correction:** distinguish top-level from nested destinations in one scaffold. Use standard M3 app bars/Up navigation and the navigation multiple-back-stack pattern. Adopt `NavigationSuiteScaffold` or equivalent window-size switching for rail/bar behavior; do not create a WLO navigation framework.

### P1 — Accessibility defects survive the current conformance gates

`TextTertiary = #5C6875` (`core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTheme.kt:23-30`) measures about 3.32:1 against the background and 3.07:1 against the card surface, but is used for normal 13sp supporting text, including `WloListRow` (`WloListRow.kt:55-60`). Repository search found no screen heading, live-region, pane-title, or explicit progress-range semantics. `WloIconAction` accepts a nullable content description despite documenting it as required (`WloIconAction.kt:19-30`). At 1.5× font scale, fixed widths produce broken segmented and navigation labels.

**Impact:** normal text falls below the usual 4.5:1 contrast expectation, custom controls convey weaker state to assistive technology, and supported font scaling causes visible layout failures.

**Smallest correction:** fix semantic color roles at theme level; add semantic requirements/tests only for custom composites; use standard M3 controls so their semantics arrive automatically; test font scales, TalkBack, switch access, and compact/expanded windows.

### P1 — Turning scheduled backup off does not stop it

`BackupScheduler` exposes only `schedule` (`core/ports/src/commonMain/kotlin/app/wlo/core/ports/BackupScheduler.kt:8-16`). `BackupControlsViewModel.setAuto(false)` changes DataStore but neither cancels unique work nor changes the worker (`feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/BackupControlsViewModel.kt:146-159`). `BackupWorker` does not recheck the persisted enabled flag (`core/vault/src/androidMain/kotlin/app/wlo/core/vault/BackupSchedulerImpl.kt:63-83`).

**Impact:** a user-visible OFF setting can continue writing backups daily.

**Smallest correction:** add cancel/set-enabled semantics to the existing port, cancel unique work on disable, and make the worker fail closed by checking persisted policy. Keep scheduling policy out of the ViewModel.

### P1 — Same-day backup replacement deletes the known-good file first

`SafBackupStore` deletes an existing same-name document before creating and writing its replacement (`core/vault/src/androidMain/kotlin/app/wlo/core/vault/SafBackupStore.kt:65-88`). This contradicts `BackupManager`'s write-new-then-rotate guarantee (`core/vault/src/commonMain/kotlin/app/wlo/core/vault/BackupManager.kt:103-105`).

**Impact:** provider, stream, or process failure after deletion loses the day's last known-good backup.

**Smallest correction:** write a unique new/temp document, close and validate it, then retire the previous document. Test a provider that fails after creation or during write.

### P1 — Restore claims global atomicity that the implementation cannot provide

The public port and UI describe one all-or-nothing operation (`core/ports/src/commonMain/kotlin/app/wlo/core/ports/DataVaultPort.kt:149-154`; `feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/RestoreWizardScreen.kt:225-244`). The Room portion commits at `RestorePipeline.kt:285-327`; settings, documents, and derived projections happen later at `:329-339` and cannot share the Room transaction.

The advertised Confirm step is also unreachable: Report's “Continue” invokes commit immediately (`RestoreWizardScreen.kt:210-220`; `RestoreWizardViewModel.kt:114-125`).

**Impact:** a failure/process death after Room commit can leave a partially restored application while UI says failure or “nothing changed”; a navigation-sounding action performs the destructive import.

**Smallest correction:** make the multi-store operation journaled, resumable, and idempotent, with honest partial/retry states. Restore the explicit confirmation boundary. Do not attempt to fake a transaction across independent stores.

### P1 — CSV import and weigh-in mutations can partially succeed

CSV import appends row by row and ignores sidecar failure (`app/src/main/kotlin/app/wlo/app/di/VaultAdapters.kt:349-377`). Weigh-in append writes a raw event, metadata, a derived trend event, and projections through separate calls; secondary failures are ignored (`core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt:159-214`). Delete has the same multi-step shape (`:217-273`). Logbook edit orchestrates append → attributes → delete original inside the ViewModel (`feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt:305-363`). `RoomMeasurementRepository.append` can commit an event and then return an error when projection refresh fails (`core/data/src/commonMain/kotlin/app/wlo/core/data/RoomRepositories.kt:184-203`).

**Impact:** UI can report failure even though data changed, or success despite missing metadata/projections; edit can leave duplicates or remove the wrong authoritative version.

**Smallest correction:** put compound mutations behind existing repository/port methods and execute required Room writes in one database transaction. Return explicit partial/recovery outcomes only where an external store prevents atomicity. Add injected-failure tests at the public operation boundary.

### P1 — Backdated weigh-ins leave later persisted trends stale

Append/delete recomputes a persisted trend scalar only for the edited day (`WeighInRepository.kt:193-212,240-271`). EWMA is path-dependent, so inserting or deleting a historical scalar changes every later result through today.

**Impact:** Hub/day projections and a fresh `currentTrend` calculation can disagree after backdated edits.

**Smallest correction:** either stop persisting redundant trend events and derive the series from raw daily scalars, or recompute the affected suffix transactionally. Test every later day after inserting/deleting a historical reading.

### P1 — Weight input accepts invalid numeric values and currently hardcodes kilograms

`WeighInViewModel` accepts any `toDoubleOrNull` result, including `NaN`, infinities, zero, and negative values (`feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:329-348`). Save and UI paths interpret/display kilograms (`:388-452`) regardless of profile preference.

**Impact:** invalid values can reach calculations whose preconditions throw; an imperial entry such as 180 can be stored as 180kg.

**Smallest correction:** reject non-finite/non-positive/out-of-domain values at the write boundary and convert display units to canonical kg. Unit preference itself belongs to existing WLO-0052; this review adds the validation requirement.

### P2 — KMP posture currently means JVM/Android sharing, not portability

The only KMP targets are JVM and Android (`build-logic/src/main/kotlin/wlo.android.kmp.library.gradle.kts:20-34`), while D3 only rejects Android imports (`ArchRules.kt:31-43`). `commonMain` code uses `Locale`, `java.io`, JCE, `java.security`, and `java.time`, for example `core/common/.../ByteFormat.kt:3-18` and multiple files in `core/vault/src/commonMain`.

**Impact:** the project pays KMP source-set/explicit-API/build complexity while documentation implies portability that is not tested and the vault could not compile for a non-JVM target.

**Smallest correction:** keep KMP for genuinely pure model/engines/documents/consent. Either describe JVM+Android reuse honestly and make JVM-specific modules JVM/Android, or add real platform boundaries and a non-JVM compilation target. Do not add `expect/actual` ceremony without a credible second platform.

### P2 — Architecture checks overstate their dependency guarantee

Architecture docs promise resolved-graph enforcement, but `wlo.architecture-check.gradle.kts:41-56` inspects declared dependencies rather than resolution results. A banned network stack can arrive transitively while the gate reports success.

**Smallest correction:** inspect selected resolved compile/runtime configurations, including transitives, or rename/narrow the guarantee. Add a TestKit transitive-dependency fixture.

### P2 — Standard-wrapper APIs erase useful M3 capabilities

Examples:

- `WloSheet` hides sheet state, drag handle, insets, properties, and behavior (`WloSheet.kt:31-56`).
- `WloDialog` permits label/handler mismatches and a no-op dismissal (`WloDialog.kt:26-66`).
- `WloCard` forces full width and uses `Surface` for static cards (`WloCard.kt:45-86`).
- `WloSwitchRow` is a hand-built row despite claiming list semantics (`WloSwitch.kt:34-93`).
- `WloTemplateCard` manually recreates clickable-card and chip behavior (`WloTemplateCard.kt:50-145`).

**Smallest correction:** use direct standard components where call sites need their real API. Keep optional helpers only when they add WLO-specific semantics without erasing standard slots/state.

### P2 — State restoration, cancellation, and reload ordering have gaps

No production ViewModel uses `SavedStateHandle`. Several form/sheet drafts use `remember` rather than `rememberSaveable`; import/restore keeps selected bytes in memory. Rapid weigh-in filter events launch independent reloads that can complete out of order (`WeighInViewModel.kt:273-300,388-550`). `BackupWorker` and the normal network dispatch path catch broad `Exception`, including cancellation (`BackupSchedulerImpl.kt:69-83`; `core/network/src/main/kotlin/app/wlo/core/network/NetworkDispatcher.kt:100-111`).

**Smallest correction:** persist only small route/form identifiers; reopen SAF content via persisted permission; use `flatMapLatest`/serialized reload ownership; always rethrow `CancellationException` and write cancellation receipts safely when required.

### P2 — Build-quality gates are disabled where they now have evidence-backed value

Application/library conventions set lint non-fatal (`build-logic/src/main/kotlin/wlo.application.gradle.kts:96-98`; `wlo.android.library.gradle.kts:28-31`). Detekt globally disables long method, parameter, cyclomatic-complexity, swallowed-exception, and generic-catch checks (`config/detekt/detekt.yml:9-17,32-36`). The current lint error is therefore informational.

**Smallest correction:** baseline intentional legacy findings, then make correctness/security lint fatal in CI. Re-enable complexity checks with pragmatic thresholds and reasoned local suppressions rather than blanket disablement.

### P2/P3 — Additional correctness and cleanup findings

- Consent-ledger restore rejects valid newer suffixes when a local prefix exists (`RestorePipeline.kt:311-326`); compare overlapping `(seq, hash)` records and append the strict suffix.
- `NetworkReceiptDao.recent` orders ascending with a limit (`core/database/src/commonMain/kotlin/app/wlo/core/database/Daos.kt:469-470`), so capped verification can inspect only the oldest prefix while claiming integrity.
- Reminder periodic work drifts across DST/time-zone changes (`app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt:97-104`).
- Backup failure notification lacks an API 33 permission guard (`BackupSchedulerImpl.kt:87-105`).
- `VaultAdapters` has 14 collaborators and reaches into F01's internal completion flag (`app/src/main/kotlin/app/wlo/app/di/VaultAdapters.kt:58,130-145,414-429`). Move the workflow/flag to an existing core contract rather than adding a universal use-case layer.
- `LogbookViewModel` imports the concrete `RoomWeighInRepository` for a semantic attribute key (`LogbookViewModel.kt:10-14,199-207`). Return typed flags through the repository contract.
- `core:designsystem` is 4,475 lines; `WloForecastCard` alone is 593 lines. Feature-specific composites should not automatically become universal UI infrastructure.
- Koin annotations are declared but the project uses manual DSL wiring. Remove the unused dependency after confirming generated output is absent.

## Material 3 component disposition

| Area | Disposition | Reason |
|---|---|---|
| `WloTheme`, typography, color, shape, spacing, motion/haptics policy | Keep and correct | Theme/token mapping is the right centralization point; complete semantic roles and contrast. |
| Standard buttons, cards, list items, icon buttons, switches, dialogs, sheets, progress, navigation | Direct M3 or optional thin helpers | Never ban direct use; helpers must preserve standard slots/state and add real policy. |
| `WloSegmentedBar` | Replace with M3 segmented buttons | Standard component exists and supplies size/selection semantics. |
| `WloCheckRow` | Replace with M3 `Checkbox` plus `ListItem`/toggleable row | Current implementation recreates control and semantics. |
| `WloPrimaryRow` | Replace with an appropriate M3 button | It is a fake button. |
| `WloSwitchRow` | Rebase on M3 `ListItem` + `Switch`, or use them directly | Aligns list anatomy and toggle semantics. |
| `WloTemplateCard` | Rebase on M3 clickable `Card`; use selection semantics | Current custom surface and tags duplicate framework behavior. |
| `WloBanner` | Keep composite container; use M3 `TextButton` for action | M3 Compose has no direct banner, but its action is standard. |
| `WloRailButton` | Conditional | Combined click/long-click may justify custom behavior; compose M3 internally and document the exact limitation/ticket. |
| `WloSwipeRevealRow` | Keep | Its release-gated, velocity-blind behavior has a documented M3 limitation and a custom accessibility action. |
| Provenance/stat composites, charts, rings, forecast bands/cards, schedule charts, heatmap | Keep as product-specific UI | These express unique WLO data and behavior; add explicit semantics and keep feature specificity visible. |
| Badge/static tag and empty-state composites | Keep small | No need to invent interaction behavior; avoid promoting every usage into a new atom. |

## Module disposition

| Module | Disposition | Review note |
|---|---|---|
| `:app` | Keep; simplify | Correct composition root. Move domain orchestration out of broad adapters and make navigation standard/adaptive. |
| `:core:model` | Keep | Stable product types and provenance earn the boundary. |
| `:core:common` | Keep; clarify JVM posture | Useful cross-module utilities; remove false platform-portability implication. |
| `:core:engines` | Keep | Pure deterministic math with property/golden tests is exemplary. |
| `:core:ports` | Keep | Enables restricted implementations; improve cohesive operation contracts. |
| `:core:consent` | Keep | Directly enforces a flagship invariant. |
| `:core:documents` | Keep | Versioned immutable documents and migrations are justified. |
| `:core:database` | Keep | Room schema/migrations are a coherent technical boundary. |
| `:core:data` | Keep; strengthen | Make compound mutations transactional and return typed domain outcomes. |
| `:core:datastore` | Keep for now | One typed owner for settings/documents; review only if module overhead becomes measurable. |
| `:core:network` | Keep restricted | The egress choke point is high-value; fix cancellation and resolved-graph verification. |
| `:core:ai` | Keep restricted | On-device implementation boundary is product-relevant. |
| `:core:media` | Keep restricted | Camera/image-lifetime isolation is product-relevant. |
| `:core:vault` | Keep; correct posture/workflows | Encryption/backup mechanics cohere, but commonMain is JVM-specific and multi-store guarantees need redesign. |
| `:core:testing` | Keep | Shared fakes/fixtures serve multiple pure modules. |
| `:core:designsystem` | Substantially simplify | Retain theme and product composites; remove mandatory wrappers and feature-specific accretion. |
| `:benchmarks` | Keep/defer expansion | Useful for deterministic planners; do not add benchmark infrastructure without a performance question. |
| `:feature:f01-onboarding` | Keep | Feature boundary matches spec; large screen/state files need cohesion review, not a new module. |
| `:feature:f02-food` | Keep | Feature boundary is justified; capture state is large and should be decomposed internally only where behavior separates. |
| `:feature:f03-planning` | Keep | Feature boundary is justified; planner visuals should not automatically move into global design infrastructure. |
| `:feature:f04-shopping` | Keep | Feature boundary is justified; preserve no feature-to-feature dependencies. |
| `:feature:f06-weight` | Keep; repair transactions | Existing WLO-0052/WLO-0054 overlap is respected. |
| `:feature:f10-daily-hub` | Keep | Composition feature is legitimate; constrain UI/state files without adding a ceremonial domain layer. |
| `:feature:f12-consent` | Keep | Consent/audit surfaces match the product boundary. |
| `:feature:f13-vault` | Keep; consolidate control ownership | VM should not coordinate scheduler, settings, keys, and vault independently. |

**Module policy:** freeze additional module creation unless it establishes an actual feature, security/classpath boundary, delivery boundary, or independently reusable build unit. Do not flatten the existing graph merely to reduce the number 25.

## What is already strong

- All implemented screens found in the audit collect flows with `collectAsStateWithLifecycle`; ViewModels consistently use `viewModelScope`.
- Feature-to-feature Gradle dependencies are absent, and only `:app` sees restricted network/AI/media/vault implementations.
- Koin is an appropriate, lightweight fit; graph verification exists. There is no evidence that replacing it with Hilt would help.
- Pure engines, explicit time inputs, constants/provenance types, property tests, and golden fixtures fit the numbers-geek product.
- Room installs explicit migrations and enables foreign keys; migration-chain tests exist.
- Consent and normal egress are centralized and generally fail closed.
- Restore validates/stages before mutation, encrypted backup format is well tested, and vault blobs use per-file AES-GCM/Keystore outside MediaStore.
- Network download cancellation is handled carefully with a non-cancellable receipt then rethrow; that pattern should be reused by normal dispatch.
- `WloSwipeRevealRow` demonstrates the correct custom-component standard: documented platform limitation, ticket, contained implementation, and accessibility action.

## Domain invariant disposition

| Invariant | Status | Evidence / owner |
|---|---|---|
| App-private health data does not leave the device implicitly | Failing | Android system backup is implicit; WLO-0058. |
| An enabled app lock never exposes content before authentication | Failing | Cold start and process death begin unlocked; WLO-0059. |
| Turning automatic backup off prevents future scheduled writes | Failing | Existing unique work remains active and the worker does not recheck policy; WLO-0060. |
| Backup replacement preserves the last known-good artifact until the new one is durable | Failing | Same-name SAF document is deleted first; WLO-0060. |
| Restore is explicit and reports its actual commit boundary | Failing | Report Continue commits; Room precedes settings/documents/projections; WLO-0061. |
| Import failure/cancellation has defined atomic or partial semantics | Failing | CSV is appended row by row and sidecar failure is ignored; WLO-0061. |
| A weigh-in mutation cannot partially update events, attributes, trends, or projections | Failing | Compound writes cross repository calls and secondary errors are suppressed; WLO-0062. |
| Backdated weight mutations produce the same later trend as a clean recomputation | Failing | Only the edited day is refreshed; WLO-0062. |
| Stored canonical mass is finite, positive, plausible, and unit-correct | Failing | Non-finite/non-positive values are accepted and UI assumes kg; WLO-0062 plus WLO-0052. |
| Consent and normal network egress fail closed and remain receipted | Largely passing | Central consent/dispatcher boundaries and tests are strong; fix capped receipt ordering/cancellation under WLO-0065 or a child ticket. |
| Silhouette storage remains vector-only | Passing in reviewed path | Media/vault boundaries and blob checks align with R-U16; preserve as a regression invariant. |
| Pure numerical engines are deterministic and explainable | Passing in reviewed path | Explicit inputs, provenance, property tests, and goldens are strong; add suffix-equivalence coverage under WLO-0062. |

## Remediation order

1. **Privacy release blockers:** Android backup policy and cold-start app lock.
2. **Truthful user controls/data safety:** cancel disabled backup work; safe same-day replacement; restore confirmation and resumable multi-store commit.
3. **M3 architecture policy:** remove import bans, replace the three proven fake controls, and establish behavior/accessibility gates.
4. **Mutation correctness:** repository-level atomic weigh-in/CSV operations and trend-suffix recomputation.
5. **Accessibility/navigation:** contrast, font scale, semantics, nested Up behavior, saved tab state, and adaptive navigation.
6. **Architecture honesty:** KMP scope, resolved dependency checks, lint/Detekt gates, and cancellation/state-restoration cleanup.

Each remediation should be a small ticket/commit with a failing test or reproducible check first. Avoid a simultaneous all-screen or all-module rewrite.

## Review acceptance status

- Every included module received a disposition.
- The design-system families received a Material 3 disposition.
- Representative onboarding, Hub, weigh-in, backup/restore, and consent/egress paths were traced.
- Existing tickets WLO-0052 and WLO-0054 retain ownership of unit preference and reload coalescing respectively.
- Findings distinguish release/privacy blockers from architecture and polish.
- No new application abstraction was introduced by this review.
